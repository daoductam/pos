# Báo cáo Đánh giá Spec Sentinel: Payment Service (Hợp đồng Dữ liệu & API)

**Tài liệu đặc tả:** `docs/designs/payment/2026-10-12-contracts.md`  
**Ngày:** 2026-10-12  
**Trạng thái:** ⚠️ Có cảnh báo (Tiến hành cẩn trọng - Proceed with Caution)

## Các Phát Hiện (Findings)

Không còn vấn đề chặn đứng nghiêm trọng (critical blocker) nào trong hợp đồng dữ liệu và API sau khi:

- Endpoint IPN được chuẩn hóa thành hợp đồng query dạng `GET` theo đúng thực tế của VNPAY sandbox.
- Quyết toán muộn (late settlement) được tách biệt hoàn toàn khỏi sự kiện `PaymentPaid` và trạng thái kết thúc của payment.
- Các trường của provider đối với phương thức thanh toán tiền mặt (CASH) được cho phép mang giá trị null đúng theo ngữ nghĩa.
- Ràng buộc lượt thử active trong PostgreSQL không còn sử dụng mệnh đề điều kiện không bất biến `now()`.

### Các Cảnh báo (Warnings)

| # | Hạng mục | Phát hiện | Rủi ro | Khuyến nghị |
|---|---|---|---|---|
| 1 | Hợp đồng Cha | Hợp đồng nền tảng cha vẫn cần cập nhật phương thức/đường dẫn IPN, các trường tiền mặt có thể null, khóa duy nhất có phạm vi theo provider và sự kiện `PaymentReconciliationRequired`. | Các client / AsyncAPI / ràng buộc DB sinh tự động có thể bị sai lệch so với hợp đồng của Payment. | Áp dụng đầy đủ 10 sửa đổi bổ sung trong Mục 8 của tài liệu Hợp đồng trước khi triển khai và sinh lại dữ liệu mẫu kiểm thử hợp đồng. |
| 2 | Tích hợp Provider | Thuật toán tính checksum VNPAY, mã hóa query chuẩn hóa, ánh xạ mã RspCode và hành vi thử lại phụ thuộc vào cấu hình sandbox của merchant. | Chữ ký hợp lệ có thể bị từ chối sai hoặc callback gửi lại không đúng cách. | Ghi lại các dữ liệu mẫu sandbox đã làm sạch (fixtures) cho cấu hình SHA256/HMACSHA512 và kiểm thử ánh xạ các mã `00/02/01/04/97/99`. |
| 3 | Quyền riêng tư / Logging | IPN sử dụng query parameters qua phương thức GET; log truy cập tại API Gateway có nguy cơ ghi toàn bộ query `vnp_*` và chuỗi checksum. | Payload và chữ ký của provider có thể bị rò rỉ trong log hệ thống. | Che giấu (redact) các giá trị query, `vnp_SecureHash`, `vnp_OrderInfo` và các trường callback thô tại tầng API Gateway cũng như trong log ứng dụng. |
| 4 | Vận hành Đối soát | Hợp đồng đã có cơ chế lease/retry/trạng thái nhưng cam kết SLA vận hành, đầu mối xử lý và cấu hình thời gian lưu giữ cụ thể chưa được hiện thực hóa thành cấu hình triển khai. | Giao dịch bị treo có thể không có người phụ trách xử lý hoặc bị lưu trữ quá thời gian quy định. | Bổ sung các key cấu hình, cảnh báo alarm, quyền sở hữu runbook và các hành động audit của nhân viên vận hành vào kế hoạch triển khai. |
| 5 | Báo cáo Thống kê | Hợp đồng đã chốt múi giờ chi nhánh / ngày nghiệp vụ nhưng chính sách tổng hợp báo cáo chéo nhiều chi nhánh cần được thể hiện rõ trong schema consumer của Reporting. | Báo cáo gom nhiều chi nhánh có thể bị gộp sai ngày kế toán. | Đưa chính sách `businessDate`/múi giờ vào bảng chiếu của Reporting Service và hiển thị rõ ràng metadata về độ tươi mới / tổng hợp số liệu. |
| 6 | Năng lực Provider ở P1 | Hợp đồng domain/endpoint cho hoàn tiền đã có, nhưng năng lực hoàn tiền của VNPAY, lý do vận hành và ngữ nghĩa timeout của provider cần có fixture/adapter contract. | Thao tác hoàn tiền có thể bị hiểu nhầm là thành công đồng bộ trong khi provider chỉ mới trả về trạng thái chấp nhận/đang chờ xử lý. | Giữ trạng thái hoàn tiền độc lập; chỉ hoàn thiện P1 sau khi đã có dữ liệu mẫu tra cứu/hoàn tiền và ma trận năng lực nhà cung cấp đầy đủ. |

## Phân Tích Tác Động Chéo (Cross-Impact Analysis)

| Chiều phân tích | Phát hiện | Mức độ rủi ro | Khuyến nghị |
|---|---|---|---|
| **Logic Dùng chung** | Các hợp đồng cha OpenAPI/AsyncAPI và các client sinh tự động tiêu thụ các trường của Payment; hiện chưa có code gọi thực tế nào trong codebase. | 🟡 Trung bình | Chỉ sinh lại client và hợp đồng sau khi đã hoàn tất các sửa đổi bổ sung cho hệ thống cha và duyệt lại schema. |
| **Dữ liệu / Trạng thái** | Các bảng của Payment lưu vết attempt bất biến, callback, đối soát và bằng chứng quyết toán muộn với các bước chuyển trạng thái chỉ nối thêm. | 🟢 Thấp | Bảo toàn các bất biến giao dịch, ràng buộc duy nhất và cơ chế lease trong các file migration và bài kiểm thử tích hợp. |
| **Hợp đồng API** | Các endpoint khởi tạo nội bộ, đọc/danh sách, GET IPN, đối soát và hoàn tiền P1 đều rất tường minh và có phiên bản rõ ràng. | 🟡 Trung bình | Bổ sung các ví dụ mẫu trong OpenAPI cho các phản hồi trùng lặp / đến muộn / provider không khả dụng và dữ liệu mẫu của VNPAY adapter. |
| **Tính Toàn vẹn Luồng** | Hợp đồng cho tiền mặt, VNPAY IPN, đối soát và thanh toán muộn hoàn toàn khớp với quyền sở hữu Saga của Order Service. | 🟢 Thấp | Các bài kiểm thử consumer bắt buộc phải khẳng định trạng thái `SETTLED_LATE` không thể kích hoạt `PaymentPaid` hay `OrderPaid`. |
| **Yêu cầu Xuyên suốt** | Phạm vi tenant/chi nhánh, xác thực Cognito/workload, phân trang con trỏ, thời gian lưu giữ và che giấu dữ liệu đã được đặc tả chi tiết. | 🟡 Trung bình | Xác minh việc che giấu query tại gateway và kiểm tra phạm vi repository trong các bài kiểm thử an toàn bảo mật. |

## Tuân Thủ Hướng Dẫn (Instruction Compliance)

| Chiều kiểm tra | Kết luận | Sai lệch (nếu có) | Khuyến nghị |
|---|---|---|---|
| **Quy ước** | ✅ | Đường dẫn REST có phiên bản, envelope chung, phân trang con trỏ và envelope sự kiện hoàn toàn khớp với quy ước nền tảng cha. | — |
| **Kiến trúc** | ✅ | Payment tự sở hữu cơ sở dữ liệu và trạng thái của riêng mình; không truy cập chéo DB; các bộ điều hợp cô lập hoàn toàn VNPAY. | — |
| **Kiểm thử** | ✅ | Ma trận kiểm thử hợp đồng bao quát tiền mặt, tương tranh, checksum, IPN trùng lặp / sai thứ tự / đến muộn, đối soát và bảo mật phạm vi. | Bổ sung các fixture có thể thực thi được khi bước vào giai đoạn triển khai code. |

## Quyết Định (Decision)

Tài liệu Hợp đồng **có thể được phê duyệt cùng với các cảnh báo nêu trên**. Tuyệt đối không chuyển sang giai đoạn viết mã triển khai cho tới khi các sửa đổi bổ sung cho hợp đồng cha, dữ liệu mẫu VNPAY sandbox và việc che giấu thông tin tại API Gateway được hoàn tất đầy đủ.
