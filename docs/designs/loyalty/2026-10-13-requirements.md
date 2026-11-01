# Module 5 — Loyalty service: Requirements

**Ngày:** 2026-10-13  
**Trạng thái:** Draft — chờ Spec Review  
**Phạm vi:** M — Standard Service Flow  
**Module:** Khách hàng thân thiết & khuyến mãi (Loyalty service)

## 1. Mục tiêu và ranh giới

Loyalty service sở hữu hồ sơ khách hàng theo tenant, sổ cái điểm bất biến, số dư điểm khả dụng, hạng thành viên, voucher và rule engine khuyến mãi.

Order service sở hữu vòng đời đơn hàng, basket snapshot, tổng tiền cuối cùng và checkout attempt. Payment service sở hữu payment attempt và provider payment state. Loyalty không đọc trực tiếp database của Order, Payment hoặc Catalog và không tự thay đổi tổng tiền của Order.

### 1.1 Phạm vi P0

- Tìm/tạo hồ sơ khách theo số điện thoại trong tenant.
- Tích điểm từ `OrderPaid.v1` theo giá trị đủ điều kiện do Order cung cấp.
- Đổi điểm bằng flow giữ điểm → commit hoặc release.
- Thu hồi điểm cho hoàn toàn bộ đơn đã thanh toán.
- Outbox/event consumer idempotent, audit và tenant isolation.

### 1.2 Phạm vi P1

- Hạng `SILVER`, `GOLD`, `DIAMOND` theo rolling 12 tháng.
- FIFO point lots và hết hạn điểm.
- Voucher limits, reservation và consumption.
- Rule `% discount`, `buy X get Y`, `happy hour`.
- Conflict resolution deterministic khi nhiều rule cùng áp dụng.
- Split redemption/hoàn điểm trong các flow đã hỗ trợ ở Order.
- Lịch sử điểm, voucher và thay đổi hạng cho staff.

### 1.3 Phạm vi P2

- Customer portal read-only để xem điểm, lịch sử, hạng và voucher.
- Customer authentication bằng OTP tách khỏi Cognito nhân viên.

### 1.4 Không thuộc phạm vi

- Loyalty không chốt payment, không phát `OrderPaid.v1` và không finalize Order.
- Loyalty không tính lại Catalog price, VAT, phí, tip hoặc tổng tiền cuối cùng.
- P0 không hỗ trợ merge khách hàng, manual point adjustment, partial line refund hoặc cash-out điểm.
- P2 portal không cho đổi điểm, áp voucher tại checkout hoặc chỉnh hồ sơ.

## 2. Actors và authorization

| Actor | Quyền chính |
|---|---|
| `cashier` | Tìm/tạo khách trong tenant; xem điểm/hạng/voucher; tạo quote và point hold cho checkout thuộc branch scope |
| `manager` | Toàn bộ quyền cashier trong branch scope; xem lịch sử cần thiết và thực hiện các thao tác được policy cho phép |
| `owner` | Toàn tenant; quản lý cấu hình tier, earn/redeem, expiry, promotion và voucher |
| `order-service` / `checkout-saga` | Gọi quote, tạo/commit/release point hold; gửi order events; dùng service identity |
| `payment-service` | Không trực tiếp thay đổi điểm; refund event đi qua Order contract |
| `customer-portal` | Đọc dữ liệu của chính customer sau OTP; không có quyền staff hoặc tenant-wide |
| `loyalty-worker` | Consume event, expire lots, recalculate tier, reconcile reservation; không hoạt động như user role |

Mọi read/write phải kiểm tra `tenantId` tại service layer và repository/query layer. ID trên URL không phải bằng chứng sở hữu. Branch scope áp dụng cho thao tác staff/checkout; hồ sơ khách và số dư thuộc tenant và có thể được dùng tại các chi nhánh của cùng tenant.

## 3. Use cases

| ID | Priority | Actor | Use case | Kết quả chính |
|---|---|---|---|---|
| LOY-UC-01 | P0 | Cashier/Manager | Tìm khách theo phone | Trả customer masked, số dư khả dụng, hạng và voucher đủ điều kiện trong tenant |
| LOY-UC-02 | P0 | Cashier/Manager | Tạo hồ sơ khách | Chuẩn hóa phone, lưu encrypted phone + lookup hash, không tạo trùng |
| LOY-UC-03 | P0 | Order event consumer | Tích điểm sau OrderPaid | Tạo ledger `EARNED` một lần theo unique source key |
| LOY-UC-04 | P0 | Checkout Saga | Đổi điểm | Giữ điểm atomic, commit khi `OrderPaid`, release khi fail/timeout/cancel |
| LOY-UC-05 | P0 | Order event consumer | Thu hồi điểm hoàn đơn | Tạo `REVERSED`/restore offset theo `refundId`, idempotent |
| LOY-UC-06 | P1 | Scheduler/Worker | Expire point lots | Tạo `EXPIRED` cho số điểm còn lại không bị hold |
| LOY-UC-07 | P1 | Worker | Recalculate tier | Cập nhật hạng và phát `MembershipTierChanged.v1` nếu thay đổi |
| LOY-UC-08 | P1 | Order/Saga | Promotion quote | Đánh giá rule/voucher và trả discount breakdown deterministic |
| LOY-UC-09 | P1 | Order/Saga | Reserve/consume voucher | Reserve theo checkout attempt, consume sau OrderPaid, release khi fail |
| LOY-UC-10 | P2 | Customer | Xem loyalty portal | OTP-authenticated read-only access cho dữ liệu của chính customer |

## 4. Business requirements và invariants

### 4.1 Customer profile

1. Phone được normalize về E.164 theo country/tenant policy trước khi lookup hoặc create.
2. Hệ thống lưu phone encrypted để hiển thị/đối soát và lưu HMAC/peppered hash để lookup; không dùng plaintext phone làm unique key hoặc log.
3. Unique constraint là `(tenantId, phoneHash)`.
4. Response staff chỉ hiển thị phone masked. Full phone chỉ được dùng nội bộ theo chính sách PII và không trả trong API thông thường.
5. Tìm kiếm không được tiết lộ customer có tồn tại ở tenant khác; tenant context luôn lấy từ authenticated context.
6. Tạo lại cùng normalized phone trong tenant trả hồ sơ hiện có hoặc conflict idempotent, không tạo customer thứ hai.
7. P0 không tự động merge hai customer. Nếu phát hiện dữ liệu trùng do migration/import, phải giữ audit và xử lý bằng quy trình quản trị riêng.
8. Customer profile không chứa Order/Payment snapshot; chỉ lưu reference và summary cần cho Loyalty.

### 4.2 Accrual từ OrderPaid

1. `OrderPaid.v1` là nguồn sự thật duy nhất cho tích điểm; `PaymentPaid.v1` không trực tiếp tạo điểm.
2. Event phải mang `loyaltyEligibleAmountMinor` và `loyaltyPolicyVersion` do Order tính từ immutable order snapshot.
3. `loyaltyEligibleAmountMinor` là giá trị hàng hóa sau discount, trước VAT; không gồm phí, tip và phần giá trị được thanh toán bằng voucher.
4. Earn ratio là cấu hình theo tenant và policy version. Loyalty tính điểm bằng integer arithmetic và làm tròn xuống.
5. Một event chỉ tạo tối đa một accrual theo unique key `(tenantId, sourceType=ORDER_PAID, orderId)`.
6. Event retry, duplicate delivery hoặc consumer restart không được tạo duplicate `EARNED` hoặc tăng số dư hai lần.
7. Nếu customer chưa tồn tại hoặc event thiếu customer reference hợp lệ, consumer không silently bỏ qua: ghi failed event/retry hoặc dead-letter theo policy và tạo operational alert.
8. Ledger `EARNED` phải lưu `orderId`, `sourceEventId`, `policyVersion`, eligible amount, points và timestamp nguồn.

### 4.3 Ledger và point balance

1. Ledger là append-only; không update hoặc delete entry lịch sử.
2. P0/P1 entry types gồm `EARNED`, `REDEEMED`, `REVERSED`, `EXPIRED`. Mỗi entry có `entryId`, tenant/customer, points, source type/id, occurredAt, idempotency key và liên kết entry gốc khi cần.
3. Giữ điểm được quản lý ở `point_holds`, không phải là giao dịch đã tiêu trong ledger.
4. Hold có trạng thái `HELD`, `COMMITTED`, `RELEASED`, `EXPIRED`, gắn duy nhất với `checkoutAttemptId` và customer.
5. `HELD` làm giảm available balance ngay lập tức nhưng không làm giảm lifetime earned hoặc ghi `REDEEMED`.
6. Commit hold tạo `REDEEMED` một lần và chuyển hold sang `COMMITTED`; release chuyển hold sang `RELEASED` và trả available balance.
7. Công thức projection phải phân biệt `earned/unexpired`, `redeemed`, `reversed`, `expired` và active holds. Projection có thể rebuild từ ledger + hold history.
8. Mỗi source operation có idempotency key và unique constraint ở database; application-level dedupe không đủ.
9. Không cho available balance âm. Concurrent hold phải dùng transaction/locking hoặc optimistic concurrency có retry rõ ràng.

### 4.4 Đổi điểm

1. Điểm chỉ dùng làm discount trên đơn, không cash-out.
2. Redemption rate cấu hình theo tenant; ví dụ `100 points = 10.000 VND`, lưu kèm policy version.
3. Order/Saga gửi customer, checkout attempt, requested points và amount context; Loyalty trả redemption quote/hold result.
4. Làm tròn xuống theo điểm nguyên. Tối đa amount được giảm theo policy; mặc định không vượt giá trị hàng hóa sau promotion và không áp dụng cho delivery fee, tip hoặc phụ phí.
5. Flow bắt buộc là `quote/validate → hold → payment/order success → commit`, hoặc `release` khi fail/timeout/cancel.
6. Hold phải có `expiresAt` lấy từ checkout attempt của Order, không dùng TTL độc lập gây lệch lifecycle.
7. Retry cùng checkout attempt không tạo hold thứ hai; request khác với cùng idempotency key trả conflict.
8. Nếu point lot có expiry trong lúc hold, điểm được bảo vệ cho tới khi hold kết thúc. Khi release, lot đã quá hạn phải expire ngay; lot chưa quá hạn được trả available.

### 4.5 Thu hồi điểm

1. `OrderCancelled` trước thanh toán không thu hồi điểm vì chưa có `EARNED`.
2. `OrderRefunded.v1` với `refundType=FULL` là trigger P0 cho hoàn toàn bộ đơn đã paid.
3. Event phải có `refundId`, `orderId`, tenant/branch context, original paid reference và correlation/causation IDs.
4. Loyalty tìm các entry/hold liên quan đến order và tạo entry offset mới, không sửa entry gốc.
5. Accrual đã cộng bị thu hồi bằng `REVERSED` liên kết với `EARNED` gốc. Redemption đã commit trong đơn được khôi phục bằng offsetting entry/lot mới; hold chưa commit được release.
6. Cùng `refundId` chỉ được áp dụng một lần. Retry hoặc duplicate event không làm thu hồi nhiều lần.
7. Partial refund/partial line reversal là P1; contract phải dành chỗ cho `refundType=PARTIAL`, line/amount allocation và proportional points nhưng chưa được xử lý như full refund.

### 4.6 Membership tier

1. Tier gồm `SILVER`, `GOLD`, `DIAMOND`; ngưỡng và metric cấu hình theo tenant.
2. Tenant chọn một metric: `ELIGIBLE_SPEND_MINOR` hoặc `EARNED_POINTS`.
3. Cửa sổ xét là rolling 12 tháng. Order bị refund/reversal/cancel không được tính trong metric.
4. Recalculation chạy sau event liên quan và job định kỳ hằng ngày để sửa sai lệch hoặc missed event.
5. Tier có thể tăng hoặc hạ; tier mới có hiệu lực khi recalculation thành công. Không có cấu hình/dữ liệu đủ điều kiện thì `SILVER`.
6. Lưu materialized current tier và append-only tier history; thay đổi tier phát `MembershipTierChanged.v1` một lần theo transition.
7. Rule ưu đãi dựa trên tier phải đọc current tier từ Loyalty, không tự suy ra từ lịch sử Order.

### 4.7 FIFO và expiry

1. Mỗi accrual tạo một point lot với `earnedAt`, `remainingPoints`, `expiresAt` và nguồn ledger.
2. Mặc định lot hết hạn sau 12 tháng kể từ `earnedAt`; tenant có thể cấu hình policy mới cho lot mới.
3. Redemption phân bổ theo `expiresAt` sớm nhất; tie-break bằng `earnedAt`, rồi `lotId`.
4. Held points không expire khi hold đang active.
5. Job expire chạy idempotent mỗi ngày theo timezone tenant. Mỗi lot tạo `EXPIRED` cho số điểm còn lại thực tế.
6. Job không được expire điểm đã committed/reversed hoặc phần đang bị hold.
7. Expiry và redemption cạnh tranh phải được serialize theo lot/customer để không vượt số dư.

### 4.8 Promotion và voucher

1. Order gửi basket snapshot, branch/time/channel context, customer/tier context, requested voucher và checkout attempt; Loyalty trả `PromotionQuote`.
2. Quote gồm discount total, line breakdown, promotion IDs, rule versions, applied voucher, expiration và explanation/rejected rules.
3. Order là authority cho final total; Order revalidate quote tại checkout và snapshot quote/rule versions vào đơn.
4. Rule P1 hỗ trợ `% discount` theo line/order với min spend/max discount, `buy X get Y` theo item/variant/quantity và happy hour theo branch timezone/weekday/date/local window.
5. Rule/promotion version là immutable sau khi publish. Thay đổi tạo version mới để quote và lịch sử replay được.
6. Trong cùng `conflictGroup`, chỉ chọn một rule: phương án giảm nhiều nhất cho customer; nếu bằng nhau chọn priority cao hơn; nếu vẫn bằng nhau chọn `promotionId` theo thứ tự ổn định.
7. Các group khác chỉ stack khi các rule liên quan có `stackable=true`. Tổng giảm không được làm giá trị phải trả âm.
8. Với buy X get Y, phần hàng tặng có giá trị 0 và không phát sinh điểm.
9. Voucher mặc định cùng conflict group; tenant có thể cấu hình voucher stack với happy hour nếu policy cho phép.
10. Voucher code được normalize uppercase và unique trong tenant. Limits gồm total, per customer, thời gian, branch, channel và minimum spend.
11. Voucher reservation gắn với `checkoutAttemptId`, có `expiresAt` từ Order; reserve không tăng usage counter.
12. Chỉ `OrderPaid` mới consume voucher và tăng usage. Fail/timeout/cancel release reservation; retry cùng checkout attempt không tăng usage.
13. Reservation/consume/release phải chống race condition ở giới hạn total/per-customer bằng database constraint hoặc atomic counter transaction.

### 4.9 Customer portal P2

1. Customer đăng nhập bằng OTP tới phone đã xác minh; identity/session tách khỏi Cognito nhân viên.
2. Portal chỉ được đọc current balance, held/expired summary, ledger history, tier và vouchers của chính customer.
3. Portal không đổi điểm, tạo hold, áp voucher, sửa hồ sơ hoặc xem customer khác.
4. OTP endpoint có rate limit, max attempts, cooldown và audit. Response không tiết lộ phone có customer hay không.
5. Session/token phải bind đúng tenant/customer và không được dùng để gọi staff API.

## 5. Event và flow requirements

### 5.1 OrderPaid → accrue

1. Order phát `OrderPaid.v1` sau khi payment thành công và Order finalize theo platform contract.
2. Loyalty consumer validate schema, tenant/customer context, `loyaltyEligibleAmountMinor`, policy version và source uniqueness.
3. Trong một transaction, tạo ledger `EARNED`, point lots, update/rebuild balance projection và outbox event nếu cần.
4. Ack event chỉ sau commit. Lỗi tạm thời retry; lỗi dữ liệu vào DLQ kèm reason và operational visibility.

### 5.2 Checkout redemption

1. POS/Order tạo quote với customer và requested redemption.
2. Loyalty kiểm tra available balance, expiry allocation, rate, max amount và customer/tenant scope.
3. Loyalty tạo hold atomic theo `checkoutAttemptId` và trả hold ID, points, discount amount, expiry.
4. `OrderPaid` commit hold và voucher reservation; event handler phải idempotent.
5. Payment/order failure, cancellation hoặc checkout expiry release hold/reservation; cleanup job là fallback, không thay thế command path.

### 5.3 Full refund → reversal

1. Order phát `OrderRefunded.v1` với full refund contract.
2. Loyalty lookup theo `orderId`, xác nhận tenant và refund uniqueness.
3. Tạo offset entries/lots, khôi phục redemption đã commit và cập nhật balance/tier metric trong cùng consistency boundary.
4. Ack sau commit; duplicate refund không tạo offset thứ hai.

## 6. API requirements cấp nghiệp vụ

API contract chi tiết sẽ được chốt ở phase API/Data Contracts. Các capability bắt buộc:

| Capability | Caller | Kết quả |
|---|---|---|
| Customer lookup/create | Staff POS | Customer masked, current balance/tier/voucher summary |
| Promotion quote | Order/Saga | Deterministic discount quote và explanation |
| Point hold | Order/Saga | Hold ID, points, discount amount, expiry |
| Point commit/release | Order/Saga/event consumer | Transition idempotent theo checkout attempt/event |
| Voucher reserve/consume/release | Order/Saga/event consumer | Usage lifecycle idempotent |
| Read ledger/history | Staff/portal | Pagination, tenant/customer ownership và masked PII |
| Tier/promotion/voucher config | Owner/Manager theo policy | Versioned immutable publish, audit |

Mọi mutation yêu cầu idempotency key, correlation ID, authenticated actor/service identity và kiểm tra tenant. Lỗi phải có stable error code; không trả stack trace, secret hoặc dữ liệu tenant khác.

## 7. Acceptance criteria

1. Cùng một `OrderPaid.v1` được deliver ít nhất hai lần chỉ tạo một `EARNED` và một balance effect.
2. Hai checkout đồng thời không thể hold vượt available balance; retry không tạo hold hoặc voucher usage duplicate.
3. Payment/order success commit đúng hold; failure/timeout release đúng hold; hold state chuyển terminal một lần.
4. Full refund sau paid tạo reversal đúng một lần và không sửa/xóa ledger gốc.
5. FIFO redemption dùng lot hết hạn sớm hơn; expiry job không làm âm balance hoặc expire phần đang held.
6. Tier recalculation loại order bị reversed/refunded và phát tier event đúng một lần khi tier đổi.
7. Conflict engine cho cùng input trả cùng quote; tie-break không phụ thuộc thứ tự query/database.
8. Voucher vượt giới hạn total/per-customer hoặc hết hạn bị reject; concurrent reserve không vượt limit.
9. Staff/portal không đọc được customer hoặc transaction ngoài tenant/ownership scope.
10. Customer phone không xuất hiện plaintext trong log ứng dụng và lookup không yêu cầu scan plaintext column.
11. Mọi event consumer có retry/DLQ/observability và không ack trước transaction commit.
12. Order có thể replay quote/ledger reference bằng policy/rule version đã snapshot mà không phụ thuộc rule hiện tại.

## 8. Edge cases và lỗi bắt buộc xử lý

- `OrderPaid` đến trước customer creation hoàn tất: retry/DLQ có correlation, không silently drop.
- Duplicate event, duplicate callback/command và cùng idempotency key với payload khác.
- Refund đến sau expiry, sau redemption hoặc sau tier downgrade; offset phải không làm số dư âm và phải có lineage.
- Hai hold đồng thời trên cùng customer; một hold expire trong lúc payment callback đến.
- Voucher reservation treo do worker crash; cleanup không consume voucher và không xóa lịch sử.
- Promotion quote hết hạn hoặc rule version đã retired khi checkout revalidate.
- Rule stack làm giảm vượt payable amount, buy X get Y thiếu quantity, happy-hour crossing midnight/branch timezone.
- Phone normalize khác nhau giữa country policy, customer bị khóa hoặc request thiếu tenant context.
- Projection hỏng hoặc lệch ledger; có command rebuild read model mà không sửa ledger.
- Event broker at-least-once, out-of-order hoặc DLQ replay.

## 9. Observability và vận hành

- Metrics: accrual success/failure, event lag, duplicate rate, balance mismatch, hold active/expired, redemption failure, voucher reservation conflict, promotion quote latency, tier recalculation result, expiry volume và portal OTP failures.
- Structured logs bắt buộc `tenantId`, `customerId` opaque, `orderId`, `checkoutAttemptId`, `eventId`, `correlationId`; không log phone plaintext hoặc OTP.
- Audit cho customer PII access, point reversal, configuration publish, voucher consume/release, tier change và authorization failure.
- Alert cho DLQ growth, ledger/projection mismatch, negative-balance invariant, stuck holds/reservations và provider/event contract mismatch.
- Job expiry, tier recalculation và reservation cleanup phải có run ID, lease, retry và dead-letter/manual review path.

## 10. Quyết định đã chốt

- `OrderPaid.v1` là nguồn tích điểm; event mang `loyaltyEligibleAmountMinor` và `loyaltyPolicyVersion`.
- Hold tách khỏi append-only ledger; chỉ commit mới tạo `REDEEMED`.
- P0 reversal dùng `OrderRefunded.v1` full refund; partial reversal để P1.
- Customer phone tenant-scoped, normalize E.164, encrypted + lookup hash, masked response, không auto merge P0.
- Tier rolling 12 tháng, metric cấu hình `ELIGIBLE_SPEND_MINOR` hoặc `EARNED_POINTS`, có thể tăng/hạ.
- FIFO theo expiry; mặc định expiry 12 tháng; held points được bảo vệ tới khi hold kết thúc.
- Promotion quote do Loyalty tính; Order authoritative cho final total và snapshot quote.
- Conflict resolution deterministic: customer benefit → priority → stable promotion ID.
- Voucher reservation theo checkout attempt; chỉ OrderPaid mới consume.
- Portal P2 dùng OTP riêng, read-only và anti-enumeration.

## 11. Tài liệu liên quan

- [Module 5 Vision](./2026-10-13-vision.md)
- [Module 3 Order Requirements](../order/2026-10-12-requirements.md)
- [Module 3 Order Contracts](../order/2026-10-12-contracts.md)
- [Module 4 Payment Requirements](../payment/2026-10-12-requirements.md)

---

**Thời gian:**

Started at: 2026/10/13 00:00:00  
Finished at: 2026/10/13 00:00:00  
Total time: 0 minutes
