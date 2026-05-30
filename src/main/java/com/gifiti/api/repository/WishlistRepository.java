package com.gifiti.api.repository;

import com.gifiti.api.model.Wishlist;
import com.gifiti.api.model.enums.Visibility;
import com.gifiti.api.model.enums.WishlistCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Wishlist MongoDB operations.
 *
 * <p>Extends {@link WishlistRepositoryCustom} for operations that cannot be
 * expressed via derived-query naming — currently the atomic compare-and-set
 * backfill write (feature 008 / T13, Security findings F-6 pin 3).
 */
@Repository
public interface WishlistRepository extends MongoRepository<Wishlist, String>, WishlistRepositoryCustom {

    /**
     * Find all wishlists owned by a user.
     *
     * @param ownerUserId Owner's user ID
     * @return List of wishlists
     */
    List<Wishlist> findByOwnerUserId(String ownerUserId);

    Page<Wishlist> findByOwnerUserId(String ownerUserId, Pageable pageable);

    Page<Wishlist> findByOwnerUserIdAndCategory(String ownerUserId, WishlistCategory category, Pageable pageable);

    /**
     * Find a wishlist by its shareable ID.
     *
     * @param shareableId Shareable identifier
     * @return Optional containing wishlist if found
     */
    Optional<Wishlist> findByShareableId(String shareableId);

    /**
     * Count wishlists owned by a user.
     *
     * @param ownerUserId Owner's user ID
     * @return Number of wishlists
     */
    long countByOwnerUserId(String ownerUserId);

    List<Wishlist> findByIdIn(List<String> ids);

    /**
     * Find wishlists by visibility with null {@code accessCode}, used by the
     * {@code AccessCodeBackfillRunner} (feature 008 / T13). Pairs with the
     * atomic write {@link WishlistRepositoryCustom#updateAccessCodeIfNull}:
     * the read narrows the candidate set; the write rechecks the null
     * precondition (Security findings F-6 pin 3).
     */
    List<Wishlist> findByVisibilityAndAccessCodeIsNull(Visibility visibility);
}
