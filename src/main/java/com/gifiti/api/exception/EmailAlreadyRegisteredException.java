package com.gifiti.api.exception;

/**
 * Thrown by {@code AuthService.register()} when the supplied email matches an
 * existing User document (feature 009 / T10 / T12).
 *
 * <p>Maps to HTTP 409 with {@code errorCode = "EMAIL_ALREADY_REGISTERED"} via
 * {@link GlobalExceptionHandler}. Per ADR 0009 § Decision C this dedicated
 * subclass replaces the prior throw site that used the generic
 * {@link ConflictException}; the existing
 * {@code error.email.already.registered} MessageSource key continues to back
 * the human-readable {@code message} field.
 *
 * <p>Per ADR 0009 § Decision D (Shape A) the response shape is top-level
 * errorCode only — no {@code details[]} field-level entry, even though
 * {@code TAKEN} remains a reserved value in the {@code FieldError.errorCode}
 * inventory for future field-level uniqueness validators.
 */
public class EmailAlreadyRegisteredException extends LocalizedRuntimeException {

    /** MessageSource key for the localized response message. */
    public static final String MESSAGE_KEY = "error.email.already.registered";

    /** Machine-readable discriminator placed on {@code ErrorResponse.errorCode}. */
    public static final String ERROR_CODE = "EMAIL_ALREADY_REGISTERED";

    public EmailAlreadyRegisteredException() {
        super(MESSAGE_KEY);
    }
}
