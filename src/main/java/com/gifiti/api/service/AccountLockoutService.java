package com.gifiti.api.service;

import com.gifiti.api.model.AccountLockout;
import com.gifiti.api.repository.AccountLockoutRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Account lockout service to prevent brute force attacks.
 * Uses MongoDB for persistence — lockout state survives application restarts.
 *
 * Security hardening (H-02):
 * - Tracks failed login attempts per account
 * - Locks accounts after MAX_ATTEMPTS failed attempts
 * - Lock duration: LOCKOUT_DURATION minutes
 * - TTL index auto-cleans expired records from MongoDB
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountLockoutService {

    @Value("${app.account-lockout.enabled:true}")
    private boolean lockoutEnabled;

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(30);

    private final AccountLockoutRepository accountLockoutRepository;

    public void recordFailedAttempt(String email) {
        String normalizedEmail = email.toLowerCase().trim();
        AccountLockout lockout = accountLockoutRepository.findByEmail(normalizedEmail)
                .orElse(AccountLockout.builder()
                        .email(normalizedEmail)
                        .failedAttempts(0)
                        .expiresAt(Instant.now().plus(LOCKOUT_DURATION).plusSeconds(60))
                        .build());

        lockout.setFailedAttempts(lockout.getFailedAttempts() + 1);

        if (lockout.getFailedAttempts() >= MAX_ATTEMPTS) {
            Instant unlockTime = Instant.now().plus(LOCKOUT_DURATION);
            lockout.setLockedUntil(unlockTime);
            lockout.setExpiresAt(unlockTime.plusSeconds(60));
            log.warn("SECURITY_EVENT: Account locked due to {} failed attempts: {}",
                     lockout.getFailedAttempts(), maskEmail(normalizedEmail));
        }

        accountLockoutRepository.save(lockout);
        log.debug("Failed attempt {} for account: {}", lockout.getFailedAttempts(), maskEmail(normalizedEmail));
    }

    public void recordSuccessfulLogin(String email) {
        String normalizedEmail = email.toLowerCase().trim();
        accountLockoutRepository.findByEmail(normalizedEmail).ifPresent(lockout -> {
            if (lockout.getLockedUntil() == null || Instant.now().isAfter(lockout.getLockedUntil())) {
                accountLockoutRepository.deleteByEmail(normalizedEmail);
            } else {
                lockout.setFailedAttempts(0);
                accountLockoutRepository.save(lockout);
            }
        });
        log.debug("Cleared failed attempts for account: {}", maskEmail(normalizedEmail));
    }

    public boolean isLocked(String email) {
        if (!lockoutEnabled) {
            return false;
        }
        String normalizedEmail = email.toLowerCase().trim();
        return accountLockoutRepository.findByEmail(normalizedEmail)
                .map(lockout -> {
                    if (lockout.getLockedUntil() == null) return false;
                    if (Instant.now().isAfter(lockout.getLockedUntil())) {
                        accountLockoutRepository.deleteByEmail(normalizedEmail);
                        return false;
                    }
                    return true;
                })
                .orElse(false);
    }

    public Duration getRemainingLockDuration(String email) {
        String normalizedEmail = email.toLowerCase().trim();
        return accountLockoutRepository.findByEmail(normalizedEmail)
                .filter(l -> l.getLockedUntil() != null && Instant.now().isBefore(l.getLockedUntil()))
                .map(l -> Duration.between(Instant.now(), l.getLockedUntil()))
                .orElse(Duration.ZERO);
    }

    public int getFailedAttempts(String email) {
        String normalizedEmail = email.toLowerCase().trim();
        return accountLockoutRepository.findByEmail(normalizedEmail)
                .map(AccountLockout::getFailedAttempts)
                .orElse(0);
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        int atIndex = email.indexOf('@');
        if (atIndex <= 2) return "***" + email.substring(atIndex);
        return email.substring(0, 2) + "***" + email.substring(atIndex);
    }
}
