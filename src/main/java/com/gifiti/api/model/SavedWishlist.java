package com.gifiti.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "saved_wishlists")
@CompoundIndex(name = "user_wishlist_idx", def = "{'userId': 1, 'wishlistId': 1}", unique = true)
public class SavedWishlist {

    @Id
    private String id;

    private String userId;

    private String wishlistId;

    @CreatedDate
    private Instant createdAt;
}
