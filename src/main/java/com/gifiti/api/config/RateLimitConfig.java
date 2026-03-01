package com.gifiti.api.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Rate limiting configuration using Bucket4j with Caffeine cache.
 *
 * Security hardening (H-02):
 * - Uses Caffeine cache with automatic TTL-based eviction (prevents memory leak)
 * - Hard limit of 10,000 entries per cache (prevents memory exhaustion)
 * - Buckets automatically evicted after 10 minutes of inactivity
 *
 * Rate limits:
 * - Auth endpoints: 10 requests per minute per IP (prevents brute force)
 * - Reservation endpoint: 30 requests per minute per IP (prevents abuse)
 * - Authenticated endpoints: 100 requests per minute per IP (prevents abuse)
 */
@Slf4j
@Component
public class RateLimitConfig {

    /**
     * Cache for auth endpoint rate limit buckets.
     * Auto-evicts entries after 10 minutes of inactivity.
     * Maximum 10,000 unique IPs tracked.
     */
    private final Cache<String, Bucket> authBuckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .maximumSize(10_000)
            .build();

    /**
     * Cache for reservation endpoint rate limit buckets.
     */
    private final Cache<String, Bucket> reservationBuckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .maximumSize(10_000)
            .build();

    /**
     * Cache for authenticated endpoint rate limit buckets.
     * More permissive limit for logged-in users.
     */
    private final Cache<String, Bucket> authenticatedBuckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(5))
            .maximumSize(10_000)
            .build();

    /**
     * Cache for public endpoint rate limit buckets (M-04 security fix).
     * Prevents enumeration and DoS attacks on public wishlist viewing.
     */
    private final Cache<String, Bucket> publicBuckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .maximumSize(10_000)
            .build();

    /**
     * Cache for token refresh endpoint rate limit buckets (M-02 security fix).
     * Separate from auth to prevent refresh traffic from blocking logins.
     */
    private final Cache<String, Bucket> refreshBuckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .maximumSize(10_000)
            .build();

    /**
     * Try to consume a token from the auth bucket.
     * Limit: 10 requests per minute per IP.
     *
     * @param clientIp The client IP address
     * @return true if request is allowed, false if rate limited
     */
    public boolean tryConsumeAuth(String clientIp) {
        Bucket bucket = authBuckets.get(clientIp, ip -> createAuthBucket());
        boolean allowed = bucket.tryConsume(1);
        if (!allowed) {
            log.warn("SECURITY_EVENT: Rate limit exceeded for auth endpoint, IP: {}", maskIp(clientIp));
        }
        return allowed;
    }

    /**
     * Try to consume a token from the reservation bucket.
     * Limit: 30 requests per minute per IP.
     *
     * @param clientIp The client IP address
     * @return true if request is allowed, false if rate limited
     */
    public boolean tryConsumeReservation(String clientIp) {
        Bucket bucket = reservationBuckets.get(clientIp, ip -> createReservationBucket());
        boolean allowed = bucket.tryConsume(1);
        if (!allowed) {
            log.warn("SECURITY_EVENT: Rate limit exceeded for reservation endpoint, IP: {}", maskIp(clientIp));
        }
        return allowed;
    }

    /**
     * Try to consume a token from the authenticated bucket.
     * Limit: 100 requests per minute per IP.
     *
     * @param clientIp The client IP address
     * @return true if request is allowed, false if rate limited
     */
    public boolean tryConsumeAuthenticated(String clientIp) {
        Bucket bucket = authenticatedBuckets.get(clientIp, ip -> createAuthenticatedBucket());
        boolean allowed = bucket.tryConsume(1);
        if (!allowed) {
            log.warn("SECURITY_EVENT: Rate limit exceeded for authenticated endpoint, IP: {}", maskIp(clientIp));
        }
        return allowed;
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
     * Create a rate limit bucket for authenticated endpoints.
     * 100 requests per minute (generous for legitimate users).
     */
    private Bucket createAuthenticatedBucket() {
        Bandwidth limit = Bandwidth.classic(100, Refill.greedy(100, Duration.ofMinutes(1)));
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    /**
     * Try to consume a token from the public bucket (M-04 security fix).
     * Limit: 60 requests per minute per IP.
     *
     * @param clientIp The client IP address
     * @return true if request is allowed, false if rate limited
     */
    public boolean tryConsumePublic(String clientIp) {
        Bucket bucket = publicBuckets.get(clientIp, ip -> createPublicBucket());
        boolean allowed = bucket.tryConsume(1);
        if (!allowed) {
            log.warn("SECURITY_EVENT: Rate limit exceeded for public endpoint, IP: {}", maskIp(clientIp));
        }
        return allowed;
    }

    /**
     * Create a rate limit bucket for public endpoints.
     * 60 requests per minute (prevents enumeration/scraping).
     */
    private Bucket createPublicBucket() {
        Bandwidth limit = Bandwidth.classic(60, Refill.greedy(60, Duration.ofMinutes(1)));
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    /**
     * Try to consume a token from the refresh bucket (M-02 security fix).
     * Limit: 20 requests per minute per IP.
     * More permissive than auth since no password guessing risk.
     *
     * @param clientIp The client IP address
     * @return true if request is allowed, false if rate limited
     */
    public boolean tryConsumeRefresh(String clientIp) {
        Bucket bucket = refreshBuckets.get(clientIp, ip -> createRefreshBucket());
        boolean allowed = bucket.tryConsume(1);
        if (!allowed) {
            log.warn("SECURITY_EVENT: Rate limit exceeded for refresh endpoint, IP: {}", maskIp(clientIp));
        }
        return allowed;
    }

    /**
     * Create a rate limit bucket for token refresh endpoint.
     * 20 requests per minute (less restrictive than login since no password guessing).
     */
    private Bucket createRefreshBucket() {
        Bandwidth limit = Bandwidth.classic(20, Refill.greedy(20, Duration.ofMinutes(1)));
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    /**
     * Mask IP address for logging (privacy).
     */
    private String maskIp(String ip) {
        if (ip == null || ip.length() < 4) {
            return "***";
        }
        int lastDot = ip.lastIndexOf('.');
        if (lastDot > 0) {
            return ip.substring(0, lastDot + 1) + "***";
        }
        return ip.substring(0, Math.min(ip.length(), 8)) + "***";
    }

    /**
     * Get current cache sizes for monitoring.
     */
    public String getCacheStats() {
        return String.format("auth=%d, reservation=%d, authenticated=%d, public=%d, refresh=%d",
                authBuckets.estimatedSize(),
                reservationBuckets.estimatedSize(),
                authenticatedBuckets.estimatedSize(),
                publicBuckets.estimatedSize(),
                refreshBuckets.estimatedSize());
    }
}
