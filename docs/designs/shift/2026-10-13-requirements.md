# Module 7 — Ca làm việc: Requirements

**Ngày:** 2026-10-13  
**Trạng thái:** Approved with warnings  
**Vision:** [Shift Vision](./2026-10-13-vision.md)  
**Vision review:** [Shift Vision Review](./2026-10-13-vision-review.md)  
**Phạm vi:** M — Standard Service Flow

## 1. Mục tiêu và ranh giới

Shift service là source of truth cho shift lifecycle và cash-drawer accountability theo `(tenantId, branchId)`. Service sở hữu shift, opening float, cash count attempt, discrepancy, review decision, cancellation và các event/audit references.

Shift không sở hữu order total, payment state, payment provider settlement, user/membership hoặc accounting journal. Order/Payment giao tiếp với Shift qua contract/API/event; không đọc trực tiếp Shift DB.

### Priority

- **P0:** Mở ca với tiền đầu ca, truy vấn ca hiện tại, đóng ca, state/idempotency/RBAC/audit cơ bản.
- **P1:** Cash count, expected cash, discrepancy, review/recount và late-fact reconciliation.
- P2 không nằm trong module này.

## 2. Actors và quyền

| Actor | Quyền |
|---|---|
| Owner | Mở/đóng/hủy/review ca trong branch được chọn; cấu hình threshold/cash-count policy toàn tenant; xem history/audit. |
| Manager | Mở/đóng/hủy/review ca trong branch được cấp; đóng thay hoặc request recount phải có reason; cấu hình policy trong phạm vi được cấp. |
| Cashier | Mở ca trong branch được cấp; đóng ca mình mở; nhập count/recount; xem ca hiện tại và kết quả của ca được cấp. |
| Order/Saga workload | Kiểm tra transaction admission và gắn `shiftId` cho checkout; không mutate shift lifecycle. |
| Payment workload | Gửi cash payment/refund fact kèm `shiftId`; không đóng/mở shift. |
| Reporting/Audit workload | Consume events; không sửa Shift state. |

Identity cung cấp tenant/branch/role decision. Shift re-checks tenant/resource/branch ownership ở application và repository layer. Owner tenant-wide phải chọn branch cụ thể cho mọi branch-scoped command.

## 3. Core domain rules

1. Mỗi `(tenantId, branchId)` chỉ có tối đa một shift ở `ACTIVE` hoặc `PENDING_REVIEW`.
2. `openingFloatMinor`, count và mọi cash amount dùng nonnegative integer minor units; P0 currency là `VND`.
3. Timestamps lưu UTC; `businessDate` tính theo IANA timezone của branch tại thời điểm mở ca.
4. Shift `CLOSED` và `CANCELLED` là terminal; không reopen hoặc sửa trực tiếp historical values.
5. Side-effect command có `Idempotency-Key`; cùng key + cùng canonical hash trả semantic result cũ, khác hash trả conflict.
6. Manager/Owner thao tác thay mặt cashier hoặc override phải gửi reason và tạo audit event.
7. Shift service không dùng `openingFloat` như Payment transaction. Nếu hủy ca, phải ghi nhận xác nhận hoàn lại opening float.

## 4. Shift state machine

| Current | Command/condition | Next | Actor/guard |
|---|---|---|---|
| None | Open shift | `ACTIVE` | Cashier/Manager/Owner, branch authorized, no active/pending shift |
| `ACTIVE` | P0 close without count | `CLOSED` | Opener or Manager/Owner; override reason if applicable |
| `ACTIVE` | P1 submit count within threshold | `CLOSED` | Opener/assigned cashier; count immutable after submit |
| `ACTIVE` | P1 submit count over threshold | `PENDING_REVIEW` | Count valid; note required |
| `ACTIVE` | Manager cancel with no cash transaction + float returned | `CANCELLED` | Manager/Owner, reason + returned float confirmation |
| `PENDING_REVIEW` | Approve latest count | `CLOSED` | Manager/Owner, `If-Match`/review version |
| `PENDING_REVIEW` | Request recount | `PENDING_REVIEW` | Manager/Owner; new immutable count attempt required |
| `CLOSED`/`CANCELLED` | Any lifecycle mutation | Conflict/no-op by command semantics | No reopen; audit only for read/reconciliation |

`PENDING_REVIEW` blocks new shift opening for the branch. Recount does not return the shift to `ACTIVE`.

## 5. Functional requirements P0

### FR-SHIFT-001 — Open shift

Request phải có `branchId`, `openingFloatMinor`, actor context và idempotency key. Nếu Manager/Owner mở thay cashier, request phải có `reason`.

System phải:

- validate branch membership/tenant ownership and branch is `ACTIVE`;
- lock/uniquely check branch active-shift slot;
- reject if an `ACTIVE` or `PENDING_REVIEW` shift exists;
- calculate and store `businessDate` from branch timezone;
- store opener, opening time, opening float, correlation and version;
- write `ShiftOpened.v1` to outbox in the same transaction;
- return the existing semantic result on identical retry.

Errors tối thiểu: `branch_not_found`, `forbidden`, `shift_already_open`, `invalid_amount`, `duplicate_request`, `idempotency_conflict`.

### FR-SHIFT-002 — Read current shift

`GET current` returns the authorized branch's current `ACTIVE`/`PENDING_REVIEW` shift, or `404 no_active_shift` when none exists. Response includes status, branch, opener, opening float, business date, version, close fence if present and current review/count summary subject to role scope.

The endpoint never returns another tenant/branch's resource existence. `PENDING_REVIEW` must be visible to Manager/Owner and the authorized branch operator so the POS can explain why opening/checkout is blocked.

### FR-SHIFT-003 — Close shift P0

P0 `CloseShift` must:

- atomically establish a close fence before transitioning `ACTIVE` to `CLOSED`;
- reject new transaction admissions after the fence;
- include only cash/payment facts admitted before the fence;
- require opener or Manager/Owner authorization;
- require reason for Manager/Owner close-on-behalf/override;
- write `ShiftClosed.v1` and audit in the same local transaction;
- be idempotent for duplicate command/retry;
- reject close on `PENDING_REVIEW` until approved and reject close on terminal states according to typed state semantics.

P0 has no cash count requirement. When the P1 cash-count policy is enabled for the branch, omitting a count for close returns `cash_count_required`.

### FR-SHIFT-004 — Cancel abandoned shift

Manager/Owner may cancel only an `ACTIVE` shift with no admitted cash sale/refund transaction. The command must:

- require reason and explicit `openingFloatReturnedMinor`;
- require `openingFloatReturnedMinor = openingFloatMinor`;
- create an append-only cancellation/return audit record;
- write `ShiftCancelled.v1`;
- never create a cash discrepancy or `ShiftClosed.v1`;
- reject cancellation if any cash transaction is already admitted, even if its provider event is still pending.

## 6. Functional requirements P1

### FR-SHIFT-101 — Cash count attempt

When P1 cash-count policy is enabled, close must submit an immutable cash count attempt. The count captures:

- `countedCashMinor` nonnegative;
- `expectedCashMinor` snapshot;
- `openingFloatMinor`, `cashCollectedMinor`, `cashRefundedMinor` inputs used;
- `discrepancyMinor = countedCashMinor - expectedCashMinor`;
- `discrepancyThresholdMinor` snapshot;
- count actor, count time, close fence, source-fact watermark/version and note when required.

Formula:

```text
expectedCashMinor = openingFloatMinor
                   + cashCollectedMinor
                   - cashRefundedMinor
discrepancyMinor = countedCashMinor - expectedCashMinor
```

Count attempts are append-only after submission. An employee cannot patch the amount after submitting it.

### FR-SHIFT-102 — Expected cash facts

Only valid facts with matching tenant, branch and `shiftId` are included:

- successful cash payment amount received;
- cash refund amount finalized before the close fence;
- no VNPAY or non-cash payment amount;
- no manual cash-in/cash-out in P0/P1.

Payment/Saga must provide a stable source event/transaction ID, `admittedAt`, occurred time, amount, currency, status, `shiftId` and correlation ID. Close-fence inclusion uses trusted admission time, not late broker delivery time. Shift deduplicates source facts and never reads Payment DB.

### FR-SHIFT-103 — Discrepancy threshold and review

- Threshold is configurable per tenant/branch and defaults to `0 VND`.
- If `abs(discrepancyMinor) <= threshold`, the submitted count may close the shift immediately.
- If it exceeds threshold, the shift becomes `PENDING_REVIEW`; a note is mandatory.
- Cashier may see the result but cannot approve or edit it.
- Manager/Owner may approve the latest attempt with decision note and `If-Match`/review version.
- On approval, Shift becomes `CLOSED` and emits close/reconciliation facts.
- Manager/Owner may request recount; the current attempt becomes `RECOUNT_REQUIRED`, Shift remains `PENDING_REVIEW`, and a new attempt must be submitted.
- Review approval cannot alter Payment/Order facts or directly change the counted amount.

### FR-SHIFT-104 — Late/duplicate fact reconciliation

- A cash fact admitted after the close fence, or a duplicate/unknown source fact, must not mutate a `CLOSED` shift.
- Shift creates or updates an append-only `reconciliation_case` with source ID, shift ID, reason, amount, detected time and correlation.
- Duplicate known source fact is a no-op after source-key dedupe.
- Unknown/mismatched tenant, branch or shift is rejected and audited without revealing unrelated resource data.
- Operations can resolve a reconciliation case through a separate authorized command; resolution does not reopen the shift.

### FR-SHIFT-105 — Reconciliation history

Shift history must expose opening, close, count attempts, review decisions, cancellation/float return and reconciliation cases with actor, reason, version and timestamps. Historical records are immutable; correction creates a new record linked to the original.

## 7. Integration requirements

| Integration | Requirement |
|---|---|
| Order | Order creation/checkout must carry `shiftId`; it must not admit a new cash transaction after Shift close fence. |
| Payment | Cash payment/refund request and facts must carry `shiftId`, `branchId`, `tenantId`, source ID, amount and status. Payment must not silently infer shift from time or branch. |
| Saga | Saga propagates `shiftId`, correlation and close/transaction-admission result; retry is idempotent. |
| Identity | Cognito/workload context is authoritative for actor, tenant, branch and permission. |
| Reporting | Consume versioned shift open/close/count/discrepancy/reconciliation facts with freshness metadata. |
| Audit | Store append-only audit for lifecycle mutation, override, cancellation, count, review, rejection and cross-scope denial. |

## 8. API requirements

### 8.1 Public API surface

- `POST /api/v1/branches/{branchId}/shifts` — open shift, idempotent.
- `GET /api/v1/branches/{branchId}/shifts/current` — read current authorized shift.
- `POST /api/v1/shifts/{shiftId}/close` — close P0 or submit P1 count/close.
- `POST /api/v1/shifts/{shiftId}/cancel` — Manager/Owner cancel abandoned shift.
- `POST /api/v1/shifts/{shiftId}/cash-counts` — submit a recount attempt after Manager/Owner requested recount; the initial P1 count is submitted in `CloseShiftRequest`.
- `POST /api/v1/shifts/{shiftId}/cash-counts/{countAttemptId}/approve` — approve latest count.
- `POST /api/v1/shifts/{shiftId}/cash-counts/{countAttemptId}/request-recount` — request recount.
- `GET /api/v1/shifts/{shiftId}` and history endpoints — authorized read/audit view.

All mutations use standard envelope, `Authorization`, `X-Correlation-Id`, `X-Active-Branch-Id`, `Idempotency-Key`; approval/review uses `If-Match` or review version.

### 8.2 Internal integration surface

- `POST /internal/v1/shifts/{shiftId}/transaction-admissions` — validate/admit a transaction before the close fence, returning admission/version metadata.
- `POST /internal/v1/shifts/{shiftId}/cash-facts` — idempotently ingest finalized cash payment/refund fact.
- `GET /internal/v1/shifts/{shiftId}/cash-summary` — return authoritative expected-cash inputs for Saga/Payment reconciliation.
- `POST /internal/v1/shifts/reconciliation-cases/{caseId}/resolve` — authorized operations resolution without reopening shift.

Internal calls require workload identity/mTLS. Tenant and branch are trusted context plus server-side ownership checks; caller body IDs are not authority.

### 8.3 Error taxonomy

Minimum machine-readable codes:

`unauthorized`, `forbidden`, `not_found`, `shift_already_open`, `no_active_shift`, `shift_not_active`, `shift_pending_review`, `shift_closed`, `cash_count_required`, `invalid_amount`, `invalid_currency`, `idempotency_conflict`, `duplicate_request`, `version_conflict`, `count_attempt_immutable`, `recount_required`, `transaction_after_close_fence`, `cash_fact_duplicate`, `foreign_shift_reference`, `late_cash_fact_reconciliation_required`, `opening_float_not_returned`, `manual_cash_movement_not_supported`, `lock_timeout`, `service_unavailable`.

## 9. Data and consistency requirements

Core entities:

- `shifts`: lifecycle, branch, opener, opening float, business date, close fence, status/version.
- `cash_count_attempts`: immutable count inputs/results, snapshot/watermark, attempt status and review version.
- `cash_facts`: deduplicated Payment cash collection/refund facts by shift/source.
- `reconciliation_cases`: late/mismatched/unsafe fact cases and resolution metadata.
- `shift_policies`: threshold and P1 cash-count-required configuration by tenant/branch.
- `outbox_messages`, `idempotency_records`, `audit_references`.

Required invariants:

- partial unique active slot: `(tenant_id, branch_id)` where status in `ACTIVE`, `PENDING_REVIEW`;
- `opening_float_minor`, `counted_cash_minor`, `expected_cash_minor`, refund/collection amounts `>= 0`;
- `discrepancy_minor = counted_cash_minor - expected_cash_minor`;
- cash fact source key unique within tenant/shift/source type/source ID;
- count attempt version prevents duplicate approval and lost review;
- close fence and state transition update are one local transaction;
- outbox insert is atomic with lifecycle/review state;
- every query/write includes tenant and branch predicates where branch-scoped.

## 10. Non-functional requirements

| Category | Requirement | Target |
|---|---|---|
| Performance | Open/current/close/count/review API excluding downstream broker | p95 ≤ 500 ms under demo load |
| Consistency | One active slot and terminal state transitions | Strong PostgreSQL transaction consistency |
| Concurrency | Concurrent open, close/payment admission, duplicate review | Deterministic lock/version winner; no double active shift or double approval |
| Reliability | Retryable commands/events | Idempotency, transactional outbox, bounded retry and DLQ/reconciliation |
| Security | Tenant/branch/RBAC/IDOR | Defense-in-depth at API/service/repository; fail closed |
| Money | Amount arithmetic | Integer minor units, VND P0, no floating point |
| Time | Business date and timestamps | UTC storage; branch IANA timezone for business date/display |
| Auditability | Lifecycle, override, count/review and discrepancy | Append-only audit reference with actor/reason/correlation |
| Observability | Shift aging, close fence, discrepancy, late facts, queue | Structured logs, metrics, traces and alerts |
| Retention | Shift/count/reconciliation/audit history | At least 7 years, subject to platform retention policy |
| Scale | Demo baseline | 100 branches, 2–4 shifts/branch/day, 100k orders/day inherited parent baseline |
| Availability | Dependency outage | Do not silently close based on incomplete cash facts; expose safe retry/reconciliation state |

## 11. Edge cases and failure policy

- Concurrent open: one succeeds, others receive `409 shift_already_open` or the idempotent existing result.
- Close retry: same key/hash returns original result; different payload returns `409 idempotency_conflict`.
- Close while order/payment admission is in flight: close fence deterministically wins or waits according to admission contract; no fact is silently omitted.
- Cash payment insufficient: Payment rejects before cash fact; Shift receives nothing.
- Duplicate PaymentPaid/refund fact: source-key no-op.
- Late cash fact after `CLOSED`: create reconciliation case; never reopen or mutate expected cash.
- Count amount negative/overflow/wrong currency: validation error; no state transition.
- Recount after review approval: rejected; closed shift cannot reopen.
- Cancel active shift after any admitted cash fact: rejected; use close/count/reconciliation path.
- Cross-tenant/branch shift or cash fact: forbidden/foreign reference; no resource detail leakage.
- Broker outage after local close: local state remains committed, outbox retries; read response includes event publication status only if safe.

## 12. Acceptance criteria

### P0

1. Authorized Cashier opens a branch shift with nonnegative opening float and receives `ACTIVE`.
2. Two concurrent open requests cannot create two active/pending shifts.
3. Unauthorized role, wrong tenant or wrong branch cannot open/read/close another shift.
4. P0 close transitions `ACTIVE → CLOSED` with close fence, audit and one outbox event.
5. Manager/Owner close-on-behalf requires reason; duplicate retry does not duplicate event/audit/state transition.
6. Manager cancels an `ACTIVE` shift only when no cash fact exists and full opening float return is confirmed.
7. Order/Payment admission after close fence is rejected with typed error; late fact creates reconciliation case.

### P1

1. Close with count computes expected cash and signed discrepancy exactly with integer arithmetic.
2. Threshold `0` routes any nonzero discrepancy to `PENDING_REVIEW`; matching count closes immediately.
3. `PENDING_REVIEW` blocks new shift opening and new transaction admission for the branch.
4. Manager/Owner can approve latest immutable count; approval closes shift exactly once.
5. Manager/Owner can request recount; new count attempt is required and old attempt remains auditable.
6. Cashier cannot edit submitted count or approve discrepancy.
7. Duplicate/out-of-order/late cash facts do not alter a closed shift or double-count money.
8. Reporting/Audit receive opening, close, count, review, cancellation and reconciliation facts.

## 13. Traceability

| Capability | Vision | Requirement/contract coverage |
|---|---|---|
| Open with opening cash | §4.1 | FR-SHIFT-001, open API, `shifts` invariants |
| Close shift | §4.2 | FR-SHIFT-003, close fence, state machine |
| Cash count | §5.1 | FR-SHIFT-101/102, count API/schema |
| Discrepancy review | §5.2 | FR-SHIFT-103, approval/recount state |
| One active shift per branch | §4.1 | Partial unique + concurrency acceptance |
| Late/duplicate fact safety | §9 | FR-SHIFT-104, cash fact source dedupe/reconciliation |
| RBAC/audit | §3, §8 | Permission matrix, audit/outbox, negative tests |

## 14. Next phase inputs

- Architecture must define branch close fence/admission implementation, transaction locks, outbox publisher, reconciliation worker and projection/read model.
- API/Data Contracts must define OpenAPI request/response/error schemas, Payment/Order `shiftId` propagation, event payloads and pagination.
- Database Design must define every column/comment, partial active constraint, source dedupe, count snapshot/watermark and migration order.
- Sequence diagrams must cover open race, P0 close, P1 count/review/recount, close/payment race, cancellation and late fact reconciliation.

## Approval gate

- **Current status:** Approved with warnings
- **Next action:** Architecture and Contracts completed; resolve cross-service warnings before code

Started at: 2026-10-13 00:00:00  
Finished at: 2026-10-13 00:00:00  
Total time: 0 minutes

