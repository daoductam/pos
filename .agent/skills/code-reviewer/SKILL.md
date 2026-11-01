---
name: code-reviewer
description: >
  Use when code has been written and needs quality review before merging or delivery.
  Trigger when user says 'review code', 'review this', 'check my code', 'code review',
  'review the implementation', or when a workflow's verification step requires code quality assessment.
  Performs structured review against security, logic, performance, and convention checklists.
  Outputs a RED/YELLOW/GREEN verdict report.
allowed-tools: Read, Glob, Grep, Bash
---

# Code Reviewer — Structured Code Quality Gate

**Role:** Senior Code Reviewer.
**Mission:** Review implemented code against a structured checklist, identify issues by severity, and produce an actionable verdict report.

**Input:** File paths, git diff, or branch name to review.
**Output:** Code Review Report with RED / YELLOW / GREEN verdict.

> **Compatibility:** This skill is **agent-agnostic** and **tech-stack agnostic**. It uses only standard capabilities: read files, search text, and run shell commands.

---

## Process Flow

```
Receive code to review (files, diff, or branch)
        |
Phase 0: Context Bootstrap
        |-- Scan project conventions, tech stack
        +-- Read spec/plan if available
        |
Phase 1: Scope Identification
        |-- What files changed?
        |-- What is the intent? (feature, fix, refactor)
        +-- Boundary: review ONLY changed code + direct dependencies
        |
Phase 2: Review Against Checklist
        |-- Security
        |-- Business Logic & Edge Cases
        |-- Performance
        |-- Clean Code & Maintainability
        +-- Convention Compliance
        |
Phase 3: Generate Code Review Report
        |
Decision Gate:
   |-- RED (Critical)    -> STOP, must fix before proceeding
   |-- YELLOW (Warnings) -> Present, ask user to confirm proceed or fix
   +-- GREEN (Approved)  -> Proceed
```

---

## Phase 0: Context Bootstrap (Conditional)

> **Rule:** Skip if context already exists from a prior step in the same session. Never re-read files already in conversation context.

**Skip when:** A prior brainstorming, planning, or implementation step ran before this in the same session.
**Run when:** Invoked standalone, new session, or targeting a different repo.

When skipping, log: `"Phase 0 skipped -- reusing context from current session."`

### When context is missing:

| Aspect | How |
|--------|-----|
| Tech Stack | Read manifest files (`package.json`, `pom.xml`, `go.mod`, `*.csproj`, etc.) |
| Conventions | Read 2-3 existing files similar to the code under review |
| Spec/Plan | Search `docs/plans/` for related spec or plan — the review will verify implementation matches design intent |
| Agent Instructions | Check for `.instructions.md`, `CLAUDE.md`, `.cursorrules`, `AGENTS.md`, `.antigravity` |

---

## Phase 1: Scope Identification

Determine what to review:

1. **From git diff:** `git diff <base>..HEAD --name-only` to list changed files
2. **From user:** User provides specific file paths or directories
3. **Direct dependencies:** For each changed file, identify its immediate callers/callees (1 hop only — do NOT review the entire codebase)

Present scope to user:

> "I will review [N] files: [list]. Does this scope look right?"

**STOP** — Wait for user confirmation before proceeding.

---

## Phase 2: Review Against Checklist

For each file in scope, evaluate against these categories:

### 2.1 Security

| Check | What to Look For | Severity |
|-------|------------------|----------|
| Hardcoded secrets | API keys, passwords, tokens, connection strings in source | CRITICAL |
| SQL injection | String concatenation in queries instead of parameterized | CRITICAL |
| XSS | Unsanitized user input rendered in HTML/templates | CRITICAL |
| Command injection | User input passed to shell/exec without sanitization | CRITICAL |
| Auth/Authz | Missing permission checks on sensitive operations | CRITICAL |
| Data exposure | Sensitive fields (password hash, tokens) in API responses | WARNING |
| CORS/SSL | Overly permissive CORS, disabled SSL verification | WARNING |

### 2.2 Business Logic & Edge Cases

| Check | What to Look For | Severity |
|-------|------------------|----------|
| Spec compliance | Implementation matches spec/plan requirements | CRITICAL |
| Null/empty handling | Missing null checks on external data (API responses, DB results, user input) | CRITICAL |
| Boundary conditions | Off-by-one errors, empty collections, zero/negative values | WARNING |
| Error paths | All if/else/switch branches covered, including default/fallback | WARNING |
| Concurrency | Race conditions, shared mutable state without synchronization | WARNING |
| Date/timezone | Timezone-naive date operations, locale-dependent formatting | WARNING |

### 2.3 Performance

| Check | What to Look For | Severity |
|-------|------------------|----------|
| N+1 queries | Loop with DB/API call inside — should batch or JOIN | CRITICAL |
| Unbounded operations | No pagination, loading entire table, unbounded loops | CRITICAL |
| Memory | Loading large files/datasets entirely into memory | WARNING |
| Unnecessary computation | Repeated calculations, missing caching for expensive ops | WARNING |
| Indexing | Queries on unindexed columns (if schema is available) | WARNING |

### 2.4 Clean Code & Maintainability

| Check | What to Look For | Severity |
|-------|------------------|----------|
| Dead code | Commented-out code blocks, unused imports/variables | WARNING |
| Magic numbers | Hardcoded values without named constants or explanation | WARNING |
| Function size | Functions > 50 lines or doing multiple unrelated things | WARNING |
| Naming | Unclear or misleading variable/function names | WARNING |
| Duplication | Copy-pasted logic that should be extracted | WARNING |
| Error handling | Empty catch blocks, swallowed exceptions, generic catches | WARNING |

### 2.5 Convention Compliance

| Check | What to Look For | Severity |
|-------|------------------|----------|
| Project patterns | Does the code follow established project patterns? (layer structure, error handling style, naming convention) | WARNING |
| Import style | Matches project's import grouping and ordering | INFO |
| Formatting | Matches project's formatting (indentation, brackets, line length) | INFO |
| Documentation | Public APIs have adequate documentation | INFO |

---

## Phase 3: Code Review Report

Generate the report using this exact structure:

```markdown
# Code Review Report

**Scope:** [N files reviewed — list paths]
**Date:** [YYYY-MM-DD]
**Verdict:** RED — Must Fix / YELLOW — Proceed with Caution / GREEN — Approved

---

## Summary

[1-2 sentences: overall code quality assessment]

## Critical Issues (Must Fix)
<!-- Omit section entirely if none -->

| # | File:Line | Category | Issue | Recommendation |
|---|-----------|----------|-------|----------------|
| 1 | `path/to/file.ts:42` | Security | [specific issue] | [how to fix] |

## Warnings (Should Fix)
<!-- Omit section entirely if none -->

| # | File:Line | Category | Issue | Recommendation |
|---|-----------|----------|-------|----------------|
| 1 | `path/to/file.ts:15` | Performance | [specific issue] | [how to fix] |

## Info (Suggestions)
<!-- Omit section entirely if none -->

| # | File:Line | Category | Suggestion |
|---|-----------|----------|------------|
| 1 | `path/to/file.ts:8` | Convention | [suggestion] |

## Checklist Summary

| Category | Verdict | Issues |
|----------|---------|--------|
| Security | PASS / WARN / FAIL | [count] |
| Business Logic | PASS / WARN / FAIL | [count] |
| Performance | PASS / WARN / FAIL | [count] |
| Clean Code | PASS / WARN / FAIL | [count] |
| Conventions | PASS / WARN / FAIL | [count] |
```

---

## Decision Rules

| Verdict | Condition | Action |
|---------|-----------|--------|
| RED | Any CRITICAL issue exists | **STOP.** Present report. Code MUST be fixed before proceeding. |
| YELLOW | No CRITICAL, but WARNING issues exist | Present report. Ask: *"Warnings found. Fix now or proceed?"* |
| GREEN | No CRITICAL, no WARNING (INFO only is OK) | Present report. Proceed. |

### On RED verdict

List the critical issues clearly with file paths and line numbers. After the user fixes them, **re-run Phase 2 on affected files only**. Repeat until GREEN or YELLOW-confirmed.

### On GREEN verdict

> "Code review passed. No critical issues found."

---

## Anti-Patterns

| Anti-Pattern | Why It's Wrong |
|--------------|----------------|
| Reviewing the entire codebase | Scope creep — review only changed files + 1-hop dependencies |
| Vague findings ("code looks messy") | Every finding must have a specific file:line, issue, and recommendation |
| Reporting style issues as critical | Formatting is INFO, not CRITICAL — reserve severity for real risks |
| Skipping the scope confirmation | User must agree on what's being reviewed before the review begins |
| Generating fixes without asking | Report findings; let the user decide what to fix |
