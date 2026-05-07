# Frontend Handoff — Feature 007 (PostHog Product Analytics)

> **Audience:** frontend engineers picking up the PostHog integration after backend ships (May 2026).
>
> **Status of backend (as of 2026-05-07):** complete. All 5 backend events wired, tested, deployed to staging with `POSTHOG_ENABLED=false`. Frontend work is currently the only remaining engineering work to make PostHog actually emit events.
>
> **Canonical contract doc:** [`docs/posthog-backend-integration.md`](./posthog-backend-integration.md). This handoff is the introduction; that doc is the spec. Read it end-to-end before starting.
>
> **Architecture decisions:** [`docs/adr/0007-posthog-product-analytics.md`](./adr/0007-posthog-product-analytics.md) — Decisions A–I, including the rationale for what's frontend-side vs backend-side.

---

## TL;DR — what you build, in priority order

1. **PostHog JS SDK initialization** (gated by cookie consent).
2. **Cookie consent banner** that blocks SDK init until the user consents.
3. **Two frontend-only events:** `wishlist_viewed`, `wishlist_shared`.
4. **`posthog.identify(userId, { $anon_distinct_id })`** after signup completion to merge anonymous pre-signup activity with authenticated identity.
5. **Capture `signupTrigger` + `referrerWishlistId`** from share-link URLs, persist through signup flow, send in `POST /api/v1/auth/register`.

The backend is currently running with `POSTHOG_ENABLED=false` — your work doesn't depend on the backend env-var flip. The flip happens later, after the privacy policy disclosure (F-2) and the operational TODOs in `docs/posthog-account-deletion-runbook.md` are filled in. **None of that blocks your work.**

---

## 1. PostHog JS SDK initialization

Install:

```bash
npm install posthog-js
# or
pnpm add posthog-js
```

Initialize with the US host (matches account region; LGPD Art. 33 compliance via PostHog DPA standard contractual clauses — Decision A revised 2026-05-07):

```js
import posthog from 'posthog-js';

posthog.init('<frontend-posthog-api-key>', {
  api_host: 'https://us.i.posthog.com',
  loaded: (posthog) => {
    // SDK ready. Do NOT capture events from here directly —
    // events should be captured at user-action boundaries.
  },
});
```

**Critical:** wrap this entire `posthog.init` call inside a check for cookie consent. If the user hasn't consented, PostHog must NOT initialize. This satisfies F-4 from the backend security review (consent-before-tracking).

```js
if (hasUserConsented()) {
  posthog.init(/* ... */);
}
```

Where to get the **frontend** PostHog API key: PostHog dashboard → Project Settings → API Keys → use the one labeled **"Project API Key"** (the public-safe key designed to ship in frontend code). NOT the Personal API Key — that one is for the deletion runbook only.

---

## 2. Cookie consent banner

Show on first visit (and any visit where consent isn't recorded).

**Two requirements:**

- **Block PostHog SDK init until the user consents.** No tracking before consent.
- **Record the consent decision** in `localStorage` (or `sessionStorage` if you prefer per-session consent) so subsequent visits don't re-prompt.

**LGPD-compliant patterns** (consult a lawyer for your specific jurisdiction):

- **Opt-in (recommended):** banner says "We use cookies for analytics. Accept to help us improve." with Accept + Reject buttons. PostHog only initializes after Accept. Most LGPD-safe — matches the "freely given, specific, informed consent" standard in LGPD Art. 8.
- **Opt-out (riskier):** PostHog initializes by default, banner offers a "decline analytics" toggle. Higher legal risk under LGPD; common in practice but not what we'd recommend.

**Suggested storage shape:**

```js
// On Accept:
localStorage.setItem('gifiti.consent.analytics', 'granted');
localStorage.setItem('gifiti.consent.timestamp', new Date().toISOString());

// On Reject:
localStorage.setItem('gifiti.consent.analytics', 'denied');

// At every page load:
function hasUserConsented() {
  return localStorage.getItem('gifiti.consent.analytics') === 'granted';
}
```

**Re-consent triggers** (consider these for the long term):
- Privacy policy update (signal a re-prompt by bumping a `consent.version` key).
- Significant change in PostHog event taxonomy.

---

## 3. Two frontend-only events

The cofounder's authoritative 7-event taxonomy splits 5 backend / 2 frontend. **Backend is authoritative for the 5 backend events — do NOT emit them from frontend** (would double-count in PostHog dashboards).

Your 2 events:

### `wishlist_viewed`

**When to emit:** every time the wishlist viewing page renders (anonymous OR authenticated).

**Properties:**

| Key | Type | Source |
|---|---|---|
| `shareable_id` | string | The `shareableId` from the URL (NanoID, 21 chars `[A-Za-z0-9_-]`) |
| `item_count` | number | Number of items on the wishlist at view time |
| `is_authenticated` | boolean | `!!currentUser` |

```js
posthog.capture('wishlist_viewed', {
  shareable_id: shareableId,
  item_count: wishlist.items.length,
  is_authenticated: !!currentUser,
});
```

### `wishlist_shared`

**When to emit:** when the user clicks any share button (copy-link, native share sheet, social platform button).

**Properties:**

| Key | Type | Source |
|---|---|---|
| `shareable_id` | string | The `shareableId` of the wishlist being shared |
| `method` | string | Which share method was used. Currently only `'copy_link'` is wired; future values may include `'native'`, `'whatsapp'`, `'twitter'`, `'email'` |

```js
posthog.capture('wishlist_shared', {
  shareable_id: shareableId,
  method: 'copy_link',
});
```

> **Note on property naming:** these property names reflect the shipped frontend implementation (per frontend team report 2026-05-07, citing commit `0084e8a` and `src/pages/shared-wishlist-page.tsx`). Backend's `PostHogClient.ALLOWED_PROPERTIES` is event-name-keyed, not property-keyed — so the backend allowlist doesn't enforce these specific frontend property names. Consistency in PostHog dashboards depends on the frontend keeping the names stable.

### Property-naming discipline (load-bearing)

The backend's `PostHogClient` enforces a strict allowlist of property keys for every event. The frontend SHOULD follow the same naming discipline even though there's no allowlist enforcing it on your side — consistency in dashboards depends on it.

**Never put PII in event properties.** No `email`, `displayName`, `firstName`, `lastName`, `phoneNumber`, `address`, `wishlistTitle`, `itemName`, `itemDescription`, `imageUrl`, `coverImageUrl`, `productLink`. The backend's allowlist drops these explicitly; your frontend code shouldn't generate them in the first place.

---

## 4. Anonymous-then-authenticated identity stitching

This is the most subtle part of the integration. Read it carefully.

**The flow:**

1. **First page visit (anonymous):** PostHog assigns an anonymous `distinctId` automatically. The user clicks a share link, views a wishlist, maybe shares it — all under that anonymous ID.

2. **Signup completion** (after the backend returns the new `userId` from `POST /api/v1/auth/register`): the frontend must call `posthog.identify` to tell PostHog "merge this anonymous user with this newly-authenticated user."

3. **From that point forward:** all events emit under the authenticated `userId`. Past anonymous events get linked to the authenticated profile in PostHog's data model.

**Code shape:**

```js
// After successful signup response:
async function onSignupSuccess(newUserId) {
  const anonId = posthog.get_distinct_id();
  posthog.identify(newUserId, { $anon_distinct_id: anonId });
}
```

**Important sequencing:**
- `posthog.identify` must be called AFTER the backend returns the new `userId`, NOT before (the backend's response is what tells you the canonical `userId`).
- `posthog.identify` must be called BEFORE any subsequent events you emit, so they're attributed to the authenticated user.

**Why backend doesn't do this:** the backend emits `signup_completed` server-side using the new `userId` directly as `distinctId` (Decision I in ADR 0007). The frontend's `posthog.identify` is what merges the two histories at PostHog's processor level.

---

## 5. Capture `signupTrigger` + `referrerWishlistId` from share-link URLs

When a user clicks a share link like `gifiti.app/wishlists/share/abc123` and then signs up, the backend wants to know two things:

- **Why did they sign up?** (`signupTrigger`)
- **Which wishlist's share link did they click?** (`referrerWishlistId`)

This is the strategic property the cofounder cares most about — share-link conversion attribution.

**The flow:**

### Step 1 — At first page load (before signup)

When the user lands on a share link or click-through page, capture and stash the context in `sessionStorage`:

```js
// Example: extract shareableId from URL like /wishlists/share/abc123
const shareableId = extractShareableIdFromUrl(window.location.pathname);

if (shareableId) {
  sessionStorage.setItem('gifiti.signup.referrerWishlistId', shareableId);
  // The user opened a share link → infer their signup trigger.
  // You decide the mapping based on UX context.
  // E.g., "create your own wishlist" CTA on the share page = CREATED_WISHLIST.
  //       "reserve this item" button click = RESERVED_ITEM.
  //       just viewing the share link = leave unset, defaults to DIRECT at submit time.
}
```

Use **`sessionStorage`** (not `localStorage`) so the values clear naturally when the browser tab closes. Don't persist across sessions — would create stale attributions.

### Step 2 — At signup form submit

Read the stashed values and attach to the registration request:

```js
async function submitSignup(formData) {
  const referrerWishlistId = sessionStorage.getItem('gifiti.signup.referrerWishlistId');
  const signupTrigger = sessionStorage.getItem('gifiti.signup.trigger') ?? 'DIRECT';

  const response = await fetch('/api/v1/auth/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      email: formData.email,
      password: formData.password,
      displayName: formData.displayName,
      // The two new optional fields:
      signupTrigger,           // string: 'CREATED_WISHLIST' | 'RESERVED_ITEM' | 'DIRECT'
      referrerWishlistId,      // string: NanoID 21-char [A-Za-z0-9_-], or null
    }),
  });

  if (response.ok) {
    sessionStorage.removeItem('gifiti.signup.referrerWishlistId');
    sessionStorage.removeItem('gifiti.signup.trigger');
  }

  return response;
}
```

### Critical: shape constraints

- **`signupTrigger`** must be exactly one of: `'CREATED_WISHLIST'`, `'RESERVED_ITEM'`, `'DIRECT'`. The backend enum is case-sensitive. Other values get a 400.
- **`referrerWishlistId`** must be a NanoID **21 characters** in alphabet `[A-Za-z0-9_-]`. The backend regex (`^[A-Za-z0-9_-]{21}$|^$`) rejects anything else with a 400.
- **Do NOT send the wishlist's internal `_id`** (24-char hex). The shareableId from the URL is the right value. This was Code Reviewer Finding 0001 — the regex was originally UUID-shaped and was corrected pre-merge to match the actual shareableId shape.
- Both fields are **optional** — passing `null` (or omitting them) is valid and the backend handles it gracefully (`signup_trigger` defaults to `DIRECT`).

---

## What you DON'T need to do (explicitly out of scope)

- **No `X-PostHog-DistinctId` header.** Code Reviewer Finding 0003 — that infrastructure was deleted because all backend events have an authenticated `userId`. Don't send any custom PostHog headers.
- **No session replay.** Decision F deferred it; can be added later but would need a separate F-3 amendment for PII redaction.
- **No backend event emission from frontend.** The 5 backend events (`signup_completed`, `wishlist_created`, `item_added`, `item_reserved`, `wishlist_returned`) are 100% backend-side. Emitting from frontend would double-count.
- **No PostHog dashboards setup.** That's product/analytics work, not engineering.

---

## What you need from the backend team

Three things:

1. **The frontend PostHog API key** — see § 1 above (Project API Key, not Personal).
2. **The canonical contract:** [`docs/posthog-backend-integration.md`](./posthog-backend-integration.md). Read it end-to-end.
3. **The 7-event taxonomy reference** — embedded in the contract doc; cofounder's authoritative spec.

---

## Smoke test before shipping to prod

Before you flip your frontend feature flag or deploy to production:

1. Verify `posthog.init` only fires after consent (in dev tools, observe `posthog` is `undefined` before clicking Accept).
2. Verify the 2 frontend events appear in PostHog's "Live events" view (https://us.posthog.com/project/412989/activity/explore).
3. Verify `posthog.identify` fires after signup with both `userId` and `$anon_distinct_id` (use PostHog's "Persons" view to confirm the merge).
4. Verify share-link sign-ups arrive on the backend with `referrerWishlistId` populated (check backend logs or PostHog's `signup_completed` event for the property).
5. Verify a malformed `referrerWishlistId` (e.g., a UUID-shaped string from old code) returns 400 from the registration endpoint, not 500 — confirms the validation is doing what it should.

---

## Open questions / things you might surface back to backend

If you hit any of these during implementation, route back:

- **Backend doesn't accept your `signupTrigger` value:** the enum is `CREATED_WISHLIST | RESERVED_ITEM | DIRECT`. If you need a fourth case (e.g., `SHARED_LINK_VIEW` separate from `DIRECT`), that's an enum-extension request — talk to backend, it's a small backend change.
- **Backend's `referrerWishlistId` regex is too strict:** if you're capturing values that don't fit `^[A-Za-z0-9_-]{21}$`, that's a Decision-H amendment — talk to backend.
- **You want to add a new frontend-emitted event beyond the 2:** out of feature 007 scope. Talk to product strategy first; new events are a Product Strategist + Backend Architect ratification.

---

## Related docs

- [`posthog-backend-integration.md`](./posthog-backend-integration.md) — the canonical contract.
- [`adr/0007-posthog-product-analytics.md`](./adr/0007-posthog-product-analytics.md) — architectural decisions A–I.
- [`posthog-account-deletion-runbook.md`](./posthog-account-deletion-runbook.md) — when a user requests deletion under LGPD Art. 18 VI.
- [`i18n-frontend-integration.md`](./i18n-frontend-integration.md) — sister doc for feature 005 (Accept-Language). PostHog events compose with this; localized responses still apply.

---

**Last reviewed:** 2026-05-07 (initial handoff post-feature-007 ship)
