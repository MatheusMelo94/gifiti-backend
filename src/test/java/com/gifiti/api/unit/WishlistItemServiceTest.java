package com.gifiti.api.unit;

import com.gifiti.api.dto.request.CreateItemRequest;
import com.gifiti.api.dto.request.UpdateItemRequest;
import com.gifiti.api.dto.response.ItemListResponse;
import com.gifiti.api.dto.response.WishlistItemResponse;
import com.gifiti.api.exception.AccessDeniedException;
import com.gifiti.api.exception.ResourceNotFoundException;
import com.gifiti.api.mapper.WishlistItemMapper;
import com.gifiti.api.model.Wishlist;
import com.gifiti.api.model.WishlistItem;
import com.gifiti.api.model.enums.ItemStatus;
import com.gifiti.api.model.enums.Priority;
import com.gifiti.api.repository.ReservationRepository;
import com.gifiti.api.repository.WishlistItemRepository;
import com.gifiti.api.service.WishlistItemService;
import com.gifiti.api.service.WishlistService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WishlistItemService business logic.
 */
@ExtendWith(MockitoExtension.class)
class WishlistItemServiceTest {

    @Mock
    private WishlistItemRepository wishlistItemRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private WishlistItemMapper wishlistItemMapper;

    @Mock
    private WishlistService wishlistService;

    @InjectMocks
    private WishlistItemService wishlistItemService;

    private static final String USER_ID = "user123";
    private static final String OTHER_USER_ID = "other456";
    private static final String WISHLIST_ID = "wishlist789";
    private static final String ITEM_ID = "item012";

    @Nested
    @DisplayName("create()")
    class CreateTests {

        @Test
        @DisplayName("should create item successfully")
        void shouldCreateItem() {
            CreateItemRequest request = CreateItemRequest.builder()
                    .name("New Item")
                    .price(new BigDecimal("29.99"))
                    .priority(Priority.HIGH)
                    .build();

            Wishlist wishlist = Wishlist.builder()
                    .id(WISHLIST_ID)
                    .ownerUserId(USER_ID)
                    .build();

            WishlistItem entity = WishlistItem.builder()
                    .id(ITEM_ID)
                    .wishlistId(WISHLIST_ID)
                    .ownerUserId(USER_ID)
                    .name("New Item")
                    .build();

            WishlistItemResponse response = WishlistItemResponse.builder()
                    .id(ITEM_ID)
                    .name("New Item")
                    .status(ItemStatus.AVAILABLE)
                    .build();

            when(wishlistService.findAndVerifyOwnership(WISHLIST_ID, USER_ID)).thenReturn(wishlist);
            when(wishlistItemMapper.toEntity(request, WISHLIST_ID, USER_ID)).thenReturn(entity);
            when(wishlistItemRepository.save(entity)).thenReturn(entity);
            when(wishlistItemMapper.toResponse(entity)).thenReturn(response);

            WishlistItemResponse result = wishlistItemService.create(WISHLIST_ID, request, USER_ID);

            assertThat(result.getId()).isEqualTo(ITEM_ID);
            assertThat(result.getName()).isEqualTo("New Item");
            verify(wishlistItemRepository).save(entity);
        }

        @Test
        @DisplayName("should throw AccessDeniedException for non-owner")
        void shouldThrowAccessDeniedForNonOwner() {
            CreateItemRequest request = CreateItemRequest.builder()
                    .name("Unauthorized Item")
                    .build();

            when(wishlistService.findAndVerifyOwnership(WISHLIST_ID, OTHER_USER_ID))
                    .thenThrow(new AccessDeniedException("Access denied"));

            assertThatThrownBy(() -> wishlistItemService.create(WISHLIST_ID, request, OTHER_USER_ID))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Nested
    @DisplayName("findById()")
    class FindByIdTests {

        @Test
        @DisplayName("should return item for owner")
        void shouldReturnItemForOwner() {
            WishlistItem item = WishlistItem.builder()
                    .id(ITEM_ID)
                    .wishlistId(WISHLIST_ID)
                    .ownerUserId(USER_ID)
                    .name("Test Item")
                    .build();

            WishlistItemResponse response = WishlistItemResponse.builder()
                    .id(ITEM_ID)
                    .name("Test Item")
                    .build();

            when(wishlistItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));
            when(wishlistItemMapper.toResponse(item)).thenReturn(response);

            WishlistItemResponse result = wishlistItemService.findById(WISHLIST_ID, ITEM_ID, USER_ID);

            assertThat(result.getId()).isEqualTo(ITEM_ID);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException for non-existent item")
        void shouldThrowResourceNotFoundForNonExistent() {
            when(wishlistItemRepository.findById("nonexistent")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> wishlistItemService.findById(WISHLIST_ID, "nonexistent", USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException for item in different wishlist")
        void shouldThrowResourceNotFoundForItemInDifferentWishlist() {
            WishlistItem item = WishlistItem.builder()
                    .id(ITEM_ID)
                    .wishlistId("different-wishlist")
                    .ownerUserId(USER_ID)
                    .build();

            when(wishlistItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));

            assertThatThrownBy(() -> wishlistItemService.findById(WISHLIST_ID, ITEM_ID, USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw AccessDeniedException for non-owner")
        void shouldThrowAccessDeniedForNonOwner() {
            WishlistItem item = WishlistItem.builder()
                    .id(ITEM_ID)
                    .wishlistId(WISHLIST_ID)
                    .ownerUserId(USER_ID)
                    .build();

            when(wishlistItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));

            assertThatThrownBy(() -> wishlistItemService.findById(WISHLIST_ID, ITEM_ID, OTHER_USER_ID))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Nested
    @DisplayName("update()")
    class UpdateTests {

        @Test
        @DisplayName("should update item for owner")
        void shouldUpdateItemForOwner() {
            WishlistItem item = WishlistItem.builder()
                    .id(ITEM_ID)
                    .wishlistId(WISHLIST_ID)
                    .ownerUserId(USER_ID)
                    .name("Original Name")
                    .build();

            UpdateItemRequest request = UpdateItemRequest.builder()
                    .name("Updated Name")
                    .build();

            WishlistItemResponse response = WishlistItemResponse.builder()
                    .id(ITEM_ID)
                    .name("Updated Name")
                    .build();

            when(wishlistItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));
            when(wishlistItemRepository.save(any())).thenReturn(item);
            when(wishlistItemMapper.toResponse(any())).thenReturn(response);

            WishlistItemResponse result = wishlistItemService.update(WISHLIST_ID, ITEM_ID, request, USER_ID);

            assertThat(result.getName()).isEqualTo("Updated Name");
            verify(wishlistItemMapper).updateEntity(item, request);
        }
    }

    @Nested
    @DisplayName("delete()")
    class DeleteTests {

        @Test
        @DisplayName("should delete item and cascade delete reservation")
        void shouldDeleteItemAndCascadeDeleteReservation() {
            WishlistItem item = WishlistItem.builder()
                    .id(ITEM_ID)
                    .wishlistId(WISHLIST_ID)
                    .ownerUserId(USER_ID)
                    .build();

            when(wishlistItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));

            wishlistItemService.delete(WISHLIST_ID, ITEM_ID, USER_ID);

            verify(reservationRepository).deleteByItemId(ITEM_ID);
            verify(wishlistItemRepository).delete(item);
        }

        @Test
        @DisplayName("should throw AccessDeniedException for non-owner")
        void shouldThrowAccessDeniedForNonOwner() {
            WishlistItem item = WishlistItem.builder()
                    .id(ITEM_ID)
                    .wishlistId(WISHLIST_ID)
                    .ownerUserId(USER_ID)
                    .build();

            when(wishlistItemRepository.findById(ITEM_ID)).thenReturn(Optional.of(item));

            assertThatThrownBy(() -> wishlistItemService.delete(WISHLIST_ID, ITEM_ID, OTHER_USER_ID))
                    .isInstanceOf(AccessDeniedException.class);

            verify(wishlistItemRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("findAllByWishlistId(paginated)")
    class PaginatedFindAllTests {

        @Test
        @DisplayName("should return paginated items")
        void shouldReturnPaginatedItems() {
            Wishlist wishlist = Wishlist.builder()
                    .id(WISHLIST_ID)
                    .ownerUserId(USER_ID)
                    .build();

            WishlistItem item = WishlistItem.builder()
                    .id(ITEM_ID)
                    .wishlistId(WISHLIST_ID)
                    .ownerUserId(USER_ID)
                    .name("Test Item")
                    .build();

            WishlistItemResponse response = WishlistItemResponse.builder()
                    .id(ITEM_ID)
                    .name("Test Item")
                    .build();

            Page<WishlistItem> page = new PageImpl<>(List.of(item),
                    org.springframework.data.domain.PageRequest.of(0, 20), 1);

            when(wishlistService.findAndVerifyOwnership(WISHLIST_ID, USER_ID)).thenReturn(wishlist);
            when(wishlistItemRepository.findByWishlistId(eq(WISHLIST_ID), any(Pageable.class))).thenReturn(page);
            when(wishlistItemMapper.toResponse(item)).thenReturn(response);

            ItemListResponse result = wishlistItemService.findAllByWishlistId(WISHLIST_ID, USER_ID, 0, 20);

            assertThat(result.getItems()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getTotalPages()).isEqualTo(1);
            assertThat(result.getCurrentPage()).isEqualTo(0);
        }

        @Test
        @DisplayName("should return empty page when no items")
        void shouldReturnEmptyPage() {
            Wishlist wishlist = Wishlist.builder()
                    .id(WISHLIST_ID)
                    .ownerUserId(USER_ID)
                    .build();

            Page<WishlistItem> emptyPage = new PageImpl<>(Collections.emptyList(),
                    org.springframework.data.domain.PageRequest.of(0, 20), 0);

            when(wishlistService.findAndVerifyOwnership(WISHLIST_ID, USER_ID)).thenReturn(wishlist);
            when(wishlistItemRepository.findByWishlistId(eq(WISHLIST_ID), any(Pageable.class))).thenReturn(emptyPage);

            ItemListResponse result = wishlistItemService.findAllByWishlistId(WISHLIST_ID, USER_ID, 0, 20);

            assertThat(result.getItems()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }
    }
}
