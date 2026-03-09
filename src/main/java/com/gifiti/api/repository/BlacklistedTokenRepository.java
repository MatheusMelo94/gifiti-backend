package com.gifiti.api.repository;

import com.gifiti.api.model.BlacklistedToken;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BlacklistedTokenRepository extends MongoRepository<BlacklistedToken, String> {

    boolean existsByTokenHash(String tokenHash);
}
