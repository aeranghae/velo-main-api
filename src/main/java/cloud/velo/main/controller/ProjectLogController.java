package cloud.velo.main.controller;

import cloud.velo.main.controller.dto.ProjectLogResponseDto;
import cloud.velo.main.controller.dto.ProjectLogSaveDto;
import cloud.velo.main.service.ProjectLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
     * 보안을 위해 실제 로그인한 사용자의 이메일을 필수로 전달받아야 합니다.
     */
    @GetMapping("/{uuid}/status")
    public ResponseEntity<ProjectLogResponseDto> getProjectLogs(
            @PathVariable("uuid") String uuid,
            @RequestParam("loginEmail") String loginEmail) {

        ProjectLogResponseDto response = projectLogService.getProjectMetadataAndLogs(uuid, loginEmail);
        return ResponseEntity.ok(response);
    }
    @GetMapping(value = "/{uuid}/logs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamProjectLogs(
            @PathVariable("uuid") String uuid,
            @RequestParam("loginEmail") String loginEmail) {
        return projectLogService.createSseConnection(uuid, loginEmail);
    }
}