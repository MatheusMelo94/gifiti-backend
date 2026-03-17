package com.gifiti.api.dto.request;

import com.gifiti.api.model.enums.Priority;
import com.gifiti.api.validation.NoHtml;
import com.gifiti.api.validation.SafeUrl;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request DTO for creating a new wishlist item.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Create a new wishlist item")
public class CreateItemRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 200, message = "Name must not exceed 200 characters")
    @NoHtml
    @Schema(description = "Item name", example = "Sony WH-1000XM5 Headphones")
    private String name;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    @NoHtml
    @Schema(description = "Optional description", example = "Noise-cancelling, black color")
    private String description;

    @SafeUrl(message = "Product link must be a valid URL (http/https only)")
    @Schema(description = "Link to the product page", example = "https://www.amazon.com/dp/B0BX2L8PBT")
    private String productLink;

    @SafeUrl(message = "Image URL must be a valid URL (http/https only)")
    @Schema(description = "Image URL for display", example = "https://images.example.com/headphones.jpg")
    private String imageUrl;

    @Positive(message = "Price must be positive")
    @Schema(description = "Item price", example = "349.99")
    private BigDecimal price;

    @Builder.Default
    @Schema(description = "Priority level", example = "HIGH")
    private Priority priority = Priority.MEDIUM;

    @Positive(message = "Quantity must be positive")
    @Builder.Default
    @Schema(description = "Desired quantity (default 1)", example = "1")
    private int quantity = 1;
}
