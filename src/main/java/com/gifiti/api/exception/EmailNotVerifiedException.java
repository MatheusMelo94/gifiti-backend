package com.gifiti.api.exception;

/**
 * Thrown by {@code AuthService.login()} when the credentials match but the
 * user has not yet verified their email (feature 009 / T10 / T14, user
 * ratification Q1 = YES on 2026-05-30).
 *
 * <p>Maps to HTTP 401 with {@code errorCode = "EMAIL_NOT_VERIFIED"} via
 * {@link GlobalExceptionHandler}. Per ADR 0009 § Decision C + Open Q1
 * resolution: 401 chosen (consistent with the other login-failure modes —
 * INVALID_CREDENTIALS, ACCOUNT_LOCKED — so all login failures share one HTTP
 * status; the discriminator carries the reason).
 *
 * <p>NEW BUSINESS BEHAVIOR per user Q1=YES: unverified users can no longer log
 * in. Existing unverified users will be blocked on next login attempt and must
 * complete email verification first.
 *
 * <p>Security note (plan §4, indirect touchpoint): this surface trivially
 * confirms email existence to a caller who already supplied the correct
 * password — i.e., the caller already knows the email exists. The register
 * path's {@code EMAIL_ALREADY_REGISTERED} is a strictly more informative
 * leak (anonymous probe via wrong password is allowed). No new enumeration
 * vector introduced.
 */
public class EmailNotVerifiedException extends LocalizedRuntimeException {

    /** MessageSource key for the localized response message (added in T9). */
    public static final String MESSAGE_KEY = "error.auth.email.not.verified";

    /** Machine-readable discriminator placed on {@code ErrorResponse.errorCode}. */
    public static final String ERROR_CODE = "EMAIL_NOT_VERIFIED";

    public EmailNotVerifiedException() {
        super(MESSAGE_KEY);
    }
}
