package cloud.velo.main.controller;

import cloud.velo.main.controller.dto.FrameworkStatisticsResponse;
import cloud.velo.main.controller.dto.ProjectCreateRequestDto;
import cloud.velo.main.controller.dto.ProjectNodeResponse;
import cloud.velo.main.controller.dto.ProjectResponseDto;
import cloud.velo.main.domain.Project;
import cloud.velo.main.domain.User;
import cloud.velo.main.repository.ProjectRepository;
import cloud.velo.main.repository.UserRepository;
import cloud.velo.main.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/storage")
@RequiredArgsConstructor
public class StorageApiController {

    private final StorageService storageService;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;

    // 생성 - - - - - - - - - - - - - - - - - - - -  - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // 2. 새 프로젝트 생성 (UUID 기반)
    @PostMapping("/projects")
    public ResponseEntity<ProjectResponseDto> createProject(@AuthenticationPrincipal String email,
                                                            @RequestBody Map<String, String> request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("유저 없음"));

        // TODO: 나중에 DTO로 받도록 수정해야 겠습니다.
        ProjectCreateRequestDto requestDto = new ProjectCreateRequestDto();
        requestDto.setProjectName(request.get("projectName"));
        requestDto.setModel(request.get("model"));
        requestDto.setFramework(request.get("framework"));

        if (requestDto.getProjectName() == null || requestDto.getProjectName().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        // Step 1: DB 메타데이터 저장 및 NFS 베이스 템플릿 복사 완료 (1차 트랜잭션 종료)
        ProjectResponseDto newProject = storageService.createProject(user, requestDto);

        try {
            // Step 2: [컨트롤러 단계] DB 커밋이 완료되었으므로 안전하게 빈 폴더 포함 파일 색인 진행
            storageService.indexProjectFiles(newProject.getUuid());

            // TODO: [로그상태갱신] 프로젝트 트리 색인 완료

        } catch (Exception e) {
            // [방어선 작동]: 디스크 색인(장부 기록) 도중 에러 발생시
            // DB와 NFS 물리 폴더를 통째로 롤백시도
            storageService.deleteProject(user, newProject.getUuid());

            // 유저와 서버 로그에 명확한 에러 원인을 전달합니다.
            throw new RuntimeException("프로젝트 초기 파일 색인에 실패하여 생성을 취소하고 롤백했습니다. UUID: " + newProject.getUuid(), e);
        }

        // 색인까지 무사히 완수되었을 때만 안전하게 200 OK와 함께 DTO를 리턴합니다.
        return ResponseEntity.ok(newProject);
    }

    // 조회 - - - - - - - - - - - - - - - - - - - -  - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // 개인 저장소 사용량 반환
    @GetMapping("/usage")
    public ResponseEntity<Map<String, Object>> getUsage(@AuthenticationPrincipal String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("유저 없음"));

        // 서비스 레이어에 만들어두신 깔끔한 비즈니스 메서드를 호출합니다.
        long usageBytes = storageService.getUserTotalStorageUsage(user);

        Map<String, Object> response = new HashMap<>();
        response.put("usageBytes", usageBytes);
        response.put("usageMB", String.format("%.2f", (double) usageBytes / (1024 * 1024)));

        return ResponseEntity.ok(response);
    }

    // 개인 저장소 내 프로젝트 폴더 리스트 반환 (상세 정보 포함)
    @GetMapping("/projects")
    public ResponseEntity<List<ProjectResponseDto>> getProjectList(@AuthenticationPrincipal String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("유저 없음"));

        // 변경된 서비스 로직에 맞춰 User 객체를 직접 전달
        List<ProjectResponseDto> projects = storageService.getUserProjectDetails(user);

        return ResponseEntity.ok(projects);
    }

    // 개인 저장소 내 프로젝트 폴더 리스트 반환 (상세 정보 포함)
    @GetMapping("/projects/{uuid}/update")
    public ResponseEntity<ProjectResponseDto> updateProjectDetail(
            @PathVariable String uuid,
            @AuthenticationPrincipal String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("유저 없음"));

        ProjectResponseDto projectDetail = storageService.updateUserProjectDetails(uuid, user);

        return ResponseEntity.ok(projectDetail);
    }

    // 프로젝트 파일 트리 구조 조회
    // URL 예시: GET /api/storage/projects/v-uuid-123/tree
    @GetMapping("/projects/{uuid}/tree")
    public ResponseEntity<List<ProjectNodeResponse>> getProjectTree(@AuthenticationPrincipal String email,
                                                                    @PathVariable String uuid) {
        // 보안 검증을 위해 이메일 정보와 UUID를 서비스로 함께 넘깁니다.
        List<ProjectNodeResponse> tree = storageService.getProjectTree(email, uuid);
        return ResponseEntity.ok(tree);
    }

    // 특정 파일 내용 실시간 조회
    // URL 예시: GET /api/storage/projects/v-uuid-123/file-content?path=src/MainLogic.java
    @GetMapping("/projects/{uuid}/file-content")
    public ResponseEntity<String> getFileContent(@AuthenticationPrincipal String email,
                                                 @PathVariable String uuid,
                                                 @RequestParam String path) {
        // 예: src/MainLogic.java
        // NFS 파일 스트림을 열어 텍스트를 긁어오는 작업도 서비스가 처리하도록 패스합니다.
        String content = storageService.getFileContent(email, uuid, path);
        return ResponseEntity.ok(content);
    }

    // 프레임워크 통계
    @GetMapping("/projects/framework/statistics")
    public ResponseEntity<FrameworkStatisticsResponse> getFrameworkStatistics(@AuthenticationPrincipal String email) {
        FrameworkStatisticsResponse stats = storageService.getFrameworkStatistics(email);
        return ResponseEntity.ok(stats);
    }



    // 수정 - - - - - - - - - - - - - - - - - - - -  - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    // 프로젝트 설명 변경
    @PatchMapping("/projects/{uuid}/description")
    public ResponseEntity<ProjectResponseDto> updateProjectDescription(@AuthenticationPrincipal String email,
                                                                       @PathVariable String uuid,
                                                                       @RequestBody Map<String, String> request) {
        User user = userRepository.findByEmail(email).orElseThrow();
        String description = request.get("description");

        return ResponseEntity.ok(storageService.updateProjectDescription(user, uuid, description));
    }

    // 프로젝트 이름 변경
    @PatchMapping("/projects/{uuid}")
    public ResponseEntity<ProjectResponseDto> updateProject(@AuthenticationPrincipal String email,
                                                            @PathVariable String uuid,
                                                            @RequestBody Map<String, String> request) {
        User user = userRepository.findByEmail(email).orElseThrow();
        String newName = request.get("newName");

        return ResponseEntity.ok(storageService.updateProjectName(user, uuid, newName));
    }

    // 프로젝트 삭제
    @DeleteMapping("/projects/{uuid}")
    public ResponseEntity<Void> deleteProject(@AuthenticationPrincipal String email,
                                              @PathVariable String uuid) {
        User user = userRepository.findByEmail(email).orElseThrow();
        storageService.deleteProject(user, uuid);
        return ResponseEntity.noContent().build();
    }

    // 프로젝트 다운
    @GetMapping("/{uuid}/download")
    public ResponseEntity<byte[]> downloadProject(
            @AuthenticationPrincipal String email,
            @PathVariable String uuid
    ) {
        User user = userRepository.findByEmail(email).orElseThrow();
        Project project = projectRepository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 프로젝트입니다. UUID: " + uuid));

        // 1. 서비스 레이어를 호출하여 압축된 파일의 바이너리 데이터(byte[])를 가져옴
        byte[] zipData = storageService.downloadProject(user, uuid);

        // 2. HTTP 응답 헤더 설정
        HttpHeaders headers = new HttpHeaders();

        // 응답 타입을 zip 파일로 명시
        headers.setContentType(MediaType.parseMediaType("application/zip"));

        // 클라이언트가 진행률을 계산할 수 있도록 전체 파일 크기(바이트 수)를 지정합니다.
        headers.setContentLength(zipData.length);

        // 모든 종류의 공백(\\s)을 찾아 빈 문자열("")로 대체합니다.
        String zipName = project.getName().replaceAll("\\s+", "") + ".zip";

        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(zipName, StandardCharsets.UTF_8)
                .build();
        headers.setContentDisposition(contentDisposition);

        // 3. 데이터, 헤더, HTTP 상태 코드를 ResponseEntity에 담아서 반환
        return new ResponseEntity<>(zipData, headers, HttpStatus.OK);
    }
}