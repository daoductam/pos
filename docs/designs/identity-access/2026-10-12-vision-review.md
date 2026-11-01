Started at: 2026/10/12 14:55:00
Finished at: 2026/10/12 14:55:00
Total time: 0 minutes
---

# Spec Sentinel Report

**Spec:** `docs/designs/identity-access/2026-10-12-vision.md`  
**Date:** 2026-10-12  
**Status:** ⚠️ Warnings (Proceed with Caution)

## Review Context

- Phase 0 skipped — reusing context from the approved parent POS design and the current Vision discussion.
- Repository currently contains design inputs only (`idea.md` and design documents); no implementation source, migrations, handlers, or existing consumers were found to trace.
- Parent constraints reviewed: [POS Platform Architecture](../pos-platform/2026-10-12-architecture.md), [POS Platform Requirements](../pos-platform/2026-10-12-requirements.md), and [POS Platform Contracts](../pos-platform/2026-10-12-contracts.md).

## Findings

### Warnings

| # | Category | Finding | Risk | Recommendation |
|---|---|---|---|---|
| 1 | Authorization policy | Vision states that owner/manager can invite and manage employees, but does not define the exact manager limits: which roles can be invited, whether a manager can reassign another manager, and whether a manager can suspend/revoke access. | Different services or UI clients may implement privilege escalation paths inconsistently. | Define a complete command-level RBAC matrix in Requirements. Default recommendation: manager may manage only `CASHIER`/`KITCHEN` memberships in assigned branches; only owner may manage owner/manager memberships and tenant-wide changes. |
| 2 | Lifecycle | Cognito invitation and Identity membership states are named but their complete state machine and reconciliation behavior are not specified. | A user may remain `INVITED`, become active in Cognito but not Identity DB, or be re-invited into a duplicate membership. | Specify membership states, allowed transitions, idempotency key, unique constraints, retry behavior, and a reconciliation/remediation path. |
| 3 | Revocation semantics | Vision requires revoke to apply on the next request but does not define which endpoints require a fresh DB authorization check versus cached context. | A revoked user could retain access through a stale authorization cache or an already-issued JWT. | Require server-side membership lookup/version validation for every protected request, or define a bounded cache TTL plus immediate invalidation on membership mutation. Never rely on Cognito role claims alone. |
| 4 | PIN security | PIN is scoped to branch/device, but device registration, trust establishment, PIN reset/rotation, lockout thresholds, and lost-device revocation are not defined. | A guessed or copied PIN could impersonate a cashier on a shared POS, or an operator may be unable to recover a locked device safely. | Define a device identity/registration model, secure enrollment by an authenticated user, Argon2id/bcrypt hash policy, rate limits, lockout, reset/revoke flow, and audit events. |
| 5 | API boundary | Vision references `/me` and `X-Active-Branch-Id`, but does not yet define request/response fields, error cases, pagination, optimistic concurrency, or whether tenant selection is represented in the same header/context. | Consumers may infer incompatible contracts, and branch/tenant isolation may be enforced only in the UI. | Capture public and internal API contracts in the Contracts phase; Requirements must list validation and negative authorization cases first. |
| 6 | Audit/PII | Audit is required, but the retention, actor representation, redaction rules, and event delivery guarantee are not yet specified. | Audit records may leak email/token/PIN data or fail to support security investigation. | Define an append-only audit schema, redaction rules, retention/owner, correlation/causation IDs, and outbox/event delivery requirements. |
| 7 | Scope coupling | P1 PIN is in the same service but introduces POS-device lifecycle and security concerns separate from Cognito onboarding. | PIN design may delay the P0 identity slice or create a second authentication mechanism accidentally. | Keep PIN behind a P1 boundary and feature flag; make P0 acceptance independent. Do not issue a Cognito-equivalent token from PIN quick switch. |

## Cross-Impact Analysis

| Dimension | Finding | Risk Level | Recommendation |
|---|---|---|---|
| **Shared Logic** | No implementation symbols or call sites exist yet. Planned shared authorization context will be consumed by API Gateway and Order, Catalog, Payment, Inventory, Loyalty, Shift, Reporting and Audit services. | 🟡 Medium | Define one versioned authorization context contract and prohibit downstream services from reading Identity DB directly. |
| **Data/State** | New Identity DB ownership for tenants, branches, memberships, Cognito subject mapping, invitation lifecycle, PIN/device records and audit/outbox metadata. | 🟡 Medium | Use explicit tenant/branch constraints, unique membership keys, optimistic versioning, migrations, and transactional outbox where events are emitted. |
| **API Contracts** | Parent contract has `/me`, tenant/branch and membership endpoints; module will add employee lifecycle, branch updates, authorization context and PIN endpoints. | 🟡 Medium | Preserve `/api/v1` conventions, standard error envelope, `Idempotency-Key` for mutations, `X-Correlation-Id`, and explicit `401/403/404/409/422/429` behavior. |
| **Flow Integrity** | Main flows are Cognito invitation → completion → membership activation; login → `/me` → branch selection; admin mutation → authorization/audit event; PIN quick switch → authenticated POS context. | 🟡 Medium | Requirements must cover dependency failures, duplicate/retry behavior, concurrent role changes, revoked sessions, and invalid active-branch selection. |
| **Cross-Cutting** | Security-sensitive inputs include JWTs, email/PII, branch IDs, role changes and PIN attempts. | 🔴 High | Apply tenant/ownership checks at controller/service/repository boundaries; never log tokens, raw PINs or secrets; add negative authorization, rate-limit and audit tests. |

## Instruction Compliance

| Dimension | Verdict | Deviation (if any) | Recommendation |
|---|---|---|---|
| **Conventions** | ✅ | — | Continue using the parent REST/OpenAPI, UUID v7, UTC timestamps, standard error envelope and `docs/designs/identity-access/` artifact conventions. |
| **Architecture** | ✅ | — | Vision respects service ownership, Cognito authentication, Identity DB authorization, no cross-service database reads, and parent tenant/branch isolation rules. |
| **Testing** | ⚠️ | Vision lists measurable outcomes but not yet the concrete test matrix. | Requirements must include unit, integration, contract, end-to-end and security tests for RBAC, BOLA/IDOR, tenant/branch isolation, revoke freshness, invitation idempotency and PIN abuse controls. |

## Decision

The Vision is coherent and aligned with the parent POS architecture. No blocking contradiction or unsafe cross-tenant access path is present at this stage, but the warnings must become explicit requirements before implementation planning.

**Recommended next step:** proceed to Requirements Discovery and resolve the warnings in the requirements artifact. No implementation planning or code changes are authorized by this review.

