package cloud.velo.main.controller;

import cloud.velo.main.controller.dto.ProjectLogResponseDto;
import cloud.velo.main.domain.Project;
import cloud.velo.main.service.ProjectLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import cloud.velo.main.repository.ProjectRepository;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectLogController {

    private final ProjectLogService projectLogService;
    private final ProjectRepository projectRepository;

    /**
     * 클라이언트(리액트) 전용 로그 조회 API
     */
    @GetMapping("/{uuid}/status")
    public ResponseEntity<ProjectLogResponseDto> getProjectLogs(
            @PathVariable("uuid") String uuid,
            @AuthenticationPrincipal String email) {

        if (!isValidUser(email)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        ProjectLogResponseDto response = projectLogService.getProjectMetadataAndLogs(uuid, email);
        return ResponseEntity.ok(response);
    }

    /**
     * 리액트 SSE 파이프라인 수신용 창구
     * Nginx 등의 프록시 환경에서 데이터가 고이지 않도록 필수 헤더를 명시적으로 빌드합니다.
     */
    @GetMapping(value = "/{uuid}/logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> streamProjectLogs(
            @PathVariable("uuid") String uuid,
            @AuthenticationPrincipal String email) {

        // 🛡️ [안전 가드] 무인증 유저 및 스프링 시큐리티 익명 객체 분쇄
        if (!isValidUser(email)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        SseEmitter emitter = projectLogService.createSseConnection(uuid, email);

        // HTTP 스트리밍 표준 헤더 강제 주입
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header("X-Accel-Buffering", "no") // Nginx 버퍼링 해제 (핵심)
                .cacheControl(CacheControl.noCache())
                .body(emitter);
    }

    /**
     * Spring Security 익명 토큰 문자열 검증용 헬퍼 메서드
     */
    private boolean isValidUser(String email) {
        return StringUtils.hasText(email) && !"anonymousUser".equals(email);
    }
}