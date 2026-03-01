package com.gifiti.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for public wishlist viewing.
 * Contains wishlist details and all items for anonymous viewers.
 * PRIVACY: Never includes ownerUserId or any information identifying the owner.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicWishlistResponse {

    private String shareableId;
    private String title;
    private String description;
    private int itemCount;
    private List<PublicItemResponse> items;

    // PRIVACY: No ownerUserId, no internal id, no timestamps
    // Public viewers only see what they need to browse and reserve items
}
