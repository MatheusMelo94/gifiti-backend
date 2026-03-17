package com.gifiti.api.repository;

import com.gifiti.api.model.Reservation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Reservation entity operations.
 * The unique index on itemId in the Reservation entity provides atomicity guarantees.
 */
@Repository
public interface ReservationRepository extends MongoRepository<Reservation, String> {

    /**
     * Find reservation by item ID.
     *
     * @param itemId the item identifier
     * @return optional reservation if exists
     */
    Optional<Reservation> findByItemId(String itemId);

    /**
     * Delete reservation by item ID.
     * Used when unreserving an item.
     *
     * @param itemId the item identifier
     */
    void deleteByItemId(String itemId);

    /**
     * Find all reservations for a list of item IDs.
     * Used for batch operations like cascade delete.
     *
     * @param itemIds list of item identifiers
     * @return list of reservations
     */
    List<Reservation> findByItemIdIn(List<String> itemIds);

    /**
     * Delete all reservations for a list of item IDs.
     * Used for cascade delete when a wishlist is deleted.
     *
     * @param itemIds list of item identifiers
     */
    void deleteByItemIdIn(List<String> itemIds);

    /**
     * Find all reservations made by a specific reserver.
     * Allows gifters to see what they've reserved.
     *
     * @param reserverId the reserver identifier
     * @return list of reservations
     */
    List<Reservation> findByReserverId(String reserverId);

    Optional<Reservation> findByItemIdAndReserverId(String itemId, String reserverId);

    void deleteByItemIdAndReserverId(String itemId, String reserverId);
}
