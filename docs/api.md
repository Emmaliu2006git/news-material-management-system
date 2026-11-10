# HTTP API Reference

This document describes the implemented `/api/v1` contract. Examples use synthetic UUIDs, timestamps, tokens, checksums, and filenames. They are not credentials or production data.

## Conventions

- Base URL: the same origin as the web application.
- Media type: `application/json`, except direct file `PUT` requests.
- Authentication: server-side HTTP session in the `JSESSIONID` cookie.
- Time: ISO-8601 instants in UTC, for example `2026-08-22T16:30:00Z`.
- IDs: UUID strings.
- All endpoints except login and CSRF bootstrap require an authenticated session.
- All mutating endpoints except login require a current CSRF token.
- Material and version creation also require an `Idempotency-Key` header. After trimming, it must be nonblank and at most 255 characters. The web client uses a UUID.

## Authentication and CSRF

### Login

`POST /api/v1/auth/login` is the only state-changing endpoint exempt from CSRF.

Request:

```http
POST /api/v1/auth/login
Content-Type: application/json

{"username":"emma","password":"demo-password"}
```

Success: `200 OK`. The response sets `JSESSIONID`.

```json
{
  "id": "11111111-1111-4111-8111-111111111111",
  "username": "emma"
}
```
A successful login applies the configured session-authentication strategy before saving the security context. If the caller already has an anonymous session (for example after CSRF bootstrap), its session ID is rotated; the old ID cannot authenticate.


Invalid credentials return `401 INVALID_CREDENTIALS`.

### Current user

`GET /api/v1/auth/me` returns `200` with the same user shape, or `401 AUTHENTICATION_REQUIRED`.

### Fetch a CSRF token

Before any authenticated mutation, call:

```http
GET /api/v1/auth/csrf
Cookie: JSESSIONID=<opaque-session>
```

Response: `200 OK`. It also sets the readable `XSRF-TOKEN` cookie.

```json
{
  "token": "<opaque-csrf-token>",
  "headerName": "X-XSRF-TOKEN"
}
```

Send both the session cookie and the returned header on the mutation:

```http
X-XSRF-TOKEN: <opaque-csrf-token>
Cookie: JSESSIONID=<opaque-session>; XSRF-TOKEN=<opaque-csrf-token>
```

The browser client fetches a fresh token before each mutation. A missing or invalid token returns `403 FORBIDDEN`.

### Logout

`POST /api/v1/auth/logout` requires session and CSRF and returns `204 No Content`. The server invalidates the session.

## Capabilities

`GET /api/v1/capabilities` requires authentication and returns the active server upload policy:

```json
{
  "upload": {
    "maxFiles": 10,
    "maxFileBytes": 52428800,
    "maxTotalBytes": 209715200
  }
}
```

The defaults are 10 files, 50 MiB per file, and 200 MiB per FILE snapshot. Deployments may configure other values. The browser reads this endpoint and validates new FILE materials and retained-plus-new version snapshots with the same policy; it does not hardcode the defaults.

## Permissions

There is one ordinary user type.

| Action | Authenticated user | Project creator | Material owner |
| --- | --- | --- | --- |
| List/create projects | Yes | Yes | Yes |
| Rename a project | No | Yes, for that project | Only if also creator |
| View/search any project material | Yes | Yes | Yes |
| Add independent material to any project | Yes | Yes | Yes |
| View current/history/download | Yes | Yes | Yes |
| Create a new material version | No | No, unless owner | Yes |
| Retry failed image analysis | No | No, unless owner | Yes |

Downloading a file and later uploading it creates an independent FileAsset; no provenance link is created.

## Projects

### List projects

`GET /api/v1/projects` returns `200 OK`, newest first.

```json
[
  {
    "id": "22222222-2222-4222-8222-222222222222",
    "title": "Downtown Flood",
    "creatorId": "11111111-1111-4111-8111-111111111111",
    "creatorUsername": "emma",
    "createdAt": "2026-08-22T15:00:00Z",
    "updatedAt": "2026-08-22T15:00:00Z"
  }
]
```

### Create a project

`POST /api/v1/projects` requires CSRF.

```json
{"title":"Downtown Flood"}
```

`title` is required, trimmed, and limited to 160 characters. Success returns `201 Created` and the project shape above.

### Rename a project

`PATCH /api/v1/projects/{projectId}` takes the same body and returns `200 OK`. Only the project creator can rename.

### Deletion boundary

The API does not expose DELETE endpoints for projects, materials, or versions. This preserves reporting evidence and complete version histories.

## Material field model

Every Material response contains stable identity plus one Version snapshot:

| Field | Meaning |
| --- | --- |
| `id` | Stable Material ID |
| `projectId`, `projectTitle` | Reporting project |
| `ownerId`, `ownerUsername` | User who created the material |
| `type` | Permanent `TEXT` or `FILE` |
| `materialCreatedTime` | Server-generated time when v1 was created |
| `currentVersionId` | ID of the current immutable version |
| `canEdit` | True only for the owner viewing the current version |
| `historical` | True for every explicit `/versions/{versionNumber}` response, including when that number is current |
| `version` | The selected full Version snapshot |

Common Version fields:

| Field | Required | Source |
| --- | --- | --- |
| `id`, `versionNumber` | Always present | Server |
| `createdBy`, `creatorUsername` | Always present | Server |
| `versionCreatedTime` | Always present | Server |
| `snapshot.title` | Yes | User |
| `snapshot.eventTime` | No; nullable | User |
| `snapshot.location` | No; nullable | User |
| `snapshot.manualTags` | Always present; may be empty | User |
| `snapshot.content` | Yes | Discriminated union |

Input limits and normalization applied before fingerprinting or persistence:

- `title`: trim leading/trailing whitespace; the result must be nonblank and at most 240 characters.
- `location`: trim; blank becomes null; a non-null result is at most 240 characters.
- Each `manualTags` item: trim; the result must be nonblank and at most 80 characters. Null or blank items are rejected. Duplicate normalized tags keep their first position.
- A null `manualTags` list becomes an empty list.
- `textBody`: trim; the result must be nonblank.
- FILE `textContent`: trim; blank becomes null.
- `eventTime` and UUID/selectors are not string-normalized.

The content union is:

```json
{"kind":"TEXT","textBody":"Non-empty written observation."}
```

or:

```json
{
  "kind": "FILE",
  "textContent": "Optional notes about this file set.",
  "files": []
}
```

A FILE snapshot always has one to ten ordered files; the empty array above only illustrates the JSON shape.

## Create TEXT material

`POST /api/v1/materials/text` requires CSRF and `Idempotency-Key`.

```http
Idempotency-Key: 88888888-8888-4888-8888-888888888888
```

```json
{
  "projectId": "22222222-2222-4222-8222-222222222222",
  "title": "Bridge water-level note",
  "eventTime": "2026-08-22T14:20:00Z",
  "location": "River Street bridge",
  "manualTags": ["flood", "bridge"],
  "textBody": "Water reached the lower inspection marker."
}
```

Required: `projectId`, nonblank `title` (maximum 240 after trimming), and nonblank `textBody`. Optional: `eventTime`, `location` (maximum 240 after trimming), and `manualTags` (each normalized tag is nonblank and at most 80; null list becomes empty). Success returns `201 Created` with a Material whose version number is 1.

## Upload and create FILE material

FILE creation is three steps. The server never receives file bytes in the S3 profile.

### 1. Create upload intents

`POST /api/v1/uploads/intents` requires CSRF.

```json
{
  "files": [
    {
      "originalFilename": "street-a.png",
      "mimeType": "image/png",
      "sizeBytes": 68,
      "checksumSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    },
    {
      "originalFilename": "interview.txt",
      "mimeType": "text/plain",
      "sizeBytes": 42,
      "checksumSha256": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    }
  ]
}
```

`files` must fit the active capabilities policy (defaults: 1–10 entries, 52,428,800 bytes per file, 209,715,200 bytes total). Each entry requires a nonblank filename of at most 255 characters with no control characters, a nonblank MIME type of at most 160 characters with no control characters, a positive size, and a 64-digit hexadecimal SHA-256. Accepted types are `image/*`, `video/*`, `audio/*`, `application/pdf`, and `text/plain`.

Success: `201 Created`.

```json
{
  "files": [
    {
      "intentId": "77777777-7777-4777-8777-777777777777",
      "objectKey": "uploads/11111111-1111-4111-8111-111111111111/99999999-9999-4999-8999-999999999999/street-a.png",
      "uploadUrl": "https://storage.example.invalid/signed-upload",
      "requiredHeaders": {
        "Content-Type": "image/png",
        "x-amz-checksum-sha256": "<base64-checksum>"
      },
      "expiresAt": "2026-08-22T16:15:00Z"
    }
  ]
}
```

The local profile instead returns `/api/v1/local-storage/uploads/{intentId}` and only a `Content-Type` required header. Intents expire after 15 minutes by default and belong to the requesting user.

### 2. Upload each file

Send one `PUT` to each `uploadUrl`, preserving every `requiredHeaders` entry. S3/MinIO receives the bytes directly. The local profile's same-origin PUT also requires session and CSRF and returns `204 No Content`.

### 3. Finalize the FILE material

`POST /api/v1/materials/file` requires CSRF and `Idempotency-Key`.

```json
{
  "projectId": "22222222-2222-4222-8222-222222222222",
  "title": "Street-level evidence",
  "eventTime": "2026-08-22T14:25:00Z",
  "location": "River Street",
  "manualTags": ["photo", "evidence"],
  "textContent": "Optional notes for the whole material.",
  "uploadIntentIds": [
    "77777777-7777-4777-8777-777777777777",
    "78787878-7878-4787-8787-787878787878"
  ]
}
```

Required: `projectId`, nonblank `title` (maximum 240 after trimming), and a capability-compliant list of distinct, owned, pending, unexpired, successfully uploaded intent IDs. Optional: `eventTime`, `location` (maximum 240 after trimming), `manualTags` (each normalized tag is nonblank and at most 80), and `textContent`. The order of `uploadIntentIds` becomes display order. Finalization re-applies count, per-file, total-size, MIME, and checksum verification before returning `201 Created`.

## Read current and historical versions

### Current

`GET /api/v1/materials/{materialId}` returns `200 OK`.

```json
{
  "id": "33333333-3333-4333-8333-333333333333",
  "projectId": "22222222-2222-4222-8222-222222222222",
  "projectTitle": "Downtown Flood",
  "ownerId": "11111111-1111-4111-8111-111111111111",
  "ownerUsername": "emma",
  "type": "TEXT",
  "materialCreatedTime": "2026-08-22T15:10:00Z",
  "currentVersionId": "44444444-4444-4444-8444-444444444444",
  "canEdit": true,
  "historical": false,
  "version": {
    "id": "44444444-4444-4444-8444-444444444444",
    "versionNumber": 2,
    "createdBy": "11111111-1111-4111-8111-111111111111",
    "creatorUsername": "emma",
    "versionCreatedTime": "2026-08-22T15:30:00Z",
    "snapshot": {
      "title": "Verified bridge note",
      "eventTime": "2026-08-22T14:20:00Z",
      "location": "River Street bridge",
      "manualTags": ["verified"],
      "content": {"kind":"TEXT","textBody":"The measurement was confirmed."}
    }
  }
}
```

### Version list

`GET /api/v1/materials/{materialId}/versions` returns lightweight summaries ordered by ascending `versionNumber`; it never includes snapshots, text, files, checksums, or AI data:

```json
[
  {
    "id": "44444444-4444-4444-8444-444444444444",
    "versionNumber": 2,
    "createdBy": "11111111-1111-4111-8111-111111111111",
    "creatorUsername": "emma",
    "versionCreatedTime": "2026-08-22T15:30:00Z",
    "title": "Verified bridge note"
  }
]
```

Select `GET /api/v1/materials/{id}/versions/{number}` for one complete read-only snapshot. The current API does not paginate version history.

### One historical version

`GET /api/v1/materials/{materialId}/versions/{versionNumber}` is always the explicit read-only version route and returns the same Material layout as current. It sets `historical:true` and `canEdit:false`, even for the owner and even when `versionNumber` equals the current version. The editable current view is available only from `GET /api/v1/materials/{materialId}`.

### FILE asset and image analysis

Each object in `snapshot.content.files` has:

```json
{
  "id": "66666666-6666-4666-8666-666666666666",
  "uploaderId": "11111111-1111-4111-8111-111111111111",
  "uploaderUsername": "emma",
  "originalFilename": "street-a.png",
  "mimeType": "image/png",
  "sizeBytes": 68,
  "checksumSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
  "fileCreatedTime": "2026-08-22T15:12:00Z",
  "imageAnalysis": {
    "status": "COMPLETED",
    "aiDescription": "Local demo analysis for image 0123456789ab",
    "aiTags": ["image", "png", "demo-analysis"],
    "generatedTime": "2026-08-22T15:12:05Z"
  }
}
```

`imageAnalysis` is null for non-images. For images, `status` is `PENDING`, `PROCESSING`, `COMPLETED`, or `FAILED`; description/tags/time are nullable or empty until completion. Analysis belongs to the immutable FileAsset, not to the whole material.

## Create a new version

`POST /api/v1/materials/{materialId}/versions` requires owner, CSRF, and `Idempotency-Key`. It always creates a full immutable snapshot; it never edits an old row.

TEXT request:

```json
{
  "expectedCurrentVersion": 1,
  "title": "Verified bridge note",
  "eventTime": "2026-08-22T14:20:00Z",
  "location": "River Street bridge",
  "manualTags": ["verified"],
  "textBody": "The measurement was confirmed."
}
```

FILE request uses one ordered selector per file:

```json
{
  "expectedCurrentVersion": 1,
  "title": "Reordered street evidence",
  "eventTime": null,
  "location": "River Street",
  "manualTags": ["evidence"],
  "textContent": "Second snapshot with one retained and one new file.",
  "files": [
    {
      "kind": "EXISTING",
      "fileId": "66666666-6666-4666-8666-666666666666",
      "uploadIntentId": null
    },
    {
      "kind": "UPLOAD",
      "fileId": null,
      "uploadIntentId": "78787878-7878-4787-8787-787878787878"
    }
  ]
}
```

An `EXISTING` selector may reference only a distinct file in the current version. An `UPLOAD` selector requires a newly uploaded, owned intent. The list must fit the active capability policy and is the exact new display order. Legacy top-level `fileIds` and `uploadIntentIds` are not accepted by the next-version endpoint.

The version request is a strict union. TEXT accepts `textBody` and rejects `textContent` or `files` even when an opposite-type field is explicitly null. FILE accepts optional `textContent` plus required ordered `files` and rejects `textBody`. Unknown properties and wrong/ignored union fields fail with `400` before idempotency fingerprinting.

`expectedCurrentVersion` is required. Version requests use the same 240-character title/location limits, nonblank 80-character tag-item limit, and trimming/null normalization as creation. If another write has advanced the material, the server returns `409 VERSION_CONFLICT`; reload current state before retrying.

## Browse and search current material

`GET /api/v1/projects/{projectId}/materials` returns the current version of every Material in one project. The project page calls it without filters so users can browse the list immediately. The endpoint retains its earlier optional filter parameters for compatibility, including location within its legacy `q` corpus, but the global Search page does not use them.

`GET /api/v1/materials/search` searches current material versions across every project. An empty request returns all current materials. Optional query parameters:

| Parameter | Behavior |
| --- | --- |
| `q` | Comma-separated terms. Terms are trimmed, lowercased, de-duplicated, and combined with OR. Each term is a case-insensitive substring across current title, manual tags, TEXT body or FILE notes, and completed image AI description/tags. Location is not part of the keyword corpus |
| `fileType` | `ALL`, `TEXT`, `IMAGE`, `VIDEO`, `AUDIO`, or `OTHER` |
| `eventFrom`, `eventTo` | Inclusive event-time bounds; null event time does not match a bound |
| `location` | Case-insensitive substring |

`TEXT` selects TEXT materials. The other non-`ALL` categories select FILE materials by current-file MIME type. A multi-file material matches when any current file belongs to the selected category. `OTHER` means a current file whose MIME type is not image, video, or audio.

A material matches when at least one keyword term matches. `matchedTermCount` is the number of distinct query terms found anywhere in that material's current search documents. Results sort by `matchedTermCount` descending, then event time descending with nulls last, then material-created time descending. This is literal substring matching, not typo correction; for example, `flo` matches `flood`, while `flod` does not.

Example:

```http
GET /api/v1/materials/search?q=flood,bridge&fileType=IMAGE&location=river
```

```json
[
  {
    "id": "33333333-3333-4333-8333-333333333333",
    "projectId": "22222222-2222-4222-8222-222222222222",
    "projectTitle": "Downtown Flood",
    "ownerId": "11111111-1111-4111-8111-111111111111",
    "ownerUsername": "emma",
    "type": "FILE",
    "materialCreatedTime": "2026-08-22T15:12:00Z",
    "currentVersionId": "44444444-4444-4444-8444-444444444444",
    "currentVersionNumber": 1,
    "title": "Street-level evidence",
    "eventTime": null,
    "location": "River Street",
    "manualTags": ["evidence"],
    "matchedFileId": "66666666-6666-4666-8666-666666666666",
    "matchedTermCount": 2
  }
]
```

`matchedFileId` is non-null only when an image-AI search row caused the match, allowing the UI to highlight that file. Project-list responses use the same result shape with `matchedTermCount: 0` when no keyword query was supplied.

## Download and AI retry

### Download

`POST /api/v1/files/{fileId}/download-url` requires CSRF and returns `200 OK`.

```json
{
  "downloadUrl": "https://storage.example.invalid/signed-download",
  "expiresAt": "2026-08-22T16:05:00Z"
}
```

The URL expires after five minutes. In local mode it is a same-origin authenticated route. Any authenticated user may download any known FileAsset.

### Retry failed analysis

`POST /api/v1/files/{fileId}/analysis/retry` requires CSRF and material ownership. It only accepts a `FAILED` image analysis.

```json
{
  "status": "PENDING",
  "attemptCount": 0,
  "nextAttemptTime": "2026-08-22T16:00:00Z"
}
```

Success is `200 OK`. A non-owner gets `403 FORBIDDEN`; a non-image or missing analysis gets `404 ANALYSIS_NOT_FOUND`; a non-failed analysis gets `409 ANALYSIS_NOT_FAILED`.

## Idempotency

The key scope is authenticated user + operation + normalized `Idempotency-Key`. Operations are create TEXT, create FILE, and create version. The key is trimmed before lookup and storage; its normalized value must be nonblank and at most 255 characters.

Before both persistence and fingerprinting, the server applies the field normalization defined above: trimmed title/location/manual-tag values, first-occurrence tag deduplication, trimmed TEXT body, and trimmed-or-null FILE notes. Therefore whitespace-only differences in those normalized fields or the key replay the original result. Meaningful characters, tag order, event time, project/material IDs, file order, and file selectors remain fingerprint-significant.

- Same normalized key and same normalized request fingerprint: returns the originally recorded material/version with `201`; no duplicate is created.
- Same key and different request: `409 IDEMPOTENCY_CONFLICT`.
- A replay of an older successful version may return `historical:true` if a later version is now current.
- Reuse the same key only for a retry of the exact same logical request.

## Error envelope

Handled application and validation errors use:

```json
{
  "code": "VERSION_CONFLICT",
  "message": "The material has a newer version",
  "requestId": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
  "details": []
}
```

Implemented stable statuses and codes:

| HTTP | Codes |
| --- | --- |
| 400 | `VALIDATION_ERROR`, `INVALID_REQUEST`, `INVALID_VERSION_CONTENT`, `INVALID_UPLOAD_REQUEST` |
| 401 | `AUTHENTICATION_REQUIRED`, `INVALID_CREDENTIALS` |
| 403 | `FORBIDDEN` (authorization or CSRF) |
| 404 | `PROJECT_NOT_FOUND`, `MATERIAL_NOT_FOUND`, `VERSION_NOT_FOUND`, `FILE_NOT_FOUND`, `UPLOAD_INTENT_NOT_FOUND`, `ANALYSIS_NOT_FOUND` |
| 409 | `VERSION_CONFLICT`, `IDEMPOTENCY_CONFLICT`, `UPLOAD_INTENT_CONSUMED`, `UPLOAD_ALREADY_EXISTS`, `ANALYSIS_NOT_FAILED` |
| 410 | `UPLOAD_INTENT_EXPIRED` |
| 413 | `UPLOAD_LIMIT_EXCEEDED` |
| 415 | `UNSUPPORTED_FILE_TYPE` |
| 422 | `INVALID_FILE_SET`, `UPLOAD_METADATA_MISMATCH`, `UPLOAD_NOT_VERIFIED` |
| 503 | `STORAGE_UNAVAILABLE` |

Malformed JSON/unknown enums or properties, invalid UUID/path types, missing headers/parameters, bean validation, method validation, and constraint violations all use exactly `code`, `message`, `requestId`, and `details`. Field-level validation details contain the real field and validator message. Authentication, authorization, and CSRF errors use the same envelope shape.
