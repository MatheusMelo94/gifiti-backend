# ADR 0007 — PostHog Product Analytics Integration

- **Status:** Accepted
- **Date:** 2026-05-06
- **Author:** Backend Architect
- **Deciders:** User (project owner / sole architectural authority)
- **Supersedes:** none
- **Superseded by:** none
- **Source plan:** `specs/007-posthog-integration/plan.md` (v2, 2026-05-02; 2026-05-06 addenda)
- **Related security findings:** `specs/007-posthog-integration/security-findings.md` F-1…F-8
- **Code Reviewer findings ratified here:** 0001 (Finding 0001 — `referrerWishlistId` shareableId), 0002 (`item_position` indexing), 0003 (`PostHogContext` removal), 0004 (`wishlist_returned` dedupe), 0006 (this ADR's existence)

---

## 1. Context

Feature 007 introduces server-side product analytics via PostHog Cloud. The cofounder's authoritative 7-event taxonomy (plan §1) gates definition-of-done on event correctness, anonymous→authenticated identity stitching, and two PostHog-UI-built dashboards (funnel + retention cohort).

PostHog is a new third-party processor in a Brazilian-user product, which triggers two convention bars at once:

- `architecture-conventions.md § Stack Baseline` — new external technology dependency requires an ADR.
- LGPD applies to Brazilian users (features 005, 006). Lawful basis, DPA, and PII boundary are non-negotiable preconditions for production traffic.

This ADR ratifies the nine plan decisions (A–I), the SDK-substitution that occurred during implementation, and three Code Reviewer findings (0002, 0003, 0004). It is the single durable record; `specs/` is gitignored, so the plan is not under version control.

---

## 2. Decisions

### Decision A — Hosting: PostHog Cloud (EU region)

- **Context.** Self-hosting PostHog adds Render compute + Postgres + ClickHouse-side patch discipline that MVP scale does not justify. Region affects LGPD adequacy posture.
- **Decision.** Use PostHog Cloud at `https://eu.i.posthog.com`. EU region simplifies LGPD adequacy: PostHog's standard DPA covers EU↔Brazil transfer; PostHog's standard sub-processor list applies.
- **Consequences.** Zero ops burden; vendor lock-in mitigated by export/import availability. **LGPD lawful basis required** — see Decision C and security-findings F-1 (DPA execution, lawful basis selection).

### Decision B — Instrumentation surface: per-event split

- **Context.** The 7-event taxonomy mixes events with authoritative server state (creation, persistence-driven counts) and events tied to client UX (referrer, share-channel selection).
- **Decision.** Per-event classification (plan §4): five events backend-emit (`wishlist_created`, `item_added`, `item_reserved`, `signup_completed`, `wishlist_returned`); two events frontend-emit (`wishlist_viewed`, `wishlist_shared`).
- **Consequences.** Backend owns reliability for the events that depend on persistence success; frontend owns the events that need DOM-level context. The split is documented in `docs/posthog-backend-integration.md` so frontend Engineer cannot accidentally double-emit.

### Decision C — Identity model: frontend-generated `distinctId`; backend uses `userId` for authenticated server events

- **Context.** PostHog's anon→authenticated merge is a frontend-owned `posthog.identify(userId, { $anon_distinct_id })` flow. Backend must not invent identities.
- **Decision.** Frontend generates the anonymous `distinctId` and owns the merge. Backend, when emitting server-side events for an authenticated user, uses `userId` as `distinctId` so server-side and client-side events stitch under the same identity. **The previously-planned `X-PostHog-DistinctId` header propagation channel is removed in this ADR (see Finding 0003 below).**
- **Consequences.** `distinctId = userId` (24-char hex Mongo ObjectId) is **pseudonymous personal data under LGPD Art. 5, XI** — covered by the PostHog DPA. Backend never sees anonymous-session `distinctId`s. Documented in the frontend contract (`docs/posthog-backend-integration.md`).

### Decision D — PII posture: minimum-PII default, allowlist enforced at the wrapper

- **Context.** `architecture-conventions.md § Logging` (no PII at INFO), feature 005 F-1 (anti-enumeration), feature 006 F-2 (owner-identity discipline) all set a high privacy bar. LGPD Art. 6, IX (data minimization) makes the bar a legal requirement.
- **Decision.** `PostHogClient` enforces (a) the 7-event-name allowlist and (b) the per-event property allowlist defined in plan §5.6 / `docs/posthog-backend-integration.md § 4`. Non-allowlisted event names and property keys are dropped with a `WARN`. Forbidden property keys: `email`, `displayName`, `name`, `firstName`, `lastName`, `phoneNumber`, `address`, `wishlistTitle`, `itemName`, `itemDescription`, `imageUrl`, `coverImageUrl`, `productLink`, `ipAddress`, `userAgent`.
- **Consequences.** Defense-in-depth — frontend mistakes (passing PII as a property) are dropped server-side. Add-a-property is a deliberate ADR/plan-level decision, not a one-line code change. **LGPD Art. 6, IX** satisfied at the contract layer.

### Decision E — Phase 1 event surface: full 7-event taxonomy

- **Context.** Cofounder's spec is prescriptive: all seven events live before definition-of-done. Plan v1 originally proposed `email_verified` only; cofounder spec replaces that.
- **Decision.** Backend implements all five backend-emit events in Phase 1. `email_verified` is dropped (was in v1; not in cofounder taxonomy).
- **Consequences.** No phasing in scope. Re-introducing `email_verified` later requires a separate plan and explicit cofounder approval (the wrapper's event-name allowlist makes scope-creep visible at the call site).

### Decision F — Sync vs. async: async batched flush

- **Context.** Hot paths (signup, wishlist create, item add, reserve) cannot tolerate analytics-vendor latency.
- **Decision.** Async batched flush via the SDK's standard cadence (`POSTHOG_FLUSH_INTERVAL_MS=5000`, `POSTHOG_FLUSH_AT=100`). All emission sites wrap in try/catch with `WARN`-level logging — PostHog errors never propagate to the request thread.
- **Consequences.** Eventual loss on crash is acceptable for analytics; these are not financial events. **LGPD note:** async emission does not change the lawful-basis analysis — the data leaves the process the same way either way.

### Decision G — Environment separation: one PostHog project per environment, dev no-op

- **Context.** Mirrors existing env-var-driven config (Resend, R2, MongoDB Atlas) per `architecture-conventions.md § Configuration & Secrets`.
- **Decision.** Three PostHog projects: `prod`, `staging`, `local`. `POSTHOG_ENABLED=false` in the local profile makes `PostHogClient` a no-op stub — no events leave dev machines. Staging/prod fail-fast if `POSTHOG_ENABLED=true` but `POSTHOG_API_KEY` is missing.
- **Consequences.** Local development never touches PostHog. Per-environment key rotation is independent. `CLAUDE.md § Production Security Checklist` lists the rotation cadence.

### Decision H — `signup_trigger` propagation: optional DTO fields on `RegisterRequest`

- **Context.** `signup_trigger` is the single most strategic property in the taxonomy (cofounder emphasis, plan §1) — it captures *why* people sign up and cannot be reconstructed later. Frontend-only emission sacrifices the most important event to a network partition. Header-based propagation has equivalent validation cost without the self-documenting benefit of a typed DTO field.
- **Decision.** `RegisterRequest` carries two **optional** fields:
  - `signupTrigger`: enum (`CREATED_WISHLIST` | `RESERVED_ITEM` | `DIRECT`) — defaults to `DIRECT` server-side when absent.
  - `referrerWishlistId`: String — accepts the wishlist **`shareableId` (21-char NanoID, alphabet `[A-Za-z0-9_-]`)**, validated via `@Pattern(regexp = "^[A-Za-z0-9_-]{21}$|^$")`. **Updated 2026-05-06 per Code Reviewer Finding 0001.** Original v2 plan accepted Mongo `_id`; the frontend has `shareableId` in hand from the share-link URL with no extra fetch, the analytic purpose (share-attribution) keys naturally to `shareableId`, and the shareableId-vs-`_id` distinction matches the rest of the public-wishlist surface area (feature 006).
- **Consequences.** No breaking change for existing signup callers. The Engineer's i18n key was renamed `validation.shared.uuid.invalid` → `validation.shared.wishlistId.invalid` (sole consumer was `RegisterRequest`). **LGPD note:** `signup_trigger` + `referrer_wishlist_id` together form a user-journey identifier — security-findings.md flags the join-risk; Architect's lean is plain (un-hashed) under consent basis is acceptable for the MVP analytic value, Security Engineer made the call.

### Decision I — Anonymous→authenticated stitching: frontend-owned

- **Context.** "Test it in incognito" (cofounder definition-of-done item #2) is a frontend QA gate. Backend never sees anonymous-session `distinctId`s and shouldn't.
- **Decision.** After successful signup, frontend calls `posthog.identify(userId, { $anon_distinct_id: anonId })` where `anonId` is the pre-login distinctId PostHog assigned to the anonymous session. Backend's only contractual obligation: server-side events for authenticated users use `userId` as `distinctId`. Backend does NOT need the anonymous-session distinctId for the `signup_completed` emission — the merge is owned by the frontend.
- **Consequences.** Backend has zero responsibility for anonymous identity material. Documented in `docs/posthog-backend-integration.md`. **LGPD note:** the merge happens client-side under whatever lawful basis governs the frontend's PostHog SDK init (consent banner per OQ-2); the backend's pseudonymous `userId` emission is a separate processing operation under PostHog DPA.

---

## 3. SDK substitution — `com.posthog:posthog-server:2.5.0`

- **Context.** Plan v2 §5.1 referenced `com.posthog.java:posthog` — that artifact was archived 2026-04-17. The Backend Engineer substituted to `com.posthog:posthog-server:2.5.0`, the official replacement (verified at pom.xml:144-151). This was an upstream-forced substitution, not a discretionary choice.
- **Decision.** Adopt `com.posthog:posthog-server:2.5.0` as the canonical SDK. Pinned version; track upstream releases on the standard dependency-update cadence.
- **Consequences.** No semantic change to plan A–I. The wrapper (`PostHogClient`) hides the SDK from services; future SDK swaps are wrapper-internal. OWASP `dependency-check-maven` (pom.xml:206-213) covers CVE scanning at the new artifact coordinates. CLAUDE.md § Production Security Checklist already references `POSTHOG_API_KEY` rotation discipline.

---

## 4. Code Reviewer ratifications

### Finding 0001 — `referrerWishlistId` accepts shareableId (NanoID), not Mongo `_id`

- **Status.** Ratified 2026-05-06. Folded into Decision H above.
- **Code state.** `RegisterRequest` `@Pattern` already updated; i18n key already renamed; integration doc already shows the NanoID example (`docs/posthog-backend-integration.md:63`). No further work required.

### Finding 0002 — `item_position` indexing: 0-indexed (ratified)

- **Context.** Plan §4 row 3 + §9 T7 acceptance criterion say "1-indexed". Production code (`WishlistItemService.java:58-79`) emits 0-indexed (the natural list-index semantic). Cofounder's authoritative taxonomy doesn't specify either way; analytic value is identical. Code Reviewer offered both options without prejudice.
- **Decision.** **Ratify 0-indexed.** The plan text drifts to match the code, not the other way around.
- **Reasoning.** (1) The code-truthful semantic — `position = list.size()` before insert — is the cheaper invariant to defend in tests and to reason about under retries. (2) The integration doc already documents "position 0" for the first item (`docs/posthog-backend-integration.md:37`), so the contract surface is already 0-indexed. (3) The "1-indexed reads more naturally on dashboards" argument is real but marginal: PostHog cohort filters use `>=`/`<` thresholds; label-rendering can be done in the dashboard layer if a non-engineer ever cares. (4) Flipping would introduce a wire-format change to a property already being emitted, with no analytic benefit. (5) Cost of ratifying: one plan-addendum line + one sentence in the integration doc. Code stays as-is.
- **Consequences.** Plan §4 row 3 and plan §9 T7 acceptance criterion are amended via the 2026-05-06 plan addendum (local-only history; this ADR is the durable record). `docs/posthog-backend-integration.md § 1` gets a single explicit line: "`item_position` is 0-indexed: the first item added to a wishlist emits `item_position=0`." No code change; no test change.

### Finding 0003 — `PostHogContext` and `PostHogDistinctIdFilter` removed (ratified, deviation from plan §5.4)

- **Context.** All five backend emissions use authenticated `userId` directly as `distinctId`. The filter validates and stashes `X-PostHog-DistinctId` in a thread-local; **no service ever calls `PostHogContext.currentDistinctId()`** (verified at HEAD `77521b2`). The infrastructure works but has no consumer.
- **Decision.** **Delete `PostHogContext` and `PostHogDistinctIdFilter`. Remove the `X-PostHog-DistinctId` header from the frontend contract.** This is a documented deviation from plan v2 §5.4 — ratified here.
- **Reasoning.** YAGNI. (1) All five current backend events run authenticated; `userId` is always available. (2) Decision I (frontend-owned `posthog.identify(userId, { $anon_distinct_id })`) is the canonical anon→authenticated merge mechanism — the header was a backup channel for a future case (server-emitted events without an authenticated `userId`) that does not exist in scope. (3) Dead infrastructure has maintenance cost: two unit tests, two integration tests, security-filter-chain ordering, and a documented header in the frontend contract that misleads about how identity actually flows. Deleting reduces surface area. (4) If the future need arises (e.g., a server-emitted event before authentication), wiring back in is roughly 30 minutes — the filter pattern is well-established.
- **Consequences.** Files to delete: `src/main/java/com/gifiti/api/analytics/PostHogContext.java`, `src/main/java/com/gifiti/api/analytics/PostHogDistinctIdFilter.java`, `src/test/java/com/gifiti/api/analytics/PostHogDistinctIdFilterTest.java`, `src/test/java/com/gifiti/api/integration/PostHogDistinctIdFilterIntegrationTest.java`. Filter-chain ordering: `CorrelationIdFilter` is unaffected (it ran at `HIGHEST_PRECEDENCE`; the deleted filter ran at `HIGHEST_PRECEDENCE + 100`). Frontend contract update: `docs/posthog-backend-integration.md § 2` item 5 ("Send the `X-PostHog-DistinctId` header") removed; replaced with a single sentence stating that backend uses `userId` for all authenticated server-emit events and the header is not consulted. **LGPD note:** removing the header narrows the data the backend ingests — strictly privacy-positive.

### Finding 0004 — `wishlist_returned` dedupe: Caffeine cache, 24h TTL, UTC day bucket (ratified)

- **Context.** `WishlistService.java:151-171` emits `wishlist_returned` unconditionally on every owner `findById` past the threshold. The Engineer's code comment punts dedupe to PostHog; PostHog's standard ingestion does NOT dedupe identical events with different timestamps. Plan §9 T9 acceptance criterion explicitly requires "Idempotent guard so the same return doesn't emit twice per session". The acceptance criterion is unmet at HEAD `77521b2`.
- **Decision.** **Caffeine cache, key `(userId, wishlistId, utcDayBucket)`, 24-hour TTL.** Day bucket is computed in UTC, not server-local time, to avoid timezone-edge double-emits. On every `emitWishlistReturnedIfThresholdMet` invocation that passes the threshold check: build the key, do a `getIfPresent` — if present, skip emission; if absent, `put(key, true)` and emit.
- **Reasoning.** (1) Caffeine is already a pom.xml dependency at v3.1.8 (pom.xml:131-136) — no new dependency, no schema migration. (2) Per-(user, wishlist, UTC-day) matches the analytic intent: track returns to the wishlist, not refresh-button-mashing. A user reopening their wishlist 50 times in one day produces one event; reopening the next day produces one more. (3) 24-hour TTL bounds memory pressure; key cardinality is `users × wishlists-per-user × days` which is comfortable for in-memory cache at MVP scale. (4) No Mongo write per emission decision — the schema-write alternative (a `lastReturnedEmittedAt` field on the `Wishlist` entity) is multi-instance-correct but adds I/O on a hot read path. (5) Multi-instance over-emit risk acknowledged: Render is currently single-dyno, so per-dyno cache is functionally per-instance correct; **when horizontal scale forces a Redis migration, this cache moves with `RateLimitFilter`'s Caffeine cache to Redis (both the rate-limiter and the dedupe cache share the same horizontal-scale precondition).** Tracked for revisit in `docs/posthog-followups.md`.
- **Consequences.** Engineer adds a `WishlistReturnedDedupeCache` (or equivalent — placement at Engineer's discretion within `service/analytics/` or `service/wishlist/`). Cache configured: `Caffeine.newBuilder().expireAfterWrite(Duration.ofHours(24)).maximumSize(<reasonable cap>)`. Day bucket computed via `LocalDate.now(ZoneOffset.UTC)` or `Instant.now().truncatedTo(ChronoUnit.DAYS)` — Engineer choice. Tests: (a) two emissions in the same UTC day with same `(userId, wishlistId)` produce one PostHog `capture` call; (b) emissions on different UTC days produce two; (c) emissions for different `(userId, wishlistId)` pairs are independent.

---

## 5. LGPD / privacy summary

| Decision | LGPD lawful-basis citation | Notes |
|---|---|---|
| A (EU host) | Art. 33 (international transfer) | EU↔Brazil transfer covered by PostHog DPA. |
| C (identity model) | Art. 5, XI (pseudonymization) | `distinctId = userId` (Mongo ObjectId) is pseudonymous. |
| D (PII allowlist) | Art. 6, IX (data minimization) | Wrapper enforces; no email / displayName / titles / images leave the process. |
| F (async) | (no specific citation) | Async emission does not affect the lawful-basis analysis. |
| H (DTO field) | Art. 6, IX (data minimization); Art. 7 (lawful basis) | `referrer_wishlist_id` join-risk acknowledged in security-findings.md; plain emission accepted under consent basis at MVP scale. |
| I (anon stitching) | Art. 7 (consent basis, frontend-owned) | Backend has zero responsibility for anonymous identity material. |

DPA execution and lawful-basis selection (consent vs. legitimate interest) are tracked under security-findings.md F-1 and `CLAUDE.md § Production Security Checklist`.

---

## 6. Convention drift surfaced

These items remain user-approval-gated proposals (not promoted unilaterally):

1. **`security-build-vs-buy.md`** — add "Product analytics — BUY default — PostHog, Amplitude, Mixpanel — build justified: never (privacy and ETL infra cost dominate)."
2. **`architecture-conventions.md § Third-Party Processors`** — codify the allowlist-at-wrapper pattern (Decision D mechanism) as a standing rule for future processor integrations (Resend, R2, Google OAuth, MongoDB Atlas, PostHog all qualify today).
3. **`architecture-conventions.md § DTO Extension for Downstream Analytics`** — codify Decision H as a pattern: "DTO fields added solely for downstream analytics/attribution are marked optional, default to a benign value server-side, and are documented in the per-feature contract doc."

---

## 7. Sign-off

- Status: **Accepted**
- Date: **2026-05-06**
- Approver: User (project owner)
- Author: Backend Architect
- Immutable. Future changes happen via a superseding ADR.
