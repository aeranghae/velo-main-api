package cloud.velo.main.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UserProjectListResponse {
    // List를 이 객체(상자) 내부 필드로 격리시킵니다.
    private List<ProjectResponse> projects;
}