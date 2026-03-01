package com.gifiti.api.dto.response;

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
public class ItemListResponse {

    private List<WishlistItemResponse> items;
}
