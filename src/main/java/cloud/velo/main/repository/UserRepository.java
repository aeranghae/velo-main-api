package cloud.velo.main.repository;

import cloud.velo.main.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    // 소셜 로그인으로 반환되는 값 중 email을 통해 이미 가입된 사용자인지 판단하기 위함
    Optional<User> findByEmail(String email);
}