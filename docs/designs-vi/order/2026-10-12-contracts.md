Started at: 2026/10/12 16:33:39 +07
Finished at: 2026/10/12 16:33:39 +07
Total time: 0 minutes
---

# Hợp đồng Dữ liệu & API: Order Service (Dịch vụ Đơn hàng)

## Nguồn tài liệu và Quyền hạn Hợp đồng

- [Tầm nhìn Order (Vision)](./2026-10-12-vision.md)
- [Yêu cầu Kỹ thuật Order (Requirements)](./2026-10-12-requirements.md)
- [Kiến trúc Order (Architecture)](./2026-10-12-architecture.md)
- [Hợp đồng Nền tảng POS](../pos-platform/2026-10-12-contracts.md)
- [Hợp đồng Identity & Access](../identity-access/2026-10-12-contracts.md)
- [Hợp đồng Catalog](../catalog/2026-10-12-contracts.md)

Tài liệu này là hợp đồng quy chuẩn (normative contract) cho các API công khai của Order, các callback Saga nội bộ, dữ liệu do Order sở hữu và các sự kiện vòng đời đơn hàng. Việc triển khai thực tế bắt buộc phải tạo ra các artifact OpenAPI 3.1 và AsyncAPI/JSON Schema từ các định nghĩa này trước khi viết controller hoặc các DTO Angular.

## Các Nguyên tắc Hợp đồng

- Các API đồng bộ công khai sử dụng REST + OpenAPI 3.1 dưới tiền tố `/api/v1/orders`.
- Các API giao tiếp giữa các service (service-to-service) sử dụng REST + OpenAPI 3.1 dưới tiền tố `/internal/v1/orders`, bắt buộc phải có workload identity/mTLS và tuyệt đối không được đưa vào bảng định tuyến của Angular.
- Các sự kiện sử dụng cấu trúc bao bọc (envelope) của nền tảng và JSON Schema/AsyncAPI. Cơ chế chuyển phát là ít nhất một lần (at-least-once); các consumer khử trùng lặp theo `eventId`.
- Order sở hữu vòng đời đơn hàng, trạng thái dòng món, các snapshot Catalog bất biến, snapshot tổng tiền và các lượt thử thanh toán (checkout attempts). Catalog, Inventory, Payment, Loyalty và Audit vẫn là các bên sở hữu riêng biệt.
- Các ID là chuỗi UUID v7. Timestamp là chuỗi ISO-8601 UTC. Tiền tệ là số nguyên không âm `int64` tính theo VND. Tuyệt đối không chấp nhận tiền tệ dấu phẩy động (float).
- Ngữ cảnh tenant được thiết lập bởi Identity; Order không đưa vào header `X-Active-Tenant-Id`. Header `X-Active-Branch-Id` chọn một chi nhánh đã được máy chủ xác thực cho các thao tác theo phạm vi chi nhánh.
- Mọi lệnh công khai đều mang `X-Correlation-Id`; mọi tác dụng phụ có thể thử lại đều mang `Idempotency-Key`; mọi biến đổi đơn nháp có phiên bản đều mang `If-Match`.
- Sự kiện `OrderPaid.v1` là sự thật đơn hàng đã thanh toán chuẩn mực (canonical paid-order fact) cho Loyalty và Reporting. Sự kiện `PaymentPaid.v1` không đủ điều kiện để tích điểm hoặc ghi nhận doanh thu.

## Hợp đồng HTTP Chung

### Các Header HTTP

| Header | Bắt buộc | Ý nghĩa |
|---|---:|---|
| `Authorization` | Các API công khai được bảo vệ | `Bearer <Cognito access token>`; tư cách thành viên và quyền hạn hiện tại từ Identity là nguồn thẩm quyền tối cao. |
| `X-Active-Branch-Id` | Thao tác công khai theo chi nhánh | Chi nhánh do client chọn; được xác thực đối chiếu với tư cách thành viên hiện tại. Chủ sở hữu (Owner) bắt buộc phải chọn chi nhánh cho các thao tác đơn hàng. |
| `X-Correlation-Id` | Khuyến nghị | Tương quan yêu cầu, Saga, audit, outbox và các lệnh gọi hạ nguồn; tự động sinh nếu vắng mặt. |
| `Idempotency-Key` | Lệnh tạo/checkout/tính lại giá/hủy/thử lại | Có phạm vi actor + tenant + operation; cùng một khóa bắt buộc phải mang cùng một hash yêu cầu. |
| `If-Match` | Biến đổi Order/dòng món có phiên bản | ETag từ lần đọc gần nhất, ví dụ `"order-v7"`. |
| `Content-Type` | Yêu cầu có body | `application/json; charset=utf-8`. |

Các yêu cầu nội bộ ngoài ra còn đòi hỏi thông tin xác thực workload identity/mTLS. Client công khai không thể gọi `/internal/v1`.

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

Các phản hồi danh sách (collection) sử dụng phân trang theo con trỏ (cursor pagination) vì lịch sử đơn hàng mang đặc tính chủ yếu là nối thêm (append-heavy):

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

### Cấu trúc Bao bọc Lỗi (Error Envelope)

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

Các mã lỗi chuẩn và mã lỗi đặc thù của Order:

| HTTP | Mã lỗi | Ý nghĩa |
|---:|---|---|
| 401 | `unauthorized` | Thiếu, không hợp lệ hoặc hết hạn JWT. |
| 403 | `forbidden` | Vai trò, tư cách thành viên, phạm vi tenant hoặc chi nhánh không được phép. |
| 404 | `not_found` | Tài nguyên không tồn tại trong phạm vi được ủy quyền; không làm lộ sự tồn tại chéo tenant. |
| 409 | `conflict` | Xung đột trạng thái chung. |
| 409 | `catalog_changed` | Lựa chọn/giá/phiên bản Catalog hiện tại khác với snapshot trong đơn nháp. |
| 409 | `catalog_version_conflict` | Bộ phân giải Catalog không thể cung cấp một lựa chọn hiện tại nhất quán. |
| 409 | `order_version_conflict` | Header `If-Match` đã bị lỗi thời. |
| 409 | `order_not_editable` | Đơn hàng đang ở trạng thái `CHECKOUT_PENDING`, `PAID` hoặc `CANCELLED`. |
| 409 | `line_not_cancellable` | Trạng thái dòng món / đơn hàng không cho phép hủy. |
| 409 | `order_no_active_lines` | Đơn hàng không có dòng món nào đang active để thực hiện checkout. |
| 409 | `checkout_in_progress` | Đã tồn tại một lượt checkout attempt chưa kết thúc. |
| 409 | `compensation_incomplete` | Quy trình bù trừ Saga trước đó chưa an toàn / kết thúc; lệnh thử lại bị khóa. |
| 409 | `payment_reconciliation_required` | Tác dụng phụ của Payment/Inventory cần nhân viên vận hành đối soát thủ công. |
| 409 | `duplicate_request` | Idempotency key bị tái sử dụng với một hash yêu cầu khác. |
| 409 | `saga_callback_stale` | Callback không khớp với trạng thái attempt hiện tại; được xử lý như một thao tác no-op an toàn hoặc chuyển sang đối soát. |
| 422 | `validation_error` | Payload hoặc dữ liệu nghiệp vụ đầu vào không hợp lệ. |
| 422 | `invalid_service_mode` | Chế độ phục vụ hoặc ngữ cảnh bàn không được hỗ trợ / sai định dạng. |
| 422 | `invalid_line_selection` | Lựa chọn món/biến thể/modifier Catalog không hợp lệ. |
| 422 | `invalid_cash_amount` | Số tiền mặt nhận được nhỏ hơn tổng tiền thanh toán bất biến. |
| 422 | `checkout_total_invalid` | Tổng tiền âm, bị trộn lẫn loại tiền tệ hoặc không nhất quán với snapshot dòng món. |
| 429 | `rate_limited` | Vượt quá giới hạn tốc độ tại Gateway hoặc service. |
| 503 | `authorization_unavailable` | Không thể phân giải an toàn quyết định phân quyền từ Identity. |
| 503 | `dependency_unavailable` | Phụ thuộc Catalog, Inventory, Payment hoặc Saga không khả dụng sau khi đã thử lại có giới hạn. |

Thông báo lỗi không bao giờ để lộ câu lệnh SQL, JWT, bí mật nhà cung cấp, chữ ký thô hoặc PII khách hàng không cần thiết.

## Hợp đồng Phân quyền và Phạm vi (Scope)

| Quyền hạn | Vai trò | Phạm vi (Scope) | Các thao tác Order được phép |
|---|---|---|---|
| `order:read` | Owner, Manager, Cashier | Chi nhánh được chọn / phân công | Đọc một đơn hoặc danh sách đơn được ủy quyền. |
| `order:create` | Owner, Manager, Cashier | Chi nhánh được chọn / phân công | Tạo đơn nháp và thêm (các) dòng món ban đầu. |
| `order:edit` | Owner, Manager, Cashier | Chi nhánh được chọn / phân công, `DRAFT` | Sửa metadata / dòng món / tính lại giá. |
| `order:checkout` | Owner, Manager, Cashier | Chi nhánh được chọn / phân công | Bắt đầu hoặc thử lại checkout. |
| `order:cancel-line` | Owner, Manager | Chi nhánh được chọn / phân công, `DRAFT` | Hủy dòng món active kèm lý do và audit. |
| `order:resolve` | Workload của Order/Saga | Tenant/chi nhánh/đơn hàng tường minh | Chỉ dành riêng cho các callback nội bộ và hoàn tất đơn. |

Service gọi sang Identity để lấy quyết định hiện tại và lặp lại kiểm tra quyền sở hữu tại service/repository. Client công khai không thể tự leo thang đặc quyền bằng cách đưa vai trò, tenant, chi nhánh hoặc actor ID vào trong request body.

## Bề mặt API Công khai — P0

### Các Lệnh Đơn hàng và Đơn nháp

| Phương thức | Đường dẫn | Quyền | Yêu cầu | Phản hồi | Mã trạng thái |
|---|---|---|---|---|---|
| POST | `/api/v1/orders` | `order:create` + idempotency | `CreateOrderRequest` | `OrderResponse` | 201, 403, 409, 422 |
| GET | `/api/v1/orders` | `order:read` | con trỏ cursor + bộ lọc | `OrderPage` | 200, 403 |
| GET | `/api/v1/orders/{orderId}` | `order:read` | — | `OrderResponse` | 200, 403, 404 |
| PATCH | `/api/v1/orders/{orderId}` | `order:edit` | `UpdateOrderRequest` + `If-Match` | `OrderResponse` | 200, 403, 404, 409, 422 |
| POST | `/api/v1/orders/{orderId}/lines` | `order:edit` + idempotency | `AddOrderLineRequest` + `If-Match` | `OrderResponse` | 201/200, 403, 404, 409, 422 |
| PATCH | `/api/v1/orders/{orderId}/lines/{lineId}` | `order:edit` | `UpdateOrderLineRequest` + `If-Match` | `OrderResponse` | 200, 403, 404, 409, 422 |
| POST | `/api/v1/orders/{orderId}/lines/{lineId}/cancel` | `order:cancel-line` + idempotency | `CancelLineRequest` + `If-Match` | `OrderResponse` | 200, 403, 404, 409, 422 |
| POST | `/api/v1/orders/{orderId}/reprice` | `order:edit` + idempotency | `RepriceOrderRequest` + `If-Match` | `RepriceResponse` | 200, 403, 404, 409, 422 |
| POST | `/api/v1/orders/{orderId}/checkout` | `order:checkout` + idempotency | `CheckoutRequest` + `If-Match` | `CheckoutResponse` | 200/202, 403, 404, 409, 422, 503 |
| GET | `/api/v1/orders/{orderId}/checkout-attempts` | `order:read` | cursor | `CheckoutAttemptPage` | 200, 403, 404 |
| GET | `/api/v1/orders/{orderId}/checkout-attempts/{attemptId}` | `order:read` | — | `CheckoutAttemptResponse` | 200, 403, 404 |

Endpoint `DELETE /orders/{id}` và các endpoint xóa cứng dòng món không tồn tại trong P0. Việc hủy dòng món active cuối cùng là con đường công khai duy nhất để chuyển trạng thái Order sang `CANCELLED`.

### Bộ lọc Danh sách Đơn hàng

```text
GET /api/v1/orders
  ?status=DRAFT|CHECKOUT_PENDING|PAID|CHECKOUT_FAILED|CANCELLED
  &serviceMode=COUNTER|TAKEAWAY|TABLE
  &from=2026-10-12T00:00:00Z
  &to=2026-10-12T23:59:59Z
  &tableReference=A03
  &limit=20
  &cursor=<opaque>
```

`branchId` được chọn thông qua ngữ cảnh đã xác thực, không phải là ghi đè body/query đối với các vai trò không phải Owner. Owner có thể chọn chi nhánh thông qua cùng ngữ cảnh đã xác thực này.

### Các Endpoint Tương thích P1/P2

Các đường dẫn này được giữ chỗ và kiểm soát bằng feature flag; chúng không bắt buộc cho P0:

| Phương thức | Đường dẫn | Mục đích |
|---|---|---|
| POST | `/api/v1/orders/{orderId}/hold` | Giữ đơn nháp để tiếp tục sau. |
| POST | `/api/v1/orders/{orderId}/resume` | Tiếp tục xử lý một đơn nháp đang giữ. |
| POST | `/api/v1/orders/{orderId}/splits` | Tách hóa đơn / tách dòng món. |
| POST | `/api/v1/orders/merge` | Gộp các đơn nháp tương thích trước khi thanh toán. |
| GET | `/api/v1/branches/{branchId}/tables` | Sơ đồ bàn / trạng thái có khách ở P1. |
| POST | `/api/v1/orders/{orderId}/kitchen-tickets` | Gửi phiếu bếp bền bỉ cho KDS ở P1. |
| WebSocket | `/ws/v1/kds` | Chuyển phát KDS thời gian thực; cơ chế kết nối lại/phát lại sử dụng sự kiện bền bỉ. |
| POST | `/api/v1/qr-sessions` | Phiên QR gọi món tại bàn của khách hàng ở P2. |

## Lược đồ Yêu cầu Công khai (Public Request Schemas)

### `CreateOrderRequest`

```json
{
  "serviceMode": "TABLE",
  "tableReference": "A03",
  "orderNote": "Ít đá",
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

| Trường | Kiểu dữ liệu | Bắt buộc | Ràng buộc |
|---|---|---:|---|
| `serviceMode` | enum | có | `COUNTER`, `TAKEAWAY`, `TABLE`. Bất biến sau khi tạo. |
| `tableReference` | string/null | Chỉ dành cho TABLE | 1–64 ký tự sau khi trim; chỉ là metadata; không duy nhất trong P0. |
| `orderNote` | string/null | không | Tối đa 500 ký tự; đã làm sạch. |
| `customerId` | UUID/null | không | Tham chiếu Loyalty mờ; phải thuộc cùng tenant nếu có. |
| `lines` | array | có | 1–100 dòng; client có thể gộp các dòng giống hệt nhau nhưng Order không tự động gộp âm thầm. |

Yêu cầu được phân giải bởi Catalog trước khi hàng Order được commit vào DB. Nếu bất kỳ dòng nào không hợp lệ, không bản ghi Order dở dang nào được tạo.

Cấu trúc `OrderLineInput`:

| Trường | Kiểu dữ liệu | Bắt buộc | Ràng buộc |
|---|---|---:|---|
| `catalogItemId` | UUID | có | Được phân giải trong tenant hiện tại. |
| `variantId` | UUID/null | có cho client mới | Null kiểu cũ chỉ phân giải khi có đúng một biến thể active. |
| `modifierOptionIds` | UUID array | không | Mỗi tùy chọn xuất hiện tối đa một lần; Catalog xác thực quy tắc gắn nhóm. |
| `quantity` | int32 | có | 1–999. |
| `note` | string/null | không | Tối đa 250 ký tự; làm sạch và che giấu trong log thông thường. |

### `UpdateOrderRequest`

```json
{
  "orderNote": "Giao nhanh",
  "tableReference": "A03"
}
```

Mọi trường dữ liệu đều là tùy chọn và chỉ áp dụng cho đơn `DRAFT`. Các trường `serviceMode` và `branchId` không thể thay đổi. `tableReference` chỉ có thể được đặt/xóa khi `serviceMode=TABLE`.

### `AddOrderLineRequest` / `UpdateOrderLineRequest`

```json
{
  "catalogItemId": "0192b2d0-7f1e-7abc-8d2c-123456789012",
  "variantId": "0192b2d0-7f20-7abc-8d2c-123456789012",
  "modifierOptionIds": [],
  "quantity": 1,
  "note": ""
}
```

Thao tác thêm dòng bắt buộc phải có các trường lựa chọn Catalog. Thao tác cập nhật có thể gửi bất kỳ tập con nào của `quantity`, `variantId`, `modifierOptionIds` và `note`; việc thay đổi trường lựa chọn sẽ gọi bộ phân giải Catalog. Thao tác chỉ sửa số lượng hoặc ghi chú sẽ tái sử dụng snapshot hiện tại nhưng vẫn kiểm tra `If-Match` và tính toán lại các khoản tổng.

### `CancelLineRequest`

```json
{
  "reason": "Khách đổi món"
}
```

Trường `reason` là bắt buộc, đã cắt khoảng trắng, dài 1–500 ký tự và được làm sạch. Máy chủ tự trích xuất thông tin tác nhân, vai trò, tenant, chi nhánh, timestamp và snapshot trước/sau; client không thể tự cung cấp các trường audit này.

### `RepriceOrderRequest` và `RepriceResponse`

```json
{
  "confirm": false,
  "expectedCatalogVersion": 18
}
```

- `confirm=false` trả về bản xem trước khác biệt (diff) không làm biến đổi dữ liệu, bao gồm các dòng bị ảnh hưởng, phiên bản Catalog hiện tại và các snapshot đề xuất.
- `confirm=true` yêu cầu header `If-Match` mới nhất của Order và áp dụng toàn bộ snapshot/tổng tiền đề xuất một cách nguyên tử. Nếu Catalog lại thay đổi tiếp, nó sẽ trả về mã `409 catalog_changed`.
- Thao tác tính lại giá chỉ được phép trong `DRAFT`; nó không bao giờ làm biến đổi một lượt checkout attempt lịch sử.

### `CheckoutRequest`

```json
{
  "paymentMethod": "VNPAY_QR",
  "cashReceivedMinor": null,
  "returnUrl": "https://pos.example.test/payment-result",
  "clientReference": "counter-03-20261012-0007"
}
```

| Trường | Kiểu dữ liệu | Bắt buộc | Ràng buộc |
|---|---|---:|---|
| `paymentMethod` | enum | có | `CASH`, `VNPAY_QR`. |
| `cashReceivedMinor` | int64/null | Chỉ cho tiền mặt | Phải >= tổng tiền thanh toán bất biến. Tuyệt đối không chấp nhận cho QR. |
| `returnUrl` | URI/null | Chỉ cho VNPAY | Nằm trong danh sách trắng theo chính sách Payment/Gateway; không dùng làm tín hiệu auth. |
| `clientReference` | string/null | không | Tối đa 100 ký tự; chỉ dùng hiển thị/tương quan. |

Trước khi tạo attempt, Order phân giải lại với Catalog và xác thực các khoản tổng hiện tại. Yêu cầu không thể cung cấp giá, tổng tiền, tác nhân hay ghi đè chi nhánh.

## Lược đồ Phản hồi Công khai (Public Response Schemas)

### `OrderResponse`

```json
{
  "id": "0192b2d0-7f30-7abc-8d2c-123456789012",
  "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
  "branchId": "0192b2d0-7f23-7abc-8d2c-123456789012",
  "orderNumber": "A03-000127",
  "serviceMode": "TABLE",
  "tableReference": "A03",
  "status": "DRAFT",
  "orderNote": "Ít đá",
  "customerId": null,
  "lines": [],
  "totals": {
    "subtotalMinor": 68000,
    "vatMinor": 0,
    "discountMinor": 0,
    "grandTotalMinor": 68000,
    "currency": "VND",
    "pricingPolicyVersion": 1
  },
  "catalogVersion": 18,
  "version": 4,
  "currentCheckoutAttempt": null,
  "createdAt": "2026-10-12T09:00:00Z",
  "updatedAt": "2026-10-12T09:03:00Z"
}
```

Phản hồi tài nguyên trả về header `ETag: "order-v4"`. Mảng `lines` bao gồm cả các dòng active và đã hủy để lưu vết lịch sử được ủy quyền; client phải sử dụng trạng thái dòng món khi tính toán hoặc hiển thị tổng tiền hiện tại.

### `OrderLineResponse`

```json
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
      "optionName": "Trân châu",
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
  "version": 2,
  "cancelledAt": null,
  "cancelReason": null
}
```

### `CheckoutResponse`

```json
{
  "orderId": "0192b2d0-7f30-7abc-8d2c-123456789012",
  "attemptId": "0192b2d0-7f32-7abc-8d2c-123456789012",
  "sagaId": "0192b2d0-7f33-7abc-8d2c-123456789012",
  "orderStatus": "CHECKOUT_PENDING",
  "attemptStatus": "PAYMENT_PENDING",
  "paymentId": "0192b2d0-7f34-7abc-8d2c-123456789012",
  "reservationId": "0192b2d0-7f35-7abc-8d2c-123456789012",
  "grandTotalMinor": 68000,
  "currency": "VND",
  "cashChangeMinor": null,
  "qrPayload": "opaque-payment-payload",
  "expiresAt": "2026-10-12T09:18:00Z"
}
```

- Thanh toán tiền mặt thành công trả về HTTP `200` với `orderStatus=PAID`, `attemptStatus=COMPLETED` và `cashChangeMinor`.
- Khởi tạo VNPAY thành công trả về HTTP `202` với `orderStatus=CHECKOUT_PENDING`, `attemptStatus=PAYMENT_PENDING`, chuỗi dữ liệu QR payload và thời gian hết hạn.
- Checkout thất bại hoặc đã bù trừ trả về HTTP `200`/`409` tùy theo lệnh đã được xử lý hay bị xung đột; body chứa `CHECKOUT_FAILED`, `compensationStatus` và `paymentReconciliationRequired`.

### `CheckoutAttemptResponse`

Bao gồm `attemptId`, `orderId`, `sagaId`, `status`, `compensationStatus`, `paymentReconciliationRequired`, `paymentId`, `reservationId`, snapshot các khoản tổng bất biến, `failureCode`, `failureReasonSafe`, `expiresAt`, `startedAt`, `completedAt` và `correlationId`. Payload hoặc chữ ký thô từ nhà cung cấp cổng thanh toán không bao giờ bị lộ ra ngoài.

## Hợp đồng Saga Nội bộ (Internal Saga Contracts)

Mọi endpoint đều bắt buộc có mTLS / workload identity, ngữ cảnh tenant/chi nhánh tường minh, `sagaId`, `attemptId`, `Idempotency-Key` và metadata correlation. Chúng có tính bất biến (idempotent) theo bộ tứ `(tenantId, orderId, attemptId, taskName)`.

### `POST /internal/v1/orders/{orderId}/checkout-attempts/{attemptId}/finalize`

Được gọi sau khi thanh toán thành công và bước commit tồn kho trong Inventory thành công.

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

Phản hồi là mã `200` kèm theo trạng thái kết thúc hiện tại của Order/attempt. Một lượt phát lại (replay) trả về kết quả tương tự và không phát sinh thêm một sự kiện `OrderPaid` thứ hai.

### `POST /internal/v1/orders/{orderId}/checkout-attempts/{attemptId}/fail`

Được gọi sau khi thanh toán hoặc giữ chỗ tồn kho thất bại không thể thử lại, hoặc bị quá thời gian (timeout). Body bao gồm `reasonCode`, `retryable`, `paymentStatus`, `reservationStatus`, `compensationStatus` và `failureAt`. Nó chỉ chuyển trạng thái Order sang `CHECKOUT_FAILED` khi attempt hoàn toàn khớp; callback cũ trả về mã `409 saga_callback_stale` hoặc kết quả hiện tại có tính bất biến.

### `POST /internal/v1/orders/{orderId}/checkout-attempts/{attemptId}/compensation`

Cập nhật `compensationStatus` thành `RETRYING`, `SAFE`, hoặc `FAILED`, kèm theo tên tác vụ, số lần thử lại, mã lý do và thời gian thử lại tiếp theo. Kết quả `FAILED` sẽ kích hoạt khóa thử lại và cờ `paymentReconciliationRequired` khi thanh toán đã thành công.

### `POST /internal/v1/orders/{orderId}/checkout-attempts/{attemptId}/late-payment`

Ghi nhận một thanh toán thành công đã xác minh từ Payment đến muộn sau khi việc giữ chỗ tồn kho đã bị giải phóng / attempt đã kết thúc thất bại. Thao tác này có tính bất biến, không chuyển Order sang `PAID`, thiết lập cờ `paymentReconciliationRequired=true` và phát ra sự kiện đối soát vận hành.

### `GET /internal/v1/orders/{orderId}/checkout-context`

Trả về dữ liệu đầu vào attempt bất biến mà Step Functions đòi hỏi: snapshot các dòng active, các khoản tổng, tenant/chi nhánh, phiên bản Catalog, thời gian hết hạn attempt và trạng thái hiện tại. Không bao giờ để lộ bí mật hay thông tin đăng nhập của nhà cung cấp.

## Hợp đồng Sự kiện (Event Contracts)

### Cấu trúc Bao bọc Chung (Common Envelope)

```json
{
  "eventId": "0192b2d0-7f40-7abc-8d2c-123456789012",
  "eventType": "OrderPaid",
  "eventVersion": 1,
  "occurredAt": "2026-10-12T09:03:00Z",
  "producer": "order-service",
  "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
  "branchId": "0192b2d0-7f23-7abc-8d2c-123456789012",
  "aggregateType": "Order",
  "aggregateId": "0192b2d0-7f30-7abc-8d2c-123456789012",
  "correlationId": "cor_01J...",
  "payload": {}
}
```

Các sự kiện của Order luôn luôn có `branchId` không null vì mọi đơn hàng đều thuộc về một chi nhánh cụ thể. Payload sự kiện chứa các ID ổn định, snapshot bất biến mà consumer cần, các khoản tổng và phiên bản; không chứa JWT, bí mật, dữ liệu thanh toán thô hay PII không cần thiết.

### Các Kiểu Sự kiện (Event Types)

| Kiểu sự kiện | Mức ưu tiên | Mục đích payload | Consumers |
|---|---|---|---|
| `OrderCreated.v1` | P0 | Định danh đơn nháp mới, chế độ phục vụ, chi nhánh, tóm tắt snapshot dòng món và các khoản tổng | Reporting, Audit, Recommendation |
| `OrderLineCancelled.v1` | P0 | Tóm tắt lý do / tác nhân hủy dòng món, phiên bản order kết quả | Audit, Reporting |
| `OrderCancelled.v1` | P0 | Hủy kết thúc toàn bộ đơn sau khi hủy dòng món active cuối cùng | Audit, Reporting |
| `OrderCheckoutFailed.v1` | P0 | Lý do attempt thất bại, trạng thái bù trừ / đối soát | Audit, Reporting, vận hành |
| `OrderPaid.v1` | P0 | Sự thật đơn hàng đã thanh toán bất biến chuẩn mực kèm snapshot dòng món và các khoản tổng | Loyalty, Reporting, Audit |
| `OrderItemSubmitted.v1` | P1 | Phiếu món ăn bền bỉ cho KDS / phát lại | Bộ điều hợp KDS, Audit |
| `OrderHeld.v1` / `OrderResumed.v1` | P1 | Trạng thái lưu giữ / tiếp tục đơn nháp | Reporting, Audit |

### Payload `OrderPaid.v1`

```json
{
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
      "modifiers": []
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
```

Các sự kiện tiến hóa theo phương thức bổ sung (additive) trong cùng một version. Việc xóa hoặc thay đổi ngữ nghĩa trường dữ liệu đòi hỏi phải nâng phiên bản sự kiện mới và hỗ trợ song song các consumer trong quá trình migration.

## Hợp đồng Dữ liệu do Order Sở hữu

### Bảng `orders`

| Cột | Kiểu | Ràng buộc / Ý nghĩa |
|---|---|---|
| `id` | UUID | Khóa chính (PK), UUID v7. |
| `tenant_id` | UUID | Phạm vi tenant bắt buộc. |
| `branch_id` | UUID | Phạm vi chi nhánh bắt buộc và bất biến. |
| `order_number` | TEXT | Bắt buộc, duy nhất trong cặp `(tenant_id, branch_id)`. |
| `service_mode` | TEXT | `COUNTER`, `TAKEAWAY`, `TABLE`. |
| `table_reference` | TEXT | Có thể null trừ khi là TABLE; tối đa 64 ký tự; không duy nhất trong P0. |
| `status` | TEXT | `DRAFT`, `CHECKOUT_PENDING`, `PAID`, `CHECKOUT_FAILED`, `CANCELLED`. |
| `order_note` | TEXT | Có thể null, đã làm sạch, tối đa 500 ký tự. |
| `customer_id` | UUID | Tham chiếu Loyalty mờ có thể null. |
| `subtotal_minor` | BIGINT | Tổng tiền các dòng món active không âm. |
| `vat_minor` | BIGINT | Thuế có tính tiền định không âm; mặc định P0 bằng 0. |
| `discount_minor` | BIGINT | Chiết khấu không âm; mặc định P0 bằng 0. |
| `grand_total_minor` | BIGINT | `subtotal + vat - discount`, số không âm. |
| `currency` | CHAR(3) | `VND` trong P0. |
| `pricing_policy_version` | INT | Phiên bản chính sách định giá/thuế cục bộ. |
| `catalog_version` | BIGINT | Phiên bản Catalog nhất quán gần nhất được sử dụng. |
| `version` | BIGINT | Phiên bản kiểm soát tương tranh lạc quan. |
| `created_at`/`updated_at` | TIMESTAMPTZ | Dấu thời gian chuẩn UTC. |

### Bảng `order_lines`

| Cột | Kiểu | Ràng buộc / Ý nghĩa |
|---|---|---|
| `id` | UUID | Khóa chính (PK), UUID v7. |
| `order_id` | UUID | Khóa ngoại cục bộ tới `orders`; kiểm tra quyền sở hữu tenant/chi nhánh. |
| `catalog_item_id` | UUID | ID món Catalog mờ. |
| `catalog_variant_id` | UUID | ID biến thể cụ thể đã được phân giải. |
| `item_name_snapshot` | TEXT | Tên món lịch sử bắt buộc. |
| `variant_name_snapshot` | TEXT | Tên biến thể lịch sử bắt buộc. |
| `base_unit_price_minor` | BIGINT | Giá cơ sở đã phân giải không âm. |
| `modifier_unit_price_minor` | BIGINT | Tổng phụ thu các modifier đã chọn không âm. |
| `unit_price_minor` | BIGINT | Đơn giá tổng (cơ sở + modifier). |
| `quantity` | INT | 1–999 khi đang active. |
| `note` | TEXT | Có thể null, đã làm sạch, tối đa 250 ký tự. |
| `status` | TEXT | `ACTIVE` hoặc `CANCELLED`. |
| `cancel_reason` | TEXT | Bắt buộc khi bị hủy. |
| `cancelled_by`/`cancelled_at` | UUID/TIMESTAMPTZ | Tác nhân và thời gian UTC khi hủy. |
| `catalog_version` | BIGINT | Phiên bản Catalog được sử dụng cho snapshot này. |
| `version` | BIGINT | Phiên bản dòng lạc quan. |

### Bảng `order_line_modifiers`

Lưu trữ các ID nhóm/tùy chọn modifier đã chọn, tên và `price_delta_minor` được sử dụng bởi snapshot dòng món. Đây là dữ liệu lịch sử cục bộ, không phải là mối quan hệ sống tới Catalog. Nghiêm cấm trùng lặp ID tùy chọn trong cùng một dòng món.

### Bảng `checkout_attempts`

Lưu trữ `id`, `order_id`, `tenant_id`, `branch_id`, `attempt_number`, `saga_id`, `status`, `compensation_status`, `idempotency_key`, `request_hash`, snapshot các khoản tổng bất biến, `payment_id`, `reservation_id`, `failure_code`, lý do thất bại an toàn, `payment_reconciliation_required`, `expires_at`, số lần thử lại, timestamp và correlation ID. Chỉ có duy nhất một attempt chưa kết thúc tồn tại cho mỗi Order.

### Bảng `order_state_transitions`, `outbox_messages`, `idempotency_records`

- `order_state_transitions` là bảng chỉ nối thêm (append-only) ghi nhận các chuyển đổi trạng thái quan trọng, tác nhân/workload, lý do, phiên bản và correlation ID.
- `outbox_messages` lưu trữ envelope/payload sự kiện chung, kiểu/phiên bản sự kiện, tenant/chi nhánh, ID tập hợp, trạng thái xuất bản, thời gian thử lại tiếp theo, số lần thử lại và lỗi gần nhất.
- `idempotency_records` lưu trữ tenant, actor/workload, thao tác, khóa, hash yêu cầu chuẩn hóa, phản hồi/trạng thái gốc và thời gian hết hạn. Bí mật thanh toán thô hoặc ghi chú đầy đủ không bao giờ được lưu trữ.

## Tính Bất Biến (Idempotency) và Quản Lý Phiên Bản

- Các lệnh tạo đơn, thêm dòng món, hủy món, tính lại giá và checkout đều bắt buộc có `Idempotency-Key`.
- Cùng một khóa và cùng một nội dung yêu cầu chuẩn hóa sẽ trả về phản hồi ban đầu mà không tạo thêm dòng mới, không thực thi Saga lặp, không phát sự kiện audit trùng hay phát trùng sự kiện `OrderPaid`.
- Cùng một khóa nhưng khác hash yêu cầu sẽ trả về mã lỗi `409 duplicate_request`.
- Các khóa tác vụ checkout trong Saga được sinh từ `(tenantId, orderId, attemptId, taskName)` và hoàn toàn độc lập với khóa checkout công khai.
- Header `If-Match` là bắt buộc cho các thay đổi Order/dòng món và hủy món/tính lại giá. Một giá trị lỗi thời sẽ trả về mã `409 order_version_conflict`.
- Consumer sự kiện khử trùng lặp theo `eventId`; phiên bản sự kiện là bất biến sau khi xuất bản.
- Các trường tùy chọn mới là các bổ sung tương thích ngược. Các thay đổi phá vỡ tương thích (breaking changes) bắt buộc phải sử dụng đường dẫn API phiên bản lớn mới hoặc version sự kiện mới.

## Tính Tương Thích Hợp Đồng và Các Sửa Đổi Bổ Sung

| Hợp đồng hiện có | Quyết định của Order | Hành động tiếp theo bắt buộc |
|---|---|---|
| Trường `OrderLineInput.variantId` có thể null ở hệ thống cha | Client mới bắt buộc gửi biến thể cụ thể; null kiểu cũ chỉ giải quyết khi có đúng một biến thể active. | Sửa đổi DTO Order/Catalog dùng chung và các bài kiểm thử resolver. |
| Hệ thống cha dùng `modifierIds` | Hợp đồng Catalog sử dụng ID tùy chọn/nhóm modifier và chụp snapshot tên/phụ thu. | Đổi tên/làm rõ thành `modifierOptionIds` trong hợp đồng Order. |
| Yêu cầu `UpdateOrderRequest` của cha quá rộng | P0 tách riêng các lệnh sửa metadata, biến đổi dòng món, hủy món và tính lại giá. | Cập nhật quyền sở hữu endpoint/lược đồ trong OpenAPI. |
| CheckoutResponse của hệ thống cha quá tối giản | P0 trả về attempt/saga/payment/reservation ID, thời gian hết hạn pending, trạng thái bù trừ và đối soát. | Mở rộng phản hồi mà không xóa bỏ các trường hiện có. |
| Sự kiện `OrderPaid` của hệ thống cha là chuẩn mực | Loyalty/Reporting chỉ tiêu thụ duy nhất OrderPaid; PaymentPaid chỉ là sự thật thanh toán của Saga. | Bổ sung kiểm thử hợp đồng consumer và quy tắc định tuyến. |
| API Inventory của hệ thống cha thiếu ngữ nghĩa commit | Thanh toán thành công đòi hỏi phải commit giữ chỗ tồn kho bất biến trước khi hoàn tất Order. | Bổ sung endpoint commit và kết quả vào hợp đồng Inventory/Saga. |
| Endpoint hủy dòng món của hệ thống cha chỉ ghi dành cho Quản lý | Chỉ Owner/Manager, chỉ áp dụng trong `DRAFT`, có lý do/audit, hủy dòng cuối chuyển đơn sang CANCELLED. | Bổ sung ràng buộc vai trò/trạng thái/lý do và lược đồ sự kiện audit. |
| Phạm vi chi nhánh của cha cho phép đọc chi nhánh | Owner cần chọn chi nhánh tường minh; Manager/Cashier phải có tư cách thành viên chi nhánh active. | Áp dụng cùng một ngữ cảnh phân quyền cho mọi đường dẫn Order. |

## Ma trận Kiểm thử Hợp đồng (Contract Test Matrix)

1. OpenAPI xác thực mọi envelope yêu cầu / phản hồi / lỗi, ETag, phân trang con trỏ và mã trạng thái HTTP.
2. Các quy tắc chi nhánh của Owner/Manager/Cashier và việc từ chối hủy món của Cashier được kiểm thử với quyết định Identity hiện tại, thu hồi thành viên và ID chéo chi nhánh.
3. Chỉnh sửa bản nháp hợp lệ trong cùng tenant thành công; header `If-Match` lỗi thời thất bại mà không ghi nhận dở dang.
4. Bộ phân giải Catalog chụp snapshot các lựa chọn hợp lệ và từ chối các tùy chọn thuộc tenant khác / không active / mơ hồ; không tạo Order dở dang.
5. Lỗi `catalog_changed` chặn đứng quá trình checkout và lệnh xác nhận tính lại giá rõ ràng chỉ cập nhật snapshot đúng một lần.
6. Hủy dòng món active cuối cùng sẽ chuyển đổi Order sang `CANCELLED` một cách nguyên tử và phát ra một sự kiện audit dòng/đơn.
7. Checkout tiền mặt tính toán chính xác tiền thừa số nguyên, hoàn tất đơn một lần và phát lại bất biến.
8. VNPAY QR trả về trạng thái pending, giữ chỗ tồn kho trong 15 phút, chấp nhận một IPN hợp lệ và phát ra một sự kiện `OrderPaid`.
9. Các đường dẫn IPN không hợp lệ, trùng lặp, đến sai thứ tự hoặc đến muộn không tạo thanh toán/hoàn tất đơn lặp; IPN muộn tạo trạng thái đối soát.
10. Tồn kho không đủ / thanh toán thất bại / timeout giải phóng giữ chỗ tồn kho đúng một lần và chuyển đơn sang `CHECKOUT_FAILED`.
11. Bù trừ thử lại / thất bại sẽ khóa checkout mới và hiển thị trạng thái vận hành an toàn kèm bằng chứng DLQ.
12. Thanh toán thành công cộng với commit tồn kho thất bại sẽ thử lại và không bao giờ tự ý đánh dấu Order là `PAID`.
13. Thử lại checkout mới tạo ra một attempt mới chỉ sau khi bù trừ an toàn; các attempt trước đó giữ nguyên bất biến.
14. Sự kiện `OrderPaid` chỉ phát ra chính xác một lần và việc phát lại không làm trùng lặp tác dụng phụ của Loyalty/Reporting.
15. Các mệnh đề repository theo tenant/chi nhánh ngăn ngừa lỗ hổng IDOR cho mọi thao tác đơn lẻ / hàng loạt / nội bộ.
16. Ghi chú, tham chiếu khách hàng, JWT, chữ ký nhà cung cấp và bí mật đều vắng mặt hoặc được che giấu khỏi log/sự kiện/phản hồi.
17. Các endpoint giữ chỗ của P1 không làm biến đổi ngữ nghĩa trạng thái P0; KDS tự kết nối lại/phát lại sử dụng sự kiện bền bỉ thay vì trạng thái socket.

## Các Sản phẩm Bàn giao Triển khai Bắt buộc Trước khi Viết Code

- File `openapi.yaml` cho các API Order công khai và các callback Saga `/internal/v1/orders`.
- File `asyncapi.yaml` kèm theo các file JSON Schema cho `OrderCreated`, `OrderLineCancelled`, `OrderCancelled`, `OrderCheckoutFailed`, `OrderPaid` và các sự kiện P1.
- Định nghĩa máy trạng thái AWS Step Functions kèm theo các khóa idempotency của tác vụ, các đường dẫn timeout/retry/catch/compensation và tương quan callback.
- Dữ liệu mẫu kiểm thử hợp đồng (fixtures) cho lỗi Catalog `catalog_changed`, Inventory reserve/commit/release, Payment pending/paid/failed/late và kết quả đối soát.
- Các kiểu client Angular sinh tự động; các DTO yêu cầu/phản hồi viết tay lặp lại không phải là nguồn chân lý.
- Mã nguồn SQL migration Flyway kèm chú thích, các ràng buộc, chỉ mục và dữ liệu mẫu có tính tiền định cho đa tenant / đa chi nhánh.
- Dữ liệu mẫu kiểm thử hợp đồng / bảo mật cho IDOR chéo tenant/chi nhánh, phiên bản lỗi thời, lệnh trùng lặp, callback muộn và che giấu thông tin nhạy cảm.

## Các Kỹ năng Chuyên môn được Tham chiếu

- **api-design:** Phân định tài nguyên REST, tách biệt lệnh công khai, phân trang con trỏ, mô hình lỗi, xác thực phân quyền, tính bất biến (idempotency) và tương tranh lạc quan.
- **database-design:** Lược đồ snapshot của Order, checkout attempts, các ràng buộc, chỉ mục, UUID v7, tiền tệ số nguyên và chú thích migration.
- **log-processing:** Các trường correlation/audit/outbox, metadata sự kiện, khả năng quan sát retry/DLQ và làm sạch dữ liệu nhạy cảm.
- **sequence-diagram:** Tài liệu Kiến trúc đã chứa các luồng checkout / bù trừ khái niệm; biểu đồ PlantUML thời gian chạy là sản phẩm bàn giao sau khi triển khai.

## Phê duyệt

- **Trạng thái:** Đã được người dùng phê duyệt
- **Giai đoạn:** Hợp đồng Dữ liệu & API (API & Data Contracts)
- **Giai đoạn tiếp theo sau phê duyệt:** Hoàn thiện (Finalization)
