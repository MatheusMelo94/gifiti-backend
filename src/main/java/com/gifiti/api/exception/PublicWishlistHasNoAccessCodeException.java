package com.gifiti.api.exception;

/**
 * Thrown when a caller attempts to rotate the access code on a PUBLIC wishlist
 * (feature 008 / T11). PUBLIC wishlists have no access code by definition
 * (ADR 0008 § Decision E + § Decision F), so rotation is meaningless.
 *
 * <p>Maps to HTTP 400 with {@code errorCode = "PUBLIC_WISHLIST_HAS_NO_ACCESS_CODE"}
 * via {@link GlobalExceptionHandler}.
 */
public class PublicWishlistHasNoAccessCodeException extends LocalizedRuntimeException {

    /** MessageSource key for the localized response message (T12). */
    public static final String MESSAGE_KEY = "error.wishlist.access-code.rotate.public-visibility";

    /** Machine-readable discriminator placed on {@link com.gifiti.api.dto.response.ErrorResponse#errorCode}. */
    public static final String ERROR_CODE = "PUBLIC_WISHLIST_HAS_NO_ACCESS_CODE";

    public PublicWishlistHasNoAccessCodeException() {
        super(MESSAGE_KEY);
    }
}
