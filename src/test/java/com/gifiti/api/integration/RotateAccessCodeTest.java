package com.gifiti.api.integration;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.gifiti.api.dto.request.CreateWishlistRequest;
import com.gifiti.api.model.enums.Visibility;
import com.gifiti.api.service.WishlistService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@code POST /api/v1/wishlists/{id}/rotate-access-code}
 * (feature 008 / T11).
 *
 * <p>Cases:
 * <ul>
 *   <li>Owner rotates PRIVATE → 200 with new access code (different from
 *       previous).</li>
 *   <li>Owner rotates PUBLIC → 400 with errorCode
 *       {@code PUBLIC_WISHLIST_HAS_NO_ACCESS_CODE}.</li>
 *   <li>Non-owner rotates → 404 (per Security recommendation: 404 for
 *       both not-found and not-owner to preserve IDOR-resistance — matches
 *       rotateShareableId precedent and the user's 2026-05-30 ratification
 *       of plan §4.3 vs §T13 contradiction).</li>
 *   <li>Nonexistent wishlistId → 404.</li>
 *   <li>Unauthenticated → 401 (existing security filter).</li>
 *   <li>{@code SECURITY_EVENT: access code rotated} INFO log on success,
 *       carrying wishlistId + userId only — NEVER the code value (Security
 *       findings F-5).</li>
 * </ul>
 */
class RotateAccessCodeTest extends BaseIntegrationTest {

    private String ownerToken;
    private String otherUserToken;

    private ListAppender<ILoggingEvent> logAppender;
    private Logger serviceLogger;

    @BeforeEach
    void setup() throws Exception {
        ownerToken = createVerifiedUserAndGetToken("rotate-owner@example.test", "Mvn-Build-Cyan-Glow-2026!");
        otherUserToken = createVerifiedUserAndGetToken("rotate-other@example.test", "Mvn-Build-Cyan-Glow-2026!");

        serviceLogger = (Logger) LoggerFactory.getLogger(WishlistService.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        serviceLogger.addAppender(logAppender);
    }

    @AfterEach
    void teardownLogger() {
        serviceLogger.detachAppender(logAppender);
    }

    @Test
    @DisplayName("owner rotates PRIVATE wishlist → 200 with NEW accessCode")
    void ownerRotatesPrivateWishlistReturnsNewCode() throws Exception {
        var ids = createWishlist("Secret", Visibility.PRIVATE);
        String previousCode = ids.accessCode();

        MvcResult result = mockMvc.perform(post("/api/v1/wishlists/" + ids.wishlistId() + "/rotate-access-code")
                        .header("Authorization", bearerToken(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ids.wishlistId()))
                .andExpect(jsonPath("$.visibility").value("PRIVATE"))
                .andExpect(jsonPath("$.accessCode").exists())
                .andReturn();

        String newCode = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessCode").asText();

        // Per ADR 0008 § Decision F: 4-digit numeric.
        assertThat(newCode).matches("^\\d{4}$");
        // Statistical: 1/10000 chance of collision. Acceptable for an
        // integration test; if this becomes flaky, retry up to N times.
        assertThat(newCode).isNotEqualTo(previousCode);
    }

    @Test
    @DisplayName("owner rotates PUBLIC wishlist → 400 PUBLIC_WISHLIST_HAS_NO_ACCESS_CODE")
    void ownerRotatesPublicWishlistReturns400() throws Exception {
        var ids = createWishlist("Public list", Visibility.PUBLIC);

        mockMvc.perform(post("/api/v1/wishlists/" + ids.wishlistId() + "/rotate-access-code")
                        .header("Authorization", bearerToken(ownerToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PUBLIC_WISHLIST_HAS_NO_ACCESS_CODE"));
    }

    @Test
    @DisplayName("non-owner rotates → 404 (IDOR-resistance, matches rotateShareableId precedent)")
    void nonOwnerRotateReturns404() throws Exception {
        var ids = createWishlist("Secret", Visibility.PRIVATE);

        mockMvc.perform(post("/api/v1/wishlists/" + ids.wishlistId() + "/rotate-access-code")
                        .header("Authorization", bearerToken(otherUserToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("nonexistent wishlistId → 404")
    void nonexistentWishlistReturns404() throws Exception {
        mockMvc.perform(post("/api/v1/wishlists/nonexistent-id/rotate-access-code")
                        .header("Authorization", bearerToken(ownerToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("unauthenticated rotate → 401")
    void unauthenticatedRotateReturns401() throws Exception {
        var ids = createWishlist("Secret", Visibility.PRIVATE);

        mockMvc.perform(post("/api/v1/wishlists/" + ids.wishlistId() + "/rotate-access-code"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("successful rotation emits SECURITY_EVENT log line — without the code value (Security F-5)")
    void successfulRotationEmitsSecurityEventLog() throws Exception {
        var ids = createWishlist("Secret", Visibility.PRIVATE);
        String previousCode = ids.accessCode();

        MvcResult result = mockMvc.perform(post("/api/v1/wishlists/" + ids.wishlistId() + "/rotate-access-code")
                        .header("Authorization", bearerToken(ownerToken)))
                .andExpect(status().isOk())
                .andReturn();
        String newCode = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessCode").asText();

        // Assert the SECURITY_EVENT log line was emitted with wishlistId.
        boolean found = logAppender.list.stream().anyMatch(event -> {
            String msg = event.getFormattedMessage();
            return msg.contains("SECURITY_EVENT")
                    && msg.contains("access code rotated")
                    && msg.contains(ids.wishlistId());
        });
        assertThat(found)
                .as("expected SECURITY_EVENT log line on rotateAccessCode success")
                .isTrue();

        // Security findings F-5: NO log line may contain the new code or
        // the old code.
        for (ILoggingEvent event : logAppender.list) {
            assertThat(event.getFormattedMessage())
                    .as("log line must not contain the new access code")
                    .doesNotContain(newCode);
            assertThat(event.getFormattedMessage())
                    .as("log line must not contain the previous access code")
                    .doesNotContain(previousCode);
        }
    }

    // --- helpers -------------------------------------------------------

    private record WishlistIds(String wishlistId, String shareableId, String accessCode) {}

    private WishlistIds createWishlist(String title, Visibility visibility) throws Exception {
        CreateWishlistRequest request = CreateWishlistRequest.builder()
                .title(title)
                .visibility(visibility)
                .build();

        MvcResult result = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/v1/wishlists")
                                .header("Authorization", bearerToken(ownerToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        var json = objectMapper.readTree(result.getResponse().getContentAsString());
        String code = json.has("accessCode") && !json.get("accessCode").isNull()
                ? json.get("accessCode").asText()
                : null;
        return new WishlistIds(
                json.get("id").asText(),
                json.get("shareableId").asText(),
                code);
    }
}
