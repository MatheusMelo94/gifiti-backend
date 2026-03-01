package com.gifiti.api.service;

import lombok.extern.slf4j.Slf4j;
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
 */
@Slf4j
@Service
public class PasswordValidationService {

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
     * @throws IllegalArgumentException if password is weak
     */
    public void validate(String password, String email) {
        String lowerPassword = password.toLowerCase();

        // Check against common password patterns
        for (String pattern : COMMON_PATTERNS) {
            if (lowerPassword.contains(pattern)) {
                log.warn("SECURITY_EVENT: Weak password rejected - contains common pattern");
                throw new IllegalArgumentException(
                    "Password contains a common pattern. Please choose a stronger password."
                );
            }
        }

        // Check email username similarity
        if (email != null && email.contains("@")) {
            String emailPrefix = email.split("@")[0].toLowerCase();
            if (emailPrefix.length() >= 3 && lowerPassword.contains(emailPrefix)) {
                log.warn("SECURITY_EVENT: Weak password rejected - contains email username");
                throw new IllegalArgumentException(
                    "Password must not contain your email username."
                );
            }
        }

        // Check for sequential characters (4 or more)
        if (hasSequentialChars(lowerPassword, 4)) {
            log.warn("SECURITY_EVENT: Weak password rejected - contains sequential characters");
            throw new IllegalArgumentException(
                "Password must not contain sequential characters (e.g., 'aaaa', '1234', 'abcd')."
            );
        }

        // Check for repeated patterns
        if (hasRepeatedPattern(lowerPassword)) {
            log.warn("SECURITY_EVENT: Weak password rejected - contains repeated pattern");
            throw new IllegalArgumentException(
                "Password must not contain repeated patterns."
            );
        }

        log.debug("Password validation passed");
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
