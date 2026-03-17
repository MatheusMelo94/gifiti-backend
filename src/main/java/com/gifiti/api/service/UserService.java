package com.gifiti.api.service;

import com.gifiti.api.exception.AccessDeniedException;
import com.gifiti.api.exception.ResourceNotFoundException;
import com.gifiti.api.model.User;
import com.gifiti.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    /**
     * Find a user by ID.
     *
     * @param id User ID
     * @return User entity
     * @throws ResourceNotFoundException if user not found
     */
    public User findById(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
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
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
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
            throw new AccessDeniedException("Email verification required. Please verify your email before performing this action.");
        }
    }
}
