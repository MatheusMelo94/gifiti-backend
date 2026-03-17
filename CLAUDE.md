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

<!-- MANUAL ADDITIONS START -->

## Production Security Checklist

- `.env` must NEVER be committed — verify `.gitignore` includes it
- Rotate `JWT_SECRET` and `MONGODB_URI` credentials regularly
- Ensure `APP_COOKIE_SECURE=true` (default) in production
- Set `CORS_ALLOWED_ORIGINS` to exact production domain(s)
- Swagger UI is disabled by default — set `SWAGGER_ENABLED=true` only in dev/staging

<!-- MANUAL ADDITIONS END -->
