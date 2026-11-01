---
name: test-strategy
description: >
  Use when planning or writing tests for a feature, module, or bug fix.
  Trigger when user says 'write tests', 'test strategy', 'test plan', 'what should I test',
  'test cases', 'edge cases', 'test coverage', or when a workflow's verification step requires test planning.
  Analyzes code and spec to generate structured test cases covering happy paths, error paths,
  edge cases, and boundary conditions. Outputs a prioritized test plan with optional test code generation.
allowed-tools: Read, Glob, Grep, Bash
---

# Test Strategy — Structured Test Planning & Case Generation

**Role:** Senior QA Engineer and Test Architect.
**Mission:** Analyze code or spec to generate comprehensive, prioritized test cases that catch real bugs — not just prove the happy path works.

**Input:** Code files, spec, plan, or feature description.
**Output:** Structured test plan with categorized test cases, optionally followed by test code.

> **Compatibility:** This skill is **agent-agnostic** and **tech-stack agnostic**. It detects the project's test framework automatically. If none is found, outputs a Markdown table for manual implementation.

---

## Process Flow

```
Receive input (code, spec, or description)
        |
Phase 0: Context Bootstrap
        |-- Detect test framework & patterns
        +-- Read spec/plan if available
        |
Phase 1: Identify Test Subjects
        |-- What functions/endpoints/components to test?
        +-- What are the inputs, outputs, side effects?
        |
Phase 2: Generate Test Cases
        |-- Happy path
        |-- Error / unhappy paths
        |-- Edge cases (MANDATORY — 4 categories)
        +-- Integration points
        |
Phase 3: Present Test Plan
        |
        STOP — User approves test plan
        |
Phase 4: Generate Test Code (optional, on user request)
        |
Done
```

---

## Phase 0: Context Bootstrap

### 0.1 Detect test framework

Search for existing test files and test configurations:

| Ecosystem | Config Files | Test File Patterns |
|-----------|-------------|-------------------|
| JavaScript/TypeScript | `jest.config.*`, `vitest.config.*`, `.mocharc.*`, `karma.conf.*` | `*.spec.ts`, `*.test.ts`, `*.spec.js`, `*.test.js` |
| Java | `pom.xml` (junit/testng deps), `build.gradle` | `src/test/**/*Test.java`, `*Spec.java` |
| Python | `pytest.ini`, `conftest.py`, `setup.cfg [tool:pytest]`, `tox.ini` | `test_*.py`, `*_test.py` |
| Go | (built-in) | `*_test.go` |
| C#/.NET | `*.csproj` (xunit/nunit/mstest refs) | `*Tests.cs`, `*Test.cs` |
| Other | Search for common test patterns | Inspect test directory structure |

### 0.2 Analyze existing test patterns

Read 2-3 existing test files to understand:

- **Naming conventions:** describe/it, given/when/then, test_method_name, @DisplayName
- **Setup/teardown:** beforeEach, @BeforeAll, setUp(), fixture
- **Mocking approach:** jest.mock, Mockito, unittest.mock, testify/mock
- **Assertion style:** expect().toBe(), assertThat(), assert, require

### 0.3 Read spec/plan (if available)

If a spec or plan file exists in `docs/plans/`, extract:

- **Acceptance criteria** from task specifications -> direct test cases
- **Edge cases section** (Section 9 of plan template) -> mandatory coverage
- **Contracts** (Section 4) -> interface test cases with pre/post conditions

> If no test framework is detected, ask:
> *"No test framework detected. Which framework should I target? Or should I output as a Markdown table only?"*

---

## Phase 1: Identify Test Subjects

For each function, endpoint, or component in scope, extract:

| Aspect | What to Identify |
|--------|-----------------|
| **Inputs** | Parameters, request body, query params, headers, env vars, injected dependencies |
| **Outputs** | Return values, response body, status codes, thrown exceptions |
| **Side effects** | DB writes, API calls to other services, events/messages emitted, files created/modified |
| **Dependencies** | External services, databases, caches, configs — candidates for mocking |
| **State** | What state does it read? What state does it mutate? Pre-conditions required? |

Present to user:

> "I identified [N] test subjects: [list with brief description]. Does this look complete?"

**STOP** — Wait for user confirmation. User may add or remove subjects.

---

## Phase 2: Generate Test Cases

### 2.1 Happy Path (Required)

The main success scenario(s). At minimum:

- Valid input with all required fields -> expected successful output
- Complete flow from entry to exit with correct state changes
- If multiple success paths exist (e.g., different user roles, different input combinations), cover each

### 2.2 Error / Unhappy Paths (Required)

| Scenario | Expected Behavior |
|----------|-------------------|
| Invalid input (wrong type, out of range) | Validation error with clear message |
| Missing required fields | 400 / validation error listing missing fields |
| Unauthorized access | 401 / 403 with appropriate message |
| Resource not found | 404 / null handling / empty result |
| External dependency failure | Graceful degradation, retry, or clear error |
| Timeout / network error | Timeout handling, circuit breaker behavior |
| Duplicate operation | Idempotency check or conflict error |

### 2.3 Edge Cases (MANDATORY — Never Skip)

Every test plan MUST include test cases from ALL 4 categories below:

| # | Category | Examples |
|---|----------|----------|
| 1 | **Null / Undefined / Empty** | `null` input, empty string `""`, empty array `[]`, empty object `{}`, `undefined`, numeric `0`, boolean `false`, whitespace-only string `"   "` |
| 2 | **Boundary Values** | Min/max integer, empty collection vs. single item vs. many items, first/last element, exact limit values (e.g., page size = 20 -> test with 19, 20, 21), max length string |
| 3 | **Format Outliers** | Unicode characters, extra leading/trailing whitespace, special chars (`<>&"'\`), very long strings (>1000 chars), SQL/HTML injection strings in input, dates across timezones, leap year dates (Feb 29), DST transitions |
| 4 | **Concurrency & State** | Duplicate submissions (double-click), rapid sequential calls, stale data reads, race conditions on shared resources, partial failures in batch operations, concurrent modifications |

> **Rule:** If a test plan arrives at Phase 3 without at least 1 test case from each of these 4 categories, it is INCOMPLETE. Go back and add them.

### 2.4 Integration Points (If applicable)

| Type | What to Test |
|------|-------------|
| API contracts | Request/response shape matches spec, status codes correct |
| Database operations | CRUD works, constraints enforced, transactions rollback on failure |
| Events/messages | Published with correct payload, consumed and processed correctly |
| External service calls | Correct request sent, response handled, error/timeout handled |

---

## Phase 3: Present Test Plan

Format as a structured document:

```markdown
# Test Plan: <feature/module name>

## Test Framework: [detected or chosen]
## Test Subjects: [N subjects listed]

---

### Happy Path

| # | Test Case | Input | Expected Output | Priority |
|---|-----------|-------|-----------------|----------|
| 1 | [descriptive name] | [specific input] | [specific output] | High |

### Error Paths

| # | Test Case | Input | Expected Output | Priority |
|---|-----------|-------|-----------------|----------|
| 1 | [descriptive name] | [specific input] | [specific error] | High |

### Edge Cases

| # | Category | Test Case | Input | Expected Output | Priority |
|---|----------|-----------|-------|-----------------|----------|
| 1 | Null/Empty | [name] | `null` | [expected] | High |
| 2 | Boundary | [name] | [boundary value] | [expected] | Medium |
| 3 | Format | [name] | [outlier value] | [expected] | Medium |
| 4 | Concurrency | [name] | [scenario] | [expected] | Medium |

### Integration (if applicable)

| # | Test Case | Components | Expected Behavior | Priority |
|---|-----------|------------|-------------------|----------|
| 1 | [name] | [A -> B] | [expected] | Medium |

---

## Summary

| Category | Count | High | Medium | Low |
|----------|-------|------|--------|-----|
| Happy Path | [N] | [N] | [N] | [N] |
| Error Paths | [N] | [N] | [N] | [N] |
| Edge Cases | [N] | [N] | [N] | [N] |
| Integration | [N] | [N] | [N] | [N] |
| **Total** | **[N]** | **[N]** | **[N]** | **[N]** |
```

**STOP:**

> "Test plan generated with [N] test cases ([H] high, [M] medium, [L] low priority). Review and approve before proceeding."
>
> Options:
> 1. **Approve** — test plan is complete
> 2. **Add cases** — I missed scenarios (please describe)
> 3. **Remove cases** — some cases are unnecessary (please specify)
> 4. **Generate test code** — approve and write test files

---

## Phase 4: Generate Test Code (Optional)

Only proceed when user explicitly requests test code generation (option 4 above or separate request).

### Rules for test code generation:

1. **Follow existing patterns** — Use the naming, setup, mocking, and assertion styles detected in Phase 0
2. **One test file per subject** — Unless the project convention groups tests differently
3. **Group by category** — Use describe blocks, nested classes, or equivalent to group Happy/Error/Edge/Integration
4. **Clear test names** — Each test name should describe the scenario without reading the test body
5. **Independent tests** — No test should depend on another test's execution or side effects
6. **Proper mocking** — Mock external dependencies, NOT the unit under test
7. **Clear assertion messages** — Every assertion should have a descriptive failure message
8. **Place correctly** — Follow the project's test directory structure and naming convention

### If no test framework was detected:

Output the test plan as a Markdown table only. Do NOT generate code without a confirmed framework.

---

## Anti-Patterns

| Anti-Pattern | Why It's Wrong |
|--------------|----------------|
| Testing only happy path | Most bugs live in edge cases and error paths — happy path tests provide false confidence |
| Testing implementation details | Tests break on every refactoring; test observable behavior and contracts instead |
| No assertion messages | When tests fail, developers cannot diagnose the issue without re-reading test code |
| Mocking everything | Over-mocking hides real integration bugs and makes tests pass when production fails |
| Copy-pasting test setup | Creates brittle, hard-to-maintain tests; use shared fixtures or factories |
| Ignoring async/concurrency | Race conditions are the hardest bugs to find and reproduce — test them explicitly |
| Giant test methods | Each test should verify ONE behavior; split multi-assertion tests into separate cases |
| Skipping edge case categories | "It probably won't happen" is exactly how production incidents start |
