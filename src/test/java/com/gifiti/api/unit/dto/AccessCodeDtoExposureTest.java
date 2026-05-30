package com.gifiti.api.unit.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gifiti.api.dto.response.PublicWishlistResponse;
import com.gifiti.api.dto.response.SharedWishlistResponse;
import com.gifiti.api.dto.response.WishlistResponse;
import com.gifiti.api.mapper.WishlistMapper;
import com.gifiti.api.model.Wishlist;
import com.gifiti.api.model.enums.Visibility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for T5 — owner-only exposure of {@code accessCode} in DTOs.
 *
 * <p>Per Security findings F-4 pins (mass-assignment / field-leak coverage),
 * three explicit assertions must exist:
 *
 * <ol>
 *   <li>{@link WishlistResponse} (owner-facing) MUST include {@code accessCode}
 *       when non-null on the entity.</li>
 *   <li>{@link PublicWishlistResponse} (anonymous recipient) MUST NOT expose
 *       {@code accessCode} — the field shouldn't exist on the DTO at all, and
 *       a JSON-serialized response must not carry the key (test pins both
 *       structural absence via reflection AND serialization-time absence).</li>
 *   <li>{@link SharedWishlistResponse} (authenticated "shared with me" view)
 *       MUST NOT expose {@code accessCode} — same structural and serialization
 *       guarantees.</li>
 * </ol>
 *
 * <p>Per ADR 0008 § Decision E (plaintext storage at app layer) + Security
 * findings F-4 (highest-impact field-leak surface), the gate UX requires the
 * code be visible to the OWNER only; downstream views see only the wishlist
 * body, never the code.
 */
class AccessCodeDtoExposureTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("WishlistResponse exposes accessCode for owners (PRIVATE wishlist)")
    void wishlistResponseIncludesAccessCodeForOwner() throws Exception {
        WishlistResponse response = WishlistResponse.builder()
                .id("w1")
                .title("Birthday")
                .visibility(Visibility.PRIVATE)
                .accessCode("1234")
                .build();

        String json = objectMapper.writeValueAsString(response);
        assertThat(json)
                .as("owner-facing WishlistResponse must serialize accessCode")
                .contains("\"accessCode\":\"1234\"");
    }

    @Test
    @DisplayName("WishlistResponse omits accessCode when null (PUBLIC wishlist)")
    void wishlistResponseOmitsNullAccessCode() throws Exception {
        WishlistResponse response = WishlistResponse.builder()
                .id("w1")
                .title("Public")
                .visibility(Visibility.PUBLIC)
                .accessCode(null)
                .build();

        // Per the @JsonInclude(NON_NULL) settings on the DTO family, null
        // fields should not be serialized.
        String json = objectMapper.writeValueAsString(response);
        assertThat(json)
                .as("WishlistResponse must NOT emit accessCode when null (PUBLIC case)")
                .doesNotContain("accessCode");
    }

    @Test
    @DisplayName("PublicWishlistResponse does NOT declare accessCode field (mass-assignment safety)")
    void publicWishlistResponseDoesNotDeclareAccessCodeField() {
        // F-4 pin: even reflection should not find an accessCode field on the
        // public-facing DTO. The discriminator (PRIVATE vs PUBLIC) is in the
        // HTTP layer (403 vs 200), never in the wishlist body.
        boolean hasField = false;
        for (var field : PublicWishlistResponse.class.getDeclaredFields()) {
            if (field.getName().equals("accessCode")) {
                hasField = true;
                break;
            }
        }
        assertThat(hasField)
                .as("PublicWishlistResponse MUST NOT declare an accessCode field "
                        + "(Security F-4 mass-assignment/serialization safety)")
                .isFalse();
    }

    @Test
    @DisplayName("PublicWishlistResponse JSON serialization never includes accessCode")
    void publicWishlistResponseDoesNotExposeAccessCode() throws Exception {
        PublicWishlistResponse response = PublicWishlistResponse.builder()
                .shareableId("share-1")
                .title("Birthday")
                .ownerDisplayName("Maria")
                .itemCount(0)
                .build();

        String json = objectMapper.writeValueAsString(response);
        assertThat(json)
                .as("PublicWishlistResponse JSON MUST never contain accessCode (F-4)")
                .doesNotContain("accessCode");
    }

    @Test
    @DisplayName("SharedWishlistResponse does NOT declare accessCode field (F-4 pin)")
    void sharedWishlistResponseDoesNotDeclareAccessCodeField() {
        boolean hasField = false;
        for (var field : SharedWishlistResponse.class.getDeclaredFields()) {
            if (field.getName().equals("accessCode")) {
                hasField = true;
                break;
            }
        }
        assertThat(hasField)
                .as("SharedWishlistResponse MUST NOT declare an accessCode field "
                        + "(Security F-4 — 'shared with me' view leaks would expose every "
                        + "PRIVATE access code the user has been gifted access to)")
                .isFalse();
    }

    @Test
    @DisplayName("SharedWishlistResponse JSON serialization never includes accessCode")
    void sharedWishlistResponseDoesNotExposeAccessCode() throws Exception {
        SharedWishlistResponse response = SharedWishlistResponse.builder()
                .shareableId("share-1")
                .title("Birthday")
                .ownerDisplayName("Maria")
                .itemCount(5)
                .myReservationCount(2)
                .build();

        String json = objectMapper.writeValueAsString(response);
        assertThat(json)
                .as("SharedWishlistResponse JSON MUST never contain accessCode (F-4)")
                .doesNotContain("accessCode");
    }

    @Test
    @DisplayName("WishlistMapper.toResponse copies accessCode from entity to owner DTO")
    void mapperPropagatesAccessCodeToOwnerResponse() {
        WishlistMapper mapper = new WishlistMapper();
        Wishlist entity = Wishlist.builder()
                .id("w1")
                .ownerUserId("u1")
                .title("Birthday")
                .visibility(Visibility.PRIVATE)
                .accessCode("4242")
                .build();

        WishlistResponse response = mapper.toResponse(entity, 0);

        assertThat(response.getAccessCode())
                .as("mapper must propagate accessCode entity → owner DTO")
                .isEqualTo("4242");
    }

    @Test
    @DisplayName("WishlistMapper.toResponse leaves accessCode null for PUBLIC wishlists")
    void mapperLeavesAccessCodeNullForPublic() {
        WishlistMapper mapper = new WishlistMapper();
        Wishlist entity = Wishlist.builder()
                .id("w1")
                .ownerUserId("u1")
                .title("Public")
                .visibility(Visibility.PUBLIC)
                .accessCode(null)
                .build();

        WishlistResponse response = mapper.toResponse(entity, 0);

        assertThat(response.getAccessCode()).isNull();
    }
}
