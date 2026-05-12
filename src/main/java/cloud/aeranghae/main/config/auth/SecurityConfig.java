package cloud.aeranghae.main.config.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;

    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 1. CORS 설정 활성화
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 2. REST API 서버이므로 화면 관련 보안 기능(CSRF, Form Login) 비활성화
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)

                // 3. 세션을 사용하지 않고 Stateless(무상태)하게 설정 (토큰 방식의 기본)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // 4. API 엔드포인트 권한(문지기) 설정
                .authorizeHttpRequests(auth -> auth
                        // 방금 만든 구글 로그인 검증 API는 누구나 접근 가능해야 함
                        .requestMatchers("/api/auth/google").permitAll()
                        // 나중에 SSE 통신이나 다른 열어둘 API가 있다면 여기에 추가
                        // 그 외의 모든 API 요청은 인증(토큰)이 있어야만 접근 가능하도록 막음
                        .anyRequest().authenticated()
                )

                // 5. 추가된 핵심 부분: 스프링 기본 인증 필터가 돌기 '전'에 우리가 만든 JWT 필터 끼워넣기!
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // CORS 세부 설정 메서드
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // 일렉트론(리액트) 로컬 개발 서버 주소 명시적 허용
        //config.setAllowedOrigins(allowedOrigins);
        config.setAllowedOriginPatterns(List.of("*"));

        // 허용할 HTTP 메서드
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // 허용할 헤더
        config.setAllowedHeaders(List.of("*"));
        // 인증 정보(쿠키, 헤더 등)를 포함한 요청 허용 여부
        config.setAllowCredentials(true);

        config.setExposedHeaders(List.of("Authorization")); // 리액트에서 토큰을 읽어야 할 경우 대비

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 모든 API 경로("/**")에 대해 위 설정 적용
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}