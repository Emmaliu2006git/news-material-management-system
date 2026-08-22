# Task 3 Report: Direct File Uploads and Immutable File Versions

**Status:** DONE

**Base commit:** `1eb26dc`

## Implemented

- Added authenticated, owner-bound, expiring, one-use upload intents with configurable file-count, per-file, total-size, MIME-family, and expiry limits.
- Added stable `413` and `415` API errors for size and media-type validation.
- Added a production S3 storage adapter using AWS SDK v2 pre-signed PUT/GET URLs and HEAD-based verification.
- Added an opt-in local-profile storage adapter and authenticated controlled PUT/GET routes for credential-free demonstrations.
- Added atomic FILE material creation after storage verification, with ordered immutable file assets, version links, current-version updates, and intent consumption.
- Extended material version creation so owners can retain/reorder current files and append newly uploaded files without modifying historical snapshots.
- Added authenticated file download URL generation for every user.
- Added the FILE response discriminator and nullable per-file `imageAnalysis` placeholder without exposing S3 object keys.
- Added the coherent PostgreSQL V3 migration; local H2 continues using JPA `create-drop`.
- Preserved TEXT material behavior, idempotency semantics, and optimistic `expectedCurrentVersion` conflict handling.

## Changed Files

- `pom.xml`
- `src/main/java/com/example/newsmaterial/material/api/MaterialApi.java`
- `src/main/java/com/example/newsmaterial/material/domain/MaterialVersion.java`
- `src/main/java/com/example/newsmaterial/material/service/MaterialService.java`
- `src/main/java/com/example/newsmaterial/upload/*`
- `src/main/java/com/example/newsmaterial/storage/*`
- `src/main/java/com/example/newsmaterial/file/*`
- `src/main/resources/application.yml`
- `src/main/resources/application-local.yml`
- `src/main/resources/db/migration/V3__uploads_and_files.sql`
- `src/test/java/com/example/newsmaterial/upload/UploadIntentServiceTest.java`
- `src/test/java/com/example/newsmaterial/file/FileMaterialApiTest.java`
- `src/test/java/com/example/newsmaterial/file/FileVersionHistoryTest.java`
- `src/test/java/com/example/newsmaterial/storage/S3StorageAdapterTest.java`

## TDD Evidence

### Baseline

- `./mvnw test`: 15 tests passed before Task 3 changes.

### Initial RED

- Added eight upload-intent, FILE material, and FILE version/history behavioral tests before production code.
- Focused run failed 8/8 as expected because the new routes returned HTTP 404.

### Initial GREEN

- Focused Task 3 tests passed 8/8 after implementing the requested behavior.
- A clean full-suite run exposed a backward-compatibility compile failure for the prior `TextVersionRequest` test contract; a compatibility type and service overload restored it.
- The full suite then exposed transaction proxy bypass in that compatibility overload; adding its transaction boundary made the focused concurrency tests pass 2/2.

### Regression RED/GREEN

- Added `S3StorageAdapterTest` after self-review found that generic S3 HEAD 404 responses were mapped to a service error.
- RED: expected `422 UPLOAD_NOT_VERIFIED`, received `503 STORAGE_UNAVAILABLE`.
- GREEN: the focused S3 regression test passed 1/1 after mapping all S3 404 responses to verification failure.

### Final Verification

- `git diff --check && ./mvnw clean verify`
- Result: 24 tests, 0 failures, 0 errors, 0 skipped.
- Build result: `BUILD SUCCESS`; executable JAR produced.

## Self-Review

- Production file bytes bypass the normal backend; only the local profile exposes controlled byte-transfer routes.
- Existing session authentication and CSRF policy cover upload intents and local PUT/GET routes.
- Upload intents cannot be used by another user, after expiry, or after successful consumption.
- Object keys and file assets are immutable; version-file rows preserve exact ordered snapshots.
- FILE creation verifies every object before the database transaction; transaction failures leave no visible material and do not consume intents.
- FILE versioning accepts retained files only from the material's current version and never mutates earlier associations.
- API responses omit storage object keys and expose `imageAnalysis: null` only as the Task 4-compatible placeholder.

## Commits

- `4a8232f` — `feat: add file uploads and immutable file versions`
- This report is committed separately immediately after the implementation commit.

## Concerns / Follow-up

- No live AWS S3 or PostgreSQL environment was available in this task. Production adapters and migrations compile, S3 error behavior is unit-tested, and the local adapter is covered by integration tests.
- Expired/unconsumed object cleanup is intentionally outside Task 3; intent expiry prevents use but does not delete orphaned storage objects.
- Image analysis processing remains intentionally deferred to Task 4.

## Review Fix Round 1

**Status:** DONE

### Findings Resolved

- Closed the aggregate-size bypass by rechecking the complete FILE snapshot at finalization. Initial snapshots sum all selected upload intents; later versions sum both retained `FileAsset` rows and new upload intents. Overflow or a configured-limit breach returns `413 UPLOAD_LIMIT_EXCEEDED`.
- Added the ordered discriminated FILE-version request list:
  - `{"kind":"EXISTING","fileId":"..."}`
  - `{"kind":"UPLOAD","uploadIntentId":"..."}`
- Persisted `displayOrder` directly from that combined list, so retained and new files can be freely interleaved.
- Preserved the prior `fileIds` plus `uploadIntentIds` JSON request for compatibility; requests cannot mix the legacy and combined representations.
- Added direct coverage proving a consumed intent cannot finalize either another material or another version.
- Kept the approved MIME allowlist unchanged.

### Changed Files

- `src/main/java/com/example/newsmaterial/material/api/MaterialApi.java`
- `src/main/java/com/example/newsmaterial/material/service/MaterialService.java`
- `src/main/java/com/example/newsmaterial/upload/UploadIntentService.java`
- `src/test/java/com/example/newsmaterial/file/FileAggregateLimitTest.java`
- `src/test/java/com/example/newsmaterial/file/FileMaterialApiTest.java`
- `src/test/java/com/example/newsmaterial/file/FileVersionHistoryTest.java`

### RED / GREEN Evidence

- Focused RED command: `./mvnw -Dtest=FileAggregateLimitTest,FileVersionHistoryTest,FileMaterialApiTest test`
- RED result: 7 tests run, 4 failures:
  - cross-request initial snapshot returned 201 instead of 413;
  - retained-plus-new version returned 422 instead of 413;
  - `[UPLOAD, EXISTING]` ordered request returned 422 instead of 201;
  - consumed intent in the combined version request returned 422 instead of 409.
- Focused GREEN result: 7 tests, 0 failures, 0 errors, 0 skipped.
- Full verification: `git diff --check && ./mvnw clean verify`
- Full result: 27 tests, 0 failures, 0 errors, 0 skipped; executable JAR produced.

### Self-Review

- Aggregate checks use configured limits and overflow-safe addition.
- Combined selectors require exactly one ID matching their discriminator.
- Existing file IDs are accepted only from the material's current snapshot; duplicate retained IDs remain invalid.
- New intent ownership, expiry, object verification, distinctness, and one-use enforcement still run before version persistence.
- Idempotency fingerprints include the ordered combined list, while identical retries replay the created version even after their intents become consumed.

### Commit

- `9dafafc` — `fix: enforce file snapshot limits and ordering`
