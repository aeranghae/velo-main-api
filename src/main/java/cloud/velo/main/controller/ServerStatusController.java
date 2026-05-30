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
        SseEmitter emitter = new SseEmitter(1800000L);
        String id = String.valueOf(System.currentTimeMillis());

        sseEmitterManager.add(id, emitter);

        emitter.onCompletion(() -> sseEmitterManager.remove(id));
        emitter.onTimeout(() -> sseEmitterManager.remove(id));
        emitter.onError((e) -> sseEmitterManager.remove(id));

        try {
            emitter.send(SseEmitter.event()
                    .name("connect")
                    .data("SSE Connected!"));
        } catch (IOException e) {
            sseEmitterManager.remove(id);
        }

        return emitter;
    }
}