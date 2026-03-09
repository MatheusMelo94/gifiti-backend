package com.gifiti.api.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.gifiti.api.model.enums.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Response DTO for successful authentication.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Authentication response with JWT tokens and user info")
public class AuthResponse {

    @Schema(description = "Authenticated user details")
    private UserInfo user;

    @JsonIgnore
    private String accessToken;

    @JsonIgnore
    private String refreshToken;

    @Schema(description = "Access token TTL in seconds", example = "3600")
    private long expiresIn;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Basic user information")
    public static class UserInfo {
        @Schema(description = "User ID", example = "65f1a2b3c4d5e6f7a8b9c0d1")
        private String id;

        @Schema(description = "User email", example = "jane@example.com")
        private String email;

        @Schema(description = "Display name", example = "Maria Santos")
        private String displayName;

        @Schema(description = "User roles", example = "[\"USER\"]")
        private Set<Role> roles;
    }
}
