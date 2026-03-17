package com.gifiti.api.controller;

import com.gifiti.api.dto.request.CreateWishlistRequest;
import com.gifiti.api.dto.request.UpdateWishlistRequest;
import com.gifiti.api.dto.response.ErrorResponse;
import com.gifiti.api.dto.response.MessageResponse;
import com.gifiti.api.dto.response.SharedWishlistListResponse;
import com.gifiti.api.dto.response.WishlistListResponse;
import com.gifiti.api.dto.response.WishlistResponse;
import com.gifiti.api.model.enums.WishlistCategory;
import com.gifiti.api.service.GifterService;
import com.gifiti.api.service.UserService;
import com.gifiti.api.service.WishlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for wishlist management.
 * All endpoints require authentication.
 *
 * Security hardening (H-03):
 * - Method-level @PreAuthorize for defense-in-depth
 * - URL-level security in SecurityConfig provides first layer
 * - Service-layer ownership validation provides third layer
 *
 * Security hardening (L-01):
 * - Explicit produces for Content-Type validation
 * - Returns application/json responses
 */
@Slf4j
@RestController
@RequestMapping(path = "/api/v1/wishlists", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Validated
@Tag(name = "Wishlists", description = "CRUD operations for wishlists (authenticated)")
public class WishlistController {

    private final WishlistService wishlistService;
    private final UserService userService;
    private final GifterService gifterService;

    /**
     * List all wishlists owned by the authenticated user.
     *
     * GET /api/v1/wishlists
     *
     * @param userDetails Authenticated user
     * @return List of wishlists
     */
    @Operation(summary = "List all wishlists for the authenticated user")
    @GetMapping
    public ResponseEntity<WishlistListResponse> listWishlists(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) WishlistCategory category) {
        log.debug("Listing wishlists for user: {} (category={}, page={}, size={})", userDetails.getUsername(), category, page, size);
        WishlistListResponse response = wishlistService.findAllByOwner(getUserId(userDetails), category, page, size);
        return ResponseEntity.ok(response);
    }

    /**
     * Create a new wishlist.
     *
     * POST /api/v1/wishlists
     *
     * @param request Wishlist creation details
     * @param userDetails Authenticated user
     * @return Created wishlist
     */
    @Operation(
            summary = "Create a new wishlist",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Wishlist created"),
                    @ApiResponse(responseCode = "400", description = "Validation error",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WishlistResponse> createWishlist(
            @Valid @RequestBody CreateWishlistRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.debug("Creating wishlist for user: {}", userDetails.getUsername());
        userService.requireEmailVerified(userDetails.getUsername());
        WishlistResponse response = wishlistService.create(request, getUserId(userDetails));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get a specific wishlist by ID.
     *
     * GET /api/v1/wishlists/{id}
     *
     * @param id Wishlist ID
     * @param userDetails Authenticated user
     * @return Wishlist details
     */
    @Operation(
            summary = "Get a wishlist by ID",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Wishlist found"),
                    @ApiResponse(responseCode = "404", description = "Wishlist not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    @GetMapping("/{id}")
    public ResponseEntity<WishlistResponse> getWishlist(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.debug("Getting wishlist {} for user: {}", id, userDetails.getUsername());
        WishlistResponse response = wishlistService.findById(id, getUserId(userDetails));
        return ResponseEntity.ok(response);
    }

    /**
     * Update a wishlist.
     *
     * PUT /api/v1/wishlists/{id}
     *
     * @param id Wishlist ID
     * @param request Update details
     * @param userDetails Authenticated user
     * @return Updated wishlist
     */
    @Operation(
            summary = "Update a wishlist",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Wishlist updated"),
                    @ApiResponse(responseCode = "404", description = "Wishlist not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WishlistResponse> updateWishlist(
            @PathVariable String id,
            @Valid @RequestBody UpdateWishlistRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.debug("Updating wishlist {} for user: {}", id, userDetails.getUsername());
        WishlistResponse response = wishlistService.update(id, request, getUserId(userDetails));
        return ResponseEntity.ok(response);
    }

    /**
     * Delete a wishlist and all its items.
     *
     * DELETE /api/v1/wishlists/{id}
     *
     * @param id Wishlist ID
     * @param userDetails Authenticated user
     * @return No content
     */
    @Operation(
            summary = "Delete a wishlist and all its items",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Wishlist deleted"),
                    @ApiResponse(responseCode = "404", description = "Wishlist not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWishlist(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.debug("Deleting wishlist {} for user: {}", id, userDetails.getUsername());
        wishlistService.delete(id, getUserId(userDetails));
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "List wishlists where I have reservations",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Shared wishlists found")
            })
    @GetMapping("/shared-with-me")
    public ResponseEntity<SharedWishlistListResponse> getSharedWithMe(
            @AuthenticationPrincipal UserDetails userDetails) {
        log.debug("Listing shared wishlists for user: {}", userDetails.getUsername());
        SharedWishlistListResponse response = gifterService.listSharedWishlists(getUserId(userDetails));
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Save a shared wishlist to your list",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Wishlist saved"),
                    @ApiResponse(responseCode = "403", description = "Cannot save your own wishlist"),
                    @ApiResponse(responseCode = "404", description = "Wishlist not found or not public",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    @PostMapping("/shared-with-me/{shareableId}")
    public ResponseEntity<MessageResponse> saveSharedWishlist(
            @PathVariable String shareableId,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.debug("User {} saving shared wishlist {}", userDetails.getUsername(), shareableId);
        MessageResponse response = gifterService.saveSharedWishlist(shareableId, getUserId(userDetails));
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Leave a shared wishlist (cancels all your reservations on it)",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Left wishlist successfully"),
                    @ApiResponse(responseCode = "404", description = "No reservations found on this wishlist",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    @DeleteMapping("/shared-with-me/{shareableId}")
    public ResponseEntity<Void> leaveSharedWishlist(
            @PathVariable String shareableId,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.debug("User {} leaving shared wishlist {}", userDetails.getUsername(), shareableId);
        gifterService.leaveSharedWishlist(shareableId, getUserId(userDetails));
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Rotate the shareable link (invalidates old link)",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Link rotated"),
                    @ApiResponse(responseCode = "404", description = "Wishlist not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    @PostMapping("/{id}/rotate-link")
    public ResponseEntity<WishlistResponse> rotateShareableLink(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.debug("Rotating shareable link for wishlist {} for user: {}", id, userDetails.getUsername());
        WishlistResponse response = wishlistService.rotateShareableId(id, getUserId(userDetails));
        return ResponseEntity.ok(response);
    }

    /**
     * Extract user ID from UserDetails.
     * Looks up the actual MongoDB user ID from the email (which is the JWT subject).
     */
    private String getUserId(UserDetails userDetails) {
        return userService.findByEmail(userDetails.getUsername()).getId();
    }
}
