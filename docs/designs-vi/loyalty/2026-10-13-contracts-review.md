# Báo Cáo Đánh Giá Hợp Đồng Kỹ Thuật (Contracts Sentinel Report)

**Tài liệu đánh giá:** [`docs/designs/loyalty/2026-10-13-contracts.md`](./2026-10-13-contracts.md)  
**Ngày:** 2026-10-13  
**Trạng thái:** ⚠️ Có cảnh báo — Tiến hành cẩn trọng (Warnings - Proceed with Caution)

---

## 1. Ngữ Cảnh Đánh Giá

- Tái sử dụng toàn bộ ngữ cảnh đã được phê duyệt từ Tầm nhìn (Vision), Yêu cầu nghiệp vụ (Requirements) và Kiến trúc hệ thống (Architecture) của Loyalty Service.
- Đối chiếu chéo với các tài liệu hợp đồng hiện có của Order Service, Payment Service, nền tảng POS Platform và Identity Service.
- Repository hiện tại chứa toàn bộ tài liệu thiết kế nhưng chưa có mã nguồn ứng dụng hay các file migration/API schema thực thi được.

---

## 2. Các Phát Hiện & Cảnh Báo (Warnings)

| # | Phân loại | Nội dung phát hiện | Rủi ro | Khuyến nghị giải quyết |
|---|---|---|---|---|
| 1 | Hợp đồng hệ thống cha (Parent Contract) | Tài liệu `OrderPaid.v1` hiện tại của Order Service chưa bao gồm các trường bổ sung của Loyalty: số tiền đủ điều kiện (`eligibleAmount`), phiên bản chính sách, các mã tham chiếu đơn giữ điểm/voucher và mã lượt checkout. | Các schema hoặc thư viện producer được sinh tự động có thể bỏ sót các trường cần thiết cho việc tích điểm/commit, dẫn đến việc tính toán dự phòng sai lệch hoặc các đơn giữ điểm bị treo. | Áp dụng các nội dung sửa đổi ở Mục 11 của tài liệu Contracts vào tài liệu hợp đồng của Order Service cha; bổ sung fixture kiểm thử schema giữa producer và consumer trước khi code. |
| 2 | Sự kiện hoàn tiền (Refund Event) | Sự kiện `OrderRefunded.v1` được định nghĩa bởi Loyalty Service nhưng chưa xuất hiện trong bảng sự kiện của Order Service; trạng thái hoàn tiền của Payment Service thì lại tách biệt. | Loyalty Service có thể không nhận được trigger hoàn tiền toàn bộ chuẩn tắc hoặc có thể tiêu thụ trùng lặp sự kiện hoàn tiền từ nhà cung cấp thanh toán. | Quy định Order Service là bên duy nhất phát hành sự kiện nghiệp vụ hoàn tiền chuẩn tắc sau khi hoàn tất thủ tục; ánh xạ bằng chứng từ Payment Service vào Order Service và kiểm thử kỹ luồng sự kiện hoàn tiền gửi lặp hoặc đến sai thứ tự. |
| 3 | Thứ tự sự kiện (Event Ordering) | Sự kiện `OrderCheckoutFailed` hoặc lệnh release gửi trễ có thể đến sau sự kiện `OrderPaid` do cơ chế phân phối của Message Broker là at-least-once và không bảo đảm thứ tự tuyệt đối. | Một lượt giữ điểm hoặc voucher đã commit có thể bị giải phóng nhầm sau khi đã tiêu thụ, hoặc một lượt giữ đã giải phóng lại bị commit ngược. | Mang theo số phiên bản vòng đời đơn hàng/checkout attempt và định nghĩa quy tắc ưu tiên trạng thái terminal: Trạng thái `COMMITTED/CONSUMED` luôn thắng lệnh release đến muộn; lệnh release gửi tới sau khi đã paid sẽ được xử lý dạng no-op kèm ghi log audit. |
| 4 | Tái hiện báo giá khuyến mãi (Quote Replay) | `quoteId` đóng vai trò là mã tham chiếu mờ (opaque reference) phục vụ bản chụp giỏ hàng và audit của Order Service, không phải là một bản ghi báo giá được lưu trữ dài hạn trong database của Loyalty ở P0. | Đội ngũ vận hành không thể tái hiện lại toàn bộ các quy tắc bị từ chối tạm thời từ phía Loyalty Service sau khi báo giá hết hạn nếu bản chụp của Order Service không đầy đủ. | Yêu cầu Order Service bắt buộc phải lưu snapshot đầy đủ cơ cấu chiết khấu và phiên bản quy tắc; bổ sung hợp đồng lưu trữ/truy vấn báo giá trong giai đoạn P1 nếu có nhu cầu replay vận hành từ Loyalty. |
| 5 | Hạch toán tiêu hao lô điểm (Redemption Accounting) | Tài liệu hợp đồng đã định nghĩa bảng phân bổ lô điểm và các biến động bù trừ có dấu, nhưng chưa cung cấp ví dụ thực thi cụ thể cho các tình huống: giải phóng điểm giữ, khôi phục điểm sau khi hoàn đơn, và tranh chấp khi điểm hết hạn. | Các lập trình viên triển khai có thể hiểu sai về việc lệnh release có khôi phục lại cột `remaining_points` hay chỉ đơn thuần xóa bản ghi phân bổ active. | Bổ sung các fixture bất biến và ví dụ transaction mẫu trong tài liệu kiểm thử domain/migration cho từng loại phân bổ và các kịch bản tranh chấp đồng thời. |
| 6 | Tính nguyên tử của hạn mức sử dụng (Usage Limit Atomicity) | Các trường hạn mức voucher và ràng buộc duy nhất đã được định nghĩa, nhưng việc cài đặt bộ đếm hạn mức tổng toàn hệ thống và hạn mức trên mỗi khách hàng được để ngỏ cho tầng migration/domain. | Cách lập trình ngây thơ kiểu "đọc đếm rồi mới chèn" (count-then-insert) có thể khiến số lượt sử dụng bị vượt hạn mức khi có nhiều lượt checkout diễn ra đồng thời. | Bắt buộc áp dụng khóa dòng trong câu lệnh SQL (`SELECT ... FOR UPDATE`) hoặc kiểm thử giao dịch mức Serializable; tuyệt đối không chỉ dựa vào các bước kiểm tra ở tầng ứng dụng. |
| 7 | Xác thực cổng khách hàng (Portal Authentication) | Các route OTP của cổng khách hàng đã được đặc tả, nhưng nhà cung cấp OTP, kho lưu trữ phiên đăng nhập và chính sách lưu trữ khóa nằm ngoài phạm vi nội bộ của Loyalty Service. | Việc triển khai cổng khách hàng có thể làm trùng lặp logic quản lý định danh hoặc lưu trữ các bằng chứng OTP không nhất quán. | Giữ OTP như một adapter/ranh giới xác thực riêng biệt, tài liệu hóa hợp đồng giao tiếp phiên/nhà cung cấp và chỉ lưu các claims gắn với tenant/khách hàng bên trong Loyalty Service. |

---

## 3. Phân Tích Tác Động Chéo (Cross-Impact Analysis)

| Chiều phân tích | Đánh giá hiện trạng | Mức độ rủi ro | Khuyến nghị |
|---|---|---|---|
| **Logic Dùng Chung (Shared Logic)** | Hợp đồng checkout, paid, failure và refund của Order Service tiêu thụ các tham chiếu của Loyalty; hiện chưa có code gọi hàm thực tế. | 🔴 Cao | Cập nhật đầy đủ các schema của hệ thống cha và bổ sung kiểm thử hợp đồng trước khi sinh code client hoặc consumer. |
| **Dữ Liệu & Trạng Thái (Data/State)** | Schema cơ sở dữ liệu bao quát đầy đủ khách hàng, sổ cái, lô điểm, phân bổ, đơn giữ điểm, chính sách, hạng thành viên, khuyến mãi, voucher, inbox/outbox và audit. | 🟡 Trung bình | Cụ thể hóa toàn bộ comment, ràng buộc toàn vẹn, hành vi khóa ngoại và chỉ mục xử lý đồng thời trong script migration; kiểm thử quy trình khôi phục/tái tạo. |
| **Hợp Đồng API (API Contracts)** | Toàn bộ bề mặt API cho nhân viên, checkout nội bộ, cấu hình quản trị và cổng khách hàng đều được phiên bản hóa kèm envelope và mã lỗi chuẩn. | 🟢 Thấp | Sinh file OpenAPI 3.1 và kiểm tra tính hợp lệ của các ví dụ payload; giữ các route token của cổng khách hàng hoàn toàn tách biệt khỏi API nhân viên. |
| **Tính Toàn Vẹn Của Luồng (Flow Integrity)** | Chuỗi luồng: Báo giá/Giữ điểm → Thanh toán Saga → OrderPaid commit/tích điểm và Thu hồi điểm khi hoàn tiền đã được đặc tả, nhưng thứ tự của lệnh release đến muộn cần số phiên bản từ Order. | 🟡 Trung bình | Bổ sung số phiên bản vòng đời đơn hàng và thứ tự ưu tiên trạng thái terminal vào bộ kiểm thử hợp đồng sự kiện giữa Order và Loyalty. |
| **Các Vấn Đề Xuyên Suốt (Cross-Cutting)** | Điều kiện tenant bắt buộc, che mờ dữ liệu PII, giới hạn tần suất gọi, cơ chế idempotent, outbox/inbox, DLQ và mô tả comment đều đã tường minh. | 🟡 Trung bình | Bổ sung các bài kiểm tra tự động về an toàn bảo mật, xử lý đồng thời và che mờ PII vào pipeline CI và quy trình review migration. |

---

## 4. Đánh Giá Tuân Thủ Hướng Dẫn (Instruction Compliance)

| Tiêu chuẩn | Kết luận | Đánh giá sai lệch (nếu có) | Khuyến nghị |
|---|---|---|---|
| **Quy ước (Conventions)** | ✅ Đạt | Các đường dẫn REST, envelope chuẩn, UUID v7, dấu thời gian UTC, phân trang con trỏ và phong bì sự kiện đều tuân thủ chặt chẽ quy ước nền tảng. | Duy trì áp dụng. |
| **Kiến trúc (Architecture)** | ⚠️ Cần chú ý | Tài liệu Hợp đồng đang phụ thuộc vào các trường bổ sung của Order Service và một sự kiện hoàn tiền chuẩn tắc mới. | Áp dụng các sửa đổi vào hệ thống cha trước khi bước vào triển khai mã nguồn. |
| **Kiểm thử (Testing)** | ⚠️ Cần chú ý | Hợp đồng đã có các yêu cầu nghiệm thu và xử lý đồng thời nhưng các fixture OpenAPI/AsyncAPI thực thi được chưa có sẵn trong repository. | Sinh mã kiểm thử schema và các fixture kịch bản tranh chấp/replay làm điều kiện tiên quyết trước khi ký duyệt triển khai. |

---

## 5. Kết Luận & Quyết Định (Verdict)

Tài liệu Hợp đồng kỹ thuật (Contracts) **được phê duyệt với các cảnh báo trên**.

Không còn bất kỳ rào cản nghiêm trọng nội bộ nào bên trong Loyalty Service. Việc cập nhật hợp đồng của Order Service cha, thiết lập thứ tự ưu tiên trạng thái terminal cho sự kiện và xây dựng bộ fixture kiểm thử tranh chấp đồng thời bắt buộc phải được đưa vào danh sách điều kiện tiên quyết trước khi lập trình.
