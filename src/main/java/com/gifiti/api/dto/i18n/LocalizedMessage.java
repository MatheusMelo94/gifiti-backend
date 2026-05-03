package com.gifiti.api.dto.i18n;

import java.util.Objects;

/**
 * Wire-bound value type for response DTOs that need a localized success message.
 *
 * <p>Services (e.g. {@code AuthService}) attach a {@code LocalizedMessage} to
 * response DTOs in place of a hardcoded English string. The {@code MessageSource}
 * key + positional MessageFormat arguments travel as data; resolution to a
 * locale-specific string happens at JSON serialization time inside
 * {@link LocalizedMessageSerializer}, using the locale carried on
 * {@code LocaleContextHolder} (set by {@code GifitiLocaleResolver}).</p>
 *
 * <p>Two consequences pinned by tests:</p>
 * <ul>
 *   <li>The internal {@code messageKey} field is <em>never</em> serialized to JSON —
 *       the serializer emits only the resolved string, so internal key namespaces
 *       never leak to API clients (security concern).</li>
 *   <li>Services remain locale-agnostic — they neither inject {@code MessageSource}
 *       nor read {@code LocaleContextHolder}. This satisfies
 *       {@code architecture-conventions.md § Layer Rules} (services own business
 *       logic; serializers handle wire-format translation).</li>
 * </ul>
 *
 * <p>Construction: prefer {@link #of(String, Object...)} for readable call sites.
 * The canonical constructor normalizes a {@code null} args array to an empty
 * array and rejects a {@code null} key — services are expected to always supply
 * a key, and the serializer relies on a non-null key for lookup.</p>
 *
 * @param messageKey {@code MessageSource} bundle key, e.g. {@code "auth.register.success"}
 * @param args       MessageFormat positional arguments; never {@code null}
 *                   after construction
 */
public record LocalizedMessage(String messageKey, Object[] args) {

    /**
     * Canonical constructor. Rejects a null key; normalizes null args to an
     * empty array so callers (and the serializer) never need to defend against
     * an unexpected null on the args side.
     */
    public LocalizedMessage {
        Objects.requireNonNull(messageKey, "messageKey");
        if (args == null) {
            args = new Object[0];
        }
    }

    /**
     * Convenience factory: {@code LocalizedMessage.of("auth.register.success")}
     * or {@code LocalizedMessage.of("error.something", "arg1", 42)}.
     */
    public static LocalizedMessage of(String key, Object... args) {
        return new LocalizedMessage(key, args);
    }
}
