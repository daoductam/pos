---
description: >
  Structured feature development workflow. Guides the user through 4 phases:
  Brainstorming -> Planning -> Implementation -> Verification.
  Each phase produces a concrete artifact and requires user approval before proceeding.
  At Implementation, asks user to choose TDD or Standard approach, then dispatches
  tasks in parallel groups via sub-agents (when supported) or sequential-parallel groups.
  Use when user says 'new feature', 'build a feature', 'develop feature', 'feature workflow',
  or wants structured end-to-end feature development.
---

# Feature Development Workflow

Orchestrate end-to-end feature development through 4 mandatory phases, each producing a concrete artifact. This workflow does not implement anything itself — it invokes existing skills and manages transitions between phases.

---

## Process Flow Overview

```
Step 0: FEATURE SIZING
|  Assess: files affected, methods/services, new vs modify, contracts
|  Output: Size verdict (S / M / L) + user confirmation
|  Route:
|    S (Small)  → FAST-TRACK (inline brainstorm → implement → quick verify)
|    M (Medium) → STANDARD (4-phase, spec-reviewer optional)
|    L (Large)  → FULL CEREMONY (4-phase with all gates)
|
Step 1: BRAINSTORMING
|  Invoke: brainstorming skill (terminal state overridden)
|  Output: docs/plans/<topic>/YYYY-MM-DD-spec.md
|  STOP — user approve spec
|
Step 2: PLANNING
|  |-- 2a: Invoke spec-reviewer skill -> Spec Sentinel Report
|  |       STOP — must be Approved
|  |-- 2b: Invoke writing-plans skill -> plan.md (12-section template)
|  |       Must annotate Task Graph with: depends_on, parallel_group,
|  |       sub_agent_scope, domain_skills
|  |       STOP — user approve plan
|  +-- 2c: (Optional) Invoke sequence-diagram skill
|  Output: docs/plans/<topic>/YYYY-MM-DD-plan.md
|
Step 3: IMPLEMENTATION
|  Ask: TDD or Standard approach?
|  Present: parallel group execution plan
|  |-- 3.0: Ask user TDD vs Standard
|  |-- 3.1: Dispatch tasks by parallel group (sub-agent if supported)
|  |        Concurrent: tasks within same group with no dependencies
|  |        Sequential: tasks across dependency groups
|  |-- Per task: Reference domain skills, implement (or TDD cycle)
|  |-- Per group: Gate for user review
|  +-- Per group: Commit via action-commit
|  Output: docs/plans/<topic>/YYYY-MM-DD-walkthrough.md
|
Step 4: VERIFICATION
|  |-- 4a: Invoke code-reviewer skill -> RED/YELLOW/GREEN
|  |       STOP — must be GREEN
|  |-- 4b: Invoke test-strategy skill -> test plan
|  |       (If TDD: review existing tests for coverage gaps)
|  |       STOP — user confirm test plan
|  |-- 4c: (Conditional) Invoke code-secure-fixer
|  |-- 4d: (Conditional) Invoke debug-fe
|  +-- 4e: (Conditional) Invoke sequence-diagram (as-built)
|  Output: docs/plans/<topic>/YYYY-MM-DD-<feature>_result.md
```

---

## Artifact Registry

Every phase produces a concrete file in `docs/plans/<topic>/`. Each artifact includes timing metadata at the top:

```markdown
Started at:  YYYY/MM/DD HH:MM:SS
Finished at: YYYY/MM/DD HH:MM:SS
Total time: X minutes
---
```

| Phase | Artifact | Description |
|-------|----------|-------------|
| Brainstorming | `YYYY-MM-DD-spec.md` | Design spec with requirements, approaches, decisions |
| Planning | `YYYY-MM-DD-plan.md` | 12-section implementation plan with task graph |
| Implementation | `YYYY-MM-DD-walkthrough.md` | Execution log: what was built, decisions, deviations |
| Verification | `YYYY-MM-DD-<feature>_result.md` | Final report: review verdict, test coverage, status |

---

## Skill Reference Map

At ANY phase, if the current context relates to a domain skill, reference it. Do not wait until Implementation to use domain knowledge.

| Skill | When to Reference (at any phase) |
|-------|----------------------------------|
| `api-design` | Discussing API endpoints, choosing REST vs GraphQL vs tRPC, designing error formats, pagination, auth patterns |
| `database-design` | Discussing data models, choosing ID strategies (Snowflake, UUID v4/v7, ULID), schema design, partitioning, indexing |
| `angular-form-generator` | Discussing form UIs, form validation, reactive form patterns |
| `log-processing` | Discussing logging strategy, log formats, observability |
| `sequence-diagram` | Visualizing call flows — offered at Planning (Step 2c) and Verification (Step 4e) |
| `action-commit` | Creating commits at any point with standardized Git Flow messages |
| `code-secure-fixer` | Security analysis when scanner reports are available |
| `debug-fe` | UI debugging when frontend issues are found |
| `code-reviewer` | Structured code review in Verification (Step 4a) |
| `test-strategy` | Test planning and case generation in Verification (Step 4b) |
| `spec-reviewer` | Spec quality validation in Planning (Step 2a) |
| `writing-plans` | Plan generation in Planning (Step 2b) |

**Examples of cross-phase skill usage:**

- **Brainstorming:** User is exploring API approaches -> reference `api-design` decision tree (REST vs GraphQL) to help evaluate options with concrete trade-offs.
- **Brainstorming:** User is deciding data model -> reference `database-design` for ID strategy comparison table and partitioning thresholds.
- **Brainstorming:** User is building a UI feature -> scan existing pages/components first, then reference `angular-form-generator` for form patterns.
- **Planning:** Writing Contracts section in plan -> reference `api-design` for standard error format, pagination pattern, auth patterns.
- **Planning:** Writing Target Architecture section -> reference `database-design` for schema conventions, index strategy guidance.
- **Implementation:** Building UI components -> read related existing pages to match layout patterns BEFORE writing new code.

---

## Step 0: Feature Sizing

Before entering the 4-phase workflow, assess the feature's size to determine the appropriate process depth. This prevents wasting tokens and time on full ceremony for small changes.

### Assessment Criteria

| Signal | Small (S) | Medium (M) | Large (L) |
|--------|-----------|------------|-----------|
| **Files affected** | 1-2 files | 3-5 files | 6+ files |
| **Methods/services** | Single method change | Multiple methods, 1-2 services | Cross-service, new APIs |
| **New contracts** | None | 1-2 new interfaces/DTOs | New APIs, schemas, contracts |
| **Scope** | Bug fix, UI tweak, config change | New endpoint, new component | New module, cross-cutting feature |
| **Risk** | Low — isolated change | Medium — touches shared logic | High — architectural impact |

### Sizing Prompt

Present to user:

> "Let me assess the feature size to determine the right process depth."
>
> | Criterion | Assessment |
> |-----------|------------|
> | Files affected | [estimate] |
> | Methods/services | [estimate] |
> | New contracts | [yes/no] |
> | Risk level | [low/medium/high] |
>
> **Verdict: [S / M / L]**
>
> Recommended flow:
> - **S → Fast-track** (inline brainstorm → implement → quick verify)
> - **M → Standard** (4-phase, spec-reviewer rút gọn)
> - **L → Full ceremony** (4-phase đầy đủ)
>
> "Agree with this sizing, or override?"

### Fast-Track Flow (Size S)

For small features, skip artifact file generation and run inline:

```
1. INLINE BRAINSTORM — Ask 2-3 clarifying questions in conversation
   (no spec.md file — decisions captured in conversation context)
2. INLINE PLAN — Brief implementation plan in conversation
   (no plan.md file — just outline the approach)
3. IMPLEMENT — Execute directly with per-task review gates
4. QUICK VERIFY — Self-review checklist + suggest test cases inline
   (no walkthrough.md or result.md — commit messages serve as record)
```

**Gate rules still apply** — user must approve before implementation even in fast-track.

**Override:** User can always say "I want full process" to escalate from S to M/L flow.

### Standard Flow (Size M)

Run all 4 phases with these simplifications:
- Step 2a (spec-reviewer) is **optional** — ask user: "Run spec review or proceed to planning?"
- Artifact files are generated as normal
- All gates still apply

### Full Ceremony (Size L)

Run all 4 phases exactly as described below. No shortcuts.

---

## Artifact Naming Convention

### Folder Naming Rules

Before creating `docs/plans/<topic>/`:

1. **Scan existing folders** — Search `docs/plans/` for folders related to the same feature or module
2. **Reuse if found** — If a related folder exists, place new artifacts there (append date-prefixed files)
3. **Create canonical name if new** — Use the module/feature name in noun-based kebab-case

### Naming Pattern

- **Folder:** `<module-or-feature-name>/` (noun-based, kebab-case)
- **Files:** `YYYY-MM-DD-spec.md`, `YYYY-MM-DD-plan.md`, etc.
- **Versioned specs:** `YYYY-MM-DD-v2-spec.md` for revisions within the same feature

### Examples

| Scenario | Correct | Incorrect |
|----------|---------|-----------|
| New feature A | `function-a/2026-03-25-spec.md` | `initiate-function-a/2026-03-25-spec.md` |
| Enhancement to A | `function-a/2026-04-01-spec.md` (same folder) | `create-button-for-function-a/2026-04-01-spec.md` (new folder) |
| Bug fix for A | `function-a/2026-04-02-spec.md` | `fix-function-a-bug/2026-04-02-spec.md` |

> **Rule:** One feature = one folder. Action-based names (initiate-, create-, fix-) are forbidden as folder names. If unsure about the canonical name, ask the user.

---

## Step 1: Brainstorming

**Invoke:** `brainstorming` skill.

**IMPORTANT — Terminal state override:**

When invoking `brainstorming` within this workflow, its normal terminal state (auto-invoke `writing-plans`) is **overridden**. After the spec is written and approved, control returns to this workflow so that `spec-reviewer` runs BEFORE `writing-plans`.

> Instruction to pass: "After the spec is approved and written to file, STOP. Do NOT invoke `writing-plans`. Return control to the feature workflow for the Planning phase."

**During brainstorming — reference domain skills when relevant:**

- Discussing API design? -> Reference `api-design` for decision trees, conventions, patterns
- Discussing data model? -> Reference `database-design` for ID strategies, schema patterns, partitioning rules
- Discussing UI forms? -> Reference `angular-form-generator` for form patterns, validation approach
- Discussing logging? -> Reference `log-processing` for format options, log level strategy

Let the domain skill knowledge inform the brainstorming conversation, but do NOT invoke implementation — only use their reference material to help the user make better design decisions.

**Output:** `docs/plans/<topic>/YYYY-MM-DD-spec.md`

**Timing:** Log start time when entering Phase 1. When spec is approved and file is written, log finish time and calculate total duration.

**Gate:**

> "Spec has been written and committed. Before we move to Planning, please review."
> - Ready to proceed to Planning?
> - Need to revise the spec?

---

## Step 2: Planning

### Step 2a — Spec Review (Quality Gate)

**Invoke:** `spec-reviewer` skill with the spec file path from Step 1.

**Terminal state override:** When `spec-reviewer` reaches its Approved state, it normally asks the user to choose a planning tool. Within this workflow, **skip that question** — proceed directly to Step 2b with `writing-plans`.

> Instruction to pass: "After generating the Spec Sentinel Report, if approved, do NOT ask the user to choose a planning tool. Return control to the feature workflow."

**Gate:**

| Verdict | Action |
|---------|--------|
| Issues Found | Return to Step 1 to revise the spec. Present specific issues to fix. |
| Warnings Only | Ask: *"Warnings detected. Proceed to planning or revise spec first?"* |
| Approved | Continue to Step 2b. |

### Step 2b — Implementation Plan

**Invoke:** `writing-plans` skill with the approved spec.

**During plan writing — reference domain skills for key sections:**

- **Section 4 (Contracts):** Reference `api-design` for API contract patterns, error response formats, pagination conventions.
- **Section 5 (Target Architecture):** Reference `database-design` for schema design, indexing strategy. Reference `api-design` for endpoint structure.
- **Section 7 (Task Graph):** Consider which tasks need domain skill expertise (mark them for Step 3).

**Task Graph annotations for parallel execution:**

The plan's Task Graph (Section 7) and Task Specifications (Section 8) MUST annotate each task with:

| Field | Description |
|-------|-------------|
| `depends_on` | List of task IDs this task depends on (empty `[]` = can start immediately) |
| `parallel_group` | Tasks in the same group with no inter-dependencies can run concurrently |
| `sub_agent_scope` | Files/modules the task touches — used to scope sub-agent context window |
| `domain_skills` | Which domain skills the sub-agent should reference during this task |

Example Task Graph output:

```
Task 1: Create database migration    | depends_on: []   | group: A | scope: internal/state/, internal/content/    | skills: database-design
Task 2: Add API endpoint             | depends_on: [1]  | group: B | scope: internal/cli/                      | skills: api-design
Task 3: Write unit tests for state    | depends_on: []   | group: A | scope: internal/state/                    | skills: test-strategy
Task 4: Write unit tests for content  | depends_on: []   | group: A | scope: internal/content/                  | skills: test-strategy
Task 5: Integration test              | depends_on: [1,2]| group: C | scope: test/integration/                  | skills: test-strategy
```

Tasks 1, 3, 4 are in group A with no inter-dependencies → dispatched in parallel.
Task 2 depends on Task 1 → must wait.
Task 5 depends on Tasks 1 and 2 → must wait.

**Output:** `docs/plans/<topic>/YYYY-MM-DD-plan.md`

**Timing:** Log start time when entering Phase 2. When plan is approved and file is written, log finish time and calculate total duration.

**Gate:**

> "Plan has been written with [N] tasks across [M] phases. Please review before implementation begins."
> - Approve plan and proceed?
> - Need to adjust the plan?

### Step 2c — Sequence Diagram (Optional)

After the plan is approved, ask:

> "Would you like to generate a sequence diagram for the main flow before implementation?"
> 1. **Yes** — invoke `sequence-diagram` skill, save alongside plan
> 2. **No** — skip
> 3. **Later** — mark for Step 4e, ask again during Verification

---

## Step 3: Implementation

### Step 3.0 — Implementation Strategy

Before starting implementation, ask the user one question:

> "Which implementation approach would you like to use?"
> 1. **TDD** — Write tests first, then implement to make them pass (Red → Green → Refactor). Each task: write failing test → implement minimum code → refactor.
> 2. **Standard** — Implement first, then add tests during Verification (Step 4).

After the user chooses, present the execution plan with parallel groups:

> "Implementation approach: **[TDD / Standard]**"
>
> "Tasks grouped for parallel execution:"
>
> | Group | Tasks (can run in parallel) | Dependencies |
> |-------|-----------------------------|-------------|
> | A | [Task 1: name], [Task 3: name], [Task 4: name] | None |
> | B | [Task 2: name] | Task 1 |
> | C | [Task 5: name] | Task 1, Task 2 |
>
> "Within each group, tasks with no inter-dependencies will be dispatched concurrently. Groups execute in dependency order. Proceed?"

Record the user's implementation approach for use throughout Step 3.

### Sub-Agent Dispatch Protocol

The workflow dispatches tasks in parallel by default. The dispatch strategy adapts to the platform's capabilities:

#### Mode 1: Sub-Agent Dispatch (Claude Code, OpenCode)

When the platform supports sub-agents (Task tool), dispatch independent tasks within the same parallel group simultaneously:

```
For parallel group A (Tasks 1, 3, 4):
  ├── Sub-agent 1: Task 1 — "Create database migration"
  │     context: [sub_agent_scope files + relevant plan sections]
  │     skills: database-design
  │
  ├── Sub-agent 2: Task 3 — "Write unit tests for state"
  │     context: [sub_agent_scope files + relevant plan sections]
  │     skills: test-strategy
  │
  └── Sub-agent 3: Task 4 — "Write unit tests for content"
        context: [sub_agent_scope files + relevant plan sections]
        skills: test-strategy
```

**Sub-agent prompt template:**

> "Implement Task [N]: [name].
>
> Context: [sub_agent_scope files and relevant plan sections]
> Domain skills to reference: [list]
> Acceptance criteria: [from Section 8]
> Contracts to follow: [from Section 4]
>
> [If TDD mode:] Follow Red → Green → Refactor cycle:
> 1. RED: Write failing test(s) for this task's acceptance criteria
> 2. GREEN: Implement minimum code to make test(s) pass, referencing Contracts (Section 4) and acceptance criteria (Section 8)
> 3. REFACTOR: Clean up while keeping tests green
>
> [If Standard mode:] Implement according to plan's Task Specification (Section 8), following Contracts (Section 4). Handle edge cases from Section 9.
>
> When complete, report: files created/modified, acceptance criteria status, any deviations from plan."

**After all sub-agents in a group complete:**
1. Collect results from all sub-agents
2. Verify no conflicts in shared files
3. Present consolidated results to user as a group gate
4. Proceed to the next parallel group

#### Mode 2: Sequential Parallel (Antigravity, Codex, etc.)

For platforms that do not support sub-agent dispatch, optimize execution by grouping independent tasks and processing them sequentially within each group, minimizing context switches (group by scope/module). This preserves the parallel execution mental model while running tasks one at a time:

1. For each parallel group, present ALL tasks in the group together
2. Execute tasks one by one within the group, ordered by scope proximity (minimize module-switching)
3. After completing all tasks in a group, present consolidated results
4. Gate: "Group [X] complete. Review before next group?"

### TDD Implementation Cycle

When the user chose TDD mode, each task follows the Red-Green-Refactor cycle:

**For each task (or per sub-agent when dispatched):**

1. **RED Phase:**
   - Write test(s) covering the task's acceptance criteria
   - Run tests → confirm they fail
   - Announce: "Task [N] RED phase — [M] tests written, all failing as expected."

2. **GREEN Phase:**
   - Implement minimum code to make tests pass
   - Reference domain skills as needed (api-design, database-design, etc.)
   - Run tests → confirm they pass

3. **REFACTOR Phase:**
   - Clean up code while keeping tests green
   - Self-check against acceptance criteria

**Note:** In Sub-Agent Dispatch mode (Mode 1), each sub-agent performs the full TDD cycle within its task scope. The orchestrator collects results and runs integration tests after each parallel group completes.

### Per-Task Execution Flow

For each parallel group, in dependency order:

1. **Dispatch** — Announce group and dispatch applicable tasks (sub-agent or sequential)

2. **Reference domain skills** when a task involves:
   - API implementation -> `api-design` (conventions, error handling, status codes)
   - Database schema/queries -> `database-design` (schema patterns, migration format, N+1 prevention)
   - Form components -> `angular-form-generator` (reactive forms, validation patterns)
   - Logging -> `log-processing` (format selection, log level rules, what to log/not log)
   - **UI components** -> Read existing related pages/components FIRST to match layout, spacing,
     navigation patterns, and component composition. Then reference `angular-form-generator`
     for form-specific patterns. Follow the application's design-pattern conventions —
     do NOT invent new layout structures when existing patterns already exist.

3. **Implement** the task according to its specification in plan.md:
   - Follow Contracts (Section 4) exactly
   - Check acceptance criteria (Section 8) during implementation
   - Handle edge cases assigned to this task (Section 9)
   - **TDD only:** Follow Red → Green → Refactor cycle per task

4. **Self-check** against the task's acceptance criteria before presenting to user

5. **Group gate** (after all tasks in a parallel group complete):

   > "Group [X] complete: [N] tasks."
   > - Files created/modified: [consolidated list]
   > - Acceptance criteria: [per-task status]
   > - Review before proceeding to Group [X+1]?

6. **Commit:** After user approves each group, ask explicitly:

   > "Commit changes for Group [X] (Tasks [list])?"
   > - Yes → invoke `action-commit` skill
   > - No → continue without committing

### Generate Walkthrough Document

After ALL tasks are complete, generate `walkthrough.md`:

```markdown
# Implementation Walkthrough: <feature_name>

## Execution Summary

- **Spec:** [link to spec.md]
- **Plan:** [link to plan.md]
- **Branch:** [branch name]
- **Implementation approach:** [TDD / Standard]
- **Execution mode:** [Sub-Agent Dispatch / Sequential Parallel]
- **Tasks completed:** [N/N]

## Task Execution Log

### Task 1: <task_name>

- **Status:** Completed
- **Parallel group:** [A / B / C]
- **Sub-agent:** [Yes — dispatched / No — sequential]
- **Files created/modified:** [list with paths]
- **Domain skills referenced:** [list]
- **Key decisions:** [decisions made during implementation, especially deviations from plan]
- [**TDD only:** Tests written: [N], all passing]
- **Commit:** [hash + message]

### Task 2: <task_name>

[repeat for each task]

## Parallel Execution Summary

| Group | Tasks | Mode | Duration |
|-------|-------|------|----------|
| A | [task names] | [concurrent / sequential] | [time] |
| B | [task names] | [concurrent / sequential] | [time] |

## Deviations from Plan

| # | Task | Planned | Actual | Reason |
|---|------|---------|--------|--------|
| 1 | [task] | [what plan said] | [what was done] | [why] |

> If no deviations: "Implementation followed the plan exactly."

## Known Limitations

- [anything not perfect, tech debt introduced, improvements deferred]
- [or "None identified" if clean implementation]
```

**Output:** `docs/plans/<topic>/YYYY-MM-DD-walkthrough.md`

**Timing:** Log start time when entering Phase 3. When walkthrough is generated, log finish time and calculate total duration.

**Gate:**

> "All [N] tasks implemented. Walkthrough document saved. Ready for Verification?"

---

## Step 4: Verification

### Step 4a — Code Review

**Invoke:** `code-reviewer` skill.

**Input:** All files created/modified during Step 3 (extract from walkthrough.md's Task Execution Log).

The code reviewer will:
1. Scope the review to changed files + 1-hop dependencies
2. Check against: Security, Business Logic, Performance, Clean Code, Conventions
3. Produce a verdict report

**Gate:**

| Verdict | Action |
|---------|--------|
| RED (Critical issues) | Return to Step 3 — fix critical issues. After fix, re-run code review on affected files. Update walkthrough.md. |
| YELLOW (Warnings) | Ask: *"Warnings found. Fix now or proceed to testing?"* |
| GREEN (Approved) | Continue to Step 4b. |

### Step 4b — Test Strategy

**Invoke:** `test-strategy` skill.

**Input:** Spec (requirements + edge cases) + plan (contracts + acceptance criteria) + implemented code.

**TDD adjustment:** If TDD mode was used in Step 3:
- Tests already exist for each task from the Red → Green → Refactor cycles
- The test-strategy skill should review existing tests for coverage gaps, add missing edge cases and integration tests, and generate supplementary test cases for areas not fully covered

**Standard mode:** Generate full test plan from scratch.

The test strategist will:
1. Detect test framework and existing patterns
2. Identify test subjects from the implementation
3. Generate test cases: happy path, error paths, edge cases (mandatory 4 categories), integration points
4. Present test plan for approval
5. Optionally generate test code

**Gate:**

> "Test plan generated with [N] test cases. Review and approve."
> 1. Approve test plan
> 2. Approve and generate test code
> 3. Add/remove test cases

### Step 4c — Security Check (Conditional)

**When to invoke:**
- User provides a security scanner report -> invoke `code-secure-fixer` skill
- No report available -> the always-on `security` rule has already been enforced during commits in Step 3

> "Do you have a security scanner report (semgrep, gitleaks) to analyze? If not, security checks from the commit phase are sufficient."

### Step 4d — UI Debugging (Conditional)

**When to invoke:**
- The feature includes frontend UI AND issues are found during verification -> invoke `debug-fe` skill
- The feature is backend-only -> skip

> Only offer if applicable: "This feature includes UI components. Would you like to run a Playwright debug session to verify the UI?"

### Step 4e — Sequence Diagram (Conditional)

**When to invoke:**
- User chose "Later" in Step 2c
- OR user wants an as-built diagram to compare with the design-time diagram
- OR feature is complex enough to benefit from documentation

> "Would you like to generate a sequence diagram from the actual implementation? This captures the as-built flow for documentation."

### Generate Feature Result Document

After all verification steps, generate `<feature>_result.md`:

```markdown
# Feature Result: <feature_name>

## Status: APPROVED / APPROVED WITH WARNINGS / NEEDS WORK

## Artifacts

| Phase | Artifact | Path | Status |
|-------|----------|------|--------|
| Brainstorming | Spec | `docs/plans/<topic>/YYYY-MM-DD-spec.md` | Approved |
| Planning | Plan | `docs/plans/<topic>/YYYY-MM-DD-plan.md` | Approved |
| Implementation | Walkthrough | `docs/plans/<topic>/YYYY-MM-DD-walkthrough.md` | Complete |
| Verification | Result | (this file) | [status] |

## Code Review Summary

- **Verdict:** GREEN / YELLOW / RED
- **Critical issues:** [count] (all resolved)
- **Warnings:** [count] ([resolved/accepted])
- **Suggestions:** [count]

## Test Coverage

- **Test cases planned:** [N]
- **Test cases implemented:** [N] (if code was generated)
- **Breakdown:**
  - Happy path: [N] cases
  - Error paths: [N] cases
  - Edge cases: [N] cases
  - Integration: [N] cases

## Security Check

- **Status:** Passed / Warnings / Issues Found / Skipped (no report provided)
- **Details:** [summary if applicable]

## Sequence Diagram

- **Status:** Generated / Skipped
- **Path:** [link if generated]

## Final Checklist

- [ ] Code review: GREEN
- [ ] Test cases planned and approved
- [ ] Security checks passed (or acknowledged)
- [ ] Documentation: walkthrough.md written
- [ ] Commits clean with standardized messages
- [ ] Ready for PR / merge
```

**Output:** `docs/plans/<topic>/YYYY-MM-DD-<feature>_result.md`

**Timing:** Log start time when entering Phase 4. When result document is generated, log finish time and calculate total duration.

**Gate:**

> "Feature development complete. Result document saved at [path]."
> "Final status: [APPROVED / APPROVED WITH WARNINGS / NEEDS WORK]."

---

## Error Recovery

| Situation | Action |
|-----------|--------|
| Spec rejected by spec-reviewer (Step 2a) | Return to Step 1, revise spec based on Spec Sentinel Report findings |
| Plan rejected by user (Step 2b) | Revise plan, re-run writing-plans with user's feedback |
| TDD tests fail to turn GREEN (Step 3) | Review plan's acceptance criteria, adjust implementation or revise tests |
| Sub-agent conflict in shared files (Step 3) | Manual merge, then re-run code review on conflicting files |
| Code review RED (Step 4a) | Return to Step 3, fix critical issues, update walkthrough.md, re-run code review |
| Test failures found (Step 4b) | Return to Step 3, fix code, re-verify |
| Security issues found (Step 4c) | Fix via code-secure-fixer, then re-run code review on affected files |
| User wants to restart a phase | Allowed — re-invoke the corresponding skill for that phase |
| Context getting long | Suggest saving progress and starting a new session — artifact files preserve all state |

---

## Key Principles

1. **Orchestrate, don't implement** — This workflow manages transitions and invokes skills. The skills do the actual work.
2. **Every phase has an artifact** — spec.md -> plan.md -> walkthrough.md -> result.md. Progress is never lost.
3. **Every transition has a gate** — Never auto-proceed between phases. User must approve.
4. **Skills are available everywhere** — Domain skills can be referenced at any phase when context is relevant.
5. **Recovery is always possible** — User can go back to any previous phase to fix issues.
6. **Artifacts are the source of truth** — If context is lost (long session, new session), the artifact files contain everything needed to resume.
7. **Parallel by default** — Independent tasks are dispatched concurrently via sub-agents when supported, or grouped for sequential-parallel execution otherwise.
8. **TDD is opt-in** — Ask the user at Step 3.0 whether to use TDD or Standard approach. Adapt the implementation cycle accordingly.
