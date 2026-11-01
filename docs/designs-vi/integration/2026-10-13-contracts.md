Started at: 2026/10/13 00:00:00
Finished at: 2026/10/13 00:00:00
Total time: 0 minutes
---

# Module 12 — Tích hợp mở rộng: Giao ước API & Dữ liệu (API & Data Contracts)

**Ngày:** 2026-10-13  
**Trạng thái:** Approved with warnings — người dùng đã phê duyệt sau đợt đánh giá Contracts review  
**Tầm nhìn:** [Integration & Demo Orchestration Vision](./2026-10-13-vision.md)  
**Yêu cầu nghiệp vụ:** [Integration & Demo Orchestration Requirements](./2026-10-13-requirements.md)  
**Kiến trúc hệ thống:** [Integration & Demo Orchestration Architecture](./2026-10-13-architecture.md)  
**Biểu đồ tuần tự:** [Integration & Demo Orchestration Sequence Diagrams](./2026-10-13-sequence-diagrams.adoc)

Tài liệu này là nguồn chuẩn mực để sinh tài liệu OpenAPI 3.1, AsyncAPI/JSON Schema, các file migration PostgreSQL/Flyway, cổng adapter nhà cung cấp, schema kịch bản demo, các fixture tham chiếu kiểm toán và các kiểu dữ liệu client sinh tự động. Dịch vụ Integration sở hữu trạng thái hóa đơn/chuyển phát/điều khiển demo; tuyệt đối không sở hữu trạng thái nghiệp vụ cốt lõi của Order, Payment, Loyalty, Inventory, Recommendation, Reporting hay AI.

## 1. Quy ước giao ước (Contract Conventions)

### 1.1 Danh tính, thời gian và tiền tệ (Identity, Time & Money)

- API REST công khai đặt dưới đường dẫn `/api/v1`; API REST nội bộ/tải công việc đặt dưới `/internal/v1`; ranh giới tiếp nhận webhook callback từ nhà cung cấp đặt dưới `/callbacks/v1`.
- Chuyển phát qua EventBridge/SQS đảm bảo tính chất ít nhất một lần (at-least-once); tên sự kiện tuân theo định dạng `EventName.v1` trong sổ đăng ký của Integration. Cơ chế ánh xạ tương thích chấp nhận sự kiện cha hiện tại `OrderPaid` + `eventVersion=1` cho tới khi phần sửa đổi bổ sung của dịch vụ cha công bố `OrderPaid.v1`.
- Các định danh (IDs) là chuỗi UUID v7; dấu thời gian tính theo chuẩn ISO-8601 UTC.
- Khung thời gian kinh doanh sử dụng bản chụp múi giờ IANA của chi nhánh; múi giờ trình duyệt không có thẩm quyền quyết định.
- Tiền tệ MVP là số nguyên `int64` ở đơn vị nhỏ nhất (minor units) không âm kèm mã tiền tệ `currency=VND`; tổng tiền hóa đơn bắt buộc phải đối soát khớp hoàn toàn với bản chụp của Order.
- Phản hồi công khai sử dụng cấu trúc phong bì `{data, meta}`; payload thô của nhà cung cấp tuyệt đối không bao giờ xuất hiện trong phản hồi công khai.
- Các trường bổ sung mới phải đảm bảo tính tương thích ngược; việc thay đổi ngữ nghĩa hoặc xóa trường bắt buộc phải nâng phiên bản đường dẫn API chính (major path) hoặc phiên bản sự kiện.

### 1.2 Tiêu đề HTTP (Headers)

| Tiêu đề | Công khai | Nội bộ | Callback | Ý nghĩa nghiệp vụ |
|---|---:|---:|---:|---|
| `Authorization` | Bắt buộc | Bắt buộc | Không | Cognito JWT hoặc chứng thực tải công việc / mTLS |
| `X-Correlation-Id` | Khuyến nghị | Bắt buộc | Khuyến nghị | Truy vết xuyên suốt request / sự kiện / worker |
| `X-Causation-Id` | Không | Khuyến nghị | Khuyến nghị | Tham chiếu lệnh hoặc sự kiện thượng nguồn |
| `Idempotency-Key` | Thao tác thay đổi | Thao tác/Vận hành | Khóa provider | Khóa thử lại ổn định; phân phạm vi theo tenant + người gọi |
| `If-Match` | Tùy chọn mutation | Bắt buộc cho CAS demo | Không | Phiên bản tài nguyên kỳ vọng |
| `X-Demo-Run-Id` | Không | Chỉ demo | Không | Ngữ cảnh phiên chạy; chỉ chấp nhận trên non-prod |
| `X-Fault-Lease-Id` | Không | Chỉ demo | Không | Quyền tiêm lỗi được phân bổ trong phiên chạy |
| `X-Provider-Signature` | Không | Không | Bắt buộc khi provider hỗ trợ | Xác thực chữ ký số callback tại adapter |
| `Cache-Control` | Máy chủ | Máy chủ | Không | Cấm cache thông tin liên hệ/provider; chỉ cache trạng thái an toàn |

Client không thể tự gửi tenant, tác nhân, vai trò, người vận hành, môi trường, thẩm quyền sự kiện nguồn hay quyền sở hữu chi nhánh làm căn cứ chân lý. Mọi ID trong phần thân hoặc đường dẫn đều được thẩm định đối chiếu với phạm vi phân quyền đáng tin cậy.

### 1.3 Phong bì phản hồi chung (Common Response Envelope)

```json
{
  "data": {},
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J...",
    "resourceVersion": 3,
    "dataQuality": {
      "status": "FRESH",
      "freshnessLagSeconds": 2,
      "completenessStatus": "COMPLETE",
      "watermark": "order-paid:v901",
      "watermarkVersion": 901,
      "lastUpdatedAt": "2026-10-13T08:00:02Z"
    }
  }
}
```

Các trạng thái chất lượng dữ liệu: `FRESH`, `STALE`, `PARTIAL`, `UNAVAILABLE`, `NO_DATA`, `DEGRADED_TRACE`, `NO_RECIPIENT`.

### 1.4 Phong bì báo lỗi (Error Envelope)

```json
{
  "error": {
    "code": "invoice_state_conflict",
    "message": "The requested operation is not valid for the current state",
    "details": [],
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

Thông báo lỗi không bao giờ để lộ sự tồn tại của tài nguyên ngoài phạm vi phân quyền hoặc làm rò rỉ thông tin liên hệ, payload nhà cung cấp, secret, token, câu lệnh SQL hay stack trace.

### 1.5 Phân trang và bộ lọc (Pagination & Filtering)

- Danh sách hóa đơn và lịch sử chuyển phát sử dụng phân trang theo con trỏ (cursor pagination), mặc định 20, tối đa 100 bản ghi.
- Sắp xếp ổn định: `createdAt DESC, id DESC`.
- Con trỏ phân trang ràng buộc các bộ lọc đã chuẩn hóa, mã băm phạm vi đã giải quyết, phiên bản phân quyền và mốc kiểm chứng nguồn.
- Khoảng thời gian truy vấn tương tác tối đa là 90 ngày; các dữ liệu lịch sử cũ hơn phải thông qua quy trình xuất dữ liệu bất đồng bộ do dịch vụ Audit hoặc quy trình Vận hành phê duyệt sở hữu.
- Trả về mã lỗi `404 not_found` cho các tài nguyên không tồn tại hoặc nằm ngoài phạm vi; không để lộ sự tồn tại của tài nguyên ngoài quyền.

## 2. Giao ước phân quyền và phạm vi (Authorization & Scope Contract)

### 2.1 Sửa đổi bổ sung danh mục quyền hạn (Permission Amendments)

| Quyền hạn | Owner | Manager | Cashier | Tải công việc Demo | Phạm vi áp dụng |
|---|---:|---:|---:|---:|---|
| `integration:invoice:read` | Có | Chi nhánh được giao | Chi nhánh / đơn hàng hiện tại | Không | Tenant / Chi nhánh |
| `integration:invoice:prepare` | Có | Chi nhánh được giao | Đơn hàng / checkout hiện tại | Không | Chi nhánh + Đơn hàng |
| `integration:delivery:retry` | Có | Chi nhánh được giao | Chỉ đơn hàng hiện tại / có giới hạn | Không | Chi nhánh + Hóa đơn |
| `integration:operate` | Vận hành giới hạn | Không | Không | Không | Provider / Phát lại / Lưu trữ |
| `demo:run` | Không | Không | Không | Cho phép trên non-prod | Namespace kịch bản demo |
| `demo:seed-reset` | Không | Không | Không | Cho phép trên non-prod | Namespace phiên chạy |
| `demo:fault-inject` | Không | Không | Không | Cho phép trên non-prod | Phiên chạy / Khóa thuê |
| `demo:trace:read` | Không | Không | Không | Cho phép trên non-prod | Tham chiếu trace thuộc phiên chạy |

Identity luôn là thẩm quyền phân quyền trực tuyến. Integration gọi giao ước quyết định phân quyền của dịch vụ cha khi cần, sau đó áp dụng điều kiện lọc tại repository với `tenant_id` và `branch_id` đã được giải quyết.

### 2.2 Phạm vi phân quyền đã giải quyết (Resolved Scope)

```json
{
  "scopeType": "BRANCH",
  "tenantId": "0192...001",
  "branchIds": ["0192...002"],
  "role": "MANAGER",
  "permissions": ["integration:invoice:read", "integration:delivery:retry"],
  "membershipVersion": 12,
  "authorizationVersion": 7,
  "scopeHash": "sha256:opaque",
  "resolvedAt": "2026-10-13T08:00:00Z",
  "expiresAt": "2026-10-13T08:05:00Z"
}
```

Phạm vi toàn tenant của Owner phải được khai báo tường minh; cấm sử dụng ký tự đại diện cho chi nhánh. Mọi thao tác xem chi tiết, danh sách, gửi lại, xuất dữ liệu, trace demo và callback đều được kiểm tra phạm vi chặt chẽ.

## 3. Các schema dữ liệu dùng chung (Common Schemas)

### 3.1 `MoneyAmount` (Số tiền)

```json
{
  "amountMinor": 50000,
  "currency": "VND"
}
```

Ràng buộc: `amountMinor >= 0`, `currency=VND` trong MVP, chỉ sử dụng số học số nguyên, và các khoản tiền hàng/chiết khấu/VAT/tổng tiền trên hóa đơn phải đối soát khớp tuyệt đối với bản chụp từ OrderPaid tin cậy.

### 3.2 `InvoiceRecipientIntentRequest` (Ý định người nhận hóa đơn)

```json
{
  "orderId": "0192...",
  "checkoutAttemptId": "0192...",
  "branchId": "0192...",
  "channel": "EMAIL",
  "contact": {"email": "customer@example.test"},
  "consent": {
    "policyVersion": "invoice-delivery-consent-v1",
    "textVersion": "2026-10-13",
    "capturedAt": "2026-10-13T08:00:00Z",
    "purpose": "INVOICE_DELIVERY"
  }
}
```

Đối với kênh `ZALO`, trường `contact` là một mã `zaloUserId` hoặc số điện thoại định dạng E.164 đã qua kiểm thực hợp lệ tùy theo năng lực của provider; API không bao giờ trả trường này ra ngoài. Thông tin liên hệ thô chỉ được tiếp nhận qua kênh bảo mật TLS, được mã hóa/token hóa trước khi lưu trữ và phải được che giấu trước khi ghi ra log/trace.

### 3.3 `InvoiceRecipientSnapshot` (Bản chụp người nhận hóa đơn)

```json
{
  "invoiceRecipientRef": "0192...",
  "channel": "EMAIL",
  "maskedContact": "c***@example.test",
  "consentPolicyVersion": "invoice-delivery-consent-v1",
  "consentTextVersion": "2026-10-13",
  "consentedAt": "2026-10-13T08:00:00Z",
  "version": 1,
  "status": "ACTIVE"
}
```

### 3.4 `InvoiceOperationSummary` (Tóm tắt tác vụ hóa đơn)

```json
{
  "invoiceOperationId": "0192...",
  "tenantId": "0192...",
  "branchId": "0192...",
  "orderId": "0192...",
  "checkoutAttemptId": "0192...",
  "sourceEventId": "0192...",
  "profile": "SANDBOX_TECHNICAL",
  "status": "ISSUED",
  "technicalReference": "sandbox-inv-123",
  "total": {"amountMinor": 105000, "currency": "VND"},
  "recipient": {"channel": "EMAIL", "maskedContact": "c***@example.test"},
  "deliveryStatus": "SENT",
  "version": 3,
  "createdAt": "2026-10-13T08:00:01Z",
  "updatedAt": "2026-10-13T08:00:05Z"
}
```

Trạng thái `ISSUED` chỉ thể hiện việc chấp thuận kỹ thuật trên môi trường sandbox/provider. Tuyệt đối không mang ý nghĩa là hóa đơn thuế có giá trị pháp lý.

### 3.5 `DemoCheckpoint` (Điểm kiểm tra Demo)

```json
{
  "checkpointId": "0192...",
  "demoRunId": "0192...",
  "stepKey": "PAYMENT_IPN_VERIFIED",
  "sequence": 5,
  "expectedState": {"payment": "PAID"},
  "observedState": {"payment": "PAID", "order": "PAID"},
  "status": "PASSED",
  "quality": "FRESH",
  "traceRefs": ["trace_opaque_1"],
  "correlationId": "cor_01J...",
  "recordedAt": "2026-10-13T08:01:00Z"
}
```

Mã tham chiếu trace là các chuỗi mờ, có phạm vi và có thể ở trạng thái `DEGRADED_TRACE` khi AWS X-Ray không khả dụng; điểm kiểm tra không được báo ĐẠT giả tạo khi thiếu dấu vết bắt buộc.

## 4. Giao ước API công khai (Public API Contracts)

### 4.1 Danh mục Endpoint (Endpoint Catalogue)

| Phương thức | Đường dẫn | Quyền hạn | Phản hồi | Mục đích nghiệp vụ |
|---|---|---|---|---|
| POST | `/api/v1/invoice-recipient-intents` | `integration:invoice:prepare` | `201 RecipientResponse` | Thu thập liên hệ/sự đồng ý, trả ref mờ |
| GET | `/api/v1/invoices` | `integration:invoice:read` | `InvoicePage` | Danh sách hóa đơn có phân quyền theo đơn/chi nhánh |
| GET | `/api/v1/invoices/{invoiceOperationId}` | `integration:invoice:read` | `InvoiceResponse` | Xem chi tiết an toàn của hóa đơn/chuyển phát |
| GET | `/api/v1/invoices/{invoiceOperationId}/deliveries` | `integration:invoice:read` | `DeliveryPage` | Xem lịch sử các kênh/lần thử chuyển phát |
| POST | `/api/v1/invoices/{invoiceOperationId}/delivery-retries` | `integration:delivery:retry` | `202 DeliveryRetryResponse` | Yêu cầu gửi lại có phân quyền |

### 4.2 Yêu cầu thu thập kênh nhận hóa đơn

`POST /api/v1/invoice-recipient-intents` bắt buộc có tiêu đề `Idempotency-Key`.

```json
{
  "data": {
    "orderId": "0192...",
    "checkoutAttemptId": "0192...",
    "channel": "EMAIL",
    "contact": {"email": "customer@example.test"},
    "consent": {
      "policyVersion": "invoice-delivery-consent-v1",
      "textVersion": "2026-10-13",
      "capturedAt": "2026-10-13T08:00:00Z",
      "purpose": "INVOICE_DELIVERY"
    }
  }
}
```

Phản hồi mã `201`:

```json
{
  "data": {
    "invoiceRecipientRef": "0192...",
    "channel": "EMAIL",
    "maskedContact": "c***@example.test",
    "version": 1,
    "status": "ACTIVE",
    "expiresAt": "2026-10-13T10:00:00Z"
  },
  "meta": {"requestId": "req_01J...", "correlationId": "cor_01J..."}
}
```

Cùng khóa lũy quyền và yêu cầu chuẩn sẽ phát lại đúng phản hồi ban đầu. Cùng khóa nhưng sai khác thông tin liên hệ/kênh sẽ trả về mã lỗi `409 idempotency_conflict`.

### 4.3 Danh sách và chi tiết hóa đơn

Đường dẫn: `GET /api/v1/invoices?from=&to=&branchIds=&orderId=&status=&deliveryStatus=&cursor=&limit=`.

Quy tắc: Máy chủ tự giải quyết phạm vi chi nhánh; khoảng thời gian tối đa 90 ngày; `branchIds` tối đa 20 chi nhánh và phải là tập con của quyền được cấp; không trả thông tin liên hệ thô hay phản hồi thô của provider; trường `technicalReference` của hóa đơn chỉ trả về mã tham chiếu nhà cung cấp an toàn.

### 4.4 Yêu cầu gửi lại chuyển phát (Delivery Retry)

`POST /api/v1/invoices/{invoiceOperationId}/delivery-retries` bắt buộc có `Idempotency-Key` và tùy chọn `If-Match`.

```json
{
  "data": {
    "channel": "EMAIL",
    "reason": "Khách yêu cầu gửi lại",
    "expectedVersion": 3
  }
}
```

Quy tắc nghiệp vụ:

- Máy chủ kiểm tra lại phạm vi hiện tại, sự đồng ý, trạng thái liên hệ, trạng thái hóa đơn và năng lực kênh gửi.
- Thao tác gửi lại tuyệt đối không chấp nhận thông tin liên hệ thô mới; việc đổi thông tin liên hệ đòi hỏi phải tạo bản chụp sự đồng ý mới.
- Phản hồi `202` trả về `deliveryOperationId`, `attemptId`, trạng thái hiện tại và địa chỉ `pollUrl`.
- Phiên bản bị cũ trả về mã `409 invoice_state_conflict`; cùng yêu cầu lũy quyền trả về đúng tác vụ gốc.

## 5. Giao ước API nội bộ phục vụ Demo (Internal/Demo API Contracts)

### 5.1 Danh mục Endpoint Demo

| Phương thức | Đường dẫn | Quyền hạn | Phản hồi |
|---|---|---|---|
| POST | `/internal/v1/demo/runs` | `demo:run` | `201 DemoRunResponse` |
| GET | `/internal/v1/demo/runs/{demoRunId}` | `demo:run` | `DemoRunResponse` |
| POST | `/internal/v1/demo/runs/{demoRunId}/execute` | `demo:run` | `202 DemoRunResponse` |
| POST | `/internal/v1/demo/runs/{demoRunId}/reset` | `demo:seed-reset` | `202 ResetOperationResponse` |
| POST | `/internal/v1/demo/runs/{demoRunId}/fault-leases` | `demo:fault-inject` | `201 FaultLeaseResponse` |
| DELETE | `/internal/v1/demo/runs/{demoRunId}/fault-leases/{faultLeaseId}` | `demo:fault-inject` | `204 No Content` |
| GET | `/internal/v1/demo/runs/{demoRunId}/traces` | `demo:trace:read` | `TraceReferencePage` |
| GET | `/internal/v1/integration/health` | workload/ops | `IntegrationHealthResponse` |

### 5.2 Khởi tạo phiên chạy Demo

```json
{
  "data": {
    "scenarioKey": "POS_P0_SUCCESS_AND_PAYMENT_OUTAGE",
    "scenarioVersion": 1,
    "seedVersion": "pos-demo-seed-v1",
    "tenantNamespace": "demo-tenant-01",
    "branchNamespace": "demo-branch-01",
    "requestedSteps": ["SUCCESS", "PAYMENT_UNAVAILABLE"]
  }
}
```

Máy chủ bỏ qua thông tin môi trường do client tự gửi; môi trường triển khai bắt buộc phải là `DEMO` hoặc `TEST`. Việc tạo phiên chạy sẽ xác lập quyền sở hữu namespace/khóa thuê và chạy kiểm tra sơ bộ preflight trước khi chuyển sang trạng thái `READY`.

### 5.3 Khóa thuê tiêm lỗi (Fault Lease)

```json
{
  "data": {
    "faultType": "PAYMENT_UNAVAILABLE",
    "target": "payment-initiate",
    "stepKey": "PAYMENT_FAILURE",
    "reason": "Verify Saga releases Inventory reservation",
    "ttlSeconds": 300
  }
}
```

Máy chủ chặn trần thời gian TTL, thẩm định danh mục kịch bản cho phép, gắn khóa thuê với phiên chạy/môi trường/người sở hữu và trả về `faultLeaseId`, thời điểm hết hạn và trạng thái. Khóa thuê khi hết hạn hoặc bị thu hồi sẽ được ghi nhận kiểm toán và tự động trả về hành vi bình thường.

### 5.4 Kiểm tra sức khỏe và đối soát nhà cung cấp

Đường dẫn: `GET /internal/v1/integration/health` trả về trạng thái chi tiết theo nguồn/hàng đợi/nhà cung cấp:

```json
{
  "data": {
    "environment": "DEMO",
    "sources": [{
      "name": "order-paid-consumer",
      "status": "HEALTHY",
      "lagSeconds": 2,
      "dlqCount": 0,
      "watermark": "order-paid:v901"
    }],
    "providers": [{
      "name": "invoice-sandbox",
      "profile": "SANDBOX_TECHNICAL",
      "status": "HEALTHY",
      "latencyMsP95": 240
    }]
  }
}
```

## 6. Giao ước cổng Adapter nhà cung cấp (Provider Adapter Ports)

### 6.1 Cổng phát hành hóa đơn (Invoice Port)

```text
issue(IssueInvoiceCommand, ProviderIdempotencyKey)
  → Accepted(profile, technicalReference, artifactRef, occurredAt)
  → RetryableFailure(normalizedCode, retryAfter)
  → TerminalFailure(normalizedCode)
  → Unknown(operationQueryRef)
```

Lệnh `IssueInvoiceCommand` bao gồm hồ sơ an toàn theo tenant/chi nhánh, tham chiếu đơn hàng/lượt checkout/sự kiện nguồn, bản chụp bất biến của dòng món/tổng tiền/VAT/chiết khấu, năng lực chuyển phát và mã tương quan. Tuyệt đối không chứa token Cognito hoặc dữ liệu PII không liên quan của khách hàng.

### 6.2 Cổng chuyển phát (Delivery Port)

```text
send(SendDeliveryCommand, ChannelIdempotencyKey)
  → Accepted(channel, providerMessageRefSafe, acceptedAt)
  → RetryableFailure(normalizedCode, retryAfter)
  → TerminalFailure(normalizedCode)
  → Unknown(callbackOrQueryRef)
```

Adapter chịu trách nhiệm xác thực chữ ký/thông tin xác thực provider, quản lý timeout, ánh xạ header Retry-After, thẩm định webhook callback, lũy quyền provider và che giấu payload thô. Các bộ điều phối domain chỉ tiếp nhận kết quả đã chuẩn hóa.

### 6.3 Giao ước Webhook Callback từ nhà cung cấp

Các webhook callback từ nhà cung cấp chỉ được phép đi vào qua ranh giới adapter:

- `POST /callbacks/v1/invoice-providers/{providerKey}/invoice-status`
- `POST /callbacks/v1/delivery-providers/{providerKey}/delivery-status`

Yêu cầu đối với callback: Thẩm định chữ ký số/chứng thực của provider, kiểm tra khung thời gian chống tấn công phát lại (replay attack), khử trùng lặp sự kiện provider, ánh xạ số tiền/đơn hàng/mã tham chiếu, trả phản hồi an toàn, không phụ thuộc token Cognito và tuyệt đối không trực tiếp làm thay đổi trạng thái của các dịch vụ cha. Các callback không được hỗ trợ sẽ bị cách ly an toàn.

## 7. Giao ước sự kiện (Event Contracts)

### 7.1 Phong bì sự kiện chung (Common Envelope)

```json
{
  "eventId": "0192...",
  "eventType": "InvoiceIssued.v1",
  "eventVersion": 1,
  "occurredAt": "2026-10-13T08:00:02Z",
  "tenantId": "0192...",
  "branchId": "0192...",
  "aggregateType": "INVOICE_OPERATION",
  "aggregateId": "0192...",
  "aggregateVersion": 3,
  "sourceEventId": "0192...",
  "sourceVersion": 18,
  "correlationId": "cor_01J...",
  "causationId": "evt_01J...",
  "producer": "integration-service",
  "schemaVersion": "integration-events-v1",
  "data": {}
}
```

Các bất biến: Định danh `eventId` ổn định qua các lần phát lại, bộ `(producer, sourceEventId)` là duy nhất, bắt buộc có `tenantId`, bắt buộc có `branchId` đối với các sự kiện thuộc chi nhánh, bảo toàn dấu thời gian gốc ban đầu, phiên bản lạ hoặc lỗi che giấu dữ liệu sẽ bị đưa vào khu vực cách ly / DLQ.

### 7.2 Các sự kiện nguồn tiếp nhận (Consumed Source Events)

| Sự kiện | Nguồn chuẩn mực | Hành vi xử lý của Integration |
|---|---|---|
| `OrderPaid.v1` | Dịch vụ Order | Khởi tạo / hội tụ tác vụ hóa đơn; chấp nhận `OrderPaid` + version 1 trong giai đoạn chuyển đổi |
| `OrderCheckoutFailed.v1` | Dịch vụ Order | Hủy bỏ ý định nhận hóa đơn chưa dùng; tuyệt đối không hủy hóa đơn đã phát hành thành công |
| `SuggestionShown.v1` | Dịch vụ Recommendation | Chỉ dùng làm điểm kiểm tra quy kết demo; không làm thay đổi trạng thái hóa đơn |
| `SuggestionAccepted.v1` | Recommendation / Bằng chứng Order | Điểm kiểm tra quy kết demo; không phải chân lý doanh thu |
| `PaymentReconciliationRequired.v1` | Dịch vụ Payment | Ngữ cảnh chất lượng dữ liệu demo/vận hành; tuyệt đối không tạo hóa đơn |

Phân hệ Demo Control Plane cũng có thể đọc các API/sự kiện kiểm tra điểm checkpoint trên non-production từ Shift, Order, Payment, Inventory, Loyalty, Reporting và AI. Các API này hoàn toàn thuộc sở hữu của các dịch vụ cha tương ứng.

### 7.3 Các trường bổ sung bắt buộc trên sự kiện `OrderPaid.v1`

Dịch vụ Order cha cần bổ sung/chuẩn hóa các trường sau:

```json
{
  "orderId": "0192...",
  "checkoutAttemptId": "0192...",
  "branchId": "0192...",
  "aggregateVersion": 18,
  "sourceVersion": 18,
  "invoiceRecipientRef": "0192...",
  "invoiceProfile": "SANDBOX_TECHNICAL",
  "lines": [],
  "subtotalMinor": 100000,
  "discountMinor": 0,
  "vatMinor": 0,
  "grandTotalMinor": 100000,
  "currency": "VND"
}
```

Trường `invoiceRecipientRef` là tùy chọn và có giá trị mờ. Thông tin email, số Zalo hay số điện thoại thô tuyệt đối không bao giờ xuất hiện trong sự kiện `OrderPaid.v1`.

### 7.4 Các sự kiện phát ra (Produced Events)

| Sự kiện | Payload tối thiểu | Ngữ nghĩa nghiệp vụ |
|---|---|---|
| `InvoiceOperationRequested.v1` | Tác vụ hóa đơn, tham chiếu đơn/lượt checkout/nguồn, hồ sơ, số tiền, correlation ID | Đã tạo tác vụ kỹ thuật |
| `InvoiceIssued.v1` | Tác vụ/phiên bản, hồ sơ, mã tham chiếu kỹ thuật, tham chiếu artifact an toàn, trạng thái | Nhà cung cấp sandbox chấp thuận; không có giá trị thuế pháp lý |
| `InvoiceFailed.v1` | Tác vụ/phiên bản, khả năng thử lại, mã lỗi chuẩn hóa, lịch thử lại / trạng thái kết thúc | Dữ kiện lỗi an toàn |
| `InvoiceDeliveryRequested.v1` | Hóa đơn, kênh gửi, phiên bản bản chụp người nhận, tham chiếu lần thử / lũy quyền | Đã lập lịch gửi tin qua kênh |
| `InvoiceDeliveryStatusChanged.v1` | Tác vụ chuyển phát/lần thử, kênh gửi, trạng thái, mã tham chiếu provider an toàn, chất lượng | Nhà cung cấp chấp thuận / lỗi / hết hạn |
| `DemoRunStarted.v1` | Tham chiếu an toàn phiên chạy / kịch bản / hạt giống / môi trường | Chỉ phát trên Non-prod |
| `DemoCheckpointRecorded.v1` | Phiên chạy/bước, trạng thái kỳ vọng/thực tế an toàn, kết quả, chất lượng, trace refs | Bằng chứng điểm kiểm tra |
| `DemoRunCompleted.v1` | Phiên chạy, kết quả tổng kết, phân loại lỗi, tóm tắt các điểm kiểm tra | Chỉ phát trên Non-prod |
| `IntegrationDataQualityChanged.v1` | Nguồn/nhà cung cấp/bản chiếu, trạng thái cũ/mới, độ trễ, mốc watermark | Độ tươi mới trên màn hình vận hành |
| `IntegrationOperationAuditReferenced.v1` | Loại thao tác/tài nguyên/phạm vi/tác nhân/tải công việc/lý do, phiên bản che giấu | Tham chiếu kiểm toán; không chứa thân thô |

Các sự kiện phát ra tuyệt đối không chứa yêu cầu/phản hồi thô của nhà cung cấp, chữ ký số, khóa bí mật, mã OTP hay thông tin liên hệ chưa che giấu.

## 8. Giao ước cơ sở dữ liệu PostgreSQL (PostgreSQL Data Contract)

Cơ sở dữ liệu thuộc sở hữu độc quyền của Integration Runtime. Cơ sở dữ liệu Demo Control DB là một cơ sở dữ liệu tách biệt riêng chỉ nằm trên môi trường Non-production. Mọi cột trong các bảng bắt buộc phải có câu lệnh `COMMENT ON COLUMN` mô tả mục đích, phân loại PII và phân loại thời hạn lưu trữ.

### 8.1 `invoice_recipient_snapshots` (Bản chụp thông tin người nhận hóa đơn)

| Tên cột | Kiểu dữ liệu | Bắt buộc | Mô tả / Ghi chú |
|---|---|---:|---|
| `id` | UUID | Có | Định danh bản chụp |
| `tenant_id` | UUID | Có | Tenant sở hữu |
| `branch_id` | UUID | Có | Chi nhánh ghi nhận |
| `order_id` | UUID | Có | Đơn hàng gắn kết |
| `checkout_attempt_id` | UUID | Có | Lượt checkout gắn kết |
| `channel` | TEXT | Có | `EMAIL` hoặc `ZALO` |
| `encrypted_contact` | BYTEA/TEXT ref | Có | Thông tin liên hệ đã mã hóa/token hóa; thuộc nhóm dữ liệu PII giới hạn |
| `masked_contact` | TEXT | Có | Chuỗi hiển thị an toàn đã che giấu |
| `consent_policy_version` | TEXT | Có | Phiên bản chính sách đồng ý |
| `consent_text_version` | TEXT | Có | Phiên bản nội dung văn bản khách đã xem |
| `consented_at` | TIMESTAMPTZ | Có | Thời điểm khách hàng xác nhận đồng ý |
| `status` | TEXT | Có | Trạng thái: `ACTIVE`, `REVOKED`, `EXPIRED`, `USED` |
| `version` | INT | Có | Phiên bản bản chụp bất biến |
| `expires_at` | TIMESTAMPTZ | Có | Thời hạn hết hạn của thông tin liên hệ / ý định |
| `created_at` | TIMESTAMPTZ | Có | Thời điểm tạo tính theo chuẩn UTC |

### 8.2 `invoice_operations` (Tác vụ hóa đơn)

| Tên cột | Kiểu dữ liệu | Bắt buộc | Mô tả / Ghi chú |
|---|---|---:|---|
| `id` | UUID | Có | Định danh tác vụ hóa đơn |
| `tenant_id`/`branch_id` | UUID | Có | Phạm vi phân quyền |
| `order_id`/`checkout_attempt_id` | UUID | Có | Tham chiếu nghiệp vụ nguồn |
| `source_event_id` | UUID | Có | ID sự kiện OrderPaid chuẩn mực |
| `source_version` | BIGINT | Có | Phiên bản nguồn/aggregate của Order |
| `profile` | TEXT | Có | Hồ sơ năng lực: `SANDBOX_TECHNICAL` |
| `status` | TEXT | Có | Trạng thái: `REQUESTED`, `ISSUING`, `ISSUED`, `FAILED_RETRYABLE`, `FAILED_TERMINAL` |
| `currency` | TEXT | Có | Loại tiền tệ: `VND` trong MVP |
| `subtotal_minor`/`discount_minor`/`vat_minor`/`total_minor` | BIGINT | Có | Bản chụp số tiền tin cậy từ sự kiện OrderPaid |
| `technical_reference` | TEXT | Không | Mã tham chiếu an toàn từ nhà cung cấp |
| `artifact_ref` | TEXT | Không | Tham chiếu artifact đã mã hóa / giảm thiểu dữ liệu |
| `version` | INT | Có | Phiên bản kiểm soát khóa lạc quan CAS |
| `next_attempt_at` | TIMESTAMPTZ | Không | Lịch thử lại tiếp theo |
| `quality_code` | TEXT | Không | Mã chất lượng dữ liệu: thiếu / chậm / chuẩn hóa |
| `created_at`/`updated_at` | TIMESTAMPTZ | Có | Dấu thời gian vòng đời theo chuẩn UTC |

### 8.3 `invoice_items` (Chi tiết dòng hóa đơn)

Lưu trữ bản chụp dòng món an toàn bất biến: ID món/biến thể, tên món hiển thị đã qua xử lý, số lượng, đơn giá / chiết khấu / tiền thuế / thành tiền (tính theo minor units), loại tiền tệ, phiên bản phân bổ và ID dòng món nguồn. Tuyệt đối không lưu modifier thô hay bí mật thanh toán.

### 8.4 Các bảng quản lý nhà cung cấp và chuyển phát (Provider & Delivery Tables)

Trường dữ liệu trong `invoice_provider_attempts`: `id`, tenant, mã tác vụ, khóa/hồ sơ provider, khóa lũy quyền, số thứ tự lần thử, trạng thái, mã lỗi chuẩn hóa, mã ref provider an toàn, thời gian retry-after, thời điểm bắt đầu/hoàn tất/thử tiếp theo, mã vân tay phản hồi, correlation ID và phân loại lỗi.

Trường dữ liệu trong `delivery_operations`: `id`, tenant/chi nhánh, mã tác vụ hóa đơn, kênh gửi, mã bản chụp người nhận/phiên bản, trạng thái, số lần thử, thời hạn hết hạn, mã ref provider hiện tại, phiên bản và các mốc thời gian.

Trường dữ liệu trong `delivery_provider_attempts`: `id`, mã tác vụ chuyển phát, khóa provider, khóa lũy quyền, số thứ tự lần thử, trạng thái, mã lỗi chuẩn hóa, mã ref provider an toàn, thời gian retry-after, khóa/vân tay sự kiện callback và các mốc thời gian.

### 8.5 Các bảng hạ tầng và điều khiển (Infrastructure & Control Tables)

| Tên bảng | Các cột bắt buộc và ý nghĩa |
|---|---|
| `inbox_events` | Bên phát, ID/phiên bản sự kiện nguồn, loại sự kiện, tenant/chi nhánh, trạng thái, thời điểm nhận/xử lý, phân loại lỗi, số lần thử lại, mã vân tay payload |
| `outbox_messages` | ID/loại/phiên bản sự kiện, aggregate, tenant/chi nhánh, tham chiếu payload, trạng thái phát, lần thử tiếp theo, số lần thử lại, thông tin DLQ/lỗi và timestamps |
| `projection_watermarks` | Nguồn/bản chiếu/phạm vi, mốc watermark/phiên bản, trạng thái độ tươi mới/tính đầy đủ, thời điểm cập nhật gần nhất, tham chiếu khoảng trống/lỗi |
| `audit_references` | Tenant/chi nhánh, tác nhân/tải công việc, hành động/tài nguyên, lý do, tóm tắt an toàn trước/sau, correlation ID, mã tham chiếu Audit và thời điểm tạo |

### 8.6 Cơ sở dữ liệu Demo Control DB

`demo_runs`: phiên bản run/kịch bản/dữ liệu hạt giống, môi trường, namespace, người sở hữu, trạng thái/phiên bản, thời hạn hết hạn/khóa thuê, thời điểm tạo/kết thúc.  
`demo_resource_manifests`: phiên chạy, dịch vụ cha, loại tài nguyên/ID mờ, thứ tự/trạng thái dọn dẹp, chính sách xóa.  
`demo_checkpoints`: phiên chạy, bước/thứ tự, trạng thái an toàn kỳ vọng/thực tế, kết quả/chất lượng, liên kết trace, correlation ID và timestamps.  
`fault_leases`: phiên chạy, môi trường, loại lỗi/mục tiêu/bước, người sở hữu/lý do, trạng thái, thời điểm cấp/hết hạn/thu hồi, phiên bản công tắc ngắt khẩn cấp.  
`demo_reset_operations`: phiên chạy, khóa lũy quyền, trạng thái, số lượng tài nguyên đã xử lý, phân loại lỗi và timestamps.

## 9. Ràng buộc toàn vẹn, chỉ mục và quy tắc dữ liệu (Constraints & Indexes)

### 9.1 Các ràng buộc bắt buộc (Required Constraints)

- Mọi dòng dữ liệu runtime đều có `tenant_id`; trường `branch_id` là bắt buộc đối với các tài nguyên thuộc chi nhánh.
- `invoice_recipient_snapshots` duy nhất theo `(tenant_id, order_id, checkout_attempt_id, version)`; các phiên bản là bất biến.
- `invoice_operations` duy nhất theo `(tenant_id, order_id, checkout_attempt_id, profile)` và duy nhất theo `(tenant_id, source_event_id)`.
- `invoice_provider_attempts.idempotency_key` là duy nhất theo từng nhà cung cấp và tenant; các lần thử chỉ được ghi thêm.
- `delivery_operations` duy nhất theo `(tenant_id, invoice_operation_id, channel, recipient_snapshot_id)` cho phả hệ hiện hành.
- Chuyển đổi trạng thái chuyển phát/nhà cung cấp sử dụng enum danh mục cho phép và phiên bản kiểm soát lạc quan `version` (CAS).
- Các trường tiền tệ là số nguyên VND không âm và đối soát khớp với bản chụp từ sự kiện OrderPaid; giá trị âm hoặc tràn số sẽ từ chối sự kiện.
- Khử trùng lặp nguồn sự kiện inbox sử dụng cặp duy nhất `(producer, source_event_id)`; việc phát lại bảo toàn ID ban đầu.
- Thao tác chèn vào Outbox bắt buộc phải commit cùng giao dịch với lần chuyển trạng thái cục bộ.
- Kiểm tra môi trường cho phiên chạy demo và khóa thuê tiêm lỗi là bắt buộc; khóa thuê lỗi đang hoạt động là duy nhất cho mỗi bộ run/target/step.
- Các thao tác đặt lại/thực thi/tiêm lỗi đòi hỏi phải có khóa thuê và phiên bản hợp lệ; cập nhật phiên bản cũ sẽ trả về lỗi xung đột mà không ghi dữ liệu từng phần.
- Ứng dụng không có API cập nhật/xóa cho các bảng lần thử provider, lịch sử sự đồng ý, checkpoint hay tham chiếu kiểm toán; chỉ cho phép quy trình lưu trữ/lưu vết chuyên biệt.

### 9.2 Các chỉ mục bắt buộc (Required Indexes)

| Tên chỉ mục | Các cột đánh chỉ mục | Mục đích sử dụng |
|---|---|---|
| `idx_invoice_scope_time` | tenant, branch, created_at DESC, id DESC | Danh sách hóa đơn có phân quyền |
| `idx_invoice_order_attempt` | tenant, order_id, checkout_attempt_id | Xem chi tiết / Khử trùng lặp |
| `idx_invoice_worker` | tenant, status, next_attempt_at | Worker xử lý phát hành hóa đơn |
| `idx_delivery_worker` | tenant, status, next_attempt_at, channel | Worker xử lý chuyển phát |
| `idx_delivery_invoice` | tenant, invoice_operation_id, created_at DESC | Lịch sử chuyển phát theo hóa đơn |
| `uq_inbox_source` | producer, source_event_id | Khử trùng lặp sự kiện nguồn |
| `idx_inbox_retry` | status, next_attempt_at | Xử lý thử lại / Hàng đợi DLQ |
| `idx_outbox_publish` | status, next_attempt_at | Worker phát sự kiện ra ngoài |
| `idx_watermark_health` | source, scope, last_updated_at | Cảnh báo độ tươi mới dữ liệu |
| `idx_demo_run_state` | environment, state, expires_at | Quản lý khóa thuê / Dọn dẹp phiên chạy demo |
| `idx_demo_checkpoint` | demo_run_id, sequence | Tóm tắt điểm kiểm tra demo |
| `idx_fault_lease_expiry` | environment, status, expires_at | Tự động hết hạn / Công tắc ngắt khẩn cấp |

## 10. Thời hạn lưu trữ, quyền riêng tư và lưu giữ pháp lý (Retention Matrix)

Chính sách mặc định cho MVP (sẽ được rà soát pháp lý/nhà cung cấp trước khi triển khai hóa đơn thuế chính thức):

| Phân loại dữ liệu | Thời hạn lưu trữ mặc định | Quy tắc xử lý chi tiết |
|---|---:|---|
| Metadata an toàn của tác vụ/dòng hóa đơn | 2 năm | Duy trì phả hệ kỹ thuật và trạng thái; lưu trữ/xóa sau khi hết hạn trừ khi có lệnh giữ pháp lý |
| Metadata sự đồng ý / Mã băm / Liên hệ đã che giấu | 2 năm | Giữ lại làm bằng chứng kiểm toán; không lưu thông tin liên hệ thô sau khi bản chụp hết hạn |
| Bản chụp thông tin liên hệ thô đã mã hóa | 30 ngày sau khi kết thúc hóa đơn/chuyển phát | Xóa khóa mã hóa hoặc dữ liệu liên hệ cụ thể sau khi hết hạn; lệnh giữ pháp lý sẽ chặn xóa |
| Payload thô của nhà cung cấp | Mặc định 0 ngày | Chỉ lưu kết quả chuẩn hóa an toàn và mã vân tay; ngoại lệ mã hóa đòi hỏi chính sách vận hành riêng |
| Lịch sử lần thử provider / Trạng thái chuyển phát | 2 năm | Giữ phả hệ thử lại và đối soát; không lưu payload thô |
| Tham chiếu kiểm toán | Theo chính sách Audit cha, tối thiểu 7 năm nếu là chứng từ | Integration lưu mã tham chiếu, Audit sở hữu thẩm quyền lưu trữ pháp lý |
| Phiên chạy demo / Checkpoints / Khóa thuê lỗi | 90 ngày | Dọn dẹp tự động theo TTL trên non-prod; chỉ giữ tóm tắt đã che giấu lâu hơn khi có yêu cầu |
| Outbox / Inbox / Dữ liệu viễn thám | 90 ngày hoặc theo chính sách phát lại | Xóa/lưu trữ sau khung thời gian phát lại; không bao giờ xóa DLQ chưa xử lý |

Lệnh giữ pháp lý (Legal Hold) quy định rõ tenant, phạm vi, phân loại dữ liệu, khoảng thời gian, lý do và tác nhân, đồng thời có quyền ưu tiên cao hơn việc xóa dữ liệu thông thường. Các tác vụ xóa dữ liệu đảm bảo tính lũy quyền, đếm số lượng dòng/artifact, ghi nhận quyết định giữ pháp lý và phát ra tham chiếu kiểm toán. Việc lưu trữ hóa đơn thuế chính thức là một quyết định năng lực tách biệt hoàn toàn.

## 11. Xử lý lỗi, cơ chế thử lại và phục hồi (Error & Recovery)

| Tình huống sự cố | Mã lỗi chuẩn hóa / Trạng thái | Hành vi hệ thống yêu cầu |
|---|---|---|
| Sự kiện nguồn bị trùng lặp | `duplicate_source` | Bỏ qua an toàn; xác nhận ACK sau khi khử trùng lặp |
| Trùng khóa lũy quyền nhưng sai request | `idempotency_conflict` | Trả mã HTTP 409; tuyệt đối không gọi nhà cung cấp |
| Nhà cung cấp timeout / 429 / 5xx | `provider_retryable` | Giãn cách lũy thừa, tiêu hao ngân sách thử lại, sau đó đưa vào DLQ |
| Nhà cung cấp chưa rõ kết quả sau timeout | `provider_unknown` | Kích hoạt truy vấn đối soát; không tự ý coi là thành công hay thất bại |
| Dữ liệu hóa đơn không hợp lệ | `invoice_data_invalid` | Lỗi kết thúc; không gọi lại nhà cung cấp vô ích |
| Sự đồng ý không hợp lệ hoặc bị thu hồi | `consent_invalid` | Bỏ qua hoặc hủy bỏ chuyển phát; trạng thái hóa đơn giữ nguyên |
| Bị từ chối phạm vi hoặc môi trường | `forbidden` | Đóng ngắt an toàn; trả mã lỗi an toàn kèm tham chiếu kiểm toán |
| Xung đột phiên bản lạc quan CAS bị cũ | `version_conflict` | Trả mã HTTP 409; tuyệt đối không ghi dữ liệu từng phần |
| Cơ sở dữ liệu Integration gián đoạn | `integration_unavailable` | Không xác nhận sự kiện; để message broker thử lại hoặc đưa vào DLQ |
| Bộ xuất trace AWS X-Ray gián đoạn | `trace_degraded` | Tiếp tục luồng nghiệp vụ; điểm kiểm tra đánh dấu chất lượng suy giảm + cảnh báo |
| Xung đột khi đặt lại dữ liệu demo | `demo_run_conflict` | Không thực hiện dọn dẹp; phía gọi phải lấy lại trạng thái và thử lại |

## 12. Giao ước khả năng quan sát và kiểm toán (Observability & Audit Contract)

Các trường bắt buộc trên Span và Log: Tên dịch vụ, môi trường, mã băm phạm vi tenant/chi nhánh an toàn, thao tác, loại/phiên bản sự kiện, ID của đơn hàng/lượt checkout/hóa đơn/chuyển phát/demo, correlation/causation/saga ID, khóa/hồ sơ nhà cung cấp, trạng thái, mã lỗi chuẩn hóa, số lần thử lại và thời gian thực thi.

Tuyệt đối cấm ghi ra Log hoặc Span: Email thô, số điện thoại, Zalo ID, mã OTP, token JWT, chữ ký số provider, secret, payload thanh toán thô, phản hồi thô của provider, câu lệnh SQL hoặc ghi chú tùy ý của khách hàng.

Các chỉ số đo lường (Metrics):

- `order_paid_consumer_lag`, `invoice_operation_lag`, `invoice_issue_success_rate`, `invoice_provider_latency`, `invoice_retry_count`;
- `delivery_success_rate{channel}`, `delivery_latency{channel}`, `delivery_retry_count`, `delivery_expiry_count`;
- `inbox_duplicate_count`, `outbox_lag`, `dlq_depth`, `watermark_age`, `provider_unknown_count`;
- `demo_preflight_failure`, `demo_step_duration`, `demo_checkpoint_failure`, `demo_reset_duration`, `fault_lease_active_count`;
- `scope_denial`, `redaction_failure`, `trace_export_failure`.

Payload tham chiếu kiểm toán: Tenant/chi nhánh, tác nhân/tải công việc, hành động, loại/ID tài nguyên, lý do, tóm tắt an toàn trước/sau, correlation ID, phiên bản che giấu và thao tác nguồn. Dịch vụ Audit luôn là nơi lưu trữ bằng chứng pháp lý chính thức trong 7 năm.

## 13. Ma trận kiểm thử giao ước (Contract Test Matrix)

1. Kiểm thử OpenAPI xác thực đầy đủ cấu trúc phong bì, phân quyền, phạm vi, tính lũy quyền, tiêu đề ETag/CAS, con trỏ phân trang, cấu trúc lỗi và quy tắc cấm cache.
2. Kiểm thử tương thích sự kiện cha hiện tại (`OrderPaid` + version 1) và schema mới `OrderPaid.v1` đều hội tụ an toàn về duy nhất một tác vụ.
3. Trường hợp thiếu, sai, hết hạn hoặc sai tenant của `invoiceRecipientRef` không bao giờ làm rò rỉ thông tin liên hệ và không tạo yêu cầu chuyển phát trái phép.
4. Sự kiện `OrderPaid` bị trùng lặp, đến sai thứ tự hoặc phát lại chỉ tạo duy nhất một tác vụ hóa đơn và một lần gọi phát hành kỹ thuật.
5. Trường hợp nhà cung cấp đã chấp thuận nhưng worker bị crash sẽ hội tụ an toàn thông qua cùng khóa lũy quyền hoặc truy vấn đối soát.
6. Lỗi timeout, lỗi 429, lỗi 5xx hoặc phản hồi không hợp lệ từ provider được ánh xạ chính xác sang các trạng thái tương ứng mà không làm thay đổi trạng thái Order.
7. Chuyển phát Email/Zalo thành công, webhook callback trùng lặp, vượt rate limit, thông tin liên hệ sai, thu hồi sự đồng ý, hết hạn và gửi lại thủ công diễn ra tất định.
8. Kiểm thử kiểm soát phạm vi tenant/chi nhánh/vai trò, thu hồi tư cách thành viên, lỗ hổng IDOR và bộ nhớ đệm trên toàn bộ các endpoint công khai và nội bộ.
9. Rào chắn môi trường production từ chối tuyệt đối các endpoint nạp hạt giống/đặt lại dữ liệu/tiêm lỗi; hai phiên chạy demo đồng thời được cách ly hoàn toàn về namespace và trace.
10. Khóa thuê tiêm lỗi khi hết hạn, bị thu hồi hoặc kích hoạt công tắc ngắt khẩn cấp không để lại sự cố thanh toán diện rộng; Checkout Saga giải phóng kho chính xác một lần.
11. Các điểm kiểm tra demo thành công xác minh đầy đủ tính toàn vẹn của Shift, Recommendation, Loyalty, Payment/IPN, OrderPaid, Inventory, Reporting và Trợ lý AI.
12. Các bộ fixture số liệu chuẩn cho AI bao quát các trường hợp: chỉ hiển thị gợi ý, chấp nhận nhưng không thêm món, thêm món nhưng không thanh toán, dữ liệu Reporting đến muộn hoặc từng phần, và tính đúng mức tăng trưởng doanh thu.
13. Tác vụ dọn dẹp dữ liệu xóa bỏ thông tin liên hệ thô đúng hạn, bảo toàn phả hệ an toàn, tôn trọng lệnh giữ pháp lý và đảm bảo tính lũy quyền.
14. Cơ chế phát lại outbox/inbox/DLQ bảo toàn nguyên vẹn ID sự kiện và không bao giờ tạo tác dụng phụ trùng lặp cho hóa đơn, chuyển phát hay demo.
15. Kiểm thử tải xác thực hệ thống duy trì ổn định 10 sự kiện thanh toán/giây, 100 lần thử chuyển phát và 10 phiên chạy demo đồng thời với độ trễ hàng đợi và API nằm trong ngưỡng cam kết.

## 14. Các sửa đổi bổ sung cho giao ước cha (Parent Contract Amendments)

### POS Platform / Identity

- Bổ sung các quyền hạn của Integration và các quyết định phân quyền theo phạm vi môi trường/tải công việc trực tuyến.
- Đăng ký các kênh định tuyến sự kiện của Integration, phiên bản schema, khử trùng lặp nguồn và quyền sở hữu tham chiếu sang dịch vụ Audit.
- Tách biệt hoàn toàn các route/vai trò Demo Control Plane ra khỏi bản triển khai production và các client công khai sinh tự động.

### Order

- Bổ sung trường tùy chọn mờ `invoiceRecipientRef` và `invoiceProfile` vào ngữ cảnh checkout và sự kiện `OrderPaid.v1`.
- Chuẩn hóa việc ánh xạ tên/phiên bản sự kiện, phiên bản nguồn/aggregate, lượt checkout, bản chụp an toàn của dòng món/tổng tiền và correlation/causation ID.
- Công bố sự kiện bồi hoàn chuẩn mực hoặc API kiểm tra điểm checkpoint được phê duyệt phục vụ việc xác minh kịch bản demo.

### Payment / Step Functions

- Duy trì quyền sở hữu IPN/checksum và sự kiện `PaymentPaid.v1` bên trong Payment; cung cấp ngữ cảnh khóa thuê lỗi an toàn trên non-production tại ranh giới có kiểm soát.
- Bảo toàn ngữ nghĩa bồi hoàn của Checkout Saga và xử lý thanh toán muộn; Integration chỉ đóng vai trò quan sát.

### Inventory / Loyalty / Shift

- Công bố các API đọc hoặc sự kiện kiểm tra điểm checkpoint trên non-production phục vụ việc giải phóng/commit tồn kho, tích điểm, nhận diện khách hàng và trạng thái ca làm việc.

### Recommendation / Reporting / AI Assistant

- Công bố phả hệ quy kết chuẩn mực từ việc thêm dòng món cho tới nhóm khách hàng thanh toán hoàn tất.
- Công bố từ điển số liệu về mức tăng trưởng doanh thu / AOV, schema công cụ, độ tươi mới/tính đầy đủ và phiên bản nguồn gốc.
- Trợ lý AI duy trì nguyên tắc chỉ đọc; tuyệt đối không để Integration hoặc LLM tự tính toán số liệu doanh thu.

### Audit

- Đăng ký các tham chiếu hóa đơn, chuyển phát, demo, tiêm lỗi và truy cập dữ liệu kèm phân loại che giấu và thời hạn lưu trữ / giữ pháp lý.

## 15. Các Artifact triển khai bắt buộc trước khi viết mã nguồn

- `openapi.yaml` cho các API công khai về hóa đơn, kênh nhận, trạng thái và gửi lại.
- `openapi-internal.yaml` cho các thao tác demo, kiểm tra sức khỏe, đối soát và quản lý nhà cung cấp.
- `asyncapi.yaml` và các file JSON Schema cho các sự kiện tiếp nhận/phát ra cùng cơ chế ánh xạ tương thích sự kiện cha.
- `provider-ports.schema.yaml` và các bộ fixture adapter sandbox cho hóa đơn, email và Zalo.
- `demo-scenario.schema.yaml`, schema manifest dữ liệu hạt giống/tài nguyên và các bộ fixture điểm kiểm tra chuẩn.
- Các file migration PostgreSQL/Flyway hoàn chỉnh kèm theo comment cột, phân quyền bảng PII giới hạn, các ràng buộc toàn vẹn, chỉ mục và tác vụ lưu trữ.
- Các bộ fixture giao ước cho sự kiện OrderPaid trùng lặp/phát lại, worker bị crash, xác thực chữ ký callback, thu hồi sự đồng ý và truy cập ngoài phạm vi.
- Tập mẫu kiểm thử an ninh cho khóa thuê lỗi và kiểm tra chính sách môi trường non-production.
- Từ điển số liệu tăng trưởng cho AI, dữ liệu mẫu chuẩn và schema hàm của Reporting/Recommendation.
- Kịch bản kiểm thử tải k6, quy trình vận hành xử lý DLQ/phát lại, kiểm thử che giấu trace AWS X-Ray và quy trình xoay vòng khóa bí mật nhà cung cấp.

## 16. Cổng phê duyệt giao ước (Contracts Approval Gate)

- **Trạng thái hiện tại:** Approved — Người dùng đã chấp thuận các cảnh báo từ đợt đánh giá Giao ước.
- **Yêu cầu trước khi Hoàn thiện:** Đã hoàn thành; các schema máy đọc được, sửa đổi bổ sung của dịch vụ cha, migration cơ sở dữ liệu, vector nhà cung cấp và fixture demo được duy trì dưới dạng các cổng triển khai trong tài liệu tổng kết cuối cùng.
- **Quy tắc tiên quyết khi triển khai:** Tuyệt đối không viết controller, worker, adapter nhà cung cấp hay trình điều phối demo trực tiếp từ văn xuôi; bắt buộc phải sinh các giao ước máy đọc được và các bộ fixture kiểm thử trước tiên.
