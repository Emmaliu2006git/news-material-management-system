# Final Review Fix Report

- Date: 2026-08-23
- Baseline: `a6cd41c`
- Implementation commit: `196a593` (`fix: close final review gaps`)
- Branch: `codex/implementation`

## Outcome

All six Important findings and all three Minor findings from final review are addressed. The final committed revision passed the repository's complete verification pipeline twice: once in the working repository and once from a detached clean checkout. Both packaged-app Playwright projects passed, including the emulated Pixel 7 login and multi-file FILE upload journey.

The change preserves the agreed demo scope: one ordinary authenticated user type, owner-only linear version creation, current-version search, immutable historical snapshots, image-only AI analysis, no public registration, and no teams/roles/Kafka/native app.

## Findings-to-fixes map

| # | Finding | Resolution | Primary evidence |
| --- | --- | --- | --- |
| 1 | Manual login allowed session fixation | `AuthApi` now invokes the configured `SessionAuthenticationStrategy` before saving the authenticated `SecurityContext`; `ChangeSessionIdAuthenticationStrategy` rotates an existing anonymous session. | `SessionFixationRegressionTest` obtains CSRF in an anonymous session, logs in, proves the ID changed, proves the old ID receives `401`, and proves the new session is authenticated. |
| 2 | Framework-generated 4xx responses escaped the global envelope | `ApiExceptionHandler` now covers malformed JSON/invalid enum, body validation, handler-method validation, constraint violations, invalid argument types, missing headers, and missing parameters. Every handled response has exactly `code`, `message`, `requestId`, and `details`; validation details use actual parameter/field names. | `FrameworkErrorContractTest` exercises packaged Spring MVC paths for each category. Existing authentication, authorization, and CSRF tests remain green. |
| 3 | Next-version request was a permissive pseudo-union | The public next-version DTO tracks JSON field presence. TEXT rejects `textContent`/`files`, FILE rejects `textBody`, FILE requires ordered discriminated `files`, and legacy top-level `fileIds`/`uploadIntentIds` are rejected. Unknown properties fail globally. Union validation occurs before the idempotency fingerprint. | `StrictVersionUnionTest` proves opposite-type and legacy fields fail and proves a corrected request can reuse the same idempotency key without a false conflict. Frontend payload types and docs use the strict union. |
| 4 | Upload limits were duplicated and could diverge | `FilePolicy` is the single backend count/per-file/total policy. It is used by intent creation, FILE v1 finalization, and every retained-plus-new FILE snapshot. Authenticated `GET /api/v1/capabilities` exposes it; both browser forms load and apply it. Defaults remain 10 files, 50 MiB per file, and 200 MiB total. | `FilePolicyContractTest` configures lower limits and proves capabilities, intents, v1, and next versions align. Browser form tests prove lower count, file-size, and total-size limits are rendered/enforced without a hardcoded ten. |
| 5 | Web Crypto absence broke plain-LAN/mobile upload; no mobile packaged journey | Browser SHA-256 prefers Web Crypto but falls back to pinned `@noble/hashes@1.8.0` when `crypto.subtle` is absent or throws. Playwright now has Desktop Chrome and Pixel 7 projects running the same packaged journey. Responsive E2E opens the real mobile menu before inspecting the signed-in user or logging out. | Client unit test verifies the known SHA-256 when `subtle` is unavailable. Packaged E2E passes in both projects and exercises login plus multi-file FILE upload. README/demo/operations recommend HTTPS or a trusted tunnel while documenting the LAN fallback. |
| 6 | Timeline returned full versions | `GET /api/v1/materials/{id}/versions` returns dedicated `VersionSummaryView` records containing only `id`, `versionNumber`, creator ID/name, created time, and title. Selecting `/versions/{number}` still returns the complete immutable snapshot. | `VersionSummaryApiTest` asserts the summary fields and absence of snapshot/text/files. Frontend timeline types/rendering and API docs match. |
| 7 | Production cookie defaults were not fail-safe | Base/cloud configuration enables `Secure`, `HttpOnly`, and `SameSite=Lax`, and uses framework forwarded-header handling. The local profile explicitly disables `Secure`; the HTTP Compose demo carries an explicit opt-out environment variable. | `SessionCookieSecurityTest` inspects the real server's `Set-Cookie`; `DockerComposeContractTest` verifies the demo override. |
| 8 | `current_version_id` could reference another Material's version | Forward migration V5 adds unique `(material_id, id)` and deferred composite FK `(materials.id, current_version_id) -> material_versions(material_id, id)`. Deferral preserves the existing one-transaction Material/v1 insertion order while enforcing the invariant at commit. | `CurrentVersionSchemaContractTest`, successful clean compilation/package, and design/operations migration documentation. A live PostgreSQL migration remains an explicit deployment check. |
| 9 | Cloud mode had no supported account bootstrap | The non-web `provision-user` profile reads username plus password from environment or hidden console input, validates a 12-character minimum, BCrypt-hashes, inserts idempotently, never changes an existing password, prints only username/outcome, clears its mutable password buffer, and exits. No registration/role/admin endpoint was added. | `UserProvisioningServiceTest` proves create/idempotent behavior. A packaged local-profile smoke run exited `0` and printed `Account provisioning-smoke: CREATED` without printing the password. Operations docs provide the one-time procedure. |

## TDD evidence

Behavioral changes were introduced with failing focused tests before implementation, then rerun to green.

| Area | RED observation | GREEN evidence |
| --- | --- | --- |
| Session fixation | Anonymous and authenticated requests retained the same session identity before the strategy was called. | `SessionFixationRegressionTest`: 1/1 passed. |
| MVC error contract | Malformed/framework validation paths did not consistently return the four-field envelope or real field details. | `FrameworkErrorContractTest`: 3/3 passed, covering all requested categories through multiple requests. |
| Strict version union | Opposite-type/legacy fields were ignored or accepted. | `StrictVersionUnionTest`: 2/2 passed. |
| Shared upload limits | No capabilities API existed and different flows could use different limits. | `FilePolicyContractTest`: 2/2 passed; frontend material-form suite passed 13/13. |
| Checksum fallback | Hashing failed when `crypto.subtle` was unavailable. | `client.test.ts`: fallback assertion passed as part of 6/6 client tests. |
| Version summary | Timeline serialized full snapshot content. | `VersionSummaryApiTest`: 1/1 passed. |
| Secure cookie | Base-mode `JSESSIONID` lacked the required Secure contract. | `SessionCookieSecurityTest`: 1/1 passed. |
| Database invariant | No V5 migration/resource enforced current-version ownership. | `CurrentVersionSchemaContractTest`: 1/1 passed. |
| Provisioning | Provisioning service/profile did not exist. | `UserProvisioningServiceTest`: 1/1 passed; packaged smoke run passed. |
| Mobile packaged flow | First mobile run failed because the test looked inside the collapsed navigation without opening it. | Focused Pixel 7 rerun passed, then both full-suite Playwright projects passed twice. |

## Full verification

### Working repository

Command: `./scripts/test-all.sh`

- Locked frontend install: passed; npm reported 0 vulnerabilities.
- TypeScript typecheck: passed.
- ESLint with zero warnings: passed.
- Frontend unit tests: 32/32 passed across 3 files.
- Production frontend build: passed.
- Backend verification: 83/83 tests passed.
- Spring Boot executable JAR package: passed.
- Desktop Chromium packaged journey: passed.
- Pixel 7 Chromium packaged journey: passed.
- Playwright total: 2/2 passed.

### Detached clean checkout

A detached worktree was created at commit `196a593` with no tracked or untracked source changes. The same complete `./scripts/test-all.sh` pipeline was run there using only the pinned external Node runtime path.

- Frontend unit tests: 32/32 passed.
- Backend tests: 83/83 passed.
- Clean-checkout executable JAR: built and verified non-empty.
- Desktop Chromium packaged journey: passed.
- Pixel 7 Chromium packaged journey: passed.
- Playwright total: 2/2 passed.

The temporary clean worktree and its generated Node/build/browser artifacts were then removed through `git worktree remove`; all outputs are reproducible from the commit.

### Additional audits

- `git diff --check`: passed before the implementation commit and for this report.
- High-confidence AWS/OpenAI/private-key pattern scan: no matches.
- `npm ci` audit: 0 vulnerabilities.
- Packaged provisioning runner: exit `0`, non-web mode, password absent from output.
- Main worktree after implementation commit: clean.

## Files and responsibilities

### Security and API error contract

- `src/main/java/com/example/newsmaterial/security/AuthApi.java`
- `src/main/java/com/example/newsmaterial/security/SecurityConfiguration.java`
- `src/main/java/com/example/newsmaterial/common/api/ApiExceptionHandler.java`
- `src/main/resources/application.yml`
- `src/main/resources/application-local.yml`
- Session fixation, real-cookie, and framework error contract tests under `src/test/java/com/example/newsmaterial/security` and `common/api`.

### Strict versions, summaries, and persistence invariant

- `MaterialApi.java` and `MaterialService.java` implement the strict public union and summary timeline.
- `V5__enforce_material_current_version_ownership.sql` adds the deferred composite invariant.
- Strict union, summary API, schema contract, and updated FILE history tests cover the behavior.

### Upload policy and mobile browser support

- `FilePolicy.java`, `CapabilitiesApi.java`, and `UploadIntentService.java` centralize/expose/enforce limits.
- Frontend API types/client, form utilities, new-material/version forms, fixtures, and form tests consume the server policy.
- `@noble/hashes@1.8.0` is pinned in `package.json` and lockfile.
- Playwright config and happy-path test cover Desktop Chrome and Pixel 7.

### Provisioning and deployment

- `UserProvisioningService.java`, `UserProvisioningConfiguration.java`, and `application-provision-user.yml` provide the one-time non-web command.
- `.env.example`, Compose, and deployment contract test make the HTTP-demo cookie exception explicit.
- README plus `docs/api.md`, `docs/operations.md`, `docs/demo-script.md`, and `docs/design.md` document the final contracts and manual checks.

## Self-review

- The public v1 FILE creation API intentionally still accepts ordered `uploadIntentIds`; the legacy arrays were removed only from the next-version endpoint, exactly as reviewed.
- Union validation happens after the Material type is known but before fingerprint lookup, so ignored opposite-type fields cannot poison idempotency behavior.
- FILE version limits apply to the complete retained-plus-new snapshot, not just newly uploaded files.
- Capabilities require authentication and contain no credentials or infrastructure details.
- Version summaries cannot accidentally serialize files or AI analysis because they use a separate response record rather than a projection of the full view.
- The composite FK is `DEFERRABLE INITIALLY DEFERRED`; a non-deferred FK would break the established Material/v1 circular insertion transaction.
- Cloud/default cookies remain secure by default. Only documented local/demo HTTP profiles opt out.
- Provisioning is profile-gated, non-web, idempotent, and does not expand product authorization scope.
- No teams, roles, public registration, reset, delete, notifications, Kafka, semantic search, or native-app work was introduced.

## Commits

- `196a593` — `fix: close final review gaps` — all implementation, regression tests, configuration, migration, frontend behavior, and documentation.
- This final report is committed separately as a report-only follow-up; its exact hash is available in the final handoff and `git log`.

## Remaining manual checks

The following require external infrastructure or a physical device and were not represented as automated success:

1. Start the full Docker Compose stack with a working Docker daemon and verify health plus restart persistence.
2. Apply Flyway V1–V5 to a real PostgreSQL database, including an upgrade containing representative pre-V5 data, and verify the composite FK in `pg_constraint`.
3. Exercise direct presigned upload/download against live private MinIO/S3 with the deployed CORS origin and browser-reachable presigner endpoint.
4. Run the one-time provisioning profile against the target cloud PostgreSQL identity and confirm a second invocation returns `ALREADY_EXISTS`.
5. Run OpenAI image analysis with an approved account, privacy policy, network path, timeouts, and synthetic media before real newsroom data.
6. Complete a real-phone login, camera/gallery/file-picker upload, download, logout, and rotation/accessibility check. Prefer HTTPS or a trusted tunnel; plain LAN hashing now has the tested fallback.
7. Confirm the reverse proxy passes standard forwarded headers and that production `JSESSIONID` remains `Secure; HttpOnly; SameSite=Lax` over HTTPS.
8. Run the organization's Java dependency/container vulnerability scanner; no Java CVE database scanner is configured in this demo repository.

No automated Critical or Important finding remains open. Live services and the real-device checks above are the only outstanding acceptance work.

## Post-fix scoped re-review

The required independent scoped re-review confirmed findings 1 through 8 as addressed and found no new Critical or Important regression in their fix diff. It did reproduce one unresolved Important issue in finding 9: the documented provisioning-only profile inherited the OpenAI provider, but a non-web Spring Boot context has no auto-configured `RestClient.Builder`, so startup stopped before the provisioning runner.

The root cause was profile configuration rather than the provisioning transaction. `application-provision-user.yml` now selects the deterministic local analysis provider while keeping analysis scheduling disabled. This satisfies the inactive worker dependency without contacting OpenAI and does not change the default cloud application's OpenAI provider.

`UserProvisioningProfileTest` starts the complete application with only `provision-user`, an isolated in-memory database, and synthetic credentials. It failed before the change with the same missing-`RestClient.Builder` exception, then passed after the one-line profile correction and created the account.

On the corrected tree, `./scripts/test-all.sh` passed 32/32 frontend tests, 84/84 backend tests, the production build and executable JAR, plus 2/2 packaged-app Playwright journeys (Desktop Chrome and Pixel 7). A separate packaged-JAR run using only `provision-user` exited 0 and printed `Account packaged-profile-smoke: CREATED`.
