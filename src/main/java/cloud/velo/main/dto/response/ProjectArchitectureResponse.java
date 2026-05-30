package cloud.velo.main.dto.response;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectArchitectureResponse {

    private String subject;
    private String architectureType; // FULL_STACK 또는 CLIENT_SERVER
    private String projectDescription;

    private StackDetail framework;
    private StackDetail language;

    private String database;
    private List<String> coreFeatures;
    private List<String> constraints;
    private String rationale;

    /**
     * 중첩된 내부 JSON 구조(framework, language)를 받아내기 위한 스택 디테일 DTO
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StackDetail {
        private String unified;
        private String backend;
        private String frontend;
    }
}