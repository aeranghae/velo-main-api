package cloud.velo.main.controller;

import cloud.velo.main.dto.request.ProjectCreateRequest;
import cloud.velo.main.dto.response.*;
import cloud.velo.main.service.StorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/storage")
@RequiredArgsConstructor
public class StorageApiController {

    private final StorageService storageService;

    // 생성 - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    /**
     * 2. 새 프로젝트 생성 (UUID 기반)
     */
    @PostMapping("/projects")
    public ResponseEntity<ProjectResponse> createProject(@AuthenticationPrincipal String email,
                                                         @Valid @RequestBody ProjectCreateRequest requestDto) {

        // 서비스 한 줄로 생성 + 파일 색인을 통째로 원자적 트랜잭션 울타리로 위임합니다.
        ProjectResponse newProject = storageService.createAndIndexProject(email, requestDto);
        return ResponseEntity.ok(newProject);
    }

    // 조회 - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    /**
     * 개인 저장소 사용량 반환
     */
    @GetMapping("/usage")
    public ResponseEntity<StorageUsageResponse> getUsage(@AuthenticationPrincipal String email) {
        // 맵 대신 가공 연산이 완비된 순수 DTO 상자로 토스받습니다.
        StorageUsageResponse usage = storageService.getUserStorageUsage(email);
        return ResponseEntity.ok(usage);
    }

    /**
     * 개인 저장소 내 프로젝트 폴더 리스트 반환 (상세 정보 포함)
     */
    @GetMapping("/projects")
    public ResponseEntity<List<ProjectResponse>> getProjectList(@AuthenticationPrincipal String email) {

        UserProjectListResponse response = storageService.getUserProjectDetailsByEmail(email);
        return ResponseEntity.ok(response.getProjects());
    }

    /**
     * 프로젝트 상세 정보 실시간 갱신 및 반환
     */
    @GetMapping("/projects/{uuid}/update")
    public ResponseEntity<ProjectResponse> updateProjectDetail(@PathVariable String uuid,
                                                               @AuthenticationPrincipal String email) {
        ProjectResponse projectDetail = storageService.updateUserProjectDetailsByEmail(uuid, email);
        return ResponseEntity.ok(projectDetail);
    }

    /**
     * 프로젝트 파일 트리 구조 조회
     */
    @GetMapping("/projects/{uuid}/tree")
    public ResponseEntity<List<ProjectNodeResponse>> getProjectTree(@AuthenticationPrincipal String email,
                                                                    @PathVariable String uuid) {
        // 서비스로부터 DTO 상자 수령
        UserProjectTreeResponse response = storageService.getProjectTree(email, uuid);

        // 내부의 순수 트리 리스트만 꺼내서 전송
        return ResponseEntity.ok(response.getTree());
    }

    /**
     * 특정 파일 내용 실시간 조회
     */
    @GetMapping("/projects/{uuid}/file-content")
    public ResponseEntity<String> getFileContent(@AuthenticationPrincipal String email,
                                                 @PathVariable String uuid,
                                                 @RequestParam String path) {
        String content = storageService.getFileContent(email, uuid, path);
        return ResponseEntity.ok(content);
    }

    /**
     * 프레임워크 통계 조회
     */
    @GetMapping("/projects/framework/statistics")
    public ResponseEntity<FrameworkStatisticsResponse> getFrameworkStatistics(@AuthenticationPrincipal String email) {
        FrameworkStatisticsResponse stats = storageService.getFrameworkStatistics(email);
        return ResponseEntity.ok(stats);
    }

    // 수정 - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    /**
     * 프로젝트 설명 변경
     */
    @PatchMapping("/projects/{uuid}/description")
    public ResponseEntity<ProjectResponse> updateProjectDescription(@AuthenticationPrincipal String email,
                                                                    @PathVariable String uuid,
                                                                    @RequestBody Map<String, String> request) {
        String description = request.get("description");
        ProjectResponse response = storageService.updateProjectDescriptionByEmail(email, uuid, description);
        return ResponseEntity.ok(response);
    }

    /**
     * 프로젝트 이름 변경
     */
    @PatchMapping("/projects/{uuid}")
    public ResponseEntity<ProjectResponse> updateProject(@AuthenticationPrincipal String email,
                                                         @PathVariable String uuid,
                                                         @RequestBody Map<String, String> request) {
        String newName = request.get("newName");
        ProjectResponse response = storageService.updateProjectNameByEmail(email, uuid, newName);
        return ResponseEntity.ok(response);
    }

    // 삭제 - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    /**
     * 프로젝트 단건 삭제
     */
    @DeleteMapping("/projects/{uuid}")
    public ResponseEntity<ProjectDeleteResponse> deleteProject(@AuthenticationPrincipal String email,
                                                               @PathVariable String uuid) {
        ProjectDeleteResponse response = storageService.deleteProjectAndGetResponse(email, uuid);
        return ResponseEntity.ok(response);
    }

    /**
     * 프로젝트 전체 일괄 삭제
     */
    @DeleteMapping("/projects/clean")
    public ResponseEntity<ProjectCleanResponse> deleteAllProjects(@AuthenticationPrincipal String email) {
        ProjectCleanResponse response = storageService.cleanAllProjectsByEmail(email);
        return ResponseEntity.ok(response);
    }

    // 다운로드 - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    /**
     * 프로젝트 압축 전송 및 다운로드
     */
    @GetMapping("/{uuid}/download")
    public ResponseEntity<byte[]> downloadProject(@AuthenticationPrincipal String email,
                                                  @PathVariable String uuid) {
        // 헤더 조립용 가공 메타데이터와 압축 데이터 자체를 서비스로부터 안전하게 한번에 수령합니다.
        ProjectDownloadPackResponse downloadPackResponse = storageService.prepareProjectDownload(email, uuid);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/zip"));
        headers.setContentLength(downloadPackResponse.getZipData().length);
        headers.setContentDisposition(downloadPackResponse.getContentDisposition());

        return new ResponseEntity<>(downloadPackResponse.getZipData(), headers, HttpStatus.OK);
    }
}