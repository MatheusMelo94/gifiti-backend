package com.gifiti.api.mapper;

import com.gifiti.api.dto.request.CreateWishlistRequest;
import com.gifiti.api.dto.request.UpdateWishlistRequest;
import com.gifiti.api.dto.response.WishlistResponse;
import com.gifiti.api.model.Wishlist;
import org.springframework.stereotype.Component;

/**
 * Mapper for Wishlist entity to DTO transformations.
 */
@Component
public class WishlistMapper {

    /**
     * Convert CreateWishlistRequest to Wishlist entity.
     *
     * @param request Creation request
     * @param userId Owner's user ID
     * @return Wishlist entity
     */
    public Wishlist toEntity(CreateWishlistRequest request, String userId) {
        return Wishlist.builder()
                .ownerUserId(userId)
                .title(request.getTitle())
                .description(request.getDescription())
                .visibility(request.getVisibility())
                .eventDate(request.getEventDate())
                .category(request.getCategory())
                .coverImageUrl(request.getCoverImageUrl())
                .build();
    }

    /**
     * Convert Wishlist entity to WishlistResponse DTO.
     *
     * @param wishlist Wishlist entity
     * @param itemCount Number of items in the wishlist
     * @return WishlistResponse DTO
     */
    public WishlistResponse toResponse(Wishlist wishlist, int itemCount) {
        return WishlistResponse.builder()
                .id(wishlist.getId())
                .title(wishlist.getTitle())
                .description(wishlist.getDescription())
                .visibility(wishlist.getVisibility())
                .shareableId(wishlist.getShareableId())
                .eventDate(wishlist.getEventDate())
                .category(wishlist.getCategory())
                .coverImageUrl(wishlist.getCoverImageUrl())
                .itemCount(itemCount)
                .createdAt(wishlist.getCreatedAt())
                .updatedAt(wishlist.getUpdatedAt())
                .build();
    }

    /**
     * Update existing Wishlist entity with UpdateWishlistRequest data.
     * Only non-null fields are updated (partial update).
     *
     * @param wishlist Entity to update
     * @param request Update request
     */
    public void updateEntity(Wishlist wishlist, UpdateWishlistRequest request) {
        if (request.getTitle() != null) {
            wishlist.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            wishlist.setDescription(request.getDescription());
        }
        if (request.getVisibility() != null) {
            wishlist.setVisibility(request.getVisibility());
        }
        if (request.getEventDate() != null) {
            wishlist.setEventDate(request.getEventDate());
        }
        if (request.getCategory() != null) {
            wishlist.setCategory(request.getCategory());
        }
        if (request.getCoverImageUrl() != null) {
            if (request.getCoverImageUrl().isBlank()) {
                wishlist.setCoverImageUrl(null); // Empty string = remove
            } else {
                wishlist.setCoverImageUrl(request.getCoverImageUrl());
            }
        }
    }
}
