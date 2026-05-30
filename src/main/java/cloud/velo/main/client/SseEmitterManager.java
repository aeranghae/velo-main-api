package cloud.velo.main.client;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SseEmitterManager {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public void add(String id, SseEmitter emitter) {
        emitters.put(id, emitter);
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

        deadEmitters.forEach(emitters::remove);
    }
}