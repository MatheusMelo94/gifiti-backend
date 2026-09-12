package com.gifiti.api.health;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Probes the configured Resend credential so an unusable API key is loud at
 * deploy time instead of silent until the first user tries to register.
 *
 * <p>Motivated by the 2026-09-12 outage: {@code RESEND_API_KEY} was present but
 * rejected, so the application booted clean and every verification email failed
 * one-by-one on a pool thread. {@code new Resend(apiKey)} performs no network
 * call and Spring's placeholder resolution only proves the variable is non-empty,
 * so nothing in the startup path could have caught it.
 *
 * <h2>Why this never reports DOWN</h2>
 * <p>{@code render.yaml} points {@code healthCheckPath} at {@code /actuator/health}.
 * A {@link Status#DOWN} contribution here would fail Render's health check and pull
 * the entire backend out of rotation over an email-only fault — turning a broken
 * signup flow into a total outage, which is strictly worse than the bug this
 * guards against. It therefore reports {@link #DEGRADED}, which is ordered above
 * {@code UP} so it surfaces in the payload but is mapped to HTTP 200 (see
 * {@code management.endpoint.health.status} in {@code application.yml}).
 *
 * <h2>Why a rejected admin call can still be healthy</h2>
 * <p>Resend keys carry either full access or sending-only access, and there is no
 * token-introspection endpoint. The probe calls {@code domains().list()}, which a
 * correctly-scoped sending-only key is *supposed* to refuse. That refusal proves
 * the credential authenticated, so it is treated as healthy — otherwise this
 * indicator would flag every properly least-privileged deployment.
 */
@Slf4j
@Component
@Profile("!test")
public class ResendHealthIndicator implements HealthIndicator {

    /**
     * Custom status, deliberately not {@link Status#DOWN} — see the class Javadoc.
     */
    public static final Status DEGRADED = new Status("DEGRADED");

    /**
     * Render polls {@code /actuator/health} frequently; without a cache this
     * would call Resend on every poll and risk rate-limiting the account.
     */
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final Resend resend;
    private final AtomicReference<Probe> cached = new AtomicReference<>();

    @Autowired
    public ResendHealthIndicator(@Value("${app.resend.api-key}") String apiKey) {
        this(new Resend(apiKey));
    }

    /**
     * Testing seam — lets a stubbed {@link Resend} client be injected. A separate
     * client from {@code ResendEmailService}'s is intentional: the probe must not
     * share state with the send path it is reporting on.
     */
    public ResendHealthIndicator(Resend resend) {
        this.resend = resend;
    }

    /**
     * Probes once at boot so a bad credential is visible in the deploy log rather
     * than only to whoever thinks to query actuator. Never blocks or fails
     * startup: a Resend outage at boot must not stop the service from serving
     * every non-email request.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void probeOnStartup() {
        Probe probe = refresh();
        if (DEGRADED.equals(probe.status())) {
            log.error("RESEND_CREDENTIAL_REJECTED: {} — verification and password-reset "
                    + "emails will NOT be delivered. Check RESEND_API_KEY.", probe.detail());
        } else {
            log.info("Resend credential probe: {} — {}", probe.status().getCode(), probe.detail());
        }
    }

    @Override
    public Health health() {
        Probe probe = current();
        return Health.status(probe.status())
                .withDetail("probe", probe.detail())
                .withDetail("checkedAt", probe.checkedAt().toString())
                .build();
    }

    private Probe current() {
        Probe existing = cached.get();
        if (existing == null || existing.checkedAt().isBefore(Instant.now().minus(CACHE_TTL))) {
            return refresh();
        }
        return existing;
    }

    private Probe refresh() {
        Probe probe = executeProbe();
        cached.set(probe);
        return probe;
    }

    private Probe executeProbe() {
        Instant now = Instant.now();
        try {
            resend.domains().list();
            return new Probe(Status.UP, "credential accepted (full access)", now);
        } catch (ResendException e) {
            if (indicatesRestrictedKey(e)) {
                return new Probe(Status.UP, "credential accepted (sending-restricted)", now);
            }
            if (indicatesRejectedKey(e)) {
                return new Probe(DEGRADED, "credential rejected by Resend: " + e.getMessage(), now);
            }
            return new Probe(Status.UNKNOWN, "inconclusive: " + e.getClass().getSimpleName(), now);
        } catch (Exception e) {
            // Transport faults say nothing about the credential. Reporting UNKNOWN
            // keeps a Resend outage from being misread as a bad key — and UNKNOWN
            // is ordered below UP, so it cannot degrade the aggregate status.
            return new Probe(Status.UNKNOWN, "could not reach Resend: " + e.getClass().getSimpleName(), now);
        }
    }

    /**
     * A sending-only key refusing an admin endpoint proves it authenticated.
     * Checked before {@link #indicatesRejectedKey} because Resend returns 401 for
     * both cases and only the error name distinguishes them.
     */
    private static boolean indicatesRestrictedKey(ResendException e) {
        return searchable(e).contains("restricted");
    }

    private static boolean indicatesRejectedKey(ResendException e) {
        Integer statusCode = e.getStatusCode();
        if (statusCode != null && statusCode >= 500) {
            // A Resend-side fault is not a verdict on the credential.
            return false;
        }
        String searchable = searchable(e);
        return searchable.contains("invalid_api_key")
                || searchable.contains("api key is invalid")
                || searchable.contains("missing_api_key")
                || searchable.contains("unauthorized");
    }

    /**
     * Matches against error name and message together, and loosely: the SDK does
     * not guarantee {@code getErrorName()} is populated, and a misclassification
     * here is only ever a wrong label on a health detail.
     */
    private static String searchable(ResendException e) {
        return (e.getErrorName() + " " + e.getMessage()).toLowerCase(Locale.ROOT);
    }

    private record Probe(Status status, String detail, Instant checkedAt) { }
}
