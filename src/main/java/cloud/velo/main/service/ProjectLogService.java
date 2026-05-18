package cloud.velo.main.service;

import cloud.velo.main.controller.dto.ProjectLogResponseDto;
import cloud.velo.main.controller.dto.ProjectLogSaveDto;
import cloud.velo.main.domain.Project;
import cloud.velo.main.domain.ProjectLog;
import cloud.velo.main.domain.User;
import cloud.velo.main.repository.ProjectLogRepository;
import cloud.velo.main.repository.ProjectRepository;
import cloud.velo.main.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectLogService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectLogRepository projectLogRepository;

    // 💡 기존 RedisConfig에 잡혀있는 템플릿을 그대로 주입받아 사용합니다.
    private final RedisTemplate<String, Object> redisTemplate;

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

        // 이미 공정이 끝나서 PostgreSQL 테이블에 영구 저장된 정적 로그 먼저 장착
        List<ProjectLog> dbLogs = projectLogRepository.findLogsByUserIdAndProjectId(currentUser.getId(), project.getId());
        for (ProjectLog bl : dbLogs) {
            logBuilder.append(String.format("[%s] %s\n", bl.getLogLevel(), bl.getMessage()));
        }

        // 사용자가 중간에 화면을 켰을 수도 있으므로, 현재 Redis 버퍼 리스트에 대기 중인 실시간 로그도 긁어와서 병합 진행
        String redisKey = "project:logs:" + uuid;
        List<Object> bufferedLogs = redisTemplate.opsForList().range(redisKey, 0, -1);
        if (bufferedLogs != null) {
            for (Object raw : bufferedLogs) {
                // 직렬화 안정성을 위해 "로그레벨||메시지" 문자열 규격으로 파싱합니다.
                String[] parts = ((String) raw).split("\\|\\|", 2);
                logBuilder.append(String.format("[%s] %s\n", parts[0], parts[1]));
            }
        }

        return ProjectLogResponseDto.builder()
                .uuid(project.getUuid())
                .status(project.getStatus())
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

        //  DB로 가지 않고, Redis List의 오른쪽에 로그 문자열을 1초에 수백 번씩 가볍게 꽂아 넣습니다.
        String logLine = dto.getLogLevel() + "||" + dto.getMessage();
        redisTemplate.opsForList().rightPush(redisKey, logLine);

        //  만약 FastAPI 워커가 COMPLETED 나 FAILED 신호를 보낼 시
        if (dto.getStatus() != null && (dto.getStatus().equals("COMPLETED") || dto.getStatus().equals("FAILED"))) {

            Project project = projectRepository.findByUuid(dto.getUuid())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 프로젝트 UUID: " + dto.getUuid()));
            User owner = project.getUser();

            // 1. Redis 메모리 창고에 보관 중이던 로그 전체를 원패스로 긁어옵니다.
            List<Object> rawLogs = redisTemplate.opsForList().range(redisKey, 0, -1);

            if (rawLogs != null && !rawLogs.isEmpty()) {
                List<ProjectLog> bulkLogEntities = new ArrayList<>();

                for (Object raw : rawLogs) {
                    String[] parts = ((String) raw).split("\\|\\|", 2);
                    bulkLogEntities.add(ProjectLog.builder()
                            .user(owner)
                            .project(project)
                            .logLevel(parts[0])
                            .status(dto.getStatus()) // 종결 당시의 상태 스냅샷
                            .message(parts[1])
                            .build());
                }

                // 수백 줄의 로그 엔티티들을 단 한 번의 커넥션으로 무더기 일괄 인서트(Bulk Insert) 실행
                projectLogRepository.saveAll(bulkLogEntities);
                log.info("▶ [Redis ➡️ DB Dump 완료] 총 {}개의 로그가 PostgreSQL로 일괄 덤프되었습니다.", bulkLogEntities.size());
            }

            // 2. 장부 정리가 끝났으므로 Redis에 할당되어 메모리를 차지하던 임시 큐(List)를 깔끔하게 파괴합니다.
            redisTemplate.delete(redisKey);

            // 3. 메인 프로젝트 요약 상태도 최종 종결 상태로 전이합니다.
            project.updateStatus(dto.getStatus());
        }
    }
}