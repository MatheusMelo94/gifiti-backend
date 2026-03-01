package com.gifiti.api.service;

import com.gifiti.api.dto.request.CreateWishlistRequest;
import com.gifiti.api.dto.request.UpdateWishlistRequest;
import com.gifiti.api.dto.response.WishlistListResponse;
import com.gifiti.api.dto.response.WishlistResponse;
import com.gifiti.api.exception.AccessDeniedException;
import com.gifiti.api.exception.ResourceNotFoundException;
import com.gifiti.api.mapper.WishlistMapper;
import com.gifiti.api.model.Wishlist;
import com.gifiti.api.model.WishlistItem;
import com.gifiti.api.repository.ReservationRepository;
import com.gifiti.api.repository.WishlistItemRepository;
import com.gifiti.api.repository.WishlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for wishlist CRUD operations with ownership validation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final WishlistItemRepository wishlistItemRepository;
    private final ReservationRepository reservationRepository;
    private final WishlistMapper wishlistMapper;

    /**
     * Create a new wishlist for the authenticated user.
     *
     * @param request Wishlist creation details
     * @param userId Owner's user ID
     * @return Created wishlist response
     */
    public WishlistResponse create(CreateWishlistRequest request, String userId) {
        log.info("Creating wishlist '{}' for user: {}", request.getTitle(), userId);

        Wishlist wishlist = wishlistMapper.toEntity(request, userId);
        Wishlist saved = wishlistRepository.save(wishlist);

        log.info("Wishlist created with ID: {}", saved.getId());
        return wishlistMapper.toResponse(saved, 0);
    }

    /**
     * Get all wishlists owned by a user.
     *
     * @param userId Owner's user ID
     * @return List of wishlist responses
     */
    public WishlistListResponse findAllByOwner(String userId) {
        log.debug("Finding all wishlists for user: {}", userId);

        List<Wishlist> wishlists = wishlistRepository.findByOwnerUserId(userId);
        List<WishlistResponse> responses = wishlists.stream()
                .map(wishlist -> wishlistMapper.toResponse(wishlist, getItemCount(wishlist.getId())))
                .toList();

        return WishlistListResponse.builder()
                .wishlists(responses)
                .build();
    }

    /**
     * Get a wishlist by ID, verifying ownership.
     *
     * @param id Wishlist ID
     * @param userId Requesting user's ID
     * @return Wishlist response
     * @throws ResourceNotFoundException if wishlist not found
     * @throws AccessDeniedException if user is not the owner
     */
    public WishlistResponse findById(String id, String userId) {
        log.debug("Finding wishlist {} for user {}", id, userId);

        Wishlist wishlist = findAndVerifyOwnership(id, userId);
        return wishlistMapper.toResponse(wishlist, getItemCount(id));
    }

    /**
     * Update a wishlist, verifying ownership.
     *
     * @param id Wishlist ID
     * @param request Update details
     * @param userId Requesting user's ID
     * @return Updated wishlist response
     * @throws ResourceNotFoundException if wishlist not found
     * @throws AccessDeniedException if user is not the owner
     */
    public WishlistResponse update(String id, UpdateWishlistRequest request, String userId) {
        log.info("Updating wishlist {} for user {}", id, userId);

        Wishlist wishlist = findAndVerifyOwnership(id, userId);
        wishlistMapper.updateEntity(wishlist, request);
        Wishlist saved = wishlistRepository.save(wishlist);

        log.info("Wishlist {} updated successfully", id);
        return wishlistMapper.toResponse(saved, getItemCount(id));
    }

    /**
     * Delete a wishlist and all its items, verifying ownership.
     *
     * @param id Wishlist ID
     * @param userId Requesting user's ID
     * @throws ResourceNotFoundException if wishlist not found
     * @throws AccessDeniedException if user is not the owner
     */
    public void delete(String id, String userId) {
        log.info("Deleting wishlist {} for user {}", id, userId);

        Wishlist wishlist = findAndVerifyOwnership(id, userId);

        // Get all item IDs for cascade delete of reservations
        List<String> itemIds = wishlistItemRepository.findByWishlistId(id).stream()
                .map(WishlistItem::getId)
                .toList();

        // Cascade delete all reservations for these items
        if (!itemIds.isEmpty()) {
            reservationRepository.deleteByItemIdIn(itemIds);
            log.debug("Cascade deleted reservations for {} items in wishlist {}", itemIds.size(), id);
        }

        // Cascade delete all items in the wishlist
        wishlistItemRepository.deleteByWishlistId(id);
        log.debug("Cascade deleted items for wishlist {}", id);

        wishlistRepository.delete(wishlist);
        log.info("Wishlist {} deleted successfully", id);
    }

    /**
     * Find a wishlist by ID and verify the user is the owner.
     *
     * @param id Wishlist ID
     * @param userId User ID to verify ownership
     * @return Wishlist entity
     * @throws ResourceNotFoundException if wishlist not found
     * @throws AccessDeniedException if user is not the owner
     */
    public Wishlist findAndVerifyOwnership(String id, String userId) {
        Wishlist wishlist = wishlistRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Wishlist", "id", id));

        if (!wishlist.getOwnerUserId().equals(userId)) {
            log.warn("Access denied: user {} is not owner of wishlist {}", userId, id);
            throw new AccessDeniedException("Access denied");
        }

        return wishlist;
    }

    /**
     * Find a wishlist by its shareable ID.
     *
     * @param shareableId Shareable identifier
     * @return Wishlist entity
     * @throws ResourceNotFoundException if wishlist not found
     */
    public Wishlist findByShareableId(String shareableId) {
        return wishlistRepository.findByShareableId(shareableId)
                .orElseThrow(() -> new ResourceNotFoundException("Wishlist", "shareableId", shareableId));
    }

    /**
     * Get the count of items in a wishlist.
     *
     * @param wishlistId Wishlist ID
     * @return Number of items
     */
    private int getItemCount(String wishlistId) {
        return wishlistItemRepository.findByWishlistId(wishlistId).size();
    }
}
