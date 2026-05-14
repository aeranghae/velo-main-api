package cloud.aeranghae.main.service;

import cloud.aeranghae.main.controller.dto.AiModelNameResponseDto;
import cloud.aeranghae.main.domain.AiModel;
import cloud.aeranghae.main.domain.User;
import cloud.aeranghae.main.repository.AiModelRepository;
import cloud.aeranghae.main.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiModelService {

    private final AiModelRepository aiModelRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<AiModelNameResponseDto> getActiveModelNames() {
        return aiModelRepository.findAllByIsActiveTrue().stream()
                .map(model -> new AiModelNameResponseDto(
                        model.getModelName(),
                        model.getProvider()
                ))
                .collect(Collectors.toList());
    }

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
}