package cloud.velo.main.controller;

import cloud.velo.main.client.SseEmitterManager;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
public class ServerStatusController {

    private final SseEmitterManager sseEmitterManager;

    @GetMapping(value = "/api/server/status/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamServerStatus(HttpServletResponse response) {

        // 1. HTTP 응답 스트림이 Nginx/인프라 레이어에서 버퍼링되지 않도록 헤더 설정
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");

        // 30분 타임아웃 (1800000ms)
        SseEmitter emitter = new SseEmitter(1800000L);
        String id = String.valueOf(System.currentTimeMillis());

        // 2. 콜백 안전하게 선등록
        emitter.onCompletion(() -> sseEmitterManager.remove(id));
        emitter.onTimeout(() -> sseEmitterManager.remove(id));
        emitter.onError((e) -> sseEmitterManager.remove(id));

        try {
            // 3. 503 Service Unavailable 및 Nginx 버퍼링 차단을 위해 즉시 첫 이벤트(더미) 전송
            // 매니저에 등록(0.5초 뒤)하기 전에 최초 연결 수신을 여기서 강제로 밀어 넣어 버퍼를 뚫습니다.
            emitter.send(SseEmitter.event()
                    .name("connect")
                    .data("SSE Connected!"));
        } catch (IOException e) {
            return emitter;
        }

        // 4. 최초 연결 수신이 성공하면 비동기 매니저에 추가 (스케줄러 방송용)
        sseEmitterManager.add(id, emitter);

        return emitter;
    }
}

