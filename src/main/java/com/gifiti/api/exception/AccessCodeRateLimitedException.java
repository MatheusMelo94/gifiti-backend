package com.gifiti.api.exception;

/**
 * Thrown when the per-(IP, shareableId) access-code rate-limit bucket is
 * exhausted (feature 008 / T9).
 *
 * <p>Maps to HTTP 429 with {@code errorCode = "ACCESS_CODE_RATE_LIMITED"} via
 * {@link GlobalExceptionHandler}. Per ADR 0008 § Decision G: bucket is
 * 5 tokens, refill 5 / 10 min, per-(IP, shareableId). Successful validation
 * resets the bucket; failed validation (incl. malformed-format header per
 * Security F-3 pin 3) consumes a token.
 */
public class AccessCodeRateLimitedException extends LocalizedRuntimeException {

    /** MessageSource key for the localized response message (T12). */
    public static final String MESSAGE_KEY = "error.wishlist.access-code.rate-limited";

    /** Machine-readable discriminator placed on {@link com.gifiti.api.dto.response.ErrorResponse#errorCode}. */
    public static final String ERROR_CODE = "ACCESS_CODE_RATE_LIMITED";

    public AccessCodeRateLimitedException() {
        super(MESSAGE_KEY);
    }
}
