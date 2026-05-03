package com.gifiti.api.unit;

import com.gifiti.api.dto.request.RegisterRequest;
import com.gifiti.api.dto.response.AuthResponse;
import com.gifiti.api.dto.response.MessageResponse;
import com.gifiti.api.dto.response.RegisterResponse;
import com.gifiti.api.exception.UnauthorizedException;
import com.gifiti.api.model.User;
import com.gifiti.api.model.BlacklistedToken;
import com.gifiti.api.model.enums.Role;
import com.gifiti.api.repository.BlacklistedTokenRepository;
import com.gifiti.api.repository.UserRepository;
import com.gifiti.api.security.JwtTokenProvider;
import com.gifiti.api.service.AccountLockoutService;
import com.gifiti.api.service.AuthService;
import com.gifiti.api.service.EmailService;
import com.gifiti.api.service.EmailTemplateRenderer;
import com.gifiti.api.service.EmailTemplateRenderer.RenderedEmail;
import com.gifiti.api.service.PasswordValidationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private AccountLockoutService accountLockoutService;
    @Mock
    private PasswordValidationService passwordValidationService;
    @Mock
    private EmailService emailService;
    @Mock
    private EmailTemplateRenderer emailTemplateRenderer;
    @Mock
    private BlacklistedTokenRepository blacklistedTokenRepository;

    @InjectMocks
    private AuthService authService;

    @Nested
    @DisplayName("register()")
    class RegisterTests {

        @Test
        @DisplayName("should register user and send verification email")
        void shouldRegisterAndSendEmail() {
            ReflectionTestUtils.setField(authService, "baseUrl", "http://localhost:3000");

            RegisterRequest request = RegisterRequest.builder()
                    .email("test@example.com")
                    .password("SecureP@ss123!")
                    .build();

            when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
            when(passwordEncoder.encode(any())).thenReturn("encoded");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId("user-id");
                return u;
            });
            // Post-Task 8 (i18n): EmailTemplateRenderer is the single source of
            // truth for the email subject + body. AuthService delegates to it
            // and forwards the rendered fields verbatim to EmailService. The
            // byte-for-byte English text assertion lives in
            // EmailTemplateRendererTest; here we only verify orchestration.
            when(emailTemplateRenderer.verification(any(), anyString()))
                    .thenReturn(new RenderedEmail("subj-stub", "body-stub"));

            RegisterResponse response = authService.register(request);

            assertThat(response.getEmail()).isEqualTo("test@example.com");
            // Post-Task 7 (i18n): RegisterResponse.message is a LocalizedMessage
            // carrying a key. The serializer resolves it at JSON-write time;
            // assert on the key here, not the resolved string.
            assertThat(response.getMessage().messageKey()).isEqualTo("auth.register.success");

            // Verify email was sent with the rendered (stubbed) subject + body
            verify(emailService).send("test@example.com", "subj-stub", "body-stub");

            // Verify user was saved with verification token
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            User savedUser = userCaptor.getValue();
            assertThat(savedUser.getVerificationToken()).isNotNull();
            assertThat(savedUser.getVerificationTokenExpiry()).isAfter(Instant.now());
            assertThat(savedUser.isEmailVerified()).isFalse();
        }
    }

    @Nested
    @DisplayName("verifyEmail()")
    class VerifyEmailTests {

        @Test
        @DisplayName("should verify email with valid token")
        void shouldVerifyWithValidToken() {
            User user = User.builder()
                    .email("test@example.com")
                    .verificationToken("valid-token")
                    .verificationTokenExpiry(Instant.now().plus(1, ChronoUnit.HOURS))
                    .build();

            when(userRepository.findByVerificationToken(anyString())).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenReturn(user);

            MessageResponse response = authService.verifyEmail("valid-token");

            assertThat(response.getMessage().messageKey()).isEqualTo("auth.email.verified.success");
            assertThat(user.isEmailVerified()).isTrue();
            assertThat(user.getVerificationToken()).isNull();
        }

        @Test
        @DisplayName("should reject invalid token")
        void shouldRejectInvalidToken() {
            when(userRepository.findByVerificationToken(anyString())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.verifyEmail("bad-token"))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("should reject expired token")
        void shouldRejectExpiredToken() {
            User user = User.builder()
                    .email("test@example.com")
                    .verificationToken("expired-token")
                    .verificationTokenExpiry(Instant.now().minus(1, ChronoUnit.HOURS))
                    .build();

            when(userRepository.findByVerificationToken(anyString())).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.verifyEmail("expired-token"))
                    .isInstanceOf(UnauthorizedException.class)
                    // Task 10: getMessage() returns the i18n key.
                    .hasMessage("error.auth.verification.token.expired");
        }
    }

    @Nested
    @DisplayName("resendVerification()")
    class ResendVerificationTests {

        @Test
        @DisplayName("should resend verification email")
        void shouldResendVerificationEmail() {
            ReflectionTestUtils.setField(authService, "baseUrl", "http://localhost:3000");

            User user = User.builder()
                    .email("test@example.com")
                    .emailVerified(false)
                    .build();

            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenReturn(user);
            when(emailTemplateRenderer.verification(any(), anyString()))
                    .thenReturn(new RenderedEmail("subj-stub", "body-stub"));

            MessageResponse response = authService.resendVerification("test@example.com");

            assertThat(response.getMessage().messageKey()).isEqualTo("auth.email.verification.sent");
            verify(emailService).send(eq("test@example.com"), any(), any());
        }

        @Test
        @DisplayName("should return already verified message")
        void shouldReturnAlreadyVerified() {
            User user = User.builder()
                    .email("test@example.com")
                    .emailVerified(true)
                    .build();

            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

            MessageResponse response = authService.resendVerification("test@example.com");

            assertThat(response.getMessage().messageKey()).isEqualTo("auth.email.already.verified");
            verify(emailService, never()).send(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("forgotPassword()")
    class ForgotPasswordTests {

        @Test
        @DisplayName("should send reset email for existing user")
        void shouldSendResetEmail() {
            ReflectionTestUtils.setField(authService, "baseUrl", "http://localhost:3000");

            User user = User.builder().email("test@example.com").build();
            when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
            when(userRepository.save(any(User.class))).thenReturn(user);
            when(emailTemplateRenderer.passwordReset(any(), anyString()))
                    .thenReturn(new RenderedEmail("subj-stub", "body-stub"));

            MessageResponse response = authService.forgotPassword("test@example.com");

            assertThat(response.getMessage().messageKey()).isEqualTo("auth.password.reset.requested");
            verify(emailService).send(eq("test@example.com"), any(), any());
        }

        @Test
        @DisplayName("should return same message for non-existent email (anti-enumeration)")
        void shouldReturnSameMessageForNonExistent() {
            when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

            MessageResponse response = authService.forgotPassword("ghost@example.com");

            assertThat(response.getMessage().messageKey()).isEqualTo("auth.password.reset.requested");
            verify(emailService, never()).send(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("resetPassword()")
    class ResetPasswordTests {

        @Test
        @DisplayName("should reset password with valid token")
        void shouldResetPassword() {
            User user = User.builder()
                    .email("test@example.com")
                    .passwordResetToken("reset-token")
                    .passwordResetTokenExpiry(Instant.now().plus(1, ChronoUnit.HOURS))
                    .build();

            when(userRepository.findByPasswordResetToken(anyString())).thenReturn(Optional.of(user));
            when(passwordEncoder.encode(any())).thenReturn("new-encoded");
            when(userRepository.save(any(User.class))).thenReturn(user);

            MessageResponse response = authService.resetPassword("reset-token", "NewSecureP@ss1!");

            assertThat(response.getMessage().messageKey()).isEqualTo("auth.password.reset.success");
            assertThat(user.getPasswordResetToken()).isNull();
            assertThat(user.getVerificationToken()).isNull();
            verify(passwordEncoder).encode("NewSecureP@ss1!");
        }

        @Test
        @DisplayName("should reject invalid token")
        void shouldRejectInvalidToken() {
            when(userRepository.findByPasswordResetToken(anyString())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.resetPassword("bad", "NewSecureP@ss1!"))
                    .isInstanceOf(UnauthorizedException.class);
        }

        @Test
        @DisplayName("should reject expired token")
        void shouldRejectExpiredToken() {
            User user = User.builder()
                    .email("test@example.com")
                    .passwordResetToken("expired-token")
                    .passwordResetTokenExpiry(Instant.now().minus(1, ChronoUnit.HOURS))
                    .build();

            when(userRepository.findByPasswordResetToken(anyString())).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.resetPassword("expired-token", "NewSecureP@ss1!"))
                    .isInstanceOf(UnauthorizedException.class)
                    // Task 10: getMessage() returns the i18n key.
                    .hasMessage("error.auth.password.reset.token.expired");
        }
    }

    @Nested
    @DisplayName("refreshFromToken()")
    class RefreshTokenTests {

        @Test
        @DisplayName("should rotate refresh token on refresh")
        void shouldRotateRefreshToken() {
            String oldRefreshToken = "old-refresh-token";
            User user = User.builder()
                    .id("user-id")
                    .email("user@test.com")
                    .displayName("Test User")
                    .roles(java.util.Set.of(Role.USER))
                    .build();

            when(jwtTokenProvider.validateToken(oldRefreshToken)).thenReturn(true);
            when(jwtTokenProvider.getUsernameFromToken(oldRefreshToken)).thenReturn("user@test.com");
            when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
            when(jwtTokenProvider.generateAccessToken("user@test.com")).thenReturn("new-access-token");
            when(jwtTokenProvider.generateRefreshToken("user@test.com")).thenReturn("new-refresh-token");
            when(jwtTokenProvider.getAccessTokenExpirationInSeconds()).thenReturn(3600L);
            when(jwtTokenProvider.getExpirationFromToken(oldRefreshToken)).thenReturn(Instant.now().plusSeconds(3600));

            AuthResponse response = authService.refreshFromToken(oldRefreshToken);

            assertThat(response.getRefreshToken()).isNotEqualTo(oldRefreshToken);
            assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token");
            verify(blacklistedTokenRepository).save(any(BlacklistedToken.class));
        }
    }
}
