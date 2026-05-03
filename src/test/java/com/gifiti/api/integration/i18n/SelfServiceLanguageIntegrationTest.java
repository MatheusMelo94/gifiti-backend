package com.gifiti.api.integration.i18n;

import com.gifiti.api.integration.BaseIntegrationTest;
import com.gifiti.api.integration.support.CapturingEmailService;
import com.gifiti.api.integration.support.CapturingEmailService.CapturedEmail;
import com.gifiti.api.model.User;
import com.gifiti.api.model.enums.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Group G — Self-service language change (spec criteria #18, #19).
 *
 * <p>Covers TC-29 through TC-31. Two-step flow for TC-29:
 * <ol>
 *   <li>Authenticated user PUT /profile updates {@code preferredLanguage}.</li>
 *   <li>Trigger an email through resend-verification: it must use the new
 *       stored preference (criterion #18 — recipient-language rule).</li>
 * </ol>
 *
 * <p>Cite: {@code architecture-conventions.md § Engineering Workflow},
 * {@code § Testing}.
 */
class SelfServiceLanguageIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CapturingEmailService capturingEmailService;

    @BeforeEach
    void clearCapturedEmails() {
        capturingEmailService.clear();
    }

    private User readUser(String email) {
        return mongoTemplate.findOne(
                new Query(Criteria.where("email").is(email)), User.class);
    }

    @Test
    @DisplayName("TC-29: PUT /profile preferredLanguage=pt-BR persists and switches subsequent emails to pt-BR")
    void tc29_change_language_switches_emails() throws Exception {
        // Register English-speaking user (no Accept-Language header).
        String token = createUserAndGetToken("lang-change@example.test", "BlueP4nther$Xyz2!");
        capturingEmailService.clear();

        // Step 1: switch preferredLanguage via PUT /profile.
        String body = "{\"preferredLanguage\":\"pt-BR\"}";
        mockMvc.perform(put("/api/v1/profile")
                        .header("Authorization", bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredLanguage").value("pt-BR"));

        // GET /profile reflects the persisted change.
        mockMvc.perform(get("/api/v1/profile")
                        .header("Authorization", bearerToken(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredLanguage").value("pt-BR"));

        // Stored on the User document as the enum value.
        User reread = readUser("lang-change@example.test");
        assertThat(reread.getPreferredLanguage()).isEqualTo(Language.PT_BR);

        // Step 2: trigger an email; no Accept-Language header so the only
        // language signal is the stored preference (now PT_BR).
        mockMvc.perform(post("/api/v1/auth/resend-verification")
                        .header("Authorization", bearerToken(token)))
                .andExpect(status().isOk());

        List<CapturedEmail> sent = capturingEmailService.sent();
        assertThat(sent).as("resend-verification triggers exactly one email").hasSize(1);

        CapturedEmail email = sent.get(0);
        assertThat(email.to()).isEqualTo("lang-change@example.test");
        assertThat(email.body())
                .as("body language must match the new stored preference (pt-BR)")
                .contains("<html lang=\"pt-BR\">");
    }

    @Test
    @DisplayName("TC-30: PUT /profile with unsupported preferredLanguage='fr-FR' returns 400; user not mutated")
    void tc30_unsupported_value_returns_400() throws Exception {
        String token = createUserAndGetToken("badlang-30@example.test", "BlueP4nther$Xyz2!");

        // Per Language.fromJsonTag, an unsupported BCP-47 tag throws
        // IllegalArgumentException at Jackson deserialization → 400 with
        // error.request.malformed. Spec #19 satisfied.
        String body = "{\"preferredLanguage\":\"fr-FR\"}";

        mockMvc.perform(put("/api/v1/profile")
                        .header("Authorization", bearerToken(token))
                        .header("Accept-Language", "en-US")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        // Negative: User document NOT mutated.
        User reread = readUser("badlang-30@example.test");
        assertThat(reread).isNotNull();
        assertThat(reread.getPreferredLanguage())
                .as("Failed update must not mutate stored preference")
                .isEqualTo(Language.EN_US);
    }

    @Test
    @DisplayName("TC-31: PUT /profile with garbage preferredLanguage='xx' returns 400 (alias for TC-11)")
    void tc31_garbage_value_returns_400() throws Exception {
        String token = createUserAndGetToken("badlang-31@example.test", "BlueP4nther$Xyz2!");

        String body = "{\"preferredLanguage\":\"xx\"}";

        mockMvc.perform(put("/api/v1/profile")
                        .header("Authorization", bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
