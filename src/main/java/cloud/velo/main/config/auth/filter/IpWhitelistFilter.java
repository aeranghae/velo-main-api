package cloud.velo.main.config.auth.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
public class IpWhitelistFilter extends OncePerRequestFilter {

    @Value("${llm.server.allowed-ips}")
    private List<String> allowedIps;

    @Value("${llm.server.allowed-paths}")
    private List<String> allowedPaths;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String requestUri = request.getRequestURI();

        boolean isProtectedPath = allowedPaths.stream()
                .anyMatch(requestUri::startsWith);

        if (isProtectedPath) {
            String clientIp = getClientIp(request);

            if (!allowedIps.contains(clientIp)) {
                log.warn("허용되지 않은 IP 접근 시도: {} - {}", clientIp, requestUri);
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "허용되지 않은 IP");
                return;
            }

            log.info("LLM 서버 요청 허용: {} - {}", clientIp, requestUri);
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}