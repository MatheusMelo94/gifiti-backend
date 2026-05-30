package com.gifiti.api.unit.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gifiti.api.dto.response.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for feature 009 / T6 — {@code ErrorResponse.FieldError.errorCode}.
 *
 * <p>Per ADR 0009 Decision D2 (Bucket 2), {@code FieldError} grows a nullable
 * {@code errorCode} field carrying one of {@code REQUIRED}, {@code
 * INVALID_FORMAT}, {@code TOO_SHORT}, {@code TOO_LONG}, {@code OUT_OF_RANGE},
 * {@code WEAK_PASSWORD}, {@code TAKEN}, {@code INVALID}. Frontend narrows on
 * this discriminator for Spanish field-level error rendering.
 *
 * <p>Backwards compatibility contract: existing call sites that only set
 * {@code field} + {@code message} continue to work; the new field is
 * {@code @JsonInclude(NON_NULL)} so legacy responses don't grow a {@code
 * "errorCode": null} entry.
 */
class ErrorResponseFieldErrorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("FieldError builder accepts errorCode")
    void builder_accepts_errorCode() {
        ErrorResponse.FieldError fe = ErrorResponse.FieldError.builder()
                .field("email")
                .message("Email is required")
                .errorCode("REQUIRED")
                .build();

        assertThat(fe.getField()).isEqualTo("email");
        assertThat(fe.getMessage()).isEqualTo("Email is required");
        assertThat(fe.getErrorCode()).isEqualTo("REQUIRED");
    }

    @Test
    @DisplayName("FieldError builder accepts no errorCode (backwards compat)")
    void builder_without_errorCode_remains_valid() {
        ErrorResponse.FieldError fe = ErrorResponse.FieldError.builder()
                .field("email")
                .message("Email is required")
                .build();

        assertThat(fe.getField()).isEqualTo("email");
        assertThat(fe.getMessage()).isEqualTo("Email is required");
        assertThat(fe.getErrorCode()).isNull();
    }

    @Test
    @DisplayName("FieldError JSON omits errorCode when null (NON_NULL discipline)")
    void json_omits_errorCode_when_null() throws Exception {
        ErrorResponse.FieldError fe = ErrorResponse.FieldError.builder()
                .field("email")
                .message("Email is required")
                .build();

        String json = objectMapper.writeValueAsString(fe);

        assertThat(json)
                .contains("\"field\":\"email\"")
                .contains("\"message\":\"Email is required\"")
                .doesNotContain("errorCode");
    }

    @Test
    @DisplayName("FieldError JSON includes errorCode when set")
    void json_includes_errorCode_when_set() throws Exception {
        ErrorResponse.FieldError fe = ErrorResponse.FieldError.builder()
                .field("email")
                .message("Email must be valid")
                .errorCode("INVALID_FORMAT")
                .build();

        String json = objectMapper.writeValueAsString(fe);

        assertThat(json)
                .contains("\"errorCode\":\"INVALID_FORMAT\"");
    }
}
