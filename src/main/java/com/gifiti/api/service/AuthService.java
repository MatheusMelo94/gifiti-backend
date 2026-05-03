package com.gifiti.api.service;

import com.gifiti.api.dto.i18n.LocalizedMessage;
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
import com.gifiti.api.model.enums.AuthProvider;
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
    private final GoogleTokenVerifierService googleTokenVerifierService;

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
        String email = normalizeEmail(request.getEmail());
        log.info("Registering new user with email: {}", email);

        // M-05 security fix: Enhanced password validation
        passwordValidationService.validate(request.getPassword(), email);

        if (userRepository.existsByEmail(email)) {
            log.warn("Registration failed: email already exists: {}", email);
            throw new ConflictException("Email already registered");
        }

        // Derive displayName if not provided
        String displayName = request.getDisplayName();
        if (displayName == null || displayName.isBlank()) {
            displayName = email.split("@")[0];
        }

        String verificationToken = UUID.randomUUID().toString();

        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .displayName(displayName)
                .roles(Set.of(Role.USER))
                .verificationToken(hashToken(verificationToken))
                .verificationTokenExpiry(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();

        User savedUser = userRepository.save(user);
        log.info("User registered successfully: {}", savedUser.getId());

        sendVerificationEmail(savedUser.getEmail(), verificationToken);

        return RegisterResponse.builder()
                .id(savedUser.getId())
                .email(savedUser.getEmail())
                .displayName(savedUser.getDisplayName())
                .message(LocalizedMessage.of("auth.register.success"))
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
        String email = normalizeEmail(request.getEmail());
        log.info("Login attempt for email: {}", email);

        // Check if account is locked (H-02 security fix)
        if (accountLockoutService.isLocked(email)) {
            log.warn("SECURITY_EVENT: Login attempt on locked account: {}", email);
            throw new UnauthorizedException("Account temporarily locked due to multiple failed attempts. Try again later.");
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            email,
                            request.getPassword()
                    )
            );

            // Clear failed attempts on successful login
            accountLockoutService.recordSuccessfulLogin(email);

            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

            String accessToken = jwtTokenProvider.generateAccessToken(authentication);
            String refreshToken = jwtTokenProvider.generateRefreshToken(authentication);

            log.info("Login successful for email: {}", email);

            return AuthResponse.builder()
                    .user(AuthResponse.UserInfo.builder()
                            .id(user.getId())
                            .email(user.getEmail())
                            .displayName(user.getDisplayName())
                            .profilePictureUrl(user.getProfilePictureUrl())
                            .roles(user.getRoles())
                            .build())
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .expiresIn(jwtTokenProvider.getAccessTokenExpirationInSeconds())
                    .build();

        } catch (BadCredentialsException e) {
            // Record failed attempt for lockout tracking (H-02 security fix)
            accountLockoutService.recordFailedAttempt(email);
            log.warn("SECURITY_EVENT: Login failed for email: {} - invalid credentials", email);
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

        if (!jwtTokenProvider.validateToken(refreshToken)) {
            log.warn("SECURITY_EVENT: Invalid refresh token presented");
            throw new UnauthorizedException("Invalid or expired refresh token");
        }

        // Check if this refresh token has been blacklisted (replay detection)
        if (isTokenBlacklisted(refreshToken)) {
            log.warn("SECURITY_EVENT: Blacklisted refresh token reuse detected");
            throw new UnauthorizedException("Invalid or expired refresh token");
        }

        String username = jwtTokenProvider.getUsernameFromToken(refreshToken);

        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> {
                    log.warn("SECURITY_EVENT: Refresh token for non-existent user: {}", username);
                    return new UnauthorizedException("Invalid refresh token");
                });

        // Rotate: blacklist old token FIRST to close replay window, then issue new tokens
        blacklistToken(refreshToken);
        String newAccessToken = jwtTokenProvider.generateAccessToken(username);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(username);

        log.info("Token refreshed and rotated for user: {}", username);

        return AuthResponse.builder()
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .displayName(user.getDisplayName())
                        .profilePictureUrl(user.getProfilePictureUrl())
                        .roles(user.getRoles())
                        .build())
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .expiresIn(jwtTokenProvider.getAccessTokenExpirationInSeconds())
                .build();
    }

    public MessageResponse verifyEmail(String token) {
        User user = userRepository.findByVerificationToken(hashToken(token))
                .orElseThrow(() -> new UnauthorizedException("Invalid verification token"));

        if (user.getVerificationTokenExpiry().isBefore(Instant.now())) {
            throw new UnauthorizedException("Verification token has expired");
        }

        user.setEmailVerified(true);
        user.setVerificationToken(null);
        user.setVerificationTokenExpiry(null);
        userRepository.save(user);

        log.info("Email verified for user: {}", user.getEmail());
        return MessageResponse.builder()
                .message(LocalizedMessage.of("auth.email.verified.success"))
                .build();
    }

    public MessageResponse resendVerification(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        if (user.isEmailVerified()) {
            return MessageResponse.builder()
                    .message(LocalizedMessage.of("auth.email.already.verified"))
                    .build();
        }

        String token = UUID.randomUUID().toString();
        user.setVerificationToken(hashToken(token));
        user.setVerificationTokenExpiry(Instant.now().plus(24, ChronoUnit.HOURS));
        userRepository.save(user);

        sendVerificationEmail(email, token);

        log.info("Verification email resent to: {}", email);
        return MessageResponse.builder()
                .message(LocalizedMessage.of("auth.email.verification.sent"))
                .build();
    }

    public MessageResponse forgotPassword(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        // Anti-enumeration: identical response for found and not-found emails.
        // Spec § Component 7 — the response message follows the request locale
        // (LocaleContextHolder), not the user's stored preferredLanguage, because
        // there may be no such user.
        LocalizedMessage antiEnumerationMessage =
                LocalizedMessage.of("auth.password.reset.requested");

        Optional<User> optionalUser = userRepository.findByEmail(email);
        if (optionalUser.isEmpty()) {
            log.debug("Password reset requested for non-existent email: {}", email);
            return MessageResponse.builder().message(antiEnumerationMessage).build();
        }

        User user = optionalUser.get();
        String token = UUID.randomUUID().toString();
        user.setPasswordResetToken(hashToken(token));
        user.setPasswordResetTokenExpiry(Instant.now().plus(1, ChronoUnit.HOURS));
        userRepository.save(user);

        sendPasswordResetEmail(email, token);

        log.info("Password reset email sent to: {}", email);
        return MessageResponse.builder().message(antiEnumerationMessage).build();
    }

    public MessageResponse resetPassword(String token, String newPassword) {
        User user = userRepository.findByPasswordResetToken(hashToken(token))
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
        return MessageResponse.builder()
                .message(LocalizedMessage.of("auth.password.reset.success"))
                .build();
    }

    public AuthResponse loginWithGoogle(String idToken) {
        GoogleTokenVerifierService.GoogleUserInfo googleUser = googleTokenVerifierService.verify(idToken);
        if (googleUser == null) {
            throw new UnauthorizedException("Invalid Google credentials");
        }

        if (!googleUser.emailVerified()) {
            log.warn("SECURITY_EVENT: Google login rejected — email not verified: {}", googleUser.email());
            throw new UnauthorizedException("Google account email not verified");
        }

        String email = normalizeEmail(googleUser.email());
        User user;

        // 1. Find by Google ID (returning user)
        Optional<User> byGoogleId = userRepository.findByGoogleId(googleUser.googleId());
        if (byGoogleId.isPresent()) {
            user = byGoogleId.get();
            boolean changed = false;
            // Handle email change on Google side
            if (!email.equals(user.getEmail()) && !userRepository.existsByEmail(email)) {
                user.setEmail(email);
                changed = true;
            }
            // Refresh profile picture
            if (googleUser.picture() != null && !googleUser.picture().equals(user.getProfilePictureUrl())) {
                user.setProfilePictureUrl(googleUser.picture());
                changed = true;
            }
            if (changed) {
                userRepository.save(user);
                log.info("Updated profile for Google user: {}", user.getId());
            }
        } else {
            // 2. Find by email (link or create)
            Optional<User> byEmail = userRepository.findByEmail(email);
            if (byEmail.isPresent()) {
                user = byEmail.get();
                user.setGoogleId(googleUser.googleId());
                user.setProfilePictureUrl(googleUser.picture());
                if (user.isEmailVerified()) {
                    // Verified local user — link accounts
                    user.setAuthProvider(AuthProvider.BOTH);
                    log.info("Linked Google account to verified user: {}", email);
                } else {
                    // Unverified local user — Google takes over
                    user.setAuthProvider(AuthProvider.GOOGLE);
                    user.setEmailVerified(true);
                    user.setPassword(null);
                    log.info("Google account took over unverified user: {}", email);
                }
                userRepository.save(user);
            } else {
                // 3. New user
                String displayName = googleUser.name();
                if (displayName == null || displayName.isBlank()) {
                    displayName = email.split("@")[0];
                }

                user = User.builder()
                        .email(email)
                        .googleId(googleUser.googleId())
                        .authProvider(AuthProvider.GOOGLE)
                        .displayName(displayName)
                        .profilePictureUrl(googleUser.picture())
                        .emailVerified(true)
                        .roles(Set.of(Role.USER))
                        .build();

                try {
                    user = userRepository.save(user);
                    log.info("Created new Google user: {}", email);
                } catch (org.springframework.dao.DuplicateKeyException e) {
                    throw new ConflictException("Email already registered");
                }
            }
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user.getEmail());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getEmail());

        log.info("Google login successful for: {}", email);

        return AuthResponse.builder()
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .displayName(user.getDisplayName())
                        .profilePictureUrl(user.getProfilePictureUrl())
                        .roles(user.getRoles())
                        .build())
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtTokenProvider.getAccessTokenExpirationInSeconds())
                .build();
    }

    private void sendVerificationEmail(String email, String token) {
        String verifyUrl = baseUrl + "/verify-email?token=" + token;
        emailService.send(email, "Welcome to Gifiti - Please confirm your email",
                EmailTemplates.verification(verifyUrl));
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
        return MessageResponse.builder()
                .message(LocalizedMessage.of("auth.logout.success"))
                .build();
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

    private String normalizeEmail(String email) {
        return email == null ? null : email.toLowerCase().trim();
    }

    private void sendPasswordResetEmail(String email, String token) {
        String resetUrl = baseUrl + "/reset-password?token=" + token;
        emailService.send(email, "Reset your Gifiti password",
                EmailTemplates.passwordReset(resetUrl));
    }
}
