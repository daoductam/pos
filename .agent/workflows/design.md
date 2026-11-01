---
description: >
  Structured system/project design workflow. Guides through 5 phases:
  Vision -> Requirements -> Architecture -> Contracts -> Finalization.
  Each phase produces a concrete artifact and requires user approval before proceeding.
  Use when designing a new system, service, major component, or rearchitecting existing systems.
  Trigger when user says 'design system', 'architecture', 'design project', 'design service',
  'new system', 'system design', 'rearchitect', 'design from scratch'.
---

# System Design Workflow

Orchestrate structured system/project design through 5 mandatory phases, each producing a concrete artifact. This workflow produces **design documents**, not code — it invokes existing skills and manages transitions between phases.

> **Fundamental principle:** Vision before requirements. Requirements before architecture. Architecture before contracts. Orchestrate, don't implement.

---

## Process Flow Overview

```
Step 0: DESIGN SCOPE ASSESSMENT
|  Assess: scope, stakeholders, contracts, data model complexity
|  Output: Scope verdict (S / M / L) + user confirmation
|  Route:
|    S (Component) -> STREAMLINED (Steps 1+3 combined, lighter docs)
|    M (Service)   -> STANDARD (5-phase, spec-reviewer optional)
|    L (System)    -> FULL CEREMONY (5-phase with all gates)
|
Step 1: VISION & GOALS
|  Invoke: brainstorming skill (terminal state overridden)
|  Clarify: what, why, for whom, success criteria
|  Output: docs/designs/<project>/YYYY-MM-DD-vision.md
|  STOP — user approve vision
|
Step 2: REQUIREMENTS DISCOVERY
|  |-- 2a: Invoke spec-reviewer skill -> validate vision completeness
|  |       STOP — must be Approved
|  |-- 2b: Functional requirements (use cases, user stories)
|  |-- 2c: Non-functional requirements (performance, scalability, security)
|  +-- 2d: Constraints (tech stack, timeline, compliance)
|  Output: docs/designs/<project>/YYYY-MM-DD-requirements.md
|  STOP — user approve requirements
|
Step 3: ARCHITECTURE DESIGN
|  |-- 3a: High-level component diagram (services, boundaries, communication)
|  |-- 3b: Data architecture (schema, ID strategy, partitioning)
|  +-- 3c: (Optional) Invoke sequence-diagram for critical flows
|  Output: docs/designs/<project>/YYYY-MM-DD-architecture.md
|  STOP — user approve architecture
|
Step 4: API & DATA CONTRACTS
|  |-- 4a: API contracts (endpoints, error formats, pagination, auth)
|  |-- 4b: Data contracts (schemas, migrations, indexes)
|  +-- 4c: Integration contracts (events, messages, shared types)
|  Output: docs/designs/<project>/YYYY-MM-DD-contracts.md
|  STOP — user approve contracts
|
Step 5: DESIGN FINALIZATION
|  |-- 5a: Design review (complexity-gated)
|  |-- 5b: Implementation roadmap (phases, milestones, task breakdown)
|  +-- 5c: (Optional) Invoke sequence-diagram for all critical paths
|  Output: docs/designs/<project>/YYYY-MM-DD-<project>_result.md
|  STOP — Design APPROVED / APPROVED WITH WARNINGS / NEEDS WORK
```

---

## Artifact Registry

Every phase produces a concrete file in `docs/designs/<project>/`. Each artifact includes timing metadata at the top:

```markdown
Started at:  YYYY/MM/DD HH:MM:SS
Finished at: YYYY/MM/DD HH:MM:SS
Total time: X minutes
---
```

| Phase | Artifact | Description |
|-------|----------|-------------|
| Vision | `YYYY-MM-DD-vision.md` | Project goals, scope, stakeholders, success criteria |
| Requirements | `YYYY-MM-DD-requirements.md` | Functional, non-functional requirements, constraints |
| Architecture | `YYYY-MM-DD-architecture.md` | Component diagram, data architecture, communication patterns |
| Contracts | `YYYY-MM-DD-contracts.md` | API contracts, data contracts, integration contracts |
| Finalization | `YYYY-MM-DD-<project>_result.md` | Design review results, implementation roadmap, final verdict |

---

## Artifact Naming Convention

### Folder Naming Rules

Before creating `docs/designs/<project>/`:

1. **Scan existing folders** — Search `docs/designs/` for folders related to the same project or system
2. **Reuse if found** — If a related folder exists (same project, different iteration), place new artifacts there
3. **Create canonical name if new** — Use the project/system name in noun-based kebab-case

### Naming Pattern

- **Folder:** `<project-or-system-name>/` (noun-based, kebab-case)
- **Files:** `YYYY-MM-DD-vision.md`, `YYYY-MM-DD-requirements.md`, etc.
- **Versioned designs:** `YYYY-MM-DD-v2-architecture.md` for revisions within the same project

### Examples

| Scenario | Correct | Incorrect |
|----------|---------|-----------|
| New payment system | `payment-system/2026-04-02-vision.md` | `design-payment-system/2026-04-02-vision.md` |
| Iteration on same system | `payment-system/2026-04-15-architecture.md` (same folder) | `redesign-payment/2026-04-15-architecture.md` (new folder) |
| Auth service design | `auth-service/2026-04-02-vision.md` | `build-auth-service/2026-04-02-vision.md` |

> **Rule:** One project = one folder. Action-based names (design-, build-, create-) are forbidden as folder names. If unsure about the canonical name, ask the user.

---

## Skill Reference Map

At ANY phase, if the current context relates to a domain skill, reference it. Domain skills inform design decisions — they are not just for implementation.

| Skill | When to Reference |
|-------|------------------|
| `brainstorming` | Step 1 (vision exploration — terminal state overridden) |
| `spec-reviewer` | Step 2a (vision completeness validation — terminal state overridden) |
| `api-design` | Steps 2-4 (API patterns, REST vs GraphQL, error formats, pagination, auth) |
| `database-design` | Steps 3-4 (schema design, ID strategy, partitioning, indexing, migration) |
| `log-processing` | Steps 2-3 (observability requirements, logging architecture) |
| `sequence-diagram` | Steps 3c, 5c (visualize critical flows, component interactions) |
| `code-reviewer` | Step 5a (design document review — NOT code review. Uses deep reasoning for coherence checking.) |
| `action-commit` | After each phase artifact is finalized |

**Examples of cross-phase skill usage:**

- **Vision:** User exploring a new API platform -> reference `api-design` for API-first vs code-first decision, REST vs GraphQL trade-offs at the vision level.
- **Requirements:** Defining NFRs for data layer -> reference `database-design` for partitioning thresholds, ID strategy implications on scalability.
- **Requirements:** Defining observability needs -> reference `log-processing` for log format options, what events need structured logging.
- **Architecture:** Designing service communication -> reference `api-design` for synchronous vs async, event-driven vs request-response patterns.
- **Architecture:** Designing data model -> reference `database-design` for schema conventions, index strategy, Snowflake vs UUID vs ULID comparison.
- **Contracts:** Writing API specification -> reference `api-design` for standard error format, pagination pattern (offset vs cursor), auth header conventions.
- **Contracts:** Writing data schema -> reference `database-design` for migration format, constraint naming, index naming conventions.

---

## Step 0: Design Scope Assessment

Before entering the 5-phase workflow, assess the design scope to determine the appropriate process depth. This prevents full ceremony for a simple component addition.

### Assessment Criteria

| Signal | Component (S) | Service (M) | System (L) |
|--------|--------------|-------------|-------------|
| **Scope** | Single component/module | Complete service/API | Multi-service system |
| **Stakeholders** | 1 developer/team | 1-2 teams | Cross-team/organization |
| **New contracts** | Internal interfaces only | External API contracts | Multiple APIs + schemas + events |
| **Data model** | Extend existing schema | New tables/collections | New DB + cross-service data flows |
| **Duration** | Days | Weeks | Months |

### Sizing Prompt

Present to user:

> "Let me assess the design scope to determine the right process depth."
>
> | Criterion | Assessment |
> |-----------|------------|
> | Scope | [component/service/system] |
> | Stakeholders | [who is involved] |
> | New contracts | [internal only / external / multiple] |
> | Data model | [extend / new tables / new DB] |
> | Estimated duration | [days/weeks/months] |
>
> **Verdict: [S / M / L]**
>
> Recommended flow:
> - **S -> Streamlined** (Steps 1+3 combined, lighter docs)
> - **M -> Standard** (5-phase, spec-reviewer optional)
> - **L -> Full ceremony** (5-phase with all gates)
>
> "Agree with this sizing, or override?"

### Streamlined Flow (Component — Size S)

For small component-level designs:

```
1. VISION + ARCHITECTURE COMBINED
   Invoke brainstorming (terminal override) to explore the component
   Include high-level architecture decisions in the spec
   Output: docs/designs/<project>/YYYY-MM-DD-vision.md
   (combines vision + architecture into one document)

2. CONTRACTS (if external interfaces involved)
   Define interfaces/contracts inline or in separate doc
   Output: docs/designs/<project>/YYYY-MM-DD-contracts.md (if needed)

3. FINALIZATION
   Self-review the design document
   Generate implementation roadmap
   Output: docs/designs/<project>/YYYY-MM-DD-<project>_result.md
```

**Gate rules still apply** — user must approve before each transition.

**Override:** User can always say "I want full process" to escalate.

### Standard Flow (Service — Size M)

Run all 5 phases with these simplifications:
- Step 2a (spec-reviewer on vision) is **optional** — ask user: "Run vision review or proceed to requirements?"
- All other gates apply as normal
- Artifact files are generated for each phase

### Full Ceremony (System — Size L)

Run all 5 phases exactly as described below. No shortcuts.

---

## Step 1: Vision & Goals

**Goal:** Establish a clear, shared understanding of what is being built and why, before any technical design.

**Invoke:** `brainstorming` skill.

**IMPORTANT — Terminal state override:**

When invoking `brainstorming` within this workflow, its normal terminal state (auto-invoke `writing-plans`) is **overridden**. After the vision is explored and documented, control returns to this workflow for Requirements Discovery.

> Instruction to pass: "After the vision/spec is approved and written to file, STOP. Do NOT invoke `writing-plans`. Return control to the design workflow for the Requirements phase."

### 1a — Key Questions to Explore

Guide the brainstorming to answer:

1. **What** are we building? (scope boundaries)
2. **Why** are we building it? (business value, problem being solved)
3. **For whom?** (users, teams, systems that will interact with this)
4. **What does success look like?** (measurable outcomes)
5. **What are the known unknowns?** (risks, open questions)

### 1b — Reference Domain Skills

During vision exploration:

- System involves APIs -> reference `api-design` for API-first vs code-first discussion
- System involves data storage -> reference `database-design` for data volume/pattern implications
- System needs observability -> reference `log-processing` for monitoring strategy at the vision level

**Output:** `docs/designs/<project>/YYYY-MM-DD-vision.md`

```markdown
# Vision: <project name>

## What We're Building
[Scope definition — what is included and what is NOT included]

## Why
[Business value, problem being solved, opportunity]

## For Whom
[Primary users/consumers, stakeholder map]

## Success Criteria
| # | Criterion | How to Measure |
|---|-----------|---------------|
| 1 | [criterion] | [measurement] |

## Known Unknowns & Risks
| # | Risk/Unknown | Impact | Mitigation |
|---|-------------|--------|------------|
| 1 | [risk] | [impact] | [mitigation or "TBD"] |

## Domain Skills Referenced
[Which skills informed the vision discussion and key takeaways]
```

**Timing:** Log start time when entering Step 1. When vision is approved, log finish time.

**Gate:**

> "Vision document saved. Summary: [1-2 sentence summary]."
> - Proceed to Requirements Discovery?
> - Need to revise the vision?

---

## Step 2: Requirements Discovery

**Goal:** Extract complete functional and non-functional requirements from the approved vision.

### Step 2a — Vision Review (Quality Gate)

**Invoke:** `spec-reviewer` skill with the vision document from Step 1.

**Terminal state override:** When `spec-reviewer` reaches its Approved state, it normally asks the user to choose a planning tool. Within this workflow, **skip that question** — proceed directly to Step 2b.

> Instruction to pass: "After generating the Spec Sentinel Report, if approved, do NOT ask the user to choose a planning tool. Return control to the design workflow."

**Gate:**

| Verdict | Action |
|---------|--------|
| Issues Found | Return to Step 1 to revise the vision. Present specific gaps. |
| Warnings Only | Ask: *"Warnings detected. Proceed to requirements or revise vision first?"* |
| Approved | Continue to Step 2b. |

### Step 2b — Functional Requirements

Extract from the vision:

- **Use cases / User stories** — What can users do? What does the system do in response?
- **Business rules** — Domain-specific logic, constraints, validations
- **Integration points** — Which external systems does it interact with? How?

Reference `api-design` if the system exposes or consumes APIs — capture API-level requirements here.

### Step 2c — Non-Functional Requirements (NFRs)

| Category | Questions to Ask |
|----------|-----------------|
| **Performance** | Expected throughput? Latency targets? Data volume? |
| **Scalability** | Growth projections? Horizontal vs vertical? |
| **Availability** | Uptime SLA? Recovery time? Failover strategy? |
| **Security** | Authentication method? Authorization model? Data sensitivity? Compliance? |
| **Observability** | Logging requirements? Metrics? Alerting? Tracing? |
| **Maintainability** | Team size? Deployment frequency? Technology constraints? |

Reference `database-design` for data-related NFRs (volume, partitioning thresholds, consistency requirements).
Reference `log-processing` for observability NFRs (log format, retention, search requirements).

### Step 2d — Constraints

- **Technology:** Required tech stack, language, framework, cloud provider
- **Timeline:** Delivery deadlines, milestone dates
- **Budget/resources:** Team size, infrastructure budget
- **Compliance:** Regulations, data residency, audit requirements

**Output:** `docs/designs/<project>/YYYY-MM-DD-requirements.md`

```markdown
# Requirements: <project name>

## Source
- **Vision:** [link to vision.md]

## Functional Requirements

### Use Cases
| # | Actor | Action | System Response |
|---|-------|--------|----------------|
| UC-1 | [actor] | [action] | [response] |

### Business Rules
| # | Rule | Rationale |
|---|------|-----------|
| BR-1 | [rule] | [why] |

## Non-Functional Requirements

| Category | Requirement | Target |
|----------|------------|--------|
| Performance | [requirement] | [target value] |
| Scalability | [requirement] | [target value] |
| Availability | [requirement] | [target value] |
| Security | [requirement] | [standard/compliance] |
| Observability | [requirement] | [tooling/format] |

## Constraints

| Type | Constraint | Impact |
|------|-----------|--------|
| Technology | [constraint] | [how it affects design] |
| Timeline | [constraint] | [deadline] |
| Compliance | [constraint] | [regulation] |

## Domain Skills Referenced
[Which skills informed requirements and key decisions]
```

**Timing:** Log start time when entering Step 2. When requirements are approved, log finish time.

**Gate:**

> "Requirements document saved. Functional: [N] use cases, [M] business rules. NFRs: [categories covered]. Constraints: [N] identified."
> - Proceed to Architecture Design?
> - Need to adjust requirements?

---

## Step 3: Architecture Design

**Goal:** Design the system's structure — components, communication, and data architecture — satisfying the requirements from Step 2.

### Step 3a — High-Level Component Architecture

Design the system's components and their interactions:

1. **Component identification** — What services/modules make up the system?
2. **Boundaries** — What belongs in each component? (bounded contexts)
3. **Communication** — How do components talk? (sync/async, REST/gRPC/events)
4. **Infrastructure** — Deployment model (monolith, microservices, serverless)

Reference `api-design` for:
- Service communication patterns (REST vs GraphQL vs gRPC)
- API gateway patterns
- Authentication and authorization architecture

### Step 3b — Data Architecture

Reference `database-design` extensively:

1. **Entity model** — Core entities and relationships
2. **Database selection** — SQL vs NoSQL (reference `sql-database-selection.md` / `nosql-database-selection.md`)
3. **ID strategy** — Reference `id-strategy.md` (Snowflake vs UUID v4 vs UUID v7 vs ULID)
4. **Partitioning** — Reference `partitioning.md` (when needed, strategy selection)
5. **Data flow** — How data moves between components (sync replication, event sourcing, CQRS)

### Step 3c — Sequence Diagrams (Optional)

For critical flows, ask:

> "Would you like to generate sequence diagrams for the critical execution paths? This helps validate the architecture before moving to contracts."

If yes -> invoke `sequence-diagram` skill for the top 2-3 flows.

**Output:** `docs/designs/<project>/YYYY-MM-DD-architecture.md`

```markdown
# Architecture: <project name>

## Source
- **Vision:** [link to vision.md]
- **Requirements:** [link to requirements.md]

## Component Architecture

### System Overview
[High-level component diagram — ASCII or description]

### Components
| Component | Responsibility | Technology | Communication |
|-----------|---------------|------------|---------------|
| [name] | [what it does] | [tech stack] | [how it communicates] |

### Communication Patterns
[Sync vs async, protocols, API gateway, event bus]

## Data Architecture

### Entity Model
[Entity-relationship description or diagram]

### Database Decisions
| Decision | Choice | Rationale |
|----------|--------|-----------|
| Database type | [SQL/NoSQL/both] | [why] |
| ID strategy | [Snowflake/UUID/ULID] | [why] |
| Partitioning | [strategy or N/A] | [why] |

### Data Flow
[How data moves between components]

## Sequence Diagrams
[Links to diagrams if generated, or "Deferred to Step 5c"]

## Architecture Decision Records (ADRs)
| # | Decision | Alternatives Considered | Rationale |
|---|----------|------------------------|-----------|
| ADR-1 | [decision] | [alternatives] | [why this choice] |

## Domain Skills Referenced
[Which skills informed architecture decisions]
```

**Timing:** Log start time when entering Step 3. When architecture is approved, log finish time.

**Gate:**

> "Architecture document saved. Components: [N]. Databases: [list]. Key decisions: [summary]."
> - Proceed to API & Data Contracts?
> - Need to revise architecture?

---

## Step 4: API & Data Contracts

**Goal:** Define precise, implementation-ready contracts that bridge design to code.

### Step 4a — API Contracts

Reference `api-design` extensively:

1. **Endpoints** — For each API endpoint: method, path, request/response schema, status codes
2. **Error format** — Standard error response structure (reference `api-design` conventions)
3. **Pagination** — Offset vs cursor-based (reference `api-design` decision tree)
4. **Authentication** — Auth header, token format, scope/permission model
5. **Versioning** — API versioning strategy (URL path, header, query param)

### Step 4b — Data Contracts

Reference `database-design`:

1. **Table/collection schemas** — Columns, types, constraints, defaults
2. **Indexes** — Which columns, covering indexes, unique constraints
3. **Migrations** — Reference `migrations.md` for migration file format and naming
4. **Seed data** — Required initial data or reference data

### Step 4c — Integration Contracts

For systems that communicate with other services:

1. **Event schemas** — Event name, payload structure, producer, consumer
2. **Message formats** — Queue/topic names, message structure, retry policy
3. **Shared types** — DTOs, enums, constants shared across service boundaries

**Output:** `docs/designs/<project>/YYYY-MM-DD-contracts.md`

```markdown
# Contracts: <project name>

## Source
- **Architecture:** [link to architecture.md]

## API Contracts

### [Service/Module Name]

#### [Endpoint Group]

| Method | Path | Description |
|--------|------|-------------|
| GET | /api/v1/[resource] | [description] |
| POST | /api/v1/[resource] | [description] |

**Request/Response schemas** for each endpoint: [detailed schemas]

### Error Format
[Standard error response structure]

### Pagination
[Strategy and format]

### Authentication
[Auth mechanism and header format]

## Data Contracts

### [Table/Collection Name]
| Column | Type | Constraints | Default | Description |
|--------|------|------------|---------|-------------|
| id | [type] | PK | [strategy] | [description] |

### Indexes
| Table | Index | Columns | Type | Rationale |
|-------|-------|---------|------|-----------|
| [table] | [name] | [columns] | [unique/covering] | [why] |

### Migration Plan
[Migration sequence and naming]

## Integration Contracts

### Events
| Event | Producer | Consumer(s) | Payload Schema |
|-------|----------|-------------|---------------|
| [event] | [service] | [services] | [schema] |

## Domain Skills Referenced
[Which skills informed contract decisions]
```

**Timing:** Log start time when entering Step 4. When contracts are approved, log finish time.

**Gate:**

> "Contracts document saved. APIs: [N] endpoints. Tables: [N]. Events: [N]."
> - Proceed to Design Finalization?
> - Need to adjust contracts?

---

## Step 5: Design Finalization

**Goal:** Review the complete design for coherence and produce an implementation roadmap.

### Step 5a — Design Review (Complexity-Gated)

**IMPORTANT:** Design review evaluates **documents**, not code. This requires deep reasoning about completeness, consistency, and feasibility.

Based on design scope from Step 0:

| Scope | Review Action |
|-------|---------------|
| Component (S) | Self-review: check that architecture satisfies requirements satisfies vision |
| Service (M) | Standard review: systematically verify each requirement has an architecture component, each component has contracts |
| System (L) | **Thorough review:** Invoke design review covering: completeness (all requirements addressed?), consistency (no contradictions?), feasibility (can this be built with stated constraints?), risk (what could go wrong?) |

**Review Checklist (all sizes):**

- [ ] Every functional requirement has a corresponding component and contract
- [ ] Every NFR has an architectural decision addressing it
- [ ] All constraints are satisfied by the design
- [ ] No contradictions between architecture and contracts
- [ ] Data flows are complete (no orphan data, no missing transformations)
- [ ] Security model covers all access paths
- [ ] Observability requirements are addressable with the architecture

### Step 5b — Implementation Roadmap

Decompose the design into implementable phases and milestones:

```markdown
## Implementation Roadmap

### Phase 1: [Foundation]
- **Goal:** [what this phase delivers]
- **Tasks:** [high-level task list]
- **Estimated effort:** [days/weeks]
- **Dependencies:** None

### Phase 2: [Core Features]
- **Goal:** [what this phase delivers]
- **Tasks:** [high-level task list]
- **Estimated effort:** [days/weeks]
- **Dependencies:** Phase 1

### Phase 3: [Integration & Polish]
...
```

> This roadmap can be fed directly into the `/feature` workflow for each phase's implementation.

### Step 5c — Sequence Diagrams (Optional)

If not generated in Step 3c, or if the design has evolved:

> "Would you like to generate sequence diagrams for all critical paths? This provides a complete visual reference for implementation."

If yes -> invoke `sequence-diagram` skill.

### Generate Design Result Document

**Output:** `docs/designs/<project>/YYYY-MM-DD-<project>_result.md`

```markdown
# Design Result: <project name>

## Verdict: APPROVED / APPROVED WITH WARNINGS / NEEDS WORK

## Artifacts

| Phase | Artifact | Path | Status |
|-------|----------|------|--------|
| Vision | Vision | `docs/designs/<project>/YYYY-MM-DD-vision.md` | Approved |
| Requirements | Requirements | `docs/designs/<project>/YYYY-MM-DD-requirements.md` | Approved |
| Architecture | Architecture | `docs/designs/<project>/YYYY-MM-DD-architecture.md` | Approved |
| Contracts | Contracts | `docs/designs/<project>/YYYY-MM-DD-contracts.md` | Approved |
| Finalization | Result | (this file) | [verdict] |

## Design Review Summary

- **Completeness:** [all requirements addressed? gaps?]
- **Consistency:** [any contradictions found?]
- **Feasibility:** [can this be built within constraints?]
- **Risks identified:** [count and summary]

## Architecture Summary

- **Components:** [N] ([list])
- **Databases:** [list with types]
- **APIs:** [N] endpoints across [M] services
- **Integration points:** [N] events/messages

## Implementation Roadmap Summary

| Phase | Goal | Effort | Dependencies |
|-------|------|--------|-------------|
| 1 | [goal] | [estimate] | None |
| 2 | [goal] | [estimate] | Phase 1 |

## Sequence Diagrams

- **Status:** Generated / Skipped
- **Paths covered:** [list if generated]

## Next Steps

- [ ] Implementation Phase 1 via `/feature` workflow
- [ ] Set up project scaffolding
- [ ] Configure CI/CD pipeline
- [ ] Communicate design to stakeholders

## Final Checklist

- [ ] All requirements traceable to architecture components
- [ ] All components have defined contracts
- [ ] NFRs addressed by architectural decisions
- [ ] No contradictions in the design
- [ ] Implementation roadmap is actionable
- [ ] Design artifacts committed and accessible
```

**Timing:** Log start time when entering Step 5. When result document is generated, log finish time.

**Gate:**

> "Design complete. Result document saved at [path]."
> "Final verdict: [APPROVED / APPROVED WITH WARNINGS / NEEDS WORK]."

---

## Terminal State Overrides

This workflow overrides the terminal state of 2 skills to maintain orchestration control:

### 1. Brainstorming Override (Step 1)

```
Normal flow:    brainstorming -> auto-invoke writing-plans
Override:       brainstorming -> STOP, return to design workflow
Reason:         Design workflow needs Requirements Discovery (Step 2) before
                any planning. The brainstorming output is a VISION document,
                not an implementation spec.
```

### 2. Spec-Reviewer Override (Step 2a)

```
Normal flow:    spec-reviewer (Approved) -> ask user to choose planning tool
Override:       spec-reviewer (Approved) -> STOP, return to design workflow
Reason:         Design workflow continues with Requirements extraction (Step 2b),
                not implementation planning.
```

---

## Error Recovery

| Situation | Action |
|-----------|--------|
| Vision too vague (user cannot define scope or success criteria) | Ask targeted questions: "If this project is successful in 6 months, what would be true?" Break down into smaller scopes. |
| Requirements contradictory (two NFRs conflict) | Highlight the conflict explicitly. Ask user to prioritize: "We can optimize for [A] or [B], but not both. Which is more critical?" |
| Architecture doesn't meet NFRs | Return to Step 3. Identify which NFR is not satisfied. Propose architectural alternatives that address it. |
| Contract design conflicts with architecture | Return to Step 3 — the architecture may need revision. Contracts reveal implementation constraints that expose architecture gaps. |
| Design review finds major gaps | Return to the relevant step (vision/requirements/architecture). The review identifies which phase has the gap. |
| Scope creep during design | Stop and reassess. If scope changed significantly, return to Step 0 to re-size. Update all affected artifacts. |
| User wants to skip directly to implementation | Warn: "Implementation without design creates technical debt. At minimum, capture vision + contracts before coding." Offer streamlined flow. |
| Context getting long | Save progress and start new session. Artifact files contain everything needed to resume from any phase. |

---

## Key Principles

1. **Orchestrate, don't implement** — This workflow produces DESIGN documents, not code. The skills do analysis and validation work.
2. **Vision before requirements, requirements before architecture** — Each phase builds on the previous. Skipping creates gaps that surface late.
3. **Every phase has an artifact** — vision.md -> requirements.md -> architecture.md -> contracts.md -> result.md. Progress is never lost.
4. **Every transition has a gate** — Never auto-proceed between phases. User must approve.
5. **Domain skills available everywhere** — `api-design`, `database-design`, `log-processing` inform decisions at any phase, not just contracts.
6. **Contracts are the bridge to implementation** — They must be precise enough to code from. Vague contracts mean the design is incomplete.
7. **Design review validates coherence** — Architecture must satisfy requirements, which must satisfy vision. Review checks this chain.
8. **Recovery is always possible** — User can return to any phase. Contracts revealing architecture gaps is normal — it means the process is working.
