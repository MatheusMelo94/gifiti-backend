package com.gifiti.api.dto.request;

import com.gifiti.api.model.enums.Priority;
import com.gifiti.api.validation.SafeUrl;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request DTO for updating a wishlist item.
 * All fields are optional for partial updates.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Update item fields (all optional)")
public class UpdateItemRequest {

    @Size(max = 200, message = "Name must not exceed 200 characters")
    @Schema(description = "New item name", example = "Sony WH-1000XM5 (Silver)")
    private String name;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    @Schema(description = "New description", example = "Changed my mind — silver color")
    private String description;

    @SafeUrl(message = "Product link must be a valid URL (http/https only)")
    @Schema(description = "New product link", example = "https://www.amazon.com/dp/B0BX2L8PBT")
    private String productLink;

    @SafeUrl(message = "Image URL must be a valid URL (http/https only)")
    @Schema(description = "New image URL", example = "https://images.example.com/headphones-silver.jpg")
    private String imageUrl;

    @Positive(message = "Price must be positive")
    @Schema(description = "New price", example = "329.99")
    private BigDecimal price;

    @Schema(description = "New priority level", example = "LOW")
    private Priority priority;

    @Positive(message = "Quantity must be positive")
    @Schema(description = "New quantity", example = "3")
    private Integer quantity;
}
