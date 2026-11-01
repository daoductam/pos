# Module 5 — Loyalty service: Architecture

**Ngày:** 2026-10-13  
**Trạng thái:** Draft — chờ phê duyệt Architecture  
**Phạm vi:** M — Standard Service Flow  
**Module:** Khách hàng thân thiết & khuyến mãi (Loyalty service)

## 1. Boundary và ownership

Loyalty service sở hữu:

- Customer profile và phone lookup trong phạm vi tenant.
- Append-only points ledger, point lots, redemption allocation và balance projection.
- Point hold lifecycle theo checkout attempt.
- Membership tier hiện tại và lịch sử tier.
- Earn/redeem/expiry point policies theo tenant.
- Promotion versions, rule evaluation, voucher reservation và usage history.
- Inbox/outbox, idempotency, audit và worker schedules của Loyalty.

Loyalty không sở hữu hoặc không làm:

- Không đọc trực tiếp database của Order, Payment, Catalog hoặc Inventory.
- Không tự tính lại Catalog price, VAT, phí, tip hoặc Order grand total.
- Không phát `OrderPaid.v1`; chỉ Order phát canonical paid-order fact.
- Không tự finalize Order, Payment hoặc Inventory.
- Không sửa/xóa ledger entry; projection có thể rebuild.

Order/Saga gọi Loyalty đồng bộ cho quote/hold/commit/release. Order phát `OrderPaid.v1` sau khi checkout finalize. Order là producer của business-level `OrderRefunded.v1` sau khi full refund đã được xác nhận theo contract; Payment chỉ cung cấp payment/refund fact cho Saga/Order.

## 2. Topology

```text
POS/Angular
    → API Gateway + Cognito
    → Order API / Step Functions
          ├─ internal REST: Loyalty quote/hold/commit/release
          └─ EventBridge → Loyalty SQS queue
                              ├─ OrderPaid.v1
                              ├─ OrderRefunded.v1
                              └─ checkout failure/cancel/expiry

Customer/Staff API
    → Loyalty API adapter
    → Loyalty application/domain
    → PostgreSQL Loyalty DB
          ├─ customers / balance projections
          ├─ points ledger / lots / allocations / holds
          ├─ promotions / vouchers / reservations / usage
          ├─ inbox / outbox / audit
          └─ worker run metadata

Loyalty outbox → EventBridge → Reporting / Audit / downstream consumers

Scheduler → Loyalty worker queue
    → expiry worker / tier worker / reservation cleanup / projection rebuild / DLQ replay

Loyalty → KMS envelope encryption + HMAC key / Secrets Manager
Loyalty → CloudWatch/OpenTelemetry metrics, logs and traces
```

### 2.1 Component responsibilities

| Component | Trách nhiệm |
|---|---|
| Public API adapter | Customer lookup/create, staff read, portal read; request validation và auth/scope |
| Internal API adapter | Promotion quote, point hold/commit/release, voucher lifecycle; workload identity |
| Customer module | Normalize phone, encrypted PII, protected hash, profile state và access policy |
| Points application service | Accrual, redemption, reversal, balance projection và idempotency orchestration |
| Points domain | Ledger invariants, FIFO allocation, hold state machine và reversal lineage |
| Promotion engine | Load immutable active rule versions, evaluate conditions/actions, resolve conflicts và explain result |
| Voucher module | Code lookup, limits, reservation/consume/release và concurrent usage protection |
| Event consumer/inbox | Validate event, dedupe, retry/DLQ và dispatch domain command |
| Outbox publisher | Publish Loyalty facts at-least-once sau local transaction commit |
| Scheduled workers | Expiry, tier recalculation, reservation cleanup, projection rebuild và DLQ replay |
| PostgreSQL repositories | Explicit tenant predicates, constraints, row locks/optimistic versioning |
| Audit/observability adapter | PII access, config publish, reversal, authorization failure, metrics/traces |

## 3. Communication và consistency

### 3.1 Synchronous calls

- POS/Angular chỉ gọi public Loyalty API sau Cognito authorization.
- Order/Saga gọi internal Loyalty API cho quote và point/voucher hold lifecycle.
- Quote/hold đọc primary DB; không đọc cache hoặc replica có thể stale.
- Internal request mang `tenantId`, `branchId`, `orderId`, `checkoutAttemptId`, correlation và idempotency context; Loyalty đối chiếu trusted workload context.
- Synchronous calls dùng timeout, bounded retry cho lỗi transient và fail closed khi không thể xác định scope, policy hoặc số dư.

### 3.2 Asynchronous events

- Order events đi qua EventBridge → SQS; delivery at-least-once.
- Consumer ghi `inbox_events` và domain side effect trong cùng PostgreSQL transaction.
- Chỉ ack message sau khi transaction commit. Duplicate event trở thành no-op theo unique `eventId`/source key.
- Poison event retry hữu hạn rồi vào DLQ; replay đi qua cùng inbox/idempotency path.
- Loyalty outbox ghi trong cùng transaction với ledger/hold/projection và publish ngoài transaction.

### 3.3 Transaction boundary

Trong một transaction Loyalty DB:

1. Claim/check idempotency hoặc inbox event.
2. Validate tenant, customer, order/checkout ownership và current state.
3. Lock customer/hold/lots hoặc kiểm tra optimistic version.
4. Ghi ledger, lot allocation, hold/reservation state và balance/tier projection.
5. Ghi audit/outbox và đánh dấu inbox/idempotency processed.
6. Commit atomic.

Không giữ transaction mở trong lúc gọi Order, Payment, broker, KMS network API hoặc provider bên ngoài. Không dùng distributed transaction; Saga điều phối commit/release/reversal.

## 4. Data architecture

### 4.1 Logical write model

Database riêng PostgreSQL, UUID v7, `BIGINT` cho points/money minor unit và `TIMESTAMPTZ` UTC. Mọi bảng nghiệp vụ có `tenant_id`; customer/ledger access luôn thêm tenant predicate.

Các bảng và cột logic dưới đây là normative ở mức kiến trúc. Migration/SQL chi tiết và index DDL sẽ được chốt ở Contracts.

#### `customers`

| Cột | Mục đích |
|---|---|
| `id` | Định danh customer UUID v7. |
| `tenant_id` | Tenant sở hữu hồ sơ. |
| `phone_ciphertext` | Số điện thoại đã mã hóa để phục vụ hiển thị/đối soát được kiểm soát. |
| `phone_lookup_hash` | HMAC/peppered hash chuẩn hóa dùng lookup và unique constraint. |
| `display_name` | Tên hiển thị tối thiểu của customer, nullable theo P0. |
| `status` | Trạng thái hồ sơ, ví dụ `ACTIVE` hoặc `BLOCKED`. |
| `current_tier` | Projection hạng hiện tại để đọc nhanh. |
| `created_at` | Thời điểm tạo hồ sơ UTC. |
| `updated_at` | Thời điểm cập nhật hồ sơ UTC. |
| `version` | Optimistic concurrency version của hồ sơ. |

#### `point_ledger_entries`

| Cột | Mục đích |
|---|---|
| `id` | Định danh ledger entry UUID v7. |
| `tenant_id` | Tenant sở hữu entry. |
| `customer_id` | Customer nhận hoặc sử dụng điểm. |
| `entry_type` | `EARNED`, `REDEEMED`, `REVERSED` hoặc `EXPIRED`. |
| `points_delta` | Delta có dấu; entry đối ứng không sửa entry gốc. |
| `source_type` | Loại nguồn như `ORDER_PAID`, `ORDER_REFUND`, `POINT_HOLD`, `EXPIRY`. |
| `source_id` | ID nguồn dùng replay/dedupe/audit. |
| `related_entry_id` | Ledger entry gốc được offset/reverse khi có liên kết. |
| `policy_version` | Earn/redeem/expiry policy đã áp dụng. |
| `idempotency_key` | Khóa chống duplicate side effect. |
| `occurred_at` | Thời điểm business effect xảy ra. |
| `metadata_json` | Metadata đã allowlist, không chứa PII không cần thiết. |
| `created_at` | Thời điểm ghi entry vào DB. |

#### `point_lots`

| Cột | Mục đích |
|---|---|
| `id` | Định danh point lot UUID v7. |
| `tenant_id` | Tenant sở hữu lot. |
| `customer_id` | Customer sở hữu lot. |
| `source_entry_id` | `EARNED` entry tạo lot. |
| `earned_at` | Thời điểm lot được earn. |
| `expires_at` | Thời điểm lot hết hạn theo policy snapshot. |
| `original_points` | Số điểm ban đầu của lot. |
| `remaining_points` | Số điểm còn lại chưa allocate/expire. |
| `status` | `OPEN`, `DEPLETED` hoặc `EXPIRED`. |
| `created_at` | Thời điểm tạo lot. |
| `updated_at` | Thời điểm cập nhật remaining/status. |
| `version` | Optimistic concurrency version của lot. |

#### `point_lot_allocations`

| Cột | Mục đích |
|---|---|
| `id` | Định danh allocation UUID v7. |
| `tenant_id` | Tenant sở hữu allocation. |
| `lot_id` | Lot bị allocate. |
| `customer_id` | Customer của allocation, dùng defense-in-depth. |
| `allocation_type` | `HOLD`, `REDEEM`, `RELEASE`, `REVERSE` hoặc `EXPIRE`. |
| `source_type` | Loại command/event tạo allocation. |
| `source_id` | ID hold, ledger entry, refund hoặc job run. |
| `points` | Số điểm allocation của lot. |
| `created_at` | Thời điểm allocation. |

#### `point_holds`

| Cột | Mục đích |
|---|---|
| `id` | Định danh hold UUID v7. |
| `tenant_id` | Tenant của checkout. |
| `customer_id` | Customer bị giữ điểm. |
| `order_id` | Order opaque reference. |
| `checkout_attempt_id` | Checkout attempt sở hữu lifecycle hold. |
| `requested_points` | Số điểm client yêu cầu. |
| `held_points` | Số điểm thực tế đã giữ sau validation. |
| `state` | `HELD`, `COMMITTED`, `RELEASED` hoặc `EXPIRED`. |
| `expires_at` | Hạn hold lấy từ Order checkout attempt. |
| `idempotency_key` | Khóa retry của hold command. |
| `created_at` | Thời điểm tạo hold. |
| `updated_at` | Thời điểm chuyển state. |
| `version` | Optimistic concurrency version của hold. |

#### `loyalty_balances`

| Cột | Mục đích |
|---|---|
| `tenant_id` | Tenant của projection. |
| `customer_id` | Customer của projection. |
| `earned_points` | Tổng điểm earn được theo projection. |
| `redeemed_points` | Tổng điểm đã commit redeem. |
| `reversed_points` | Tổng delta reversal đã áp dụng. |
| `expired_points` | Tổng điểm đã expire. |
| `held_points` | Tổng điểm đang ở active holds. |
| `available_points` | Điểm có thể tiếp tục sử dụng. |
| `projection_version` | Version/sequence cuối đã materialize. |
| `calculated_at` | Thời điểm projection được cập nhật. |

#### Tier, promotion và voucher tables

| Bảng | Cột logic bắt buộc và mục đích |
|---|---|
| `loyalty_point_policies` | `id` định danh policy; `tenant_id` tenant; `version` policy bất biến; `earn_amount_minor_per_unit` ngưỡng giá trị earn; `earn_points_per_unit` điểm earn mỗi unit; `redeem_points_per_unit` điểm cho một redeem unit; `redeem_amount_minor_per_unit` giá trị discount mỗi unit; `max_redeem_percent_bps` giới hạn redeem theo basis points; `default_expiry_days` thời hạn lot mặc định; `status` lifecycle; `effective_from`/`effective_to` hiệu lực; `created_by` actor publish; `created_at` thời điểm tạo. |
| `membership_tier_configs` | `id` định danh config; `tenant_id` tenant; `metric` là `ELIGIBLE_SPEND_MINOR` hoặc `EARNED_POINTS`; `tier_code` là tier; `threshold_value` ngưỡng; `rolling_window_days` cửa sổ; `version` policy version; `status` lifecycle; `effective_from`/`effective_to` hiệu lực; `created_by` actor publish; `created_at` thời điểm tạo. |
| `membership_tier_history` | `id` định danh transition; `tenant_id` tenant; `customer_id` customer; `from_tier` hạng cũ; `to_tier` hạng mới; `metric` metric xét; `metric_value` giá trị tại lần tính; `policy_version` config đã dùng; `effective_at` thời điểm hiệu lực; `source_run_id` job/event tạo transition; `created_at` thời điểm ghi. |
| `promotions` | `id` định danh promotion; `tenant_id` tenant; `promotion_key` business key ổn định; `status` lifecycle tổng; `created_at`/`updated_at` timestamps. |
| `promotion_versions` | `id` định danh immutable version; `tenant_id` tenant; `promotion_id` parent; `version` số version; `rule_type` loại rule; `priority` thứ tự ưu tiên; `stackable` cho phép stack; `conflict_group` nhóm xung đột; `scope_json` branch/channel scope; `rule_config_json` condition/action đã validate; `valid_from`/`valid_to` hiệu lực; `status` draft/published/retired; `published_by` actor; `published_at` thời điểm publish; `created_at` thời điểm tạo. |
| `vouchers` | `id` định danh voucher; `tenant_id` tenant; `code_normalized` code uppercase; `promotion_version_id` rule áp dụng; `status` lifecycle; `total_limit` giới hạn toàn tenant; `per_customer_limit` giới hạn customer; `valid_from`/`valid_to` hiệu lực; `branch_scope_json` branch allowlist; `channel_scope_json` channel allowlist; `min_spend_minor` minimum spend; `created_at`/`updated_at` timestamps. |
| `voucher_reservations` | `id` định danh reservation; `tenant_id` tenant; `voucher_id` voucher; `customer_id` customer; `order_id` order; `checkout_attempt_id` attempt; `state` `RESERVED/CONSUMED/RELEASED/EXPIRED`; `expires_at` hạn lấy từ attempt; `idempotency_key` khóa reserve; `reserved_at` thời điểm giữ; `committed_at` thời điểm consume; `released_at` thời điểm release. |
| `voucher_usages` | `id` định danh usage; `tenant_id` tenant; `voucher_id` voucher; `customer_id` customer; `order_id` order; `checkout_attempt_id` attempt; `usage_count` số lượt consume; `consumed_at` thời điểm consume. |

#### Integration, idempotency và audit tables

| Bảng | Cột logic bắt buộc và mục đích |
|---|---|
| `inbox_events` | `id` row; `tenant_id` tenant; `event_id` broker event ID; `event_type` schema type; `aggregate_id` nguồn; `received_at` thời điểm nhận; `processed_at` thời điểm xong; `processing_state` `RECEIVED/PROCESSED/FAILED/DLQ`; `attempt_count` số lần retry; `last_error` lỗi đã sanitize. |
| `outbox_events` | `id` message ID; `tenant_id` tenant; `event_type` event schema; `aggregate_type` loại aggregate; `aggregate_id` aggregate; `payload_json` event envelope; `occurred_at` business time; `publish_state` `PENDING/PUBLISHED/DLQ`; `attempt_count` retry count; `next_attempt_at` lịch retry; `published_at` thời điểm publish; `created_at` thời điểm tạo. |
| `audit_events` | `id` audit ID; `tenant_id` tenant; `actor_type` user/service/worker; `actor_id` actor opaque; `action` hành động; `entity_type` loại entity; `entity_id` entity; `reason` lý do nếu có; `metadata_json` fields được allowlist/masked; `occurred_at` thời điểm audit. |
| `worker_runs` | `id` run ID; `tenant_id` tenant hoặc null cho system run; `worker_type` loại worker; `lease_owner` worker instance; `lease_expires_at` hạn lease; `started_at` bắt đầu; `completed_at` kết thúc; `state` trạng thái run; `processed_count` số item; `failed_count` số lỗi; `last_error` lỗi đã sanitize. |

### 4.2 Constraints và indexes

- Unique `(tenant_id, phone_lookup_hash)` trên `customers`.
- Unique source/idempotency cho accrual: `(tenant_id, source_type, source_id, entry_type)` và unique event ID trong inbox.
- Unique `(tenant_id, checkout_attempt_id)` cho point hold lifecycle; các transition retry update cùng aggregate, không tạo hold thứ hai.
- Index lots theo `(tenant_id, customer_id, expires_at, earned_at, id)` cho FIFO; lock các lot còn `remaining_points > 0` khi allocate.
- Unique `(tenant_id, code_normalized)` cho voucher.
- Unique reservation theo `(tenant_id, voucher_id, checkout_attempt_id, customer_id)`; index active reservations theo `expires_at`.
- Index usage theo `(tenant_id, voucher_id, customer_id, consumed_at)` để kiểm tra limits.
- Index ledger/history theo `(tenant_id, customer_id, occurred_at DESC, id)`; projection đọc theo `(tenant_id, customer_id)`.
- Inbox unique `(tenant_id, event_id)`; outbox index `(publish_state, next_attempt_at, created_at)`.
- Không partition P0. Khi ledger/history đạt ngưỡng vận hành khoảng 10M+ rows và có số liệu retention, đánh giá range partition theo `occurred_at`; unique/idempotency constraint phải được thiết kế tương thích partition.

### 4.3 Projection và rebuild

- `loyalty_balances` và `customers.current_tier` là materialized projections, không phải source of truth.
- Rebuild đọc ledger, lot allocations, active/terminal holds và tier history theo tenant/customer.
- Rebuild chạy ngoài hot path, có run ID, checkpoint/lease và swap projection an toàn.
- Projection mismatch tạo alert/audit; không sửa ledger để chữa projection.

## 5. State machines

### 5.1 Point hold

```text
                 ┌──────────────┐
        create → │    HELD      │ ── OrderPaid ──→ COMMITTED
                 └──────┬───────┘
                        │
              fail/cancel/timeout
                        │
                        ├────────→ RELEASED
                        └────────→ EXPIRED (cleanup sau expiry)
```

- Chỉ `HELD` được chuyển state; terminal retry trả kết quả cũ.
- `COMMITTED` tạo `REDEEMED` ledger và allocation; `RELEASED` không tạo redeemed ledger.
- Hold expiry không tự consume; worker release/expire theo `expires_at` và Order state.

### 5.2 Voucher reservation

```text
RESERVED ── OrderPaid ──→ CONSUMED
    │
    ├── fail/cancel ─────→ RELEASED
    └── expires_at ──────→ EXPIRED
```

- Usage counter chỉ tăng trên `CONSUMED`.
- Concurrent reservation/consume phải kiểm tra limit trong transaction và giữ lịch sử terminal.

### 5.3 Event processing

```text
RECEIVED → PROCESSING → PROCESSED
                  └── retry → RECEIVED
                  └── retry exhausted → DLQ
```

Inbox record không được đánh `PROCESSED` trước khi domain transaction commit.

## 6. Critical flows

Repo chưa có implementation source để trace controller/consumer thực tế. Các flow dưới đây là contract-driven architecture flows, dựa trên các module contract đã duyệt.

### 6.1 Quote, hold và checkout thành công

```mermaid
sequenceDiagram
    autonumber
    participant POS
    participant Order as Order/Saga
    participant Loyalty as Loyalty API
    participant DB as Loyalty PostgreSQL
    participant Payment
    participant Bus as EventBridge/SQS

    POS->>Order: Checkout(customerId, voucher, requestedPoints)
    Order->>Loyalty: Quote + reserve point/voucher
    Loyalty->>DB: Lock customer/lots; evaluate rules; reserve atomically
    DB-->>Loyalty: Quote + hold/reservation IDs + expiry
    Loyalty-->>Order: PromotionQuote + PointHold + VoucherReservation
    Order->>Payment: Initiate payment
    Payment-->>Order: Paid
    Order->>Order: Commit inventory and finalize order
    Order-->>Bus: OrderPaid.v1 + loyalty references
    Bus-->>Loyalty: Deliver event at-least-once
    Loyalty->>DB: Inbox dedupe; EARNED; REDEEMED; consume voucher; projection
    Loyalty->>DB: Insert outbox/audit in same transaction
```

Nếu Payment/Inventory/Order thất bại, Saga gọi release. Release idempotent và không tạo `REDEEMED`.

### 6.2 `OrderPaid` retry/duplicate

```mermaid
sequenceDiagram
    autonumber
    participant Bus as EventBridge/SQS
    participant Consumer as Loyalty Consumer
    participant DB as Loyalty PostgreSQL
    participant Outbox

    Bus->>Consumer: OrderPaid.v1(eventId)
    Consumer->>DB: Begin; insert inbox(eventId)
    alt event duplicate
        DB-->>Consumer: Unique conflict / already processed
        Consumer-->>Bus: Ack no-op
    else first delivery
        Consumer->>DB: Validate tenant/customer/source uniqueness
        Consumer->>DB: Append EARNED + point lot + balance projection
        Consumer->>DB: Commit inbox + audit/outbox
        DB-->>Consumer: Commit
        Consumer-->>Bus: Ack
        Outbox-->>Bus: Publish Loyalty facts at-least-once
    end
```

### 6.3 Full refund and reversal

```mermaid
sequenceDiagram
    autonumber
    participant Order
    participant Bus as EventBridge/SQS
    participant Loyalty as Loyalty Consumer
    participant DB as Loyalty PostgreSQL

    Order-->>Bus: OrderRefunded.v1(refundId, FULL)
    Bus-->>Loyalty: Deliver event
    Loyalty->>DB: Dedupe refundId; load order ledger/allocations
    Loyalty->>DB: Append reversal offsets; restore committed redemption
    Loyalty->>DB: Update lots/balance/tier metric; audit/outbox
    DB-->>Loyalty: Commit
    Loyalty-->>Bus: Ack
```

Partial refund không đi qua flow này trong P0; P1 thêm amount/line allocation và proportional points contract.

### 6.4 Promotion quote và voucher reservation

1. Order gửi immutable basket snapshot, branch/time/channel, customer/tier context và voucher code.
2. Promotion engine load active immutable versions theo tenant/time/scope.
3. Engine evaluate candidate rules, group theo `conflictGroup`, chọn customer-beneficial result và stable tie-break.
4. Nếu có voucher, Voucher module lock usage limit và tạo reservation gắn checkout attempt.
5. Loyalty trả quote, explanation, rule versions, reservation expiry và rejected-rule reasons.
6. Order revalidate quote trước finalization; quote stale/retired hoặc input khác thì fail closed và yêu cầu quote mới.

## 7. Jobs và failure handling

| Worker | Trigger | Lease/transaction behavior | Kết quả |
|---|---|---|---|
| Point expiry | Hằng ngày theo tenant timezone | Lock lot còn điểm; tạo `EXPIRED` + allocation idempotent | Không expire phần đang held |
| Tier recalculation | Event liên quan + hằng ngày | Tính rolling window, lock customer projection | Tier history + `MembershipTierChanged.v1` nếu đổi |
| Hold/reservation cleanup | Theo `expires_at` | Claim bằng lease; kiểm tra Order state trước release | Release/expire một lần |
| Projection rebuild | Manual/operator hoặc mismatch alert | Checkpoint, batch, swap projection | Không sửa ledger |
| Outbox publisher | Continuous polling/queue | Claim message, retry/backoff | Publish at-least-once hoặc DLQ |
| Inbox/DLQ replay | Operator/controlled job | Replay qua cùng event ID/idempotency | Không bypass domain validation |

Failure policies:

- DB unavailable: không trả success cho mutation; client retry cùng idempotency key.
- Broker unavailable: local outbox/inbox giữ trạng thái; không mất ledger effect.
- Event schema invalid: không ack business effect; DLQ với sanitized reason.
- Customer không tồn tại khi `OrderPaid`: retry bounded, sau đó DLQ/alert; không silently drop points.
- Quote/promotion engine timeout hoặc rule version không đọc được: không áp dụng discount/voucher; Order phải retry/requote.
- Projection mismatch: tiếp tục bảo vệ source ledger, alert và chạy rebuild; không sửa projection thủ công.

## 8. Security architecture

- API Gateway validate Cognito JWT; Loyalty kiểm tra lại issuer, audience, expiry, role, tenant và branch scope.
- Internal API dùng workload identity/mTLS hoặc signed service context; public client không gọi internal paths.
- `tenant_id` từ trusted context được kiểm tra lại ở application và repository query. Body/query `tenantId`, `branchId`, `customerId` không phải bằng chứng sở hữu.
- Phone encrypted bằng KMS envelope encryption; HMAC/pepper key ở Secrets Manager/KMS; key rotation không làm mất khả năng lookup các bản ghi đã lưu.
- Không log phone plaintext, OTP, token, raw encryption material, full rule payload nếu có PII hoặc secret.
- Customer PII access, point reversal, config publish, voucher consume/release và authorization denial đều có audit append-only.
- Public lookup/OTP có rate limit, anti-enumeration và response masked.
- Rule JSONB chỉ được parse bằng allowlisted schema; không thực thi expression/code tùy ý từ database.

## 9. Observability architecture

Trace/correlation xuyên POS → Order/Saga → Loyalty → Payment/Broker. Structured logs chỉ dùng identifiers opaque và metadata đã mask.

Metrics chính:

- customer lookup/create success, conflict và cross-tenant denial;
- quote latency/error/stale quote/rejected rule;
- point hold success, contention, release/expiry và negative-balance invariant;
- accrual/reversal/redeem volume, duplicate rate, event lag và DLQ depth;
- ledger/projection mismatch và rebuild duration;
- voucher reservation conflict, consume/release/expiry, limit rejection;
- tier recalculation result và tier transition count;
- expiry job duration/failure, outbox backlog và inbox retry;
- portal OTP rate-limit/failure, PII access audit và authorization failures.

Alert khi có negative/over-allocation invariant, DLQ tăng, outbox backlog, event lag > 60 giây, stuck holds/reservations, projection mismatch hoặc cross-tenant denial spike.

## 10. Performance, scaling và deployment

- Core synchronous API target p95 ≤ 500 ms trong demo load profile; platform timeout tổng 3 giây.
- Point correctness luôn đọc primary. Read replica/cache chỉ có thể dùng cho portal/reporting sau khi có freshness contract; không dùng cho hold.
- Scale API stateless theo request; scale consumer/worker theo SQS queue age và tenant-safe leases.
- Không partition P0. Đánh giá range partition ledger/history theo `occurred_at` khi vượt khoảng 10M rows và có retention evidence.
- Local/demo dùng Docker Compose với PostgreSQL, local broker, fake SMS/OTP và deterministic worker fixtures.
- AWS/hybrid dùng ECS/Fargate, API Gateway, EventBridge, SQS/DLQ, EventBridge Scheduler, RDS PostgreSQL, KMS, Secrets Manager và CloudWatch/OpenTelemetry.
- Backup/restore phải bảo toàn ledger, inbox/outbox lineage; restore drill kiểm tra replay không tạo duplicate.

## 11. Architecture decisions

| ADR | Decision | Alternatives | Rationale |
|---|---|---|---|
| LOY-ADR-01 | PostgreSQL riêng cho Loyalty | Shared DB, NoSQL primary ledger | Transaction, constraints, FIFO allocation và tenant ownership rõ ràng |
| LOY-ADR-02 | Outbox + inbox với EventBridge/SQS | Direct publish, 2PC | Tránh dual-write, hỗ trợ at-least-once/replay/DLQ |
| LOY-ADR-03 | Hold tách khỏi append-only ledger | Ghi `HELD` như balance ledger trước commit | Phân biệt reservation với tiêu điểm và đơn giản hóa rebuild |
| LOY-ADR-04 | `OrderPaid.v1` là accrual/commit trigger | `PaymentPaid.v1`, polling Order DB | Order là canonical paid-order fact và không chia sẻ DB |
| LOY-ADR-05 | Order phát canonical `OrderRefunded.v1` | Loyalty nghe Payment refund trực tiếp | Refund business scope thuộc Order, tránh payment/order double reversal |
| LOY-ADR-06 | Point lots + allocation lineage | Chỉ giữ aggregate balance | Cần FIFO, expiry, restore redemption và audit/rebuild |
| LOY-ADR-07 | Immutable promotion versions + JSONB validated rules | Mutate rule đang active, arbitrary script | Deterministic quote, replayable snapshot, an toàn khi evaluate |
| LOY-ADR-08 | No cache in correctness path | Redis làm source of truth | Hold/concurrency/ledger cần transaction primary |
| LOY-ADR-09 | No partition P0 | Partition from day one | Tránh phức tạp unique/FK trước khi có volume; đánh giá bằng metrics |

## 12. Cross-module contract changes required

Trước phase implementation, API/Data Contracts phải materialize:

1. `OrderPaid.v1` additive fields `loyaltyEligibleAmountMinor`, `loyaltyPolicyVersion`, cùng semantics và validation.
2. Optional `pointHoldId`, `voucherReservationId` hoặc correlation reference trong checkout/paid snapshot để Loyalty commit đúng reservation.
3. `OrderRefunded.v1` do Order phát sau full refund finalized, gồm `refundId`, `refundType=FULL`, original order/paid reference, tenant/branch và idempotency lineage.
4. `OrderCheckoutFailed`/cancel/expiry command boundary cho release point hold và voucher reservation.
5. Consumer contract test cho duplicate/out-of-order/replay, cùng source uniqueness và tenant ownership.

## 13. Deferred artifacts

- OpenAPI/AsyncAPI/JSON Schema và migration DDL sẽ được tạo ở phase API & Data Contracts.
- Partial line refund/proportional points, manual adjustment, merge customer và portal mutation giữ ở P1/P2 theo Requirements.
- Production SLA, data retention cụ thể và k6 measured results sẽ được chốt trong hardening/implementation planning; target hiện tại là demo target.

---

Started at: 2026/10/13 00:00:00  
Finished at: 2026/10/13 00:00:00  
Total time: 0 minutes
