package com.gifiti.api.exception;

/**
 * Thrown by {@code AuthService.verifyEmail()} when the supplied verification
 * token matches a User that has already verified their email — i.e., a
 * re-clicked verification link (feature 009 / T10 / T15, user ratification
 * Q2 = YES on 2026-05-30; ADR 0009 Open Q2 resolution = shape (a)).
 *
 * <p>Maps to HTTP 409 with {@code errorCode = "ALREADY_VERIFIED"} via
 * {@link GlobalExceptionHandler}. Per ADR 0009 § Decision C this dedicated
 * subclass replaces the prior generic-401-INVALID_TOKEN outcome a re-clicked
 * link used to produce.
 *
 * <p>NEW BUSINESS BEHAVIOR per user Q2 = YES: the verification token hash is
 * NO LONGER nulled on successful verification. It remains on the User
 * document so that a second verify request can be distinguished from
 * "no such token" / "wrong token". Privacy posture per ADR 0009 Decision E
 * reasoning: the hashed token has zero value post-verification (it is a
 * single-use marker, not a credential); LGPD Art. 6 IX data-minimization
 * remains satisfied — no new personal data is collected, only the existing
 * single-use marker is not actively cleared.
 */
public class AlreadyVerifiedException extends LocalizedRuntimeException {

    /** MessageSource key for the localized response message (added in T9). */
    public static final String MESSAGE_KEY = "error.auth.already.verified";

    /** Machine-readable discriminator placed on {@code ErrorResponse.errorCode}. */
    public static final String ERROR_CODE = "ALREADY_VERIFIED";

    public AlreadyVerifiedException() {
        super(MESSAGE_KEY);
    }
}
