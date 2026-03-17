package com.gifiti.api.integration;

import com.gifiti.api.dto.request.CreateItemRequest;
import com.gifiti.api.dto.request.CreateWishlistRequest;
import com.gifiti.api.model.enums.Visibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Privacy integration tests for reservations.
 * Verifies that reserver identity (displayName, email, ID) is NEVER
 * exposed in owner wishlist views or public views.
 */
class GifterPrivacyIntegrationTest extends BaseIntegrationTest {

    private String ownerToken;
    private String reserverToken;
    private String wishlistId;
    private String shareableId;
    private String itemId;

    @BeforeEach
    void setup() throws Exception {
        ownerToken = createUserAndGetToken("privowner@example.com", "SecurePass123!");
        reserverToken = createUserAndGetToken("secretreserver@example.com", "SecurePass123!");

        CreateWishlistRequest wishlistRequest = CreateWishlistRequest.builder()
                .title("Privacy Wishlist")
                .visibility(Visibility.PUBLIC)
                .build();

        MvcResult wishlistResult = mockMvc.perform(post("/api/v1/wishlists")
                        .header("Authorization", bearerToken(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wishlistRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        var wishlistJson = objectMapper.readTree(wishlistResult.getResponse().getContentAsString());
        wishlistId = wishlistJson.get("id").asText();
        shareableId = wishlistJson.get("shareableId").asText();

        CreateItemRequest itemRequest = CreateItemRequest.builder()
                .name("Secret Gift")
                .build();

        MvcResult itemResult = mockMvc.perform(post("/api/v1/wishlists/" + wishlistId + "/items")
                        .header("Authorization", bearerToken(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(itemRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        itemId = objectMapper.readTree(itemResult.getResponse().getContentAsString())
                .get("id").asText();

        // User reserves the item
        mockMvc.perform(post("/api/v1/public/wishlists/" + shareableId + "/items/" + itemId + "/reserve")
                        .header("Authorization", bearerToken(reserverToken)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PRIVACY: Owner item list must NOT contain reserver identity")
    void ownerItemListMustNotContainReserverIdentity() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/wishlists/" + wishlistId + "/items")
                        .header("Authorization", bearerToken(ownerToken)))
                .andExpect(status().isOk())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        assertThat(response)
                .doesNotContain("secretreserver")
                .doesNotContain("reserverId");
    }

    @Test
    @DisplayName("PRIVACY: Owner single item must NOT contain reserver identity")
    void ownerSingleItemMustNotContainReserverIdentity() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/wishlists/" + wishlistId + "/items/" + itemId)
                        .header("Authorization", bearerToken(ownerToken)))
                .andExpect(status().isOk())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        assertThat(response)
                .doesNotContain("secretreserver")
                .doesNotContain("reserverId");
    }

    @Test
    @DisplayName("PRIVACY: Public view must NOT contain reserver identity")
    void publicViewMustNotContainReserverIdentity() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/public/wishlists/" + shareableId))
                .andExpect(status().isOk())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        assertThat(response)
                .doesNotContain("secretreserver")
                .doesNotContain("reserverId");
    }

    @Test
    @DisplayName("PRIVACY: Public view shows ownerDisplayName but NOT email")
    void publicViewShowsOwnerNameButNotEmail() throws Exception {
        mockMvc.perform(get("/api/v1/public/wishlists/" + shareableId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerDisplayName").exists())
                .andExpect(jsonPath("$.ownerEmail").doesNotExist())
                .andExpect(jsonPath("$.ownerUserId").doesNotExist());
    }

    @Test
    @DisplayName("PRIVACY: Only the reserver can see their own reservations")
    void onlyReserverCanSeeTheirOwnReservations() throws Exception {
        mockMvc.perform(get("/api/v1/reservations/mine")
                        .header("Authorization", bearerToken(reserverToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservations[0].itemId").value(itemId));
    }
}
