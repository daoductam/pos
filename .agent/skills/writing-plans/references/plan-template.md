# Implementation Plan: <FEATURE_NAME>

Spec Source: `<path-to-spec.md>`
Owner: `<team / developer>`
Last Updated: `<date>`
Status: `Draft | In Review | Approved | In Progress | Done`

---

# 1. Context

**Problem:**
<2-3 sentences: what problem this solves and why it matters>

**Affected Modules:**
- `module_a` — <one-line role in this feature>
- `module_b` — <one-line role in this feature>

**Non-Goals:**
- <things explicitly NOT implemented in this plan>

---

# 2. Constraints

**Language:** `<Java | Kotlin | Go | TypeScript>`
**Architecture:** `<Hexagonal | Clean | MVVM>`

**Rules:**
- Do NOT modify unrelated modules
- Do NOT introduce new external dependencies
- Maintain backward compatibility with existing APIs

**Performance Budgets (if applicable):**
- Latency: < X ms (p99)
- Memory: < X MB

---

# 3. Conventions

> Shared rules ALL tasks must follow. Prevents inconsistency when tasks are
> implemented independently.

**Naming:**
- Classes: `PascalCase` — e.g. `PageLoader`, `ReaderController`
- Methods/variables: `camelCase` — e.g. `splitToPages()`, `currentPage`
- Constants: `UPPER_SNAKE_CASE` — e.g. `MAX_PAGE_SIZE`
- Packages: `<com.company.module.feature>` — e.g. `com.app.reader.core`

**Error Handling:**
- Strategy: throw domain exceptions (never generic RuntimeException)
- Pattern: `throw ReaderException.pageNotFound(pageId)`
- Never return null — use `Optional<T>` or empty collection
- Validation: fail-fast at public method boundaries

**Logging:**
- Library: `<SLF4J | Timber | slog>`
- Format: structured — `log.info("action={} entity={} id={}", action, entity, id)`
- Required at: entry of public methods, error/catch blocks

**Testing:**
- Framework: `<JUnit5 + MockK | pytest | Jest>`
- Naming: `should_<expected>_when_<condition>()`
- Example: `should_returnEmptyList_when_chapterIsEmpty()`
- Coverage: >= 80% line coverage on new code

**Task Size:**
- Max 200 LOC per task (excluding tests)
- If a task exceeds this, split into subtasks

---

# 4. Contracts

> Define ALL interfaces and data structures BEFORE task specs.
> Tasks reference these contracts — not the other way around.

## Interface: `<InterfaceName>`

```
methodA(param: ParamType): ReturnType
  - pre: param must not be null/blank
  - post: returns non-empty result when input is valid
  - throws: DomainException when <condition>

methodB(param: ParamType): ReturnType
  - pre: <precondition>
  - post: <postcondition>
  - throws: <exception when condition>
```

**Example — filled in:**

```
PageLoader.splitToPages(content: String, config: PageConfig): List<Page>
  - pre: content is non-blank; config.maxCharsPerPage > 0
  - post: returns >= 1 page; no page exceeds maxCharsPerPage
  - throws: InvalidContentException when content is blank

ReaderController.goToPage(index: Int): NavigationResult
  - pre: controller is initialized with pages
  - post: currentPage == index when index is in bounds
  - throws: IllegalArgumentException when index < 0
  - returns: NavigationResult(success=false) when index >= totalPages
```

---

## Data Structure: `<EntityName>`

```
EntityName {
  id:        Long           — auto-generated, immutable
  name:      String         — max 255 chars, non-blank
  status:    Enum(A|B|C)    — default = A
  createdAt: Instant        — set on creation, immutable
  updatedAt: Instant        — updated on every mutation
}
```

**Example — filled in:**

```
Page {
  index:     Int            — 0-based, unique within chapter
  content:   String         — non-blank, max = PageConfig.maxCharsPerPage
  isLast:    Boolean        — true only for final page in chapter
}

PageConfig {
  maxCharsPerPage:  Int     — default = 2000, must be > 0
  preserveWords:    Boolean — default = true, never split mid-word
}

NavigationResult {
  success:     Boolean      — true if navigation occurred
  currentPage: Int          — page index after navigation
  totalPages:  Int          — total pages in current chapter
}
```

---

## Shared DTOs (if any):

```
RequestDTO {
  field1: Type  — required, <constraint>
  field2: Type  — optional, default = X
}

ResponseDTO {
  field1: Type  — always present
  field2: Type  — nullable when <condition>
}
```

---

# 5. Target Architecture

**Components:**
- `ComponentA` — <one-line responsibility>
- `ComponentB` — <one-line responsibility>
- `ComponentC` — <one-line responsibility>

**Interaction Flow:**

```
Client
  → ComponentA.methodName(param: Type)
    → ComponentB.methodName(param) → ReturnType
    → ComponentC.methodName(param) → ReturnType
  ← returns ResponseType(field1, field2)
```

**Example — filled in:**

```
UI Layer
  → ReaderController.loadChapter(chapterId: String)
    → PageLoader.splitToPages(content, config) → List<Page>
    → PageCache.store(chapterId, pages) → CacheResult
  ← returns ReaderState(currentPage=0, totalPages=N, content=firstPage)

User swipe event
  → ReaderController.nextPage()
    → PageCache.get(chapterId, nextIndex) → Page?
  ← returns NavigationResult(success, currentPage, totalPages)
```

**Key Decisions:**
- ReaderController owns navigation state — single source of truth
- PageLoader is stateless and pure — no side effects, easy to test
- PageCache is bounded — max N pages in memory, LRU eviction

---

# 6. Artifact Registry

> Files that will exist after all tasks are complete.
> Each artifact traces back to a contract and an owner task.

| Artifact | Type | Owner Task | Implements |
|----------|------|------------|------------|
| `path/to/FileA.kt` | class | TASK-001 | `InterfaceName` |
| `path/to/FileB.kt` | class | TASK-002 | `InterfaceName` |
| `path/to/FileC.kt` | class | TASK-003 | — (internal) |
| `path/to/FileDTest.kt` | test | TASK-004 | — |

**Example — filled in:**

| Artifact | Type | Owner Task | Implements |
|----------|------|------------|------------|
| `reader/controller/ReaderController.kt` | class | TASK-001 | `ReaderInterface` |
| `reader/core/PageLoader.kt` | class | TASK-002 | `PageLoaderInterface` |
| `reader/cache/PageCache.kt` | class | TASK-003 | `CacheInterface` |
| `reader/ui/ReaderNavigation.kt` | class | TASK-004 | — |
| `reader/test/ReaderIntegrationTest.kt` | test | TASK-005 | — |

---

# 7. Task Graph

| ID | Name | Depends On | Effort |
|----|------|------------|--------|
| TASK-001 | <task name> | — | S / M / L |
| TASK-002 | <task name> | TASK-001 | S / M / L |
| TASK-003 | <task name> | TASK-001 | S / M / L |
| TASK-004 | <task name> | TASK-001 | S / M / L |
| TASK-005 | <task name> | TASK-002, TASK-003, TASK-004 | S / M / L |

**Dependency Graph:**

```
TASK-001
 ├── TASK-002 ──┐
 ├── TASK-003 ──┼── TASK-005
 └── TASK-004 ──┘
```

**Execution Rules:**
- TASK-002, TASK-003, TASK-004 can run in parallel after TASK-001
- TASK-005 requires all three complete

---

# 8. Task Specifications

## TASK-001: <task name>

**Description:**
<2-3 sentences: what this task implements>

**Input:**
- None (root task)
- OR: From TASK-XXX — `SpecificType` via `method()` call

**Output:**
- `ConcreteArtifact` — consumed by TASK-XXX, TASK-YYY

**Files:**
- `path/to/file1` — **create**: <what this file does>
- `path/to/file2` — **modify**: <what changes>

**Responsibilities:**
- Implement `InterfaceName.methodA()` per contract in Section 4
- Validate input per `EntityName` constraints in Section 4

**Acceptance Criteria:**
- [ ] `methodA("valid")` → returns `ExpectedResult`
- [ ] `methodA("")` → throws `InvalidContentException`
- [ ] `methodA(null)` → throws `IllegalArgumentException`
- [ ] Edge case #N from Section 9 handled: <specific behavior>
- [ ] Unit tests pass with >= 80% coverage
- [ ] Code compiles with zero warnings

---

## TASK-002: <task name>

**Description:**
<2-3 sentences>

**Input:**
- From TASK-001: `InterfaceName` — calls `methodA()` to get data

**Output:**
- `ArtifactName` — consumed by TASK-005 for integration test

**Files:**
- `path/to/file` — **create**: <description>

**Responsibilities:**
- Transform raw data into `ProcessedType` format
- Handle edge case: empty input → return empty result (not error)

**Acceptance Criteria:**
- [ ] `process([item1, item2])` → returns result with 2 entries
- [ ] `process([])` → returns empty result (not exception)
- [ ] Processing time < 50ms for 1000 items
- [ ] Integration: `taskOneOutput |> taskTwoMethod()` works end-to-end

---

> Repeat for each task. Every task MUST have:
> - Input (with source task reference)
> - Output (with consumer task reference)
> - Concrete acceptance criteria (input → expected output)
> - Reference to relevant edge cases from Section 9

---

# 9. Edge Cases

| # | Scenario | Expected Behavior | Handled In |
|---|----------|-------------------|------------|
| 1 | <description> | <concrete behavior> | TASK-XXX |
| 2 | <description> | <concrete behavior> | TASK-XXX |
| 3 | <description> | <concrete behavior> | TASK-XXX |

**Example — filled in:**

| # | Scenario | Expected Behavior | Handled In |
|---|----------|-------------------|------------|
| 1 | Empty chapter content | return `List<Page>` with 0 items | TASK-002 |
| 2 | nextPage() at last page | return `NavigationResult(success=false)`, stay on current | TASK-001 |
| 3 | previousPage() at page 0 | return `NavigationResult(success=false)`, stay on page 0 | TASK-001 |
| 4 | Chapter with 1 character | return single page, `isLast=true` | TASK-002 |
| 5 | goToPage(-1) | throw `IllegalArgumentException` | TASK-001 |

**Mandatory edge cases (include when applicable):**

> **Multi-Tenant / Authorization** — required when the project uses tenant-scoped data:

| # | Scenario | Expected Behavior | Handled In |
|---|----------|-------------------|------------|
| MT-1 | User A requests resource owned by User B (IDOR) | Return 403 Forbidden, log suspicious access attempt | TASK-XXX |
| MT-2 | Request missing tenant context (no tenant ID in auth) | Reject with 400 Bad Request, never fall back to "all tenants" | TASK-XXX |
| MT-3 | Bulk operation with mixed-tenant items | Reject entire batch with 400, list offending item IDs | TASK-XXX |
| MT-4 | Tenant ID in URL path differs from auth token tenant | Reject with 403, log as potential ID manipulation | TASK-XXX |

---

# 10. Risks

| # | Risk | Impact | Likelihood | Mitigation |
|---|------|--------|------------|------------|
| 1 | <description> | High/Med/Low | High/Med/Low | <solution> |
| 2 | <description> | High/Med/Low | High/Med/Low | <solution> |

---

# 11. Verification Plan

**Unit Tests:**
- `TestClassName` — validates: <what>
- `TestClassName` — validates: <what>

**Integration Tests:**
- <component interaction test> — validates: <end-to-end scenario>

**Manual / Smoke Tests:**
- Scenario A — steps: <1, 2, 3> → expected: <result>
- Scenario B — steps: <1, 2, 3> → expected: <result>

**Success Criteria:**
- All automated tests pass
- Feature matches spec behavior (ref: `<path-to-spec.md>`)
- Performance within budget (latency/memory)
- No regressions in existing test suite

---

# 12. Rollout Plan (Optional)

| Phase | Scope | Gate Criteria |
|-------|-------|---------------|
| 1 | Internal / staging | All tests pass, team review done |
| 2 | Feature flag (X% traffic) | Error rate < 0.1%, latency OK |
| 3 | Full rollout | 48h stable in Phase 2 |

**Rollback:**
- Feature flag → disable immediately
- DB migration → backward-compatible, no rollback script needed

---

# 13. Future Improvements (Optional)

- <improvement A> — rationale: <why defer to later>
- <improvement B> — rationale: <why defer to later>
