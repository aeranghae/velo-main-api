package cloud.velo.main.component;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimiterService {

    // 사용자 이메일별 버킷을 동시성 보장이 되는 Map으로 관리
    private final Map<String, Bucket> cache = new ConcurrentHashMap<>();

    public Bucket resolveBucket(String email) {
        return cache.computeIfAbsent(email, this::createNewBucket);
    }

    // 유저 전용 버킷 생성: 총 용량 3개, 1분마다 3개씩 완충 (분당 3회 제한)
    private Bucket createNewBucket(String email) {
        Bandwidth limit = Bandwidth.classic(3, Refill.intervally(3, Duration.ofMinutes(1)));
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}