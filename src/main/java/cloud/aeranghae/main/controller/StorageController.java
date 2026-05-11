package cloud.aeranghae.main.controller;

import cloud.aeranghae.main.controller.dto.ProjectResponseDto;
import cloud.aeranghae.main.domain.User;
import cloud.aeranghae.main.repository.UserRepository;
import cloud.aeranghae.main.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/storage")
@RequiredArgsConstructor
public class StorageController {

    private final StorageService storageService;
    private final UserRepository userRepository;

    // 1. 개인 저장소 사용량 반환
    @GetMapping("/usage")
    public ResponseEntity<Map<String, Object>> getUsage(@AuthenticationPrincipal String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("유저 없음"));

        // 유저 PK(ID) 폴더 전체의 용량을 계산
        long usageBytes = storageService.calculateDirectorySize(
                java.nio.file.Paths.get(String.valueOf(user.getId())) // StorageService 내부에서 baseStoragePath와 결합됨
        );

        Map<String, Object> response = new HashMap<>();
        response.put("usageBytes", usageBytes);
        response.put("usageMB", String.format("%.2f", (double) usageBytes / (1024 * 1024)));

        return ResponseEntity.ok(response);
    }

    // 2. 새 프로젝트 생성 (UUID 기반)
    @PostMapping("/projects")
    public ResponseEntity<ProjectResponseDto> createProject(@AuthenticationPrincipal String email,
                                                            @RequestBody Map<String, String> request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("유저 없음"));

        String projectName = request.get("projectName"); // 리액트에서 보낸 프로젝트 이름

        if (projectName == null || projectName.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        ProjectResponseDto newProject = storageService.createProject(user, projectName);
        return ResponseEntity.ok(newProject);
    }

    // 3. 개인 저장소 내 프로젝트 폴더 리스트 반환 (상세 정보 포함)
    @GetMapping("/projects")
    public ResponseEntity<List<ProjectResponseDto>> getProjectList(@AuthenticationPrincipal String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("유저 없음"));

        // 변경된 서비스 로직에 맞춰 User 객체를 직접 전달
        List<ProjectResponseDto> projects = storageService.getUserProjectDetails(user);

        return ResponseEntity.ok(projects);
    }

    // 4. 프로젝트 이름 변경
    @PatchMapping("/projects/{uuid}")
    public ResponseEntity<ProjectResponseDto> updateProject(@AuthenticationPrincipal String email,
                                                            @PathVariable String uuid,
                                                            @RequestBody Map<String, String> request) {
        User user = userRepository.findByEmail(email).orElseThrow();
        String newName = request.get("newName");

        return ResponseEntity.ok(storageService.updateProjectName(user, uuid, newName));
    }

    // 5. 프로젝트 삭제
    @DeleteMapping("/projects/{uuid}")
    public ResponseEntity<Void> deleteProject(@AuthenticationPrincipal String email,
                                              @PathVariable String uuid) {
        User user = userRepository.findByEmail(email).orElseThrow();
        storageService.deleteProject(user, uuid);
        return ResponseEntity.noContent().build();
    }
}