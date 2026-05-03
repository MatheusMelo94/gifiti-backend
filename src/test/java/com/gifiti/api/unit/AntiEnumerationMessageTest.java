package com.gifiti.api.unit;

import com.gifiti.api.config.I18nConfig;
import com.gifiti.api.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Regression tests for Security finding F-1 (anti-enumeration discipline).
 *
 * <p>These tests pin the contract that specific bundle keys — those returned to
 * unauthenticated callers in response to authentication, token, or password-reset
 * flows — never leak per-user information that would enable an attacker to
 * enumerate registered accounts.
 *
 * <p>Pinned invariants (per threat-model.md F-1):
 * <ul>
 *   <li>The resolved en-US message never contains an "@" sign (no email leakage).</li>
 *   <li>The resolved en-US message never contains user-controlled values (no
 *       interpolated email/username text).</li>
 *   <li>The resolved en-US message never affirms account existence
 *       (e.g. "the account exists", "user found").</li>
 *   <li>Every F-1 key resolves successfully in both en-US and pt-BR (so a bundle
 *       miss can never silently leak the key as response text in production).</li>
 * </ul>
 *
 * <p>Length-ratio enforcement between en-US and pt-BR is Task 11's responsibility
 * (integration-level test). This test focuses on key-level content discipline.
 */
@ExtendWith(SpringExtension.class)
@SpringJUnitConfig(classes = {I18nConfig.class, AntiEnumerationMessageTest.MockRepoConfig.class})
class AntiEnumerationMessageTest {

    @Configuration
    static class MockRepoConfig {
        @Bean
        UserRepository userRepository() {
            return mock(UserRepository.class);
        }
    }

    @Autowired
    private MessageSource messageSource;

    private static final Locale EN_US = Locale.forLanguageTag("en-US");
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    /**
     * The five F-1-named keys from threat-model.md. Each must resolve in both
     * locales and must satisfy the anti-enumeration invariants below.
     */
    private static final String[] F1_KEYS = {
            "auth.password.reset.requested",
            "error.auth.credentials.invalid",
            "error.auth.verification.token.invalid",
            "error.auth.password.reset.token.invalid",
            "error.auth.password.reset.token.expired"
    };

    @Test
    @DisplayName("F-1: every anti-enumeration key resolves in en-US without leaking '@' or user-controlled values")
    void f1_keys_resolve_without_email_marker_in_en_us() {
        for (String key : F1_KEYS) {
            String message = messageSource.getMessage(key, null, EN_US);
            assertThat(message)
                    .as("F-1 key %s must not contain '@' in en-US (would leak email)", key)
                    .doesNotContain("@");
            assertThat(message)
                    .as("F-1 key %s must not be empty in en-US", key)
                    .isNotBlank();
        }
    }

    @Test
    @DisplayName("F-1: every anti-enumeration key resolves in pt-BR (no missing-key fallback)")
    void f1_keys_resolve_in_pt_br() {
        for (String key : F1_KEYS) {
            String message = messageSource.getMessage(key, null, PT_BR);
            assertThat(message)
                    .as("F-1 key %s must resolve to a non-blank value in pt-BR (Task 12 fills in real translation)", key)
                    .isNotBlank();
            // Task 12 will replace the [TODO pt-BR] prefix; until then, the placeholder
            // is acceptable — what matters is the key resolves and is not blank.
            assertThat(message)
                    .as("F-1 key %s pt-BR value must not contain '@'", key)
                    .doesNotContain("@");
        }
    }

    @Test
    @DisplayName("F-1: auth.password.reset.requested in en-US never affirms account existence")
    void password_reset_requested_does_not_affirm_account_existence() {
        String message = messageSource.getMessage("auth.password.reset.requested", null, EN_US);
        // The anti-enumeration message is intentionally CONDITIONAL ("If an account
        // exists with this email, ...") — it must NOT positively affirm.
        assertThat(message)
                .as("anti-enumeration message must not affirm account existence")
                .doesNotContainIgnoringCase("the account exists")
                .doesNotContainIgnoringCase("we found your account")
                .doesNotContainIgnoringCase("user exists");
        assertThat(message)
                .as("anti-enumeration message should be conditional ('If an account exists...')")
                .containsIgnoringCase("if an account exists");
    }

    @Test
    @DisplayName("F-1: error.auth.credentials.invalid is intentionally vague (does not distinguish missing email from wrong password)")
    void credentials_invalid_does_not_distinguish_failure_modes() {
        String message = messageSource.getMessage("error.auth.credentials.invalid", null, EN_US);
        // Must not say "user not found", "no such account", "wrong password",
        // "incorrect password" — which would let an attacker enumerate.
        assertThat(message)
                .as("credentials.invalid must not reveal whether the email exists")
                .doesNotContainIgnoringCase("user not found")
                .doesNotContainIgnoringCase("no such")
                .doesNotContainIgnoringCase("does not exist");
        assertThat(message)
                .as("credentials.invalid must not specify which credential failed")
                .doesNotContainIgnoringCase("wrong password")
                .doesNotContainIgnoringCase("incorrect password");
    }

    @Test
    @DisplayName("F-1: token-invalid keys are uniform — verify and reset use equally vague phrasing")
    void token_invalid_keys_are_uniformly_vague() {
        String verify = messageSource.getMessage("error.auth.verification.token.invalid", null, EN_US);
        String reset = messageSource.getMessage("error.auth.password.reset.token.invalid", null, EN_US);
        // Both must not embed user info or distinguish "token unknown" from "token rejected".
        assertThat(verify).doesNotContain("@").isNotBlank();
        assertThat(reset).doesNotContain("@").isNotBlank();
        assertThat(verify)
                .as("verify token invalid must not say 'no such token' / 'token does not exist'")
                .doesNotContainIgnoringCase("does not exist")
                .doesNotContainIgnoringCase("no such");
        assertThat(reset)
                .as("reset token invalid must not say 'no such token' / 'token does not exist'")
                .doesNotContainIgnoringCase("does not exist")
                .doesNotContainIgnoringCase("no such");
    }

    @Test
    @DisplayName("F-1: token-expired keys reveal expiry but never user identity")
    void token_expired_does_not_leak_user_identity() {
        String resetExpired = messageSource.getMessage("error.auth.password.reset.token.expired", null, EN_US);
        assertThat(resetExpired)
                .doesNotContain("@")
                .doesNotContainIgnoringCase("user")
                .containsIgnoringCase("expired");
    }
}
