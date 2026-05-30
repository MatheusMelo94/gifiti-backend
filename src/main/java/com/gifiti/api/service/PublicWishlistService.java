package com.gifiti.api.service;

import com.gifiti.api.config.RateLimitConfig;
import com.gifiti.api.dto.response.PublicItemResponse;
import com.gifiti.api.dto.response.PublicWishlistResponse;
import com.gifiti.api.exception.AccessCodeRateLimitedException;
import com.gifiti.api.exception.AccessCodeRequiredException;
import com.gifiti.api.exception.InvalidAccessCodeException;
import com.gifiti.api.exception.ResourceNotFoundException;
import com.gifiti.api.model.User;
import com.gifiti.api.model.Wishlist;
import com.gifiti.api.model.WishlistItem;
import com.gifiti.api.model.enums.Visibility;
import com.gifiti.api.repository.UserRepository;
import com.gifiti.api.repository.WishlistItemRepository;
import com.gifiti.api.repository.WishlistRepository;
import com.gifiti.api.util.ClientIpResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Service for public (unauthenticated) wishlist access.
 * Enforces visibility checks and provides read-only access to public wishlists.
 *
 * <p>Feature 008 / T6 + T10: gates {@code GET /api/v1/public/wishlists/{id}}
 * (and the reserve endpoint via {@link #findByShareableId(String, Optional, String)})
 * behind a 4-digit {@code X-Wishlist-Access-Code} header for PRIVATE wishlists.
 * See {@link #findByShareableId} for the gate logic + comparison + rate-limit
 * interaction. See ADR 0008 § 3 + § Decision G + § Decision H, and Security
 * findings F-1 / F-2 / F-3 / F-4 for the design rationale and implementation
 * pins this code implements.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublicWishlistService {

    private final WishlistRepository wishlistRepository;
    private final WishlistItemRepository wishlistItemRepository;
    private final UserRepository userRepository;
    private final MessageSource messageSource;
    private final RateLimitConfig rateLimitConfig;

    /**
     * Bundle key for the localized owner displayName fallback shown to
     * anonymous viewers when the owner has not set a displayName.
     * Replaces the pre-006 email-prefix fallback (Security finding F-2).
     */
    private static final String OWNER_FALLBACK_KEY = "wishlist.owner.anonymous.fallback";

    /**
     * Per Security findings F-3 pin 2: pre-validate the access-code format with
     * {@code ^\d{4}$} BEFORE entering the constant-time compare — otherwise
     * {@link MessageDigest#isEqual(byte[], byte[])} would return early on
     * length mismatch and leak length information through timing.
     */
    private static final Pattern ACCESS_CODE_PATTERN = Pattern.compile("^\\d{4}$");

    /**
     * Get a public wishlist by its shareable ID, gated by access code for
     * PRIVATE visibility (feature 008 / T6 + T10).
     *
     * <p>Behavior matrix:
     * <ul>
     *   <li>Wishlist not found → 404 (unchanged).</li>
     *   <li>Wishlist PUBLIC → 200 with body. {@code accessCode} parameter is
     *       ignored (per ADR 0008 § 3 — PUBLIC wishlists need no gate).</li>
     *   <li>Wishlist PRIVATE, no header → 403 {@code ACCESS_CODE_REQUIRED}
     *       (privacy-posture inversion vs. feature 006's 404; Security F-1
     *       concurs).</li>
     *   <li>Wishlist PRIVATE, header matches → 200 with body, rate-limit
     *       bucket reset (ADR 0008 § Decision G "reset on success").</li>
     *   <li>Wishlist PRIVATE, header does NOT match (incl. malformed format
     *       per Security F-3 pin 3) → consume rate-limit token. If consumption
     *       succeeds → 403 {@code INVALID_ACCESS_CODE}. If the bucket is
     *       already exhausted → 429 {@code ACCESS_CODE_RATE_LIMITED}.</li>
     * </ul>
     *
     * <p>Per Security findings F-3 pin 1: comparison is byte-array
     * {@link MessageDigest#isEqual} after the format pre-check, NOT
     * {@link String#equals}. Per F-3 pin 4 and F-5: NEVER log the submitted
     * value or the stored code; the {@code access_code_*} log lines carry
     * only correlationId, shareableId, and masked IP.
     *
     * @param shareableId  the wishlist's shareable NanoID
     * @param accessCode   the {@code X-Wishlist-Access-Code} header value;
     *                     {@link Optional#empty()} when absent
     * @param clientIp     the request's client IP, resolved by
     *                     {@link ClientIpResolver#resolveClientIp} — must use
     *                     the same trusted-proxy logic as the rate-limit
     *                     filter per Security findings F-2 pin 4
     * @return the public wishlist DTO
     */
    public PublicWishlistResponse findByShareableId(
            String shareableId,
            Optional<String> accessCode,
            String clientIp) {
        log.debug("Public access request for wishlist: {}", shareableId);

        Wishlist wishlist = wishlistRepository.findByShareableId(shareableId)
                .orElseThrow(() -> {
                    log.debug("Wishlist not found: {}", shareableId);
                    return new ResourceNotFoundException(
                            ResourceNotFoundException.KEY_NOT_FOUND_WITH_FIELD,
                            "Wishlist", "shareableId", shareableId);
                });

        if (wishlist.getVisibility() == Visibility.PUBLIC) {
            return toPublicWishlistResponse(wishlist);
        }

        // PRIVATE — apply the access-code gate.
        enforceAccessCodeGate(wishlist, accessCode, clientIp);
        return toPublicWishlistResponse(wishlist);
    }

    /**
     * Enforce the PRIVATE-wishlist access-code gate (feature 008 / T6 + T10).
     *
     * <p>Encapsulates the no-header / wrong-header / rate-limited / success
     * branches so the gate is reusable from both the GET viewer path
     * ({@code PublicWishlistController.getPublicWishlist}) and the reserve
     * path ({@code PublicWishlistController.reserveItem}, T8). Throws on
     * failure; returns normally on success after resetting the bucket.
     */
    private void enforceAccessCodeGate(
            Wishlist wishlist,
            Optional<String> accessCode,
            String clientIp) {
        String shareableId = wishlist.getShareableId();

        if (accessCode.isEmpty()) {
            // Per ADR 0008 § 3: 403 ACCESS_CODE_REQUIRED on missing header.
            // Per Security findings F-3 pin 3 + ADR 0008 § Decision G: we do
            // NOT consume a rate-limit token here — the missing-header case
            // is "I didn't even guess", not a failed guess. The bucket protects
            // against guessing, not header-omission probes.
            log.info("access_code_required shareableId={} clientIp={} correlationId={}",
                    shareableId, ClientIpResolver.maskIp(clientIp), MDC.get("correlationId"));
            throw new AccessCodeRequiredException();
        }

        if (!isCodeValid(wishlist.getAccessCode(), accessCode.get())) {
            // Failed validation — try to consume a rate-limit token first.
            // If the bucket is already exhausted (returns false), surface
            // 429 ACCESS_CODE_RATE_LIMITED. Otherwise surface 403
            // INVALID_ACCESS_CODE. Per Security F-3 pin 3: malformed-format
            // headers ALSO route here and consume a token — they are
            // failed-validation attempts from the rate-limit's perspective.
            boolean tokenConsumed = rateLimitConfig.tryConsumeAccessCode(clientIp, shareableId);
            if (!tokenConsumed) {
                log.warn("SECURITY_EVENT: access code rate limit exhausted shareableId={} clientIp={} correlationId={}",
                        shareableId, ClientIpResolver.maskIp(clientIp), MDC.get("correlationId"));
                throw new AccessCodeRateLimitedException();
            }
            log.info("access_code_failed shareableId={} clientIp={} correlationId={}",
                    shareableId, ClientIpResolver.maskIp(clientIp), MDC.get("correlationId"));
            // Per Security findings F-4 pin 5 + F-5: NEVER log the submitted
            // value or the stored code.
            throw new InvalidAccessCodeException();
        }

        // SUCCESS — reset the bucket per ADR 0008 § Decision G.
        rateLimitConfig.resetAccessCodeBucket(clientIp, shareableId);
        log.info("access_code_success shareableId={} clientIp={} correlationId={}",
                shareableId, ClientIpResolver.maskIp(clientIp), MDC.get("correlationId"));
    }

    /**
     * Constant-time access-code comparison per ADR 0008 § Decision H and
     * Security findings F-3 (feature 008 / T10).
     *
     * <p>Implementation pins per Security findings F-3:
     * <ol>
     *   <li>Compare byte arrays, not Strings ({@link MessageDigest#isEqual}
     *       is the constant-time primitive in standard Java; do not roll your
     *       own).</li>
     *   <li>Pre-validate format with {@code ^\d{4}$} BEFORE the compare so
     *       the byte arrays always have the same length (length-mismatch
     *       short-circuit in {@code MessageDigest.isEqual} would leak
     *       length).</li>
     *   <li>Malformed format counts as a failed validation (consumes a token
     *       upstream) — same outcome as wrong-but-conforming code.</li>
     *   <li>Never log the submitted value (enforced at all call sites).</li>
     *   <li>{@code MessageDigest.isEqual} is the canonical primitive; do not
     *       substitute {@code Arrays.equals}.</li>
     * </ol>
     *
     * @return {@code true} iff {@code submittedCode} is a 4-digit string equal
     *         to {@code storedCode} byte-for-byte
     */
    private boolean isCodeValid(String storedCode, String submittedCode) {
        if (storedCode == null || submittedCode == null) {
            return false;
        }
        // Pin 2: pre-validate format so the constant-time compare always
        // operates on same-length inputs.
        if (!ACCESS_CODE_PATTERN.matcher(submittedCode).matches()) {
            return false;
        }
        if (storedCode.length() != submittedCode.length()) {
            return false;
        }
        // Pin 1 + 5: byte-array MessageDigest.isEqual is the canonical
        // constant-time primitive.
        return MessageDigest.isEqual(
                storedCode.getBytes(StandardCharsets.UTF_8),
                submittedCode.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Build the public response DTO from a wishlist entity. Extracted so the
     * gate logic can short-circuit into success / failure paths without
     * touching the projection.
     *
     * <p>Security finding F-4 sanity check: the projection NEVER includes
     * {@code accessCode}. PRIVATE wishlists, once gated through, expose the
     * same shape as PUBLIC ones.
     */
    private PublicWishlistResponse toPublicWishlistResponse(Wishlist wishlist) {
        String ownerDisplayName = resolveOwnerDisplayName(wishlist.getOwnerUserId());
        List<WishlistItem> items = wishlistItemRepository.findByWishlistId(wishlist.getId());
        List<PublicItemResponse> publicItems = items.stream()
                .map(this::toPublicItemResponse)
                .toList();

        log.info("Public wishlist accessed: {} with {} items",
                wishlist.getShareableId(), publicItems.size());

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
