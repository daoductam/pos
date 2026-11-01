# Module 4 — Payment service: Architecture

**Ngày:** 2026-10-12  
**Trạng thái:** Approved  
**Phạm vi:** M — Standard Service Flow  
**Module:** Thanh toán (Payment service)

## 1. Boundary và ownership

Payment service sở hữu:

- `payment_attempt` lifecycle.
- Provider references và provider callback.
- IPN checksum/signature verification.
- Idempotency và duplicate protection.
- Reconciliation run/item.
- `late_payment_reconciliation`.
- Payment events và outbox.

Payment không:

- Đọc trực tiếp Order/Inventory DB.
- Tự tính lại giá/thuế/discount.
- Tự finalize Order.
- Tự phát `OrderPaid.v1`.
- Tự re-reserve Inventory cho late settlement.

Order/Saga xác nhận checkout attempt còn active, gửi immutable total snapshot và điều phối Payment → Inventory commit → Order finalize.

## 2. Topology

```text
POS/API
  → API Gateway + Cognito
  → Order/Step Functions
  → Payment internal REST API
  → Payment Application/Domain
  → PostgreSQL Payment DB
       ├─ payment_attempts
       ├─ provider_callbacks
       ├─ reconciliation_runs/items
       ├─ late_payment_reconciliation
       ├─ idempotency_records
       └─ outbox_messages

Payment → VNPAY Sandbox Adapter → VNPAY
VNPAY → Gateway IPN endpoint → Payment IPN Handler

Outbox → EventBridge → SQS consumer queues
Scheduler → Reconciliation queue → Reconciliation Worker → VNPAY
```

## 3. Thành phần chính

| Component | Trách nhiệm |
|---|---|
| API adapter | REST endpoints, request validation, Cognito/service authorization, tenant/branch scope |
| Payment application service | Initiate, read, cancel/expire và reconciliation command |
| Payment domain | State machine, amount validation, idempotency decision, terminal-state protection |
| Cash provider adapter | Xử lý cash synchronously và change calculation |
| VNPAY adapter | QR creation, checksum, IPN mapping, status query và refund capability P1 |
| IPN handler | Lưu callback, verify signature, mapping validation, dedupe và gọi domain transition |
| Reconciliation worker | Claim bằng lease/row lock, query provider, retry/backoff và tạo late settlement record |
| Persistence layer | PostgreSQL repositories với tenant predicates, unique constraints và append-only transitions |
| Outbox publisher | Publish at-least-once, retry/DLQ |
| Read/reporting projection | P1, read-only và không thay đổi payment ledger |

## 4. Transaction boundaries

Trong một Payment DB transaction:

1. Lock/find idempotency record.
2. Validate payment attempt và invariant.
3. Apply state transition hoặc tạo reconciliation record.
4. Persist callback/transition metadata.
5. Insert outbox event.
6. Commit atomic.

Broker publish nằm ngoài transaction và có thể retry; consumer dedupe theo `eventId`.

Provider API call không giữ database transaction mở lâu hơn cần thiết. Provider result được đưa trở lại domain command với correlation/provider reference và được commit bằng transaction mới.

## 5. State và late settlement

- `PENDING → PAID` chỉ khi cash hợp lệ hoặc VNPAY success đã verify.
- `PENDING → FAILED/EXPIRED/CANCELLED` theo transition policy.
- Terminal state không được ghi đè.
- Provider success sau `EXPIRED`/`CANCELLED`:
  - giữ nguyên payment attempt state;
  - tạo `late_payment_reconciliation` với `SETTLED_LATE`;
  - phát `PaymentReconciliationRequired.v1`;
  - không phát `PaymentPaid.v1`, không phát `OrderPaid.v1`, không hồi sinh Order.

## 6. Communication và orchestration

- Step Functions gọi internal Payment API đồng bộ bằng service identity.
- VNPAY IPN là inbound webhook tại gateway boundary.
- Payment events đi qua transactional outbox → EventBridge → SQS.
- Reconciliation chạy bất đồng bộ qua scheduler/queue/worker.
- Không có direct database sharing giữa services.
- Payment không tự điều phối Order hoặc Inventory; `PaymentPaid.v1` chỉ là payment fact cho Saga/Audit. Saga là nơi gọi `CommitReservation` tới Inventory.
- `OrderPaid.v1` chỉ do Order phát sau khi Inventory commit và Order finalization hoàn tất.

## 7. Provider adapter architecture

Payment domain chỉ phụ thuộc các normalized ports:

- `PaymentProviderPort.createDynamicQr(...)`.
- `PaymentProviderPort.verifyCallback(...)`.
- `PaymentProviderPort.queryStatus(...)`.
- P1 `PaymentProviderPort.refund(...)`.

Adapter implementations:

- `CashPaymentProvider`: synchronous, không external call.
- `VnpaySandboxAdapter`: VNPAY field names, response codes, canonicalization, checksum và HTTP integration.
- `FakePaymentProviderAdapter`: deterministic local/demo fixtures cho duplicate, out-of-order, timeout và late IPN.

VNPAY-specific data không đi vào domain model. Adapter không được tự ghi database hoặc phát event; adapter chỉ trả normalized result để domain quyết định transition.

## 8. Database architecture

- PostgreSQL database riêng cho Payment.
- UUID v7 cho `paymentId`, `callbackId`, `reconciliationRunId`, `refundId` và `outboxMessageId`.
- `BIGINT` cho tiền minor unit; không dùng floating point.
- Core records:
  - `payment_attempts`;
  - `payment_state_transitions`;
  - `provider_callbacks`;
  - `reconciliation_runs`/`reconciliation_items`;
  - `late_payment_reconciliation`;
  - `idempotency_records`;
  - `outbox_messages`;
  - P1 `payment_allocations`/`refunds`.
- Unique constraints:
  - `(tenant_id, operation, idempotency_key)`;
  - `(tenant_id, provider, provider_transaction_id)` khi provider reference tồn tại;
  - `(provider, provider_event_key)`;
  - một VNPAY `PENDING` active cho mỗi `checkout_attempt_id`.
- Index chính:
  - `(tenant_id, branch_id, created_at DESC)`;
  - `(tenant_id, status, expires_at)`;
  - `(tenant_id, reconciliation_required, next_retry_at)`;
  - provider/event key;
  - outbox publish status/created time.
- Row lock/lease cho reconciliation; transaction lock cho state transition.
- Không partition P0; đánh giá lại theo volume và retention sau khi có số liệu vận hành.
- Mọi column migration có comment mô tả mục đích.

## 9. Security architecture

- API Gateway kiểm tra Cognito JWT; Payment service kiểm tra lại JWT, issuer, audience, expiry, role và tenant/branch scope.
- Internal call dùng trusted service context qua service identity/mTLS hoặc signed workload context.
- `tenantId`, `branchId`, `orderId` và amount trong body không phải bằng chứng sở hữu độc lập; phải đối chiếu trusted context và immutable Order snapshot.
- VNPAY IPN chỉ public tại gateway boundary; có schema validation, request-size/rate limit, timeout và replay protection.
- Checksum/signature phải được verify trước business lookup/callback transition.
- VNPAY secret ở Secrets Manager.
- Không log secret, raw signature, raw signing input hoặc sensitive callback payload.
- Callback raw payload được encrypted/minimized theo retention policy.
- Audit append-only cho state transition, invalid/mismatch callback, reconciliation, refund và authorization denial.

## 10. Observability architecture

- Correlation/trace ID xuyên suốt POS → Order/Saga → Payment → provider/broker.
- Structured logs có `tenantId`, `branchId`, `paymentId`, `orderId`, `checkoutAttemptId`, provider reference nếu an toàn, `eventId` và outcome; không có secret/raw sensitive payload.
- Metrics:
  - payment initiation success/failure theo method;
  - cash validation failure/change;
  - VNPAY QR creation failure/latency;
  - invalid signature, mapping mismatch, duplicate/out-of-order IPN;
  - pending age, expired count, reconciliation required count/age;
  - provider query failure/latency;
  - outbox retry/DLQ và event lag;
  - duplicate side-effect detector cho `PaymentPaid`;
  - P1 refund pending/failure/discrepancy.
- Alert khi pending/reconciliation vượt ngưỡng, DLQ tăng, outbox backlog, provider failure hoặc cross-tenant denial tăng.

## 11. Failure handling

| Failure | Xử lý |
|---|---|
| Cash thiếu tiền | Reject, không đổi state |
| POS retry | Trả idempotent response cũ |
| VNPAY tạo QR lỗi | Không báo `PAID`, trả retryable error |
| IPN sai checksum | Lưu security event, không đổi state |
| IPN duplicate | No-op, trả provider-compatible result |
| Provider timeout | Giữ pending/reconciliation required |
| Provider success khi pending | `PAID`, phát một `PaymentPaid` |
| Provider success sau terminal | `SETTLED_LATE`, không phát `PaymentPaid` |
| Outbox publish lỗi | Retry rồi DLQ |
| Payment success nhưng Inventory commit lỗi | Saga retry; terminal exception cần operator reconciliation |

## 12. Contract-driven sequence flows

Repo chưa có implementation để trace controller/consumer thực tế; các flow dưới đây là contract-driven, không phải runtime trace.

### 12.1 Cash

1. POS gửi checkout qua Order/Saga.
2. Order/Saga reserve Inventory rồi gọi Payment initiate.
3. Payment validate `amountReceivedMinor` trong transaction.
4. Payment chuyển `PENDING → PAID`, lưu change và outbox.
5. `PaymentPaid.v1` tiếp tục Saga; Saga gọi `CommitReservation` tới Inventory, sau đó Order finalize rồi phát `OrderPaid.v1`.

### 12.2 VNPAY QR và IPN

1. Order/Saga gọi Payment initiate.
2. Payment tạo `PENDING`, gọi VNPAY adapter và trả QR/expiry.
3. VNPAY gọi IPN tại gateway boundary.
4. IPN handler lưu callback, verify checksum, mapping và amount.
5. Payment transition `PENDING → PAID` trong transaction; outbox phát một `PaymentPaid.v1`.
6. Saga commit Inventory, Order finalize và Order phát `OrderPaid.v1`.

### 12.3 Reconciliation/late settlement

1. Scheduler enqueue payment pending quá hạn/unknown.
2. Worker claim bằng lease, query VNPAY và retry có backoff.
3. Definitive provider success khi payment còn pending → `PAID` + `PaymentPaid.v1`.
4. Unknown → giữ state, gắn reconciliation required, alert/manual queue.
5. Success sau terminal → `late_payment_reconciliation(SETTLED_LATE)`, chỉ `PaymentReconciliationRequired.v1`, không hồi sinh Order.

## 13. Deployment

### Local/demo

- Docker Compose chạy Payment service, PostgreSQL, local broker và fake provider adapter.
- Fixture deterministic mô phỏng duplicate/out-of-order/late IPN và provider timeout.
- VNPAY sandbox adapter được bật bằng environment config, không thay đổi domain logic.

### AWS hybrid

- ECS/Fargate cho Payment service.
- API Gateway cho public/internal routing và throttling.
- EventBridge route event đến SQS consumer queues và DLQ.
- EventBridge Scheduler hoặc equivalent trigger reconciliation queue.
- Secrets Manager cho VNPAY credentials/signing secret.
- CloudWatch/OpenTelemetry/X-Ray cho logs, metrics và traces.
- Terraform quản lý environment resources.

## 14. Architecture decisions

| ADR | Decision | Alternatives | Rationale |
|---|---|---|---|
| PAY-ADR-01 | PostgreSQL riêng cho Payment | Shared DB, NoSQL ledger | Strong transaction phù hợp money/state/idempotency và ownership rõ ràng |
| PAY-ADR-02 | Provider adapter/normalized ports | VNPAY fields trong domain | Cô lập sandbox/provider behavior và hỗ trợ fake adapter |
| PAY-ADR-03 | Step Functions + internal REST cho command | Choreography/direct DB | Saga cần orchestration, timeout, compensation và trace rõ |
| PAY-ADR-04 | Outbox → EventBridge/SQS | Direct publish, 2PC | Tránh dual-write và hỗ trợ retry/replay |
| PAY-ADR-05 | Terminal attempt + late settlement record | Ghi đè terminal thành `PAID` | Không hồi sinh Order và giữ financial evidence bất biến |
| PAY-ADR-06 | UUID v7 + integer minor unit | UUID v4, float/decimal amount | Sortable IDs và money arithmetic deterministic |
| PAY-ADR-07 | No cross-service DB access | Shared schema/query join | Giữ bounded context và độc lập migration |

## 15. Deferred implementation artifacts

- PlantUML L1/L2 sequence diagrams sẽ được tạo sau khi API/event contracts có endpoint/topic/schema cụ thể; hiện chỉ giữ contract-driven flows để không bịa participant hoặc runtime call.
- OpenAPI/AsyncAPI/schema migrations sẽ được materialize ở pha API & Data Contracts.
- Reconciliation retention/SLA, reporting business timezone và parent contract amendments phải được ghi rõ trong contracts trước implementation.

