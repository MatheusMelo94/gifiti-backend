package com.gifiti.api.integration.i18n;

import com.gifiti.api.dto.request.RegisterRequest;
import com.gifiti.api.dto.response.ErrorResponse;
import com.gifiti.api.integration.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Group B — Validation message localization (spec criteria #5, #6, #7) plus
 * the F-4 no-key-leak guard (HIGH severity).
 *
 * <p>Covers TC-07 through TC-11 from the QA test plan. Spot-checks one
 * annotation per representative DTO; per-DTO exhaustive coverage lives in
 * {@code DtoValidationLocalizationTest} (Task 6 unit). The F-4 test pins the
 * invariant that no validation response ever contains a literal {@code {} } or
 * {@code }}, which would indicate {@code LocalValidatorFactoryBean} lost its
 * {@code MessageSource} wiring (Risk #1 in plan).
 *
 * <p>Translation-agnostic: pt-BR assertions verify "differs from English bundle
 * AND no curly-brace leak". Task 12 fills in real Portuguese; assertions
 * survive that change.
 *
 * <p>Cite: {@code architecture-conventions.md § Engineering Workflow},
 * {@code § Testing}.
 */
class ValidationLocalizationIntegrationTest extends BaseIntegrationTest {

    private static final String EN_PASSWORD_SIZE_MSG = "Password must be 12-128 characters";
    private static final String EN_EMAIL_INVALID_MSG = "Email must be valid";
    private static final String EN_WISHLIST_TITLE_REQUIRED_MSG = "Title is required";
    private static final String EN_DISPLAYNAME_SIZE_MSG = "Display name must not exceed 50 characters";

    private MvcResult postRegister(String email, String password, String acceptLang) throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email(email)
                .password(password)
                .build();

        var builder = post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request));
        if (acceptLang != null) {
            builder = builder.header("Accept-Language", acceptLang);
        }
        return mockMvc.perform(builder)
                .andExpect(status().isBadRequest())
                .andReturn();
    }

    private ErrorResponse parseError(MvcResult result) throws Exception {
        return objectMapper.readValue(
                result.getResponse().getContentAsString(), ErrorResponse.class);
    }

    private String fieldMessage(ErrorResponse response, String field) {
        return response.getDetails().stream()
                .filter(d -> field.equals(d.getField()))
                .map(ErrorResponse.FieldError::getMessage)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No error for field: " + field));
    }

    @Test
    @DisplayName("TC-07: RegisterRequest @Size on password under pt-BR resolves pt-BR bundle, no key leak")
    void tc07_register_password_too_short_pt_BR() throws Exception {
        ErrorResponse body = parseError(
                postRegister("ok@example.test", "short", "pt-BR"));

        String passwordMsg = fieldMessage(body, "password");
        // Does not equal the English bundle value.
        assertThat(passwordMsg)
                .as("@Size on password resolves the pt-BR bundle")
                .isNotEqualTo(EN_PASSWORD_SIZE_MSG);
        // No curly-brace key leak (Risk #1 / F-4 guard).
        assertThat(passwordMsg)
                .as("Validator must not leak the literal {validation.*} key")
                .doesNotContain("{").doesNotContain("}");
    }

    @Test
    @DisplayName("TC-08: RegisterRequest @Size on password under en-US returns the byte-identical English value (#20)")
    void tc08_register_password_too_short_en_US() throws Exception {
        ErrorResponse body = parseError(
                postRegister("ok@example.test", "short", "en-US"));

        // "short" violates both @Size (<12 chars) and @Pattern (no uppercase
        // / no special). Both messages appear in details under field
        // "password"; we assert the @Size literal is present somewhere in the
        // password-field messages — that's the #20 byte-for-byte anchor for
        // this annotation. The Pattern message presence is incidental.
        assertThat(body.getDetails())
                .filteredOn(d -> "password".equals(d.getField()))
                .extracting(ErrorResponse.FieldError::getMessage)
                .as("@Size on password preserves byte-identical English literal under #20")
                .contains(EN_PASSWORD_SIZE_MSG);
    }

    @Test
    @DisplayName("TC-09: UpdateProfileRequest @Size on displayName under pt-BR (substitute for CreateWishlistRequest @NotBlank)")
    void tc09_profile_displayname_too_long_pt_BR() throws Exception {
        // Authenticated endpoint — wishlist creation also requires email
        // verification, so we use the profile-update path which only requires
        // a JWT. The annotation under test (@Size on a string) is identical to
        // what TC-09 originally targets on CreateWishlistRequest.title.
        String token = createUserAndGetToken("displayname-pt@example.test", "BlueP4nther$Xyz2!");

        // 51-char display name violates @Size(max=50).
        String body = "{\"displayName\":\"" + "a".repeat(51) + "\"}";

        MvcResult result = mockMvc.perform(put("/api/v1/profile")
                        .header("Authorization", bearerToken(token))
                        .header("Accept-Language", "pt-BR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andReturn();

        ErrorResponse error = parseError(result);
        String fieldMsg = fieldMessage(error, "displayName");
        assertThat(fieldMsg)
                .as("@Size pt-BR resolved")
                .isNotEqualTo(EN_DISPLAYNAME_SIZE_MSG)
                .doesNotContain("{").doesNotContain("}");
    }

    @Test
    @DisplayName("TC-10: RegisterRequest @Email rule under pt-BR resolves pt-BR bundle, no key leak")
    void tc10_register_email_invalid_pt_BR() throws Exception {
        ErrorResponse body = parseError(
                postRegister("not-an-email", "BlueP4nther$Xyz2!", "pt-BR"));

        String emailMsg = fieldMessage(body, "email");
        assertThat(emailMsg)
                .as("@Email pt-BR resolved")
                .isNotEqualTo(EN_EMAIL_INVALID_MSG)
                .doesNotContain("{").doesNotContain("}");
    }

    @Test
    @DisplayName("TC-11: UpdateProfileRequest preferredLanguage='xx' under en-US returns 400 (criterion #19)")
    void tc11_profile_preferredLanguage_unsupported() throws Exception {
        // Per Language.fromJsonTag, an unsupported BCP-47 tag throws
        // IllegalArgumentException at Jackson deserialization time, which
        // GlobalExceptionHandler maps to error.request.malformed (400).
        // Spec #19 is satisfied by 400 status; the implementation chose the
        // Jackson path over a @Pattern annotation, but the contract is the
        // same: unsupported value cannot mutate the user.
        String token = createUserAndGetToken("badlang@example.test", "BlueP4nther$Xyz2!");

        String body = "{\"preferredLanguage\":\"xx\"}";

        mockMvc.perform(put("/api/v1/profile")
                        .header("Authorization", bearerToken(token))
                        .header("Accept-Language", "en-US")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    /**
     * F-4 (MEDIUM, plan threat-model): no validation response ever contains a
     * literal {@code {} } or {@code }} — that would indicate
     * {@code LocalValidatorFactoryBean.setValidationMessageSource(...)} is no
     * longer wired through {@code I18nConfig}, leaking the bundle key as the
     * field message. Catches the regression loudly.
     *
     * <p>Spans 3 of the 13 request DTOs (RegisterRequest @Email + @Size,
     * UpdateProfileRequest @Size on displayName) — combined with the per-DTO
     * exhaustive {@code DtoValidationLocalizationTest} unit (which exercises
     * all 13 DTOs in the validator), this is the HTTP-boundary guard.
     */
    @Test
    @DisplayName("F-4: validation responses never contain '{' or '}' — LocalValidatorFactoryBean wiring guard")
    void f4_no_curly_brace_leak_in_any_validation_response() throws Exception {
        // Bad password — exercises @Size + @Pattern + @NotBlank combinations.
        ErrorResponse pwd = parseError(postRegister("ok2@example.test", "x", "pt-BR"));
        for (ErrorResponse.FieldError fe : pwd.getDetails()) {
            assertThat(fe.getMessage())
                    .as("RegisterRequest violation '%s' must be bundle-resolved, not key-leaked", fe.getField())
                    .doesNotContain("{").doesNotContain("}");
        }

        // Bad email — exercises @Email.
        ErrorResponse mail = parseError(postRegister("not-an-email", "BlueP4nther$Xyz2!", "pt-BR"));
        for (ErrorResponse.FieldError fe : mail.getDetails()) {
            assertThat(fe.getMessage())
                    .as("RegisterRequest @Email violation must be bundle-resolved")
                    .doesNotContain("{").doesNotContain("}");
        }

        // UpdateProfileRequest @Size on displayName.
        String token = createUserAndGetToken("f4-guard@example.test", "BlueP4nther$Xyz2!");
        MvcResult result = mockMvc.perform(put("/api/v1/profile")
                        .header("Authorization", bearerToken(token))
                        .header("Accept-Language", "pt-BR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"" + "x".repeat(51) + "\"}"))
                .andExpect(status().isBadRequest())
                .andReturn();
        ErrorResponse profile = parseError(result);
        for (ErrorResponse.FieldError fe : profile.getDetails()) {
            assertThat(fe.getMessage())
                    .as("UpdateProfileRequest violation must be bundle-resolved")
                    .doesNotContain("{").doesNotContain("}");
        }
    }
}
