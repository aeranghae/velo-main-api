package cloud.aeranghae.main.domain;

import cloud.aeranghae.main.util.encryption.ApiKeyConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String modelName; // 예: "gpt-4", "claude-3-opus"

    @Column(nullable = false)
    private String provider;  // 예: "OpenAI", "Anthropic", "Google"

    @Convert(converter = ApiKeyConverter.class)
    @Column(nullable = true)
    private String apiKey;    // 각 모델별 API Key

    @Column(nullable = false)
    private String endpoint;  // 호출할 API 주소 (필요 시)

    private boolean isActive; // 현재 사용 가능한 모델인지 여부

    private boolean defaultActive; // 기본 모델 지정 여부

    // 업데이트 로직
    public void updateInfo(String apiKey, String endpoint, boolean isActive) {
        this.apiKey = apiKey;
        this.endpoint = endpoint;
        this.isActive = isActive;
    }

    // 기본 설정 해제
    public void releaseDefault() {
        this.defaultActive = false;
    }

    // 기본 설정 지정
    public void setAsDefault() {
        this.isActive = true; // 기본 모델은 당연히 활성화 상태여야 함
        this.defaultActive = true;
    }
}