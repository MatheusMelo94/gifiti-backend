package com.gifiti.api.controller;

import com.gifiti.api.dto.request.CreateWishlistRequest;
import com.gifiti.api.dto.request.UpdateWishlistRequest;
import com.gifiti.api.dto.response.WishlistListResponse;
import com.gifiti.api.dto.response.WishlistResponse;
import com.gifiti.api.service.UserService;
import com.gifiti.api.service.WishlistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
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
public class WishlistController {

    private final WishlistService wishlistService;
    private final UserService userService;

    /**
     * List all wishlists owned by the authenticated user.
     *
     * GET /api/v1/wishlists
     *
     * @param userDetails Authenticated user
     * @return List of wishlists
     */
    @GetMapping
    public ResponseEntity<WishlistListResponse> listWishlists(
            @AuthenticationPrincipal UserDetails userDetails) {
        log.debug("Listing wishlists for user: {}", userDetails.getUsername());
        WishlistListResponse response = wishlistService.findAllByOwner(getUserId(userDetails));
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
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WishlistResponse> createWishlist(
            @Valid @RequestBody CreateWishlistRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.debug("Creating wishlist for user: {}", userDetails.getUsername());
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
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWishlist(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.debug("Deleting wishlist {} for user: {}", id, userDetails.getUsername());
        wishlistService.delete(id, getUserId(userDetails));
        return ResponseEntity.noContent().build();
    }

    /**
     * Extract user ID from UserDetails.
     * Looks up the actual MongoDB user ID from the email (which is the JWT subject).
     */
    private String getUserId(UserDetails userDetails) {
        return userService.findByEmail(userDetails.getUsername()).getId();
    }
}
