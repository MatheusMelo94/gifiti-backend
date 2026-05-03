package com.gifiti.api.dto.response;

import com.gifiti.api.dto.i18n.LocalizedMessage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for successful user registration.
 *
 * <p>Per Task 7 of {@code 005-i18n-backend-support}, the {@code message} field
 * carries a {@link LocalizedMessage} (key + args) rather than a hardcoded
 * English string. The registered Jackson serializer resolves it to a plain
 * JSON string at write time using {@code LocaleContextHolder.getLocale()}, so
 * the wire shape is unchanged from the pre-i18n era.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Registration confirmation")
public class RegisterResponse {

    @Schema(description = "Created user ID", example = "65f1a2b3c4d5e6f7a8b9c0d1")
    private String id;

    @Schema(description = "Registered email", example = "jane@example.com")
    private String email;

    @Schema(description = "Display name", example = "Maria Santos")
    private String displayName;

    @Schema(description = "Localized confirmation message",
            example = "Registration successful. Please check your email to verify your account.",
            type = "string")
    private LocalizedMessage message;
}
