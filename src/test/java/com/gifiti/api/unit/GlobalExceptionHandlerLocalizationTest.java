package com.gifiti.api.unit;

import com.gifiti.api.dto.response.ErrorResponse;
import com.gifiti.api.exception.ConflictException;
import com.gifiti.api.exception.GlobalExceptionHandler;
import com.gifiti.api.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the locale-aware behavior introduced in
 * {@link GlobalExceptionHandler} by Task 5 of the i18n backend feature.
 *
 * <p>These tests exercise the handler with a mocked {@link MessageSource} so we
 * can assert (a) the handler resolves keyed business exceptions via the message
 * source using the request's locale, (b) legacy literal-message exceptions
 * still pass through unchanged, (c) the handler's own previously-hardcoded
 * English strings now route through the message source, and (d) a
 * {@link NoSuchMessageException} is caught and turned into a safe English
 * fallback rather than escaping the handler (Risk #3 in
 * {@code specs/005-i18n-backend-support/plan.md}).
 *
 * <p>Per {@code architecture-conventions.md § Error Handling}, the response
 * shape ({@link ErrorResponse}) is unchanged — only the {@code message} field
 * is now locale-aware.
 */
class GlobalExceptionHandlerLocalizationTest {

    private static final Locale EN_US = Locale.forLanguageTag("en-US");
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private MessageSource messageSource;
    private GlobalExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        messageSource = mock(MessageSource.class);
        handler = new GlobalExceptionHandler(messageSource);
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.setRequestURI("/api/v1/wishlists/abc123");
        request = mockRequest;
        LocaleContextHolder.setLocale(EN_US);
    }

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    @DisplayName("LocalizedRuntimeException with key resolves via MessageSource using request locale (en-US)")
    void localized_exception_with_key_resolves_via_messageSource() {
        when(messageSource.getMessage(
                eq("error.test.resource.not_found"),
                nullable(Object[].class),
                eq(EN_US)))
                .thenReturn("Wishlist with id abc123 not found");

        // Pass args via explicit Object[] to avoid the legacy 3-arg
        // (resourceName, fieldName, fieldValue) constructor on
        // ResourceNotFoundException, which Java fixed-arity overloading
        // would otherwise prefer over the (String, Object...) varargs form.
        ResourceNotFoundException ex = new ResourceNotFoundException(
                "error.test.resource.not_found", new Object[]{"Wishlist", "abc123"});

        ResponseEntity<ErrorResponse> response = handler.handleResourceNotFound(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage())
                .isEqualTo("Wishlist with id abc123 not found");
        verify(messageSource).getMessage(
                eq("error.test.resource.not_found"),
                argThat((Object[] args) -> args != null
                        && args.length == 2
                        && "Wishlist".equals(args[0])
                        && "abc123".equals(args[1])),
                eq(EN_US));
    }

    @Test
    @DisplayName("LocalizedRuntimeException with key resolves to pt-BR text when request locale is pt-BR")
    void localized_exception_with_key_resolves_to_pt_BR_when_locale_is_pt_BR() {
        LocaleContextHolder.setLocale(PT_BR);
        when(messageSource.getMessage(
                eq("error.test.resource.not_found"),
                nullable(Object[].class),
                eq(PT_BR)))
                .thenReturn("Wishlist com id abc123 não encontrado");

        ResourceNotFoundException ex = new ResourceNotFoundException(
                "error.test.resource.not_found", new Object[]{"Wishlist", "abc123"});

        ResponseEntity<ErrorResponse> response = handler.handleResourceNotFound(ex, request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage())
                .isEqualTo("Wishlist com id abc123 não encontrado");
        verify(messageSource).getMessage(
                eq("error.test.resource.not_found"),
                nullable(Object[].class),
                eq(PT_BR));
    }

    @Test
    @DisplayName("Legacy LocalizedRuntimeException without a key falls back to ex.getMessage() and skips MessageSource")
    @SuppressWarnings("deprecation")
    void legacy_exception_without_key_falls_back_to_getMessage() {
        ResourceNotFoundException legacy = new ResourceNotFoundException("Some literal English string");

        ResponseEntity<ErrorResponse> response = handler.handleResourceNotFound(legacy, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Some literal English string");
        // No key — handler must not invoke MessageSource for the body message.
        verify(messageSource, never()).getMessage(
                eq("Some literal English string"), any(), any(Locale.class));
    }

    @Test
    @DisplayName("Missing key (NoSuchMessageException) falls back to a safe default and does not throw, status preserved")
    void missing_key_falls_back_to_safe_default_does_not_throw() {
        when(messageSource.getMessage(
                eq("error.never.defined"),
                any(),
                eq(EN_US)))
                .thenThrow(new NoSuchMessageException("error.never.defined"));

        ResourceNotFoundException ex = new ResourceNotFoundException(
                "error.never.defined", new Object[0]);

        ResponseEntity<ErrorResponse> response = handler.handleResourceNotFound(ex, request);

        // Status preserved — exception's @ResponseStatus(NOT_FOUND) wins.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        // Safe English fallback — must not be empty/null and must not be the raw key.
        assertThat(response.getBody().getMessage())
                .isNotBlank()
                .isNotEqualTo("error.never.defined");
    }

    @Test
    @DisplayName("Validation handler's own 'Validation failed' string resolves via MessageSource (error.validation.failed)")
    void handler_own_validation_failed_string_resolves_via_messageSource() throws Exception {
        when(messageSource.getMessage(
                eq("error.validation.failed"),
                any(),
                eq(EN_US)))
                .thenReturn("Validation failed");

        // Build a MethodArgumentNotValidException via a real (no-arg) target
        // method on this test class, so the underlying MethodParameter is
        // non-null (Spring's getMessage path dereferences it on construction
        // and during toString invariants).
        java.lang.reflect.Method target = getClass().getDeclaredMethod(
                "handler_own_validation_failed_string_resolves_via_messageSource");
        org.springframework.core.MethodParameter param =
                new org.springframework.core.MethodParameter(target, -1);
        org.springframework.validation.BeanPropertyBindingResult bindingResult =
                new org.springframework.validation.BeanPropertyBindingResult(new Object(), "target");
        org.springframework.web.bind.MethodArgumentNotValidException ex =
                new org.springframework.web.bind.MethodArgumentNotValidException(param, bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidationErrors(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Validation failed");
        verify(messageSource).getMessage(
                eq("error.validation.failed"), any(), eq(EN_US));
    }

    @Test
    @DisplayName("DuplicateKey handler's 'Resource already exists' string resolves via MessageSource (error.resource.already.exists)")
    void duplicate_key_handler_resolves_via_messageSource() {
        when(messageSource.getMessage(
                eq("error.resource.already.exists"),
                any(),
                eq(EN_US)))
                .thenReturn("Resource already exists");

        org.springframework.dao.DuplicateKeyException ex =
                new org.springframework.dao.DuplicateKeyException("dup");

        ResponseEntity<ErrorResponse> response = handler.handleDuplicateKey(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Resource already exists");
        verify(messageSource).getMessage(
                eq("error.resource.already.exists"), any(), eq(EN_US));
    }

    @Test
    @DisplayName("HttpMessageNotReadable handler resolves 'Malformed request body' via MessageSource (error.request.malformed)")
    void malformed_body_handler_resolves_via_messageSource() {
        when(messageSource.getMessage(
                eq("error.request.malformed"),
                any(),
                eq(EN_US)))
                .thenReturn("Malformed request body");

        HttpMessageNotReadableException ex =
                new HttpMessageNotReadableException("bad", null, null);

        ResponseEntity<ErrorResponse> response = handler.handleHttpMessageNotReadable(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Malformed request body");
        verify(messageSource).getMessage(
                eq("error.request.malformed"), any(), eq(EN_US));
    }

    @Test
    @DisplayName("HttpRequestMethodNotSupported handler resolves with the method as arg (error.http.method.not.supported)")
    void method_not_supported_handler_passes_method_as_arg() {
        HttpRequestMethodNotSupportedException ex =
                new HttpRequestMethodNotSupportedException("DELETE");
        when(messageSource.getMessage(
                eq("error.http.method.not.supported"),
                nullable(Object[].class),
                eq(EN_US)))
                .thenReturn("HTTP method 'DELETE' is not supported for this endpoint");

        ResponseEntity<ErrorResponse> response = handler.handleMethodNotSupported(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage())
                .isEqualTo("HTTP method 'DELETE' is not supported for this endpoint");
    }

    @Test
    @DisplayName("Generic Exception handler resolves 'An unexpected error occurred' via MessageSource (error.unexpected)")
    void generic_exception_handler_resolves_via_messageSource() {
        when(messageSource.getMessage(
                eq("error.unexpected"),
                any(),
                eq(EN_US)))
                .thenReturn("An unexpected error occurred. Please try again later.");

        ResponseEntity<ErrorResponse> response = handler.handleGenericException(
                new RuntimeException("kaboom"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage())
                .isEqualTo("An unexpected error occurred. Please try again later.");
    }

    @Test
    @DisplayName("Legacy ConflictException (literal message) returns the literal verbatim and skips MessageSource for the body message")
    @SuppressWarnings("deprecation")
    void legacy_conflict_exception_returns_literal_verbatim() {
        ConflictException legacy = new ConflictException("Email already registered");

        ResponseEntity<ErrorResponse> response = handler.handleConflict(legacy, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Email already registered");
        verify(messageSource, never()).getMessage(
                eq("Email already registered"), any(), any(Locale.class));
    }
}
