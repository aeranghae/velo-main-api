package cloud.velo.main.config.jpa;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;

@Configuration
@EnableJpaRepositories(basePackages = "cloud.velo.main.repository")
@EnableRedisRepositories(basePackages = "cloud.velo.main.repository.redis")
public class JpaConfig {
    // 나중에 @EnableJpaAuditing 등을 여기에 추가해서 관리

    /** memo
     * Spring Data Redis의 패키지 스캔 자동 설정 경고 메시지 해결
     * Spring Boot는 구동할 때 프로젝트 내의 모든 Repository 인터페이스를 훑어봅니다. 이때 프로젝트에 JPA와 Redis 의존성이 둘 다 들어가 있으면,
     * Spring은 "어? 이 UserRepository가 JPA용이야? Redis용이야? 구분이 안 가는데?" 하면서 일단 전부 경고(INFO 레벨)를 던진다고 합니다.
     */
}