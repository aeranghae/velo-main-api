package cloud.velo.main.dto.response;

import cloud.velo.main.domain.ProjectNode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProjectNodeResponse {
    private String path;
    private String type; // "DIR" 또는 "FILE"

    public ProjectNodeResponse(ProjectNode node) {
        this.path = node.getPath();
        this.type = node.getType();
    }
}