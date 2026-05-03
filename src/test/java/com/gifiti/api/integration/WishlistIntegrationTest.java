package com.gifiti.api.integration;

import com.gifiti.api.dto.request.CreateWishlistRequest;
import com.gifiti.api.dto.request.UpdateWishlistRequest;
import com.gifiti.api.model.enums.Visibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for wishlist CRUD endpoints.
 */
class WishlistIntegrationTest extends BaseIntegrationTest {

    private String userToken;

    @BeforeEach
    void setup() throws Exception {
        userToken = createVerifiedUserAndGetToken("wishlistuser@example.com", "BlueP4nther$Xyz2!");
    }

    @Nested
    @DisplayName("POST /api/v1/wishlists")
    class CreateWishlistTests {

        @Test
        @DisplayName("should create wishlist successfully")
        void shouldCreateWishlist() throws Exception {
            CreateWishlistRequest request = CreateWishlistRequest.builder()
                    .title("My Birthday Wishlist")
                    .description("Things I want for my birthday")
                    .visibility(Visibility.PRIVATE)
                    .build();

            mockMvc.perform(post("/api/v1/wishlists")
                            .header("Authorization", bearerToken(userToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").exists())
                    .andExpect(jsonPath("$.title").value("My Birthday Wishlist"))
                    .andExpect(jsonPath("$.description").value("Things I want for my birthday"))
                    .andExpect(jsonPath("$.visibility").value("PRIVATE"))
                    .andExpect(jsonPath("$.shareableId").exists())
                    .andExpect(jsonPath("$.itemCount").value(0));
        }

        @Test
        @DisplayName("should require authentication")
        void shouldRequireAuthentication() throws Exception {
            CreateWishlistRequest request = CreateWishlistRequest.builder()
                    .title("No Auth Wishlist")
                    .build();

            mockMvc.perform(post("/api/v1/wishlists")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("should reject empty title")
        void shouldRejectEmptyTitle() throws Exception {
            CreateWishlistRequest request = CreateWishlistRequest.builder()
                    .title("")
                    .build();

            mockMvc.perform(post("/api/v1/wishlists")
                            .header("Authorization", bearerToken(userToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/wishlists")
    class ListWishlistsTests {

        @Test
        @DisplayName("should list user wishlists")
        void shouldListUserWishlists() throws Exception {
            // Create two wishlists
            createWishlist("Wishlist 1");
            createWishlist("Wishlist 2");

            mockMvc.perform(get("/api/v1/wishlists")
                            .header("Authorization", bearerToken(userToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.wishlists").isArray())
                    .andExpect(jsonPath("$.wishlists", hasSize(2)));
        }

        @Test
        @DisplayName("should return empty list for new user")
        void shouldReturnEmptyListForNewUser() throws Exception {
            mockMvc.perform(get("/api/v1/wishlists")
                            .header("Authorization", bearerToken(userToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.wishlists").isArray())
                    .andExpect(jsonPath("$.wishlists", hasSize(0)));
        }

        @Test
        @DisplayName("should not show other users' wishlists")
        void shouldNotShowOtherUsersWishlists() throws Exception {
            // Create wishlist as first user
            createWishlist("First User's Wishlist");

            // Create second user and check their wishlists
            String otherUserToken = createVerifiedUserAndGetToken("otheruser@example.com", "BlueP4nther$Xyz2!");

            mockMvc.perform(get("/api/v1/wishlists")
                            .header("Authorization", bearerToken(otherUserToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.wishlists", hasSize(0)));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/wishlists/{id}")
    class GetWishlistTests {

        @Test
        @DisplayName("should get wishlist by ID")
        void shouldGetWishlistById() throws Exception {
            String wishlistId = createWishlist("My Wishlist");

            mockMvc.perform(get("/api/v1/wishlists/" + wishlistId)
                            .header("Authorization", bearerToken(userToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(wishlistId))
                    .andExpect(jsonPath("$.title").value("My Wishlist"));
        }

        @Test
        @DisplayName("should return 404 for non-existent wishlist")
        void shouldReturn404ForNonExistent() throws Exception {
            mockMvc.perform(get("/api/v1/wishlists/nonexistent123")
                            .header("Authorization", bearerToken(userToken)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 403 for other user's wishlist")
        void shouldReturn403ForOtherUserWishlist() throws Exception {
            String wishlistId = createWishlist("Private Wishlist");

            String otherUserToken = createVerifiedUserAndGetToken("otheruser2@example.com", "BlueP4nther$Xyz2!");

            mockMvc.perform(get("/api/v1/wishlists/" + wishlistId)
                            .header("Authorization", bearerToken(otherUserToken)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/wishlists/{id}")
    class UpdateWishlistTests {

        @Test
        @DisplayName("should update wishlist")
        void shouldUpdateWishlist() throws Exception {
            String wishlistId = createWishlist("Original Title");

            UpdateWishlistRequest request = UpdateWishlistRequest.builder()
                    .title("Updated Title")
                    .visibility(Visibility.PUBLIC)
                    .build();

            mockMvc.perform(put("/api/v1/wishlists/" + wishlistId)
                            .header("Authorization", bearerToken(userToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("Updated Title"))
                    .andExpect(jsonPath("$.visibility").value("PUBLIC"));
        }

        @Test
        @DisplayName("should support partial update")
        void shouldSupportPartialUpdate() throws Exception {
            String wishlistId = createWishlist("Original Title");

            UpdateWishlistRequest request = UpdateWishlistRequest.builder()
                    .description("New description only")
                    .build();

            mockMvc.perform(put("/api/v1/wishlists/" + wishlistId)
                            .header("Authorization", bearerToken(userToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("Original Title"))
                    .andExpect(jsonPath("$.description").value("New description only"));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/wishlists/{id}")
    class DeleteWishlistTests {

        @Test
        @DisplayName("should delete wishlist")
        void shouldDeleteWishlist() throws Exception {
            String wishlistId = createWishlist("To Delete");

            mockMvc.perform(delete("/api/v1/wishlists/" + wishlistId)
                            .header("Authorization", bearerToken(userToken)))
                    .andExpect(status().isNoContent());

            // Verify deleted
            mockMvc.perform(get("/api/v1/wishlists/" + wishlistId)
                            .header("Authorization", bearerToken(userToken)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should not delete other user's wishlist")
        void shouldNotDeleteOtherUserWishlist() throws Exception {
            String wishlistId = createWishlist("Protected Wishlist");

            String otherUserToken = createVerifiedUserAndGetToken("otheruser3@example.com", "BlueP4nther$Xyz2!");

            mockMvc.perform(delete("/api/v1/wishlists/" + wishlistId)
                            .header("Authorization", bearerToken(otherUserToken)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Event Date (Phase 1C)")
    class EventDateTests {

        @Test
        @DisplayName("should create wishlist with eventDate")
        void shouldCreateWishlistWithEventDate() throws Exception {
            CreateWishlistRequest request = CreateWishlistRequest.builder()
                    .title("Birthday 2026")
                    .eventDate(LocalDate.of(2026, 6, 15))
                    .visibility(Visibility.PUBLIC)
                    .build();

            mockMvc.perform(post("/api/v1/wishlists")
                            .header("Authorization", bearerToken(userToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.eventDate").value("2026-06-15"));
        }

        @Test
        @DisplayName("should update eventDate")
        void shouldUpdateEventDate() throws Exception {
            String wishlistId = createWishlist("Date Test");

            UpdateWishlistRequest request = UpdateWishlistRequest.builder()
                    .eventDate(LocalDate.of(2026, 12, 25))
                    .build();

            mockMvc.perform(put("/api/v1/wishlists/" + wishlistId)
                            .header("Authorization", bearerToken(userToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.eventDate").value("2026-12-25"));
        }

        @Test
        @DisplayName("should show eventDate in public view")
        void shouldShowEventDateInPublicView() throws Exception {
            CreateWishlistRequest request = CreateWishlistRequest.builder()
                    .title("Public Event")
                    .eventDate(LocalDate.of(2026, 6, 15))
                    .visibility(Visibility.PUBLIC)
                    .build();

            MvcResult result = mockMvc.perform(post("/api/v1/wishlists")
                            .header("Authorization", bearerToken(userToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andReturn();

            String shareableId = objectMapper.readTree(result.getResponse().getContentAsString())
                    .get("shareableId").asText();

            // Shared wishlists now require authentication (link + login per
            // SecurityConfig). The endpoint name is historical — it serves
            // share-link consumers, who must be authenticated.
            mockMvc.perform(get("/api/v1/public/wishlists/" + shareableId)
                            .header("Authorization", bearerToken(userToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.eventDate").value("2026-06-15"))
                    .andExpect(jsonPath("$.ownerDisplayName").exists());
        }

        @Test
        @DisplayName("should accept null eventDate")
        void shouldAcceptNullEventDate() throws Exception {
            CreateWishlistRequest request = CreateWishlistRequest.builder()
                    .title("No Date Wishlist")
                    .build();

            mockMvc.perform(post("/api/v1/wishlists")
                            .header("Authorization", bearerToken(userToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.eventDate").doesNotExist());
        }
    }

    private String createWishlist(String title) throws Exception {
        CreateWishlistRequest request = CreateWishlistRequest.builder()
                .title(title)
                .visibility(Visibility.PRIVATE)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/wishlists")
                        .header("Authorization", bearerToken(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText();
    }
}
