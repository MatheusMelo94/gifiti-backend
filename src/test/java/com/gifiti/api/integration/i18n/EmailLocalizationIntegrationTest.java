package com.gifiti.api.integration.i18n;

import com.gifiti.api.dto.request.ForgotPasswordRequest;
import com.gifiti.api.dto.request.RegisterRequest;
import com.gifiti.api.integration.BaseIntegrationTest;
import com.gifiti.api.integration.support.CapturingEmailService;
import com.gifiti.api.integration.support.CapturingEmailService.CapturedEmail;
import com.gifiti.api.model.User;
import com.gifiti.api.model.enums.Language;
import com.gifiti.api.service.EmailTemplateRenderer;
import com.gifiti.api.service.EmailTemplateRenderer.RenderedEmail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.MediaType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Group E — Email localization (spec criteria #12, #13, #14) plus the F-6 §3
 * link-target-equivalence guard (MEDIUM advisory).
 *
 * <p>Covers TC-21 through TC-24. The load-bearing tests:
 * <ul>
 *   <li>TC-23 — forgot-password email follows the recipient's stored
 *       {@code preferredLanguage}, NOT the request {@code Accept-Language}.
 *       This is the contract proof for plan Risk #2 ("services that send
 *       emails accept Language as method arg, never read LocaleContextHolder").</li>
 *   <li>F-6 §3 — for every URL-bearing email template key, the resolved URL
 *       set is identical across en-US and pt-BR bundles. Catches translator
 *       drift where pt-BR template accidentally points at a different host
 *       (phishing vector).</li>
 * </ul>
 *
 * <p>Cite: {@code architecture-conventions.md § Engineering Workflow},
 * {@code § Testing}; {@code plan-amendment-f6.md § 3}.
 */
class EmailLocalizationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CapturingEmailService capturingEmailService;

    @Autowired
    private EmailTemplateRenderer emailTemplateRenderer;

    @BeforeEach
    void clearCapturedEmails() {
        capturingEmailService.clear();
    }

    @Test
    @DisplayName("TC-21: registration with Accept-Language: pt-BR captures pt-BR verification email")
    void tc21_register_pt_BR_email() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("ptbr-email@example.test")
                .password("BlueP4nther$Xyz2!")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .header("Accept-Language", "pt-BR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        List<CapturedEmail> sent = capturingEmailService.sent();
        assertThat(sent).as("registration triggers exactly one verification email").hasSize(1);

        CapturedEmail email = sent.get(0);
        assertThat(email.to()).isEqualTo("ptbr-email@example.test");
        // Translation-agnostic: subject/body differ from en-US value.
        assertThat(email.subject())
                .as("pt-BR subject differs from en-US")
                .isNotEqualTo("Welcome to Gifiti - Please confirm your email");
        // Body declared lang attribute proves the bundle selection (en-US body
        // hardcodes <html lang="en-US">, pt-BR hardcodes <html lang="pt-BR">).
        assertThat(email.body()).contains("<html lang=\"pt-BR\">");
    }

    @Test
    @DisplayName("TC-22: registration with no Accept-Language captures en-US verification email byte-for-byte (#20)")
    void tc22_register_no_header_email_en_US() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("en-email@example.test")
                .password("BlueP4nther$Xyz2!")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        List<CapturedEmail> sent = capturingEmailService.sent();
        assertThat(sent).hasSize(1);
        CapturedEmail email = sent.get(0);

        // Spec #20 anchors — byte-for-byte match against pre-i18n English text.
        assertThat(email.subject()).isEqualTo("Welcome to Gifiti - Please confirm your email");
        assertThat(email.body())
                .contains("<html lang=\"en-US\">")
                .contains("Thanks for signing up. Please confirm your email address")
                .contains("Confirm Email Address")
                .contains("If you didn't create a Gifiti account")
                .doesNotContain("[TODO pt-BR]");
    }

    @Test
    @DisplayName("TC-23: forgot-password email follows stored preference, NOT request locale (Risk #2)")
    void tc23_forgot_password_email_follows_stored_pref() throws Exception {
        // Seed a user with stored preferredLanguage=PT_BR.
        registerTestUser("stored-pt@example.test", "BlueP4nther$Xyz2!");
        mongoTemplate.updateFirst(
                new Query(Criteria.where("email").is("stored-pt@example.test")),
                new Update().set("preferredLanguage", Language.PT_BR),
                User.class);
        capturingEmailService.clear();

        ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                .email("stored-pt@example.test")
                .build();

        // Request locale: en-US. Recipient's stored locale: pt-BR. Per
        // criterion #13, the EMAIL must be in pt-BR (recipient's stored
        // preference) while the response message follows the request locale
        // (asserted in SuccessMessageLocalizationIntegrationTest TC-20).
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .header("Accept-Language", "en-US")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        List<CapturedEmail> sent = capturingEmailService.sent();
        assertThat(sent).as("forgot-password triggers exactly one email").hasSize(1);
        CapturedEmail email = sent.get(0);

        // The email is in pt-BR — Risk #2 contract: emailing services accept
        // Language as method arg (user.effectiveLanguage()) and NEVER read
        // LocaleContextHolder. If they did, the en-US Accept-Language header
        // would have leaked into the email subject.
        assertThat(email.subject())
                .as("Email subject must be in pt-BR (recipient's stored preference)")
                .isNotEqualTo("Reset your Gifiti password")
                .isNotBlank();
        assertThat(email.body()).contains("<html lang=\"pt-BR\">");
    }

    @Test
    @DisplayName("TC-24: password-reset email is en-US for legacy user with preferredLanguage=null")
    void tc24_legacy_user_email_en_US() throws Exception {
        // Insert via raw Document to omit preferredLanguage entirely.
        org.bson.Document doc = new org.bson.Document()
                .append("email", "legacy@example.test")
                .append("password",
                        "$2a$12$abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123")
                .append("emailVerified", false)
                .append("authProvider", "LOCAL")
                .append("roles", List.of("USER"));
        mongoTemplate.getCollection("users").insertOne(doc);
        capturingEmailService.clear();

        ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                .email("legacy@example.test")
                .build();

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        List<CapturedEmail> sent = capturingEmailService.sent();
        assertThat(sent).hasSize(1);
        CapturedEmail email = sent.get(0);

        // user.effectiveLanguage() lazy-defaults to EN_US; email must be English.
        assertThat(email.subject()).isEqualTo("Reset your Gifiti password");
        assertThat(email.body()).contains("<html lang=\"en-US\">");

        // Negative: the User document is NOT silently rewritten with
        // preferredLanguage=EN_US (lazy default contract).
        org.bson.Document reread = mongoTemplate.getCollection("users")
                .find(new org.bson.Document("email", "legacy@example.test")).first();
        assertThat(reread).isNotNull();
        assertThat(reread.get("preferredLanguage"))
                .as("legacy user document remains unchanged on read (lazy default contract)")
                .isNull();
    }

    /**
     * F-6 §3 (MEDIUM advisory, plan-amendment-f6.md): for each URL-bearing
     * email template key, the resolved URL set with identical args is
     * identical across en-US and pt-BR. Catches the phishing-vector regression
     * where a translator hardcodes a different host into the pt-BR bundle.
     */
    @Test
    @DisplayName("F-6 §3: email link targets are identical across en-US and pt-BR")
    void f6_link_targets_identical_across_languages() {
        Pattern URL_PATTERN = Pattern.compile("https?://[^\\s<>\"']+");
        String testUrl = "https://test.example.com/verify?token=ABC123";

        // Verification email: identical RenderedEmail bodies must produce the
        // identical URL set when fed identical args.
        RenderedEmail verifyEn = emailTemplateRenderer.verification(Language.EN_US, testUrl);
        RenderedEmail verifyPt = emailTemplateRenderer.verification(Language.PT_BR, testUrl);
        assertThat(extractUrls(verifyEn.htmlBody(), URL_PATTERN))
                .as("Verification email URL set must match across en-US and pt-BR bundles")
                .isEqualTo(extractUrls(verifyPt.htmlBody(), URL_PATTERN));

        // Password-reset email: same contract.
        RenderedEmail resetEn = emailTemplateRenderer.passwordReset(Language.EN_US, testUrl);
        RenderedEmail resetPt = emailTemplateRenderer.passwordReset(Language.PT_BR, testUrl);
        assertThat(extractUrls(resetEn.htmlBody(), URL_PATTERN))
                .as("Password-reset email URL set must match across en-US and pt-BR bundles")
                .isEqualTo(extractUrls(resetPt.htmlBody(), URL_PATTERN));
    }

    private static Set<String> extractUrls(String html, Pattern pattern) {
        Set<String> urls = new HashSet<>();
        Matcher m = pattern.matcher(html);
        while (m.find()) {
            urls.add(m.group());
        }
        return urls;
    }
}
