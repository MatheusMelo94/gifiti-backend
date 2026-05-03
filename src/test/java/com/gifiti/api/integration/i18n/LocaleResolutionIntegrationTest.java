package com.gifiti.api.integration.i18n;

import com.gifiti.api.dto.request.ForgotPasswordRequest;
import com.gifiti.api.dto.response.ErrorResponse;
import com.gifiti.api.integration.BaseIntegrationTest;
import com.gifiti.api.model.User;
import com.gifiti.api.model.enums.Language;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Group A — Locale resolution precedence (spec criteria #1-4).
 *
 * <p>Covers TC-01 through TC-06 from
 * {@code specs/005-i18n-backend-support/test-plan.md}. Asserts the
 * {@link com.gifiti.api.config.GifitiLocaleResolver} chain:
 *
 * <ol>
 *   <li>{@code Accept-Language} header (when supported) wins.</li>
 *   <li>Authenticated user's stored {@code preferredLanguage} otherwise.</li>
 *   <li>{@code en-US} default fallback.</li>
 * </ol>
 *
 * <p>Translation-agnostic strategy: pt-BR assertions check that the resolved
 * value differs from the en-US bundle value AND is non-blank — never that it
 * matches a specific Portuguese string. Task 12 fills in real translations;
 * these tests survive that change.
 *
 * <p>Cite: {@code architecture-conventions.md § Engineering Workflow},
 * {@code § Testing}.
 */
class LocaleResolutionIntegrationTest extends BaseIntegrationTest {

    /**
     * Insert a verified user with the given stored preferredLanguage so we can
     * authenticate and observe how the resolver chain reads it. Uses
     * MongoTemplate directly to keep the fixture explicit (no register-then-mutate
     * round-trip).
     */
    private void seedVerifiedUser(String email, String password, Language language) throws Exception {
        registerTestUser(email, password);
        markEmailVerified(email);
        // Force the stored preferredLanguage explicitly (registration would
        // default to EN_US under no Accept-Language header).
        mongoTemplate.updateFirst(
                new Query(Criteria.where("email").is(email)),
                new org.springframework.data.mongodb.core.query.Update()
                        .set("preferredLanguage", language),
                User.class);
    }

    @Test
    @DisplayName("TC-01: authenticated PT_BR user, no Accept-Language header → response in pt-BR")
    void tc01_stored_pt_BR_no_header_returns_pt_BR() throws Exception {
        String password = "BlueP4nther$Xyz2!";
        seedVerifiedUser("user.pt@example.test", password, Language.PT_BR);
        String token = loginAndGetToken("user.pt@example.test", password);

        MvcResult result = mockMvc.perform(get("/api/v1/wishlists/000000000000000000000000")
                        .header("Authorization", bearerToken(token)))
                .andExpect(status().isNotFound())
                .andReturn();

        ErrorResponse body = objectMapper.readValue(
                result.getResponse().getContentAsString(), ErrorResponse.class);

        // Translation-agnostic: value differs from en-US, and (today) starts with
        // the [TODO pt-BR] placeholder marker. Task 12 replaces the placeholder
        // with real Portuguese; the inequality assertion still holds.
        assertThat(body.getMessage())
                .as("pt-BR bundle resolved")
                .isNotEqualTo("Wishlist not found with id: '000000000000000000000000'");
        assertThat(body.getMessage())
                .as("ResourceNotFoundException carries resource + field args identically across bundles")
                .contains("Wishlist")
                .contains("000000000000000000000000");
    }

    @Test
    @DisplayName("TC-02: authenticated EN_US user, no Accept-Language → en-US (#20 anchor)")
    void tc02_stored_en_US_no_header_returns_en_US() throws Exception {
        String password = "BlueP4nther$Xyz2!";
        seedVerifiedUser("user.en@example.test", password, Language.EN_US);
        String token = loginAndGetToken("user.en@example.test", password);

        MvcResult result = mockMvc.perform(get("/api/v1/wishlists/000000000000000000000001")
                        .header("Authorization", bearerToken(token)))
                .andExpect(status().isNotFound())
                .andReturn();

        ErrorResponse body = objectMapper.readValue(
                result.getResponse().getContentAsString(), ErrorResponse.class);

        // Spec #20 byte-for-byte anchor against pre-i18n English literal.
        assertThat(body.getMessage())
                .isEqualTo("Wishlist not found with id: '000000000000000000000001'");
    }

    @Test
    @DisplayName("TC-03: stored EN_US user with Accept-Language: pt-BR → pt-BR (header wins) and User doc unchanged")
    void tc03_header_overrides_stored_preference() throws Exception {
        String email = "user.en.override@example.test";
        String password = "BlueP4nther$Xyz2!";
        seedVerifiedUser(email, password, Language.EN_US);
        String token = loginAndGetToken(email, password);

        MvcResult result = mockMvc.perform(get("/api/v1/wishlists/000000000000000000000002")
                        .header("Authorization", bearerToken(token))
                        .header("Accept-Language", "pt-BR"))
                .andExpect(status().isNotFound())
                .andReturn();

        ErrorResponse body = objectMapper.readValue(
                result.getResponse().getContentAsString(), ErrorResponse.class);

        // pt-BR bundle resolved (header wins).
        assertThat(body.getMessage())
                .as("pt-BR header overrides stored EN_US")
                .isNotEqualTo("Wishlist not found with id: '000000000000000000000002'");

        // Negative: User document is NOT mutated by the override.
        User reread = mongoTemplate.findOne(
                new Query(Criteria.where("email").is(email)), User.class);
        assertThat(reread).isNotNull();
        assertThat(reread.getPreferredLanguage())
                .as("Accept-Language override is per-request; never mutates stored preference")
                .isEqualTo(Language.EN_US);
    }

    @Test
    @DisplayName("TC-04: unauthenticated request with Accept-Language: pt-BR → pt-BR")
    void tc04_unauthenticated_with_header_returns_pt_BR() throws Exception {
        ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                .email("unknown.user@example.test")
                .build();

        // Translation-agnostic: assert the message field (read via JSON path)
        // doesn't equal the English bundle value byte-for-byte. The pt-BR
        // placeholder today carries the English text as a substring after
        // the marker, so a contains-check would false-positive — equality is
        // the surviving guarantee both today and after Task 12.
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
    @DisplayName("TC-05: unauthenticated, no Accept-Language → en-US (#20 anchor)")
    void tc05_unauthenticated_no_header_returns_en_US() throws Exception {
        ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                .email("unknown.user.en@example.test")
                .build();

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        "If an account exists with this email, a password reset link has been sent."));
    }

    @Test
    @DisplayName("TC-06: unauthenticated with Accept-Language: fr-FR (unsupported) → en-US fallback")
    void tc06_unsupported_header_falls_back_to_en_US() throws Exception {
        ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                .email("unknown.user.fr@example.test")
                .build();

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .header("Accept-Language", "fr-FR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        "If an account exists with this email, a password reset link has been sent."));
    }
}
