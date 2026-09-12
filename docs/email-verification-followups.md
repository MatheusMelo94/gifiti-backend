# Email Verification — Deferred Follow-ups

> **Context:** Opened 2026-09-12 after the invalid-`RESEND_API_KEY` outage. The
> outage itself is closed — the key was rotated on the prod Render service and
> registration was confirmed working end to end. The hardening that came out of
> it shipped on branch `implemenatation-of-resend` (broad catch + MDC
> propagation, credential health probe, `email_delivery_log`).
>
> **Nothing here is blocking.** Item 1 is a real design gap that is currently
> harming nobody; items 2–4 are deferred work and closed findings recorded so
> they are not re-litigated.

---

## 1. Unverified users have no self-service recovery path

**Status:** open design gap. Not currently affecting any user.

Three behaviours combine into a dead end for anyone whose verification email
does not arrive:

| Path | Behaviour | Where |
|---|---|---|
| Log in | `EmailNotVerifiedException` thrown *before* token issuance | `AuthService.login()`, feature 009 / T14 (`f2039a6`) |
| Resend verification | Requires `@AuthenticationPrincipal`; returns 401 when absent | `AuthController.resendVerification()` |
| Register again | `EmailAlreadyRegisteredException` (409) | `AuthService.register()` |

An unverified user cannot log in, so cannot obtain a token, so cannot reach the
resend endpoint — which is the only way to get a second verification email. The
sole recovery is operator intervention against the database.

Not a regression from any single change. T14 (ratified 2026-05-30, ADR 0009 Open
Q1) was a deliberate business decision, and the auth requirement on
resend-verification predates it. The gap is the *interaction* between them, which
neither change considered.

**Scope of exposure.** Google sign-in sets `emailVerified(true)` at
`AuthService:471,489`, so OAuth users are unaffected. Only password registrants
can enter this state.

**Why it isn't urgent right now.** During the 2026-09-12 outage window no users
registered (confirmed by the user, 2026-09-12), so nobody is presently stuck.
That is luck, not a property of the system — the next delivery failure of any
kind puts real users into this state.

### Options

- **(a) Accept an email on an unauthenticated resend endpoint** — the direct fix.
  Requires an anti-enumeration response (one identical message for unknown /
  already-verified / genuinely-pending, mirroring `forgotPassword`) plus the
  existing 10 req/min IP bucket that already covers `/api/v1/auth/**`. The
  `@Async` send means the three branches are already indistinguishable by
  response time. **Changes the API contract** — the endpoint currently takes an
  empty body and the frontend calls it authenticated, so it needs frontend
  coordination. Recommended.
- **(b) Let unverified users log in with reduced capability**, replacing the T14
  hard block with per-endpoint gating. Reopens a decision the user already
  ratified; larger blast radius.
- **(c) Operator-triggered bulk resend** — a one-shot `CommandLineRunner` in the
  manner of `AccessCodeBackfillRunner`. A mitigation for an incident in progress,
  not a fix for the gap.

**Note if (a) is chosen:** an anti-enumeration implementation for
`resendVerification` was written during this session and reverted unshipped, for
the reason in item 4 below. Under (a) that reasoning no longer holds and the work
becomes necessary — the endpoint would then genuinely accept an attacker-supplied
address.

---

## 2. Deferred — outbox + retry sweeper

**Status:** deliberately deferred (user decision, 2026-09-12).

`email_delivery_log` (commit `9378727`) records every send attempt but does not
retry them. The full version adds `@EnableScheduling` plus a backoff retry job,
making delivery self-healing: rotate a bad key and queued mail flushes on its own.

Deferred because it is the largest piece by far and introduces job
infrastructure the codebase does not currently have — there is no `@Scheduled` or
`@EnableScheduling` anywhere in `src/main/java`. It would also need idempotency
and a distributed claim/lock before Render ever runs more than one instance; the
same multi-instance caveat `AccessCodeBackfillRunner` documents applies.

---

## 3. Registration over-promises on a send it cannot confirm

**Status:** open, low cost, needs frontend coordination.

`register()` returns 201 with `auth.register.success` ("check your email to
verify your account") regardless of whether the send succeeded. It cannot do
otherwise: `send()` is `@Async void`, so the response is written before the
outcome exists. Blocking on the result would reintroduce exactly the latency and
failure coupling that `@Async` exists to avoid.

The cheap mitigation is on the frontend rather than here — soften the copy so it
does not promise delivery, and surface a prominent "didn't get it? resend"
affordance. Costs nothing and shortens every future incident of this shape.
Depends on item 1 being fixed first, since the resend path is currently
unreachable for the users who would need it.

---

## 4. Closed — `resendVerification` is not a user-enumeration oracle

**Status:** closed 2026-09-12. Recorded so it is not re-raised.

It was proposed during this session that `AuthService.resendVerification`'s
`UnauthorizedException("error.auth.user.not.found")` branch leaked account
existence, on the reasoning that the method takes a raw email and
`/api/v1/auth/**` is `permitAll` in `SecurityConfig`. **That was wrong.**

`AuthController.resendVerification` takes `@AuthenticationPrincipal UserDetails`
and passes `userDetails.getUsername()` — the caller's *own* address — returning
401 before the service when the principal is absent. A caller can therefore only
ask about the account they already hold a valid token for. The not-found branch
is reachable only with a live token for a deleted user, and the distinct
"already verified" response tells an account owner about their own account, which
is useful feedback rather than a leak.

The fix was written and reverted unshipped: it would have degraded a precise
message into a vague conditional one to close a hole that does not exist.

**This holds only while the endpoint derives the address from the authenticated
principal.** See the note under item 1 — option (a) inverts it.

---

## 5. Domain references need confirming

**Status:** open, needs a human with dashboard access.

`APP_BASE_URL` on the prod Render service is `https://www.ggifiti.com` (confirmed
2026-09-12), and the email templates hardcode `https://www.ggifiti.com` for the
logo and footer link. Several docs still say `gifiti.app`:

- `CLAUDE.md` — privacy policy cited as `gifiti.app/privacy`, the URL backing
  compliance finding F-2. Worth settling first: a privacy policy cited as
  clearing a finding should not point at a domain the product may not be served
  from.
- `docs/API_REFERENCE.md:5` — production base URL `https://api.gifiti.app`. This
  one may well be correct; an API host legitimately differs from the frontend
  origin. Needs confirming, not assuming.
- `docs/frontend-handoff.md:183`, `docs/adr/0008` line 17 — prose references.

It also recasts `docs/posthog-replug-followups.md:19` ("**Zero gifiti.app
pageviews**"), which was read as thin traffic. If the site is served at
`www.ggifiti.com`, zero pageviews on `gifiti.app` is just the wrong hostname.
Does not change the Touchlinne conclusion, but the inference should not be
carried into the re-plug review.
