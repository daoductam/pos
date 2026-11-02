# Hợp đồng Dữ liệu & API: Order Service (Dịch vụ Đơn hàng)

## Nguồn tài liệu và Quyền hạn Hợp đồng

- [Tầm nhìn Order (Vision)](./2026-10-12-vision.md)
- [Yêu cầu Kỹ thuật Order (Requirements)](./2026-10-12-requirements.md)
- [Kiến trúc Order (Architecture)](./2026-10-12-architecture.md)
- [Hợp đồng Nền tảng POS](../pos-platform/2026-10-12-contracts.md)
- [Hợp đồng Identity & Access](../identity-access/2026-10-12-contracts.md)
- [Hợp đồng Catalog](../catalog/2026-10-12-contracts.md)

Tài liệu này là hợp đồng quy chuẩn (normative contract) cho các API công khai của Order, các callback Saga nội bộ, dữ liệu do Order sở hữu và các sự kiện vòng đời đơn hàng. Việc triển khai thực tế bắt buộc phải tạo ra các artifact OpenAPI 3.1 và AsyncAPI/JSON Schema từ các định nghĩa này trước khi viết controller hoặc các DTO Angular.

---

## Các Nguyên tắc Hợp đồng (Contract Principles)

- Toàn bộ API đồng bộ công khai sử dụng chuẩn **RESTful + OpenAPI 3.1** dưới tiền tố `/api/v1/orders`.
- Các API giao tiếp giữa các service (service-to-service) sử dụng REST + OpenAPI 3.1 dưới tiền tố `/internal/v1/orders`, bắt buộc phải có chứng chỉ Workload Identity / mTLS và tuyệt đối không được đưa vào bảng định tuyến của Angular.
- Các sự kiện sử dụng cấu trúc bao bọc (envelope) chuẩn của nền tảng và JSON Schema/AsyncAPI. Cơ chế chuyển phát là ít nhất một lần (at-least-once); các consumer khử trùng lặp theo `eventId`.
- Order sở hữu vòng đời đơn hàng, trạng thái dòng món, các snapshot Catalog bất biến, snapshot tổng tiền và các lượt thử thanh toán (checkout attempts). Catalog, Inventory, Payment, Loyalty và Audit vẫn là các bên sở hữu riêng biệt.
- Các định danh (ID) do Order quản lý là chuỗi UUID v7. Mốc thời gian là chuỗi ISO-8601 UTC trong API và kiểu `TIMESTAMPTZ` trong PostgreSQL.
- Tiền tệ là số nguyên không âm `int64` tính theo đơn vị minor VND (`subtotalMinor`, `grandTotalMinor`), trong đó 1 minor tương đương 1 VND nguyên. Tuyệt đối không chấp nhận tiền tệ dấu phẩy động (float).
- Ngữ cảnh tenant được thiết lập bởi Identity; Order không đưa vào header `X-Active-Tenant-Id`. Header `X-Active-Branch-Id` chọn một chi nhánh đã được máy chủ xác thực cho các thao tác theo phạm vi chi nhánh. Mọi đơn hàng bắt buộc phải gắn cố định với một chi nhánh cụ thể.
- Mọi lệnh công khai đều mang `X-Correlation-Id`; mọi tác dụng phụ có thể thử lại đều mang `Idempotency-Key`; mọi biến đổi đơn nháp có phiên bản đều mang `If-Match`.
- Sự kiện `OrderPaid.v1` là sự thật đơn hàng đã thanh toán chuẩn mực (canonical paid-order fact) cho Loyalty và Reporting. Sự kiện `PaymentPaid.v1` của Payment Service chỉ phục vụ Saga orchestration và không đủ điều kiện để tích điểm hoặc ghi nhận doanh thu trực tiếp.
- Tuyệt đối không có endpoint `DELETE /orders/{id}` hay xóa cứng dòng món trong P0. Việc hủy dòng món active cuối cùng là con đường duy nhất để chuyển trạng thái Order sang `CANCELLED`.

---

## Hợp đồng HTTP Chung (Common HTTP Contract)

### Các Header HTTP

| Header | Bắt buộc | Ý nghĩa và Quy ước sử dụng |
|---|:---:|---|
| `Authorization` | Bắt buộc cho Public API | `Bearer <Cognito access token>`; tư cách thành viên và quyền hạn hiện tại từ Identity là nguồn thẩm quyền tối cao. |
| `X-Active-Branch-Id` | Thao tác công khai theo chi nhánh | Chi nhánh do client chọn; được xác thực đối chiếu với tư cách thành viên hiện tại. Chủ sở hữu (Owner) bắt buộc phải chọn chi nhánh cho các thao tác đơn hàng. |
| `X-Correlation-Id` | Khuyến nghị (Tự sinh nếu thiếu) | Tương quan yêu cầu, Saga, audit, outbox và các lệnh gọi hạ nguồn; tự động sinh nếu vắng mặt. |
| `Idempotency-Key` | Bắt buộc cho các lệnh ghi có thể retry | Lệnh tạo/checkout/tính lại giá/hủy/thử lại; có phạm vi actor + tenant + operation. Cùng một khóa bắt buộc phải mang cùng một request hash. |
| `If-Match` | Bắt buộc khi cập nhật tài nguyên | Mã ETag của đơn hàng từ lần đọc gần nhất (ví dụ: `"order-v7"`). Bắt buộc cho các cập nhật metadata, sửa dòng, hủy món, tính lại giá và checkout. |
| `Content-Type` | Bắt buộc với request có body | `application/json; charset=utf-8`. |

---

### Cấu trúc Bao bì Chuẩn hóa (Standard Envelope)

#### Phản hồi Thành công Đối tượng Đơn lẻ (Single Entity Response)

```json
{
  "data": {},
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

#### Phản hồi Danh sách Phân trang theo Con trỏ (Cursor Pagination)

Do lịch sử đơn hàng mang đặc tính chủ yếu là nối thêm liên tục (append-heavy), Order Service sử dụng phân trang theo con trỏ (Cursor Pagination):

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

Tham số `limit` mặc định là 20 và tối đa là 100. Các trường lọc/sắp xếp nằm trong danh sách trắng (allowlist) và các mệnh đề phạm vi được áp dụng trước khi phân trang.

#### Phản hồi Lỗi (Error Envelope)

```json
{
  "error": {
    "code": "catalog_changed",
    "message": "Thực đơn đã thay đổi sau khi bản nháp này được tính giá",
    "details": [
      {
        "field": "lines[0]",
        "code": "price_changed",
        "message": "Làm mới và xác nhận snapshot thực đơn mới"
      }
    ],
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

#### Bảng Quy tắc Mã Lỗi của Order Service

| HTTP Status | Mã lỗi (Code) | Ngữ cảnh sử dụng trong Order |
|---:|---|---|
| 401 | `unauthorized` | Thiếu, không hợp lệ hoặc hết hạn Cognito JWT. |
| 403 | `forbidden` | Vai trò, tư cách thành viên, phạm vi tenant hoặc chi nhánh không được phép (ví dụ: Cashier cố tình hủy món). |
| 404 | `not_found` | Tài nguyên không tồn tại trong phạm vi được ủy quyền; không làm lộ sự tồn tại chéo tenant. |
| 409 | `conflict` | Xung đột trạng thái chung. |
| 409 | `catalog_changed` | Lựa chọn/giá/phiên bản Catalog hiện tại khác với snapshot trong đơn nháp; yêu cầu xem trước và xác nhận reprice. |
| 409 | `catalog_version_conflict` | Bộ phân giải Catalog không thể cung cấp một lựa chọn hiện tại nhất quán. |
| 409 | `order_version_conflict` | Header `If-Match` đã bị lỗi thời so với phiên bản hiện tại trên database. |
| 409 | `order_not_editable` | Đơn hàng đang ở trạng thái `CHECKOUT_PENDING`, `PAID` hoặc `CANCELLED`. |
| 409 | `line_not_cancellable` | Trạng thái dòng món hoặc đơn hàng không cho phép hủy (dòng đã hủy từ trước hoặc đơn đã thanh toán). |
| 409 | `order_no_active_lines` | Đơn hàng không có dòng món nào đang active để thực hiện checkout. |
| 409 | `checkout_in_progress` | Đã tồn tại một lượt checkout attempt chưa kết thúc cho đơn hàng này. |
| 409 | `compensation_incomplete` | Quy trình bù trừ Saga trước đó chưa hoàn tất an toàn; lệnh thử lại bị khóa. |
| 409 | `payment_reconciliation_required`| Tác dụng phụ của Payment/Inventory cần nhân viên vận hành đối soát thủ công. |
| 409 | `duplicate_request` | Idempotency key bị tái sử dụng với một request hash khác. |
| 409 | `saga_callback_stale` | Callback không khớp với trạng thái attempt hiện tại; được xử lý như một thao tác no-op an toàn. |
| 422 | `validation_error` | Payload hoặc dữ liệu nghiệp vụ đầu vào không hợp lệ. |
| 422 | `invalid_service_mode` | Chế độ phục vụ (`COUNTER`, `TAKEAWAY`, `TABLE`) hoặc ngữ cảnh bàn không hợp lệ. |
| 422 | `invalid_line_selection` | Lựa chọn món/biến thể/modifier Catalog không hợp lệ hoặc không tồn tại. |
| 422 | `invalid_cash_amount` | Số tiền mặt nhận được nhỏ hơn tổng tiền thanh toán bất biến (`cashReceivedMinor < grandTotalMinor`). |
| 422 | `checkout_total_invalid` | Tổng tiền âm, bị trộn lẫn loại tiền tệ hoặc không nhất quán với snapshot dòng món. |
| 429 | `rate_limited` | Vượt quá giới hạn tốc độ tại Gateway hoặc service. |
| 503 | `authorization_unavailable` | Không thể phân giải an toàn quyết định phân quyền từ Identity (áp dụng Fail-Closed). |
| 503 | `dependency_unavailable` | Phụ thuộc Catalog, Inventory, Payment hoặc Step Functions không khả dụng sau khi đã thử lại có giới hạn. |

---

## Hợp đồng Phân quyền và Phạm vi (Scope)

Order gọi sang Identity Access để lấy quyết định hiện tại và kiểm tra lặp lại quyền sở hữu tại repository:

| Quyền hạn | Vai trò sở hữu | Phạm vi (Scope) | Các thao tác Order được phép |
|---|---|---|---|
| `order:read` | `OWNER`, `MANAGER`, `CASHIER` | Chi nhánh được chọn / phân công | Đọc một đơn hoặc danh sách đơn hàng được ủy quyền. |
| `order:create` | `OWNER`, `MANAGER`, `CASHIER` | Chi nhánh được chọn / phân công | Tạo đơn nháp và thêm (các) dòng món ban đầu. |
| `order:edit` | `OWNER`, `MANAGER`, `CASHIER` | Chi nhánh được chọn / phân công, `DRAFT` | Sửa metadata, thêm/sửa dòng món, tính lại giá đơn nháp. |
| `order:checkout` | `OWNER`, `MANAGER`, `CASHIER` | Chi nhánh được chọn / phân công | Bắt đầu hoặc thử lại quy trình checkout. |
| `order:cancel-line`| `OWNER`, `MANAGER` | Chi nhánh được chọn / phân công, `DRAFT` | **Chỉ dành cho Quản lý / Chủ chuỗi:** Hủy dòng món active kèm lý do và audit log. |
| `order:resolve` | Workload nội bộ của Order/Saga | Tenant/chi nhánh/đơn hàng tường minh | Dành riêng cho các callback nội bộ và hoàn tất đơn hàng. |

*Lưu ý bảo mật:* Thu ngân (`CASHIER`) tuyệt đối **không có quyền `order:cancel-line`**. Thao tác hủy món trên POS bắt buộc phải có sự phê duyệt của Quản lý (`MANAGER`) hoặc Chủ chuỗi (`OWNER`).

---

## Bề mặt API Công khai (Public API Surface Summary)

| Phương thức | Đường dẫn (Path) | Quyền hạn (Scope) | Idempotent | If-Match | Mã HTTP Thành công |
|---|---|---|:---:|:---:|---|
| **POST** | `/api/v1/orders` | `order:create` | Bắt buộc | Không | `201 Created` |
| **GET** | `/api/v1/orders` | `order:read` | Không | Không | `200 OK` |
| **GET** | `/api/v1/orders/{orderId}` | `order:read` | Không | Không | `200 OK` |
| **PATCH** | `/api/v1/orders/{orderId}` | `order:edit` | Bắt buộc | Bắt buộc | `200 OK` |
| **POST** | `/api/v1/orders/{orderId}/lines` | `order:edit` | Bắt buộc | Bắt buộc | `201 Created` |
| **PATCH** | `/api/v1/orders/{orderId}/lines/{lineId}` | `order:edit` | Bắt buộc | Bắt buộc | `200 OK` |
| **POST** | `/api/v1/orders/{orderId}/lines/{lineId}/cancel` | `order:cancel-line` (`OWNER`, `MANAGER`) | Bắt buộc | Bắt buộc | `200 OK` |
| **POST** | `/api/v1/orders/{orderId}/reprice` | `order:edit` | Bắt buộc | Bắt buộc | `200 OK` |
| **POST** | `/api/v1/orders/{orderId}/checkout` | `order:checkout` | Bắt buộc | Bắt buộc | `200 OK` / `202 Accepted` |
| **GET** | `/api/v1/orders/{orderId}/checkout-attempts` | `order:read` | Không | Không | `200 OK` |
| **GET** | `/api/v1/orders/{orderId}/checkout-attempts/{attemptId}` | `order:read` | Không | Không | `200 OK` |
| **POST** | `/api/v1/orders/{orderId}/hold` (P1) | `order:edit` | Bắt buộc | Bắt buộc | `200 OK` |
| **POST** | `/api/v1/orders/{orderId}/resume` (P1) | `order:edit` | Bắt buộc | Bắt buộc | `200 OK` |
| **POST** | `/api/v1/orders/{orderId}/splits` (P1) | `order:edit` | Bắt buộc | Bắt buộc | `201 Created` |
| **POST** | `/api/v1/orders/merge` (P1) | `order:edit` | Bắt buộc | Bắt buộc | `200 OK` |
| **GET** | `/api/v1/branches/{branchId}/tables` (P1) | `order:read` | Không | Không | `200 OK` |
| **POST** | `/api/v1/orders/{orderId}/kitchen-tickets` (P1) | `order:edit` | Bắt buộc | Bắt buộc | `201 Created` |

---

## Chi tiết Hợp đồng Từng API Công khai (Public API Details)

### 1. Tạo Đơn hàng Nháp (Create Order)

#### `POST /api/v1/orders`
Khởi tạo một đơn hàng mới ở trạng thái `DRAFT` kèm theo một hoặc nhiều dòng món ban đầu. Yêu cầu này được Order gửi sang Catalog Service (`POST /internal/v1/catalog/resolutions`) để phân giải và chụp lại snapshot bất biến (tên món, biến thể, phụ thu modifier, đơn giá) trước khi commit vào Order DB.

- **Thẩm quyền:** Quyền `order:create`.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `X-Active-Branch-Id: <uuid>` (Bắt buộc)
  - `Idempotency-Key: <unique-key>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Khuyến nghị)
  - `Content-Type: application/json; charset=utf-8` (Bắt buộc)
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "serviceMode": "TABLE",
  "tableReference": "A03",
  "orderNote": "Ít đá, mang ra cùng lúc",
  "customerId": null,
  "lines": [
    {
      "catalogItemId": "0192b2d0-7f1e-7abc-8d2c-123456789012",
      "variantId": "0192b2d0-7f20-7abc-8d2c-123456789012",
      "modifierOptionIds": ["0192b2d0-7f22-7abc-8d2c-123456789012"],
      "quantity": 2,
      "note": "Không hành"
    }
  ]
}
```

*Quy cách kiểm tra (Validation):*
- `serviceMode`: `COUNTER` (tại quầy), `TAKEAWAY` (mang về), `TABLE` (tại bàn). Bất biến sau khi tạo.
- `tableReference`: 1–64 ký tự Unicode (bắt buộc khi `serviceMode = 'TABLE'`, bắt buộc `null` khi là COUNTER/TAKEAWAY).
- `orderNote`: Chuỗi tối đa 500 ký tự (tùy chọn hoặc `null`).
- `customerId`: UUID của khách hàng thành viên Loyalty (tùy chọn).
- `lines`: Mảng chứa từ 1 đến 100 dòng món. Mỗi phần tử chứa `catalogItemId`, `variantId`, `modifierOptionIds` (mảng UUID không trùng), `quantity` (1–999), và `note` (tối đa 250 ký tự).

- **Phản hồi Thành công (`201 Created`):**

```json
{
  "data": {
    "id": "0192b2d0-7f30-7abc-8d2c-123456789012",
    "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
    "branchId": "0192b2d0-7f23-7abc-8d2c-123456789012",
    "orderNumber": "A03-000127",
    "serviceMode": "TABLE",
    "tableReference": "A03",
    "status": "DRAFT",
    "orderNote": "Ít đá, mang ra cùng lúc",
    "customerId": null,
    "lines": [
      {
        "id": "0192b2d0-7f31-7abc-8d2c-123456789012",
        "catalogItemId": "0192b2d0-7f1e-7abc-8d2c-123456789012",
        "catalogVariantId": "0192b2d0-7f20-7abc-8d2c-123456789012",
        "itemName": "Cà phê sữa",
        "variantName": "STANDARD",
        "modifiers": [
          {
            "catalogGroupId": "0192b2d0-7f21-7abc-8d2c-123456789012",
            "catalogOptionId": "0192b2d0-7f22-7abc-8d2c-123456789012",
            "groupName": "Topping",
            "optionName": "Trân châu đen",
            "priceDeltaMinor": 5000
          }
        ],
        "baseUnitPriceMinor": 29000,
        "modifierUnitPriceMinor": 5000,
        "unitPriceMinor": 34000,
        "quantity": 2,
        "note": "Không hành",
        "status": "ACTIVE",
        "catalogVersion": 18,
        "version": 1,
        "cancelledAt": null,
        "cancelReason": null
      }
    ],
    "totals": {
      "subtotalMinor": 68000,
      "vatMinor": 0,
      "discountMinor": 0,
      "grandTotalMinor": 68000,
      "currency": "VND",
      "pricingPolicyVersion": 1
    },
    "catalogVersion": 18,
    "version": 1,
    "currentCheckoutAttempt": null,
    "createdAt": "2026-10-12T09:00:00Z",
    "updatedAt": "2026-10-12T09:00:00Z"
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

*Lưu ý về Header phản hồi:* Trả về `ETag: "order-v1"`.

- **Mã lỗi khả dĩ:**
  - `401 Unauthorized`, `403 Forbidden` (sai chi nhánh).
  - `409 Conflict` (`duplicate_request`): Trùng `Idempotency-Key` nhưng nội dung body khác.
  - `422 Unprocessable Entity` (`invalid_service_mode`, `invalid_line_selection`): Món không tồn tại hoặc tùy chọn modifier vi phạm quy tắc.
  - `503 Service Unavailable` (`dependency_unavailable`): Không gọi được sang Catalog Service.

---

### 2. Danh sách & Chi tiết Đơn hàng (List & Get Order)

#### `GET /api/v1/orders`
Lấy danh sách đơn hàng tại chi nhánh theo cơ chế phân trang con trỏ (Cursor Pagination).

- **Thẩm quyền:** Quyền `order:read`.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `X-Active-Branch-Id: <uuid>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Tùy chọn)
- **Tham số Truy vấn (Query Parameters):**
  - `status` (string, Tùy chọn): `DRAFT`, `CHECKOUT_PENDING`, `PAID`, `CHECKOUT_FAILED`, `CANCELLED`.
  - `serviceMode` (string, Tùy chọn): `COUNTER`, `TAKEAWAY`, `TABLE`.
  - `tableReference` (string, Tùy chọn): Lọc theo tên bàn.
  - `from` / `to` (ISO-8601 UTC, Tùy chọn): Lọc theo khoảng thời gian tạo đơn.
  - `limit` (integer, Mặc định: `20`, Tối đa: `100`): Số lượng bản ghi.
  - `cursor` (string, Tùy chọn): Con trỏ chuỗi mờ từ lần gọi trước.
- **Phản hồi Thành công (`200 OK`):**

```json
{
  "data": [
    {
      "id": "0192b2d0-7f30-7abc-8d2c-123456789012",
      "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
      "branchId": "0192b2d0-7f23-7abc-8d2c-123456789012",
      "orderNumber": "A03-000127",
      "serviceMode": "TABLE",
      "tableReference": "A03",
      "status": "DRAFT",
      "totals": {
        "subtotalMinor": 68000,
        "grandTotalMinor": 68000,
        "currency": "VND"
      },
      "linesCount": 1,
      "version": 1,
      "createdAt": "2026-10-12T09:00:00Z",
      "updatedAt": "2026-10-12T09:00:00Z"
    }
  ],
  "meta": {
    "hasMore": false,
    "nextCursor": null,
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

---

#### `GET /api/v1/orders/{orderId}`
Xem chi tiết một đơn hàng, bao gồm danh sách toàn bộ dòng món (cả active và cancelled) và thông tin checkout attempt gần nhất.

- **Thẩm quyền:** Quyền `order:read` tại chi nhánh của đơn hàng.
- **Tiêu đề Yêu cầu (Request Headers):** `Authorization`, `X-Active-Branch-Id`, `X-Correlation-Id`.
- **Tham số Đường dẫn (Path Parameters):** `orderId` (UUID, Bắt buộc).
- **Phản hồi Thành công (`200 OK`):** Trả về `OrderResponse` kèm header `ETag: "order-v{version}"`.
- **Mã lỗi khả dĩ:** `401 Unauthorized`, `403 Forbidden`, `404 Not Found`.

---

### 3. Cập nhật Đơn hàng Nháp (Update Order & Lines)

Mọi thao tác chỉnh sửa chỉ được phép thực hiện khi đơn hàng đang ở trạng thái `DRAFT`. Nếu đơn hàng đang `CHECKOUT_PENDING`, `PAID` hoặc `CANCELLED`, hệ thống sẽ từ chối với lỗi `409 order_not_editable`.

#### `PATCH /api/v1/orders/{orderId}`
Cập nhật metadata của đơn nháp (`orderNote`, `tableReference`). Các trường `serviceMode` và `branchId` là bất biến và không thể sửa đổi.

- **Thẩm quyền:** Quyền `order:edit`.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `If-Match: "order-v1"` (Bắt buộc)
  - `Idempotency-Key: <unique-key>` (Bắt buộc)
  - `X-Active-Branch-Id: <uuid>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Khuyến nghị)
  - `Content-Type: application/json; charset=utf-8` (Bắt buộc)
- **Tham số Đường dẫn (Path Parameters):** `orderId` (UUID, Bắt buộc).
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "orderNote": "Khách dặn giao nhanh trước 10 phút",
  "tableReference": "A05"
}
```

- **Phản hồi Thành công (`200 OK`):** Trả về `OrderResponse` với `version: 2` và `ETag: "order-v2"`.
- **Mã lỗi khả dĩ:** `403 Forbidden`, `404 Not Found`, `409 order_not_editable`, `409 order_version_conflict`, `422 validation_error`.

---

#### `POST /api/v1/orders/{orderId}/lines`
Thêm một dòng món mới vào đơn hàng nháp hiện có. Gọi sang Catalog resolver để nạp snapshot và tự động tính toán lại các khoản tổng.

- **Thẩm quyền:** Quyền `order:edit`.
- **Tiêu đề Yêu cầu (Request Headers):** `Authorization`, `If-Match: "order-v2"`, `Idempotency-Key`, `X-Active-Branch-Id`, `X-Correlation-Id`, `Content-Type`.
- **Tham số Đường dẫn (Path Parameters):** `orderId` (UUID, Bắt buộc).
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "catalogItemId": "0192b2d0-7f1e-7abc-8d2c-123456789012",
  "variantId": "0192b2d0-7f20-7abc-8d2c-123456789012",
  "modifierOptionIds": [],
  "quantity": 1,
  "note": "Ít ngọt"
}
```

- **Phản hồi Thành công (`201 Created`):** Trả về `OrderResponse` với dòng món mới được bổ sung và tổng tiền cập nhật.
- **Mã lỗi khả dĩ:** `403 Forbidden`, `404 Not Found`, `409 order_version_conflict`, `422 invalid_line_selection`.

---

#### `PATCH /api/v1/orders/{orderId}/lines/{lineId}`
Chỉnh sửa số lượng, ghi chú hoặc tùy chọn modifier của một dòng món đang `ACTIVE`.

- **Thẩm quyền:** Quyền `order:edit`.
- **Tiêu đề Yêu cầu (Request Headers):** `Authorization`, `If-Match: "order-v3"`, `Idempotency-Key`, `X-Active-Branch-Id`, `X-Correlation-Id`, `Content-Type`.
- **Tham số Đường dẫn (Path Parameters):**
  - `orderId` (UUID, Bắt buộc)
  - `lineId` (UUID, Bắt buộc)
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "quantity": 3,
  "note": "Không đá"
}
```

- **Phản hồi Thành công (`200 OK`):** Trả về `OrderResponse` cập nhật.
- **Mã lỗi khả dĩ:** `403 Forbidden`, `404 Not Found`, `409 order_version_conflict`, `422 validation_error`.

---

#### `POST /api/v1/orders/{orderId}/lines/{lineId}/cancel`
Hủy một dòng món trong đơn hàng nháp. **Chỉ dành riêng cho Chủ chuỗi (`OWNER`) hoặc Quản lý chi nhánh (`MANAGER`)**; Thu ngân (`CASHIER`) bị từ chối với lỗi `403 Forbidden`. Bất biến nghiệp vụ: Hủy dòng món active cuối cùng sẽ tự động chuyển trạng thái của toàn bộ đơn hàng sang `CANCELLED`.

- **Thẩm quyền:** Quyền `order:cancel-line` (`OWNER`, `MANAGER`).
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `If-Match: "order-v4"` (Bắt buộc)
  - `Idempotency-Key: <unique-key>` (Bắt buộc)
  - `X-Active-Branch-Id: <uuid>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Khuyến nghị)
  - `Content-Type: application/json; charset=utf-8` (Bắt buộc)
- **Tham số Đường dẫn (Path Parameters):**
  - `orderId` (UUID, Bắt buộc)
  - `lineId` (UUID, Bắt buộc)
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "reason": "Khách hàng đổi ý muốn đổi sang món trà trái cây"
}
```

*Quy cách kiểm tra:* `reason` từ 1 đến 500 ký tự, bắt buộc để lưu audit log.

- **Phản hồi Thành công (`200 OK`):** Trả về `OrderResponse` với dòng món mang `status: "CANCELLED"`, `cancelledAt`, `cancelReason` và tổng tiền được tính lại (chỉ tính các dòng còn active).
- **Mã lỗi khả dĩ:**
  - `403 Forbidden` (`forbidden`): Thu ngân tự ý gọi endpoint hủy món.
  - `404 Not Found` (`not_found`): Không tìm thấy dòng món.
  - `409 Conflict` (`line_not_cancellable`): Dòng món đã bị hủy từ trước hoặc đơn không ở trạng thái `DRAFT`.
  - `409 Conflict` (`order_version_conflict`): Không khớp `If-Match`.

---

#### `POST /api/v1/orders/{orderId}/reprice`
Xem trước hoặc xác nhận tính lại giá đơn hàng nháp khi Catalog thực đơn có sự thay đổi.

- **Thẩm quyền:** Quyền `order:edit`.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `If-Match: "order-v5"` (Bắt buộc khi `confirm = true`)
  - `Idempotency-Key: <unique-key>` (Bắt buộc khi `confirm = true`)
  - `X-Active-Branch-Id: <uuid>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Khuyến nghị)
  - `Content-Type: application/json; charset=utf-8` (Bắt buộc)
- **Tham số Đường dẫn (Path Parameters):** `orderId` (UUID, Bắt buộc).
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "confirm": true,
  "expectedCatalogVersion": 19
}
```

*Cơ chế hoạt động:*
- `confirm = false`: Chế độ xem trước (Dry-run). Trả về bản so sánh chênh lệch giá (diff) mà không làm thay đổi dữ liệu đơn hàng.
- `confirm = true`: Chế độ xác nhận. Cập nhật toàn bộ snapshot món/giá và tổng tiền đơn hàng sang phiên bản Catalog mới nhất một cách nguyên tử.

- **Phản hồi Thành công (`200 OK`):**

```json
{
  "data": {
    "orderId": "0192b2d0-7f30-7abc-8d2c-123456789012",
    "confirmed": true,
    "previousGrandTotalMinor": 68000,
    "newGrandTotalMinor": 72000,
    "currency": "VND",
    "catalogVersion": 19,
    "changedLinesCount": 1,
    "order": {
      "id": "0192b2d0-7f30-7abc-8d2c-123456789012",
      "status": "DRAFT",
      "version": 6
    }
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

- **Mã lỗi khả dĩ:** `403 Forbidden`, `404 Not Found`, `409 catalog_changed`, `409 order_version_conflict`.

---

### 4. Quy trình Thanh toán (Checkout Saga)

#### `POST /api/v1/orders/{orderId}/checkout`
Bắt đầu thanh toán cho đơn hàng. Hỗ trợ phương thức Tiền mặt (`CASH`) và VNPAY QR (`VNPAY_QR`). Thao tác này kích hoạt luồng Saga điều phối phân tán (AWS Step Functions Saga Orchestration) phối hợp giữa Order, Inventory và Payment Service.

- **Thẩm quyền:** Quyền `order:checkout`.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `If-Match: "order-v6"` (Bắt buộc - ETag của đơn nháp)
  - `Idempotency-Key: <unique-key>` (Bắt buộc)
  - `X-Active-Branch-Id: <uuid>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Khuyến nghị)
  - `Content-Type: application/json; charset=utf-8` (Bắt buộc)
- **Tham số Đường dẫn (Path Parameters):** `orderId` (UUID, Bắt buộc).
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "paymentMethod": "CASH",
  "cashReceivedMinor": 100000,
  "returnUrl": null,
  "clientReference": "POS-01-SHIFT-02"
}
```

*Hoặc với VNPAY QR:*

```json
{
  "paymentMethod": "VNPAY_QR",
  "cashReceivedMinor": null,
  "returnUrl": "https://pos.coffeechain.com/checkout-result",
  "clientReference": "POS-01-SHIFT-02"
}
```

*Quy cách kiểm tra:*
- `paymentMethod`: `CASH` hoặc `VNPAY_QR`.
- `cashReceivedMinor`: Bắt buộc với `CASH` và phải `>= grandTotalMinor` của đơn hàng; bắt buộc `null` với `VNPAY_QR`.
- `returnUrl`: Bắt buộc với `VNPAY_QR` (phải thuộc whitelist URL của hệ thống); bắt buộc `null` với `CASH`.

- **Phản hồi Thành công đối với Tiền mặt (`200 OK` - Hoàn tất ngay lập tức):**

```json
{
  "data": {
    "orderId": "0192b2d0-7f30-7abc-8d2c-123456789012",
    "attemptId": "0192b2d0-7f32-7abc-8d2c-123456789012",
    "sagaId": "0192b2d0-7f33-7abc-8d2c-123456789012",
    "orderStatus": "PAID",
    "attemptStatus": "COMPLETED",
    "paymentId": "0192b2d0-7f34-7abc-8d2c-123456789012",
    "reservationId": "0192b2d0-7f35-7abc-8d2c-123456789012",
    "grandTotalMinor": 72000,
    "currency": "VND",
    "cashReceivedMinor": 100000,
    "cashChangeMinor": 28000,
    "qrPayload": null,
    "expiresAt": null
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

- **Phản hồi Thành công đối với VNPAY QR (`202 Accepted` - Chờ thanh toán qua IPN):**

```json
{
  "data": {
    "orderId": "0192b2d0-7f30-7abc-8d2c-123456789012",
    "attemptId": "0192b2d0-7f32-7abc-8d2c-123456789012",
    "sagaId": "0192b2d0-7f33-7abc-8d2c-123456789012",
    "orderStatus": "CHECKOUT_PENDING",
    "attemptStatus": "PAYMENT_PENDING",
    "paymentId": "0192b2d0-7f34-7abc-8d2c-123456789012",
    "reservationId": "0192b2d0-7f35-7abc-8d2c-123456789012",
    "grandTotalMinor": 72000,
    "currency": "VND",
    "cashReceivedMinor": null,
    "cashChangeMinor": null,
    "qrPayload": "00020101021238540010A00000072701240006970422...",
    "expiresAt": "2026-10-12T09:25:00Z"
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

- **Mã lỗi khả dĩ:**
  - `403 Forbidden`, `404 Not Found`.
  - `409 Conflict` (`checkout_in_progress`): Đã có một lượt checkout attempt chưa kết thúc cho đơn này.
  - `409 Conflict` (`compensation_incomplete`): Lượt checkout trước đó đang bù trừ chưa xong; tạm khóa thanh toán lại.
  - `409 Conflict` (`catalog_changed`): Giá món đã đổi trong Catalog; yêu cầu gọi endpoint reprice để xác nhận trước khi thanh toán.
  - `409 Conflict` (`order_no_active_lines`): Đơn không có dòng món nào active.
  - `409 Conflict` (`order_version_conflict`): ETag `If-Match` lỗi thời.
  - `422 Unprocessable Entity` (`invalid_cash_amount`): Tiền mặt nhận nhỏ hơn tổng tiền đơn hàng.
  - `503 Service Unavailable` (`dependency_unavailable`): Inventory hoặc Payment Service tạm thời không kết nối được.

---

#### `GET /api/v1/orders/{orderId}/checkout-attempts`
Lấy danh sách lịch sử các lượt checkout attempt của đơn hàng.

- **Thẩm quyền:** Quyền `order:read`.
- **Tham số Đường dẫn (Path Parameters):** `orderId` (UUID, Bắt buộc).
- **Phản hồi Thành công (`200 OK`):** Trả về danh sách `CheckoutAttemptResponse[]`.

---

### 5. Các Endpoint Giai đoạn P1 & P2 (Feature-Gated & Tương lai)

Các endpoint dưới đây được bảo vệ bằng cờ tính năng (Feature Flag). Chúng được đặc tả đầy đủ hợp đồng để đảm bảo tính sẵn sàng khi hệ thống mở rộng sang sơ đồ bàn, phiếu in bếp KDS và gọi món QR tại bàn.

#### `POST /api/v1/orders/{orderId}/hold`
Tạm giữ một đơn hàng nháp đang phục vụ để giải phóng màn hình POS tiếp nhận đơn khác (ví dụ: khách đang gọi món nhưng chờ bạn đi cùng đến thêm).

- **Thẩm quyền:** Quyền `order:edit`.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `If-Match: "order-v7"` (Bắt buộc)
  - `Idempotency-Key: <unique-key>` (Bắt buộc)
  - `X-Active-Branch-Id: <uuid>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Khuyến nghị)
  - `Content-Type: application/json; charset=utf-8` (Bắt buộc)
- **Tham số Đường dẫn (Path Parameters):** `orderId` (UUID, Bắt buộc).
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "reason": "Khách đợi thêm người, giữ bàn và món đã gọi"
}
```

*Quy cách kiểm tra:* `reason` từ 1 đến 500 ký tự. Chỉ áp dụng cho đơn đang ở trạng thái `DRAFT`.

- **Phản hồi Thành công (`200 OK`):** Trả về `OrderResponse` với `status: "HELD"` và version tăng thêm 1.
- **Mã lỗi khả dĩ:** `403 Forbidden`, `404 Not Found`, `409 order_not_editable`, `409 order_version_conflict`.

---

#### `POST /api/v1/orders/{orderId}/resume`
Mở lại một đơn hàng đang bị tạm giữ (`HELD -> DRAFT`) để tiếp tục chỉnh sửa hoặc tiến hành thanh toán.

- **Thẩm quyền:** Quyền `order:edit`.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `If-Match: "order-v8"` (Bắt buộc)
  - `Idempotency-Key: <unique-key>` (Bắt buộc)
  - `X-Active-Branch-Id: <uuid>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Khuyến nghị)
- **Tham số Đường dẫn (Path Parameters):** `orderId` (UUID, Bắt buộc).
- **Phản hồi Thành công (`200 OK`):** Trả về `OrderResponse` với `status: "DRAFT"`.
- **Mã lỗi khả dĩ:** `403 Forbidden`, `404 Not Found`, `409 conflict` (đơn không ở trạng thái `HELD`).

---

#### `POST /api/v1/orders/{orderId}/splits`
Tách hóa đơn (Split Bill) / tách một số dòng món từ đơn hiện tại sang một đơn hàng nháp mới độc lập.

- **Thẩm quyền:** Quyền `order:edit`.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `If-Match: "order-v8"` (Bắt buộc)
  - `Idempotency-Key: <unique-key>` (Bắt buộc)
  - `X-Active-Branch-Id: <uuid>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Khuyến nghị)
  - `Content-Type: application/json; charset=utf-8` (Bắt buộc)
- **Tham số Đường dẫn (Path Parameters):** `orderId` (UUID, Bắt buộc).
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "targetLines": [
    {
      "orderLineId": "0192b2d0-7f31-7abc-8d2c-123456789012",
      "quantity": 1
    }
  ],
  "newOrderServiceMode": "TABLE",
  "newOrderTableReference": "A03-B"
}
```

*Quy cách kiểm tra:* `quantity` tách phải nhỏ hơn hoặc bằng số lượng hiện có của dòng món. Các dòng được tách sang đơn mới sẽ giữ nguyên toàn bộ snapshot món, biến thể và giá gốc.

- **Phản hồi Thành công (`201 Created`):**

```json
{
  "data": {
    "originalOrder": {
      "id": "0192b2d0-7f30-7abc-8d2c-123456789012",
      "grandTotalMinor": 34000,
      "version": 9
    },
    "newOrder": {
      "id": "0192b2d0-7fa0-7abc-8d2c-123456789012",
      "orderNumber": "A03B-000128",
      "status": "DRAFT",
      "grandTotalMinor": 34000,
      "version": 1
    }
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

- **Mã lỗi khả dĩ:** `403 Forbidden`, `404 Not Found`, `409 order_not_editable`, `422 validation_error`.

---

#### `POST /api/v1/orders/merge`
Gộp hai hoặc nhiều đơn hàng nháp đang mở thuộc cùng một bàn thành một đơn hàng duy nhất trước khi thanh toán.

- **Thẩm quyền:** Quyền `order:edit`.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `Idempotency-Key: <unique-key>` (Bắt buộc)
  - `X-Active-Branch-Id: <uuid>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Khuyến nghị)
  - `Content-Type: application/json; charset=utf-8` (Bắt buộc)
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "sourceOrderIds": [
    "0192b2d0-7fa0-7abc-8d2c-123456789012"
  ],
  "targetOrderId": "0192b2d0-7f30-7abc-8d2c-123456789012"
}
```

*Quy tắc:* Toàn bộ các đơn phải ở trạng thái `DRAFT` và cùng thuộc chi nhánh của người gọi. Các đơn nguồn (`sourceOrderIds`) sau khi gộp sẽ tự động chuyển sang `CANCELLED` với lý do `MERGED_INTO_<targetOrderId>`.

- **Phản hồi Thành công (`200 OK`):** Trả về `OrderResponse` của đơn đích sau khi đã gộp toàn bộ dòng món và tính lại tổng tiền.
- **Mã lỗi khả dĩ:** `403 Forbidden`, `404 Not Found`, `409 order_not_editable`, `422 validation_error`.

---

#### `GET /api/v1/branches/{branchId}/tables`
Lấy danh sách sơ đồ bàn và trạng thái phục vụ hiện tại tại chi nhánh (bàn trống, bàn đang có khách, đơn nháp liên kết).

- **Thẩm quyền:** Quyền `order:read` tại chi nhánh.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `X-Active-Branch-Id: <uuid>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Tùy chọn)
- **Tham số Đường dẫn (Path Parameters):** `branchId` (UUID, Bắt buộc).
- **Phản hồi Thành công (`200 OK`):**

```json
{
  "data": [
    {
      "tableReference": "A03",
      "zone": "Tầng 1 - Trong nhà",
      "status": "OCCUPIED",
      "activeOrderId": "0192b2d0-7f30-7abc-8d2c-123456789012",
      "orderNumber": "A03-000127",
      "occupiedSince": "2026-10-12T09:00:00Z"
    },
    {
      "tableReference": "A04",
      "zone": "Tầng 1 - Trong nhà",
      "status": "VACANT",
      "activeOrderId": null,
      "orderNumber": null,
      "occupiedSince": null
    }
  ],
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

- **Mã lỗi khả dĩ:** `401 Unauthorized`, `403 Forbidden`, `404 Not Found`.

---

#### `POST /api/v1/orders/{orderId}/kitchen-tickets`
Gửi phiếu chế biến món ăn bền bỉ xuống hệ thống màn hình nhà bếp (KDS).

- **Thẩm quyền:** Quyền `order:edit`.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `If-Match: "order-v9"` (Bắt buộc)
  - `Idempotency-Key: <unique-key>` (Bắt buộc)
  - `X-Active-Branch-Id: <uuid>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Khuyến nghị)
  - `Content-Type: application/json; charset=utf-8` (Bắt buộc)
- **Tham số Đường dẫn (Path Parameters):** `orderId` (UUID, Bắt buộc).
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "lineIds": [
    "0192b2d0-7f31-7abc-8d2c-123456789012"
  ],
  "priority": "HIGH",
  "ticketNote": "Bàn khách VIP, ưu tiên chế biến trước"
}
```

- **Phản hồi Thành công (`201 Created`):**

```json
{
  "data": {
    "ticketId": "0192b2d0-7fb0-7abc-8d2c-123456789012",
    "ticketNumber": "K-102",
    "orderId": "0192b2d0-7f30-7abc-8d2c-123456789012",
    "orderNumber": "A03-000127",
    "tableReference": "A03",
    "status": "SENT_TO_KITCHEN",
    "priority": "HIGH",
    "lines": [
      {
        "lineId": "0192b2d0-7f31-7abc-8d2c-123456789012",
        "itemName": "Cà phê sữa",
        "variantName": "STANDARD",
        "quantity": 2,
        "note": "Không hành",
        "modifiers": ["Trân châu đen"]
      }
    ],
    "createdAt": "2026-10-12T09:10:00Z"
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

- **Mã lỗi khả dĩ:** `403 Forbidden`, `404 Not Found`, `409 order_version_conflict`, `422 validation_error`.

---

#### `WebSocket /ws/v1/kds`
Kênh kết nối WebSocket truyền phát các sự kiện nhà bếp theo thời gian thực tới màn hình KDS.

- **Xác thực:** Truyền Cognito token qua query parameter `?token=<access-token>` trong quá trình bắt tay (Handshake). Phải có membership chi nhánh hợp lệ với vai trò `KITCHEN`, `MANAGER` hoặc `OWNER`.
- **Cơ chế phục hồi:** Nếu kết nối WebSocket bị đứt, KDS client tự động kết nối lại và thăm dò sự kiện bền bỉ từ Outbox/EventBridge dựa trên `lastTicketId` để tránh mất đơn.

---

#### `POST /api/v1/qr-sessions` (Giai đoạn P2)
Khởi tạo phiên gọi món tại bàn bằng mã QR dành cho khách hàng tự phục vụ (Self-service Table QR Ordering).

- **Thẩm quyền:** Public endpoint dành cho khách hàng (không yêu cầu JWT nhân viên); được bảo vệ bằng chữ ký mật mã (HMAC signature) gắn cố định với bàn và chi nhánh.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `X-Correlation-Id: <uuid>` (Tùy chọn)
  - `Content-Type: application/json; charset=utf-8` (Bắt buộc)
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "branchId": "0192b2d0-7f23-7abc-8d2c-123456789012",
  "tableReference": "A03",
  "tableSignature": "hmac-sha256-signature-from-printed-qr-code"
}
```

- **Phản hồi Thành công (`201 Created`):**

```json
{
  "data": {
    "sessionId": "0192b2d0-7fc0-7abc-8d2c-123456789012",
    "qrOrderToken": "qr_tok_01J...",
    "branchId": "0192b2d0-7f23-7abc-8d2c-123456789012",
    "tableReference": "A03",
    "expiresAt": "2026-10-12T11:00:00Z",
    "menuCatalogVersion": 19
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

- **Mã lỗi khả dĩ:** `403 Forbidden` (chữ ký mã QR giả mạo hoặc bàn bị khóa), `404 Not Found`, `422 validation_error`.

---

## Hợp đồng Saga Nội bộ (Internal Saga Contracts)

Các endpoint dưới đây dành riêng cho **AWS Step Functions Saga Orchestrator** gọi vào Order Service qua mạng nội bộ. Bắt buộc phải có xác thực **Workload Identity / mTLS**.

### 1. `POST /internal/v1/orders/{orderId}/checkout-attempts/{attemptId}/finalize`
Được Step Functions gọi sau khi Payment Service xác nhận thanh toán thành công (`PaymentPaid`) và Inventory Service xác nhận trừ kho thành công (`COMMITTED`). Endpoint này chuyển trạng thái đơn hàng sang `PAID`, đóng attempt sang `COMPLETED`, và phát ra sự kiện domain chuẩn mực `OrderPaid.v1`.

```json
{
  "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
  "branchId": "0192b2d0-7f23-7abc-8d2c-123456789012",
  "sagaId": "0192b2d0-7f33-7abc-8d2c-123456789012",
  "paymentId": "0192b2d0-7f34-7abc-8d2c-123456789012",
  "reservationId": "0192b2d0-7f35-7abc-8d2c-123456789012",
  "paymentStatus": "PAID",
  "inventoryStatus": "COMMITTED"
}
```

- **Phản hồi:** `200 OK` kèm trạng thái hoàn tất của đơn hàng. Lần gọi lại với cùng attempt sẽ trả về kết quả cũ và **không phát trùng sự kiện `OrderPaid.v1`**.

---

### 2. `POST /internal/v1/orders/{orderId}/checkout-attempts/{attemptId}/fail`
Được Step Functions gọi khi việc thanh toán hoặc giữ chỗ tồn kho thất bại không thể thử lại, hoặc hết hạn (timeout). Chuyển trạng thái đơn sang `CHECKOUT_FAILED` và attempt sang `FAILED`.

```json
{
  "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
  "branchId": "0192b2d0-7f23-7abc-8d2c-123456789012",
  "sagaId": "0192b2d0-7f33-7abc-8d2c-123456789012",
  "failureCode": "INVENTORY_INSUFFICIENT",
  "failureReason": "Hết nguyên liệu trân châu hoàng kim",
  "retryable": false
}
```

- **Phản hồi:** `200 OK`.

---

### 3. `POST /internal/v1/orders/{orderId}/checkout-attempts/{attemptId}/compensation`
Cập nhật tiến trình bù trừ (Compensation) của Saga (ví dụ: giải phóng giữ chỗ tồn kho khi thanh toán lỗi). Trạng thái bù trừ gồm: `RETRYING`, `SAFE`, hoặc `FAILED`. Nếu việc bù trừ `FAILED`, hệ thống sẽ kích hoạt cờ đối soát `paymentReconciliationRequired: true`.

- **Phản hồi:** `200 OK`.

---

### 4. `POST /internal/v1/orders/{orderId}/checkout-attempts/{attemptId}/late-payment`
Xử lý trường hợp IPN thanh toán từ VNPAY đến muộn sau khi giữ chỗ tồn kho đã bị giải phóng và attempt đã bị đánh dấu thất bại. Thao tác này ghi nhận vết đối soát, giữ nguyên trạng thái đơn ở `CHECKOUT_FAILED` (không tự ý khôi phục sang `PAID`) và phát sự kiện cảnh báo vận hành.

- **Phản hồi:** `200 OK`.

---

### 5. `GET /internal/v1/orders/{orderId}/checkout-context`
Trả về dữ liệu ngữ cảnh snapshot bất biến để Step Functions sử dụng trong quá trình thực thi Saga.

- **Phản hồi:** `200 OK` chứa danh sách dòng active, tổng tiền, tenantId, branchId, expiresAt.

---

## Cấu trúc Schema Dùng lại (Reusable Schemas)

### 1. `OrderResponse`

| Tên trường | Kiểu dữ liệu | Bắt buộc | Mô tả ý nghĩa |
|---|---|:---:|---|
| `id` | UUID string | Có | Mã định danh đơn hàng UUID v7. |
| `tenantId` / `branchId` | UUID string | Có | Thuộc tenant và chi nhánh nào. |
| `orderNumber` | string | Có | Mã số đơn hiển thị (ví dụ: `A03-000127`). |
| `serviceMode` | enum | Có | `COUNTER`, `TAKEAWAY`, `TABLE`. |
| `tableReference` | string \| null | Có điều kiện | Tên bàn (bắt buộc với `TABLE`). |
| `status` | enum | Có | `DRAFT`, `CHECKOUT_PENDING`, `PAID`, `CHECKOUT_FAILED`, `CANCELLED`. |
| `orderNote` | string \| null | Không | Ghi chú đơn hàng. |
| `customerId` | UUID \| null | Không | Mã khách hàng thành viên Loyalty. |
| `lines` | array | Có | Danh sách toàn bộ các dòng món (active và cancelled). |
| `totals` | object | Có | Snapshot các khoản tổng: `subtotalMinor`, `vatMinor`, `discountMinor`, `grandTotalMinor`, `currency`. |
| `catalogVersion` | integer | Có | Phiên bản Catalog được sử dụng khi snapshot. |
| `version` | integer | Có | Phiên bản kiểm soát khóa lạc quan `If-Match`. |
| `currentCheckoutAttempt` | object \| null | Không | Lượt checkout attempt đang chạy gần nhất. |
| `createdAt` / `updatedAt` | ISO-8601 UTC | Có | Mốc thời gian tạo và cập nhật UTC. |

### 2. `CheckoutResponse`

| Tên trường | Kiểu dữ liệu | Bắt buộc | Mô tả ý nghĩa |
|---|---|:---:|---|
| `orderId` | UUID string | Có | Mã đơn hàng. |
| `attemptId` | UUID string | Có | Mã lượt checkout attempt UUID v7. |
| `sagaId` | UUID string | Có | Mã phiên điều phối Saga Step Functions. |
| `orderStatus` | enum | Có | Trạng thái đơn sau khi gọi checkout. |
| `attemptStatus` | enum | Có | `PAYMENT_PENDING`, `COMPLETED`, `FAILED`. |
| `paymentId` | UUID \| null | Không | Mã giao dịch thanh toán phía Payment Service. |
| `reservationId` | UUID \| null | Không | Mã giữ chỗ tồn kho phía Inventory Service. |
| `grandTotalMinor` | integer | Có | Tổng tiền thanh toán bất biến (VND). |
| `cashReceivedMinor` | integer \| null | Không | Tiền mặt nhận từ khách (chỉ có khi thanh toán tiền mặt). |
| `cashChangeMinor` | integer \| null | Không | Tiền thừa trả lại khách (chỉ có khi thanh toán tiền mặt). |
| `qrPayload` | string \| null | Không | Chuỗi mã VietQR / VNPAY QR (chỉ có khi thanh toán QR). |
| `expiresAt` | ISO-8601 UTC \| null | Không | Thời hạn hết hạn của mã QR / phiên thanh toán (15 phút). |

---

## Cấu trúc Cơ sở Dữ liệu (Database Schema)

Cơ sở dữ liệu của Order Service sử dụng PostgreSQL 16+ / Aurora PostgreSQL.

### 1. Bảng `orders` (Đơn hàng)

| Cột | Kiểu | Null | Ràng buộc | Chú thích cột |
|---|---|:---:|---|---|
| `id` | uuid | Không | PK | Mã định danh đơn hàng UUID v7. |
| `tenant_id` | uuid | Không | — | Ngữ cảnh tenant sở hữu. |
| `branch_id` | uuid | Không | — | Chi nhánh phát sinh đơn hàng (bất biến). |
| `order_number` | text | Không | Duy nhất theo branch | Mã số đơn hiển thị (ví dụ: `A03-000127`). |
| `service_mode` | text | Không | `COUNTER/TAKEAWAY/TABLE` | Chế độ phục vụ. |
| `table_reference` | text | Có | Tối đa 64 ký tự | Tên bàn phục vụ. |
| `status` | text | Không | State machine | `DRAFT/CHECKOUT_PENDING/PAID/CHECKOUT_FAILED/CANCELLED`. |
| `order_note` | text | Có | Tối đa 500 ký tự | Ghi chú đơn hàng. |
| `customer_id` | uuid | Có | — | Tham chiếu khách hàng Loyalty. |
| `subtotal_minor` | bigint | Không | `>= 0` | Tổng tiền các dòng món active. |
| `vat_minor` | bigint | Không | Mặc định 0, `>= 0` | Tiền thuế GTGT. |
| `discount_minor` | bigint | Không | Mặc định 0, `>= 0` | Tiền chiết khấu/khuyến mãi. |
| `grand_total_minor` | bigint | Không | `>= 0` | `subtotal + vat - discount`. |
| `currency` | text | Không | Mặc định `'VND'` | Đơn vị tiền tệ. |
| `pricing_policy_version` | integer | Không | Mặc định 1 | Phiên bản chính sách giá/thuế. |
| `catalog_version` | bigint | Không | — | Phiên bản Catalog nhất quán khi tính giá. |
| `version` | bigint | Không | Mặc định 1 | Phiên bản khóa lạc quan `If-Match`. |
| `created_at` / `updated_at` | timestamptz | Không | Mặc định `now()` | Thời điểm tạo và cập nhật UTC. |

### 2. Bảng `order_lines` (Dòng món trong đơn)

| Cột | Kiểu | Null | Ràng buộc | Chú thích cột |
|---|---|:---:|---|---|
| `id` | uuid | Không | PK | Mã dòng món UUID v7. |
| `order_id` | uuid | Không | FK `orders(id)` | Thuộc đơn hàng nào. |
| `tenant_id` | uuid | Không | — | Đảm bảo cùng tenant. |
| `branch_id` | uuid | Không | — | Đảm bảo cùng chi nhánh. |
| `catalog_item_id` | uuid | Không | — | ID món ăn trong Catalog. |
| `catalog_variant_id`| uuid | Không | — | ID biến thể cụ thể đã phân giải. |
| `item_name_snapshot`| text | Không | — | Tên món ăn lịch sử bất biến. |
| `variant_name_snapshot` | text | Không | — | Tên biến thể lịch sử bất biến. |
| `base_unit_price_minor` | bigint | Không | `>= 0` | Giá cơ sở của biến thể tại thời điểm chọn. |
| `modifier_unit_price_minor` | bigint | Không | `>= 0` | Tổng phụ thu các tùy chọn modifier. |
| `unit_price_minor` | bigint | Không | `>= 0` | Đơn giá tổng của 1 suất món. |
| `quantity` | integer | Không | 1–999 | Số lượng món đặt. |
| `note` | text | Có | Tối đa 250 ký tự | Ghi chú chế biến món. |
| `status` | text | Không | `ACTIVE/CANCELLED` | Trạng thái dòng món. |
| `cancel_reason` | text | Có | Tối đa 500 ký tự | Bắt buộc có khi `status = 'CANCELLED'`. |
| `cancelled_by` | uuid | Có | — | Tác nhân quản lý thực hiện hủy món. |
| `cancelled_at` | timestamptz | Có | — | Thời điểm hủy dòng món. |
| `catalog_version` | bigint | Không | — | Phiên bản Catalog khi chụp snapshot. |
| `version` | bigint | Không | Mặc định 1 | Phiên bản khóa lạc quan. |

### 3. Bảng `order_line_modifiers` (Snapshot Modifier của dòng món)

| Cột | Kiểu | Null | Ràng buộc | Chú thích cột |
|---|---|:---:|---|---|
| `id` | uuid | Không | PK | Mã bản ghi snapshot modifier. |
| `order_line_id` | uuid | Không | FK `order_lines(id)` | Thuộc dòng món nào. |
| `catalog_group_id` | uuid | Không | — | ID nhóm modifier. |
| `catalog_option_id`| uuid | Không | — | ID tùy chọn modifier. |
| `group_name_snapshot` | text | Không | — | Tên nhóm modifier lịch sử. |
| `option_name_snapshot`| text | Không | — | Tên tùy chọn modifier lịch sử. |
| `price_delta_minor` | bigint | Không | `>= 0` | Mức phụ thu tại thời điểm chọn. |

### 4. Bảng `checkout_attempts` (Lượt thử thanh toán)

| Cột | Kiểu | Null | Ràng buộc | Chú thích cột |
|---|---|:---:|---|---|
| `id` | uuid | Không | PK | Mã lượt checkout attempt UUID v7. |
| `order_id` | uuid | Không | FK `orders(id)` | Thuộc đơn hàng nào. |
| `tenant_id` / `branch_id` | uuid | Không | — | Ngữ cảnh phân quyền. |
| `attempt_number` | integer | Không | `>= 1` | Số thứ tự lần thử checkout của đơn. |
| `saga_id` | uuid | Không | — | Mã phiên điều phối Step Functions. |
| `status` | text | Không | Attempt state | `PENDING/PAYMENT_PENDING/COMPLETED/FAILED`. |
| `compensation_status` | text | Có | — | `RETRYING/SAFE/FAILED`. |
| `payment_id` | uuid | Có | — | Mã tham chiếu thanh toán từ Payment Service. |
| `reservation_id` | uuid | Có | — | Mã giữ chỗ tồn kho từ Inventory Service. |
| `grand_total_minor` | bigint | Không | — | Tổng tiền bất biến của lượt attempt này. |
| `cash_received_minor`| bigint | Có | — | Tiền mặt nhận từ khách (nếu có). |
| `cash_change_minor` | bigint | Có | — | Tiền thừa thối lại khách (nếu có). |
| `failure_code` | text | Có | — | Mã lỗi kỹ thuật/nghiệp vụ khi attempt hỏng. |
| `payment_reconciliation_required` | boolean | Không | Mặc định `false` | Cờ cảnh báo cần nhân viên đối soát thủ công. |
| `expires_at` | timestamptz | Có | — | Hạn chót thanh toán (cho mã QR VNPAY). |
| `created_at` / `completed_at` | timestamptz | Không | — | Thời điểm bắt đầu và kết thúc attempt. |

---

## Danh mục Chỉ mục Cơ sở Dữ liệu (Database Indexes)

| Tên bảng | Tên chỉ mục / ràng buộc | Các cột (Columns) | Loại chỉ mục | Mục đích tối ưu hóa |
|---|---|---|---|---|
| `orders` | `uq_orders_branch_number` | `(tenant_id, branch_id, order_number)` | Unique B-tree | Mã số đơn duy nhất trong chi nhánh. |
| `orders` | `idx_orders_branch_cursor` | `(tenant_id, branch_id, status, created_at, id)` | B-tree | Tối ưu hóa phân trang con trỏ (Cursor Pagination). |
| `order_lines` | `idx_order_lines_order` | `(order_id, status)` | B-tree | Nạp nhanh các dòng món active và tính tổng tiền. |
| `checkout_attempts` | `idx_checkout_attempts_order` | `(order_id, attempt_number)` | B-tree | Lấy attempt gần nhất và lịch sử thanh toán. |
| `checkout_attempts` | `uq_checkout_attempts_active` | `(order_id)` WHERE status IN ('PENDING', 'PAYMENT_PENDING') | Unique Partial B-tree | Mỗi đơn hàng chỉ có tối đa 1 lượt checkout attempt đang chạy dở dang. |
| `outbox_messages` | `idx_outbox_order_queue` | `(publish_status, next_attempt_at, created_at)` | B-tree | Tiến trình Outbox Publisher gửi sự kiện lên EventBridge. |
| `idempotency_records` | `uq_idempotency_order_actor` | `(tenant_id, operation, actor_subject, idempotency_key)` | Unique B-tree | Chống trùng lặp lệnh tạo đơn, tính lại giá và checkout. |

---

## Kế hoạch Thực thi Cơ sở Dữ liệu với Flyway

| Thứ tự | Tên File Script Migration | Nội dung chính | Lưu ý Rollback / Phục hồi |
|:---:|---|---|---|
| 1 | `V1__create_order_extensions.sql` | Cấu hình extension UUID và các hàm tiện ích. | Forward-only. |
| 2 | `V2__create_orders_and_lines.sql` | Tạo bảng `orders`, `order_lines`, `order_line_modifiers` và các khóa ngoại. | Khôi phục từ backup; không drop bảng. |
| 3 | `V3__create_checkout_attempts.sql` | Tạo bảng `checkout_attempts`, ràng buộc trạng thái Saga và partial unique index. | Đảm bảo tính toàn vẹn trạng thái attempt. |
| 4 | `V4__create_order_transitions_and_outbox.sql` | Tạo bảng `order_state_transitions`, `outbox_messages` và `idempotency_records`. | Giữ lại các bản ghi outbox để replay sự kiện. |
| 5 | `V5__create_p1_tables_and_kitchen_tickets.sql` | Tạo bảng sơ đồ bàn và phiếu in bếp KDS (P1). | Triển khai trước khi bật feature flag P1. |
| 6 | `V6__seed_local_demo_orders.sql` | Nạp dữ liệu mẫu một số đơn hàng và lượt checkout cho môi trường local demo. | Chỉ chạy trên môi trường local demo. |

---

## Hợp đồng Sự kiện Tích hợp (Integration Event Contracts)

### Cấu trúc Bao bì Sự kiện Chuẩn (Event Envelope)

```json
{
  "eventId": "0192b2d0-7f40-7abc-8d2c-123456789012",
  "eventType": "OrderPaid.v1",
  "eventVersion": 1,
  "occurredAt": "2026-10-12T09:03:00Z",
  "producer": "order-service",
  "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
  "branchId": "0192b2d0-7f23-7abc-8d2c-123456789012",
  "aggregateType": "Order",
  "aggregateId": "0192b2d0-7f30-7abc-8d2c-123456789012",
  "correlationId": "cor_01J...",
  "causationId": "cmd_01J...",
  "payload": {
    "orderId": "0192b2d0-7f30-7abc-8d2c-123456789012",
    "orderNumber": "A03-000127",
    "serviceMode": "TABLE",
    "tableReference": "A03",
    "customerId": null,
    "lines": [
      {
        "lineId": "0192b2d0-7f31-7abc-8d2c-123456789012",
        "catalogItemId": "0192b2d0-7f1e-7abc-8d2c-123456789012",
        "catalogVariantId": "0192b2d0-7f20-7abc-8d2c-123456789012",
        "itemName": "Cà phê sữa",
        "variantName": "STANDARD",
        "quantity": 2,
        "unitPriceMinor": 34000,
        "modifiers": [
          {
            "optionId": "0192b2d0-7f22-7abc-8d2c-123456789012",
            "optionName": "Trân châu đen",
            "priceDeltaMinor": 5000
          }
        ]
      }
    ],
    "subtotalMinor": 68000,
    "vatMinor": 0,
    "discountMinor": 0,
    "grandTotalMinor": 68000,
    "currency": "VND",
    "paidAt": "2026-10-12T09:03:00Z",
    "catalogVersion": 18,
    "orderVersion": 7
  }
}
```

### Danh mục Sự kiện Domain (Event Registry)

| Kiểu sự kiện | Mức ưu tiên | Mục đích của Payload | Các bên tiêu thụ (Consumers) |
|---|:---:|---|---|
| `OrderCreated.v1` | P0 | Thông báo đơn nháp mới được tạo, chế độ phục vụ, chi nhánh và các dòng món ban đầu. | Reporting, Audit, Recommendation. |
| `OrderLineCancelled.v1` | P0 | Thông tin chi tiết dòng món bị hủy, người hủy, lý do hủy và tổng tiền mới. | Audit, Reporting, Loss Prevention. |
| `OrderCancelled.v1` | P0 | Toàn bộ đơn hàng bị hủy (sau khi hủy dòng món active cuối cùng). | Reporting, Audit. |
| `OrderCheckoutFailed.v1` | P0 | Lượt checkout attempt thất bại, lý do hỏng, trạng thái bù trừ kho. | Audit, Reporting, Vận hành đối soát. |
| `OrderPaid.v1` | P0 | **Sự thật chuẩn mực đơn hàng thanh toán thành công**, snapshot bất biến toàn bộ dòng món và tổng tiền. | Loyalty (tích điểm), Reporting (doanh thu), Audit. |
| `OrderItemSubmitted.v1` | P1 | Phiếu in bếp bền bỉ gửi tới màn hình nhà bếp (KDS). | Màn hình KDS, Audit. |

---

## Ma trận Kiểm thử Hợp đồng (Contract Test Requirements)

1. **Kiểm thử OpenAPI & Định dạng:** Tự động đối soát toàn bộ request body, response body, error envelope, ETag header và cấu trúc phân trang con trỏ (Cursor Pagination) với OpenAPI 3.1.
2. **Kiểm thử Thẩm quyền & Phân quyền (RBAC):**
   - Xác thực đúng chi nhánh giữa token JWT và header `X-Active-Branch-Id`.
   - Thu ngân (`CASHIER`) bị chặn với mã `403 Forbidden` khi cố tình gọi API hủy dòng món `POST /orders/{id}/lines/{lineId}/cancel`.
3. **Kiểm thử Cô lập Đa Tenant & IDOR:** Người dùng từ chi nhánh hoặc tenant khác không thể truy cập đơn hàng bằng cách truyền `orderId` qua path; hệ thống trả về mã `404 Not Found`.
4. **Kiểm thử Tính Bất biến (Idempotency):**
   - Gửi lại cùng một `Idempotency-Key` với cùng nội dung request body phải trả về kết quả cũ mà không tạo thêm đơn hàng mới, không chạy lại Saga Step Functions, và không phát trùng sự kiện `OrderPaid.v1`.
   - Gửi cùng `Idempotency-Key` nhưng khác request hash phải trả về `409 duplicate_request`.
5. **Kiểm thử Khóa Lạc quan (If-Match):** Mọi thao tác chỉnh sửa đơn, thêm/sửa dòng, hủy món, reprice và checkout đều yêu cầu `If-Match`. Gửi header lỗi thời phải bị từ chối với mã `409 order_version_conflict`.
6. **Kiểm thử Bất biến Nghiệp vụ:**
   - Không cho phép sửa đơn khi đơn không ở trạng thái `DRAFT` (`409 order_not_editable`).
   - Không cho phép thanh toán đơn hàng không có dòng món nào active (`409 order_no_active_lines`).
   - Khi thanh toán tiền mặt: `cashReceivedMinor < grandTotalMinor` phải bị từ chối với lỗi `422 invalid_cash_amount`.
   - Hủy dòng món active cuối cùng bắt buộc phải chuyển trạng thái đơn hàng sang `CANCELLED` một cách nguyên tử.
7. **Kiểm thử Tích hợp Catalog & Reprice:**
   - Khi thực đơn Catalog thay đổi giá, lệnh checkout bị chặn lại với lỗi `409 catalog_changed`.
   - Thao tác reprice với `confirm = false` trả về bản diff xem trước mà không sửa DB; `confirm = true` cập nhật toàn bộ snapshot giá mới nhất.
8. **Kiểm thử Điều phối Checkout Saga:**
   - Xử lý thành công: Tiền mặt trả về `200 PAID`; VNPAY QR trả về `202 CHECKOUT_PENDING` kèm chuỗi QR payload và thời hạn 15 phút.
   - Xử lý thất bại / Quá hạn: Tồn kho không đủ hoặc timeout giải phóng kho thành công và chuyển đơn sang `CHECKOUT_FAILED`.
   - Xử lý bù trừ hỏng: Nếu giải phóng kho thất bại, attempt chuyển sang `FAILED` và kích hoạt cờ đối soát `paymentReconciliationRequired = true`.
   - Xử lý IPN muộn: Thanh toán xác minh đến muộn không tự ý khôi phục đơn sang `PAID`, thiết lập cờ đối soát và phát sự kiện cảnh báo.
