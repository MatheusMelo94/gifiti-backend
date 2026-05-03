package com.gifiti.api.integration.i18n;

import com.gifiti.api.dto.request.ForgotPasswordRequest;
import com.gifiti.api.dto.request.RegisterRequest;
import com.gifiti.api.integration.BaseIntegrationTest;
import com.gifiti.api.model.User;
import com.gifiti.api.model.enums.Language;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Group D — Success message localization (spec criteria #10, #11).
 *
 * <p>Covers TC-18 through TC-20. {@code RegisterResponse.message} carries a
 * {@link com.gifiti.api.dto.i18n.LocalizedMessage}; the registered Jackson
 * serializer resolves it via {@code MessageSource} +
 * {@code LocaleContextHolder.getLocale()} (set by {@code GifitiLocaleResolver})
 * at JSON-write time. The on-wire shape stays a plain JSON String — never a
 * nested object.
 *
 * <p>TC-20 is the load-bearing test for the request-locale-vs-stored-preference
 * distinction in the forgot-password flow (plan Risk #2 contract): the response
 * body follows the request locale, while the email follows stored preference
 * (asserted in {@code EmailLocalizationIntegrationTest}).
 *
 * <p>Cite: {@code architecture-conventions.md § Engineering Workflow},
 * {@code § Testing}, {@code § API Contracts}.
 */
class SuccessMessageLocalizationIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("TC-18: RegisterResponse.message under pt-BR resolved from pt-BR bundle, wire shape unchanged")
    void tc18_register_response_pt_BR() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("ptbr-success@example.test")
                .password("BlueP4nther$Xyz2!")
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .header("Accept-Language", "pt-BR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("ptbr-success@example.test"))
                // Translation-agnostic: pt-BR resolved → not the English literal.
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.not(
                                "Registration successful. Please check your email to verify your account.")))
                .andReturn();

        // LocalizedMessage serializer contract: wire shape is a plain String,
        // no messageKey leak, no nested object replacing the field.
        String body = result.getResponse().getContentAsString();
        assertThat(body)
                .as("messageKey must never leak into the JSON wire format")
                .doesNotContain("messageKey")
                .doesNotContain("auth.register.success");
    }

    @Test
    @DisplayName("TC-19: forgot-password MessageResponse under pt-BR resolved from pt-BR bundle")
    void tc19_message_response_pt_BR() throws Exception {
        // Use forgot-password rather than profile-update because PUT /profile
        // returns a ProfileResponse, not a MessageResponse. Forgot-password is
        // the canonical MessageResponse endpoint and exercises the exact same
        // LocalizedMessage path.
        ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                .email("any.user@example.test")
                .build();

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .header("Accept-Language", "pt-BR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.not(
                                "If an account exists with this email, a password reset link has been sent.")));
    }

    @Test
    @DisplayName("TC-20: forgot-password ack follows REQUEST locale, not stored preference (Risk #2)")
    void tc20_anti_enum_ack_follows_request_locale() throws Exception {
        // Seed an existing PT_BR user. Forgot-password ack still follows the
        // REQUEST locale (en-US here) because the message goes back over the
        // wire to the requestor — who may not be the account owner. Spec
        // criterion #11 + plan § Component 7.
        registerTestUser("stored-pt-user@example.test", "BlueP4nther$Xyz2!");
        mongoTemplate.updateFirst(
                new Query(Criteria.where("email").is("stored-pt-user@example.test")),
                new Update().set("preferredLanguage", Language.PT_BR),
                User.class);

        ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                .email("stored-pt-user@example.test")
                .build();

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .header("Accept-Language", "en-US")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                // Request-locale wins for response message; stored pt-BR does
                // NOT bleed into the wire response. The email body separately
                // follows stored preference — see EmailLocalizationIntegrationTest TC-23.
                .andExpect(jsonPath("$.message").value(
                        "If an account exists with this email, a password reset link has been sent."));
    }
}
