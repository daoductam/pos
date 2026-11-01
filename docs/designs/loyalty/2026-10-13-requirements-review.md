# Spec Sentinel Report

**Spec:** [`docs/designs/loyalty/2026-10-13-requirements.md`](./2026-10-13-requirements.md)  
**Date:** 2026-10-13  
**Status:** ⚠️ Warnings (Proceed with Caution)

## Review context

- Phase 0 skipped — reused the preceding Vision/brainstorming context and existing module documents.
- Repository scan found design documents and `idea.md`, but no implementation source or test suite to trace.
- Cross-checked against Order Contracts/Requirements, Payment Requirements and POS Platform Contracts.

## Findings

## Warnings

| # | Category | Finding | Risk | Recommendation |
|---|---|---|---|---|
| 1 | API Contracts | Loyalty requires `loyaltyEligibleAmountMinor` and `loyaltyPolicyVersion` on `OrderPaid.v1`, but the current Order payload only contains generic totals and line snapshots. | Loyalty may reimplement pricing rules or calculate different points from Order. | In API/Data Contracts, add these as additive fields with semantics, validation and versioning; add producer/consumer contract tests. |
| 2 | Event Ownership | Requirements consume `OrderRefunded.v1` for P0 full reversal, but the current Order event table does not define this event and Payment owns provider refund state. | Refund may be emitted twice, by the wrong service, or before the refund is final; points could be reversed incorrectly. | Define Order as the canonical business refund event producer after a finalized refund, with Payment refund facts mapped into it. Include `refundId`, `refundType`, amount/status and idempotency semantics. |
| 3 | Data Integrity | Full reversal says to restore committed redemption with an offsetting entry/lot, but the exact lot allocation and behavior after expiry/re-redemption are not fully formalized. | Reversal can create incorrect available balance or negative lots in long-running histories. | Architecture/Data Contracts should define redemption-to-lot allocation, reversal lineage, and invariants for `earned`, `redeemed`, `reversed`, `expired` and active holds. |
| 4 | Cross-Service Flow | `OrderPaid` is both the accrual trigger and the commit trigger for point/voucher reservations, while the current Order contract does not explicitly snapshot point-hold or voucher-reservation references. | Loyalty may not know which hold/reservation to commit, especially when an order has retries or multiple payment attempts. | Extend checkout and `OrderPaid` contracts with optional loyalty hold/reservation IDs and define the no-hold/no-voucher path. |
| 5 | Promotion Scope | “Customer-beneficial” conflict resolution with stacking can require evaluating multiple combinations; no maximum active rules or evaluation timeout is stated. | A large rule set can make checkout slow or produce non-deterministic operational behavior under load. | Add bounded active-rule count, evaluation timeout, deterministic candidate ordering and a fail-closed behavior for unavailable/expired quote. |
| 6 | Customer Profile Coverage | The document defines lookup/create and PII handling, but does not define the minimal editable profile fields or whether a customer can update name/consent/contact metadata. | Different clients may invent incompatible profile mutations or expose excessive PII. | Keep P0 minimal and explicitly define fields/permissions in API Contracts; defer nonessential profile attributes and merge to P1/P2. |
| 7 | Security/Data Lifecycle | Encryption and masked responses are required, but key rotation, retention/deletion policy and OTP data retention are not yet specified. | PII and authentication artifacts may be retained longer than policy allows or become difficult to rotate safely. | Capture KMS/key rotation, retention, deletion/anonymization and OTP storage rules in Architecture/Security sections. |

## Cross-Impact Analysis

| Dimension | Finding | Risk Level | Recommendation |
|---|---|---|---|
| **Shared Logic** | No implementation symbols or call sites exist. The conceptual shared boundary is `OrderPaid.v1`, `OrderRefunded.v1`, checkout attempt and customer reference. | 🟡 Medium | Treat event schemas as shared APIs; add schema compatibility and replay tests before implementation. |
| **Data/State** | Loyalty introduces append-only ledger, point lots, holds, voucher reservations, usage counters, tier history and rebuildable projections. | 🔴 High | Use explicit unique constraints, allocation lineage, tenant predicates and transaction boundaries in Database Design. |
| **API Contracts** | Order `OrderPaid.v1` currently lacks loyalty-specific eligible amount/policy fields; refund event is not yet defined. | 🔴 High | Resolve the two event contracts before implementation planning; prefer additive versioned fields where semantics remain compatible. |
| **Flow Integrity** | POS → Order quote/hold → Payment/Saga → OrderPaid → Loyalty commit/accrue; refund → OrderRefunded → Loyalty reversal. | 🟡 Medium | Write sequence diagrams for success, payment failure, timeout, duplicate event and full refund paths. |
| **Cross-Cutting** | Tenant/branch authorization, PII protection, idempotency, outbox delivery, DLQ/replay and job leases are required. | 🔴 High | Apply defense-in-depth tenant predicates, structured audit, metrics/alerts and bounded retries to every mutation/consumer. |

## Instruction Compliance

| Dimension | Verdict | Deviation (if any) | Recommendation |
|---|---|---|---|
| **Conventions** | ✅ | Document is under `docs/designs/loyalty/`, uses module/priority sections and links parent documents. | — |
| **Architecture** | ⚠️ | Loyalty boundary is clear, but it depends on Order event fields/events not yet present in the parent contract. | Resolve dependencies in API/Data Contracts and update the affected parent contract documents. |
| **Testing** | ⚠️ | Acceptance criteria cover core invariants, but schema contract tests, property/concurrency tests and replay tests are not yet mapped to artifacts. | Add a test matrix during Architecture/Contracts; include duplicate, concurrent hold, FIFO, reversal and cross-tenant cases. |

## Verdict

The Requirements document is sufficiently complete to proceed to Architecture with caution. No internal Loyalty requirement is blocked, but the Order/Loyalty event dependencies must be resolved before implementation planning or code generation.

**Recommended next step:** after user approval, proceed to Architecture and carry warnings 1–4 as explicit architecture decisions and contract-change tasks.

---

Started at: 2026/10/13 00:00:00  
Finished at: 2026/10/13 00:00:00  
Total time: 0 minutes
