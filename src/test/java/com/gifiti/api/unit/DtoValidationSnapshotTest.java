package com.gifiti.api.unit;

import com.gifiti.api.config.I18nConfig;
import com.gifiti.api.dto.request.CreateItemRequest;
import com.gifiti.api.dto.request.CreateWishlistRequest;
import com.gifiti.api.dto.request.ForgotPasswordRequest;
import com.gifiti.api.dto.request.GoogleLoginRequest;
import com.gifiti.api.dto.request.LoginRequest;
import com.gifiti.api.dto.request.RefreshTokenRequest;
import com.gifiti.api.dto.request.RegisterRequest;
import com.gifiti.api.dto.request.ResetPasswordRequest;
import com.gifiti.api.dto.request.SetPasswordRequest;
import com.gifiti.api.dto.request.UpdateItemRequest;
import com.gifiti.api.dto.request.UpdateProfileRequest;
import com.gifiti.api.dto.request.UpdateWishlistRequest;
import com.gifiti.api.dto.request.VerifyEmailRequest;
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

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Snapshot-pin tests for the canonical English Jakarta Validation messages on
 * each of the 13 request DTOs covered by spec criterion #7 of
 * {@code 005-i18n-backend-support}.
 *
 * <p>Each test triggers all the validation annotations on a single DTO with
 * inputs crafted to violate every rule simultaneously, then asserts the
 * resulting {@link ConstraintViolation} messages contain the canonical English
 * text from {@link EnglishValidationSnapshots}. The locale is forced to
 * {@code en-US} so resolution comes out of {@code messages.properties}.</p>
 *
 * <p>This is the load-bearing regression net for spec criterion #20: any
 * accidental rewording of an English literal in {@code messages.properties}
 * (or in an inline annotation if a future engineer reverts the i18n keying)
 * trips a red test the next run.</p>
 *
 * <p>Lives in {@code com.gifiti.api.unit.*} so it runs in the regular suite,
 * not behind the integration-test exclusion. Loads only {@link I18nConfig}
 * plus a stub {@code UserRepository} — no Spring Boot autoconfig, no Mongo.</p>
 */
@ExtendWith(SpringExtension.class)
@SpringJUnitConfig(classes = {I18nConfig.class, DtoValidationSnapshotTest.MockRepoConfig.class})
class DtoValidationSnapshotTest {

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
    void pinLocaleToEnglish() {
        LocaleContextHolder.setLocale(Locale.US);
        validator = validatorFactory;
    }

    @AfterEach
    void clearLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    /**
     * Collects all violation messages from a validate(...) call into a single
     * set so the assertions can be order-independent.
     */
    private static <T> Set<String> messagesOf(Set<ConstraintViolation<T>> violations) {
        return violations.stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("RegisterRequest: every English validation message matches the canonical snapshot")
    void registerRequest_englishSnapshots() {
        RegisterRequest dto = RegisterRequest.builder()
                .email("")
                .password("")
                .displayName("x".repeat(51))
                .build();
        Set<String> messages = messagesOf(validator.validate(dto));

        assertThat(messages)
                .contains(EnglishValidationSnapshots.EMAIL_REQUIRED,
                        EnglishValidationSnapshots.PASSWORD_REQUIRED,
                        EnglishValidationSnapshots.DISPLAYNAME_SIZE);

        // Long-but-malformed email exercises @Email and @Size simultaneously.
        RegisterRequest emailDto = RegisterRequest.builder()
                .email("a".repeat(260))
                .password("Short1!")
                .build();
        Set<String> emailMessages = messagesOf(validator.validate(emailDto));
        assertThat(emailMessages)
                .contains(EnglishValidationSnapshots.EMAIL_INVALID,
                        EnglishValidationSnapshots.REGISTER_EMAIL_SIZE,
                        EnglishValidationSnapshots.PASSWORD_SIZE,
                        EnglishValidationSnapshots.PASSWORD_PATTERN);
    }

    @Test
    @DisplayName("LoginRequest: every English validation message matches the canonical snapshot")
    void loginRequest_englishSnapshots() {
        LoginRequest dto = LoginRequest.builder().email("").password("").build();
        Set<String> messages = messagesOf(validator.validate(dto));

        assertThat(messages)
                .contains(EnglishValidationSnapshots.EMAIL_REQUIRED,
                        EnglishValidationSnapshots.PASSWORD_REQUIRED);

        LoginRequest invalidEmail = LoginRequest.builder().email("not-an-email").password("anything").build();
        assertThat(messagesOf(validator.validate(invalidEmail)))
                .contains(EnglishValidationSnapshots.EMAIL_INVALID);
    }

    @Test
    @DisplayName("CreateWishlistRequest: every English validation message matches the canonical snapshot")
    void createWishlistRequest_englishSnapshots() {
        CreateWishlistRequest dto = CreateWishlistRequest.builder()
                .title("")
                .description("d".repeat(501))
                .build();
        Set<String> messages = messagesOf(validator.validate(dto));
        assertThat(messages)
                .contains(EnglishValidationSnapshots.WISHLIST_TITLE_REQUIRED,
                        EnglishValidationSnapshots.WISHLIST_DESCRIPTION_SIZE);

        CreateWishlistRequest tooLongTitle = CreateWishlistRequest.builder()
                .title("t".repeat(101))
                .build();
        assertThat(messagesOf(validator.validate(tooLongTitle)))
                .contains(EnglishValidationSnapshots.WISHLIST_TITLE_SIZE);
    }

    @Test
    @DisplayName("CreateItemRequest: every English validation message matches the canonical snapshot")
    void createItemRequest_englishSnapshots() {
        CreateItemRequest dto = CreateItemRequest.builder()
                .name("")
                .description("d".repeat(1001))
                .productLink("javascript:alert(1)")
                .imageUrl("javascript:alert(1)")
                .price(new BigDecimal("-1"))
                .quantity(-5)
                .build();
        Set<String> messages = messagesOf(validator.validate(dto));
        assertThat(messages)
                .contains(EnglishValidationSnapshots.ITEM_NAME_REQUIRED,
                        EnglishValidationSnapshots.ITEM_DESCRIPTION_SIZE,
                        EnglishValidationSnapshots.ITEM_PRODUCT_LINK_SAFEURL,
                        EnglishValidationSnapshots.ITEM_IMAGE_URL_SAFEURL,
                        EnglishValidationSnapshots.ITEM_PRICE_POSITIVE,
                        EnglishValidationSnapshots.ITEM_QUANTITY_POSITIVE);

        CreateItemRequest tooLongName = CreateItemRequest.builder()
                .name("n".repeat(201))
                .build();
        assertThat(messagesOf(validator.validate(tooLongName)))
                .contains(EnglishValidationSnapshots.ITEM_NAME_SIZE);
    }

    @Test
    @DisplayName("UpdateProfileRequest: every English validation message matches the canonical snapshot")
    void updateProfileRequest_englishSnapshots() {
        UpdateProfileRequest dto = UpdateProfileRequest.builder()
                .displayName("x".repeat(51))
                .build();
        assertThat(messagesOf(validator.validate(dto)))
                .contains(EnglishValidationSnapshots.DISPLAYNAME_SIZE);
    }

    @Test
    @DisplayName("ResetPasswordRequest: every English validation message matches the canonical snapshot")
    void resetPasswordRequest_englishSnapshots() {
        ResetPasswordRequest dto = ResetPasswordRequest.builder()
                .token("")
                .newPassword("")
                .build();
        Set<String> messages = messagesOf(validator.validate(dto));
        assertThat(messages)
                .contains(EnglishValidationSnapshots.TOKEN_REQUIRED,
                        EnglishValidationSnapshots.PASSWORD_REQUIRED);

        ResetPasswordRequest weakPassword = ResetPasswordRequest.builder()
                .token("anything")
                .newPassword("short")
                .build();
        Set<String> weakMessages = messagesOf(validator.validate(weakPassword));
        assertThat(weakMessages)
                .contains(EnglishValidationSnapshots.PASSWORD_SIZE,
                        EnglishValidationSnapshots.PASSWORD_PATTERN);
    }

    @Test
    @DisplayName("SetPasswordRequest: every English validation message matches the canonical snapshot")
    void setPasswordRequest_englishSnapshots() {
        SetPasswordRequest dto = SetPasswordRequest.builder().newPassword("").build();
        assertThat(messagesOf(validator.validate(dto)))
                .contains(EnglishValidationSnapshots.PASSWORD_REQUIRED);
    }

    @Test
    @DisplayName("ForgotPasswordRequest: every English validation message matches the canonical snapshot")
    void forgotPasswordRequest_englishSnapshots() {
        ForgotPasswordRequest dto = ForgotPasswordRequest.builder().email("").build();
        assertThat(messagesOf(validator.validate(dto)))
                .contains(EnglishValidationSnapshots.EMAIL_REQUIRED);

        ForgotPasswordRequest invalidEmail = ForgotPasswordRequest.builder().email("not-an-email").build();
        assertThat(messagesOf(validator.validate(invalidEmail)))
                .contains(EnglishValidationSnapshots.EMAIL_INVALID);
    }

    @Test
    @DisplayName("GoogleLoginRequest: every English validation message matches the canonical snapshot")
    void googleLoginRequest_englishSnapshots() {
        GoogleLoginRequest dto = GoogleLoginRequest.builder().idToken("").build();
        assertThat(messagesOf(validator.validate(dto)))
                .contains(EnglishValidationSnapshots.GOOGLE_ID_TOKEN_REQUIRED);
    }

    @Test
    @DisplayName("RefreshTokenRequest: every English validation message matches the canonical snapshot")
    void refreshTokenRequest_englishSnapshots() {
        RefreshTokenRequest dto = RefreshTokenRequest.builder().refreshToken("").build();
        assertThat(messagesOf(validator.validate(dto)))
                .contains(EnglishValidationSnapshots.REFRESH_TOKEN_REQUIRED);
    }

    @Test
    @DisplayName("VerifyEmailRequest: every English validation message matches the canonical snapshot")
    void verifyEmailRequest_englishSnapshots() {
        VerifyEmailRequest dto = VerifyEmailRequest.builder().token("").build();
        assertThat(messagesOf(validator.validate(dto)))
                .contains(EnglishValidationSnapshots.TOKEN_REQUIRED);
    }

    @Test
    @DisplayName("UpdateWishlistRequest: every English validation message matches the canonical snapshot")
    void updateWishlistRequest_englishSnapshots() {
        UpdateWishlistRequest dto = UpdateWishlistRequest.builder()
                .title("t".repeat(101))
                .description("d".repeat(501))
                .build();
        assertThat(messagesOf(validator.validate(dto)))
                .contains(EnglishValidationSnapshots.WISHLIST_TITLE_SIZE,
                        EnglishValidationSnapshots.WISHLIST_DESCRIPTION_SIZE);
    }

    @Test
    @DisplayName("UpdateItemRequest: every English validation message matches the canonical snapshot")
    void updateItemRequest_englishSnapshots() {
        UpdateItemRequest dto = UpdateItemRequest.builder()
                .name("n".repeat(201))
                .description("d".repeat(1001))
                .productLink("javascript:alert(1)")
                .imageUrl("javascript:alert(1)")
                .price(new BigDecimal("-1"))
                .quantity(-1)
                .build();
        assertThat(messagesOf(validator.validate(dto)))
                .contains(EnglishValidationSnapshots.ITEM_NAME_SIZE,
                        EnglishValidationSnapshots.ITEM_DESCRIPTION_SIZE,
                        EnglishValidationSnapshots.ITEM_PRODUCT_LINK_SAFEURL,
                        EnglishValidationSnapshots.ITEM_IMAGE_URL_SAFEURL,
                        EnglishValidationSnapshots.ITEM_PRICE_POSITIVE,
                        EnglishValidationSnapshots.ITEM_QUANTITY_POSITIVE);
    }

    @Test
    @DisplayName("Validator never leaks a literal '{validation.*}' key string to the caller (Task 2 wiring backstop)")
    void validator_doesNotLeakLiteralKeyStrings() {
        RegisterRequest dto = RegisterRequest.builder().email("").password("").build();
        Set<String> messages = messagesOf(validator.validate(dto));
        assertThat(messages)
                .as("If LocalValidatorFactoryBean wiring breaks, '{validation.*}' literals leak — pin it")
                .noneMatch(m -> m.startsWith("{validation."));
    }
}
