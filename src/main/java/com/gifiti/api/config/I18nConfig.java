package com.gifiti.api.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.LocaleResolver;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Internationalization (i18n) infrastructure for the Gifiti backend.
 *
 * <p>Wires three beans:</p>
 * <ul>
 *   <li>{@link MessageSource} — {@link ResourceBundleMessageSource} backed by
 *       {@code messages.properties} (default / en-US) and {@code messages_pt_BR.properties}.
 *       Fails loud on missing keys ({@code useCodeAsDefaultMessage=false}) so we don't
 *       silently leak {@code {key}} strings to API consumers.</li>
 *   <li>{@link LocalValidatorFactoryBean} — overrides Spring Boot's default
 *       Jakarta Validation provider so validation messages like
 *       {@code @NotBlank(message = "{validation.x.notblank}")} resolve through the
 *       project {@link MessageSource}. This is the load-bearing wiring for Risk #1
 *       in {@code specs/005-i18n-backend-support/plan.md}.</li>
 *   <li>{@link LocaleResolver} — placeholder returning {@link Locale#US} always.
 *       The real precedence-chain resolver
 *       ({@code Accept-Language} → user preference → default) lands in Task 3
 *       ({@code GifitiLocaleResolver}).</li>
 * </ul>
 *
 * <p>Conventions: {@code architecture-conventions.md § Package Layout} (config/),
 * spec constraint #6, ADR-0001.</p>
 */
@Configuration
public class I18nConfig {

    /**
     * Application-wide {@link MessageSource}. Resolves keys against
     * {@code src/main/resources/messages.properties} and locale-specific siblings
     * (e.g. {@code messages_pt_BR.properties}).
     *
     * <p>Settings (per ADR-0001):</p>
     * <ul>
     *   <li>{@code basename = "messages"} — convention name.</li>
     *   <li>UTF-8 — required for Portuguese accents (á, ç, ã, õ, …).</li>
     *   <li>{@code useCodeAsDefaultMessage = false} — missing keys throw
     *       {@link org.springframework.context.NoSuchMessageException} rather than
     *       silently returning the key. Caught and logged by
     *       {@code GlobalExceptionHandler} once Task 5 lands.</li>
     *   <li>{@code fallbackToSystemLocale = false} — never let the JVM's locale leak
     *       into resolution; always fall back to the configured default locale.</li>
     *   <li>{@code defaultLocale = Locale.US} — explicit en-US fallback.</li>
     * </ul>
     */
    @Bean
    MessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding(StandardCharsets.UTF_8.name());
        source.setUseCodeAsDefaultMessage(false);
        source.setFallbackToSystemLocale(false);
        source.setDefaultLocale(Locale.US);
        return source;
    }

    /**
     * Jakarta Validation provider wired to the project {@link MessageSource}.
     *
     * <p>Declaring this bean causes Spring Boot to skip its auto-configured default
     * validator (single {@link LocalValidatorFactoryBean} bean replaces it).
     * Spring MVC then uses this validator for {@code @Valid} request body validation,
     * and the message interpolator routes {@code {key}} templates through our
     * {@link ResourceBundleMessageSource} — including the locale carried on
     * {@code LocaleContextHolder} once {@code GifitiLocaleResolver} (Task 3) is in place.</p>
     */
    @Bean
    LocalValidatorFactoryBean validator(MessageSource messageSource) {
        LocalValidatorFactoryBean factory = new LocalValidatorFactoryBean();
        factory.setValidationMessageSource(messageSource);
        return factory;
    }

    /**
     * Placeholder {@link LocaleResolver} — always returns {@link Locale#US}.
     *
     * <p>Replaced in Task 3 by {@code GifitiLocaleResolver} which implements the
     * spec'd precedence chain: {@code Accept-Language} header → authenticated
     * principal's {@code User.preferredLanguage} → en-US default. Declaring the bean
     * here (rather than waiting for Task 3) keeps Spring's locale-resolution
     * machinery wired today, which lets follow-up tasks layer in behavior without
     * touching the bean wiring.</p>
     *
     * <p>{@code setLocale} throws {@link UnsupportedOperationException} — locale
     * changes happen via the user's profile-update endpoint (Task 9), not via
     * Spring's locale-change interceptor.</p>
     */
    @Bean
    LocaleResolver localeResolver() {
        return new FixedDefaultLocaleResolver();
    }

    /**
     * Package-private placeholder. Removed when {@code GifitiLocaleResolver} lands in Task 3.
     */
    static class FixedDefaultLocaleResolver implements LocaleResolver {

        @Override
        @NonNull
        public Locale resolveLocale(@NonNull HttpServletRequest request) {
            return Locale.US;
        }

        @Override
        public void setLocale(@NonNull HttpServletRequest request,
                              @Nullable HttpServletResponse response,
                              @Nullable Locale locale) {
            throw new UnsupportedOperationException(
                    "Locale changes happen via profile update, not via LocaleResolver.setLocale().");
        }
    }
}
