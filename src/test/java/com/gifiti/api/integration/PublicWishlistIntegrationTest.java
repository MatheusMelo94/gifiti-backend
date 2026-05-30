package com.gifiti.api.integration;

import com.gifiti.api.dto.request.CreateItemRequest;
import com.gifiti.api.dto.request.CreateWishlistRequest;
import com.gifiti.api.dto.request.UpdateWishlistRequest;
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
 * Integration tests for shared wishlist viewing.
 * Anonymous (unauthenticated) GET is permitted on PUBLIC wishlists since feature 006
 * (2026-05-05). PRIVATE wishlists return 404 regardless of auth state.
 */
class PublicWishlistIntegrationTest extends BaseIntegrationTest {

    private String ownerToken;
    private String viewerToken;

    @BeforeEach
    void setup() throws Exception {
        ownerToken = createVerifiedUserAndGetToken("publicowner@example.com", "Mvn-Build-Cyan-Glow-2026!");
        viewerToken = createVerifiedUserAndGetToken("viewer@example.com", "Mvn-Build-Cyan-Glow-2026!");
    }

    @Nested
    @DisplayName("GET /api/v1/public/wishlists/{shareableId}")
    class SharedViewTests {

        @Test
        @DisplayName("should allow authenticated user to view shared wishlist")
        void shouldAllowAuthenticatedAccessToSharedWishlist() throws Exception {
            String shareableId = createPublicWishlist("Public Birthday List");

            mockMvc.perform(get("/api/v1/public/wishlists/" + shareableId)
                            .header("Authorization", bearerToken(viewerToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("Public Birthday List"))
                    .andExpect(jsonPath("$.shareableId").value(shareableId));
        }

        @Test
        @DisplayName("should return wishlist with all items")
        void shouldReturnWishlistWithAllItems() throws Exception {
            var ids = createPublicWishlistWithItems();

            mockMvc.perform(get("/api/v1/public/wishlists/" + ids.shareableId)
                            .header("Authorization", bearerToken(viewerToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items").isArray())
                    .andExpect(jsonPath("$.items", hasSize(3)))
                    .andExpect(jsonPath("$.itemCount").value(3));
        }

        @Test
        @DisplayName("should show item details in shared view")
        void shouldShowItemDetailsInSharedView() throws Exception {
            var ids = createPublicWishlistWithItems();

            mockMvc.perform(get("/api/v1/public/wishlists/" + ids.shareableId)
                            .header("Authorization", bearerToken(viewerToken)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].name").exists())
                    .andExpect(jsonPath("$.items[0].description").exists())
                    .andExpect(jsonPath("$.items[0].price").exists())
                    .andExpect(jsonPath("$.items[0].priority").exists())
                    .andExpect(jsonPath("$.items[0].status").value("AVAILABLE"));
        }

        @Test
        @DisplayName("should return 403 ACCESS_CODE_REQUIRED for private wishlist with no header (feature 008 / T6)")
        void shouldReturn403ForPrivateWishlistWithoutHeader() throws Exception {
            // Feature 008 / T6 inverts feature 006's anti-enumeration posture:
            // PRIVATE wishlists now return 403 with an ACCESS_CODE_REQUIRED
            // discriminator so the frontend gate UX can distinguish "wrong
            // link" from "right link, need code". See ADR 0008 § 3 and
            // Security findings F-1 (CONCUR-WITH-ARCHITECT).
            String shareableId = createPrivateWishlist("Secret Wishlist");

            mockMvc.perform(get("/api/v1/public/wishlists/" + shareableId)
                            .header("Authorization", bearerToken(viewerToken)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("ACCESS_CODE_REQUIRED"));
        }

        @Test
        @DisplayName("should return 404 for non-existent shareableId")
        void shouldReturn404ForNonExistentShareableId() throws Exception {
            mockMvc.perform(get("/api/v1/public/wishlists/nonexistent123")
                            .header("Authorization", bearerToken(viewerToken)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should allow anonymous (unauthenticated) GET on shared wishlist")
        void shouldAllowAnonymousAccessToSharedWishlist() throws Exception {
            // Pins behavior introduced by feature 006 (anonymous public wishlist viewing,
            // 2026-05-05): GET /api/v1/public/wishlists/{shareableId} is intentionally
            // open to unauthenticated requests. See CLAUDE.md "Recent Changes" entry for
            // 006-anonymous-public-wishlist-viewing.
            String shareableId = createPublicWishlist("Anonymous Access");

            // No Authorization header → 200 with public wishlist body
            mockMvc.perform(get("/api/v1/public/wishlists/" + shareableId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("Anonymous Access"))
                    .andExpect(jsonPath("$.shareableId").value(shareableId));
        }
    }

    @Nested
    @DisplayName("Visibility Changes")
    class VisibilityChangeTests {

        @Test
        @DisplayName("should require access code when visibility changes to PRIVATE (feature 008 / T6)")
        void shouldRequireAccessCodeWhenVisibilityChangesToPrivate() throws Exception {
            // Feature 008 / T6: when visibility flips to PRIVATE, the public
            // endpoint now returns 403 ACCESS_CODE_REQUIRED (previously 404 per
            // feature 006 anti-enumeration). See ADR 0008 § 3.
            String shareableId = createPublicWishlist("Initially Public");

            // Verify authenticated access works
            mockMvc.perform(get("/api/v1/public/wishlists/" + shareableId)
                            .header("Authorization", bearerToken(viewerToken)))
                    .andExpect(status().isOk());

            // Change to private
            String wishlistId = getWishlistIdFromShareableId(shareableId);
            UpdateWishlistRequest request = UpdateWishlistRequest.builder()
                    .visibility(Visibility.PRIVATE)
                    .build();

            mockMvc.perform(put("/api/v1/wishlists/" + wishlistId)
                            .header("Authorization", bearerToken(ownerToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            // Access without the access-code header now surfaces the gate
            mockMvc.perform(get("/api/v1/public/wishlists/" + shareableId)
                            .header("Authorization", bearerToken(viewerToken)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("ACCESS_CODE_REQUIRED"));
        }

        @Test
        @DisplayName("should show wishlist when visibility changes to PUBLIC")
        void shouldShowWishlistWhenVisibilityChangesToPublic() throws Exception {
            String shareableId = createPrivateWishlist("Initially Private");

            // Pre-flip, the PRIVATE wishlist surfaces the gate (feature 008 /
            // T6 — was 404 pre-008).
            mockMvc.perform(get("/api/v1/public/wishlists/" + shareableId)
                            .header("Authorization", bearerToken(viewerToken)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("ACCESS_CODE_REQUIRED"));

            // Change to public
            String wishlistId = getWishlistIdFromShareableId(shareableId);
            UpdateWishlistRequest request = UpdateWishlistRequest.builder()
                    .visibility(Visibility.PUBLIC)
                    .build();

            mockMvc.perform(put("/api/v1/wishlists/" + wishlistId)
                            .header("Authorization", bearerToken(ownerToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            // Access should now work
            mockMvc.perform(get("/api/v1/public/wishlists/" + shareableId)
                            .header("Authorization", bearerToken(viewerToken)))
                    .andExpect(status().isOk());
        }
    }

    private String createPublicWishlist(String title) throws Exception {
        CreateWishlistRequest request = CreateWishlistRequest.builder()
                .title(title)
                .description("A public wishlist for testing")
                .visibility(Visibility.PUBLIC)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/wishlists")
                        .header("Authorization", bearerToken(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("shareableId").asText();
    }

    private String createPrivateWishlist(String title) throws Exception {
        CreateWishlistRequest request = CreateWishlistRequest.builder()
                .title(title)
                .visibility(Visibility.PRIVATE)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/wishlists")
                        .header("Authorization", bearerToken(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("shareableId").asText();
    }

    private record WishlistIds(String wishlistId, String shareableId) {}

    private WishlistIds createPublicWishlistWithItems() throws Exception {
        CreateWishlistRequest wishlistRequest = CreateWishlistRequest.builder()
                .title("Wishlist With Items")
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
        String shareableId = wishlistJson.get("shareableId").asText();

        // Add items
        for (int i = 1; i <= 3; i++) {
            CreateItemRequest itemRequest = CreateItemRequest.builder()
                    .name("Item " + i)
                    .description("Description for item " + i)
                    .price(new BigDecimal("19.99"))
                    .priority(Priority.MEDIUM)
                    .build();

            mockMvc.perform(post("/api/v1/wishlists/" + wishlistId + "/items")
                            .header("Authorization", bearerToken(ownerToken))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(itemRequest)))
                    .andExpect(status().isCreated());
        }

        return new WishlistIds(wishlistId, shareableId);
    }

    private String getWishlistIdFromShareableId(String shareableId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/wishlists")
                        .header("Authorization", bearerToken(ownerToken)))
                .andExpect(status().isOk())
                .andReturn();

        var wishlists = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("wishlists");

        for (var wishlist : wishlists) {
            if (wishlist.get("shareableId").asText().equals(shareableId)) {
                return wishlist.get("id").asText();
            }
        }
        throw new RuntimeException("Wishlist not found for shareableId: " + shareableId);
    }
}
