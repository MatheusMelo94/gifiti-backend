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
@Schema(description = "Google Sign-In credentials")
public class GoogleLoginRequest {

    @NotBlank(message = "{validation.googlelogin.idtoken.notblank}")
    @Schema(description = "Google ID token from Sign-In SDK")
    private String idToken;
}
