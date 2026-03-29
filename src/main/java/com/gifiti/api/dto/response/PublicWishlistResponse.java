package com.gifiti.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Response DTO for public wishlist viewing.
 * Contains wishlist details and all items for anonymous viewers.
 * PRIVACY: Never includes ownerUserId or any information identifying the owner.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Public wishlist view (no auth required)")
public class PublicWishlistResponse {

    @Schema(description = "Shareable NanoID", example = "V1StGXR8_Z5jdHi6B-myT")
    private String shareableId;

    @Schema(description = "Wishlist title", example = "Birthday 2026")
    private String title;

    @Schema(description = "Wishlist description", example = "Things I'd love for my birthday")
    private String description;

    @Schema(description = "Owner's display name", example = "Maria Santos")
    private String ownerDisplayName;

    @Schema(description = "Event date", example = "2026-06-15")
    private LocalDate eventDate;

    @Schema(description = "Cover image URL", example = "https://pub-abc.r2.dev/users/u1/wishlists/img.jpg")
    private String coverImageUrl;

    @Schema(description = "Total number of items", example = "5")
    private int itemCount;

    @Schema(description = "Items in the wishlist")
    private List<PublicItemResponse> items;

    // PRIVACY: No ownerUserId, no internal id, no timestamps
    // Public viewers only see what they need to browse and reserve items
}
