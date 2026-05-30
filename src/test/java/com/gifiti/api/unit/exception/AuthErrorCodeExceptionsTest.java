package com.gifiti.api.unit.exception;

import com.gifiti.api.exception.AccountLockedException;
import com.gifiti.api.exception.AlreadyVerifiedException;
import com.gifiti.api.exception.EmailAlreadyRegisteredException;
import com.gifiti.api.exception.EmailNotVerifiedException;
import com.gifiti.api.exception.ExpiredTokenException;
import com.gifiti.api.exception.InvalidCredentialsException;
import com.gifiti.api.exception.InvalidTokenException;
import com.gifiti.api.exception.LocalizedRuntimeException;
import com.gifiti.api.exception.WeakPasswordException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for feature 009 / T10 — the 8 dedicated auth-flow exception
 * subclasses per ADR 0009 § Decision C.
 *
 * <p>Each class mirrors the feature-008 {@code AccessCodeRequiredException}
 * template: a public {@code ERROR_CODE} constant + a public
 * {@code MESSAGE_KEY} constant + a {@code LocalizedRuntimeException} parent
 * constructed via {@code super(MESSAGE_KEY)}. For {@link InvalidTokenException}
 * and {@link ExpiredTokenException} — shared between {@code verify-email} and
 * {@code reset-password} — the constructor accepts the MessageSource key so
 * each call site retains its specific human copy.
 *
 * <p>Note: {@code super(MESSAGE_KEY)} routes through the @Deprecated legacy
 * literal-message constructor of {@link LocalizedRuntimeException}, so
 * {@code getMessage()} returns the key (and {@code getMessageKey()} returns
 * {@code null}). The 8 GlobalExceptionHandler handlers reference
 * {@code Class.MESSAGE_KEY} directly when calling {@code localize(...)} —
 * the same posture as the four feature-008 handlers.
 *
 * <p>Grep discipline: a single
 * {@code grep "ERROR_CODE" src/main/java/com/gifiti/api/exception/} returns
 * the complete discriminator inventory across features 008 + 009.
 */
class AuthErrorCodeExceptionsTest {

    @Test
    @DisplayName("InvalidCredentialsException carries INVALID_CREDENTIALS errorCode + key")
    void invalidCredentials_constants() {
        assertThat(InvalidCredentialsException.ERROR_CODE).isEqualTo("INVALID_CREDENTIALS");
        assertThat(InvalidCredentialsException.MESSAGE_KEY).isEqualTo("error.auth.credentials.invalid");

        InvalidCredentialsException ex = new InvalidCredentialsException();
        assertThat(ex).isInstanceOf(LocalizedRuntimeException.class);
        assertThat(ex.getMessage()).isEqualTo("error.auth.credentials.invalid");
    }

    @Test
    @DisplayName("EmailNotVerifiedException carries EMAIL_NOT_VERIFIED errorCode + key")
    void emailNotVerified_constants() {
        assertThat(EmailNotVerifiedException.ERROR_CODE).isEqualTo("EMAIL_NOT_VERIFIED");
        assertThat(EmailNotVerifiedException.MESSAGE_KEY).isEqualTo("error.auth.email.not.verified");

        EmailNotVerifiedException ex = new EmailNotVerifiedException();
        assertThat(ex).isInstanceOf(LocalizedRuntimeException.class);
        assertThat(ex.getMessage()).isEqualTo("error.auth.email.not.verified");
    }

    @Test
    @DisplayName("AccountLockedException carries ACCOUNT_LOCKED errorCode + key")
    void accountLocked_constants() {
        assertThat(AccountLockedException.ERROR_CODE).isEqualTo("ACCOUNT_LOCKED");
        assertThat(AccountLockedException.MESSAGE_KEY).isEqualTo("error.auth.account.locked");

        AccountLockedException ex = new AccountLockedException();
        assertThat(ex).isInstanceOf(LocalizedRuntimeException.class);
        assertThat(ex.getMessage()).isEqualTo("error.auth.account.locked");
    }

    @Test
    @DisplayName("EmailAlreadyRegisteredException carries EMAIL_ALREADY_REGISTERED errorCode + key")
    void emailAlreadyRegistered_constants() {
        assertThat(EmailAlreadyRegisteredException.ERROR_CODE).isEqualTo("EMAIL_ALREADY_REGISTERED");
        assertThat(EmailAlreadyRegisteredException.MESSAGE_KEY).isEqualTo("error.email.already.registered");

        EmailAlreadyRegisteredException ex = new EmailAlreadyRegisteredException();
        assertThat(ex).isInstanceOf(LocalizedRuntimeException.class);
        assertThat(ex.getMessage()).isEqualTo("error.email.already.registered");
    }

    @Test
    @DisplayName("WeakPasswordException carries WEAK_PASSWORD errorCode + key")
    void weakPassword_constants() {
        assertThat(WeakPasswordException.ERROR_CODE).isEqualTo("WEAK_PASSWORD");
        assertThat(WeakPasswordException.MESSAGE_KEY).isEqualTo("error.auth.password.weak");

        WeakPasswordException ex = new WeakPasswordException();
        assertThat(ex).isInstanceOf(LocalizedRuntimeException.class);
        assertThat(ex.getMessage()).isEqualTo("error.auth.password.weak");
    }

    @Test
    @DisplayName("InvalidTokenException carries INVALID_TOKEN errorCode and constructor-supplied key (verify-email site)")
    void invalidToken_verifyEmail_site() {
        assertThat(InvalidTokenException.ERROR_CODE).isEqualTo("INVALID_TOKEN");

        InvalidTokenException ex = new InvalidTokenException("error.auth.verification.token.invalid");
        assertThat(ex).isInstanceOf(LocalizedRuntimeException.class);
        // Constructor-arg key flows through super("..."): getMessage() returns it.
        assertThat(ex.getMessage()).isEqualTo("error.auth.verification.token.invalid");
    }

    @Test
    @DisplayName("InvalidTokenException accepts the reset-password key (constructor-arg flexibility)")
    void invalidToken_resetPassword_site() {
        InvalidTokenException ex = new InvalidTokenException("error.auth.password.reset.token.invalid");
        assertThat(ex.getMessage()).isEqualTo("error.auth.password.reset.token.invalid");
    }

    @Test
    @DisplayName("ExpiredTokenException carries EXPIRED_TOKEN errorCode and constructor-supplied key (verify-email site)")
    void expiredToken_verifyEmail_site() {
        assertThat(ExpiredTokenException.ERROR_CODE).isEqualTo("EXPIRED_TOKEN");

        ExpiredTokenException ex = new ExpiredTokenException("error.auth.verification.token.expired");
        assertThat(ex).isInstanceOf(LocalizedRuntimeException.class);
        assertThat(ex.getMessage()).isEqualTo("error.auth.verification.token.expired");
    }

    @Test
    @DisplayName("ExpiredTokenException accepts the reset-password key (constructor-arg flexibility)")
    void expiredToken_resetPassword_site() {
        ExpiredTokenException ex = new ExpiredTokenException("error.auth.password.reset.token.expired");
        assertThat(ex.getMessage()).isEqualTo("error.auth.password.reset.token.expired");
    }

    @Test
    @DisplayName("AlreadyVerifiedException carries ALREADY_VERIFIED errorCode + key (user Q2 = YES)")
    void alreadyVerified_constants() {
        assertThat(AlreadyVerifiedException.ERROR_CODE).isEqualTo("ALREADY_VERIFIED");
        assertThat(AlreadyVerifiedException.MESSAGE_KEY).isEqualTo("error.auth.already.verified");

        AlreadyVerifiedException ex = new AlreadyVerifiedException();
        assertThat(ex).isInstanceOf(LocalizedRuntimeException.class);
        assertThat(ex.getMessage()).isEqualTo("error.auth.already.verified");
    }
}
