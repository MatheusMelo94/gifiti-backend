# Staging Setup Runbook — gifiti-backend

> Cite: `devops-conventions.md § Project Setup`, `architecture-conventions.md § Multi-target Deployment`
>
> Goal: stand up a Render staging service that mirrors prod, isolated at the
> data layer (separate MongoDB cluster, separate R2 bucket). Once this is
> done, every merge to `main` auto-deploys to staging.
>
> Scope of THIS runbook: staging service only. The existing prod service
> (`gifiti-backend`, Oregon, Starter) is intentionally untouched and
> continues to be managed manually via the Render Dashboard. Adopting prod
> into the blueprint is a separate later task, paired with the manual
> prod-deploy workflow (Step 5 of the DevOps remediation plan).
>
> Estimated time: 30–45 minutes (mostly waiting on Atlas + Render builds).

---

## Step 1 — Create a staging MongoDB Atlas cluster

1. Atlas > Projects > (your project) > Database > **Build a Database**.
2. Choose **M0 Free** tier. Name the cluster something like `gifiti-staging`.
3. Provider/region: same provider as prod, same or nearby region (latency to
   Render Oregon shouldn't matter at MVP scale).
4. Database Access > **Add New Database User**:
   - Username: `gifiti-staging-app`
   - Password: generate a strong one; save it temporarily — you'll paste it
     into Render in step 4.
   - Role: `readWrite` on database `gifiti` only (not Atlas admin).
5. Network Access > **Add IP Address** > `0.0.0.0/0` (Render uses dynamic
   egress IPs; Atlas IP allowlist isn't your security boundary — auth is).
6. Cluster > Connect > Drivers > copy the SRV connection string. It looks
   like:
   `mongodb+srv://gifiti-staging-app:<password>@gifiti-staging.xxxxx.mongodb.net/gifiti?appName=gifiti-staging`
7. Replace `<password>` with the one you generated. Save the full URI for
   step 4 (this is `MONGODB_URI`).

## Step 2 — Create a staging R2 bucket

1. Cloudflare Dashboard > R2 > **Create bucket**.
2. Name: `gifiti-user-uploads-staging`.
3. Location: same as prod bucket (or "Automatic").
4. Once created, open the bucket > **Settings** > note the S3 API endpoint:
   `https://<account-id>.r2.cloudflarestorage.com` — same account-id as prod.
   This is `R2_ENDPOINT`.
5. R2 > **Manage R2 API Tokens** > **Create API Token**:
   - Name: `gifiti-staging-app`
   - Permissions: **Object Read & Write**
   - Specify bucket: `gifiti-user-uploads-staging` only (do NOT grant
     account-wide access — minimum scope per `CLAUDE.md` security checklist).
   - TTL: leave default; you can rotate later.
   - Copy `Access Key ID` and `Secret Access Key` immediately — Cloudflare
     shows the secret exactly once. These are `R2_ACCESS_KEY_ID` and
     `R2_SECRET_ACCESS_KEY`.
6. Bucket > **Settings** > **Public access** > enable a public URL (or attach
   a custom subdomain like `staging-uploads.ggifiti.com`). Copy the
   `pub-<hash>.r2.dev` URL — this is `R2_PUBLIC_URL`.

## Step 3 — Apply the `render.yaml` blueprint

1. Make sure the branch containing `render.yaml` is merged to `main` (Render
   reads the blueprint from the branch you point it at; `main` is the
   default).
2. Render Dashboard > **Blueprints** > **New Blueprint Instance**.
3. Connect the `gifiti-backend` repo.
4. Render parses `render.yaml` and shows ONE service to create:
   `gifiti-backend-staging`. (Prod is intentionally not in the blueprint.)
5. Confirm creation. Render will start a first build that WILL fail (no env
   vars set yet). That's expected — the build itself succeeds; the container
   crashes at startup because Spring Boot can't find `MONGODB_URI` etc.

## Step 4 — Populate env vars in the staging service

Render Dashboard > `gifiti-backend-staging` > **Environment** > **Edit**.

For each row below, paste the value. The blueprint already created the keys;
you only fill values.

| Key | Value guidance |
|---|---|
| `MONGODB_URI` | The full SRV string from Step 1.7 (with password substituted in). |
| `JWT_SECRET` | Run `openssl rand -base64 32` locally. NEW secret — do not reuse the prod one. |
| `CORS_ALLOWED_ORIGINS` | Your staging frontend URL, e.g. `https://staging.ggifiti.com`. Comma-separate if multiple. |
| `RESEND_API_KEY` | Either a separate Resend API key for staging, or the prod key with a staging-only sending domain. Prefer separate keys. |
| `MAIL_FROM` | E.g. `hello@staging.ggifiti.com` (must match a Resend-verified domain). |
| `GOOGLE_CLIENT_ID` | A separate OAuth client in Google Cloud Console with the staging redirect URI configured. Do NOT reuse the prod client (its redirect URIs won't match). |
| `APP_BASE_URL` | Your staging frontend URL, e.g. `https://staging.ggifiti.com`. Used in email links. |
| `APP_COOKIE_DOMAIN` | The staging cookie domain, e.g. `.staging.ggifiti.com`. Leave empty if cookies are scoped to the Render `*.onrender.com` host. |
| `R2_ACCESS_KEY_ID` | From Step 2.5. |
| `R2_SECRET_ACCESS_KEY` | From Step 2.5. |
| `R2_ENDPOINT` | From Step 2.4. |
| `R2_BUCKET_NAME` | `gifiti-user-uploads-staging`. |
| `R2_PUBLIC_URL` | From Step 2.6. |

The following are pre-set by the blueprint and should NOT be overridden in
staging: `SPRING_PROFILES_ACTIVE=staging`, `PORT=8080`, `SWAGGER_ENABLED=true`,
`APP_COOKIE_SECURE=true`, `APP_COOKIE_SAME_SITE=Lax`.

Click **Save**. Render redeploys automatically.

## Step 5 — Confirm staging boots and is healthy

1. Watch the deploy logs in Render Dashboard until status is **Live**.
2. Open the staging URL Render assigns (e.g.
   `https://gifiti-backend-staging.onrender.com`).
3. Hit `/actuator/health` — expect `{"status":"UP"}`.
4. Smoke checks (do these manually for now; QA Engineer owns the formal
   smoke suite later):
   - Hit Swagger UI at `/swagger-ui/index.html` — expect 200, since
     `SWAGGER_ENABLED=true` in staging.
   - Run an end-to-end signup/login from the staging frontend (after you
     point the frontend's `VITE_API_URL` at the staging backend).
   - Upload a small image to verify R2 is wired correctly (this is the
     regression that motivated wanting staging in the first place — F-009).

## Step 6 — Document the staging URL

Note the staging URL Render assigns (e.g.
`https://gifiti-backend-staging.onrender.com`). Save it to your password
manager / project notes alongside the prod URL
(`https://gifiti-backend-za3h.onrender.com`).

## What's NOT done by this runbook (deferred to later steps)

- **GitHub Actions deploy workflows** (`deploy-staging.yml`,
  `deploy-prod.yml`): a separate task. Auto-deploy from the Render side is
  what fires staging deploys today. The deploy workflows give us extra
  control (gating, manual prod promotion, deploy hooks) and will be added
  in their own PR.
- **Adopting prod into the blueprint:** prod stays manually-managed for now.
  Adopt it later when the prod-deploy workflow lands.
- **Disabling Render auto-deploy on prod:** related to the above. Today,
  prod auto-deploys on every merge to `main`. That changes when the manual
  deploy workflow exists.
- **Staging frontend:** without a staging frontend pointed at this staging
  backend, end-to-end smoke testing in staging is API-level only. Frontend
  staging is out of this backend repo's scope.
- **Separate observability project for staging:** routes to Backend
  Architect / Security Engineer; not DevOps scope.
