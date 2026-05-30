package com.gifiti.api.integration;

import com.gifiti.api.dto.request.CreateItemRequest;
import com.gifiti.api.dto.request.CreateWishlistRequest;
import com.gifiti.api.model.enums.Priority;
import com.gifiti.api.model.enums.Visibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the {@code X-Wishlist-Access-Code} gate on
 * {@code POST /api/v1/public/wishlists/{shareableId}/items/{itemId}/reserve}
 * (feature 008 / T8).
 *
 * <p>This is the highest-impact test suite in feature 008 (Security findings
 * F-4 pin 4): an authenticated user who possesses a leaked PRIVATE shareableId
 * must NOT be able to reserve items on it without solving the access-code
 * gate. The reserve flow calls {@code PublicWishlistService.findByShareableId}
 * for visibility resolution; T6 + T8 ensure that SAME gate now enforces
 * access-code validation before the reservation proceeds.
 *
 * <p>Note: a no-header reserve test on PRIVATE already lives in
 * {@link ReservationIntegrationTest} (the migrated 404→403 test); this suite
 * adds the wrong-header / matching-header / 429 paths and the
 * PUBLIC-unaffected regression.
 */
class ReserveAccessCodeGateTest extends BaseIntegrationTest {

    private static final String HEADER = "X-Wishlist-Access-Code";

    private String ownerToken;
    private String reserverToken;

    @BeforeEach
    void setup() throws Exception {
        ownerToken = createVerifiedUserAndGetToken("reserve-gate-owner@example.test", "Mvn-Build-Cyan-Glow-2026!");
        reserverToken = createVerifiedUserAndGetToken("reserve-gate-reserver@example.test", "Mvn-Build-Cyan-Glow-2026!");
    }

    @Test
    @DisplayName("PRIVATE wishlist reserve + wrong header → 403 INVALID_ACCESS_CODE (Security F-4 pin 4)")
    void privateWishlistReserveWrongHeaderReturns403() throws Exception {
        var fixture = createPrivateWishlistWithItem("Gift Item");
        String wrongCode = fixture.accessCode().equals("0000") ? "9999" : "0000";

        mockMvc.perform(post("/api/v1/public/wishlists/" + fixture.shareableId()
                        + "/items/" + fixture.itemId() + "/reserve")
                        .header("Authorization", bearerToken(reserverToken))
                        .header(HEADER, wrongCode))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ACCESS_CODE"));
    }

    @Test
    @DisplayName("PRIVATE wishlist reserve + correct header → 200 reserves successfully")
    void privateWishlistReserveCorrectHeaderReserves() throws Exception {
        var fixture = createPrivateWishlistWithItem("Gift Item");

        mockMvc.perform(post("/api/v1/public/wishlists/" + fixture.shareableId()
                        + "/items/" + fixture.itemId() + "/reserve")
                        .header("Authorization", bearerToken(reserverToken))
                        .header(HEADER, fixture.accessCode()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemId").value(fixture.itemId()))
                .andExpect(jsonPath("$.reserved").value(true));
    }

    @Test
    @DisplayName("PUBLIC wishlist reserve without header → 200 (existing behavior preserved)")
    void publicWishlistReserveWithoutHeaderUnaffected() throws Exception {
        var fixture = createPublicWishlistWithItem("Gift Item");

        mockMvc.perform(post("/api/v1/public/wishlists/" + fixture.shareableId()
                        + "/items/" + fixture.itemId() + "/reserve")
                        .header("Authorization", bearerToken(reserverToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reserved").value(true));
    }

    @Test
    @DisplayName("PRIVATE wishlist reserve + 6 wrong attempts → 429 ACCESS_CODE_RATE_LIMITED")
    void sixWrongReserveAttemptsReturn429() throws Exception {
        var fixture = createPrivateWishlistWithItem("Gift Item");
        String wrongCode = fixture.accessCode().equals("0000") ? "9999" : "0000";

        // 5 wrong attempts allowed.
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/public/wishlists/" + fixture.shareableId()
                            + "/items/" + fixture.itemId() + "/reserve")
                            .header("Authorization", bearerToken(reserverToken))
                            .header(HEADER, wrongCode))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_ACCESS_CODE"));
        }

        // 6th attempt → rate-limited.
        mockMvc.perform(post("/api/v1/public/wishlists/" + fixture.shareableId()
                        + "/items/" + fixture.itemId() + "/reserve")
                        .header("Authorization", bearerToken(reserverToken))
                        .header(HEADER, wrongCode))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_CODE_RATE_LIMITED"));
    }

    @Test
    @DisplayName("PRIVATE wishlist GET + reserve share the SAME rate-limit bucket")
    void getAndReserveShareSameBucket() throws Exception {
        // F-2 sanity: the per-(IP, shareableId) bucket is keyed on shareableId
        // only, so failed GET attempts and failed reserve attempts share the
        // budget. Confirm by burning 5 on GET and asserting the next reserve
        // attempt with a wrong code returns 429 (not 403).
        var fixture = createPrivateWishlistWithItem("Gift Item");
        String wrongCode = fixture.accessCode().equals("0000") ? "9999" : "0000";

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/v1/public/wishlists/" + fixture.shareableId())
                            .header(HEADER, wrongCode))
                    .andExpect(status().isForbidden());
        }

        mockMvc.perform(post("/api/v1/public/wishlists/" + fixture.shareableId()
                        + "/items/" + fixture.itemId() + "/reserve")
                        .header("Authorization", bearerToken(reserverToken))
                        .header(HEADER, wrongCode))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_CODE_RATE_LIMITED"));
    }

    // --- helpers -------------------------------------------------------

    private record WishlistFixture(
            String wishlistId,
            String shareableId,
            String accessCode,
            String itemId) {}

    private WishlistFixture createPrivateWishlistWithItem(String itemName) throws Exception {
        return createWishlistWithItem(itemName, Visibility.PRIVATE);
    }

    private WishlistFixture createPublicWishlistWithItem(String itemName) throws Exception {
        return createWishlistWithItem(itemName, Visibility.PUBLIC);
    }

    private WishlistFixture createWishlistWithItem(String itemName, Visibility visibility) throws Exception {
        CreateWishlistRequest wishlistRequest = CreateWishlistRequest.builder()
                .title(visibility + " list")
                .visibility(visibility)
                .build();

        MvcResult wishlistResult = mockMvc.perform(post("/api/v1/wishlists")
                        .header("Authorization", bearerToken(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wishlistRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        var json = objectMapper.readTree(wishlistResult.getResponse().getContentAsString());
        String wishlistId = json.get("id").asText();
        String shareableId = json.get("shareableId").asText();
        String accessCode = json.has("accessCode") && !json.get("accessCode").isNull()
                ? json.get("accessCode").asText()
                : null;

        CreateItemRequest itemRequest = CreateItemRequest.builder()
                .name(itemName)
                .description("Reserve-gate fixture item")
                .price(new BigDecimal("19.99"))
                .priority(Priority.MEDIUM)
                .build();

        MvcResult itemResult = mockMvc.perform(post("/api/v1/wishlists/" + wishlistId + "/items")
                        .header("Authorization", bearerToken(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(itemRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String itemId = objectMapper.readTree(itemResult.getResponse().getContentAsString())
                .get("id").asText();

        return new WishlistFixture(wishlistId, shareableId, accessCode, itemId);
    }
}
