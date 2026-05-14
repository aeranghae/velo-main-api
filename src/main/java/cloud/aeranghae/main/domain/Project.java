package cloud.aeranghae.main.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Project {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name; // 유저가 설정한 프로젝트 이름

    @Column(unique = true, nullable = false, updatable = false)
    private String uuid; // 물리 저장소 식별자 (생성 후 변경 불가)

    // 현제 프로젝트에 적용된 ai model
    @ManyToOne(fetch = FetchType.LAZY) // 지연 로딩 권장
    @JoinColumn(name = "ai_model_id") // DB 컬럼명
    private AiModel model;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private LocalDateTime createdAt;
    private LocalDateTime lastModifiedAt;

    @Builder
    public Project(String name, String uuid, User user,  AiModel model) {
        this.name = name;
        this.uuid = uuid;
        this.user = user;
        this.model = model;
        this.createdAt = LocalDateTime.now();
        this.lastModifiedAt = LocalDateTime.now();
    }

    /**
     * 비즈니스 로직: 프로젝트 이름 변경
     */
    public void updateName(String newName) {
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("프로젝트 이름은 비어있을 수 없습니다.");
        }
        this.name = newName;
        this.lastModifiedAt = LocalDateTime.now(); // 수정 시점에 시간 갱신
    }
}