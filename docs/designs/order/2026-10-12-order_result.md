Started at: 2026/10/12 16:35:00 +07
Finished at: 2026/10/12 16:35:00 +07
Total time: 0 minutes
---

# Design Result: Order Service

## Final Verdict

**APPROVED WITH WARNINGS — ready for implementation after the pre-coding gates below.**

The five design phases are complete. The Order service boundaries, state machine, Catalog snapshot policy, Step Functions Saga, compensation model, payment reconciliation path and tenant/branch isolation are coherent. No critical security or state-integrity blocker was found in the documents. The remaining warnings are integration/release gates, especially the parent contract amendments and executable Saga/payment/inventory fixtures.

## Design Scope

| Item | Result |
|---|---|
| Module | Module 3 — Bán hàng / Order service |
| Workflow | Standard Service Flow, scope M |
| P0 | Counter/takeaway/table orders, Catalog snapshots, draft edits, Manager/Owner line cancellation, cash/VNPAY checkout Saga, compensation, events and audit integration |
| P1 | Table map, hold/resume, split/merge invoice, KDS WebSocket, post-payment refund/reversal compatibility |
| P2 | Customer self-order by table QR |
| Persistence | Service-owned normalized PostgreSQL database |
| Orchestration | AWS Step Functions or semantics-compatible local adapter |
| Stack target | Java 21, Spring Boot 3.x, REST/OpenAPI 3.1, AsyncAPI/JSON Schema, UUID v7, integer VND |
| Final status | Approved with warnings |

## Artifacts

1. [Vision](./2026-10-12-vision.md) — approved.
2. [Vision review](./2026-10-12-vision-review.md) — warnings accepted and resolved into requirements.
3. [Requirements](./2026-10-12-requirements.md) — approved.
4. [Architecture](./2026-10-12-architecture.md) — approved.
5. [API & Data Contracts](./2026-10-12-contracts.md) — approved.
6. This [Design Result](./2026-10-12-order_result.md) — approved with warnings.

## Final Architecture Summary

```text
POS → API Gateway → Order Service → Order PostgreSQL
                         │             ├─ immutable Catalog snapshots
                         │             ├─ checkout attempts
                         │             ├─ idempotency/version state
                         │             └─ transactional outbox
                         │
                         ├─ Identity authorization decision
                         ├─ Catalog resolver
                         └─ Step Functions Checkout Saga
                              ├─ Inventory reserve/commit/release
                              ├─ Payment cash/VNPAY/pending/IPN
                              └─ Order finalize/fail/reconcile

Order events → EventBridge → SQS/DLQ → Loyalty / Reporting / Audit / KDS(P1)
```

Authoritative decisions:

- Order owns lifecycle, line state, immutable Catalog snapshots, totals snapshot and checkout attempts.
- `DRAFT` is editable by authorized Cashier/Manager/Owner in branch context; optimistic concurrency prevents silent overwrite.
- Owner/Manager can cancel an active line only in `DRAFT`; Cashier cannot cancel. The last active-line cancellation atomically cancels the Order.
- Checkout re-resolves Catalog; stale menu/price/configuration returns `catalog_changed` and requires explicit reprice confirmation.
- Step Functions orchestrates `reserve → payment → commit → finalize`, with idempotent tasks, timeout, retry and compensation.
- VNPAY pending holds reservation for 15 minutes; late success after release never resurrects the Order.
- Payment success with failed Inventory commit becomes a reconciliation exception, never a silent `PAID` transition.
- `OrderPaid` is the canonical paid-order fact; PaymentPaid is not consumed directly for Loyalty points or Reporting revenue.
- Order events/audit records use transactional outbox; consumers are eventually consistent and deduplicate by event ID.

## Design Review Report

**Scope:** Five Order design artifacts plus direct parent Identity, Catalog and POS Platform contracts.

**Review mode:** Document coherence review using the code-reviewer checklist; no Order implementation code exists in the repository.

### Critical Issues

None found.

### Warnings

| # | File:Line | Category | Issue | Recommendation |
|---|---|---|---|---|
| 1 | `docs/designs/order/2026-10-12-contracts.md:562` | Integration | Parent contracts still need the concrete variant, expanded checkout response, Inventory commit, cancellation constraints and Order event routing amendments. | Apply and contract-test the listed amendments before the first cross-service vertical slice. |
| 2 | `docs/designs/order/2026-10-12-contracts.md:595` | Delivery | OpenAPI, AsyncAPI, JSON Schemas and the Step Functions definition are described but not yet materialized. | Generate/validate them in CI before implementing controllers, Angular DTOs or Saga tasks. |
| 3 | `docs/designs/order/2026-10-12-requirements.md:116` | Financial / Failure | Payment can be successful while Inventory commit fails; P0 has no automatic refund feature. | Implement a reconciliation runbook and failure fixture; block new checkout and never silently mark `PAID`. Confirm Payment's operator/refund path before demo. |
| 4 | `docs/designs/order/2026-10-12-requirements.md:102` | Business policy | P0 defaults VAT to zero and leaves nonzero VAT behind a future tenant policy contract. | Obtain business/tax confirmation before enabling nonzero VAT; keep deterministic integer rounding tests ready. |
| 5 | `docs/designs/order/2026-10-12-architecture.md:444` | Traceability | Runtime L1/L2 sequence diagrams are deferred because no controllers/adapters/listeners exist yet. | Generate and confirm PlantUML traces immediately after the service skeleton and Saga contracts are running. |

These warnings do not require a redesign. They define the minimum safe path from approved documents to executable integration.

### Checklist Summary

| Category | Verdict | Issues |
|---|---|---:|
| Security | PASS WITH WARNING | 1 |
| Business Logic | PASS WITH WARNING | 2 |
| Performance | PASS | 0 |
| Clean Design | PASS | 0 |
| Conventions | PASS WITH WARNING | 2 |

Security warning is limited to applying the already documented parent authorization/event amendments. Business warnings cover payment/Inventory reconciliation and VAT policy confirmation; both are explicit and testable.

## Traceability Matrix

| Requirement area | Architecture owner | Contract evidence | Acceptance evidence |
|---|---|---|---|
| Counter/takeaway/table order | Draft and line module | CreateOrderRequest, service mode and table reference | Service-mode creation tests |
| Draft edit/concurrency | Order DB/version module | PATCH/add line, `If-Match`, `order_version_conflict` | Concurrent POS update tests |
| Line cancellation/audit | Line lifecycle + outbox | Cancel endpoint, reason, audit event | RBAC/last-line/audit tests |
| Catalog snapshot | Catalog adapter | Resolver selection, line snapshot, reprice/catalog_changed | Price/config drift tests |
| Money/totals | Totals validator | MoneyTotals, integer VND, pricing policy version | Cash/change/rounding tests |
| Checkout Saga | Step Functions + attempt module | CheckoutResponse, internal finalize/fail/compensation APIs | Happy/failure/retry tests |
| Inventory reservation | Inventory task boundary | Reserve/commit/release references | Insufficient stock/duplicate command tests |
| Payment/IPN | Payment task/reconciliation boundary | Pending/paid/late-payment callbacks | Duplicate/out-of-order/late IPN tests |
| Canonical paid fact | Order outbox | `OrderPaid.v1` schema | Loyalty/Reporting replay/dedup tests |
| Tenant/branch isolation | Identity adapter + repositories | Permission/scope headers and internal context | IDOR/revoked membership tests |
| Observability | Outbox/Saga telemetry | Correlation/saga/attempt fields and metrics | Trace/redaction/DLQ evidence |
| P1/P2 compatibility | Feature-gated adapters | Hold/table/split/KDS/QR reserved paths | P0 regression plus later feature tests |

## Pre-Coding Gates

1. Apply parent contract amendments: concrete `variantId`, modifier option naming, expanded checkout response, Inventory commit result, cancellation constraints and canonical event routing.
2. Register `order:read`, `order:create`, `order:edit`, `order:checkout`, `order:cancel-line` and `order:resolve` with Identity without copying membership state into Order.
3. Generate `openapi.yaml`, `asyncapi.yaml`, JSON Schemas and typed Angular client models.
4. Materialize the Step Functions state machine with deterministic task idempotency keys, timeout/retry/catch/compensation and callback correlation.
5. Prove Catalog → Order snapshot → Inventory reserve/commit/release → Payment pending/paid/late behavior using contract fixtures.
6. Confirm the payment-success/Inventory-commit-failure reconciliation runbook and P0 VAT policy before enabling the demo checkout path.
7. Keep runtime PlantUML sequence diagrams as a gate after controllers, task adapters and event listeners are traceable.

## Implementation Roadmap

### Phase 0 — Contract alignment and failure spikes

**Goal:** Make the cross-service semantics executable before domain implementation.

- Apply parent contract amendments and register Identity permissions.
- Generate/validate OpenAPI/AsyncAPI/JSON Schema and typed clients.
- Build minimal Step Functions/local orchestration adapter with one successful and one compensated execution.
- Create Payment/Inventory fixtures for duplicate, timeout, out-of-order IPN and commit-failure reconciliation.
- Confirm integer total/VAT policy and publish the operator runbook.

**Exit gate:** Contract validation passes and a deterministic Saga demonstrates success, payment failure/release and compensation retry without duplicate side effects.

### Phase 1 — Service foundation and Order persistence

**Goal:** Establish secure, tenant-scoped Order state.

- Create Java 21/Spring Boot Order service, Docker Compose profile and configuration conventions.
- Add Flyway migrations for orders, lines, modifier snapshots, checkout attempts, transitions, idempotency and outbox.
- Implement Identity authorization adapter, `TenantBranchScope`, standard errors, correlation and ETag/version handling.
- Add repository tenant/branch predicates, branch order-number generation and deterministic seed data.

**Exit gate:** Cross-tenant/branch, revoked membership, stale version and idempotency tests pass.

### Phase 2 — Draft and Catalog snapshot vertical slice

**Goal:** Make POS draft creation/editing reliable.

- Implement create/list/read/update order metadata.
- Implement add/update line and Catalog resolver snapshot mapping.
- Implement integer totals and explicit reprice preview/confirmation.
- Implement line cancellation, last-line terminal cancellation and audit outbox event.
- Build Angular cart/draft/error states from generated client types.

**Exit gate:** Counter/takeaway/table drafts, concurrent edits, catalog changes and cancellation RBAC/audit acceptance tests pass.

### Phase 3 — Checkout Saga and P0 payment paths

**Goal:** Complete cash and VNPAY checkout with compensation.

- Implement checkout precondition transaction and attempt creation.
- Implement Step Functions tasks for Catalog validation, Inventory reserve, Payment initiate/confirm, Inventory commit/release and Order finalize/fail.
- Implement cash change and VNPAY pending/expiry/IPN correlation through Payment boundary.
- Implement compensation status/retry lock, DLQ and late-payment reconciliation state.
- Add checkout pending/failed/retry UI and recovery actions.

**Exit gate:** Cash and QR success, Payment failure, Inventory shortage, timeout, duplicate IPN, late IPN and Inventory commit failure all have deterministic evidence with no duplicate `OrderPaid`.

### Phase 4 — Events, audit, reporting and reliability

**Goal:** Make downstream consequences observable and replay-safe.

- Publish lifecycle, cancellation, failed-checkout and paid-order events through outbox/EventBridge/SQS.
- Add consumer dedup fixtures and projection freshness metrics for Loyalty/Reporting/Audit.
- Add structured logs, X-Ray/OpenTelemetry traces, dashboard metrics and alerts for Saga/pending/DLQ/reconciliation.
- Run k6 checkout profile, service restart, broker delay, DLQ replay and compensation failure scenarios.

**Exit gate:** P0 event schemas/replay, audit evidence, trace correlation and reliability targets pass.

### Phase 5 — P1/P2 extensions

**Goal:** Expand Order without changing P0 lifecycle/snapshot semantics.

- Add hold/resume and table master/occupancy behind feature flags.
- Add split/merge invoice invariants and audit links.
- Add durable KDS item events and WebSocket reconnect/replay adapter.
- Add post-payment refund/reversal contracts with Payment/Loyalty.
- Add QR self-order sessions with separate customer authorization and anti-tampering.

**Exit gate:** P0 clients remain compatible, feature flags are off by default and every extension preserves Order ownership/idempotency rules.

## Suggested Work Breakdown

| ID | Work item | Dependency | Priority |
|---|---|---|---|
| ORD-01 | Parent contract amendments and Identity permission registration | Platform + Identity owners | P0 gate |
| ORD-02 | OpenAPI/AsyncAPI schemas and generated Angular client | ORD-01 | P0 gate |
| ORD-03 | Step Functions state machine and local Saga adapter spike | ORD-01, ORD-02 | P0 gate |
| ORD-04 | Payment/Inventory failure and reconciliation fixtures | ORD-03 | P0 gate |
| ORD-05 | Order service skeleton, auth context, errors and correlation | ORD-02 | P0 |
| ORD-06 | Flyway schema, snapshots, attempts, constraints and indexes | ORD-05 | P0 |
| ORD-07 | Draft/order/line APIs and Catalog snapshot integration | ORD-06 | P0 |
| ORD-08 | Reprice, cancellation, last-line terminal state and audit outbox | ORD-07 | P0 |
| ORD-09 | Checkout command, cash and VNPAY pending response | ORD-03, ORD-07 | P0 |
| ORD-10 | Inventory reserve/commit/release and Payment task integration | ORD-03, ORD-04, ORD-09 | P0 |
| ORD-11 | OrderPaid/events, consumer contracts, metrics and failure tests | ORD-08, ORD-10 | P0 |
| ORD-12 | P1 table/hold/split/merge/KDS and P2 QR ordering | ORD-11 | P1/P2 |

## Definition of Done for P0

- All P0 requirements and acceptance tests in [Requirements](./2026-10-12-requirements.md) pass.
- Public and internal OpenAPI/AsyncAPI/JSON Schema contracts validate in CI; generated Angular types are used by clients.
- Every public/internal endpoint validates current Identity scope and repeats tenant/branch ownership checks at repository/service layers.
- Order/line snapshots and checkout attempts are immutable where specified; state transitions and `OrderPaid` finalization are idempotent.
- Draft edits use `If-Match`; line cancellation requires Owner/Manager, reason and audit outbox event.
- Cash/VNPAY happy paths, reservation timeout, compensation retry/DLQ, late IPN and Inventory commit failure have automated evidence.
- Order, outbox and idempotency records are atomic; no cross-service database reads or foreign keys exist.
- `OrderPaid` is emitted once and is the only paid-order fact used by Loyalty/Reporting.
- Structured logs/traces/metrics have correlation, tenant, branch, order, attempt and Saga identifiers without secrets/PII leakage.
- Docker Compose local and AWS target semantics match for API, event and Saga contracts.
- P1/P2 features are disabled or absent from P0 runtime paths.

## Explicit Non-Goals for Implementation

- Do not let Order own Catalog menu/price/modifier state, Inventory stock/recipe state, Payment provider state or Loyalty ledger state.
- Do not permit Cashier line cancellation or public edits during `CHECKOUT_PENDING`.
- Do not auto-revive an Order after late payment or silently mark it `PAID` after Inventory commit failure.
- Do not use floating point, client-provided totals, client-provided actor/role, or unbounded retry loops.
- Do not expose raw payment callbacks, signatures, JWTs, secrets or unnecessary customer PII in Order responses/events/logs.
- Do not make P1 table occupancy, split/merge, KDS WebSocket or P2 QR flow a prerequisite for P0 checkout.

## Final Approval Record

- **Status:** APPROVED WITH WARNINGS
- **Design phase:** Finalization complete
- **Critical issues:** 0
- **Warnings:** 5, tracked above as pre-coding/integration gates
- **Implementation readiness:** Ready after Pre-Coding Gates 1–7
- **Next activity:** Execute the roadmap, starting with ORD-01 through ORD-04

## Domain Skills Referenced

- **code-reviewer:** performed final design coherence review against security, business logic, performance and convention checklists; no implementation code existed.
- **api-design:** validated public/internal REST, pagination, errors, auth, idempotency and optimistic concurrency.
- **database-design:** validated Order-owned snapshots, checkout attempts, constraints, indexes, UUID v7, money and migration strategy.
- **log-processing:** validated audit, correlation, outbox, Saga metrics, redaction and DLQ observability.
- **sequence-diagram:** validated conceptual critical flows; runtime L1/L2 diagrams remain an implementation gate.
