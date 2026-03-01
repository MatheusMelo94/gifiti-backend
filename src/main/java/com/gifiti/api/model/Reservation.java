package com.gifiti.api.model;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Reservation entity representing a gift item reservation.
 *
 * ATOMICITY: The unique index on itemId ensures only one reservation can exist
 * per item. Concurrent reservation attempts will fail with DuplicateKeyException.
 *
 * PRIVACY: reserverId is stored but NEVER exposed to the wishlist owner.
 * This allows gifters to track their reservations while keeping the surprise.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "reservations")
public class Reservation {

    @Id
    private String id;

    /**
     * The item being reserved.
     * UNIQUE INDEX ensures only one reservation per item (atomicity guarantee).
     */
    @NotNull(message = "Item ID is required")
    @Indexed(unique = true)
    private String itemId;

    /**
     * Anonymous identifier for the person making the reservation.
     * This could be a session ID, browser fingerprint, or optional email.
     * PRIVACY: This is NEVER exposed to the wishlist owner.
     */
    @NotNull(message = "Reserver ID is required")
    private String reserverId;

    @CreatedDate
    private Instant createdAt;
}
