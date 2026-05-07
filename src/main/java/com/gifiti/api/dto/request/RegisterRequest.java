package com.gifiti.api.dto.request;

import com.gifiti.api.model.enums.SignupTrigger;
import com.gifiti.api.validation.NoHtml;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for user registration.
 *
 * Security hardening:
 * - Password minimum 12 characters (OWASP recommendation)
 * - Password maximum 128 characters (prevents DoS via BCrypt)
 * - Complexity requirements: uppercase, lowercase, digit, special char
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Registration details for a new user")
public class RegisterRequest {

    @NotBlank(message = "{validation.shared.email.notblank}")
    @Email(message = "{validation.shared.email.invalid}")
    @Size(max = 254, message = "{validation.register.email.size}")
    @Schema(description = "User email address", example = "jane@example.com")
    private String email;

    @NotBlank(message = "{validation.shared.password.notblank}")
    @Size(min = 12, max = 128, message = "{validation.shared.password.size}")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&._#^()\\-+=])[A-Za-z\\d@$!%*?&._#^()\\-+=]{12,}$",
        message = "{validation.shared.password.pattern}"
    )
    @Schema(description = "Password (12-128 chars, must include upper, lower, digit, special)", example = "MySecureP@ss1")
    private String password;

    @Size(max = 50, message = "{validation.shared.displayname.size}")
    @NoHtml
    @Schema(description = "Optional display name (derived from email if absent)", example = "Maria Santos")
    private String displayName;

    /**
     * Decision H (plan v2 §3, §5.5): optional analytics field describing the
     * UX context that originated the signup. Server-side default is
     * {@link SignupTrigger#DIRECT} if omitted; surfaced in the
     * {@code signup_completed} PostHog event as {@code signup_trigger}.
     */
    @Schema(description = "Analytics: what action triggered the signup",
            example = "CREATED_WISHLIST")
    private SignupTrigger signupTrigger;

    /**
     * Decision H: when the signup was triggered from a specific public
     * wishlist (e.g. "Create my own list" CTA on a friend's birthday list),
     * this field carries that wishlist's {@code shareableId} (NanoID, the
     * 21-char URL-safe identifier exposed in share-link URLs at
     * {@code /wishlists/share/{shareableId}}). Rejected if non-empty and
     * malformed. Surfaced as {@code referrer_wishlist_id} in the
     * {@code signup_completed} event.
     *
     * <p>Plan §5.5 originally specified "Mongo ObjectId 24-char hex". This
     * was ratified as a deviation on 2026-05-06 (post Code Reviewer Finding
     * 0001): the frontend has the {@code shareableId} from the share-link
     * URL with no extra fetch, and the analytic purpose is share-attribution
     * which keys naturally to the {@code shareableId}.
     */
    @Pattern(
            regexp = "^[A-Za-z0-9_-]{21}$|^$",
            message = "{validation.shared.wishlistId.invalid}"
    )
    @Schema(description = "Analytics: shareableId (NanoID) of the wishlist that referred this signup, if any",
            example = "V1StGXR8_Z5jdHi6B-myT")
    private String referrerWishlistId;
}
