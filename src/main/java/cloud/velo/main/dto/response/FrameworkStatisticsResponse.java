package cloud.velo.main.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
public class FrameworkStatisticsResponse {

    private long totalProjectCount;            // 총 프로젝트 개수
    private Map<String, Long> frameworkCounts; // 프레임워크별 개수 (예: {"SPRING_BOOT": 3, "REACT": 2})

    public FrameworkStatisticsResponse(long totalProjectCount, Map<String, Long> frameworkCounts) {
        this.totalProjectCount = totalProjectCount;
        this.frameworkCounts = frameworkCounts;
    }
}