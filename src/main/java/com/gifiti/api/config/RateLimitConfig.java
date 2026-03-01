package com.gifiti.api.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting configuration using Bucket4j.
 * Provides token bucket rate limiters for sensitive endpoints.
 *
 * Security hardening:
 * - Auth endpoints: 10 requests per minute per IP (prevents brute force)
 * - Reservation endpoint: 30 requests per minute per IP (prevents abuse)
 */
@Slf4j
@Component
public class RateLimitConfig {

    // Cache of rate limit buckets per IP
    private final Map<String, Bucket> authBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> reservationBuckets = new ConcurrentHashMap<>();

    /**
     * Get or create a rate limit bucket for auth endpoints.
     * Limit: 10 requests per minute per IP.
     *
     * @param clientIp The client IP address
     * @return The rate limit bucket for this IP
     */
    public Bucket getAuthBucket(String clientIp) {
        return authBuckets.computeIfAbsent(clientIp, ip -> createAuthBucket());
    }

    /**
     * Get or create a rate limit bucket for reservation endpoints.
     * Limit: 30 requests per minute per IP.
     *
     * @param clientIp The client IP address
     * @return The rate limit bucket for this IP
     */
    public Bucket getReservationBucket(String clientIp) {
        return reservationBuckets.computeIfAbsent(clientIp, ip -> createReservationBucket());
    }

    /**
     * Create a rate limit bucket for auth endpoints.
     * 10 requests per minute with gradual refill.
     */
    private Bucket createAuthBucket() {
        Bandwidth limit = Bandwidth.classic(10, Refill.greedy(10, Duration.ofMinutes(1)));
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    /**
     * Create a rate limit bucket for reservation endpoints.
     * 30 requests per minute with gradual refill.
     */
    private Bucket createReservationBucket() {
        Bandwidth limit = Bandwidth.classic(30, Refill.greedy(30, Duration.ofMinutes(1)));
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    /**
     * Try to consume a token from the auth bucket.
     *
     * @param clientIp The client IP address
     * @return true if request is allowed, false if rate limited
     */
    public boolean tryConsumeAuth(String clientIp) {
        boolean allowed = getAuthBucket(clientIp).tryConsume(1);
        if (!allowed) {
            log.warn("Rate limit exceeded for auth endpoint, IP: {}", maskIp(clientIp));
        }
        return allowed;
    }

    /**
     * Try to consume a token from the reservation bucket.
     *
     * @param clientIp The client IP address
     * @return true if request is allowed, false if rate limited
     */
    public boolean tryConsumeReservation(String clientIp) {
        boolean allowed = getReservationBucket(clientIp).tryConsume(1);
        if (!allowed) {
            log.warn("Rate limit exceeded for reservation endpoint, IP: {}", maskIp(clientIp));
        }
        return allowed;
    }

    /**
     * Mask IP address for logging (privacy).
     */
    private String maskIp(String ip) {
        if (ip == null || ip.length() < 4) {
            return "***";
        }
        return ip.substring(0, ip.lastIndexOf('.') + 1) + "***";
    }

    /**
     * Clean up old buckets (call periodically in production).
     * Simple implementation - clears all buckets.
     */
    public void cleanup() {
        log.debug("Cleaning up rate limit buckets: {} auth, {} reservation",
                authBuckets.size(), reservationBuckets.size());
        authBuckets.clear();
        reservationBuckets.clear();
    }
}
