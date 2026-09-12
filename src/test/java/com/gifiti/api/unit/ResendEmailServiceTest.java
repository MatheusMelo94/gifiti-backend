package com.gifiti.api.unit;

import com.gifiti.api.service.ResendEmailService;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.Emails;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the swallow-and-log contract of the async email sender.
 *
 * <p>{@code send} runs under {@code @Async}, so anything it throws lands on a pool
 * thread where nothing observes it. Commit {@code c00fd46} widened the
 * predecessor SMTP sender to {@code catch (Exception)} for exactly that reason;
 * the Resend rewrite ({@code 6f93881}) narrowed it back to {@code ResendException}
 * and silently reopened the gap. These tests keep it closed.
 */
@DisplayName("ResendEmailService")
class ResendEmailServiceTest {

    private static final String INVALID_KEY_BODY =
            "{\"statusCode\":401,\"name\":\"invalid_api_key\",\"message\":\"API key is invalid\"}";

    private Emails emails;
    private ResendEmailService service;

    @BeforeEach
    void setUp() {
        Resend resend = mock(Resend.class);
        emails = mock(Emails.class);
        when(resend.emails()).thenReturn(emails);
        service = new ResendEmailService(resend, "hello@ggifiti.com");
    }

    @Test
    @DisplayName("sends through the Resend client on the happy path")
    void sendsViaResendClient() throws ResendException {
        CreateEmailResponse response = mock(CreateEmailResponse.class);
        when(response.getId()).thenReturn("resend-id-1");
        when(emails.send(any(CreateEmailOptions.class))).thenReturn(response);

        service.send("user@example.com", "Verify your email", "<p>body</p>");

        verify(emails).send(any(CreateEmailOptions.class));
    }

    @Test
    @DisplayName("swallows ResendException — the 2026-09-12 invalid-key failure shape")
    void swallowsCheckedResendException() throws ResendException {
        when(emails.send(any(CreateEmailOptions.class)))
                .thenThrow(new ResendException(401, INVALID_KEY_BODY));

        assertThatCode(() -> service.send("user@example.com", "subject", "<p>body</p>"))
                .as("a rejected API key must not propagate out of the async sender")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("swallows unchecked exceptions too — the c00fd46 regression guard")
    void swallowsUncheckedException() throws ResendException {
        // The narrow `catch (ResendException)` let anything unchecked escape into
        // the @Async pool thread and disappear with no log line whatsoever. This is
        // the case that produced no evidence at all during the outage.
        when(emails.send(any(CreateEmailOptions.class)))
                .thenThrow(new IllegalStateException("unchecked failure from the HTTP layer"));

        assertThatCode(() -> service.send("user@example.com", "subject", "<p>body</p>"))
                .as("unchecked SDK/transport failures must be caught and logged, not swallowed silently")
                .doesNotThrowAnyException();
    }
}
