Started at: 2026/10/13 00:00:00
Finished at: 2026/10/13 00:00:00
Total time: 0 minutes
---

# Module 10 — Báo Cáo & Dashboard: Hợp Đồng API & Dữ Liệu (Contracts)

**Ngày:** 2026-10-13  
**Trạng thái:** Đã phê duyệt kèm cảnh báo — người dùng đã phê duyệt  
**Tầm nhìn:** [Reporting Vision](file:///d:/vibe-code/pos/docs/designs-vi/reporting/2026-10-13-vision.md)  
**Yêu cầu:** [Reporting Requirements](file:///d:/vibe-code/pos/docs/designs-vi/reporting/2026-10-13-requirements.md)  
**Kiến trúc:** [Reporting Architecture](file:///d:/vibe-code/pos/docs/designs-vi/reporting/2026-10-13-architecture.md)  
**Biểu đồ tuần tự:** [Reporting Sequences](file:///d:/vibe-code/pos/docs/designs-vi/reporting/2026-10-13-sequence-diagrams.adoc)

Tài liệu này là nguồn chuẩn để sinh các đặc tả OpenAPI 3.1 công khai/nội bộ, AsyncAPI/JSON Schema, migration PostgreSQL/Flyway, các bộ fixture chỉ số (metric fixtures) và các kiểu dữ liệu client Angular được tạo tự động. Reporting sở hữu các bảng read model nhưng không sở hữu các dữ kiện nghiệp vụ nguồn (source business facts).

## 1. Quy Ước Hợp Đồng (Contract Conventions)

### 1.1 Mã hóa và định danh (Encoding and Identity)

- REST công khai nằm dưới `/api/v1`; REST nội bộ nằm dưới `/internal/v1`.
- Phân phối EventBridge/SQS theo cơ chế at-least-once; phong bì sự kiện sử dụng `eventType` + `eventVersion`.
- ID sử dụng chuỗi UUID v7; timestamp định dạng ISO-8601 UTC.
- `businessDate` là ngày cục bộ theo chính sách múi giờ IANA do server phân giải.
- MVP chỉ hỗ trợ `VND`; tiền tệ là số nguyên có dấu `int64` ở đơn vị nhỏ nhất (minor units) + `currency`.
- Tỷ lệ/phần trăm là chuỗi số thập phân (decimal strings); không dùng số thực dấu phẩy động (binary floating point) cho KPI.
- Thay đổi gây phá vỡ tương thích (breaking change) API/event phải tăng major version; các trường bổ sung phải tương thích ngược (backward-compatible).

### 1.2 Headers

| Header | Công khai | Nội bộ | Ý nghĩa |
|---|---:|---:|---|
| `Authorization` | bắt buộc | bắt buộc | Cognito JWT hoặc workload credential/mTLS |
| `X-Correlation-Id` | khuyến nghị | bắt buộc | Truy vết request/event/truy vấn |
| `X-Causation-Id` | không | khuyến nghị | Tham chiếu sự kiện/lệnh đầu vào upstream |
| `Idempotency-Key` | không áp dụng cho GET | bắt buộc cho lệnh rebuild | Phạm vi Tenant + thao tác + tác nhân/workload |
| `If-None-Match` | tùy chọn | không | Phản hồi điều kiện an toàn sau khi đã phân quyền |
| `Cache-Control` | server kiểm soát | server kiểm soát | Dữ liệu báo cáo công khai không được browser cache vượt chính sách |

Client tuyệt đối không được gửi `tenantId`, vai trò, phiên bản membership hoặc quyền sở hữu chi nhánh với tư cách là thẩm quyền tự quyết. `branchIds` chỉ là bộ lọc yêu cầu; server/Identity là nơi quyết định phạm vi (scope) thực tế.

### 1.3 Phong Bì Phản Hồi (Envelopes)

```json
{
  "data": {},
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J...",
    "nextCursor": null
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

Thông điệp lỗi không được tiết lộ tenant/chi nhánh nằm ngoài phạm vi, thông tin định danh nhân viên/khách hàng/thanh toán, câu lệnh SQL, tên bảng, sự kiện thô hoặc stack trace nội bộ.

### 1.4 Phân Trang và Sắp Xếp (Pagination & Sorting)

- Các endpoint danh sách sử dụng phân trang bằng con trỏ (cursor pagination), mặc định `20`, tối đa `100`.
- Cursor mã hóa bộ tuple sắp xếp ổn định + hash phạm vi/bộ lọc + watermark projection; server sẽ từ chối cursor nếu sử dụng bộ lọc/phạm vi khác.
- Tiêu chí phá vỡ hòa điểm ổn định (stable tie-break) luôn kết thúc bằng UUID/khóa mờ ASC.
- Chuỗi báo cáo theo ngày/giờ có thể trả về mảng có chặn trên không cần cursor nếu giới hạn khoảng thời gian/số dòng đã được kiểm soát.

### 1.5 Phân Loại Mã Lỗi (Error Taxonomy)

| HTTP | Mã lỗi | Ý nghĩa | Retry |
|---:|---|---|---:|
| 200 | — | Phản hồi đọc thành công, bao gồm cả `NO_DATA` | không |
| 304 | — | Phản hồi điều kiện hợp lệ không thay đổi nội dung | không |
| 401 | `unauthorized` | JWT/workload credential không hợp lệ | làm mới/sửa token auth |
| 403 | `forbidden` | Vai trò/quyền hạn không được phép | không |
| 409 | `scope_not_allowed` | Chi nhánh yêu cầu nằm ngoài phạm vi được phân giải | không |
| 409 | `report_not_ready` | Projection chưa hoàn tất cho nguồn bắt buộc | retry có giới hạn |
| 409 | `rebuild_conflict` | Cùng phạm vi/phiên bản đang có tác vụ rebuild chạy | kiểm tra thao tác |
| 422 | `invalid_report_filter` | Ngày/khoảng thời gian/chiều/giới hạn không hợp lệ | không |
| 422 | `unsupported_currency` | Dữ liệu tổng hợp tiền tệ lẫn lộn/khác VND trong MVP | không |
| 429 | `rate_limited` | Vượt giới hạn người dùng/tenant/báo cáo | Retry-After |
| 503 | `reporting_unavailable` | Read model/cache không khả dụng | retry có giới hạn |

Các trạng thái `NO_DATA`, `STALE`, `PARTIAL` và `UNAVAILABLE` là các trạng thái domain an toàn trong `dataQuality`; trạng thái không có dữ liệu không được tự ý gán thành giá trị 0.

## 2. Hợp Đồng Phân Quyền và Phạm Vi (Authorization & Scope)

### 2.1 Tu chính danh mục quyền hạn (Permission Catalogue)

Identity cần bổ sung:

| Quyền | Chủ cửa hàng (Owner) | Quản lý (Manager) | Thu ngân | Bếp | Phạm vi áp dụng |
|---|---:|---:|---:|---:|---|
| `report:read` | có | các chi nhánh được phân công | không | không | Tenant hoặc chi nhánh được giao |
| `reporting:operate` | thao tác hạn chế | không | không | không | Chỉ dùng cho tác vụ rebuild/replay nội bộ |

Identity luôn là thẩm quyền trực tuyến. Reporting gọi `POST /internal/v1/authorization-decisions` của service gốc cho mọi request công khai được bảo vệ.

### 2.2 Phạm Vi Đã Phân Giải (Resolved Scope)

```json
{
  "scopeType": "BRANCH",
  "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789001",
  "branchIds": ["0192b2d0-7f1d-7abc-8d2c-123456789002"],
  "role": "MANAGER",
  "permission": "report:read",
  "membershipVersion": 12,
  "authorizationVersion": 7,
  "resolvedAt": "2026-10-13T08:00:00Z",
  "expiresAt": "2026-10-13T08:05:00Z"
}
```

Quy tắc:

- Phạm vi `TENANT` chỉ dành riêng cho Chủ cửa hàng (Owner) và phải tường minh; cấm dùng ID chi nhánh ký tự đại diện (wildcard).
- Tập hợp chi nhánh của Quản lý (Manager) phải là tập con của quan hệ thành viên hiện hành.
- Một request báo cáo công khai không thể mở rộng phạm vi hiện có thông qua tham số query, cursor hoặc drill-down ID.
- Cache key bắt buộc chứa `authorizationVersion` và hash phạm vi; kiểm tra phân quyền luôn diễn ra trước khi tra cứu cache.
- Repository của Reporting luôn áp dụng điều kiện tenant/chi nhánh ngay cả sau khi Identity đã phê duyệt.

## 3. Các Schema Báo Cáo Chung (Common Report Schemas)

### 3.1 Kỳ báo cáo đã chuẩn hóa (Normalized Period)

```json
{
  "from": "2026-10-01",
  "to": "2026-10-13",
  "timezone": "Asia/Ho_Chi_Minh",
  "utcFrom": "2026-09-30T17:00:00Z",
  "utcToExclusive": "2026-10-13T17:00:00Z",
  "businessDateMode": "BRANCH_LOCAL"
}
```

Truy vấn một chi nhánh sử dụng múi giờ cục bộ của chi nhánh đó. Truy vấn đa chi nhánh sử dụng múi giờ báo cáo của tenant. Tuyệt đối không dùng múi giờ trình duyệt làm thẩm quyền.

### 3.2 `DataQualityEnvelope`

| Trường | Kiểu dữ liệu | Bắt buộc | Quy tắc |
|---|---|---:|---|
| `status` | enum | có | `FRESH`, `STALE`, `PARTIAL`, `UNAVAILABLE`, `NO_DATA` |
| `freshnessLagSeconds` | int64 | có | Không âm; tính toán từ watermark nguồn |
| `completenessStatus` | enum | có | `COMPLETE`, `PARTIAL`, `MISSING` |
| `watermark` | string | có | Checkpoint nguồn mờ |
| `watermarkVersion` | int64 | có | Đơn điệu tăng trong nguồn/phạm vi/projection |
| `lastUpdatedAt` | timestamp | có | Thời điểm UTC cập nhật projection |
| `projectionVersion` | int64 | có | Phiên bản projection cụ thể hóa hiện hành |
| `missingSources` | string[] | có | Chỉ chứa tên nguồn an toàn |
| `calculationVersion` | string | có | Từ điển chỉ số / phiên bản tính toán |
| `asOfWatermark` | string | có | Checkpoint của chế độ xem điều chỉnh hiện hành |

Quy tắc trạng thái:

- `FRESH`: độ trễ ≤60 giây và các nguồn bắt buộc hoàn tất đầy đủ.
- `STALE`: độ trễ >60 giây trong khi read model vẫn sẵn sàng; >300 giây sẽ kích hoạt cảnh báo leo thang.
- `PARTIAL`: thiếu bất kỳ nguồn/chi nhánh bắt buộc nào.
- `UNAVAILABLE`: truy vấn/read model không khả dụng.
- `NO_DATA`: truy vấn hợp lệ và hoàn tất nhưng không có sự kiện/dữ kiện nào trong kỳ yêu cầu.

### 3.3 `ReportScope`

```json
{
  "type": "BRANCH",
  "branchIds": ["0192b2d0-7f1d-7abc-8d2c-123456789002"]
}
```

Phản hồi công khai chỉ trả về các ID chi nhánh được cấp quyền và nhãn hiển thị an toàn. Tuyệt đối không trả về danh sách chi nhánh lạ hoặc thông tin nội bộ của quan hệ thành viên.

## 4. Hợp Đồng API Công Khai (Public API Contracts)

Mọi endpoint đều yêu cầu quyền `report:read` và trả về phong bì `{data, meta}`. Các tham số query thông dụng được hỗ trợ:

| Tham số | Quy tắc |
|---|---|
| `branchIds` | Tối đa 20; server xác thực tính hợp lệ tập con; bỏ trống nghĩa là dùng mặc định theo vai trò |
| `from`, `to` | Ngày ISO; mặc định 7 ngày; tối đa 12 tháng |
| `timezonePolicy` | Chỉ chấp nhận `SERVER_RESOLVED` trong MVP; client không được tự truyền múi giờ tùy ý |
| `comparison` | `NONE`, `PREVIOUS_EQUAL_PERIOD`, `SAME_WEEKDAY_LAST_WEEK` tại endpoint hỗ trợ |
| `limit` | Từ 1–100 tùy theo endpoint |
| `cursor` | Chỉ dùng cursor do chính endpoint/bộ lọc/phạm vi đó sinh ra |

### 4.1 Danh mục Endpoint

| Phương thức | Đường dẫn | Phản hồi | Mục đích |
|---|---|---|---|
| GET | `/api/v1/reports/dashboard/overview` | `DashboardOverviewResponse` | Các widget KPI và tóm tắt ngắn gọn |
| GET | `/api/v1/reports/sales/daily` | `DailySalesResponse` | Chuỗi doanh thu/đơn hàng/AOV theo ngày |
| GET | `/api/v1/reports/sales/by-branch` | `BranchSalesResponse` | Tổng hợp và so sánh theo chi nhánh |
| GET | `/api/v1/reports/sales/hourly` | `HourlySalesResponse` | Phân bổ theo khung giờ; 1 chi nhánh trong MVP |
| GET | `/api/v1/reports/sales/by-employee` | `EmployeeSalesPage` | Chỉ số theo nhân viên checkout |
| GET | `/api/v1/reports/top-items` | `TopItemsPage` | Danh sách món bán chạy nhất |
| GET | `/api/v1/reports/promotions/effectiveness` | `PromotionEffectivenessPage` | Hiệu quả theo phiên bản chương trình khuyến mãi |
| GET | `/api/v1/reports/recommendations/effectiveness` | `RecommendationEffectivenessResponse` | Phân nhóm hiển thị/chấp nhận/AOV gợi ý |
| GET | `/api/v1/reports/freshness` | `FreshnessResponse` | Sức khỏe và độ tươi mới của projection/nguồn |

---

### 4.2 Phong Bì Phản Hồi Chung

```json
{
  "data": {
    "scope": {
      "type": "BRANCH",
      "branchIds": ["0192b2d0-7f1d-7abc-8d2c-123456789002"]
    },
    "period": {
      "from": "2026-10-01",
      "to": "2026-10-13",
      "timezone": "Asia/Ho_Chi_Minh"
    },
    "rows": [],
    "dataQuality": {
      "status": "FRESH",
      "freshnessLagSeconds": 20,
      "completenessStatus": "COMPLETE",
      "watermark": "reporting:tenant:v901",
      "watermarkVersion": 901,
      "lastUpdatedAt": "2026-10-13T08:00:00Z",
      "projectionVersion": 12,
      "missingSources": [],
      "calculationVersion": "sales-metric-v1",
      "asOfWatermark": "reporting:tenant:v901"
    }
  },
  "meta": {
    "requestId": "req_0192b2d0-7f1d-7abc-8d2c-123456789001",
    "correlationId": "cor_0192b2d0-7f1d-7abc-8d2c-123456789002",
    "timestamp": "2026-10-13T08:00:00Z",
    "nextCursor": null
  }
}
```

---

### 4.3 Tổng quan các chỉ số KPI Dashboard (Dashboard Overview)

`GET /api/v1/reports/dashboard/overview`

Truy vấn các widget KPI tổng thể, tăng trưởng so với kỳ trước và trạng thái dữ liệu.

#### Tiêu đề Yêu cầu (Headers)
```http
Authorization: Bearer <Cognito JWT Token>
Accept: application/json
X-Correlation-ID: 0192b2d0-7f1d-7abc-8d2c-123456789001
```

#### Tham số Truy vấn (Query Parameters)
| Tham số | Kiểu | Bắt buộc | Mô tả |
|---|---|:---:|---|
| `branchIds` | UUID[] | Không | Danh sách chi nhánh (tối đa 20) |
| `from`, `to` | date (YYYY-MM-DD) | Không | Mặc định 7 ngày gần nhất |
| `comparison` | enum | Không | `PREVIOUS_EQUAL_PERIOD`, `SAME_WEEKDAY_LAST_WEEK` |

#### Phản hồi Thành Công `200 OK`
```json
{
  "data": {
    "kpis": {
      "netSalesMinor": 12400000,
      "grossSalesMinor": 13500000,
      "discountMinor": 1100000,
      "vatMinor": 1127273,
      "paidOrderCount": 120,
      "aovMinor": 103333,
      "currency": "VND"
    },
    "comparisons": {
      "netSalesDeltaMinor": -900000,
      "netSalesDeltaRatio": "-0.0677",
      "comparisonPeriod": {"from": "2026-09-18", "to": "2026-09-30"}
    },
    "dataQuality": {
      "status": "FRESH",
      "freshnessLagSeconds": 15,
      "completenessStatus": "COMPLETE",
      "watermark": "reporting:tenant:v901",
      "watermarkVersion": 901,
      "lastUpdatedAt": "2026-10-13T08:00:00Z",
      "projectionVersion": 12
    }
  },
  "meta": {
    "requestId": "req_0192b2d0-7f1d-7abc-8d2c-123456789001",
    "correlationId": "cor_0192b2d0-7f1d-7abc-8d2c-123456789002",
    "timestamp": "2026-10-13T08:00:01Z"
  }
}
```

Tổng quan KPI tuyệt đối không được kết hợp các widget có watermark/phiên bản tính toán không tương thích mà không công khai trạng thái bán phần/kém tươi mới nhất.

---

### 4.4 Báo cáo doanh số theo ngày (Daily Sales Report)

`GET /api/v1/reports/sales/daily`

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
    "scope": {"type": "BRANCH", "branchIds": ["0192b2d0-7f1d-7abc-8d2c-123456789002"]},
    "period": {"from": "2026-10-12", "to": "2026-10-12", "timezone": "Asia/Ho_Chi_Minh"},
    "rows": [
      {
        "businessDate": "2026-10-12",
        "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789002",
        "branchLabel": "Quận 1",
        "paidOrderCount": 42,
        "grossSalesMinor": 4800000,
        "discountMinor": 300000,
        "netSalesMinor": 4500000,
        "vatMinor": 409091,
        "aovMinor": 107143,
        "currency": "VND",
        "calculationVersion": "sales-metric-v1"
      }
    ],
    "dataQuality": {"status": "FRESH", "completenessStatus": "COMPLETE"}
  },
  "meta": {
    "requestId": "req_0192b2d0-7f1d-7abc-8d2c-123456789001",
    "correlationId": "cor_0192b2d0-7f1d-7abc-8d2c-123456789002",
    "timestamp": "2026-10-13T08:00:02Z"
  }
}
```

---

### 4.4.1 Báo cáo so sánh theo chi nhánh (Sales by Branch Report)

`GET /api/v1/reports/sales/by-branch`

Sử dụng các trường chỉ số tương tự kèm theo `rank`, `deltaMinor`, `deltaRatio` và nhãn chi nhánh an toàn. Chi nhánh không thuộc quyền sở hữu sẽ bị loại bỏ, không trả về dưới dạng dòng ẩn/không được phân quyền.

---

### 4.4.2 Báo cáo doanh số theo khung giờ (Hourly Sales Report)

`GET /api/v1/reports/sales/hourly`

#### Phản hồi Thành Công `200 OK`
```json
{
  "data": {
    "scope": {"type": "BRANCH", "branchIds": ["0192b2d0-7f1d-7abc-8d2c-123456789002"]},
    "period": {"from": "2026-10-12", "to": "2026-10-12", "timezone": "Asia/Ho_Chi_Minh"},
    "rows": [
      {
        "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789002",
        "businessDate": "2026-10-12",
        "hourBucket": 18,
        "timezone": "Asia/Ho_Chi_Minh",
        "paidOrderCount": 8,
        "netSalesMinor": 920000,
        "currency": "VND"
      }
    ],
    "dataQuality": {"status": "FRESH", "completenessStatus": "COMPLETE"}
  },
  "meta": {
    "requestId": "req_0192b2d0-7f1d-7abc-8d2c-123456789001",
    "correlationId": "cor_0192b2d0-7f1d-7abc-8d2c-123456789002",
    "timestamp": "2026-10-13T08:00:03Z"
  }
}
```

Khung giờ (hour bucket) là giờ cục bộ cho truy vấn một chi nhánh và giờ chuẩn tenant cho chính sách đa chi nhánh. Không bao giờ trả về timestamp giao dịch chính xác.

---

### 4.5 Doanh số Theo Nhân Viên (Sales by Employee)

`GET /api/v1/reports/sales/by-employee`

#### Phản hồi Thành Công `200 OK`
```json
{
  "data": {
    "scope": {"type": "BRANCH", "branchIds": ["0192b2d0-7f1d-7abc-8d2c-123456789002"]},
    "rows": [
      {
        "employee": {
          "id": "0192b2d0-7f1d-7abc-8d2c-123456789020",
          "displayLabel": "Thu ngân A",
          "statusAtSale": "ACTIVE"
        },
        "paidOrderCount": 42,
        "netSalesMinor": 4500000,
        "aovMinor": 107143,
        "currency": "VND",
        "attributionVersion": "checkout-employee-v1",
        "qualityCode": null
      }
    ],
    "dataQuality": {"status": "FRESH", "completenessStatus": "COMPLETE"}
  },
  "meta": {
    "requestId": "req_0192b2d0-7f1d-7abc-8d2c-123456789001",
    "correlationId": "cor_0192b2d0-7f1d-7abc-8d2c-123456789002",
    "timestamp": "2026-10-13T08:00:04Z"
  }
}
```

Trường hợp thiếu/không khớp dữ liệu ghi nhận nhân viên sẽ dùng `employee.id=UNASSIGNED`, nhãn an toàn `Chưa xác định`, và `qualityCode=missing_checkout_attribution`; bản ghi này vẫn được giữ lại trong tổng doanh số của tenant/chi nhánh.

---

### 4.6 Món Bán Chạy (Top Items)

`GET /api/v1/reports/top-items`

#### Phản hồi Thành Công `200 OK`
```json
{
  "data": {
    "rows": [
      {
        "itemId": "0192b2d0-7f1d-7abc-8d2c-123456789040",
        "itemLabel": "Trà sữa Ô long nướng",
        "itemStatus": "ACTIVE",
        "rank": 1,
        "quantitySold": 120,
        "revenueMinor": 5400000,
        "currency": "VND"
      }
    ],
    "dataQuality": {"status": "FRESH", "completenessStatus": "COMPLETE"}
  },
  "meta": {
    "requestId": "req_0192b2d0-7f1d-7abc-8d2c-123456789001",
    "correlationId": "cor_0192b2d0-7f1d-7abc-8d2c-123456789002",
    "timestamp": "2026-10-13T08:00:05Z"
  }
}
```

`revenueMinor` là doanh thu dòng thuần sau khi đã phân bổ chiết khấu và chưa bao gồm VAT/phí/tip. Danh sách sắp xếp cho phép: `REVENUE` hoặc `QUANTITY`; hòa điểm được xử lý ổn định bằng ID món.

---

### 4.7 Hiệu Quả Khuyến Mãi (Promotion Effectiveness)

`GET /api/v1/reports/promotions/effectiveness`

#### Phản hồi Thành Công `200 OK`
```json
{
  "data": {
    "rows": [
      {
        "promotionVersionId": "0192b2d0-7f1d-7abc-8d2c-123456789050",
        "promotionLabel": "Happy Hour 10% v3",
        "ruleVersion": 3,
        "conflictGroup": "happy-hour",
        "appliedOrderCount": 52,
        "grossSalesMinor": 5200000,
        "discountMinor": 520000,
        "netSalesMinor": 4680000,
        "averageNetOrderMinor": 90000,
        "allocationStatus": "ALLOCATED",
        "currency": "VND"
      }
    ],
    "dataQuality": {"status": "FRESH", "completenessStatus": "COMPLETE"}
  },
  "meta": {
    "requestId": "req_0192b2d0-7f1d-7abc-8d2c-123456789001",
    "correlationId": "cor_0192b2d0-7f1d-7abc-8d2c-123456789002",
    "timestamp": "2026-10-13T08:00:06Z"
  }
}
```

Các giá trị `allocationStatus` có thể có: `ALLOCATED`, `UNALLOCATED_PROMOTION`, `PARTIAL`. Phiên bản khuyến mãi là bất biến và không gộp chung với phiên bản khác.

---

### 4.8 Hiệu Quả Gợi Ý (Recommendation Effectiveness)

`GET /api/v1/reports/recommendations/effectiveness`

#### Phản hồi Thành Công `200 OK`
```json
{
  "data": {
    "shownCount": 1000,
    "acceptedCount": 180,
    "acceptanceRate": "0.1800",
    "acceptedOrderCount": 160,
    "controlOrderCount": 500,
    "acceptedAovMinor": 118000,
    "controlAovMinor": 103000,
    "aovUpliftMinor": 15000,
    "aovUpliftRatio": "0.1456",
    "currency": "VND",
    "attributionVersion": "suggestion-cohort-v1",
    "interpretation": "OBSERVED_COHORT_DIFFERENCE",
    "dataQuality": {"status": "FRESH", "completenessStatus": "COMPLETE"}
  },
  "meta": {
    "requestId": "req_0192b2d0-7f1d-7abc-8d2c-123456789001",
    "correlationId": "cor_0192b2d0-7f1d-7abc-8d2c-123456789002",
    "timestamp": "2026-10-13T08:00:07Z"
  }
}
```

Một đơn hàng chấp nhận nhiều gợi ý chỉ được tính 1 lần trong nhóm đơn hàng chấp nhận (accepted AOV cohort). Số lượng chấp nhận vẫn giữ nguyên ở cấp độ tương tác. Nếu thiếu mẫu số nhóm đối chứng (control denominator) sẽ trả về `NO_DATA`; nếu thiếu correlation ghi nhận đơn hàng sẽ trả về `PARTIAL`/chờ projection và không tính toán âm thầm.

---

### 4.9 Phản Hồi Độ Tươi Mới (Freshness Response)

`GET /api/v1/reports/freshness`

Trả về một `DataQualityEnvelope` cho mỗi phạm vi/nguồn/projection được yêu cầu. Phản hồi bao gồm danh sách `missingSources` an toàn, độ trễ, watermark và phiên bản projection hiện hành. Tuyệt đối không trả về payload hàng đợi thô hay nội dung event.

#### Phản hồi Thành Công `200 OK`
```json
{
  "data": {
    "status": "FRESH",
    "freshnessLagSeconds": 12,
    "completenessStatus": "COMPLETE",
    "watermark": "reporting:tenant:v901",
    "watermarkVersion": 901,
    "lastUpdatedAt": "2026-10-13T08:00:00Z",
    "projectionVersion": 12,
    "missingSources": [],
    "calculationVersion": "sales-metric-v1"
  },
  "meta": {
    "requestId": "req_0192b2d0-7f1d-7abc-8d2c-123456789001",
    "correlationId": "cor_0192b2d0-7f1d-7abc-8d2c-123456789002",
    "timestamp": "2026-10-13T08:00:08Z"
  }
}
```

## 5. Hợp Đồng Vận Hành Nội Bộ (Internal Operational Contracts)

### 5.1 API Rebuild

`POST /internal/v1/reporting/rebuilds`

Yêu cầu (Request):

```json
{
  "tenantId": "0192...",
  "branchIds": ["0192..."],
  "projectionNames": ["sales_daily", "top_items"],
  "fromBusinessDate": "2026-10-01",
  "toBusinessDate": "2026-10-13",
  "sourceWatermarkFrom": "order:v800",
  "sourceWatermarkTo": "order:v901",
  "reasonCode": "PROJECTION_REPAIR"
}
```

Quy tắc: bắt buộc workload có quyền `reporting:operate`, khoảng ngày có giới hạn tối đa cho mỗi thao tác, quyền sở hữu tenant/chi nhánh tường minh, bắt buộc có `Idempotency-Key` và tham chiếu audit. Cùng key/request trả về cùng `operationId`; cùng key/khác request trả về `409 idempotency_conflict`.

Phản hồi `202`:

```json
{
  "data": {
    "operationId": "0192...",
    "status": "REQUESTED",
    "targetProjectionVersion": 13,
    "pollUrl": "/internal/v1/reporting/rebuilds/0192..."
  },
  "meta": {"requestId": "req_01J...", "correlationId": "cor_01J..."}
}
```

Các trạng thái: `REQUESTED`, `RUNNING`, `PAUSED`, `COMPLETED`, `FAILED`, `CANCELLED`.

### 5.2 Sức Khỏe Projection (Projection Health)

`GET /internal/v1/reporting/projection-health`

Chỉ dành riêng cho workload/vận hành viên được cấp phép. Trả về watermark nguồn/projection, độ trễ, độ hoàn tất, số lượng DLQ, phiên bản hiện tại/mục tiêu và mã lỗi gần nhất. Tuyệt đối không trả về payload sự kiện thô.

## 6. Hợp Đồng Sự Kiện (Event Contracts)

### 6.1 Phong Bì Sự Kiện Chung

```json
{
  "eventId": "0192b2d0-7f1d-7abc-8d2c-123456789050",
  "eventType": "OrderPaid",
  "eventVersion": 1,
  "occurredAt": "2026-10-13T08:00:05Z",
  "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789001",
  "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789002",
  "aggregateType": "Order",
  "aggregateId": "0192b2d0-7f1d-7abc-8d2c-123456789011",
  "aggregateVersion": 4,
  "sourceVersion": 901,
  "correlationId": "0192b2d0-7f1d-7abc-8d2c-123456789020",
  "causationId": "0192b2d0-7f1d-7abc-8d2c-123456789021",
  "producer": "order-service",
  "payload": {}
}
```

Các bất biến bắt buộc:

- `eventId` là duy nhất và ổn định xuyên suốt các lần retry/phát lại.
- `tenantId` là bắt buộc; `branchId` là bắt buộc đối với các sự kiện có phạm vi chi nhánh.
- `sourceVersion` phải tăng đơn điệu theo chính sách của producer aggregate/nguồn; Reporting không bao giờ sắp xếp chỉ dựa trên thời điểm đến.
- Consumer ACK chỉ diễn ra sau khi transaction inbox/read-model/watermark đã commit thành công.
- Phiên bản event lạ không xác định sẽ bị cách ly/chuyển vào DLQ.

### 6.2 Tu Chính `OrderPaid.v1` Cho Reporting

Sự kiện `OrderPaid` hiện hành tiếp tục là nguồn chuẩn tắc cho doanh thu. Tu chính bổ sung cho Reporting yêu cầu:

```json
{
  "orderId": "0192...",
  "branchId": "0192...",
  "businessDate": "2026-10-12",
  "businessTimezone": "Asia/Ho_Chi_Minh",
  "checkoutActor": {
    "employeeId": "0192...",
    "displayLabel": "Thu ngân A",
    "role": "CASHIER",
    "membershipVersion": 12,
    "attributionVersion": "checkout-employee-v1"
  },
  "totals": {
    "subtotalMinor": 5200000,
    "discountMinor": 520000,
    "vatMinor": 468000,
    "grandTotalMinor": 5148000,
    "currency": "VND"
  },
  "lines": [],
  "appliedPromotions": [
    {
      "promotionVersionId": "0192...",
      "ruleVersion": 3,
      "conflictGroup": "happy-hour",
      "discountAllocatedMinor": 520000,
      "allocationStatus": "ALLOCATED"
    }
  ],
  "paidAt": "2026-10-12T10:00:00Z"
}
```

Các trường `customerId`, số điện thoại và thông tin cá nhân khách hàng không được tiêu thụ bởi các chỉ số của Reporting. Nếu `checkoutActor` bị thiếu/không hợp lệ, Reporting sẽ gán nhãn `UNASSIGNED/UNKNOWN` và giữ nguyên tổng doanh thu.

### 6.3 Sự Kiện Điều Chỉnh / Hoàn Tiền (Correction/Refund)

Order gốc phải phát hành sự kiện thực tế nghiệp vụ có phiên bản như `OrderRefunded.v1` hoặc `OrderCorrected.v1` với:

- Tham chiếu tenant/chi nhánh/đơn hàng và ID sự kiện nguồn;
- ID điều chỉnh/hoàn tiền và phiên bản aggregate/nguồn;
- Số tiền/dòng món bị ảnh hưởng và ngày kinh doanh có hiệu lực;
- Lý do/trạng thái và tham chiếu đơn thanh toán gốc;
- ID tương quan (correlation/causation IDs).

Riêng các sự kiện từ cổng thanh toán (Payment provider) không thể tự thay đổi doanh thu của Reporting. Các khoản quyết toán Payment đến muộn mà không sinh ra sự kiện OrderPaid/điều chỉnh chuẩn tắc của Order sẽ bị loại trừ.

### 6.4 Sự Kiện Tương Tác Gợi Ý (Recommendation Interactions)

Tối thiểu cho `SuggestionShown.v1`:

```json
{
  "interactionId": "0192...",
  "suggestionId": "0192...",
  "itemId": "0192...",
  "ruleOrModelVersion": "association-v4",
  "orderId": "0192...",
  "cartSessionId": "0192...",
  "renderedAt": "2026-10-12T10:00:00Z"
}
```

`SuggestionAccepted.v1` bổ sung thêm `acceptedInteractionId`, `shownInteractionId`, bằng chứng `orderLineId`/phiên bản đơn hàng và `acceptedAt`. Reporting yêu cầu thứ tự hiển thị → chấp nhận và cùng ngữ cảnh đơn hàng/giỏ hàng. Sự kiện chấp nhận chỉ dùng để ghi nhận đóng góp, không bao giờ là nguồn sự thật doanh thu.

### 6.5 Sự Kiện Metadata

Sự kiện metadata từ Identity/chi nhánh phải cung cấp nhãn an toàn/múi giờ/trạng thái chi nhánh và snapshot nhãn/trạng thái/phiên bản nhân viên. Reporting chỉ lưu trữ các trường cần thiết phục vụ việc hiển thị lịch sử và gom nhóm đã được cấp phép.

### 6.6 Các Sự Kiện Do Reporting Phát Hành

| Sự kiện | Payload | Consumer |
|---|---|---|
| `ReportProjectionWatermarkUpdated.v1` | tenant/phạm vi, projection, watermark/phiên bản, trạng thái, độ trễ, phiên bản projection | Gợi ý cho Dashboard, AI Assistant, vận hành |
| `ReportProjectionRebuilt.v1` | ID thao tác, phạm vi, tên projection, khoảng nguồn, phiên bản mục tiêu, trạng thái, tham chiếu audit | Audit/vận hành |
| `ReportDataQualityChanged.v1` | phạm vi/projection, trạng thái cũ/mới, nguồn bị thiếu, watermark/phiên bản | Gợi ý Dashboard, giám sát khả năng quan sát |
| `ReportAccessAudited.v1` | hash định danh/vai trò an toàn, tóm tắt phạm vi, endpoint, hash bộ lọc, kết quả, correlation | Audit |

Các sự kiện này là dữ kiện gợi ý/quan sát, không phải thẩm quyền phân quyền hay sự thật doanh thu.

## 7. Hợp Đồng Dữ Liệu PostgreSQL

Cơ sở dữ liệu thuộc sở hữu độc quyền của Reporting. Mọi cột bắt buộc phải có `COMMENT ON COLUMN` khớp với mô tả bên dưới. Tất cả các bảng đều có `tenant_id`; các bảng theo phạm vi chi nhánh có `branch_id`. Khóa chính và ID là UUID v7 do ứng dụng sinh ra.

### 7.1 `sales_daily`

| Cột | Kiểu | Null | Mô tả |
|---|---|---:|---|
| `tenant_id` | UUID | không | Tenant sở hữu |
| `branch_id` | UUID | không | Phạm vi chi nhánh |
| `business_date` | DATE | không | Ngày cục bộ theo chính sách chi nhánh/tenant |
| `currency` | TEXT | không | MVP là `VND` |
| `paid_order_count` | BIGINT | không | Số đơn hàng thanh toán thành công duy nhất |
| `gross_sales_minor` | BIGINT | không | Doanh thu gộp trước chiết khấu và chưa thuế |
| `discount_minor` | BIGINT | không | Chiết khấu đã áp dụng |
| `net_sales_minor` | BIGINT | không | Doanh thu thuần (gộp trừ chiết khấu) |
| `vat_minor` | BIGINT | không | Snapshot thuế VAT |
| `projection_version` | BIGINT | không | Phiên bản projection cụ thể hóa hiện hành |
| `watermark` | TEXT | không | Checkpoint nguồn |
| `watermark_version` | BIGINT | không | Checkpoint nguồn đơn điệu tăng |
| `calculation_version` | TEXT | không | Phiên bản từ điển chỉ số |
| `last_updated_at` | TIMESTAMPTZ | không | Thời điểm UTC cập nhật projection |

Khóa chính: `(tenant_id, branch_id, business_date, currency)`.

### 7.2 `sales_by_branch`

| Cột | Kiểu | Null | Mô tả |
|---|---|---:|---|
| `tenant_id` | UUID | không | Tenant sở hữu |
| `branch_id` | UUID | không | Phạm vi chi nhánh |
| `period_start` | DATE | không | Ngày bắt đầu kỳ chuẩn hóa |
| `period_end` | DATE | không | Ngày kết thúc kỳ chuẩn hóa |
| `timezone` | TEXT | không | Múi giờ báo cáo của tenant cho bảng tổng hợp đa chi nhánh |
| `branch_label` | TEXT | không | Nhãn chi nhánh lịch sử an toàn |
| `currency` | TEXT | không | MVP là `VND` |
| `paid_order_count` | BIGINT | không | Số đơn thanh toán duy nhất |
| `gross_sales_minor` | BIGINT | không | Doanh thu gộp |
| `discount_minor` | BIGINT | không | Chiết khấu |
| `net_sales_minor` | BIGINT | không | Doanh thu thuần |
| `vat_minor` | BIGINT | không | Thuế VAT |
| `projection_version` | BIGINT | không | Phiên bản projection |
| `watermark_version` | BIGINT | không | Phiên bản checkpoint nguồn |
| `calculation_version` | TEXT | không | Phiên bản từ điển chỉ số |
| `updated_at` | TIMESTAMPTZ | không | Thời điểm UTC cập nhật projection |

Khóa chính: `(tenant_id, branch_id, period_start, period_end, currency)`.

### 7.3 `sales_by_hour`

| Cột | Kiểu | Null | Mô tả |
|---|---|---:|---|
| `tenant_id` | UUID | không | Tenant sở hữu |
| `branch_id` | UUID | không | Phạm vi chi nhánh |
| `business_date` | DATE | không | Ngày kinh doanh cục bộ |
| `hour_bucket` | SMALLINT | không | Khung giờ cục bộ 0..23 |
| `timezone` | TEXT | không | Múi giờ IANA |
| `currency` | TEXT | không | MVP là `VND` |
| `paid_order_count` | BIGINT | không | Số đơn thanh toán duy nhất |
| `gross_sales_minor` | BIGINT | không | Doanh thu gộp |
| `discount_minor` | BIGINT | không | Chiết khấu |
| `net_sales_minor` | BIGINT | không | Doanh thu thuần |
| `vat_minor` | BIGINT | không | Thuế VAT |
| `projection_version` | BIGINT | không | Phiên bản projection |
| `watermark_version` | BIGINT | không | Phiên bản checkpoint nguồn |
| `calculation_version` | TEXT | không | Phiên bản từ điển chỉ số |
| `updated_at` | TIMESTAMPTZ | không | Thời điểm UTC cập nhật projection |

Khóa chính: `(tenant_id, branch_id, business_date, hour_bucket, currency)`.

### 7.4 `sales_by_employee`

| Cột | Kiểu | Null | Mô tả |
|---|---|---:|---|
| `tenant_id` | UUID | không | Tenant sở hữu |
| `branch_id` | UUID | không | Phạm vi chi nhánh |
| `period_start` | DATE | không | Ngày bắt đầu kỳ truy vấn |
| `period_end` | DATE | không | Ngày kết thúc kỳ truy vấn |
| `employee_key` | TEXT | không | ID nhân viên mờ hoặc `UNASSIGNED` |
| `employee_label` | TEXT | không | Nhãn snapshot an toàn |
| `employee_status_at_sale` | TEXT | không | Snapshot trạng thái lịch sử tại thời điểm bán |
| `attribution_version` | TEXT | không | Chính sách ghi nhận nhân viên checkout |
| `currency` | TEXT | không | MVP là `VND` |
| `paid_order_count` | BIGINT | không | Số đơn thanh toán duy nhất |
| `net_sales_minor` | BIGINT | không | Doanh thu thuần |
| `projection_version` | BIGINT | không | Phiên bản projection |
| `watermark_version` | BIGINT | không | Phiên bản checkpoint nguồn |
| `quality_code` | TEXT | có | Cảnh báo thiếu/không khớp dữ liệu ghi nhận |
| `updated_at` | TIMESTAMPTZ | không | Thời điểm UTC cập nhật projection |

Khóa chính: `(tenant_id, branch_id, period_start, period_end, employee_key, currency)`.

### 7.5 `top_items`

| Cột | Kiểu | Null | Mô tả |
|---|---|---:|---|
| `tenant_id` | UUID | không | Tenant sở hữu |
| `branch_id` | UUID | không | Phạm vi chi nhánh |
| `business_date` | DATE | không | Ngày kinh doanh cục bộ |
| `item_id` | UUID | không | Tham chiếu món trong catalog |
| `item_label` | TEXT | không | Snapshot tên món tại thời điểm thanh toán |
| `item_status` | TEXT | không | Trạng thái hiển thị hiện tại/lịch sử |
| `quantity_sold` | BIGINT | không | Số lượng đã bán và thanh toán |
| `revenue_minor` | BIGINT | không | Doanh thu dòng thuần sau khi phân bổ |
| `currency` | TEXT | không | MVP là `VND` |
| `projection_version` | BIGINT | không | Phiên bản projection |
| `watermark_version` | BIGINT | không | Phiên bản checkpoint nguồn |
| `updated_at` | TIMESTAMPTZ | không | Thời điểm UTC cập nhật projection |

Khóa chính: `(tenant_id, branch_id, business_date, item_id, currency)`.

### 7.6 `promotion_effectiveness`

| Cột | Kiểu | Null | Mô tả |
|---|---|---:|---|
| `tenant_id` | UUID | không | Tenant sở hữu |
| `branch_id` | UUID | không | Phạm vi chi nhánh |
| `period_start` | DATE | không | Ngày bắt đầu kỳ |
| `period_end` | DATE | không | Ngày kết thúc kỳ |
| `promotion_version_id` | UUID | không | Phiên bản khuyến mãi bất biến |
| `rule_version` | BIGINT | không | Phiên bản quy tắc đã ban hành |
| `promotion_label` | TEXT | không | Nhãn lịch sử an toàn |
| `conflict_group` | TEXT | có | Nhóm xung đột áp dụng |
| `applied_order_count` | BIGINT | không | Nhóm đơn thanh toán duy nhất có áp dụng |
| `gross_sales_minor` | BIGINT | không | Doanh thu gộp của nhóm |
| `discount_minor` | BIGINT | không | Chiết khấu đã phân bổ |
| `net_sales_minor` | BIGINT | không | Doanh thu thuần của nhóm |
| `allocation_status` | TEXT | không | `ALLOCATED`, `UNALLOCATED_PROMOTION`, `PARTIAL` |
| `currency` | TEXT | không | MVP là `VND` |
| `projection_version` | BIGINT | không | Phiên bản projection |
| `watermark_version` | BIGINT | không | Phiên bản checkpoint nguồn |
| `updated_at` | TIMESTAMPTZ | không | Thời điểm UTC cập nhật projection |

Khóa chính: `(tenant_id, branch_id, period_start, period_end, promotion_version_id, currency)`.

### 7.7 `recommendation_effectiveness`

| Cột | Kiểu | Null | Mô tả |
|---|---|---:|---|
| `tenant_id` | UUID | không | Tenant sở hữu |
| `branch_id` | UUID | không | Phạm vi chi nhánh |
| `period_start` | DATE | không | Ngày bắt đầu kỳ |
| `period_end` | DATE | không | Ngày kết thúc kỳ |
| `dimension_key` | TEXT | không | Nhóm tổng thể hoặc chiều mô hình/quy tắc ổn định |
| `shown_count` | BIGINT | không | Số lượt tương tác hiển thị duy nhất |
| `accepted_count` | BIGINT | không | Số lượt tương tác chấp nhận duy nhất |
| `accepted_order_count` | BIGINT | không | Số đơn thanh toán duy nhất có chấp nhận |
| `control_order_count` | BIGINT | không | Số đơn thanh toán duy nhất có hiển thị nhưng không chấp nhận |
| `accepted_net_sales_minor` | BIGINT | không | Doanh thu thuần của nhóm chấp nhận |
| `control_net_sales_minor` | BIGINT | không | Doanh thu thuần của nhóm đối chứng |
| `currency` | TEXT | không | MVP là `VND` |
| `attribution_version` | TEXT | không | Chính sách phân nhóm/ghi nhận |
| `quality_code` | TEXT | có | Trạng thái thiếu liên kết/chờ xử lý/bán phần |
| `projection_version` | BIGINT | không | Phiên bản projection |
| `watermark_version` | BIGINT | không | Phiên bản checkpoint nguồn |
| `updated_at` | TIMESTAMPTZ | không | Thời điểm UTC cập nhật projection |

Khóa chính: `(tenant_id, branch_id, period_start, period_end, dimension_key, currency)`.

### 7.8 `projection_watermarks`

| Cột | Kiểu | Null | Mô tả |
|---|---|---:|---|
| `id` | UUID | không | Khóa chính UUID v7 |
| `tenant_id` | UUID | không | Tenant sở hữu |
| `scope_key` | TEXT | không | Khóa phạm vi tenant/chi nhánh |
| `source_name` | TEXT | không | Nguồn sự kiện / đầu vào projection |
| `projection_name` | TEXT | không | Projection read model |
| `watermark` | TEXT | không | Checkpoint mờ |
| `watermark_version` | BIGINT | không | Checkpoint đơn điệu tăng |
| `projection_version` | BIGINT | không | Phiên bản cụ thể hóa |
| `freshness_status` | TEXT | không | `FRESH`, `STALE`, `PARTIAL`, `UNAVAILABLE`, `NO_DATA` |
| `completeness_status` | TEXT | không | `COMPLETE`, `PARTIAL`, `MISSING` |
| `missing_sources` | JSONB | không | Danh sách tên nguồn an toàn |
| `last_updated_at` | TIMESTAMPTZ | không | Thời điểm UTC cập nhật projection |
| `calculation_version` | TEXT | không | Phiên bản từ điển chỉ số |

Khóa duy nhất: `(tenant_id, scope_key, source_name, projection_name)`.

### 7.9 `inbox_events`

| Cột | Kiểu | Null | Mô tả |
|---|---|---:|---|
| `id` | UUID | không | ID dòng inbox nội bộ |
| `tenant_id` | UUID | không | Tenant sở hữu |
| `source_name` | TEXT | không | Producer / nguồn |
| `event_id` | UUID | không | ID sự kiện nguồn toàn cục |
| `event_type` | TEXT | không | Loại sự kiện |
| `event_version` | INTEGER | không | Phiên bản schema sự kiện |
| `source_key` | TEXT | có | Khóa dedupe nghiệp vụ |
| `source_version` | BIGINT | có | Phiên bản producer/aggregate |
| `status` | TEXT | không | `RECEIVED`, `VALIDATING`, `APPLIED`, `IGNORED_DUPLICATE`, `RETRY_WAIT`, `DLQ` |
| `error_code` | TEXT | có | Mã lỗi an toàn |
| `received_at` | TIMESTAMPTZ | không | Thời điểm UTC tiếp nhận |
| `processed_at` | TIMESTAMPTZ | có | Thời điểm UTC xử lý xong |

Khóa duy nhất: `(source_name, event_id)` và `(tenant_id, source_name, source_key)` tương ứng.

### 7.10 `rebuild_operations`

| Cột | Kiểu | Null | Mô tả |
|---|---|---:|---|
| `id` | UUID | không | ID thao tác UUID v7 |
| `tenant_id` | UUID | không | Tenant sở hữu |
| `scope_key` | TEXT | không | Phạm vi tenant/chi nhánh |
| `projection_names` | JSONB | không | Danh sách projection cho phép |
| `from_business_date` | DATE | không | Giới hạn khoảng nguồn bắt đầu |
| `to_business_date` | DATE | không | Giới hạn khoảng nguồn kết thúc |
| `source_watermark_from` | TEXT | có | Cận dưới watermark nguồn |
| `source_watermark_to` | TEXT | có | Cận trên watermark nguồn |
| `target_projection_version` | BIGINT | không | Phiên bản mục tiêu |
| `status` | TEXT | không | `REQUESTED`, `RUNNING`, `PAUSED`, `COMPLETED`, `FAILED`, `CANCELLED` |
| `lease_owner` | TEXT | có | Định danh worker giữ lease |
| `lease_expires_at` | TIMESTAMPTZ | có | Thời hạn hết hạn lease |
| `last_error_code` | TEXT | có | Mã lỗi an toàn |
| `audit_reference_id` | UUID | không | Tham chiếu audit đã ẩn thông tin nhạy cảm |
| `created_at` | TIMESTAMPTZ | không | Thời điểm tạo UTC |
| `updated_at` | TIMESTAMPTZ | không | Thời điểm cập nhật UTC |

### 7.11 `report_access_audit`

| Cột | Kiểu | Null | Mô tả |
|---|---|---:|---|
| `id` | UUID | không | ID tham chiếu audit |
| `tenant_id` | UUID | không | Tenant sở hữu |
| `actor_subject_hash` | TEXT | không | Tham chiếu tác nhân có gắn khóa |
| `role` | TEXT | không | Snapshot vai trò tác nhân |
| `scope_summary` | JSONB | không | Chỉ tóm tắt loại phạm vi/số lượng/phiên bản |
| `endpoint` | TEXT | không | Endpoint báo cáo |
| `filter_hash` | TEXT | không | Hash bộ lọc chuẩn hóa, không chứa PII thô |
| `outcome` | TEXT | không | `ALLOWED`, `DENIED`, `PARTIAL`, `UNAVAILABLE` |
| `data_quality_status` | TEXT | có | Trạng thái chất lượng phản hồi |
| `correlation_id` | UUID | không | Tham chiếu truy vết |
| `created_at` | TIMESTAMPTZ | không | Thời điểm audit UTC |

## 8. Ràng Buộc, Chỉ Mục và Di Trú Dữ Liệu (Constraints, Indexes & Migration)

### 8.1 Các ràng buộc và chỉ mục bắt buộc

- Mọi bảng đều theo phạm vi tenant; các bảng chi nhánh bắt buộc có branch ID.
- `currency = 'VND'` cho các dòng tổng hợp trong MVP.
- Mọi số lượng đếm và số tiền minor unit đều không âm, ngoại trừ các trường chênh lệch (delta) có dấu trong các bảng so sánh.
- Khung giờ hợp lệ trong khoảng `0..23`.
- Khoảng ngày thỏa mãn `from <= to` và tối đa 12 tháng cho truy vấn công khai.
- Hash cursor/bộ lọc/phạm vi ngăn việc tái sử dụng cursor trong ngữ cảnh phân quyền khác.
- Khóa sự kiện nguồn/inbox duy nhất ngăn chặn các tác động trùng lặp.
- Con trỏ/phiên bản projection hiện tại là duy nhất trên `(tenant_id, scope_key, projection_name)`.
- Phiên bản watermark không bao giờ giảm cho cùng một bộ nguồn/phạm vi/projection.
- Phiên bản khuyến mãi là bất biến; không tổng hợp gộp các phiên bản lại với nhau.
- Nhóm chấp nhận/đối chứng gợi ý chỉ có duy nhất 1 dòng cho mỗi đơn hàng/ngữ cảnh theo từng phiên bản ghi nhận.
- `UNASSIGNED`, `UNALLOCATED_PROMOTION` và các mã chất lượng là các giá trị enum tường minh.
- Mệnh đề lọc của repository trong truy vấn công khai luôn bao gồm tenant và tập hợp chi nhánh được cấp phép.

Các chỉ mục bắt buộc:

| Tên chỉ mục | Các cột | Mục đích |
|---|---|---|
| `pk_sales_daily` | tenant, branch, date, currency | Upsert và truy vấn daily |
| `idx_sales_daily_period` | tenant, date DESC, branch | Dashboard daily toàn tenant |
| `pk_sales_by_hour` | tenant, branch, date, hour, currency | Biểu đồ khung giờ |
| `idx_employee_period` | tenant, branch, period_start DESC, employee | Bảng nhân viên |
| `idx_top_items_revenue` | tenant, branch, date DESC, revenue DESC, item | Bảng món bán chạy |
| `idx_promotion_period` | tenant, branch, period_start DESC, promotion_version | Bảng khuyến mãi |
| `idx_recommendation_period` | tenant, branch, period_start DESC, dimension | Bảng gợi ý |
| `idx_watermark_freshness` | tenant, scope, projection, last_updated_at | Độ tươi mới và cảnh báo |
| `uq_inbox_event` | source, event_id | Loại bỏ trùng lặp |
| `idx_rebuild_lease` | status, lease_expires_at | Lease của worker |
| `idx_report_audit_scope_time` | tenant, created_at DESC | Duyệt vết audit |

### 8.2 Thứ Tự Migration

1. Tạo các bảng chung cho tenant/audit/inbox/tính lũy kế (idempotency).
2. Tạo các bảng/ràng buộc cho watermark và phiên bản projection hiện hành.
3. Tạo các read model P0: `sales_daily`, `sales_by_branch`, `top_items` và các chỉ mục tương ứng.
4. Tạo các read model P1: bảng khung giờ/nhân viên/khuyến mãi/gợi ý và các chỉ mục tương ứng.
5. Tạo bảng thao tác rebuild, bảng worker lease và outbox/telemetry.
6. Bổ sung các fixture tương thích với sự kiện nguồn và hợp đồng hệ thống gốc.
7. Chỉ bổ sung phân vùng theo khoảng (range partition) tùy chọn sau khi đã đạt ngưỡng dung lượng/lưu trữ được đo lường thực tế.
8. Nạp dữ liệu mẫu (seed) các tham chiếu phiên bản từ điển chỉ số mang tính đơn định; không thực hiện backfill sự kiện nghiệp vụ từ các nguồn không xác định.

Các migration chỉ được tiến về phía trước (forward-only) trong môi trường dùng chung. Các thay đổi mang tính phá hủy (destructive) phải áp dụng chiến lược expand → migrate → contract. Mọi cột bắt buộc phải có `COMMENT ON COLUMN`.

### 8.3 Các Kịch Bản Migration Flyway (Flyway Migration Scripts)

#### `V1__init_reporting_read_models.sql`
```sql
-- Flyway Migration V1: Bảng inbox, watermark và các bảng tổng hợp doanh thu cốt lõi P0
CREATE TABLE reporting_inbox (
    source VARCHAR(64) NOT NULL,
    event_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    branch_id VARCHAR(64),
    payload JSONB NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_reporting_inbox PRIMARY KEY (source, event_id)
);
COMMENT ON TABLE reporting_inbox IS 'Bảng inbox bảo đảm tính idempotent cho các event tiêu thụ vào reporting read model';

CREATE TABLE reporting_watermarks (
    tenant_id VARCHAR(64) NOT NULL,
    scope VARCHAR(64) NOT NULL,
    projection_name VARCHAR(64) NOT NULL,
    last_event_id VARCHAR(128) NOT NULL,
    last_event_time TIMESTAMPTZ NOT NULL,
    last_updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'FRESH',
    CONSTRAINT pk_reporting_watermarks PRIMARY KEY (tenant_id, scope, projection_name)
);
CREATE INDEX idx_watermark_freshness ON reporting_watermarks (tenant_id, scope, projection_name, last_updated_at);
COMMENT ON TABLE reporting_watermarks IS 'Theo dõi tiến độ xử lý và độ tươi mới (freshness) của từng projection';

CREATE TABLE sales_daily (
    tenant_id VARCHAR(64) NOT NULL,
    branch_id VARCHAR(64) NOT NULL,
    report_date DATE NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    gross_sales BIGINT NOT NULL DEFAULT 0,
    net_sales BIGINT NOT NULL DEFAULT 0,
    discount_amount BIGINT NOT NULL DEFAULT 0,
    tax_amount BIGINT NOT NULL DEFAULT 0,
    order_count INT NOT NULL DEFAULT 0,
    customer_count INT NOT NULL DEFAULT 0,
    average_order_value BIGINT NOT NULL DEFAULT 0,
    cash_sales BIGINT NOT NULL DEFAULT 0,
    qr_sales BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_sales_daily PRIMARY KEY (tenant_id, branch_id, report_date, currency)
);
CREATE INDEX idx_sales_daily_period ON sales_daily (tenant_id, report_date DESC, branch_id);
COMMENT ON TABLE sales_daily IS 'Read model doanh số lũy kế theo ngày';

CREATE TABLE sales_top_items (
    tenant_id VARCHAR(64) NOT NULL,
    branch_id VARCHAR(64) NOT NULL,
    report_date DATE NOT NULL,
    item_id VARCHAR(64) NOT NULL,
    item_name VARCHAR(255) NOT NULL,
    category_id VARCHAR(64) NOT NULL,
    quantity_sold INT NOT NULL DEFAULT 0,
    net_revenue BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_sales_top_items PRIMARY KEY (tenant_id, branch_id, report_date, item_id)
);
CREATE INDEX idx_top_items_revenue ON sales_top_items (tenant_id, branch_id, report_date DESC, net_revenue DESC);
COMMENT ON TABLE sales_top_items IS 'Read model xếp hạng món bán chạy';
```

#### `V2__init_hourly_and_employee_reports.sql`
```sql
-- Flyway Migration V2: Bảng phân tích theo khung giờ và năng suất nhân viên P1
CREATE TABLE sales_by_hour (
    tenant_id VARCHAR(64) NOT NULL,
    branch_id VARCHAR(64) NOT NULL,
    report_date DATE NOT NULL,
    hour_of_day SMALLINT NOT NULL CHECK (hour_of_day >= 0 AND hour_of_day <= 23),
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    net_sales BIGINT NOT NULL DEFAULT 0,
    order_count INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_sales_by_hour PRIMARY KEY (tenant_id, branch_id, report_date, hour_of_day, currency)
);
COMMENT ON TABLE sales_by_hour IS 'Read model doanh thu và số đơn theo khung giờ trong ngày';

CREATE TABLE employee_sales_summary (
    tenant_id VARCHAR(64) NOT NULL,
    branch_id VARCHAR(64) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    employee_id VARCHAR(64) NOT NULL,
    employee_name VARCHAR(255) NOT NULL,
    total_orders INT NOT NULL DEFAULT 0,
    total_net_sales BIGINT NOT NULL DEFAULT 0,
    total_tips BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_employee_sales_summary PRIMARY KEY (tenant_id, branch_id, period_start, period_end, employee_id)
);
CREATE INDEX idx_employee_period ON employee_sales_summary (tenant_id, branch_id, period_start DESC, employee_id);
COMMENT ON TABLE employee_sales_summary IS 'Read model doanh số và số lượt phục vụ/checkout của nhân viên';
```

#### `V3__init_promotion_and_recommendation_reports.sql`
```sql
-- Flyway Migration V3: Bảng hiệu quả khuyến mãi và gợi ý món P1
CREATE TABLE promotion_performance_summary (
    tenant_id VARCHAR(64) NOT NULL,
    branch_id VARCHAR(64) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    promotion_version VARCHAR(64) NOT NULL,
    promotion_id VARCHAR(64) NOT NULL,
    promotion_name VARCHAR(255) NOT NULL,
    total_discount_amount BIGINT NOT NULL DEFAULT 0,
    total_redemptions INT NOT NULL DEFAULT 0,
    total_order_revenue BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_promotion_perf_summary PRIMARY KEY (tenant_id, branch_id, period_start, period_end, promotion_version)
);
CREATE INDEX idx_promotion_period ON promotion_performance_summary (tenant_id, branch_id, period_start DESC, promotion_version);
COMMENT ON TABLE promotion_performance_summary IS 'Read model hiệu quả áp dụng voucher và khuyến mãi';

CREATE TABLE recommendation_performance_summary (
    tenant_id VARCHAR(64) NOT NULL,
    branch_id VARCHAR(64) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    dimension VARCHAR(64) NOT NULL,
    impressions INT NOT NULL DEFAULT 0,
    acceptances INT NOT NULL DEFAULT 0,
    acceptance_rate NUMERIC(5,2) NOT NULL DEFAULT 0.00,
    attributed_revenue BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_recommendation_perf PRIMARY KEY (tenant_id, branch_id, period_start, period_end, dimension)
);
CREATE INDEX idx_recommendation_period ON recommendation_performance_summary (tenant_id, branch_id, period_start DESC, dimension);
COMMENT ON TABLE recommendation_performance_summary IS 'Read model tỷ lệ chấp nhận và doanh thu đóng góp từ module AI recommendation';
```

#### `V4__init_rebuild_and_leases.sql`
```sql
-- Flyway Migration V4: Bảng điều phối Rebuild và Lease worker
CREATE TABLE reporting_rebuild_jobs (
    job_id VARCHAR(64) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    scope VARCHAR(64) NOT NULL,
    projections TEXT[] NOT NULL,
    date_from DATE NOT NULL,
    date_to DATE NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'QUEUED',
    progress_percentage SMALLINT NOT NULL DEFAULT 0,
    trigger_reason VARCHAR(255) NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE reporting_worker_leases (
    lease_name VARCHAR(64) NOT NULL PRIMARY KEY,
    worker_id VARCHAR(128) NOT NULL,
    lease_expires_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACQUIRED',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_rebuild_lease ON reporting_worker_leases (status, lease_expires_at);
```

#### `V5__init_reporting_audit_logs.sql`
```sql
-- Flyway Migration V5: Vết kiểm toán truy cập báo cáo
CREATE TABLE reporting_access_audit_logs (
    audit_id VARCHAR(64) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    branch_id VARCHAR(64),
    user_id VARCHAR(64) NOT NULL,
    user_role VARCHAR(64) NOT NULL,
    endpoint VARCHAR(128) NOT NULL,
    query_parameters JSONB,
    export_format VARCHAR(20),
    ip_address VARCHAR(45) NOT NULL,
    user_agent TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_report_audit_scope_time ON reporting_access_audit_logs (tenant_id, created_at DESC);
COMMENT ON TABLE reporting_access_audit_logs IS 'Lưu vết kiểm toán truy xuất báo cáo, chống rò rỉ dữ liệu tài chính kinh doanh';
```


## 9. Hợp Đồng Bộ Nhớ Đệm và Tính Nhất Quán (Cache & Consistency)

Cấu trúc cache key:

```text
report:v1:{tenantId}:{scopeType}:{scopeHash}:{authorizationVersion}
  :{endpoint}:{normalizedFilterHash}:{calculationVersion}:{watermarkVersion}
```

- TTL tối đa 15 giây.
- Bước kiểm tra phân quyền luôn đi trước việc tra cứu cache.
- Phiên bản phân quyền/phạm vi là thành phần bắt buộc trong khóa cache.
- Cơ chế hủy bỏ cache theo namespace watermark được kích hoạt ngay sau khi có sự kiện điều chỉnh hoặc rebuild.
- Lỗi kết nối Redis tự động fallback truy vấn sang PostgreSQL; lỗi kết nối PostgreSQL trả về `503 reporting_unavailable`.
- Phản hồi từ cache mang nguyên vẹn watermark/lastUpdatedAt gốc; cache hit không được tự ý báo timestamp mới hơn.
- Mặc định vô hiệu hóa browser/public CDN caching đối với các phản hồi báo cáo được bảo vệ.

## 10. Ma Trận Kiểm Thử Hợp Đồng (Contract Test Matrix)

| Khu vực | Các fixture kiểm thử bắt buộc |
|---|---|
| Phân quyền / Phạm vi | Chủ sở hữu toàn tenant, Chủ sở hữu tập con chi nhánh, Quản lý chi nhánh được giao, chi nhánh lạ ngoài quyền, thu hồi membership, kiểm tra cache sau khi thu hồi quyền |
| Doanh thu | Sự kiện OrderPaid trùng lặp, quyết toán Payment đến muộn bị loại trừ, hoàn tiền/điều chỉnh, không có dữ liệu, VND và kiểm tra tiền tệ lẫn lộn |
| Nhân viên | Chuyển ca nhanh, lịch sử nhân viên bị vô hiệu hóa, thiếu dữ liệu ghi nhận → UNASSIGNED, nhãn snapshot/phiên bản |
| Khuyến mãi | Nhiều phiên bản quy tắc, nhóm xung đột, phân bổ, chưa phân bổ/bán phần, thay đổi phiên bản, điều chỉnh |
| Gợi ý | Hiển thị/chấp nhận trùng lặp, cùng ngữ cảnh giỏ hàng, OrderPaid đến trễ, chấp nhận nhiều gợi ý trong 1 đơn, mẫu số nhóm đối chứng bằng 0, phân nhóm tính 1 lần |
| Độ tươi mới | ≤1m FRESH, >1m STALE, >5m kích hoạt cảnh báo, thiếu nguồn PARTIAL, DB không khả dụng, NO_DATA |
| Projection | Phát lại at-least-once, phiên bản không xác định, lỗi schema, retry/DLQ, phiên bản nguồn sai thứ tự, hoán đổi rebuild |
| API | Bộ lọc/khoảng thời gian/giới hạn, cursor phân trang không khớp, mọi endpoint/phong bì, mã lỗi 401/403/409/422/429/503 |
| Cache | Cách ly khóa phạm vi, hủy cache theo watermark, ETag sau khi phân quyền, fallback khi Redis gặp sự cố |
| Hiệu năng | KPI p95 ≤1s, bảng dữ liệu p95 ≤2s, phạm vi 12 tháng, 20 chi nhánh, tải đồng thời trên dashboard |
| Quyền riêng tư | Tuyệt đối không để lộ SĐT/thông tin khách hàng/thông tin thanh toán/ID đơn hàng thô trong phản hồi/log/trace/event; nhãn nhân viên được giới hạn phạm vi |
| Sự kiện | Tính tương thích AsyncAPI, tính nguyên tử của inbox, dedupe, tính đơn điệu của phiên bản nguồn, phả hệ phát lại |
| Hợp đồng UI | Đầy đủ mọi trạng thái chất lượng dữ liệu, lỗi an toàn, không thay số 0 cho NO_DATA, hành vi chi nhánh/múi giờ/bộ lọc |

## 11. Các Tu Chính Hợp Đồng Hệ Thống Gốc (Parent Amendments)

### Identity Access

- Bổ sung quyền `report:read` và quyền hạn chế `reporting:operate`.
- Hỗ trợ phân giải phạm vi tenant/tập con chi nhánh, phiên bản membership/phân quyền và hành vi từ chối không liệt kê danh sách.

### Order

- Bổ sung tham chiếu bất biến, snapshot nhãn hiển thị và phiên bản ghi nhận của nhân viên checkout/thu ngân vào `OrderPaid.v1`.
- Bổ sung phiên bản khuyến mãi áp dụng và cấu trúc phân bổ chiết khấu bất biến.
- Phát hành sự kiện thực tế nghiệp vụ đã duyệt `OrderRefunded.v1`/`OrderCorrected.v1` kèm phiên bản nguồn/hiệu lực.

### Loyalty/Promotion

- Bảo toàn phiên bản quy tắc/khuyến mãi bất biến, nhóm xung đột, nguồn voucher/khuyến mãi và giá trị phân bổ trong snapshot của OrderPaid.
- Reporting không bao giờ đánh giá lại quy tắc khuyến mãi đã ban hành dựa trên cấu hình hiện tại.

### Recommendation

- Đảm bảo `SuggestionShown.v1`/`SuggestionAccepted.v1` chứa ID tương tác ổn định, cùng correlation đơn hàng/giỏ hàng, phiên bản mô hình/quy tắc, thứ tự sự kiện và bằng chứng chấp nhận.
- Reporting chỉ tiêu thụ sự kiện chấp nhận với vai trò ghi nhận đóng góp; `OrderPaid.v1` tiếp tục là nguồn sự thật cho đơn thanh toán.

### POS Platform / Audit

- Bổ sung danh mục API/read model/sự kiện của Reporting và tham chiếu quyền `report:read` vào hợp đồng nền tảng chung.
- Bổ sung schema tiêu thụ sự kiện đã ẩn thông tin nhạy cảm `ReportAccessAudited.v1`, watermark, rebuild và chất lượng dữ liệu.

## 12. Các Artifact Triển Khai Bắt Buộc Trước Khi Viết Code

- File `openapi.yaml` cho tất cả các endpoint báo cáo/freshness công khai.
- File `openapi-internal.yaml` cho các endpoint rebuild/sức khỏe/vận hành.
- File `asyncapi.yaml` và các JSON Schema cho các sự kiện tiêu thụ và phát hành.
- File `reporting-metric-dictionary.yaml` chứa các phiên bản tính toán, quy tắc múi giờ, làm tròn và điều chỉnh.
- File `reporting-read-model.schema.yaml` hoặc migration SQL/Flyway được sinh ra với đầy đủ comment trên cột, chỉ mục và kiểm tra ràng buộc.
- Các fixture vàng (golden fixtures) cho doanh thu, nhân viên, khuyến mãi, gợi ý, độ tươi mới và phát lại.
- Các fixture tương thích hướng người tiêu thụ (consumer-driven) cho Order, Loyalty/Promotion, Recommendation và Identity.
- Kiểu dữ liệu client được sinh cho Angular và mock phản hồi dashboard cho toàn bộ các trạng thái chất lượng dữ liệu.
- Hồ sơ tải (load profile) cho mục tiêu KPI/bảng p95 và các truy vấn bị chặn trong 12 tháng / 20 chi nhánh.
- Bộ dữ liệu đánh giá/bảo mật cho IDOR, rò rỉ cache, lộ lọt PII, thu hồi phạm vi và các bộ lọc không an toàn.

## 13. Cổng Phê Duyệt (Approval Gate)

- **Trạng thái hiện tại:** Đã phê duyệt kèm cảnh báo — người dùng đã phê duyệt sau khi đánh giá Contracts.
- **Yêu cầu trước khi triển khai code:** Giải quyết sự kiện điều chỉnh/hoàn tiền chuẩn tắc, ban hành các tu chính hợp đồng hệ thống gốc, và tạo các artifact API/event/schema/migration ở dạng máy đọc được.
- **Quy tắc triển khai:** Tuyệt đối không viết trực tiếp controller/query/consumer/migration từ văn bản mô tả; bắt buộc phải tạo các hợp đồng và fixture máy đọc được trước.
