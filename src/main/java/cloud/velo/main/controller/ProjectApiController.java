package cloud.velo.main.controller;

import cloud.velo.main.controller.dto.FrameworkStatisticsResponse;
import cloud.velo.main.controller.dto.ProjectCreateRequestDto;
import cloud.velo.main.controller.dto.ProjectResponseDto;
import cloud.velo.main.controller.dto.ProjectStatusResponseDto;
import cloud.velo.main.domain.User;
import cloud.velo.main.repository.ProjectRepository;
import cloud.velo.main.repository.UserRepository;
import cloud.velo.main.service.ProjectService;
import cloud.velo.main.service.StorageService;
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
    private final ProjectRepository projectRepository;

    @Value("${velo.project.maxcount}")
    private int maxProjectGenerateCount;

    /**
     * 프로젝트 생성 시작 엔드포인트
     */
    @PostMapping("/projects/generate")
    public ResponseEntity<ProjectResponseDto> generateProject(@AuthenticationPrincipal String email,
                                                              @RequestBody ProjectCreateRequestDto requestDto) {
        // 1. 유저 조회
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("유저 정보를 찾을 수 없습니다."));

        int projectCount = projectRepository.countByUser(user);
        if (projectCount >= maxProjectGenerateCount) {
            // 최대 6개 초과 생성 불가
            return ResponseEntity.status(429)
                    .build();
        }

        // 2. 기본 폴더 및 프로젝트 엔티티 생성 (기존 기능 활용)
        // storageService에서 UUID 기반 폴더 생성 + 기본 프레임워크 파일 추가
        ProjectResponseDto newProject = storageService.createProject(user, requestDto);

        try {
            // Step 2: [컨트롤러 단계] DB 커밋이 완료되었으므로 안전하게 빈 폴더 포함 파일 색인 진행
            storageService.indexProjectFiles(newProject.getUuid());

            // TODO: [로그상태갱신] 프로젝트 트리 색인 완료

        } catch (Exception e) {
            // 디스크 색인(장부 기록) 도중 에러 발생시
            // DB와 NFS 물리 폴더를 통째로 롤백시도
            storageService.deleteProject(user, newProject.getUuid());

            // 유저와 서버 로그에 명확한 에러 원인을 전달합니다.
            throw new RuntimeException("프로젝트 초기 파일 색인에 실패하여 생성을 취소하고 롤백했습니다. UUID: " + newProject.getUuid(), e);
        }

        // 3. 생성된 폴더 내부에 상세 데이터 기반으로 자동화 공정 시작
        // 이 단계에서 FastAPI(LLM 서버)로 framework, language, prompt 등을 전송
        // 비동기라 다음 단계로 바로 넘어가게됨
        // projectService.startAutomationProcess(user, projectInfo.getUuid(), requestDto);

        return ResponseEntity.ok(newProject);
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


