package cloud.velo.main.controller.dto;

import cloud.velo.main.domain.ProjectNode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(force = true)
@AllArgsConstructor
public class ProjectNodeResponse {
    private final String path;
    private final String type; // "DIR" 또는 "FILE"

    public ProjectNodeResponse(ProjectNode node) {
        this.path = node.getPath();
        this.type = node.getType();
    }
}