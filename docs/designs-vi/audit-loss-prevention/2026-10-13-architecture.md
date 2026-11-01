Started at: 2026/10/13 00:00:00
Finished at: 2026/10/13 00:00:00
Total time: 0 minutes
---

# Module 11 — Giám Sát & Chống Thất Thoát: Kiến Trúc (Architecture)

**Ngày:** 2026-10-13  
**Trạng thái:** Đã phê duyệt — người dùng đã phê duyệt Kiến trúc sau khi xác nhận các luồng tuần tự  
**Tầm nhìn:** [Audit & Loss Prevention Vision](file:///d:/vibe-code/pos/docs/designs-vi/audit-loss-prevention/2026-10-13-vision.md)  
**Yêu cầu:** [Audit & Loss Prevention Requirements](file:///d:/vibe-code/pos/docs/designs-vi/audit-loss-prevention/2026-10-13-requirements.md)  
**Đánh giá yêu cầu:** [Audit & Loss Prevention Requirements Review](file:///d:/vibe-code/pos/docs/designs-vi/audit-loss-prevention/2026-10-13-requirements-review.md)  
**Biểu đồ tuần tự:** [Audit & Loss Prevention Sequence Diagrams](file:///d:/vibe-code/pos/docs/designs-vi/audit-loss-prevention/2026-10-13-sequence-diagrams.adoc)

## 1. Tóm Tắt Quyết Định Kiến Trúc

Audit & Loss Prevention là một service tập trung, định hướng đọc và lưu trữ bằng chứng (read/evidence-oriented), sở hữu cơ sở dữ liệu riêng. Service nguồn phát hành các dữ kiện bảo mật/nghiệp vụ chuyên biệt theo từng hành động qua Transactional Outbox; Audit chuẩn hóa và lưu trữ bằng chứng dạng chỉ ghi thêm (append-only). Worker phát hiện bất thường P2 chỉ tạo các phát hiện/vụ việc (finding/case), không tham gia vào luồng xử lý nghiệp vụ đồng bộ và không có quyền ngăn chặn thao tác ở nguồn.

| Quyết định | Lựa chọn | Lý do |
|---|---|---|
| Quyền sở hữu Audit | Dedicated Audit service + PostgreSQL | Tạo một nguồn duy nhất cho bằng chứng, truy vấn và lưu trữ; không để từng service tự định nghĩa dòng thời gian riêng |
| Dữ kiện nguồn chuẩn tắc | Các sự kiện nguồn chuyên biệt theo hành động là chuẩn tắc cho mutation; Audit tạo bằng chứng đã chuẩn hóa | Giữ Order/Payment/Shift/POS Device làm chủ trạng thái và tránh nhân đôi bằng chứng qua sự kiện chung |
| Định tuyến audit chung | `AuditRecorded.v1` dùng cho các sự kiện vận hành/bảo mật chung không có sự kiện nghiệp vụ nguồn tương ứng | Tương thích với nền tảng gốc nhưng không dùng thêm cho cùng một thao tác hủy/giảm giá/két tiền |
| Cơ chế thu nhận (Ingestion) | EventBridge → SQS `pos-audit-events` → Audit consumer/inbox | Đảm bảo at-least-once, retry/DLQ, tách rời service nguồn và hỗ trợ phát lại |
| Mô hình lưu trữ | `audit_events` bất biến + query projection + các bảng vụ việc/hành động | Tách bằng chứng pháp lý khỏi hot path lọc/truy vấn; các điều chỉnh mang tính chỉ ghi thêm (append-only) |
| Tính toàn vẹn (Integrity) | Phân quyền/ràng buộc append-only ở DB + bản tóm tắt băm SHA-256 theo lô / checkpoint | Đạt khả năng phát hiện can thiệp cho MVP mà không phụ thuộc hạ tầng WORM ngoài; digest có phả hệ và kiểm tra xác minh |
| Truy vấn | REST/OpenAPI 3.1, tương tác có chặn 90 ngày, xuất file bất đồng bộ cho dữ liệu cũ hơn | Bảo vệ độ trễ và dữ liệu lưu trữ; API an toàn theo phạm vi phân quyền |
| Phát hiện P2 | Quy tắc có phiên bản + đường cơ sở nhân viên/chi nhánh/thời gian trong PostgreSQL | Dễ giải thích, đơn định và phù hợp với bài toán khởi động nguội; chưa cần dùng ML |
| Quy trình vụ việc | Chuyển trạng thái append-only + kiểm tra phiên bản lạc quan (CAS); Manager điều tra, Owner giải quyết/bác bỏ | Phân tách trách nhiệm rõ ràng và bảo toàn phả hệ điều tra |
| Lưu trữ dài hạn / Xuất file | Object storage được mã hóa cho các file xuất/archive có giới hạn, vòng đời được kiểm toán | Không trả file dung lượng lớn qua request đồng bộ; lưu trữ pháp lý/hết hạn được quản lý riêng |
| Phân quyền | Quyết định trực tuyến từ Identity + điều kiện lọc repository + hash phạm vi/phiên bản trong cache | Phòng thủ chiều sâu (defense-in-depth) cho việc cô lập đa tenant/chi nhánh |
| Thời gian / Định danh | UUID v7, timestamp UTC, cửa sổ kinh doanh cục bộ chi nhánh | Nhất quán nền tảng và đường cơ sở bất thường tính đúng theo ca/giờ địa phương |

## 2. Ranh Giới Service và Sơ Đồ Thành Phần (Component Map)

```text
Order / Promotion / Payment / Shift / POS Device / Identity
  └─ transactional outbox
        └─ EventBridge
              └─ SQS pos-audit-events + DLQ
                    └─ Audit Ingestor
                         ├─ Schema/Redaction Validator
                         ├─ Inbox + Dedupe
                         ├─ Immutable Evidence Writer ──┐
                         ├─ Projection Writer             │
                         └─ Watermark/Completeness       │
                                                         ▼
                                                   Audit PostgreSQL
                                                         │
             ┌───────────────────────────────────────────┼────────────────┐
             ▼                                           ▼                ▼
       Query API + Policy                         Digest Worker     Loss Worker
             │                                           │                │
       Angular Audit Console                      digest/checkpoints  rules/baselines
             │                                                            │
             └───────────────────────────────┐                            ▼
                                             ▼                     Findings/Case Service
                                      Identity decision                     │
                                             │                              ▼
                                      Scope-safe response             Owner/Manager console
```

### 2.1 Các Thành Phần

| Thành phần | Trách nhiệm | Điều cấm (Must not do) |
|---|---|---|
| Source producers | Phát hành các dữ kiện kiểm toán chuyên biệt trong outbox nguồn sau khi commit | Ghi trực tiếp vào DB của Audit hoặc tự bịa ra thẩm quyền Identity |
| EventBridge/SQS | Định tuyến, retry, lưu trữ tạm, DLQ và cung cấp metadata phân phối | Làm sai lệch bằng chứng hoặc âm thầm sắp xếp lại thứ tự sự kiện nguồn |
| Audit Ingestor | Xác thực phong bì/schema/làm sạch dữ liệu, dedupe, lưu bằng chứng/projection nguyên tử | Đánh giá lại quy tắc nghiệp vụ hoặc suy đoán mutation thành công từ log |
| Immutable Evidence Writer | Thêm mới bằng chứng đã chuẩn hóa và phả hệ nguồn dạng append-only | Cập nhật hoặc xóa các dòng sự kiện lịch sử |
| Query Projection Writer | Xây dựng các view/chỉ mục dòng thời gian đã giải chuẩn an toàn và trạng thái chất lượng dữ liệu | Trở thành nguồn sự thật thứ hai |
| Watermark/Completeness | Theo dõi checkpoint nguồn/projection, khoảng trống dữ liệu, độ trễ và nguồn bị thiếu | Đánh dấu nguồn bị thiếu thành hoạt động sạch / không có vi phạm |
| Query API + Policy | Phân giải phân quyền, xác thực bộ lọc, truy vấn projection/bằng chứng và ẩn dữ liệu nhạy cảm | Chấp nhận tenant/chi nhánh/tác nhân từ client với tư cách là thẩm quyền |
| Digest Worker | Chuẩn hóa lô sự kiện có thứ tự, tính toán/xác minh digest/checkpoint SHA-256 | Sửa chữa sự không khớp bằng cách ghi đè lên bằng chứng |
| Retention/Export Worker | Thực thi lưu trữ/lưu trữ pháp lý, tạo các file xuất/archive bất đồng bộ được mã hóa | Xóa dữ liệu đang bị lưu trữ pháp lý hoặc phục vụ xuất file đồng bộ không giới hạn |
| Loss Prevention Worker | Cụ thể hóa đường cơ sở, đánh giá các quy tắc có phiên bản, tạo/chỉnh sửa phát hiện | Chặn thao tác của Order/Payment/Shift/POS |
| Case Service | Chuyển đổi trạng thái bằng CAS, quản lý tham chiếu snapshot bằng chứng và thao tác vụ việc | Giải quyết/bác bỏ vụ việc mà thiếu quyền của Owner hoặc thiếu lý do |
| Identity Scope Client | Phân giải quyền hạn/phạm vi/phiên bản hiện hành | Lưu trữ hoặc trở thành thẩm quyền snapshot tác nhân lịch sử |
| Audit PostgreSQL | Sở hữu độc quyền bằng chứng, projection, chính sách, phát hiện, vụ việc và thao tác | Bị chia sẻ kết nối trực tiếp bởi các service nguồn |

## 3. Kiến Trúc Sự Kiện Chuẩn Tắc và Quyền Sở Hữu

### 3.1 Quy tắc nguồn sự thật (Source-of-truth Rule)

Đối với một thao tác thay đổi nghiệp vụ, sự kiện nguồn chuyên biệt theo hành động là chuẩn tắc:

```text
Order.cancel line / apply discount
  → Order transaction + outbox(OrderLineCancelled / DiscountApplied)
  → Audit tiêu thụ và chuẩn hóa thành một dòng AuditEvidence duy nhất

POS Device/Shift drawer operation
  → Transaction thao tác/kết quả két tiền + outbox(CashDrawerOpened)
  → Audit tiêu thụ và chuẩn hóa thành một dòng AuditEvidence duy nhất

Thao tác bảo mật / ủy quyền chung
  → Transaction nguồn + outbox(AuditRecorded.v1)
  → Audit tiêu thụ thành một dòng AuditEvidence duy nhất
```

Cùng một thao tác nguồn tuyệt đối không được phát hành cả sự kiện nghiệp vụ chuyên biệt lẫn sự kiện chung `AuditRecorded.v1` để Audit tiêu thụ. Nếu nền tảng gốc duy trì `AuditRecorded.v1` như một phong bì tương thích, nó phải mang `sourceEventId`/`sourceEventType` và registry sự kiện phải chỉ rõ route nào là chuẩn tắc.

### 3.2 Ranh giới chuẩn hóa (Normalization Boundary)

Audit chuẩn hóa payload từ các producer khác nhau thành cấu trúc `AuditEvidence` bao gồm:

- Phả hệ chung của tenant/chi nhánh/nguồn/aggregate/sự kiện;
- Snapshot tác nhân (actor) và thu ngân thao tác thực tế (effective operator);
- Hành động/tài nguyên/kết quả/lý do;
- Tóm tắt trước/sau có chặn độ dài và đã làm sạch nhạy cảm;
- Số tiền/phân bổ dưới dạng số nguyên minor units + `currency`;
- Tham chiếu phiên bản thiết bị/ca/đơn hàng/dòng/khuyến mãi;
- `redactionVersion`, `normalizationVersion` và `qualityCode`.

Bằng chứng đã chuẩn hóa là biểu diễn nội bộ của Audit. Các consumer hạ nguồn chỉ sử dụng sự kiện `AuditEvidenceRecorded.v1` cho mục đích tham chiếu/tóm tắt an toàn; nó không bao giờ là lệnh ủy quyền hay nguồn doanh thu.

## 4. Kiến Trúc Dữ Liệu

### 4.1 Quyền sở hữu PostgreSQL

Audit sử dụng cơ sở dữ liệu PostgreSQL chuyên biệt. Mọi bảng đều mang `tenant_id`; các dòng theo phạm vi chi nhánh mang `branch_id`; các câu truy vấn repository luôn bao gồm điều kiện tenant và các chi nhánh đã phân giải.

| Bảng / Aggregate | Mục đích | Tính biến đổi (Mutability) |
|---|---|---|
| `audit_events` | Bằng chứng chuẩn tắc đã chuẩn hóa, phả hệ nguồn, tóm tắt payload đã làm sạch | Chỉ chèn (Insert-only); điều chỉnh là dòng liên kết mới |
| `audit_event_links` | Tham chiếu nguồn/điều chỉnh/bản chỉnh sửa/bằng chứng liên quan | Chỉ chèn (Insert-only) |
| `audit_event_projection` | Dòng thời gian/chỉ mục được tối ưu hóa cho truy vấn kèm nhãn an toàn | Trạng thái phái sinh có thể tái tạo (Rebuildable) |
| `inbox_events` | Dedupe at-least-once, phiên bản nguồn/trạng thái/lỗi | Chuyển trạng thái kèm lịch sử xử lý bất biến |
| `projection_watermarks` | Độ trễ nguồn/projection, độ hoàn tất, checkpoint áp dụng gần nhất | Con trỏ hiện hành + telemetry chuyển đổi |
| `digest_batches` | Khoảng lô, chuẩn hóa/phiên bản, SHA-256 digest, digest/checkpoint trước, trạng thái xác minh | Metadata lô dạng append-only; trạng thái bổ sung bản ghi xác minh |
| `audit_policies` | Các phiên bản chính sách làm sạch, allowlist hành động, lưu trữ và truy vấn | Ban hành có phiên bản; không sửa đổi chính sách lịch sử |
| `anomaly_rule_versions` | Nhóm quy tắc/cấu hình/ngưỡng/chiều đường cơ sở/mức độ nghiêm trọng | Các phiên bản ban hành bất biến |
| `anomaly_baselines` | Thống kê theo nhân viên/chi nhánh/cửa sổ thời gian và trạng thái khởi động | Cụ thể hóa có phiên bản |
| `anomaly_findings` | Khóa phát hiện đơn định, tham chiếu quy tắc/bằng chứng/baseline, mức độ nghiêm trọng/trạng thái | Có quản lý bản chỉnh sửa (Revisioned); không xóa mang tính phá hủy |
| `case_actions` | Các bước chuyển trạng thái xác nhận/điều tra/giải quyết/bác bỏ chỉ ghi thêm | Chỉ chèn (Insert-only) |
| `audit_exports` | Thao tác xuất bất đồng bộ / trạng thái / tham chiếu object / thời hạn hết hạn | Máy trạng thái + dấu vết kiểm toán |
| `legal_holds` | Phạm vi/loại dữ liệu/người tạo/lý do/thời gian hiệu lực | Vòng đời có phiên bản |
| `retention_operations` | Đánh giá lưu trữ dài hạn / xóa / lưu trữ pháp lý và kết quả thực hiện | Bằng chứng thao tác dạng append-only |
| `outbox_messages` | Các sự kiện Audit/phát hiện/vụ việc/sức khỏe phát hành ra ngoài | Trạng thái outbox chuẩn |

### 4.2 Phân Vùng và Chỉ Mục (Partitioning & Indexes)

- Các bảng `audit_events`, `audit_event_projection`, `case_actions` và bảng có lưu lượng lớn `inbox_events` được thiết kế sẵn sàng cho phân vùng theo khoảng (RANGE partitioning) hàng tháng theo trường `occurred_at`/`created_at`; chỉ kích hoạt/chia kích thước phân vùng sau khi đã đo lường khối lượng sự kiện thực tế, đồng thời bảo toàn quy trình quản lý vòng đời 7 năm.
- Khóa chính/khóa duy nhất bao gồm cả ngày phân vùng ở những nơi PostgreSQL yêu cầu; tính duy nhất của sự kiện trên toàn cầu được thực thi qua registry khóa nguồn chuyên biệt hoặc chiến lược chỉ mục duy nhất theo phạm vi.
- Các chỉ mục tối thiểu:
  - `(tenant_id, branch_id, occurred_at DESC, id)` cho dòng thời gian;
  - `(tenant_id, branch_id, action, occurred_at DESC)` cho bộ lọc hành động;
  - `(tenant_id, branch_id, actor_key, occurred_at DESC)` cho điều tra nhân viên;
  - `(tenant_id, branch_id, shift_id, occurred_at DESC)` cho điều tra ca làm việc / thiết bị;
  - `(tenant_id, status, severity, detected_at DESC)` cho danh sách phát hiện / vụ việc;
  - unique `(source_name, source_event_id)` và `(tenant_id, source_key)` tương ứng để khử trùng lặp;
  - `(tenant_id, scope_key, projection_name, watermark_version)` cho kiểm tra sức khỏe và phát lại;
  - `(tenant_id, active_from, active_to, data_class)` cho lưu trữ pháp lý.
- Mọi cột trong migration bắt buộc phải có `COMMENT ON COLUMN`; tuyệt đối không có service nguồn nào giữ khóa ngoại (foreign key) trỏ vào DB của Audit.

### 4.3 Kiến Trúc Bản Tóm Tắt Băm (Digest Architecture)

Đối với mỗi lô theo tenant / phạm vi / thời gian:

1. Chọn các dòng bằng chứng đã chuẩn hóa đã commit theo thứ tự tuple đơn định `(occurred_at, event_id)`.
2. Tuần tự hóa tập hợp các trường chuẩn tắc thuộc danh mục cho phép bằng cách sử dụng `canonicalizationVersion`.
3. Tính toán mã băm SHA-256 trên các bản ghi sự kiện có sắp xếp; đính kèm `previousDigest` khi liên kết chuỗi các lô cho cùng một phạm vi.
4. Lưu vào bảng `digest_batches` kèm: số lượng sự kiện, ID sự kiện đầu/cuối, watermark nguồn/projection, thuật toán/phiên bản, mã digest và thời điểm tạo.
5. Worker xác minh sẽ tính toán lại một lô có giới hạn và ghi nhận trạng thái `VERIFIED` hoặc `MISMATCH`; trường hợp không khớp (mismatch) sẽ tạo phát hiện bảo mật và không được ghi đè lên bằng chứng.

Digest là bằng chứng toàn vẹn, không phải phân quyền. Việc rebuild projection sẽ tạo ra phiên bản projection mới và tham chiếu đến cùng phả hệ bằng chứng/digest bất biến.

## 5. Kiến Trúc Giao Tiếp (Communication Architecture)

### 5.1 Thu nhận bất đồng bộ (Asynchronous Ingest)

- Service nguồn ghi nhận thay đổi nghiệp vụ và bản ghi outbox trong cùng một transaction cục bộ.
- EventBridge định tuyến các sự kiện hành động/bảo mật tới hàng đợi `pos-audit-events`.
- SQS phân phối at-least-once tới consumer của Audit; áp dụng retry lũy thừa có chặn, sau đó chuyển sang `pos-audit-events-dlq`.
- Audit chỉ gửi ACK sau khi transaction bao gồm: dedupe inbox, lưu bằng chứng chuẩn hóa, cập nhật projection và cập nhật watermark đã commit thành công.
- Phiên bản schema không xác định hoặc lỗi làm sạch dữ liệu sẽ bị cách ly kiểm dịch; tuyệt đối không tự ý đoán định các trường.

### 5.2 Luồng truy vấn và xử lý vụ việc đồng bộ

- Client Angular gọi API Audit kèm Cognito JWT; quyết định phân quyền từ Identity được phân giải trước khi chạm vào cache hoặc database.
- Query API kiểm tra hợp lệ các bộ lọc có chặn và ánh xạ sang điều kiện lọc của repository; việc lấy chi tiết sự kiện / bằng chứng / vụ việc lặp lại bước kiểm tra phạm vi.
- Thay đổi trạng thái vụ việc sử dụng Idempotency key + kiểm tra phiên bản `If-Match`/CAS; chuyển đổi thành công sẽ ghi vào `case_actions`, cập nhật con trỏ vụ việc hiện hành và ghi outbox trong cùng một transaction.
- Sự cố truy vấn của Audit không bao giờ chặn thao tác thay đổi ở service nguồn; outbox và hàng đợi nguồn tiếp tục bảo toàn ý định kiểm toán.

### 5.3 Xuất file / Lưu trữ dài hạn / Xóa dữ liệu

- Lệnh xuất file tạo một tác vụ có tính lũy kế trong `audit_exports` chỉ rõ tenant, chi nhánh, loại dữ liệu và khoảng ngày.
- Worker kiểm tra lại phân quyền và các lệnh lưu trữ pháp lý, stream các bản ghi đã làm sạch có giới hạn lên object storage được mã hóa, ghi mã checksum/thời hạn hết hạn và phát sự kiện hoàn tất.
- Việc tải file sử dụng đường dẫn tham chiếu có phân quyền và tồn tại ngắn hạn; không dùng public bucket hay URL object vô thời hạn.
- Worker lưu trữ đánh giá các lệnh lưu trữ pháp lý trước khi tiến hành archive hay xóa; mọi thao tác đều ghi nhận vào `retention_operations` và sinh bằng chứng kiểm toán.

## 6. Kiến Trúc Phát Hiện Bất Thường (Anomaly Architecture)

```text
Audit evidence + approved Order/Payment/Shift facts
        │ normalized event stream / query projection
        ▼
Feature Aggregator
  ├─ employee/branch/time-window counters
  ├─ monetary/count ratios
  ├─ shift/device context
  └─ data-quality/warm-up state
        ▼
Versioned Rule Evaluator
  ├─ cancel spike
  ├─ discount spike
  ├─ drawer frequency/outside shift
  └─ repeated order/shift actions
        ▼
Finding Writer (idempotent key + evidence snapshot)
        ▼
Case Service → Owner/Manager workflow
```

### 6.1 Chính sách đường cơ sở (Baseline Policy)

- Đường cơ sở mang tính cục bộ theo chi nhánh và nhận biết múi giờ; các chiều mặc định là thu ngân thao tác thực tế, chi nhánh, khung giờ/buổi trong ngày và nhóm hành động.
- Một chính sách quy tắc công bố rõ: thời gian khởi động (warm-up), kích thước mẫu tối thiểu, cửa sổ so sánh, ngưỡng kích hoạt, ánh xạ mức độ nghiêm trọng, cơ chế giảm nhiễu/hạ nhiệt và `ruleVersion`.
- Nhân viên/chi nhánh/ca mới hoặc khi thiếu dữ kiện nguồn sẽ sinh trạng thái `INSUFFICIENT_BASELINE`; worker không tạo phát hiện với độ tin cậy cao.
- Khóa phát hiện mang tính đơn định trên bộ `(tenant, branch, subject, ruleVersion, observationWindow, evidenceVersion)`.
- Dữ kiện đến sau tạo ra phiên bản chỉnh sửa (revision) / phiên bản bằng chứng liên kết; phát hiện trước đó vẫn được lưu giữ phục vụ kiểm toán.

### 6.2 Máy trạng thái vụ việc (Case State Machine)

```text
OPEN → ACKNOWLEDGED → INVESTIGATING → RESOLVED
                         │             └→ OPEN (mở lại bởi Owner kèm lý do)
                         └────────────→ DISMISSED (Owner kèm lý do)
```

- Manager chỉ được phép chuyển trạng thái `OPEN → ACKNOWLEDGED → INVESTIGATING` trong phạm vi chi nhánh được giao.
- Owner có thể thực hiện toàn bộ các bước chuyển đổi và là người duy nhất có thẩm quyền chuyển sang `RESOLVED` hoặc `DISMISSED`.
- Mọi bước chuyển đổi đều sử dụng kiểm tra phiên bản CAS và tạo một dòng bất biến trong `case_actions` kèm sự kiện `AuditCaseActionRecorded.v1`.
- Trạng thái vụ việc không bao giờ xóa bỏ phát hiện hay bằng chứng; ghi chú giải quyết có giới hạn độ dài và được làm sạch PII.

## 7. Kiến Trúc Bảo Mật và Quyền Riêng Tư

- Identity là thẩm quyền trực tuyến; Audit chỉ lưu trữ snapshot tác nhân/membership lịch sử phục vụ hiển thị và điều tra hồi tố.
- Thứ tự thực thi phân quyền: Xác thực JWT/workload → Phân giải quyền/phạm vi hiện hành → Chuẩn hóa bộ lọc → Tra cứu cache (nếu bật) → Mệnh đề lọc tenant/chi nhánh tại repository → Làm sạch dữ liệu trước khi trả về client.
- Không có API công khai nào chấp nhận các trường tenant/chi nhánh/tác nhân/kết quả/trước-sau do client tự gửi làm thẩm quyền.
- API Audit tuyệt đối không để lộ payload nguồn thô, số điện thoại, mã PIN, JWT, chữ ký VNPAY, thông tin thẻ hay toàn bộ ghi chú đơn hàng/khách hàng.
- Bộ nhớ đệm (nếu được kích hoạt cho danh sách dòng thời gian/phát hiện) chỉ tồn tại ngắn hạn (≤15s), đánh khóa theo hash phạm vi + phiên bản phân quyền + hash bộ lọc; cấm browser/CDN cache dữ liệu bằng chứng được bảo vệ.
- Mọi hành vi bị từ chối truy cập kiểm toán, xuất file, phát lại, xác minh digest, lưu trữ pháp lý và thao tác vụ việc bản thân chúng đều trở thành bằng chứng kiểm toán.
- Thông tin xác thực của vận hành viên / phát lại sử dụng workload identity/mTLS và quyền tối thiểu `audit:operate`; cấm kết nối SQL trực tiếp từ POS hay client công khai.

## 8. Xử Lý Sự Cố, Tính Nhất Quán và Phục Hồi

| Sự cố phát sinh | Phản ứng của kiến trúc |
|---|---|
| Trễ outbox ở producer | Thay đổi ở nguồn vẫn đã commit; watermark/chất lượng dữ liệu của Audit hiển thị độ trễ khi sự kiện đến sau; đối soát phát cảnh báo khoảng trống |
| Phân phối trùng lặp | Khóa nguồn / ID sự kiện duy nhất trong inbox giúp biến thao tác thành no-op; gửi ACK SQS sau khi transaction hoàn tất |
| Phiên bản nguồn sai thứ tự | Giữ lại bằng chứng mới hơn; đưa vào hàng đợi giữ/chờ hoặc phát lại theo chính sách phiên bản producer; không bao giờ ghi đè lịch sử |
| Lỗi schema / làm sạch dữ liệu | Cách ly / chuyển vào DLQ; ghi log lỗi an toàn và phát cảnh báo tới producer; không lưu trữ payload không an toàn |
| Database của Audit không khả dụng | Retry qua hàng đợi / DLQ; API trả về trạng thái không khả dụng; các service nguồn tiếp tục hoạt động độc lập |
| Hỏng projection truy vấn | Rebuild lại projection từ bảng bất biến `audit_events`; bằng chứng nguồn và digest không bị ghi đè |
| Lệch mã băm digest | Đóng băng kết quả xác minh, tạo phát hiện toàn vẹn, bảo toàn dữ liệu dòng và yêu cầu điều tra có phân quyền |
| Sự cố worker quy tắc | Giữ nguyên trạng thái baseline/phát hiện trước đó; retry/DLQ; không gây ảnh hưởng đến thao tác ở nguồn |
| Xung đột thao tác vụ việc đồng thời | Cơ chế CAS / `If-Match` trả về mã lỗi `409`; không cho phép thao tác bán phần hay chuyển trạng thái trùng lặp |
| Xung đột lưu trữ pháp lý | Lệnh lưu trữ pháp lý luôn thắng; tác vụ dọn dẹp ghi nhận thao tác bị bỏ qua và kích hoạt cảnh báo |

## 9. Khả Năng Quan Sát và Vận Hành (Observability)

### Các chỉ số (Metrics)

- `audit_ingest_lag_seconds`, `audit_projection_lag_seconds`, `audit_missing_source_count`;
- `audit_event_processed_total`, số lượng trùng lặp, từ chối schema, từ chối làm sạch dữ liệu, retry và số lượng DLQ;
- Số lượng lô digest, độ trễ xác minh, số lượng không khớp và thời gian chưa xác minh;
- Độ trễ API p50/p95, tỷ lệ mã lỗi 401/403/404/409/422/429/503, thời gian xuất file / hết hạn / thất bại;
- Số lượt đánh giá bất thường, baseline chưa đủ dữ liệu, phát hiện theo quy tắc/mức độ nghiêm trọng, tỷ lệ trùng lặp/chỉnh sửa/bác bỏ;
- Xung đột/từ chối chuyển trạng thái vụ việc và kết quả của các tác vụ lưu trữ pháp lý / dọn dẹp lưu trữ.

### Nhật ký và Truy vết (Logs & Traces)

- Log có cấu trúc chứa correlation/causation ID, tham chiếu an toàn tenant/chi nhánh, ID sự kiện/nguồn, thao tác và kết quả.
- Tuyệt đối không log payload sự kiện thô, văn bản lý do chưa làm sạch, JWT/PIN, số điện thoại, chữ ký thanh toán hay nội dung file xuất.
- Trace span bao phủ toàn diện: outbox của producer, phân phối qua EventBridge/SQS, transaction thu nhận, phân quyền truy vấn, worker digest, đánh giá quy tắc và chuyển trạng thái vụ việc.

### Cảnh báo và Runbook (Alerts & Runbooks)

- Cảnh báo khi độ trễ kiểm toán >60 giây, hàng đợi DLQ tăng trưởng, xuất hiện khoảng trống nguồn, lệch digest toàn vẹn, digest quá hạn xác minh, rò rỉ/lỗi xuất file, đột biến từ chối phân quyền và tỷ lệ từ chối schema cao.
- Runbook hướng dẫn chi tiết: phát lại theo cửa sổ nguồn, rebuild projection, xác minh digest, áp dụng lưu trữ pháp lý, thu hồi file xuất, cách ly kiểm dịch DLQ và quy trình ứng phó sự cố rò rỉ dữ liệu riêng tư.

## 10. Các Quyết Định Kiến Trúc (ADRs)

| ADR | Quyết định | Giải pháp thay thế bị bác bỏ | Lý do |
|---|---|---|---|
| AUD-ADR-01 | Dedicated Audit service sở hữu bằng chứng/truy vấn/vụ việc | Từng service tự sở hữu audit riêng; Reporting sở hữu audit | Đảm bảo tính nhất quán về thời hạn lưu trữ, phân quyền và dòng thời gian pháp lý |
| AUD-ADR-02 | Sự kiện hành động nguồn là chuẩn tắc; Audit chuẩn hóa thành một dòng bằng chứng | Vừa phát sự kiện nguồn vừa phát AuditRecorded chung; Audit tự đoán từ log | Ngăn chặn nhân đôi bằng chứng và bảo toàn tính tự chủ của service nguồn |
| AUD-ADR-03 | PostgreSQL lưu trữ bằng chứng bất biến + projection | Dùng chung DB; chỉ lưu trữ event mà không có read model truy vấn | Đáp ứng trải nghiệm truy vấn 90 ngày tối ưu với đường dẫn đọc có thể tái tạo |
| AUD-ADR-04 | Append-only + SHA-256 batch digest / checkpoint | Sử dụng hạ tầng WORM ngoài cho MVP; chỉ dùng append-only ở ứng dụng không kiểm tra | Đáp ứng nhu cầu phát hiện can thiệp của MVP với chi phí và vận hành AWS/PostgreSQL hợp lý |
| AUD-ADR-05 | Quy tắc + đường cơ sở thống kê, không dùng ML và không tự động khóa | Ưu tiên áp dụng ML ngay từ đầu; tự động khóa tài khoản/thao tác | Đảm bảo tính minh bạch, dễ giải thích, giải quyết bài toán khởi động nguội và an toàn cho P2 |
| AUD-ADR-06 | Xuất file/archive bất đồng bộ cho 7 năm; truy vấn tương tác tối đa 90 ngày | Cho phép truy vấn đồng bộ trực tiếp toàn bộ 7 năm | Bảo vệ độ trễ của hệ thống và cô lập tài nguyên hiệu quả |
| AUD-ADR-07 | Thao tác vụ việc dạng append-only với thẩm quyền giải quyết thuộc về Owner | Chỉ cập nhật đè trên dòng vụ việc; trao toàn quyền giải quyết cho Manager | Phân tách trách nhiệm rõ ràng và bảo đảm khả năng kiểm toán hồi tố |

## 11. Các Ứng Viên Biểu Đồ Tuần Tự (Sequence Diagram Candidates)

Biểu đồ tuần tự được xây dựng sau khi xác nhận tập hợp 8 luồng nghiệp vụ và các bên tham gia:

| # | Luồng xử lý | Các bên tham gia L1 | Các bên tham gia L2 |
|---|---|---|---|
| 1 | Hủy đơn thành công → ghi nhận bằng chứng kiểm toán | Manager/POS, Order, EventBridge/SQS, Audit, PostgreSQL | Audit API/consumer, validator schema-làm sạch, inbox, evidence writer, DB/outbox |
| 2 | Giảm giá thủ công / khuyến mãi → ghi nhận bằng chứng | POS/Order/Promotion, EventBridge/SQS, Audit | adapter sự kiện nguồn, normalizer, validator phân bổ, evidence/projection writer |
| 3 | Lệnh/kết quả mở két tiền → ghi nhận bằng chứng | Cashier/POS Device, Shift, EventBridge/SQS, Audit | adapter thao tác két, reconciler kết quả, inbox/evidence writer |
| 4 | Owner/Manager truy vấn dòng thời gian kèm xử lý từ chối phạm vi | Angular Console, Audit API, Identity, PostgreSQL | Policy, cache, query service, repository/DB |
| 5 | Phát hiện bất thường P2 → tạo phát hiện/vụ việc | Dữ kiện Audit/Order/Payment/Shift, Loss Worker, Audit DB, Case/Console | feature aggregator, rule evaluator, finding writer, case transition store |
| 6 | Chuyển trạng thái vụ việc bởi Owner/Manager | Console, Identity, Audit API, Audit DB, Audit event bus | controller/policy, CAS case service, case actions, outbox |
| 7 | Xác minh digest phát hiện không khớp (mismatch) | Scheduler/Ops, Digest Worker, Audit DB, Alert/Audit | batch selector, canonicalizer, verifier, integrity finding writer |
| 8 | Xuất file 7 năm / lưu trữ pháp lý / phát lại | Owner/Ops, Audit API, Identity, Export/Retention Worker, PostgreSQL/Object Storage | operation state, hold evaluator, stream/export writer, audit/outbox |

Artifact biểu đồ tuần tự bao gồm các khối PlantUML L1 và L2, không đưa vào các thành phần triển khai ngoài các ranh giới thiết kế đã được xác nhận này.

## 12. Cổng Phê Duyệt Kiến Trúc (Architecture Approval Gate)

- **Trạng thái hiện tại:** Đã phê duyệt — người dùng đã xác nhận tám luồng tuần tự và ranh giới các bên tham gia.
- **Yêu cầu trước khi chuyển sang Hợp đồng (Contracts):** Đã hoàn thành; việc cụ thể hóa hợp đồng và các tu chính hệ thống gốc còn lại là các cổng triển khai, không phải các quyết định kiến trúc chưa giải quyết.
- **Quy tắc triển khai:** Tuyệt đối không viết code; Contracts phải cụ thể hóa các schema API/event, quy tắc migration/chỉ mục/phân vùng, chính sách quy tắc và các fixture chuyển trạng thái.
