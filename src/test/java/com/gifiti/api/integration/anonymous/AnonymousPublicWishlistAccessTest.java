package com.gifiti.api.integration.anonymous;

import com.gifiti.api.dto.response.PublicWishlistResponse;
import com.gifiti.api.integration.BaseIntegrationTest;
import com.gifiti.api.model.User;
import com.gifiti.api.model.Wishlist;
import com.gifiti.api.model.WishlistItem;
import com.gifiti.api.model.enums.ItemStatus;
import com.gifiti.api.model.enums.Priority;
import com.gifiti.api.model.enums.Visibility;
import com.gifiti.api.repository.UserRepository;
import com.gifiti.api.repository.WishlistItemRepository;
import com.gifiti.api.repository.WishlistRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Feature 006 — Anonymous Public Wishlist Viewing.
 *
 * <p>Asserts the SecurityConfig + controller carve-out for unauthenticated
 * GETs on shared wishlists, plus the F-2 mitigation (localized owner
 * displayName fallback). Reserve writes and any non-GET method on the same
 * namespace must continue to require authentication.
 *
 * <p>Per Architect plan § 5 (Test strategy) — case enumeration:
 * <ol>
 *   <li>Anonymous GET → 200 with the public DTO.</li>
 *   <li>Authenticated GET still → 200 (regression).</li>
 *   <li>Anonymous reserve POST → 401 (matcher specificity).</li>
 *   <li>Anonymous GET on non-existent shareableId → 404 (path reachable, not 401).</li>
 *   <li>Anonymous GET on PRIVATE wishlist → 404 (anti-enumeration parity).</li>
 *   <li>Method matrix POST/PUT/PATCH/DELETE on the bare path → 401.</li>
 *   <li>F-2: anonymous GET response uses localized fallback when owner
 *       displayName is null/blank (en-US: "Wishlist owner";
 *       pt-BR: "Anônimo"). Stays RED until Task 3.</li>
 * </ol>
 *
 * <p>Cite: {@code architecture-conventions.md § Authentication & Authorization},
 * {@code § Testing}; Security findings F-1, F-2.
 */
class AnonymousPublicWishlistAccessTest extends BaseIntegrationTest {

    @Autowired
    private WishlistRepository wishlistRepository;

    @Autowired
    private WishlistItemRepository wishlistItemRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * Seed a verified user with the given displayName (or null) and return the
     * persisted user document for follow-up wishlist seeding.
     */
    private User seedOwner(String email, String password, String displayName) throws Exception {
        registerTestUser(email, password);
        markEmailVerified(email);
        // displayName is set during registration to null by default; force it
        // explicitly so tests don't rely on registration-side behavior.
        User user = userRepository.findByEmail(email).orElseThrow();
        user.setDisplayName(displayName);
        return userRepository.save(user);
    }

    /**
     * Seed a wishlist owned by the given user with the given visibility, plus
     * one item. Returns the persisted wishlist (with assigned shareableId).
     */
    private Wishlist seedWishlist(User owner, Visibility visibility, String title) {
        Wishlist wishlist = Wishlist.builder()
                .ownerUserId(owner.getId())
                .title(title)
                .description("Sample description for anonymous-access test")
                .visibility(visibility)
                .build();
        wishlist = wishlistRepository.save(wishlist);

        WishlistItem item = WishlistItem.builder()
                .wishlistId(wishlist.getId())
                .ownerUserId(owner.getId())
                .name("Sample item")
                .description("Item description")
                .price(new BigDecimal("19.99"))
                .priority(Priority.MEDIUM)
                .quantity(1)
                .reservedQuantity(0)
                .status(ItemStatus.AVAILABLE)
                .build();
        wishlistItemRepository.save(item);

        return wishlist;
    }

    @Test
    @DisplayName("Anonymous GET on a PUBLIC wishlist returns 200 with the public DTO")
    void anonymous_get_public_wishlist_returns_200() throws Exception {
        User owner = seedOwner("anon-owner@example.test", "BlueP4nther$Xyz2!", "Maria Silva");
        Wishlist wishlist = seedWishlist(owner, Visibility.PUBLIC, "Birthday 2026");

        mockMvc.perform(get("/api/v1/public/wishlists/{shareableId}", wishlist.getShareableId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shareableId").value(wishlist.getShareableId()))
                .andExpect(jsonPath("$.title").value("Birthday 2026"))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    @DisplayName("Authenticated GET on a PUBLIC wishlist still returns 200 (regression)")
    void authenticated_get_public_wishlist_still_returns_200() throws Exception {
        User owner = seedOwner("anon-owner-auth@example.test", "BlueP4nther$Xyz2!", "Maria Silva");
        Wishlist wishlist = seedWishlist(owner, Visibility.PUBLIC, "Birthday 2026");

        String viewerToken = createVerifiedUserAndGetToken(
                "anon-viewer@example.test", "BlueP4nther$Xyz2!");

        mockMvc.perform(get("/api/v1/public/wishlists/{shareableId}", wishlist.getShareableId())
                        .header("Authorization", bearerToken(viewerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shareableId").value(wishlist.getShareableId()));
    }

    @Test
    @DisplayName("Anonymous POST on /reserve returns 401 (matcher specificity verification)")
    void anonymous_reserve_post_returns_401() throws Exception {
        User owner = seedOwner("anon-reserve-owner@example.test", "BlueP4nther$Xyz2!", "Maria Silva");
        Wishlist wishlist = seedWishlist(owner, Visibility.PUBLIC, "Birthday 2026");
        String itemId = wishlistItemRepository.findByWishlistId(wishlist.getId()).get(0).getId();

        mockMvc.perform(post(
                        "/api/v1/public/wishlists/{shareableId}/items/{itemId}/reserve",
                        wishlist.getShareableId(), itemId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Anonymous GET on non-existent shareableId returns 404 (path reachable, not 401)")
    void anonymous_get_unknown_shareable_id_returns_404() throws Exception {
        mockMvc.perform(get("/api/v1/public/wishlists/{shareableId}", "does-not-exist-zzz"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Anonymous GET on a PRIVATE wishlist returns 403 ACCESS_CODE_REQUIRED (feature 008 / T6 inverts the 006 posture)")
    void anonymous_get_private_wishlist_returns_403_access_code_required() throws Exception {
        // Feature 008 / T6 + ADR 0008 § 3 ratify the privacy-posture
        // inversion: PRIVATE wishlists now return 403 with an
        // ACCESS_CODE_REQUIRED discriminator so the frontend gate UX can
        // distinguish "right link, need code" from "wrong link". Security
        // findings F-1 (CONCUR-WITH-ARCHITECT) accepts the trade given the
        // 21-char NanoID makes random enumeration infeasible regardless.
        User owner = seedOwner("anon-private-owner@example.test", "BlueP4nther$Xyz2!", "Maria Silva");
        Wishlist wishlist = seedWishlist(owner, Visibility.PRIVATE, "Private list");

        mockMvc.perform(get("/api/v1/public/wishlists/{shareableId}", wishlist.getShareableId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_CODE_REQUIRED"));
    }

    @ParameterizedTest(name = "Anonymous {0} on the bare path returns 401 (method-specificity matrix)")
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    void anonymous_non_get_method_on_bare_path_returns_401(String method) throws Exception {
        // Use a syntactically valid shareableId; we don't care whether it
        // resolves — what we care about is that the security layer rejects
        // the request before any controller mapping is consulted.
        String path = "/api/v1/public/wishlists/some-shareable-id";

        var resultActions = switch (method) {
            case "POST" -> mockMvc.perform(post(path));
            case "PUT" -> mockMvc.perform(put(path));
            case "PATCH" -> mockMvc.perform(patch(path));
            case "DELETE" -> mockMvc.perform(delete(path));
            default -> throw new IllegalArgumentException("Unsupported method: " + method);
        };

        // 401 is the only acceptable status; 200 would mean the GET-scoped
        // permitAll() bled into write methods on the same path.
        resultActions.andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("F-2: anonymous GET with en-US returns localized 'Wishlist owner' when displayName is null")
    void anonymous_get_returns_localized_fallback_for_null_displayname_en_us() throws Exception {
        User owner = seedOwner("f2-owner-en@example.test", "BlueP4nther$Xyz2!", null);
        Wishlist wishlist = seedWishlist(owner, Visibility.PUBLIC, "Birthday 2026");

        MvcResult result = mockMvc.perform(get(
                        "/api/v1/public/wishlists/{shareableId}", wishlist.getShareableId())
                        .header("Accept-Language", "en-US"))
                .andExpect(status().isOk())
                .andReturn();

        PublicWishlistResponse body = objectMapper.readValue(
                result.getResponse().getContentAsString(), PublicWishlistResponse.class);

        assertThat(body.getOwnerDisplayName())
                .as("F-2 mitigation: null displayName resolves through MessageSource, "
                        + "not email-derived fallback")
                .isEqualTo("Wishlist owner");
        assertThat(body.getOwnerDisplayName())
                .as("F-2 regression guard: response must NOT contain the email local-part")
                .doesNotContain("f2-owner-en");
    }

    @Test
    @DisplayName("F-2: anonymous GET with pt-BR returns localized 'Anônimo' when displayName is null")
    void anonymous_get_returns_localized_fallback_for_null_displayname_pt_br() throws Exception {
        User owner = seedOwner("f2-owner-pt@example.test", "BlueP4nther$Xyz2!", null);
        Wishlist wishlist = seedWishlist(owner, Visibility.PUBLIC, "Lista de aniversário");

        MvcResult result = mockMvc.perform(get(
                        "/api/v1/public/wishlists/{shareableId}", wishlist.getShareableId())
                        .header("Accept-Language", "pt-BR"))
                .andExpect(status().isOk())
                .andReturn();

        PublicWishlistResponse body = objectMapper.readValue(
                result.getResponse().getContentAsString(), PublicWishlistResponse.class);

        assertThat(body.getOwnerDisplayName())
                .as("F-2 mitigation: pt-BR Accept-Language resolves to localized fallback")
                .isEqualTo("Anônimo");
        assertThat(body.getOwnerDisplayName())
                .as("F-2 regression guard: response must NOT contain the email local-part")
                .doesNotContain("f2-owner-pt");
    }
}
