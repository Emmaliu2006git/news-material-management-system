# Operations Guide

This guide distinguishes the runnable local demo from production adapters. PostgreSQL, S3/MinIO, Docker, and OpenAI were not live-connected in the implementation environment; their configuration is covered by deterministic migration, adapter, and Compose contract tests. Perform the manual checks in this guide before any shared or production deployment.

## Runtime modes

| Mode | Command/profile | Database | File storage | Image analysis | Seed users |
| --- | --- | --- | --- | --- | --- |
| Development | `./scripts/dev.sh`; `local` | In-memory H2, schema generated, Flyway off | Local filesystem at `.local-data/storage` | Deterministic local provider; scheduler explicitly enabled by script | Emma and Alex |
| Packaged demo | JAR with `local` | In-memory H2 | Local filesystem, default OS temp path unless overridden | Deterministic local provider; pass scheduler flag | Emma and Alex |
| Browser E2E | `local,e2e` | In-memory H2 | `target/e2e-storage` | Local provider, fast polling, one automatic attempt | Emma and Alex |
| Compose demo | `demo` | PostgreSQL 17 + Flyway | Private MinIO bucket through S3 adapter | Local by default; scheduled | Emma and Alex |
| Cloud | base configuration, normally no demo/local profile | PostgreSQL + Flyway | Private AWS S3 | OpenAI by default; scheduled | None seeded |

Do not use the demo accounts or default Compose fallback passwords in a shared environment.

## Commands

### Development UI and API

```bash
./scripts/dev.sh
```

Open `http://127.0.0.1:5173`. The script performs a locked frontend install, starts the local backend on 8080, waits for health, then starts Vite with an API proxy.

### Packaged same-origin demo

```bash
./mvnw package
java -jar target/news-material-management-system-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=local \
  --app.analysis.scheduling-enabled=true
```

Open `http://127.0.0.1:8080`. The package embeds the built React application in the executable JAR.

### Full verification

```bash
./scripts/test-all.sh
```

This runs locked install, typecheck, lint, frontend tests/build, backend verify/package, Playwright browser install, and the packaged-JAR browser journey. It needs Java 21+, Node 22+, npm, and network/cache access for dependencies and Chromium.

### Compose demo

```bash
cp .env.example .env
# Replace every placeholder; do not commit .env.
docker compose up --build
```

Open `http://localhost:8080`. Stop without deleting data using `docker compose down`. Only use `docker compose down --volumes` when the named PostgreSQL and MinIO demo volumes are intentionally disposable.

## Compose substitution inputs

`docker-compose.yml` reads the following host/`.env` variables. The values shown are Compose defaults; copy `.env.example`, replace secrets, and keep `.env` out of Git.

| Compose input | Default | Container mapping / purpose |
| --- | --- | --- |
| `POSTGRES_PASSWORD` | `demo-only-postgres` | PostgreSQL password and app `SPRING_DATASOURCE_PASSWORD` |
| `MINIO_ROOT_USER` | `demo-minio-user` | MinIO root user and app S3 access key |
| `MINIO_ROOT_PASSWORD` | `demo-only-minio-password` | MinIO root password and app S3 secret |
| `MINIO_API_CORS_ALLOW_ORIGIN` | `http://localhost:8080` | Browser origin accepted by MinIO |
| `SERVER_PORT` | `8080` | App container port, published host port, and app health-check port |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://postgres:5432/news_material` | App JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | `news_material` | App database identity |
| `NEWS_MATERIAL_S3_BUCKET` | `news-material-private` | App bucket and MinIO bucket initialization |
| `AWS_REGION` | `us-east-1` | App S3 region |
| `NEWS_MATERIAL_S3_ENDPOINT` | `http://minio:9000` | App internal S3 endpoint |
| `NEWS_MATERIAL_S3_PRESIGNER_ENDPOINT` | `http://localhost:9000` | Browser-reachable signed URL endpoint |
| `NEWS_MATERIAL_S3_PATH_STYLE` | `true` | App S3 addressing mode |
| `NEWS_MATERIAL_AI_PROVIDER` | `local` | App analysis provider |
| `OPENAI_API_KEY` | empty | App provider credential; needed only when provider is `openai` |
| `OPENAI_BASE_URL` | `https://api.openai.com/v1` | App Responses API base |
| `OPENAI_VISION_MODEL` | `gpt-5-mini` | App image-capable model |
| `OPENAI_CONNECT_TIMEOUT` | `PT3S` | App provider connection timeout |
| `OPENAI_REQUEST_TIMEOUT` | `PT20S` | App provider request timeout |
| `ANALYSIS_LEASE_DURATION` | `PT5M` | App stuck-work reclaim lease |
| `APP_ANALYSIS_SCHEDULING_ENABLED` | `true` | App worker scheduler switch |
| `APP_ANALYSIS_MAX_ATTEMPTS` | `3` | App automatic attempt limit |
| `APP_ANALYSIS_BASE_BACKOFF` | `PT1M` | App retry backoff base |
| `APP_ANALYSIS_POLL_DELAY_MS` | `5000` | App worker poll delay |
| `APP_UPLOAD_MAX_FILES` | `10` | App files-per-request/snapshot limit |
| `APP_UPLOAD_MAX_FILE_BYTES` | `52428800` | App 50 MiB per-file limit |
| `APP_UPLOAD_MAX_TOTAL_BYTES` | `209715200` | App 200 MiB snapshot limit |
| `APP_UPLOAD_INTENT_TTL` | `PT15M` | App presign/intent lifetime |

Compose keeps these topology values fixed: app profile `demo`; PostgreSQL database/user `news_material`; PostgreSQL service host `postgres`; MinIO service endpoint/ports `minio:9000`, `9000`, and `9001`. The direct JDBC URL and username inputs may point the app at a different database, but they do not reconfigure the included PostgreSQL container. If `SERVER_PORT` changes, also set `MINIO_API_CORS_ALLOW_ORIGIN` to the resulting browser origin. Never use fallback credentials in a shared environment.

## Direct application and cloud environment

These variables/properties are read by Spring Boot when running the JAR/container directly. Spring Boot relaxed binding permits the uppercase `APP_...` names shown for `app.*` properties.

| Variable/property | Default | Required where | Purpose |
| --- | --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | none | Explicit for local/demo | Select runtime mode |
| `SERVER_PORT` | `8080` | Optional | HTTP port |
| `SERVER_SERVLET_SESSION_COOKIE_SECURE` | `true`; Compose demo overrides `false` | HTTP-only demo | Keep `true` behind HTTPS |
| `SPRING_DATASOURCE_URL` | PostgreSQL on localhost | Cloud/direct | JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | `news_material` | Cloud/direct | Database identity |
| `SPRING_DATASOURCE_PASSWORD` | `news_material` | Cloud/direct | Secret; use secret manager |
| `NEWS_MATERIAL_S3_BUCKET` | `news-material-private` | S3/MinIO | Private bucket |
| `AWS_REGION` | `us-east-1` | S3 | AWS SDK region |
| `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` | none | MinIO only; avoid static AWS keys | SDK credential-chain inputs |
| `NEWS_MATERIAL_S3_ENDPOINT` | empty | MinIO/custom S3 | Internal SDK endpoint |
| `NEWS_MATERIAL_S3_PRESIGNER_ENDPOINT` | falls back to S3 endpoint | MinIO/mobile | Browser-reachable signed URL endpoint |
| `NEWS_MATERIAL_S3_PATH_STYLE` | `false` | `true` for included MinIO | Addressing mode |
| `NEWS_MATERIAL_AI_PROVIDER` | `openai` | Set `local` for demos | `local` or `openai` |
| `OPENAI_API_KEY` | empty | OpenAI provider | Secret API credential |
| `OPENAI_BASE_URL` | `https://api.openai.com/v1` | Optional | Responses API base |
| `OPENAI_VISION_MODEL` | `gpt-5-mini` | Optional | Image-capable model |
| `OPENAI_CONNECT_TIMEOUT` | `PT3S` | Optional | Connection timeout |
| `OPENAI_REQUEST_TIMEOUT` | `PT20S` | Optional | Read/request timeout |
| `ANALYSIS_LEASE_DURATION` | `PT5M` | Optional | Reclaim timeout for stuck work |
| `APP_ANALYSIS_SCHEDULING_ENABLED` | `true` base; `false` local | Local hand-start | Scheduler switch |
| `APP_ANALYSIS_MAX_ATTEMPTS` | `3` | Optional | Automatic attempts |
| `APP_ANALYSIS_BASE_BACKOFF` | `PT1M` | Optional | Backoff base; delay is base × attempt |
| `APP_ANALYSIS_POLL_DELAY_MS` | `5000` | Optional | Worker poll delay |
| `APP_UPLOAD_MAX_FILES` | `10` | Optional | Files per request/snapshot |
| `APP_UPLOAD_MAX_FILE_BYTES` | `52428800` | Optional | 50 MiB per file |
| `APP_UPLOAD_MAX_TOTAL_BYTES` | `209715200` | Optional | 200 MiB per snapshot |
| `APP_UPLOAD_INTENT_TTL` | `PT15M` | Optional | Presign/intent lifetime |

Base/cloud defaults are `Secure=true`, `HttpOnly=true`, and `SameSite=Lax`, with framework forwarded-header handling. The `local` profile and included HTTP Compose demo explicitly set `Secure=false`; those overrides are for loopback/LAN demonstration only. Terminate HTTPS at a trusted proxy, pass standard forwarded headers, and leave the secure default enabled in shared deployments.

## PostgreSQL and Flyway

Flyway runs automatically outside the local profile. Hibernate uses `ddl-auto=validate`; it must not create or mutate the production schema.

Migration order is fixed and contiguous:

1. `V1__identity_and_projects.sql` — users and reporting projects.
2. `V2__materials_and_versions.sql` — stable materials, immutable snapshots, tags, idempotency.
3. `V3__uploads_and_files.sql` — upload intents, FileAssets, ordered version files.
4. `V4__search_and_analysis.sql` — `pg_trgm`, image analysis, current-version search documents and indexes.
5. `V5__enforce_material_current_version_ownership.sql` — composite unique/FK proving `current_version_id` belongs to the same Material; the FK is deferred so Material and v1 can be inserted atomically.

Before the first application startup, an administrator should enable the extension if the application role cannot:

```sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;
```

Verification:

```sql
SELECT extname, extversion
FROM pg_extension
WHERE extname = 'pg_trgm';

SELECT installed_rank, version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

Expected: one `pg_trgm` row and successful Flyway rows 1 through 5. Never edit an applied migration; add a new forward migration.

Recommended role separation:

- Migration identity: schema DDL plus access to create/use the approved extension.
- Runtime identity: DML on application tables/sequences only.
- If one identity is used for this demo, reduce it before production.

## One-time cloud account provisioning

Cloud/base mode seeds no users and the application exposes no registration, roles, or administrator endpoint. Build the JAR, configure the normal cloud PostgreSQL connection, then run the non-web `provision-user` profile once:

```bash
read -rp 'Username: ' NEWS_MATERIAL_PROVISION_USERNAME
read -rsp 'Password: ' NEWS_MATERIAL_PROVISION_PASSWORD
printf '\n'
export NEWS_MATERIAL_PROVISION_USERNAME NEWS_MATERIAL_PROVISION_PASSWORD
java -jar target/news-material-management-system-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=provision-user
unset NEWS_MATERIAL_PROVISION_USERNAME NEWS_MATERIAL_PROVISION_PASSWORD
```

The password must contain at least 12 characters. The procedure BCrypt-hashes it, inserts the account idempotently, prints only username/outcome, clears its mutable password buffer, closes the application, and never starts a public HTTP server. Re-running the same username reports `ALREADY_EXISTS` and does not change the password.

Prefer an interactive secret injection mechanism. The password environment variable supports non-interactive deployment tooling but must come from a secret manager and must never be placed in shell history, Compose files, logs, or source control. If an interactive console is available, omit `NEWS_MATERIAL_PROVISION_PASSWORD`; the runner prompts without echo. Remove both variables from the environment after completion. Account deletion, reset, roles, and self-registration remain out of scope.

## Private S3 or MinIO

The application generates random immutable object keys. Browsers PUT directly with a short-lived signed URL; the backend HEAD-verifies size, MIME type, and SHA-256 before finalization. Downloads use five-minute signed GET URLs. The AI worker reads image bytes through the backend S3 client.

The bucket must block public access. The application identity needs only the selected bucket/prefix:

- `s3:PutObject` for upload signing.
- `s3:GetObject` for download signing and AI reads.
- `s3:GetObjectAttributes` or `s3:HeadObject`-equivalent permission (normally `s3:GetObject`) for verification.
- No public ACL, bucket-policy mutation, bucket creation, or object deletion is used by the application.

Illustrative browser CORS policy; replace the origin exactly:

```json
[
  {
    "AllowedOrigins": ["https://newsroom.example.invalid"],
    "AllowedMethods": ["PUT", "GET", "HEAD"],
    "AllowedHeaders": ["Content-Type", "x-amz-checksum-sha256"],
    "ExposeHeaders": ["ETag", "x-amz-checksum-sha256"],
    "MaxAgeSeconds": 300
  }
]
```

Do not use `*` origins with credentials. For a phone demo, both the web URL and presigner endpoint must be reachable from the phone; `localhost` refers to the phone itself. Update MinIO CORS and `NEWS_MATERIAL_S3_PRESIGNER_ENDPOINT`, then restart Compose.

Enable S3 versioning or an equivalent object backup policy operationally. Application Material versions do not replace infrastructure backups.

## OpenAI image analysis

When `NEWS_MATERIAL_AI_PROVIDER=openai`, the worker reads the full image, base64-encodes it as a data URL, and sends it to the configured Responses API with a structured JSON schema. The application sends image bytes and a fixed prompt, not material notes or user credentials.

Before enabling it:

1. Obtain newsroom privacy/legal approval for the provider, region, retention, and data-use policy.
2. Use synthetic images until that approval is recorded.
3. Put `OPENAI_API_KEY` in a workload secret manager; never in Git, shell history, an image layer, or logs.
4. Restrict outbound network access to the approved endpoint.
5. Confirm the selected model accepts image input and structured output.
6. Monitor provider errors without logging request bodies, base64 images, authorization headers, or response content.

Default automatic behavior is three attempts. A failed attempt returns to `PENDING` with delays of one and two base intervals; the third failure becomes `FAILED`. A processing lease can be reclaimed after five minutes. Owner-triggered retry resets the counter to zero. Material creation remains committed and usable throughout.

## Health, logs, and metrics

Implemented health endpoint:

```http
GET /actuator/health
```

It is used by local scripts, Compose, Playwright, and the container health check. Default health details are not exposed. The application currently relies on standard Spring Boot logs and does not log request/response bodies, file bytes, credentials, sessions, OpenAI authorization, or signed URLs.

Current observability boundary:

- No custom request-correlation filter; the `requestId` in an error response is not yet propagated into logs.
- No custom counters for version conflicts, upload backlog, analysis backlog, or search failures.
- No metrics endpoint is explicitly exposed beyond the default health endpoint.
- Compose health proves process readiness only; it is not a complete external-dependency synthetic test.

Before production, add a redacted structured logging policy, log correlation, authenticated metrics export, alerts for database/storage/provider failures, and analysis queue age. These are operational hardening items, not hidden demo claims.

## Upload intents and orphan cleanup

The application persists upload intents and uses them once. It does not currently run the cleanup worker proposed in the design.

Consequences:

- Expired, unattached intent rows remain.
- Direct-upload objects that were never finalized can remain in S3/MinIO.
- Local filesystem bytes can outlive the in-memory H2 record after restart.
- The application has no DELETE endpoint and must not guess whether an object is safe to remove.

A read-only PostgreSQL audit query is:

```sql
SELECT ui.id, ui.object_key, ui.expires_at
FROM upload_intents ui
LEFT JOIN file_assets fa ON fa.upload_intent_id = ui.id
WHERE fa.id IS NULL
  AND ui.status = 'PENDING'
  AND ui.expires_at < CURRENT_TIMESTAMP
ORDER BY ui.expires_at;
```

Do not bulk-delete based only on age. A production cleanup job must re-check that no FileAsset references the intent, use a safety delay, delete the object idempotently, then record/delete the intent in a controlled transaction or reconciliation workflow. Until implemented, monitor and review orphans manually.

## Backup and restore

Back up PostgreSQL and object storage as one logical system.

Recommended minimum:

- Scheduled encrypted PostgreSQL backups with tested point-in-time recovery.
- S3 versioning plus lifecycle/replication appropriate to newsroom retention.
- Retention for Flyway history and configuration metadata.
- Restore drills that verify every restored FileAsset key exists and is downloadable.
- Separate protection for deployment secrets; never embed them in database dumps.

Example database backup without inline credentials:

```bash
pg_dump --format=custom --host=db.example.invalid --username=news_material_backup \
  --file=news-material.dump news_material
```

Supply authentication through IAM, an interactive prompt, or a protected credential mechanism.

Restore into an empty recovery database:

```bash
createdb --host=db-recovery.example.invalid --username=news_material_restore news_material_recovery
pg_restore --exit-on-error --single-transaction \
  --host=db-recovery.example.invalid --username=news_material_restore \
  --dbname=news_material_recovery news-material.dump
```

Then verify Flyway history, current-version foreign keys, FileAsset object existence, representative downloads, searches, and AI states before switching traffic. Database recovery alone is incomplete if object storage is missing.

## Deployment and rollback

1. Build and verify one immutable JAR/container image.
2. Back up PostgreSQL and confirm object-storage protection.
3. Validate `pg_trgm`, Flyway privileges, bucket privacy/CORS/IAM, secrets, and HTTPS cookies.
4. Run migrations once, then start application instances.
5. Check health, login, create/read in a synthetic project, signed upload verification, download, current-only search, and one synthetic AI analysis.
6. Keep the previous image available until the observation window closes.

Flyway migrations are forward-only. Do not run an older binary against a schema unless its compatibility is proven. If a release fails after a compatible additive migration, stop new writes and roll the application image back. If schema compatibility is not proven, keep traffic stopped and deploy a forward repair migration/application. Never manually remove Flyway rows or drop columns during an incident.

## Troubleshooting

| Symptom | Checks |
| --- | --- |
| Startup asks for OpenAI key | Use `local`, or set `NEWS_MATERIAL_AI_PROVIDER=local`; cloud default is OpenAI |
| Startup cannot reach PostgreSQL | Verify profile, JDBC URL, DNS, firewall, TLS parameters, and credentials |
| Flyway fails at V4 | Confirm managed PostgreSQL permits/preinstalls `pg_trgm` |
| Upload URL works on laptop but not phone | Replace browser-facing localhost with a reachable LAN/TLS endpoint and update CORS |
| Upload finalization returns 422 | Check intent ownership/expiry, exact byte count, MIME header, and SHA-256 header |
| Upload returns 413 | Read `/api/v1/capabilities`; respect the configured count, per-file, and total limits |
| AI remains PENDING locally | Start with `--app.analysis.scheduling-enabled=true` |
| AI becomes FAILED | Inspect provider reachability/timeouts without logging content; owner may retry |
| Search misses old wording | Expected: historical versions are excluded |
| Flyway fails at V5 | Confirm existing `current_version_id` values point to a version owned by the same material; repair data explicitly before retrying |
| Non-owner cannot see edit/retry | Expected owner-only action; all signed-in users still view/download |
| Browser test cannot launch | Run the full script so the pinned Playwright browser is installed |
| Compose unavailable in WSL | Enable Docker Desktop WSL integration and verify `docker info` inside the distribution |

## Known non-production limitations

- PostgreSQL, MinIO/S3, Docker Compose, and OpenAI were contract-tested but not live-started in the implementation environment.
- Desktop Chromium and emulated Pixel 7 run the packaged login/FILE-upload journey automatically. A real-device picker, permissions, camera/gallery integration, and LAN routing remain manual.
- No malware scanning; do not accept untrusted public uploads.
- No orphan cleanup worker, retention administration, or delete endpoint.
- No rate limiting, account administration, password reset, SSO, audit-event export, or custom observability.
- Login is the one CSRF-exempt mutation; all authenticated mutations are protected.
- Local H2 data resets on restart, while local files may remain.
- Local AI output is deterministic demo metadata, not content understanding.
- Search is keyword/filter search over current versions, not semantic/vector search.
- No Java CVE database scanner is configured; run an organization-approved scanner against the resolved Maven runtime graph and container image before release.
- No teams, roles, Kafka, native app, notifications, collaborative branches/merges, lineage after re-upload, or media editor.

### Mobile/LAN note

The browser checksum path first uses Web Crypto and falls back to pinned `@noble/hashes` SHA-256 when `crypto.subtle` is absent or unusable on an insecure LAN origin. This makes plain-LAN demos functional; HTTPS or a trusted tunnel is still recommended for secure sessions and real reporting data. Configure browser-reachable presigned endpoints and exact CORS origins either way.
