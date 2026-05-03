package com.gifiti.api.unit;

import com.gifiti.api.config.I18nConfig;
import com.gifiti.api.dto.request.CreateWishlistRequest;
import com.gifiti.api.dto.request.RegisterRequest;
import com.gifiti.api.repository.UserRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Spot-check tests proving that key-based Jakarta Validation messages on the
 * 13 request DTOs route through the project {@link org.springframework.context.MessageSource}
 * and resolve against the {@code pt-BR} bundle when {@link LocaleContextHolder}
 * carries that locale.
 *
 * <p>Each test exercises a different annotation type ({@code @Size},
 * {@code @Email}, {@code @NotBlank}, {@code @Pattern}) so the wiring is
 * verified across the breadth of validators used in this codebase. The
 * {@code pt-BR} bundle still contains placeholder strings (real Portuguese
 * translations land in Task 12), so the assertion is "the resolved message
 * starts with the placeholder marker AND differs from the English text" —
 * that proves the locale plumbing chose the right bundle.</p>
 *
 * <p>Lives in {@code com.gifiti.api.unit.*}; loads only {@link I18nConfig}
 * plus a stubbed {@code UserRepository}.</p>
 */
@ExtendWith(SpringExtension.class)
@SpringJUnitConfig(classes = {I18nConfig.class, DtoValidationLocalizationTest.MockRepoConfig.class})
class DtoValidationLocalizationTest {

    @Configuration
    static class MockRepoConfig {
        @Bean
        UserRepository userRepository() {
            return mock(UserRepository.class);
        }
    }

    @Autowired
    private LocalValidatorFactoryBean validatorFactory;

    private Validator validator;

    @BeforeEach
    void setLocaleToPtBr() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("pt-BR"));
        validator = validatorFactory;
    }

    @AfterEach
    void clearLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    private static <T> Set<String> messagesOf(Set<ConstraintViolation<T>> violations) {
        return violations.stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("@Size on RegisterRequest.password resolves the pt-BR placeholder under pt-BR locale")
    void size_password_resolves_pt_BR() {
        RegisterRequest dto = RegisterRequest.builder()
                .email("ok@example.com")
                .password("short")
                .build();
        Set<String> messages = messagesOf(validator.validate(dto));

        assertThat(messages)
                .as("@Size violation for password must resolve via the pt-BR bundle")
                .anyMatch(m -> m.startsWith("[TODO pt-BR]") && m.contains("12-128"));
        assertThat(messages)
                .as("Validator must NOT leak the literal {validation.*} key")
                .noneMatch(m -> m.startsWith("{validation."));
    }

    @Test
    @DisplayName("@Email on RegisterRequest.email resolves the pt-BR placeholder under pt-BR locale")
    void email_invalid_resolves_pt_BR() {
        RegisterRequest dto = RegisterRequest.builder()
                .email("not-an-email")
                .password("ValidPass1@xyz")
                .build();
        Set<String> messages = messagesOf(validator.validate(dto));

        assertThat(messages)
                .as("@Email violation must resolve via the pt-BR bundle")
                .anyMatch(m -> m.startsWith("[TODO pt-BR]") && m.contains("Email"));
    }

    @Test
    @DisplayName("@NotBlank on CreateWishlistRequest.title resolves the pt-BR placeholder under pt-BR locale")
    void notblank_title_resolves_pt_BR() {
        CreateWishlistRequest dto = CreateWishlistRequest.builder().title("").build();
        Set<String> messages = messagesOf(validator.validate(dto));

        assertThat(messages)
                .as("@NotBlank violation on wishlist title must resolve via the pt-BR bundle")
                .anyMatch(m -> m.startsWith("[TODO pt-BR]") && m.contains("Title"));
    }

    @Test
    @DisplayName("@Pattern on RegisterRequest.password resolves the pt-BR placeholder under pt-BR locale")
    void pattern_password_resolves_pt_BR() {
        RegisterRequest dto = RegisterRequest.builder()
                .email("ok@example.com")
                .password("alllowercase12345") // long enough but no upper/digit/special combo
                .build();
        Set<String> messages = messagesOf(validator.validate(dto));

        assertThat(messages)
                .as("@Pattern violation must resolve via the pt-BR bundle")
                .anyMatch(m -> m.startsWith("[TODO pt-BR]") && m.contains("uppercase"));
    }

    @Test
    @DisplayName("Validator under pt-BR locale never leaks a literal '{validation.*}' key string")
    void no_literal_key_leaks_under_pt_BR() {
        RegisterRequest dto = RegisterRequest.builder().email("").password("").build();
        Set<String> messages = messagesOf(validator.validate(dto));
        assertThat(messages)
                .as("If a key is missing from BOTH bundles, the {key} literal would leak")
                .noneMatch(m -> m.startsWith("{validation."));
    }
}
