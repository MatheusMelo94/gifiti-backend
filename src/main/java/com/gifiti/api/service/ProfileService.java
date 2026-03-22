package com.gifiti.api.service;

import com.gifiti.api.dto.request.UpdateProfileRequest;
import com.gifiti.api.dto.response.ProfileResponse;
import com.gifiti.api.exception.ResourceNotFoundException;
import com.gifiti.api.model.User;
import com.gifiti.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for user profile operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;

    /**
     * Get the current user's profile.
     *
     * @param email Authenticated user's email
     * @return Profile response
     */
    public ProfileResponse getProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        return toProfileResponse(user);
    }

    /**
     * Update the current user's profile.
     *
     * @param email Authenticated user's email
     * @param request Update request with new profile fields
     * @return Updated profile response
     */
    public ProfileResponse updateProfile(String email, UpdateProfileRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        if (request.getDisplayName() != null) {
            user.setDisplayName(request.getDisplayName());
        }

        User savedUser = userRepository.save(user);
        log.info("Profile updated for user: {}", savedUser.getId());

        return toProfileResponse(savedUser);
    }

    private ProfileResponse toProfileResponse(User user) {
        return ProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .profilePictureUrl(user.getProfilePictureUrl())
                .roles(user.getRoles())
                .emailVerified(user.isEmailVerified())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
