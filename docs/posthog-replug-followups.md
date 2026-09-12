# PostHog Re-plug — Deferred Follow-ups

> **Context:** Tracker for the work required to turn product analytics back on for ggifiti against a **new** PostHog project. Analytics went fully dark on 2026-09-12 (backend `POSTHOG_ENABLED=false`; frontend `posthog-js` uninstalled in gifiti-front-end PR #37). No analytics code was deleted on either side — the split-gate in `PostHogConfiguration` exists precisely so emission can be stopped without code surgery.
>
> **Status as of 2026-09-12:** nothing here is blocking. The product runs normally with analytics dark; the five capture sites are no-ops. These items become real work when the user decides to create the new project.
>
> **Do NOT delete this file when analytics are re-plugged.** It tracks debt the project knowingly took on; closing items belongs in commits that mitigate them.

---

## 0. Read this first — the old project is someone else's

PostHog project **412989** is named "GGIFITI" but is **Touchlinne's live production analytics project** (`www.touchlinne.com`). It is the only project in the "solo" organization, and it was actively ingesting traffic at the time analytics were unplugged here.

Verified 2026-09-12 by querying the project directly:

- Gifiti backend events, all-time: **12** (`signup_completed` ×1, `item_added` ×2, `item_reserved` ×2, `wishlist_created` ×4, `wishlist_returned` ×3). Last one **2026-05-30**.
- Gifiti frontend events, all-time: `wishlist_viewed` ×13 (last 2026-05-30), `wishlist_shared` ×20 (last 2026-05-31).
- Everything else in the project — ~350k events and counting — is Touchlinne's. `$pageview` breaks down to `www.touchlinne.com` and a touchline Vercel preview host. **Zero gifiti.app pageviews.**

**Consequences, which apply to every item below:**

- **Never delete project 412989.** It would destroy Touchlinne's analytics.
- **Never rotate project 412989's key in the PostHog dashboard.** CLAUDE.md's general "rotate on suspected compromise" guidance does not apply to it any more. Clearing `POSTHOG_API_KEY` from the *Gifiti Render service* is safe — that is a per-service env var and touches nothing in PostHog.
- The 12 stale Gifiti events left in 412989 are noise next to Touchlinne's volume. No cleanup is required; leaving them is the accepted decision (user, 2026-09-12).

---

## 1. Create the new ggifiti PostHog project

**Status:** deferred by user decision, 2026-09-12. "I will create in the future a new project for ggifiti."

**What it involves:**
- New project in the "solo" org (or wherever ggifiti should live). Confirm region — `POSTHOG_HOST` stays `https://us.i.posthog.com` unless the new project is EU, in which case both the backend env var and the frontend SDK init must change together.
- New server-side project API key. Set `POSTHOG_API_KEY` on the prod `gifiti-backend` Render service — dashboard-only, since prod is manually managed and **not** declared in `render.yaml` (see `render.yaml:109`).
- Staging already declares `POSTHOG_ENABLED: "false"` with `POSTHOG_API_KEY: sync: false` (`render.yaml:100-107`), so staging picks up a key the same way without a code change.

**Ordering — do not get this backwards.** `PostHogConfiguration` fails fast: `enabled=true` with a missing/blank key throws `IllegalStateException` and **the app will not start** (`PostHogConfiguration.java:52-60`). Therefore:
1. Set `POSTHOG_API_KEY` first, confirm the service is healthy.
2. Only then flip `POSTHOG_ENABLED=true`.

This is the mirror image of the unplug ordering (flag off first, *then* clear the key), and for the same reason.

**Revisit triggers:**
- User decides ggifiti needs product analytics again.
- A product question arises that cannot be answered without event data.

**Owner role at trigger time:** User (project creation) → Backend Engineer (Render env vars) → DevOps Engineer (verification).

---

## 2. Compliance artifacts must be re-reviewed against the new project

**Risk:** three compliance gates were cleared in May 2026 against project 412989 specifically. They do **not** automatically carry over to a new project, and CLAUDE.md's Production Security Checklist still lists them as cleared.

| Artifact | Current state | Needed before re-enabling |
|---|---|---|
| PostHog DPA | Signed 2026-05-07 (F-1 cleared) | Confirm the executed DPA covers the new project / is with the same legal entity. Re-sign or amend if not. Renew per PostHog cadence regardless. |
| Privacy policy sub-processor disclosure | Live at gifiti.app/privacy since 2026-05-07 (F-2 cleared), full LGPD Art. 9 transparency | Re-read against the new project's data flows. If the event taxonomy or region changes, the disclosure changes. |
| Account-deletion runbook | `docs/posthog-account-deletion-runbook.md` (F-3 Track 1) | Its "PostHog project ID for production" TODO must be filled with the **new** project ID — never 412989, which would send a deletion request at Touchlinne's data. Four other operational TODOs remain open from 2026-05-07. |

**Revisit triggers:** fires together with item 1 — this is a hard prerequisite, not a parallel track. `POSTHOG_ENABLED=true` must not be flipped until all three rows are green.

**Owner role at trigger time:** Security Engineer (compliance review) → Backend Architect (sign-off).

---

## 3. Frontend / backend re-plug must be coordinated

**Risk:** the state that prompted the unplug was *half* on — backend emitting, frontend silent, no `posthog.identify()` from the browser. Identity merging was broken and the resulting data was misleading. Re-plugging one side without the other recreates exactly that.

**What must land together:**
- Frontend: reinstall `posthog-js`, restore `src/lib/analytics.ts` from its no-op seam, re-add the PostHog hosts to the `vercel.json` CSP, restore `identify()` on login/registration. Keep `ip: false` in SDK init — that was the frontend security review F-04 mitigation and reduces personal-data scope.
- Backend: items 1 and 2 above.

**Also note:** person / `distinct_id` history from project 412989 does **not** carry over. Treat the new project as a clean slate — no continuity of funnels, cohorts, or retention baselines. Any saved insight or dashboard built on the old project's data stays with Touchlinne.

**Revisit triggers:** fires together with item 1.

**Owner role at trigger time:** Backend Engineer + Frontend Engineer, sequenced by the user.

---

## 4. `signupTrigger` / `referrerWishlistId` are collected but discarded while dark

**Context:** `POST /api/v1/auth/register` still accepts and validates `signupTrigger` and `referrerWishlistId` (`RegisterRequest.java:58,81`), and the frontend still sends them. `AuthService` reads them **only** inside the PostHog try-block (`AuthService.java:143-152`) to build `signup_completed` props. They are persisted nowhere.

**Consequence:** while the gate is closed these values are accepted, validated, and dropped on the floor. **There is no backfill path** — signup-attribution data for the dark period is permanently unavailable, no matter what the new project does.

**Decision taken (2026-09-12):** leave the plumbing in place. Removing it would mean a frontend contract change for no benefit, and it must exist again at re-plug time.

**Mitigation candidate (only if attribution for the dark period turns out to matter):** persist `signupTrigger` / `referrerWishlistId` onto the `User` document so they can be replayed into the new project later. This is a schema addition and an LGPD question (new personal-data field, new retention basis) — not a free change.

**Revisit triggers:**
- Someone asks "where did our signups come from between 2026-05-30 and re-plug?" and the answer matters commercially.
- The dark period extends beyond one quarter.

**Owner role at trigger time:** Backend Architect (schema + LGPD call) → Backend Engineer.

---

## How this list is updated

When a trigger fires:
1. The owning role drafts a small `/plan` for the mitigation.
2. User approves.
3. Backend Engineer implements with TDD.
4. After merge, the relevant section above is **deleted** from this file (with a commit citing what closed it).

Section 0 is the exception: it stays until project 412989 is no longer shared with another product, regardless of what else closes.

When analytics are fully re-plugged and sections 1–4 are closed, this file is deleted in a single commit titled `chore(analytics): close PostHog re-plug follow-ups`.

---

**Last reviewed:** 2026-09-12 (created at unplug time — backend gate closed and verified via Render boot log; old-project ownership discovered and recorded)
