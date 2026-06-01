package cloud.velo.main.service;

import cloud.velo.main.dto.response.AiModelNameResponse;
import cloud.velo.main.dto.response.ProjectArchitectureResponse;
import cloud.velo.main.domain.AiModel;
import cloud.velo.main.domain.User;
import cloud.velo.main.repository.AiModelRepository;
import cloud.velo.main.repository.UserRepository;
import cloud.velo.main.util.bucket.RateLimiter;
import cloud.velo.main.exception.OverRateLimitException;
import cloud.velo.main.exception.UserNotFoundException;
import cloud.velo.main.exception.ModelNotFoundException;
import io.github.bucket4j.Bucket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiModelService {

    private final RestClient restClient = RestClient.create();
    private final AiModelRepository aiModelRepository;
    private final UserRepository userRepository;
    private final RateLimiter rateLimiter;

    @Value("${llm.server.url}")
    private String serverUrl;

    @Cacheable(value = "aiModelList")
    @Transactional(readOnly = true)
    public List<AiModelNameResponse> getActiveModelNames() {
        return aiModelRepository.findAllByIsActiveTrue().stream()
                .map(model -> new AiModelNameResponse(model.getModelName(), model.getProvider()))
                .collect(Collectors.toList());
    }

    @Cacheable(value = "aiModelList")
    @Transactional(readOnly = true)
    public List<AiModelNameResponse> getActiveModelNamesWithUserCheck(String email) {

        userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다. email: " + email));
        return getActiveModelNames();
    }

    @CacheEvict(value = "aiModelList", allEntries = true)
    @Transactional
    public void setDefaultModel(Long modelId) {
        aiModelRepository.findAllByDefaultActiveTrue()
                .forEach(AiModel::releaseDefault);

        AiModel newDefault = aiModelRepository.findById(modelId)
                .orElseThrow(() -> new ModelNotFoundException("지정하려는 모델을 찾을 수 없습니다. ID: " + modelId));

        newDefault.setAsDefault();
    }

    @CacheEvict(value = "userCache", key = "#email")
    @Transactional
    public void modifyDefaultModel(String email, String modelName) {
        if (modelName == null || modelName.isEmpty()) {
            throw new IllegalArgumentException("모델 이름이 필요합니다.");
        }

        AiModel aiModel = aiModelRepository.findByModelNameAndIsActiveTrue(modelName)
                .orElseThrow(() -> new ModelNotFoundException("존재하지 않거나 현재 비활성화된 AI 모델입니다. model: " + modelName));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다. email: " + email));

        user.updateModel(aiModel);
    }

    @Transactional
    public ProjectArchitectureResponse analyzeProjectIdeaWithRateLimit(String email, String idea) {
        Bucket bucket = rateLimiter.resolveBucket(email);

        if (!bucket.tryConsume(1)) {
            throw new OverRateLimitException("분당 요청 횟수가 초과되었습니다. 잠시 후 다시 시도해주세요.");
        }

        return analyzeProjectIdea(email, idea);
    }

    @Transactional(readOnly = true)
    public ProjectArchitectureResponse analyzeProjectIdea(String email, String userIdea) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다. email: " + email));

        if (user.getId() != 1 && user.getId() != 2) {
            throw new AccessDeniedException("AI 아키텍처 분석 권한이 없는 계정입니다.");
        }

        String fastApiUrl = serverUrl + "/api/llm/architecture";
        Map<String, String> requestBody = Map.of("description", userIdea);

        int maxRetries = 3;
        int attempt = 0;

        while (attempt < maxRetries) {
            attempt++;
            try {
                log.info("[Architecture-Api] FastAPI 기획서 요청 시작 (시도 {}/{})", attempt, maxRetries);

                ProjectArchitectureResponse response = restClient.post()
                        .uri(fastApiUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .retrieve()
                        .body(ProjectArchitectureResponse.class);

                if (response == null || response.getSubject() == null || response.getSubject().isBlank()) {
                    log.warn("[Architecture-Api] 올바르지 않은 데이터 형식 유입. 재요청 진행 (시도 {}/{})", attempt, maxRetries);
                    continue;
                }

                log.info("[Architecture-Api] 완벽한 아키텍처 기획 스냅샷 수신 성공! (주제: {})", response.getSubject());
                return response;

            } catch (RestClientException e) {
                log.error("[Architecture-Api] FastAPI 엔진 응답 실패 (시도 {}/{}): {}", attempt, maxRetries, e.getMessage());

                if (attempt >= maxRetries) {
                    throw new IllegalStateException("AI 기획 엔진이 지속적으로 올바르지 않은 양식을 응답했습니다.", e);
                }

                try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
            }
        }

        throw new IllegalStateException("요구사항 분석 공정이 올바르게 마감되지 않았습니다.");
    }
}