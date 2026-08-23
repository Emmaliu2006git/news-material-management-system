# Five-Minute Demo Script

This script uses the packaged local application. It needs Java 21+, but no PostgreSQL, Docker, AWS, S3, OpenAI key, or real newsroom data.

## Before the demo

From the repository root:

```bash
./mvnw package
java -jar target/news-material-management-system-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=local \
  --app.analysis.scheduling-enabled=true
```

Open `http://127.0.0.1:8080`.

Prepare two small synthetic PNG files:

- `street-demo.png` — expected local AI success.
- `fail-ai-demo.png` — the `fail-ai-` prefix deliberately exercises local AI failure.

Demo accounts:

| User | Password | Purpose |
| --- | --- | --- |
| `emma` | `demo-password` | Creates and versions material |
| `alex` | `demo-password` | Demonstrates shared viewing and owner-only editing |

The local database is in memory. Restarting the application resets the demo.

## 0:00–0:35 — Sign in and create the reporting project

1. Sign in as `emma`.
2. In **Start a project**, enter `Downtown Flood Demo`.
3. Select **Create project**, then open the new project card.

Expected visible result:

- The top bar shows signed-in user `emma`.
- The project page heading is `Downtown Flood Demo`.
- The page states that searches include current material versions only.
- **Add material** is available.

Narration: a project is the shared event container. Every signed-in user can add independently owned material to it.

## 0:35–1:25 — Create and version a TEXT material

1. Select **Add material** and leave **Text material** selected.
2. Enter:
   - Title: `Initial bridge note`
   - Event time: any demo time
   - Location: `River Street bridge`
   - Manual tags: `flood, field-note`
   - Text body: `Water reached old-marker near the lower inspection point.`
3. Select **Create material**.
4. On the detail page, select **Create new version**.
5. Change:
   - Title: `Verified bridge note`
   - Manual tags: `flood, verified`
   - Text body: `Water level confirmed; current-marker recorded by the bridge team.`
6. Select **Save new version**.

Expected visible result:

- The detail page shows `Version 2`.
- Material ID, project, owner, material-created time, version creator, and version-created time remain visible.
- **Version history** contains Version 2 and Version 1.
- Opening Version 1 shows a **Historical version 1 · Read-only snapshot** banner and the original title/body.
- **View current version** returns to Version 2.

Narration: versions are immutable full snapshots. Only the owner can append the next linear version.

## 1:25–2:00 — Prove current-only search

1. Return through **Project materials**.
2. Search for `old-marker`.

Expected: `0 found`.

3. Search for `current-marker`.

Expected: the `Verified bridge note` result appears with `v2`.

Narration: history remains viewable, but search intentionally indexes only the current version.

## 2:00–3:10 — Multi-file upload and per-image AI isolation

1. Select **Add material**, then **File material**.
2. Enter:
   - Title: `Street photo evidence`
   - Location: `River Street`
   - Manual tags: `photo, evidence`
   - Files: select both `street-demo.png` and `fail-ai-demo.png`
   - Material notes: `Two image assets from the same reporting stop.`
3. Select **Create material**.
4. If an analysis is still pending, wait about five seconds and refresh.

Expected visible result:

- Upload progress reaches 100% for both files before navigation.
- The material has two separate file cards in the selected order.
- `street-demo.png` reaches `COMPLETED`, shows a **Local demo analysis for image …** description, and AI tags including `demo-analysis`.
- `fail-ai-demo.png` reaches `FAILED` and shows **Retry AI analysis**.
- The material title, notes, both downloads, and the successful analysis remain usable despite the second analysis failure.
- Selecting retry returns the failed item to `PENDING`; because the filename intentionally requests failure, it will fail again after processing.

Narration: AI belongs to each image FileAsset. Its failure is isolated and never rolls back the material.

## 3:10–3:40 — Search AI metadata and download

1. Return to **Project materials**.
2. Search `demo-analysis`.

Expected:

- `Street photo evidence` appears.
- The result says **Matched an AI-analyzed image**.
- Opening it highlights the matched image card.

3. Select **Download street-demo.png**.

Expected: the browser downloads the same filename.

Narration: manual tags describe the whole material; AI tags and descriptions stay attached to one image but participate in current-version search.

## 3:40–4:35 — Demonstrate second-user permissions

1. Log out.
2. Sign in as `alex`.
3. Open `Downtown Flood Demo`, then open both existing materials.

Expected visible result:

- Alex can search, view current snapshots, open Version 1, and download files.
- **Create new version** is absent on Emma's materials.
- **Retry AI analysis** is absent for Alex.
- **Add material** remains available because Alex may add a separate material to the same reporting project.

Narration: the system has one user type, not editor/journalist roles. Ownership controls only version creation and AI retry; shared reading and independent contribution remain open to signed-in users.

## 4:35–5:00 — Close on the scope boundary

Show the project list and summarize:

- Implemented: projects, TEXT/FILE materials, multi-file direct-upload contract, immutable linear history, current-only search, download, and per-image AI status/metadata.
- Intentionally absent: teams, roles, Kafka, native mobile app, delete, media editing, branches/merges, vector search, and AI for video/audio/text.
- The same responsive web application is used on desktop and phone.

## Mobile viewport checklist

Desktop browser emulation is automated only for the desktop journey. Complete this checklist manually at a narrow viewport such as 390 × 844, then repeat the upload on one supported real phone before a production-style presentation.

- [ ] Login card fits without horizontal scrolling.
- [ ] Header, logout control, breadcrumbs, and primary actions remain reachable.
- [ ] Project cards and search filters collapse into one readable column.
- [ ] **Add material** opens the same TEXT/FILE form.
- [ ] Native file picker opens and accepts multiple supported files.
- [ ] Every selected filename has visible hashing/upload progress.
- [ ] A failed upload leaves a clear retry-safe error.
- [ ] Material metadata, notes, file cards, AI status, and download controls fit the viewport.
- [ ] Version history is readable; historical banner and **View current version** are visible.
- [ ] Search returns the same current-only result set as desktop.
- [ ] No page has clipped content or unintended horizontal scrolling.

Expected result: all items pass. Real-device mobile upload is a manual acceptance item because this environment cannot verify device permissions, mobile browser file pickers, LAN routing, or MinIO CORS.
