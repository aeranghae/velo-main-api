package cloud.velo.main.config;

import cloud.velo.main.filter.JwtAuthenticationFilter;
import cloud.velo.main.security.JwtTokenProvider;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;

    private final ObjectMapper objectMapper;

    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Value("${llm.server.allowed-paths}")
    private List<String> llmAllowedPaths;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        http
                // 1. 스프링 시큐리티 자체 CORS 활성화 가드를 명시하여
                // 시큐리티 필터 체인 최상단에 CorsFilter가 자동으로 예쁘게 배치되도록 유도합니다.
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            // [추가] 클라이언트 실제 IP 획득
                            String clientIp = getClientIp(request);

                            // [수정] 로그 포맷에 IP 추가
                            log.warn("[Security] 인증 실패 보호 작동 - IP: {}, URI: {}, Reason: {}",
                                    clientIp, request.getRequestURI(), authException.getMessage());

                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding(StandardCharsets.UTF_8.name());

                            String jsonResponse = objectMapper.writeValueAsString(
                                    "로그인 실패: 인증 자격 증명이 유효하지 않거나 누락되었습니다."
                            );
                            response.getWriter().write(jsonResponse);
                        })
                )
                .authorizeHttpRequests(auth -> {
                    // 브라우저가 날리는 모든 'OPTIONS' 임시 사전 요청(Preflight)은
                    // 토큰 검사를 하지 않고 패스(permitAll) 시킴
                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();

                    auth.dispatcherTypeMatchers(
                            jakarta.servlet.DispatcherType.ASYNC,
                            jakarta.servlet.DispatcherType.ERROR
                    ).permitAll();

                    auth.requestMatchers("/api/auth/google").permitAll();

                    llmAllowedPaths.forEach(path ->
                            auth.requestMatchers(path + "/**").permitAll()
                    );

                    auth.anyRequest().authenticated();
                })
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider, objectMapper), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setExposedHeaders(List.of("Authorization", "Content-Disposition", "Content-Length"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private String getClientIp(jakarta.servlet.http.HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");

        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        // 프록시 목록(X-Forwarded-For) 중 실제 클라이언트의 첫 IP만 정제
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        return ip;
    }
}