package cloud.velo.main.controller;

import cloud.velo.main.controller.dto.AiModelNameResponseDto;
import cloud.velo.main.domain.User;
import cloud.velo.main.repository.UserRepository;
import cloud.velo.main.service.AiModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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


}