# Module 5 — Dịch Vụ Khách Hàng Thân Thiết & Khuyến Mãi (Loyalty Service): Kiến Trúc Hệ Thống (Architecture)

**Ngày:** 2026-10-13  
**Trạng thái:** Bản thảo — Chờ phê duyệt Kiến trúc (Draft — Pending Architecture Approval)  
**Phạm vi:** M — Luồng Service Tiêu chuẩn (Standard Service Flow)  
**Module:** Khách hàng thân thiết & khuyến mãi (Loyalty Service)

---

## 1. Ranh Giới & Quyền Sở Hữu (Boundary & Ownership)

### Loyalty Service sở hữu:
- Hồ sơ khách hàng và cơ chế tra cứu số điện thoại an toàn trong phạm vi tenant.
- Sổ cái điểm thưởng bất biến chỉ ghi thêm (append-only points ledger), các lô điểm (point lots), bảng phân bổ tiêu hao (redemption allocation) và view tổng hợp số dư (balance projection).
- Vòng đời giữ điểm thưởng (`point_holds`) theo từng lượt checkout.
- Hạng thành viên hiện tại (`current_tier`) và sổ nhật ký lịch sử thay đổi hạng.
- Các chính sách tích điểm, đổi điểm và hết hạn điểm theo từng tenant.
- Các phiên bản quy tắc khuyến mãi bất biến, bộ máy đánh giá quy tắc (rule engine), tạm giữ và lịch sử sử dụng voucher.
- Hàng đợi Inbox/Outbox, cơ chế chống trùng lặp (idempotency), nhật ký kiểm toán và lịch trình các worker nền của Loyalty.

### Loyalty Service không sở hữu và không làm:
- Tuyệt đối không đọc trực tiếp cơ sở dữ liệu của Order, Payment, Catalog hoặc Inventory Service.
- Không tự ý tính toán lại giá bán trong Catalog, thuế VAT, phí vận chuyển, tiền tip hoặc tổng giá trị hóa đơn.
- Không phát sự kiện `OrderPaid.v1`; chỉ Order Service mới là bên phát hành sự kiện đơn hàng đã thanh toán chuẩn tắc.
- Không tự ý hoàn tất đơn hàng, thanh toán hay trừ kho.
- Không chỉnh sửa (`UPDATE`) hay xóa (`DELETE`) các dòng sổ cái; bảng số dư là projection có thể tái tạo từ đầu.

*Quy tắc tích hợp:* Order Service và Step Functions Saga gọi đồng bộ sang Loyalty Service để lấy báo giá chiết khấu, giữ điểm/voucher và commit/release. Order Service phát sự kiện `OrderPaid.v1` sau khi đơn hàng được chốt thanh toán. Order Service là bên phát hành sự kiện nghiệp vụ `OrderRefunded.v1` sau khi giao dịch hoàn tiền toàn bộ được xác nhận; Payment Service chỉ cung cấp bằng chứng thanh toán/hoàn tiền cho Saga và Order Service.

---

## 2. Tô-pô Hệ Thống (System Topology)

```text
Máy POS / Ứng dụng Angular
    → API Gateway + Xác thực Cognito
    → Order API / AWS Step Functions Saga
          ├─ REST nội bộ: Báo giá / Giữ điểm / Commit / Release (Loyalty API)
          └─ AWS EventBridge → Hàng đợi SQS của Loyalty
                               ├─ Sự kiện OrderPaid.v1
                               ├─ Sự kiện OrderRefunded.v1
                               └─ Lỗi checkout / Hủy đơn / Hết hạn

Ứng dụng Khách hàng / Nhân viên (Customer / Staff API)
    → Loyalty API Adapter
    → Tầng Application & Domain của Loyalty
    → Cơ sở dữ liệu PostgreSQL của Loyalty Service
          ├─ Khách hàng / Bảng tổng hợp số dư (customers, loyalty_balances)
          ├─ Sổ cái điểm / Lô điểm / Phân bổ / Đơn giữ điểm (ledger, lots, holds)
          ├─ Chương trình khuyến mãi / Voucher / Giữ voucher / Sử dụng
          ├─ Bảng Inbox / Outbox / Nhật ký kiểm toán (audit_events)
          └─ Metadata vận hành của Background Workers

Loyalty Outbox → EventBridge → Reporting / Audit / Các downstream consumer khác

CloudWatch Scheduler → Hàng đợi Worker của Loyalty
    → Quét hết hạn điểm / Tính lại hạng / Dọn dẹp giữ điểm treo / Tái tạo số dư / Replay DLQ

Loyalty Service → Mã hóa phong bì AWS KMS + Khóa HMAC pepper trong Secrets Manager
Loyalty Service → Thu thập Metrics, Logs và Traces qua CloudWatch / OpenTelemetry
```

### 2.1 Trách Nhiệm Các Thành Phần Hệ Thống

| Thành phần | Trách nhiệm chi tiết |
|---|---|
| **Public API Adapter** | Xử lý tra cứu/tạo khách hàng, các API đọc dữ liệu cho nhân viên thu ngân và cổng portal; xác thực request và kiểm tra thẩm quyền RBAC/tenant. |
| **Internal API Adapter** | Cung cấp các endpoint tính báo giá khuyến mãi, giữ/commit/release điểm và voucher cho Step Functions Saga; xác thực bằng Workload Identity. |
| **Customer Module** | Chuẩn hóa số điện thoại E.164, mã hóa dữ liệu PII, sinh chuỗi băm HMAC tra cứu, quản lý trạng thái hồ sơ và thực thi chính sách bảo mật. |
| **Points Application Service** | Điều phối các luồng nghiệp vụ: tích điểm (`Accrual`), đổi điểm (`Redemption`), thu hồi (`Reversal`), cập nhật projection số dư và kiểm soát idempotency. |
| **Points Domain** | Bảo toàn các bất biến sổ cái, thực thi thuật toán phân bổ FIFO, quản lý state machine giữ điểm và duy trì vết liên kết bồi hoàn (reversal lineage). |
| **Promotion Engine** | Nạp các phiên bản quy tắc khuyến mãi active bất biến, thẩm định điều kiện/hành động, giải quyết xung đột tất định và giải thích chi tiết cơ cấu chiết khấu. |
| **Voucher Module** | Tra cứu mã voucher, kiểm tra hạn mức toàn hệ thống/khách hàng, thực hiện quy trình giữ/tiêu thụ/giải phóng và chống tranh chấp đồng thời. |
| **Event Consumer / Inbox** | Kiểm tra cấu trúc sự kiện, khử trùng lặp qua bảng Inbox, thực hiện retry có giãn cách, chuyển lỗi vào DLQ và điều hướng lệnh domain. |
| **Outbox Publisher** | Quét và phát hành các sự kiện của Loyalty lên EventBridge theo mô hình at-least-once sau khi local database transaction commit thành công. |
| **Scheduled Workers** | Thực thi các tác vụ định kỳ: quét hết hạn điểm FIFO, tính toán lại hạng thành viên, dọn dẹp các đơn giữ điểm treo, tái tạo bảng số dư và replay DLQ. |
| **PostgreSQL Repositories** | Thực thi các điều kiện lọc `tenant_id` bắt buộc, bảo đảm ràng buộc toàn vẹn dữ liệu, thực hiện khóa dòng bi quan hoặc kiểm soát phiên bản lạc quan. |
| **Audit / Observability Adapter** | Ghi nhận nhật ký truy cập PII, xuất bản cấu hình, thu hồi điểm, lỗi phân quyền; đo lường metrics và vết phân tán. |

---

## 3. Giao Tiếp & Mức Độ Nhất Quán (Communication & Consistency)

### 3.1 Giao Tiếp Đồng Bộ (Synchronous Calls)
- POS / Ứng dụng client chỉ được gọi vào Public Loyalty API sau khi đã được phân quyền qua Cognito.
- Order Service / Step Functions Saga gọi vào Internal Loyalty API để lấy báo giá chiết khấu và quản lý vòng đời giữ điểm/voucher.
- Các thao tác báo giá và giữ điểm bắt buộc đọc từ Primary Database; tuyệt đối không đọc từ cache hoặc Read Replica có nguy cơ bị trễ dữ liệu (stale data).
- Request nội bộ bắt buộc mang: `tenantId`, `branchId`, `orderId`, `checkoutAttemptId`, cùng correlation ID và idempotency key; Loyalty Service đối chiếu với thông tin workload identity tin cậy.
- Các cuộc gọi đồng bộ áp dụng timeout nghiêm ngặt, retry có giới hạn cho các lỗi tạm thời, và kích hoạt cơ chế fail-closed khi không thể xác định được phạm vi dữ liệu, chính sách hoặc số dư điểm.

### 3.2 Giao Tiếp Bất Đồng Bộ (Asynchronous Events)
- Các sự kiện từ Order Service truyền qua EventBridge → hàng đợi SQS; phân phối theo mô hình at-least-once.
- Consumer ghi nhận bản ghi vào bảng `inbox_events` và thực thi biến động domain trong cùng một database transaction cục bộ.
- Chỉ gửi xác nhận (acknowledge) message về hàng đợi sau khi database transaction commit thành công. Sự kiện gửi trùng lặp sẽ được xử lý dạng no-op nhờ khóa duy nhất `eventId`/source key.
- Các sự kiện lỗi độc hại (poison messages) được retry có giới hạn trước khi chuyển vào hàng đợi chết DLQ; quy trình replay từ DLQ đi qua đúng luồng inbox/idempotency chuẩn.
- Bảng Transactional Outbox được ghi cùng transaction với sổ cái/đơn giữ điểm/projection và được xuất bản ra ngoài sau khi commit.

### 3.3 Ranh Giới Giao Dịch Cơ Sở Dữ Liệu (Transaction Boundary)
Trong một database transaction duy nhất của Loyalty Service:
1. Tiếp nhận và kiểm tra tính duy nhất của idempotency key hoặc sự kiện inbox.
2. Xác thực tính hợp lệ của tenant, khách hàng, quyền sở hữu đơn hàng/checkout attempt và trạng thái hiện tại.
3. Khóa dòng dữ liệu khách hàng/đơn giữ điểm/lô điểm (`SELECT ... FOR UPDATE`) hoặc kiểm tra phiên bản lạc quan.
4. Ghi dòng sổ cái, phân bổ lô điểm, chuyển đổi trạng thái giữ điểm/voucher và cập nhật projection số dư/hạng thành viên.
5. Ghi nhật ký kiểm toán, ghi bản ghi outbox và đánh dấu sự kiện inbox/idempotency đã hoàn tất.
6. Commit transaction một cách nguyên tử.

*Nguyên tắc sống còn:* Tuyệt đối không giữ mở database transaction trong lúc đang thực hiện các cuộc gọi mạng sang Order, Payment, Message Broker, dịch vụ KMS hay các API đối tác bên ngoài. Không sử dụng distributed transaction (2PC); toàn bộ quy trình phối hợp liên service do Step Functions Saga điều phối.

---

## 4. Kiến Trúc Mô Hình Dữ Liệu (Data Architecture)

### 4.1 Mô Hình Dữ Liệu Ghi Logic (Write Model)
Sử dụng database PostgreSQL độc lập, khóa chính UUID v7, kiểu dữ liệu `BIGINT` cho điểm thưởng và tiền tệ (minor units VND), dấu thời gian `TIMESTAMPTZ` chuẩn UTC. Mọi bảng dữ liệu nghiệp vụ đều có cột `tenant_id`; mọi truy vấn đều bắt buộc có điều kiện tenant.

#### Bảng `customers` (Hồ sơ khách hàng)
- `id` (UUID, PK): Khóa định danh UUID v7 của khách hàng.
- `tenant_id` (UUID, NOT NULL): Định danh tenant sở hữu hồ sơ.
- `phone_ciphertext` (TEXT, NOT NULL): Số điện thoại đã được mã hóa phục vụ hiển thị đối soát nội bộ có kiểm soát.
- `phone_lookup_hash` (TEXT, NOT NULL): Mã băm HMAC có muối (peppered hash) dùng để tra cứu và đảm bảo ràng buộc duy nhất.
- `display_name` (TEXT, NULL): Tên hiển thị tối thiểu của khách hàng.
- `status` (TEXT, CHECK `IN ('ACTIVE', 'BLOCKED')`): Trạng thái hoạt động của hồ sơ.
- `current_tier` (TEXT, NOT NULL): Hạng thành viên hiện tại (projection đọc nhanh: `SILVER`, `GOLD`, `DIAMOND`).
- `created_at`, `updated_at` (TIMESTAMPTZ, NOT NULL): Dấu thời gian tạo và cập nhật UTC.
- `version` (BIGINT, NOT NULL): Số phiên bản phục vụ kiểm soát khóa lạc quan.

#### Bảng `point_ledger_entries` (Sổ cái biến động điểm bất biến)
- `id` (UUID, PK): Khóa định danh dòng sổ cái UUID v7.
- `tenant_id` (UUID, NOT NULL): Định danh tenant sở hữu.
- `customer_id` (UUID, FK `customers(id)`): Khách hàng phát sinh giao dịch điểm.
- `entry_type` (TEXT, CHECK `IN ('EARNED', 'REDEEMED', 'REVERSED', 'EXPIRED')`): Loại biến động điểm.
- `points_delta` (BIGINT, NOT NULL): Số điểm biến động có dấu (+/-); ghi nhận đối ứng, không sửa dòng cũ.
- `source_type` (TEXT, NOT NULL): Loại nguồn phát sinh (`ORDER_PAID`, `ORDER_REFUND`, `POINT_HOLD`, `EXPIRY`).
- `source_id` (UUID, NOT NULL): Mã định danh nguồn nghiệp vụ phục vụ replay, khử trùng lặp và kiểm toán.
- `related_entry_id` (UUID, NULL): Tham chiếu dòng sổ cái gốc khi thực hiện bồi hoàn thu hồi điểm (`REVERSED`).
- `policy_version` (TEXT, NOT NULL): Phiên bản chính sách tích/đổi điểm đã áp dụng.
- `idempotency_key` (TEXT, NOT NULL): Khóa chống trùng lặp tác dụng phụ.
- `occurred_at` (TIMESTAMPTZ, NOT NULL): Thời điểm phát sinh nghiệp vụ thực tế UTC.
- `metadata_json` (JSONB, NOT NULL): Metadata bổ trợ an toàn, không chứa dữ liệu PII nhạy cảm.
- `created_at` (TIMESTAMPTZ, NOT NULL): Thời điểm ghi vào database UTC.

#### Bảng `point_lots` (Các lô điểm thưởng FIFO)
- `id` (UUID, PK): Khóa định danh lô điểm UUID v7.
- `tenant_id` (UUID, NOT NULL): Định danh tenant.
- `customer_id` (UUID, FK `customers(id)`): Khách hàng sở hữu lô điểm.
- `source_entry_id` (UUID, FK `point_ledger_entries(id)`): Tham chiếu dòng `EARNED` đã sinh ra lô điểm này.
- `earned_at` (TIMESTAMPTZ, NOT NULL): Thời điểm lô điểm được tích lũy.
- `expires_at` (TIMESTAMPTZ, NOT NULL): Thời điểm lô điểm hết hạn hiệu lực theo chính sách.
- `original_points` (BIGINT, NOT NULL): Số điểm tích lũy ban đầu của lô.
- `remaining_points` (BIGINT, CHECK `>= 0`): Số điểm còn lại thực tế chưa bị tiêu thụ hoặc hết hạn.
- `status` (TEXT, CHECK `IN ('OPEN', 'DEPLETED', 'EXPIRED')`): Trạng thái của lô điểm.
- `created_at`, `updated_at` (TIMESTAMPTZ, NOT NULL): Dấu thời gian cập nhật.
- `version` (BIGINT, NOT NULL): Số phiên bản kiểm soát khóa lạc quan.

#### Bảng `point_lot_allocations` (Phân bổ tiêu hao lô điểm)
- `id` (UUID, PK): Khóa định danh lượt phân bổ UUID v7.
- `tenant_id` (UUID, NOT NULL): Định danh tenant.
- `lot_id` (UUID, FK `point_lots(id)`): Lô điểm bị phân bổ tiêu hao.
- `customer_id` (UUID, NOT NULL): Định danh khách hàng (phòng thủ chuyên sâu).
- `allocation_type` (TEXT, CHECK `IN ('HOLD', 'REDEEM', 'RELEASE', 'REVERSE', 'EXPIRE')`): Loại phân bổ.
- `source_type` (TEXT, NOT NULL): Loại lệnh hoặc sự kiện tạo ra phân bổ.
- `source_id` (UUID, NOT NULL): Mã tham chiếu nguồn (hold ID, ledger entry ID, refund ID hoặc worker run ID).
- `points` (BIGINT, NOT NULL): Số điểm phân bổ từ lô điểm tương ứng.
- `created_at` (TIMESTAMPTZ, NOT NULL): Thời điểm ghi nhận phân bổ.

#### Bảng `point_holds` (Yêu cầu giữ điểm đổi thưởng cho checkout)
- `id` (UUID, PK): Khóa định danh lượt giữ điểm UUID v7.
- `tenant_id` (UUID, NOT NULL): Định danh tenant.
- `customer_id` (UUID, FK `customers(id)`): Khách hàng bị giữ điểm.
- `order_id` (UUID, NOT NULL): Mã đơn hàng tham chiếu từ Order Service.
- `checkout_attempt_id` (UUID, NOT NULL): Khóa duy nhất của lượt checkout sở hữu vòng đời giữ điểm.
- `requested_points` (BIGINT, NOT NULL): Số điểm khách hàng yêu cầu đổi.
- `held_points` (BIGINT, NOT NULL): Số điểm thực tế đã giữ sau khi thẩm định số dư và hạn mức.
- `state` (TEXT, CHECK `IN ('HELD', 'COMMITTED', 'RELEASED', 'EXPIRED')`): Trạng thái của lượt giữ điểm.
- `expires_at` (TIMESTAMPTZ, NOT NULL): Thời hạn giữ điểm kế thừa từ checkout attempt của Order Service.
- `idempotency_key` (TEXT, NOT NULL): Khóa chống trùng lặp của lệnh giữ điểm.
- `created_at`, `updated_at` (TIMESTAMPTZ, NOT NULL): Dấu thời gian vòng đời.
- `version` (BIGINT, NOT NULL): Số phiên bản kiểm soát khóa lạc quan.

#### Bảng `loyalty_balances` (Bảng tổng hợp số dư điểm thưởng)
- `tenant_id` (UUID, NOT NULL): Định danh tenant.
- `customer_id` (UUID, NOT NULL, PK): Định danh khách hàng.
- `earned_points` (BIGINT, NOT NULL): Tổng điểm đã tích lũy trọn đời.
- `redeemed_points` (BIGINT, NOT NULL): Tổng điểm đã chính thức đổi trừ tiền.
- `reversed_points` (BIGINT, NOT NULL): Tổng số điểm đã bị thu hồi do hoàn đơn.
- `expired_points` (BIGINT, NOT NULL): Tổng số điểm đã bị hết hạn.
- `held_points` (BIGINT, NOT NULL): Tổng số điểm đang bị giữ chỗ cho các đơn hàng chưa thanh toán.
- `available_points` (BIGINT, CHECK `>= 0`): Số dư điểm khả dụng có thể tiếp tục sử dụng.
- `projection_version` (BIGINT, NOT NULL): Số phiên bản biến động của bảng projection.
- `calculated_at` (TIMESTAMPTZ, NOT NULL): Thời điểm tính toán cập nhật số dư.

#### Các Bảng Quản Lý Hạng Thành Viên, Khuyến Mãi & Voucher
- `loyalty_point_policies`: Quản lý các phiên bản chính sách tích/đổi điểm, tỷ lệ quy đổi tiền tệ sang điểm, giới hạn % tối đa được đổi và thời hạn hết hạn điểm mặc định.
- `membership_tier_configs`: Quản lý ngưỡng định mức xét hạng (`threshold_value`), chỉ số áp dụng (`ELIGIBLE_SPEND_MINOR` hoặc `EARNED_POINTS`) và chu kỳ trượt (mặc định 365 ngày).
- `membership_tier_history`: Sổ nhật ký ghi lại toàn bộ các lần thăng/hạ hạng của khách hàng, lưu vết hạng cũ, hạng mới, giá trị chỉ số đạt được và mã run ID.
- `promotions` & `promotion_versions`: Quản lý các chương trình và phiên bản quy tắc khuyến mãi bất biến, lưu rõ loại rule, độ ưu tiên, cờ cho phép cộng dồn (`stackable`), nhóm xung đột (`conflict_group`), phạm vi áp dụng và cấu hình điều kiện/hành động dạng JSONB đã validate.
- `vouchers`: Quản lý danh mục mã voucher đã chuẩn hóa viết hoa, liên kết phiên bản quy tắc áp dụng, hạn mức tổng toàn tenant, hạn mức trên mỗi khách, khoảng thời gian hiệu lực và chi tiêu tối thiểu.
- `voucher_reservations` & `voucher_usages`: Quản lý việc tạm giữ voucher theo lượt checkout (`RESERVED`, `CONSUMED`, `RELEASED`, `EXPIRED`) và lịch sử tiêu thụ chính thức sau khi đơn thanh toán thành công.

#### Các Bảng Hạ Tầng Độ Tin Cậy, Idempotency & Kiểm Toán
- `inbox_events`: Quản lý các sự kiện nhận được từ EventBridge, hỗ trợ khử trùng lặp theo `(tenant_id, event_id)` và lưu vết trạng thái xử lý (`RECEIVED`, `PROCESSED`, `FAILED`, `DLQ`).
- `outbox_events`: Quản lý các sự kiện domain của Loyalty Service chờ xuất bản lên EventBridge theo mô hình at-least-once.
- `audit_events`: Ghi nhận nhật ký kiểm toán cho các hành vi xem dữ liệu PII, thu hồi điểm, phát hành cấu hình, tiêu thụ voucher và các lỗi phân quyền.
- `worker_runs`: Quản lý metadata vận hành của các tiến trình worker nền, lưu thông tin phân bổ lease lock phân tán (`lease_owner`, `lease_expires_at`), số lượng bản ghi xử lý và nhật ký lỗi.

### 4.2 Các Ràng Buộc Toàn Vẹn & Chỉ Mục Cơ Sở Dữ Liệu
- Ràng buộc duy nhất trên bảng khách hàng: `UNIQUE (tenant_id, phone_lookup_hash)`.
- Ràng buộc duy nhất chống trùng lặp tích điểm: `UNIQUE (tenant_id, source_type, source_id, entry_type)`.
- Ràng buộc duy nhất của lượt giữ điểm: `UNIQUE (tenant_id, checkout_attempt_id)`.
- Chỉ mục tối ưu hóa thuật toán FIFO trên bảng lô điểm: `(tenant_id, customer_id, expires_at, earned_at, id)`.
- Ràng buộc duy nhất mã voucher trong tenant: `UNIQUE (tenant_id, code_normalized)`.
- Ràng buộc duy nhất giữ voucher: `UNIQUE (tenant_id, voucher_id, checkout_attempt_id, customer_id)`.
- Chỉ mục kiểm tra hạn mức sử dụng voucher: `(tenant_id, voucher_id, customer_id, consumed_at)`.
- Chỉ mục phân trang con trỏ sổ cái: `(tenant_id, customer_id, occurred_at DESC, id)`.
- Ràng buộc duy nhất bảng Inbox: `UNIQUE (tenant_id, event_id)`.
- Chỉ mục cho tiến trình Outbox Publisher: `(publish_state, next_attempt_at, created_at)`.
- Không phân vùng bảng trong P0; xem xét phân vùng theo khoảng thời gian `occurred_at` khi bảng sổ cái vượt ngưỡng 10 triệu dòng.

### 4.3 Tái Tạo Bảng Tổng Hợp Số Dư (Projection Rebuild)
- Bảng `loyalty_balances` và trường `customers.current_tier` là các view tổng hợp được vật chất hóa (materialized projections), không phải là nguồn chân lý duy nhất.
- Tiến trình tái tạo số dư đọc toàn bộ lịch sử sổ cái, phân bổ lô điểm, trạng thái các đơn giữ điểm và lịch sử hạng theo từng khách hàng.
- Quá trình tái tạo chạy độc lập bên ngoài hot path của quầy POS, có mã run ID, phân bổ checkpoint/lease an toàn và tráo đổi projection nguyên tử.
- Nếu phát hiện sai lệch số dư (mismatch), hệ thống phát cảnh báo vận hành; tuyệt đối không tự ý sửa đổi dữ liệu sổ cái để "khớp" với projection.

---

## 5. Các Máy Trạng Thái Domain (Domain State Machines)

### 5.1 Vòng Đời Giữ Điểm Thưởng (Point Hold State Machine)

```text
                 ┌──────────────┐
        Tạo mới → │    HELD      │ ── OrderPaid.v1 ──→ COMMITTED
                 └──────┬───────┘
                        │
         Thất bại / Hủy / Quá thời gian
                        │
                        ├────────→ RELEASED
                        └────────→ EXPIRED (Worker dọn dẹp sau khi quá hạn)
```

- Chỉ các bản ghi ở trạng thái `HELD` mới được phép chuyển trạng thái; các yêu cầu retry trên trạng thái terminal sẽ trả về kết quả đã lưu trước đó.
- Lệnh Commit chuyển sang `COMMITTED`, ghi một dòng `REDEEMED` vào sổ cái và tạo bản ghi phân bổ tiêu hao; lệnh Release chuyển sang `RELEASED` và hoàn trả điểm khả dụng, tuyệt đối không ghi dòng `REDEEMED`.
- Khi lượt giữ điểm bị quá hạn, tiến trình dọn dẹp chuyển trạng thái sang `EXPIRED` dựa trên thời hạn `expires_at` và trạng thái đơn hàng của Order Service, không tự ý trừ điểm của khách.

### 5.2 Vòng Đời Tạm Giữ Voucher (Voucher Reservation State Machine)

```text
RESERVED ── OrderPaid.v1 ──→ CONSUMED
    │
    ├── Thất bại / Hủy đơn ──→ RELEASED
    └── Quá hạn expires_at ──→ EXPIRED
```

- Bộ đếm số lượt đã sử dụng của voucher chỉ chính thức tăng khi chuyển sang trạng thái `CONSUMED`.
- Quá trình tạm giữ và tiêu thụ đồng thời phải kiểm tra chặt chẽ các hạn mức tổng/khách hàng trong transaction cơ sở dữ liệu.

### 5.3 Vòng Đời Xử Lý Sự Kiện Inbox (Event Processing State Machine)

```text
RECEIVED → PROCESSING → PROCESSED
                  └── Lỗi tạm thời (Retry) → RECEIVED
                  └── Hết số lần retry → DLQ
```

Bản ghi trong bảng Inbox tuyệt đối không được đánh dấu `PROCESSED` trước khi database transaction của nghiệp vụ domain commit thành công.

---

## 6. Chi Tiết Các Luồng Nghiệp Vụ Trọng Yếu (Critical Flows)

### 6.1 Báo Giá, Giữ Điểm & Thanh Toán Đơn Hàng Thành Công
```mermaid
sequenceDiagram
    autonumber
    participant POS as Máy POS
    participant Order as Order Service / Saga
    participant Loyalty as Loyalty API
    participant DB as Loyalty PostgreSQL
    participant Payment as Payment Service
    participant Bus as EventBridge / SQS

    POS->>Order: Yêu cầu Checkout (customerId, voucherCode, requestedPoints)
    Order->>Loyalty: POST /internal/v1/loyalty/promotions/quote + Giữ điểm/voucher
    Loyalty->>DB: Khóa dòng khách hàng/lô điểm; thẩm định quy tắc; giữ điểm/voucher nguyên tử
    DB-->>Loyalty: Trả về Quote + Hold ID + Voucher Reservation ID + thời hạn
    Loyalty-->>Order: PromotionQuote + PointHold + VoucherReservation
    Order->>Payment: Khởi tạo giao dịch thanh toán
    Payment-->>Order: Báo thanh toán thành công (Paid)
    Order->>Order: Commit kho nguyên liệu và hoàn tất đơn hàng
    Order-->>Bus: Phát sự kiện OrderPaid.v1 kèm tham chiếu Loyalty
    Bus-->>Loyalty: Phân phối sự kiện OrderPaid.v1 (at-least-once)
    Loyalty->>DB: Khử trùng lặp Inbox; ghi EARNED; commit REDEEMED; tiêu thụ voucher; cập nhật projection
    Loyalty->>DB: Ghi bản ghi Outbox và Audit trong cùng transaction
```
*Lưu ý:* Nếu thanh toán, commit kho hoặc đơn hàng thất bại, Saga sẽ gửi lệnh giải phóng (`Release`). Lệnh release đảm bảo tính idempotent và không ghi nhận giao dịch trừ điểm trong sổ cái.

### 6.2 Xử Lý Sự Kiện `OrderPaid` Bị Gửi Lặp / Retry
```mermaid
sequenceDiagram
    autonumber
    participant Bus as EventBridge / SQS
    participant Consumer as Loyalty Consumer
    participant DB as Loyalty PostgreSQL
    participant Outbox as Outbox Publisher

    Bus->>Consumer: OrderPaid.v1 (eventId)
    Consumer->>DB: Bắt đầu transaction; chèn vào inbox_events (eventId)
    alt Sự kiện bị gửi lặp (Duplicate Event)
        DB-->>Consumer: Xung đột khóa duy nhất / Đã xử lý xong trước đó
        Consumer-->>Bus: Acknowledge message (xử lý no-op an toàn)
    else Sự kiện đến lần đầu (First Delivery)
        Consumer->>DB: Xác thực tenant/khách hàng/tính duy nhất của nguồn
        Consumer->>DB: Ghi sổ cái EARNED + tạo point lots + cập nhật số dư projection
        Consumer->>DB: Commit Inbox + ghi Audit + ghi Outbox
        DB-->>Consumer: Transaction commit thành công
        Consumer-->>Bus: Acknowledge message hoàn tất
        Outbox-->>Bus: Quét và xuất bản các sự kiện của Loyalty lên EventBridge
    end
```

### 6.3 Hoàn Tiền Toàn Bộ Đơn Hàng & Thu Hồi Điểm (Full Refund & Reversal)
```mermaid
sequenceDiagram
    autonumber
    participant Order as Order Service
    participant Bus as EventBridge / SQS
    participant Loyalty as Loyalty Consumer
    participant DB as Loyalty PostgreSQL

    Order-->>Bus: OrderRefunded.v1 (refundId, refundType = FULL)
    Bus-->>Loyalty: Phân phối sự kiện hoàn tiền
    Loyalty->>DB: Khử trùng lặp refundId; nạp toàn bộ sổ cái/phân bổ của đơn hàng cũ
    Loyalty->>DB: Ghi dòng sổ cái REVERSED đối ứng; khôi phục điểm đã đổi bằng lô điểm mới
    Loyalty->>DB: Cập nhật lại số dư/chỉ số xét hạng thành viên; ghi Audit + Outbox
    DB-->>Loyalty: Transaction commit thành công
    Loyalty-->>Bus: Acknowledge message hoàn tất
```

### 6.4 Báo Giá Khuyến Mãi & Giữ Lượt Voucher
1. Order Service gửi bản chụp giỏ hàng bất biến, ngữ cảnh chi nhánh/thời gian/kênh bán, thông tin khách hàng/hạng thẻ và mã voucher.
2. Promotion Engine nạp các phiên bản quy tắc khuyến mãi đang active theo tenant/thời gian/chi nhánh.
3. Engine thẩm định toàn bộ các quy tắc ứng viên, gom nhóm theo `conflictGroup`, chọn phương án có lợi nhất cho khách hàng và phá vỡ thế hòa một cách tất định.
4. Nếu có mã voucher, Voucher Module khóa dòng kiểm tra hạn mức sử dụng và tạo đơn tạm giữ gắn với lượt checkout.
5. Loyalty Service trả về báo giá chiết khấu, phân bổ dòng món, phiên bản quy tắc áp dụng, thời hạn báo giá và lý do từ chối các quy tắc không thỏa mãn.
6. Order Service kiểm tra lại tính hợp lệ của báo giá trước khi chốt đơn; nếu báo giá bị quá hạn hoặc quy tắc đã bị thu hồi thì kích hoạt cơ chế fail-closed và yêu cầu lấy báo giá mới.

---

## 7. Các Tiến Trình Nền & Xử Lý Sự Cố (Jobs & Failure Handling)

| Tiến trình Worker | Cơ chế kích hoạt | Hành vi Transaction & Lease Lock | Kết quả mong đợi |
|---|---|---|---|
| **Quét hết hạn điểm (Point Expiry)** | Hàng ngày theo múi giờ tenant | Khóa các lô điểm còn điểm (`remaining_points > 0`); tạo bản ghi `EXPIRED` và phân bổ tiêu hao idempotent | Tuyệt đối không làm hết hạn phần điểm đang bị giữ chỗ |
| **Tính toán lại hạng (Tier Recalculation)** | Sau sự kiện biến động hoặc định kỳ hàng ngày | Tính toán tổng chi tiêu/điểm trong chu kỳ trượt 12 tháng, khóa dòng cập nhật hạng | Ghi lịch sử hạng và phát sự kiện `MembershipTierChanged.v1` nếu có thay đổi |
| **Dọn dẹp đơn giữ treo (Cleanup)** | Quét định kỳ theo `expires_at` | Giành quyền xử lý qua lease lock; kiểm tra trạng thái đơn hàng tại Order Service trước khi release | Giải phóng điểm giữ và voucher quá hạn đúng một lần |
| **Tái tạo số dư (Projection Rebuild)** | Lệnh bảo trì thủ công của Admin khi có cảnh báo lệch | Phân bổ checkpoint, xử lý theo batch, tráo đổi dữ liệu projection an toàn | Tái tạo chính xác từ đầu, không sửa dữ liệu sổ cái |
| **Xuất bản Outbox (Outbox Publisher)** | Quét liên tục chu kỳ ngắn | Giành quyền xử lý lease message, retry có exponential backoff | Gửi sự kiện at-least-once hoặc đẩy vào DLQ khi lỗi |
| **Replay Inbox / DLQ** | Lệnh vận hành có kiểm soát | Replay sự kiện đi qua đúng luồng kiểm tra `eventId`/idempotency | Không bypass qua các tầng thẩm định domain |

---

## 8. Kiến Trúc An Toàn Bảo Mật (Security Architecture)

- **Xác thực API Gateway:** API Gateway xác thực Cognito Access Token; Loyalty Service tái kiểm tra tính hợp lệ của issuer, audience, thời hạn token, vai trò, tenant và phạm vi chi nhánh.
- **Xác thực API Nội bộ:** Toàn bộ API nội bộ sử dụng Workload Identity / mTLS; client công khai tuyệt đối bị chặn không thể truy cập các route nội bộ.
- **Phòng thủ đa tenant chuyên sâu:** `tenant_id` luôn được trích xuất từ ngữ cảnh xác thực tin cậy ở server và bắt buộc áp dụng trong toàn bộ câu lệnh truy vấn cơ sở dữ liệu. Tham số `tenantId` truyền từ client không được dùng làm bằng chứng cấp quyền.
- **Mã hóa dữ liệu PII:** Số điện thoại khách hàng được mã hóa đối xứng theo cơ chế AWS KMS Envelope Encryption; khóa băm HMAC pepper lưu trữ an toàn trong AWS Secrets Manager; cơ chế xoay vòng khóa (key rotation) không làm mất khả năng tra cứu các hồ sơ cũ.
- **Chính sách ghi nhật ký:** Tuyệt đối không log số điện thoại dạng plaintext, mã OTP, token xác thực, khóa mã hóa thô, hay payload quy tắc chứa thông tin nhạy cảm.
- **Nhật ký kiểm toán bất biến:** Mọi thao tác truy cập dữ liệu PII của khách hàng, thu hồi điểm, phát hành cấu hình, tiêu thụ voucher và các lỗi từ chối phân quyền đều được ghi vào bảng `audit_events`.
- **Chống rò rỉ thông tin (Anti-Enumeration):** Endpoint tra cứu số điện thoại công khai và gửi mã OTP áp dụng giới hạn tần suất (rate limit), phản hồi chuẩn hóa không làm lộ việc số điện thoại đó đã đăng ký tài nguyên hay chưa.
- **Thẩm định quy tắc khuyến mãi an toàn:** Dữ liệu cấu hình quy tắc dạng JSONB chỉ được phân tích cú pháp thông qua schema allowlist an toàn; tuyệt đối không thực thi các đoạn mã kịch bản (script) tùy ý từ database.

---

## 9. Khả Năng Quan Sát Hệ Thống (Observability)

- **Truy vết phân tán (Distributed Tracing):** Gắn liền chuỗi correlation ID và causation ID xuyên suốt từ POS → Order Service / Step Functions → Loyalty Service → Payment Service / Message Broker.
- **Các chỉ số đo lường cốt lõi (Metrics):**
  - Tỷ lệ tra cứu/tạo khách hàng thành công, xung đột và số lần từ chối truy cập sai tenant.
  - Độ trễ tính toán báo giá khuyến mãi, tỷ lệ báo giá hết hạn và số lượng quy tắc bị từ chối.
  - Tỷ lệ giữ điểm thành công, mức độ tranh chấp giữ điểm, tỷ lệ giải phóng/hết hạn.
  - Khối lượng điểm tích lũy, đổi điểm, thu hồi điểm, tỷ lệ sự kiện trùng lặp và độ sâu hàng đợi DLQ.
  - Sai lệch số dư giữa sổ cái và projection, thời gian thực hiện rebuild projection.
  - Tỷ lệ xung đột khi giữ voucher, tỷ lệ tiêu thụ/giải phóng và số lần từ chối do chạm hạn mức.
  - Kết quả tính toán lại hạng thành viên và số lượng khách hàng thay đổi thứ hạng.
  - Tỷ lệ lỗi gửi OTP trên cổng khách hàng và số lần vi phạm giới hạn tần suất.
- **Cảnh báo tự động (Alerts):** Cảnh báo ngay lập tức khi phát hiện bất biến số dư bị âm, độ sâu hàng đợi DLQ tăng đột biến, độ trễ sự kiện vượt quá 60 giây, phát sinh các đơn giữ điểm/voucher bị treo quá thời hạn, hoặc có dấu hiệu tấn công quét tài khoản xuyên tenant.

---

## 10. Bảng Quyết Định Kiến Trúc (Architecture Decision Records - ADR)

| Mã ADR | Quyết định đã chốt | Các phương án thay thế | Lý do & Căn cứ kỹ thuật |
|---|---|---|---|
| **LOY-ADR-01** | Sử dụng database PostgreSQL riêng biệt cho Loyalty Service | Dùng chung database với Order, dùng NoSQL làm sổ cái chính | Đảm bảo tính toàn vẹn transaction, ràng buộc toàn vẹn dữ liệu, thuật toán phân bổ FIFO và cô lập dữ liệu tenant rõ ràng. |
| **LOY-ADR-02** | Kết hợp Transactional Outbox và Inbox với AWS EventBridge/SQS | Gửi trực tiếp lên broker, áp dụng 2PC | Loại bỏ triệt để nguy cơ mất mát dữ liệu do dual-write, hỗ trợ phân phối at-least-once, replay sự kiện và hàng đợi chết DLQ. |
| **LOY-ADR-03** | Tách biệt đơn giữ điểm (`point_holds`) khỏi sổ cái bất biến | Ghi nhận giao dịch `HELD` vào sổ cái trước khi thanh toán | Phân biệt rõ ràng giữa việc tạm giữ điểm và việc tiêu điểm chính thức, đơn giản hóa quy trình tái tạo bảng số dư. |
| **LOY-ADR-04** | Sự kiện `OrderPaid.v1` là tín hiệu kích hoạt tích điểm và commit điểm | Lắng nghe `PaymentPaid.v1`, quét định kỳ Order DB | Order Service là cơ quan có thẩm quyền chốt đơn thanh toán chuẩn tắc; ngăn chặn việc trừ kho hoặc tích điểm sớm trước khi đơn hoàn tất. |
| **LOY-ADR-05** | Order Service phát sự kiện hoàn tiền chuẩn tắc `OrderRefunded.v1` | Loyalty Service lắng nghe trực tiếp từ Payment Gateway | Nghiệp vụ hoàn tiền thuộc phạm vi quản lý của đơn hàng, tránh việc thu hồi điểm bị trùng lặp giữa tầng thanh toán và tầng đơn. |
| **LOY-ADR-06** | Quản lý các lô điểm (`point_lots`) kết hợp vết phân bổ | Chỉ lưu một số dư tổng hợp duy nhất | Bắt buộc phải có để thực hiện thuật toán hết hạn FIFO, truy vết nguồn gốc thu hồi điểm và phục hồi dữ liệu khi có sự cố. |
| **LOY-ADR-07** | Phiên bản quy tắc khuyến mãi bất biến lưu dưới dạng JSONB có thẩm định | Chỉnh sửa trực tiếp quy tắc active, dùng script động | Đảm bảo tính tất định khi tính báo giá, tái hiện được lịch sử chiết khấu của đơn hàng cũ, an toàn tuyệt đối khi thực thi. |
| **LOY-ADR-08** | Tuyệt đối không dùng cache trong đường dẫn kiểm tra số dư và giữ điểm | Dùng Redis làm nguồn chân lý duy nhất | Các thao tác giữ điểm, xử lý đồng thời và ghi sổ cái đòi hỏi tính nhất quán mạnh (ACID) của cơ sở dữ liệu chính. |
| **LOY-ADR-09** | Không phân vùng bảng cơ sở dữ liệu trong giai đoạn P0 | Phân vùng bảng ngay từ ngày đầu tiên | Tránh gây phức tạp hóa các ràng buộc khóa ngoại và khóa duy nhất khi khối lượng dữ liệu chưa lớn; đánh giá lại khi vượt mốc 10 triệu dòng. |

---

## 11. Các Yêu Cầu Thay Đổi Hợp Đồng Giữa Các Module (Cross-Module Changes)

Trước khi bước vào giai đoạn triển khai mã nguồn, tài liệu Hợp đồng API & Dữ liệu cần chuẩn hóa:
1. Bổ sung các trường `loyaltyEligibleAmountMinor` và `loyaltyPolicyVersion` vào sự kiện `OrderPaid.v1` của Order Service.
2. Bổ sung các trường tùy chọn `pointHoldId` và `voucherReservationId` vào bản chụp giỏ hàng và sự kiện thanh toán để Loyalty Service commit chính xác đơn giữ chỗ.
3. Định nghĩa sự kiện `OrderRefunded.v1` do Order Service phát hành sau khi hoàn tất thủ tục hoàn tiền toàn bộ, bao gồm: `refundId`, `refundType = 'FULL'`, mã tham chiếu đơn hàng gốc, tenant/chi nhánh và vết idempotency.
4. Chuẩn hóa ranh giới lệnh hủy/hết hạn checkout từ Order Service để giải phóng các lượt giữ điểm và voucher kịp thời.
5. Xây dựng bộ kiểm thử hợp đồng phía consumer cho các kịch bản sự kiện gửi lặp, đến sai thứ tự, replay và kiểm tra quyền sở hữu đa tenant.
