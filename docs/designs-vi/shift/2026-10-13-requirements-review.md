# Báo Cáo Đánh Giá Yêu Cầu (Spec Sentinel Report) - Shift Service

**Tài liệu yêu cầu:** [`docs/designs-vi/shift/2026-10-13-requirements.md`](file:///d:/vibe-code/pos/docs/designs-vi/shift/2026-10-13-requirements.md)  
**Ngày:** 13-10-2026  
**Trạng thái:** ⚠️ Cảnh báo (Tiến hành với sự thận trọng)

## Ngữ cảnh đánh giá

- Tái sử dụng Shift Vision đã được phê duyệt và bản đánh giá Vision tương ứng.
- Áp dụng các bộ quy tắc kiểm tra `spec-reviewer`, `api-design` và `database-design`.
- Đối chiếu chéo với các hợp đồng của POS Platform, Order, Payment và Identity.
- Hiện chưa có mã nguồn triển khai Shift, schema được sinh tự động hay migration có thể thực thi.

## Các phát hiện

## Cảnh báo (Warnings)

| # | Phân loại | Phát hiện | Rủi ro | Khuyến nghị |
|---|---|---|---|---|
| 1 | Hợp đồng liên service | Yêu cầu nghiệp vụ đòi hỏi `shiftId` và `admittedAt` đáng tin cậy trên các sự kiện tiền mặt/hoàn tiền của Payment, nhưng hợp đồng sự kiện Payment/parent hiện tại chưa công bố các trường này một cách chuẩn tắc. | Shift có thể tính toán tiền mặt kỳ vọng dựa trên dữ liệu quy kết không đầy đủ hoặc suy đoán. | Bổ sung các schema khởi tạo/hoàn tiền Payment và sự kiện trong Contracts; thêm fixture cho producer/consumer và từ chối các sự kiện thiếu phạm vi shift/chi nhánh đáng tin cậy. |
| 2 | Đồng thời (Concurrency) | Ngữ nghĩa đóng rào chắn (close fence)/tiếp nhận (admission) được mô tả theo hành vi, trong khi cơ chế khóa/token liên service chính xác được hoãn lại cho Architecture. | Việc đóng ca và thanh toán tiền mặt có thể xảy ra race condition khác nhau giữa các cách triển khai. | Chọn một giao thức admission duy nhất, định nghĩa thứ tự fence/version và kiểm thử cả hai kết quả: admission-trước-fence được hoàn tất; admission-sau-fence bị từ chối. |
| 3 | Độ tươi của dữ liệu đọc (Read freshness) | `cash-summary` được gọi là có thẩm quyền (authoritative) nhưng các sự kiện lại đến thông qua cơ chế gửi tin ít nhất một lần (at-least-once messaging) và có thể bị trễ. | Một bản kiểm kê có thể được phê duyệt dựa trên bản tóm tắt cũ mà không có độ tươi/watermark rõ ràng. | Trả về watermark nguồn, fence, `calculatedAt` và độ tươi/trạng thái; chặn phê duyệt khi các sự kiện bắt buộc chưa được giải quyết. |
| 4 | Đồng bộ schema với parent | Schema/sự kiện `shifts` của parent hiện chỉ bao quát mở/đóng ca, chưa bao gồm các lần kiểm kê, chênh lệch, xem xét/kiểm kê lại, hoàn trả hủy đơn hoặc các ca đối soát. | DTO/bản tóm tắt DB do parent sinh ra có thể lệch khỏi hợp đồng nguồn của Shift. | Cập nhật hợp đồng parent sau khi Architecture/Contracts được duyệt; giữ Contracts của Shift làm nguồn chân lý chi tiết. |
| 5 | Chính sách vận hành | Mục tiêu lưu trữ/quy mô là các giả định kế thừa và quy trình khôi phục ca đang hoạt động chưa phải là worker/runbook có thể thực thi. | Các ca mở/chờ duyệt quá cũ có thể chặn một chi nhánh hoặc khiến việc dọn dẹp lịch sử không an toàn. | Định nghĩa chỉ số độ tuổi, lệnh khôi phục của Quản lý/Chủ chuỗi, cảnh báo và chính sách lưu trữ/di chuyển dữ liệu 7 năm trong Architecture/Contracts. |

## Phân tích tác động chéo (Cross-Impact Analysis)

| Chiều phân tích | Phát hiện | Mức độ rủi ro | Khuyến nghị |
|---|---|---|---|
| **Logic dùng chung** | Các route Shift của parent được POS/Order/Payment tiêu thụ; hiện chưa có call site triển khai nào tồn tại. | 🟡 Trung bình | Giữ nguyên định danh route và sinh client từ hợp đồng Shift chuyên biệt. |
| **Dữ liệu / Trạng thái** | Vòng đời ca làm việc, các lần kiểm đếm, sự kiện tiền mặt và ca đối soát đòi hỏi tính nhất quán cục bộ mạnh và lịch sử bất biến. | 🔴 Cao | Dùng ràng buộc vị từ tenant/chi nhánh, ràng buộc partial active duy nhất, khử trùng lặp nguồn, khóa hàng/phiên bản và bản ghi điều chỉnh chỉ-ghi-nối (append-only). |
| **Hợp đồng API** | Các route công khai mở/ca hiện tại/đóng ca đã có; các route P1 kiểm đếm/phê duyệt và route nội bộ tiếp nhận/sự kiện tiền mặt là mới. | 🟡 Trung bình | Định nghĩa các ví dụ OpenAPI/mã lỗi và schema tích hợp Payment/Order trước khi triển khai. |
| **Tính toàn vẹn luồng** | Mở → tiếp nhận đơn/thanh toán → đóng/kiểm đếm → xem xét/kiểm đếm lại → đã đóng được bao quát; dữ liệu trễ và độ trễ broker cần hành vi độ tươi rõ ràng. | 🟡 Trung bình | Thêm sơ đồ tuần tự và fixture nghiệm thu cho tranh chấp rào chắn (fence races), sự kiện đến trễ, sự kiện trùng lặp và hủy ca bị bỏ quên. |
| **Khía cạnh xuyên suốt** | Xác thực Cognito/workload, tiền tệ số nguyên, múi giờ UTC/kinh doanh, audit, outbox và DLQ tuân theo quy ước của parent. | 🟢 Thấp | Đưa các quy ước vào hợp đồng API/dữ liệu/sự kiện của Shift và runbook vận hành. |

## Tuân thủ chỉ dẫn (Instruction Compliance)

| Tiêu chí | Kết luận | Sai lệch (nếu có) | Khuyến nghị |
|---|---|---|---|
| **Quy ước** | ✅ | Tuân thủ REST/OpenAPI, UUID v7, envelope chuẩn, tính lũy nghiệm (idempotency) và cách đặt tên route của parent. | — |
| **Kiến trúc** | ⚠️ | Cơ chế rào chắn đóng ca (close-fence) liên service được chủ ý hoãn lại cho Architecture. | Giải quyết giao thức trước khi phê duyệt Contracts; không triển khai kiểm tra thời gian theo kiểu nỗ lực tối đa (best-effort). |
| **Kiểm thử** | ⚠️ | Tiêu chí nghiệm thu đã có, nhưng các fixture có thể thực thi cho phép toán tiền tệ, thứ tự tranh chấp và dữ liệu trễ chưa được cụ thể hóa. | Bổ sung các fixture unit, integration, contract, concurrency và replay vào điều kiện cổng triển khai. |

## Kết luận (Verdict)

Tài liệu yêu cầu **có thể được duyệt với các cảnh báo**. Không còn điểm nghẽn (blocker) về phạm vi, quyền sở hữu, vòng đời trạng thái hay công thức tiền tệ. Các cảnh báo phải được xử lý tại Architecture/Contracts trước khi viết mã, đặc biệt là việc truyền `shiftId` từ Payment và giao thức tiếp nhận rào chắn đóng ca (close-fence admission protocol).
