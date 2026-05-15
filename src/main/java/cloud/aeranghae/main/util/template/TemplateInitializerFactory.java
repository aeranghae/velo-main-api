package cloud.aeranghae.main.util.template;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TemplateInitializerFactory {

    private final List<TemplateInitializer> initializers;

    public TemplateInitializer getInitializer(String framework) {
        return initializers.stream()
                .filter(i -> i.getSupportedFramework().equalsIgnoreCase(framework))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "지원하지 않는 프레임워크: " + framework));
    }
}