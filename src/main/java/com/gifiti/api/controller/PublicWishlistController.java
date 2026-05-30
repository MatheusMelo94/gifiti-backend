package com.gifiti.api.controller;

import com.gifiti.api.dto.response.ErrorResponse;
import com.gifiti.api.dto.response.PublicWishlistResponse;
import com.gifiti.api.dto.response.ReservationResponse;
import com.gifiti.api.service.PublicWishlistService;
import com.gifiti.api.service.ReservationService;
import com.gifiti.api.service.UserService;
import com.gifiti.api.util.ClientIpResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * REST controller for shared wishlist access.
 * Endpoints for viewing shared wishlists (anonymous) and reserving items (authenticated).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/public/wishlists")
@RequiredArgsConstructor
@Tag(name = "Public Wishlists", description = "View shared wishlists and reserve items")
public class PublicWishlistController {

    /**
     * HTTP header carrying the 4-digit access code for PRIVATE wishlists
     * (feature 008 / T6 + T8). Optional on the controller — missing-header
     * on PUBLIC is fine; missing-header on PRIVATE surfaces as
     * {@code AccessCodeRequiredException} in the service layer.
     */
    private static final String ACCESS_CODE_HEADER = "X-Wishlist-Access-Code";

    private final PublicWishlistService publicWishlistService;
    private final ReservationService reservationService;
    private final UserService userService;

    /**
     * View a shared wishlist by its shareable ID.
     *
     * <p>Anonymous read access — anyone holding the shareable link can view a
     * PUBLIC wishlist. PRIVATE wishlists require a matching
     * {@code X-Wishlist-Access-Code} header (feature 008 / T6); see
     * {@link PublicWishlistService#findByShareableId} for the gate semantics
     * (200 / 403 ACCESS_CODE_REQUIRED / 403 INVALID_ACCESS_CODE / 429).
     */
    @Operation(
            summary = "View a shared wishlist by shareable ID",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Wishlist found"),
                    @ApiResponse(responseCode = "403", description = "Access code required or invalid (PRIVATE)",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Wishlist not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "429", description = "Access-code rate limit exhausted",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    @GetMapping("/{shareableId}")
    public ResponseEntity<PublicWishlistResponse> getPublicWishlist(
            @PathVariable String shareableId,
            @RequestHeader(value = ACCESS_CODE_HEADER, required = false) String accessCode,
            HttpServletRequest request) {
        log.debug("Shared wishlist request for: {}", shareableId);
        String clientIp = ClientIpResolver.resolveClientIp(request);
        PublicWishlistResponse response = publicWishlistService.findByShareableId(
                shareableId, Optional.ofNullable(accessCode), clientIp);
        return ResponseEntity.ok(response);
    }

    /**
     * Reserve an item on a public wishlist (feature 008 / T8: now gated by
     * the access-code header for PRIVATE wishlists).
     *
     * <p>Per Security findings F-4 pin 4 (highest-impact test): the reserve
     * endpoint MUST enforce the gate too — an authenticated user who reads
     * a leaked PRIVATE shareableId must not be able to reserve items without
     * solving the gate.
     */
    @Operation(
            summary = "Reserve an item on a public wishlist",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Item reserved"),
                    @ApiResponse(responseCode = "401", description = "Authentication required"),
                    @ApiResponse(responseCode = "403", description = "Access code required or invalid (PRIVATE)",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Wishlist or item not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "409", description = "Item already reserved",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "429", description = "Access-code rate limit exhausted",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    @PostMapping("/{shareableId}/items/{itemId}/reserve")
    public ResponseEntity<ReservationResponse> reserveItem(
            @PathVariable String shareableId,
            @PathVariable String itemId,
            @RequestHeader(value = ACCESS_CODE_HEADER, required = false) String accessCode,
            HttpServletRequest request,
            Authentication authentication) {
        log.debug("Reserve request for item {} in wishlist {} by user {}", itemId, shareableId, authentication.getName());

        userService.requireEmailVerified(authentication.getName());

        // Verify wishlist exists, is reachable, and (if PRIVATE) the access
        // code passes the gate. This reuses the SAME service-layer gate that
        // the GET viewer uses — feature 008 / T8 highest-impact test (Security
        // findings F-4 pin 4).
        String clientIp = ClientIpResolver.resolveClientIp(request);
        publicWishlistService.findByShareableId(
                shareableId, Optional.ofNullable(accessCode), clientIp);

        // Resolve user ID from authenticated user
        String userId = userService.getUserIdByEmail(authentication.getName());

        ReservationResponse response = reservationService.reserve(itemId, userId);
        return ResponseEntity.ok(response);
    }
}
