---
name: spec-reviewer
description: "Review a spec document for completeness, cross-impact risks, flow integrity, and instruction compliance before implementation planning. Trigger when the user asks to review a spec, validate a design, or check readiness for coding."
allowed-tools: Read, Shell, Glob, Grep, Bash
---

# Spec Reviewer — Pre-Implementation Gate

**Role:** Senior Spec Reviewer and System Architect.
**Mission:** Ensure a spec is bulletproof — complete, consistent, safe to implement, and compliant with project conventions — before any code is written.

**Input:** Spec file path (provided by user or from a prior brainstorming/design step).
**Output:** Spec Sentinel Report with actionable verdict.

> **Compatibility:** This skill is **agent-agnostic** and **tech-stack agnostic**. The review methodology works with any AI coding agent (GitHub Copilot, Claude Code, Cursor, Windsurf, Antigravity, etc.) and any language/framework. It uses only standard capabilities: read files, search text, and run shell commands.

---

## Process Flow

```
Receive spec file path
        ↓
Phase 0: Session context available?
        ├── YES → reuse (skip reconnaissance)
        └── NO  → run targeted codebase scan
        ↓
Phase 1: Spec Quality Audit
        ↓
Phase 2: Flow & Dependency Tracing (grep actual codebase)
        ↓
Phase 3: Instruction & Convention Compliance
        ↓
Phase 4: Generate Spec Sentinel Report
        ↓
Decision Gate:
   ├── ❌ Issues Found   → STOP, present report, wait for revision
   ├── ⚠️ Warnings Only  → Present report, ask user to confirm proceed
   └── ✅ Approved        → TERMINAL STATE (see Decision Rules)
```

---

## Phase 0: Context Bootstrap (Conditional)

> **Rule:** Never re-read files already in conversation context. If a prior brainstorming/design step has discovered tech stack, project structure, and conventions — reuse that knowledge. Only scout for **gaps**.

**Skip when:** A brainstorming or design step ran before this in the same session, or user provided project context.
**Run when:** Invoked standalone, new session, or targeting a different repo.

When skipping, log: `"Phase 0 skipped — reusing context from current session."`

### Minimal Discovery (when context is missing)

| Aspect | How |
|--------|-----|
| Tech Stack | File extensions, manifest files (`package.json`, `pom.xml`, `go.mod`, `*.csproj`, etc.) |
| Architecture | Folder structure, entry points, config files |
| Conventions | Read 2-3 files similar to what the spec targets |
| Agent Instructions | Search for any of these (varies by tool): |

**Agent instruction files to locate (check all that exist):**

| Agent / Tool | Instruction Files |
|-------------|-------------------|
| GitHub Copilot | `.instructions.md`, `.copilot-instructions.md`, `.github/copilot-instructions.md`, `AGENTS.md` |
| Claude Code | `CLAUDE.md`, `.claude/settings.json` |
| Cursor | `.cursorrules`, `.cursor/rules/*.md` |
| Antigravity | `.antigravity` |

---

## Phase 1: Spec Quality Audit

Read the spec file thoroughly and evaluate against this checklist:

| Category | What to Look For | Severity |
|----------|------------------|----------|
| **Completeness** | TODO, TBD, placeholders, "will spec later", empty sections | ❌ Blocker |
| **Clarity** | Ambiguous requirements that could lead to multiple valid but conflicting implementations | ❌ Blocker |
| **Consistency** | Internal contradictions between sections, conflicting constraints | ❌ Blocker |
| **Coverage** | Missing error handling, edge cases, boundary conditions, unhappy paths | ⚠️ Warning |
| **Scope** | Spec covers multiple independent subsystems that should be separate specs | ⚠️ Warning |
| **YAGNI** | Features not requested, over-engineered abstractions, premature optimization | ⚠️ Warning |
| **Boundaries** | Units without clear input/output contracts, blurred responsibilities | ⚠️ Warning |

---

## Phase 2: Cross-Impact & Dependency Tracing

> **This is the most critical phase.** Do not rely on assumptions — grep the actual codebase to verify impact.

### 2.1 Shared Logic Scan

For every function, module, component, or service the spec proposes to **modify or extend**:

1. `Grep` for all call sites and imports of that symbol across the codebase.
2. List every consumer file and feature that depends on it.
3. Flag if any consumer expects behavior that the spec would change.

> **New symbols** (added by the spec, no existing call sites) do not need consumer scanning — but verify they don't shadow or conflict with existing names.

### 2.2 Data & State Integrity

- Does the spec modify DB schemas, migrations, or shared state stores?
- Does it change data shapes passed between layers (DTOs, models, API payloads)?
- Could it cause data inconsistency in existing records or concurrent flows?

### 2.3 API & Contract Analysis

- Does it add, remove, or change fields in existing API endpoints?
- Does it alter authentication, authorization, or middleware behavior?
- Are there downstream consumers (other services, mobile apps, third-party integrations) that would break?

### 2.4 Flow Integrity Check

Trace the end-to-end flow(s) affected by this spec:

1. Identify the **entry point** (user action, API call, event trigger).
2. Walk through each step in the flow: validation → processing → persistence → response.
3. At each step, verify: does the spec account for what happens when this step fails?
4. Flag any step where the spec's changes could interrupt an existing flow path.

### 2.5 Cross-Cutting Concerns

| Concern | What to Check |
|---------|---------------|
| **Security** | Does it introduce new inputs without validation? New endpoints without auth? |
| **Performance** | Does it add N+1 queries, unbounded loops, or heavy operations in hot paths? |
| **Observability** | Does it affect logging, monitoring, or alerting for existing flows? |
| **Configuration** | Does it require new env vars, feature flags, or config changes? |

### 2.6 Multi-Tenant & Authorization Audit

> **Activation:** If the project has tenant/org/company ID in its data models, auth context, or database schema — this section is mandatory.

For each data-accessing operation in the spec:

1. **Tenant-scoped queries** — Does every data read/write include tenant filtering? Flag if any query could return cross-tenant data.
2. **IDOR vulnerabilities** — Are there endpoints where User A could access User B's resources by guessing/manipulating IDs? Check: order IDs, document IDs, transaction IDs, etc.
3. **Ownership validation** — Does the spec validate that the requesting user/tenant actually owns the resource before performing read/update/delete?
4. **Batch operations** — If the spec involves bulk operations, does it validate ALL items in the batch belong to the same tenant?
5. **Shared vs. tenant-scoped resources** — Are shared resources (lookup tables, configs) clearly distinguished from tenant-scoped data?

| Severity | Condition |
|----------|-----------|
| ❌ Blocker | Data access without tenant filtering, or endpoint without ownership validation |
| ⚠️ Warning | Implicit tenant filtering (relying on middleware only, no defense-in-depth) |
| ✅ Pass | Explicit tenant validation at both service and repository layers |

---

## Phase 3: Instruction & Convention Compliance

> Validate the spec does **not** violate existing project rules.

1. **Locate project instructions** — Use instruction files found in Phase 0. If Phase 0 was skipped, search for agent instruction files (`.instructions.md`, `CLAUDE.md`, `.cursorrules`, `AGENTS.md`, `CONTRIBUTING.md`, or equivalent for the current tool).
2. **Check naming conventions** — Does the spec follow the project's established naming patterns?
3. **Check architecture rules** — Does the spec respect layer boundaries, module ownership, and dependency direction?
4. **Check file/folder conventions** — Are proposed new files placed in the correct directories with correct naming?
5. **Check test requirements** — Does the spec include or account for tests matching the project's testing strategy?

If no instruction files exist, skip this phase and note: `"No project instruction files found — compliance check skipped."`

---

## Phase 4: Spec Sentinel Report

Generate the report using this exact structure:

```markdown
# Spec Sentinel Report

**Spec:** `[path/to/spec-file]`
**Date:** [YYYY-MM-DD]
**Status:** ✅ Approved | ⚠️ Warnings (Proceed with Caution) | ❌ Issues Found (Blocked)

---

## Findings

> Every finding **must** include a recommendation. Do not report a problem without proposing how to fix it.

### Critical Issues (Blockers)
<!-- Only if status is ❌. Omit section entirely if none. -->

| # | Section / Topic | Issue | Why It Blocks | Recommendation |
|---|----------------|-------|---------------|----------------|
| 1 | [section] | [specific issue] | [why it blocks progress] | [how to fix] |

### Warnings
<!-- Only if status is ⚠️ or ❌. Omit section entirely if none. -->

| # | Category | Finding | Risk | Recommendation |
|---|----------|---------|------|----------------|
| 1 | [category] | [what was found] | [potential impact] | [suggested action] |

## Cross-Impact Analysis

| Dimension | Finding | Risk Level | Recommendation |
|-----------|---------|------------|----------------|
| **Shared Logic** | [symbols affected] → [N consumer(s): list them] | 🔴 High / 🟡 Medium / 🟢 Low | [action to mitigate] |
| **Data/State** | [schemas or state affected] | 🔴 / 🟡 / 🟢 | [action] |
| **API Contracts** | [endpoints changed] → [downstream impact] | 🔴 / 🟡 / 🟢 | [action] |
| **Flow Integrity** | [flows traced] → [break points, if any] | 🔴 / 🟡 / 🟢 | [action] |
| **Cross-Cutting** | [security / performance / config concerns] | 🔴 / 🟡 / 🟢 | [action] |

## Instruction Compliance

| Dimension | Verdict | Deviation (if any) | Recommendation |
|-----------|---------|-------------------|----------------|
| **Conventions** | ✅ / ⚠️ | [specific deviation or "—"] | [action or "—"] |
| **Architecture** | ✅ / ⚠️ | [specific violation or "—"] | [action or "—"] |
| **Testing** | ✅ / ⚠️ | [missing requirements or "—"] | [action or "—"] |
```

---

## Decision Rules

| Report Status | Action |
|---------------|--------|
| ❌ Issues Found | **STOP.** Present report. Do NOT proceed to planning. Wait for user to revise the spec. |
| ⚠️ Warnings Only | Present report. Ask: *"Warnings detected. Proceed to planning or revise first?"* |
| ✅ Approved | Present report. Proceed to **On Approval** below. |

### On Approval (✅ or ⚠️ confirmed)

This skill does **not** hardcode which planning tool to use. After approval, ask the user:

> *"Spec approved. Choose next step:"*
> 1. **Use `writing-plans` skill** (bundled with this kit)
> 2. **Use your own planning skill** (specify skill name)
> 3. **Skip planning** — go straight to implementation

If the user does not respond or has a default preference configured, fall back to `writing-plans` or the agent's built-in planning capability.