package cloud.velo.main.scheduler;

import cloud.velo.main.client.SseEmitterManager;
import cloud.velo.main.dto.response.ServerStatusResponse;
import com.sun.management.OperatingSystemMXBean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;

@Slf4j
@Component
@RequiredArgsConstructor
public class ServerStatusScheduler {

    private final SseEmitterManager sseEmitterManager;

    @Scheduled(fixedRate = 5000)
    public void broadcastServerStatus() {
        if (sseEmitterManager.isEmpty()) return;

        Runtime runtime = Runtime.getRuntime();
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        OperatingSystemMXBean osMXBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        double cpuUsage = Math.max(0.0, osMXBean.getCpuLoad() * 100);

        ServerStatusResponse status = ServerStatusResponse.builder()
                .status("UP")
                .uptime(runtimeMXBean.getUptime())
                .cpuUsage(Math.round(cpuUsage * 100.0) / 100.0)
                .totalMemory(totalMemory)
                .freeMemory(freeMemory)
                .usedMemory(totalMemory - freeMemory)
                .build();

        // MediaType.APPLICATION_JSON 을 명시 (직렬화)
        sseEmitterManager.broadcast(
                SseEmitter.event()
                        .name("server-status")
                        .data(status, org.springframework.http.MediaType.APPLICATION_JSON)
        );
    }
}