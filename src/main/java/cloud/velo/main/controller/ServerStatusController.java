package cloud.velo.main.controller;

import cloud.velo.main.client.SseEmitterManager;
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
    public SseEmitter streamServerStatus() {
        // 30분 타임아웃
        SseEmitter emitter = new SseEmitter(1800000L);
        String id = String.valueOf(System.currentTimeMillis());

        // 스케줄러가 접근하기 전에 콜백부터 안전하게 등록
        emitter.onCompletion(() -> sseEmitterManager.remove(id));
        emitter.onTimeout(() -> sseEmitterManager.remove(id));
        emitter.onError((e) -> sseEmitterManager.remove(id));

        // 매니저에 추가
        sseEmitterManager.add(id, emitter);

        try {
            // 연결 확인용 더미 데이터 전송 (503 에러 방지)
            emitter.send(SseEmitter.event()
                    .name("connect")
                    .data("SSE Connected!"));
        } catch (IOException e) {
            sseEmitterManager.remove(id);
        }

        return emitter;
    }
}