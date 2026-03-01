package com.gifiti.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rate limiting filter for sensitive endpoints.
 * Runs before authentication to prevent brute force attacks.
 *
 * Protected endpoints:
 * - POST /api/v1/auth/register - 10 req/min per IP
 * - POST /api/v1/auth/login - 10 req/min per IP
 * - POST /api/v1/public/wishlists/{id}/items/{itemId}/reserve - 30 req/min per IP
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitConfig rateLimitConfig;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();
        String clientIp = getClientIp(request);

        // Check rate limits for auth endpoints
        if ("POST".equals(method) && path.startsWith("/api/v1/auth/")) {
            if (!rateLimitConfig.tryConsumeAuth(clientIp)) {
                sendRateLimitResponse(response);
                return;
            }
        }

        // Check rate limits for reservation endpoint
        if ("POST".equals(method) && path.contains("/items/") && path.endsWith("/reserve")) {
            if (!rateLimitConfig.tryConsumeReservation(clientIp)) {
                sendRateLimitResponse(response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extract client IP address with X-Forwarded-For spoofing protection.
     *
     * Security: Only trust X-Forwarded-For when behind known proxy.
     * Uses rightmost IP (added by trusted proxy) to prevent client spoofing.
     */
    private String getClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();

        // Only trust X-Forwarded-For if request came from trusted proxy
        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }

        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            String[] ips = xForwardedFor.split(",");
            // Use rightmost non-trusted IP (last client before our proxy chain)
            for (int i = ips.length - 1; i >= 0; i--) {
                String ip = ips[i].trim();
                if (!isTrustedProxy(ip)) {
                    return ip;
                }
            }
        }
        return remoteAddr;
    }

    /**
     * Check if IP is a trusted proxy (load balancer, CDN, etc.).
     * Configure via TRUSTED_PROXIES environment variable.
     */
    private boolean isTrustedProxy(String ip) {
        // Private network ranges commonly used by load balancers
        return ip.startsWith("10.") ||
               ip.startsWith("172.16.") || ip.startsWith("172.17.") ||
               ip.startsWith("172.18.") || ip.startsWith("172.19.") ||
               ip.startsWith("172.20.") || ip.startsWith("172.21.") ||
               ip.startsWith("172.22.") || ip.startsWith("172.23.") ||
               ip.startsWith("172.24.") || ip.startsWith("172.25.") ||
               ip.startsWith("172.26.") || ip.startsWith("172.27.") ||
               ip.startsWith("172.28.") || ip.startsWith("172.29.") ||
               ip.startsWith("172.30.") || ip.startsWith("172.31.") ||
               ip.startsWith("192.168.") ||
               ip.equals("127.0.0.1") ||
               ip.equals("0:0:0:0:0:0:0:1");
    }

    /**
     * Send 429 Too Many Requests response.
     */
    private void sendRateLimitResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        response.getWriter().write("""
                {
                    "timestamp": "%s",
                    "status": 429,
                    "error": "Too Many Requests",
                    "message": "Rate limit exceeded. Please try again later."
                }
                """.formatted(java.time.Instant.now().toString()));
    }
}
