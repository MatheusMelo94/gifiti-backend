package com.gifiti.api.exception;

/**
 * Thrown by {@code AuthService.login()} when
 * {@code AccountLockoutService.isLocked(email)} returns true (feature 009 /
 * T10 / T12). The lockout itself is unchanged business logic — 5 failed
 * attempts → 30-minute lock; this dedicated subclass only swaps the throw
 * type so the response carries the {@code ACCOUNT_LOCKED} discriminator.
 *
 * <p>Maps to HTTP 401 with {@code errorCode = "ACCOUNT_LOCKED"} via
 * {@link GlobalExceptionHandler}. Per ADR 0009 § Decision E this is a 1-line
 * refactor at the throw site; the existing {@code error.auth.account.locked}
 * MessageSource key continues to back the human-readable {@code message}
 * field.
 */
public class AccountLockedException extends LocalizedRuntimeException {

    /** MessageSource key for the localized response message. */
    public static final String MESSAGE_KEY = "error.auth.account.locked";

    /** Machine-readable discriminator placed on {@code ErrorResponse.errorCode}. */
    public static final String ERROR_CODE = "ACCOUNT_LOCKED";

    public AccountLockedException() {
        super(MESSAGE_KEY);
    }
}
