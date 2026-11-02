Started at: 2026/10/13 00:00:00
Finished at: 2026/10/13 00:00:00
Total time: 0 minutes
---

# Module 9 — Trợ lý AI cho chủ quán: Giao ước API & Dữ liệu (API & Data Contracts)

**Ngày:** 2026-10-13  
**Trạng thái:** Approved — warning remediation applied  
**Tầm nhìn:** [AI Assistant Vision](./2026-10-13-vision.md)  
**Yêu cầu nghiệp vụ:** [AI Assistant Requirements](./2026-10-13-requirements.md)  
**Kiến trúc hệ thống:** [AI Assistant Architecture](./2026-10-13-architecture.md)  
**Biểu đồ tuần tự:** [AI Assistant Sequences](./2026-10-13-sequence-diagrams.adoc)

Tài liệu này là nguồn chuẩn mực để sinh tài liệu OpenAPI 3.1 công khai/nội bộ, JSON Schema cho các công cụ Reporting, AsyncAPI/JSON Schema cho sự kiện, cấu hình mẫu chính sách nhà cung cấp LLM và các mã script migration PostgreSQL cho AI Assistant Service. Các dịch vụ Reporting, Identity và giao ước domain cha là các đơn vị sở hữu tích hợp; AI Assistant tuyệt đối không tạo ra cơ sở dữ liệu dùng chung hoặc tự biến mình thành nguồn chân lý cho số liệu.

## 1. Quy ước giao ước (Contract Conventions)

### 1.1 Giao thức API và mã hóa dữ liệu (API & Encoding)

- API REST công khai tuân theo OpenAPI 3.1 đặt dưới đường dẫn `/api/v1`.
- API REST nội bộ tuân theo OpenAPI 3.1 đặt dưới đường dẫn `/internal/v1`, bắt buộc sử dụng định danh tải công việc (workload identity) hoặc mTLS, không bao giờ để lộ ra ngoài giao diện Angular.
- Sự kiện (Events) tuân theo cấu trúc phong bì cha + AsyncAPI/JSON Schema; đảm bảo cơ chế chuyển phát ít nhất một lần (at-least-once).
- Các trường định danh (IDs) là chuỗi UUID v7; dấu thời gian (timestamps) tính theo chuẩn ISO-8601 UTC.
- `businessDate` có định dạng `YYYY-MM-DD` theo múi giờ IANA của từng chi nhánh; múi giờ của trình duyệt không có thẩm quyền quyết định.
- Số tiền (Money) sử dụng số nguyên `int64` ở đơn vị nhỏ nhất (minor units, ví dụ: đồng) kèm mã tiền tệ `currency`; loại tiền tệ chuẩn P0 là `VND`.
- Các tỷ lệ và phần trăm (ratios/percentages) dùng kiểu số thập phân định dạng chuỗi có độ chính xác cố định (fixed-scale decimal string); tuyệt đối không dùng số thực dấu phẩy động nhị phân (binary floating point) cho tiền tệ hoặc chỉ số KPI hiển thị.
- Mọi thay đổi phá vỡ tương thích (breaking change) đều phải nâng phiên bản chính (major version) trên đường dẫn URI hoặc phiên bản sự kiện; các trường bổ sung không bắt buộc phải đảm bảo tính tương thích ngược.

### 1.2 Tiêu đề HTTP (Headers)

| Header | Công khai | Nội bộ | Ý nghĩa |
|---|---:|---:|---|
| `Authorization` | Bắt buộc | Bắt buộc | Cognito JWT hoặc thông tin xác thực tải công việc / mTLS |
| `X-Active-Branch-Id` | Không bắt buộc | Không có thẩm quyền | Chi nhánh đang chọn trên UI; máy chủ/Identity sẽ tự kiểm tra lại quyền |
| `X-Correlation-Id` | Khuyến nghị | Bắt buộc | Truy vết xuyên suốt request / công cụ / nhà cung cấp / sự kiện |
| `X-Causation-Id` | Không | Khuyến nghị | Lệnh hoặc sự kiện trực tiếp gây ra thao tác này |
| `Idempotency-Key` | Bắt buộc cho POST turn/rerun | Bắt buộc cho job/mutation | Khóa lũy quyền theo tenant + thao tác + tác nhân/tải công việc |
| `If-Match` | Dùng khi cập nhật revision tóm tắt | Dùng cho lệnh có phiên bản | Khóa phiên bản lạc quan (Optimistic version) |
| `Content-Type` | Phần thân | Phần thân | `application/json; charset=utf-8` |

Phía client không được phép gửi `tenantId`, vai trò (role), phiên bản tư cách thành viên hoặc quyền sở hữu chi nhánh với tư cách là thẩm quyền. Trường `requestedScope` chỉ là dữ liệu đầu vào cần Identity thẩm định. Các đối số hàm của LLM tuyệt đối không được chứa quyền hạn hoặc thẩm quyền về phạm vi.

### 1.3 Phong bì phản hồi thành công và lỗi (Success/Error Envelopes)

```json
{
  "data": {},
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

```json
{
  "error": {
    "code": "scope_not_allowed",
    "message": "The requested report scope is not available",
    "details": [],
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

Thông báo lỗi tuyệt đối không được làm lộ thông tin chi nhánh/tenant nằm ngoài phạm vi được phép, chuỗi JWT, nội dung prompt, câu lệnh SQL, tên bảng dữ liệu, số điện thoại, artifact mô hình, khóa bí mật của nhà cung cấp hoặc dữ liệu thô từ công cụ.

### 1.4 Phân trang và bộ nhớ đệm (Pagination & Caching)

- Danh sách tóm tắt sử dụng phân trang theo con trỏ (cursor pagination); mặc định `20`, tối đa `100`, sắp xếp theo `(businessDate DESC, branchId ASC, summaryId DESC)`.
- Lịch sử các lượt hỏi trong hội thoại dùng phân trang con trỏ; mặc định `20`, tối đa `100`.
- Câu trả lời của trợ lý AI và bản tóm tắt không được cache công khai. Dữ liệu từ các công cụ nội bộ không đi qua bộ nhớ cache trình duyệt.
- ETag chỉ được sử dụng cho các phiên bản tóm tắt an toàn nếu quyền truy cập tài nguyên vẫn được kiểm tra trước khi trả lời có điều kiện; không bao giờ dùng ETag để vượt qua bước kiểm tra thu hồi quyền.

### 1.5 Phân loại mã lỗi và HTTP (HTTP/Error Taxonomy)

| HTTP | Mã lỗi | Ý nghĩa | Thử lại (Retry) |
|---:|---|---|---:|
| 200 | — | Đọc dữ liệu / câu trả lời / phát lại lũy quyền | Không |
| 201 | — | Đã tạo hội thoại / tác vụ mới | Không |
| 202 | `turn_queued` | Lượt hỏi đã tiếp nhận, cần client poll kết quả | Poll |
| 202 | `summary_queued` | Yêu cầu chạy lại tóm tắt đã tiếp nhận | Poll |
| 400 | `validation_error` | Định dạng, ngày tháng hoặc trường dữ liệu không hợp lệ | Không |
| 401 | `unauthorized` | Thiếu, sai hoặc hết hạn chuỗi JWT | Refresh token |
| 403 | `forbidden` | Vai trò / tenant / chi nhánh không được phép truy cập | Không |
| 404 | `not_found` | Không tìm thấy tài nguyên trong phạm vi phân quyền | Không |
| 409 | `turn_expired` | Thời hạn phiên hoặc lượt hỏi đã hết hạn | Mở turn/phiên mới |
| 409 | `idempotency_conflict` | Trùng khóa lũy quyền nhưng sai khác nội dung yêu cầu | Không |
| 409 | `summary_revision_conflict` | Phiên bản tóm tắt yêu cầu chạy lại không còn là bản hiện hành | Đọc lại bản mới |
| 409 | `summary_not_ready` | Dữ liệu chưa đủ hoặc chưa đến giờ chốt ca | Poll/chạy lại sau |
| 409 | `scope_required` | Cần làm rõ chi nhánh hoặc khoảng thời gian | Cung cấp phạm vi |
| 409 | `scope_not_allowed` | Chi nhánh yêu cầu nằm ngoài quyền thành viên | Không |
| 409 | `tool_not_allowed` | Ý định / công cụ không nằm trong danh mục chính sách | Không |
| 409 | `tool_arguments_invalid` | Đối số của hàm vi phạm schema hoặc vượt ngưỡng cho phép | Không |
| 409 | `report_freshness_invalid` | Báo cáo bị chậm hoặc chưa đủ theo yêu cầu chính sách | Thử lại có giới hạn |
| 409 | `privacy_redaction_failed` | Không thể đảm bảo dữ liệu an toàn trước khi gửi | Đóng ngắt an toàn |
| 409 | `budget_exceeded` | Vượt quá ngân sách chi phí/truy vấn của lượt hỏi hoặc tenant | Thử lại sau |
| 422 | `unsupported_intent` | Câu hỏi nằm ngoài danh mục hỗ trợ | Không |
| 422 | `ambiguous_period` | Khoảng thời gian mơ hồ, chính sách không tự giải quyết được | Làm rõ |
| 429 | `rate_limited` | Vượt giới hạn tần suất của người dùng, tenant hoặc LLM | Chờ Retry-After |
| 503 | `authorization_unavailable` | Dịch vụ phân quyền Identity bị gián đoạn | Thử lại có giới hạn |
| 503 | `reporting_unavailable` | Công cụ Reporting tạm thời không khả dụng | Thử lại có giới hạn |
| 503 | `provider_unavailable` | DeepSeek gặp sự cố / timeout / ngắt mạch đang mở | Dùng số liệu cấu trúc dự phòng |
| 503 | `service_unavailable` | Cơ sở dữ liệu AI hoặc message broker không an toàn | Thử lại có giới hạn |

Các trạng thái `NO_DATA`, `STALE_DATA`, `PARTIAL`, `UNSUPPORTED` và `DATA_LATE` là các trạng thái domain an toàn trả về trong trường `data.status`, không nhất thiết phải trả về mã lỗi HTTP 4xx.

## 2. Giao ước phân quyền (Authorization Contract)

### 2.1 Quyền hạn công khai (Public Permissions)

| Quyền | Owner | Manager | Cashier | Kitchen | Phạm vi áp dụng |
|---|---:|---:|---:|---:|---|
| `ai:report:read` | Có | Chi nhánh được phân công | Không | Không | Toàn tenant hoặc chi nhánh được giao |
| `ai:summary:read` | Có | Chi nhánh được phân công | Không | Không | Toàn tenant hoặc chi nhánh được giao |
| `ai:summary:rerun` | Có | Chi nhánh được phân công | Không | Không | Giống quyền đọc tóm tắt; giới hạn tần suất |
| `ai:audit:read` | Qua chính sách Audit | Qua chính sách Audit | Không | Không | Dịch vụ Audit sở hữu quyền thẩm định cuối |
| `ai:provider:operate` | Vận hành giới hạn | Không | Không | Không | Chỉ dành cho nội bộ / vận hành |

Identity Access luôn là thẩm quyền tối cao cho vai trò và tư cách thành viên. AI Assistant gọi endpoint cha `POST /internal/v1/authorization-decisions` tại mỗi phiên/lượt hỏi được bảo vệ kèm theo quyền hạn và phạm vi yêu cầu. Reporting kiểm tra lại các điều kiện lọc tenant/chi nhánh và từ chối các ngữ cảnh tải công việc không có chữ ký, hết hạn hoặc sai lệch.

### 2.2 Schema của phạm vi đã giải quyết (Resolved Scope Schema)

```json
{
  "scopeType": "BRANCH",
  "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789001",
  "branchIds": ["0192b2d0-7f1d-7abc-8d2c-123456789002"],
  "subjectId": "0192b2d0-7f1d-7abc-8d2c-123456789003",
  "role": "MANAGER",
  "permission": "ai:report:read",
  "membershipVersion": 12,
  "authorizationVersion": 7,
  "resolvedAt": "2026-10-13T08:00:00Z",
  "expiresAt": "2026-10-13T08:05:00Z"
}
```

Phạm vi `TENANT` chỉ hợp lệ đối với vai trò Owner. Không chấp nhận ký tự đại diện cho chi nhánh. Mỗi lượt hỏi lưu lại một phiên bản hiệu chỉnh phạm vi; phạm vi này tuyệt đối không được mở rộng sau khi đã bắt đầu thực thi công cụ.

## 3. Danh mục API công khai (Public API Surface)

| Phương thức | Đường dẫn | Quyền / Phạm vi | Request | Response | Mã trạng thái |
|---|---|---|---|---|---|
| POST | `/api/v1/ai/conversations` | `ai:report:read` | `CreateConversationRequest` | `ConversationResponse` | 201, 401, 403, 409, 422 |
| GET | `/api/v1/ai/conversations/{conversationId}` | Sở hữu + cùng phạm vi | — | `ConversationResponse` | 200, 403, 404 |
| GET | `/api/v1/ai/conversations/{conversationId}/turns` | Sở hữu + cùng phạm vi | Con trỏ / Bộ lọc | `TurnPage` | 200, 403, 404, 422 |
| POST | `/api/v1/ai/conversations/{conversationId}/turns` | `ai:report:read` | `CreateTurnRequest` | `TurnResponse` | 200, 202, 403, 404, 409, 422, 429, 503 |
| GET | `/api/v1/ai/conversations/{conversationId}/turns/{turnId}` | Sở hữu + cùng phạm vi | — | `TurnResponse` | 200, 403, 404 |
| GET | `/api/v1/ai/daily-summaries` | `ai:summary:read` | Bộ lọc ngày/chi nhánh/con trỏ | `SummaryPage` | 200, 403, 422 |
| GET | `/api/v1/ai/daily-summaries/{summaryId}` | Sở hữu + cùng phạm vi | — | `SummaryResponse` | 200, 403, 404 |
| POST | `/api/v1/ai/daily-summaries/{summaryId}/reruns` | `ai:summary:rerun` | `RerunSummaryRequest` | `SummaryJobResponse` | 202, 403, 404, 409, 422, 429, 503 |

API công khai tuyệt đối không làm lộ danh mục công cụ nội bộ, prompt hệ thống, prompt gửi nhà cung cấp, payload hàm thô, câu lệnh SQL hoặc các thông số kiểm tra sức khỏe của nhà cung cấp LLM.

## 4. Schema chi tiết của API công khai (Public API Schemas)

### 4.1 Tạo hội thoại mới (Create Conversation)

`POST /api/v1/ai/conversations`

API khởi tạo ngữ cảnh hội thoại AI cho chủ chuỗi hoặc quản lý chi nhánh. Máy chủ thẩm định quyền hạn trực tuyến với Identity Service và chỉ cấp phạm vi hợp lệ.

#### Tiêu đề Yêu cầu (Headers)
```http
Authorization: Bearer <Cognito JWT Token>
Content-Type: application/json
Accept: application/json
X-Correlation-ID: 0192b2d0-7f1d-7abc-8d2c-123456789001
Idempotency-Key: 0192b2d0-7f1d-7abc-8d2c-123456789002
```

#### Request Body Mẫu
```json
{
  "requestedScope": {
    "type": "BRANCH",
    "branchIds": ["0192b2d0-7f1d-7abc-8d2c-123456789002"]
  },
  "locale": "vi-VN",
  "clientReference": "owner-dashboard-chat-1"
}
```

| Trường | Kiểu dữ liệu | Bắt buộc | Quy tắc nghiệp vụ |
|---|---|:---:|---|
| `requestedScope.type` | enum (`TENANT`, `BRANCH`) | Có | Loại phạm vi truy vấn dữ liệu |
| `requestedScope.branchIds` | UUID[] | Có điều kiện | Bắt buộc khi `type=BRANCH`; tối đa 20 chi nhánh; máy chủ thẩm định quyền sở hữu |
| `locale` | string | Không | Mã ngôn ngữ cho phép; mặc định `vi-VN` |
| `clientReference` | string | Không | Tối đa 128 ký tự; chỉ dùng truy vết UI; cấm chứa PII |

#### Phản hồi Thành Công `201 Created`
```json
{
  "data": {
    "conversationId": "0192b2d0-7f1d-7abc-8d2c-123456789010",
    "status": "ACTIVE",
    "scope": {
      "type": "BRANCH",
      "branchIds": ["0192b2d0-7f1d-7abc-8d2c-123456789002"],
      "authorizationVersion": 7
    },
    "policyVersion": "ai-policy-v1",
    "expiresAt": "2026-10-13T10:00:00Z",
    "createdAt": "2026-10-13T08:00:00Z"
  },
  "meta": {
    "requestId": "req_0192b2d0-7f1d-7abc-8d2c-123456789001",
    "correlationId": "cor_0192b2d0-7f1d-7abc-8d2c-123456789002",
    "timestamp": "2026-10-13T08:00:00Z"
  }
}
```

#### Phản hồi Lỗi Thường Gặp
- `401 Unauthorized` (`code: "unauthorized"`): JWT token thiếu hoặc hết hạn.
- `403 Forbidden` (`code: "forbidden"`): Người dùng không có quyền `ai:report:read` trên chi nhánh yêu cầu.
- `422 Unprocessable Entity` (`code: "validation_error"`): Chi nhánh không tồn tại hoặc vượt quá 20 chi nhánh.

---

### 4.2 Chi tiết cuộc hội thoại (Get Conversation Detail)

`GET /api/v1/ai/conversations/{conversationId}`

Truy vấn thông tin chi tiết và trạng thái hiệu lực của một cuộc hội thoại.

#### Tiêu đề Yêu cầu (Headers)
```http
Authorization: Bearer <Cognito JWT Token>
Accept: application/json
X-Correlation-ID: 0192b2d0-7f1d-7abc-8d2c-123456789001
```

#### Tham số Đường dẫn (Path Parameters)
| Tham số | Kiểu | Bắt buộc | Mô tả |
|---|---|:---:|---|
| `conversationId` | UUIDv7 | Có | Mã định danh cuộc hội thoại |

#### Phản hồi Thành Công `200 OK`
```json
{
  "data": {
    "conversationId": "0192b2d0-7f1d-7abc-8d2c-123456789010",
    "status": "ACTIVE",
    "scope": {
      "type": "BRANCH",
      "branchIds": ["0192b2d0-7f1d-7abc-8d2c-123456789002"],
      "authorizationVersion": 7
    },
    "policyVersion": "ai-policy-v1",
    "totalTurns": 3,
    "expiresAt": "2026-10-13T10:00:00Z",
    "createdAt": "2026-10-13T08:00:00Z",
    "updatedAt": "2026-10-13T08:15:20Z"
  },
  "meta": {
    "requestId": "req_0192b2d0-7f1d-7abc-8d2c-123456789001",
    "correlationId": "cor_0192b2d0-7f1d-7abc-8d2c-123456789002",
    "timestamp": "2026-10-13T08:15:21Z"
  }
}
```

---

### 4.3 Danh sách lượt hỏi đáp trong hội thoại (List Conversation Turns)

`GET /api/v1/ai/conversations/{conversationId}/turns`

Truy vấn lịch sử các lượt hỏi đáp trong hội thoại theo cơ chế phân trang con trỏ (Cursor Pagination).

#### Tiêu đề Yêu cầu (Headers)
```http
Authorization: Bearer <Cognito JWT Token>
Accept: application/json
X-Correlation-ID: 0192b2d0-7f1d-7abc-8d2c-123456789001
```

#### Tham số Truy vấn (Query Parameters)
| Tham số | Kiểu | Mặc định | Mô tả |
|---|---|:---:|---|
| `limit` | integer | 20 | Số bản ghi trên mỗi trang (tối đa 50) |
| `cursor` | string | null | Con trỏ phân trang trả về từ trang trước |

#### Phản hồi Thành Công `200 OK`
```json
{
  "data": [
    {
      "turnId": "0192b2d0-7f1d-7abc-8d2c-123456789011",
      "conversationId": "0192b2d0-7f1d-7abc-8d2c-123456789010",
      "status": "ANSWERED",
      "headline": "Doanh thu giảm 12,4%",
      "createdAt": "2026-10-13T08:00:05Z"
    }
  ],
  "meta": {
    "limit": 20,
    "hasMore": false,
    "nextCursor": null,
    "requestId": "req_0192b2d0-7f1d-7abc-8d2c-123456789001",
    "correlationId": "cor_0192b2d0-7f1d-7abc-8d2c-123456789002",
    "timestamp": "2026-10-13T08:16:00Z"
  }
}
```

---

### 4.4 Tạo lượt hỏi đáp mới (Create Turn)

`POST /api/v1/ai/conversations/{conversationId}/turns`

Gửi câu hỏi báo cáo phân tích vào cuộc hội thoại. Hệ thống hỗ trợ xử lý đồng bộ (trả kết quả ngay nếu câu trả lời đã sẵn sàng trong cache hoặc mô hình phản hồi nhanh) hoặc xử lý bất đồng bộ (trả `202 Accepted` kèm URL polling).

#### Tiêu đề Yêu cầu (Headers)
```http
Authorization: Bearer <Cognito JWT Token>
Content-Type: application/json
Accept: application/json
X-Correlation-ID: 0192b2d0-7f1d-7abc-8d2c-123456789001
Idempotency-Key: 0192b2d0-7f1d-7abc-8d2c-123456789003
```

#### Request Body Mẫu
```json
{
  "clientTurnId": "turn-client-0004",
  "question": "Tuần này doanh thu chi nhánh Quận 1 thay đổi thế nào so với cùng thứ tuần trước?",
  "requestedPeriod": {
    "type": "RELATIVE_WEEK"
  },
  "requestedScope": null,
  "responseMode": "TEXT_WITH_PROVENANCE"
}
```

| Trường | Kiểu dữ liệu | Bắt buộc | Quy tắc nghiệp vụ |
|---|---|:---:|---|
| `clientTurnId` | string | Có | Tối đa 128 ký tự; duy nhất trong hội thoại; là khóa lũy quyền |
| `question` | string | Có | 1–4.000 ký tự Unicode; luôn bị coi là dữ liệu không đáng tin cậy |
| `requestedPeriod` | object/null | Không | Máy chủ tự chuẩn hóa khoảng thời gian/múi giờ chính xác |
| `requestedScope` | object/null | Không | Phải là tập con của phạm vi quyền hiện hành |
| `responseMode` | enum | Không | `TEXT_WITH_PROVENANCE`; MVP không hỗ trợ chế độ xem công cụ thô |

#### Phản hồi Thành Công Đồng Bộ `200 OK`
```json
{
  "data": {
    "turnId": "0192b2d0-7f1d-7abc-8d2c-123456789011",
    "conversationId": "0192b2d0-7f1d-7abc-8d2c-123456789010",
    "status": "ANSWERED",
    "answer": {
      "headline": "Doanh thu giảm 12,4%",
      "text": "Doanh thu chi nhánh Quận 1 tuần này đạt 12.400.000 đ, giảm 12,4% so với cùng kỳ tuần trước (14.155.200 đ). Nguyên nhân chính do món Trà sữa Ô long nướng giảm lượng bán vào buổi chiều.",
      "warnings": []
    },
    "scope": {
      "type": "BRANCH",
      "branchIds": ["0192b2d0-7f1d-7abc-8d2c-123456789002"]
    },
    "period": {
      "from": "2026-10-05",
      "to": "2026-10-11",
      "timezone": "Asia/Ho_Chi_Minh"
    },
    "sources": [
      {
        "tool": "getSalesSummary",
        "watermark": "reporting:branch:42:v901",
        "watermarkVersion": 901,
        "lastUpdatedAt": "2026-10-12T17:00:00Z",
        "calculationVersion": "sales-metric-v2"
      }
    ],
    "provider": {
      "modelVersion": "configured-model-reference",
      "policyVersion": "ai-provider-policy-v1"
    },
    "warnings": [],
    "createdAt": "2026-10-13T08:00:05Z"
  },
  "meta": {
    "requestId": "req_0192b2d0-7f1d-7abc-8d2c-123456789001",
    "correlationId": "cor_0192b2d0-7f1d-7abc-8d2c-123456789002",
    "timestamp": "2026-10-13T08:00:05Z"
  }
}
```

#### Phản hồi Chờ Xử Lý `202 Accepted` (Polling Mode)
```json
{
  "data": {
    "turnId": "0192b2d0-7f1d-7abc-8d2c-123456789011",
    "conversationId": "0192b2d0-7f1d-7abc-8d2c-123456789010",
    "status": "QUEUED",
    "pollUrl": "/api/v1/ai/conversations/0192b2d0-7f1d-7abc-8d2c-123456789010/turns/0192b2d0-7f1d-7abc-8d2c-123456789011",
    "pollAfterMs": 500,
    "expiresAt": "2026-10-13T08:00:30Z"
  },
  "meta": {
    "requestId": "req_0192b2d0-7f1d-7abc-8d2c-123456789001",
    "correlationId": "cor_0192b2d0-7f1d-7abc-8d2c-123456789002",
    "timestamp": "2026-10-13T08:00:00Z"
  }
}
```

---

### 4.4.1 Schema chuẩn cho câu trả lời an toàn (Normative Safe Answer Schema)

Trường `answer_payload` chỉ được chấp nhận khi khớp chính xác với cấu trúc dưới đây và các luận điểm (claims) của nó đối soát hoàn toàn khớp với các dữ kiện công cụ đã lưu:

```json
{
  "headline": "Doanh thu giảm 12,4%",
  "text": "Doanh thu ...",
  "claims": [
    {"key": "net_revenue_delta_ratio", "value": "-0.124", "sourceRef": "getSalesSummary#1"}
  ],
  "sourceRefs": ["getSalesSummary#1"],
  "warnings": []
}
```

Các trường `headline`, `text`, `claims`, `sourceRefs` và `warnings` là bắt buộc; `claims[].sourceRef` phải tham chiếu đến một lần thực thi công cụ hợp lệ trong cùng lượt hỏi. Các con số trong văn bản được kiểm tra đối chiếu chặt chẽ với claims/facts sau khi áp dụng quy tắc làm tròn đã cấu hình. Bộ xác thực sẽ từ chối chuỗi ký tự dạng số điện thoại, ID thô của UUID/đơn hàng/thanh toán, câu lệnh SQL, văn bản hướng dẫn prompt và các luận điểm không có căn cứ. Mọi thất bại trong khâu che giấu dữ liệu hoặc đối soát đều được gán mã `privacy_redaction_failed`/`provider_output_invalid` và đóng ngắt an toàn.

---

### 4.5 Chi tiết lượt hỏi đáp / Endpoint Polling (Get Turn Detail)

`GET /api/v1/ai/conversations/{conversationId}/turns/{turnId}`

Dùng để lấy chi tiết lượt hỏi hoặc để client thăm dò (polling) khi nhận mã phản hồi `202 Accepted`.

#### Tiêu đề Yêu cầu (Headers)
```http
Authorization: Bearer <Cognito JWT Token>
Accept: application/json
X-Correlation-ID: 0192b2d0-7f1d-7abc-8d2c-123456789001
```

#### Tham số Đường dẫn (Path Parameters)
| Tham số | Kiểu | Bắt buộc | Mô tả |
|---|---|:---:|---|
| `conversationId` | UUIDv7 | Có | Mã định danh cuộc hội thoại |
| `turnId` | UUIDv7 | Có | Mã định danh lượt hỏi đáp |

#### Phản hồi Thành Công `200 OK` (Khi trạng thái là `ANSWERED`)
Cấu trúc dữ liệu tương tự phản hồi `200 OK` của mục 4.4. Nếu trạng thái vẫn là `QUEUED` hoặc `RUNNING`, trả về mã `200 OK` với thông tin `pollAfterMs` và `status`.

---

### 4.6 Danh sách bản tóm tắt ngày (List Daily Summaries)

`GET /api/v1/ai/daily-summaries`

Truy vấn danh sách các bản tóm tắt kinh doanh ngày được AI tự động tạo sau giờ chốt ca.

#### Tiêu đề Yêu cầu (Headers)
```http
Authorization: Bearer <Cognito JWT Token>
Accept: application/json
X-Correlation-ID: 0192b2d0-7f1d-7abc-8d2c-123456789001
```

#### Tham số Truy vấn (Query Parameters)
| Tham số | Kiểu | Bắt buộc | Mô tả |
|---|---|:---:|---|
| `branchId` | UUID | Không | Lọc theo chi nhánh (bắt buộc với Manager) |
| `businessDateFrom` | date (YYYY-MM-DD) | Không | Ngày kinh doanh bắt đầu |
| `businessDateTo` | date (YYYY-MM-DD) | Không | Ngày kinh doanh kết thúc |
| `status` | string | Không | Trạng thái: `PUBLISHED`, `DATA_LATE`, `NO_BASELINE`, `FAILED` |
| `limit` | integer | Không | Số bản ghi (mặc định 20, tối đa 100) |
| `cursor` | string | Không | Con trỏ phân trang |

#### Phản hồi Thành Công `200 OK`
```json
{
  "data": [
    {
      "summaryId": "0192b2d0-7f1d-7abc-8d2c-123456789060",
      "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789002",
      "businessDate": "2026-10-12",
      "timezone": "Asia/Ho_Chi_Minh",
      "status": "PUBLISHED",
      "revision": 1,
      "headline": "Doanh thu giảm 12,4%",
      "publishedAt": "2026-10-12T17:30:00Z"
    }
  ],
  "meta": {
    "limit": 20,
    "hasMore": false,
    "nextCursor": null,
    "requestId": "req_0192b2d0-7f1d-7abc-8d2c-123456789001",
    "correlationId": "cor_0192b2d0-7f1d-7abc-8d2c-123456789002",
    "timestamp": "2026-10-13T08:20:00Z"
  }
}
```

---

### 4.7 Chi tiết bản tóm tắt ngày (Get Daily Summary Detail)

`GET /api/v1/ai/daily-summaries/{summaryId}`

Truy vấn toàn văn nội dung bản tóm tắt an toàn và các nhân tố đóng góp (drivers).

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
    "summaryId": "0192b2d0-7f1d-7abc-8d2c-123456789060",
    "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789002",
    "businessDate": "2026-10-12",
    "timezone": "Asia/Ho_Chi_Minh",
    "status": "PUBLISHED",
    "revision": 1,
    "supersedesSummaryId": null,
    "supersededBySummaryId": null,
    "safeContent": {
      "headline": "Doanh thu giảm 12,4%",
      "actual": {"metric": "NET_REVENUE", "valueMinor": 12400000, "currency": "VND"},
      "baseline": {"type": "SAME_WEEKDAY_LAST_WEEK", "valueMinor": 14155200},
      "deltaRatio": "-0.124",
      "drivers": [
        {"safeLabel": "Trà sữa Ô long nướng", "signedDeltaMinor": -600000, "causalEvidence": false}
      ],
      "caveats": ["Dữ liệu đã hoàn tất đến 17:00 giờ địa phương."],
      "wordingMode": "OBSERVED_CONTRIBUTION"
    },
    "watermark": "reporting:branch:42:v901",
    "calculationVersion": "sales-metric-v2",
    "policyVersion": "summary-policy-v1",
    "modelVersion": "configured-model-reference",
    "generatedAt": "2026-10-12T17:28:10Z",
    "publishedAt": "2026-10-12T17:30:00Z"
  },
  "meta": {
    "requestId": "req_0192b2d0-7f1d-7abc-8d2c-123456789001",
    "correlationId": "cor_0192b2d0-7f1d-7abc-8d2c-123456789002",
    "timestamp": "2026-10-13T08:20:00Z"
  }
}
```

---

### 4.8 Yêu cầu chạy lại tóm tắt ngày (Summary Rerun)

`POST /api/v1/ai/daily-summaries/{summaryId}/reruns`

Yêu cầu kích hoạt lại quá trình tạo bản tóm tắt khi phát hiện có dữ liệu doanh thu bổ sung muộn từ Reporting Service.

#### Tiêu đề Yêu cầu (Headers)
```http
Authorization: Bearer <Cognito JWT Token>
Content-Type: application/json
Accept: application/json
X-Correlation-ID: 0192b2d0-7f1d-7abc-8d2c-123456789001
Idempotency-Key: 0192b2d0-7f1d-7abc-8d2c-123456789004
```

#### Request Body Mẫu
```json
{
  "reason": "Late OrderPaid projection completed",
  "expectedRevision": 1,
  "requestedSourceWatermark": "reporting:branch:42:v902"
}
```

| Trường | Kiểu dữ liệu | Bắt buộc | Mô tả |
|---|---|:---:|---|
| `reason` | string | Có | Lý do chạy lại bản tóm tắt (tối đa 250 ký tự) |
| `expectedRevision` | integer | Có | Số revision hiện tại mà client đang nhìn thấy (chống race condition) |
| `requestedSourceWatermark` | string | Có | Mốc dữ liệu mới từ Reporting Service |

#### Phản hồi Tiếp Nhận `202 Accepted`
```json
{
  "data": {
    "summaryJobId": "0192b2d0-7f1d-7abc-8d2c-123456789070",
    "summaryId": "0192b2d0-7f1d-7abc-8d2c-123456789060",
    "status": "READY",
    "targetRevision": 2,
    "queuedAt": "2026-10-13T08:25:00Z"
  },
  "meta": {
    "requestId": "req_0192b2d0-7f1d-7abc-8d2c-123456789001",
    "correlationId": "cor_0192b2d0-7f1d-7abc-8d2c-123456789002",
    "timestamp": "2026-10-13T08:25:01Z"
  }
}
```

Thao tác chạy lại chỉ được phép trong phạm vi phân quyền hiện hành và khi mốc kiểm chứng nguồn/chính sách cho phép. Cùng một khóa lũy quyền sẽ trả về cùng một `summaryJobId`; nếu phiên bản dự kiến `expectedRevision` đã cũ, hệ thống trả về mã lỗi `409 summary_revision_conflict`.

## 5. Giao ước phân quyền nội bộ với Identity (Internal Identity Authorization Contract)

AI Assistant sử dụng giao ước của Identity cha. Các tên trường chuẩn mực là các tên trường phía dưới; các tên trường dự thảo cũ (`subjectId`, `permission`) không còn giá trị sử dụng.

`POST /internal/v1/authorization-decisions`

Yêu cầu tối thiểu:

```json
{
  "subject": "cognito-sub-opaque",
  "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789001",
  "requestedBranchIds": ["0192b2d0-7f1d-7abc-8d2c-123456789002"],
  "requiredPermission": "ai:report:read",
  "operation": "AI_REPORT_TURN",
  "correlationId": "0192b2d0-7f1d-7abc-8d2c-123456789020"
}
```

Phản hồi tối thiểu:

```json
{
  "data": {
    "allowed": true,
    "denialCode": null,
    "resolvedScope": {
      "scopeType": "BRANCH",
      "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789001",
      "branchIds": ["0192b2d0-7f1d-7abc-8d2c-123456789002"],
      "role": "MANAGER",
      "permission": "ai:report:read",
      "membershipVersion": 12,
      "authorizationVersion": 7,
      "expiresAt": "2026-10-13T08:05:00Z"
    }
  },
  "meta": {"requestId": "req_01J...", "correlationId": "cor_01J..."}
}
```

Phần sửa đổi bổ sung của Identity cha bổ sung trường `requestedBranchIds[]` cho các yêu cầu đa chi nhánh có giới hạn trong khi vẫn giữ `branchId` làm alias tương thích khi chỉ truy vấn đúng một chi nhánh. Trường `requiredPermission` là tên chuẩn; trường `permission` trong `resolvedScope` là trường quyết định trả về. Phản hồi bổ sung `authorizationVersion` và `resolvedScope`; bắt buộc phải có phong bì `data`/`meta`.

AI Assistant ánh xạ `allowed=false` sang mã an toàn `403`/`NEEDS_SCOPE` mà không để lộ sự tồn tại của các chi nhánh ngoài quyền. Identity luôn là thẩm quyền trực tuyến; cache cục bộ không được phép phân quyền sau khi đã thu hồi. Bộ kiểm thử giao ước phải từ chối yêu cầu chứa đồng thời cả `branchId` và `requestedBranchIds`, chứa ký tự đại diện cho chi nhánh hoặc sai lệch subject/tenant.

## 6. Giao ước công cụ Reporting (Reporting Tool Contracts)

### 6.1 Endpoint nội bộ

`POST /internal/v1/reporting/ai-tools/{toolName}`

Bên gọi: AI Assistant Tool Gateway. `toolName` chỉ được chấp nhận nếu nằm trong phiên bản registry đã cấu hình ở cả AI Assistant và Reporting; các tên lạ hoặc bị vô hiệu hóa sẽ trả về `tool_not_allowed`.

Phong bì yêu cầu:

```json
{
  "toolVersion": "report-tools-v1",
  "registryVersion": "report-tool-registry-v1",
  "registryDigest": "sha256:tool-registry-digest",
  "resolvedScope": {
    "scopeType": "BRANCH",
    "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789001",
    "branchIds": ["0192b2d0-7f1d-7abc-8d2c-123456789002"],
    "membershipVersion": 12,
    "authorizationVersion": 7
  },
  "arguments": {
    "businessDateFrom": "2026-10-05",
    "businessDateTo": "2026-10-11",
    "timezone": "Asia/Ho_Chi_Minh"
  },
  "correlationId": "0192b2d0-7f1d-7abc-8d2c-123456789020"
}
```

Reporting bắt buộc phải kiểm tra tính hợp lệ của định danh tải công việc, tenant, phạm vi chi nhánh, `registryVersion`, mã băm `registryDigest`, phiên bản công cụ, khoảng thời gian và các điều kiện lọc tại repository. Tuyệt đối không chấp nhận các biểu thức SQL, tên bảng hay mệnh đề ORDER BY. Nếu sai lệch mã băm registry sẽ trả về `tool_registry_version_mismatch`; nếu công cụ lạ hoặc bị vô hiệu hóa sẽ trả về `tool_not_allowed`.

### 6.2 Phong bì chất lượng dữ liệu chung (`DataQualityEnvelope`)

| Trường | Kiểu dữ liệu | Bắt buộc | Ý nghĩa |
|---|---|---:|---|
| `freshnessStatus` | enum | Có | `FRESH`, `STALE`, `INCOMPLETE`, `UNAVAILABLE` |
| `completenessStatus` | enum | Có | `COMPLETE`, `PARTIAL`, `MISSING` |
| `projectionVersion` | int64 | Có | Phiên bản bản chiếu / xây dựng lại |
| `watermark` | string | Có | Mốc kiểm chứng nguồn |
| `watermarkVersion` | int64 | Có | Phiên bản mốc kiểm chứng tăng đơn điệu dùng để sắp xếp thứ tự revision trong scope |
| `lastUpdatedAt` | timestamp | Có | Thời điểm cập nhật bản chiếu theo giờ UTC |
| `missingSources` | string[] | Có | Danh sách tên nguồn bị thiếu (chỉ dùng nhãn an toàn) |
| `calculationVersion` | string | Có | Phiên bản từ điển / công thức tính toán |

Mọi phản hồi từ công cụ đều được đóng gói trong:

```json
{
  "tool": "getSalesSummary",
  "toolVersion": "report-tools-v1",
  "registryVersion": "report-tool-registry-v1",
  "registryDigest": "sha256:tool-registry-digest",
  "status": "OK",
  "data": {},
  "dataQuality": {}
}
```

Trường `status` nhận một trong các giá trị: `OK`, `NO_DATA`, `STALE`, `PARTIAL`, `UNAVAILABLE`. Trường `dataQuality.watermarkVersion` là giá trị duy nhất mà AI Assistant được phép dùng để so sánh độ tươi mới / thứ tự phiên bản; chuỗi `watermark` thô được lưu trữ phục vụ phát lại và kiểm toán.

### 6.3 Schema của từng công cụ cụ thể

Mọi đối số của công cụ đều bao gồm phạm vi quyền do máy chủ tự tiêm vào ngoài tầm kiểm soát của mô hình. Mô hình LLM chỉ được phép cung cấp các trường liệt kê dưới đây:

#### `getSalesSummary`

Đối số: `businessDateFrom`, `businessDateTo`, tùy chọn `comparison` (`SAME_WEEKDAY_LAST_WEEK`, `SEVEN_DAY_AVERAGE`, `NONE`). Tối đa 31 ngày kinh doanh.

Kết quả: `paidOrderCount`, `grossMinor`, `netMinor`, `vatMinor`, `currency`, `comparisonDeltaMinor`, `comparisonDeltaRatio`, `baselinePeriod`, `dataQuality`.

#### `getOrderCount`

Đối số: khoảng thời gian + tùy chọn so sánh. Kết quả: `paidOrderCount`, `baselineCount`, `deltaCount`, `deltaRatio`, `dataQuality`.

#### `getAverageOrderValue`

Đối số: khoảng thời gian + tùy chọn so sánh. Kết quả: `aovMinor`, `paidOrderCountDenominator`, `baselineAovMinor`, `deltaMinor`, `deltaRatio`, `roundingVersion`, `currency`, `dataQuality`.

Công thức tính là `netMinor / paidOrderCountDenominator`; trường hợp mẫu số bằng 0 trả về `NO_DATA` chứ không trả về số 0 giả lập.

#### `getTopItems`

Đối số: khoảng thời gian, giới hạn `limit` từ 1–10, sắp xếp theo `sortBy` nằm trong danh mục trắng `REVENUE`/`QUANTITY`. Các phần tử kết quả chỉ chứa mã tham chiếu an toàn `itemId`, nhãn món an toàn `itemLabel`, số lượng bán `quantitySold`, doanh thu `revenueMinor`, loại tiền tệ `currency`, thứ hạng `rank`; không chứa ID đơn hàng/dòng món/khách hàng thô.

#### `compareBranches`

Đối số: Phạm vi toàn tenant của Chủ quán, khoảng thời gian, danh sách các chi nhánh được chọn (nếu có), chỉ số so sánh trong danh mục trắng `NET_REVENUE`/`ORDER_COUNT`/`AOV`, giới hạn `limit` ≤ 20. Kết quả chỉ chứa nhãn an toàn/mã của các chi nhánh có quyền, giá trị chỉ số, thứ hạng, mức chênh lệch, loại tiền tệ và chất lượng dữ liệu. Sự tồn tại của các chi nhánh ngoài quyền hoàn toàn bị giấu kín.

#### `getHourlySales`

Đối số: Đúng một chi nhánh có quyền, khoảng thời gian tối đa 7 ngày kinh doanh, khung giờ `hourBucket` từ 0–23. Kết quả chứa khung giờ địa phương, số lượng đơn, doanh thu net, múi giờ và chất lượng dữ liệu. Tuyệt đối không chứa dấu thời gian giao dịch chính xác.

#### `getContributionDrivers`

Đối số: Một ngày kinh doanh, khoảng thời gian mốc so sánh, danh mục chiều phân tích cho phép `ITEM`/`CATEGORY`/`HOUR`/`BRANCH`, giới hạn `limit` từ 1–10, phiên bản chính sách trọng yếu. Kết quả:

```json
{
  "drivers": [
    {
      "dimension": "ITEM",
      "safeLabel": "Trà sữa truyền thống",
      "actualMinor": 1200000,
      "baselineMinor": 1800000,
      "signedDeltaMinor": -600000,
      "contributionRatio": "-0.21",
      "material": true
    }
  ],
  "causalEvidence": false,
  "calculationVersion": "contribution-v1",
  "dataQuality": {}
}
```

Không một yếu tố đóng góp nào được phép trả về dưới dạng bằng chứng nguyên nhân chắc chắn trừ khi Reporting thiết lập tường minh cờ `causalEvidence=true`; giá trị mặc định luôn là false.

### 6.4 Registry của Reporting và Từ điển số liệu (Metric Dictionary)

Danh mục công cụ là một artifact có phiên bản dùng chung giữa AI Assistant và Reporting, không phải là một dòng bảng cơ sở dữ liệu được chỉnh sửa độc lập:

```json
{
  "registryVersion": "report-tool-registry-v1",
  "registryDigest": "sha256:tool-registry-digest",
  "tools": [
    {
      "name": "getSalesSummary",
      "schemaId": "reporting.getSalesSummary",
      "schemaVersion": 1,
      "enabled": true
    }
  ]
}
```

Registry tự sinh chứa đầy đủ cả 7 công cụ, schema đối số, schema kết quả, các ngưỡng giới hạn và trạng thái kích hoạt. Cả AI Assistant và Reporting đều từ chối yêu cầu nếu phiên bản registry hoặc mã băm digest không khớp. Registry được đồng bộ triển khai cùng lúc với dịch vụ Reporting và được ghi nhận trong metadata `policy_version`/`tool_version`.

| Chỉ số | Định nghĩa chuẩn mực | Quy tắc xử lý trường hợp biên | Mã Fixture kiểm thử |
|---|---|---|---|
| `paidOrderCount` | Đếm số lượng sự kiện `OrderPaid.v1` hoàn tất duy nhất trong bản chiếu của Reporting | Trùng ID sự kiện/khóa nguồn chỉ đếm 1 lần; đơn nháp, đơn lỗi và thanh toán đối soát muộn không được tính | `report-duplicate-order-paid`, `report-payment-late-settlement` |
| `grossMinor` / `netMinor` / `vatMinor` | Giá trị bản chiếu của Reporting từ sự kiện đơn hàng thanh toán chuẩn kết hợp chính sách điều chỉnh/hoàn tiền đã duyệt | Việc xử lý hoàn tiền/điều chỉnh được quy định theo phiên bản chính sách và trả về trong `calculationVersion` | `report-full-refund`, `report-partial-refund`, `report-correction` |
| `AOV` | Công thức `netMinor / paidOrderCount`; Reporting áp dụng quy tắc làm tròn tất định | Mẫu số bằng 0 trả về `NO_DATA`, tuyệt đối không tự bịa ra số 0 | `report-aov-zero-denominator`, `report-aov-rounding` |
| Khung giờ (Hour bucket) | Múi giờ kinh doanh địa phương của chi nhánh, số nguyên từ `0..23` | Xử lý chênh lệch giờ theo múi giờ IANA của chi nhánh; không dùng giờ trình duyệt | `report-hourly-branch-timezone`, `report-hourly-boundary` |
| Đóng góp (Contribution) | Reporting trả về mức chênh lệch có dấu, tỷ lệ, ngưỡng trọng yếu và cờ nhân quả | Tiêu chí phân định khi bằng điểm là `dimension`, nhãn an toàn và mã tham chiếu món ổn định; LLM không tính lại | `report-contribution-negative`, `report-contribution-tie`, `report-contribution-materiality` |

Hợp đồng dịch vụ Reporting cha bắt buộc phải công bố các schema và mã fixture này trước khi bắt đầu triển khai AI Assistant.

## 7. Giao ước Adapter nhà cung cấp LLM (Provider Adapter Contract)

Adapter kết nối nhà cung cấp LLM là thành phần nội bộ; không có API của nhà cung cấp nào được để lộ ra bảng điều khiển dashboard.

### 7.1 Chính sách nhà cung cấp (`ProviderPolicy`)

| Trường | Kiểu dữ liệu | Bắt buộc | Quy tắc nghiệp vụ |
|---|---|---:|---|
| `policyVersion` | string | Có | Phiên bản chính sách bất biến |
| `providerName` | enum | Có | Mặc định `DEEPSEEK` trong MVP |
| `modelReference` | string | Có | Tham chiếu cấu hình mô hình; không chứa bí mật |
| `regionPolicy` | string | Có | Chính sách khu vực / lưu lượng ra ngoài cho phép |
| `promptPolicyVersion` | string | Có | Phiên bản chính sách che giấu dữ liệu / prompt hệ thống |
| `maxInputTokens` | int | Có | Giới hạn cứng token đầu vào |
| `maxOutputTokens` | int | Có | Giới hạn cứng token đầu ra |
| `requestTimeoutMs` | int | Có | Thời gian chờ tối đa trong MVP ≤ 8.000 ms |
| `maxRetries` | int | Có | Số lần thử lại tối đa trong MVP ≤ 1 lần |
| `dailyBudgetMinor` | int64 | Có | Hạn mức ngân sách chi phí theo tenant/nhà cung cấp |
| `budgetCurrency` | string | Có | Loại tiền tệ hạch toán chi phí nhà cung cấp LLM |
| `secretReference` | string | Có | Chỉ lưu tham chiếu tới Secrets Manager / KMS |
| `enabled` | boolean | Có | Cờ tính năng kích hoạt |

Tuyệt đối không có cột nào trong bảng chính sách nhà cung cấp lưu trữ API key, bí mật prompt, phản hồi thô của nhà cung cấp hay tin nhắn người dùng.

### 7.2 Yêu cầu và phản hồi với nhà cung cấp LLM

Yêu cầu gửi sang nhà cung cấp chứa `policyVersion`, ngôn ngữ an toàn, ngữ cảnh ngữ nghĩa có cấu trúc, câu hỏi đã che giấu thông tin nhạy cảm hiện tại, schema công cụ và các dữ kiện tổng hợp. Tuyệt đối không chứa chuỗi JWT, số điện thoại, danh tính khách hàng, ID đơn hàng/thanh toán thô, câu lệnh SQL, bí mật prompt hoặc các dòng báo cáo tùy ý.

Phản hồi từ nhà cung cấp chỉ được chấp nhận nếu vượt qua một trong các schema sau:

- `FunctionCallSchema`: Tên công cụ đã biết + đối số JSON hợp lệ;
- `AnswerSchema`: Văn bản / tiêu đề / cảnh báo / tham chiếu nguồn khớp chính xác với dữ kiện cấu trúc;
- `RefusalSchema`: Mã lý do từ chối an toàn.

Hệ thống cho phép tối đa 1 lần gọi sửa lỗi có ranh giới. Nếu sau khi sửa vẫn mâu thuẫn, hệ thống trả về mã `provider_output_invalid` và cấu trúc dữ kiện phi văn xuôi an toàn nếu có sẵn; tuyệt đối không bao giờ lưu trữ hoặc hiển thị văn bản chưa qua kiểm định của mô hình.

### 7.3 Sử dụng nhà cung cấp và đặt trước ngân sách (Provider Usage & Budget Reservation)

Mỗi lần thử gọi nhà cung cấp đều có cặp định danh duy nhất `providerRequestId` và `attemptId`. Trước khi phát yêu cầu đi, adapter sẽ thực hiện đặt trước (reserve) chi phí ước tính một cách nguyên tử đối chiếu với chính sách hiện hành của tenant. Sau khi nhận được phản hồi, khoản đặt trước sẽ được quyết toán với mức tiêu thụ thực tế hoặc được giải phóng nếu gặp lỗi truyền tải mạng.

```json
{
  "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789001",
  "policyVersion": "ai-provider-policy-v1",
  "attemptId": "0192b2d0-7f1d-7abc-8d2c-123456789060",
  "providerRequestId": "0192b2d0-7f1d-7abc-8d2c-123456789061",
  "estimatedCostMinor": 12,
  "budgetCurrency": "USD"
}
```

Kết quả đặt trước nhận một trong các trạng thái: `RESERVED`, `BUDGET_EXCEEDED` hoặc `POLICY_DISABLED`. Các yêu cầu đặt trước đồng thời được tuần tự hóa theo từng bộ `(tenant_id, policy_version, business_date)`; lần thử gọi thất bại hoặc hết hạn không thể tiêu hao cùng một khoản đặt trước hai lần. Bản ghi sử dụng lưu trữ số token đầu vào/đầu ra ước tính và thực tế, chi phí thực tế, loại tiền tệ, số lần thử lại, ID yêu cầu nhà cung cấp, phiên bản chính sách và kết quả xử lý.

## 8. Giao ước tác vụ tóm tắt ngày (Daily Summary Job Contracts)

### 8.1 API tác vụ nội bộ

| Phương thức | Đường dẫn | Bên gọi | Mã phản hồi |
|---|---|---|---|
| POST | `/internal/v1/ai/summary-jobs` | Scheduler / Vận hành | 201/202, 409 |
| GET | `/internal/v1/ai/summary-jobs/{jobId}` | Worker / Vận hành | 200, 404 |
| POST | `/internal/v1/ai/summary-jobs/{jobId}/run` | Worker tóm tắt | 202, 409, 503 |
| POST | `/internal/v1/ai/provider-health` | Vận hành / Worker | 200, 403, 503 |

### 8.2 Tạo tác vụ mới

```json
{
  "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789001",
  "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789002",
  "businessDate": "2026-10-12",
  "branchTimezone": "Asia/Ho_Chi_Minh",
  "summaryPolicyVersion": "summary-policy-v1",
  "cutoffAt": "2026-10-12T17:15:00Z",
  "sourceWatermarkHint": "reporting:branch:42:v901"
}
```

Khóa nghiệp vụ duy nhất: `(tenantId, branchId, businessDate, summaryPolicyVersion)`. Scheduler khi gửi lại yêu cầu sẽ nhận lại bản ghi tác vụ đã tồn tại.

### 8.3 Trạng thái của bản tóm tắt ngày

`SCHEDULED`, `WAITING_FOR_DATA`, `READY`, `GENERATING`, `PUBLISHED`, `NO_BASELINE`, `DATA_LATE`, `PROVIDER_UNAVAILABLE`, `FAILED`, `SUPERSEDED`.

Trạng thái `DATA_LATE` là trạng thái kết thúc của lần chạy hiện tại; chỉ có lần chạy mới với mốc kiểm chứng nguồn mới hơn mới được phép tạo ra phiên bản hiệu chỉnh mới.

## 9. Giao ước sự kiện (Event Contracts)

### 9.1 Phong bì sự kiện chung (Common Envelope)

```json
{
  "eventId": "0192b2d0-7f1d-7abc-8d2c-123456789050",
  "eventType": "AIReportTurnCompleted.v1",
  "eventVersion": 1,
  "occurredAt": "2026-10-13T08:00:05Z",
  "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789001",
  "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789002",
  "aggregateType": "AI_TURN",
  "aggregateId": "0192b2d0-7f1d-7abc-8d2c-123456789011",
  "aggregateVersion": 1,
  "scopeSummary": {
    "scopeType": "BRANCH",
    "branchCount": 1,
    "authorizationVersion": 7
  },
  "auditReferenceId": "0192b2d0-7f1d-7abc-8d2c-123456789051",
  "correlationId": "0192b2d0-7f1d-7abc-8d2c-123456789020",
  "causationId": "0192b2d0-7f1d-7abc-8d2c-123456789021",
  "producer": "ai-assistant-service",
  "payload": {}
}
```

Trường `branchId` chỉ được phép null đối với các sự kiện toàn tenant được phân quyền tường minh; payload vẫn phải mang loại phạm vi an toàn và số lượng chi nhánh, không bao giờ mang danh sách chi nhánh ngoài quyền.

Trường `scopeSummary` là bắt buộc trên mọi sự kiện do AI phát ra và chỉ chứa `scopeType`, `branchCount` và `authorizationVersion`. Trường `auditReferenceId` là bắt buộc đối với mọi hành động của người dùng, nhà cung cấp hoặc tóm tắt và trỏ tới bản ghi kiểm toán bất biến đã che giấu thông tin nhạy cảm. Trường `aggregateVersion` là phiên bản trạng thái cục bộ sinh ra sự kiện; không phải phiên bản của nhà cung cấp hay bản chiếu Reporting.

### 9.2 Các sự kiện tiếp nhận (Consumed Events)

| Sự kiện | Bên phát | Mục đích sử dụng | Quy tắc nghiệp vụ |
|---|---|---|---|
| `AuthorizationContextChanged.v1` | Identity | Hủy bỏ bộ nhớ cache tối ưu | Không bao giờ là thẩm quyền duy nhất |
| `ReportProjectionWatermarkUpdated.v1` | Reporting | Kích hoạt đánh giá lại độ tươi mới / tóm tắt | Truy vấn Reporting vẫn là thẩm quyền tối cao |
| `BusinessDayReady.v1` | Chính sách Reporting/Shift | Gợi ý cho bộ lập lịch tóm tắt | Bắt buộc endpoint tính đầy đủ phải xác nhận |

AI Assistant tuyệt đối không tiêu thụ các sự kiện `OrderPaid.v1` hoặc sự kiện thanh toán thô để tính toán số liệu; Reporting sở hữu hoàn toàn các bản chiếu này.

### 9.3 Các sự kiện phát ra (Produced Events)

| Sự kiện | Payload tối thiểu | Thành phần tiếp nhận |
|---|---|---|
| `AIReportTurnCompleted.v1` | ID turn/hội thoại, vai trò, `scopeSummary`, trạng thái, tên các công cụ, mốc nguồn/phiên bản, mã băm phản hồi, phiên bản chính sách/mô hình, `auditReferenceId` | Audit, Reporting/Vận hành |
| `AIAccessRefused.v1` | ID turn/hội thoại, `scopeSummary`, mã từ chối, phiên bản quyền/chính sách, `auditReferenceId`, correlation ID | Audit / An ninh |
| `AIDailySummaryPublished.v1` | ID tóm tắt, tenant/chi nhánh/ngày, số revision, loại mốc so sánh, mốc nguồn/phiên bản, phiên bản tính toán/chính sách/mô hình, `scopeSummary`, `auditReferenceId` | Dashboard/Reporting, Audit |
| `AIDailySummarySuperseded.v1` | ID tóm tắt cũ/mới, ngày/chi nhánh, mốc nguồn/phiên bản cũ/mới, lý do, số revision, `auditReferenceId` | Dashboard/Reporting, Audit |

Tuyệt đối không có bất kỳ sự kiện nào chứa câu hỏi thô, prompt thô, phản hồi thô, số điện thoại, tên khách hàng, thông tin thanh toán, mã SQL, bí mật nhà cung cấp, artifact mô hình hoặc dữ liệu dòng món tùy ý.

### 9.4 Quy tắc chuyển phát sự kiện (Event Delivery Rules)

- Bảng Outbox ghi nhận sự kiện cùng một giao dịch (transaction) với lần chuyển trạng thái cục bộ.
- Định danh sự kiện `eventId` ổn định qua các lần thử lại hoặc phát lại; thời điểm xảy ra `occurredAt` giữ nguyên mốc thời gian nghiệp vụ ban đầu.
- Phía tiếp nhận tiến hành khử trùng lặp theo `eventId` và xác thực phiên bản tenant/aggregate.
- Sự kiện có phiên bản không xác định sẽ được đưa vào cách ly hoặc hàng đợi thư chết (DLQ); không tự ý cập nhật trạng thái ngầm định.
- Việc phát lại (replay) bảo toàn ID ban đầu và không thể phát hành bản tóm tắt thứ hai nếu không có khóa nguồn/revision rõ ràng.
- AsyncAPI payload schema là nguồn chuẩn mực cho các trường dữ liệu trên; các mô tả bảng không thay thế được mã sinh giao ước.

## 10. Giao ước cơ sở dữ liệu PostgreSQL (PostgreSQL Data Contract)

Cơ sở dữ liệu: PostgreSQL thuộc sở hữu độc quyền của AI Assistant Service. Mọi cột trong các bảng đều bắt buộc phải có câu lệnh `COMMENT ON COLUMN` mô tả đúng nghiệp vụ dưới đây.

### 10.1 `ai_conversations`

| Tên cột | Kiểu dữ liệu | Cho phép Null | Mô tả / Ghi chú |
|---|---|---:|---|
| `id` | UUID | Không | UUID v7 định danh cuộc hội thoại |
| `tenant_id` | UUID | Không | Định danh tenant sở hữu |
| `created_by_subject_hash` | TEXT | Không | Mã băm có muối / tham chiếu người dùng mờ; cấm lưu Cognito subject thô |
| `role` | TEXT | Không | Vai trò `OWNER` hoặc `MANAGER` lúc tạo; chỉ dùng làm bản chụp kiểm toán |
| `scope_type` | TEXT | Không | Loại phạm vi `TENANT` hoặc `BRANCH` |
| `policy_version` | TEXT | Không | Phiên bản chính sách công cụ / phân quyền AI |
| `authorization_version` | BIGINT | Không | Phiên bản ngữ cảnh phân quyền từ Identity |
| `status` | TEXT | Không | Trạng thái: `ACTIVE`, `EXPIRED`, `REVOKED` |
| `client_reference_hash` | TEXT | Có | Mã băm tham chiếu giao diện; không lưu PII thô |
| `expires_at` | TIMESTAMPTZ | Không | Thời hạn hết hạn của hội thoại (TTL) |
| `created_at` | TIMESTAMPTZ | Không | Thời điểm tạo tính theo chuẩn UTC |
| `updated_at` | TIMESTAMPTZ | Không | Thời điểm cập nhật trạng thái gần nhất |

### 10.2 `ai_scope_revisions`

| Tên cột | Kiểu dữ liệu | Cho phép Null | Mô tả / Ghi chú |
|---|---|---:|---|
| `id` | UUID | Không | Định danh bản hiệu chỉnh phạm vi |
| `tenant_id` | UUID | Không | Định danh tenant sở hữu |
| `conversation_id` | UUID | Không | Hội thoại cha sở hữu |
| `revision` | BIGINT | Không | Số hiệu bản hiệu chỉnh tăng đơn điệu |
| `scope_type` | TEXT | Không | Loại phạm vi: `TENANT` hoặc `BRANCH` |
| `branch_ids` | JSONB | Không | Bản chụp danh sách ID chi nhánh có quyền; cấm wildcard; cấm chi nhánh ngoài quyền |
| `role` | TEXT | Không | Bản chụp vai trò được phân quyền |
| `permission` | TEXT | Không | Quyền hạn dùng để ra quyết định |
| `membership_version` | BIGINT | Không | Phiên bản tư cách thành viên từ Identity |
| `authorization_version` | BIGINT | Không | Phiên bản quyết định từ Identity |
| `resolved_at` | TIMESTAMPTZ | Không | Thời điểm ra quyết định phân quyền |
| `expires_at` | TIMESTAMPTZ | Không | Thời điểm hết hạn của ngữ cảnh đã ký |
| `created_at` | TIMESTAMPTZ | Không | Thời điểm lưu bản ghi theo chuẩn UTC |

### 10.3 `ai_turns`

| Tên cột | Kiểu dữ liệu | Cho phép Null | Mô tả / Ghi chú |
|---|---|---:|---|
| `id` | UUID | Không | UUID v7 định danh lượt hỏi đáp |
| `tenant_id` | UUID | Không | Định danh tenant sở hữu |
| `conversation_id` | UUID | Không | Hội thoại cha sở hữu |
| `scope_revision_id` | UUID | Không | Bản hiệu chỉnh phạm vi bất biến được áp dụng |
| `client_turn_id` | TEXT | Không | Khóa lũy quyền phía client; không chứa PII |
| `question_hash` | TEXT | Không | Mã băm có muối của câu hỏi đã chuẩn hóa; không lưu câu hỏi thô |
| `semantic_context` | JSONB | Không | Ngữ cảnh ngữ nghĩa cấu trúc (ý định/thời gian/số liệu); cấm lưu transcript thô |
| `intent` | TEXT | Có | Ý định người dùng đã chuẩn hóa trong danh mục trắng |
| `requested_period` | JSONB | Có | Khoảng thời gian đã chuẩn hóa an toàn |
| `status` | TEXT | Không | Trạng thái máy trạng thái của lượt hỏi |
| `answer_payload` | JSONB | Có | Câu trả lời an toàn đã qua kiểm định/che giấu |
| `response_hash` | TEXT | Có | Mã băm của câu trả lời an toàn đã được chấp nhận |
| `warning_codes` | JSONB | Không | Danh sách mã cảnh báo độ tươi mới/nhà cung cấp/chính sách |
| `policy_version` | TEXT | Không | Phiên bản chính sách công cụ/prompt/che giấu |
| `model_version` | TEXT | Có | Tham chiếu mô hình LLM; không chứa bí mật |
| `attempt_count` | INTEGER | Không | Số lần thử của bộ điều phối (có giới hạn) |
| `expires_at` | TIMESTAMPTZ | Không | Thời điểm hết hạn lượt hỏi / phiên |
| `created_at` | TIMESTAMPTZ | Không | Thời điểm tạo tính theo chuẩn UTC |
| `updated_at` | TIMESTAMPTZ | Không | Thời điểm cập nhật trạng thái gần nhất |

Khóa nguồn duy nhất: `(tenant_id, conversation_id, client_turn_id)`.

### 10.4 `ai_tool_executions`

| Tên cột | Kiểu dữ liệu | Cho phép Null | Mô tả / Ghi chú |
|---|---|---:|---|
| `id` | UUID | Không | Định danh lần thực thi công cụ |
| `tenant_id` | UUID | Không | Định danh tenant sở hữu |
| `turn_id` | UUID | Không | Lượt hỏi cha sở hữu |
| `sequence_no` | INTEGER | Không | Thứ tự gọi trong lượt: `1..maxToolCallsPerTurn` |
| `tool_name` | TEXT | Không | Tên công cụ nằm trong danh mục trắng |
| `tool_version` | TEXT | Không | Phiên bản schema/chính sách công cụ |
| `validated_arguments` | JSONB | Không | Đối số an toàn đã được máy chủ thẩm định; cấm SQL/phạm vi quyền |
| `status` | TEXT | Không | Trạng thái thực thi công cụ |
| `source_watermark` | TEXT | Có | Mốc kiểm chứng nguồn từ Reporting |
| `freshness_status` | TEXT | Có | Trạng thái chất lượng dữ liệu do Reporting trả về |
| `calculation_version` | TEXT | Có | Phiên bản công thức tính số liệu từ Reporting |
| `result_summary` | JSONB | Có | Tóm tắt tổng hợp đã che giấu có ranh giới; không lưu dòng thô |
| `error_code` | TEXT | Có | Mã lỗi có phân loại an toàn |
| `latency_ms` | BIGINT | Có | Thời gian thực thi (mili-giây) |
| `created_at` | TIMESTAMPTZ | Không | Thời điểm thực thi theo chuẩn UTC |

Chỉ mục duy nhất `(tenant_id, turn_id, sequence_no)`; trường `validated_arguments` bắt buộc phải vượt qua kiểm tra schema/chính sách trước khi thực hiện cuộc gọi.

### 10.5 `ai_daily_summary_jobs`

| Tên cột | Kiểu dữ liệu | Cho phép Null | Mô tả / Ghi chú |
|---|---|---:|---|
| `id` | UUID | Không | UUID v7 định danh tác vụ tóm tắt |
| `tenant_id` | UUID | Không | Định danh tenant sở hữu |
| `branch_id` | UUID | Không | Phạm vi chi nhánh áp dụng |
| `business_date` | DATE | Không | Ngày kinh doanh địa phương của chi nhánh |
| `branch_timezone` | TEXT | Không | Bản chụp múi giờ IANA của chi nhánh |
| `summary_policy_version` | TEXT | Không | Chính sách mốc so sánh / giờ chốt ca / trọng yếu |
| `status` | TEXT | Không | Trạng thái: scheduled, waiting, ready, data-late, terminal |
| `source_watermark_hint` | TEXT | Có | Gợi ý mốc nguồn từ bộ lập lịch, không phải thẩm quyền |
| `source_watermark_used` | TEXT | Có | Mốc nguồn thực tế đã sử dụng để sinh báo cáo |
| `attempt_count` | INTEGER | Không | Số lần thực hiện tác vụ (có giới hạn) |
| `next_attempt_at` | TIMESTAMPTZ | Có | Thời điểm thử lại tiếp theo |
| `lease_owner` | TEXT | Có | Định danh worker đang giữ khóa thuê (phi bí mật) |
| `lease_expires_at` | TIMESTAMPTZ | Có | Thời hạn hết hạn của khóa thuê worker |
| `last_error_code` | TEXT | Có | Mã lỗi kết thúc hoặc lỗi cần thử lại an toàn |
| `created_at` | TIMESTAMPTZ | Không | Thời điểm tạo tác vụ theo chuẩn UTC |
| `updated_at` | TIMESTAMPTZ | Không | Thời điểm chuyển trạng thái tác vụ gần nhất |

Khóa nghiệp vụ duy nhất: `(tenant_id, branch_id, business_date, summary_policy_version)`.

### 10.6 `ai_daily_summaries`

| Tên cột | Kiểu dữ liệu | Cho phép Null | Mô tả / Ghi chú |
|---|---|---:|---|
| `id` | UUID | Không | UUID v7 định danh bản tóm tắt |
| `tenant_id` | UUID | Không | Định danh tenant sở hữu |
| `branch_id` | UUID | Không | Phạm vi chi nhánh áp dụng |
| `job_id` | UUID | Không | Tác vụ nguồn sinh ra bản tóm tắt này |
| `business_date` | DATE | Không | Ngày kinh doanh địa phương |
| `summary_policy_version` | TEXT | Không | Khóa chính sách tóm tắt tác vụ đã dùng |
| `revision` | BIGINT | Không | Số hiệu phiên bản hiệu chỉnh tăng đơn điệu |
| `status` | TEXT | Không | Trạng thái: `PUBLISHED`, `NO_BASELINE`, `DATA_LATE`, `PROVIDER_UNAVAILABLE`, `FAILED`, `SUPERSEDED` |
| `baseline_type` | TEXT | Có | Loại mốc so sánh: cùng thứ tuần trước, 7 ngày, không có |
| `baseline_period` | JSONB | Có | Các ngày so sánh chính xác |
| `safe_content` | JSONB | Không | Nội dung an toàn: tiêu đề, số liệu, đóng góp, lưu ý |
| `source_watermark` | TEXT | Không | Mốc kiểm chứng nguồn Reporting |
| `source_watermark_version` | BIGINT | Không | Phiên bản mốc kiểm chứng nguồn tăng đơn điệu để xác định thứ tự thay thế |
| `calculation_version` | TEXT | Không | Phiên bản số liệu / phân tích đóng góp |
| `policy_version` | TEXT | Không | Phiên bản chính sách tóm tắt/prompt/che giấu |
| `model_version` | TEXT | Có | Tham chiếu mô hình LLM đã dùng |
| `supersedes_summary_id` | UUID | Có | Bản hiệu chỉnh bất biến trước đó bị thay thế |
| `superseded_by_summary_id` | UUID | Có | Bản hiệu chỉnh mới hơn đã thay thế bản này |
| `response_hash` | TEXT | Không | Mã băm của nội dung an toàn |
| `generated_at` | TIMESTAMPTZ | Không | Thời điểm sinh báo cáo theo chuẩn UTC |
| `published_at` | TIMESTAMPTZ | Có | Thời điểm công bố báo cáo theo chuẩn UTC |
| `created_at` | TIMESTAMPTZ | Không | Thời điểm lưu bản ghi theo chuẩn UTC |

Tuyệt đối không lưu prompt thô hoặc phản hồi thô của nhà cung cấp LLM trong trường `safe_content`.

### 10.7 `ai_summary_sources`

| Tên cột | Kiểu dữ liệu | Cho phép Null | Mô tả / Ghi chú |
|---|---|---:|---|
| `id` | UUID | Không | Định danh tham chiếu nguồn |
| `tenant_id` | UUID | Không | Định danh tenant sở hữu |
| `summary_id` | UUID | Không | Bản tóm tắt cha sở hữu |
| `tool_name` | TEXT | Không | Tên công cụ Reporting nguồn |
| `watermark` | TEXT | Không | Mốc kiểm chứng nguồn |
| `watermark_version` | BIGINT | Không | Phiên bản mốc nguồn tăng đơn điệu phục vụ phả hệ |
| `projection_version` | BIGINT | Không | Phiên bản bản chiếu nguồn |
| `last_updated_at` | TIMESTAMPTZ | Không | Độ tươi mới của nguồn |
| `freshness_status` | TEXT | Không | Trạng thái chất lượng dữ liệu |
| `calculation_version` | TEXT | Không | Phiên bản tính toán số liệu |
| `created_at` | TIMESTAMPTZ | Không | Thời điểm lưu bản ghi theo chuẩn UTC |

### 10.8 `ai_provider_policies`

| Tên cột | Kiểu dữ liệu | Cho phép Null | Mô tả / Ghi chú |
|---|---|---:|---|
| `id` | UUID | Không | Định danh dòng chính sách |
| `tenant_id` | UUID | Không | Tenant sở hữu; có thể tham chiếu mặc định nền tảng |
| `policy_version` | TEXT | Không | Phiên bản chính sách bất biến |
| `provider_name` | TEXT | Không | Tên nhà cung cấp (`DEEPSEEK` trong MVP) |
| `model_reference` | TEXT | Không | Tham chiếu cấu hình mô hình (phi bí mật) |
| `region_policy` | TEXT | Không | Chính sách khu vực / lưu lượng ra ngoài cho phép |
| `prompt_policy_version` | TEXT | Không | Chính sách prompt hệ thống / che giấu dữ liệu |
| `max_input_tokens` | INTEGER | Không | Giới hạn cứng token đầu vào |
| `max_output_tokens` | INTEGER | Không | Giới hạn cứng token đầu ra |
| `request_timeout_ms` | INTEGER | Không | Thời gian chờ tối đa khi gọi nhà cung cấp |
| `max_retries` | INTEGER | Không | Số lần thử lại tối đa (có giới hạn) |
| `daily_budget_minor` | BIGINT | Không | Hạn mức ngân sách chi phí theo loại tiền cấu hình |
| `budget_currency` | TEXT | Không | Loại tiền tệ hạch toán chi phí LLM; không phải doanh thu POS |
| `secret_reference` | TEXT | Không | Tham chiếu Secrets Manager/KMS; không lưu secret |
| `enabled` | BOOLEAN | Không | Cờ tính năng kích hoạt |
| `created_by_user_id` | UUID | Có | Tác nhân thiết lập chính sách; null nếu là dữ liệu hạt giống hệ thống |
| `created_at` | TIMESTAMPTZ | Không | Thời điểm tạo theo chuẩn UTC |
| `updated_at` | TIMESTAMPTZ | Không | Thời điểm cập nhật theo chuẩn UTC |

### 10.9 `ai_provider_usage`

| Tên cột | Kiểu dữ liệu | Cho phép Null | Mô tả / Ghi chú |
|---|---|---:|---|
| `id` | UUID | Không | UUID v7 định danh bản ghi đặt trước / sử dụng |
| `tenant_id` | UUID | Không | Định danh tenant sở hữu |
| `policy_version` | TEXT | Không | Chính sách nhà cung cấp bất biến có hiệu lực |
| `business_date` | DATE | Không | Ngày hạch toán ngân sách theo múi giờ chính sách tenant |
| `attempt_id` | UUID | Không | Định danh một lần thử gọi nhà cung cấp có ranh giới |
| `provider_request_id` | UUID | Không | ID tương quan của yêu cầu gửi nhà cung cấp |
| `reservation_status` | TEXT | Không | Trạng thái: `RESERVED`, `FINALIZED`, `RELEASED`, `BUDGET_EXCEEDED`, `POLICY_DISABLED` |
| `estimated_cost_minor` | BIGINT | Không | Số tiền đặt trước nguyên tử |
| `actual_cost_minor` | BIGINT | Có | Chi phí thực tế cuối cùng nếu xác định được |
| `budget_currency` | TEXT | Không | Loại tiền tệ hạch toán chi phí |
| `input_tokens` | INTEGER | Có | Số lượng token đầu vào thực tế |
| `output_tokens` | INTEGER | Có | Số lượng token đầu ra thực tế |
| `retry_no` | INTEGER | Không | Số thứ tự lần thử lại (bắt đầu từ 0) |
| `outcome` | TEXT | Có | Kết quả: `SUCCESS`, `TIMEOUT`, `INVALID_OUTPUT`, `CIRCUIT_OPEN`, `ERROR` |
| `created_at` | TIMESTAMPTZ | Không | Thời điểm đặt trước theo chuẩn UTC |
| `finalized_at` | TIMESTAMPTZ | Có | Thời điểm quyết toán / giải phóng theo UTC |

Các ràng buộc duy nhất `(tenant_id, attempt_id)` và `(tenant_id, provider_request_id)` đảm bảo ngăn chặn việc tính phí hai lần hoặc quyết toán lặp lại.

### 10.10 `ai_audit_references`

| Tên cột | Kiểu dữ liệu | Cho phép Null | Mô tả / Ghi chú |
|---|---|---:|---|
| `id` | UUID | Không | Định danh bản ghi tham chiếu kiểm toán |
| `tenant_id` | UUID | Không | Định danh tenant sở hữu |
| `branch_id` | UUID | Có | Phạm vi chi nhánh nếu áp dụng |
| `actor_subject_hash` | TEXT | Có | Mã băm có muối / tham chiếu tác nhân an toàn |
| `role` | TEXT | Có | Bản chụp vai trò tác nhân |
| `action` | TEXT | Không | Hành động: lượt hỏi, công cụ, từ chối, tóm tắt |
| `resource_type` | TEXT | Không | Loại tài nguyên: conversation, turn, tool, summary, policy |
| `resource_id` | UUID | Không | Định danh tài nguyên liên quan |
| `scope_summary` | JSONB | Không | Tóm tắt phạm vi an toàn: loại/số lượng/phiên bản; không lưu chi nhánh ngoài quyền |
| `policy_decision` | TEXT | Không | Lý do cho phép, từ chối hoặc chặn |
| `tool_summary` | JSONB | Có | Chỉ chứa tên công cụ, trạng thái và độ trễ |
| `source_summary` | JSONB | Có | Mốc nguồn, độ tươi mới và phiên bản tính toán |
| `response_hash` | TEXT | Có | Mã băm của kết quả an toàn |
| `correlation_id` | UUID | Không | Mã định danh truy vết |
| `created_at` | TIMESTAMPTZ | Không | Thời điểm kiểm toán bất biến |

### 10.11 Các bảng phục vụ độ tin cậy (Reliability Tables)

Các bảng `outbox_messages`, `inbox_events` và `idempotency_records` tuân thủ các quy ước chuẩn của nền tảng:

| Bảng | Các trường bắt buộc |
|---|---|
| `outbox_messages` | ID sự kiện, tenant, loại/ID/phiên bản aggregate, loại/phiên bản sự kiện, payload an toàn bất biến, thời điểm phát, số lần thử, thời điểm thử tiếp theo |
| `inbox_events` | ID sự kiện nguồn, tenant, nguồn/phiên bản, trạng thái, thời điểm xử lý, mã lỗi an toàn |
| `idempotency_records` | Tenant, thao tác, khóa lũy quyền, mã băm yêu cầu chuẩn, trạng thái/thân phản hồi hoặc ID tài nguyên, thời hạn hết hạn |

Tuyệt đối không có payload tin cậy nào được chứa prompt thô, phản hồi LLM thô, số điện thoại, chuỗi JWT, khóa bí mật, câu lệnh SQL hoặc dữ liệu giao dịch thô.

## 11. Ràng buộc, chỉ mục, thời hạn lưu trữ và Migration (Constraints, Indexes, Retention & Migration)

### 11.1 Ràng buộc và chỉ mục bắt buộc (Required Constraints/Indexes)

- Mọi bảng đều có trường `tenant_id`; các bảng theo phạm vi chi nhánh bắt buộc có `branch_id`.
- `ai_conversations` và `ai_turns` bắt buộc phải kiểm tra quyền sở hữu tác nhân/phạm vi ở mọi thao tác đọc công khai.
- Khóa duy nhất cho lượt hỏi: `(tenant_id, conversation_id, client_turn_id)`.
- Khóa duy nhất cho tác vụ tóm tắt ngày: `(tenant_id, branch_id, business_date, summary_policy_version)`.
- Chỉ tồn tại một phiên bản tóm tắt hiện hành duy nhất cho mỗi bộ `(tenant_id, branch_id, business_date, summary_policy_version)`; các phiên bản cũ bị thay thế là bất biến.
- Trường `ai_tool_executions.sequence_no` là duy nhất trong một lượt hỏi và bị chặn trần bởi chính sách.
- Chính sách nhà cung cấp là bất biến theo `policy_version`; việc kích hoạt áp dụng cờ tính năng hoặc phiên bản lạc quan.
- Danh sách chi nhánh trong bản hiệu chỉnh phạm vi phải thuộc cùng một tenant; phạm vi `TENANT` chỉ áp dụng cho Owner.
- Các trường `safe_content`, ngữ cảnh ngữ nghĩa và tóm tắt kết quả bắt buộc phải vượt qua bước kiểm định JSON Schema trước khi commit.
- Tuyệt đối cấm tồn tại cột có tên `phone`, prompt thô, phản hồi thô, JWT, API key, SQL hoặc bí mật nhà cung cấp trong schema của AI.
- Mốc kiểm chứng nguồn và phiên bản mốc nguồn phải tăng đơn điệu trong phả hệ tóm tắt ngày; mốc nguồn cũ hơn không thể thay thế phiên bản mới hơn.
- Bảng `ai_provider_usage` có duy nhất một lần đặt trước/quyết toán cho mỗi bộ `(tenant_id, attempt_id)` và một yêu cầu cho mỗi bộ `(tenant_id, provider_request_id)`.

Phả hệ tóm tắt ngày được bảo vệ bởi các ràng buộc mức migration sau (tên ràng buộc mang tính chuẩn mực; cú pháp SQL có thể điều chỉnh tương đương trên PostgreSQL):

```sql
ALTER TABLE ai_daily_summaries
  ADD CONSTRAINT ck_ai_summary_revision_positive CHECK (revision > 0),
  ADD CONSTRAINT ck_ai_summary_watermark_version_positive CHECK (source_watermark_version >= 0),
  ADD CONSTRAINT ck_ai_summary_supersede_links CHECK (
    (status = 'SUPERSEDED' AND superseded_by_summary_id IS NOT NULL)
    OR (status <> 'SUPERSEDED' AND superseded_by_summary_id IS NULL)
  );

CREATE UNIQUE INDEX uq_ai_summary_revision
  ON ai_daily_summaries (tenant_id, branch_id, business_date, summary_policy_version, revision);

CREATE UNIQUE INDEX uq_ai_summary_current
  ON ai_daily_summaries (tenant_id, branch_id, business_date, summary_policy_version)
  WHERE status <> 'SUPERSEDED';
```

Đối với trường hợp cập nhật số liệu phát sinh muộn, worker sẽ khóa bản ghi phả hệ, kiểm tra `new.source_watermark_version > current.source_watermark_version`, đánh dấu dòng hiện tại thành `SUPERSEDED`, chèn dòng mới bất biến và ghi sự kiện `AIDailySummarySuperseded.v1` trong cùng một giao dịch. Bất kỳ xung đột tính duy nhất hay sai lệch mốc nguồn nào đều sẽ rollback toàn bộ thay đổi. Trường `expectedRevision` và khóa lũy quyền được kiểm tra trước khi thực hiện lock; việc gửi lặp yêu cầu sẽ trả về tác vụ/phiên bản gốc ban đầu.

### 11.2 Thời hạn lưu trữ và phân vùng dữ liệu (Retention & Partition)

- Thời gian lưu trữ prompt thô và phản hồi thô từ nhà cung cấp: Mặc định bằng 0.
- Ngữ cảnh ngữ nghĩa có cấu trúc và metadata an toàn của lượt hỏi: Mặc định lưu 90 ngày, có thể cấu hình theo chính sách tenant kèm cơ chế xóa/tombstone.
- Nội dung an toàn của bản tóm tắt: Lưu trữ theo chính sách nghiệp vụ của Dashboard/Reporting; các phiên bản hiệu chỉnh được giữ lại trong khung thời gian hiệu chỉnh/kiểm toán đã cấu hình.
- Bản ghi tham chiếu kiểm toán tuân theo thời hạn lưu trữ pháp lý của hệ thống Audit cha và chỉ chứa bằng chứng đã che giấu.
- Ứng viên phân vùng RANGE theo tháng: `ai_turns`, `ai_tool_executions`, `ai_audit_references` chỉ áp dụng sau khi có số liệu đo đạc thực tế về khối lượng dữ liệu; mặc định trong MVP chưa cần phân vùng.
- Cơ chế xóa hoặc đánh dấu xóa (tombstone) phải đảm bảo loại bỏ khả năng tái sử dụng dữ liệu cho việc huấn luyện mô hình trong tương lai và vô hiệu hóa phạm vi phiên hội thoại; không được xóa các bằng chứng kiểm toán đã che giấu bắt buộc theo quy định pháp lý.

### 11.3 Ma trận thời hạn lưu trữ dữ liệu (Retention Matrix)

| Phân loại dữ liệu / Bảng | Thời hạn lưu trữ mặc định | Xóa / Đánh dấu xóa (Tombstone) | Lưu trữ pháp lý (Legal Hold) | Sử dụng cho huấn luyện / Mô hình |
|---|---|---|---|---|
| Câu hỏi thô, prompt thô, phản hồi LLM thô | 0 ngày | Tuyệt đối không lưu; bộ nhớ đệm truyền tải bị xóa ngay sau request | Dịch vụ này không cho phép lưu trữ thô vì mục đích pháp lý | Bị cấm tuyệt đối |
| `ai_turns.semantic_context`, `answer_payload`, `ai_tool_executions.result_summary` | 90 ngày | Xóa hoặc tombstone bằng mật mã; vô hiệu hóa phạm vi hội thoại | Chỉ bảo lưu các trường đã che giấu thông tin nhạy cảm khi có lệnh giữ pháp lý | `not_for_training=true`; cấm gửi nội dung lịch sử cho nhà cung cấp |
| `ai_daily_summaries`, `ai_summary_sources` | Theo chính sách Dashboard/Reporting, mặc định 24 tháng | Chỉ tombstone sau khi hết hạn chính sách; bảo toàn phả hệ phiên bản | Lệnh giữ pháp lý có quyền ưu tiên hơn việc xóa đối với nội dung an toàn | Chỉ dùng số liệu tổng hợp an toàn; mặc định không dùng làm tập dữ liệu huấn luyện |
| `ai_audit_references`, metadata sự kiện | Theo chính sách lưu trữ kiểm toán / pháp lý cha | Dịch vụ Audit sở hữu quyền quyết định xóa hoặc giữ lại | Chính sách của Audit cha là thẩm quyền tối cao | Bị cấm sử dụng cho huấn luyện |
| `ai_provider_usage` | 24 tháng hoặc theo chính sách tài chính | Xóa sau khi hoàn tất đối soát; giữ lại bằng chứng ngân sách tổng hợp | Áp dụng lệnh giữ tài chính / pháp lý | Bị cấm sử dụng cho huấn luyện |

Các tác vụ xóa dữ liệu đảm bảo tính lũy quyền, chỉ phát ra metadata xóa đã che giấu và không bao giờ kích hoạt lại việc sử dụng dữ liệu cho nhà cung cấp hay đào tạo mô hình. Lệnh giữ pháp lý (legal hold) phải được khai báo tường minh cho từng tài nguyên/phân loại dữ liệu; nó không cho phép giữ lại prompt thô vì việc lưu trữ prompt thô bị ngăn chặn từ mặt cấu trúc hệ thống.

### 11.4 Trình tự thực thi Migration (Migration Order)

1. Tạo các bảng kiểm toán / độ tin cậy chung và các ràng buộc theo tenant.
2. Tạo chính sách nhà cung cấp và nạp dữ liệu hạt giống registry (chỉ chứa tham chiếu khóa bí mật).
3. Tạo các bảng hội thoại, bản hiệu chỉnh phạm vi, lượt hỏi và ràng buộc tính lũy quyền duy nhất.
4. Tạo bảng thực thi công cụ và các chỉ mục có giới hạn.
5. Tạo các bảng tác vụ tóm tắt, bản tóm tắt ngày, nguồn tóm tắt và các ràng buộc phiên bản.
6. Tạo các chỉ mục sử dụng/ngân sách nhà cung cấp và các ràng buộc đặt trước chi phí.
7. Tạo các chỉ mục bảng outbox/inbox và các ràng buộc khóa thuê của worker.
8. Nạp dữ liệu tham chiếu hạt giống cho danh mục công cụ, chính sách và phiên bản tính toán; tuyệt đối không nạp nội dung người dùng.
9. Mặc định không cần backfill dữ liệu; việc xây dựng lại các sự kiện Reporting phải thông qua quy trình do chính Reporting sở hữu, không tự suy diễn từ cơ sở dữ liệu của AI.

### 11.5 Các kịch bản Migration Flyway (Flyway Migration Scripts)

#### 11.5.1 `V1__init_ai_core_and_conversations.sql`
```sql
CREATE TABLE ai_conversations (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    created_by_subject_hash TEXT NOT NULL,
    role TEXT NOT NULL CHECK (role IN ('OWNER', 'MANAGER')),
    scope_type TEXT NOT NULL CHECK (scope_type IN ('TENANT', 'BRANCH')),
    policy_version TEXT NOT NULL,
    authorization_version BIGINT NOT NULL,
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'EXPIRED', 'REVOKED')),
    client_reference_hash TEXT,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE ai_conversations IS 'Lưu trữ các phiên hội thoại hỏi đáp báo cáo AI có giới hạn phạm vi';
COMMENT ON COLUMN ai_conversations.created_by_subject_hash IS 'Mã băm có muối của người dùng; cấm lưu Cognito subject thô';

CREATE INDEX idx_ai_conv_tenant_status ON ai_conversations(tenant_id, status);
CREATE INDEX idx_ai_conv_expires ON ai_conversations(expires_at) WHERE status = 'ACTIVE';

CREATE TABLE ai_scope_revisions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    conversation_id UUID NOT NULL REFERENCES ai_conversations(id) ON DELETE CASCADE,
    revision BIGINT NOT NULL,
    scope_type TEXT NOT NULL CHECK (scope_type IN ('TENANT', 'BRANCH')),
    branch_ids JSONB NOT NULL,
    role TEXT NOT NULL,
    permission TEXT NOT NULL,
    membership_version BIGINT NOT NULL,
    authorization_version BIGINT NOT NULL,
    resolved_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ai_scope_rev UNIQUE (tenant_id, conversation_id, revision)
);

CREATE TABLE ai_turns (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    conversation_id UUID NOT NULL REFERENCES ai_conversations(id) ON DELETE CASCADE,
    scope_revision_id UUID NOT NULL REFERENCES ai_scope_revisions(id),
    client_turn_id TEXT NOT NULL,
    question_hash TEXT NOT NULL,
    semantic_context JSONB NOT NULL,
    intent TEXT,
    requested_period JSONB,
    status TEXT NOT NULL CHECK (status IN ('QUEUED', 'RUNNING', 'ANSWERED', 'NEEDS_SCOPE', 'UNSUPPORTED', 'NO_DATA', 'STALE_DATA', 'PARTIAL', 'PROVIDER_UNAVAILABLE', 'EXPIRED', 'FAILED')),
    answer_payload JSONB,
    response_hash TEXT,
    warning_codes JSONB NOT NULL DEFAULT '[]'::jsonb,
    policy_version TEXT NOT NULL,
    model_version TEXT,
    poll_after_ms INTEGER DEFAULT 500,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    answered_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_ai_turn_client UNIQUE (tenant_id, conversation_id, client_turn_id)
);

CREATE INDEX idx_ai_turns_conv_created ON ai_turns(tenant_id, conversation_id, created_at DESC);
```

#### 11.5.2 `V2__init_ai_tool_executions.sql`
```sql
CREATE TABLE ai_tool_executions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    turn_id UUID NOT NULL REFERENCES ai_turns(id) ON DELETE CASCADE,
    tool_name TEXT NOT NULL CHECK (tool_name IN ('getSalesSummary', 'getOrderCount', 'getAverageOrderValue', 'getTopItems', 'compareBranches', 'getHourlySales', 'getContributionDrivers')),
    execution_order INTEGER NOT NULL,
    safe_arguments JSONB NOT NULL,
    result_summary JSONB,
    source_watermark TEXT,
    watermark_version BIGINT,
    calculation_version TEXT,
    freshness_status TEXT CHECK (freshness_status IN ('FRESH', 'ACCEPTABLE', 'STALE', 'REBUILDING')),
    status TEXT NOT NULL CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'CIRCUIT_OPEN', 'INVALID_ARGUMENTS')),
    error_code TEXT,
    latency_ms INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ai_tool_turn_order UNIQUE (tenant_id, turn_id, execution_order)
);

CREATE INDEX idx_ai_tools_turn ON ai_tool_executions(tenant_id, turn_id);
```

#### 11.5.3 `V3__init_ai_daily_summaries.sql`
```sql
CREATE TABLE ai_daily_summary_jobs (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    business_date DATE NOT NULL,
    branch_timezone TEXT NOT NULL,
    cutoff_at TIMESTAMPTZ NOT NULL,
    summary_policy_version TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('SCHEDULED', 'WAITING_FOR_DATA', 'READY', 'GENERATING', 'PUBLISHED', 'NO_BASELINE', 'DATA_LATE', 'PROVIDER_UNAVAILABLE', 'FAILED', 'SUPERSEDED')),
    worker_lease_id UUID,
    lease_expires_at TIMESTAMPTZ,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    target_revision INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ai_job_target UNIQUE (tenant_id, branch_id, business_date, summary_policy_version, target_revision)
);

CREATE TABLE ai_daily_summaries (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    business_date DATE NOT NULL,
    timezone TEXT NOT NULL,
    revision INTEGER NOT NULL DEFAULT 1,
    status TEXT NOT NULL CHECK (status IN ('PUBLISHED', 'NO_BASELINE', 'DATA_LATE', 'PROVIDER_UNAVAILABLE', 'FAILED', 'SUPERSEDED')),
    supersedes_summary_id UUID REFERENCES ai_daily_summaries(id),
    superseded_by_summary_id UUID REFERENCES ai_daily_summaries(id),
    safe_content JSONB NOT NULL,
    watermark TEXT NOT NULL,
    calculation_version TEXT NOT NULL,
    policy_version TEXT NOT NULL,
    model_version TEXT NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ai_summary_branch_date_rev UNIQUE (tenant_id, branch_id, business_date, revision)
);

CREATE TABLE ai_summary_sources (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    summary_id UUID NOT NULL REFERENCES ai_daily_summaries(id) ON DELETE CASCADE,
    source_type TEXT NOT NULL,
    source_watermark TEXT NOT NULL,
    watermark_version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

#### 11.5.4 `V4__init_ai_provider_policies_and_usage.sql`
```sql
CREATE TABLE ai_provider_policies (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    policy_version TEXT NOT NULL,
    provider_name TEXT NOT NULL,
    model_name TEXT NOT NULL,
    region_policy TEXT NOT NULL,
    prompt_policy_version TEXT NOT NULL,
    max_input_tokens INTEGER NOT NULL,
    max_output_tokens INTEGER NOT NULL,
    request_timeout_ms INTEGER NOT NULL,
    max_retries INTEGER NOT NULL DEFAULT 1,
    daily_budget_minor BIGINT NOT NULL,
    budget_currency TEXT NOT NULL DEFAULT 'VND',
    secret_reference TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_by_user_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ai_policy_ver UNIQUE (tenant_id, policy_version)
);

CREATE TABLE ai_provider_usage (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    policy_version TEXT NOT NULL,
    business_date DATE NOT NULL,
    attempt_id UUID NOT NULL,
    provider_request_id UUID NOT NULL,
    reservation_status TEXT NOT NULL CHECK (reservation_status IN ('RESERVED', 'FINALIZED', 'RELEASED', 'BUDGET_EXCEEDED', 'POLICY_DISABLED')),
    estimated_cost_minor BIGINT NOT NULL,
    actual_cost_minor BIGINT,
    budget_currency TEXT NOT NULL DEFAULT 'VND',
    input_tokens INTEGER,
    output_tokens INTEGER,
    retry_no INTEGER NOT NULL DEFAULT 0,
    outcome TEXT CHECK (outcome IN ('SUCCESS', 'TIMEOUT', 'INVALID_OUTPUT', 'CIRCUIT_OPEN', 'ERROR')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finalized_at TIMESTAMPTZ,
    CONSTRAINT uq_ai_usage_attempt UNIQUE (tenant_id, attempt_id),
    CONSTRAINT uq_ai_usage_req UNIQUE (tenant_id, provider_request_id)
);

CREATE INDEX idx_ai_usage_budget ON ai_provider_usage(tenant_id, policy_version, business_date, reservation_status);
```

#### 11.5.5 `V5__init_ai_reliability_tables.sql`
```sql
CREATE TABLE ai_outbox_events (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    aggregate_type TEXT NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type TEXT NOT NULL,
    event_version INTEGER NOT NULL DEFAULT 1,
    payload JSONB NOT NULL,
    correlation_id UUID NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    retry_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ
);

CREATE INDEX idx_ai_outbox_pending ON ai_outbox_events(status, created_at) WHERE status = 'PENDING';

CREATE TABLE ai_inbox_dedup (
    event_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    event_type TEXT NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

## 12. Ma trận kiểm thử giao ước (Contract Test Matrix)

| Lĩnh vực | Các bộ Fixture kiểm thử bắt buộc |
|---|---|
| Phân quyền / Phạm vi | Phạm vi toàn tenant của Owner, phạm vi chi nhánh của Owner, chi nhánh được giao của Manager, chi nhánh ngoài quyền, tư cách thành viên bị thu hồi, phiên bản thành viên cũ |
| Chat API | Tạo hội thoại, lượt hỏi trả mã 200, lượt hỏi trả mã 202/poll, phiên hết hạn, phát lại lũy quyền, phân trang con trỏ |
| Danh mục công cụ | Công cụ không xác định, phiên bản bị vô hiệu hóa, JSON sai định dạng, đối số chứa SQL/tên bảng, kiểm tra biên ngày/phạm vi/giới hạn, vòng lặp tối đa |
| Reporting | Doanh thu `OrderPaid` chuẩn, sự kiện trùng lặp, thanh toán đối soát muộn, hoàn tiền toàn phần/từng phần, sự kiện điều chỉnh, mẫu số/làm tròn AOV, múi giờ theo giờ/DST, món bằng điểm, so sánh chi nhánh, dữ liệu rỗng, mốc kiểm chứng bị chậm/chưa đủ, sai lệch mã băm registry |
| An toàn prompt | Bỏ qua chính sách, tiết lộ prompt hệ thống, leo thang đặc quyền, yêu cầu thay đổi dữ liệu, chèn dữ liệu PII, đầu ra không an toàn, luận điểm số học sai lệch, rà soát số điện thoại/UUID/SQL |
| Nhà cung cấp LLM | Quá thời gian chờ, thử lại 1 lần, ngắt mạch mở, đặt trước ngân sách đồng thời, quyết toán chi phí thực tế, hạn mức token/chi phí, tương quan yêu cầu/lần thử, hàm sai định dạng, câu trả lời sai, sửa chữa sai lệch, không chuyển đổi nhà cung cấp |
| Hội thoại | Ngữ cảnh ngữ nghĩa cấu trúc cho câu hỏi tiếp nối, bản hiệu chỉnh khi đổi chi nhánh, không lưu transcript thô, thời hạn TTL/xóa/tombstone |
| Tóm tắt ngày | Giờ chốt ca + 15 phút, dữ liệu đủ/chưa đủ, thời gian chờ 30 phút, trạng thái `DATA_LATE`, mốc so sánh cùng thứ tuần trước, dự phòng 7 ngày, trường hợp không có mốc so sánh |
| Hiệu chỉnh tóm tắt | Mốc kiểm chứng khi có `OrderPaid`/hoàn tiền muộn, so sánh mốc nguồn tăng đơn điệu, khóa worker đồng thời, lũy quyền chạy lại, thay thế phiên bản bất biến, ngăn chặn công bố trùng lặp |
| Quyền riêng tư | Không có số điện thoại, tên khách hàng, ID đơn hàng/thanh toán, khóa bí mật trong payload nhà cung cấp, logs, traces, DB và sự kiện |
| Sự kiện | Tính tương thích của phong bì/schema, tóm tắt phạm vi `scopeSummary` / tham chiếu kiểm toán, tính nguyên tử của outbox, khử trùng lặp phía nhận, cách ly phiên bản lạ, phả hệ phát lại |
| Hiệu năng | Gọi công cụ p95 ≤ 2s, từ chối quyền ≤ 500ms, mục tiêu thời gian chat end-to-end, độ trễ tóm tắt ngày, giới hạn tần suất / ngân sách đồng thời |
| An ninh | Lỗ hổng BOLA/IDOR, truy cập chéo tenant, truy cập chéo chi nhánh, tấn công prompt injection, định danh tải công việc, JWT bị thu hồi / đổi vai trò |

## 13. Các sửa đổi bổ sung cho giao ước cha (Parent Contract Amendments)

### POS Platform

- Bổ sung bảng endpoint/sự kiện của AI Assistant cùng các tham chiếu quyền `ai:report:read`, `ai:summary:read`, `ai:summary:rerun`.
- Mở rộng tóm tắt schema của Reporting với các chỉ số `sales_by_hour`, `aov`, `contribution_drivers` và phong bì độ tươi mới/tính đầy đủ.
- Bổ sung các kênh định tuyến / hàng đợi cho sự kiện AI và tham chiếu chính sách che giấu dữ liệu; các sự kiện của AI là dữ kiện phân tích/kiểm toán, không phải dữ kiện doanh thu.

### Identity Access

- Bổ sung các quyền `ai:report:read`, `ai:summary:read`, `ai:summary:rerun` vào danh mục quyền hạn.
- Điều chỉnh endpoint `/internal/v1/authorization-decisions` sử dụng các trường yêu cầu chuẩn `subject`, `tenantId`, `requestedBranchIds`, `requiredPermission`, `operation`, `correlationId`; giữ trường số ít `branchId` chỉ làm alias tương thích cho trường hợp đúng một chi nhánh.
- Xác nhận phong bì phản hồi trả về đầy đủ `allowed`, `denialCode`, `resolvedScope`, `membershipVersion`, `authorizationVersion` và hành vi từ chối an toàn không để lộ thông tin chi nhánh ngoài quyền.
- Duy trì sự kiện `AuthorizationContextChanged.v1` như một tín hiệu bổ trợ; quyết định trực tuyến luôn là thẩm quyền tối cao.

### Reporting

- Định nghĩa đầy đủ API công cụ chuẩn / JSON Schema, cơ chế bắt tay mã băm registry digest và từ điển số liệu cho doanh số ngày / chi nhánh / giờ, món bán chạy, AOV và phân tích đóng góp.
- Định nghĩa quy tắc xử lý sự kiện `OrderPaid.v1` / sự kiện điều chỉnh / hoàn tiền đã duyệt, múi giờ kinh doanh, mốc kiểm chứng tính đầy đủ, trạng thái độ tươi mới, việc xây dựng lại bản chiếu và cơ chế kích hoạt hiệu chỉnh khi có dữ liệu muộn.
- Đảm bảo tầng repository của Reporting luôn áp dụng điều kiện lọc tenant/chi nhánh và tuyệt đối không bao giờ tiếp nhận câu lệnh SQL tùy ý từ AI Assistant.

### Order / Payment / Shift

- Duy trì `OrderPaid.v1` là nguồn doanh thu duy nhất; loại bỏ các khoản thanh toán đối soát muộn của Payment ra khỏi bản chiếu doanh thu.
- Cung cấp ngày kinh doanh / múi giờ của chi nhánh và chính sách đóng ca / chốt ngày cần thiết cho việc kiểm tra tính sẵn sàng tóm tắt của Reporting.
- Định nghĩa các sự kiện điều chỉnh / hoàn tiền khi tính năng này được kích hoạt; các phiên bản tóm tắt lịch sử phải tuân theo chính sách của Reporting.

### Audit / Observability

- Bổ sung định nghĩa AsyncAPI / JSON Schema cho các thành phần tiêu thụ sự kiện `AIReportTurnCompleted.v1`, `AIAccessRefused.v1`, `AIDailySummaryPublished.v1`, `AIDailySummarySuperseded.v1`, bao gồm các trường `scopeSummary`, `aggregateVersion` và `auditReferenceId`.
- Chỉ lưu trữ các bằng chứng kiểm toán đã được che giấu dữ liệu nhạy cảm; dịch vụ AI tuyệt đối không bao giờ phát hành prompt thô hay payload thô từ nhà cung cấp ra ngoài.

## 14. Các Artifact triển khai bắt buộc trước khi viết mã nguồn

- `openapi.yaml` cho các API hội thoại / lượt hỏi / tóm tắt công khai.
- `openapi-internal.yaml` cho công cụ Reporting, kiểm tra độ tươi mới, tác vụ tóm tắt và kiểm tra sức khỏe nhà cung cấp.
- `tool-registry.json` và các file JSON Schema cho toàn bộ công cụ được phép cùng `DataQualityEnvelope`.
- `asyncapi.yaml` và các file JSON Schema cho các sự kiện AI tiếp nhận và phát ra.
- `provider-policy.schema.json` cùng bộ dữ liệu mẫu cho việc che giấu dữ liệu và đóng gói prompt.
- Các file migration PostgreSQL / Flyway hoàn chỉnh kèm theo `COMMENT ON COLUMN`, các ràng buộc kiểm tra (checks), chỉ mục, khóa thuê worker và ràng buộc phiên bản hiệu chỉnh.
- Các bộ dữ liệu kiểm thử số liệu chuẩn (Golden Fixtures) cho Reporting về AOV, múi giờ, mốc so sánh, phân tích đóng góp và dữ kiện bổ sung muộn.
- Tập mẫu dữ liệu kiểm thử an ninh và đánh giá chất lượng cho việc tấn công prompt injection, leo thang đặc quyền, rò rỉ PII và hiện tượng mô hình sinh số liệu mâu thuẫn.

## 15. Cổng phê duyệt giao ước (Contract Approval Gate)

- **Trạng thái hiện tại:** Approved — warning remediation applied.
- **Đã giải quyết trong bản sửa đổi này:** Ánh xạ trường dữ liệu với Identity, registry công cụ Reporting / mã băm digest và fixture số liệu chuẩn, schema đầu ra an toàn, đặt trước ngân sách nhà cung cấp, payload polling bất đồng bộ, yêu cầu tối thiểu cho schema sự kiện, các quy tắc SQL bảo vệ phả hệ tóm tắt, ma trận thời hạn lưu trữ và các bài kiểm thử đồng thời.
- **Yêu cầu trước khi hoàn thiện:** Người dùng xác nhận các API công khai/nội bộ, schema công cụ Reporting, các sửa đổi cho Identity, chính sách nhà cung cấp, payload sự kiện, chính sách che giấu/lưu trữ và các ràng buộc cơ sở dữ liệu.
- **Quy tắc tiên quyết khi triển khai:** Tuyệt đối không viết trực tiếp Controller / DTO / Worker / Migration từ văn xuôi; bắt buộc phải sinh các giao ước máy có thể đọc được (machine-readable contracts) và các bộ fixture kiểm thử trước tiên.
