package com.gifiti.api.model;

import com.gifiti.api.model.enums.Language;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link User} domain helpers.
 *
 * <p>Pins the lazy-default behavior for {@code preferredLanguage} per spec
 * criterion #15: a user document with no {@code preferredLanguage} field
 * (legacy production data) must be treated as {@link Language#EN_US}
 * without rewriting the document.
 */
class UserTest {

    @Test
    void effectiveLanguage_returns_EN_US_when_preferredLanguage_is_null() {
        User user = User.builder()
                .email("alice@example.com")
                .build();

        assertThat(user.getPreferredLanguage()).isNull();
        assertThat(user.effectiveLanguage()).isEqualTo(Language.EN_US);
    }

    @Test
    void effectiveLanguage_returns_stored_language_when_set() {
        User user = User.builder()
                .email("alice@example.com")
                .preferredLanguage(Language.PT_BR)
                .build();

        assertThat(user.effectiveLanguage()).isEqualTo(Language.PT_BR);
    }
}
