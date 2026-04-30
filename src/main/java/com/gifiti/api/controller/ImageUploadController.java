package com.gifiti.api.controller;

import com.gifiti.api.config.RateLimitConfig;
import com.gifiti.api.dto.response.ErrorResponse;
import com.gifiti.api.dto.response.ImageUploadResponse;
import com.gifiti.api.service.ImageUploadService;
import com.gifiti.api.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST controller for image uploads.
 * Uploads images to Cloudflare R2 for use as item photos or wishlist covers.
 */
@Slf4j
@RestController
@RequestMapping(path = "/api/v1/uploads", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Uploads", description = "Image upload operations (authenticated)")
public class ImageUploadController {

    private final ImageUploadService uploadService;
    private final UserService userService;
    private final RateLimitConfig rateLimitConfig;

    /**
     * Upload an image file for use as a wishlist item photo or wishlist cover.
     *
     * POST /api/v1/uploads/image
     */
    @Operation(
            summary = "Upload an image",
            description = "Upload an image for use as a wishlist item photo or wishlist cover. Max 5MB. Accepted formats: JPEG, PNG, WebP.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Image uploaded successfully"),
                    @ApiResponse(responseCode = "400", description = "Invalid file or parameters",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Not authenticated"),
                    @ApiResponse(responseCode = "429", description = "Upload rate limit exceeded",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
            })
    @PostMapping(path = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageUploadResponse> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam("context") String context,
            @AuthenticationPrincipal UserDetails userDetails) {

        String userId = getUserId(userDetails);
        log.debug("Image upload request from user: {}, context: {}", userDetails.getUsername(), context);

        // Rate limiting — per user, not per IP
        if (!rateLimitConfig.tryConsumeUpload(userId)) {
            log.warn("Upload rate limit exceeded for user: {}", userDetails.getUsername());
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }

        String url = uploadService.upload(file, context, userId);

        log.info("Image uploaded successfully for user: {}, context: {}", userDetails.getUsername(), context);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ImageUploadResponse.builder().url(url).build());
    }

    private String getUserId(UserDetails userDetails) {
        return userService.findByEmail(userDetails.getUsername()).getId();
    }
}
