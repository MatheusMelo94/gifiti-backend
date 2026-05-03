package com.gifiti.api.unit;

import com.gifiti.api.config.I18nConfig;
import com.gifiti.api.model.enums.Language;
import com.gifiti.api.repository.UserRepository;
import com.gifiti.api.service.EmailTemplateRenderer;
import com.gifiti.api.service.EmailTemplateRenderer.RenderedEmail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit-style Spring slice test for {@link EmailTemplateRenderer}.
 *
 * <p>Covers Task 8 of {@code 005-i18n-backend-support} and the F-6 amendment
 * ({@code plan-amendment-f6.md}). The renderer's contract is:</p>
 * <ul>
 *   <li>Locale comes from the {@link Language} method argument — NEVER from
 *       {@link LocaleContextHolder}. This survives {@code @Async} and
 *       {@code @Scheduled} email flows that lose request-thread context
 *       (plan § Risk #2).</li>
 *   <li>Every runtime argument substituted into the rendered HTML body is
 *       HTML-escaped via {@code HtmlUtils.htmlEscape} before substitution. The
 *       contract is "renderer escapes all runtime args," not "renderer escapes
 *       user-controlled args." Defense in depth (F-6 amendment § 2).</li>
 * </ul>
 *
 * <p>Loads only {@link I18nConfig} plus a stub {@link UserRepository} — no
 * Spring Boot autoconfig, no Mongo. Lives in {@code com.gifiti.api.unit.*}
 * so it runs in the regular suite, not behind the integration-test
 * exclusion.</p>
 */
@ExtendWith(SpringExtension.class)
@SpringJUnitConfig(classes = {
        I18nConfig.class,
        EmailTemplateRenderer.class,
        EmailTemplateRendererTest.MockRepoConfig.class
})
class EmailTemplateRendererTest {

    @Configuration
    static class MockRepoConfig {
        @Bean
        UserRepository userRepository() {
            return mock(UserRepository.class);
        }
    }

    @Autowired
    private EmailTemplateRenderer renderer;

    @AfterEach
    void clearLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    @DisplayName("verification renders in English when language is EN_US")
    void verification_renders_in_english_when_language_is_EN_US() {
        RenderedEmail email = renderer.verification(
                Language.EN_US, "https://example.com/verify?token=abc123");

        assertThat(email.subject())
                .isEqualTo("Welcome to Gifiti - Please confirm your email");
        assertThat(email.htmlBody())
                .as("body must contain the verification URL")
                .contains("https://example.com/verify?token=abc123");
        assertThat(email.htmlBody())
                .as("body must carry the en-US copy from the bundle (byte-for-byte "
                        + "equivalent to pre-i18n production text per spec criterion #20)")
                .contains("Thanks for signing up")
                .contains("Confirm Email Address")
                .contains("If you didn't create a Gifiti account");
    }

    @Test
    @DisplayName("verification renders in pt-BR when language is PT_BR")
    void verification_renders_in_pt_BR_when_language_is_PT_BR() {
        RenderedEmail enUs = renderer.verification(
                Language.EN_US, "https://example.com/verify?token=abc123");
        RenderedEmail ptBr = renderer.verification(
                Language.PT_BR, "https://example.com/verify?token=abc123");

        assertThat(ptBr.subject())
                .as("pt-BR subject differs from en-US (pulled from pt-BR bundle)")
                .isNotEqualTo(enUs.subject())
                .contains("Bem-vindo");
        assertThat(ptBr.htmlBody())
                .as("pt-BR body differs from en-US")
                .isNotEqualTo(enUs.htmlBody())
                .contains("Bem-vindo");
    }

    @Test
    @DisplayName("passwordReset renders correctly in both languages")
    void passwordReset_renders_correctly_in_both_languages() {
        RenderedEmail enUs = renderer.passwordReset(
                Language.EN_US, "https://example.com/reset?token=xyz789");
        RenderedEmail ptBr = renderer.passwordReset(
                Language.PT_BR, "https://example.com/reset?token=xyz789");

        assertThat(enUs.subject()).isEqualTo("Reset your Gifiti password");
        assertThat(enUs.htmlBody())
                .contains("https://example.com/reset?token=xyz789")
                .contains("Reset your password")
                .contains("Reset Password")
                .contains("We received a request to reset your Gifiti password");

        assertThat(ptBr.subject())
                .isNotEqualTo(enUs.subject())
                .contains("Redefina");
        assertThat(ptBr.htmlBody())
                .isNotEqualTo(enUs.htmlBody())
                .contains("Redefina");
    }

    /**
     * F-6 amendment § 2: every runtime argument substituted into the rendered
     * HTML body MUST be HTML-escaped via {@code HtmlUtils.htmlEscape} before
     * substitution. This test pins the contract for ALL email types, ALL HTML
     * metacharacters, and ALL runtime args (today: only the URL — but the
     * contract is "all args," not "user-controlled args").
     *
     * <p>HtmlUtils.htmlEscape encoding (verified empirically on
     * {@code spring-web 6.2.12}):</p>
     * <ul>
     *   <li>{@code <} → {@code &lt;}</li>
     *   <li>{@code >} → {@code &gt;}</li>
     *   <li>{@code &} → {@code &amp;}</li>
     *   <li>{@code "} → {@code &quot;}</li>
     *   <li>{@code '} → {@code &#39;}</li>
     *   <li>{@code {} → {@code {} (left alone — not an HTML metacharacter)</li>
     * </ul>
     */
    @Test
    @DisplayName("htmlEscapesAllRuntimeArguments — F-6 contract")
    void htmlEscapesAllRuntimeArguments() {
        String maliciousArg = "<script>alert('xss')</script>&\"{";

        RenderedEmail verification = renderer.verification(Language.EN_US, maliciousArg);
        assertEscapedFor("verification body", verification.htmlBody(), maliciousArg);

        RenderedEmail passwordReset = renderer.passwordReset(Language.EN_US, maliciousArg);
        assertEscapedFor("passwordReset body", passwordReset.htmlBody(), maliciousArg);
    }

    private void assertEscapedFor(String description, String body, String rawArg) {
        assertThat(body)
                .as("%s must NOT contain the raw <script> substring", description)
                .doesNotContain("<script>")
                .doesNotContain("</script>")
                .doesNotContain("alert('xss')");

        assertThat(body)
                .as("%s must contain the entity-escaped form for every metacharacter", description)
                .contains("&lt;script&gt;alert(&#39;xss&#39;)&lt;/script&gt;")
                .contains("&amp;")
                .contains("&quot;");
    }

    /**
     * Risk #2 from {@code plan.md § Risks} (load-bearing): emails MUST NOT
     * depend on thread-local locale state. The {@link Language} method
     * argument wins over any value carried on {@link LocaleContextHolder}.
     * Pinning this protects the future {@code @Async} email path.
     */
    @Test
    @DisplayName("renders independently of LocaleContextHolder")
    void renders_independently_of_LocaleContextHolder() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("pt-BR"));

        RenderedEmail email = renderer.verification(
                Language.EN_US, "https://example.com/verify?token=abc");

        assertThat(email.subject())
                .as("Language argument wins over LocaleContextHolder")
                .isEqualTo("Welcome to Gifiti - Please confirm your email")
                .doesNotContain("Bem-vindo"); // no pt-BR content leaked
        assertThat(email.htmlBody())
                .doesNotContain("Bem-vindo"); // no pt-BR content leaked
    }
}
