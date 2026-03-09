package com.gifiti.api.controller;

import com.gifiti.api.dto.response.ErrorResponse;
import com.gifiti.api.dto.response.PublicWishlistResponse;
import com.gifiti.api.dto.response.ReservationResponse;
import com.gifiti.api.model.User;
import com.gifiti.api.repository.UserRepository;
import com.gifiti.api.service.PublicWishlistService;
import com.gifiti.api.service.ReservationService;
import com.gifiti.api.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for shared wishlist access.
 * All endpoints require authentication — users must be logged in AND have the shareable link.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/public/wishlists")
@RequiredArgsConstructor
@Tag(name = "Public Wishlists", description = "View shared wishlists and reserve items")
public class PublicWishlistController {

    private final PublicWishlistService publicWishlistService;
    private final ReservationService reservationService;
    private final UserRepository userRepository;
    private final UserService userService;

    /**
     * View a shared wishlist by its shareable ID.
     * Authentication required — only logged-in users with the link can view.
     */
    @Operation(
            summary = "View a shared wishlist by shareable ID",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Wishlist found"),
                    @ApiResponse(responseCode = "401", description = "Authentication required"),
                    @ApiResponse(responseCode = "404", description = "Wishlist not found or not public",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    @GetMapping("/{shareableId}")
    public ResponseEntity<PublicWishlistResponse> getPublicWishlist(
            @PathVariable String shareableId,
            Authentication authentication) {
        log.debug("Shared wishlist request for: {} by user: {}", shareableId, authentication.getName());
        PublicWishlistResponse response = publicWishlistService.findByShareableId(shareableId);
        return ResponseEntity.ok(response);
    }

    /**
     * Reserve an item on a public wishlist.
     * Authentication required — user must be logged in to reserve.
     */
    @Operation(
            summary = "Reserve an item on a public wishlist",
            security = @SecurityRequirement(name = "bearerAuth"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Item reserved"),
                    @ApiResponse(responseCode = "401", description = "Authentication required"),
                    @ApiResponse(responseCode = "404", description = "Wishlist or item not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "409", description = "Item already reserved",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    @PostMapping("/{shareableId}/items/{itemId}/reserve")
    public ResponseEntity<ReservationResponse> reserveItem(
            @PathVariable String shareableId,
            @PathVariable String itemId,
            Authentication authentication) {
        log.debug("Reserve request for item {} in wishlist {} by user {}", itemId, shareableId, authentication.getName());

        userService.requireEmailVerified(authentication.getName());

        // Verify wishlist exists and is public
        publicWishlistService.findByShareableId(shareableId);

        // Resolve user ID from authenticated user
        String userId = userRepository.findByEmail(authentication.getName())
                .map(User::getId)
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));

        ReservationResponse response = reservationService.reserve(itemId, userId);
        return ResponseEntity.ok(response);
    }
}
