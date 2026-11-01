Started at: 2026/10/12 15:52:00 +07
Finished at: 2026/10/12 15:52:00 +07
Total time: 0 minutes
---

# Hợp đồng Dữ liệu & API: Catalog Service (Dịch vụ Danh mục Món ăn)

## Nguồn tài liệu và Quyền hạn Hợp đồng

- [Tầm nhìn Catalog (Vision)](./2026-10-12-vision.md)
- [Yêu cầu Kỹ thuật Catalog (Requirements)](./2026-10-12-requirements.md)
- [Kiến trúc Catalog (Architecture)](./2026-10-12-architecture.md)
- [Hợp đồng Identity & Access](../identity-access/2026-10-12-contracts.md)
- [Hợp đồng Nền tảng POS](../pos-platform/2026-10-12-contracts.md)

Tài liệu này là hợp đồng quy chuẩn (normative contract) cho Catalog Service. Việc triển khai thực tế bắt buộc phải sinh và xác thực tài liệu OpenAPI 3.1 cùng các artifact AsyncAPI/JSON Schema từ các định nghĩa này trước bản phát hành mã nguồn đầu tiên.

## Các Nguyên tắc Hợp đồng

- Các API đồng bộ công khai sử dụng REST + OpenAPI 3.1 dưới tiền tố `/api/v1/catalog`.
- Các API giao tiếp giữa các service (service-to-service) sử dụng REST + OpenAPI 3.1 dưới tiền tố `/internal/v1/catalog` và bắt buộc phải có workload identity/mTLS. Chúng tuyệt đối không được đưa vào bảng định tuyến của Angular.
- Các sự kiện sử dụng cấu trúc bao bọc (envelope) của nền tảng và JSON Schema/AsyncAPI. Cơ chế chuyển phát là ít nhất một lần (at-least-once); các consumer khử trùng lặp theo `eventId`.
- Các ID của Catalog là chuỗi UUID v7. Timestamp là chuỗi ISO-8601 UTC. Tiền tệ là số nguyên không âm `int64` tính theo đơn vị minor VND, trong đó `amountMinor` là số tiền nguyên VND.
- Mọi yêu cầu công khai đều được phân quyền thông qua quyết định hiện tại từ Identity. Các claim role/branch trong JWT và các giá trị tenant do client gửi lên chỉ đóng vai trò gợi ý (hints), không bao giờ là nguồn thẩm quyền phân quyền.
- Ngữ cảnh tenant được trích xuất từ ngữ cảnh Identity đã xác thực. Catalog không đưa vào header `X-Active-Tenant-Id` và không bao giờ chấp nhận quyền sở hữu tenant từ request body hoặc query parameter. Header `X-Active-Branch-Id` chỉ được chấp nhận như một bộ chọn chi nhánh đã được máy chủ xác thực.
- Trạng thái thực đơn và giá cơ sở ở P0 có phạm vi trên toàn tenant. Một header chi nhánh vẫn bắt buộc đối với các vai trò có phạm vi chi nhánh để tư cách thành viên và quyền truy cập chi nhánh được kiểm tra ngay cả khi đồ thị thực đơn P0 trả về là toàn cầu.
- Không có thao tác xóa cứng (hard delete) trong P0. Các chuyển đổi trạng thái/lưu trữ (archive) bảo toàn các ID cần thiết cho các tham chiếu lịch sử của Order và Inventory.

## Hợp đồng HTTP Chung

### Các Header HTTP

| Header | Bắt buộc | Ý nghĩa |
|---|---:|---|
| `Authorization` | Các API công khai được bảo vệ | `Bearer <Cognito access token>`. Cognito xác thực danh tính người dùng; Identity phân giải tư cách thành viên và quyền hạn hiện tại. |
| `X-Correlation-Id` | Khuyến nghị | Giá trị tương quan của bên gọi; được service tự sinh nếu vắng mặt. Được trả về trong metadata thành công/lỗi. |
| `X-Active-Branch-Id` | Vai trò/lượt đọc theo chi nhánh | Chi nhánh do client chọn; được xác thực đối chiếu với tư cách thành viên đang hoạt động hiện tại. Không bao giờ được tin cậy đơn lẻ. Tùy chọn đối với các thao tác toàn tenant của Owner và các biến đổi của Manager. |
| `Idempotency-Key` | Thao tác POST/biến đổi trạng thái thử lại được | Khóa do client sinh có phạm vi actor + tenant + operation. Cùng một khóa bắt buộc phải mang cùng một hash yêu cầu. |
| `If-Match` | Cập nhật có phiên bản | ETag của tài nguyên từ lần đọc gần nhất, ví dụ `"catalog-item-v7"`. Bắt buộc cho các cập nhật và thay đổi quan hệ. |
| `Content-Type` | Yêu cầu có body | `application/json; charset=utf-8`. |

Các yêu cầu nội bộ ngoài ra còn mang thông tin xác thực workload identity/mTLS. Các client công khai không thể gọi `/internal/v1`.

### Cấu trúc Bao bọc Thành công (Success Envelope)

```json
{
  "data": {},
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

Các phản hồi danh sách (collection) bổ sung thêm phân trang offset:

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

### Cấu trúc Bao bọc Lỗi (Error Envelope)

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

Các mã lỗi chuẩn và mã lỗi đặc thù của Catalog:

| HTTP | Mã lỗi | Ý nghĩa |
|---:|---|---|
| 401 | `unauthorized` | Thiếu, không hợp lệ hoặc hết hạn Cognito JWT. |
| 403 | `forbidden` | Vai trò, tư cách thành viên, phạm vi tenant hoặc chi nhánh hiện tại không cho phép thao tác. |
| 404 | `not_found` | Tài nguyên không tồn tại trong phạm vi được ủy quyền của bên gọi. Tuyệt đối không làm lộ sự tồn tại của tài nguyên thuộc tenant khác. |
| 409 | `conflict` | Xung đột trạng thái chung. |
| 409 | `catalog_version_conflict` | Header `If-Match` đã lỗi thời. |
| 409 | `sku_conflict` | SKU đã được đặt trước bởi một món khác trong cùng tenant, bao gồm cả món đã lưu trữ. |
| 409 | `category_has_items` | Danh mục không thể lưu trữ khi nó vẫn còn chứa các món chưa lưu trữ. |
| 409 | `last_active_variant` | Thao tác sẽ khiến một món đang hoạt động không còn biến thể/biến thể mặc định nào đang hoạt động. |
| 409 | `invalid_required_modifier_rule` | Thao tác sẽ khiến quy tắc nhóm modifier bắt buộc trở nên bất khả thi. |
| 409 | `duplicate_request` | Cùng một idempotency key được tái sử dụng nhưng với một hash yêu cầu khác. |
| 409 | `archived_resource` | Một tài nguyên đã lưu trữ ở trạng thái kết thúc (terminal) không thể bị biến đổi. |
| 422 | `validation_error` | Xác thực payload, trường dữ liệu hoặc quy tắc nghiệp vụ thất bại. |
| 422 | `invalid_modifier_selection` | Tùy chọn được chọn thuộc tenant khác, không hoạt động, bị trùng lặp hoặc vi phạm min/max. |
| 422 | `invalid_variant_selection` | Biến thể bị thiếu, không hoạt động, thuộc tenant khác hoặc mơ hồ theo quy tắc phân giải kiểu cũ. |
| 422 | `foreign_resource` | Tài nguyên được tham chiếu không thuộc về tenant hiện tại. |
| 429 | `rate_limited` | Vượt quá giới hạn tốc độ tại Gateway hoặc service. |
| 503 | `authorization_unavailable` | Không thể phân giải an toàn quyết định phân quyền từ Identity; từ chối truy cập (fail closed). |
| 503 | `service_unavailable` | Catalog hoặc một dependency nội bộ bắt buộc tạm thời không khả dụng. |

Thông báo lỗi luôn an toàn đối với client. Tuyệt đối không trả về hoặc ghi log các chi tiết câu lệnh SQL, JWT, bí mật, mã PIN thô hoặc toàn bộ payload yêu cầu.

## Hợp đồng Phân quyền và Phạm vi (Scope)

Catalog gửi một yêu cầu quyết định tới Identity cho subject hiện tại, ngữ cảnh tenant được chọn, ngữ cảnh chi nhánh tùy chọn và một trong các quyền hạn sau:

| Quyền hạn | Vai trò | Phạm vi (Scope) | Mục đích sử dụng trong Catalog |
|---|---|---|---|
| `catalog:read` | Owner, Manager, Cashier, Kitchen | Tenant cộng với tư cách thành viên chi nhánh đã xác thực cho các vai trò chi nhánh | Các lượt đọc trên POS và quản trị. |
| `catalog:manage` | Owner, Manager | Toàn tenant; Manager cần có bất kỳ tư cách thành viên Manager nào đang hoạt động trong tenant được chọn | Các biến đổi danh mục, món ăn, biến thể và modifier. |
| `catalog:resolve` | Workload identity của Order | Yêu cầu tenant/chi nhánh đã xác thực, không có vai trò người dùng cuối | Phân giải Đơn hàng nội bộ. |

Các quy tắc:

1. Chủ sở hữu (Owner) có thể đọc/ghi trong tenant được chọn mà không cần header chi nhánh.
2. Quản lý (Manager) có thể đọc/ghi thực đơn dùng chung của tenant khi Identity xác nhận có tư cách thành viên Manager đang hoạt động tại bất kỳ chi nhánh nào đang hoạt động thuộc tenant đó. Header chi nhánh gửi lên được xác thực nhưng không làm thu hẹp biến đổi của Catalog; biến đổi được ghi audit là có tác động toàn tenant.
3. Thu ngân (Cashier) và Bếp/Bar (Kitchen) bắt buộc phải có tư cách thành viên chi nhánh đang hoạt động và chỉ có quyền đọc (read-only).
4. Bộ phân giải nội bộ/lịch sử chỉ có thể phân giải các tham chiếu đã lưu trữ khi bên gọi là một workload của Order được ủy quyền. Nó tuyệt đối không được làm cho các tài nguyên đã lưu trữ trở thành có thể bán được trên POS.
5. Quyết định từ Identity bị thất bại hoặc không khả dụng sẽ dẫn đến từ chối truy cập (fail closed). Quyền hạn lưu trong cache không được phép tồn tại lâu hơn thời gian sống (TTL) quyết định cho phép theo hợp đồng Identity.

## Bề mặt API Công khai (Public API Surface)

Các endpoint dưới đây thuộc phạm vi P0 trừ khi được đánh dấu là P1. Mọi endpoint biến đổi đều sử dụng envelope chung và yêu cầu header `Idempotency-Key` khi được liệt kê là có thể thử lại. Mọi biến đổi có phiên bản đều yêu cầu header `If-Match` ngoại trừ thao tác tạo mới.

### Danh mục (Categories)

| Phương thức | Đường dẫn | Quyền | Yêu cầu | Phản hồi | Mã trạng thái |
|---|---|---|---|---|---|
| GET | `/api/v1/catalog/categories` | `catalog:read` | `page`, `limit`, `status`, `q`, `sort` | `CategoryPage` | 200, 401, 403 |
| POST | `/api/v1/catalog/categories` | `catalog:manage` + idempotency | `CreateCategoryRequest` | `CategoryResponse` | 201, 403, 409, 422 |
| GET | `/api/v1/catalog/categories/{categoryId}` | `catalog:read` | — | `CategoryResponse` | 200, 403, 404 |
| PATCH | `/api/v1/catalog/categories/{categoryId}` | `catalog:manage` | `UpdateCategoryRequest` + `If-Match` | `CategoryResponse` | 200, 403, 404, 409, 422 |
| POST | `/api/v1/catalog/categories/{categoryId}/archive` | `catalog:manage` + idempotency | `{}` + `If-Match` | `CategoryResponse` | 200, 403, 404, 409 |

Cấu trúc danh mục là dạng phẳng trong P0. Phương thức `DELETE` không được hỗ trợ.

### Món ăn và Biến thể (Items and Variants)

| Phương thức | Đường dẫn | Quyền | Yêu cầu | Phản hồi | Mã trạng thái |
|---|---|---|---|---|---|
| GET | `/api/v1/catalog/items` | `catalog:read` | bộ lọc + phân trang trang | `CatalogItemPage` | 200, 401, 403 |
| POST | `/api/v1/catalog/items` | `catalog:manage` + idempotency | `CreateCatalogItemRequest` | `CatalogItemResponse` | 201, 403, 409, 422 |
| GET | `/api/v1/catalog/items/{itemId}` | `catalog:read` | `view=sellable\|admin` | `CatalogItemResponse` | 200, 403, 404 |
| PATCH | `/api/v1/catalog/items/{itemId}` | `catalog:manage` | `UpdateCatalogItemRequest` + `If-Match` | `CatalogItemResponse` | 200, 403, 404, 409, 422 |
| POST | `/api/v1/catalog/items/{itemId}/state` | `catalog:manage` + idempotency | `ChangeStateRequest` + `If-Match` | `CatalogItemResponse` | 200, 403, 404, 409, 422 |
| POST | `/api/v1/catalog/items/{itemId}/variants` | `catalog:manage` + idempotency | `CreateVariantRequest` | `VariantResponse` | 201, 403, 404, 409, 422 |
| PATCH | `/api/v1/catalog/variants/{variantId}` | `catalog:manage` | `UpdateVariantRequest` + `If-Match` | `VariantResponse` | 200, 403, 404, 409, 422 |
| POST | `/api/v1/catalog/variants/{variantId}/state` | `catalog:manage` + idempotency | `ChangeStateRequest` + `If-Match` | `VariantResponse` | 200, 403, 404, 409, 422 |
| POST | `/api/v1/catalog/variants/{variantId}/default` | `catalog:manage` + idempotency | `{}` + `If-Match` | `CatalogItemResponse` | 200, 403, 404, 409 |

Endpoint `GET /items` hỗ trợ các tham số:

```text
?view=sellable|admin
&categoryId=<uuid>
&status=ACTIVE|INACTIVE|ARCHIVED
&q=<search text>
&page=1&limit=20
&sort=displayOrder|name|updatedAt
&direction=asc|desc
```

Giá trị mặc định là `view=sellable`, `page=1`, `limit=20`, và `sort=displayOrder,asc`. Giá trị tối đa của `limit` là 100. `view=admin` chỉ dành riêng cho Owner/Manager. `view=sellable` sẽ loại bỏ các danh mục, món, biến thể không hoạt động/đã lưu trữ và các lựa chọn modifier không hợp lệ.

Endpoint cũ của hệ thống cha `POST /api/v1/catalog/items/{itemId}/availability` chỉ được giữ lại như một bí danh tương thích (compatibility alias) trong quá trình di chuyển (migration). Nó bắt buộc phải thực thi chỉ cho Owner/Manager; quyền ghi của Thu ngân (Cashier) bị loại bỏ. Các client mới phải sử dụng endpoint `state` của item.

### Nhóm Modifier và Tùy chọn (Modifier Groups and Options)

| Phương thức | Đường dẫn | Quyền | Yêu cầu | Phản hồi | Mã trạng thái |
|---|---|---|---|---|---|
| GET | `/api/v1/catalog/modifier-groups` | `catalog:read` | bộ lọc + phân trang trang | `ModifierGroupPage` | 200, 401, 403 |
| POST | `/api/v1/catalog/modifier-groups` | `catalog:manage` + idempotency | `CreateModifierGroupRequest` | `ModifierGroupResponse` | 201, 403, 409, 422 |
| GET | `/api/v1/catalog/modifier-groups/{groupId}` | `catalog:read` | `view=sellable\|admin` | `ModifierGroupResponse` | 200, 403, 404 |
| PATCH | `/api/v1/catalog/modifier-groups/{groupId}` | `catalog:manage` | `UpdateModifierGroupRequest` + `If-Match` | `ModifierGroupResponse` | 200, 403, 404, 409, 422 |
| POST | `/api/v1/catalog/modifier-groups/{groupId}/state` | `catalog:manage` + idempotency | `ChangeStateRequest` + `If-Match` | `ModifierGroupResponse` | 200, 403, 404, 409 |
| POST | `/api/v1/catalog/modifier-groups/{groupId}/options` | `catalog:manage` + idempotency | `CreateModifierOptionRequest` | `ModifierOptionResponse` | 201, 403, 404, 409, 422 |
| PATCH | `/api/v1/catalog/modifier-options/{optionId}` | `catalog:manage` | `UpdateModifierOptionRequest` + `If-Match` | `ModifierOptionResponse` | 200, 403, 404, 409, 422 |
| POST | `/api/v1/catalog/modifier-options/{optionId}/state` | `catalog:manage` + idempotency | `ChangeStateRequest` + `If-Match` | `ModifierOptionResponse` | 200, 403, 404, 409 |
| PUT | `/api/v1/catalog/items/{itemId}/modifier-groups/{groupId}` | `catalog:manage` + idempotency | `AttachModifierGroupRequest` + `If-Match` | `ItemModifierGroupResponse` | 200, 403, 404, 409, 422 |
| DELETE | `/api/v1/catalog/items/{itemId}/modifier-groups/{groupId}` | `catalog:manage` + idempotency | `If-Match` | 204 | 403, 404, 409 |

Phương thức `PUT` có tính bất biến (idempotent) và dùng để gắn một nhóm tái sử dụng vào một món. Nó thay thế giá trị ghi đè của cặp item-group đó; nó không làm biến đổi cấu hình mặc định của nhóm. Phương thức `DELETE` là thao tác gỡ liên kết (detach), không phải là xóa cứng nhóm tái sử dụng.

### Các Endpoint P1 (P1 Endpoints)

Các endpoint này đã được định hình hợp đồng nhưng được kiểm soát bằng tính năng (feature-gated) và nằm ngoài phạm vi nghiệm thu P0.

| Phương thức | Đường dẫn | Mục đích |
|---|---|---|
| GET/POST | `/api/v1/catalog/combos` | Liệt kê/tạo combo giá cố định. |
| GET/PATCH | `/api/v1/catalog/combos/{comboId}` | Đọc/cập nhật cấu thành combo và mô tả. |
| POST | `/api/v1/catalog/combos/{comboId}/state` | Kích hoạt, hủy kích hoạt hoặc lưu trữ combo. |
| PUT/DELETE | `/api/v1/catalog/branches/{branchId}/variants/{variantId}/price` | Thiết lập/xóa ghi đè giá biến thể hiện tại theo chi nhánh. |
| GET/POST | `/api/v1/catalog/tags` | Liệt kê/tạo tag chuẩn hóa thuộc sở hữu của tenant. |
| PUT | `/api/v1/catalog/items/{itemId}/tags` | Thay thế toàn bộ tập hợp tag của món. |

Các route chi nhánh ở P1 yêu cầu header `X-Active-Branch-Id` khớp với đường dẫn URL và phải có quyết định phân quyền hợp lệ của Owner/Manager. Các thành phần combo trong P1 không được chứa một combo khác.

## Lược đồ Yêu cầu Công khai (Public Request Schemas)

### `CreateCategoryRequest` và `UpdateCategoryRequest`

```json
{
  "name": "Cà phê",
  "displayOrder": 10
}
```

| Trường | Kiểu dữ liệu | Tạo mới | Cập nhật | Ràng buộc |
|---|---|---:|---:|---|
| `name` | string | bắt buộc | tùy chọn | 1–120 ký tự Unicode sau khi cắt khoảng trắng (trim); duy nhất trong tenant không phân biệt hoa thường. |
| `displayOrder` | int32 | tùy chọn | tùy chọn | Số không âm; nếu trùng thứ tự sẽ phân giải theo ID để đảm bảo tính tiền định khi đọc. |

### `CreateCatalogItemRequest`

```json
{
  "sku": "CF-SUA",
  "name": "Cà phê sữa",
  "categoryId": "0192b2d0-7f1c-7abc-8d2c-123456789012",
  "description": null,
  "displayOrder": 10,
  "status": "INACTIVE",
  "variants": [
    {
      "name": "STANDARD",
      "price": { "amountMinor": 29000, "currency": "VND" },
      "isDefault": true,
      "displayOrder": 0
    }
  ]
}
```

| Trường | Kiểu dữ liệu | Bắt buộc | Ràng buộc |
|---|---|---:|---|
| `sku` | string | có | 1–64 ký tự, chuẩn hóa chữ hoa, duy nhất trong tenant và vẫn được giữ đặt chỗ sau khi lưu trữ. |
| `name` | string | có | 1–200 ký tự Unicode sau khi trim. |
| `categoryId` | UUID | có | Danh mục đang hoạt động trong cùng tenant. |
| `description` | string/null | không | Tối đa 2.000 ký tự; đã làm sạch; metadata P1. |
| `displayOrder` | int32 | không | Số không âm. |
| `status` | enum | không | Mặc định là `INACTIVE` cho bản nháp chưa hoàn chỉnh; `ACTIVE` yêu cầu thỏa mãn toàn bộ bất biến bán hàng. |
| `variants` | array | có | Ít nhất một biến thể; có một và chỉ một biến thể mặc định. |

Thao tác tạo món và các biến thể ban đầu nằm trong cùng một giao dịch. Một món mới có thể được tạo ở trạng thái `INACTIVE` trong khi đang được cấu hình, nhưng thao tác tạo với trạng thái `ACTIVE` bắt buộc phải chứa một biến thể mặc định đang hoạt động và ít nhất một biến thể đang hoạt động.

### `UpdateCatalogItemRequest`

Mọi trường dữ liệu đều là tùy chọn; các trường bị bỏ qua sẽ giữ nguyên không đổi. `categoryId` phải thuộc cùng một tenant. Thay đổi `sku` chỉ được phép khi món chưa bị lưu trữ; SKU cũ vẫn được giữ đặt chỗ.

```json
{
  "name": "Cà phê sữa đá",
  "categoryId": "0192b2d0-7f1c-7abc-8d2c-123456789012",
  "displayOrder": 20,
  "description": "Cà phê rang xay với sữa đặc"
}
```

### Các Lược đồ Biến thể (Variant Schemas)

```json
{
  "name": "LARGE",
  "price": { "amountMinor": 39000, "currency": "VND" },
  "isDefault": false,
  "displayOrder": 1
}
```

Trường `amountMinor` phải là số nguyên `>= 0`; chỉ chấp nhận đơn vị tiền tệ `VND` trong P0. Một biến thể không thể bị lưu trữ hoặc vô hiệu hóa nếu việc đó khiến một món đang active không còn biến thể/biến thể mặc định nào active, trừ khi trong cùng giao dịch có chỉ định biến thể thay thế hoặc thay đổi trạng thái của món.

### `CreateModifierGroupRequest`

```json
{
  "name": "Topping",
  "minSelections": 0,
  "maxSelections": 3,
  "displayOrder": 20,
  "options": [
    {
      "name": "Trân châu",
      "priceDelta": { "amountMinor": 5000, "currency": "VND" },
      "displayOrder": 0
    }
  ]
}
```

| Trường | Kiểu dữ liệu | Ràng buộc |
|---|---|---|
| `name` | string | 1–120 ký tự; duy nhất trong tenant giữa các nhóm đang active không phân biệt hoa thường. |
| `minSelections` / `maxSelections` | int32 | `0 <= min <= max <= số tùy chọn đang active`; `min >= 1` nghĩa là bắt buộc. |
| `options` | array | Ít nhất một tùy chọn cho nhóm được gắn vào món đang active. Tên tùy chọn là duy nhất trong nhóm. |
| `priceDelta.amountMinor` | int64 | Số không âm trong P0; không hỗ trợ chiết khấu âm hoặc số lượng theo từng tùy chọn. |

`UpdateModifierGroupRequest` có thể thay đổi các giá trị mặc định, tên và thứ tự hiển thị. Nó không được phép làm vô hiệu hóa trong im lặng một món đang active được gắn nhóm này. Thao tác tạo/sửa tùy chọn sử dụng `name`, `priceDelta`, và `displayOrder`; các ID tùy chọn không bao giờ được tái sử dụng.

### `AttachModifierGroupRequest`

```json
{
  "minSelections": 1,
  "maxSelections": 2,
  "displayOrder": 30
}
```

Cả hai trường ghi đè đều là tùy chọn. Nếu vắng mặt, các giá trị mặc định của nhóm sẽ được áp dụng. Các giá trị hiệu lực phải thỏa mãn `0 <= min <= max <= số tùy chọn đang active`. Một nhóm bắt buộc được gắn vào món phải có đủ số tùy chọn đang hoạt động; ngược lại yêu cầu sẽ thất bại với lỗi `invalid_required_modifier_rule`.

### `ChangeStateRequest`

```json
{
  "status": "INACTIVE",
  "reason": "Tạm thời ngừng bán trên thực đơn"
}
```

Các chuyển đổi trạng thái `status`:

- Danh mục (Category): `ACTIVE -> INACTIVE -> ACTIVE`, hoặc `ACTIVE/INACTIVE -> ARCHIVED` chỉ khi không còn món nào chưa lưu trữ.
- Món ăn, biến thể, nhóm modifier và tùy chọn modifier: `ACTIVE <-> INACTIVE`, sau đó chuyển sang `ARCHIVED` là trạng thái kết thúc (terminal).
- Trạng thái `ARCHIVED` không thể kích hoạt lại hoặc chỉnh sửa.

## Lược đồ Phản hồi Công khai (Public Response Schemas)

Mọi phản hồi tài nguyên đều bao gồm `id`, `tenantId`, `version`, `createdAt`, `updatedAt`, và một HTTP header `ETag`. Phản hồi không làm lộ phiên bản hàng cơ sở dữ liệu nội bộ hoặc tên khóa ngoại nội bộ.

### `CategoryResponse`

```json
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
```

Trường `itemCount` có phạm vi theo tenant đã xác thực và chỉ mang tính chất thông tin; nó không được dùng làm tín hiệu phân quyền.

### `CatalogItemResponse`

```json
{
  "id": "0192b2d0-7f1e-7abc-8d2c-123456789012",
  "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
  "sku": "CF-SUA",
  "name": "Cà phê sữa",
  "description": null,
  "category": {
    "id": "0192b2d0-7f1c-7abc-8d2c-123456789012",
    "name": "Cà phê"
  },
  "status": "ACTIVE",
  "variants": [],
  "modifierGroups": [],
  "catalogVersion": 18,
  "version": 4,
  "createdAt": "2026-10-12T08:00:00Z",
  "updatedAt": "2026-10-12T08:10:00Z"
}
```

Đối với `view=sellable`, phản hồi chỉ chứa các kết hợp đang hoạt động có thể bán được. Đối với `view=admin`, nó có thể chứa các phần tử con không hoạt động hoặc đã lưu trữ, được đánh dấu rõ ràng qua trạng thái. `catalogVersion` là phiên bản sửa đổi trên toàn tenant phục vụ ETag/vô hiệu hóa cache của POS.

### Cấu trúc Biến thể và Modifier Lồng nhau

```json
{
  "id": "0192b2d0-7f20-7abc-8d2c-123456789012",
  "name": "STANDARD",
  "price": { "amountMinor": 29000, "currency": "VND" },
  "isDefault": true,
  "status": "ACTIVE",
  "displayOrder": 0,
  "version": 2
}
```

```json
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
      "name": "Trân châu",
      "priceDelta": { "amountMinor": 5000, "currency": "VND" },
      "status": "ACTIVE",
      "displayOrder": 0,
      "version": 1
    }
  ],
  "version": 3
}
```

Phản hồi của món ăn bao gồm `minSelections`, `maxSelections` và `displayOrder` hiệu lực lấy từ liên kết giữa món và nhóm. Phản hồi của nhóm độc lập sẽ hiển thị các giá trị mặc định của nhóm.

## Hợp đồng Đọc POS và Bộ nhớ đệm (Cache Contract)

Endpoint `GET /api/v1/catalog/items?view=sellable` và chi tiết món ăn trả về:

- `ETag: "catalog-tenant-<tenantId>-v<catalogVersion>"` cho đồ thị P0 trên toàn tenant.
- `Cache-Control: private, max-age=30` cho các lượt đọc trên POS; client phải xác thực lại sau khi có sự kiện biến đổi hoặc khi hết hạn.
- `304 Not Modified` khi header `If-None-Match` khớp sau khi đã phân quyền thành công. Quá trình phân quyền luôn chạy trước khi trả về `304`.

Khóa cache là `(tenantId, branchId-or-tenant, view, filters, catalogVersion)`. Branch ID vẫn được giữ trong khóa để cô lập phân quyền ngay cả khi giá/thực đơn P0 là toàn cầu.

Catalog chỉ trả về một biến thể khi danh mục, món ăn, biến thể và mọi quy tắc modifier bắt buộc đều đang hoạt động và hợp lệ. Tồn kho vật lý trong Inventory không được gộp vào phản hồi của Catalog. POS có thể hiển thị chỉ báo hết hàng từ Inventory, nhưng Order mới là bên sở hữu quyết định hoàn tất đơn hàng cuối cùng.

## Hợp đồng Bộ Phân giải Đơn hàng Nội bộ (Internal Order Resolver)

### `POST /internal/v1/catalog/resolutions`

Endpoint này xác thực và định giá một tập hợp lựa chọn hoàn chỉnh của Đơn hàng dựa trên một snapshot Catalog nhất quán duy nhất. Nó được gọi bởi Order Service trong quá trình tạo/cập nhật đơn hàng; nó không tạo Đơn hàng và không thực hiện giữ chỗ trong Inventory.

**Xác thực:** Workload identity/mTLS của Order và một ngữ cảnh phân quyền hợp lệ từ Identity cho quyền `catalog:resolve`.

**Yêu cầu (Request):**

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

| Trường | Kiểu dữ liệu | Ràng buộc |
|---|---|---|
| `tenantId` | UUID | Bắt buộc; phải khớp với ngữ cảnh phân quyền workload. |
| `branchId` | UUID | Bắt buộc; phải thuộc về tenant và đang hoạt động. |
| `requestedCatalogVersion` | int64/null | Gợi ý tùy chọn. Không phải là điều kiện tiên quyết bắt buộc; phản hồi sẽ báo cáo phiên bản thực tế được sử dụng. |
| `lines` | array | 1–100 dòng; từ chối các client line ID bị trùng lặp. |
| `variantId` | UUID/null | Các client mới bắt buộc phải cung cấp. Giá trị null kiểu cũ chỉ phân giải được nếu có chính xác một biến thể đang hoạt động. |
| `modifierOptionIds` | UUID array | Mỗi tùy chọn xuất hiện tối đa một lần; toàn bộ tùy chọn phải thuộc các nhóm được gắn vào món. |
| `quantity` | int32 | 1–999; dùng cho việc xác thực yêu cầu, không dùng cho việc giữ chỗ tồn kho trong Catalog. |

**Phản hồi thành công:**

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
            "optionName": "Trân châu",
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

**Từ chối về mặt nghiệp vụ (Business rejection):** Trả về HTTP 200 với `data.valid=false` khi yêu cầu được xử lý thành công nhưng một lựa chọn hiện không thể bán được. Điều này giúp tránh việc xem việc từ chối thực đơn thông thường như một lỗi tầng truyền tải (transport failure).

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

Các mã lý do từ chối cho phép bao gồm: `item_not_found`, `item_inactive`, `variant_required`, `variant_ambiguous`, `variant_inactive`, `modifier_group_required`, `modifier_group_too_few`, `modifier_group_too_many`, `modifier_duplicate`, `modifier_inactive`, `modifier_not_attached`, `category_inactive`, và `catalog_version_changed`.

Các lỗi kết nối/dependency là `401`, `403`, `422`, `503`. Order tuyệt đối không được lưu bất kỳ dòng nào từ phản hồi có `valid=false` và chỉ được chụp lại (snapshot) tên/giá/ID/phiên bản sau khi nhận được phản hồi hợp lệ (`valid=true`).

## Tính Bất biến (Idempotency) và Tương tranh Lạc quan

- Các bản ghi idempotency có phạm vi `(tenantId, actorOrWorkloadId, operation, idempotencyKey)` và lưu trữ hash của yêu cầu cùng tham chiếu trạng thái/body gốc.
- Gửi lại cùng một khóa và hash chuẩn hóa sẽ phát lại kết quả ban đầu mà không tạo thêm một biến đổi hay sự kiện outbox thứ hai. Một khóa mới sẽ tạo ra một biến đổi mới.
- Tái sử dụng một khóa với một nội dung yêu cầu khác sẽ trả về lỗi `409 duplicate_request`.
- Header `If-Match` so sánh phiên bản tài nguyên cho các biến đổi món/danh mục/nhóm/biến thể. Nếu không khớp sẽ trả về `409 catalog_version_conflict` và đính kèm phiên bản/ETag hiện tại trong metadata an toàn khi khả dụng.
- Một biến đổi thành công sẽ tăng phiên bản tài nguyên và `catalogVersion` của tenant trong cùng một giao dịch cơ sở dữ liệu.
- Một giao dịch thay đổi nhiều tài nguyên liên quan sẽ phát ra một sự kiện nhất quán duy nhất sau khi commit với phiên bản catalog cuối cùng của tenant. Không một consumer nào phải nhìn thấy trạng thái trung gian không hợp lệ.

## Hợp đồng Sự kiện (Event Contracts)

### Cấu trúc Bao bọc Chung (Common Envelope)

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
  "payload": {}
}
```

Trường `branchId` có thể mang giá trị null đối với các sự kiện toàn tenant ở P0. Các sự kiện giá chi nhánh ở P1 sẽ đặt giá trị này là chi nhánh đang hoạt động bị ảnh hưởng. Đây là một sửa đổi tường minh của Catalog đối với trường sự kiện của hệ thống cha (vốn mặc định mọi sự kiện đều có branchId không null).

### Các Kiểu Sự kiện (Event Types)

| Kiểu sự kiện | P0/P1 | Mục đích payload | Consumers |
|---|---|---|---|
| `catalog.category.changed` | P0 | Tên/thứ tự/trạng thái danh mục và phiên bản catalog. | POS cache, Reporting, Audit. |
| `catalog.item.changed` | P0 | Định danh món, danh mục, trạng thái, SKU, tóm tắt mô tả và phiên bản. | POS cache, Order cache, Reporting, Recommendation. |
| `catalog.variant.changed` | P0 | Định danh biến thể, ID món, trạng thái/mặc định, giá và phiên bản. | POS cache, Order cache, Reporting, bảng chiếu tham chiếu Inventory. |
| `catalog.modifier-group.changed` | P0 | Nhóm, tùy chọn, tóm tắt liên kết/quy tắc và phiên bản. | POS cache, Recommendation, Reporting. |
| `catalog.combo.changed` | P1 | Cấu thành combo/trạng thái/giá cố định. | POS cache, Recommendation, Reporting. |
| `catalog.branch-price.changed` | P1 | Ghi đè chi nhánh được đặt/xóa và giá hiệu lực. | POS cache, Order cache, Reporting. |
| `catalog.item-metadata.changed` | P1 | Mô tả/tag cho khởi động nguội AI. | Bảng chiếu Recommendation/AI, Reporting. |

Các sự kiện được xuất bản thông qua transactional outbox. Payload chứa các ID ổn định và trạng thái, không chứa bí mật, JWT claims, raw request body hoặc PII nhân viên không cần thiết. Consumer phải sử dụng `eventId` để khử trùng lặp và `catalogVersion` để phát hiện bảng chiếu bị lỗi thời (stale).

### Payload `catalog.item.changed`

```json
{
  "changeType": "UPDATED",
  "itemId": "0192b2d0-7f1e-7abc-8d2c-123456789012",
  "categoryId": "0192b2d0-7f1c-7abc-8d2c-123456789012",
  "sku": "CF-SUA",
  "status": "ACTIVE",
  "version": 4,
  "sellable": true,
  "changedFields": ["name", "description"]
}
```

Các sự kiện tiến hóa theo phương thức bổ sung (additive) trong cùng một `eventVersion`. Việc xóa/đổi tên trường hoặc thay đổi ngữ nghĩa yêu cầu phải nâng version sự kiện mới và hỗ trợ song song consumer trong quá trình migration.

## Các Bổ sung Lược đồ P1

### Combo

`ComboResponse` chứa `id`, `name`, `description`, `status`, `fixedPrice`, `components`, `version` và `catalogVersion`. Một thành phần chứa một item ID, variant ID bắt buộc và một tập hợp modifier option ID. API từ chối combo con lồng nhau, thiếu thành phần bắt buộc, thành phần không hoạt động hoặc giá cố định âm. Order chụp lại cấu trúc combo đã phân giải; không bao giờ phụ thuộc vào một lần đọc Catalog sau đó để tái tạo một đơn hàng lịch sử.

### Ghi đè Giá Chi nhánh (Branch Price Override)

```json
{
  "branchId": "0192b2d0-7f23-7abc-8d2c-123456789012",
  "variantId": "0192b2d0-7f20-7abc-8d2c-123456789012",
  "price": { "amountMinor": 31000, "currency": "VND" }
}
```

Có tối đa một bản ghi đè hiện tại cho bộ ba `(tenantId, branchId, variantId)`. Việc xóa bản ghi đè sẽ đưa phân giải giá quay về giá biến thể mặc định của tenant. Các client P0 bỏ qua phần mở rộng này và luôn dùng giá của tenant.

### Tag và Metadata

Tag slug là các giá trị chữ thường đã chuẩn hóa duy nhất trong tenant; tên hiển thị phục vụ người dùng. Endpoint `PUT /items/{itemId}/tags` thay thế tập hợp tag một cách nguyên tử. Các sự kiện metadata có phiên bản và có thể được tiêu thụ bất đồng bộ bởi bảng chiếu khởi động nguội AI.

## Tính Tương thích Hợp đồng và Các Sửa đổi Bổ sung

| Hợp đồng hiện có | Quyết định của Catalog | Hành động tiếp theo bắt buộc |
|---|---|---|
| Quyền ghi khả dụng của Thu ngân (Cashier) ở hợp đồng cha | Thu ngân là quyền chỉ đọc (read-only) đối với Catalog. | Sửa đổi ma trận phân quyền Catalog của hợp đồng cha trước khi triển khai. |
| Cột `catalog_items.category` dạng chuỗi văn bản ở hệ thống cha | Danh mục là một thực thể tenant được tham chiếu qua `categoryId`. | Cập nhật định dạng lược đồ/DTO của hợp đồng cha. |
| Mối quan hệ phẳng `item_modifiers` ở hệ thống cha | Nhóm modifier tái sử dụng cộng với quy tắc liên kết item-group. | Thay thế bằng hợp đồng group/options/link. |
| Trường `OrderLineInput.variantId` có thể null ở hệ thống cha | Các client mới bắt buộc gửi biến thể cụ thể; null kiểu cũ chỉ phân giải khi có đúng một biến thể active. | Bổ sung hành vi của resolver vào các bài kiểm thử hợp đồng Order. |
| Header `branchId` bắt buộc trong sự kiện của hệ thống cha | Các sự kiện P0 của Catalog có phạm vi toàn tenant và dùng `branchId: null`, `scope: TENANT`. | Cập nhật envelope của hệ thống cha cho phép branchId nullable kèm phạm vi tường minh. |
| Khả năng sẵn sàng của Inventory và Catalog | Bật/tắt thực đơn của Catalog là độc lập với tồn kho vật lý. | Order gọi sang Inventory cho quyết định tồn kho/hoàn tất đơn. |

Tuyệt đối không có endpoint nào của Catalog cấp quyền quản trị chi nhánh, quản lý nhân viên, biến đổi Order, biến đổi Inventory hay chuyển đổi tenant.

## Ma trận Kiểm thử Hợp đồng (Contract Test Matrix)

1. OpenAPI xác thực mọi yêu cầu, phản hồi, cấu trúc phân trang, ETag và cấu trúc bao bọc lỗi chuẩn.
2. Owner và Manager đang active có thể biến đổi Catalog toàn tenant; Cashier/Kitchen nhận mã `403` cho mọi đường dẫn biến đổi, bao gồm cả bí danh availability kiểu cũ.
3. Manager từ tenant A không thể đọc hoặc biến đổi tenant B bằng cách sử dụng ID, header hoặc tham chiếu body; các tài nguyên chéo tenant trả về `404` hoặc `403` theo chính sách dùng chung mà không làm lộ sự tồn tại.
4. Thiếu hoặc bị thu hồi tư cách thành viên, hoặc quyết định từ Identity không khả dụng sẽ dẫn đến từ chối truy cập (fail closed).
5. Header `If-Match` lỗi thời trả về `409 catalog_version_conflict`; các cập nhật hợp lệ đồng thời không bao giờ để lộ một món đang active nhưng cấu hình dang dở.
6. Cùng một idempotency key và request sẽ phát lại kết quả mà không tạo trạng thái hoặc sự kiện trùng lặp; cùng key nhưng khác request trả về `409 duplicate_request`.
7. Các endpoint item/variant/state thực thi các bất biến về biến thể mặc định/biến thể active cuối cùng và tính kết thúc của trạng thái lưu trữ.
8. Thao tác gắn/cập nhật modifier xác thực min/max hiệu lực, lựa chọn trùng lặp, các tùy chọn không hoạt động và tính khả thi của nhóm bắt buộc.
9. Yêu cầu `GET` đọc có thể bán được trả về header ETag; mã `304` được phép trả về khi đã xác thực hợp lệ, trong khi `304` chưa xác thực tuyệt đối không được trả về.
10. Bộ phân giải chấp nhận các lựa chọn hợp lệ, từ chối `variantId=null` kiểu cũ nếu mơ hồ, trả về giá/tên/phiên bản nhất quán, và không bao giờ gọi sang Inventory.
11. Từ chối nghiệp vụ của bộ phân giải sử dụng cấu trúc có định kiểu `valid=false`; lỗi kết nối/dependency sử dụng các mã lỗi HTTP đã tài liệu hóa.
12. Payload sự kiện xác thực `branchId` có thể null ở P0, ID ổn định, phiên bản catalog và tính tiến hóa bổ sung; gửi lặp lại là an toàn.
13. Các bài kiểm thử P1 bao phủ việc lồng combo, cơ chế rơi về giá tenant/cô lập giá chi nhánh và slug tag duy nhất theo tenant mà không ảnh hưởng tới client P0.

## Các Sản phẩm Bàn giao Triển khai Bắt buộc Trước khi Viết Code

- File `openapi.yaml` cho các hợp đồng REST công khai và nội bộ, bao gồm các ví dụ mẫu và schema tái sử dụng.
- File `asyncapi.yaml` kèm theo các file JSON Schema cho 7 kiểu sự kiện và cấu trúc bao bọc chung.
- Dữ liệu mẫu kiểm thử hợp đồng (fixtures) cho toàn bộ các mã lỗi chuẩn/đặc thù của Catalog và các trường hợp kiểm thử phủ định chéo tenant.
- Bản ghi chú sửa đổi hợp đồng cha cho quyền availability của Cashier, cấu trúc thực thể danh mục, phạm vi sự kiện có branch nullable và phân giải biến thể Order.
- Một client Angular được sinh tự động hoặc client có định kiểu tương đương từ tài liệu OpenAPI; các DTO viết tay lặp lại không phải là nguồn chân lý.

## Các Kỹ năng Chuyên môn được Tham chiếu

- **api-design:** Đặt tên tài nguyên REST, hợp đồng tiếp cận API-first qua OpenAPI, mô hình trạng thái/lỗi, phân trang, tương tranh lạc quan, lưu cache qua ETag và kiểm tra bảo mật.
- **database-design:** Các trường ID, quyền sở hữu tenant và phiên bản được phản ánh trong các DTO công khai; lược đồ cơ sở dữ liệu vẫn do tài liệu Kiến trúc sở hữu.
- **log-processing:** Các trường correlation/audit và yêu cầu làm sạch dữ liệu nhạy cảm trong các hợp đồng biến đổi và sự kiện.

## Phê duyệt

- **Trạng thái:** Đã được người dùng phê duyệt
- **Giai đoạn:** Hợp đồng Dữ liệu & API (API & Data Contracts)
- **Giai đoạn tiếp theo sau phê duyệt:** Hoàn thiện (Finalization)
