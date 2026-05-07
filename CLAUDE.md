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
- `POSTHOG_API_KEY` must NEVER be committed; per-environment keys (prod != staging != local); rotate via PostHog dashboard on suspected compromise.
- PostHog DPA executed (signed 2026-05-07) — **F-1 cleared**. Renew per PostHog cadence (annual / on amendment).
- Account-deletion runbook drafted in `docs/posthog-account-deletion-runbook.md` before `POSTHOG_ENABLED=true` in production (security-findings.md F-3 Track 1). **STILL OPEN.**
- Privacy policy must disclose PostHog as a sub-processor before `POSTHOG_ENABLED=true` in production (security-findings.md F-2). **STILL OPEN.**

## Telemetry

- `password_validation_rejected` INFO logs are calibration telemetry; correlate by `correlation_id`, no password content is logged. Emitted by `PasswordValidationService` on each rejection with `rule=<common_pattern|email_username_match|sequential_chars|repeated_pattern>`. Goal: data-driven decision on whether the "common_pattern" rule is overcalibrated for real users (Move 2 / Diagnosis C, 2026-05-06).

<!-- MANUAL ADDITIONS END -->
