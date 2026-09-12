package com.gifiti.api.service;

import com.gifiti.api.config.CorrelationIdFilter;
import com.gifiti.api.model.EmailDeliveryLog;
import com.gifiti.api.model.EmailDeliveryStatus;
import com.gifiti.api.repository.EmailDeliveryLogRepository;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@Profile("!test")
public class ResendEmailService implements EmailService {

    private final Resend resend;
    private final String fromAddress;
    private final EmailDeliveryLogRepository deliveryLogRepository;

    @Autowired
    public ResendEmailService(
            @Value("${app.resend.api-key}") String apiKey,
            @Value("${app.mail.from}") String fromAddress,
            EmailDeliveryLogRepository deliveryLogRepository) {
        this(new Resend(apiKey), fromAddress, deliveryLogRepository);
    }

    /**
     * Testing seam — lets a stubbed {@link Resend} client be injected so the
     * failure paths below can be exercised without a live API key. Not annotated
     * {@code @Autowired}: Spring always resolves the property-driven constructor
     * above.
     */
    public ResendEmailService(
            Resend resend,
            String fromAddress,
            EmailDeliveryLogRepository deliveryLogRepository) {
        this.resend = resend;
        this.fromAddress = fromAddress;
        this.deliveryLogRepository = deliveryLogRepository;
    }

    /**
     * Sends one email, never throwing to the caller, and records the outcome.
     *
     * <p>The catch is deliberately broad. Commit {@code c00fd46} widened the
     * predecessor {@code SmtpEmailService} to {@code catch (Exception)} because
     * {@code @Async} runs this on a pool thread where an uncaught throwable is
     * swallowed with no log line at all; the Resend rewrite ({@code 6f93881})
     * narrowed it back to {@code ResendException} and lost that property.
     * Anything unchecked the SDK or its HTTP layer raises — an invalid endpoint,
     * a TLS failure, a JSON parse error — must still produce a log line rather
     * than vanish.
     */
    @Async
    @Override
    public void send(String to, String subject, String body) {
        String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        try {
            log.info("Sending email via Resend to {} with subject: {}", to, subject);
            CreateEmailOptions request = CreateEmailOptions.builder()
                    .from(fromAddress)
                    .to(to)
                    .subject(subject)
                    .html(body)
                    .build();
            CreateEmailResponse response = resend.emails().send(request);
            log.info("Email sent successfully to {} - Resend ID: {}", to, response.getId());
            record(entry(to, subject, correlationId)
                    .status(EmailDeliveryStatus.SENT)
                    .build());
        } catch (Exception e) {
            log.error("Failed to send email to {}: {} - {}", to, e.getClass().getSimpleName(), e.getMessage());
            record(entry(to, subject, correlationId)
                    .status(EmailDeliveryStatus.FAILED)
                    .errorName(errorNameOf(e))
                    .errorMessage(e.getMessage())
                    .build());
        }
    }

    private static EmailDeliveryLog.EmailDeliveryLogBuilder entry(
            String to, String subject, String correlationId) {
        return EmailDeliveryLog.builder()
                .recipient(to)
                .subject(subject)
                .correlationId(correlationId)
                .createdAt(Instant.now());
    }

    /**
     * Persists a delivery record on a best-effort basis.
     *
     * <p>This is diagnostics, not the work. A Mongo failure here must not become a
     * second, louder exception that buries the send outcome it was meant to
     * explain — the exact inversion that made the original outage hard to read.
     */
    private void record(EmailDeliveryLog entry) {
        try {
            deliveryLogRepository.save(entry);
        } catch (Exception e) {
            log.warn("Could not persist email delivery record for {}: {}",
                    entry.getRecipient(), e.getClass().getSimpleName());
        }
    }

    /**
     * Prefers Resend's own error name ({@code invalid_api_key}, {@code restricted_api_key})
     * over the Java class name, since the provider's vocabulary is what an
     * operator will search for.
     */
    private static String errorNameOf(Exception e) {
        if (e instanceof ResendException resendException && resendException.getErrorName() != null) {
            return resendException.getErrorName();
        }
        return e.getClass().getSimpleName();
    }
}
