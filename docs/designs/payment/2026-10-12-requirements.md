# Module 4 — Payment service: Requirements

**Ngày:** 2026-10-12  
**Trạng thái:** Approved  
**Phạm vi:** M — Standard Service Flow  
**Module:** Thanh toán (Payment service)

## 1. Phạm vi và phụ thuộc

Payment service sở hữu payment attempt, provider payment state, IPN verification, reconciliation và payment events. Order service sở hữu Order state, checkout attempt và Order total snapshot.

### 1.1 P0

- Thanh toán tiền mặt và tự tính tiền thừa.
- Tạo VNPAY dynamic QR sandbox.
- Payment state machine với transition hợp lệ.
- IPN verification, checksum/signature validation và idempotency.
- Reconciliation job cho payment treo, callback lỗi hoặc trạng thái provider chưa rõ.
- API/event boundary với Order/Saga, Inventory và vận hành.

### 1.2 P1

- Split tender.
- Refund một phần/toàn bộ.
- Lịch sử giao dịch.
- Báo cáo đối soát.

### 1.3 Phụ thuộc

- Order gửi `orderId`, `checkoutAttemptId`, `tenantId`, `branchId`, `amountDueMinor`, `currency` và idempotency context trong authenticated service request; trusted service context là nguồn kiểm tra scope.
- Step Functions/Order điều phối bước Payment → Inventory commit → Order finalize.
- Saga nhận `PaymentPaid.v1` để tiếp tục checkout; Saga gọi trực tiếp `CommitReservation` tới Inventory. Payment không tự finalize Order và `PaymentPaid` không là trigger tự động của Inventory.
- VNPAY sandbox cung cấp QR, IPN và API query status theo adapter.
- Cognito cung cấp user identity/role cho API có người dùng.
- Secrets Manager cung cấp VNPAY credentials/signing secret.
- Outbox/broker cung cấp delivery at-least-once cho event.

## 2. Use cases

| ID | Priority | Actor | Use case | Kết quả chính |
|---|---|---|---|---|
| PAY-UC-01 | P0 | Cashier/Manager | Thu tiền mặt | Payment `PAID`, trả số tiền thừa, phát `PaymentPaid.v1` một lần |
| PAY-UC-02 | P0 | Cashier/Manager | Tạo VNPAY QR | Payment `PENDING`, trả QR payload và expiry |
| PAY-UC-03 | P0 | VNPAY | Gửi IPN | Callback được verify, dedupe và transition idempotent |
| PAY-UC-04 | P0 | Worker/Operator | Đối soát payment treo | Provider state được kết luận hoặc tạo reconciliation exception |
| PAY-UC-05 | P0 | Order/Saga | Đọc trạng thái payment | Trả state, amount, provider reference và reconciliation flags đúng scope |
| PAY-UC-06 | P0 | System | Xử lý late payment | Ghi `SETTLED_LATE` reconciliation exception nhưng không hồi sinh Order |
| PAY-UC-07 | P1 | Cashier/Manager | Split tender | Nhiều payment được gom đến khi đủ `amountDue` |
| PAY-UC-08 | P1 | Manager/Owner | Refund | Tạo refund partial/full cho payment `PAID`, có idempotency và audit |
| PAY-UC-09 | P1 | Manager/Owner | Xem history/report | Đọc lịch sử và số liệu đối soát theo tenant/branch scope |

## 3. Actors và authorization

| Actor/role | Quyền |
|---|---|
| `cashier` | Tạo cash/VNPAY payment cho Order thuộc branch scope; đọc payment thuộc branch scope |
| `manager` | Toàn bộ quyền cashier trong branch scope; yêu cầu hủy payment pending theo chính sách; refund; đọc history/report của branch |
| `owner` | Toàn quyền trong tenant, bao gồm refund, reconciliation và report toàn tenant |
| `kitchen` | Không có quyền truy cập Payment |
| `order-saga-service` | Initiate payment, nhận/đọc state, gửi command expire/cancel theo service authorization |
| `reconciliation-worker` | Query provider, transition state theo reconciliation policy, phát event; không hoạt động như user role |
| VNPAY | Chỉ gọi IPN endpoint tại gateway boundary; không có Cognito user token |

Mọi API có dữ liệu tenant/branch phải kiểm tra authorization tại service layer và repository/query layer. Không dùng ID từ URL làm bằng chứng sở hữu resource.

## 4. Functional requirements

### 4.1 Common payment initiation

1. Payment chỉ nhận `amountDueMinor` và `currency` từ Order snapshot; không gọi Catalog, không tính lại giá/thuế/discount/phí.
2. P0 chỉ chấp nhận `currency=VND` và amount là số nguyên không âm theo đơn vị nhỏ nhất; không dùng floating point.
3. Request phải chứa `orderId`, `checkoutAttemptId`, `paymentMethod`, `amountDueMinor`, `currency`, `idempotencyKey`, `correlationId` và actor/service identity; `tenantId`/`branchId` trong body nếu có chỉ là context để đối chiếu với trusted service context.
4. Order/Saga xác nhận checkout attempt còn active và gửi immutable snapshot/version. Payment xác thực consistency của trusted tenant/branch/order context, nhưng không đọc trực tiếp Order DB và không tự sở hữu Order state.
5. Cùng `Idempotency-Key` và request fingerprint phải trả response đã lưu. Cùng key nhưng request khác phải trả conflict.
6. Mỗi `checkoutAttemptId` chỉ có một payment VNPAY `PENDING`; attempt terminal không được reset.
7. Payment attempt lưu immutable amount snapshot, method, references, expiry, state transitions và timestamps.
8. Không API public nào được phép tùy ý set payment state; mọi transition đi qua domain command/IPN/reconciliation.

### 4.2 Cash payment P0

1. Request cash phải có `amountReceivedMinor`.
2. Nếu `amountReceivedMinor < amountDueMinor`, trả lỗi validation và không tạo payment `PAID`.
3. Nếu đủ tiền, tính `cashChangeMinor = amountReceivedMinor - amountDueMinor` bằng integer arithmetic.
4. Cash payment chuyển `PENDING → PAID` trong cùng transaction nghiệp vụ sau khi validate amount.
5. Response phải trả `amountDueMinor`, `amountReceivedMinor`, `cashChangeMinor`, `paymentId`, `state=PAID` và `paidAt`.
6. Retry do POS timeout không được tạo thêm payment hoặc phát duplicate `PaymentPaid.v1`.
7. Cash không gọi VNPAY và không lưu provider transaction reference.

### 4.3 VNPAY QR P0

1. Request VNPAY tạo payment `PENDING` và gọi adapter tạo dynamic QR.
2. Nếu có payment VNPAY `PENDING` còn hạn cho cùng checkout attempt, trả lại QR/payment hiện tại; không tạo attempt thứ hai.
3. Chỉ tạo attempt mới khi attempt trước `FAILED` hoặc `EXPIRED`.
4. TTL demo mặc định là 15 phút, có thể cấu hình nhưng phải được trả trong response `expiresAt` và dùng nhất quán bởi Order/Saga và reconciliation.
5. Response pending phải trả `paymentId`, `orderId`, `checkoutAttemptId`, `state`, `amountDueMinor`, `currency`, `qrPayload`, `providerOrderRef`, `expiresAt` và `correlationId`.
6. Provider credentials/signing secret lấy từ Secrets Manager; không hard-code hoặc log secret.
7. Không lưu raw card data. QR payload và provider references phải được bảo vệ theo data classification của nền tảng.
8. Nếu provider không tạo được QR, payment không được báo `PAID`; lỗi phải giữ được correlation và có retry/error code rõ ràng.

### 4.4 Payment state machine P0

| Current | Command/event | Guard | Next | Side effect |
|---|---|---|---|---|
| `PENDING` | Cash amount validated | Amount received ≥ amount due | `PAID` | Lưu paid/change, outbox `PaymentPaid.v1` |
| `PENDING` | Verified VNPAY success | Signature, mapping, amount và provider status hợp lệ | `PAID` | Lưu provider ref, outbox `PaymentPaid.v1` |
| `PENDING` | Provider definitive failure | Callback/query kết luận thất bại | `FAILED` | Outbox `PaymentFailed.v1` |
| `PENDING` | Expiry decision | Provider definitive expiry hoặc local policy đóng attempt sau khi reconciliation boundary được áp dụng; provider unknown giữ `PENDING` + flag | `EXPIRED` | Outbox `PaymentExpired.v1`, báo Saga release |
| `PENDING` | Authorized cancel | Order/Saga hoặc manager policy hợp lệ | `CANCELLED` | Outbox `PaymentCancelled.v1` |
| Terminal | Duplicate same result | Idempotency key/event đã xử lý | Same state | No-op, trả kết quả cũ |
| Terminal | Conflicting callback | Callback hợp lệ nhưng mâu thuẫn state | Same state | Lưu callback, audit và reconciliation exception |
| `EXPIRED`/`CANCELLED` | Verified provider success đến muộn | Provider settlement hợp lệ nhưng attempt đã terminal | Same state | Tạo `late_payment_reconciliation` với `SETTLED_LATE`; chỉ outbox `PaymentReconciliationRequired.v1` |

`PAID`, `FAILED`, `EXPIRED`, `CANCELLED` là terminal state của một attempt. Retry tạo attempt mới. Refund P1 là state machine riêng, không sửa payment attempt gốc.

### 4.5 VNPAY IPN P0

IPN pipeline bắt buộc theo thứ tự:

1. Gateway áp dụng schema validation, size/rate validation và nhận raw payload.
2. Lưu raw payload tối thiểu cần thiết cùng request metadata; dữ liệu nhạy cảm phải mã hóa/minimize và không log plaintext.
3. Verify checksum/signature theo thuật toán và field canonicalization của VNPAY sandbox adapter, secret lấy từ Secrets Manager.
4. Nếu signature sai: không tạo idempotency business record, không đổi payment state; lưu security/audit event và trả provider-compatible invalid response.
5. Nếu signature đúng: kiểm tra provider, merchant/order reference, payment mapping, tenant/branch mapping, amount và provider success status.
6. Nếu mapping/amount/status sai: không chuyển `PAID`; lưu callback và lý do; tạo reconciliation/security event khi phù hợp.
7. Với callback hợp lệ, dedupe theo provider event key/provider transaction reference và payload fingerprint trong transaction có unique constraint.
8. Callback hợp lệ đã xử lý trả kết quả provider-compatible nhưng không phát side effect lần hai.
9. Callback đến ngoài thứ tự hoặc đến sau terminal state không được ghi đè state; xử lý như no-op hoặc reconciliation exception theo policy.
10. IPN response không làm lộ internal error, secret, tenant data hoặc stack trace.

### 4.6 Reconciliation job P0

1. Scheduler chạy mặc định mỗi 5 phút; interval, TTL, timeout và retry threshold là cấu hình.
2. Worker chọn payment `PENDING` quá hạn, callback không kết luận hoặc có `RECONCILIATION_REQUIRED`.
3. Worker claim bằng lease/row lock; nhiều worker không được query provider hoặc transition cùng payment đồng thời.
4. Mỗi run có `reconciliationRunId`, item records, attempt count, started/completed time, result và correlation ID.
5. Worker gọi provider status API bằng provider transaction/order reference, timeout và exponential backoff.
6. Provider success hợp lệ của payment còn `PENDING` chuyển payment sang `PAID` và phát đúng một `PaymentPaid.v1`.
7. Provider definitive failure chuyển payment sang `FAILED`; provider expiry hoặc policy expiry chuyển `EXPIRED`.
8. Provider timeout/unknown không bị ép thành thất bại; giữ state, tăng retry metadata và gắn `RECONCILIATION_REQUIRED`.
9. Sau retry threshold, phát alert và đưa vào manual operations queue.
10. Re-run cùng item/run phải idempotent và không phát duplicate event.
11. Late success sau khi Order đã release reservation/terminal không đổi payment attempt state; worker tạo `late_payment_reconciliation` với provider settlement `SETTLED_LATE`, phát `PaymentReconciliationRequired.v1` và không gọi command tự động hồi sinh Order.

### 4.7 Events và Saga boundary P0

Payment phát qua transactional outbox:

- `PaymentInitiated.v1`
- `PaymentPaid.v1`
- `PaymentFailed.v1`
- `PaymentExpired.v1`
- `PaymentCancelled.v1`
- `PaymentReconciliationRequired.v1`

Mỗi event có tối thiểu `eventId`, `eventType`, `eventVersion`, `occurredAt`, `tenantId`, `branchId`, `aggregateId`, `paymentId`, `orderId`, `checkoutAttemptId`, `correlationId`, `causationId` và `data`.

- Event delivery là at-least-once; consumer dedupe theo `eventId`.
- `PaymentPaid.v1` là payment fact cho Order/Saga và Audit. Saga dùng fact này để gọi command commit có workload identity tới Inventory.
- Chỉ Order service phát `OrderPaid.v1` sau khi Payment success và Inventory commit hoàn tất.
- Payment không phát `OrderPaid.v1`, không tự finalize Order và không tự re-reserve Inventory cho late payment.

### 4.8 Split tender P1

1. Một Order có thể có nhiều payment attempt với method và allocation riêng.
2. Tổng allocation không được vượt `amountDueMinor`; tổng các payment `PAID` phải bằng đúng amount due để hoàn tất checkout.
3. Mỗi tender có state, idempotency, provider reference và audit độc lập.
4. Order/Saga nhận progress theo payment aggregate nhưng chỉ cho finalize khi đủ tiền.
5. Duplicate allocation hoặc retry không được vượt tổng tiền Order.

### 4.9 Refund P1

1. Chỉ `PAID` payment mới đủ điều kiện refund.
2. Hỗ trợ partial/full refund nhưng tổng refund thành công không vượt số tiền payment đã paid trừ refund trước đó.
3. Mỗi refund có `refundId`, payment gốc, amount, reason, actor, provider reference, state, idempotency key và audit.
4. Refund cần role `manager` hoặc `owner` theo branch/tenant scope.
5. Refund provider failure/pending không được sửa payment paid history; refund có state machine và reconciliation riêng.
6. Retry cùng refund idempotency key trả kết quả cũ; payload khác bị conflict.
7. Refund không tự thay đổi Order state hoặc phát `OrderPaid` lần nữa.

### 4.10 History và reconciliation report P1

1. API đọc phân trang, read-only, filter theo date range, branch, Order/payment/provider ID, method, state, cashier, refund và reconciliation flag.
2. Chi tiết payment hiển thị amount, paid amount, cash change, timestamps, provider reference, state transition summary, callback/reconciliation summary và refund links.
3. Báo cáo theo ngày/branch/provider gồm tổng tiền theo method, số lượng theo state, local-provider discrepancy, late payment và refund.
4. Read model/report không được sửa payment ledger gốc.
5. Export file và báo cáo kế toán nâng cao không thuộc P1 tối thiểu.

## 5. API requirements

### 5.1 Internal initiate payment

`POST /internal/v1/orders/{orderId}/payments`

Caller: Order/Saga service identity. Trusted tenant/branch context phải đến từ service authentication hoặc signed context; body fields chỉ là consistency check.  
Response: cash có thể `200`; VNPAY initiation trả `202` với `PENDING`.

Request tối thiểu:

```json
{
  "checkoutAttemptId": "uuid",
  "tenantId": "uuid",
  "branchId": "uuid",
  "paymentMethod": "CASH|VNPAY_QR",
  "amountDueMinor": 125000,
  "currency": "VND",
  "amountReceivedMinor": 150000,
  "idempotencyKey": "checkout-attempt-key",
  "correlationId": "uuid",
  "returnUrl": null
}
```

`amountReceivedMinor` bắt buộc cho `CASH`, bỏ qua/không chấp nhận cho `VNPAY_QR`; `returnUrl` chỉ dùng theo allowlist cho VNPAY và không phải auth signal.

Response tối thiểu:

```json
{
  "paymentId": "uuid",
  "orderId": "uuid",
  "checkoutAttemptId": "uuid",
  "paymentMethod": "CASH",
  "paymentStatus": "PAID",
  "amountDueMinor": 125000,
  "amountReceivedMinor": 150000,
  "cashChangeMinor": 25000,
  "currency": "VND",
  "providerTransactionId": null,
  "qrPayload": null,
  "expiresAt": null,
  "reconciliationRequired": false,
  "correlationId": "uuid"
}
```

### 5.2 Read payment

`GET /api/v1/payments/{paymentId}`

- Cognito JWT bắt buộc.
- Branch/tenant scope phải được kiểm tra server-side.
- Không trả raw callback payload, secret hoặc internal stack trace.
- Có thể trả state transition summary và reconciliation summary theo role.

### 5.3 VNPAY IPN

`POST /webhooks/vnpay/ipn`

- Public chỉ tại gateway boundary; không yêu cầu Cognito user token.
- VNPAY adapter chịu trách nhiệm parse field provider, canonicalize checksum input, verify signature và map provider status.
- Response chỉ chứa provider-compatible success/invalid semantics; lỗi nội bộ ghi log/audit với correlation ID.

### 5.4 Reconciliation run

`POST /internal/v1/payments/reconciliation/run`

- Caller là scheduler/worker hoặc operator service identity.
- Hỗ trợ run toàn bộ hoặc theo payment/provider reference trong phạm vi được cấp.
- Không cho client POS gọi trực tiếp.
- Response trả `reconciliationRunId`, accepted count và trạng thái enqueue; kết quả item đọc qua reconciliation API/read model.

### 5.5 Error contract

Tối thiểu phải phân biệt:

| HTTP/error code | Ý nghĩa |
|---|---|
| `400` | Payload/amount/currency/schema không hợp lệ |
| `401/403` | Thiếu hoặc không đủ authorization/scope |
| `404` | Resource không tồn tại trong tenant scope |
| `409` | Idempotency conflict, active payment conflict, invalid transition hoặc reconciliation required |
| `422` | Provider/amount/mapping business validation thất bại |
| `503` | Provider/Payment dependency unavailable; không khẳng định payment failed nếu chưa có kết luận |

Error response phải có `code`, `message` an toàn, `correlationId`, `retryable` và resource identifiers cần thiết; không trả secret/provider raw payload.

## 6. Data và consistency requirements

### 6.1 Payment-owned records

- `payment_attempts`: tenant/branch, order/checkout references, method, immutable amount snapshot, state, provider refs, expiry, actor/service, timestamps, reconciliation flags.
- `payment_state_transitions`: append-only transition history, from/to state, reason, actor, correlation/causation.
- `provider_callbacks`: provider, provider event key, payment reference, payload hash, signature result, provider status, encrypted/minimized raw payload, processed time, received time.
- `reconciliation_runs` và `reconciliation_items`: run metadata, lease, attempts, provider/local result, discrepancy, operator status.
- `late_payment_reconciliation`: terminal payment reference, provider settlement reference/status, amount, reason, recovery/refund status, operator action and audit reference.
- `idempotency_records`: scope, key, request fingerprint, response reference/status, expiry.
- `outbox_messages`: versioned event payload, aggregate, publish status, retry/DLQ metadata.
- P1 `refunds` và `payment_allocations` cho refund/split tender.

### 6.2 Required invariants

- Unique idempotency key theo tenant, operation và caller scope.
- Unique provider event key theo provider.
- Unique provider transaction reference theo tenant và provider khi provider trả reference.
- Tối đa một VNPAY `PENDING` còn hạn cho mỗi `checkoutAttemptId`.
- Không có transition từ terminal state sang state khác.
- Payment amount và refund amount dùng integer minor unit.
- Mọi query payment/reconciliation/history có tenant predicate; branch predicate bắt buộc khi actor không có tenant-wide scope.
- State transition và outbox event phải commit atomic với business state.
- Callback signature invalid không được tạo business idempotency record.

### 6.3 Consistency model

- Strong consistency trong Payment database cho state/idempotency/unique invariants.
- Eventual consistency giữa Payment, Order, Inventory và reporting thông qua Saga/outbox/broker.
- Consumer có at-least-once delivery và deduplication.
- Payment success không đồng nghĩa Order paid; `OrderPaid.v1` là canonical paid-order fact. Late provider settlement không phát `PaymentPaid.v1`/`OrderPaid.v1` sau terminal attempt.

## 7. Security, privacy và audit

- Verify Cognito JWT, role và tenant/branch scope ở API có user.
- Service-to-service API dùng service identity và allowlist operation.
- IPN public endpoint có schema validation, rate limit, provider signature/checksum và replay/idempotency protection.
- VNPAY secret, database credentials và signing keys ở Secrets Manager.
- Không log secret, raw signing input, raw card data hoặc full sensitive callback payload.
- Callback payload lưu encrypted/minimized theo retention policy.
- Audit bắt buộc cho: payment initiation, cash amount/change, state transition, invalid callback, mismatch callback, reconciliation, manual override, refund và authorization denial.
- Audit event có actor type/id, tenant, branch, payment/order reference, action, reason, correlation ID, timestamp và outcome.

## 8. Observability và vận hành

Structured logs phải có `tenantId`, `branchId`, `paymentId`, `orderId`, `checkoutAttemptId`, `provider`, `providerTransactionId` (nếu có), `correlationId` và `eventId`, nhưng không có secret/raw sensitive payload.

Metrics/alerts tối thiểu:

- payment initiation success/failure theo method;
- cash validation failure và change distribution;
- VNPAY QR creation failure/latency;
- IPN invalid signature, mapping mismatch, duplicate và out-of-order count;
- payment pending age, expired count, reconciliation required count/age;
- provider query failure/latency;
- outbox retry/DLQ và event lag;
- duplicate side-effect detector cho `PaymentPaid`;
- refund pending/failure/discrepancy P1.

Runbook phải có hướng dẫn cho late payment, provider unknown, Payment `PAID` nhưng Inventory commit thất bại, duplicate IPN, DLQ replay và manual reconciliation.

## 9. Non-functional requirements

- API retry an toàn và không tạo duplicate payment/refund/event.
- Hệ thống chịu được tối thiểu 10 initiate attempts/giây trong 5 phút mà không tạo duplicate payment cho cùng checkout attempt.
- Reconciliation job có lease, retry/backoff, DLQ/manual queue và không chạy vô hạn không quan sát được.
- Payment provider outage không làm hệ thống khẳng định sai `FAILED`; phải trả pending/retryable/reconciliation state phù hợp.
- Local/demo có provider adapter giả lập deterministic; sandbox adapter vẫn được contract-test bằng fixture thật đã sanitize.
- Test bắt buộc: cash insufficient/exact/change, duplicate create, VNPAY QR duplicate, invalid checksum, wrong amount/order, duplicate/out-of-order/late IPN, provider timeout, reconciliation race, service restart, outbox retry/DLQ, cross-tenant/branch access và refund/split tender P1.

## 10. Acceptance criteria

1. Cash amount nhỏ hơn total bị từ chối; amount bằng total có change `0`; amount lớn hơn total trả đúng change.
2. Retry cash request cùng idempotency key trả cùng `paymentId` và không phát duplicate `PaymentPaid`.
3. Hai request VNPAY đồng thời cho một checkout attempt chỉ tạo một payment `PENDING` và một QR active.
4. VNPAY QR response chứa expiry 15 phút theo demo policy và Order giữ reservation cùng policy.
5. Invalid checksum không đổi state, không phát payment event và tạo security/audit evidence.
6. Valid signature nhưng sai order/payment/amount không chuyển `PAID`; callback được lưu và route reconciliation khi cần.
7. Duplicate valid IPN không tạo duplicate transition, `PaymentPaid` hoặc Saga finalize.
8. Out-of-order/late IPN không ghi đè terminal state; late success sau release tạo `SETTLED_LATE` reconciliation exception, chỉ phát `PaymentReconciliationRequired.v1` và không resurrect Order.
9. Reconciliation worker claim độc quyền, retry provider query và phân biệt definitive result với unknown result.
10. Payment success chỉ là `PaymentPaid`; `OrderPaid` chỉ xuất hiện sau Inventory commit và Order finalization.
11. Mọi API đọc/ghi không thể truy cập payment của tenant hoặc branch khác bằng cách đổi ID.
12. P1 split tender không finalize khi tổng paid chưa đủ; refund không vượt paid amount và retry refund không tạo duplicate.
13. History/report chỉ read-only, có filter/scope và không làm thay đổi ledger gốc.

## 11. Ràng buộc cần giữ khi sang Architecture/Contracts

- VNPAY checksum algorithm, canonical field ordering và provider response mapping phải được đóng gói trong adapter, có fixture/contract test theo sandbox contract; domain không phụ thuộc field provider.
- Contract parent ở `docs/designs/pos-platform/2026-10-12-contracts.md` cần materialize các field payment response, IPN result, late-payment reconciliation và event schema trước khi implement cross-service.
- Step Functions phải biểu diễn rõ timeout, Inventory commit failure, compensation và reconciliation required; Payment không tự quyết định Order finalization.
- P0 không tự động refund khi Inventory commit thất bại; runbook/manual reconciliation là bắt buộc trước demo production-like.
- Các bảng P1 có thể được thiết kế cùng boundary nhưng không được làm chậm hoặc thay đổi invariant P0.
- Internal payment initiation phải xác thực service identity/trusted tenant context; không được coi các ID và amount trong request body là bằng chứng sở hữu hoặc nguồn sự thật độc lập.
