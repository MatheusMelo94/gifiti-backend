package com.gifiti.api.integration.auth;

import com.gifiti.api.integration.BaseIntegrationTest;
import com.gifiti.api.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Feature 009 / T12+T15 integration tests — pins the three verify-email
 * errorCodes per ADR 0009 § 3.1 Bucket 1 endpoint mapping.
 *
 * <p>Tests:
 * <ul>
 *   <li>{@code invalidToken_returns401WithErrorCode} — unknown token → 401
 *       {@code INVALID_TOKEN}.</li>
 *   <li>{@code expiredToken_returns401WithErrorCode} — valid hash but
 *       expiry in the past → 401 {@code EXPIRED_TOKEN}.</li>
 *   <li>{@code alreadyVerifiedToken_returns409WithErrorCode} — re-clicked
 *       verification link → 409 {@code ALREADY_VERIFIED} (NEW behavior per
 *       user Q2 = YES on 2026-05-30; previously returned INVALID_TOKEN).</li>
 * </ul>
 *
 * <p>The Q2 = YES architectural shape (ADR 0009 Open Q2 → shape (a)) is
 * implemented at T15 by retaining the verification token hash on the User
 * document after successful verification, so the {@code verifyEmail} lookup
 * still finds the user and the new {@code AlreadyVerifiedException} branch
 * can fire.
 */
class VerifyEmailErrorCodeIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("unknown verification token → 401 with errorCode INVALID_TOKEN")
    void invalidToken_returns401WithErrorCode() throws Exception {
        String body = "{\"token\":\"definitely-not-a-real-token\"}";

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("expired verification token → 401 with errorCode EXPIRED_TOKEN")
    void expiredToken_returns401WithErrorCode() throws Exception {
        registerTestUser("expired-token@example.test", "BlueP4nther$Xyz2!");

        // Seed a known plaintext token, store its hash on the user, set expiry in the past.
        String plaintext = UUID.randomUUID().toString();
        String hashed = sha256(plaintext);
        mongoTemplate.updateFirst(
                new Query(Criteria.where("email").is("expired-token@example.test")),
                new Update()
                        .set("verificationToken", hashed)
                        .set("verificationTokenExpiry", Instant.now().minus(1, ChronoUnit.HOURS))
                        .set("emailVerified", false),
                User.class);

        String body = String.format("{\"token\":\"%s\"}", plaintext);

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("EXPIRED_TOKEN"));
    }

    @Test
    @DisplayName("re-clicked verification link → 409 with errorCode ALREADY_VERIFIED (user Q2 = YES)")
    void alreadyVerifiedToken_returns409WithErrorCode() throws Exception {
        registerTestUser("rever@example.test", "BlueP4nther$Xyz2!");

        // Seed a known token and verify the user. Per Q2=YES (shape a),
        // verifyEmail no longer nulls verificationToken after success — it
        // retains the hash so a re-clicked link can be resolved as
        // ALREADY_VERIFIED instead of INVALID_TOKEN.
        String plaintext = UUID.randomUUID().toString();
        String hashed = sha256(plaintext);
        mongoTemplate.updateFirst(
                new Query(Criteria.where("email").is("rever@example.test")),
                new Update()
                        .set("verificationToken", hashed)
                        .set("verificationTokenExpiry", Instant.now().plus(1, ChronoUnit.HOURS))
                        .set("emailVerified", false),
                User.class);

        String body = String.format("{\"token\":\"%s\"}", plaintext);

        // First verify — succeeds, marks emailVerified=true, retains the hash.
        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        // Second verify (re-clicked link) — same token, but user already verified.
        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ALREADY_VERIFIED"));
    }

    private String sha256(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
