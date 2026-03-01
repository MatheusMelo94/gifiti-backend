package com.gifiti.api.integration;

import com.gifiti.api.dto.request.CreateItemRequest;
import com.gifiti.api.dto.request.CreateWishlistRequest;
import com.gifiti.api.model.enums.Visibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for reservation functionality.
 * Tests reservation atomicity and the reserve/unreserve flow.
 */
class ReservationIntegrationTest extends BaseIntegrationTest {

    private String ownerToken;
    private String wishlistId;
    private String shareableId;

    @BeforeEach
    void setup() throws Exception {
        ownerToken = createUserAndGetToken("owner@example.com", "Password123!");

        // Create public wishlist
        CreateWishlistRequest wishlistRequest = CreateWishlistRequest.builder()
                .title("Birthday Wishlist")
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

    @Nested
    @DisplayName("POST /api/v1/public/wishlists/{shareableId}/items/{itemId}/reserve")
    class ReserveTests {

        @Test
        @DisplayName("should reserve an available item")
        void shouldReserveAvailableItem() throws Exception {
            String itemId = createItem("Gift Item");

            mockMvc.perform(post("/api/v1/public/wishlists/" + shareableId + "/items/" + itemId + "/reserve"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.itemId").value(itemId))
                    .andExpect(jsonPath("$.reserved").value(true))
                    .andExpect(jsonPath("$.message").value(containsString("successfully")));

            // Verify item status changed to RESERVED
            mockMvc.perform(get("/api/v1/public/wishlists/" + shareableId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].status").value("RESERVED"));
        }

        @Test
        @DisplayName("should reject reservation of already reserved item (409 Conflict)")
        void shouldRejectAlreadyReservedItem() throws Exception {
            String itemId = createItem("Popular Item");

            // First reservation succeeds
            mockMvc.perform(post("/api/v1/public/wishlists/" + shareableId + "/items/" + itemId + "/reserve"))
                    .andExpect(status().isOk());

            // Second reservation fails with 409 Conflict
            mockMvc.perform(post("/api/v1/public/wishlists/" + shareableId + "/items/" + itemId + "/reserve"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(containsString("already reserved")));
        }

        @Test
        @DisplayName("should return 404 for non-existent item")
        void shouldReturn404ForNonExistentItem() throws Exception {
            mockMvc.perform(post("/api/v1/public/wishlists/" + shareableId + "/items/nonexistent/reserve"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 404 for private wishlist")
        void shouldReturn404ForPrivateWishlist() throws Exception {
            // Create a private wishlist
            CreateWishlistRequest privateWishlistRequest = CreateWishlistRequest.builder()
                    .title("Private Wishlist")
                    .visibility(Visibility.PRIVATE)
                    .build();

            MvcResult result = mockMvc.perform(post("/api/v1/wishlists")
                            .header("Authorization", bearerToken(ownerToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(privateWishlistRequest)))
                    .andExpect(status().isCreated())
                    .andReturn();

            String privateShareableId = objectMapper.readTree(result.getResponse().getContentAsString())
                    .get("shareableId").asText();

            // Try to reserve on private wishlist
            mockMvc.perform(post("/api/v1/public/wishlists/" + privateShareableId + "/items/anyitem/reserve"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/wishlists/{wishlistId}/items/{itemId}/reservation")
    class UnreserveTests {

        @Test
        @DisplayName("should allow owner to unreserve an item")
        void shouldAllowOwnerToUnreserve() throws Exception {
            String itemId = createItem("Reserved Item");

            // Reserve the item
            mockMvc.perform(post("/api/v1/public/wishlists/" + shareableId + "/items/" + itemId + "/reserve"))
                    .andExpect(status().isOk());

            // Owner unreserves
            mockMvc.perform(delete("/api/v1/wishlists/" + wishlistId + "/items/" + itemId + "/reservation")
                            .header("Authorization", bearerToken(ownerToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reserved").value(false));

            // Verify item is available again
            mockMvc.perform(get("/api/v1/public/wishlists/" + shareableId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].status").value("AVAILABLE"));
        }

        @Test
        @DisplayName("should not allow non-owner to unreserve")
        void shouldNotAllowNonOwnerToUnreserve() throws Exception {
            String itemId = createItem("Protected Item");

            // Reserve the item
            mockMvc.perform(post("/api/v1/public/wishlists/" + shareableId + "/items/" + itemId + "/reserve"))
                    .andExpect(status().isOk());

            // Another user tries to unreserve
            String otherToken = createUserAndGetToken("attacker@example.com", "Password123!");

            mockMvc.perform(delete("/api/v1/wishlists/" + wishlistId + "/items/" + itemId + "/reservation")
                            .header("Authorization", bearerToken(otherToken)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("should require authentication to unreserve")
        void shouldRequireAuthenticationToUnreserve() throws Exception {
            String itemId = createItem("Auth Required Item");

            mockMvc.perform(delete("/api/v1/wishlists/" + wishlistId + "/items/" + itemId + "/reservation"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Reservation can be made again after unreserve")
    class ReReservationTests {

        @Test
        @DisplayName("should allow re-reservation after unreserve")
        void shouldAllowReReservationAfterUnreserve() throws Exception {
            String itemId = createItem("Re-reservable Item");

            // First reserve
            mockMvc.perform(post("/api/v1/public/wishlists/" + shareableId + "/items/" + itemId + "/reserve"))
                    .andExpect(status().isOk());

            // Owner unreserves
            mockMvc.perform(delete("/api/v1/wishlists/" + wishlistId + "/items/" + itemId + "/reservation")
                            .header("Authorization", bearerToken(ownerToken)))
                    .andExpect(status().isOk());

            // Reserve again
            mockMvc.perform(post("/api/v1/public/wishlists/" + shareableId + "/items/" + itemId + "/reserve"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reserved").value(true));
        }
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
}
