Started at: 2026/10/12 15:24:00
Finished at: 2026/10/12 15:24:00
Total time: 0 minutes
---

# Design Result: Identity & Access

## Verdict: APPROVED WITH WARNINGS

The `identity-access` service design is coherent, implementable within the parent POS platform constraints, and ready to enter implementation after the pre-implementation actions below are acknowledged. No critical security blocker or cross-tenant access path was found in the design review.

## Artifacts

| Phase | Artifact | Path | Status |
|---|---|---|---|
| Vision | Vision | `docs/designs/identity-access/2026-10-12-vision.md` | Approved |
| Vision review | Spec Sentinel Report | `docs/designs/identity-access/2026-10-12-vision-review.md` | Warnings accepted |
| Requirements | Requirements | `docs/designs/identity-access/2026-10-12-requirements.md` | Approved |
| Architecture | Architecture | `docs/designs/identity-access/2026-10-12-architecture.md` | Approved |
| Contracts | API & Data Contracts | `docs/designs/identity-access/2026-10-12-contracts.md` | Approved |
| Finalization | Result | `docs/designs/identity-access/2026-10-12-identity-access_result.md` | Approved with warnings |

## Design Review Report

**Scope:** Five design artifacts in `docs/designs/identity-access/`; no implementation source exists yet.  
**Date:** 2026-10-12  
**Verdict:** YELLOW — Proceed with Caution

### Summary

The documents consistently separate Cognito authentication from Identity DB authorization, define tenant/branch isolation, cover P0 employee/branch management and keep P1 PIN bounded. The remaining warnings are integration and operational hardening items, not reasons to redesign the bounded context.

### Critical Issues

None found.

### Warnings

| # | Category | Finding | Recommendation |
|---|---|---|---|
| 1 | Parent contract alignment | Parent contracts describe JWT fields such as `tenant_memberships`, `roles` and `branch_ids` as required, while this module correctly treats DB membership state as authoritative. | Before implementation, update the shared contract wording so JWT fields are explicitly hints/UI context and never authorization authority; retain online Identity DB resolution. |
| 2 | Bootstrap authentication | `POST /api/v1/tenants` is under the public API path but is restricted to a platform bootstrap principal/workload. | Pick one concrete deployment contract before coding: platform-admin Cognito JWT with `platform:tenant:create`, or move bootstrap to a private `/internal/v1` route. Add the chosen authorizer to OpenAPI. |
| 3 | Online authorization dependency | Per-request Identity DB authorization is the correct security choice for immediate revoke, but makes Identity Access a latency and availability dependency for all protected services. | Load-test the resolver, size connection pools, add bounded timeouts/circuit breakers and alerting; never fall back to stale client claims when unavailable. |
| 4 | P1 device proof | The contract names `deviceProof` but does not yet select browser key storage, rotation, challenge signing or recovery behavior. | Finalize device enrollment/proof lifecycle before enabling the P1 feature flag; keep this out of P0 acceptance. |
| 5 | Contract artifact format | API/event contracts are complete in Markdown but not yet machine-validatable OpenAPI/AsyncAPI files. | Generate versioned OpenAPI 3.1 and AsyncAPI/JSON Schema artifacts before implementation and validate them in CI. |

### Checklist Summary

| Category | Verdict | Issues |
|---|---|---:|
| Security | PASS with hardening actions | 0 critical, 2 warnings |
| Business Logic | PASS | 0 |
| Performance | WARN | 1 online-authorization capacity risk |
| Data Integrity | PASS with migration actions | 0 critical, 1 migration/tool choice action |
| Contracts | WARN | 2 alignment/machine-artifact actions |
| Observability | PASS | 0 |
| Scope | PASS | P1 PIN is bounded and feature-flagged |

## Traceability Review

| Requirement group | Architecture component | Contract coverage | Verification |
|---|---|---|---|
| Cognito login and invitation | Cognito adapter, JWT validator, invitation worker/reconciliation | `/me`, membership invitation, delivery retry, Cognito error codes | Cognito adapter/integration/E2E tests |
| RBAC roles | Authorization Context Resolver, Membership module | Permission catalog, role/branch validation, internal decision API | Role matrix and escalation-negative tests |
| Tenant isolation | Explicit scope object, service/repository predicates, DB constraints | Tenant path, tenant-scoped endpoints, `403/404` behavior | Cross-tenant BOLA/IDOR tests |
| Branch isolation | Branch module, membership branch scope, online resolver | `X-Active-Branch-Id`, branch APIs, branch-scoped internal decision | Cross-branch read/write tests |
| Branch management | Tenant/Branch module, PostgreSQL `branches` | Branch CRUD/archive endpoints | Owner/manager authorization and concurrency tests |
| Employee management | Membership/Profile modules, invitation deliveries | Employee/membership endpoints and lifecycle states | Invitation/idempotency/state-machine tests |
| Immediate revoke | Primary DB authorization lookup | Membership suspend/revoke/reactivate APIs | Old-JWT-after-revoke test |
| Audit | Outbox + Audit Event Publisher + Audit Service | 16 event families, redacted envelope | Audit completeness and secret-leak tests |
| P1 PIN | Device/PIN module, opaque switch session | Device, PIN, quick-switch endpoints and schemas | Rate-limit, lockout, expiry and scope-narrowing tests |
| Reliability | Idempotency records, optimistic version, outbox, DLQ | Headers, error codes, event delivery contract | Replay/concurrency/failure-injection tests |

## Architecture Summary

- **Deployable service:** 1 `identity-access` Spring Boot service with modular internal boundaries.
- **Core components:** API adapters, JWT validator, authorization resolver, tenant/branch module, membership/invitation module, Cognito adapter, P1 device/PIN module, outbox publisher, audit publisher, PostgreSQL and observability.
- **Databases:** 1 service-owned PostgreSQL/Aurora PostgreSQL database; Audit Service owns the long-term audit query store.
- **Public APIs:** 23 documented REST endpoints.
- **Internal APIs:** 3 documented workload-only endpoints.
- **Event contracts:** 16 versioned Identity event families using the parent envelope.
- **Core tables:** 10 conceptual tables, with P1 device/PIN/session tables feature-flagged.
- **ID strategy:** Application-generated UUID v7.
- **Primary authorization path:** Cognito JWT verification → online Identity DB membership decision → downstream service-owned data query.

## Implementation Roadmap

### Phase 1 — Service Foundation and Security Boundary

- **Goal:** Create a runnable Spring Boot service with secure request boundaries and database migrations.
- **Tasks:**
  - Create Java 21/Spring Boot service structure and module packages.
  - Configure Cognito resource-server JWT/JWK validation and platform/workload authorizers.
  - Add correlation/request IDs, standard success/error envelopes and security redaction.
  - Add PostgreSQL connection, Flyway migrations V1–V3, Testcontainers and seed profile.
  - Implement repository scope objects and deny-by-default authorization primitives.
- **Estimated effort:** 3–4 days.
- **Dependencies:** Parent stack and chosen bootstrap authorizer.

### Phase 2 — Tenant, Branch and Membership P0

- **Goal:** Deliver branch/employee lifecycle and complete RBAC behavior.
- **Tasks:**
  - Implement tenant bootstrap and owner invariant.
  - Implement branch create/read/update/archive with optimistic versioning.
  - Implement users/memberships, role/branch assignment, manager limits and lifecycle transitions.
  - Implement `/me` and online `/internal/v1/authorization-decisions`.
  - Implement ownership checks at controller, application service and repository boundaries.
- **Estimated effort:** 5–7 days.
- **Dependencies:** Phase 1, Cognito configuration and resolved parent JWT contract wording.

### Phase 3 — Invitation, Audit and Event Integration

- **Goal:** Make onboarding and authorization changes durable, observable and consumable by the platform.
- **Tasks:**
  - Implement Cognito invitation adapter and `invitation_deliveries` worker/reconciliation.
  - Implement Flyway V4–V5, idempotency records and transactional outbox.
  - Publish membership/branch/authorization events to EventBridge/SQS.
  - Integrate Audit Service consumer and redacted security event flow.
  - Generate OpenAPI/AsyncAPI/JSON Schema artifacts and contract-test them in CI.
- **Estimated effort:** 4–6 days.
- **Dependencies:** Phase 2, AWS/local broker adapter and Audit Service contract.

### Phase 4 — Downstream Authorization Integration and Security Verification

- **Goal:** Prove that other services cannot bypass Identity authorization.
- **Tasks:**
  - Add shared client/adapter for internal authorization decisions.
  - Integrate one vertical slice first (recommended: Shift or Order) using tenant/branch context.
  - Add cross-tenant, cross-branch, IDOR, role-escalation and revoked-JWT tests.
  - Add load profile for `/me` and authorization resolver; tune pools/timeouts/circuit breakers.
  - Add dependency outage/fail-closed and outbox/DLQ replay tests.
- **Estimated effort:** 3–5 days.
- **Dependencies:** Phase 3 and one downstream service ready for integration.

### Phase 5 — P1 POS PIN Quick Switch

- **Goal:** Add fast cashier switching without creating a second authentication system.
- **Tasks:**
  - Finalize device proof/enrollment/recovery decision.
  - Implement feature-flagged Flyway V6 and device/PIN/session modules.
  - Implement Argon2id/bcrypt hashing, rate limits, lockout, rotation and revocation.
  - Implement opaque short-lived switch context and downstream validation.
  - Add abuse, expiry, branch-binding and permission-narrowing tests.
- **Estimated effort:** 3–5 days.
- **Dependencies:** P0 complete; device proof decision; POS UI support.

### Phase 6 — Operational Hardening

- **Goal:** Make the service demo-ready and safe to operate.
- **Tasks:**
  - ECS/Fargate deployment, Secrets Manager, health/readiness checks and autoscaling.
  - CloudWatch/OpenTelemetry dashboards and alerts.
  - Run migration rehearsal, backup/restore and DLQ replay procedure.
  - Run k6, chaos/restart and Cognito/email failure scenarios.
  - Document runbooks for invite failure, Cognito drift, last-owner recovery and auth outage.
- **Estimated effort:** 3–5 days.
- **Dependencies:** Phases 1–4; Phase 5 for P1 operational coverage.

## Sequence Diagrams

- **Status:** Skipped/deferred.
- **Reason:** No implementation source exists, and the local sequence-diagram workflow requires tracing actual code paths rather than inventing them.
- **Design-level flows available:** invitation/activation, login + `/me` + active branch, membership revoke, and P1 PIN quick switch in the Architecture artifact.
- **Follow-up:** Generate implementation-trace sequence diagrams after Phase 2 or Phase 3 source exists, then update this result document if flows materially diverge.

## Pre-Implementation Actions

These actions do not reopen the approved bounded-context design, but must be completed before production code is merged:

1. Resolve the bootstrap authorizer and update the parent JWT claim wording.
2. Generate machine-readable OpenAPI 3.1 and AsyncAPI/JSON Schema files from the approved Markdown contracts.
3. Select Flyway as the migration tool in the service scaffold and enforce column remarks in CI/review.
4. Load-test the online authorization resolver and document timeout/circuit-breaker/connection-pool values.
5. Decide the P1 device proof lifecycle before enabling P1; do not let it delay P0.

## Next Steps

- [ ] Complete the five pre-implementation actions.
- [ ] Start implementation Phase 1 through the project implementation workflow.
- [ ] Create CI checks for OpenAPI/AsyncAPI compatibility, migration validation and security tests.
- [ ] Integrate the first downstream service with the internal authorization decision contract.
- [ ] Re-run design review after the first vertical slice if implementation reveals contract drift.

## Final Checklist

- [x] Vision, requirements, architecture and contracts are linked and approved.
- [x] P0/P1 boundary is explicit.
- [x] RBAC and tenant/branch isolation are represented in requirements, architecture and contracts.
- [x] Cognito authentication is separated from Identity DB authorization.
- [x] Immediate revoke, idempotency, optimistic concurrency and audit flows are covered.
- [x] Database schema, column remarks, indexes, migrations and partitioning decision are documented.
- [x] API, event and internal authorization contracts are documented.
- [x] NFRs have corresponding architectural decisions and verification plans.
- [x] Implementation roadmap is actionable.
- [ ] OpenAPI/AsyncAPI machine artifacts and source-code verification are still pending implementation phase.

## Final Status

**Design complete — APPROVED WITH WARNINGS.** The module is ready for implementation after the listed pre-implementation actions are handled. No code was created in this design workflow.

