package cloud.velo.main.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "project_log")
public class ProjectLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // UUID 대신 고속 매핑의 기준점이 될 실물 사용자 ID 외래키 수립
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "log_level", nullable = false)
    private String logLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectStatus status;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String message;

    private LocalDateTime createdAt;

    @Builder
    public ProjectLog(User user, Project project, String logLevel, ProjectStatus status, String message) {
        this.user = user;
        this.project = project;
        this.logLevel = (logLevel != null) ? logLevel : "INFO";
        this.status = (status != null) ? status : ProjectStatus.GENERATING;
        this.message = message;
        this.createdAt = LocalDateTime.now();
    }
}