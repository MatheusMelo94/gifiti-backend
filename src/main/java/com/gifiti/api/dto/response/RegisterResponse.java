package com.gifiti.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for successful user registration.
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

    @Schema(description = "Confirmation message", example = "Registration successful")
    private String message;
}
