package com.gifiti.api.integration.i18n;

import com.gifiti.api.dto.request.ForgotPasswordRequest;
import com.gifiti.api.dto.request.RegisterRequest;
import com.gifiti.api.integration.BaseIntegrationTest;
import com.gifiti.api.integration.support.CapturingEmailService;
import com.gifiti.api.integration.support.CapturingEmailService.CapturedEmail;
import com.gifiti.api.model.User;
import com.gifiti.api.model.enums.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.MediaType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Feature 009 / T4 — Spanish (es-419) transactional-email localization.
 *
 * <p>Pins both transactional emails (verification + password reset) for the
 * new locale, mirroring the en-US / pt-BR coverage in
 * {@link EmailLocalizationIntegrationTest}. Confirms:
 *
 * <ol>
 *   <li>Verification email rendered in Spanish when register carries
 *       {@code Accept-Language: es-419}.</li>
 *   <li>Password-reset email rendered in Spanish for a user with stored
 *       {@code preferredLanguage = ES_419} (per feature 005 / criterion #13:
 *       email follows recipient's stored preference, NOT the request locale).</li>
 * </ol>
 *
 * <p>No production change required beyond T1 (enum) + T2 (bundle):
 * {@link com.gifiti.api.service.EmailTemplateRenderer} pulls every copy string
 * from {@code MessageSource} per the locale resolved at render time, so the
 * new bundle is picked up automatically (ADR 0009 § Decision F).
 *
 * <p>Convention citations:
 * <ul>
 *   <li>{@code architecture-conventions.md § Testing} — integration tests own
 *       transactional-email locale contracts.</li>
 *   <li>ADR 0009 § Decision F — email rendering is MessageSource-based.</li>
 *   <li>Feature 005 plan-amendment-f6.md § 3 — preserved indirectly: the
 *       fallback.link {0} placeholder is identical across all locales (the
 *       T2 bundle preserved {0} verbatim per the pt-BR pattern).</li>
 * </ul>
 */
class SpanishEmailIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CapturingEmailService capturingEmailService;

    @BeforeEach
    void clearCapturedEmails() {
        capturingEmailService.clear();
    }

    @Test
    @DisplayName("registration with Accept-Language: es-419 captures Spanish verification email")
    void verification_email_renders_in_spanish() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("es419-verify@example.test")
                .password("BlueP4nther$Xyz2!")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .header("Accept-Language", "es-419")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        List<CapturedEmail> sent = capturingEmailService.sent();
        assertThat(sent)
                .as("registration triggers exactly one verification email")
                .hasSize(1);

        CapturedEmail email = sent.get(0);
        assertThat(email.to()).isEqualTo("es419-verify@example.test");

        // Subject pinned to the Spanish translation from messages_es_419.properties.
        assertThat(email.subject())
                .isEqualTo("Bienvenido a Gifiti - Por favor, confirma tu correo electrónico");

        // Body assertions — bundle-selection proof via lang attribute + key Spanish phrases.
        assertThat(email.body())
                .contains("<html lang=\"es-419\">")
                .contains("Gracias por registrarte")
                .contains("Confirmar dirección de correo")
                .contains("Si no creaste una cuenta en Gifiti");
    }

    @Test
    @DisplayName("forgot-password email is in Spanish for user with stored preferredLanguage=ES_419")
    void password_reset_email_renders_in_spanish() throws Exception {
        // Seed a user with stored preferredLanguage=ES_419. We use the raw
        // mongoTemplate update path (same pattern as TC-23 in
        // EmailLocalizationIntegrationTest) to keep the test isolated from the
        // T1 registration-locale derivation path.
        registerTestUser("stored-es@example.test", "BlueP4nther$Xyz2!");
        mongoTemplate.updateFirst(
                new Query(Criteria.where("email").is("stored-es@example.test")),
                new Update().set("preferredLanguage", Language.ES_419),
                User.class);
        capturingEmailService.clear();

        ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                .email("stored-es@example.test")
                .build();

        // Request locale: en-US. Recipient's stored locale: es-419. Per
        // feature 005 / criterion #13 (Risk #2 contract): emailing services
        // accept Language as method arg and NEVER read LocaleContextHolder,
        // so the email is in es-419 regardless of the request header.
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .header("Accept-Language", "en-US")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        List<CapturedEmail> sent = capturingEmailService.sent();
        assertThat(sent)
                .as("forgot-password triggers exactly one email")
                .hasSize(1);

        CapturedEmail email = sent.get(0);
        assertThat(email.subject())
                .isEqualTo("Restablece tu contraseña de Gifiti");
        assertThat(email.body())
                .contains("<html lang=\"es-419\">")
                .contains("Restablecer contraseña")
                .contains("Si no solicitaste un restablecimiento de contraseña");
    }
}
