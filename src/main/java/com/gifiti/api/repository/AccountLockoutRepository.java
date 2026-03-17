package com.gifiti.api.repository;

import com.gifiti.api.model.AccountLockout;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface AccountLockoutRepository extends MongoRepository<AccountLockout, String> {
    Optional<AccountLockout> findByEmail(String email);
    void deleteByEmail(String email);
}
