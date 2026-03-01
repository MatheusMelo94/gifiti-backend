package com.gifiti.api.repository;

import com.gifiti.api.model.WishlistItem;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for WishlistItem entity operations.
 * Provides queries by wishlistId and ownerUserId for efficient lookups.
 */
@Repository
public interface WishlistItemRepository extends MongoRepository<WishlistItem, String> {

    /**
     * Find all items belonging to a specific wishlist.
     *
     * @param wishlistId the wishlist identifier
     * @return list of items in the wishlist
     */
    List<WishlistItem> findByWishlistId(String wishlistId);

    /**
     * Find all items owned by a specific user across all wishlists.
     *
     * @param ownerUserId the user identifier
     * @return list of items owned by the user
     */
    List<WishlistItem> findByOwnerUserId(String ownerUserId);

    /**
     * Delete all items belonging to a specific wishlist.
     * Used for cascade deletion when a wishlist is deleted.
     *
     * @param wishlistId the wishlist identifier
     */
    void deleteByWishlistId(String wishlistId);
}
