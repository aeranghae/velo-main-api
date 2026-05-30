package cloud.velo.main.controller;

import cloud.velo.main.dto.response.ProjectLogResponse;
import cloud.velo.main.service.ProjectLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectLogController {

    private final ProjectLogService projectLogService;

    /**
     * 클라이언트(리액트) 전용 로그 조회 API
     * 시큐리티 가드 덕분에 email은 무조건 정식 인증 유저임이 보장됩니다.
     */
    @GetMapping("/{uuid}/status")
    public ResponseEntity<ProjectLogResponse> getProjectLogs(
            @PathVariable String uuid,
            @AuthenticationPrincipal String email) {

        ProjectLogResponse response = projectLogService.getProjectMetadataAndLogs(uuid, email);
        return ResponseEntity.ok(response);
    }

    /**
     * 리액트 SSE 파이프라인 수신용 창구
     * Nginx 등 리버스 프록시 환경에서 데이터가 병목 없이 실시간으로 밀려 나가도록 필수 헤더를 유지합니다.
     */
    @GetMapping(value = "/{uuid}/logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> streamProjectLogs(
            @PathVariable String uuid,
            @AuthenticationPrincipal String email) {

        SseEmitter emitter = projectLogService.createSseConnection(uuid, email);

        // HTTP 스트리밍 표준 헤더 강제 주입 (실시간성 확보)
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header("X-Accel-Buffering", "no") // Nginx 실시간 스트리밍 필수 옵션
                .cacheControl(CacheControl.noCache())
                .body(emitter);
    }
}