package com.gifiti.api.service;

import com.gifiti.api.dto.response.PublicItemResponse;
import com.gifiti.api.dto.response.PublicWishlistResponse;
import com.gifiti.api.exception.ResourceNotFoundException;
import com.gifiti.api.model.Wishlist;
import com.gifiti.api.model.WishlistItem;
import com.gifiti.api.model.enums.Visibility;
import com.gifiti.api.repository.WishlistItemRepository;
import com.gifiti.api.repository.WishlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for public (unauthenticated) wishlist access.
 * Enforces visibility checks and provides read-only access to public wishlists.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublicWishlistService {

    private final WishlistRepository wishlistRepository;
    private final WishlistItemRepository wishlistItemRepository;

    /**
     * Get a public wishlist by its shareable ID.
     * Only returns wishlists with PUBLIC visibility.
     * Returns 404 for non-existent OR private wishlists (security: don't reveal existence).
     *
     * @param shareableId The shareable identifier (NanoID)
     * @return Public wishlist response with items
     * @throws ResourceNotFoundException if wishlist not found or not public
     */
    public PublicWishlistResponse findByShareableId(String shareableId) {
        log.debug("Public access request for wishlist: {}", shareableId);

        // Find wishlist by shareable ID
        Wishlist wishlist = wishlistRepository.findByShareableId(shareableId)
                .orElseThrow(() -> {
                    log.debug("Wishlist not found: {}", shareableId);
                    return new ResourceNotFoundException("Wishlist", "shareableId", shareableId);
                });

        // Security check: only PUBLIC wishlists can be viewed
        // Return 404 (not 403) to avoid revealing that the wishlist exists
        if (wishlist.getVisibility() != Visibility.PUBLIC) {
            log.debug("Access denied to private wishlist: {}", shareableId);
            throw new ResourceNotFoundException("Wishlist", "shareableId", shareableId);
        }

        // Fetch all items in the wishlist
        List<WishlistItem> items = wishlistItemRepository.findByWishlistId(wishlist.getId());

        // Map to public response (privacy-safe)
        List<PublicItemResponse> publicItems = items.stream()
                .map(this::toPublicItemResponse)
                .toList();

        log.info("Public wishlist accessed: {} with {} items", shareableId, publicItems.size());

        return PublicWishlistResponse.builder()
                .shareableId(wishlist.getShareableId())
                .title(wishlist.getTitle())
                .description(wishlist.getDescription())
                .itemCount(publicItems.size())
                .items(publicItems)
                .build();
    }

    /**
     * Map a WishlistItem to a PublicItemResponse.
     * PRIVACY: Only includes fields safe for public viewing.
     */
    private PublicItemResponse toPublicItemResponse(WishlistItem item) {
        return PublicItemResponse.builder()
                .id(item.getId())
                .name(item.getName())
                .description(item.getDescription())
                .productLink(item.getProductLink())
                .imageUrl(item.getImageUrl())
                .price(item.getPrice())
                .priority(item.getPriority())
                .status(item.getStatus())
                .build();
        // PRIVACY: No reserverId, no ownerUserId, no timestamps
    }
}
