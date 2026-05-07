package com.gifiti.api.unit;

import com.gifiti.api.analytics.PostHogClient;
import com.gifiti.api.analytics.PostHogProperties;
import com.gifiti.api.analytics.WishlistReturnedDedupeCache;
import com.gifiti.api.dto.response.WishlistResponse;
import com.gifiti.api.mapper.WishlistMapper;
import com.gifiti.api.model.Wishlist;
import com.gifiti.api.repository.ReservationRepository;
import com.gifiti.api.repository.WishlistItemRepository;
import com.gifiti.api.repository.WishlistRepository;
import com.gifiti.api.service.WishlistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T9 / Code Reviewer Finding 0004 (ADR 0007): wishlist_returned dedupe.
 *
 * <p>Per ADR 0007 § Finding 0004 ratification, wishlist_returned must be
 * deduplicated per (userId, wishlistId, UTC-day-bucket) for 24h via a
 * Caffeine cache. Tests below pin the contract: same UTC day → exactly
 * one capture; different UTC days → two captures; different
 * (userId, wishlistId) pairs → independent dedupe; the chosen delimiter
 * is collision-safe for our ID alphabets.</p>
 *
 * <p>Cite: {@code architecture-conventions.md § Layer Rules} —
 * dedupe collaborator is a separate {@code @Component} consumed by the
 * service, not invented inline.</p>
 */
class WishlistServiceReturnedDedupeTest {

    private static final String OWNER_ID = "owner-d1";
    private static final String OTHER_OWNER_ID = "owner-d2";
    private static final String WISHLIST_ID = "wl-d1";
    private static final String OTHER_WISHLIST_ID = "wl-d2";

    private WishlistRepository wishlistRepository;
    private WishlistItemRepository wishlistItemRepository;
    private ReservationRepository reservationRepository;
    private WishlistMapper wishlistMapper;
    private PostHogClient postHogClient;

    private MutableClock clock;
    private WishlistReturnedDedupeCache dedupeCache;
    private WishlistService service;

    @BeforeEach
    void setUp() {
        wishlistRepository = mock(WishlistRepository.class);
        wishlistItemRepository = mock(WishlistItemRepository.class);
        reservationRepository = mock(ReservationRepository.class);
        wishlistMapper = mock(WishlistMapper.class);
        postHogClient = mock(PostHogClient.class);

        // Anchor at 2026-05-06T12:00:00Z (mid-UTC-day to avoid edge effects
        // when we assert "advance by 24h crosses one day boundary").
        clock = new MutableClock(Instant.parse("2026-05-06T12:00:00Z"));
        dedupeCache = new WishlistReturnedDedupeCache(clock);

        PostHogProperties props = new PostHogProperties(true, "key", "https://eu.i.posthog.com", 7);
        service = new WishlistService(
                wishlistRepository,
                wishlistItemRepository,
                reservationRepository,
                wishlistMapper,
                postHogClient,
                props,
                dedupeCache);

        when(wishlistMapper.toResponse(any(Wishlist.class), any(Integer.class)))
                .thenReturn(WishlistResponse.builder().id(WISHLIST_ID).build());
        when(wishlistItemRepository.findByWishlistId(anyString()))
                .thenReturn(java.util.List.of());
    }

    private Wishlist agedWishlist(String wishlistId, String ownerId, int daysOld) {
        return Wishlist.builder()
                .id(wishlistId)
                .ownerUserId(ownerId)
                .createdAt(clock.instant().minus(daysOld, ChronoUnit.DAYS))
                .build();
    }

    @Test
    @DisplayName("dedupe: same UTC day, same (userId, wishlistId) → exactly one capture")
    void dedupe_same_day_same_pair_single_capture() {
        when(wishlistRepository.findById(WISHLIST_ID))
                .thenReturn(Optional.of(agedWishlist(WISHLIST_ID, OWNER_ID, 30)));

        service.findById(WISHLIST_ID, OWNER_ID);
        service.findById(WISHLIST_ID, OWNER_ID);

        verify(postHogClient, times(1)).capture(eq("wishlist_returned"), eq(OWNER_ID), any());
    }

    @Test
    @DisplayName("dedupe: different UTC days → two captures")
    void dedupe_different_days_two_captures() {
        when(wishlistRepository.findById(WISHLIST_ID))
                .thenReturn(Optional.of(agedWishlist(WISHLIST_ID, OWNER_ID, 30)));

        service.findById(WISHLIST_ID, OWNER_ID);

        // Advance the clock past the next UTC midnight → key bucket changes.
        clock.advance(java.time.Duration.ofHours(24));

        // Re-mock the wishlist with a still-past-threshold createdAt.
        when(wishlistRepository.findById(WISHLIST_ID))
                .thenReturn(Optional.of(agedWishlist(WISHLIST_ID, OWNER_ID, 31)));

        service.findById(WISHLIST_ID, OWNER_ID);

        verify(postHogClient, times(2)).capture(eq("wishlist_returned"), eq(OWNER_ID), any());
    }

    @Test
    @DisplayName("dedupe: different (userId, wishlistId) pairs → independent capture")
    void dedupe_different_pairs_independent() {
        when(wishlistRepository.findById(WISHLIST_ID))
                .thenReturn(Optional.of(agedWishlist(WISHLIST_ID, OWNER_ID, 30)));
        when(wishlistRepository.findById(OTHER_WISHLIST_ID))
                .thenReturn(Optional.of(agedWishlist(OTHER_WISHLIST_ID, OTHER_OWNER_ID, 30)));

        service.findById(WISHLIST_ID, OWNER_ID);
        service.findById(OTHER_WISHLIST_ID, OTHER_OWNER_ID);

        verify(postHogClient, times(1)).capture(eq("wishlist_returned"), eq(OWNER_ID), any());
        verify(postHogClient, times(1)).capture(eq("wishlist_returned"), eq(OTHER_OWNER_ID), any());
    }

    @Nested
    @DisplayName("dedupe key delimiter is collision-safe")
    class KeyDelimiter {

        /**
         * Pin the chosen delimiter (`:`) is not present in either ID alphabet.
         * userIds are Mongo ObjectId hex (24 chars `[0-9a-fA-F]`); wishlistIds
         * may also be Mongo ObjectId or NanoID-shaped (`[A-Za-z0-9_-]{21}`).
         * Neither alphabet permits `:`, so the delimiter cannot be confused
         * with ID content. This test pins that invariant against future
         * delimiter swaps that would silently re-introduce a collision risk.
         */
        @Test
        @DisplayName("delimiter ':' yields distinct keys for shifted-boundary inputs")
        void delimiter_does_not_collide_at_boundary() {
            // If the delimiter were absent from either ID alphabet AND
            // someone naively swapped it for an alphabet character, these
            // two pairs would produce the same concatenated key.
            // Verify our key builder produces distinct outputs.
            String keyA = WishlistReturnedDedupeCache.buildKey("ab", "cd", java.time.LocalDate.of(2026, 5, 6));
            String keyB = WishlistReturnedDedupeCache.buildKey("a", "bcd", java.time.LocalDate.of(2026, 5, 6));
            assertThat(keyA).isNotEqualTo(keyB);

            // And confirm `:` actually appears between segments — defense
            // against a regex-strip refactor.
            assertThat(keyA).contains(":");
        }
    }

    /**
     * Minimal mutable {@link Clock} for time-travel tests. Only the methods
     * the dedupe cache calls (instant() and zone-via-withZone) are
     * implemented.
     */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant initial) {
            this.now = initial;
        }

        void advance(java.time.Duration delta) {
            this.now = this.now.plus(delta);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
