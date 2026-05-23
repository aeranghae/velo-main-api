package cloud.velo.main.service;

import cloud.velo.main.controller.dto.ProjectLogResponseDto;
import cloud.velo.main.controller.dto.ProjectLogSaveDto;
import cloud.velo.main.domain.Project;
import cloud.velo.main.domain.ProjectStatus;
import cloud.velo.main.domain.User;
import cloud.velo.main.repository.ProjectRepository;
import cloud.velo.main.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
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

    // UUID 대신 "userId:projectId" 조합을 Key로 사용
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final StringRedisTemplate redisTemplate;

    // 시간 포멧터
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * 1. 과거 로그 전체 조회 (DB 데이터 + Redis 임시 버퍼 데이터 실시간 병합 서빙)
     */
    @Transactional(readOnly = true)
    public ProjectLogResponseDto getProjectMetadataAndLogs(String uuid, String loginEmail) {

        User currentUser = userRepository.findByEmail(loginEmail)
                .orElseThrow(() -> new IllegalArgumentException("인증 장부에 등록되지 않은 유저입니다: " + loginEmail));

        Project project = projectRepository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 프로젝트입니다. UUID: " + uuid));

        if (!project.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("해당 프로젝트의 로그를 조회할 정식 권한이 없습니다.");
        }

        StringBuilder logBuilder = new StringBuilder();

        // DB 로그 복원
        List<Map<String, Object>> dbLogs = project.getPipelineLogs();
        if (dbLogs != null && !dbLogs.isEmpty()) {
            for (Map<String, Object> logMap : dbLogs) {
                logBuilder.append(String.format("[%s][%s] %s\n",
                        logMap.get("level"),
                        logMap.get("time"),
                        logMap.get("message")));
            }
        }

        // 2. Redis 로그 복원
        String redisKey = "project:logs:" + uuid;
        List<String> bufferedLogs = redisTemplate.opsForList().range(redisKey, 0, -1);
        if (bufferedLogs != null) {
            for (String raw : bufferedLogs) {
                String[] parts = raw.split("\\|\\|", 4);
                if (parts.length < 4) continue;

                // ISO 표준 시간을 포멧터로 지정
                String timeStr = LocalDateTime.parse(parts[1], ISO_FORMATTER).format(TIME_FORMATTER);
                logBuilder.append(String.format("[%s][%s] %s\n", parts[0], timeStr, parts[3]));
            }
        }

        return ProjectLogResponseDto.builder()
                .uuid(project.getUuid())
                .status(project.getStatus().name()) // "GENERATING" 같은 영어 이름
                .statusDescription(project.getStatus().getDescription())
                .status(project.getStatus().name())
                .framework(project.getFramework())
                .previousLogs(logBuilder.isEmpty() ? "[System] 아직 누적된 파이프라인 로그가 존재하지 않습니다." : logBuilder.toString())
                .build();
    }

    /**
     * 2. FastAPI 웹훅 로그 수신부 (Redis 버퍼링 + 밀어내기 덤프 메커니즘 탑재)
     */
    @Transactional
    public void saveWorkerLog(ProjectLogSaveDto dto) {

        String uuid = dto.getUuid();
        String redisKey = "project:logs:" + uuid;

        // 로그 발생 시점 서버 시간
        String createdAtStr = LocalDateTime.now().format(ISO_FORMATTER);


        ProjectStatus incomingStatus = ProjectStatus.CREATED;
        if (dto.getStatus() != null) {
            try {
                String statusStr = dto.getStatus().toUpperCase();

                if ("COMPLETEDE".equals(statusStr)) {
                    statusStr = "COMPLETED";
                }
                incomingStatus = ProjectStatus.valueOf(statusStr);

            } catch (IllegalArgumentException e) {
                // 2. return을 삭제하고, 로그만 남긴 뒤 기본값으로 진행하거나
                // 아예 상태값 변환에 실패해도 덤프 로직을 탈 수 있도록 구조를 잡아야 합니다.
                log.warn("정의되지 않은 상태 값이 수신되었습니다. 기본값(CREATED)으로 처리합니다: {}", dto.getStatus());
                incomingStatus = ProjectStatus.CREATED;
            }
        }

        // Redis 버퍼 리스트에 실시간 적재 시 STATUS(incomingStatus)도 같이 4파트로 조립해서 저장!
        String logLine = dto.getLogLevel() + "||" + createdAtStr + "||" + incomingStatus.name() + "||" + dto.getMessage();
        redisTemplate.opsForList().rightPush(redisKey, logLine);

        // 만료 시간을 24시간으로 설정
        redisTemplate.expire(redisKey, java.time.Duration.ofDays(1));

        // 2. SSE 실시간 스트리밍
        SseEmitter emitter = emitters.get(uuid);

        if (emitter != null) {
            try {
                // 텍스트 로그 라인 전송
                emitter.send(SseEmitter.event()
                        .name("log-stream")
                        .data(logLine));
            } catch (Exception e) {
                emitters.remove(uuid);
            }
        }

        // COMPLETED 나 FAILED만 DB에 한번 저장
        if (incomingStatus == ProjectStatus.COMPLETED || incomingStatus == ProjectStatus.FAILED) {
            List<String> rawLogs = redisTemplate.opsForList().range(redisKey, 0, -1);

            if (rawLogs != null && !rawLogs.isEmpty()) {
                try {
                    Project project = projectRepository.findByUuid(uuid).orElseThrow();

                    // 파싱한 로그를 JSONB용 Map 구조로 담기
                    List<Map<String, Object>> jsonbLogs = new ArrayList<>();
                    for (String raw : rawLogs) {
                        String[] parts = raw.split("\\|\\|", 4);
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

                    log.info("[DB] 최종 로그 일괄 덤프 완료. 루프를 성공적으로 마감합니다.");
            } catch (Exception e) {
                log.error("[🚨DB Error] 최종 로그를 DB에 백업하는 과정에서 실패했습니다.", e);
            } finally {
                // 예외 발생 여부와 무관하게 실행
                clearRedisLogCache(uuid);
            }
        }

            //  공정 종결 시 종결된 복합 키를 타겟으로 에미터 종료 및 장부 삭제 진행
            if (emitter != null) {
                emitter.complete();
            }
            emitters.remove(uuid);
        }
    }

    /**
     * 3. 리액트 전용 SSE 전화선 개설 창구
     * 컨트롤러로부터 uuid와 유저 이메일을 받아 완벽한 소유권 검증 후 복합 키로 에미터를 등록합니다.
     */
    @Transactional(readOnly = true)
    public SseEmitter createSseConnection(String uuid, String loginEmail) {
        User currentUser = userRepository.findByEmail(loginEmail).orElseThrow();
        Project project = projectRepository.findByUuid(uuid).orElseThrow();

        if (!project.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("권한이 없습니다.");
        }

        // 복잡한 키 대신 직관적이고 빠른 uuid를 전화선 번호로 씁니다.
        SseEmitter emitter = new SseEmitter(-1L);
        emitters.put(uuid, emitter);

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

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 프로젝트입니다. ID: " + projectId));

        clearRedisLogCache(project.getUuid());
    }

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