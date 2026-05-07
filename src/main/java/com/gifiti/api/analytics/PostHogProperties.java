package com.gifiti.api.analytics;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed configuration for the PostHog analytics integration (feature 007).
 *
 * <p>Bound from {@code app.posthog.*} keys in {@code application.yml} (which
 * in turn read {@code POSTHOG_*} env vars). Per the split-gate model
 * (security-findings.md F-1, F-3), {@code enabled} defaults to {@code false}
 * across every committed config; production enablement happens by flipping
 * the env var in Render's dashboard AFTER the DPA is signed and the
 * account-deletion runbook is drafted.</p>
 *
 * <p>Cite: {@code architecture-conventions.md § Configuration & Secrets} —
 * env-driven, no defaults for secrets, profile-aware.</p>
 *
 * @param enabled split-gate flag — when {@code false}, {@link PostHogClient}
 *                short-circuits before touching the SDK
 * @param apiKey PostHog server-side project key; per-environment
 * @param host PostHog ingest host; US region per Decision A (revised 2026-05-07)
 * @param returnThresholdDays threshold for the {@code wishlist_returned}
 *                            event (T9, OQ-4 default 7)
 */
@ConfigurationProperties(prefix = "app.posthog")
public record PostHogProperties(
        boolean enabled,
        String apiKey,
        String host,
        int returnThresholdDays
) {
    public PostHogProperties {
        if (host == null || host.isBlank()) {
            host = "https://us.i.posthog.com";
        }
        if (returnThresholdDays <= 0) {
            returnThresholdDays = 7;
        }
    }
}
