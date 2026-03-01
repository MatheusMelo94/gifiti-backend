package com.gifiti.api.service;

import com.gifiti.api.dto.request.CreateItemRequest;
import com.gifiti.api.dto.request.UpdateItemRequest;
import com.gifiti.api.dto.response.ItemListResponse;
import com.gifiti.api.dto.response.WishlistItemResponse;
import com.gifiti.api.exception.AccessDeniedException;
import com.gifiti.api.exception.ResourceNotFoundException;
import com.gifiti.api.mapper.WishlistItemMapper;
import com.gifiti.api.model.Wishlist;
import com.gifiti.api.model.WishlistItem;
import com.gifiti.api.repository.ReservationRepository;
import com.gifiti.api.repository.WishlistItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for wishlist item CRUD operations with ownership validation.
 * Enforces that users can only modify items in wishlists they own.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WishlistItemService {

    private final WishlistItemRepository wishlistItemRepository;
    private final ReservationRepository reservationRepository;
    private final WishlistItemMapper wishlistItemMapper;
    private final WishlistService wishlistService;

    /**
     * Create a new item in a wishlist.
     *
     * @param wishlistId Wishlist ID
     * @param request Item creation details
     * @param userId Owner's user ID
     * @return Created item response
     * @throws ResourceNotFoundException if wishlist not found
     * @throws AccessDeniedException if user is not the wishlist owner
     */
    public WishlistItemResponse create(String wishlistId, CreateItemRequest request, String userId) {
        log.info("Creating item '{}' in wishlist {} for user {}", request.getName(), wishlistId, userId);

        // Verify user owns the wishlist
        Wishlist wishlist = wishlistService.findAndVerifyOwnership(wishlistId, userId);

        WishlistItem item = wishlistItemMapper.toEntity(request, wishlistId, userId);
        WishlistItem saved = wishlistItemRepository.save(item);

        log.info("Item created with ID: {} in wishlist {}", saved.getId(), wishlistId);
        return wishlistItemMapper.toResponse(saved);
    }

    /**
     * Get all items in a wishlist owned by the user.
     *
     * @param wishlistId Wishlist ID
     * @param userId Owner's user ID
     * @return List of item responses
     * @throws ResourceNotFoundException if wishlist not found
     * @throws AccessDeniedException if user is not the wishlist owner
     */
    public ItemListResponse findAllByWishlistId(String wishlistId, String userId) {
        log.debug("Finding all items in wishlist {} for user {}", wishlistId, userId);

        // Verify user owns the wishlist
        wishlistService.findAndVerifyOwnership(wishlistId, userId);

        List<WishlistItem> items = wishlistItemRepository.findByWishlistId(wishlistId);
        List<WishlistItemResponse> responses = items.stream()
                .map(wishlistItemMapper::toResponse)
                .toList();

        return ItemListResponse.builder()
                .items(responses)
                .build();
    }

    /**
     * Get a specific item by ID, verifying ownership.
     *
     * @param wishlistId Wishlist ID
     * @param itemId Item ID
     * @param userId Requesting user's ID
     * @return Item response
     * @throws ResourceNotFoundException if item not found
     * @throws AccessDeniedException if user is not the owner
     */
    public WishlistItemResponse findById(String wishlistId, String itemId, String userId) {
        log.debug("Finding item {} in wishlist {} for user {}", itemId, wishlistId, userId);

        WishlistItem item = findAndVerifyOwnership(wishlistId, itemId, userId);
        return wishlistItemMapper.toResponse(item);
    }

    /**
     * Update an item, verifying ownership.
     *
     * @param wishlistId Wishlist ID
     * @param itemId Item ID
     * @param request Update details
     * @param userId Requesting user's ID
     * @return Updated item response
     * @throws ResourceNotFoundException if item not found
     * @throws AccessDeniedException if user is not the owner
     */
    public WishlistItemResponse update(String wishlistId, String itemId, UpdateItemRequest request, String userId) {
        log.info("Updating item {} in wishlist {} for user {}", itemId, wishlistId, userId);

        WishlistItem item = findAndVerifyOwnership(wishlistId, itemId, userId);
        wishlistItemMapper.updateEntity(item, request);
        WishlistItem saved = wishlistItemRepository.save(item);

        log.info("Item {} updated successfully", itemId);
        return wishlistItemMapper.toResponse(saved);
    }

    /**
     * Delete an item, verifying ownership.
     *
     * @param wishlistId Wishlist ID
     * @param itemId Item ID
     * @param userId Requesting user's ID
     * @throws ResourceNotFoundException if item not found
     * @throws AccessDeniedException if user is not the owner
     */
    public void delete(String wishlistId, String itemId, String userId) {
        log.info("Deleting item {} in wishlist {} for user {}", itemId, wishlistId, userId);

        WishlistItem item = findAndVerifyOwnership(wishlistId, itemId, userId);

        // Cascade delete any reservation for this item
        reservationRepository.deleteByItemId(itemId);
        log.debug("Cascade deleted reservation for item {}", itemId);

        wishlistItemRepository.delete(item);

        log.info("Item {} deleted successfully", itemId);
    }

    /**
     * Delete all items belonging to a wishlist.
     * Used for cascade deletion when a wishlist is deleted.
     *
     * @param wishlistId Wishlist ID
     */
    public void deleteAllByWishlistId(String wishlistId) {
        log.info("Cascade deleting all items in wishlist {}", wishlistId);
        wishlistItemRepository.deleteByWishlistId(wishlistId);
    }

    /**
     * Count items in a wishlist.
     *
     * @param wishlistId Wishlist ID
     * @return Number of items
     */
    public int countByWishlistId(String wishlistId) {
        return wishlistItemRepository.findByWishlistId(wishlistId).size();
    }

    /**
     * Find an item and verify the user owns it via wishlist ownership.
     *
     * @param wishlistId Expected wishlist ID
     * @param itemId Item ID
     * @param userId User ID to verify ownership
     * @return WishlistItem entity
     * @throws ResourceNotFoundException if item not found
     * @throws AccessDeniedException if user is not the owner or item not in wishlist
     */
    private WishlistItem findAndVerifyOwnership(String wishlistId, String itemId, String userId) {
        WishlistItem item = wishlistItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("WishlistItem", "id", itemId));

        // Verify the item belongs to the specified wishlist
        if (!item.getWishlistId().equals(wishlistId)) {
            log.warn("Item {} does not belong to wishlist {}", itemId, wishlistId);
            throw new ResourceNotFoundException("WishlistItem", "id", itemId);
        }

        // Verify user owns the item (via denormalized ownerUserId)
        if (!item.getOwnerUserId().equals(userId)) {
            log.warn("Access denied: user {} is not owner of item {}", userId, itemId);
            throw new AccessDeniedException("Access denied");
        }

        return item;
    }
}
