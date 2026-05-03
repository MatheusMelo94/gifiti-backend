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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Group F — Persistence and backfill (spec criteria #15, #16, #17).
 *
 * <p>Covers TC-25 through TC-28. Asserts:
 * <ul>
 *   <li>Legacy User documents (no {@code preferredLanguage} field) read as
 *       {@code EN_US} and are NOT silently rewritten on read.</li>
 *   <li>New registrations persist the request locale resolved by
 *       {@code GifitiLocaleResolver} into the User document.</li>
 *   <li>Unsupported {@code Accept-Language} values fall back to {@code EN_US}
 *       at write time as well.</li>
 * </ul>
 *
 * <p>Cite: {@code architecture-conventions.md § Engineering Workflow},
 * {@code § Testing}, {@code § Data Model}.
 */
class PersistenceBackfillIntegrationTest extends BaseIntegrationTest {

    private User readUser(String email) {
        return mongoTemplate.findOne(
                new Query(Criteria.where("email").is(email)), User.class);
    }

    @Test
    @DisplayName("TC-25: legacy user with no preferredLanguage field is NOT rewritten on read")
    void tc25_legacy_user_not_rewritten() {
        // Insert via raw Document to omit preferredLanguage entirely (the User
        // builder would default-write the field one way or another).
        org.bson.Document doc = new org.bson.Document()
                .append("email", "legacy.user@example.test")
                .append("password",
                        "$2a$12$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123")
                .append("emailVerified", false)
                .append("authProvider", "LOCAL")
                .append("roles", List.of("USER"));
        mongoTemplate.getCollection("users").insertOne(doc);

        // Read via the same path the resolver would use (effectiveLanguage()).
        User read = readUser("legacy.user@example.test");
        assertThat(read).isNotNull();
        assertThat(read.getPreferredLanguage())
                .as("Legacy field absent: should read as null on the entity")
                .isNull();
        assertThat(read.effectiveLanguage())
                .as("effectiveLanguage() lazy-defaults to EN_US")
                .isEqualTo(Language.EN_US);

        // Negative: the document on disk is unchanged.
        org.bson.Document reread = mongoTemplate.getCollection("users")
                .find(new org.bson.Document("email", "legacy.user@example.test")).first();
        assertThat(reread).isNotNull();
        assertThat(reread.get("preferredLanguage"))
                .as("legacy user document remains unchanged on read (lazy default contract per spec #15)")
                .isNull();
    }

    @Test
    @DisplayName("TC-26: register with Accept-Language: pt-BR persists preferredLanguage=PT_BR")
    void tc26_register_pt_BR_persists_PT_BR() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("new-pt@example.test")
                .password("BlueP4nther$Xyz2!")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .header("Accept-Language", "pt-BR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        User stored = readUser("new-pt@example.test");
        assertThat(stored).isNotNull();
        assertThat(stored.getPreferredLanguage()).isEqualTo(Language.PT_BR);
    }

    @Test
    @DisplayName("TC-27: register with no Accept-Language persists preferredLanguage=EN_US")
    void tc27_register_no_header_persists_EN_US() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("new-en@example.test")
                .password("BlueP4nther$Xyz2!")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        User stored = readUser("new-en@example.test");
        assertThat(stored).isNotNull();
        assertThat(stored.getPreferredLanguage()).isEqualTo(Language.EN_US);
    }

    @Test
    @DisplayName("TC-28: register with Accept-Language: fr-FR persists preferredLanguage=EN_US (fallback)")
    void tc28_register_unsupported_header_persists_EN_US() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("new-fr@example.test")
                .password("BlueP4nther$Xyz2!")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .header("Accept-Language", "fr-FR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        User stored = readUser("new-fr@example.test");
        assertThat(stored).isNotNull();
        assertThat(stored.getPreferredLanguage())
                .as("Unsupported Accept-Language falls through to GifitiLocaleResolver default (EN_US)")
                .isEqualTo(Language.EN_US);
    }
}
