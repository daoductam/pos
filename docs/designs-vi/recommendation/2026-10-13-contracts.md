Started at: 2026/10/13 00:00:00
Finished at: 2026/10/13 00:00:00
Total time: 0 minutes
---

# Module 8 — Gợi ý món / Upsell: API & Data Contracts

**Ngày:** 2026-10-13  
**Trạng thái:** Approved with warnings  
**Vision:** [Recommendation Vision](./2026-10-13-vision.md)  
**Requirements:** [Recommendation Requirements](./2026-10-13-requirements.md)  
**Architecture:** [Recommendation Architecture](./2026-10-13-architecture.md)  
**Sequence diagrams:** [Recommendation Sequences](./2026-10-13-sequence-diagrams.adoc)

Tài liệu này là nguồn chuẩn để sinh OpenAPI 3.1, AsyncAPI/JSON Schema và các migration PostgreSQL cho Recommendation Service. Các điều chỉnh bổ sung đối với Order, Catalog, Inventory và Loyalty trong §12 là các hợp đồng tích hợp bắt buộc phải hoàn thành trước khi triển khai mã nguồn.

## 1. Quy ước hợp đồng (Contract conventions)

### 1.1 Phong cách API (API style)

- Public REST tuân thủ OpenAPI 3.1 dưới tiền tố `/api/v1`.
- Internal REST tuân thủ OpenAPI 3.1 dưới tiền tố `/internal/v1`, sử dụng workload identity/mTLS, không mở ra ngoài qua Angular.
- Các sự kiện sử dụng envelope chuẩn của parent platform kết hợp AsyncAPI/JSON Schema; cơ chế phân phối ít nhất một lần (at-least-once).
- Các trường định danh (ID) là chuỗi UUID v7; mốc thời gian là chuẩn ISO-8601 UTC.
- Tiền tệ sử dụng đơn vị minor `int64`; tiền tệ P0 là `VND`.
- Điểm số `score` / chỉ số ML sử dụng chuỗi số thập phân (decimal string) hoặc số có độ chính xác cố định (fixed-scale number) trong schema; tuyệt đối không dùng số thực dấu phẩy động nhị phân cho tiền tệ.
- Mọi thay đổi phá vỡ tính tương thích (breaking change) bắt buộc tạo major URI / phiên bản sự kiện mới; các trường tùy chọn mới phải tương thích ngược.

### 1.2 Headers

| Header | Public | Internal | Ý nghĩa |
|---|---:|---:|---|
| `Authorization` | Bắt buộc | Bắt buộc | Cognito JWT hoặc thông tin xác thực workload / mTLS |
| `X-Active-Branch-Id` | Yêu cầu theo chi nhánh | Ngữ cảnh đã ký | Chi nhánh đang chọn trên UI; máy chủ thẩm định lại quyền thành viên |
| `X-Correlation-Id` | Khuyến nghị | Bắt buộc | Truy vết xuyên suốt POS/Order/Recommendation/sự kiện |
| `X-Causation-Id` | Không | Khuyến nghị | Thao tác lệnh/sự kiện trực tiếp gây ra hành động |
| `Idempotency-Key` | Đột biến dữ liệu | Đột biến / tác vụ nền | Phạm vi tenant + thao tác + actor/workload |
| `If-Match` | Đột biến luật/mô hình | Lệnh có phiên bản | Token phiên bản kiểm soát đồng thời lạc quan |
| `Content-Type` | Body | Body | `application/json; charset=utf-8` |

Client không được tự truyền quyền sở hữu tenant, vai trò, số điện thoại khách hàng hay tư cách thành viên chi nhánh trong request body. `customerKey` chỉ hợp lệ nếu là khóa opaque do Loyalty cấp và Recommendation đã xác thực được phạm vi / tính đủ điều kiện.

### 1.3 Envelope thành công và lỗi (Success/error envelopes)

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
    "code": "projection_stale",
    "message": "Recommendation data is temporarily stale",
    "details": [
      {
        "field": "branchId",
        "code": "availability_watermark_old",
        "message": "Refresh the cart and retry"
      }
    ],
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

Thông báo lỗi không bao giờ để lộ SQL, JWT, số điện thoại, vector đặc trưng, prompt, artifact mô hình, secret nhà cung cấp hoặc sự tồn tại của tài nguyên ngoài phạm vi tenant.

### 1.4 Bảng mã HTTP và mã lỗi (HTTP/error codes)

| HTTP | Mã lỗi | Ý nghĩa | Thử lại (Retry) |
|---:|---|---|---:|
| 200 | — | Truy vấn / tương tác được chấp nhận hoặc phát lại lũy nghiệm | Không |
| 201 | — | Bản nháp / phiên / tác vụ đã được tạo thành công | Không |
| 202 | `accepted` | Tác vụ huấn luyện / cold-start / tái dựng đã vào hàng đợi | Poll kết quả |
| 400 | `validation_error` | Định dạng hoặc trường dữ liệu không hợp lệ | Không |
| 401 | `unauthorized` | Thiếu, sai hoặc hết hạn thông tin xác thực | Làm mới token |
| 403 | `forbidden` | Vai trò / tenant / chi nhánh không được phép truy cập | Không |
| 404 | `not_found` | Tài nguyên không tồn tại trong phạm vi được ủy quyền | Không |
| 409 | `cart_context_unavailable` | Thiếu ngữ cảnh giỏ hàng hoặc sai lệch phiên bản đơn hàng | Làm mới giỏ hàng |
| 409 | `suggestion_expired` | Phiên gợi ý đã hết hạn TTL hoặc giỏ hàng đã thay đổi | Yêu cầu gợi ý mới |
| 409 | `candidate_not_in_session` | Món không thuộc phiên gợi ý này | Không |
| 409 | `candidate_unavailable` | Ứng viên không thỏa mãn điều kiện khả dụng mới nhất | Làm mới / Order thẩm định |
| 409 | `projection_stale` | Hình chiếu cần thiết bị cũ hơn ngưỡng TTL an toàn | Thử lại / Rebuild |
| 409 | `customer_feature_unavailable` | Thiếu hoặc không đủ điều kiện ngữ cảnh đặc trưng opaque | Fallback về luật kết hợp |
| 409 | `model_unavailable` | Mô hình active bị thiếu hoặc không khỏe mạnh | Fallback về luật kết hợp |
| 409 | `rule_not_editable` | Phiên bản đã công bố hoặc đã ngừng áp dụng là bất biến | Tạo bản nháp mới |
| 409 | `rule_publish_forbidden` | Chỉ duy nhất Chủ chuỗi (Owner) mới được công bố/ngừng luật | Không |
| 409 | `rule_version_conflict` | Sai lệch phiên bản hoặc header If-Match đã cũ | Đọc lại dữ liệu |
| 409 | `model_quality_gate_failed` | Mô hình ứng viên không vượt qua cổng kiểm soát chất lượng | Giữ mô hình active cũ |
| 409 | `training_run_conflict` | Trùng khóa thuê (lease) hoặc trùng tác vụ đang chạy | Dùng tác vụ hiện có |
| 409 | `idempotency_conflict` | Trùng key nhưng mã băm dữ liệu chuẩn tắc khác nhau | Không |
| 409 | `duplicate_request` | Đã tồn tại kết quả ngữ nghĩa trước đó | Dùng kết quả cũ |
| 422 | `tenant_scope_mismatch` | ID hoặc khóa nằm ngoài phạm vi tenant đã ký xác thực | Không |
| 422 | `interaction_duplicate` | Trùng lặp nguồn tương tác | No-op ngữ nghĩa |
| 429 | `provider_rate_limited` | Vượt giới hạn tần suất gọi DeepSeek / nhà cung cấp | Chờ theo backoff |
| 503 | `provider_unavailable` | Nhà cung cấp dịch vụ ngoại vi gặp sự cố | Thử lại bất đồng bộ |
| 503 | `service_unavailable` | Cơ sở dữ liệu hoặc phụ thuộc cục bộ không an toàn | Thử lại có giới hạn |

Gợi ý P0 khi không có ứng viên hợp lệ sẽ trả về HTTP `200` với `items=[]`; đây là kết quả bình thường, không phải lỗi máy chủ.

## 2. Hợp đồng phân quyền (Authorization contract)

| Quyền hạn | Chủ chuỗi (Owner) | Quản lý (Manager) | Thu ngân (Cashier) | Worker nội bộ |
|---|---:|---:|---:|---:|
| `recommendation:read` | Phạm vi tenant/chi nhánh | Chi nhánh phân công | Chi nhánh phân công | — |
| `suggestion:interact` | Có | Có | Chi nhánh phân công | — |
| `rule:draft` | Có | Có | Không | — |
| `rule:publish` | Có | Không | Không | Worker đã ký sau lệnh của Owner |
| `rule:retire` | Có | Không | Không | Worker đã ký sau lệnh của Owner |
| `model:read-health` | Toàn tenant | Xem theo tenant/chi nhánh | Không | Có |
| `model:activate/rollback` | Có (qua lệnh có audit) | Không | Không | Worker đã ký |
| `training:run/replay` | Vận hành giới hạn | Không | Không | Bộ lập lịch / Worker |
| `cold-start:review` | Có / Người xét duyệt | Phạm vi xét duyệt được giao | Không | Chỉ tạo mới, không duyệt |
| `projection:rebuild` | Vận hành giới hạn | Không | Không | Worker đã ký |

Mọi truy vấn repository đều bắt buộc có điều kiện lọc tenant; vị từ chi nhánh áp dụng cho dữ liệu theo chi nhánh / phiên / hình chiếu. Chủ chuỗi bắt buộc phải chỉ định chi nhánh cụ thể khi gọi yêu cầu gợi ý giỏ hàng.

## 3. Bề mặt API công khai (Public API surface)

| Phương thức | Đường dẫn | Quyền hạn | Yêu cầu (Request) | Phản hồi (Response) | Trạng thái |
|---|---|---|---|---|---|
| POST | `/api/v1/recommendations` | recommendation:read | `RecommendationRequest` | `RecommendationResponse` | 200, 403, 409, 422 |
| POST | `/api/v1/recommendations/{suggestionId}/shown` | suggestion:interact | `SuggestionShownRequest` | `InteractionResponse` | 200, 403, 404, 409, 422 |
| POST | `/api/v1/recommendations/{suggestionId}/accept` | suggestion:interact | `SuggestionAcceptedRequest` | `InteractionResponse` | 200, 403, 404, 409, 422 |
| GET | `/api/v1/recommendation-rules` | rule:draft/read | cursor/filter | `RulePage` | 200, 403, 422 |
| POST | `/api/v1/recommendation-rules` | rule:draft | `CreateRuleRequest` | `RuleResponse` | 201, 403, 409, 422 |
| PATCH | `/api/v1/recommendation-rules/{ruleId}` | rule:draft | `UpdateRuleRequest` + If-Match | `RuleResponse` | 200, 403, 404, 409, 422 |
| POST | `/api/v1/recommendation-rules/{ruleId}/publish` | rule:publish | `PublishRuleRequest` | `RuleResponse` | 200, 403, 404, 409, 422 |
| POST | `/api/v1/recommendation-rules/{ruleId}/retire` | rule:retire | `RetireRuleRequest` | `RuleResponse` | 200, 403, 404, 409 |
| GET | `/api/v1/recommendation-models/active` | model:read-health | Phạm vi tenant/chi nhánh | `ActiveModelResponse` | 200, 403, 404 |
| GET | `/api/v1/cold-start-features` | cold-start:review | cursor/filter | `ColdStartFeaturePage` | 200, 403, 422 (P2) |
| POST | `/api/v1/cold-start-features/{candidateId}/approve` | cold-start:review | `ReviewColdStartRequest` | `ColdStartFeatureResponse` | 200, 403, 404, 409 (P2) |

Danh sách luật sử dụng phân trang theo cursor, mặc định `20`, tối đa `100`, sắp xếp ổn định theo `(updatedAt DESC, ruleId DESC)`. Các bộ lọc được phép: `status`, `source`, `candidateItemId`, `updatedFrom`, `updatedTo`.

## 4. Schema API gợi ý (Recommendation API schemas)

### 4.1 Lấy danh sách gợi ý món theo giỏ hàng (Get Cart Recommendations)

`POST /api/v1/recommendations`

API thời gian thực tính toán gợi ý món thêm (upsell/cross-sell) dựa trên giỏ hàng hiện tại với SLA p95 ≤ 150ms.

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
  "orderId": "0192b2d0-7f1d-7abc-8d2c-123456789001",
  "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789002",
  "cartVersion": 12,
  "customerKey": "custkey_opaque_01J...",
  "maxItems": 3,
  "clientReference": "cart-panel-refresh-4"
}
```

| Trường | Kiểu | Bắt buộc | Quy tắc |
|---|---|:---:|---|
| `orderId` | UUID | Có | Đơn hàng thuộc cùng ngữ cảnh tenant / chi nhánh |
| `branchId` | UUID | Có | Máy chủ thẩm định chi nhánh được ủy quyền |
| `cartVersion` | int64 | Có | Phải khớp với ngữ cảnh / hình chiếu của Order |
| `customerKey` | Chuỗi opaque/null | Không | Do Loyalty cấp; không có SĐT; gắn với tenant |
| `maxItems` | int32 | Không | Mặc định 3, phạm vi từ 1 đến 5 |
| `clientReference` | string/null | Không | Phục vụ truy vết UI; tối đa 128 ký tự đã làm sạch |

Recommendation sẽ gọi hợp đồng ngữ cảnh của Order khi cache bị miss hoặc phiên bản không khớp; client công khai không thể tự gửi ảnh chụp danh sách món để làm chân lý.

#### Phản hồi Thành Công `200 OK`
```json
{
  "data": {
    "suggestionId": "0192b2d0-7f1d-7abc-8d2c-123456789010",
    "orderId": "0192b2d0-7f1d-7abc-8d2c-123456789001",
    "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789002",
    "cartVersion": 12,
    "strategy": "ASSOCIATION",
    "fallbackReason": null,
    "items": [
      {
        "itemId": "0192b2d0-7f1d-7abc-8d2c-123456789011",
        "variantId": null,
        "name": "Coca Cola",
        "priceMinor": 15000,
        "currency": "VND",
        "rank": 1,
        "score": "0.8400",
        "reasonCode": "FREQUENTLY_BOUGHT_TOGETHER",
        "ruleId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
        "ruleVersion": 4,
        "modelVersion": null,
        "availabilityObservedAt": "2026-10-13T08:00:00Z"
      }
    ],
    "availabilityStatus": "FRESH",
    "projectionWatermark": "inventory:branch:42:v901",
    "expiresAt": "2026-10-13T08:10:00Z",
    "generatedAt": "2026-10-13T08:00:01Z"
  },
  "meta": {
    "requestId": "req_0192b2d0-7f1d-7abc-8d2c-123456789001",
    "correlationId": "cor_0192b2d0-7f1d-7abc-8d2c-123456789002",
    "timestamp": "2026-10-13T08:00:01Z"
  }
}
```

Trường `strategy` nhận các giá trị: `ASSOCIATION`, `PERSONALIZED_ALS` hoặc `ASSOCIATION_FALLBACK`. Mảng `items` rỗng là kết quả hoàn toàn hợp lệ khi không có ứng viên phù hợp.

#### Phản hồi Lỗi Thường Gặp
- `403 Forbidden` (`code: "forbidden"`): Không có quyền `recommendation:read` tại chi nhánh.
- `409 Conflict` (`code: "cart_version_mismatch"`): Phiên bản giỏ hàng của client cũ hơn server.
- `422 Unprocessable Entity` (`code: "validation_error"`): Chi nhánh không hợp lệ.

---

### 4.2 Ghi nhận tương tác hiển thị gợi ý (Shown Interaction)

`POST /api/v1/recommendations/{suggestionId}/shown`

Ghi nhận sự kiện món gợi ý đã được hiển thị trên giao diện POS phục vụ tính tỷ lệ chuyển đổi (conversion tracking).

#### Tiêu đề Yêu cầu (Headers)
```http
Authorization: Bearer <Cognito JWT Token>
Content-Type: application/json
Accept: application/json
X-Correlation-ID: 0192b2d0-7f1d-7abc-8d2c-123456789001
```

#### Request Body Mẫu
```json
{
  "itemId": "0192b2d0-7f1d-7abc-8d2c-123456789011",
  "interactionId": "ui-interaction-01J...",
  "renderedAt": "2026-10-13T08:00:02Z",
  "displaySlot": "CART_UPSELL",
  "clientVersion": "pos-web-1.2.0"
}
```

Máy chủ trích xuất thông tin tenant, chi nhánh và người thực hiện từ token xác thực và phiên gợi ý. Lệnh chỉ hợp lệ với món ứng viên thuộc phiên đó; gửi trùng lặp bộ `(suggestionId, itemId, interactionId, SHOWN)` sẽ trả về kết quả ban đầu (idempotent).

#### Phản hồi Thành Công `200 OK`
```json
{
  "data": {
    "interactionId": "ui-interaction-01J...",
    "suggestionId": "0192b2d0-7f1d-7abc-8d2c-123456789010",
    "itemId": "0192b2d0-7f1d-7abc-8d2c-123456789011",
    "eventType": "SHOWN",
    "status": "RECORDED",
    "eventId": "0192b2d0-7f1d-7abc-8d2c-123456789015",
    "recordedAt": "2026-10-13T08:00:02Z"
  },
  "meta": {
    "requestId": "req_0192b2d0-7f1d-7abc-8d2c-123456789001",
    "correlationId": "cor_0192b2d0-7f1d-7abc-8d2c-123456789002",
    "timestamp": "2026-10-13T08:00:02Z"
  }
}
```

---

### 4.3 Ghi nhận tương tác chấp thuận gợi ý (Accepted Interaction)

`POST /api/v1/recommendations/{suggestionId}/accept`

Ghi nhận sự kiện thu ngân hoặc khách hàng chấp thuận thêm món gợi ý vào đơn hàng.

#### Tiêu đề Yêu cầu (Headers)
```http
Authorization: Bearer <Cognito JWT Token>
Content-Type: application/json
Accept: application/json
X-Correlation-ID: 0192b2d0-7f1d-7abc-8d2c-123456789001
```

#### Request Body Mẫu
```json
{
  "itemId": "0192b2d0-7f1d-7abc-8d2c-123456789011",
  "interactionId": "ui-interaction-01J...-accept",
  "orderLineId": "0192b2d0-7f1d-7abc-8d2c-123456789013",
  "orderVersionAfterAdd": 13,
  "acceptedAt": "2026-10-13T08:00:03Z",
  "addLineCorrelationId": "0192b2d0-7f1d-7abc-8d2c-123456789014"
}
```

Giao diện POS bắt buộc phải gọi Order thêm dòng trước và chỉ gọi endpoint này sau khi Order phản hồi thành công. Recommendation kiểm tra mối liên kết phiên/đơn hàng/món và lưu lại bằng chứng; tuyệt đối không gọi sang Payment hay tự ý thay đổi Order.

#### Phản hồi Thành Công `200 OK`
```json
{
  "data": {
    "interactionId": "ui-interaction-01J...-accept",
    "suggestionId": "0192b2d0-7f1d-7abc-8d2c-123456789010",
    "itemId": "0192b2d0-7f1d-7abc-8d2c-123456789011",
    "eventType": "ACCEPTED",
    "status": "RECORDED",
    "eventId": "0192b2d0-7f1d-7abc-8d2c-123456789016",
    "recordedAt": "2026-10-13T08:00:03Z"
  },
  "meta": {
    "requestId": "req_0192b2d0-7f1d-7abc-8d2c-123456789001",
    "correlationId": "cor_0192b2d0-7f1d-7abc-8d2c-123456789002",
    "timestamp": "2026-10-13T08:00:03Z"
  }
}
```

---

## 5. Schema quản trị luật kết hợp (Rule governance schemas)

### 5.1 Danh sách luật gợi ý (List Recommendation Rules)

`GET /api/v1/recommendation-rules`

Truy vấn danh sách các luật gợi ý của tenant kèm phân trang con trỏ và bộ lọc trạng thái.

#### Tiêu đề Yêu cầu (Headers)
```http
Authorization: Bearer <Cognito JWT Token>
Accept: application/json
X-Correlation-ID: 0192b2d0-7f1d-7abc-8d2c-123456789001
```

#### Tham số Truy vấn (Query Parameters)
| Tham số | Kiểu | Mặc định | Mô tả |
|---|---|:---:|---|
| `status` | string | null | Lọc theo: `DRAFT`, `PUBLISHED`, `RETIRED` |
| `source` | string | null | Lọc theo: `MANUAL`, `MINED_FP_GROWTH` |
| `candidateItemId` | UUID | null | Lọc theo món được gợi ý (consequent) |
| `limit` | integer | 20 | Số lượng bản ghi (tối đa 100) |
| `cursor` | string | null | Con trỏ phân trang |

#### Phản hồi Thành Công `200 OK`
```json
{
  "data": [
    {
      "ruleId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
      "logicalRuleKey": "rule_chicken_coke",
      "version": 4,
      "status": "PUBLISHED",
      "source": "MANUAL",
      "antecedents": [
        {"itemId": "0192b2d0-7f1d-7abc-8d2c-123456789020", "itemName": "Gà rán giòn cay"}
      ],
      "consequent": {
        "itemId": "0192b2d0-7f1d-7abc-8d2c-123456789021",
        "itemName": "Coca Cola lon"
      },
      "priority": 100,
      "supportBps": 450,
      "confidenceBps": 6800,
      "liftMilli": 1420,
      "effectiveFrom": "2026-10-13T00:00:00Z",
      "effectiveTo": null,
      "publishedAt": "2026-10-13T07:00:00Z",
      "updatedAt": "2026-10-13T07:00:00Z"
    }
  ],
  "meta": {
    "limit": 20,
    "hasMore": false,
    "nextCursor": null,
    "requestId": "req_0192b2d0-7f1d-7abc-8d2c-123456789001",
    "correlationId": "cor_0192b2d0-7f1d-7abc-8d2c-123456789002",
    "timestamp": "2026-10-13T08:00:00Z"
  }
}
```

---

### 5.2 Tạo bản nháp luật mới (Create Draft Rule)

`POST /api/v1/recommendation-rules`

Tạo bản nháp luật gợi ý thủ công hoặc từ đề xuất khai phá dữ liệu.

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
  "source": "MANUAL",
  "antecedents": [
    {
      "itemId": "0192b2d0-7f1d-7abc-8d2c-123456789020",
      "variantId": null,
      "modifierOptionId": null
    }
  ],
  "consequent": {
    "itemId": "0192b2d0-7f1d-7abc-8d2c-123456789021",
    "variantId": null
  },
  "priority": 100,
  "effectiveFrom": "2026-10-13T00:00:00Z",
  "effectiveTo": null,
  "note": "Gà rán đi cùng Coca"
}
```

Máy chủ thẩm định định danh chuẩn tắc, không tự gợi ý chính nó, số lượng điều kiện tối đa, quyền sở hữu món của tenant, khoảng thời gian hiệu lực và giới hạn độ ưu tiên. Các chỉ số support/confidence/lift do máy chủ hoặc tác vụ ML đáng tin cậy tính toán/nhập vào; dữ liệu nhập từ Manager không thể giả mạo các chỉ số chất lượng này.

#### Phản hồi Thành Công `201 Created`
```json
{
  "data": {
    "ruleId": "0192b2d0-7f1d-7abc-8d2c-123456789030",
    "logicalRuleKey": "rule_manual_01J...",
    "version": 1,
    "status": "DRAFT",
    "source": "MANUAL",
    "antecedents": [
      {"itemId": "0192b2d0-7f1d-7abc-8d2c-123456789020", "variantId": null}
    ],
    "consequent": {
      "itemId": "0192b2d0-7f1d-7abc-8d2c-123456789021",
      "variantId": null
    },
    "priority": 100,
    "effectiveFrom": "2026-10-13T00:00:00Z",
    "effectiveTo": null,
    "createdAt": "2026-10-13T08:05:00Z",
    "updatedAt": "2026-10-13T08:05:00Z"
  },
  "meta": {
    "requestId": "req_0192b2d0-7f1d-7abc-8d2c-123456789001",
    "correlationId": "cor_0192b2d0-7f1d-7abc-8d2c-123456789002",
    "timestamp": "2026-10-13T08:05:00Z"
  }
}
```

---

### 5.3 Cập nhật bản nháp luật (Update Draft Rule)

`PATCH /api/v1/recommendation-rules/{ruleId}`

Chỉnh sửa bản nháp luật chưa công bố. Bắt buộc kèm theo header `If-Match: "<version>"` để ngăn ngừa xung đột ghi đè.

#### Tiêu đề Yêu cầu (Headers)
```http
Authorization: Bearer <Cognito JWT Token>
Content-Type: application/json
Accept: application/json
If-Match: "1"
X-Correlation-ID: 0192b2d0-7f1d-7abc-8d2c-123456789001
```

#### Request Body Mẫu
```json
{
  "priority": 150,
  "note": "Cập nhật ưu tiên mùa hè"
}
```

#### Phản hồi Thành Công `200 OK`
Trả về `RuleResponse` với `version: 2` và các trường đã được cập nhật.

---

### 5.4 Công bố luật gợi ý (Publish Rule)

`POST /api/v1/recommendation-rules/{ruleId}/publish`

Chỉ Chủ chuỗi (Owner) mới có quyền công bố luật. Sau khi công bố, luật chuyển sang trạng thái `PUBLISHED` bất biến và áp dụng vào engine gợi ý giỏ hàng.

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
  "expectedVersion": 2,
  "reason": "Phê duyệt triển khai chiến dịch đồ uống kèm đồ ăn nhanh",
  "qualityEvidenceRef": "fp_growth_run_20261012"
}
```

#### Phản hồi Thành Công `200 OK`
```json
{
  "data": {
    "ruleId": "0192b2d0-7f1d-7abc-8d2c-123456789030",
    "status": "PUBLISHED",
    "version": 3,
    "publishedAt": "2026-10-13T08:10:00Z"
  },
  "meta": {
    "requestId": "req_0192b2d0-7f1d-7abc-8d2c-123456789001",
    "correlationId": "cor_0192b2d0-7f1d-7abc-8d2c-123456789002",
    "timestamp": "2026-10-13T08:10:00Z"
  }
}
```

---

### 5.5 Ngừng áp dụng luật gợi ý (Retire Rule)

`POST /api/v1/recommendation-rules/{ruleId}/retire`

Ngừng hiệu lực của một luật đã công bố mà không xóa vật lý bản ghi lịch sử.

#### Tiêu đề Yêu cầu (Headers)
```http
Authorization: Bearer <Cognito JWT Token>
Content-Type: application/json
Accept: application/json
X-Correlation-ID: 0192b2d0-7f1d-7abc-8d2c-123456789001
```

#### Request Body Mẫu
```json
{
  "expectedVersion": 3,
  "reason": "Hết chiến dịch mùa hè"
}
```

#### Phản hồi Thành Công `200 OK`
Trả về `RuleResponse` với `status: "RETIRED"`.

---

### 5.6 Vòng đời luật (Rule lifecycle)

| Trạng thái | Chuyển đổi cho phép | Tác nhân thực hiện |
|---|---|---|
| `DRAFT` | Cập nhật, nộp/công bố | Manager/Owner cập nhật; Chủ chuỗi (Owner) công bố |
| `PUBLISHED` | Ngừng áp dụng, bị thay thế bởi phiên bản mới | Chủ chuỗi (Owner) |
| `RETIRED` | Trạng thái kết thúc / chỉ đọc | Không ai |

`PublishRuleRequest` yêu cầu `reason`, `expectedVersion` và trường tùy chọn `qualityEvidenceRef`. `RetireRuleRequest` yêu cầu `reason` và `expectedVersion`. Các phiên bản đã công bố là bất biến; việc chỉnh sửa sẽ tạo ra bản nháp / phiên bản mới.

---

### 5.7 Thông tin mô hình gợi ý đang hoạt động (Get Active Model)

`GET /api/v1/recommendation-models/active`

Truy vấn thông tin kiểm toán về mô hình máy học (ALS) hoặc phiên bản tập luật đang active của tenant.

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
    "modelFamily": "ALS",
    "modelVersion": "als-model-v2.1",
    "status": "ACTIVE",
    "sourceWatermark": "order_paid:2026-10-12T17:00:00Z",
    "metrics": {
      "precisionAtK": "0.1850",
      "recallAtK": "0.4210",
      "ndcg": "0.2940"
    },
    "activatedAt": "2026-10-12T18:00:00Z"
  },
  "meta": {
    "requestId": "req_0192b2d0-7f1d-7abc-8d2c-123456789001",
    "correlationId": "cor_0192b2d0-7f1d-7abc-8d2c-123456789002",
    "timestamp": "2026-10-13T08:15:00Z"
  }
}
```

---

### 5.8 Danh sách đặc trưng khởi động nguội (P2 Cold Start Features)

`GET /api/v1/cold-start-features`

Truy vấn các món mới chưa có dữ liệu giao dịch cần gán đặc trưng tương đồng từ LLM (DeepSeek) để nhân viên duyệt.

#### Phản hồi Thành Công `200 OK`
Trả về danh sách các đặc trưng ứng viên kèm `candidateId`, `itemId`, `featurePayload`, `confidence`, và `reviewStatus`.

---

### 5.9 Phê duyệt đặc trưng khởi động nguội (P2 Approve Cold Start)

`POST /api/v1/cold-start-features/{candidateId}/approve`

Quản lý phê duyệt đặc trưng của món mới để đưa vào không gian vector gợi ý tương đồng.

#### Phản hồi Thành Công `200 OK`
Trả về kết quả phê duyệt với `reviewStatus: "APPROVED"`.

---

## 6. Hợp đồng mô hình, huấn luyện và API nội bộ (Internal contracts)

### 6.1 Danh mục endpoint nội bộ

| Phương thức | Đường dẫn | Caller | Request | Response | Trạng thái |
|---|---|---|---|---|---|
| POST | `/internal/v1/recommendations/training-runs` | Bộ lập lịch / Vận hành | `CreateTrainingRunRequest` | `TrainingRunResponse` | 201/202, 409 |
| GET | `/internal/v1/recommendations/training-runs/{runId}` | Worker / Vận hành | — | `TrainingRunResponse` | 200, 404 |
| POST | `/internal/v1/recommendations/models/{modelId}/validate` | Worker ML | `ValidateModelRequest` | `ModelResponse` | 200, 409, 422 |
| POST | `/internal/v1/recommendations/models/{modelId}/activate` | Luồng duyệt của Owner / Worker | `ActivateModelRequest` | `ModelResponse` | 200, 403, 409 |
| POST | `/internal/v1/recommendations/models/{modelId}/rollback` | Luồng vận hành của Owner | `RollbackModelRequest` | `ModelResponse` | 200, 403, 409 |
| POST | `/internal/v1/recommendations/projections/rebuild` | Worker vận hành | `RebuildProjectionRequest` | `RebuildOperationResponse` | 202, 409 |
| POST | `/internal/v1/recommendations/cold-start-jobs` | Bộ lập lịch / Worker | `CreateColdStartJobRequest` | `ColdStartJobResponse` | 201/202, 409 |

### 6.2 Yêu cầu huấn luyện (Training request)

```json
{
  "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789030",
  "modelFamily": "ALS",
  "sourceWatermark": "order-paid:2026-10-12T23:59:59Z:v901",
  "trainingWindowFrom": "2026-07-01T00:00:00Z",
  "trainingWindowTo": "2026-10-12T23:59:59Z",
  "featureVersion": "loyalty-feature-v3",
  "codeVersion": "recommender-2026.10.13",
  "requestedBy": "scheduler",
  "correlationId": "0192b2d0-7f1d-7abc-8d2c-123456789031"
}
```

Mỗi bộ ba `(tenantId, modelFamily, sourceWatermark)` chỉ có duy nhất một lần chạy được cấp quyền khóa thuê (lease); yêu cầu trùng lặp trả về lần chạy hiện có.

### 6.3 Thẩm định và kích hoạt mô hình (Validation and activation)

`ValidateModelRequest` bao gồm URI artifact ứng viên / mã băm checksum, ID ảnh chụp, số dòng dữ liệu, số tenant, phiên bản đặc trưng/mã nguồn, các chỉ số (`recallAtK`, `ndcgAtK`, `coverage`), kết quả kiểm tra cổng bắt buộc và tham chiếu báo cáo thẩm định.

`ActivateModelRequest` bao gồm `expectedRegistryVersion`, `reason`, `qualityPolicyVersion`, `artifactChecksum`. Máy chủ yêu cầu 100% cổng kiểm soát đạt yêu cầu và thực hiện cập nhật con trỏ active bằng kỹ thuật compare-and-swap.

`RollbackModelRequest` bao gồm `targetModelVersion`, `expectedRegistryVersion`, `reason`, `incidentReference`.

### 6.4 Trạng thái mô hình (Model statuses)

Gồm: `CANDIDATE`, `VALIDATING`, `ACTIVE`, `RETIRED`, `REJECTED`, `FAILED`.

Chỉ có duy nhất một mô hình `ACTIVE` cho mỗi tenant / dòng mô hình. Khi nạp mô hình lúc runtime gặp sự cố, hệ thống tự động kích hoạt fallback về luật kết hợp, tuyệt đối không tự ý kích hoạt mô hình mới.

## 7. Hợp đồng tích hợp liên service (Cross-service contracts)

### 7.1 Hợp đồng đọc ngữ cảnh giỏ hàng từ Order (Order context read contract)

Order bắt buộc phải mở một hợp đồng nội bộ dành riêng cho workload của Recommendation:

`GET /internal/v1/orders/{orderId}/recommendation-context?branchId={branchId}&cartVersion={cartVersion}`

Phản hồi:

```json
{
  "data": {
    "orderId": "0192b2d0-7f1d-7abc-8d2c-123456789001",
    "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789030",
    "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789002",
    "cartVersion": 12,
    "status": "DRAFT",
    "lines": [
      {
        "lineId": "0192b2d0-7f1d-7abc-8d2c-123456789040",
        "itemId": "0192b2d0-7f1d-7abc-8d2c-123456789020",
        "variantId": null,
        "modifierOptionIds": [],
        "quantity": 1
      }
    ],
    "resolvedAt": "2026-10-13T08:00:00Z"
  }
}
```

Order trả về `409 cart_context_unavailable` khi sai lệch phiên bản và không bao giờ để lộ đơn hàng của tenant/chi nhánh khác. Recommendation không lưu cache phản hồi này vượt quá thời hạn yêu cầu / phiên quy định.

Lệnh thêm dòng của Order phải hỗ trợ lưu trữ thông tin quy kết tùy chọn:

```json
{
  "suggestionAttribution": {
    "suggestionId": "0192b2d0-7f1d-7abc-8d2c-123456789010",
    "ruleId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
    "modelVersion": null,
    "candidateItemId": "0192b2d0-7f1d-7abc-8d2c-123456789011"
  }
}
```

Order chỉ lưu thông tin quy kết sau khi dòng đơn hàng đã commit thành công; client không thể dùng trường này để ghi đè món, giá hay kiểm tra tính khả dụng.

### 7.2 Ngữ cảnh đặc trưng gợi ý từ Loyalty (Loyalty feature context)

Loyalty phải cung cấp API nội bộ hoặc sự kiện tương đương có đánh phiên bản:

`POST /internal/v1/loyalty/recommendation-feature-context`

Yêu cầu chỉ chứa `customerId` / tham chiếu opaque trong phạm vi tenant, tuyệt đối không có số điện thoại. Phản hồi:

```json
{
  "data": {
    "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789030",
    "opaqueCustomerKey": "custkey_opaque_01J...",
    "eligibility": "ELIGIBLE",
    "consentVersion": "loyalty-consent-v2",
    "featureVersion": "loyalty-feature-v3",
    "generatedAt": "2026-10-13T07:59:00Z"
  }
}
```

Trường `eligibility` nhận các giá trị: `ELIGIBLE`, `INELIGIBLE` hoặc `DELETED`. Recommendation chỉ lưu khóa opaque, trạng thái đủ điều kiện và phiên bản; số điện thoại không bao giờ đi qua ranh giới này.

### 7.3 Chuẩn hóa hình chiếu từ Catalog và Inventory

Các sự kiện nguồn bắt buộc phải có `tenantId`, phạm vi chi nhánh (nếu có), ID thực thể nguồn, phiên bản nguồn, `occurredAt`, giá trị trạng thái/khả dụng, correlation ID và ID sự kiện.

- Sự kiện món/biến thể của Catalog cung cấp định danh chuẩn tắc, tên hiển thị, ảnh chụp giá, trạng thái active và khả năng bán theo chi nhánh.
- Sự kiện tồn kho của Inventory cung cấp trạng thái khả dụng theo chính sách số lượng, phiên bản nguồn và thời điểm ghi nhận; Recommendation không thực hiện tính toán số học tồn kho.
- Sự kiện có phiên bản cũ hơn sẽ bị bỏ qua; sự kiện trùng ID được xử lý lũy nghiệm; khoảng trống phiên bản (gap) sẽ phát cảnh báo yêu cầu checkpoint hoặc rebuild.

## 8. Hợp đồng sự kiện (Event contracts)

### 8.1 Envelope dùng chung (Common envelope)

```json
{
  "eventId": "0192b2d0-7f1d-7abc-8d2c-123456789050",
  "eventType": "SuggestionAccepted.v1",
  "eventVersion": 1,
  "occurredAt": "2026-10-13T08:00:03Z",
  "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789030",
  "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789002",
  "aggregateType": "SUGGESTION",
  "aggregateId": "0192b2d0-7f1d-7abc-8d2c-123456789010",
  "aggregateVersion": 2,
  "correlationId": "0192b2d0-7f1d-7abc-8d2c-123456789014",
  "causationId": "0192b2d0-7f1d-7abc-8d2c-123456789015",
  "producer": "recommendation-service",
  "payload": {}
}
```

### 8.2 Danh mục sự kiện phát ra (Produced event catalogue)

| Tên sự kiện | Dữ liệu tối thiểu trong Payload | Bên tiêu thụ |
|---|---|---|
| `SuggestionShown.v1` | suggestion/item/order/cart version, ID tương tác, phiên bản luật/mô hình, renderedAt | Reporting, Audit |
| `SuggestionAccepted.v1` | Các trường của shown + orderLineId/orderVersionAfterAdd, acceptedAt, correlation bằng chứng | Reporting, Audit |
| `RecommendationRulePublished.v1` | ID/phiên bản luật, tenant, actor, reason, khoảng hiệu lực | Reporting, Audit, xóa cache |
| `RecommendationRuleRetired.v1` | ID/phiên bản luật, actor, reason | Reporting, Audit, xóa cache |
| `RecommendationModelActivated.v1` | tenant/dòng mô hình/phiên bản, các chỉ số, checksum artifact, phiên bản chính sách | Reporting, Audit, làm mới runtime |
| `RecommendationModelRolledBack.v1` | phiên bản mô hình cũ/mới, actor, reason, tham chiếu sự cố | Reporting, Audit, làm mới runtime |
| `RecommendationTrainingRunCompleted.v1` | ID lần chạy, trạng thái, watermark nguồn, ID ảnh chụp, chỉ số/lỗi | Reporting, Audit, vận hành |
| `ColdStartFeatureCandidateCreated.v1` | ID ứng viên, ID món, phiên bản nhà cung cấp/mô hình/prompt, điểm tin cậy, trạng thái duyệt | Chủ chuỗi xét duyệt, Audit |

### 8.3 Quy tắc sự kiện (Event rules)

- Đúng một ID sự kiện duy nhất cho mỗi lần chuyển đổi tương tác / luật / mô hình cục bộ đã commit.
- Bên tiêu thụ khử trùng lặp theo ID sự kiện và phiên bản nguồn / aggregate.
- Việc phát lại sự kiện bắt buộc bảo toàn ID sự kiện và thời điểm `occurredAt` ban đầu.
- Tuyệt đối không đưa số điện thoại, vector đặc trưng thô, nội dung prompt, artifact mô hình hay secret nhà cung cấp vào payload.
- `SuggestionAccepted` chỉ là dữ liệu quy kết phân tích đo lường, không phải nguồn chân lý thanh toán/doanh thu; `OrderPaid.v1` vẫn là sự kiện đơn hàng thanh toán chuẩn mực duy nhất.

## 9. Hợp đồng dữ liệu PostgreSQL (PostgreSQL data contract)

Cơ sở dữ liệu: PostgreSQL, thuộc quyền sở hữu độc quyền của Recommendation Service. Mọi cột mới đều bắt buộc có `COMMENT ON COLUMN` mô tả rõ ràng như bảng dưới đây.

### 9.1 `association_rule_sets`

| Cột | Kiểu | Null | Mô tả / Chú thích |
|---|---|---:|---|
| `id` | UUID | Không | UUID v7 định danh tập luật logic |
| `tenant_id` | UUID | Không | Tenant sở hữu |
| `active_version` | BIGINT | Có | Phiên bản tập luật đã công bố đang được áp dụng |
| `status` | TEXT | Không | `DRAFT`, `ACTIVE`, `RETIRED` |
| `created_by_user_id` | UUID | Không | Người tạo bản nháp |
| `published_by_user_id` | UUID | Có | Chủ chuỗi thực hiện công bố tập luật |
| `published_at` | TIMESTAMPTZ | Có | Thời điểm công bố tập luật active |
| `version` | BIGINT | Không | Phiên bản aggregate lạc quan |
| `created_at` | TIMESTAMPTZ | Không | Thời điểm tạo bản ghi |
| `updated_at` | TIMESTAMPTZ | Không | Thời điểm cập nhật metadata cuối |

### 9.2 `association_rule_versions`

| Cột | Kiểu | Null | Mô tả / Chú thích |
|---|---|---:|---|
| `id` | UUID | Không | UUID v7 định danh phiên bản luật bất biến |
| `tenant_id` | UUID | Không | Tenant sở hữu |
| `logical_rule_key` | TEXT | Không | Định danh ổn định của một liên kết logic |
| `rule_set_id` | UUID | Không | Tập luật cha |
| `version` | BIGINT | Không | Số phiên bản tăng đơn điệu của luật logic |
| `source` | TEXT | Không | `MANUAL` hoặc `ML` |
| `status` | TEXT | Không | `DRAFT`, `PUBLISHED`, `RETIRED` |
| `support_bps` | INTEGER | Có | Chỉ số độ hỗ trợ tính theo basis points (bps) |
| `confidence_bps` | INTEGER | Có | Chỉ số độ tin cậy tính theo basis points (bps) |
| `lift_milli` | INTEGER | Có | Chỉ số lift nhân tỷ lệ 1000; 1000 nghĩa là 1.000 |
| `priority` | INTEGER | Không | Mức độ ưu tiên xếp hạng của tenant |
| `effective_from` | TIMESTAMPTZ | Không | Thời điểm bắt đầu có hiệu lực |
| `effective_to` | TIMESTAMPTZ | Có | Thời điểm hết hiệu lực |
| `quality_evidence_ref` | TEXT | Có | Tham chiếu ảnh chụp/báo cáo của các chỉ số |
| `created_by_user_id` | UUID | Không | Người tạo bản nháp hoặc actor của worker |
| `published_by_user_id` | UUID | Có | Chủ chuỗi thực hiện công bố |
| `publish_reason` | TEXT | Có | Lý do công bố có lưu vết kiểm toán |
| `created_at` | TIMESTAMPTZ | Không | Thời điểm tạo bản ghi |
| `updated_at` | TIMESTAMPTZ | Không | Thời điểm cập nhật metadata |

### 9.3 `rule_antecedents` và `rule_consequents`

| Bảng / Cột | Kiểu | Null | Mô tả / Chú thích |
|---|---|---:|---|
| `rule_antecedents.id` | UUID | Không | ID dòng điều kiện |
| `rule_antecedents.rule_version_id` | UUID | Không | Phiên bản luật cha bất biến |
| `rule_antecedents.position` | INTEGER | Không | Vị trí sắp xếp chuẩn tắc |
| `rule_antecedents.item_id` | UUID | Không | Tham chiếu món trong Catalog |
| `rule_antecedents.variant_id` | UUID | Có | Tham chiếu biến thể tùy chọn |
| `rule_antecedents.modifier_option_id` | UUID | Có | Tham chiếu topping tùy chọn |
| `rule_antecedents.identity_key` | TEXT | Không | Định danh chuẩn tắc dùng trong chữ ký giỏ hàng |
| `rule_consequents.id` | UUID | Không | ID dòng kết quả |
| `rule_consequents.rule_version_id` | UUID | Không | Phiên bản luật cha bất biến |
| `rule_consequents.item_id` | UUID | Không | Món Catalog được gợi ý |
| `rule_consequents.variant_id` | UUID | Có | Biến thể được gợi ý tùy chọn |
| `rule_consequents.identity_key` | TEXT | Không | Định danh chuẩn tắc của ứng viên |

Ràng buộc: Khóa duy nhất phiên bản luật + vị trí/định danh, cấm tự gợi ý chính nó, mọi ID gắn với tenant qua phiên bản cha, lưu mã băm chữ ký giỏ hàng chuẩn tắc để đánh index.

### 9.4 Các bảng hình chiếu (Projection tables)

#### `catalog_item_projections`

| Cột | Kiểu | Null | Mô tả / Chú thích |
|---|---|---:|---|
| `id` | UUID | Không | ID dòng hình chiếu |
| `tenant_id` | UUID | Không | Tenant sở hữu |
| `item_id` | UUID | Không | Định danh món Catalog |
| `variant_id` | UUID | Có | Định danh biến thể Catalog |
| `name_snapshot` | TEXT | Không | Ảnh chụp tên món hiển thị cho gợi ý |
| `price_minor` | BIGINT | Không | Ảnh chụp giá bán tính theo VND |
| `currency` | TEXT | Không | Loại tiền tệ giá bán |
| `item_status` | TEXT | Không | Ảnh chụp trạng thái món trong Catalog |
| `source_version` | BIGINT | Không | Phiên bản nguồn / sự kiện Catalog |
| `source_occurred_at` | TIMESTAMPTZ | Không | Thời điểm phát sinh sự kiện Catalog |
| `observed_at` | TIMESTAMPTZ | Không | Thời điểm hình chiếu nạp dữ liệu |
| `updated_at` | TIMESTAMPTZ | Không | Thời điểm cập nhật hình chiếu cuối |

#### `branch_item_eligibility`

| Cột | Kiểu | Null | Mô tả / Chú thích |
|---|---|---:|---|
| `id` | UUID | Không | ID dòng tính đủ điều kiện |
| `tenant_id` | UUID | Không | Tenant sở hữu |
| `branch_id` | UUID | Không | Chi nhánh áp dụng |
| `item_id` | UUID | Không | Định danh món Catalog |
| `variant_id` | UUID | Có | Định danh biến thể |
| `sellable` | BOOLEAN | Không | Khả năng bán của món tại chi nhánh theo Catalog |
| `catalog_status` | TEXT | Không | Ảnh chụp trạng thái hoạt động/ngừng bán |
| `source_version` | BIGINT | Không | Phiên bản sự kiện khả dụng của Catalog |
| `last_source_event_at` | TIMESTAMPTZ | Không | Thời điểm phát sinh sự kiện nguồn |
| `observed_at` | TIMESTAMPTZ | Không | Thời điểm hình chiếu nạp dữ liệu |
| `updated_at` | TIMESTAMPTZ | Không | Thời điểm cập nhật cuối |

#### `inventory_availability_projections`

| Cột | Kiểu | Null | Mô tả / Chú thích |
|---|---|---:|---|
| `id` | UUID | Không | ID dòng hình chiếu |
| `tenant_id` | UUID | Không | Tenant sở hữu |
| `branch_id` | UUID | Không | Chi nhánh áp dụng |
| `item_id` | UUID | Không | Định danh món Catalog |
| `variant_id` | UUID | Có | Định danh biến thể |
| `available` | BOOLEAN | Không | Quyết định khả dụng tồn kho từ Inventory |
| `availability_policy` | TEXT | Không | Mã định danh chính sách / phiên bản nguồn |
| `source_version` | BIGINT | Không | Phiên bản sự kiện của Inventory |
| `last_source_event_at` | TIMESTAMPTZ | Không | Thời điểm phát sinh sự kiện Inventory |
| `observed_at` | TIMESTAMPTZ | Không | Thời điểm hình chiếu nạp dữ liệu |
| `updated_at` | TIMESTAMPTZ | Không | Thời điểm cập nhật cuối |

#### `projection_checkpoints`

| Cột | Kiểu | Null | Mô tả / Chú thích |
|---|---|---:|---|
| `id` | UUID | Không | ID dòng checkpoint |
| `tenant_id` | UUID | Không | Tenant áp dụng |
| `source_name` | TEXT | Không | Tên nguồn Catalog/Inventory/Order/Loyalty |
| `partition_key` | TEXT | Không | Khóa phân vùng / chi nhánh của nguồn |
| `last_event_id` | UUID | Có | ID sự kiện cuối cùng đã commit |
| `last_source_version` | BIGINT | Có | Phiên bản nguồn cuối cùng đã áp dụng |
| `last_event_at` | TIMESTAMPTZ | Có | Thời điểm của sự kiện nguồn cuối |
| `status` | TEXT | Không | `HEALTHY`, `STALE`, `REBUILDING`, `FAILED` |
| `updated_at` | TIMESTAMPTZ | Không | Thời điểm cập nhật checkpoint |

### 9.5 Các bảng phiên gợi ý và tương tác (Suggestion/session tables)

#### `suggestion_sessions`

| Cột | Kiểu | Null | Mô tả / Chú thích |
|---|---|---:|---|
| `id` | UUID | Không | ID phiên gợi ý / hiển thị |
| `tenant_id` | UUID | Không | Tenant áp dụng |
| `branch_id` | UUID | Không | Chi nhánh áp dụng |
| `order_id` | UUID | Không | Đơn hàng giỏ hàng liên kết |
| `cart_version` | BIGINT | Không | Phiên bản đơn hàng ràng buộc với phiên |
| `customer_key_hash` | TEXT | Có | Mã băm không thể dịch ngược của khóa khách hàng |
| `strategy` | TEXT | Không | `ASSOCIATION`, `PERSONALIZED_ALS`, `ASSOCIATION_FALLBACK` |
| `rule_set_version` | BIGINT | Có | Phiên bản tập luật đã áp dụng |
| `model_version` | TEXT | Có | Phiên bản mô hình ALS đã áp dụng |
| `projection_watermark` | TEXT | Không | Watermark nguồn dữ liệu khả dụng |
| `availability_status` | TEXT | Không | `FRESH`, `STALE`, `PARTIAL` |
| `generated_at` | TIMESTAMPTZ | Không | Thời điểm sinh danh sách gợi ý |
| `expires_at` | TIMESTAMPTZ | Không | Mốc hết hạn thời gian sống (TTL) của phiên |
| `created_at` | TIMESTAMPTZ | Không | Thời điểm tạo bản ghi |

#### `suggestion_candidates`

| Cột | Kiểu | Null | Mô tả / Chú thích |
|---|---|---:|---|
| `id` | UUID | Không | ID dòng ứng viên |
| `tenant_id` | UUID | Không | Tenant áp dụng |
| `suggestion_id` | UUID | Không | Phiên gợi ý cha |
| `item_id` | UUID | Không | Món ứng viên |
| `variant_id` | UUID | Có | Biến thể ứng viên |
| `rank` | INTEGER | Không | Thứ hạng tất định trả về cho UI |
| `score` | NUMERIC(20,8) | Không | Điểm số xếp hạng phi tiền tệ |
| `reason_code` | TEXT | Không | Mã lý do hiển thị an toàn trên giao diện |
| `rule_id` | UUID | Có | Quy kết theo luật |
| `rule_version` | BIGINT | Có | Quy kết theo phiên bản luật |
| `model_version` | TEXT | Có | Quy kết theo phiên bản mô hình |
| `name_snapshot` | TEXT | Không | Ảnh chụp tên món hiển thị |
| `price_minor` | BIGINT | Không | Ảnh chụp giá bán hiển thị |
| `currency` | TEXT | Không | Loại tiền tệ của giá bán |
| `availability_observed_at` | TIMESTAMPTZ | Không | Mốc độ tươi hiển thị trong phản hồi API |
| `created_at` | TIMESTAMPTZ | Không | Thời điểm tạo bản ghi |

#### `suggestion_interactions`

| Cột | Kiểu | Null | Mô tả / Chú thích |
|---|---|---:|---|
| `id` | UUID | Không | ID tương tác / sự kiện |
| `tenant_id` | UUID | Không | Tenant áp dụng |
| `branch_id` | UUID | Không | Chi nhánh áp dụng |
| `suggestion_id` | UUID | Không | Phiên gợi ý liên kết |
| `item_id` | UUID | Không | Món ứng viên |
| `event_type` | TEXT | Không | `SHOWN` hoặc `ACCEPTED` |
| `interaction_key` | TEXT | Không | Định danh lũy nghiệm từ máy khách / nguồn |
| `order_id` | UUID | Không | Đơn hàng giỏ hàng liên kết |
| `order_line_id` | UUID | Có | Bằng chứng dòng đơn hàng thêm thành công cho ACCEPTED |
| `order_version_after_add` | BIGINT | Có | Bằng chứng phiên bản đơn hàng sau khi thêm |
| `rendered_at` | TIMESTAMPTZ | Có | Thời điểm giao diện xác nhận render xong |
| `accepted_at` | TIMESTAMPTZ | Có | Thời điểm chấp thuận |
| `evidence_correlation_id` | UUID | Có | Correlation ID thêm dòng của Order |
| `correlation_id` | UUID | Không | Trace ID |
| `created_at` | TIMESTAMPTZ | Không | Thời điểm lưu trữ |

Khóa duy nhất: `(tenant_id, suggestion_id, item_id, event_type, interaction_key)`.

### 9.6 Các bảng khách hàng, huấn luyện và mô hình ML

#### `customer_feature_contexts`

| Cột | Kiểu | Null | Mô tả / Chú thích |
|---|---|---:|---|
| `id` | UUID | Không | ID dòng ngữ cảnh |
| `tenant_id` | UUID | Không | Tenant áp dụng |
| `opaque_customer_key_hash` | TEXT | Không | Mã băm khóa khách hàng không thể dịch ngược |
| `eligibility` | TEXT | Không | `ELIGIBLE`, `INELIGIBLE`, `DELETED` |
| `consent_version` | TEXT | Có | Phiên bản sự đồng thuận từ Loyalty |
| `feature_version` | TEXT | Không | Phiên bản cấu trúc đặc trưng từ Loyalty |
| `source_version` | BIGINT | Không | Phiên bản ngữ cảnh của Loyalty |
| `generated_at` | TIMESTAMPTZ | Không | Thời điểm tạo dữ liệu phía Loyalty |
| `observed_at` | TIMESTAMPTZ | Không | Thời điểm Recommendation nạp dữ liệu |
| `tombstoned_at` | TIMESTAMPTZ | Có | Thời điểm đánh dấu xóa dữ liệu riêng tư / chặn |
| `updated_at` | TIMESTAMPTZ | Không | Thời điểm cập nhật cuối |

#### `paid_interaction_projections`

| Cột | Kiểu | Null | Mô tả / Chú thích |
|---|---|---:|---|
| `id` | UUID | Không | ID dòng hình chiếu |
| `tenant_id` | UUID | Không | Tenant áp dụng |
| `event_id` | UUID | Không | ID sự kiện nguồn OrderPaid |
| `order_id` | UUID | Không | Đơn hàng đã thanh toán |
| `opaque_customer_key_hash` | TEXT | Có | Mã băm khóa khách hàng đủ điều kiện |
| `item_id` | UUID | Không | Món đã thanh toán |
| `variant_id` | UUID | Có | Biến thể đã thanh toán |
| `quantity` | INTEGER | Không | Số lượng món đã thanh toán |
| `occurred_at` | TIMESTAMPTZ | Không | Thời điểm phát sinh sự kiện OrderPaid |
| `source_version` | BIGINT | Có | Phiên bản aggregate/sự kiện của Order |
| `feature_version` | TEXT | Có | Phiên bản ngữ cảnh đặc trưng |
| `created_at` | TIMESTAMPTZ | Không | Thời điểm lưu bản ghi hình chiếu |

Khóa nguồn duy nhất: `(tenant_id, event_id, order_id, item_id, variant_id)` tương ứng với từng dòng món trong sự kiện.

#### `training_runs`

| Cột | Kiểu | Null | Mô tả / Chú thích |
|---|---|---:|---|
| `id` | UUID | Không | ID lần chạy huấn luyện |
| `tenant_id` | UUID | Không | Tenant huấn luyện |
| `model_family` | TEXT | Không | Dòng mô hình: `ALS` |
| `status` | TEXT | Không | `QUEUED`, `RUNNING`, `VALIDATING`, `ACTIVATED`, `REJECTED`, `FAILED`, `CANCELLED` |
| `source_watermark` | TEXT | Không | Ranh giới dữ liệu đơn đã thanh toán |
| `snapshot_id` | UUID | Có | ID ảnh chụp dữ liệu huấn luyện bất biến |
| `feature_version` | TEXT | Không | Phiên bản schema đặc trưng đầu vào |
| `code_version` | TEXT | Không | Phiên bản mã nguồn huấn luyện |
| `lease_owner` | TEXT | Có | Định danh worker sở hữu khóa thuê |
| `lease_expires_at` | TIMESTAMPTZ | Có | Thời hạn hết hạn khóa thuê |
| `error_code` | TEXT | Có | Mã lỗi an toàn khi thất bại |
| `started_at` | TIMESTAMPTZ | Có | Thời điểm bắt đầu chạy |
| `completed_at` | TIMESTAMPTZ | Có | Thời điểm kết thúc lần chạy |
| `created_at` | TIMESTAMPTZ | Không | Thời điểm tạo bản ghi |
| `updated_at` | TIMESTAMPTZ | Không | Thời điểm cập nhật trạng thái cuối |

#### `training_snapshot_manifests`

| Cột | Kiểu | Null | Mô tả / Chú thích |
|---|---|---:|---|
| `id` | UUID | Không | ID manifest ảnh chụp |
| `tenant_id` | UUID | Không | Tenant áp dụng |
| `training_run_id` | UUID | Không | Lần chạy huấn luyện sở hữu |
| `object_uri` | TEXT | Không | Đường dẫn lưu trữ đối tượng đã mã hóa |
| `checksum` | TEXT | Không | Mã băm toàn vẹn nội dung |
| `row_count` | BIGINT | Không | Số dòng dữ liệu tương tác đầu vào |
| `source_watermark` | TEXT | Không | Ranh giới dữ liệu đầu vào |
| `schema_version` | TEXT | Không | Phiên bản schema của ảnh chụp |
| `retention_until` | TIMESTAMPTZ | Không | Mốc thời gian lưu trữ tối thiểu |
| `created_at` | TIMESTAMPTZ | Không | Thời điểm tạo ảnh chụp |

#### `model_registry`

| Cột | Kiểu | Null | Mô tả / Chú thích |
|---|---|---:|---|
| `id` | UUID | Không | ID đăng ký mô hình |
| `tenant_id` | UUID | Không | Tenant sở hữu |
| `model_family` | TEXT | Không | Dòng mô hình: `ALS` |
| `model_version` | TEXT | Không | Phiên bản mô hình bất biến |
| `status` | TEXT | Không | `CANDIDATE`, `VALIDATING`, `ACTIVE`, `RETIRED`, `REJECTED`, `FAILED` |
| `artifact_uri` | TEXT | Không | Đường dẫn artifact đã mã hóa |
| `artifact_checksum` | TEXT | Không | Mã băm toàn vẹn của artifact |
| `training_snapshot_id` | UUID | Không | ID ảnh chụp dữ liệu nguồn |
| `feature_version` | TEXT | Không | Phiên bản schema đặc trưng |
| `code_version` | TEXT | Không | Phiên bản mã nguồn huấn luyện |
| `quality_policy_version` | TEXT | Không | Phiên bản chính sách thẩm định áp dụng |
| `registry_version` | BIGINT | Không | Phiên bản con trỏ kiểm soát CAS |
| `activated_at` | TIMESTAMPTZ | Có | Thời điểm kích hoạt active |
| `retired_at` | TIMESTAMPTZ | Có | Thời điểm ngừng áp dụng |
| `created_at` | TIMESTAMPTZ | Không | Thời điểm tạo bản ghi |
| `updated_at` | TIMESTAMPTZ | Không | Thời điểm cập nhật cuối |

#### `model_metrics`

| Cột | Kiểu | Null | Mô tả / Chú thích |
|---|---|---:|---|
| `id` | UUID | Không | ID dòng chỉ số |
| `tenant_id` | UUID | Không | Tenant áp dụng |
| `model_id` | UUID | Không | Tham chiếu mô hình trong model registry |
| `metric_name` | TEXT | Không | Tên định danh chỉ số đánh giá |
| `metric_value` | NUMERIC(20,8) | Không | Giá trị đánh giá có độ chính xác cố định |
| `threshold_value` | NUMERIC(20,8) | Không | Ngưỡng bắt buộc (hard) hoặc khuyến nghị (soft) |
| `gate_status` | TEXT | Không | `PASS` hoặc `FAIL` |
| `evaluation_window` | JSONB | Không | Định nghĩa tập chia / thời gian đánh giá (không chứa PII) |
| `created_at` | TIMESTAMPTZ | Không | Thời điểm thực hiện đánh giá |

### 9.7 Các bảng khởi động nguội P2 (P2 cold-start tables)

#### `cold_start_jobs`

| Cột | Kiểu | Null | Mô tả / Chú thích |
|---|---|---:|---|
| `id` | UUID | Không | ID tác vụ |
| `tenant_id` | UUID | Không | Tenant áp dụng |
| `item_id` | UUID | Không | Tham chiếu món trong Catalog |
| `source_event_id` | UUID | Không | Sự kiện kích hoạt từ Catalog |
| `status` | TEXT | Không | `QUEUED`, `RUNNING`, `PENDING_REVIEW`, `COMPLETED`, `FAILED`, `DLQ` |
| `prompt_version` | TEXT | Không | Phiên bản mẫu cấu hình / prompt |
| `provider_model` | TEXT | Không | Định danh mô hình DeepSeek |
| `attempt_count` | INTEGER | Không | Số lần đã thử gọi nhà cung cấp |
| `last_error_code` | TEXT | Có | Mã lỗi an toàn từ nhà cung cấp / thẩm định |
| `lease_expires_at` | TIMESTAMPTZ | Có | Thời hạn hết hạn khóa thuê worker |
| `created_at` | TIMESTAMPTZ | Không | Thời điểm tạo tác vụ |
| `updated_at` | TIMESTAMPTZ | Không | Thời điểm cập nhật cuối |

#### `cold_start_feature_candidates`

| Cột | Kiểu | Null | Mô tả / Chú thích |
|---|---|---:|---|
| `id` | UUID | Không | ID đặc trưng ứng viên |
| `tenant_id` | UUID | Không | Tenant áp dụng |
| `job_id` | UUID | Không | Tác vụ cold-start nguồn |
| `item_id` | UUID | Không | Tham chiếu món trong Catalog |
| `feature_payload` | JSONB | Không | Kết quả đặc trưng có cấu trúc đã làm sạch |
| `confidence` | NUMERIC(10,8) | Không | Điểm tin cậy sau khi thẩm định |
| `provider_model` | TEXT | Không | Phiên bản mô hình nhà cung cấp |
| `prompt_version` | TEXT | Không | Phiên bản prompt |
| `provenance_ref` | TEXT | Không | Tham chiếu bằng chứng đã làm sạch |
| `review_status` | TEXT | Không | `PENDING_REVIEW`, `APPROVED`, `REJECTED` |
| `reviewed_by_user_id` | UUID | Có | Người thực hiện xét duyệt |
| `reviewed_at` | TIMESTAMPTZ | Có | Thời điểm xét duyệt |
| `created_at` | TIMESTAMPTZ | Không | Thời điểm tạo bản ghi |

Tuyệt đối không lưu phản hồi thô từ nhà cung cấp, prompt chứa thông tin nhạy cảm hay API secret.

### 9.8 Các bảng hỗ trợ độ tin cậy (Reliability tables)

Các bảng `outbox_messages`, `inbox_events`, `idempotency_records` và `audit_references` tuân thủ quy ước chuẩn của nền tảng POS Platform kèm các cột đặc thù của Recommendation:

| Bảng | Các cột bắt buộc và mô tả chi tiết |
|---|---|
| `outbox_messages` | `id` — ID sự kiện; `tenant_id` — quyền sở hữu; `aggregate_type/id` — tài nguyên liên quan; `aggregate_version` — thứ tự phiên bản; `event_type` — tên schema sự kiện; `payload` — envelope bất biến; `published_at` — thời điểm gửi thành công; `attempt_count` — số lần thử lại; `next_attempt_at` — lịch trình backoff |
| `inbox_events` | `id` — ID dòng inbox; `tenant_id` — phạm vi; `event_id` — khóa khử trùng lặp nguồn; `source_type` — dịch vụ phát; `source_version` — phiên bản tăng đơn điệu; `status` — đã áp dụng/bỏ qua/thất bại; `processed_at` — thời điểm commit; `error_code` — mã lỗi an toàn |
| `idempotency_records` | `id` — ID bản ghi; `tenant_id` — quyền sở hữu; `operation` — tên lệnh; `idempotency_key` — khóa thử lại; `request_hash` — mã băm chuẩn tắc; `response_status` — mã HTTP đã lưu; `response_body` — kết quả an toàn; `resource_id` — ID tài nguyên tạo ra; `expires_at` — mốc hết hạn lưu trữ |
| `audit_references` | `id` — ID kiểm toán; `tenant_id` — quyền sở hữu; `branch_id` — phạm vi chi nhánh; `actor_user_id` — người thực hiện; `action` — tên hành động; `resource_type/id` — đối tượng tác động; `reason` — lý do công bố/rollback/xét duyệt bắt buộc; `before_summary/after_summary` — trạng thái tóm tắt đã che mờ; `correlation_id` — trace ID; `created_at` — thời điểm kiểm toán bất biến |

## 10. Ràng buộc, chỉ mục, lưu trữ dữ liệu và di chuyển dữ liệu (Constraints, retention and migration)

### 10.1 Ràng buộc và chỉ mục bắt buộc

- Mọi bảng đều có quyền sở hữu tenant; các bảng theo chi nhánh có chỉ mục kép `(tenant_id, branch_id)`.
- Duy nhất một phiên bản tập luật active cho mỗi tenant; các phiên bản luật đã công bố là bất biến.
- Khóa nguồn tương tác duy nhất; sự kiện trùng lặp là thao tác no-op an toàn.
- Duy nhất một mô hình ALS active cho mỗi cặp `(tenant_id, model_family)` thông qua chỉ mục unique partial index.
- Khóa thuê lần chạy huấn luyện là duy nhất theo bộ ba `(tenant_id, model_family, source_watermark)`.
- Phiên bản nguồn và checkpoint của hình chiếu phải tăng đơn điệu; sự kiện cũ hơn không được phép ghi đè trạng thái mới hơn.
- Giá món ứng viên là ảnh chụp số nguyên `BIGINT`, tuyệt đối không tính toán lại từ Catalog trực tiếp trong quá trình replay phân tích.
- URI artifact và mã băm checksum là bắt buộc khi kích hoạt mô hình; sai lệch checksum sẽ chặn việc kích hoạt.
- Khóa khách hàng luôn được lưu trữ dưới dạng mã băm / tham chiếu opaque; không tồn tại cột số điện thoại trong toàn bộ cơ sở dữ liệu.

### 10.2 Chính sách lưu trữ và phân vùng dữ liệu (Retention and partitioning)

- Phiên/tương tác gợi ý, hình chiếu đơn đã thanh toán, outbox/inbox và audit tuân thủ chính sách nền tảng; bằng chứng kiểm toán tối thiểu là 7 năm.
- Ảnh chụp huấn luyện và artifact mô hình được lưu trữ theo chính sách tái lập kết quả mô hình; các mô hình active và mô hình đã thẩm định trước đó phải duy trì khả năng rollback trong cửa sổ thời gian cấu hình.
- Các ứng viên áp dụng phân vùng RANGE theo tháng: bảng tương tác khối lượng lớn `suggestion_interactions`, `paid_interaction_projections`, `outbox_messages`, nhật ký audit và lịch sử lần chạy huấn luyện.
- Giai đoạn MVP không phân vùng các bảng metadata luật và mô hình nhỏ.
- Khi nhận sự kiện xóa dữ liệu riêng tư / tombstone, hệ thống phải loại bỏ khỏi các lần huấn luyện tương lai và xóa/hết hạn ngữ cảnh đặc trưng theo chính sách của Loyalty; hồ sơ pháp lý/audit chỉ giữ lại các bằng chứng đã được khử độc danh tính theo quy định.

### 10.3 Thứ tự thực hiện migration

1. Tạo các bảng hỗ trợ độ tin cậy và các ràng buộc dùng chung về tenant / audit / outbox.
2. Tạo các bảng hình chiếu Catalog/Inventory và bảng checkpoint tương ứng.
3. Tạo các bảng tập luật / phiên bản luật / điều kiện / kết quả và các chỉ mục phiên bản active.
4. Tạo các bảng phiên gợi ý / ứng viên / tương tác kèm các khóa tương tác duy nhất.
5. Tạo các bảng đặc trưng khách hàng, đơn đã thanh toán, ảnh chụp/lần chạy huấn luyện, model registry và chỉ số mô hình.
6. Tạo các bảng khởi động nguội P2 và metadata tích hợp lưu trữ đối tượng đã mã hóa.
7. Seed dữ liệu cấu hình mặc định về top-K / TTL / chính sách chất lượng có đánh phiên bản.
8. Nạp bù dữ liệu (backfill) hình chiếu từ các sự kiện nguồn / ảnh chụp được lưu trữ; tuyệt đối không tự bịa đặt các sự kiện gợi ý lịch sử.

### 10.4 Các kịch bản Migration Flyway (Flyway Migration Scripts)

#### 10.4.1 `V1__init_recommendation_core_and_projections.sql`
```sql
CREATE TABLE catalog_item_projections (
    tenant_id UUID NOT NULL,
    item_id UUID NOT NULL,
    variant_id UUID,
    item_name TEXT NOT NULL,
    category_id UUID NOT NULL,
    price_minor BIGINT NOT NULL,
    currency TEXT NOT NULL DEFAULT 'VND',
    status TEXT NOT NULL CHECK (status IN ('AVAILABLE', 'HIDDEN', 'DELETED')),
    version BIGINT NOT NULL,
    source_watermark TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, item_id, variant_id)
);

CREATE INDEX idx_rec_cat_proj ON catalog_item_projections(tenant_id, status);

CREATE TABLE inventory_availability_projections (
    tenant_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    item_id UUID NOT NULL,
    variant_id UUID,
    is_available BOOLEAN NOT NULL DEFAULT TRUE,
    source_watermark TEXT NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, branch_id, item_id, variant_id)
);

CREATE INDEX idx_rec_inv_avail ON inventory_availability_projections(tenant_id, branch_id, is_available);
```

#### 10.4.2 `V2__init_association_rules.sql`
```sql
CREATE TABLE association_rule_sets (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    name TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE')),
    active_version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_rec_ruleset_active UNIQUE (tenant_id, status) WHERE status = 'ACTIVE'
);

CREATE TABLE association_rule_versions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    rule_set_id UUID NOT NULL REFERENCES association_rule_sets(id) ON DELETE CASCADE,
    logical_rule_key TEXT NOT NULL,
    version INTEGER NOT NULL,
    source TEXT NOT NULL CHECK (source IN ('MANUAL', 'MINED_FP_GROWTH')),
    status TEXT NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED', 'RETIRED')),
    priority INTEGER NOT NULL DEFAULT 100,
    support_bps INTEGER,
    confidence_bps INTEGER,
    lift_milli INTEGER,
    effective_from TIMESTAMPTZ NOT NULL,
    effective_to TIMESTAMPTZ,
    note TEXT,
    created_by_user_id UUID,
    published_by_user_id UUID,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_rec_rule_version UNIQUE (tenant_id, logical_rule_key, version)
);

CREATE TABLE rule_antecedents (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    rule_version_id UUID NOT NULL REFERENCES association_rule_versions(id) ON DELETE CASCADE,
    item_id UUID NOT NULL,
    variant_id UUID,
    modifier_option_id UUID
);

CREATE TABLE rule_consequents (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    rule_version_id UUID NOT NULL REFERENCES association_rule_versions(id) ON DELETE CASCADE,
    item_id UUID NOT NULL,
    variant_id UUID
);

CREATE INDEX idx_rec_antecedents ON rule_antecedents(tenant_id, item_id);
```

#### 10.4.3 `V3__init_suggestion_sessions_and_interactions.sql`
```sql
CREATE TABLE suggestion_sessions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    order_id UUID NOT NULL,
    cart_version BIGINT NOT NULL,
    strategy TEXT NOT NULL CHECK (strategy IN ('ASSOCIATION', 'PERSONALIZED_ALS', 'ASSOCIATION_FALLBACK')),
    fallback_reason TEXT,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_rec_session_order ON suggestion_sessions(tenant_id, order_id, cart_version);

CREATE TABLE suggestion_candidates (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    suggestion_id UUID NOT NULL REFERENCES suggestion_sessions(id) ON DELETE CASCADE,
    item_id UUID NOT NULL,
    variant_id UUID,
    item_name TEXT NOT NULL,
    price_minor BIGINT NOT NULL,
    currency TEXT NOT NULL DEFAULT 'VND',
    rank INTEGER NOT NULL,
    score NUMERIC(10,4) NOT NULL,
    reason_code TEXT NOT NULL,
    rule_version_id UUID,
    model_version TEXT,
    CONSTRAINT uq_rec_candidate_rank UNIQUE (tenant_id, suggestion_id, rank)
);

CREATE TABLE suggestion_interactions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    branch_id UUID NOT NULL,
    suggestion_id UUID NOT NULL REFERENCES suggestion_sessions(id) ON DELETE CASCADE,
    item_id UUID NOT NULL,
    event_type TEXT NOT NULL CHECK (event_type IN ('SHOWN', 'ACCEPTED')),
    interaction_key TEXT NOT NULL,
    order_id UUID NOT NULL,
    order_line_id UUID,
    order_version_after_add BIGINT,
    rendered_at TIMESTAMPTZ,
    accepted_at TIMESTAMPTZ,
    correlation_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_rec_interaction UNIQUE (tenant_id, suggestion_id, item_id, event_type, interaction_key)
);

CREATE INDEX idx_rec_interact_created ON suggestion_interactions(tenant_id, event_type, created_at);
```

#### 10.4.4 `V4__init_ml_training_and_models.sql`
```sql
CREATE TABLE customer_feature_contexts (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    opaque_customer_key_hash TEXT NOT NULL,
    eligibility TEXT NOT NULL CHECK (eligibility IN ('ELIGIBLE', 'INELIGIBLE', 'DELETED')),
    consent_version TEXT,
    feature_version TEXT NOT NULL,
    source_version BIGINT NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    observed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    tombstoned_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_rec_cust_feat UNIQUE (tenant_id, opaque_customer_key_hash)
);

CREATE TABLE paid_interaction_projections (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    event_id UUID NOT NULL,
    order_id UUID NOT NULL,
    opaque_customer_key_hash TEXT,
    item_id UUID NOT NULL,
    variant_id UUID,
    quantity INTEGER NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    source_version BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_rec_paid_item UNIQUE (tenant_id, event_id, order_id, item_id, variant_id)
);

CREATE TABLE model_registry (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    model_family TEXT NOT NULL CHECK (model_family IN ('ALS')),
    model_version TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('CANDIDATE', 'VALIDATING', 'ACTIVE', 'RETIRED', 'REJECTED', 'FAILED')),
    artifact_uri TEXT NOT NULL,
    artifact_checksum TEXT NOT NULL,
    training_snapshot_id UUID NOT NULL,
    feature_version TEXT NOT NULL,
    code_version TEXT NOT NULL,
    quality_policy_version TEXT NOT NULL,
    registry_version BIGINT NOT NULL,
    activated_at TIMESTAMPTZ,
    retired_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_rec_model_ver UNIQUE (tenant_id, model_version)
);

CREATE UNIQUE INDEX uq_rec_active_model ON model_registry(tenant_id, model_family) WHERE status = 'ACTIVE';

CREATE TABLE model_metrics (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    model_id UUID NOT NULL REFERENCES model_registry(id) ON DELETE CASCADE,
    metric_name TEXT NOT NULL,
    metric_value NUMERIC(20,8) NOT NULL,
    threshold_value NUMERIC(20,8) NOT NULL,
    gate_status TEXT NOT NULL CHECK (gate_status IN ('PASS', 'FAIL')),
    evaluation_window JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

#### 10.4.5 `V5__init_cold_start_and_reliability.sql`
```sql
CREATE TABLE cold_start_features (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    job_id UUID NOT NULL,
    item_id UUID NOT NULL,
    feature_payload JSONB NOT NULL,
    confidence NUMERIC(10,8) NOT NULL,
    provider_model TEXT NOT NULL,
    prompt_version TEXT NOT NULL,
    provenance_ref TEXT NOT NULL,
    review_status TEXT NOT NULL CHECK (review_status IN ('PENDING_REVIEW', 'APPROVED', 'REJECTED')),
    reviewed_by_user_id UUID,
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_rec_cold_item UNIQUE (tenant_id, item_id)
);

CREATE TABLE recommendation_outbox_events (
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

CREATE INDEX idx_rec_outbox_pending ON recommendation_outbox_events(status, created_at) WHERE status = 'PENDING';

CREATE TABLE recommendation_inbox_dedup (
    event_id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    event_type TEXT NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

## 11. Ma trận kiểm thử hợp đồng (Contract test matrix)

| Khu vực kiểm thử | Các kịch bản fixture bắt buộc |
|---|---|
| API Gợi ý | Khớp chính xác phiên bản giỏ hàng; giỏ hàng rỗng; giới hạn top-K; phá vỡ thế hòa tất định; không có ứng viên hợp lệ |
| Tính đủ điều kiện | Món ngừng bán, sai chi nhánh, đã có trong giỏ, hết hàng tồn kho, hình chiếu quá hạn, sự kiện nguồn trùng/sai thứ tự |
| Hiển thị / Chấp thuận | Chỉ gửi shown sau render; có bằng chứng Order thêm dòng thành công; thêm dòng thất bại không sinh accept; replay/hết hạn phiên |
| Quản trị luật | Manager tạo bản nháp; Owner công bố/ngừng áp dụng; header If-Match đã cũ; tính bất biến của luật đã công bố; cấm tự gợi ý |
| Tích hợp Loyalty | Khóa opaque; không đủ điều kiện / đã xóa / phiên bản đồng thuận; sai tenant; không có SĐT trong log và sự kiện |
| Mô hình ALS | Dữ liệu đầu vào chỉ từ đơn đã thanh toán; khách hàng thưa thớt fallback về P0; cách ly tenant; cổng chất lượng; con trỏ CAS; rollback |
| Huấn luyện | Trùng khóa thuê tác vụ; sai lệch checksum ảnh chụp; worker gặp sự cố và tự phục hồi; xử lý qua DLQ; bảo toàn mô hình cũ |
| Các hình chiếu | Thứ tự checkpoint; tái dựng dữ liệu; khoảng trống phiên bản / quá hạn TTL; sự kiện nguồn trùng lặp |
| Tích hợp DeepSeek | Dữ liệu đã làm sạch; lỗi schema/an toàn; timeout / giới hạn tần suất; xét duyệt của con người; không làm đổi Catalog |
| Bảo mật | Chống tấn công IDOR xuyên tenant đối với đơn hàng/luật/khách hàng/mô hình; ma trận phân quyền; từ chối workload identity sai |
| Hiệu năng | Cam kết độ trễ p95 gợi ý; tập ứng viên giới hạn; nạp cache/mô hình; cô lập tài nguyên worker không ảnh hưởng API |
| Các sự kiện | Tương thích envelope/schema; tính nguyên tử của Outbox; consumer khử trùng lặp; phát lại bảo toàn ID sự kiện gốc |

## 12. Điều chỉnh bổ sung đối với các hợp đồng parent (Parent contract amendments)

### POS Platform

- Mở rộng bảng endpoint của Recommendation với `/shown`, vòng đời luật, kiểm tra sức khỏe mô hình và hợp đồng worker nội bộ.
- Bổ sung các sự kiện phát sinh: `RecommendationRulePublished.v1`, `RecommendationRuleRetired.v1`, `RecommendationModelActivated.v1`, `RecommendationModelRolledBack.v1`, `RecommendationTrainingRunCompleted.v1` và `ColdStartFeatureCandidateCreated.v1`.
- Mở rộng phần tóm tắt `association_rules` / `suggestion_events` để tham chiếu đến schema chi tiết do Recommendation sở hữu; tài liệu parent không phải là nguồn chân lý thứ hai.

### Order

- Bổ sung hợp đồng đọc nội bộ `recommendation-context` kèm phiên bản đơn hàng / giỏ hàng.
- Bổ sung trường tùy chọn `suggestionAttribution` vào lệnh thêm dòng đơn hàng và chỉ lưu vào sự kiện đơn hàng sau khi dòng đã commit thành công.
- Bảo đảm Recommendation không thể lợi dụng trường quy kết này để bỏ qua các bước kiểm tra của Catalog, Inventory hay Order.

### Catalog và Inventory

- Đưa thêm phiên bản nguồn, phạm vi chi nhánh, thời điểm quan sát/phát sinh và ID sự kiện vào các sự kiện tồn kho và khả dụng.
- Định nghĩa rõ hành vi phát lại / checkpoint cho các hình chiếu của Recommendation.

### Loyalty

- Bổ sung API tra cứu / sự kiện ngữ cảnh đặc trưng gợi ý dạng opaque kèm tính đủ điều kiện, phiên bản đồng thuận, phiên bản đặc trưng, ngữ nghĩa tombstone và phạm vi tenant.
- Tuyệt đối không bao giờ để lộ số điện thoại plaintext hay dữ liệu tra cứu có thể dịch ngược sang cho Recommendation.

## 13. Các artifact kỹ thuật bắt buộc trước khi lập trình

Trước khi bắt đầu viết code:

- `openapi.yaml` cho các API gợi ý và quản trị luật công khai.
- `openapi-internal.yaml` cho các API nội bộ: ngữ cảnh Order, huấn luyện, mô hình, tái dựng hình chiếu và worker cold-start.
- `asyncapi.yaml` và các JSON Schema cho toàn bộ sự kiện tiêu thụ và phát ra.
- Kịch bản migration PostgreSQL / Flyway kèm đầy đủ `COMMENT ON COLUMN` cho từng cột.
- Các fixture kiểm thử hợp đồng cho phía producer (Order/Catalog/Inventory/Loyalty) và consumer (Reporting/Audit).
- Fixture kiểm thử chính sách đánh giá ML và chuyển đổi trạng thái model registry.

## 14. Cổng phê duyệt hợp đồng (Contract approval gate)

- **Trạng thái hiện tại:** Approved with warnings (Đã phê duyệt với các cảnh báo)
- **Yêu cầu trước khi hoàn tất:** Người dùng xác nhận cấu trúc API/sự kiện, vòng đời luật, các điều chỉnh Order/Loyalty, vòng đời mô hình và các ràng buộc DB.
- **Điều kiện tiên quyết triển khai:** Không lập trình controller/DTO/trainer/migration trực tiếp từ văn bản mô tả; bắt buộc sinh các artifact hợp đồng máy đọc và các fixture kiểm thử trước khi viết mã.
