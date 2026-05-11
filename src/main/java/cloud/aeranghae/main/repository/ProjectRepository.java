package cloud.aeranghae.main.repository;

import cloud.aeranghae.main.domain.Project;
import cloud.aeranghae.main.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByUserOrderByLastModifiedAtDesc(User user);
    Optional<Project> findByUuid(String uuid);
}