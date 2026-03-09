package com.gifiti.api.service;

import com.gifiti.api.dto.request.LoginRequest;
import com.gifiti.api.dto.request.RefreshTokenRequest;
import com.gifiti.api.dto.request.RegisterRequest;
import com.gifiti.api.dto.response.AuthResponse;
import com.gifiti.api.dto.response.MessageResponse;
import com.gifiti.api.dto.response.RegisterResponse;
import com.gifiti.api.exception.ConflictException;
import com.gifiti.api.exception.UnauthorizedException;
import com.gifiti.api.model.BlacklistedToken;
import com.gifiti.api.model.User;
import com.gifiti.api.model.enums.Role;
import com.gifiti.api.repository.BlacklistedTokenRepository;
import com.gifiti.api.repository.UserRepository;
import com.gifiti.api.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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
    private final BlacklistedTokenRepository blacklistedTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final AccountLockoutService accountLockoutService;
    private final PasswordValidationService passwordValidationService;
    private final EmailService emailService;

    @Value("${app.base-url}")
    private String baseUrl;

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

        // Derive displayName if not provided
        String displayName = request.getDisplayName();
        if (displayName == null || displayName.isBlank()) {
            displayName = request.getEmail().split("@")[0];
        }

        String verificationToken = UUID.randomUUID().toString();

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .displayName(displayName)
                .roles(Set.of(Role.USER))
                .verificationToken(verificationToken)
                .verificationTokenExpiry(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();

        User savedUser = userRepository.save(user);
        log.info("User registered successfully: {}", savedUser.getId());

        sendVerificationEmail(savedUser.getEmail(), verificationToken);

        return RegisterResponse.builder()
                .id(savedUser.getId())
                .email(savedUser.getEmail())
                .displayName(savedUser.getDisplayName())
                .message("Registration successful. Please check your email to verify your account.")
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

            User user = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

            String accessToken = jwtTokenProvider.generateAccessToken(authentication);
            String refreshToken = jwtTokenProvider.generateRefreshToken(authentication);

            log.info("Login successful for email: {}", request.getEmail());

            return AuthResponse.builder()
                    .user(AuthResponse.UserInfo.builder()
                            .id(user.getId())
                            .email(user.getEmail())
                            .displayName(user.getDisplayName())
                            .roles(user.getRoles())
                            .build())
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
        return refreshFromToken(request.getRefreshToken());
    }

    public AuthResponse refreshFromToken(String refreshToken) {
        log.debug("Processing token refresh request");

        // Validate the refresh token
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            log.warn("SECURITY_EVENT: Invalid refresh token presented");
            throw new UnauthorizedException("Invalid or expired refresh token");
        }

        // Extract username from refresh token
        String username = jwtTokenProvider.getUsernameFromToken(refreshToken);

        // Verify user still exists
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> {
                    log.warn("SECURITY_EVENT: Refresh token for non-existent user: {}", username);
                    return new UnauthorizedException("Invalid refresh token");
                });

        // Generate new access token only (don't rotate refresh token)
        String newAccessToken = jwtTokenProvider.generateAccessToken(username);

        log.info("Token refreshed successfully for user: {}", username);

        return AuthResponse.builder()
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .displayName(user.getDisplayName())
                        .roles(user.getRoles())
                        .build())
                .accessToken(newAccessToken)
                .refreshToken(refreshToken) // Return same refresh token
                .expiresIn(jwtTokenProvider.getAccessTokenExpirationInSeconds())
                .build();
    }

    public MessageResponse verifyEmail(String token) {
        User user = userRepository.findByVerificationToken(token)
                .orElseThrow(() -> new UnauthorizedException("Invalid verification token"));

        if (user.getVerificationTokenExpiry().isBefore(Instant.now())) {
            throw new UnauthorizedException("Verification token has expired");
        }

        user.setEmailVerified(true);
        user.setVerificationToken(null);
        user.setVerificationTokenExpiry(null);
        userRepository.save(user);

        log.info("Email verified for user: {}", user.getEmail());
        return MessageResponse.builder().message("Email verified successfully").build();
    }

    public MessageResponse resendVerification(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        if (user.isEmailVerified()) {
            return MessageResponse.builder().message("Email is already verified").build();
        }

        String token = UUID.randomUUID().toString();
        user.setVerificationToken(token);
        user.setVerificationTokenExpiry(Instant.now().plus(24, ChronoUnit.HOURS));
        userRepository.save(user);

        sendVerificationEmail(email, token);

        log.info("Verification email resent to: {}", email);
        return MessageResponse.builder().message("Verification email sent").build();
    }

    public MessageResponse forgotPassword(String email) {
        String message = "If an account exists with this email, a password reset link has been sent.";

        Optional<User> optionalUser = userRepository.findByEmail(email);
        if (optionalUser.isEmpty()) {
            log.debug("Password reset requested for non-existent email: {}", email);
            return MessageResponse.builder().message(message).build();
        }

        User user = optionalUser.get();
        String token = UUID.randomUUID().toString();
        user.setPasswordResetToken(token);
        user.setPasswordResetTokenExpiry(Instant.now().plus(1, ChronoUnit.HOURS));
        userRepository.save(user);

        sendPasswordResetEmail(email, token);

        log.info("Password reset email sent to: {}", email);
        return MessageResponse.builder().message(message).build();
    }

    public MessageResponse resetPassword(String token, String newPassword) {
        User user = userRepository.findByPasswordResetToken(token)
                .orElseThrow(() -> new UnauthorizedException("Invalid password reset token"));

        if (user.getPasswordResetTokenExpiry().isBefore(Instant.now())) {
            throw new UnauthorizedException("Password reset token has expired");
        }

        passwordValidationService.validate(newPassword, user.getEmail());

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordResetToken(null);
        user.setPasswordResetTokenExpiry(null);
        user.setVerificationToken(null);
        user.setVerificationTokenExpiry(null);
        userRepository.save(user);

        log.info("Password reset for user: {}", user.getEmail());
        return MessageResponse.builder().message("Password has been reset successfully").build();
    }

    private void sendVerificationEmail(String email, String token) {
        String verifyUrl = baseUrl + "/verify-email?token=" + token;
        String body = "<h2>Welcome to Gifiti!</h2>"
                + "<p>Thanks for signing up. Please confirm your email address by clicking the link below:</p>"
                + "<p><a href=\"" + verifyUrl + "\">Confirm Email Address</a></p>"
                + "<p>This link expires in 24 hours.</p>"
                + "<p>If you didn't create a Gifiti account, you can safely ignore this email.</p>";
        emailService.send(email, "Welcome to Gifiti - Please confirm your email", body);
    }

    /**
     * Logout by blacklisting both the access and refresh tokens.
     */
    public MessageResponse logout(String accessToken, String refreshToken) {
        blacklistToken(accessToken);
        if (refreshToken != null) {
            blacklistToken(refreshToken);
        }
        log.info("User logged out, tokens blacklisted");
        return MessageResponse.builder().message("Logged out successfully").build();
    }

    /**
     * Check if a token has been blacklisted (logged out).
     */
    public boolean isTokenBlacklisted(String token) {
        return blacklistedTokenRepository.existsByTokenHash(hashToken(token));
    }

    private void blacklistToken(String token) {
        try {
            Instant expiry = jwtTokenProvider.getExpirationFromToken(token);
            blacklistedTokenRepository.save(BlacklistedToken.builder()
                    .tokenHash(hashToken(token))
                    .expiresAt(expiry)
                    .build());
        } catch (Exception e) {
            log.warn("Could not blacklist token: {}", e.getMessage());
        }
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private void sendPasswordResetEmail(String email, String token) {
        String resetUrl = baseUrl + "/reset-password?token=" + token;
        String body = "<h2>Reset your Gifiti password</h2>"
                + "<p>Click the link below to reset your password:</p>"
                + "<p><a href=\"" + resetUrl + "\">Reset Password</a></p>"
                + "<p>This link expires in 1 hour. If you didn't request this, ignore this email.</p>";
        emailService.send(email, "Reset your Gifiti password", body);
    }
}
