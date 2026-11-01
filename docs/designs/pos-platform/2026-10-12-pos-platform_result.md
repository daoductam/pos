Started at: 2026/10/12 14:32:07
Finished at: 2026/10/12 14:32:07
Total time: 0 minutes
---

# Design Result: POS Platform

## Verdict: APPROVED WITH WARNINGS

The design is coherent and sufficiently detailed to begin implementation of the P0 vertical slice. The warnings below are implementation gates/spikes, not blockers for starting the design-approved work.

## Artifacts

| Phase | Artifact | Path | Status |
|---|---|---|---|
| Vision | Vision | [`2026-10-12-vision.md`](./2026-10-12-vision.md) | Approved |
| Vision Review | Sentinel Report | [`2026-10-12-vision-review.md`](./2026-10-12-vision-review.md) | Warnings accepted |
| Requirements | Requirements | [`2026-10-12-requirements.md`](./2026-10-12-requirements.md) | Approved |
| Architecture | Architecture | [`2026-10-12-architecture.md`](./2026-10-12-architecture.md) | Approved |
| Contracts | API/Data Contracts | [`2026-10-12-contracts.md`](./2026-10-12-contracts.md) | Approved |
| Finalization | Design Result | This file | Approved with warnings |

## Design Review Summary

### Completeness

- 22 P0 use cases are mapped to bounded contexts, API surfaces, state ownership and test layers.
- 20 P0 business rules cover tenant isolation, money precision, payment, inventory reservation, loyalty ledger, recommendation filtering, outbox, retry and audit.
- 35 API endpoints, 12 versioned integration events, queue/DLQ policies, core schemas, indexes and Flyway migration conventions are documented.
- Security, observability, failure injection, load and chaos testing are included as explicit requirements.

### Consistency

- `OrderPaid` is the canonical paid-order fact for Loyalty and Reporting.
- `PaymentPaid` is limited to Saga and Audit consumers; Saga directly commands Inventory commit, preventing duplicate stock and downstream payment side effects.
- Database ownership, no cross-service SQL, outbox publishing and event metadata are consistent across Architecture and Contracts.
- The API, event and database contracts carry tenant/branch scope and correlation identifiers.

### Feasibility

- Spring Boot + Angular + PostgreSQL + Docker is a practical base for the demo.
- Hybrid adapters isolate AWS/VNPAY availability from the local core flow.
- The complete system is intentionally large; implementation must follow the vertical-slice roadmap and keep P1/P2 out of the first runtime path.

### Review Method

The finalization review applied the design-review checklist from the local workflow: requirements-to-component traceability, NFR coverage, contract consistency, data-flow completeness, security access paths, observability and implementation feasibility. No application code exists yet, so this is a document review rather than a code review.

## Warnings and Required Spikes

| # | Warning | Risk | Required action | Owner phase |
|---|---|---|---|---|
| W-01 | VNPAY sandbox fields/checksum behavior and IPN retry semantics must be verified against the actual sandbox. | Payment may remain pending or accept an invalid/out-of-order callback. | Build a thin VNPAY adapter spike with recorded sandbox fixtures and duplicate/out-of-order tests. | Foundation |
| W-02 | Step Functions callback/event correlation is not yet validated in a running environment. | Saga may finalize twice or compensate after a late success. | Prove one reserve → payment wait → finalize/compensate execution before broad service work. | Foundation |
| W-03 | Inventory locking strategy is deliberately left open. | Concurrent checkout could oversell or deadlock. | Benchmark optimistic versioning versus row-level/pessimistic locking with the k6 checkout profile. | Core checkout |
| W-04 | NFR targets are design targets, not measured results. | The demo may fail p95/throughput targets after real integrations are added. | Add k6 baseline and publish measured p95/event lag in the implementation closeout. | Reliability |
| W-05 | Local broker/adapters and AWS EventBridge/SQS differ operationally. | Local success may not predict cloud behavior. | Run at least one cloud contract/integration test and keep adapter parity tests. | Integration |
| W-06 | Sequence diagrams are deferred because no code endpoints/topics exist to trace. | Implementation may drift from conceptual flow. | Generate L1/L2 PlantUML diagrams after the first contracts and service skeletons are running. | Contracts/implementation |
| W-07 | Physical database separation in AWS is a deployment optimization, while local demo uses one PostgreSQL container. | Operators may mistake deployment co-location for shared ownership. | Enforce separate credentials/databases locally and document the production topology in IaC. | Infrastructure |

## Architecture Summary

- **Components:** 16 logical components: Angular UI, API Gateway, Cognito, 10 bounded-context/read/audit services, Step Functions, EventBridge/SQS and Observability.
- **Databases:** 10 PostgreSQL-owned stores/read models with strict service ownership.
- **APIs:** 35 documented endpoint contracts across public, internal and callback boundaries.
- **Integration points:** 12 versioned event contracts plus Cognito, VNPAY, Step Functions, EventBridge/SQS, Secrets Manager and CloudWatch/X-Ray.
- **Primary consistency model:** strong consistency inside service transactions; eventual consistency across event projections; Saga compensation for checkout boundaries.

## Implementation Roadmap

### Phase 0 — Foundation and Architecture Spikes

- **Goal:** Make the stack bootable and prove the riskiest external boundaries.
- **Tasks:**
  - Create Java 21/Spring Boot service template, Angular shell and shared contract repository.
  - Add Docker Compose PostgreSQL topology with one database/credential boundary per service.
  - Add Flyway baseline, common correlation/error/idempotency libraries and OpenAPI/AsyncAPI validation.
  - Create local broker and AWS adapter interfaces.
  - Prove Cognito JWT validation, VNPAY fixture/checksum handling and one Step Functions callback execution.
  - Add Terraform skeleton for sandbox resources and basic CI checks.
- **Dependencies:** None.
- **Exit criteria:** All service templates boot; contract tests run; spike demonstrates one successful and one compensated Saga.

### Phase 1 — Identity, Tenant, Catalog and Shift

- **Goal:** Establish secure branch-scoped context and the data needed to sell an item.
- **Tasks:**
  - Implement Cognito resource-server validation and membership/role policy.
  - Implement tenant/branch/employee administration and negative scope tests.
  - Implement catalog item/variant/modifier CRUD, availability events and seed data.
  - Implement shift open/current/close and opening cash.
  - Build Angular login, branch selection, catalog and open-shift screens.
- **Dependencies:** Phase 0.
- **Exit criteria:** Authorized cashier can log in, select an allowed branch, open a shift and load active catalog; cross-tenant/branch access is rejected.

### Phase 2 — Order and Recommendation Vertical Slice

- **Goal:** Create a usable order draft and demonstrate upsell analytics.
- **Tasks:**
  - Implement order draft, line item/modifier, price snapshot and optimistic versioning.
  - Implement association-rule recommendation with availability/cart filtering.
  - Emit `OrderCreated`, `SuggestionShown` and `SuggestionAccepted` through outbox.
  - Build Angular order/cart/recommendation UI and acceptance event handling.
- **Dependencies:** Phase 1.
- **Exit criteria:** Demo user adds the seed food item, receives the seed drink suggestion, accepts it and the two analytics events are queryable.

### Phase 3 — Inventory, Payment and Checkout Saga

- **Goal:** Complete the P0 money/stock flow and failure compensation.
- **Tasks:**
  - Implement recipe/ingredient/reservation/stock ledger schema and concurrency spike result.
  - Implement cash payment and change calculation using integer amounts.
  - Implement VNPAY QR sandbox adapter, IPN verification, payment state machine and reconciliation job.
  - Implement Step Functions checkout orchestration, timeout, retry, compensation and idempotent internal endpoints.
  - Connect `PaymentPaid`, `PaymentFailed`, reservation and `OrderPaid` event flows.
  - Add Angular checkout, QR pending/success/failure and recovery states.
- **Dependencies:** Phase 0, Phase 1 and Phase 2; inventory/payment spike from W-01/W-03.
- **Exit criteria:** Cash and QR happy paths reach `PAID`; stopping Payment releases inventory and produces a complete trace without duplicate side effects.

### Phase 4 — Loyalty, Reporting and Audit

- **Goal:** Make the P0 business outcomes measurable and auditable.
- **Tasks:**
  - Implement customer lookup with protected phone representation.
  - Implement append-only loyalty ledger, balance projection, accrual and reversal contracts.
  - Implement reporting consumers/projections for daily branch revenue and top items.
  - Implement append-only audit event storage/query and security denial events.
  - Add dashboard freshness state, loyalty balance and audit views.
- **Dependencies:** Phase 3 and canonical `OrderPaid` event.
- **Exit criteria:** Paid order adds points once, replay does not duplicate points, dashboard updates within target lag and sensitive actions are auditable.

### Phase 5 — Reliability, Security and Demo Hardening

- **Goal:** Validate the architecture under expected failures and prepare repeatable demonstration.
- **Tasks:**
  - Add unit/integration/contract/E2E/security negative suites.
  - Run k6 checkout/recommendation/reporting profile and record p95/event lag.
  - Run chaos scenarios: service restart, broker delay, duplicate IPN, DLQ replay and compensation failure.
  - Add CloudWatch/X-Ray dashboards and alarms.
  - Build deterministic multi-tenant/multi-branch seed data and demo runbook.
  - Generate L1/L2 sequence diagrams from the running code/contracts.
- **Dependencies:** Phases 1–4.
- **Exit criteria:** All Vision success criteria have a runnable evidence path and the demo can be reset/replayed safely.

## Parallel Workstreams

| Workstream | Can run in parallel after | Primary deliverables |
|---|---|---|
| Platform/Infra | Phase 0 start | Docker, Terraform, CI/CD, observability, broker adapters |
| Contract/Data | Phase 0 start | OpenAPI, AsyncAPI, Flyway schemas, contract tests |
| Backend services | Service-specific contract ready | Spring Boot bounded contexts and consumers |
| Angular UI | Public API contract ready | POS, checkout, dashboard and error/freshness states |
| Reliability/Security | First vertical slice available | k6, chaos, tenant isolation and audit verification |

## Traceability Matrix

| Requirement area | Architecture owner | Contract evidence | Acceptance evidence |
|---|---|---|---|
| RBAC + tenant/branch isolation | Identity, Gateway, every service/repository | JWT/scope conventions and authorization matrix | Cross-tenant/branch negative tests |
| Catalog + availability | Catalog | Catalog endpoints and schema | CRUD/availability integration tests |
| Order + recommendation | Order, Recommendation | Order/recommendation endpoints/events | E2E suggestion shown/accepted |
| Payment + IPN | Payment, VNPAY adapter | Payment/IPN schemas and state enum | Duplicate/out-of-order callback tests |
| Inventory + compensation | Inventory, Step Functions | Reserve/release internal APIs/events | Payment outage releases reservation |
| Loyalty | Loyalty | Customer/ledger schemas and `OrderPaid` consumer | Replay produces one ledger entry |
| Reporting | Reporting read model | Projection schemas and report endpoints | Freshness/lag and totals checks |
| Audit + observability | Audit, Observability | Audit event, correlation/event envelope | Trace/audit evidence for sensitive action |
| Resilience | Outbox, SQS/DLQ, Saga | Retry/queue contract | Restart/chaos/DLQ replay tests |

## Sequence Diagrams

- **Status:** Deferred during design because the repository has no concrete controllers, event listeners or endpoint/topic configuration to trace.
- **Planned paths:** P0 happy checkout, Payment failure compensation, duplicate VNPAY IPN, and `OrderPaid` → Loyalty/Reporting projection.
- **Generation gate:** After Phase 0 contracts/service skeletons exist, trace actual code and generate both L1 and L2 PlantUML diagrams in `.adoc` files.

## Next Steps

1. Run the Phase 0 foundation/spike before implementing the whole service set.
2. Convert each roadmap phase into a scoped feature plan using the feature workflow.
3. Treat W-01, W-02 and W-03 as exit gates for the checkout implementation.
4. Keep P1/P2 outside the MVP runtime path until all P0 acceptance criteria pass.
5. Generate sequence diagrams and update contracts when concrete paths are available.

## Final Checklist

- [x] Vision approved and saved.
- [x] Requirements approved and traceable.
- [x] Architecture approved and consistent with requirements.
- [x] API, data and integration contracts approved.
- [x] All P0 requirements mapped to an owner and acceptance evidence.
- [x] NFRs addressed by architecture and roadmap tasks.
- [x] Tenant isolation and sensitive-data handling covered at API, service, data and event boundaries.
- [x] Saga, outbox, idempotency, retry, DLQ and compensation covered.
- [x] Implementation roadmap is ordered by dependencies and vertical slices.
- [ ] Sequence diagrams generated after code/contracts can be traced.
- [ ] Performance/chaos measurements collected during implementation.
