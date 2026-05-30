package com.gifiti.api.repository;

import com.gifiti.api.model.Wishlist;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/**
 * Implementation of {@link WishlistRepositoryCustom} backed by
 * {@link MongoTemplate}. Spring Data picks this up automatically when the
 * class name matches {@code <RepositoryName>Impl}.
 *
 * <p>Feature 008 / T13 — Security findings F-6 pin 3: atomic compare-and-set
 * for the backfill runner.
 */
@RequiredArgsConstructor
public class WishlistRepositoryImpl implements WishlistRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    @Override
    public long updateAccessCodeIfNull(String id, String newCode) {
        // Filter: doc matches ONLY when accessCode is null OR the field is
        // absent entirely (legacy documents written before feature 008).
        Query filter = new Query(new Criteria().andOperator(
                Criteria.where("_id").is(id),
                new Criteria().orOperator(
                        Criteria.where("accessCode").isNull(),
                        Criteria.where("accessCode").exists(false))));

        Update update = new Update().set("accessCode", newCode);
        return mongoTemplate.updateFirst(filter, update, Wishlist.class).getModifiedCount();
    }
}
