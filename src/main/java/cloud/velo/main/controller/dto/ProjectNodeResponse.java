package cloud.velo.main.controller.dto;

import cloud.velo.main.domain.ProjectNode;
import lombok.Getter;

@Getter
public class ProjectNodeResponse {
    private final String path;
    private final String type; // "DIR" 또는 "FILE"

    public ProjectNodeResponse(ProjectNode node) {
        this.path = node.getPath();
        this.type = node.getType();
    }
}