# Hợp đồng Dữ liệu & API: Catalog Service (Dịch vụ Danh mục Món ăn)

## Nguồn tài liệu và Quyền hạn Hợp đồng

- [Tầm nhìn Catalog (Vision)](./2026-10-12-vision.md)
- [Yêu cầu Kỹ thuật Catalog (Requirements)](./2026-10-12-requirements.md)
- [Kiến trúc Catalog (Architecture)](./2026-10-12-architecture.md)
- [Hợp đồng Identity & Access](../identity-access/2026-10-12-contracts.md)
- [Hợp đồng Nền tảng POS](../pos-platform/2026-10-12-contracts.md)

Tài liệu này là hợp đồng quy chuẩn (normative contract) cho Catalog Service. Việc triển khai thực tế bắt buộc phải sinh và xác thực tài liệu OpenAPI 3.1 cùng các artifact AsyncAPI/JSON Schema từ các định nghĩa này trước bản phát hành mã nguồn đầu tiên.

---

## Các Nguyên tắc Hợp đồng (Contract Principles)

- Toàn bộ API đồng bộ công khai sử dụng chuẩn **RESTful + OpenAPI 3.1** dưới tiền tố `/api/v1/catalog`.
- Các API giao tiếp giữa các service (service-to-service) sử dụng REST + OpenAPI 3.1 dưới tiền tố `/internal/v1/catalog`, bắt buộc phải có chứng chỉ Workload Identity / mTLS và tuyệt đối không đưa vào bảng định tuyến của Angular.
- Các sự kiện bất đồng bộ tuân thủ cấu trúc bao bọc (envelope) chuẩn của nền tảng và JSON Schema/AsyncAPI. Cơ chế chuyển phát là ít nhất một lần (at-least-once); các consumer khử trùng lặp theo `eventId`.
- Các định danh (ID) do Catalog quản lý đều là chuỗi UUID v7. Mốc thời gian là chuỗi ISO-8601 UTC trong API và kiểu `TIMESTAMPTZ` trong PostgreSQL.
- Tiền tệ là số nguyên không âm `int64` tính theo đơn vị minor VND (`amountMinor`), trong đó 1 đơn vị minor tương đương 1 VND nguyên. Tuyệt đối không dùng số thực dấu phẩy động (float).
- Mọi yêu cầu công khai đều được phân quyền thông qua quyết định thẩm quyền từ Identity Access. JWT role/branch claims và các giá trị tenant do client gửi lên chỉ đóng vai trò gợi ý (hints), không bao giờ là nguồn thẩm quyền phân quyền.
- Ngữ cảnh tenant được trích xuất từ ngữ cảnh Identity đã xác thực. Catalog không đưa vào header `X-Active-Tenant-Id` và không bao giờ chấp nhận quyền sở hữu tenant từ request body hoặc query parameter. Header `X-Active-Branch-Id` chỉ được chấp nhận như một bộ chọn chi nhánh đã được máy chủ xác thực.
- Trạng thái thực đơn và giá cơ sở ở P0 có phạm vi trên toàn tenant. Header chi nhánh vẫn bắt buộc đối với các vai trò có phạm vi chi nhánh (`MANAGER`, `CASHIER`, `KITCHEN`) để tư cách thành viên và quyền truy cập chi nhánh được kiểm tra ngay cả khi đồ thị thực đơn P0 trả về là toàn chuỗi.
- Tuyệt đối không có thao tác xóa cứng (hard delete) trong P0. Các chuyển đổi trạng thái/lưu trữ (`ARCHIVED`) là xóa mềm (soft delete) nhằm bảo toàn các ID cần thiết cho các tham chiếu lịch sử của Order và Inventory.
- Mọi thao tác ghi có khả năng thử lại bắt buộc phải có `Idempotency-Key`. Mọi thao tác cập nhật tài nguyên có phiên bản bắt buộc phải truyền `If-Match`.

---

## Hợp đồng HTTP Chung (Common HTTP Contract)

### Các Header HTTP

| Header | Bắt buộc | Ý nghĩa và Quy ước sử dụng |
|---|:---:|---|
| `Authorization` | Bắt buộc cho Public API | `Bearer <Cognito access token>`. Cognito xác thực danh tính người dùng; Identity phân giải tư cách thành viên và quyền hạn hiện tại. |
| `X-Correlation-Id` | Khuyến nghị (Tự sinh nếu thiếu) | Mã truy vết yêu cầu; được trả về trong metadata thành công/lỗi, outbox event và audit log. |
| `X-Active-Branch-Id` | Khi gọi với vai trò chi nhánh | Chi nhánh do client chọn; được xác thực đối chiếu với tư cách thành viên đang hoạt động hiện tại. Tùy chọn đối với các thao tác toàn tenant của Owner. |
| `Idempotency-Key` | Bắt buộc cho các lệnh ghi có thể retry | Khóa do client sinh có phạm vi actor + tenant + operation. Cùng một khóa bắt buộc phải mang cùng một hash yêu cầu. |
| `If-Match` | Bắt buộc khi cập nhật tài nguyên | Mã ETag của tài nguyên từ lần đọc gần nhất (ví dụ: `"item-v7"` hoặc `"category-v3"`). Bắt buộc cho các cập nhật và thay đổi liên kết. |
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

#### Phản hồi Danh sách Phân trang (Paginated Collection Response)

Catalog Service sử dụng phân trang trang (Offset Pagination) cho các danh sách quản trị và POS:

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

#### Phản hồi Lỗi (Error Envelope)

```json
{
  "error": {
    "code": "catalog_version_conflict",
    "message": "Tài nguyên danh mục đã thay đổi sau khi được đọc",
    "details": [
      {
        "field": "If-Match",
        "code": "etag_mismatch",
        "message": "Làm mới tài nguyên và thử lại"
      }
    ],
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

#### Bảng Quy tắc Mã Lỗi của Catalog Service

| HTTP Status | Mã lỗi (Code) | Ngữ cảnh sử dụng trong Catalog |
|---:|---|---|
| 401 | `unauthorized` | Thiếu, không hợp lệ hoặc hết hạn Cognito JWT. |
| 403 | `forbidden` | Vai trò, tư cách thành viên, phạm vi tenant hoặc chi nhánh hiện tại không cho phép thao tác (ví dụ: Cashier cố tình sửa thực đơn). |
| 404 | `not_found` | Tài nguyên không tồn tại trong phạm vi được ủy quyền của bên gọi. Tuyệt đối không làm lộ sự tồn tại của tài nguyên thuộc tenant khác. |
| 409 | `conflict` | Xung đột trạng thái chung. |
| 409 | `catalog_version_conflict` | Header `If-Match` đã lỗi thời so với phiên bản hiện tại trên database. |
| 409 | `sku_conflict` | Mã SKU đã được đặt trước bởi một món khác trong cùng tenant (bao gồm cả món đã lưu trữ). |
| 409 | `category_has_items` | Danh mục không thể lưu trữ khi nó vẫn còn chứa các món chưa lưu trữ. |
| 409 | `last_active_variant` | Thao tác sẽ khiến một món đang hoạt động không còn biến thể/biến thể mặc định nào đang hoạt động. |
| 409 | `invalid_required_modifier_rule` | Thao tác sẽ khiến quy tắc nhóm modifier bắt buộc trở nên bất khả thi (ví dụ: số tùy chọn active < minSelections). |
| 409 | `duplicate_request` | Cùng một `Idempotency-Key` được tái sử dụng nhưng với một request hash khác. |
| 409 | `archived_resource` | Một tài nguyên đã lưu trữ ở trạng thái kết thúc (terminal) không thể bị biến đổi. |
| 422 | `validation_error` | Xác thực payload, trường dữ liệu hoặc quy tắc nghiệp vụ thất bại. |
| 422 | `invalid_modifier_selection` | Tùy chọn được chọn thuộc tenant khác, không hoạt động, bị trùng lặp hoặc vi phạm min/max. |
| 422 | `invalid_variant_selection` | Biến thể bị thiếu, không hoạt động, thuộc tenant khác hoặc mơ hồ theo quy tắc phân giải kiểu cũ. |
| 422 | `foreign_resource` | Tài nguyên được tham chiếu không thuộc về tenant hiện tại. |
| 429 | `rate_limited` | Vượt quá giới hạn tốc độ tại Gateway hoặc service. |
| 503 | `authorization_unavailable` | Không thể phân giải an toàn quyết định phân quyền từ Identity; hệ thống áp dụng nguyên tắc đóng cửa an toàn (fail-closed). |
| 503 | `service_unavailable` | Catalog hoặc một dependency nội bộ bắt buộc tạm thời không khả dụng. |

---

## Hợp đồng Phân quyền và Phạm vi (Scope)

Catalog gửi yêu cầu quyết định tới Identity Access với subject hiện tại, ngữ cảnh tenant được chọn, ngữ cảnh chi nhánh và một trong các quyền hạn sau:

| Quyền hạn | Vai trò sở hữu | Phạm vi (Scope) | Mục đích sử dụng trong Catalog |
|---|---|---|---|
| `catalog:read` | `OWNER`, `MANAGER`, `CASHIER`, `KITCHEN` | Tenant cộng với tư cách thành viên chi nhánh hợp lệ | Đọc menu bán hàng trên POS và đọc dữ liệu quản trị. |
| `catalog:manage` | `OWNER`, `MANAGER` | Toàn tenant; Manager cần có tư cách thành viên active tại bất kỳ chi nhánh nào trong tenant | Tạo mới, cập nhật, đổi trạng thái danh mục, món ăn, biến thể và modifier. |
| `catalog:resolve` | Workload identity của Order | Tenant/chi nhánh đã xác thực, không có vai trò người dùng cuối | Phân giải và định giá giỏ hàng Đơn hàng nội bộ. |

*Các quy tắc bảo mật:*
1. `OWNER` có thể đọc/ghi trong tenant mà không cần header chi nhánh.
2. `MANAGER` có thể đọc/ghi thực đơn dùng chung của tenant khi Identity xác nhận có tư cách thành viên Manager đang hoạt động tại bất kỳ chi nhánh nào thuộc tenant đó. Thao tác biến đổi được ghi audit là có tác động toàn tenant.
3. `CASHIER` và `KITCHEN` bắt buộc phải có tư cách thành viên chi nhánh đang hoạt động và **chỉ có quyền đọc (read-only)**. Cố tình gửi lệnh ghi sẽ nhận mã `403 Forbidden`.
4. Mọi quyết định từ Identity bị lỗi hoặc timeout đều dẫn đến từ chối truy cập (Fail-Closed).

---

## Bề mặt API Công khai (Public API Surface Summary)

| Phương thức | Đường dẫn (Path) | Quyền hạn (Scope) | Idempotent | If-Match | Mã HTTP Thành công |
|---|---|---|:---:|:---:|---|
| **GET** | `/api/v1/catalog/categories` | `catalog:read` | Không | Không | `200 OK` |
| **POST** | `/api/v1/catalog/categories` | `catalog:manage` | Bắt buộc | Không | `201 Created` |
| **GET** | `/api/v1/catalog/categories/{categoryId}` | `catalog:read` | Không | Không | `200 OK` |
| **PATCH** | `/api/v1/catalog/categories/{categoryId}` | `catalog:manage` | Bắt buộc | Bắt buộc | `200 OK` |
| **POST** | `/api/v1/catalog/categories/{categoryId}/archive` | `catalog:manage` | Bắt buộc | Bắt buộc | `200 OK` |
| **GET** | `/api/v1/catalog/items` | `catalog:read` | Không | Không | `200 OK` |
| **POST** | `/api/v1/catalog/items` | `catalog:manage` | Bắt buộc | Không | `201 Created` |
| **GET** | `/api/v1/catalog/items/{itemId}` | `catalog:read` | Không | Không | `200 OK` |
| **PATCH** | `/api/v1/catalog/items/{itemId}` | `catalog:manage` | Bắt buộc | Bắt buộc | `200 OK` |
| **POST** | `/api/v1/catalog/items/{itemId}/state` | `catalog:manage` | Bắt buộc | Bắt buộc | `200 OK` |
| **POST** | `/api/v1/catalog/items/{itemId}/variants` | `catalog:manage` | Bắt buộc | Không | `201 Created` |
| **PATCH** | `/api/v1/catalog/variants/{variantId}` | `catalog:manage` | Bắt buộc | Bắt buộc | `200 OK` |
| **POST** | `/api/v1/catalog/variants/{variantId}/state` | `catalog:manage` | Bắt buộc | Bắt buộc | `200 OK` |
| **POST** | `/api/v1/catalog/variants/{variantId}/default` | `catalog:manage` | Bắt buộc | Bắt buộc | `200 OK` |
| **GET** | `/api/v1/catalog/modifier-groups` | `catalog:read` | Không | Không | `200 OK` |
| **POST** | `/api/v1/catalog/modifier-groups` | `catalog:manage` | Bắt buộc | Không | `201 Created` |
| **GET** | `/api/v1/catalog/modifier-groups/{groupId}` | `catalog:read` | Không | Không | `200 OK` |
| **PATCH** | `/api/v1/catalog/modifier-groups/{groupId}` | `catalog:manage` | Bắt buộc | Bắt buộc | `200 OK` |
| **POST** | `/api/v1/catalog/modifier-groups/{groupId}/state` | `catalog:manage` | Bắt buộc | Bắt buộc | `200 OK` |
| **POST** | `/api/v1/catalog/modifier-groups/{groupId}/options` | `catalog:manage` | Bắt buộc | Không | `201 Created` |
| **PATCH** | `/api/v1/catalog/modifier-options/{optionId}` | `catalog:manage` | Bắt buộc | Bắt buộc | `200 OK` |
| **POST** | `/api/v1/catalog/modifier-options/{optionId}/state` | `catalog:manage` | Bắt buộc | Bắt buộc | `200 OK` |
| **PUT** | `/api/v1/catalog/items/{itemId}/modifier-groups/{groupId}` | `catalog:manage` | Bắt buộc | Bắt buộc | `200 OK` |
| **DELETE** | `/api/v1/catalog/items/{itemId}/modifier-groups/{groupId}` | `catalog:manage` | Bắt buộc | Bắt buộc | `204 No Content` |
| **GET** | `/api/v1/catalog/combos` (P1) | `catalog:read` | Không | Không | `200 OK` |
| **POST** | `/api/v1/catalog/combos` (P1) | `catalog:manage` | Bắt buộc | Không | `201 Created` |
| **GET** | `/api/v1/catalog/combos/{comboId}` (P1) | `catalog:read` | Không | Không | `200 OK` |
| **PATCH** | `/api/v1/catalog/combos/{comboId}` (P1) | `catalog:manage` | Bắt buộc | Bắt buộc | `200 OK` |
| **POST** | `/api/v1/catalog/combos/{comboId}/state` (P1) | `catalog:manage` | Bắt buộc | Bắt buộc | `200 OK` |
| **PUT** | `/api/v1/catalog/branches/{branchId}/variants/{variantId}/price` (P1) | `catalog:manage` | Bắt buộc | Không | `200 OK` |
| **DELETE** | `/api/v1/catalog/branches/{branchId}/variants/{variantId}/price` (P1) | `catalog:manage` | Bắt buộc | Không | `204 No Content` |
| **GET** | `/api/v1/catalog/tags` (P1) | `catalog:read` | Không | Không | `200 OK` |
| **POST** | `/api/v1/catalog/tags` (P1) | `catalog:manage` | Bắt buộc | Không | `201 Created` |
| **PUT** | `/api/v1/catalog/items/{itemId}/tags` (P1) | `catalog:manage` | Bắt buộc | Bắt buộc | `200 OK` |

---

## Chi tiết Hợp đồng Từng API Công khai (Public API Details)

### 1. Quản lý Danh mục (Categories)

Cấu trúc danh mục trong P0 là dạng phẳng (không phân nhánh lồng nhau). Không hỗ trợ phương thức `DELETE` vật lý; chỉ hỗ trợ thao tác lưu trữ mềm (`archive`).

#### `GET /api/v1/catalog/categories`
Lấy danh sách các danh mục trong tenant.

- **Thẩm quyền:** Quyền `catalog:read`.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `X-Active-Branch-Id: <uuid>` (Bắt buộc với Cashier/Kitchen)
  - `X-Correlation-Id: <uuid>` (Tùy chọn)
- **Tham số Truy vấn (Query Parameters):**
  - `page` (integer, Mặc định: `1`): Số thứ tự trang.
  - `limit` (integer, Mặc định: `20`, Tối đa: `100`): Kích thước trang.
  - `status` (string, Tùy chọn): Lọc theo trạng thái (`ACTIVE`, `INACTIVE`, `ARCHIVED`). Mặc định trên POS là chỉ lấy `ACTIVE`.
  - `q` (string, Tùy chọn): Tìm kiếm gần đúng theo tên danh mục.
  - `sort` (string, Mặc định: `displayOrder:asc`): Sắp xếp (`displayOrder:asc`, `name:asc`, `createdAt:desc`).
- **Phản hồi Thành công (`200 OK`):**

```json
{
  "data": [
    {
      "id": "0192b2d0-7f1c-7abc-8d2c-123456789012",
      "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
      "name": "Cà phê",
      "displayOrder": 10,
      "status": "ACTIVE",
      "itemCount": 12,
      "version": 3,
      "createdAt": "2026-10-12T08:00:00Z",
      "updatedAt": "2026-10-12T08:10:00Z"
    }
  ],
  "meta": {
    "pagination": {
      "page": 1,
      "limit": 20,
      "total": 1,
      "totalPages": 1
    },
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

- **Mã lỗi khả dĩ:** `401 Unauthorized`, `403 Forbidden`, `422 Unprocessable Entity`.

---

#### `POST /api/v1/catalog/categories`
Tạo mới một danh mục trong tenant.

- **Thẩm quyền:** Quyền `catalog:manage` (`OWNER`, `MANAGER`).
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `Idempotency-Key: <unique-key>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Khuyến nghị)
  - `Content-Type: application/json; charset=utf-8` (Bắt buộc)
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "name": "Trà sữa & Trà trái cây",
  "displayOrder": 20
}
```

*Quy cách kiểm tra (Validation):*
- `name`: Chuỗi từ 1 đến 120 ký tự Unicode (sau khi trim); duy nhất trong phạm vi tenant (không phân biệt chữ hoa chữ thường).
- `displayOrder`: Số nguyên `>= 0` (mặc định 0).

- **Phản hồi Thành công (`201 Created`):**

```json
{
  "data": {
    "id": "0192b2d0-7f50-7abc-8d2c-123456789012",
    "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
    "name": "Trà sữa & Trà trái cây",
    "displayOrder": 20,
    "status": "ACTIVE",
    "itemCount": 0,
    "version": 1,
    "createdAt": "2026-10-12T08:30:00Z",
    "updatedAt": "2026-10-12T08:30:00Z"
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

- **Mã lỗi khả dĩ:**
  - `403 Forbidden` (`forbidden`): Cashier/Kitchen cố tình tạo danh mục.
  - `409 Conflict` (`conflict`): Tên danh mục đã tồn tại trong tenant.
  - `409 Conflict` (`duplicate_request`): Trùng `Idempotency-Key` nhưng body khác.
  - `422 Unprocessable Entity` (`validation_error`): Tên rỗng hoặc displayOrder âm.

---

#### `GET /api/v1/catalog/categories/{categoryId}`
Xem chi tiết một danh mục.

- **Thẩm quyền:** Quyền `catalog:read`.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Tùy chọn)
- **Tham số Đường dẫn (Path Parameters):**
  - `categoryId` (UUID, Bắt buộc)
- **Phản hồi Thành công (`200 OK`):** Trả về đối tượng `CategoryResponse`.
- **Mã lỗi khả dĩ:** `401 Unauthorized`, `403 Forbidden`, `404 Not Found`.

---

#### `PATCH /api/v1/catalog/categories/{categoryId}`
Cập nhật tên hoặc thứ tự hiển thị của danh mục.

- **Thẩm quyền:** Quyền `catalog:manage`.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `If-Match: "category-v1"` (Bắt buộc - ETag phiên bản hiện tại)
  - `Idempotency-Key: <unique-key>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Khuyến nghị)
  - `Content-Type: application/json; charset=utf-8` (Bắt buộc)
- **Tham số Đường dẫn (Path Parameters):**
  - `categoryId` (UUID, Bắt buộc)
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "name": "Trà sữa Đặc biệt",
  "displayOrder": 25
}
```

- **Phản hồi Thành công (`200 OK`):** Trả về `CategoryResponse` với `version` tăng lên.
- **Mã lỗi khả dĩ:**
  - `403 Forbidden`, `404 Not Found`.
  - `409 Conflict` (`catalog_version_conflict`): Không khớp ETag `If-Match`.
  - `409 Conflict` (`archived_resource`): Danh mục đã lưu trữ không được sửa.
  - `422 Unprocessable Entity` (`validation_error`): Tên rỗng hoặc trùng tên khác.

---

#### `POST /api/v1/catalog/categories/{categoryId}/archive`
Lưu trữ danh mục (`status: ARCHIVED`). Bất biến nghiệp vụ: Danh mục chỉ được phép lưu trữ khi nó không còn chứa bất kỳ món ăn nào chưa lưu trữ (`category_has_items`).

- **Thẩm quyền:** Quyền `catalog:manage`.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `If-Match: "category-v2"` (Bắt buộc)
  - `Idempotency-Key: <unique-key>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Khuyến nghị)
- **Tham số Đường dẫn (Path Parameters):**
  - `categoryId` (UUID, Bắt buộc)
- **Dữ liệu Gửi lên (Request Body):** Trống `{}`.
- **Phản hồi Thành công (`200 OK`):** `CategoryResponse` với `status: "ARCHIVED"`.
- **Mã lỗi khả dĩ:**
  - `403 Forbidden`, `404 Not Found`.
  - `409 Conflict` (`category_has_items`): Vẫn còn món thuộc danh mục chưa archive.
  - `409 Conflict` (`catalog_version_conflict`).

---

### 2. Quản lý Món ăn và Biến thể (Items & Variants)

Một món ăn (`CatalogItem`) đại diện cho một sản phẩm trong thực đơn. Mọi món ăn bắt buộc phải có ít nhất một biến thể (`CatalogVariant`, ví dụ: SIZE_M, SIZE_L, STANDARD) và chính xác một biến thể mặc định (`isDefault: true`). Thao tác tạo món và các biến thể ban đầu được thực hiện trong cùng một giao dịch cơ sở dữ liệu.

#### `GET /api/v1/catalog/items`
Lấy danh sách món ăn trong thực đơn. Hỗ trợ hai chế độ xem: `view=sellable` (dành cho POS) và `view=admin` (dành cho Dashboard quản trị).

- **Thẩm quyền:** Quyền `catalog:read`.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `If-None-Match: "catalog-tenant-<tenantId>-v<catalogVersion>"` (Tùy chọn - kiểm tra cache 304)
  - `X-Active-Branch-Id: <uuid>` (Bắt buộc với Cashier/Kitchen)
  - `X-Correlation-Id: <uuid>` (Tùy chọn)
- **Tham số Truy vấn (Query Parameters):**
  - `view` (string, Mặc định: `sellable`): `sellable` chỉ trả về các món/biến thể đang `ACTIVE` và có thể bán được; `admin` trả về đầy đủ các trạng thái và chỉ dành cho Owner/Manager.
  - `categoryId` (UUID, Tùy chọn): Lọc theo danh mục.
  - `status` (string, Tùy chọn): `ACTIVE`, `INACTIVE`, `ARCHIVED` (chỉ có tác dụng với `view=admin`).
  - `q` (string, Tùy chọn): Tìm kiếm theo tên hoặc SKU.
  - `page` (integer, Mặc định: `1`): Số trang.
  - `limit` (integer, Mặc định: `20`, Tối đa: `100`): Kích thước trang.
  - `sort` (string, Mặc định: `displayOrder:asc`): Sắp xếp (`displayOrder`, `name`, `updatedAt`).
- **Phản hồi Thành công (`200 OK`):**

```json
{
  "data": [
    {
      "id": "0192b2d0-7f1e-7abc-8d2c-123456789012",
      "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
      "sku": "CF-SUA",
      "name": "Cà phê sữa",
      "description": "Cà phê rang xay nguyên chất pha phin cùng sữa đặc",
      "category": {
        "id": "0192b2d0-7f1c-7abc-8d2c-123456789012",
        "name": "Cà phê"
      },
      "status": "ACTIVE",
      "variants": [
        {
          "id": "0192b2d0-7f20-7abc-8d2c-123456789012",
          "name": "STANDARD",
          "price": { "amountMinor": 29000, "currency": "VND" },
          "isDefault": true,
          "status": "ACTIVE",
          "displayOrder": 0,
          "version": 2
        }
      ],
      "modifierGroups": [],
      "catalogVersion": 18,
      "version": 4,
      "createdAt": "2026-10-12T08:00:00Z",
      "updatedAt": "2026-10-12T08:10:00Z"
    }
  ],
  "meta": {
    "pagination": {
      "page": 1,
      "limit": 20,
      "total": 1,
      "totalPages": 1
    },
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

*Lưu ý về Cache Header:* Đi kèm tiêu đề phản hồi `ETag: "catalog-tenant-<tenantId>-v18"` và `Cache-Control: private, max-age=30`. Nếu client gửi `If-None-Match` khớp ETag và phân quyền hợp lệ, server trả về `304 Not Modified`.

- **Mã lỗi khả dĩ:** `401 Unauthorized`, `403 Forbidden`, `422 Unprocessable Entity`.

---

#### `POST /api/v1/catalog/items`
Tạo mới một món ăn cùng các biến thể ban đầu trong một giao dịch.

- **Thẩm quyền:** Quyền `catalog:manage`.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `Idempotency-Key: <unique-key>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Khuyến nghị)
  - `Content-Type: application/json; charset=utf-8` (Bắt buộc)
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "sku": "TS-TRAN-CHAU",
  "name": "Trà sữa Trân châu Đường đen",
  "categoryId": "0192b2d0-7f50-7abc-8d2c-123456789012",
  "description": "Trà sữa truyền thống kết hợp trân châu nấu đường mật",
  "displayOrder": 10,
  "status": "ACTIVE",
  "variants": [
    {
      "name": "SIZE_M",
      "price": { "amountMinor": 35000, "currency": "VND" },
      "isDefault": true,
      "displayOrder": 0
    },
    {
      "name": "SIZE_L",
      "price": { "amountMinor": 42000, "currency": "VND" },
      "isDefault": false,
      "displayOrder": 1
    }
  ]
}
```

*Quy cách kiểm tra (Validation):*
- `sku`: 1–64 ký tự chữ và số, tự động chuẩn hóa chữ hoa (`trim + uppercase`), duy nhất trong tenant (kể cả món đã lưu trữ).
- `name`: 1–200 ký tự Unicode, bắt buộc.
- `categoryId`: UUID của một danh mục đang `ACTIVE` trong cùng tenant.
- `description`: Tối đa 2.000 ký tự (tùy chọn hoặc `null`).
- `status`: `ACTIVE` hoặc `INACTIVE` (mặc định `INACTIVE`). Nếu là `ACTIVE`, bắt buộc phải có ít nhất 1 biến thể `ACTIVE` và chính xác 1 biến thể mặc định `isDefault: true`.
- `variants`: Mảng chứa từ 1 đến 20 biến thể. Bắt buộc có chính xác một phần tử mang `isDefault: true`. Mọi giá trị `amountMinor` phải là số nguyên `>= 0`, đơn vị tiền tệ bắt buộc là `"VND"`.

- **Phản hồi Thành công (`201 Created`):** Trả về `CatalogItemResponse` đầy đủ thông tin món và mảng biến thể vừa tạo, kèm `catalogVersion` mới của tenant.
- **Mã lỗi khả dĩ:**
  - `403 Forbidden` (`forbidden`).
  - `409 Conflict` (`sku_conflict`): Mã SKU đã bị trùng trong tenant.
  - `422 Unprocessable Entity` (`validation_error`): Không có biến thể mặc định, tiền tệ sai, hoặc danh mục không tồn tại/không thuộc tenant.

---

#### `GET /api/v1/catalog/items/{itemId}`
Xem chi tiết một món ăn, bao gồm các biến thể và danh sách nhóm modifier được liên kết.

- **Thẩm quyền:** Quyền `catalog:read`.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `X-Active-Branch-Id: <uuid>` (Bắt buộc với Cashier/Kitchen)
  - `X-Correlation-Id: <uuid>` (Tùy chọn)
- **Tham số Đường dẫn (Path Parameters):**
  - `itemId` (UUID, Bắt buộc)
- **Tham số Truy vấn (Query Parameters):**
  - `view` (string, Mặc định: `sellable`): `sellable` hoặc `admin`.
- **Phản hồi Thành công (`200 OK`):**

```json
{
  "data": {
    "id": "0192b2d0-7f1e-7abc-8d2c-123456789012",
    "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
    "sku": "TS-TRAN-CHAU",
    "name": "Trà sữa Trân châu Đường đen",
    "description": "Trà sữa truyền thống kết hợp trân châu nấu đường mật",
    "category": {
      "id": "0192b2d0-7f50-7abc-8d2c-123456789012",
      "name": "Trà sữa & Trà trái cây"
    },
    "status": "ACTIVE",
    "variants": [
      {
        "id": "0192b2d0-7f60-7abc-8d2c-123456789012",
        "name": "SIZE_M",
        "price": { "amountMinor": 35000, "currency": "VND" },
        "isDefault": true,
        "status": "ACTIVE",
        "displayOrder": 0,
        "version": 1
      },
      {
        "id": "0192b2d0-7f61-7abc-8d2c-123456789012",
        "name": "SIZE_L",
        "price": { "amountMinor": 42000, "currency": "VND" },
        "isDefault": false,
        "status": "ACTIVE",
        "displayOrder": 1,
        "version": 1
      }
    ],
    "modifierGroups": [
      {
        "id": "0192b2d0-7f70-7abc-8d2c-123456789012",
        "name": "Mức đường",
        "minSelections": 1,
        "maxSelections": 1,
        "displayOrder": 10,
        "status": "ACTIVE",
        "options": [
          {
            "id": "0192b2d0-7f71-7abc-8d2c-123456789012",
            "name": "100% Đường",
            "priceDelta": { "amountMinor": 0, "currency": "VND" },
            "status": "ACTIVE",
            "displayOrder": 0,
            "version": 1
          },
          {
            "id": "0192b2d0-7f72-7abc-8d2c-123456789012",
            "name": "50% Đường",
            "priceDelta": { "amountMinor": 0, "currency": "VND" },
            "status": "ACTIVE",
            "displayOrder": 1,
            "version": 1
          }
        ],
        "version": 2
      }
    ],
    "catalogVersion": 19,
    "version": 1,
    "createdAt": "2026-10-12T09:00:00Z",
    "updatedAt": "2026-10-12T09:00:00Z"
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

- **Mã lỗi khả dĩ:** `401 Unauthorized`, `403 Forbidden`, `404 Not Found`.

---

#### `PATCH /api/v1/catalog/items/{itemId}`
Cập nhật thông tin cơ bản của món ăn (`name`, `description`, `categoryId`, `displayOrder`). Các trường không truyền sẽ giữ nguyên.

- **Thẩm quyền:** Quyền `catalog:manage`.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `If-Match: "item-v1"` (Bắt buộc)
  - `Idempotency-Key: <unique-key>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Khuyến nghị)
  - `Content-Type: application/json; charset=utf-8` (Bắt buộc)
- **Tham số Đường dẫn (Path Parameters):**
  - `itemId` (UUID, Bắt buộc)
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "name": "Trà sữa Trân châu Đường đen (Công thức mới)",
  "displayOrder": 15
}
```

- **Phản hồi Thành công (`200 OK`):** Trả về `CatalogItemResponse` với `version` món tăng thêm 1 và `catalogVersion` tăng thêm 1.
- **Mã lỗi khả dĩ:**
  - `403 Forbidden`, `404 Not Found`.
  - `409 Conflict` (`catalog_version_conflict`).
  - `422 Unprocessable Entity` (`validation_error`).

---

#### `POST /api/v1/catalog/items/{itemId}/state`
Chuyển đổi trạng thái hoạt động của món ăn (`ACTIVE <-> INACTIVE` hoặc chuyển sang `ARCHIVED`).

- **Thẩm quyền:** Quyền `catalog:manage`.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `If-Match: "item-v2"` (Bắt buộc)
  - `Idempotency-Key: <unique-key>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Khuyến nghị)
  - `Content-Type: application/json; charset=utf-8` (Bắt buộc)
- **Tham số Đường dẫn (Path Parameters):**
  - `itemId` (UUID, Bắt buộc)
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "status": "INACTIVE",
  "reason": "Tạm dừng phục vụ để thay đổi nhà cung cấp sữa"
}
```

*Quy tắc chuyển trạng thái:*
- Kích hoạt lại `ACTIVE`: Yêu cầu món phải có ít nhất 1 biến thể `ACTIVE` và 1 biến thể mặc định `ACTIVE`.
- Chuyển sang `ARCHIVED`: Đây là trạng thái kết thúc (terminal); không thể kích hoạt lại hoặc sửa đổi sau khi archive. Mã SKU của món vẫn được bảo lưu để đảm bảo tính toàn vẹn dữ liệu.

- **Phản hồi Thành công (`200 OK`):** Trả về `CatalogItemResponse` với trạng thái mới.
- **Mã lỗi khả dĩ:**
  - `403 Forbidden`, `404 Not Found`.
  - `409 Conflict` (`last_active_variant`): Món không có biến thể active nào nên không thể mở `ACTIVE`.
  - `409 Conflict` (`archived_resource`): Không thể đổi trạng thái món đã lưu trữ.

---

#### `POST /api/v1/catalog/items/{itemId}/variants`
Bổ sung thêm một biến thể mới cho món ăn hiện có.

- **Thẩm quyền:** Quyền `catalog:manage`.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `Idempotency-Key: <unique-key>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Khuyến nghị)
  - `Content-Type: application/json; charset=utf-8` (Bắt buộc)
- **Tham số Đường dẫn (Path Parameters):**
  - `itemId` (UUID, Bắt buộc)
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "name": "SIZE_XL",
  "price": { "amountMinor": 49000, "currency": "VND" },
  "isDefault": false,
  "displayOrder": 2
}
```

- **Phản hồi Thành công (`201 Created`):**

```json
{
  "data": {
    "id": "0192b2d0-7f62-7abc-8d2c-123456789012",
    "name": "SIZE_XL",
    "price": { "amountMinor": 49000, "currency": "VND" },
    "isDefault": false,
    "status": "ACTIVE",
    "displayOrder": 2,
    "version": 1
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

- **Mã lỗi khả dĩ:** `403 Forbidden`, `404 Not Found`, `422 Unprocessable Entity`.

---

#### `PATCH /api/v1/catalog/variants/{variantId}`
Cập nhật tên, giá bán hoặc thứ tự của một biến thể cụ thể.

- **Thẩm quyền:** Quyền `catalog:manage`.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `If-Match: "variant-v1"` (Bắt buộc)
  - `Idempotency-Key: <unique-key>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Khuyến nghị)
  - `Content-Type: application/json; charset=utf-8` (Bắt buộc)
- **Tham số Đường dẫn (Path Parameters):**
  - `variantId` (UUID, Bắt buộc)
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "name": "SIZE_XL_KHONG_LO",
  "price": { "amountMinor": 52000, "currency": "VND" }
}
```

- **Phản hồi Thành công (`200 OK`):** Trả về `VariantResponse` với giá mới và version tăng lên.
- **Mã lỗi khả dĩ:** `403 Forbidden`, `404 Not Found`, `409 Conflict` (`catalog_version_conflict`), `422 Unprocessable Entity`.

---

#### `POST /api/v1/catalog/variants/{variantId}/state`
Đổi trạng thái biến thể (`ACTIVE`, `INACTIVE`, `ARCHIVED`). Bất biến: Không được phép vô hiệu hóa hoặc lưu trữ biến thể nếu việc đó làm cho món ăn đang `ACTIVE` mất đi biến thể active cuối cùng hoặc mất biến thể mặc định.

- **Thẩm quyền:** Quyền `catalog:manage`.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `If-Match: "variant-v2"` (Bắt buộc)
  - `Idempotency-Key: <unique-key>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Khuyến nghị)
  - `Content-Type: application/json; charset=utf-8` (Bắt buộc)
- **Tham số Đường dẫn (Path Parameters):**
  - `variantId` (UUID, Bắt buộc)
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "status": "INACTIVE",
  "reason": "Tạm ngưng size XL do thiếu cốc đựng"
}
```

- **Phản hồi Thành công (`200 OK`):** Trả về `VariantResponse` với trạng thái mới.
- **Mã lỗi khả dĩ:**
  - `403 Forbidden`, `404 Not Found`.
  - `409 Conflict` (`last_active_variant`): Biến thể này là biến thể active duy nhất của món đang bán.

---

#### `POST /api/v1/catalog/variants/{variantId}/default`
Chuyển đổi một biến thể thành biến thể mặc định của món ăn. Hệ thống tự động gỡ cờ mặc định của biến thể cũ trong cùng một giao dịch.

- **Thẩm quyền:** Quyền `catalog:manage`.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `If-Match: "item-v3"` (Bắt buộc - ETag của món ăn)
  - `Idempotency-Key: <unique-key>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Khuyến nghị)
- **Tham số Đường dẫn (Path Parameters):**
  - `variantId` (UUID, Bắt buộc)
- **Dữ liệu Gửi lên (Request Body):** Trống `{}`.
- **Phản hồi Thành công (`200 OK`):** Trả về `CatalogItemResponse` cập nhật.
- **Mã lỗi khả dĩ:**
  - `403 Forbidden`, `404 Not Found`.
  - `409 Conflict` (`conflict`): Biến thể không ở trạng thái `ACTIVE`.

---

### 3. Quản lý Nhóm Modifier và Tùy chọn (Modifier Groups & Options)

Nhóm Modifier được thiết kế theo mô hình tái sử dụng (Reusable Groups). Một nhóm modifier (ví dụ: Topping, Mức đường, Mức đá) có thể được định nghĩa một lần và liên kết vào nhiều món ăn khác nhau với các quy tắc chọn tối thiểu/tối đa (`minSelections`, `maxSelections`) được phép ghi đè riêng cho từng món.

#### `GET /api/v1/catalog/modifier-groups`
Lấy danh sách các nhóm modifier trong tenant.

- **Thẩm quyền:** Quyền `catalog:read`.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Tùy chọn)
- **Tham số Truy vấn (Query Parameters):**
  - `page` (integer, Mặc định: `1`)
  - `limit` (integer, Mặc định: `20`, Tối đa: `100`)
  - `status` (string, Tùy chọn: `ACTIVE`, `INACTIVE`, `ARCHIVED`)
  - `q` (string, Tùy chọn): Tìm kiếm theo tên nhóm.
- **Phản hồi Thành công (`200 OK`):**

```json
{
  "data": [
    {
      "id": "0192b2d0-7f21-7abc-8d2c-123456789012",
      "name": "Topping",
      "minSelections": 0,
      "maxSelections": 3,
      "displayOrder": 20,
      "status": "ACTIVE",
      "options": [
        {
          "id": "0192b2d0-7f22-7abc-8d2c-123456789012",
          "name": "Trân châu đen",
          "priceDelta": { "amountMinor": 5000, "currency": "VND" },
          "status": "ACTIVE",
          "displayOrder": 0,
          "version": 1
        }
      ],
      "version": 3
    }
  ],
  "meta": {
    "pagination": {
      "page": 1,
      "limit": 20,
      "total": 1,
      "totalPages": 1
    },
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

---

#### `POST /api/v1/catalog/modifier-groups`
Tạo mới một nhóm modifier kèm các tùy chọn ban đầu.

- **Thẩm quyền:** Quyền `catalog:manage`.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `Idempotency-Key: <unique-key>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Khuyến nghị)
  - `Content-Type: application/json; charset=utf-8` (Bắt buộc)
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "name": "Topping Cao Cấp",
  "minSelections": 0,
  "maxSelections": 4,
  "displayOrder": 30,
  "options": [
    {
      "name": "Trân châu hoàng kim",
      "priceDelta": { "amountMinor": 8000, "currency": "VND" },
      "displayOrder": 0
    },
    {
      "name": "Pudding trứng",
      "priceDelta": { "amountMinor": 10000, "currency": "VND" },
      "displayOrder": 1
    }
  ]
}
```

*Quy cách kiểm tra:*
- `name`: 1–120 ký tự Unicode, duy nhất trong tenant giữa các nhóm active.
- `minSelections` / `maxSelections`: `0 <= minSelections <= maxSelections <= số tùy chọn`.
- `priceDelta.amountMinor`: Số nguyên `>= 0` (trong P0 phụ thu không được âm).

- **Phản hồi Thành công (`201 Created`):** Trả về `ModifierGroupResponse` đầy đủ.
- **Mã lỗi khả dĩ:** `403 Forbidden`, `409 Conflict`, `422 Unprocessable Entity`.

---

#### `GET /api/v1/catalog/modifier-groups/{groupId}`
Xem chi tiết một nhóm modifier.

- **Thẩm quyền:** Quyền `catalog:read`.
- **Tham số Đường dẫn (Path Parameters):** `groupId` (UUID, Bắt buộc).
- **Phản hồi Thành công (`200 OK`):** Trả về `ModifierGroupResponse`.

---

#### `PATCH /api/v1/catalog/modifier-groups/{groupId}`
Cập nhật thông tin nhóm modifier (tên, minSelections, maxSelections mặc định, displayOrder).

- **Thẩm quyền:** Quyền `catalog:manage`.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `If-Match: "group-v3"` (Bắt buộc)
  - `Idempotency-Key: <unique-key>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Khuyến nghị)
  - `Content-Type: application/json; charset=utf-8` (Bắt buộc)
- **Tham số Đường dẫn (Path Parameters):** `groupId` (UUID, Bắt buộc).
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "name": "Topping Cao Cấp Đặc biệt",
  "maxSelections": 5
}
```

- **Phản hồi Thành công (`200 OK`):** `ModifierGroupResponse` với version tăng thêm 1.
- **Mã lỗi khả dĩ:** `403 Forbidden`, `404 Not Found`, `409 Conflict`, `422 Unprocessable Entity`.

---

#### `POST /api/v1/catalog/modifier-groups/{groupId}/state`
Đổi trạng thái nhóm modifier (`ACTIVE`, `INACTIVE`, `ARCHIVED`).

- **Thẩm quyền:** Quyền `catalog:manage`.
- **Tiêu đề Yêu cầu (Request Headers):** `If-Match`, `Idempotency-Key`, `X-Correlation-Id`, `Content-Type`.
- **Dữ liệu Gửi lên (Request Body):** `{ "status": "INACTIVE", "reason": "Hết nguyên liệu" }`.
- **Phản hồi Thành công (`200 OK`):** `ModifierGroupResponse`.

---

#### `POST /api/v1/catalog/modifier-groups/{groupId}/options`
Thêm tùy chọn mới vào nhóm modifier.

- **Thẩm quyền:** Quyền `catalog:manage`.
- **Tiêu đề Yêu cầu (Request Headers):** `Authorization`, `Idempotency-Key`, `X-Correlation-Id`, `Content-Type`.
- **Tham số Đường dẫn (Path Parameters):** `groupId` (UUID, Bắt buộc).
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "name": "Thạch dừa",
  "priceDelta": { "amountMinor": 6000, "currency": "VND" },
  "displayOrder": 2
}
```

- **Phản hồi Thành công (`201 Created`):** `ModifierOptionResponse`.
- **Mã lỗi khả dĩ:** `403 Forbidden`, `404 Not Found`, `409 Conflict`, `422 Unprocessable Entity`.

---

#### `PATCH /api/v1/catalog/modifier-options/{optionId}`
Cập nhật tên, phụ thu hoặc thứ tự hiển thị của tùy chọn modifier.

- **Thẩm quyền:** Quyền `catalog:manage`.
- **Tiêu đề Yêu cầu (Request Headers):** `Authorization`, `If-Match: "option-v1"`, `Idempotency-Key`, `X-Correlation-Id`, `Content-Type`.
- **Tham số Đường dẫn (Path Parameters):** `optionId` (UUID, Bắt buộc).
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "name": "Thạch dừa non",
  "priceDelta": { "amountMinor": 7000, "currency": "VND" }
}
```

- **Phản hồi Thành công (`200 OK`):** `ModifierOptionResponse`.

---

#### `POST /api/v1/catalog/modifier-options/{optionId}/state`
Đổi trạng thái tùy chọn modifier (`ACTIVE`, `INACTIVE`, `ARCHIVED`).

- **Thẩm quyền:** Quyền `catalog:manage`.
- **Tiêu đề Yêu cầu (Request Headers):** `If-Match`, `Idempotency-Key`, `X-Correlation-Id`, `Content-Type`.
- **Dữ liệu Gửi lên (Request Body):** `{ "status": "INACTIVE", "reason": "Tạm hết" }`.
- **Phản hồi Thành công (`200 OK`):** `ModifierOptionResponse`.

---

#### `PUT /api/v1/catalog/items/{itemId}/modifier-groups/{groupId}`
Gắn một nhóm modifier tái sử dụng vào một món ăn, kèm theo giá trị ghi đè số lượng lựa chọn tối thiểu/tối đa dành riêng cho món đó.

- **Thẩm quyền:** Quyền `catalog:manage`.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `If-Match: "item-v4"` (Bắt buộc - ETag của món ăn)
  - `Idempotency-Key: <unique-key>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Khuyến nghị)
  - `Content-Type: application/json; charset=utf-8` (Bắt buộc)
- **Tham số Đường dẫn (Path Parameters):**
  - `itemId` (UUID, Bắt buộc)
  - `groupId` (UUID, Bắt buộc)
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "overrideMin": 1,
  "overrideMax": 2,
  "displayOrder": 10
}
```

*Quy tắc kiểm tra:*
- Cả hai trường `overrideMin` và `overrideMax` đều là tùy chọn. Nếu không truyền, hệ thống sẽ sử dụng giá trị mặc định của nhóm.
- Ràng buộc: `0 <= effectiveMin <= effectiveMax <= số tùy chọn đang ACTIVE của nhóm`.
- Nếu món ăn đang `ACTIVE` và được gắn nhóm bắt buộc (`min >= 1`), nhóm đó bắt buộc phải có đủ số tùy chọn active; nếu không yêu cầu sẽ thất bại với lỗi `409 invalid_required_modifier_rule`.

- **Phản hồi Thành công (`200 OK`):** Trả về `CatalogItemResponse` cập nhật với nhóm modifier vừa được gắn.
- **Mã lỗi khả dĩ:**
  - `403 Forbidden`, `404 Not Found`.
  - `409 Conflict` (`invalid_required_modifier_rule`).
  - `409 Conflict` (`catalog_version_conflict`).
  - `422 Unprocessable Entity` (`foreign_resource`): Nhóm modifier thuộc tenant khác.

---

#### `DELETE /api/v1/catalog/items/{itemId}/modifier-groups/{groupId}`
Gỡ liên kết (detach) nhóm modifier khỏi món ăn. Thao tác này không xóa bản thân nhóm modifier trong cơ sở dữ liệu.

- **Thẩm quyền:** Quyền `catalog:manage`.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `If-Match: "item-v5"` (Bắt buộc)
  - `Idempotency-Key: <unique-key>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Khuyến nghị)
- **Tham số Đường dẫn (Path Parameters):**
  - `itemId` (UUID, Bắt buộc)
  - `groupId` (UUID, Bắt buộc)
- **Phản hồi Thành công (`204 No Content`):** Không có body.
- **Mã lỗi khả dĩ:** `403 Forbidden`, `404 Not Found`, `409 Conflict`.

---

### 4. Các Endpoint Giai đoạn P1 (Feature-Gated)

Các endpoint dưới đây được bảo vệ bằng cờ tính năng (Feature Flag) P1. Chúng được định nghĩa đầy đủ để đảm bảo tính tương thích kiến trúc khi mở rộng sang combo, giá chi nhánh và tag phân loại.

#### `GET /api/v1/catalog/combos`
Lấy danh sách các combo món ăn trong tenant.

- **Thẩm quyền:** Quyền `catalog:read`.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `X-Active-Branch-Id: <uuid>` (Bắt buộc với Cashier/Kitchen)
  - `X-Correlation-Id: <uuid>` (Tùy chọn)
- **Tham số Truy vấn (Query Parameters):**
  - `page` (integer, Mặc định: `1`): Số trang.
  - `limit` (integer, Mặc định: `20`, Tối đa: `100`): Kích thước trang.
  - `status` (string, Tùy chọn): `ACTIVE`, `INACTIVE`, `ARCHIVED`.
  - `q` (string, Tùy chọn): Tìm kiếm theo tên combo.
- **Phản hồi Thành công (`200 OK`):**

```json
{
  "data": [
    {
      "id": "0192b2d0-7f90-7abc-8d2c-123456789012",
      "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
      "name": "Combo Ăn Sáng Tiết Kiệm",
      "description": "Bánh mì kẹp thịt + Cà phê sữa đá",
      "fixedPrice": { "amountMinor": 45000, "currency": "VND" },
      "status": "ACTIVE",
      "components": [
        {
          "catalogItemId": "0192b2d0-7f1e-7abc-8d2c-123456789012",
          "itemName": "Cà phê sữa",
          "variantId": "0192b2d0-7f20-7abc-8d2c-123456789012",
          "variantName": "STANDARD",
          "modifierOptionIds": []
        }
      ],
      "catalogVersion": 19,
      "version": 1,
      "createdAt": "2026-10-12T09:30:00Z",
      "updatedAt": "2026-10-12T09:30:00Z"
    }
  ],
  "meta": {
    "pagination": {
      "page": 1,
      "limit": 20,
      "total": 1,
      "totalPages": 1
    },
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

- **Mã lỗi khả dĩ:** `401 Unauthorized`, `403 Forbidden`, `422 Unprocessable Entity`.

---

#### `POST /api/v1/catalog/combos`
Tạo mới một combo với mức giá cố định (`fixedPrice`).

- **Thẩm quyền:** Quyền `catalog:manage` (`OWNER`, `MANAGER`).
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `Idempotency-Key: <unique-key>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Khuyến nghị)
  - `Content-Type: application/json; charset=utf-8` (Bắt buộc)
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "name": "Combo Ăn Sáng Tiết Kiệm",
  "description": "Bánh mì kẹp thịt + Cà phê sữa đá",
  "fixedPrice": { "amountMinor": 45000, "currency": "VND" },
  "components": [
    {
      "catalogItemId": "0192b2d0-7f1e-7abc-8d2c-123456789012",
      "variantId": "0192b2d0-7f20-7abc-8d2c-123456789012",
      "modifierOptionIds": []
    }
  ]
}
```

*Quy cách kiểm tra (Validation):*
- `name`: 1–200 ký tự Unicode, bắt buộc.
- `fixedPrice.amountMinor`: Số nguyên `>= 0`, đơn vị tiền tệ bắt buộc `"VND"`.
- `components`: Mảng từ 2 đến 10 món thành phần. Mỗi thành phần trỏ tới `catalogItemId` và `variantId` hợp lệ thuộc cùng tenant. Bất biến: Thành phần combo không được phép là một combo khác.

- **Phản hồi Thành công (`201 Created`):** Trả về `ComboResponse`.
- **Mã lỗi khả dĩ:** `403 Forbidden`, `409 duplicate_request`, `422 validation_error`.

---

#### `GET /api/v1/catalog/combos/{comboId}`
Xem chi tiết một combo cụ thể.

- **Thẩm quyền:** Quyền `catalog:read`.
- **Tiêu đề Yêu cầu (Request Headers):** `Authorization`, `X-Correlation-Id`.
- **Tham số Đường dẫn (Path Parameters):** `comboId` (UUID, Bắt buộc).
- **Phản hồi Thành công (`200 OK`):** Trả về `ComboResponse`.
- **Mã lỗi khả dĩ:** `401 Unauthorized`, `403 Forbidden`, `404 Not Found`.

---

#### `PATCH /api/v1/catalog/combos/{comboId}`
Cập nhật tên, mô tả, giá cố định hoặc các thành phần của combo.

- **Thẩm quyền:** Quyền `catalog:manage`.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `If-Match: "combo-v1"` (Bắt buộc)
  - `Idempotency-Key: <unique-key>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Khuyến nghị)
  - `Content-Type: application/json; charset=utf-8` (Bắt buộc)
- **Tham số Đường dẫn (Path Parameters):** `comboId` (UUID, Bắt buộc).
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "name": "Combo Ăn Sáng Đặc Biệt",
  "fixedPrice": { "amountMinor": 49000, "currency": "VND" }
}
```

- **Phản hồi Thành công (`200 OK`):** Trả về `ComboResponse` với version mới.
- **Mã lỗi khả dĩ:** `403 Forbidden`, `404 Not Found`, `409 catalog_version_conflict`, `422 validation_error`.

---

#### `POST /api/v1/catalog/combos/{comboId}/state`
Đổi trạng thái của combo (`ACTIVE`, `INACTIVE`, `ARCHIVED`).

- **Thẩm quyền:** Quyền `catalog:manage`.
- **Tiêu đề Yêu cầu (Request Headers):** `Authorization`, `If-Match: "combo-v2"`, `Idempotency-Key`, `X-Correlation-Id`, `Content-Type`.
- **Tham số Đường dẫn (Path Parameters):** `comboId` (UUID, Bắt buộc).
- **Dữ liệu Gửi lên (Request Body):** `{ "status": "INACTIVE", "reason": "Tạm dừng chương trình combo" }`.
- **Phản hồi Thành công (`200 OK`):** Trả về `ComboResponse`.
- **Mã lỗi khả dĩ:** `403 Forbidden`, `404 Not Found`, `409 catalog_version_conflict`.

---

#### `PUT /api/v1/catalog/branches/{branchId}/variants/{variantId}/price`
Thiết lập hoặc cập nhật mức giá ghi đè theo chi nhánh cho một biến thể món ăn (Branch Price Override).

- **Thẩm quyền:** Quyền `catalog:manage` tại chi nhánh đó.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `X-Active-Branch-Id: <uuid>` (Bắt buộc - phải khớp với `branchId` trên URL)
  - `Idempotency-Key: <unique-key>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Khuyến nghị)
  - `Content-Type: application/json; charset=utf-8` (Bắt buộc)
- **Tham số Đường dẫn (Path Parameters):**
  - `branchId` (UUID, Bắt buộc)
  - `variantId` (UUID, Bắt buộc)
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "price": { "amountMinor": 32000, "currency": "VND" }
}
```

*Quy cách kiểm tra:* `price.amountMinor` là số nguyên `>= 0`, đơn vị `"VND"`. Biến thể và chi nhánh phải thuộc cùng tenant.

- **Phản hồi Thành công (`200 OK`):**

```json
{
  "data": {
    "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
    "branchId": "0192b2d0-7f23-7abc-8d2c-123456789012",
    "variantId": "0192b2d0-7f20-7abc-8d2c-123456789012",
    "price": { "amountMinor": 32000, "currency": "VND" },
    "createdAt": "2026-10-12T09:40:00Z",
    "updatedAt": "2026-10-12T09:40:00Z"
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

- **Mã lỗi khả dĩ:** `403 Forbidden`, `404 Not Found`, `422 validation_error`.

---

#### `DELETE /api/v1/catalog/branches/{branchId}/variants/{variantId}/price`
Xóa bản ghi đè giá chi nhánh. Sau khi xóa, giá của biến thể tại chi nhánh đó sẽ tự động quay trở về mức giá cơ sở toàn tenant.

- **Thẩm quyền:** Quyền `catalog:manage` tại chi nhánh đó.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `X-Active-Branch-Id: <uuid>` (Bắt buộc)
  - `Idempotency-Key: <unique-key>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Khuyến nghị)
- **Tham số Đường dẫn (Path Parameters):**
  - `branchId` (UUID, Bắt buộc)
  - `variantId` (UUID, Bắt buộc)
- **Phản hồi Thành công (`204 No Content`):** Không có body.
- **Mã lỗi khả dĩ:** `403 Forbidden`, `404 Not Found`.

---

#### `GET /api/v1/catalog/tags`
Lấy danh sách các nhãn phân loại (Tags) trong tenant.

- **Thẩm quyền:** Quyền `catalog:read`.
- **Tiêu đề Yêu cầu (Request Headers):** `Authorization`, `X-Correlation-Id`.
- **Phản hồi Thành công (`200 OK`):**

```json
{
  "data": [
    {
      "id": "0192b2d0-7f80-7abc-8d2c-123456789012",
      "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
      "name": "Món Bán Chạy",
      "slug": "best-seller",
      "createdAt": "2026-10-12T08:00:00Z"
    }
  ],
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

---

#### `POST /api/v1/catalog/tags`
Tạo mới một nhãn phân loại trong tenant.

- **Thẩm quyền:** Quyền `catalog:manage`.
- **Tiêu đề Yêu cầu (Request Headers):** `Authorization`, `Idempotency-Key`, `X-Correlation-Id`, `Content-Type`.
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "name": "Món Mới Ra Mắt",
  "slug": "new-arrival"
}
```

*Quy cách kiểm tra:* `name` từ 1–100 ký tự; `slug` từ 1–100 ký tự chữ thường và gạch nối (`^[a-z0-9-]+$`), duy nhất trong tenant.

- **Phản hồi Thành công (`201 Created`):** Trả về `TagResponse`.
- **Mã lỗi khả dĩ:** `403 Forbidden`, `409 conflict` (trùng slug), `422 validation_error`.

---

#### `PUT /api/v1/catalog/items/{itemId}/tags`
Thay thế toàn bộ tập hợp Tag được gán cho món ăn một cách nguyên tử.

- **Thẩm quyền:** Quyền `catalog:manage`.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `If-Match: "item-v6"` (Bắt buộc - ETag của món ăn)
  - `Idempotency-Key: <unique-key>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Khuyến nghị)
  - `Content-Type: application/json; charset=utf-8` (Bắt buộc)
- **Tham số Đường dẫn (Path Parameters):** `itemId` (UUID, Bắt buộc).
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "tagIds": [
    "0192b2d0-7f80-7abc-8d2c-123456789012"
  ]
}
```

*Quy cách kiểm tra:* Toàn bộ `tagIds` phải tồn tại và thuộc cùng một tenant.

- **Phản hồi Thành công (`200 OK`):** Trả về `CatalogItemResponse` với danh sách tag cập nhật.
- **Mã lỗi khả dĩ:** `403 Forbidden`, `404 Not Found`, `409 catalog_version_conflict`, `422 foreign_resource`.

---

## Hợp đồng API Nội bộ (Internal API Contracts)

### `POST /internal/v1/catalog/resolutions`
Endpoint bộ phân giải Đơn hàng nội bộ (Order Resolver). Được gọi bởi Order Service trong quá trình tạo hoặc tính lại giá đơn hàng nháp (`reprice`). Endpoint này chịu trách nhiệm xác thực tính hợp lệ và định giá toàn bộ giỏ hàng dựa trên một snapshot Catalog nhất quán duy nhất.

- **Thẩm quyền:** Workload Identity / mTLS của Order Service với quyền `catalog:resolve`.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `X-Correlation-Id: <uuid>` (Bắt buộc)
  - `Content-Type: application/json; charset=utf-8` (Bắt buộc)
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
  "branchId": "0192b2d0-7f23-7abc-8d2c-123456789012",
  "requestedCatalogVersion": 18,
  "lines": [
    {
      "clientLineId": "line-1",
      "catalogItemId": "0192b2d0-7f1e-7abc-8d2c-123456789012",
      "variantId": "0192b2d0-7f20-7abc-8d2c-123456789012",
      "modifierOptionIds": ["0192b2d0-7f22-7abc-8d2c-123456789012"],
      "quantity": 2
    }
  ]
}
```

*Quy cách kiểm tra:*
- `lines`: Từ 1 đến 100 dòng món; từ chối nếu có `clientLineId` bị trùng.
- `variantId`: Bắt buộc cung cấp. Giá trị null kiểu cũ chỉ phân giải được khi món có duy nhất một biến thể active.
- `quantity`: Số nguyên từ 1 đến 999 (chỉ dùng cho tính toán giá, Catalog không kiểm tra hay giữ chỗ tồn kho).

- **Phản hồi Thành công (`200 OK` - Giỏ hàng Hợp lệ `valid: true`):**

```json
{
  "data": {
    "valid": true,
    "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
    "branchId": "0192b2d0-7f23-7abc-8d2c-123456789012",
    "catalogVersion": 18,
    "lines": [
      {
        "clientLineId": "line-1",
        "catalogItemId": "0192b2d0-7f1e-7abc-8d2c-123456789012",
        "itemName": "Cà phê sữa",
        "variantId": "0192b2d0-7f20-7abc-8d2c-123456789012",
        "variantName": "STANDARD",
        "selectedModifiers": [
          {
            "optionId": "0192b2d0-7f22-7abc-8d2c-123456789012",
            "optionName": "Trân châu đen",
            "priceDelta": { "amountMinor": 5000, "currency": "VND" }
          }
        ],
        "baseUnitPrice": { "amountMinor": 29000, "currency": "VND" },
        "modifierUnitPrice": { "amountMinor": 5000, "currency": "VND" },
        "unitPrice": { "amountMinor": 34000, "currency": "VND" },
        "quantity": 2,
        "itemVersion": 4,
        "variantVersion": 2
      }
    ]
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

- **Phản hồi Từ chối Nghiệp vụ (`200 OK` - Giỏ hàng Không Hợp lệ `valid: false`):**
Khi yêu cầu được xử lý thành công nhưng có món/biến thể đã ngừng bán hoặc vi phạm quy tắc modifier, hệ thống vẫn trả về HTTP 200 kèm `data.valid: false` và danh sách chi tiết các lý do từ chối:

```json
{
  "data": {
    "valid": false,
    "catalogVersion": 19,
    "lines": [],
    "rejections": [
      {
        "clientLineId": "line-1",
        "code": "invalid_variant_selection",
        "reason": "variant_inactive",
        "field": "lines[0].variantId"
      }
    ]
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

*Các mã lý do từ chối chuẩn:* `item_not_found`, `item_inactive`, `variant_required`, `variant_ambiguous`, `variant_inactive`, `modifier_group_required`, `modifier_group_too_few`, `modifier_group_too_many`, `modifier_duplicate`, `modifier_inactive`, `modifier_not_attached`, `category_inactive`, `catalog_version_changed`.

- **Mã lỗi tầng hạ tầng:** `401 Unauthorized`, `403 Forbidden` (sai workload identity), `422 Unprocessable Entity`, `503 Service Unavailable`.

---

## Cấu trúc Schema Dùng lại (Reusable Schemas)

### 1. `CategoryResponse`

| Tên trường | Kiểu dữ liệu | Bắt buộc | Mô tả ý nghĩa |
|---|---|:---:|---|
| `id` | UUID string | Có | Mã định danh danh mục UUID v7. |
| `tenantId` | UUID string | Có | Tenant sở hữu danh mục. |
| `name` | string | Có | Tên hiển thị của danh mục (1–120 ký tự). |
| `displayOrder` | integer | Có | Thứ tự hiển thị số nguyên không âm. |
| `status` | enum | Có | `ACTIVE`, `INACTIVE`, `ARCHIVED`. |
| `itemCount` | integer | Có | Số lượng món thuộc danh mục (mang tính thông tin). |
| `version` | integer | Có | Phiên bản dùng cho kiểm tra `If-Match`. |
| `createdAt` / `updatedAt` | ISO-8601 UTC | Có | Mốc thời gian tạo và cập nhật UTC. |

### 2. `CatalogItemResponse`

| Tên trường | Kiểu dữ liệu | Bắt buộc | Mô tả ý nghĩa |
|---|---|:---:|---|
| `id` | UUID string | Có | Mã định danh món ăn UUID v7. |
| `tenantId` | UUID string | Có | Tenant sở hữu món ăn. |
| `sku` | string | Có | Mã SKU duy nhất trong tenant (1–64 ký tự). |
| `name` | string | Có | Tên món ăn hiển thị (1–200 ký tự). |
| `description` | string \| null | Không | Mô tả món ăn (tối đa 2.000 ký tự). |
| `category` | object | Có | Chứa `id` và `name` của danh mục sở hữu. |
| `status` | enum | Có | `ACTIVE`, `INACTIVE`, `ARCHIVED`. |
| `variants` | array | Có | Danh sách các biến thể của món. |
| `modifierGroups` | array | Có | Danh sách các nhóm modifier được gắn vào món. |
| `catalogVersion` | integer | Có | Phiên bản sửa đổi toàn tenant phục vụ ETag cache. |
| `version` | integer | Có | Phiên bản dùng cho kiểm tra `If-Match`. |
| `createdAt` / `updatedAt` | ISO-8601 UTC | Có | Dấu vết thời gian kiểm toán. |

### 3. `VariantResponse`

| Tên trường | Kiểu dữ liệu | Bắt buộc | Mô tả ý nghĩa |
|---|---|:---:|---|
| `id` | UUID string | Có | Mã định danh biến thể UUID v7. |
| `name` | string | Có | Tên biến thể (ví dụ: `SIZE_M`, `STANDARD`). |
| `price` | Money object | Có | Đối tượng `{ "amountMinor": 35000, "currency": "VND" }`. |
| `isDefault` | boolean | Có | Có phải là biến thể mặc định của món hay không. |
| `status` | enum | Có | `ACTIVE`, `INACTIVE`, `ARCHIVED`. |
| `displayOrder` | integer | Có | Thứ tự hiển thị. |
| `version` | integer | Có | Phiên bản khóa lạc quan. |

### 4. `ModifierGroupResponse`

| Tên trường | Kiểu dữ liệu | Bắt buộc | Mô tả ý nghĩa |
|---|---|:---:|---|
| `id` | UUID string | Có | Mã định danh nhóm modifier. |
| `name` | string | Có | Tên nhóm (ví dụ: `Topping`, `Mức đường`). |
| `minSelections` | integer | Có | Số lựa chọn tối thiểu (`0` là tùy chọn, `>= 1` là bắt buộc). |
| `maxSelections` | integer | Có | Số lựa chọn tối đa cho phép. |
| `displayOrder` | integer | Có | Thứ tự hiển thị. |
| `status` | enum | Có | `ACTIVE`, `INACTIVE`, `ARCHIVED`. |
| `options` | array | Có | Danh sách các tùy chọn modifier trong nhóm. |
| `version` | integer | Có | Phiên bản khóa lạc quan. |

### 5. `ModifierOptionResponse`

| Tên trường | Kiểu dữ liệu | Bắt buộc | Mô tả ý nghĩa |
|---|---|:---:|---|
| `id` | UUID string | Có | Mã định danh tùy chọn modifier. |
| `name` | string | Có | Tên tùy chọn (ví dụ: `Trân châu đen`, `50% Đường`). |
| `priceDelta` | Money object | Có | Mức giá phụ thu cộng thêm (VND). |
| `status` | enum | Có | `ACTIVE`, `INACTIVE`, `ARCHIVED`. |
| `displayOrder` | integer | Có | Thứ tự hiển thị trong nhóm. |
| `version` | integer | Có | Phiên bản khóa lạc quan. |

---

## Cấu trúc Cơ sở Dữ liệu (Database Schema)

Cơ sở dữ liệu Catalog sử dụng PostgreSQL 16+ / Aurora PostgreSQL. Mọi bảng đều chứa `tenant_id` phục vụ kiểm soát đa tenant tuyệt đối; không có khóa ngoại vật lý chéo sang database của service khác.

### 1. Bảng `categories` (Danh mục thực đơn)

| Cột | Kiểu | Null | Ràng buộc | Chú thích cột |
|---|---|:---:|---|---|
| `id` | uuid | Không | PK | Mã định danh danh mục UUID v7. |
| `tenant_id` | uuid | Không | — | Ngữ cảnh tenant sở hữu. |
| `name` | text | Không | `CHECK length(name) BETWEEN 1 AND 120` | Tên danh mục hiển thị. |
| `normalized_name` | text | Không | Duy nhất theo tenant | Tên chuẩn hóa chữ thường phục vụ index duy nhất. |
| `display_order` | integer | Không | Mặc định 0, `>= 0` | Thứ tự hiển thị. |
| `status` | text | Không | `ACTIVE/INACTIVE/ARCHIVED` | Trạng thái vòng đời danh mục. |
| `version` | bigint | Không | Mặc định 1 | Phiên bản khóa lạc quan `If-Match`. |
| `created_at` / `updated_at` | timestamptz | Không | Mặc định `now()` | Thời điểm tạo và cập nhật UTC. |

### 2. Bảng `catalog_items` (Món ăn)

| Cột | Kiểu | Null | Ràng buộc | Chú thích cột |
|---|---|:---:|---|---|
| `id` | uuid | Không | PK | Mã định danh món ăn UUID v7. |
| `tenant_id` | uuid | Không | — | Ngữ cảnh tenant sở hữu. |
| `category_id` | uuid | Không | FK `categories(id)` | Thuộc danh mục nào. |
| `sku` | text | Không | 1–64 ký tự | Mã SKU gốc do người dùng đặt. |
| `normalized_sku` | text | Không | Duy nhất theo tenant | SKU chuẩn hóa chữ hoa phục vụ kiểm tra trùng lặp. |
| `name` | text | Không | `CHECK length(name) BETWEEN 1 AND 200` | Tên hiển thị món ăn. |
| `description` | text | Có | Tối đa 2.000 ký tự | Mô tả chi tiết món ăn. |
| `display_order` | integer | Không | Mặc định 0 | Thứ tự hiển thị. |
| `status` | text | Không | `ACTIVE/INACTIVE/ARCHIVED` | Trạng thái món ăn. |
| `default_variant_id` | uuid | Có | FK `catalog_variants(id)` | Biến thể mặc định của món. |
| `version` | bigint | Không | Mặc định 1 | Phiên bản khóa lạc quan `If-Match`. |
| `created_at` / `updated_at` | timestamptz | Không | Mặc định `now()` | Dấu vết thời gian kiểm toán. |

### 3. Bảng `catalog_variants` (Biến thể kích cỡ / Giá)

| Cột | Kiểu | Null | Ràng buộc | Chú thích cột |
|---|---|:---:|---|---|
| `id` | uuid | Không | PK | Mã định danh biến thể UUID v7. |
| `tenant_id` | uuid | Không | — | Tenant sở hữu. |
| `item_id` | uuid | Không | FK `catalog_items(id)` | Thuộc món ăn nào. |
| `name` | text | Không | 1–100 ký tự | Tên biến thể (ví dụ: `SIZE_M`, `SIZE_L`). |
| `price_minor` | bigint | Không | `CHECK price_minor >= 0` | Giá cơ sở nguyên VND. |
| `currency` | text | Không | Mặc định `'VND'` | Đơn vị tiền tệ. |
| `is_default` | boolean | Không | Mặc định `false` | Có phải biến thể mặc định không. |
| `display_order` | integer | Không | Mặc định 0 | Thứ tự hiển thị. |
| `status` | text | Không | `ACTIVE/INACTIVE/ARCHIVED` | Trạng thái biến thể. |
| `version` | bigint | Không | Mặc định 1 | Phiên bản khóa lạc quan. |
| `created_at` / `updated_at` | timestamptz | Không | Mặc định `now()` | Dấu vết thời gian. |

### 4. Bảng `modifier_groups` (Nhóm Modifier)

| Cột | Kiểu | Null | Ràng buộc | Chú thích cột |
|---|---|:---:|---|---|
| `id` | uuid | Không | PK | Mã định danh nhóm modifier. |
| `tenant_id` | uuid | Không | — | Tenant sở hữu. |
| `name` | text | Không | 1–120 ký tự | Tên nhóm modifier. |
| `normalized_name` | text | Không | Duy nhất theo tenant | Tên chuẩn hóa chữ thường. |
| `min_selections` | integer | Không | `>= 0` | Số lựa chọn tối thiểu mặc định. |
| `max_selections` | integer | Không | `>= min_selections` | Số lựa chọn tối đa mặc định. |
| `display_order` | integer | Không | Mặc định 0 | Thứ tự hiển thị. |
| `status` | text | Không | `ACTIVE/INACTIVE/ARCHIVED` | Trạng thái nhóm modifier. |
| `version` | bigint | Không | Mặc định 1 | Phiên bản khóa lạc quan. |
| `created_at` / `updated_at` | timestamptz | Không | Mặc định `now()` | Dấu vết thời gian. |

### 5. Bảng `modifiers` (Tùy chọn Modifier)

| Cột | Kiểu | Null | Ràng buộc | Chú thích cột |
|---|---|:---:|---|---|
| `id` | uuid | Không | PK | Mã định danh tùy chọn modifier. |
| `tenant_id` | uuid | Không | — | Tenant sở hữu. |
| `group_id` | uuid | Không | FK `modifier_groups(id)` | Thuộc nhóm modifier nào. |
| `name` | text | Không | 1–120 ký tự | Tên tùy chọn modifier. |
| `price_delta_minor` | bigint | Không | Mặc định 0, `>= 0` | Mức giá phụ thu cộng thêm (VND). |
| `currency` | text | Không | Mặc định `'VND'` | Đơn vị tiền tệ. |
| `display_order` | integer | Không | Mặc định 0 | Thứ tự hiển thị trong nhóm. |
| `status` | text | Không | `ACTIVE/INACTIVE/ARCHIVED` | Trạng thái tùy chọn. |
| `version` | bigint | Không | Mặc định 1 | Phiên bản khóa lạc quan. |
| `created_at` / `updated_at` | timestamptz | Không | Mặc định `now()` | Dấu vết thời gian. |

### 6. Bảng `item_modifier_groups` (Liên kết Món ăn - Nhóm Modifier)

| Cột | Kiểu | Null | Ràng buộc | Chú thích cột |
|---|---|:---:|---|---|
| `item_id` | uuid | Không | FK `catalog_items(id)` | Khóa chính phức hợp. |
| `group_id` | uuid | Không | FK `modifier_groups(id)` | Khóa chính phức hợp. |
| `tenant_id` | uuid | Không | — | Đảm bảo cùng tenant. |
| `override_min` | integer | Có | `>= 0` | Giá trị min ghi đè cho món. |
| `override_max` | integer | Có | `>= override_min` | Giá trị max ghi đè cho món. |
| `display_order` | integer | Không | Mặc định 0 | Thứ tự hiển thị của nhóm trong món. |

### 7. Bảng `tenant_catalog_revisions` (Đếm phiên bản sửa đổi Catalog)

| Cột | Kiểu | Null | Ràng buộc | Chú thích cột |
|---|---|:---:|---|---|
| `tenant_id` | uuid | Không | PK | Mỗi tenant có chính xác một bản ghi. |
| `revision` | bigint | Không | Mặc định 1, tăng đơn điệu | Bộ đếm revision toàn tenant cho ETag và outbox. |
| `updated_at` | timestamptz | Không | Mặc định `now()` | Thời điểm sửa đổi gần nhất. |

### 8. Bảng `outbox_messages` (Transactional Outbox)

| Cột | Kiểu | Null | Ràng buộc | Chú thích cột |
|---|---|:---:|---|---|
| `id` | uuid | Không | PK | Mã sự kiện UUID v7 để khử trùng lặp. |
| `tenant_id` | uuid | Không | — | Ngữ cảnh tenant. |
| `branch_id` | uuid | Có | — | Null ở P0; có giá trị ở P1 branch price. |
| `event_type` | text | Không | — | Tên sự kiện (ví dụ: `catalog.item.changed`). |
| `event_version` | integer | Không | Mặc định 1 | Phiên bản cấu trúc sự kiện. |
| `catalog_revision` | bigint | Không | — | Phiên bản catalog tại thời điểm phát sinh sự kiện. |
| `aggregate_type` | text | Không | — | `CATALOG_ITEM`, `CATEGORY`, v.v. |
| `aggregate_id` | uuid | Không | — | Định danh đối tượng phát sinh sự kiện. |
| `payload` | jsonb | Không | — | Dữ liệu sự kiện an toàn (không chứa secret). |
| `correlation_id` | text | Không | — | Mã truy vết luồng. |
| `publish_status` | text | Không | `PENDING/PUBLISHED/FAILED` | Trạng thái gửi lên broker. |
| `attempt_count` | integer | Không | Mặc định 0 | Số lần đã thử publish. |
| `next_attempt_at` | timestamptz | Có | — | Thời gian thử lại tiếp theo. |
| `created_at` | timestamptz | Không | Mặc định `now()` | Thời điểm tạo sự kiện. |

### 9. Bảng `idempotency_records` (Kiểm soát tính lũy kế)

| Cột | Kiểu | Null | Ràng buộc | Chú thích cột |
|---|---|:---:|---|---|
| `id` | uuid | Không | PK | Mã bản ghi idempotency. |
| `tenant_id` | uuid | Không | — | Ngữ cảnh tenant. |
| `actor_subject` | text | Không | — | Định danh người gọi. |
| `operation` | text | Không | — | Tên thao tác (ví dụ: `create_item`). |
| `idempotency_key` | text | Không | — | Khóa client gửi lên. |
| `request_hash` | text | Không | SHA-256 | Mã băm toàn bộ payload request. |
| `response_status` | integer | Không | — | HTTP status code gốc. |
| `response_metadata` | jsonb | Không | — | Nội dung phản hồi an toàn để replay. |
| `expires_at` | timestamptz | Không | — | Thời hạn lưu trữ trước khi dọn dẹp. |
| `created_at` | timestamptz | Không | Mặc định `now()` | Thời điểm tạo bản ghi. |

---

## Danh mục Chỉ mục Cơ sở Dữ liệu (Database Indexes)

| Tên bảng | Tên chỉ mục / ràng buộc | Các cột (Columns) | Loại chỉ mục | Mục đích tối ưu hóa |
|---|---|---|---|---|
| `categories` | `uq_categories_tenant_name` | `(tenant_id, normalized_name)` | Unique B-tree | Tên danh mục duy nhất trong tenant. |
| `categories` | `idx_categories_tenant_status` | `(tenant_id, status, display_order)` | B-tree | Tối ưu hóa truy vấn menu và bảng quản trị. |
| `catalog_items` | `uq_catalog_items_tenant_sku` | `(tenant_id, normalized_sku)` | Unique B-tree | Mã SKU duy nhất trong tenant. |
| `catalog_items` | `idx_catalog_items_pos_read` | `(tenant_id, status, category_id, display_order, id)` | B-tree | Tối ưu hóa API đọc menu POS `view=sellable`. |
| `catalog_variants` | `idx_catalog_variants_item` | `(tenant_id, item_id, status, display_order, id)` | B-tree | Tối ưu hóa nạp các biến thể active của món. |
| `modifier_groups` | `uq_modifier_groups_tenant_name` | `(tenant_id, normalized_name)` | Unique B-tree | Tên nhóm modifier duy nhất trong tenant. |
| `modifiers` | `idx_modifiers_group` | `(tenant_id, group_id, status, display_order, id)` | B-tree | Nạp nhanh các tùy chọn trong nhóm modifier. |
| `item_modifier_groups` | `idx_item_mod_groups_item` | `(tenant_id, item_id, display_order, group_id)` | B-tree | Nạp các nhóm modifier được gắn vào món ăn. |
| `outbox_messages` | `idx_outbox_catalog_queue` | `(publish_status, next_attempt_at, created_at)` | B-tree | Tiến trình Outbox Publisher thăm dò sự kiện cần gửi. |
| `idempotency_records` | `uq_idempotency_catalog_actor` | `(tenant_id, operation, actor_subject, idempotency_key)` | Unique B-tree | Phát hiện trùng lặp khóa và request hash. |

---

## Kế hoạch Thực thi Cơ sở Dữ liệu với Flyway

| Thứ tự | Tên File Script Migration | Nội dung chính | Lưu ý Rollback / Phục hồi |
|:---:|---|---|---|
| 1 | `V1__create_catalog_extensions.sql` | Cấu hình extension UUID và các hàm tiện ích. | Forward-only. |
| 2 | `V2__create_categories_and_items.sql` | Tạo bảng `categories`, `catalog_items`, `catalog_variants`, ràng buộc SKU và FK. | Khôi phục từ bản backup; không thực hiện DROP phá hủy. |
| 3 | `V3__create_modifiers_and_links.sql` | Tạo bảng `modifier_groups`, `modifiers`, `item_modifier_groups` và các check constraint. | Điền dữ liệu trước khi bật ràng buộc trong DB có sẵn dữ liệu. |
| 4 | `V4__create_catalog_revisions.sql` | Tạo bảng `tenant_catalog_revisions` và hàm tiện ích tăng revision đơn điệu. | Bảo toàn số revision hiện tại. |
| 5 | `V5__create_idempotency_outbox.sql` | Tạo bảng `idempotency_records` và `outbox_messages`. | Giữ lại các sự kiện để replay; không xóa bản ghi chưa publish. |
| 6 | `V6__create_p1_combos_branch_prices_tags.sql` | Tạo các bảng `combos`, `branch_variant_prices`, `tags` (bảo vệ sau cờ tính năng P1). | Triển khai schema trước khi bật feature flag; tắt cờ trước khi rollback. |
| 7 | `V7__seed_local_demo_catalog.sql` | Nạp dữ liệu menu mẫu (Cà phê, Trà sữa, Topping) cho môi trường local demo. | Chỉ chạy trên môi trường local demo; tuyệt đối không chạy trên production. |

---

## Hợp đồng Sự kiện Tích hợp (Integration Event Contracts)

### Cấu trúc Bao bì Sự kiện Chuẩn (Event Envelope)

```json
{
  "eventId": "0192b2d0-7f30-7abc-8d2c-123456789012",
  "eventType": "catalog.item.changed",
  "eventVersion": 1,
  "occurredAt": "2026-10-12T08:10:00Z",
  "producer": "catalog-service",
  "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
  "branchId": null,
  "scope": "TENANT",
  "aggregateType": "CATALOG_ITEM",
  "aggregateId": "0192b2d0-7f1e-7abc-8d2c-123456789012",
  "catalogVersion": 18,
  "correlationId": "cor_01J...",
  "payload": {
    "changeType": "UPDATED",
    "itemId": "0192b2d0-7f1e-7abc-8d2c-123456789012",
    "categoryId": "0192b2d0-7f1c-7abc-8d2c-123456789012",
    "sku": "CF-SUA",
    "status": "ACTIVE",
    "version": 4,
    "sellable": true,
    "changedFields": ["name", "description"]
  }
}
```

*Lưu ý về `branchId`:* Trường `branchId` mang giá trị `null` đối với toàn bộ các sự kiện P0 toàn chuỗi (`scope: "TENANT"`). Các sự kiện giá chi nhánh ở P1 sẽ đặt giá trị này là chi nhánh bị ảnh hưởng.

### Danh mục Sự kiện Domain (Event Registry)

| Kiểu sự kiện | Giai đoạn | Mục đích của Payload | Các bên tiêu thụ (Consumers) |
|---|:---:|---|---|
| `catalog.category.changed` | P0 | Tên, thứ tự hiển thị, trạng thái danh mục và `catalogVersion`. | POS Cache, Reporting, Audit. |
| `catalog.item.changed` | P0 | Định danh món, danh mục, trạng thái, SKU, khả năng bán và `catalogVersion`. | POS Cache, Order Cache, Reporting, Recommendation. |
| `catalog.variant.changed` | P0 | Định danh biến thể, ID món, giá cơ sở, trạng thái mặc định và version. | POS Cache, Order Cache, Reporting, Inventory reference. |
| `catalog.modifier-group.changed` | P0 | Nhóm, danh sách tùy chọn, liên kết món và quy tắc lựa chọn. | POS Cache, Recommendation, Reporting. |
| `catalog.combo.changed` | P1 | Cấu thành combo, thành phần món, giá cố định và trạng thái. | POS Cache, Recommendation, Reporting. |
| `catalog.branch-price.changed` | P1 | Ghi đè giá biến thể theo chi nhánh được thiết lập hoặc xóa bỏ. | POS Cache, Order Cache, Reporting. |
| `catalog.item-metadata.changed` | P1 | Mô tả mở rộng, danh sách tag phục vụ thuật toán AI / gợi ý món. | Recommendation / AI Engine, Reporting. |

---

## Ma trận Kiểm thử Hợp đồng (Contract Test Requirements)

1. **Kiểm thử OpenAPI:** Tự động đối soát toàn bộ request, response body, pagination envelope, ETag header và error envelope với định nghĩa OpenAPI 3.1.
2. **Kiểm thử Thẩm quyền & Phân quyền:**
   - `OWNER` và `MANAGER` đang active có thể thực hiện mọi thao tác biến đổi Catalog trên toàn chuỗi.
   - `CASHIER` và `KITCHEN` nhận mã HTTP `403 Forbidden` đối với mọi thao tác biến đổi (POST, PATCH, PUT, DELETE).
3. **Kiểm thử Cô lập Đa Tenant & IDOR:** Manager từ tenant A không thể đọc hoặc sửa đổi dữ liệu của tenant B bằng cách truyền ID qua path hoặc body; tài nguyên thuộc tenant khác phải trả về `404 Not Found` (không làm lộ sự tồn tại của dữ liệu).
4. **Kiểm thử Fail-Closed:** Khi không thể kết nối sang Identity Access để phân giải quyền, Catalog bắt buộc phải từ chối truy cập an toàn với mã `503 Service Unavailable`.
5. **Kiểm thử Khóa Lạc quan (If-Match):** Gửi header `If-Match` lỗi thời bắt buộc phải bị từ chối với lỗi `409 catalog_version_conflict` kèm ETag hiện tại.
6. **Kiểm thử Tính Lũy kế (Idempotency):**
   - Gửi lại cùng một `Idempotency-Key` và cùng nội dung request body phải phát lại kết quả cũ mà không tạo thêm bản ghi hay sự kiện outbox thứ hai.
   - Gửi cùng `Idempotency-Key` nhưng khác request body phải trả về mã `409 duplicate_request`.
7. **Kiểm thử Bất biến Nghiệp vụ:**
   - Không cho phép vô hiệu hóa hoặc lưu trữ biến thể nếu việc đó làm mất đi biến thể active cuối cùng của món (`last_active_variant`).
   - Không cho phép lưu trữ danh mục khi vẫn còn món ăn chưa lưu trữ (`category_has_items`).
   - Kiểm tra tính duy nhất của mã SKU trong cùng tenant (`sku_conflict`).
8. **Kiểm thử Bộ phân giải Đơn hàng (Order Resolver):**
   - Xác thực các giỏ hàng hợp lệ, trả về giá cơ sở, phụ thu modifier và `catalogVersion` nhất quán.
   - Từ chối các lựa chọn không hợp lệ bằng phản hồi có định kiểu `valid: false` kèm mã lý do chính xác.
   - Đảm bảo resolver không bao giờ gọi sang Inventory DB hay ghi dữ liệu vào Order DB.
9. **Kiểm thử Bộ nhớ đệm (Cache & ETag):** Gửi `If-None-Match` khớp với ETag hiện tại trả về mã `304 Not Modified` chỉ sau khi đã xác thực và kiểm tra phân quyền thành công.
