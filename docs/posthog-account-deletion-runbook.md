# Account Deletion Runbook — PostHog (LGPD Art. 18 VI)

> **Audience:** anyone who needs to execute a user's deletion request that touches PostHog. Optimize for the case where future-you (or a teammate) reads this under time pressure.
>
> **Trigger:** a user requests account deletion via support email, in-app deletion flow, ANPD demand, or any LGPD Art. 18 VI request.
>
> **Legal basis:** LGPD Art. 18 VI (right to deletion of personal data processed under consent or contract). DPA with PostHog Inc. signed 2026-05-07 establishes processor relationship under LGPD Art. 33 + 46.
>
> **Status:** **Track 1 (manual)** — operational runbook executed by hand. Track 2 (engineered automation that triggers on `AccountDeletionService` calls) is deferred per security-findings.md F-3.
>
> **SLA:** complete all steps within **15 calendar days** of receiving the request (ANPD interpretation of "without undue delay").
>
> **Last reviewed:** 2026-05-07

---

## Pre-execution checklist (verify BEFORE you start)

- [ ] Request is genuine. Verify identity by replying to the email-of-record on the user's account; do NOT act on a forwarded request without confirming.
- [ ] Request is for full deletion, not partial (e.g., "remove my email but keep my wishlists" requires different handling).
- [ ] You have access to:
  - Production MongoDB credentials (read + delete permissions on `gifiti` database).
  - PostHog Personal API Key with `cohort:write` and `person:write` scopes ([create at posthog.com/settings/user-api-keys](https://posthog.com/settings/user-api-keys)).
  - This repo's audit-log location: **TODO — fill in path** (e.g., `s3://gifiti-audit/deletions/`, dedicated `deletions` Mongo collection, or third-party tool). If audit log doesn't exist yet, create one before executing — see "Audit log requirements" below.
- [ ] PostHog project ID for production: **TODO — fill in** (find at posthog.com → Project Settings → top of page).

---

## Step 1 — Locate the user's `distinctId` in PostHog

PostHog identifies users by `distinctId`, not by email. In gifiti-backend, the `distinctId` is the user's MongoDB `_id` (24-char hex, per Architect Decision I in ADR 0007).

**To get the `distinctId`:**

```bash
# Query MongoDB for the user record by email.
# Replace <user-email> with the email from the deletion request.
# Replace <mongo-uri> with the production MONGODB_URI.

mongosh "<mongo-uri>" --quiet --eval '
  db.users.findOne(
    { email: "<user-email>" },
    { _id: 1, email: 1, displayName: 1, createdAt: 1 }
  )
'
```

**Record the output for the audit log:**
- `_id` (this is the `distinctId`)
- email
- displayName (may be the email-prefix fallback if user never set one)
- createdAt

**If the query returns null:** the user's account has already been deleted from MongoDB (or the email-of-record changed). PostHog deletion still applies — but you'll need to find the `distinctId` in PostHog directly via the email property:

```bash
# PostHog person search by email (PostHog Personal API Key required).
# Replace <posthog-api-key> and <project-id>.

curl -sS -G "https://us.i.posthog.com/api/projects/<project-id>/persons/" \
  -H "Authorization: Bearer <posthog-api-key>" \
  --data-urlencode 'search=<user-email>' | jq '.results[].distinct_ids'
```

If both return empty, document that in the audit log: "no PostHog data to delete; deletion request closed at MongoDB level only."

---

## Step 2 — Delete the user's PostHog person record

PostHog's deletion-by-distinctId API removes the person record AND all events keyed to that `distinctId`. This is the load-bearing call.

**API endpoint:** `DELETE /api/projects/<project-id>/persons/<person-id>/`

The `<person-id>` is PostHog's internal ID, NOT the `distinctId`. Get it via the search call from Step 1's null-fallback path:

```bash
# Get PostHog's internal person ID for this distinctId.
# Replace <distinct-id> with the value from Step 1.
PERSON_ID=$(curl -sS -G "https://us.i.posthog.com/api/projects/<project-id>/persons/" \
  -H "Authorization: Bearer <posthog-api-key>" \
  --data-urlencode "distinct_id=<distinct-id>" \
  | jq -r '.results[0].id')

echo "Person ID: $PERSON_ID"
```

If `PERSON_ID` is `null` or empty, the person never had any events captured — skip to Step 4 (the user's MongoDB record may still need deletion).

```bash
# Delete the person record + all associated events.
# Replace <project-id> and <posthog-api-key>.
# This is IRREVERSIBLE.

curl -sS -X DELETE \
  -H "Authorization: Bearer <posthog-api-key>" \
  "https://us.i.posthog.com/api/projects/<project-id>/persons/$PERSON_ID/"
```

**Expected response:** HTTP 204 No Content. Empty body.

**Verify deletion:**

```bash
# Re-query — should return empty.
curl -sS -G "https://us.i.posthog.com/api/projects/<project-id>/persons/" \
  -H "Authorization: Bearer <posthog-api-key>" \
  --data-urlencode "distinct_id=<distinct-id>" \
  | jq '.results | length'
# Expected output: 0
```

**If verification returns 1 instead of 0:** retry the delete after 60 seconds (PostHog's deletion is asynchronous on their side). If still 1 after a second retry, escalate by emailing PostHog support (`hey@posthog.com`) with the `<distinct-id>` and request manual processor-level deletion.

---

## Step 3 — Delete session replay recordings (if applicable)

**As of 2026-05-07: gifiti-backend does NOT enable session replay.** Skip this step unless the frontend has since enabled `posthog.startSessionRecording()`.

If session replay is active, recordings are scoped per `distinct_id` and PostHog's person-deletion (Step 2) cascades to recordings — but verify explicitly:

```bash
# List session recordings for this distinct_id.
curl -sS -G "https://us.i.posthog.com/api/projects/<project-id>/session_recordings/" \
  -H "Authorization: Bearer <posthog-api-key>" \
  --data-urlencode "person_uuid=$PERSON_ID" \
  | jq '.results | length'
# Expected output: 0 (after Step 2)
```

If non-zero, delete each recording explicitly:

```bash
# Get the list of recording IDs.
curl -sS -G "https://us.i.posthog.com/api/projects/<project-id>/session_recordings/" \
  -H "Authorization: Bearer <posthog-api-key>" \
  --data-urlencode "person_uuid=$PERSON_ID" \
  | jq -r '.results[].id' \
  | while read RECORDING_ID; do
      curl -sS -X DELETE \
        -H "Authorization: Bearer <posthog-api-key>" \
        "https://us.i.posthog.com/api/projects/<project-id>/session_recordings/$RECORDING_ID/"
    done
```

---

## Step 4 — Delete the user's MongoDB data

**As of 2026-05-07: gifiti-backend has NO in-app account deletion endpoint or service method.** Verified via grep across `src/main/java/`. The only deletion code is `AccountLockoutService.deleteByEmail` (unrelated — it clears failed-login records, not user accounts). All deletion is manual via the steps below.

**This is a known gap.** Track 2 (engineered automation) will introduce a proper `AccountDeletionService` that wraps Steps 1–5 in a single transactional operation. Until Track 2 ships, follow the manual procedure below.

```bash
# Identify all collections that reference this userId.
# Replace <user-id> with the distinctId from Step 1.
mongosh "<mongo-uri>" --quiet --eval '
  const userId = "<user-id>";
  console.log("Wishlists:", db.wishlists.countDocuments({ ownerId: userId }));
  console.log("Items:",     db.wishlistItems.countDocuments({ ownerId: userId }));
  console.log("Reservations:", db.reservations.countDocuments({ reserverUserId: userId }));
  console.log("User record:", db.users.countDocuments({ _id: ObjectId(userId) }));
'
```

**Manual deletion in correct order (children first to avoid orphan-reference cleanup later):**

```bash
mongosh "<mongo-uri>" --quiet --eval '
  const userId = "<user-id>";
  const userObjectId = ObjectId(userId);

  // Order matters: delete leaves before nodes.
  print("Reservations:",  db.reservations.deleteMany({ reserverUserId: userId }).deletedCount);
  print("WishlistItems:", db.wishlistItems.deleteMany({ ownerId: userId }).deletedCount);
  print("Wishlists:",     db.wishlists.deleteMany({ ownerId: userId }).deletedCount);
  print("User record:",   db.users.deleteOne({ _id: userObjectId }).deletedCount);
'
```

**TODO — verify the collection list above is complete for current schema.** Check by running `db.runCommand("listCollections")` and reviewing whether any new collections (added in features after 007) reference user IDs.

**Also delete: Cloudflare R2 images.** Verified pattern from `R2StorageService.buildKey()` (2026-05-07): all of a user's images live under the prefix `users/<userId>/`, with subfolders `items/` and `wishlists/` and individual files named `<fileId>.<ext>`. Bulk-delete the prefix:

```bash
# Using AWS CLI configured for R2 endpoint (recommended for repeatable runs).
# Replace <r2-bucket-name>, <r2-endpoint>, <user-id>.
aws s3 rm "s3://<r2-bucket-name>/users/<user-id>/" \
  --recursive \
  --endpoint-url "<r2-endpoint>"

# Verify nothing remains under the prefix.
aws s3 ls "s3://<r2-bucket-name>/users/<user-id>/" \
  --recursive \
  --endpoint-url "<r2-endpoint>" | wc -l
# Expected output: 0
```

**TODO — fill in:** R2 bucket name + R2 endpoint URL. Find both in your Cloudflare R2 dashboard. The endpoint format is typically `https://<account-id>.r2.cloudflarestorage.com`.

---

## Step 5 — Audit log entry

**Required fields** (LGPD Art. 37 — operational records of processing):

```yaml
deletion_request_id: <uuid you generate>
received_at: <ISO 8601 timestamp from the request email>
executed_at: <ISO 8601 timestamp now>
user_email: <user's email>
user_distinct_id: <distinctId from Step 1>
user_object_id: <Mongo _id; same value typically>
data_deleted:
  posthog_person_record: true | false | not_present
  posthog_session_recordings: <count>
  mongo_users: <count>
  mongo_wishlists: <count>
  mongo_wishlist_items: <count>
  mongo_reservations: <count>
  r2_objects: <count>
executed_by: <your name + role>
notes: <any anomalies; e.g., "PostHog returned 204 on first try", or "user had no PostHog person record">
```

**Where this log lives:** **TODO — fill in.** Options:
- A dedicated `deletion_audit` MongoDB collection (recommended for solo operations; auditable, queryable, integrated).
- A versioned text file in a private S3 bucket (recommended if multiple operators).
- A row in a Google Sheet (acceptable interim if neither of the above is set up; export to durable storage quarterly).

**Retention:** keep the audit log entry for **at least 5 years** post-deletion (LGPD Art. 16 retention obligation for processing records, even after data subject's data is deleted).

---

## Step 6 — Confirm deletion to the user

Reply to the user's request email within the same business day:

> Subject: Your account deletion request — completed
>
> [User's name],
>
> Your gifiti account and all associated data have been deleted. This includes:
> - Your account record and all wishlists, items, and reservations.
> - Your event history in our analytics provider (PostHog Cloud US).
> - Cover images uploaded to your wishlists.
>
> Per LGPD Art. 18 VI, this deletion is irreversible. We retain a record of the deletion event itself for compliance purposes (5-year retention per LGPD Art. 16), but no personally identifiable information beyond your email and the timestamp.
>
> If you have questions about this deletion or wish to know what we retain about the deletion event, reply to this email.
>
> [Your name]
> gifiti

---

## Audit log requirements (one-time setup if not done)

If this is the first deletion request and no audit log mechanism exists, set one up before executing.

**Minimum viable: a dedicated MongoDB collection.**

```bash
mongosh "<mongo-uri>" --quiet --eval '
  db.createCollection("deletion_audit");
  db.deletion_audit.createIndex({ executed_at: -1 });
  db.deletion_audit.createIndex({ user_email: 1 });
  print("deletion_audit collection ready");
'
```

For each deletion, insert a document matching the schema in Step 5.

**Note:** the audit log itself must NOT contain personal data beyond what's strictly necessary for the audit (the email + execution timestamps). Do NOT log the user's wishlist contents, item descriptions, etc. — those were the data being deleted, not data to retain about the deletion.

---

## Escalation

- **PostHog deletion fails after 2 retries:** email `hey@posthog.com` with the `<distinct_id>`, your project ID, and the request timestamp. Reference the executed DPA.
- **Mongo deletion fails (replication lag, write concern issues):** verify the deletion succeeded on at least one secondary by running the count queries against a different replica set member. Re-run the deletion against the replica set primary.
- **R2 object deletion fails:** retry with the wrangler CLI or AWS S3 SDK pointing at the R2 endpoint. If still failing, document in the audit log with notes and retry the next business day.
- **Auditor or ANPD asks for proof:** the audit log entry plus the executed DPA (signed 2026-05-07) plus this runbook are the three documents to produce. The runbook lives at this path; the DPA is in `<TODO — fill in your DPA storage location>`; the audit log is in `<TODO — fill in your audit log location>`.

---

## Open follow-ups

This runbook is Track 1 (manual). Track 2 is automation that wires deletion into the in-app account deletion flow, so a user clicking "Delete my account" triggers all of Steps 1–5 automatically. Tracked in `specs/007-posthog-integration/security-findings.md` § F-3 Track 2. Revisit triggers:

- First real deletion request executed manually (validates this runbook against a real case).
- Any deletion request fails to complete within the 15-day SLA.
- Deletion request volume exceeds 1/month (manual process becomes a real operational tax).
- Any LGPD/ANPD adequacy update tightening the SLA.

---

**Last reviewed:** 2026-05-07 (initial draft, post-feature-007 ship)
