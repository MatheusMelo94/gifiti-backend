package com.gifiti.api.integration.i18n;

import com.gifiti.api.dto.request.RegisterRequest;
import com.gifiti.api.integration.BaseIntegrationTest;
import com.gifiti.api.model.User;
import com.gifiti.api.model.enums.Language;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Feature 009 / T3 — Spanish locale resolution end-to-end.
 *
 * <p>Verifies that {@code Language.ES_419} flows through the existing locale
 * resolver, registration locale derivation, and self-service profile-update
 * paths originally pinned for en-US / pt-BR (feature 005 / Tasks 3, 8, 9).
 *
 * <p>No production code changes expected at T3: T1 added the enum value, and
 * {@code GifitiLocaleResolver.SUPPORTED_LOCALES} derives from
 * {@link Language#values()}. This test exercises the integration to prove the
 * resolver picked the new value up automatically.
 *
 * <p>Convention citations:
 * <ul>
 *   <li>{@code architecture-conventions.md § Engineering Workflow} — TDD.</li>
 *   <li>ADR 0009 § Decision A — single Spanish variant es-419.</li>
 * </ul>
 */
class SpanishLocaleResolutionTest extends BaseIntegrationTest {

    private User readUser(String email) {
        return mongoTemplate.findOne(
                new Query(Criteria.where("email").is(email)), User.class);
    }

    @Test
    @DisplayName("register with Accept-Language: es-419 persists preferredLanguage=ES_419")
    void registration_persists_es_419_on_user() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("es419-register@example.test")
                .password("BlueP4nther$Xyz2!")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .header("Accept-Language", "es-419")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        User reread = readUser("es419-register@example.test");
        assertThat(reread.getPreferredLanguage()).isEqualTo(Language.ES_419);
    }

    @Test
    @DisplayName("PUT /profile preferredLanguage=es-419 persists the new value")
    void profile_update_to_es_419_persists() throws Exception {
        String token = createUserAndGetToken("es419-profile@example.test", "BlueP4nther$Xyz2!");

        String body = "{\"preferredLanguage\":\"es-419\"}";
        mockMvc.perform(put("/api/v1/profile")
                        .header("Authorization", bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredLanguage").value("es-419"));

        User reread = readUser("es419-profile@example.test");
        assertThat(reread.getPreferredLanguage()).isEqualTo(Language.ES_419);
    }

    @Test
    @DisplayName("PUT /profile with preferredLanguage=fr-FR returns 400 (regression: ES_419 does not loosen validation)")
    void profile_update_to_unsupported_locale_returns_400() throws Exception {
        String token = createUserAndGetToken("fr-rejected@example.test", "BlueP4nther$Xyz2!");

        String body = "{\"preferredLanguage\":\"fr-FR\"}";
        mockMvc.perform(put("/api/v1/profile")
                        .header("Authorization", bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
