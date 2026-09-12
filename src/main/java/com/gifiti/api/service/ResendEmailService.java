package com.gifiti.api.service;

import com.resend.Resend;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Profile("!test")
public class ResendEmailService implements EmailService {

    private final Resend resend;
    private final String fromAddress;

    @Autowired
    public ResendEmailService(
            @Value("${app.resend.api-key}") String apiKey,
            @Value("${app.mail.from}") String fromAddress) {
        this(new Resend(apiKey), fromAddress);
    }

    /**
     * Testing seam — lets a stubbed {@link Resend} client be injected so the
     * failure paths below can be exercised without a live API key. Not annotated
     * {@code @Autowired}: Spring always resolves the property-driven constructor
     * above.
     */
    public ResendEmailService(Resend resend, String fromAddress) {
        this.resend = resend;
        this.fromAddress = fromAddress;
    }

    /**
     * Sends one email, never throwing to the caller.
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
        } catch (Exception e) {
            log.error("Failed to send email to {}: {} - {}", to, e.getClass().getSimpleName(), e.getMessage());
        }
    }
}
