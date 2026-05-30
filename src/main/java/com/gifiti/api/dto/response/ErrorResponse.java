package com.gifiti.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Consistent error response format for all API errors.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard error response")
public class ErrorResponse {

    @Schema(description = "Error timestamp")
    private Instant timestamp;

    @Schema(description = "HTTP status code", example = "400")
    private int status;

    @Schema(description = "HTTP error name (HTTP reason phrase)", example = "Forbidden")
    private String error;

    /**
     * Machine-readable error discriminator (feature 008 / T7). Populated only
     * by exception handlers that explicitly need a frontend-narrowable code
     * (currently: AccessCodeRequiredException, InvalidAccessCodeException,
     * AccessCodeRateLimitedException). Null for all pre-008 exception paths,
     * which preserves backward compatibility via {@code @JsonInclude(NON_NULL)}.
     *
     * <p>Per user Q1 reconciliation (2026-05-17): the existing {@code error}
     * field continues to carry the HTTP reason phrase ({@code "Forbidden"},
     * {@code "Bad Request"}). Frontend narrows on this new {@code errorCode}
     * field for the gate UX.
     */
    @Schema(description = "Machine-readable discriminator code "
            + "(e.g. ACCESS_CODE_REQUIRED, INVALID_ACCESS_CODE, ACCESS_CODE_RATE_LIMITED). "
            + "Only present on responses where a frontend narrows on the discriminator.",
            example = "ACCESS_CODE_REQUIRED")
    private String errorCode;

    @Schema(description = "Human-readable error message", example = "Validation failed")
    private String message;

    @Schema(description = "Request path", example = "/api/v1/auth/register")
    private String path;

    @Schema(description = "Correlation ID for tracing", example = "a1b2c3d4-e5f6-7890")
    private String correlationId;

    @Schema(description = "Field-level validation errors")
    private List<FieldError> details;

    /**
     * Field-level validation error details.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Validation error on a specific field")
    public static class FieldError {
        @Schema(description = "Field name", example = "email")
        private String field;

        @Schema(description = "Validation message", example = "Email is required")
        private String message;

        /**
         * Machine-readable discriminator for the field-level validation
         * failure (feature 009 / T6). One of: {@code REQUIRED},
         * {@code INVALID_FORMAT}, {@code TOO_SHORT}, {@code TOO_LONG},
         * {@code OUT_OF_RANGE}, {@code WEAK_PASSWORD}, {@code TAKEN},
         * {@code INVALID} per ADR 0009 § Decision D2.
         *
         * <p>Populated by {@code FieldErrorCodeMapper} (T7) when wired through
         * the {@code @ExceptionHandler} handlers in {@code GlobalExceptionHandler}
         * (T8). Null on legacy code paths that haven't migrated yet; the
         * {@code @JsonInclude(NON_NULL)} on this class omits the field from
         * JSON when null so existing clients are unaffected.
         */
        @Schema(description = "Machine-readable validation-failure discriminator "
                + "(e.g. REQUIRED, INVALID_FORMAT, TOO_SHORT, TOO_LONG). "
                + "Only present when the frontend narrows on the discriminator.",
                example = "REQUIRED")
        private String errorCode;
    }
}
