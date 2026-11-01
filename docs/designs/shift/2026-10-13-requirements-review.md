# Spec Sentinel Report

**Spec:** [`docs/designs/shift/2026-10-13-requirements.md`](./2026-10-13-requirements.md)  
**Date:** 2026-10-13  
**Status:** ⚠️ Warnings (Proceed with Caution)

## Review context

- Reused the approved Shift Vision and its Vision review.
- Applied the `spec-reviewer`, `api-design` and `database-design` checks.
- Cross-checked POS Platform, Order, Payment and Identity contracts.
- No Shift implementation source, generated schemas or executable migrations exist yet.

## Findings

## Warnings

| # | Category | Finding | Risk | Recommendation |
|---|---|---|---|---|
| 1 | Cross-service contract | Requirements require `shiftId` and trusted `admittedAt` on Payment cash/refund facts, but current Payment/parent event contracts do not yet expose these fields normatively. | Shift could calculate expected cash from incomplete or inferred attribution. | Amend Payment initiate/refund and event schemas in Contracts; add producer/consumer fixtures and reject facts without trusted shift/branch scope. |
| 2 | Concurrency | Close fence/admission semantics are specified behaviorally, while the exact cross-service lock/token mechanism is deferred to Architecture. | Close and cash payment may race differently across implementations. | Choose one admission protocol, define fence/version ordering and test both outcomes: admission-before-fence completes; admission-after-fence is rejected. |
| 3 | Read freshness | `cash-summary` is called authoritative but facts arrive through at-least-once messaging and may be late. | A count could be approved using a stale summary without visible freshness/watermark. | Return source watermark, fence, `calculatedAt` and freshness/status; block approval when required facts are unresolved. |
| 4 | Parent schema alignment | Parent `shifts` schema/events currently cover opening/closing only, not count attempts, discrepancy, review/recount, cancellation return or reconciliation cases. | Parent-generated DTO/DB summaries may drift from the Shift source contract. | Update the parent contract after Architecture/Contracts approval; keep Shift Contracts as the detailed source of truth. |
| 5 | Operational policy | Retention/scale targets are inherited assumptions and the active-shift recovery path is not yet an executable worker/runbook. | Old open/review shifts can block a branch or make history cleanup unsafe. | Define aging metrics, Manager/Owner recovery commands, alerts and 7-year retention/migration policy in Architecture/Contracts. |

## Cross-Impact Analysis

| Dimension | Finding | Risk Level | Recommendation |
|---|---|---|---|
| **Shared Logic** | Parent Shift routes are consumed by POS/Order/Payment; no implementation call sites exist. | 🟡 Medium | Preserve route identity and generate clients from the specialized Shift contract. |
| **Data/State** | Shift lifecycle, count attempts, cash facts and reconciliation cases require strong local consistency and immutable history. | 🔴 High | Use tenant/branch predicates, partial active constraint, source dedupe, row/version locks and append-only correction records. |
| **API Contracts** | Public open/current/close routes exist; P1 count/review and internal admission/cash-fact routes are new. | 🟡 Medium | Define OpenAPI examples/error codes and Payment/Order integration schemas before implementation. |
| **Flow Integrity** | Open → order/payment admission → close/count → review/recount → closed is covered; late facts and broker delay need freshness behavior. | 🟡 Medium | Add sequence diagrams and acceptance fixtures for fence races, late facts, duplicate events and abandoned shift cancellation. |
| **Cross-Cutting** | Cognito/workload auth, integer money, UTC/business timezone, audit, outbox and DLQ follow parent conventions. | 🟢 Low | Carry conventions into Shift API/data/event contracts and runbooks. |

## Instruction Compliance

| Dimension | Verdict | Deviation (if any) | Recommendation |
|---|---|---|---|
| **Conventions** | ✅ | REST/OpenAPI, UUID v7, common envelope, idempotency and parent route naming are followed. | — |
| **Architecture** | ⚠️ | Cross-service close-fence mechanism is intentionally deferred to Architecture. | Resolve the protocol before Contracts approval; do not implement a best-effort time check. |
| **Testing** | ⚠️ | Acceptance criteria are present, but executable fixtures for money math, race ordering and late facts are not materialized. | Add unit, integration, contract, concurrency and replay fixtures to the implementation gate. |

## Verdict

Requirements **có thể được duyệt với warnings**. Không còn blocker về scope, ownership, state lifecycle hoặc money formula. Các warnings phải được xử lý ở Architecture/Contracts trước khi code, đặc biệt là Payment `shiftId` propagation và close-fence admission protocol.

