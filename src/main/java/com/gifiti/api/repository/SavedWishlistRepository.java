package com.gifiti.api.repository;

import com.gifiti.api.model.SavedWishlist;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SavedWishlistRepository extends MongoRepository<SavedWishlist, String> {

    List<SavedWishlist> findByUserId(String userId);

    Optional<SavedWishlist> findByUserIdAndWishlistId(String userId, String wishlistId);

    boolean existsByUserIdAndWishlistId(String userId, String wishlistId);

    void deleteByUserIdAndWishlistId(String userId, String wishlistId);
}
