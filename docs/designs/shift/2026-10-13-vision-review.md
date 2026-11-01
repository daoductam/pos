# Spec Sentinel Report

**Spec:** [`docs/designs/shift/2026-10-13-vision.md`](./2026-10-13-vision.md)  
**Date:** 2026-10-13  
**Status:** ⚠️ Warnings (Proceed with Caution)

## Review context

- Reused the approved POS Platform, Identity, Order, Payment and Inventory design context.
- Applied the `spec-reviewer` gate to the Shift Vision.
- Repository contains design documents but no Shift implementation source or executable migrations.
- Existing parent documents already reserve a Shift service/DB and basic open/current/close routes; this Vision adds the P1 cash count/review boundary.

## Findings

## Warnings

| # | Category | Finding | Risk | Recommendation |
|---|---|---|---|---|
| 1 | Cross-service contract | Payment and Payment events do not yet carry a normative `shiftId`, while Shift computes expected cash from cash payments/refunds scoped to a shift. | Shift cannot reliably attribute a cash transaction to the correct drawer, or may need unsafe inference from order data. | Requirements/Contracts must make `shiftId` mandatory for branch cash payment/refund facts and validate tenant/branch/shift ownership at the Payment/Saga boundary. |
| 2 | Parent data model | Parent `shifts` schema currently models opening/closing cash only; it lacks count snapshot, expected/count/discrepancy, review actor/note and threshold/config version fields. | Generated parent schema and Shift DB may disagree about the source of reconciliation truth. | Extend the Shift-owned schema in the Contracts phase; keep parent POS Platform contract as a summary/reference, not a second source of truth. |
| 3 | Concurrency/close fence | Vision says no new order after close transition begins, but does not define the atomic fence against an order/payment request already in flight. | A cash payment could commit after close or a close could omit a payment that passed validation, producing a false discrepancy. | Requirements must define a DB lock/version fence, accepted/rejected race outcomes, and idempotent behavior for close versus order/payment commands. |
| 4 | Eventual consistency | Expected cash depends on Payment facts and refund timing; late, duplicate or out-of-order facts are acknowledged but not yet assigned a concrete reconciliation policy. | Count may be finalized against an incomplete projection and require manual correction. | Define freshness/closing cutoff, source-event dedupe, late-fact handling and a reconciliation case before approving the data contract. |
| 5 | Scope policy | Vision lists Manager/Owner override and P1 review but does not yet specify whether a zero-transaction shift can be cancelled, or how abandoned active shifts are recovered. | A branch can be blocked by an unused/forgotten shift without a typed operational path. | Resolve cancellation/abandonment policy in Requirements; do not add automatic reopen or silent force-close. |

## Cross-Impact Analysis

| Dimension | Finding | Risk Level | Recommendation |
|---|---|---|---|
| **Shared Logic** | Existing parent Shift routes are referenced by POS, Order and Payment; no implementation call sites exist. | 🟡 Medium | Preserve the parent route identity while specializing request/response and state semantics in Shift contracts. |
| **Data/State** | New shift lifecycle and cash reconciliation records are branch/tenant scoped and interact with immutable payment facts. | 🔴 High | Use local tenant/branch predicates, one active-shift constraint, append-only reconciliation/audit records and explicit snapshot/version fields. |
| **API Contracts** | Open/current/close APIs exist in the parent; P1 count/review and Payment `shiftId` propagation are not yet fully specified. | 🟡 Medium | Amend parent OpenAPI/AsyncAPI/DB summaries after Shift Requirements are approved. |
| **Flow Integrity** | Open → order/payment → count/close → review → closed is conceptually complete; close/payment race and late fact paths need exact transitions. | 🟡 Medium | Add sequence scenarios and acceptance tests for concurrent close, duplicate facts and late refunds. |
| **Cross-Cutting** | Cognito/workload auth, audit, idempotency, timezone and integer money are inherited from parent conventions. | 🟢 Low | Carry these conventions into Shift API/data contracts and runbooks. |

## Instruction Compliance

| Dimension | Verdict | Deviation (if any) | Recommendation |
|---|---|---|---|
| **Conventions** | ✅ | REST, UUID v7, UTC, integer minor units and parent route naming are followed. | — |
| **Architecture** | ✅ | Reuses the approved dedicated Shift service/DB boundary; no shared DB with Order/Payment. | — |
| **Testing** | ⚠️ | Vision has success criteria and risks, but no executable race/late-fact fixture exists yet. | Define contract and concurrency fixtures in Requirements/Contracts before implementation. |

## Verdict

Vision **có thể chuyển sang Requirements với warnings**. Các warnings không chặn phase tiếp theo; Requirements phải biến chúng thành state transition, API, data ownership và testable acceptance criteria.

