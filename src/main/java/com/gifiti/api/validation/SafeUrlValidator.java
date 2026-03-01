package com.gifiti.api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Set;

/**
 * Validator for @SafeUrl annotation.
 *
 * Security hardening (M-01):
 * - Only allows http and https protocols
 * - Validates URL format using java.net.URL
 * - Logs security events for blocked dangerous URLs
 * - Null/blank values are considered valid (use @NotBlank for required)
 */
@Slf4j
public class SafeUrlValidator implements ConstraintValidator<SafeUrl, String> {

    /**
     * Allowed URL protocols (case-insensitive).
     */
    private static final Set<String> ALLOWED_PROTOCOLS = Set.of("http", "https");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Null/blank values are valid (use @NotBlank for required fields)
        if (value == null || value.isBlank()) {
            return true;
        }

        try {
            URL url = new URL(value);
            String protocol = url.getProtocol().toLowerCase();

            if (!ALLOWED_PROTOCOLS.contains(protocol)) {
                log.warn("SECURITY_EVENT: Blocked URL with dangerous protocol: {}", protocol);
                return false;
            }

            return true;

        } catch (MalformedURLException e) {
            log.debug("Invalid URL format rejected: {}", e.getMessage());
            return false;
        }
    }
}
