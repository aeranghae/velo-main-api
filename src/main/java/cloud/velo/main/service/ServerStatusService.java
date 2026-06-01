package cloud.velo.main.service;

import cloud.velo.main.client.SseEmitterManager;
import cloud.velo.main.dto.response.ServerStatusResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.lang.management.ManagementFactory;

@Slf4j
@RequiredArgsConstructor
@Service
public class ServerStatusService {

    private final SseEmitterManager sseEmitterManager;
    private static final Long SSE_TIMEOUT = 1800000L; // 30분

    public SseEmitter createStatusStream() {
        String id = String.valueOf(System.currentTimeMillis());

        // Emitter 순수 생성
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        // 비동기 매니저에 먼저 등록합니다.
        // 이제 onCompletion, onTimeout, onError 같은 생명주기 콜백은
        // sseEmitterManager.add() 안에서 가드
        sseEmitterManager.add(id, emitter);

        try {
            // 3. 더미 이벤트를 전송하여 Nginx 버퍼링 및 503 에러 원천 차단
            emitter.send(SseEmitter.event()
                    .name("connect")
                    .data("SSE Connected!"));

            log.info("[SSE] 실시간 서버 메트릭 관문 개통 성공. ID: {}", id);

        } catch (IOException | IllegalStateException e) {
            // 4. [보안 가드] 최초 연결 송출 중 브라우저가 깨지거나 I/O 에러 발생 시 포장 격발
            log.error("[SSE] 최초 연결 더미 이벤트 전송 중 물리적 I/O 에러 발생: {}", e.getMessage());
            throw new IllegalStateException("실시간 서버 상태 스트림 연결에 실패했습니다.", e);
        }

        return emitter;
    }

    public ServerStatusResponse calculateCurrentStatus() {
        // 1. JVM 컨테이너 가용 메모리 계산
        long maxMemory = Runtime.getRuntime().maxMemory();
        long totalMemory = Runtime.getRuntime().totalMemory();
        long freeMemory = Runtime.getRuntime().freeMemory();
        long usedMemory = totalMemory - freeMemory;

        // 2. 호스트 비종속적 CPU Load 계산 및 보정
        double cpuUsage = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
        if (cpuUsage < 0) {
            cpuUsage = 0.0;
        } else {
            cpuUsage = Math.min(100.0, cpuUsage * 100.0);
        }

        long uptime = ManagementFactory.getRuntimeMXBean().getUptime();

        return ServerStatusResponse.builder()
                .status("UP")
                .uptime(uptime)
                .cpuUsage(Math.round(cpuUsage * 100.0) / 100.0)
                .totalMemory(maxMemory)
                .freeMemory(maxMemory - usedMemory)
                .usedMemory(usedMemory)
                .build();
    }
}