# Anonymous Public Wishlist — Deferred Follow-ups

> **Context:** Tracker for items intentionally OUT OF SCOPE for the "anonymous shared wishlist viewing" feature (006), surfaced by the Backend Architect's plan and accepted as deferred.
>
> **Status as of 2026-05-04:** All three items below are not blocking the main feature ship. They become real work to scope and execute when triggers fire.
>
> **Do NOT delete this file when feature 006 ships.** It tracks debt the project knowingly took on; closing items belongs in commits that mitigate them.

---

## 1. Edge / multi-instance rate limiting on anonymous GETs

**Status correction (2026-05-04):** the prior version of this section claimed in-app rate limiting was deferred. **It is not** — `RateLimitFilter.java:94-100` already enforces 60 req/min/IP on `GET /api/v1/public/wishlists/*`, registered with `@Order(Ordered.HIGHEST_PRECEDENCE + 1)` so it runs before Spring Security. Per-IP per-instance limit IS in place from day one of feature 006. Verified by Security Engineer (F-7).

**Real risk that remains:** the rate limit is in-memory per Render dyno (Caffeine-backed `publicBuckets`). At MVP scale Render runs one instance, so 60 req/min/IP is the effective ceiling. **When the service scales horizontally, an attacker hitting different instances multiplies their effective rate to N×60 req/min**, where N is the dyno count.

**Mitigation candidates:**
- Cloudflare-edge rate limit (deploy-time config; no app code change). Single global counter regardless of dyno count.
- Redis-backed Bucket4j replacing the in-memory Caffeine cache. Centralizes rate-limit state across instances.
- Both (defense in depth).

**Revisit triggers:**
- Render service moves to multi-instance (Caffeine-local rate limit becomes ineffective; one of the above is mandatory).
- Anomalous traffic patterns from a single IP/AS — visible in Render Events / Cloudflare analytics.
- Public wishlist GET endpoint sustains >5,000 req/hr globally in production.
- A specific shareable-ID scraping incident is reported by a user.

**Owner role at trigger time:** Backend Architect (decide edge vs. Redis) → DevOps Engineer (Cloudflare side) OR Backend Engineer (Redis side).

---

## 2. Audit logging of anonymous access

**Risk:** today's `gifiti-backend` logs every authenticated request with `userId` + correlationId. Anonymous requests log only correlationId — no per-request identity. If a malicious actor scrapes a wishlist, post-incident forensics are limited to "someone hit the endpoint" with no way to correlate to upstream activity.

**Mitigation candidates:**
- Log per-request: `correlationId`, anonymized client IP (last octet redacted), `User-Agent`, `Referer`, response status. Keep retention ≤30 days per privacy standard.
- For specific high-value wishlist accesses, fire a structured event (separate logger) for security-team querying.

**Revisit triggers:**
- Real shareable-ID compromise incident.
- Compliance/audit requirement for anonymous access logging surfaces.
- Volume of anonymous access exceeds 1,000 req/day (forensic value increases).

**Owner role at trigger time:** Security Engineer → Backend Engineer.

---

## 3. R2 cover-image URLs leak `ownerUserId` via path segment

**Risk (Security finding F-3, HIGH, deferred per user decision 2026-05-04):** Cloudflare R2 public URLs for wishlist cover images follow the path `pub-abc.r2.dev/users/{ownerUserId}/wishlists/{wishlistId}/cover.jpg`. Anonymous viewers of a shared wishlist can parse the `coverImageUrl` field on `PublicWishlistResponse` and extract the owner's `userId`. The DTO's privacy contract (`// PRIVACY: No ownerUserId, no internal id, no timestamps`) is honored at the JSON-field level but undermined at the URL-content level.

**Why this exists:** the R2 path layout was set during feature 004 (image upload, March 2026) when only authenticated users saw `coverImageUrl`. Loosening auth in feature 006 widens the leak's audience to the open internet.

**Why deferred (not blocking feature 006):** the fix touches feature 004's upload pipeline (`ImageUploadService` and the R2 storage layer) — different code, different test surface than feature 006. Bundling would 3x the PR size with mostly-orthogonal work. The blast radius of the leak is moderate, not catastrophic — `userId` alone is a 24-char hex string, not standalone PII. Correlation attacks would require combining this with another data source, which doesn't exist in the current API surface.

**Mitigation candidates:**
- **Preferred:** R2 path layout switches to opaque keys (`images/{nanoId}/cover.jpg`). New uploads use the new layout immediately; existing uploads migrate lazily on next-edit. Backend Architect decides the path-format change; Backend Engineer executes.
- **Quick fix at API layer:** rewrite the URL to a signed, short-lived URL via Cloudflare Workers or app-side proxying. Adds latency and infra complexity. Not preferred.
- **Lower-effort partial:** swap `userId` for `wishlistId` in the path (still leaks an internal ID but less identifying). Acceptable interim.

**Revisit triggers:**
- First reported correlation attack OR observable exploitation attempt.
- Audit logging on anonymous access (item #2 above) is implemented and reveals scraping patterns suggestive of `userId` collection.
- The API surface grows to include any second public-readable endpoint that accepts or echoes `userId` (correlation becomes meaningful).
- LGPD/GDPR DPA review flags the path-content as a privacy concern.
- One-year anniversary of feature 006 ship (2027-05-04) — review whether the threat model is still acceptable.

**Acceptance note (2026-05-04):** user explicitly accepted this risk during the feature 006 pre-impl Security review (Security Engineer findings F-3 OPEN, recommendation Option C/A). The acceptance is documented; the trigger list above is the contract for revisiting.

**Owner role at trigger time:** Backend Architect (path-layout decision) → Backend Engineer (upload pipeline change + lazy migration).

---

## 4. CDN / ETag caching for anonymous public wishlist GETs

**Risk:** every anonymous GET hits Render → Spring Boot → MongoDB, even though wishlist data changes infrequently. Server resources scale linearly with anonymous traffic. CDN caching with proper invalidation could absorb the bulk of read traffic at the edge.

**Mitigation candidates:**
- ETag header + `Cache-Control: public, max-age=60` on `GET /api/v1/public/wishlists/{shareableId}`.
- Cloudflare cache rules in front of Render.
- Wishlist mutation paths (PUT, DELETE on the owner's `/wishlists/...`) emit cache-purge events.

**Revisit triggers:**
- Render bandwidth/compute costs grow disproportionate to revenue.
- Public wishlist GET p95 latency exceeds 500ms sustained.
- Wishlist viral moment (e.g., a shared list goes viral on social media) overwhelms current capacity.

**Owner role at trigger time:** Backend Architect → DevOps Engineer.

---

## How this list is updated

When a trigger fires:
1. The owning role drafts a small `/plan` for the mitigation.
2. User approves.
3. Backend Engineer implements with TDD.
4. After merge, the relevant section above is **deleted** from this file (with a commit citing what closed it).

When all four are addressed, this file is deleted in a single commit titled `chore(security): close all deferred follow-ups for feature 006`.

---

**Last reviewed:** 2026-05-04 (revised after Security Engineer pre-impl review of feature 006 — F-4 documentation correction on rate limiting; F-3 added as new deferred item with revisit triggers per user acceptance)
