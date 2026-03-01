package com.gifiti.api.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Account lockout service to prevent brute force attacks.
 *
 * Security hardening (H-02):
 * - Tracks failed login attempts per account (not just IP)
 * - Locks accounts after MAX_ATTEMPTS failed attempts
 * - Lock duration: LOCKOUT_DURATION minutes
 * - Uses Caffeine cache for automatic eviction (no memory leak)
 *
 * Combined with IP-based rate limiting in RateLimitFilter,
 * this provides defense-in-depth against distributed brute force.
 */
@Slf4j
@Service
public class AccountLockoutService {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(30);
    private static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(15);

    /**
     * Cache for tracking failed login attempts per email.
     * Auto-evicts after 15 minutes of inactivity.
     */
    private final Cache<String, Integer> failedAttempts = Caffeine.newBuilder()
            .expireAfterWrite(ATTEMPT_WINDOW)
            .maximumSize(50_000)
            .build();

    /**
     * Cache for locked accounts.
     * Stores the unlock time (Instant) for each locked account.
     */
    private final Cache<String, Instant> lockedAccounts = Caffeine.newBuilder()
            .expireAfterWrite(LOCKOUT_DURATION.plusMinutes(1)) // Slightly longer than lockout
            .maximumSize(50_000)
            .build();

    /**
     * Record a failed login attempt for an account.
     * If max attempts exceeded, locks the account.
     *
     * @param email The account email
     */
    public void recordFailedAttempt(String email) {
        String normalizedEmail = email.toLowerCase().trim();
        Integer attempts = failedAttempts.get(normalizedEmail, k -> 0);
        attempts++;
        failedAttempts.put(normalizedEmail, attempts);

        log.debug("Failed attempt {} for account: {}", attempts, maskEmail(normalizedEmail));

        if (attempts >= MAX_ATTEMPTS) {
            Instant unlockTime = Instant.now().plus(LOCKOUT_DURATION);
            lockedAccounts.put(normalizedEmail, unlockTime);
            log.warn("SECURITY_EVENT: Account locked due to {} failed attempts: {}",
                     attempts, maskEmail(normalizedEmail));
        }
    }

    /**
     * Record a successful login, clearing failed attempts.
     *
     * @param email The account email
     */
    public void recordSuccessfulLogin(String email) {
        String normalizedEmail = email.toLowerCase().trim();
        failedAttempts.invalidate(normalizedEmail);
        // Don't clear lock - let it expire naturally to prevent lock-bypass attacks
        log.debug("Cleared failed attempts for account: {}", maskEmail(normalizedEmail));
    }

    /**
     * Check if an account is currently locked.
     *
     * @param email The account email
     * @return true if locked, false otherwise
     */
    public boolean isLocked(String email) {
        String normalizedEmail = email.toLowerCase().trim();
        Instant unlockTime = lockedAccounts.getIfPresent(normalizedEmail);

        if (unlockTime == null) {
            return false;
        }

        if (Instant.now().isAfter(unlockTime)) {
            // Lock expired, clean up
            lockedAccounts.invalidate(normalizedEmail);
            failedAttempts.invalidate(normalizedEmail);
            log.debug("Account lock expired: {}", maskEmail(normalizedEmail));
            return false;
        }

        return true;
    }

    /**
     * Get remaining lockout duration for an account.
     *
     * @param email The account email
     * @return Remaining lock duration, or Duration.ZERO if not locked
     */
    public Duration getRemainingLockDuration(String email) {
        String normalizedEmail = email.toLowerCase().trim();
        Instant unlockTime = lockedAccounts.getIfPresent(normalizedEmail);

        if (unlockTime == null || Instant.now().isAfter(unlockTime)) {
            return Duration.ZERO;
        }

        return Duration.between(Instant.now(), unlockTime);
    }

    /**
     * Get the number of failed attempts for an account.
     *
     * @param email The account email
     * @return Number of failed attempts
     */
    public int getFailedAttempts(String email) {
        String normalizedEmail = email.toLowerCase().trim();
        Integer attempts = failedAttempts.getIfPresent(normalizedEmail);
        return attempts != null ? attempts : 0;
    }

    /**
     * Mask email for logging (privacy).
     */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 2) {
            return "***" + email.substring(atIndex);
        }
        return email.substring(0, 2) + "***" + email.substring(atIndex);
    }
}
