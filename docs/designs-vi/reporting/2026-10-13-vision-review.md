Started at: 2026/10/13 00:00:00
Finished at: 2026/10/13 00:00:00
Total time: 0 minutes
---

# Báo Cáo Đánh Giá Tầm Nhìn (Spec Sentinel Report) — Báo Cáo & Dashboard

**Tài liệu tầm nhìn:** [`docs/designs-vi/reporting/2026-10-13-vision.md`](file:///d:/vibe-code/pos/docs/designs-vi/reporting/2026-10-13-vision.md)  
**Ngày:** 13-10-2026  
**Trạng thái:** ⚠️ Cảnh báo (Tiến hành với sự thận trọng)

## Ngữ cảnh đánh giá (Review context)

- Bỏ qua Phase 0 — tái sử dụng quyết định phạm vi Module 10, tài liệu Tầm nhìn đã được phê duyệt và ngữ cảnh nền tảng POS Platform trong phiên làm việc hiện tại.
- Quét toàn bộ repository: Hiện chưa có mã nguồn ứng dụng hay triển khai cho Reporting. Các tài liệu thiết kế liên quan gồm hợp đồng POS Platform, Order, Loyalty và Recommendation.
- Bản Tầm nhìn (Vision) đã đủ rõ ràng để chuyển sang bước Yêu cầu (Requirements), tuy nhiên một số hợp đồng dữ liệu liên service bắt buộc phải được cụ thể hóa thành hợp đồng thực thi trong các bước Requirements / Architecture / Contracts tiếp theo.

## Các phát hiện

### Cảnh báo (Warnings)

| # | Phân loại | Phát hiện | Rủi ro | Khuyến nghị |
|---|---|---|---|---|
| 1 | Hợp đồng nguồn dữ liệu báo cáo | Nền tảng POS Platform hiện tại mới ghi nhận `sales_daily` và `top_items`, trong khi Vision này bổ sung thêm doanh thu theo giờ, doanh thu theo nhân viên, hiệu quả khuyến mãi và hiệu quả gợi ý món. | Bước Requirements có thể tự ý bịa đặt các read model không tương thích hoặc truy vấn trực tiếp vào database nghiệp vụ, phá vỡ ranh giới sở hữu dịch vụ. | Định nghĩa rõ các read model do Reporting sở hữu, sự kiện đầu vào, từ điển chỉ số, phiên bản hình chiếu và envelope độ tươi/độ đầy đủ trước khi làm Architecture. |
| 2 | Quy kết nhân viên thu ngân | Vision chọn thu ngân đang active tại thời điểm checkout thành công, nhưng bản tóm tắt canonical `OrderPaid.v1` hiện tại chưa bảo đảm một trường quy kết nhân viên chuẩn mực và ngữ nghĩa phiên bản. | Doanh thu nhân viên có thể bị gán sai khi đổi ca/thu ngân nhanh hoặc khi phát lại (replay) sự kiện. | Bổ sung trường tham chiếu `checkoutActor` / nhân viên bất biến cùng chính sách quy kết vào hợp đồng `OrderPaid.v1`; định nghĩa ứng xử khi nhân viên bị thiếu, thay đổi hoặc bị vô hiệu hóa. |
| 3 | Quy kết chương trình khuyến mãi | Yêu cầu khuyến mãi phụ thuộc vào báo giá Loyalty/Promotion, voucher, phiên bản luật và phân bổ giảm giá, nhưng dữ liệu đầu vào cho Reporting và cách xử lý hoàn tiền / điều chỉnh chưa được định nghĩa thành một hợp đồng dữ kiện duy nhất. | Số tiền giảm giá, doanh thu ròng và chỉ số ROI của khuyến mãi có thể sai lệch so với lịch sử thanh toán thực tế. | Tiêu thụ các dữ kiện khuyến mãi đã áp dụng bất biến hoặc ảnh chụp trong `OrderPaid.v1` có phân rã khuyến mãi; định nghĩa rõ ngữ nghĩa nhóm xung đột, phân bổ giảm giá, hoàn tác và điều chỉnh trễ. |
| 4 | Quy kết gợi ý món (Recommendation) | `SuggestionAccepted` chỉ là tín hiệu quy kết và `OrderPaid.v1` là chuẩn mực cuối cùng, nhưng Vision chưa định nghĩa tập đối chứng đủ điều kiện (eligible-control), cửa sổ quy kết hay cách xử lý nhiều gợi ý trong một giỏ hàng. | Tỷ lệ chấp thuận hoặc mức tăng trưởng AOV có thể bị sai lệch, tính trùng lặp và không thể tái lập kết quả. | Định nghĩa rõ liên kết hiển thị/chấp thuận/đơn hàng, tập đối chứng không chấp thuận, cửa sổ thời gian quy kết, xử lý một/nhiều gợi ý và phiên bản tổng hợp. |
| 5 | Cam kết SLO về độ tươi | Đặt mục tiêu dashboard ≤ 1 phút, trong khi nền tảng parent hiện mô tả mục tiêu lan truyền sự kiện rộng hơn và dùng hàng đợi ít nhất một lần. | Giao diện UI có thể cam kết độ tươi mà hình chiếu không thể bảo đảm khi hàng đợi tồn đọng, đang phát lại dữ liệu hoặc nhà cung cấp gặp sự cố. | Định nghĩa rõ ngưỡng độ tươi bình thường / suy giảm, sự chuyển đổi trạng thái trễ, quy tắc tính độ đầy đủ, hệ thống cảnh báo và ý nghĩa chuẩn xác của `FRESH` / `STALE` / `PARTIAL`. |
| 6 | Múi giờ và kỳ so sánh | Các chỉ số theo ngày / theo giờ / theo chi nhánh cần ngày kinh doanh cục bộ của chi nhánh, múi giờ so sánh xuyên chi nhánh và ứng xử với giờ mùa hè (DST); Vision đang liệt kê đây là ẩn số. | Ranh giới ngày và tổng doanh thu theo giờ có thể không khớp giữa dashboard, Order và vận hành thực tế tại chi nhánh. | Chọn một chính sách múi giờ chuẩn tắc và yêu cầu mọi phản hồi tổng hợp đều phải trả về ngày kinh doanh, múi giờ, ranh giới chu kỳ và phiên bản tính toán. |
| 7 | Điều chỉnh dữ liệu lịch sử | Việc hoàn tiền, sửa sai và sự kiện đến trễ có thể làm thay đổi doanh thu và quy kết trong quá khứ, nhưng Vision mới chỉ nêu cơ chế replay/rebuild ở mức khái niệm. | Số liệu trên dashboard có thể bị thay đổi mà không có phả hệ truy vết, hoặc việc replay làm tính trùng lặp số liệu. | Định nghĩa sự kiện nguồn bất biến, khóa khử trùng lặp, phả hệ điều chỉnh/sửa đổi, watermark tái dựng và quy định rõ dashboard hiển thị giá trị hiện tại hay giá trị tại thời điểm phát sinh (as-of). |
| 8 | Ranh giới truy vấn dữ liệu 12 tháng | PostgreSQL được chọn phục vụ lưu lượng dashboard và Vision cho phép xem tối đa 12 tháng, nhưng chiến lược phân vùng / chỉ mục / tổng hợp trước chưa được định nghĩa. | Các truy vấn quét lớn xuyên nhiều chi nhánh có thể vi phạm mục tiêu độ trễ hoặc làm quá tải read model vận hành. | Thiết kế các read model ưu tiên truy vấn (query-first), các chỉ mục / phân vùng và bảng tổng hợp trước (pre-aggregates); xác định ngưỡng dung lượng cụ thể để chuyển sang Athena. |
| 9 | Phân quyền trên Dashboard | Giao diện dùng chung cho Owner/Manager và có tính năng drill-down, nhưng việc phân quyền mức tài nguyên cho từng bộ lọc, danh sách chi nhánh và đường dẫn drill-down chưa được đặc tả. | Một endpoint dashboard hợp lệ có thể làm lộ dữ liệu chi nhánh hoặc nhân viên trái phép thông qua tham số truy vấn hoặc dữ liệu cache. | Yêu cầu phân giải phạm vi quyền trực tuyến qua Identity, áp dụng vị từ ở repository, cách ly cache key theo phạm vi và bổ sung fixture kiểm thử phủ định IDOR cho toàn bộ tài nguyên báo cáo. |

## Phân tích tác động chéo (Cross-Impact Analysis)

| Chiều phân tích | Phát hiện | Mức độ rủi ro | Khuyến nghị |
|---|---|---|---|
| **Logic dùng chung** | Reporting tiêu thụ `OrderPaid.v1`, `SuggestionShown.v1`, `SuggestionAccepted.v1`, dữ kiện khuyến mãi/giảm giá và ngữ cảnh chi nhánh/nhân viên; bên tiêu thụ gồm Dashboard, AI Assistant và Audit. | 🔴 Cao | Xử lý từng sự kiện như một API có đánh phiên bản; bổ sung kiểm thử tương thích nguồn/bên tiêu thụ và tuyệt đối không trích xuất dữ liệu trực tiếp từ database của service khác. |
| **Dữ liệu / Trạng thái** | Các read model mới cho doanh thu ngày/chi nhánh/giờ, top món, nhân viên, khuyến mãi và gợi ý; các hình chiếu phải có khả năng phát lại an toàn. | 🔴 Cao | Định nghĩa khóa tenant/chi nhánh, ID sự kiện nguồn, phiên bản hình chiếu, watermark, phả hệ điều chỉnh và quy tắc upsert lũy nghiệm. |
| **Hợp đồng API** | Các endpoint báo cáo dashboard mới kèm bộ lọc, drill-down, phân trang cursor, trạng thái độ tươi và dữ liệu một phần (partial data). | 🟡 Trung bình | Sử dụng chuẩn REST/OpenAPI 3.1, bộ lọc nằm trong allowlist, cache key nhận biết phạm vi quyền và envelope chất lượng dữ liệu/mã lỗi rõ ràng. |
| **Tính toàn vẹn luồng** | Luồng Sự kiện → thẩm định/khử trùng lặp → hình chiếu → độ tươi → API → dashboard; các nhánh phát lại và điều chỉnh sự kiện trễ chưa có kịch bản thực thi. | 🟡 Trung bình | Bổ sung sơ đồ tuần tự và bao phủ trạng thái / fixture cho các luồng: trùng lặp, sai thứ tự, trễ dữ liệu, dữ liệu một phần và tái dựng (rebuild). |
| **Khía cạnh xuyên suốt** | Yêu cầu đa tenant, hiệu năng dashboard, tối thiểu hóa PII cho dữ kiện gắn với nhân viên/khách hàng, khả năng quan sát và lưu trữ dữ liệu đều liên quan mật thiết. | 🟡 Trung bình | Định nghĩa payload báo cáo đã che mờ, giới hạn truy vấn, cảnh báo độ trễ lag, nhật ký audit truy cập và thời hạn lưu trữ theo từng read model. |

## Tuân thủ chỉ dẫn (Instruction Compliance)

| Tiêu chí | Kết luận | Sai lệch (nếu có) | Khuyến nghị |
|---|---|---|---|
| **Quy ước** | ✅ | Bản Tầm nhìn tuân thủ định hướng CQRS, cơ sở dữ liệu riêng cho từng service, kiến trúc hướng sự kiện, REST/OpenAPI, UUID v7 và UTC của hệ thống nền tảng. | — |
| **Kiến trúc** | ⚠️ | Quyền sở hữu của Reporting đã rõ ràng, nhưng ranh giới cụ thể của sự kiện đầu vào và read model được chủ ý hoãn lại cho bước Requirements. | Giải quyết dứt điểm quyền sở hữu dữ kiện nguồn và nguyên tắc không truy vấn chéo database trong Requirements/Architecture. |
| **Kiểm thử** | ⚠️ | Tiêu chí thành công đã có, nhưng các fixture có thể thực thi cho chỉ số, phát lại sự kiện, phân quyền, độ tươi và tương tác dashboard chưa được định nghĩa. | Bổ sung các ma trận kiểm thử hợp đồng, phát lại hình chiếu, bảo mật, tải và nghiệm thu giao diện UI trong Requirements. |

## Kết luận (Verdict)

Bản Tầm nhìn (Vision) **có thể tiếp tục sang bước Khảo sát yêu cầu (Requirements) với các cảnh báo**. Không có điểm nghẽn về phạm vi nghiệp vụ hay định hướng phát triển dashboard. Bước Requirements bắt buộc phải giải quyết triệt để các cảnh báo về hợp đồng sự kiện/nguồn dữ liệu, cơ chế quy kết, múi giờ/độ tươi, điều chỉnh lịch sử, mở rộng quy mô truy vấn và phân quyền drill-down trước khi phê duyệt Architecture.
