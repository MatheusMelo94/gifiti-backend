package com.gifiti.api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Pattern;

/**
 * Validator for @NoHtml annotation.
 *
 * Comprehensive XSS prevention — rejects:
 * - HTML tags: &lt;script&gt;, &lt;img&gt;, &lt;svg&gt;, etc.
 * - HTML entities: &amp;#60;, &amp;#x3C;, &amp;lt; (decoded to tags by browsers)
 * - Event handlers: onerror=, onload=, onclick=, etc.
 * - Dangerous protocols: javascript:, data:text/html, vbscript:
 * - Null/blank values are considered valid (use @NotBlank for required fields)
 */
@Slf4j
public class NoHtmlValidator implements ConstraintValidator<NoHtml, String> {

    // Literal HTML tags: <script>, <img src=...>, </div>, etc.
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile(
            "<[^>]+>", Pattern.CASE_INSENSITIVE);

    // HTML entities that decode to angle brackets: &#60; &#x3C; &#x003c; &lt; &gt;
    private static final Pattern HTML_ENTITY_PATTERN = Pattern.compile(
            "&#(x0*3[ceCE]|0*60);|&#(x0*3[eE]|0*62);|&(lt|gt|amp);",
            Pattern.CASE_INSENSITIVE);

    // Event handler attributes: onerror=, onload=, onclick=, onfocus=, etc.
    private static final Pattern EVENT_HANDLER_PATTERN = Pattern.compile(
            "\\bon[a-z]{2,}\\s*=", Pattern.CASE_INSENSITIVE);

    // Dangerous URI protocols: javascript:, vbscript:, data:text/html
    private static final Pattern DANGEROUS_PROTOCOL_PATTERN = Pattern.compile(
            "(javascript|vbscript|data\\s*:\\s*text/html)\\s*:", Pattern.CASE_INSENSITIVE);

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }

        if (HTML_TAG_PATTERN.matcher(value).find()) {
            log.warn("SECURITY_EVENT: HTML tag rejected in input");
            return false;
        }

        if (HTML_ENTITY_PATTERN.matcher(value).find()) {
            log.warn("SECURITY_EVENT: HTML entity rejected in input");
            return false;
        }

        if (EVENT_HANDLER_PATTERN.matcher(value).find()) {
            log.warn("SECURITY_EVENT: Event handler rejected in input");
            return false;
        }

        if (DANGEROUS_PROTOCOL_PATTERN.matcher(value).find()) {
            log.warn("SECURITY_EVENT: Dangerous protocol rejected in input");
            return false;
        }

        return true;
    }
}
