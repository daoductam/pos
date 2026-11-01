# Module 11 — Giám Sát & Chống Thất Thoát: Kết Quả Thiết Kế Cuối Cùng (Final Design Result)

**Ngày:** 2026-10-13  
**Trạng thái:** Thiết kế hoàn tất — Đã phê duyệt kèm cảnh báo  
**Kết luận:** YELLOW — Tiếp tục một cách thận trọng (Proceed with Caution)  
**Phạm vi:** 8 artifact thiết kế, chưa có mã nguồn ứng dụng

## 1. Kết Quả Cuối Cùng (Final Outcome)

Bộ tài liệu thiết kế Module 11 đã hoàn tất và được phê duyệt xuyên suốt qua các cổng: Tầm nhìn (Vision) → Yêu cầu (Requirements) → Kiến trúc (Architecture) → Biểu đồ tuần tự (Sequence) → Hợp đồng (Contracts) → Hoàn tất (Finalization).

Phạm vi kỹ thuật đã được chốt:

- Phân hệ P0: Kiểm toán dạng chỉ ghi thêm (append-only) cho việc hủy món, giảm giá/ghi đè thủ công và mở két tiền.
- Phân hệ P2: Phát hiện bất thường bằng hàng rào quy tắc (rule-based guardrail) kết hợp đường cơ sở thống kê (statistical baseline), chỉ sinh phát hiện/vụ việc (finding/case) cho Owner/Manager; tuyệt đối không tự động khóa tài khoản nhân viên hay chặn giao dịch.
- Chủ sở hữu (Owner) xem toàn tenant hoặc tập con chi nhánh; Quản lý (Manager) chỉ xem các chi nhánh được phân công; Thu ngân và bếp không có quyền truy cập Audit console.
- Thời gian lưu trữ tối thiểu 7 năm, lưu trữ pháp lý (legal hold), mã băm toàn vẹn SHA-256 digest và cơ chế xuất file bất đồng bộ cho dữ liệu lịch sử trên 90 ngày.
- Audit là service trung tâm sở hữu bằng chứng, vụ việc và read model; các service Order, Payment, Shift, POS Device, Promotion/Loyalty và Identity tiếp tục sở hữu trạng thái nghiệp vụ nguồn.

## 2. Danh Mục Các Artifact (Artifact Registry)

| Artifact | Vai trò | Trạng thái |
|---|---|---|
| [Tầm nhìn (Vision)](file:///d:/vibe-code/pos/docs/designs-vi/audit-loss-prevention/2026-10-13-vision.md) | Phạm vi, kết quả mong đợi, ranh giới và nguyên tắc | Đã phê duyệt kèm cảnh báo |
| [Đánh giá tầm nhìn](file:///d:/vibe-code/pos/docs/designs-vi/audit-loss-prevention/2026-10-13-vision-review.md) | Đánh giá tính đầy đủ và tác động chéo | Cảnh báo đã được chấp nhận |
| [Yêu cầu (Requirements)](file:///d:/vibe-code/pos/docs/designs-vi/audit-loss-prevention/2026-10-13-requirements.md) | Yêu cầu chức năng, phi chức năng, bảo mật và nghiệm thu | Đã phê duyệt kèm cảnh báo |
| [Đánh giá yêu cầu](file:///d:/vibe-code/pos/docs/designs-vi/audit-loss-prevention/2026-10-13-requirements-review.md) | Báo cáo Spec Sentinel đánh giá yêu cầu | Cảnh báo đã được chấp nhận |
| [Kiến trúc (Architecture)](file:///d:/vibe-code/pos/docs/designs-vi/audit-loss-prevention/2026-10-13-architecture.md) | Kiến trúc service, dữ liệu, giao tiếp và xử lý lỗi | Đã phê duyệt |
| [Biểu đồ tuần tự](file:///d:/vibe-code/pos/docs/designs-vi/audit-loss-prevention/2026-10-13-sequence-diagrams.adoc) | 8 luồng × 2 cấp L1/L2 PlantUML thiết kế hoàn chỉnh | Đã xác nhận |
| [Hợp đồng (Contracts)](file:///d:/vibe-code/pos/docs/designs-vi/audit-loss-prevention/2026-10-13-contracts.md) | Hợp đồng REST, events, database, digest, quy tắc, vụ việc, lưu trữ | Đã phê duyệt kèm cảnh báo |
| [Đánh giá hợp đồng](file:///d:/vibe-code/pos/docs/designs-vi/audit-loss-prevention/2026-10-13-contracts-review.md) | Báo cáo Spec Sentinel đánh giá hợp đồng API/dữ liệu | Cảnh báo đã được chấp nhận |

## 3. Tóm Tắt Đánh Giá Thiết Kế (Design Review Summary)

| Hạng mục | Kết luận | Bằng chứng / Hành động còn lại |
|---|---|---|
| Phạm vi và tính đầy đủ | ĐẠT kèm cảnh báo | P0/P2, tác nhân, ranh giới, luồng sự cố, lưu trữ và tu chính hệ thống gốc đã được bao quát. |
| Bảo mật và cô lập tenant | ĐẠT kèm cổng triển khai | Phạm vi Cognito do server phân giải, điều kiện lọc repository, từ chối an toàn fail-closed, làm sạch dữ liệu và phân quyền xuất file đã xác định; tu chính Identity và fixture IDOR vẫn là điều kiện bắt buộc. |
| Logic nghiệp vụ | ĐẠT kèm cổng triển khai | Danh mục sự kiện, nguồn giảm giá, khóa thao tác két tiền, nhóm quy tắc và máy trạng thái vụ việc đã được định nghĩa; ma trận timeout/kết quả két tiền và fixture chính sách vẫn là điều kiện bắt buộc. |
| Tính toàn vẹn dữ liệu | ĐẠT kèm cổng triển khai | Bằng chứng append-only, dedupe inbox, CAS, chuỗi digest và lưu trữ pháp lý đã xác định; role DB, migration và test vector digest vẫn là điều kiện bắt buộc. |
| Mức sẵn sàng API/Event | CẢNH BÁO | Danh mục hợp đồng đã đầy đủ ở mức thiết kế; đặc tả OpenAPI, AsyncAPI, registry và schema sinh tự động vẫn chưa được cụ thể hóa. |
| Hiệu năng | ĐẠT kèm cổng triển khai | Truy vấn có chặn 90 ngày, phân trang cursor, chỉ mục, xuất file bất đồng bộ và phân tách worker đã xác định; hồ sơ kiểm thử tải vẫn là điều kiện bắt buộc. |
| Khả năng quan sát | ĐẠT kèm cổng triển khai | Watermark/độ tươi mới, DLQ, lệch digest toàn vẹn, correlation và sự kiện chất lượng dữ liệu đã định nghĩa; dashboard/cảnh báo/runbook tiếp tục là artifact triển khai. |
| Kiểm thử | CẢNH BÁO | Ma trận nghiệm thu và danh sách fixture đã có; các fixture thực thi cho hợp đồng, phát lại, bảo mật, lưu trữ và bất thường vẫn là điều kiện bắt buộc. |

## 4. Cổng Kiểm Tra Của Code Reviewer

### Tóm tắt

Repository hiện chưa có mã nguồn ứng dụng để review. Các artifact thiết kế đã được rà soát kỹ lưỡng dựa trên checklist về an toàn thông tin, logic nghiệp vụ, hiệu năng, khả năng bảo trì và quy ước kỹ thuật. Không phát hiện bất kỳ lỗi nghiêm trọng (critical issue) nào; toàn bộ các cảnh báo đều là các cổng triển khai và cụ thể hóa artifact trước khi viết mã nguồn production.

### Các Cảnh Báo (Nên Xử Lý)

| # | File:Dòng | Hạng mục | Vấn đề | Khuyến nghị |
|---|---|---|---|---|
| 1 | [`2026-10-13-contracts.md:452-520`](file:///d:/vibe-code/pos/docs/designs-vi/audit-loss-prevention/2026-10-13-contracts.md#L452) | Bảo mật / Toàn vẹn dữ liệu | Registry sự kiện hệ thống gốc và route producer chuyên biệt theo hành động chưa tồn tại ở dạng artifact máy đọc được. | Tạo `audit-event-registry.yaml`, thực thi route chuẩn tắc/dedupe và kiểm thử sự kiện trùng lặp giữa route chung và chuyên biệt. |
| 2 | [`2026-10-13-contracts.md:495-512`](file:///d:/vibe-code/pos/docs/designs-vi/audit-loss-prevention/2026-10-13-contracts.md#L495) | Logic nghiệp vụ | Timeout lệnh/kết quả két tiền, callback trễ và kết quả phần cứng mâu thuẫn chưa có ma trận trạng thái đầy đủ. | Chốt hợp đồng producer với POS Device/Shift và tạo các golden fixture trước khi triển khai code. |
| 3 | [`2026-10-13-contracts.md:227-367`](file:///d:/vibe-code/pos/docs/designs-vi/audit-loss-prevention/2026-10-13-contracts.md#L227) | Hợp đồng API | OpenAPI chưa được cụ thể hóa; thứ tự ưu tiên CAS/idempotency/header và vòng đời tải file xuất mới dừng ở dạng văn bản mô tả. | Sinh OpenAPI 3.1, AsyncAPI/JSON Schema và các bài kiểm thử hợp đồng. |
| 4 | [`2026-10-13-contracts.md:806-829`](file:///d:/vibe-code/pos/docs/designs-vi/audit-loss-prevention/2026-10-13-contracts.md#L806) | Toàn vẹn dữ liệu | Chuẩn hóa digest chưa có test vector thực thi dạng file. | Tạo fixture kiểm thử cho chuỗi byte chuẩn tắc, mã băm, digest trước đó và hành vi không khớp kỳ vọng. |
| 5 | [`2026-10-13-contracts.md:831-864`](file:///d:/vibe-code/pos/docs/designs-vi/audit-loss-prevention/2026-10-13-contracts.md#L831) | Logic nghiệp vụ | Quản trị quy tắc và hành vi khởi động nguội / baseline bằng 0 / sự kiện trễ chưa có fixture thực thi. | Chốt quyền ban hành/ngừng quy tắc, mốc `effectiveAt`, phiên bản chính sách bất biến và các fixture đơn định. |
| 6 | [`2026-10-13-contracts.md:770-900`](file:///d:/vibe-code/pos/docs/designs-vi/audit-loss-prevention/2026-10-13-contracts.md#L770) | Bảo mật / Vận hành | Quyền role DB append-only, manifest lưu trữ dài hạn và hành vi lưu trữ pháp lý bán phần trên phân vùng chưa được cụ thể hóa. | Tạo các script migration/role, hợp đồng tác vụ lưu trữ và các bài test xử lý lỗi/retry. |

### Tóm Tắt Checklist

| Hạng mục | Kết luận | Số lượng vấn đề |
|---|---|---:|
| An toàn thông tin (Security) | CẢNH BÁO | 2 cổng triển khai; không có lỗi nghiêm trọng |
| Logic nghiệp vụ (Business Logic) | CẢNH BÁO | 2 cổng triển khai; không có lỗi nghiêm trọng |
| Hiệu năng (Performance) | ĐẠT | 0 vấn đề hiện tại; vẫn cần hồ sơ đo lường tải |
| Mã nguồn sạch (Clean Code) | ĐẠT | Thiết kế được phân tách rõ ràng theo workflow artifact; chưa có mã nguồn ứng dụng |
| Quy ước kỹ thuật (Conventions) | CẢNH BÁO | Các artifact máy đọc được chưa được sinh ra |

## 5. Tóm Tắt Kiến Trúc (Architecture Summary)

```text
Order / Promotion / POS Device / Shift / Identity
        │ transactional outbox + canonical source events
        ▼
EventBridge → SQS pos-audit-events → Audit inbox/dedupe
                                      │
                                      ├─ append-only audit_events
                                      ├─ query projection + watermark
                                      ├─ SHA-256 digest/checkpoint
                                      └─ anomaly rules/baselines → finding/case
                                                           │
                                                           ├─ Owner/Manager console
                                                           └─ async export / legal hold
```

Mô hình tính nhất quán: Thao tác tại nguồn là thẩm quyền tối cao; Audit xử lý theo cơ chế at-least-once và nhất quán cuối cùng; sự kiện trùng lặp / phát lại mang tính lũy kế (idempotent); dữ liệu chưa đầy đủ hoặc bị cũ được hiển thị rõ ràng và không bao giờ đánh đồng thành `NO_DATA`.

## 6. Các Cổng Triển Khai Bắt Buộc Trước Khi Viết Code

1. Cụ thể hóa `openapi.yaml`, `openapi-internal.yaml`, `asyncapi.yaml` và các JSON Schema.
2. Cụ thể hóa `audit-event-registry.yaml` với quyền sở hữu producer, route chuẩn tắc, khóa dedupe, các trường bắt buộc và chính sách làm sạch dữ liệu.
3. Áp dụng các bản tu chính hệ thống gốc cho Identity, Order, Promotion/Loyalty, Payment/Shift/POS Device và POS Platform.
4. Tạo migration PostgreSQL/Flyway với các ràng buộc tenant/chi nhánh, role append-only, chỉ mục, kiểm tra phân vùng và comment trên cột.
5. Tạo test vector chuẩn hóa digest và xác minh hành vi khi phát hiện không khớp (mismatch).
6. Tạo các fixture cho lệnh/kết quả két tiền, trùng lặp/phát lại, cơ chế CAS/idempotency vụ việc, lưu trữ pháp lý và tác vụ lưu trữ.
7. Xây dựng vòng đời chính sách bất thường và các fixture quy tắc đơn định cho toàn bộ 4 nhóm quy tắc ban đầu.
8. Định nghĩa dashboard / cảnh báo / runbook cho: độ tươi mới, hàng đợi DLQ, độ trễ projection, lỗi xuất file, xung đột vụ việc và lệch digest toàn vẹn.
9. Chạy các bài test tải / bảo mật / quyền riêng tư cho: truy vấn 90 ngày, xuất file 7 năm, IDOR, thu hồi membership, rò rỉ cache, làm sạch PII và hết hạn link tải có chữ ký.

## 7. Lộ Trình Triển Khai (Delivery Roadmap)

| Giai đoạn | Phạm vi | Tiêu chí hoàn thành |
|---|---|---|
| 0. Cụ thể hóa hợp đồng | Tu chính hệ thống gốc, OpenAPI/AsyncAPI, registry, schemas, fixtures | Các hợp đồng compile và validate thành công; ma trận quyền sở hữu producer và phân quyền được duyệt |
| 1. Bằng chứng P0 | Thu nhận, inbox/dedupe, bằng chứng append-only, sự kiện hủy / giảm giá / két tiền | Các luồng tuần tự P0 vượt qua kiểm thử phát lại / tính lũy kế / làm sạch dữ liệu |
| 2. Truy vấn và quản trị | Dòng thời gian / chi tiết, phạm vi chi nhánh, cursor, audit console, lưu trữ / xuất file / lưu trữ pháp lý | Vượt qua kiểm thử IDOR/bảo mật, độ trễ truy vấn 90 ngày và phân quyền xuất file |
| 3. Phát hiện P2 | Tổng hợp đặc trưng, đường cơ sở, 4 nhóm quy tắc, tự động tạo phát hiện | Vượt qua kiểm thử khởi động nguội / dữ kiện trễ / trùng lặp; không có bất kỳ đường dẫn tự động khóa nào |
| 4. Vụ việc và gia cố vận hành | CAS/hành động vụ việc, xác minh digest, DLQ/phát lại, dashboard/runbook | Vượt qua kiểm thử toàn vẹn, phục hồi sự cố, đồng thời, lưu trữ và sẵn sàng vận hành |

## 8. Ma Trận Truy Vết (Traceability Matrix)

| Yêu cầu của người dùng | Bao phủ trong thiết kế | Hợp đồng / Luồng tuần tự |
|---|---|---|
| Kiểm toán hủy món | `OrderLineCancelled.v1`, `OrderCancelled.v1`, bằng chứng append-only | Yêu cầu §4.2; Hợp đồng §6.2; Luồng tuần tự 1 |
| Kiểm toán giảm giá | Nguồn tự động / thủ công, phân bổ, lý do và kết quả | Yêu cầu §4.3; Hợp đồng §3.2/§6.2; Luồng tuần tự 2 |
| Kiểm toán mở két tiền | Khóa thao tác, ca làm việc / thiết bị / thu ngân, kết quả phần cứng | Yêu cầu §4.4; Hợp đồng §6.2; Luồng tuần tự 3 |
| Khả năng hiển thị theo chi nhánh / RBAC | Quyết định Identity + điều kiện lọc repository + cursor/export theo phạm vi | Yêu cầu §2/§4.5; Hợp đồng §2/§4; Luồng tuần tự 4 |
| Bất thường nhân viên | Quy tắc / đường cơ sở, tham chiếu bằng chứng, phát hiện có thể giải thích | Yêu cầu §4.7; Hợp đồng §10; Luồng tuần tự 5 |
| Xử lý vụ việc | Phân quyền Owner / Manager, chuyển trạng thái CAS, kiểm toán hành động vụ việc | Yêu cầu §4.8; Hợp đồng §11; Luồng tuần tự 6 |
| Chống can thiệp dữ liệu | Database append-only + chuỗi digest SHA-256 | Yêu cầu §4.6; Hợp đồng §9; Luồng tuần tự 7 |
| Bảy năm / lưu trữ pháp lý / xuất file | Ma trận lưu trữ, quyền ưu tiên của hold, xuất file bất đồng bộ mã hóa | Yêu cầu §4.6; Hợp đồng §12; Luồng tuần tự 8 |

## 9. Checklist Hoàn Tất

- [x] Tầm nhìn (Vision), Yêu cầu (Requirements), Kiến trúc (Architecture) và Hợp đồng (Contracts) đã được review kỹ lưỡng.
- [x] Người dùng đã phê duyệt các cảnh báo tại từng cổng kiểm soát.
- [x] Phạm vi P0/P2 và ranh giới an toàn không tự động khóa đã được chốt.
- [x] Phân quyền tenant/chi nhánh, làm sạch dữ liệu và quyền sở hữu append-only đã được chốt.
- [x] 8 luồng tuần tự đã được xây dựng và kiểm tra hợp lệ đủ 16 khối PlantUML.
- [x] Các hợp đồng về API, event, database, digest, quy tắc bất thường, vụ việc và lưu trữ đã được ghi nhận đầy đủ.
- [ ] Các file OpenAPI/AsyncAPI/JSON Schema và migration được cụ thể hóa.
- [ ] Các tu chính service hệ thống gốc được cập nhật vào bộ tài liệu nền tảng.
- [ ] Fixture thực thi, hồ sơ kiểm thử tải, bộ dữ liệu bảo mật/riêng tư và runbook vận hành được tạo đầy đủ.
- [ ] Chỉ sau khi hoàn tất các cổng kiểm soát trên mới bắt đầu viết code production.

**Kết luận:** Thiết kế Module 11 đã hoàn tất ở mức thiết kế sẵn sàng triển khai (implementation-ready design), với toàn bộ các cảnh báo đã được chấp nhận và chuyển hóa thành các cổng triển khai rõ ràng. Chưa viết code.
