package com.gifiti.api.integration;

import com.gifiti.api.dto.request.CreateItemRequest;
import com.gifiti.api.dto.request.CreateWishlistRequest;
import com.gifiti.api.model.enums.Visibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests specifically for privacy guarantees.
 * Verifies that reserverId is NEVER exposed to the wishlist owner.
 * This is a critical security requirement of the system.
 */
class PrivacyIntegrationTest extends BaseIntegrationTest {

    private String ownerToken;
    private String reserverToken;
    private String wishlistId;
    private String shareableId;

    @BeforeEach
    void setup() throws Exception {
        ownerToken = createUserAndGetToken("privacyowner@example.com", "Password123!");
        reserverToken = createUserAndGetToken("privacyreserver@example.com", "Password123!");

        CreateWishlistRequest wishlistRequest = CreateWishlistRequest.builder()
                .title("Privacy Test Wishlist")
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
    }

    @Test
    @DisplayName("PRIVACY: Owner's item list should NEVER include reserverId")
    void ownerItemListShouldNeverIncludeReserverId() throws Exception {
        String itemId = createItem("Secret Gift");
        reserveItem(itemId);

        mockMvc.perform(get("/api/v1/wishlists/" + wishlistId + "/items")
                        .header("Authorization", bearerToken(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].reserverId").doesNotExist())
                .andExpect(jsonPath("$.items[0].status").value("RESERVED"));
    }

    @Test
    @DisplayName("PRIVACY: Owner's single item GET should NEVER include reserverId")
    void ownerSingleItemShouldNeverIncludeReserverId() throws Exception {
        String itemId = createItem("Another Secret Gift");
        reserveItem(itemId);

        mockMvc.perform(get("/api/v1/wishlists/" + wishlistId + "/items/" + itemId)
                        .header("Authorization", bearerToken(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reserverId").doesNotExist())
                .andExpect(jsonPath("$.status").value("RESERVED"));
    }

    @Test
    @DisplayName("PRIVACY: Public wishlist view should NEVER include reserverId")
    void publicWishlistViewShouldNeverIncludeReserverId() throws Exception {
        String itemId = createItem("Public View Item");
        reserveItem(itemId);

        mockMvc.perform(get("/api/v1/public/wishlists/" + shareableId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].reserverId").doesNotExist())
                .andExpect(jsonPath("$.items[0].status").value("RESERVED"));
    }

    @Test
    @DisplayName("PRIVACY: Owner should only see status, not who reserved")
    void ownerShouldOnlySeeStatusNotWhoReserved() throws Exception {
        String itemId = createItem("Mystery Gift");
        reserveItem(itemId);

        MvcResult result = mockMvc.perform(get("/api/v1/wishlists/" + wishlistId + "/items/" + itemId)
                        .header("Authorization", bearerToken(ownerToken)))
                .andExpect(status().isOk())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(response)
                .doesNotContain("reserverId")
                .doesNotContain("privacyreserver");
    }

    @Test
    @DisplayName("PRIVACY: Wishlist response should not include ownerUserId")
    void wishlistResponseShouldNotIncludeOwnerUserId() throws Exception {
        mockMvc.perform(get("/api/v1/public/wishlists/" + shareableId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerUserId").doesNotExist())
                .andExpect(jsonPath("$.id").doesNotExist());
    }

    @Test
    @DisplayName("PRIVACY: Item in public view should not expose ownerUserId")
    void itemInPublicViewShouldNotExposeOwnerUserId() throws Exception {
        createItem("Public Item");

        mockMvc.perform(get("/api/v1/public/wishlists/" + shareableId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].ownerUserId").doesNotExist())
                .andExpect(jsonPath("$.items[0].wishlistId").doesNotExist());
    }

    private String createItem(String name) throws Exception {
        CreateItemRequest request = CreateItemRequest.builder()
                .name(name)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/wishlists/" + wishlistId + "/items")
                        .header("Authorization", bearerToken(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText();
    }

    private void reserveItem(String itemId) throws Exception {
        mockMvc.perform(post("/api/v1/public/wishlists/" + shareableId + "/items/" + itemId + "/reserve")
                        .header("Authorization", bearerToken(reserverToken)))
                .andExpect(status().isOk());
    }
}
