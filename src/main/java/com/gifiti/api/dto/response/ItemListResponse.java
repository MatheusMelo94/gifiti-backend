package com.gifiti.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO wrapping a list of wishlist items.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "List of wishlist items")
public class ItemListResponse {

    @Schema(description = "Items in the wishlist")
    private List<WishlistItemResponse> items;

    @Schema(description = "Total number of items")
    private long totalElements;

    @Schema(description = "Total number of pages")
    private int totalPages;

    @Schema(description = "Current page number (0-based)")
    private int currentPage;

    @Schema(description = "Page size")
    private int size;
}
