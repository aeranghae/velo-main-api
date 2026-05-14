package cloud.aeranghae.main.domain;

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

    @Column(nullable = false)
    private String apiKey;    // 각 모델별 API Key

    @Column(nullable = false)
    private String endpoint;  // 호출할 API 주소 (필요 시)

    private boolean isActive; // 현재 사용 가능한 모델인지 여부

    // 업데이트 로직
    public void updateInfo(String apiKey, String endpoint, boolean isActive) {
        this.apiKey = apiKey;
        this.endpoint = endpoint;
        this.isActive = isActive;
    }
}