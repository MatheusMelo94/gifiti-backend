package com.gifiti.api.service.migration;

import com.gifiti.api.model.Wishlist;
import com.gifiti.api.model.enums.Visibility;
import com.gifiti.api.repository.WishlistRepository;
import com.gifiti.api.util.AccessCodeGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * One-shot Spring Boot {@link CommandLineRunner} that backfills
 * {@code accessCode} on existing PRIVATE wishlists missing the field
 * (feature 008 / T13).
 *
 * <p>Per ADR 0008 § Decision J3 (hybrid migration + lazy generation):
 * <ul>
 *   <li>Runs at application startup.</li>
 *   <li>Filters {@code visibility == PRIVATE AND accessCode == null}.</li>
 *   <li>Generates a fresh 4-digit code via {@link AccessCodeGenerator}
 *       (ADR 0008 § Decision F).</li>
 *   <li>Atomically writes the code only when the precondition still holds —
 *       see {@link com.gifiti.api.repository.WishlistRepositoryCustom#updateAccessCodeIfNull}
 *       (Security findings F-6 pin 3).</li>
 *   <li>Idempotent — re-running on a fully-backfilled collection is a fast
 *       no-op (Security findings F-6 pin 2).</li>
 *   <li>Logs only the document count — NEVER the generated codes (Security
 *       findings F-5 / F-6).</li>
 * </ul>
 *
 * <p><b>Multi-instance deploy revisit trigger:</b> per Security findings F-6
 * pin 4, this runner is safe at single-instance Render topology because the
 * atomic compare-and-set degrades gracefully (the second writer's update
 * filter fails to match after the first writer commits). Moving to a
 * multi-instance topology REQUIRES adding a distributed lock; the trigger is
 * documented but not implemented here.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccessCodeBackfillRunner implements CommandLineRunner {

    private final WishlistRepository wishlistRepository;

    @Override
    public void run(String... args) {
        // Per Security findings F-6 pin 1: bounded query — at MVP scale
        // (estimated <100 PRIVATE wishlists in prod) a single fetch is fine.
        // If wishlist volume grows past O(10K), the runner should batch via
        // page-size cursor; documented as a revisit trigger.
        List<Wishlist> candidates = wishlistRepository
                .findByVisibilityAndAccessCodeIsNull(Visibility.PRIVATE);

        if (candidates.isEmpty()) {
            log.info("AccessCodeBackfillRunner: no wishlists need backfill");
            return;
        }

        int updated = 0;
        int skipped = 0;
        for (Wishlist w : candidates) {
            String newCode = AccessCodeGenerator.generate();
            // Per Security findings F-6 pin 3: atomic compare-and-set. If a
            // concurrent writer populated the field between our read and our
            // write, this update no-ops (modifiedCount == 0) and we count it
            // as "skipped" — no overwrite, no race, no log of the code value.
            long modified = wishlistRepository.updateAccessCodeIfNull(w.getId(), newCode);
            if (modified > 0) {
                updated++;
            } else {
                skipped++;
            }
        }

        // CRITICAL (Security findings F-5): the log line carries counts only.
        // It MUST NOT carry the generated codes.
        log.info("AccessCodeBackfillRunner: backfilled {} PRIVATE wishlists with access codes ({} skipped due to concurrent write)",
                updated, skipped);
    }
}
