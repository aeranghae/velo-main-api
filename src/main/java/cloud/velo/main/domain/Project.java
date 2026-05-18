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
@Table(name = "project")
public class Project {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false, updatable = false)
    private String uuid;

    @Column(nullable = false)
    private String framework;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ai_model_id")
    private AiModel model;

    private long totalSize;
    private int fileCount;

    // 💡 1. 여기에 status 필드를 추가해야 project.getStatus()를 호출할 수 있습니다.
    @Column(nullable = false)
    private String status;

    private LocalDateTime createdAt;
    private LocalDateTime lastModifiedAt;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "project_file_nodes", joinColumns = @JoinColumn(name = "project_id"))
    private List<ProjectNode> fileNodes = new ArrayList<>();

    // 💡 2. 빌더 생성자 파라미터에 String status를 추가해야 StorageService의 빌더가 작동합니다.
    @Builder
    public Project(String name, String uuid, String framework, User user, AiModel model, String status) {
        this.name = name;
        this.uuid = uuid;
        this.framework = framework;
        this.user = user;
        this.model = model;
        this.status = (status != null) ? status : "GENERATING"; // 기본값 방어
        this.createdAt = LocalDateTime.now();
        this.lastModifiedAt = LocalDateTime.now();
        this.totalSize = 0L;
        this.fileCount = 0;
    }

    // 💡 3. 여기에 이 메서드가 있어야 수신 웹훅에서 project.updateStatus(...)를 호출할 수 있습니다.
    public void updateStatus(String newStatus) {
        this.status = newStatus;
        this.lastModifiedAt = LocalDateTime.now(); // 상태 변경 시 수정 시간도 갱신!
    }

    public void updateStorageMeta(long totalSize, int fileCount, List<ProjectNode> newNodes) {
        this.totalSize = totalSize;
        this.fileCount = fileCount;
        this.lastModifiedAt = LocalDateTime.now();
        this.fileNodes.clear();
        this.fileNodes.addAll(newNodes);
    }

    public void updateName(String newName) {
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("프로젝트 이름은 비어있을 수 없습니다.");
        }
        this.name = newName;
        this.lastModifiedAt = LocalDateTime.now();
    }

    public void updateFileNodes(List<ProjectNode> newNodes) {
        this.fileNodes.clear();
        if (newNodes != null) {
            this.fileNodes.addAll(newNodes);
        }
        this.lastModifiedAt = LocalDateTime.now();
    }

    public void updateFramework(String framework) {
        if (framework == null || framework.isBlank()) {
            throw new IllegalArgumentException("프레임워크 정보는 비어있을 수 없습니다.");
        }
        this.framework = framework;
        this.lastModifiedAt = LocalDateTime.now();
    }
}