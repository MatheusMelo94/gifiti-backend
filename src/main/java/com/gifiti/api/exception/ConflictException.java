package com.gifiti.api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a conflict occurs (e.g., duplicate reservation).
 * Maps to HTTP 409 Conflict.
 *
 * <p>Extends {@link LocalizedRuntimeException}; localized at response time by
 * {@code GlobalExceptionHandler} via {@code MessageSource} (Task 5 of the i18n
 * backend feature).
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class ConflictException extends LocalizedRuntimeException {

    /**
     * Keyed constructor — preferred for new code.
     *
     * @param messageKey MessageSource key
     * @param args       optional positional args
     */
    public ConflictException(String messageKey, Object... args) {
        super(messageKey, args);
    }

    /**
     * Legacy literal-message constructor.
     *
     * @deprecated Use {@link #ConflictException(String, Object...)} with a
     *     MessageSource key. Retained until Task 10 migrates all call sites.
     * @param message literal English message
     */
    @Deprecated
    public ConflictException(String message) {
        super(message);
    }
}
