package cloud.velo.main.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class IpWhitelistFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;

    @Value("${llm.server.allowed-ips}")
    private List<String> allowedIps;

    @Value("${llm.server.allowed-paths}")
    private List<String> allowedPaths;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String requestUri = request.getRequestURI();

        boolean isProtectedPath = allowedPaths.stream()
                .anyMatch(requestUri::startsWith);

        if (isProtectedPath) {
            String clientIp = getClientIp(request);

            if (!allowedIps.contains(clientIp)) {
                log.warn("[IP-Guard] 허용되지 않은 IP 접근 완전 거부: {} -> 경로: {}", clientIp, requestUri);

                // response.sendError 우회를 차단하고,
                // 403 규격과 일치하는 정제된 JSON Body 응답을 직접 사출합니다.
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());

                String jsonResponse = objectMapper.writeValueAsString(
                        "허용되지 않은 파일 접근입니다. 격리 구역 외부의 자원은 조작할 수 없습니다."
                );
                response.getWriter().write(jsonResponse);
                return;
            }

            log.info("[IP-Guard] 정식 승인된 LLM 내부 관문 인입 성공: {} -> 경로: {}", clientIp, requestUri);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Nginx 등 외부 프록시 장비 뒤에 안착한 실무 환경에서 실제 클라이언트 IP를 정확히 발굴하는 메서드
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");

        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip.trim())) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip.trim())) {
            ip = request.getRemoteAddr();
        }

        // 여러 프록시를 거쳐 IP가 콤마(,) 연쇄로 들어올 경우, 최좌측의 최초 클라이언트 실제 IP만 추출
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        return ip;
    }
}