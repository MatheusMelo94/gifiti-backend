package com.gifiti.api.service;

import com.gifiti.api.dto.i18n.LocalizedMessage;
import com.gifiti.api.dto.response.MessageResponse;
import com.gifiti.api.exception.AccessDeniedException;
import com.gifiti.api.exception.ResourceNotFoundException;
import com.gifiti.api.model.User;
import com.gifiti.api.model.enums.AuthProvider;
import com.gifiti.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service for user lookup and validation operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordValidationService passwordValidationService;

    /**
     * Find a user by ID.
     *
     * @param id User ID
     * @return User entity
     * @throws ResourceNotFoundException if user not found
     */
    public User findById(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ResourceNotFoundException.KEY_NOT_FOUND_WITH_FIELD,
                        "User", "id", id));
    }

    /**
     * Find a user by email.
     *
     * @param email User email
     * @return User entity
     * @throws ResourceNotFoundException if user not found
     */
    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ResourceNotFoundException.KEY_NOT_FOUND_WITH_FIELD,
                        "User", "email", email));
    }

    /**
     * Find a user by email, returning Optional.
     *
     * @param email User email
     * @return Optional containing user if found
     */
    public Optional<User> findByEmailOptional(String email) {
        return userRepository.findByEmail(email);
    }

    /**
     * Check if a user exists with the given email.
     *
     * @param email Email to check
     * @return true if user exists
     */
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    public String getUserIdByEmail(String email) {
        return findByEmail(email).getId();
    }

    public void requireEmailVerified(String email) {
        User user = findByEmail(email);
        if (!user.isEmailVerified()) {
            throw new AccessDeniedException("error.email.verification.required", new Object[0]);
        }
    }

    public MessageResponse setPassword(String email, String newPassword) {
        User user = findByEmail(email);

        if (user.getAuthProvider() != AuthProvider.GOOGLE) {
            throw new IllegalArgumentException("Password already set. Use forgot-password to change it.");
        }

        passwordValidationService.validate(newPassword, email);

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setAuthProvider(AuthProvider.BOTH);
        userRepository.save(user);

        log.info("Password set for Google user: {}", email);
        return MessageResponse.builder()
                .message(LocalizedMessage.of("user.password.set.success"))
                .build();
    }
}
