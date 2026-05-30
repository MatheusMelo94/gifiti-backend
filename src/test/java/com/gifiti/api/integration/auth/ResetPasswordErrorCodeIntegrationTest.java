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
 * Feature 009 / T12 integration tests — pins the two reset-password errorCodes
 * per ADR 0009 § 3.1 Bucket 1 endpoint mapping.
 *
 * <p>Tests:
 * <ul>
 *   <li>{@code invalidToken_returns401WithErrorCode} — unknown reset token →
 *       401 {@code INVALID_TOKEN}.</li>
 *   <li>{@code expiredToken_returns401WithErrorCode} — valid hash but expiry
 *       in the past → 401 {@code EXPIRED_TOKEN}.</li>
 * </ul>
 */
class ResetPasswordErrorCodeIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("unknown reset-password token → 401 with errorCode INVALID_TOKEN")
    void invalidToken_returns401WithErrorCode() throws Exception {
        String body = "{\"token\":\"never-existed\",\"newPassword\":\"BlueP4nther$NEW2!\"}";

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("expired reset-password token → 401 with errorCode EXPIRED_TOKEN")
    void expiredToken_returns401WithErrorCode() throws Exception {
        registerTestUser("reset-expired@example.test", "BlueP4nther$Xyz2!");

        String plaintext = UUID.randomUUID().toString();
        String hashed = sha256(plaintext);
        mongoTemplate.updateFirst(
                new Query(Criteria.where("email").is("reset-expired@example.test")),
                new Update()
                        .set("passwordResetToken", hashed)
                        .set("passwordResetTokenExpiry", Instant.now().minus(1, ChronoUnit.HOURS)),
                User.class);

        String body = String.format(
                "{\"token\":\"%s\",\"newPassword\":\"BlueP4nther$NEW2!\"}",
                plaintext);

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("EXPIRED_TOKEN"));
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
