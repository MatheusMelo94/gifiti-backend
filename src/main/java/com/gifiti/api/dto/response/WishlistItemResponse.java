package com.gifiti.api.dto.response;

import com.gifiti.api.model.enums.ItemStatus;
import com.gifiti.api.model.enums.Priority;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Response DTO for wishlist item details.
 * PRIVACY: This DTO NEVER includes reserverId - privacy by design.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Wishlist item details (owner view)")
public class WishlistItemResponse {

    @Schema(description = "Item ID", example = "65f1a2b3c4d5e6f7a8b9c0d2")
    private String id;

    @Schema(description = "Parent wishlist ID", example = "65f1a2b3c4d5e6f7a8b9c0d1")
    private String wishlistId;

    @Schema(description = "Item name", example = "Sony WH-1000XM5 Headphones")
    private String name;

    @Schema(description = "Item description", example = "Noise-cancelling, black color")
    private String description;

    @Schema(description = "Link to product page", example = "https://www.amazon.com/dp/B0BX2L8PBT")
    private String productLink;

    @Schema(description = "Image URL", example = "https://images.example.com/headphones.jpg")
    private String imageUrl;

    @Schema(description = "Item price", example = "349.99")
    private BigDecimal price;

    @Schema(description = "Priority level", example = "HIGH")
    private Priority priority;

    @Schema(description = "Desired quantity", example = "3")
    private int quantity;

    @Schema(description = "Number already reserved", example = "1")
    private int reservedQuantity;

    @Schema(description = "Remaining available quantity", example = "2")
    private int remainingQuantity;

    @Schema(description = "Reservation status", example = "AVAILABLE")
    private ItemStatus status;

    @Schema(description = "Creation timestamp")
    private Instant createdAt;

    @Schema(description = "Last update timestamp")
    private Instant updatedAt;

    // PRIVACY: reserverId is deliberately NOT included here.
    // The wishlist owner must NEVER know who reserved an item.
}
