package com.gifiti.api.exception;

/**
 * Thrown when a verification or password-reset token does not match any user
 * document (feature 009 / T10 / T12). Shared between
 * {@code AuthService.verifyEmail()} and {@code AuthService.resetPassword()};
 * the constructor takes the MessageSource key so each call site retains its
 * specific human copy.
 *
 * <p>Maps to HTTP 401 with {@code errorCode = "INVALID_TOKEN"} via
 * {@link GlobalExceptionHandler}. Per ADR 0009 § Decision C — one class per
 * discriminator, even when the discriminator spans multiple endpoints; the
 * grep-the-inventory contract still holds because both call sites share the
 * single ERROR_CODE.
 *
 * <p>Security: anti-enumeration discipline (feature 005, message bundle
 * comment) is preserved — the resolved {@code message} field stays vague
 * across "no such token", "token belonged to someone else", and "expired"
 * branches. The expiry case has its own dedicated subclass
 * ({@link ExpiredTokenException}) only because that branch is user-actionable
 * — both still avoid embedding user identity in the message.
 */
public class InvalidTokenException extends LocalizedRuntimeException {

    /** Machine-readable discriminator placed on {@code ErrorResponse.errorCode}. */
    public static final String ERROR_CODE = "INVALID_TOKEN";

    /**
     * @param messageKey MessageSource key for the localized response message;
     *                   either {@code error.auth.verification.token.invalid}
     *                   (verify-email site) or
     *                   {@code error.auth.password.reset.token.invalid}
     *                   (reset-password site).
     */
    public InvalidTokenException(String messageKey) {
        super(messageKey);
    }
}
