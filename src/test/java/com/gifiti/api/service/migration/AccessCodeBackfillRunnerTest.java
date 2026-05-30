package com.gifiti.api.service.migration;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.gifiti.api.integration.BaseIntegrationTest;
import com.gifiti.api.model.Wishlist;
import com.gifiti.api.model.enums.Visibility;
import com.gifiti.api.repository.WishlistRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link AccessCodeBackfillRunner} (feature 008 / T13).
 *
 * <p>Per ADR 0008 § Decision J (hybrid migration + lazy generation):
 * <ul>
 *   <li>One-shot {@code CommandLineRunner} at deploy time touches every
 *       PRIVATE wishlist where {@code accessCode} is null.</li>
 *   <li>Idempotent — a second run touches zero documents (Security findings
 *       F-6 pin 2).</li>
 *   <li>Atomic compare-and-set at WRITE time — only updates if
 *       {@code accessCode} is still null (Security findings F-6 pin 3).</li>
 *   <li>Bounded query (Security findings F-6 pin 1).</li>
 *   <li>Logs document count at INFO; NEVER logs the codes (Security
 *       findings F-5 / F-6).</li>
 * </ul>
 *
 * <p>Uses {@link BaseIntegrationTest} to inherit the singleton Mongo
 * Testcontainer — the runner is wired into the Spring context, but this test
 * invokes it directly so the assertion model is deterministic.
 */
class AccessCodeBackfillRunnerTest extends BaseIntegrationTest {

    @Autowired
    private AccessCodeBackfillRunner runner;

    @Autowired
    private WishlistRepository wishlistRepository;

    private ListAppender<ILoggingEvent> logAppender;
    private Logger runnerLogger;

    @BeforeEach
    void setupLogger() {
        runnerLogger = (Logger) LoggerFactory.getLogger(AccessCodeBackfillRunner.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        runnerLogger.addAppender(logAppender);
    }

    @AfterEach
    void teardownLogger() {
        runnerLogger.detachAppender(logAppender);
    }

    @Test
    @DisplayName("populates accessCode for PRIVATE wishlists with null code")
    void populatesNullAccessCodeForPrivateWishlists() throws Exception {
        // Seed 3 PRIVATE wishlists with null accessCode (bypass the service
        // so we exercise the legacy-state the migration is designed to fix).
        Wishlist a = seedRaw("backfill-owner-1", "Title A", Visibility.PRIVATE, null);
        Wishlist b = seedRaw("backfill-owner-2", "Title B", Visibility.PRIVATE, null);
        Wishlist c = seedRaw("backfill-owner-3", "Title C", Visibility.PRIVATE, null);

        runner.run();

        Wishlist updatedA = wishlistRepository.findById(a.getId()).orElseThrow();
        Wishlist updatedB = wishlistRepository.findById(b.getId()).orElseThrow();
        Wishlist updatedC = wishlistRepository.findById(c.getId()).orElseThrow();

        assertThat(updatedA.getAccessCode()).matches("^\\d{4}$");
        assertThat(updatedB.getAccessCode()).matches("^\\d{4}$");
        assertThat(updatedC.getAccessCode()).matches("^\\d{4}$");
    }

    @Test
    @DisplayName("leaves PRIVATE wishlists with existing accessCode UNCHANGED")
    void leavesExistingAccessCodeUnchanged() throws Exception {
        Wishlist seeded = seedRaw("backfill-owner-existing", "Title", Visibility.PRIVATE, "5678");

        runner.run();

        Wishlist after = wishlistRepository.findById(seeded.getId()).orElseThrow();
        assertThat(after.getAccessCode()).isEqualTo("5678");
    }

    @Test
    @DisplayName("NEVER generates an accessCode for PUBLIC wishlists")
    void neverGeneratesCodeForPublicWishlists() throws Exception {
        Wishlist seeded = seedRaw("backfill-owner-public", "Title", Visibility.PUBLIC, null);

        runner.run();

        Wishlist after = wishlistRepository.findById(seeded.getId()).orElseThrow();
        assertThat(after.getAccessCode()).isNull();
    }

    @Test
    @DisplayName("idempotent — second run touches zero documents and completes quickly")
    void isIdempotent() throws Exception {
        seedRaw("backfill-owner-idem-1", "Title", Visibility.PRIVATE, null);
        seedRaw("backfill-owner-idem-2", "Title", Visibility.PRIVATE, null);

        // First run populates.
        runner.run();
        // Capture the count of "backfilled N" log lines after the first run.
        long backfillLogsAfterFirstRun = logAppender.list.stream()
                .filter(e -> e.getFormattedMessage().contains("backfilled"))
                .count();

        // Second run: expect no documents to need updating.
        long start = System.currentTimeMillis();
        runner.run();
        long duration = System.currentTimeMillis() - start;

        // Per Security findings F-6 pin 2: idempotent second run completes
        // within a small time bound.
        assertThat(duration)
                .as("second backfill run must complete in <1s — guards against silent non-idempotency")
                .isLessThan(1000L);

        // The second run should NOT log "backfilled N" with N>0 — it should
        // log "no wishlists need backfill" instead.
        long backfillLogsAfterSecondRun = logAppender.list.stream()
                .filter(e -> e.getFormattedMessage().contains("backfilled "))
                .count();
        assertThat(backfillLogsAfterSecondRun)
                .as("second run must not emit a 'backfilled N' line")
                .isEqualTo(backfillLogsAfterFirstRun);
    }

    @Test
    @DisplayName("log line carries count; does NOT carry any code value (Security F-5 / F-6)")
    void logLineDoesNotContainCodes() throws Exception {
        Wishlist seeded = seedRaw("backfill-owner-log", "Title", Visibility.PRIVATE, null);

        runner.run();

        Wishlist after = wishlistRepository.findById(seeded.getId()).orElseThrow();
        String code = after.getAccessCode();
        assertThat(code).isNotNull();

        // No log line emitted by the runner may contain the freshly-generated
        // code value.
        for (ILoggingEvent event : logAppender.list) {
            assertThat(event.getFormattedMessage())
                    .as("backfill log line must not contain the generated code: %s", event.getFormattedMessage())
                    .doesNotContain(code);
        }
    }

    // --- helper --------------------------------------------------------

    /**
     * Seed a Wishlist directly via the repository, bypassing the service so
     * we can construct legacy null-accessCode rows the migration is designed
     * to fix.
     */
    private Wishlist seedRaw(String ownerUserId, String title, Visibility visibility, String accessCode) {
        Wishlist wishlist = Wishlist.builder()
                .ownerUserId(ownerUserId)
                .title(title)
                .visibility(visibility)
                .accessCode(accessCode)
                .build();
        return wishlistRepository.save(wishlist);
    }
}
