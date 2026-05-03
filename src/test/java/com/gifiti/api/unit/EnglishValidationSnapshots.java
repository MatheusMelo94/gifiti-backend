package com.gifiti.api.unit;

/**
 * Canonical English text for every Jakarta Validation message attached to a
 * request DTO at the start of Task 6 of {@code 005-i18n-backend-support}.
 *
 * <p>This fixture pins the byte-for-byte English copy that existed inline as
 * {@code @Annotation(message = "...")} on each DTO before the i18n refactor.
 * Spec criterion #20 forbids any English drift during the migration —
 * localization is a delivery mechanism, not a content rewrite. Refactor
 * snapshot tests assert each violation message equals the constant here, so
 * any accidental wording change in {@code messages.properties} (or anywhere
 * else) trips a red test on the next run.</p>
 *
 * <p>Constants live as {@code public static final String} so the values are
 * visible in stack traces and IDE tooling without ceremony. Do not edit these
 * values to "improve" the wording — replace via a separate, explicitly-scoped
 * commit if/when the user authorizes a copy update.</p>
 */
final class EnglishValidationSnapshots {

    private EnglishValidationSnapshots() {
        // utility class
    }

    // Shared messages reused across multiple DTOs.
    static final String EMAIL_REQUIRED = "Email is required";
    static final String EMAIL_INVALID = "Email must be valid";
    static final String PASSWORD_REQUIRED = "Password is required";
    static final String PASSWORD_SIZE = "Password must be 12-128 characters";
    static final String PASSWORD_PATTERN =
            "Password must contain uppercase, lowercase, digit, and special character (@$!%*?&._#^()-+=)";
    static final String DISPLAYNAME_SIZE = "Display name must not exceed 50 characters";
    static final String TOKEN_REQUIRED = "Token is required";

    // RegisterRequest
    static final String REGISTER_EMAIL_SIZE = "Email must not exceed 254 characters";

    // CreateWishlistRequest / UpdateWishlistRequest
    static final String WISHLIST_TITLE_REQUIRED = "Title is required";
    static final String WISHLIST_TITLE_SIZE = "Title must not exceed 100 characters";
    static final String WISHLIST_DESCRIPTION_SIZE = "Description must not exceed 500 characters";

    // CreateItemRequest / UpdateItemRequest
    static final String ITEM_NAME_REQUIRED = "Name is required";
    static final String ITEM_NAME_SIZE = "Name must not exceed 200 characters";
    static final String ITEM_DESCRIPTION_SIZE = "Description must not exceed 1000 characters";
    static final String ITEM_PRODUCT_LINK_SAFEURL = "Product link must be a valid URL (http/https only)";
    static final String ITEM_IMAGE_URL_SAFEURL = "Image URL must be a valid URL (http/https only)";
    static final String ITEM_PRICE_POSITIVE = "Price must be positive";
    static final String ITEM_QUANTITY_POSITIVE = "Quantity must be positive";

    // GoogleLoginRequest
    static final String GOOGLE_ID_TOKEN_REQUIRED = "ID token is required";

    // RefreshTokenRequest
    static final String REFRESH_TOKEN_REQUIRED = "Refresh token is required";
}
