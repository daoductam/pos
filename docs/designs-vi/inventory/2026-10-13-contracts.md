# Module 6 — Dịch Vụ Kho Nguyên Liệu (Inventory Service): Hợp Đồng API & Dữ Liệu (API & Data Contracts)

**Ngày:** 2026-10-13  
**Trạng thái:** Đã phê duyệt (Approved)  
**Phạm vi:** M — Luồng Service Tiêu chuẩn (Standard Service Flow)  
**Module:** Kho nguyên liệu (Inventory Service)

---

## Nguồn tài liệu và Quyền hạn Hợp đồng

- [Tầm nhìn Inventory (Vision)](./2026-10-13-vision.md)
- [Yêu cầu Kỹ thuật Inventory (Requirements)](./2026-10-13-requirements.md)
- [Kiến trúc Kỹ thuật Inventory (Architecture)](./2026-10-13-architecture.md)
- [Hợp đồng Identity & Access](../identity-access/2026-10-12-contracts.md)
- [Hợp đồng Catalog Service](../catalog/2026-10-12-contracts.md)
- [Hợp đồng Order Service](../order/2026-10-12-contracts.md)
- [Hợp đồng Payment Service](../payment/2026-10-12-contracts.md)
- [Hợp đồng Nền tảng POS](../pos-platform/2026-10-12-contracts.md)

Tài liệu này là đặc tả chuẩn mực (normative contract) cho toàn bộ REST API công khai và nội bộ của Inventory Service, cấu trúc JSON Schema cho các sự kiện bất đồng bộ, và lược đồ dữ liệu cơ sở dữ liệu PostgreSQL. Quá trình triển khai mã nguồn bắt buộc phải sinh/kiểm tra tính hợp lệ của đặc tả OpenAPI 3.1, AsyncAPI/JSON Schema và các file migration Flyway từ các định nghĩa trong tài liệu này trước khi viết code controller hoặc domain.

---

## 1. Các Nguyên Tắc Thiết Kế Hợp Đồng (Contract Principles)

- **Kiến trúc Bounded Context & Quyền Sở hữu:**
  - **API công khai (Public API):** Sử dụng kiến trúc REST + OpenAPI 3.1 dưới tiền tố `/api/v1/inventory`.
  - **API nội bộ liên service (Internal API):** Sử dụng REST + OpenAPI 3.1 dưới tiền tố `/internal/v1/inventory`; bắt buộc xác thực định danh hệ thống (Workload identity / mTLS), tuyệt đối không mở route ra ngoài phía client Angular.
  - Inventory Service sở hữu toàn bộ vòng đời của danh mục nguyên liệu, công thức định lượng (`recipe`), số dư tồn kho chi nhánh (`stock_balances`), sổ cái bất biến (`stock_ledger`), đơn giữ chỗ (`reservations`), phiếu nhập kho (`receipts`), phiên kiểm kê (`count_sessions`) và cảnh báo tồn thấp.
  - Inventory Service **tuyệt đối không** đọc/ghi trực tiếp cơ sở dữ liệu của Catalog, Order hay Payment Service; không tự ý lắng nghe sự kiện `PaymentPaid` để trừ kho (chỉ Step Functions Saga mới có quyền điều phối gọi lệnh `CommitReservation`); và không tự ý hoàn tất đơn hàng.
- **Định danh & Thời gian:**
  - Toàn bộ khóa chính và định danh thực thể (`ingredientId`, `recipeVersionId`, `reservationId`, `ledgerEntryId`, `receiptId`, `countSessionId`, `alertId`, `outboxMessageId`) đều là chuỗi **UUID v7**.
  - Toàn bộ dấu thời gian đều là chuỗi ISO-8601 UTC trong API (`YYYY-MM-DDTHH:mm:ssZ`) và kiểu `TIMESTAMPTZ` trong PostgreSQL. Ngày kinh doanh được tính toán theo múi giờ chi nhánh (`Asia/Ho_Chi_Minh` - ICT).
- **Định lượng & Đơn vị đo lường:**
  - Đơn vị cơ sở chuẩn hóa: `baseUnit ∈ {'g', 'ml', 'unit'}`.
  - Toàn bộ số lượng lưu trữ trong cơ sở dữ liệu sử dụng kiểu `NUMERIC(18,3)` với độ chính xác 3 chữ số thập phân (làm tròn `HALF_UP` ở ứng dụng khi cần thiết). Nghiêm cấm dùng số thực dấu phẩy động (`float`, `double`).
  - Hệ số hao hụt chế biến (`wasteFactor`) có giá trị `>= 1.000` (ví dụ: `1.050` tương ứng hao hụt 5%).
- **Bảo mật Đa người thuê (Multi-tenant) & Phân quyền:**
  - Ngữ cảnh `tenantId` bắt buộc trích xuất từ token danh tính đã xác thực (Cognito JWT hoặc Workload Token). Tuyệt đối không chấp nhận `tenantId` truyền từ request body hoặc query parameter.
  - Header `X-Active-Branch-Id` bắt buộc đối với người dùng chi nhánh (`CASHIER`, `MANAGER`, `KITCHEN`). Nếu đường dẫn URL chứa `branchId`, giá trị này bắt buộc phải khớp với header.
- **Tính Bất biến (Idempotency) & Khóa Lạc quan (Optimistic Locking):**
  - Mọi thao tác ghi có khả năng thử lại bắt buộc phải có header `Idempotency-Key`. Khóa có phạm vi: `(tenantId, actorOrWorkloadId, operation)`.
  - Mọi thao tác cập nhật tài nguyên có phiên bản (`recipe`, `count_session`, `ingredient`) bắt buộc phải truyền header `If-Match` mang ETag hoặc số phiên bản hiện tại.
- **Tính Toàn vẹn Tồn kho & Không Xóa cứng:**
  - Tồn kho luôn đảm bảo bất biến: `available = on_hand - reserved` và `available >= 0, on_hand >= 0, reserved >= 0`.
  - Sổ cái kho (`stock_ledger`) là cấu trúc **chỉ nối thêm (append-only)** và là nguồn chân lý duy nhất để kiểm toán và tái tạo bảng số dư.
  - Tuyệt đối không xóa cứng (no hard delete) nguyên liệu hoặc công thức đã từng phát sinh tham chiếu lịch sử; chỉ cho phép chuyển sang trạng thái lưu trữ mềm (`ARCHIVED`).

---

## 2. Tiêu Chuẩn HTTP Chung (Common HTTP Contract)

### 2.1 Các HTTP Header Quy Chuẩn

| Header | Bắt buộc | Phạm vi | Ý nghĩa kỹ thuật |
|---|:---:|---|---|
| `Authorization` | Bắt buộc | Public API | `Bearer <Cognito access token>`. Xác thực danh tính người dùng và quyền qua Identity Service. |
| `Authorization` | Bắt buộc | Internal API | `Bearer <Workload Identity / mTLS token>`. Xác thực định danh hệ thống của AWS Step Functions Checkout Saga hoặc Scheduler. |
| `X-Active-Branch-Id` | Có điều kiện | Public API | Bắt buộc đối với vai trò chi nhánh (`CASHIER`, `MANAGER`, `KITCHEN`). Xác thực tư cách thành viên hợp lệ tại chi nhánh. |
| `X-Correlation-Id` | Khuyến nghị | Tất cả | Mã UUID v7 truy vết phân tán xuyên service; service tự sinh nếu thiếu và phản hồi trong metadata. |
| `X-Causation-Id` | Có điều kiện | Internal / Sự kiện | Định danh của lệnh hoặc sự kiện trực tiếp gây ra thao tác hiện tại. |
| `Idempotency-Key` | Bắt buộc | Các lệnh ghi (POST/PUT/PATCH) | Khóa duy nhất do client/Saga sinh nhằm đảm bảo an toàn khi thử lại mạng. |
| `If-Match` | Bắt buộc | Cập nhật / Phê duyệt | ETag hoặc số phiên bản đọc được từ phản hồi GET trước đó (ví dụ: `"recipe-v4"`, `"ingredient-v2"`). |
| `Content-Type` | Bắt buộc | Request có body | `application/json; charset=utf-8`. |

---

### 2.2 Định Dạng Bao Bì Chuẩn Hóa (Envelope Standards)

#### 1. Phản hồi Thực thể Đơn lẻ (Single Entity Response)

```json
{
  "data": {},
  "meta": {
    "requestId": "req_0192b300-7f1d-7abc-8d2c-123456789001",
    "correlationId": "0192b300-7f1d-7abc-8d2c-123456789002"
  }
}
```

#### 2. Phản hồi Phân trang theo Vị trí (Offset-based Pagination)

```json
{
  "data": [],
  "meta": {
    "pagination": {
      "page": 1,
      "limit": 20,
      "total": 42,
      "totalPages": 3
    },
    "requestId": "req_0192b300-7f1d-7abc-8d2c-123456789003",
    "correlationId": "0192b300-7f1d-7abc-8d2c-123456789002"
  }
}
```

#### 3. Phản hồi Phân trang theo Con trỏ (Cursor-based Pagination)

```json
{
  "data": [],
  "meta": {
    "hasMore": true,
    "nextCursor": "eyJvY2N1cnJlZEF0IjoiMjAyNi0xMC0xM1QxMDoxNTowMFoiLCJpZCI6IjAxOTJiMzAwLTdmMmQtN2FiYy04ZDJjLTEyMzQ1Njc4OTAxMiJ9",
    "limit": 20,
    "requestId": "req_0192b300-7f1d-7abc-8d2c-123456789004",
    "correlationId": "0192b300-7f1d-7abc-8d2c-123456789002"
  }
}
```

#### 4. Phản hồi Báo Lỗi Chuẩn Hóa (Standard Error Envelope)

```json
{
  "error": {
    "code": "insufficient_stock",
    "message": "Không đủ tồn kho khả dụng để thực hiện giữ chỗ nguyên liệu",
    "details": [
      {
        "field": "shortages[0].ingredientId",
        "code": "available_below_required",
        "message": "Nguyên liệu Sữa tươi thanh trùng (MILK-WHOLE) còn 1200.000 ml khả dụng, nhưng đơn yêu cầu 1800.000 ml"
      }
    ],
    "requestId": "req_0192b300-7f1d-7abc-8d2c-123456789005",
    "correlationId": "0192b300-7f1d-7abc-8d2c-123456789002"
  }
}
```

---

### 2.3 Bảng Mã Lỗi Chuẩn Hóa và Mã Trạng Thái HTTP

| HTTP Status | Mã lỗi (`error.code`) | Ý nghĩa nghiệp vụ | Hành vi Thử lại (Retryable) |
|:---:|---|---|:---:|
| `200 OK` | — | Đọc thành công hoặc thao tác commit/release hoàn tất. | Không |
| `201 Created` | — | Tạo mới nguyên liệu, công thức, phiếu nhập kho hoặc giữ chỗ thành công. | Không |
| `400 Bad Request` | `validation_error` | Yêu cầu sai định dạng, thiếu trường bắt buộc hoặc vi phạm schema. | Không |
| `401 Unauthorized` | `unauthorized` | Token xác thực thiếu, hết hạn hoặc không hợp lệ. | Có (sau khi làm mới token) |
| `403 Forbidden` | `forbidden` | Vai trò hoặc phạm vi chi nhánh không đủ quyền thực hiện thao tác. | Không |
| `404 Not Found` | `not_found` | Tài nguyên không tồn tại trong phạm vi tenant/chi nhánh được ủy quyền. | Không |
| `409 Conflict` | `idempotency_conflict` | Cùng `Idempotency-Key` nhưng hash payload yêu cầu bị thay đổi. | Không |
| `409 Conflict` | `recipe_version_conflict` | ETag hoặc phiên bản công thức trong `If-Match` không khớp dữ liệu máy chủ. | Lấy lại dữ liệu mới |
| `409 Conflict` | `active_recipe_exists` | Đã tồn tại một công thức khác đang ở trạng thái `ACTIVE` cho cùng item/variant. | Làm mới dữ liệu |
| `409 Conflict` | `insufficient_stock` | Tồn kho khả dụng không đủ để đáp ứng đơn giữ chỗ. Trả về mảng `shortages[]`. | Phía POS / Saga quyết định |
| `409 Conflict` | `reservation_state_conflict` | Lệnh gửi đến mâu thuẫn với trạng thái kết thúc (`COMMITTED`, `RELEASED`, `EXPIRED`). | Không |
| `409 Conflict` | `reservation_expired` | Yêu cầu commit gửi tới sau khi đơn giữ chỗ đã hết hạn. | Chuyển luồng đối soát |
| `409 Conflict` | `count_session_conflict` | Phiên kiểm kê đã được duyệt hoặc snapshot kiểm kê bị xung đột. | Làm mới phiên kiểm kê |
| `409 Conflict` | `archived_resource` | Tài nguyên đã lưu trữ mềm (`ARCHIVED`) không được phép sửa đổi. | Không |
| `422 Unprocessable` | `invalid_recipe_line` | Sai phạm vi nguyên liệu, trùng lặp dòng `(ingredientId, modifierOptionId)`. | Không |
| `422 Unprocessable` | `invalid_branch_scope` | Chi nhánh trên URL không khớp với header `X-Active-Branch-Id`. | Không |
| `422 Unprocessable` | `forecast_insufficient_history` | Chưa đủ số ngày dữ liệu lịch sử để tính toán dự báo nhu cầu (P2). | Không |
| `429 Too Many Requests`| `rate_limited` | Vượt quá giới hạn tần suất yêu cầu tại API Gateway hoặc Service. | Có (lùi thời gian số mũ) |
| `503 Unavailable` | `lock_timeout` | Quá thời gian chờ khóa dòng số dư kho bi quan (`SELECT FOR UPDATE`). | Có (Saga retry) |
| `503 Unavailable` | `service_unavailable` | Cơ sở dữ liệu PostgreSQL hoặc EventBridge phụ thuộc tạm thời gián đoạn. | Có |

---

## 3. Danh Mục Các Endpoint

| Phương thức | Đường dẫn API | Phân loại | Thẩm quyền / Phạm vi | Ý nghĩa nghiệp vụ |
|---|---|:---:|---|---|
| `POST` | `/internal/v1/inventory/reservations` | Internal | Workload Identity (Step Functions) | Giữ chỗ nguyên liệu cho đơn hàng trong luồng checkout saga. |
| `POST` | `/internal/v1/inventory/reservations/{reservationId}/commit` | Internal | Workload Identity (Step Functions) | Xác nhận trừ kho vĩnh viễn khi thanh toán thành công (`SALE_COMMITTED`). |
| `POST` | `/internal/v1/inventory/reservations/{reservationId}/release` | Internal | Workload Identity (Step Functions) | Lệnh bồi hoàn giải phóng nguyên liệu khi thanh toán thất bại hoặc timeout. |
| `POST` | `/internal/v1/inventory/availability` | Internal | Workload Identity (Order / Saga) | Tra cứu nhanh khả năng đáp ứng nguyên liệu cho giỏ hàng (Advisory). |
| `GET` | `/api/v1/inventory/ingredients` | Public | `inventory:read` | Lấy danh sách nguyên liệu master (phân trang offset, lọc status). |
| `POST` | `/api/v1/inventory/ingredients` | Public | `inventory:recipe:manage` | Tạo mới nguyên liệu và quy đổi đơn vị mua hàng. |
| `GET` | `/api/v1/inventory/ingredients/{ingredientId}` | Public | `inventory:read` | Xem chi tiết nguyên liệu và các quy đổi đơn vị mua. |
| `PATCH` | `/api/v1/inventory/ingredients/{ingredientId}` | Public | `inventory:recipe:manage` + `If-Match` | Cập nhật thông tin nguyên liệu hoặc đơn vị quy đổi. |
| `POST` | `/api/v1/inventory/ingredients/{ingredientId}/state` | Public | `inventory:recipe:manage` + `If-Match` | Chuyển trạng thái nguyên liệu (`ACTIVE`, `INACTIVE`, `ARCHIVED`). |
| `GET` | `/api/v1/inventory/recipes` | Public | `inventory:read` | Tra cứu danh sách phiên bản công thức theo món/biến thể. |
| `POST` | `/api/v1/inventory/recipes` | Public | `inventory:recipe:manage` | Tạo mới phiên bản công thức định lượng ở trạng thái `DRAFT`. |
| `GET` | `/api/v1/inventory/recipes/{recipeVersionId}` | Public | `inventory:read` | Xem chi tiết công thức và các dòng định lượng nguyên liệu. |
| `PATCH` | `/api/v1/inventory/recipes/{recipeVersionId}` | Public | `inventory:recipe:manage` + `If-Match` | Cập nhật các dòng định lượng của công thức `DRAFT`. |
| `POST` | `/api/v1/inventory/recipes/{recipeVersionId}/validate` | Public | `inventory:recipe:manage` | Kiểm tra tính hợp lệ của công thức trước khi kích hoạt. |
| `POST` | `/api/v1/inventory/recipes/{recipeVersionId}/activate` | Public | `inventory:recipe:manage` + `If-Match` | Kích hoạt công thức `ACTIVE`, tự động vô hiệu hóa bản active cũ. |
| `POST` | `/api/v1/inventory/recipes/{recipeVersionId}/archive` | Public | `inventory:recipe:manage` + `If-Match` | Lưu trữ mềm công thức không còn sử dụng. |
| `GET` | `/api/v1/inventory/branches/{branchId}/balances` | Public | `inventory:read` | Tra cứu số dư tồn kho chi nhánh (`onHand`, `reserved`, `available`). |
| `GET` | `/api/v1/inventory/branches/{branchId}/ledger` | Public | `inventory:read` | Tra cứu lịch sử biến động sổ cái kho (phân trang con trỏ `cursor`). |
| `GET` | `/api/v1/inventory/branches/{branchId}/reservations` | Public | `inventory:read` | Tra cứu lịch sử các đơn giữ chỗ nguyên liệu (phân trang con trỏ). |
| `POST` | `/api/v1/inventory/branches/{branchId}/opening-balances` | Public | `inventory:stock:write` | Khởi tạo số dư đầu kỳ cho chi nhánh mới thành lập. |
| `GET` | `/api/v1/inventory/branches/{branchId}/receipts` | Public | `inventory:read` | Danh sách phiếu nhập kho nguyên liệu tại chi nhánh. |
| `POST` | `/api/v1/inventory/branches/{branchId}/receipts` | Public | `inventory:stock:write` | Tạo phiếu nhập kho và tự động ghi sổ cái `RECEIPT`. |
| `GET` | `/api/v1/inventory/branches/{branchId}/count-sessions` | Public | `inventory:read` | Danh sách các phiên kiểm kê kho tại chi nhánh. |
| `POST` | `/api/v1/inventory/branches/{branchId}/count-sessions` | Public | `inventory:count:input` | Mở phiên kiểm kê kho mới (chụp snapshot số dư). |
| `POST` | `/api/v1/inventory/count-sessions/{countSessionId}/lines` | Public | `inventory:count:input` | Nhập số lượng kiểm đếm thực tế cho từng nguyên liệu. |
| `POST` | `/api/v1/inventory/count-sessions/{countSessionId}/approve` | Public | `inventory:count:approve` + `If-Match` | Phê duyệt kiểm kê: tính chênh lệch thực tế và ghi sổ cái điều chỉnh. |
| `POST` | `/api/v1/inventory/count-sessions/{countSessionId}/cancel` | Public | `inventory:count:approve` + `If-Match` | Hủy bỏ phiên kiểm kê kho đang mở. |
| `GET` | `/api/v1/inventory/branches/{branchId}/low-stock-alerts` | Public | `inventory:read` | Danh sách cảnh báo tồn kho thấp dưới ngưỡng an toàn. |
| `POST` | `/api/v1/inventory/low-stock-alerts/{alertId}/acknowledge` | Public | `inventory:alert:manage` | Quản lý tiếp nhận xử lý cảnh báo tồn kho thấp. |
| `POST` | `/api/v1/inventory/low-stock-alerts/{alertId}/resolve` | Public | `inventory:alert:manage` | Đóng cảnh báo tồn kho thấp sau khi đã nhập hàng bù tồn. |
| `GET` | `/api/v1/inventory/branches/{branchId}/forecasts` | Public (P2) | `inventory:forecast:read` | Tra cứu số liệu dự báo nhu cầu nhập hàng theo ngày. |
| `PUT` | `/api/v1/inventory/branches/{branchId}/forecast-policies/{ingredientId}` | Public (P2) | `inventory:forecast:configure` | Cấu hình định mức tồn an toàn và thời gian đặt hàng NCC. |

---

## 4. Hợp Đồng Chi Tiết Các Endpoint

### 4.1 Các API Nội Bộ Phục Vụ Saga Checkout (Internal Saga APIs)

#### `POST /internal/v1/inventory/reservations`
Được AWS Step Functions Checkout Saga gọi đồng bộ để giữ chỗ nguyên liệu trước khi tiến hành thanh toán. Quy trình: phân giải công thức `ACTIVE`, tính tổng nhu cầu, khóa dòng bi quan `SELECT ... FOR UPDATE` theo thứ tự `ingredientId` tăng dần, kiểm tra `available >= required`, tăng `reserved` và phát sự kiện `InventoryReserved.v1`.

- **Thẩm quyền:** Workload Identity của AWS Step Functions / Order Service.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <workload-token>` (Bắt buộc)
  - `Idempotency-Key: <unique-saga-task-key>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Bắt buộc)
  - `Content-Type: application/json; charset=utf-8` (Bắt buộc)
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "sagaId": "0192b300-7f33-7abc-8d2c-123456789012",
  "orderId": "0192b300-7f30-7abc-8d2c-123456789012",
  "checkoutAttemptId": "0192b300-7f32-7abc-8d2c-123456789012",
  "branchId": "0192b300-7f23-7abc-8d2c-123456789012",
  "expiresAt": "2026-10-13T10:15:00Z",
  "lines": [
    {
      "lineId": "0192b300-7f31-7abc-8d2c-123456789012",
      "catalogItemId": "0192b2d0-7f1e-7abc-8d2c-123456789012",
      "variantId": "0192b2d0-7f20-7abc-8d2c-123456789012",
      "modifierOptionIds": [
        "0192b2d0-7f22-7abc-8d2c-123456789012"
      ],
      "quantity": 2
    }
  ]
}
```

*Quy cách kiểm tra (Validation):*
- `branchId`, `orderId`, `checkoutAttemptId`, `sagaId`: Bắt buộc, chuỗi UUID v7.
- `lines`: Mảng từ 1 đến 100 dòng món; mỗi dòng có `quantity` là số nguyên dương từ 1 đến 999.
- `expiresAt`: Mốc thời gian hết hạn hiệu lực của đơn giữ chỗ (thường từ 5–15 phút kể từ lúc tạo).

- **Phản hồi Thành công (`201 Created`):**

```json
{
  "data": {
    "reservationId": "0192b300-7f35-7abc-8d2c-123456789012",
    "status": "RESERVED",
    "orderId": "0192b300-7f30-7abc-8d2c-123456789012",
    "checkoutAttemptId": "0192b300-7f32-7abc-8d2c-123456789012",
    "branchId": "0192b300-7f23-7abc-8d2c-123456789012",
    "expiresAt": "2026-10-13T10:15:00Z",
    "reservationVersion": 1,
    "reservedIngredients": [
      {
        "ingredientId": "0192b300-7f10-7abc-8d2c-123456789012",
        "ingredientCode": "COFFEE-BEANS-ROBUSTA",
        "quantity": 40.000,
        "baseUnit": "g"
      },
      {
        "ingredientId": "0192b300-7f11-7abc-8d2c-123456789012",
        "ingredientCode": "CONDENSED-MILK",
        "quantity": 60.000,
        "baseUnit": "ml"
      },
      {
        "ingredientId": "0192b300-7f12-7abc-8d2c-123456789012",
        "ingredientCode": "BLACK-PEARL",
        "quantity": 50.000,
        "baseUnit": "g"
      }
    ],
    "shortages": [],
    "recipeSnapshots": [
      {
        "catalogItemId": "0192b2d0-7f1e-7abc-8d2c-123456789012",
        "variantId": "0192b2d0-7f20-7abc-8d2c-123456789012",
        "recipeVersionId": "0192b300-7f14-7abc-8d2c-123456789012",
        "recipeVersionNumber": 3
      }
    ]
  },
  "meta": {
    "requestId": "req_0192b300-7f35-7abc-8d2c-123456789001",
    "correlationId": "0192b300-7f1d-7abc-8d2c-123456789002"
  }
}
```

- **Mã lỗi khả dĩ:**
  - `409 Conflict` (`insufficient_stock`): Không đủ tồn kho khả dụng; trả về danh sách chi tiết các nguyên liệu thiếu trong mảng `shortages[]` để Saga hủy checkout.
  - `409 Conflict` (`duplicate_request`): Trùng `Idempotency-Key` nhưng hash payload yêu cầu bị thay đổi.
  - `422 Unprocessable Entity` (`missing_active_recipe`): Món ăn chưa được định nghĩa công thức `ACTIVE` (nguyên tắc fail-closed: không có công thức thì từ chối giữ chỗ).
  - `503 Service Unavailable` (`lock_timeout`): Quá thời gian chờ khóa dòng số dư khi nhiều đơn hàng giữ chỗ cùng lúc.

---

#### `POST /internal/v1/inventory/reservations/{reservationId}/commit`
Được AWS Step Functions Checkout Saga gọi sau khi nhận được sự kiện thanh toán thành công (`PaymentPaid.v1`). Thao tác này trừ tồn kho vật lý (`onHand`), giải phóng lượng giữ chỗ (`reserved`), ghi các dòng sổ cái `SALE_COMMITTED` số lượng âm và chuyển trạng thái đơn giữ chỗ sang `COMMITTED`.

- **Thẩm quyền:** Workload Identity của AWS Step Functions.
- **Tiêu đề Yêu cầu (Request Headers):** `Authorization`, `Idempotency-Key`, `X-Correlation-Id`, `Content-Type`.
- **Tham số Đường dẫn (Path Parameters):**
  - `reservationId` (UUID, Bắt buộc): Định danh đơn giữ chỗ cần commit.
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "checkoutAttemptId": "0192b300-7f32-7abc-8d2c-123456789012",
  "orderId": "0192b300-7f30-7abc-8d2c-123456789012",
  "paymentId": "0192b2d0-7f34-7abc-8d2c-123456789012",
  "paymentAttemptId": "0192b2d0-7f34-7abc-8d2c-123456789012",
  "paymentStatus": "SUCCEEDED",
  "paidAmountMinor": 68000,
  "currency": "VND"
}
```

*Quy cách kiểm tra:* `paymentStatus` bắt buộc phải là `"SUCCEEDED"`. Toàn bộ `checkoutAttemptId`, `orderId`, `paymentId` phải khớp hoàn toàn với thông tin checkout đã lưu.

- **Phản hồi Thành công (`200 OK`):**

```json
{
  "data": {
    "reservationId": "0192b300-7f35-7abc-8d2c-123456789012",
    "status": "COMMITTED",
    "committedAt": "2026-10-13T10:15:35Z",
    "ledgerEntryIds": [
      "0192b300-7f36-7abc-8d2c-123456789012",
      "0192b300-7f37-7abc-8d2c-123456789012",
      "0192b300-7f38-7abc-8d2c-123456789012"
    ],
    "reservationVersion": 2
  },
  "meta": {
    "requestId": "req_0192b300-7f35-7abc-8d2c-123456789002",
    "correlationId": "0192b300-7f1d-7abc-8d2c-123456789002"
  }
}
```

- **Mã lỗi khả dĩ:**
  - `404 Not Found`: Không tìm thấy `reservationId`.
  - `409 Conflict` (`reservation_state_conflict`): Đơn giữ chỗ đã bị hủy (`RELEASED`) hoặc đã hết hạn trước đó (`EXPIRED`). Hệ thống tự động tạo bản ghi đối soát ngoại lệ `reconciliation_cases`.
  - `409 Conflict` (`payment_context_mismatch`): Thông tin thanh toán không khớp với đơn hàng.

---

#### `POST /internal/v1/inventory/reservations/{reservationId}/release`
Lệnh bồi hoàn (Saga Compensation) được gọi khi thanh toán thất bại, checkout bị timeout, hoặc đơn hàng bị hủy bỏ trước khi thanh toán. Thao tác này giảm `reserved`, phục hồi lại `available` và chuyển trạng thái đơn giữ chỗ sang `RELEASED`.

- **Thẩm quyền:** Workload Identity của AWS Step Functions.
- **Tiêu đề Yêu cầu (Request Headers):** `Authorization`, `Idempotency-Key`, `X-Correlation-Id`, `Content-Type`.
- **Tham số Đường dẫn (Path Parameters):** `reservationId` (UUID, Bắt buộc).
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "checkoutAttemptId": "0192b300-7f32-7abc-8d2c-123456789012",
  "orderId": "0192b300-7f30-7abc-8d2c-123456789012",
  "reasonCode": "PAYMENT_FAILED",
  "reason": "Khách hàng hủy giao dịch trên màn hình VNPAY Sandbox"
}
```

*Mã lý do hợp lệ:* `PAYMENT_FAILED`, `CHECKOUT_TIMEOUT`, `ORDER_CANCELLED`, `SAGA_COMPENSATION`, `OPERATOR_RECONCILIATION`.

- **Phản hồi Thành công (`200 OK`):**

```json
{
  "data": {
    "reservationId": "0192b300-7f35-7abc-8d2c-123456789012",
    "status": "RELEASED",
    "releasedAt": "2026-10-13T10:16:00Z",
    "reasonCode": "PAYMENT_FAILED",
    "reservationVersion": 2
  },
  "meta": {
    "requestId": "req_0192b300-7f35-7abc-8d2c-123456789003",
    "correlationId": "0192b300-7f1d-7abc-8d2c-123456789002"
  }
}
```

---

#### `POST /internal/v1/inventory/availability`
Tra cứu nhanh khả năng đáp ứng nguyên liệu cho giỏ hàng tại quầy POS trước khi bấm Checkout (Advisory check). Endpoint này chỉ đọc, không khóa database và có thể trả kết quả kèm độ trễ nhỏ.

- **Thẩm quyền:** Workload Identity của Order Service hoặc POS API Gateway.
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "branchId": "0192b300-7f23-7abc-8d2c-123456789012",
  "selections": [
    {
      "lineId": "line-1",
      "catalogItemId": "0192b2d0-7f1e-7abc-8d2c-123456789012",
      "variantId": "0192b2d0-7f20-7abc-8d2c-123456789012",
      "modifierOptionIds": ["0192b2d0-7f22-7abc-8d2c-123456789012"],
      "quantity": 10
    }
  ]
}
```

- **Phản hồi Thành công (`200 OK`):**

```json
{
  "data": {
    "canFulfill": true,
    "shortages": [],
    "evaluatedAt": "2026-10-13T10:14:00Z"
  },
  "meta": {
    "requestId": "req_0192b300-7f35-7abc-8d2c-123456789004",
    "correlationId": "0192b300-7f1d-7abc-8d2c-123456789002"
  }
}
```

---

### 4.2 Các API Quản Lý Nguyên Liệu (Ingredients Master Data)

#### `GET /api/v1/inventory/ingredients`
Lấy danh sách các nguyên liệu master trong tenant, hỗ trợ lọc trạng thái, tìm kiếm và phân trang offset.

- **Thẩm quyền:** Quyền `inventory:read`.
- **Tiêu đề Yêu cầu (Request Headers):** `Authorization: Bearer <token>`, `X-Correlation-Id`.
- **Tham số Truy vấn (Query Parameters):**
  - `page` (integer, Mặc định: `1`): Số trang.
  - `limit` (integer, Mặc định: `20`, Tối đa: `100`): Số bản ghi trên mỗi trang.
  - `status` (string, Tùy chọn): `ACTIVE`, `INACTIVE`, `ARCHIVED`.
  - `q` (string, Tùy chọn): Tìm kiếm theo mã hoặc tên nguyên liệu.
- **Phản hồi Thành công (`200 OK`):**

```json
{
  "data": [
    {
      "id": "0192b300-7f10-7abc-8d2c-123456789012",
      "code": "COFFEE-BEANS-ROBUSTA",
      "name": "Hạt cà phê Robusta rang mộc",
      "baseUnit": "g",
      "status": "ACTIVE",
      "purchaseConversions": [
        {
          "id": "0192b300-7f15-7abc-8d2c-123456789012",
          "purchaseUnit": "bao 20kg",
          "baseQuantity": 20000.000,
          "status": "ACTIVE"
        }
      ],
      "version": 1,
      "createdAt": "2026-10-13T08:00:00Z",
      "updatedAt": "2026-10-13T08:00:00Z"
    }
  ],
  "meta": {
    "pagination": {
      "page": 1,
      "limit": 20,
      "total": 1,
      "totalPages": 1
    },
    "requestId": "req_0192b300-7f10-7abc-8d2c-123456789001",
    "correlationId": "0192b300-7f1d-7abc-8d2c-123456789002"
  }
}
```

---

#### `POST /api/v1/inventory/ingredients`
Tạo mới một nguyên liệu master trong tenant kèm các đơn vị quy đổi mua hàng.

- **Thẩm quyền:** Quyền `inventory:recipe:manage` (`OWNER`, `MANAGER`).
- **Tiêu đề Yêu cầu (Request Headers):** `Authorization`, `Idempotency-Key`, `X-Correlation-Id`, `Content-Type`.
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "code": "MILK-WHOLE",
  "name": "Sữa tươi thanh trùng tiệt trùng",
  "baseUnit": "ml",
  "purchaseConversions": [
    {
      "purchaseUnit": "thùng 12 hộp 1L",
      "baseQuantity": 12000.000
    },
    {
      "purchaseUnit": "hộp 1L",
      "baseQuantity": 1000.000
    }
  ]
}
```

*Quy cách kiểm tra:* `code` viết hoa không dấu và gạch nối (`^[A-Z0-9-]+$`), duy nhất trong tenant. `baseUnit ∈ {'g', 'ml', 'unit'}`. `baseQuantity > 0`.

- **Phản hồi Thành công (`201 Created`):** Trả về `IngredientResponse` với `status: "ACTIVE"` và `version: 1`.
- **Mã lỗi khả dĩ:** `403 Forbidden`, `409 Conflict` (trùng mã code), `422 validation_error`.

---

#### `PATCH /api/v1/inventory/ingredients/{ingredientId}`
Cập nhật tên nguyên liệu hoặc danh sách đơn vị quy đổi mua hàng.

- **Thẩm quyền:** Quyền `inventory:recipe:manage`.
- **Tiêu đề Yêu cầu (Request Headers):** `Authorization`, `If-Match: "ingredient-v1"`, `Idempotency-Key`, `X-Correlation-Id`, `Content-Type`.
- **Tham số Đường dẫn (Path Parameters):** `ingredientId` (UUID, Bắt buộc).
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "name": "Sữa tươi thanh trùng DalatMilk 1L"
}
```

- **Phản hồi Thành công (`200 OK`):** Trả về `IngredientResponse` với version tăng thêm 1.
- **Mã lỗi khả dĩ:** `403 Forbidden`, `404 Not Found`, `409 Conflict` (sai ETag `If-Match`).

---

### 4.3 Các API Quản Lý Phiên Bản Công Thức (Recipe Versions)

#### `POST /api/v1/inventory/recipes`
Tạo mới một phiên bản công thức định lượng ở trạng thái `DRAFT` cho một món ăn/biến thể.

- **Thẩm quyền:** Quyền `inventory:recipe:manage`.
- **Tiêu đề Yêu cầu (Request Headers):** `Authorization`, `Idempotency-Key`, `X-Correlation-Id`, `Content-Type`.
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "catalogItemId": "0192b2d0-7f1e-7abc-8d2c-123456789012",
  "variantId": "0192b2d0-7f20-7abc-8d2c-123456789012",
  "lines": [
    {
      "ingredientId": "0192b300-7f10-7abc-8d2c-123456789012",
      "modifierOptionId": null,
      "quantityPerSale": 20.000,
      "wasteFactor": 1.050,
      "baseUnit": "g"
    },
    {
      "ingredientId": "0192b300-7f11-7abc-8d2c-123456789012",
      "modifierOptionId": null,
      "quantityPerSale": 30.000,
      "wasteFactor": 1.000,
      "baseUnit": "ml"
    },
    {
      "ingredientId": "0192b300-7f12-7abc-8d2c-123456789012",
      "modifierOptionId": "0192b2d0-7f22-7abc-8d2c-123456789012",
      "quantityPerSale": 25.000,
      "wasteFactor": 1.000,
      "baseUnit": "g"
    }
  ]
}
```

*Quy cách kiểm tra:*
- `lines`: Có ít nhất 1 dòng. Mỗi dòng có `quantityPerSale > 0` và `wasteFactor >= 1.000`.
- Ràng buộc: Cặp `(ingredientId, modifierOptionId)` không được trùng lặp trong cùng một công thức.
- Toàn bộ `ingredientId` phải tồn tại và đang ở trạng thái `ACTIVE`.

- **Phản hồi Thành công (`201 Created`):**

```json
{
  "data": {
    "id": "0192b300-7f14-7abc-8d2c-123456789012",
    "tenantId": "0192b300-7f1d-7abc-8d2c-123456789012",
    "catalogItemId": "0192b2d0-7f1e-7abc-8d2c-123456789012",
    "variantId": "0192b2d0-7f20-7abc-8d2c-123456789012",
    "version": 4,
    "status": "DRAFT",
    "versionEtag": "recipe-v4",
    "linesCount": 3,
    "lines": [
      {
        "id": "0192b300-7f16-7abc-8d2c-123456789012",
        "ingredientId": "0192b300-7f10-7abc-8d2c-123456789012",
        "modifierOptionId": null,
        "quantityPerSale": 20.000,
        "wasteFactor": 1.050,
        "baseUnit": "g"
      }
    ],
    "createdAt": "2026-10-13T09:00:00Z"
  },
  "meta": {
    "requestId": "req_0192b300-7f14-7abc-8d2c-123456789001",
    "correlationId": "0192b300-7f1d-7abc-8d2c-123456789002"
  }
}
```

---

#### `POST /api/v1/inventory/recipes/{recipeVersionId}/activate`
Kích hoạt phiên bản công thức (`DRAFT -> ACTIVE`). Bất biến: Khi một công thức được kích hoạt, hệ thống tự động chuyển phiên bản `ACTIVE` trước đó của cùng cặp `(catalogItemId, variantId)` sang trạng thái `INACTIVE` một cách nguyên tử trong cùng một database transaction.

- **Thẩm quyền:** Quyền `inventory:recipe:manage`.
- **Tiêu đề Yêu cầu (Request Headers):** `Authorization`, `If-Match: "recipe-v4"`, `Idempotency-Key`, `X-Correlation-Id`.
- **Tham số Đường dẫn (Path Parameters):** `recipeVersionId` (UUID, Bắt buộc).
- **Phản hồi Thành công (`200 OK`):** Trả về `RecipeResponse` với `status: "ACTIVE"` và `activatedAt: "2026-10-13T09:10:00Z"`.
- **Mã lỗi khả dĩ:** `403 Forbidden`, `404 Not Found`, `409 Conflict` (sai ETag `If-Match`), `422 validation_error` (công thức rỗng hoặc chứa nguyên liệu inactive).

---

### 4.4 Các API Quản Lý Số Dư, Sổ Cái & Nhập Kho

#### `GET /api/v1/inventory/branches/{branchId}/balances`
Tra cứu số dư tồn kho chi nhánh (`onHand`, `reserved`, `available`).

- **Thẩm quyền:** Quyền `inventory:read` tại chi nhánh.
- **Tiêu đề Yêu cầu (Request Headers):** `Authorization`, `X-Active-Branch-Id: <uuid>`, `X-Correlation-Id`.
- **Tham số Đường dẫn (Path Parameters):** `branchId` (UUID, Bắt buộc).
- **Tham số Truy vấn (Query Parameters):** `page`, `limit`, `ingredientId`, `belowLowStock` (boolean, lọc các món sắp hết hàng).
- **Phản hồi Thành công (`200 OK`):**

```json
{
  "data": [
    {
      "ingredientId": "0192b300-7f10-7abc-8d2c-123456789012",
      "ingredientCode": "COFFEE-BEANS-ROBUSTA",
      "ingredientName": "Hạt cà phê Robusta rang mộc",
      "branchId": "0192b300-7f23-7abc-8d2c-123456789012",
      "baseUnit": "g",
      "onHand": 15000.000,
      "reserved": 1200.000,
      "available": 13800.000,
      "lowStockThreshold": 2000.000,
      "isLowStock": false,
      "version": 25,
      "updatedAt": "2026-10-13T10:15:35Z"
    }
  ],
  "meta": {
    "pagination": { "page": 1, "limit": 20, "total": 1, "totalPages": 1 },
    "requestId": "req_0192b300-7f23-7abc-8d2c-123456789001",
    "correlationId": "0192b300-7f1d-7abc-8d2c-123456789002"
  }
}
```

---

#### `GET /api/v1/inventory/branches/{branchId}/ledger`
Tra cứu lịch sử biến động sổ cái kho bất biến (`stock_ledger`). Sử dụng phân trang con trỏ (Cursor Pagination) cố định trên cặp `(occurredAt, id)` nhằm đảm bảo tốc độ truy vấn cao.

- **Thẩm quyền:** Quyền `inventory:read` (`MANAGER`, `OWNER`).
- **Tham số Truy vấn (Query Parameters):** `limit`, `cursor`, `ingredientId`, `movementType` (`OPENING_BALANCE`, `RECEIPT`, `SALE_COMMITTED`, `COUNT_ADJUSTMENT`, `WASTE`, `RETURN`), `from`, `to`.
- **Phản hồi Thành công (`200 OK`):**

```json
{
  "data": [
    {
      "id": "0192b300-7f36-7abc-8d2c-123456789012",
      "branchId": "0192b300-7f23-7abc-8d2c-123456789012",
      "ingredientId": "0192b300-7f10-7abc-8d2c-123456789012",
      "movementType": "SALE_COMMITTED",
      "quantityDelta": -40.000,
      "baseUnit": "g",
      "sourceType": "RESERVATION",
      "sourceId": "0192b300-7f35-7abc-8d2c-123456789012",
      "reason": "Xuất kho bán hàng đơn A03-000127",
      "occurredAt": "2026-10-13T10:15:35Z"
    }
  ],
  "meta": {
    "hasMore": false,
    "nextCursor": null,
    "limit": 20,
    "requestId": "req_0192b300-7f23-7abc-8d2c-123456789002",
    "correlationId": "0192b300-7f1d-7abc-8d2c-123456789002"
  }
}
```

---

#### `POST /api/v1/inventory/branches/{branchId}/receipts`
Tạo phiếu nhập kho nguyên liệu tại chi nhánh. Hệ thống tự động quy đổi từ đơn vị mua hàng sang đơn vị cơ sở, tăng `onHand`, tăng `available` và ghi dòng sổ cái `RECEIPT` số lượng dương.

- **Thẩm quyền:** Quyền `inventory:stock:write` (`MANAGER`, `OWNER`).
- **Tiêu đề Yêu cầu (Request Headers):** `Authorization`, `X-Active-Branch-Id`, `Idempotency-Key`, `X-Correlation-Id`, `Content-Type`.
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "supplierName": "Công ty TNHH Cà phê Việt",
  "invoiceNumber": "HD-20261013-01",
  "note": "Nhập hàng định kỳ đầu tuần",
  "lines": [
    {
      "ingredientId": "0192b300-7f10-7abc-8d2c-123456789012",
      "purchaseUnit": "bao 20kg",
      "purchaseQuantity": 2.000,
      "conversionFactor": 20000.000,
      "baseQuantity": 40000.000,
      "lotCode": "LOT-2026-10",
      "expiresAt": "2027-04-13T00:00:00Z"
    }
  ]
}
```

- **Phản hồi Thành công (`201 Created`):** Trả về `ReceiptResponse` và các ID dòng sổ cái được sinh ra.

---

### 4.5 Các API Quản Lý Phiên Kiểm Kê Kho (Count Sessions)

#### `POST /api/v1/inventory/branches/{branchId}/count-sessions`
Mở một phiên kiểm kê kho mới tại chi nhánh. Tại thời điểm này, hệ thống chụp snapshot số dư tồn vật lý (`snapshot_on_hand`), giữ chỗ (`snapshot_reserved`) và khả dụng (`snapshot_available`). Quá trình bán hàng tại POS vẫn diễn ra bình thường.

- **Thẩm quyền:** Quyền `inventory:count:input` (`CASHIER`, `MANAGER`).
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "note": "Kiểm kê định kỳ cuối ngày ca sáng"
}
```

- **Phản hồi Thành công (`201 Created`):** Trả về `CountSessionResponse` với `status: "IN_PROGRESS"` và danh sách các nguyên liệu cần đếm kèm snapshot.

---

#### `POST /api/v1/inventory/count-sessions/{countSessionId}/lines`
Nhập số lượng kiểm đếm thực tế cho từng nguyên liệu trong phiên kiểm kê.

- **Thẩm quyền:** Quyền `inventory:count:input`.
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "lines": [
    {
      "ingredientId": "0192b300-7f10-7abc-8d2c-123456789012",
      "countedQuantity": 14950.000
    }
  ]
}
```

---

#### `POST /api/v1/inventory/count-sessions/{countSessionId}/approve`
Quản lý phê duyệt kết quả kiểm kê. Hệ thống tự động tính toán tồn lý thuyết tại thời điểm duyệt dựa trên công thức:
`expectedOnHandAtApproval = snapshotOnHand + tổng biến động sổ cái phát sinh từ thời điểm snapshot đến thời điểm duyệt`.
Số lượng chênh lệch: `adjustmentQuantity = countedQuantity - expectedOnHandAtApproval`. Hệ thống ghi dòng sổ cái `COUNT_ADJUSTMENT` và điều chỉnh lại số dư kho.

- **Thẩm quyền:** Quyền `inventory:count:approve` (`MANAGER`, `OWNER`).
- **Tiêu đề Yêu cầu (Request Headers):** `Authorization`, `If-Match: "count-v1"`, `Idempotency-Key`, `X-Correlation-Id`, `Content-Type`.
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "approvalNote": "Chấp thuận điều chỉnh hao hụt thực tế 50g hạt cà phê trong quá trình pha chế"
}
```

- **Phản hồi Thành công (`200 OK`):** Trả về `CountSessionResponse` với `status: "APPROVED"` và các dòng sổ cái điều chỉnh đã ghi.

---

### 4.6 Các API Cảnh Báo & Dự Báo Nhu Cầu (Alerts & Forecast P2)

#### `GET /api/v1/inventory/branches/{branchId}/low-stock-alerts`
Lấy danh sách các cảnh báo tồn kho thấp dưới ngưỡng an toàn tại chi nhánh.

- **Thẩm quyền:** Quyền `inventory:read`.
- **Phản hồi Thành công (`200 OK`):**

```json
{
  "data": [
    {
      "id": "0192b300-7f40-7abc-8d2c-123456789012",
      "branchId": "0192b300-7f23-7abc-8d2c-123456789012",
      "ingredientId": "0192b300-7f11-7abc-8d2c-123456789012",
      "ingredientCode": "CONDENSED-MILK",
      "ingredientName": "Sữa đặc có đường",
      "availableQuantity": 800.000,
      "threshold": 1500.000,
      "baseUnit": "ml",
      "status": "OPEN",
      "detectedAt": "2026-10-13T10:15:35Z"
    }
  ],
  "meta": {
    "pagination": { "page": 1, "limit": 20, "total": 1, "totalPages": 1 },
    "requestId": "req_0192b300-7f40-7abc-8d2c-123456789001",
    "correlationId": "0192b300-7f1d-7abc-8d2c-123456789002"
  }
}
```

---

#### `POST /api/v1/inventory/low-stock-alerts/{alertId}/acknowledge`
Tiếp nhận cảnh báo tồn kho thấp (`OPEN -> ACKNOWLEDGED`).

- **Thẩm quyền:** Quyền `inventory:alert:manage` (`MANAGER`, `OWNER`).
- **Phản hồi Thành công (`200 OK`):** Trả về `LowStockAlertResponse` với `status: "ACKNOWLEDGED"`.

---

#### `GET /api/v1/inventory/branches/{branchId}/forecasts` (Giai đoạn P2)
Tra cứu dự báo nhu cầu nhập hàng theo ngày cho từng nguyên liệu dựa trên thuật toán Moving Average có trọng số ngày trong tuần (Day-of-Week Weighted Moving Average).

- **Thẩm quyền:** Quyền `inventory:forecast:read`.
- **Tham số Truy vấn (Query Parameters):** `forecastDate` (`YYYY-MM-DD`, Bắt buộc), `ingredientId` (Tùy chọn).
- **Phản hồi Thành công (`200 OK`):**

```json
{
  "data": [
    {
      "branchId": "0192b300-7f23-7abc-8d2c-123456789012",
      "ingredientId": "0192b300-7f10-7abc-8d2c-123456789012",
      "forecastDate": "2026-10-14",
      "recommendedQuantity": 2400.000,
      "baseUnit": "g",
      "quality": "HIGH",
      "sampleCount": 35,
      "inputWindow": { "from": "2026-09-16", "to": "2026-10-13" },
      "safetyStock": 500.000,
      "safetyStockSource": "TENANT_DEFAULT",
      "modelVersion": "moving-average-weekday-v1",
      "calculatedAt": "2026-10-13T10:00:00Z"
    }
  ],
  "meta": {
    "requestId": "req_0192b300-7f50-7abc-8d2c-123456789001",
    "correlationId": "0192b300-7f1d-7abc-8d2c-123456789002"
  }
}
```

---

## 5. Hợp Đồng Máy Trạng Thái (State Machine Contracts)

### 5.1 Máy Trạng Thái Đơn Giữ Chỗ (Reservation State Machine)

```text
                  [ Step Functions: Reserve ]
                              │
                              ▼
                        ┌───────────┐
                        │ RESERVED  │
                        └─────┬─────┘
                              │
          ┌───────────────────┼───────────────────┐
          │ (Saga Commit:     │ (Saga Release:    │ (Worker quét hết hạn:
          │  Payment Paid)    │  Payment Failed)  │  expiresAt < NOW)
          ▼                   ▼                   ▼
    ┌───────────┐       ┌───────────┐       ┌───────────┐
    │ COMMITTED │       │ RELEASED  │       │  EXPIRED  │
    └───────────┘       └───────────┘       └─────┬─────┘
     (Terminal)          (Terminal)               │
                                                  │ (Commit đến sau khi
                                                  │  đã hết hạn)
                                                  ▼
                                    ┌───────────────────────────┐
                                    │  RECONCILIATION_REQUIRED  │
                                    │ (Tạo case đối soát ngoại lệ│
                                    │  xử lý thủ công)          │
                                    └───────────────────────────┘
```

*Quy tắc bất biến của Reservation:*
- `COMMITTED`, `RELEASED`, `EXPIRED`, `RECONCILIATION_REQUIRED` là các trạng thái kết thúc (Terminal).
- Thử lại lệnh Commit/Release trên reservation đã kết thúc là thao tác No-op trả lại kết quả trước đó nếu cùng mã thanh toán/hash yêu cầu.
- Nếu lệnh Commit gửi tới khi reservation đã chuyển sang `EXPIRED`, hệ thống không tự động trừ kho mà chuyển trạng thái sang `RECONCILIATION_REQUIRED`, ghi bản ghi vào bảng `reconciliation_cases` và phát ra sự kiện `InventoryReconciliationRequired.v1` để nhân viên vận hành xử lý.

---

### 5.2 Máy Trạng Thái Công Thức Định Lượng (Recipe State Machine)

```text
       [ Tạo công thức mới ]
                 │
                 ▼
             ┌───────┐
             │ DRAFT │ ◄────── (Sửa công thức bằng PATCH)
             └───┬───┘
                 │ (Thao tác activate)
                 ▼
            ┌────────┐
            │ ACTIVE │ ──────► (Tự động vô hiệu hóa khi có bản active mới)
            └───┬────┘                    │
                │                         ▼
                │ (Archive)         ┌──────────┐
                │                   │ INACTIVE │
                ▼                   └────┬─────┘
          ┌──────────┐                   │ (Archive)
          │ ARCHIVED │ ◄─────────────────┘
          └──────────┘
```

*Bất biến công thức:* Chỉ có duy nhất một bản ghi mang trạng thái `ACTIVE` cho mỗi cặp `(tenant_id, catalog_item_id, variant_id)` tại một thời điểm (được kiểm soát bằng Partial Unique Index trong PostgreSQL).

---

## 6. Hợp Đồng Sự Kiện Tích Hợp (Integration Event Contracts)

### 6.1 Cấu Trúc Phong Bì Sự Kiện Chuẩn (Standard Event Envelope)

```json
{
  "eventId": "0192b300-7f60-7abc-8d2c-123456789012",
  "eventType": "InventoryReservationCommitted",
  "eventVersion": 1,
  "occurredAt": "2026-10-13T10:15:35Z",
  "tenantId": "0192b300-7f1d-7abc-8d2c-123456789012",
  "branchId": "0192b300-7f23-7abc-8d2c-123456789012",
  "aggregateType": "InventoryReservation",
  "aggregateId": "0192b300-7f35-7abc-8d2c-123456789012",
  "correlationId": "0192b300-7f1d-7abc-8d2c-123456789002",
  "causationId": "0192b300-7f35-7abc-8d2c-123456789002",
  "producer": "inventory-service",
  "payload": {}
}
```

---

### 6.2 Schemas Chi Tiết Từng Sự Kiện

#### 1. `InventoryReserved.v1`
Phát ra khi các nguyên liệu trong đơn hàng đã được giữ chỗ thành công tại chi nhánh.

```json
{
  "reservationId": "0192b300-7f35-7abc-8d2c-123456789012",
  "orderId": "0192b300-7f30-7abc-8d2c-123456789012",
  "checkoutAttemptId": "0192b300-7f32-7abc-8d2c-123456789012",
  "sagaId": "0192b300-7f33-7abc-8d2c-123456789012",
  "branchId": "0192b300-7f23-7abc-8d2c-123456789012",
  "expiresAt": "2026-10-13T10:15:00Z",
  "lines": [
    {
      "ingredientId": "0192b300-7f10-7abc-8d2c-123456789012",
      "quantity": 40.000,
      "baseUnit": "g"
    }
  ]
}
```

---

#### 2. `InventoryReservationCommitted.v1`
Phát ra khi tồn kho đã bị trừ vĩnh viễn và các dòng sổ cái `SALE_COMMITTED` đã được ghi nhận.

```json
{
  "reservationId": "0192b300-7f35-7abc-8d2c-123456789012",
  "orderId": "0192b300-7f30-7abc-8d2c-123456789012",
  "checkoutAttemptId": "0192b300-7f32-7abc-8d2c-123456789012",
  "sagaId": "0192b300-7f33-7abc-8d2c-123456789012",
  "branchId": "0192b300-7f23-7abc-8d2c-123456789012",
  "paymentId": "0192b2d0-7f34-7abc-8d2c-123456789012",
  "committedAt": "2026-10-13T10:15:35Z",
  "ledgerEntryIds": [
    "0192b300-7f36-7abc-8d2c-123456789012"
  ]
}
```

---

#### 3. `InventoryReservationReleased.v1`
Phát ra khi lượng giữ chỗ được giải phóng thành công do hủy đơn hoặc thanh toán thất bại.

```json
{
  "reservationId": "0192b300-7f35-7abc-8d2c-123456789012",
  "orderId": "0192b300-7f30-7abc-8d2c-123456789012",
  "checkoutAttemptId": "0192b300-7f32-7abc-8d2c-123456789012",
  "branchId": "0192b300-7f23-7abc-8d2c-123456789012",
  "reasonCode": "PAYMENT_FAILED",
  "releasedAt": "2026-10-13T10:16:00Z"
}
```

---

#### 4. `InventoryReservationExpired.v1`
Phát ra khi đơn giữ chỗ bị hủy tự động do vượt quá thời hạn `expiresAt`.

```json
{
  "reservationId": "0192b300-7f35-7abc-8d2c-123456789012",
  "orderId": "0192b300-7f30-7abc-8d2c-123456789012",
  "checkoutAttemptId": "0192b300-7f32-7abc-8d2c-123456789012",
  "branchId": "0192b300-7f23-7abc-8d2c-123456789012",
  "expiredAt": "2026-10-13T10:15:00Z",
  "reasonCode": "CHECKOUT_TIMEOUT"
}
```

---

#### 5. `InventoryStockChanged.v1`
Phát ra sau mỗi lần ghi sổ cái thành công để phục vụ đồng bộ mô hình đọc và báo cáo doanh thu/tồn kho (Reporting Service).

```json
{
  "ledgerEntryId": "0192b300-7f36-7abc-8d2c-123456789012",
  "branchId": "0192b300-7f23-7abc-8d2c-123456789012",
  "ingredientId": "0192b300-7f10-7abc-8d2c-123456789012",
  "movementType": "SALE_COMMITTED",
  "quantityDelta": -40.000,
  "onHand": 15000.000,
  "reserved": 1200.000,
  "available": 13800.000,
  "sourceType": "RESERVATION",
  "sourceId": "0192b300-7f35-7abc-8d2c-123456789012",
  "occurredAt": "2026-10-13T10:15:35Z"
}
```

---

#### 6. `InventoryLowStockDetected.v1`
Phát ra khi tồn khả dụng cắt xuống dưới ngưỡng tối thiểu an toàn để kích hoạt thông báo tới quản lý chi nhánh.

```json
{
  "alertId": "0192b300-7f40-7abc-8d2c-123456789012",
  "branchId": "0192b300-7f23-7abc-8d2c-123456789012",
  "ingredientId": "0192b300-7f11-7abc-8d2c-123456789012",
  "available": 800.000,
  "threshold": 1500.000,
  "baseUnit": "ml",
  "detectedAt": "2026-10-13T10:15:35Z",
  "alertState": "OPEN"
}
```

---

#### 7. `InventoryReconciliationRequired.v1`
Phát ra khi phát hiện ngoại lệ tài chính/kho nghiêm trọng (ví dụ: tiền đã trừ thành công nhưng commit tồn kho gửi tới sau khi đơn giữ chỗ đã hết hạn).

```json
{
  "reconciliationCaseId": "0192b300-7f70-7abc-8d2c-123456789012",
  "reservationId": "0192b300-7f35-7abc-8d2c-123456789012",
  "orderId": "0192b300-7f30-7abc-8d2c-123456789012",
  "checkoutAttemptId": "0192b300-7f32-7abc-8d2c-123456789012",
  "branchId": "0192b300-7f23-7abc-8d2c-123456789012",
  "paymentId": "0192b2d0-7f34-7abc-8d2c-123456789012",
  "reasonCode": "COMMIT_AFTER_EXPIRY",
  "detectedAt": "2026-10-13T10:20:00Z"
}
```

---

## 7. Hợp Đồng Cơ Sở Dữ Liệu & Flyway Migrations (PostgreSQL DDL)

Dưới đây là mã DDL đầy đủ cho 7 file migration Flyway của cơ sở dữ liệu độc lập `pos_inventory`:

### `V1__create_inventory_core.sql`
Tạo các bảng nguyên liệu master, quy đổi đơn vị mua hàng và phiên bản công thức định lượng.

```sql
-- V1__create_inventory_core.sql
-- Bounded Context: Inventory Service Core Master Data

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Bảng 1: ingredients - Danh mục nguyên liệu master toàn tenant
CREATE TABLE ingredients (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    base_unit TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 1,
    created_by UUID NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_ingredient_base_unit CHECK (base_unit IN ('g', 'ml', 'unit')),
    CONSTRAINT ck_ingredient_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'ARCHIVED'))
);

-- Bảng 2: ingredient_conversions - Quy đổi từ đơn vị mua hàng sang đơn vị cơ sở
CREATE TABLE ingredient_conversions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    ingredient_id UUID NOT NULL REFERENCES ingredients(id) ON DELETE RESTRICT,
    purchase_unit TEXT NOT NULL,
    base_quantity NUMERIC(18,3) NOT NULL,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_conversion_base_quantity CHECK (base_quantity > 0),
    CONSTRAINT ck_conversion_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

-- Bảng 3: recipe_versions - Phiên bản công thức định lượng món ăn
CREATE TABLE recipe_versions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    catalog_item_id UUID NOT NULL,
    variant_id UUID NOT NULL,
    version INTEGER NOT NULL,
    status TEXT NOT NULL DEFAULT 'DRAFT',
    version_etag TEXT NOT NULL,
    activated_at TIMESTAMPTZ NULL,
    created_by UUID NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_recipe_status CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE', 'ARCHIVED'))
);

-- Bảng 4: recipe_lines - Chi tiết định lượng tiêu hao nguyên liệu cho 1 đơn vị bán
CREATE TABLE recipe_lines (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    recipe_version_id UUID NOT NULL REFERENCES recipe_versions(id) ON DELETE CASCADE,
    ingredient_id UUID NOT NULL REFERENCES ingredients(id) ON DELETE RESTRICT,
    modifier_option_id UUID NULL,
    quantity_per_sale NUMERIC(18,3) NOT NULL,
    waste_factor NUMERIC(18,3) NOT NULL DEFAULT 1.000,
    base_unit TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_recipe_line_quantity CHECK (quantity_per_sale > 0),
    CONSTRAINT ck_recipe_line_waste CHECK (waste_factor >= 1.000)
);
```

---

### `V2__create_stock_and_reservations.sql`
Tạo bảng số dư tồn kho chi nhánh, bảng đơn giữ chỗ và chi tiết dòng giữ chỗ.

```sql
-- V2__create_stock_and_reservations.sql

-- Bảng 5: stock_balances - Số dư tồn kho thực tế và khả dụng theo chi nhánh
CREATE TABLE stock_balances (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    ingredient_id UUID NOT NULL REFERENCES ingredients(id) ON DELETE RESTRICT,
    on_hand NUMERIC(18,3) NOT NULL DEFAULT 0.000,
    reserved NUMERIC(18,3) NOT NULL DEFAULT 0.000,
    available NUMERIC(18,3) NOT NULL DEFAULT 0.000,
    version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_balance_on_hand CHECK (on_hand >= 0),
    CONSTRAINT ck_balance_reserved CHECK (reserved >= 0),
    CONSTRAINT ck_balance_available CHECK (available >= 0),
    CONSTRAINT ck_balance_formula CHECK (available = on_hand - reserved)
);

-- Bảng 6: reservations - Đơn giữ chỗ nguyên liệu trong luồng Checkout Saga
CREATE TABLE reservations (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    order_id UUID NOT NULL,
    checkout_attempt_id UUID NOT NULL,
    saga_id UUID NOT NULL,
    status TEXT NOT NULL DEFAULT 'RESERVED',
    expires_at TIMESTAMPTZ NOT NULL,
    request_hash TEXT NOT NULL,
    idempotency_key TEXT NOT NULL,
    reservation_version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    terminal_at TIMESTAMPTZ NULL,

    CONSTRAINT ck_reservation_status CHECK (status IN ('RESERVED', 'COMMITTED', 'RELEASED', 'EXPIRED', 'RECONCILIATION_REQUIRED')),
    CONSTRAINT ck_reservation_expiry CHECK (expires_at > created_at)
);

-- Bảng 7: reservation_lines - Nhu cầu nguyên liệu chi tiết cho từng dòng giữ chỗ
CREATE TABLE reservation_lines (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    reservation_id UUID NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
    ingredient_id UUID NOT NULL REFERENCES ingredients(id) ON DELETE RESTRICT,
    quantity NUMERIC(18,3) NOT NULL,
    base_unit TEXT NOT NULL,
    source_line_ids JSONB NOT NULL,
    recipe_snapshot JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_res_line_quantity CHECK (quantity > 0)
);
```

---

### `V3__create_ledger_receipt_count.sql`
Tạo bảng sổ cái biến động kho bất biến, bảng phiếu nhập kho và bảng phiên kiểm kê kho.

```sql
-- V3__create_ledger_receipt_count.sql

-- Bảng 8: stock_ledger - Sổ cái biến động kho bất biến (Append-only Ledger)
CREATE TABLE stock_ledger (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    ingredient_id UUID NOT NULL REFERENCES ingredients(id) ON DELETE RESTRICT,
    movement_type TEXT NOT NULL,
    quantity_delta NUMERIC(18,3) NOT NULL,
    base_unit TEXT NOT NULL,
    source_type TEXT NOT NULL,
    source_id UUID NOT NULL,
    reason TEXT NULL,
    actor_id UUID NULL,
    correlation_id UUID NOT NULL,
    idempotency_key TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_ledger_movement_type CHECK (movement_type IN ('OPENING_BALANCE', 'RECEIPT', 'SALE_COMMITTED', 'COUNT_ADJUSTMENT', 'WASTE', 'RETURN')),
    CONSTRAINT ck_ledger_quantity_delta CHECK (quantity_delta <> 0)
);

-- Bảng 9: receipts & receipt_lines - Phiếu nhập kho nguyên liệu
CREATE TABLE receipts (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    receipt_number TEXT NOT NULL,
    supplier_name TEXT NOT NULL,
    invoice_number TEXT NULL,
    note TEXT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE receipt_lines (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    receipt_id UUID NOT NULL REFERENCES receipts(id) ON DELETE CASCADE,
    ingredient_id UUID NOT NULL REFERENCES ingredients(id) ON DELETE RESTRICT,
    purchase_unit TEXT NOT NULL,
    purchase_quantity NUMERIC(18,3) NOT NULL,
    conversion_factor NUMERIC(18,3) NOT NULL,
    base_quantity NUMERIC(18,3) NOT NULL,
    lot_code TEXT NULL,
    expires_at TIMESTAMPTZ NULL,

    CONSTRAINT ck_receipt_line_qty CHECK (purchase_quantity > 0 AND base_quantity > 0)
);

-- Bảng 10: count_sessions & count_session_lines - Phiên kiểm kê kho
CREATE TABLE count_sessions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    status TEXT NOT NULL DEFAULT 'IN_PROGRESS',
    snapshot_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approved_time TIMESTAMPTZ NULL,
    approved_by UUID NULL,
    note TEXT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_count_session_status CHECK (status IN ('IN_PROGRESS', 'SUBMITTED', 'APPROVED', 'CANCELLED'))
);

CREATE TABLE count_session_lines (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    session_id UUID NOT NULL REFERENCES count_sessions(id) ON DELETE CASCADE,
    ingredient_id UUID NOT NULL REFERENCES ingredients(id) ON DELETE RESTRICT,
    snapshot_on_hand NUMERIC(18,3) NOT NULL,
    snapshot_reserved NUMERIC(18,3) NOT NULL,
    snapshot_available NUMERIC(18,3) NOT NULL,
    counted_quantity NUMERIC(18,3) NULL,
    expected_on_hand_at_approval NUMERIC(18,3) NULL,
    adjustment_quantity NUMERIC(18,3) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

---

### `V4__create_alert_reconciliation_forecast.sql`
Tạo bảng ngưỡng cảnh báo tồn thấp, hồ sơ đối soát ngoại lệ và bảng dự báo nhu cầu P2.

```sql
-- V4__create_alert_reconciliation_forecast.sql

-- Bảng 11: low_stock_thresholds & low_stock_alerts - Cảnh báo tồn kho thấp
CREATE TABLE low_stock_thresholds (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    ingredient_id UUID NOT NULL REFERENCES ingredients(id) ON DELETE RESTRICT,
    threshold_quantity NUMERIC(18,3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_threshold_qty CHECK (threshold_quantity >= 0)
);

CREATE TABLE low_stock_alerts (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    ingredient_id UUID NOT NULL REFERENCES ingredients(id) ON DELETE RESTRICT,
    crossing_key TEXT NOT NULL,
    available_at_detection NUMERIC(18,3) NOT NULL,
    threshold_at_detection NUMERIC(18,3) NOT NULL,
    status TEXT NOT NULL DEFAULT 'OPEN',
    acknowledged_by UUID NULL,
    acknowledged_at TIMESTAMPTZ NULL,
    resolved_at TIMESTAMPTZ NULL,
    detected_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_alert_status CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'RESOLVED'))
);

-- Bảng 12: reconciliation_cases - Hồ sơ đối soát ngoại lệ vận hành
CREATE TABLE reconciliation_cases (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    reservation_id UUID NOT NULL REFERENCES reservations(id) ON DELETE RESTRICT,
    order_id UUID NOT NULL,
    checkout_attempt_id UUID NOT NULL,
    payment_id UUID NOT NULL,
    reason_code TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'OPEN',
    operator_id UUID NULL,
    resolution_note TEXT NULL,
    resolved_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_recon_case_status CHECK (status IN ('OPEN', 'UNDER_REVIEW', 'RESOLVED', 'CLOSED'))
);

-- Bảng 13: Bảng cấu hình và kết quả dự báo nhu cầu (P2)
CREATE TABLE forecast_policies (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    branch_id UUID NULL,
    ingredient_id UUID NOT NULL REFERENCES ingredients(id) ON DELETE RESTRICT,
    lead_time_days INTEGER NOT NULL DEFAULT 1,
    safety_stock_quantity NUMERIC(18,3) NOT NULL DEFAULT 0.000,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE forecast_results (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    ingredient_id UUID NOT NULL REFERENCES ingredients(id) ON DELETE RESTRICT,
    forecast_date DATE NOT NULL,
    recommended_quantity NUMERIC(18,3) NOT NULL,
    quality TEXT NOT NULL,
    sample_count INTEGER NOT NULL,
    model_version TEXT NOT NULL,
    calculated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_forecast_quality CHECK (quality IN ('HIGH', 'MEDIUM', 'LOW', 'INSUFFICIENT_HISTORY'))
);
```

---

### `V5__create_outbox_idempotency.sql`
Tạo bảng Transactional Outbox và bảng nhật ký Idempotency.

```sql
-- V5__create_outbox_idempotency.sql

-- Bảng 14: idempotency_records
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

-- Bảng 15: outbox_messages
CREATE TABLE outbox_messages (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    branch_id UUID NULL,
    aggregate_type TEXT NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type TEXT NOT NULL,
    event_version INTEGER NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    publish_status TEXT NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NULL,
    lease_owner TEXT NULL,
    lease_until TIMESTAMPTZ NULL,
    last_error_safe TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_outbox_status CHECK (publish_status IN ('PENDING', 'PUBLISHED', 'DLQ'))
);
```

---

### `V6__add_inventory_unique_constraints.sql`
Thiết lập các ràng buộc duy nhất (Unique Constraints / Partial Unique Indexes) phức hợp.

```sql
-- V6__add_inventory_unique_constraints.sql

-- 1. Mã nguyên liệu duy nhất trong từng tenant
ALTER TABLE ingredients
    ADD CONSTRAINT uq_ingredient_code UNIQUE (tenant_id, code);

-- 2. Chỉ có duy nhất một công thức ACTIVE cho mỗi cặp món/biến thể
CREATE UNIQUE INDEX uq_active_recipe_version
    ON recipe_versions (tenant_id, catalog_item_id, variant_id)
    WHERE status = 'ACTIVE';

-- 3. Số thứ tự phiên bản công thức duy nhất theo cặp món/biến thể
ALTER TABLE recipe_versions
    ADD CONSTRAINT uq_recipe_version_number UNIQUE (tenant_id, catalog_item_id, variant_id, version);

-- 4. Không trùng lặp nguyên liệu/topping trong cùng 1 phiên bản công thức
CREATE UNIQUE INDEX uq_recipe_line_ingredient_modifier
    ON recipe_lines (recipe_version_id, ingredient_id, COALESCE(modifier_option_id, '00000000-0000-0000-0000-000000000000'::uuid));

-- 5. Mỗi nguyên liệu tại một chi nhánh chỉ có duy nhất 1 dòng số dư
ALTER TABLE stock_balances
    ADD CONSTRAINT uq_branch_ingredient_balance UNIQUE (tenant_id, branch_id, ingredient_id);

-- 6. Mỗi lượt checkout attempt chỉ có duy nhất 1 reservation
ALTER TABLE reservations
    ADD CONSTRAINT uq_checkout_attempt_reservation UNIQUE (tenant_id, checkout_attempt_id);

-- 7. Khóa Idempotency duy nhất toàn tenant
ALTER TABLE idempotency_records
    ADD CONSTRAINT uq_inventory_idempotency UNIQUE (tenant_id, actor_or_workload_id, operation, idempotency_key);

-- 8. Chống spam cảnh báo trùng lặp cùng 1 đợt tụt tồn
CREATE UNIQUE INDEX uq_active_low_stock_crossing
    ON low_stock_alerts (tenant_id, branch_id, ingredient_id, crossing_key)
    WHERE status IN ('OPEN', 'ACKNOWLEDGED');
```

---

### `V7__add_inventory_operational_indexes.sql`
Thêm các chỉ mục tối ưu hóa hiệu năng vận hành và chú thích chi tiết từng cột (`COMMENT ON COLUMN`).

```sql
-- V7__add_inventory_operational_indexes.sql

-- 1. Chỉ mục phân trang con trỏ sổ cái kho
CREATE INDEX idx_stock_ledger_cursor
    ON stock_ledger (tenant_id, branch_id, occurred_at DESC, id DESC);

-- 2. Chỉ mục quét các reservation quá hạn
CREATE INDEX idx_reservations_pending_expiry
    ON reservations (tenant_id, branch_id, status, expires_at)
    WHERE status = 'RESERVED';

-- 3. Chỉ mục tiến trình polling outbox
CREATE INDEX idx_outbox_publish_polling
    ON outbox_messages (publish_status, next_attempt_at, created_at)
    WHERE publish_status = 'PENDING';

-- 4. Chú thích bảng và cột
COMMENT ON TABLE ingredients IS 'Bảng danh mục nguyên liệu master cấp tenant.';
COMMENT ON COLUMN stock_balances.available IS 'Số lượng tồn khả dụng có thể bán (available = on_hand - reserved).';
COMMENT ON COLUMN reservations.status IS 'Trạng thái vòng đời giữ chỗ nguyên liệu (RESERVED, COMMITTED, RELEASED, EXPIRED, RECONCILIATION_REQUIRED).';
COMMENT ON TABLE stock_ledger IS 'Sổ cái bất biến chỉ ghi thêm lưu vết toàn bộ biến động kho.';
```

---

## 8. Ma Trận Kiểm Thử Hợp Đồng Bắt Buộc (Contract Test Matrix)

| Mã kiểm thử | Kịch bản kiểm thử | Dữ liệu đầu vào | Kết quả hợp đồng kỳ vọng |
|:---:|---|---|---|
| `TC-INV-01` | Giữ chỗ thành công đủ hàng | 3 dòng món yêu cầu cà phê, sữa, trân châu; tồn khả dụng đủ | Trả về `201 Created`, `status = RESERVED`, tăng `reserved` tại 3 dòng `stock_balances`, phát `InventoryReserved.v1`. |
| `TC-INV-02` | Thiếu 1 nguyên liệu | Cà phê đủ, nhưng sữa đặc thiếu 200ml | Trả về `409 Conflict`, mã lỗi `insufficient_stock`, mảng `shortages[]` chứa ID sữa đặc, không giữ chỗ bất kỳ nguyên liệu nào. |
| `TC-INV-03` | Món chưa có công thức ACTIVE | Gọi giữ chỗ cho món mới chưa kích hoạt công thức | Trả về mã lỗi `422 missing_active_recipe`, từ chối giữ chỗ (nguyên tắc fail-closed). |
| `TC-INV-04` | Commit thành công | `paymentStatus = "SUCCEEDED"`, reservation đang `RESERVED` | Trả về `200 OK`, `status = COMMITTED`, giảm `reserved`, giảm `onHand`, ghi sổ cái `SALE_COMMITTED` số lượng âm, phát `InventoryReservationCommitted.v1`. |
| `TC-INV-05` | Release giải phóng reservation | Saga gọi bồi hoàn khi thanh toán thất bại | Trả về `200 OK`, `status = RELEASED`, giảm `reserved`, phục hồi `available`, phát `InventoryReservationReleased.v1`. |
| `TC-INV-06` | Commit sau khi đã hết hạn | Lệnh commit gửi tới sau khi reservation đã chuyển `EXPIRED` | Trả về `409 Conflict`, chuyển trạng thái reservation sang `RECONCILIATION_REQUIRED`, tạo bản ghi trong `reconciliation_cases`, phát `InventoryReconciliationRequired.v1`. |
| `TC-INV-07` | Tránh Deadlock khi giữ chỗ đồng thời | 2 đơn hàng giữ chỗ cùng lúc các nguyên liệu A và B theo thứ tự ngược nhau | Hệ thống luôn sắp xếp khóa `ingredientId` tăng dần trước khi `SELECT FOR UPDATE`; cả 2 giao dịch hoàn tất tuần tự, không xảy ra deadlock. |
| `TC-INV-08` | Phê duyệt kiểm kê khi có bán hàng đồng thời | Đếm kho bắt đầu lúc 10:00; từ 10:00–10:15 có 5 đơn bán hàng trừ kho | Khi duyệt, tồn lý thuyết tự động tính bù 5 đơn bán hàng; số lượng điều chỉnh `adjustmentQuantity` hoàn toàn chính xác. |
| `TC-INV-09` | Chống phát lặp cảnh báo tồn thấp | Nhiều đơn hàng liên tiếp làm tồn kho giảm sâu dưới ngưỡng | Chỉ phát sinh duy nhất 1 bản ghi cảnh báo `OPEN` nhờ ràng buộc `uq_active_low_stock_crossing`; không spam thông báo. |
| `TC-INV-10` | Kích hoạt 2 công thức active đồng thời | Cố tình kích hoạt 2 công thức cho cùng 1 cặp món/biến thể | Bản ghi kích hoạt sau tự động chuyển bản ghi active trước đó sang `INACTIVE` trong cùng 1 transaction nguyên tử. |
