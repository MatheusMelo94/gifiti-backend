package com.gifiti.api.analytics;

import com.posthog.server.PostHogCaptureOptions;
import com.posthog.server.PostHogInterface;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * PostHog analytics wrapper. Single boundary between Gifiti services and the
 * PostHog Java SDK.
 *
 * <p><b>Allowlist contract (security-findings.md §5.6, F-5).</b> Two
 * allowlists enforced at runtime, one for event names and one for property
 * keys. Anything not on the allowlist is dropped with a {@code WARN} log; an
 * event with disallowed properties still fires (without the offending props),
 * but an event with a disallowed name is suppressed entirely. The
 * {@link #FORBIDDEN_PROPERTIES} list is a defense-in-depth deny list of known
 * PII / secret-bearing keys (including {@code productLink} per F-5) — it
 * short-circuits even if a future event were added to the allowlist by
 * mistake.</p>
 *
 * <p><b>Split-gate model (F-1, F-3).</b> Production emission is unblocked
 * only by the explicit {@code POSTHOG_ENABLED=true} flag. When disabled the
 * wrapper short-circuits before touching the SDK reference (which may be
 * {@code null} in disabled mode), guaranteeing that local development and
 * any environment whose DPA / deletion-runbook prerequisites have not been
 * completed cannot accidentally emit.</p>
 *
 * <p><b>Fail-open semantics (F-6).</b> {@link #capture} dispatches to a
 * single-thread executor and never throws to the caller. Exceptions raised by
 * the SDK are logged at {@code WARN}; signup, wishlist creation, item
 * addition, and reservation must succeed even when PostHog is unreachable.
 * The wrapper does not retry — analytics are not financial events.</p>
 *
 * <p>Cite: {@code architecture-conventions.md § Layer Rules} (services depend
 * on this wrapper; only this wrapper knows the SDK), {@code § Logging}
 * (structured WARN on drops, never PII at INFO).</p>
 */
@Slf4j
public class PostHogClient {

    /**
     * The 7-event taxonomy from the cofounder's authoritative spec. Backend
     * emits events #2, #3, #5, #6, #7. Frontend emits #1 and #4. The wrapper
     * gates on the full 7 so the contract is symmetric — any future shared
     * SDK pattern keeps the same allowlist.
     */
    static final Set<String> ALLOWED_EVENTS = Set.of(
            "wishlist_viewed",
            "wishlist_created",
            "item_added",
            "wishlist_shared",
            "item_reserved",
            "signup_completed",
            "wishlist_returned"
    );

    /**
     * Per-event allowed property keys, derived from plan §5.6. The wrapper
     * filters caller-supplied properties against the union of (a) the
     * per-event allowed set and (b) the global pseudo-property
     * {@code $server_initiated}.
     */
    static final Map<String, Set<String>> ALLOWED_PROPERTIES = Map.of(
            "wishlist_viewed", Set.of("wishlist_id", "viewer_logged_in", "referrer", "item_count"),
            "wishlist_created", Set.of("user_id", "occasion_type", "item_count_at_creation"),
            "item_added", Set.of("wishlist_id", "user_id", "item_position"),
            "wishlist_shared", Set.of("wishlist_id", "share_channel"),
            "item_reserved", Set.of("wishlist_id", "item_id", "reserver_user_id"),
            "signup_completed", Set.of("signup_trigger", "referrer_wishlist_id"),
            "wishlist_returned", Set.of("user_id", "days_since_creation")
    );

    /**
     * Defense-in-depth deny list. Even if a property key were mistakenly added
     * to {@link #ALLOWED_PROPERTIES} for some event, anything in this set is
     * unconditionally stripped before emission. Per security-findings.md F-5,
     * {@code productLink} is on this list — its values are user-supplied URLs
     * that may carry tokens / PII in query strings.
     */
    static final Set<String> FORBIDDEN_PROPERTIES = Set.of(
            "email", "displayName", "name", "firstName", "lastName",
            "phoneNumber", "address",
            "wishlistTitle", "itemName", "itemDescription",
            "imageUrl", "coverImageUrl",
            "ipAddress", "userAgent",
            "productLink",
            // Feature 008 / T14 (Security findings F-9 defense-in-depth):
            // accessCode is the 4-digit PRIVATE-wishlist secret. Not on any
            // event's allowlist today, but listing it here ensures a future
            // taxonomy expansion cannot leak it even by allowlist mistake.
            "accessCode"
    );

    /**
     * The {@code $server_initiated} pseudo-property is a global filterable
     * flag PostHog supports out of the box; the wrapper passes it through
     * regardless of the per-event property allowlist.
     */
    private static final String SERVER_INITIATED_PROPERTY = "$server_initiated";

    private final PostHogInterface sdk;
    private final boolean enabled;
    private final ExecutorService dispatcher;

    public PostHogClient(PostHogInterface sdk, boolean enabled) {
        this(sdk, enabled, enabled
                ? Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "posthog-dispatcher");
                    t.setDaemon(true);
                    return t;
                })
                : null);
    }

    /**
     * Visible-for-tests constructor. Allows injecting a deterministic
     * {@link ExecutorService} (e.g. a same-thread executor) so the
     * fail-open + property-filtering behavior can be asserted without
     * timing flakiness.
     */
    PostHogClient(PostHogInterface sdk, boolean enabled, ExecutorService dispatcher) {
        this.sdk = sdk;
        this.enabled = enabled;
        // Dedicated single-thread dispatcher decouples the request thread from
        // SDK call latency. The SDK is itself batched/async internally, but
        // its capture() entry point can still touch synchronized state we do
        // not want on the request hot path.
        this.dispatcher = dispatcher;
    }

    /**
     * Drain pending dispatched submissions synchronously. Visible for tests
     * only — production callers use {@link #flush()}.
     */
    void awaitPendingForTest() {
        if (dispatcher == null) {
            return;
        }
        try {
            dispatcher.submit(() -> { }).get();
        } catch (Exception e) {
            // Test-only path; surface as RuntimeException so the test fails fast.
            throw new RuntimeException(e);
        }
    }

    /**
     * Capture an analytics event. No-op when the wrapper is disabled.
     *
     * @param eventName one of the 7 names in {@link #ALLOWED_EVENTS}
     * @param distinctId PostHog distinct identifier (userId for authenticated
     *                   server-side events; frontend-supplied UUID for
     *                   anonymous-stitch flows)
     * @param properties caller-supplied event properties — filtered against
     *                   the allowlist before emission
     */
    public void capture(String eventName, String distinctId, Map<String, Object> properties) {
        if (!enabled) {
            return;
        }
        if (!ALLOWED_EVENTS.contains(eventName)) {
            log.warn("Dropping PostHog event: event_name={} dropped_reason=unknown_event", eventName);
            return;
        }

        Map<String, Object> sanitized = sanitizeProperties(eventName, properties);
        sanitized.putIfAbsent(SERVER_INITIATED_PROPERTY, true);

        PostHogCaptureOptions options = new PostHogCaptureOptions.Builder()
                .properties(sanitized)
                .build();

        dispatcher.submit(() -> {
            try {
                sdk.capture(distinctId, eventName, options);
            } catch (RuntimeException e) {
                // F-6: never propagate to the caller (we're already off-thread,
                // but defense-in-depth — log so dropped events are visible).
                log.warn(
                        "PostHog SDK threw on capture: event_name={} dropped_reason=sdk_exception message={}",
                        eventName, e.getMessage());
            }
        });
    }

    /**
     * Flush queued events synchronously. Wired to Spring's container shutdown
     * via {@code @PreDestroy} (security-findings.md F-6) so events queued
     * late in the request lifecycle are not lost when the JVM stops. The
     * disabled-mode short-circuit makes this a safe no-op in environments
     * where the SDK was never instantiated.
     */
    @PreDestroy
    public void flush() {
        if (!enabled) {
            return;
        }
        try {
            sdk.flush();
        } catch (RuntimeException e) {
            log.warn("PostHog SDK threw on flush: message={}", e.getMessage());
        }
    }

    private Map<String, Object> sanitizeProperties(String eventName, Map<String, Object> properties) {
        Map<String, Object> sanitized = new HashMap<>();
        if (properties == null || properties.isEmpty()) {
            return sanitized;
        }
        Set<String> allowed = ALLOWED_PROPERTIES.getOrDefault(eventName, Set.of());

        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String key = entry.getKey();
            if (FORBIDDEN_PROPERTIES.contains(key)) {
                log.warn(
                        "Dropping PostHog property: event_name={} property_key={} dropped_reason=forbidden_key",
                        eventName, key);
                continue;
            }
            if (!allowed.contains(key)) {
                log.warn(
                        "Dropping PostHog property: event_name={} property_key={} dropped_reason=not_in_allowlist",
                        eventName, key);
                continue;
            }
            sanitized.put(key, entry.getValue());
        }
        return sanitized;
    }
}
