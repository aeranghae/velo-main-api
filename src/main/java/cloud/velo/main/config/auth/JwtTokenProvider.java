package cloud.velo.main.config.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long validityInMilliseconds;

    // application.yml 에 임의의 긴 비밀번호를 설정해두고 가져옵니다.
    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long validityInMilliseconds) { // 기본 1시간
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.validityInMilliseconds = validityInMilliseconds;
    }

    // 🔑 핵심 메서드: 유저 이메일을 받아서 JWT 토큰을 생성합니다.
    public String createToken(String email, String role) {
        Date now = new Date();
        Date validity = new Date(now.getTime() + validityInMilliseconds);

        return Jwts.builder()
                .subject(email) // 토큰의 주인 (이메일)
                .claim("role", role) // 토큰에 담을 추가 정보 (권한)
                .issuedAt(now) // 발행 시간
                .expiration(validity) // 만료 시간
                .signWith(secretKey) // 서버만 아는 비밀키로 서명 (위조 방지)
                .compact(); // 문자열로 압축!
    }

    // 🔍 1. 토큰에서 유저 이메일(Subject) 꺼내기
    public String getUserEmail(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    // 🛡️ 2. 토큰 유효성 검사 (위조되거나 만료되지 않았는지)
    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            // 만료되었거나 손상된 토큰이면 false 반환
            return false;
        }
    }
}
