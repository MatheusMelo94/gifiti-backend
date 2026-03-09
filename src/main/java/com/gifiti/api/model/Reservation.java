package com.gifiti.api.model;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Reservation entity representing a gift item reservation.
 *
 * ATOMICITY: The compound unique index on (itemId, reserverId) ensures only one
 * reservation per item per gifter. Multiple gifters can reserve the same item
 * when quantity > 1.
 *
 * PRIVACY: reserverId is stored but NEVER exposed to the wishlist owner.
 * This allows gifters to track their reservations while keeping the surprise.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "reservations")
@CompoundIndex(name = "item_reserver_idx", def = "{'itemId': 1, 'reserverId': 1}", unique = true)
public class Reservation {

    @Id
    private String id;

    @NotNull(message = "Item ID is required")
    @Indexed
    private String itemId;

    /**
     * The user ID of the person who reserved this item.
     * PRIVACY: This is NEVER exposed to the wishlist owner.
     */
    @NotNull(message = "Reserver ID is required")
    private String reserverId;

    @Builder.Default
    private int quantity = 1;

    @CreatedDate
    private Instant createdAt;
}
