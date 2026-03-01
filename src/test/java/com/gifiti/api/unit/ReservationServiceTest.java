package com.gifiti.api.unit;

import com.gifiti.api.dto.response.ReservationResponse;
import com.gifiti.api.exception.ConflictException;
import com.gifiti.api.exception.ResourceNotFoundException;
import com.gifiti.api.model.Reservation;
import com.gifiti.api.model.WishlistItem;
import com.gifiti.api.model.enums.ItemStatus;
import com.gifiti.api.repository.ReservationRepository;
import com.gifiti.api.repository.WishlistItemRepository;
import com.gifiti.api.service.ReservationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ReservationService.
 * Tests atomicity guarantees and reservation logic.
 */
@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private WishlistItemRepository wishlistItemRepository;

    @InjectMocks
    private ReservationService reservationService;

    private static final String ITEM_ID = "item123";
    private static final String RESERVER_ID = "session:abc123";

    @Nested
    @DisplayName("reserve()")
    class ReserveTests {

        @Test
        @DisplayName("should reserve available item successfully")
        void shouldReserveAvailableItem() {
            WishlistItem item = WishlistItem.builder()
                    .id(ITEM_ID)
                    .status(ItemStatus.AVAILABLE)
                    .build();

            when(wishlistItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));
            when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(wishlistItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ReservationResponse result = reservationService.reserve(ITEM_ID, RESERVER_ID);

            assertThat(result.getItemId()).isEqualTo(ITEM_ID);
            assertThat(result.isReserved()).isTrue();
            verify(reservationRepository).save(any(Reservation.class));
            verify(wishlistItemRepository).save(argThat(i -> i.getStatus() == ItemStatus.RESERVED));
        }

        @Test
        @DisplayName("should throw ConflictException for already reserved item (fast fail)")
        void shouldThrowConflictForAlreadyReservedItem() {
            WishlistItem item = WishlistItem.builder()
                    .id(ITEM_ID)
                    .status(ItemStatus.RESERVED)
                    .build();

            when(wishlistItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));

            assertThatThrownBy(() -> reservationService.reserve(ITEM_ID, RESERVER_ID))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("already reserved");

            verify(reservationRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw ConflictException on concurrent reservation (DuplicateKeyException)")
        void shouldThrowConflictOnConcurrentReservation() {
            WishlistItem item = WishlistItem.builder()
                    .id(ITEM_ID)
                    .status(ItemStatus.AVAILABLE)
                    .build();

            when(wishlistItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));
            when(reservationRepository.save(any())).thenThrow(new DuplicateKeyException("Duplicate key"));

            assertThatThrownBy(() -> reservationService.reserve(ITEM_ID, RESERVER_ID))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("already reserved");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException for non-existent item")
        void shouldThrowResourceNotFoundForNonExistentItem() {
            when(wishlistItemRepository.findById("nonexistent")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reservationService.reserve("nonexistent", RESERVER_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("unreserve()")
    class UnreserveTests {

        @Test
        @DisplayName("should unreserve item successfully")
        void shouldUnreserveItem() {
            WishlistItem item = WishlistItem.builder()
                    .id(ITEM_ID)
                    .status(ItemStatus.RESERVED)
                    .build();

            when(wishlistItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));
            when(wishlistItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ReservationResponse result = reservationService.unreserve(ITEM_ID);

            assertThat(result.getItemId()).isEqualTo(ITEM_ID);
            assertThat(result.isReserved()).isFalse();
            verify(reservationRepository).deleteByItemId(ITEM_ID);
            verify(wishlistItemRepository).save(argThat(i -> i.getStatus() == ItemStatus.AVAILABLE));
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException for non-existent item")
        void shouldThrowResourceNotFoundForNonExistentItem() {
            when(wishlistItemRepository.findById("nonexistent")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reservationService.unreserve("nonexistent"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("deleteAllByItemIds()")
    class DeleteAllByItemIdsTests {

        @Test
        @DisplayName("should delete reservations for given item IDs")
        void shouldDeleteReservationsForGivenItemIds() {
            var itemIds = Arrays.asList("item1", "item2", "item3");

            reservationService.deleteAllByItemIds(itemIds);

            verify(reservationRepository).deleteByItemIdIn(itemIds);
        }

        @Test
        @DisplayName("should handle empty list gracefully")
        void shouldHandleEmptyListGracefully() {
            reservationService.deleteAllByItemIds(java.util.Collections.emptyList());

            verify(reservationRepository, never()).deleteByItemIdIn(any());
        }

        @Test
        @DisplayName("should handle null list gracefully")
        void shouldHandleNullListGracefully() {
            reservationService.deleteAllByItemIds(null);

            verify(reservationRepository, never()).deleteByItemIdIn(any());
        }
    }

    @Nested
    @DisplayName("deleteByItemId()")
    class DeleteByItemIdTests {

        @Test
        @DisplayName("should delete single reservation")
        void shouldDeleteSingleReservation() {
            reservationService.deleteByItemId(ITEM_ID);

            verify(reservationRepository).deleteByItemId(ITEM_ID);
        }
    }
}
