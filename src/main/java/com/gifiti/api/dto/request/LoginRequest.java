package com.gifiti.api.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for user login.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Login credentials")
public class LoginRequest {

    @NotBlank(message = "{validation.shared.email.notblank}")
    @Email(message = "{validation.shared.email.invalid}")
    @Schema(description = "User email address", example = "jane@example.com")
    private String email;

    @NotBlank(message = "{validation.shared.password.notblank}")
    @Schema(description = "User password", example = "MySecureP@ss1")
    private String password;
}
