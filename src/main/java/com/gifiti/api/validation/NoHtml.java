package com.gifiti.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a string does not contain HTML tags.
 *
 * Security fix (ZAP Stored XSS finding):
 * - Rejects input containing HTML tags like &lt;script&gt;, &lt;img&gt;, etc.
 * - Prevents stored XSS by blocking HTML at the API input boundary
 * - Null/blank values are considered valid (use @NotBlank for required fields)
 */
@Documented
@Constraint(validatedBy = NoHtmlValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface NoHtml {
    String message() default "HTML content is not allowed";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
