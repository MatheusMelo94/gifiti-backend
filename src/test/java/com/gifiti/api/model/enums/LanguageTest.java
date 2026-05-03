package com.gifiti.api.model.enums;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link Language}.
 *
 * <p>Pins the BCP-47 tag conversion contract used by {@code GifitiLocaleResolver}
 * (Task 3) and the registration-language derivation in {@code AuthService} (Task 8).
 */
class LanguageTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

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

    // --- Task 9: Jackson wire-format round-trip ---
    // The Language enum is exposed on the i18n profile-update API (Task 9).
    // The wire format is the BCP-47 tag (e.g., "pt-BR"), not the enum constant
    // name ("PT_BR"). The pin below is the contract for the ObjectMapper used
    // by Spring MVC.

    @Test
    void Jackson_serializes_PT_BR_as_pt_BR_tag() throws Exception {
        String json = objectMapper.writeValueAsString(Language.PT_BR);
        assertThat(json).isEqualTo("\"pt-BR\"");
    }

    @Test
    void Jackson_serializes_EN_US_as_en_US_tag() throws Exception {
        String json = objectMapper.writeValueAsString(Language.EN_US);
        assertThat(json).isEqualTo("\"en-US\"");
    }

    @Test
    void Jackson_deserializes_pt_BR_tag_to_PT_BR_enum() throws Exception {
        Language result = objectMapper.readValue("\"pt-BR\"", Language.class);
        assertThat(result).isEqualTo(Language.PT_BR);
    }

    @Test
    void Jackson_deserializes_en_US_tag_to_EN_US_enum() throws Exception {
        Language result = objectMapper.readValue("\"en-US\"", Language.class);
        assertThat(result).isEqualTo(Language.EN_US);
    }

    @Test
    void Jackson_throws_on_unsupported_tag() {
        // Spec criterion #19: unsupported preferredLanguage values must be
        // rejected. The IllegalArgumentException thrown by the @JsonCreator
        // is wrapped by Jackson in a JsonMappingException subtype
        // (ValueInstantiationException) which Spring MVC translates into
        // HttpMessageNotReadableException -> 400 (error.request.malformed)
        // via GlobalExceptionHandler. We assert on the parent type so this
        // test doesn't pin a Jackson-internal subtype.
        assertThatThrownBy(() -> objectMapper.readValue("\"fr-FR\"", Language.class))
                .isInstanceOf(JsonMappingException.class)
                .hasMessageContaining("Unsupported preferredLanguage tag")
                .hasMessageContaining("fr-FR");
    }

    @Test
    void Jackson_throws_on_garbage_tag() {
        assertThatThrownBy(() -> objectMapper.readValue("\"xx\"", Language.class))
                .isInstanceOf(JsonMappingException.class)
                .hasMessageContaining("Unsupported preferredLanguage tag")
                .hasMessageContaining("xx");
    }
}
