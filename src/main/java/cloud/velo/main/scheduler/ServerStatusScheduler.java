package cloud.velo.main.scheduler;

import cloud.velo.main.client.SseEmitterManager;
import cloud.velo.main.dto.response.ServerStatusResponse;
import cloud.velo.main.service.ServerStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ServerStatusScheduler {

    private final ServerStatusService serverStatusService;
    private final SseEmitterManager sseEmitterManager;

    /**
     * 5초마다 주기적으로 전체 커넥션 세션에 서버 메트릭 상태를 실시간 송출합니다.
     */
    @Scheduled(fixedRate = 5000)
    public void broadcastServerStatus() {
        try {
            ServerStatusResponse status = serverStatusService.calculateCurrentStatus();

            sseEmitterManager.broadcast(status);

        } catch (Exception e) {
            // 스케줄러 루프 스레드가 터져서 시스템 전체가 죽는 합선을 방지하는 최종 격리 가드
            log.error("[ServerStatusScheduler] 실시간 서버 메트릭 크롤링 및 브로드캐스팅 공정 중 예외 터짐", e);
        }
    }
}