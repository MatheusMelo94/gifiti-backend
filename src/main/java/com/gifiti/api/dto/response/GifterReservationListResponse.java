package com.gifiti.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO containing all reservations for a gifter.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "List of gifter's reservations")
public class GifterReservationListResponse {

    @Schema(description = "Reservations")
    private List<GifterReservationResponse> reservations;

    @Schema(description = "Total number of reservations", example = "3")
    private int totalCount;
}
