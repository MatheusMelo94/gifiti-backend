package com.gifiti.api.mapper;

import com.gifiti.api.dto.request.CreateItemRequest;
import com.gifiti.api.dto.request.UpdateItemRequest;
import com.gifiti.api.dto.response.WishlistItemResponse;
import com.gifiti.api.model.WishlistItem;
import org.springframework.stereotype.Component;

/**
 * Mapper for WishlistItem entity to DTO transformations.
 * PRIVACY: This mapper enforces privacy-by-design by NEVER exposing reserverId.
 */
@Component
public class WishlistItemMapper {

    /**
     * Convert CreateItemRequest to WishlistItem entity.
     *
     * @param request Creation request
     * @param wishlistId Parent wishlist ID
     * @param ownerUserId Owner's user ID (denormalized for performance)
     * @return WishlistItem entity
     */
    public WishlistItem toEntity(CreateItemRequest request, String wishlistId, String ownerUserId) {
        return WishlistItem.builder()
                .wishlistId(wishlistId)
                .ownerUserId(ownerUserId)
                .name(request.getName())
                .description(request.getDescription())
                .productLink(request.getProductLink())
                .imageUrl(request.getImageUrl())
                .price(request.getPrice())
                .priority(request.getPriority())
                .build();
    }

    /**
     * Convert WishlistItem entity to WishlistItemResponse DTO.
     * PRIVACY: reserverId is deliberately NOT mapped - owner must NEVER know who reserved.
     *
     * @param item WishlistItem entity
     * @return WishlistItemResponse DTO
     */
    public WishlistItemResponse toResponse(WishlistItem item) {
        return WishlistItemResponse.builder()
                .id(item.getId())
                .name(item.getName())
                .description(item.getDescription())
                .productLink(item.getProductLink())
                .imageUrl(item.getImageUrl())
                .price(item.getPrice())
                .priority(item.getPriority())
                .status(item.getStatus())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
        // PRIVACY: reserverId is intentionally excluded from response
    }

    /**
     * Update existing WishlistItem entity with UpdateItemRequest data.
     * Only non-null fields are updated (partial update).
     *
     * @param item Entity to update
     * @param request Update request
     */
    public void updateEntity(WishlistItem item, UpdateItemRequest request) {
        if (request.getName() != null) {
            item.setName(request.getName());
        }
        if (request.getDescription() != null) {
            item.setDescription(request.getDescription());
        }
        if (request.getProductLink() != null) {
            item.setProductLink(request.getProductLink());
        }
        if (request.getImageUrl() != null) {
            item.setImageUrl(request.getImageUrl());
        }
        if (request.getPrice() != null) {
            item.setPrice(request.getPrice());
        }
        if (request.getPriority() != null) {
            item.setPriority(request.getPriority());
        }
    }
}
