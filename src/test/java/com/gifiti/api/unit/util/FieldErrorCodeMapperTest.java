package com.gifiti.api.unit.util;

import com.gifiti.api.util.FieldErrorCodeMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.validation.FieldError;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for feature 009 / T7 — {@link FieldErrorCodeMapper}.
 *
 * <p>Covers the two source surfaces of validation errors in
 * {@code GlobalExceptionHandler} per ADR 0009 § Decision D2:
 *
 * <ol>
 *   <li>Spring {@link FieldError} (from {@code MethodArgumentNotValidException}
 *       when {@code @Valid @RequestBody} fails Bean Validation).</li>
 *   <li>Jakarta {@link ConstraintViolation} (from
 *       {@code ConstraintViolationException} when path-level constraints
 *       like {@code @Valid} on method parameters fail).</li>
 * </ol>
 *
 * <p>Inventory pinned: {@code REQUIRED}, {@code INVALID_FORMAT},
 * {@code TOO_SHORT}, {@code TOO_LONG}, {@code OUT_OF_RANGE}, {@code INVALID}
 * (default). {@code WEAK_PASSWORD} and {@code TAKEN} are reserved values in
 * the inventory (Decision D2) for future custom annotations / business
 * uniqueness paths; not emitted by the deterministic mapper today.
 */
class FieldErrorCodeMapperTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    // --- Spring FieldError surface (MethodArgumentNotValidException path) ---

    @Test
    @DisplayName("Spring FieldError NotBlank → REQUIRED")
    void springFieldError_NotBlank_returns_REQUIRED() {
        FieldError fe = new FieldError("obj", "email", null, false,
                new String[]{"NotBlank"}, null, "must not be blank");
        assertThat(FieldErrorCodeMapper.mapMethodArgumentError(fe)).isEqualTo("REQUIRED");
    }

    @Test
    @DisplayName("Spring FieldError NotNull → REQUIRED")
    void springFieldError_NotNull_returns_REQUIRED() {
        FieldError fe = new FieldError("obj", "foo", null, false,
                new String[]{"NotNull"}, null, "must not be null");
        assertThat(FieldErrorCodeMapper.mapMethodArgumentError(fe)).isEqualTo("REQUIRED");
    }

    @Test
    @DisplayName("Spring FieldError NotEmpty → REQUIRED")
    void springFieldError_NotEmpty_returns_REQUIRED() {
        FieldError fe = new FieldError("obj", "foo", null, false,
                new String[]{"NotEmpty"}, null, "must not be empty");
        assertThat(FieldErrorCodeMapper.mapMethodArgumentError(fe)).isEqualTo("REQUIRED");
    }

    @Test
    @DisplayName("Spring FieldError Email → INVALID_FORMAT")
    void springFieldError_Email_returns_INVALID_FORMAT() {
        FieldError fe = new FieldError("obj", "email", "abc", false,
                new String[]{"Email"}, null, "must be a well-formed email address");
        assertThat(FieldErrorCodeMapper.mapMethodArgumentError(fe)).isEqualTo("INVALID_FORMAT");
    }

    @Test
    @DisplayName("Spring FieldError Pattern → INVALID_FORMAT")
    void springFieldError_Pattern_returns_INVALID_FORMAT() {
        FieldError fe = new FieldError("obj", "phone", "abc", false,
                new String[]{"Pattern"}, null, "must match pattern");
        assertThat(FieldErrorCodeMapper.mapMethodArgumentError(fe)).isEqualTo("INVALID_FORMAT");
    }

    @Test
    @DisplayName("Spring FieldError Size with rejected value under min → TOO_SHORT")
    void springFieldError_Size_underMin_returns_TOO_SHORT() {
        // Arguments per Spring's MessageSourceResolvable contract for @Size:
        // [0] = the field's display label (DefaultMessageSourceResolvable for fieldName)
        // [1] = the @Size(max) value
        // [2] = the @Size(min) value
        // Rejected value "abc" has length 3, min=12, max=128 → TOO_SHORT.
        FieldError fe = new FieldError("obj", "password", "abc", false,
                new String[]{"Size"},
                new Object[]{null, 128, 12},
                "size must be between 12 and 128");
        assertThat(FieldErrorCodeMapper.mapMethodArgumentError(fe)).isEqualTo("TOO_SHORT");
    }

    @Test
    @DisplayName("Spring FieldError Size with rejected value over max → TOO_LONG")
    void springFieldError_Size_overMax_returns_TOO_LONG() {
        // Rejected value "verylong..." has length 60, min=0, max=50 → TOO_LONG.
        String rejected = "a".repeat(60);
        FieldError fe = new FieldError("obj", "displayName", rejected, false,
                new String[]{"Size"},
                new Object[]{null, 50, 0},
                "size must be between 0 and 50");
        assertThat(FieldErrorCodeMapper.mapMethodArgumentError(fe)).isEqualTo("TOO_LONG");
    }

    @Test
    @DisplayName("Spring FieldError Min → OUT_OF_RANGE")
    void springFieldError_Min_returns_OUT_OF_RANGE() {
        FieldError fe = new FieldError("obj", "qty", 0, false,
                new String[]{"Min"}, null, "must be at least 1");
        assertThat(FieldErrorCodeMapper.mapMethodArgumentError(fe)).isEqualTo("OUT_OF_RANGE");
    }

    @Test
    @DisplayName("Spring FieldError Max → OUT_OF_RANGE")
    void springFieldError_Max_returns_OUT_OF_RANGE() {
        FieldError fe = new FieldError("obj", "qty", 1000, false,
                new String[]{"Max"}, null, "must be at most 100");
        assertThat(FieldErrorCodeMapper.mapMethodArgumentError(fe)).isEqualTo("OUT_OF_RANGE");
    }

    @Test
    @DisplayName("Spring FieldError PositiveOrZero → OUT_OF_RANGE")
    void springFieldError_PositiveOrZero_returns_OUT_OF_RANGE() {
        FieldError fe = new FieldError("obj", "qty", -1, false,
                new String[]{"PositiveOrZero"}, null, "must be >= 0");
        assertThat(FieldErrorCodeMapper.mapMethodArgumentError(fe)).isEqualTo("OUT_OF_RANGE");
    }

    @Test
    @DisplayName("Spring FieldError Positive → OUT_OF_RANGE")
    void springFieldError_Positive_returns_OUT_OF_RANGE() {
        FieldError fe = new FieldError("obj", "qty", 0, false,
                new String[]{"Positive"}, null, "must be > 0");
        assertThat(FieldErrorCodeMapper.mapMethodArgumentError(fe)).isEqualTo("OUT_OF_RANGE");
    }

    @Test
    @DisplayName("Spring FieldError unknown code → INVALID")
    void springFieldError_unknownCode_returns_INVALID() {
        FieldError fe = new FieldError("obj", "foo", null, false,
                new String[]{"WhateverCustomThing"}, null, "...");
        assertThat(FieldErrorCodeMapper.mapMethodArgumentError(fe)).isEqualTo("INVALID");
    }

    @Test
    @DisplayName("Spring FieldError null code → INVALID")
    void springFieldError_nullCode_returns_INVALID() {
        FieldError fe = new FieldError("obj", "foo", null, false,
                null, null, "...");
        assertThat(FieldErrorCodeMapper.mapMethodArgumentError(fe)).isEqualTo("INVALID");
    }

    // --- Jakarta ConstraintViolation surface (ConstraintViolationException path) ---

    @Test
    @DisplayName("ConstraintViolation NotBlank → REQUIRED")
    void constraintViolation_NotBlank_returns_REQUIRED() {
        var v = firstViolation(new NotBlankBean(""));
        assertThat(FieldErrorCodeMapper.mapConstraintViolation(v)).isEqualTo("REQUIRED");
    }

    @Test
    @DisplayName("ConstraintViolation NotNull → REQUIRED")
    void constraintViolation_NotNull_returns_REQUIRED() {
        var v = firstViolation(new NotNullBean(null));
        assertThat(FieldErrorCodeMapper.mapConstraintViolation(v)).isEqualTo("REQUIRED");
    }

    @Test
    @DisplayName("ConstraintViolation NotEmpty → REQUIRED")
    void constraintViolation_NotEmpty_returns_REQUIRED() {
        var v = firstViolation(new NotEmptyBean(Set.of()));
        assertThat(FieldErrorCodeMapper.mapConstraintViolation(v)).isEqualTo("REQUIRED");
    }

    @Test
    @DisplayName("ConstraintViolation Email → INVALID_FORMAT")
    void constraintViolation_Email_returns_INVALID_FORMAT() {
        var v = firstViolation(new EmailBean("not-an-email"));
        assertThat(FieldErrorCodeMapper.mapConstraintViolation(v)).isEqualTo("INVALID_FORMAT");
    }

    @Test
    @DisplayName("ConstraintViolation Pattern → INVALID_FORMAT")
    void constraintViolation_Pattern_returns_INVALID_FORMAT() {
        var v = firstViolation(new PatternBean("zzz"));
        assertThat(FieldErrorCodeMapper.mapConstraintViolation(v)).isEqualTo("INVALID_FORMAT");
    }

    @Test
    @DisplayName("ConstraintViolation Size with value under min → TOO_SHORT")
    void constraintViolation_Size_underMin_returns_TOO_SHORT() {
        var v = firstViolation(new SizeBean("ab"));
        assertThat(FieldErrorCodeMapper.mapConstraintViolation(v)).isEqualTo("TOO_SHORT");
    }

    @Test
    @DisplayName("ConstraintViolation Size with value over max → TOO_LONG")
    void constraintViolation_Size_overMax_returns_TOO_LONG() {
        var v = firstViolation(new SizeBean("a".repeat(15)));
        assertThat(FieldErrorCodeMapper.mapConstraintViolation(v)).isEqualTo("TOO_LONG");
    }

    @Test
    @DisplayName("ConstraintViolation Min → OUT_OF_RANGE")
    void constraintViolation_Min_returns_OUT_OF_RANGE() {
        var v = firstViolation(new MinBean(0));
        assertThat(FieldErrorCodeMapper.mapConstraintViolation(v)).isEqualTo("OUT_OF_RANGE");
    }

    @Test
    @DisplayName("ConstraintViolation Positive → OUT_OF_RANGE")
    void constraintViolation_Positive_returns_OUT_OF_RANGE() {
        var v = firstViolation(new PositiveBean(0));
        assertThat(FieldErrorCodeMapper.mapConstraintViolation(v)).isEqualTo("OUT_OF_RANGE");
    }

    @Test
    @DisplayName("ConstraintViolation PositiveOrZero → OUT_OF_RANGE")
    void constraintViolation_PositiveOrZero_returns_OUT_OF_RANGE() {
        var v = firstViolation(new PositiveOrZeroBean(-1));
        assertThat(FieldErrorCodeMapper.mapConstraintViolation(v)).isEqualTo("OUT_OF_RANGE");
    }

    private <T> ConstraintViolation<T> firstViolation(T bean) {
        Set<ConstraintViolation<T>> violations = validator.validate(bean);
        assertThat(violations).as("bean must produce at least one violation").isNotEmpty();
        return violations.iterator().next();
    }

    // --- Lightweight beans for each constraint annotation under test ---

    @NoArgsConstructor @AllArgsConstructor
    static class NotBlankBean { @NotBlank String value; }

    @NoArgsConstructor @AllArgsConstructor
    static class NotNullBean { @NotNull String value; }

    @NoArgsConstructor @AllArgsConstructor
    static class NotEmptyBean { @NotEmpty Set<String> value; }

    @NoArgsConstructor @AllArgsConstructor
    static class EmailBean { @Email String value; }

    @NoArgsConstructor @AllArgsConstructor
    static class PatternBean { @Pattern(regexp = "^[a-c]+$") String value; }

    @NoArgsConstructor @AllArgsConstructor
    static class SizeBean { @Size(min = 5, max = 10) String value; }

    @NoArgsConstructor @AllArgsConstructor
    static class MinBean { @Min(1) int value; }

    @NoArgsConstructor @AllArgsConstructor
    static class PositiveBean { @Positive int value; }

    @NoArgsConstructor @AllArgsConstructor
    static class PositiveOrZeroBean { @PositiveOrZero int value; }
}
