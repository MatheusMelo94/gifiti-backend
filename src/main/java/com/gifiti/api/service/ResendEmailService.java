package com.gifiti.api.service;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import lombok.extern.slf4j.Slf4j;
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

    public ResendEmailService(
            @Value("${app.resend.api-key}") String apiKey,
            @Value("${app.mail.from}") String fromAddress) {
        this.resend = new Resend(apiKey);
        this.fromAddress = fromAddress;
    }

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
        } catch (ResendException e) {
            log.error("Failed to send email to {}: {} - {}", to, e.getClass().getSimpleName(), e.getMessage());
        }
    }
}
