package com.gifiti.api.integration;

import com.gifiti.api.dto.request.RegisterRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration tests for Task 7 of {@code 005-i18n-backend-support}:
 * success-message DTOs (notably {@code RegisterResponse.message}) carry a
 * {@code LocalizedMessage} that the registered Jackson serializer resolves via
 * {@code MessageSource} + {@code LocaleContextHolder.getLocale()} at write time.
 *
 * <p>The pt-BR bundle still contains placeholder strings (Task 12 fills in real
 * Portuguese), so the assertion is "the response carries the placeholder text,
 * which proves the locale plumbing chose the pt-BR bundle". The English path
 * asserts byte-for-byte equivalence with the pre-i18n production text per spec
 * criterion #20.</p>
 *
 * <p>Lives in {@code com.gifiti.api.integration.*}, which the CI workflow
 * temporarily excludes via {@code -Dtest='!com.gifiti.api.integration.*Test'}.
 * Engineer runs this explicitly for verification; full re-inclusion happens
 * in Task 11 of the i18n feature.</p>
 */
class SuccessMessageLocalizationIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("POST /auth/register with Accept-Language: pt-BR returns the pt-BR (placeholder) success message")
    void register_with_pt_BR_returns_localized_pt_BR_message() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("ptbr-user@example.com")
                .password("SecurePass123!")
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .header("Accept-Language", "pt-BR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("ptbr-user@example.com"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("[TODO pt-BR]")))
                .andReturn();

        // The wire shape is a plain JSON string for `message`, never a nested
        // object — Task 7 acceptance pins the LocalizedMessage serializer must
        // emit a string. Pin against accidental shape regressions.
        String body = result.getResponse().getContentAsString();
        assertThat(body)
                .as("messageKey must never leak into the JSON wire format")
                .doesNotContain("messageKey")
                .doesNotContain("auth.register.success");
    }

    @Test
    @DisplayName("POST /auth/register with no Accept-Language returns the English success message byte-for-byte")
    void register_with_no_header_returns_en_US_message_unchanged() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("en-user@example.com")
                .password("SecurePass123!")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("en-user@example.com"))
                .andExpect(jsonPath("$.message").value(
                        "Registration successful. Please check your email to verify your account."));
    }
}
