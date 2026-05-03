package com.gifiti.api.integration;

import com.gifiti.api.dto.request.CreateItemRequest;
import com.gifiti.api.dto.request.CreateWishlistRequest;
import com.gifiti.api.dto.request.UpdateItemRequest;
import com.gifiti.api.model.enums.Priority;
import com.gifiti.api.model.enums.Visibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for wishlist item CRUD endpoints.
 */
class WishlistItemIntegrationTest extends BaseIntegrationTest {

    private String userToken;
    private String wishlistId;

    @BeforeEach
    void setup() throws Exception {
        userToken = createVerifiedUserAndGetToken("itemuser@example.com", "BlueP4nther$Xyz2!");
        wishlistId = createWishlist();
    }

    @Nested
    @DisplayName("POST /api/v1/wishlists/{wishlistId}/items")
    class CreateItemTests {

        @Test
        @DisplayName("should create item successfully")
        void shouldCreateItem() throws Exception {
            CreateItemRequest request = CreateItemRequest.builder()
                    .name("PlayStation 5")
                    .description("The latest gaming console")
                    .productLink("https://example.com/ps5")
                    .price(new BigDecimal("499.99"))
                    .priority(Priority.HIGH)
                    .build();

            mockMvc.perform(post("/api/v1/wishlists/" + wishlistId + "/items")
                            .header("Authorization", bearerToken(userToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").exists())
                    .andExpect(jsonPath("$.name").value("PlayStation 5"))
                    .andExpect(jsonPath("$.price").value(499.99))
                    .andExpect(jsonPath("$.priority").value("HIGH"))
                    .andExpect(jsonPath("$.status").value("AVAILABLE"));
        }

        @Test
        @DisplayName("should create item with default priority")
        void shouldCreateItemWithDefaultPriority() throws Exception {
            CreateItemRequest request = CreateItemRequest.builder()
                    .name("Simple Item")
                    .build();

            mockMvc.perform(post("/api/v1/wishlists/" + wishlistId + "/items")
                            .header("Authorization", bearerToken(userToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.priority").value("MEDIUM"));
        }

        @Test
        @DisplayName("should reject item in non-existent wishlist")
        void shouldRejectItemInNonExistentWishlist() throws Exception {
            CreateItemRequest request = CreateItemRequest.builder()
                    .name("Orphan Item")
                    .build();

            mockMvc.perform(post("/api/v1/wishlists/nonexistent/items")
                            .header("Authorization", bearerToken(userToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should reject item in other user's wishlist")
        void shouldRejectItemInOtherUserWishlist() throws Exception {
            String otherUserToken = createVerifiedUserAndGetToken("otheritemuser@example.com", "BlueP4nther$Xyz2!");

            CreateItemRequest request = CreateItemRequest.builder()
                    .name("Unauthorized Item")
                    .build();

            mockMvc.perform(post("/api/v1/wishlists/" + wishlistId + "/items")
                            .header("Authorization", bearerToken(otherUserToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/wishlists/{wishlistId}/items")
    class ListItemsTests {

        @Test
        @DisplayName("should list all items in wishlist")
        void shouldListAllItems() throws Exception {
            createItem("Item 1");
            createItem("Item 2");
            createItem("Item 3");

            mockMvc.perform(get("/api/v1/wishlists/" + wishlistId + "/items")
                            .header("Authorization", bearerToken(userToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isArray())
                    .andExpect(jsonPath("$.items", hasSize(3)));
        }

        @Test
        @DisplayName("should return empty list for wishlist with no items")
        void shouldReturnEmptyList() throws Exception {
            mockMvc.perform(get("/api/v1/wishlists/" + wishlistId + "/items")
                            .header("Authorization", bearerToken(userToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items", hasSize(0)));
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/wishlists/{wishlistId}/items/{itemId}")
    class UpdateItemTests {

        @Test
        @DisplayName("should update item")
        void shouldUpdateItem() throws Exception {
            String itemId = createItem("Original Name");

            UpdateItemRequest request = UpdateItemRequest.builder()
                    .name("Updated Name")
                    .price(new BigDecimal("99.99"))
                    .build();

            mockMvc.perform(put("/api/v1/wishlists/" + wishlistId + "/items/" + itemId)
                            .header("Authorization", bearerToken(userToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Updated Name"))
                    .andExpect(jsonPath("$.price").value(99.99));
        }

        @Test
        @DisplayName("should support partial update")
        void shouldSupportPartialUpdate() throws Exception {
            String itemId = createItem("Keep This Name");

            UpdateItemRequest request = UpdateItemRequest.builder()
                    .priority(Priority.LOW)
                    .build();

            mockMvc.perform(put("/api/v1/wishlists/" + wishlistId + "/items/" + itemId)
                            .header("Authorization", bearerToken(userToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Keep This Name"))
                    .andExpect(jsonPath("$.priority").value("LOW"));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/wishlists/{wishlistId}/items/{itemId}")
    class DeleteItemTests {

        @Test
        @DisplayName("should delete item")
        void shouldDeleteItem() throws Exception {
            String itemId = createItem("To Delete");

            mockMvc.perform(delete("/api/v1/wishlists/" + wishlistId + "/items/" + itemId)
                            .header("Authorization", bearerToken(userToken)))
                    .andExpect(status().isNoContent());

            // Verify deleted
            mockMvc.perform(get("/api/v1/wishlists/" + wishlistId + "/items/" + itemId)
                            .header("Authorization", bearerToken(userToken)))
                    .andExpect(status().isNotFound());
        }
    }

    private String createWishlist() throws Exception {
        CreateWishlistRequest request = CreateWishlistRequest.builder()
                .title("Test Wishlist")
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

    private String createItem(String name) throws Exception {
        CreateItemRequest request = CreateItemRequest.builder()
                .name(name)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/wishlists/" + wishlistId + "/items")
                        .header("Authorization", bearerToken(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText();
    }
}
