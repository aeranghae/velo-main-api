package cloud.velo.main.controller;

import cloud.velo.main.controller.dto.ProjectLogResponseDto;
import cloud.velo.main.domain.Project;
import cloud.velo.main.service.ProjectLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import cloud.velo.main.repository.ProjectRepository;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectLogController {

    private final ProjectLogService projectLogService;
    private final ProjectRepository projectRepository;

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

    @DeleteMapping("/{uuid}")
    @Transactional // 🌟 데이터 파괴 공정이므로 트랜잭션 필수 걸어주기
    public ResponseEntity<Void> deleteProjectAndLogs(
            @PathVariable("uuid") String uuid,
            @AuthenticationPrincipal String email) {

        // 1. 안전 가드: UUID에 해당하는 프로젝트가 진짜 있는지 확인
        Project project = projectRepository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 프로젝트입니다. UUID: " + uuid));

        // 2. 소유권 검증: 로그인한 유저가 이 프로젝트의 주인이 맞는지 체크
        if (!project.getUser().getEmail().equals(email)) {
            throw new SecurityException("해당 프로젝트를 삭제할 정식 권한이 없습니다.");
        }

        // 3. 외래키 자식 로그 데이터부터 삭제
        projectLogService.deleteLogsByProjectId(project.getId());

        // 4. 부모인 프로젝트 테이블 데이터를 안전하게 삭제
        projectRepository.delete(project);

        // 204 No Content로 깔끔하게 성공 반환
        return ResponseEntity.noContent().build();
    }
}