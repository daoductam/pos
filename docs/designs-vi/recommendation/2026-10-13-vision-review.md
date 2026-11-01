# Báo Cáo Đánh Giá Tầm Nhìn (Spec Sentinel Report) - Recommendation Service

**Tài liệu tầm nhìn:** [`docs/designs-vi/recommendation/2026-10-13-vision.md`](file:///d:/vibe-code/pos/docs/designs-vi/recommendation/2026-10-13-vision.md)  
**Ngày:** 13-10-2026  
**Trạng thái:** ⚠️ Cảnh báo (Tiến hành với sự thận trọng)

## Ngữ cảnh đánh giá

- Bỏ qua Phase 0 — tái sử dụng phạm vi và ngữ cảnh từ phiên thiết kế hiện tại.
- Bản tầm nhìn (Vision) đã được người dùng xác nhận sau khi làm rõ hành vi P0/P1/P2 và chọn kiến trúc Hybrid Recommendation Service.
- Đối chiếu chéo với các tài liệu nền tảng POS Platform, Order, Catalog và Loyalty.
- Hiện chưa có mã nguồn triển khai Recommendation/ML trong repository; mới chỉ có khung skeleton API/schema của parent.

## Các phát hiện

## Cảnh báo (Warnings)

| # | Phân loại | Phát hiện | Rủi ro | Khuyến nghị |
|---:|---|---|---|---|
| 1 | Ranh giới API / sự kiện | Vision yêu cầu frontend xác nhận `SuggestionShown` sau khi render và `SuggestionAccepted` sau khi Order thêm món thành công, nhưng hợp đồng parent hiện mới có endpoint recommendation/accept và bảng sự kiện cơ bản. | Số lượt hiển thị (impression) hoặc chấp thuận (acceptance) có thể bị ghi nhận quá sớm, trùng lặp hoặc không phản ánh đúng kết quả thực tế trên UI / Order. | Requirements/Contracts phải định nghĩa rõ ràng lệnh hiển thị/chấp thuận, tính lũy nghiệm tương tác và mối liên kết correlation với phiên bản đơn hàng / kết quả thêm món của Order. |
| 2 | Mô hình dữ liệu luật | Bảng `association_rules` của parent mới chỉ mô tả quan hệ một món điều kiện (antecedent item) → một món kết quả (consequent item) và điểm tin cậy; Vision lại yêu cầu giỏ hàng nhiều món, biến thể/topping, support/lift, mức ưu tiên và phiên bản. | Động cơ luật (Rule engine) có thể không biểu diễn chính xác ngữ cảnh giỏ hàng hoặc xếp hạng mang tính tất định. | Mở rộng schema của luật với tập vế điều kiện (antecedent set/signature), loại định danh ứng viên, các chỉ số thống kê, phiên bản/trạng thái/phạm vi hiệu lực; vẫn giữ chính sách áp dụng toàn tenant. |
| 3 | Độ tươi của dữ liệu khả dụng | Recommendation dựa trên hình chiếu (projection) từ Catalog/Inventory nhưng cấu trúc sự kiện, SLA về độ tươi và ứng xử khi hình chiếu quá cũ chưa được chốt cụ thể. | Có thể đề xuất món đã hết hàng hoặc loại nhầm món còn bán; trải nghiệm người dùng (UX) không đồng nhất giữa danh sách gợi ý và Order. | Định nghĩa rõ các sự kiện nguồn, watermark/TTL của hình chiếu, chính sách khi dữ liệu cũ và bắt buộc Order phải kiểm tra thẩm định lần cuối trước khi thêm món. |
| 4 | Cá nhân hóa và quyền riêng tư | Sự kiện `OrderPaid.v1` có thể không mang `customerId`; feature key từ Loyalty, sự đồng thuận (consent), chính sách lưu trữ và ngưỡng tín hiệu tối thiểu cho ALS chưa có hợp đồng cụ thể. | Dữ liệu mô hình bị thưa thớt, rò rỉ danh tính khách hàng hoặc huấn luyện nhầm tenant / khách hàng chưa cấp quyền đồng thuận. | Chốt hợp đồng feature key dạng opaque, chính sách khách hàng đủ điều kiện, ngưỡng tương tác tối thiểu, ảnh chụp huấn luyện phân lập theo tenant và hành vi lưu trữ/xóa dữ liệu. |
| 5 | Vận hành mô hình ML | Việc đăng ký mô hình (model registry), mã hóa artifact, chỉ số thẩm định chất lượng, chính sách rollback/lưu trữ và quyền sở hữu tác vụ chạy đêm mới chỉ dừng ở mức nguyên tắc. | Mô hình lỗi hoặc không tương thích có thể vô tình được kích hoạt hoặc không thể rollback / tái lập kết quả. | Architecture/Contracts phải định nghĩa vòng đời mô hình, các cổng kiểm soát chất lượng, metadata artifact, con trỏ mô hình đang chạy (active pointer), cơ chế rollback và trạng thái tác vụ qua DLQ. |
| 6 | AI ngoại vi (DeepSeek) | Tính năng P2 với DeepSeek cần đặc tả rõ prompt/schema, phạm vi dữ liệu được phép gửi ra ngoài, giới hạn tần suất/chi phí (rate/cost limit), phương án khi nhà cung cấp gặp sự cố và quy trình con người duyệt. | Có thể gửi nhầm thông tin cá nhân (PII) hoặc dữ liệu thực đơn không phù hợp, hoặc sinh ra các thẻ/đặc trưng không an toàn. | Giữ adapter xử lý bất đồng bộ; chỉ gửi metadata Catalog đã được khử độc, kiểm tra chặt chẽ cấu trúc dữ liệu đầu ra, lưu nguồn gốc và trạng thái phê duyệt; tuyệt đối không chặn luồng P0/P1. |
| 7 | Quản trị luật (Rule governance) | Vision chưa chốt ai có quyền tạo/sửa/công bố luật kết hợp và cách thức lưu vết kiểm toán (audit). | Quản lý hoặc Chủ chuỗi có thể tạo các luật mâu thuẫn giữa các chi nhánh hoặc thay đổi thứ tự xếp hạng mà không thể truy vết nguyên nhân. | Requirements cần định nghĩa quyền của Owner/Manager, quyền sở hữu cấp tenant, quy trình dự thảo (draft) / công bố (publish) / phiên bản / audit. |

## Phân tích tác động chéo (Cross-Impact Analysis)

| Chiều phân tích | Phát hiện | Mức độ rủi ro | Khuyến nghị |
|---|---|---|---|
| **Logic dùng chung** | Recommendation tiêu thụ các sự kiện `OrderCreated`/`OrderPaid` từ Order, sự kiện khả dụng từ Catalog và sự kiện tồn kho từ Inventory; phát các sự kiện Suggestion cho Reporting/Audit. | 🟡 Trung bình | Xử lý các schema sự kiện như các API có đánh phiên bản; bổ sung fixture cho consumer và kiểm thử khả năng phát lại / khử trùng lặp. |
| **Dữ liệu / Trạng thái** | Phát sinh các thực thể mới: luật, hình chiếu, phiên/sự kiện gợi ý, metadata/artifact mô hình và trạng thái lần chạy huấn luyện; hiện chưa có bảng triển khai nào. | 🔴 Cao | Áp dụng bắt buộc các vị từ tenant rõ ràng, phả hệ sự kiện bất biến, con trỏ phiên bản mô hình và các hình chiếu có khả năng tái dựng hoàn toàn từ sự kiện gốc. |
| **Hợp đồng API** | Các route gợi ý của parent chưa hoàn thiện ngữ nghĩa xác nhận hiển thị và xác nhận thêm món thành công; API tra cứu đặc trưng từ Loyalty chưa được định nghĩa. | 🔴 Cao | Giải quyết triệt để trong Requirements/Contracts trước khi sinh client hoặc tích hợp giao diện frontend. |
| **Tính toàn vẹn luồng** | Luồng Giỏ hàng → luật/bộ lọc → render → Order thêm món → ghi nhận chấp thuận đã hoàn chỉnh về mặt khái niệm; các nhánh dữ liệu khả dụng bị cũ và lỗi thêm món cần đặc tả rõ hành vi phản hồi/lệnh. | 🟡 Trung bình | Bổ sung sơ đồ tuần tự và fixture nghiệm thu cho các kịch bản hình chiếu bị trễ, thêm món thất bại, thử lại và gợi ý hết hạn. |
| **Khía cạnh xuyên suốt** | Yêu cầu nghiêm ngặt về phân lập tenant, tối thiểu hóa thông tin cá nhân (PII), tính tái lập của mô hình ML, cơ chế rollback mô hình, độ trễ và khả năng quan sát của worker bất đồng bộ. | 🔴 Cao | Định nghĩa rõ chính sách quyền riêng tư / lưu trữ dữ liệu, các cổng chất lượng, chỉ số giám sát, cơ chế DLQ / phát lại và ngân sách độ trễ p95 / lỗi trong Requirements. |

## Tuân thủ chỉ dẫn (Instruction Compliance)

| Tiêu chí | Kết luận | Sai lệch (nếu có) | Khuyến nghị |
|---|---|---|---|
| **Quy ước** | ✅ | Vision tuân thủ cấu trúc `docs/designs/recommendation/`, ranh giới REST/sự kiện/database và quy ước UUID/UTC của hệ thống nền tảng. | — |
| **Kiến trúc** | ⚠️ | Quyền sở hữu hình chiếu/mô hình của Recommendation đã rõ, nhưng khung skeleton sự kiện/API/schema của parent cần được chuyên biệt hóa chi tiết. | Làm rõ cấu trúc sự kiện, schema luật và vòng đời mô hình trước khi phê duyệt Architecture. |
| **Kiểm thử** | ⚠️ | Tiêu chí thành công và rủi ro đã có, nhưng các fixture có thể thực thi cho đánh giá mô hình ML, độ tươi của hình chiếu và tương tác UI/Order chưa được định nghĩa. | Bổ sung các fixture unit test, contract test, replay, quyền riêng tư, thẩm định mô hình và nghiệm thu E2E trong Requirements/Contracts. |

## Kết luận (Verdict)

Bản tầm nhìn (Vision) **có thể tiếp tục sang bước Khảo sát yêu cầu (Requirements) với các cảnh báo**. Không có điểm nghẽn về phạm vi, quyền sở hữu hay ranh giới mức độ ưu tiên. Các điểm bắt buộc phải xử lý trước khi viết mã nguồn là hợp đồng xác nhận hiển thị/chấp thuận, schema luật kết hợp, độ tươi của dữ liệu khả dụng, hợp đồng feature key từ Loyalty và quy trình vận hành mô hình ML / tích hợp DeepSeek.
