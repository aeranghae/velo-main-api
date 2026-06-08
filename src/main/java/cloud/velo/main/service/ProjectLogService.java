package cloud.velo.main.service;

import cloud.velo.main.dto.response.ProjectLogResponse;
import cloud.velo.main.dto.request.ProjectLogSaveRequest;
import cloud.velo.main.domain.Project;
import cloud.velo.main.domain.ProjectStatus;
import cloud.velo.main.domain.User;
import cloud.velo.main.exception.UserNotFoundException;
import cloud.velo.main.exception.ProjectNotFoundException;
import cloud.velo.main.repository.ProjectRepository;
import cloud.velo.main.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectLogService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final StringRedisTemplate redisTemplate;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * 1. 과거 로그 전체 조회 (DB 데이터 + Redis 임시 버퍼 데이터 실시간 병합 서빙)
     */
    @Transactional(readOnly = true)
    public ProjectLogResponse getProjectMetadataAndLogs(String uuid, String loginEmail) {
        // 자바 공용 예외 -> 커스텀 예외로 전면 리팩토링
        User currentUser = userRepository.findByEmail(loginEmail)
                .orElseThrow(() -> new UserNotFoundException("인증 장부에 등록되지 않은 유저입니다. email: " + loginEmail));

        Project project = projectRepository.findByUuid(uuid)
                .orElseThrow(() -> new ProjectNotFoundException("존재하지 않는 프로젝트입니다. UUID: " + uuid));

        // SecurityException 대신 관제탑이 403 Forbidden 상자로 처리할 수 있게 시큐리티 표준 예외 유발
        if (!project.getUser().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("해당 프로젝트의 로그를 조회할 정식 권한이 없습니다. UUID: " + uuid);
        }

        StringBuilder logBuilder = new StringBuilder();

        // DB 로그 복원
        List<Map<String, Object>> dbLogs = project.getPipelineLogs();
        if (dbLogs != null && !dbLogs.isEmpty()) {
            for (Map<String, Object> logMap : dbLogs) {
                String level = String.valueOf(logMap.getOrDefault("level", "INFO"));
                String time = String.valueOf(logMap.getOrDefault("time", "00:00:00"));
                String message = String.valueOf(logMap.getOrDefault("message", ""));
                logBuilder.append(String.format("[%s][%s] %s\n", level, time, message));
            }
        }

        // Redis 로그 복원
        String redisKey = "project:logs:" + uuid;
        List<String> bufferedLogs = redisTemplate.opsForList().range(redisKey, 0, -1);
        if (bufferedLogs != null) {
            for (String raw : bufferedLogs) {
                String[] parts = raw.split("\\|\\|", 5);
                if (parts.length < 4) continue;

                String timeStr = LocalDateTime.parse(parts[1], ISO_FORMATTER).format(TIME_FORMATTER);
                logBuilder.append(String.format("[%s][%s] %s\n", parts[0], timeStr, parts[3]));
            }
        }

        return ProjectLogResponse.builder()
                .uuid(project.getUuid())
                .status(project.getStatus().name())
                .statusDescription(project.getStatus().getDescription())
                .framework(project.getFramework())
                .previousLogs(logBuilder.isEmpty() ? "[System] 아직 누적된 파이프라인 로그가 존재하지 않습니다." : logBuilder.toString())
                .build();
    }

    /**
     * 2. FastAPI 웹훅 로그 수신부 (Redis 버퍼링 + 밀어내기 덤프 메커니즘 탑재)
     */
    @Transactional
    public void saveWorkerLog(ProjectLogSaveRequest dto) {
        String uuid = dto.getUuid();
        String redisKey = "project:logs:" + uuid;

        String createdAtStr = LocalDateTime.now().format(ISO_FORMATTER);
        ProjectStatus incomingStatus = parseStatus(dto.getStatus());

        String logLine = dto.getLogLevel() + "||" + createdAtStr + "||" + incomingStatus.name() + "||" + dto.getMessage() + "||" + dto.isActivityFeed();
        redisTemplate.opsForList().rightPush(redisKey, logLine);
        redisTemplate.expire(redisKey, java.time.Duration.ofDays(1));

        // SSE 실시간 스트리밍
        SseEmitter emitter = emitters.get(uuid);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("log-stream")
                        .data(logLine));
            } catch (java.io.IOException e) {
              // 정상적인 이탈(Broken pipe)은 SSE 중단 알림
              log.info("[SSE] 클라이언트 이탈 감지 (스트리밍 중단). UUID: {}", uuid);
              emitters.remove(uuid);
            } catch (Exception e) {
              log.warn("[SSE] 실시간 스트리밍 중 통신 오류 발생. UUID: {} | 원인: {}", uuid, e.getMessage());
              emitters.remove(uuid);
            }
        }

        // COMPLETED 나 FAILED만 DB에 일괄 저장 및 백업
        if (incomingStatus == ProjectStatus.COMPLETED || incomingStatus == ProjectStatus.FAILED) {
            List<String> rawLogs = redisTemplate.opsForList().range(redisKey, 0, -1);

            if (rawLogs != null && !rawLogs.isEmpty()) {
                // [버그 수정] 깨져있던 try-catch-finally 괄호 밸런스를 아키텍처 원칙에 맞춰 완전히 리폼했습니다.
                try {
                    Project project = projectRepository.findByUuid(uuid)
                            .orElseThrow(() -> new ProjectNotFoundException("로그를 백업할 프로젝트 장부를 찾을 수 없습니다. UUID: " + uuid));

                    List<Map<String, Object>> jsonbLogs = new ArrayList<>();
                    for (String raw : rawLogs) {
                        String[] parts = raw.split("\\|\\|", 5);
                        if (parts.length < 4) continue;

                        String timeStr = LocalDateTime.parse(parts[1], ISO_FORMATTER).format(TIME_FORMATTER);

                        Map<String, Object> logMap = new HashMap<>();
                        logMap.put("level", parts[0]);
                        logMap.put("time", timeStr);
                        logMap.put("status", parts[2]);
                        logMap.put("message", parts[3]);

                        jsonbLogs.add(logMap);
                    }
                    project.getPipelineLogs().addAll(jsonbLogs);
                    project.updateStatus(incomingStatus);

                    log.info("[DB] 최종 파이프라인 로그 일괄 JSONB 덤프 성공 마감. UUID: {}", uuid);

                } catch (Exception e) {
                    log.error("[DB Error] 최종 로그를 PostgreSQL JSONB에 백업하는 과정에서 심각한 시스템 장애 발생.", e);
                    throw new IllegalStateException("로그 데이터베이스 마감 공정 중 인프라 합선 오류가 발생했습니다.", e);
                } finally {
                    clearRedisLogCache(uuid);
                }
            }

            if (emitter != null) {
                emitter.complete();
            }
            emitters.remove(uuid);
        }
    }

    /**
     * 3. 리액트 전용 SSE 전화선 개설 창구
     */
    @Transactional(readOnly = true)
    public SseEmitter createSseConnection(String uuid, String loginEmail) {
        // 자바 공용 예외 컷 -> 커스텀 예외로 교체
        User currentUser = userRepository.findByEmail(loginEmail)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다. email: " + loginEmail));

        Project project = projectRepository.findByUuid(uuid)
                .orElseThrow(() -> new ProjectNotFoundException("프로젝트를 찾을 수 없습니다. UUID: " + uuid));

        if (!project.getUser().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("해당 실시간 로그 스트림에 접근할 권한이 없습니다.");
        }

        // 권한 검증 후 타임아웃은 무한대 기본 설정
        SseEmitter emitter = new SseEmitter(-1L);
        emitters.put(uuid, emitter); // 세션관리 : concurrentHashMap 구조로 emitter에 uuid 저장

        // 클라이언트 측에서 타임아웃 발생시, 콜백 바인딩하여 메모리 누수 방지
        emitter.onCompletion(() -> emitters.remove(uuid));
        emitter.onTimeout(() -> emitters.remove(uuid));

        return emitter;
    }

    /**
     * 4. 프로젝트 삭제 전 외래키 제약조건을 원천 차단하기 위해 자식 로그들을 선제 삭제
     */
    @Transactional
    public void deleteLogsByProjectId(Long projectId) {
        log.info("[System] 프로젝트(ID: {}) 삭제 요청 감지. 연관된 모든 자식 로그 데이터를 먼저 제거합니다.", projectId);

        // 커스텀 예외 교체
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException("존재하지 않는 프로젝트입니다. ID: " + projectId));

        clearRedisLogCache(project.getUuid()); // 프로젝트 제거시 남아있는 고아 redis 캐시 메모리 제거
    }

    private ProjectStatus parseStatus(String status) {
        if (status == null) return ProjectStatus.CREATED;

        try {
            String s = status.toUpperCase().replace("COMPLETED", "COMPLETED");
            return ProjectStatus.valueOf(s);
        } catch (Exception e) {
            log.warn("정의되지 않은 상태 값이 수신되었습니다. 기본값(CREATED)으로 처리합니다: {}", status);
            return ProjectStatus.CREATED;
        }
    }

    // 남아있는 고아 redis 캐쉬 메모리 제거 메서드
    private void clearRedisLogCache(String uuid) {
        String redisKey = "project:logs:" + uuid;
        Boolean isDeleted = redisTemplate.delete(redisKey);

        if (Boolean.TRUE.equals(isDeleted)) {
            log.info("[Redis] 임시 로그 캐시가 성공적으로 비워졌습니다. Key: {}", redisKey);
        } else {
            log.debug("[Redis] 삭제하려는 키가 존재하지 않거나 이미 만료되었습니다. Key: {}", redisKey);
        }
    }
}
