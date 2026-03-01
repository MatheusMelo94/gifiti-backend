package com.gifiti.api.dto.response;

import com.gifiti.api.model.enums.Visibility;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO for wishlist details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WishlistResponse {

    private String id;
    private String title;
    private String description;
    private Visibility visibility;
    private String shareableId;
    private int itemCount;
    private Instant createdAt;
    private Instant updatedAt;
}
