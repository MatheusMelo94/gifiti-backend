package com.gifiti.api.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gifiti.api.dto.response.ErrorResponse;
import com.gifiti.api.exception.AccessCodeRateLimitedException;
import com.gifiti.api.exception.AccessCodeRequiredException;
import com.gifiti.api.exception.GlobalExceptionHandler;
import com.gifiti.api.exception.InvalidAccessCodeException;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the three new access-code exception types (T7 / plan §4.5)
 * and their wiring in {@link GlobalExceptionHandler}.
 *
 * <p>Per ADR 0008 § Decision K (rollout) and plan §4.5:
 * <ul>
 *   <li>{@code AccessCodeRequiredException} → 403, errorCode
 *       {@code "ACCESS_CODE_REQUIRED"}, i18n key
 *       {@code error.wishlist.access-code.required};</li>
 *   <li>{@code InvalidAccessCodeException} → 403, errorCode
 *       {@code "INVALID_ACCESS_CODE"}, i18n key
 *       {@code error.wishlist.access-code.invalid};</li>
 *   <li>{@code AccessCodeRateLimitedException} → 429, errorCode
 *       {@code "ACCESS_CODE_RATE_LIMITED"}, i18n key
 *       {@code error.wishlist.access-code.rate-limited}.</li>
 * </ul>
 *
 * <p>The {@code errorCode} field is the new machine-readable discriminator on
 * {@link ErrorResponse} (user Q1 reconciliation 2026-05-17): existing
 * {@code error} field continues to hold the HTTP reason phrase; the new
 * {@code errorCode} field is what the frontend narrows on for the gate UX.
 */
class AccessCodeExceptionHandlingTest {

    private GlobalExceptionHandler handler;
    private MessageSource messageSource;
    private HttpServletRequest request;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        messageSource = Mockito.mock(MessageSource.class);
        // Default fallback for any key — returns the key so tests can verify
        // localization went through MessageSource.
        when(messageSource.getMessage(any(String.class), any(), any(Locale.class)))
                .thenAnswer(inv -> "localized:" + inv.getArgument(0));
        handler = new GlobalExceptionHandler(messageSource);
        request = Mockito.mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/public/wishlists/foo");
    }

    @Test
    @DisplayName("AccessCodeRequiredException → 403 + errorCode=ACCESS_CODE_REQUIRED + localized message")
    void accessCodeRequiredReturns403WithDiscriminator() {
        when(messageSource.getMessage(
                eq("error.wishlist.access-code.required"), any(), any(Locale.class)))
                .thenReturn("Access code required");

        ResponseEntity<ErrorResponse> response =
                handler.handleAccessCodeRequired(new AccessCodeRequiredException(), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(403);
        assertThat(body.getError()).isEqualTo(HttpStatus.FORBIDDEN.getReasonPhrase());
        assertThat(body.getErrorCode()).isEqualTo("ACCESS_CODE_REQUIRED");
        assertThat(body.getMessage()).isEqualTo("Access code required");
    }

    @Test
    @DisplayName("InvalidAccessCodeException → 403 + errorCode=INVALID_ACCESS_CODE")
    void invalidAccessCodeReturns403WithDiscriminator() {
        when(messageSource.getMessage(
                eq("error.wishlist.access-code.invalid"), any(), any(Locale.class)))
                .thenReturn("Invalid access code");

        ResponseEntity<ErrorResponse> response =
                handler.handleInvalidAccessCode(new InvalidAccessCodeException(), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getErrorCode()).isEqualTo("INVALID_ACCESS_CODE");
        assertThat(body.getMessage()).isEqualTo("Invalid access code");
    }

    @Test
    @DisplayName("AccessCodeRateLimitedException → 429 + errorCode=ACCESS_CODE_RATE_LIMITED")
    void accessCodeRateLimitedReturns429WithDiscriminator() {
        when(messageSource.getMessage(
                eq("error.wishlist.access-code.rate-limited"), any(), any(Locale.class)))
                .thenReturn("Too many attempts");

        ResponseEntity<ErrorResponse> response =
                handler.handleAccessCodeRateLimited(new AccessCodeRateLimitedException(), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getErrorCode()).isEqualTo("ACCESS_CODE_RATE_LIMITED");
        assertThat(body.getMessage()).isEqualTo("Too many attempts");
    }

    @Test
    @DisplayName("errorCode is omitted from JSON when null (existing endpoints unaffected)")
    void errorCodeOmittedWhenNull() throws Exception {
        ErrorResponse response = ErrorResponse.builder()
                .status(400)
                .error("Bad Request")
                .message("Validation failed")
                .build();
        String json = objectMapper.writeValueAsString(response);
        assertThat(json)
                .as("errorCode must be omitted when null (JsonInclude.NON_NULL preserves "
                        + "backward compat for existing 4xx responses)")
                .doesNotContain("errorCode");
    }

    @Test
    @DisplayName("errorCode is included in JSON when populated")
    void errorCodeIncludedWhenSet() throws Exception {
        ErrorResponse response = ErrorResponse.builder()
                .status(403)
                .error("Forbidden")
                .errorCode("ACCESS_CODE_REQUIRED")
                .message("Access code required")
                .build();
        String json = objectMapper.writeValueAsString(response);
        assertThat(json).contains("\"errorCode\":\"ACCESS_CODE_REQUIRED\"");
    }
}
