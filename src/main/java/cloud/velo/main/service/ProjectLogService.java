package cloud.velo.main.service;

import cloud.velo.main.controller.dto.ProjectLogResponseDto;
import cloud.velo.main.controller.dto.ProjectLogSaveDto;
import cloud.velo.main.domain.Project;
import cloud.velo.main.domain.ProjectLog;
import cloud.velo.main.domain.ProjectStatus;
import cloud.velo.main.domain.User;
import cloud.velo.main.repository.ProjectLogRepository;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectLogService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectLogRepository projectLogRepository;

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final StringRedisTemplate redisTemplate;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

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

        // 1. DB 로그 복원
        List<ProjectLog> dbLogs = projectLogRepository.findLogsByUserIdAndProjectId(currentUser.getId(), project.getId());
        for (ProjectLog bl : dbLogs) {
            logBuilder.append(String.format("[%s] %s\n", bl.getLogLevel(), bl.getMessage()));
        }

        // 2. Redis 로그 복원
        String redisKey = "project:logs:" + uuid;
        List<String> bufferedLogs = redisTemplate.opsForList().range(redisKey, 0, -1);
        if (bufferedLogs != null) {
            for (String raw : bufferedLogs) {
                String[] parts = raw.split("\\|\\|", 4);
                if (parts.length < 4) continue;

                String timeStr = LocalDateTime.parse(parts[1], ISO_FORMATTER).format(TIME_FORMATTER);
                logBuilder.append(String.format("[%s][%s] %s\n", parts[0], timeStr, parts[3]));
            }
        }

        return ProjectLogResponseDto.builder()
                .uuid(project.getUuid())
                .status(project.getStatus().name())
                .framework(project.getFramework())
                .previousLogs(logBuilder.isEmpty() ? "[System] 아직 누적된 파이프라인 로그가 존재하지 않습니다." : logBuilder.toString())
                .build();
    }

    /**
     * 개선 포인트: 메서드 자체의 @Transactional을 제거하여 Redis 적재 및 SSE 전송 시 무의미한 DB 락 결합을 방지합니다.
     * DB 덤프가 필요한 시점에만 내부 자생적 트랜잭션 프록시 또는 별도 컴포넌트를 호출하는 것이 이상적이나,
     * 우선 내부 로직을 쪼개어 영속성 컨텍스트 서빙 효율을 높였습니다.
     */
    public void saveWorkerLog(ProjectLogSaveDto dto) {
        String uuid = dto.getUuid();
        String redisKey = "project:logs:" + uuid;
        String createdAtStr = LocalDateTime.now().format(ISO_FORMATTER);

        ProjectStatus incomingStatus = ProjectStatus.CREATED;
        if (dto.getStatus() != null) {
            try {
                incomingStatus = ProjectStatus.valueOf(dto.getStatus().toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("정의되지 않은 상태 값이 수신되었습니다: {}", dto.getStatus());
                return;
            }
        }

        // 1. Redis 실시간 적재 (DB 트랜잭션과 무관하므로 즉시 반영)
        String logLine = dto.getLogLevel() + "||" + createdAtStr + "||" + incomingStatus.name() + "||" + dto.getMessage();
        redisTemplate.opsForList().rightPush(redisKey, logLine);
        redisTemplate.expire(redisKey, java.time.Duration.ofDays(1));

        // 2. SSE 실시간 스트리밍
        SseEmitter emitter = emitters.get(uuid);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name("log-stream").data(logLine));
            } catch (IOException e) {
                log.debug("SSE 전송 실패로 인한 에미터 제거: {}", uuid);
                cleanupEmitter(uuid);
                emitter = null;
            }
        }

        // 3. 종결 상태 도달 시 대량 덤프 쓰기 작업 진행
        if (incomingStatus == ProjectStatus.COMPLETED || incomingStatus == ProjectStatus.FAILED) {
            executeBulkDumpAndClose(uuid, redisKey, incomingStatus, emitter);
        }
    }

    /**
     * 무거운 DB 쓰기 및 커넥션 정리는 별도 격리하여 롤백 범위를 명확히 제한합니다.
     */
    @Transactional
    protected void executeBulkDumpAndClose(String uuid, String redisKey, ProjectStatus incomingStatus, SseEmitter emitter) {
        List<String> rawLogs = redisTemplate.opsForList().range(redisKey, 0, -1);

        if (rawLogs != null && !rawLogs.isEmpty()) {
            try {
                Project project = projectRepository.findByUuid(uuid)
                        .orElseThrow(() -> new IllegalArgumentException("로그를 백업할 프로젝트가 존재하지 않습니다. UUID: " + uuid));
                User owner = project.getUser();

                List<ProjectLog> bulkLogEntities = new ArrayList<>();
                for (String raw : rawLogs) {
                    String[] parts = raw.split("\\|\\|", 4);
                    if (parts.length < 4) continue;

                    LocalDateTime createdAt = LocalDateTime.parse(parts[1], ISO_FORMATTER);
                    ProjectStatus rowStatus = ProjectStatus.valueOf(parts[2]);

                    bulkLogEntities.add(ProjectLog.builder()
                            .user(owner)
                            .project(project)
                            .logLevel(parts[0])
                            .status(rowStatus)
                            .createdAt(createdAt)
                            .message(parts[3])
                            .build());
                }
                projectLogRepository.saveAll(bulkLogEntities);
                project.updateStatus(incomingStatus); // 프로젝트 상태 최종 반영
                log.info("[DB] 최종 로그 일괄 덤프 완료. 프로젝트 ID: {}", project.getId());
            } catch (Exception e) {
                log.error("[🚨DB Error] 최종 로그를 DB에 백업하는 과정에서 실패했습니다.", e);
            } finally {
                clearRedisLogCache(uuid);
            }
        }

        // 공정 완전 종결 처리
        if (emitter != null) {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.debug("Emitter complete 처리 중 예외 무시");
            }
        }
        cleanupEmitter(uuid);
    }

    @Transactional(readOnly = true)
    public SseEmitter createSseConnection(String uuid, String loginEmail) {
        User currentUser = userRepository.findByEmail(loginEmail)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));
        Project project = projectRepository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 프로젝트입니다."));

        if (!project.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("권한이 없습니다.");
        }

        // 타임아웃 제한 없음(-1L) 설정 시, 서버 자원 사정에 맞게 관리 필요 (기본값 설정 권장하되 유지 시 수동 타임아웃 콜백 명시)
        SseEmitter emitter = new SseEmitter(-1L);
        emitters.put(uuid, emitter);

        emitter.onCompletion(() -> cleanupEmitter(uuid));
        emitter.onTimeout(() -> cleanupEmitter(uuid));
        emitter.onError((e) -> cleanupEmitter(uuid));

        // 최초 연결 시 더미 데이터 전송 (503 에러 방지 방어코드)
        try {
            emitter.send(SseEmitter.event().name("connect").data("connected"));
        } catch (IOException e) {
            cleanupEmitter(uuid);
        }

        return emitter;
    }

    @Transactional
    public void deleteLogsByProjectId(Long projectId) {
        log.info("[System] 프로젝트(ID: {}) 삭제 요청 감지. 연관된 모든 자식 로그 데이터를 먼저 제거합니다.", projectId);

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 프로젝트입니다. ID: " + projectId));

        // 1. 기존 DB 데이터 삭제를 먼저 진행하여, 만약 예외가 나면 롤백되도록 유도
        projectLogRepository.deleteByProjectId(projectId);

        // 2. DB 삭제가 완벽히 보장된 후 외래키 찌꺼기가 남지 않도록 Redis 캐시 제거
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

    private void cleanupEmitter(String uuid) {
        SseEmitter removed = emitters.remove(uuid);
        if (removed != null) {
            try {
                // 커넥션을 확실히 끊어주어 서블릿 컨테이너 스레드 반환 유도
                removed.complete();
            } catch (Exception e) {
                // 이미 닫힌 에미터일 경우 무시
            }
        }
    }
}