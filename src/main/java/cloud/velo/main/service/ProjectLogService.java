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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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

    // 🌟 [수정] UUID 대신 "userId:projectId" 조합을 Key로 삼는 안전한 고속 장부 수립
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 편의 메서드: 사용자 ID와 프로젝트 ID로 장부용 고유 키 생성
     */
    private String getEmitterKey(Long userId, Long projectId) {
        return userId + ":" + projectId;
    }

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

        List<ProjectLog> dbLogs = projectLogRepository.findLogsByUserIdAndProjectId(currentUser.getId(), project.getId());
        for (ProjectLog bl : dbLogs) {
            logBuilder.append(String.format("[%s] %s\n", bl.getLogLevel(), bl.getMessage()));
        }

        String redisKey = "project:logs:" + uuid;
        List<Object> bufferedLogs = redisTemplate.opsForList().range(redisKey, 0, -1);
        if (bufferedLogs != null) {
            for (Object raw : bufferedLogs) {
                String[] parts = ((String) raw).split("\\|\\|", 2);
                logBuilder.append(String.format("[%s] %s\n", parts[0], parts[1]));
            }
        }

        return ProjectLogResponseDto.builder()
                .uuid(project.getUuid())
                .status(project.getStatus().name())
                .framework(project.getFramework())
                .previousLogs(logBuilder.length() == 0 ? "[System] 아직 누적된 파이프라인 로그가 존재하지 않습니다." : logBuilder.toString())
                .build();
    }

    /**
     * 2. FastAPI 웹훅 로그 수신부 (Redis 버퍼링 + 밀어내기 덤프 메커니즘 탑재)
     */
    @Transactional
    public void saveWorkerLog(ProjectLogSaveDto dto) {
        String redisKey = "project:logs:" + dto.getUuid();

        // 1. Redis 버퍼 리스트에 실시간 적재
        String logLine = dto.getLogLevel() + "||" + dto.getMessage();
        redisTemplate.opsForList().rightPush(redisKey, logLine);

        // 실시간 스트리밍 중계를 위해 연관된 실물 엔티티 정보(User, Project ID)를 먼저 조회합니다.
        Project project = projectRepository.findByUuid(dto.getUuid())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 프로젝트 UUID: " + dto.getUuid()));
        User owner = project.getUser();

        // "userId:projectId" 조합 키로 정확하게 매핑된 에미터를 추적합니다.
        String emitterKey = getEmitterKey(owner.getId(), project.getId());
        SseEmitter emitter = emitters.get(emitterKey);

        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("log-stream")
                        .data(logLine));
            } catch (Exception e) {
                emitters.remove(emitterKey);
            }
        }

        // 외부에서 들어온 String 상태를 Enum으로 번역
        ProjectStatus incomingStatus = null;
        if (dto.getStatus() != null) {
            try {
                incomingStatus = ProjectStatus.valueOf(dto.getStatus().toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("정의되지 않은 상태 값이 수신되었습니다: {}", dto.getStatus());
                return;
            }
        }

        // COMPLETED 나 FAILED 신호 처리
        if (incomingStatus == ProjectStatus.COMPLETED || incomingStatus == incomingStatus.FAILED) {

            List<Object> rawLogs = redisTemplate.opsForList().range(redisKey, 0, -1);

            if (rawLogs != null && !rawLogs.isEmpty()) {
                List<ProjectLog> bulkLogEntities = new ArrayList<>();

                for (Object raw : rawLogs) {
                    String[] parts = ((String) raw).split("\\|\\|", 2);
                    bulkLogEntities.add(ProjectLog.builder()
                            .user(owner)
                            .project(project)
                            .logLevel(parts[0])
                            .status(incomingStatus)
                            .message(parts[1])
                            .build());
                }

                projectLogRepository.saveAll(bulkLogEntities);
                log.info("▶ [Redis ➡️ DB Dump 완료] 총 {}개의 로그가 PostgreSQL로 일괄 덤프되었습니다.", bulkLogEntities.size());
            }

            //  공정 종결 시 종결된 복합 키를 타겟으로 에미터 종료 및 장부 삭제 진행
            if (emitter != null) {
                emitter.complete();
            }
            emitters.remove(emitterKey);

            redisTemplate.delete(redisKey);
            project.updateStatus(incomingStatus);
        }
    }

    /**
     * 3. 리액트 전용 SSE 전화선 개설 창구
     * 컨트롤러로부터 uuid와 유저 이메일을 받아 완벽한 소유권 검증 후 복합 키로 에미터를 등록합니다.
     */
    @Transactional(readOnly = true)
    public SseEmitter createSseConnection(String uuid, String loginEmail) {
        User currentUser = userRepository.findByEmail(loginEmail)
                .orElseThrow(() -> new IllegalArgumentException("인증되지 않은 사용자입니다: " + loginEmail));

        Project project = projectRepository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 프로젝트입니다. UUID: " + uuid));

        if (!project.getUser().getId().equals(currentUser.getId())) {
            throw new SecurityException("해당 프로젝트의 스트리밍을 구독할 정식 권한이 없습니다.");
        }

        // 안전한 유저별 복합 키 생성 ("userId:projectId")
        String emitterKey = getEmitterKey(currentUser.getId(), project.getId());

        SseEmitter emitter = new SseEmitter(60 * 1000L);
        emitters.put(emitterKey, emitter);

        // 에러나 만료 시 복합 키를 기준으로 장부에서 격리 및 삭제
        emitter.onCompletion(() -> emitters.remove(emitterKey));
        emitter.onTimeout(() -> emitters.remove(emitterKey));

        return emitter;
    }
}