# gifiti-backend Development Guidelines

Auto-generated from all feature plans. Last updated: 2026-02-28

## Active Technologies

- Java 21 + Spring Boot 3.x, Spring Security, Spring Data MongoDB, Jakarta Validation (001-gift-wishlist-backend)

## Project Structure

```text
backend/
frontend/
tests/
```

## Commands

# Add commands for Java 21

## Code Style

Java 21: Follow standard conventions

## Recent Changes

- 001-gift-wishlist-backend: Added Java 21 + Spring Boot 3.x, Spring Security, Spring Data MongoDB, Jakarta Validation
- 004-image-upload: Added image upload via Cloudflare R2 (AWS S3 SDK), coverImageUrl on Wishlist model, triple-layer file validation
- 005-i18n-backend-support: Added backend i18n (en-US / pt-BR) — Spring MessageSource + LocaleResolver, locale-aware validation/exception/success messages, localized email templates, PUT /profile preferredLanguage field
- 006-anonymous-public-wishlist-viewing: Removed authentication gate on GET /api/v1/public/wishlists/{shareableId}; localized owner displayName fallback (mitigates F-2 email-prefix leak); reserve/unreserve still require auth + email verification

<!-- MANUAL ADDITIONS START -->

## Production Security Checklist

- `.env` must NEVER be committed — verify `.gitignore` includes it
- Rotate `JWT_SECRET` and `MONGODB_URI` credentials regularly
- Ensure `APP_COOKIE_SECURE=true` (default) in production
- Set `CORS_ALLOWED_ORIGINS` to exact production domain(s)
- Swagger UI is disabled by default — set `SWAGGER_ENABLED=true` only in dev/staging
- `R2_ACCESS_KEY_ID` and `R2_SECRET_ACCESS_KEY` must NEVER be committed
- R2 API token should have minimal permissions (Object Read & Write on single bucket)
- Image upload rate limited to 20/hour per user
- `POSTHOG_API_KEY` must NEVER be committed; per-environment keys (prod != staging != local). **Do NOT rotate the key prod currently references via the PostHog dashboard** — it is no longer Gifiti's key; see the analytics-dark entries below.
- PostHog DPA executed (signed 2026-05-07) — **F-1 cleared**. Renew per PostHog cadence (annual / on amendment).
- Privacy policy at gifiti.app/privacy discloses PostHog as sub-processor with full LGPD Art. 9 transparency (live 2026-05-07) — **F-2 cleared**. Frontend additionally suppresses IP collection via `ip: false` in PostHog SDK init (frontend security review F-04 mitigation), reducing personal-data scope.
- Account-deletion runbook drafted in `docs/posthog-account-deletion-runbook.md` (security-findings.md F-3 Track 1). Operational TODOs (5 placeholders for audit-log location, PostHog Project ID/API key location, DPA storage location, R2 bucket+endpoint) remain to be filled in before first real deletion request — acceptable per user decision 2026-05-07.
- **PostHog analytics are DARK as of 2026-09-12, backend and frontend.** `POSTHOG_ENABLED=false` on the prod `gifiti-backend` Render service (manually managed, NOT in `render.yaml` — dashboard-only change); boot log confirms `PostHog analytics DISABLED (split-gate). No events will be emitted from this process.` Frontend unplugged separately in gifiti-front-end PR #37 (`posthog-js` uninstalled, `src/lib/analytics.ts` reduced to a no-op seam, PostHog hosts stripped from the `vercel.json` CSP). No code was deleted on either side — the split-gate exists so analytics can be turned off without code surgery.
- **IMPORTANT — PostHog project 412989 ("GGIFITI") is NOT Gifiti's project. It is Touchlinne's live production project** (`www.touchlinne.com`, actively ingesting; the only project in the "solo" org). Gifiti emitted 12 backend events into it in total, the last on 2026-05-30. **Never delete that project, and never rotate its key in the PostHog dashboard** — either action takes down Touchlinne's analytics, not Gifiti's. Clearing `POSTHOG_API_KEY` from the Gifiti Render service is safe, since that is a per-service env var. Verified 2026-09-12.
- Re-plug plan: a **new, separate** PostHog project for ggifiti, created later. Checklist and compliance re-review live in `docs/posthog-replug-followups.md`. The DPA, privacy-policy, and deletion-runbook entries above were all executed against project 412989 and **must be re-reviewed against the new project** before `POSTHOG_ENABLED=true` is flipped again.

## Telemetry

- `password_validation_rejected` INFO logs are calibration telemetry; correlate by `correlation_id`, no password content is logged. Emitted by `PasswordValidationService` on each rejection with `rule=<common_pattern|email_username_match|sequential_chars|repeated_pattern>`. Goal: data-driven decision on whether the "common_pattern" rule is overcalibrated for real users (Move 2 / Diagnosis C, 2026-05-06).

<!-- MANUAL ADDITIONS END -->
