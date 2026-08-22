# News Material Management System Implementation Plan

> Use subagent-driven development. Every behavioral change follows RED-GREEN-REFACTOR and receives task-scoped review before the next task.

**Goal:** Deliver the responsive internal news material system defined by docs/design.md: login, reporting projects, TEXT and FILE materials, immutable owner-only versions, multi-file uploads, current-only search, per-image AI analysis, and a runnable local demo.

**Architecture:** Spring Boot 4.1.1 modular monolith and same-origin React 19 TypeScript SPA. PostgreSQL, private S3, and an OpenAI-compatible vision provider are production adapters. H2, local file storage, and deterministic analysis are local-demo adapters behind the same ports.

**Tech:** Java 21 target, Maven Wrapper, Spring Security sessions/CSRF, JPA/Flyway, PostgreSQL/H2, AWS SDK v2, React/Vite, Vitest/Testing Library, Playwright, Docker Compose.

**Spec:** docs/design.md

## Global Constraints

- Work only in /home/emmma06/projects/news-material-management-system.
- One authenticated user type. Everyone authenticated reads all data and adds independent materials; only the material owner creates the next version.
- Stable Material fields: id, reportingProjectId, ownerId, materialType, materialCreatedTime, currentVersionId.
- Immutable complete Version snapshot: id, materialId, versionNumber, versionCreatedTime, versionCreator, title, eventTime, location, manualTags, type-specific content.
- TEXT requires nonblank textBody and no files. FILE requires 1-10 ordered files and may have nullable textContent.
- FileAsset stores id, uploaderId, immutable random objectKey, originalFilename, MIME, size, SHA-256, createdTime. Each image has its own optional ImageAnalysis; non-images have none.
- Current and historical views use the same snapshot layout. Historical views are read-only.
- Search returns one row per material and reads current versions only, including completed per-image AI text and matchedFileId.
- Production bytes move browser-to-S3 with short-lived pre-signed PUT/GET URLs after server authorization. AI failure never invalidates a material.
- No Kafka, teams, roles, native app, media editing, branches/merges, collaborative editing, hard delete, lineage tracking, or vector search.
- API errors contain code, message, requestId, details. State changes keep CSRF protection.
- Demo limits are configurable: 10 files, 50 MiB each, 200 MiB total, 15-minute intent expiry, 24-hour orphan delay, three AI attempts.
- Any authenticated user creates a project; only its creator renames; no deletion.
- Build/bootstrap configuration is exempt from TDD. Every behavior begins with an observed failing test.
- Production profile remains PostgreSQL + S3 + OpenAI-compatible AI; local adapters exist only to make the demo credential-free.

## API Contract

Base: /api/v1, camelCase JSON.
- POST /auth/login, POST /auth/logout, GET /auth/me
- GET/POST /projects, PATCH /projects/{id}
- GET /projects/{id}/materials with q/type/owner/date/event/location/tag filters
- POST /uploads/intents
- POST /materials/text, POST /materials/file
- GET /materials/{id}
- GET /materials/{id}/versions
- GET /materials/{id}/versions/{number}
- POST /materials/{id}/versions with expectedCurrentVersion
- POST /files/{id}/download-url
- POST /files/{id}/analysis/retry
- Material and version creation accept Idempotency-Key. Stale writes return 409 VERSION_CONFLICT.

## Task 1: Bootstrap, Authentication, Projects

Files:
- Create pom.xml, mvnw, mvnw.cmd, .mvn/wrapper/*, README.md
- Create src/main/java/com/example/newsmaterial/NewsMaterialApplication.java
- Create common/api/*, security/*, project/*
- Create application.yml, application-local.yml, db/migration/V1__identity_and_projects.sql
- Test AuthenticationApiTest and ProjectApiTest

RED: prove anonymous API is 401; demo login establishes a session; authenticated list/create works; creator rename succeeds and non-creator rename is 403.
GREEN: implement session security, password hashing, local demo users, CSRF token endpoint/cookie, project model/API, DTO boundaries, and stable error envelope.
VERIFY: focused tests then full Maven suite; commit.

## Task 2: TEXT Materials and Immutable Versions

Files:
- Create material/domain/*, material/api/*, material/service/*, material/persistence/*
- Create V2__materials_and_versions.sql
- Test TextMaterialApiTest, VersionAuthorizationTest, VersionConflictTest

RED: blank text fails; v1 contains every common field; current/history share response layout; v2 preserves v1; only owner versions; stale version is 409; repeated Idempotency-Key returns the original result.
GREEN: implement Material, immutable MaterialVersion, snapshot DTO/mapping, TEXT invariants, transactional pessimistic lock/current pointer, history and idempotency.
VERIFY: focused and full backend tests; commit.

## Task 3: Uploads, FILE Materials, Downloads

Files:
- Create storage/*, file/*, V3__uploads_and_files.sql
- Test UploadIntentServiceTest, FileMaterialApiTest, FileVersionHistoryTest

RED: prove count/size/total limits, owner-bound expiring intents, object verification, ordered multi-file v1, optional textContent, authenticated downloads, owner-only versions, unchanged-file reuse, replacement through new intents, and immutable old file sets.
GREEN: implement StoragePort, local and S3 adapters, pre-sign responses, intent lifecycle, FileAsset/VersionFile, FILE snapshots, local controlled upload/download routes, and authorization.
VERIFY: focused tests, full backend suite, local adapter integration; commit.

## Task 4: Search and Image Analysis

Files:
- Create search/*, analysis/*, V4__search_and_analysis.sql
- Test SearchApiTest, ImageAnalysisStateTest, AnalysisWorkerTest

RED: prove one result/material, current match, history-only miss, composed filters, AI match with matchedFileId, per-image separation, no non-image analysis, upload survives AI failure, three-attempt failure, owner retry.
GREEN: implement current-only query/search documents, filters, ImageAnalysis state machine, persisted scheduled worker, deterministic local provider, OpenAI RestClient provider, structured validation/backoff/retry.
VERIFY: focused and full backend tests; commit.

## Task 5: Responsive React User Flow

Files:
- Create frontend package/TypeScript/Vite config
- Create frontend/src/api/*, auth/*, projects/*, materials/*, App.tsx, main.tsx, styles.css
- Test frontend/src/**/*.test.tsx and api tests

RED: cover login, projects, search, TEXT creation, FILE selection/direct upload/finalize, current snapshot, same-layout read-only history, owner edit, non-owner controls hidden, image AI display/retry, download, responsive navigation.
GREEN: implement accessible routes/components. Reuse MaterialSnapshotView for current/history and rely on server permissions.
VERIFY: unit tests, typecheck, lint, production build; commit.

## Task 6: Packaging and End-to-End Demo

Files:
- Modify pom.xml and README.md
- Create web/SpaForwardingController.java, scripts/dev.sh, scripts/test-all.sh
- Create docker-compose.yml, .env.example, Playwright config and happy-path.spec.ts
- Test SpaPackagingTest

RED: SPA fallback must not intercept /api/v1; E2E covers login, project, TEXT search/version/history, FILE multi-upload/download, AI isolation, non-owner denial.
GREEN: package SPA with Spring Boot; add credential-free local workflow, production PostgreSQL/MinIO-compatible compose, health checks, demo data, cloud configuration.
VERIFY: test-all, frontend build, Maven package, Playwright; commit.

## Task 7: Hardening and Delivery Docs

Files:
- Create docs/api.md, docs/demo-script.md, docs/operations.md
- Modify only files required by regression failures.

RED: add a failing regression test for every discovered defect.
GREEN: fix verified defects; document fields, permissions, upload/version/search semantics, run/deploy/limitations, and five-minute demo.
VERIFY: all backend/frontend tests, typecheck, lint, builds, package inspection, Playwright, secret/generated-file audit, final whole-branch review; commit.

## Delivery Definition

Fresh checkout follows README commands. Local demo needs no AWS/OpenAI credentials. Production adapters for PostgreSQL/S3/OpenAI exist. All design acceptance criteria have automated evidence except real-device mobile, which has a manual checklist. No implementation file exists in multiverse-exploration.

