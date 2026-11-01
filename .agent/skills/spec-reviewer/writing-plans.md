# Writing Plans — Implementation Architect

**Purpose:** Create a detailed, step-by-step implementation plan (Atomic Steps) from an approved spec.
**Input:** Approved spec file (passed review with ✅ status from `spec-reviewer`).
**Output:** Plan file written to `docs/plans/[topic]/[YYYY-MM-DD]-plan.md`.

> This skill is **tech-stack agnostic**. It adapts to any project by leveraging codebase context — either reused from the current session or freshly discovered. Never assume a language, framework, or toolchain.

---

## Process Flow

```
Receive approved spec
        ↓
Phase 0: Context available in session?
        ├── YES → reuse existing context
        └── NO  → run Codebase Reconnaissance
        ↓
File Scouting — locate exact files & lines affected by the spec
        ↓
Step Breakdown — decompose into atomic, independently verifiable tasks
        ↓
Verification Planning — attach test/check using the project's actual tooling
        ↓
Write plan to docs/plans/[topic]/[YYYY-MM-DD]-plan.md
        ↓
TERMINAL STATE — "Plan is ready. Start execution from Step 1?"
```

---

## Phase 0: Codebase Reconnaissance (CONDITIONAL)

> **Rule:** Never re-read files already in conversation context. If `spec-reviewer` or a prior step has discovered tech stack, test commands, and conventions — reuse that knowledge. Only scout for **gaps** (e.g., test tooling not yet identified but needed for verify steps).

**Skip** when: `spec-reviewer` ran before this in the same session, or user already provided project context.
**Run** when: invoked standalone, new session, or targeting a different repo.

When skipping, log: `"Phase 0 skipped — reusing context from current session."`

### Discovery Checklist (when context is missing)

| Aspect | How to Discover |
|--------|----------------|
| **Language & Runtime** | File extensions, `package.json`, `pom.xml`, `build.gradle`, `Cargo.toml`, `go.mod`, `requirements.txt`, `*.csproj`, etc. |
| **Framework & Architecture** | Entry points, config files, folder structure patterns |
| **Test Framework** | Existing test files, test scripts in build config |
| **Build & Run Commands** | `scripts` in package.json, `Makefile`, `Dockerfile`, CI config |
| **Code Conventions** | Read 2-3 existing files similar to what the spec targets |
| **Existing Plans/Docs** | `docs/plans/`, `docs/specs/`, `CHANGELOG`, `README` |

Summarize findings (or reused context) in the plan's **Project Context** header.

---

## Planning Rules

### Atomic Steps
- Each step solves exactly **one** problem — data model and UI change are separate steps.
- Each step must be independently executable — if step N fails, prior steps remain valid.
- Each step must have a clear **Definition of Done** — a verifiable condition, not a vague description.
- If a step touches 3+ files, split it.

### Test-Driven Verification
- Every step modifying shared logic, business rules, or data flow MUST be followed by a verify step.
- Verify steps MUST use the project's actual tooling — never invent generic commands.
- If no test tooling exists, use **manual check** or **build validation**.
- Format: `[Step N] → [Verify N] → [Step N+1]`

### Safety & Rollback
- Schema/migration changes must include rollback instructions.
- Shared module changes must list all consumers needing regression testing.
- Backup/branch steps come **BEFORE** destructive changes, never after.

### Ordering Strategy
- **Foundation first:** Shared modules, models, types, interfaces → before consumers.
- **Inside-out:** Core logic → integration layer → presentation layer.
- **Risk-first:** High-risk changes early (easier to abort), low-risk polish last.
- **Dependency-aware:** If Step B depends on Step A's output, A comes first.

---

## Plan File Template

```markdown
# Implementation Plan: [Topic]

**Spec:** `[path to approved spec]`
**Date:** [YYYY-MM-DD]
**Estimated Steps:** [N]

## Project Context
> Discovered from codebase or reused from session — never hardcoded.

- **Stack:** [language, framework, runtime version]
- **Test Tooling:** [test framework, test command]
- **Build Command:** [actual build command]
- **Relevant Patterns:** [conventions observed in existing code]

## Affected Files
| File | Action | Reason |
|------|--------|--------|
| `[actual path]` | Modify | [What changes and why] |
| `[actual path]` | Create | [Purpose] |

## Execution Steps

### Step 1: [Concise action title]
- **File(s):** `[actual file path]`
- **Action:** [What to change — reference specific functions, classes, or lines]
- **Definition of Done:** [Verifiable condition]

### Verify 1: [What to check]
- **Command:** `[actual test/build command]`
- **Expected:** [Expected outcome]

### Step 2: [Concise action title]
...

## Rollback Plan
- [How to safely undo each critical step]
```

---

## Anti-Patterns

| Anti-Pattern | Correct Approach |
|---|---|
| Vague steps like "implement the feature" | Break into specific file-level atomic actions |
| No verification between steps | Add verify step after every logic change |
| Using generic commands without checking | Discover actual test/build commands from project config |
| Assuming file paths or naming conventions | Scout the codebase — follow existing patterns exactly |
| Modifying shared code without listing consumers | Enumerate all consumers and add regression checks |
| Mixing different concerns in one step | Separate: data model → logic → integration → presentation |
| Skipping Phase 0 in a fresh session | No prior context = must scout. No exceptions. |

---

**TERMINAL STATE:** After the plan file is written, stop and ask the user:
> "Plan is ready and saved to `docs/plans/[topic]/[date]-plan.md`. Would you like me to begin execution from Step 1?"

Do NOT begin execution without explicit user approval.