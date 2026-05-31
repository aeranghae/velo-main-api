package cloud.velo.main.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class SseEmitterManager {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public void add(String id, SseEmitter emitter) {
        // 혹시 모를 이전 찌꺼기 세션 청소
        if (!emitters.isEmpty()) {
            emitters.clear();
        }

        CompletableFuture.delayedExecutor(500, TimeUnit.MILLISECONDS)
                .execute(() -> {
                    try {
                        // 0.5초 사이에 클라이언트가 나가서 이미 끝난 세션인지 검사
                        emitter.send(SseEmitter.event().comment("ping"));
                        emitters.put(id, emitter);
                        log.info("[스프링] 유효한 SSE 세션 등록 성공! ID: " + id);
                    } catch (Exception e) {
                        log.info("[스프링] 등록 전 이미 종료된 세션이라 폐기합니다. ID: " + id);
                    }
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