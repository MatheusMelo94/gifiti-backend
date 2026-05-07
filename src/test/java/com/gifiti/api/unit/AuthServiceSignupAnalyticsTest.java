package com.gifiti.api.unit;

import com.gifiti.api.analytics.PostHogClient;
import com.gifiti.api.dto.request.RegisterRequest;
import com.gifiti.api.exception.ConflictException;
import com.gifiti.api.model.User;
import com.gifiti.api.model.enums.SignupTrigger;
import com.gifiti.api.repository.BlacklistedTokenRepository;
import com.gifiti.api.repository.UserRepository;
import com.gifiti.api.security.JwtTokenProvider;
import com.gifiti.api.service.AccountLockoutService;
import com.gifiti.api.service.AuthService;
import com.gifiti.api.service.EmailService;
import com.gifiti.api.service.EmailTemplateRenderer;
import com.gifiti.api.service.EmailTemplateRenderer.RenderedEmail;
import com.gifiti.api.service.GoogleTokenVerifierService;
import com.gifiti.api.service.PasswordValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for the {@code signup_completed} PostHog emission added to
 * {@link AuthService#register} (T5 of feature 007).
 *
 * <p>Per Architect plan v2 §3 Decision H + §5.4, the signup_trigger property
 * is the most strategic in the 7-event taxonomy. Backend emission via the
 * server-generated userId guarantees capture even when the frontend's
 * post-signup analytics call fails. {@link PostHogClient} failures must
 * never propagate to the user-facing call (security-findings.md F-6).</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceSignupAnalyticsTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private AccountLockoutService accountLockoutService;
    @Mock private PasswordValidationService passwordValidationService;
    @Mock private EmailService emailService;
    @Mock private EmailTemplateRenderer emailTemplateRenderer;
    @Mock private BlacklistedTokenRepository blacklistedTokenRepository;
    @Mock private GoogleTokenVerifierService googleTokenVerifierService;
    @Mock private PostHogClient postHogClient;

    @InjectMocks private AuthService authService;

    @BeforeEach
    void setBaseUrl() {
        ReflectionTestUtils.setField(authService, "baseUrl", "http://localhost:3000");
        when(emailTemplateRenderer.verification(any(), anyString()))
                .thenReturn(new RenderedEmail("subj-stub", "body-stub"));
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId("new-user-id-123");
            return u;
        });
    }

    @Test
    @DisplayName("register() emits signup_completed with the supplied trigger and referrer")
    void emits_with_explicit_trigger_and_referrer() {
        RegisterRequest request = RegisterRequest.builder()
                .email("user@example.test")
                .password("SecureP@ss123!")
                .signupTrigger(SignupTrigger.CREATED_WISHLIST)
                .referrerWishlistId("123e4567-e89b-12d3-a456-426614174000")
                .build();

        authService.register(request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> propsCaptor =
                ArgumentCaptor.forClass(Map.class);
        verify(postHogClient).capture(
                eq("signup_completed"),
                eq("new-user-id-123"),
                propsCaptor.capture());

        Map<String, Object> props = propsCaptor.getValue();
        assertThat(props).containsEntry("signup_trigger", "CREATED_WISHLIST");
        assertThat(props).containsEntry(
                "referrer_wishlist_id",
                "123e4567-e89b-12d3-a456-426614174000");
    }

    @Test
    @DisplayName("register() defaults signup_trigger to DIRECT when the field is omitted, and omits referrer when null")
    void defaults_to_direct_when_omitted() {
        RegisterRequest request = RegisterRequest.builder()
                .email("user2@example.test")
                .password("SecureP@ss123!")
                .build();

        authService.register(request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> propsCaptor =
                ArgumentCaptor.forClass(Map.class);
        verify(postHogClient).capture(
                eq("signup_completed"),
                eq("new-user-id-123"),
                propsCaptor.capture());

        Map<String, Object> props = propsCaptor.getValue();
        assertThat(props).containsEntry("signup_trigger", "DIRECT");
        assertThat(props).doesNotContainKey("referrer_wishlist_id");
    }

    @Test
    @DisplayName("register() succeeds (and the exception is swallowed) when PostHogClient throws")
    void posthog_failure_does_not_propagate_to_signup() {
        // F-6: the wrapper catches its own SDK failures, but defense-in-depth
        // — even if a future contract change causes capture() to throw, the
        // signup must succeed.
        doThrow(new RuntimeException("posthog down"))
                .when(postHogClient).capture(anyString(), anyString(), any());

        RegisterRequest request = RegisterRequest.builder()
                .email("user3@example.test")
                .password("SecureP@ss123!")
                .build();

        assertThatCode(() -> authService.register(request))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("register() does NOT emit signup_completed if registration fails (e.g. duplicate email)")
    void no_emit_on_failed_registration() {
        when(userRepository.existsByEmail("dup@example.test")).thenReturn(true);

        RegisterRequest request = RegisterRequest.builder()
                .email("dup@example.test")
                .password("SecureP@ss123!")
                .build();

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ConflictException.class);

        verify(postHogClient, never()).capture(anyString(), anyString(), any());
    }
}
