package com.gifiti.api.controller;

import com.gifiti.api.dto.request.LoginRequest;
import com.gifiti.api.dto.request.RefreshTokenRequest;
import com.gifiti.api.dto.request.RegisterRequest;
import com.gifiti.api.dto.response.AuthResponse;
import com.gifiti.api.dto.response.RegisterResponse;
import com.gifiti.api.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for authentication endpoints.
 * Handles user registration, login, and token refresh.
 *
 * Security hardening (L-01):
 * - Explicit consumes/produces for Content-Type validation
 * - Only accepts application/json requests
 * - Returns application/json responses
 */
@Slf4j
@RestController
@RequestMapping(
        path = "/api/v1/auth",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
)
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Register a new user.
     *
     * POST /api/v1/auth/register
     *
     * @param request Registration details (email, password)
     * @return 201 Created with user details, or 409 Conflict if email exists
     */
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.debug("Registration request received for email: {}", request.getEmail());
        RegisterResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Authenticate user and return JWT tokens.
     *
     * POST /api/v1/auth/login
     *
     * @param request Login credentials (email, password)
     * @return 200 OK with tokens, or 401 Unauthorized if credentials invalid
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.debug("Login request received for email: {}", request.getEmail());
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Refresh access token using a valid refresh token.
     *
     * POST /api/v1/auth/refresh
     *
     * Security (C-01 fix):
     * - Validates refresh token signature and expiration
     * - Issues new access token without requiring password
     * - Returns same refresh token (no rotation to prevent token replay issues)
     * - Rate limited via RateLimitFilter
     *
     * @param request Refresh token
     * @return 200 OK with new access token, or 401 Unauthorized if token invalid
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        log.debug("Token refresh request received");
        AuthResponse response = authService.refresh(request);
        return ResponseEntity.ok(response);
    }
}
