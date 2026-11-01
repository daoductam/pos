Started at: 2026/10/12 15:54:00 +07
Finished at: 2026/10/12 15:54:00 +07
Total time: 0 minutes
---

# Design Result: Catalog Service

## Final Verdict

**APPROVED WITH WARNINGS — ready for implementation after the pre-coding gates below.**

The five design phases are complete and internally coherent. No critical security, data ownership or business-logic gap was found in the module design. The remaining warnings are integration obligations with the parent platform and are explicitly tracked; they should be completed before the first production-like vertical slice is coded.

## Design Scope

| Item | Result |
|---|---|
| Module | Module 2 — Menu / Catalog service |
| Workflow | Standard Service Flow, scope M |
| P0 | Categories, items, variants, modifier groups/options, tenant-global menu availability, POS read, Order resolver, events |
| P1 | Fixed-price combo, current branch variant price override, description and normalized tags for AI cold start |
| Out of scope | Order lifecycle, payment, Inventory recipes/stock, KDS workflow, dynamic pricing, full price history, multilingual/media/search ranking |
| Persistence | Service-owned normalized PostgreSQL database |
| Stack target | Java 21, Spring Boot 3.x, REST/OpenAPI 3.1, AsyncAPI/JSON Schema, UUID v7, integer VND |
| Final status | Approved with warnings |

## Artifacts

1. [Vision](./2026-10-12-vision.md) — approved.
2. [Vision review](./2026-10-12-vision-review.md) — warnings accepted and resolved into requirements.
3. [Requirements](./2026-10-12-requirements.md) — approved.
4. [Architecture](./2026-10-12-architecture.md) — approved.
5. [API & Data Contracts](./2026-10-12-contracts.md) — approved.

## Final Architecture Summary

```text
Angular POS/Dashboard
        |
API Gateway + Cognito JWT validation
        |
Catalog Service ── current Identity authorization decision
        |
Catalog PostgreSQL
  categories/items/variants
  modifier groups/options/links
  tenant catalog revision
  idempotency + transactional outbox
        |
EventBridge → SQS/DLQ → Recommendation / Reporting / Audit / cache consumers

Order ── private Catalog resolver → immutable Order snapshots
Order/Saga ── Inventory availability and reservation boundary
```

Authoritative decisions:

- Catalog is the source of truth for menu structure, base price and menu enablement.
- Owner and Manager can mutate the shared tenant menu. A Manager needs any active Manager membership in the tenant; the write is audited as tenant-wide.
- Cashier and Kitchen are read-only and require a validated active branch membership for reads.
- P0 category hierarchy is flat, each item has one primary category, and price is owned by a concrete variant.
- An active item must retain at least one active variant and exactly one active default variant.
- Modifier groups are reusable; item-group links carry optional min/max/order overrides.
- Catalog sellability and Inventory stock availability are separate. Order makes the final fulfillment decision.
- Every mutation increments the tenant catalog revision and writes its outbox event in the same transaction.
- Order calls the private resolver for a coherent current selection and stores immutable name/price/variant/modifier snapshots.

## Design Review Report

**Scope:** Five Catalog design artifacts and their direct parent contracts.

**Review mode:** Design coherence review using the code-reviewer checklist adapted for documents; no implementation code exists in the repository.

### Critical Issues

None found.

### Warnings

| # | File:Line | Category | Issue | Recommendation |
|---|---|---|---|---|
| 1 | `docs/designs/catalog/2026-10-12-contracts.md:631` | Convention / Integration | Parent contracts still contain the old Cashier availability grant, flat category field, flat modifier relation and non-null branch event assumption. | Apply the listed parent contract amendments before implementing the first cross-service slice. |
| 2 | `docs/designs/catalog/2026-10-12-contracts.md:16` | Delivery | OpenAPI/AsyncAPI and generated client artifacts are specified but not yet materialized. | Generate them from the approved contract before writing controllers or Angular DTOs. |
| 3 | `docs/designs/catalog/2026-10-12-contracts.md:25` | Security / Integration | Catalog deliberately does not add `X-Active-Tenant-Id`; the gateway and Identity context flow must reliably establish the selected tenant when a user has multiple memberships. | Add an integration contract test for missing/ambiguous tenant context and fail closed. |
| 4 | `docs/designs/catalog/2026-10-12-architecture.md:172` | Boundary | Catalog does not check stock, while Order must combine Catalog sellability with Inventory availability. | Implement the Order–Catalog–Inventory sequence as a cross-service acceptance test before checkout is considered complete. |

These warnings do not require redesigning Catalog. They are release gates for integration and contract alignment.

### Checklist Summary

| Category | Verdict | Notes |
|---|---|---|
| Security | PASS WITH WARNING | Tenant predicates, Identity online authorization, branch validation and fail-closed behavior are defined; shared parent auth matrix needs amendment. |
| Business Logic | PASS | Lifecycle, default variant, modifier selection, version conflict and Order snapshot rules are explicit. |
| Performance | PASS | Pagination, allowlisted filters, ETag, revision-based invalidation and bounded resolver inputs are defined. |
| Clean Design | PASS | Service boundaries, data ownership, P0/P1 separation and event ownership are clear. |
| Conventions | PASS WITH WARNING | REST/OpenAPI, standard envelopes and event versioning are aligned; parent envelope/schema updates remain. |

## Traceability Matrix

| Requirement area | Architecture coverage | Contract coverage | Acceptance evidence |
|---|---|---|---|
| Category/item lifecycle | Normalized tables and state transaction | Category/item endpoints and state transitions | Archive/category and item lifecycle tests |
| Variant price/default | Variant-owned price and invariant | Variant create/update/state/default APIs | Price and last-active/default tests |
| Modifier rules | Reusable group/option/link model | Group, option and attachment APIs; typed selection errors | Min/max, duplicate, inactive and foreign-ID tests |
| RBAC/isolation | Identity adapter plus repository tenant predicates | `catalog:read/manage/resolve`, branch header rules | Cross-tenant, revoked membership and role tests |
| POS read | Sellability policy and tenant revision | ETag, `304`, pagination and sellable view | Cache/auth/read latency tests |
| Order integration | Repeatable-read resolver and snapshot boundary | `/internal/v1/catalog/resolutions` | Coherent price/name/version and legacy variant tests |
| Inventory boundary | Catalog does not own stock | Explicit downstream responsibility | Order + Inventory fulfillment test |
| Events | Outbox and EventBridge/SQS | Versioned envelopes and nullable tenant-wide branch scope | Schema, retry, DLQ and dedup tests |
| P1 extensions | Feature-gated tables/modules | Combo, branch price and tag contracts | P1 isolation/fallback tests |

## Pre-Coding Gates

These are the required first tasks before feature implementation:

1. Amend the parent POS contracts for Cashier Catalog writes, category entity shape, modifier groups, nullable tenant-wide event `branchId`, and concrete Order `variantId` resolution.
2. Register `catalog:read`, `catalog:manage` and the workload authorization path in the Identity permission mapping without copying Identity membership state into Catalog.
3. Produce `openapi.yaml`, `asyncapi.yaml` and JSON Schemas from the approved contract; validate examples and error envelopes in CI.
4. Define the gateway/Identity selected-tenant context behavior and add a negative test for ambiguous or missing tenant selection.
5. Agree with Order and Inventory teams on resolver error mapping and the final stock/fulfillment call boundary.

## Implementation Roadmap

### Phase 0 — Contract alignment and scaffolding

**Goal:** Make shared contracts executable before domain code.

- Apply the parent contract amendments listed above.
- Generate OpenAPI/AsyncAPI artifacts and typed Angular client models.
- Create Java 21/Spring Boot 3.x service skeleton, Docker Compose profile and configuration conventions.
- Add correlation ID, tenant context, standard envelope/error mapper and workload mTLS adapter.

**Exit gate:** Contract validation passes; service rejects missing/ambiguous authorization context; no controller owns an untyped request shape.

### Phase 1 — Persistence and security foundation

**Goal:** Establish safe service-owned state.

- Add Flyway migrations for categories, items, variants, modifier groups/options/links, tenant revision, idempotency and outbox.
- Add UUID v7 generation, UTC timestamps, integer VND constraints and composite tenant ownership constraints.
- Add Identity authorization adapter and repository-level `tenant_id` predicates.
- Add optimistic versioning, idempotency storage, audit summary and transaction boundary.

**Exit gate:** Cross-tenant and stale-version integration tests pass before business endpoints are enabled.

### Phase 2 — P0 Catalog administration and read model

**Goal:** Deliver category/item/variant CRUD and POS read.

- Implement category and item lifecycle with atomic initial variant creation.
- Implement variant pricing/default transitions and item sellability policy.
- Implement admin offset pagination and allowlisted filters/sorts.
- Implement POS sellable view, ETag/`304`, catalog revision and cache invalidation hook.

**Exit gate:** Owner/Manager writes and Cashier/Kitchen reads satisfy the acceptance matrix; no half-configured active item is observable.

### Phase 3 — P0 Modifier domain

**Goal:** Deliver reusable modifier configuration and selection validation.

- Implement group/options lifecycle and item-group attach/detach.
- Validate effective min/max/order rules and required-group viability.
- Add modifier price deltas and resolver-compatible selection DTOs.
- Publish modifier change events through the outbox.

**Exit gate:** POS and resolver reject duplicate, foreign, inactive and under/over-selected options with typed errors.

### Phase 4 — Order resolver and fulfillment boundary

**Goal:** Integrate the Catalog contract into the Order vertical slice.

- Implement `/internal/v1/catalog/resolutions` with a coherent primary read transaction.
- Resolve legacy null variant only for exactly one active variant; require concrete variant for new clients.
- Return immutable names, prices, selected modifiers, resource versions and `catalogVersion`.
- Add Order snapshot tests and the separate Inventory stock/reservation call.

**Exit gate:** A menu price change affects only subsequent resolutions; existing Order snapshots remain unchanged; stock failure cannot be hidden by Catalog sellability.

### Phase 5 — Events, operations and hardening

**Goal:** Make downstream integration reliable and observable.

- Publish versioned category/item/variant/modifier events through EventBridge/SQS.
- Add consumer deduplication fixtures, retry/DLQ handling and projection freshness metrics.
- Add structured logs and audit records with correlation, tenant, actor and resource IDs; redact secrets/PII.
- Add p95 read/resolver measurements, authorization failure metrics and outbox lag alerts.

**Exit gate:** Event schema, replay, deduplication, DLQ and observability tests pass in local and AWS-like profiles.

### Phase 6 — P1 extensions

**Goal:** Add features without changing P0 semantics.

- Implement fixed-price non-nested combos and required-component sellability.
- Implement current branch variant price override with tenant/branch isolation and fallback.
- Implement description, normalized tags and metadata events for AI cold start.

**Exit gate:** P1 feature flags are off by default, P0 clients remain compatible, and branch price changes cannot affect another branch.

## Suggested Work Breakdown

| ID | Work item | Dependency | Priority |
|---|---|---|---|
| CAT-01 | Parent contract amendments and permission registration | Identity + platform owners | P0 gate |
| CAT-02 | OpenAPI/AsyncAPI schemas and generated client | CAT-01 | P0 gate |
| CAT-03 | Service skeleton, auth context, errors and correlation | CAT-02 | P0 |
| CAT-04 | Flyway schema, constraints, indexes and seed data | CAT-03 | P0 |
| CAT-05 | Category/item/variant administration | CAT-04 | P0 |
| CAT-06 | POS read, ETag and tenant revision | CAT-05 | P0 |
| CAT-07 | Modifier groups/options and item links | CAT-04, CAT-05 | P0 |
| CAT-08 | Transactional outbox and event publisher | CAT-04 | P0 |
| CAT-09 | Order resolver and snapshot integration | CAT-05, CAT-07 | P0 |
| CAT-10 | Inventory fulfillment boundary and end-to-end acceptance | CAT-09 + Inventory | P0 |
| CAT-11 | Security, concurrency, idempotency and contract test suite | CAT-03 through CAT-10 | P0 |
| CAT-12 | Combo, branch pricing, tags and AI metadata projection | CAT-11 | P1 |

## Definition of Done for P0

- All P0 requirements and acceptance tests in [Requirements](./2026-10-12-requirements.md) pass.
- OpenAPI/AsyncAPI validation is part of CI and generated client types are used by consumers.
- Every public read/write and internal resolver path has an authorization and tenant-isolation test.
- Active-item/default-variant/modifier invariants are enforced in service logic and database constraints where possible.
- Mutations, idempotency records and outbox messages are atomic.
- Order snapshots catalog data; Catalog never writes Order or Inventory state.
- Events are versioned, retryable, deduplicated and observable.
- Docker Compose local flow and the AWS target flow use the same contract semantics.
- No P1 feature is required for P0 acceptance and no P0 client depends on a P1 field.

## Explicit Non-Goals for Implementation

- Do not add branch-specific availability to the P0 schema.
- Do not make Cashier or Kitchen Catalog writers.
- Do not store recipes, stock, reservations, VAT, discounts or payment state in Catalog.
- Do not let Order or Inventory read the Catalog database directly.
- Do not hard-delete records referenced by historical Order or Inventory data.
- Do not implement AI ranking/recommendation logic inside Catalog.

## Final Approval Record

- **Status:** APPROVED WITH WARNINGS
- **Design phase:** Finalization complete
- **Critical issues:** 0
- **Warnings:** 4, tracked above as pre-coding/integration gates
- **Implementation readiness:** Ready after Pre-Coding Gates 1–5
- **Next activity:** Execute the roadmap, starting with CAT-01 and CAT-02

## Domain Skills Referenced

- **code-reviewer:** performed the final design coherence review using security, business-logic, performance and convention checklists; no implementation code was present.
- **api-design:** validated REST/OpenAPI, error, pagination, ETag, auth, idempotency and integration contracts.
- **database-design:** validated normalized PostgreSQL ownership, constraints, indexes, versions, migrations and integer money.
- **log-processing:** validated correlation, audit, outbox and sensitive-data redaction requirements.
- **sequence-diagram:** applicability reviewed; runtime sequence diagrams remain an implementation follow-up because no code path exists yet.
