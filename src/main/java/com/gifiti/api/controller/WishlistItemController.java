package com.gifiti.api.controller;

import com.gifiti.api.dto.request.CreateItemRequest;
import com.gifiti.api.dto.request.UpdateItemRequest;
import com.gifiti.api.dto.response.ErrorResponse;
import com.gifiti.api.dto.response.ItemListResponse;
import com.gifiti.api.dto.response.ReservationResponse;
import com.gifiti.api.dto.response.WishlistItemResponse;
import com.gifiti.api.service.ReservationService;
import com.gifiti.api.service.UserService;
import com.gifiti.api.service.WishlistItemService;
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
 * REST controller for wishlist item management.
 * Items are nested under wishlists: /wishlists/{wishlistId}/items
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
@RequestMapping(path = "/api/v1/wishlists/{wishlistId}/items", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Validated
@Tag(name = "Wishlist Items", description = "Manage items within a wishlist (authenticated)")
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
    @Operation(summary = "List all items in a wishlist")
    @GetMapping
    public ResponseEntity<ItemListResponse> listItems(
            @PathVariable String wishlistId,
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        log.debug("Listing items in wishlist {} for user: {} (page={}, size={})", wishlistId, userDetails.getUsername(), page, size);
        ItemListResponse response = wishlistItemService.findAllByWishlistId(wishlistId, getUserId(userDetails), page, size);
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
    @Operation(
            summary = "Add an item to a wishlist",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Item created"),
                    @ApiResponse(responseCode = "400", description = "Validation error",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "404", description = "Wishlist not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
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
    @Operation(
            summary = "Get an item by ID",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Item found"),
                    @ApiResponse(responseCode = "404", description = "Item or wishlist not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
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
    @Operation(
            summary = "Update an item",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Item updated"),
                    @ApiResponse(responseCode = "404", description = "Item or wishlist not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    @PutMapping(path = "/{itemId}", consumes = MediaType.APPLICATION_JSON_VALUE)
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
    @Operation(
            summary = "Delete an item",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Item deleted"),
                    @ApiResponse(responseCode = "404", description = "Item or wishlist not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
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
    @Operation(
            summary = "Cancel a reservation on an item",
            description = "Only the wishlist owner can unreserve items",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Reservation cancelled"),
                    @ApiResponse(responseCode = "404", description = "Item not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
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
