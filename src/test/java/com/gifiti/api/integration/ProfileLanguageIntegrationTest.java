package com.gifiti.api.integration;

import com.gifiti.api.integration.support.CapturingEmailService;
import com.gifiti.api.integration.support.CapturingEmailService.CapturedEmail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration tests for Task 9 of {@code 005-i18n-backend-support}:
 * the self-service language preference flow on
 * {@code PUT /api/v1/profile}.
 *
 * <p>Covers spec criteria:
 * <ul>
 *   <li>#18: an authenticated user can change {@code preferredLanguage} via
 *       the existing profile-update endpoint, the User document is updated,
 *       and subsequent emails to that user use the new language.</li>
 *   <li>#19: setting {@code preferredLanguage} to an unsupported value (e.g.,
 *       {@code "fr-FR"}) yields a 400 response.</li>
 * </ul>
 *
 * <p>Lives in {@code com.gifiti.api.integration.*}, which the CI workflow
 * temporarily excludes via
 * {@code -Dtest='!com.gifiti.api.integration.*Test'}.
 * Engineer runs this explicitly for verification; full re-inclusion happens
 * in Task 11.</p>
 */
class ProfileLanguageIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CapturingEmailService capturingEmailService;

    @BeforeEach
    void clearCapturedEmails() {
        capturingEmailService.clear();
    }

    @Test
    @DisplayName("PUT /profile with preferredLanguage=pt-BR persists and switches subsequent emails to pt-BR")
    void put_profile_with_valid_preferredLanguage_persists_it() throws Exception {
        // Register an English-speaking user (no Accept-Language header).
        String token = createUserAndGetToken("lang@example.com", "SecurePass123!");

        // Drop the verification email captured during registration so the
        // assertion below counts only the resend-verification triggered AFTER
        // the language change.
        capturingEmailService.clear();

        // Switch the user's preferredLanguage to pt-BR via the existing
        // profile-update endpoint.
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

        // Trigger a fresh email through the existing resend-verification
        // path. Spec criterion #18: this email must be in pt-BR because the
        // user's stored preference now wins (recipient-language rule).
        // No Accept-Language header sent — the user's stored preference is
        // the only signal driving the email language.
        mockMvc.perform(post("/api/v1/auth/resend-verification")
                        .header("Authorization", bearerToken(token)))
                .andExpect(status().isOk());

        List<CapturedEmail> sent = capturingEmailService.sent();
        assertThat(sent)
                .as("resend-verification must trigger exactly one email")
                .hasSize(1);

        CapturedEmail email = sent.get(0);
        assertThat(email.to()).isEqualTo("lang@example.com");
        assertThat(email.body())
                .as("body language matches the user's NEW stored preference (pt-BR), not en-US")
                .contains("<html lang=\"pt-BR\">")
                .contains("[TODO pt-BR]");
    }

    @Test
    @DisplayName("PUT /profile with unsupported preferredLanguage returns 400 (spec criterion #19)")
    void put_profile_with_unsupported_preferredLanguage_returns_400() throws Exception {
        String token = createUserAndGetToken("badlang@example.com", "SecurePass123!");

        // Spec criterion #19: any non-{en-US, pt-BR} value is rejected.
        // Jackson's @JsonCreator on Language throws IllegalArgumentException ->
        // InvalidFormatException -> HttpMessageNotReadableException ->
        // GlobalExceptionHandler -> 400 (error.request.malformed).
        String body = "{\"preferredLanguage\":\"fr-FR\"}";

        mockMvc.perform(put("/api/v1/profile")
                        .header("Authorization", bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /profile with garbage preferredLanguage returns 400")
    void put_profile_with_garbage_preferredLanguage_returns_400() throws Exception {
        String token = createUserAndGetToken("badlang2@example.com", "SecurePass123!");

        String body = "{\"preferredLanguage\":\"xx\"}";

        mockMvc.perform(put("/api/v1/profile")
                        .header("Authorization", bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
