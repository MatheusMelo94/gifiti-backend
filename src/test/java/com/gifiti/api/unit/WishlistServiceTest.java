package com.gifiti.api.unit;

import com.gifiti.api.dto.request.CreateWishlistRequest;
import com.gifiti.api.dto.request.UpdateWishlistRequest;
import com.gifiti.api.dto.response.WishlistResponse;
import com.gifiti.api.exception.AccessDeniedException;
import com.gifiti.api.exception.ResourceNotFoundException;
import com.gifiti.api.mapper.WishlistMapper;
import com.gifiti.api.model.Wishlist;
import com.gifiti.api.model.enums.Visibility;
import com.gifiti.api.repository.ReservationRepository;
import com.gifiti.api.repository.WishlistItemRepository;
import com.gifiti.api.repository.WishlistRepository;
import com.gifiti.api.service.WishlistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WishlistService business logic.
 */
@ExtendWith(MockitoExtension.class)
class WishlistServiceTest {

    @Mock
    private WishlistRepository wishlistRepository;

    @Mock
    private WishlistItemRepository wishlistItemRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private WishlistMapper wishlistMapper;

    @InjectMocks
    private WishlistService wishlistService;

    private static final String USER_ID = "user123";
    private static final String OTHER_USER_ID = "other456";
    private static final String WISHLIST_ID = "wishlist789";

    @Nested
    @DisplayName("create()")
    class CreateTests {

        @Test
        @DisplayName("should create wishlist successfully")
        void shouldCreateWishlist() {
            CreateWishlistRequest request = CreateWishlistRequest.builder()
                    .title("My Wishlist")
                    .visibility(Visibility.PRIVATE)
                    .build();

            Wishlist entity = Wishlist.builder()
                    .id(WISHLIST_ID)
                    .ownerUserId(USER_ID)
                    .title("My Wishlist")
                    .visibility(Visibility.PRIVATE)
                    .build();

            WishlistResponse response = WishlistResponse.builder()
                    .id(WISHLIST_ID)
                    .title("My Wishlist")
                    .visibility(Visibility.PRIVATE)
                    .itemCount(0)
                    .build();

            when(wishlistMapper.toEntity(request, USER_ID)).thenReturn(entity);
            when(wishlistRepository.save(entity)).thenReturn(entity);
            when(wishlistMapper.toResponse(entity, 0)).thenReturn(response);

            WishlistResponse result = wishlistService.create(request, USER_ID);

            assertThat(result.getId()).isEqualTo(WISHLIST_ID);
            assertThat(result.getTitle()).isEqualTo("My Wishlist");
            verify(wishlistRepository).save(entity);
        }
    }

    @Nested
    @DisplayName("findById()")
    class FindByIdTests {

        @Test
        @DisplayName("should return wishlist for owner")
        void shouldReturnWishlistForOwner() {
            Wishlist wishlist = Wishlist.builder()
                    .id(WISHLIST_ID)
                    .ownerUserId(USER_ID)
                    .title("Test Wishlist")
                    .build();

            WishlistResponse response = WishlistResponse.builder()
                    .id(WISHLIST_ID)
                    .title("Test Wishlist")
                    .build();

            when(wishlistRepository.findById(WISHLIST_ID)).thenReturn(Optional.of(wishlist));
            when(wishlistItemRepository.findByWishlistId(WISHLIST_ID)).thenReturn(Collections.emptyList());
            when(wishlistMapper.toResponse(wishlist, 0)).thenReturn(response);

            WishlistResponse result = wishlistService.findById(WISHLIST_ID, USER_ID);

            assertThat(result.getId()).isEqualTo(WISHLIST_ID);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException for non-existent wishlist")
        void shouldThrowResourceNotFoundForNonExistent() {
            when(wishlistRepository.findById("nonexistent")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> wishlistService.findById("nonexistent", USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw AccessDeniedException for non-owner")
        void shouldThrowAccessDeniedForNonOwner() {
            Wishlist wishlist = Wishlist.builder()
                    .id(WISHLIST_ID)
                    .ownerUserId(USER_ID)
                    .build();

            when(wishlistRepository.findById(WISHLIST_ID)).thenReturn(Optional.of(wishlist));

            assertThatThrownBy(() -> wishlistService.findById(WISHLIST_ID, OTHER_USER_ID))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Nested
    @DisplayName("update()")
    class UpdateTests {

        @Test
        @DisplayName("should update wishlist for owner")
        void shouldUpdateWishlistForOwner() {
            Wishlist wishlist = Wishlist.builder()
                    .id(WISHLIST_ID)
                    .ownerUserId(USER_ID)
                    .title("Original Title")
                    .build();

            UpdateWishlistRequest request = UpdateWishlistRequest.builder()
                    .title("Updated Title")
                    .build();

            WishlistResponse response = WishlistResponse.builder()
                    .id(WISHLIST_ID)
                    .title("Updated Title")
                    .build();

            when(wishlistRepository.findById(WISHLIST_ID)).thenReturn(Optional.of(wishlist));
            when(wishlistRepository.save(any())).thenReturn(wishlist);
            when(wishlistItemRepository.findByWishlistId(WISHLIST_ID)).thenReturn(Collections.emptyList());
            when(wishlistMapper.toResponse(any(), eq(0))).thenReturn(response);

            WishlistResponse result = wishlistService.update(WISHLIST_ID, request, USER_ID);

            assertThat(result.getTitle()).isEqualTo("Updated Title");
            verify(wishlistMapper).updateEntity(wishlist, request);
        }
    }

    @Nested
    @DisplayName("delete()")
    class DeleteTests {

        @Test
        @DisplayName("should delete wishlist and cascade delete items")
        void shouldDeleteWishlistAndCascadeDeleteItems() {
            Wishlist wishlist = Wishlist.builder()
                    .id(WISHLIST_ID)
                    .ownerUserId(USER_ID)
                    .build();

            when(wishlistRepository.findById(WISHLIST_ID)).thenReturn(Optional.of(wishlist));
            when(wishlistItemRepository.findByWishlistId(WISHLIST_ID)).thenReturn(Collections.emptyList());

            wishlistService.delete(WISHLIST_ID, USER_ID);

            verify(wishlistItemRepository).deleteByWishlistId(WISHLIST_ID);
            verify(wishlistRepository).delete(wishlist);
        }

        @Test
        @DisplayName("should throw AccessDeniedException for non-owner")
        void shouldThrowAccessDeniedForNonOwner() {
            Wishlist wishlist = Wishlist.builder()
                    .id(WISHLIST_ID)
                    .ownerUserId(USER_ID)
                    .build();

            when(wishlistRepository.findById(WISHLIST_ID)).thenReturn(Optional.of(wishlist));

            assertThatThrownBy(() -> wishlistService.delete(WISHLIST_ID, OTHER_USER_ID))
                    .isInstanceOf(AccessDeniedException.class);

            verify(wishlistRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("findByShareableId()")
    class FindByShareableIdTests {

        @Test
        @DisplayName("should find wishlist by shareable ID")
        void shouldFindWishlistByShareableId() {
            String shareableId = "abc123nanoid";
            Wishlist wishlist = Wishlist.builder()
                    .id(WISHLIST_ID)
                    .shareableId(shareableId)
                    .build();

            when(wishlistRepository.findByShareableId(shareableId)).thenReturn(Optional.of(wishlist));

            Wishlist result = wishlistService.findByShareableId(shareableId);

            assertThat(result.getShareableId()).isEqualTo(shareableId);
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException for invalid shareable ID")
        void shouldThrowResourceNotFoundForInvalidShareableId() {
            when(wishlistRepository.findByShareableId("invalid")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> wishlistService.findByShareableId("invalid"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
