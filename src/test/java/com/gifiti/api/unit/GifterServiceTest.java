package com.gifiti.api.unit;

import com.gifiti.api.dto.response.GifterReservationListResponse;
import com.gifiti.api.dto.response.ReservationResponse;
import com.gifiti.api.exception.ResourceNotFoundException;
import com.gifiti.api.model.Reservation;
import com.gifiti.api.model.Wishlist;
import com.gifiti.api.model.WishlistItem;
import com.gifiti.api.model.enums.ItemStatus;
import com.gifiti.api.repository.ReservationRepository;
import com.gifiti.api.repository.WishlistItemRepository;
import com.gifiti.api.repository.WishlistRepository;
import com.gifiti.api.service.GifterService;
import com.gifiti.api.service.ReservationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GifterService.
 */
@ExtendWith(MockitoExtension.class)
class GifterServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private WishlistItemRepository wishlistItemRepository;

    @Mock
    private WishlistRepository wishlistRepository;

    @Mock
    private ReservationService reservationService;

    @InjectMocks
    private GifterService gifterService;

    @Nested
    @DisplayName("listReservations")
    class ListReservationsTests {

        @Test
        @DisplayName("should return all reservations for gifter")
        void shouldReturnAllReservationsForGifter() {
            String gifterId = "gifter-123";

            Reservation reservation = Reservation.builder()
                    .itemId("item-1")
                    .reserverId(gifterId)
                    .createdAt(Instant.now())
                    .build();

            WishlistItem item = WishlistItem.builder()
                    .id("item-1")
                    .name("Headphones")
                    .price(BigDecimal.valueOf(349.99))
                    .wishlistId("wl-1")
                    .build();

            Wishlist wishlist = Wishlist.builder()
                    .id("wl-1")
                    .title("Birthday List")
                    .shareableId("abc123")
                    .build();

            when(reservationRepository.findByReserverId(gifterId)).thenReturn(List.of(reservation));
            when(wishlistItemRepository.findById("item-1")).thenReturn(Optional.of(item));
            when(wishlistRepository.findById("wl-1")).thenReturn(Optional.of(wishlist));

            GifterReservationListResponse response = gifterService.listReservations(gifterId);

            assertThat(response.getTotalCount()).isEqualTo(1);
            assertThat(response.getReservations()).hasSize(1);
            assertThat(response.getReservations().get(0).getItemName()).isEqualTo("Headphones");
            assertThat(response.getReservations().get(0).getWishlistTitle()).isEqualTo("Birthday List");
        }

        @Test
        @DisplayName("should return empty list when no reservations")
        void shouldReturnEmptyListWhenNoReservations() {
            when(reservationRepository.findByReserverId("gifter-no-res")).thenReturn(List.of());

            GifterReservationListResponse response = gifterService.listReservations("gifter-no-res");

            assertThat(response.getTotalCount()).isZero();
            assertThat(response.getReservations()).isEmpty();
        }
    }

    @Nested
    @DisplayName("cancelReservation")
    class CancelReservationTests {

        @Test
        @DisplayName("should cancel own reservation and decrement quantity")
        void shouldCancelOwnReservation() {
            String gifterId = "gifter-123";

            Reservation reservation = Reservation.builder()
                    .itemId("item-1")
                    .reserverId(gifterId)
                    .quantity(1)
                    .build();

            WishlistItem item = WishlistItem.builder()
                    .id("item-1")
                    .quantity(3)
                    .reservedQuantity(2)
                    .status(ItemStatus.AVAILABLE)
                    .build();

            when(reservationRepository.findByItemIdAndReserverId("item-1", gifterId)).thenReturn(Optional.of(reservation));
            when(wishlistItemRepository.findById("item-1")).thenReturn(Optional.of(item));
            when(wishlistItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ReservationResponse response = gifterService.cancelReservation("item-1", gifterId);

            assertThat(response.isReserved()).isFalse();
            verify(reservationRepository).delete(reservation);
            assertThat(item.getReservedQuantity()).isEqualTo(1);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException for other gifter's reservation")
        void shouldThrowForOtherGiftersReservation() {
            when(reservationRepository.findByItemIdAndReserverId("item-1", "gifter-123")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> gifterService.cancelReservation("item-1", "gifter-123"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException for non-existent reservation")
        void shouldThrowForNonExistentReservation() {
            when(reservationRepository.findByItemIdAndReserverId("nonexistent", "gifter-123")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> gifterService.cancelReservation("nonexistent", "gifter-123"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
