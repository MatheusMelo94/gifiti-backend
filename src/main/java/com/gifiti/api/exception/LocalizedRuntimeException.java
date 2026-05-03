package com.gifiti.api.exception;

import java.util.Objects;

/**
 * Base class for business exceptions that carry a {@code MessageSource} key
 * and positional arguments instead of a hardcoded English message.
 *
 * <p>Localization happens in {@code GlobalExceptionHandler} at response time
 * (Task 5 of the i18n backend feature), based on the locale resolved by
 * {@code GifitiLocaleResolver}. Operator-facing logs intentionally see the
 * key (via {@link #getMessage()}) rather than the localized text — log lines
 * remain English-only and deterministic across locales (spec § Out of Scope #11).
 *
 * <p>Two construction paths are supported:
 * <ul>
 *   <li><b>Keyed (preferred):</b> {@link #LocalizedRuntimeException(String, Object...)}
 *       and {@link #LocalizedRuntimeException(String, Object[], Throwable)}. New code
 *       and migrated call sites use these.</li>
 *   <li><b>Legacy (deprecated):</b> {@link #LocalizedRuntimeException(String)}.
 *       Retained so existing call sites continue to compile and behave identically
 *       until Task 10 migrates them en masse.</li>
 * </ul>
 *
 * <p>Java overload resolution: {@code new LocalizedRuntimeException("literal")}
 * resolves to the fixed-arity legacy constructor (per JLS 15.12.2.2 fixed arity is
 * preferred over varargs in phase 1), so single-string call sites are unambiguous.
 * Keyed calls always supply at least one argument after the key. Keyed
 * construction with zero args is supported by passing an explicit empty array:
 * {@code new LocalizedRuntimeException("error.foo", new Object[0])} — though in
 * practice every i18n message either has placeholders or doesn't need them, so
 * this branch is rarely needed.
 *
 * <p>Per {@code architecture-conventions.md § Error Handling}, custom exception
 * types per domain concern remain the norm; this base class is purely an
 * infrastructure layer for localization plumbing.
 */
public class LocalizedRuntimeException extends RuntimeException {

    /**
     * MessageSource key (e.g. {@code "error.resource.not.found.with.field"}).
     * {@code null} when constructed via the legacy literal-message constructor.
     */
    private final String messageKey;

    /**
     * Positional arguments for the message template (e.g. {@code {0}}, {@code {1}}).
     * Never {@code null}; an empty array is used when no args are supplied.
     */
    private final Object[] args;

    /**
     * Keyed constructor — preferred for new code.
     *
     * <p>The MessageSource key is also passed to {@code super} so {@link #getMessage()}
     * returns the key (useful for operator-facing logs).
     *
     * @param messageKey MessageSource key; must not be {@code null}
     * @param args       optional positional args for the message template;
     *                   {@code null} is normalized to an empty array
     */
    public LocalizedRuntimeException(String messageKey, Object... args) {
        super(Objects.requireNonNull(messageKey, "messageKey"));
        this.messageKey = messageKey;
        this.args = args == null ? new Object[0] : args;
    }

    /**
     * Keyed constructor with cause — preferred for new code where a wrapped
     * exception must be preserved (e.g. R2 upload failures wrapping {@code IOException}).
     *
     * @param messageKey MessageSource key; must not be {@code null}
     * @param args       optional positional args; {@code null} normalized to empty array
     * @param cause      the underlying throwable, may be {@code null}
     */
    public LocalizedRuntimeException(String messageKey, Object[] args, Throwable cause) {
        super(Objects.requireNonNull(messageKey, "messageKey"), cause);
        this.messageKey = messageKey;
        this.args = args == null ? new Object[0] : args;
    }

    /**
     * Legacy constructor — preserves backward compatibility with call sites that
     * still pass a literal English message.
     *
     * <p>{@link #getMessageKey()} returns {@code null} for instances built this
     * way, which {@code GlobalExceptionHandler} (Task 5) treats as a signal to
     * fall back to {@link #getMessage()} and log a deprecation warning.
     *
     * @deprecated Use {@link #LocalizedRuntimeException(String, Object...)} instead.
     *     Retained until Task 10 migrates all call sites to keyed construction.
     * @param literalMessage the literal English message
     */
    @Deprecated
    public LocalizedRuntimeException(String literalMessage) {
        super(literalMessage);
        this.messageKey = null;
        this.args = new Object[0];
    }

    /**
     * Legacy constructor with cause — preserves backward compatibility with call
     * sites that pass a literal English message together with the underlying cause.
     *
     * @deprecated Use {@link #LocalizedRuntimeException(String, Object[], Throwable)}
     *     instead. Retained until Task 10 migrates all call sites.
     * @param literalMessage the literal English message
     * @param cause          the underlying throwable, may be {@code null}
     */
    @Deprecated
    public LocalizedRuntimeException(String literalMessage, Throwable cause) {
        super(literalMessage, cause);
        this.messageKey = null;
        this.args = new Object[0];
    }

    /**
     * @return the MessageSource key, or {@code null} if this exception was built
     *     via the legacy literal-message constructor
     */
    public String getMessageKey() {
        return messageKey;
    }

    /**
     * @return positional arguments for the message template; never {@code null}
     */
    public Object[] getArgs() {
        return args;
    }
}
