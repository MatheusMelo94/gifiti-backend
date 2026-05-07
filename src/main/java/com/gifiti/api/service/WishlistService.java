package com.gifiti.api.service;

import com.gifiti.api.analytics.PostHogClient;
import com.gifiti.api.analytics.PostHogProperties;
import com.gifiti.api.analytics.WishlistReturnedDedupeCache;
import com.gifiti.api.dto.request.CreateWishlistRequest;
import com.gifiti.api.dto.request.UpdateWishlistRequest;
import com.gifiti.api.dto.response.WishlistListResponse;
import com.gifiti.api.dto.response.WishlistResponse;
import com.gifiti.api.exception.AccessDeniedException;
import com.gifiti.api.exception.ResourceNotFoundException;
import com.gifiti.api.mapper.WishlistMapper;
import com.gifiti.api.model.Wishlist;
import com.gifiti.api.model.WishlistItem;
import com.gifiti.api.model.enums.WishlistCategory;
import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import com.gifiti.api.repository.ReservationRepository;
import com.gifiti.api.repository.WishlistItemRepository;
import com.gifiti.api.repository.WishlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private final PostHogClient postHogClient;
    private final PostHogProperties postHogProperties;
    private final WishlistReturnedDedupeCache wishlistReturnedDedupeCache;

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

        // Feature 007 / T6: emit wishlist_created AFTER persist succeeds.
        // Properties limited to the §5.6 allowlist (user_id, occasion_type,
        // item_count_at_creation). occasion_type is the closed enum
        // WishlistCategory; safe per Security review. Fail-open per F-6.
        try {
            Map<String, Object> props = new HashMap<>();
            props.put("user_id", userId);
            WishlistCategory category = saved.getCategory();
            props.put("occasion_type", category != null ? category.name() : null);
            props.put("item_count_at_creation", 0);
            postHogClient.capture("wishlist_created", userId, props);
        } catch (RuntimeException analyticsFailure) {
            log.warn(
                    "Suppressed PostHog failure during wishlist_created emission: {}",
                    analyticsFailure.getMessage());
        }

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

    public WishlistListResponse findAllByOwner(String userId, int page, int size) {
        return findAllByOwner(userId, null, page, size);
    }

    public WishlistListResponse findAllByOwner(String userId, WishlistCategory category, int page, int size) {
        log.debug("Finding wishlists for user: {} (category={}, page={}, size={})", userId, category, page, size);

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Wishlist> wishlistPage = category != null
                ? wishlistRepository.findByOwnerUserIdAndCategory(userId, category, pageRequest)
                : wishlistRepository.findByOwnerUserId(userId, pageRequest);

        List<WishlistResponse> responses = wishlistPage.getContent().stream()
                .map(wishlist -> wishlistMapper.toResponse(wishlist, getItemCount(wishlist.getId())))
                .toList();

        return WishlistListResponse.builder()
                .wishlists(responses)
                .totalElements(wishlistPage.getTotalElements())
                .totalPages(wishlistPage.getTotalPages())
                .currentPage(wishlistPage.getNumber())
                .size(wishlistPage.getSize())
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

        // Feature 007 / T9 — wishlist_returned per OQ-1 (X): owner returns to
        // view their own wishlist >= N days after creation. Non-owner reads
        // exit upstream via AccessDeniedException, so reaching this point
        // already proves ownership. Per ADR 0007 § Finding 0004 ratification,
        // dedupe is owned by the backend (PostHog does not dedupe identical
        // events with different timestamps): see WishlistReturnedDedupeCache
        // for the (userId, wishlistId, UTC-day) key.
        emitWishlistReturnedIfThresholdMet(wishlist, userId);

        return wishlistMapper.toResponse(wishlist, getItemCount(id));
    }

    private void emitWishlistReturnedIfThresholdMet(Wishlist wishlist, String userId) {
        if (wishlist.getCreatedAt() == null) {
            return;
        }
        long daysSinceCreation = ChronoUnit.DAYS.between(wishlist.getCreatedAt(), Instant.now());
        int threshold = postHogProperties.returnThresholdDays();
        if (daysSinceCreation < threshold) {
            return;
        }

        // Per ADR 0007 § Finding 0004: reserve the (userId, wishlistId,
        // UTC-day) slot BEFORE invoking the wrapper. Dedupe consumes the
        // slot even if PostHog later throws — fail-open semantics still hold
        // (the user request never breaks), but we deliberately do NOT retry
        // emission on SDK failure because analytics are not financial events.
        if (!wishlistReturnedDedupeCache.tryReserve(userId, wishlist.getId())) {
            return;
        }

        try {
            Map<String, Object> props = new HashMap<>();
            props.put("user_id", userId);
            props.put("days_since_creation", daysSinceCreation);
            postHogClient.capture("wishlist_returned", userId, props);
        } catch (RuntimeException analyticsFailure) {
            log.warn(
                    "Suppressed PostHog failure during wishlist_returned emission: {}",
                    analyticsFailure.getMessage());
        }
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
     * Security hardening (H-01):
     * - Uses MongoDB transaction for atomic cascade deletion
     * - Prevents race condition where reservation could be created during deletion
     * - All operations commit/rollback together
     *
     * @param id Wishlist ID
     * @param userId Requesting user's ID
     * @throws ResourceNotFoundException if wishlist not found
     * @throws AccessDeniedException if user is not the owner
     */
    @Transactional
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
                .orElseThrow(() -> new ResourceNotFoundException(
                        ResourceNotFoundException.KEY_NOT_FOUND_WITH_FIELD,
                        "Wishlist", "id", id));

        if (!wishlist.getOwnerUserId().equals(userId)) {
            log.warn("SECURITY_EVENT: Access denied - user {} attempted to access wishlist {} owned by {}",
                     userId, id, wishlist.getOwnerUserId());
            throw new AccessDeniedException("error.access.denied", new Object[0]);
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
                .orElseThrow(() -> new ResourceNotFoundException(
                        ResourceNotFoundException.KEY_NOT_FOUND_WITH_FIELD,
                        "Wishlist", "shareableId", shareableId));
    }

    public WishlistResponse rotateShareableId(String id, String userId) {
        log.info("Rotating shareable ID for wishlist {} by user {}", id, userId);

        Wishlist wishlist = findAndVerifyOwnership(id, userId);
        wishlist.setShareableId(NanoIdUtils.randomNanoId());
        Wishlist saved = wishlistRepository.save(wishlist);

        log.info("Shareable ID rotated for wishlist {}", id);
        return wishlistMapper.toResponse(saved, getItemCount(id));
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
