package com.gifiti.api.unit;

import com.gifiti.api.dto.response.AuthResponse;
import com.gifiti.api.exception.ConflictException;
import com.gifiti.api.exception.UnauthorizedException;
import com.gifiti.api.model.User;
import com.gifiti.api.model.enums.AuthProvider;
import com.gifiti.api.model.enums.Role;
import com.gifiti.api.repository.BlacklistedTokenRepository;
import com.gifiti.api.repository.UserRepository;
import com.gifiti.api.security.JwtTokenProvider;
import com.gifiti.api.service.AccountLockoutService;
import com.gifiti.api.service.AuthService;
import com.gifiti.api.service.EmailService;
import com.gifiti.api.service.EmailTemplateRenderer;
import com.gifiti.api.service.GoogleTokenVerifierService;
import com.gifiti.api.service.GoogleTokenVerifierService.GoogleUserInfo;
import com.gifiti.api.service.PasswordValidationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleAuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private BlacklistedTokenRepository blacklistedTokenRepository;
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
    private GoogleTokenVerifierService googleTokenVerifierService;

    @InjectMocks
    private AuthService authService;

    private static final String GOOGLE_ID = "google-123";
    private static final String EMAIL = "user@example.com";
    private static final String NAME = "Test User";
    private static final String ID_TOKEN = "valid-id-token";

    private static final String PICTURE_URL = "https://lh3.googleusercontent.com/photo.jpg";

    private GoogleUserInfo validGoogleUser() {
        return new GoogleUserInfo(GOOGLE_ID, EMAIL, NAME, PICTURE_URL, true);
    }

    private void stubJwtTokens() {
        when(jwtTokenProvider.generateAccessToken(anyString())).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(anyString())).thenReturn("refresh-token");
        when(jwtTokenProvider.getAccessTokenExpirationInSeconds()).thenReturn(3600L);
    }

    @Nested
    @DisplayName("loginWithGoogle() — invalid token")
    class InvalidTokenTests {

        @Test
        @DisplayName("should reject null verification result (invalid token)")
        void shouldRejectInvalidToken() {
            when(googleTokenVerifierService.verify(ID_TOKEN)).thenReturn(null);

            assertThatThrownBy(() -> authService.loginWithGoogle(ID_TOKEN))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("Invalid Google credentials");
        }

        @Test
        @DisplayName("should reject unverified Google email")
        void shouldRejectUnverifiedGoogleEmail() {
            GoogleUserInfo unverified = new GoogleUserInfo(GOOGLE_ID, EMAIL, NAME, PICTURE_URL, false);
            when(googleTokenVerifierService.verify(ID_TOKEN)).thenReturn(unverified);

            assertThatThrownBy(() -> authService.loginWithGoogle(ID_TOKEN))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessageContaining("email not verified");
        }
    }

    @Nested
    @DisplayName("loginWithGoogle() — new user")
    class NewUserTests {

        @Test
        @DisplayName("should create new user on first Google login")
        void shouldCreateNewUser() {
            when(googleTokenVerifierService.verify(ID_TOKEN)).thenReturn(validGoogleUser());
            when(userRepository.findByGoogleId(GOOGLE_ID)).thenReturn(Optional.empty());
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId("new-user-id");
                return u;
            });


            stubJwtTokens();

            AuthResponse response = authService.loginWithGoogle(ID_TOKEN);

            assertThat(response.getAccessToken()).isEqualTo("access-token");
            assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
            assertThat(response.getUser().getEmail()).isEqualTo(EMAIL);
            assertThat(response.getUser().getDisplayName()).isEqualTo(NAME);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            User saved = captor.getValue();
            assertThat(saved.getGoogleId()).isEqualTo(GOOGLE_ID);
            assertThat(saved.getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
            assertThat(saved.isEmailVerified()).isTrue();
            assertThat(saved.getPassword()).isNull();
        }

        @Test
        @DisplayName("should derive displayName from email when Google name is null")
        void shouldDeriveDisplayNameFromEmail() {
            GoogleUserInfo noName = new GoogleUserInfo(GOOGLE_ID, EMAIL, null, PICTURE_URL, true);
            when(googleTokenVerifierService.verify(ID_TOKEN)).thenReturn(noName);
            when(userRepository.findByGoogleId(GOOGLE_ID)).thenReturn(Optional.empty());
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId("new-user-id");
                return u;
            });
            stubJwtTokens();

            authService.loginWithGoogle(ID_TOKEN);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getDisplayName()).isEqualTo("user");
        }
    }

    @Nested
    @DisplayName("loginWithGoogle() — returning user")
    class ReturningUserTests {

        @Test
        @DisplayName("should log in returning user by Google ID")
        void shouldLoginReturningUser() {
            User existingUser = User.builder()
                    .id("existing-id")
                    .email(EMAIL)
                    .googleId(GOOGLE_ID)
                    .displayName(NAME)
                    .profilePictureUrl(PICTURE_URL)
                    .authProvider(AuthProvider.GOOGLE)
                    .roles(Set.of(Role.USER))
                    .build();

            when(googleTokenVerifierService.verify(ID_TOKEN)).thenReturn(validGoogleUser());
            when(userRepository.findByGoogleId(GOOGLE_ID)).thenReturn(Optional.of(existingUser));
            stubJwtTokens();

            AuthResponse response = authService.loginWithGoogle(ID_TOKEN);

            assertThat(response.getUser().getId()).isEqualTo("existing-id");
            assertThat(response.getUser().getEmail()).isEqualTo(EMAIL);
            assertThat(response.getUser().getProfilePictureUrl()).isEqualTo(PICTURE_URL);
            // Should NOT save since nothing changed
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("should update email when Google email changes")
        void shouldUpdateEmailOnGoogleEmailChange() {
            User existingUser = User.builder()
                    .id("existing-id")
                    .email("old@example.com")
                    .googleId(GOOGLE_ID)
                    .displayName(NAME)
                    .profilePictureUrl(PICTURE_URL)
                    .authProvider(AuthProvider.GOOGLE)
                    .roles(Set.of(Role.USER))
                    .build();

            when(googleTokenVerifierService.verify(ID_TOKEN)).thenReturn(validGoogleUser());
            when(userRepository.findByGoogleId(GOOGLE_ID)).thenReturn(Optional.of(existingUser));
            when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
            when(userRepository.save(any(User.class))).thenReturn(existingUser);
            stubJwtTokens();

            AuthResponse response = authService.loginWithGoogle(ID_TOKEN);

            assertThat(response.getUser().getEmail()).isEqualTo(EMAIL);
            verify(userRepository).save(existingUser);
        }

        @Test
        @DisplayName("should NOT update email if new email is already taken")
        void shouldNotUpdateEmailIfTaken() {
            User existingUser = User.builder()
                    .id("existing-id")
                    .email("old@example.com")
                    .googleId(GOOGLE_ID)
                    .displayName(NAME)
                    .profilePictureUrl(PICTURE_URL)
                    .authProvider(AuthProvider.GOOGLE)
                    .roles(Set.of(Role.USER))
                    .build();

            when(googleTokenVerifierService.verify(ID_TOKEN)).thenReturn(validGoogleUser());
            when(userRepository.findByGoogleId(GOOGLE_ID)).thenReturn(Optional.of(existingUser));
            when(userRepository.existsByEmail(EMAIL)).thenReturn(true);
            stubJwtTokens();

            AuthResponse response = authService.loginWithGoogle(ID_TOKEN);

            // Email should remain old since new one is taken
            assertThat(existingUser.getEmail()).isEqualTo("old@example.com");
            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("loginWithGoogle() — account linking")
    class AccountLinkingTests {

        @Test
        @DisplayName("should link Google to verified local account")
        void shouldLinkToVerifiedLocalAccount() {
            User localUser = User.builder()
                    .id("local-id")
                    .email(EMAIL)
                    .password("encoded-password")
                    .displayName(NAME)
                    .authProvider(AuthProvider.LOCAL)
                    .emailVerified(true)
                    .roles(Set.of(Role.USER))
                    .build();

            when(googleTokenVerifierService.verify(ID_TOKEN)).thenReturn(validGoogleUser());
            when(userRepository.findByGoogleId(GOOGLE_ID)).thenReturn(Optional.empty());
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(localUser));
            when(userRepository.save(any(User.class))).thenReturn(localUser);
            stubJwtTokens();

            AuthResponse response = authService.loginWithGoogle(ID_TOKEN);

            assertThat(response.getUser().getId()).isEqualTo("local-id");

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            User saved = captor.getValue();
            assertThat(saved.getGoogleId()).isEqualTo(GOOGLE_ID);
            assertThat(saved.getAuthProvider()).isEqualTo(AuthProvider.BOTH);
            assertThat(saved.getPassword()).isEqualTo("encoded-password"); // password preserved
        }

        @Test
        @DisplayName("should take over unverified local account")
        void shouldTakeOverUnverifiedLocalAccount() {
            User unverifiedUser = User.builder()
                    .id("unverified-id")
                    .email(EMAIL)
                    .password("encoded-password")
                    .displayName(NAME)
                    .authProvider(AuthProvider.LOCAL)
                    .emailVerified(false)
                    .roles(Set.of(Role.USER))
                    .build();

            when(googleTokenVerifierService.verify(ID_TOKEN)).thenReturn(validGoogleUser());
            when(userRepository.findByGoogleId(GOOGLE_ID)).thenReturn(Optional.empty());
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(unverifiedUser));
            when(userRepository.save(any(User.class))).thenReturn(unverifiedUser);
            stubJwtTokens();

            AuthResponse response = authService.loginWithGoogle(ID_TOKEN);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            User saved = captor.getValue();
            assertThat(saved.getGoogleId()).isEqualTo(GOOGLE_ID);
            assertThat(saved.getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
            assertThat(saved.isEmailVerified()).isTrue();
            assertThat(saved.getPassword()).isNull(); // password wiped
        }
    }

    @Nested
    @DisplayName("loginWithGoogle() — race condition")
    class RaceConditionTests {

        @Test
        @DisplayName("should throw ConflictException on DuplicateKeyException")
        void shouldHandleDuplicateKeyException() {
            when(googleTokenVerifierService.verify(ID_TOKEN)).thenReturn(validGoogleUser());
            when(userRepository.findByGoogleId(GOOGLE_ID)).thenReturn(Optional.empty());
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenThrow(new DuplicateKeyException("duplicate"));

            assertThatThrownBy(() -> authService.loginWithGoogle(ID_TOKEN))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("Email already registered");
        }
    }
}
