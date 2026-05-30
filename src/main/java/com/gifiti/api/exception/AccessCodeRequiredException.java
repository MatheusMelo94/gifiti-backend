package com.gifiti.api.exception;

/**
 * Thrown when a PRIVATE wishlist is requested without an
 * {@code X-Wishlist-Access-Code} header (feature 008 / T6).
 *
 * <p>Maps to HTTP 403 with {@code errorCode = "ACCESS_CODE_REQUIRED"} via
 * {@link GlobalExceptionHandler}. Per ADR 0008 § 3 (privacy-posture inversion):
 * feature 008 deliberately surfaces existence via 403 (rather than feature
 * 006's 404) to power the gate UX. Security findings F-1 concur.
 */
public class AccessCodeRequiredException extends LocalizedRuntimeException {

    /** MessageSource key for the localized response message (T12). */
    public static final String MESSAGE_KEY = "error.wishlist.access-code.required";

    /** Machine-readable discriminator placed on {@link com.gifiti.api.dto.response.ErrorResponse#errorCode}. */
    public static final String ERROR_CODE = "ACCESS_CODE_REQUIRED";

    public AccessCodeRequiredException() {
        super(MESSAGE_KEY);
    }
}
