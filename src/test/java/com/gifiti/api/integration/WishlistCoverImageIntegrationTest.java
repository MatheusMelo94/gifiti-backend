package com.gifiti.api.integration;

import com.gifiti.api.dto.request.CreateWishlistRequest;
import com.gifiti.api.dto.request.UpdateWishlistRequest;
import com.gifiti.api.model.enums.Visibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for coverImageUrl in wishlist CRUD operations.
 */
class WishlistCoverImageIntegrationTest extends BaseIntegrationTest {

    private String userToken;

    @BeforeEach
    void setup() throws Exception {
        userToken = createUserAndGetToken("coveruser@example.com", "Str0ng!Xyz#9");
        // Email verification is required to create wishlists — set it directly in MongoDB
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("email").is("coveruser@example.com")),
                Update.update("emailVerified", true),
                "users"
        );
    }

    @Nested
    @DisplayName("Create wishlist with cover image")
    class CreateWithCover {

        @Test
        @DisplayName("should create wishlist with coverImageUrl")
        void shouldCreateWithCover() throws Exception {
            CreateWishlistRequest request = CreateWishlistRequest.builder()
                    .title("Birthday Wishlist")
                    .coverImageUrl("https://example.com/cover.jpg")
                    .visibility(Visibility.PUBLIC)
                    .build();

            mockMvc.perform(post("/api/v1/wishlists")
                            .header("Authorization", bearerToken(userToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.coverImageUrl").value("https://example.com/cover.jpg"));
        }

        @Test
        @DisplayName("should create wishlist without coverImageUrl (null)")
        void shouldCreateWithoutCover() throws Exception {
            CreateWishlistRequest request = CreateWishlistRequest.builder()
                    .title("No Cover Wishlist")
                    .build();

            mockMvc.perform(post("/api/v1/wishlists")
                            .header("Authorization", bearerToken(userToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.coverImageUrl").doesNotExist());
        }
    }

    @Nested
    @DisplayName("Update wishlist cover image")
    class UpdateCover {

        @Test
        @DisplayName("should add cover image to existing wishlist")
        void shouldAddCover() throws Exception {
            // Create wishlist without cover
            String wishlistId = createWishlist("Test Wishlist");

            // Update with cover
            UpdateWishlistRequest update = UpdateWishlistRequest.builder()
                    .coverImageUrl("https://example.com/new-cover.jpg")
                    .build();

            mockMvc.perform(put("/api/v1/wishlists/" + wishlistId)
                            .header("Authorization", bearerToken(userToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(update)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.coverImageUrl").value("https://example.com/new-cover.jpg"));
        }

        @Test
        @DisplayName("should remove cover image with empty string")
        void shouldRemoveCover() throws Exception {
            // Create wishlist with cover
            CreateWishlistRequest create = CreateWishlistRequest.builder()
                    .title("With Cover")
                    .coverImageUrl("https://example.com/cover.jpg")
                    .build();

            MvcResult result = mockMvc.perform(post("/api/v1/wishlists")
                            .header("Authorization", bearerToken(userToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(create)))
                    .andExpect(status().isCreated())
                    .andReturn();

            String id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

            // Update with empty string to remove
            UpdateWishlistRequest update = UpdateWishlistRequest.builder()
                    .coverImageUrl("")
                    .build();

            mockMvc.perform(put("/api/v1/wishlists/" + id)
                            .header("Authorization", bearerToken(userToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(update)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.coverImageUrl").doesNotExist());
        }
    }

    @Nested
    @DisplayName("Read endpoints include coverImageUrl")
    class ReadEndpoints {

        @Test
        @DisplayName("should return coverImageUrl in GET by ID")
        void shouldReturnCoverInGetById() throws Exception {
            String id = createWishlistWithCover("Cover Test", "https://example.com/cover.jpg");

            mockMvc.perform(get("/api/v1/wishlists/" + id)
                            .header("Authorization", bearerToken(userToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.coverImageUrl").value("https://example.com/cover.jpg"));
        }

        @Test
        @DisplayName("should return coverImageUrl in list")
        void shouldReturnCoverInList() throws Exception {
            createWishlistWithCover("Cover List Test", "https://example.com/list-cover.jpg");

            mockMvc.perform(get("/api/v1/wishlists")
                            .header("Authorization", bearerToken(userToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.wishlists[0].coverImageUrl").value("https://example.com/list-cover.jpg"));
        }

        @Test
        @DisplayName("should return coverImageUrl in public wishlist")
        void shouldReturnCoverInPublicWishlist() throws Exception {
            // Create a public wishlist with cover
            CreateWishlistRequest request = CreateWishlistRequest.builder()
                    .title("Public With Cover")
                    .visibility(Visibility.PUBLIC)
                    .coverImageUrl("https://example.com/public-cover.jpg")
                    .build();

            MvcResult result = mockMvc.perform(post("/api/v1/wishlists")
                            .header("Authorization", bearerToken(userToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andReturn();

            String shareableId = objectMapper.readTree(result.getResponse().getContentAsString())
                    .get("shareableId").asText();

            // Access via public endpoint
            mockMvc.perform(get("/api/v1/public/wishlists/" + shareableId)
                            .header("Authorization", bearerToken(userToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.coverImageUrl").value("https://example.com/public-cover.jpg"));
        }
    }

    // Helper methods

    private String createWishlist(String title) throws Exception {
        CreateWishlistRequest request = CreateWishlistRequest.builder()
                .title(title)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/wishlists")
                        .header("Authorization", bearerToken(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String createWishlistWithCover(String title, String coverUrl) throws Exception {
        CreateWishlistRequest request = CreateWishlistRequest.builder()
                .title(title)
                .coverImageUrl(coverUrl)
                .visibility(Visibility.PUBLIC)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/wishlists")
                        .header("Authorization", bearerToken(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }
}
