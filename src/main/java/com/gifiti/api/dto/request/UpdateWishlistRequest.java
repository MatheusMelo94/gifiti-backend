package com.gifiti.api.dto.request;

import com.gifiti.api.model.enums.Visibility;
import com.gifiti.api.model.enums.WishlistCategory;
import com.gifiti.api.validation.NoHtml;
import com.gifiti.api.validation.SafeUrl;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Request DTO for updating a wishlist.
 * All fields are optional for partial updates.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Update wishlist fields (all optional)")
public class UpdateWishlistRequest {

    @Size(max = 100, message = "Title must not exceed 100 characters")
    @NoHtml
    @Schema(description = "New title", example = "Christmas 2026")
    private String title;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    @NoHtml
    @Schema(description = "New description", example = "Updated gift ideas")
    private String description;

    @Schema(description = "New visibility", example = "PUBLIC")
    private Visibility visibility;

    @Schema(description = "New event date", example = "2026-06-20")
    private LocalDate eventDate;

    @Schema(description = "New category", example = "CHRISTMAS")
    private WishlistCategory category;

    @SafeUrl
    @Schema(description = "Cover image URL (null to remove)", example = "https://pub-abc.r2.dev/users/u1/wishlists/img.jpg")
    private String coverImageUrl;
}
