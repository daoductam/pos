Started at: 2026/10/13 00:00:00
Finished at: 2026/10/13 00:00:00
Total time: 0 minutes
---

# Module 10 — Báo Cáo & Dashboard: Kiến Trúc (Architecture)

**Ngày:** 2026-10-13  
**Trạng thái:** Đã phê duyệt — các luồng tuần tự đã được xác nhận  
**Tầm nhìn:** [Reporting Vision](file:///d:/vibe-code/pos/docs/designs-vi/reporting/2026-10-13-vision.md)  
**Yêu cầu:** [Reporting Requirements](file:///d:/vibe-code/pos/docs/designs-vi/reporting/2026-10-13-requirements.md)  
**Đánh giá yêu cầu:** [Reporting Requirements Review](file:///d:/vibe-code/pos/docs/designs-vi/reporting/2026-10-13-requirements-review.md)  
**Biểu đồ tuần tự:** [Reporting Sequence Diagrams](file:///d:/vibe-code/pos/docs/designs-vi/reporting/2026-10-13-sequence-diagrams.adoc)

## 1. Tóm Tắt Quyết Định Kiến Trúc

Reporting là một bounded context chỉ đọc (read-only), sử dụng các read model tổng hợp sẵn (pre-aggregated) hướng sự kiện trong PostgreSQL cho dashboard giai đoạn MVP. Các domain service nghiệp vụ giữ vai trò là nguồn sự thật duy nhất (source of truth); Reporting không truy vấn chéo cơ sở dữ liệu và không phát sinh bất kỳ thay đổi trạng thái (mutation) nghiệp vụ nào.

| Quyết định | Lựa chọn | Lý do |
|---|---|---|
| Luồng đọc (Read path) | PostgreSQL read models | Đáp ứng KPI p95 ≤1s, bảng dữ liệu p95 ≤2s và độ tươi mới (freshness) ≤1 phút |
| Thu nhận dữ liệu (Ingestion) | EventBridge + hàng đợi SQS, at-least-once | Phân tách với producer, hỗ trợ retry/DLQ và khả năng phát lại (replay) |
| Xử lý Projection | Tổng hợp sẵn ưu tiên truy vấn (Query-first pre-aggregates) | Không chạy aggregate trên các bản ghi thô (raw rows) trên hot path truy vấn 12 tháng |
| Thực thi phạm vi (Scope enforcement) | Quyết định trực tuyến từ Identity + điều kiện lọc repository | Phòng thủ chiều sâu (defense-in-depth) cho tenant/chi nhánh |
| Bộ nhớ đệm (Cache) | Redis/ElastiCache tối đa 15s | Giảm tải dashboard nhưng không vi phạm chính sách độ tươi mới |
| Phân tích lịch sử mở rộng | Ranh giới mở rộng Athena | Chưa đưa vào luồng xử lý request của MVP |
| Xử lý điều chỉnh | Projection hiện tại + phả hệ watermark/phiên bản | Dashboard hiển thị số liệu hiện hành, kèm theo `asOfWatermark` |
| Giao diện API | REST/OpenAPI 3.1 | Phù hợp với Angular dashboard và chuẩn nền tảng cha |
| Định danh/Thời gian | UUID v7, UTC, ngày kinh doanh cục bộ chi nhánh | Đảm bảo tính nhất quán toàn nền tảng |

## 2. Ngữ Cảnh Hệ Thống (System Context)

```text
Owner/Manager Dashboard (Angular)
              |
              v
       API Gateway / Cognito
              |
              v
       Reporting Query API ──────────────> Redis Cache (≤15s)
              |
              v
       Reporting PostgreSQL Read Models
              ^
              |
       Projection Workers / Watermark Manager
         ^          ^          ^          ^
         |          |          |          |
      Order      Loyalty/    Recommend.  Identity/Branch
    OrderPaid   Promotion     Shown/      metadata
   correction    snapshot     Accepted
              ^
              |
      EventBridge + SQS + DLQ
```

API Gateway/Cognito xác thực request/JWT bên ngoài. Reporting vẫn gọi Identity để nhận quyết định phân quyền trực tuyến hiện hành vì tính hợp lệ của quan hệ thành viên/thu hồi quyền chi nhánh thuộc thẩm quyền trực tuyến, sau đó áp dụng lại phạm vi đã phân giải vào tầng cache và tầng repository.

## 3. Ranh Giới Thành Phần (Component Boundaries)

| Thành phần | Trách nhiệm | Điều cấm (Must not do) |
|---|---|---|
| Dashboard Angular | Render KPI/biểu đồ/bảng/bộ lọc/drill-down và trạng thái chất lượng dữ liệu | Tự tính KPI có thẩm quyền hoặc tin cậy timezone/phạm vi do trình duyệt gửi |
| Reporting Query API | Xác thực bộ lọc, phân giải phạm vi, đọc cache/read model, trả về phong bì chuẩn | Truy vấn DB của producer, nhận câu lệnh SQL, để lộ bản ghi domain thô |
| Identity Scope Client | Yêu cầu quyết định `report:read`, phiên bản phân quyền/membership và danh sách chi nhánh được cấp phép | Cache phân quyền làm thẩm quyền tồn tại lâu dài |
| Report Policy Engine | Thực thi kiểm tra vai trò, khoảng thời gian tối đa, số lượng chi nhánh, chiều phân tích, chính sách riêng tư/nhân viên | Phân giải định danh từ input của LLM/client |
| Cache Adapter | Bộ nhớ đệm phản hồi 15s an toàn theo phạm vi và metadata ETag | Phục vụ dữ liệu trước khi phân quyền hoặc dùng cache key xuyên phạm vi |
| Projection Ingestor | Đọc event, kiểm tra phong bì/schema, loại bỏ trùng lặp, định tuyến đến projection handler | Gửi ACK trước khi transaction DB commit thành công |
| Projection Handlers | Cập nhật upsert bảng read model tổng hợp và watermark trong transaction | Đánh giá lại quy tắc khuyến mãi lịch sử hoặc query DB nguồn |
| Watermark/Completeness Manager | Theo dõi độ trễ theo từng nguồn/phạm vi, tính đầy đủ của nguồn bắt buộc và chất lượng dữ liệu | Đánh dấu dữ liệu bán phần (partial) là tươi mới (fresh) |
| Rebuild Worker | Xử lý lại phạm vi event có giới hạn vào phiên bản projection mục tiêu, hoán đổi nguyên tử | Thay đổi dữ liệu nguồn producer hoặc ghi đè âm thầm lên phiên bản hiện hành |
| Reporting PostgreSQL | Sở hữu các read model, inbox, watermark, rebuild, đối soát và tham chiếu audit | Trở thành nguồn sự thật nghiệp vụ thứ hai |
| Athena Adapter (tương lai) | Phục vụ tải phân tích lịch sử/tùy biến lớn sau hợp đồng truy vấn rõ ràng | Bước vào hot path của dashboard MVP khi chưa có chứng minh SLO |

## 4. Luồng Dữ Liệu (Data Flow)

### 4.1 Thu nhận sự kiện (Event Ingestion)

1. Producer phát hành sự kiện có phiên bản thông qua Transactional Outbox.
2. EventBridge định tuyến sự kiện tới hàng đợi SQS của Reporting theo nhóm nghiệp vụ (event family).
3. Consumer của Reporting kiểm tra phạm vi tenant/chi nhánh, phiên bản event, khóa nguồn và schema.
4. Transaction inbox loại bỏ trùng lặp dựa trên `eventId`/business key nguồn.
5. Projection handler cập nhật các bảng tổng hợp sẵn bị ảnh hưởng và bảng `projection_watermarks`.
6. Transaction commit; consumer xác nhận hoàn tất (ACK) message.
7. Lỗi tạm thời (transient failures) được retry; lỗi schema/bảo mật/chỉ số chuyển sang DLQ kèm ngữ cảnh lỗi đã ẩn thông tin nhạy cảm (redacted).

### 4.2 Luồng truy vấn (Query Flow)

1. Dashboard gửi request endpoint/bộ lọc báo cáo kèm JWT từ Cognito.
2. API xác thực định dạng bộ lọc và gọi phân quyền trực tuyến từ Identity cho quyền `report:read`.
3. Policy engine chuẩn hóa phạm vi, kỳ báo cáo và múi giờ.
4. Khóa cache được tạo từ: tenant, phạm vi đã phân giải/phiên bản phân quyền, endpoint, hash bộ lọc chuẩn hóa, phiên bản công thức tính toán và watermark.
5. Khi cache hit, chỉ trả về sau khi đã vượt qua phân quyền; khi cache miss, đọc các bản ghi tổng hợp sẵn với điều kiện lọc tenant/chi nhánh.
6. API đính kèm `dataQuality`, `asOfWatermark`, `lastUpdatedAt`, `projectionVersion` và `calculationVersion`.
7. Dashboard render giá trị và trạng thái độ tươi mới; tuyệt đối không âm thầm thay thế giá trị 0 cho trường hợp `NO_DATA`.

### 4.3 Luồng điều chỉnh và phát lại (Correction/Replay Flow)

1. Sự kiện hoàn tiền/điều chỉnh/đến muộn đã duyệt đến với ID sự kiện nguồn và phiên bản hiệu lực.
2. Handler xác thực tính đơn điệu của phiên bản nguồn và loại trừ trùng lặp.
3. Bản ghi tổng hợp hiện hành được điều chỉnh hoặc tính toán lại từ phả hệ nguồn có giới hạn theo chính sách chỉ số.
4. `asOfWatermark` và `projectionVersion` tăng tiến một cách nguyên tử cùng với cập nhật trên read model.
5. Các mục cache khớp với phạm vi/kỳ báo cáo bị ảnh hưởng được hủy bỏ hiệu lực (invalidate) theo namespace watermark.
6. Dashboard đọc chế độ xem hiện hành đã điều chỉnh; lịch sử sự kiện nguồn vẫn bất biến.

## 5. Kiến Trúc Bảo Mật và Phạm Vi (Scope & Security Architecture)

### 5.1 Phân quyền yêu cầu (Request Authorization)

- API bắt buộc có Cognito JWT và quyền `report:read`.
- Reporting gọi `POST /internal/v1/authorization-decisions` kèm đối tượng hiện tại, tenant, các chi nhánh yêu cầu và thao tác.
- Chủ cửa hàng (Owner) có thể nhận phạm vi `TENANT` hoặc các chi nhánh được ủy quyền chọn lọc; Quản lý (Manager) chỉ nhận các chi nhánh được phân công.
- `authorizationVersion`/phiên bản quan hệ thành viên được đưa vào ngữ cảnh phạm vi và khóa cache.
- Phân quyền được kiểm tra trước khi tra cứu cache và trước mỗi truy vấn tài nguyên/drill-down.

### 5.2 Phòng thủ chiều sâu tại Repository (Repository Defense-in-depth)

Mọi câu truy vấn đều bắt buộc có:

```text
tenant_id = resolvedScope.tenantId
AND (
  resolvedScope.scopeType = TENANT
  OR branch_id IN resolvedScope.branchIds
)
```

Điều kiện lọc này do policy phía server sinh ra, không bao giờ được nối chuỗi từ chuỗi request của client. Các chiều phân tích nhân viên, khuyến mãi và gợi ý kế thừa phạm vi chi nhánh; không một endpoint nào được phép mở rộng phạm vi thông qua bộ lọc chiều phân tích.

### 5.3 Quyền riêng tư (Privacy)

- Không trả về số điện thoại/tên khách hàng, thông tin thanh toán hay ID đơn hàng thô trong phản hồi báo cáo công khai.
- Báo cáo nhân viên chỉ trả về snapshot hiển thị an toàn cho Chủ cửa hàng/Quản lý được cấp quyền.
- Log/trace chứa correlation ID, endpoint, loại phạm vi/số lượng và metadata chỉ số; mặc định không chứa payload sự kiện thô.
- Bản ghi audit ghi nhận truy cập báo cáo, từ chối phạm vi, thao tác rebuild/replay và replay DLQ mà không lưu trữ PII thô.

## 6. Kiến Trúc Dữ Liệu Read-Model

### 6.1 Các bảng cốt lõi (Core Tables)

| Bảng | Hạt nhân dữ liệu (Grain) | Mẫu truy vấn / Khóa chính |
|---|---|---|
| `sales_daily` | tenant/chi nhánh/ngày kinh doanh | `(tenant_id, branch_id, business_date)` |
| `sales_by_branch` | tenant/kỳ báo cáo/chi nhánh | `(tenant_id, period_start, branch_id)` |
| `sales_by_hour` | tenant/chi nhánh/ngày/khung giờ | `(tenant_id, branch_id, business_date, hour_bucket)` |
| `sales_by_employee` | tenant/chi nhánh/kỳ/nhân viên | `(tenant_id, branch_id, period_start, employee_id_or_unassigned)` |
| `top_items` | tenant/chi nhánh/ngày/món | `(tenant_id, branch_id, business_date, item_id)` |
| `promotion_effectiveness` | tenant/chi nhánh/kỳ/phiên bản khuyến mãi | `(tenant_id, branch_id, period_start, promotion_version_id)` |
| `recommendation_effectiveness` | tenant/chi nhánh/kỳ/chiều ghi nhận | `(tenant_id, branch_id, period_start, attribution_dimension)` |
| `projection_watermarks` | nguồn/phạm vi/projection | `(tenant_id, source_name, scope_key, projection_name)` |
| `inbox_events` | sự kiện nguồn | `(source, event_id)` kèm tenant |
| `rebuild_operations` | tác vụ rebuild có giới hạn | UUID v7 operation ID |

Mọi bảng đều bao gồm `tenant_id`, `created_at`/`updated_at` (nếu phù hợp), `projection_version`, metadata watermark nguồn và bắt buộc có `COMMENT ON COLUMN` trong script migration. Các bảng scoped theo chi nhánh luôn bao gồm `branch_id`.

### 6.2 Snapshot nhân viên và khuyến mãi

Reporting lưu các snapshot an toàn cần thiết để phục vụ lịch sử tái lập được, không thực hiện join trực tiếp lúc query:

- `checkout_employee_id`: UUID/tham chiếu mờ, nhãn hiển thị an toàn, snapshot vai trò/chi nhánh và `attribution_version`.
- `promotion_version_id`: nhãn/phiên bản quy tắc, nhóm xung đột, chiết khấu được phân bổ và trạng thái phân bổ.
- `suggestion_interaction_id`: món/quy tắc/phiên bản mô hình và correlation đơn hàng/giỏ hàng phục vụ việc ghi nhận.

Nếu snapshot nguồn bị thiếu, bản ghi vẫn được tính vào tổng chỉ số chung nhưng được gắn nhãn `UNASSIGNED`, `UNALLOCATED` hoặc cờ `PARTIAL` theo chính sách chỉ số.

### 6.3 Chiến lược phân vùng và đánh chỉ mục (Partition/Index Strategy)

- Chỉ mục MVP tối ưu hóa cho `(tenant_id, branch_id, business_date DESC)` và các truy vấn khoảng thời gian.
- Phân vùng theo tháng (monthly range partitions) là phương án dự phòng cho các bảng daily/hourly/top-item/employee khi đạt ngưỡng dung lượng hoặc lưu trữ được đo lường thực tế; không đưa vào sự phức tạp phân vùng chưa được đo lường ngay từ đầu.
- Composite index luôn đặt tenant lên đầu tiên; chi nhánh/ngày/giờ theo sau theo mẫu truy vấn.
- Bảng khuyến mãi/gợi ý đánh chỉ mục theo phiên bản/chiều bất biến và kỳ báo cáo.
- Khóa sắp xếp con trỏ (cursor sort keys) mang tính đơn định và được đánh chỉ mục.
- Giới hạn truy vấn tối đa 12 tháng được thực thi nghiêm ngặt tại tầng API và tầng policy.

## 7. Kiến Trúc Sự Kiện và Projection

### 7.1 Đầu vào sự kiện chuẩn tắc (Canonical Event Inputs)

| Producer | Sự kiện / Thực tế nghiệp vụ | Các trường bắt buộc cho Reporting |
|---|---|---|
| Order | `OrderPaid.v1` | order/tenant/branch, thời gian/ngày kinh doanh, các giá trị tổng, danh sách dòng món, ghi nhận nhân viên checkout, bóc tách khuyến mãi áp dụng, phiên bản nguồn |
| Order | Hoàn tiền / Điều chỉnh đã duyệt | order/branch nguồn, ID điều chỉnh/hoàn tiền, số tiền/dòng món, phiên bản/thời gian hiệu lực |
| Recommendation | `SuggestionShown.v1` | ID tương tác ổn định, gợi ý/món/quy tắc/mô hình, liên kết order/giỏ hàng, thời gian hiển thị |
| Recommendation | `SuggestionAccepted.v1` | ID tương tác hiển thị, ID tương tác chấp nhận, liên kết order/giỏ hàng, bằng chứng/thời gian chấp nhận |
| Loyalty/Promotion | Snapshot voucher/khuyến mãi áp dụng | Phiên bản khuyến mãi/quy tắc bất biến, nhóm xung đột, giá trị phân bổ, tham chiếu đơn hàng nguồn |
| Identity/Branch | Sự kiện metadata chi nhánh/nhân viên | Múi giờ chi nhánh, nhãn hiển thị an toàn, phiên bản/trạng thái nhân viên |

Phân rã khuyến mãi áp dụng trong `OrderPaid` là đầu vào chuẩn tắc duy nhất cho khuyến mãi. Các sự kiện khuyến mãi riêng biệt có thể làm giàu thêm nhãn nhưng không được tạo ra tác động doanh thu bổ sung.

### 7.2 Watermark và độ hoàn tất (Watermark & Completeness)

`projection_watermarks` theo dõi checkpoint nguồn, `watermarkVersion` đơn điệu tăng, phiên bản projection, thời gian cập nhật gần nhất, độ trễ và độ hoàn tất của các nguồn bắt buộc.

Trạng thái độ tươi mới:

```text
FRESH       độ trễ <= 1 phút và các nguồn bắt buộc đã hoàn tất
STALE       độ trễ > 1 phút nhưng read model vẫn sẵn sàng phục vụ
PARTIAL     thiếu một hoặc nhiều projection nguồn/chi nhánh bắt buộc
UNAVAILABLE read model / truy vấn không khả dụng
NO_DATA     truy vấn hợp lệ và hoàn tất nhưng không có sự kiện nào trong kỳ yêu cầu
```

Khi độ trễ > 5 phút, trạng thái vẫn là `STALE` kèm theo cảnh báo leo thang; không bao giờ trở thành `FRESH` cho đến khi watermark tăng tiến. Một bảng tổng hợp xuyên chi nhánh sẽ mang trạng thái `PARTIAL` khi thiếu bất kỳ chi nhánh/nguồn bắt buộc nào, trừ khi yêu cầu chỉ định rõ chỉ lấy dữ liệu khả dụng (chưa kích hoạt trong MVP).

### 7.3 Trạng thái Projection và tính nguyên tử

Trạng thái Event Inbox:

`RECEIVED → VALIDATING → APPLIED | IGNORED_DUPLICATE | RETRY_WAIT | DLQ`

Trạng thái Rebuild:

`REQUESTED → RUNNING → PAUSED | COMPLETED | FAILED | CANCELLED`

Transaction của projection handler bao gồm: dedupe trong inbox, biến đổi read model, tăng tiến watermark và tín hiệu outbox/hủy cache. Transaction thất bại sẽ không tăng tiến watermark. Quá trình rebuild ghi vào `projection_version` mục tiêu và hoán đổi con trỏ hiện hành nguyên tử sau khi đã kiểm tra xác thực.

## 8. Kiến Trúc API và Bộ Nhớ Đệm

### 8.1 Các module Query API

- `ReportScopeResolver`: Nhận quyết định từ Identity và chuẩn hóa phạm vi.
- `ReportFilterPolicy`: Xác thực ngày/khoảng thời gian/chiều phân tích/giới hạn/múi giờ.
- `ReportQueryService`: Truy vấn read model chuyên biệt theo endpoint và ánh xạ chỉ số.
- `DataQualityService`: Đóng gói phong bì watermark/độ tươi mới/độ hoàn tất.
- `ReportCacheAdapter`: Quản lý namespace khóa Redis, TTL, ETag và hủy bỏ cache.
- `ReportAuditPublisher`: Phát hành bằng chứng truy cập/từ chối/rebuild đã ẩn PII.

### 8.2 Cấu trúc Khóa Cache

```text
report:v1:{tenantId}:{scopeType}:{scopeHash}:{authorizationVersion}
  :{endpoint}:{normalizedFilterHash}:{calculationVersion}:{watermarkVersion}
```

TTL tối đa là 15 giây. Phân quyền luôn diễn ra trước khi tra cứu cache. Cơ chế hủy bỏ cache theo namespace watermark ngăn việc trả về dữ liệu cũ vượt quá chính sách sau khi đã có điều chỉnh. Khi cache hit, hệ thống vẫn trả về phong bì chất lượng dữ liệu đã lưu; không tự ý tạo ra timestamp mới hơn.

### 8.3 Ranh giới mở rộng Athena

Athena không được gọi bởi các API dashboard MVP. Một adapter `HistoricalReportAdapter` trong tương lai chỉ có thể định tuyến truy vấn khi:

- Khoảng thời gian yêu cầu vượt quá ngưỡng đo lường thực tế của PostgreSQL;
- Schema đầu ra và từ điển chỉ số hoàn toàn trùng khớp;
- Phân quyền tenant/chi nhánh được truyền đạt đầy đủ;
- Chính sách chi phí, độ tươi mới và SLO được xác định tường minh;
- Kết quả truy vấn ghi rõ nguồn `ATHENA` và watermark truy vấn tương ứng.

## 9. Kiến Trúc Dashboard

Dashboard Angular sử dụng một shell duy nhất tích hợp bộ chọn phạm vi nhận biết vai trò và các adapter truy vấn cấp widget:

- Overview KPI adapter.
- Daily/branch/hourly chart adapters.
- Top-items table adapter.
- Employee/promotion/recommendation table adapters.
- Trạng thái bộ lọc và con trỏ phân trang dùng chung.
- Banner chất lượng dữ liệu dùng chung và timestamp độ tươi mới.

Giao diện người dùng không gộp các chỉ số có watermark nguồn khác nhau vào chung một thẻ "tươi mới". Nếu các widget có watermark khác nhau, từng widget sẽ mang trạng thái riêng hoặc màn hình tổng quan sẽ báo cáo theo nguồn bắt buộc có độ tươi mới thấp nhất.

## 10. Độ Tin Cậy và Vận Hành (Reliability & Operations)

- EventBridge/SQS đảm bảo at-least-once, retry lũy thừa (exponential retry), có DLQ riêng cho từng consumer.
- Dedupe inbox bằng ID sự kiện / khóa nguồn.
- Timeout cho Query API và giới hạn kết quả trả về có chặn trên.
- Lỗi Redis tự động fallback về PostgreSQL; lỗi database trả về `UNAVAILABLE`, tuyệt đối không fallback sang DB của producer.
- Cảnh báo độ trễ projection ở mức 1 phút (warning) và leo thang ở mức 5 phút (escalation).
- Rebuild/replay đòi hỏi quyền vận hành viên hạn chế và có tham chiếu audit.
- Áp lực tải ngược (backpressure) được giám sát qua độ sâu hàng đợi, mức đồng thời của worker và chỉ số lag.
- Xung đột/bất tương thích schema được cách ly kiểm dịch; không xử lý tùy tiện (best-effort) các phiên bản event lạ.

## 11. Các Quyết Định Kiến Trúc (ADRs)

| ADR | Quyết định | Giải pháp thay thế bị bác bỏ | Lý do |
|---|---|---|---|
| REP-01 | PostgreSQL pre-aggregated read model cho MVP | Ưu tiên Athena ngay từ đầu, query projection thô | Đáp ứng độ trễ/độ tươi mới của dashboard với độ phức tạp có kiểm soát |
| REP-02 | Projection hướng sự kiện kèm inbox/dedupe | Fan-out đồng bộ, join DB chéo service | Giữ quyền tự chủ service và hỗ trợ replay |
| REP-03 | Quyết định Identity trực tuyến + điều kiện lọc repository | Cache quyền lâu dài, chỉ kiểm tra scope trên UI | Xử lý thu hồi quyền tức thì và phòng thủ chiều sâu |
| REP-04 | Chế độ xem hiện hành đã điều chỉnh kèm phả hệ watermark | Chỉ lưu snapshot dashboard bất biến | Vận hành viên cần số liệu hiện hành nhưng vẫn bảo toàn phả hệ audit |
| REP-05 | Snapshot trong OrderPaid là chuẩn tắc cho doanh thu/khuyến mãi | Tính toán lại từ DB Payment/Promotion | Tránh sai lệch chỉ số và diễn giải lại quy tắc quá khứ |
| REP-06 | Nhóm đối chứng AOV gợi ý ở cấp độ đơn hàng | Cộng dồn mức tăng trưởng theo từng gợi ý riêng | Tránh tính trùng lặp với đơn hàng chấp nhận nhiều gợi ý |
| REP-07 | Redis cache an toàn phạm vi trong 15s | Không dùng cache, cache thời gian 1 phút | Cân bằng mục tiêu p95 và độ tươi mới ≤1 phút |
| REP-08 | Hoãn triển khai Athena như phần mở rộng | Đưa Athena vào hot path MVP | Chi phí, độ trễ và SLO chưa được đo lường thực tế |

## 12. Các Luồng Tuần Tự (Sequence Flows)

Biểu đồ tuần tự được xây dựng dưới dạng L1 (cấp độ hệ thống) + L2 (cấp độ thành phần backend nội bộ) sau khi người dùng xác nhận.

### Các luồng đề xuất

1. **P0 OrderPaid projection thành công** — Order outbox → EventBridge/SQS → Reporting inbox/handler → read models/watermark.
2. **Sự kiện trùng lặp / sai thứ tự** — dedupe hoặc xử lý điều chỉnh đơn điệu mà không tính trùng lặp.
3. **Truy vấn KPI Dashboard cache miss / hit** — Dashboard → Identity scope → policy → Redis/PostgreSQL → phản hồi kèm chất lượng dữ liệu.
4. **Quản lý truy vấn chi nhánh không có quyền / drill-down** — bộ lọc chi nhánh giả mạo hoặc membership bị thu hồi sẽ thất bại mà không liệt kê danh sách.
5. **Hiệu quả gợi ý từ chờ sang đã thanh toán** — SuggestionShown/Accepted → ghi nhận pending → OrderPaid → phân nhóm accepted/control.
6. **Projection doanh thu theo phiên bản khuyến mãi** — Snapshot khuyến mãi trong OrderPaid → phân bổ → read model khuyến mãi.
7. **Hoàn tiền / điều chỉnh đến muộn** — sự kiện điều chỉnh → cập nhật bảng tổng hợp hiện tại → hủy cache/watermark → dashboard hiển thị view hiện hành.
8. **Rebuild / phát lại projection** — yêu cầu từ vận hành viên → projection mục tiêu → xác thực → hoán đổi nguyên tử sang hiện hành hoặc thất bại/DLQ.

### Các bên tham gia L1 đề xuất

| Bên tham gia | Loại | Vai trò |
|---|---|---|
| Owner/Manager Dashboard | Client | Bộ lọc báo cáo / widget / hiển thị |
| Reporting Query API | Service | API đọc nhận biết phạm vi |
| Identity Access | Service | Quyết định phân quyền / phạm vi trực tuyến |
| Redis Cache | Hạ tầng | Cache ngắn hạn an toàn phạm vi |
| Reporting PostgreSQL | Cơ sở dữ liệu | Read model / inbox / watermark / trạng thái rebuild |
| Order Service | Service | Producer sinh `OrderPaid` / sự kiện điều chỉnh |
| Loyalty/Promotion Service | Service | Producer sinh snapshot / phiên bản khuyến mãi |
| Recommendation Service | Service | Producer sinh sự kiện hiển thị / chấp nhận gợi ý |
| EventBridge/SQS/DLQ | Message Broker | Phân phối bất đồng bộ / retry / kiểm dịch |
| Reporting Projection Worker | Worker | Xác thực / dedupe / project / rebuild |
| Audit/Observability | Consumer | Thu thập audit / metric / trace đã ẩn PII |

### Các bên tham gia L2 đề xuất

`ReportController`, `ReportScopeResolver`, `ReportPolicyEngine`, `ReportQueryService`, `ReportCacheAdapter`, `Report PostgreSQL`, `ProjectionConsumer`, `ProjectionHandler`, `WatermarkManager`, `RebuildWorker`, `IdentityClient` và `EventBridge/SQS adapter` sẽ được thu gọn về mức ≤7 bên tham gia trên mỗi biểu đồ.

## 13. Cổng Phê Duyệt Kiến Trúc (Architecture Approval Gate)

- **Trạng thái hiện tại:** Đã phê duyệt — tám luồng tuần tự và ranh giới các bên tham gia đã được xác nhận; các biểu đồ L1/L2 đã được tạo và xác thực.
- **Yêu cầu trước khi duyệt Hợp đồng (Contracts):** Người dùng phê duyệt Kiến trúc và các biểu đồ tuần tự.
- **Quy tắc triển khai:** Không viết code; Contracts phải cụ thể hóa các API schema, event schema, kịch bản migration read model, ngữ nghĩa cache và giao thức projection/rebuild.
