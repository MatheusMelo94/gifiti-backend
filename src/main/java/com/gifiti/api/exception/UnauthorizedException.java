package com.gifiti.api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when authentication fails.
 * Maps to HTTP 401 Unauthorized.
 *
 * <p>Extends {@link LocalizedRuntimeException}; localized at response time by
 * {@code GlobalExceptionHandler} via {@code MessageSource} (Task 5 of the i18n
 * backend feature).
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class UnauthorizedException extends LocalizedRuntimeException {

    /**
     * Keyed constructor — preferred for new code.
     *
     * @param messageKey MessageSource key
     * @param args       optional positional args
     */
    public UnauthorizedException(String messageKey, Object... args) {
        super(messageKey, args);
    }

    /**
     * Legacy literal-message constructor.
     *
     * @deprecated Use {@link #UnauthorizedException(String, Object...)} with a
     *     MessageSource key. Retained until Task 10 migrates all call sites.
     * @param message literal English message
     */
    @Deprecated
    public UnauthorizedException(String message) {
        super(message);
    }

    /**
     * Legacy no-arg constructor producing the default literal "Authentication required".
     *
     * @deprecated Use {@link #UnauthorizedException(String, Object...)} with a
     *     MessageSource key. Retained until Task 10 migrates all call sites.
     */
    @Deprecated
    public UnauthorizedException() {
        super("Authentication required");
    }
}
