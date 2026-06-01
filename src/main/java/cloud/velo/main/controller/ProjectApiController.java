package cloud.velo.main.controller;

import cloud.velo.main.dto.request.ProjectCreateRequest;
import cloud.velo.main.dto.response.ProjectResponse;
import cloud.velo.main.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/project")
@RequiredArgsConstructor
public class ProjectApiController {

    private final ProjectService projectService;

    /**
     * 프로젝트 코드 생성 시작 엔드포인트
     */
    @PostMapping("/projects/generate")
    public ResponseEntity<ProjectResponse> generateProject(@AuthenticationPrincipal String email,
                                                           @Valid @RequestBody ProjectCreateRequest requestDto) {

        ProjectResponse response = projectService.generateAndAutomationPipeline(email, requestDto);
        return ResponseEntity.ok(response);
    }
}