package com.gifiti.api.exception;

/**
 * Thrown when a verification or password-reset token is matched against a user
 * but its expiry timestamp has passed (feature 009 / T10 / T12). Shared
 * between {@code AuthService.verifyEmail()} and
 * {@code AuthService.resetPassword()}; the constructor takes the
 * MessageSource key so each call site retains its specific human copy.
 *
 * <p>Maps to HTTP 401 with {@code errorCode = "EXPIRED_TOKEN"} via
 * {@link GlobalExceptionHandler}. Per ADR 0009 § Decision C — distinct
 * discriminator from {@link InvalidTokenException} because the expired branch
 * is legitimately user-actionable ("request a new link") while the
 * "no-such-token" branch is not (anti-enumeration).
 */
public class ExpiredTokenException extends LocalizedRuntimeException {

    /** Machine-readable discriminator placed on {@code ErrorResponse.errorCode}. */
    public static final String ERROR_CODE = "EXPIRED_TOKEN";

    /**
     * @param messageKey MessageSource key for the localized response message;
     *                   either {@code error.auth.verification.token.expired}
     *                   (verify-email site) or
     *                   {@code error.auth.password.reset.token.expired}
     *                   (reset-password site).
     */
    public ExpiredTokenException(String messageKey) {
        super(messageKey);
    }
}
