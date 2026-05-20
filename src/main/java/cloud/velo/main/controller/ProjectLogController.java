package cloud.velo.main.controller;

import cloud.velo.main.controller.dto.ProjectLogResponseDto;
import cloud.velo.main.service.ProjectLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import cloud.velo.main.config.auth.JwtTokenProvider;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectLogController {

    private final ProjectLogService projectLogService;
    private final JwtTokenProvider jwtTokenProvider;

    /*
     * 클라이언트(리액트) 전용 로그 조회 API
     * 사용자가 특정 프로젝트의 상세 페이지에 접속했을 때 누적된 로그와 상태를 반환합니다.
     * 보안을 위해 Spring Security의 인증 객체(@AuthenticationPrincipal)에서 로그인한 사용자의 이메일을 자동 추출합니다.
     */
    @GetMapping("/{uuid}/status")
    public ResponseEntity<ProjectLogResponseDto> getProjectLogs(
            @PathVariable("uuid") String uuid,
            @AuthenticationPrincipal UserDetails userDetails) {

        ProjectLogResponseDto response = projectLogService.getProjectMetadataAndLogs(uuid, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }
    @GetMapping(value = "/{uuid}/logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamProjectLogs(
            @PathVariable("uuid") String uuid,
            @RequestParam("token") String token) {
        if (token == null || !jwtTokenProvider.validateToken(token)) {
            throw new IllegalArgumentException("유효하지 않거나 만료된 토큰입니다.");
        }

        // 2. 진짜 토큰이 맞으면 안에 들어있는 이메일을 안전하게 꺼냄
        String email = jwtTokenProvider.getUserEmail(token);

        // 3. 추출한 이메일로 SSE 연결 생성
        return projectLogService.createSseConnection(uuid, email);
    }
}