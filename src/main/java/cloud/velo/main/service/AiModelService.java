package cloud.velo.main.service;

import cloud.velo.main.controller.dto.AiModelNameResponseDto;
import cloud.velo.main.controller.dto.ProjectArchitectureResponse;
import cloud.velo.main.domain.AiModel;
import cloud.velo.main.domain.User;
import cloud.velo.main.repository.AiModelRepository;
import cloud.velo.main.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

        if(user.getId() != 1 && user.getId() != 2){
            return null;
        }


        String fastApiUrl = serverUrl + "/api/llm/architecture";

        // FastAPI로 넘겨줄 JSON 요청 바디 구성
        Map<String, String> requestBody = Map.of("description", userIdea);

        // FastAPI 연동 실행 및 DTO 매핑 리턴
        return restClient.post()
                .uri(fastApiUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(ProjectArchitectureResponse.class);
    }

}