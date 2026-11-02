Started at: 2026/10/13 00:00:00
Finished at: 2026/10/13 00:00:00
Total time: 0 minutes
---

# Module 11 — Giám Sát & Chống Thất Thoát: Hợp Đồng API & Dữ Liệu (Contracts)

**Ngày:** 2026-10-13  
**Trạng thái:** Đã phê duyệt kèm cảnh báo — người dùng đã phê duyệt sau khi đánh giá Contracts  
**Tầm nhìn:** [Audit & Loss Prevention Vision](file:///d:/vibe-code/pos/docs/designs-vi/audit-loss-prevention/2026-10-13-vision.md)  
**Yêu cầu:** [Audit & Loss Prevention Requirements](file:///d:/vibe-code/pos/docs/designs-vi/audit-loss-prevention/2026-10-13-requirements.md)  
**Kiến trúc:** [Audit & Loss Prevention Architecture](file:///d:/vibe-code/pos/docs/designs-vi/audit-loss-prevention/2026-10-13-architecture.md)  
**Biểu đồ tuần tự:** [Audit & Loss Prevention Sequence Diagrams](file:///d:/vibe-code/pos/docs/designs-vi/audit-loss-prevention/2026-10-13-sequence-diagrams.adoc)

Tài liệu này là nguồn chuẩn để sinh các đặc tả OpenAPI 3.1 công khai/nội bộ, AsyncAPI/JSON Schema, migration PostgreSQL/Flyway, fixture quy tắc bất thường, chính sách lưu trữ và các kiểu dữ liệu client Angular được tạo tự động. Audit sở hữu trạng thái bằng chứng/vụ việc nhưng không sở hữu trạng thái nghiệp vụ nguồn.

## 1. Quy Ước Hợp Đồng (Contract Conventions)

### 1.1 Mã hóa, định danh và tiền tệ (Encoding, Identity & Money)

- REST công khai nằm dưới `/api/v1`; REST nội bộ nằm dưới `/internal/v1`.
- Phân phối EventBridge/SQS theo cơ chế at-least-once; phong bì sự kiện sử dụng `eventType` + `eventVersion`.
- ID sử dụng chuỗi UUID v7; timestamp định dạng ISO-8601 UTC.
- Cửa sổ kinh doanh sử dụng snapshot múi giờ IANA của chi nhánh; múi giờ trình duyệt không có thẩm quyền.
- MVP chỉ hỗ trợ `VND`; tiền tệ sử dụng số nguyên có dấu/không âm `int64` ở đơn vị nhỏ nhất (minor units) theo ngữ nghĩa từng trường + `currency`.
- `reason` và bản tóm tắt là văn bản UTF-8 có giới hạn độ dài; không lưu raw request/response hoặc JSON không giới hạn.
- Các trường/sự kiện tùy chọn mới bổ sung phải tương thích ngược; việc thay đổi ý nghĩa hoặc xóa trường bắt buộc phải tăng major version của sự kiện/API.

### 1.2 Headers

| Header | Công khai | Nội bộ | Ý nghĩa |
|---|---:|---:|---|
| `Authorization` | bắt buộc | bắt buộc | Cognito JWT hoặc workload credential/mTLS |
| `X-Correlation-Id` | khuyến nghị | bắt buộc | Truy vết request/event/thao tác worker |
| `X-Causation-Id` | không | khuyến nghị | Tham chiếu lệnh/sự kiện đầu vào upstream |
| `Idempotency-Key` | thay đổi/xuất file | bắt buộc cho vận hành | Phạm vi Tenant + tác nhân/workload + thao tác |
| `If-Match` | thay đổi vụ việc | tùy chọn | Phiên bản vụ việc hiện tại / rào chắn CAS |
| `If-None-Match` | tùy chọn cho GET | không | Phản hồi điều kiện an toàn sau khi đã phân quyền |
| `Cache-Control` | server kiểm soát | server kiểm soát | Bằng chứng kiểm toán được bảo vệ không lưu cache browser/CDN |

Client tuyệt đối không được gửi `tenantId`, vai trò, quyền sở hữu chi nhánh, tác nhân/thu ngân thao tác, kết quả hay snapshot trước/sau với tư cách là dữ liệu có thẩm quyền. Các ID bộ lọc chỉ là phạm vi yêu cầu; server/Identity là nơi quyết định phạm vi (scope) thực tế.

### 1.3 Phong Bì Phản Hồi (Envelopes)

```json
{
  "data": {},
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J...",
    "nextCursor": null,
    "dataQuality": {
      "status": "FRESH",
      "freshnessLagSeconds": 12,
      "completenessStatus": "COMPLETE",
      "watermark": "audit:tenant:v901",
      "watermarkVersion": 901,
      "lastUpdatedAt": "2026-10-13T08:00:12Z"
    }
  }
}
```

```json
{
  "error": {
    "code": "scope_not_allowed",
    "message": "The requested audit scope is not available",
    "details": [],
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

Thông điệp lỗi không được tiết lộ tenant/chi nhánh ngoại lai, sự tồn tại của tác nhân, chi tiết đơn hàng/thanh toán, câu lệnh SQL, sự kiện thô, secret hay stack trace.

### 1.4 Phân Trang, Sắp Xếp và Khoảng Thời Gian

- Dòng thời gian/danh sách phát hiện sử dụng phân trang bằng con trỏ (cursor pagination); mặc định `20`, tối đa `100`.
- Cursor mã hóa bộ tuple sắp xếp ổn định, hash bộ lọc chuẩn hóa, hash phạm vi đã phân giải, phiên bản phân quyền và watermark projection.
- Khoảng thời gian tương tác mặc định là 30 ngày; tối đa là 90 ngày (tính bao gồm/không bao gồm theo mốc UTC chuẩn hóa).
- Dữ liệu lịch sử >90 ngày đến 7 năm sử dụng cơ chế xuất file bất đồng bộ; không hỗ trợ truy vấn đồng bộ không giới hạn.
- Sắp xếp ổn định mặc định theo `occurredAt DESC, eventId DESC` hoặc `detectedAt DESC, findingId DESC`.
- Sai lệch cursor so với bộ lọc/phạm vi/phiên bản trả về mã lỗi `422 invalid_cursor` hoặc `409 scope_not_allowed` tùy theo nguyên nhân.

### 1.5 Chất Lượng Dữ Liệu (Data Quality)

| Trạng thái | Quy tắc |
|---|---|
| `FRESH` | Độ trễ ≤60 giây và các nguồn/projection bắt buộc đã hoàn tất đầy đủ |
| `STALE` | Độ trễ >60 giây; >300 giây sẽ kích hoạt cảnh báo leo thang |
| `PARTIAL` | Thiếu nguồn/chi nhánh bắt buộc hoặc đang có sự cố khoảng trống/toàn vẹn dữ liệu |
| `UNAVAILABLE` | Read model hoặc cơ sở dữ liệu không khả dụng |
| `NO_DATA` | Truy vấn hợp lệ và hoàn tất nhưng không có sự kiện/phát hiện nào trong kỳ |
| `INSUFFICIENT_BASELINE` | Đánh giá bất thường bị thiếu kích thước mẫu tối thiểu hoặc đang trong giai đoạn khởi động |

`NO_DATA` không đồng nghĩa với "không xảy ra thao tác" khi nguồn/projection đang ở trạng thái `PARTIAL` hoặc `STALE`.

## 2. Hợp Đồng Phân Quyền và Danh Mục Quyền Hạn

### 2.1 Tu chính danh mục quyền hạn (Permission Catalogue)

| Quyền | Chủ sở hữu (Owner) | Quản lý (Manager) | Thu ngân | Bếp | Phạm vi áp dụng |
|---|---:|---:|---:|---:|---|
| `audit:read` | có | các chi nhánh được phân công | không | không | Tenant/tập con hoặc chi nhánh được giao |
| `loss-case:read` | có | các chi nhánh được phân công | không | không | Phạm vi phát hiện/vụ việc |
| `loss-case:investigate` | có | các chi nhánh được phân công | không | không | Xác nhận / điều tra vụ việc |
| `loss-case:resolve` | có | không | không | không | Giải quyết vụ việc |
| `loss-case:dismiss` | có | không | không | không | Bác bỏ vụ việc kèm lý do |
| `audit:operate` | thao tác hạn chế | không | không | không | Các tác vụ xuất file/phát lại/toàn vẹn/lưu trữ |

Identity luôn là thẩm quyền phân quyền trực tuyến. Audit gọi `POST /internal/v1/authorization-decisions` của service gốc cho mỗi request công khai được bảo vệ và tiếp tục áp dụng các mệnh đề lọc tại repository ngay cả sau khi đã được phê duyệt.

### 2.2 Phạm Vi Đã Phân Giải (Resolved Scope)

```json
{
  "scopeType": "BRANCH",
  "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789001",
  "branchIds": ["0192b2d0-7f1d-7abc-8d2c-123456789002"],
  "role": "MANAGER",
  "permissions": ["audit:read", "loss-case:read", "loss-case:investigate"],
  "membershipVersion": 12,
  "authorizationVersion": 7,
  "resolvedAt": "2026-10-13T08:00:00Z",
  "expiresAt": "2026-10-13T08:05:00Z"
}
```

Quy tắc:

- Phạm vi toàn tenant của Owner là tường minh; cấm dùng ID chi nhánh ký tự đại diện (wildcard).
- Tập hợp chi nhánh của Manager phải là tập con của các quan hệ thành viên đang hoạt động.
- Việc lấy chi tiết tài nguyên/vụ việc/bằng chứng và xuất file/phát lại luôn kiểm tra lại quyền sở hữu; ID trên URL không thể mở rộng phạm vi.
- Phiên bản phân quyền và hash phạm vi là một phần của khóa cache / cursor.
- Từ chối truy cập luôn đóng an toàn (fail-closed) và không liệt kê tài nguyên ngoại lai.

## 3. Các Schema Dùng Chung (Common Schemas)

### 3.1 `ActorSnapshot`

```json
{
  "actorType": "USER",
  "actorId": "0192...",
  "displayLabel": "Thu ngân A",
  "role": "CASHIER",
  "membershipId": "0192...",
  "membershipVersion": 12,
  "statusAtEvent": "ACTIVE",
  "snapshotVersion": "identity-actor-v1"
}
```

Chỉ lưu trữ nhãn hiển thị và trạng thái an toàn. Cognito subject có thể được đại diện bằng tham chiếu mờ/gắn khóa; số điện thoại/email/token không bao giờ xuất hiện trong bằng chứng của Audit.

### 3.2 `MoneyAmount` và `DiscountAllocation`

```json
{
  "amountMinor": 5000,
  "currency": "VND"
}
```

```json
{
  "source": "MANUAL_OVERRIDE",
  "promotionVersionId": null,
  "ruleVersion": null,
  "beforeMinor": 50000,
  "discountMinor": 5000,
  "afterMinor": 45000,
  "allocationStatus": "ALLOCATED"
}
```

Các ràng buộc: `amountMinor >= 0`, `currency=VND` trong MVP, `afterMinor = beforeMinor - discountMinor`, và tổng phân bổ theo dòng/đơn phải khớp đối soát hoặc sử dụng trạng thái chất lượng tường minh `UNALLOCATED`/`PARTIAL`.

### 3.3 `DataQualityEnvelope`

| Trường | Kiểu | Bắt buộc | Quy tắc |
|---|---|---:|---|
| `status` | enum | có | `FRESH`, `STALE`, `PARTIAL`, `UNAVAILABLE`, `NO_DATA`, `INSUFFICIENT_BASELINE` |
| `freshnessLagSeconds` | int64 | có | Không âm khi đã có watermark nguồn |
| `completenessStatus` | enum | có | `COMPLETE`, `PARTIAL`, `MISSING` |
| `watermark` | string | có | Checkpoint mờ |
| `watermarkVersion` | int64 | có | Tăng đơn điệu theo từng nguồn/phạm vi/projection |
| `lastUpdatedAt` | timestamp | có | Thời điểm UTC cập nhật projection |
| `projectionVersion` | int64 | có | Phiên bản projection truy vấn hiện hành |
| `missingSources` | string[] | có | Chỉ chứa tên nguồn an toàn |
| `normalizationVersion` | string | có | Chính sách chuẩn hóa của Audit |
| `asOfWatermark` | string | có | Checkpoint của bằng chứng/projection |

### 3.4 `AuditEvidenceSummary`

```json
{
  "id": "0192...",
  "eventType": "DiscountApplied.v1",
  "action": "DISCOUNT_APPLIED",
  "outcome": "SUCCEEDED",
  "tenantId": "0192...",
  "branchId": "0192...",
  "actor": {"actorType": "USER", "actorId": "0192...", "displayLabel": "Thu ngân A", "role": "CASHIER"},
  "effectiveOperator": {"actorId": "0192...", "displayLabel": "Thu ngân B", "role": "CASHIER"},
  "resource": {"type": "ORDER_LINE", "id": "0192..."},
  "shiftId": "0192...",
  "deviceId": "0192...",
  "reason": "Khách được hỗ trợ",
  "beforeSummary": {"discountMinor": 0, "currency": "VND"},
  "afterSummary": {"discountMinor": 5000, "currency": "VND"},
  "qualityCode": null,
  "occurredAt": "2026-10-13T08:00:00Z",
  "createdAt": "2026-10-13T08:00:03Z",
  "sourceEventId": "0192...",
  "sourceVersion": 18,
  "correlationId": "0192...",
  "redactionVersion": "audit-redaction-v1",
  "normalizationVersion": "audit-normalization-v1"
}
```

Các trường `beforeSummary`/`afterSummary` thuộc danh mục cho phép và có giới hạn; API công khai tuyệt đối không bao giờ trả về toàn bộ payload sự kiện thô.

## 4. Hợp Đồng API Công Khai (Public API Contracts)

Mọi endpoint công khai đều yêu cầu Cognito JWT và quyền/phạm vi do server phân giải. Phản hồi được bảo vệ sử dụng định dạng `{data, meta}`.

### 4.1 Danh mục Endpoint

| Phương thức | Đường dẫn | Quyền | Phản hồi | Mục đích |
|---|---|---|---|---|
| GET | `/api/v1/audit/events` | `audit:read` | `AuditEventPage` | Dòng thời gian / tìm kiếm, tối đa 90 ngày |
| GET | `/api/v1/audit/events/{eventId}` | `audit:read` | `AuditEventResponse` | Chi tiết an toàn kèm kiểm tra lại phạm vi |
| GET | `/api/v1/loss-prevention/findings` | `loss-case:read` | `FindingPage` | Danh sách phát hiện / vụ việc bất thường |
| GET | `/api/v1/loss-prevention/findings/{findingId}` | `loss-case:read` | `FindingResponse` | Chi tiết vụ việc / bằng chứng / giải thích |
| POST | `/api/v1/loss-prevention/findings/{findingId}/acknowledge` | `loss-case:investigate` | `FindingResponse` | Tiếp nhận / xác nhận vụ việc |
| POST | `/api/v1/loss-prevention/findings/{findingId}/investigation` | `loss-case:investigate` | `FindingResponse` | Bắt đầu hoặc cập nhật điều tra |
| POST | `/api/v1/loss-prevention/findings/{findingId}/resolve` | `loss-case:resolve` | `FindingResponse` | Owner giải quyết vụ việc |
| POST | `/api/v1/loss-prevention/findings/{findingId}/dismiss` | `loss-case:dismiss` | `FindingResponse` | Owner bác bỏ vụ việc |
| POST | `/api/v1/audit/exports` | `audit:operate` | `ExportOperationResponse` | Khởi tạo xuất dữ liệu lịch sử bất đồng bộ |
| GET | `/api/v1/audit/exports/{operationId}` | `audit:operate` | `ExportOperationResponse` | Kiểm tra tiến độ tác vụ xuất dữ liệu |

---

### 4.2 Danh sách sự kiện kiểm toán (Audit Events List)

`GET /api/v1/audit/events`

Truy vấn nhật ký sự kiện kiểm toán bảo mật với bộ lọc đa chiều và phân trang con trỏ (Cursor Pagination).

#### Tiêu đề Yêu cầu (Headers)
```http
Authorization: Bearer <Cognito JWT Token>
Accept: application/json
X-Correlation-ID: 0192b2d0-7f1d-7abc-8d2c-123456789001
```

#### Tham số Truy vấn (Query Parameters)
| Tham số | Kiểu | Bắt buộc | Quy tắc |
|---|---|:---:|---|
| `from`, `to` | ISO date/time | Có | Tối đa 90 ngày cho tương tác phân tích trực tuyến |
| `branchIds` | UUID[] | Không | Tối đa 20; phải là tập con của phạm vi đã phân giải |
| `actions` | enum[] | Không | `ORDER_LINE_CANCELLED`, `ORDER_CANCELLED`, `DISCOUNT_APPLIED`, `CASH_DRAWER_OPENED`, `AUTHORIZATION_DENIED`, `CASE_ACTION` |
| `outcomes` | enum[] | Không | `SUCCEEDED`, `REJECTED`, `FAILED`, `NO_OP` |
| `actorId` | UUID | Không | Tham chiếu nhân viên mờ; có kiểm tra phạm vi |
| `effectiveOperatorId` | UUID | Không | Tham chiếu thu ngân thao tác mờ; có kiểm tra phạm vi |
| `resourceType` | enum | Không | `ORDER`, `ORDER_LINE`, `PAYMENT`, `SHIFT`, `POS_DEVICE`, `AUDIT_CASE` |
| `resourceId` | UUID | Không | Bắt buộc thuộc phạm vi được cấp quyền |
| `shiftId`, `deviceId`, `correlationId` | UUID/string | Không | Các tham chiếu an toàn chính xác |
| `limit` | int | Không | Mặc định 20, tối đa 100 |
| `cursor` | string | Không | Chỉ dùng cursor do chính bộ lọc/phạm vi/phiên bản đó sinh ra |

#### Phản hồi Thành Công `200 OK`
```json
{
  "data": {
    "scope": {"type": "BRANCH", "branchIds": ["0192b2d0-7f1d-7abc-8d2c-123456789002"]},
    "period": {"from": "2026-10-01T00:00:00Z", "to": "2026-10-13T00:00:00Z", "timezone": "Asia/Ho_Chi_Minh"},
    "rows": [
      {
        "id": "0192b2d0-7f1d-7abc-8d2c-123456789010",
        "eventType": "DiscountApplied.v1",
        "action": "DISCOUNT_APPLIED",
        "outcome": "SUCCEEDED",
        "actor": {"actorType": "USER", "actorId": "0192b2d0-7f1d-7abc-8d2c-123456789020", "displayLabel": "Thu ngân A", "role": "CASHIER"},
        "effectiveOperator": {"actorId": "0192b2d0-7f1d-7abc-8d2c-123456789020", "displayLabel": "Thu ngân A", "role": "CASHIER"},
        "resource": {"type": "ORDER_LINE", "id": "0192b2d0-7f1d-7abc-8d2c-123456789030"},
        "reason": "Khách hàng thân thiết VIP",
        "beforeSummary": {"discountMinor": 0, "currency": "VND"},
        "afterSummary": {"discountMinor": 20000, "currency": "VND"},
        "occurredAt": "2026-10-13T08:00:00Z"
      }
    ],
    "dataQuality": {
      "status": "FRESH",
      "freshnessLagSeconds": 15,
      "completenessStatus": "COMPLETE",
      "watermark": "audit:branch:v901",
      "watermarkVersion": 901,
      "lastUpdatedAt": "2026-10-13T08:00:15Z",
      "projectionVersion": 4,
      "missingSources": [],
      "normalizationVersion": "audit-normalization-v1",
      "asOfWatermark": "audit:branch:v901"
    }
  },
  "meta": {
    "limit": 20,
    "hasMore": false,
    "nextCursor": null,
    "requestId": "req_0192b2d0-7f1d-7abc-8d2c-123456789001",
    "correlationId": "cor_0192b2d0-7f1d-7abc-8d2c-123456789002",
    "timestamp": "2026-10-13T08:00:20Z"
  }
}
```

---

### 4.2.1 Chi tiết sự kiện kiểm toán (Get Audit Event Detail)

`GET /api/v1/audit/events/{eventId}`

Truy vấn toàn văn bằng chứng kiểm toán đã được làm sạch PII (không bao gồm payload thô chưa khử độc).

#### Tiêu đề Yêu cầu (Headers)
```http
Authorization: Bearer <Cognito JWT Token>
Accept: application/json
X-Correlation-ID: 0192b2d0-7f1d-7abc-8d2c-123456789001
```

#### Phản hồi Thành Công `200 OK`
```json
{
  "data": {
    "id": "0192b2d0-7f1d-7abc-8d2c-123456789010",
    "eventType": "DiscountApplied.v1",
    "action": "DISCOUNT_APPLIED",
    "outcome": "SUCCEEDED",
    "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789001",
    "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789002",
    "actor": {"actorType": "USER", "actorId": "0192b2d0-7f1d-7abc-8d2c-123456789020", "displayLabel": "Thu ngân A", "role": "CASHIER"},
    "effectiveOperator": {"actorId": "0192b2d0-7f1d-7abc-8d2c-123456789020", "displayLabel": "Thu ngân A", "role": "CASHIER"},
    "resource": {"type": "ORDER_LINE", "id": "0192b2d0-7f1d-7abc-8d2c-123456789030"},
    "shiftId": "0192b2d0-7f1d-7abc-8d2c-123456789040",
    "deviceId": "0192b2d0-7f1d-7abc-8d2c-123456789050",
    "reason": "Khách hàng thân thiết VIP",
    "beforeSummary": {"discountMinor": 0, "currency": "VND"},
    "afterSummary": {"discountMinor": 20000, "currency": "VND"},
    "occurredAt": "2026-10-13T08:00:00Z",
    "sourceEventId": "0192b2d0-7f1d-7abc-8d2c-123456789099",
    "sourceVersion": 18,
    "correlationId": "0192b2d0-7f1d-7abc-8d2c-123456789001",
    "digestVerified": true
  },
  "meta": {
    "requestId": "req_0192b2d0-7f1d-7abc-8d2c-123456789001",
    "correlationId": "cor_0192b2d0-7f1d-7abc-8d2c-123456789002",
    "timestamp": "2026-10-13T08:00:20Z"
  }
}
```

---

### 4.3 Danh sách và chi tiết phát hiện (Findings)

`GET /api/v1/loss-prevention/findings`

Hỗ trợ các tham số bộ lọc: `state`, `severity`, `ruleFamily`, `branchIds`, `subjectId`, `from`, `to`, `limit` và `cursor`. Trường `subjectId` luôn được kiểm tra phạm vi và tuyệt đối không bao giờ được dùng để làm lộ thông tin nhân viên ngoại lai.

#### Tiêu đề Yêu cầu (Headers)
```http
Authorization: Bearer <Cognito JWT Token>
Accept: application/json
X-Correlation-ID: 0192b2d0-7f1d-7abc-8d2c-123456789001
```

#### Phản hồi Thành Công `200 OK`
```json
{
  "data": [
    {
      "findingId": "0192b2d0-7f1d-7abc-8d2c-123456789100",
      "caseId": "0192b2d0-7f1d-7abc-8d2c-123456789101",
      "state": "OPEN",
      "severity": "MEDIUM",
      "ruleFamily": "DISCOUNT_SPIKE",
      "ruleVersion": "loss-rule-v1",
      "subject": {"id": "0192b2d0-7f1d-7abc-8d2c-123456789020", "displayLabel": "Thu ngân A", "role": "CASHIER"},
      "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789002",
      "observationWindow": {"from": "2026-10-12T00:00:00Z", "to": "2026-10-13T00:00:00Z", "timezone": "Asia/Ho_Chi_Minh"},
      "metric": {"current": "0.1800", "baseline": "0.0400", "ratio": "4.5000", "unit": "discount_rate"},
      "minimumSample": 30,
      "sampleCount": 92,
      "explanationCode": "ABOVE_BASELINE_AND_MIN_SAMPLE",
      "evidenceRefs": [{"eventId": "0192b2d0-7f1d-7abc-8d2c-123456789010", "action": "DISCOUNT_APPLIED"}],
      "dataQuality": {"status": "FRESH", "completenessStatus": "COMPLETE"},
      "detectedAt": "2026-10-13T08:01:00Z",
      "version": 2
    }
  ],
  "meta": {
    "limit": 20,
    "hasMore": false,
    "nextCursor": null,
    "requestId": "req_0192b2d0-7f1d-7abc-8d2c-123456789001",
    "correlationId": "cor_0192b2d0-7f1d-7abc-8d2c-123456789002",
    "timestamp": "2026-10-13T08:02:00Z"
  }
}
```

`FindingResponse` bao gồm tóm tắt trạng thái/lịch sử vụ việc, không bao gồm payload nguồn thô. Các tham chiếu bằng chứng được kiểm tra quyền riêng lẻ trên từng mục.

---

### 4.4 Các yêu cầu thay đổi trạng thái vụ việc (Case State Machine Actions)

Các endpoint chuyển đổi trạng thái vụ việc:
- Tiếp nhận: `POST /api/v1/loss-prevention/findings/{findingId}/acknowledge`
- Điều tra: `POST /api/v1/loss-prevention/findings/{findingId}/investigation`
- Giải quyết (Chỉ Owner): `POST /api/v1/loss-prevention/findings/{findingId}/resolve`
- Bác bỏ (Chỉ Owner): `POST /api/v1/loss-prevention/findings/{findingId}/dismiss`

#### Tiêu đề Yêu cầu (Headers)
```http
Authorization: Bearer <Cognito JWT Token>
Content-Type: application/json
Accept: application/json
X-Correlation-ID: 0192b2d0-7f1d-7abc-8d2c-123456789001
Idempotency-Key: 0192b2d0-7f1d-7abc-8d2c-123456789005
```

#### Request Body Mẫu
```json
{
  "reason": "Đã kiểm tra camera và đối chiếu ca",
  "note": "Bằng chứng đã được xác minh",
  "expectedVersion": 2
}
```

- `reason` là bắt buộc đối với `investigation`, `resolve`, `dismiss`; độ dài từ 1–500 ký tự sau khi cắt tỉa và làm sạch.
- `note` là tùy chọn, có giới hạn và đã làm sạch; không chứa số điện thoại, secret hay văn bản tự do của khách hàng.
- Server tự động xác định tác nhân, vai trò, tenant, chi nhánh, trạng thái trước/sau và timestamp.
- `Owner` có quyền giải quyết/bác bỏ; `Manager` nhận lỗi `403 forbidden` nếu thực hiện các hành động này.
- Khi `expectedVersion` bị cũ, server trả về lỗi `409 case_version_conflict` mà không ghi nhận bán phần.

#### Phản hồi Thành Công `200 OK`
```json
{
  "data": {
    "findingId": "0192b2d0-7f1d-7abc-8d2c-123456789100",
    "caseId": "0192b2d0-7f1d-7abc-8d2c-123456789101",
    "state": "INVESTIGATING",
    "version": 3,
    "lastAction": {
      "action": "START_INVESTIGATION",
      "performedBy": {"actorId": "0192b2d0-7f1d-7abc-8d2c-123456789025", "displayLabel": "Quản lý B", "role": "MANAGER"},
      "reason": "Đã kiểm tra camera và đối chiếu ca",
      "occurredAt": "2026-10-13T08:15:00Z"
    }
  },
  "meta": {
    "requestId": "req_0192b2d0-7f1d-7abc-8d2c-123456789001",
    "correlationId": "cor_0192b2d0-7f1d-7abc-8d2c-123456789002",
    "timestamp": "2026-10-13T08:15:01Z"
  }
}
```

---

### 4.5 Thao tác xuất dữ liệu (Export Operation)

#### 4.5.1 Khởi tạo tác vụ xuất dữ liệu
`POST /api/v1/audit/exports`

#### Tiêu đề Yêu cầu (Headers)
```http
Authorization: Bearer <Cognito JWT Token>
Content-Type: application/json
Accept: application/json
X-Correlation-ID: 0192b2d0-7f1d-7abc-8d2c-123456789001
Idempotency-Key: 0192b2d0-7f1d-7abc-8d2c-123456789006
```

#### Request Body Mẫu
```json
{
  "branchIds": ["0192b2d0-7f1d-7abc-8d2c-123456789002"],
  "from": "2021-01-01T00:00:00Z",
  "to": "2026-10-13T00:00:00Z",
  "dataClasses": ["AUDIT_EVIDENCE", "CASE_ACTIONS"],
  "format": "JSONL",
  "reason": "Kiểm tra nội bộ định kỳ"
}
```

Quy tắc: tối đa 7 năm, chỉ định rõ phạm vi, đánh giá lưu trữ pháp lý, bắt buộc có `Idempotency-Key`, trả về `202 Accepted` kèm `operationId`/`pollUrl`.

#### Phản hồi Tiếp Nhận `202 Accepted`
```json
{
  "data": {
    "operationId": "0192b2d0-7f1d-7abc-8d2c-123456789120",
    "status": "PROCESSING",
    "pollUrl": "/api/v1/audit/exports/0192b2d0-7f1d-7abc-8d2c-123456789120",
    "pollAfterMs": 2000,
    "createdAt": "2026-10-13T08:20:00Z"
  },
  "meta": {
    "requestId": "req_0192b2d0-7f1d-7abc-8d2c-123456789001",
    "correlationId": "cor_0192b2d0-7f1d-7abc-8d2c-123456789002",
    "timestamp": "2026-10-13T08:20:00Z"
  }
}
```

#### 4.5.2 Kiểm tra trạng thái xuất dữ liệu
`GET /api/v1/audit/exports/{operationId}`

Khi hoàn tất trả về tham chiếu tải xuống an toàn có thời hạn và mã checksum SHA-256, tuyệt đối không cung cấp URL bucket công khai.

#### Phản hồi Thành Công `200 OK` (Khi hoàn tất)
```json
{
  "data": {
    "operationId": "0192b2d0-7f1d-7abc-8d2c-123456789120",
    "status": "COMPLETED",
    "downloadUrl": "https://audit-export-store.s3.amazonaws.com/exports/0192.../audit.jsonl.gz?AWSAccessKeyId=...",
    "expiresAt": "2026-10-14T08:20:00Z",
    "checksumSha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    "rowCount": 14250,
    "completedAt": "2026-10-13T08:22:15Z"
  },
  "meta": {
    "requestId": "req_0192b2d0-7f1d-7abc-8d2c-123456789001",
    "correlationId": "cor_0192b2d0-7f1d-7abc-8d2c-123456789002",
    "timestamp": "2026-10-13T08:22:16Z"
  }
}
```

---

### 4.6 Bảng phân loại mã lỗi chung

| HTTP | Mã lỗi | Ý nghĩa | Retry |
|---:|---|---|---|
| 200 | — | Thành công, bao gồm cả trạng thái `NO_DATA`/chất lượng dữ liệu | không |
| 202 | — | Tác vụ xuất file / rebuild / kiểm tra toàn vẹn đã được tiếp nhận | thực hiện poll |
| 304 | — | Kết quả điều kiện hợp lệ không thay đổi | không |
| 401 | `unauthorized` | JWT / workload credential không hợp lệ | làm mới token |
| 403 | `forbidden` | Quyền hạn / vai trò không được phép | không |
| 404 | `not_found` | Tài nguyên không hiển thị hoặc không tồn tại | không |
| 409 | `scope_not_allowed` | Phạm vi yêu cầu nằm ngoài phạm vi hiện hành | không |
| 409 | `case_version_conflict` | Xung đột phiên bản vụ việc do cập nhật đồng thời | lấy lại dữ liệu |
| 409 | `idempotency_conflict` | Cùng idempotency key nhưng request khác nhau | không |
| 409 | `audit_not_ready` | Projection / nguồn bắt buộc chưa hoàn tất | retry có giới hạn |
| 422 | `invalid_audit_filter` | Khoảng thời gian/bộ lọc/hành động/giới hạn không hợp lệ | không |
| 422 | `retention_policy_violation` | Xung đột khoảng thời gian / loại dữ liệu / lưu trữ pháp lý | không |
| 429 | `rate_limited` | Vượt giới hạn người dùng/tenant/xuất file | Retry-After |
| 503 | `audit_unavailable` | DB/projection/object storage không khả dụng | retry có giới hạn |

## 5. Hợp Đồng Vận Hành Nội Bộ (Internal Operational Contracts)

### 5.1 Kiểm tra sức khỏe (Health)

`GET /internal/v1/audit/health` yêu cầu workload identity và trả về trạng thái theo từng nguồn/projection:

```json
{
  "data": {
    "sources": [{
      "sourceName": "order-service",
      "projectionName": "audit_event_projection",
      "watermark": "order:v901",
      "watermarkVersion": 901,
      "freshnessStatus": "FRESH",
      "completenessStatus": "COMPLETE",
      "lagSeconds": 12,
      "dlqCount": 0,
      "lastErrorCode": null
    }]
  }
}
```

Tuyệt đối không trả về payload hàng đợi thô hay chi tiết lỗi nhạy cảm.

### 5.2 Rebuild và phát lại dữ liệu

`POST /internal/v1/audit/rebuilds`:

```json
{
  "tenantId": "0192...",
  "branchIds": ["0192..."],
  "projectionNames": ["audit_event_projection", "anomaly_baseline_projection"],
  "from": "2026-10-01T00:00:00Z",
  "to": "2026-10-13T00:00:00Z",
  "sourceWatermarkFrom": "audit:v800",
  "sourceWatermarkTo": "audit:v901",
  "reasonCode": "PROJECTION_REPAIR"
}
```

Quy tắc: chỉ định phạm vi rõ ràng, khoảng thời gian có giới hạn, projection thuộc allowlist, workload có quyền `audit:operate`, bắt buộc có `Idempotency-Key`, cùng request trả về cùng operation ID, khác request cùng key trả về `409 idempotency_conflict`, bằng chứng và digest bất biến không bao giờ bị viết lại.

### 5.3 Kiểm tra tính toàn vẹn (Integrity Check)

`POST /internal/v1/audit/integrity-checks` bắt đầu một tác vụ có giữ lease trên lô digest / phạm vi / khoảng ngày chỉ định. Phản hồi `202` bao gồm `operationId`, lô mục tiêu, phiên bản chuẩn hóa và URL thăm dò (poll URL). Các trạng thái xác minh: `REQUESTED`, `RUNNING`, `VERIFIED`, `MISMATCH`, `FAILED`, `CANCELLED`.

## 6. Hợp Đồng Sự Kiện (Event Contracts)

### 6.1 Phong bì sự kiện chung

```json
{
  "eventId": "0192b2d0-7f1d-7abc-8d2c-123456789050",
  "eventType": "DiscountApplied.v1",
  "eventVersion": 1,
  "occurredAt": "2026-10-13T08:00:05Z",
  "tenantId": "0192...",
  "branchId": "0192...",
  "aggregateType": "Order",
  "aggregateId": "0192...",
  "aggregateVersion": 18,
  "sourceEventId": "0192...",
  "sourceVersion": 18,
  "correlationId": "0192...",
  "causationId": "0192...",
  "producer": "order-service",
  "redactionVersion": "audit-redaction-v1",
  "payload": {}
}
```

Các bất biến bắt buộc:

- `eventId` ổn định qua các lần retry/phát lại; `(producer, sourceEventId)` là duy nhất phục vụ dedupe ở nguồn.
- `tenantId` là bắt buộc; `branchId` là bắt buộc đối với các sự kiện POS theo chi nhánh.
- `sourceVersion` là phiên bản aggregate/producer, không phải thứ tự đến tại hàng đợi.
- Audit chỉ gửi ACK sau khi transaction lưu inbox / bằng chứng / projection / watermark commit thành công.
- Phiên bản sự kiện lạ hoặc làm sạch dữ liệu thất bại sẽ bị cách ly / chuyển vào DLQ.
- Việc phát lại bảo toàn nguyên vẹn ID gốc, thời điểm occurredAt, correlation/causation và phiên bản nguồn.

### 6.2 Các sự kiện nguồn chuẩn tắc do Audit tiêu thụ

#### `OrderLineCancelled.v1` / `OrderCancelled.v1`

```json
{
  "orderId": "0192...",
  "lineId": "0192...",
  "orderVersion": 8,
  "cancellationType": "LINE",
  "reason": "Khách đổi món",
  "actor": {},
  "effectiveOperator": {},
  "beforeSummary": {"lineState": "ACTIVE", "quantity": 1},
  "afterSummary": {"lineState": "CANCELLED", "quantity": 0},
  "outcome": "SUCCEEDED"
}
```

#### `DiscountApplied.v1`

```json
{
  "orderId": "0192...",
  "lineId": "0192...",
  "orderVersion": 18,
  "discountSource": "MANUAL_OVERRIDE",
  "promotionVersionId": null,
  "voucherVersionId": null,
  "ruleVersion": null,
  "beforeAmount": {"amountMinor": 50000, "currency": "VND"},
  "discountAmount": {"amountMinor": 5000, "currency": "VND"},
  "afterAmount": {"amountMinor": 45000, "currency": "VND"},
  "allocationStatus": "ALLOCATED",
  "reason": "Khách được hỗ trợ",
  "actor": {},
  "effectiveOperator": {},
  "outcome": "SUCCEEDED"
}
```

Sự kiện khuyến mãi/voucher tự động bắt buộc bảo toàn phiên bản/phân bổ bất biến. Ghi đè thủ công bắt buộc có lý do không rỗng.

#### `CashDrawerOpened.v1`

```json
{
  "operationKey": "0192...",
  "deviceId": "0192...",
  "shiftId": "0192...",
  "trigger": "NO_SALE",
  "requestedAt": "2026-10-13T08:00:00Z",
  "resultAt": "2026-10-13T08:00:01Z",
  "hardwareResult": "OPENED",
  "actor": {},
  "effectiveOperator": {},
  "outcome": "SUCCEEDED"
}
```

`operationKey` ổn định qua các lần retry lệnh/kết quả. Lỗi phần cứng sử dụng `FAILED`; kết quả đến trễ liên kết với cùng operation key và không thể gây tính trùng.

#### `AuthorizationDenied.v1`

Chứa `permission`, loại tài nguyên an toàn `resourceType`, `resourceId` mờ (nếu an toàn), tóm tắt phạm vi yêu cầu, tham chiếu tác nhân/workload, mã từ chối và kết quả. Tuyệt đối không chứa JWT, header request thô hay chi tiết tài nguyên ngoại lai.

### 6.3 Sự kiện tương thích chung `AuditRecorded.v1`

`AuditRecorded.v1` chỉ là nguồn chuẩn tắc cho các sự kiện bảo mật/vận hành chung không có sự kiện nguồn chuyên biệt tương ứng. Nó bắt buộc phải chứa `sourceEventId`, `sourceEventType`, `sourceVersion`, phong bì chung và hành động đã chuẩn hóa. Registry producer sẽ từ chối sự kiện `AuditRecorded.v1` thứ hai nếu thao tác đó đã được biểu diễn bởi một sự kiện chuẩn tắc chuyên biệt.

### 6.4 Các sự kiện do Audit phát hành

| Sự kiện | Payload tối thiểu | Consumer |
|---|---|---|
| `AuditEvidenceRecorded.v1` | ID bằng chứng, hành động, tenant/chi nhánh, tham chiếu nguồn, tóm tắt an toàn, chất lượng/phiên bản | Reporting, Ops, các bên hạ nguồn được phép |
| `AuditDataQualityChanged.v1` | phạm vi/nguồn/projection, trạng thái cũ/mới, độ trễ, nguồn thiếu, watermark | Ops, gợi ý trên dashboard |
| `AnomalyFindingCreated.v1` | ID phát hiện/vụ việc, quy tắc/phiên bản, đối tượng/chi nhánh, mức nghiêm trọng, ref bằng chứng, mã giải thích | Console, Ops |
| `AnomalyFindingRevised.v1` | ID/phiên bản phát hiện, thay đổi bằng chứng/baseline/chất lượng dữ liệu, lý do | Console, Ops |
| `AuditCaseActionRecorded.v1` | vụ việc/phát hiện, hành động, trạng thái cũ/mới, snapshot tác nhân, lý do, phiên bản vụ việc | Audit, Ops |
| `AuditIntegrityMismatchDetected.v1` | lô digest, phạm vi, thuật toán/phiên bản, tham chiếu digest kỳ vọng/thực tế, operation ID | Bảo mật, Ops |
| `AuditExportCompleted.v1` | operation ID, tóm tắt phạm vi, loại dữ liệu, mã checksum, thời hạn hết hạn, tham chiếu audit | Ops, client được phân quyền |

Payload của các sự kiện phát hành tuyệt đối không chứa nội dung sự kiện kiểm toán thô, PII, secret hay lệnh phân quyền trực tiếp.

## 7. Hợp Đồng Dữ Liệu PostgreSQL

Cơ sở dữ liệu thuộc sở hữu độc quyền của Audit service. Các khóa ID là UUID v7 do ứng dụng sinh ra. Mọi cột bắt buộc phải có `COMMENT ON COLUMN` khớp với mô tả bên dưới.

### 7.1 `audit_events` — bằng chứng bất biến

| Cột | Kiểu | Null | Mô tả |
|---|---|---:|---|
| `id` | UUID | không | ID bằng chứng kiểm toán bất biến đã chuẩn hóa |
| `tenant_id` | UUID | không | Tenant sở hữu |
| `branch_id` | UUID | có | Phạm vi chi nhánh; null chỉ dành cho dữ kiện bảo mật toàn tenant |
| `event_type` | TEXT | không | Loại sự kiện nguồn chuẩn tắc / sự kiện chung |
| `event_version` | INTEGER | không | Phiên bản schema sự kiện |
| `action` | TEXT | không | Hành động đã chuẩn hóa thuộc danh mục cho phép |
| `outcome` | TEXT | không | `SUCCEEDED`, `REJECTED`, `FAILED`, `NO_OP` |
| `aggregate_type` | TEXT | không | Nhóm aggregate nguồn |
| `aggregate_id` | UUID | có | Tham chiếu mờ của aggregate nguồn |
| `aggregate_version` | BIGINT | có | Phiên bản aggregate nguồn |
| `source_name` | TEXT | không | Ranh giới service producer / thiết bị |
| `source_event_id` | UUID | không | ID sự kiện producer ổn định |
| `source_version` | BIGINT | có | Checkpoint producer / nguồn |
| `actor_key` | TEXT | có | Tham chiếu tác nhân mờ |
| `actor_snapshot` | JSONB | có | Snapshot tác nhân / vai trò lịch sử an toàn |
| `effective_operator_key` | TEXT | có | Thu ngân thao tác POS thực tế mờ |
| `effective_operator_snapshot` | JSONB | có | Snapshot thu ngân thao tác lịch sử an toàn |
| `device_id` | UUID | có | Tham chiếu thiết bị POS |
| `shift_id` | UUID | có | Tham chiếu ca làm việc |
| `resource_type` | TEXT | không | Order / dòng món / thanh toán / ca / thiết bị / vụ việc |
| `resource_id` | UUID | có | Tham chiếu tài nguyên đã kiểm tra phạm vi |
| `reason` | TEXT | có | Lý do đã cắt tỉa và làm sạch |
| `before_summary` | JSONB | có | Tóm tắt trạng thái trước đã làm sạch và giới hạn độ dài |
| `after_summary` | JSONB | có | Tóm tắt trạng thái sau đã làm sạch và giới hạn độ dài |
| `money_summary` | JSONB | có | Tóm tắt số tiền VND minor-unit / phân bổ |
| `quality_code` | TEXT | có | Cảnh báo chất lượng về tác nhân/kích hoạt/phân bổ/nguồn |
| `correlation_id` | UUID | không | Tham chiếu truy vết phân tán |
| `causation_id` | UUID | có | Tham chiếu lệnh / sự kiện nguyên nhân |
| `redaction_version` | TEXT | không | Phiên bản chính sách làm sạch dữ liệu |
| `normalization_version` | TEXT | không | Phiên bản chuẩn hóa của Audit |
| `occurred_at` | TIMESTAMPTZ | không | Thời điểm phát sinh nghiệp vụ UTC |
| `created_at` | TIMESTAMPTZ | không | Thời điểm lưu trữ vào Audit UTC |

Tuyệt đối không cấp quyền `UPDATE`/`DELETE` cho các role ứng dụng trên các dòng dữ liệu lịch sử này.

### 7.2 `audit_event_projection`

| Cột | Kiểu | Null | Mô tả |
|---|---|---:|---|
| `id` | UUID | không | ID dòng projection / bằng chứng |
| `tenant_id` | UUID | không | Tenant sở hữu |
| `branch_id` | UUID | có | Phạm vi chi nhánh |
| `occurred_at` | TIMESTAMPTZ | không | Thời gian dùng để sắp xếp và lọc |
| `action` | TEXT | không | Bộ lọc hành động đã chuẩn hóa |
| `outcome` | TEXT | không | Bộ lọc kết quả |
| `actor_key` | TEXT | có | Bộ lọc tác nhân an toàn theo phạm vi |
| `actor_label` | TEXT | có | Nhãn tác nhân lịch sử an toàn |
| `effective_operator_key` | TEXT | có | Bộ lọc thu ngân thao tác an toàn theo phạm vi |
| `effective_operator_label` | TEXT | có | Nhãn thu ngân thao tác lịch sử an toàn |
| `resource_type` | TEXT | không | Bộ lọc loại tài nguyên |
| `resource_id` | UUID | có | Tham chiếu tài nguyên |
| `shift_id` | UUID | có | Bộ lọc ca làm việc |
| `device_id` | UUID | có | Bộ lọc thiết bị |
| `summary_json` | JSONB | không | Tóm tắt hiển thị an toàn có giới hạn độ dài |
| `quality_code` | TEXT | có | Cảnh báo chất lượng bằng chứng |
| `projection_version` | BIGINT | không | Phiên bản projection rebuild hiện hành |
| `updated_at` | TIMESTAMPTZ | không | Thời điểm UTC cập nhật projection |

Bảng này có thể tái tạo (rebuildable); `audit_events` luôn là nguồn bằng chứng gốc.

### 7.3 `inbox_events` và `projection_watermarks`

`inbox_events`:

| Cột | Kiểu | Null | Mô tả |
|---|---|---:|---|
| `id` | UUID | không | ID dòng inbox cục bộ |
| `tenant_id` | UUID | không | Tenant sở hữu |
| `source_name` | TEXT | không | Producer / nguồn |
| `source_event_id` | UUID | không | ID dedupe toàn cục |
| `source_key` | TEXT | có | Khóa thao tác / két tiền khi phù hợp |
| `event_type` | TEXT | không | Tên schema sự kiện nguồn |
| `event_version` | INTEGER | không | Phiên bản schema nguồn |
| `source_version` | BIGINT | có | Phiên bản producer |
| `status` | TEXT | không | `RECEIVED`, `VALIDATING`, `APPLIED`, `IGNORED_DUPLICATE`, `RETRY_WAIT`, `DLQ` |
| `error_code` | TEXT | có | Mã lỗi xử lý an toàn |
| `received_at` | TIMESTAMPTZ | không | Thời điểm đến hàng đợi |
| `processed_at` | TIMESTAMPTZ | có | Thời điểm hoàn tất xử lý |

`projection_watermarks`:

| Cột | Kiểu | Null | Mô tả |
|---|---|---:|---|
| `id` | UUID | không | ID dòng watermark |
| `tenant_id` | UUID | không | Tenant sở hữu |
| `scope_key` | TEXT | không | Phạm vi tenant/chi nhánh |
| `source_name` | TEXT | không | Nguồn sự kiện |
| `projection_name` | TEXT | không | Projection / worker |
| `watermark` | TEXT | không | Checkpoint mờ |
| `watermark_version` | BIGINT | không | Checkpoint đơn điệu tăng |
| `projection_version` | BIGINT | không | Phiên bản projection cụ thể hóa |
| `freshness_status` | TEXT | không | Trạng thái chất lượng độ tươi mới |
| `completeness_status` | TEXT | không | `COMPLETE`, `PARTIAL`, `MISSING` |
| `missing_sources` | JSONB | không | Danh sách tên nguồn bị thiếu an toàn |
| `last_updated_at` | TIMESTAMPTZ | không | Thời điểm UTC cập nhật checkpoint |

### 7.4 `digest_batches`

| Cột | Kiểu | Null | Mô tả |
|---|---|---:|---|
| `id` | UUID | không | ID lô digest |
| `tenant_id` | UUID | không | Tenant sở hữu |
| `scope_key` | TEXT | không | Phạm vi tenant/chi nhánh |
| `from_occurred_at` | TIMESTAMPTZ | không | Mốc bắt đầu lô (bao gồm) |
| `to_occurred_at` | TIMESTAMPTZ | không | Mốc kết thúc lô (không bao gồm) |
| `first_event_id` | UUID | có | ID bằng chứng có sắp xếp đầu tiên |
| `last_event_id` | UUID | có | ID bằng chứng có sắp xếp cuối cùng |
| `event_count` | BIGINT | không | Số lượng dòng bằng chứng chuẩn tắc |
| `algorithm` | TEXT | không | MVP là `SHA-256` |
| `canonicalization_version` | TEXT | không | Phiên bản trường/tuần tự hóa chuẩn tắc |
| `previous_digest` | TEXT | có | Mã digest của lô trước trong cùng phạm vi |
| `digest` | TEXT | không | Tham chiếu mã băm digest kỳ vọng dạng hex/base64 |
| `source_watermark` | TEXT | không | Checkpoint bằng chứng |
| `verification_status` | TEXT | không | `UNVERIFIED`, `VERIFIED`, `MISMATCH`, `FAILED` |
| `verified_at` | TIMESTAMPTZ | có | Thời điểm xác minh gần nhất |
| `created_at` | TIMESTAMPTZ | không | Thời điểm tạo lô |

### 7.5 `audit_policies` và `anomaly_rule_versions`

`audit_policies` lưu các phiên bản chính sách bất biến về làm sạch dữ liệu, lưu trữ, phân quyền và truy vấn:

| Cột | Kiểu | Null | Mô tả |
|---|---|---:|---|
| `id` | UUID | không | ID phiên bản chính sách |
| `tenant_id` | UUID | không | Tenant sở hữu |
| `policy_type` | TEXT | không | `REDACTION`, `RETENTION`, `QUERY`, `INTEGRITY` |
| `version` | BIGINT | không | Phiên bản bất biến |
| `status` | TEXT | không | `DRAFT`, `ACTIVE`, `RETIRED` |
| `policy_json` | JSONB | không | Cấu hình chính sách có giới hạn và hợp lệ |
| `published_by` | UUID | có | Người ban hành Owner / vận hành |
| `published_at` | TIMESTAMPTZ | có | Thời điểm ban hành |
| `reason` | TEXT | có | Lý do ban hành được kiểm toán |

`anomaly_rule_versions`:

| Cột | Kiểu | Null | Mô tả |
|---|---|---:|---|
| `id` | UUID | không | ID phiên bản quy tắc |
| `tenant_id` | UUID | không | Tenant sở hữu |
| `branch_id` | UUID | có | Null đối với cấu hình mặc định toàn tenant |
| `rule_family` | TEXT | không | `CANCEL_SPIKE`, `DISCOUNT_SPIKE`, `DRAWER_FREQUENCY`, `REPEATED_ACTION` |
| `version` | BIGINT | không | Phiên bản quy tắc bất biến |
| `status` | TEXT | không | `DRAFT`, `ACTIVE`, `RETIRED` |
| `window_json` | JSONB | không | Múi giờ / cửa sổ quan sát và so sánh |
| `baseline_json` | JSONB | không | Các chiều / khởi động / kích thước mẫu tối thiểu |
| `threshold_json` | JSONB | không | Ngưỡng đơn định / ánh xạ mức độ nghiêm trọng |
| `suppression_json` | JSONB | không | Chính sách giảm nhiễu / gom nhóm / hạ nhiệt |
| `published_by` | UUID | có | Tác nhân chính sách / Owner |
| `published_at` | TIMESTAMPTZ | có | Thời điểm ban hành |
| `reason` | TEXT | có | Lý do ban hành quy tắc được kiểm toán |

### 7.6 `anomaly_baselines`

| Cột | Kiểu | Null | Mô tả |
|---|---|---:|---|
| `id` | UUID | không | ID dòng đường cơ sở |
| `tenant_id` | UUID | không | Tenant sở hữu |
| `branch_id` | UUID | không | Phạm vi chi nhánh |
| `subject_key` | TEXT | có | Thu ngân thao tác mờ; null cho baseline toàn chi nhánh |
| `action_family` | TEXT | không | Nhóm hành động: hủy / giảm giá / két tiền / hành động lặp |
| `local_time_bucket` | SMALLINT | không | Khung giờ / buổi cục bộ theo chi nhánh |
| `window_start` | DATE | không | Ngày bắt đầu khoảng baseline |
| `window_end` | DATE | không | Ngày kết thúc khoảng baseline |
| `sample_count` | BIGINT | không | Mẫu số / kích thước mẫu hợp lệ |
| `mean_value` | NUMERIC(20,8) | không | Giá trị trung bình của baseline |
| `stddev_value` | NUMERIC(20,8) | không | Độ lệch chuẩn của baseline |
| `baseline_status` | TEXT | không | `READY`, `WARMING`, `INSUFFICIENT`, `PARTIAL` |
| `source_watermark` | TEXT | không | Dữ kiện đã sử dụng |
| `rule_version` | BIGINT | không | Phiên bản chính sách quy tắc |
| `updated_at` | TIMESTAMPTZ | không | Thời điểm cụ thể hóa |

### 7.7 `anomaly_findings` và `case_actions`

`anomaly_findings`:

| Cột | Kiểu | Null | Mô tả |
|---|---|---:|---|
| `id` | UUID | không | ID phát hiện |
| `case_id` | UUID | không | ID aggregate vụ việc |
| `tenant_id` | UUID | không | Tenant sở hữu |
| `branch_id` | UUID | không | Chi nhánh phát sinh phát hiện |
| `subject_key` | TEXT | có | Nhân viên / thu ngân thao tác mờ |
| `rule_family` | TEXT | không | Nhóm quy tắc |
| `rule_version_id` | UUID | không | Tham chiếu phiên bản quy tắc bất biến |
| `finding_key` | TEXT | không | Khóa dedupe / bản chỉnh sửa đơn định |
| `observation_from` | TIMESTAMPTZ | không | Mốc bắt đầu cửa sổ bằng chứng |
| `observation_to` | TIMESTAMPTZ | không | Mốc kết thúc cửa sổ bằng chứng |
| `baseline_id` | UUID | có | Tham chiếu đường cơ sở |
| `severity` | TEXT | không | `LOW`, `MEDIUM`, `HIGH`, `CRITICAL` |
| `state` | TEXT | không | `OPEN`, `ACKNOWLEDGED`, `INVESTIGATING`, `RESOLVED`, `DISMISSED` |
| `metric_json` | JSONB | không | Các giá trị hiện tại / baseline / mẫu / tỷ lệ |
| `explanation_code` | TEXT | không | Mã giải thích đơn định an toàn |
| `evidence_refs` | JSONB | không | Danh sách ID / phiên bản sự kiện thuộc allowlist |
| `data_quality_status` | TEXT | không | Trạng thái: tươi mới / bán phần / chưa đủ baseline |
| `revision` | BIGINT | không | Bản chỉnh sửa phát hiện đơn điệu tăng |
| `version` | BIGINT | không | Phiên bản kiểm tra CAS |
| `detected_at` | TIMESTAMPTZ | không | Thời điểm phát hiện |
| `updated_at` | TIMESTAMPTZ | không | Thời điểm cập nhật con trỏ hiện hành |

`case_actions`:

| Cột | Kiểu | Null | Mô tả |
|---|---|---:|---|
| `id` | UUID | không | ID hành động vụ việc |
| `case_id` | UUID | không | Aggregate phát hiện / vụ việc |
| `tenant_id` | UUID | không | Tenant sở hữu |
| `branch_id` | UUID | không | Chi nhánh của vụ việc |
| `action` | TEXT | không | `CREATED`, `ACKNOWLEDGED`, `INVESTIGATION_UPDATED`, `RESOLVED`, `DISMISSED`, `REOPENED` |
| `from_state` | TEXT | có | Trạng thái trước đó |
| `to_state` | TEXT | không | Trạng thái mới |
| `actor_snapshot` | JSONB | không | Snapshot tác nhân thao tác an toàn |
| `reason` | TEXT | không | Lý do chuyển trạng thái có giới hạn |
| `note` | TEXT | có | Ghi chú điều tra đã làm sạch và giới hạn độ dài |
| `evidence_version` | BIGINT | có | Phiên bản bằng chứng được sử dụng |
| `case_version` | BIGINT | không | Phiên bản vụ việc sau chuyển đổi |
| `correlation_id` | UUID | không | Tham chiếu truy vết |
| `created_at` | TIMESTAMPTZ | không | Thời điểm chuyển trạng thái bất biến |

### 7.8 `audit_exports`, `legal_holds`, `retention_operations`

| Bảng | Các cột bắt buộc và mô tả |
|---|---|
| `audit_exports` | `id` operation ID; `tenant_id`; `scope_key`; khoảng ngày `from/to`; danh sách `data_classes`; `format`; `status`; `object_key`; mã băm `checksum`; `expires_at`; `idempotency_key`; `audit_reference_id`; các mốc thời gian |
| `legal_holds` | `id`; `tenant_id`; `scope_key`; `data_classes`; khoảng ngày `from/to`; `status`; `reason`; `requested_by`; `approved_by`; `active_at`; `released_at`; các mốc thời gian |
| `retention_operations` | `id`; `tenant_id`; `scope_key`; `data_class`; phân vùng/cửa sổ `partition/window`; thao tác `operation`; quyết định lưu trữ `hold_decision`; `status`; số dòng `row_count`; tham chiếu archive `archive_ref`; `error_code`; tác nhân/workload; các mốc thời gian |

## 8. Ràng Buộc, Chỉ Mục và Tính Toàn Vẹn

### 8.1 Các ràng buộc bắt buộc

- Mọi dòng bằng chứng, phát hiện, vụ việc, xuất file, lệnh lưu trữ đều theo phạm vi tenant; phạm vi chi nhánh là bắt buộc trừ khi sự kiện được chỉ định rõ là cấp toàn tenant / sự kiện bảo mật.
- Các trường `event_type`, `action`, `outcome`, `rule_family`, `state`, `severity`, `data_quality_status` sử dụng enum / ràng buộc check thuộc danh mục cho phép.
- `source_event_id` và `source_key` (khi áp dụng) là duy nhất trong phạm vi producer/tenant.
- `source_version`/`watermark_version` không bao giờ giảm cho cùng một bộ nguồn/phạm vi/projection.
- Các bảng lịch sử `audit_events` và `case_actions` không có quyền UPDATE/DELETE cho ứng dụng; tác vụ retention sử dụng phân vùng/archive dưới quyền workload hạn chế.
- `currency='VND'` cho tóm tắt tiền tệ trong MVP; mọi số tiền minor unit đều không âm; bất biến phân bổ là tường minh.
- Khóa phát hiện (finding key) là duy nhất trên `(tenant_id, branch_id, finding_key, revision)`; chỉ duy nhất một con trỏ hiện hành cho mỗi case.
- Chuyển trạng thái vụ việc chỉ hợp lệ theo đúng đồ thị trạng thái và phiên bản kỳ vọng CAS.
- Chỉ có 1 phiên bản quy tắc kích hoạt duy nhất cho mỗi `(tenant_id, branch_id/default, rule_family)`; các phiên bản đã ban hành là bất biến.
- Lưu trữ pháp lý (legal hold) chặn hoàn toàn việc archive/xóa đối với các khoảng thời gian/tenant/loại dữ liệu chồng lấn.
- Lô digest có khoảng thời gian đơn định có thứ tự và là duy nhất trên `(tenant_id, scope_key, from, to, canonicalization_version)`.

### 8.2 Các chỉ mục bắt buộc

| Tên chỉ mục | Các cột | Mục đích |
|---|---|---|
| `idx_audit_timeline` | tenant, branch, occurred_at DESC, id DESC | Dòng thời gian 90 ngày |
| `idx_audit_action_time` | tenant, branch, action, occurred_at DESC | Điều tra theo hành động |
| `idx_audit_actor_time` | tenant, branch, actor_key, occurred_at DESC | Kiểm toán theo nhân viên |
| `idx_audit_operator_time` | tenant, branch, effective_operator_key, occurred_at DESC | Trách nhiệm đổi ca nhanh |
| `idx_audit_shift_device` | tenant, branch, shift_id, device_id, occurred_at DESC | Điều tra két tiền / ca làm việc |
| `uq_audit_source_event` | source_name, source_event_id | Dedupe tại nguồn |
| `idx_inbox_status` | tenant, status, received_at | Vận hành retry / DLQ |
| `idx_watermark_health` | tenant, scope_key, projection_name, last_updated_at | Cảnh báo độ tươi mới |
| `idx_findings_queue` | tenant, branch, state, severity, detected_at DESC | Hàng đợi vụ việc |
| `idx_findings_subject` | tenant, branch, subject_key, detected_at DESC | Phát hiện theo nhân viên |
| `uq_finding_key` | tenant, branch, finding_key, revision | Tính lũy kế của phát hiện |
| `idx_case_actions` | tenant, branch, case_id, created_at DESC | Dòng thời gian vụ việc |
| `idx_digest_verify` | tenant, scope_key, verification_status, created_at | Worker kiểm tra toàn vẹn |
| `idx_exports_status` | tenant, status, created_at | Worker xuất file |
| `idx_holds_range` | tenant, scope_key, status, active_at, released_at | Đánh giá lưu trữ pháp lý |

## 9. Hợp Đồng Digest và Chuẩn Hóa Bản Ghi (Canonicalization)

### 9.1 Bản ghi chuẩn tắc (Canonical Record)

Digest chỉ bao gồm các trường thuộc allowlist từ bảng `audit_events`, theo đúng thứ tự sau:

```text
tenantId, branchId, id, eventType, eventVersion, action, outcome,
aggregateType, aggregateId, aggregateVersion, sourceName, sourceEventId,
sourceVersion, actorKey, effectiveOperatorKey, deviceId, shiftId,
resourceType, resourceId, reasonHash, beforeSummaryCanonical,
afterSummaryCanonical, moneySummaryCanonical, qualityCode, correlationId,
causationId, redactionVersion, normalizationVersion, occurredAt, createdAt
```

- Phiên bản chuẩn hóa: `canonicalizationVersion=audit-c14n-v1`.
- Các key của JSON object được sắp xếp theo bảng chữ cái; các trường nullable bị vắng mặt được gán `null` tường minh; các mảng được sắp xếp theo khóa ổn định quy định trong schema.
- Trường `reason` được đại diện bằng mã hash/tham chiếu an toàn có gắn khóa, không bao giờ đưa văn bản tự do thô vào payload của digest.
- Các sự kiện được sắp xếp theo `(occurred_at ASC, id ASC)` bên trong một lô.
- Thuật toán băm là SHA-256 trên các bản ghi chuẩn tắc có phân tách độ dài, sau đó liên kết chuỗi với `previousDigest` cho cùng một phạm vi.

### 9.2 Xác minh tính toàn vẹn (Verification)

Worker xác minh tính toán lại một lô có giới hạn, so sánh mã digest, số lượng sự kiện cùng ID đầu/cuối, ghi nhận bản ghi xác minh và phát hành sự kiện `AuditIntegrityMismatchDetected.v1` nếu phát hiện không khớp. Quá trình xác minh tuyệt đối không bao giờ tự ý sửa chữa bằng cách chỉnh sửa bằng chứng.

## 10. Hợp Đồng Chính Sách Quy Tắc Bất Thường (Anomaly Rule Policy)

### 10.1 Schema chính sách quy tắc

```json
{
  "ruleFamily": "DISCOUNT_SPIKE",
  "version": 1,
  "status": "ACTIVE",
  "timezonePolicy": "BRANCH_LOCAL",
  "observationWindow": "BUSINESS_DAY",
  "comparisonWindow": "ROLLING_28_COMPARABLE_DAYS",
  "dimensions": ["TENANT", "BRANCH", "EFFECTIVE_OPERATOR", "LOCAL_DAYPART"],
  "minimumSample": 30,
  "warmupDays": 28,
  "threshold": {"minZScore": "3.0000", "minRatio": "3.0000"},
  "severityMap": {"3": "MEDIUM", "4": "HIGH", "5": "CRITICAL"},
  "suppressionMinutes": 1440,
  "qualityOnInsufficient": "INSUFFICIENT_BASELINE"
}
```

Các giá trị ngưỡng là cấu hình có quản lý phiên bản, không phải hằng số ẩn trong code. Một tenant/chi nhánh có thể ban hành phiên bản quy tắc mới; các phát hiện lịch sử vẫn giữ nguyên phiên bản chính sách đã sử dụng tại thời điểm đó.

### 10.2 Các nhóm quy tắc ban đầu

| Quy tắc | Giá trị hiện tại | Đường cơ sở (Baseline) | Kích hoạt / Bằng chứng |
|---|---|---|---|
| `CANCEL_SPIKE` | Số lượng / tỷ lệ / số tiền hủy theo ngày kinh doanh / buổi | Cuộn 28 ngày tương đương theo chi nhánh / thu ngân thao tác | Mẫu tối thiểu + `z >= minZScore` và ngưỡng tỷ lệ |
| `DISCOUNT_SPIKE` | Tỷ lệ / số tiền giảm giá thủ công; tách riêng khuyến mãi tự động | Cùng các chiều trên; phân bổ tiền VND | Mẫu tối thiểu + ngưỡng z/tỷ lệ; bằng chứng bao gồm nguồn/phiên bản |
| `DRAWER_FREQUENCY` | Số lần mở két thủ công / không bán hàng theo ca / buổi | Cửa sổ tương đương theo chi nhánh / thiết bị / thu ngân | Ngưỡng z/tuyệt đối hoặc mở ngoài ca đang hoạt động; thiếu ca trả về chất lượng thấp |
| `REPEATED_ACTION` | Cùng một nhóm hành động trên cùng đơn hàng / ca | Cửa sổ ngắn đơn định | Tối thiểu 3 hành động trong vòng 10 phút; gom nhóm theo khóa phát hiện |

Trạng thái `INSUFFICIENT_BASELINE` không bao giờ tạo phát hiện với độ tin cậy cao. Các dữ kiện trùng lặp / thất bại / bị từ chối / no-op được loại trừ khỏi tỷ lệ hành động thành công trừ khi chính sách quy tắc chỉ định đưa vào.

## 11. Hợp Đồng Máy Trạng Thái Vụ Việc (Case State Machine)

```text
OPEN ── acknowledge ──> ACKNOWLEDGED ── investigate ──> INVESTIGATING
  │                                                   │
  │                                                   ├─ Owner resolve ──> RESOLVED
  │                                                   ├─ Owner dismiss ──> DISMISSED
  │                                                   └─ Owner reopen ────> OPEN
  └─ Owner dismiss ─────────────────────────────────────────────────────> DISMISSED
```

| Bước chuyển | Tác nhân được phép | Dữ liệu bắt buộc |
|---|---|---|
| create → `OPEN` | Worker Loss | phát hiện đơn định / bằng chứng / phiên bản quy tắc |
| `OPEN` → `ACKNOWLEDGED` | Owner / Manager trong phạm vi | lý do, phiên bản CAS |
| `ACKNOWLEDGED` → `INVESTIGATING` | Owner / Manager trong phạm vi | lý do / ghi chú, phiên bản CAS |
| `INVESTIGATING` → `RESOLVED` | Chỉ riêng Owner | lý do / ghi chú giải quyết, phiên bản CAS |
| `OPEN/ACKNOWLEDGED/INVESTIGATING` → `DISMISSED` | Chỉ riêng Owner | lý do bác bỏ bắt buộc, phiên bản CAS |
| `RESOLVED/DISMISSED` → `OPEN` | Chỉ riêng Owner | lý do mở lại và bằng chứng / bản chỉnh sửa mới |

Mọi bước chuyển đổi đều chèn một bản ghi vào `case_actions`, tăng `version` của vụ việc hiện hành, phát sự kiện `AuditCaseActionRecorded.v1` qua outbox và bản thân nó hiển thị minh bạch trên dòng thời gian kiểm toán.

## 12. Hợp Đồng Lưu Trữ, Archive và Lưu Trữ Pháp Lý (Legal Hold)

| Phân loại dữ liệu | Lưu trữ tối thiểu | Quy tắc Archive / Xóa | Lưu trữ pháp lý (Legal hold) |
|---|---:|---|---|
| `AUDIT_EVIDENCE` | 7 năm | Phân vùng hàng tháng / archive; chỉ xóa sau khi hết hạn và không có lệnh hold | Lệnh hold luôn thắng |
| `CASE_ACTIONS` | 7 năm | Lưu trữ cùng phả hệ bằng chứng / vụ việc liên quan | Lệnh hold luôn thắng |
| `DIGEST_BATCHES` | 7 năm | Lưu trữ digest / checkpoint cùng cửa sổ bằng chứng | Lệnh hold luôn thắng |
| `FINDINGS` | 7 năm | Lưu trữ các bản chỉnh sửa cùng lý do bác bỏ / giải quyết | Lệnh hold luôn thắng |
| `EXPORT_ARTIFACT` | Mặc định 24 giờ sau khi hoàn tất | Object mã hóa tồn tại ngắn hạn, thu hồi/hết hạn; mặc định không giữ bản sao lâu dài | Lệnh hold chặn xóa nếu được chỉ định rõ |
| `INBOX/TELEMETRY` | 90 ngày hoặc theo chính sách vận hành | Xóa / archive sau khi kết thúc cửa sổ phát lại | Lệnh hold chỉ áp dụng nếu được vụ việc tham chiếu |
| `RULE/BASELINE_POLICY` | Tối thiểu 7 năm theo phả hệ bằng chứng | Các phiên bản đã ban hành là bất biến; chỉ ngừng sử dụng (retire), không xóa khi còn tham chiếu | Lệnh hold luôn thắng |

Lệnh lưu trữ pháp lý yêu cầu chỉ rõ tenant, phạm vi, loại dữ liệu, khoảng ngày, lý do và tác nhân; mang trạng thái `ACTIVE`/`RELEASED` và Manager không có quyền gỡ bỏ lệnh này. Các tác vụ dọn dẹp lưu trữ mang tính lũy kế và ghi nhận đầy đủ số dòng/phân vùng, quyết định hold, tham chiếu archive và kết quả.

## 13. Kế Hoạch Migration và Các Artifact Vận Hành

Thứ tự thực hiện Migration:

1. Tạo các enum tenant/phạm vi, các bảng chung về thao tác / idempotency / outbox / inbox và các role hạn chế.
2. Tạo bảng append-only `audit_events`, các ràng buộc dedupe nguồn, comment đầy đủ và chiến lược phân vùng theo tháng.
3. Tạo projection truy vấn, watermarks và các chỉ mục sức khỏe.
4. Tạo các bảng lưu lô digest / xác minh và phân quyền kiểm tra tính toàn vẹn.
5. Tạo các phiên bản chính sách audit / quy tắc, baselines, phát hiện, vụ việc và các ràng buộc thao tác vụ việc.
6. Tạo các bảng xuất file / lưu trữ pháp lý / tác vụ retention và các tham chiếu object storage được mã hóa.
7. Bổ sung các fixture tương thích producer cho Order, Promotion/Loyalty, Payment, Shift/POS Device và Identity.
8. Nạp dữ liệu mẫu chỉ cho các fixture chính sách quy tắc đơn định; các ngưỡng sản xuất yêu cầu ban hành / kiểm toán có phiên bản.
9. Kiểm tra hợp lệ các script Flyway migration trên cả database rỗng lẫn database sao chép; áp dụng chiến lược expand → migrate → contract cho các thay đổi tương lai.

### 13.1 Các kịch bản Migration Flyway (Flyway Migration Scripts)

#### 13.1.1 `V1__init_audit_core_and_events.sql`
```sql
CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    branch_id UUID,
    event_type TEXT NOT NULL,
    action TEXT NOT NULL CHECK (action IN ('ORDER_LINE_CANCELLED', 'ORDER_CANCELLED', 'DISCOUNT_APPLIED', 'CASH_DRAWER_OPENED', 'AUTHORIZATION_DENIED', 'CASE_ACTION')),
    outcome TEXT NOT NULL CHECK (outcome IN ('SUCCEEDED', 'REJECTED', 'FAILED', 'NO_OP')),
    actor JSONB NOT NULL,
    effective_operator JSONB,
    resource JSONB NOT NULL,
    shift_id UUID,
    pos_device_id UUID,
    reason TEXT,
    before_summary JSONB,
    after_summary JSONB,
    occurred_at TIMESTAMPTZ NOT NULL,
    source_event_id UUID NOT NULL,
    source_version BIGINT NOT NULL,
    correlation_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_audit_source_event UNIQUE (tenant_id, source_event_id, action)
) PARTITION BY RANGE (occurred_at);

COMMENT ON TABLE audit_events IS 'Sổ cái bất biến ghi nhận mọi sự kiện kiểm toán bảo mật và hành động nhạy cảm';
REVOKE UPDATE, DELETE ON audit_events FROM PUBLIC;

CREATE TABLE audit_events_default PARTITION OF audit_events DEFAULT;
```

#### 13.1.2 `V2__init_audit_projections_and_digests.sql`
```sql
CREATE TABLE audit_event_projections (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    branch_id UUID,
    action TEXT NOT NULL,
    outcome TEXT NOT NULL,
    actor_id UUID,
    effective_operator_id UUID,
    resource_type TEXT NOT NULL,
    resource_id UUID NOT NULL,
    shift_id UUID,
    occurred_at TIMESTAMPTZ NOT NULL,
    before_summary JSONB,
    after_summary JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_proj_tenant_branch ON audit_event_projections(tenant_id, branch_id, occurred_at DESC);
CREATE INDEX idx_audit_proj_action ON audit_event_projections(tenant_id, action, occurred_at DESC);
CREATE INDEX idx_audit_proj_operator ON audit_event_projections(tenant_id, effective_operator_id, occurred_at DESC);

CREATE TABLE digest_batches (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    batch_sequence BIGINT NOT NULL,
    from_occurred_at TIMESTAMPTZ NOT NULL,
    to_occurred_at TIMESTAMPTZ NOT NULL,
    event_count BIGINT NOT NULL,
    sha256_digest TEXT NOT NULL,
    previous_digest TEXT NOT NULL,
    verified_at TIMESTAMPTZ,
    status TEXT NOT NULL DEFAULT 'SEALED' CHECK (status IN ('SEALED', 'VERIFIED', 'CORRUPTED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_audit_digest_seq UNIQUE (tenant_id, batch_sequence)
);
```

#### 13.1.3 `V3__init_anomaly_rules_and_findings.sql`
```sql
CREATE TABLE anomaly_rule_versions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    rule_family TEXT NOT NULL CHECK (rule_family IN ('CANCEL_SPIKE', 'DISCOUNT_SPIKE', 'DRAWER_FREQUENCY', 'REPEATED_ACTION')),
    rule_version TEXT NOT NULL,
    configuration JSONB NOT NULL,
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'RETIRED')),
    effective_from TIMESTAMPTZ NOT NULL,
    effective_to TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_audit_rule_ver UNIQUE (tenant_id, rule_family, rule_version)
);

CREATE TABLE anomaly_findings (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    rule_family TEXT NOT NULL,
    rule_version TEXT NOT NULL,
    severity TEXT NOT NULL CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    state TEXT NOT NULL DEFAULT 'OPEN' CHECK (state IN ('OPEN', 'ACKNOWLEDGED', 'INVESTIGATING', 'RESOLVED', 'DISMISSED')),
    subject_user_id UUID NOT NULL,
    observation_window JSONB NOT NULL,
    metric_summary JSONB NOT NULL,
    sample_count INTEGER NOT NULL,
    evidence_refs JSONB NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    detected_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_findings_state ON anomaly_findings(tenant_id, branch_id, state);
```

#### 13.1.4 `V4__init_case_actions_and_state_machine.sql`
```sql
CREATE TABLE case_actions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    finding_id UUID NOT NULL REFERENCES anomaly_findings(id) ON DELETE CASCADE,
    action TEXT NOT NULL CHECK (action IN ('ACKNOWLEDGE', 'START_INVESTIGATION', 'UPDATE_INVESTIGATION', 'RESOLVE', 'DISMISS', 'REOPEN')),
    performed_by_actor JSONB NOT NULL,
    reason TEXT NOT NULL,
    note TEXT,
    from_state TEXT NOT NULL,
    to_state TEXT NOT NULL,
    case_version_after BIGINT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE case_actions IS 'Sổ cái bất biến ghi nhận mọi thao tác xử lý vụ việc chống thất thoát';
REVOKE UPDATE, DELETE ON case_actions FROM PUBLIC;

CREATE INDEX idx_case_actions_finding ON case_actions(tenant_id, finding_id, occurred_at);
```

#### 13.1.5 `V5__init_audit_exports_and_legal_holds.sql`
```sql
CREATE TABLE audit_exports (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED', 'EXPIRED')),
    scope JSONB NOT NULL,
    date_from TIMESTAMPTZ NOT NULL,
    date_to TIMESTAMPTZ NOT NULL,
    data_classes JSONB NOT NULL,
    export_format TEXT NOT NULL DEFAULT 'JSONL',
    reason TEXT NOT NULL,
    artifact_uri TEXT,
    artifact_checksum_sha256 TEXT,
    row_count BIGINT,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ
);

CREATE TABLE legal_holds (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    data_class TEXT NOT NULL,
    scope JSONB NOT NULL,
    from_time TIMESTAMPTZ NOT NULL,
    to_time TIMESTAMPTZ NOT NULL,
    reason TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'RELEASED')),
    applied_by_user_id UUID NOT NULL,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    released_at TIMESTAMPTZ,
    released_by_user_id UUID
);
```

## 14. Ma Trận Kiểm Thử Hợp Đồng (Contract Test Matrix)

| Khu vực | Các fixture / ca kiểm thử bắt buộc |
|---|---|
| Sự kiện nguồn | Route hành động chuẩn tắc, route chung, không nhân đôi bằng chứng, phiên bản sự kiện/nguồn, phiên bản lạ, từ chối làm sạch dữ liệu |
| Hủy món | Hủy dòng món / hủy toàn bộ đơn, lý do, trước/sau, trùng lặp / sai thứ tự, hủy bị từ chối |
| Giảm giá | Khuyến mãi / voucher / thủ công, minor units tiền VND, phân bổ, bán phần / chưa phân bổ, thiếu lý do, thay đổi phiên bản |
| Mở két tiền | Bán hàng / không bán hàng / hoàn tiền / mở ca / bảo trì, trễ lệnh / kết quả, lỗi phần cứng, retry thao tác, thiếu ca / thu ngân |
| Thu nhận (Ingestion) | At-least-once, tính nguyên tử của inbox, trùng lặp khóa nguồn, retry / lũy thừa backoff, DLQ / phát lại, sự cố database |
| Phân quyền | Owner toàn tenant/tập con, Manager chi nhánh, ID ngoại lai, thu hồi quan hệ thành viên, leo thang quyền hạn/vai trò, phạm vi cache / cursor / export |
| API | Các phong bì chuẩn, allowlist bộ lọc, giới hạn 30/90 ngày, sai lệch cursor, mã lỗi 401/403/404/409/422/429/503, ETag sau phân quyền |
| Tính toàn vẹn | Byte chuẩn tắc, thứ tự sắp xếp, SHA-256, digest trước đó, xác minh thành công / không khớp, không tự sửa âm thầm |
| Lưu trữ (Retention) | Ma trận 7 năm, chồng lấn lưu trữ pháp lý, hết hạn / thu hồi file xuất, tác vụ có tính lũy kế, kiểm toán việc archive / xóa |
| Quy tắc bất thường | Từng nhóm quy tắc, khởi động / mẫu tối thiểu, baseline bằng 0, ngưỡng / mức nghiêm trọng, giảm nhiễu, múi giờ / DST, phạm vi chi nhánh / thu ngân |
| Phát hiện | Khóa đơn định, đánh giá trùng lặp, chỉnh sửa muộn, nguồn bán phần, chưa đủ baseline, giải thích an toàn |
| Vụ việc | Quyền hạn Manager / Owner, đồ thị trạng thái, xung đột CAS, lý do bắt buộc, mở lại, các bước chuyển đổi append-only |
| Quyền riêng tư | Tuyệt đối không có JWT/PIN/SĐT/bí mật thanh toán/payload thô trong DB/API/log/trace/event/object storage |
| Hiệu năng | Độ trễ thu nhận ≤60s bình thường, danh sách/chi tiết p95 ≤2s có chặn, truy vấn 90 ngày, người dùng đồng thời, thông lượng xuất file |
| Giao diện (UI) | Hiển thị đầy đủ FRESH/STALE/PARTIAL/UNAVAILABLE/NO_DATA/INSUFFICIENT_BASELINE, lỗi an toàn, phạm vi và trạng thái vụ việc |

## 15. Các Tu Chính Hợp Đồng Hệ Thống Gốc Bắt Buộc

### POS Platform / Audit

- Bổ sung registry sự kiện hành động chuẩn tắc, quy tắc định tuyến nguồn / dedupe và schema tương thích `AuditRecorded.v1` mở rộng.
- Thay thế khung cấu trúc `audit_events` tối thiểu bằng tham chiếu schema do Audit sở hữu; bổ sung fixture tương thích producer/consumer cho `pos-audit-events`.
- Bổ sung các quyền hạn của Audit, quyền sở hữu API/read model, digest, lưu trữ / lưu trữ pháp lý và hợp đồng thao tác hạn chế.

### Order

- Bổ sung `DiscountApplied.v1` kèm nguồn/phiên bản/phân bổ/tác nhân/lý do và giữ sự kiện hủy món là nguồn có thẩm quyền cho việc hủy dòng món.
- Đảm bảo các sự kiện hủy món / giảm giá mang phiên bản nguồn/aggregate ổn định, chi nhánh, tóm tắt trước/sau an toàn và correlation.

### Promotion / Loyalty

- Bảo toàn phiên bản khuyến mãi/quy tắc/voucher bất biến và phân bổ áp dụng; phân biệt rõ giảm giá tự động với ghi đè thủ công.

### Payment / Shift / Thiết bị POS

- Định nghĩa quyền sở hữu producer của `CashDrawerOpened.v1`, operation key, kết quả lệnh / phần cứng và ngữ cảnh thiết bị / ca / thu ngân thao tác.
- Chỉ phát hành các dữ kiện thanh toán/hoàn tiền/đối soát/tiền mặt an toàn phục vụ ngữ cảnh kiểm toán và bất thường; tuyệt đối không phát credential hay payload ký của nhà cung cấp.

### Identity

- Bổ sung các quyền của Audit/Loss và các trường quyết định phạm vi / phiên bản phân quyền / thu hồi quyền hiện hành.
- Cung cấp hợp đồng snapshot tác nhân / quan hệ thành viên lịch sử an toàn mà không đòi hỏi Audit phải truy vấn PII hiện tại.

## 16. Các Artifact Triển Khai Bắt Buộc Trước Khi Viết Code

- File `openapi.yaml` cho các endpoint công khai về dòng thời gian, phát hiện, vụ việc và xuất file của Audit.
- File `openapi-internal.yaml` cho các thao tác sức khỏe, rebuild, toàn vẹn và lưu trữ.
- File `asyncapi.yaml` và các JSON Schema cho các sự kiện nguồn chuẩn tắc, sự kiện chung `AuditRecorded`, sự kiện bằng chứng/phát hiện/vụ việc/toàn vẹn do Audit phát hành.
- File `audit-event-registry.yaml` ánh xạ route producer chuẩn tắc, dedupe key, các trường bắt buộc và chính sách làm sạch dữ liệu cho mọi hành động.
- File `audit-rule-policy.schema.yaml` kèm các fixture đơn định cho toàn bộ các nhóm quy tắc ban đầu và bài toán khởi động nguội.
- File `audit-metric/data-quality.schema.yaml` cho ngữ nghĩa watermark và trạng thái chất lượng dữ liệu.
- Các migration PostgreSQL/Flyway với comment đầy đủ trên cột, role append-only, các ràng buộc, chỉ mục và kiểm tra phân vùng.
- Test vector chuẩn hóa digest: các dòng đầu vào, byte/hash chuẩn tắc, digest trước đó và kết quả xác minh kỳ vọng.
- Các fixture vàng (golden fixtures) cho commit/outbox nguồn, trùng lặp/phát lại, lệnh/kết quả két tiền, phân quyền, lưu trữ / lưu trữ pháp lý và cơ chế CAS vụ việc.
- Hồ sơ kiểm thử tải (load profile) cho truy vấn tương tác 90 ngày, xuất file 7 năm, thu nhận sự kiện và worker phát hiện bất thường.
- Bộ dữ liệu đánh giá bảo mật/quyền riêng tư cho IDOR, thu hồi quan hệ thành viên, rò rỉ cache, làm sạch PII/secret và thu hồi file xuất.

## 17. Cổng Phê Duyệt Hợp Đồng (Contracts Approval Gate)

- **Trạng thái hiện tại:** Đã phê duyệt — người dùng đã chấp nhận các cảnh báo trong đánh giá Contracts.
- **Yêu cầu trước khi Hoàn tất (Finalization):** Đã hoàn thành; các schema máy đọc được, test vector, tu chính hệ thống gốc và migration tiếp tục là các cổng triển khai được liệt kê trong kết quả thiết kế cuối cùng.
- **Quy tắc triển khai:** Tuyệt đối không viết controller/consumer/migration/anomaly worker trực tiếp từ văn bản mô tả; bắt buộc phải tạo các hợp đồng và fixture máy đọc được trước.
