package com.gifiti.api.dto.response;

import com.gifiti.api.model.enums.ItemStatus;
import com.gifiti.api.model.enums.Priority;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Response DTO for public item viewing.
 * PRIVACY: Never includes reserverId or any information about who reserved the item.
 * Only shows that an item is AVAILABLE or RESERVED.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicItemResponse {

    private String id;
    private String name;
    private String description;
    private String productLink;
    private String imageUrl;
    private BigDecimal price;
    private Priority priority;
    private ItemStatus status;

    // PRIVACY: No reserverId, no timestamps, no ownerUserId
    // Viewers only need to know if an item is available to reserve
}
