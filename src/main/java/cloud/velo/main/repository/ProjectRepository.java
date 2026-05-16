package cloud.velo.main.repository;

import cloud.velo.main.domain.Project;
import cloud.velo.main.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByUserOrderByLastModifiedAtDesc(User user);
    Optional<Project> findByUuid(String uuid);
    int countByUser(User user);
}