package com.gifiti.api.dto.request;

import com.gifiti.api.model.enums.Language;
import com.gifiti.api.validation.NoHtml;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating user profile.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Update profile fields")
public class UpdateProfileRequest {

    @Size(max = 50, message = "{validation.shared.displayname.size}")
    @NoHtml
    @Schema(description = "Display name", example = "Maria Santos")
    private String displayName;

    /**
     * Preferred UI/email language. Optional — partial-update DTO; a {@code null}
     * value leaves the persisted preference untouched.
     *
     * <p>Wire format is the BCP-47 tag (e.g., {@code "en-US"}, {@code "pt-BR"}),
     * not the enum constant name; Jackson handles the conversion via
     * {@link Language#fromJsonTag(String)} (deserialize) and the
     * {@code @JsonValue} on {@link Language#getTag()} (serialize). Unsupported
     * tags raise {@code IllegalArgumentException}, which Spring MVC translates
     * into a 400 {@code error.request.malformed} response — satisfies spec
     * criterion #19 of {@code 005-i18n-backend-support}.
     */
    @Schema(description = "Preferred UI/email language (BCP-47 tag, en-US or pt-BR)",
            example = "pt-BR",
            allowableValues = {"en-US", "pt-BR"})
    private Language preferredLanguage;
}
