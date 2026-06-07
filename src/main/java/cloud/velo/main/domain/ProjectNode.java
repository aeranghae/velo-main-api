package cloud.velo.main.domain;

import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA 엔티티/임베더블 규칙 보장
@AllArgsConstructor
public class ProjectNode {

    private String path; // 상대 경로 (예: src/MainLogic.java, src/utils)
    private String type; // 타입 ("DIR" 또는 "FILE")

    // 값 객체의 특성상 불변성을 유지하고 동일성 비교를 위해 equals & hashCode
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProjectNode that = (ProjectNode) o;
        return Objects.equals(path, that.path) && Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, type);
    }
}