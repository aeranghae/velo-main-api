package cloud.velo.main.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Project {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(unique = true, nullable = false, updatable = false)
    private String uuid;

    @Column(nullable = false)
    private String framework;

    // 현재 진행 상황을 나타내는 필드
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_model_id")
    private AiModel model;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private long totalSize;
    private int fileCount;

    private LocalDateTime createdAt;
    private LocalDateTime lastModifiedAt;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "file_nodes", columnDefinition = "jsonb")
    private List<ProjectNode> fileNodes = new ArrayList<>();

    @Builder
    public Project(String name, String description, String uuid, String framework, User user, AiModel model, String status) {
        this.name = name;
        this.description = description;
        this.uuid = uuid;
        this.framework = framework;
        this.user = user;
        this.model = model;
        this.status = ProjectStatus.CREATED; // 프로젝트를 처음 만들었을때 default
        this.createdAt = LocalDateTime.now();
        this.lastModifiedAt = LocalDateTime.now();
        this.totalSize = 0L;
        this.fileCount = 0;

        this.fileNodes = new ArrayList<>();
        this.pipelineLogs = new ArrayList<>();
    }

    /**
     * 색인 엔진이 돌아갈 때 DB 장부의 수치들을 한 번에 리프레시해 줄 편의 메서드
     */
    public void updateStorageMeta(long totalSize, int fileCount, List<ProjectNode> newNodes) {
        this.totalSize = totalSize;
        this.fileCount = fileCount;
        this.lastModifiedAt = LocalDateTime.now(); // 파일 구조가 바뀌었으니 수정 시간도 갱신!

        // 기존 값 타입 컬렉션 갱신
        if (this.fileNodes == null) {
            this.fileNodes = new ArrayList<>();
        }
        this.fileNodes.clear();
        if (newNodes != null) {
            this.fileNodes.addAll(newNodes);
        }
    }

    /**
     * 비즈니스 로직: 프로젝트 이름 변경
     */
    public void updateName(String newName) {
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("프로젝트 이름은 비어있을 수 없습니다.");
        }
        this.name = newName;
        this.lastModifiedAt = LocalDateTime.now();
    }

    /**
     * 비즈니스 로직: 프로젝트 설명만 단독 변경 (필요 시 사용)
     */
    public void updateDescription(String newDescription) {
        this.description = newDescription;
        this.lastModifiedAt = LocalDateTime.now();
    }

    /**
     * 파일 노드 목록 전면 최신화
     * 파일 스캔이 끝난 뒤 새로운 구조를 통째로 갈아 끼워주는 안전장치 메서드
     */
    public void updateFileNodes(List<ProjectNode> newNodes) {
        if (this.fileNodes == null) {
            this.fileNodes = new ArrayList<>();
        }
        this.fileNodes.clear();
        if (newNodes != null) {
            this.fileNodes.addAll(newNodes);
        }
        this.lastModifiedAt = LocalDateTime.now();
    }

    /**
     * 프레임워크 변경 (필요할 경우 사용)
     */
    public void updateFramework(String framework) {
        if (framework == null || framework.isBlank()) {
            throw new IllegalArgumentException("프레임워크 정보는 비어있을 수 없습니다.");
        }
        this.framework = framework;
        this.lastModifiedAt = LocalDateTime.now();
    }

    // ProjectService에서 작업이 끝나거나 실패했을때 상태를 바꿈
    public void updateStatus(ProjectStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("상태 정보는 비어있을 수 없습니다.");
        }
        this.status = status;
        this.lastModifiedAt = LocalDateTime.now();
    }

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "pipeline_logs", columnDefinition = "jsonb")
    private List<java.util.Map<String, Object>> pipelineLogs = new ArrayList<>();
}