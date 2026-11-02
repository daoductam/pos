# Module 7 — Dịch Vụ Ca Làm Việc (Shift Service): Hợp Đồng API & Dữ Liệu (API & Data Contracts)

**Ngày:** 2026-10-13  
**Trạng thái:** Đã phê duyệt (Approved)  
**Phạm vi:** M — Luồng Service Tiêu chuẩn (Standard Service Flow)  
**Module:** Quản lý Ca làm việc & Đối soát tiền mặt (Shift Service)

---

## Nguồn tài liệu và Quyền hạn Hợp đồng

- [Tầm nhìn Shift (Vision)](./2026-10-13-vision.md)
- [Yêu cầu Kỹ thuật Shift (Requirements)](./2026-10-13-requirements.md)
- [Kiến trúc Kỹ thuật Shift (Architecture)](./2026-10-13-architecture.md)
- [Biểu đồ Trình tự Shift (Sequence Diagrams)](./2026-10-13-sequence-diagrams.adoc)
- [Hợp đồng Identity & Access](../identity-access/2026-10-12-contracts.md)
- [Hợp đồng Order Service](../order/2026-10-12-contracts.md)
- [Hợp đồng Payment Service](../payment/2026-10-12-contracts.md)
- [Hợp đồng Nền tảng POS](../pos-platform/2026-10-12-contracts.md)

Tài liệu này là đặc tả chuẩn mực (normative contract) cho toàn bộ REST API công khai và nội bộ của Shift Service, cấu trúc JSON Schema cho các sự kiện bất đồng bộ, lược đồ dữ liệu cơ sở dữ liệu PostgreSQL và các bản migration Flyway thực thi được. Quá trình triển khai mã nguồn bắt buộc phải sinh/kiểm tra tính hợp lệ của đặc tả OpenAPI 3.1, AsyncAPI/JSON Schema và các file migration từ các định nghĩa trong tài liệu này trước khi viết code controller hoặc domain.

---

## 1. Các Nguyên Tắc Thiết Kế Hợp Đồng (Contract Principles)

- **Kiến trúc Bounded Context & Quyền Sở hữu:**
  - Shift Service là một Bounded Context độc lập sở hữu cơ sở dữ liệu PostgreSQL riêng (`pos_shift`).
  - Shift Service sở hữu toàn bộ vòng đời của ca làm việc (`shift lifecycle`), rào chắn tiếp nhận giao dịch (`admission fence`), tiền đầu ca (`opening float`), sự kiện tiền mặt (`cash facts`), các lượt kiểm đếm tiền mặt (`cash count attempts`), quy trình xem xét chênh lệch (`discrepancy review`) và hồ sơ đối soát ngoại lệ (`reconciliation cases`).
  - Order, Payment và AWS Step Functions Saga **tuyệt đối không** đọc/ghi trực tiếp vào cơ sở dữ liệu của Shift; chỉ giao tiếp qua REST API nội bộ và sự kiện domain.
- **Rào chắn Tiếp nhận Đồng bộ (Synchronous Admission & Local Close Fence):**
  - Trước khi thực hiện checkout hoặc thanh toán tiền mặt, Order/Saga bắt buộc phải xin một quyền tiếp nhận giao dịch (`transaction admission`) từ Shift Service.
  - Admission được cấp số thứ tự tăng dần đơn điệu (`admission_seq`) và mã mờ `admissionId`.
  - Khi đóng ca, hệ thống kích hoạt rào chắn đóng ca (`close fence`) ghi nhận số thứ tự rào chắn (`close_fence_seq`) trong cùng transaction với ca làm việc.
  - Các giao dịch được cấp admission trước rào chắn (`admission_seq <= close_fence_seq`) được phép hoàn tất và tính vào ca; mọi giao dịch gửi tới sau rào chắn lập tức bị từ chối với mã lỗi `409 transaction_after_close_fence`.
- **Định danh & Thời gian:**
  - Toàn bộ khóa chính và định danh thực thể (`shiftId`, `admissionId`, `cashFactId`, `countAttemptId`, `caseId`, `outboxMessageId`) đều là chuỗi **UUID v7**.
  - Toàn bộ dấu thời gian đều là chuỗi ISO-8601 UTC trong API (`YYYY-MM-DDTHH:mm:ssZ`) và kiểu `TIMESTAMPTZ` trong PostgreSQL. Ngày kinh doanh (`businessDate`) là chuỗi `YYYY-MM-DD` được tính toán theo múi giờ chi nhánh (`Asia/Ho_Chi_Minh` - ICT).
- **Tiền tệ & Đơn vị Minor:**
  - Tiền tệ trong P0 là VND (`currency = "VND"`).
  - Số tiền luôn là số nguyên `int64` tính theo đơn vị minor VND (`openingFloatMinor`, `cashCollectedMinor`, `cashRefundedMinor`, `expectedCashMinor`, `countedCashMinor`).
  - Tiền chênh lệch (`discrepancyMinor = countedCashMinor - expectedCashMinor`) là số nguyên có dấu (`int64`). Nghiêm cấm tuyệt đối dùng số thực dấu phẩy động (`float`, `double`).
- **Bảo mật Đa người thuê & Duy nhất một Két tiền mặt (Single Drawer Invariant):**
  - Trong P0, mỗi chi nhánh chỉ có **duy nhất một ca làm việc hoạt động** tại một thời điểm (két tiền mặt dùng chung giữa các nhân viên thu ngân trong cùng một ca).
  - Trạng thái `PENDING_REVIEW` khóa toàn bộ chi nhánh: không cho phép mở ca mới và không cho phép checkout đơn hàng mới cho đến khi ca chờ duyệt được giải quyết xong.
- **Tính Bất biến (Terminal State Invariants):**
  - Các trạng thái `CLOSED` và `CANCELLED` là trạng thái kết thúc (Terminal). Tuyệt đối không cho phép mở lại (reopen) hoặc sửa đổi số liệu của một ca đã đóng.
  - Khi có sự kiện tiền mặt gửi đến trễ sau khi ca đã đóng: Hệ thống ghi nhận vào hồ sơ đối soát ngoại lệ `reconciliation_cases` và phát sự kiện `ShiftCashFactReconciled.v1`, **tuyệt đối không làm thay đổi báo cáo của ca đã đóng**.

---

## 2. Tiêu Chuẩn HTTP Chung (Common HTTP Contract)

### 2.1 Các HTTP Header Quy Chuẩn

| Header | Bắt buộc | Phạm vi | Ý nghĩa kỹ thuật |
|---|:---:|---|---|
| `Authorization` | Bắt buộc | Public API | `Bearer <Cognito access token>`. Xác thực danh tính người dùng và quyền qua Identity Service. |
| `Authorization` | Bắt buộc | Internal API | `Bearer <Workload Identity / mTLS token>`. Xác thực danh tính của AWS Step Functions Saga, Order hoặc Payment Service. |
| `X-Active-Branch-Id` | Bắt buộc | Public API chi nhánh | Chi nhánh UI đang thao tác; server bắt buộc kiểm tra lại quyền thành viên của người gọi tại chi nhánh này. |
| `X-Correlation-Id` | Khuyến nghị | Tất cả | Mã UUID v7 truy vết phân tán xuyên suốt qua POS, Saga, Payment, Shift và EventBridge. |
| `X-Causation-Id` | Có điều kiện | Internal / Sự kiện | Định danh của lệnh hoặc sự kiện trực tiếp gây ra thao tác hiện tại. |
| `Idempotency-Key` | Bắt buộc | Các lệnh ghi (POST/PATCH) | Khóa duy nhất chống trùng lặp trong phạm vi `tenant + actor/workload + operation`. |
| `If-Match` | Bắt buộc | Phê duyệt / Phiên bản | ETag hoặc số phiên bản review (`review_version`) để kiểm soát xung đột ghi đè lạc quan. |
| `Content-Type` | Bắt buộc | Request có body | `application/json; charset=utf-8`. |

---

### 2.2 Định Dạng Bao Bì Chuẩn Hóa (Envelope Standards)

#### 1. Phản hồi Thực thể Đơn lẻ (Single Entity Response)

```json
{
  "data": {},
  "meta": {
    "requestId": "req_0192b310-7f1d-7abc-8d2c-123456789001",
    "correlationId": "0192b310-7f1d-7abc-8d2c-123456789002"
  }
}
```

#### 2. Phản hồi Phân trang theo Con trỏ (Cursor-based Paginated Response)

```json
{
  "data": [],
  "meta": {
    "hasMore": true,
    "nextCursor": "eyJjcmVhdGVkQXQiOiIyMDI2LTEwLTEzVDE3OjAwOjAwWiIsImlkIjoiMDE5MmIzMTAtN2YxZC03YWJjLThkMmMtMTIzNDU2Nzg5MDEyIn0=",
    "limit": 20,
    "requestId": "req_0192b310-7f1d-7abc-8d2c-123456789003",
    "correlationId": "0192b310-7f1d-7abc-8d2c-123456789002"
  }
}
```

#### 3. Phản hồi Lỗi Chuẩn Hóa (Standard Error Envelope)

```json
{
  "error": {
    "code": "transaction_after_close_fence",
    "message": "Ca làm việc đã kích hoạt rào chắn đóng ca, không chấp nhận thêm giao dịch mới",
    "details": [
      {
        "field": "shiftId",
        "code": "fence_closed",
        "message": "Rào chắn đã kích hoạt tại số thứ tự 42 lúc 2026-10-13T00:04:59Z. Vui lòng mở ca mới để tiếp tục bán hàng."
      }
    ],
    "requestId": "req_0192b310-7f1d-7abc-8d2c-123456789004",
    "correlationId": "0192b310-7f1d-7abc-8d2c-123456789002"
  }
}
```

---

### 2.3 Bảng Mã Lỗi Chuẩn Hóa và Mã Trạng Thái HTTP

| HTTP Status | Mã lỗi (`error.code`) | Ý nghĩa nghiệp vụ | Hành vi Thử lại (Retryable) |
|:---:|---|---|:---:|
| `200 OK` | — | Đọc dữ liệu thành công hoặc đóng ca hoàn tất (`COMPLETED`). | Không |
| `201 Created` | — | Mở ca mới hoặc tiếp nhận giao dịch thành công. | Không |
| `202 Accepted` | `close_processing` | Rào chắn đóng ca đã kích hoạt, hệ thống đang chờ drain các giao dịch đã tiếp nhận trước rào chắn. | Có (polling trạng thái) |
| `400 Bad Request` | `validation_error` | Yêu cầu sai định dạng, thiếu trường bắt buộc hoặc vi phạm schema. | Không |
| `401 Unauthorized` | `unauthorized` | Token xác thực thiếu, hết hạn hoặc không hợp lệ. | Có (sau khi làm mới token) |
| `403 Forbidden` | `forbidden` | Người dùng hoặc service không có thẩm quyền truy cập tài nguyên. | Không |
| `404 Not Found` | `not_found` | Không tìm thấy ca làm việc hoặc chi nhánh không tồn tại trong tenant. | Không |
| `409 Conflict` | `idempotency_conflict` | Cùng `Idempotency-Key` nhưng hash payload yêu cầu bị thay đổi. | Không |
| `409 Conflict` | `shift_already_open` | Chi nhánh đã tồn tại một ca đang ở trạng thái `ACTIVE` hoặc `PENDING_REVIEW`. | Không (phải đóng ca cũ) |
| `409 Conflict` | `shift_pending_review` | Chi nhánh đang có ca chờ duyệt chênh lệch; khóa toàn bộ thao tác bán hàng. | Không (phải xử lý review) |
| `409 Conflict` | `shift_not_active` | Ca làm việc không ở trạng thái `ACTIVE` để thực hiện thao tác. | Không |
| `409 Conflict` | `transaction_after_close_fence` | Yêu cầu tiếp nhận gửi tới sau khi rào chắn đóng ca đã được kích hoạt. | Không (phải mở ca mới) |
| `409 Conflict` | `cash_count_required` | Chính sách chi nhánh yêu cầu kiểm đếm nhưng request đóng ca thiếu số tiền đếm. | Không (nhập tiền đếm) |
| `409 Conflict` | `version_conflict` | Phiên bản `If-Match` hoặc `review_version` không khớp dữ liệu máy chủ. | Lấy lại dữ liệu mới |
| `409 Conflict` | `count_attempt_immutable` | Cố ý chỉnh sửa một bản ghi kiểm đếm tiền mặt đã được gửi trước đó. | Không |
| `409 Conflict` | `opening_float_not_returned` | Hủy ca nhưng số tiền hoàn lại không khớp với tiền mặt đầu ca ban đầu. | Không (sửa lại số tiền) |
| `422 Unprocessable` | `invalid_amount` | Số tiền âm, sai loại tiền tệ (khác `VND`) hoặc tràn số. | Không |
| `429 Too Many Requests`| `rate_limited` | Vượt quá giới hạn tần suất yêu cầu tại API Gateway hoặc Service. | Có (lùi thời gian số mũ) |
| `503 Unavailable` | `service_unavailable` | Cơ sở dữ liệu hoặc phụ thuộc nội bộ tạm thời gián đoạn. | Có |

---

## 3. Danh Mục Các Endpoint

| Phương thức | Đường dẫn API | Phân loại | Thẩm quyền / Phạm vi | Ý nghĩa nghiệp vụ |
|---|---|:---:|---|---|
| `POST` | `/api/v1/branches/{branchId}/shifts` | Public | `shift:open` (`CASHIER`, `MANAGER`, `OWNER`) | Mở ca làm việc mới tại chi nhánh (khai báo tiền đầu ca). |
| `GET` | `/api/v1/branches/{branchId}/shifts/current` | Public | `shift:read` (`CASHIER`, `MANAGER`, `OWNER`) | Lấy thông tin ca làm việc hiện tại đang mở hoặc đang chờ duyệt. |
| `GET` | `/api/v1/branches/{branchId}/shifts` | Public | `shift:read` (`MANAGER`, `OWNER`) | Tra cứu lịch sử ca làm việc chi nhánh (phân trang con trỏ `cursor`). |
| `GET` | `/api/v1/shifts/{shiftId}` | Public | `shift:read` (`CASHIER`, `MANAGER`, `OWNER`) | Xem chi tiết ca làm việc (tóm tắt tiền mặt, các lượt đếm, review). |
| `POST` | `/api/v1/shifts/{shiftId}/close` | Public | `shift:close` (`CASHIER`, `MANAGER`, `OWNER`) | Kích hoạt rào chắn đóng ca và nộp kết quả kiểm đếm tiền mặt. |
| `POST` | `/api/v1/shifts/{shiftId}/cancel` | Public | `shift:cancel` (`MANAGER`, `OWNER`) | Hủy ca mở nhầm (chưa có giao dịch phát sinh và hoàn đủ tiền đầu ca). |
| `POST` | `/api/v1/shifts/{shiftId}/cash-counts` | Public | `shift:count` (`CASHIER`, `MANAGER`) | Gửi kết quả kiểm đếm lại (recount) sau khi quản lý yêu cầu. |
| `POST` | `/api/v1/shifts/{shiftId}/cash-counts/{countAttemptId}/approve` | Public | `shift:review` (`MANAGER`, `OWNER`) | Phê duyệt chênh lệch kiểm đếm và chính thức đóng ca (`CLOSED`). |
| `POST` | `/api/v1/shifts/{shiftId}/cash-counts/{countAttemptId}/request-recount` | Public | `shift:review` (`MANAGER`, `OWNER`) | Yêu cầu nhân viên thu ngân thực hiện kiểm đếm lại tiền mặt. |
| `POST` | `/internal/v1/shifts/{shiftId}/transaction-admissions` | Internal | Workload Identity (Order / Saga / Payment) | Xin quyền tiếp nhận giao dịch trước khi thanh toán / checkout. |
| `POST` | `/internal/v1/shifts/{shiftId}/transaction-admissions/{admissionId}/complete` | Internal | Workload Identity (Order / Saga / Payment) | Báo cáo hoàn tất hoặc hủy bỏ một giao dịch đã tiếp nhận. |
| `POST` | `/internal/v1/shifts/{shiftId}/cash-facts` | Internal | Workload Identity (Payment Service) | Ghi nhận sự kiện tiền mặt thực tế đã quyết toán (`CASH_COLLECTION` / `CASH_REFUND`). |
| `GET` | `/internal/v1/shifts/{shiftId}/cash-summary` | Internal | Workload Identity (Payment / Saga / Ops) | Tra cứu tóm tắt số liệu tiền mặt tính toán theo rào chắn đóng ca. |
| `POST` | `/internal/v1/shifts/reconciliation-cases/{caseId}/resolve` | Internal | Workload Identity (Operations / Admin) | Xử lý và giải quyết hồ sơ đối soát ngoại lệ (tiền về muộn sau đóng ca). |

---

## 4. Hợp Đồng Chi Tiết Các Endpoint

### 4.1 Các API Công Khai (Public REST APIs)

#### `POST /api/v1/branches/{branchId}/shifts`
Mở ca làm việc mới tại chi nhánh. Thao tác này khai báo số tiền mặt ban đầu trong két (`openingFloatMinor`), ghi nhận người vận hành thực tế và thiết lập ngày kinh doanh (`businessDate`).

- **Thẩm quyền:** Quyền `shift:open` tại chi nhánh đó.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <cognito-token>` (Bắt buộc)
  - `X-Active-Branch-Id: <uuid>` (Bắt buộc - phải khớp với `branchId` trên URL)
  - `Idempotency-Key: <unique-key>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Khuyến nghị)
  - `Content-Type: application/json; charset=utf-8` (Bắt buộc)
- **Tham số Đường dẫn (Path Parameters):**
  - `branchId` (UUID, Bắt buộc): Định danh chi nhánh mở ca.
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "openingFloatMinor": 1000000,
  "currency": "VND",
  "openedForUserId": null,
  "reason": null
}
```

*Quy cách kiểm tra (Validation):*
- `openingFloatMinor`: Số nguyên `int64 >= 0` (mặc định 0 nếu không có tiền lẻ đầu ca).
- `currency`: Bắt buộc `"VND"`.
- `openedForUserId`: Tùy chọn (chỉ dành cho `MANAGER`/`OWNER` khi mở ca thay cho nhân viên thu ngân).
- `reason`: Bắt buộc khi `openedForUserId` khác người gọi (1–500 ký tự).
- Bất biến: Chi nhánh không được phép có ca khác đang ở trạng thái `ACTIVE` hoặc `PENDING_REVIEW`.

- **Phản hồi Thành công (`201 Created`):**

```json
{
  "data": {
    "shiftId": "0192b310-7f1d-7abc-8d2c-123456789012",
    "tenantId": "0192b310-7f1d-7abc-8d2c-123456789013",
    "branchId": "0192b310-7f23-7abc-8d2c-123456789012",
    "openedByUserId": "0192b310-7f11-7abc-8d2c-123456789015",
    "operatorUserId": "0192b310-7f11-7abc-8d2c-123456789015",
    "status": "ACTIVE",
    "admissionPhase": "OPEN",
    "openingFloatMinor": 1000000,
    "currency": "VND",
    "businessDate": "2026-10-13",
    "branchTimezone": "Asia/Ho_Chi_Minh",
    "version": 1,
    "openedAt": "2026-10-13T06:00:00Z",
    "closeStatus": "NOT_STARTED"
  },
  "meta": {
    "requestId": "req_0192b310-7f1d-7abc-8d2c-123456789001",
    "correlationId": "0192b310-7f1d-7abc-8d2c-123456789002"
  }
}
```

- **Mã lỗi khả dĩ:** `403 Forbidden`, `409 Conflict` (`shift_already_open` hoặc `shift_pending_review`), `422 validation_error`.

---

#### `GET /api/v1/branches/{branchId}/shifts/current`
Lấy thông tin ca làm việc đang mở hoặc đang chờ duyệt tại chi nhánh.

- **Thẩm quyền:** Quyền `shift:read`.
- **Tham số Đường dẫn (Path Parameters):** `branchId` (UUID, Bắt buộc).
- **Phản hồi Thành công (`200 OK` — Khi có ca đang hoạt động):**

```json
{
  "data": {
    "shiftId": "0192b310-7f1d-7abc-8d2c-123456789012",
    "tenantId": "0192b310-7f1d-7abc-8d2c-123456789013",
    "branchId": "0192b310-7f23-7abc-8d2c-123456789012",
    "operatorUserId": "0192b310-7f11-7abc-8d2c-123456789015",
    "status": "ACTIVE",
    "admissionPhase": "OPEN",
    "openingFloatMinor": 1000000,
    "currency": "VND",
    "businessDate": "2026-10-13",
    "openedAt": "2026-10-13T06:00:00Z",
    "branchCheckoutBlocked": false
  },
  "meta": {
    "requestId": "req_0192b310-7f1d-7abc-8d2c-123456789005",
    "correlationId": "0192b310-7f1d-7abc-8d2c-123456789002"
  }
}
```

*Trường hợp ca đang ở trạng thái `PENDING_REVIEW`:* Trả về `status: "PENDING_REVIEW"`, `branchCheckoutBlocked: true` kèm `reviewReason` và chi tiết lần đếm gần nhất.
- **Mã lỗi khả dĩ:** `404 Not Found` (`no_active_shift`: chi nhánh hiện không có ca nào đang mở).

---

#### `POST /api/v1/shifts/{shiftId}/close`
Đóng ca làm việc và kích hoạt rào chắn tiếp nhận giao dịch (`close fence`).
- Trong P0: Hỗ trợ đóng nhanh không cần đếm tiền (`countedCashMinor = null`).
- Trong P1: Hỗ trợ nộp số tiền mặt kiểm đếm thực tế (`countedCashMinor`), hệ thống tự động so sánh với số tiền kỳ vọng và chuyển sang `PENDING_REVIEW` nếu vượt ngưỡng chênh lệch.

- **Thẩm quyền:** Quyền `shift:close` (Người mở ca hoặc `MANAGER`/`OWNER` đóng thay).
- **Tiêu đề Yêu cầu (Request Headers):** `Authorization`, `X-Active-Branch-Id`, `Idempotency-Key`, `X-Correlation-Id`, `Content-Type`.
- **Tham số Đường dẫn (Path Parameters):** `shiftId` (UUID, Bắt buộc).
- **Dữ liệu Gửi lên (Request Body — Khi có kiểm đếm P1):**

```json
{
  "closedForUserId": null,
  "reason": null,
  "countedCashMinor": 3077500,
  "countNote": "Đã đếm cùng quản lý ca sáng"
}
```

- **Phản hồi Thành công ngay lập tức (`200 OK` — Khi hoàn tất không còn giao dịch pending):**

```json
{
  "data": {
    "closeOperationId": "0192b310-7f1d-7abc-8d2c-123456789020",
    "closeStatus": "COMPLETED",
    "shift": {
      "shiftId": "0192b310-7f1d-7abc-8d2c-123456789012",
      "status": "CLOSED",
      "admissionPhase": "FROZEN",
      "version": 4,
      "closedAt": "2026-10-13T14:05:00Z"
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
      "calculatedAt": "2026-10-13T14:05:00Z"
    }
  },
  "meta": {
    "requestId": "req_0192b310-7f1d-7abc-8d2c-123456789006",
    "correlationId": "0192b310-7f1d-7abc-8d2c-123456789002"
  }
}
```

- **Phản hồi Đang xử lý Drain (`202 Accepted` — Khi còn giao dịch trước rào chắn đang xử lý):**

```json
{
  "data": {
    "closeOperationId": "0192b310-7f1d-7abc-8d2c-123456789020",
    "closeStatus": "PROCESSING",
    "shift": {
      "shiftId": "0192b310-7f1d-7abc-8d2c-123456789012",
      "status": "ACTIVE",
      "admissionPhase": "CLOSING",
      "version": 3,
      "closeFenceSeq": 42,
      "closeFenceAt": "2026-10-13T14:04:59Z"
    },
    "pollAfterMs": 500
  },
  "meta": {
    "requestId": "req_0192b310-7f1d-7abc-8d2c-123456789007",
    "correlationId": "0192b310-7f1d-7abc-8d2c-123456789002"
  }
}
```

*Quy tắc Drain Timeout:* Nếu sau thời gian chờ drain (`drainTimeoutMs`, thường 5000ms) mà vẫn còn giao dịch chưa hoàn tất, hệ thống chuyển ca sang `PENDING_REVIEW` với `reviewReason = "UNRESOLVED_PRE_FENCE_ADMISSION"` để bảo vệ tính toàn vẹn tài chính.

---

#### `POST /api/v1/shifts/{shiftId}/cancel`
Hủy ca làm việc mở nhầm hoặc bị bỏ quên. Thao tác này chỉ hợp lệ khi ca **chưa từng phát sinh bất kỳ giao dịch tiền mặt nào** và số tiền hoàn lại `openingFloatReturnedMinor` khớp chính xác với tiền đầu ca ban đầu.

- **Thẩm quyền:** Quyền `shift:cancel` (`MANAGER`, `OWNER`).
- **Tiêu đề Yêu cầu (Request Headers):** `Authorization`, `Idempotency-Key`, `X-Correlation-Id`, `Content-Type`.
- **Tham số Đường dẫn (Path Parameters):** `shiftId` (UUID, Bắt buộc).
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "reason": "Mở nhầm ca trên thiết bị POS dự phòng, đã hoàn trả lại đủ 1.000.000 VND vào két",
  "openingFloatReturnedMinor": 1000000
}
```

- **Phản hồi Thành công (`200 OK`):** Trả về `ShiftResponse` với `status: "CANCELLED"` và `admissionPhase: "FROZEN"`.
- **Mã lỗi khả dĩ:**
  - `409 Conflict` (`shift_has_transactions`): Ca đã phát sinh giao dịch tiền mặt, không được phép hủy (bắt buộc phải đóng ca).
  - `409 Conflict` (`opening_float_not_returned`): Số tiền hoàn trả không khớp với tiền đầu ca.

---

#### `POST /api/v1/shifts/{shiftId}/cash-counts/{countAttemptId}/approve`
Quản lý hoặc Chủ chuỗi phê duyệt kết quả kiểm đếm chênh lệch tiền mặt của ca đang chờ duyệt (`PENDING_REVIEW -> CLOSED`).

- **Thẩm quyền:** Quyền `shift:review` (`MANAGER`, `OWNER`).
- **Tiêu đề Yêu cầu (Request Headers):** `Authorization`, `If-Match: "3"` (phiên bản `review_version`), `Idempotency-Key`, `X-Correlation-Id`, `Content-Type`.
- **Tham số Đường dẫn (Path Parameters):**
  - `shiftId` (UUID, Bắt buộc)
  - `countAttemptId` (UUID, Bắt buộc)
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "decisionNote": "Chấp thuận mức chênh lệch thừa 5.000 VND do khách không lấy tiền thừa",
  "expectedReviewVersion": 3
}
```

- **Phản hồi Thành công (`200 OK`):** Trả về `ShiftResponse` với `status: "CLOSED"` và mở khóa chi nhánh cho ca tiếp theo.
- **Mã lỗi khả dĩ:** `403 Forbidden` (thu ngân không được tự duyệt ca của mình), `409 Conflict` (`version_conflict`).

---

#### `POST /api/v1/shifts/{shiftId}/cash-counts/{countAttemptId}/request-recount`
Quản lý từ chối kết quả đếm hiện tại và yêu cầu nhân viên thu ngân thực hiện kiểm đếm lại tiền mặt trong két.

- **Thẩm quyền:** Quyền `shift:review` (`MANAGER`, `OWNER`).
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "reason": "Chênh lệch thiếu 50.000 VND chưa rõ nguyên nhân, yêu cầu thu ngân kiểm đếm lại cẩn thận",
  "expectedReviewVersion": 2
}
```

- **Phản hồi Thành công (`200 OK`):** Ca tiếp tục giữ trạng thái `PENDING_REVIEW` và cho phép thu ngân gọi endpoint `POST /cash-counts` để nộp lần đếm tiếp theo (`attempt_number` tăng lên 2).

---

### 4.2 Các API Nội Bộ Phục Vụ Saga & Payment (Internal Service APIs)

#### `POST /internal/v1/shifts/{shiftId}/transaction-admissions`
Được gọi bởi AWS Step Functions Checkout Saga hoặc Payment Service để xin cấp quyền tiếp nhận giao dịch vào ca làm việc trước khi xử lý thanh toán.

- **Thẩm quyền:** Workload Identity của Order Service, Payment Service hoặc Step Functions Saga.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <workload-token>` (Bắt buộc)
  - `Idempotency-Key: <unique-key>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Bắt buộc)
  - `Content-Type: application/json; charset=utf-8` (Bắt buộc)
- **Tham số Đường dẫn (Path Parameters):** `shiftId` (UUID, Bắt buộc).
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "tenantId": "0192b310-7f1d-7abc-8d2c-123456789013",
  "branchId": "0192b310-7f23-7abc-8d2c-123456789012",
  "sourceType": "ORDER_CHECKOUT",
  "sourceId": "0192b310-7f30-7abc-8d2c-123456789030",
  "transactionKind": "CASH_SALE",
  "amountMinor": 68000,
  "currency": "VND",
  "correlationId": "0192b310-7f1d-7abc-8d2c-123456789031"
}
```

*Quy tắc tiếp nhận (Admission Rules):*
- Nếu ca đang ở pha `OPEN`: Cấp admission thành công, tăng số thứ tự đơn điệu `admissionSeq = 42`, lưu vào bảng `shift_transaction_admissions`.
- Nếu ca đã ở pha `CLOSING` hoặc `FROZEN`: Lập tức từ chối với mã lỗi `409 transaction_after_close_fence`.

- **Phản hồi Thành công (`201 Created`):**

```json
{
  "data": {
    "admissionId": "0192b310-7f32-7abc-8d2c-123456789032",
    "shiftId": "0192b310-7f1d-7abc-8d2c-123456789012",
    "branchId": "0192b310-7f23-7abc-8d2c-123456789012",
    "admissionSeq": 42,
    "admittedAt": "2026-10-13T10:04:59Z",
    "status": "ADMITTED",
    "cashAccountability": true,
    "shiftVersion": 3
  },
  "meta": {
    "requestId": "req_0192b310-7f32-7abc-8d2c-123456789001",
    "correlationId": "0192b310-7f1d-7abc-8d2c-123456789031"
  }
}
```

---

#### `POST /internal/v1/shifts/{shiftId}/transaction-admissions/{admissionId}/complete`
Báo cáo hoàn tất hoặc hủy bỏ một giao dịch đã được cấp quyền tiếp nhận.

- **Thẩm quyền:** Workload Identity của Order/Payment Service.
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "completionStatus": "COMPLETED",
  "sourceId": "0192b310-7f30-7abc-8d2c-123456789030",
  "correlationId": "0192b310-7f1d-7abc-8d2c-123456789031"
}
```

*Giá trị hợp lệ của `completionStatus`:* `COMPLETED`, `FAILED`, `CANCELLED`.

- **Phản hồi Thành công (`200 OK`):** Trả về `AdmissionResponse` với trạng thái kết thúc tương ứng.

---

#### `POST /internal/v1/shifts/{shiftId}/cash-facts`
Được Payment Service gọi để ghi nhận một sự kiện tiền mặt thực tế đã quyết toán thành công (`CASH_COLLECTION` hoặc `CASH_REFUND`).

- **Thẩm quyền:** Workload Identity của Payment Service.
- **Tiêu đề Yêu cầu (Request Headers):** `Authorization`, `Idempotency-Key`, `X-Correlation-Id`, `Content-Type`.
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "tenantId": "0192b310-7f1d-7abc-8d2c-123456789013",
  "branchId": "0192b310-7f23-7abc-8d2c-123456789012",
  "shiftId": "0192b310-7f1d-7abc-8d2c-123456789012",
  "admissionId": "0192b310-7f32-7abc-8d2c-123456789032",
  "sourceType": "PAYMENT",
  "sourceId": "0192b2d0-7f34-7abc-8d2c-123456789012",
  "factKind": "CASH_COLLECTION",
  "amountMinor": 68000,
  "currency": "VND",
  "factStatus": "FINALIZED",
  "admittedAt": "2026-10-13T10:04:59Z",
  "occurredAt": "2026-10-13T10:05:00Z",
  "correlationId": "0192b310-7f1d-7abc-8d2c-123456789031"
}
```

*Quy tắc ghi nhận sự kiện tiền mặt:*
- Khử trùng lặp theo cặp `(tenant_id, shift_id, source_type, source_id, fact_kind)`.
- Nếu sự kiện có `admissionSeq <= close_fence_seq`: Được tính vào tổng tiền mặt thu của ca.
- Nếu sự kiện đến sau khi ca đã `CLOSED`: Không làm thay đổi số liệu ca đã đóng, tự động tạo hồ sơ đối soát `reconciliation_cases` với `case_type = "LATE_FACT"`.

- **Phản hồi Thành công (`201 Created` / `200 OK` nếu trùng lặp):** Trả về `CashFactResponse`.

---

#### `GET /internal/v1/shifts/{shiftId}/cash-summary`
Tính toán và trả về bảng tóm tắt tiền mặt chính xác của ca làm việc dựa trên các sự kiện tiền mặt đã được ghi nhận.

- **Thẩm quyền:** Workload Identity của Payment, Step Functions hoặc Reporting Service.
- **Phản hồi Thành công (`200 OK`):**

```json
{
  "data": {
    "shiftId": "0192b310-7f1d-7abc-8d2c-123456789012",
    "openingFloatMinor": 1000000,
    "cashCollectedMinor": 2100000,
    "cashRefundedMinor": 22500,
    "expectedCashMinor": 3077500,
    "sourceWatermark": "admission-seq-42",
    "complete": true,
    "unresolvedAdmissionCount": 0,
    "unresolvedCaseCount": 0,
    "calculatedAt": "2026-10-13T14:05:00Z"
  },
  "meta": {
    "requestId": "req_0192b310-7f1d-7abc-8d2c-123456789010",
    "correlationId": "0192b310-7f1d-7abc-8d2c-123456789002"
  }
}
```

---

#### `POST /internal/v1/shifts/reconciliation-cases/{caseId}/resolve`
Giải quyết hồ sơ đối soát ngoại lệ (ví dụ: tiền mặt về muộn sau khi ca đã đóng hoặc chênh lệch tiền kiểm đếm cần điều chỉnh kế toán).

- **Thẩm quyền:** Workload Identity của bộ phận Vận hành (Operations / Admin).
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "resolution": "ACCEPT_AS_LATE_FACT",
  "note": "Xác nhận đối chiếu tiền mặt nộp bổ sung vào sổ quỹ cuối ngày, không điều chỉnh ca đã đóng",
  "externalReference": "OPS-REC-20261013-01"
}
```

*Mã giải quyết hợp lệ:* `ACCEPT_AS_LATE_FACT`, `REJECT_INVALID_FACT`, `DUPLICATE_CONFIRMED`, `NO_ACTION_REQUIRED`. Thao tác giải quyết ghi nhận vào hồ sơ và phát sự kiện `ShiftCashFactReconciled.v1`, tuyệt đối không mở lại ca `CLOSED`.

---

## 5. Hợp Đồng Máy Trạng Thái (State Machine Contract)

### 5.1 Vòng Đời Trạng Thái Ca Làm Việc (Shift Lifecycle)

```text
                  [ POST /shifts (Mở ca) ]
                             │
                             ▼
                        ┌─────────┐
                        │ ACTIVE  │
                        └────┬────┘
                             │
        ┌────────────────────┼────────────────────┐
        │                    │                    │
        │ (Đóng ca P0 /      │ (Đóng ca P1 vượt   │ (Hủy ca không có
        │  Đếm P1 khớp tiền) │  ngưỡng lệch hoặc  │  giao dịch và hoàn
        │                    │  drain timeout)    │  đủ tiền đầu ca)
        ▼                    ▼                    ▼
  ┌───────────┐      ┌────────────────┐     ┌───────────┐
  │  CLOSED   │      │ PENDING_REVIEW │     │ CANCELLED │
  └───────────┘      └───────┬────────┘     └───────────┘
   (Terminal)                │               (Terminal)
                             │
                ┌────────────┴────────────┐
                │                         │
                │ (Quản lý duyệt:         │ (Quản lý yêu cầu
                │  POST /approve)         │  đếm lại: POST /recount)
                ▼                         ▼
          ┌───────────┐             ┌────────────────┐
          │  CLOSED   │             │ PENDING_REVIEW │ (Giữ nguyên trạng
          └───────────┘             └────────────────┘  thái, tăng attempt)
           (Terminal)
```

### 5.2 Pha Tiếp Nhận Giao Dịch Nội Bộ (Internal Admission Phase)

Song song với trạng thái công khai, Shift Service duy trì trường `admission_phase` nội bộ để điều phối rào chắn đóng ca:

```text
  ┌──────┐    Kích hoạt Close Fence    ┌─────────┐   Drain hoàn tất    ┌────────┐
  │ OPEN │ ──────────────────────────► │ CLOSING │ ──────────────────► │ FROZEN │
  └──────┘                             └─────────┘                     └────────┘
(Cấp admission                    (Chặn admission mới,               (Đóng băng vĩnh viễn,
 bình thường)                      chờ hoàn tất giao dịch cũ)         chỉ nhận đối soát)
```

- `OPEN`: Ca đang mở, nhận yêu cầu `POST /transaction-admissions` bình thường.
- `CLOSING`: Ca đang trong quá trình đóng. Rào chắn `close_fence_seq` đã được thiết lập. Từ chối mọi admission mới; chỉ chờ các admission có `seq <= fence` hoàn tất.
- `FROZEN`: Ca đã đóng (`CLOSED`) hoặc đã hủy (`CANCELLED`). Đóng băng vĩnh viễn. Mọi sự kiện tiền mặt gửi đến muộn đều được chuyển thành hồ sơ đối soát ngoại lệ.

---

## 6. Hợp Đồng Sự Kiện Tích Hợp (Integration Event Contracts)

### 6.1 Cấu Trúc Phong Bì Sự Kiện Chuẩn (Event Envelope)

```json
{
  "eventId": "0192b310-7f40-7abc-8d2c-123456789040",
  "eventType": "ShiftClosed.v1",
  "eventVersion": 1,
  "occurredAt": "2026-10-13T14:05:00Z",
  "tenantId": "0192b310-7f1d-7abc-8d2c-123456789013",
  "branchId": "0192b310-7f23-7abc-8d2c-123456789012",
  "aggregateType": "SHIFT",
  "aggregateId": "0192b310-7f1d-7abc-8d2c-123456789012",
  "aggregateVersion": 4,
  "correlationId": "0192b310-7f1d-7abc-8d2c-123456789031",
  "causationId": "0192b310-7f32-7abc-8d2c-123456789032",
  "producer": "shift-service",
  "payload": {}
}
```

---

### 6.2 Danh Mục & Payload Chi Tiết Các Sự Kiện

#### 1. `ShiftOpened.v1`
Phát ra khi ca làm việc mới được mở thành công tại chi nhánh.

```json
{
  "shiftId": "0192b310-7f1d-7abc-8d2c-123456789012",
  "openedByUserId": "0192b310-7f11-7abc-8d2c-123456789015",
  "operatorUserId": "0192b310-7f11-7abc-8d2c-123456789015",
  "status": "ACTIVE",
  "openingFloatMinor": 1000000,
  "currency": "VND",
  "businessDate": "2026-10-13",
  "branchTimezone": "Asia/Ho_Chi_Minh",
  "openedAt": "2026-10-13T06:00:00Z",
  "policySnapshot": {
    "cashCountRequired": true,
    "discrepancyThresholdMinor": 0,
    "drainTimeoutMs": 5000
  }
}
```

---

#### 2. `ShiftCloseStarted.v1`
Phát ra khi rào chắn đóng ca bắt đầu được kích hoạt và ca chuyển sang pha `CLOSING`.

```json
{
  "shiftId": "0192b310-7f1d-7abc-8d2c-123456789012",
  "closeOperationId": "0192b310-7f1d-7abc-8d2c-123456789020",
  "closeFenceSeq": 42,
  "closeFenceAt": "2026-10-13T14:04:59Z",
  "initiatedByUserId": "0192b310-7f11-7abc-8d2c-123456789015"
}
```

---

#### 3. `ShiftCountSubmitted.v1`
Phát ra khi kết quả kiểm đếm tiền mặt được nộp vào hệ thống.

```json
{
  "shiftId": "0192b310-7f1d-7abc-8d2c-123456789012",
  "countAttemptId": "0192b310-7f41-7abc-8d2c-123456789012",
  "attemptNumber": 1,
  "countedCashMinor": 3077500,
  "expectedCashMinor": 3077500,
  "discrepancyMinor": 0,
  "currency": "VND",
  "countedByUserId": "0192b310-7f11-7abc-8d2c-123456789015",
  "submittedAt": "2026-10-13T14:05:00Z"
}
```

---

#### 4. `ShiftReviewRequired.v1`
Phát ra khi ca làm việc bị đưa vào trạng thái chờ duyệt (`PENDING_REVIEW`) do lệch tiền vượt ngưỡng hoặc drain timeout. Kích hoạt thông báo tới Quản lý và khóa thao tác bán hàng tại chi nhánh.

```json
{
  "shiftId": "0192b310-7f1d-7abc-8d2c-123456789012",
  "reviewReason": "CASH_DISCREPANCY_EXCEEDED_THRESHOLD",
  "discrepancyMinor": -50000,
  "thresholdMinor": 0,
  "currency": "VND",
  "latestCountAttemptId": "0192b310-7f41-7abc-8d2c-123456789012",
  "detectedAt": "2026-10-13T14:05:05Z"
}
```

---

#### 5. `ShiftRecountRequested.v1`
Phát ra khi quản lý yêu cầu thu ngân kiểm đếm lại tiền mặt.

```json
{
  "shiftId": "0192b310-7f1d-7abc-8d2c-123456789012",
  "previousCountAttemptId": "0192b310-7f41-7abc-8d2c-123456789012",
  "requestedByUserId": "0192b310-7f12-7abc-8d2c-123456789012",
  "reason": "Chênh lệch thiếu 50.000 VND chưa rõ nguyên nhân",
  "reviewVersion": 2
}
```

---

#### 6. `ShiftClosed.v1`
Phát ra khi ca làm việc chính thức chuyển sang trạng thái đóng kết thúc (`CLOSED`). Sự kiện này được Reporting Service và Audit Service tiêu thụ để tổng kết doanh thu và chốt sổ quỹ tiền mặt.

```json
{
  "shiftId": "0192b310-7f1d-7abc-8d2c-123456789012",
  "status": "CLOSED",
  "closeOperationId": "0192b310-7f1d-7abc-8d2c-123456789020",
  "closedByUserId": "0192b310-7f11-7abc-8d2c-123456789015",
  "operatorUserId": "0192b310-7f11-7abc-8d2c-123456789015",
  "closeFenceSeq": 42,
  "closedAt": "2026-10-13T14:05:00Z",
  "cashSummary": {
    "openingFloatMinor": 1000000,
    "cashCollectedMinor": 2100000,
    "cashRefundedMinor": 22500,
    "expectedCashMinor": 3077500,
    "countedCashMinor": 3077500,
    "discrepancyMinor": 0,
    "currency": "VND",
    "sourceWatermark": "admission-seq-42",
    "calculatedAt": "2026-10-13T14:05:00Z"
  },
  "countAttemptId": "0192b310-7f41-7abc-8d2c-123456789012",
  "reviewDecisionId": null
}
```

---

#### 7. `ShiftCancelled.v1`
Phát ra khi một ca mở nhầm bị hủy bỏ thành công.

```json
{
  "shiftId": "0192b310-7f1d-7abc-8d2c-123456789012",
  "cancelledByUserId": "0192b310-7f12-7abc-8d2c-123456789012",
  "cancellationReason": "Mở nhầm ca trên thiết bị POS dự phòng",
  "openingFloatReturnedMinor": 1000000,
  "cancelledAt": "2026-10-13T06:15:00Z"
}
```

---

#### 8. `ShiftReconciliationCaseOpened.v1` & `ShiftCashFactReconciled.v1`
Phát ra khi phát hiện các ngoại lệ tiền mặt hoặc khi hồ sơ đối soát ngoại lệ được xử lý hoàn tất bởi nhân viên vận hành.

---

## 7. Hợp Đồng Cơ Sở Dữ Liệu & Flyway Migrations (PostgreSQL DDL)

Cơ sở dữ liệu: `pos_shift` độc lập. Dưới đây là mã DDL đầy đủ cho 6 file migration Flyway:

### `V1__create_shift_core.sql`
Tạo bảng ca làm việc chính `shifts` và bảng chính sách ca `shift_policies`.

```sql
-- V1__create_shift_core.sql
-- Bounded Context: Shift Service Core

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Bảng 1: shift_policies - Chính sách quản lý ca toàn tenant hoặc ghi đè theo chi nhánh
CREATE TABLE shift_policies (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    branch_id UUID NULL,
    cash_count_required BOOLEAN NOT NULL DEFAULT FALSE,
    discrepancy_threshold_minor BIGINT NOT NULL DEFAULT 0,
    drain_timeout_ms INTEGER NOT NULL DEFAULT 5000,
    effective_from TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    effective_to TIMESTAMPTZ NULL,
    updated_by_user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_policy_threshold CHECK (discrepancy_threshold_minor >= 0),
    CONSTRAINT ck_policy_drain_timeout CHECK (drain_timeout_ms BETWEEN 1000 AND 30000)
);

-- Bảng 2: shifts - Quản lý vòng đời ca làm việc tại chi nhánh
CREATE TABLE shifts (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    admission_phase TEXT NOT NULL DEFAULT 'OPEN',
    opened_by_user_id UUID NOT NULL,
    operator_user_id UUID NOT NULL,
    opening_float_minor BIGINT NOT NULL,
    currency TEXT NOT NULL DEFAULT 'VND',
    business_date DATE NOT NULL,
    branch_timezone TEXT NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    opened_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    close_operation_id UUID NULL,
    close_fence_seq BIGINT NULL,
    close_fence_at TIMESTAMPTZ NULL,
    close_fence_version BIGINT NULL,
    closed_by_user_id UUID NULL,
    closed_at TIMESTAMPTZ NULL,
    cancelled_by_user_id UUID NULL,
    cancelled_at TIMESTAMPTZ NULL,
    cancellation_reason TEXT NULL,
    opening_float_returned_minor BIGINT NULL,
    review_reason TEXT NULL,
    review_version BIGINT NOT NULL DEFAULT 1,
    latest_count_attempt_id UUID NULL,
    policy_snapshot JSONB NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_shift_status CHECK (status IN ('ACTIVE', 'PENDING_REVIEW', 'CLOSED', 'CANCELLED')),
    CONSTRAINT ck_shift_admission_phase CHECK (admission_phase IN ('OPEN', 'CLOSING', 'FROZEN')),
    CONSTRAINT ck_shift_opening_float CHECK (opening_float_minor >= 0),
    CONSTRAINT ck_shift_currency CHECK (currency = 'VND'),
    CONSTRAINT ck_shift_float_returned CHECK (opening_float_returned_minor IS NULL OR opening_float_returned_minor >= 0)
);
```

---

### `V2__create_admissions_and_cash_facts.sql`
Tạo bảng tiếp nhận giao dịch (`shift_transaction_admissions`) và bảng sự kiện tiền mặt (`cash_facts`).

```sql
-- V2__create_admissions_and_cash_facts.sql

-- Bảng 3: shift_transaction_admissions - Quản lý quyền tiếp nhận giao dịch trước rào chắn
CREATE TABLE shift_transaction_admissions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    shift_id UUID NOT NULL REFERENCES shifts(id) ON DELETE RESTRICT,
    source_type TEXT NOT NULL,
    source_id UUID NOT NULL,
    transaction_kind TEXT NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency TEXT NOT NULL DEFAULT 'VND',
    admission_seq BIGINT NOT NULL,
    admitted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status TEXT NOT NULL DEFAULT 'ADMITTED',
    cash_accountability BOOLEAN NOT NULL DEFAULT TRUE,
    completed_at TIMESTAMPTZ NULL,
    completion_source_id UUID NULL,
    correlation_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_admission_status CHECK (status IN ('ADMITTED', 'COMPLETED', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_admission_amount CHECK (amount_minor >= 0),
    CONSTRAINT ck_admission_kind CHECK (transaction_kind IN ('CASH_SALE', 'CASH_REFUND', 'NON_CASH_CHECKOUT'))
);

-- Bảng 4: cash_facts - Lưu vết các sự kiện tiền mặt thực tế đã quyết toán
CREATE TABLE cash_facts (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    shift_id UUID NOT NULL REFERENCES shifts(id) ON DELETE RESTRICT,
    admission_id UUID NOT NULL REFERENCES shift_transaction_admissions(id) ON DELETE RESTRICT,
    source_type TEXT NOT NULL,
    source_id UUID NOT NULL,
    fact_kind TEXT NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency TEXT NOT NULL DEFAULT 'VND',
    fact_status TEXT NOT NULL DEFAULT 'FINALIZED',
    admission_seq BIGINT NOT NULL,
    admitted_at TIMESTAMPTZ NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    correlation_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_cash_fact_kind CHECK (fact_kind IN ('CASH_COLLECTION', 'CASH_REFUND')),
    CONSTRAINT ck_cash_fact_amount CHECK (amount_minor >= 0),
    CONSTRAINT ck_cash_fact_currency CHECK (currency = 'VND'),
    CONSTRAINT ck_cash_fact_status CHECK (fact_status IN ('FINALIZED', 'REJECTED'))
);
```

---

### `V3__create_count_and_reconciliation.sql`
Tạo bảng các lượt kiểm đếm tiền mặt (`cash_count_attempts`) và bảng hồ sơ đối soát ngoại lệ (`reconciliation_cases`).

```sql
-- V3__create_count_and_reconciliation.sql

-- Bảng 5: cash_count_attempts - Các lượt kiểm đếm tiền mặt thực tế (Bất biến)
CREATE TABLE cash_count_attempts (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    shift_id UUID NOT NULL REFERENCES shifts(id) ON DELETE RESTRICT,
    attempt_number INTEGER NOT NULL,
    counted_cash_minor BIGINT NOT NULL,
    opening_float_minor BIGINT NOT NULL,
    cash_collected_minor BIGINT NOT NULL,
    cash_refunded_minor BIGINT NOT NULL,
    expected_cash_minor BIGINT NOT NULL,
    discrepancy_minor BIGINT NOT NULL,
    threshold_minor BIGINT NOT NULL,
    fence_version BIGINT NOT NULL,
    source_watermark TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'SUBMITTED',
    note TEXT NULL,
    counted_by_user_id UUID NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_by_user_id UUID NULL,
    reviewed_at TIMESTAMPTZ NULL,
    decision_note TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_count_attempt_counted CHECK (counted_cash_minor >= 0),
    CONSTRAINT ck_count_attempt_discrepancy CHECK (discrepancy_minor = counted_cash_minor - expected_cash_minor),
    CONSTRAINT ck_count_attempt_status CHECK (status IN ('SUBMITTED', 'RECOUNT_REQUIRED', 'APPROVED'))
);

-- Bảng 6: reconciliation_cases - Hồ sơ đối soát ngoại lệ vận hành
CREATE TABLE reconciliation_cases (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    shift_id UUID NOT NULL REFERENCES shifts(id) ON DELETE RESTRICT,
    case_type TEXT NOT NULL,
    source_type TEXT NULL,
    source_id UUID NULL,
    amount_minor BIGINT NULL,
    currency TEXT NULL DEFAULT 'VND',
    reason TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'OPEN',
    resolution TEXT NULL,
    resolution_note TEXT NULL,
    resolved_by_user_id UUID NULL,
    resolved_at TIMESTAMPTZ NULL,
    correlation_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_recon_case_type CHECK (case_type IN ('LATE_FACT', 'UNKNOWN_FACT', 'MISMATCH', 'UNRESOLVED_ADMISSION')),
    CONSTRAINT ck_recon_case_status CHECK (status IN ('OPEN', 'RESOLVED', 'REJECTED'))
);
```

---

### `V4__create_outbox_idempotency_audit.sql`
Tạo bảng Transactional Outbox, Idempotency và Audit References.

```sql
-- V4__create_outbox_idempotency_audit.sql

-- Bảng 7: outbox_messages
CREATE TABLE outbox_messages (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    aggregate_type TEXT NOT NULL DEFAULT 'SHIFT',
    aggregate_id UUID NOT NULL,
    aggregate_version BIGINT NOT NULL DEFAULT 1,
    event_type TEXT NOT NULL,
    event_version INTEGER NOT NULL DEFAULT 1,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    publish_status TEXT NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NULL,
    last_error_safe TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_outbox_status CHECK (publish_status IN ('PENDING', 'PUBLISHED', 'DLQ'))
);

-- Bảng 8: idempotency_records
CREATE TABLE idempotency_records (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    actor_or_workload_id TEXT NOT NULL,
    operation TEXT NOT NULL,
    idempotency_key TEXT NOT NULL,
    request_hash TEXT NOT NULL,
    response_status INTEGER NOT NULL,
    response_body JSONB NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Bảng 9: audit_references
CREATE TABLE audit_references (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    shift_id UUID NOT NULL,
    action TEXT NOT NULL,
    actor_user_id UUID NOT NULL,
    operator_user_id UUID NOT NULL,
    reason TEXT NULL,
    before_state JSONB NULL,
    after_state JSONB NULL,
    correlation_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

---

### `V5__add_shift_unique_constraints.sql`
Thiết lập các ràng buộc duy nhất (Unique Constraints / Partial Unique Indexes).

```sql
-- V5__add_shift_unique_constraints.sql

-- 1. Bất biến duy nhất một ca ACTIVE hoặc PENDING_REVIEW tại mỗi chi nhánh
CREATE UNIQUE INDEX uq_shifts_open_slot
    ON shifts (tenant_id, branch_id)
    WHERE status IN ('ACTIVE', 'PENDING_REVIEW');

-- 2. Số thứ tự tiếp nhận giao dịch duy nhất trong từng ca
ALTER TABLE shift_transaction_admissions
    ADD CONSTRAINT uq_shift_admission_seq UNIQUE (shift_id, admission_seq);

-- 3. Khử trùng lặp tiếp nhận giao dịch từ cùng một nguồn
ALTER TABLE shift_transaction_admissions
    ADD CONSTRAINT uq_shift_source_admission UNIQUE (tenant_id, shift_id, source_type, source_id);

-- 4. Khử trùng lặp sự kiện tiền mặt từ producer
ALTER TABLE cash_facts
    ADD CONSTRAINT uq_cash_fact_source UNIQUE (tenant_id, shift_id, source_type, source_id, fact_kind);

-- 5. Số thứ tự lượt đếm tiền mặt duy nhất trong từng ca
ALTER TABLE cash_count_attempts
    ADD CONSTRAINT uq_shift_count_attempt_number UNIQUE (shift_id, attempt_number);

-- 6. Khóa Idempotency duy nhất
ALTER TABLE idempotency_records
    ADD CONSTRAINT uq_shift_idempotency UNIQUE (tenant_id, actor_or_workload_id, operation, idempotency_key);
```

---

### `V6__add_shift_operational_indexes.sql`
Thêm các chỉ mục phục vụ hiệu năng vận hành và chú thích từng cột (`COMMENT ON COLUMN`).

```sql
-- V6__add_shift_operational_indexes.sql

-- 1. Chỉ mục phân trang con trỏ tra cứu lịch sử ca
CREATE INDEX idx_shifts_history_cursor
    ON shifts (tenant_id, branch_id, business_date DESC, created_at DESC, id DESC);

-- 2. Chỉ mục phục vụ tiến trình drain các admission đang xử lý
CREATE INDEX idx_shift_admissions_drain
    ON shift_transaction_admissions (tenant_id, shift_id, status, admission_seq)
    WHERE status = 'ADMITTED';

-- 3. Chỉ mục tổng hợp báo cáo sự kiện tiền mặt
CREATE INDEX idx_cash_facts_summary
    ON cash_facts (tenant_id, shift_id, admission_seq, fact_kind);

-- 4. Chỉ mục tiến trình polling xuất bản outbox
CREATE INDEX idx_outbox_shift_publish
    ON outbox_messages (publish_status, next_attempt_at, created_at)
    WHERE publish_status = 'PENDING';

-- 5. Chú thích bảng và cột
COMMENT ON TABLE shifts IS 'Bảng quản lý ca làm việc và rào chắn tiếp nhận giao dịch tại chi nhánh.';
COMMENT ON COLUMN shifts.opening_float_minor IS 'Tiền mặt đầu ca (tiền lẻ thối ban đầu), tính theo số nguyên VND.';
COMMENT ON COLUMN shifts.close_fence_seq IS 'Số thứ tự rào chắn đóng ca; chỉ các admission có số thứ tự nhỏ hơn hoặc bằng giá trị này mới được tính vào ca.';
COMMENT ON TABLE shift_transaction_admissions IS 'Quyền tiếp nhận giao dịch đồng bộ trước khi thực hiện checkout/thanh toán.';
COMMENT ON TABLE cash_facts IS 'Sự kiện tiền mặt thực tế đã được finalized thành công từ Payment Service.';
```

---

## 8. Ma Trận Kiểm Thử Hợp Đồng Bắt Buộc (Contract Test Matrix)

| Mã kiểm thử | Kịch bản kiểm thử | Dữ liệu đầu vào | Kết quả hợp đồng kỳ vọng |
|:---:|---|---|---|
| `TC-SFT-01` | Mở ca thành công | `openingFloatMinor = 1000000`, chi nhánh chưa có ca active | Trả về `201 Created`, `status = ACTIVE`, `admissionPhase = OPEN`, phát sự kiện `ShiftOpened.v1`. |
| `TC-SFT-02` | Mở ca khi chi nhánh đã có ca active | Mở thêm ca mới trong khi ca hiện tại đang `ACTIVE` | Trả về `409 Conflict`, mã lỗi `shift_already_open`, từ chối mở ca mới. |
| `TC-SFT-03` | Tiếp nhận trước rào chắn | Gửi `CreateAdmissionRequest` khi ca đang ở pha `OPEN` | Trả về `201 Created`, cấp số thứ tự `admissionSeq = 42`, trạng thái `ADMITTED`. |
| `TC-SFT-04` | Tiếp nhận sau rào chắn | Gửi `CreateAdmissionRequest` sau khi đã kích hoạt rào chắn đóng ca | Trả về `409 Conflict`, mã lỗi `transaction_after_close_fence`, từ chối nhận đơn. |
| `TC-SFT-05` | Đóng ca P0 thành công ngay lập tức | Đóng ca khi toàn bộ admission trước rào chắn đã `COMPLETED` | Trả về `200 OK`, `closeStatus = COMPLETED`, `status = CLOSED`, `admissionPhase = FROZEN`, phát `ShiftClosed.v1`. |
| `TC-SFT-06` | Đóng ca chờ drain giao dịch | Đóng ca khi còn 1 admission đang ở trạng thái `ADMITTED` | Trả về `202 Accepted`, `closeStatus = PROCESSING`, `admissionPhase = CLOSING`, client polling chờ kết quả. |
| `TC-SFT-07` | Drain timeout chuyển sang Review | Quá 5000ms mà admission trước rào chắn chưa kết thúc | Ca tự động chuyển sang `PENDING_REVIEW`, khóa bán hàng chi nhánh, phát `ShiftReviewRequired.v1`. |
| `TC-SFT-08` | Đếm tiền P1 lệch tiền | Tiền kỳ vọng 3.077.500 VND, thu ngân đếm được 3.027.500 VND | Chuyển sang `PENDING_REVIEW`, `discrepancyMinor = -50000`, khóa chi nhánh, phát `ShiftReviewRequired.v1`. |
| `TC-SFT-09` | Quản lý phê duyệt chênh lệch | Quản lý gọi endpoint `/approve` với `expectedReviewVersion = 3` | Trả về `200 OK`, ca chuyển sang `CLOSED`, mở khóa chi nhánh cho ca tiếp theo, phát `ShiftClosed.v1`. |
| `TC-SFT-10` | Thu ngân tự duyệt ca của mình | Thu ngân gọi endpoint `/approve` | Trả về `403 Forbidden`, chỉ có vai trò `MANAGER` hoặc `OWNER` mới có quyền phê duyệt. |
| `TC-SFT-11` | Hủy ca mở nhầm thành công | Ca chưa có giao dịch nào, hoàn đủ 1.000.000 VND tiền đầu ca | Trả về `200 OK`, `status = CANCELLED`, không phát `ShiftClosed.v1`, phát `ShiftCancelled.v1`. |
| `TC-SFT-12` | Tiền mặt về muộn sau khi ca đã đóng | Payment gửi `CashFact` của admission cũ sau khi ca đã `CLOSED` | Không sửa ca đã đóng, ghi nhận vào `reconciliation_cases(LATE_FACT)`, phát `ShiftCashFactReconciled.v1`. |
