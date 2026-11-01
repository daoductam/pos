# Báo Cáo Đánh Giá Yêu Cầu (Spec Sentinel Report) - Recommendation Service

**Tài liệu yêu cầu:** [`docs/designs-vi/recommendation/2026-10-13-requirements.md`](file:///d:/vibe-code/pos/docs/designs-vi/recommendation/2026-10-13-requirements.md)  
**Ngày:** 13-10-2026  
**Trạng thái:** ⚠️ Cảnh báo (Tiến hành với sự thận trọng)

## Ngữ cảnh đánh giá

- Tái sử dụng bản Tầm nhìn Recommendation và tài liệu đánh giá Tầm nhìn đã được phê duyệt.
- Áp dụng các bộ quy tắc kiểm tra `spec-reviewer`, `api-design`, `database-design` cùng các tiêu chuẩn quan sát/quyền riêng tư.
- Đối chiếu chéo với nền tảng POS Platform, Order, Catalog, Loyalty và khung skeleton Recommendation hiện có.
- Hiện chưa có mã nguồn triển khai Recommendation/ML, artifact OpenAPI hay kịch bản migration nào trong repository.

## Các phát hiện

## Cảnh báo (Warnings)

| # | Phân loại | Phát hiện | Rủi ro | Khuyến nghị |
|---:|---|---|---|---|
| 1 | Quy kết đơn hàng (Order attribution) | Yêu cầu đòi hỏi `SuggestionAccepted` chỉ được phát sau khi thêm dòng thành công và có `orderLineId/orderVersionAfterAdd`, nhưng Order của parent chưa có sự kiện hoặc trường quy kết chuẩn cho gợi ý. | UI có thể tự khai báo thành công hoặc sự kiện chấp thuận không liên kết được với dòng đơn hàng được commit thực tế. | Architecture/Contracts phải chọn một nguồn chuẩn: sự kiện thêm dòng có quy kết từ Order hoặc bằng chứng đột biến có chữ ký; chỉ phát sự kiện chấp thuận duy nhất một lần theo khóa nguồn. |
| 2 | Hợp đồng đặc trưng Loyalty | Khái niệm `customerId` opaque / feature key, sự đồng thuận, ngưỡng tín hiệu tối thiểu và hành vi lưu trữ/xóa dữ liệu chưa tồn tại trong hợp đồng parent của Loyalty. | Mô hình ALS có thể bị thiếu dữ liệu, huấn luyện nhầm khách hàng hoặc xử lý danh tính ngoài phạm vi chính sách. | Định nghĩa hợp đồng nội bộ về ngữ cảnh đặc trưng từ Loyalty, kiểm tra tenant/sự đồng thuận và vòng đời dữ liệu trước khi triển khai P1. |
| 3 | Độ tươi của hình chiếu | Đã nêu TTL 60s và chính sách loại bỏ ứng viên khi dữ liệu cũ, nhưng phiên bản sự kiện Catalog/Inventory, ngữ nghĩa khả dụng và cơ chế khôi phục từ ảnh chụp chưa có schema cụ thể. | Danh sách ứng viên có thể sai lệch giữa Recommendation và Order, hoặc hình chiếu không thể tự động tái dựng (rebuild). | Architecture chọn chiến lược checkpoint/rebuild sự kiện; Contracts định nghĩa phiên bản nguồn, metadata độ tươi và hành vi phản hồi khi dữ liệu trễ. |
| 4 | Biểu diễn cấu trúc luật | Vế điều kiện nhiều món (multi-item antecedent), biến thể/topping, định danh chuẩn tắc của ứng viên và kiểm tra tính hợp lệ của các chỉ số chưa có schema / API shape cụ thể. | Động cơ luật và giao diện chỉnh sửa luật có thể hiểu khác nhau; kết quả xếp hạng không thể tái lập (reproducible). | Contracts định nghĩa chữ ký giỏ hàng chuẩn tắc, phiên bản luật / phạm vi hiệu lực, dải giá trị support/confidence/lift và cơ chế phá vỡ thế hòa tất định. |
| 5 | Chính sách chất lượng mô hình | Yêu cầu đã nêu Recall@K/NDCG@K/coverage nhưng chưa chốt chỉ số bắt buộc, đơn vị sở hữu ngưỡng chất lượng hay cửa sổ dữ liệu đánh giá. | Mô hình ứng viên có thể được kích hoạt dựa trên cổng chất lượng không nhất quán giữa các tenant hoặc các lần chạy. | Architecture/Contracts lưu trữ phiên bản chính sách + các cổng kiểm soát bắt buộc (hard gates); lần chạy ban đêm phải fail-closed nếu thiếu chỉ số. |
| 6 | Quản trị dữ liệu ML | Việc khử trùng lặp ảnh chụp, điều chỉnh sự kiện, lan truyền xóa khách hàng và chính sách lưu trữ artifact mới chỉ dừng ở mức bất biến lý thuyết. | Việc tái dựng/chạy lại có thể sinh ra mô hình khác hoặc giữ lại đặc trưng sau khi khách hàng đã bị xóa/khóa. | Định nghĩa manifest ảnh chụp bất biến, watermark nguồn, xử lý bản ghi đánh dấu xóa (tombstone), tác vụ lưu trữ/thanh lọc và metadata phục vụ tái lập kết quả. |
| 7 | Vận hành nhà cung cấp P2 | Luồng DeepSeek bất đồng bộ có kiểm tra schema/xét duyệt con người nhưng chưa có ngân sách chi phí cụ thể, cơ chế đánh phiên bản prompt và hợp đồng hàng đợi phê duyệt. | Chi phí/tần suất gọi có thể tăng đột biến hoặc dữ liệu không an toàn phát sinh mà không có cơ chế phục hồi có giới hạn. | Chốt giao diện adapter, schema payload đã khử độc, ngân sách/giới hạn tần suất, cơ chế thử lại qua DLQ và trạng thái phê duyệt trong Contracts. |
| 8 | Tính nhất quán của API | Parent có `POST /recommendations/{suggestionId}/accept`; Requirements bổ sung `/shown` và các lệnh quản trị luật/mô hình nhưng chưa có đầy đủ ví dụ request/response/lỗi. | Client sinh tự động hoặc giao diện frontend có thể gửi bằng chứng / khóa lũy nghiệm khác nhau. | Contracts phải đóng vai trò nguồn chân lý API-first với đầy đủ cấu phần / ví dụ OpenAPI cho toàn bộ đường dẫn lệnh và bộ lọc cursor. |

## Phân tích tác động chéo (Cross-Impact Analysis)

| Chiều phân tích | Phát hiện | Mức độ rủi ro | Khuyến nghị |
|---|---|---|---|
| **Logic dùng chung** | Recommendation tiêu thụ dữ kiện từ Order, Catalog, Inventory, Loyalty và phát sự kiện sang Reporting/Audit; hiện chưa có call site triển khai nào tồn tại. | 🔴 Cao | Xử lý toàn bộ cấu trúc sự kiện/API như các hợp đồng có đánh phiên bản; bổ sung fixture producer/consumer trước khi sinh mã client. |
| **Dữ liệu / Trạng thái** | Các thực thể: luật, hình chiếu, tương tác, ảnh chụp huấn luyện, model registry và ứng viên cold-start đòi hỏi phả hệ dữ liệu bất biến và khả năng tái dựng hoàn toàn. | 🔴 Cao | Sử dụng các ràng buộc phạm vi tenant, watermark nguồn, lưu tương tác theo kiểu chỉ ghi nối (append-only) và con trỏ CAS cho mô hình active. |
| **Hợp đồng API** | Bằng chứng hiển thị/chấp thuận mới, quản trị luật, trạng thái mô hình và ngữ cảnh đặc trưng Loyalty mở rộng đáng kể bề mặt API của parent. | 🔴 Cao | Chốt chính xác schema / trạng thái / phân quyền và cập nhật lại các phần tương ứng trong tài liệu parent Order/Loyalty/Recommendation trước khi viết code. |
| **Tính toàn vẹn luồng** | Các luồng: Giỏ hàng → lọc → render → thêm món → chấp thuận; sự kiện thanh toán → snapshot → huấn luyện → thẩm định → kích hoạt/fallback đã được bao quát, có ghi nhận thiếu sót về bằng chứng nguồn. | 🟡 Trung bình | Bổ sung sơ đồ tuần tự cho các kịch bản thành công/thất bại, hình chiếu bị trễ, tranh chấp khi chấp thuận, rollback và phát lại sự kiện huấn luyện. |
| **Khía cạnh xuyên suốt** | Yêu cầu nghiêm ngặt về phân lập tenant, tối thiểu hóa PII, tính tái lập của ML, an toàn prompt, độ trễ và khả năng quan sát của tác vụ nền nhưng cần tham số vận hành cụ thể. | 🔴 Cao | Đánh phiên bản và lưu vết kiểm toán cho các cấu hình ngưỡng/TTL/lưu trữ/ngân sách; bổ sung cảnh báo và kiểm thử xóa dữ liệu riêng tư. |

## Tuân thủ chỉ dẫn (Instruction Compliance)

| Tiêu chí | Kết luận | Sai lệch (nếu có) | Khuyến nghị |
|---|---|---|---|
| **Quy ước** | ✅ | Tuân thủ REST/OpenAPI, UUID v7, UTC, envelope chuẩn, phân trang cursor và event envelope của nền tảng POS Platform. | — |
| **Kiến trúc** | ⚠️ | Yêu cầu chủ ý hoãn lại việc chọn cơ chế vận chuyển hình chiếu, model registry và lựa chọn bằng chứng nguồn cho Architecture/Contracts. | Giải quyết dứt điểm các điểm này trước khi phê duyệt Architecture; không triển khai dựa trên các mặc định mơ hồ. |
| **Kiểm thử** | ⚠️ | Tiêu chí nghiệm thu đã được nêu tổng quan, nhưng các fixture có thể thực thi cho đánh giá ML, xóa dữ liệu riêng tư, tái dựng hình chiếu và quy kết sự kiện chưa được cụ thể hóa. | Bổ sung các fixture unit / contract / integration / replay / load / security / model evaluation vào điều kiện cổng triển khai. |

## Kết luận (Verdict)

Tài liệu yêu cầu **có thể được duyệt với các cảnh báo**. Không có điểm nghẽn về phạm vi hay mức độ ưu tiên nghiệp vụ. Các cảnh báo bắt buộc phải được xử lý trong Architecture/Contracts, đặc biệt là cơ chế quy kết thêm dòng của Order, hợp đồng ngữ cảnh đặc trưng Loyalty, độ tươi/tái dựng hình chiếu, schema biểu diễn luật và quản trị mô hình ML.
