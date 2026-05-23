package cloud.velo.main.service;

import cloud.velo.main.component.RateLimiterService;
import cloud.velo.main.controller.dto.AiModelNameResponseDto;
import cloud.velo.main.controller.dto.ProjectArchitectureResponse;
import cloud.velo.main.domain.AiModel;
import cloud.velo.main.domain.User;
import cloud.velo.main.repository.AiModelRepository;
import cloud.velo.main.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
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

    @Value("${llm.server.url}")
    private String serverUrl;

    @Cacheable(value = "aiModelList")
    @Transactional(readOnly = true)
    public List<AiModelNameResponseDto> getActiveModelNames() {
        return aiModelRepository.findAllByIsActiveTrue().stream()
                .map(model -> new AiModelNameResponseDto(
                        model.getModelName(),
                        model.getProvider()
                ))
                .collect(Collectors.toList());
    }

    @CacheEvict(value = "aiModelList", allEntries = true)
    @Transactional
    public void setDefaultModel(Long modelId) {
        // 1. 기존에 기본값으로 설정된 모델들을 모두 false로 변경
        aiModelRepository.findAllByDefaultActiveTrue()
                .forEach(AiModel::releaseDefault);

        // 2. 새로운 모델을 기본값으로 설정
        AiModel newDefault = aiModelRepository.findById(modelId)
                .orElseThrow(() -> new IllegalArgumentException("모델을 찾을 수 없습니다."));

        newDefault.setAsDefault();
    }

    @CacheEvict(value = "userCache", key = "#email")
    @Transactional
    public void updateDefaultModel(String email, String modelName) {
        // 1. 해당 모델이 존재하는지, 그리고 활성화 상태인지 확인
        AiModel aiModel = aiModelRepository.findByModelNameAndIsActiveTrue(modelName)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않거나 비활성화된 모델입니다."));

        // 2. 유저 조회
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // 3. 유저의 기본 모델 변경 (영속성 컨텍스트 덕분에 자동 update)
        user.updateModel(aiModel);
    }

    // 요구사항 분석을 위한 파일
    public ProjectArchitectureResponse analyzeProjectIdea(String email, String userIdea) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));


        // TODO: 일단 관리자 두명만 허가
        if (user.getId() != 1 && user.getId() != 2) {
            return null;
        }

        String fastApiUrl = serverUrl + "/api/llm/architecture";
        Map<String, String> requestBody = Map.of("description", userIdea);

        int maxRetries = 3; // 재시도 허용 횟수
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

                //  [검증 가드] HTTP 통신은 성공했으나 본문 내용(DTO)이 null이거나 필수 핵심 필드가 깨져서 유입된 경우
                if (response == null || response.getSubject() == null || response.getSubject().isBlank()) {
                    log.warn("[Architecture-Api] 올바르지 않은 데이터 형식 유입. 재요청을 진행합니다. (시도 {}/{})", attempt, maxRetries);
                    continue; // 아래 코드를 건너뛰고 다음 attempt 루프로 이동
                }

                // 올바른 데이터가 정상 수신되었다면 즉시 결과 리턴 후 탈출
                log.info("[Architecture-Api] 완벽한 아키텍처 기획 스냅샷 수신 성공! (주제: {})", response.getSubject());
                return response;

            } catch (RestClientException e) {
                // FastAPI 단에서 Pydantic 제약조건 유효성 위반으로 500 이나 400 에러를 뱉었을 때 낚아챕니다.
                log.error("[Architecture-Api] FastAPI 엔진 응답 실패 또는 규칙 위반 예외 발생 (시도 {}/{}): {}", attempt, maxRetries, e.getMessage());

                if (attempt >= maxRetries) {
                    // 3번 다 튕겼을 때 최종적으로 멱살 잡고 런타임 에러 격발
                    throw new IllegalStateException("AI 기획 엔진이 지속적으로 올바르지 않은 양식을 응답했습니다. 잠시 후 다시 시도해주세요.", e);
                }

                // 다음 턴 격발 전 안트로픽/구글 서버 버퍼 리셋을 위해 1.5초간 가볍게 숨 고르기 대기
                try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
            }
        }

        throw new IllegalStateException("요구사항 분석 공정이 올바르게 마감되지 않았습니다.");
    }

}