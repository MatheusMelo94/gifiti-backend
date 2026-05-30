package com.gifiti.api.exception;

/**
 * Thrown when an {@code X-Wishlist-Access-Code} header is supplied but does
 * not match the stored code (feature 008 / T6 + T10).
 *
 * <p>Maps to HTTP 403 with {@code errorCode = "INVALID_ACCESS_CODE"} via
 * {@link GlobalExceptionHandler}. Per Security findings F-3 implementation
 * pins: malformed-format headers (failing the {@code ^\d{4}$} pre-check) also
 * raise this exception — identical outcome to wrong-but-conforming codes so
 * the constant-time compare always operates on same-length inputs.
 */
public class InvalidAccessCodeException extends LocalizedRuntimeException {

    /** MessageSource key for the localized response message (T12). */
    public static final String MESSAGE_KEY = "error.wishlist.access-code.invalid";

    /** Machine-readable discriminator placed on {@link com.gifiti.api.dto.response.ErrorResponse#errorCode}. */
    public static final String ERROR_CODE = "INVALID_ACCESS_CODE";

    public InvalidAccessCodeException() {
        super(MESSAGE_KEY);
    }
}
