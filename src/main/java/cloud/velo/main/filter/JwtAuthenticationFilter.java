package cloud.velo.main.filter;

import cloud.velo.main.security.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper securityObjectMapper;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        // 클라이언트(리액트)의 요청 헤더에서 JWT 토큰을 꺼냅니다.
        String token = resolveToken(request);

        // 토큰 존재 여부에 따른 분기 정밀화
        if (token != null) {
            //  토큰이 존재하는데 위조되었거나 유효기간이 끝난 쓰레기 토큰인 경우,
            // 다음 필터로 넘기지 않고 여기서 즉시 401 JSON으로 차단
            if (!jwtTokenProvider.validateToken(token)) {
                log.warn("[JWT-Guard] 유효하지 않거나 만료된 토큰 감지 차단 - URI: {}", request.getRequestURI());

                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());

                // 관제탑 규격과 완벽 동기화된 토큰 만료 전용 한글 응답 메시지 사출
                String jsonResponse = securityObjectMapper.writeValueAsString(
                        "로그인 실패: 만료되거나 유효하지 않은 인증 토큰입니다."
                );
                response.getWriter().write(jsonResponse);
                return; // 다음 필터나 컨트롤러로 진입하지 못하게 톰캣 선에서 연산 즉시 종결!
            }

            // 진짜 검증된 정당한 토큰인 경우에만 인증 장부에 도장을 찍어줍니다.
            String email = jwtTokenProvider.getUserEmail(token);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(email, null, Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(authentication);

            log.debug("[JWT-Guard] 정식 인증 유저 검증 완료 - Email: {}", email);
        }

        // 토큰이 아예 없는 익명 사용자(예: permitAll 경로 접근 등)는
        // 일단 다음 필터로 통과시키고, 이후 시큐리티 권한 가드가 처리하도록 바통을 넘깁니다.
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