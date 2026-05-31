package cloud.velo.main.client;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class SseEmitterManager {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public void add(String id, SseEmitter emitter) {
        // [핵심 튜닝] 0.5초(500ms) 지연 후 안전하게 emitters 맵에 추가합니다.
        // 이 처리를 통해 Controller가 return을 무사히 마치고 톰캣 연결이 생성됩니다.
        CompletableFuture.delayedExecutor(500, TimeUnit.MILLISECONDS)
                .execute(() -> {
                    emitters.put(id, emitter);
                });
    }

    public void remove(String id) {
        emitters.remove(id);
    }

    public boolean isEmpty() {
        return emitters.isEmpty();
    }

    public void broadcast(SseEmitter.SseEventBuilder event) {
        List<String> deadEmitters = new ArrayList<>();

        emitters.forEach((id, emitter) -> {
            try {
                emitter.send(event);
            } catch (Exception e) {
                deadEmitters.add(id);
            }
        });

        if (!deadEmitters.isEmpty()) {
            deadEmitters.forEach(emitters::remove);
        }
    }
}