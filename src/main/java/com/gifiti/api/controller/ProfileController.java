package com.gifiti.api.controller;

import com.gifiti.api.dto.request.UpdateProfileRequest;
import com.gifiti.api.dto.response.ProfileResponse;
import com.gifiti.api.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for user profile operations.
 * Accessible by any authenticated user (USER or GIFTER).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Profile", description = "View and update user profile")
public class ProfileController {

    private final ProfileService profileService;

    @Operation(summary = "Get current user's profile")
    @GetMapping
    public ResponseEntity<ProfileResponse> getProfile(Authentication authentication) {
        ProfileResponse response = profileService.getProfile(authentication.getName());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Update current user's profile")
    @PutMapping
    public ResponseEntity<ProfileResponse> updateProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateProfileRequest request) {
        ProfileResponse response = profileService.updateProfile(authentication.getName(), request);
        return ResponseEntity.ok(response);
    }
}
