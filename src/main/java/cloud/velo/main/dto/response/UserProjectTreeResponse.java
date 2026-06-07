package cloud.velo.main.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UserProjectTreeResponse {
    // List 인터페이스 컬렉션을 일반 구체 클래스 내부로 격리
    private List<ProjectNodeResponse> tree;
}