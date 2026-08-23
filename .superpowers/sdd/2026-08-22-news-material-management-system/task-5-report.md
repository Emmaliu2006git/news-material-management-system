# Task 5 Report — Responsive React User Flow

Status: DONE

## Delivered

- Added a React 19 + TypeScript + Vite single-page application under `frontend/`.
- Added authenticated routes for login, project list/detail, material creation, current material, historical versions, and owner version creation.
- Added a same-origin, credentialed, CSRF-aware API client with stable error mapping.
- Added project creation plus current-version-only material search with matched-image highlighting.
- Added complete TEXT creation and FILE creation with SHA-256 calculation, upload intents, direct PUT upload progress, ordered finalization, and retry-safe errors.
- Added one shared `MaterialSnapshotView` for current and historical snapshots.
- Added owner-gated immutable version creation, expected-current-version conflict handling, and ordered mixed existing/new FILE selections.
- Added per-image AI status, description, AI tags, retry controls, and file download. Manual and AI tags are visually and semantically distinct.
- Added a mobile-first responsive visual system and accessible semantic navigation/forms.
- Added the missing backend session contracts required by the SPA: `GET /api/v1/auth/me` and `POST /api/v1/auth/logout`.

## Files

- Frontend tooling: `frontend/package.json`, `package-lock.json`, TypeScript/Vite/ESLint configs.
- Runtime: `frontend/src/main.tsx`, `App.tsx`, `styles.css`.
- API/auth: `frontend/src/api/*`, `frontend/src/auth/*`.
- Screens/components: `frontend/src/components/*`, `projects/*`, `materials/*`.
- Tests: `frontend/src/App.test.tsx`, `api/client.test.ts`, `materials/MaterialFormPage.test.tsx`.
- Backend compatibility: `AuthApi.java`, `AuthenticationApiTest.java`.

## TDD Evidence

### RED

- Added session restore/logout integration tests first; both failed with HTTP 404 before the endpoints existed.
- Added application rendering tests before the route implementations; initial run failed on the missing application module, then eight behavioral assertions remained red.
- Added material creation/version tests before their screens were complete; the suite initially reported five failing workflows.
- The FILE version test was strengthened to require an ordered mixture of existing and newly uploaded files; the first assertion exposed an ordering expectation mismatch before correction.

### GREEN

- `npm test -- --run --reporter=dot`: 3 test files, 17 tests passed.
- `npm run typecheck`: passed.
- `npm run lint`: passed with zero warnings.
- `npm run build`: passed; 52 modules transformed.
- `./mvnw -q -Dtest=AuthenticationApiTest test`: passed.

## Self-review

- Frontend request/response types were derived from the implemented backend DTOs and controllers.
- All state-changing backend requests bootstrap and send the server-provided CSRF header.
- Direct upload URLs and file content are never logged; direct PUT requests use only the server-provided required headers.
- Historical routes use the same full snapshot component and expose no editing or AI retry controls.
- Server-provided `canEdit` is the permission source for version creation and AI retry.
- FILE finalization occurs only after every direct upload succeeds.
- New versions send both `expectedCurrentVersion` and an `Idempotency-Key`.
- `node_modules/` and `dist/` remain ignored and uncommitted.
- No team, role, administrator, deletion, media-editing, or lineage concepts were added.

## Commit

- `8ac1f43 feat: add responsive newsroom frontend`

## Concerns / Follow-up

- The backend material DTO currently exposes the owner as an ID, not a display username, so the UI faithfully displays that identifier.
- Task 6 packaging, browser E2E, Compose wiring, and runtime smoke tests are intentionally not included in this task.

---

## Fix Round 1 — DONE

### Delivered

- Local direct uploads now bootstrap the current real CSRF token, attach credentials and the CSRF header only for same-origin URLs, and send only signed required headers to cross-origin object storage.
- Logical TEXT, FILE, and version submissions keep a stable idempotency key while their payload is unchanged.
- FILE retries retain valid upload intents and uploaded-file state, retry only failed PUTs, and replay an identical finalization payload/key after a lost response.
- Search now composes text, type, owner, location, tag, material-created range, and event-time range filters. Date-only end values become inclusive UTC end-of-day instants.
- Owner choices come from the initial unfiltered project result set.
- Current and historical snapshot views now show human-readable project, owner, version creator, and file uploader names plus material, version, and file IDs and all required timestamps.
- Image analysis now shows status, description, AI tags, and generation time.
- Failed logout keeps the authenticated shell intact and displays the server error.
- Mobile layouts use a real media-query-driven, accessible expandable navigation control.

### Files

- Security/client: `frontend/src/api/client.ts`, `client.test.ts`, `LocalStorageSecurityTest.java`.
- Retry flows: `NewMaterialPage.tsx`, `VersionEditorPage.tsx`, `formUtils.ts`, `MaterialFormPage.test.tsx`.
- Search: `ProjectDetailPage.tsx`, `SearchService.java`, `SearchApiTest.java`.
- Snapshot contract/UI: `MaterialApi.java`, `MaterialService.java`, `MaterialSnapshotView.tsx`, `types.ts`, fixtures and lifecycle/API tests.
- Shell/responsive behavior: `Layout.tsx`, `styles.css`, `App.test.tsx`.

### TDD RED Evidence

- Backend metadata regression run: 10 tests executed; missing human-readable DTO fields produced 1 failure and 2 errors before implementation.
- Direct-upload client run: 5 tests executed; the same-origin CSRF assertion failed before implementation.
- Stable retry run: 9 tests executed; 3 failed because TEXT keys changed, FILE intents/uploads repeated, and FILE version keys changed.
- UI behavior run: 13 tests executed; 4 failed for logout error handling, mobile navigation, complete filters, and snapshot metadata before implementation.

### GREEN / Verification Evidence

- Focused backend auth/storage/DTO contract run: 14 tests passed.
- Frontend unit/integration: 3 files, 27 tests passed.
- Frontend typecheck: passed.
- Frontend lint: passed with zero warnings.
- Frontend production build: passed; 52 modules transformed.
- Full backend suite: 45 tests passed, 0 failures, 0 errors.
- `git diff --check`: passed with no whitespace errors.

### Self-review

- Same-origin detection compares parsed origins, so absolute same-origin upload URLs are protected as well as relative local URLs.
- A fresh CSRF token is bootstrapped for every mutation; cross-origin uploads do not fetch or receive it.
- Upload intent state is independent from finalization state: unchanged retries reuse both, while materially changed finalization payloads receive a new key.
- An expired, unfinalized upload session is regenerated; a failed finalization replays the immutable payload/key without repeating PUTs.
- Search remains current-version-only and introduces no team, role, or administrator concept.
- UUIDs are visible as traceable identifiers but never substituted for human-readable usernames or project titles.
- Server `canEdit` remains authoritative for version creation and AI retry; downloads remain available to authenticated non-owners.
- The earlier owner-display concern is resolved by the additive DTO fields.

### Commit
