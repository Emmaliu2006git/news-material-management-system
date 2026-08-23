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
