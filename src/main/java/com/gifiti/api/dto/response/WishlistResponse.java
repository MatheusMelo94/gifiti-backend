package com.gifiti.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
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
 *
 * <p>Per feature 008 (T5): the {@code accessCode} field is owner-facing only.
 * It carries a 4-digit numeric code for PRIVATE wishlists and is null for
 * PUBLIC wishlists. {@code @JsonInclude(NON_NULL)} ensures the field is
 * omitted from the JSON payload when null (PUBLIC case), preserving the
 * pre-008 response shape for PUBLIC wishlists.
 *
 * <p>Per Security findings F-4: the field is NEVER exposed in
 * {@code PublicWishlistResponse} or {@code SharedWishlistResponse}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
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

    @Schema(description = "Cover image URL", example = "https://pub-abc.r2.dev/users/u1/wishlists/img.jpg")
    private String coverImageUrl;

    @Schema(description = "Number of items in the wishlist", example = "5")
    private int itemCount;

    /**
     * 4-digit numeric access code for PRIVATE wishlists (feature 008 / T5).
     * Null for PUBLIC wishlists.
     *
     * <p>Owner-facing only. {@code PublicWishlistResponse} and
     * {@code SharedWishlistResponse} deliberately do NOT include this field
     * per Security findings F-4 (mass-assignment / field-leak coverage).
     */
    @Schema(description = "Access code for PRIVATE wishlists (null for PUBLIC)",
            example = "1234")
    private String accessCode;

    @Schema(description = "Creation timestamp")
    private Instant createdAt;

    @Schema(description = "Last update timestamp")
    private Instant updatedAt;
}
