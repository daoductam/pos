Started at: 2026/10/13 00:00:00
Finished at: 2026/10/13 00:00:00
Total time: 0 minutes
---

# Báo Cáo Spec Sentinel — Đánh Giá Hợp Đồng API & Dữ Liệu Reporting

**Tài liệu Spec:** [`docs/designs-vi/reporting/2026-10-13-contracts.md`](file:///d:/vibe-code/pos/docs/designs-vi/reporting/2026-10-13-contracts.md)  
**Ngày:** 2026-10-13  
**Trạng thái:** ⚠️ Cảnh báo (Tiếp tục một cách thận trọng)

## Phạm Vi Đánh Giá

- Giai đoạn Phase 0 đã bỏ qua — tái sử dụng Tầm nhìn (Vision), Yêu cầu (Requirements), Kiến trúc (Architecture) và ngữ cảnh biểu đồ tuần tự đã được duyệt trong quy trình hiện tại.
- Đối chiếu Hợp đồng (Contracts) với nền tảng POS Platform gốc, Identity Access, Order, Loyalty/Promotion và Recommendation.
- Repository hiện chưa có mã nguồn ứng dụng, OpenAPI/AsyncAPI sinh ra hay mã nguồn migration; bản đánh giá này kiểm tra tính tương thích của hợp đồng thiết kế và việc liên kết xuyên service, không phải review mã nguồn triển khai.
- Đã áp dụng các quy tắc hiện có trong `.agent/rules/security.md` và `.agent/rules/code-quality.md`.

## Các Phát Hiện

### Cảnh Báo

| # | Hạng mục | Phát hiện | Rủi ro | Khuyến nghị |
|---|---|---|---|---|
| 1 | Đồng bộ sự kiện hệ thống gốc | Sự kiện `OrderPaid.v1` gốc hiện mới có snapshot dòng món, tổng tiền, tiền tệ, thời điểm thanh toán và phiên bản đơn hàng; Reporting cần thêm `businessDate`, timezone, đối tượng checkout và phân bổ khuyến mãi áp dụng. | Producer và Reporting có thể hiểu khác nhau về ghi nhận nhân viên/khuyến mãi; dữ liệu dashboard tổng và drill-down có thể bị lệch. | Cụ thể hóa một bản tu chính bổ sung / JSON Schema duy nhất cho `OrderPaid.v1` của hệ thống gốc, kèm các fixture tương thích giữa producer/consumer; giữ `OrderPaid` là dữ kiện doanh thu chuẩn duy nhất. |
| 2 | Nguồn sự kiện điều chỉnh/hoàn tiền | Hợp đồng vẫn viết dạng mô tả chung "chẳng hạn như `OrderRefunded.v1` hoặc `OrderCorrected.v1`", chưa chốt tên sự kiện chuẩn tắc, trạng thái và ngữ nghĩa hoàn tiền toàn phần/bán phần. | Projection có thể áp dụng điều chỉnh hai lần hoặc xử lý bất đồng nhất giữa Order, Payment, Loyalty và Reporting. | Chọn một hợp đồng sự kiện nghiệp vụ chuẩn tắc (canonical business-fact contract) trước khi triển khai code; định nghĩa rõ ID sự kiện nguồn, ngày kinh doanh có hiệu lực, các dòng/số tiền bị ảnh hưởng, thứ tự ưu tiên hiệu chỉnh và idempotency key. Sự kiện từ nhà cung cấp thanh toán không được tự ý sửa doanh thu. |
| 3 | Hợp đồng phân quyền Identity | Reporting giới thiệu các quyền `report:read`/`reporting:operate` và gọi quyết định phân quyền trực tuyến, nhưng danh mục quyền và schema phản hồi của Identity gốc chưa có các quyền này cùng đầy đủ các trường phạm vi/phiên bản. | Chính sách hoặc client được sinh ra có thể cấp nhầm phạm vi tenant/chi nhánh, đặc biệt khi quan hệ thành viên bị thu hồi trong khi cache vẫn còn hiệu lực. | Bổ sung tu chính cho Identity gốc, chuẩn hóa schema request/response, các fixture kiểm thử TTL/thu hồi quyền và phân quyền workload cho tác vụ rebuild/kiểm tra sức khỏe nội bộ. |
| 4 | Danh mục nền tảng gốc | Nền tảng POS Platform gốc hiện mô tả Reporting với các bảng `sales_daily`, `sales_by_branch`, `top_items`; Contracts đã mở rộng thêm các bảng hourly, employee, promotion, recommendation, freshness, rebuild và các sự kiện phát hành. | Định tuyến nền tảng, ma trận quyền sở hữu và danh mục vận hành có thể bỏ sót các read model hoặc sự kiện mới. | Cập nhật danh mục service/sự kiện/read model của nền tảng gốc trước khi sinh hạ tầng; ghi rõ Reporting sở hữu các read model phái sinh nhưng không sở hữu dữ kiện nghiệp vụ. |
| 5 | Phả hệ gợi ý (Recommendation Lineage) | Bảng `recommendation_effectiveness` tổng hợp theo `dimension_key`, nhưng schema chưa lưu đủ phả hệ tương tác nguồn / ngữ cảnh đơn hàng để phát lại, audit và đối soát trạng thái từ chờ sang đã thanh toán. | Việc chấp nhận nhiều gợi ý, sự kiện trùng lặp/sai thứ tự hoặc sự kiện điều chỉnh có thể làm sai lệch nhóm phân tích mà không thể truy vết nguyên nhân. | Bổ sung khóa tương tác nguồn / ngữ cảnh đơn hàng hoặc một projection dữ kiện ghi nhận dạng append-only; lưu trữ chính sách/phiên bản và ràng buộc ngữ cảnh đơn hàng duy nhất, sau đó mới tổng hợp vào bảng truy vấn. |
| 6 | Quyền sở hữu read model và khóa kỳ báo cáo | Các bảng `sales_by_branch`, `sales_by_employee` và `promotion_effectiveness` sử dụng khoảng thời gian (period range) làm khóa cho dòng cụ thể hóa, trong khi truy vấn công khai lại có nhiều khoảng thời gian tùy biến. | Dễ phát sinh quá nhiều bản ghi theo từng truy vấn hoặc không xác định rõ bản ghi nào là nguồn sự thật khi các khoảng thời gian chồng lấn nhau. | Chốt các hạt nhân dữ liệu (grain) cố định của projection (ví dụ ngày/chi nhánh/nhân viên/khuyến mãi) và thực hiện tổng hợp khoảng thời gian ở tầng truy vấn; nếu giữ cụ thể hóa theo kỳ, phải quy định rõ phả hệ rebuild và tính duy nhất cho mọi trường hợp chồng lấn. |
| 7 | Ranh giới kiểm toán (Audit Boundary) | Reporting có bảng `report_access_audit` và phát sự kiện `ReportAccessAudited.v1`, trong khi POS Platform quy định Service Audit là chủ sở hữu lưu trữ append-only duy nhất. | Việc cả hai nơi cùng được coi là nguồn audit có thể dẫn đến sai lệch về chính sách lưu trữ (retention), lưu trữ pháp lý (legal hold) hoặc quyền truy cập. | Định nghĩa bảng của Reporting chỉ là outbox/staging/tham chiếu tối thiểu; Audit Service là nguồn lưu trữ chuẩn (source-of-record) cho việc truy vấn/lưu trữ dài hạn, hoặc làm rõ nếu audit do Reporting sở hữu là một ngoại lệ được phê duyệt. |
| 8 | Cổng máy đọc được và quy mô tải | Contracts đã nêu rõ OpenAPI/AsyncAPI, fixtures, cache/ETag và ngưỡng phân vùng là các artifact triển khai, nhưng chúng chưa tồn tại và ngưỡng tải/lưu trữ cho PostgreSQL chưa có số liệu đo lường cụ thể. | Code có nguy cơ được viết trực tiếp từ văn bản mô tả, dẫn đến sai lệch schema; tải truy vấn 12 tháng × 20 chi nhánh và điều chỉnh/rebuild có thể vi phạm SLO. | Giữ các artifact này là cổng kiểm soát bắt buộc trước khi code; bổ sung hồ sơ tải, ngưỡng số dòng/phân vùng, ngữ nghĩa chính xác của cursor/ETag và các fixture chuẩn cho múi giờ, sự kiện trùng lặp, điều chỉnh, các trạng thái bán phần/không khả dụng. |

## Phân Tích Tác Động Chéo (Cross-Impact Analysis)

| Chiều tác động | Phát hiện | Mức rủi ro | Khuyến nghị |
|---|---|---|---|
| **Logic Dùng Chung (Shared Logic)** | Chưa có điểm gọi mã nguồn triển khai thực tế; ranh giới dùng chung là `OrderPaid.v1`, sự kiện điều chỉnh/hoàn tiền, quyết định phân quyền Identity, sự kiện tương tác Recommendation và phong bì sự kiện chung. | 🟡 Trung bình | Quản lý các ranh giới này như các API có phiên bản; sinh schema/fixture dùng chung thay vì sao chép DTO thủ công giữa các service. |
| **Dữ Liệu/Trạng Thái (Data/State)** | Reporting bổ sung các bảng tổng hợp sẵn cho doanh số/nhân viên/khuyến mãi/gợi ý, bảng inbox, watermark, tác vụ rebuild và tham chiếu kiểm toán truy cập. | 🟡 Trung bình | Thực thi điều kiện lọc tenant, cơ chế dedupe duy nhất, watermark đơn điệu tăng, snapshot bất biến, hoán đổi projection nguyên tử và `COMMENT ON COLUMN` đầy đủ trong migration. |
| **Hợp Đồng API (API Contracts)** | Chín endpoint báo cáo công khai và ba endpoint vận hành nội bộ là mới; phạm vi phân quyền, nguồn sự kiện điều chỉnh và hạt nhân dữ liệu/kỳ báo cáo phụ thuộc vào các tu chính hệ thống gốc. | 🔴 Cao | Hoàn tất các tu chính cho Identity/Order/Platform rồi mới sinh OpenAPI 3.1; chạy kiểm thử tương thích consumer/provider trước khi viết controller. |
| **Tính Toàn Vẹn Luồng (Flow Integrity)** | `OrderPaid`/sự kiện tương tác → inbox/dedupe → projection → watermark/chất lượng dữ liệu → cache → dashboard đã phân quyền; điều chỉnh và rebuild phải cập nhật đúng chế độ xem hiện hành đã điều chỉnh. | 🟡 Trung bình | Cụ thể hóa các fixture L1/L2 cho các trường hợp trùng lặp/sai thứ tự, từ chờ sang đã thanh toán, điều chỉnh đến muộn, thu hồi quyền, sự cố Redis và hoán đổi rebuild. |
| **Mối Quan Tâm Xuyên Suốt (Cross-Cutting)** | Cách ly tenant/chi nhánh, chống rò rỉ cache, ngữ nghĩa dữ liệu cũ/bán phần, ẩn danh thông tin nhạy cảm (PII redaction), kiểm toán và hiệu năng đã được quy định rõ ràng. | 🔴 Cao | Phòng thủ chiều sâu tại Identity, chính sách truy vấn và repository; phân quyền trước khi đọc cache; bổ sung kiểm thử phủ định IDOR/cache/PII và cảnh báo độ tươi mới trước khi phát hành. |

## Tuân Thủ Hướng Dẫn Kỹ Thuật (Instruction Compliance)

| Chiều đánh giá | Kết luận | Độ lệch (nếu có) | Khuyến nghị |
|---|---|---|---|
| **Quy ước chuẩn (Conventions)** | ✅ | Đồng bộ chuẩn REST/OpenAPI 3.1, AsyncAPI/JSON Schema, UUID v7, UTC, số nguyên VND, phong bì chung, phân trang con trỏ và sự kiện at-least-once phù hợp với quy ước nền tảng. | — |
| **Kiến trúc (Architecture)** | ✅ | Reporting tuân thủ đúng ranh giới CQRS/read model, không truy vấn database chéo service và không trở thành nguồn sự thật cho Order/Payment/Promotion. | Coi các tu chính hệ thống gốc và các artifact máy đọc được là điều kiện tiên quyết bắt buộc, không đi tắt bằng cách chia sẻ DB hoặc query trực tiếp. |
| **Kiểm thử (Testing)** | ⚠️ | Ma trận kiểm thử hợp đồng rất rộng nhưng các fixture/schema được sinh ra và phả hệ điều chỉnh/phân quyền chính xác vẫn chưa được cụ thể hóa thành file. | Viết các fixture chuẩn, kiểm thử chuyển đổi trạng thái/phát lại, kiểm thử tương thích OpenAPI/AsyncAPI và kiểm thử tải/bảo mật trước khi viết code. |

## Kết Luận Đánh Giá (Verdict)

Tài liệu Hợp đồng (Contracts) **có thể được duyệt kèm các cảnh báo**. Không có rào cản ngăn chặn nào về phạm vi hay định hướng kiến trúc; toàn bộ các điểm còn tồn đọng đều đã được xác định thành các tu chính hệ thống gốc hoặc các artifact triển khai. Trước khi bắt đầu viết code, cần chốt sự kiện hoàn tiền/điều chỉnh chuẩn tắc, đồng bộ schema của `OrderPaid.v1`/Identity/Platform, xác định rõ hạt nhân dữ liệu/phả hệ của read model và tạo đầy đủ các đặc tả OpenAPI/AsyncAPI, migration, fixture cùng hồ sơ kiểm thử tải.

## Quyết Định Cần Người Dùng Phê Duyệt

Bạn có đồng ý phê duyệt Hợp đồng (Contracts) kèm các cảnh báo để chuyển sang bước **Hoàn tất (Finalization)** không?
