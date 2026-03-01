package com.gifiti.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Filter that ensures every request has a correlation ID for tracing.
 * Accepts incoming X-Correlation-ID header or generates a new one.
 * Propagates to MDC for logging and response header.
 *
 * Security hardening:
 * - Validates correlation ID format to prevent log injection
 * - Only accepts UUID format or alphanumeric with hyphens
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String CORRELATION_ID_MDC_KEY = "correlationId";

    // Pattern: UUID or alphanumeric with hyphens, max 64 chars
    private static final Pattern VALID_CORRELATION_ID = Pattern.compile("^[a-zA-Z0-9-]{1,64}$");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String correlationId = request.getHeader(CORRELATION_ID_HEADER);

        // Validate format to prevent log injection attacks
        if (correlationId == null || correlationId.isBlank() || !isValidCorrelationId(correlationId)) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(CORRELATION_ID_MDC_KEY);
        }
    }

    /**
     * Validate correlation ID format to prevent log injection.
     * Accepts UUID format or alphanumeric characters with hyphens only.
     */
    private boolean isValidCorrelationId(String id) {
        return VALID_CORRELATION_ID.matcher(id).matches();
    }
}
