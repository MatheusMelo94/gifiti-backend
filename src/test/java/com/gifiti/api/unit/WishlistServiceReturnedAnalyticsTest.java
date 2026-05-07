package com.gifiti.api.unit;

import com.gifiti.api.analytics.PostHogClient;
import com.gifiti.api.analytics.PostHogProperties;
import com.gifiti.api.analytics.WishlistReturnedDedupeCache;
import com.gifiti.api.dto.response.WishlistResponse;
import com.gifiti.api.exception.AccessDeniedException;
import com.gifiti.api.mapper.WishlistMapper;
import com.gifiti.api.model.Wishlist;
import com.gifiti.api.repository.ReservationRepository;
import com.gifiti.api.repository.WishlistItemRepository;
import com.gifiti.api.repository.WishlistRepository;
import com.gifiti.api.service.WishlistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T9 — wishlist_returned emission. Per OQ-1 (X): owner returns to view
 * their own wishlist N+ days after creation. Non-owner reads are
 * already excluded via {@code findAndVerifyOwnership}'s
 * {@link AccessDeniedException}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WishlistServiceReturnedAnalyticsTest {

    @Mock private WishlistRepository wishlistRepository;
    @Mock private WishlistItemRepository wishlistItemRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private WishlistMapper wishlistMapper;
    @Mock private PostHogClient postHogClient;
    @Mock private WishlistReturnedDedupeCache wishlistReturnedDedupeCache;

    @InjectMocks private WishlistService service;

    private static final String OWNER_ID = "owner-9";
    private static final String OTHER_USER_ID = "stranger-9";
    private static final String WISHLIST_ID = "wl-9";

    @BeforeEach
    void wireDefaults() {
        // Threshold: 7 days, matching the plan's recommended default (OQ-4).
        PostHogProperties props = new PostHogProperties(true, "key", "https://us.i.posthog.com", 7);
        ReflectionTestUtils.setField(service, "postHogProperties", props);

        // Dedupe cache permits emission by default in this suite. The
        // dedupe-specific contract is exercised in
        // WishlistServiceReturnedDedupeTest. Per ADR 0007 § Finding 0004.
        when(wishlistReturnedDedupeCache.tryReserve(anyString(), anyString()))
                .thenReturn(true);

        when(wishlistMapper.toResponse(any(Wishlist.class), any(Integer.class)))
                .thenReturn(WishlistResponse.builder().id(WISHLIST_ID).build());
        when(wishlistItemRepository.findByWishlistId(anyString()))
                .thenReturn(java.util.List.of());
    }

    private Wishlist wishlistAged(int daysAgo) {
        return Wishlist.builder()
                .id(WISHLIST_ID)
                .ownerUserId(OWNER_ID)
                .createdAt(Instant.now().minus(daysAgo, ChronoUnit.DAYS))
                .build();
    }

    @Test
    @DisplayName("findById() emits wishlist_returned when owner returns >= N days after creation")
    void emits_when_owner_returns_after_threshold() {
        when(wishlistRepository.findById(WISHLIST_ID))
                .thenReturn(Optional.of(wishlistAged(10)));

        service.findById(WISHLIST_ID, OWNER_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> propsCaptor =
                ArgumentCaptor.forClass(Map.class);
        verify(postHogClient).capture(eq("wishlist_returned"), eq(OWNER_ID), propsCaptor.capture());

        Map<String, Object> props = propsCaptor.getValue();
        assertThat(props).containsEntry("user_id", OWNER_ID);
        // days_since_creation should be ~10; allow off-by-one due to clock
        // boundary at the test moment.
        Object days = props.get("days_since_creation");
        assertThat(days).isInstanceOfSatisfying(Long.class, value ->
                assertThat(value).isBetween(9L, 11L));
    }

    @Test
    @DisplayName("findById() does NOT emit when the wishlist is younger than the threshold")
    void no_emit_when_under_threshold() {
        when(wishlistRepository.findById(WISHLIST_ID))
                .thenReturn(Optional.of(wishlistAged(2)));

        service.findById(WISHLIST_ID, OWNER_ID);

        verify(postHogClient, never()).capture(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("findById() does NOT emit when the requester is not the owner")
    void no_emit_for_non_owner() {
        when(wishlistRepository.findById(WISHLIST_ID))
                .thenReturn(Optional.of(wishlistAged(30)));

        // Non-owner read fails with AccessDenied before we reach the
        // emission site — wishlist_returned is owner-only per OQ-1 (X).
        assertThatThrownBy(() -> service.findById(WISHLIST_ID, OTHER_USER_ID))
                .isInstanceOf(AccessDeniedException.class);

        verify(postHogClient, never()).capture(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("findById() succeeds when PostHogClient throws (fail-open)")
    void posthog_failure_does_not_propagate() {
        when(wishlistRepository.findById(WISHLIST_ID))
                .thenReturn(Optional.of(wishlistAged(30)));
        doThrow(new RuntimeException("posthog down"))
                .when(postHogClient).capture(anyString(), anyString(), any());

        assertThatCode(() -> service.findById(WISHLIST_ID, OWNER_ID))
                .doesNotThrowAnyException();
    }
}
