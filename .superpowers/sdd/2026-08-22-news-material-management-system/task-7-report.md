# Task 7 Report: Hardening and Delivery Documentation

## Status

DONE

The delivery documentation now describes the implemented API and operational contracts, a five-minute nontechnical demo, and the current production boundaries. A defect found during the required pre-edit audit was reproduced with a regression test and fixed. The committed branch also passes a full packaged-application test and a clean-checkout package build.

## Pre-edit audit

The audit began from clean branch `codex/implementation` at `acde9e9` and covered Git status, history, tracked artifacts, application routes, frontend request types, service boundaries, security configuration, error mapping, runtime configuration, migrations, scripts, Compose, E2E coverage, and both desktop and mobile UI paths.

Findings:

- No user-authored changes were present before Task 7 edits.
- No source-content, authorization-header, session-token, presigned-URL, or AI-key logging was found.
- No real AWS, OpenAI, GitHub, private-key, or application secrets were found.
- Generated frontend, Maven, Playwright, and local-data outputs are ignored and were not tracked. The Maven wrapper JAR is the expected tracked bootstrap artifact.
- Flyway migrations are contiguous and ordered `V1` through `V4`; `V4` creates the required `pg_trgm` extension.
- The frontend methods and field names agree with the implemented API routes and response contracts exercised by the packaged E2E.
- No roles, teams, Kafka, native application, deletion workflow, vector search, media editor, or provenance graph entered the implementation.
- `docs/design.md` was accidentally executable. Its mode was corrected from `100755` to `100644`.
- Upload-intent metadata accepted a missing file element and unsafe or database-oversized filename/MIME values. A null element caused an observed server-side null dereference instead of a stable client error.
- The production npm audit was clean. The full audit initially reported two high-severity development-only findings through `@playwright/test` 1.55.0 (`GHSA-7mvr-c777-76hp`). The exact development dependency was safely upgraded to 1.62.1; both audits are now clean.
- The Java runtime dependency graph resolves successfully. This repository does not configure a Java CVE database scanner, so this report does not claim zero Java vulnerabilities.

Marker scans found no product `TODO`, `FIXME`, placeholder endpoint, or targeted `XXX` markers. One broad initial `XXX` match was only encoded package-lock integrity data; the source-only retry was clean.

## RED / GREEN defect evidence

### RED

Added `rejectsMissingUnsafeOrOversizedFileMetadataWithStableClientError` to `UploadIntentServiceTest` before changing production code. The focused test failed with:

```text
Cannot invoke "UploadFileSpec.sizeBytes()" because "f" is null
```

This demonstrated that malformed upload metadata escaped validation and produced an internal failure.

### GREEN

`UploadIntentService.create` now validates every file element before dereferencing it and rejects:

- null file elements;
- blank filenames or media types;
- control characters in filenames or media types;
- filenames longer than the schema's 255-character limit;
- media types longer than the schema's 160-character limit.

Every case returns status 400 with stable code `INVALID_UPLOAD_REQUEST`. The focused test then passed: 1 test, 0 failures, 0 errors.

## Files

- `docs/api.md`: exact session and CSRF flow; project, TEXT, upload-intent, FILE, current, history, version, search, download, and AI-retry request/response contracts; permissions; the material/content union; ordered FILE selectors; required and optional fields; stable errors; current-only search; idempotency; version conflicts. All example identifiers and values are synthetic.
- `docs/demo-script.md`: five-minute local packaged-app demo with the exact demo accounts, project creation, TEXT and FILE multi-upload, AI success/failure isolation, search, versions/history, second-user permissions, expected visible results, and a mobile viewport checklist.
- `docs/operations.md`: local, packaged, Compose, and cloud profiles; environment matrix; PostgreSQL, `pg_trgm`, Flyway, S3, CORS, IAM, presigning, OpenAI privacy and retry behavior, health/logging/metrics, orphan limitations, backup/restore, rollback, troubleshooting, and known non-production limitations.
- `README.md`: exact documentation links and the shortest documented local/package paths.
- `frontend/package.json` and `frontend/package-lock.json`: exact Playwright development dependency upgrade from 1.55.0 to 1.62.1.
- `src/main/java/com/example/newsmaterial/upload/UploadIntentService.java`: stable upload metadata validation.
- `src/test/java/com/example/newsmaterial/upload/UploadIntentServiceTest.java`: regression coverage for missing, unsafe, and oversized metadata.
- `docs/design.md`: documentation file mode corrected to non-executable.

## Verification evidence

### Complete repository workflow

`./scripts/test-all.sh` completed successfully after all code and dependency changes:

1. locked `npm ci`: passed;
2. TypeScript type check: passed;
3. ESLint with zero warnings: passed;
4. Vitest: 3 files, 29 tests passed;
5. Vite production build: passed;
6. Maven verify/package: 63 tests, 0 failures, 0 errors, 0 skipped;
7. packaged-JAR Chromium Playwright journey: 1 test passed.

The E2E starts the packaged Spring Boot JAR and covers login, generated project creation, TEXT v1/v2 and history, current-only search, ordered two-file upload, file download, image-specific AI completion, isolated AI failure with the material remaining usable, AI-derived search, second-user view permissions, hidden edit controls, and server-enforced 403 on non-owner version creation.

### Package inspection

The verified executable JAR had SHA-256:

```text
c3213096e9e7cd6c460592e4aef61771d0a589e112df7f37064ee96947b1c7bb
```

`jar tf` confirmed:

- Flyway migrations `V1` through `V4`;
- packaged SPA `index.html` and hashed JavaScript/CSS assets;
- Spring Boot runtime libraries.

No `.env`, `node_modules`, Playwright report, test result, frontend source, or backend test source was present in the JAR. The first inspection attempt used unavailable `unzip`; the equivalent JDK `jar` inspection then completed successfully. This was an environment-tool retry, not a product failure.

### Dependency and repository hygiene

- `npm audit --omit=dev`: 0 vulnerabilities.
- full `npm audit`: 0 vulnerabilities after the Playwright upgrade.
- Maven runtime dependency-tree resolution: successful.
- `git diff --check`: passed.
- secret and sensitive-log scan: no matches.
- generated-artifact and executable-bit audit: passed after the design-doc mode correction.
- package-lock and install emitted transitive deprecation notices for `whatwg-encoding` and the ESLint support line; neither audit reports a vulnerability.

## Fresh-checkout proof

A disposable clone of the committed `codex/implementation` branch was created at:

```text
/tmp/news-material-task7-fresh.Nxvi98/repo
```

Before the build it contained none of the ignored project-local prerequisites: `.tools`, `target`, `frontend/node_modules`, `frontend/dist`, or `.playwright-browsers`.

Following the README package command, `./mvnw package`, succeeded from that clone. The build:

- installed Node 22.20.0 and npm 10.9.3 under the clone's `target/frontend-tooling` directory;
- completed locked `npm ci` with 0 audited vulnerabilities;
- passed 29 frontend tests and the production build;
- passed 63 backend tests;
- produced the executable JAR;
- completed with Maven `BUILD SUCCESS` in 1 minute 5 seconds.

This proves the documented package path does not rely on ignored files or globally installed Node/npm. It did use the host Maven/npm download caches and network access where cache misses occurred, so it is not evidence of an offline build.

## Design acceptance evidence

- Project creation and access by any authenticated user: backend and packaged E2E.
- TEXT material creation, complete immutable v1/v2 snapshots, current view, and history: backend, frontend, and packaged E2E.
- FILE material with an ordered multi-file manifest, direct upload intents, confirmation, download, and full snapshots: backend contract/storage tests and packaged E2E.
- Owner-only version creation with authenticated-user viewing: authorization tests and second-user packaged E2E.
- Search over current versions only, including structured fields and AI-derived image text/tags: repository/service tests and packaged E2E.
- Per-image AI analysis with retryable failure isolation: worker/provider tests and packaged E2E.
- Local credential-free demo and executable delivery: complete workflow plus fresh-checkout package proof.
- Responsive mobile browser layout: automated viewport coverage and the documented manual checklist. Real-device mobile verification remains manual and is not claimed here.

## Infrastructure boundary and manual next step

Docker, a live PostgreSQL instance, a live S3-compatible service, and a live OpenAI account were unavailable in this environment. Therefore:

- Compose topology, health gates, private-bucket initialization, adapter wiring, configuration, migrations, and storage contracts were verified deterministically in tests;
- no live Compose, PostgreSQL, S3, or OpenAI success claim is made;
- the next manual release step is to follow `docs/operations.md`: start Docker Desktop with WSL integration, run the Compose profile, verify health, create/upload/download a multi-file material, confirm `pg_trgm`, then separately exercise the cloud profile against a private staging bucket and a restricted OpenAI project key.

## Self-review

- Documentation values were copied from implemented commands, configuration, controllers, DTOs, frontend behavior, and tests, not from aspirational design values.
- API examples use synthetic UUIDs, filenames, tags, locations, accounts, and content.
- The login CSRF exception, cookie flow, conflict behavior, AI retry states, and current-only search rule are explicit.
- S3 remains private; download and upload authorization remains server-mediated through short-lived presigned URLs.
- AI privacy documentation explicitly states that configured image bytes are sent to OpenAI in the cloud profile; it does not imply local processing.
- No scope was added for roles, teams, Kafka, native apps, delete, vector search, media editing, or cross-material provenance.
- No generated artifacts, credentials, or environment files were committed.

## Commits

- `55b3164 fix: harden upload validation and delivery`
- This evidence report is committed separately immediately after it is written.

## Concerns and known limits

- Real-device mobile behavior requires the documented manual device check.
- Live PostgreSQL, S3/MinIO, Docker Compose, and OpenAI integrations require the documented manual staging verification.
- Confirmed-but-never-completed upload intents can leave stale intent rows or orphaned objects; no cleanup worker exists in this scope. Operations includes an audit procedure and manual response.
- The application currently relies on standard Spring logs and health information; it has no custom correlation IDs, business metrics, rate limiting, malware scanning, or production alert rules.
- Login is intentionally exempt from CSRF so a browser can establish the first session; authenticated state-changing routes require the CSRF cookie/header pair.
- No Java CVE scanner is configured, so Java vulnerability status is not asserted beyond successful dependency resolution and tests.

## Fix Round 1

### Findings addressed

1. Material title, location, tag items, and idempotency keys previously reached persistence without matching the schema's length limits. Blank tags were silently removed. The service now normalizes and validates before fingerprinting, lookup-dependent work, or persistence: title 240, location 240, every tag nonblank/80, and normalized idempotency key nonblank/255. TEXT create, FILE create, and the shared TEXT/FILE version path return `400 VALIDATION_ERROR` for invalid values.
2. Project title was inspected separately. `ProjectRequest` already enforces nonblank/160 and matches both `ReportingProject` and Flyway `V1`; no project code change was required.
3. The explicit `GET /materials/{id}/versions/{number}` route derived `historical` from whether the selected version was current. It now always returns `historical:true` and `canEdit:false`; only `GET /materials/{id}` is the editable current route.
4. Idempotency fingerprints used raw request strings while persistence used trimmed strings. Keys, title, location, tags, TEXT body, and FILE notes are now normalized once and the same values feed fingerprinting and persistence. Duplicate normalized tags keep the first position.
5. Compose previously exposed only a subset of documented overrides. The Compose file, safe `.env.example`, deterministic contract test, and operations guide now agree on credentials, server port, database settings, S3 settings, OpenAI settings, worker settings, and upload limits. The guide separates Compose substitution inputs from direct application/cloud environment.
6. API documentation now states that filename and MIME type are both nonblank, control-free, and length-limited. Demo documentation distinguishes unit-level `matchMedia` navigation coverage from manual narrow-layout and real-device checks. Operations now exposes the missing Java CVE-scanner limitation.

No roles, teams, Kafka, native app, delete workflow, vector search, media editor, or provenance feature was added.

### RED evidence

The new focused command was:

```bash
./mvnw -Dskip.frontend=true   -Dtest=MaterialInputValidationTest,TextMaterialLifecycleTest,DockerComposeContractTest test
```

Before production/configuration changes it ran 12 tests with 8 expected failures:

- four input-limit tests received `404` or `422` instead of stable `400`;
- a whitespace-variant create used a second Material ID instead of replaying;
- a whitespace-variant version replay returned `409`;
- the explicit current-version history route returned `historical:false`;
- the Compose app environment lacked `SERVER_PORT` and the documented override mappings.

These failures directly reproduced each reported behavior rather than failing on test setup.

### GREEN evidence

- `MaterialInputValidationTest,TextMaterialLifecycleTest`: 10 tests, 0 failures/errors.
- `DockerComposeContractTest`: 2 tests, 0 failures/errors.
- The exact Compose contract asserts every `.env.example` key, credential mapping, browser/internal endpoint, app/health port, analysis setting, and upload setting.
- The project title/schema inspection confirmed the existing 160-character request/entity/migration agreement.

### Full verification

A fresh `./scripts/test-all.sh` completed successfully after all changes:

1. locked frontend install: 0 audit vulnerabilities;
2. TypeScript type check: passed;
3. ESLint with zero warnings: passed;
4. frontend unit tests: 29/29;
5. production frontend build: passed;
6. backend verify/package: 71/71, 0 failures/errors/skips;
7. packaged-JAR Chromium journey: 1/1 passed in 6.5 seconds.

The executable JAR SHA-256 was:

```text
a768bfec964a9b856c98a2487de6564640147b414e14e4594573c994f4ca0c73
```

JAR inspection again confirmed Flyway `V1`–`V4`, SPA `index.html`, and hashed JS/CSS assets. `npm audit --omit=dev` returned 0 vulnerabilities. `git diff --check` and the tracked-file secret scan were clean.

### Fresh-checkout evidence

Committed fix `0129ac7` was cloned without hardlinks to:

```text
/tmp/news-material-task7-fix1-fresh-0129ac7-a/repo
```

Before building, the clone had no `.tools`, `target`, `frontend/node_modules`, `frontend/dist`, or `frontend/.playwright-browsers`. Following README command `./mvnw package` installed project-local Node 22.20.0/npm 10.9.3, passed 29 frontend tests, passed 71 backend tests, built the SPA, and produced the executable JAR with `BUILD SUCCESS` in 1 minute 6 seconds.

The build used the host Maven/npm download caches and would require network access for cache misses; it is not an offline-build claim. An initial harness attempt lost a shell-local temporary-path variable at the permission boundary and tried `/repo`; it failed before cloning or changing project state. Retrying with the explicit safe `/tmp` path above succeeded. This was an orchestration issue, not a product failure.

### Files

- `MaterialService.java`: normalized validation, fingerprint/persistence alignment, and explicit historical-route semantics.
- `MaterialInputValidationTest.java`: API regressions for TEXT/FILE creation, shared TEXT/FILE version requests, and every material mutation's idempotency key.
- `TextMaterialLifecycleTest.java`: current-version historical-route and create/version idempotency normalization regressions.
- `docker-compose.yml`, `.env.example`, `DockerComposeContractTest.java`: exact configurable Compose contract.
- `docs/api.md`, `docs/demo-script.md`, `docs/operations.md`: exact limits, normalization, route, mobile-evidence, environment, and Java-audit boundaries.

### Self-review

- Validation occurs before database-width failures and before FILE intent verification can mask malformed common metadata.
- Normalized values are fingerprinted and persisted from the same local values.
- Key normalization happens before idempotency lookup and storage.
- Tag order remains meaningful; only exact duplicates after trimming are removed.
- Existing content semantics are preserved: TEXT body is trimmed/nonblank and FILE notes are trimmed/nullable.
- Version history retains the identical Material/Version snapshot layout while the explicit route is always read-only.
- Compose fallback secrets remain demo-only and the documentation tells operators to replace them.
- Live Docker/PostgreSQL/MinIO/S3/OpenAI and real-device mobile success are still not claimed.

### Commits

- `0129ac7 fix: enforce stable material contracts`
- This report append is committed separately.

### Remaining concerns

The existing delivery boundaries remain: live infrastructure and real-device mobile need the documented manual checks; no Java CVE scanner is configured; upload-orphan cleanup and custom production observability remain out of scope and explicitly documented.
