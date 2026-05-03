package com.gifiti.api.integration.support;

import com.gifiti.api.service.EmailService;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Test double {@link EmailService} that records every {@code send(...)} call
 * for later assertion. Activated under the {@code test} profile alongside the
 * production-only {@code NoOpEmailService}, with {@link Primary} resolving the
 * collision in favor of this capturing impl during integration tests.
 *
 * <p>Used by integration tests in Task 8 of {@code 005-i18n-backend-support}
 * (and forward) to verify that {@code AuthService} delegates to the localized
 * {@code EmailTemplateRenderer} end-to-end and selects the language per the
 * spec criteria #12, #13.</p>
 */
@Service
@Primary
@Profile("test")
public class CapturingEmailService implements EmailService {

    private final List<CapturedEmail> sent = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void send(String to, String subject, String body) {
        sent.add(new CapturedEmail(to, subject, body));
    }

    /**
     * Returns an unmodifiable snapshot of every email captured since
     * {@link #clear()} (or service construction).
     */
    public List<CapturedEmail> sent() {
        synchronized (sent) {
            return List.copyOf(sent);
        }
    }

    /**
     * Drops every captured email. Tests should call this before exercising
     * scenarios that depend on capture-count assertions.
     */
    public void clear() {
        sent.clear();
    }

    /**
     * @param to recipient address (the {@code email} arg passed to
     *           {@link #send(String, String, String)}).
     * @param subject the locale-resolved Subject header.
     * @param body the locale-resolved HTML body.
     */
    public record CapturedEmail(String to, String subject, String body) {}
}
