package com.gifiti.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Response DTO for a gifter's reservation.
 * Shows the gifter what they've reserved across all wishlists.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A gifter's reservation details")
public class GifterReservationResponse {

    @Schema(description = "Item ID", example = "65f1a2b3c4d5e6f7a8b9c0d2")
    private String itemId;

    @Schema(description = "Item name", example = "Sony WH-1000XM5 Headphones")
    private String itemName;

    @Schema(description = "Item image URL")
    private String itemImageUrl;

    @Schema(description = "Item price", example = "349.99")
    private BigDecimal itemPrice;

    @Schema(description = "Wishlist title", example = "Maria's Birthday 2026")
    private String wishlistTitle;

    @Schema(description = "Wishlist shareable ID", example = "V1StGXR8_Z5jdHi6B-myT")
    private String wishlistShareableId;

    @Schema(description = "Event date", example = "2026-06-15")
    private LocalDate eventDate;

    @Schema(description = "When the reservation was made")
    private Instant reservedAt;
}
