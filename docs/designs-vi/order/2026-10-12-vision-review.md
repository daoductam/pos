Started at: 2026/10/12 16:16:00 +07
Finished at: 2026/10/12 16:16:00 +07
Total time: 0 minutes
---

# Báo cáo Đánh giá Spec Sentinel: Order Service (Dịch vụ Đơn hàng)

**Tài liệu đặc tả:** [2026-10-12-vision.md](./2026-10-12-vision.md)

**Ngày:** 2026-10-12

**Trạng thái:** ⚠️ Có cảnh báo (Tiến hành cẩn trọng - Proceed with Caution)

## Các Phát Hiện (Findings)

### Các Cảnh báo (Warnings)

| # | Hạng mục | Phát hiện | Rủi ro | Khuyến nghị |
|---|---|---|---|---|
| 1 | Ranh giới Saga | Tài liệu Tầm nhìn chỉ định Step Functions là bộ điều phối và Order là chủ sở hữu trạng thái, nhưng chưa định nghĩa chính xác hợp đồng lệnh/sự kiện cho `Inventory.commit`, sự kiện thanh toán thành công sau khi giữ chỗ, hoặc cơ chế thử lại hoàn tất đơn (finalize retry). | Một callback đến muộn hoặc bị trùng lặp có thể kích hoạt hoàn tất đơn hai lần hoặc khiến payment, tồn kho và Order rơi vào các trạng thái kết thúc xung đột nhau. | Tài liệu Yêu cầu (Requirements) bắt buộc phải định nghĩa máy trạng thái Saga, phạm vi idempotency, thứ tự bù trừ và các trạng thái đối soát kết thúc cho từng kết quả xử lý. |
| 2 | Ngoại lệ thanh toán | Giao dịch VNPAY thành công muộn sau khi việc giữ chỗ nguyên vật liệu đã bị giải phóng được chuyển hướng đến `late_payment_reconciliation`, nhưng quyền sở hữu và các hành động được phép của nhân viên vận hành chưa được đặc tả. | Khách hàng có thể đã bị trừ tiền trong khi đơn hàng vẫn ở trạng thái thất bại; một lượt thử lại tự động không an toàn có thể trừ tiền hai lần hoặc bán vượt quá tồn kho. | Xác định Payment là bên sở hữu việc quyết toán/đối soát với nhà cung cấp, Order là bên sở hữu trạng thái đơn hàng, và quy định rõ nguyên tắc không tự động hoàn tất / không trừ tiền hai lần kèm theo kênh phát cảnh báo và audit. |
| 3 | Tiền tệ và các khoản tổng | Tầm nhìn nêu rằng Order chụp ảnh giá và loại trừ trách nhiệm tính VAT/chiết khấu trừ khi có hợp đồng riêng. Tài liệu chưa nêu rõ cách tính tạm tính (subtotal), VAT, chiết khấu, làm tròn và tổng tiền thanh toán cuối cùng trong P0. | Payment và Order có thể sử dụng các số tiền khác nhau, gây lệch lạc tài chính và phát sinh các lệnh giữ chỗ/thanh toán không hợp lệ. | Tài liệu Yêu cầu bắt buộc phải chỉ định một công thức tính tổng chuẩn mực duy nhất trong Order và định nghĩa tiền số nguyên, dữ liệu đầu vào thuế/làm tròn, hành vi khi vắng mặt chiết khấu/hành vi P1 và các khoản tổng checkout bất biến. |
| 4 | Làm mới snapshot | Quá trình thanh toán bị chặn khi thực đơn Catalog thay đổi, nhưng lệnh từ client để làm mới hoặc áp dụng lại các lựa chọn thực đơn hiện tại từ Catalog chưa được định nghĩa. | Thu ngân có thể bị kẹt với các snapshot dòng món cũ hoặc vô tình ghi đè lên modifier/số lượng đã bị thay đổi. | Định nghĩa phản hồi có định kiểu rõ ràng `catalog_changed`, lệnh làm mới/tính lại giá tường minh và yêu cầu phiên bản trước khi thực hiện lượt thử checkout mới. |
| 5 | Hợp đồng Audit | Yêu cầu audit thao tác hủy món là bắt buộc, nhưng thời gian lưu trữ, cấu trúc sự kiện, các trường trước/sau và hành vi khi xảy ra sự cố chưa được định nghĩa. | Các hành động hủy món nhạy cảm có thể không thể kiểm chứng được hoặc có thể chặn các biến đổi nghiệp vụ do consumer Audit không khả dụng. | Order phải phát ra một sự kiện outbox audit nguyên tử cùng giao dịch mang thông tin tác nhân, vai trò, chi nhánh, lý do, snapshot dòng món và correlation; Audit Service tiêu thụ bất đồng bộ với cơ chế retry/DLQ. |
| 6 | Phạm vi chéo tenant/chi nhánh | Tầm nhìn nêu rõ việc cô lập tenant/chi nhánh nhưng chưa làm rõ việc lựa chọn của Owner/Manager, kiểm tra chi nhánh đối với order ID và việc cùng sửa đơn nháp trong cùng chi nhánh tại từng đường dẫn repository. | Lỗ hổng IDOR hoặc biến đổi đơn nháp chéo chi nhánh có thể làm lộ đơn hàng hoặc làm thay đổi trạng thái checkout trái phép. | Tài liệu Yêu cầu bắt buộc phải yêu cầu quyết định phân quyền từ Identity kết hợp các mệnh đề điều kiện phòng thủ chiều sâu `tenant_id`/`branch_id` cho mọi thao tác đọc, ghi, xử lý hàng loạt và các lệnh gọi Saga nội bộ. |
| 7 | Độ rộng phạm vi P1 | Sơ đồ bàn, giữ đơn, tách/gộp hóa đơn và KDS WebSocket đều là các tính năng lớn, trong khi Tầm nhìn chỉ mới gọi tên chúng thuộc P1. | P1 có thể bị rò rỉ vào mô hình dữ liệu P0 hoặc làm chậm tiến độ bàn giao tính năng checkout. | Giữ P1 phía sau các feature flag và chỉ định nghĩa các ranh giới tương thích ở thời điểm hiện tại; hoãn chi tiết về trạng thái bàn có khách, các bất biến tách/gộp đơn và phát lại WebSocket sang giai đoạn hợp đồng sau. |
| 8 | Liên kết Khách hàng / Loyalty | Nền tảng cha bao gồm các định danh khách hàng/loyalty tùy chọn, nhưng Tầm nhìn Order chưa định nghĩa việc tra cứu khách hàng là một phần của Order P0 hay là một luồng riêng của Loyalty. | Các consumer API có thể bổ sung trường số điện thoại/PII vào Order một cách không đúng đắn hoặc làm trùng lặp quyền sở hữu dữ liệu khách hàng. | Giữ `customerId` dưới dạng một tham chiếu mờ (opaque reference) tùy chọn; Loyalty Service sở hữu việc tra cứu/thông tin cá nhân và Order chỉ chụp lại tham chiếu cần thiết cho sự kiện `OrderPaid`. |

## Phân Tích Tác Động Chéo (Cross-Impact Analysis)

| Chiều phân tích | Phát hiện | Mức độ rủi ro | Khuyến nghị |
|---|---|---|---|
| **Logic Dùng chung** | Chưa có mã nguồn triển khai Order nào; các phụ thuộc trực tiếp là ngữ cảnh Identity, Catalog resolver, API giữ chỗ Inventory, API/IPN Payment và tiến trình Step Functions. | 🟡 Trung bình | Định nghĩa từng ranh giới dưới dạng một hợp đồng có phiên bản trước khi triển khai; không tạo repository domain dùng chung. |
| **Dữ liệu / Trạng thái** | Cơ sở dữ liệu Order DB mới sở hữu đơn hàng, dòng món, snapshot và các lượt checkout attempt; Saga tham chiếu đến các ID giữ chỗ/thanh toán bên ngoài. | 🔴 Cao | Sử dụng các chuyển đổi trạng thái cục bộ, snapshot bất biến, idempotency key duy nhất và các trường đối soát tường minh. |
| **Hợp đồng API** | Các endpoint `/api/v1/orders`, hủy dòng món và checkout của hệ thống cha là đường cơ sở; việc phân giải biến thể Catalog và các API nội bộ Inventory/Payment cần được chuyên biệt hóa. | 🟡 Trung bình | Cập nhật các lược đồ Order cha cho snapshot biến thể cụ thể, phiên bản catalog và phản hồi lỗi checkout / bù trừ có định kiểu rõ ràng. |
| **Tính Toàn vẹn Luồng** | Luồng Đơn nháp → Catalog resolve → Giữ chỗ tồn kho → Thanh toán → Hoàn tất / Bù trừ đã được bao quát về mặt khái niệm; trường hợp IPN đến muộn và xung đột tranh chấp commit/release cần có bảng trạng thái chính xác. | 🔴 Cao | Bổ sung bảng chuyển đổi trạng thái và các bài kiểm thử nghiệm thu luồng sự cố trong tài liệu Yêu cầu. |
| **Yêu cầu Xuyên suốt** | Idempotency, audit, phạm vi tenant, lan truyền trace, timeout và DLQ là bắt buộc trên cả API công khai và các callback của Saga. | 🔴 Cao | Bắt buộc phải có metadata correlation/saga/tenant/branch và bổ sung các bài kiểm thử bảo mật / kiểm thử phủ định. |

## Tuân Thủ Hướng Dẫn (Instruction Compliance)

| Chiều kiểm tra | Kết luận | Sai lệch (nếu có) | Khuyến nghị |
|---|---|---|---|
| **Quy ước** | ✅ | Sử dụng đúng thư mục hiện có `docs/designs/order/`, các liên kết tới hệ thống cha và ranh giới P0/P1/P2. | — |
| **Kiến trúc** | ✅ | Phù hợp với các quyết định của hệ thống cha: database riêng cho từng service, REST/OpenAPI, EventBridge/SQS, transactional outbox và AWS Step Functions. | Giữ quyền sở hữu dữ liệu của từng service tường minh trong tài liệu Yêu cầu. |
| **Kiểm thử** | ⚠️ | Tầm nhìn đã liệt kê unit test, integration test, contract test, E2E test, load test và failure test nhưng chưa ánh xạ chúng vào các bước chuyển đổi Saga cụ thể. | Chuyển đổi mọi cảnh báo / đường dẫn sự cố thành một bài kiểm thử nghiệm thu có tên rõ ràng trước khi bước vào giai đoạn Kiến trúc. |

## Quyết Định Đánh Giá (Review Decision)

- Không tìm thấy vấn đề chặn đứng (blocker).
- Tài liệu Tầm nhìn có thể chuyển sang giai đoạn Yêu cầu Kỹ thuật (Requirements) sau khi người dùng xác nhận các cảnh báo này.
- Tài liệu Yêu cầu bắt buộc phải giải quyết tường minh các cảnh báo từ 1 đến 6; các cảnh báo 7 và 8 có thể giữ nguyên dưới dạng các quyết định phân định ranh giới P1/P0 được kiểm soát.

## Phê Duyệt

- **Trạng thái:** Đang chờ người dùng xác nhận các cảnh báo
- **Giai đoạn tiếp theo sau khi xác nhận:** Khám phá Yêu cầu Kỹ thuật (Requirements Discovery)
