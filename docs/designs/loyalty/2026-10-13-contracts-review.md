# Spec Sentinel Report

**Spec:** [`docs/designs/loyalty/2026-10-13-contracts.md`](./2026-10-13-contracts.md)  
**Date:** 2026-10-13  
**Status:** ⚠️ Warnings (Proceed with Caution)

## Review context

- Reused the approved Loyalty Vision, Requirements and Architecture.
- Cross-checked the current Order, Payment, POS Platform and Identity contract documents.
- Repository contains design documents but no implementation source or executable migration/API artifacts yet.

## Findings

## Warnings

| # | Category | Finding | Risk | Recommendation |
|---|---|---|---|---|
| 1 | Parent contract | Current Order `OrderPaid.v1` document does not yet contain the Loyalty additive fields defined here: eligible amount, policy version, hold/reservation references and checkout attempt reference. | Generated schemas or producers may omit fields required for accrual/commit, causing fallback calculations or stuck holds. | Apply the amendments in Contracts §11 to the parent Order contract and add producer/consumer schema fixtures before implementation. |
| 2 | Refund event | `OrderRefunded.v1` is defined by Loyalty but is not yet present in the current Order event table; Payment refund state is separate. | Loyalty may receive no canonical full-refund trigger or may consume a provider refund twice. | Make Order the single business-event producer after finalized full refund; map Payment facts into Order and test duplicate/out-of-order refund delivery. |
| 3 | Event ordering | A stale `OrderCheckoutFailed`/release can arrive after `OrderPaid` because broker delivery is at-least-once and out-of-order. | A committed point hold or voucher reservation could be released after consumption, or an already released hold could be committed. | Carry an Order lifecycle/attempt version and define terminal precedence: `COMMITTED/CONSUMED` wins over stale release; release after paid is a no-op with audit. |
| 4 | Quote replay | `quoteId` is an opaque Order snapshot/audit reference and is not a persisted P0 Loyalty quote record. | Operations cannot reconstruct every transient rejected rule from Loyalty after expiry unless Order snapshot is complete. | Require Order to persist full quote breakdown/rule versions; add a P1 quote persistence/read contract if operational replay from Loyalty is required. |
| 5 | Redemption accounting | Contracts define lot allocations and signed reversal deltas, but do not yet provide executable allocation examples for release, restore-after-reversal and expiry races. | Implementations may disagree on whether a release restores `remaining_points` or only removes an active allocation. | Add invariant fixtures and transaction examples in migration/domain test artifacts for every allocation type and concurrent race. |
| 6 | Usage limit atomicity | Voucher limit fields and usage uniqueness are defined, but total/per-customer counter implementation is intentionally left to the migration/domain layer. | A naive count-then-insert can exceed limits under concurrent checkouts. | Require atomic SQL locking/counter update or serializable transaction tests; do not rely on application-only prechecks. |
| 7 | Portal authentication | Portal OTP routes are specified, but the owning OTP provider/session store and key/retention configuration are outside Loyalty. | Portal implementation may duplicate identity logic or retain OTP artifacts inconsistently. | Keep OTP as a separate auth adapter/boundary, document provider/session contract and retain only tenant/customer-scoped portal claims in Loyalty. |

## Cross-Impact Analysis

| Dimension | Finding | Risk Level | Recommendation |
|---|---|---|---|
| **Shared Logic** | Order checkout/paid/failure and refund contracts consume Loyalty references; no implementation call sites exist. | 🔴 High | Update parent schemas and add contract tests before generated clients or consumers. |
| **Data/State** | Core schema covers customer, ledger, lots, allocations, holds, policy, tier, promotion, voucher, inbox/outbox and audit. | 🟡 Medium | Materialize comments, constraints, FK behavior and concurrency indexes in migrations; test restore/rebuild. |
| **API Contracts** | Public staff, internal checkout, config and portal surfaces are versioned with common envelopes and errors. | 🟢 Low | Generate OpenAPI and validate examples; keep portal token routes isolated from staff API. |
| **Flow Integrity** | Quote/hold → Payment/Saga → OrderPaid commit/accrual and refund reversal are specified, but stale release ordering needs explicit parent attempt version. | 🟡 Medium | Add lifecycle version/terminal precedence to Order/Loyalty event contract tests. |
| **Cross-Cutting** | Tenant predicates, PII masking, rate limits, idempotency, outbox/inbox, DLQ and comments are explicit. | 🟡 Medium | Add security/concurrency/PII redaction checks to CI and migration review. |

## Instruction Compliance

| Dimension | Verdict | Deviation (if any) | Recommendation |
|---|---|---|---|
| **Conventions** | ✅ | REST paths, common envelopes, UUID v7, UTC, cursor pagination and event envelope follow parent conventions. | — |
| **Architecture** | ⚠️ | Contract depends on parent Order additions and a new canonical refund event. | Apply parent amendments before implementation. |
| **Testing** | ⚠️ | Contract has acceptance/concurrency requirements but executable OpenAPI/AsyncAPI fixtures do not exist yet. | Generate schema tests and race/replay fixtures before implementation sign-off. |

## Verdict

Contracts **có thể được duyệt với warnings**. Không còn blocker nội bộ trong Loyalty. Parent Order amendments, event terminal precedence và concurrency fixtures phải được đưa vào implementation prerequisites.

---

Started at: 2026/10/13 00:00:00  
Finished at: 2026/10/13 00:00:00  
Total time: 0 minutes
