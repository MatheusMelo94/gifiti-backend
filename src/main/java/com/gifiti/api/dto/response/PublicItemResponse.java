package com.gifiti.api.dto.response;

import com.gifiti.api.model.enums.ItemStatus;
import com.gifiti.api.model.enums.Priority;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Response DTO for public item viewing.
 * PRIVACY: Never includes reserverId or any information about who reserved the item.
 * Only shows that an item is AVAILABLE or RESERVED.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Public item view (no auth required)")
public class PublicItemResponse {

    @Schema(description = "Item ID", example = "65f1a2b3c4d5e6f7a8b9c0d2")
    private String id;

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

    @Schema(description = "Reservation status (AVAILABLE or RESERVED)", example = "AVAILABLE")
    private ItemStatus status;

    // PRIVACY: No reserverId, no timestamps, no ownerUserId
    // Viewers only need to know if an item is available to reserve
}
