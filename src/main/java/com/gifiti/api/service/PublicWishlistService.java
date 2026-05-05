package com.gifiti.api.service;

import com.gifiti.api.dto.response.PublicItemResponse;
import com.gifiti.api.dto.response.PublicWishlistResponse;
import com.gifiti.api.exception.ResourceNotFoundException;
import com.gifiti.api.model.User;
import com.gifiti.api.model.Wishlist;
import com.gifiti.api.model.WishlistItem;
import com.gifiti.api.model.enums.Visibility;
import com.gifiti.api.repository.UserRepository;
import com.gifiti.api.repository.WishlistItemRepository;
import com.gifiti.api.repository.WishlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
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
    private final UserRepository userRepository;
    private final MessageSource messageSource;

    /**
     * Bundle key for the localized owner displayName fallback shown to
     * anonymous viewers when the owner has not set a displayName.
     * Replaces the pre-006 email-prefix fallback (Security finding F-2).
     */
    private static final String OWNER_FALLBACK_KEY = "wishlist.owner.anonymous.fallback";

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
                    return new ResourceNotFoundException(
                            ResourceNotFoundException.KEY_NOT_FOUND_WITH_FIELD,
                            "Wishlist", "shareableId", shareableId);
                });

        // Security check: only PUBLIC wishlists can be viewed
        // Return 404 (not 403) to avoid revealing that the wishlist exists
        if (wishlist.getVisibility() != Visibility.PUBLIC) {
            log.debug("Access denied to private wishlist: {}", shareableId);
            throw new ResourceNotFoundException(
                    ResourceNotFoundException.KEY_NOT_FOUND_WITH_FIELD,
                    "Wishlist", "shareableId", shareableId);
        }

        // Look up owner display name (PRIVACY: only displayName, never email or ID)
        String ownerDisplayName = resolveOwnerDisplayName(wishlist.getOwnerUserId());

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
                .coverImageUrl(wishlist.getCoverImageUrl())
                .ownerDisplayName(ownerDisplayName)
                .eventDate(wishlist.getEventDate())
                .itemCount(publicItems.size())
                .items(publicItems)
                .build();
    }

    /**
     * Resolve owner display name with a localized fallback for anonymous
     * viewers.
     *
     * <p>PRIVACY (Security finding F-2): NEVER returns an email-derived value.
     * When {@code displayName} is null/blank or the owner record is missing,
     * the response uses {@link MessageSource} to resolve the
     * {@code wishlist.owner.anonymous.fallback} key against the request locale
     * (en-US: "Wishlist owner"; pt-BR: "Anônimo"). The pre-006 email-prefix
     * fallback was removed entirely — there is no code path from this method
     * back to the owner's email.
     */
    private String resolveOwnerDisplayName(String ownerUserId) {
        User owner = userRepository.findById(ownerUserId).orElse(null);

        if (owner != null && owner.getDisplayName() != null && !owner.getDisplayName().isBlank()) {
            return owner.getDisplayName();
        }

        return messageSource.getMessage(
                OWNER_FALLBACK_KEY,
                null,
                LocaleContextHolder.getLocale());
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
                .quantity(item.getQuantity())
                .reservedQuantity(item.getReservedQuantity())
                .remainingQuantity(item.getQuantity() - item.getReservedQuantity())
                .status(item.getStatus())
                .build();
        // PRIVACY: No reserverId, no ownerUserId, no timestamps
    }
}
