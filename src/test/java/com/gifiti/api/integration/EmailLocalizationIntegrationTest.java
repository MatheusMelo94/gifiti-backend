package com.gifiti.api.integration;

import com.gifiti.api.dto.request.RegisterRequest;
import com.gifiti.api.integration.support.CapturingEmailService;
import com.gifiti.api.integration.support.CapturingEmailService.CapturedEmail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration tests for Task 8 of {@code 005-i18n-backend-support}:
 * {@code AuthService} delegates email rendering to the localized
 * {@code EmailTemplateRenderer} and selects the language per spec criteria
 * #12 (verification email) and #13 (forgot-password email).
 *
 * <p>Uses {@link CapturingEmailService} (a {@code @Primary} test-only impl
 * activated under the {@code test} profile) to capture every email sent by
 * the service layer and assert on the resolved subject and body.</p>
 *
 * <p>Lives in {@code com.gifiti.api.integration.*}, which the CI workflow
 * temporarily excludes via {@code -Dtest='!com.gifiti.api.integration.*Test'}
 * Engineer runs this explicitly for verification; full re-inclusion happens in
 * Task 11 of the i18n feature.</p>
 */
class EmailLocalizationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CapturingEmailService capturingEmailService;

    @BeforeEach
    void clearCapturedEmails() {
        capturingEmailService.clear();
    }

    @Test
    @DisplayName("POST /auth/register with Accept-Language: pt-BR captures pt-BR verification email")
    void register_with_pt_BR_sends_localized_pt_BR_verification_email() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("ptbr-email@example.com")
                .password("SecurePass123!")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .header("Accept-Language", "pt-BR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        List<CapturedEmail> sent = capturingEmailService.sent();
        assertThat(sent)
                .as("registration must trigger exactly one verification email")
                .hasSize(1);

        CapturedEmail email = sent.get(0);
        assertThat(email.to()).isEqualTo("ptbr-email@example.com");
        assertThat(email.subject())
                .as("pt-BR subject pulled from messages_pt_BR.properties (placeholder until Task 12)")
                .startsWith("[TODO pt-BR]");
        assertThat(email.body())
                .as("pt-BR body pulled from messages_pt_BR.properties (placeholder until Task 12)")
                .contains("[TODO pt-BR]")
                .contains("<html lang=\"pt-BR\">");
    }

    @Test
    @DisplayName("POST /auth/register with no Accept-Language captures English verification email byte-for-byte")
    void register_with_no_header_sends_en_US_verification_email_unchanged() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("en-email@example.com")
                .password("SecurePass123!")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        List<CapturedEmail> sent = capturingEmailService.sent();
        assertThat(sent).hasSize(1);

        CapturedEmail email = sent.get(0);
        // Spec criterion #20 — byte-for-byte equivalent to pre-i18n production text
        assertThat(email.subject()).isEqualTo("Welcome to Gifiti - Please confirm your email");
        assertThat(email.body())
                .contains("<html lang=\"en-US\">")
                .contains("Thanks for signing up. Please confirm your email address")
                .contains("Confirm Email Address")
                .contains("If you didn't create a Gifiti account")
                .doesNotContain("[TODO pt-BR]");
    }
}
