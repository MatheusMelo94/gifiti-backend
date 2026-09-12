package com.gifiti.api.unit;

import com.gifiti.api.health.ResendHealthIndicator;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.domains.Domains;
import com.resend.services.domains.model.ListDomainsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the classification rules of the Resend credential probe.
 *
 * <p>The response bodies below are shaped the way the SDK actually parses them:
 * {@code ResendException(int, String)} reads {@code name} into
 * {@code getErrorName()} and {@code message} into {@code getMessage()}.
 */
@DisplayName("ResendHealthIndicator")
class ResendHealthIndicatorTest {

    private static final String INVALID_KEY_BODY =
            "{\"statusCode\":401,\"name\":\"invalid_api_key\",\"message\":\"API key is invalid\"}";
    private static final String RESTRICTED_KEY_BODY =
            "{\"statusCode\":401,\"name\":\"restricted_api_key\","
                    + "\"message\":\"This API key is restricted to only send emails\"}";
    private static final String SERVER_ERROR_BODY =
            "{\"statusCode\":500,\"name\":\"internal_server_error\",\"message\":\"Something went wrong\"}";

    private Domains domains;
    private ResendHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        Resend resend = mock(Resend.class);
        domains = mock(Domains.class);
        when(resend.domains()).thenReturn(domains);
        indicator = new ResendHealthIndicator(resend);
    }

    @Test
    @DisplayName("UP when a full-access credential is accepted")
    void reportsUpForAcceptedCredential() throws ResendException {
        when(domains.list()).thenReturn(mock(ListDomainsResponse.class));

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    @DisplayName("UP for a sending-restricted key — the refusal proves it authenticated")
    void reportsUpForSendingRestrictedCredential() throws ResendException {
        // A correctly least-privileged sending key is *supposed* to be refused by
        // the admin endpoint. Treating that as unhealthy would flag every properly
        // scoped deployment.
        when(domains.list()).thenThrow(new ResendException(401, RESTRICTED_KEY_BODY));

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    @DisplayName("DEGRADED — not DOWN — when the credential is rejected")
    void reportsDegradedForRejectedCredential() throws ResendException {
        when(domains.list()).thenThrow(new ResendException(401, INVALID_KEY_BODY));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(ResendHealthIndicator.DEGRADED);
        assertThat(health.getStatus())
                .as("render.yaml health-checks /actuator/health — DOWN here would pull the "
                        + "whole service out of rotation over an email-only fault")
                .isNotEqualTo(Status.DOWN);
        assertThat(health.getDetails().get("probe").toString()).contains("API key is invalid");
    }

    @Test
    @DisplayName("UNKNOWN for a Resend-side 5xx — not a verdict on the credential")
    void reportsUnknownForServerError() throws ResendException {
        when(domains.list()).thenThrow(new ResendException(500, SERVER_ERROR_BODY));

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UNKNOWN);
    }

    @Test
    @DisplayName("UNKNOWN when Resend cannot be reached at all")
    void reportsUnknownForTransportFailure() throws ResendException {
        when(domains.list()).thenThrow(new IllegalStateException("connection reset"));

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UNKNOWN);
    }

    @Test
    @DisplayName("caches the probe so Render's health polling does not hammer Resend")
    void cachesProbeResult() throws ResendException {
        when(domains.list()).thenReturn(mock(ListDomainsResponse.class));

        indicator.health();
        indicator.health();
        indicator.health();

        verify(domains, times(1)).list();
    }

    @Test
    @DisplayName("startup probe never throws, so a Resend outage cannot block boot")
    void startupProbeIsNonFatal() throws ResendException {
        when(domains.list()).thenThrow(new IllegalStateException("connection reset"));

        indicator.probeOnStartup();

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UNKNOWN);
    }
}
