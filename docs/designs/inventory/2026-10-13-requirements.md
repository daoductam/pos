Started at: 2026-10-13 00:00:00
Finished at: 2026-10-13 00:00:00

# Module 6 — Inventory service: Requirements

**Ngày:** 2026-10-13  
**Trạng thái:** Approved  
**Vision:** [Inventory Vision](./2026-10-13-vision.md)  
**Phạm vi:** M — Standard Service Flow

## 1. Mục đích và ranh giới

Inventory service là source of truth cho:

- ingredient master và base unit;
- versioned recipe của catalog item/variant/modifier;
- branch stock balance và append-only stock ledger;
- reservation trong checkout Saga;
- receipt, count adjustment và low-stock alert ở P1;
- read-only purchase forecast ở P2.

Inventory không sở hữu catalog name/price, Order total, payment state, loyalty points hoặc purchase order. Service khác không được đọc trực tiếp Inventory DB.

## 2. Actors và quyền

| Actor | Quyền |
|---|---|
| Owner | Toàn tenant: ingredient, recipe, opening balance, receipt, count, threshold, forecast và audit read. |
| Manager | Ingredient/recipe/recipe version dùng chung tenant; stock operations trong branch được cấp; xem forecast/alert trong branch được cấp. |
| Cashier | Đọc recipe/availability cần cho POS; không tạo manual stock mutation. |
| Kitchen | Đọc ingredient/recipe projection cần cho KDS nếu được cấp; không tạo stock mutation. |
| Order/Step Functions workload | Reserve, commit, release và reconciliation command theo workload identity. |
| Reporting/Audit/Notification workload | Consume event theo allowlist; không sửa Inventory state. |

Service và repository đều phải enforce `tenantId`; stock operation phải enforce `branchId`. Client không được quyết định scope chỉ bằng body hoặc UI.

## 3. Chuẩn dữ liệu và invariant chung

### 3.1 Quantity

- Base unit P0: `g`, `ml`, `unit`.
- Quantity, on-hand và reserved dùng `NUMERIC(18,3)`.
- Không dùng floating point.
- Phép tính làm tròn ở bước cuối bằng `HALF_UP` tới scale 3.
- Quantity đầu vào phải dương; zero chỉ dùng để biểu diễn recipe line đã bị disable, không tạo stock transaction.
- `available = onHand - reserved`.
- Không transaction nào được commit nếu `onHand < 0` hoặc `available < 0`.

### 3.2 Tenant/branch

- Ingredient master và recipe thuộc tenant, không nhân bản theo branch.
- Balance, reservation, receipt, count session, alert và stock ledger thuộc tenant + branch.
- Reservation chỉ được dùng cho branch của order/checkout attempt.
- Cross-tenant hoặc cross-branch resource ID phải trả lỗi ownership/authorization, không làm lộ dữ liệu.

### 3.3 Immutable history

- Recipe version đã active hoặc đã được reservation tham chiếu không được sửa nội dung.
- Reservation snapshot và stock ledger entry không được update/delete.
- Ingredient/recipe/variant reference đã được sử dụng không được hard delete.
- Correction phải tạo entry/state mới liên kết source entry hoặc source operation.

## 4. Domain state machines

### 4.1 Recipe version

```text
DRAFT → ACTIVE → INACTIVE → ARCHIVED
```

- Mỗi `(tenantId, catalogItemId, variantId)` có tối đa một `ACTIVE` version.
- Activate version mới phải atomically deactivate version cũ.
- Recipe không được activate nếu line có ingredient inactive, quantity không hợp lệ, duplicate ingredient/modifier line hoặc reference ngoài tenant.
- `ARCHIVED` không thể activate lại; tạo version mới nếu cần khôi phục.

### 4.2 Reservation

```text
RESERVED → COMMITTED
RESERVED → RELEASED
RESERVED → EXPIRED
RESERVED → RECONCILIATION_REQUIRED
```

- Chỉ `RESERVED` được commit/release/expire.
- `COMMITTED`, `RELEASED`, `EXPIRED` và `RECONCILIATION_REQUIRED` là terminal; command lặp trả kết quả terminal hoặc no-op theo command semantics.
- `RECONCILIATION_REQUIRED` dùng khi payment success nhưng Inventory không thể hoàn thành commit/release tự động. Không được commit/release lại reservation này; Saga/operations xử lý compensation ở boundary riêng, còn mọi stock correction phải tạo ledger entry mới.
- Không có transition quay lại `RESERVED`.

### 4.3 Low-stock alert

```text
OPEN → ACKNOWLEDGED → RESOLVED
OPEN → RESOLVED
```

- Alert mới chỉ được tạo khi available crossing từ trên threshold xuống `<= threshold`.
- Sau khi available vượt threshold, crossing tiếp theo được tạo alert mới.
- Acknowledge không thay đổi stock condition.

### 4.4 Count session

```text
OPEN → PENDING_APPROVAL → APPROVED
OPEN → CANCELLED
PENDING_APPROVAL → CANCELLED
```

- `APPROVED` mới tạo `COUNT_ADJUSTMENT`.
- Employee có thể nhập count nếu được giao; Manager/Owner phê duyệt adjustment.
- Count session không khóa toàn branch và không dừng bán hàng.

## 5. Functional requirements P0

### FR-INV-001 — Ingredient master

Inventory phải cho phép Owner/Manager trong scope:

- tạo ingredient với code/name, base unit và optional purchase conversion;
- sửa metadata bằng optimistic version;
- activate/inactivate ingredient;
- xem ingredient theo tenant và filter/sort allowlist.

Validation:

- code unique trong tenant, normalize trước khi so sánh;
- base unit thuộc `g/ml/unit`;
- conversion không âm, conversion factor dương;
- ingredient inactive không được dùng trong recipe active mới.

### FR-INV-002 — Versioned recipe

Inventory phải cho phép tạo draft, validate, activate và archive recipe cho `catalogItemId + variantId`.

Recipe line gồm:

- `ingredientId` bắt buộc;
- `modifierOptionId` nullable, dùng để scope ingredient line cho modifier đã chọn; line của base item/variant có giá trị null;
- `quantityPerSale`;
- `wasteFactor`, mặc định `1.000`, không nhỏ hơn `1.000`;
- base unit snapshot và metadata cần cho audit.

Demand formula:

```text
requiredQuantity = orderQuantity × quantityPerSale × wasteFactor
```

Recipe version phải lưu line snapshot và không phụ thuộc runtime vào Catalog DB.

### FR-INV-003 — Opening balance

Owner/Manager phải có thể tạo `OPENING_BALANCE` cho ingredient tại branch.

- Opening balance làm tăng `onHand`, không làm tăng `reserved`.
- Bắt buộc reason, actor, branch và idempotency key.
- Duplicate source/idempotency key không tạo entry thứ hai.
- Không cho opening balance đưa balance về trạng thái âm.

### FR-INV-004 — Reserve ingredients

Reserve phải:

1. xác thực workload identity, tenant, branch và checkout context;
2. validate request hash/idempotency key;
3. resolve active recipe cho mọi item/variant/modifier line;
4. expand recipe thành tổng demand theo ingredient;
5. lock balance rows theo thứ tự `ingredientId` tăng dần;
6. kiểm tra `available >= requiredQuantity`;
7. tăng `reserved` atomically nếu đủ;
8. lưu recipe/input/demand snapshot và phát outbox event sau commit.

Payload tối thiểu gồm `orderId`, `checkoutAttemptId`, `branchId`, `expiresAt`, `lines[]`, `idempotencyKey` và correlation context. Mỗi line gồm `lineId`, `catalogItemId`, `variantId`, selected `modifierOptionIds` và `quantity`.

Idempotency:

- Unique business key: `(tenantId, checkoutAttemptId)`.
- Request đầu tiên lưu canonical request hash.
- Retry cùng hash trả kết quả cũ.
- Retry khác hash trả `409 RESERVATION_REQUEST_CONFLICT` và không thay đổi state.
- `Idempotency-Key` bắt buộc ở transport layer.

Failure behavior:

- thiếu/inactive recipe: fail closed;
- không đủ available: fail với ingredient-level shortage details, không tăng reserved cho bất kỳ ingredient nào;
- lock timeout/deadlock: bounded retry; hết retry trả conflict/temporary failure;
- lỗi sau DB rollback: không phát success response hoặc success event.

### FR-INV-005 — Commit reservation

`CommitReservation` chỉ được gọi bởi Step Functions/Saga sau khi Payment đã được xác thực thành công.

Payload phải có `reservationId`, `checkoutAttemptId`, `orderId`, `paymentId`, `paymentAttemptId`, `paymentStatus=SUCCEEDED`, `paidAmountMinor`, `currency` và idempotency context.

Inventory phải:

- kiểm tra ownership của reservation và branch;
- kiểm tra payment context khớp checkout context đã lưu;
- lock reservation trước, rồi lock balance rows theo thứ tự cố định;
- append một `SALE_COMMITTED` entry cho từng ingredient demand;
- giảm `reserved`, giảm `onHand` và chuyển reservation sang `COMMITTED` trong cùng transaction;
- ghi outbox event sau commit.

Inventory không gọi Payment service trong transaction và không tự consume `PaymentPaid` để commit.

Nếu payment context không khớp, trả `PAYMENT_CONTEXT_MISMATCH` và không thay đổi kho. Nếu reservation đã `COMMITTED`, retry trả terminal result. Nếu đã `RELEASED/EXPIRED`, trả conflict và tạo reconciliation signal khi cần.

### FR-INV-006 — Release reservation

`ReleaseReservation` được gọi khi payment/order fail, timeout hoặc Saga compensation.

- Lock reservation trước khi cập nhật.
- Chỉ reservation `RESERVED` mới giảm `reserved` và chuyển `RELEASED`.
- Không tạo `SALE_COMMITTED`.
- Retry trên `RELEASED` trả thành công idempotent.
- Release trên `COMMITTED`, `EXPIRED` hoặc `RELEASED` không được làm thay đổi balance.

### FR-INV-007 — Reservation expiry/reconciliation

- `expiresAt` là bắt buộc và do checkout attempt cung cấp.
- TTL mặc định 15 phút, giới hạn tối đa 30 phút.
- Cleanup job tìm reservation `RESERVED` đã quá hạn, dùng cùng lock rule với release và chuyển sang `EXPIRED`.
- Commit sau expiry trả `RESERVATION_EXPIRED`, không tự trừ kho.
- Payment success sau expiry tạo `RECONCILIATION_REQUIRED` signal để Saga xử lý refund/compensation.
- Cleanup/reconciliation phải idempotent và có metric về age, retry và terminal failure.

### FR-INV-008 — Stock ledger/balance projection

P0 ledger types:

- `OPENING_BALANCE`: quantity dương;
- `SALE_COMMITTED`: quantity âm;
- P1 mở rộng `RECEIPT`, `COUNT_ADJUSTMENT`, `WASTE`, `RETURN`.

Mỗi ledger entry phải có tenant, branch, ingredient, signed quantity, base unit, source type/id, reason nếu cần, actor/workload, occurred time, idempotency key, correlation ID và immutable metadata.

Balance projection phải giữ `onHand`, `reserved`, `available` và version/update timestamp. Projection rebuild phải tái tạo được `onHand` từ ledger và `reserved` từ reservation state.

## 6. Functional requirements P1

### FR-INV-101 — Receipt

- Owner/Manager tạo receipt theo branch.
- Receipt có supplier/reference, quantity, unit conversion, optional `lotCode`/`expiresAt` và reason.
- Receipt làm tăng `onHand`; không sửa reservation.
- P1 không phân bổ lot/FEFO khi reserve.

### FR-INV-102 — Count session

- Mở count session với snapshot `onHand`, `reserved`, available và timestamp.
- Nhập counted quantity theo ingredient.
- Tính discrepancy từ snapshot; hiển thị giao dịch concurrent kể từ snapshot.
- Manager/Owner approve để tạo `COUNT_ADJUSTMENT`.
- Adjustment không được làm `onHand` hoặc available âm.

### FR-INV-103 — Waste/return adjustment

- `WASTE` giảm onHand, bắt buộc reason và actor.
- `RETURN` tăng onHand khi nguyên liệu còn sử dụng được, bắt buộc source/reference và reason.
- Không sửa entry gốc; correction liên kết source operation.

### FR-INV-104 — Low-stock alert

- Threshold cấu hình theo tenant/branch/ingredient.
- Trigger theo available, không theo onHand riêng.
- Threshold crossing tạo một alert idempotent.
- Alert hỗ trợ `OPEN`, `ACKNOWLEDGED`, `RESOLVED`, actor, note và audit.
- Threshold update phải re-evaluate và audit.
- Phát `InventoryLowStockDetected.v1` cho downstream.

## 7. Functional requirements P2

### FR-INV-201 — Read-only forecast

- Query forecast theo branch, ingredient và forecast date.
- Baseline dùng weighted moving average 28 ngày từ `SALE_COMMITTED`, có weekday adjustment khi đủ dữ liệu.
- Nếu dưới 14 ngày dữ liệu, dùng average trên dữ liệu có sẵn.
- Nếu không có dữ liệu, trả `INSUFFICIENT_HISTORY` và không đoán quantity.
- Công thức đề xuất:

```text
forecastDemand = demandDuringLeadTime + safetyStock - available
```

- Response có input window, calculatedAt, modelVersion, confidence/quality và freshness.
- Không tạo purchase order và không tự nhập kho.

## 8. API requirements

### 8.1 Style và security

- REST + OpenAPI 3.1; internal Saga commands nằm dưới internal API boundary.
- User API dùng Cognito access token với tenant/branch/role claims; internal API dùng workload identity.
- Service và repository đều kiểm tra tenant; branch-scoped endpoint kiểm tra branch membership.
- Error response chuẩn gồm `code`, `message`, `details[]` và `requestId`.
- Machine-readable codes tối thiểu: `validation_error`, `forbidden`, `not_found`, `conflict`, `insufficient_stock`, `reservation_request_conflict`, `reservation_expired`, `payment_context_mismatch`, `reconciliation_required`, `lock_timeout`.

### 8.2 Collections

- Offset pagination cho ingredient, recipe, receipt, count session và low-stock alert.
- Cursor pagination theo `occurredAt + id` cho stock ledger và reservation history.
- Page size có maximum; filter/sort dùng allowlist.
- Không expose raw SQL filter hoặc arbitrary column sort.

### 8.3 Idempotency and concurrency

- Reserve/commit/release/opening balance/receipt/count adjustment đều yêu cầu idempotency key.
- State-changing API trả cùng semantic result khi retry cùng request/hash.
- Conflict response phải chỉ ra current state và resource ID, không lộ dữ liệu ngoài scope.

## 9. Event and integration requirements

### 9.1 Events

- `InventoryReserved.v1`
- `InventoryReservationCommitted.v1`
- `InventoryReservationReleased.v1`
- `InventoryReservationExpired.v1`
- `InventoryStockChanged.v1`
- `InventoryLowStockDetected.v1`
- `InventoryReconciliationRequired.v1`

Event envelope phải có `eventId`, `eventType`, `occurredAt`, `tenantId`, `branchId`, `aggregateId`, `aggregateVersion`, `correlationId` và `causationId`.

### 9.2 Delivery

- Ghi outbox cùng transaction nghiệp vụ.
- Publish at-least-once.
- Consumer deduplicate theo event ID hoặc semantic source key.
- Event không chứa payment secret, credential hoặc PII không cần thiết.
- Event replay/out-of-order phải idempotent hoặc đưa vào reconciliation.

## 10. Non-functional requirements

| Category | Requirement |
|---|---|
| Consistency | Reserve/commit/release và balance mutation phải strongly consistent trong một branch DB transaction. |
| Availability | Retry được command sau transient error mà không tạo duplicate reservation/ledger. |
| Performance | P0 reserve/commit/release hướng tới parent target p95 ≤ 500 ms trong tải demo; lock wait và retry phải có metric. |
| Concurrency | Pessimistic row lock theo thứ tự ingredient ID; transaction ngắn, không external call dưới lock. |
| Security | Tenant/branch defense-in-depth, workload identity cho Saga, role authorization cho manual mutation. |
| Auditability | Ledger/reservation/count/alert mutation có actor/workload, reason/source, correlation và timestamp UTC. |
| Observability | Structured logs, metrics cho shortage, contention, expiry, reconciliation, outbox lag và forecast freshness. |
| Reliability | Outbox/inbox, bounded retry, dead-letter/reconciliation path cho event và Saga failure. |
| Retention | Ledger, reservation và audit tối thiểu 7 năm; đánh giá partition khi ledger vượt khoảng 10 triệu dòng. |
| Time | Tất cả timestamp lưu UTC; forecast và alert hiển thị theo branch timezone ở read layer. |

## 11. Error and edge-case requirements

- Một order có nhiều line dùng cùng ingredient phải được aggregate trước khi lock/check để tránh kiểm tra rời rạc.
- Nếu bất kỳ ingredient nào thiếu, toàn bộ reserve rollback; không partial reservation.
- Concurrent reserve phải có một kết quả thắng theo DB lock; request còn lại nhận insufficient stock hoặc retryable conflict.
- Commit và release chạy đồng thời phải có một terminal winner; command đến sau không được làm đảo state.
- Recipe activation và reserve đồng thời phải chọn một version nhất quán; reservation snapshot không được trộn line giữa hai version.
- Duplicate event/command không tạo ledger, alert hoặc reservation usage trùng.
- Tenant/branch mismatch trả authorization/ownership error trước khi trả resource detail.
- Quantity vượt precision, sai unit hoặc overflow trả validation error.
- Missing recipe, inactive ingredient và invalid modifier mapping fail closed.
- Payment success nhưng inventory terminal failure tạo reconciliation record, không tự ghi âm kho.
- Projection lag phải được đo; API đọc balance phải nêu freshness/version nếu đọc từ projection không đồng bộ.

## 12. Acceptance criteria theo priority

### P0

- Có thể tạo ingredient base unit và recipe active cho variant.
- Một ly trà sữa với size/topping được expand thành đúng tổng demand nguyên liệu.
- Reserve đồng thời không làm available âm hoặc reserved tăng trùng.
- Payment success + Saga commit tạo đúng `SALE_COMMITTED` và reservation terminal `COMMITTED`.
- Payment/order fail hoặc timeout release đúng reservation, không tạo sale ledger.
- Retry cùng request trả semantic result cũ; payload khác cùng checkout attempt bị từ chối.
- Recipe change sau reserve không làm thay đổi snapshot cũ.
- Branch A không đọc/consume balance branch B.
- Opening balance và mọi stock history rebuild được.

### P1

- Receipt làm tăng onHand đúng base unit.
- Count session không dừng bán hàng và adjustment yêu cầu approval.
- Waste/return/count ledger không sửa lịch sử.
- Low-stock alert đúng threshold crossing, không duplicate và re-arm sau khi tồn hồi phục.

### P2

- Forecast có baseline, fallback, quality/freshness và model version.
- Không có forecast khi thiếu dữ liệu; không tự tạo purchase order.

## 13. Traceability

| Vision decision | Requirements |
|---|---|
| Base units và no negative stock | §3.1, FR-INV-001, FR-INV-004, FR-INV-008 |
| Versioned recipe và modifier components | §3.3, FR-INV-002, FR-INV-004 |
| Reserve/commit/release Saga | §4.2, FR-INV-004–007, §11 |
| Pessimistic locking | FR-INV-004–006, §10 |
| Branch-specific stock | §2, §3.2, acceptance P0 |
| Receipt/count/alert | FR-INV-101–104 |
| Forecast baseline | FR-INV-201 |
| REST/idempotency/outbox | §8, §9, §10 |

## 14. Next phase inputs

- Architecture phải chốt transaction boundaries, table/index shape, lock wait/retry, outbox publisher và reconciliation worker.
- API contracts phải chốt OpenAPI schemas, response/error examples, internal workload auth và event JSON Schema.
- Database design phải thêm descriptive comment cho mọi column, unique/check constraints, projection rebuild strategy và migration order.
- Sequence diagrams phải mô tả reserve success/shortage, payment success commit, compensation release, expiry và concurrent lock behavior.

## Approval gate

- **Current status:** Approved
- **Next action:** Architecture design
