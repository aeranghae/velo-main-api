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

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * 새로운 실시간 SSE 관문 등록
     */
    public void add(String id, SseEmitter emitter) {
        this.emitters.put(id, emitter);

        emitter.onCompletion(() -> {
            log.info("[SSE] 정상 마감 공정으로 세션 해제 완료. ID: {}", id);
            this.emitters.remove(id);
        });

        emitter.onTimeout(() -> {
            log.warn("[SSE] 타임아웃 오버헤드로 세션 자동 만료. ID: {}", id);
            this.emitters.remove(id);
        });

        emitter.onError(e -> {
            log.warn("[SSE] 소켓 통신 단절 장애 신호 감지. ID: {}", id);
            this.emitters.remove(id);
        });
    }

    /**
     * 전역 사용자들에게 서버 메트릭 상태를 안전하게 브로드캐스팅합니다.
     */
    public void broadcast(Object data) {
        if (emitters.isEmpty()) return;

        // ConcurrentHashMap의 안전한 요소 순회 및 조건부 제거 메커니즘 가동
        emitters.entrySet().removeIf(entry -> {
            String id = entry.getKey();
            SseEmitter emitter = entry.getValue();

            try {
                // 정상적인 녀석에겐 데이터를 안전하게 실어 보냄
                emitter.send(SseEmitter.event()
                        .name("server-status")
                        .data(data));
                return false; // 전송 성공 시 장부에서 제거하지 않음 (유지)

            } catch (IOException | IllegalStateException e) {
                log.info("[SSE-Guard] 방송 중 유령 세션 무력화 및 강제 추방 완료. ID: {}, 사유: {}", id, e.getMessage());

                try {
                    emitter.complete(); // 자원 정상 마감 시도
                } catch (Exception ignored) {}

                return true; // true를 반환하면 removeIf 규칙에 의해 ConcurrentHashMap에서 영구 삭제
            }
        });
    }
}