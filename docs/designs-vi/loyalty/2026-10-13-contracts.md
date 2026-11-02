# Module 5 — Dịch Vụ Khách Hàng Thân Thiết & Khuyến Mãi (Loyalty Service): Hợp Đồng API & Dữ Liệu (API & Data Contracts)

**Ngày:** 2026-10-13  
**Trạng thái:** Bản thảo — Chờ Contract Review (Draft — Pending Contract Review)  
**Phạm vi:** M — Luồng Service Tiêu chuẩn (Standard Service Flow)  
**Module:** Khách hàng thân thiết & khuyến mãi (Loyalty Service)

---

## 1. Thẩm Quyền Hợp Đồng & Các Nguyên Tắc Cốt Lõi

Tài liệu này là đặc tả chuẩn mực (normative contract) cho toàn bộ API công khai của nhân viên, API nội bộ phục vụ quy trình checkout, API cổng tra cứu khách hàng, lược đồ cơ sở dữ liệu PostgreSQL, và các sự kiện tích hợp của Loyalty Service. Quá trình triển khai mã nguồn bắt buộc phải sinh các file OpenAPI 3.1, AsyncAPI/JSON Schema và các migration script Flyway từ các định nghĩa trong tài liệu này trước khi lập trình tầng controller hoặc domain.

### Tài Liệu Nguồn Liên Quan:
- [Loyalty Vision](./2026-10-13-vision.md)
- [Loyalty Requirements](./2026-10-13-requirements.md)
- [Loyalty Architecture](./2026-10-13-architecture.md)
- [Hợp đồng Order Service](../order/2026-10-12-contracts.md)
- [Hợp đồng Payment Service](../payment/2026-10-12-contracts.md)
- [Hợp đồng POS Platform](../pos-platform/2026-10-12-contracts.md)
- [Hợp đồng Identity & Access](../identity-access/2026-10-12-contracts.md)

### Các Nguyên Tắc Bắt Buộc:
- Giao tiếp đồng bộ sử dụng kiến trúc RESTful chuẩn hóa theo đặc tả OpenAPI 3.1; giao tiếp bất đồng bộ qua sự kiện tuân thủ AsyncAPI / JSON Schema.
- Phân vùng đường dẫn rõ ràng: API công khai cho nhân viên quầy POS nằm dưới `/api/v1`; API nội bộ liên service cho checkout nằm dưới `/internal/v1`; cổng tra cứu khách hàng có ranh giới xác thực riêng biệt nằm dưới `/portal/v1`.
- Định danh duy nhất toàn hệ thống sử dụng UUID v7 dạng chuỗi; dấu thời gian tuân thủ định dạng ISO-8601 chuẩn UTC; điểm thưởng và tiền tệ là số nguyên không âm `int64` (ngoại trừ trường biến động `pointsDelta` trong sổ cái có dấu âm/dương).
- Ngữ cảnh Tenant (`tenantId`) luôn được trích xuất từ token xác thực Cognito của nhân viên hoặc Workload Identity tin cậy của hệ thống; tham số `tenantId` trong request body/query không được coi là bằng chứng cấp quyền sở hữu tài nguyên.
- Sự kiện được phân phối theo mô hình ít nhất một lần (at-least-once); phía consumer thực hiện khử trùng lặp (deduplication) dựa trên `eventId` và khóa nghiệp vụ idempotency.
- Sổ cái điểm thưởng là bất biến chỉ ghi thêm; bảng tổng hợp số dư và số liệu thống kê sử dụng voucher là các view tổng hợp (projections) có thể tái tạo hoàn toàn từ đầu.
- Tuyệt đối không lưu trữ, không trả về và không ghi vào log: số điện thoại dạng văn bản thô (plaintext), mã OTP, token xác thực, secret, khóa mã hóa thô, hay thông tin về sự tồn tại của khách hàng ở tenant khác.

---

## 2. Tiêu Chuẩn HTTP Chung (Common HTTP Contract)

### 2.1 Các HTTP Header Quy Chuẩn

| Header | Bắt buộc | Ý nghĩa kỹ thuật |
|---|---:|---|
| `Authorization` | API công khai / Cổng portal có bảo vệ | Nhân viên: `Bearer <Cognito access token>`. Khách hàng: `Bearer <Customer access token>` được cấp sau khi xác thực OTP thành công. |
| `X-Correlation-Id` | Khuyến nghị | Mã truy vết phân tán xuyên suốt API, Saga, sự kiện, nhật ký audit và trace; server tự sinh nếu client không truyền. |
| `X-Active-Branch-Id` | Thao tác của nhân viên tại chi nhánh | Định danh chi nhánh đang thao tác; server kiểm tra quyền hạn thành viên của nhân viên. Tuyệt đối không dùng để thay đổi tenant. |
| `Idempotency-Key` | API thay đổi dữ liệu có thể retry (POST/PATCH) | Khóa chống trùng lặp trong phạm vi `actor/service + tenant + operation`; mã hash của payload request phải giữ nguyên khi gửi lại. |
| `If-Match` | Cập nhật cấu hình / Hồ sơ có phiên bản | ETag hoặc số phiên bản của tài nguyên; nếu phiên bản gửi lên bị lỗi thời sẽ trả về lỗi xung đột `409 Conflict`. |
| `Content-Type` | Yêu cầu có request body | `application/json; charset=utf-8`. |

*Lưu ý bảo mật:* Yêu cầu nội bộ bổ sung thêm Workload Identity / mTLS hoặc ngữ cảnh service context có chữ ký. Client công khai tuyệt đối bị chặn không thể truy cập các route `/internal/v1`.

### 2.2 Định Dạng Phản Hồi Thành Công (Success Envelope)

Phản hồi tài nguyên đơn lẻ:
```json
{
  "data": {},
  "meta": {
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
    "pagination": {
      "hasMore": true,
      "nextCursor": "eyJvY2N1cnJlZEF0Ijoi..."
    },
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```
*Quy chuẩn:* Kích thước trang mặc định là 20 phần tử, tối đa không quá 100. Chuỗi con trỏ `nextCursor` là chuỗi mã hóa mờ (opaque), có gắn kèm bộ lọc và tự động hết hạn theo chính sách.

### 2.3 Định Dạng Phản Hồi Lỗi (Error Envelope)

```json
{
  "error": {
    "code": "insufficient_points",
    "message": "The customer does not have enough available points",
    "details": [
      {
        "field": "requestedPoints",
        "code": "available_balance",
        "message": "Requested points exceed the available balance"
      }
    ],
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

### Bảng Mã Lỗi Máy Đọc Được (Machine-Readable Error Codes):

| HTTP Status | Mã lỗi máy đọc (`code`) | Ý nghĩa nghiệp vụ |
|---:|---|---|
| 401 | `unauthorized` | Thiếu, sai hoặc hết hạn token xác thực. |
| 403 | `forbidden` | Quyền hạn vai trò, phạm vi tenant, chi nhánh hoặc quyền sở hữu khách hàng không hợp lệ. |
| 404 | `not_found` | Tài nguyên không tồn tại trong phạm vi được ủy quyền; không làm lộ dữ liệu của tenant khác. |
| 404 | `customer_not_found` | Thao tác tra cứu của nhân viên không tìm thấy số điện thoại chuẩn hóa trong tenant. |
| 409 | `duplicate_request` | Idempotency key bị sử dụng lại với nội dung payload request khác. |
| 409 | `customer_phone_conflict` | Số điện thoại đã được đăng ký cho một hồ sơ khách hàng khác trong cùng tenant. |
| 409 | `insufficient_points` | Số dư điểm khả dụng của khách hàng không đủ tại thời điểm thực hiện giữ điểm. |
| 409 | `hold_not_active` | Lượt giữ điểm đã bị committed, released, expired hoặc không thuộc về checkout attempt này. |
| 409 | `quote_expired` | Báo giá chiết khấu hoặc lượt giữ chỗ voucher đã hết hạn hoặc không thể tái thẩm định. |
| 409 | `voucher_limit_reached` | Voucher đã chạm hạn mức sử dụng tối đa toàn hệ thống hoặc hạn mức trên mỗi khách. |
| 409 | `voucher_reservation_conflict` | Xung đột tranh chấp tạm giữ voucher không thể hoàn tất một cách an toàn. |
| 409 | `rule_version_retired` | Phiên bản quy tắc khuyến mãi không còn active tại thời điểm kiểm tra lại đơn hàng. |
| 409 | `version_conflict` | Header `If-Match` không khớp với phiên bản dữ liệu hiện tại trong cơ sở dữ liệu. |
| 409 | `state_conflict` | Lệnh gửi tới không hợp lệ đối với trạng thái hiện tại của thực thể. |
| 422 | `validation_error` | Dữ liệu đầu vào hoặc quy tắc nghiệp vụ vi phạm điều kiện hợp lệ. |
| 422 | `invalid_phone` | Số điện thoại không thể chuẩn hóa về định dạng quốc tế E.164 theo chính sách tenant. |
| 422 | `invalid_basket_snapshot` | Bản chụp giỏ hàng bị thiếu các trường thông tin/dòng món/giá trị cần thiết để tính báo giá. |
| 422 | `invalid_rule_config` | Cấu hình quy tắc khuyến mãi dạng JSONB không hợp lệ với loại quy tắc `ruleType`. |
| 422 | `foreign_resource` | Mã tham chiếu khách hàng/đơn hàng/voucher/hold không thuộc về tenant context tin cậy. |
| 429 | `rate_limited` | Yêu cầu gửi mã OTP hoặc gọi API vượt quá tần suất cho phép. |
| 503 | `authorization_unavailable` | Không thể phân giải thông tin ủy quyền một cách an toàn; kích hoạt cơ chế fail-closed. |
| 503 | `dependency_unavailable` | Phụ thuộc Message Broker, KMS hoặc cấu hình tạm thời gián đoạn sau số lần retry tối đa. |
| 503 | `loyalty_temporarily_unavailable` | Không thể xác định số dư điểm, chính sách hoặc lượt giữ điểm an toàn; từ chối giao dịch. |

---

## 3. Hợp Đồng Phân Quyền & Phạm Vi Dữ Liệu (Authorization & Scope)

| Mã quyền (Permission) | Vai trò / Tác nhân gọi | Phạm vi áp dụng | Các thao tác được phép |
|---|---|---|---|
| `loyalty:customer-read` | Owner, Manager, Cashier | Tenant + Thành viên chi nhánh hợp lệ | Tra cứu khách hàng, xem hồ sơ, số dư, lịch sử điểm và danh sách voucher. |
| `loyalty:customer-create` | Owner, Manager, Cashier | Tenant + Thành viên chi nhánh hợp lệ | Tạo mới hồ sơ khách hàng bằng số điện thoại. |
| `loyalty:customer-write` | Owner, Manager | Tenant (ghi nhận audit chi nhánh) | Cập nhật thông tin hồ sơ cơ bản (tên hiển thị, trạng thái). |
| `loyalty:config-write` | Owner, Manager (theo phân quyền) | Toàn bộ Tenant | Cấu hình chính sách tích/đổi điểm, hạng thành viên, khuyến mãi, voucher. |
| `loyalty:checkout` | Order Service / Saga Workload | Tenant, chi nhánh, đơn hàng, lượt checkout | Tính báo giá khuyến mãi, giữ điểm/voucher, giải phóng giữ điểm/voucher. |
| `loyalty:replay` | Worker / Quản trị viên hệ thống | Phạm vi đợt chạy (run) / Tenant | Quản lý các job quét dọn, tái tạo bảng số dư và replay sự kiện từ DLQ. |
| `loyalty:portal-read` | Token xác thực của khách hàng | Duy nhất 1 khách hàng + Tenant | Xem số dư điểm, lịch sử biến động sổ cái, hạng thẻ và voucher của chính mình. |

*Nguyên tắc:*
1. Chủ chuỗi (Owner) có thể chọn tenant/chi nhánh theo Identity Service; Quản lý/Thu ngân bắt buộc phải có tư cách thành viên chi nhánh đang hoạt động.
2. Mã ID của khách hàng/đơn hàng/voucher trên đường dẫn URL bắt buộc phải được truy vấn kèm điều kiện `tenant_id`; tuyệt đối không cho phép client đoán ID để truy cập dữ liệu.
3. `customerId` trong đơn hàng là mã tham chiếu mờ (opaque reference) của Loyalty Service. Loyalty Service là nơi duy nhất chịu trách nhiệm phân giải số điện thoại và hồ sơ.
4. Token của cổng khách hàng (Customer Portal Token) tuyệt đối không thể sử dụng để gọi các endpoint của nhân viên hoặc truy cập dữ liệu của khách hàng khác.
5. Nếu dịch vụ phân quyền tạm thời gián đoạn hoặc ngữ cảnh tenant có sự mâu thuẫn, hệ thống lập tức kích hoạt cơ chế fail-closed (từ chối truy cập).

---

## 4. Danh Sách API Công Khai Cho Nhân Viên (Public Staff API)

### 4.1 Tra Cứu Khách Hàng Bằng Số Điện Thoại
`POST /api/v1/customers/lookup`  
Quyền hạn: `loyalty:customer-read`; Cashier, Manager, Owner.

#### Headers
| Header | Bắt buộc | Giá trị mẫu / Mô tả |
|---|---:|---|
| `Authorization` | Có | `Bearer eyJhbGciOiJSUzI1NiIs...` (Cognito access token) |
| `X-Active-Branch-Id` | Có | `0192b2d0-7f23-7abc-8d2c-123456789012` |
| `X-Correlation-Id` | Không | `cor_01J9X1234567890ABCDEF12345` |
| `Content-Type` | Có | `application/json` |

#### Request Body
```json
{
  "phone": "+84901234567"
}
```
*Quy tắc:* Server tự động chuẩn hóa số điện thoại theo định dạng E.164; không nhận chuỗi hash từ client. Số điện thoại không truyền qua URL path/query để tránh lọt vào access log. Nếu không tìm thấy, trả về lỗi `404 customer_not_found`.

#### Response Body `200 OK`
```json
{
  "data": {
    "customerId": "0192b2d0-8000-7abc-8d2c-123456789012",
    "maskedPhone": "+84******567",
    "displayName": "Nguyễn An",
    "status": "ACTIVE",
    "currentTier": "GOLD",
    "balance": {
      "availablePoints": 820,
      "heldPoints": 100,
      "expiredPoints": 60,
      "asOf": "2026-10-13T09:00:00Z"
    },
    "activeVoucherCount": 2
  },
  "meta": {
    "requestId": "req_01J9X1234567890ABCDEF12345",
    "correlationId": "cor_01J9X1234567890ABCDEF12345"
  }
}
```

#### Phản Hồi Lỗi
- `404 Not Found`: Khi không tìm thấy khách hàng trong tenant.
```json
{
  "error": {
    "code": "customer_not_found",
    "message": "Không tìm thấy hồ sơ khách hàng gắn với số điện thoại đã cung cấp",
    "details": [],
    "requestId": "req_01J9X1234567890ABCDEF12345",
    "correlationId": "cor_01J9X1234567890ABCDEF12345"
  }
}
```
- `422 Unprocessable Content`: `invalid_phone` khi số điện thoại không thể chuẩn hóa thành E.164.

---

### 4.2 Tạo Mới Hồ Sơ Khách Hàng
`POST /api/v1/customers`  
Quyền hạn: `loyalty:customer-create`; Cashier, Manager, Owner.

#### Headers
| Header | Bắt buộc | Giá trị mẫu / Mô tả |
|---|---:|---|
| `Authorization` | Có | `Bearer eyJhbGciOiJSUzI1NiIs...` |
| `X-Active-Branch-Id` | Có | `0192b2d0-7f23-7abc-8d2c-123456789012` |
| `Idempotency-Key` | Có | `idem_cust_create_01J9X234` |
| `X-Correlation-Id` | Không | `cor_01J9X1234567890ABCDEF12345` |
| `Content-Type` | Có | `application/json` |

#### Request Body
```json
{
  "phone": "+84901234567",
  "displayName": "Nguyễn An"
}
```
*Quy tắc:* `phone` là bắt buộc, chuẩn hóa E.164. `displayName` là tùy chọn (tối đa 200 ký tự). Trả về `201 Created` nếu tạo mới thành công. Nếu số điện thoại đã tồn tại với cùng fingerprint request, trả về hồ sơ hiện tại với `200 OK`; nếu khác fingerprint trả về lỗi `409 customer_phone_conflict`. Tuyệt đối không tự động gộp hồ sơ.

#### Response Body `201 Created`
```json
{
  "data": {
    "customerId": "0192b2d0-8000-7abc-8d2c-123456789012",
    "maskedPhone": "+84******567",
    "displayName": "Nguyễn An",
    "status": "ACTIVE",
    "currentTier": "SILVER",
    "balance": {
      "availablePoints": 0,
      "heldPoints": 0,
      "expiredPoints": 0,
      "asOf": "2026-10-13T09:00:00Z"
    },
    "createdAt": "2026-10-13T09:00:00Z",
    "version": 1
  },
  "meta": {
    "requestId": "req_01J9X1234567890ABCDEF12345",
    "correlationId": "cor_01J9X1234567890ABCDEF12345"
  }
}
```

---

### 4.3 Xem Chi Tiết Hồ Sơ Khách Hàng
`GET /api/v1/customers/{customerId}`  
Quyền hạn: `loyalty:customer-read`; Cashier, Manager, Owner.

#### Headers & Path Parameters
- **Headers:** `Authorization: Bearer <token>`, `X-Active-Branch-Id`, `X-Correlation-Id`
- **Path Parameters:**
  - `customerId` (UUID, bắt buộc): Định danh khách hàng.

#### Response Body `200 OK`
```json
{
  "data": {
    "customerId": "0192b2d0-8000-7abc-8d2c-123456789012",
    "maskedPhone": "+84******567",
    "displayName": "Nguyễn An",
    "status": "ACTIVE",
    "currentTier": "GOLD",
    "totalSpentMinor": 8500000,
    "currency": "VND",
    "createdAt": "2026-01-15T08:30:00Z",
    "updatedAt": "2026-10-13T09:00:00Z",
    "version": 4
  },
  "meta": {
    "requestId": "req_01J9X1234567890ABCDEF12345",
    "correlationId": "cor_01J9X1234567890ABCDEF12345"
  }
}
```

---

### 4.4 Cập Nhật Hồ Sơ Khách Hàng
`PATCH /api/v1/customers/{customerId}`  
Quyền hạn: `loyalty:customer-write`; Manager, Owner.

#### Headers
| Header | Bắt buộc | Giá trị mẫu / Mô tả |
|---|---:|---|
| `Authorization` | Có | `Bearer eyJhbGciOiJSUzI1NiIs...` |
| `If-Match` | Có | `"4"` (Số phiên bản optimistic lock) |
| `Idempotency-Key` | Có | `idem_cust_patch_01J9X` |
| `Content-Type` | Có | `application/json` |

#### Request Body
```json
{
  "displayName": "Nguyễn Văn An",
  "status": "ACTIVE"
}
```
*Quy tắc:* Trong giai đoạn P0 chỉ cho phép chỉnh sửa `displayName` và chuyển đổi trạng thái `status` (`ACTIVE`/`BLOCKED`). Tuyệt đối không cho phép sửa số điện thoại qua lệnh PATCH. Mọi thao tác đều được ghi nhật ký kiểm toán.

#### Response Body `200 OK`
```json
{
  "data": {
    "customerId": "0192b2d0-8000-7abc-8d2c-123456789012",
    "maskedPhone": "+84******567",
    "displayName": "Nguyễn Văn An",
    "status": "ACTIVE",
    "currentTier": "GOLD",
    "updatedAt": "2026-10-13T09:05:00Z",
    "version": 5
  },
  "meta": {
    "requestId": "req_01J9X1234567890ABCDEF12345",
    "correlationId": "cor_01J9X1234567890ABCDEF12345"
  }
}
```

---

### 4.5 Tra Cứu Số Dư Điểm Thưởng
`GET /api/v1/customers/{customerId}/loyalty-balance`  
Quyền hạn: `loyalty:customer-read`.

#### Headers & Path Parameters
- **Headers:** `Authorization: Bearer <token>`, `X-Active-Branch-Id`, `X-Correlation-Id`
- **Path Parameters:**
  - `customerId` (UUID, bắt buộc): Định danh khách hàng.

#### Response Body `200 OK`
```json
{
  "data": {
    "customerId": "0192b2d0-8000-7abc-8d2c-123456789012",
    "earnedPoints": 1200,
    "redeemedPoints": 280,
    "reversedPoints": 40,
    "expiredPoints": 60,
    "heldPoints": 100,
    "availablePoints": 820,
    "currentTier": "GOLD",
    "asOf": "2026-10-13T09:00:00Z",
    "projectionVersion": 154
  },
  "meta": {
    "requestId": "req_01J9X1234567890ABCDEF12345",
    "correlationId": "cor_01J9X1234567890ABCDEF12345"
  }
}
```
*Lưu ý:* Nếu bảng tổng hợp số dư đang trong quá trình rebuild hoặc bị trễ vượt ngưỡng an toàn, API trả về lỗi `503 loyalty_temporarily_unavailable` đối với các quyết định giao dịch mutation.

---

### 4.6 Tra Cứu Lịch Sử Sổ Cái Biến Động Điểm
`GET /api/v1/customers/{customerId}/ledger`  
Quyền hạn: `loyalty:customer-read`.

#### Headers & Query Parameters
- **Headers:** `Authorization: Bearer <token>`, `X-Active-Branch-Id`, `X-Correlation-Id`
- **Path Parameters:**
  - `customerId` (UUID, bắt buộc): Định danh khách hàng.
- **Query Parameters:**
  | Tham số | Bắt buộc | Mặc định | Mô tả |
  |---|---:|---|---|
  | `cursor` | Không | null | Con trỏ phân trang từ trang trước |
  | `limit` | Không | 20 | Số lượng bản ghi (tối đa 100) |
  | `entryType` | Không | null | Lọc theo loại biến động: `EARNED`, `REDEEMED`, `REVERSED`, `EXPIRED` |
  | `sourceType` | Không | null | Lọc theo nguồn: `ORDER_PAID`, `ORDER_REFUND`, `EXPIRY` |
  | `from` | Không | null | Dấu thời gian bắt đầu (ISO-8601 UTC) |
  | `to` | Không | null | Dấu thời gian kết thúc (ISO-8601 UTC) |

#### Response Body `200 OK`
```json
{
  "data": [
    {
      "entryId": "0192b2d0-8010-7abc-8d2c-123456789012",
      "entryType": "EARNED",
      "pointsDelta": 120,
      "sourceType": "ORDER_PAID",
      "sourceId": "0192b2d0-7f30-7abc-8d2c-123456789012",
      "relatedEntryId": null,
      "policyVersion": 4,
      "occurredAt": "2026-10-12T09:03:00Z"
    },
    {
      "entryId": "0192b2d0-7e00-7abc-8d2c-123456789012",
      "entryType": "REDEEMED",
      "pointsDelta": -100,
      "sourceType": "ORDER_PAID",
      "sourceId": "0192b2d0-7d10-7abc-8d2c-123456789012",
      "relatedEntryId": null,
      "policyVersion": 4,
      "occurredAt": "2026-10-10T14:20:00Z"
    }
  ],
  "meta": {
    "pagination": {
      "hasMore": true,
      "nextCursor": "eyJvY2N1cnJlZEF0IjoiMjAyNi0xMC0xMFQxNDoyMDowMFoiLCJpZCI6IjAxOTJiMmQwLTdlMDAtN2FiYy04ZDJjLTEyMzQ1Njc4OTAxMiJ9"
    },
    "requestId": "req_01J9X1234567890ABCDEF12345",
    "correlationId": "cor_01J9X1234567890ABCDEF12345"
  }
}
```

---

### 4.7 Tra Cứu Danh Sách Voucher Của Khách Hàng
`GET /api/v1/customers/{customerId}/vouchers`  
Quyền hạn: `loyalty:customer-read`.

#### Headers & Query Parameters
- **Headers:** `Authorization: Bearer <token>`, `X-Active-Branch-Id`, `X-Correlation-Id`
- **Path Parameters:**
  - `customerId` (UUID, bắt buộc): Định danh khách hàng.
- **Query Parameters:**
  | Tham số | Bắt buộc | Mặc định | Mô tả |
  |---|---:|---|---|
  | `cursor` | Không | null | Con trỏ phân trang |
  | `limit` | Không | 20 | Số lượng bản ghi (tối đa 100) |
  | `status` | Không | null | Lọc trạng thái: `AVAILABLE`, `RESERVED`, `CONSUMED`, `EXPIRED`, `RELEASED` |
  | `includeExpired` | Không | false | Có bao gồm voucher đã quá hạn hay không |

#### Response Body `200 OK`
```json
{
  "data": [
    {
      "voucherId": "0192b2d0-8020-7abc-8d2c-123456789012",
      "codeMasked": "WELCOME-****",
      "status": "AVAILABLE",
      "promotionId": "0192b2d0-8030-7abc-8d2c-123456789012",
      "promotionTitle": "Voucher Chào Mừng Thành Viên Mới 10%",
      "validFrom": "2026-10-01T00:00:00Z",
      "validTo": "2026-10-31T16:59:59Z",
      "minSpendMinor": 100000,
      "currency": "VND",
      "scope": {
        "branches": ["0192b2d0-7f23-7abc-8d2c-123456789012"],
        "channels": ["POS"]
      }
    }
  ],
  "meta": {
    "pagination": {
      "hasMore": false,
      "nextCursor": null
    },
    "requestId": "req_01J9X1234567890ABCDEF12345",
    "correlationId": "cor_01J9X1234567890ABCDEF12345"
  }
}
```

---

## 5. Danh Sách API Nội Bộ Phục Vụ Checkout Saga (Internal Checkout API)

### 5.1 Báo Giá Khuyến Mãi & Giữ Chỗ Điểm/Voucher Nguyên Tử
`POST /internal/v1/loyalty/checkout-quotes`  
Xác thực: `loyalty:checkout` Workload Identity / mTLS tin cậy; Bắt buộc có header `Idempotency-Key`.

#### Headers
| Header | Bắt buộc | Giá trị mẫu / Mô tả |
|---|---:|---|
| `Authorization` | Có | `Bearer <Internal-Workload-Token>` |
| `Idempotency-Key` | Có | `idem_quote_chk_0192b2d0-8040` |
| `X-Correlation-Id` | Không | `cor_01J9X1234567890ABCDEF12345` |
| `Content-Type` | Có | `application/json` |

#### Request Body
```json
{
  "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
  "branchId": "0192b2d0-7f23-7abc-8d2c-123456789012",
  "orderId": "0192b2d0-7f30-7abc-8d2c-123456789012",
  "checkoutAttemptId": "0192b2d0-8040-7abc-8d2c-123456789012",
  "customerId": "0192b2d0-8000-7abc-8d2c-123456789012",
  "currency": "VND",
  "serviceMode": "TABLE",
  "channel": "POS",
  "expiresAt": "2026-10-13T09:15:00Z",
  "requestedPoints": 100,
  "voucherCode": "WELCOME10",
  "basket": {
    "subtotalMinor": 120000,
    "lines": [
      {
        "lineId": "line-1",
        "catalogItemId": "0192b2d0-8050-7abc-8d2c-123456789012",
        "variantId": "0192b2d0-8060-7abc-8d2c-123456789012",
        "quantity": 2,
        "unitPriceMinor": 60000,
        "lineSubtotalMinor": 120000,
        "tags": ["cold"]
      }
    ]
  }
}
```

*Quy tắc nghiệp vụ:*
- Các mã định danh trong request body phải khớp hoàn toàn với service context tin cậy.
- Thời hạn `expiresAt` phải nhỏ hơn hoặc bằng thời hạn của phiên checkout từ Order Service.
- Giỏ hàng `basket` là bản chụp dữ liệu (snapshot) để tính toán chiết khấu; Loyalty Service tuyệt đối không truy vấn ngược vào Catalog hay Order database.
- Lệnh giữ điểm (`point hold`) và tạm giữ voucher (`voucher reservation`) được thực thi trong cùng một transaction nguyên tử; nếu một trong hai tác vụ thất bại thì toàn bộ transaction sẽ rollback.

#### Response Body `201 Created`
```json
{
  "data": {
    "quoteId": "0192b2d0-8070-7abc-8d2c-123456789012",
    "quoteVersion": 3,
    "expiresAt": "2026-10-13T09:15:00Z",
    "discountTotalMinor": 12000,
    "lineDiscounts": [
      {
        "lineId": "line-1",
        "discountMinor": 12000,
        "reason": "WELCOME10"
      }
    ],
    "appliedPromotions": [
      {
        "promotionId": "0192b2d0-8030-7abc-8d2c-123456789012",
        "version": 2,
        "discountMinor": 12000
      }
    ],
    "rejectedPromotions": [],
    "promotionExplanation": ["WELCOME10 selected in conflict group default-voucher"],
    "pointRedemption": {
      "holdId": "0192b2d0-8080-7abc-8d2c-123456789012",
      "requestedPoints": 100,
      "heldPoints": 100,
      "discountMinor": 10000,
      "state": "HELD",
      "expiresAt": "2026-10-13T09:15:00Z",
      "policyVersion": 5
    },
    "voucherReservation": {
      "reservationId": "0192b2d0-8090-7abc-8d2c-123456789012",
      "voucherId": "0192b2d0-8020-7abc-8d2c-123456789012",
      "codeMasked": "WELCOME-****",
      "state": "RESERVED",
      "expiresAt": "2026-10-13T09:15:00Z"
    }
  },
  "meta": {
    "requestId": "req_01J9X1234567890ABCDEF12345",
    "correlationId": "cor_01J9X1234567890ABCDEF12345"
  }
}
```

---

### 5.2 Giải Phóng Lượt Giữ Điểm (Release Point Hold)
`POST /internal/v1/loyalty/point-holds/{holdId}/release`  
Xác thực: `loyalty:checkout` Workload Identity.

#### Headers & Path Parameters
- **Headers:** `Authorization: Bearer <Workload-Token>`, `Content-Type: application/json`
- **Path Parameters:**
  - `holdId` (UUID, bắt buộc): Định danh lượt giữ điểm cần giải phóng.

#### Request Body
```json
{
  "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
  "checkoutAttemptId": "0192b2d0-8040-7abc-8d2c-123456789012",
  "reasonCode": "PAYMENT_FAILED",
  "reason": "Nhà cung cấp thanh toán phản hồi giao dịch thất bại"
}
```
*Quy tắc:* Gửi lại yêu cầu release trên đơn giữ điểm đã ở trạng thái terminal sẽ trả về trạng thái hiện tại một cách idempotent. Yêu cầu sai tenant hoặc sai lượt checkout sẽ bị từ chối với lỗi `403/404` mà không làm lộ dữ liệu.

#### Response Body `200 OK`
```json
{
  "data": {
    "holdId": "0192b2d0-8080-7abc-8d2c-123456789012",
    "state": "RELEASED",
    "releasedPoints": 100,
    "releasedAt": "2026-10-13T09:05:00Z",
    "reasonCode": "PAYMENT_FAILED"
  },
  "meta": {
    "requestId": "req_01J9X1234567890ABCDEF12345",
    "correlationId": "cor_01J9X1234567890ABCDEF12345"
  }
}
```

---

### 5.3 Giải Phóng Lượt Tạm Giữ Voucher (Release Voucher Reservation)
`POST /internal/v1/loyalty/voucher-reservations/{reservationId}/release`  
Xác thực: `loyalty:checkout` Workload Identity.

#### Headers & Path Parameters
- **Headers:** `Authorization: Bearer <Workload-Token>`, `Content-Type: application/json`
- **Path Parameters:**
  - `reservationId` (UUID, bắt buộc): Định danh lượt tạm giữ voucher.

#### Request Body
```json
{
  "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
  "checkoutAttemptId": "0192b2d0-8040-7abc-8d2c-123456789012",
  "reasonCode": "PAYMENT_FAILED",
  "reason": "Nhà cung cấp thanh toán phản hồi giao dịch thất bại"
}
```
*Quy tắc:* Thao tác đảm bảo tính idempotent và không làm giảm bộ đếm số lượt đã sử dụng trong lịch sử vì voucher chưa từng bị tiêu thụ chính thức.

#### Response Body `200 OK`
```json
{
  "data": {
    "reservationId": "0192b2d0-8090-7abc-8d2c-123456789012",
    "voucherId": "0192b2d0-8020-7abc-8d2c-123456789012",
    "state": "RELEASED",
    "releasedAt": "2026-10-13T09:05:00Z",
    "reasonCode": "PAYMENT_FAILED"
  },
  "meta": {
    "requestId": "req_01J9X1234567890ABCDEF12345",
    "correlationId": "cor_01J9X1234567890ABCDEF12345"
  }
}
```

---

### 5.4 Các API Đọc & Vận Hành Nội Bộ

#### Tra cứu trạng thái phiên checkout nội bộ
`GET /internal/v1/loyalty/checkout-attempts/{checkoutAttemptId}`  
Xác thực: `loyalty:checkout` Workload Identity. Đọc trạng thái lượt giữ điểm và voucher phục vụ đối soát quy trình Step Functions Saga.

#### Response Body `200 OK`
```json
{
  "data": {
    "checkoutAttemptId": "0192b2d0-8040-7abc-8d2c-123456789012",
    "orderId": "0192b2d0-7f30-7abc-8d2c-123456789012",
    "pointHold": {
      "holdId": "0192b2d0-8080-7abc-8d2c-123456789012",
      "state": "HELD",
      "heldPoints": 100
    },
    "voucherReservation": {
      "reservationId": "0192b2d0-8090-7abc-8d2c-123456789012",
      "state": "RESERVED",
      "voucherId": "0192b2d0-8020-7abc-8d2c-123456789012"
    }
  },
  "meta": {
    "requestId": "req_01J9X1234567890ABCDEF12345",
    "correlationId": "cor_01J9X1234567890ABCDEF12345"
  }
}
```

#### Kích hoạt Rebuild Projection số dư
`POST /internal/v1/loyalty/rebuilds`  
Xác thực: `loyalty:replay` Workload Identity. Bắt đầu tiến trình tái tạo projection số dư điểm theo phạm vi chỉ định (dành riêng cho Worker / Quản trị viên).

#### Request Body
```json
{
  "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
  "customerId": "0192b2d0-8000-7abc-8d2c-123456789012",
  "triggerReason": "DISCREPANCY_DETECTED"
}
```

#### Response Body `202 Accepted`
```json
{
  "data": {
    "jobId": "job_rebuild_loyalty_01J9X",
    "status": "QUEUED",
    "estimatedDurationSeconds": 10
  },
  "meta": {
    "requestId": "req_01J9X1234567890ABCDEF12345",
    "correlationId": "cor_01J9X1234567890ABCDEF12345"
  }
}
```

#### Replay sự kiện DLQ
`POST /internal/v1/loyalty/dlq-replays`  
Xác thực: `loyalty:replay` Workload Identity. Replay danh sách các sự kiện lỗi từ hàng đợi chết DLQ qua luồng Inbox chuẩn.


---

## 6. Danh Sách API Cấu Hình Quản Trị Giai Đoạn P1 (Configuration APIs)

Mọi thao tác thay đổi cấu hình đều yêu cầu quyền hạn Chủ chuỗi / Quản lý, bắt buộc có header `If-Match` khi cập nhật, hỗ trợ idempotency và ghi nhật ký kiểm toán. Toàn bộ các phiên bản cấu hình sau khi xuất bản (`published`) là hoàn toàn bất biến.

| Phương thức | Đường dẫn API | Mục đích nghiệp vụ |
|---|---|---|
| GET / PUT | `/api/v1/loyalty/point-policies` | Cấu hình tỷ lệ tích điểm, tỷ lệ đổi điểm, hạn mức đổi tối đa và phiên bản chính sách. |
| GET / PUT | `/api/v1/loyalty/tier-policies` | Cấu hình chỉ số xét hạng, chu kỳ trượt và các ngưỡng điểm/chi tiêu của Silver, Gold, Diamond. |
| GET / POST | `/api/v1/promotions` | Danh sách và khởi tạo chương trình khuyến mãi cha. |
| POST | `/api/v1/promotions/{promotionId}/versions` | Tạo phiên bản quy tắc khuyến mãi nháp (draft). |
| POST | `/api/v1/promotions/{promotionId}/versions/{version}/publish` | Thẩm định và chính thức xuất bản phiên bản quy tắc khuyến mãi. |
| GET / POST | `/api/v1/vouchers` | Quản lý danh mục mã voucher và các hạn mức sử dụng. |
| PATCH | `/api/v1/vouchers/{voucherId}` | Cập nhật metadata của voucher chưa xuất bản. |
| POST | `/api/v1/vouchers/{voucherId}/state` | Kích hoạt, tạm dừng hoặc lưu trữ voucher. |

Cấu trúc yêu cầu tạo phiên bản khuyến mãi:
```json
{
  "ruleType": "PERCENT_DISCOUNT",
  "priority": 100,
  "stackable": false,
  "conflictGroup": "default-voucher",
  "scope": { "branches": ["branch-1"], "channels": ["POS"] },
  "validFrom": "2026-10-15T00:00:00Z",
  "validTo": "2026-10-31T16:59:59Z",
  "ruleConfig": {
    "target": "ORDER",
    "percentBps": 1000,
    "minSpendMinor": 100000,
    "maxDiscountMinor": 50000
  }
}
```
*Quy chuẩn cấu hình:* Hỗ trợ các loại quy tắc `PERCENT_DISCOUNT` (giảm % theo dòng/đơn), `BUY_X_GET_Y` (mua X tặng Y), và `HAPPY_HOUR` (khung giờ vàng theo múi giờ chi nhánh). Cấu hình sai định dạng hoặc chứa toán tử lạ sẽ bị từ chối với lỗi `422 invalid_rule_config`. Tuyệt đối không cho phép thực thi script động.

---

## 7. Danh Sách API Cổng Khách Hàng Giai Đoạn P2 (Customer Portal API)

Cổng thông tin khách hàng sử dụng hệ thống xác thực độc lập, hoàn toàn tách biệt khỏi hệ thống Cognito của nhân viên cửa hàng.

| Phương thức | Đường dẫn API | Quyền xác thực | Mục đích sử dụng |
|---|---|---|---|
| POST | `/portal/v1/auth/otp/request` | Public (Rate-limited) | Yêu cầu gửi mã OTP về số điện thoại; phản hồi chuẩn hóa chống dò quét tài khoản. |
| POST | `/portal/v1/auth/otp/verify` | OTP Challenge | Xác thực mã OTP và cấp token truy cập cổng portal gắn với khách hàng. |
| POST | `/portal/v1/auth/logout` | Portal Token | Đăng xuất và thu hồi phiên làm việc. |
| GET | `/portal/v1/me/loyalty` | `loyalty:portal-read` | Xem số dư điểm, hạng thành viên và độ tươi dữ liệu của chính mình. |
| GET | `/portal/v1/me/ledger` | `loyalty:portal-read` | Tra cứu lịch sử biến động điểm cá nhân (phân trang con trỏ). |
| GET | `/portal/v1/me/vouchers` | `loyalty:portal-read` | Tra cứu danh sách voucher cá nhân khả dụng / đã dùng / hết hạn. |

---

## 8. Hợp Đồng Sự Kiện Tích Hợp (Integration Event Contract)

### 8.1 Cấu Trúc Phong Bì Sự Kiện Chuẩn (Standard Envelope)

```json
{
  "eventId": "0192b2d0-80a0-7abc-8d2c-123456789012",
  "eventType": "OrderPaid",
  "eventVersion": 1,
  "occurredAt": "2026-10-13T09:03:00Z",
  "producer": "order-service",
  "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
  "branchId": "0192b2d0-7f23-7abc-8d2c-123456789012",
  "aggregateType": "Order",
  "aggregateId": "0192b2d0-7f30-7abc-8d2c-123456789012",
  "correlationId": "cor_01J...",
  "causationId": "evt_01J...",
  "payload": {}
}
```
*Quy tắc an toàn:* Tên sự kiện logic là `eventType + ".v" + eventVersion` (ví dụ: `OrderPaid.v1`). Nội dung sự kiện chỉ chứa các mã ID mờ và bản chụp dữ liệu bất biến; tuyệt đối không chứa JWT, secret, số điện thoại plaintext, mã OTP hay thông tin thẻ ngân hàng.

### 8.2 Các Sự Kiện Từ Order Service Mà Loyalty Tiêu Thụ

#### Sự kiện `OrderPaid.v1`
Loyalty Service chỉ lắng nghe duy nhất sự kiện này để kích hoạt tích điểm và commit các đơn giữ điểm/voucher. Hợp đồng của Order Service bổ sung thêm các trường:
```json
{
  "orderId": "0192b2d0-7f30-7abc-8d2c-123456789012",
  "customerId": "0192b2d0-8000-7abc-8d2c-123456789012",
  "checkoutAttemptId": "0192b2d0-8040-7abc-8d2c-123456789012",
  "pointHoldId": "0192b2d0-8080-7abc-8d2c-123456789012",
  "voucherReservationId": "0192b2d0-8090-7abc-8d2c-123456789012",
  "promotionQuoteId": "0192b2d0-8070-7abc-8d2c-123456789012",
  "promotionQuoteVersion": 3,
  "loyaltyEligibleAmountMinor": 108000,
  "loyaltyPolicyVersion": 5,
  "currency": "VND",
  "paidAt": "2026-10-13T09:03:00Z",
  "orderVersion": 7,
  "lines": []
}
```
*Lưu ý:* `loyaltyEligibleAmountMinor` là giá trị hàng hóa thực tế sau chiết khấu, trước VAT, không gồm phí, tip và phần voucher. Nếu đơn không có khách hàng hoặc không dùng điểm/voucher, các trường tham chiếu sẽ là null.

#### Sự kiện `OrderRefunded.v1`
Order Service là bên phát hành sự kiện chuẩn tắc sau khi hoàn tất thủ tục hoàn tiền toàn bộ đơn hàng:
```json
{
  "orderId": "0192b2d0-7f30-7abc-8d2c-123456789012",
  "customerId": "0192b2d0-8000-7abc-8d2c-123456789012",
  "refundId": "0192b2d0-80b0-7abc-8d2c-123456789012",
  "refundType": "FULL",
  "refundStatus": "FINALIZED",
  "refundAmountMinor": 118000,
  "currency": "VND",
  "originalPaidAt": "2026-10-13T09:03:00Z",
  "originalOrderVersion": 7,
  "refundedAt": "2026-10-20T10:00:00Z"
}
```
*Quy tắc:* Khóa thu hồi duy nhất là cặp `(tenantId, refundId)`. Loyalty Service tự tính toán số điểm cần thu hồi và khôi phục dựa trên lịch sử sổ cái và phân bổ lô điểm của chính mình, tuyệt đối không tin tưởng số điểm do caller tự truyền.

#### Sự kiện `OrderCheckoutFailed.v1` (Giải phóng giữ chỗ khi checkout thất bại)
Chứa các trường: `orderId`, `checkoutAttemptId`, `pointHoldId`, `voucherReservationId`, `reasonCode`, `compensationStatus`, `failedAt`.

### 8.3 Danh Mục Sự Kiện Do Loyalty Service Phát Hành

| Tên sự kiện | Điều kiện kích hoạt | Các trường payload bắt buộc |
|---|---|---|
| `LoyaltyPointsAccrued.v1` | Tích điểm `EARNED` commit thành công | `customerId`, `orderId`, `ledgerEntryId`, `points`, `eligibleAmountMinor`, `policyVersion` |
| `LoyaltyPointsRedeemed.v1` | Điểm giữ được commit thành công | `customerId`, `orderId`, `checkoutAttemptId`, `holdId`, `ledgerEntryId`, `points`, `discountMinor` |
| `LoyaltyPointsReversed.v1` | Hoàn tiền toàn bộ đơn hàng | `customerId`, `orderId`, `refundId`, `originalEntryIds`, `reversalEntryIds`, `pointsDelta` |
| `PointHoldReleased.v1` | Giải phóng điểm giữ do lỗi/hủy/hết hạn | `customerId`, `orderId`, `checkoutAttemptId`, `holdId`, `points`, `reasonCode` |
| `VoucherConsumed.v1` | Tiêu thụ voucher khi `OrderPaid` commit | `voucherId`, `reservationId`, `customerId`, `orderId`, `usageId`, `promotionVersion` |
| `VoucherReservationReleased.v1` | Giải phóng lượt giữ voucher do lỗi/hết hạn | `voucherId`, `reservationId`, `customerId`, `orderId`, `reasonCode` |
| `MembershipTierChanged.v1` | Hạng thành viên của khách thay đổi | `customerId`, `fromTier`, `toTier`, `metric`, `metricValue`, `policyVersion`, `effectiveAt` |

---

## 9. Hợp Đồng Dữ Liệu PostgreSQL (PostgreSQL Data Contract)

### 9.1 Quy Ước Chung
- Cơ sở dữ liệu: PostgreSQL / Amazon Aurora PostgreSQL. Khóa chính: UUID v7. Tiền tệ và điểm: `BIGINT`. Dấu thời gian: `TIMESTAMPTZ` chuẩn UTC.
- Toàn bộ các bảng và cột bắt buộc phải có câu lệnh `COMMENT ON TABLE/COLUMN` mô tả chi tiết bằng tiếng Việt hoặc tiếng Anh.
- Mọi bảng đều có trường `created_at`; các bảng có cập nhật bổ sung thêm `updated_at` và cột phiên bản `version` phục vụ kiểm soát khóa lạc quan.
- Ràng buộc khóa ngoại Foreign Key chỉ áp dụng nội bộ bên trong cơ sở dữ liệu của Loyalty Service với chính sách `ON DELETE RESTRICT` để bảo toàn lịch sử sổ cái. Khách hàng ngưng hoạt động áp dụng chuyển trạng thái (`status = 'BLOCKED'`), tuyệt đối không xóa cứng (hard delete).

### 9.2 Các Bảng Dữ Liệu Cốt Lõi

#### Bảng `customers` (Hồ sơ khách hàng)
Cột: `id` (UUID, PK), `tenant_id` (UUID, NOT NULL), `phone_ciphertext` (BYTEA, NOT NULL - số điện thoại mã hóa), `phone_lookup_hash` (BYTEA, NOT NULL - mã băm HMAC tra cứu), `phone_key_version` (SMALLINT, NOT NULL - phiên bản khóa mã hóa KMS), `display_name` (TEXT, NULL), `status` (TEXT, NOT NULL - `ACTIVE/BLOCKED`), `current_tier` (TEXT, NOT NULL - `SILVER/GOLD/DIAMOND`), `created_at`/`updated_at` (TIMESTAMPTZ, NOT NULL), `version` (BIGINT, NOT NULL).

#### Bảng `point_ledger_entries` (Sổ cái biến động điểm bất biến)
Cột: `id` (UUID, PK), `tenant_id` (UUID, NOT NULL), `customer_id` (UUID, NOT NULL), `entry_type` (TEXT, NOT NULL - `EARNED/REDEEMED/REVERSED/EXPIRED`), `points_delta` (BIGINT, NOT NULL, CHECK `<> 0`), `source_type` (TEXT, NOT NULL - `ORDER_PAID/ORDER_REFUND/POINT_HOLD/EXPIRY`), `source_id` (UUID, NOT NULL), `related_entry_id` (UUID, NULL), `policy_version` (BIGINT, NULL), `idempotency_key` (TEXT, NOT NULL), `occurred_at` (TIMESTAMPTZ, NOT NULL), `metadata_json` (JSONB, NOT NULL), `created_at` (TIMESTAMPTZ, NOT NULL - không bao giờ update).

#### Bảng `point_lots` & `point_lot_allocations` (Các lô điểm FIFO & Phân bổ)
- `point_lots`: `id` (UUID, PK), `tenant_id` (UUID, NOT NULL), `customer_id` (UUID, NOT NULL), `source_entry_id` (UUID, NOT NULL), `earned_at` (TIMESTAMPTZ, NOT NULL), `expires_at` (TIMESTAMPTZ, NOT NULL), `original_points` (BIGINT, NOT NULL, CHECK `> 0`), `remaining_points` (BIGINT, NOT NULL, CHECK `>= 0 AND <= original_points`), `status` (TEXT, NOT NULL - `OPEN/DEPLETED/EXPIRED`), `created_at`/`updated_at` (TIMESTAMPTZ, NOT NULL), `version` (BIGINT, NOT NULL).
- `point_lot_allocations`: `id` (UUID, PK), `tenant_id` (UUID, NOT NULL), `lot_id` (UUID, NOT NULL), `customer_id` (UUID, NOT NULL), `allocation_type` (TEXT, NOT NULL - `HOLD/REDEEM/RELEASE/REVERSE/EXPIRE`), `source_type` (TEXT, NOT NULL), `source_id` (UUID, NOT NULL), `points` (BIGINT, NOT NULL, CHECK `> 0`), `created_at` (TIMESTAMPTZ, NOT NULL).

#### Bảng `point_holds` & `loyalty_balances` (Đơn giữ điểm & Tổng hợp số dư)
- `point_holds`: `id` (UUID, PK), `tenant_id` (UUID, NOT NULL), `customer_id` (UUID, NOT NULL), `order_id` (UUID, NOT NULL), `checkout_attempt_id` (UUID, NOT NULL), `requested_points` (BIGINT, NOT NULL, CHECK `>= 0`), `held_points` (BIGINT, NOT NULL, CHECK `>= 0`), `state` (TEXT, NOT NULL - `HELD/COMMITTED/RELEASED/EXPIRED`), `expires_at` (TIMESTAMPTZ, NOT NULL), `idempotency_key` (TEXT, NOT NULL), `created_at`/`updated_at` (TIMESTAMPTZ, NOT NULL), `version` (BIGINT, NOT NULL).
- `loyalty_balances`: `tenant_id` (UUID, NOT NULL), `customer_id` (UUID, NOT NULL, PK cùng tenant), `earned_points` (BIGINT, NOT NULL), `redeemed_points` (BIGINT, NOT NULL), `reversed_points` (BIGINT, NOT NULL), `expired_points` (BIGINT, NOT NULL), `held_points` (BIGINT, NOT NULL), `available_points` (BIGINT, NOT NULL, CHECK `>= 0`), `projection_version` (BIGINT, NOT NULL), `calculated_at` (TIMESTAMPTZ, NOT NULL).

### 9.3 Các Bảng Quản Lý Hạng Thành Viên, Khuyến Mãi & Voucher
Bao gồm chi tiết cấu trúc cho: `loyalty_point_policies`, `membership_tier_configs`, `membership_tier_history`, `promotions`, `promotion_versions`, `vouchers`, `voucher_reservations`, và `voucher_usages` như đã đặc tả ở phần Kiến trúc.

### 9.4 Các Bảng Tích Hợp & Kiểm Toán
Bao gồm chi tiết cấu trúc cho: `inbox_events`, `outbox_events`, `audit_events`, và `worker_runs`.

### 9.5 Danh Mục Ràng Buộc Toàn Vẹn & Chỉ Mục Bắt Buộc (Constraints & Indexes)
- `uq_customers_tenant_phone_hash`: `UNIQUE (tenant_id, phone_lookup_hash)`.
- `uq_ledger_accrual_source`: `UNIQUE (tenant_id, source_type, source_id, entry_type)`.
- `uq_inbox_event`: `UNIQUE (tenant_id, event_id)`.
- `uq_point_hold_checkout_attempt`: `UNIQUE (tenant_id, checkout_attempt_id)`.
- `uq_voucher_tenant_code`: `UNIQUE (tenant_id, code_normalized)`.
- `uq_voucher_reservation_attempt`: `UNIQUE (tenant_id, voucher_id, checkout_attempt_id, customer_id)`.
- `uq_voucher_usage_attempt`: `UNIQUE (tenant_id, voucher_id, checkout_attempt_id)`.
- `idx_point_lots_fifo`: `(tenant_id, customer_id, expires_at, earned_at, id)`.
- `idx_ledger_customer_time`: `(tenant_id, customer_id, occurred_at DESC, id)`.
- `idx_active_reservations_expiry`: `(tenant_id, state, expires_at)`.
- `idx_outbox_pending`: `(publish_state, next_attempt_at, created_at)`.
- Toàn bộ các ràng buộc kiểm tra số lượng không âm (`CHECK >= 0`), tỷ lệ quy đổi dương, và mã trạng thái hợp lệ.

### 9.6 Các Kịch Bản Migration Flyway (Flyway Migration Scripts)

#### `V1__init_loyalty_customers_and_ledger.sql`
```sql
-- Flyway Migration V1: Bảng hồ sơ khách hàng và sổ cái điểm thưởng bất biến
CREATE TABLE customers (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    phone_ciphertext BYTEA NOT NULL,
    phone_lookup_hash BYTEA NOT NULL,
    phone_key_version SMALLINT NOT NULL DEFAULT 1,
    display_name VARCHAR(200),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'BLOCKED')),
    current_tier VARCHAR(20) NOT NULL DEFAULT 'SILVER' CHECK (current_tier IN ('SILVER', 'GOLD', 'DIAMOND')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 1
);
CREATE UNIQUE INDEX uq_customers_tenant_phone_hash ON customers (tenant_id, phone_lookup_hash);
CREATE INDEX idx_customers_tenant_status ON customers (tenant_id, status);
COMMENT ON TABLE customers IS 'Hồ sơ khách hàng định danh bằng số điện thoại được mã hóa (PII protection)';

CREATE TABLE point_ledger_entries (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    customer_id UUID NOT NULL REFERENCES customers(id) ON DELETE RESTRICT,
    entry_type VARCHAR(20) NOT NULL CHECK (entry_type IN ('EARNED', 'REDEEMED', 'REVERSED', 'EXPIRED')),
    points_delta BIGINT NOT NULL CHECK (points_delta <> 0),
    source_type VARCHAR(30) NOT NULL CHECK (source_type IN ('ORDER_PAID', 'ORDER_REFUND', 'POINT_HOLD', 'EXPIRY', 'MANUAL_ADJUSTMENT')),
    source_id UUID NOT NULL,
    related_entry_id UUID REFERENCES point_ledger_entries(id) ON DELETE RESTRICT,
    policy_version BIGINT,
    idempotency_key VARCHAR(128) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uq_ledger_accrual_source ON point_ledger_entries (tenant_id, source_type, source_id, entry_type);
CREATE INDEX idx_ledger_customer_time ON point_ledger_entries (tenant_id, customer_id, occurred_at DESC, id);
COMMENT ON TABLE point_ledger_entries IS 'Sổ cái điểm thưởng bất biến chỉ ghi thêm (append-only ledger)';
```

#### `V2__init_point_lots_and_holds.sql`
```sql
-- Flyway Migration V2: Quản lý lô điểm FIFO, lệnh giữ điểm và tổng hợp số dư
CREATE TABLE point_lots (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    customer_id UUID NOT NULL REFERENCES customers(id) ON DELETE RESTRICT,
    source_entry_id UUID NOT NULL REFERENCES point_ledger_entries(id) ON DELETE RESTRICT,
    earned_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    original_points BIGINT NOT NULL CHECK (original_points > 0),
    remaining_points BIGINT NOT NULL CHECK (remaining_points >= 0 AND remaining_points <= original_points),
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'DEPLETED', 'EXPIRED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 1
);
CREATE INDEX idx_point_lots_fifo ON point_lots (tenant_id, customer_id, expires_at, earned_at, id);
COMMENT ON TABLE point_lots IS 'Lô điểm phục vụ khấu trừ theo nguyên tắc FIFO';

CREATE TABLE point_lot_allocations (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    lot_id UUID NOT NULL REFERENCES point_lots(id) ON DELETE RESTRICT,
    customer_id UUID NOT NULL REFERENCES customers(id) ON DELETE RESTRICT,
    allocation_type VARCHAR(20) NOT NULL CHECK (allocation_type IN ('HOLD', 'REDEEM', 'RELEASE', 'REVERSE', 'EXPIRE')),
    source_type VARCHAR(30) NOT NULL,
    source_id UUID NOT NULL,
    points BIGINT NOT NULL CHECK (points > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_allocations_source ON point_lot_allocations (tenant_id, source_type, source_id);

CREATE TABLE point_holds (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    customer_id UUID NOT NULL REFERENCES customers(id) ON DELETE RESTRICT,
    order_id UUID NOT NULL,
    checkout_attempt_id UUID NOT NULL,
    requested_points BIGINT NOT NULL CHECK (requested_points >= 0),
    held_points BIGINT NOT NULL CHECK (held_points >= 0),
    state VARCHAR(20) NOT NULL DEFAULT 'HELD' CHECK (state IN ('HELD', 'COMMITTED', 'RELEASED', 'EXPIRED')),
    expires_at TIMESTAMPTZ NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 1
);
CREATE UNIQUE INDEX uq_point_hold_checkout_attempt ON point_holds (tenant_id, checkout_attempt_id);
CREATE INDEX idx_point_holds_expiry ON point_holds (tenant_id, state, expires_at);
COMMENT ON TABLE point_holds IS 'Lệnh giữ điểm tạm thời trong phiên checkout Saga';

CREATE TABLE loyalty_balances (
    tenant_id UUID NOT NULL,
    customer_id UUID NOT NULL REFERENCES customers(id) ON DELETE RESTRICT,
    earned_points BIGINT NOT NULL DEFAULT 0 CHECK (earned_points >= 0),
    redeemed_points BIGINT NOT NULL DEFAULT 0 CHECK (redeemed_points >= 0),
    reversed_points BIGINT NOT NULL DEFAULT 0 CHECK (reversed_points >= 0),
    expired_points BIGINT NOT NULL DEFAULT 0 CHECK (expired_points >= 0),
    held_points BIGINT NOT NULL DEFAULT 0 CHECK (held_points >= 0),
    available_points BIGINT NOT NULL DEFAULT 0 CHECK (available_points >= 0),
    projection_version BIGINT NOT NULL DEFAULT 1,
    calculated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_loyalty_balances PRIMARY KEY (tenant_id, customer_id)
);
COMMENT ON TABLE loyalty_balances IS 'Read model tổng hợp số dư điểm thưởng có thể tính toán lại hoàn toàn từ ledger';
```

#### `V3__init_loyalty_policies_and_tiers.sql`
```sql
-- Flyway Migration V3: Chính sách tích/đổi điểm và cấu hình hạng thành viên
CREATE TABLE loyalty_point_policies (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    earn_rate_basis_points INT NOT NULL DEFAULT 1000, -- 10% = 1000 bps
    redeem_rate_points_per_minor INT NOT NULL DEFAULT 1, -- 1 điểm = 100 VND
    max_redeem_percent_order INT NOT NULL DEFAULT 50 CHECK (max_redeem_percent_order >= 0 AND max_redeem_percent_order <= 100),
    point_validity_days INT NOT NULL DEFAULT 365,
    is_active BOOLEAN NOT NULL DEFAULT true,
    published_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 1
);
CREATE INDEX idx_point_policies_active ON loyalty_point_policies (tenant_id, is_active, version DESC);

CREATE TABLE membership_tier_configs (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    tier_name VARCHAR(20) NOT NULL CHECK (tier_name IN ('SILVER', 'GOLD', 'DIAMOND')),
    min_spend_minor BIGINT NOT NULL DEFAULT 0 CHECK (min_spend_minor >= 0),
    min_points_earned BIGINT NOT NULL DEFAULT 0 CHECK (min_points_earned >= 0),
    tier_discount_percent_bps INT NOT NULL DEFAULT 0,
    validity_months INT NOT NULL DEFAULT 12,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_tier_config_tenant_tier UNIQUE (tenant_id, tier_name)
);

CREATE TABLE membership_tier_history (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    customer_id UUID NOT NULL REFERENCES customers(id) ON DELETE RESTRICT,
    from_tier VARCHAR(20) NOT NULL,
    to_tier VARCHAR(20) NOT NULL,
    reason VARCHAR(100) NOT NULL,
    effective_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_tier_history_customer ON membership_tier_history (tenant_id, customer_id, effective_at DESC);
```

#### `V4__init_promotions_and_vouchers.sql`
```sql
-- Flyway Migration V4: Quản lý chương trình khuyến mãi và voucher
CREATE TABLE promotions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE promotion_versions (
    id UUID PRIMARY KEY,
    promotion_id UUID NOT NULL REFERENCES promotions(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL,
    version INT NOT NULL,
    rule_type VARCHAR(30) NOT NULL CHECK (rule_type IN ('PERCENT_DISCOUNT', 'BUY_X_GET_Y', 'HAPPY_HOUR')),
    priority INT NOT NULL DEFAULT 100,
    stackable BOOLEAN NOT NULL DEFAULT false,
    conflict_group VARCHAR(64) NOT NULL DEFAULT 'default',
    scope_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    rule_config JSONB NOT NULL,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_to TIMESTAMPTZ NOT NULL,
    is_published BOOLEAN NOT NULL DEFAULT false,
    published_at TIMESTAMPTZ,
    CONSTRAINT uq_promotion_version UNIQUE (promotion_id, version)
);
CREATE INDEX idx_promo_versions_scope ON promotion_versions (tenant_id, is_published, valid_from, valid_to);

CREATE TABLE vouchers (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    promotion_id UUID NOT NULL REFERENCES promotions(id) ON DELETE RESTRICT,
    code_normalized VARCHAR(64) NOT NULL,
    customer_id UUID REFERENCES customers(id) ON DELETE RESTRICT,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE', 'RESERVED', 'CONSUMED', 'EXPIRED', 'RELEASED')),
    min_spend_minor BIGINT NOT NULL DEFAULT 0,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_to TIMESTAMPTZ NOT NULL,
    scope_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uq_voucher_tenant_code ON vouchers (tenant_id, code_normalized);
CREATE INDEX idx_vouchers_customer ON vouchers (tenant_id, customer_id, status);

CREATE TABLE voucher_reservations (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    voucher_id UUID NOT NULL REFERENCES vouchers(id) ON DELETE RESTRICT,
    customer_id UUID NOT NULL REFERENCES customers(id) ON DELETE RESTRICT,
    checkout_attempt_id UUID NOT NULL,
    state VARCHAR(20) NOT NULL DEFAULT 'RESERVED' CHECK (state IN ('RESERVED', 'COMMITTED', 'RELEASED', 'EXPIRED')),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uq_voucher_reservation_attempt ON voucher_reservations (tenant_id, voucher_id, checkout_attempt_id, customer_id);
CREATE INDEX idx_active_reservations_expiry ON voucher_reservations (tenant_id, state, expires_at);

CREATE TABLE voucher_usages (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    voucher_id UUID NOT NULL REFERENCES vouchers(id) ON DELETE RESTRICT,
    customer_id UUID NOT NULL REFERENCES customers(id) ON DELETE RESTRICT,
    order_id UUID NOT NULL,
    checkout_attempt_id UUID NOT NULL,
    promotion_version INT NOT NULL,
    discount_amount_minor BIGINT NOT NULL CHECK (discount_amount_minor >= 0),
    consumed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uq_voucher_usage_attempt ON voucher_usages (tenant_id, voucher_id, checkout_attempt_id);
```

#### `V5__init_loyalty_inbox_outbox_audit.sql`
```sql
-- Flyway Migration V5: Inbox, Outbox và Bảng Audit sự kiện
CREATE TABLE loyalty_inbox_events (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    event_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    producer VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX uq_inbox_event ON loyalty_inbox_events (tenant_id, event_id);

CREATE TABLE loyalty_outbox_events (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    event_id UUID NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    event_version INT NOT NULL DEFAULT 1,
    payload JSONB NOT NULL,
    publish_state VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (publish_state IN ('PENDING', 'PUBLISHED', 'FAILED')),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_outbox_pending ON loyalty_outbox_events (publish_state, next_attempt_at, created_at);

CREATE TABLE loyalty_audit_events (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    branch_id UUID,
    actor_id VARCHAR(64) NOT NULL,
    actor_role VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id VARCHAR(64) NOT NULL,
    changes JSONB,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_loyalty_audit_tenant_time ON loyalty_audit_events (tenant_id, occurred_at DESC);
```

#### `V6__init_loyalty_worker_runs.sql`
```sql
-- Flyway Migration V6: Bảng quản lý worker chạy nền và dọn dẹp phiếu giữ hết hạn
CREATE TABLE loyalty_worker_runs (
    id UUID PRIMARY KEY,
    job_type VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING' CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED')),
    items_processed INT NOT NULL DEFAULT 0,
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    error_message TEXT
);
CREATE INDEX idx_worker_runs_type_time ON loyalty_worker_runs (job_type, started_at DESC);
```


---

## 10. Ma Trận Kiểm Thử Hợp Đồng Bắt Buộc (Testing Contract)

- **Kiểm thử tính Idempotent:** Gửi lại cùng idempotency key và cùng mã hash payload phải trả về kết quả đã lưu mà không tạo thêm dòng dữ liệu hay sự kiện nào; gửi cùng key nhưng khác payload phải trả về lỗi `409 duplicate_request`.
- **Kiểm thử Consumer sự kiện:** Consumer bỏ qua các trường bổ sung không biết, từ chối sự kiện thiếu trường bắt buộc vào DLQ; replay sự kiện `OrderPaid` không bao giờ tạo thêm bản ghi tích điểm, đổi điểm hay tiêu thụ voucher thứ hai; replay sự kiện `OrderRefunded` với cùng `refundId` không tạo thêm lần thu hồi thứ hai.
- **Kiểm thử xử lý đồng thời:** Nhiều yêu cầu giữ điểm và tiêu thụ voucher đồng thời phải chứng minh qua kiểm thử cơ sở dữ liệu là không bao giờ bị vượt quá số dư khả dụng và không vượt quá hạn mức sử dụng.
- **Kiểm thử bảo mật & Chống rò rỉ dữ liệu:** Kiểm tra chặn tấn công IDOR xuyên tenant/chi nhánh, kiểm tra dữ liệu PII luôn được che mờ, kiểm tra số điện thoại không hợp lệ, kiểm tra cơ chế giải quyết hòa trong xung đột khuyến mãi, kiểm tra hết hạn FIFO và kiểm tra cơ chế chống quét tài khoản trên cổng khách hàng.
- **Kiểm thử Migration Database:** Kiểm tra toàn bộ script Flyway có đầy đủ comment mô tả cho từng bảng/cột, các ràng buộc check/index hoạt động chính xác trên cả DB trống và DB có dữ liệu mẫu.

---

## 11. Các Điều Chỉnh Bắt Buộc Đối Với Hợp Đồng Hệ Thống Cha

Trước khi bắt tay vào triển khai mã nguồn, các thay đổi sau bắt buộc phải được áp dụng vào tài liệu hợp đồng của Order Service:
1. Bổ sung các trường của Loyalty vào sự kiện `OrderPaid.v1` như đã đặc tả ở Mục 8.2 mà không làm thay đổi ngữ nghĩa của các trường hiện có.
2. Định nghĩa sự kiện `OrderRefunded.v1` do Order Service phát hành sau khi hoàn tất thủ tục hoàn tiền toàn bộ.
3. Chuyển tiếp các mã tham chiếu `pointHoldId` và `voucherReservationId` qua ngữ cảnh checkout, sự kiện thanh toán và luồng bồi hoàn khi thất bại.
4. Xác định rõ ràng cơ chế phân phối và correlation ID của sự kiện `OrderCheckoutFailed.v1` để giải phóng các lượt giữ điểm và voucher kịp thời.
5. Xây dựng tài liệu schema AsyncAPI và bộ fixture kiểm thử consumer cho Loyalty Service.
