---
name: writing-plans
description: "Use when you have a spec or requirements for a multi-step task, before touching code. Transforms a spec.md (from brainstorming) into a detailed, structured implementation plan using a proven template. Trigger this whenever: the user says 'create a plan', 'plan this out', 'turn this spec into a plan', 'write an implementation plan'; when a spec doc exists and the user wants to start building; or when the brainstorming skill hands off to planning. Always use this skill before writing any implementation code for a multi-step feature."
allowed-tools: Read, Glob, Grep, Bash, Write
---

# Writing Plans

Transform a validated spec into a concrete, structured implementation plan using the plan template at `references/plan-template.md`.

## When You're Invoked

You're typically called after `brainstorming` completes and a spec doc exists at `docs/plans/<topic>/YYYY-MM-DD-spec.md`. You can also be invoked with inline requirements or a file path in `$ARGUMENTS`.

Output: `docs/plans/<topic>/YYYY-MM-DD-plan.md`

---

## Step 1: Read the Template

Read `references/plan-template.md` from the same directory as this skill. This is your output format — follow it section by section.

---

## Step 2: Find and Read the Spec

1. Check `$ARGUMENTS` for a file path or inline requirements
2. Search `docs/plans/` for the most recent spec file
3. Read the full codebase context: key files, tech stack, existing patterns, recent commits
4. If no spec exists, ask the user to provide one

Read the spec carefully. Understand the *why* behind requirements — it shapes how you order phases and define contracts.

---

## Step 3: Analyze Before Writing

Work through these before touching the plan:

- **Contracts first**: What interfaces and data structures must exist? Define these before tasks — tasks depend on contracts, not the other way around.
- **Phases**: What's the natural implementation order? Each phase should leave the system in a working (if partial) state.
- **Artifact Registry**: What files will be created or modified? Which task owns each?
- **Task Graph**: Which tasks can run in parallel? What are the true dependencies?
- **Edge Cases**: What scenarios need explicit handling? Which task handles each?
- **Risks**: What's uncertain or technically risky? Plan spikes for unknowns.

---

## Step 4: Fill in the Plan Template

Work through each section of `references/plan-template.md` in order. Key guidance per section:

### Section 1 — Context
Extract the problem statement and affected modules from the spec. Be explicit about Non-Goals — things the spec explicitly excludes or that would be scope creep.

### Section 2 — Constraints
Fill in the tech stack from the spec/codebase. Add performance budgets from spec requirements (e.g., "< 200ms p95"). Default rules (no unrelated module changes, no new deps without reason) apply unless the spec overrides.

### Section 3 — Conventions
Derive from the existing codebase — don't invent conventions, read the actual code. If the project has a consistent pattern for error handling, logging, or naming, capture it here. This section is shared across all tasks and prevents inconsistency when tasks are implemented in parallel.

### Section 4 — Contracts (Critical)
Define ALL interfaces and data structures before writing any task specs. Tasks reference contracts — not the other way around. For each interface method, specify:
- Pre-conditions (what must be true for the call to be valid)
- Post-conditions (what is guaranteed on success)
- What it throws and when

This section is what separates a plan from a list of bullet points. It forces the real design decisions to be made upfront.

### Section 5 — Target Architecture
Show the interaction flow as a call graph. Document key decisions with rationale — not just what, but why. This section answers "how does data flow through the system?"

### Section 6 — Artifact Registry
List every file that will be created or modified. Map each to its owner task and the contract it implements. This makes the plan auditable: after implementation, every file should be accounted for.

### Section 7 — Task Graph
Build the dependency table, then draw the ASCII dependency graph. Be explicit about what can run in parallel — this is valuable for teams and for implementation ordering.

### Section 8 — Task Specifications
For each task:
- **Input**: Where does input come from? Name the source task and the type.
- **Output**: What does this task produce? Name the consumer tasks.
- **Files**: Exact file paths with create/modify intent.
- **Responsibilities**: What contracts from Section 4 does this implement?
- **Acceptance Criteria**: Concrete, checkable conditions. Format: `method(input) → expected output`. Never vague ("works correctly"). Each criterion should reference a Section 9 edge case where relevant.

Max 200 LOC per task (excluding tests). Split if larger.

### Section 9 — Edge Cases
Every edge case must have a "Handled In" column pointing to a specific task. If an edge case has no owner, it will be forgotten. Pull these from the spec's constraints, error conditions, and boundary values.

### Section 10 — Risks
Flag what's uncertain or technically risky. If a risk is high-likelihood or high-impact, add a spike task in the Task Graph to resolve it before the dependent implementation tasks begin.

### Sections 11–13
Fill these in to the extent the spec provides information. Rollout and Future Improvements are optional — include them only if the spec addresses them.

---

## After Writing the Plan

1. Save to `docs/plans/<topic>/YYYY-MM-DD-plan.md`
2. Tell the user the plan is ready with its path
3. ⛔ STOP — Ask user: "Plan document written. Commit plan and spec to git?"
   - If approved → Commit to git alongside the spec
   - If declined → Continue without committing (user may want to review or edit first)
