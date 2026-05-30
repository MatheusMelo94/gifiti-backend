# ADR 0009 — Spanish Locale (`es-419`) + Auth errorCode Discriminators + FieldError Discriminators

- **Status:** Accepted
- **Date:** 2026-05-30
- **Author:** Backend Architect
- **Deciders:** User (project owner / sole architectural authority)
- **Supersedes:** none
- **Superseded by:** none
- **Source plan:** `specs/009-spanish-locale/plan.md` (v1, 2026-05-30)
- **Source spec:** `specs/009-spanish-locale/spec.md` (ratified 2026-05-30, "Ready for /plan")
- **Frontend source doc:** "Backend To-Do: Spanish Locale Support" (frontend reframing, 2026-05-30) — Option A (errorCode discriminators)
- **Related ADRs:** 0008 (private wishlist access codes — established the `errorCode` discriminator pattern + `error` field convention this ADR extends)

---

## 1. Context

Feature 009 delivers a **minimal backend slice** to complete Spanish (`es-419`) support for Gifiti's auth flows. The frontend has already shipped Spanish UI (570 keys translated, language dropdown live, `Accept-Language: es-419` on every request). The backend still leaks English/Portuguese strings on auth-error paths, validation-error paths, and the two transactional emails (verification + password reset).

The ratified spec splits the work into three buckets:

1. **Bucket 1** — 10 auth errorCode discriminators (4 endpoints).
2. **Bucket 2** — `FieldError.errorCode` extension on `ErrorResponse`.
3. **Bucket 3** — `Language.ES_419` enum value + `messages_es_419.properties` with the 4 access-code keys (feature 008) AND the email copy strings (so verification + password-reset emails render in Spanish for users whose `preferredLanguage = ES_419`).

This ADR ratifies seven decisions (A–G) and three flagged-for-user-input open questions (Open §1–§3).

### Existing constraints (verified at HEAD `41a43eb`)

- `ErrorResponse.errorCode` field already exists (`ErrorResponse.java` line 49) with `@JsonInclude(NON_NULL)` — Bucket 1 additive, no breaking change.
- `ErrorResponse.FieldError` today has only `field` + `message` — Bucket 2 adds a new nullable `errorCode` field.
- `GlobalExceptionHandler.buildErrorResponseWithCode(...)` helper exists (line 378) — construction path for new errorCode-carrying responses is already in place.
- `EmailTemplateRenderer` (`EmailTemplateRenderer.java`) pulls every copy string from `MessageSource`; HTML chrome lives in a Java text block. **There are no `.html` template files in the codebase**: locale variance is entirely bundle-driven (Decision F).
- `Language` enum has 2 values (`EN_US`, `PT_BR`); JavaDoc explicitly anticipates "one new value plus a `messages_xx_YY.properties` resource bundle." `GifitiLocaleResolver`'s supported-locales set derives from `Language.values()`.
- `AccountLockoutService` exists and throws via `AuthService.login()` line 178 — `ACCOUNT_LOCKED` wiring is a 1-line refactor, not new business logic (Decision E).
- pt-BR bundle currently exposes the 4 access-code keys + 9 `email.verification.*` keys + 9 `email.password.reset.*` keys = **22 keys** to translate for Spanish (Decision F + Bucket 3 scope).
- LGPD applies (Brazilian + LATAM users). No new PII surfaces are introduced by this feature; errorCode strings are not personal data.

### Two material discoveries during planning (surfaced to user as Open Questions §1, §2)

1. **`EMAIL_NOT_VERIFIED` at login is unimplemented business logic.** `AuthService.login()` does not currently check `user.isEmailVerified()`. Surfacing `EMAIL_NOT_VERIFIED` requires adding the check + a new throw. This is new business behavior, not a refactor — needs user signoff (Open Q1).
2. **`ALREADY_VERIFIED` on `POST /auth/verify-email` is unimplemented.** Today the endpoint nulls `verificationToken` on success, so a re-clicked link returns `INVALID_TOKEN`. Distinguishing "expired/never-existed" from "already-used (success path)" requires retaining the token hash (or its email association) post-verification. Three architectural shapes (Open Q2) — surfaced for user choice.

---

## 2. Decisions

### Decision A — Spanish locale: `es-419` (Latin America & Caribbean Spanish)

- **Context.** Frontend shipped `es-419` 2026-05-30 with 570 keys translated; backend catches up. Single Spanish variant; `es-ES`, `es-MX` remain unsupported (spec § Non-Goals #3).
- **Decision.** Add `Language.ES_419("es-419")` enum value. Resource bundle filename `messages_es_419.properties`. `Locale.forLanguageTag("es-419")` round-trips losslessly.
- **Consequences.** `GifitiLocaleResolver.SUPPORTED_LOCALES` picks up the new locale automatically (already derived from `Language.values()` per feature 005). MongoDB is schemaless — no migration; existing User documents are unaffected.

### Decision B — Error-message localization strategy: errorCode discriminators (NOT Spring resource-bundle localization of auth messages)

- **Context.** Frontend has invested in a translation pipeline; backend has not. Frontend's 2026-05-30 doc proposes Option A: backend emits machine-readable `errorCode` discriminators, frontend renders human-readable copy from its own `translation.json` bundles. Feature 008 established this pattern for access-code errors.
- **Decision.** All auth-error and validation-error copy in scope for feature 009 (Buckets 1 + 2) flows through the `errorCode` discriminator. NO new `error.auth.*` or validation keys added to any `messages_*.properties` bundle. The existing `message` field on `ErrorResponse` continues to carry locale-resolved text via the existing `MessageSource` plumbing — but the **frontend narrows on `errorCode` for translation**, not on `message`. Per spec § Decision B, the only server-rendered Spanish copy is (i) the 18 email copy strings (Decision F) and (ii) the 4 access-code message keys.
- **Consequences.** Backend localization surface stays narrow (22 Spanish strings total). Frontend owns the translation toil for the 10 auth errorCodes + Bucket 2 field-level codes (their cost; their preference per the 2026-05-30 doc). Pattern is consistent with feature 008.

### Decision C — Exception-class strategy: Option 1A (8 new dedicated exception subclasses)

- **Context.** The 10 errorCode values from Bucket 1 don't map 1:1 to existing exception classes. Three options were considered (1A: new dedicated subclasses; 1B: reuse `UnauthorizedException`/`ConflictException` with errorCode constructor arg; 1C: hybrid). Strategist's nudge was 1A.
- **Decision.** Add **8 new dedicated exception subclasses**, one per distinct errorCode (note `INVALID_TOKEN` and `EXPIRED_TOKEN` are shared between verify-email and reset-password — one class each covers both endpoints):
  1. `InvalidCredentialsException` → `ERROR_CODE = "INVALID_CREDENTIALS"` → HTTP 401
  2. `EmailNotVerifiedException` → `ERROR_CODE = "EMAIL_NOT_VERIFIED"` → HTTP 401 (or 403 — see Open Q1)
  3. `AccountLockedException` → `ERROR_CODE = "ACCOUNT_LOCKED"` → HTTP 401
  4. `EmailAlreadyRegisteredException` → `ERROR_CODE = "EMAIL_ALREADY_REGISTERED"` → HTTP 409
  5. `WeakPasswordException` → `ERROR_CODE = "WEAK_PASSWORD"` → HTTP 400
  6. `InvalidTokenException` → `ERROR_CODE = "INVALID_TOKEN"` → HTTP 401
  7. `ExpiredTokenException` → `ERROR_CODE = "EXPIRED_TOKEN"` → HTTP 401
  8. `AlreadyVerifiedException` → `ERROR_CODE = "ALREADY_VERIFIED"` → HTTP 409 (or 200 — see Open Q2)

  All 8 extend `LocalizedRuntimeException` directly (same parent as the existing `AccessCodeRequiredException` etc.), each carrying a `public static final String ERROR_CODE` constant + a `public static final String MESSAGE_KEY` constant, exactly matching the feature-008 template (`AccessCodeRequiredException.java`).

  Each gets a dedicated `@ExceptionHandler` method on `GlobalExceptionHandler` that calls the existing `buildErrorResponseWithCode(...)` helper — same pattern as the four feature-008 handlers (lines 228–287).

- **Consequences.**
  - **+8 exception class files**, each ~25 lines.
  - **+8 `@ExceptionHandler` methods** on `GlobalExceptionHandler` (~12 lines each).
  - **Pattern consistency** with feature 008. Mechanically obvious where each errorCode originates (grep `ERROR_CODE` lists every discriminator).
  - **Existing `UnauthorizedException` and `ConflictException` remain unchanged.** They continue to carry `error.auth.*` / `error.email.already.registered` semantics for callers that don't (yet) need the errorCode discriminator. The new dedicated classes replace specific throw sites in `AuthService`; the generic classes stay in use for other paths.
  - **Rejected Option 1B (reuse generics + errorCode constructor arg):** would have blurred the discriminator pattern. `grep "ERROR_CODE"` would not enumerate the discriminator inventory; locality at the throw site would be weaker.
  - **Rejected Option 1C (hybrid):** would introduce a second pattern with no payoff. The 8 new classes are mechanically trivial; uniform dedicated subclasses are cleaner than splitting the inventory.

### Decision D — `FieldError.errorCode` shape: top-level errorCode for the duplicate-email case, NO field-level entry

- **Context.** Bucket 1 emits `EMAIL_ALREADY_REGISTERED` as a top-level `errorCode` for `POST /auth/register` duplicate-email; Bucket 2 wants `TAKEN` as a possible `FieldError.errorCode` value. Two response shapes were considered (Shape A: top-level only; Shape B: top-level + redundant field-level entry).
- **Decision.** **Shape A.** Duplicate-email on registration emits `{"errorCode": "EMAIL_ALREADY_REGISTERED", "status": 409, ...}` with **no** `details[]` field-level entry. `TAKEN` remains a valid `FieldError.errorCode` *value* in the inventory (Decision D2 below) but is **not emitted for the registration duplicate-email path** — that path has a dedicated top-level discriminator.

  **D2 — `FieldError.errorCode` value inventory (Bucket 2).** The deterministic Jakarta-Validation-constraint-to-errorCode mapper outputs these codes:

  | Constraint / source | `FieldError.errorCode` |
  |---|---|
  | `@NotBlank` / `@NotNull` violation | `REQUIRED` |
  | `@Email` violation | `INVALID_FORMAT` |
  | `@Size(min=...)` violation (under min length) | `TOO_SHORT` |
  | `@Size(max=...)` violation (over max length) | `TOO_LONG` |
  | `@Pattern` violation | `INVALID_FORMAT` |
  | `@Min` / `@Max` / `@PositiveOrZero` violation | `OUT_OF_RANGE` |
  | Future custom password validator (annotation-based) | `WEAK_PASSWORD` |
  | Future business-layer uniqueness check that emits a field-level error | `TAKEN` |
  | Anything else (default) | `INVALID` |

  The mapper is a single pure static method in `util/FieldErrorCodeMapper` keyed on the violation's constraint annotation type (Jakarta exposes this via `ConstraintViolation.getConstraintDescriptor().getAnnotation().annotationType()`). The mapping is documented in the JavaDoc; new codes get added by appending a switch arm + a Jakarta-annotation entry — Engineer must NOT invent new codes outside this table without architect signoff.

- **Consequences.**
  - Single source of truth per error (Shape A).
  - `TAKEN` lives in the inventory as a reserved value for future field-level uniqueness paths (the codebase doesn't have one today on the registration path — duplicate-email is a service-layer business-rule check, not a Jakarta annotation — so the registration path emits the top-level discriminator only).
  - The mapper handles `@Pattern` and `@Email` both as `INVALID_FORMAT` — frontend distinguishes by field name + endpoint context, not by errorCode.
  - **Rejected Shape B:** redundant emission of the same semantic information at two levels was rejected on simplicity grounds. Frontend's 2026-05-30 doc framed `TAKEN` as "lower priority — only matters for server-only validation rules"; Shape A respects that priority.

### Decision E — `ACCOUNT_LOCKED` scope: IN SCOPE (Bucket 1)

- **Context.** Gap-2 question: does `AccountLockoutService` produce a throwable surface? Verified at HEAD `41a43eb`: yes — `AccountLockoutService.isLocked(email)` is called in `AuthService.login()` line 176, and the locked branch throws `UnauthorizedException("error.auth.account.locked")` at line 178. The MessageSource key `error.auth.account.locked` already exists in `messages.properties` and `messages_pt_BR.properties` (feature 005 i18n migration).
- **Decision.** Wire `ACCOUNT_LOCKED` via a new `AccountLockedException extends LocalizedRuntimeException` (Decision C, class #3). Replace the throw site at `AuthService.login()` line 178 from `throw new UnauthorizedException("error.auth.account.locked", new Object[0])` to `throw new AccountLockedException()` — the new class wraps the same MessageSource key as its `MESSAGE_KEY` constant. No service-layer logic change.
- **Consequences.** 1-line refactor at the throw site. The pre-existing `error.auth.account.locked` MessageSource key continues to back the `message` field on the response (the human-readable text that's already locale-resolved). The new `errorCode = "ACCOUNT_LOCKED"` adds the discriminator. **Total scope addition: 0 lines of new business logic.**

### Decision F — Email template rendering: MessageSource-based (CORRECTS the spec's assumption)

- **Context.** The ratified spec assumed file-based email templates (e.g., new `verification-es_419.html` files). **This is incorrect.** Verified at HEAD `41a43eb`: `EmailTemplateRenderer.java` (lines 36–379) holds the HTML chrome in a Java text block and pulls **every locale-variant string** from `MessageSource` via `messageSource.getMessage("email.verification.subject", ...)` etc. There are no `.html` template files in `src/main/resources/`.
- **Decision.** Bucket 3 ships Spanish emails entirely by adding 18 new keys to `messages_es_419.properties`:
  - 9 `email.verification.*` keys: `subject`, `welcome`, `body`, `cta`, `fallback.notice`, `fallback.link`, `ignore.notice`, `footer.copyright`, `footer.signup`.
  - 9 `email.password.reset.*` keys: `subject`, `heading`, `body`, `cta`, `fallback.notice`, `fallback.link`, `ignore.notice`, `footer.copyright`, `footer.notice`.
  - **No new Java files. No new HTML template files.** The renderer picks up `Language.ES_419` automatically once the enum value is added (Decision A) and the bundle exists.
- **Consequences.**
  - **Bucket 3 scope shrinks** vs the spec's assumption. Engineer adds ~22 properties-file lines (4 access-code + 18 email) instead of authoring 2 new HTML templates. Saves ~30–60 min of engineer time; reduces translation surface to clearly-scoped string keys.
  - **Translation deliverable:** ~22 Spanish strings (not "~24"). AI-drafts + user spot-check per Decision C of the spec.
  - Updates the spec's Strategist-supplied wall-clock estimate slightly downward — see Plan §6 for the refined estimate.

### Decision G — `WEAK_PASSWORD` granularity: single generic errorCode (no rule-specific variants)

- **Context.** `PasswordValidationService` rejects on 4 distinct rules (`common_pattern`, `email_username_match`, `sequential_chars`, `repeated_pattern`) and emits per-rule INFO telemetry per the feature-007 calibration contract. Question (Gap raised in dispatch brief): should the user-facing `WEAK_PASSWORD` errorCode encode the rule (e.g., `WEAK_PASSWORD_COMMON_PATTERN`) or stay generic?
- **Decision.** **Single generic `WEAK_PASSWORD`** errorCode. The 4 rule names remain internal to the INFO telemetry (feature 007 Move 2 / Diagnosis C calibration channel) and do not leak to the frontend's translation surface.
- **Consequences.**
  - Frontend has 1 Spanish string to translate ("Senha fraca" → "Contraseña débil"), not 4.
  - Per-rule diagnostics remain operator-side via the existing `password_validation_rejected rule=<name> correlation_id=<id>` log line — that's the right channel for the rule-overcalibration question feature 007 set up.
  - If product signal later demands rule-specific user-facing copy (e.g., "your password is too sequential" vs "your password is too common"), supersede this decision with a follow-up ADR — additive change, low blast radius.

---

## 3. Privacy / LGPD Summary

Feature 009 introduces **no new PII surfaces.** The errorCode discriminators are machine-readable constants (not personal data). The 22 Spanish translation strings are static product copy. No new logs, no new telemetry, no new collections.

| Item | LGPD basis | Notes |
|---|---|---|
| 10 new auth `ERROR_CODE` string constants | n/a (not personal data) | Public API discriminators; identical entropy class to feature 008's 4 access-code codes. |
| `FieldError.errorCode` value (e.g., `REQUIRED`, `TOO_SHORT`) | n/a (not personal data) | Field-name-keyed; same logging discipline as feature 005 (no PII at INFO). |
| `messages_es_419.properties` | n/a (translation content) | Static product copy; user-reviewed before merge per spec § Decision C. |
| `Language.ES_419` enum value on `User.preferredLanguage` | Art. 6 IX (data minimization) | Same status as existing `EN_US` / `PT_BR` — single enum field. |

**No new Security Engineer concurrence is required for this feature.** The architectural pattern (errorCode discriminators) was concurrence-cleared in feature 008. No new attack surface; no enumeration-vs-UX trade-off (the closest analog — `EMAIL_NOT_VERIFIED` at login surfaces email-verification state — is gated by Open Q1 below).

---

## 4. Out of Scope (Deferred, with Revisit Triggers Recorded)

| Item | Why deferred | Revisit trigger |
|---|---|---|
| Backend Spring resource-bundle localization of auth messages (en/pt/es) | Frontend owns auth-error copy via errorCode discriminators (Decision B) | Frontend reverses architectural direction and asks backend to localize |
| Additional Spanish variants (`es-ES`, `es-MX`) | Single-variant strategy per spec § Non-Goals #3 | Real adoption signal from an `es-ES` or `es-MX` market |
| Welcome / marketing / admin email translations | Out of spec scope (only 2 transactional emails in Bucket 3) | Product Strategist surfaces marketing-locale requirement |
| Per-language Resend templates | Spec § Non-Goals #12 | Engineering-cost driver (e.g., bundle size or per-locale tooling) |
| Locale-aware data formatting (currency / dates / numbers) | Spec § Non-Goals #6 | First product feature that surfaces currency or formatted dates to LATAM users |
| Rule-specific `WEAK_PASSWORD_*` errorCodes | Decision G; calibration telemetry handles operator side | Product demand for rule-specific user-facing copy |

---

## 5. Convention Drift Surfaced

Items the Architect surfaces for user approval to promote to standing convention:

1. **`architecture-conventions.md § API Contracts`** — codify the `errorCode` discriminator pattern formally (it is now established across 2 features and 14 codes: 4 from feature 008 + 10 from feature 009). Specifically: when a response shape needs a frontend-narrowable machine-readable code, ALL endpoints in that domain (or all endpoints, project-wide) emit `errorCode` as a top-level field. `error` retains HTTP-reason-phrase semantics. `message` retains human-readable locale-resolved text via MessageSource for legacy clients.

2. **`architecture-conventions.md § API Contracts` (additional)** — codify the field-level `FieldError.errorCode` inventory (`REQUIRED`, `INVALID_FORMAT`, `TOO_SHORT`, `TOO_LONG`, `OUT_OF_RANGE`, `WEAK_PASSWORD`, `TAKEN`, `INVALID`) as the canonical set. New values require ADR amendment.

3. **`architecture-conventions.md § Error Handling`** — codify the "dedicated exception class per discriminator" pattern (Decision C / Option 1A). Each errorCode has its own subclass with `public static final String ERROR_CODE` + `public static final String MESSAGE_KEY`. `grep "ERROR_CODE"` over `src/main/java/com/gifiti/api/exception/` returns the complete discriminator inventory.

4. **`architecture-conventions.md § Internationalization`** (new section) — codify the email-localization architecture: HTML chrome in Java text blocks; copy strings in `messages_xx_YY.properties`. **No HTML template files.** Adding a new locale = 1 enum value + 1 properties file (the JavaDoc on `Language.java` already states this; promote it to a standing convention).

These remain proposals. The user approves convention edits.

---

## 6. Open Questions Routed Forward

### Open Q1 — Should `POST /auth/login` reject unverified emails with `EMAIL_NOT_VERIFIED`?

- **Today.** `AuthService.login()` does NOT check `user.isEmailVerified()` before returning tokens. An unverified user can log in.
- **Frontend's request.** Their 2026-05-30 doc lists `EMAIL_NOT_VERIFIED` as one of the 3 login errorCodes — implying they expect the backend to reject unverified-email logins.
- **Two paths:**
  - **(a) Add the check** in `AuthService.login()` after successful credential check, before token issuance: if `!user.isEmailVerified()`, throw `EmailNotVerifiedException`. HTTP 401 (or 403 — see below).
  - **(b) Drop `EMAIL_NOT_VERIFIED` from Bucket 1.** Tell frontend to surface verification state via a different channel (e.g., the existing `/auth/resend-verification` endpoint, or a profile-state field). 9-code Bucket 1.
- **HTTP status sub-question.** If (a): 401 (consistent with the other login errors — INVALID_CREDENTIALS, ACCOUNT_LOCKED) or 403 (semantically: "authenticated but not authorized to log in until verified")? Architect's lean: **401** — keeps all login failures at one status; the discriminator carries the reason.
- **Architect's recommendation.** Path (a) with HTTP 401. The behavior change is small (~5 lines in `AuthService.login()`) and aligns Gifiti with industry-standard auth UX (Stripe, GitHub, etc. all block unverified-email logins). But this is **new business behavior**, not just a refactor — user signs off, not architect.
- **Blocking.** Engineer can't ship Bucket 1's `EMAIL_NOT_VERIFIED` until user resolves this.

### Open Q2 — `ALREADY_VERIFIED` on `POST /auth/verify-email` — three architectural shapes

- **Today.** `AuthService.verifyEmail(token)` looks up the user by hashed verification token (line 281). On success, it nulls `verificationToken` + `verificationTokenExpiry` (lines 289–290) and saves. **A re-clicked link finds no matching token → throws `UnauthorizedException("error.auth.verification.token.invalid")` → today maps to `INVALID_TOKEN`.** The endpoint cannot distinguish "this token was never valid" from "this token was valid yesterday and succeeded."
- **Three shapes to surface `ALREADY_VERIFIED`:**
  - **(a) Retain the token hash post-verification** (don't null it). The endpoint can then find the user, see `emailVerified=true` and `verificationTokenExpiry` is set (or some new flag), and throw `AlreadyVerifiedException`. **Storage cost:** indefinite retention of the hashed token (small). **Risk:** mild — the hashed token isn't a secret post-verification (it was single-use; nulling was hygiene, not security-critical).
  - **(b) Add an `expiredVerificationToken` field** that retains the *last* hashed token after verification, separate from the active `verificationToken`. Cleaner separation; +1 field on User.
  - **(c) Add `email` to the request DTO** so the endpoint can look up the user by email *and* the token. Cleanest, but frontend has to send the email (which it has). Requires DTO change.
  - **(d) Drop `ALREADY_VERIFIED` from Bucket 1.** Re-clicked verify links continue to return `INVALID_TOKEN`. Frontend renders generic "this link can't verify you — check your inbox or request a new one." 9-code Bucket 1 (or 8 if Q1 also drops).
- **Architect's lean.** **(a)** — minimal change, idiomatic. The hashed verification token has zero value post-verification; retaining it costs nothing.
- **Blocking.** Engineer can't ship `ALREADY_VERIFIED` until user resolves this.

### Open Q3 — `EMAIL_NOT_VERIFIED` HTTP status (sub-question of Q1)

Captured inside Q1 above. 401 vs 403. Architect's lean: 401.

---

## 7. Sign-Off

- **Status:** Accepted
- **Date:** 2026-05-30
- **Approver:** User (project owner)
- **Author:** Backend Architect
- **Immutability:** Future changes via a superseding ADR. Decisions A, B, D, E, F, G are stable; Decision C is stable. Open Q1 and Q2 resolutions land as either spec-amendment language or as a follow-up plan increment — they do NOT amend this ADR (this ADR records that they exist and are routed).
