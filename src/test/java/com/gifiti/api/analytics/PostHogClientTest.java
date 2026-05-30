package com.gifiti.api.analytics;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.posthog.server.PostHogCaptureOptions;
import com.posthog.server.PostHogInterface;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for {@link PostHogClient} — the wrapper around the PostHog Java
 * SDK that enforces the §5.6 PII allowlist (event-name + property allowlist),
 * the split-gate model (no-op when disabled), and async fail-open semantics.
 *
 * <p>Cite: Architect plan v2 § Component 5.3 + 5.6, Security findings F-5
 * (productLink in forbidden list) and F-6 (no-exception-propagation,
 * non-blocking capture, flush-on-shutdown).</p>
 */
class PostHogClientTest {

    private PostHogInterface sdk;
    private ListAppender<ILoggingEvent> logAppender;
    private Logger postHogLogger;

    @BeforeEach
    void setup() {
        sdk = mock(PostHogInterface.class);
        postHogLogger = (Logger) LoggerFactory.getLogger(PostHogClient.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        postHogLogger.addAppender(logAppender);
    }

    @AfterEach
    void teardown() {
        postHogLogger.detachAppender(logAppender);
    }

    private PostHogClient enabledClient() {
        return new PostHogClient(sdk, true);
    }

    private PostHogClient disabledClient() {
        // Disabled wrapper: SDK is irrelevant — pass null to prove the
        // wrapper never touches the SDK reference when disabled.
        return new PostHogClient(null, false);
    }

    @Test
    @DisplayName("capture() is a no-op when the wrapper is disabled — split-gate enforcement")
    void capture_when_enabled_false_is_noop() {
        PostHogClient client = disabledClient();

        // No exception, no SDK call. Null sdk would throw NPE on any access.
        client.capture("signup_completed", "user-123", Map.of("signup_trigger", "DIRECT"));

        // SDK is null — if the wrapper had touched it, we'd have NPE'd already.
        // No assertion on logs: disabled mode is intentionally silent.
    }

    @Test
    @DisplayName("capture() invokes the SDK exactly once when enabled and inputs are valid")
    void capture_when_enabled_true_invokes_sdk() {
        PostHogClient client = enabledClient();

        Map<String, Object> props = new HashMap<>();
        props.put("signup_trigger", "DIRECT");
        client.capture("signup_completed", "user-123", props);
        client.awaitPendingForTest();

        ArgumentCaptor<PostHogCaptureOptions> optionsCaptor =
                ArgumentCaptor.forClass(PostHogCaptureOptions.class);
        verify(sdk, times(1)).capture(eq("user-123"), eq("signup_completed"), optionsCaptor.capture());

        PostHogCaptureOptions captured = optionsCaptor.getValue();
        assertThat(captured.getProperties())
                .containsEntry("signup_trigger", "DIRECT");
    }

    @Test
    @DisplayName("capture() drops events whose name is not in the 7-event allowlist")
    void capture_drops_event_with_unknown_name() {
        PostHogClient client = enabledClient();

        client.capture("unknown_event", "user-123", Map.of());
        client.awaitPendingForTest();

        verifyNoInteractions(sdk);
        assertThat(logAppender.list)
                .anySatisfy(event -> {
                    assertThat(event.getLevel().toString()).isEqualTo("WARN");
                    assertThat(event.getFormattedMessage()).contains("unknown_event");
                });
    }

    @Test
    @DisplayName("capture() drops a property whose key is not in the per-event allowlist (still sends event)")
    void capture_drops_property_with_unknown_key() {
        PostHogClient client = enabledClient();

        Map<String, Object> props = new HashMap<>();
        props.put("user_id", "user-1");
        props.put("occasion_type", "BIRTHDAY");
        props.put("item_count_at_creation", 0);
        props.put("secret_key", "leaked");

        client.capture("wishlist_created", "user-1", props);
        client.awaitPendingForTest();

        ArgumentCaptor<PostHogCaptureOptions> captor =
                ArgumentCaptor.forClass(PostHogCaptureOptions.class);
        verify(sdk).capture(eq("user-1"), eq("wishlist_created"), captor.capture());
        Map<String, Object> sent = captor.getValue().getProperties();
        assertThat(sent).containsKeys("user_id", "occasion_type", "item_count_at_creation");
        assertThat(sent).doesNotContainKey("secret_key");

        assertThat(logAppender.list)
                .anySatisfy(event -> {
                    assertThat(event.getLevel().toString()).isEqualTo("WARN");
                    assertThat(event.getFormattedMessage()).contains("secret_key");
                });
    }

    @Test
    @DisplayName("capture() drops a property keyed 'email' even if a caller tries to send it")
    void capture_drops_property_named_email() {
        PostHogClient client = enabledClient();

        Map<String, Object> props = new HashMap<>();
        props.put("user_id", "user-1");
        props.put("occasion_type", "BIRTHDAY");
        props.put("item_count_at_creation", 0);
        props.put("email", "leak@example.test");

        client.capture("wishlist_created", "user-1", props);
        client.awaitPendingForTest();

        ArgumentCaptor<PostHogCaptureOptions> captor =
                ArgumentCaptor.forClass(PostHogCaptureOptions.class);
        verify(sdk).capture(anyString(), anyString(), captor.capture());
        assertThat(captor.getValue().getProperties()).doesNotContainKey("email");

        assertThat(logAppender.list)
                .anySatisfy(event -> {
                    assertThat(event.getLevel().toString()).isEqualTo("WARN");
                    assertThat(event.getFormattedMessage()).contains("email");
                });
    }

    @Test
    @DisplayName("FORBIDDEN_PROPERTIES contains 'accessCode' (feature 008 / T14, Security findings F-9 defense-in-depth)")
    void forbidden_properties_includes_accessCode() {
        // Per Security findings F-9: even though accessCode is not currently
        // on any event's allowlist (and thus would already be dropped at
        // emission), the defense-in-depth deny list MUST list it so that any
        // future taxonomy expansion that mistakenly adds accessCode to an
        // event's allowlist still cannot leak the secret.
        assertThat(PostHogClient.FORBIDDEN_PROPERTIES).contains("accessCode");
    }

    @Test
    @DisplayName("capture() drops a property keyed 'productLink' (security-findings.md F-5)")
    void capture_drops_property_named_productLink() {
        PostHogClient client = enabledClient();

        Map<String, Object> props = new HashMap<>();
        props.put("wishlist_id", "wl-1");
        props.put("user_id", "user-1");
        props.put("item_position", 0);
        props.put("productLink", "https://example.test/item/123?secret=token");

        client.capture("item_added", "user-1", props);
        client.awaitPendingForTest();

        ArgumentCaptor<PostHogCaptureOptions> captor =
                ArgumentCaptor.forClass(PostHogCaptureOptions.class);
        verify(sdk).capture(anyString(), anyString(), captor.capture());
        assertThat(captor.getValue().getProperties()).doesNotContainKey("productLink");

        assertThat(logAppender.list)
                .anySatisfy(event -> {
                    assertThat(event.getLevel().toString()).isEqualTo("WARN");
                    assertThat(event.getFormattedMessage()).contains("productLink");
                });
    }

    @Test
    @DisplayName("capture() does not propagate SDK exceptions — fail-open (security-findings.md F-6)")
    void capture_does_not_propagate_sdk_exceptions() {
        PostHogClient client = enabledClient();
        doThrow(new RuntimeException("PostHog is down"))
                .when(sdk).capture(anyString(), anyString(), any(PostHogCaptureOptions.class));

        // Must not throw.
        client.capture("signup_completed", "user-1", Map.of("signup_trigger", "DIRECT"));
        client.awaitPendingForTest();

        assertThat(logAppender.list)
                .anySatisfy(event -> {
                    assertThat(event.getLevel().toString()).isEqualTo("WARN");
                    assertThat(event.getFormattedMessage()).contains("PostHog");
                });
    }

    @Test
    @DisplayName("capture() returns to caller within 50ms even when the SDK takes longer (sanity check on async semantic)")
    void capture_does_not_block_caller() throws Exception {
        // Simulate a slow SDK that takes 200ms — wrapper must still return < 50ms
        // because it dispatches to a background thread and does not await the call.
        // F-6 finding: assert wrapper does not block the calling thread for
        // observable user-facing latency.
        doAnswerWithDelay(sdk, 200);

        PostHogClient client = enabledClient();

        long start = System.nanoTime();
        client.capture("signup_completed", "user-1", Map.of("signup_trigger", "DIRECT"));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMs).isLessThan(50);
    }

    @Test
    @DisplayName("flush() delegates to the SDK exactly once when enabled")
    void flush_invokes_sdk_flush_once() {
        PostHogClient client = enabledClient();

        client.flush();

        verify(sdk, times(1)).flush();
    }

    @Test
    @DisplayName("flush() is a no-op when the wrapper is disabled")
    void flush_is_noop_when_disabled() {
        // Same null-SDK trick as the capture no-op test.
        PostHogClient client = disabledClient();
        client.flush();
    }

    private static void doAnswerWithDelay(PostHogInterface mock, long delayMs) {
        org.mockito.Mockito.doAnswer(invocation -> {
            Thread.sleep(delayMs);
            return null;
        }).when(mock).capture(anyString(), anyString(), any(PostHogCaptureOptions.class));
    }
}
