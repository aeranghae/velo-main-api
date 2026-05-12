package cloud.aeranghae.main.config.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        // 1. 클라이언트(리액트)의 요청 헤더에서 JWT 토큰을 꺼냅니다.
        String token = resolveToken(request);

        // 2. 토큰이 존재하고, 유효한 토큰인지 검사합니다.
        if (token != null && jwtTokenProvider.validateToken(token)) {
            // 3. 진짜 토큰이라면 안에 들어있는 이메일을 꺼냅니다.
            String email = jwtTokenProvider.getUserEmail(token);

            // 4. 스프링 시큐리티에게 "이 사람은 인증된 유저(email)야!" 라고 도장을 찍어줍니다.
            // (이 도장을 찍어야 아까 컨트롤러에서 @AuthenticationPrincipal 로 이메일을 받을 수 있습니다!)
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(email, null, Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        // 5. 다음 필터나 목적지(컨트롤러)로 통과시킵니다.
        filterChain.doFilter(request, response);
    }

    // 헤더에서 "Bearer " 부분을 떼어내고 순수 토큰만 추출하는 헬퍼 메서드
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}