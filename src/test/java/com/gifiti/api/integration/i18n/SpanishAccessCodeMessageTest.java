package com.gifiti.api.integration.i18n;

import com.gifiti.api.dto.request.CreateWishlistRequest;
import com.gifiti.api.integration.BaseIntegrationTest;
import com.gifiti.api.model.enums.Visibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Feature 009 / T5 — Spanish (es-419) rendering of the feature-008 access-code
 * error messages via the existing {@code error} flow on {@link
 * com.gifiti.api.exception.GlobalExceptionHandler}.
 *
 * <p>Pins that hitting a PRIVATE wishlist gate with {@code Accept-Language:
 * es-419} returns the Spanish translation of
 * {@code error.wishlist.access-code.required} in the {@code message} field
 * while the {@code errorCode} discriminator (ACCESS_CODE_REQUIRED) is
 * unchanged. Proves the new bundle is wired into the same MessageSource
 * pipeline as en-US / pt-BR (per ADR 0009 Decision A: SUPPORTED_LOCALES
 * derives from {@link com.gifiti.api.model.enums.Language#values()}).
 *
 * <p>No production change required — this is a pure integration regression
 * test for the bundle + resolver wiring established at T1 + T2.
 */
class SpanishAccessCodeMessageTest extends BaseIntegrationTest {

    private static final String HEADER = "X-Wishlist-Access-Code";

    private String ownerToken;

    @BeforeEach
    void setup() throws Exception {
        ownerToken = createVerifiedUserAndGetToken(
                "es419-gate-owner@example.test", "Mvn-Build-Cyan-Glow-2026!");
    }

    @Test
    @DisplayName("PRIVATE wishlist + no header + Accept-Language: es-419 → 403 with Spanish message + ACCESS_CODE_REQUIRED errorCode")
    void access_code_required_renders_in_spanish() throws Exception {
        String shareableId = createPrivateWishlist("Secreta");

        mockMvc.perform(get("/api/v1/public/wishlists/" + shareableId)
                        .header("Accept-Language", "es-419"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_CODE_REQUIRED"))
                // Spanish translation from messages_es_419.properties:
                .andExpect(jsonPath("$.message").value("Código de acceso obligatorio"));
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
}
