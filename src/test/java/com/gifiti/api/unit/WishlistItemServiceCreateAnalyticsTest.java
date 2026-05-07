package com.gifiti.api.unit;

import com.gifiti.api.analytics.PostHogClient;
import com.gifiti.api.dto.request.CreateItemRequest;
import com.gifiti.api.dto.response.WishlistItemResponse;
import com.gifiti.api.mapper.WishlistItemMapper;
import com.gifiti.api.model.Wishlist;
import com.gifiti.api.model.WishlistItem;
import com.gifiti.api.repository.ReservationRepository;
import com.gifiti.api.repository.WishlistItemRepository;
import com.gifiti.api.service.WishlistItemService;
import com.gifiti.api.service.WishlistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T7 — item_added emission. {@code item_position} is the count of items in
 * the target wishlist BEFORE the new one is added (so the first item lands
 * at position 0). Server-derived count is authoritative — the frontend's
 * view may be stale in concurrent flows.
 */
@ExtendWith(MockitoExtension.class)
class WishlistItemServiceCreateAnalyticsTest {

    @Mock private WishlistItemRepository wishlistItemRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private WishlistItemMapper wishlistItemMapper;
    @Mock private WishlistService wishlistService;
    @Mock private PostHogClient postHogClient;

    @InjectMocks private WishlistItemService service;

    private static final String USER_ID = "owner-1";
    private static final String WISHLIST_ID = "wl-700";
    private static final String ITEM_ID = "item-200";

    @BeforeEach
    void wireMapper() {
        when(wishlistService.findAndVerifyOwnership(WISHLIST_ID, USER_ID))
                .thenReturn(Wishlist.builder().id(WISHLIST_ID).ownerUserId(USER_ID).build());
        when(wishlistItemMapper.toEntity(any(CreateItemRequest.class), anyString(), anyString()))
                .thenAnswer(inv -> WishlistItem.builder()
                        .wishlistId(inv.getArgument(1))
                        .ownerUserId(inv.getArgument(2))
                        .build());
        when(wishlistItemRepository.save(any(WishlistItem.class))).thenAnswer(inv -> {
            WishlistItem i = inv.getArgument(0);
            i.setId(ITEM_ID);
            return i;
        });
        when(wishlistItemMapper.toResponse(any(WishlistItem.class)))
                .thenReturn(WishlistItemResponse.builder().id(ITEM_ID).build());
    }

    @Test
    @DisplayName("create() emits item_added with the pre-insert count as item_position (0 for the first item)")
    void emits_with_zero_position_when_empty() {
        // Empty wishlist — pre-insert count is 0; first added item lands
        // at position 0.
        when(wishlistItemRepository.findByWishlistId(WISHLIST_ID)).thenReturn(List.of());

        service.create(WISHLIST_ID, CreateItemRequest.builder().name("Coffee").build(), USER_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> propsCaptor =
                ArgumentCaptor.forClass(Map.class);
        verify(postHogClient).capture(eq("item_added"), eq(USER_ID), propsCaptor.capture());

        Map<String, Object> props = propsCaptor.getValue();
        assertThat(props).containsEntry("wishlist_id", WISHLIST_ID);
        assertThat(props).containsEntry("user_id", USER_ID);
        assertThat(props).containsEntry("item_position", 0);
    }

    @Test
    @DisplayName("create() emits item_added with item_position equal to the existing item count")
    void emits_with_correct_position_when_nonempty() {
        when(wishlistItemRepository.findByWishlistId(WISHLIST_ID))
                .thenReturn(List.of(
                        WishlistItem.builder().id("a").build(),
                        WishlistItem.builder().id("b").build(),
                        WishlistItem.builder().id("c").build()
                ));

        service.create(WISHLIST_ID, CreateItemRequest.builder().name("Tea").build(), USER_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> propsCaptor =
                ArgumentCaptor.forClass(Map.class);
        verify(postHogClient).capture(eq("item_added"), eq(USER_ID), propsCaptor.capture());

        // Three items already there -> new item lands at position 3.
        assertThat(propsCaptor.getValue()).containsEntry("item_position", 3);
    }

    @Test
    @DisplayName("create() succeeds when PostHogClient throws (fail-open)")
    void posthog_failure_does_not_propagate() {
        when(wishlistItemRepository.findByWishlistId(WISHLIST_ID)).thenReturn(List.of());
        doThrow(new RuntimeException("posthog down"))
                .when(postHogClient).capture(anyString(), anyString(), any());

        assertThatCode(() -> service.create(
                WISHLIST_ID, CreateItemRequest.builder().name("Resilient").build(), USER_ID))
                .doesNotThrowAnyException();
    }
}
