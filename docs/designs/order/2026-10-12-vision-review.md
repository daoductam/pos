Started at: 2026/10/12 16:16:00 +07
Finished at: 2026/10/12 16:16:00 +07
Total time: 0 minutes
---

# Spec Sentinel Report

**Spec:** [2026-10-12-vision.md](./2026-10-12-vision.md)

**Date:** 2026-10-12

**Status:** ⚠️ Warnings (Proceed with Caution)

## Findings

### Warnings

| # | Category | Finding | Risk | Recommendation |
|---|---|---|---|---|
| 1 | Saga boundary | Vision names Step Functions as orchestrator and Order as state owner, but does not yet define the exact command/event contract for `Inventory.commit`, payment success after reservation, or finalize retry. | A late/duplicate callback could finalize twice or leave payment, stock and Order in conflicting terminal states. | Requirements must define the Saga state machine, idempotency scope, compensation order and terminal reconciliation states for every outcome. |
| 2 | Payment exception | Late VNPAY success after reservation release is routed to `late_payment_reconciliation`, but ownership and allowed operator actions are not specified. | A customer may be charged while Order remains failed; an unsafe automatic retry could double-charge or oversell. | Define Payment as owner of provider settlement/reconciliation, Order as owner of order state, and document a no-auto-finalize/no-double-charge rule plus alert/audit path. |
| 3 | Money/totals | Vision says Order snapshots price and excludes VAT/discount responsibility unless separately contracted. It does not yet state how subtotal, VAT, discount, rounding and final payable amount are calculated for P0. | Payment and Order may use different amounts, causing financial mismatch and invalid reservation/payment commands. | Requirements must designate one authoritative total calculation in Order and define integer money, tax/rounding inputs, discount absence/P1 behavior and immutable checkout totals. |
| 4 | Snapshot refresh | Checkout blocks when Catalog changed, but the client command to refresh/reapply the current Catalog selection is not defined. | Cashier may be stuck with stale line snapshots or accidentally overwrite a changed modifier/quantity. | Define a typed `catalog_changed` response, explicit refresh/reprice command and version requirement before a new checkout attempt. |
| 5 | Audit contract | Cancel audit is required, but retention, event shape, before/after fields and failure behavior are not defined. | Sensitive cancellation actions may be unverifiable or may block business mutations due to an unavailable Audit consumer. | Order must atomically emit an audit outbox event with actor, role, branch, reason, line snapshot and correlation; Audit consumes asynchronously with retry/DLQ. |
| 6 | Cross-tenant/branch scope | Vision states tenant/branch isolation but does not spell out Owner/Manager selection, branch checks for order IDs and same-branch draft edits at each repository path. | IDOR or cross-branch draft mutation could expose orders or alter checkout state. | Requirements must require Identity decision plus defense-in-depth `tenant_id`/`branch_id` predicates for every read, write, batch and internal Saga call. |
| 7 | P1 scope breadth | Table map, hold order, split/merge invoice and KDS WebSocket are each substantial features, while the Vision only names them as P1. | P1 may leak into the P0 data model or delay checkout delivery. | Keep P1 behind feature flags and define only compatibility boundaries now; defer table occupancy, split/merge invariants and WebSocket replay details to a later contract phase. |
| 8 | Customer/Loyalty linkage | Parent platform includes optional customer/loyalty identifiers, but Order Vision does not define whether customer lookup is part of Order P0 or a separate Loyalty flow. | API consumers may add phone/PII fields to Order incorrectly or duplicate customer ownership. | Keep `customerId` as an opaque optional reference; Loyalty owns lookup/PII and Order only snapshots the reference needed by `OrderPaid`. |

## Cross-Impact Analysis

| Dimension | Finding | Risk Level | Recommendation |
|---|---|---|---|
| **Shared Logic** | No Order implementation exists; direct dependencies are the Identity context, Catalog resolver, Inventory reservation API, Payment API/IPN and Step Functions execution. | 🟡 Medium | Define each boundary as a versioned contract before implementation; do not create shared domain repositories. |
| **Data/State** | New Order DB owns orders, lines, snapshots and checkout attempts; Saga references external reservation/payment IDs. | 🔴 High | Use local state transitions, immutable snapshots, unique idempotency keys and explicit reconciliation fields. |
| **API Contracts** | Parent `/api/v1/orders`, line cancel and checkout endpoints are baseline; Catalog variant resolution and Inventory/Payment internal APIs must be specialized. | 🟡 Medium | Update parent Order schemas for concrete variant snapshots, catalog version and typed checkout failure/compensation responses. |
| **Flow Integrity** | Draft → Catalog resolve → reservation → payment → finalize/compensate is covered conceptually; late IPN and commit/release races need exact transitions. | 🔴 High | Add state transition tables and failure-path acceptance tests in Requirements. |
| **Cross-Cutting** | Idempotency, audit, tenant scope, trace propagation, timeout and DLQ are required across public API and Saga callbacks. | 🔴 High | Make correlation/saga/tenant/branch metadata mandatory and add negative/security tests. |

## Instruction Compliance

| Dimension | Verdict | Deviation (if any) | Recommendation |
|---|---|---|---|
| **Conventions** | ✅ | Uses the existing `docs/designs/order/` folder, parent links and P0/P1/P2 boundaries. | — |
| **Architecture** | ✅ | Aligns with database-per-service, REST/OpenAPI, EventBridge/SQS, transactional outbox and Step Functions parent decisions. | Keep service ownership explicit in Requirements. |
| **Testing** | ⚠️ | Vision lists unit, integration, contract, E2E, load and failure tests but does not map them to exact Saga transitions. | Convert every warning/failure path into a named acceptance test before Architecture. |

## Review Decision

- No blocker was found.
- The Vision may proceed to Requirements after the user confirms these warnings.
- Requirements must resolve warnings 1–6 explicitly; warnings 7–8 may remain as controlled P1/P0 boundary decisions.

## Approval

- **Status:** Awaiting user confirmation of warnings
- **Next phase after confirmation:** Requirements Discovery
