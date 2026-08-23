# Task 6 Report: Packaging and End-to-End Demo

## Status

DONE

The React application is packaged into the executable Spring Boot JAR, known client routes support direct navigation, deployment and development entry points are documented, and a real Chromium happy-path test exercises the packaged same-origin application.

## Files

### Build and same-origin delivery

- `pom.xml`: pinned frontend Maven plugin, Node 22.20.0, npm 10.9.3; locked install, type check, unit test, build, and classpath static copy.
- `frontend/vite.config.ts`: restricts Vitest discovery to unit tests under `src`.
- `src/main/java/com/example/newsmaterial/web/SpaForwardingController.java`: forwards only the approved client routes.
- `src/test/java/com/example/newsmaterial/web/SpaPackagingTest.java`: verifies packaged `index.html`, every approved route, and non-interception of API, actuator, and asset paths.

### Local/demo behavior and infrastructure

- `scripts/dev.sh`, `scripts/test-all.sh`: strict prerequisite checks and deterministic workflows.
- `Dockerfile`, `docker-compose.yml`, `.dockerignore`, `.env.example`: app/PostgreSQL/MinIO/private bucket stack with health/readiness gates and named volumes.
- `src/main/resources/application.yml`: configurable S3 endpoint, public presigner endpoint, and path-style access.
- `src/main/resources/application-e2e.yml`: fast deterministic local image-analysis worker for isolated E2E execution.
- `src/main/java/com/example/newsmaterial/security/SecurityConfiguration.java`: seed ordinary demo users for local and demo profiles.
- `src/main/java/com/example/newsmaterial/storage/S3StorageConfiguration.java` and `S3StorageConfigurationTest.java`: MinIO-compatible client/presigner configuration.
- `src/test/java/com/example/newsmaterial/deployment/DockerComposeContractTest.java`: deterministic Compose contract validation without a Docker daemon.

### AI failure isolation and E2E

- `ImageAnalysisProvider.java`, `LocalImageAnalysisProvider.java`, `AnalysisWorker.java`: pass original filename to the local provider and support the local/test-only `fail-ai-` failure trigger without changing the production provider.
- `ImageAnalysisProviderTest.java`: regression coverage for the explicit local failure trigger.
- `frontend/package.json`, `frontend/package-lock.json`, `frontend/playwright.config.ts`, `frontend/e2e/happy-path.spec.ts`: pinned Playwright and the packaged-JAR journey.
- `.gitignore`: ignores downloaded browsers and disposable local data.
- `README.md`: prerequisites, local/package/Compose commands, demo accounts, production configuration, architecture, limitations, mobile-browser guidance, and troubleshooting.

## TDD Evidence

### RED

- `./mvnw -Dtest=ImageAnalysisProviderTest test`: new filename-trigger test initially failed compilation because the provider accepted no filename.
- `./mvnw -Dtest=S3StorageConfigurationTest test`: new presigner test initially produced an AWS virtual-host URL instead of the configured MinIO path-style URL.
- `./mvnw -Dtest=SpaPackagingTest test`: initial run had missing classpath `index.html` plus eight direct-route `404` failures.
- `./mvnw -Dskip.frontend=true -Dtest=DockerComposeContractTest test`: initially failed because `docker-compose.yml` did not exist.
- First full `./mvnw package`: exposed Vitest collecting the new Playwright spec; the unit-test include boundary was then made explicit.
- First Playwright run: exposed an ambiguous `Version 1` locator; it was narrowed to the visible version chip.

### GREEN

- `./mvnw -Dtest=ImageAnalysisProviderTest test`: 3 tests passed.
- `./mvnw -Dtest=S3StorageConfigurationTest test`: 1 test passed.
- `./mvnw -Dtest=SpaPackagingTest test`: 12 tests passed.
- `./mvnw -Dskip.frontend=true -Dtest=DockerComposeContractTest test`: 1 test passed.
- `npm test`: 3 files, 29 tests passed after separating unit and E2E discovery.

## Package and E2E Evidence

- `./mvnw package`: BUILD SUCCESS; frontend type check, 29 frontend tests, production build, 61 backend tests, and executable JAR creation passed.
- `jar tf target/news-material-management-system-0.0.1-SNAPSHOT.jar`: confirmed `BOOT-INF/classes/static/index.html` and hashed JS/CSS assets.
- `PLAYWRIGHT_BROWSERS_PATH=... npm run e2e`: 1 Chromium test passed against the packaged JAR.
- The E2E covers login; generated project creation; TEXT v1/v2/history; current-only search; two-file upload; file download; image-specific AI completion; visible isolated AI failure with material still usable; AI search; logout/login as a second user; hidden edit control; and direct server-side `403 FORBIDDEN` for non-owner version creation.

## Full Verification

`./scripts/test-all.sh` completed successfully in its documented order:

1. Locked `npm ci`
2. TypeScript type check
3. ESLint with zero warnings
4. Vitest: 29/29
5. Vite production build
6. Maven verify/package: 61/61 backend tests
7. Playwright packaged-JAR E2E: 1/1

Additional checks:

- `bash -n scripts/dev.sh scripts/test-all.sh`: passed.
- `git diff --check`: passed.
- `npm audit --omit=dev`: 0 production vulnerabilities.
- MinIO server and client release tags were checked against their upstream release repositories.

## Self-review

- The SPA controller has no catch-all and cannot intercept `/api/v1`, actuator, or real asset requests.
- S3 buckets remain private; browser transfers use existing presigned URL authorization.
- The controlled AI failure exists only in `LocalImageAnalysisProvider`; the OpenAI provider is unaffected.
- The Playwright scenario uses tiny synthetic in-memory PNGs and unique generated names.
- No generated `dist`, `node_modules`, browser binaries, test reports, local data, credentials, or `.env` file are committed.
- The requested reviewer subagent was not used because Task 6 explicitly prohibited spawning subagents; an inline staged-diff review and complete automated verification were performed instead.

## Commits

- `0a791a3 feat: package runnable newsroom demo`
- Documentation evidence report: committed immediately after this report was written.

## Concerns

- Docker is unavailable inside the current WSL distribution, so the Compose stack could not be live-started. The semantic Compose contract test passed, image release tags were verified upstream, and the README documents the Docker Desktop WSL integration requirement.
- The locked frontend toolchain reports two high-severity findings in development-only dependencies. `npm audit --omit=dev` reports zero production vulnerabilities; no forced dependency rewrite was applied.

## Fix Round 1

Addressed both Important review findings:

1. `scripts/dev.sh` now explicitly passes `--app.analysis.scheduling-enabled=true`, so images uploaded through the advertised Vite/local-backend workflow are processed by the deterministic local provider.
2. `README.md` now documents that migration `V4` creates `pg_trgm`, the required Flyway/application database permission when it is absent, the safer administrator preinstallation option for managed services, and credential-free installation/verification commands.

TDD and verification evidence:

- RED: `./mvnw -Dskip.frontend=true -Dtest=DevWorkflowContractTest test` failed because `scripts/dev.sh` did not contain the required scheduling override.
- GREEN: the same focused command passed 1/1 after the minimal script change.
- `bash -n scripts/dev.sh`: passed.
- `git diff --check`: passed.
- `./scripts/test-all.sh`: passed end to end with type check, zero-warning lint, 29/29 frontend unit tests, production build, 62/62 backend tests, executable JAR packaging, and 1/1 packaged-JAR Chromium E2E.

Remaining environmental concern is unchanged: Docker is unavailable in the current WSL distribution, so Compose remains contract-validated rather than live-started.
