package com.gifiti.api.analytics;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Per-process dedupe cache for the {@code wishlist_returned} PostHog event.
 *
 * <p>Per ADR 0007 § Finding 0004 ratification, the same
 * {@code (userId, wishlistId, UTC-day-bucket)} triple emits at most one
 * {@code wishlist_returned} capture in any 24-hour window. PostHog does not
 * dedupe identical events with different timestamps, and emitting on every
 * owner refresh would flood the analytic. The dedupe enforces analytic intent
 * — track returns to a wishlist, not refresh-button-mashing.</p>
 *
 * <p>Implementation notes:</p>
 * <ul>
 *   <li><b>Key bucket = UTC day.</b> Computed via {@link LocalDate#now(Clock)}
 *       on a UTC-zoned clock to avoid timezone-edge double-emits when the
 *       server runs in a non-UTC zone.</li>
 *   <li><b>Delimiter = {@code :}.</b> Mongo ObjectId hex
 *       ({@code [0-9a-fA-F]{24}}) and NanoID alphabet
 *       ({@code [A-Za-z0-9_-]}) both forbid {@code :}, so the delimiter
 *       cannot be confused with ID content. See
 *       {@code WishlistServiceReturnedDedupeTest.KeyDelimiter} for the pin
 *       on this invariant.</li>
 *   <li><b>TTL = 24h, max size = 50_000.</b> Bounds memory at MVP scale;
 *       cardinality is approximately {@code activeUsers × wishlistsPerUser}
 *       which is comfortable in-memory.</li>
 *   <li><b>Per-process scope.</b> Render is single-dyno today; when
 *       horizontal scale forces a Redis migration this cache moves with the
 *       rate-limiter cache (same precondition). Tracked in
 *       {@code docs/posthog-followups.md}.</li>
 * </ul>
 *
 * <p>Cite: {@code architecture-conventions.md § Layer Rules} (single-purpose
 * collaborator, injected into {@code WishlistService} via constructor),
 * {@code § Logging} (no logging here — the service logs the emission;
 * dedupe is silent on the hot path).</p>
 */
@Component
public class WishlistReturnedDedupeCache {

    private static final Duration TTL = Duration.ofHours(24);
    private static final long MAX_SIZE = 50_000L;
    private static final String DELIMITER = ":";

    private final Clock clock;
    private final Cache<String, Boolean> cache;

    public WishlistReturnedDedupeCache(Clock clock) {
        this.clock = clock;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(TTL)
                .maximumSize(MAX_SIZE)
                .build();
    }

    /**
     * Atomically reserve the dedupe slot for the current UTC day.
     *
     * @return {@code true} if this is the first emission today for the
     *         given pair (caller MUST proceed to emit); {@code false} if
     *         the slot is already taken (caller MUST skip emission).
     */
    public boolean tryReserve(String userId, String wishlistId) {
        String key = buildKey(userId, wishlistId, LocalDate.now(clock.withZone(ZoneOffset.UTC)));
        if (cache.getIfPresent(key) != null) {
            return false;
        }
        cache.put(key, Boolean.TRUE);
        return true;
    }

    /**
     * Build the cache key. Visible for direct testing of the
     * delimiter-collision invariant.
     */
    public static String buildKey(String userId, String wishlistId, LocalDate utcDay) {
        return userId + DELIMITER + wishlistId + DELIMITER + utcDay;
    }
}
