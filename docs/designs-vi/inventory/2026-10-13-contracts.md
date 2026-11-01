# Module 6 — Dịch Vụ Kho Nguyên Liệu (Inventory Service): Hợp Đồng API & Dữ Liệu (API & Data Contracts)

**Ngày:** 2026-10-13  
**Trạng thái:** Đã phê duyệt có cảnh báo (Approved with warnings)  
**Tài liệu tầm nhìn (Vision):** [Inventory Vision](./2026-10-13-vision.md)  
**Yêu cầu kỹ thuật (Requirements):** [Inventory Requirements](./2026-10-13-requirements.md)  
**Kiến trúc hệ thống (Architecture):** [Inventory Architecture](./2026-10-13-architecture.md)  
**Phạm vi:** M — Luồng Service Tiêu chuẩn (Standard Service Flow)

Tài liệu này là đặc tả chuẩn mực (normative contract) cho toàn bộ REST API công khai và nội bộ của Inventory Service, cấu trúc JSON Schema cho các sự kiện bất đồng bộ, và lược đồ dữ liệu cơ sở dữ liệu PostgreSQL. Quá trình triển khai mã nguồn bắt buộc phải sinh/kiểm tra tính hợp lệ của đặc tả OpenAPI 3.1, AsyncAPI/JSON Schema và các file migration Flyway từ các định nghĩa trong tài liệu này trước khi viết code controller hoặc domain.

---

## 1. Các Nguyên Tắc Thiết Kế Hợp Đồng (Contract Principles)

- **API công khai (Public API):** Sử dụng kiến trúc REST + OpenAPI 3.1 dưới tiền tố `/api/v1/inventory`.
- **API nội bộ liên service (Internal API):** Sử dụng REST + OpenAPI 3.1 dưới tiền tố `/internal/v1/inventory`; bắt buộc xác thực định danh hệ thống (Workload identity / mTLS), tuyệt đối không mở route ra ngoài phía client Angular.
- **Sự kiện bất đồng bộ (Domain Events):** Tuân thủ cấu trúc phong bì (event envelope) của nền tảng kết hợp JSON Schema / AsyncAPI; phân phối theo mô hình ít nhất một lần (at-least-once) và phía consumer thực hiện khử trùng lặp (deduplication) dựa trên `eventId`.
- **Quy chuẩn dữ liệu:** Toàn bộ khóa định danh nội bộ sử dụng UUID v7 dạng chuỗi; dấu thời gian tuân thủ chuẩn ISO-8601 UTC; số lượng lưu trữ thập phân chính xác với 3 chữ số sau dấu phẩy (`NUMERIC(18,3)`).
- **Ngữ cảnh Tenant:** Trích xuất hoàn toàn từ token xác thực của Identity Service hoặc ngữ cảnh workload tin cậy; tuyệt đối không chấp nhận `tenantId` truyền từ request body/query để quyết định quyền sở hữu tài nguyên.
- **Header Chi nhánh (`X-Active-Branch-Id`):** Chỉ đóng vai trò là tham số chọn chi nhánh (branch selector) đã được server xác thực tư cách thành viên; nếu đường dẫn URL có `branchId` thì giá trị này phải trùng khớp với header.
- **Xử lý thử lại (Retry) & Cập nhật phiên bản:** Mọi request làm thay đổi trạng thái có thể thử lại đều bắt buộc có header `Idempotency-Key`; mọi thao tác cập nhật kiểm soát phiên bản lạc quan đều yêu cầu header `If-Match`.
- **Không xóa cứng (No Hard Delete):** Không xóa cứng các bản ghi nguyên liệu, công thức, đơn giữ chỗ, dòng sổ cái, phiên kiểm kê hay cảnh báo đã từng phát sinh tham chiếu lịch sử.
- **Bảo mật truy vấn bộ sưu tập:** OpenAPI không cho phép truyền các trường lọc hoặc sắp xếp tùy ý; mỗi bộ sưu tập (collection) quy định một danh sách trường an toàn (allowlist) riêng.

---

## 2. Tiêu Chuẩn HTTP Chung (Common HTTP Contract)

### 2.1 Các HTTP Header Quy Chuẩn

| Header | Bắt buộc | Ý nghĩa kỹ thuật |
|---|---:|---|
| `Authorization` | API công khai có bảo vệ | `Bearer <Cognito access token>`; Identity Service cung cấp các quyết định phân quyền role, tenant và branch. |
| `X-Correlation-Id` | Khuyến nghị | Mã truy vết phân tán xuyên service; service tự sinh nếu client không truyền và trả lại trong metadata. |
| `X-Active-Branch-Id` | API công khai cấp chi nhánh | Định danh chi nhánh đang thao tác; server kiểm tra quyền hạn của user tại chi nhánh này. |
| `Idempotency-Key` | API thay đổi dữ liệu có thể retry | Khóa chống trùng lặp trong phạm vi `actor/workload + tenant + operation`. |
| `If-Match` | Cập nhật / Phê duyệt có phiên bản | ETag hoặc số phiên bản đọc được từ phản hồi GET trước đó để kiểm soát xung đột ghi đè. |
| `Content-Type` | Yêu cầu có request body | `application/json; charset=utf-8`. |
| `X-Workload-Identity` / mTLS | API nội bộ (`/internal/v1`) | Định danh hệ thống của AWS Step Functions Saga hoặc Scheduler tiến trình nền. |

*Lưu ý bảo mật:* Client người dùng cuối tuyệt đối bị chặn không được gọi vào các endpoint `/internal/v1`. `tenantId` không bao giờ được lấy từ body hoặc query để cấp quyền truy cập.

### 2.2 Định Dạng Phản Hồi Thành Công (Success Envelope)

Phản hồi đơn lẻ (Single resource):
```json
{
  "data": {},
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

Phản hồi danh sách phân trang theo vị trí (Offset pagination):
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
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

Phản hồi danh sách phân trang theo con trỏ (Cursor pagination):
```json
{
  "data": [],
  "meta": {
    "hasMore": true,
    "nextCursor": "eyJvY2N1cnJlZEF0Ijoi...",
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

### 2.3 Định Dạng Phản Hồi Lỗi (Error Envelope)

```json
{
  "error": {
    "code": "insufficient_stock",
    "message": "Required ingredients are not available",
    "details": [
      {
        "field": "shortages[0].ingredientId",
        "code": "available_below_required",
        "message": "The requested quantity cannot be reserved"
      }
    ],
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

*Nguyên tắc an toàn:* Thông báo lỗi trả về cho client phải an toàn, không chứa chi tiết câu lệnh SQL, stack trace, secret, mã JWT, payload thô của nhà cung cấp thanh toán, hay dữ liệu PII nhạy cảm.

### Bảng Mã Lỗi Chuẩn Hóa:

| HTTP Status | Mã lỗi máy đọc (`code`) | Ý nghĩa nghiệp vụ | Hành vi Retry |
|---:|---|---|---|
| 400 | `malformed_request` | JSON sai định dạng hoặc media type không được hỗ trợ | Không retry |
| 401 | `unauthorized` | Thiếu, sai hoặc hết hạn token xác thực | Không retry |
| 403 | `forbidden` | Bị từ chối do vai trò, sai tenant hoặc ngoài phạm vi chi nhánh | Không retry |
| 404 | `not_found` | Không tìm thấy tài nguyên trong phạm vi được phân quyền | Không retry |
| 409 | `duplicate_request` | Cùng idempotency key nhưng khác mã hash payload | Không retry |
| 409 | `recipe_version_conflict` | Xung đột phiên bản công thức cũ khi cập nhật/kích hoạt | Lấy lại dữ liệu mới |
| 409 | `active_recipe_exists` | Xung đột bất biến: đã có công thức khác đang ACTIVE | Làm mới / Retry |
| 409 | `insufficient_stock` | Tồn kho khả dụng không đủ để đáp ứng nhu cầu giữ chỗ | Phía người dùng / Saga quyết định |
| 409 | `reservation_request_conflict` | Cùng checkout attempt nhưng gửi payload giữ chỗ khác | Không retry |
| 409 | `reservation_state_conflict` | Lệnh không hợp lệ với trạng thái kết thúc hiện tại | Không retry |
| 409 | `reservation_expired` | Yêu cầu commit gửi tới sau khi reservation đã hết hạn | Chuyển luồng đối soát |
| 409 | `payment_context_mismatch` | Thông tin thanh toán không khớp với ngữ cảnh checkout | Không retry |
| 409 | `count_session_version_conflict` | Phê duyệt kiểm kê dựa trên snapshot/phiên bản cũ | Tải lại / Kiểm đếm lại |
| 409 | `reconciliation_required` | Không thể hoàn tất Saga tự động an toàn | Chuyển nhân viên / Saga |
| 409 | `archived_resource` | Tài nguyên đã lưu trữ không được phép chỉnh sửa | Không retry |
| 422 | `validation_error` | Vi phạm kiểm tra tính hợp lệ về số lượng, đơn vị, trường dữ liệu | Không retry |
| 422 | `invalid_recipe_line` | Sai phạm vi ingredient/modifier hoặc trùng lặp dòng công thức | Không retry |
| 422 | `invalid_branch_scope` | Sai lệch chi nhánh giữa đường dẫn URL và request body | Không retry |
| 422 | `forecast_insufficient_history` | Chưa có đủ lịch sử tiêu thụ để dự báo nhu cầu | Không retry |
| 429 | `rate_limited` | Bị giới hạn tần suất gọi tại API Gateway hoặc Service | Retry có giới hạn |
| 503 | `lock_timeout` | Quá thời gian chờ khóa dòng sau số lần retry tối đa | Saga / Client retry |
| 503 | `service_unavailable` | Database hoặc Message Broker phụ thuộc tạm thời gián đoạn | Retry có giới hạn |

---

## 3. Hợp Đồng Phân Quyền (Authorization Contract)

| Mã quyền (Permission) | Vai trò mặc định (Role) | Phạm vi áp dụng |
|---|---|---|
| `inventory:read` | Owner, Manager, Cashier, Kitchen | Xem danh mục/số dư theo chi nhánh được cấp quyền; thông tin chi tiết tồn kho nhạy cảm có thể giới hạn cho Manager/Owner. |
| `inventory:recipe:manage` | Owner, Manager | Quản lý công thức định lượng dùng chung toàn tenant. |
| `inventory:stock:write` | Owner, Manager | Thực hiện các biến động kho tại chi nhánh được ủy quyền. |
| `inventory:count:input` | Nhân viên được phân công | Nhập số liệu kiểm đếm tại phiên kiểm kê được chỉ định. |
| `inventory:count:approve` | Owner, Manager | Phê duyệt điều chỉnh kiểm kê tại chi nhánh được ủy quyền. |
| `inventory:alert:manage` | Owner, Manager | Quản lý và xử lý cảnh báo tồn thấp tại chi nhánh. |
| `inventory:forecast:read` | Owner, Manager | Tra cứu dự báo nhu cầu tại chi nhánh được ủy quyền. |
| `inventory:forecast:configure` | Owner, Manager | Owner cấu hình mặc định toàn tenant; Manager ghi đè theo chi nhánh/nguyên liệu. |

---

## 4. Danh Sách API Công Khai (Public API Surface)

### 4.1 Quản Lý Danh Mục Nguyên Liệu (Ingredients)

| Phương thức | Đường dẫn API | Quyền hạn | Request Body | Response Body | HTTP Status |
|---|---|---|---|---|---|
| GET | `/api/v1/inventory/ingredients` | `inventory:read` | Tham số lọc phân trang | `IngredientPage` | 200, 401, 403 |
| POST | `/api/v1/inventory/ingredients` | `inventory:recipe:manage` + Idempotency | `CreateIngredientRequest` | `IngredientResponse` | 201, 403, 409, 422 |
| GET | `/api/v1/inventory/ingredients/{ingredientId}` | `inventory:read` | — | `IngredientResponse` | 200, 403, 404 |
| PATCH | `/api/v1/inventory/ingredients/{ingredientId}` | `inventory:recipe:manage` + `If-Match` | `UpdateIngredientRequest` | `IngredientResponse` | 200, 403, 404, 409, 422 |
| POST | `/api/v1/inventory/ingredients/{ingredientId}/state` | `inventory:recipe:manage` + Idempotency + `If-Match` | `ChangeIngredientStateRequest` | `IngredientResponse` | 200, 403, 404, 409 |

### 4.2 Quản Lý Phiên Bản Công Thức (Recipe Versions)

| Phương thức | Đường dẫn API | Quyền hạn | Request Body | Response Body | HTTP Status |
|---|---|---|---|---|---|
| GET | `/api/v1/inventory/recipes` | `inventory:read` | Tham số lọc phân trang | `RecipePage` | 200, 403 |
| POST | `/api/v1/inventory/recipes` | `inventory:recipe:manage` + Idempotency | `CreateRecipeRequest` | `RecipeResponse` | 201, 403, 409, 422 |
| GET | `/api/v1/inventory/recipes/{recipeVersionId}` | `inventory:read` | — | `RecipeResponse` | 200, 403, 404 |
| PATCH | `/api/v1/inventory/recipes/{recipeVersionId}` | `inventory:recipe:manage` + `If-Match` | `UpdateRecipeRequest` | `RecipeResponse` | 200, 403, 404, 409, 422 |
| POST | `/api/v1/inventory/recipes/{recipeVersionId}/validate` | `inventory:recipe:manage` + `If-Match` | `{}` | `RecipeValidationResponse` | 200, 403, 404, 409 |
| POST | `/api/v1/inventory/recipes/{recipeVersionId}/activate` | `inventory:recipe:manage` + Idempotency + `If-Match` | `{}` | `RecipeResponse` | 200, 403, 404, 409, 422 |
| POST | `/api/v1/inventory/recipes/{recipeVersionId}/archive` | `inventory:recipe:manage` + Idempotency + `If-Match` | `{}` | `RecipeResponse` | 200, 403, 404, 409 |

*Lưu ý:* Lệnh `PATCH` chỉ áp dụng cho công thức ở trạng thái `DRAFT`. Công thức đã kích hoạt muốn sửa phải tạo bản draft mới. Thao tác kích hoạt sẽ tự động vô hiệu hóa bản `ACTIVE` cũ của cùng cặp item/variant.

### 4.3 Tra Cứu Số Dư & Sổ Cái Biến Động Kho (Stock & Ledger)

| Phương thức | Đường dẫn API | Quyền hạn | Request Body | Response Body | HTTP Status |
|---|---|---|---|---|---|
| GET | `/api/v1/inventory/branches/{branchId}/balances` | `inventory:read` | Tham số lọc phân trang | `BalancePage` | 200, 403, 404 |
| GET | `/api/v1/inventory/branches/{branchId}/ledger` | `inventory:read` | Cursor + filters | `StockLedgerCursorPage` | 200, 403, 404 |
| GET | `/api/v1/inventory/branches/{branchId}/reservations` | `inventory:read` | Cursor + filters | `ReservationCursorPage` | 200, 403, 404 |
| POST | `/api/v1/inventory/branches/{branchId}/opening-balances` | `inventory:stock:write` + Idempotency | `CreateOpeningBalanceRequest` | `StockLedgerResponse` | 201, 403, 409, 422 |

*Quy tắc phân trang:* Con trỏ phân trang của sổ cái cố định dựa trên cặp `(occurredAt, id)`, tuyệt đối không cho phép client truyền cú pháp SQL hoặc mệnh đề ORDER BY tùy biến.

### 4.4 Nhập Kho & Phiên Kiểm Kê (Receipts & Count Sessions)

| Phương thức | Đường dẫn API | Quyền hạn | Request Body | Response Body | HTTP Status |
|---|---|---|---|---|---|
| GET | `/api/v1/inventory/branches/{branchId}/receipts` | `inventory:read` | Tham số lọc phân trang | `ReceiptPage` | 200, 403, 404 |
| POST | `/api/v1/inventory/branches/{branchId}/receipts` | `inventory:stock:write` + Idempotency | `CreateReceiptRequest` | `ReceiptResponse` | 201, 403, 409, 422 |
| GET | `/api/v1/inventory/branches/{branchId}/count-sessions` | `inventory:read` | Tham số lọc phân trang | `CountSessionPage` | 200, 403, 404 |
| POST | `/api/v1/inventory/branches/{branchId}/count-sessions` | `inventory:count:input` + Idempotency | `CreateCountSessionRequest` | `CountSessionResponse` | 201, 403, 409, 422 |
| POST | `/api/v1/inventory/count-sessions/{countSessionId}/lines` | `inventory:count:input` + Idempotency | `RecordCountLineRequest` | `CountSessionResponse` | 200, 403, 404, 409, 422 |
| POST | `/api/v1/inventory/count-sessions/{countSessionId}/approve` | `inventory:count:approve` + Idempotency + `If-Match` | `ApproveCountSessionRequest` | `CountSessionResponse` | 200, 403, 404, 409, 422 |
| POST | `/api/v1/inventory/count-sessions/{countSessionId}/cancel` | `inventory:count:approve` + Idempotency + `If-Match` | `CancelCountSessionRequest` | `CountSessionResponse` | 200, 403, 404, 409 |

### 4.5 Cảnh Báo Tồn Kho & Dự Báo (Alerts & Forecast)

| Phương thức | Đường dẫn API | Quyền hạn | Request Body | Response Body | HTTP Status |
|---|---|---|---|---|---|
| GET | `/api/v1/inventory/branches/{branchId}/low-stock-alerts` | `inventory:read` | Tham số lọc phân trang | `LowStockAlertPage` | 200, 403, 404 |
| POST | `/api/v1/inventory/low-stock-alerts/{alertId}/acknowledge` | `inventory:alert:manage` + Idempotency | `AcknowledgeAlertRequest` | `LowStockAlertResponse` | 200, 403, 404, 409 |
| POST | `/api/v1/inventory/low-stock-alerts/{alertId}/resolve` | `inventory:alert:manage` + Idempotency | `ResolveAlertRequest` | `LowStockAlertResponse` | 200, 403, 404, 409 |
| GET | `/api/v1/inventory/branches/{branchId}/forecasts` | `inventory:forecast:read` | Tham số lọc ngày | `ForecastPage` | 200, 403, 404 |
| PUT | `/api/v1/inventory/branches/{branchId}/forecast-policies/{ingredientId}` | `inventory:forecast:configure` + Idempotency + `If-Match` | `UpdateForecastPolicyRequest` | `ForecastPolicyResponse` | 200, 403, 404, 409, 422 |

---

## 5. Danh Sách API Nội Bộ Phục Vụ Saga (Internal Saga API Surface)

| Phương thức | Đường dẫn API | Quyền xác thực | Request Body | Response Body | HTTP Status |
|---|---|---|---|---|---|
| POST | `/internal/v1/inventory/reservations` | Saga Workload Identity | `ReserveInventoryRequest` | `ReservationResponse` | 201, 409, 422, 503 |
| POST | `/internal/v1/inventory/reservations/{reservationId}/commit` | Saga Workload Identity | `CommitReservationRequest` | `ReservationResponse` | 200, 404, 409, 422, 503 |
| POST | `/internal/v1/inventory/reservations/{reservationId}/release` | Saga Workload Identity | `ReleaseReservationRequest` | `ReservationResponse` | 200, 404, 409, 503 |
| POST | `/internal/v1/inventory/availability` | Order / Saga Workload | `AvailabilityRequest` | `AvailabilityResponse` | 200, 403, 422, 503 |

*Nguyên tắc kiến trúc:* Sự kiện `PaymentPaid.v1` được Step Functions Saga lắng nghe và xử lý. Inventory Service tuyệt đối không có endpoint nào tự động trừ kho trực tiếp từ sự kiện đó.

---

## 6. Lược Đồ Dữ Liệu Yêu Cầu (Request Schemas)

### 6.1 Tạo Nguyên Liệu (`CreateIngredientRequest`)

```json
{
  "code": "MILK-WHOLE",
  "name": "Sữa tươi thanh trùng",
  "baseUnit": "ml",
  "purchaseConversions": [
    { "purchaseUnit": "thùng 12 hộp 1L", "baseQuantity": 12000.000 }
  ]
}
```
*Quy tắc:* `code` được chuẩn hóa và là duy nhất trong tenant; `baseUnit ∈ {g, ml, unit}`; `baseQuantity > 0`; nguyên liệu inactive không được dùng trong công thức kích hoạt mới.

### 6.2 Tạo Công Thức Định Lượng (`CreateRecipeRequest`)

```json
{
  "catalogItemId": "01J...",
  "variantId": "01J...",
  "lines": [
    {
      "ingredientId": "01J...",
      "modifierOptionId": null,
      "quantityPerSale": 180.000,
      "wasteFactor": 1.050,
      "baseUnit": "ml"
    }
  ]
}
```
*Quy tắc:* Mỗi dòng bắt buộc có `ingredientId`. `modifierOptionId` là trường scope tùy chọn (nullable). `quantityPerSale > 0`, `wasteFactor >= 1.000`; trùng lặp dòng `(ingredientId, modifierOptionId)` sẽ bị từ chối.

### 6.3 Giữ Chỗ Nguyên Liệu (`ReserveInventoryRequest`)

```json
{
  "sagaId": "01J...",
  "orderId": "01J...",
  "checkoutAttemptId": "01J...",
  "branchId": "01J...",
  "expiresAt": "2026-10-13T10:15:00Z",
  "lines": [
    {
      "lineId": "01J...",
      "catalogItemId": "01J...",
      "variantId": "01J...",
      "modifierOptionIds": ["01J..."],
      "quantity": 2
    }
  ]
}
```
*Quy tắc:* `tenantId` trích xuất từ trusted workload context. Header bắt buộc có `Idempotency-Key`. Số lượng là số nguyên dương `1..999`. Inventory Service tự động phân giải công thức `ACTIVE` và lưu snapshot nhu cầu nguyên liệu.

### 6.4 Xác Nhận Trừ Kho (`CommitReservationRequest`)

```json
{
  "checkoutAttemptId": "01J...",
  "orderId": "01J...",
  "paymentId": "01J...",
  "paymentAttemptId": "01J...",
  "paymentStatus": "SUCCEEDED",
  "paidAmountMinor": 200000,
  "currency": "VND"
}
```
*Quy tắc:* `reservationId` nằm trên đường dẫn URL. Toàn bộ các mã định danh, số tiền và loại tiền tệ phải khớp hoàn toàn với thông tin checkout đã lưu. `paymentStatus` chỉ chấp nhận giá trị duy nhất là `SUCCEEDED`.

### 6.5 Hủy Giữ Chỗ (`ReleaseReservationRequest`)

```json
{
  "checkoutAttemptId": "01J...",
  "orderId": "01J...",
  "reasonCode": "PAYMENT_FAILED",
  "reason": "Nhà cung cấp cổng thanh toán trả về lỗi giao dịch"
}
```
*Mã lý do cho phép:* `PAYMENT_FAILED`, `CHECKOUT_TIMEOUT`, `ORDER_CANCELLED`, `SAGA_COMPENSATION`, `OPERATOR_RECONCILIATION`.

### 6.6 Tra Cứu Khả Năng Đáp Ứng (`AvailabilityRequest`)

```json
{
  "branchId": "01J...",
  "selections": [
    {
      "lineId": "01J...",
      "catalogItemId": "01J...",
      "variantId": "01J...",
      "modifierOptionIds": [],
      "quantity": 1
    }
  ]
}
```
*Quy tắc:* API này chỉ mang tính tham khảo (advisory) và có thể có độ trễ nhỏ. Kết quả trả về `canFulfill`, danh sách thiếu hụt `shortages[]`, phiên bản công thức và thời điểm tính toán. Quyết định giữ chỗ thực tế tại quầy POS luôn do lệnh `Reserve` thẩm định trực tiếp với DB lock.

---

## 7. Lược Đồ Dữ Liệu Phản Hồi (Response Schemas)

### 7.1 Phản Hồi Giữ Chỗ (`ReservationResponse`)

```json
{
  "reservationId": "01J...",
  "status": "RESERVED",
  "orderId": "01J...",
  "checkoutAttemptId": "01J...",
  "branchId": "01J...",
  "expiresAt": "2026-10-13T10:15:00Z",
  "reservationVersion": 1,
  "reservedIngredients": [
    { "ingredientId": "01J...", "quantity": 360.000, "baseUnit": "ml" }
  ],
  "shortages": [],
  "recipeSnapshots": [
    { "catalogItemId": "01J...", "variantId": "01J...", "recipeVersion": 4 }
  ]
}
```
*Trạng thái kết thúc:* `COMMITTED`, `RELEASED`, `EXPIRED`, `RECONCILIATION_REQUIRED`.

### 7.2 Phản Hồi Số Dư Kho (`BalanceResponse`)

```json
{
  "ingredientId": "01J...",
  "branchId": "01J...",
  "baseUnit": "ml",
  "onHand": 12000.000,
  "reserved": 1800.000,
  "available": 10200.000,
  "version": 17,
  "updatedAt": "2026-10-13T10:12:00Z"
}
```

### 7.3 Phản Hồi Lịch Sử Giữ Chỗ (`ReservationCursorPage`)
Sử dụng cấu trúc phân trang con trỏ chuẩn. Mỗi phần tử chứa các trường định danh và vòng đời bất biến: `reservationId`, `orderId`, `checkoutAttemptId`, `branchId`, `status`, `expiresAt`, `reservationVersion`, `createdAt`, `updatedAt`, `terminalAt`, kèm mảng tổng hợp `reservedIngredients` khi caller có quyền xem chi tiết tồn kho.

### 7.4 Phản Hồi Dự Báo Nhu Cầu (`ForecastResponse`)

```json
{
  "branchId": "01J...",
  "ingredientId": "01J...",
  "forecastDate": "2026-10-14",
  "recommendedQuantity": 2400.000,
  "baseUnit": "ml",
  "quality": "HIGH",
  "sampleCount": 35,
  "inputWindow": { "from": "2026-09-16", "to": "2026-10-13" },
  "safetyStock": 500.000,
  "safetyStockSource": "TENANT_DEFAULT",
  "modelVersion": "moving-average-weekday-v1",
  "calculatedAt": "2026-10-13T10:00:00Z",
  "freshnessSeconds": 120
}
```
*Mức chất lượng `INSUFFICIENT_HISTORY`:* Trả về `recommendedQuantity = null` kèm mã trạng thái `forecast_insufficient_history` trong metadata.

---

## 8. Quy Chuẩn Phân Trang, Lọc & Caching

- **Phân trang dạng Offset:** Áp dụng cho các danh mục quản trị: nguyên liệu, công thức, số dư, phiếu nhập, phiên kiểm kê, cảnh báo và cấu hình dự báo.
- **Phân trang dạng Con trỏ (Cursor):** Cố định trên cặp `(occurredAt, id)` cho sổ cái biến động kho và lịch sử giữ chỗ.
- **Giới hạn kích thước trang:** Mặc định là 20 phần tử, tối đa không quá 100 phần tử trên một trang.
- **Bộ lọc an toàn (Allowlist):** Phạm vi tenant/branch, trạng thái (`status`), mã nguyên liệu, món/biến thể công thức, loại giao dịch nguồn, khoảng ngày và trạng thái cảnh báo.
- **Chính sách Caching:** Tuyệt đối không cache dữ liệu số dư, kết quả giữ chỗ hoặc biến động sổ cái ra ngoài public gateway. API tra cứu khả năng đáp ứng (Availability) có thể cache rất ngắn kèm ETag và chỉ số độ tươi rõ ràng.

---

## 9. Hợp Đồng Sự Kiện Bất Đồng Bộ (Event Contract)

### 9.1 Cấu Trúc Phong Bì Chuẩn (Standard Event Envelope)

```json
{
  "eventId": "01J...",
  "eventType": "InventoryReservationCommitted",
  "eventVersion": 1,
  "occurredAt": "2026-10-13T10:15:05Z",
  "tenantId": "01J...",
  "branchId": "01J...",
  "aggregateType": "InventoryReservation",
  "aggregateId": "01J...",
  "aggregateVersion": 2,
  "correlationId": "01J...",
  "causationId": "01J...",
  "producer": "inventory-service",
  "payload": {}
}
```
*Lưu ý:* `branchId` là null đối với các sự kiện danh mục nguyên liệu/công thức toàn tenant, và bắt buộc phải có đối với các sự kiện kho, giữ chỗ, kiểm kê, cảnh báo. Phiên bản sự kiện là bất biến sau khi xuất bản; các phiên bản không tương thích sẽ bị cách ly vào DLQ.

### 9.2 Danh Mục & Cấu Trúc Payload Sự Kiện

| Tên sự kiện | Các trường payload bắt buộc | Các Service lắng nghe (Consumers) |
|---|---|---|
| `InventoryReserved.v1` | `reservationId`, `orderId`, `checkoutAttemptId`, `sagaId`, `branchId`, `expiresAt`, `lines[]`, `recipeSnapshots[]` | Step Functions Saga, Order, Audit |
| `InventoryReservationCommitted.v1` | `reservationId`, `orderId`, `checkoutAttemptId`, `sagaId`, `branchId`, `paymentId`, `lines[]`, `committedAt`, `ledgerEntryIds[]` | Step Functions Saga, Order, Reporting, Audit |
| `InventoryReservationReleased.v1` | `reservationId`, `orderId`, `checkoutAttemptId`, `sagaId`, `branchId`, `reasonCode`, `releasedAt` | Step Functions Saga, Order, Audit |
| `InventoryReservationExpired.v1` | `reservationId`, `orderId`, `checkoutAttemptId`, `sagaId`, `branchId`, `expiredAt`, `reasonCode` | Step Functions Saga, Order, Audit |
| `InventoryStockChanged.v1` | `ledgerEntryId`, `branchId`, `ingredientId`, `movementType`, `quantityDelta`, `onHand`, `reserved`, `available`, `sourceType`, `sourceId` | Reporting, Audit |
| `InventoryLowStockDetected.v1` | `alertId`, `branchId`, `ingredientId`, `available`, `threshold`, `detectedAt`, `alertState` | Reporting, Notification, Audit |
| `InventoryReconciliationRequired.v1` | `reconciliationCaseId`, `reservationId`, `orderId`, `checkoutAttemptId`, `sagaId`, `branchId`, `paymentId`, `reasonCode`, `detectedAt` | Step Functions Saga, Payment, Order, Audit |

---

## 10. Hợp Đồng Dữ Liệu PostgreSQL (PostgreSQL Data Contract)

### 10.1 Quy Ước Chung
- Cơ sở dữ liệu: PostgreSQL / Amazon Aurora PostgreSQL. Khóa chính: UUID v7. Dấu thời gian: `TIMESTAMPTZ` chuẩn UTC. Dữ liệu bán cấu trúc: `JSONB`.
- Các cột số lượng sử dụng `NUMERIC(18,3)` với logic làm tròn `HALF_UP` ở ứng dụng.
- Toàn bộ bảng và cột bắt buộc phải có câu lệnh `COMMENT ON TABLE/COLUMN` mô tả chi tiết bằng tiếng Anh/Việt.
- Mọi bảng đều có `created_at`; các bảng có khả năng cập nhật bổ sung thêm `updated_at` và cột phiên bản `version` phục vụ khóa lạc quan.
- Tuyệt đối không tạo Foreign Key xuyên cơ sở dữ liệu. Mọi ràng buộc FK chỉ tồn tại nội bộ bên trong cơ sở dữ liệu của Inventory Service.

### 10.2 Bảng Danh Mục Nguyên Liệu & Công Thức

#### Bảng `ingredients` (Danh mục nguyên liệu master)
- `id` (UUID, PK): Khóa định danh UUID v7 của nguyên liệu.
- `tenant_id` (UUID, NOT NULL, INDEX): Định danh tenant sở hữu.
- `code` (TEXT, NOT NULL): Mã nguyên liệu duy nhất trong tenant (chuẩn hóa chữ hoa/viết liền).
- `name` (TEXT, NOT NULL): Tên hiển thị của nguyên liệu.
- `base_unit` (TEXT, CHECK `IN ('g', 'ml', 'unit')`): Đơn vị đo lường cơ sở.
- `status` (TEXT, CHECK `IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')`): Trạng thái hoạt động.
- `version` (BIGINT, NOT NULL): Số phiên bản phục vụ kiểm soát khóa lạc quan.
- `created_by` (UUID, NULL): Định danh người tạo.
- `created_at` (TIMESTAMPTZ, NOT NULL): Thời gian tạo UTC.
- `updated_at` (TIMESTAMPTZ, NOT NULL): Thời gian cập nhật lần cuối UTC.

#### Bảng `ingredient_conversions` (Quy đổi đơn vị mua hàng)
- `id` (UUID, PK): Khóa định danh quy đổi.
- `tenant_id` (UUID, NOT NULL): Định danh tenant.
- `ingredient_id` (UUID, FK `ingredients(id)`): Tham chiếu nguyên liệu tương ứng.
- `purchase_unit` (TEXT, NOT NULL): Tên đơn vị mua hàng (ví dụ: "thùng", "hộp", "bao").
- `base_quantity` (NUMERIC(18,3), CHECK `> 0`): Số lượng đơn vị cơ sở tương ứng trong 1 đơn vị mua.
- `version` (BIGINT, NOT NULL): Phiên bản quy đổi.
- `status` (TEXT, CHECK `IN ('ACTIVE', 'INACTIVE')`): Trạng thái áp dụng.
- `created_at`, `updated_at` (TIMESTAMPTZ, NOT NULL): Dấu thời gian vòng đời.

#### Bảng `recipe_versions` (Phiên bản công thức định lượng)
- `id` (UUID, PK): Khóa định danh phiên bản công thức.
- `tenant_id` (UUID, NOT NULL): Định danh tenant sở hữu.
- `catalog_item_id` (UUID, NOT NULL): Tham chiếu opaque ID món ăn từ Catalog Service.
- `variant_id` (UUID, NOT NULL): Tham chiếu opaque ID biến thể món từ Catalog Service.
- `version` (INTEGER, NOT NULL): Số thứ tự phiên bản tăng dần theo từng cặp item/variant.
- `status` (TEXT, CHECK `IN ('DRAFT', 'ACTIVE', 'INACTIVE', 'ARCHIVED')`): Trạng thái công thức.
- `version_etag` (TEXT, NOT NULL): Chuỗi token kiểm soát xung đột ghi đè API (`If-Match`).
- `activated_at` (TIMESTAMPTZ, NULL): Thời điểm kích hoạt công thức.
- `created_by` (UUID, NULL): Người tạo phiên bản.
- `created_at`, `updated_at` (TIMESTAMPTZ, NOT NULL): Dấu thời gian vòng đời.

#### Bảng `recipe_lines` (Chi tiết dòng định lượng nguyên liệu)
- `id` (UUID, PK): Khóa định danh dòng công thức.
- `tenant_id` (UUID, NOT NULL): Định danh tenant.
- `recipe_version_id` (UUID, FK `recipe_versions(id)`): Tham chiếu phiên bản công thức cha.
- `ingredient_id` (UUID, FK `ingredients(id)`): Tham chiếu nguyên liệu tiêu hao.
- `modifier_option_id` (UUID, NULL): Tham chiếu tùy chọn thêm/topping (null nếu thuộc món gốc).
- `quantity_per_sale` (NUMERIC(18,3), CHECK `> 0`): Định lượng tiêu hao cho 1 đơn vị bán.
- `waste_factor` (NUMERIC(18,3), CHECK `>= 1.000`): Hệ số hao hụt trong chế biến (mặc định 1.000).
- `base_unit` (TEXT, NOT NULL): Bản chụp đơn vị đo cơ sở tại thời điểm lập công thức.
- `created_at` (TIMESTAMPTZ, NOT NULL): Thời gian tạo UTC.

### 10.3 Bảng Số Dư Tồn Kho & Giữ Chỗ

#### Bảng `stock_balances` (Số dư tồn kho chi nhánh)
- `id` (UUID, PK): Khóa định danh dòng số dư.
- `tenant_id` (UUID, NOT NULL): Định danh tenant.
- `branch_id` (UUID, NOT NULL): Định danh chi nhánh sở hữu kho.
- `ingredient_id` (UUID, FK `ingredients(id)`): Tham chiếu nguyên liệu.
- `on_hand` (NUMERIC(18,3), CHECK `>= 0`): Tồn kho vật lý thực tế.
- `reserved` (NUMERIC(18,3), CHECK `>= 0`): Số lượng đang bị giữ chỗ cho các đơn hàng.
- `available` (NUMERIC(18,3), CHECK `>= 0`): Tồn khả dụng có thể bán tiếp (`on_hand - reserved`).
- `version` (BIGINT, NOT NULL): Số phiên bản biến động phục vụ kiểm tra tính toàn vẹn.
- `created_at`, `updated_at` (TIMESTAMPTZ, NOT NULL): Dấu thời gian vòng đời.
*Ràng buộc:* Bắt buộc thỏa mãn `available = on_hand - reserved`; Unique index trên bộ ba `(tenant_id, branch_id, ingredient_id)`.

#### Bảng `reservations` (Thông tin giữ chỗ nguyên liệu đơn hàng)
- `id` (UUID, PK): Khóa định danh đơn giữ chỗ.
- `tenant_id` (UUID, NOT NULL): Định danh tenant.
- `branch_id` (UUID, NOT NULL): Chi nhánh bán hàng.
- `order_id` (UUID, NOT NULL): Tham chiếu đơn hàng từ Order Service.
- `checkout_attempt_id` (UUID, NOT NULL): Khóa nghiệp vụ duy nhất cho mỗi lượt checkout.
- `saga_id` (UUID, NOT NULL): Tham chiếu workflow điều phối của Step Functions Saga.
- `status` (TEXT, CHECK `IN ('RESERVED', 'COMMITTED', 'RELEASED', 'EXPIRED', 'RECONCILIATION_REQUIRED')`): Trạng thái vòng đời.
- `expires_at` (TIMESTAMPTZ, CHECK `> created_at`): Thời điểm hết hạn hiệu lực của đơn giữ chỗ.
- `request_hash` (TEXT, NOT NULL): Mã hash chuẩn hóa của payload giữ chỗ ban đầu.
- `idempotency_key` (TEXT, NOT NULL): Khóa chống trùng lặp tầng giao vận.
- `reservation_version` (BIGINT, NOT NULL): Phiên bản chuyển trạng thái.
- `created_at`, `updated_at` (TIMESTAMPTZ, NOT NULL): Dấu thời gian cập nhật.
- `terminal_at` (TIMESTAMPTZ, NULL): Thời điểm chuyển sang trạng thái kết thúc (terminal).

#### Bảng `reservation_lines` (Chi tiết nhu cầu nguyên liệu giữ chỗ)
- `id` (UUID, PK): Khóa định danh dòng giữ chỗ.
- `tenant_id` (UUID, NOT NULL): Định danh tenant.
- `reservation_id` (UUID, FK `reservations(id)`): Tham chiếu đơn giữ chỗ cha.
- `ingredient_id` (UUID, FK `ingredients(id)`): Tham chiếu nguyên liệu giữ chỗ.
- `quantity` (NUMERIC(18,3), CHECK `> 0`): Số lượng nguyên liệu giữ chỗ theo đơn vị cơ sở.
- `base_unit` (TEXT, NOT NULL): Đơn vị cơ sở.
- `source_line_ids` (JSONB, NOT NULL): Mảng ID các dòng món ăn đóng góp vào nhu cầu này.
- `recipe_snapshot` (JSONB, NOT NULL): Bản chụp bất biến của phiên bản công thức đã áp dụng.
- `created_at` (TIMESTAMPTZ, NOT NULL): Thời gian tạo UTC.

#### Bảng `stock_ledger` (Sổ cái biến động kho bất biến)
- `id` (UUID, PK): Khóa định danh dòng sổ cái UUID v7.
- `tenant_id` (UUID, NOT NULL): Định danh tenant.
- `branch_id` (UUID, NOT NULL): Chi nhánh phát sinh biến động.
- `ingredient_id` (UUID, FK `ingredients(id)`): Tham chiếu nguyên liệu.
- `movement_type` (TEXT, CHECK `IN ('OPENING_BALANCE', 'RECEIPT', 'SALE_COMMITTED', 'COUNT_ADJUSTMENT', 'WASTE', 'RETURN')`): Loại biến động.
- `quantity_delta` (NUMERIC(18,3), CHECK `<> 0`): Số lượng biến động có dấu (+/-).
- `base_unit` (TEXT, NOT NULL): Đơn vị cơ sở.
- `source_type` (TEXT, NOT NULL): Loại nguồn (`ORDER`, `RESERVATION`, `RECEIPT`, `COUNT`, `MANUAL`).
- `source_id` (UUID, NOT NULL): Tham chiếu mã nghiệp vụ phát sinh.
- `reason` (TEXT, NULL): Lý do nghiệp vụ (bắt buộc với thao tác thủ công, kiểm kê, hao hụt).
- `actor_id` (UUID, NULL): Người thực hiện (null nếu do hệ thống Saga tự động).
- `correlation_id` (UUID, NOT NULL): Mã truy vết luồng phân tán.
- `occurred_at` (TIMESTAMPTZ, NOT NULL): Thời điểm phát sinh nghiệp vụ UTC.
- `idempotency_key` (TEXT, NOT NULL): Khóa chống trùng lặp tác dụng phụ.
- `created_at` (TIMESTAMPTZ, NOT NULL): Thời điểm ghi vào database UTC.

### 10.4 Bảng Nhập Kho, Kiểm Kê, Cảnh Báo & Đối Soát

#### Bảng `receipts` & `receipt_lines` (Phiếu nhập kho)
- Quản lý thông tin phiếu nhập kho nguyên liệu tại chi nhánh, số hóa đơn nhà cung cấp, lý do nhập, người thực hiện.
- Chi tiết dòng phiếu nhập lưu trữ số lượng mua, đơn vị mua, hệ số quy đổi, số lượng cơ sở quy đổi, và metadata tùy chọn về mã lô (`lot_code`) và hạn dùng (`expires_at`).

#### Bảng `count_sessions` & `count_session_lines` (Phiên kiểm kê kho)
- Lưu trữ snapshot số dư tồn vật lý (`snapshot_on_hand`), giữ chỗ (`snapshot_reserved`), và khả dụng (`snapshot_available`) tại thời điểm bắt đầu phiên kiểm đếm.
- Quá trình kiểm đếm ghi nhận số lượng thực tế (`counted_quantity`). Khi phê duyệt, hệ thống tính toán tồn lý thuyết tại thời điểm duyệt (`expected_on_hand_at_approval`) dựa trên snapshot cộng trừ các biến động phát sinh sau snapshot, từ đó sinh số lượng điều chỉnh (`adjustment_quantity`) để ghi sổ cái `COUNT_ADJUSTMENT`.

#### Bảng `low_stock_thresholds` & `low_stock_alerts` (Ngưỡng & Cảnh báo tồn thấp)
- Quản lý cấu hình mức tồn khả dụng tối thiểu theo từng chi nhánh/nguyên liệu.
- Khi tồn khả dụng cắt qua ngưỡng, hệ thống tạo bản ghi cảnh báo với trạng thái `OPEN`, hỗ trợ người quản lý tiếp nhận (`ACKNOWLEDGED`) và đánh dấu giải quyết (`RESOLVED`), kèm khóa chống lặp cảnh báo (`crossing_key`).

#### Bảng `reconciliation_cases` (Hồ sơ đối soát ngoại lệ)
- Lưu trữ các trường hợp thanh toán thành công nhưng không thể trừ kho tự động, hoặc commit sau khi đơn giữ chỗ đã hết hạn.
- Ghi nhận đầy đủ mã đơn hàng, mã thanh toán, mã giữ chỗ, trạng thái an toàn cuối cùng, nguyên nhân lỗi (`reason_code`), và lịch trình đánh giá của đội ngũ vận hành.

### 10.5 Bảng Dự Báo Nhu Cầu Nhập Hàng (P2 Forecast)
- Bảng `forecast_policies`: Cấu hình thời gian giao hàng NCC (lead time), định mức tồn an toàn (safety stock) mặc định toàn tenant hoặc theo chi nhánh.
- Bảng `forecast_runs`: Nhật ký các đợt chạy tính toán dự báo nhu cầu.
- Bảng `forecast_results`: Kết quả dự báo số lượng đề xuất nhập cho từng ngày mục tiêu kèm chỉ số chất lượng dữ liệu (`HIGH`, `MEDIUM`, `LOW`, `INSUFFICIENT_HISTORY`), số ngày mẫu khảo sát và phiên bản thuật toán.

### 10.6 Bảng Hạ Tầng Độ Tin Cậy Dùng Chung

#### Bảng `outbox_messages` (Transactional Outbox)
- Lưu trữ toàn bộ các sự kiện domain được commit cùng transaction nghiệp vụ.
- Hỗ trợ các cột phục vụ worker phân phối: `publish_status` (`PENDING`, `PUBLISHED`, `FAILED`), `attempt_count`, `next_attempt_at`, `lease_owner`, `lease_until`.

#### Bảng `idempotency_records` (Sổ nhật ký chống trùng lặp)
- Lưu trữ kết quả phản hồi của các thao tác làm thay đổi dữ liệu theo bộ khóa: `(tenant_id, actor_or_workload_id, operation, idempotency_key)`.
- Ghi nhận `request_hash`, mã trạng thái HTTP ban đầu, nội dung phản hồi `response_body` (JSONB) và thời hạn hết hạn lưu trữ (`expires_at`).

---

## 11. Các Ràng Buộc Toàn Vẹn & Chỉ Mục Cơ Sở Dữ Liệu

### 11.1 Ràng Buộc Toàn Vẹn Cục Bộ (Constraints)
- Chỉ thiết lập ràng buộc khóa ngoại (Foreign Key) nội bộ bên trong cơ sở dữ liệu của Inventory Service; tuyệt đối không tạo FK liên service.
- Bảng `stock_balances`: Bắt buộc `available = on_hand - reserved` và các chỉ số `>= 0`.
- Bảng `stock_ledger`: `quantity_delta <> 0`; loại biến động quy định dấu âm hoặc dương.
- Bảng `recipe_versions`: Partial unique index `(tenant_id, catalog_item_id, variant_id) WHERE status = 'ACTIVE'` (chỉ có duy nhất 1 bản active tại một thời điểm).
- Bảng `reservations`: Unique index `(tenant_id, checkout_attempt_id)` (mỗi lượt checkout chỉ có duy nhất 1 reservation).
- Bảng `stock_balances`: Unique index `(tenant_id, branch_id, ingredient_id)`.
- Bảng `idempotency_records`: Unique index `(tenant_id, actor_or_workload_id, operation, idempotency_key)`.
- Bảng `stock_ledger`: Unique index `(tenant_id, branch_id, source_type, source_id, idempotency_key)`.
- Bảng `low_stock_alerts`: Partial unique index `(tenant_id, branch_id, ingredient_id, crossing_key) WHERE status IN ('OPEN', 'ACKNOWLEDGED')`.

### 11.2 Các Chỉ Mục Cơ Bản Phục Vụ Hiệu Năng (Baseline Indexes)

| Bảng | Định nghĩa Index | Mục đích sử dụng |
|---|---|---|
| `ingredients` | `(tenant_id, status, code)` | Tra cứu và lọc danh mục nguyên liệu theo tenant. |
| `recipe_versions` | `(tenant_id, catalog_item_id, variant_id, status, version DESC)` | Phân giải nhanh phiên bản công thức ACTIVE và tra cứu lịch sử. |
| `recipe_lines` | `(tenant_id, recipe_version_id, ingredient_id)` | Mở rộng công thức và thẩm định các dòng nguyên liệu. |
| `stock_balances` | UNIQUE `(tenant_id, branch_id, ingredient_id)` | Khóa dòng và cập nhật số dư tồn kho tại chi nhánh. |
| `reservations` | `(tenant_id, branch_id, status, expires_at)` | Tiến trình nền quét các reservation quá hạn để hủy tự động. |
| `reservations` | UNIQUE `(tenant_id, checkout_attempt_id)` | Kiểm tra idempotency và quyền sở hữu lượt checkout. |
| `reservation_lines` | `(tenant_id, reservation_id, ingredient_id)` | Trừ kho và giải phóng tồn kho theo từng nguyên liệu. |
| `stock_ledger` | `(tenant_id, branch_id, ingredient_id, occurred_at DESC, id DESC)` | Phân trang con trỏ và tra cứu lịch sử biến động sổ cái. |
| `stock_ledger` | UNIQUE `(tenant_id, source_type, source_id, idempotency_key)` | Chống trùng lặp tác dụng phụ ghi sổ cái. |
| `count_sessions` | `(tenant_id, branch_id, status, created_at DESC)` | Danh sách quản trị phiên kiểm kê và luồng duyệt. |
| `low_stock_alerts` | `(tenant_id, branch_id, status, detected_at DESC)` | Hàng đợi cảnh báo và dashboard giám sát tồn kho thấp. |
| `reconciliation_cases` | `(tenant_id, status, next_review_at)` | Hàng đợi đối soát xử lý ngoại lệ vận hành. |
| `forecast_results` | `(tenant_id, branch_id, ingredient_id, forecast_date DESC)` | Truy vấn nhanh số liệu dự báo nhu cầu nhập hàng. |
| `outbox_messages` | `(publish_status, next_attempt_at, created_at)` | Tiến trình Outbox Publisher quét các sự kiện sẵn sàng gửi. |
| `outbox_messages` | `(lease_until) WHERE publish_status = 'PENDING'` | Thu hồi các lease bị quá hạn của các worker bị treo/crash. |

---

## 12. Thứ Tự Script Migration Flyway

Thư mục quản lý migration: `classpath:db/migration/inventory/`.

| Phiên bản | Tên file Migration Script | Nội dung chi tiết |
|---|---|---|
| **V1** | `V1__create_inventory_core.sql` | Khởi tạo extension UUID v7, bảng nguyên liệu (`ingredients`), bảng quy đổi (`ingredient_conversions`), bảng công thức (`recipe_versions`, `recipe_lines`), cùng các câu lệnh comment và ràng buộc kiểm tra cơ sở. |
| **V2** | `V2__create_stock_and_reservations.sql` | Khởi tạo bảng số dư (`stock_balances`), bảng giữ chỗ (`reservations`, `reservation_lines`), các ràng buộc kiểm tra số lượng không âm và chỉ mục phục vụ khóa dòng. |
| **V3** | `V3__create_ledger_receipt_count.sql` | Khởi tạo bảng sổ cái bất biến (`stock_ledger`), phiếu nhập kho (`receipts`, `receipt_lines`), phiên kiểm kê (`count_sessions`, `count_session_lines`) và các ràng buộc nguồn. |
| **V4** | `V4__create_alert_reconciliation_forecast.sql` | Khởi tạo bảng ngưỡng cảnh báo (`low_stock_thresholds`), cảnh báo tồn thấp (`low_stock_alerts`), hồ sơ đối soát (`reconciliation_cases`), và các bảng dự báo nhu cầu (`forecast_policies`, `forecast_runs`, `forecast_results`). |
| **V5** | `V5__create_outbox_idempotency.sql` | Khởi tạo bảng Transactional Outbox (`outbox_messages`) và bảng lưu vết Idempotency (`idempotency_records`) cùng các chỉ mục phục vụ phân phối sự kiện. |
| **V6** | `V6__add_inventory_unique_constraints.sql` | Bổ sung các ràng buộc duy nhất phức hợp: partial unique active recipe, unique checkout attempt, unique stock balance, và unique alert crossing. |
| **V7** | `V7__add_inventory_operational_indexes.sql` | Bổ sung các chỉ mục tối ưu hóa vận hành: phân trang con trỏ sổ cái, quét dọn hết hạn, hàng đợi đối soát và truy vấn dự báo. |

---

## 13. Cơ Chế Phân Phối Sự Kiện & Thử Lại (Delivery & Retry Contract)

- Lệnh chèn vào bảng Outbox được thực thi nguyên tử trong cùng transaction cơ sở dữ liệu với nghiệp vụ kho.
- Tiến trình Outbox Publisher giành quyền xử lý (lease lock) trên các bản ghi `PENDING`, phát hành lên AWS EventBridge theo mô hình at-least-once, và chuyển trạng thái sang `PUBLISHED` khi broker xác nhận thành công.
- Retry sử dụng thuật toán exponential backoff có jitter; các lỗi nghiêm trọng về cấu trúc dữ liệu hoặc xác thực sẽ chuyển vào hàng đợi chết DLQ để can thiệp thủ công.
- Phía Consumer kiểm tra phiên bản sự kiện và metadata tenant/chi nhánh trước khi áp dụng thay đổi.
- Sự kiện bị gửi trùng lặp sẽ được xử lý dạng no-op nhờ bảng Inbox hoặc kiểm tra khóa nghiệp vụ nguồn.

---

## 14. Ma Trận Kiểm Thử Hợp Đồng Bắt Buộc (Contract Test Matrix)

| Khu vực kiểm thử | Các kịch bản và fixture kiểm thử bắt buộc |
|---|---|
| **Xác thực & Phân quyền (Auth/Scope)** | Thiếu JWT Token, sai vai trò người dùng, user bị thu hồi quyền chi nhánh, giả mạo ID xuyên tenant (IDOR), truy cập sai chi nhánh, và sai lệch workload identity nội bộ. |
| **Nguyên liệu & Công thức** | Trùng lặp mã nguyên liệu trong tenant, kích hoạt công thức chứa nguyên liệu inactive, đơn vị đo không hợp lệ, sai header `If-Match`, cố tình kích hoạt 2 công thức cho cùng một món, và sửa đổi công thức đã archive. |
| **Giữ chỗ (Reserve)** | Mở rộng công thức trà sữa kèm topping, tổng hợp các nguyên liệu dùng chung trong cùng đơn hàng, từ chối khi thiếu công thức (fail-closed), rollback toàn bộ khi thiếu 1 nguyên liệu, retry cùng request trả kết quả cũ, và xung đột khi gửi khác hash payload. |
| **Xử lý đồng thời (Concurrency)** | Nhiều POS cùng giữ chỗ nguyên liệu giới hạn, tự động retry khi gặp deadlock, xử lý quá thời gian chờ khóa (lock timeout), tranh chấp giữa Commit và Release, tranh chấp giữa Commit và Expiry, và kích hoạt công thức mới đồng thời với giữ chỗ. |
| **Trừ kho & Hủy (Commit/Release)** | Sai lệch ngữ cảnh thanh toán (Payment context mismatch), retry trên reservation đã kết thúc, bồi hoàn giải phóng reservation, tính duy nhất của dòng sổ cái `SALE_COMMITTED`, và kiểm chứng Inventory không tự tiện commit từ sự kiện thanh toán thô. |
| **Sổ cái & Tái tạo số dư** | Kiểm tra dấu âm/dương của các loại biến động, kiểm tra checksum khi rebuild projection từ sổ cái, kiểm chứng không bao giờ có tồn khả dụng âm, và tính bất biến chỉ ghi thêm của sổ cái. |
| **Kiểm kê kho (Count)** | Công thức tính delta dựa trên snapshot cộng trừ biến động sau snapshot, kiểm kê đồng thời với bán hàng tại POS, từ chối phê duyệt phiên bản cũ (stale approval), và chặn điều chỉnh làm âm tồn kho thực tế. |
| **Cảnh báo tồn thấp** | Kích hoạt cảnh báo đúng khi cắt qua ngưỡng, chống spam lặp lại cùng một đợt tụt tồn, tự động mở lại cảnh báo sau khi nhập thêm hàng, và kiểm tra phạm vi tenant/chi nhánh. |
| **Dự báo nhu cầu** | Kiểm thử các bậc dữ liệu mẫu (0 ngày, 1–13 ngày, 14–27 ngày, trên 28 ngày), hiệu chỉnh theo thứ trong tuần, ghi đè tồn an toàn theo chi nhánh, và phản hồi mã `forecast_insufficient_history`. |
| **Độ tin cậy hạ tầng** | Rollback outbox khi transaction kho thất bại, retry phân phối sự kiện, xử lý sự kiện trùng lặp, cách ly phiên bản sự kiện lạ vào DLQ, và bảo toàn correlation ID xuyên suốt hệ thống. |
| **Tuân thủ chuẩn API** | Thẩm định toàn diện schema request/response/error theo OpenAPI 3.1, kiểm tra allowlist phân trang, giới hạn kích thước trang tối đa, và ngăn chặn triệt để việc truyền câu lệnh SQL thô. |

---

## 15. Tiêu Chuẩn Nghiệm Thu Hợp Đồng Trước Khi Triển Khai (Handoff Gate)

Cổng kiểm soát chuyển giao sang giai đoạn lập kế hoạch triển khai yêu cầu hoàn thành:
1. File đặc tả `openapi.yaml` cho cả Public API và Internal Saga API của Inventory Service.
2. File AsyncAPI / JSON Schemas cho toàn bộ 7 sự kiện nghiệp vụ của Inventory Service.
3. Bộ dữ liệu mẫu (fixtures) cho các kịch bản giữ chỗ, trừ kho, bồi hoàn hủy, hết hạn, thiếu hàng và đối soát ngoại lệ.
4. Cơ chế tự động kiểm tra tính hợp lệ của mã nguồn sinh ra (client/server contract validation) trong pipeline CI/CD.
5. Bộ kiểm thử hướng người tiêu thụ (Consumer-driven contract tests) cho các service phụ thuộc: Step Functions Saga, Order Service, Reporting Service, Audit Service và Notification Service.
6. Nguyên tắc tương thích: Các thay đổi trong cùng một phiên bản sự kiện chỉ được phép mang tính bổ sung (additive); việc xóa trường hoặc thay đổi ngữ nghĩa bắt buộc phải nâng phiên bản sự kiện (`v2`) và có kế hoạch migration chi tiết.
