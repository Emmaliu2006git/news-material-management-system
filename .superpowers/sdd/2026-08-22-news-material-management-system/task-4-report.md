# Task 4 Report: Current Search and Per-Image Analysis

**Status:** DONE

**Base commit:** `b46814c`

## Implemented

- Added authenticated project-scoped material search with optional free-text, material type, owner, created-time, event-time, location, and manual-tag filters.
- Search returns one summary per Material and reads only its current version.
- Added persisted material and per-image AI search documents. Every query joins each row to `Material.currentVersionId`, so stale historical text, metadata, tags, and image analysis cannot match.
- AI search results expose `matchedFileId` only when the query matched a per-image AI row.
- Added reconciliation that rebuilds current search rows without Kafka.
- Added exactly one persisted `ImageAnalysis` row for each new image FileAsset, including images introduced by later FILE versions; non-image files receive none.
- Added the `PENDING -> PROCESSING -> COMPLETED/FAILED` state machine, configurable three-attempt retry/backoff, and owner-only explicit retry from `FAILED`.
- Added a scheduled transactional worker that reads private bytes through `StoragePort`, validates structured provider results, stores per-image descriptions/tags, and updates the AI search row.
- Added a deterministic credential-free local provider and a conditional OpenAI-compatible Responses API adapter using Spring `RestClient`.
- FILE material views now expose each image's analysis status, description, AI tags, and generated time while keeping manual tags at snapshot level.
- Added coherent PostgreSQL V4 tables, constraints, indexes, and `pg_trgm`; local H2 remains JPA create-drop.

## Changed Files

- `src/main/java/com/example/newsmaterial/analysis/*`
- `src/main/java/com/example/newsmaterial/search/*`
- `src/main/java/com/example/newsmaterial/material/api/MaterialApi.java`
- `src/main/java/com/example/newsmaterial/material/service/MaterialService.java`
- `src/main/java/com/example/newsmaterial/material/persistence/MaterialRepository.java`
- `src/main/java/com/example/newsmaterial/file/*`
- `src/main/java/com/example/newsmaterial/storage/*`
- `src/main/resources/application.yml`
- `src/main/resources/application-local.yml`
- `src/main/resources/db/migration/V4__search_and_analysis.sql`
- `src/test/java/com/example/newsmaterial/analysis/*`
- `src/test/java/com/example/newsmaterial/search/SearchApiTest.java`
- `src/test/java/com/example/newsmaterial/file/FileMaterialApiTest.java`

## TDD Evidence

### Baseline

- The existing 27 tests passed before Task 4 behavior was added.

### RED

Behavioral tests were introduced before their production implementations and observed failing for the expected reasons:

- `ImageAnalysisStateTest`: missing analysis state model.
- `ImageAnalysisApiTest`: missing image association/repository and retry route; owner/non-owner retry initially returned 404 instead of the required authorization behavior.
- `AnalysisWorkerTest`: missing durable worker/provider result contract.
- `SearchApiTest`: search route initially returned 404.
- `ImageAnalysisProviderTest`: missing OpenAI-compatible provider/parser.
- Existing FILE API coverage caught the intentional response-contract change from a null placeholder to visible `PENDING` analysis state.

### GREEN

- Focused state, API, worker, search, and provider test runs passed after each corresponding implementation.
- A full-suite run exposed shared-H2 queue ordering in the AI search test. The test was corrected to process a bounded persisted queue until its target analysis completed.
- Self-review added explicit coverage proving that AI content from an image removed in a later FILE version no longer matches, even though its stale search row still exists.

### Final Verification

- Command: `git diff --check && ./mvnw clean verify`
- Result: 38 tests, 0 failures, 0 errors, 0 skipped.
- Build result: `BUILD SUCCESS`; executable JAR produced.

## Self-Review

- Current-only behavior is enforced in the database query by joining search-document version IDs to the authoritative Material current-version pointer, not by relying on cleanup timing.
- Search grouping guarantees one result per Material; an AI match preserves the exact image ID for UI highlighting.
- Image analysis is committed with the FileAsset but executed asynchronously, so provider/storage failure changes only analysis state and never material visibility.
- New version creation reuses existing image analyses and creates analysis only for genuinely new image assets.
- Retry authorization is based on stable Material ownership; authenticated non-owners receive 403 and only `FAILED` rows can reset.
- Worker claim, completion, and failure transitions use separate transactions and pessimistic locks; backoff and terminal failure are persisted.
- Provider results enforce description/tag shape and size limits before completion.
- Production code does not log image bytes, content, credentials, signed URLs, or provider payloads.
- V4 uniqueness and check constraints preserve one analysis per file and one search row per material/version/source identity.

## Commits

- `048c89b` — `feat: add current search and image analysis`
- This report is committed separately immediately after the implementation commit.

## Concerns / Follow-up

- No live PostgreSQL, S3, or OpenAI-compatible endpoint was available in this task. The complete local H2/storage/provider workflow is integration-tested; production adapters compile, structured provider parsing is unit-tested, and V4 is reviewed against V1-V3.
- The production AI provider requires `OPENAI_API_KEY`; local demonstrations remain credential-free under the `local` profile.

## Review Fix Round 1

**Status:** DONE

### Findings Resolved

- Added a persisted claim lease. The worker now reclaims expired `PROCESSING` rows, fences stale completions by attempt number, and moves an expired final attempt to `FAILED`.
- Added PostgreSQL migration backfill plus application reconciliation for existing `image/*` assets. Both paths are idempotent, preserve existing rows, and exclude non-images.
- Added explicit production provider connect and request/read timeouts. Provider timeouts follow the same persisted retry/backoff path as any other provider failure.
- Escaped literal backslash, percent, and underscore in free-text `ILIKE` input and added an explicit SQL `ESCAPE` clause. Location and tag filters remain literal Java comparisons and never enter a SQL `LIKE` expression.

### Changed Files

- Analysis entity, repository, worker, service, production provider, and configuration
- File repository and reconciliation job
- Search repository/service
- PostgreSQL V4 migration
- Worker, state, API/reconciliation, and search regression tests

### TDD Evidence

#### RED

- The focused suite first failed to compile because the lease-aware claim/worker API, reconciliation method, and timeout-aware provider constructor did not exist.
- After the first production pass, focused tests exposed committed-queue ordering and database timestamp precision assumptions in the new tests; those tests were corrected to drain a bounded durable queue and compare persisted time with database precision.
- The literal wildcard regression was introduced before escaping and demonstrated that percent and underscore could otherwise act as `LIKE` wildcards.

#### GREEN

- Focused command: `./mvnw -q -Dtest=AnalysisWorkerTest,ImageAnalysisApiTest,ImageAnalysisStateTest,SearchApiTest test`
- Result: passed, including committed lease recovery, maximum-attempt exhaustion, existing-image reconciliation, timeout retry persistence, and literal wildcard search.
- Final command: `git diff --check && ./mvnw clean verify`
- Result: 42 tests, 0 failures, 0 errors, 0 skipped; `BUILD SUCCESS`.

### Self-Review

- Claim and reclaim run under pessimistic locking, and completion/failure carry the claimed attempt number so a late worker cannot overwrite a newer claim.
- A crash after the last permitted claim cannot leave the row permanently `PROCESSING`; lease expiry transitions it to `FAILED`.
- V4 uses deterministic UUIDs and `ON CONFLICT (file_id) DO NOTHING`; application reconciliation is also repeat-safe and covered with repeated invocation.
- The in-process delayed HTTP test exercises the production adapter timeout without network credentials and proves the worker persists the normal retry state.
- The query still joins search documents to the authoritative current version. Escaping changes literal interpretation only and does not weaken current-only behavior.
- No image bytes, content, credentials, or signed URLs are logged.

### Commit

- `1093da6` — `fix: harden analysis recovery and search`

### Concerns / Follow-up

- No live PostgreSQL or external OpenAI-compatible endpoint was available. V4 SQL is reviewed against V1-V3; H2 verifies application reconciliation, and an in-process HTTP server verifies the production timeout boundary.
