package com.gifiti.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "List of wishlists shared with the current user")
public class SharedWishlistListResponse {

    @Schema(description = "Wishlists where the user has reservations")
    private List<SharedWishlistResponse> wishlists;

    @Schema(description = "Total number of shared wishlists", example = "3")
    private int totalCount;
}
