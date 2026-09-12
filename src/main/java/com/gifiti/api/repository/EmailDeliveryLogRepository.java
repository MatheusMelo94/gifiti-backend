package com.gifiti.api.repository;

import com.gifiti.api.model.EmailDeliveryLog;
import com.gifiti.api.model.EmailDeliveryStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface EmailDeliveryLogRepository extends MongoRepository<EmailDeliveryLog, String> {

    /**
     * Support query — "did this address ever receive anything, and what failed".
     */
    List<EmailDeliveryLog> findByRecipientOrderByCreatedAtDesc(String recipient);

    /**
     * Ops query — "how many sends failed since X". This is the number that would
     * have surfaced the 2026-09-12 outage on the day it started.
     */
    long countByStatusAndCreatedAtAfter(EmailDeliveryStatus status, Instant since);
}
