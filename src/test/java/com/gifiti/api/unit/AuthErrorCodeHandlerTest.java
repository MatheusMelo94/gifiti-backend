package com.gifiti.api.unit;

import com.gifiti.api.dto.response.ErrorResponse;
import com.gifiti.api.exception.AccountLockedException;
import com.gifiti.api.exception.AlreadyVerifiedException;
import com.gifiti.api.exception.EmailAlreadyRegisteredException;
import com.gifiti.api.exception.EmailNotVerifiedException;
import com.gifiti.api.exception.ExpiredTokenException;
import com.gifiti.api.exception.GlobalExceptionHandler;
import com.gifiti.api.exception.InvalidCredentialsException;
import com.gifiti.api.exception.InvalidTokenException;
import com.gifiti.api.exception.WeakPasswordException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Feature 009 / T11 unit tests — pins each of the 8 new
 * {@code @ExceptionHandler} methods on {@link GlobalExceptionHandler} to its
 * expected (HTTP status, errorCode, localized-message) tuple per ADR 0009
 * § Decision C and the plan §3.1 endpoint-to-errorCode mapping.
 *
 * <p>MessageSource is mocked to return {@code "localized:<key>"} so the test
 * asserts that the handler routed through the i18n layer with the correct key,
 * without depending on actual bundle contents.
 *
 * <p>This is the unit-level coverage; end-to-end coverage (request → 4xx →
 * full JSON shape via real MessageSource) lives in
 * {@code LoginErrorCodeIntegrationTest},
 * {@code RegisterErrorCodeIntegrationTest},
 * {@code VerifyEmailErrorCodeIntegrationTest}, and
 * {@code ResetPasswordErrorCodeIntegrationTest} (T12 / T14 / T15).
 */
class AuthErrorCodeHandlerTest {

    private GlobalExceptionHandler handler;
    private MessageSource messageSource;
    private HttpServletRequest request;

    @BeforeEach
    void setup() {
        messageSource = Mockito.mock(MessageSource.class);
        when(messageSource.getMessage(any(String.class), any(), any(Locale.class)))
                .thenAnswer(inv -> "localized:" + inv.getArgument(0));
        handler = new GlobalExceptionHandler(messageSource);
        request = Mockito.mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/auth/test");
    }

    @Test
    @DisplayName("InvalidCredentialsException → 401, INVALID_CREDENTIALS, localized credentials message")
    void handle_invalidCredentials() {
        ResponseEntity<ErrorResponse> response = handler.handleInvalidCredentials(
                new InvalidCredentialsException(), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(401);
        assertThat(body.getErrorCode()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(body.getMessage()).isEqualTo("localized:error.auth.credentials.invalid");
    }

    @Test
    @DisplayName("EmailNotVerifiedException → 401, EMAIL_NOT_VERIFIED (user Q1 = YES)")
    void handle_emailNotVerified() {
        ResponseEntity<ErrorResponse> response = handler.handleEmailNotVerified(
                new EmailNotVerifiedException(), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        ErrorResponse body = response.getBody();
        assertThat(body.getErrorCode()).isEqualTo("EMAIL_NOT_VERIFIED");
        assertThat(body.getMessage()).isEqualTo("localized:error.auth.email.not.verified");
    }

    @Test
    @DisplayName("AccountLockedException → 401, ACCOUNT_LOCKED")
    void handle_accountLocked() {
        ResponseEntity<ErrorResponse> response = handler.handleAccountLocked(
                new AccountLockedException(), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        ErrorResponse body = response.getBody();
        assertThat(body.getErrorCode()).isEqualTo("ACCOUNT_LOCKED");
        assertThat(body.getMessage()).isEqualTo("localized:error.auth.account.locked");
    }

    @Test
    @DisplayName("EmailAlreadyRegisteredException → 409, EMAIL_ALREADY_REGISTERED")
    void handle_emailAlreadyRegistered() {
        ResponseEntity<ErrorResponse> response = handler.handleEmailAlreadyRegistered(
                new EmailAlreadyRegisteredException(), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ErrorResponse body = response.getBody();
        assertThat(body.getErrorCode()).isEqualTo("EMAIL_ALREADY_REGISTERED");
        assertThat(body.getMessage()).isEqualTo("localized:error.email.already.registered");
    }

    @Test
    @DisplayName("WeakPasswordException → 400, WEAK_PASSWORD (generic per Decision G)")
    void handle_weakPassword() {
        ResponseEntity<ErrorResponse> response = handler.handleWeakPassword(
                new WeakPasswordException(), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ErrorResponse body = response.getBody();
        assertThat(body.getErrorCode()).isEqualTo("WEAK_PASSWORD");
        assertThat(body.getMessage()).isEqualTo("localized:error.auth.password.weak");
    }

    @Test
    @DisplayName("InvalidTokenException (verify-email site) → 401, INVALID_TOKEN, verification-key message")
    void handle_invalidToken_verifyEmail() {
        InvalidTokenException ex = new InvalidTokenException(
                "error.auth.verification.token.invalid");
        ResponseEntity<ErrorResponse> response = handler.handleInvalidToken(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        ErrorResponse body = response.getBody();
        assertThat(body.getErrorCode()).isEqualTo("INVALID_TOKEN");
        assertThat(body.getMessage()).isEqualTo("localized:error.auth.verification.token.invalid");
    }

    @Test
    @DisplayName("InvalidTokenException (reset-password site) → 401, INVALID_TOKEN, reset-key message")
    void handle_invalidToken_resetPassword() {
        InvalidTokenException ex = new InvalidTokenException(
                "error.auth.password.reset.token.invalid");
        ResponseEntity<ErrorResponse> response = handler.handleInvalidToken(ex, request);

        ErrorResponse body = response.getBody();
        assertThat(body.getErrorCode()).isEqualTo("INVALID_TOKEN");
        assertThat(body.getMessage()).isEqualTo("localized:error.auth.password.reset.token.invalid");
    }

    @Test
    @DisplayName("ExpiredTokenException (verify-email site) → 401, EXPIRED_TOKEN, verification-key message")
    void handle_expiredToken_verifyEmail() {
        ExpiredTokenException ex = new ExpiredTokenException(
                "error.auth.verification.token.expired");
        ResponseEntity<ErrorResponse> response = handler.handleExpiredToken(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        ErrorResponse body = response.getBody();
        assertThat(body.getErrorCode()).isEqualTo("EXPIRED_TOKEN");
        assertThat(body.getMessage()).isEqualTo("localized:error.auth.verification.token.expired");
    }

    @Test
    @DisplayName("ExpiredTokenException (reset-password site) → 401, EXPIRED_TOKEN, reset-key message")
    void handle_expiredToken_resetPassword() {
        ExpiredTokenException ex = new ExpiredTokenException(
                "error.auth.password.reset.token.expired");
        ResponseEntity<ErrorResponse> response = handler.handleExpiredToken(ex, request);

        ErrorResponse body = response.getBody();
        assertThat(body.getErrorCode()).isEqualTo("EXPIRED_TOKEN");
        assertThat(body.getMessage()).isEqualTo("localized:error.auth.password.reset.token.expired");
    }

    @Test
    @DisplayName("AlreadyVerifiedException → 409, ALREADY_VERIFIED (user Q2 = YES)")
    void handle_alreadyVerified() {
        ResponseEntity<ErrorResponse> response = handler.handleAlreadyVerified(
                new AlreadyVerifiedException(), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ErrorResponse body = response.getBody();
        assertThat(body.getErrorCode()).isEqualTo("ALREADY_VERIFIED");
        assertThat(body.getMessage()).isEqualTo("localized:error.auth.already.verified");
    }
}
