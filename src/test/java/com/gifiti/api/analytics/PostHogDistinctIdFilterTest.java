package com.gifiti.api.analytics;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PostHogDistinctIdFilter} and {@link PostHogContext}.
 *
 * <p>Cite: Architect plan v2 §5.4 (header-based identity propagation,
 * services receive the value as a method parameter, never read thread-local
 * from inside the wrapper).</p>
 */
class PostHogDistinctIdFilterTest {

    private PostHogDistinctIdFilter filter;
    private ListAppender<ILoggingEvent> logAppender;
    private Logger filterLogger;

    @BeforeEach
    void setup() {
        filter = new PostHogDistinctIdFilter();
        filterLogger = (Logger) LoggerFactory.getLogger(PostHogDistinctIdFilter.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        filterLogger.addAppender(logAppender);
        // Defensive — ensure no leftover thread-local from a prior test.
        PostHogContext.clear();
    }

    @AfterEach
    void teardown() {
        filterLogger.detachAppender(logAppender);
        PostHogContext.clear();
    }

    @Test
    @DisplayName("filter exposes a valid header value via PostHogContext during the request")
    void valid_header_is_exposed_to_context() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-PostHog-DistinctId", "anon-abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<Optional<String>> seenInChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> seenInChain.set(PostHogContext.currentDistinctId());

        filter.doFilter(request, response, chain);

        assertThat(seenInChain.get()).hasValue("anon-abc-123");
        // After the filter completes, the thread-local must be cleared.
        assertThat(PostHogContext.currentDistinctId()).isEmpty();
    }

    @Test
    @DisplayName("filter tolerates a missing header — context returns Optional.empty()")
    void missing_header_yields_empty_context() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<Optional<String>> seen = new AtomicReference<>();
        FilterChain chain = (req, res) -> seen.set(PostHogContext.currentDistinctId());

        filter.doFilter(request, response, chain);

        assertThat(seen.get()).isEmpty();
    }

    @Test
    @DisplayName("filter rejects header values exceeding 64 chars (drops with WARN, request continues)")
    void overlength_header_is_dropped() throws ServletException, IOException {
        String tooLong = "a".repeat(65);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-PostHog-DistinctId", tooLong);
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<Optional<String>> seen = new AtomicReference<>();
        FilterChain chain = (req, res) -> seen.set(PostHogContext.currentDistinctId());

        filter.doFilter(request, response, chain);

        assertThat(seen.get()).isEmpty();
        assertThat(logAppender.list)
                .anySatisfy(event -> {
                    assertThat(event.getLevel().toString()).isEqualTo("WARN");
                    assertThat(event.getFormattedMessage()).contains("dropped_reason=invalid_header");
                });
    }

    @Test
    @DisplayName("filter rejects header values containing invalid characters (drops with WARN)")
    void invalid_chars_header_is_dropped() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        // Whitespace and quotes are common log-injection vectors; reject.
        request.addHeader("X-PostHog-DistinctId", "abc def\"; DROP TABLE x;--");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<Optional<String>> seen = new AtomicReference<>();
        FilterChain chain = (req, res) -> seen.set(PostHogContext.currentDistinctId());

        filter.doFilter(request, response, chain);

        assertThat(seen.get()).isEmpty();
        assertThat(logAppender.list)
                .anySatisfy(event -> {
                    assertThat(event.getLevel().toString()).isEqualTo("WARN");
                    assertThat(event.getFormattedMessage()).contains("dropped_reason=invalid_header");
                });
    }

    @Test
    @DisplayName("filter clears the context even if the downstream chain throws")
    void context_is_cleared_after_chain_exception() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-PostHog-DistinctId", "anon-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain failingChain = (req, res) -> {
            throw new ServletException("downstream boom");
        };

        try {
            filter.doFilter(request, response, failingChain);
        } catch (ServletException expected) {
            // Expected — we want the chain exception to propagate, but the
            // thread-local must be cleared regardless.
        } catch (IOException unexpected) {
            throw new RuntimeException(unexpected);
        }

        assertThat(PostHogContext.currentDistinctId()).isEmpty();
    }
}
