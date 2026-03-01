package com.gifiti.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * MongoDB configuration with transaction support.
 *
 * Security hardening (H-01):
 * - Enables MongoDB transactions for atomic multi-document operations
 * - Prevents race conditions in cascade deletion
 * - Requires MongoDB 4.0+ replica set (MongoDB Atlas supports this)
 *
 * Note: @EnableMongoAuditing is on GifitiApplication to avoid duplicate bean registration.
 */
@Configuration
@EnableTransactionManagement
public class MongoConfig {

    /**
     * Configure MongoDB transaction manager for @Transactional support.
     * Requires MongoDB 4.0+ with replica set (MongoDB Atlas M0+ supports this).
     */
    @Bean
    MongoTransactionManager transactionManager(MongoDatabaseFactory dbFactory) {
        return new MongoTransactionManager(dbFactory);
    }
}
