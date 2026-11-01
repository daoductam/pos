# Module 4 — Payment service: API & Data Contracts

**Ngày:** 2026-10-12  
**Trạng thái:** Approved  
**Phạm vi:** M — Standard Service Flow  
**Module:** Thanh toán (Payment service)

## 1. Contract conventions

### 1.1 API-first

- REST contracts versioned bằng `/api/v1`, `/internal/v1` và `/callbacks/v1`.
- OpenAPI là nguồn sinh client cho POS/internal consumers.
- AsyncAPI/event schema là nguồn sinh consumer contract.
- VNPAY-specific fields chỉ xuất hiện ở callback/provider adapter boundary, không xuất hiện trong Payment domain command.
- Breaking change dùng major version mới; optional field mới phải backward-compatible.

### 1.2 Headers

| Header | Required | Scope | Meaning |
|---|---:|---|---|
| `Authorization` | User API | Cognito | `Bearer <Cognito access token>` |
| `Authorization` | Internal API | Workload identity | Service credential/mTLS hoặc signed workload context |
| `X-Active-Branch-Id` | Branch-scoped user API | User membership | Branch được chọn; server phải validate membership |
| `X-Correlation-Id` | Recommended | All | Correlates API, Saga, audit, outbox và downstream calls |
| `X-Causation-Id` | Conditional | Internal/event callback | References command/event gây ra operation hiện tại |
| `Idempotency-Key` | Side-effect command | Tenant + actor/workload + operation | Retry key; canonical request hash phải giữ nguyên |
| `Content-Type` | Body API | REST | `application/json; charset=utf-8` |

Client không được cung cấp role, actor, tenant ownership hoặc branch ownership trong body để thay thế auth context.

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
    "code": "provider_amount_mismatch",
    "message": "Provider amount does not match the payment attempt",
    "details": [
      {
        "field": "amountMinor",
        "code": "amount_mismatch",
        "message": "The provider amount is not accepted"
      }
    ],
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

Rules:

- `message` không chứa SQL, JWT, provider secret, raw signature, raw callback hoặc unnecessary PII.
- Cross-tenant/branch resource không tồn tại trong authorized scope trả `404`, không tiết lộ existence.
- Validation details chỉ trả field/code an toàn.

### 1.5 HTTP/error codes

| HTTP | Code | Meaning | Retryable |
|---:|---|---|---:|
| `200` | — | Read success hoặc cash command đã hoàn tất | no |
| `202` | `accepted` | VNPAY initiation/reconciliation đã enqueue, chưa final | yes/status query |
| `400` | `validation_error` | Malformed request/schema/unsupported field | no |
| `401` | `unauthorized` | Missing/invalid/expired authentication | after auth refresh |
| `403` | `forbidden` | Role, tenant hoặc branch scope không đủ | no |
| `404` | `not_found` | Resource không tồn tại trong authorized scope | no |
| `409` | `idempotency_conflict` | Cùng key nhưng request hash khác | no |
| `409` | `payment_already_pending` | Đã có VNPAY attempt pending active | no; return existing payment |
| `409` | `payment_already_terminal` | Attempt đã terminal và command mâu thuẫn | no |
| `409` | `payment_reconciliation_required` | Side effect cần operator reconciliation | no automatic retry |
| `409` | `split_tender_exceeds_order_total` | P1 allocation vượt amount due | no |
| `422` | `invalid_cash_amount` | Cash received nhỏ hơn amount due | no |
| `422` | `provider_mapping_mismatch` | Provider/order/payment mapping sai | no |
| `422` | `provider_amount_mismatch` | Provider amount sai | no |
| `422` | `invalid_provider_signature` | Checksum/signature không hợp lệ | provider policy |
| `429` | `rate_limited` | Gateway/service/provider limit | bounded backoff |
| `503` | `provider_unavailable` | Provider unavailable/timeout, chưa kết luận failed | yes |
| `503` | `service_unavailable` | Payment dependency unavailable | yes |

### 1.6 Idempotency

- Bắt buộc `Idempotency-Key` cho initiate payment, expire/cancel command, reconciliation command và P1 refund.
- Key scope: `(tenantId, actorOrWorkloadId, operation)`. Với Saga, task key chứa `(tenantId, orderId, checkoutAttemptId, taskName)`.
- Lưu canonical request hash, original response/status và expiry.
- Cùng key + cùng hash trả lại kết quả gốc.
- Cùng key + hash khác trả `409 idempotency_conflict`.
- Unique constraint/transaction lock là lớp bảo vệ cuối cùng; không dựa vào memory cache.

### 1.7 Pagination/filtering

P1 history/reconciliation collections dùng cursor pagination:

```text
GET /api/v1/payments?limit=20&cursor=<opaque>&status=PAID&method=VNPAY_QR
```

- `limit` mặc định `20`, tối đa `100`.
- Sort ổn định `(createdAt DESC, id DESC)`.
- Cursor opaque, không do client tự sửa.
- Scope predicates và allowlisted filters áp dụng trước cursor.

Response:

```json
{
  "data": [],
  "meta": {
    "hasMore": true,
    "nextCursor": "eyJjcmVhdGVkQXQiOi...",
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

Không cache P0 payment state/read response. Read model/report P1 chỉ cache khi có freshness timestamp và invalidation policy.

## 2. Endpoint catalogue

| Method | Path | Auth/scope | Request | Response | Status |
|---|---|---|---|---|---|
| `POST` | `/internal/v1/orders/{orderId}/payments` | Order/Saga workload identity | `InitiatePaymentRequest` | `PaymentResponse` | `200` cash, `202` VNPAY, `409`, `422`, `503` |
| `GET` | `/api/v1/payments/{paymentId}` | Cognito + tenant/branch scope | — | `PaymentResponse` | `200`, `403`, `404` |
| `GET` | `/api/v1/payments` | Manager/Owner + scope | cursor/filter query | `PaymentPage` | `200`, `403`, `422` |
| `GET` | `/callbacks/v1/vnpay/ipn` | Gateway/provider boundary | VNPAY query parameters | `VnpayIpnResponse` | `200` provider-compatible |
| `POST` | `/internal/v1/payments/reconciliation/run` | Scheduler/operator workload identity | `ReconciliationRunRequest` | `ReconciliationRunResponse` | `202`, `409`, `503` |
| `GET` | `/api/v1/reconciliations/{reconciliationId}` | Manager/Owner/operator scope | — | `ReconciliationResponse` | `200`, `403`, `404` |
| `POST` | `/api/v1/payments/{paymentId}/refunds` | Manager/Owner + scope | `CreateRefundRequest` | `RefundResponse` | `201/202`, `403`, `409`, `422` P1 |
| `GET` | `/api/v1/refunds/{refundId}` | Manager/Owner + scope | — | `RefundResponse` | `200`, `403`, `404` P1 |

### 2.1 Initiate payment

`POST /internal/v1/orders/{orderId}/payments`

Trusted `tenantId`, `branchId`, `actor/workload` và order ownership đến từ service authentication/signed context. Body fields không phải source of truth độc lập.

Request:

```json
{
  "sagaId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
  "checkoutAttemptId": "0192b2d0-7f1d-7abc-8d2c-123456789013",
  "paymentMethod": "CASH",
  "amountDueMinor": 125000,
  "currency": "VND",
  "amountReceivedMinor": 150000,
  "orderSnapshotVersion": 7,
  "providerOrderRef": null,
  "returnUrl": null,
  "correlationId": "0192b2d0-7f1d-7abc-8d2c-123456789014"
}
```

Field rules:

| Field | Required | Rule |
|---|---:|---|
| `sagaId` | yes | Step Functions execution reference |
| `checkoutAttemptId` | yes | Order-owned immutable attempt reference |
| `paymentMethod` | yes | `CASH` or `VNPAY_QR` P0 |
| `amountDueMinor` | yes | Non-negative integer, must equal trusted Order snapshot |
| `currency` | yes | `VND` P0 |
| `amountReceivedMinor` | CASH only | Integer ≥ `amountDueMinor`; absent for VNPAY |
| `orderSnapshotVersion` | yes | Version used by Order/Saga |
| `providerOrderRef` | VNPAY only | Stable merchant reference, generated/validated by Payment |
| `returnUrl` | VNPAY only | Allowlisted URL; never auth signal |
| `correlationId` | yes | Propagated trace context |

`Idempotency-Key` is a header and is not duplicated in body.

Cash success response (`200`):

```json
{
  "data": {
    "paymentId": "0192b2d0-7f1d-7abc-8d2c-123456789015",
    "orderId": "0192b2d0-7f1d-7abc-8d2c-123456789016",
    "checkoutAttemptId": "0192b2d0-7f1d-7abc-8d2c-123456789013",
    "sagaId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
    "paymentMethod": "CASH",
    "paymentStatus": "PAID",
    "amountDueMinor": 125000,
    "amountReceivedMinor": 150000,
    "cashChangeMinor": 25000,
    "currency": "VND",
    "provider": null,
    "providerOrderRef": null,
    "providerTransactionId": null,
    "qrPayload": null,
    "expiresAt": null,
    "paidAt": "2026-10-12T07:00:00Z",
    "reconciliationRequired": false,
    "lateSettlementStatus": null
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

VNPAY initiation response (`202`):

```json
{
  "data": {
    "paymentId": "0192b2d0-7f1d-7abc-8d2c-123456789017",
    "orderId": "0192b2d0-7f1d-7abc-8d2c-123456789016",
    "checkoutAttemptId": "0192b2d0-7f1d-7abc-8d2c-123456789013",
    "sagaId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
    "paymentMethod": "VNPAY_QR",
    "paymentStatus": "PENDING",
    "amountDueMinor": 125000,
    "currency": "VND",
    "provider": "VNPAY",
    "providerOrderRef": "POS-20261012-000123-A1",
    "providerTransactionId": null,
    "qrPayload": "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?...",
    "expiresAt": "2026-10-12T07:15:00Z",
    "paidAt": null,
    "reconciliationRequired": false,
    "lateSettlementStatus": null
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

### 2.2 Read payment

`GET /api/v1/payments/{paymentId}`

- Cognito JWT và server-side tenant/branch check bắt buộc.
- Trả current state, immutable amounts, provider references, expiry, reconciliation flag và safe transition summary.
- Không trả raw callback, secret, raw checksum hoặc stack trace.
- Payment terminal `EXPIRED/CANCELLED` có thể có `lateSettlementStatus=SETTLED_LATE` nếu reconciliation record tồn tại; không đổi `paymentStatus`.

### 2.3 Payment history

`GET /api/v1/payments`

Allowlisted filters:

- `cursor`, `limit`;
- `from`, `to` theo branch business timezone;
- `branchId` nếu Owner hoặc assigned scope cho phép;
- `orderId`, `paymentId`, `providerTransactionId`;
- `paymentMethod`, `paymentStatus`, `cashierId`;
- `reconciliationRequired`, `refundStatus`.

Manager chỉ đọc branch scope. Owner có thể đọc tenant scope và phải chọn branch khi endpoint/policy yêu cầu branch-specific view.

### 2.4 VNPAY IPN callback

`GET /callbacks/v1/vnpay/ipn`

External VNPAY contract là query string `vnp_*`; gateway phải preserve raw query/canonical input để adapter verify checksum. Normalized domain callback không expose provider field names.

Required/optional provider fields:

| Provider field | Required | Normalized meaning |
|---|---:|---|
| `vnp_TmnCode` | yes | Merchant terminal identifier |
| `vnp_Amount` | yes | Provider amount, scaled by 100; adapter normalizes to VND minor unit |
| `vnp_BankCode` | yes | Payment bank/wallet code |
| `vnp_BankTranNo` | no | Bank transaction reference |
| `vnp_CardType` | no | Provider payment type; may include QR |
| `vnp_PayDate` | no | Provider payment time, GMT+7 format |
| `vnp_OrderInfo` | yes | Provider order description |
| `vnp_TransactionNo` | yes | Provider transaction ID |
| `vnp_ResponseCode` | yes | Provider payment response code |
| `vnp_TransactionStatus` | yes | Provider transaction status |
| `vnp_TxnRef` | yes | Merchant order/payment reference |
| `vnp_SecureHashType` | no | Configured hash algorithm indicator |
| `vnp_SecureHash` | yes | Provider checksum |

IPN processing contract:

1. Validate query shape/size and capture raw metadata.
2. Verify `vnp_SecureHash` before DB/business processing.
3. Resolve `vnp_TxnRef` to payment attempt and verify tenant/branch/provider mapping.
4. Convert `vnp_Amount / 100` according to provider contract and compare exact VND minor amount.
5. Check `vnp_ResponseCode` and `vnp_TransactionStatus`.
6. Dedupe by provider event key/transaction reference and payload hash.
7. Apply only allowed transition; terminal conflict becomes reconciliation record/no-op.
8. Return provider-compatible JSON.

Response:

```json
{
  "RspCode": "00",
  "Message": "Confirm Success"
}
```

The provider contract uses `RspCode=00`/`02` for accepted update and retry-oriented codes for invalid/unprocessed cases. Exact mapping stays in the VNPAY adapter and is covered by fixtures; internal errors never appear in `Message`.

### 2.5 Reconciliation run

`POST /internal/v1/payments/reconciliation/run`

Request:

```json
{
  "paymentIds": [],
  "providerTransactionIds": [],
  "reason": "PENDING_TIMEOUT_SCAN",
  "maxItems": 100,
  "correlationId": "0192b2d0-7f1d-7abc-8d2c-123456789020"
}
```

- Empty target lists mean scheduler-selected eligible items within workload scope.
- `maxItems` is capped at `1000`.
- Response `202` means run accepted/enqueued, not that payment state is resolved.
- `409` is reserved for duplicate/conflicting command or active run conflict, not provider unknown.

Response:

```json
{
  "data": {
    "reconciliationRunId": "0192b2d0-7f1d-7abc-8d2c-123456789021",
    "status": "QUEUED",
    "acceptedCount": 12
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

### 2.6 Refund P1

`POST /api/v1/payments/{paymentId}/refunds`

Request:

```json
{
  "amountMinor": 50000,
  "reason": "CUSTOMER_REQUEST",
  "correlationId": "0192b2d0-7f1d-7abc-8d2c-123456789022"
}
```

- Requires Manager/Owner scope and `Idempotency-Key`.
- Payment must be `PAID`.
- `amountMinor` must be positive and not exceed refundable balance.
- Refund record is independent from original payment attempt.
- VNPAY refund request is sent by adapter as provider-specific POST; provider response is normalized to refund state.

## 3. State and command contracts

### 3.1 Payment attempt state

```text
PENDING
 ├─ cashValidated              → PAID
 ├─ verifiedProviderSuccess    → PAID
 ├─ providerDefinitiveFailure  → FAILED
 ├─ providerExpiry/localClose  → EXPIRED
 └─ authorizedCancel           → CANCELLED
```

Rules:

- `PAID`, `FAILED`, `EXPIRED`, `CANCELLED` are terminal.
- Same-state duplicate is a no-op returning previous result.
- Conflicting callback after terminal does not overwrite state.
- Verified provider success after terminal creates `late_payment_reconciliation(SETTLED_LATE)` and emits only `PaymentReconciliationRequired`.
- Provider unknown keeps `PENDING` with reconciliation flag until a definitive decision or operator resolution.

### 3.2 Normalized provider port

```text
createDynamicQr(CreateQrCommand) -> ProviderQrCreated
verifyCallback(RawProviderCallback) -> VerifiedProviderCallback | InvalidCallback
queryStatus(QueryProviderStatusCommand) -> ProviderStatusResult
refund(RefundProviderCommand) -> ProviderRefundResult       # P1
```

Normalized callback result contains:

| Field | Rule |
|---|---|
| `provider` | `VNPAY` |
| `providerOrderRef` | Maps to one payment attempt |
| `providerTransactionId` | Unique within provider/tenant when present |
| `amountMinor` | Exact VND minor unit after adapter normalization |
| `providerStatus` | Provider-neutral `SUCCESS`, `FAILED`, `EXPIRED`, `UNKNOWN` |
| `signatureValid` | Must be true before domain processing |
| `providerOccurredAt` | Original provider time if available |
| `payloadHash` | Canonical raw payload digest |
| `providerEventKey` | Stable dedupe key |

## 4. Event contracts

### 4.1 Event envelope

Canonical wire fields use `eventType` + `eventVersion`; documentation shorthand `PaymentPaid.v1` means `eventType=PaymentPaid`, `eventVersion=1`.

```json
{
  "eventId": "0192b2d0-7f1d-7abc-8d2c-123456789023",
  "eventType": "PaymentPaid",
  "eventVersion": 1,
  "occurredAt": "2026-10-12T07:00:00Z",
  "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789024",
  "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789025",
  "aggregateType": "Payment",
  "aggregateId": "0192b2d0-7f1d-7abc-8d2c-123456789015",
  "correlationId": "0192b2d0-7f1d-7abc-8d2c-123456789014",
  "causationId": "0192b2d0-7f1d-7abc-8d2c-123456789026",
  "producer": "payment-service",
  "payload": {}
}
```

Envelope rules:

- `eventId` globally unique; consumer dedupe key.
- `eventType`/`eventVersion` immutable after publish.
- `tenantId` mandatory; `branchId` mandatory for branch-scoped payment.
- Consumer acknowledges only after local transaction commits.
- Unknown event version is quarantined/DLQ, not interpreted as an older version.
- Event delivery is at-least-once.

### 4.2 Payment event catalogue

| Event | Producer → consumers | Payload rules |
|---|---|---|
| `PaymentInitiated.v1` | Payment → Saga, Audit | `paymentId`, `orderId`, `checkoutAttemptId`, `method`, `amountMinor`, `currency`, `expiresAt` nullable for CASH |
| `PaymentPaid.v1` | Payment → Saga, Audit | `paymentId`, `orderId`, `method`, `amountMinor`, `currency`, `paidAt`; provider fields nullable for CASH; cash received/change optional. Saga then calls Inventory commit. |
| `PaymentFailed.v1` | Payment → Saga, Order, Audit | `paymentId`, `orderId`, `reasonCode`, `retryable`, `failedAt` |
| `PaymentExpired.v1` | Payment → Saga, Order, Audit | `paymentId`, `orderId`, `expiredAt`, `reasonCode` |
| `PaymentCancelled.v1` | Payment → Saga, Order, Audit | `paymentId`, `orderId`, `cancelledAt`, `reasonCode`, actor type/id |
| `PaymentReconciliationRequired.v1` | Payment → Saga, Order, Audit, Operations | `paymentId`, `orderId`, `reconciliationId`, `reasonCode`, `settlementStatus`, `amountMinor`, `occurredAt` |

`PaymentReconciliationRequired.v1` with `settlementStatus=SETTLED_LATE` is not a paid-order fact. It must not trigger Inventory commit, Loyalty accrual, Reporting revenue or `OrderPaid.v1`.

`OrderPaid.v1` remains an Order-owned event emitted only after Payment success, Inventory commit and Order finalization.

### 4.3 Routing/retry

| Event route | Consumer | Retry/DLQ |
|---|---|---|
| `PaymentPaid`, `PaymentFailed`, `PaymentExpired`, `PaymentCancelled` | Saga/Order callback | bounded retry, then `pos-saga-events` DLQ |
| All Payment events | Audit | durable retry/DLQ |
| `PaymentReconciliationRequired` | Operations/reconciliation | alert + manual queue; replay requires authorization |

## 5. Data contracts

### 5.1 Common database conventions

- PostgreSQL types: `UUID`, `TEXT`, `BIGINT`, `INTEGER`, `BOOLEAN`, `JSONB`, `TIMESTAMPTZ`.
- UUIDs are UUID v7.
- Money values are `BIGINT` minor units; VNPAY adapter divides provider-scaled amount before persistence.
- All timestamps are UTC `TIMESTAMPTZ`.
- Business date is derived from branch IANA timezone; browser timezone is never authoritative.
- Every migration must include a descriptive `COMMENT ON COLUMN` for every new column.
- Sensitive raw payload is encrypted/minimized; secrets and raw signature inputs are never logged.

### 5.2 `payment_attempts`

| Column | Type | Constraints | Description/comment |
|---|---|---|---|
| `id` | UUID | PK | Internal payment attempt identifier. |
| `tenant_id` | UUID | NOT NULL, indexed | Owning tenant scope. |
| `branch_id` | UUID | NOT NULL, indexed | Branch where the Order/payment was created. |
| `order_id` | UUID | NOT NULL, indexed | Opaque Order reference; no cross-service FK. |
| `checkout_attempt_id` | UUID | NOT NULL, indexed | Immutable Order checkout attempt reference. |
| `saga_id` | UUID | NOT NULL, indexed | Step Functions/Saga execution reference. |
| `method` | TEXT | CHECK `CASH`/`VNPAY_QR` | Payment method. |
| `status` | TEXT | CHECK state enum | `PENDING`, `PAID`, `FAILED`, `EXPIRED`, `CANCELLED`. |
| `amount_due_minor` | BIGINT | NOT NULL, CHECK >= 0 | Immutable amount expected from trusted Order snapshot. |
| `amount_received_minor` | BIGINT | NULL, CHECK >= 0 | Cash received; null for non-cash. |
| `cash_change_minor` | BIGINT | NULL, CHECK >= 0 | Cash change calculated by Payment; null for non-cash. |
| `currency` | TEXT | NOT NULL, CHECK `VND` P0 | Currency of the payment. |
| `provider` | TEXT | NULL | External provider name; null for CASH. |
| `provider_order_ref` | TEXT | NULL | Merchant reference sent to provider. |
| `provider_transaction_id` | TEXT | NULL | Provider settlement reference; unique within tenant/provider when present. |
| `qr_payload` | TEXT | NULL, protected at rest | Dynamic QR/redirect payload returned to POS; never a secret. |
| `expires_at` | TIMESTAMPTZ | NULL | Pending expiry; null for synchronous CASH. |
| `paid_at` | TIMESTAMPTZ | NULL | Time the attempt transitioned to `PAID`. |
| `failure_code` | TEXT | NULL | Safe normalized failure code. |
| `failure_reason_safe` | TEXT | NULL | Redacted operator-safe failure reason. |
| `reconciliation_required` | BOOLEAN | NOT NULL DEFAULT false | Indicates unresolved provider/local discrepancy. |
| `created_by_type` | TEXT | NOT NULL | Human, Saga, scheduler or provider-originated actor type. |
| `created_by_id` | UUID | NULL | Actor/workload identifier when available. |
| `created_at` | TIMESTAMPTZ | NOT NULL | Creation time in UTC. |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Last aggregate update time in UTC. |

### 5.3 `payment_state_transitions`

| Column | Type | Constraints | Description/comment |
|---|---|---|---|
| `id` | UUID | PK | Append-only transition identifier. |
| `tenant_id` | UUID | NOT NULL, indexed | Tenant scope. |
| `branch_id` | UUID | NOT NULL, indexed | Branch scope. |
| `payment_id` | UUID | NOT NULL, indexed | Local payment attempt reference. |
| `from_status` | TEXT | NULL | Previous status; null for initial creation. |
| `to_status` | TEXT | NOT NULL | Resulting status. |
| `reason_code` | TEXT | NOT NULL | Normalized transition reason. |
| `actor_type` | TEXT | NOT NULL | User, service, provider or worker actor type. |
| `actor_id` | UUID | NULL | Actor/workload identifier. |
| `correlation_id` | UUID | NOT NULL | Distributed trace correlation. |
| `causation_id` | UUID | NULL | Command/event causing transition. |
| `occurred_at` | TIMESTAMPTZ | NOT NULL | Business transition time in UTC. |
| `created_at` | TIMESTAMPTZ | NOT NULL | Persistence time in UTC. |

### 5.4 `provider_callbacks`

| Column | Type | Constraints | Description/comment |
|---|---|---|---|
| `id` | UUID | PK | Provider callback record identifier. |
| `tenant_id` | UUID | NULL until mapping | Tenant resolved from trusted payment mapping. |
| `branch_id` | UUID | NULL until mapping | Branch resolved from trusted payment mapping. |
| `payment_id` | UUID | NULL until mapping | Matched local payment attempt; null for unknown reference. |
| `provider` | TEXT | NOT NULL | Provider name, P0 `VNPAY`. |
| `provider_event_key` | TEXT | NOT NULL | Stable provider callback dedupe key. |
| `provider_transaction_id` | TEXT | NULL | Provider transaction identifier. |
| `payload_hash` | TEXT | NOT NULL | Digest of canonical raw callback payload. |
| `signature_valid` | BOOLEAN | NOT NULL | Result of checksum/signature verification. |
| `provider_response_code` | TEXT | NULL | Raw provider response code, safe normalized copy. |
| `provider_transaction_status` | TEXT | NULL | Raw provider transaction status, safe normalized copy. |
| `raw_payload_encrypted` | JSONB | NULL | Encrypted/minimized raw provider payload for retention window. |
| `processing_result` | TEXT | NOT NULL | `ACCEPTED`, `DUPLICATE`, `REJECTED`, `RECONCILIATION_REQUIRED`. |
| `rejection_reason_code` | TEXT | NULL | Safe reason for invalid/mismatch callback. |
| `received_at` | TIMESTAMPTZ | NOT NULL | Gateway receipt time in UTC. |
| `provider_occurred_at` | TIMESTAMPTZ | NULL | Provider-reported business time normalized to UTC. |
| `processed_at` | TIMESTAMPTZ | NULL | Business processing completion time. |

Invalid signature callbacks may be stored here for security evidence but must not create a business idempotency record or state transition.

### 5.5 `reconciliation_runs` and `reconciliation_items`

#### `reconciliation_runs`

| Column | Type | Constraints | Description/comment |
|---|---|---|---|
| `id` | UUID | PK | Reconciliation run identifier. |
| `tenant_id` | UUID | NOT NULL, indexed | Tenant scope of the run. |
| `requested_by_type` | TEXT | NOT NULL | Scheduler, worker or operator actor type. |
| `requested_by_id` | UUID | NULL | Workload/operator identifier. |
| `reason` | TEXT | NOT NULL | Run trigger reason. |
| `status` | TEXT | NOT NULL | `QUEUED`, `RUNNING`, `COMPLETED`, `PARTIAL`, `FAILED`. |
| `requested_at` | TIMESTAMPTZ | NOT NULL | Run request time in UTC. |
| `started_at` | TIMESTAMPTZ | NULL | Worker start time. |
| `completed_at` | TIMESTAMPTZ | NULL | Worker completion time. |
| `correlation_id` | UUID | NOT NULL | Run trace correlation. |

#### `reconciliation_items`

| Column | Type | Constraints | Description/comment |
|---|---|---|---|
| `id` | UUID | PK | Reconciliation item identifier. |
| `run_id` | UUID | NOT NULL, indexed | Parent reconciliation run. |
| `payment_id` | UUID | NULL | Local payment attempt reference. |
| `provider` | TEXT | NOT NULL | Provider queried. |
| `provider_reference` | TEXT | NOT NULL | Provider order/transaction reference queried. |
| `local_status_before` | TEXT | NOT NULL | Payment status before query. |
| `provider_status` | TEXT | NULL | Normalized provider result. |
| `result` | TEXT | NOT NULL | `PAID`, `FAILED`, `EXPIRED`, `UNKNOWN`, `SETTLED_LATE`, `ERROR`. |
| `attempt_count` | INTEGER | NOT NULL DEFAULT 0 | Number of provider query attempts. |
| `next_retry_at` | TIMESTAMPTZ | NULL | Next eligible retry time. |
| `lease_owner` | TEXT | NULL | Worker lease owner. |
| `lease_expires_at` | TIMESTAMPTZ | NULL | Worker lease expiry. |
| `error_code` | TEXT | NULL | Safe normalized provider/processing error. |
| `operator_status` | TEXT | NOT NULL | `UNASSIGNED`, `OPEN`, `UNDER_REVIEW`, `RESOLVED`. |
| `created_at` | TIMESTAMPTZ | NOT NULL | Item creation time. |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Item last update time. |

### 5.6 `late_payment_reconciliation`

| Column | Type | Constraints | Description/comment |
|---|---|---|---|
| `id` | UUID | PK | Late settlement reconciliation identifier. |
| `tenant_id` | UUID | NOT NULL, indexed | Tenant scope. |
| `branch_id` | UUID | NOT NULL, indexed | Branch scope. |
| `payment_id` | UUID | NOT NULL, indexed | Terminal local payment attempt. |
| `order_id` | UUID | NOT NULL | Opaque Order reference. |
| `checkout_attempt_id` | UUID | NOT NULL | Original checkout attempt. |
| `provider` | TEXT | NOT NULL | Provider that reported late settlement. |
| `provider_order_ref` | TEXT | NOT NULL | Provider merchant reference. |
| `provider_transaction_id` | TEXT | NOT NULL | Late provider settlement identifier. |
| `amount_minor` | BIGINT | NOT NULL | Provider-reported late amount. |
| `currency` | TEXT | NOT NULL | Settlement currency. |
| `settlement_status` | TEXT | NOT NULL | P0 `SETTLED_LATE`; does not mean Order paid. |
| `status` | TEXT | NOT NULL | `OPEN`, `UNDER_REVIEW`, `RECOVERED`, `REFUNDED`, `CLOSED`. |
| `reason_code` | TEXT | NOT NULL | Why settlement arrived after terminal attempt. |
| `operator_id` | UUID | NULL | Operator handling the exception. |
| `resolution_note_safe` | TEXT | NULL | Redacted operator resolution note. |
| `resolved_at` | TIMESTAMPTZ | NULL | Resolution time in UTC. |
| `created_at` | TIMESTAMPTZ | NOT NULL | Exception creation time. |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Exception last update time. |

### 5.7 `idempotency_records`

| Column | Type | Constraints | Description/comment |
|---|---|---|---|
| `id` | UUID | PK | Idempotency record identifier. |
| `tenant_id` | UUID | NOT NULL, indexed | Tenant scope. |
| `actor_scope` | TEXT | NOT NULL | Human/service workload scope. |
| `operation` | TEXT | NOT NULL | Command operation name. |
| `idempotency_key` | TEXT | NOT NULL | Caller-provided retry key. |
| `request_hash` | TEXT | NOT NULL | Canonical request fingerprint. |
| `response_status` | INTEGER | NOT NULL | Original HTTP/status result. |
| `response_reference` | UUID | NULL | Payment/refund/run returned by original command. |
| `response_body_redacted` | JSONB | NOT NULL | Safe replayable response body. |
| `expires_at` | TIMESTAMPTZ | NOT NULL | Idempotency retention expiry. |
| `created_at` | TIMESTAMPTZ | NOT NULL | Record creation time. |

### 5.8 `outbox_messages`

| Column | Type | Constraints | Description/comment |
|---|---|---|---|
| `id` | UUID | PK | Outbox event identifier and consumer dedupe key. |
| `tenant_id` | UUID | NOT NULL | Tenant scope in event envelope. |
| `branch_id` | UUID | NOT NULL | Branch scope in event envelope. |
| `aggregate_type` | TEXT | NOT NULL | Aggregate type, P0 `Payment`. |
| `aggregate_id` | UUID | NOT NULL | Payment aggregate identifier. |
| `event_type` | TEXT | NOT NULL | Event type without version suffix. |
| `event_version` | INTEGER | NOT NULL | Immutable event schema version. |
| `payload` | JSONB | NOT NULL | Versioned event envelope/payload without secrets. |
| `occurred_at` | TIMESTAMPTZ | NOT NULL | Business event time. |
| `publish_status` | TEXT | NOT NULL | `PENDING`, `PUBLISHED`, `DLQ`. |
| `attempt_count` | INTEGER | NOT NULL DEFAULT 0 | Number of broker publish attempts. |
| `next_attempt_at` | TIMESTAMPTZ | NULL | Next eligible publish attempt. |
| `last_error_safe` | TEXT | NULL | Redacted last publish error. |
| `created_at` | TIMESTAMPTZ | NOT NULL | Outbox record creation time. |

### 5.9 P1 `payment_allocations` and `refunds`

`payment_allocations` records one tender allocation per Order/payment attempt; `refunds` records partial/full refund independently from the original payment attempt.

| Table | Required columns and remarks |
|---|---|
| `payment_allocations` | `id` UUID allocation ID; `tenant_id`/`branch_id` scope; `order_id`; `payment_id`; `amount_minor`; `currency`; `status`; `idempotency_key`; `created_at`; `updated_at`. Allocation cannot make total allocated exceed immutable Order amount due. |
| `refunds` | `id` UUID refund ID; `tenant_id`/`branch_id` scope; `payment_id`; `amount_minor`; `currency`; `reason`; `status`; `provider`; `provider_ref`; `requested_by`; `processed_at`; `idempotency_key`; `created_at`; `updated_at`. Total successful refunds cannot exceed paid/refundable balance. |

## 6. Constraints and indexes

| Name | Columns/condition | Purpose |
|---|---|---|
| `uq_payment_idempotency` | `tenant_id, actor_scope, operation, idempotency_key` | One replay result per command key |
| `uq_provider_transaction` | `tenant_id, provider, provider_transaction_id` where non-null | Prevent duplicate provider settlement within scope |
| `uq_provider_callback_key` | `provider, provider_event_key` | Deduplicate provider callback |
| `uq_active_vnpay_attempt` | `checkout_attempt_id` where method `VNPAY_QR` and status `PENDING` | One active QR attempt; expiry is evaluated and old attempt is transitioned atomically before a new attempt is created |
| `idx_payment_scope_time` | `tenant_id, branch_id, created_at DESC` | Branch/tenant history |
| `idx_payment_pending_expiry` | `tenant_id, status, expires_at` | Reconciliation scan |
| `idx_payment_reconciliation_retry` | `tenant_id, reconciliation_required, next_retry_at` | Retry queue selection |
| `idx_callback_provider_time` | `provider, received_at DESC` | Callback operations/history |
| `idx_outbox_publish` | `publish_status, next_attempt_at, created_at` | Outbox publisher |
| `idx_reconciliation_operator` | `tenant_id, branch_id, operator_status, updated_at DESC` | Operations queue |

Expiry is evaluated inside the initiate/reconciliation transaction. A request that sees an expired `PENDING` attempt must transition it to `EXPIRED` under the same lock before creating a new attempt; the database unique index must not use the non-immutable predicate `expires_at > now()`.

## 7. Security and data handling contracts

- User API authorization: Cognito JWT + current Identity membership/permission decision.
- Internal API authorization: workload identity/mTLS or signed workload context; public clients cannot call `/internal/v1`.
- Repository methods require tenant scope; branch scope is mandatory unless caller has tenant-wide Owner/operator permission.
- `vnp_SecureHash` is verified before provider mapping/business lookup according to VNPAY provider contract.
- Raw callback may be retained encrypted for 90 days; normalized metadata/hash/settlement evidence follows financial retention policy.
- Payment/refund/reconciliation ledger and audit/security evidence baseline retention is 7 years, subject to legal/tenant policy.
- No raw card data, secret, JWT, full signing input or unnecessary PII in logs/events/responses.
- Audit records include actor type/id, action, reason, resource, tenant/branch, correlation ID, outcome and timestamps.

## 8. Parent contract amendments

The following changes must be applied to `docs/designs/pos-platform/2026-10-12-contracts.md` before implementation:

1. Change VNPAY IPN endpoint from `POST` to `GET /callbacks/v1/vnpay/ipn`; request is query parameters and response is provider-compatible `{RspCode, Message}`.
2. Mark `PaymentInitiated.expiresAt` nullable for synchronous CASH.
3. Mark `PaymentPaid.provider` and `providerTransactionId` nullable for CASH.
4. Add `paymentMethod`, `amountReceivedMinor` and `cashChangeMinor` to `PaymentPaid` payload where applicable.
5. Add `PaymentReconciliationRequired.v1` with `reconciliationId`, `reasonCode` and `settlementStatus`.
6. Define late `SETTLED_LATE` as reconciliation evidence, not `PaymentPaid` or `OrderPaid`.
7. Scope `uq_provider_transaction` by tenant/provider and permit null provider references.
8. Update Payment initiate request/response with `sagaId`, `checkoutAttemptId`, VNPAY QR/expiry, reconciliation flags and cash change.
9. Add `GET /api/v1/payments`, reconciliation detail and P1 refund contracts.
10. Add contract fixtures for invalid checksum, wrong amount/order, duplicate/out-of-order/late IPN, cash response and VNPAY retry response.

## 9. Contract test matrix

| Scenario | Expected contract result |
|---|---|
| Cash exact amount | `200`, `PAID`, change `0`, one `PaymentPaid` |
| Cash overpayment | `200`, exact integer change, one `PaymentPaid` |
| Cash underpayment | `422 invalid_cash_amount`, no state/event |
| Duplicate cash request | Original response, no duplicate row/event |
| Concurrent VNPAY initiate | One active `PENDING`, same QR/payment returned |
| Invalid checksum | Provider-compatible invalid response, no state/event |
| Valid signature/wrong amount | No `PAID`, callback rejection/reconciliation record |
| Valid success IPN | `PENDING → PAID`, one `PaymentPaid` |
| Duplicate valid IPN | Same provider result, no second side effect |
| Out-of-order callback | No terminal overwrite, callback/audit evidence |
| Late success after terminal | `SETTLED_LATE`, reconciliation event only, no Order resurrection |
| Provider timeout | `PENDING` + reconciliation required, no false failure |
| Reconciliation definitive success while pending | `PAID`, one `PaymentPaid` |
| Reconciliation unknown | Keep state, retry/backoff/manual queue |
| Cross-tenant/branch read | `404`/`403` without existence leak |
| Same idempotency key/different hash | `409 idempotency_conflict` |
| P1 refund above balance | `409/422`, no refund side effect |

## 10. External references

- [VNPAY PAY integration — payment URL and IPN](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html): sandbox payment URL, VNPAY query fields, checksum ordering, GET IPN, response semantics and retry behavior.
- [VNPAY querydr/refund API](https://sandbox.vnpayment.vn/apis/docs/truy-van-hoan-tien/querydr%26refund.html): status query, checksum contract and refund API payload/response.
