package cloud.velo.main.config.docker;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "velo.docker")
public class DockerImageProperties {

    private String defaultImage = "ubuntu:22.04";
    private Map<String, String> frameworkImages = new HashMap<>();
    private Map<String, String> languageImages = new HashMap<>();

    // 대소문자 무관하게 조회가 가능하도록 소문자로 변환하여 매핑 값을 찾는 커스텀 헬퍼 메서드
    public String findFrameworkImage(String framework) {
        if (framework == null) return null;
        return frameworkImages.get(framework.trim().toLowerCase());
    }

    public String findLanguageImage(String language) {
        if (language == null) return null;
        return languageImages.get(language.trim().toLowerCase());
    }
}