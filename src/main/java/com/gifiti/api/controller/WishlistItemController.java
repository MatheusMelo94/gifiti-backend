package com.gifiti.api.controller;

import com.gifiti.api.dto.request.CreateItemRequest;
import com.gifiti.api.dto.request.UpdateItemRequest;
import com.gifiti.api.dto.response.ItemListResponse;
import com.gifiti.api.dto.response.ReservationResponse;
import com.gifiti.api.dto.response.WishlistItemResponse;
import com.gifiti.api.service.ReservationService;
import com.gifiti.api.service.UserService;
import com.gifiti.api.service.WishlistItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for wishlist item management.
 * Items are nested under wishlists: /wishlists/{wishlistId}/items
 * All endpoints require authentication.
 *
 * Security hardening (H-03):
 * - Method-level @PreAuthorize for defense-in-depth
 * - URL-level security in SecurityConfig provides first layer
 * - Service-layer ownership validation provides third layer
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/wishlists/{wishlistId}/items")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class WishlistItemController {

    private final WishlistItemService wishlistItemService;
    private final ReservationService reservationService;
    private final UserService userService;

    /**
     * List all items in a wishlist.
     *
     * GET /api/v1/wishlists/{wishlistId}/items
     *
     * @param wishlistId Wishlist ID
     * @param userDetails Authenticated user
     * @return List of items
     */
    @GetMapping
    public ResponseEntity<ItemListResponse> listItems(
            @PathVariable String wishlistId,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.debug("Listing items in wishlist {} for user: {}", wishlistId, userDetails.getUsername());
        ItemListResponse response = wishlistItemService.findAllByWishlistId(wishlistId, getUserId(userDetails));
        return ResponseEntity.ok(response);
    }

    /**
     * Create a new item in a wishlist.
     *
     * POST /api/v1/wishlists/{wishlistId}/items
     *
     * @param wishlistId Wishlist ID
     * @param request Item creation details
     * @param userDetails Authenticated user
     * @return Created item
     */
    @PostMapping
    public ResponseEntity<WishlistItemResponse> createItem(
            @PathVariable String wishlistId,
            @Valid @RequestBody CreateItemRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.debug("Creating item in wishlist {} for user: {}", wishlistId, userDetails.getUsername());
        WishlistItemResponse response = wishlistItemService.create(wishlistId, request, getUserId(userDetails));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get a specific item by ID.
     *
     * GET /api/v1/wishlists/{wishlistId}/items/{itemId}
     *
     * @param wishlistId Wishlist ID
     * @param itemId Item ID
     * @param userDetails Authenticated user
     * @return Item details
     */
    @GetMapping("/{itemId}")
    public ResponseEntity<WishlistItemResponse> getItem(
            @PathVariable String wishlistId,
            @PathVariable String itemId,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.debug("Getting item {} in wishlist {} for user: {}", itemId, wishlistId, userDetails.getUsername());
        WishlistItemResponse response = wishlistItemService.findById(wishlistId, itemId, getUserId(userDetails));
        return ResponseEntity.ok(response);
    }

    /**
     * Update an item.
     *
     * PUT /api/v1/wishlists/{wishlistId}/items/{itemId}
     *
     * @param wishlistId Wishlist ID
     * @param itemId Item ID
     * @param request Update details
     * @param userDetails Authenticated user
     * @return Updated item
     */
    @PutMapping("/{itemId}")
    public ResponseEntity<WishlistItemResponse> updateItem(
            @PathVariable String wishlistId,
            @PathVariable String itemId,
            @Valid @RequestBody UpdateItemRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.debug("Updating item {} in wishlist {} for user: {}", itemId, wishlistId, userDetails.getUsername());
        WishlistItemResponse response = wishlistItemService.update(wishlistId, itemId, request, getUserId(userDetails));
        return ResponseEntity.ok(response);
    }

    /**
     * Delete an item.
     *
     * DELETE /api/v1/wishlists/{wishlistId}/items/{itemId}
     *
     * @param wishlistId Wishlist ID
     * @param itemId Item ID
     * @param userDetails Authenticated user
     * @return No content
     */
    @DeleteMapping("/{itemId}")
    public ResponseEntity<Void> deleteItem(
            @PathVariable String wishlistId,
            @PathVariable String itemId,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.debug("Deleting item {} in wishlist {} for user: {}", itemId, wishlistId, userDetails.getUsername());
        wishlistItemService.delete(wishlistId, itemId, getUserId(userDetails));
        return ResponseEntity.noContent().build();
    }

    /**
     * Cancel a reservation on an item.
     * Only the wishlist owner can unreserve items.
     *
     * DELETE /api/v1/wishlists/{wishlistId}/items/{itemId}/reservation
     *
     * @param wishlistId Wishlist ID
     * @param itemId Item ID
     * @param userDetails Authenticated user
     * @return Unreservation confirmation
     */
    @DeleteMapping("/{itemId}/reservation")
    public ResponseEntity<ReservationResponse> unreserveItem(
            @PathVariable String wishlistId,
            @PathVariable String itemId,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.debug("Unreserving item {} in wishlist {} for user: {}", itemId, wishlistId, userDetails.getUsername());

        // Verify ownership (will throw AccessDeniedException if not owner)
        wishlistItemService.findById(wishlistId, itemId, getUserId(userDetails));

        ReservationResponse response = reservationService.unreserve(itemId);
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
