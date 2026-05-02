package com.gifiti.api.model.enums;

import java.util.Locale;
import java.util.Optional;

/**
 * Supported user-facing languages for backend i18n (spec 005-i18n-backend-support).
 *
 * <p>Each value carries its BCP-47 language tag and exposes a {@link Locale}
 * conversion. The enum is the canonical type used across services, DTOs,
 * and the {@code User} entity; conversion to/from {@link Locale} or raw
 * {@link String} tags happens at the boundaries.
 *
 * <p>Adding a third language is one new value plus a {@code messages_xx_YY.properties}
 * resource bundle — no architectural change.
 */
public enum Language {

    EN_US("en-US"),
    PT_BR("pt-BR");

    private final String tag;

    Language(String tag) {
        this.tag = tag;
    }

    /**
     * The BCP-47 language tag (e.g., {@code "en-US"}, {@code "pt-BR"}).
     */
    public String getTag() {
        return tag;
    }

    /**
     * The {@link Locale} corresponding to this language's BCP-47 tag.
     *
     * <p>Constructed via {@link Locale#forLanguageTag(String)} so the round-trip
     * {@code Language.toLocale().toLanguageTag()} is byte-identical to {@link #getTag()}.
     */
    public Locale toLocale() {
        return Locale.forLanguageTag(tag);
    }

    /**
     * Look up a {@link Language} by BCP-47 tag.
     *
     * @param tag BCP-47 language tag (e.g., {@code "en-US"}); may be {@code null}.
     * @return the matching {@link Language}, or {@link Optional#empty()} if the
     *         tag is {@code null} or not one of the supported values.
     */
    public static Optional<Language> fromTag(String tag) {
        if (tag == null) {
            return Optional.empty();
        }
        for (Language language : values()) {
            if (language.tag.equals(tag)) {
                return Optional.of(language);
            }
        }
        return Optional.empty();
    }

    /**
     * Look up a {@link Language} by {@link Locale}.
     *
     * <p>Delegates to {@link #fromTag(String)} after extracting the locale's
     * BCP-47 tag. A {@code null} locale returns {@link Optional#empty()}.
     *
     * @param locale a {@link Locale}; may be {@code null}.
     * @return the matching {@link Language}, or {@link Optional#empty()} if the
     *         locale is {@code null} or its tag is not one of the supported values.
     */
    public static Optional<Language> fromLocale(Locale locale) {
        if (locale == null) {
            return Optional.empty();
        }
        return fromTag(locale.toLanguageTag());
    }
}
