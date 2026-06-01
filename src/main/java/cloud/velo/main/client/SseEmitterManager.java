package cloud.velo.main.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class SseEmitterManager {

    // 동시성 멀티스레드 환경을 위한 안전한 맵 구조 유지
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public void add(String id, SseEmitter emitter) {
        try {
            emitters.put(id, emitter);
            log.info("[SSE] 유효한 SSE 세션 등록 성공! ID: {}", id);
        } catch (Exception e) {
            log.error("[SSE] 세션 등록 중 알 수 없는 에러 발생 ID: {}", id, e);
        }
    }

    public void remove(String id) {
        SseEmitter removed = emitters.remove(id);
        if (removed != null) {
            log.info("[SSE] 세션이 정상적으로 제거되었습니다. ID: {}", id);
        }
    }

    public boolean isEmpty() {
        return emitters.isEmpty();
    }

    /**
     * 전체 클라이언트에게 실시간 상태 방송
     */
    public void broadcast(SseEmitter.SseEventBuilder event) {
        if (emitters.isEmpty()) {
            return;
        }

        emitters.entrySet().removeIf(entry -> {
            try {
                entry.getValue().send(event);
                return false;
            } catch (IOException e) {
                // 네트워크 단절은 예상된 '정상 탈락'이므로 true를 반환해 맵에서 지웁니다.
                return true;
            } catch (Exception e) {
                // 네트워크 단절이 아닌 시스템 합선 등 '치명적 에러'는 삼키지 않고 언체크 예외로 감싸 던집니다!
                throw new IllegalStateException("[SSE] 브로드캐스팅 중 예측하지 못한 치명적 오류 발생", e);
            }
        });
    }
}