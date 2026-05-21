package cloud.velo.main.controller;

import cloud.velo.main.component.RateLimiterService;
import cloud.velo.main.controller.dto.AiModelNameResponseDto;
import cloud.velo.main.controller.dto.ProjectAnalysisRequest;
import cloud.velo.main.controller.dto.ProjectArchitectureResponse;
import cloud.velo.main.domain.User;
import cloud.velo.main.repository.UserRepository;
import cloud.velo.main.service.AiModelService;
import io.github.bucket4j.Bucket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/llm")
@RequiredArgsConstructor
public class AiModelApiController {

    private final AiModelService aiModelService;
    private final UserRepository userRepository;
    private final RateLimiterService rateLimiterService;

    @GetMapping("/list")
    public ResponseEntity<List<AiModelNameResponseDto>> getModelList(@AuthenticationPrincipal String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("유저 없음"));

        // log.info("AI 모델 리스트 조회 요청 - User: {}", user.getName());
        List<AiModelNameResponseDto> modelList = aiModelService.getActiveModelNames();
        return ResponseEntity.ok(modelList);
    }

    @PatchMapping("/setdefaultmodel")
    public ResponseEntity<?> updateDefaultModel(
            @AuthenticationPrincipal String email,
            @RequestBody Map<String, String> request) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("유저 없음"));

        String modelName = request.get("modelName");

        if (modelName == null || modelName.isEmpty()) {
            return ResponseEntity.badRequest().body("모델 이름이 필요합니다.");
        }

        try {
            aiModelService.updateDefaultModel(email, modelName);
            return ResponseEntity.ok("기본 모델이 " + modelName + "(으)로 변경되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("변경 중 오류 발생: " + e.getMessage());
        }
    }

    /**
     * 사용자의 아이디어를 기반으로 AI 아키텍처 스택 추천 요청
     * POST /api/storage/projects/analyze
     */
    @PostMapping("/projects/analyze")
    public ResponseEntity<ProjectArchitectureResponse> analyzeProject(
            @AuthenticationPrincipal String email,
            @RequestBody ProjectAnalysisRequest request) {

        // 1. 해당 유저의 버킷 가져오기
        Bucket bucket = rateLimiterService.resolveBucket(email);

        // 2. 토큰 1개 소비 시도 (분당 3회 제한 체크)
        if (!bucket.tryConsume(1)) {
            // 앞서 프론트엔드 예외 처리 코드에 있던 429 에러 대응단으로 진입하게 됩니다.
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }

        // request 객체 내부에서 자바의 Getter로 아이디어 텍스트만 쏙 추출
        ProjectArchitectureResponse response = aiModelService.analyzeProjectIdea(email, request.getIdea());
        return ResponseEntity.ok(response);
    }

}