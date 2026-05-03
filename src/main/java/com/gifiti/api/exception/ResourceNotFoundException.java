package com.gifiti.api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a requested resource is not found.
 * Maps to HTTP 404 Not Found.
 *
 * <p>Extends {@link LocalizedRuntimeException} so the message is resolved by
 * {@code GlobalExceptionHandler} via {@code MessageSource} at response time
 * (Task 5 of the i18n backend feature).
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends LocalizedRuntimeException {

    /**
     * MessageSource key used by the legacy 3-arg structured constructor.
     * Bundle entry added in Task 4; populated for both locales in Task 5.
     */
    public static final String KEY_NOT_FOUND_WITH_FIELD = "error.resource.not.found.with.field";

    /**
     * Keyed constructor — preferred for new code.
     *
     * @param messageKey MessageSource key (e.g. {@code "error.user.not.found"})
     * @param args       optional positional args for the message template
     */
    public ResourceNotFoundException(String messageKey, Object... args) {
        super(messageKey, args);
    }

    /**
     * Legacy literal-message constructor.
     *
     * @deprecated Use {@link #ResourceNotFoundException(String, Object...)} with a
     *     MessageSource key. Retained until Task 10 migrates all call sites.
     * @param message literal English message
     */
    @Deprecated
    public ResourceNotFoundException(String message) {
        super(message);
    }

    /**
     * Legacy structured constructor — delegates to the keyed form using
     * {@link #KEY_NOT_FOUND_WITH_FIELD}, so existing call sites continue to
     * compile while new code can use the keyed path directly.
     *
     * @deprecated Use {@link #ResourceNotFoundException(String, Object...)} with a
     *     specific MessageSource key. Retained until Task 10 migrates all call sites.
     * @param resourceName e.g. {@code "User"}, {@code "Wishlist"}
     * @param fieldName    e.g. {@code "id"}, {@code "email"}
     * @param fieldValue   the offending value
     */
    @Deprecated
    public ResourceNotFoundException(String resourceName, String fieldName, String fieldValue) {
        super(KEY_NOT_FOUND_WITH_FIELD, resourceName, fieldName, fieldValue);
    }
}
