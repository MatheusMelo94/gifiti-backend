package com.gifiti.api.dto.response;

import com.gifiti.api.model.enums.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;

/**
 * Response DTO for user profile.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "User profile details")
public class ProfileResponse {

    @Schema(description = "User ID", example = "65f1a2b3c4d5e6f7a8b9c0d1")
    private String id;

    @Schema(description = "Email address", example = "maria@gmail.com")
    private String email;

    @Schema(description = "Display name", example = "Maria Santos")
    private String displayName;

    @Schema(description = "Profile picture URL")
    private String profilePictureUrl;

    @Schema(description = "User roles", example = "[\"USER\"]")
    private Set<Role> roles;

    @Schema(description = "Whether email is verified", example = "true")
    private boolean emailVerified;

    @Schema(description = "Account creation timestamp")
    private Instant createdAt;
}
