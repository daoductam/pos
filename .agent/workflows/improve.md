---
description: >
  Structured improvement workflow for existing code. Guides through 4 phases:
  Scoping -> Analysis -> Implementation -> Verification.
  Use when code works but can be better: performance, robustness, refactoring,
  concurrency, edge cases, observability, or test coverage.
  Trigger when user says 'improve', 'optimize', 'refactor', 'clean up code',
  'fix edge cases', 'add error handling', 'code is slow', 'make it better'.
---

# Code Improvement Workflow

Orchestrate structured improvement of existing, working code through 4 mandatory phases, each producing a concrete artifact. This workflow does not implement anything itself — it invokes existing skills and manages transitions between phases.

> **Fundamental principle:** Measure before you change. Analyze before you implement. Compare before/after. No regression.

---

## Process Flow Overview

```
Step 0: IMPROVEMENT SIZING
|  Assess: files affected, scope, risk, measurability
|  Output: Size verdict (S / M / L) + user confirmation
|  Route:
|    S (Quick Fix)     -> FAST-TRACK (inline scope -> implement -> quick verify)
|    M (Targeted)      -> STANDARD (4-phase, analysis depth proportional)
|    L (Deep Overhaul) -> FULL CEREMONY (4-phase with all gates)
|
Step 1: SCOPING
|  Identify: target files/module + improvement type(s) + success criteria
|  Reference domain skills based on improvement type
|  Output: docs/improvements/<topic>/YYYY-MM-DD-scope.md
|  STOP — user approve scope and success criteria
|
Step 2: ANALYSIS
|  |-- 2a: Invoke code-reviewer -> surface ALL issues (analysis mode)
|  |-- 2b: Type-specific deep analysis (N+1, test gaps, security, log coverage)
|  +-- 2c: Synthesize findings into Improvement Backlog (sorted by impact/effort)
|  Output: docs/improvements/<topic>/YYYY-MM-DD-analysis.md
|  STOP — user approve/select/prioritize backlog items
|
Step 3: IMPLEMENTATION
|  Work backlog in priority order (P1 -> P2 -> P3)
|  |-- Per task: reference domain skills as needed
|  |-- Per task: STOP for user review
|  +-- Per logical unit: invoke action-commit
|  Output: docs/improvements/<topic>/YYYY-MM-DD-walkthrough.md
|  STOP — user confirm all improvements complete
|
Step 4: VERIFICATION
|  |-- 4a: Re-run code-reviewer on changed files -> compare with Phase 2 findings
|  |-- 4b: Before/After comparison against Phase 1 success criteria
|  |-- 4c: (Conditional) Invoke test-strategy — always if Robustness/Edge/Test types
|  |-- 4d: (Conditional) Invoke code-secure-fixer — only if scanner report available
|  |-- 4e: Regression check — existing tests still passing?
|  +-- 4f: (Optional) Invoke sequence-diagram if flow changed significantly
|  Output: docs/improvements/<topic>/YYYY-MM-DD-<topic>_result.md
```

---

## Artifact Registry

Every phase produces a concrete file in `docs/improvements/<topic>/`. Each artifact includes timing metadata at the top:

```markdown
Started at:  YYYY/MM/DD HH:MM:SS
Finished at: YYYY/MM/DD HH:MM:SS
Total time: X minutes
---
```

| Phase | Artifact | Description |
|-------|----------|-------------|
| Scoping | `YYYY-MM-DD-scope.md` | Target files, improvement types, success criteria baseline |
| Analysis | `YYYY-MM-DD-analysis.md` | Code review findings + prioritized improvement backlog |
| Implementation | `YYYY-MM-DD-walkthrough.md` | Execution log per improvement task, decisions, deviations |
| Verification | `YYYY-MM-DD-<topic>_result.md` | Before/after comparison, verdict, coverage status |

---

## Artifact Naming Convention

### Folder Naming Rules

Before creating `docs/improvements/<topic>/`:

1. **Scan existing folders** — Search `docs/improvements/` for folders related to the same module or area
2. **Reuse if found** — If a related folder exists (same module, different improvement), place new artifacts there (append date-prefixed files)
3. **Create canonical name if new** — Use the module/area name in noun-based kebab-case

### Naming Pattern

- **Folder:** `<module-or-area-name>/` (noun-based, kebab-case)
- **Files:** `YYYY-MM-DD-scope.md`, `YYYY-MM-DD-analysis.md`, etc.
- **Multiple improvements same area:** `YYYY-MM-DD-scope.md`, `YYYY-MM-DD-v2-scope.md`

### Examples

| Scenario | Correct | Incorrect |
|----------|---------|-----------|
| Improve user service performance | `user-service/2026-04-02-scope.md` | `optimize-user-queries/2026-04-02-scope.md` |
| Another improvement to same service | `user-service/2026-04-10-scope.md` (same folder) | `refactor-user-validation/2026-04-10-scope.md` (new folder) |
| Improve payment module robustness | `payment-module/2026-04-02-scope.md` | `add-error-handling-payment/2026-04-02-scope.md` |

> **Rule:** One module/area = one folder. Action-based names (optimize-, refactor-, improve-, add-) are forbidden as folder names. If unsure about the canonical name, ask the user.

---

## Skill Reference Map

At ANY phase, if the current context relates to a domain skill, reference it. Do not wait until Implementation to use domain knowledge.

| Skill | When to Reference |
|-------|------------------|
| `code-reviewer` | Phase 2 (analysis — surface all issues) + Phase 4a (re-review changed files) |
| `test-strategy` | Phase 2 (test gap analysis for Robustness/Edge/Test types) + Phase 4c (coverage verification) |
| `api-design` | Scoping API-related goals; implementation of API contract fixes |
| `database-design` | N+1 detection, index strategy, query optimization in Phases 2–3 |
| `log-processing` | Observability scoping (Phase 1), log coverage analysis (Phase 2), log implementation (Phase 3) |
| `action-commit` | After each approved improvement task in Phase 3 |
| `code-secure-fixer` | Phase 4d — only when user provides a security scanner report |
| `sequence-diagram` | Phase 4f — optional, when control/data flow changed significantly |

**Examples of cross-phase skill usage:**

- **Scoping:** User wants to fix slow queries -> reference `database-design` to ask the right N+1 / indexing questions before setting success criteria.
- **Scoping:** User wants to improve logging -> reference `log-processing` to discuss log format choices and coverage expectations.
- **Analysis:** Performance type detected -> reference `performance` rule's scale questions to size the problem before benchmarking.
- **Implementation:** Fixing an N+1 query -> reference `database-design` for batch fetch / JOIN patterns.
- **Implementation:** Adding structured logging -> reference `log-processing` for format selection and log level rules.

---

## Step 0: Improvement Sizing

Before entering the 4-phase workflow, assess the improvement's scope to determine the appropriate process depth. This prevents wasting tokens and time on full ceremony for a single-function fix.

### Assessment Criteria

| Signal | Quick Fix (S) | Targeted (M) | Deep Overhaul (L) |
|--------|--------------|--------------|-------------------|
| **Files affected** | 1-2 files | 3-5 files | 6+ files |
| **Scope** | Single function/method | Multiple methods, 1-2 services | Cross-service, architectural |
| **Risk** | Low — isolated change | Medium — touches shared logic | High — core logic or data |
| **Measurability** | Easy to verify (obvious before/after) | Needs before/after metrics | Complex benchmarking required |
| **Improvement types** | Single type (e.g., just refactoring) | 2-3 types combined | Multiple types, systemic issues |

### Sizing Prompt

Present to user:

> "Let me assess the improvement scope to determine the right process depth."
>
> | Criterion | Assessment |
> |-----------|------------|
> | Files affected | [estimate] |
> | Scope | [single function/multi-method/cross-service] |
> | Risk | [low/medium/high] |
> | Measurability | [easy/metrics needed/complex] |
> | Improvement types | [list] |
>
> **Verdict: [S / M / L]**
>
> Recommended flow:
> - **S -> Fast-track** (inline scope -> implement -> quick verify)
> - **M -> Standard** (4-phase, analysis depth proportional)
> - **L -> Full ceremony** (4-phase with all gates)
>
> "Agree with this sizing, or override?"

### Fast-Track Flow (Quick Fix — Size S)

For single-function or small-scope improvements, skip artifact file generation:

```
1. INLINE SCOPE — Confirm target, improvement type, success criterion in conversation
   (no scope.md — context captured in conversation)
2. INLINE ANALYSIS — Quick review of the target code, identify the improvement
   (no analysis.md — findings discussed inline)
3. IMPLEMENT — Apply the improvement with user approval
4. QUICK VERIFY — Confirm improvement achieved + no regression
   (no walkthrough.md or result.md — commit messages serve as record)
```

**Gate rules still apply** — user must approve the improvement approach even in fast-track.

**Override:** User can always say "I want full process" to escalate from S to M/L flow.

### Standard Flow (Targeted — Size M)

Run all 4 phases with these simplifications:
- Phase 2 analysis depth is proportional — focus on the identified types, not exhaustive audit
- All gates still apply
- Artifact files are generated as normal

### Full Ceremony (Deep Overhaul — Size L)

Run all 4 phases exactly as described below. No shortcuts.

---

## Step 1: Scoping

**Goal:** Understand *what* is being improved, *why*, and define *what success looks like* — before any code is read or changed.

### 1a — Identify Target and Improvement Type(s)

Ask the user for:

1. **Target** — Which files, module, service, or function should be improved?
2. **Goal** — What is wrong with it right now? What triggered this improvement request?

Classify the improvement type(s) from this list (multiple allowed):

| Type | Examples of triggers |
|------|---------------------|
| **Performance** | "slow queries", "high latency", "too many DB calls", "memory spike" |
| **Robustness** | "crashes on bad input", "unhandled exceptions", "missing null checks" |
| **Refactoring** | "god class", "duplicated logic", "poor naming", "function too long" |
| **Concurrency** | "race condition", "shared mutable state", "deadlock risk" |
| **Edge Cases** | "breaks on empty input", "fails at boundary values", "unexpected data" |
| **Observability** | "no logging", "can't debug in production", "missing metrics/traces" |
| **Test Coverage** | "no tests", "low coverage", "critical paths untested" |

### 1b — Reference Domain Skills Based on Type

Based on the classified type(s), bring in relevant domain skills **during the scoping conversation**:

- **Performance goal** -> reference `performance` rule: ask "Roughly how many records/MB are we processing? What is the current measured latency/throughput?"
- **Database-related** -> reference `database-design`: ask about query patterns, current index coverage, ORM usage
- **API-related** -> reference `api-design`: discuss contract stability, error response format, pagination impact
- **Observability goal** -> reference `log-processing`: discuss target log format, log level strategy, what events need coverage

### 1c — Define Success Criteria

**Success criteria MUST be defined before any code is touched.**

Work with the user to establish measurable criteria. Tailor the question to the improvement type:

| Type | Success Criteria Examples |
|------|--------------------------|
| Performance | "p95 latency < 200ms", "query count per request reduced by > 50%", "memory usage < 512MB under load" |
| Robustness | "zero uncaught exceptions on all known error paths", "graceful degradation when upstream is down" |
| Refactoring | "cyclomatic complexity < 10 per function", "no function > 50 lines", "zero duplicated blocks > 5 lines" |
| Concurrency | "no shared mutable state without synchronization", "all race conditions in audit eliminated" |
| Edge Cases | "all boundary values return defined behavior", "empty/null inputs handled without exception" |
| Observability | "all error paths emit a structured log entry", "request duration logged at INFO level" |
| Test Coverage | "line coverage > 80% on target module", "all happy path + error path scenarios have test cases" |

> If the user cannot define success criteria, ask targeted questions: "If this improvement is successful, how would you know? What would you measure or observe?"

**Output:** `docs/improvements/<topic>/YYYY-MM-DD-scope.md`

**Timing:** Log start time when entering Step 1. When scope is approved and file is written, log finish time and calculate total duration.

```markdown
# Improvement Scope: <topic>

## Target
- **Files / Module:** [list of files or module path]
- **Description:** [brief description of what this code does]

## Improvement Types
- [ ] Performance
- [ ] Robustness
- [ ] Refactoring
- [ ] Concurrency
- [ ] Edge Cases
- [ ] Observability
- [ ] Test Coverage

## Motivation
[What triggered this improvement request? What is currently wrong?]

## Success Criteria
| # | Criterion | How to Measure |
|---|-----------|---------------|
| 1 | [criterion] | [measurement method] |
| 2 | [criterion] | [measurement method] |

## Out of Scope
[Anything explicitly NOT being changed in this session]

## Domain Skills Referenced
[Which skills were consulted during scoping and key decisions made]
```

**Gate:**

> "Scope document saved. Improvement type(s): [list]. Success criteria: [summary]."
> - Proceed to Analysis?
> - Need to adjust scope or success criteria?

---

## Step 2: Analysis

**Goal:** Understand the current state of the code in full. Surface ALL issues — not just the one the user mentioned.

### Step 2a — Code Review (Analysis Mode)

**Invoke:** `code-reviewer` skill on the target files from Step 1.

**IMPORTANT — Analysis mode instruction:**

> Instruction to pass: "This is analysis mode, not approval mode. Surface ALL issues found across all severity levels — critical, warnings, and suggestions. Do not filter for brevity. The goal is a complete picture of what can be improved."

The code reviewer will examine: Security, Business Logic, Performance, Clean Code, Conventions.

### Step 2b — Type-Specific Deep Analysis

Based on the improvement type(s) identified in Step 1, perform additional targeted analysis:

| Type | Additional Analysis |
|------|-------------------|
| **Performance** | Reference `performance` rule: identify N+1 queries, unbounded loops, missing pagination, synchronous I/O that could be async. Reference `database-design` for missing indexes, full-table scans. |
| **Robustness / Edge Cases** | Invoke `test-strategy` Phase 0+1 (test subject discovery + happy/error/edge case identification) to map coverage gaps. |
| **Concurrency** | Identify shared mutable state, missing locks, race-prone patterns (check-then-act, read-modify-write without atomicity). |
| **Observability** | Reference `log-processing`: audit which error paths, state transitions, and key operations are missing log entries. |
| **Security** | Reference `security` rule: check for injection vectors, exposed sensitive data, missing auth checks, insecure defaults. |
| **Test Coverage** | Invoke `test-strategy` Phase 0+1 to enumerate all test subjects and existing test gaps. |

### Step 2c — Synthesize Improvement Backlog

Combine all findings (from code review + type-specific analysis) into a prioritized **Improvement Backlog**:

**Priority scoring:**
- **P1** — High impact, Low effort (quick wins that meaningfully advance success criteria)
- **P2** — High impact, High effort (important but costly)
- **P3** — Low impact, Low effort (nice-to-have cleanups)
- **P4** — Low impact, High effort (defer or skip)

**Improvement Backlog format:**

| # | Category | Issue | File:Line | Impact | Effort | Priority |
|---|---------|-------|----------|--------|--------|----------|
| 1 | Performance | N+1 query in `UserService.findAll()` | `user.service.ts:42` | High | Low | P1 |
| 2 | Robustness | Unhandled rejection in `fetchOrders()` | `order.service.ts:88` | High | Low | P1 |
| 3 | Refactoring | `processPayment()` is 120 lines, 3 responsibilities | `payment.service.ts:15` | Medium | High | P2 |
| 4 | Edge Cases | No validation on empty `userId` input | `user.controller.ts:31` | Medium | Low | P1 |

**Output:** `docs/improvements/<topic>/YYYY-MM-DD-analysis.md`

**Timing:** Log start time when entering Step 2. When analysis is approved and file is written, log finish time and calculate total duration.

```markdown
# Improvement Analysis: <topic>

## Source
- **Scope:** [link to scope.md]
- **Files Analyzed:** [list]
- **Analysis Date:** [date]

## Code Review Findings Summary

- **Critical issues:** [count]
- **Warnings:** [count]
- **Suggestions:** [count]
- **Full report:** [inline or reference to code-reviewer output]

## Type-Specific Findings

### [Type 1, e.g., Performance]
[Findings from type-specific analysis]

### [Type 2, e.g., Robustness]
[Findings from type-specific analysis]

## Improvement Backlog

| # | Category | Issue | File:Line | Impact | Effort | Priority |
|---|---------|-------|----------|--------|--------|----------|
| 1 | [category] | [issue] | [file:line] | High/Med/Low | High/Med/Low | P1/P2/P3 |

## Items Deferred (P4)
[Items identified but not recommended for this session, with reason]
```

**Gate:**

> "Analysis complete. Found [N] improvement opportunities across [M] categories."
> "Improvement Backlog:"
> [display backlog table]
>
> - Approve all items and proceed to Implementation?
> - Approve selected items only? (specify which)
> - Adjust priorities before proceeding?

---

## Step 3: Implementation

**Goal:** Execute the approved backlog in priority order, one task at a time, with user review after each.

### Preparation

1. Read the approved backlog from `analysis.md`
2. Present execution order to user:

> "Implementing [N] approved improvements in priority order:"
>
> | Order | # | Category | Issue | File:Line | Priority |
> |-------|---|---------|-------|----------|----------|
> | 1 | [#] | [category] | [issue] | [location] | P1 |
> | 2 | [#] | [category] | [issue] | [location] | P1 |
> | ... | ... | ... | ... | ... | ... |
>
> "Starting with item 1. Proceed?"

### Per-Task Execution

For each backlog item in priority order:

1. **Announce:** "Implementing item [N]/[Total]: [issue description] in [file:line]"

2. **Reference domain skills** when the task involves:
   - Query optimization or schema changes -> `database-design` (batch fetch, JOIN patterns, migration format)
   - API contract changes -> `api-design` (error formats, backward compatibility)
   - Logging additions -> `log-processing` (format, level selection, what to log)
   - Performance hotspots -> `performance` rule (streaming, chunking, async patterns)

3. **Implement** the fix:
   - Target only the scope of this backlog item — do not expand to unrelated changes
   - Follow existing project conventions (naming, error handling style, import patterns)
   - Verify the fix addresses the success criterion it maps to

4. **Self-check** before presenting:
   - Does this fix the identified issue without introducing new ones?
   - Does it match project conventions?
   - Are error paths handled?

5. **Gate:**

   > "Item [N]/[Total] complete: [issue description]"
   > - Files modified: [list]
   > - Maps to success criterion: [which criterion from scope.md]
   > - Any deviations from original plan: [yes/no — explain if yes]
   > - Review and approve before continuing?

6. **Commit:** After user approves -> invoke `action-commit` skill

### Generate Walkthrough Document

After ALL approved items are complete, generate `walkthrough.md`:

```markdown
# Improvement Walkthrough: <topic>

## Execution Summary

- **Scope:** [link to scope.md]
- **Analysis:** [link to analysis.md]
- **Items approved:** [N]
- **Items implemented:** [N]
- **Items skipped:** [N] (if any — list with reason)

## Implementation Log

### Item 1: <issue_description>

- **Category:** [type]
- **Location:** [file:line]
- **Status:** Completed
- **Files modified:** [list with paths]
- **Domain skills referenced:** [list]
- **Approach:** [brief description of the fix]
- **Maps to success criterion:** [which criterion]
- **Commit:** [hash + message]

### Item 2: <issue_description>

[repeat for each item]

## Deviations from Backlog

| # | Item | Planned Approach | Actual Approach | Reason |
|---|------|-----------------|-----------------|--------|
| 1 | [item] | [what analysis suggested] | [what was done] | [why] |

> If no deviations: "Implementation followed the approved backlog exactly."

## Known Limitations

- [anything not fully resolved, trade-offs accepted, follow-up items identified]
- [or "None identified" if all items fully resolved]
```

**Output:** `docs/improvements/<topic>/YYYY-MM-DD-walkthrough.md`

**Timing:** Log start time when entering Step 3. When walkthrough is generated, log finish time and calculate total duration.

**Gate:**

> "All [N] improvements implemented. Walkthrough document saved. Ready for Verification?"

---

## Step 4: Verification

**Goal:** Confirm the improvements actually achieved the success criteria from Step 1. No regression.

### Step 4a — Code Review (Comparison Mode)

**Invoke:** `code-reviewer` skill on all files modified during Step 3 (extract from `walkthrough.md`).

> Instruction to pass: "This is a post-improvement review. Focus on: (1) are the issues from the Phase 2 analysis resolved? (2) were any new issues introduced by the fixes? Compare findings against the original backlog."

Compare results with Phase 2 analysis:

| Issue (from Phase 2) | Phase 2 Finding | Phase 4 Finding | Status |
|----------------------|-----------------|-----------------|--------|
| N+1 query in UserService | Critical | Not found | ✅ Resolved |
| Missing null check | Warning | Not found | ✅ Resolved |
| Long function (120 lines) | Warning | Still 80 lines | ⚠️ Partially resolved |

**Gate:**

| Verdict | Action |
|---------|--------|
| New critical issues introduced | Return to Step 3 — fix regressions. Re-run review after fix. Update walkthrough.md. |
| All original issues resolved, warnings only | Ask: *"Warnings found. Address now or accept and proceed?"* |
| All original issues resolved, clean | Continue to Step 4b. |

### Step 4b — Success Criteria Comparison

Compare current state against the success criteria defined in Step 1.

Generate a before/after table:

| # | Success Criterion | Before | After | Status |
|---|-------------------|--------|-------|--------|
| 1 | "query count < 5 per request" | 23 queries | 3 queries | ✅ Met |
| 2 | "p95 latency < 200ms" | 850ms | 140ms | ✅ Met |
| 3 | "no function > 50 lines" | 3 violations | 1 violation | ⚠️ Partial |

> For criteria that cannot be measured automatically, ask the user: "Can you provide the measured value for [criterion] after the changes?"

**Gate:**

> "Success criteria comparison complete."
> - All criteria met: proceed to final steps.
> - Partial criteria met: ask "Accept current state, or return to Step 3 to address remaining items?"
> - Criteria not met: return to Step 3 with specific guidance on what still needs work.

### Step 4c — Test Strategy (Conditional)

**Always invoke** `test-strategy` skill if improvement type(s) include:
- Robustness
- Edge Cases
- Test Coverage

**Offer** for all other types:
> "Would you like to verify or add test coverage for the changed code?"

**Input:** scope.md (success criteria) + analysis.md (identified gaps) + walkthrough.md (modified files).

The test strategist will:
1. Map changed code to test subjects
2. Identify which gaps from Phase 2 analysis were addressed
3. Generate test cases covering: happy path, error paths, edge cases, regression cases
4. Present test plan for approval

**Gate:**

> "Test plan generated with [N] test cases for changed code."
> 1. Approve test plan
> 2. Approve and generate test code
> 3. Adjust test cases

### Step 4d — Security Check (Conditional)

**When to invoke:**
- User provides a security scanner report (semgrep, gitleaks, etc.) -> invoke `code-secure-fixer` skill
- No report available -> the always-on `security` rule has already been enforced during commits in Step 3

> "Do you have a security scanner report to analyze for the changed files? If not, the security checks applied during commits in Step 3 are sufficient."

### Step 4e — Regression Check

Ask:

> "Were any existing tests run against the changed code? Did they pass?"

- If tests exist in the project, offer to list relevant test files from the walkthrough.
- If tests were not run, remind: "It is strongly recommended to run existing tests before marking this improvement as complete."
- If a regression is found: return to Step 3 to fix, then re-run Step 4a and Step 4e.

### Step 4f — Sequence Diagram (Optional)

**When to offer:**
- The improvement involved significant flow changes (async refactoring, new retry logic, restructured call chains)
- The user wants to document the as-improved architecture

> "The changes altered the control flow in [module]. Would you like to generate an updated sequence diagram to document the improved flow?"

### Generate Improvement Result Document

After all verification steps, generate `<topic>_result.md`:

```markdown
# Improvement Result: <topic>

## Verdict: IMPROVED / PARTIALLY IMPROVED / NEEDS MORE WORK

## Artifacts

| Phase | Artifact | Path | Status |
|-------|----------|------|--------|
| Scoping | Scope | `docs/improvements/<topic>/YYYY-MM-DD-scope.md` | Approved |
| Analysis | Analysis | `docs/improvements/<topic>/YYYY-MM-DD-analysis.md` | Approved |
| Implementation | Walkthrough | `docs/improvements/<topic>/YYYY-MM-DD-walkthrough.md` | Complete |
| Verification | Result | (this file) | [verdict] |

## Success Criteria Results

| # | Criterion | Before | After | Status |
|---|-----------|--------|-------|--------|
| 1 | [criterion] | [before] | [after] | ✅/⚠️/❌ |

## Code Review Summary

- **Phase 2 issues found:** [N] (critical: [N], warnings: [N])
- **Phase 4 issues resolved:** [N]
- **New issues introduced:** [N] (should be 0)
- **Remaining issues:** [N] (accepted or deferred — explain)

## Improvement Backlog Resolution

| # | Category | Issue | Status |
|---|---------|-------|--------|
| 1 | [category] | [issue] | ✅ Resolved / ⚠️ Partial / ❌ Deferred |

## Test Coverage

- **Test strategy invoked:** Yes / No
- **Test cases planned:** [N]
- **Test cases implemented:** [N] (if code was generated)
- **Regression check:** Passed / Not run / Failures found (details below)

## Security Check

- **Status:** Passed / Warnings / Issues Found / Skipped (no report provided)
- **Details:** [summary if applicable]

## Final Checklist

- [ ] All P1 backlog items resolved
- [ ] Success criteria met (or deviation accepted and documented)
- [ ] Code review: no new critical issues introduced
- [ ] Regression check: existing tests still passing
- [ ] Commits clean with standardized messages
- [ ] Walkthrough.md documents all decisions and deviations
```

**Output:** `docs/improvements/<topic>/YYYY-MM-DD-<topic>_result.md`

**Timing:** Log start time when entering Step 4. When result document is generated, log finish time and calculate total duration.

**Gate:**

> "Verification complete. Result document saved at [path]."
> "Final verdict: [IMPROVED / PARTIALLY IMPROVED / NEEDS MORE WORK]."

---

## Error Recovery

| Situation | Action |
|-----------|--------|
| Scope too vague (user cannot define success criteria) | Ask targeted questions based on improvement type — see Step 1c examples |
| Code-reviewer finds no significant issues in Phase 2 | Present findings honestly, ask: "No significant issues found. Continue with a deeper type-specific analysis, or narrow the scope?" |
| Implementation makes things worse (verified by metrics in Phase 4b) | Revert to previous commit, re-analyze with different approach, document in walkthrough.md |
| Test regression found after improvement | Return to Step 3, fix regression, re-run Step 4a + Step 4e |
| User wants to add more improvement items mid-Phase 3 | Allowed — add to backlog, re-prioritize, continue in order |
| Success criteria cannot be measured automatically | Ask user to provide measured values; document measurement method in result.md |
| Context getting long | Suggest saving progress and starting a new session — scope.md + analysis.md contain everything needed to resume |

---

## Key Principles

1. **Measure before you change** — Success criteria MUST be defined in Step 1 before any code is read in detail or touched.
2. **Analyze before you implement** — Always run `code-reviewer` in Phase 2 to surface the full picture, not just the obvious fix the user mentioned.
3. **Prioritize by impact/effort** — The improvement backlog must be sorted; P1 first. Never implement P3 before P1.
4. **Compare before/after** — Phase 4 must explicitly compare against Phase 1 success criteria. "It feels better" is not a verdict.
5. **No regression = non-negotiable** — Verification must include a regression check. A fix that breaks existing behavior is not an improvement.
6. **Artifacts preserve state** — If the session ends, `scope.md` + `analysis.md` contain everything needed to resume without starting over.
7. **Orchestrate, don't expand scope** — Implement only what is in the approved backlog. New issues found during implementation go into a future backlog, not the current session.
