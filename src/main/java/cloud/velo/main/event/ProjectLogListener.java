package cloud.velo.main.event;

import cloud.velo.main.dto.request.ProjectLogSaveRequest;
import cloud.velo.main.service.ProjectLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectLogListener {

    private final ProjectLogService projectLogService;
    @Async
    @EventListener
    public void handleProjectLogEvent(ProjectLogEvent event) {
        try {
            ProjectLogSaveRequest logDto = new ProjectLogSaveRequest();
            logDto.setUuid(event.uuid());
            logDto.setLogLevel(event.logLevel());
            logDto.setMessage(event.message());
            logDto.setStatus(event.status().name());

            // Redis 적재 및 SSE 브로드캐스팅이 안전하게 실행
            projectLogService.saveWorkerLog(logDto);
        } catch (Exception e) {
            log.error("[이벤트 리스너] 비동기 로그 저장 및 SSE 전송 중 오류 발생 - UUID: {}", event.uuid(), e);
        }
    }
}