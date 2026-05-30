package com.gifiti.api.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AccessCodeGenerator} (T2).
 *
 * <p>Per ADR 0008 § Decision F: 4-digit numeric, leading zeros allowed, backed
 * by {@code java.security.SecureRandom}. No exclusion lists.
 *
 * <p>Tests validate:
 * <ul>
 *   <li>output is always a 4-digit numeric String matching {@code ^\d{4}$};</li>
 *   <li>leading zeros are emitted (so the keyspace is the full 0000..9999);</li>
 *   <li>over a 10k-sample population every digit (0..9) appears in every
 *       position — a coarse uniformity smoke test that {@code SecureRandom} is
 *       producing usable entropy (a chi-square test is overkill for this MVP).</li>
 * </ul>
 *
 * <p>Per `architecture-conventions.md § Engineering Workflow` — RED-GREEN-
 * REFACTOR.
 */
class AccessCodeGeneratorTest {

    private static final Pattern FOUR_DIGITS = Pattern.compile("^\\d{4}$");

    @Test
    @DisplayName("generate() returns a 4-digit numeric String on every invocation")
    void generateProduces4DigitNumericString() {
        for (int i = 0; i < 1_000; i++) {
            String code = AccessCodeGenerator.generate();
            assertThat(code)
                    .as("iteration %d produced %s", i, code)
                    .isNotNull()
                    .hasSize(4)
                    .matches(FOUR_DIGITS);
        }
    }

    @Test
    @DisplayName("generate() emits leading zeros (e.g. '0042', '0007', '0000')")
    void leadingZerosAreAllowed() {
        // Sample ~30k codes; statistically certain we see at least one with a
        // leading zero (P ≈ 1 − 0.9^30000 ≈ 1) if the keyspace is the full
        // 0..9999 range as Decision F mandates.
        boolean sawLeadingZero = false;
        for (int i = 0; i < 30_000; i++) {
            String code = AccessCodeGenerator.generate();
            if (code.charAt(0) == '0') {
                sawLeadingZero = true;
                break;
            }
        }
        assertThat(sawLeadingZero)
                .as("at least one generated code in 30k samples should have a leading zero "
                        + "— if this fails, the generator is not using the full 0..9999 keyspace")
                .isTrue();
    }

    @Test
    @DisplayName("generate() exercises every digit (0..9) in every position over 10k samples")
    void distributionCoversAllDigitsInAllPositions() {
        Set<Character>[] perPosition = new Set[4];
        for (int i = 0; i < 4; i++) {
            perPosition[i] = new HashSet<>();
        }

        for (int i = 0; i < 10_000; i++) {
            String code = AccessCodeGenerator.generate();
            for (int pos = 0; pos < 4; pos++) {
                perPosition[pos].add(code.charAt(pos));
            }
        }

        for (int pos = 0; pos < 4; pos++) {
            assertThat(perPosition[pos])
                    .as("position %d should see every digit 0..9 in 10k samples", pos)
                    .hasSize(10);
        }
    }

    @Test
    @DisplayName("generator class is final and not instantiable (static utility)")
    void utilityClassIsNotInstantiable() {
        // Per `architecture-conventions.md § Stack Baseline` — utility classes
        // are static-only; no public constructor. Defensive assertion in case
        // someone tries to autowire it as a bean.
        var constructors = AccessCodeGenerator.class.getDeclaredConstructors();
        assertThat(constructors).hasSize(1);
        assertThat(constructors[0].canAccess(null)).isFalse();
    }
}
