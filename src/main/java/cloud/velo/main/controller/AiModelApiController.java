package cloud.velo.main.controller;

import cloud.velo.main.dto.request.ProjectAnalysisRequest;
import cloud.velo.main.dto.response.AiModelNameResponse;
import cloud.velo.main.dto.response.ProjectArchitectureResponse;
import cloud.velo.main.service.AiModelService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/llm")
@RequiredArgsConstructor
public class AiModelApiController {

    private final AiModelService aiModelService;

    @GetMapping("/list")
    public ResponseEntity<List<AiModelNameResponse>> getModelList(@AuthenticationPrincipal String email) {
        List<AiModelNameResponse> modelList = aiModelService.getActiveModelNamesWithUserCheck(email);
        return ResponseEntity.ok(modelList);
    }

    @PatchMapping("/setdefaultmodel")
    public ResponseEntity<String> updateDefaultModel(
            @AuthenticationPrincipal String email,
            @RequestBody Map<String, String> request) {

        String modelName = request.get("modelName");
        aiModelService.modifyDefaultModel(email, modelName);

        return ResponseEntity.ok("기본 모델이 " + modelName + "(으)로 변경되었습니다.");
    }

    /**
     * 사용자의 아이디어를 기반으로 AI 아키텍처 스택 추천 요청
     */
    @PostMapping("/projects/analyze")
    public ResponseEntity<ProjectArchitectureResponse> analyzeProject(
            @AuthenticationPrincipal String email,
            @RequestBody ProjectAnalysisRequest request) {

        // Rate Limit 체크와 LLM 연산 전체를 서비스 한 줄로 위임!
        ProjectArchitectureResponse response = aiModelService.analyzeProjectIdeaWithRateLimit(email, request.getIdea());
        return ResponseEntity.ok(response);
    }
}