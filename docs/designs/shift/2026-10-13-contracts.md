Started at: 2026/10/13 00:00:00
Finished at: 2026/10/13 00:00:00
Total time: 0 minutes
---

# Module 7 — Ca làm việc: API & Data Contracts

**Ngày:** 2026-10-13  
**Trạng thái:** Approved  
**Vision:** [Shift Vision](./2026-10-13-vision.md)  
**Requirements:** [Shift Requirements](./2026-10-13-requirements.md)  
**Architecture:** [Shift Architecture](./2026-10-13-architecture.md)  
**Sequence diagrams:** [Shift Sequences](./2026-10-13-sequence-diagrams.adoc)

Tài liệu này là nguồn chuẩn để sinh OpenAPI 3.1, AsyncAPI/JSON Schema và migration plan cho Shift Service. Các contract Order/Payment được amend trong phần 9 để đảm bảo cash accountability không suy diễn từ thời gian hoặc branch.

## 1. Contract conventions

### 1.1 API và encoding

- Public REST: `/api/v1`, content type `application/json; charset=utf-8`.
- Internal REST: `/internal/v1`, chỉ workload identity/mTLS, không expose qua Angular.
- Events: AsyncAPI + JSON Schema, event envelope của parent platform.
- IDs: UUID v7 string.
- Timestamp: ISO-8601 UTC; `businessDate` là `YYYY-MM-DD` theo IANA timezone snapshot của branch.
- Money: signed/nonnegative `int64` minor units; P0 chỉ `VND`, không dùng floating point.
- Breaking change tạo major URI/event version mới; field optional mới phải backward-compatible.

### 1.2 Headers

| Header | Public | Internal | Ý nghĩa |
|---|---:|---:|---|
| `Authorization` | bắt buộc | bắt buộc | Cognito JWT hoặc workload credential/mTLS |
| `X-Active-Branch-Id` | branch command/query | không dùng làm authority | Branch UI đang chọn; server phải re-check membership |
| `X-Correlation-Id` | khuyến nghị | bắt buộc | Trace xuyên POS/Saga/Payment/Shift/events |
| `X-Causation-Id` | không | khuyến nghị | Command/event gây ra operation |
| `Idempotency-Key` | mutation retryable | mutation retryable | Scope tenant + operation + actor/workload |
| `If-Match` | review/version command | khi dùng version | Optimistic concurrency token |
| `Content-Type` | body API | body API | `application/json; charset=utf-8` |

Client không được đặt `tenantId`, role, actor hoặc branch ownership trong body để thay thế auth context. `openedForUserId`/`closedForUserId` chỉ là effective operator request và phải được server kiểm tra.

### 1.3 Success envelope

```json
{
  "data": {},
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

### 1.4 Error envelope

```json
{
  "error": {
    "code": "transaction_after_close_fence",
    "message": "The shift no longer accepts new transactions",
    "details": [
      {
        "field": "shiftId",
        "code": "fence_closed",
        "message": "Start a new shift before retrying checkout"
      }
    ],
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

Không trả SQL, JWT, provider secret, raw signature hoặc resource existence ngoài scope.

### 1.5 HTTP và error codes

| HTTP | Code | Khi dùng | Retry |
|---:|---|---|---:|
| 200 | — | Read/successful terminal command | no |
| 201 | — | Created shift/admission/count attempt | no |
| 202 | `close_processing` | Fence đã commit, finalizer đang drain | poll/retry cùng key |
| 401 | `unauthorized` | Auth thiếu/sai/hết hạn | sau khi refresh auth |
| 403 | `forbidden` | Sai role/tenant/branch | no |
| 404 | `not_found` | Không tồn tại trong authorized scope | no |
| 409 | `shift_already_open` | Đã có ACTIVE/PENDING_REVIEW/CLOSING slot | no |
| 409 | `shift_pending_review` | Branch/shift đang review | no; resolve review |
| 409 | `shift_not_active` | Command không hợp lệ với state hiện tại | no |
| 409 | `transaction_after_close_fence` | Admission mới sau fence | no; chọn shift mới |
| 409 | `cash_count_required` | Policy yêu cầu count nhưng request thiếu count | sửa request |
| 409 | `version_conflict` | If-Match/review version cũ | đọc lại |
| 409 | `idempotency_conflict` | Cùng key nhưng canonical hash khác | no |
| 409 | `duplicate_request` | Request đã có semantic result | dùng result cũ |
| 409 | `count_attempt_immutable` | Sửa attempt đã submit | no |
| 409 | `late_cash_fact_reconciliation_required` | Fact không thể mutate historical shift | operator |
| 409 | `opening_float_not_returned` | Cancel thiếu xác nhận trả đủ float | sửa request |
| 422 | `validation_error` | Schema/business input sai | no |
| 422 | `invalid_amount` | Amount âm, overflow hoặc sai currency | no |
| 422 | `foreign_shift_reference` | Shift không cùng tenant/branch | no |
| 422 | `manual_cash_movement_not_supported` | Cash-in/out ngoài scope | no |
| 429 | `rate_limited` | Gateway/service limit | bounded backoff |
| 503 | `service_unavailable` | DB/dependency không an toàn để tiếp tục | bounded retry |

## 2. Authorization contract

| Operation | Owner | Manager | Cashier | Workload |
|---|---:|---:|---:|---:|
| Open shift | branch selected | assigned branch | assigned branch | — |
| Read current/history | tenant/branch scope | assigned branch | assigned branch | — |
| Close own shift | yes | yes | opener only | — |
| Close on behalf/override | reason required | reason required | no | — |
| Cancel abandoned shift | reason + full float return | reason + full float return | no | — |
| Submit initial count/recount | yes if acting operator | yes if acting operator | yes | — |
| Approve/recount request | yes | yes | no | — |
| Transaction admission | — | — | — | Order/Saga/Payment workload |
| Cash fact ingestion | — | — | — | Payment workload |
| Resolve reconciliation case | restricted ops/owner | assigned ops permission | no | operations workload |

Identity supplies actor, tenant, branch and role decision. Shift repeats ownership checks at application and repository layers. Manager/Owner action thay mặt cashier requires `reason`; server records actor and effective operator separately.

## 3. Public API surface

| Method | Path | Auth/scope | Request | Response | Status |
|---|---|---|---|---|---|
| POST | `/api/v1/branches/{branchId}/shifts` | shift:open | `OpenShiftRequest` | `ShiftResponse` | 201, 403, 409, 422 |
| GET | `/api/v1/branches/{branchId}/shifts/current` | shift:read | — | `CurrentShiftResponse` | 200, 403, 404 |
| GET | `/api/v1/branches/{branchId}/shifts` | shift:read | cursor/filter | `ShiftPage` | 200, 403, 422 |
| GET | `/api/v1/shifts/{shiftId}` | shift:read | — | `ShiftDetailResponse` | 200, 403, 404 |
| POST | `/api/v1/shifts/{shiftId}/close` | shift:close | `CloseShiftRequest` | `CloseShiftResponse` | 200, 202, 403, 404, 409, 422 |
| POST | `/api/v1/shifts/{shiftId}/cancel` | shift:cancel | `CancelShiftRequest` | `ShiftResponse` | 200, 403, 404, 409, 422 |
| POST | `/api/v1/shifts/{shiftId}/cash-counts` | shift:count | `RecountRequest` | `CashCountAttemptResponse` | 201, 403, 404, 409, 422 |
| POST | `/api/v1/shifts/{shiftId}/cash-counts/{countAttemptId}/approve` | shift:review | `ApproveCountRequest` | `ShiftResponse` | 200, 403, 404, 409, 422 |
| POST | `/api/v1/shifts/{shiftId}/cash-counts/{countAttemptId}/request-recount` | shift:review | `RequestRecountRequest` | `CashCountAttemptResponse` | 200, 403, 404, 409 |

`POST /cash-counts` chỉ dùng cho recount sau khi Manager/Owner đã yêu cầu recount. Initial count của P1 nằm trong `CloseShiftRequest` để fence và count được xử lý cùng close operation.

### 3.1 Open shift

`POST /api/v1/branches/{branchId}/shifts`

Request:

```json
{
  "openingFloatMinor": 1000000,
  "currency": "VND",
  "openedForUserId": null,
  "reason": null
}
```

| Field | Type | Required | Rule |
|---|---|---:|---|
| `openingFloatMinor` | int64 | yes | `>= 0`, P0 max theo tenant policy |
| `currency` | enum | yes | `VND` |
| `openedForUserId` | UUID/null | no | Effective cashier; default actor; Manager/Owner only |
| `reason` | string/null | conditional | Required when actor opens for another user or override; 1–500 chars |

Response `201`:

```json
{
  "data": {
    "shiftId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
    "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789013",
    "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789014",
    "openedByUserId": "0192b2d0-7f1d-7abc-8d2c-123456789015",
    "operatorUserId": "0192b2d0-7f1d-7abc-8d2c-123456789016",
    "status": "ACTIVE",
    "admissionPhase": "OPEN",
    "openingFloatMinor": 1000000,
    "currency": "VND",
    "businessDate": "2026-10-13",
    "branchTimezone": "Asia/Ho_Chi_Minh",
    "version": 1,
    "openedAt": "2026-10-12T17:00:00Z",
    "closeStatus": "NOT_STARTED"
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

### 3.2 Current shift và history

`GET /api/v1/branches/{branchId}/shifts/current`

- Trả `ACTIVE`, `PENDING_REVIEW` hoặc `ACTIVE + closeStatus=PROCESSING` nếu close fence đã commit.
- Không có current shift trả `404 no_active_shift` trong authorized scope.
- `PENDING_REVIEW` phải hiển thị `reviewReason`, `latestCountAttempt`, `branchCheckoutBlocked=true`.

`GET /api/v1/branches/{branchId}/shifts?limit=20&cursor=<opaque>&status=CLOSED`

- Cursor ổn định theo `(createdAt DESC, shiftId DESC)`.
- `limit` mặc định 20, tối đa 100.
- Filter `status`, `businessDateFrom`, `businessDateTo`, `openedByUserId`, `hasDiscrepancy` được allowlist.
- Scope predicate được áp dụng trước cursor; cursor không được client tự giải mã/sửa.

### 3.3 Close shift

`POST /api/v1/shifts/{shiftId}/close`

Request P0:

```json
{
  "closedForUserId": null,
  "reason": null,
  "countedCashMinor": null,
  "countNote": null
}
```

Request P1 khi cash-count policy bật:

```json
{
  "closedForUserId": null,
  "reason": null,
  "countedCashMinor": 2875000,
  "countNote": "Đã đếm cùng quản lý"
}
```

| Field | Type | Required | Rule |
|---|---|---:|---|
| `closedForUserId` | UUID/null | no | Effective cashier; default actor; override cần reason |
| `reason` | string/null | conditional | Required for on-behalf/override; 1–500 chars |
| `countedCashMinor` | int64/null | policy-dependent | Nonnegative; required when P1 count policy enabled |
| `countNote` | string/null | discrepancy-dependent | Required when threshold exceeded or unresolved admission |

Close response `200` terminal:

```json
{
  "data": {
    "closeOperationId": "0192b2d0-7f1d-7abc-8d2c-123456789020",
    "closeStatus": "COMPLETED",
    "shift": {
      "shiftId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
      "status": "CLOSED",
      "admissionPhase": "FROZEN",
      "version": 4,
      "closedAt": "2026-10-13T00:05:00Z"
    },
    "cashSummary": {
      "openingFloatMinor": 1000000,
      "cashCollectedMinor": 2100000,
      "cashRefundedMinor": 22500,
      "expectedCashMinor": 3077500,
      "countedCashMinor": 3077500,
      "discrepancyMinor": 0,
      "currency": "VND",
      "sourceWatermark": "admission-seq-42",
      "calculatedAt": "2026-10-13T00:05:00Z"
    }
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

Close response `202`:

```json
{
  "data": {
    "closeOperationId": "0192b2d0-7f1d-7abc-8d2c-123456789020",
    "closeStatus": "PROCESSING",
    "shift": {
      "shiftId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
      "status": "ACTIVE",
      "admissionPhase": "CLOSING",
      "version": 3,
      "closeFenceSeq": 42,
      "closeFenceAt": "2026-10-13T00:04:59Z"
    },
    "pollAfterMs": 500
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

Trong `PROCESSING`, retry phải giữ cùng `Idempotency-Key`; không tạo close operation mới. Khi drain timeout, operation trả terminal `PENDING_REVIEW` với `reviewReason=UNRESOLVED_PRE_FENCE_ADMISSION`.

### 3.4 Cancel abandoned shift

`POST /api/v1/shifts/{shiftId}/cancel`

```json
{
  "reason": "Ca mở nhầm, đã hoàn lại đủ tiền đầu ca",
  "openingFloatReturnedMinor": 1000000
}
```

Cancel chỉ thành công khi shift `ACTIVE`, chưa có admitted cash sale/refund, và `openingFloatReturnedMinor` bằng chính xác `openingFloatMinor`. Cancel không tạo `ShiftClosed.v1`, không tạo discrepancy và không tự động mở shift mới.

### 3.5 Recount/review

Recount:

```json
{
  "reason": "Đếm lại do lệch 5.000 VND",
  "expectedReviewVersion": 2
}
```

Submit recount:

```json
{
  "countedCashMinor": 3072500,
  "note": "Đã đếm lại với quản lý"
}
```

Approve:

```json
{
  "decisionNote": "Đã xác nhận lệch theo biên bản",
  "expectedReviewVersion": 3
}
```

Approval không nhận `countedCashMinor`; amount chỉ lấy từ immutable attempt. `If-Match` hoặc `expectedReviewVersion` bắt buộc để tránh approve attempt cũ.

## 4. Internal API surface

| Method | Path | Caller | Request | Response | Status |
|---|---|---|---|---|---|
| POST | `/internal/v1/shifts/{shiftId}/transaction-admissions` | Order/Saga/Payment | `CreateAdmissionRequest` | `AdmissionResponse` | 201, 409, 422, 503 |
| POST | `/internal/v1/shifts/{shiftId}/transaction-admissions/{admissionId}/complete` | Order/Saga/Payment | `CompleteAdmissionRequest` | `AdmissionResponse` | 200, 404, 409 |
| POST | `/internal/v1/shifts/{shiftId}/cash-facts` | Payment | `CashFactRequest` | `CashFactResponse` | 201/200, 409, 422 |
| GET | `/internal/v1/shifts/{shiftId}/cash-summary` | Payment/Saga/Reporting | query `asOfFence` | `CashSummaryResponse` | 200, 404, 409 |
| POST | `/internal/v1/shifts/reconciliation-cases/{caseId}/resolve` | Operations | `ResolveCaseRequest` | `ReconciliationCaseResponse` | 200, 403, 404, 409 |

### 4.1 Transaction admission

Request:

```json
{
  "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789013",
  "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789014",
  "sourceType": "ORDER_CHECKOUT",
  "sourceId": "0192b2d0-7f1d-7abc-8d2c-123456789030",
  "transactionKind": "CASH_SALE",
  "amountMinor": 125000,
  "currency": "VND",
  "correlationId": "0192b2d0-7f1d-7abc-8d2c-123456789031"
}
```

`tenantId`/`branchId` trong body phải khớp signed workload context. `transactionKind` là `CASH_SALE`, `CASH_REFUND` hoặc `NON_CASH_CHECKOUT`. Admission trả:

```json
{
  "data": {
    "admissionId": "0192b2d0-7f1d-7abc-8d2c-123456789032",
    "shiftId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
    "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789014",
    "admissionSeq": 42,
    "admittedAt": "2026-10-13T00:04:59Z",
    "status": "ADMITTED",
    "cashAccountability": true,
    "shiftVersion": 3
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

Admission sau fence trả `409 transaction_after_close_fence`; admission trước fence giữ nguyên sequence dù message delivery đến trễ.

### 4.2 Complete admission

Request:

```json
{
  "completionStatus": "COMPLETED",
  "sourceId": "0192b2d0-7f1d-7abc-8d2c-123456789030",
  "correlationId": "0192b2d0-7f1d-7abc-8d2c-123456789031"
}
```

`completionStatus` là `COMPLETED`, `FAILED` hoặc `CANCELLED`. Cash success vẫn phải gửi `CashFactRequest`; completion không tự cộng expected cash.

### 4.3 Cash fact

Request:

```json
{
  "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789013",
  "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789014",
  "shiftId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
  "admissionId": "0192b2d0-7f1d-7abc-8d2c-123456789032",
  "sourceType": "PAYMENT",
  "sourceId": "0192b2d0-7f1d-7abc-8d2c-123456789033",
  "factKind": "CASH_COLLECTION",
  "amountMinor": 125000,
  "currency": "VND",
  "factStatus": "FINALIZED",
  "admittedAt": "2026-10-13T00:04:59Z",
  "occurredAt": "2026-10-13T00:05:00Z",
  "correlationId": "0192b2d0-7f1d-7abc-8d2c-123456789031"
}
```

`factKind` là `CASH_COLLECTION` hoặc `CASH_REFUND`. Refund amount luôn là nonnegative input và được trừ trong formula. Shift xác thực source key, admission ownership, tenant/branch, currency, status và fence sequence. Fact admitted sau fence không mutate historical summary.

### 4.4 Cash summary

Response fields:

| Field | Meaning |
|---|---|
| `openingFloatMinor` | Snapshot opening float |
| `cashCollectedMinor` | Sum accepted finalized collection facts |
| `cashRefundedMinor` | Sum accepted finalized refund facts |
| `expectedCashMinor` | Formula result |
| `sourceWatermark` | Highest admission/fact sequence included |
| `complete` | Có thể dùng để count/approve hay chưa |
| `unresolvedAdmissionCount` | Admission pre-fence chưa terminal |
| `unresolvedCaseCount` | Reconciliation cases ảnh hưởng summary |
| `calculatedAt` | UTC calculation time |
| `fenceVersion` | Fence snapshot used |

`complete=false` không được approve count. Query không đọc Payment DB; chỉ đọc facts đã ingest vào Shift.

### 4.5 Resolve reconciliation case

```json
{
  "resolution": "ACCEPT_AS_LATE_FACT",
  "note": "Đối chiếu biên bản cuối ngày, không điều chỉnh shift đã đóng",
  "externalReference": "OPS-2026-001"
}
```

Allowed resolutions: `ACCEPT_AS_LATE_FACT`, `REJECT_INVALID_FACT`, `DUPLICATE_CONFIRMED`, `NO_ACTION_REQUIRED`. Resolution không reopen hoặc mutate `CLOSED` shift; chỉ tạo correction/reconciliation record và `ShiftCashFactReconciled.v1`.

## 5. State and idempotency contract

### 5.1 State transitions

| Public status | Admission phase | Command | Result |
|---|---|---|---|
| none | — | open | `ACTIVE/OPEN` |
| `ACTIVE` | `OPEN` | close fence | `ACTIVE/CLOSING` + 202 hoặc finalizer |
| `ACTIVE` | `FROZEN` | P0 close complete | `CLOSED/FROZEN` |
| `ACTIVE` | `FROZEN` | P1 count over threshold | `PENDING_REVIEW/FROZEN` |
| `PENDING_REVIEW` | `FROZEN` | approve latest | `CLOSED/FROZEN` |
| `PENDING_REVIEW` | `FROZEN` | request recount | same state + new attempt required |
| `ACTIVE` | `OPEN` | cancel + no cash + float returned | `CANCELLED/FROZEN` |
| `CLOSED`/`CANCELLED` | `FROZEN` | lifecycle mutation | conflict; no reopen |

Internal `CLOSING` is not an open slot and does not allow new admission/open.

### 5.2 Idempotency rules

- Public mutation: `Idempotency-Key` + canonical JSON hash.
- Internal admission: source operation key plus `(sourceType, sourceId, shiftId)` dedupe.
- Cash fact: unique source key is authoritative dedupe; duplicate known source returns prior semantic result.
- Review approval: `If-Match`/review version prevents double approval; retry same key returns terminal result.
- Outbox: one event ID per committed aggregate transition; republish does not generate a new event ID.

## 6. Event contracts

### 6.1 Common envelope

```json
{
  "eventId": "0192b2d0-7f1d-7abc-8d2c-123456789040",
  "eventType": "ShiftClosed.v1",
  "eventVersion": 1,
  "occurredAt": "2026-10-13T00:05:00Z",
  "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789013",
  "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789014",
  "aggregateType": "SHIFT",
  "aggregateId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
  "aggregateVersion": 4,
  "correlationId": "0192b2d0-7f1d-7abc-8d2c-123456789031",
  "causationId": "0192b2d0-7f1d-7abc-8d2c-123456789032",
  "producer": "shift-service",
  "payload": {}
}
```

### 6.2 Event catalogue

| Event | Payload | Consumers |
|---|---|---|
| `ShiftOpened.v1` | shift, operator, opening float, business date, policy snapshot | Reporting, Audit |
| `ShiftCloseStarted.v1` | close operation, fence seq/time, actor | Reporting, Audit/ops |
| `ShiftCountSubmitted.v1` | attempt, expected/count/discrepancy, watermark | Reporting, Audit |
| `ShiftReviewRequired.v1` | reason, discrepancy/reconciliation summary, attempt ID | Reporting, Audit, ops |
| `ShiftRecountRequested.v1` | old attempt, reason, review version | Reporting, Audit |
| `ShiftClosed.v1` | terminal shift, close summary, count/review references | Reporting, Audit |
| `ShiftCancelled.v1` | cancellation reason, float return confirmation | Reporting, Audit |
| `ShiftReconciliationCaseOpened.v1` | case, source fact/admission, reason and detected time | Reporting, Audit, ops |
| `ShiftCashFactReconciled.v1` | case, source fact, resolution and operator | Reporting, Audit |

### 6.3 `ShiftOpened.v1` payload

```json
{
  "shiftId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
  "openedByUserId": "0192b2d0-7f1d-7abc-8d2c-123456789015",
  "operatorUserId": "0192b2d0-7f1d-7abc-8d2c-123456789016",
  "status": "ACTIVE",
  "openingFloatMinor": 1000000,
  "currency": "VND",
  "businessDate": "2026-10-13",
  "branchTimezone": "Asia/Ho_Chi_Minh",
  "openedAt": "2026-10-12T17:00:00Z",
  "policySnapshot": {
    "cashCountRequired": false,
    "discrepancyThresholdMinor": 0,
    "drainTimeoutMs": 5000
  },
  "reason": null
}
```

### 6.4 `ShiftClosed.v1` payload

```json
{
  "shiftId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
  "status": "CLOSED",
  "closeOperationId": "0192b2d0-7f1d-7abc-8d2c-123456789020",
  "closedByUserId": "0192b2d0-7f1d-7abc-8d2c-123456789015",
  "operatorUserId": "0192b2d0-7f1d-7abc-8d2c-123456789015",
  "closeFenceSeq": 42,
  "closedAt": "2026-10-13T00:05:00Z",
  "cashSummary": {
    "openingFloatMinor": 1000000,
    "cashCollectedMinor": 2100000,
    "cashRefundedMinor": 22500,
    "expectedCashMinor": 3077500,
    "countedCashMinor": 3077500,
    "discrepancyMinor": 0,
    "currency": "VND",
    "sourceWatermark": "admission-seq-42",
    "calculatedAt": "2026-10-13T00:05:00Z"
  },
  "countAttemptId": null,
  "reviewDecisionId": null,
  "reason": null
}
```

Event consumer requirements:

- Deduplicate by `eventId` and protect ordering by `(aggregateId, aggregateVersion)`.
- Ignore unknown future payload fields.
- Do not treat Reporting/Audit projection as source of truth for close or payment admission.
- Event replay must preserve original `eventId` and `occurredAt`.

## 7. JSON Schema primitives

```yaml
MoneyMinor:
  type: integer
  format: int64
  minimum: 0

SignedMoneyMinor:
  type: integer
  format: int64

Currency:
  type: string
  enum: [VND]

ShiftStatus:
  type: string
  enum: [ACTIVE, PENDING_REVIEW, CLOSED, CANCELLED]

AdmissionPhase:
  type: string
  enum: [OPEN, CLOSING, FROZEN]

AdmissionStatus:
  type: string
  enum: [ADMITTED, COMPLETED, FAILED, CANCELLED]

CashFactKind:
  type: string
  enum: [CASH_COLLECTION, CASH_REFUND]
```

`expectedCashMinor` và `discrepancyMinor` được server tính; client không được override trong command. `discrepancyMinor` là signed; `abs(discrepancyMinor)` dùng để so threshold.

## 8. Data contract — PostgreSQL schema

Database: PostgreSQL, owned exclusively by Shift Service. Mọi cột mới có mô tả/remark chuẩn như bảng dưới đây.

### 8.1 `shifts`

| Column | Type | Null | Description / remark |
|---|---|---:|---|
| `id` | UUID | no | UUID v7 định danh shift |
| `tenant_id` | UUID | no | Tenant sở hữu shift |
| `branch_id` | UUID | no | Branch sử dụng một cash drawer |
| `status` | TEXT | no | `ACTIVE`, `PENDING_REVIEW`, `CLOSED`, `CANCELLED` |
| `admission_phase` | TEXT | no | Internal `OPEN`, `CLOSING`, `FROZEN` |
| `opened_by_user_id` | UUID | no | Actor thực hiện open command |
| `operator_user_id` | UUID | no | Cashier chịu trách nhiệm effective |
| `opening_float_minor` | BIGINT | no | Tiền đầu ca, VND integer, nonnegative |
| `currency` | TEXT | no | Currency snapshot; P0 `VND` |
| `business_date` | DATE | no | Ngày kinh doanh theo branch timezone |
| `branch_timezone` | TEXT | no | IANA timezone snapshot lúc mở ca |
| `opened_at` | TIMESTAMPTZ | no | Thời điểm mở ca UTC |
| `close_operation_id` | UUID | yes | Operation id của close hiện tại/đã hoàn tất |
| `close_fence_seq` | BIGINT | yes | Admission sequence cuối cùng được phép hoàn tất |
| `close_fence_at` | TIMESTAMPTZ | yes | Thời điểm fence commit theo DB clock |
| `close_fence_version` | BIGINT | yes | Version dùng để bảo vệ close operation |
| `closed_by_user_id` | UUID | yes | Actor thực hiện close/approve |
| `closed_at` | TIMESTAMPTZ | yes | Thời điểm terminal close UTC |
| `cancelled_by_user_id` | UUID | yes | Actor hủy abandoned shift |
| `cancelled_at` | TIMESTAMPTZ | yes | Thời điểm cancel UTC |
| `cancellation_reason` | TEXT | yes | Lý do cancel đã sanitize |
| `opening_float_returned_minor` | BIGINT | yes | Số tiền xác nhận trả lại khi cancel |
| `review_reason` | TEXT | yes | Reason `PENDING_REVIEW` |
| `review_version` | BIGINT | no | Optimistic version cho review/recount |
| `latest_count_attempt_id` | UUID | yes | Attempt mới nhất |
| `policy_snapshot` | JSONB | no | Threshold/count/drain policy áp dụng cho shift |
| `version` | BIGINT | no | Aggregate version monotonic |
| `created_at` | TIMESTAMPTZ | no | Thời điểm insert |
| `updated_at` | TIMESTAMPTZ | no | Thời điểm update cuối |

Constraints/indexes:

- PK `shifts_pkey(id)`.
- `CHECK (opening_float_minor >= 0)`.
- `CHECK (status IN (...))` và `CHECK (admission_phase IN (...))`.
- Partial unique index `uq_shifts_open_slot` trên `(tenant_id, branch_id)` khi status in `ACTIVE`, `PENDING_REVIEW`.
- Index `ix_shifts_branch_history` trên `(tenant_id, branch_id, business_date DESC, created_at DESC, id DESC)`.
- Không hard-delete shift.

### 8.2 `shift_transaction_admissions`

| Column | Type | Null | Description / remark |
|---|---|---:|---|
| `id` | UUID | no | Opaque admission ID, UUID v7 |
| `tenant_id` | UUID | no | Tenant từ signed workload context |
| `branch_id` | UUID | no | Branch admission |
| `shift_id` | UUID | no | Shift target; opaque cross-service reference |
| `source_type` | TEXT | no | `ORDER_CHECKOUT`, `PAYMENT`, `REFUND` |
| `source_id` | UUID | no | Order/payment/refund source ID |
| `transaction_kind` | TEXT | no | `CASH_SALE`, `CASH_REFUND`, `NON_CASH_CHECKOUT` |
| `amount_minor` | BIGINT | no | Amount snapshot relevant to source |
| `currency` | TEXT | no | Currency snapshot |
| `admission_seq` | BIGINT | no | Monotonic order within shift |
| `admitted_at` | TIMESTAMPTZ | no | DB clock timestamp at admission |
| `status` | TEXT | no | `ADMITTED`, `COMPLETED`, `FAILED`, `CANCELLED` |
| `cash_accountability` | BOOLEAN | no | Có ảnh hưởng cash summary hay không |
| `completed_at` | TIMESTAMPTZ | yes | Thời điểm completion terminal |
| `completion_source_id` | UUID | yes | Source xác nhận completion |
| `correlation_id` | UUID | no | Trace/correlation ID |
| `created_at` | TIMESTAMPTZ | no | Thời điểm insert |
| `updated_at` | TIMESTAMPTZ | no | Thời điểm update trạng thái |

Constraints/indexes:

- Unique `(tenant_id, shift_id, source_type, source_id)`.
- Unique `(shift_id, admission_seq)`.
- Index pending drain `(tenant_id, shift_id, status, admission_seq)`.
- `CHECK (amount_minor >= 0)`.
- Completion transition chỉ từ `ADMITTED` sang một terminal status.

### 8.3 `cash_facts`

| Column | Type | Null | Description / remark |
|---|---|---:|---|
| `id` | UUID | no | UUID v7 cash fact ID |
| `tenant_id` | UUID | no | Tenant fact |
| `branch_id` | UUID | no | Branch fact |
| `shift_id` | UUID | no | Shift accountability target |
| `admission_id` | UUID | no | Admission trước fence |
| `source_type` | TEXT | no | Payment/refund producer type |
| `source_id` | UUID | no | Stable payment/refund event ID |
| `fact_kind` | TEXT | no | `CASH_COLLECTION` hoặc `CASH_REFUND` |
| `amount_minor` | BIGINT | no | Nonnegative amount; refund trừ trong formula |
| `currency` | TEXT | no | Currency fact |
| `fact_status` | TEXT | no | `FINALIZED` only for summary; rejected facts are cases |
| `admission_seq` | BIGINT | no | Copied trusted fence ordering |
| `admitted_at` | TIMESTAMPTZ | no | Trusted Shift admission time |
| `occurred_at` | TIMESTAMPTZ | no | Producer business occurrence |
| `received_at` | TIMESTAMPTZ | no | Shift ingest time |
| `correlation_id` | UUID | no | Trace/correlation ID |
| `created_at` | TIMESTAMPTZ | no | Insert time |

Constraints/indexes:

- Unique `(tenant_id, shift_id, source_type, source_id, fact_kind)`.
- Index summary `(tenant_id, shift_id, admission_seq, fact_kind)`.
- `CHECK (amount_minor >= 0)` and currency `VND` for P0.
- No update to amount/source identity; invalid correction is a reconciliation case.

### 8.4 `cash_count_attempts`

| Column | Type | Null | Description / remark |
|---|---|---:|---|
| `id` | UUID | no | UUID v7 attempt ID |
| `tenant_id` | UUID | no | Tenant owner |
| `branch_id` | UUID | no | Branch owner |
| `shift_id` | UUID | no | Shift being counted |
| `attempt_number` | INTEGER | no | 1-based immutable sequence |
| `counted_cash_minor` | BIGINT | no | Physical count submitted by cashier |
| `opening_float_minor` | BIGINT | no | Opening float snapshot |
| `cash_collected_minor` | BIGINT | no | Accepted collection snapshot |
| `cash_refunded_minor` | BIGINT | no | Accepted refund snapshot |
| `expected_cash_minor` | BIGINT | no | Formula output |
| `discrepancy_minor` | BIGINT | no | Counted minus expected |
| `threshold_minor` | BIGINT | no | Policy snapshot |
| `fence_version` | BIGINT | no | Close fence used |
| `source_watermark` | TEXT | no | Admission/fact watermark |
| `status` | TEXT | no | `SUBMITTED`, `RECOUNT_REQUIRED`, `APPROVED` |
| `note` | TEXT | yes | Required for over-threshold/unresolved case |
| `counted_by_user_id` | UUID | no | Count actor |
| `submitted_at` | TIMESTAMPTZ | no | Immutable submit time |
| `reviewed_by_user_id` | UUID | yes | Approver |
| `reviewed_at` | TIMESTAMPTZ | yes | Approval/recount review time |
| `decision_note` | TEXT | yes | Manager/Owner decision note |
| `created_at` | TIMESTAMPTZ | no | Insert time |

Constraints/indexes:

- Unique `(shift_id, attempt_number)`.
- Index latest `(tenant_id, shift_id, attempt_number DESC)`.
- `CHECK (counted_cash_minor >= 0)` and all unsigned inputs nonnegative.
- Generated/application invariant `discrepancy_minor = counted_cash_minor - expected_cash_minor`.
- Submitted monetary fields are immutable.

### 8.5 `reconciliation_cases`

| Column | Type | Null | Description / remark |
|---|---|---:|---|
| `id` | UUID | no | UUID v7 case ID |
| `tenant_id` | UUID | no | Tenant case |
| `branch_id` | UUID | no | Branch case |
| `shift_id` | UUID | no | Related shift |
| `case_type` | TEXT | no | `LATE_FACT`, `UNKNOWN_FACT`, `MISMATCH`, `UNRESOLVED_ADMISSION` |
| `source_type` | TEXT | yes | Producer/source category |
| `source_id` | UUID | yes | Related payment/refund/admission |
| `amount_minor` | BIGINT | yes | Amount under investigation |
| `currency` | TEXT | yes | Currency under investigation |
| `reason` | TEXT | no | Machine-readable reason |
| `status` | TEXT | no | `OPEN`, `RESOLVED`, `REJECTED` |
| `resolution` | TEXT | yes | Authorized resolution code |
| `resolution_note` | TEXT | yes | Operator explanation |
| `resolved_by_user_id` | UUID | yes | Actor resolving case |
| `resolved_at` | TIMESTAMPTZ | yes | Resolution time |
| `correlation_id` | UUID | no | Trace/correlation ID |
| `created_at` | TIMESTAMPTZ | no | Case creation time |

Constraints/indexes:

- Index open cases `(tenant_id, branch_id, status, created_at)`.
- Unique active case key for same source/reason to avoid case fan-out.
- Resolution never deletes or mutates original cash fact.

### 8.6 `shift_policies`

| Column | Type | Null | Description / remark |
|---|---|---:|---|
| `id` | UUID | no | Policy ID |
| `tenant_id` | UUID | no | Tenant owner |
| `branch_id` | UUID | yes | Null means tenant default; non-null branch override |
| `cash_count_required` | BOOLEAN | no | P1 count required at close |
| `discrepancy_threshold_minor` | BIGINT | no | Absolute allowed discrepancy |
| `drain_timeout_ms` | INTEGER | no | Bounded pre-fence admission drain |
| `effective_from` | TIMESTAMPTZ | no | Policy start UTC |
| `effective_to` | TIMESTAMPTZ | yes | Policy end UTC |
| `updated_by_user_id` | UUID | no | Policy actor |
| `created_at` | TIMESTAMPTZ | no | Insert time |
| `updated_at` | TIMESTAMPTZ | no | Last update |

`discrepancy_threshold_minor >= 0`, `drain_timeout_ms` nằm trong safe operational range. Effective policy được snapshot vào `shifts.policy_snapshot` lúc open.

### 8.7 Supporting tables

`outbox_messages`, `idempotency_records` và `audit_references` tuân theo parent platform schema, bổ sung Shift-specific aggregate/resource columns:

| Table | Required Shift-specific columns and descriptions |
|---|---|
| `outbox_messages` | `aggregate_type` — loại aggregate; `aggregate_id` — shift/case ID; `aggregate_version` — thứ tự event; `event_type` — event name; `payload` — immutable JSON envelope; `published_at` — delivery time; `attempt_count` — retry count; `next_attempt_at` — backoff schedule |
| `idempotency_records` | `tenant_id` — ownership; `operation` — command name; `idempotency_key` — retry key; `request_hash` — canonical payload hash; `response_status` — saved HTTP status; `response_body` — safe semantic result; `resource_id` — created resource; `expires_at` — retention boundary |
| `audit_references` | `tenant_id` — ownership; `branch_id` — branch scope; `shift_id` — related shift; `action` — command/action; `actor_user_id` — JWT actor; `operator_user_id` — effective cashier; `reason` — required override/cancel/review reason; `before_state`/`after_state` — state snapshots; `correlation_id` — trace; `created_at` — immutable audit time |

## 9. Required amendments to Order and Payment contracts

### 9.1 Order contract amendment

`CreateOrderRequest` must add:

| Field | Type | Required | Rule |
|---|---|---:|---|
| `shiftId` | UUID | yes | Must be `ACTIVE`, `admissionPhase=OPEN`, same tenant/branch |

`OrderResponse` must return `shiftId`. `CheckoutRequest` must retain/check the same shift context and reject if Shift admission cannot be obtained. Order/Saga stores `admissionId` in checkout attempt context but does not let public client provide it.

Before Payment initiation, Saga calls:

```text
Shift.createAdmission(
  tenantId, branchId, shiftId,
  sourceType=ORDER_CHECKOUT,
  sourceId=orderId/checkoutAttemptId,
  transactionKind=CASH_SALE|NON_CASH_CHECKOUT,
  amountMinor, currency
)
```

Saga forwards the returned `admissionId`, `admissionSeq` and `admittedAt` to Payment. Retry uses the same source/idempotency key.

### 9.2 Payment contract amendment

`InitiatePaymentRequest` must add/require:

```json
{
  "tenantId": "...",
  "branchId": "...",
  "shiftId": "...",
  "admissionId": "...",
  "admissionSeq": 42,
  "admittedAt": "2026-10-13T00:04:59Z"
}
```

Rules:

- `tenantId`, `branchId`, `shiftId` must match signed Saga context and Order snapshot.
- `admissionId`, `admissionSeq`, `admittedAt` must match Shift response; Payment cannot fabricate them.
- `admissionId` is required for cash sale/refund and optional only for non-cash flows where the Saga admission is marked non-cash.
- Payment never infers shift from branch/time.

`PaymentPaid.v1` payload must add:

```json
{
  "tenantId": "...",
  "branchId": "...",
  "shiftId": "...",
  "admissionId": "...",
  "admissionSeq": 42,
  "admittedAt": "2026-10-13T00:04:59Z",
  "sourceId": "payment-id",
  "amountMinor": 125000,
  "currency": "VND",
  "status": "PAID",
  "paidAt": "2026-10-13T00:05:00Z"
}
```

`PaymentRefunded.v1` must carry the same tenant/branch/shift/admission metadata, with `refundId`, `amountMinor`, `status=REFUNDED`, `refundedAt` and `sourceId`. A cash refund is valid for Shift summary only when its admission was granted before the fence and the refund is finalized.

### 9.3 Parent event compatibility

- Existing consumers must ignore new optional fields in `PaymentPaid.v1` during rollout.
- Shift consumers must deduplicate by `eventId` and accept `aggregateVersion`.
- Parent `shifts` schema must be expanded from basic open/close fields to the Shift-owned model; the parent document remains a platform summary, not a second source of truth.
- Parent event catalogue must include `ShiftReviewRequired.v1`, `ShiftRecountRequested.v1`, `ShiftCancelled.v1`, `ShiftReconciliationCaseOpened.v1` and `ShiftCashFactReconciled.v1`.

## 10. Migration and rollout contract

Migration order:

1. Create supporting `shift_policies`, `shift_transaction_admissions`, `cash_facts`, `cash_count_attempts`, `reconciliation_cases` tables with remarks, constraints and indexes.
2. Extend `shifts` with status/phase/fence/review/policy snapshot columns; backfill existing active rows as `status=ACTIVE`, `admission_phase=OPEN`, `review_version=1` only after verifying one active row per branch.
3. Add partial unique active-slot index and source/idempotency unique constraints.
4. Deploy Shift APIs accepting new fields while preserving parent basic response fields.
5. Deploy Order/Saga to require/store `shiftId` and admission metadata.
6. Deploy Payment to persist/emit shift/admission metadata and send cash facts.
7. Enable P1 count policy by tenant/branch after fixtures pass; default policy remains P0 close without count.

Compatibility rules:

- Never create a cash fact without `shiftId` + valid `admissionId` after enforcement begins.
- During dual-read rollout, old Payment events without shift metadata are routed to reconciliation, never guessed into a shift.
- Rollback of application code must not drop columns or disable unique constraints; schema is forward-compatible.
- Backfill/repair is an audited Operations command, not direct SQL from POS.

## 11. Contract test matrix

| Area | Required fixtures |
|---|---|
| Open | concurrent open; idempotent retry; wrong tenant/branch; on-behalf reason |
| Admission | before fence accepted; after fence rejected; same source deduped; foreign shift rejected |
| Close | 200 terminal; 202 processing; drain completion; timeout to review; duplicate close |
| Count | integer formula; zero threshold; negative/overflow; missing count policy; immutable attempt |
| Review | approve latest; stale If-Match; request recount; approve only Manager/Owner |
| Cancel | no cash + full float return; incomplete return; any admitted cash rejected |
| Cash facts | collection/refund; duplicate; late; unknown; mismatch; wrong currency |
| Events | outbox atomicity; duplicate delivery; event ordering; DLQ/replay |
| Isolation | cross-tenant IDOR; wrong branch; no resource existence leakage |
| Integration | Order shift propagation; PaymentPaid/Refunded metadata; Saga retry/admission reuse |

## 12. Contract approval gate

- **Current status:** Approved
- **Required before implementation:** Generate OpenAPI/AsyncAPI/migrations and synchronize parent Order/Payment/platform documents.
- **Implementation prerequisite:** Sinh OpenAPI 3.1 + AsyncAPI/JSON Schema + migration scripts từ tài liệu này; chưa viết controller/DTO trước khi contracts được approve.

