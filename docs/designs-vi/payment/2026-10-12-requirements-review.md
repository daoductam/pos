# Báo cáo Đánh giá Spec Sentinel: Payment Service (Yêu cầu Kỹ thuật)

**Tài liệu đặc tả:** `docs/designs/payment/2026-10-12-requirements.md`  
**Ngày:** 2026-10-12  
**Trạng thái:** ⚠️ Có cảnh báo (Tiến hành cẩn trọng - Proceed with Caution)

## Các Phát Hiện (Findings)

Không còn vấn đề chặn đứng nghiêm trọng (critical blocker) nào sau khi đã tách quyết toán muộn (late settlement) ra khỏi trạng thái vòng đời, xác định ngữ cảnh service đáng tin cậy (trusted service context) và ghi rõ các sửa đổi bổ sung cho hợp đồng cha cần thực hiện trước khi triển khai mã nguồn.

### Các Cảnh báo (Warnings)

| # | Hạng mục | Phát hiện | Rủi ro | Khuyến nghị |
|---|---|---|---|---|
| 1 | Tích hợp bên ngoài | Thuật toán tính checksum, thứ tự chuẩn hóa trường dữ liệu (canonical field ordering) và mã phản hồi xác nhận cho provider phụ thuộc vào hợp đồng VNPAY sandbox thực tế. | Bộ điều hợp (adapter) có thể xác thực sai hoặc khiến VNPAY gửi lại webhook thử lại (retry) không đúng cách. | Chốt bằng các dữ liệu mẫu kiểm thử hợp đồng / sandbox đã được làm sạch thông tin (sanitized); giữ toàn bộ logic đặc thù của provider trong adapter, không đưa các trường đặc thù của VNPAY vào domain core. |
| 2 | Vận hành đối soát | Đã có ngưỡng thử lại và hàng đợi xử lý thủ công nhưng chưa chốt thời gian lưu giữ dữ liệu (retention), cam kết SLA của vận hành, quyền can thiệp thủ công và bằng chứng bắt buộc. | Giao dịch bị treo có thể tồn tại quá lâu hoặc bị nhân viên vận hành xử lý thủ công không nhất quán. | Tài liệu Kiến trúc / Hợp đồng bổ sung vòng đời của bản ghi đối soát (reconciliation item), thời gian hết hạn khóa lease, hành động audit của nhân viên vận hành, ngưỡng cảnh báo và thời gian lưu trữ. |
| 3 | Ngữ nghĩa báo cáo | Lịch sử / báo cáo chưa chốt múi giờ nghiệp vụ, ngày đối soát kế toán, quy tắc VAT/làm tròn và thời gian lưu giữ. | Việc tổng hợp số liệu theo ngày hoặc so sánh với báo cáo của provider có thể bị lệch số liệu. | Sử dụng múi giờ chi nhánh / ngày nghiệp vụ và số nguyên đơn vị nhỏ nhất (minor units); chốt quy tắc VAT/làm tròn/retention tại giai đoạn Hợp đồng trước khi xây dựng mô hình đọc (read model). |
| 4 | Hợp đồng API | Thao tác chạy đối soát là bất đồng bộ nhưng bảng mã lỗi có `409 reconciliation_required`; chưa phân biệt rõ giữa việc chấp nhận lệnh chạy với kết quả của từng item cụ thể. | Máy POS hoặc nhân viên vận hành có thể hiểu nhầm mã `409` là thanh toán bị thất bại. | Trả về mã HTTP `202` cho lệnh chạy đã được chấp nhận; chỉ dành mã `409` cho các yêu cầu xung đột / trùng lặp; trạng thái của từng item cụ thể được đọc qua tài nguyên đối soát. |
| 5 | Ranh giới P1 | Tính năng hoàn tiền (refund) và thanh toán kết hợp (split tender) đã có các bất biến nhưng chưa có ma trận năng lực nhà cung cấp và payload endpoint hoàn chỉnh. | Có thể thiết kế P1 làm thay đổi sổ cái thanh toán P0 hoặc không tương thích với luồng hoàn tiền của VNPAY. | Giữ các bản ghi hoàn tiền / phân bổ thanh toán là các thực thể riêng biệt; giai đoạn Hợp đồng bắt buộc phải định nghĩa mức độ hỗ trợ của provider, quy tắc hoàn một phần/toàn bộ và quy trình đối soát hoàn tiền trước khi triển khai P1. |

## Phân Tích Tác Động Chéo (Cross-Impact Analysis)

| Chiều phân tích | Phát hiện | Mức độ rủi ro | Khuyến nghị |
|---|---|---|---|
| **Logic Dùng chung** | Chưa có mã nguồn triển khai; không tìm thấy code service nào ngoài các tài liệu thiết kế. Tác động chéo trực tiếp nằm ở Order/Saga, Inventory và các hợp đồng API/sự kiện cha. | 🟡 Trung bình | Hiện thực hóa các hợp đồng có phiên bản và các bài kiểm thử phía consumer trước khi viết code; không tạo repository domain dùng chung giữa các service. |
| **Dữ liệu / Trạng thái** | Các bản ghi attempt, callback, đối soát và outbox do Payment tự sở hữu; thanh toán muộn đã được tách thành bản ghi riêng biệt với attempt đã kết thúc. | 🟡 Trung bình | Duy trì ranh giới giao dịch, các ràng buộc duy nhất và lịch sử chuyển trạng thái chỉ nối thêm (append-only); hiện thực hóa lược đồ trong Kiến trúc. |
| **Hợp đồng API** | Các lệnh khởi tạo nội bộ, đọc payment, VNPAY IPN và chạy đối soát; các trường sự kiện / cơ sở dữ liệu của hệ thống cha chưa biểu diễn đầy đủ cả tiền mặt và VNPAY chung. | 🟡 Trung bình | Sửa đổi hợp đồng OpenAPI/AsyncAPI/DB của hệ thống cha trước khi triển khai: các trường provider có thể null, các trường tiền mặt và khóa duy nhất có phạm vi theo provider. |
| **Tính Toàn vẹn Luồng** | Luồng tiền mặt thành công, VNPAY pending/IPN, callback trùng lặp / sai thứ tự, thanh toán muộn và ranh giới đối soát đã hoàn toàn nhất quán. | 🟢 Thấp | Duy trì bất biến thanh toán muộn: Chỉ phát sự kiện `PaymentReconciliationRequired.v1`, tuyệt đối không phát `PaymentPaid` hay `OrderPaid` sau khi attempt đã kết thúc. |
| **Yêu cầu Xuyên suốt** | Đã bao quát cô lập tenant/chi nhánh, vai trò Cognito, kiểm tra checksum, quản lý secret qua Secrets Manager, outbox và audit; ngữ cảnh bên gọi nội bộ cần xác thực service đáng tin cậy. | 🟡 Trung bình | Áp dụng mệnh đề điều kiện tenant phòng thủ chiều sâu tại tầng service/repository; không tin tưởng ID thô hoặc body từ bên gọi; ghi log cảnh báo an ninh khi có sai lệch. |

## Tuân Thủ Hướng Dẫn (Instruction Compliance)

| Chiều kiểm tra | Kết luận | Sai lệch (nếu có) | Khuyến nghị |
|---|---|---|---|
| **Quy ước** | ✅ | Tài liệu nằm đúng trong thư mục `docs/designs/payment/`, sử dụng API/sự kiện có phiên bản và quy ước đặt tên nhất quán với tài liệu cha. | — |
| **Kiến trúc** | ⚠️ | Quyền sở hữu dữ liệu giữa Payment và Order đã được phân định; ngữ cảnh service đáng tin cậy và các sửa đổi bổ sung cho hệ thống cha vẫn cần được hiện thực hóa. | Đưa ngữ cảnh lệnh, bản ghi thanh toán muộn và hợp đồng chéo service vào tài liệu Kiến trúc và Hợp đồng. |
| **Kiểm thử** | ✅ | Đã bao phủ các bài kiểm thử nghiệm thu và tiêm lỗi cho tiền mặt, IPN, tương tranh đồng thời, đối soát, phạm vi tenant và outbox. | Bổ sung bài kiểm thử riêng biệt cho bản ghi thanh toán muộn đảm bảo không phát sinh `PaymentPaid` sau khi chính sách được chốt. |

## Quyết Định (Decision)

Tài liệu Yêu cầu Kỹ thuật **có thể chuyển sang giai đoạn Thiết kế Kiến trúc (Architecture) cùng với các cảnh báo nêu trên**. Trước khi triển khai mã nguồn, bắt buộc phải hiện thực hóa các sửa đổi bổ sung cho hợp đồng cha và chốt chính sách vận hành đối soát / báo cáo trong Kiến trúc và Hợp đồng.
