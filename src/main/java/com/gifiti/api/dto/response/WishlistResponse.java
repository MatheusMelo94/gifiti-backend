package com.gifiti.api.dto.response;

import com.gifiti.api.model.enums.Visibility;
import com.gifiti.api.model.enums.WishlistCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Response DTO for wishlist details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Wishlist details")
public class WishlistResponse {

    @Schema(description = "Wishlist ID", example = "65f1a2b3c4d5e6f7a8b9c0d1")
    private String id;

    @Schema(description = "Wishlist title", example = "Birthday 2026")
    private String title;

    @Schema(description = "Wishlist description", example = "Things I'd love for my birthday")
    private String description;

    @Schema(description = "Visibility setting", example = "PUBLIC")
    private Visibility visibility;

    @Schema(description = "Shareable NanoID for public link", example = "V1StGXR8_Z5jdHi6B-myT")
    private String shareableId;

    @Schema(description = "Optional event date", example = "2026-06-15")
    private LocalDate eventDate;

    @Schema(description = "Wishlist category", example = "BIRTHDAY")
    private WishlistCategory category;

    @Schema(description = "Number of items in the wishlist", example = "5")
    private int itemCount;

    @Schema(description = "Creation timestamp")
    private Instant createdAt;

    @Schema(description = "Last update timestamp")
    private Instant updatedAt;
}
