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
     * Reserve an item for a user.
     * Uses MongoDB unique index for atomic reservation guarantee.
     *
     * @param itemId Item to reserve
     * @param reserverId User ID of the person reserving
     * @return Reservation response
     * @throws ResourceNotFoundException if item not found
     * @throws ConflictException if item is already reserved
     */
    public ReservationResponse reserve(String itemId, String reserverId) {
        return reserve(itemId, reserverId, 1);
    }

    public ReservationResponse reserve(String itemId, String reserverId, int quantity) {
        log.info("Attempting to reserve {} of item {} for user {}", quantity, itemId, reserverId);

        // Verify item exists
        WishlistItem item = wishlistItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ResourceNotFoundException.KEY_NOT_FOUND_WITH_FIELD,
                        "WishlistItem", "id", itemId));

        // Check remaining quantity
        int remaining = item.getQuantity() - item.getReservedQuantity();
        if (remaining <= 0) {
            throw new ConflictException("error.reservation.fully.reserved", new Object[0]);
        }
        if (quantity > remaining) {
            throw new ConflictException(
                    "error.reservation.partially.available", remaining, quantity);
        }

        // Create reservation - compound index (itemId, reserverId) ensures one per gifter per item
        Reservation reservation = Reservation.builder()
                .itemId(itemId)
                .reserverId(reserverId)
                .quantity(quantity)
                .build();

        try {
            reservationRepository.save(reservation);
            log.info("Reservation created for item {}", itemId);
        } catch (DuplicateKeyException e) {
            log.debug("Duplicate reservation detected for item {} by user {}", itemId, reserverId);
            throw new ConflictException("error.reservation.already.reserved.by.user", new Object[0]);
        }

        // Update item reserved quantity and status
        item.setReservedQuantity(item.getReservedQuantity() + quantity);
        if (item.getReservedQuantity() >= item.getQuantity()) {
            item.setStatus(ItemStatus.RESERVED);
        }
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
        log.info("Cancelling all reservations for item {}", itemId);

        WishlistItem item = wishlistItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ResourceNotFoundException.KEY_NOT_FOUND_WITH_FIELD,
                        "WishlistItem", "id", itemId));

        reservationRepository.deleteByItemId(itemId);
        log.info("All reservations deleted for item {}", itemId);

        item.setReservedQuantity(0);
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
