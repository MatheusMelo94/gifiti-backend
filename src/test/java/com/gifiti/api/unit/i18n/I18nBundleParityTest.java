package com.gifiti.api.unit.i18n;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@code messages_es_419.properties} carries the in-scope subset
 * of keys required by feature 009 (Bucket 3, per ADR 0009 Decisions A + F).
 *
 * <p>Per ADR 0009 Decision B, this does NOT assert full pt-BR ↔ es-419 parity
 * across every key — auth/validation error copy flows through errorCode
 * discriminators on the frontend, not through MessageSource on the backend.
 * Only the keys that the backend renders server-side at the wire (4 access-code
 * keys + 18 email-template copy keys + 3 auth-flow human-readable copy keys
 * added in T9) need a Spanish translation.
 *
 * <p>The 3 T9 auth keys (error.auth.password.weak,
 * error.auth.email.not.verified, error.auth.already.verified) are checked once
 * T9 lands; they live alongside the 4 access-code keys + 18 email keys to keep
 * the in-scope set explicit and grep-friendly.
 */
class I18nBundleParityTest {

    /**
     * Keys that MUST be present in every supported locale bundle.
     *
     * <p>Feature 008 surfaced 4 access-code error keys; feature 005 surfaced
     * 18 email-template copy keys; feature 009 T9 adds 3 auth-flow keys for
     * exception message text (HTTP body / legacy email-localization clients).
     */
    private static final Set<String> IN_SCOPE_KEYS = Set.of(
            // Feature 008 — access-code errors
            "error.wishlist.access-code.required",
            "error.wishlist.access-code.invalid",
            "error.wishlist.access-code.rate-limited",
            "error.wishlist.access-code.rotate.public-visibility",
            // Feature 005 — verification email (9 keys)
            "email.verification.subject",
            "email.verification.welcome",
            "email.verification.body",
            "email.verification.cta",
            "email.verification.fallback.notice",
            "email.verification.fallback.link",
            "email.verification.ignore.notice",
            "email.verification.footer.copyright",
            "email.verification.footer.signup",
            // Feature 005 — password-reset email (9 keys)
            "email.password.reset.subject",
            "email.password.reset.heading",
            "email.password.reset.body",
            "email.password.reset.cta",
            "email.password.reset.fallback.notice",
            "email.password.reset.fallback.link",
            "email.password.reset.ignore.notice",
            "email.password.reset.footer.copyright",
            "email.password.reset.footer.notice",
            // Feature 009 / T9 — auth-flow human-readable copy for the new
            // exception classes (Bucket 1). The errorCode discriminator is
            // the frontend narrowing key (ADR 0009 Decision B), but the
            // server-rendered message field still needs locale-resolved text
            // for legacy clients + Spring-side log determinism.
            "error.auth.password.weak",
            "error.auth.email.not.verified",
            "error.auth.already.verified"
    );

    @Test
    @DisplayName("es-419 bundle exists and contains all in-scope keys")
    void es_419_hasAllInScopeKeys() throws IOException {
        Properties spanish = loadBundle("messages_es_419.properties");
        for (String key : IN_SCOPE_KEYS) {
            assertThat(spanish.getProperty(key))
                    .as("Missing key '%s' in messages_es_419.properties", key)
                    .isNotNull()
                    .isNotEmpty();
        }
    }

    @Test
    @DisplayName("pt-BR bundle contains all in-scope keys (regression baseline)")
    void pt_BR_hasAllInScopeKeys() throws IOException {
        Properties portuguese = loadBundle("messages_pt_BR.properties");
        for (String key : IN_SCOPE_KEYS) {
            assertThat(portuguese.getProperty(key))
                    .as("Missing key '%s' in messages_pt_BR.properties", key)
                    .isNotNull()
                    .isNotEmpty();
        }
    }

    @Test
    @DisplayName("en-US (default) bundle contains all in-scope keys")
    void en_US_hasAllInScopeKeys() throws IOException {
        Properties english = loadBundle("messages.properties");
        for (String key : IN_SCOPE_KEYS) {
            assertThat(english.getProperty(key))
                    .as("Missing key '%s' in messages.properties", key)
                    .isNotNull()
                    .isNotEmpty();
        }
    }

    private Properties loadBundle(String resourceName) throws IOException {
        Properties props = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            assertThat(in)
                    .as("Could not load bundle '%s' from classpath", resourceName)
                    .isNotNull();
            props.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        }
        return props;
    }
}
