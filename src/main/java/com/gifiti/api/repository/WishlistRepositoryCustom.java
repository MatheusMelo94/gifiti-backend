package com.gifiti.api.repository;

/**
 * Custom repository fragment for {@link WishlistRepository} — operations that
 * cannot be expressed via Spring Data MongoDB's derived-query naming or
 * {@code @Query} annotation surface.
 *
 * <p>Feature 008 / T13: hosts {@link #updateAccessCodeIfNull} to provide the
 * atomic compare-and-set semantics required by Security findings F-6 pin 3 —
 * a race-safe write that only mutates when {@code accessCode} is still null
 * at the moment of the update.
 */
public interface WishlistRepositoryCustom {

    /**
     * Atomically set {@code accessCode} to the given value ONLY IF the
     * current stored value is {@code null} (Security findings F-6 pin 3).
     *
     * <p>The Mongo filter is {@code {_id: id, accessCode: null OR missing}};
     * the update is {@code {$set: {accessCode: newCode}}}. If a concurrent
     * writer (e.g. lazy-fallback inside the service, or a sibling instance
     * during a hypothetical multi-instance deploy) has already populated the
     * field, the update no-ops and returns 0.
     *
     * <p>Single-instance deploys still benefit: the atomicity guards against
     * the race window where the migration's read and write are separated by
     * the access-code generation step.
     *
     * @param id      the wishlist's MongoDB id
     * @param newCode the freshly-generated 4-digit access code
     * @return the number of documents modified (0 or 1)
     */
    long updateAccessCodeIfNull(String id, String newCode);
}
