package com.gifiti.api.analytics;

import java.util.Optional;

/**
 * Thread-local accessor for the per-request PostHog distinctId carried by
 * the {@code X-PostHog-DistinctId} HTTP header.
 *
 * <p>Per Architect plan v2 §5.4 and {@code architecture-conventions.md
 * § Layer Rules}, services do not read this thread-local from inside the
 * {@link PostHogClient} wrapper — the controller (or service) reads the
 * value here at the request boundary and passes it explicitly to the
 * wrapper. Keeping the thread-local off the wrapper boundary preserves
 * unit-test simplicity and makes it impossible to leak across
 * request-scope (e.g., into async/scheduled work).</p>
 *
 * <p>The {@link PostHogDistinctIdFilter} sets the value early in the
 * request and clears it in a {@code finally} block — see that class for
 * cleanup semantics.</p>
 */
public final class PostHogContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private PostHogContext() {
        // Utility class — no instances.
    }

    static void set(String distinctId) {
        CURRENT.set(distinctId);
    }

    public static Optional<String> currentDistinctId() {
        return Optional.ofNullable(CURRENT.get());
    }

    static void clear() {
        CURRENT.remove();
    }
}
