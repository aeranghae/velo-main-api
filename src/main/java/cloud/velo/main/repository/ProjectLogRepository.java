package cloud.velo.main.repository;

import cloud.velo.main.domain.ProjectLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectLogRepository extends JpaRepository<ProjectLog, Long> {

    /**
     * 고유 '사용자 ID(user_id)'와 '프로젝트 ID(project_id)' 숫자를 기준으로 로그 테이블을 매핑합니다.
     */
    @Query("SELECT pl FROM ProjectLog pl WHERE pl.user.id = :userId AND pl.project.id = :projectId ORDER BY pl.id ASC")
    List<ProjectLog> findLogsByUserIdAndProjectId(@Param("userId") Long userId, @Param("projectId") Long projectId);
}