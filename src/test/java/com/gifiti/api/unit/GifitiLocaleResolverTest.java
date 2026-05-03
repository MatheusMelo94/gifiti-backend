package com.gifiti.api.unit;

import com.gifiti.api.config.GifitiLocaleResolver;
import com.gifiti.api.model.User;
import com.gifiti.api.model.enums.Language;
import com.gifiti.api.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GifitiLocaleResolver} — the request-locale resolver implementing
 * the precedence chain from spec criteria #1-4 of {@code 005-i18n-backend-support}:
 *
 * <ol>
 *   <li>Accept-Language header — first supported tag wins (q-values respected).</li>
 *   <li>Authenticated principal's stored {@link User#getPreferredLanguage()}.</li>
 *   <li>{@code en-US} default fallback.</li>
 * </ol>
 *
 * <p>No Spring context — collaborators are constructed directly and {@link SecurityContextHolder}
 * is manipulated in-process. Each test resets the security context in {@link AfterEach} so the
 * suite is order-independent.</p>
 */
class GifitiLocaleResolverTest {

    private static final Locale EN_US = Locale.forLanguageTag("en-US");
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private UserRepository userRepository;
    private GifitiLocaleResolver resolver;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        resolver = new GifitiLocaleResolver(userRepository);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("authenticated user with no Accept-Language header uses stored preferredLanguage (criterion #1)")
    void auth_user_no_header_uses_stored_preference() {
        String email = "alice@example.com";
        authenticateAs(email);
        when(userRepository.findByEmail(email))
                .thenReturn(Optional.of(userWithLanguage(Language.PT_BR)));

        Locale resolved = resolver.resolveLocale(new MockHttpServletRequest());

        assertThat(resolved).isEqualTo(PT_BR);
    }

    @Test
    @DisplayName("Accept-Language header overrides authenticated user's stored preferredLanguage (criterion #2)")
    void auth_user_with_header_overrides_stored_preference() {
        String email = "alice@example.com";
        authenticateAs(email);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "pt-BR");

        Locale resolved = resolver.resolveLocale(request);

        assertThat(resolved).isEqualTo(PT_BR);
        // Header is sufficient on its own — no DB lookup needed when header already resolves.
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("unauthenticated request with supported Accept-Language header uses header (criterion #3)")
    void unauth_with_header_uses_header() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "pt-BR");

        Locale resolved = resolver.resolveLocale(request);

        assertThat(resolved).isEqualTo(PT_BR);
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("unauthenticated request with no Accept-Language header falls back to en-US (criterion #4)")
    void unauth_no_header_falls_back_to_en_US() {
        Locale resolved = resolver.resolveLocale(new MockHttpServletRequest());

        assertThat(resolved).isEqualTo(EN_US);
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("unauthenticated request with unsupported Accept-Language falls back to en-US (criterion #4)")
    void unauth_unsupported_header_falls_back_to_en_US() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "fr-FR");

        Locale resolved = resolver.resolveLocale(request);

        assertThat(resolved).isEqualTo(EN_US);
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("authenticated user with unsupported Accept-Language falls through to stored preference")
    void auth_unsupported_header_falls_back_to_stored_preference() {
        String email = "alice@example.com";
        authenticateAs(email);
        when(userRepository.findByEmail(email))
                .thenReturn(Optional.of(userWithLanguage(Language.PT_BR)));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "fr-FR");

        Locale resolved = resolver.resolveLocale(request);

        assertThat(resolved).isEqualTo(PT_BR);
    }

    @Test
    @DisplayName("multiple unsupported languages in q-list fall back to en-US default")
    void unauth_multiple_unsupported_headers_fall_back_to_en_US() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "zh-CN, ja-JP;q=0.5");

        Locale resolved = resolver.resolveLocale(request);

        assertThat(resolved).isEqualTo(EN_US);
    }

    @Test
    @DisplayName("Accept-Language with q-values picks first supported tag (pt-BR, en;q=0.5 -> pt-BR)")
    void unauth_header_with_q_values_picks_first_supported() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "pt-BR, en;q=0.5");

        Locale resolved = resolver.resolveLocale(request);

        assertThat(resolved).isEqualTo(PT_BR);
    }

    @Test
    @DisplayName("authenticated user with null preferredLanguage falls back to en-US (legacy doc)")
    void auth_user_with_no_preferredLanguage_falls_back_to_en_US() {
        String email = "legacy@example.com";
        authenticateAs(email);
        when(userRepository.findByEmail(email))
                .thenReturn(Optional.of(userWithLanguage(null)));

        Locale resolved = resolver.resolveLocale(new MockHttpServletRequest());

        // User.effectiveLanguage() returns EN_US when preferredLanguage is null.
        assertThat(resolved).isEqualTo(EN_US);
    }

    @Test
    @DisplayName("authenticated email not found in repository falls back to en-US (defensive)")
    void auth_user_lookup_failure_falls_back_to_en_US() {
        String email = "ghost@example.com";
        authenticateAs(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        Locale resolved = resolver.resolveLocale(new MockHttpServletRequest());

        assertThat(resolved).isEqualTo(EN_US);
    }

    @Test
    @DisplayName("anonymous authentication tokens are treated as unauthenticated")
    void anonymous_token_is_treated_as_unauthenticated() {
        AnonymousAuthenticationToken anonymous = new AnonymousAuthenticationToken(
                "anon-key",
                "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))
        );
        SecurityContextHolder.getContext().setAuthentication(anonymous);

        Locale resolved = resolver.resolveLocale(new MockHttpServletRequest());

        assertThat(resolved).isEqualTo(EN_US);
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("setLocale always throws UnsupportedOperationException — locale changes go via profile update")
    void setLocale_throws_unsupported_operation() {
        HttpServletRequest request = new MockHttpServletRequest();
        HttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> resolver.setLocale(request, response, PT_BR))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * Build a JWT-style {@link UsernamePasswordAuthenticationToken} mirroring the principal
     * shape produced by {@link com.gifiti.api.security.JwtAuthenticationFilter} —
     * Spring's stock {@code UserDetails} carrying the user's email as the username.
     */
    private static void authenticateAs(String email) {
        UserDetails principal = org.springframework.security.core.userdetails.User.builder()
                .username(email)
                .password("{NOPE}")
                .authorities(new SimpleGrantedAuthority("ROLE_USER"))
                .build();
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static User userWithLanguage(Language language) {
        User user = new User();
        user.setPreferredLanguage(language);
        return user;
    }
}
