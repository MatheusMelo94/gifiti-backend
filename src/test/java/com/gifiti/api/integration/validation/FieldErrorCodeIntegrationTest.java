package com.gifiti.api.integration.validation;

import com.gifiti.api.dto.request.RegisterRequest;
import com.gifiti.api.integration.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Feature 009 / T8 — End-to-end pinning of {@code FieldError.errorCode}
 * emission from {@link com.gifiti.api.exception.GlobalExceptionHandler} when
 * Jakarta Validation surfaces field-level errors.
 *
 * <p>Each test triggers a {@code MethodArgumentNotValidException} via the
 * {@code POST /api/v1/auth/register} endpoint with a request body that
 * violates a specific Jakarta annotation, then asserts the
 * {@code details[N].errorCode} comes back as the inventory value mapped by
 * {@link com.gifiti.api.util.FieldErrorCodeMapper} (T7).
 *
 * <p>Convention citations:
 * <ul>
 *   <li>{@code architecture-conventions.md § Testing} — integration tests own
 *       the wire-format contract for new response fields.</li>
 *   <li>ADR 0009 § Decision D2 — inventory values are stable; failures here
 *       indicate either a regression in the handler wiring or an unsanctioned
 *       inventory change.</li>
 * </ul>
 */
class FieldErrorCodeIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("POST /auth/register with blank email → details[].errorCode = REQUIRED")
    void blankEmail_yields_REQUIRED() throws Exception {
        // password is intentionally well-formed so the only violation is email NotBlank.
        String body = "{\"email\":\"\",\"password\":\"BlueP4nther$Xyz2!\"}";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[?(@.field=='email')].errorCode")
                        .value(org.hamcrest.Matchers.hasItem("REQUIRED")));
    }

    @Test
    @DisplayName("POST /auth/register with malformed email → details[].errorCode = INVALID_FORMAT")
    void malformedEmail_yields_INVALID_FORMAT() throws Exception {
        // "abc" is non-blank so NotBlank passes; @Email then fires.
        String body = "{\"email\":\"abc\",\"password\":\"BlueP4nther$Xyz2!\"}";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[?(@.field=='email')].errorCode")
                        .value(org.hamcrest.Matchers.hasItem("INVALID_FORMAT")));
    }

    @Test
    @DisplayName("POST /auth/register with too-short password → details[].errorCode = TOO_SHORT")
    void tooShortPassword_yields_TOO_SHORT() throws Exception {
        // 5 chars vs required 12–128 → @Size(min=12) violation.
        String body = "{\"email\":\"valid@example.test\",\"password\":\"Abc1!\"}";

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[?(@.field=='password')].errorCode")
                        .value(org.hamcrest.Matchers.hasItem("TOO_SHORT")))
                .andReturn();

        // Sanity: details must carry the field name + the discriminator together
        String json = result.getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(json)
                .contains("\"errorCode\":\"TOO_SHORT\"");
    }

    @Test
    @DisplayName("POST /auth/register with too-long display name → details[].errorCode = TOO_LONG")
    void tooLongDisplayName_yields_TOO_LONG() throws Exception {
        // displayName Size cap is 50; send 60 chars.
        String tooLongName = "a".repeat(60);
        RegisterRequest req = RegisterRequest.builder()
                .email("displaylong@example.test")
                .password("BlueP4nther$Xyz2!")
                .displayName(tooLongName)
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[?(@.field=='displayName')].errorCode")
                        .value(org.hamcrest.Matchers.hasItem("TOO_LONG")));
    }
}
