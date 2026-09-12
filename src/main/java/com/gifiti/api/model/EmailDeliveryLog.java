package com.gifiti.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * One row per attempt to send an email, successful or not.
 *
 * <p>Exists because the 2026-09-12 invalid-key outage was invisible: sends fail on
 * an {@code @Async} pool thread, {@code register()} returns 201 regardless, and the
 * only evidence was an ERROR line in whatever log window had not yet rotated. This
 * makes the question "did this user ever get an email, and if not why" answerable
 * by query instead of by log archaeology.
 *
 * <h2>Personal data</h2>
 * <p>{@code recipient} is personal data under LGPD, and {@code errorMessage} may
 * echo it back from the provider. Retention is therefore capped by a TTL index at
 * 90 days rather than kept indefinitely — long enough to investigate an incident,
 * short enough to satisfy Art. 6 IX data minimization. This collection must also
 * be listed in {@code docs/posthog-account-deletion-runbook.md} as a location
 * holding user data, since an erasure request has to reach it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "email_delivery_log")
public class EmailDeliveryLog {

    @Id
    private String id;

    /** Recipient address — indexed to support "what happened to this user's mail". */
    @Indexed
    private String recipient;

    /**
     * Locale-resolved subject line. Kept as a human-readable hint at which email
     * this was; it is not a stable discriminator, since it varies by language.
     */
    private String subject;

    @Indexed
    private EmailDeliveryStatus status;

    /** Provider error name where one is available (e.g. {@code invalid_api_key}). */
    private String errorName;

    private String errorMessage;

    /**
     * Correlation ID of the request that triggered the send, carried across the
     * {@code @Async} boundary by {@code AsyncConfig}'s task decorator.
     */
    private String correlationId;

    /** TTL index — Mongo removes rows 90 days after creation. */
    @Indexed(expireAfter = "90d")
    private Instant createdAt;
}
