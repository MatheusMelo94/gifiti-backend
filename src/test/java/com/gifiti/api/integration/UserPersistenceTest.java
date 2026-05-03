package com.gifiti.api.integration;

import com.gifiti.api.model.User;
import com.gifiti.api.model.enums.Language;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link User} MongoDB persistence of {@code preferredLanguage}.
 *
 * <p>Pins three contracts:
 * <ol>
 *   <li>A User document inserted with no {@code preferredLanguage} field (legacy
 *       production data, spec criterion #15) reads back as a {@link User} whose
 *       {@code effectiveLanguage()} returns {@link Language#EN_US}, with the field
 *       absent (not null) on the raw document.</li>
 *   <li>A User saved with a non-null {@code preferredLanguage} stores the enum
 *       by NAME (e.g., {@code "PT_BR"}) — Spring Data Mongo's default behavior
 *       and the contract this feature relies on. If a future engineer adds a
 *       custom converter that switches to ordinal serialization, this test
 *       fails loudly.</li>
 *   <li>A User saved with {@code preferredLanguage = null} stores no field at
 *       all in the raw document (Spring Data Mongo skips null fields by default),
 *       preserving the lazy-default-on-read invariant.</li>
 * </ol>
 */
class UserPersistenceTest extends BaseIntegrationTest {

    @Test
    void legacy_document_without_preferredLanguage_field_reads_as_EN_US() {
        // Insert a raw document with NO preferredLanguage field — simulates a
        // user document written by the version of the code prior to this feature.
        Document legacy = new Document()
                .append("email", "legacy@example.com")
                .append("emailVerified", false);
        mongoTemplate.getCollection("users").insertOne(legacy);

        User loaded = mongoTemplate.findAll(User.class).stream()
                .filter(u -> "legacy@example.com".equals(u.getEmail()))
                .findFirst()
                .orElseThrow();

        assertThat(loaded.getPreferredLanguage()).isNull();
        assertThat(loaded.effectiveLanguage()).isEqualTo(Language.EN_US);

        // The raw document should NOT have the field — we did not write it.
        Document raw = mongoTemplate.getCollection("users")
                .find(new Document("email", "legacy@example.com"))
                .first();
        assertThat(raw).isNotNull();
        assertThat(raw.containsKey("preferredLanguage")).isFalse();
    }

    @Test
    void saving_user_with_PT_BR_stores_enum_by_name() {
        User user = User.builder()
                .email("ptbr@example.com")
                .preferredLanguage(Language.PT_BR)
                .build();
        mongoTemplate.save(user);

        Document raw = mongoTemplate.getCollection("users")
                .find(new Document("email", "ptbr@example.com"))
                .first();
        assertThat(raw).isNotNull();
        // Default Spring Data Mongo behavior: enum stored as String by NAME.
        assertThat(raw.get("preferredLanguage")).isEqualTo("PT_BR");

        User loaded = mongoTemplate.findAll(User.class).stream()
                .filter(u -> "ptbr@example.com".equals(u.getEmail()))
                .findFirst()
                .orElseThrow();
        assertThat(loaded.getPreferredLanguage()).isEqualTo(Language.PT_BR);
        assertThat(loaded.effectiveLanguage()).isEqualTo(Language.PT_BR);
    }

    @Test
    void saving_user_with_null_preferredLanguage_writes_no_field() {
        User user = User.builder()
                .email("nopref@example.com")
                .build();
        mongoTemplate.save(user);

        Document raw = mongoTemplate.getCollection("users")
                .find(new Document("email", "nopref@example.com"))
                .first();
        assertThat(raw).isNotNull();
        // Spring Data Mongo skips null fields on write — preserves the
        // lazy-default-on-read invariant for new users with no preference.
        assertThat(raw.containsKey("preferredLanguage")).isFalse();
    }
}
