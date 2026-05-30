package cloud.velo.main.repository;

import cloud.velo.main.domain.Project;
import cloud.velo.main.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByUserOrderByLastModifiedAtDesc(User user);
    Optional<Project> findByUuid(String uuid);
    int countByUser(User user);
    List<Project> findByUser(User user);

    // 유저가 가진 모든 프로젝트의 totalSize 컬럼을 더해오는 쿼리 (디스크 I/O 제로)
    @Query("SELECT COALESCE(SUM(p.totalSize), 0) FROM Project p WHERE p.user = :user")
    long getTotalStorageSizeByUser(@Param("user") User user);

    Optional<Project> findByUuidAndUser(String uuid, User user);

    @Modifying
    @Query(value = "UPDATE project SET pipeline_logs = pipeline_logs || :newLogJson::jsonb WHERE uuid = :uuid", nativeQuery = true)
    void appendPipelineLog(@Param("uuid") String uuid, @Param("newLogJson") String newLogJson);

    void deleteAllByUser(User user);

    @Modifying
    @Query("delete from Project p where p.id in :ids")
    void deleteAllByIdIn(@Param("ids") List<Long> ids);
}