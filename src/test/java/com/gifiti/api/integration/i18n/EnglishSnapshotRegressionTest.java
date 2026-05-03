package com.gifiti.api.integration.i18n;

import com.gifiti.api.dto.request.ForgotPasswordRequest;
import com.gifiti.api.dto.request.LoginRequest;
import com.gifiti.api.dto.request.RegisterRequest;
import com.gifiti.api.dto.response.ErrorResponse;
import com.gifiti.api.integration.BaseIntegrationTest;
import com.gifiti.api.integration.support.CapturingEmailService;
import com.gifiti.api.integration.support.CapturingEmailService.CapturedEmail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Group H — No regression for existing users (spec criterion #20).
 *
 * <p>Covers TC-32 through TC-36. Every assertion is byte-for-byte equality
 * against the captured pre-i18n English literal. This is the rollback safety
 * net: if any English string in any response shifts after the i18n migration,
 * one of these tests fails, blocking PASS in QA.
 *
 * <p>The English snapshots are the literal strings from the bundles
 * ({@code messages.properties}); they must equal the original pre-refactor
 * production values. The English-bundle file {@code messages.properties} also
 * carries {@code # spec criterion #20} comments around these keys.
 *
 * <p>Cite: {@code architecture-conventions.md § Engineering Workflow},
 * {@code § Testing}; {@code spec.md} criterion #20 ("no English copy may
 * shift by a single byte for users who haven't opted into pt-BR").
 */
class EnglishSnapshotRegressionTest extends BaseIntegrationTest {

    @Autowired
    private CapturingEmailService capturingEmailService;

    // Snapshots — the literal pre-i18n English strings. Treat as a
    // contractual constant: any drift here breaks the rollback contract.
    private static final String SNAP_REGISTER_SUCCESS =
            "Registration successful. Please check your email to verify your account.";
    private static final String SNAP_VALIDATION_FAILED = "Validation failed";
    private static final String SNAP_PASSWORD_SIZE = "Password must be 12-128 characters";
    private static final String SNAP_EMAIL_INVALID = "Email must be valid";
    private static final String SNAP_DISPLAYNAME_SIZE = "Display name must not exceed 50 characters";
    private static final String SNAP_MALFORMED_BODY = "Malformed request body";
    private static final String SNAP_METHOD_NOT_SUPPORTED_DELETE_LOGIN =
            "Method 'DELETE' is not supported for this endpoint";
    private static final String SNAP_RESOURCE_NOT_FOUND_WISHLIST =
            "Wishlist not found with id: '000000000000000000000000'";
    private static final String SNAP_CONFLICT_EMAIL_ALREADY_REGISTERED = "Email already registered";
    private static final String SNAP_UNAUTHORIZED_CREDENTIALS_INVALID = "Invalid email or password";
    private static final String SNAP_FORGOT_PASSWORD_ACK =
            "If an account exists with this email, a password reset link has been sent.";

    private static final String SNAP_VERIFICATION_SUBJECT =
            "Welcome to Gifiti - Please confirm your email";
    private static final String SNAP_VERIFICATION_BODY_THANKS =
            "Thanks for signing up. Please confirm your email address";
    private static final String SNAP_VERIFICATION_BODY_CTA = "Confirm Email Address";
    private static final String SNAP_VERIFICATION_BODY_IGNORE =
            "If you didn't create a Gifiti account";

    private static final String SNAP_PASSWORD_RESET_SUBJECT = "Reset your Gifiti password";
    private static final String SNAP_PASSWORD_RESET_BODY_HEADING = "Reset your password";
    private static final String SNAP_PASSWORD_RESET_BODY_CTA = "Reset Password";
    private static final String SNAP_PASSWORD_RESET_BODY_IGNORE =
            "If you didn't request a password reset";

    @BeforeEach
    void clearCapturedEmails() {
        capturingEmailService.clear();
    }

    private ErrorResponse parseError(MvcResult result) throws Exception {
        return objectMapper.readValue(
                result.getResponse().getContentAsString(), ErrorResponse.class);
    }

    @Test
    @DisplayName("TC-32: register success message is byte-identical to pre-i18n English literal")
    void tc32_register_success_message_byte_identical() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("snap-register@example.test")
                .password("BlueP4nther$Xyz2!")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value(SNAP_REGISTER_SUCCESS));
    }

    @Test
    @DisplayName("TC-33: GlobalExceptionHandler English messages byte-identical (resource-not-found, conflict, unauthorized, malformed-body, method)")
    void tc33_handler_english_messages_byte_identical() throws Exception {
        // ResourceNotFoundException
        String token = createUserAndGetToken("snap-handler@example.test", "BlueP4nther$Xyz2!");
        MvcResult rnf = mockMvc.perform(get("/api/v1/wishlists/000000000000000000000000")
                        .header("Authorization", bearerToken(token)))
                .andExpect(status().isNotFound())
                .andReturn();
        assertThat(parseError(rnf).getMessage()).isEqualTo(SNAP_RESOURCE_NOT_FOUND_WISHLIST);

        // ConflictException — duplicate registration.
        registerTestUser("snap-conflict@example.test", "BlueP4nther$Xyz2!");
        RegisterRequest dup = RegisterRequest.builder()
                .email("snap-conflict@example.test")
                .password("BlueP4nther$Xyz2!")
                .build();
        MvcResult conflict = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dup)))
                .andExpect(status().isConflict())
                .andReturn();
        assertThat(parseError(conflict).getMessage())
                .isEqualTo(SNAP_CONFLICT_EMAIL_ALREADY_REGISTERED);

        // UnauthorizedException — bad credentials.
        registerTestUser("snap-unauth@example.test", "BlueP4nther$Xyz2!");
        LoginRequest wrong = LoginRequest.builder()
                .email("snap-unauth@example.test")
                .password("BlueP4nther$Wrong2!")
                .build();
        MvcResult unauth = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrong)))
                .andExpect(status().isUnauthorized())
                .andReturn();
        assertThat(parseError(unauth).getMessage())
                .isEqualTo(SNAP_UNAUTHORIZED_CREDENTIALS_INVALID);

        // Malformed body handler.
        MvcResult malformed = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not valid"))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertThat(parseError(malformed).getMessage()).isEqualTo(SNAP_MALFORMED_BODY);

        // Method-not-supported handler.
        MvcResult method = mockMvc.perform(delete("/api/v1/auth/login"))
                .andExpect(status().isMethodNotAllowed())
                .andReturn();
        assertThat(parseError(method).getMessage())
                .isEqualTo(SNAP_METHOD_NOT_SUPPORTED_DELETE_LOGIN);
    }

    @Test
    @DisplayName("TC-34: validation field messages byte-identical to pre-i18n English literals (representative DTOs)")
    void tc34_validation_messages_byte_identical() throws Exception {
        // RegisterRequest: short password → @Size and @Pattern violation.
        RegisterRequest req = RegisterRequest.builder()
                .email("snap-val@example.test")
                .password("short")
                .build();
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andReturn();

        ErrorResponse body = parseError(result);
        assertThat(body.getMessage()).isEqualTo(SNAP_VALIDATION_FAILED);
        assertThat(body.getDetails())
                .extracting(ErrorResponse.FieldError::getMessage)
                .as("@Size on password preserves byte-identical English literal")
                .contains(SNAP_PASSWORD_SIZE);

        // RegisterRequest: invalid email → @Email violation.
        RegisterRequest badEmail = RegisterRequest.builder()
                .email("not-an-email")
                .password("BlueP4nther$Xyz2!")
                .build();
        MvcResult emailRes = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badEmail)))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertThat(parseError(emailRes).getDetails())
                .extracting(ErrorResponse.FieldError::getMessage)
                .contains(SNAP_EMAIL_INVALID);

        // UpdateProfileRequest: oversize displayName → @Size violation.
        String token = createUserAndGetToken("snap-profile@example.test", "BlueP4nther$Xyz2!");
        MvcResult prof = mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/profile")
                        .header("Authorization", bearerToken(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"" + "x".repeat(51) + "\"}"))
                .andExpect(status().isBadRequest())
                .andReturn();
        assertThat(parseError(prof).getDetails())
                .extracting(ErrorResponse.FieldError::getMessage)
                .contains(SNAP_DISPLAYNAME_SIZE);
    }

    @Test
    @DisplayName("TC-35: verification email subject + 3 body anchors byte-identical (English path)")
    void tc35_verification_email_byte_identical() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("snap-verify@example.test")
                .password("BlueP4nther$Xyz2!")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        List<CapturedEmail> sent = capturingEmailService.sent();
        assertThat(sent).hasSize(1);
        CapturedEmail email = sent.get(0);

        assertThat(email.subject()).isEqualTo(SNAP_VERIFICATION_SUBJECT);
        assertThat(email.body())
                .contains(SNAP_VERIFICATION_BODY_THANKS)
                .contains(SNAP_VERIFICATION_BODY_CTA)
                .contains(SNAP_VERIFICATION_BODY_IGNORE);
    }

    @Test
    @DisplayName("TC-36: password-reset email subject + 3 body anchors byte-identical (English path)")
    void tc36_password_reset_email_byte_identical() throws Exception {
        // Seed a known en-US user, then issue forgot-password.
        registerTestUser("snap-reset@example.test", "BlueP4nther$Xyz2!");
        capturingEmailService.clear();

        ForgotPasswordRequest request = ForgotPasswordRequest.builder()
                .email("snap-reset@example.test")
                .build();
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(SNAP_FORGOT_PASSWORD_ACK));

        List<CapturedEmail> sent = capturingEmailService.sent();
        assertThat(sent).hasSize(1);
        CapturedEmail email = sent.get(0);

        assertThat(email.subject()).isEqualTo(SNAP_PASSWORD_RESET_SUBJECT);
        assertThat(email.body())
                .contains(SNAP_PASSWORD_RESET_BODY_HEADING)
                .contains(SNAP_PASSWORD_RESET_BODY_CTA)
                .contains(SNAP_PASSWORD_RESET_BODY_IGNORE);
    }
}
