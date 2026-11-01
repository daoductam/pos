Started at: 2026-10-13 00:00:00
Finished at: 2026-10-13 00:00:00

# Spec Sentinel Report

**Spec:** [docs/designs/inventory/2026-10-13-requirements.md](./2026-10-13-requirements.md)  
**Date:** 2026-10-13  
**Status:** ⚠️ Warnings (Proceed with Caution)

Phase 0 skipped — reusing the current-session context. No application source implementation exists for Inventory; the impact scan covered the existing parent and service design documents.

The previous blocker set was rechecked after the approved amendments. The direct Saga commit boundary, parent Inventory API/schema, quantity precision, canonical event names and reservation reconciliation terminal state are now aligned.

## Findings

## Warnings

| # | Category | Finding | Risk | Recommendation |
|---|---|---|---|---|
| 1 | Count adjustment concurrency | Requirements require showing concurrent movements but do not yet define the exact formula for converting snapshot, counted quantity and post-snapshot movements into an approved delta. | Two managers could approve different deltas for the same count session. | Define one formula and one approval version/lock in Architecture/Database; reject stale approval with a typed conflict. |
| 2 | Operational thresholds | Lock wait, bounded retry count, API timeout and outbox retry limits are bounded conceptually but not numerically fixed. | Services may choose incompatible timeout/retry behavior under contention. | Set initial configuration defaults and failure budgets in Architecture, then expose metrics and test them under concurrent reserve load. |
| 3 | Forecast quality | P2 baseline and fallback are defined, but minimum sample/seasonality quality thresholds and safety-stock configuration ownership are not yet contract-level fields. | Forecast recommendations can appear precise when data is insufficient. | Include `quality`, `sampleCount`, `inputWindow`, safety-stock source and `INSUFFICIENT_HISTORY` semantics in the forecast contract. |
| 4 | Testing | Acceptance criteria cover main flows but do not yet name executable fixtures for deadlock retry, commit/release race, projection rebuild, outbox replay and cross-tenant IDOR. | Critical concurrency and isolation regressions may pass happy-path tests. | Add a P0 contract/integration test matrix before implementation planning. |

## Cross-Impact Analysis

| Dimension | Finding | Risk Level | Recommendation |
|---|---|---|---|
| **Shared Logic** | No Inventory application symbols or call sites exist. Planned consumers are Step Functions/Order, Payment event routing, Catalog IDs, Reporting, Audit and Notification. | 🟡 Medium | Materialize versioned Inventory command/event contracts and consumer tests; do not create shared domain repositories. |
| **Data/State** | Parent Inventory schema now points to tenant ingredient master, branch balances, recipe versions, reservation snapshots and typed ledger entries. | 🟡 Medium | Keep Inventory DB service-owned and require migrations/comments/check constraints from Database Design. |
| **API Contracts** | Parent API now includes direct Saga commit and updated reserve payload; concrete OpenAPI examples, error schemas and workload auth claims remain to be authored. | 🟡 Medium | Complete API Contracts and contract fixtures before implementation. |
| **Flow Integrity** | Reserve → payment fact → Saga commit → Order finalize, plus release/expiry/reconciliation paths, is coherent. | 🟢 Low | Preserve the no-direct-`PaymentPaid`-to-Inventory rule in contract tests and runbooks. |
| **Cross-Cutting** | Tenant/branch defense-in-depth, workload identity, immutable audit and no-secret event policy are present; operational limits need numerical defaults. | 🟡 Medium | Carry the remaining thresholds and metrics into Architecture/Operations. |

## Instruction Compliance

| Dimension | Verdict | Deviation (if any) | Recommendation |
|---|---|---|---|
| **Conventions** | ✅ | — | Keep amended parent contracts as the compatibility source and Inventory documents as the detailed service source. |
| **Architecture** | ✅ | — | Direct Saga commit and service-owned Inventory state align with parent Order flow. |
| **Testing** | ⚠️ | Executable concurrency/contract fixture list is incomplete. | Add the P0 test matrix in Contracts/Architecture and require it at the implementation gate. |

## Decision Gate

No critical blocker remains. Proceed with user approval of Requirements, carrying the four warnings into Architecture, API Contracts, Database Design and the implementation test gate.
