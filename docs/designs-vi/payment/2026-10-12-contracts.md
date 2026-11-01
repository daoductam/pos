# Module 4 — Payment Service: Hợp đồng Dữ liệu & API (API & Data Contracts)

**Ngày:** 2026-10-12  
**Trạng thái:** Đã phê duyệt (Approved)  
**Phạm vi:** M — Quy trình Service Chuẩn (Standard Service Flow)  
**Module:** Thanh toán (Payment Service)

## 1. Các Quy ước Hợp đồng (Contract Conventions)

### 1.1 Tiếp cận API-first

- Các hợp đồng REST có phiên bản thông qua tiền tố `/api/v1`, `/internal/v1` và `/callbacks/v1`.
- Tài liệu OpenAPI là nguồn chân lý duy nhất để sinh client cho máy POS và các consumer nội bộ.
- Tài liệu AsyncAPI và schema sự kiện là nguồn sinh hợp đồng cho các bên tiêu thụ sự kiện (event consumers).
- Các trường dữ liệu đặc thù của VNPAY chỉ xuất hiện tại ranh giới của callback / bộ điều hợp provider adapter, tuyệt đối không xuất hiện trong các lệnh domain của Payment.
- Các thay đổi phá vỡ tính tương thích (breaking changes) bắt buộc phải dùng phiên bản lớn (major version) mới; các trường tùy chọn mới bổ sung phải đảm bảo tương thích ngược.

### 1.2 Các Header HTTP

| Header | Bắt buộc | Phạm vi (Scope) | Ý nghĩa |
|---|---:|---|---|
| `Authorization` | API người dùng | Cognito | `Bearer <Cognito access token>` |
| `Authorization` | API nội bộ | Workload identity | Thông tin xác thực service / mTLS hoặc ngữ cảnh workload có chữ ký |
| `X-Active-Branch-Id` | API người dùng theo chi nhánh | Tư cách thành viên | Chi nhánh được chọn; máy chủ bắt buộc phải xác thực tư cách thành viên |
| `X-Correlation-Id` | Khuyến nghị | Tất cả | Tương quan API, Saga, audit, outbox và các lệnh gọi hạ nguồn |
| `X-Causation-Id` | Có điều kiện | Callback nội bộ / sự kiện | Tham chiếu lệnh/sự kiện trực tiếp gây ra thao tác hiện tại |
| `Idempotency-Key` | Lệnh có tác dụng phụ | Tenant + actor/workload + thao tác | Khóa thử lại; hash yêu cầu chuẩn hóa bắt buộc phải giữ nguyên |
| `Content-Type` | API có body | REST | `application/json; charset=utf-8` |

Client tuyệt đối không được phép tự đưa vai trò (role), tác nhân (actor), quyền sở hữu tenant hay quyền sở hữu chi nhánh vào trong request body để thay thế ngữ cảnh xác thực.

### 1.3 Cấu trúc Bao bọc Thành công (Success Envelope)

```json
{
  "data": {},
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

### 1.4 Cấu trúc Bao bọc Lỗi (Error Envelope)

```json
{
  "error": {
    "code": "provider_amount_mismatch",
    "message": "Số tiền từ nhà cung cấp không khớp với lượt thử thanh toán",
    "details": [
      {
        "field": "amountMinor",
        "code": "amount_mismatch",
        "message": "Số tiền từ nhà cung cấp không được chấp nhận"
      }
    ],
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

Các quy tắc:

- Thông báo `message` tuyệt đối không chứa câu lệnh SQL, JWT, bí mật nhà cung cấp, chữ ký thô, raw callback hay PII không cần thiết.
- Tài nguyên chéo tenant/chi nhánh không tồn tại trong phạm vi được ủy quyền sẽ trả về mã `404`, không làm lộ sự tồn tại của tài nguyên.
- Chi tiết xác thực (`details`) chỉ trả về tên trường và mã lỗi an toàn.

### 1.5 Bảng Mã Lỗi và Mã Trạng thái HTTP

| HTTP | Mã lỗi | Ý nghĩa | Có thể thử lại (Retryable) |
|---:|---|---|---:|
| `200` | — | Đọc thành công hoặc lệnh thanh toán tiền mặt đã hoàn tất | Không |
| `202` | `accepted` | Khởi tạo VNPAY hoặc lệnh đối soát đã đưa vào hàng đợi, chưa kết thúc | Có (truy vấn trạng thái) |
| `400` | `validation_error` | Yêu cầu sai định dạng, sai schema hoặc chứa trường không hỗ trợ | Không |
| `401` | `unauthorized` | Thiếu, không hợp lệ hoặc hết hạn thông tin xác thực | Sau khi làm mới token |
| `403` | `forbidden` | Vai trò, phạm vi tenant hoặc chi nhánh không đủ quyền | Không |
| `404` | `not_found` | Tài nguyên không tồn tại trong phạm vi được ủy quyền | Không |
| `409` | `idempotency_conflict` | Cùng idempotency key nhưng hash yêu cầu bị thay đổi | Không |
| `409` | `payment_already_pending` | Đã tồn tại một lượt thử VNPAY đang pending còn hoạt động | Không (trả về payment hiện tại) |
| `409` | `payment_already_terminal` | Attempt đã ở trạng thái kết thúc và lệnh gửi đến bị mâu thuẫn | Không |
| `409` | `payment_reconciliation_required` | Tác dụng phụ cần nhân viên vận hành đối soát thủ công | Không tự động thử lại |
| `409` | `split_tender_exceeds_order_total` | Phân bổ tiền ở P1 vượt quá tổng số tiền phải trả | Không |
| `422` | `invalid_cash_amount` | Số tiền mặt khách đưa nhỏ hơn số tiền phải trả | Không |
| `422` | `provider_mapping_mismatch` | Ánh xạ giữa provider, đơn hàng hoặc payment bị sai lệch | Không |
| `422` | `provider_amount_mismatch` | Số tiền từ provider không khớp với hệ thống | Không |
| `422` | `invalid_provider_signature` | Checksum hoặc chữ ký điện tử của provider không hợp lệ | Theo chính sách provider |
| `429` | `rate_limited` | Vượt quá giới hạn tốc độ tại Gateway, service hoặc provider | Thử lại có lùi thời gian |
| `503` | `provider_unavailable` | Provider không khả dụng hoặc timeout, chưa thể kết luận thất bại | Có |
| `503` | `service_unavailable` | Phụ thuộc nội bộ của Payment tạm thời không khả dụng | Có |

### 1.6 Tính Bất Biến (Idempotency)

- Bắt buộc phải có header `Idempotency-Key` cho các lệnh khởi tạo thanh toán, lệnh hủy/hết hạn, lệnh chạy đối soát và hoàn tiền ở P1.
- Phạm vi khóa: `(tenantId, actorOrWorkloadId, operation)`. Đối với Saga, khóa tác vụ chứa `(tenantId, orderId, checkoutAttemptId, taskName)`.
- Lưu trữ hash yêu cầu chuẩn hóa, phản hồi/trạng thái gốc và thời gian hết hạn.
- Cùng khóa + cùng hash: Trả lại kết quả gốc đã lưu trước đó.
- Cùng khóa + khác hash: Trả về mã lỗi `409 idempotency_conflict`.
- Ràng buộc duy nhất tại cơ sở dữ liệu và khóa giao dịch là lớp bảo vệ cuối cùng; không phụ thuộc vào bộ nhớ đệm ram.

### 1.7 Phân Trang và Bộ Lọc (Pagination/Filtering)

Danh sách lịch sử và đối soát ở P1 sử dụng phân trang theo con trỏ (cursor pagination):

```text
GET /api/v1/payments?limit=20&cursor=<opaque>&status=PAID&method=VNPAY_QR
```

- Tham số `limit` mặc định là `20`, tối đa là `100`.
- Sắp xếp ổn định theo `(createdAt DESC, id DESC)`.
- Chuỗi con trỏ (cursor) là dạng mờ (opaque), client không được tự ý sửa.
- Các mệnh đề phạm vi và bộ lọc trong danh sách trắng được áp dụng trước khi phân trang.

Phản hồi:

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

Tuyệt đối không lưu cache trạng thái hoặc phản hồi đọc của P0. Mô hình đọc và báo cáo ở P1 chỉ lưu cache khi có timestamp độ tươi mới và chính sách vô hiệu hóa rõ ràng.

## 2. Danh Mục Các Endpoint

| Phương thức | Đường dẫn | Phân quyền / Phạm vi | Yêu cầu | Phản hồi | Mã trạng thái |
|---|---|---|---|---|---|
| `POST` | `/internal/v1/orders/{orderId}/payments` | Workload identity của Order/Saga | `InitiatePaymentRequest` | `PaymentResponse` | `200` tiền mặt, `202` VNPAY, `409`, `422`, `503` |
| `GET` | `/api/v1/payments/{paymentId}` | Cognito + phạm vi tenant/chi nhánh | — | `PaymentResponse` | `200`, `403`, `404` |
| `GET` | `/api/v1/payments` | Manager/Owner + phạm vi | query cursor/bộ lọc | `PaymentPage` | `200`, `403`, `422` |
| `GET` | `/callbacks/v1/vnpay/ipn` | Ranh giới API Gateway / Provider | Query parameters từ VNPAY | `VnpayIpnResponse` | `200` tương thích provider |
| `POST` | `/internal/v1/payments/reconciliation/run` | Workload identity của Scheduler / Vận hành | `ReconciliationRunRequest` | `ReconciliationRunResponse` | `202`, `409`, `503` |
| `GET` | `/api/v1/reconciliations/{reconciliationId}` | Manager/Owner/Vận hành | — | `ReconciliationResponse` | `200`, `403`, `404` |
| `POST` | `/api/v1/payments/{paymentId}/refunds` | Manager/Owner + phạm vi | `CreateRefundRequest` | `RefundResponse` | `201/202`, `403`, `409`, `422` (P1) |
| `GET` | `/api/v1/refunds/{refundId}` | Manager/Owner + phạm vi | — | `RefundResponse` | `200`, `403`, `404` (P1) |

### 2.1 Khởi tạo Thanh toán (Initiate Payment)

`POST /internal/v1/orders/{orderId}/payments`

Các trường đáng tin cậy `tenantId`, `branchId`, `actor/workload` và quyền sở hữu đơn hàng bắt buộc phải đến từ xác thực service hoặc ngữ cảnh có chữ ký. Các trường trong request body không phải là nguồn chân lý độc lập.

Yêu cầu (Request):

```json
{
  "sagaId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
  "checkoutAttemptId": "0192b2d0-7f1d-7abc-8d2c-123456789013",
  "paymentMethod": "CASH",
  "amountDueMinor": 125000,
  "currency": "VND",
  "amountReceivedMinor": 150000,
  "orderSnapshotVersion": 7,
  "providerOrderRef": null,
  "returnUrl": null,
  "correlationId": "0192b2d0-7f1d-7abc-8d2c-123456789014"
}
```

Quy tắc các trường:

| Trường | Bắt buộc | Quy tắc |
|---|---:|---|
| `sagaId` | có | Tham chiếu phiên thực thi của AWS Step Functions |
| `checkoutAttemptId` | có | Tham chiếu attempt bất biến do Order sở hữu |
| `paymentMethod` | có | `CASH` hoặc `VNPAY_QR` trong P0 |
| `amountDueMinor` | có | Số nguyên không âm, phải bằng đúng snapshot Order đáng tin cậy |
| `currency` | có | `VND` trong P0 |
| `amountReceivedMinor` | Chỉ cho CASH | Số nguyên ≥ `amountDueMinor`; vắng mặt đối với VNPAY |
| `orderSnapshotVersion` | có | Phiên bản snapshot được Order/Saga sử dụng |
| `providerOrderRef` | Chỉ cho VNPAY | Mã tham chiếu merchant ổn định, do Payment sinh/xác thực |
| `returnUrl` | Chỉ cho VNPAY | URL trong danh sách trắng; không dùng làm tín hiệu xác thực |
| `correlationId` | có | Ngữ cảnh truy vết lan truyền |

Header `Idempotency-Key` được truyền trên HTTP header, không lặp lại trong body.

Phản hồi thành công cho Tiền mặt (`200`):

```json
{
  "data": {
    "paymentId": "0192b2d0-7f1d-7abc-8d2c-123456789015",
    "orderId": "0192b2d0-7f1d-7abc-8d2c-123456789016",
    "checkoutAttemptId": "0192b2d0-7f1d-7abc-8d2c-123456789013",
    "sagaId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
    "paymentMethod": "CASH",
    "paymentStatus": "PAID",
    "amountDueMinor": 125000,
    "amountReceivedMinor": 150000,
    "cashChangeMinor": 25000,
    "currency": "VND",
    "provider": null,
    "providerOrderRef": null,
    "providerTransactionId": null,
    "qrPayload": null,
    "expiresAt": null,
    "paidAt": "2026-10-12T07:00:00Z",
    "reconciliationRequired": false,
    "lateSettlementStatus": null
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

Phản hồi khởi tạo cho VNPAY QR (`202`):

```json
{
  "data": {
    "paymentId": "0192b2d0-7f1d-7abc-8d2c-123456789017",
    "orderId": "0192b2d0-7f1d-7abc-8d2c-123456789016",
    "checkoutAttemptId": "0192b2d0-7f1d-7abc-8d2c-123456789013",
    "sagaId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
    "paymentMethod": "VNPAY_QR",
    "paymentStatus": "PENDING",
    "amountDueMinor": 125000,
    "currency": "VND",
    "provider": "VNPAY",
    "providerOrderRef": "POS-20261012-000123-A1",
    "providerTransactionId": null,
    "qrPayload": "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?...",
    "expiresAt": "2026-10-12T07:15:00Z",
    "paidAt": null,
    "reconciliationRequired": false,
    "lateSettlementStatus": null
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

### 2.2 Đọc Chi tiết Thanh toán (Read Payment)

`GET /api/v1/payments/{paymentId}`

- Bắt buộc phải có Cognito JWT và kiểm tra phạm vi tenant/chi nhánh phía máy chủ.
- Trả về trạng thái hiện tại, các số tiền bất biến, mã tham chiếu provider, thời gian hết hạn, cờ đối soát và tóm tắt chuyển trạng thái an toàn.
- Tuyệt đối không trả về raw callback, secret, chuỗi checksum thô hoặc stack trace nội bộ.
- Payment đã ở trạng thái kết thúc `EXPIRED/CANCELLED` có thể mang `lateSettlementStatus=SETTLED_LATE` nếu bản ghi đối soát tồn tại; không thay đổi `paymentStatus`.

### 2.3 Lịch sử Thanh toán (Payment History)

`GET /api/v1/payments`

Các bộ lọc nằm trong danh sách trắng:

- `cursor`, `limit`;
- `from`, `to` theo múi giờ nghiệp vụ của chi nhánh;
- `branchId` nếu vai trò Owner hoặc phạm vi được phân công cho phép;
- `orderId`, `paymentId`, `providerTransactionId`;
- `paymentMethod`, `paymentStatus`, `cashierId`;
- `reconciliationRequired`, `refundStatus`.

Quản lý (Manager) chỉ được đọc trong phạm vi chi nhánh. Chủ sở hữu (Owner) có thể đọc toàn tenant và bắt buộc phải chọn chi nhánh khi endpoint/chính sách yêu cầu góc nhìn chi nhánh cụ thể.

### 2.4 Webhook VNPAY IPN Callback

`GET /callbacks/v1/vnpay/ipn`

Hợp đồng bên ngoài của VNPAY sử dụng query string dạng `vnp_*`; API Gateway bắt buộc phải bảo toàn query thô / dữ liệu chuẩn hóa để adapter xác thực checksum. Callback domain đã chuẩn hóa tuyệt đối không để lộ tên trường của provider.

Các trường của provider:

| Trường của Provider | Bắt buộc | Ý nghĩa đã chuẩn hóa |
|---|---:|---|
| `vnp_TmnCode` | có | Mã định danh terminal của merchant |
| `vnp_Amount` | có | Số tiền provider gửi (nhân 100); adapter chuẩn hóa về minor unit VND |
| `vnp_BankCode` | có | Mã ngân hàng / ví thanh toán |
| `vnp_BankTranNo` | không | Mã giao dịch phía ngân hàng |
| `vnp_CardType` | không | Loại thanh toán; có thể bao gồm QR |
| `vnp_PayDate` | không | Thời gian thanh toán phía provider, định dạng GMT+7 |
| `vnp_OrderInfo` | có | Mô tả thông tin đơn hàng |
| `vnp_TransactionNo` | có | Mã giao dịch phía VNPAY |
| `vnp_ResponseCode` | có | Mã phản hồi kết quả giao dịch của VNPAY |
| `vnp_TransactionStatus` | có | Trạng thái giao dịch của VNPAY |
| `vnp_TxnRef` | có | Mã tham chiếu đơn hàng / payment của merchant |
| `vnp_SecureHashType` | không | Chỉ báo thuật toán băm cấu hình |
| `vnp_SecureHash` | có | Chuỗi checksum kiểm tra toàn vẹn |

Quy trình xử lý IPN:

1. Xác thực định dạng / kích thước query và thu thập metadata thô.
2. Xác thực `vnp_SecureHash` trước khi thực hiện bất kỳ thao tác DB hay nghiệp vụ nào.
3. Phân giải `vnp_TxnRef` ra payment attempt tương ứng và xác thực ánh xạ tenant/chi nhánh/provider.
4. Chuyển đổi `vnp_Amount / 100` theo hợp đồng provider và so sánh chính xác với số tiền minor unit VND.
5. Kiểm tra `vnp_ResponseCode` và `vnp_TransactionStatus`.
6. Khử trùng lặp dựa trên khóa sự kiện provider / mã giao dịch provider và hash payload.
7. Chỉ áp dụng các bước chuyển trạng thái được phép; xung đột với trạng thái kết thúc sẽ chuyển thành bản ghi đối soát / no-op.
8. Trả về phản hồi JSON tương thích với nhà cung cấp VNPAY.

Phản hồi:

```json
{
  "RspCode": "00",
  "Message": "Confirm Success"
}
```

Hợp đồng VNPAY sử dụng `RspCode=00`/`02` cho cập nhật được chấp nhận và các mã lỗi khác cho trường hợp không hợp lệ / cần thử lại. Việc ánh xạ chi tiết nằm hoàn toàn trong VNPAY adapter; lỗi nội bộ hệ thống không bao giờ được đưa vào trường `Message`.

### 2.5 Lượt Chạy Đối soát (Reconciliation Run)

`POST /internal/v1/payments/reconciliation/run`

Yêu cầu (Request):

```json
{
  "paymentIds": [],
  "providerTransactionIds": [],
  "reason": "PENDING_TIMEOUT_SCAN",
  "maxItems": 100,
  "correlationId": "0192b2d0-7f1d-7abc-8d2c-123456789020"
}
```

- Mảng mục tiêu rỗng có nghĩa là bộ lập lịch tự động chọn các mục đủ điều kiện trong phạm vi workload.
- `maxItems` tối đa là `1000`.
- Phản hồi mã `202` có nghĩa là lượt chạy đã được chấp nhận và đưa vào hàng đợi, không đồng nghĩa với việc trạng thái payment đã được giải quyết xong.
- Mã lỗi `409` chỉ dành cho lệnh bị trùng lặp / xung đột hoặc đang có một lượt chạy đang hoạt động, không dùng cho trường hợp provider trả về trạng thái chưa rõ.

Phản hồi:

```json
{
  "data": {
    "reconciliationRunId": "0192b2d0-7f1d-7abc-8d2c-123456789021",
    "status": "QUEUED",
    "acceptedCount": 12
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

### 2.6 Hoàn tiền P1 (Refund)

`POST /api/v1/payments/{paymentId}/refunds`

Yêu cầu (Request):

```json
{
  "amountMinor": 50000,
  "reason": "CUSTOMER_REQUEST",
  "correlationId": "0192b2d0-7f1d-7abc-8d2c-123456789022"
}
```

- Yêu cầu phạm vi Manager/Owner và header `Idempotency-Key`.
- Payment bắt buộc phải ở trạng thái `PAID`.
- `amountMinor` phải là số dương và không vượt quá số dư có thể hoàn lại.
- Bản ghi hoàn tiền độc lập với payment attempt gốc ban đầu.
- Yêu cầu hoàn tiền sang VNPAY được gửi bởi adapter dưới dạng POST đặc thù của provider; phản hồi từ provider được chuẩn hóa về trạng thái hoàn tiền nội bộ.

## 3. Hợp đồng Trạng thái và Lệnh (State & Command Contracts)

### 3.1 Trạng thái Payment Attempt

```text
PENDING
 ├─ cashValidated              → PAID
 ├─ verifiedProviderSuccess    → PAID
 ├─ providerDefinitiveFailure  → FAILED
 ├─ providerExpiry/localClose  → EXPIRED
 └─ authorizedCancel           → CANCELLED
```

Các quy tắc:

- `PAID`, `FAILED`, `EXPIRED`, `CANCELLED` là các trạng thái kết thúc (terminal).
- Trùng lặp cùng trạng thái là thao tác No-op trả lại kết quả trước đó.
- Callback xung đột đến sau khi attempt đã kết thúc không được ghi đè trạng thái.
- Provider báo thành công hợp lệ sau khi attempt đã kết thúc sẽ tạo bản ghi `late_payment_reconciliation(SETTLED_LATE)` và chỉ phát duy nhất sự kiện `PaymentReconciliationRequired`.
- Trạng thái provider chưa xác định sẽ giữ nguyên `PENDING` kèm cờ đối soát cho đến khi có kết luận rõ ràng hoặc nhân viên vận hành can thiệp xử lý.

### 3.2 Cổng Nhà Cung cấp Chuẩn Hóa (Normalized Provider Port)

```text
createDynamicQr(CreateQrCommand) -> ProviderQrCreated
verifyCallback(RawProviderCallback) -> VerifiedProviderCallback | InvalidCallback
queryStatus(QueryProviderStatusCommand) -> ProviderStatusResult
refund(RefundProviderCommand) -> ProviderRefundResult       # P1
```

Kết quả callback đã chuẩn hóa chứa các trường:

| Trường | Quy tắc |
|---|---|
| `provider` | `VNPAY` |
| `providerOrderRef` | Ánh xạ tới duy nhất một payment attempt |
| `providerTransactionId` | Duy nhất trong phạm vi provider/tenant khi tồn tại |
| `amountMinor` | Số nguyên minor unit VND chính xác sau khi adapter đã chuẩn hóa |
| `providerStatus` | Trạng thái trung lập: `SUCCESS`, `FAILED`, `EXPIRED`, `UNKNOWN` |
| `signatureValid` | Bắt buộc phải là `true` trước khi xử lý domain |
| `providerOccurredAt` | Dấu thời gian gốc từ phía provider nếu có |
| `payloadHash` | Chuỗi băm digest của raw payload chuẩn hóa |
| `providerEventKey` | Khóa khử trùng lặp ổn định |

## 4. Hợp đồng Sự kiện (Event Contracts)

### 4.1 Cấu trúc Bao bọc Sự kiện (Event Envelope)

Các trường truyền tải chuẩn sử dụng `eventType` + `eventVersion`; ký hiệu tài liệu viết tắt `PaymentPaid.v1` có nghĩa là `eventType=PaymentPaid`, `eventVersion=1`.

```json
{
  "eventId": "0192b2d0-7f1d-7abc-8d2c-123456789023",
  "eventType": "PaymentPaid",
  "eventVersion": 1,
  "occurredAt": "2026-10-12T07:00:00Z",
  "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789024",
  "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789025",
  "aggregateType": "Payment",
  "aggregateId": "0192b2d0-7f1d-7abc-8d2c-123456789015",
  "correlationId": "0192b2d0-7f1d-7abc-8d2c-123456789014",
  "causationId": "0192b2d0-7f1d-7abc-8d2c-123456789026",
  "producer": "payment-service",
  "payload": {}
}
```

Các quy tắc bao bọc:

- `eventId` là duy nhất toàn cầu; đóng vai trò là khóa khử trùng lặp cho consumer.
- `eventType`/`eventVersion` là bất biến sau khi xuất bản.
- `tenantId` là bắt buộc; `branchId` là bắt buộc đối với payment có phạm vi chi nhánh.
- Consumer chỉ xác nhận hoàn tất (ack) sau khi giao dịch cục bộ đã commit thành công.
- Phiên bản sự kiện không xác định sẽ được cách ly vào hàng đợi DLQ, không tự diễn giải thành phiên bản cũ hơn.
- Phân phát sự kiện tuân theo nguyên tắc ít nhất một lần (at-least-once).

### 4.2 Danh Mục Các Sự Kiện của Payment

| Sự kiện | Bên phát → Bên nhận | Quy tắc payload |
|---|---|---|
| `PaymentInitiated.v1` | Payment → Saga, Audit | `paymentId`, `orderId`, `checkoutAttemptId`, `method`, `amountMinor`, `currency`, `expiresAt` có thể null đối với CASH |
| `PaymentPaid.v1` | Payment → Saga, Audit | `paymentId`, `orderId`, `method`, `amountMinor`, `currency`, `paidAt`; các trường provider có thể null cho CASH; tiền nhận/tiền thừa là tùy chọn. Saga sau đó gọi lệnh commit tồn kho. |
| `PaymentFailed.v1` | Payment → Saga, Order, Audit | `paymentId`, `orderId`, `reasonCode`, `retryable`, `failedAt` |
| `PaymentExpired.v1` | Payment → Saga, Order, Audit | `paymentId`, `orderId`, `expiredAt`, `reasonCode` |
| `PaymentCancelled.v1` | Payment → Saga, Order, Audit | `paymentId`, `orderId`, `cancelledAt`, `reasonCode`, loại/ID tác nhân |
| `PaymentReconciliationRequired.v1` | Payment → Saga, Order, Audit, Vận hành | `paymentId`, `orderId`, `reconciliationId`, `reasonCode`, `settlementStatus`, `amountMinor`, `occurredAt` |

Sự kiện `PaymentReconciliationRequired.v1` mang `settlementStatus=SETTLED_LATE` không phải là sự thật đơn hàng đã thanh toán. Nó tuyệt đối không được kích hoạt commit tồn kho, tích điểm Loyalty, ghi nhận doanh thu Reporting hay phát ra `OrderPaid.v1`.

Sự kiện `OrderPaid.v1` vẫn là sự kiện do Order Service sở hữu và chỉ được phát ra sau khi thanh toán thành công, commit tồn kho hoàn tất và Order đã hoàn tất chính thức.

### 4.3 Định Tuyến và Thử Lại (Routing/Retry)

| Tuyến sự kiện | Consumer | Cơ chế Thử lại / DLQ |
|---|---|---|
| `PaymentPaid`, `PaymentFailed`, `PaymentExpired`, `PaymentCancelled` | Callback của Saga/Order | Thử lại có giới hạn, sau đó đưa vào DLQ `pos-saga-events` |
| Toàn bộ sự kiện của Payment | Audit Service | Thử lại bền bỉ / DLQ |
| `PaymentReconciliationRequired` | Bộ phận Vận hành / Đối soát | Phát cảnh báo + đưa vào hàng đợi thủ công; phát lại đòi hỏi ủy quyền |

## 5. Hợp đồng Dữ liệu (Data Contracts)

### 5.1 Quy ước Cơ sở Dữ liệu Chung

- Sử dụng các kiểu dữ liệu PostgreSQL: `UUID`, `TEXT`, `BIGINT`, `INTEGER`, `BOOLEAN`, `JSONB`, `TIMESTAMPTZ`.
- Toàn bộ UUID đều là định danh UUID v7.
- Số tiền là số nguyên `BIGINT` đơn vị minor unit (đồng); VNPAY adapter chia tỷ lệ 100 trước khi lưu DB.
- Toàn bộ dấu thời gian đều là chuẩn UTC `TIMESTAMPTZ`.
- Ngày nghiệp vụ được tính toán dựa trên múi giờ IANA của chi nhánh; múi giờ trình duyệt không bao giờ là nguồn thẩm quyền.
- Mọi file migration bắt buộc phải có `COMMENT ON COLUMN` mô tả rõ ràng mục đích của từng cột mới.
- Dữ liệu raw payload nhạy cảm được mã hóa/thu giảm; bí mật và chuỗi ký thô tuyệt đối không được ghi log.

### 5.2 Bảng `payment_attempts`

| Cột | Kiểu | Ràng buộc | Mô tả / Chú thích |
|---|---|---|---|
| `id` | UUID | Khóa chính (PK) | Định danh nội bộ của payment attempt. |
| `tenant_id` | UUID | NOT NULL, indexed | Phạm vi tenant sở hữu. |
| `branch_id` | UUID | NOT NULL, indexed | Chi nhánh nơi Order/payment được tạo. |
| `order_id` | UUID | NOT NULL, indexed | Tham chiếu Order mờ; không tạo FK chéo service. |
| `checkout_attempt_id` | UUID | NOT NULL, indexed | Tham chiếu attempt checkout bất biến của Order. |
| `saga_id` | UUID | NOT NULL, indexed | Tham chiếu phiên thực thi Step Functions/Saga. |
| `method` | TEXT | CHECK `CASH`/`VNPAY_QR` | Phương thức thanh toán. |
| `status` | TEXT | CHECK state enum | `PENDING`, `PAID`, `FAILED`, `EXPIRED`, `CANCELLED`. |
| `amount_due_minor` | BIGINT | NOT NULL, CHECK >= 0 | Số tiền kỳ vọng bất biến từ snapshot Order đáng tin cậy. |
| `amount_received_minor` | BIGINT | NULL, CHECK >= 0 | Số tiền mặt khách đưa; null đối với phương thức khác tiền mặt. |
| `cash_change_minor` | BIGINT | NULL, CHECK >= 0 | Tiền thừa do hệ thống tính; null đối với phương thức khác tiền mặt. |
| `currency` | TEXT | NOT NULL, CHECK `VND` P0 | Đơn vị tiền tệ của thanh toán. |
| `provider` | TEXT | NULL | Tên nhà cung cấp bên ngoài; null đối với CASH. |
| `provider_order_ref` | TEXT | NULL | Mã tham chiếu merchant gửi sang nhà cung cấp. |
| `provider_transaction_id` | TEXT | NULL | Mã tham chiếu quyết toán của nhà cung cấp; duy nhất theo tenant/provider khi tồn tại. |
| `qr_payload` | TEXT | NULL, bảo vệ khi lưu | Chuỗi payload dynamic QR/chuyển hướng trả về máy POS; không phải secret. |
| `expires_at` | TIMESTAMPTZ | NULL | Thời gian hết hạn pending; null đối với CASH đồng bộ. |
| `paid_at` | TIMESTAMPTZ | NULL | Thời điểm attempt chuyển trạng thái sang `PAID`. |
| `failure_code` | TEXT | NULL | Mã lỗi thất bại đã chuẩn hóa an toàn. |
| `failure_reason_safe` | TEXT | NULL | Lý do thất bại an toàn cho nhân viên vận hành, đã làm sạch. |
| `reconciliation_required` | BOOLEAN | NOT NULL DEFAULT false | Đánh dấu sự sai lệch chưa được giải quyết giữa provider và hệ thống nội bộ. |
| `created_by_type` | TEXT | NOT NULL | Loại tác nhân: Người dùng, Saga, Scheduler hoặc Provider. |
| `created_by_id` | UUID | NULL | Mã định danh tác nhân/workload khi khả dụng. |
| `created_at` | TIMESTAMPTZ | NOT NULL | Thời điểm tạo bản ghi theo chuẩn UTC. |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Thời điểm cập nhật tập hợp gần nhất theo chuẩn UTC. |

### 5.3 Bảng `payment_state_transitions`

| Cột | Kiểu | Ràng buộc | Mô tả / Chú thích |
|---|---|---|---|
| `id` | UUID | Khóa chính (PK) | Định danh bản ghi chuyển trạng thái (chỉ nối thêm). |
| `tenant_id` | UUID | NOT NULL, indexed | Phạm vi tenant. |
| `branch_id` | UUID | NOT NULL, indexed | Phạm vi chi nhánh. |
| `payment_id` | UUID | NOT NULL, indexed | Tham chiếu payment attempt cục bộ. |
| `from_status` | TEXT | NULL | Trạng thái trước; null khi mới tạo bản ghi đầu tiên. |
| `to_status` | TEXT | NOT NULL | Trạng thái kết quả sau khi chuyển đổi. |
| `reason_code` | TEXT | NOT NULL | Mã lý do chuyển đổi trạng thái đã chuẩn hóa. |
| `actor_type` | TEXT | NOT NULL | Loại tác nhân: Người dùng, Service, Provider hoặc Worker. |
| `actor_id` | UUID | NULL | Mã định danh tác nhân / workload. |
| `correlation_id` | UUID | NOT NULL | Mã tương quan truy vết phân tán. |
| `causation_id` | UUID | NULL | Lệnh hoặc sự kiện trực tiếp gây ra bước chuyển đổi. |
| `occurred_at` | TIMESTAMPTZ | NOT NULL | Thời điểm nghiệp vụ xảy ra chuyển trạng thái (UTC). |
| `created_at` | TIMESTAMPTZ | NOT NULL | Thời điểm lưu bản ghi vào DB (UTC). |

### 5.4 Bảng `provider_callbacks`

| Cột | Kiểu | Ràng buộc | Mô tả / Chú thích |
|---|---|---|---|
| `id` | UUID | Khóa chính (PK) | Định danh bản ghi callback từ nhà cung cấp. |
| `tenant_id` | UUID | NULL cho đến khi ánh xạ | Tenant được phân giải từ ánh xạ payment đáng tin cậy. |
| `branch_id` | UUID | NULL cho đến khi ánh xạ | Chi nhánh được phân giải từ ánh xạ payment đáng tin cậy. |
| `payment_id` | UUID | NULL cho đến khi ánh xạ | Payment attempt cục bộ khớp được; null nếu tham chiếu không xác định. |
| `provider` | TEXT | NOT NULL | Tên nhà cung cấp, trong P0 là `VNPAY`. |
| `provider_event_key` | TEXT | NOT NULL | Khóa sự kiện ổn định dùng để khử trùng lặp callback. |
| `provider_transaction_id` | TEXT | NULL | Mã định danh giao dịch phía nhà cung cấp. |
| `payload_hash` | TEXT | NOT NULL | Chuỗi băm digest của raw callback payload chuẩn hóa. |
| `signature_valid` | BOOLEAN | NOT NULL | Kết quả xác thực chữ ký điện tử / checksum. |
| `provider_response_code` | TEXT | NULL | Mã phản hồi thô từ provider, bản sao chuẩn hóa an toàn. |
| `provider_transaction_status` | TEXT | NULL | Trạng thái giao dịch thô từ provider, bản sao chuẩn hóa an toàn. |
| `raw_payload_encrypted` | JSONB | NULL | Raw payload đã mã hóa/thu giảm trong thời gian lưu trữ. |
| `processing_result` | TEXT | NOT NULL | Kết quả xử lý: `ACCEPTED`, `DUPLICATE`, `REJECTED`, `RECONCILIATION_REQUIRED`. |
| `rejection_reason_code` | TEXT | NULL | Mã lý do an toàn cho callback không hợp lệ / sai lệch. |
| `received_at` | TIMESTAMPTZ | NOT NULL | Thời điểm API Gateway nhận callback (UTC). |
| `provider_occurred_at` | TIMESTAMPTZ | NULL | Thời gian nghiệp vụ do provider báo cáo (chuẩn hóa về UTC). |
| `processed_at` | TIMESTAMPTZ | NULL | Thời điểm hoàn tất xử lý nghiệp vụ. |

Callback có chữ ký sai có thể được lưu trữ tại đây làm bằng chứng an ninh nhưng tuyệt đối không được tạo bản ghi idempotency nghiệp vụ hay làm thay đổi trạng thái payment.

### 5.5 Bảng `reconciliation_runs` và `reconciliation_items`

#### Bảng `reconciliation_runs`

| Cột | Kiểu | Ràng buộc | Mô tả / Chú thích |
|---|---|---|---|
| `id` | UUID | Khóa chính (PK) | Định danh của lượt chạy đối soát. |
| `tenant_id` | UUID | NOT NULL, indexed | Phạm vi tenant của lượt chạy. |
| `requested_by_type` | TEXT | NOT NULL | Loại tác nhân yêu cầu: Scheduler, Worker hoặc Vận hành. |
| `requested_by_id` | UUID | NULL | Mã định danh workload hoặc nhân viên vận hành. |
| `reason` | TEXT | NOT NULL | Lý do kích hoạt lượt chạy đối soát. |
| `status` | TEXT | NOT NULL | Trạng thái: `QUEUED`, `RUNNING`, `COMPLETED`, `PARTIAL`, `FAILED`. |
| `requested_at` | TIMESTAMPTZ | NOT NULL | Thời điểm yêu cầu chạy đối soát (UTC). |
| `started_at` | TIMESTAMPTZ | NULL | Thời điểm worker bắt đầu thực thi. |
| `completed_at` | TIMESTAMPTZ | NULL | Thời điểm worker hoàn tất xử lý. |
| `correlation_id` | UUID | NOT NULL | Mã tương quan truy vết của lượt chạy. |

#### Bảng `reconciliation_items`

| Cột | Kiểu | Ràng buộc | Mô tả / Chú thích |
|---|---|---|---|
| `id` | UUID | Khóa chính (PK) | Định danh của từng mục đối soát cụ thể. |
| `run_id` | UUID | NOT NULL, indexed | Thuộc lượt chạy đối soát cha nào. |
| `payment_id` | UUID | NULL | Tham chiếu payment attempt cục bộ. |
| `provider` | TEXT | NOT NULL | Tên nhà cung cấp được tra cứu. |
| `provider_reference` | TEXT | NOT NULL | Mã tham chiếu đơn hàng/giao dịch được tra cứu. |
| `local_status_before` | TEXT | NOT NULL | Trạng thái payment nội bộ trước khi tra cứu. |
| `provider_status` | TEXT | NULL | Kết quả trạng thái chuẩn hóa từ phía provider. |
| `result` | TEXT | NOT NULL | Kết luận: `PAID`, `FAILED`, `EXPIRED`, `UNKNOWN`, `SETTLED_LATE`, `ERROR`. |
| `attempt_count` | INTEGER | NOT NULL DEFAULT 0 | Số lần đã thực hiện truy vấn provider. |
| `next_retry_at` | TIMESTAMPTZ | NULL | Thời điểm đủ điều kiện thử lại tiếp theo. |
| `lease_owner` | TEXT | NULL | Định danh worker đang giữ quyền khóa lease. |
| `lease_expires_at` | TIMESTAMPTZ | NULL | Thời điểm hết hạn của khóa lease. |
| `error_code` | TEXT | NULL | Mã lỗi an toàn từ provider hoặc quá trình xử lý. |
| `operator_status` | TEXT | NOT NULL | Trạng thái xử lý: `UNASSIGNED`, `OPEN`, `UNDER_REVIEW`, `RESOLVED`. |
| `created_at` | TIMESTAMPTZ | NOT NULL | Thời điểm tạo mục đối soát. |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Thời điểm cập nhật mục đối soát gần nhất. |

### 5.6 Bảng `late_payment_reconciliation`

| Cột | Kiểu | Ràng buộc | Mô tả / Chú thích |
|---|---|---|---|
| `id` | UUID | Khóa chính (PK) | Định danh bản ghi đối soát thanh toán muộn. |
| `tenant_id` | UUID | NOT NULL, indexed | Phạm vi tenant. |
| `branch_id` | UUID | NOT NULL, indexed | Phạm vi chi nhánh. |
| `payment_id` | UUID | NOT NULL, indexed | Tham chiếu payment attempt đã ở trạng thái kết thúc. |
| `order_id` | UUID | NOT NULL | Tham chiếu Order mờ. |
| `checkout_attempt_id` | UUID | NOT NULL | Tham chiếu checkout attempt gốc. |
| `provider` | TEXT | NOT NULL | Nhà cung cấp báo cáo quyết toán muộn. |
| `provider_order_ref` | TEXT | NOT NULL | Mã tham chiếu merchant phía provider. |
| `provider_transaction_id` | TEXT | NOT NULL | Mã giao dịch quyết toán muộn từ provider. |
| `amount_minor` | BIGINT | NOT NULL | Số tiền quyết toán muộn do provider báo cáo. |
| `currency` | TEXT | NOT NULL | Đơn vị tiền tệ quyết toán. |
| `settlement_status` | TEXT | NOT NULL | Giá trị P0: `SETTLED_LATE`; tuyệt đối không có nghĩa là Order đã thanh toán. |
| `status` | TEXT | NOT NULL | Trạng thái xử lý: `OPEN`, `UNDER_REVIEW`, `RECOVERED`, `REFUNDED`, `CLOSED`. |
| `reason_code` | TEXT | NOT NULL | Lý do quyết toán đến sau khi attempt đã kết thúc. |
| `operator_id` | UUID | NULL | Nhân viên vận hành được phân công xử lý. |
| `resolution_note_safe` | TEXT | NULL | Ghi chú xử lý an toàn của nhân viên vận hành. |
| `resolved_at` | TIMESTAMPTZ | NULL | Thời điểm giải quyết xong ngoại lệ (UTC). |
| `created_at` | TIMESTAMPTZ | NOT NULL | Thời điểm tạo bản ghi ngoại lệ. |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Thời điểm cập nhật bản ghi ngoại lệ gần nhất. |

### 5.7 Bảng `idempotency_records`

| Cột | Kiểu | Ràng buộc | Mô tả / Chú thích |
|---|---|---|---|
| `id` | UUID | Khóa chính (PK) | Định danh bản ghi idempotency. |
| `tenant_id` | UUID | NOT NULL, indexed | Phạm vi tenant. |
| `actor_scope` | TEXT | NOT NULL | Phạm vi workload của người dùng hoặc service. |
| `operation` | TEXT | NOT NULL | Tên thao tác lệnh nghiệp vụ. |
| `idempotency_key` | TEXT | NOT NULL | Khóa thử lại do bên gọi cung cấp. |
| `request_hash` | TEXT | NOT NULL | Fingerprint chuẩn hóa của request body/param. |
| `response_status` | INTEGER | NOT NULL | Mã trạng thái HTTP kết quả ban đầu. |
| `response_reference` | UUID | NULL | Mã payment/refund/run được trả về bởi lệnh ban đầu. |
| `response_body_redacted` | JSONB | NOT NULL | Body phản hồi an toàn có thể phát lại trực tiếp. |
| `expires_at` | TIMESTAMPTZ | NOT NULL | Thời điểm hết hạn lưu giữ idempotency. |
| `created_at` | TIMESTAMPTZ | NOT NULL | Thời điểm tạo bản ghi idempotency. |

### 5.8 Bảng `outbox_messages`

| Cột | Kiểu | Ràng buộc | Mô tả / Chú thích |
|---|---|---|---|
| `id` | UUID | Khóa chính (PK) | Định danh sự kiện outbox và khóa khử trùng lặp của consumer. |
| `tenant_id` | UUID | NOT NULL | Phạm vi tenant trong envelope sự kiện. |
| `branch_id` | UUID | NOT NULL | Phạm vi chi nhánh trong envelope sự kiện. |
| `aggregate_type` | TEXT | NOT NULL | Loại tập hợp, trong P0 là `Payment`. |
| `aggregate_id` | UUID | NOT NULL | Định danh của tập hợp Payment. |
| `event_type` | TEXT | NOT NULL | Tên kiểu sự kiện (không có hậu tố phiên bản). |
| `event_version` | INTEGER | NOT NULL | Phiên bản schema sự kiện bất biến. |
| `payload` | JSONB | NOT NULL | Envelope và payload sự kiện có phiên bản, không chứa bí mật. |
| `occurred_at` | TIMESTAMPTZ | NOT NULL | Thời điểm nghiệp vụ sự kiện phát sinh. |
| `publish_status` | TEXT | NOT NULL | Trạng thái xuất bản: `PENDING`, `PUBLISHED`, `DLQ`. |
| `attempt_count` | INTEGER | NOT NULL DEFAULT 0 | Số lần đã thực hiện xuất bản sang broker. |
| `next_attempt_at` | TIMESTAMPTZ | NULL | Thời điểm đủ điều kiện thử lại xuất bản tiếp theo. |
| `last_error_safe` | TEXT | NULL | Lỗi xuất bản gần nhất đã được làm sạch thông tin. |
| `created_at` | TIMESTAMPTZ | NOT NULL | Thời điểm tạo bản ghi outbox. |

### 5.9 Các Bảng P1: `payment_allocations` và `refunds`

Bảng `payment_allocations` ghi nhận việc phân bổ thanh toán cho từng phương thức trong một Order; bảng `refunds` ghi nhận giao dịch hoàn tiền một phần/toàn bộ độc lập với payment attempt gốc.

| Bảng | Các cột bắt buộc và chú thích |
|---|---|
| `payment_allocations` | `id` UUID; phạm vi `tenant_id`/`branch_id`; `order_id`; `payment_id`; `amount_minor`; `currency`; `status`; `idempotency_key`; `created_at`; `updated_at`. Phân bổ không được phép làm tổng tiền phân bổ vượt quá số tiền phải trả bất biến của Order. |
| `refunds` | `id` UUID; phạm vi `tenant_id`/`branch_id`; `payment_id`; `amount_minor`; `currency`; `reason`; `status`; `provider`; `provider_ref`; `requested_by`; `processed_at`; `idempotency_key`; `created_at`; `updated_at`. Tổng số tiền hoàn thành công không được vượt quá số dư khả dụng có thể hoàn tiền. |

## 6. Các Ràng Buộc và Chỉ Mục (Constraints & Indexes)

| Tên ràng buộc / Chỉ mục | Các cột / Điều kiện | Mục đích sử dụng |
|---|---|---|
| `uq_payment_idempotency` | `tenant_id, actor_scope, operation, idempotency_key` | Đảm bảo duy nhất một kết quả phát lại cho mỗi khóa lệnh |
| `uq_provider_transaction` | `tenant_id, provider, provider_transaction_id` khi khác null | Ngăn chặn việc quyết toán trùng lặp từ provider trong phạm vi |
| `uq_provider_callback_key` | `provider, provider_event_key` | Khử trùng lặp callback từ phía nhà cung cấp |
| `uq_active_vnpay_attempt` | `checkout_attempt_id` với điều kiện method `VNPAY_QR` và status `PENDING` | Chỉ duy nhất một attempt QR active; attempt cũ được đánh giá hết hạn và chuyển trạng thái nguyên tử trước khi tạo attempt mới |
| `idx_payment_scope_time` | `tenant_id, branch_id, created_at DESC` | Truy vấn lịch sử theo chi nhánh và tenant |
| `idx_payment_pending_expiry` | `tenant_id, status, expires_at` | Quét đối soát các payment pending đã quá hạn |
| `idx_payment_reconciliation_retry` | `tenant_id, reconciliation_required, next_retry_at` | Lựa chọn các mục cần đưa vào hàng đợi thử lại đối soát |
| `idx_callback_provider_time` | `provider, received_at DESC` | Lịch sử và vận hành tra cứu callback |
| `idx_outbox_publish` | `publish_status, next_attempt_at, created_at` | Tiến trình polling của outbox publisher |
| `idx_reconciliation_operator` | `tenant_id, branch_id, operator_status, updated_at DESC` | Hàng đợi tác vụ cho nhân viên vận hành |

Việc đánh giá hết hạn (expiry) được thực hiện bên trong giao dịch khởi tạo / đối soát. Một yêu cầu phát hiện attempt `PENDING` đã quá hạn bắt buộc phải chuyển trạng thái attempt cũ sang `EXPIRED` dưới cùng một khóa trước khi tạo attempt mới; chỉ mục duy nhất trong database tuyệt đối không sử dụng mệnh đề điều kiện không bất biến `expires_at > now()`.

## 7. Hợp đồng Xử lý Dữ liệu và An Toàn Bảo Mật

- Phân quyền API người dùng: Cognito JWT + quyết định tư cách thành viên và quyền hạn hiện tại từ Identity Service.
- Phân quyền API nội bộ: Workload identity / mTLS hoặc ngữ cảnh workload có chữ ký; client công khai không thể gọi `/internal/v1`.
- Các phương thức repository bắt buộc phải có phạm vi tenant; phạm vi chi nhánh là bắt buộc trừ khi bên gọi có quyền Owner hoặc vận hành toàn tenant.
- Trường `vnp_SecureHash` bắt buộc phải được xác thực thành công trước khi thực hiện ánh xạ provider hoặc tra cứu nghiệp vụ.
- Raw callback payload có thể được lưu trữ dưới dạng mã hóa trong 90 ngày; metadata chuẩn hóa, chuỗi băm và bằng chứng quyết toán tuân theo chính sách lưu giữ hồ sơ tài chính.
- Sổ cái thanh toán / hoàn tiền / đối soát và bằng chứng audit/bảo mật có thời gian lưu giữ cơ sở là 7 năm, tuân thủ theo quy định pháp luật và chính sách của tenant.
- Tuyệt đối không log dữ liệu thẻ thô, khóa bí mật, JWT, chuỗi ký thô hay PII khách hàng không cần thiết.
- Bản ghi audit log bao gồm: Loại/ID tác nhân, hành động, lý do, tài nguyên, tenant/chi nhánh, correlation ID, kết quả và các dấu thời gian.

## 8. Các Sửa Đổi Bắt Buộc Đối với Hợp đồng Cha

Các thay đổi sau đây bắt buộc phải được cập nhật vào tài liệu `docs/designs/pos-platform/2026-10-12-contracts.md` trước khi triển khai:

1. Đổi endpoint VNPAY IPN từ `POST` sang `GET /callbacks/v1/vnpay/ipn`; dữ liệu gửi lên là query parameters và phản hồi trả về tương thích với provider `{RspCode, Message}`.
2. Đánh dấu trường `PaymentInitiated.expiresAt` có thể mang giá trị null đối với phương thức CASH đồng bộ.
3. Đánh dấu các trường `PaymentPaid.provider` và `providerTransactionId` có thể mang giá trị null đối với CASH.
4. Bổ sung các trường `paymentMethod`, `amountReceivedMinor` và `cashChangeMinor` vào payload sự kiện `PaymentPaid` khi áp dụng.
5. Bổ sung sự kiện `PaymentReconciliationRequired.v1` với các trường `reconciliationId`, `reasonCode` và `settlementStatus`.
6. Định nghĩa quyết toán muộn `SETTLED_LATE` là bằng chứng đối soát, tuyệt đối không phát `PaymentPaid` hay `OrderPaid`.
7. Phạm vi hóa chỉ mục `uq_provider_transaction` theo tenant/provider và cho phép các tham chiếu provider mang giá trị null.
8. Cập nhật request/response khởi tạo Payment với `sagaId`, `checkoutAttemptId`, thông tin VNPAY QR/hết hạn, cờ đối soát và tiền thừa tiền mặt.
9. Bổ sung các hợp đồng `GET /api/v1/payments`, chi tiết đối soát và hợp đồng hoàn tiền P1.
10. Bổ sung các dữ liệu mẫu kiểm thử hợp đồng cho checksum không hợp lệ, sai số tiền/đơn hàng, IPN trùng lặp / sai thứ tự / đến muộn, phản hồi tiền mặt và phản hồi thử lại VNPAY.

## 9. Ma trận Kiểm thử Hợp đồng (Contract Test Matrix)

| Kịch bản kiểm thử | Kết quả hợp đồng kỳ vọng |
|---|---|
| Tiền mặt đưa vừa đúng số tiền | Trả về `200`, `PAID`, tiền thừa `0`, phát đúng một `PaymentPaid` |
| Tiền mặt khách đưa thừa tiền | Trả về `200`, tính đúng tiền thừa số nguyên, phát đúng một `PaymentPaid` |
| Tiền mặt khách đưa thiếu tiền | Trả về `422 invalid_cash_amount`, không đổi trạng thái, không phát sự kiện |
| Gửi trùng lặp yêu cầu tiền mặt | Trả về phản hồi ban đầu, không tạo dòng mới, không phát sự kiện lặp |
| Khởi tạo VNPAY đồng thời | Chỉ tạo duy nhất một `PENDING` active, trả về cùng thông tin QR/payment |
| Checksum IPN không hợp lệ | Trả về phản hồi không hợp lệ tương thích provider, không đổi trạng thái, không phát sự kiện |
| Chữ ký hợp lệ nhưng sai số tiền | Không chuyển `PAID`, lưu callback từ chối / bản ghi đối soát |
| IPN báo thành công hợp lệ | Chuyển `PENDING → PAID`, phát ra đúng một `PaymentPaid` |
| IPN hợp lệ bị gửi trùng lặp | Trả về kết quả tương thích provider, không tạo tác dụng phụ lần hai |
| Callback đến sai thứ tự | Không ghi đè trạng thái kết thúc, lưu bằng chứng callback/audit |
| Thành công đến sau khi kết thúc | Ghi nhận `SETTLED_LATE`, chỉ phát sự kiện đối soát, không phục hồi lại Order |
| Provider bị timeout | Giữ nguyên `PENDING` + gắn cờ đối soát, không báo thất bại giả mạo |
| Đối soát thành công khi pending | Chuyển `PAID`, phát ra đúng một `PaymentPaid` |
| Đối soát trạng thái chưa rõ | Giữ nguyên trạng thái, thử lại lùi số mũ, đưa vào hàng đợi thủ công |
| Đọc dữ liệu chéo tenant/chi nhánh | Trả về mã `404`/`403` mà không làm rò rỉ sự tồn tại của tài nguyên |
| Cùng idempotency key nhưng khác hash | Trả về mã lỗi `409 idempotency_conflict` |
| Hoàn tiền P1 vượt quá số dư | Trả về mã lỗi `409/422`, không tạo tác dụng phụ hoàn tiền |

## 10. Tài Liệu Tham Khảo Bên Ngoài

- [Tích hợp VNPAY PAY — URL thanh toán và IPN](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html): URL thanh toán sandbox, các trường query VNPAY, quy tắc sắp xếp tính checksum, GET IPN, ngữ nghĩa phản hồi và hành vi gửi lại.
- [API VNPAY querydr / refund](https://sandbox.vnpayment.vn/apis/docs/truy-van-hoan-tien/querydr%26refund.html): API tra cứu trạng thái giao dịch, hợp đồng checksum và payload/phản hồi API hoàn tiền.
