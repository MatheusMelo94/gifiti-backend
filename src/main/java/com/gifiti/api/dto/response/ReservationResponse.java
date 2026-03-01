package com.gifiti.api.dto.response;

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
public class ReservationResponse {

    private String itemId;
    private String message;
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
