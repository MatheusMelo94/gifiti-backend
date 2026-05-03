package com.gifiti.api.service;

import com.gifiti.api.dto.request.UpdateProfileRequest;
import com.gifiti.api.dto.response.ProfileResponse;
import com.gifiti.api.exception.ResourceNotFoundException;
import com.gifiti.api.model.User;
import com.gifiti.api.model.enums.Language;
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
     * <p>Partial-update semantics: a {@code null} field on the request leaves
     * the corresponding persisted value untouched. The {@code preferredLanguage}
     * field carries Security finding F-3 (audit log on language change): every
     * actual change of {@code preferredLanguage} (old != new, with legacy null
     * normalized to {@link Language#EN_US} via
     * {@link User#effectiveLanguage()}) emits one INFO log line with the
     * masked email and old/new values for forensic visibility on
     * phishing/account-takeover investigations. A no-op (request value equal
     * to current value) emits nothing — the audit log captures intent
     * changes, not request volume.
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

        if (request.getPreferredLanguage() != null) {
            Language oldLang = user.effectiveLanguage();
            Language newLang = request.getPreferredLanguage();
            if (oldLang != newLang) {
                user.setPreferredLanguage(newLang);
                log.info("Profile preference changed: user={}, field=preferredLanguage, from={}, to={}",
                        maskEmail(user.getEmail()), oldLang, newLang);
            }
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
                .preferredLanguage(user.effectiveLanguage())
                .build();
    }

    /**
     * Masks an email address for safe inclusion in audit logs (Security
     * finding F-3). Pattern: first two characters of the local part, then
     * {@code ***}, then the {@code @domain} suffix unchanged. Mirrors the
     * shape used by {@code AccountLockoutService.maskEmail} so log readers
     * see one consistent style across audit/lockout lines.
     *
     * <p>Inlined here rather than extracted to {@code util/} because the
     * shared util class does not yet exist; consolidation can happen in a
     * later refactor commit without changing any caller's contract.
     */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 2) {
            return "***" + email.substring(atIndex);
        }
        return email.substring(0, 2) + "***" + email.substring(atIndex);
    }
}
