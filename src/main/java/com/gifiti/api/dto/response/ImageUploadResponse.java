package com.gifiti.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for image upload containing the public URL.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Image upload result")
public class ImageUploadResponse {

    @Schema(description = "Public URL of the uploaded image",
            example = "https://pub-abc123.r2.dev/users/user1/items/550e8400.jpg")
    private String url;
}
