# ADR 0008 — Private Wishlist Access Codes

- **Status:** Accepted
- **Date:** 2026-05-17
- **Author:** Backend Architect
- **Deciders:** User (project owner / sole architectural authority)
- **Supersedes:** none
- **Superseded by:** none
- **Source plan:** `specs/008-private-wishlist-access-codes/plan.md` (v1, 2026-05-17)
- **Frontend source doc:** "Backend To-Do: Frontend Changes Shipping May 2026" (2026-05-17), feature §1
- **Related ADRs:** 0007 (PostHog taxonomy — relevant to Decision I); precedent for the ratification + decision-per-section structure used here

---

## 1. Context

Feature 008 lets owners share PRIVATE wishlists by giving recipients a link **plus** a numeric access code. The frontend has shipped the gate UI on production `gifiti.app`. Owners of PRIVATE wishlists are currently unable to share privately end-to-end: the share modal expects an `accessCode` the backend doesn't emit, and recipients visiting a PRIVATE share-link hit the feature-006 anti-enumeration 404 instead of the new gate.

This ADR ratifies eleven decisions (A–K), one privacy-posture inversion (PRIVATE: 404 → 403), and the rollout sequencing. It is the durable record; the plan at `specs/` is local-only.

### Existing constraints
- Feature 006 (anonymous public wishlist viewing) collapses PRIVATE → 404 to prevent shareableId enumeration. Feature 008 deliberately inverts this for the gate UX; Security Engineer concurrence is being requested in parallel.
- Feature 007 (PostHog analytics) fixed the event taxonomy at 7 events (`PostHogClient` enforces allowlist). Feature 008 does NOT expand this without cofounder approval through Product Strategist (Decision I).
- LGPD applies (Brazilian users). The access code is a low-sensitivity secret (4 digits) but it's still a secret; logging discipline, PII allowlist, and at-rest protection apply.

---

## 2. Decisions

### Decision A — Code expiry policy: never expire
- **Context.** TTL-based expiry would add a new background-job pattern to the codebase (Spring `@Scheduled`). No product evidence demands it. Rotation is already a manual control.
- **Decision.** Codes never expire automatically. Owners rotate manually via `POST /api/v1/wishlists/{id}/rotate-access-code`.
- **Consequences.** Simpler ops; no scheduled-job introduction. Owners who want forced cycling cycle manually. If/when product signal demands TTL, supersede this ADR.

### Decision B — Per-wishlist (not per-recipient) codes
- **Context.** Per-recipient codes require a `wishlist_invite` collection, invite-generation UI, per-recipient telemetry — multi-week effort no MVP product signal demands.
- **Decision.** One code per wishlist, shared across all recipients (matches frontend's sessionStorage model).
- **Consequences.** Simpler data model. Cannot revoke a code for one recipient without rotating for all. Acceptable for MVP. Revisit when audit/UX needs surface.

### Decision C — Audit log of successful unlocks: SLF4J INFO only
- **Context.** No owner-facing "recent unlocks" UI exists. LGPD pushes against retaining IPs in a queryable collection without a clear lawful basis and retention policy.
- **Decision.** Log successful and failed unlock attempts at SLF4J INFO with `SECURITY_EVENT:` prefix, carrying `correlationId`, `shareableId`, `maskedIp` (last octet masked, matching `RateLimitFilter.maskIp`). NO Mongo collection. NO PostHog event (see Decision I).
- **Consequences.** Sufficient for incident-response forensics. Matches feature 005/006 logging discipline. Revisit when the frontend adds owner-facing unlock-activity UI.

### Decision D — Upload virus/content scanning: deferred (with revisit triggers)
- **Context.** Frontend doc §1.7 explicitly lists this as out of scope. Architect concurs — no incident signal, no regulatory pressure, no scale signal.
- **Decision.** Defer. **Revisit triggers** recorded here: (1) first incident involving uploaded content; (2) regulated B2B customer onboarding (SOC2/ISO); (3) image upload volume > 100/day per user.
- **Consequences.** Documented deferral; not an implicit gap. Architect's directional lean if/when triggered: hosted scanner (Cloudflare Stream-class) before self-hosted ClamAV, mirroring the build-vs-buy lean in ADR 0007.

### Decision E — Code storage: plaintext at the application layer, encrypted-at-rest via MongoDB Atlas
- **Context.** The owner's share modal renders the current code every time. Hashing means "show once at generation, then never again," which forces rotation to recover visibility — invalidating the link for every existing recipient. UX vs defense-in-depth trade-off.
- **Decision.** Store the 4-digit code as a plaintext string field on the `Wishlist` document. Rely on (a) MongoDB Atlas encryption-at-rest for storage protection; (b) the `PostHogClient` PII allowlist (ADR 0007 Decision D) to ensure the field never leaks into analytics; (c) the SLF4J logging discipline (Decision C) to ensure it never appears in logs.
- **Consequences.** Owner UX preserved. DB-breach scenario: an attacker who already has both the database AND the shareableId can read the code; this is the smaller of the two concerns at that point. Primary protection against guessing is the rate limit (Decision G), not at-rest secrecy. **Security Engineer concurrence requested in parallel — this decision is reversible via a superseding ADR with low blast radius (the storage layer change is one field migration; the UX trade-off is the harder discussion).**

### Decision F — Code generation entropy: 4-digit numeric, leading zeros allowed
- **Context.** Frontend gate UI is built for 4 digits. Changing format requires frontend re-work. Threat model: 10,000 codes + Decision G's rate limit yields infeasible brute-force time from a single IP.
- **Decision.** `String.format("%04d", secureRandom.nextInt(10000))`. No exclusion lists (no banned sequences). `SecureRandom` instance, not `Math.random` or unsalted UUIDs.
- **Consequences.** Codes can be "0000", "1234", "1111" — defense is rate-limit + randomness, not vocabulary filtering. If a distributed (botnet) attacker becomes a concern, supersede with 6-digit format (F2). Frontend would need re-wire; acknowledged.

### Decision G — Rate-limit: reuse `RateLimitConfig` infrastructure, add a new compound-key bucket (IP + shareableId)
- **Context.** Existing `RateLimitFilter` is per-IP, path-keyed at the filter level. The new requirement is per-(IP, shareableId) and the consumption point is the service layer (rate-limit triggers on FAILED validation, which is a business-logic outcome).
- **Decision.** Add `accessCodeAttemptBuckets` Caffeine cache + `tryConsumeAccessCode(ip, shareableId)` method to `RateLimitConfig`. Bucket: 5 tokens, refill 5 / 10 min (`Bandwidth.classic(5, Refill.greedy(5, Duration.ofMinutes(10)))`). Eviction: `expireAfterAccess(Duration.ofMinutes(15))`. Max size 10,000 entries.
- **Architectural note.** The token-consume call lives in the service layer (inside `PublicWishlistService` on the failed-validation branch), NOT in the filter. This is a deliberate deviation from the filter-level rate-limit pattern in the rest of the codebase: pulling business logic into the filter chain (the filter would need to fetch the wishlist, compare codes, then conditionally consume) is the worse factoring. Successful validation invalidates the bucket entry (counter reset on success, per frontend spec).
- **Consequences.** New `RateLimitConfig` method; one new cache. `RateLimitFilter` unchanged. The hybrid (filter for IP-only buckets, service for compound buckets) creates a small dual-pattern surface — surfaced as a convention-drift candidate in the plan §11.

### Decision H — Constant-time comparison via `MessageDigest.isEqual`
- **Context.** For 4-digit codes the timing-attack signal is small but real; HTTPS termination and JVM behavior don't reliably hide it. Constant-time comparison is a 1-line change with no UX cost.
- **Decision.** Compare `stored.getBytes(UTF_8)` and `submitted.getBytes(UTF_8)` via `MessageDigest.isEqual(...)`. **Security Engineer concurrence requested in parallel — Architect recommends but does not unilaterally ratify timing-attack-resistance decisions per the task brief.**
- **Consequences.** Trivial code change. Defends against a class of attack proactively rather than reactively.

### Decision I — PostHog event emission for access-code interactions: none (taxonomy unchanged)
- **Context.** ADR 0007 Decision E ratified the cofounder's authoritative 7-event taxonomy. Re-introducing or adding events requires "a separate plan and explicit cofounder approval." `PostHogClient` enforces the event-name allowlist at the wrapper layer.
- **Decision.** No new PostHog events in feature 008. Backend Engineer must NOT add `posthog.capture(...)` calls in the access-code path. If/when Product Strategist secures cofounder approval for `wishlist_unlocked` (and optionally `wishlist_unlock_failed`), a separate plan increment wires it in and updates the `PostHogClient` allowlist.
- **Consequences.** Taxonomy stays at 7 events. SLF4J logging (Decision C) is sufficient for forensics in the meantime. The Product Strategist holds the cofounder-routing responsibility; surfaced in plan §9.

### Decision J — Backfill strategy: hybrid migration + lazy generation
- **Context.** Existing PRIVATE wishlists have no `accessCode`. Pure migration is fragile against race conditions during deploy; pure lazy generation may surface `accessCode: null` to the frontend (which expects a string for PRIVATE).
- **Decision.** Run a one-shot `CommandLineRunner`-based backfill at deploy time (`AccessCodeBackfillRunner`) that touches every PRIVATE wishlist where `accessCode` is null and sets a freshly-generated code. ADDITIONALLY, lazy-generate inside the service at any PRIVATE read-path that observes a null code (`WishlistService.findById` and equivalent). Belt-and-suspenders.
- **Consequences.** No race window. No tracking collection needed (operation is idempotent and bounded). Migration logs document count at INFO; does NOT log codes. Lazy fallback is a one-time per-wishlist operation.

### Decision K — Rollout sequencing: phased
- **Context.** Production state at decision-time: owners cannot share privately. Phase the rollout so each phase is Pareto-improving and rollback is bounded.
- **Decision.**
  - **Phase 1:** field + backfill + generate-on-create + visibility-transition logic. NO public-endpoint behavior change. Owners can see codes in their share modal and communicate out-of-band even before the gate works.
  - **Phase 2:** flip the public endpoint to 403-with-discriminator for PRIVATE. Rate-limit wired in. Header read and validated.
  - **Phase 3:** `POST /wishlists/{id}/rotate-access-code` endpoint.
  - Phases 1 + 2 MAY be combined into a single deploy if the Engineer prefers; phase 3 ships within 24h.
- **Consequences.** No phase makes any user "more broken" than they are today. Rollback is per-phase and non-destructive (phase 1 rollback `$unset`s the field; phase 2 rollback reverts to 404-on-PRIVATE; phase 3 rollback removes the endpoint).

---

## 3. Privacy-Posture Inversion (404 → 403): Acknowledged Deviation From Feature 006

Feature 006's `PublicWishlistService.findByShareableId` deliberately returns 404 for PRIVATE shareableIds — the comment reads "Return 404 (not 403) to avoid revealing that the wishlist exists." Feature 008 inverts this: PRIVATE shareableIds now return `403 ACCESS_CODE_REQUIRED`, which confirms existence.

**Architect's position.** The trade is acceptable for two reasons:
1. **Entropy makes enumeration infeasible regardless.** The shareableId is a 21-char NanoID over a 64-char alphabet (~125 bits). An attacker cannot meaningfully enumerate shareableIds whether the answer is 404 or 403.
2. **The gate UX requires the discriminator.** Without 403 + `ACCESS_CODE_REQUIRED`, the frontend cannot distinguish "wrong link" from "right link, need code" — and that distinction is the whole feature.

**Compensating control (optional).** An alternative posture: return 404 when NO `X-Wishlist-Access-Code` header is present, return 403 `INVALID_ACCESS_CODE` only when a WRONG header is present. This preserves "no info without a guess" anti-enumeration. The cost is a degraded gate UX — the frontend would need to send a "ping" header with a known-wrong value (e.g., `"0000"`) on first load to detect "needs code," which leaks slightly in a different way and is awkward.

**Decision.** Adopt the straightforward `403 ACCESS_CODE_REQUIRED` on no-header. Security Engineer to weigh in on whether the compensating control is worth its UX cost — flagged in plan §8.

---

## 4. ErrorResponse Contract Amendment (Pending User Reconciliation)

The frontend doc requires the `error` JSON field to carry a machine-readable discriminator (`"ACCESS_CODE_REQUIRED"` etc.). The existing `ErrorResponse.error` carries the HTTP reason phrase (`"Bad Request"`, `"Forbidden"`). These collide.

**Architect's recommended resolution.** Add a new field `errorCode` to `ErrorResponse`, populated only on the new exception types (`AccessCodeRequiredException`, `InvalidAccessCodeException`, `AccessCodeRateLimitedException`). The existing `error` field retains its HTTP-reason-phrase semantics for backward compatibility. The frontend narrows on `errorCode` for these specific gate cases.

**Alternative.** Repurpose `error` to carry the discriminator on these endpoints only. Worse: makes the field's semantics inconsistent across endpoints.

**User decision required** — flagged in plan §10. This ADR records the Architect's recommendation; the user reconciles with the frontend team before the Engineer codes phase 2.

---

## 5. LGPD / Privacy Summary

| Item | LGPD basis | Notes |
|---|---|---|
| `accessCode` storage (plaintext at app layer) | Art. 6, IX (data minimization) | 4-digit numeric; not personal data in isolation; combined with shareableId it's an access-control secret, not personal data. |
| Unlock event SLF4J logs (correlationId + maskedIp + shareableId) | Art. 7 (legitimate-interest, security operations) | No PII; masked IP is consistent with feature 005/006 logging. |
| No PostHog emission | Art. 6, IX (data minimization) | Decision I keeps the analytics surface unchanged. |
| 403-with-discriminator inversion | n/a (not personal data) | Privacy concern is anti-enumeration, not LGPD; addressed in §3. |
| Mongo unlock_events collection | n/a (ruled out per Decision C) | Avoids retention-policy and lawful-basis discussion entirely. |

---

## 6. Out of Scope (Deferred, with Revisit Triggers Recorded)

| Item | Why deferred | Revisit trigger |
|---|---|---|
| Per-recipient codes (Decision B) | Multi-week effort; no MVP signal | Owner-facing "who unlocked" UI request; audit-trail request |
| TTL-based code expiry (Decision A) | New background-job pattern; no signal | Security signal demanding forced rotation; compliance requirement |
| Audit-trail Mongo collection (Decision C2) | LGPD retention-policy overhead; no UI consumer | Owner-facing unlock-activity view in product roadmap |
| PostHog event for unlocks (Decision I) | Taxonomy is cofounder-authoritative | Product Strategist secures cofounder approval |
| Upload virus/content scanning (Decision D) | No incident, no regulatory pressure | Incident report; B2B SOC2/ISO customer; upload volume > 100/day/user |

---

## 7. Convention Drift Surfaced

Items the Architect surfaces for user approval to promote to standing convention:

1. **`architecture-conventions.md § API Contracts`** — codify `errorCode` (machine-readable discriminator) vs `error` (HTTP reason phrase) if the §4 reconciliation lands.
2. **`architecture-conventions.md § Rate Limiting`** (new section) — codify the split: filter-level for IP-only buckets; service-level for compound (IP + resource) buckets.
3. **`security-conventions.md § Anti-Enumeration vs Gate UX`** (new section) — document the feature-006-vs-008 trade-off as a precedent for future link-share + secondary-factor features.
4. **`architecture-conventions.md § Authentication & Authorization`** — codify "header-based shared-secret access" as a named pattern for share-link features.

These remain proposals. The user approves convention edits.

---

## 8. Open Questions Routed Forward

- **Security Engineer (plan §8):** 8 questions including privacy-posture inversion, storage decision, comparison method, rate-limit calibration, logging discipline, X-Forwarded-For trust, migration safety, PII-allowlist verification.
- **Product Strategist (plan §9):** conditional — only if Decision I should expand the taxonomy.
- **User / frontend team (plan §10):** 5 questions including the `error`-field contract collision, `UpdateWishlistRequest.accessCode` input semantics, phase-1+2 deploy combination, and 429 response shape.

These block Engineer dispatch on phase 2. Phase 1 (additive field + backfill) can proceed in parallel.

---

## 9. Sign-Off

- **Status:** Accepted
- **Date:** 2026-05-17
- **Approver:** User (project owner)
- **Author:** Backend Architect
- **Immutability:** Future changes happen via a superseding ADR. Decisions E and H are flagged for Security Engineer concurrence; if the Security Engineer counter-proposes, this ADR is amended in place (revision note appended, original text preserved per ADR 0007 precedent) OR superseded by ADR-0009, per the user's preference.
