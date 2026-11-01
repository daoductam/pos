Started at: 2026/10/13 00:00:00
Finished at: 2026/10/13 00:00:00
Total time: 0 minutes
---

# Module 10 — Báo Cáo & Dashboard: Kết Quả Thiết Kế (Design Result)

**Ngày:** 2026-10-13  
**Trạng thái:** Thiết kế hoàn tất — Đã phê duyệt kèm cảnh báo  
**Phạm vi:** L — Đầy đủ nghi thức (Full Ceremony)  
**Module:** Báo cáo & dashboard (Reporting read model; Athena là ranh giới mở rộng)

## 1. Kết Luận (Verdict)

**APPROVED WITH WARNINGS (ĐÃ PHÊ DUYỆT KÈM CẢNH BÁO)**

Tài liệu thiết kế đã cung cấp đầy đủ cơ sở kỹ thuật để lập backlog triển khai cho các hạng mục P0/P1. Reporting sử dụng các bảng read model hướng sự kiện trong PostgreSQL cho luồng thực thi MVP, duy trì `OrderPaid.v1` là nguồn sự thật duy nhất về doanh thu và tuyệt đối không truy vấn chéo cơ sở dữ liệu. Không tồn tại bất kỳ rào cản ngăn chặn (blocker) nào ở mức Critical/RED liên quan đến phạm vi, quyền sở hữu dữ liệu, cô lập đa tenant hay định hướng kiến trúc.

Trước khi bắt tay vào viết mã nguồn, bắt buộc phải hoàn tất các tu chính hợp đồng hệ thống gốc (parent amendments) và cụ thể hóa các hợp đồng máy đọc được, migration, fixture và hồ sơ tải được nêu tại Mục 6.

## 2. Các Artifact Đã Hoàn Tất

| Giai đoạn | Tên Artifact | Đường dẫn | Trạng thái |
|---|---|---|---|
| Vision | Tầm nhìn (Vision) | [`2026-10-13-vision.md`](file:///d:/vibe-code/pos/docs/designs-vi/reporting/2026-10-13-vision.md) | Đã phê duyệt |
| Vision review | Báo cáo Spec Sentinel | [`2026-10-13-vision-review.md`](file:///d:/vibe-code/pos/docs/designs-vi/reporting/2026-10-13-vision-review.md) | Cảnh báo đã được chấp nhận |
| Requirements | Yêu cầu (Requirements) | [`2026-10-13-requirements.md`](file:///d:/vibe-code/pos/docs/designs-vi/reporting/2026-10-13-requirements.md) | Đã phê duyệt kèm cảnh báo |
| Requirements review | Báo cáo Spec Sentinel | [`2026-10-13-requirements-review.md`](file:///d:/vibe-code/pos/docs/designs-vi/reporting/2026-10-13-requirements-review.md) | Cảnh báo đã được chấp nhận |
| Architecture | Kiến trúc (Architecture) | [`2026-10-13-architecture.md`](file:///d:/vibe-code/pos/docs/designs-vi/reporting/2026-10-13-architecture.md) | Đã phê duyệt |
| Sequence diagrams | L1/L2 PlantUML | [`2026-10-13-sequence-diagrams.adoc`](file:///d:/vibe-code/pos/docs/designs-vi/reporting/2026-10-13-sequence-diagrams.adoc) | Đã tạo và xác nhận |
| Contracts | Hợp đồng API & Dữ liệu | [`2026-10-13-contracts.md`](file:///d:/vibe-code/pos/docs/designs-vi/reporting/2026-10-13-contracts.md) | Đã phê duyệt kèm cảnh báo |
| Contracts review | Báo cáo Spec Sentinel | [`2026-10-13-contracts-review.md`](file:///d:/vibe-code/pos/docs/designs-vi/reporting/2026-10-13-contracts-review.md) | Cảnh báo đã được chấp nhận |
| Finalization | Kết quả thiết kế | Tài liệu này | Đã phê duyệt kèm cảnh báo |

## 3. Tóm Tắt Đánh Giá Thiết Kế (Design Review Summary)

| Tiêu chí | Kết quả | Nhận xét |
|---|---|---|
| Tính đầy đủ (Completeness) | ĐẠT KÈM CẢNH BÁO | P0 ngày/chi nhánh/món bán chạy, P1 khung giờ/nhân viên/khuyến mãi/gợi ý, dashboard, độ tươi mới, điều chỉnh, rebuild và ma trận kiểm thử đều đã đầy đủ. Tuy nhiên, OpenAPI/AsyncAPI/Flyway/fixtures vẫn cần sinh ra trước khi viết code. |
| Tính nhất quán (Consistency) | ĐẠT KÈM CẢNH BÁO | Từ điển chỉ số, múi giờ, số nguyên VND minor units, nguồn sự thật doanh thu từ `OrderPaid`, chế độ xem điều chỉnh hiện hành và phạm vi phân quyền đều nhất quán. Sự kiện hoàn tiền/điều chỉnh chuẩn tắc và hạt nhân dữ liệu của read model cần được chốt thành artifact thực thi. |
| Tính khả thi (Feasibility) | ĐẠT | Bảng tổng hợp sẵn trong PostgreSQL đáp ứng SLA KPI p95 ≤1s / bảng p95 ≤2s; EventBridge/SQS + inbox/DLQ hỗ trợ phát lại dữ liệu; Athena không nằm trên hot path của MVP. |
| An toàn thông tin (Security) | ĐẠT KÈM CẢNH BÁO | Quyết định trực tuyến từ Identity, điều kiện lọc repository, hash phạm vi cache, không lưu PII thô và ranh giới audit đã có. Cần hoàn tất schema quyền của Identity gốc, fixture kiểm thử thu hồi quyền và vai trò nguồn chuẩn của audit. |
| Hiệu năng (Performance) | ĐẠT KÈM CẢNH BÁO | Các chỉ mục, chặn truy vấn 12 tháng / 20 chi nhánh và TTL cache 15s đã nêu rõ; cần bổ sung hồ sơ tải và ngưỡng định lượng cho dung lượng/phân vùng/rebuild. |
| Khả năng quan sát (Observability) | ĐẠT | Watermark, độ tươi mới/độ hoàn tất, DLQ, sức khỏe projection, tác vụ rebuild, correlation/causation và các sự kiện chất lượng dữ liệu đều đã được thiết kế hoàn chỉnh. |
| Sẵn sàng kiểm thử (Testing readiness) | ĐẠT KÈM CẢNH BÁO | Ma trận kiểm thử bao phủ trùng lặp, sai thứ tự, điều chỉnh, ghi nhận đóng góp, phân quyền, cache, quyền riêng tư và hiệu năng; tuy nhiên các fixture/schema dạng thực thi chưa được cụ thể hóa. |

## 4. Đánh Giá Hoàn Tất — Cổng Kiểm Tra Của Code Reviewer

**Phạm vi:** Toàn bộ các artifact thiết kế Reporting và các hợp đồng hệ thống gốc có liên quan trực tiếp; repository hiện chưa có mã nguồn ứng dụng hay migration, do đó đây là đánh giá chất lượng thiết kế dựa trên checklist về bảo mật, logic nghiệp vụ, hiệu năng, khả năng bảo trì và quy ước kỹ thuật.

**Kết luận:** **YELLOW — Tiếp tục một cách thận trọng (Proceed with Caution)**

### Tóm tắt

Thiết kế mạch lạc, chặt chẽ và sẵn sàng cho việc triển khai ở cấp độ backlog. Không phát hiện bất kỳ secret nào bị hardcode, không có nguy cơ bypass authorization, không có truy vấn chéo tenant chủ ý, không để lộ PII thô trong phản hồi/sự kiện, và không phụ thuộc vào Athena/LLM trên hot path của dashboard. Các cảnh báo tập trung vào việc biến các tu chính hệ thống gốc và các hợp đồng dạng văn bản mô tả thành các artifact máy đọc được và có thể phát lại chính xác.

### Các Cảnh Báo

| # | File:Dòng | Hạng mục | Vấn đề | Khuyến nghị |
|---|---|---|---|---|
| 1 | `2026-10-13-contracts.md:96-126, 438-505` | Phân quyền / API | Quyền `report:read`/`reporting:operate` và tu chính `OrderPaid` cho Reporting chưa được ban hành vào schema của Identity/Order gốc. | Ban hành một schema quyết định phân quyền Identity chuẩn tắc và schema `OrderPaid.v1` bổ sung; thêm fixture cho scope, thu hồi quyền, checkout actor và khuyến mãi trước khi sinh client. |
| 2 | `2026-10-13-contracts.md:506-513` | Logic nghiệp vụ | Hợp đồng hoàn tiền/điều chỉnh vẫn để ngỏ giữa `OrderRefunded.v1` và `OrderCorrected.v1`. | Chọn một sự kiện thực tế nghiệp vụ chuẩn tắc, chốt rõ ngữ nghĩa hoàn tiền toàn phần/bán phần, ngày hiệu lực, thứ tự ưu tiên điều chỉnh và dedupe key nguồn. |
| 3 | `2026-10-13-contracts.md:576-608, 686-712` | Mô hình dữ liệu | Một số bảng cụ thể hóa sử dụng `period_start/period_end` trong khi truy vấn công khai nhận khoảng tùy ý; bảng tổng hợp gợi ý thiếu phả hệ đủ để phát lại/điều tra lỗi. | Chốt hạt nhân dữ liệu (grain) cố định rồi tổng hợp khoảng ở tầng truy vấn, hoặc bổ sung các bất biến về chồng lấn/phả hệ và bảng dữ kiện ghi nhận dạng append-only. |
| 4 | `2026-10-13-contracts.md:770-790` | Ranh giới kiểm toán | Reporting có bảng/sự kiện audit truy cập trong khi Platform quy định Audit Service là chủ sở hữu append-only duy nhất. | Chốt rõ bảng của Reporting chỉ là staging/tham chiếu hay là một ngoại lệ được phê duyệt; Audit Service nên là nguồn sự thật chuẩn cho việc lưu trữ dài hạn/pháp lý. |
| 5 | `2026-10-13-contracts.md:896-914` | Khả năng bảo trì / Vận hành | OpenAPI, AsyncAPI, migration, fixture và hồ sơ tải mới chỉ là yêu cầu artifact, chưa có đầu ra thực thi; ngưỡng phân vùng chưa được đo lường thực tế. | Tạo các artifact trước khi viết controller/consumer/truy vấn; chạy kiểm thử migration trên DB rỗng/copy, kiểm thử tương thích, phát lại và kiểm thử tải 12 tháng × 20 chi nhánh. |

### Tóm Tắt Checklist

| Hạng mục | Kết luận | Số lượng vấn đề |
|---|---|---:|
| An toàn thông tin (Security) | ĐẠT KÈM CẢNH BÁO | 1 cảnh báo về phân quyền/audit hệ thống gốc; 0 lỗi nghiêm trọng |
| Logic nghiệp vụ (Business Logic) | ĐẠT KÈM CẢNH BÁO | 2 cảnh báo về sự kiện chuẩn tắc / phả hệ read model |
| Hiệu năng (Performance) | ĐẠT KÈM CẢNH BÁO | 1 cảnh báo về đo lường tải/phân vùng; không có truy vấn công khai không giới hạn |
| Thiết kế sạch (Clean Design) | ĐẠT KÈM CẢNH BÁO | 1 cảnh báo về cụ thể hóa artifact máy đọc được |
| Quy ước chuẩn (Conventions) | ĐẠT KÈM CẢNH BÁO | Cần đồng bộ schema/danh mục với hệ thống gốc làm điều kiện tiên quyết |

## 5. Tóm Tắt Kiến Trúc (Architecture Summary)

| Chiều kiến trúc | Tóm tắt |
|---|---|
| Các component logic | Dashboard Angular, Query API, client/chính sách phân quyền Identity, Redis cache, consumer EventBridge/SQS, inbox/dedupe, projection handler, quản lý watermark/độ hoàn tất, worker rebuild, Reporting PostgreSQL và ranh giới mở rộng Athena trong tương lai. |
| Các Read model | `sales_daily`, `sales_by_branch`, `sales_by_hour`, `sales_by_employee`, `top_items`, `promotion_effectiveness`, `recommendation_effectiveness`, `projection_watermarks`, `inbox_events`, `rebuild_operations`. |
| Dữ kiện nguồn | `OrderPaid.v1` cho doanh thu đã thanh toán; sự kiện hoàn tiền/điều chỉnh đã duyệt; Snapshot khuyến mãi; Hiển thị/chấp nhận gợi ý; Metadata Identity/chi nhánh/nhân viên. Quyết toán muộn của Payment không tự trở thành doanh thu. |
| Giao diện API | REST công khai `/api/v1/reports/*` cho tổng quan, doanh số, món bán chạy, khuyến mãi, gợi ý và độ tươi mới; nội bộ `/internal/v1/reporting/*` cho rebuild/kiểm tra sức khỏe. |
| Mô hình tính đúng đắn | Xử lý sự kiện at-least-once, dedupe sự kiện/khóa nguồn, watermark đơn điệu tăng, chế độ xem điều chỉnh hiện hành, `asOfWatermark`, các trạng thái tường minh `FRESH/STALE/PARTIAL/UNAVAILABLE/NO_DATA`. |
| Phân quyền | Chủ cửa hàng (Owner) theo tenant/tập con chi nhánh; Quản lý (Manager) theo chi nhánh được giao; quyết định trực tuyến từ Identity trước khi đọc cache; điều kiện lọc tenant/chi nhánh tại repository; khóa cache chứa phiên bản phân quyền/hash phạm vi. |
| Quyền riêng tư / Audit | Không trả về SĐT khách hàng, thông tin thanh toán, ID đơn hàng thô hay sự kiện thô; nhãn nhân viên/khuyến mãi là các snapshot an toàn; bằng chứng truy cập được ẩn thông tin nhạy cảm và ranh giới audit được chốt rõ với Audit Service. |

## 6. Các Cổng Triển Khai và Rủi Ro (Implementation Gates & Risks)

| Cổng / Rủi ro | Điều kiện hoàn tất |
|---|---|
| Hợp đồng sự kiện hệ thống gốc | Order ban hành các trường bổ sung trong `OrderPaid.v1` cho ngày/múi giờ kinh doanh, checkout actor, snapshot tổng/dòng món và phân bổ khuyến mãi bất biến; sự kiện hoàn tiền/điều chỉnh chuẩn tắc được chọn và có fixture. |
| Phân quyền Identity | Ban hành quyền `report:read` và quyền hạn chế `reporting:operate`, schema quyết định phân quyền, phiên bản membership/phân quyền, hành vi thu hồi quyền và kiểm thử từ chối không liệt kê danh sách. |
| Hợp đồng được sinh ra | OpenAPI 3.1, OpenAPI nội bộ, AsyncAPI/JSON Schema, từ điển chỉ số và kiểu dữ liệu Angular được kiểm tra hợp lệ trong CI; không viết DTO controller/query tùy ý lệch tài liệu. |
| Tính đúng đắn của Projection | Tính nguyên tử của inbox, xử lý trùng lặp/sai thứ tự/khoảng trống dữ liệu, cách ly phiên bản lạ, watermark đơn điệu, hoán đổi con trỏ hiện hành, phát lại điều chỉnh và vượt qua runbook DLQ. |
| Ghi nhận đóng góp (Attribution) | Thiếu nhân viên gán vào `UNASSIGNED`; khuyến mãi gom nhóm theo phiên bản bất biến; gợi ý chỉ tính đơn thanh toán thành công, cùng ngữ cảnh đơn hàng/giỏ hàng, chấp nhận nhiều gợi ý chỉ tính AOV 1 lần/đơn và mẫu số đối chứng bằng 0 trả về `NO_DATA`. |
| Cơ sở dữ liệu | Migration Flyway chỉ tiến về trước, điều kiện lọc tenant, các chỉ mục/ràng buộc/comment, dedupe duy nhất, giới hạn lease rebuild và nạp dữ liệu mẫu phiên bản tính toán thành công trên DB rỗng/copy. |
| Độ tươi mới / Hiệu năng | FRESH ≤1m, STALE >1m, cảnh báo >5m; KPI p95 ≤1s, bảng dữ liệu/drill-down p95 ≤2s; hoàn thành đo lường tải 12 tháng / 20 chi nhánh và tải dashboard đồng thời. |
| Bảo mật / Quyền riêng tư | Vượt qua các bài kiểm thử tiêu cực tenant/chi nhánh/IDOR/thu hồi cache, không có PII trong API/log/trace/event, kiểm tra phân quyền trước khi đọc cache, xác minh tham chiếu audit và hành vi lưu trữ. |
| Ranh giới Athena | Tuyệt đối không phụ thuộc Athena cho MVP P0/P1; chỉ đánh giá sau khi đã đạt ngưỡng đo lường dung lượng/truy vấn/lưu trữ và có quyết định/hợp đồng triển khai riêng biệt. |

## 7. Lộ Trình Triển Khai (Implementation Roadmap)

### Giai đoạn Phase 0 — Cụ thể hóa hợp đồng và tu chính hệ thống gốc

- **Mục tiêu:** Chuyển đổi Hợp đồng thành artifact thực thi và đồng bộ các ranh giới với hệ thống gốc.
- **Nhiệm vụ:** Tạo OpenAPI công khai/nội bộ, AsyncAPI/JSON Schema, từ điển chỉ số, schema Flyway, mock/kiểu dữ liệu Angular được sinh tự động; ban hành quyền trong Identity, thuộc tính ghi nhận/khuyến mãi trong OrderPaid và sự kiện hoàn tiền/điều chỉnh chuẩn tắc; chốt quyền sở hữu Audit.
- **Tiêu chí hoàn thành:** Kiểm thử tương thích schema đạt; các fixture cho phân quyền, doanh thu, điều chỉnh, khuyến mãi, gợi ý và độ tươi mới đều đạt; không còn sự mơ hồ về nguồn sự thật hay hạt nhân dữ liệu của read model.

### Giai đoạn Phase 1 — Nền tảng projection P0

- **Mục tiêu:** Xây dựng projection doanh thu theo ngày/chi nhánh và món bán chạy nhất.
- **Nhiệm vụ:** Consumer EventBridge/SQS, inbox/dedupe, `sales_daily`, `sales_by_branch`, `top_items`, quản lý watermark/độ hoàn tất, DLQ, worker rebuild/phát lại và kiểm tra sức khỏe projection.
- **Tiêu chí hoàn thành:** Sự kiện trùng lặp / sai thứ tự không gây tính trùng; `OrderPaid` là nguồn chuẩn tắc; điều chỉnh hiện hành/phát lại có tính đơn định; kiểm thử tiêu cực tenant/chi nhánh đạt; đạt mục tiêu độ tươi mới trong hồ sơ tải chuẩn.

### Giai đoạn Phase 2 — Dashboard và API P0

- **Mục tiêu:** Chủ cửa hàng (Owner) và Quản lý (Manager) sử dụng dashboard Angular với phạm vi và trạng thái chất lượng dữ liệu rõ ràng.
- **Nhiệm vụ:** Query API tổng quan/ngày/chi nhánh/món bán chạy/độ tươi mới, phân giải phạm vi Identity, điều kiện lọc repository, Redis cache/ETag, các thẻ KPI/biểu đồ/bảng/drill-down và xử lý lỗi an toàn.
- **Tiêu chí hoàn thành:** Owner/Manager chỉ thấy phạm vi hợp lệ; việc thu hồi quyền không gây rò rỉ cache; KPI p95 ≤1s, bảng dữ liệu p95 ≤2s; UI phân biệt rõ ràng giữa `NO_DATA`, `STALE`, `PARTIAL`, `UNAVAILABLE`.

### Giai đoạn Phase 3 — Các read model ghi nhận đóng góp P1

- **Mục tiêu:** Bổ sung doanh số theo khung giờ/nhân viên và hiệu quả khuyến mãi/gợi ý.
- **Nhiệm vụ:** Triển khai `sales_by_hour`, `sales_by_employee`, `promotion_effectiveness`, `recommendation_effectiveness`; tiêu thụ checkout actor, phiên bản/phân bổ khuyến mãi và sự kiện hiển thị/chấp nhận + OrderPaid; xử lý phả hệ ghi nhận muộn / điều chỉnh.
- **Tiêu chí hoàn thành:** Thiếu nhân viên chuyển vào `UNASSIGNED`; các phiên bản khuyến mãi không bị gộp; gợi ý chỉ tính đơn thanh toán thành công, tỷ lệ chấp nhận tính ở cấp độ tương tác và AOV tính ở cấp độ đơn hàng; toàn bộ fixture ghi nhận vàng đều đạt.

### Giai đoạn Phase 4 — Gia cố vận hành và đánh giá ranh giới Athena

- **Mục tiêu:** Đảm bảo độ tin cậy vận hành, khả năng phát lại dữ liệu, mở rộng quy mô và có cơ sở bằng chứng cho quyết định mở rộng phân tích lịch sử.
- **Nhiệm vụ:** Kiểm thử tải/ngâm (load/soak) 12 tháng × 20 chi nhánh, cảnh báo hàng đợi/DLQ/độ tươi mới, diễn tập khôi phục/rebuild dữ liệu, kiểm thử bảo mật/quyền riêng tư, xác minh audit/lưu trữ, đo lường dung lượng/phân vùng và đánh giá tính khả thi của Athena.
- **Tiêu chí hoàn thành:** Lưu trữ đầy đủ kết quả đo lường SLO/SLA; việc phát lại dữ liệu không sinh bản ghi trùng lặp; hoàn tất runbook vận hành; Athena chỉ được kích hoạt bằng quyết định/hợp đồng riêng sau khi đã chạm ngưỡng thực tế.

## 8. Ma Trận Truy Vết (Traceability Matrix)

| Yêu cầu nghiệp vụ | Thành phần sở hữu kiến trúc | Bằng chứng hợp đồng | Bằng chứng nghiệm thu |
|---|---|---|---|
| P0 doanh thu ngày/chi nhánh | Sales projection + Query API | `OrderPaid.v1`, `sales_daily`, `sales_by_branch`, các endpoint overview/daily/branch | Fixture doanh thu chuẩn tắc, trùng lặp/phát lại/điều chỉnh, phạm vi Owner/Manager |
| P0 món bán chạy | Top-item projection | `top_items`, phân bổ doanh thu dòng thuần, quy tắc sắp xếp/hòa điểm | Sắp xếp theo số lượng/doanh thu, phá vỡ hòa điểm, snapshot lịch sử món đã ngừng bán |
| P1 doanh thu theo giờ | Hour projection | `sales_by_hour`, chính sách múi giờ cục bộ chi nhánh / tenant | Fixture múi giờ nửa đêm/DST/đa chi nhánh, hiệu năng truy vấn có chặn khoảng |
| P1 doanh thu theo nhân viên | Employee projection | Snapshot checkout actor, `UNASSIGNED`, phiên bản ghi nhận | Fixture đổi ca nhanh/vô hiệu hóa/thiếu nhân viên, cô lập chi nhánh |
| P1 hiệu quả khuyến mãi | Promotion projection | Phiên bản khuyến mãi/quy tắc bất biến, trạng thái phân bổ | Fixture nhiều phiên bản/nhóm xung đột/chưa phân bổ/điều chỉnh |
| P1 hiệu quả gợi ý | Recommendation projection | Sự kiện hiển thị/chấp nhận, nhóm chỉ tính đơn thanh toán, chênh lệch AOV quan sát | Fixture trùng lặp/chấp nhận nhiều gợi ý/mẫu số đối chứng bằng 0/chờ sang thanh toán |
| Độ tươi mới / Điều chỉnh | Watermark + worker rebuild | DataQualityEnvelope, `asOfWatermark`, API/sự kiện rebuild | FRESH/STALE/PARTIAL/UNAVAILABLE/NO_DATA, điều chỉnh đến muộn và phát lại dữ liệu |
| Bảo mật Dashboard | Phạm vi Identity + chính sách repository | `report:read`, `reporting:operate`, khóa cache và điều kiện lọc | Kiểm thử IDOR, thu hồi membership, chi nhánh lạ, kiểm tra cache sau thu hồi |

## 9. Các Tu Chính Hệ Thống Gốc Bắt Buộc Trước Khi Triển Khai

- **Identity Access:** Bổ sung quyền `report:read` và `reporting:operate`, schema quyết định phân quyền chuẩn tắc, phạm vi tenant/tập con chi nhánh, phiên bản membership/phân quyền và ngữ nghĩa thu hồi quyền.
- **Order:** Mở rộng `OrderPaid.v1` với các trường bổ sung cho checkout actor, ngày/múi giờ kinh doanh, snapshot tổng tiền/dòng món và phân bổ chiết khấu khuyến mãi áp dụng; chọn và ban hành sự kiện hoàn tiền/điều chỉnh chuẩn tắc.
- **Loyalty/Promotion:** Bảo toàn phiên bản khuyến mãi/quy tắc bất biến, nhóm xung đột, nguồn voucher/khuyến mãi và phân bổ chiết khấu trong snapshot đơn thanh toán; Reporting không bao giờ đánh giá lại quy tắc hiện tại.
- **Recommendation:** Đảm bảo `SuggestionShown.v1`/`SuggestionAccepted.v1` có ID tương tác ổn định, cùng correlation đơn hàng/giỏ hàng, phiên bản mô hình/quy tắc, bằng chứng chấp nhận và thứ tự phát lại.
- **POS Platform:** Cập nhật danh mục service/read model/sự kiện của Reporting bao gồm toàn bộ các projection P0/P1, tác vụ độ tươi mới/rebuild và ranh giới quyền sở hữu.
- **Audit/Observability:** Chốt nguồn lưu trữ chuẩn (source-of-record) của Audit Service so với tầng tham chiếu/staging của Reporting; ban hành schema sự kiện truy cập, projection, rebuild và chất lượng dữ liệu đã ẩn PII.

## 10. Checklist Hoàn Tất

- [x] Tầm nhìn (Vision) đã được duyệt và lưu trữ.
- [x] Yêu cầu (Requirements) đã được duyệt kèm cảnh báo và có thể truy vết.
- [x] Kiến trúc (Architecture) đã được duyệt; các luồng tuần tự đã được xác nhận.
- [x] Hợp đồng (Contracts) đã được duyệt kèm cảnh báo; đánh giá Hợp đồng đã hoàn tất.
- [x] Các yêu cầu P0/P1 đã được ánh xạ đầy đủ sang thành phần, hợp đồng và bằng chứng nghiệm thu.
- [x] Phân quyền tenant/chi nhánh, cách ly cache, độ tươi mới, điều chỉnh, phát lại và DLQ đã được bao quát.
- [x] Lộ trình triển khai có đầy đủ các giai đoạn, phụ thuộc và tiêu chí hoàn thành.
- [ ] Các artifact thực thi OpenAPI/AsyncAPI/Flyway được cụ thể hóa.
- [ ] Các tu chính hệ thống gốc cho Identity/Order/Promotion/Recommendation/Platform được ban hành.
- [ ] Các kết quả đo lường tải, phát lại, kiểm thử bảo mật tiêu cực, quyền riêng tư và lưu trữ được thu thập.

Thiết kế Module 10 được xác nhận là **APPROVED WITH WARNINGS** để chuyển sang giai đoạn lập kế hoạch triển khai (implementation planning). Tuyệt đối không viết code trực tiếp từ văn bản mô tả hay từ khung cấu trúc cũ của hệ thống cha; sử dụng Hợp đồng, từ điển chỉ số, schema được sinh ra và các cổng triển khai tại Mục 6 làm nguồn chuẩn duy nhất.
