package com.gifiti.api.service;

import com.gifiti.api.analytics.PostHogClient;
import com.gifiti.api.analytics.PostHogProperties;
import com.gifiti.api.analytics.WishlistReturnedDedupeCache;
import com.gifiti.api.dto.request.CreateWishlistRequest;
import com.gifiti.api.dto.request.UpdateWishlistRequest;
import com.gifiti.api.dto.response.WishlistListResponse;
import com.gifiti.api.dto.response.WishlistResponse;
import com.gifiti.api.exception.AccessDeniedException;
import com.gifiti.api.exception.PublicWishlistHasNoAccessCodeException;
import com.gifiti.api.exception.ResourceNotFoundException;
import com.gifiti.api.mapper.WishlistMapper;
import com.gifiti.api.model.Wishlist;
import com.gifiti.api.model.WishlistItem;
import com.gifiti.api.model.enums.Visibility;
import com.gifiti.api.model.enums.WishlistCategory;
import com.gifiti.api.util.AccessCodeGenerator;
import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import com.gifiti.api.repository.ReservationRepository;
import com.gifiti.api.repository.WishlistItemRepository;
import com.gifiti.api.repository.WishlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
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

        // Feature 008 / T3: generate the access code for PRIVATE wishlists at
        // creation time. Per ADR 0008 § Decision F (4-digit numeric, leading
        // zeros allowed, SecureRandom-backed). PUBLIC wishlists leave the
        // field null — § Decision E + § Decision F constrain access codes to
        // PRIVATE-visibility wishlists only.
        if (wishlist.getVisibility() == Visibility.PRIVATE) {
            wishlist.setAccessCode(AccessCodeGenerator.generate());
        }

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

        // Feature 008 / T4: capture visibility BEFORE the mapper mutates the
        // entity so the transition can be detected after the mapper runs.
        Visibility previousVisibility = wishlist.getVisibility();

        wishlistMapper.updateEntity(wishlist, request);

        // Apply access-code lifecycle per ADR 0008 § Decision F (transitions):
        //   PUBLIC → PRIVATE  → generate fresh code
        //   PRIVATE → PUBLIC  → clear code (set null)
        //   PRIVATE → PRIVATE → no-op (preserve existing code)
        //   PUBLIC  → PUBLIC  → no-op
        applyAccessCodeTransition(wishlist, previousVisibility);

        Wishlist saved = wishlistRepository.save(wishlist);

        log.info("Wishlist {} updated successfully", id);
        return wishlistMapper.toResponse(saved, getItemCount(id));
    }

    /**
     * Apply the access-code transition rule per ADR 0008 § Decision F.
     *
     * <p>Centralizes the four-case transition matrix so it can be reviewed in
     * one place. The previous visibility is captured before the mapper runs
     * (see {@link #update}); the current visibility is read from the entity
     * post-mapping.
     *
     * <p>Per Security findings F-4 (mass-assignment): {@code accessCode} is
     * NEVER read from request input. Rotation has its own dedicated endpoint
     * (T11).
     */
    private void applyAccessCodeTransition(Wishlist wishlist, Visibility previousVisibility) {
        Visibility currentVisibility = wishlist.getVisibility();
        if (currentVisibility == Visibility.PRIVATE && previousVisibility != Visibility.PRIVATE) {
            // PUBLIC → PRIVATE: generate a fresh code.
            wishlist.setAccessCode(AccessCodeGenerator.generate());
        } else if (currentVisibility == Visibility.PUBLIC && previousVisibility == Visibility.PRIVATE) {
            // PRIVATE → PUBLIC: clear the code.
            wishlist.setAccessCode(null);
        }
        // PRIVATE → PRIVATE or PUBLIC → PUBLIC: leave accessCode untouched.
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

    /**
     * Rotate the access code on a PRIVATE wishlist (feature 008 / T11).
     *
     * <p>Per ADR 0008 § Decision A (never expire — owner controls rotation
     * manually) and § Decision F (4-digit SecureRandom code generation).
     *
     * <p>Authorization model — per Security findings reconciliation of the
     * plan §4.3 vs §T13 contradiction (user ratification 2026-05-30):
     * <ul>
     *   <li>Wishlist not found → 404.</li>
     *   <li>Wishlist not owned by {@code userId} → 404 (NOT 403). This
     *       preserves IDOR-resistance: a non-owner cannot distinguish
     *       "exists but you can't touch it" from "doesn't exist". Matches
     *       the {@code rotateShareableId} precedent's spirit.</li>
     *   <li>Wishlist visibility != PRIVATE →
     *       {@link PublicWishlistHasNoAccessCodeException} → 400.</li>
     * </ul>
     *
     * <p>Per Security findings F-5: emit a {@code SECURITY_EVENT: access code
     * rotated} INFO log on success carrying {@code wishlistId},
     * {@code userId}, and {@code correlationId} — NEVER the code value
     * (old or new).
     *
     * @param wishlistId the wishlist's MongoDB id
     * @param userId     the authenticated user's id
     * @return the updated wishlist response carrying the freshly-generated
     *         {@code accessCode}
     * @throws ResourceNotFoundException             when the wishlist does
     *         not exist OR is owned by another user (IDOR-resistance)
     * @throws PublicWishlistHasNoAccessCodeException when the wishlist is
     *         PUBLIC
     */
    public WishlistResponse rotateAccessCode(String wishlistId, String userId) {
        log.info("Rotating access code for wishlist {} by user {}", wishlistId, userId);

        // IDOR-resistance: collapse not-found and not-owner to the SAME 404
        // path. Cannot use findAndVerifyOwnership here — that throws 403
        // (AccessDeniedException) on not-owner, which leaks existence.
        Wishlist wishlist = wishlistRepository.findById(wishlistId)
                .filter(w -> userId.equals(w.getOwnerUserId()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        ResourceNotFoundException.KEY_NOT_FOUND_WITH_FIELD,
                        "Wishlist", "id", wishlistId));

        if (wishlist.getVisibility() != Visibility.PRIVATE) {
            // PUBLIC wishlists have no access code (ADR 0008 § Decision E + F).
            throw new PublicWishlistHasNoAccessCodeException();
        }

        // Per ADR 0008 § Decision F: SecureRandom-backed 4-digit code.
        wishlist.setAccessCode(AccessCodeGenerator.generate());
        Wishlist saved = wishlistRepository.save(wishlist);

        // Per Security findings F-5: log the EVENT — never the code values.
        log.info("SECURITY_EVENT: access code rotated wishlistId={} userId={} correlationId={}",
                wishlistId, userId, MDC.get("correlationId"));

        return wishlistMapper.toResponse(saved, getItemCount(wishlistId));
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
