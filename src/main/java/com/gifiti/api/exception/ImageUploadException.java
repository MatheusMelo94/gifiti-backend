package com.gifiti.api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an image upload fails due to validation or storage errors.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ImageUploadException extends RuntimeException {

    public ImageUploadException(String message) {
        super(message);
    }

    public ImageUploadException(String message, Throwable cause) {
        super(message, cause);
    }
}
