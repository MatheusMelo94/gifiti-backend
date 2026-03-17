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
 * Integration tests for reservation features.
 * All users can reserve items (must be logged in) and track their reservations.
 */
class GifterIntegrationTest extends BaseIntegrationTest {

    private String ownerToken;
    private String reserverToken;
    private String shareableId;
    private String itemId;

    @BeforeEach
    void setup() throws Exception {
        // Create a wishlist owner
        ownerToken = createUserAndGetToken("owner@example.com", "SecurePass123!");

        // Create a user who will reserve items
        reserverToken = createUserAndGetToken("reserver@example.com", "SecurePass123!");

        // Owner creates a public wishlist with an item
        CreateWishlistRequest wishlistRequest = CreateWishlistRequest.builder()
                .title("Test Wishlist")
                .visibility(Visibility.PUBLIC)
                .build();

        MvcResult wishlistResult = mockMvc.perform(post("/api/v1/wishlists")
                        .header("Authorization", bearerToken(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wishlistRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        var wishlistJson = objectMapper.readTree(wishlistResult.getResponse().getContentAsString());
        String wishlistId = wishlistJson.get("id").asText();
        shareableId = wishlistJson.get("shareableId").asText();

        CreateItemRequest itemRequest = CreateItemRequest.builder()
                .name("Test Gift Item")
                .build();

        MvcResult itemResult = mockMvc.perform(post("/api/v1/wishlists/" + wishlistId + "/items")
                        .header("Authorization", bearerToken(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(itemRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        itemId = objectMapper.readTree(itemResult.getResponse().getContentAsString())
                .get("id").asText();
    }

    @Nested
    @DisplayName("Reservation with authentication")
    class ReservationTests {

        @Test
        @DisplayName("should reserve item when logged in")
        void shouldReserveWithToken() throws Exception {
            mockMvc.perform(post("/api/v1/public/wishlists/" + shareableId + "/items/" + itemId + "/reserve")
                            .header("Authorization", bearerToken(reserverToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reserved").value(true));
        }

        @Test
        @DisplayName("should reject reservation without authentication")
        void shouldRejectWithoutAuth() throws Exception {
            mockMvc.perform(post("/api/v1/public/wishlists/" + shareableId + "/items/" + itemId + "/reserve"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/reservations/mine")
    class ListReservationsTests {

        @Test
        @DisplayName("should list user's reservations")
        void shouldListReservations() throws Exception {
            // Reserve as user
            mockMvc.perform(post("/api/v1/public/wishlists/" + shareableId + "/items/" + itemId + "/reserve")
                            .header("Authorization", bearerToken(reserverToken)))
                    .andExpect(status().isOk());

            // List reservations
            mockMvc.perform(get("/api/v1/reservations/mine")
                            .header("Authorization", bearerToken(reserverToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reservations", hasSize(1)))
                    .andExpect(jsonPath("$.reservations[0].itemId").value(itemId))
                    .andExpect(jsonPath("$.reservations[0].itemName").value("Test Gift Item"))
                    .andExpect(jsonPath("$.reservations[0].wishlistTitle").value("Test Wishlist"))
                    .andExpect(jsonPath("$.totalCount").value(1));
        }

        @Test
        @DisplayName("should return empty list when no reservations")
        void shouldReturnEmptyList() throws Exception {
            mockMvc.perform(get("/api/v1/reservations/mine")
                            .header("Authorization", bearerToken(reserverToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reservations", hasSize(0)))
                    .andExpect(jsonPath("$.totalCount").value(0));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/reservations/mine/{itemId}")
    class CancelReservationTests {

        @Test
        @DisplayName("should cancel own reservation")
        void shouldCancelOwnReservation() throws Exception {
            // Reserve
            mockMvc.perform(post("/api/v1/public/wishlists/" + shareableId + "/items/" + itemId + "/reserve")
                            .header("Authorization", bearerToken(reserverToken)))
                    .andExpect(status().isOk());

            // Cancel
            mockMvc.perform(delete("/api/v1/reservations/mine/" + itemId)
                            .header("Authorization", bearerToken(reserverToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reserved").value(false));

            // Verify it's gone
            mockMvc.perform(get("/api/v1/reservations/mine")
                            .header("Authorization", bearerToken(reserverToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reservations", hasSize(0)));
        }

        @Test
        @DisplayName("should allow re-reservation after cancel")
        void shouldAllowReReservation() throws Exception {
            mockMvc.perform(post("/api/v1/public/wishlists/" + shareableId + "/items/" + itemId + "/reserve")
                            .header("Authorization", bearerToken(reserverToken)))
                    .andExpect(status().isOk());

            mockMvc.perform(delete("/api/v1/reservations/mine/" + itemId)
                            .header("Authorization", bearerToken(reserverToken)))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/v1/public/wishlists/" + shareableId + "/items/" + itemId + "/reserve")
                            .header("Authorization", bearerToken(reserverToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reserved").value(true));
        }
    }
}
