package com.gifiti.api.integration.auth;

import com.gifiti.api.dto.request.LoginRequest;
import com.gifiti.api.dto.request.RegisterRequest;
import com.gifiti.api.integration.BaseIntegrationTest;
import com.gifiti.api.model.AccountLockout;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Feature 009 / T12+T14 integration tests — pins the three login-failure
 * errorCodes per ADR 0009 § 3.1 Bucket 1 endpoint mapping.
 *
 * <p>Tests:
 * <ul>
 *   <li>{@code invalidCredentials_returns401WithErrorCode} — wrong password →
 *       401 {@code INVALID_CREDENTIALS}.</li>
 *   <li>{@code lockedAccount_returns401WithErrorCode} — locked account →
 *       401 {@code ACCOUNT_LOCKED}.</li>
 *   <li>{@code unverifiedEmail_returns401WithErrorCode} — user Q1 = YES;
 *       verified=false → 401 {@code EMAIL_NOT_VERIFIED} (NEW business
 *       behavior per 2026-05-30 ratification).</li>
 *   <li>{@code verifiedUser_loginsSuccessfully} — happy-path regression to
 *       confirm Q1 didn't break verified-user logins.</li>
 * </ul>
 */
class LoginErrorCodeIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("invalid password → 401 with errorCode INVALID_CREDENTIALS")
    void invalidCredentials_returns401WithErrorCode() throws Exception {
        registerTestUser("wrong-pw@example.test", "BlueP4nther$Xyz2!");
        markEmailVerified("wrong-pw@example.test"); // Q1: must be verified for login to reach credentials check

        LoginRequest req = LoginRequest.builder()
                .email("wrong-pw@example.test")
                .password("BlueP4nther$WRONG2!")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("unknown email → 401 with errorCode INVALID_CREDENTIALS (anti-enumeration)")
    void unknownEmail_returns401WithErrorCode() throws Exception {
        // No registration — email is unknown. Per feature-005 anti-enumeration
        // discipline the discriminator + the message are identical to the
        // wrong-password case.
        LoginRequest req = LoginRequest.builder()
                .email("ghost@example.test")
                .password("BlueP4nther$Xyz2!")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("locked account → 401 with errorCode ACCOUNT_LOCKED")
    void lockedAccount_returns401WithErrorCode() throws Exception {
        // Pre-seed an active AccountLockout record. AccountLockoutService.isLocked
        // checks lockedUntil > now → return true.
        registerTestUser("locked@example.test", "BlueP4nther$Xyz2!");
        markEmailVerified("locked@example.test");
        AccountLockout lock = AccountLockout.builder()
                .email("locked@example.test")
                .failedAttempts(5)
                .lockedUntil(Instant.now().plus(30, ChronoUnit.MINUTES))
                .expiresAt(Instant.now().plus(31, ChronoUnit.MINUTES))
                .build();
        mongoTemplate.save(lock);

        LoginRequest req = LoginRequest.builder()
                .email("locked@example.test")
                .password("BlueP4nther$Xyz2!")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_LOCKED"));
    }

    @Test
    @DisplayName("unverified email + correct password → 401 with errorCode EMAIL_NOT_VERIFIED (user Q1 = YES)")
    void unverifiedEmail_returns401WithErrorCode() throws Exception {
        // Register; do NOT verify. Per user Q1 = YES (2026-05-30), login is
        // gated on user.isEmailVerified(); previously, login succeeded with
        // unverified-email users.
        registerTestUser("unverified@example.test", "BlueP4nther$Xyz2!");

        LoginRequest req = LoginRequest.builder()
                .email("unverified@example.test")
                .password("BlueP4nther$Xyz2!")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    @DisplayName("verified user with correct password → 200 (Q1 happy-path regression)")
    void verifiedUser_loginsSuccessfully() throws Exception {
        registerTestUser("happy@example.test", "BlueP4nther$Xyz2!");
        markEmailVerified("happy@example.test");

        LoginRequest req = LoginRequest.builder()
                .email("happy@example.test")
                .password("BlueP4nther$Xyz2!")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }
}
