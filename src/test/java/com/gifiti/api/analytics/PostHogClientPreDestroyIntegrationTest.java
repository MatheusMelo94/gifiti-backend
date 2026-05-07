package com.gifiti.api.analytics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for security-findings.md F-6 — graceful shutdown calls
 * {@code PostHogClient.flush()} exactly once. Spring's lifecycle invokes
 * {@code @PreDestroy} hooks when the application context closes; we
 * assert the wrapper participates in that hook.
 *
 * <p>Uses a minimal {@link AnnotationConfigApplicationContext} (no
 * {@code @SpringBootTest}) so we can deterministically open and close
 * the context inside a single test method without bleeding into the
 * Spring test-context cache.</p>
 */
class PostHogClientPreDestroyIntegrationTest {

    @Configuration
    static class ShutdownSpyConfig {
        static final AtomicInteger flushCount = new AtomicInteger(0);

        @Bean
        @Primary
        PostHogClient spyingClient() {
            // Spy: enabled=false so the wrapper short-circuits any capture
            // attempt (no real SDK ever needed); the @PreDestroy still
            // fires because the bean lifecycle is independent of the
            // enabled flag. We override flush() to count invocations.
            return new PostHogClient(null, false) {
                @Override
                public void flush() {
                    flushCount.incrementAndGet();
                    super.flush();
                }
            };
        }
    }

    @Test
    @DisplayName("Spring context close invokes PostHogClient.flush() exactly once via @PreDestroy")
    void shutdown_invokes_flush_once() {
        ShutdownSpyConfig.flushCount.set(0);

        try (ConfigurableApplicationContext context =
                     new AnnotationConfigApplicationContext(ShutdownSpyConfig.class)) {
            // Force bean materialization so @PreDestroy can later run.
            assertThat(context.getBean(PostHogClient.class)).isNotNull();
        }

        // Try-with-resources fired close() on the context; @PreDestroy on
        // PostHogClient must have run exactly once.
        assertThat(ShutdownSpyConfig.flushCount.get()).isEqualTo(1);
    }
}
