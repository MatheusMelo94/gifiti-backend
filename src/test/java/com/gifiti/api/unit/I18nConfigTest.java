package com.gifiti.api.unit;

import com.gifiti.api.config.I18nConfig;
import com.gifiti.api.repository.UserRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.LocaleResolver;

import static org.mockito.Mockito.mock;

import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit-style Spring slice test for {@link I18nConfig}.
 *
 * <p>Loads ONLY {@link I18nConfig} into a tiny Spring context — no MongoDB, no Spring Boot
 * autoconfiguration, no web environment. Fast and deterministic.</p>
 *
 * <p>Verifies: bundle key resolution, locale fallback chain, fail-loud-on-missing-key, and
 * (load-bearing per plan § Risk #1) that Jakarta Validation messages route through the
 * project {@link MessageSource} via {@link LocalValidatorFactoryBean}.</p>
 */
@ExtendWith(SpringExtension.class)
@SpringJUnitConfig(classes = {I18nConfig.class, I18nConfigTest.MockRepoConfig.class})
class I18nConfigTest {

    /**
     * Test-only configuration providing a Mockito {@link UserRepository} so the
     * {@link I18nConfig#localeResolver(UserRepository)} bean can be instantiated
     * inside this lightweight Spring slice without pulling in Spring Data MongoDB.
     */
    @Configuration
    static class MockRepoConfig {
        @Bean
        UserRepository userRepository() {
            return mock(UserRepository.class);
        }
    }

    @Autowired
    private MessageSource messageSource;

    @Autowired
    private LocalValidatorFactoryBean validator;

    @Autowired
    private LocaleResolver localeResolver;

    @Test
    @DisplayName("messageSource resolves a key from the default (en-US) bundle")
    void messageSource_resolves_key_from_default_bundle() {
        String value = messageSource.getMessage("test.greeting", null, Locale.US);
        assertThat(value).isEqualTo("hello");
    }

    @Test
    @DisplayName("messageSource resolves a key from the pt-BR bundle when locale is pt-BR")
    void messageSource_resolves_key_from_pt_BR_bundle_when_locale_is_pt_BR() {
        Locale ptBr = Locale.forLanguageTag("pt-BR");
        String ptValue = messageSource.getMessage("test.greeting", null, ptBr);
        String enValue = messageSource.getMessage("test.greeting", null, Locale.US);

        assertThat(ptValue).isEqualTo("olá");
        assertThat(ptValue)
                .as("pt-BR bundle must actually be loaded — value must differ from en-US")
                .isNotEqualTo(enValue);
    }

    @Test
    @DisplayName("messageSource falls back to default bundle when key is missing in pt-BR")
    void messageSource_falls_back_to_default_when_key_missing_in_pt_BR() {
        Locale ptBr = Locale.forLanguageTag("pt-BR");
        String value = messageSource.getMessage("test.only.in.default", null, ptBr);
        assertThat(value).isEqualTo("fallback-value");
    }

    @Test
    @DisplayName("messageSource throws NoSuchMessageException for keys missing from every bundle")
    void messageSource_throws_when_key_does_not_exist_anywhere() {
        // Per ADR-0001: useCodeAsDefaultMessage=false. Missing keys must fail loud, not
        // silently leak the key string back to the caller.
        assertThatThrownBy(() ->
                messageSource.getMessage("nonexistent.key.never.defined", null, Locale.US))
                .isInstanceOf(NoSuchMessageException.class);
    }

    @Test
    @DisplayName("validator resolves @NotBlank message templates through the project MessageSource")
    void validator_resolves_message_keys_through_messageSource() {
        // Risk #1 from plan.md: if LocalValidatorFactoryBean.setValidationMessageSource(...)
        // is missing or wired incorrectly, Jakarta Validation falls back to its internal
        // ValidationMessages bundle and the literal "{key}" string leaks to the caller.
        // This test pins the wiring.
        Validator beanValidator = validator;

        Set<ConstraintViolation<TestBean>> violations =
                beanValidator.validate(new TestBean(""));

        assertThat(violations).hasSize(1);
        ConstraintViolation<TestBean> violation = violations.iterator().next();
        assertThat(violation.getMessage())
                .as("Validator must resolve {test.validation.notblank} via MessageSource, not leak the literal key")
                .isEqualTo("this field cannot be blank");
    }

    @Test
    @DisplayName("localeResolver bean is registered")
    void localeResolver_bean_is_present() {
        // Behavior of the resolver itself (precedence chain) is exercised in
        // GifitiLocaleResolverTest. This assertion only pins that the bean wiring exists.
        assertThat(localeResolver).isNotNull();
    }

    /** Minimal validation target used to drive the validator wiring assertion. */
    static class TestBean {
        @NotBlank(message = "{test.validation.notblank}")
        private final String value;

        TestBean(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
