package com.gifiti.api.util;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.metadata.ConstraintDescriptor;
import org.springframework.validation.FieldError;

import java.util.Map;

/**
 * Deterministic mapping of Jakarta Validation constraint annotations to the
 * fixed {@code FieldError.errorCode} inventory pinned in ADR 0009 § Decision D2.
 *
 * <p>Two source surfaces, mirroring {@code GlobalExceptionHandler}'s two
 * validation paths:
 * <ul>
 *   <li>{@link #mapMethodArgumentError(FieldError)} — handles Spring
 *       {@link FieldError} objects produced by
 *       {@code MethodArgumentNotValidException} when a {@code @Valid
 *       @RequestBody} DTO fails Bean Validation.</li>
 *   <li>{@link #mapConstraintViolation(ConstraintViolation)} — handles
 *       Jakarta {@link ConstraintViolation} objects produced by
 *       {@code ConstraintViolationException} when method-parameter-level
 *       constraints fail.</li>
 * </ul>
 *
 * <p>Inventory (frozen per ADR 0009 Decision D2; new values require ADR
 * amendment):
 *
 * <pre>
 * @NotBlank / @NotNull / @NotEmpty   → REQUIRED
 * @Email                              → INVALID_FORMAT
 * @Pattern                            → INVALID_FORMAT
 * @Size  (rejected.length &lt; min)   → TOO_SHORT
 * @Size  (rejected.length &gt; max)   → TOO_LONG
 * @Min / @Max / @Positive / @PositiveOrZero / @Negative / @NegativeOrZero
 *                                     → OUT_OF_RANGE
 * anything else                        → INVALID
 * </pre>
 *
 * <p>Reserved values not currently emitted (Decision D2 future-use):
 * <ul>
 *   <li>{@code WEAK_PASSWORD} — only if a future custom {@code @StrongPassword}
 *       annotation is added (today's password-strength check happens in
 *       {@code PasswordValidationService}, not via a Jakarta annotation; that
 *       path surfaces the top-level errorCode {@code WEAK_PASSWORD} via
 *       {@code WeakPasswordException}).</li>
 *   <li>{@code TAKEN} — reserved for a future field-level uniqueness
 *       validator (today's duplicate-email check happens at the service layer
 *       and surfaces the top-level errorCode {@code EMAIL_ALREADY_REGISTERED}).</li>
 * </ul>
 *
 * <p>Pure static methods, no Spring dependencies — safe to call from
 * {@code GlobalExceptionHandler} stream pipelines.
 *
 * <p>Convention citations:
 * <ul>
 *   <li>{@code architecture-conventions.md § API Contracts} — fixed inventory
 *       of machine-readable discriminators; the mapper is the single source of
 *       truth.</li>
 *   <li>ADR 0009 § Decision D2 — engineer must NOT invent new errorCode
 *       values outside this inventory without architect signoff.</li>
 * </ul>
 */
public final class FieldErrorCodeMapper {

    private FieldErrorCodeMapper() {
        // Static utility — no instances.
    }

    /**
     * Map a Spring {@link FieldError} (from {@code MethodArgumentNotValidException})
     * to its {@code errorCode} inventory value.
     *
     * @param error the Spring-side field error; may carry a {@code null} code
     *              (treated as {@code INVALID}).
     * @return one of the ADR 0009 § Decision D2 inventory values; never {@code null}.
     */
    public static String mapMethodArgumentError(FieldError error) {
        if (error == null) {
            return "INVALID";
        }
        String code = error.getCode();
        if (code == null) {
            return "INVALID";
        }
        return switch (code) {
            case "NotBlank", "NotNull", "NotEmpty" -> "REQUIRED";
            case "Email", "Pattern" -> "INVALID_FORMAT";
            case "Size" -> mapSizeFromSpringFieldError(error);
            case "Min", "Max", "PositiveOrZero", "Positive", "Negative", "NegativeOrZero", "DecimalMin", "DecimalMax" ->
                    "OUT_OF_RANGE";
            default -> "INVALID";
        };
    }

    /**
     * Map a Jakarta {@link ConstraintViolation} (from
     * {@code ConstraintViolationException}) to its {@code errorCode} inventory
     * value.
     *
     * @param violation the Jakarta-side constraint violation; may not be
     *                  {@code null}.
     * @return one of the ADR 0009 § Decision D2 inventory values; never {@code null}.
     */
    public static String mapConstraintViolation(ConstraintViolation<?> violation) {
        if (violation == null) {
            return "INVALID";
        }
        ConstraintDescriptor<?> descriptor = violation.getConstraintDescriptor();
        if (descriptor == null || descriptor.getAnnotation() == null) {
            return "INVALID";
        }
        String annotationName = descriptor.getAnnotation().annotationType().getSimpleName();
        return switch (annotationName) {
            case "NotBlank", "NotNull", "NotEmpty" -> "REQUIRED";
            case "Email", "Pattern" -> "INVALID_FORMAT";
            case "Size" -> mapSizeFromConstraintViolation(violation, descriptor);
            case "Min", "Max", "PositiveOrZero", "Positive", "Negative", "NegativeOrZero", "DecimalMin", "DecimalMax" ->
                    "OUT_OF_RANGE";
            default -> "INVALID";
        };
    }

    /**
     * Disambiguate {@code @Size} violations into {@code TOO_SHORT} vs
     * {@code TOO_LONG} for the Spring {@link FieldError} surface.
     *
     * <p>Spring's {@code MessageSourceResolvable} contract for {@code @Size}
     * places the constraint's {@code max} at arguments[1] and {@code min} at
     * arguments[2] (the [0] slot is the field-label resolvable). When both
     * bounds are present, the rejected value's length is compared against the
     * min: under-min → {@code TOO_SHORT}; over-max → {@code TOO_LONG}; ties
     * default to {@code TOO_LONG} (defensive: a value-equal-to-max is over,
     * value-equal-to-min is within). Single-bound forms (only-min, only-max)
     * resolve unambiguously.
     */
    private static String mapSizeFromSpringFieldError(FieldError error) {
        Object[] args = error.getArguments();
        Integer min = null;
        Integer max = null;
        if (args != null) {
            if (args.length > 1 && args[1] instanceof Integer m) {
                max = m;
            }
            if (args.length > 2 && args[2] instanceof Integer m) {
                min = m;
            }
        }
        Integer length = lengthOfRejected(error.getRejectedValue());
        return classifySize(length, min, max);
    }

    /**
     * Disambiguate {@code @Size} violations into {@code TOO_SHORT} vs
     * {@code TOO_LONG} for the Jakarta {@link ConstraintViolation} surface.
     *
     * <p>The descriptor exposes {@code min} and {@code max} as annotation
     * attributes; the violation exposes the rejected (invalid) value via
     * {@link ConstraintViolation#getInvalidValue()}.
     */
    private static String mapSizeFromConstraintViolation(
            ConstraintViolation<?> violation, ConstraintDescriptor<?> descriptor) {
        Map<String, Object> attrs = descriptor.getAttributes();
        Integer min = readIntAttr(attrs, "min");
        Integer max = readIntAttr(attrs, "max");
        Integer length = lengthOfRejected(violation.getInvalidValue());
        return classifySize(length, min, max);
    }

    /**
     * Common min/max-vs-length classification used by both source surfaces.
     */
    private static String classifySize(Integer length, Integer min, Integer max) {
        if (length == null) {
            // No measurable length — pick the more conservative bound. If a min
            // is set, treat as too-short; otherwise too-long; otherwise INVALID.
            if (min != null && min > 0) return "TOO_SHORT";
            if (max != null) return "TOO_LONG";
            return "INVALID";
        }
        if (min != null && length < min) return "TOO_SHORT";
        if (max != null && length > max) return "TOO_LONG";
        // Borderline: rejected value satisfies both bounds — unusual, fall to TOO_LONG.
        if (max != null) return "TOO_LONG";
        if (min != null) return "TOO_SHORT";
        return "INVALID";
    }

    private static Integer lengthOfRejected(Object rejected) {
        if (rejected == null) return null;
        if (rejected instanceof CharSequence cs) return cs.length();
        if (rejected instanceof java.util.Collection<?> c) return c.size();
        if (rejected instanceof java.util.Map<?, ?> m) return m.size();
        if (rejected.getClass().isArray()) return java.lang.reflect.Array.getLength(rejected);
        return null;
    }

    private static Integer readIntAttr(Map<String, Object> attrs, String key) {
        if (attrs == null) return null;
        Object v = attrs.get(key);
        if (v instanceof Integer i) return i;
        if (v instanceof Number n) return n.intValue();
        return null;
    }
}
