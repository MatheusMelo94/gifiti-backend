package com.gifiti.api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an image upload fails due to validation or storage errors.
 * Maps to HTTP 400 Bad Request.
 *
 * <p>Extends {@link LocalizedRuntimeException}; localized at response time by
 * {@code GlobalExceptionHandler} via {@code MessageSource} (Task 5 of the i18n
 * backend feature).
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ImageUploadException extends LocalizedRuntimeException {

    /**
     * Keyed constructor — preferred for new code.
     *
     * @param messageKey MessageSource key
     * @param args       optional positional args
     */
    public ImageUploadException(String messageKey, Object... args) {
        super(messageKey, args);
    }

    /**
     * Keyed constructor with cause — preferred for new code that wraps an
     * underlying I/O or SDK exception (e.g. R2 upload failures).
     *
     * @param messageKey MessageSource key
     * @param args       positional args; {@code null} normalized to empty array
     * @param cause      the underlying throwable, may be {@code null}
     */
    public ImageUploadException(String messageKey, Object[] args, Throwable cause) {
        super(messageKey, args, cause);
    }

    /**
     * Legacy literal-message constructor.
     *
     * @deprecated Use {@link #ImageUploadException(String, Object...)} with a
     *     MessageSource key. Retained until Task 10 migrates all call sites.
     * @param message literal English message
     */
    @Deprecated
    public ImageUploadException(String message) {
        super(message);
    }

    /**
     * Legacy literal-message + cause constructor.
     *
     * @deprecated Use {@link #ImageUploadException(String, Object[], Throwable)}
     *     with a MessageSource key. Retained until Task 10 migrates all call sites.
     * @param message literal English message
     * @param cause   the underlying throwable
     */
    @Deprecated
    public ImageUploadException(String message, Throwable cause) {
        super(message, cause);
    }
}
