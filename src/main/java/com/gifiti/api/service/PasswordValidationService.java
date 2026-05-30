package com.gifiti.api.service;

import com.gifiti.api.exception.WeakPasswordException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Enhanced password validation service.
 *
 * Security hardening (M-05):
 * - Checks against common password patterns
 * - Validates password doesn't contain email username
 * - Checks for sequential characters
 * - Works in addition to regex validation in RegisterRequest
 *
 * Calibration telemetry (Move 2 / Diagnosis C, 2026-05-06):
 * - On every rejection, emits a single INFO log line of the shape
 *   {@code password_validation_rejected rule=<name> correlation_id=<id>}.
 * - Goal: learn over the next week of production traffic which rules fire
 *   most often on real users so the cofounder can decide whether the
 *   "common_pattern" rule is overcalibrated. The rules themselves are
 *   unchanged.
 * - Privacy: no password content (literal, length, character class, hash) is
 *   ever logged. The user email is never logged. {@code correlationId} from
 *   {@link MDC} is the only join key back to the originating request, per
 *   {@code architecture-conventions.md § Logging} and LGPD Art. 6 IX
 *   (data minimization).
 */
@Slf4j
@Service
public class PasswordValidationService {

    /**
     * MDC key set by {@code CorrelationIdFilter} for the request lifecycle.
     */
    private static final String CORRELATION_ID_MDC_KEY = "correlationId";

    /** Telemetry rule name: input matched a known common-pattern substring. */
    static final String RULE_COMMON_PATTERN = "common_pattern";
    /** Telemetry rule name: input contained the user's email-username prefix. */
    static final String RULE_EMAIL_USERNAME_MATCH = "email_username_match";
    /** Telemetry rule name: input contained 4+ sequential/identical chars. */
    static final String RULE_SEQUENTIAL_CHARS = "sequential_chars";
    /** Telemetry rule name: input contained a repeated 2-4 char pattern. */
    static final String RULE_REPEATED_PATTERN = "repeated_pattern";

    /**
     * Common password patterns to reject.
     * Case-insensitive matching.
     */
    private static final Set<String> COMMON_PATTERNS = Set.of(
            "password", "qwerty", "admin", "letmein", "welcome",
            "monkey", "dragon", "master", "login", "princess",
            "sunshine", "shadow", "passw0rd", "123456", "654321",
            "abc123", "iloveyou", "trustno1", "baseball", "football",
            "batman", "superman", "michael", "ashley", "jennifer",
            "gifiti", "wishlist"  // App-specific patterns
    );

    /**
     * Validate password strength beyond regex requirements.
     *
     * @param password The password to validate
     * @param email The user's email (to check similarity)
     * @throws WeakPasswordException if password is weak (feature 009 / T13 —
     *         per ADR 0009 § Decision C; previously {@link IllegalArgumentException}
     *         with rule-specific literal English strings, but the new dedicated
     *         exception carries the {@code WEAK_PASSWORD} discriminator and
     *         the generic {@code error.auth.password.weak} MessageSource key
     *         per Decision G — per-rule diagnostics remain in the INFO
     *         telemetry below, NOT in the user-facing surface).
     */
    public void validate(String password, String email) {
        String lowerPassword = password.toLowerCase();

        // Check against common password patterns
        for (String pattern : COMMON_PATTERNS) {
            if (lowerPassword.contains(pattern)) {
                log.warn("SECURITY_EVENT: Weak password rejected - contains common pattern");
                emitRejectionTelemetry(RULE_COMMON_PATTERN);
                // Feature 009 / T13 — generic WEAK_PASSWORD discriminator
                // (Decision G). Per-rule diagnostics stay in the INFO telemetry
                // line above (feature 007 calibration channel).
                throw new WeakPasswordException();
            }
        }

        // Check email username similarity
        if (email != null && email.contains("@")) {
            String emailPrefix = email.split("@")[0].toLowerCase();
            if (emailPrefix.length() >= 3 && lowerPassword.contains(emailPrefix)) {
                log.warn("SECURITY_EVENT: Weak password rejected - contains email username");
                emitRejectionTelemetry(RULE_EMAIL_USERNAME_MATCH);
                throw new WeakPasswordException();
            }
        }

        // Check for sequential characters (4 or more)
        if (hasSequentialChars(lowerPassword, 4)) {
            log.warn("SECURITY_EVENT: Weak password rejected - contains sequential characters");
            emitRejectionTelemetry(RULE_SEQUENTIAL_CHARS);
            throw new WeakPasswordException();
        }

        // Check for repeated patterns
        if (hasRepeatedPattern(lowerPassword)) {
            log.warn("SECURITY_EVENT: Weak password rejected - contains repeated pattern");
            emitRejectionTelemetry(RULE_REPEATED_PATTERN);
            throw new WeakPasswordException();
        }

        log.debug("Password validation passed");
    }

    /**
     * Emit a single INFO-level calibration telemetry line for a rejection.
     *
     * <p>Format: {@code password_validation_rejected rule=<name>
     * correlation_id=<id>}. The {@code correlationId} is read from
     * {@link MDC}; if absent (e.g., a rejection invoked outside a request
     * lifecycle, such as a programmatic batch), it falls back to the literal
     * {@code "none"} so the line is still parseable.</p>
     *
     * <p>STRICT privacy contract: this method receives only the rule name —
     * never the password, never the email, never any derivative of either —
     * to make it physically impossible for callers to leak input through
     * this telemetry surface.</p>
     */
    private void emitRejectionTelemetry(String ruleName) {
        String correlationId = MDC.get(CORRELATION_ID_MDC_KEY);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = "none";
        }
        log.info("password_validation_rejected rule={} correlation_id={}", ruleName, correlationId);
    }

    /**
     * Check if password contains sequential characters.
     *
     * @param password Lowercase password
     * @param length Number of sequential chars to detect
     * @return true if sequential chars found
     */
    private boolean hasSequentialChars(String password, int length) {
        if (password.length() < length) {
            return false;
        }

        for (int i = 0; i <= password.length() - length; i++) {
            // Check for repeated same character (aaaa)
            boolean sameChar = true;
            char first = password.charAt(i);
            for (int j = 1; j < length; j++) {
                if (password.charAt(i + j) != first) {
                    sameChar = false;
                    break;
                }
            }
            if (sameChar) {
                return true;
            }

            // Check for ascending sequence (abcd, 1234)
            boolean ascending = true;
            for (int j = 1; j < length; j++) {
                if (password.charAt(i + j) != password.charAt(i + j - 1) + 1) {
                    ascending = false;
                    break;
                }
            }
            if (ascending) {
                return true;
            }

            // Check for descending sequence (dcba, 4321)
            boolean descending = true;
            for (int j = 1; j < length; j++) {
                if (password.charAt(i + j) != password.charAt(i + j - 1) - 1) {
                    descending = false;
                    break;
                }
            }
            if (descending) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if password contains a repeated pattern.
     * E.g., "abcabc" or "1212"
     *
     * @param password Lowercase password
     * @return true if repeated pattern found
     */
    private boolean hasRepeatedPattern(String password) {
        int len = password.length();

        // Check for patterns of length 2-4 that repeat
        for (int patternLen = 2; patternLen <= 4; patternLen++) {
            if (len < patternLen * 2) {
                continue;
            }

            for (int i = 0; i <= len - patternLen * 2; i++) {
                String pattern = password.substring(i, i + patternLen);
                String next = password.substring(i + patternLen, i + patternLen * 2);
                if (pattern.equals(next)) {
                    return true;
                }
            }
        }

        return false;
    }
}
