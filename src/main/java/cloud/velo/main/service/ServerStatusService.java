package cloud.velo.main.service;

import cloud.velo.main.client.SseEmitterManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
@Service
public class ServerStatusService {

    private final SseEmitterManager sseEmitterManager;
    private static final Long SSE_TIMEOUT = 1800000L; // 30분

    public SseEmitter createStatusStream() throws IOException {
        String id = String.valueOf(System.currentTimeMillis());

        // 1. Emitter 생성 및 콜백 선등록
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        emitter.onCompletion(() -> sseEmitterManager.remove(id));
        emitter.onTimeout(() -> sseEmitterManager.remove(id));
        emitter.onError((e) -> sseEmitterManager.remove(id));

        // 2. 더미 이벤트를 전송하여 Nginx 버퍼링 및 503 에러 원천 차단
        // IOException을 삼키지 않고 상위로 throws하여 연결 실패 시 자원이 꼬이지 않도록 합니다.
        emitter.send(SseEmitter.event()
                .name("connect")
                .data("SSE Connected!"));

        // 3. 최초 연결이 완벽하게 성공했을 때만 비동기 매니저에 최종 등록
        sseEmitterManager.add(id, emitter);

        return emitter;
    }
}