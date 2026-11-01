---
description: >
  Structured debugging workflow. Guides through 4 phases:
  Understand -> Hypothesize -> Investigate -> Fix & Verify.
  Each phase produces a concrete artifact and requires user approval before proceeding.
  Use when user reports a bug, error, unexpected behavior, crash, or when debugging
  spans multiple attempts without success.
  Trigger when user says 'debug', 'fix bug', 'investigate error', 'not working',
  'something broke', 'unexpected behavior', 'crash', 'exception'.
---

# Debugging Workflow

Orchestrate structured debugging through 4 mandatory phases, each producing a concrete artifact. This workflow does not fix anything itself — it invokes existing skills and manages transitions between phases.

> **Fundamental principle:** Understand before investigating. Hypothesize before fixing. Evidence over guessing. One variable at a time.

---

## Process Flow Overview

```
Step 0: BUG COMPLEXITY ASSESSMENT
|  Assess: scope, reproducibility, error clarity, domain impact
|  Output: Complexity verdict (S / M / C) + user confirmation
|  Route:
|    S (Simple)   -> FAST-TRACK (inline investigate -> fix -> quick verify)
|    M (Medium)   -> STANDARD (4-phase, review optional)
|    C (Complex)  -> FULL CEREMONY (4-phase with all gates)
|
Step 1: UNDERSTAND THE BUG
|  Collect: symptoms, expected vs actual, environment, recent changes
|  Reference domain skills to ask the right questions
|  Output: docs/fixes/<bug>/YYYY-MM-DD-bug-report.md
|  STOP — user confirm bug description is accurate
|
Step 2: HYPOTHESIZE ROOT CAUSES
|  Generate >= 3 hypotheses ranked by likelihood
|  For each: mechanism, evidence needed, where to look
|  Reference domain skills for domain-specific hypotheses
|  Output: hypotheses appended to bug-report.md
|  STOP — user confirm investigation direction
|
Step 3: INVESTIGATE & CONFIRM ROOT CAUSE
|  Systematic investigation: read code, check logs, trace flow
|  Narrow down to confirmed root cause with evidence
|  |-- Complexity Gate: review before fix
|  Output: docs/fixes/<bug>/YYYY-MM-DD-fix-plan.md
|  STOP — user confirm root cause and fix approach
|
Step 4: FIX & VERIFY
|  Implement fix per plan
|  |-- Per fix: STOP for user review
|  +-- Per logical unit: invoke action-commit
|  |-- 4a: Confirm bug no longer reproducible
|  |-- 4b: Regression check (existing tests pass?)
|  |-- 4c: (Conditional) Invoke test-strategy -> regression test
|  |-- 4d: (Conditional) Invoke code-secure-fixer
|  |-- 4e: (Conditional) Invoke debug-fe
|  Output: docs/fixes/<bug>/YYYY-MM-DD-fix-result.md
```

---

## Artifact Registry

Every phase produces a concrete file in `docs/fixes/<bug>/`. Each artifact includes timing metadata at the top:

```markdown
Started at:  YYYY/MM/DD HH:MM:SS
Finished at: YYYY/MM/DD HH:MM:SS
Total time: X minutes
---
```

| Phase | Artifact | Description |
|-------|----------|-------------|
| Understand | `YYYY-MM-DD-bug-report.md` | Symptoms, reproduction, environment, hypotheses |
| Investigate | `YYYY-MM-DD-fix-plan.md` | Confirmed root cause, fix approach, risk assessment |
| Fix & Verify | `YYYY-MM-DD-fix-result.md` | Fix summary, verification results, regression status |

---

## Artifact Naming Convention

### Folder Naming Rules

Before creating `docs/fixes/<bug>/`:

1. **Scan existing folders** — Search `docs/fixes/` for folders related to the same module or feature
2. **Reuse if found** — If a related folder exists (same module/feature), place new artifacts there (append date-prefixed files)
3. **Create canonical name if new** — Use the module/feature name in noun-based kebab-case

### Naming Pattern

- **Folder:** `<module-or-feature-name>/` (noun-based, kebab-case)
- **Files:** `YYYY-MM-DD-bug-report.md`, `YYYY-MM-DD-fix-plan.md`, etc.
- **Multiple bugs same module:** `YYYY-MM-DD-bug-report.md`, `YYYY-MM-DD-v2-bug-report.md`

### Examples

| Scenario | Correct | Incorrect |
|----------|---------|-----------|
| Bug in user auth module | `user-auth/2026-04-02-bug-report.md` | `fix-login-crash/2026-04-02-bug-report.md` |
| Another bug in same module | `user-auth/2026-04-05-bug-report.md` (same folder) | `fix-token-expiry/2026-04-05-bug-report.md` (new folder) |
| Bug in payment service | `payment-service/2026-04-02-bug-report.md` | `debug-payment-error/2026-04-02-bug-report.md` |

> **Rule:** One module = one folder. Action-based names (fix-, debug-, investigate-) are forbidden as folder names. If unsure about the canonical name, ask the user.

---

## Skill Reference Map

At ANY phase, if the current context relates to a domain skill, reference it. Do not wait until Fix phase to use domain knowledge.

| Skill | When to Reference |
|-------|------------------|
| `code-reviewer` | Step 3 (complexity-gated review before fix) + Step 4 (post-fix verification if needed) |
| `test-strategy` | Step 4c (generate regression test case for the bug) |
| `sequence-diagram` | Step 3 (visualize actual vs expected execution flow) |
| `code-secure-fixer` | Step 4d (security-related bugs — only with scanner report) |
| `debug-fe` | Step 4e (UI-related bugs — Playwright debugging) |
| `action-commit` | Step 4 (after each approved fix) |
| `api-design` | Steps 1-3 (API contract issues, error format bugs, status code problems) |
| `database-design` | Steps 1-3 (query bugs, N+1 issues, data integrity, constraint violations) |
| `log-processing` | Steps 1-3 (log-based investigation, tracing, adding debug logging) |

**Examples of cross-phase skill usage:**

- **Understand:** User reports slow endpoint -> reference `database-design` to ask about query patterns, data volume, indexes before forming hypotheses.
- **Understand:** User reports wrong API response -> reference `api-design` to clarify expected contract, error format, status codes.
- **Hypothesize:** Database-related bug suspected -> reference `database-design` for N+1, missing index, lock contention hypotheses.
- **Investigate:** Need to trace a request flow -> reference `sequence-diagram` to visualize the actual execution path vs expected.
- **Investigate:** Logs insufficient -> reference `log-processing` to suggest adding targeted debug logging at key points.
- **Fix:** Fixing a query issue -> reference `database-design` for batch fetch / JOIN / index patterns.

---

## Step 0: Bug Complexity Assessment

Before entering the 4-phase workflow, assess the bug's complexity to determine the appropriate process depth. This prevents wasting time on full ceremony for a one-line typo fix.

### Assessment Criteria

| Signal | Simple (S) | Medium (M) | Complex (C) |
|--------|-----------|------------|-------------|
| **Scope** | Single file, obvious cause | Multi-file, unclear cause | Cross-service, intermittent |
| **Reproducibility** | Always reproducible | Mostly reproducible | Intermittent / environment-specific |
| **Error clarity** | Clear error message + stack trace | Vague error, needs tracing | No error, just wrong behavior |
| **Domain impact** | Isolated logic, no shared state | Shared logic, 1-2 services | Cross-cutting, architectural impact |
| **Risk of fix** | Low — change is localized | Medium — touches shared code | High — core logic or data |

### Sizing Prompt

Present to user:

> "Let me assess the bug complexity to determine the right debugging depth."
>
> | Criterion | Assessment |
> |-----------|------------|
> | Scope | [estimate] |
> | Reproducibility | [always/mostly/intermittent] |
> | Error clarity | [clear/vague/none] |
> | Domain impact | [isolated/shared/cross-cutting] |
> | Risk of fix | [low/medium/high] |
>
> **Verdict: [S / M / C]**
>
> Recommended flow:
> - **S -> Fast-track** (inline investigate -> fix -> quick verify)
> - **M -> Standard** (4-phase, code review optional)
> - **C -> Full ceremony** (4-phase with all gates)
>
> "Agree with this sizing, or override?"

### Fast-Track Flow (Simple)

For simple bugs, skip artifact file generation and run inline:

```
1. INLINE UNDERSTAND — Confirm symptoms in conversation
   (no bug-report.md — context captured in conversation)
2. INLINE INVESTIGATE — Read the relevant code, identify cause
   (no fix-plan.md — just explain the fix)
3. FIX — Apply the fix with user approval
4. QUICK VERIFY — Confirm bug fixed + no obvious regression
   (no fix-result.md — commit message serves as record)
```

**Gate rules still apply** — user must approve before fix is applied even in fast-track.

**Override:** User can always say "I want full process" to escalate from S to M/C flow.

### Standard Flow (Medium)

Run all 4 phases with these simplifications:
- Step 3 complexity gate review is **optional** — ask user: "Run code review before fixing, or proceed?"
- Artifact files are generated as normal
- All gates still apply

### Full Ceremony (Complex)

Run all 4 phases exactly as described below. No shortcuts.

---

## Step 1: Understand the Bug

**Goal:** Collect enough information to form accurate hypotheses. Do NOT start investigating code yet.

### 1a — Context Interrogation

Before any investigation, gather mandatory context. Apply the Context Interrogator:

1. **Full error message** — Including stack trace if available. "It doesn't work" is not enough.
2. **Reproduction steps** — Exact sequence to trigger the bug. "It happens sometimes" needs narrowing.
3. **Expected vs actual behavior** — What should happen? What happens instead?
4. **Environment** — Which environment? (dev/staging/prod) Which version? Recent deployments?
5. **Recent changes** — Was anything changed recently that might relate? New code, config, data?

> If >= 2 items are missing -> **STOP and ask**. Do not form hypotheses without adequate information.

### 1b — Reference Domain Skills for Better Questions

Based on the bug description, bring in relevant domain knowledge **during the understanding phase**:

- **API-related bug** -> reference `api-design`: ask about expected status codes, error format, request/response payload
- **Database-related bug** -> reference `database-design`: ask about data volume, query patterns, recent schema changes
- **Logging/tracing gap** -> reference `log-processing`: ask about available log output, log levels, what events are captured

### 1c — Bug Report Document

**Output:** `docs/fixes/<bug>/YYYY-MM-DD-bug-report.md`

```markdown
# Bug Report: <brief description>

## Symptoms

- **Error message:** [exact message or "no error, wrong behavior"]
- **Stack trace:** [if available]
- **Affected area:** [module, endpoint, page, or function]

## Reproduction

1. [Step 1]
2. [Step 2]
3. [Step 3]
- **Reproducibility:** Always / Mostly / Intermittent
- **Environment:** [dev/staging/prod, version, OS]

## Expected vs Actual

- **Expected:** [what should happen]
- **Actual:** [what happens instead]

## Context

- **Recent changes:** [deployments, code changes, config changes, data changes]
- **Domain skills referenced:** [which skills informed the questioning]

## Hypotheses

(Populated in Step 2)
```

**Timing:** Log start time when entering Step 1. When bug report is written, log finish time.

**Gate:**

> "Bug report saved. Summary: [1-sentence description of the bug]."
> - Proceed to Hypothesis generation?
> - Need to add more context?

---

## Step 2: Hypothesize Root Causes

**Goal:** Generate multiple plausible root causes BEFORE diving into code. This prevents tunnel vision on the first idea.

### 2a — Generate Hypotheses

Generate **>= 3 hypotheses** ranked by likelihood. For each hypothesis:

| # | Hypothesis | Likelihood | Mechanism | Evidence Needed | Where to Look |
|---|-----------|-----------|-----------|-----------------|---------------|
| 1 | [most likely cause] | High | [how this would produce the symptoms] | [what to check] | [files, logs, data] |
| 2 | [alternative cause] | Medium | [how this would produce the symptoms] | [what to check] | [files, logs, data] |
| 3 | [less likely but possible] | Low | [how this would produce the symptoms] | [what to check] | [files, logs, data] |

### 2b — Reference Domain Skills for Hypothesis Quality

- **Database-related symptoms** -> reference `database-design`: consider N+1 queries, missing indexes, lock contention, constraint violations, stale cache
- **API-related symptoms** -> reference `api-design`: consider contract mismatch, serialization issues, auth/permission errors, timeout, retry storms
- **Concurrency symptoms** -> consider race conditions, shared mutable state, deadlocks
- **Performance symptoms** -> reference `performance` rule: consider unbounded loops, memory leaks, synchronous blocking

### 2c — Update Bug Report

Append the hypotheses table to `bug-report.md`.

**Timing:** Log start time when entering Step 2. When hypotheses are approved, log finish time and calculate total duration.

**Gate:**

> "Generated [N] hypotheses. Top hypothesis: [H1 summary]."
>
> | # | Hypothesis | Likelihood |
> |---|-----------|-----------|
> | 1 | [H1] | High |
> | 2 | [H2] | Medium |
> | 3 | [H3] | Low |
>
> - Investigate starting from H1?
> - Reorder or add hypotheses?

---

## Step 3: Investigate & Confirm Root Cause

**Goal:** Systematically verify or eliminate hypotheses until the root cause is confirmed with evidence.

### 3a — Systematic Investigation

For each hypothesis (starting from highest likelihood):

1. **Announce:** "Investigating hypothesis [N]: [description]"
2. **Gather evidence** — Read relevant code, check logs, trace execution flow
3. **Verdict per hypothesis:**
   - **Confirmed** — Evidence clearly supports this hypothesis -> proceed to fix plan
   - **Partially supported** — Some evidence, needs more investigation -> continue with caveats
   - **Eliminated** — Evidence contradicts this hypothesis -> move to next hypothesis

**Reference skills during investigation:**

- Execution flow unclear -> invoke `sequence-diagram` to visualize actual vs expected path
- Logs insufficient -> reference `log-processing` to suggest adding targeted debug logging
- Data issue suspected -> reference `database-design` for query analysis patterns

### 3b — Complexity Gate (Before Fix)

Based on bug complexity from Step 0:

| Complexity | Review Action |
|-----------|---------------|
| Simple (S) | Self-review the fix approach — no external review needed |
| Medium (M) | **Ask user:** "Root cause confirmed. Run code review on affected area before fixing, or proceed?" |
| Complex (C) | **Mandatory:** Invoke `code-reviewer` on affected files before proceeding to fix |

### 3c — Fix Plan Document

Once root cause is confirmed, generate the fix plan:

**Output:** `docs/fixes/<bug>/YYYY-MM-DD-fix-plan.md`

```markdown
# Fix Plan: <brief description>

## Root Cause

- **Confirmed hypothesis:** [which hypothesis, with evidence]
- **Root cause:** [precise description of what is wrong]
- **Evidence:** [code references, log entries, data observations that confirm]
- **Eliminated hypotheses:** [which were ruled out and why]

## Fix Approach

- **Strategy:** [what will be changed to fix the root cause]
- **Files to modify:** [list with specific locations]
- **Risk assessment:** [what could go wrong with this fix]
- **Rollback plan:** [how to revert if fix causes issues]

## Scope Guard

- **In scope:** [only what is necessary to fix this bug]
- **Out of scope:** [related improvements that should be separate work]

## Verification Plan

1. [How to confirm the bug is fixed]
2. [How to verify no regression]
3. [Test cases to add — if applicable]
```

**Timing:** Log start time when entering Step 3. When fix plan is approved, log finish time.

**Gate:**

> "Root cause confirmed: [summary]. Fix plan saved."
> - Approve fix approach and proceed?
> - Need to adjust the plan?

---

## Step 4: Fix & Verify

**Goal:** Implement the fix, verify it works, and ensure no regression.

### Preparation

1. Read `fix-plan.md` — extract fix approach and files to modify
2. Present scope to user:

> "Implementing fix: [fix summary]"
> - Files to modify: [list]
> - Estimated changes: [brief]
>
> "Proceed?"

### Fix Implementation

1. **Implement** the fix according to the plan:
   - Target only the scope defined in fix-plan.md — do NOT expand to unrelated changes
   - Follow existing project conventions
   - Handle error paths

2. **Self-check** before presenting:
   - Does this address the confirmed root cause?
   - Are error paths handled?
   - Does it match project conventions?
   - Are there obvious regressions?

3. **Gate:**

   > "Fix implemented."
   > - Files modified: [list]
   > - Changes: [brief summary]
   > - Review and approve?

4. **Commit:** After user approves -> invoke `action-commit` skill

### Verification Sub-Steps

After fix is committed, verify thoroughly:

#### Step 4a — Bug No Longer Reproducible

> "Please verify: follow the reproduction steps from the bug report. Is the bug fixed?"
>
> If user cannot verify immediately, document the verification method for later.

#### Step 4b — Regression Check

> "Were any existing tests run against the changed code? Did they pass?"
>
> - If tests exist: remind user to run them
> - If tests not run: "It is strongly recommended to run existing tests before marking this fix as complete."
> - If regression found: return to fix implementation, address regression, re-verify

#### Step 4c — Test Strategy (Conditional)

**Offer for all bugs, invoke automatically for Complex (C) bugs:**

> "Would you like to generate a regression test case for this bug? This prevents the same bug from recurring."

If yes -> invoke `test-strategy` skill with:
- Bug reproduction steps as the primary test case
- Edge cases related to the root cause
- Boundary conditions near the fix

#### Step 4d — Security Check (Conditional)

**When to invoke:**
- Bug involves authentication, authorization, input validation, or data exposure -> invoke `code-secure-fixer` (with scanner report if available)
- The always-on `security` rule has been enforced during commits in Step 4 regardless

> "This bug touches security-sensitive code. Do you have a scanner report to analyze? If not, the security rules enforced during commit are the baseline."

#### Step 4e — UI Debugging (Conditional)

**When to invoke:**
- The bug is UI-related AND requires visual verification -> invoke `debug-fe` skill (requires Playwright MCP)
- Backend-only bugs -> skip

> "This is a UI bug. Would you like to run a Playwright debug session to visually verify the fix?"

### Generate Fix Result Document

After all verification steps, generate `fix-result.md`:

```markdown
# Fix Result: <brief description>

## Verdict: FIXED / PARTIALLY FIXED / NOT FIXED

## Artifacts

| Phase | Artifact | Path | Status |
|-------|----------|------|--------|
| Understand | Bug Report | `docs/fixes/<bug>/YYYY-MM-DD-bug-report.md` | Complete |
| Investigate | Fix Plan | `docs/fixes/<bug>/YYYY-MM-DD-fix-plan.md` | Approved |
| Fix & Verify | Result | (this file) | [verdict] |

## Root Cause Summary

- **Root cause:** [confirmed root cause]
- **Hypothesis confirmed:** [which hypothesis from Step 2]
- **Hypotheses eliminated:** [count] out of [total]

## Fix Summary

- **Approach:** [what was changed]
- **Files modified:** [list with paths]
- **Commits:** [hash + message for each]

## Verification Results

| Check | Status | Details |
|-------|--------|---------|
| Bug no longer reproducible | Yes/No/Untested | [details] |
| Regression check | Passed/Failed/Not run | [details] |
| Regression test added | Yes/No/Skipped | [test location if added] |
| Security check | Passed/N/A | [details] |
| UI verification | Passed/N/A | [details] |

## Prevention

- **What could have prevented this bug?**
  - [e.g., better input validation, more tests, stricter type checking]
- **Recommended follow-up:**
  - [e.g., add integration test, improve logging, update documentation]

## Final Checklist

- [ ] Bug confirmed fixed (reproduction steps no longer trigger it)
- [ ] No regressions introduced (existing tests pass)
- [ ] Regression test added (or justification for skipping)
- [ ] Commits clean with standardized messages
- [ ] Fix scope matches plan (no scope creep)
```

**Output:** `docs/fixes/<bug>/YYYY-MM-DD-fix-result.md`

**Timing:** Log start time when entering Step 4. When result document is generated, log finish time.

**Gate:**

> "Fix verification complete. Result document saved at [path]."
> "Final verdict: [FIXED / PARTIALLY FIXED / NOT FIXED]."

---

## Loop Breaker Rule

If the same bug has been attempted **3 or more times** without success, **STOP generating fixes immediately**.

### When Loop Breaker Activates

1. **Summarize all attempts:**

   | Attempt | Approach | What Happened | Why It Failed |
   |---------|----------|---------------|---------------|
   | 1 | [approach] | [result] | [reason] |
   | 2 | [approach] | [result] | [reason] |
   | 3 | [approach] | [result] | [reason] |

2. **Analyze the pattern:** Why do fixes keep failing? Is the root cause actually different from what we confirmed? Is there a deeper architectural issue?

3. **Propose next steps** (choose one):
   - **Revert and rethink** — Go back to Step 2 with fresh hypotheses
   - **Reduce scope** — Create a minimal reproduction case, isolate the smallest failing scenario
   - **Escalate** — This bug may need a different perspective, specialized knowledge, or architectural change
   - **Accept and mitigate** — If the fix is too costly, document the bug and implement a workaround

> "Loop Breaker activated: 3 fix attempts failed. Continuing to guess is counter-productive."

---

## Error Recovery

| Situation | Action |
|-----------|--------|
| Bug cannot be reproduced | Ask for more context: exact data, timing, environment. Check if environment differs from user's. |
| All hypotheses eliminated | Return to Step 2 — broaden scope, generate new hypotheses from a different angle. Consider environmental or data-dependent causes. |
| Fix introduces regression | Revert fix. Return to Step 3 — re-analyze fix approach. The fix plan's risk assessment was wrong — update it. |
| Fix works locally but fails in other environment | Check environment-specific: config differences, data differences, dependency versions, resource limits. |
| 3+ failed fix attempts | Loop Breaker activates (see above). |
| Root cause is architectural (not a simple code fix) | Document the architectural issue. Recommend `/design` or `/feature` workflow for the structural fix. This debugging session documents the finding. |
| User provides additional info mid-investigation | Update bug-report.md. Re-evaluate hypotheses. May skip ahead or restart from Step 2. |
| Context getting long | Save progress and start new session. Artifact files (bug-report.md, fix-plan.md) contain everything needed to resume. |

---

## Key Principles

1. **Understand before investigating** — Collect symptoms, reproduction, and environment BEFORE reading code. Premature code reading causes tunnel vision.
2. **Hypothesize before fixing** — Generate >= 3 hypotheses ranked by likelihood. Never jump to the first idea.
3. **One variable at a time** — Change one thing, verify, then move on. Shotgun fixes obscure the real cause.
4. **Evidence over guessing** — Every hypothesis needs evidence to confirm or eliminate. "I think it might be..." is not evidence.
5. **No regression = non-negotiable** — A fix that breaks something else is not a fix. Verification must include regression check.
6. **Loop Breaker prevents rabbit holes** — After 3 failed attempts, stop and reassess. Persistence on the wrong path wastes time.
7. **Artifacts preserve investigation progress** — If the session ends, bug-report.md + fix-plan.md contain everything needed to resume without starting over.
8. **Scope guard prevents fix creep** — Fix only what is in the fix plan. Improvements spotted during debugging go to a separate `/improve` session.
