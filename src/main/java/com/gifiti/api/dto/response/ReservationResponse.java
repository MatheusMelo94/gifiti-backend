package com.gifiti.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for reservation operations.
 * Confirms the reservation was successful without exposing sensitive data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Reservation operation result")
public class ReservationResponse {

    @Schema(description = "Item ID", example = "65f1a2b3c4d5e6f7a8b9c0d2")
    private String itemId;

    @Schema(description = "Result message", example = "Item reserved successfully")
    private String message;

    @Schema(description = "Whether the item is currently reserved", example = "true")
    private boolean reserved;

    /**
     * Factory method for successful reservation.
     */
    public static ReservationResponse reserved(String itemId) {
        return ReservationResponse.builder()
                .itemId(itemId)
                .message("Item reserved successfully")
                .reserved(true)
                .build();
    }

    /**
     * Factory method for successful unreservation.
     */
    public static ReservationResponse unreserved(String itemId) {
        return ReservationResponse.builder()
                .itemId(itemId)
                .message("Reservation cancelled successfully")
                .reserved(false)
                .build();
    }
}
