package com.gifiti.api.integration;

import com.gifiti.api.dto.response.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration tests proving Task 5's localization wiring works
 * across the real Spring stack: {@code GifitiLocaleResolver} reads
 * {@code Accept-Language}, {@code GlobalExceptionHandler} resolves the keyed
 * {@code ResourceNotFoundException} through {@code MessageSource} using that
 * locale, and the wire response carries the locale-specific text.
 *
 * <p>The pt-BR bundle still uses {@code [TODO pt-BR]} placeholders pending
 * Task 12; the assertion is therefore "the message is the placeholder string,
 * not the English one" — i.e. the locale plumbing chose the right bundle.
 *
 * <p>Lives in {@code com.gifiti.api.integration.*}, which the CI workflow
 * temporarily excludes via {@code -Dtest='!com.gifiti.api.integration.*Test'}.
 * Engineer runs this explicitly for verification; full re-inclusion happens
 * in Task 11 of the i18n feature.
 */
class GlobalExceptionHandlerLocalizationIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("Unknown wishlist with Accept-Language: pt-BR returns the pt-BR (placeholder) message")
    void exception_with_key_returns_localized_pt_BR_when_header_is_pt_BR() throws Exception {
        String token = createUserAndGetToken("alice@example.com", "ValidPass123!");

        MvcResult result = mockMvc.perform(get("/api/v1/wishlists/does-not-exist-123")
                        .header("Authorization", bearerToken(token))
                        .header("Accept-Language", "pt-BR"))
                .andExpect(status().isNotFound())
                .andReturn();

        ErrorResponse body = objectMapper.readValue(
                result.getResponse().getContentAsString(), ErrorResponse.class);

        assertThat(body.getMessage())
                .as("pt-BR bundle should resolve, returning the placeholder text")
                .startsWith("[TODO pt-BR]")
                .contains("Wishlist")
                .contains("does-not-exist-123");
    }

    @Test
    @DisplayName("Unknown wishlist with no Accept-Language returns the en-US message")
    void exception_with_key_returns_en_US_when_no_header_and_user_pref_is_default() throws Exception {
        String token = createUserAndGetToken("bob@example.com", "ValidPass123!");

        MvcResult result = mockMvc.perform(get("/api/v1/wishlists/does-not-exist-456")
                        .header("Authorization", bearerToken(token)))
                .andExpect(status().isNotFound())
                .andReturn();

        ErrorResponse body = objectMapper.readValue(
                result.getResponse().getContentAsString(), ErrorResponse.class);

        assertThat(body.getMessage())
                .as("en-US bundle should resolve, returning the English text")
                .doesNotStartWith("[TODO pt-BR]")
                .contains("Wishlist")
                .contains("does-not-exist-456")
                .contains("not found");
    }
}
