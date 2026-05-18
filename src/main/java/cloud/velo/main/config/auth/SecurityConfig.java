package cloud.velo.main.config.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

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
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()

                        // 방금 만든 구글 로그인 검증 API는 누구나 접근 가능해야 함
                        .requestMatchers("/api/auth/google").permitAll()

                        //  FastAPI 내부 워커가 쏘아 보내는 로그 수신 웹훅 오픈
                        // 인증 토큰(JWT) 검증 없이 안전하게 수신하기 위해 permitAll() 설정합니다.
                        .requestMatchers("/api/projects/webhook/logs").permitAll()

                        // 유저 전용 로그 조회 및 SSE 실시간 스트림 라인
                        // 로그인한 일반 사용자들만 접근할 수 있도록 바리케이드를 칩니다.
                        .requestMatchers("/api/projects/**").authenticated()

                        // 그 외의 모든 API 요청은 인증(토큰)이 있어야만 접근 가능하도록 막음
                        .anyRequest().authenticated()
                )

                // 5. 스프링 기본 인증 필터가 돌기 '전'에 우리가 만든 JWT 필터 끼워넣기!
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), CorsFilter.class);

        return http.build();
    }

    // CORS 세부 설정 메서드
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization")); // 리액트에서 토큰을 읽어야 할 경우 대비

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

}