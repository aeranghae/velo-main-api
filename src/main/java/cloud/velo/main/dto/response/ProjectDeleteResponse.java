package cloud.velo.main.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.List;

@Getter
@AllArgsConstructor
public class ProjectDeleteResponse {
    private String message;
    private List<String> deletedUuids;
}