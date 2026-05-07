package com.gifiti.api.analytics;

import com.posthog.server.PostHog;
import com.posthog.server.PostHogConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the singleton {@link PostHogClient} bean used by services to emit
 * analytics events.
 *
 * <p><b>Split-gate enablement.</b> When {@code app.posthog.enabled=false}
 * (the default everywhere — security-findings.md F-1, F-3), this
 * configuration constructs a wrapper with a {@code null} SDK reference and
 * the {@code enabled} flag set to {@code false}. The wrapper short-circuits
 * before touching the SDK, so the absence of an API key in disabled mode
 * cannot break startup. Only when an operator explicitly flips
 * {@code POSTHOG_ENABLED=true} (and provides {@code POSTHOG_API_KEY}) does
 * the real SDK get instantiated.</p>
 *
 * <p>Cite: {@code architecture-conventions.md § Configuration & Secrets},
 * {@code § Layer Rules} (single boundary to the third-party SDK).</p>
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(PostHogProperties.class)
public class PostHogConfiguration {

    @Bean
    PostHogClient postHogClient(PostHogProperties properties) {
        if (!properties.enabled()) {
            log.info("PostHog analytics DISABLED (split-gate). No events will be emitted from this process.");
            return new PostHogClient(null, false);
        }

        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            // Fail-fast: enabled=true with no key is operator error, not a
            // silent fallback. Per § Configuration & Secrets, secrets have
            // no defaults — surfacing the misconfiguration at startup
            // beats silently emitting nothing in production.
            throw new IllegalStateException(
                    "app.posthog.enabled=true but POSTHOG_API_KEY is missing. "
                            + "Set the env var or set POSTHOG_ENABLED=false.");
        }

        log.info("PostHog analytics ENABLED — host={}", properties.host());
        PostHogConfig config = new PostHogConfig.Builder(properties.apiKey())
                .host(properties.host())
                .build();
        PostHog sdk = new PostHog();
        sdk.setup(config);
        return new PostHogClient(sdk, true);
    }
}
