package com.gifiti.api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a user lacks permission for an operation.
 * Maps to HTTP 403 Forbidden.
 *
 * <p>Extends {@link LocalizedRuntimeException}; localized at response time by
 * {@code GlobalExceptionHandler} via {@code MessageSource} (Task 5 of the i18n
 * backend feature).
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class AccessDeniedException extends LocalizedRuntimeException {

    /**
     * Keyed constructor — preferred for new code.
     *
     * @param messageKey MessageSource key
     * @param args       optional positional args
     */
    public AccessDeniedException(String messageKey, Object... args) {
        super(messageKey, args);
    }

    /**
     * Legacy literal-message constructor.
     *
     * @deprecated Use {@link #AccessDeniedException(String, Object...)} with a
     *     MessageSource key. Retained until Task 10 migrates all call sites.
     * @param message literal English message
     */
    @Deprecated
    public AccessDeniedException(String message) {
        super(message);
    }

    /**
     * Legacy no-arg constructor producing the default literal "Access denied".
     *
     * @deprecated Use {@link #AccessDeniedException(String, Object...)} with a
     *     MessageSource key. Retained until Task 10 migrates all call sites.
     */
    @Deprecated
    public AccessDeniedException() {
        super("Access denied");
    }
}
