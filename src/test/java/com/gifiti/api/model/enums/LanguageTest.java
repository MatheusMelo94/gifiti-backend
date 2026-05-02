package com.gifiti.api.model.enums;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link Language}.
 *
 * <p>Pins the BCP-47 tag conversion contract used by {@code GifitiLocaleResolver}
 * (Task 3) and the registration-language derivation in {@code AuthService} (Task 8).
 */
class LanguageTest {

    @Test
    void fromTag_pt_BR_returns_PT_BR() {
        assertThat(Language.fromTag("pt-BR")).contains(Language.PT_BR);
    }

    @Test
    void fromTag_en_US_returns_EN_US() {
        assertThat(Language.fromTag("en-US")).contains(Language.EN_US);
    }

    @Test
    void fromTag_unsupported_returns_empty() {
        assertThat(Language.fromTag("fr-FR")).isEmpty();
    }

    @Test
    void fromTag_null_returns_empty() {
        assertThat(Language.fromTag(null)).isEmpty();
    }

    @Test
    void fromLocale_pt_BR_returns_PT_BR() {
        Optional<Language> result = Language.fromLocale(Locale.forLanguageTag("pt-BR"));
        assertThat(result).contains(Language.PT_BR);
    }

    @Test
    void toLocale_EN_US_round_trips_to_en_US_tag() {
        assertThat(Language.EN_US.toLocale().toLanguageTag()).isEqualTo("en-US");
    }
}
