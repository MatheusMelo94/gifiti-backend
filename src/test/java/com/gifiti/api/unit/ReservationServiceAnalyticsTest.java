package com.gifiti.api.unit;

import com.gifiti.api.analytics.PostHogClient;
import com.gifiti.api.exception.ConflictException;
import com.gifiti.api.model.Reservation;
import com.gifiti.api.model.WishlistItem;
import com.gifiti.api.model.enums.ItemStatus;
import com.gifiti.api.repository.ReservationRepository;
import com.gifiti.api.repository.WishlistItemRepository;
import com.gifiti.api.service.ReservationService;
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
import org.springframework.dao.DuplicateKeyException;

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
 * T8 — item_reserved emission. Only emitted on successful reservation;
 * idempotent retries (DuplicateKeyException) and over-quota requests do
 * NOT emit.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReservationServiceAnalyticsTest {

    @Mock private ReservationRepository reservationRepository;
    @Mock private WishlistItemRepository wishlistItemRepository;
    @Mock private PostHogClient postHogClient;

    @InjectMocks private ReservationService service;

    private static final String ITEM_ID = "item-1";
    private static final String WISHLIST_ID = "wl-77";
    private static final String RESERVER_ID = "user-r";

    @BeforeEach
    void wireItem() {
        WishlistItem item = WishlistItem.builder()
                .id(ITEM_ID)
                .wishlistId(WISHLIST_ID)
                .quantity(1)
                .reservedQuantity(0)
                .status(ItemStatus.AVAILABLE)
                .build();
        when(wishlistItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));
        when(reservationRepository.save(any(Reservation.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(wishlistItemRepository.save(any(WishlistItem.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("reserve() emits item_reserved with wishlist_id, item_id, reserver_user_id on success")
    void emits_on_successful_reserve() {
        service.reserve(ITEM_ID, RESERVER_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> propsCaptor =
                ArgumentCaptor.forClass(Map.class);
        verify(postHogClient).capture(eq("item_reserved"), eq(RESERVER_ID), propsCaptor.capture());

        Map<String, Object> props = propsCaptor.getValue();
        assertThat(props).containsEntry("wishlist_id", WISHLIST_ID);
        assertThat(props).containsEntry("item_id", ITEM_ID);
        assertThat(props).containsEntry("reserver_user_id", RESERVER_ID);
    }

    @Test
    @DisplayName("reserve() does NOT emit when the item is already fully reserved")
    void no_emit_when_already_reserved() {
        WishlistItem already = WishlistItem.builder()
                .id(ITEM_ID)
                .wishlistId(WISHLIST_ID)
                .quantity(1)
                .reservedQuantity(1)
                .status(ItemStatus.RESERVED)
                .build();
        when(wishlistItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(already));

        assertThatThrownBy(() -> service.reserve(ITEM_ID, RESERVER_ID))
                .isInstanceOf(ConflictException.class);

        verify(postHogClient, never()).capture(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("reserve() does NOT emit when DuplicateKeyException signals an idempotent retry")
    void no_emit_on_duplicate_key() {
        when(reservationRepository.save(any(Reservation.class)))
                .thenThrow(new DuplicateKeyException("dup"));

        assertThatThrownBy(() -> service.reserve(ITEM_ID, RESERVER_ID))
                .isInstanceOf(ConflictException.class);

        verify(postHogClient, never()).capture(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("reserve() succeeds even when PostHogClient throws (fail-open)")
    void posthog_failure_does_not_propagate() {
        doThrow(new RuntimeException("posthog down"))
                .when(postHogClient).capture(anyString(), anyString(), any());

        assertThatCode(() -> service.reserve(ITEM_ID, RESERVER_ID))
                .doesNotThrowAnyException();
    }
}
