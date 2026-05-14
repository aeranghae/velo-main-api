package cloud.aeranghae.main.repository;

import cloud.aeranghae.main.domain.AiModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiModelRepository extends JpaRepository<AiModel, Long> {
    // 모델 이름으로 활성화된 설정 조회
    Optional<AiModel> findByModelNameAndIsActiveTrue(String modelName);
}