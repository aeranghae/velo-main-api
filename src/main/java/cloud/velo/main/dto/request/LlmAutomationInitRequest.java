package cloud.velo.main.dto.request;

import cloud.velo.main.dto.response.ProjectNodeResponse;
import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
public class LlmAutomationInitRequest {

    private final String type = "INIT";
    private final String projectName;
    private final String architectureType;
    private final String framework;
    private final String language;
    private final String database;
    private final String license;
    private final String prompt;
    private final List<ProjectNodeResponse> tree;

    @Builder
    public LlmAutomationInitRequest(String projectName, String architectureType, String framework,
                                    String language, String database, String license,
                                    String prompt, List<ProjectNodeResponse> tree) {
        this.projectName = projectName;
        this.architectureType = architectureType;
        this.framework = framework;
        this.language = language;
        this.database = database;
        this.license = license;
        this.prompt = prompt;
        this.tree = tree;
    }
}