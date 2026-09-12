package com.gifiti.api.unit;

import com.gifiti.api.config.CorrelationIdFilter;
import com.gifiti.api.model.EmailDeliveryLog;
import com.gifiti.api.model.EmailDeliveryStatus;
import com.gifiti.api.repository.EmailDeliveryLogRepository;
import com.gifiti.api.service.ResendEmailService;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.Emails;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the swallow-and-log contract of the async email sender, plus the delivery
 * record it leaves behind.
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
    private EmailDeliveryLogRepository deliveryLogRepository;
    private ResendEmailService service;

    @BeforeEach
    void setUp() {
        Resend resend = mock(Resend.class);
        emails = mock(Emails.class);
        deliveryLogRepository = mock(EmailDeliveryLogRepository.class);
        when(resend.emails()).thenReturn(emails);
        service = new ResendEmailService(resend, "hello@ggifiti.com", deliveryLogRepository);
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("sends through the Resend client and records SENT")
    void sendsViaResendClientAndRecordsSuccess() throws ResendException {
        stubSuccessfulSend();

        service.send("user@example.com", "Verify your email", "<p>body</p>");

        verify(emails).send(any(CreateEmailOptions.class));
        EmailDeliveryLog recorded = captureRecord();
        assertThat(recorded.getStatus()).isEqualTo(EmailDeliveryStatus.SENT);
        assertThat(recorded.getRecipient()).isEqualTo("user@example.com");
        assertThat(recorded.getCreatedAt()).isNotNull();
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
    @DisplayName("records FAILED with the provider's own error name")
    void recordsFailureWithProviderErrorName() throws ResendException {
        when(emails.send(any(CreateEmailOptions.class)))
                .thenThrow(new ResendException(401, INVALID_KEY_BODY));

        service.send("user@example.com", "subject", "<p>body</p>");

        EmailDeliveryLog recorded = captureRecord();
        assertThat(recorded.getStatus()).isEqualTo(EmailDeliveryStatus.FAILED);
        assertThat(recorded.getErrorName())
                .as("operators search Resend's vocabulary, not Java class names")
                .isEqualTo("invalid_api_key");
        assertThat(recorded.getErrorMessage()).contains("API key is invalid");
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

        EmailDeliveryLog recorded = captureRecord();
        assertThat(recorded.getStatus()).isEqualTo(EmailDeliveryStatus.FAILED);
        assertThat(recorded.getErrorName()).isEqualTo("IllegalStateException");
    }

    @Test
    @DisplayName("stamps the delivery record with the originating correlation ID")
    void recordsCorrelationId() throws ResendException {
        stubSuccessfulSend();
        MDC.put(CorrelationIdFilter.CORRELATION_ID_MDC_KEY, "corr-42");

        service.send("user@example.com", "subject", "<p>body</p>");

        assertThat(captureRecord().getCorrelationId()).isEqualTo("corr-42");
    }

    @Test
    @DisplayName("a failing delivery-log write never masks the send outcome")
    void deliveryLogFailureIsNonFatal() throws ResendException {
        stubSuccessfulSend();
        when(deliveryLogRepository.save(any(EmailDeliveryLog.class)))
                .thenThrow(new IllegalStateException("mongo unavailable"));

        assertThatCode(() -> service.send("user@example.com", "subject", "<p>body</p>"))
                .as("diagnostics must never become a louder failure than the work they describe")
                .doesNotThrowAnyException();
    }

    private void stubSuccessfulSend() throws ResendException {
        CreateEmailResponse response = mock(CreateEmailResponse.class);
        when(response.getId()).thenReturn("resend-id-1");
        when(emails.send(any(CreateEmailOptions.class))).thenReturn(response);
    }

    private EmailDeliveryLog captureRecord() {
        ArgumentCaptor<EmailDeliveryLog> captor = ArgumentCaptor.forClass(EmailDeliveryLog.class);
        verify(deliveryLogRepository).save(captor.capture());
        return captor.getValue();
    }
}
