package com.gifiti.api.util;

import java.security.SecureRandom;

/**
 * Generator for 4-digit numeric access codes used to gate access to PRIVATE
 * wishlists. Created in feature 008 (T2).
 *
 * <p>Per ADR 0008 § Decision F (code generation entropy):
 * <ul>
 *   <li>4-digit numeric String, leading zeros allowed (full {@code 0000..9999}
 *       keyspace);</li>
 *   <li>backed by {@link SecureRandom} — never {@code Math.random()}, never
 *       unsalted UUIDs;</li>
 *   <li>no exclusion list (codes like {@code "0000"}, {@code "1234"},
 *       {@code "1111"} are allowed — defense is rate-limit + randomness, per
 *       Decision F, not vocabulary filtering).</li>
 * </ul>
 *
 * <p>Per Security findings F-3 (constant-time comparison) and F-4 (no logging
 * of code values): callers MUST NOT log the generated value.
 *
 * <p>Per `architecture-conventions.md § Stack Baseline`: utility classes are
 * static-only with no public constructor.
 */
public final class AccessCodeGenerator {

    /**
     * Cryptographically strong random number generator. Per Decision F, this
     * MUST be {@link SecureRandom}, not {@link java.util.Random} (which is
     * predictable from seeds observable via timing) and not {@code Math.random}.
     *
     * <p>A single instance is reused across calls — {@code SecureRandom} is
     * thread-safe and there is no benefit to per-call instantiation (which
     * would also drain the entropy pool faster).
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private AccessCodeGenerator() {
        throw new UnsupportedOperationException("AccessCodeGenerator is a static utility");
    }

    /**
     * Generate a 4-digit numeric access code with leading zeros preserved.
     *
     * @return a 4-character String matching {@code ^\d{4}$} (e.g. {@code "0042"},
     *         {@code "1234"}, {@code "9999"}).
     */
    public static String generate() {
        int value = SECURE_RANDOM.nextInt(10_000);
        return String.format("%04d", value);
    }
}
