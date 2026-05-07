package com.gifiti.api.unit.dto;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gifiti.api.dto.request.RegisterRequest;
import com.gifiti.api.model.Wishlist;
import com.gifiti.api.model.enums.SignupTrigger;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Decision H tests (plan v2 §3, §5.5, post Code Reviewer Finding 0001 update
 * 2026-05-06): {@link RegisterRequest} carries two optional analytics fields,
 * {@code signupTrigger} (enum) and {@code referrerWishlistId} (NanoID-shaped
 * wishlist {@code shareableId}). Both must be optional; both must be
 * validated when provided.
 */
class RegisterRequestSignupTriggerTest {

    private static Validator validator() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            return factory.getValidator();
        }
    }

    private static RegisterRequest baseValid() {
        return RegisterRequest.builder()
                .email("user@example.test")
                .password("SecureP@ss123!")
                .build();
    }

    @Test
    @DisplayName("RegisterRequest accepts a valid signupTrigger + referrerWishlistId (NanoID shareableId)")
    void accepts_valid_trigger_and_referrer() {
        RegisterRequest request = baseValid();
        request.setSignupTrigger(SignupTrigger.CREATED_WISHLIST);
        // shareableId is a 21-char NanoID using the default jnanoid alphabet
        // [A-Za-z0-9_-] — matches Wishlist.shareableId production generation.
        request.setReferrerWishlistId("V1StGXR8_Z5jdHi6B-myT");

        Set<ConstraintViolation<RegisterRequest>> violations = validator().validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("RegisterRequest accepts a referrerWishlistId equal to a real Wishlist.shareableId from the production generator")
    void accepts_referrer_from_production_nanoid_generator() {
        // Finding 0001 regression guard: lift the shareableId verbatim from
        // the same generator Wishlist uses in production. This test fails
        // against the legacy UUID-shaped regex and passes against the
        // NanoID-shaped regex.
        Wishlist wishlist = new Wishlist();
        wishlist.setShareableId(NanoIdUtils.randomNanoId());

        RegisterRequest request = baseValid();
        request.setSignupTrigger(SignupTrigger.CREATED_WISHLIST);
        request.setReferrerWishlistId(wishlist.getShareableId());

        Set<ConstraintViolation<RegisterRequest>> violations = validator().validate(request);

        assertThat(violations)
                .as("referrerWishlistId of shape produced by NanoIdUtils.randomNanoId() must validate")
                .noneMatch(v -> v.getPropertyPath().toString().equals("referrerWishlistId"));
    }

    @Test
    @DisplayName("RegisterRequest accepts a null signupTrigger and a null referrerWishlistId")
    void accepts_null_optional_fields() {
        RegisterRequest request = baseValid();
        request.setSignupTrigger(null);
        request.setReferrerWishlistId(null);

        Set<ConstraintViolation<RegisterRequest>> violations = validator().validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("RegisterRequest accepts an empty referrerWishlistId (treated like null)")
    void accepts_empty_referrer() {
        RegisterRequest request = baseValid();
        request.setReferrerWishlistId("");

        Set<ConstraintViolation<RegisterRequest>> violations = validator().validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("RegisterRequest rejects a malformed referrerWishlistId")
    void rejects_malformed_referrer() {
        RegisterRequest request = baseValid();
        request.setReferrerWishlistId("not-a-shareable-id");

        Set<ConstraintViolation<RegisterRequest>> violations = validator().validate(request);

        assertThat(violations)
                .anyMatch(v -> v.getPropertyPath().toString().equals("referrerWishlistId"));
    }

    @Test
    @DisplayName("RegisterRequest rejects a UUID-shaped referrerWishlistId with the wishlistId.invalid i18n key (Finding 0001)")
    void rejects_uuid_shaped_referrer_with_wishlistId_key() {
        // Finding 0001 regression guard: the legacy regex accepted UUID
        // shapes; the corrected regex rejects them. The constraint must
        // surface the new i18n key validation.shared.wishlistId.invalid.
        RegisterRequest request = baseValid();
        request.setReferrerWishlistId("123e4567-e89b-12d3-a456-426614174000");

        Set<ConstraintViolation<RegisterRequest>> violations = validator().validate(request);

        assertThat(violations)
                .filteredOn(v -> v.getPropertyPath().toString().equals("referrerWishlistId"))
                .extracting(ConstraintViolation::getMessageTemplate)
                .containsExactly("{validation.shared.wishlistId.invalid}");
    }

    @Test
    @DisplayName("Jackson rejects an invalid signupTrigger enum value during deserialization")
    void jackson_rejects_invalid_enum() {
        ObjectMapper mapper = new ObjectMapper();
        String json = """
                {
                  "email": "user@example.test",
                  "password": "SecureP@ss123!",
                  "signupTrigger": "INVALID_VALUE"
                }
                """;

        assertThatThrownBy(() -> mapper.readValue(json, RegisterRequest.class))
                .hasMessageContaining("INVALID_VALUE");
    }

    @Test
    @DisplayName("SignupTrigger enum exposes the three documented values")
    void enum_has_three_values() {
        assertThat(SignupTrigger.values())
                .containsExactlyInAnyOrder(
                        SignupTrigger.CREATED_WISHLIST,
                        SignupTrigger.RESERVED_ITEM,
                        SignupTrigger.DIRECT);
    }
}
