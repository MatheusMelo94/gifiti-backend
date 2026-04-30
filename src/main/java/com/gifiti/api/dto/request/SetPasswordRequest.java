package com.gifiti.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Set password for Google-only account")
public class SetPasswordRequest {

    @NotBlank(message = "Password is required")
    @Schema(description = "New password to set")
    private String newPassword;
}
