# Module 4 — Payment Service: Hợp đồng Dữ liệu & API (API & Data Contracts)

**Ngày:** 2026-10-12  
**Trạng thái:** Đã phê duyệt (Approved)  
**Phạm vi:** M — Quy trình Service Chuẩn (Standard Service Flow)  
**Module:** Thanh toán (Payment Service)

---

## Nguồn tài liệu và Quyền hạn Hợp đồng

- [Tầm nhìn Payment (Vision)](./2026-10-12-vision.md)
- [Yêu cầu Kỹ thuật Payment (Requirements)](./2026-10-12-requirements.md)
- [Kiến trúc Kỹ thuật Payment (Architecture)](./2026-10-12-architecture.md)
- [Hợp đồng Identity & Access](../identity-access/2026-10-12-contracts.md)
- [Hợp đồng Order Service](../order/2026-10-12-contracts.md)
- [Hợp đồng Nền tảng POS](../pos-platform/2026-10-12-contracts.md)

Tài liệu này là hợp đồng quy chuẩn (normative contract) cho Payment Service. Việc triển khai thực tế bắt buộc phải sinh và xác thực tài liệu OpenAPI 3.1 cùng các artifact AsyncAPI/JSON Schema từ các định nghĩa này trước bản phát hành mã nguồn đầu tiên.

---

## 1. Các Nguyên tắc Hợp đồng (Contract Principles)

- **Tiếp cận API-first & Bounded Context:**
  - Toàn bộ API đồng bộ công khai sử dụng chuẩn **RESTful + OpenAPI 3.1** dưới tiền tố `/api/v1/payments`.
  - Các API giao tiếp giữa các service (Service-to-Service) và Step Functions Saga điều phối sử dụng REST dưới tiền tố `/internal/v1/payments`, bắt buộc phải có chứng chỉ Workload Identity / mTLS hoặc HMAC-signed workload token, tuyệt đối không đưa vào bảng định tuyến Gateway công khai.
  - Các webhook/callback từ nhà cung cấp thanh toán (Provider) nằm dưới tiền tố `/callbacks/v1`.
  - Payment Service sở hữu toàn bộ vòng đời của `payment_attempt`, mã tham chiếu provider, xác thực IPN checksum, đối soát giao dịch và hoàn tiền.
  - Payment Service **tuyệt đối không** đọc/ghi cơ sở dữ liệu của Order hay Inventory; không tự tính toán lại thuế/chiết khấu; không tự ý hoàn tất đơn hàng (`finalize Order`); và không tự ý phát ra sự kiện `OrderPaid.v1`.
- **Định danh & Thời gian:**
  - Toàn bộ khóa chính và định danh thực thể (`paymentId`, `attemptId`, `callbackId`, `reconciliationRunId`, `refundId`, `outboxMessageId`) đều là chuỗi **UUID v7**.
  - Toàn bộ dấu thời gian đều là chuỗi định dạng ISO-8601 UTC trong API (`YYYY-MM-DDTHH:mm:ssZ`) và kiểu `TIMESTAMPTZ` trong PostgreSQL. Ngày kinh doanh được tính toán theo múi giờ chi nhánh (`Asia/Ho_Chi_Minh` - ICT).
- **Tiền tệ & Đơn vị Minor:**
  - Tiền tệ trong P0 là VND (`currency = "VND"`).
  - Số tiền luôn được biểu diễn dưới dạng số nguyên không âm `int64` tính theo đơn vị minor (`amountMinor`), trong đó 1 minor unit tương đương 1 VND nguyên. Nghiêm cấm tuyệt đối dùng số thực dấu phẩy động (`float`, `double`).
  - Đối với VNPAY sandbox, giá trị `vnp_Amount` gửi từ cổng thanh toán đã bị nhân 100 (theo quy ước VNPAY minor unit). Bộ điều hợp VNPAY Adapter bắt buộc phải chia 100 để chuẩn hóa về đúng `amountMinor` VND của hệ thống trước khi kiểm tra hoặc lưu trữ.
- **Bảo mật Đa người thuê (Multi-tenant) & Phân quyền:**
  - Ngữ cảnh `tenantId` bắt buộc trích xuất từ token danh tính đã xác thực (Cognito JWT hoặc Service Workload Token). Payment Service không chấp nhận quyền sở hữu tenant từ request body hoặc query parameter.
  - Header `X-Active-Branch-Id` bắt buộc đối với người dùng chi nhánh (`CASHIER`, `MANAGER`, `KITCHEN`). Chủ sở hữu (`OWNER`) có thể xem toàn tenant nhưng phải chỉ định chi nhánh khi thực hiện thao tác phạm vi chi nhánh.
- **Tính Bất biến (Idempotency) & Máy trạng thái (State Machine):**
  - Mọi thao tác ghi có khả năng thử lại bắt buộc phải có header `Idempotency-Key`. Khóa có phạm vi: `(tenantId, actorOrWorkloadId, operation)`.
  - Các trạng thái kết thúc (`PAID`, `FAILED`, `EXPIRED`, `CANCELLED`) là bất biến (Terminal). Tuyệt đối không ghi đè trạng thái kết thúc khi có callback đến muộn.
  - Khi provider báo thành công hợp lệ đến muộn sau khi attempt đã ở trạng thái kết thúc (`EXPIRED` hoặc `CANCELLED`): Payment Service chuyển bản ghi vào `late_payment_reconciliation` với trạng thái `SETTLED_LATE`, chỉ phát sự kiện `PaymentReconciliationRequired.v1`, **tuyệt đối không phát `PaymentPaid.v1`** và không phục hồi lại Order.
- **Transactional Outbox:**
  - Mọi sự kiện miền (domain event) được ghi đồng thời vào bảng `outbox_messages` trong cùng một giao dịch cơ sở dữ liệu với thay đổi trạng thái thanh toán. Message relay chuyển phát sự kiện sang AWS EventBridge theo cơ chế ít nhất một lần (at-least-once).

---

## 2. Hợp đồng HTTP Chung (Common HTTP Contract)

### Các Header HTTP Chuẩn

| Header | Bắt buộc | Phạm vi | Ý nghĩa và Quy ước sử dụng |
|---|:---:|---|---|
| `Authorization` | Bắt buộc | Public API | `Bearer <Cognito access token>`. Xác thực người dùng, phân giải quyền qua Identity Service. |
| `Authorization` | Bắt buộc | Internal API | `Bearer <Workload Identity / mTLS token>`. Xác thực ngữ cảnh service nội bộ từ AWS Step Functions hoặc Order Service. |
| `X-Active-Branch-Id` | Có điều kiện | Public API | Bắt buộc đối với vai trò chi nhánh (`CASHIER`, `MANAGER`). Xác thực tư cách thành viên chi nhánh hợp lệ. |
| `X-Correlation-Id` | Khuyến nghị | Tất cả | Mã UUID v7 truy vết phân tán xuyên suốt qua API Gateway, Step Functions Saga, Payment DB, EventBridge và Provider. Tự sinh nếu thiếu. |
| `X-Causation-Id` | Có điều kiện | Internal / Callback | Định danh của sự kiện hoặc lệnh trực tiếp gây ra thao tác hiện tại. |
| `Idempotency-Key` | Bắt buộc | Các lệnh ghi (POST/PATCH) | Khóa duy nhất do client/Saga sinh nhằm đảm bảo an toàn khi thử lại mạng. |
| `Content-Type` | Bắt buộc | Request có body | `application/json; charset=utf-8`. |

---

### Cấu trúc Bao bì Chuẩn hóa (Envelope Standards)

#### 1. Phản hồi Thành công Thực thể Đơn lẻ (Single Entity Response)

```json
{
  "data": {},
  "meta": {
    "requestId": "req_0192b2d0-7f1d-7abc-8d2c-123456789001",
    "correlationId": "0192b2d0-7f1d-7abc-8d2c-123456789002"
  }
}
```

#### 2. Phản hồi Danh sách Phân trang theo Con trỏ (Cursor-based Paginated Response)

```json
{
  "data": [],
  "meta": {
    "hasMore": true,
    "nextCursor": "ZXlKallYUmxabVZrUVhRaU9pSXdNVEt5WWpKa01DMDNaelFrTF...",
    "limit": 20,
    "requestId": "req_0192b2d0-7f1d-7abc-8d2c-123456789003",
    "correlationId": "0192b2d0-7f1d-7abc-8d2c-123456789002"
  }
}
```

#### 3. Phản hồi Lỗi Chuẩn hóa (Standard Error Envelope)

```json
{
  "error": {
    "code": "invalid_cash_amount",
    "message": "Số tiền mặt khách đưa nhỏ hơn tổng số tiền cần thanh toán của đơn hàng",
    "details": [
      {
        "field": "cashReceivedMinor",
        "code": "insufficient_amount",
        "message": "Số tiền nhận (50000 VND) nhỏ hơn số tiền cần thu (68000 VND)"
      }
    ],
    "requestId": "req_0192b2d0-7f1d-7abc-8d2c-123456789004",
    "correlationId": "0192b2d0-7f1d-7abc-8d2c-123456789002"
  }
}
```

---

### Bảng Mã Lỗi và Mã Trạng thái HTTP

| HTTP Status | Mã lỗi (`error.code`) | Ý nghĩa nghiệp vụ | Khả năng Thử lại (Retryable) |
|:---:|---|---|:---:|
| `200 OK` | — | Thanh toán tiền mặt thành công ngay lập tức hoặc truy vấn dữ liệu thành công. | Không |
| `201 Created` | — | Tạo mới bản ghi hoàn tiền hoặc phân bổ thanh toán thành công. | Không |
| `202 Accepted` | `accepted` | Khởi tạo VNPAY QR thành công (chờ thanh toán IPN) hoặc lệnh đối soát đã đưa vào hàng đợi. | Có (truy vấn trạng thái) |
| `400 Bad Request` | `validation_error` | Request sai định dạng, thiếu trường bắt buộc hoặc vi phạm schema. | Không |
| `401 Unauthorized` | `unauthorized` | Token xác thực thiếu, hết hạn hoặc không hợp lệ. | Có (sau khi refresh token) |
| `403 Forbidden` | `forbidden` | Người dùng hoặc service không có thẩm quyền truy cập tài nguyên. | Không |
| `404 Not Found` | `not_found` | Tài nguyên không tồn tại trong phạm vi tenant/chi nhánh được ủy quyền. | Không |
| `409 Conflict` | `idempotency_conflict` | Cùng `Idempotency-Key` nhưng hash payload yêu cầu bị thay đổi. | Không |
| `409 Conflict` | `payment_already_pending` | Đang tồn tại một lượt thử VNPAY QR còn hiệu lực cho attempt này. | Có (dùng lại QR payload hiện tại) |
| `409 Conflict` | `payment_already_terminal` | Payment attempt đã ở trạng thái kết thúc (`PAID`, `FAILED`, `EXPIRED`, `CANCELLED`). | Không |
| `409 Conflict` | `payment_version_conflict` | Phiên bản snapshot đơn hàng hoặc ETag không khớp với dữ liệu máy chủ. | Không |
| `422 Unprocessable` | `invalid_cash_amount` | Tiền mặt khách đưa nhỏ hơn tổng tiền đơn hàng cần thanh toán. | Không (nhập lại tiền) |
| `422 Unprocessable` | `provider_amount_mismatch` | Số tiền từ cổng VNPAY không khớp với số tiền đăng ký của payment attempt. | Không |
| `422 Unprocessable` | `invalid_provider_signature` | Sai chữ ký số HMAC-SHA512 checksum từ VNPAY callback. | Không |
| `422 Unprocessable` | `refund_amount_exceeded` | Số tiền yêu cầu hoàn vượt quá số dư khả dụng có thể hoàn của thanh toán. | Không |
| `429 Too Many Requests`| `rate_limited` | Vượt quá giới hạn tần suất yêu cầu tại API Gateway hoặc adapter provider. | Có (lùi thời gian Exponential Backoff) |
| `503 Unavailable` | `provider_unavailable` | Cổng thanh toán VNPAY Sandbox bị lỗi mạng hoặc timeout, chưa rõ trạng thái. | Có (thử lại hoặc đưa vào đối soát) |
| `503 Unavailable` | `service_unavailable` | Cơ sở dữ liệu hoặc phụ thuộc nội bộ tạm thời gián đoạn. | Có |

---

## 3. Danh Mục Các Endpoint

| Phương thức | Đường dẫn API | Phân loại | Thẩm quyền / Phạm vi | Ý nghĩa nghiệp vụ |
|---|---|:---:|---|---|
| `POST` | `/internal/v1/orders/{orderId}/payments` | Internal | Workload Identity (Step Functions / Order) | Khởi tạo thanh toán cho đơn hàng (Tiền mặt hoặc VNPAY QR). |
| `POST` | `/internal/v1/payments/{paymentId}/cancel` | Internal | Workload Identity (Step Functions Saga) | Lệnh bồi hoàn/hủy payment pending khi Saga gặp lỗi downstream. |
| `POST` | `/internal/v1/payments/{paymentId}/refund` | Internal | Workload Identity (Step Functions Saga) | Lệnh bồi hoàn hoàn tiền tự động khi commit tồn kho thất bại hoàn toàn. |
| `GET` | `/api/v1/payments/{paymentId}` | Public | `payment:read` (Cognito User) | Xem chi tiết giao dịch thanh toán và trạng thái hiện tại. |
| `GET` | `/api/v1/payments` | Public | `payment:read` (`MANAGER`, `OWNER`) | Tra cứu danh sách lịch sử thanh toán theo chi nhánh/tenant (phân trang cursor). |
| `GET` | `/callbacks/v1/vnpay/ipn` | Callback | Provider Boundary (VNPAY Gateway IPN) | Webhook nhận thông báo kết quả giao dịch ngầm (Server-to-Server) từ VNPAY. |
| `GET` | `/callbacks/v1/vnpay/return` | Callback | Public / Client Browser Redirect | Endpoint nhận chuyển hướng trình duyệt của khách hàng sau khi thanh toán VNPAY. |
| `POST` | `/internal/v1/payments/reconciliation/run` | Internal | Workload Identity (EventBridge Scheduler / Ops) | Kích hoạt phiên đối soát giao dịch pending quá hạn với VNPAY. |
| `GET` | `/api/v1/reconciliations/{reconciliationId}` | Public | `payment:manage` (`MANAGER`, `OWNER`) | Xem kết quả chi tiết của một phiên đối soát giao dịch. |
| `POST` | `/api/v1/payments/{paymentId}/refunds` | Public (P1) | `payment:refund` (`MANAGER`, `OWNER`) | Yêu cầu hoàn tiền một phần hoặc toàn phần cho giao dịch đã thanh toán. |
| `GET` | `/api/v1/refunds/{refundId}` | Public (P1) | `payment:read` (`MANAGER`, `OWNER`) | Xem chi tiết trạng thái phiếu hoàn tiền. |
| `POST` | `/api/v1/payments/split` | Public (P1) | `order:checkout` (`CASHIER`, `MANAGER`) | Phân bổ thanh toán đa phương thức (Split Tender: kết hợp Tiền mặt + VNPAY QR). |

---

## 4. Hợp đồng Chi tiết Từng Endpoint

### 4.1 Các API Nội bộ Điều phối Saga (Internal Saga APIs)

#### `POST /internal/v1/orders/{orderId}/payments`
Được gọi đồng bộ bởi AWS Step Functions Checkout Saga để khởi tạo thanh toán. Tuân thủ nguyên tắc: thông tin tenant, branch và quyền sở hữu đơn hàng được bảo chứng qua Workload Context; payload truyền snapshot tiền tệ bất biến từ Order.

- **Thẩm quyền:** Workload Identity / mTLS của AWS Step Functions hoặc Order Service với quyền `payment:initiate`.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <workload-token>` (Bắt buộc)
  - `Idempotency-Key: <unique-saga-task-key>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Bắt buộc)
  - `Content-Type: application/json; charset=utf-8` (Bắt buộc)
- **Tham số Đường dẫn (Path Parameters):**
  - `orderId` (UUID, Bắt buộc): Định danh đơn hàng cần thanh toán.
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
  "branchId": "0192b2d0-7f23-7abc-8d2c-123456789012",
  "sagaId": "0192b2d0-7f33-7abc-8d2c-123456789012",
  "checkoutAttemptId": "0192b2d0-7f32-7abc-8d2c-123456789012",
  "orderNumber": "A03-000127",
  "paymentMethod": "CASH",
  "amountDueMinor": 68000,
  "currency": "VND",
  "cashReceivedMinor": 100000,
  "orderSnapshotVersion": 6,
  "returnUrl": null,
  "clientIp": "192.168.1.50"
}
```

*Hoặc đối với phương thức VNPAY QR:*

```json
{
  "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
  "branchId": "0192b2d0-7f23-7abc-8d2c-123456789012",
  "sagaId": "0192b2d0-7f33-7abc-8d2c-123456789012",
  "checkoutAttemptId": "0192b2d0-7f32-7abc-8d2c-123456789012",
  "orderNumber": "A03-000127",
  "paymentMethod": "VNPAY_QR",
  "amountDueMinor": 68000,
  "currency": "VND",
  "cashReceivedMinor": null,
  "orderSnapshotVersion": 6,
  "returnUrl": "https://pos.coffeechain.com/checkout-result",
  "clientIp": "192.168.1.50"
}
```

*Quy cách kiểm tra (Validation Rules):*
- `tenantId` & `branchId`: Bắt buộc, khớp với ngữ cảnh token xác thực.
- `sagaId` & `checkoutAttemptId`: Bắt buộc, định danh UUID v7 bất biến của phiên thanh toán.
- `paymentMethod`: Enum `CASH` hoặc `VNPAY_QR`.
- `amountDueMinor`: Số nguyên `BIGINT > 0`, bắt buộc khớp chính xác với snapshot tổng tiền của Order.
- `cashReceivedMinor`: Bắt buộc khi `paymentMethod = CASH` và phải `>= amountDueMinor`; bắt buộc `null` khi `paymentMethod = VNPAY_QR`.
- `returnUrl`: Bắt buộc khi `paymentMethod = VNPAY_QR` (phải thuộc danh sách URL được phép); bắt buộc `null` khi `paymentMethod = CASH`.

- **Phản hồi Thành công đối với Tiền mặt (`200 OK` — Hoàn tất ngay lập tức):**

```json
{
  "data": {
    "paymentId": "0192b2d0-7f34-7abc-8d2c-123456789012",
    "orderId": "0192b2d0-7f30-7abc-8d2c-123456789012",
    "checkoutAttemptId": "0192b2d0-7f32-7abc-8d2c-123456789012",
    "sagaId": "0192b2d0-7f33-7abc-8d2c-123456789012",
    "paymentMethod": "CASH",
    "status": "PAID",
    "amountDueMinor": 68000,
    "amountReceivedMinor": 100000,
    "cashChangeMinor": 32000,
    "currency": "VND",
    "provider": null,
    "providerOrderRef": null,
    "providerTransactionId": null,
    "qrPayload": null,
    "expiresAt": null,
    "paidAt": "2026-10-12T09:15:30Z",
    "reconciliationRequired": false,
    "lateSettlementStatus": null
  },
  "meta": {
    "requestId": "req_0192b2d0-7f34-7abc-8d2c-123456789001",
    "correlationId": "0192b2d0-7f1d-7abc-8d2c-123456789002"
  }
}
```

- **Phản hồi Thành công đối với VNPAY QR (`202 Accepted` — Chờ thanh toán qua IPN):**

```json
{
  "data": {
    "paymentId": "0192b2d0-7f34-7abc-8d2c-123456789012",
    "orderId": "0192b2d0-7f30-7abc-8d2c-123456789012",
    "checkoutAttemptId": "0192b2d0-7f32-7abc-8d2c-123456789012",
    "sagaId": "0192b2d0-7f33-7abc-8d2c-123456789012",
    "paymentMethod": "VNPAY_QR",
    "status": "PENDING",
    "amountDueMinor": 68000,
    "amountReceivedMinor": null,
    "cashChangeMinor": null,
    "currency": "VND",
    "provider": "VNPAY",
    "providerOrderRef": "POS-20261012-000127-A1",
    "providerTransactionId": null,
    "qrPayload": "00020101021238540010A000000727012400069704220110123456789053037045405680005802VN6304ABCD",
    "paymentUrl": "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_Amount=6800000&vnp_Command=pay&vnp_CreateDate=20261012091530&vnp_CurrCode=VND&vnp_IpAddr=192.168.1.50&vnp_Locale=vn&vnp_OrderInfo=Thanh+toan+don+hang+A03-000127&vnp_OrderType=other&vnp_ReturnUrl=https%3A%2F%2Fpos.coffeechain.com%2Fcheckout-result&vnp_TmnCode=COFFEETMN&vnp_TxnRef=POS-20261012-000127-A1&vnp_Version=2.1.0&vnp_SecureHash=3e4f...",
    "expiresAt": "2026-10-12T09:30:30Z",
    "paidAt": null,
    "reconciliationRequired": false,
    "lateSettlementStatus": null
  },
  "meta": {
    "requestId": "req_0192b2d0-7f34-7abc-8d2c-123456789002",
    "correlationId": "0192b2d0-7f1d-7abc-8d2c-123456789002"
  }
}
```

- **Mã lỗi khả dĩ:**
  - `400 Bad Request` (`validation_error`): Request body sai định dạng.
  - `401 Unauthorized` (`unauthorized`): Thiếu hoặc sai workload token.
  - `409 Conflict` (`idempotency_conflict`): Trùng khóa nhưng payload thay đổi.
  - `409 Conflict` (`payment_already_pending`): Đã có VNPAY attempt đang active. Trả về thông tin QR hiện tại để POS quét tiếp.
  - `422 Unprocessable` (`invalid_cash_amount`): Khách đưa thiếu tiền mặt.
  - `503 Service Unavailable` (`provider_unavailable`): Cổng VNPAY sandbox không phản hồi khi sinh QR code.

---

#### `POST /internal/v1/payments/{paymentId}/cancel`
Lệnh bồi hoàn (Compensation Step) được AWS Step Functions Checkout Saga kích hoạt khi Saga gặp lỗi ở bước tiếp theo (ví dụ: giữ chỗ nguyên vật liệu trong Inventory bị thất bại hoặc hết thời gian chờ checkout).

- **Thẩm quyền:** Workload Identity của AWS Step Functions.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <workload-token>` (Bắt buộc)
  - `Idempotency-Key: <unique-saga-task-key>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Bắt buộc)
  - `Content-Type: application/json; charset=utf-8` (Bắt buộc)
- **Tham số Đường dẫn (Path Parameters):**
  - `paymentId` (UUID, Bắt buộc): Định danh giao dịch thanh toán.
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "reasonCode": "INVENTORY_RESERVATION_FAILED",
  "reasonDescription": "Không đủ nguyên vật liệu sữa đặc tại chi nhánh để hoàn tất đơn",
  "cancelledAt": "2026-10-12T09:16:00Z"
}
```

- **Phản hồi Thành công (`200 OK`):**

```json
{
  "data": {
    "paymentId": "0192b2d0-7f34-7abc-8d2c-123456789012",
    "status": "CANCELLED",
    "cancelledAt": "2026-10-12T09:16:00Z",
    "reasonCode": "INVENTORY_RESERVATION_FAILED"
  },
  "meta": {
    "requestId": "req_0192b2d0-7f34-7abc-8d2c-123456789003",
    "correlationId": "0192b2d0-7f1d-7abc-8d2c-123456789002"
  }
}
```

*Quy tắc trạng thái:*
- Nếu payment đang ở trạng thái `PENDING`: Chuyển sang `CANCELLED`, vô hiệu hóa QR code, phát sự kiện `PaymentCancelled.v1`.
- Nếu payment đã ở trạng thái kết thúc `CANCELLED` trước đó: Xử lý No-op, trả về `200 OK` với dữ liệu ban đầu.
- Nếu payment đã ở trạng thái `PAID` (tiền đã trừ hoặc tiền mặt đã nhận): Không thể hủy đơn giản; endpoint trả về mã lỗi `409 payment_already_paid`, yêu cầu Saga chuyển sang nhánh gọi `POST /internal/v1/payments/{paymentId}/refund`.

---

#### `POST /internal/v1/payments/{paymentId}/refund`
Lệnh bồi hoàn nâng cao trong Saga (Saga Compensation) được kích hoạt khi tiền đã được xác nhận thanh toán (`PAID`), nhưng bước commit tồn kho hoặc bước finalize Order gặp sự cố kỹ thuật nghiêm trọng không thể phục hồi.

- **Thẩm quyền:** Workload Identity của AWS Step Functions.
- **Tiêu đề Yêu cầu (Request Headers):** `Authorization`, `Idempotency-Key`, `X-Correlation-Id`, `Content-Type`.
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "refundAmountMinor": 68000,
  "currency": "VND",
  "reason": "SAGA_EXECUTION_FAILURE_DOWNSTREAM",
  "correlationId": "0192b2d0-7f1d-7abc-8d2c-123456789002"
}
```

- **Phản hồi Thành công (`200 OK` / `202 Accepted`):** Trả về chi tiết phiếu hoàn tiền khẩn cấp và phát sự kiện `PaymentRefunded.v1`.

---

### 4.2 Các API Công khai cho Người dùng & Quản lý (Public REST APIs)

#### `GET /api/v1/payments/{paymentId}`
Truy vấn chi tiết trạng thái của một giao dịch thanh toán. Máy POS thăm dò (polling) endpoint này nếu không nhận được sự kiện real-time hoặc sau khi khách hàng quét mã VNPAY QR.

- **Thẩm quyền:** Quyền `payment:read` (`CASHIER`, `MANAGER`, `OWNER`).
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <cognito-token>` (Bắt buộc)
  - `X-Active-Branch-Id: <uuid>` (Bắt buộc với vai trò chi nhánh)
  - `X-Correlation-Id: <uuid>` (Tùy chọn)
- **Tham số Đường dẫn (Path Parameters):**
  - `paymentId` (UUID, Bắt buộc): Định danh giao dịch thanh toán.
- **Phản hồi Thành công (`200 OK`):**

```json
{
  "data": {
    "id": "0192b2d0-7f34-7abc-8d2c-123456789012",
    "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
    "branchId": "0192b2d0-7f23-7abc-8d2c-123456789012",
    "orderId": "0192b2d0-7f30-7abc-8d2c-123456789012",
    "checkoutAttemptId": "0192b2d0-7f32-7abc-8d2c-123456789012",
    "paymentMethod": "VNPAY_QR",
    "status": "PAID",
    "amountDueMinor": 68000,
    "amountReceivedMinor": null,
    "cashChangeMinor": null,
    "currency": "VND",
    "provider": "VNPAY",
    "providerOrderRef": "POS-20261012-000127-A1",
    "providerTransactionId": "14569823",
    "qrPayload": "00020101021238540010A00000072701240006970422...",
    "expiresAt": "2026-10-12T09:30:30Z",
    "paidAt": "2026-10-12T09:18:45Z",
    "reconciliationRequired": false,
    "lateSettlementStatus": null,
    "stateTransitions": [
      {
        "fromStatus": null,
        "toStatus": "PENDING",
        "reasonCode": "INITIALIZE_VNPAY_QR",
        "occurredAt": "2026-10-12T09:15:30Z"
      },
      {
        "fromStatus": "PENDING",
        "toStatus": "PAID",
        "reasonCode": "VNPAY_IPN_VERIFIED_SUCCESS",
        "occurredAt": "2026-10-12T09:18:45Z"
      }
    ],
    "createdAt": "2026-10-12T09:15:30Z",
    "updatedAt": "2026-10-12T09:18:45Z"
  },
  "meta": {
    "requestId": "req_0192b2d0-7f34-7abc-8d2c-123456789005",
    "correlationId": "0192b2d0-7f1d-7abc-8d2c-123456789002"
  }
}
```

- **Mã lỗi khả dĩ:** `401 Unauthorized`, `403 Forbidden`, `404 Not Found` (không tìm thấy hoặc không thuộc chi nhánh của người gọi).

---

#### `GET /api/v1/payments`
Tra cứu danh sách lịch sử thanh toán theo chi nhánh hoặc toàn tenant. Sử dụng cơ chế phân trang theo con trỏ (Cursor Pagination) nhằm đảm bảo hiệu năng cao khi dữ liệu giao dịch lớn.

- **Thẩm quyền:** Quyền `payment:read` (`MANAGER`, `OWNER`).
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <cognito-token>` (Bắt buộc)
  - `X-Active-Branch-Id: <uuid>` (Bắt buộc với Manager)
  - `X-Correlation-Id: <uuid>` (Tùy chọn)
- **Tham số Truy vấn (Query Parameters):**
  - `limit` (integer, Mặc định: `20`, Tối đa: `100`): Số bản ghi trên mỗi trang.
  - `cursor` (string, Tùy chọn): Con trỏ phân trang mã hóa base64 từ lần đọc trước.
  - `branchId` (UUID, Tùy chọn): Lọc theo chi nhánh (chỉ dành cho Owner).
  - `status` (string, Tùy chọn): `PENDING`, `PAID`, `FAILED`, `EXPIRED`, `CANCELLED`.
  - `paymentMethod` (string, Tùy chọn): `CASH`, `VNPAY_QR`.
  - `orderId` (UUID, Tùy chọn): Lọc theo đơn hàng.
  - `reconciliationRequired` (boolean, Tùy chọn): Lọc các giao dịch có cờ bất thường cần đối soát.
  - `from` & `to` (ISO-8601 UTC, Tùy chọn): Khoảng thời gian tạo giao dịch.
- **Phản hồi Thành công (`200 OK`):**

```json
{
  "data": [
    {
      "id": "0192b2d0-7f34-7abc-8d2c-123456789012",
      "orderId": "0192b2d0-7f30-7abc-8d2c-123456789012",
      "branchId": "0192b2d0-7f23-7abc-8d2c-123456789012",
      "paymentMethod": "CASH",
      "status": "PAID",
      "amountDueMinor": 68000,
      "amountReceivedMinor": 100000,
      "cashChangeMinor": 32000,
      "currency": "VND",
      "paidAt": "2026-10-12T09:15:30Z",
      "reconciliationRequired": false,
      "createdAt": "2026-10-12T09:15:30Z"
    }
  ],
  "meta": {
    "hasMore": false,
    "nextCursor": null,
    "limit": 20,
    "requestId": "req_0192b2d0-7f34-7abc-8d2c-123456789006",
    "correlationId": "0192b2d0-7f1d-7abc-8d2c-123456789002"
  }
}
```

---

#### `GET /api/v1/reconciliations/{reconciliationId}`
Tra cứu chi tiết kết quả của một phiên chạy đối soát tự động hoặc do nhân viên vận hành kích hoạt.

- **Thẩm quyền:** Quyền `payment:manage` (`MANAGER`, `OWNER`).
- **Tiêu đề Yêu cầu (Request Headers):** `Authorization`, `X-Active-Branch-Id`, `X-Correlation-Id`.
- **Tham số Đường dẫn (Path Parameters):**
  - `reconciliationId` (UUID, Bắt buộc): Định danh phiên đối soát.
- **Phản hồi Thành công (`200 OK`):**

```json
{
  "data": {
    "id": "0192b2d0-7f1d-7abc-8d2c-123456789021",
    "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
    "status": "COMPLETED",
    "reason": "PENDING_TIMEOUT_SCAN",
    "requestedByType": "SCHEDULER",
    "totalItems": 3,
    "processedItems": 3,
    "resolvedCount": 2,
    "mismatchCount": 1,
    "items": [
      {
        "paymentId": "0192b2d0-7f34-7abc-8d2c-123456789012",
        "provider": "VNPAY",
        "providerReference": "POS-20261012-000127-A1",
        "localStatusBefore": "PENDING",
        "providerStatus": "SUCCESS",
        "result": "PAID",
        "operatorStatus": "RESOLVED",
        "errorCode": null
      },
      {
        "paymentId": "0192b2d0-7f34-7abc-8d2c-123456789099",
        "provider": "VNPAY",
        "providerReference": "POS-20261012-000099-A1",
        "localStatusBefore": "EXPIRED",
        "providerStatus": "SUCCESS",
        "result": "SETTLED_LATE",
        "operatorStatus": "OPEN",
        "errorCode": "LATE_SETTLEMENT_AFTER_EXPIRY"
      }
    ],
    "requestedAt": "2026-10-12T10:00:00Z",
    "completedAt": "2026-10-12T10:00:05Z"
  },
  "meta": {
    "requestId": "req_0192b2d0-7f34-7abc-8d2c-123456789007",
    "correlationId": "0192b2d0-7f1d-7abc-8d2c-123456789002"
  }
}
```

---

### 4.3 Webhook Nhà cung cấp VNPAY (Provider Boundary)

#### `GET /callbacks/v1/vnpay/ipn`
Endpoint tiếp nhận thông báo kết quả thanh toán tức thời (Instant Payment Notification - IPN) gửi từ máy chủ VNPAY tới API Gateway.

- **Giao thức & Ranh giới:** HTTP `GET` công khai tại API Gateway; API Gateway bảo toàn nguyên vẹn raw query parameters và chuyển tiếp tới VNPAY IPN Handler.
- **Bảo mật & Xác thực Chữ ký (Mandatory Checksum Verification):**
  1. Trích xuất toàn bộ tham số bắt đầu bằng `vnp_*`.
  2. Tách riêng `vnp_SecureHash` và `vnp_SecureHashType` (nếu có).
  3. Sắp xếp các tham số còn lại theo thứ tự bảng chữ cái ASCII (a-z) của tên khóa (Key).
  4. Nối các cặp `key=value` bằng ký tự `&` sau khi đã URL-encode theo chuẩn RFC 3986 (khoảng trắng thành `%20` hoặc `+` theo quy chuẩn VNPAY).
  5. Tính toán mã băm HMAC-SHA512 với `vnp_HashSecret` lấy từ AWS Secrets Manager.
  6. So sánh bằng hàm so sánh an toàn thời gian cố định (Constant-time string comparison) với `vnp_SecureHash`. Nếu không khớp, lập tức ghi log an ninh và trả về mã lỗi VNPAY `RspCode: 97`.
- **Tham số Truy vấn (Query Parameters từ VNPAY):**

| Tham số | Bắt buộc | Mô tả & Chuẩn hóa |
|---|:---:|---|
| `vnp_TmnCode` | Có | Mã định danh thiết bị / website của merchant do VNPAY cấp (ví dụ: `COFFEETMN`). |
| `vnp_Amount` | Có | Số tiền thanh toán nhân 100 (ví dụ: `6800000` tương đương `68000 VND`). |
| `vnp_BankCode` | Có | Ngân hàng thanh toán (ví dụ: `NCB`, `VCB`, `VIETINBANK`). |
| `vnp_BankTranNo`| Không | Mã giao dịch phía ngân hàng. |
| `vnp_CardType` | Không | Loại thẻ / hình thức thanh toán (ví dụ: `QRCODE`). |
| `vnp_PayDate` | Có | Thời gian thanh toán định dạng `yyyyMMddHHmmss` theo giờ GMT+7. |
| `vnp_OrderInfo` | Có | Nội dung mô tả đơn hàng không dấu. |
| `vnp_TransactionNo`| Có | Mã số giao dịch ghi nhận trên hệ thống máy chủ VNPAY (ví dụ: `14569823`). |
| `vnp_ResponseCode` | Có | Mã phản hồi kết quả: `"00"` là thành công. |
| `vnp_TransactionStatus`| Có | Trạng thái giao dịch: `"00"` là thành công. |
| `vnp_TxnRef` | Có | Mã tham chiếu merchant (`providerOrderRef`, ví dụ: `POS-20261012-000127-A1`). |
| `vnp_SecureHash`| Có | Chuỗi kiểm tra toàn vẹn băm HMAC-SHA512. |

- **Phản hồi Chuẩn hóa Trả về cho VNPAY:**
  - VNPAY yêu cầu phản hồi theo định dạng JSON với mã HTTP `200 OK` chứa hai trường `RspCode` và `Message`.

```json
{
  "RspCode": "00",
  "Message": "Confirm Success"
}
```

- **Bảng Ánh xạ Mã Phản hồi IPN cho VNPAY:**

| Tình huống xử lý nội bộ | `RspCode` | `Message` | Hành vi hệ thống |
|---|:---:|---|---|
| Xác thực hợp lệ, chuyển trạng thái `PAID` thành công | `"00"` | `"Confirm Success"` | Giao dịch hoàn tất, phát sự kiện `PaymentPaid.v1`. |
| Giao dịch đã được xác nhận `PAID` trước đó (Trùng lặp IPN) | `"02"` | `"Order already confirmed"` | Xử lý No-op; không tạo tác dụng phụ lần hai. |
| Không tìm thấy payment attempt tương ứng với `vnp_TxnRef` | `"01"` | `"Order not found"` | Ghi log cảnh báo; không tạo giao dịch mới. |
| Số tiền `vnp_Amount / 100` không khớp với `amountDueMinor` | `"04"` | `"Invalid amount"` | Đánh dấu `reconciliation_required = true`, tạo alert. |
| Sai chữ ký checksum `vnp_SecureHash` | `"97"` | `"Invalid Checksum"` | Ghi log vi phạm an ninh, từ chối xử lý. |
| Giao dịch VNPAY báo thất bại (`vnp_ResponseCode != "00"`) | `"00"` | `"Confirm Success"` | Chuyển trạng thái `FAILED`, phát sự kiện `PaymentFailed.v1`. |
| Giao dịch thành công đến sau khi attempt đã `EXPIRED`/`CANCELLED` | `"00"` | `"Confirm Success"` | Ghi nhận `late_payment_reconciliation(SETTLED_LATE)`, phát `PaymentReconciliationRequired.v1`. Tuyệt đối không chuyển sang `PAID`. |

---

#### `GET /callbacks/v1/vnpay/return`
Endpoint tiếp nhận chuyển hướng trình duyệt (Customer Return URL) sau khi khách hàng hoàn tất giao diện thanh toán VNPAY sandbox trên điện thoại hoặc trình duyệt POS.

- **Mục đích:** Hiển thị giao diện kết quả thân thiện cho người dùng cuối (Frontend UI); **không bao giờ** dùng làm căn cứ quyết định hoàn tất đơn hàng (IPN mới là nguồn chân lý tài chính).
- **Quy trình:**
  1. Xác thực chữ ký `vnp_SecureHash`.
  2. Tra cứu trạng thái thanh toán hiện tại từ Payment DB.
  3. Chuyển hướng người dùng về màn hình hoàn tất đơn hàng trên POS frontend với trạng thái:
     - `success`: Nếu Payment đã nhận được IPN và chuyển sang `PAID`.
     - `pending`: Nếu IPN chưa tới; frontend hiển thị màn hình chờ và tự động polling endpoint `GET /api/v1/payments/{paymentId}`.
     - `failed`: Nếu mã phản hồi VNPAY là thất bại hoặc giao dịch bị hủy bởi người dùng.

---

### 4.4 Các API Giai đoạn P1 & P2 (Feature-Gated & Mở rộng)

#### `POST /api/v1/payments/{paymentId}/refunds` (Giai đoạn P1)
Yêu cầu hoàn trả một phần hoặc toàn bộ số tiền đã thanh toán của giao dịch.

- **Thẩm quyền:** Quyền `payment:refund` (`MANAGER`, `OWNER`).
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <cognito-token>` (Bắt buộc)
  - `X-Active-Branch-Id: <uuid>` (Bắt buộc)
  - `Idempotency-Key: <unique-key>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Khuyến nghị)
  - `Content-Type: application/json; charset=utf-8` (Bắt buộc)
- **Tham số Đường dẫn (Path Parameters):**
  - `paymentId` (UUID, Bắt buộc): Định danh giao dịch gốc đã thanh toán.
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "amountMinor": 34000,
  "currency": "VND",
  "reason": "CUSTOMER_RETURN_ITEM",
  "reasonNote": "Khách trả lại 1 ly Cà phê sữa do nhầm món"
}
```

*Quy cách kiểm tra:*
- Payment gốc bắt buộc phải ở trạng thái `PAID`.
- `amountMinor`: Số nguyên `> 0` và `<= số dư khả dụng có thể hoàn` (`amountDueMinor - tổng các lần hoàn thành công trước đó`).
- Đối với thanh toán qua VNPAY, adapter sẽ kích hoạt lệnh gọi API `refund` sang máy chủ VNPAY theo hợp đồng querydr & refund.

- **Phản hồi Thành công (`201 Created` đối với Tiền mặt hoặc `202 Accepted` đối với VNPAY):**

```json
{
  "data": {
    "refundId": "0192b2d0-7f34-7abc-8d2c-123456789088",
    "paymentId": "0192b2d0-7f34-7abc-8d2c-123456789012",
    "orderId": "0192b2d0-7f30-7abc-8d2c-123456789012",
    "amountMinor": 34000,
    "currency": "VND",
    "status": "COMPLETED",
    "reason": "CUSTOMER_RETURN_ITEM",
    "providerRefundRef": null,
    "requestedBy": "0192b2d0-7f11-7abc-8d2c-123456789012",
    "processedAt": "2026-10-12T09:40:00Z",
    "createdAt": "2026-10-12T09:40:00Z"
  },
  "meta": {
    "requestId": "req_0192b2d0-7f34-7abc-8d2c-123456789008",
    "correlationId": "0192b2d0-7f1d-7abc-8d2c-123456789002"
  }
}
```

- **Mã lỗi khả dĩ:** `403 Forbidden`, `404 Not Found`, `409 payment_not_paid`, `422 refund_amount_exceeded`.

---

#### `GET /api/v1/refunds/{refundId}` (Giai đoạn P1)
Tra cứu trạng thái của phiếu hoàn tiền.

- **Thẩm quyền:** Quyền `payment:read` (`MANAGER`, `OWNER`).
- **Phản hồi Thành công (`200 OK`):** Trả về `RefundResponse` tương tự như trên.

---

#### `POST /api/v1/payments/split` (Giai đoạn P1)
Thanh toán đa phương thức (Split Tender): Cho phép khách hàng thanh toán một đơn hàng bằng sự kết hợp của nhiều hình thức (ví dụ: một phần trả bằng Tiền mặt, phần còn lại quét mã VNPAY QR).

- **Thẩm quyền:** Quyền `order:checkout` (`CASHIER`, `MANAGER`).
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "orderId": "0192b2d0-7f30-7abc-8d2c-123456789012",
  "totalDueMinor": 100000,
  "currency": "VND",
  "allocations": [
    {
      "allocationId": "alloc-1",
      "method": "CASH",
      "amountMinor": 50000,
      "cashReceivedMinor": 50000
    },
    {
      "allocationId": "alloc-2",
      "method": "VNPAY_QR",
      "amountMinor": 50000,
      "returnUrl": "https://pos.coffeechain.com/checkout-result"
    }
  ]
}
```

*Quy tắc:* Tổng `amountMinor` của toàn bộ các phần phân bổ bắt buộc phải bằng chính xác `totalDueMinor`. Các attempt con được quản lý độc lập thông qua bảng `payment_allocations`.

---

## 5. Hợp đồng Máy Trạng Thái (State Machine Contract)

### 5.1 Sơ đồ Chuyển đổi Trạng thái

```text
       [ Khởi tạo thanh toán ]
                 │
                 ▼
             ┌─────────┐
             │ PENDING │
             └────┬────┘
                  │
     ┌────────────┼──────────────────────────┬────────────────────────┐
     │ (Tiền mặt đủ /                      │ (Hết hạn pending       │ (Hủy từ POS /
     │  VNPAY IPN hợp lệ)                  │  hoặc timeout quét)    │  Saga bồi hoàn)
     ▼                                     ▼                        ▼
┌─────────┐                           ┌─────────┐              ┌───────────┐
│  PAID   │ (Terminal)                │ EXPIRED │ (Terminal)   │ CANCELLED │ (Terminal)
└─────────┘                           └────┬────┘              └─────┬─────┘
     ▲                                     │                         │
     │                                     │                         │
     │ (Provider khẳng định                │ (VNPAY báo thành công   │ (VNPAY báo thành công
     │  thành công khi còn pending)        │  sau khi đã hết hạn)    │  sau khi đã hủy)
     │                                     ▼                         ▼
┌─────────┐                           ┌────────────────────────────────────────┐
│  FAILED │ (Terminal)                │      LATE_PAYMENT_RECONCILIATION       │
└─────────┘                           │        (status: SETTLED_LATE)          │
 (Provider báo                        │                                        │
  thất bại xác thực)                  │ * Chỉ phát PaymentReconciliationRequired│
                                      │ * TUYỆT ĐỐI KHÔNG phát PaymentPaid     │
                                      │ * TUYỆT ĐỐI KHÔNG làm sống lại Order   │
                                      └────────────────────────────────────────┘
```

### 5.2 Các Quy tắc Bất biến của Máy Trạng thái (Invariants)

1. **Bất biến Trạng thái Kết thúc:** Các trạng thái `PAID`, `FAILED`, `EXPIRED`, `CANCELLED` là trạng thái kết thúc (Terminal). Mọi lệnh hoặc callback gửi đến sau khi attempt đã ở trạng thái kết thúc đều không được phép ghi đè trạng thái của `payment_attempts`.
2. **Quy tắc Xử lý Trùng lặp (Idempotent No-op):** Nếu nhận được callback hoặc lệnh trùng khớp với trạng thái hiện tại (ví dụ: nhận thêm IPN thành công cho một attempt đã `PAID`), hệ thống xử lý No-op và trả về phản hồi thành công ban đầu.
3. **Quy tắc Quyết toán Muộn (Late Settlement Invariant):**
   - Khi nhà cung cấp VNPAY gửi IPN báo thành công hoặc đối soát xác nhận giao dịch đã trừ tiền thành công, nhưng attempt nội bộ đã chuyển sang `EXPIRED` hoặc `CANCELLED`:
   - Hệ thống **tuyệt đối không** chuyển trạng thái attempt về `PAID`.
   - Hệ thống ghi một bản ghi mới vào bảng `late_payment_reconciliation` với trạng thái `settlement_status = "SETTLED_LATE"`.
   - Hệ thống chỉ phát ra sự kiện `PaymentReconciliationRequired.v1` để nhân viên vận hành xử lý thủ công (liên hệ khách hàng chuyển khoản lại hoặc tạo đơn mới).
   - Hệ thống **tuyệt đối không phát sự kiện `PaymentPaid.v1`**, không phát `OrderPaid.v1` và không kích hoạt commit tồn kho Inventory.
4. **Quy tắc Trạng thái Chưa xác định (Indeterminate State):**
   - Nếu gọi sang VNPAY bị timeout hoặc mạng gián đoạn, payment attempt vẫn giữ nguyên trạng thái `PENDING`, đánh dấu `reconciliation_required = true` và đưa vào hàng đợi đối soát tự động của worker.

---

## 6. Hợp đồng Sự kiện Tích hợp (Integration Event Contracts)

Các sự kiện miền của Payment Service tuân thủ cấu trúc bao bì (Envelope) chuẩn hóa của nền tảng POS. Cơ chế chuyển phát đảm bảo ít nhất một lần (at-least-once); các consumer khử trùng lặp dựa trên `eventId`.

### 6.1 Cấu trúc Bao bì Sự kiện Chuẩn (Event Envelope)

```json
{
  "eventId": "0192b2d0-7f34-7abc-8d2c-123456789099",
  "eventType": "PaymentPaid",
  "eventVersion": 1,
  "occurredAt": "2026-10-12T09:18:45Z",
  "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
  "branchId": "0192b2d0-7f23-7abc-8d2c-123456789012",
  "aggregateType": "Payment",
  "aggregateId": "0192b2d0-7f34-7abc-8d2c-123456789012",
  "correlationId": "0192b2d0-7f1d-7abc-8d2c-123456789002",
  "causationId": "0192b2d0-7f34-7abc-8d2c-123456789005",
  "producer": "payment-service",
  "payload": {}
}
```

---

### 6.2 Chi tiết Schemas Từng Sự Kiện

#### 1. `PaymentInitiated.v1`
Phát ra khi một lượt thử thanh toán mới được khởi tạo thành công (tiền mặt hoặc sinh mã VNPAY QR).

```json
{
  "paymentId": "0192b2d0-7f34-7abc-8d2c-123456789012",
  "orderId": "0192b2d0-7f30-7abc-8d2c-123456789012",
  "checkoutAttemptId": "0192b2d0-7f32-7abc-8d2c-123456789012",
  "sagaId": "0192b2d0-7f33-7abc-8d2c-123456789012",
  "paymentMethod": "VNPAY_QR",
  "amountDueMinor": 68000,
  "currency": "VND",
  "provider": "VNPAY",
  "providerOrderRef": "POS-20261012-000127-A1",
  "expiresAt": "2026-10-12T09:30:30Z",
  "initiatedAt": "2026-10-12T09:15:30Z"
}
```

---

#### 2. `PaymentPaid.v1`
Phát ra khi thanh toán được xác nhận thành công (tiền mặt đã nhận đủ hoặc VNPAY IPN checksum hợp lệ). Sự kiện này được AWS Step Functions Saga lắng nghe để kích hoạt bước commit tồn kho trong Inventory Service.

```json
{
  "paymentId": "0192b2d0-7f34-7abc-8d2c-123456789012",
  "orderId": "0192b2d0-7f30-7abc-8d2c-123456789012",
  "checkoutAttemptId": "0192b2d0-7f32-7abc-8d2c-123456789012",
  "sagaId": "0192b2d0-7f33-7abc-8d2c-123456789012",
  "paymentMethod": "VNPAY_QR",
  "amountPaidMinor": 68000,
  "currency": "VND",
  "amountReceivedMinor": null,
  "cashChangeMinor": null,
  "provider": "VNPAY",
  "providerTransactionId": "14569823",
  "paidAt": "2026-10-12T09:18:45Z"
}
```

---

#### 3. `PaymentFailed.v1`
Phát ra khi thanh toán thất bại dứt khoát (VNPAY báo lỗi giao dịch hoặc khách hủy giao dịch trên cổng thanh toán).

```json
{
  "paymentId": "0192b2d0-7f34-7abc-8d2c-123456789012",
  "orderId": "0192b2d0-7f30-7abc-8d2c-123456789012",
  "checkoutAttemptId": "0192b2d0-7f32-7abc-8d2c-123456789012",
  "reasonCode": "PROVIDER_DECLINED",
  "reasonMessage": "Giao dịch không thành công do thẻ/tài khoản của quý khách không đủ số dư",
  "retryable": false,
  "failedAt": "2026-10-12T09:17:10Z"
}
```

---

#### 4. `PaymentExpired.v1`
Phát ra khi lượt thử thanh toán VNPAY QR vượt quá thời gian hiệu lực (`expiresAt`) mà chưa nhận được tín hiệu thanh toán thành công.

```json
{
  "paymentId": "0192b2d0-7f34-7abc-8d2c-123456789012",
  "orderId": "0192b2d0-7f30-7abc-8d2c-123456789012",
  "checkoutAttemptId": "0192b2d0-7f32-7abc-8d2c-123456789012",
  "reasonCode": "PENDING_TIMEOUT",
  "expiredAt": "2026-10-12T09:30:30Z"
}
```

---

#### 5. `PaymentCancelled.v1`
Phát ra khi payment attempt đang pending bị hủy bởi nhân viên thu ngân hoặc bởi lệnh bồi hoàn từ Step Functions Saga.

```json
{
  "paymentId": "0192b2d0-7f34-7abc-8d2c-123456789012",
  "orderId": "0192b2d0-7f30-7abc-8d2c-123456789012",
  "checkoutAttemptId": "0192b2d0-7f32-7abc-8d2c-123456789012",
  "cancelledBy": "0192b2d0-7f11-7abc-8d2c-123456789012",
  "reasonCode": "CASHIER_ABORTED",
  "cancelledAt": "2026-10-12T09:20:00Z"
}
```

---

#### 6. `PaymentReconciliationRequired.v1`
Phát ra khi phát hiện sai lệch bất thường tài chính (ví dụ: sai số tiền, sai mã hash, hoặc tiền báo về sau khi attempt đã hết hạn/hủy `SETTLED_LATE`). Sự kiện này đưa vào hàng đợi cảnh báo cho nhân viên vận hành và kiểm toán.

```json
{
  "paymentId": "0192b2d0-7f34-7abc-8d2c-123456789012",
  "orderId": "0192b2d0-7f30-7abc-8d2c-123456789012",
  "checkoutAttemptId": "0192b2d0-7f32-7abc-8d2c-123456789012",
  "reconciliationId": "0192b2d0-7f1d-7abc-8d2c-123456789021",
  "reasonCode": "SETTLED_LATE",
  "settlementStatus": "SETTLED_LATE",
  "amountMinor": 68000,
  "currency": "VND",
  "provider": "VNPAY",
  "providerTransactionId": "14569823",
  "occurredAt": "2026-10-12T09:35:00Z"
}
```

---

## 7. Hợp đồng Cơ sở Dữ liệu & Flyway Migrations (Database Contracts)

Payment Service sở hữu một cơ sở dữ liệu PostgreSQL độc lập (`pos_payment`). Dưới đây là mã DDL đầy đủ, chi tiết cho 6 bản migration Flyway tuần tự:

### `V1__initial_payment_schema.sql`
Thiết lập các bảng cốt lõi quản lý phiên thử thanh toán, lịch sử chuyển trạng thái và nhật ký callback từ nhà cung cấp.

```sql
-- V1__initial_payment_schema.sql
-- Module: Payment Service
-- Bounded Context: Payment & Provider Integrations

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Bảng 1: payment_attempts - Quản lý các lượt thử thanh toán
CREATE TABLE payment_attempts (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    order_id UUID NOT NULL,
    checkout_attempt_id UUID NOT NULL,
    saga_id UUID NOT NULL,
    method TEXT NOT NULL,
    status TEXT NOT NULL,
    amount_due_minor BIGINT NOT NULL,
    amount_received_minor BIGINT NULL,
    cash_change_minor BIGINT NULL,
    currency TEXT NOT NULL DEFAULT 'VND',
    provider TEXT NULL,
    provider_order_ref TEXT NULL,
    provider_transaction_id TEXT NULL,
    qr_payload TEXT NULL,
    expires_at TIMESTAMPTZ NULL,
    paid_at TIMESTAMPTZ NULL,
    failure_code TEXT NULL,
    failure_reason_safe TEXT NULL,
    reconciliation_required BOOLEAN NOT NULL DEFAULT FALSE,
    created_by_type TEXT NOT NULL,
    created_by_id UUID NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_payment_method CHECK (method IN ('CASH', 'VNPAY_QR')),
    CONSTRAINT ck_payment_status CHECK (status IN ('PENDING', 'PAID', 'FAILED', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT ck_payment_amount_due CHECK (amount_due_minor >= 0),
    CONSTRAINT ck_payment_amount_received CHECK (amount_received_minor IS NULL OR amount_received_minor >= 0),
    CONSTRAINT ck_payment_cash_change CHECK (cash_change_minor IS NULL OR cash_change_minor >= 0),
    CONSTRAINT ck_payment_currency CHECK (currency = 'VND'),
    CONSTRAINT ck_payment_created_by_type CHECK (created_by_type IN ('USER', 'SAGA', 'SCHEDULER', 'PROVIDER'))
);

-- Bảng 2: payment_state_transitions - Nhật ký chuyển đổi trạng thái (Append-only)
CREATE TABLE payment_state_transitions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    payment_id UUID NOT NULL REFERENCES payment_attempts(id) ON DELETE RESTRICT,
    from_status TEXT NULL,
    to_status TEXT NOT NULL,
    reason_code TEXT NOT NULL,
    actor_type TEXT NOT NULL,
    actor_id UUID NULL,
    correlation_id UUID NOT NULL,
    causation_id UUID NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_transition_to_status CHECK (to_status IN ('PENDING', 'PAID', 'FAILED', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT ck_transition_actor_type CHECK (actor_type IN ('USER', 'SERVICE', 'PROVIDER', 'WORKER'))
);

-- Bảng 3: provider_callbacks - Lưu vết dữ liệu callback từ cổng thanh toán
CREATE TABLE provider_callbacks (
    id UUID PRIMARY KEY,
    tenant_id UUID NULL,
    branch_id UUID NULL,
    payment_id UUID NULL REFERENCES payment_attempts(id) ON DELETE SET NULL,
    provider TEXT NOT NULL,
    provider_event_key TEXT NOT NULL,
    provider_transaction_id TEXT NULL,
    payload_hash TEXT NOT NULL,
    signature_valid BOOLEAN NOT NULL,
    provider_response_code TEXT NULL,
    provider_transaction_status TEXT NULL,
    raw_payload_encrypted JSONB NULL,
    processing_result TEXT NOT NULL,
    rejection_reason_code TEXT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    provider_occurred_at TIMESTAMPTZ NULL,
    processed_at TIMESTAMPTZ NULL,

    CONSTRAINT ck_callback_processing_result CHECK (processing_result IN ('ACCEPTED', 'DUPLICATE', 'REJECTED', 'RECONCILIATION_REQUIRED'))
);
```

---

### `V2__reconciliation_and_late_settlement.sql`
Thiết lập bảng quản lý các phiên chạy đối soát, danh sách các mục kiểm tra và bảng ghi nhận ngoại lệ quyết toán muộn (`SETTLED_LATE`).

```sql
-- V2__reconciliation_and_late_settlement.sql

-- Bảng 4: reconciliation_runs - Phiên chạy đối soát
CREATE TABLE reconciliation_runs (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    requested_by_type TEXT NOT NULL,
    requested_by_id UUID NULL,
    reason TEXT NOT NULL,
    status TEXT NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMPTZ NULL,
    completed_at TIMESTAMPTZ NULL,
    correlation_id UUID NOT NULL,

    CONSTRAINT ck_recon_run_requested_by CHECK (requested_by_type IN ('SCHEDULER', 'WORKER', 'OPERATOR')),
    CONSTRAINT ck_recon_run_status CHECK (status IN ('QUEUED', 'RUNNING', 'COMPLETED', 'PARTIAL', 'FAILED'))
);

-- Bảng 5: reconciliation_items - Từng mục giao dịch cần đối soát
CREATE TABLE reconciliation_items (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES reconciliation_runs(id) ON DELETE CASCADE,
    payment_id UUID NULL REFERENCES payment_attempts(id) ON DELETE SET NULL,
    provider TEXT NOT NULL,
    provider_reference TEXT NOT NULL,
    local_status_before TEXT NOT NULL,
    provider_status TEXT NULL,
    result TEXT NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMPTZ NULL,
    lease_owner TEXT NULL,
    lease_expires_at TIMESTAMPTZ NULL,
    error_code TEXT NULL,
    operator_status TEXT NOT NULL DEFAULT 'UNASSIGNED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_recon_item_result CHECK (result IN ('PAID', 'FAILED', 'EXPIRED', 'UNKNOWN', 'SETTLED_LATE', 'ERROR')),
    CONSTRAINT ck_recon_item_operator_status CHECK (operator_status IN ('UNASSIGNED', 'OPEN', 'UNDER_REVIEW', 'RESOLVED'))
);

-- Bảng 6: late_payment_reconciliation - Bằng chứng tài chính quyết toán muộn
CREATE TABLE late_payment_reconciliation (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    payment_id UUID NOT NULL REFERENCES payment_attempts(id) ON DELETE RESTRICT,
    order_id UUID NOT NULL,
    checkout_attempt_id UUID NOT NULL,
    provider TEXT NOT NULL,
    provider_order_ref TEXT NOT NULL,
    provider_transaction_id TEXT NOT NULL,
    amount_minor BIGINT NOT NULL,
    currency TEXT NOT NULL DEFAULT 'VND',
    settlement_status TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'OPEN',
    reason_code TEXT NOT NULL,
    operator_id UUID NULL,
    resolution_note_safe TEXT NULL,
    resolved_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_late_recon_amount CHECK (amount_minor > 0),
    CONSTRAINT ck_late_recon_settlement CHECK (settlement_status = 'SETTLED_LATE'),
    CONSTRAINT ck_late_recon_status CHECK (status IN ('OPEN', 'UNDER_REVIEW', 'RECOVERED', 'REFUNDED', 'CLOSED'))
);
```

---

### `V3__idempotency_and_outbox.sql`
Thiết lập bảng lưu trữ tính bất biến (Idempotency) và bảng hàng đợi sự kiện Transactional Outbox.

```sql
-- V3__idempotency_and_outbox.sql

-- Bảng 7: idempotency_records - Kiểm soát trùng lặp yêu cầu ghi
CREATE TABLE idempotency_records (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    actor_scope TEXT NOT NULL,
    operation TEXT NOT NULL,
    idempotency_key TEXT NOT NULL,
    request_hash TEXT NOT NULL,
    response_status INTEGER NOT NULL,
    response_reference UUID NULL,
    response_body_redacted JSONB NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Bảng 8: outbox_messages - Hàng đợi sự kiện xuất bản Transactional Outbox
CREATE TABLE outbox_messages (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    aggregate_type TEXT NOT NULL DEFAULT 'Payment',
    aggregate_id UUID NOT NULL,
    event_type TEXT NOT NULL,
    event_version INTEGER NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    publish_status TEXT NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NULL,
    last_error_safe TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_outbox_publish_status CHECK (publish_status IN ('PENDING', 'PUBLISHED', 'DLQ'))
);
```

---

### `V4__payment_p1_allocations_and_refunds.sql`
Bổ sung bảng phân bổ thanh toán đa phương thức (Split Tender) và bảng hoàn tiền (Refunds) cho giai đoạn P1.

```sql
-- V4__payment_p1_allocations_and_refunds.sql

-- Bảng 9: payment_allocations - Phân bổ thanh toán đa phương thức (P1)
CREATE TABLE payment_allocations (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    order_id UUID NOT NULL,
    payment_id UUID NOT NULL REFERENCES payment_attempts(id) ON DELETE RESTRICT,
    amount_minor BIGINT NOT NULL,
    currency TEXT NOT NULL DEFAULT 'VND',
    status TEXT NOT NULL,
    idempotency_key TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_allocation_amount CHECK (amount_minor > 0),
    CONSTRAINT ck_allocation_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'CANCELLED'))
);

-- Bảng 10: refunds - Phiếu hoàn tiền (P1)
CREATE TABLE refunds (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    payment_id UUID NOT NULL REFERENCES payment_attempts(id) ON DELETE RESTRICT,
    amount_minor BIGINT NOT NULL,
    currency TEXT NOT NULL DEFAULT 'VND',
    reason TEXT NOT NULL,
    status TEXT NOT NULL,
    provider TEXT NULL,
    provider_refund_ref TEXT NULL,
    requested_by UUID NOT NULL,
    processed_at TIMESTAMPTZ NULL,
    idempotency_key TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_refund_amount CHECK (amount_minor > 0),
    CONSTRAINT ck_refund_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'REJECTED'))
);
```

---

### `V5__payment_indexes_and_constraints.sql`
Thiết lập các ràng buộc duy nhất (Unique Constraints) và chỉ mục (Indexes) tối ưu hóa truy vấn và bảo vệ toàn vẹn tài chính.

```sql
-- V5__payment_indexes_and_constraints.sql

-- 1. Ràng buộc duy nhất Idempotency
ALTER TABLE idempotency_records
    ADD CONSTRAINT uq_payment_idempotency UNIQUE (tenant_id, actor_scope, operation, idempotency_key);

-- 2. Ngăn trùng lặp mã giao dịch phía provider trong cùng một tenant
CREATE UNIQUE INDEX uq_provider_transaction 
    ON payment_attempts (tenant_id, provider, provider_transaction_id) 
    WHERE provider_transaction_id IS NOT NULL;

-- 3. Khử trùng lặp callback từ provider
ALTER TABLE provider_callbacks
    ADD CONSTRAINT uq_provider_callback_key UNIQUE (provider, provider_event_key);

-- 4. Bất biến duy nhất một VNPAY attempt đang PENDING cho mỗi checkout attempt
CREATE UNIQUE INDEX uq_active_vnpay_attempt
    ON payment_attempts (checkout_attempt_id)
    WHERE method = 'VNPAY_QR' AND status = 'PENDING';

-- 5. Chỉ mục truy vấn lịch sử theo chi nhánh và thời gian
CREATE INDEX idx_payment_scope_time ON payment_attempts (tenant_id, branch_id, created_at DESC);
CREATE INDEX idx_payment_order_lookup ON payment_attempts (tenant_id, order_id);

-- 6. Chỉ mục phục vụ quét đối soát các giao dịch pending quá hạn
CREATE INDEX idx_payment_pending_expiry ON payment_attempts (tenant_id, status, expires_at)
    WHERE status = 'PENDING';

-- 7. Chỉ mục cho worker thử lại đối soát
CREATE INDEX idx_payment_reconciliation_retry ON reconciliation_items (operator_status, next_retry_at)
    WHERE operator_status != 'RESOLVED';

-- 8. Chỉ mục tra cứu callback của provider theo thời gian
CREATE INDEX idx_callback_provider_time ON provider_callbacks (provider, received_at DESC);

-- 9. Chỉ mục phục vụ tiến trình polling xuất bản outbox
CREATE INDEX idx_outbox_publish ON outbox_messages (publish_status, next_attempt_at, created_at)
    WHERE publish_status = 'PENDING';

-- 10. Chỉ mục danh sách công việc của nhân viên vận hành đối soát
CREATE INDEX idx_reconciliation_operator ON late_payment_reconciliation (tenant_id, branch_id, status, updated_at DESC);
```

---

### `V6__payment_comments_and_security.sql`
Thêm ghi chú (COMMENT ON COLUMN) mô tả chi tiết từng cột nghiệp vụ và kích hoạt chính sách bảo vệ dữ liệu.

```sql
-- V6__payment_comments_and_security.sql

COMMENT ON TABLE payment_attempts IS 'Bảng lưu trữ các lượt thử thanh toán cho đơn hàng (Tiền mặt hoặc VNPAY QR).';
COMMENT ON COLUMN payment_attempts.amount_due_minor IS 'Số tiền cần thu bất biến từ snapshot của Order, tính theo đơn vị minor unit VND (đồng).';
COMMENT ON COLUMN payment_attempts.amount_received_minor IS 'Số tiền mặt khách đưa (chỉ dùng cho CASH), tính theo đơn vị minor VND.';
COMMENT ON COLUMN payment_attempts.cash_change_minor IS 'Số tiền thừa trả lại khách (amount_received_minor - amount_due_minor).';
COMMENT ON COLUMN payment_attempts.provider_order_ref IS 'Mã tham chiếu đơn hàng gửi sang cổng thanh toán VNPAY (vnp_TxnRef).';
COMMENT ON COLUMN payment_attempts.reconciliation_required IS 'Cờ đánh dấu giao dịch có sự sai lệch cần đối soát lại với phía provider.';

COMMENT ON TABLE late_payment_reconciliation IS 'Bằng chứng tài chính ghi nhận các khoản thanh toán thành công từ VNPAY đến sau khi attempt đã kết thúc.';
COMMENT ON COLUMN late_payment_reconciliation.settlement_status IS 'Giá trị cố định SETTLED_LATE; tuyệt đối không làm sống lại Order đã hủy.';

COMMENT ON TABLE outbox_messages IS 'Hàng đợi xuất bản sự kiện theo mẫu Transactional Outbox Pattern nhằm đảm bảo tính nhất quán.';
```

---

## 8. Ma Trận Kiểm Thử Hợp Đồng (Contract Test Matrix)

Các kịch bản dưới đây bắt buộc phải được kiểm thử tự động (Contract / Integration Testing) trước khi release:

| Mã kiểm thử | Kịch bản kiểm thử | Dữ liệu đầu vào | Kết quả hợp đồng kỳ vọng |
|:---:|---|---|---|
| `TC-PAY-01` | Tiền mặt đưa vừa đúng tiền | `amountDueMinor = 68000`, `cashReceivedMinor = 68000` | Trả về `200 OK`, `status = PAID`, `cashChangeMinor = 0`, phát đúng 1 sự kiện `PaymentPaid.v1`. |
| `TC-PAY-02` | Tiền mặt đưa thừa tiền | `amountDueMinor = 68000`, `cashReceivedMinor = 100000` | Trả về `200 OK`, `status = PAID`, `cashChangeMinor = 32000`, phát đúng 1 sự kiện `PaymentPaid.v1`. |
| `TC-PAY-03` | Tiền mặt đưa thiếu tiền | `amountDueMinor = 68000`, `cashReceivedMinor = 50000` | Trả về `422 invalid_cash_amount`, không lưu DB, không chuyển trạng thái, không phát sự kiện. |
| `TC-PAY-04` | Gửi lặp lại yêu cầu tiền mặt | Cùng `Idempotency-Key` và cùng hash request | Trả về kết quả `200 OK` đã lưu ban đầu, không tạo dòng mới trong DB, không phát lặp sự kiện. |
| `TC-PAY-05` | Khởi tạo VNPAY QR đồng thời | 2 request cùng `checkoutAttemptId` đến đồng thời | Chỉ duy nhất 1 attempt `PENDING` được tạo; request thứ hai nhận `409 payment_already_pending` kèm thông tin QR hiện tại. |
| `TC-PAY-06` | VNPAY IPN sai Checksum | `vnp_SecureHash` giả mạo hoặc sai secret key | Trả về `{"RspCode": "97", "Message": "Invalid Checksum"}`, lưu callback thất bại, không chuyển trạng thái. |
| `TC-PAY-07` | VNPAY IPN sai số tiền | `vnp_Amount = 5000000` trong khi đơn là `6800000` | Trả về `{"RspCode": "04", "Message": "Invalid amount"}`, không chuyển `PAID`, đánh dấu `reconciliation_required = true`. |
| `TC-PAY-08` | VNPAY IPN hợp lệ | Checksum đúng, `vnp_ResponseCode = "00"`, đúng số tiền | Trả về `{"RspCode": "00", "Message": "Confirm Success"}`, chuyển `PENDING -> PAID`, phát ra đúng 1 `PaymentPaid.v1`. |
| `TC-PAY-09` | VNPAY IPN gửi trùng lặp | Gửi lại IPN hợp lệ lần thứ hai sau khi đã `PAID` | Trả về `{"RspCode": "02", "Message": "Order already confirmed"}`, xử lý No-op, không phát lặp sự kiện. |
| `TC-PAY-10` | IPN đến sau khi đã hết hạn | IPN báo thành công nhưng attempt đã chuyển `EXPIRED` | Trả về `RspCode: "00"`, ghi nhận `late_payment_reconciliation(SETTLED_LATE)`, phát `PaymentReconciliationRequired.v1`. Tuyệt đối không chuyển sang `PAID`. |
| `TC-PAY-11` | Cổng VNPAY bị timeout khi gọi | Worker đối soát tra cứu provider gặp lỗi mạng | Giữ nguyên `PENDING`, lùi thời gian thử lại số mũ `next_retry_at`, không tự ý báo thất bại giả mạo. |
| `TC-PAY-12` | Yêu cầu hoàn tiền vượt số dư (P1) | `amountMinor = 100000` trong khi đơn chỉ thanh toán `68000` | Trả về mã lỗi `422 refund_amount_exceeded`, từ chối yêu cầu hoàn tiền. |
| `TC-PAY-13` | Đọc dữ liệu chéo tenant | Truy vấn `paymentId` của tenant khác | Trả về mã lỗi `404 Not Found` (không làm lộ sự tồn tại của dữ liệu chéo tenant). |

---

## 9. Tài Liệu Tham Khảo Kỹ Thuật

- [Tài liệu Tích hợp VNPAY Sandbox PAY](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html): Đặc tả chi tiết URL cổng thanh toán, danh sách tham số `vnp_*`, thuật toán tính chuỗi băm HMAC-SHA512 checksum và định dạng phản hồi IPN.
- [Tài liệu API Tra Cứu và Hoàn Tiền VNPAY](https://sandbox.vnpayment.vn/apis/docs/truy-van-hoan-tien/querydr%26refund.html): Đặc tả API `querydr` phục vụ đối soát giao dịch và API `refund` cho hoàn tiền.
