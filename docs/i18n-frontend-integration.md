# Frontend Integration Guide — Backend i18n (en-US / pt-BR)

> **Audience:** frontend engineers integrating with `gifiti-backend` after the i18n feature ships (May 2026).
>
> **Scope of this document:** the wire contract between frontend and backend for language preferences, locale-aware responses, and email content. Does NOT prescribe which i18n library to use on the frontend, which state-management pattern, or how to render the language switcher UI. Those are frontend decisions.
>
> **TL;DR:** Send `Accept-Language: pt-BR` (or `en-US`) on every API call. For authenticated users, the backend ALSO honors a stored preference on the User document — settable via `PUT /api/v1/profile`. Backend resolves the locale per-request using a precedence chain (header > stored preference > en-US default). Backend localizes validation errors, exception messages, success messages, and email content automatically.

---

## 1. The locale resolution model — what happens on every request

For every API request your frontend makes, the backend resolves a single locale and uses it to localize the response. The resolution chain is:

```
1. If Accept-Language header is present and supported (en-US or pt-BR):
   → use the header
2. Else if the request is authenticated AND the User document has preferredLanguage:
   → use the stored preference
3. Else:
   → fall back to en-US
```

**Three implications for the frontend:**

| Scenario | What you should send | Backend behavior |
|---|---|---|
| Unauthenticated request (signup, forgot-password, public wishlist view) | `Accept-Language: pt-BR` (or `en-US`) | Header drives locale; no stored preference exists yet |
| Authenticated request, language matches stored preference | Either send the header (recommended) or omit it | Stored preference would resolve correctly anyway |
| Authenticated request, user temporarily wants different language for ONE request | `Accept-Language: <other-lang>` | Header overrides stored preference for THIS request only; stored preference unchanged |

**Recommendation:** always send `Accept-Language` on every API call. It eliminates ambiguity and means anonymous flows (forgot-password, signup validation errors) always honor the user's browser language.

---

## 2. Sending `Accept-Language` from the frontend

Set this once at your HTTP client level. Pseudocode for common patterns:

### Fetch wrapper

```typescript
async function api(path: string, options: RequestInit = {}) {
  const language = getCurrentAppLanguage(); // 'pt-BR' or 'en-US'
  return fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: {
      'Accept-Language': language,
      'Content-Type': 'application/json',
      ...options.headers,
    },
    credentials: 'include', // send the auth cookie
  });
}
```

### Axios interceptor

```typescript
axiosClient.interceptors.request.use((config) => {
  config.headers['Accept-Language'] = getCurrentAppLanguage();
  return config;
});
```

### Where `getCurrentAppLanguage()` comes from

Your frontend's source of truth for the active language. Typical implementations:

1. **localStorage** (`localStorage.getItem('lang') ?? navigator.language`)
2. **Browser-detect on first visit, persist in profile after login** (covered in §5)
3. **Whatever your i18n library exposes** (`i18next.language`, `vue-i18n.locale`, etc.)

**Supported values:** exactly `"en-US"` and `"pt-BR"`. Anything else (`"pt-PT"`, `"es-ES"`, `"fr-FR"`, `"pt"`) is treated as unsupported and the backend falls back to en-US.

---

## 3. Path A — New signup flow

### What the user does

1. Lands on signup page (probably already in their browser language via your frontend i18n)
2. Fills out signup form (name, email, password)
3. Submits

### What the frontend does

```typescript
POST /api/v1/auth/register
Headers:
  Accept-Language: pt-BR    ← critical: this drives backend behavior
  Content-Type: application/json
Body:
  {
    "email": "user@example.com",
    "password": "SenhaForte#2026!",
    "displayName": "Maria Silva"
  }
```

### What the backend does

1. Validates the input. If validation fails, returns 400 with **Portuguese** error messages:
   ```json
   {
     "timestamp": "2026-05-03T22:02:42Z",
     "status": 400,
     "error": "Bad Request",
     "message": "Falha na validação",
     "details": [
       { "field": "password", "message": "Senha deve ter entre 12 e 128 caracteres" }
     ]
   }
   ```
2. If validation passes:
   - Creates the User document with `preferredLanguage: "pt-BR"` **persisted** (derived from the `Accept-Language` header on the signup request)
   - Sends a verification email **in Portuguese** (subject + body)
   - Returns 201 with a Portuguese success message:
     ```json
     {
       "id": "65f1a2b3c4d5e6f7a8b9c0d1",
       "email": "user@example.com",
       "message": "Cadastro realizado com sucesso. Verifique seu email para confirmar sua conta."
     }
     ```

### What this means for the frontend

- **The user's language preference is set automatically based on the signup request's `Accept-Language` header.** You don't have to make an additional API call to set it.
- **All future emails to this user will use the persisted preference**, regardless of what `Accept-Language` they send on subsequent requests.
- **The signup confirmation message in the response is already localized** — display it as-is, no client-side translation needed.

### Edge case: signup with no `Accept-Language` (or unsupported value)

Backend defaults to `en-US`. The User document gets `preferredLanguage: "en-US"`. They can change it later via the profile-update endpoint (§5).

---

## 4. Path B — Anonymous flows (forgot-password, public wishlist viewing)

These flows have no authenticated user, so the only signal is `Accept-Language`.

The current anonymous-callable surface:

- `POST /api/v1/auth/forgot-password` — request a password reset link.
- `POST /api/v1/auth/register`, `POST /api/v1/auth/login`, `POST /api/v1/auth/verify-email`, `POST /api/v1/auth/reset-password` — the broader `/api/v1/auth/**` namespace.
- **`GET /api/v1/public/wishlists/{shareableId}` — viewing a shared wishlist (feature 006, May 2026).** No `Authorization` header required. Reserve / unreserve actions on the same wishlist still require auth.

### Forgot-password example

```typescript
POST /api/v1/auth/forgot-password
Headers:
  Accept-Language: pt-BR
Body:
  { "email": "user@example.com" }
```

**Backend behavior:**
- Response message uses request locale: `"Se existir uma conta com este email, um link para redefinir a senha foi enviado."`
- Email sent to user (if they exist) uses the **stored** `preferredLanguage` on their User document, NOT the request's `Accept-Language`. This is intentional — emails follow the user's account preference, not whoever clicked "forgot password" from whatever device.

### Important security note

The forgot-password response is **intentionally identical regardless of whether the email exists**. This prevents account enumeration attacks. Your frontend should:

- Show the success message to the user no matter what
- NOT show "user not found" or any indicator of whether the email exists
- Treat the response as informational only

The backend test suite enforces this property (see "Security guards" section below).

---

## 5. Path C — Existing users changing their language

After a user has signed up, they can change their stored preference via the profile-update endpoint.

### What the frontend does

```typescript
PUT /api/v1/profile
Headers:
  Authorization: Bearer <token>     // OR cookie-based auth
  Accept-Language: pt-BR             // optional, drives THIS response only
  Content-Type: application/json
Body:
  {
    "preferredLanguage": "pt-BR"     // or "en-US"
  }
```

(Other profile fields can be updated in the same request — `displayName`, etc. The DTO supports partial updates; only fields you include are touched.)

### Backend behavior

1. Validates `preferredLanguage` is one of `en-US` / `pt-BR`. If not, returns 400 (Bad Request) with localized message
2. If valid, updates the User document's `preferredLanguage` field
3. Logs an INFO audit-line with masked email, old language, new language (forensic visibility)
4. Returns 200 with the updated profile, including the new `preferredLanguage`:
   ```json
   {
     "id": "65f1a2b3c4d5e6f7a8b9c0d1",
     "email": "user@example.com",
     "displayName": "Maria Silva",
     "preferredLanguage": "pt-BR",
     "emailVerified": true
   }
   ```

### Important: changing `preferredLanguage` doesn't change THIS response's locale

The backend resolves the response locale based on the request's `Accept-Language` header, NOT the new `preferredLanguage` you just set. This is by design — you may be changing the language preference on behalf of someone reading in a different language right now.

If you want the next response to use the new language, send the new `Accept-Language` header on the next request.

### Edge case: user sends an unsupported language

```typescript
PUT /api/v1/profile
Body: { "preferredLanguage": "fr-FR" }
```

Returns:
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Corpo da requisição malformado"   // (or English if Accept-Language was en-US)
}
```

The frontend's language switcher UI should restrict the user's choice to the two supported options to avoid this case in practice.

---

## 6. Reading the user's current language

```typescript
GET /api/v1/profile
Headers:
  Authorization: Bearer <token>
```

Response includes:
```json
{
  "id": "65f1a2b3c4d5e6f7a8b9c0d1",
  "email": "user@example.com",
  "displayName": "Maria Silva",
  "preferredLanguage": "pt-BR",
  "emailVerified": true
}
```

**Use this on app load** (after login) to sync your frontend's i18n state with the user's stored preference. Recommended flow:

1. User opens the app → frontend reads `localStorage.lang` → sets initial UI language
2. User logs in → frontend calls `GET /api/v1/profile` → receives `preferredLanguage`
3. If `profile.preferredLanguage !== currentAppLanguage`:
   - Option A: switch the UI to the stored preference (let server win)
   - Option B: ask the user "your stored language is X, switch to it?" (let user choose)

Option A is the smoother UX; Option B respects the user's potentially-deliberate device-level preference. Frontend's call.

---

## 7. Recommended language-switcher UI flow

### First visit (anonymous)

1. Frontend reads `navigator.language` to detect browser language
2. Match against supported set: `pt-BR` if it starts with `pt`, else `en-US`
3. Set as the active language; persist in `localStorage.lang`
4. Render UI in that language; send `Accept-Language` on all API calls

### Login

1. After successful login, fetch `GET /api/v1/profile`
2. Read `preferredLanguage` from response
3. If it differs from the current app language: handle per Option A or B above
4. Update `localStorage.lang` and active i18n state

### User clicks the language switcher in the header

1. Frontend immediately switches UI to the new language (optimistic update)
2. Frontend calls `PUT /api/v1/profile` with `{ "preferredLanguage": "<new-lang>" }`
3. On 200 response: update `localStorage.lang` and active i18n state to confirmed value
4. On 4xx response: roll back optimistic update + show error toast

### Logout

1. Frontend clears auth state but **keeps** `localStorage.lang`
2. Anonymous flows continue to honor the device-level preference

---

## 8. What strings the backend localizes (and what it doesn't)

### Localized (backend handles)

- **Validation error messages** for all 13 request DTOs (signup, login, profile update, wishlist CRUD, item CRUD, password reset, etc.)
- **Exception messages** (404 not found, 403 access denied, 401 unauthorized, 409 conflict, validation 400, server 500)
- **Success messages** in response bodies (`message` field on `RegisterResponse`, `MessageResponse`, etc.)
- **Email content** — subjects + HTML bodies for verification email, password reset email
- **Anti-enumeration messages** — forgot-password ack, login bad-credentials, token errors (intentionally vague in BOTH languages)
- **Owner displayName fallback on public wishlists** — when a shared wishlist's owner has not set a displayName, `PublicWishlistResponse.ownerDisplayName` resolves through the `wishlist.owner.anonymous.fallback` bundle key (en-US: `"Wishlist owner"`, pt-BR: `"Anônimo"`). Never an email-derived string. (Feature 006 / Security finding F-2 mitigation.)

### NOT localized (frontend's responsibility)

- **All UI labels, headers, buttons, navigation, page titles, etc.** — your frontend's i18n library handles this
- **User-generated content** — wishlist titles, item names, descriptions written by users themselves (stored as-is, never translated)
- **Date/time/number/currency formatting** — backend returns ISO timestamps and raw numbers; frontend formats per locale (`Intl.DateTimeFormat`, `Intl.NumberFormat`)
- **HTML page metadata** — `<title>`, `<meta>`, Open Graph tags for SEO

### Currently still English regardless of locale (known gaps, may be fixed later)

- **Spring Security 401 "Authentication required"** — fires when a request to a protected endpoint has no token. Pre-dates the localization layer; one line away from being fixed. **Note (feature 006):** `GET /api/v1/public/wishlists/{shareableId}` no longer triggers this 401 — the path is now anonymous-permitted, so shareable links open without an Authorization header. Other protected paths (reserve, profile, wishlist write, etc.) still emit the English 401 when called without auth.
- **Spring Security AccessDenied "Cannot save your own wishlist"** — one specific 403 path in `GifterService` left in English. Tracked as a post-merge follow-up.
- **Swagger UI / OpenAPI descriptions** — explicitly out of scope (Swagger is dev-only, disabled in production).
- **Server logs** — operator-facing, intentionally English.

---

## 9. Email content the user receives

After signup with `Accept-Language: pt-BR`:

**Verification email** — subject: `"Bem-vindo ao Gifiti - Por favor, confirme seu email"`. HTML body includes welcome copy, "Confirmar endereço de email" CTA button, fallback link, ignore-notice for false-recipients, footer copyright.

**Password reset email** — subject: `"Redefina sua senha do Gifiti"`. HTML body includes reset request copy, "Redefinir senha" CTA button, fallback link, ignore-notice, footer copyright.

The verification + reset link URLs are server-generated and identical between languages. Frontend's reset-password page handles those links — typically:

- Email contains link like `https://app.ggifiti.com/reset-password?token=abc123`
- User clicks link → frontend reset-password page reads token from URL
- Frontend submits `POST /api/v1/auth/reset-password` with the token + new password
- Frontend should send `Accept-Language` here too (the response message is localized via request locale, even though the email language was determined by the user's stored preference)

---

## 10. Security guards (informational — no frontend action required)

The backend has automated regression tests for these properties. You don't need to do anything; they're listed here so you understand the guarantees:

- **Anti-enumeration length-ratio (F-1):** Portuguese versions of forgot-password / login / token-error messages stay within ±30% of English length, preventing length-based account enumeration.
- **No `{key}` template leak (F-4):** validation responses never contain literal `{validation.x}` placeholders. If the backend ever leaks a key string, the integration test fails immediately.
- **No internal key in success-message JSON (F-5):** the `message` field in success responses is always a resolved string, never an object exposing internal keys.
- **HTML-escape contract (F-6):** all runtime args substituted into emails are HTML-escaped before being inserted into the HTML body.
- **Cross-language link-target equivalence (F-6 §3):** verification + password-reset email links resolve to the same host across both languages, preventing translator phishing-vector drift.

---

## 11. Testing your frontend integration

### Manual smoke tests via curl

Sign up as a Portuguese-speaker:
```bash
curl -X POST -H "Accept-Language: pt-BR" -H "Content-Type: application/json" \
  -d '{"email":"test@exemplo.com","password":"Gifiti2026#Test!","displayName":"Maria"}' \
  https://gifiti-backend-za3h.onrender.com/api/v1/auth/register
```
Expected: 201 with Portuguese `message` field.

Trigger a validation error in Portuguese:
```bash
curl -X POST -H "Accept-Language: pt-BR" -H "Content-Type: application/json" \
  -d '{"email":"not-email","password":"short"}' \
  https://gifiti-backend-za3h.onrender.com/api/v1/auth/register
```
Expected: 400 with Portuguese error messages in `details`.

Same in English (omit `Accept-Language` or send `en-US`):
```bash
curl -X POST -H "Accept-Language: en-US" -H "Content-Type: application/json" \
  -d '{"email":"not-email","password":"short"}' \
  https://gifiti-backend-za3h.onrender.com/api/v1/auth/register
```
Expected: 400 with English error messages.

### Automated frontend tests

If your frontend has E2E tests (Playwright, Cypress), recommended additions:

1. **Locale persistence test:** sign up with `Accept-Language: pt-BR`, log out, log back in → verify the app's UI returns to Portuguese (because `preferredLanguage` was persisted)
2. **Locale switcher test:** click the language switcher → verify `PUT /api/v1/profile` is called with the right body → verify the API response has updated `preferredLanguage`
3. **Anonymous validation error test:** submit signup form with bad data while `Accept-Language: pt-BR` is set → verify the error toast/inline message shows Portuguese text

---

## 12. Common pitfalls to avoid

| Pitfall | Symptom | Fix |
|---|---|---|
| Forgetting to send `Accept-Language` | Anonymous flows always English regardless of user preference | Add to HTTP client base config (axios interceptor / fetch wrapper) |
| Sending `Accept-Language: pt-PT` (Portugal Portuguese) | Backend treats as unsupported, falls back to en-US | Map browser locales to supported set (`pt-PT` → `pt-BR`, `pt` → `pt-BR`) |
| Not syncing app language with stored preference on login | User has `preferredLanguage: "pt-BR"` in DB but app shows English | Call `GET /api/v1/profile` after login, sync if mismatch |
| Sending stale `Accept-Language` after language switch | App says it's in pt-BR but API responses are still in English | Update HTTP client config when `getCurrentAppLanguage()` source changes |
| Optimistic-update language switch without backend call | UI shows new language but `preferredLanguage` never updates → next email is in old language | Call `PUT /api/v1/profile` on switch |
| Translating user-generated content | Wishlist titles get butchered by frontend i18n library | Treat `wishlist.title`, `item.name` as opaque strings; don't pass through `i18n.t()` |
| Trying to set unsupported language values | 400 errors from `PUT /api/v1/profile` | Restrict the language switcher UI to `en-US` and `pt-BR` only |

---

## 13. API endpoint reference (i18n-related fields)

### `POST /api/v1/auth/register`
- Request: `email`, `password`, `displayName?`
- Headers honored: `Accept-Language`
- Side effect: stores `preferredLanguage` from `Accept-Language` (default `en-US`)
- Response: `RegisterResponse` with localized `message`

### `POST /api/v1/auth/login`
- Request: `email`, `password`
- Headers honored: `Accept-Language` (for response message only)
- Response: auth cookie + JSON with localized `message` field

### `POST /api/v1/auth/forgot-password`
- Request: `email`
- Headers honored: `Accept-Language` (drives RESPONSE locale)
- Email language: uses stored `preferredLanguage` of the user (if found); request locale otherwise
- Response: localized success message (intentionally identical whether email exists)

### `POST /api/v1/auth/reset-password`
- Request: `token`, `newPassword`
- Headers honored: `Accept-Language`
- Response: localized success message

### `GET /api/v1/profile`
- Auth: required
- Response: includes `preferredLanguage` field (always non-null; defaults to `en-US` for legacy users)

### `PUT /api/v1/profile`
- Auth: required
- Request: any subset of profile fields, including `preferredLanguage`
- Validation: `preferredLanguage` must be `"en-US"` or `"pt-BR"`; other values return 400
- Side effect: updates User document; emits audit log if `preferredLanguage` changes
- Response: updated `ProfileResponse`

### `GET /api/v1/public/wishlists/{shareableId}`
- Auth: optional (anonymous read enabled by feature 006, May 2026; reserve action on the same wishlist still requires auth)
- Headers honored: `Accept-Language`
- Response: localized error messages — 404 for both "not found" and "exists but PRIVATE" (anti-enumeration parity; the two cases are indistinguishable to anonymous callers by design)
- Note: if the owner has not set `displayName`, `response.ownerDisplayName` is a localized fallback (en-US: `"Wishlist owner"`; pt-BR: `"Anônimo"`). Never email-derived. (Security finding F-2 mitigation.)

---

## 13.1 Handling shareable links for unauthenticated visitors (feature 006)

Once a user receives a shareable wishlist URL, the frontend can render the wishlist without an authenticated session.

### Recommended flow

1. **Shareable URL lands on the frontend** (e.g. `https://app.ggifiti.com/w/{shareableId}`).
2. **Frontend calls `GET /api/v1/public/wishlists/{shareableId}` without an `Authorization` header.** Send `Accept-Language` so error messages localize.
3. **On 200:** render the wishlist, items, owner displayName, cover image. Treat `ownerDisplayName` as opaque text (it may be a real name OR the localized fallback string — the API guarantees it is safe to display).
4. **On 404:** render a generic "wishlist not found or no longer public" message. Do NOT distinguish "not found" from "private" — the backend collapses both intentionally. Same Accept-Language guidance applies.
5. **For "Reserve item" action:**
   - Anonymous visitor clicks "Reserve" on an item → the frontend prompts the user to sign in (or sign up). The reserve POST endpoint still requires authentication + verified email.
   - After successful login, the frontend re-issues the reserve POST with the auth cookie / token.
   - Reserve is `POST /api/v1/public/wishlists/{shareableId}/items/{itemId}/reserve` and returns 401 to anonymous calls.

### What changed from pre-006

Pre-006 (the "link + login" gate), the frontend redirected anonymous viewers to `/login?next=<shareable-url>` before issuing any backend call. That redirect is no longer required for view; keep it for the reserve action only. Existing authenticated clients keep working with no change — they can still pass an Authorization header on the GET, and the response is byte-for-byte identical.

---

## 14. Need help?

- Backend implementation: `specs/005-i18n-backend-support/` (in repo, gitignored — ask Matheus for access)
- Convention reference: `architecture-conventions.md § Layer Rules` (locale flow discipline)
- Security findings: F-1 through F-6, all mitigated or deferred per `security-findings.md`
- Production smoke test endpoint: `https://gifiti-backend-za3h.onrender.com/actuator/health` (returns 200 + JSON)

---

**Last updated:** 2026-05-04, refined for feature `006-anonymous-public-wishlist-viewing` (anonymous shareable-link viewing + F-2 owner displayName fallback localization).
