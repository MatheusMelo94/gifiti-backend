package com.gifiti.api.dto.response;

import com.gifiti.api.model.enums.ItemStatus;
import com.gifiti.api.model.enums.Priority;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Response DTO for wishlist item details.
 * PRIVACY: This DTO NEVER includes reserverId - privacy by design.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WishlistItemResponse {

    private String id;
    private String name;
    private String description;
    private String productLink;
    private String imageUrl;
    private BigDecimal price;
    private Priority priority;
    private ItemStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    // PRIVACY: reserverId is deliberately NOT included here.
    // The wishlist owner must NEVER know who reserved an item.
}
