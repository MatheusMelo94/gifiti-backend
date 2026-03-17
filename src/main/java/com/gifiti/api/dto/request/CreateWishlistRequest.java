package com.gifiti.api.dto.request;

import com.gifiti.api.model.enums.Visibility;
import com.gifiti.api.model.enums.WishlistCategory;
import com.gifiti.api.validation.NoHtml;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Request DTO for creating a new wishlist.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Create a new wishlist")
public class CreateWishlistRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 100, message = "Title must not exceed 100 characters")
    @NoHtml
    @Schema(description = "Wishlist title", example = "Birthday 2026")
    private String title;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    @NoHtml
    @Schema(description = "Optional description", example = "Things I'd love for my birthday")
    private String description;

    @Builder.Default
    @Schema(description = "Visibility (PRIVATE or PUBLIC)", example = "PUBLIC")
    private Visibility visibility = Visibility.PRIVATE;

    @Schema(description = "Optional event date", example = "2026-06-15")
    private LocalDate eventDate;

    @Schema(description = "Optional category", example = "BIRTHDAY")
    private WishlistCategory category;
}
