package com.gifiti.api.dto.request;

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

    @Size(max = 50, message = "Display name must not exceed 50 characters")
    @Schema(description = "Display name", example = "Maria Santos")
    private String displayName;
}
