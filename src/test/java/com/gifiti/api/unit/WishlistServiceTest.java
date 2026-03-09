package com.gifiti.api.unit;

import com.gifiti.api.dto.request.CreateWishlistRequest;
import com.gifiti.api.dto.request.UpdateWishlistRequest;
import com.gifiti.api.dto.response.WishlistListResponse;
import com.gifiti.api.dto.response.WishlistResponse;
import com.gifiti.api.exception.AccessDeniedException;
import com.gifiti.api.exception.ResourceNotFoundException;
import com.gifiti.api.mapper.WishlistMapper;
import com.gifiti.api.model.Wishlist;
import com.gifiti.api.model.enums.Visibility;
import com.gifiti.api.model.enums.WishlistCategory;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.List;
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

    @Nested
    @DisplayName("findAllByOwner(paginated)")
    class PaginatedFindAllByOwnerTests {

        @Test
        @DisplayName("should return paginated wishlists")
        void shouldReturnPaginatedWishlists() {
            Wishlist wishlist = Wishlist.builder()
                    .id(WISHLIST_ID)
                    .ownerUserId(USER_ID)
                    .title("Test Wishlist")
                    .build();

            WishlistResponse wishlistResponse = WishlistResponse.builder()
                    .id(WISHLIST_ID)
                    .title("Test Wishlist")
                    .itemCount(0)
                    .build();

            Page<Wishlist> page = new PageImpl<>(List.of(wishlist),
                    org.springframework.data.domain.PageRequest.of(0, 20), 1);

            when(wishlistRepository.findByOwnerUserId(eq(USER_ID), any(Pageable.class))).thenReturn(page);
            when(wishlistItemRepository.findByWishlistId(WISHLIST_ID)).thenReturn(Collections.emptyList());
            when(wishlistMapper.toResponse(wishlist, 0)).thenReturn(wishlistResponse);

            WishlistListResponse result = wishlistService.findAllByOwner(USER_ID, 0, 20);

            assertThat(result.getWishlists()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getTotalPages()).isEqualTo(1);
            assertThat(result.getCurrentPage()).isEqualTo(0);
            assertThat(result.getSize()).isEqualTo(20);
        }

        @Test
        @DisplayName("should return empty page when no wishlists")
        void shouldReturnEmptyPage() {
            Page<Wishlist> emptyPage = new PageImpl<>(Collections.emptyList(),
                    org.springframework.data.domain.PageRequest.of(0, 20), 0);

            when(wishlistRepository.findByOwnerUserId(eq(USER_ID), any(Pageable.class))).thenReturn(emptyPage);

            WishlistListResponse result = wishlistService.findAllByOwner(USER_ID, 0, 20);

            assertThat(result.getWishlists()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }
    }

    @Nested
    @DisplayName("findAllByOwner(with category filter)")
    class CategoryFilterTests {

        @Test
        @DisplayName("should filter wishlists by category")
        void shouldFilterByCategory() {
            Wishlist wishlist = Wishlist.builder()
                    .id(WISHLIST_ID)
                    .ownerUserId(USER_ID)
                    .title("Birthday List")
                    .category(WishlistCategory.BIRTHDAY)
                    .build();

            WishlistResponse wishlistResponse = WishlistResponse.builder()
                    .id(WISHLIST_ID)
                    .title("Birthday List")
                    .category(WishlistCategory.BIRTHDAY)
                    .itemCount(0)
                    .build();

            Page<Wishlist> page = new PageImpl<>(List.of(wishlist),
                    org.springframework.data.domain.PageRequest.of(0, 20), 1);

            when(wishlistRepository.findByOwnerUserIdAndCategory(eq(USER_ID), eq(WishlistCategory.BIRTHDAY), any(Pageable.class))).thenReturn(page);
            when(wishlistItemRepository.findByWishlistId(WISHLIST_ID)).thenReturn(Collections.emptyList());
            when(wishlistMapper.toResponse(wishlist, 0)).thenReturn(wishlistResponse);

            WishlistListResponse result = wishlistService.findAllByOwner(USER_ID, WishlistCategory.BIRTHDAY, 0, 20);

            assertThat(result.getWishlists()).hasSize(1);
            assertThat(result.getWishlists().get(0).getCategory()).isEqualTo(WishlistCategory.BIRTHDAY);
            verify(wishlistRepository).findByOwnerUserIdAndCategory(eq(USER_ID), eq(WishlistCategory.BIRTHDAY), any(Pageable.class));
            verify(wishlistRepository, never()).findByOwnerUserId(eq(USER_ID), any(Pageable.class));
        }

        @Test
        @DisplayName("should return all wishlists when category is null")
        void shouldReturnAllWhenCategoryNull() {
            Page<Wishlist> emptyPage = new PageImpl<>(Collections.emptyList(),
                    org.springframework.data.domain.PageRequest.of(0, 20), 0);

            when(wishlistRepository.findByOwnerUserId(eq(USER_ID), any(Pageable.class))).thenReturn(emptyPage);

            wishlistService.findAllByOwner(USER_ID, null, 0, 20);

            verify(wishlistRepository).findByOwnerUserId(eq(USER_ID), any(Pageable.class));
            verify(wishlistRepository, never()).findByOwnerUserIdAndCategory(any(), any(), any(Pageable.class));
        }
    }

    @Nested
    @DisplayName("rotateShareableId()")
    class RotateShareableIdTests {

        @Test
        @DisplayName("should generate new shareable ID")
        void shouldGenerateNewShareableId() {
            String oldShareableId = "old-nano-id";
            Wishlist wishlist = Wishlist.builder()
                    .id(WISHLIST_ID)
                    .ownerUserId(USER_ID)
                    .shareableId(oldShareableId)
                    .build();

            WishlistResponse response = WishlistResponse.builder()
                    .id(WISHLIST_ID)
                    .shareableId("new-nano-id")
                    .build();

            when(wishlistRepository.findById(WISHLIST_ID)).thenReturn(Optional.of(wishlist));
            when(wishlistRepository.save(any(Wishlist.class))).thenAnswer(inv -> inv.getArgument(0));
            when(wishlistItemRepository.findByWishlistId(WISHLIST_ID)).thenReturn(Collections.emptyList());
            when(wishlistMapper.toResponse(any(Wishlist.class), eq(0))).thenReturn(response);

            wishlistService.rotateShareableId(WISHLIST_ID, USER_ID);

            verify(wishlistRepository).save(any(Wishlist.class));
            // The shareableId should have been changed from the old one
            assertThat(wishlist.getShareableId()).isNotEqualTo(oldShareableId);
        }

        @Test
        @DisplayName("should throw AccessDeniedException for non-owner")
        void shouldThrowAccessDeniedForNonOwner() {
            Wishlist wishlist = Wishlist.builder()
                    .id(WISHLIST_ID)
                    .ownerUserId(USER_ID)
                    .build();

            when(wishlistRepository.findById(WISHLIST_ID)).thenReturn(Optional.of(wishlist));

            assertThatThrownBy(() -> wishlistService.rotateShareableId(WISHLIST_ID, OTHER_USER_ID))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }
}
