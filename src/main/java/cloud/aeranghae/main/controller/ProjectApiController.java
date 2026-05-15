package cloud.aeranghae.main.controller;

import cloud.aeranghae.main.controller.dto.ProjectCreateRequestDto;
import cloud.aeranghae.main.controller.dto.ProjectResponseDto;
import cloud.aeranghae.main.controller.dto.ProjectStatusResponseDto;
import cloud.aeranghae.main.domain.User;
import cloud.aeranghae.main.repository.UserRepository;
import cloud.aeranghae.main.service.ProjectService;
import cloud.aeranghae.main.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/project")
@RequiredArgsConstructor
public class ProjectApiController {

    private final StorageService storageService;
    private final ProjectService projectService;
    private final UserRepository userRepository;

    /**
     * 프로젝트 생성 시작 엔드포인트
     */
    @PostMapping("/projects/generate")
    public ResponseEntity<ProjectResponseDto> generateProject(@AuthenticationPrincipal String email,
                                                              @RequestBody ProjectCreateRequestDto requestDto) {
        // 1. 유저 조회
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("유저 정보를 찾을 수 없습니다."));

        // 2. 기본 폴더 및 프로젝트 엔티티 생성 (기존 기능 활용)
        // storageService에서 UUID 기반 폴더 생성 + 기본 프레임워크 파일 추가
        ProjectResponseDto projectInfo = storageService.createProject(user, requestDto);

        // 3. 생성된 폴더 내부에 상세 데이터 기반으로 자동화 공정 시작
        // 이 단계에서 FastAPI(LLM 서버)로 framework, language, prompt 등을 전송
        // 비동기라 다음 단계로 바로 넘어가게됨
        // projectService.startAutomationProcess(user, projectInfo.getUuid(), requestDto);

        return ResponseEntity.ok(projectInfo);
    }

    /**
     * 프로젝트 생성 상태 조회 (SSE 또는 폴링 방식 대비)
     */
    @GetMapping("/{projectId}/status")
    public ResponseEntity<ProjectStatusResponseDto> getProjectStatus(@PathVariable String projectId) {
        // 프로젝트가 현재 몇 % 진행되었는지, 혹은 완료되었는지 상태 반환 - 임시코드
        return ResponseEntity.ok(projectService.checkStatus(projectId));
    }
}