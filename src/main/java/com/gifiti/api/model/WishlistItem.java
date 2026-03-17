package com.gifiti.api.model;

import com.gifiti.api.model.enums.ItemStatus;
import com.gifiti.api.model.enums.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.URL;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * WishlistItem entity representing a gift idea within a wishlist.
 * Root aggregate with denormalized ownerUserId for query performance.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "wishlist_items")
@CompoundIndex(name = "wishlist_owner_idx", def = "{'wishlistId': 1, 'ownerUserId': 1}")
public class WishlistItem {

    @Id
    private String id;

    @NotNull(message = "Wishlist ID is required")
    @Indexed
    private String wishlistId;

    @NotNull(message = "Owner user ID is required")
    @Indexed
    private String ownerUserId;

    @NotBlank(message = "Name is required")
    @Size(max = 200, message = "Name must not exceed 200 characters")
    private String name;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    @URL(message = "Product link must be a valid URL")
    private String productLink;

    @URL(message = "Image URL must be a valid URL")
    private String imageUrl;

    @Positive(message = "Price must be positive")
    private BigDecimal price;

    @NotNull
    @Builder.Default
    private Priority priority = Priority.MEDIUM;

    @Builder.Default
    private int quantity = 1;

    @Builder.Default
    private int reservedQuantity = 0;

    @NotNull
    @Builder.Default
    private ItemStatus status = ItemStatus.AVAILABLE;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
