package cloud.velo.main.scheduler;

import cloud.velo.main.client.SseEmitterManager;
import cloud.velo.main.dto.response.ServerStatusResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.lang.management.ManagementFactory;

@Slf4j
@Component
@RequiredArgsConstructor
public class ServerStatusScheduler {

    private final SseEmitterManager sseEmitterManager;

    @Scheduled(fixedRate = 5000)
    public void broadcastServerStatus() {
        if (sseEmitterManager.isEmpty()) return;

        try {
            // 1. 쿠버네티스 환경에서도 안전한 JVM 런타임 메모리 계산
            long maxMemory = Runtime.getRuntime().maxMemory(); // 컨테이너 제한이 적용된 최대 메모리
            long totalMemory = Runtime.getRuntime().totalMemory(); // 현재 JVM이 차지한 메모리
            long freeMemory = Runtime.getRuntime().freeMemory(); // 차지한 메모리 중 남은 메모리

            // 실제 앱이 사용 중인 순수 메모리 가용량 계산
            long usedMemory = totalMemory - freeMemory;

            // 2. 호스트 OS 비종속적인 안전한 CPU Load 계산
            // (Com.sun.management 대신 가상 스레드/프로세스 수치 활용 혹은 고정값 방어)
            double cpuUsage = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
            if (cpuUsage < 0) {
                cpuUsage = 0.0; // 윈도우 환경 등에서 음수 반환 방어
            } else {
                // 단일 컨테이너 내부의 대략적인 코어 사용률로 보정
                cpuUsage = Math.min(100.0, cpuUsage * 100.0);
            }

            // 가동 시간 (Uptime)
            long uptime = ManagementFactory.getRuntimeMXBean().getUptime();

            ServerStatusResponse status = ServerStatusResponse.builder()
                    .status("UP")
                    .uptime(uptime)
                    .cpuUsage(Math.round(cpuUsage * 100.0) / 100.0)
                    .totalMemory(maxMemory) // 호스트 메모리 대신 컨테이너 최대 메모리를 분모로 설정
                    .freeMemory(maxMemory - usedMemory)
                    .usedMemory(usedMemory)
                    .build();

            sseEmitterManager.broadcast(
                    SseEmitter.event()
                            .name("server-status")
                            .data(status, org.springframework.http.MediaType.APPLICATION_JSON)
            );

            log.info("[스케줄러] 전송 성공 - CPU: {}%, RAM: {} bytes", status.getCpuUsage(), status.getUsedMemory());
        } catch (Exception e) {
            log.error("⚠ 쿠버네티스 메트릭 수집 중 예외 발생: ", e);
        }
    }
}