package com.gifiti.api.config;

import com.gifiti.api.model.User;
import com.gifiti.api.model.enums.Language;
import com.gifiti.api.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.LocaleResolver;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Custom {@link LocaleResolver} implementing the request-locale precedence chain
 * for spec criteria #1-4 of {@code 005-i18n-backend-support}.
 *
 * <p>Resolution order (first non-empty wins):</p>
 * <ol>
 *   <li><b>Accept-Language header</b> — parsed via {@link Locale.LanguageRange#parse(String)}
 *       and matched against {@link #SUPPORTED_LOCALES} with {@link Locale#lookup}. Quality
 *       values (q-values) are honored by the JDK's matcher.</li>
 *   <li><b>Authenticated user's stored {@link User#getPreferredLanguage()}</b> — looked up
 *       from {@link SecurityContextHolder} and resolved through {@link UserRepository}
 *       by email (the principal username). Anonymous tokens and missing users fall
 *       through to step 3.</li>
 *   <li><b>{@code en-US} default fallback.</b></li>
 * </ol>
 *
 * <p>This bean is registered in {@link I18nConfig#localeResolver(UserRepository)}. Spring
 * MVC consults it during message resolution (after authentication has populated the
 * {@link SecurityContextHolder}), and stores the result on
 * {@code LocaleContextHolder} for the request scope.</p>
 *
 * <p>Locale changes happen via {@code PUT /api/v1/profile} (Task 9), not via Spring's
 * locale-change interceptor — {@link #setLocale} therefore throws
 * {@link UnsupportedOperationException}.</p>
 *
 * <p>Conventions: {@code architecture-conventions.md § Authentication & Authorization},
 * {@code § Logging} (DEBUG-level for fall-through paths), {@code § Layer Rules}
 * (config-layer collaborator depending only on a repository).</p>
 */
@Slf4j
public class GifitiLocaleResolver implements LocaleResolver {

    /** Default locale when neither header nor stored preference yields a supported tag. */
    static final Locale DEFAULT_LOCALE = Locale.forLanguageTag("en-US");

    /**
     * The set of locales the application can serve. Order matters: when the JDK matcher
     * has multiple candidates of equal quality, earlier entries win — but in practice
     * each Accept-Language tag matches at most one of these.
     */
    static final List<Locale> SUPPORTED_LOCALES = Arrays.stream(Language.values())
            .map(Language::toLocale)
            .toList();

    private final UserRepository userRepository;

    public GifitiLocaleResolver(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @NonNull
    public Locale resolveLocale(@NonNull HttpServletRequest request) {
        Locale fromHeader = resolveFromAcceptLanguageHeader(request.getHeader("Accept-Language"));
        if (fromHeader != null) {
            return fromHeader;
        }

        Locale fromPrincipal = resolveFromAuthenticatedPrincipal();
        if (fromPrincipal != null) {
            return fromPrincipal;
        }

        return DEFAULT_LOCALE;
    }

    @Override
    public void setLocale(@NonNull HttpServletRequest request,
                          @Nullable HttpServletResponse response,
                          @Nullable Locale locale) {
        throw new UnsupportedOperationException(
                "Locale changes happen via PUT /api/v1/profile, not via LocaleResolver.setLocale().");
    }

    /**
     * Match the raw {@code Accept-Language} header against the supported locales.
     * Returns {@code null} if the header is missing, blank, malformed, or contains
     * no supported tags — callers fall through to the next step in the chain.
     */
    private Locale resolveFromAcceptLanguageHeader(String headerValue) {
        if (!StringUtils.hasText(headerValue)) {
            return null;
        }
        try {
            List<Locale.LanguageRange> ranges = Locale.LanguageRange.parse(headerValue);
            return Locale.lookup(ranges, SUPPORTED_LOCALES);
        } catch (IllegalArgumentException ex) {
            // Malformed Accept-Language header — never let it break the request.
            log.debug("Malformed Accept-Language header '{}'; falling through to next step", headerValue);
            return null;
        }
    }

    /**
     * Look up the authenticated principal's email in the {@link UserRepository} and
     * return the {@link Locale} matching their {@link User#effectiveLanguage()}.
     * Returns {@code null} when there is no authenticated principal, the principal
     * is anonymous, or the lookup fails — callers fall through to the default.
     */
    private Locale resolveFromAuthenticatedPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }

        Optional<String> email = extractEmail(authentication);
        if (email.isEmpty()) {
            return null;
        }

        try {
            return userRepository.findByEmail(email.get())
                    .map(user -> user.effectiveLanguage().toLocale())
                    .orElse(null);
        } catch (RuntimeException ex) {
            // Defensive: never let a transient repository error break locale resolution.
            log.debug("UserRepository lookup failed during locale resolution for principal '{}': {}",
                    email.get(), ex.getClass().getSimpleName());
            return null;
        }
    }

    private static Optional<String> extractEmail(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            return Optional.ofNullable(userDetails.getUsername()).filter(StringUtils::hasText);
        }
        if (principal instanceof String username && StringUtils.hasText(username)) {
            return Optional.of(username);
        }
        return Optional.ofNullable(authentication.getName()).filter(StringUtils::hasText);
    }
}
