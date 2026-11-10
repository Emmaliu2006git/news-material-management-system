# News Material Management System

A responsive internal newsroom web application for organizing reporting projects, text notes, and uploaded evidence. Users can search the current state of material, inspect immutable version history, download files, and see per-image AI descriptions/tags. Material owners alone can create the next version; every signed-in user can view material and add independent material to a project.

The browser UI and `/api/v1` API are served from the same Spring Boot application in the packaged and Docker demos. There is no native mobile app: phones use the same responsive URL in a browser.

## Documentation

- [`docs/design.md`](docs/design.md) — scope, architecture, domain model, and acceptance criteria.
- [`docs/api.md`](docs/api.md) — exact authentication, CSRF, fields, endpoints, errors, upload, version, search, download, and retry contracts.
- [`docs/demo-script.md`](docs/demo-script.md) — five-minute local demo and manual mobile viewport checklist.
- [`docs/operations.md`](docs/operations.md) — profiles, environment variables, PostgreSQL/Flyway, private S3, OpenAI privacy, recovery, rollback, and limitations.

Start with the packaged demo below for the shortest working path. Use the operations guide before Compose or cloud deployment.

## Prerequisites

- Java 21 or newer
- Node.js 22 or newer and npm (for local development and tests)
- `curl`
- Docker Desktop with WSL integration (only for the Compose demo on Windows/WSL)

The Maven wrapper downloads pinned Node.js 22.20.0 and npm 10.9.3 when packaging, so a system Node installation is not required for `./mvnw package`.

## Local development

From the repository root:

```bash
./scripts/dev.sh
```

Open `http://localhost:5173`. The script installs the locked frontend dependencies, starts Spring Boot with the local H2/filesystem profile on port 8080, waits for health, and starts Vite with an API proxy. Local data is disposable.

## Packaged same-origin demo

```bash
./mvnw package
java -jar target/news-material-management-system-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=local \
  --app.analysis.scheduling-enabled=true
```

Open `http://localhost:8080`. Maven runs locked frontend install, type checking, unit tests, and production build before copying the SPA into the executable JAR and running backend tests.

## Demo accounts

Both local and Docker demo profiles seed ordinary users only; there are no roles or administrators.

| User | Password |
| --- | --- |
| `emma` | `demo-password` |
| `alex` | `demo-password` |

## Tests

Run every check, including the packaged-JAR browser journey:

```bash
./scripts/test-all.sh
```

The deterministic order is: locked install, type check, lint, frontend unit tests, frontend build, backend verification/package, Playwright Chromium install, and packaged-JAR E2E in desktop Chromium and an emulated Pixel 7. Both projects run the same generated login, project, history/search, multi-file upload/download, AI-isolation, and owner-authorization journey.

Focused packaging is also available with:

```bash
./mvnw verify
```

## Docker Compose demo

```bash
cp .env.example .env
# Replace every placeholder in .env.
docker compose up --build
```

Open `http://localhost:8080`. Compose starts the application, PostgreSQL with Flyway migrations, MinIO, and a one-shot bucket initializer. The bucket remains private, services have health checks, and PostgreSQL/MinIO use named volumes. The default AI provider is deterministic and local; no external API key is needed.

This repository was developed in WSL where the Docker daemon was unavailable, so Compose is guarded by an automated configuration contract but was not live-started here. On Windows, enable the distribution under **Docker Desktop → Settings → Resources → WSL Integration** before running it.

To open the demo from another device, set `NEWS_MATERIAL_S3_PRESIGNER_ENDPOINT` and `MINIO_API_CORS_ALLOW_ORIGIN` in `.env` to hostnames/IPs that device can reach. `localhost` on a phone means the phone itself.
The Compose HTTP demo explicitly sets `SERVER_SERVLET_SESSION_COOKIE_SECURE=false`. Base/cloud configuration is fail-safe (`Secure`, `HttpOnly`, `SameSite=Lax`) and expects HTTPS. Do not carry the demo override into a shared TLS deployment.

Stop services with `docker compose down`. Add `--volumes` only when you intentionally want to delete demo database and object data.

## Production configuration

No cloud account is seeded and no registration endpoint exists. Use the supported one-time `provision-user` procedure in [`docs/operations.md`](docs/operations.md#one-time-cloud-account-provisioning).

### PostgreSQL extension prerequisite

Migration `V4` runs `CREATE EXTENSION IF NOT EXISTS pg_trgm` because the current-version search index uses PostgreSQL trigram operators. If the extension is not already installed, the database identity used by Flyway must have permission to create it and the managed service must allow it. An application role without that permission will fail startup at `V4`.

For a least-privilege production setup, have a database administrator preinstall the extension once, before the application's first startup. The regular application/Flyway role can then remain unable to create extensions:

```bash
psql --host=db.example.internal --dbname=news_material --username=db_admin \
  --command='CREATE EXTENSION IF NOT EXISTS pg_trgm;'
```

Use your provider's interactive, IAM, or secret-manager authentication; do not put a password in the command or repository. Verify the prerequisite without exposing credentials:

```bash
psql --host=db.example.internal --dbname=news_material --username=news_material_app \
  --command="SELECT extname, extversion FROM pg_extension WHERE extname = 'pg_trgm';"
```

Replace the example host, database, and user identifiers for your environment; none of them should contain a password.

The verification must return one `pg_trgm` row before Flyway runs. On managed PostgreSQL, follow the provider's allowlist/extension workflow if direct `CREATE EXTENSION` is restricted.

For AWS S3, leave `NEWS_MATERIAL_S3_ENDPOINT` and `NEWS_MATERIAL_S3_PRESIGNER_ENDPOINT` empty, set `NEWS_MATERIAL_S3_PATH_STYLE=false`, and provide:

- `NEWS_MATERIAL_S3_BUCKET`
- `AWS_REGION`
- AWS credentials through the standard AWS SDK credential chain (for example an IAM task/instance role)
- PostgreSQL values through `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD`

For OpenAI image analysis, set:

```text
NEWS_MATERIAL_AI_PROVIDER=openai
OPENAI_API_KEY=replace-with-a-secret-from-your-secret-manager
```

Optional settings are `OPENAI_BASE_URL`, `OPENAI_VISION_MODEL`, `OPENAI_CONNECT_TIMEOUT`, and `OPENAI_REQUEST_TIMEOUT`. Never commit real secrets or use the demo passwords in a shared deployment.

## Architecture

```text
Responsive React SPA ── same-origin HTTP ── Spring Boot API
                                             ├── PostgreSQL + Flyway (metadata, users, versions, AI state)
                                             ├── private S3/MinIO (file bytes via presigned URLs)
                                             └── scheduled image-analysis worker (local or OpenAI provider)
```

File bytes travel directly between the browser and S3/MinIO using short-lived presigned URLs. Spring Boot authorizes upload/download intent and stores metadata. Search intentionally queries current material versions only. Image analysis is asynchronous and isolated per image, so a failed analysis never makes the material unusable.

## Current limitations

- Desktop and Pixel 7 emulation are automated; a real phone file picker, device permissions, and LAN/TLS routing remain manual acceptance checks.
- No native iOS/Android app; use the responsive web URL.
- No teams, roles, administrator workflow, notifications, or cross-project provenance tracking.
- Global search uses comma-separated OR substring terms and ranks materials by the number of distinct matched terms. It is not typo-correcting or semantic/vector search, and it does not search historical versions.
- AI analysis applies to images only. Local AI output is deterministic demo data.
- The local H2 database is in memory and resets when the process stops.
- Version history is linear; only the material owner can append a version.

## Troubleshooting

- **Java/Node diagnostic:** run the scripts; they stop early with the missing prerequisite and required version.
- **Port already used:** free ports 5173/8080 for development, or 8080/9000/9001 for Compose.
- **Browser test cannot launch:** rerun `./scripts/test-all.sh`; it installs the pinned Playwright browser and does not skip a missing browser.
- **Phone can open the UI but uploads fail:** use reachable LAN hostnames in the presigner and MinIO CORS values, then restart Compose. Browser hashing has a pinned JavaScript fallback for plain-LAN/insecure contexts, although HTTPS or a trusted tunnel is recommended.
- **Compose cannot connect in WSL:** enable Docker Desktop WSL integration and confirm `docker info` works inside the distribution.
- **AI stays pending in a hand-started local JAR:** include `--app.analysis.scheduling-enabled=true` as shown above.
