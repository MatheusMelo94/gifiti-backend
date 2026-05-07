package com.gifiti.api.analytics;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Servlet filter that reads the optional {@code X-PostHog-DistinctId} HTTP
 * header and stashes the value on a request-scoped thread-local accessor
 * ({@link PostHogContext}) for the duration of the request. Cleared in a
 * {@code finally} block to prevent thread-pool leakage.
 *
 * <p>Validation: alphanumeric plus {@code -} and {@code _}, max 64 chars.
 * Anything outside that shape is dropped with a {@code WARN} log; the
 * request still proceeds (analytics propagation is best-effort and must
 * never break the user-facing call).</p>
 *
 * <p>Ordering: registered with {@link Ordered#HIGHEST_PRECEDENCE} + 100,
 * which lands AFTER {@link com.gifiti.api.config.CorrelationIdFilter}
 * (HIGHEST_PRECEDENCE) but before authentication and controllers. The
 * value is unauthenticated context — services decide whether to use it
 * based on their own auth state.</p>
 *
 * <p>Cite: Architect plan v2 §5.4, {@code architecture-conventions.md
 * § Layer Rules} (services receive the value as a parameter, do not read
 * the thread-local).</p>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class PostHogDistinctIdFilter extends OncePerRequestFilter {

    static final String HEADER = "X-PostHog-DistinctId";

    /**
     * Allowed shape: alphanumeric with dashes and underscores, 1 to 64 chars.
     * The wrapper sends this value as the PostHog distinctId; rejecting
     * non-conforming input prevents log injection and keeps PostHog's
     * person-level grouping on a clean key space.
     */
    private static final Pattern VALID_DISTINCT_ID = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String raw = request.getHeader(HEADER);
        boolean stored = false;

        if (raw != null && !raw.isBlank()) {
            if (VALID_DISTINCT_ID.matcher(raw).matches()) {
                PostHogContext.set(raw);
                stored = true;
            } else {
                // Don't echo the raw value back at INFO level — log shape only.
                log.warn(
                        "Dropping X-PostHog-DistinctId header: dropped_reason=invalid_header length={}",
                        raw.length());
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            if (stored) {
                PostHogContext.clear();
            }
        }
    }
}
