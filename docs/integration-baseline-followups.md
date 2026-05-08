# Integration Baseline — Deferred Follow-ups

> **Context:** Tracker for cross-cutting test-infrastructure debt surfaced during Move 2 (2026-05-06). Two latent bugs were masking each other in the integration suite (BaseIntegrationTest's `Password123!` fixture rejected by PasswordValidationService, AND the `createUserAndGetToken` vs `createVerifiedUserAndGetToken` mismigration in 5 test classes). Both fixed in the commit that introduced this file.
>
> **Do NOT delete this file when its items are addressed.** It tracks debt the project knowingly took on; closing items belongs in commits that mitigate them.

---

## 1. PasswordValidationService rule calibration (depends on telemetry)

**Context:** The `common_pattern` rule rejects fixture passwords like `Password123!`. Whether real users hit this rule at meaningful frequency is unknown.

**What was added in the commit that introduced this file:** INFO-level structured log line `password_validation_rejected rule={ruleName} correlation_id={cid}` on every rejection. No password content, no email — only the rule name and correlation ID.

**Decision deferred:** whether to recalibrate `common_pattern` (or any of the four rules: `common_pattern`, `email_username_match`, `sequential_chars`, `repeated_pattern`).

**Revisit triggers:**
- One week of production traffic post-deploy (date when `POSTHOG_ENABLED=true` flips, or sooner if flipping is delayed).
- Frontend reports user complaints about password rejection messages.
- Drop in registration-completion rate visible in PostHog (once feature 007 is live).
- ANY one rule fires >X% of all rejections (X to be determined empirically).

**Owner role at trigger time:** Backend Architect (calibration decision based on rule-fire distribution) → Backend Engineer (rule-rule edits).

---

## 2. PasswordValidationService WARN→INFO recategorization

**Context:** `PasswordValidationService` currently emits `WARN SECURITY_EVENT` on every weak-password rejection. A prospective user typing a weak password during signup is calibration data, not a security incident — the user isn't authenticated yet. WARN-level noise on every rejection masks real security events in operator dashboards.

**What was added in the commit that introduced this file:** parallel INFO line for telemetry (additive, not replacement, per dispatch constraint).

**Mitigation candidate:** demote the existing WARN to INFO for non-authenticated rejection paths; keep WARN only for authenticated-user password-change rejections (where weak-password attempts may indicate account compromise).

**Revisit triggers:**
- After Item 1's calibration data lands.
- Any operator-dashboard noise complaint.

**Owner role at trigger time:** Backend Engineer (small refactor, no design decision needed).

---

## 3. createUserAndGetToken vs createVerifiedUserAndGetToken — over-verification audit

**Context:** Move 2 surfaced the under-verification side of this bug (5 test classes calling `createUserAndGetToken` for endpoints that require email verification, which fail with HTTP 403). The over-verification side (tests calling `createVerifiedUserAndGetToken` for endpoints that don't require verification) silently masks bugs — a test that should detect a regression in the verification gate doesn't.

**What was discovered in the commit that introduced this file (Audit B):**

- `src/test/java/com/gifiti/api/integration/anonymous/AnonymousPublicWishlistAccessTest.java:130` — uses `createVerifiedUserAndGetToken` for a viewer that only performs `GET /api/v1/public/wishlists/{shareableId}`. That endpoint does NOT call `requireEmailVerified` — verification is irrelevant to the test subject. If the verification gate were accidentally added to that endpoint by a future change, this test would not detect it.
- `src/test/java/com/gifiti/api/integration/i18n/ExceptionLocalizationIntegrationTest.java:151` — uses `createVerifiedUserAndGetToken` for a user that only hits `POST /api/v1/uploads/image`. The image-upload endpoint does NOT call `requireEmailVerified`. Same masking concern.

**Mitigation candidate:** for each over-verified test, swap to the unverified helper. If the test still passes, the regression-masking is confirmed. Then add an explicit additional test that verifies the endpoint correctly enforces (or doesn't) the verification gate.

**Revisit triggers:**
- Any test class added in a future feature that copies the over-verification pattern.
- Real production incident traceable to a missing verification check.

**Owner role at trigger time:** Backend Engineer (mechanical migration + augment test coverage).

---

## 4. Password123! fixture audit beyond base class

**Context:** Move 2's Audit C catalogued every test using the `Password123!` literal directly (i.e., not via the `BaseIntegrationTest` constants). These tests are at risk if `PasswordValidationService` rules tighten further.

**What was discovered in the commit that introduced this file (Audit C):**

- Audit found ZERO usages of the `Password123!` literal in the test suite. The prior dispatch's fixture swap (Scope 1a) eliminated all literal `Password123!` usages from the integration test files in favour of synthetic strong passwords (`Mvn-Build-Cyan-Glow-2026!`, `SecurePass123!`, `BlueP4nther$Xyz2!`, `Str0ng!Xyz#9`).
- `PasswordValidationServiceTest` (the unit test that deliberately probes weak-password rejection) does NOT use the literal `Password123!`; it uses `Qwerty-Reject-Telemetry-2026!` to exercise the `common_pattern` rule.

**Mitigation candidate (preventative):** consolidate test fixtures to a single shared constant in `BaseIntegrationTest` (e.g., `STRONG_TEST_PASSWORD`) so future tests cannot drift. Tests that need to verify rejection of weak passwords (`PasswordValidationServiceTest`) keep their inline weak literals; the constant is for the strong-password fixture path only.

**Revisit triggers:**
- Next time `PasswordValidationService` rules tighten.
- Routine refactor pass.

**Owner role at trigger time:** Backend Engineer.

---

## 5. Integration test assertions drifted from production behavior — broader audit

**Context:** Move 2 surfaced two test-assertion-drift bugs by removing the masking layer (`Password123!` fixture + verified-helper migration). Both fixes were 2-character-string-level (`PublicWishlistIntegrationTest:105` stale auth-gate assertion from feature 006; `ReservationIntegrationTest:89` stale i18n message from feature 005). This means the integration suite has been silently red on assertion changes for at least the duration of features 005 and 006. The CI exclusion pattern that has been keeping `main`'s CI "green" has been hiding both kinds of bugs all along.

**What was discovered in the commit that introduced this section:** two test assertions, both fixed in the commit. Likely more exist — they were just masked by the 33 verification-gate failures.

**Incidental observation:** `ReservationIntegrationTest.shouldRejectAlreadyReservedItem` covers the multi-user "fully reserved" path (`error.reservation.fully.reserved`). The same-user re-reservation path (`error.reservation.already.reserved.by.user` at `messages.properties:130`) does not appear to have direct coverage in this suite. Not addressed in this dispatch (out of scope); flagged here as a potential gap.

**Mitigation candidate:** comprehensive integration-test-vs-production-behavior audit. For every `IntegrationTest` class, run the test in isolation against current `main` HEAD with the CI exclusion pattern stripped. Catalog every assertion that fails. Triage each: (a) test should be updated to match correct production behavior (b) production behavior is wrong and test was correct (c) both drifted. Most likely (a) for the bulk.

**Revisit triggers:**
- Move 3 PR's CI surfaces additional integration failures not addressed in the commit that introduced this section.
- Any future feature ships with integration tests that pass locally but fail on `main` (indicates the audit is overdue).
- Quarterly tech-debt review.

**Owner role at trigger time:** Backend Architect (audit scope) → Backend Engineer (assertion fixes).

---

## How this list is updated

When a trigger fires:
1. The owning role drafts a small `/plan` for the mitigation.
2. User approves.
3. Backend Engineer implements with TDD.
4. After merge, the relevant section above is **deleted** from this file (with a commit citing what closed it).

When all items are addressed, this file is deleted in a single commit titled `chore(test-infra): close all integration-baseline follow-ups`.

---

## 6. PostHog frontend taxonomy gaps — product-side, not engineering

**Context:** Frontend integration shipped in PR #16 + frontend repo's PostHog merge (commit `0084e8a`, 2026-05-07). Two values from the cofounder's authoritative 7-event taxonomy are not currently fired by the frontend because the UI surfaces that would set them don't exist yet.

**Gap 1 — `signupTrigger=CREATED_WISHLIST`:**
The `SignupTrigger` enum (`com.gifiti.api.model.enums.SignupTrigger`) has three values: `DIRECT`, `RESERVED_ITEM`, `CREATED_WISHLIST`. Backend accepts all three. Frontend currently fires only `DIRECT` (default) and `RESERVED_ITEM` (when a logged-out user clicks "Reserve" on a shared wishlist). `CREATED_WISHLIST` requires a "Start your own wishlist" CTA on the shared page — UI element not built yet.

**Gap 2 — Share method beyond `"copy_link"`:**
The `wishlist_shared` event has a `method` property. Frontend currently only sets `"copy_link"` because clipboard-copy is the only share UI wired. When the frontend adds native share sheet (`navigator.share()`), social platform buttons (WhatsApp, Twitter, etc.), each new share path adds a new `method` value — no schema change required, just new event property values.

**Mitigation candidates (when product is ready):**
- Add "Start your own wishlist" CTA on shared wishlist page → frontend stashes `signup_trigger=CREATED_WISHLIST` to sessionStorage on click.
- Add native + social share UI → frontend extends share button family with `method: "native"`, `method: "whatsapp"`, etc.

**Revisit triggers:**
- Cofounder building dashboards and asking "where's the CREATED_WISHLIST signup data?".
- A user-research session reveals share-link clicks are the primary attribution path and the team wants to break out copy-link vs native share.
- Product roadmap explicitly schedules either UI element.

**Owner role at trigger time:** Product Strategist (UX decision) → Frontend Engineer (implementation). Backend has no involvement; both gaps are entirely frontend-product scope.

---

**Last reviewed:** 2026-05-07 (added during frontend handoff doc correction)
