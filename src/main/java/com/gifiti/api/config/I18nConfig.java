package com.gifiti.api.config;

import com.gifiti.api.dto.i18n.LocalizedMessage;
import com.gifiti.api.dto.i18n.LocalizedMessageSerializer;
import com.gifiti.api.repository.UserRepository;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
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
 *   <li>{@link LocaleResolver} — {@link GifitiLocaleResolver} implementing the
 *       precedence chain {@code Accept-Language} header → authenticated user's
 *       {@code preferredLanguage} → {@code en-US} default
 *       (spec criteria #1-4 of {@code 005-i18n-backend-support}).</li>
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
     * Request-locale resolver implementing the precedence chain from spec criteria #1-4:
     * {@code Accept-Language} header → authenticated principal's
     * {@code User.preferredLanguage} → {@code en-US} default.
     *
     * <p>{@code setLocale} on the resolver throws {@link UnsupportedOperationException} —
     * locale changes happen via the user's profile-update endpoint (Task 9), not via
     * Spring's locale-change interceptor.</p>
     *
     * <p>Depends on {@link UserRepository} to resolve the authenticated principal
     * (carried as the user's email on the {@code SecurityContext}) to its stored
     * {@code preferredLanguage}.</p>
     */
    @Bean
    LocaleResolver localeResolver(UserRepository userRepository) {
        return new GifitiLocaleResolver(userRepository);
    }

    /**
     * Registers {@link LocalizedMessageSerializer} on every {@code ObjectMapper}
     * Spring constructs. Response DTOs that hold a {@link LocalizedMessage} field
     * (e.g. {@code RegisterResponse.message}, {@code MessageResponse.message})
     * are then resolved to a plain JSON string at write time, using the locale
     * carried on {@code LocaleContextHolder} (set by {@link GifitiLocaleResolver}).
     *
     * <p>Why a builder customizer rather than {@code @JsonComponent}: the
     * customizer runs against the pre-configured Spring builder, so the
     * serializer is registered on the same {@code ObjectMapper} Spring MVC uses
     * — including overrides applied in test slices. {@code @JsonComponent} relies
     * on classpath scanning that integration-test slices may bypass.</p>
     *
     * <p>Spec: {@code 005-i18n-backend-support} criteria #10, #11.
     * Plan citation: § Component 7 — Success-message refactor.</p>
     */
    @Bean
    Jackson2ObjectMapperBuilderCustomizer localizedMessageSerializerCustomizer(MessageSource messageSource) {
        LocalizedMessageSerializer serializer = new LocalizedMessageSerializer(messageSource);
        return builder -> builder.serializerByType(LocalizedMessage.class, serializer);
    }
}
