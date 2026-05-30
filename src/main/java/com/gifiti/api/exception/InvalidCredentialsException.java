package com.gifiti.api.exception;

/**
 * Thrown by {@code AuthService.login()} when either the email is unknown OR
 * the password fails to match (feature 009 / T10 / T12).
 *
 * <p>Maps to HTTP 401 with {@code errorCode = "INVALID_CREDENTIALS"} via
 * {@link GlobalExceptionHandler}. Per ADR 0009 § Decision C this dedicated
 * subclass replaces the prior throw site that used the generic
 * {@link UnauthorizedException}; the existing
 * {@code error.auth.credentials.invalid} MessageSource key continues to back
 * the human-readable {@code message} field (anti-enumeration discipline from
 * feature 005 preserved — same vague copy for "unknown email" vs "wrong
 * password" so the discriminator does NOT split those branches).
 */
public class InvalidCredentialsException extends LocalizedRuntimeException {

    /** MessageSource key for the localized response message. */
    public static final String MESSAGE_KEY = "error.auth.credentials.invalid";

    /** Machine-readable discriminator placed on {@code ErrorResponse.errorCode}. */
    public static final String ERROR_CODE = "INVALID_CREDENTIALS";

    public InvalidCredentialsException() {
        super(MESSAGE_KEY);
    }
}
