package com.gifiti.api.service;

import com.gifiti.api.dto.request.LoginRequest;
import com.gifiti.api.dto.request.RefreshTokenRequest;
import com.gifiti.api.dto.request.RegisterRequest;
import com.gifiti.api.dto.response.AuthResponse;
import com.gifiti.api.dto.response.RegisterResponse;
import com.gifiti.api.exception.ConflictException;
import com.gifiti.api.exception.UnauthorizedException;
import com.gifiti.api.model.User;
import com.gifiti.api.model.enums.Role;
import com.gifiti.api.repository.UserRepository;
import com.gifiti.api.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Service handling user authentication and registration.
 *
 * Security hardening (H-02):
 * - Account lockout after 5 failed login attempts
 * - 30-minute lockout duration
 * - Combined with IP-based rate limiting for defense-in-depth
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final AccountLockoutService accountLockoutService;
    private final PasswordValidationService passwordValidationService;

    /**
     * Register a new user.
     *
     * Security hardening (M-05):
     * - Enhanced password validation beyond regex
     * - Checks for common patterns, email similarity, sequential chars
     *
     * @param request Registration details
     * @return Registration response with user ID and email
     * @throws ConflictException if email already exists
     * @throws IllegalArgumentException if password is weak
     */
    public RegisterResponse register(RegisterRequest request) {
        log.info("Registering new user with email: {}", request.getEmail());

        // M-05 security fix: Enhanced password validation
        passwordValidationService.validate(request.getPassword(), request.getEmail());

        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("Registration failed: email already exists: {}", request.getEmail());
            throw new ConflictException("Email already registered");
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .roles(Set.of(Role.USER))
                .build();

        User savedUser = userRepository.save(user);
        log.info("User registered successfully: {}", savedUser.getId());

        return RegisterResponse.builder()
                .id(savedUser.getId())
                .email(savedUser.getEmail())
                .message("Registration successful")
                .build();
    }

    /**
     * Authenticate a user and return JWT tokens.
     *
     * Security hardening (H-02):
     * - Checks account lockout before authentication
     * - Records failed attempts for lockout tracking
     * - Clears failed attempts on successful login
     *
     * @param request Login credentials
     * @return Auth response with access and refresh tokens
     * @throws UnauthorizedException if credentials are invalid or account is locked
     */
    public AuthResponse login(LoginRequest request) {
        log.info("Login attempt for email: {}", request.getEmail());

        // Check if account is locked (H-02 security fix)
        if (accountLockoutService.isLocked(request.getEmail())) {
            log.warn("SECURITY_EVENT: Login attempt on locked account: {}", request.getEmail());
            throw new UnauthorizedException("Account temporarily locked due to multiple failed attempts. Try again later.");
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );

            // Clear failed attempts on successful login
            accountLockoutService.recordSuccessfulLogin(request.getEmail());

            String accessToken = jwtTokenProvider.generateAccessToken(authentication);
            String refreshToken = jwtTokenProvider.generateRefreshToken(authentication);

            log.info("Login successful for email: {}", request.getEmail());

            return AuthResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .expiresIn(jwtTokenProvider.getAccessTokenExpirationInSeconds())
                    .build();

        } catch (BadCredentialsException e) {
            // Record failed attempt for lockout tracking (H-02 security fix)
            accountLockoutService.recordFailedAttempt(request.getEmail());
            log.warn("SECURITY_EVENT: Login failed for email: {} - invalid credentials", request.getEmail());
            throw new UnauthorizedException("Invalid email or password");
        }
    }

    /**
     * Refresh access token using a valid refresh token.
     *
     * Security (C-01 fix):
     * - Validates refresh token signature and expiration
     * - Issues new access token only (no new refresh token to prevent infinite extension)
     * - Logs refresh token usage for audit trail
     *
     * @param request Refresh token request
     * @return Auth response with new access token
     * @throws UnauthorizedException if refresh token is invalid or expired
     */
    public AuthResponse refresh(RefreshTokenRequest request) {
        log.debug("Processing token refresh request");

        String refreshToken = request.getRefreshToken();

        // Validate the refresh token
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            log.warn("SECURITY_EVENT: Invalid refresh token presented");
            throw new UnauthorizedException("Invalid or expired refresh token");
        }

        // Extract username from refresh token
        String username = jwtTokenProvider.getUsernameFromToken(refreshToken);

        // Verify user still exists
        if (!userRepository.existsByEmail(username)) {
            log.warn("SECURITY_EVENT: Refresh token for non-existent user: {}", username);
            throw new UnauthorizedException("Invalid refresh token");
        }

        // Generate new access token only (don't rotate refresh token)
        String newAccessToken = jwtTokenProvider.generateAccessToken(username);

        log.info("Token refreshed successfully for user: {}", username);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken) // Return same refresh token
                .expiresIn(jwtTokenProvider.getAccessTokenExpirationInSeconds())
                .build();
    }
}
