package com.gifiti.api.exception;

import com.gifiti.api.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Global exception handler providing consistent, locale-aware error responses
 * across all endpoints.
 *
 * <p>Per Task 5 of the i18n backend feature, this handler resolves response
 * messages through {@link MessageSource} using the locale supplied by
 * {@code GifitiLocaleResolver} (via {@link LocaleContextHolder}). Two paths:
 *
 * <ul>
 *   <li><b>Keyed business exceptions:</b> {@link LocalizedRuntimeException}
 *       subtypes carry a {@code messageKey} and {@code args}; the handler
 *       resolves them via {@link #localize(String, Object[])}.</li>
 *   <li><b>Legacy literal-message exceptions:</b> the same subtypes built via
 *       deprecated literal-string constructors return {@code null} from
 *       {@link LocalizedRuntimeException#getMessageKey()}; the handler then
 *       falls back to {@link Throwable#getMessage()} unchanged. A WARN log
 *       prompts conversion. Removed when Task 10 finishes call-site migration.</li>
 * </ul>
 *
 * <p>The handler's own previously-hardcoded English strings (e.g.
 * {@code "Validation failed"}, {@code "An unexpected error occurred"}) now
 * route through {@link #localize(String, Object[])} too — keys live in
 * {@code messages.properties}.
 *
 * <p>Per {@code architecture-conventions.md § Error Handling}, the response
 * shape ({@link ErrorResponse}) is unchanged.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Hardcoded last-resort fallback when {@code error.unexpected} itself is
     * missing from the bundle (defensive against the Risk #3 infinite loop in
     * {@code specs/005-i18n-backend-support/plan.md}).
     */
    private static final String SAFE_FALLBACK_MESSAGE = "An error occurred while processing the request.";

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.NOT_FOUND, resolveLocalized(ex), request);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(
            UnauthorizedException ex, HttpServletRequest request) {
        log.warn("Unauthorized access attempt: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, resolveLocalized(ex), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Access denied: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.FORBIDDEN, resolveLocalized(ex), request);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(
            ConflictException ex, HttpServletRequest request) {
        log.warn("Conflict: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT, resolveLocalized(ex), request);
    }

    @ExceptionHandler(ImageUploadException.class)
    public ResponseEntity<ErrorResponse> handleImageUpload(
            ImageUploadException ex, HttpServletRequest request) {
        log.warn("Image upload failed: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, resolveLocalized(ex), request);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateKey(
            DuplicateKeyException ex, HttpServletRequest request) {
        log.warn("Duplicate key: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT,
                localize("error.resource.already.exists", null), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        log.warn("Validation failed: {}", ex.getMessage());

        BindingResult result = ex.getBindingResult();
        List<ErrorResponse.FieldError> fieldErrors = result.getFieldErrors().stream()
                .map(error -> ErrorResponse.FieldError.builder()
                        .field(error.getField())
                        // error.getDefaultMessage() is already locale-resolved
                        // because LocalValidatorFactoryBean is wired to the
                        // project MessageSource (see I18nConfig).
                        .message(error.getDefaultMessage())
                        .build())
                .collect(Collectors.toList());

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message(localize("error.validation.failed", null))
                .path(request.getRequestURI())
                .correlationId(MDC.get("correlationId"))
                .details(fieldErrors)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        log.warn("Constraint violation: {}", ex.getMessage());

        List<ErrorResponse.FieldError> fieldErrors = ex.getConstraintViolations().stream()
                .map(violation -> ErrorResponse.FieldError.builder()
                        .field(violation.getPropertyPath().toString())
                        .message(violation.getMessage())
                        .build())
                .collect(Collectors.toList());

        ErrorResponse response = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message(localize("error.validation.failed", null))
                .path(request.getRequestURI())
                .correlationId(MDC.get("correlationId"))
                .details(fieldErrors)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Invalid argument: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST,
                localize("error.invalid.parameter", null), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("Malformed request body: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST,
                localize("error.request.malformed", null), request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        log.warn("Method not allowed: {} {}", ex.getMethod(), request.getRequestURI());
        return buildErrorResponse(HttpStatus.METHOD_NOT_ALLOWED,
                localize("error.http.method.not.supported", new Object[]{ex.getMethod()}),
                request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        log.warn("Unsupported media type: {}", ex.getContentType());
        return buildErrorResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                localize("error.http.media.type.not.supported",
                        new Object[]{String.valueOf(ex.getContentType())}),
                request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            NoResourceFoundException ex, HttpServletRequest request) {
        log.debug("No resource found: {}", request.getRequestURI());
        return buildErrorResponse(HttpStatus.NOT_FOUND,
                localize("error.resource.not.found.generic", null), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        log.warn("Type mismatch for parameter '{}': {}", ex.getName(), ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST,
                localize("error.invalid.parameter.named", new Object[]{ex.getName()}),
                request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, HttpServletRequest request) {
        log.error("Unexpected error occurred", ex);
        return buildErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                localize("error.unexpected", null),
                request
        );
    }

    /**
     * Resolve the response message for a {@link LocalizedRuntimeException}.
     *
     * <p>If the exception carries a {@code messageKey}, it is resolved via
     * {@link MessageSource} using the request locale. If the exception was
     * built via the deprecated literal-message constructor (key {@code null}),
     * the literal {@link Throwable#getMessage()} is returned verbatim and a
     * WARN log prompts call-site migration. Both paths must keep working
     * until Task 10 of the i18n feature finishes the migration.
     *
     * @param ex the localized runtime exception
     * @return the localized response message (or the literal legacy message)
     */
    private String resolveLocalized(LocalizedRuntimeException ex) {
        String key = ex.getMessageKey();
        if (key == null) {
            log.warn("Legacy literal-message exception path used by {}: '{}'. "
                            + "Migrate the call site to a MessageSource key (Task 10).",
                    ex.getClass().getSimpleName(), ex.getMessage());
            return ex.getMessage();
        }
        return localize(key, ex.getArgs());
    }

    /**
     * Resolve a {@link MessageSource} key against {@link LocaleContextHolder}'s
     * current locale, with a defensive safety net for missing keys.
     *
     * <p>Per ADR-0001, {@code ResourceBundleMessageSource} is configured with
     * {@code useCodeAsDefaultMessage=false}, which means a missing key throws
     * {@link NoSuchMessageException}. If that happened inside an exception
     * handler, the handler itself would throw, bypassing all error-response
     * conventions and leaking Spring's default 500 page (Risk #3 in
     * {@code specs/005-i18n-backend-support/plan.md}). This helper catches
     * the exception, logs the missing key at WARN so an operator can fix the
     * bundle, and returns a hardcoded English fallback so the original
     * response (status, shape) is preserved.
     *
     * @param key  the {@link MessageSource} key
     * @param args positional args for the message template, may be {@code null}
     * @return the resolved string, or {@link #SAFE_FALLBACK_MESSAGE} if the key
     *         is missing from every bundle
     */
    private String localize(String key, Object[] args) {
        Locale locale = LocaleContextHolder.getLocale();
        try {
            return messageSource.getMessage(key, args, locale);
        } catch (NoSuchMessageException missing) {
            log.warn("Missing i18n key '{}' for locale '{}' — falling back to safe default. "
                    + "Add the key to messages.properties (and locale variants).", key, locale);
            return SAFE_FALLBACK_MESSAGE;
        }
    }

    private ResponseEntity<ErrorResponse> buildErrorResponse(
            HttpStatus status, String message, HttpServletRequest request) {
        ErrorResponse response = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(request.getRequestURI())
                .correlationId(MDC.get("correlationId"))
                .build();

        return ResponseEntity.status(status).body(response);
    }
}
