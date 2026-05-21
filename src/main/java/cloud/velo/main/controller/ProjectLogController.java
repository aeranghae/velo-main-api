package cloud.velo.main.controller;

import cloud.velo.main.controller.dto.ProjectLogResponseDto;
import cloud.velo.main.service.ProjectLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectLogController {

    private final ProjectLogService projectLogService;

    /*
     * 클라이언트(리액트) 전용 로그 조회 API
     * 사용자가 특정 프로젝트의 상세 페이지에 접속했을 때 누적된 로그와 상태를 반환합니다.
     * 보안을 위해 Spring Security의 인증 객체(@AuthenticationPrincipal)에서 로그인한 사용자의 이메일을 자동 추출합니다.
     */
    @GetMapping("/{uuid}/status")
    public ResponseEntity<ProjectLogResponseDto> getProjectLogs(
            @PathVariable("uuid") String uuid,
            @AuthenticationPrincipal String email) {

        ProjectLogResponseDto response = projectLogService.getProjectMetadataAndLogs(uuid, email);
        return ResponseEntity.ok(response);
    }
    @GetMapping(value = "/{uuid}/logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> streamProjectLogs(
            @PathVariable("uuid") String uuid,
            @AuthenticationPrincipal String email) {
        // 🛡️ [안전 가드] 무인증 유저의 좀비 무한 루프 접속 차단
        if (email == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // 3. 추출한 이메일로 SSE 연결 생성
        SseEmitter emitter = projectLogService.createSseConnection(uuid, email);
        return ResponseEntity.ok(emitter);
    }
}