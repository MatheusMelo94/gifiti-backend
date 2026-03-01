package com.gifiti.api.service;

import com.gifiti.api.dto.response.ReservationResponse;
import com.gifiti.api.exception.ConflictException;
import com.gifiti.api.exception.ResourceNotFoundException;
import com.gifiti.api.model.Reservation;
import com.gifiti.api.model.WishlistItem;
import com.gifiti.api.model.enums.ItemStatus;
import com.gifiti.api.repository.ReservationRepository;
import com.gifiti.api.repository.WishlistItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for item reservation operations.
 * Uses MongoDB unique index for atomic reservation guarantees.
 *
 * ATOMICITY: The unique index on Reservation.itemId ensures only one
 * reservation can exist per item. Concurrent attempts result in DuplicateKeyException.
 *
 * PRIVACY: This service NEVER exposes reserverId to the wishlist owner.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final WishlistItemRepository wishlistItemRepository;

    /**
     * Reserve an item for a gifter.
     * Uses MongoDB unique index for atomic reservation guarantee.
     *
     * @param itemId Item to reserve
     * @param reserverId Anonymous identifier for the reserver
     * @return Reservation response
     * @throws ResourceNotFoundException if item not found
     * @throws ConflictException if item is already reserved
     */
    public ReservationResponse reserve(String itemId, String reserverId) {
        log.info("Attempting to reserve item {} for reserver {}", itemId, reserverId);

        // Verify item exists
        WishlistItem item = wishlistItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("WishlistItem", "id", itemId));

        // Check if item is already reserved (fast fail before attempting insert)
        if (item.getStatus() == ItemStatus.RESERVED) {
            log.debug("Item {} is already reserved", itemId);
            throw new ConflictException("Item is already reserved");
        }

        // Create reservation - unique index ensures atomicity
        Reservation reservation = Reservation.builder()
                .itemId(itemId)
                .reserverId(reserverId)
                .build();

        try {
            reservationRepository.save(reservation);
            log.info("Reservation created for item {}", itemId);
        } catch (DuplicateKeyException e) {
            // Another request reserved the item concurrently
            log.debug("Concurrent reservation detected for item {}", itemId);
            throw new ConflictException("Item is already reserved");
        }

        // Update item status to RESERVED
        item.setStatus(ItemStatus.RESERVED);
        wishlistItemRepository.save(item);

        return ReservationResponse.reserved(itemId);
    }

    /**
     * Cancel a reservation (unreserve an item).
     * Only the wishlist owner can unreserve items.
     *
     * @param itemId Item to unreserve
     * @return Reservation response
     * @throws ResourceNotFoundException if item or reservation not found
     */
    public ReservationResponse unreserve(String itemId) {
        log.info("Cancelling reservation for item {}", itemId);

        // Verify item exists
        WishlistItem item = wishlistItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("WishlistItem", "id", itemId));

        // Delete the reservation
        reservationRepository.deleteByItemId(itemId);
        log.info("Reservation deleted for item {}", itemId);

        // Update item status to AVAILABLE
        item.setStatus(ItemStatus.AVAILABLE);
        wishlistItemRepository.save(item);

        return ReservationResponse.unreserved(itemId);
    }

    /**
     * Delete all reservations for a list of item IDs.
     * Used for cascade delete when items or wishlists are deleted.
     *
     * @param itemIds List of item identifiers
     */
    public void deleteAllByItemIds(List<String> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return;
        }
        log.info("Cascade deleting reservations for {} items", itemIds.size());
        reservationRepository.deleteByItemIdIn(itemIds);
    }

    /**
     * Delete a single reservation by item ID.
     * Used for cascade delete when an item is deleted.
     *
     * @param itemId Item identifier
     */
    public void deleteByItemId(String itemId) {
        log.debug("Cascade deleting reservation for item {}", itemId);
        reservationRepository.deleteByItemId(itemId);
    }
}
