package com.gifiti.api.unit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.gifiti.api.service.PasswordValidationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PasswordValidationService} — the validator that
 * rejects weak passwords during register and reset flows.
 *
 * <p>Cite: Move 2 / Diagnosis C (cofounder decision 2026-05-06): adds INFO-level
 * structured calibration telemetry around rejections so we can learn over the
 * next week of production traffic which rules fire most often without
 * altering the rules themselves. The decision to recalibrate (e.g., relax the
 * "common_pattern" rule for real users) is deferred until telemetry data
 * lands.</p>
 *
 * <p>LGPD Art. 6 IX (data minimization) + {@code architecture-conventions.md
 * § Logging}: never log password content, length, character class, hash, or
 * any input that could leak the user-supplied secret. The {@code
 * correlationId} from MDC is the only join key back to the request.</p>
 */
class PasswordValidationServiceTest {

    private PasswordValidationService service;
    private Logger serviceLogger;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setup() {
        service = new PasswordValidationService();
        serviceLogger = (Logger) LoggerFactory.getLogger(PasswordValidationService.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        serviceLogger.addAppender(logAppender);
    }

    @AfterEach
    void teardown() {
        serviceLogger.detachAppender(logAppender);
        MDC.clear();
    }

    @Test
    @DisplayName("rejection on common_pattern emits INFO calibration telemetry tagged with rule name and correlation ID, with no password content leaked")
    void rejection_on_common_pattern_emits_info_telemetry() {
        // Seed a correlation ID into MDC the way CorrelationIdFilter does.
        String correlationId = "fixture-corr-9f3c";
        MDC.put("correlationId", correlationId);

        // 'qwerty' is one of the COMMON_PATTERNS — guaranteed to fail the
        // common_pattern rule. We deliberately pick a password substring that
        // is NOT itself a rule name so the assertion "log line does not
        // contain the password content" is meaningful.
        String input = "Qwerty-Reject-Telemetry-2026!";

        // Feature 009 / T13: PasswordValidationService now throws
        // WeakPasswordException (ERROR_CODE=WEAK_PASSWORD) per ADR 0009
        // Decision C + G. Per-rule telemetry below remains unchanged.
        assertThatThrownBy(() -> service.validate(input, "tester@example.com"))
                .isInstanceOf(com.gifiti.api.exception.WeakPasswordException.class);

        // Single INFO log line with rule and correlation_id.
        assertThat(logAppender.list)
                .anySatisfy(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.INFO);
                    assertThat(event.getFormattedMessage())
                            .contains("password_validation_rejected")
                            .contains("rule=common_pattern")
                            .contains("correlation_id=" + correlationId);
                });

        // Privacy gate: the user-supplied input must NEVER appear in any log
        // line, in any form (LGPD Art. 6 IX). Check the full input and a
        // distinctive non-rule-named substring.
        assertThat(logAppender.list)
                .as("no log line may leak password content")
                .allSatisfy(event -> assertThat(event.getFormattedMessage())
                        .doesNotContain(input)
                        .doesNotContain("Reject-Telemetry-2026"));

        // Privacy gate: the user email must NEVER appear in calibration logs
        // (correlation_id is the join key, not the email).
        assertThat(logAppender.list)
                .as("no log line may leak the user email")
                .allSatisfy(event -> assertThat(event.getFormattedMessage())
                        .doesNotContain("tester@example.com"));
    }
}
