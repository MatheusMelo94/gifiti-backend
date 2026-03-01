package com.gifiti.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a URL uses only safe protocols (http/https).
 *
 * Security hardening (M-01):
 * - Prevents XSS via javascript: protocol
 * - Prevents data exfiltration via data: protocol
 * - Blocks dangerous protocols like vbscript:, file:
 * - Only allows http:// and https:// URLs
 *
 * Usage:
 * <pre>
 * {@code
 * @SafeUrl(message = "URL must use http or https protocol")
 * private String productLink;
 * }
 * </pre>
 */
@Documented
@Constraint(validatedBy = SafeUrlValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface SafeUrl {
    String message() default "URL must use http or https protocol";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
