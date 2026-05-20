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

    // UUID 대신 "userId:projectId" 조합을 Key로 사용
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final StringRedisTemplate redisTemplate;

    // 시간 포멧터
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

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
        List<ProjectLog> dbLogs = projectLogRepository.findLogsByUserIdAndProjectId(currentUser.getId(), project.getId());
        for (ProjectLog bl : dbLogs) {
            logBuilder.append(String.format("[%s] %s\n", bl.getLogLevel(), bl.getMessage()));
        }

        // redis 로그 복원
        String redisKey = "project:logs:" + uuid;
        List<String> bufferedLogs = redisTemplate.opsForList().range(redisKey, 0, -1);
        if (bufferedLogs != null) {
            for (String raw : bufferedLogs) {
                String[] parts = raw.split("\\|\\|", 3); // 🌟 3파트로 파싱
                if (parts.length < 3) continue;

                // ISO 표준 시간을 포멧터로 지정
                String timeStr = LocalDateTime.parse(parts[1]).format(TIME_FORMATTER);
                logBuilder.append(String.format("[%s][%s] %s\n", parts[0], timeStr, parts[2]));
            }
        }

        return ProjectLogResponseDto.builder()
                .uuid(project.getUuid())
                .status(project.getStatus().name()) // "GENERATING" 같은 영어 이름
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
        String createdAtStr = LocalDateTime.now().toString();

        // 1. Redis 버퍼 리스트에 실시간 적재 (LEVEL||TIME||MESSAGE)
        String logLine = dto.getLogLevel() + "||" + createdAtStr + "||" + dto.getMessage();
        redisTemplate.opsForList().rightPush(redisKey, logLine);


        SseEmitter emitter = emitters.get(uuid);

        ProjectStatus incomingStatus = ProjectStatus.CREATED;
        if (dto.getStatus() != null) {
            try {
                incomingStatus = ProjectStatus.valueOf(dto.getStatus().toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("정의되지 않은 상태 값이 수신되었습니다: {}", dto.getStatus());
                return; // 잘못된 상태가 들어오면 즉시 차단
            }
        }

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

                Project project = projectRepository.findByUuid(uuid).orElseThrow();
                User owner = project.getUser();

                List<ProjectLog> bulkLogEntities = new ArrayList<>();
                for (String raw : rawLogs) {
                    String[] parts = raw.split("\\|\\|", 2);
                    bulkLogEntities.add(ProjectLog.builder()
                            .user(owner)
                            .project(project)
                            .logLevel(parts[0])
                            .status(incomingStatus)
                            .createdAt(LocalDateTime.parse(parts[1]))
                            .message(parts[1])
                            .build());
                }

                projectLogRepository.saveAll(bulkLogEntities);
                project.updateStatus(incomingStatus); // 프로젝트 최종상태 업데이트
            }

            //  공정 종결 시 종결된 복합 키를 타겟으로 에미터 종료 및 장부 삭제 진행
            if (emitter != null) {
                emitter.complete();
            }
            emitters.remove(uuid);
            redisTemplate.delete(redisKey);
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
        SseEmitter emitter = new SseEmitter(60 * 1000L);
        emitters.put(uuid, emitter);

        emitter.onCompletion(() -> emitters.remove(uuid));
        emitter.onTimeout(() -> emitters.remove(uuid));

        return emitter;
    }
}