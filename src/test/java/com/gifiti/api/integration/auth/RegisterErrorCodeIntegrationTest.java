package com.gifiti.api.integration.auth;

import com.gifiti.api.dto.request.RegisterRequest;
import com.gifiti.api.integration.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Feature 009 / T12+T13 integration tests — pins the two register-failure
 * errorCodes per ADR 0009 § 3.1 Bucket 1 endpoint mapping.
 *
 * <p>Tests:
 * <ul>
 *   <li>{@code duplicateEmail_returns409WithErrorCode} — second register on
 *       same email → 409 {@code EMAIL_ALREADY_REGISTERED} (Shape A per
 *       Decision D — no details[] entry).</li>
 *   <li>{@code weakPassword_returns400WithErrorCode} — password matching one
 *       of {@code PasswordValidationService}'s 4 rules → 400
 *       {@code WEAK_PASSWORD} (generic per Decision G).</li>
 * </ul>
 */
class RegisterErrorCodeIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("duplicate email → 409 with errorCode EMAIL_ALREADY_REGISTERED")
    void duplicateEmail_returns409WithErrorCode() throws Exception {
        registerTestUser("dup@example.test", "BlueP4nther$Xyz2!");

        RegisterRequest req = RegisterRequest.builder()
                .email("dup@example.test")
                .password("BlueP4nther$Other2!")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    @DisplayName("weak password (common pattern) → 400 with errorCode WEAK_PASSWORD")
    void weakPassword_returns400WithErrorCode() throws Exception {
        // "password" hits PasswordValidationService's common-pattern rule.
        // The Jakarta-level @Size + @Pattern still need to pass (12-128 chars,
        // upper+lower+digit+special), so we construct a password that passes
        // the regex but trips the service-layer common-pattern check.
        // "MyPassword123!" contains the common-pattern substring "password".
        RegisterRequest req = RegisterRequest.builder()
                .email("weak-pw@example.test")
                .password("MyPassword123!")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("WEAK_PASSWORD"));
    }
}
