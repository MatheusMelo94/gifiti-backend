# PostHog Backend Integration — Frontend Contract

**Audience:** Frontend Engineer (or user wearing that hat).
**Source:** Feature 007 plan (`specs/007-posthog-integration/plan.md`) + Security findings.
**Status:** Backend Phase 1 implementation merged; production enablement gated on the split-gate prerequisites in §5.

This document defines the contract between the Gifiti backend and the frontend for PostHog product analytics. Read it end-to-end before wiring `posthog-js` on the client side.

---

## 1. What backend does

The backend emits **5 server-side events** through a single wrapper bean (`com.gifiti.api.analytics.PostHogClient`). All emission flows through this wrapper; services never touch the SDK directly.

| # | Event | Emit site (service) | Properties |
|---|---|---|---|
| 2 | `wishlist_created` | `WishlistService.create` | `user_id`, `occasion_type`, `item_count_at_creation` |
| 3 | `item_added` | `WishlistItemService.create` | `wishlist_id`, `user_id`, `item_position` |
| 5 | `item_reserved` | `ReservationService.reserve` | `wishlist_id`, `item_id`, `reserver_user_id` |
| 6 | `signup_completed` | `AuthService.register` | `signup_trigger`, `referrer_wishlist_id` |
| 7 | `wishlist_returned` | `WishlistService.findById` | `user_id`, `days_since_creation` |

Server-side guarantees:

- Events fire **after** the underlying domain mutation is persisted, so the event reflects authoritative state (no "ghost" events from rolled-back transactions).
- The wrapper enforces a strict event-name allowlist and a per-event property allowlist (see §4). Non-allowlisted property keys are dropped with a `WARN` log; non-allowlisted event names are suppressed entirely.
- Emission is fail-open: any PostHog SDK error is logged at `WARN` and never propagates to the user-facing request. Signup, wishlist creation, item addition, and reservation must always succeed even if PostHog is unreachable.
- The wrapper dispatches `capture()` to a daemon executor; the calling thread never blocks for SDK call latency.
- Every server-side event carries the global pseudo-property `$server_initiated=true` so PostHog dashboards can filter server vs. client events.

---

## 2. What frontend does

The frontend owns **2 events** and the identity-stitching dance.

| # | Event | Owner | Properties |
|---|---|---|---|
| 1 | `wishlist_viewed` | Frontend | `wishlist_id`, `viewer_logged_in`, `referrer`, `item_count` |
| 4 | `wishlist_shared` | Frontend | `wishlist_id`, `share_channel` (`whatsapp` / `copy` / `email` / `other`) |

Frontend responsibilities:

1. **Initialize `posthog-js`** with the EU host (`https://eu.i.posthog.com`) — region must match the backend's `POSTHOG_HOST`.
2. **Emit `wishlist_viewed`** from the wishlist view page. `referrer` from `document.referrer`; `item_count` from the rendered response. `viewer_logged_in` from the session state (true iff a JWT cookie is present and valid).
3. **Emit `wishlist_shared`** from share-button click handlers. `share_channel` is one of the four documented enum values; do not invent new ones without an architect-approved taxonomy update.
4. **Identity stitching after signup.** After a successful signup response, call `posthog.identify(userId, { $anon_distinct_id: anonId })` where `anonId` is the pre-login distinctId PostHog assigned to the anonymous session. Test in incognito (definition-of-done item #2 from the cofounder spec).
5. **Send the `X-PostHog-DistinctId` header** on every authenticated request. Backend reads it via `PostHogDistinctIdFilter` (alphanumeric + `-` and `_`, max 64 chars). Anything else is dropped with a `WARN`.

---

## 3. The `signup_trigger` propagation (Decision H)

The `signup_completed` event's `signup_trigger` property is the most strategic property in the entire 7-event taxonomy — it captures *why* people sign up. Backend-side emission via DTO field guarantees capture even if the frontend's post-signup analytics call fails; the value cannot be reconstructed later.

The backend exposes two **optional** fields on `RegisterRequest`:

```json
{
  "email": "user@example.test",
  "password": "...",
  "signupTrigger": "CREATED_WISHLIST",
  "referrerWishlistId": "123e4567-e89b-12d3-a456-426614174000"
}
```

Field semantics:

- `signupTrigger`: enum, one of `CREATED_WISHLIST`, `RESERVED_ITEM`, `DIRECT`. Defaults to `DIRECT` server-side when omitted. Frontend MUST set the value the user's flow actually started with.
- `referrerWishlistId`: optional UUID-shaped string. Only meaningful when `signupTrigger` is `CREATED_WISHLIST` or `RESERVED_ITEM` and the user came from a specific public wishlist. Empty string accepted as null.

When to send each value:

| Frontend flow | `signupTrigger` | `referrerWishlistId` |
|---|---|---|
| User clicks "Create my own wishlist" CTA on a friend's public wishlist | `CREATED_WISHLIST` | the friend's wishlist ID |
| User clicks "Reserve this item" on a public wishlist while not signed in | `RESERVED_ITEM` | the wishlist that owns the item |
| Top-of-funnel signup (no specific public-wishlist context) | `DIRECT` | omit / `null` |
| Existing legacy signup paths that have not yet been wired | omit / `null` | omit / `null` (defaults to `DIRECT`) |

The backend never invents the trigger — if the frontend cannot infer it, the backend records `DIRECT`.

---

## 4. The PII allowlist contract

The backend's allowlist is **canonical**. Frontend must never add PII as event properties — anything caller-supplied that ends up in the property map is filtered out at the wrapper (with a `WARN` log), but treat the wrapper as a defense-in-depth backstop, not a license to be sloppy.

**Allowed event properties** (per-event):

| Event | Allowed property keys |
|---|---|
| `wishlist_viewed` | `wishlist_id`, `viewer_logged_in`, `referrer`, `item_count` |
| `wishlist_created` | `user_id`, `occasion_type`, `item_count_at_creation` |
| `item_added` | `wishlist_id`, `user_id`, `item_position` |
| `wishlist_shared` | `wishlist_id`, `share_channel` |
| `item_reserved` | `wishlist_id`, `item_id`, `reserver_user_id` |
| `signup_completed` | `signup_trigger`, `referrer_wishlist_id` |
| `wishlist_returned` | `user_id`, `days_since_creation` |

**Forbidden** (wrapper drops even if a caller passes them):

- `email`, `displayName`, `name`, `firstName`, `lastName`, `phoneNumber`, `address`
- `wishlistTitle`, `itemName`, `itemDescription`, `imageUrl`, `coverImageUrl`
- `productLink` — security-findings.md F-5: user-supplied URLs may carry tokens / PII in query strings
- `ipAddress`, `userAgent` — PostHog auto-collects these from the frontend SDK; backend does not enrich

**Allowed user properties** (sent via `posthog.identify` from the frontend, sparingly):

- `preferredLanguage` (`en-US` | `pt-BR`) — already public from feature 005

If the frontend has an analytic question that requires a property not in the table above, route to the Backend Architect — the taxonomy is locked unless the cofounder approves an extension (plan §1: "if anything else gets built in this step, it's scope creep").

---

## 5. The split-gate model (production enablement)

The integration ships with `POSTHOG_ENABLED=false` in **every committed config** — local, test, staging, prod. The wrapper's `enabled=false` mode short-circuits before any SDK contact, so disabled environments emit nothing regardless of API-key presence.

Production enablement is **blocked on user-direct-action**:

1. **DPA executed and on file** (security-findings.md F-1) — PostHog publishes a standard DPA at `posthog.com/dpa`. User signs and stores it.
2. **Account-deletion runbook drafted** (security-findings.md F-3 Track 1) — manual procedure for honoring LGPD Art. 18, VI deletion requests via PostHog's per-`distinctId` deletion API. Tracked as a deferred follow-up; the engineered solution (auto-deletion on account-delete) is not in Phase 1.

After both prerequisites are met, the operator flips `POSTHOG_ENABLED=true` in Render's env-var dashboard. Per-environment files (`application-staging.yml`, `application-prod.yml`) hard-set `enabled: false` as defense-in-depth: a misconfigured env var cannot accidentally enable emission before the prerequisites are met.

Frontend has its own gating — the cookie-consent banner (LGPD lawful basis, OQ-2) MUST land before client-side `posthog-js` initializes. That's a separate Frontend + Product responsibility (plan §6).

---

## 6. Out of scope for the backend

Listed so frontend doesn't accidentally assume backend will do them:

- **PostHog dashboards** (view→signup funnel, retention cohort) — built in PostHog UI by the founder/user. Definition-of-done items #3 and #4. Not code.
- **Session-replay input masking** — `posthog-js` configuration; frontend MUST enable masking by default to avoid capturing form inputs (signup name, wishlist titles, item descriptions).
- **Cookie consent banner** — frontend + Product. Required if LGPD lawful basis is consent (recommended path per plan §6).
- **Web Analytics autocapture / page-view tracking** — `posthog-js` init. Frontend's call.
- **Right-to-deletion wiring (engineered solution)** — deferred follow-up. Phase 1 covers the manual runbook only (F-3 Track 1).
- **`email_verified` event** — dropped from v1 of this plan; not in the cofounder's 7-event taxonomy. Re-evaluate via separate plan if a real funnel question demands it.

---

## 7. Cross-references

- Plan: `specs/007-posthog-integration/plan.md`
- Architecture conventions: `architecture-conventions.md` § Layer Rules, § Configuration & Secrets, § Logging
- Privacy posture: `CLAUDE.md § Production Security Checklist`
- Wrapper implementation: `src/main/java/com/gifiti/api/analytics/PostHogClient.java`
- Filter: `src/main/java/com/gifiti/api/analytics/PostHogDistinctIdFilter.java`
