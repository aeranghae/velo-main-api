package cloud.velo.main.scheduler;

import cloud.velo.main.client.SseEmitterManager;
import cloud.velo.main.dto.response.ServerStatusResponse;
import cloud.velo.main.service.ServerStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Component
@RequiredArgsConstructor
public class ServerStatusScheduler {

    private final SseEmitterManager sseEmitterManager;
    private final ServerStatusService serverStatusService;

    @Scheduled(fixedRate = 5000)
    public void broadcastServerStatus() {
        // 방송 수신기가 없으면 연산조차 하지 않고 즉시 리턴 (자원 최적화)
        if (sseEmitterManager.isEmpty()) {
            return;
        }

        try {
            // 1. 복잡한 JVM 메트릭 수집 및 가공은 서비스에 위임
            ServerStatusResponse status = serverStatusService.calculateCurrentStatus();

            // 2. 매니저를 통해 안전하게 전체 브로드캐스팅 수행
            sseEmitterManager.broadcast(
                    SseEmitter.event()
                            .name("server-status")
                            .data(status, MediaType.APPLICATION_JSON)
            );

        } catch (Exception e) {
            // 스케줄러의 예외는 웹 어드바이스가 잡지 못하므로 시스템 로깅 인프라에 명확히 기록하거나
            // 모니터링 컴포넌트를 호출하여 슬랙 등으로 유도해야 합니다.
            log.error("[스케줄러 메트릭 수집 장애] 백엔드 시스템 메트릭 수집 중 예외 발생: ", e);
        }
    }
}