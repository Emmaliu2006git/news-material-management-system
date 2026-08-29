# Global Material Search and Project Browsing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move search outside individual projects, provide a directly visible material list inside each project, and rank comma-separated OR matches across all projects.

**Architecture:** Keep project browsing and global search as two explicit read flows. The project endpoint returns all current material snapshots for one project; a new global endpoint searches current snapshots across every project, calculates a distinct matched-keyword count, filters by event time, location, and file category, and returns globally ranked results with project identity.

**Tech Stack:** Spring Boot 4.1, Spring Data JPA, PostgreSQL/H2, React 19, TypeScript, React Router, Vitest, Spring MockMvc, Playwright.

**Spec:** `docs/design.md` (update the confirmed search and browsing decisions in Task 4).

## Global Constraints

- Search current material versions only; historical versions never match.
- Comma-separated query terms use case-insensitive substring matching with OR semantics.
- Rank by distinct matched-term count descending, then event time descending with missing event times last.
- The query searches title, TEXT body or FILE text content, manual tags, AI tags, and AI description.
- The global form contains only query, event-time range, location, and a file-type dropdown.
- File-type choices are `ALL`, `TEXT`, `IMAGE`, `VIDEO`, `AUDIO`, and `OTHER`; a multi-file material matches when any current file belongs to the selected category.
- Project detail pages show the current material list immediately and contain no search form.
- Do not change project ownership or deletion rules in this task.

---

### Task 1: Global Ranked Search API

**Files:**
- Create: `src/main/java/com/example/newsmaterial/search/SearchFileType.java`
- Create: `src/main/java/com/example/newsmaterial/search/GlobalSearchApi.java`
- Modify: `src/main/java/com/example/newsmaterial/search/SearchService.java`
- Modify: `src/main/java/com/example/newsmaterial/search/SearchDocumentRepository.java`
- Modify: `src/main/java/com/example/newsmaterial/file/VersionFileRepository.java`
- Modify: `src/test/java/com/example/newsmaterial/search/SearchApiTest.java`

**Interfaces:**
- Produces: `GET /api/v1/materials/search?q=&fileType=&eventFrom=&eventTo=&location=`.
- Produces: `SearchService.globalSearch(GlobalSearchFilters)` and `SearchFileType`.
- Produces: search results containing `projectId`, `projectTitle`, and `matchedTermCount`.

- [ ] **Step 1: Write failing API tests**

Add integration cases proving that `flood, bridge` returns materials matching either term, a two-term result precedes a one-term result, matches can come from manual tags and per-image AI documents, current historical text is excluded, the event/location filters compose, and each file-type category uses the current version's files.

```java
MvcResult result = mvc.perform(get("/api/v1/materials/search")
        .session(emma)
        .param("q", "flood, bridge")
        .param("fileType", "IMAGE")
        .param("location", "downtown"))
    .andExpect(status().isOk())
    .andReturn();
assertThat(JsonPath.<List<Integer>>read(body, "$[*].matchedTermCount"))
    .isSortedAccordingTo(Comparator.reverseOrder());
```

- [ ] **Step 2: Run the focused backend test and verify RED**

Run: `./mvnw -q -Dskip.frontend=true -Dtest=SearchApiTest test`

Expected: FAIL because `/api/v1/materials/search`, `SearchFileType`, project titles, and match scores do not exist.

- [ ] **Step 3: Implement query parsing, scoring, filtering, and file categories**

Use these exact public shapes:

```java
public enum SearchFileType { ALL, TEXT, IMAGE, VIDEO, AUDIO, OTHER }

public record GlobalSearchFilters(
    String q, SearchFileType fileType, Instant eventFrom,
    Instant eventTo, String location) {}
```

Split `q` on commas, trim, lowercase with `Locale.ROOT`, remove empty and duplicate terms. For each current material, count how many distinct terms appear in any current material-level or image-AI search document. Preserve one matched image file ID when an AI document contributes. Sort by score descending, event time descending with null last, material creation time descending, then material ID.

Classify MIME types using `image/`, `video/`, and `audio/` prefixes. `OTHER` means a FILE material with at least one current file outside those prefixes; `TEXT` means a TEXT material.

- [ ] **Step 4: Run focused backend tests and verify GREEN**

Run: `./mvnw -q -Dskip.frontend=true -Dtest=SearchApiTest test`

Expected: PASS with zero failures.

### Task 2: Global Search Page

**Files:**
- Create: `frontend/src/search/MaterialSearchPage.tsx`
- Modify: `frontend/src/api/types.ts`
- Modify: `frontend/src/api/client.ts`
- Modify: `frontend/src/components/Layout.tsx`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/styles.css`

**Interfaces:**
- Consumes: `GET /api/v1/materials/search` from Task 1.
- Produces: authenticated route `/search` and navigation link `Search materials`.

- [ ] **Step 1: Write failing frontend tests**

Test that the navigation opens `/search`; the form exposes only keyword, file type, event-time from/to, and location; removed owner/created-time/tag fields are absent; comma input is sent unchanged; and results show project title plus backend order.

```tsx
await actor.type(screen.getByLabelText(/keywords/i), 'flood, bridge')
await actor.selectOptions(screen.getByLabelText(/file type/i), 'IMAGE')
await actor.click(screen.getByRole('button', { name: /^search$/i }))
expect(screen.getAllByRole('link', { name: /material/i })[0]).toHaveTextContent('Bridge')
```

- [ ] **Step 2: Run `App.test.tsx` and verify RED**

Run: `cd frontend && npm test -- App.test.tsx`

Expected: FAIL because the global route, form, and project-aware result cards do not exist.

- [ ] **Step 3: Implement the global search page**

Add `SearchFileType` and `GlobalSearchFilters` TypeScript types. Build the query string with only non-empty values. The keyword helper says comma-separated terms match title, text, manual/AI tags, or AI description, and that any term may match. Render server-ranked results without client re-sorting.

- [ ] **Step 4: Run focused frontend tests and verify GREEN**

Run: `cd frontend && npm test -- App.test.tsx`

Expected: PASS with zero failures.

### Task 3: Project Material Browsing

**Files:**
- Modify: `frontend/src/projects/ProjectDetailPage.tsx`
- Modify: `frontend/src/api/client.ts`
- Modify: `frontend/src/App.test.tsx`
- Modify: `frontend/src/styles.css`

**Interfaces:**
- Consumes: existing `GET /api/v1/projects/{projectId}/materials` with no filters.
- Produces: immediately visible `Materials` list sorted by the backend's material-created-time order.

- [ ] **Step 1: Write a failing project-page test**

Assert that the project page contains no material-search form or Search button, displays `Materials` and its count before any interaction, links each current material, and shows `No materials in this project yet.` for an empty project.

- [ ] **Step 2: Run `App.test.tsx` and verify RED**

Run: `cd frontend && npm test -- App.test.tsx`

Expected: FAIL because the existing project search panel remains.

- [ ] **Step 3: Replace project search with direct browsing**

Keep project loading, breadcrumbs, title, and `Add material`. Fetch the existing project material endpoint once, render the cards directly below the heading, and use the explicit empty-state copy.

- [ ] **Step 4: Run focused frontend tests and verify GREEN**

Run: `cd frontend && npm test -- App.test.tsx`

Expected: PASS with zero failures.

### Task 4: Documentation, Full Verification, and Runtime Refresh

**Files:**
- Modify: `docs/design.md`
- Modify: `docs/api.md`

**Interfaces:**
- Documents: global current-version search, OR term scoring, file categories, global result project identity, project browsing, and unchanged project permissions.

- [ ] **Step 1: Update only the confirmed design sections**

Revise goals/requirements, authorization facts, Image Analysis and Search, Frontend/API, and user-flow descriptions. Preserve unrelated draft/open decisions for later user review.

- [ ] **Step 2: Update the API contract**

Document `/api/v1/materials/search`, allowed filters, file-category semantics, current-only behavior, match count, and stable sorting.

- [ ] **Step 3: Run complete verification**

Run: `./scripts/test-all.sh`

Expected: frontend typecheck/lint/build pass, all frontend and backend tests pass, and desktop/mobile packaged-app Playwright tests pass.

- [ ] **Step 4: Commit, push, and restart the verified JAR**

Stage only task files, leave the unrelated iCloud shortcut untouched, push `codex/account-profile`, restart port 8080, restore the demo project, and verify the health endpoint plus one live global-search request.
