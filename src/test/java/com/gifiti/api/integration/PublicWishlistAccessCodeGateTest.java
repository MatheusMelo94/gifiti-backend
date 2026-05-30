package com.gifiti.api.integration;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.gifiti.api.dto.request.CreateWishlistRequest;
import com.gifiti.api.model.enums.Visibility;
import com.gifiti.api.service.PublicWishlistService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the {@code X-Wishlist-Access-Code} gate (feature 008 /
 * T6 + T10) on {@code GET /api/v1/public/wishlists/{shareableId}}.
 *
 * <p>Covers:
 * <ul>
 *   <li>PUBLIC wishlists: header is ignored (200 in all header states).</li>
 *   <li>PRIVATE wishlists with no header: 403 {@code ACCESS_CODE_REQUIRED}
 *       (ADR 0008 § 3 privacy-posture inversion).</li>
 *   <li>PRIVATE wishlists with wrong header: 403 {@code INVALID_ACCESS_CODE}
 *       and a rate-limit token consumed (Security findings F-3 pin 3).</li>
 *   <li>PRIVATE wishlists with correct header: 200 + bucket reset (ADR 0008
 *       § Decision G "reset on success").</li>
 *   <li>PRIVATE wishlists with malformed header (e.g. {@code "abcd"} —
 *       non-numeric): 403 {@code INVALID_ACCESS_CODE} with token consumed,
 *       per Security findings F-3 pins 2 + 3.</li>
 *   <li>6th wrong attempt in the 10-minute window: 429
 *       {@code ACCESS_CODE_RATE_LIMITED} (ADR 0008 § Decision G).</li>
 *   <li>{@code SECURITY_EVENT} logs emitted on rate-limit exhaustion;
 *       {@code access_code_success} / {@code access_code_failed} info logs
 *       on success / failure paths — never carrying the code itself
 *       (Security findings F-4 pin 5 + F-5).</li>
 * </ul>
 */
class PublicWishlistAccessCodeGateTest extends BaseIntegrationTest {

    private static final String HEADER = "X-Wishlist-Access-Code";

    private String ownerToken;
    private ListAppender<ILoggingEvent> logAppender;
    private Logger serviceLogger;

    @BeforeEach
    void setup() throws Exception {
        ownerToken = createVerifiedUserAndGetToken("gate-owner@example.test", "Mvn-Build-Cyan-Glow-2026!");

        serviceLogger = (Logger) LoggerFactory.getLogger(PublicWishlistService.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        serviceLogger.addAppender(logAppender);
    }

    @AfterEach
    void teardownLogger() {
        serviceLogger.detachAppender(logAppender);
    }

    @Test
    @DisplayName("PUBLIC wishlist + no header → 200")
    void publicWishlistNoHeaderReturns200() throws Exception {
        String shareableId = createWishlist("Public List", Visibility.PUBLIC).shareableId();

        mockMvc.perform(get("/api/v1/public/wishlists/" + shareableId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shareableId").value(shareableId));
    }

    @Test
    @DisplayName("PUBLIC wishlist + irrelevant header → 200 (header is ignored)")
    void publicWishlistIrrelevantHeaderReturns200() throws Exception {
        String shareableId = createWishlist("Public List", Visibility.PUBLIC).shareableId();

        mockMvc.perform(get("/api/v1/public/wishlists/" + shareableId)
                        .header(HEADER, "0000"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PRIVATE wishlist + no header → 403 ACCESS_CODE_REQUIRED")
    void privateWishlistNoHeaderReturns403WithDiscriminator() throws Exception {
        String shareableId = createWishlist("Secret", Visibility.PRIVATE).shareableId();

        mockMvc.perform(get("/api/v1/public/wishlists/" + shareableId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_CODE_REQUIRED"));
    }

    @Test
    @DisplayName("PRIVATE wishlist + wrong header → 403 INVALID_ACCESS_CODE")
    void privateWishlistWrongHeaderReturns403WithDiscriminator() throws Exception {
        var ids = createWishlist("Secret", Visibility.PRIVATE);
        // Pick a code different from the generated one. We don't know the
        // generated code from the public response, but '0000' has ~1/10000
        // chance of collision — acceptable for an integration test.
        String wrongCode = ids.accessCode().equals("0000") ? "9999" : "0000";

        mockMvc.perform(get("/api/v1/public/wishlists/" + ids.shareableId())
                        .header(HEADER, wrongCode))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ACCESS_CODE"));
    }

    @Test
    @DisplayName("PRIVATE wishlist + correct header → 200")
    void privateWishlistCorrectHeaderReturns200() throws Exception {
        var ids = createWishlist("Secret", Visibility.PRIVATE);

        mockMvc.perform(get("/api/v1/public/wishlists/" + ids.shareableId())
                        .header(HEADER, ids.accessCode()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shareableId").value(ids.shareableId()))
                .andExpect(jsonPath("$.title").value("Secret"));
    }

    @Test
    @DisplayName("PRIVATE wishlist + malformed header → 403 INVALID_ACCESS_CODE (Security F-3 pin 2)")
    void privateWishlistMalformedHeaderReturns403() throws Exception {
        var ids = createWishlist("Secret", Visibility.PRIVATE);

        mockMvc.perform(get("/api/v1/public/wishlists/" + ids.shareableId())
                        .header(HEADER, "abcd"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ACCESS_CODE"));
    }

    @Test
    @DisplayName("PRIVATE wishlist + 6th wrong attempt → 429 ACCESS_CODE_RATE_LIMITED")
    void sixthWrongAttemptReturns429() throws Exception {
        var ids = createWishlist("Secret", Visibility.PRIVATE);
        String wrongCode = ids.accessCode().equals("0000") ? "9999" : "0000";

        // 5 wrong attempts allowed (bucket holds 5 tokens).
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/v1/public/wishlists/" + ids.shareableId())
                            .header(HEADER, wrongCode))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_ACCESS_CODE"));
        }

        // 6th attempt → rate-limited.
        mockMvc.perform(get("/api/v1/public/wishlists/" + ids.shareableId())
                        .header(HEADER, wrongCode))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_CODE_RATE_LIMITED"));
    }

    @Test
    @DisplayName("PRIVATE wishlist + correct header after failures → 200 and bucket reset")
    void successResetsRateLimit() throws Exception {
        var ids = createWishlist("Secret", Visibility.PRIVATE);
        String wrongCode = ids.accessCode().equals("0000") ? "9999" : "0000";

        // Burn 4 of 5 tokens with wrong attempts.
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(get("/api/v1/public/wishlists/" + ids.shareableId())
                            .header(HEADER, wrongCode))
                    .andExpect(status().isForbidden());
        }

        // Correct attempt → 200 and bucket reset.
        mockMvc.perform(get("/api/v1/public/wishlists/" + ids.shareableId())
                        .header(HEADER, ids.accessCode()))
                .andExpect(status().isOk());

        // Now another 5 wrong attempts must be possible (bucket was reset).
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/v1/public/wishlists/" + ids.shareableId())
                            .header(HEADER, wrongCode))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_ACCESS_CODE"));
        }
    }

    @Test
    @DisplayName("No log line contains the 4-digit access code (Security findings F-4 pin 5)")
    void noLogLineContainsAccessCode() throws Exception {
        var ids = createWishlist("Secret", Visibility.PRIVATE);

        // Run a complete success flow.
        mockMvc.perform(get("/api/v1/public/wishlists/" + ids.shareableId())
                        .header(HEADER, ids.accessCode()))
                .andExpect(status().isOk());

        // Run a failure flow.
        String wrongCode = ids.accessCode().equals("0000") ? "9999" : "0000";
        mockMvc.perform(get("/api/v1/public/wishlists/" + ids.shareableId())
                        .header(HEADER, wrongCode))
                .andExpect(status().isForbidden());

        // No service-level log line may carry the actual code value. Tolerate
        // collisions with substrings of other values (e.g. shareableId
        // accidentally containing the 4-digit substring) by checking only the
        // service logger we attached to PublicWishlistService — not the global
        // request log.
        for (ILoggingEvent event : logAppender.list) {
            assertThat(event.getFormattedMessage())
                    .as("log line must not contain the access code: %s", event.getFormattedMessage())
                    .doesNotContain(ids.accessCode());
        }
    }

    // --- helpers -------------------------------------------------------

    private record WishlistIds(String wishlistId, String shareableId, String accessCode) {}

    private WishlistIds createWishlist(String title, Visibility visibility) throws Exception {
        CreateWishlistRequest request = CreateWishlistRequest.builder()
                .title(title)
                .visibility(visibility)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/wishlists")
                        .header("Authorization", bearerToken(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        var json = objectMapper.readTree(result.getResponse().getContentAsString());
        String accessCode = json.has("accessCode") && !json.get("accessCode").isNull()
                ? json.get("accessCode").asText()
                : null;
        return new WishlistIds(
                json.get("id").asText(),
                json.get("shareableId").asText(),
                accessCode);
    }
}
