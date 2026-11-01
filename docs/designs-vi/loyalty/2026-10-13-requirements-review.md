# Báo Cáo Đánh Giá Yêu Cầu Nghiệp Vụ (Spec Sentinel Report)

**Tài liệu đánh giá:** [`docs/designs/loyalty/2026-10-13-requirements.md`](./2026-10-13-requirements.md)  
**Ngày:** 2026-10-13  
**Trạng thái:** ⚠️ Có cảnh báo — Tiến hành cẩn trọng (Warnings - Proceed with Caution)

---

## 1. Ngữ Cảnh Đánh Giá

- Giai đoạn Phase 0 được bỏ qua — tái sử dụng ngữ cảnh từ tài liệu Tầm nhìn (Vision) và các tài liệu thiết kế hiện có của các module liên quan.
- Quét toàn bộ repository: Dự án hiện chứa các tài liệu thiết kế và file `idea.md`, chưa có mã nguồn ứng dụng hay bộ kiểm thử thực thi.
- Đối chiếu chéo với các tài liệu: Yêu cầu/Hợp đồng của Order Service, Yêu cầu của Payment Service và Hợp đồng nền tảng POS Platform.

---

## 2. Các Phát Hiện & Cảnh Báo (Warnings)

| # | Phân loại | Nội dung phát hiện | Rủi ro | Khuyến nghị giải quyết |
|---|---|---|---|---|
| 1 | Hợp đồng API (API Contracts) | Loyalty Service yêu cầu trường `loyaltyEligibleAmountMinor` và `loyaltyPolicyVersion` trên sự kiện `OrderPaid.v1`, nhưng payload hiện tại của Order Service mới chỉ có các trường tổng tiền chung và snapshot dòng món ăn. | Loyalty Service có thể phải tự cài đặt lại các quy tắc định giá hoặc tính toán điểm thưởng sai lệch so với đơn hàng của Order Service. | Trong tài liệu Hợp đồng API & Dữ liệu, bổ sung các trường này dưới dạng trường bổ trợ (additive fields) kèm ngữ nghĩa, quy tắc kiểm tra và phiên bản hóa; viết test hợp đồng giữa producer và consumer. |
| 2 | Quyền sở hữu sự kiện (Event Ownership) | Tài liệu Requirements lắng nghe sự kiện `OrderRefunded.v1` cho luồng bồi hoàn toàn bộ P0, nhưng bảng sự kiện hiện tại của Order Service chưa định nghĩa sự kiện này, trong khi Payment Service lại sở hữu trạng thái hoàn tiền của nhà cung cấp. | Sự kiện hoàn tiền có thể bị phát trùng lặp, phát sai từ service không có thẩm quyền, hoặc phát trước khi giao dịch hoàn tiền hoàn tất; dẫn đến việc thu hồi điểm bị sai lệch. | Quy định rõ Order Service là bên phát hành sự kiện hoàn tiền nghiệp vụ chuẩn tắc sau khi hoàn tất thủ tục, tiếp nhận thông tin từ Payment Service. Payload sự kiện bao gồm `refundId`, `refundType`, số tiền/trạng thái và tính idempotent. |
| 3 | Tính toàn vẹn dữ liệu (Data Integrity) | Luồng thu hồi toàn bộ đơn yêu cầu khôi phục điểm đã đổi bằng một bản ghi/lô điểm đối ứng, nhưng thuật toán phân bổ lô điểm và hành vi xử lý khi điểm đã hết hạn hoặc đã bị tiêu tiếp vẫn chưa được chuẩn hóa chi tiết. | Việc bồi hoàn có thể làm sai lệch số dư khả dụng hoặc tạo ra các lô điểm bị âm trong các chuỗi lịch sử giao dịch dài hạn. | Tài liệu Architecture/Data Contracts cần chuẩn hóa thuật toán phân bổ lô điểm khi đổi điểm, truy vết nguồn gốc bồi hoàn, và các bất biến số dư giữa `earned`, `redeemed`, `reversed`, `expired` và điểm đang giữ (`held`). |
| 4 | Luồng liên Service (Cross-Service Flow) | `OrderPaid` vừa là tín hiệu kích hoạt tích điểm vừa là lệnh commit cho các lượt giữ điểm và voucher, trong khi hợp đồng hiện tại của Order Service chưa lưu snapshot rõ ràng mã giữ điểm (point hold ID) hay mã giữ voucher. | Loyalty Service có thể không xác định được chính xác lượt giữ điểm/voucher nào cần commit, đặc biệt khi đơn hàng có nhiều lần thử lại checkout hoặc thử nhiều lần thanh toán. | Mở rộng hợp đồng checkout và `OrderPaid` để bổ sung các trường tùy chọn tham chiếu hold ID / reservation ID của Loyalty, đồng thời đặc tả rõ luồng xử lý khi đơn không dùng điểm/voucher. |
| 5 | Phạm vi khuyến mãi (Promotion Scope) | Cơ chế giải quyết xung đột “Có lợi nhất cho khách hàng” kết hợp với việc cộng dồn (stacking) có thể đòi hỏi phải tính toán đánh giá nhiều tổ hợp quy tắc; tài liệu chưa quy định số lượng quy tắc active tối đa hay thời gian timeout khi tính toán. | Tập quy tắc quá lớn có thể làm chậm quá trình checkout tại quầy POS hoặc gây ra hành vi vận hành không tất định khi hệ thống chịu tải cao. | Giới hạn số lượng quy tắc khuyến mãi active tối đa, đặt timeout tính toán, sắp xếp các phương án ứng viên theo thứ tự tất định, và quy định hành vi fail-closed khi báo giá bị lỗi hoặc quá hạn. |
| 6 | Quản lý hồ sơ khách hàng | Tài liệu đã định nghĩa luồng tra cứu/tạo mới và bảo vệ dữ liệu PII, nhưng chưa quy định các trường thông tin tối thiểu được phép chỉnh sửa hoặc khách hàng có quyền cập nhật tên/sự đồng thuận/thông tin liên hệ hay không. | Các client khác nhau có thể tự ý phát triển các API cập nhật hồ sơ không tương thích hoặc làm rò rỉ quá mức dữ liệu PII. | Giữ phạm vi P0 ở mức tối giản và quy định rõ ràng các trường/quyền hạn trong tài liệu Contracts; hoãn việc bổ sung các thuộc tính mở rộng và tính năng gộp hồ sơ sang P1/P2. |
| 7 | Vòng đời bảo mật dữ liệu | Yêu cầu mã hóa và che mờ phản hồi đã có, nhưng cơ chế xoay vòng khóa (key rotation), chính sách lưu trữ/xóa dữ liệu và thời hạn lưu trữ dữ liệu OTP chưa được đặc tả cụ thể. | Dữ liệu PII và các bằng chứng xác thực có thể bị lưu trữ lâu hơn quy định chính sách hoặc khó xoay vòng khóa an toàn khi có sự cố. | Đưa các quy tắc về KMS, xoay vòng khóa mã hóa, thời gian lưu trữ/ẩn danh hóa dữ liệu và cơ chế lưu trữ mã OTP vào các mục Kiến trúc & Bảo mật. |

---

## 3. Phân Tích Tác Động Chéo (Cross-Impact Analysis)

| Chiều phân tích | Đánh giá hiện trạng | Mức độ rủi ro | Khuyến nghị |
|---|---|---|---|
| **Logic Dùng Chung (Shared Logic)** | Chưa có code gọi hàm thực tế. Ranh giới chia sẻ khái niệm xoay quanh: `OrderPaid.v1`, `OrderRefunded.v1`, phiên checkout và mã tham chiếu khách hàng. | 🟡 Trung bình | Coi schema sự kiện như các API dùng chung; bổ sung kiểm thử tương thích schema và kiểm thử replay trước khi triển khai code. |
| **Dữ Liệu & Trạng Thái (Data/State)** | Loyalty Service đưa vào: sổ cái chỉ ghi thêm, các lô điểm FIFO, các đơn giữ điểm, giữ voucher, bộ đếm lượt dùng, lịch sử hạng và các view tổng hợp số dư. | 🔴 Cao | Áp dụng triệt để ràng buộc duy nhất trong database, truy vết phân bổ, điều kiện bắt buộc tenant và ranh giới transaction trong Database Design. |
| **Hợp Đồng API (API Contracts)** | Sự kiện `OrderPaid.v1` của Order Service hiện thiếu các trường số tiền đủ điều kiện/phiên bản chính sách của Loyalty; sự kiện hoàn tiền chưa được định nghĩa. | 🔴 Cao | Giải quyết dứt điểm hai hợp đồng sự kiện này trước khi lập kế hoạch triển khai; ưu tiên bổ sung các trường có phiên bản để đảm bảo tương thích ngược. |
| **Tính Toàn Vẹn Của Luồng (Flow Integrity)** | Chuỗi luồng: POS → Báo giá/Giữ điểm Order → Thanh toán Saga → OrderPaid → Commit điểm Loyalty; Hoàn tiền → OrderRefunded → Thu hồi điểm Loyalty. | 🟡 Trung bình | Vẽ sơ đồ tuần tự chi tiết cho các luồng: thành công, thanh toán thất bại, timeout, sự kiện gửi lặp và hoàn tiền toàn bộ. |
| **Các Vấn Đề Xuyên Suốt (Cross-Cutting)** | Phân quyền tenant/chi nhánh, bảo vệ dữ liệu PII, tính idempotent, cơ chế gửi qua outbox, xử lý DLQ/replay và phân bổ lease cho job. | 🔴 Cao | Áp dụng phòng thủ chuyên sâu đa tầng, nhật ký kiểm toán có cấu trúc, hệ thống metrics/cảnh báo và giới hạn retry cho mọi mutation và consumer. |

---

## 4. Đánh Giá Tuân Thủ Hướng Dẫn (Instruction Compliance)

| Tiêu chuẩn | Kết luận | Đánh giá sai lệch (nếu có) | Khuyến nghị |
|---|---|---|---|
| **Quy ước (Conventions)** | ✅ Đạt | Tài liệu nằm đúng cấu trúc thư mục `docs/designs/loyalty/`, phân chia rõ ràng các mức ưu tiên P0/P1/P2 và liên kết đầy đủ với các tài liệu hệ thống cha. | Duy trì áp dụng. |
| **Kiến trúc (Architecture)** | ⚠️ Cần chú ý | Ranh giới của Loyalty Service rất rõ ràng, nhưng đang phụ thuộc vào các trường sự kiện và sự kiện hoàn tiền của Order Service chưa có trong hợp đồng cha. | Giải quyết các điểm phụ thuộc này trong tài liệu API/Data Contracts và cập nhật tài liệu hợp đồng cha liên quan. |
| **Kiểm thử (Testing)** | ⚠️ Cần chú ý | Tiêu chí nghiệm thu đã bao quát các bất biến cốt lõi, nhưng các bài kiểm thử hợp đồng schema, kiểm thử xử lý đồng thời và kiểm thử replay chưa được ánh xạ vào các artifact cụ thể. | Bổ sung ma trận kiểm thử chi tiết trong giai đoạn Architecture/Contracts; bao gồm các case: gửi lặp, giữ điểm đồng thời, FIFO, thu hồi điểm và kiểm tra xuyên tenant. |

---

## 5. Kết Luận & Quyết Định (Verdict)

Tài liệu Yêu cầu nghiệp vụ (Requirements) **đạt độ hoàn thiện cần thiết để chuyển sang giai đoạn Thiết kế Kiến trúc (Architecture) với sự cẩn trọng**.

Không có yêu cầu nội bộ nào của Loyalty Service bị tắc nghẽn (blocked), nhưng các điểm phụ thuộc về sự kiện giữa Order Service và Loyalty Service bắt buộc phải được giải quyết dứt điểm trước khi lập kế hoạch triển khai mã nguồn.

**Hành động tiếp theo:** Sau khi người dùng phê duyệt, tiến hành xây dựng tài liệu Kiến trúc (Architecture) và đưa 4 cảnh báo đầu tiên thành các quyết định kiến trúc rõ ràng cùng các nhiệm vụ cập nhật hợp đồng liên quan.
