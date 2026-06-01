package cloud.velo.main.controller;

import cloud.velo.main.service.ServerStatusService;
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

    private final ServerStatusService serverStatusService;

    @GetMapping(value = "/api/server/status/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamServerStatus(HttpServletResponse response) {

        // HTTP 응답 스트림이 인프라 레이어(Nginx 등)에서 버퍼링되지 않도록 규격 헤더 설정 (Web 영역의 책임)
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");

        return serverStatusService.createStatusStream();
    }
}