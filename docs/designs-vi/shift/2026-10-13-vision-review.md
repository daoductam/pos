# Báo Cáo Đánh Giá Tầm Nhìn Nghiệp Vụ (Vision Sentinel Report)

**Tài liệu đánh giá:** [`docs/designs/shift/2026-10-13-vision.md`](./2026-10-13-vision.md)  
**Ngày:** 2026-10-13  
**Trạng thái:** ⚠️ Có cảnh báo — Tiến hành cẩn trọng (Warnings - Proceed with Caution)

---

## 1. Ngữ Cảnh Đánh Giá

- Tái sử dụng ngữ cảnh thiết kế đã được phê duyệt từ nền tảng POS Platform, Identity Service, Order Service, Payment Service và Inventory Service.
- Áp dụng cổng đánh giá chuyên sâu `spec-reviewer` đối với tài liệu Tầm nhìn (Vision) của Shift Service.
- Repository hiện chứa các tài liệu thiết kế nhưng chưa có mã nguồn ứng dụng hay script migration thực thi được của Shift Service.
- Tài liệu hệ thống cha đã dự trù service/database độc lập cho Shift và các route cơ bản (mở ca, ca hiện tại, đóng ca); tài liệu Vision này bổ sung thêm ranh giới kiểm đếm tiền mặt và quy trình duyệt chênh lệch ở P1.

---

## 2. Các Phát Hiện & Cảnh Báo (Warnings)

| # | Phân loại | Nội dung phát hiện | Rủi ro | Khuyến nghị giải quyết |
|---|---|---|---|---|
| 1 | Hợp đồng liên Service (Cross-Service Contract) | Payment Service và các sự kiện của Payment hiện chưa mang trường chuẩn hóa `shiftId`, trong khi Shift Service tính toán tiền mặt kỳ vọng dựa trên các giao dịch thu/hoàn tiền mặt gắn với ca. | Shift Service không thể quy trách nhiệm chính xác các giao dịch tiền mặt vào đúng két tiền của ca, hoặc phải tự suy diễn không an toàn từ dữ liệu đơn hàng. | Trong tài liệu Requirements/Contracts, bắt buộc phải biến `shiftId` thành trường bắt buộc đối với các bằng chứng thanh toán/hoàn tiền mặt tại chi nhánh, đồng thời xác thực quyền sở hữu tenant/chi nhánh/ca tại biên giới Payment/Saga. |
| 2 | Mô hình dữ liệu hệ thống cha | Schema `shifts` trong tài liệu cha hiện mới chỉ mô hình hóa số tiền đầu ca và cuối ca; chưa có bản chụp kiểm đếm (count snapshot), các trường tiền kỳ vọng/chênh lệch, người duyệt/ghi chú và phiên bản cấu hình ngưỡng. | Schema sinh tự động của hệ thống cha và cơ sở dữ liệu thực tế của Shift Service có thể mâu thuẫn về nguồn chân lý đối soát tiền mặt. | Mở rộng schema thuộc quyền sở hữu của Shift Service trong giai đoạn Contracts; giữ tài liệu cha POS Platform đóng vai trò tóm tắt tham chiếu, không phải nguồn chân lý thứ hai. |
| 3 | Xử lý đồng thời / Rào chắn đóng ca (Closing Fence) | Tài liệu Vision nêu rằng không cho ghi nhận đơn mới sau khi quy trình đóng ca bắt đầu, nhưng chưa đặc tả rào chắn nguyên tử đối với các yêu cầu thanh toán/đơn hàng đang xử lý dở dang (in-flight). | Một giao dịch thanh toán tiền mặt có thể commit sau khi ca đã đóng, hoặc ca đóng lại bỏ sót giao dịch đã được thẩm định, gây ra chênh lệch ảo (false discrepancy). | Tài liệu Requirements bắt buộc phải quy định cơ chế khóa dòng DB / phiên bản rào chắn, xác định rõ kết quả bên thắng/thua khi có tranh chấp, và đảm bảo tính idempotent giữa lệnh đóng ca và các lệnh thanh toán. |
| 4 | Tính nhất quán sau (Eventual Consistency) | Tiền mặt kỳ vọng phụ thuộc vào các bằng chứng từ Payment Service và thời điểm hoàn tiền; các bằng chứng gửi muộn, gửi lặp hoặc đến sai thứ tự đã được nhận diện nhưng chưa có chính sách đối soát cụ thể. | Việc kiểm đếm tiền có thể bị chốt dựa trên một view tổng hợp chưa hoàn chỉnh, đòi hỏi phải can thiệp điều chỉnh thủ công. | Quy định mốc thời gian chốt sổ (closing cutoff), khử trùng lặp sự kiện nguồn, xử lý các sự kiện đến muộn và quy trình mở hồ sơ đối soát trước khi duyệt hợp đồng dữ liệu. |
| 5 | Chính sách phạm vi ca làm việc | Tài liệu Vision liệt kê việc Quản lý can thiệp đóng thay và duyệt P1 nhưng chưa quy định ca chưa phát sinh giao dịch nào có được phép hủy hay không, hoặc cách thức xử lý các ca bị bỏ quên. | Chi nhánh có thể bị tắc nghẽn không thể mở ca mới do một ca bị bỏ quên mà không có luồng vận hành định kiểu để xử lý. | Giải quyết dứt điểm chính sách hủy ca / thu hồi ca bị bỏ rơi trong tài liệu Requirements; tuyệt đối không tự ý mở lại hoặc tự động cưỡng chế đóng ca âm thầm. |

---

## 3. Phân Tích Tác Động Chéo (Cross-Impact Analysis)

| Chiều phân tích | Đánh giá hiện trạng | Mức độ rủi ro | Khuyến nghị |
|---|---|---|---|
| **Logic Dùng Chung (Shared Logic)** | Các route của Shift Service trong tài liệu cha được tham chiếu bởi POS, Order và Payment; hiện chưa có code gọi hàm thực tế. | 🟡 Trung bình | Duy trì định danh các route của tài liệu cha, đồng thời chuyên biệt hóa cấu trúc request/response và ngữ nghĩa trạng thái trong hợp đồng của Shift. |
| **Dữ Liệu & Trạng Thái (Data/State)** | Vòng đời ca làm việc và các bản ghi đối soát tiền mặt thuộc phạm vi chi nhánh/tenant và tương tác với các bằng chứng thanh toán bất biến. | 🔴 Cao | Áp dụng triệt để điều kiện lọc tenant/chi nhánh, ràng buộc duy nhất một ca active, các bản ghi kiểm toán/đối soát chỉ ghi thêm và các trường phiên bản/snapshot rõ ràng. |
| **Hợp Đồng API (API Contracts)** | Các API mở ca, tra cứu ca hiện tại, đóng ca đã có trong tài liệu cha; quy trình đếm tiền/duyệt P1 và việc truyền `shiftId` từ Payment chưa được đặc tả đầy đủ. | 🟡 Trung bình | Bổ sung và cập nhật tài liệu OpenAPI/AsyncAPI/DB của hệ thống cha sau khi tài liệu Requirements của Shift được phê duyệt. |
| **Tính Toàn Vẹn Của Luồng (Flow Integrity)** | Chuỗi luồng: Mở ca → Bán hàng/Thanh toán → Đếm tiền/Đóng ca → Duyệt chênh lệch → Đã đóng hoàn tất là mạch lạc; các tình huống tranh chấp khi đóng ca và sự kiện đến muộn cần các bước chuyển trạng thái chính xác. | 🟡 Trung bình | Bổ sung các kịch bản sơ đồ tuần tự và bài kiểm thử nghiệm thu cho việc đóng ca đồng thời, sự kiện trùng lặp và hoàn tiền muộn. |
| **Các Vấn Đề Xuyên Suốt (Cross-Cutting)** | Xác thực Cognito/Workload Identity, kiểm toán, tính idempotent, múi giờ chi nhánh và tiền tệ số nguyên minor unit đều kế thừa từ quy ước nền tảng. | 🟢 Thấp | Tiếp tục duy trì các quy ước này vào tài liệu Hợp đồng API/Dữ liệu và runbook vận hành của Shift Service. |

---

## 4. Đánh Giá Tuân Thủ Hướng Dẫn (Instruction Compliance)

| Tiêu chuẩn | Kết luận | Đánh giá sai lệch (nếu có) | Khuyến nghị |
|---|---|---|---|
| **Quy ước (Conventions)** | ✅ Đạt | Kiến trúc REST, UUID v7, dấu thời gian UTC, tiền tệ số nguyên minor unit và đặt tên route đều tuân thủ chặt chẽ quy ước. | Duy trì áp dụng. |
| **Kiến trúc (Architecture)** | ✅ Đạt | Tái sử dụng ranh giới service/database độc lập của Shift Service; tuyệt đối không dùng chung database với Order hay Payment. | Duy trì kiểm soát chặt chẽ ranh giới service. |
| **Kiểm thử (Testing)** | ⚠️ Cần chú ý | Tài liệu Vision đã có tiêu chí nghiệm thu và quản trị rủi ro nhưng chưa có fixture kiểm thử thực thi cho các kịch bản tranh chấp hay sự kiện muộn. | Định nghĩa rõ ràng các fixture hợp đồng và xử lý đồng thời trong tài liệu Requirements/Contracts trước khi lập trình. |

---

## 5. Kết Luận & Quyết Định (Verdict)

Tài liệu Tầm nhìn (Vision) **đủ điều kiện chuyển sang giai đoạn Yêu Cầu Kỹ Thuật (Requirements) với các cảnh báo trên**.

Các cảnh báo không làm tắc nghẽn giai đoạn tiếp theo; tài liệu Requirements có nhiệm vụ cụ thể hóa chúng thành các bước chuyển trạng thái chính xác, đặc tả API, quyền sở hữu dữ liệu và các tiêu chí nghiệm thu có thể kiểm chứng được.
