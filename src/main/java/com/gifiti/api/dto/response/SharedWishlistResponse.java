package com.gifiti.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A wishlist where the current user has reservations")
public class SharedWishlistResponse {

    @Schema(description = "Shareable ID for accessing the wishlist", example = "V1StGXR8_Z5jdHi6B-myT")
    private String shareableId;

    @Schema(description = "Wishlist title", example = "Maria's Birthday 2026")
    private String title;

    @Schema(description = "Wishlist owner's display name", example = "Maria")
    private String ownerDisplayName;

    @Schema(description = "Event date", example = "2026-06-15")
    private LocalDate eventDate;

    @Schema(description = "Total number of items in the wishlist", example = "5")
    private int itemCount;

    @Schema(description = "Number of items reserved by the current user", example = "2")
    private int myReservationCount;
}
