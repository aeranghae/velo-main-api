package cloud.aeranghae.main.repository;

import cloud.aeranghae.main.domain.AiModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiModelRepository extends JpaRepository<AiModel, Long> {

    // 모델 이름으로 활성화된 설정 조회
    Optional<AiModel> findByModelNameAndIsActiveTrue(String modelName);

    // [추가] 기본 설정이 true인 모든 모델 찾기 (보통 1개여야 함)
    List<AiModel> findAllByDefaultActiveTrue();

    // [추가] 기본 설정이 true인 모델 하나만 찾기
    Optional<AiModel> findByDefaultActiveTrue();

    // [추가] 활성화된 모든 모델 리스트 조회
    List<AiModel> findAllByIsActiveTrue();
}