package com.gifiti.api.exception;

/**
 * Thrown by {@code PasswordValidationService.validate()} when the supplied
 * password fails any of the 4 enhanced-strength rules (common pattern,
 * email-username match, sequential chars, repeated pattern) — feature 009 /
 * T10 / T13.
 *
 * <p>Maps to HTTP 400 with {@code errorCode = "WEAK_PASSWORD"} via
 * {@link GlobalExceptionHandler}. Per ADR 0009 § Decision G the discriminator
 * is GENERIC (single code, no per-rule variants like
 * {@code WEAK_PASSWORD_COMMON_PATTERN}). The 4 rule names remain internal to
 * the feature-007 INFO calibration telemetry —
 * {@code password_validation_rejected rule=&lt;name&gt; correlation_id=&lt;id&gt;} —
 * which is the right channel for the rule-overcalibration question, NOT the
 * user-facing surface.
 *
 * <p>The previous throw type was {@link IllegalArgumentException} with a
 * literal English string per rule; that path resolved via the generic
 * {@code error.invalid.parameter} MessageSource key. The refactor at T13
 * preserves the per-rule INFO telemetry but switches the surfaced
 * human-readable message to the dedicated {@code error.auth.password.weak}
 * key (T9).
 */
public class WeakPasswordException extends LocalizedRuntimeException {

    /** MessageSource key for the localized response message (added in T9). */
    public static final String MESSAGE_KEY = "error.auth.password.weak";

    /** Machine-readable discriminator placed on {@code ErrorResponse.errorCode}. */
    public static final String ERROR_CODE = "WEAK_PASSWORD";

    public WeakPasswordException() {
        super(MESSAGE_KEY);
    }
}
