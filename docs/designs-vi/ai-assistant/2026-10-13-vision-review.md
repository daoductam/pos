Started at: 2026/10/13 00:00:00
Finished at: 2026/10/13 00:00:00
Total time: 0 minutes
---

# Báo Cáo Spec Sentinel — Đánh Giá Tầm Nhìn (Vision) Trợ Lý AI

**Tài liệu Spec:** [`docs/designs-vi/ai-assistant/2026-10-13-vision.md`](file:///d:/vibe-code/pos/docs/designs-vi/ai-assistant/2026-10-13-vision.md)  
**Ngày:** 2026-10-13  
**Trạng thái:** ⚠️ Cảnh báo (Tiếp tục một cách thận trọng)

## Phạm Vi Đánh Giá

- Tái sử dụng các thiết kế đã được phê duyệt của POS Platform, Identity, Order, Payment và Recommendation.
- Quét codebase để tìm kiếm các triển khai hiện có của AI Assistant, LLM, Reporting, dashboard và truy vấn.
- Chưa tìm thấy mã nguồn ứng dụng; hợp đồng POS gốc hiện mới chỉ có khung cấu trúc Reporting cho `sales_daily` và `top_items`.

## Các Phát Hiện

### Cảnh Báo

| # | Hạng mục | Phát hiện | Rủi ro | Khuyến nghị |
|---|---|---|---|---|
| 1 | Ranh giới Reporting | Vision nêu 6 nhóm công cụ (doanh thu, số đơn, AOV, top items, so sánh chi nhánh, theo giờ) nhưng hợp đồng Reporting gốc chưa có schema/endpoint cho AOV, theo giờ và phân tích đóng góp. | Requirements có thể tự ý tạo ra nhiều cách tính toán khác nhau hoặc truy vấn trực tiếp DB của service khác, phá vỡ ranh giới CQRS. | Requirements bắt buộc phải định nghĩa hợp đồng công cụ/read model của Reporting, chủ sở hữu của chỉ số, múi giờ kinh doanh, quy tắc xử lý hoàn tiền/hủy đơn và watermark độ tươi mới. |
| 2 | Độ hoàn tất của ngày kinh doanh | Bản tóm tắt cuối ngày phụ thuộc vào mốc đóng ngày kinh doanh và watermark hoàn tất, nhưng chưa có chủ thể/thuật toán xử lý khi chi nhánh chưa đóng ca, `OrderPaid` đến muộn hoặc đối soát hoàn tất sau mốc cutoff. | Bản tóm tắt có thể phát hành quá sớm, sai lệch baseline so sánh hoặc không thể tái lập lại. | Chốt trạng thái hoàn tất của Reporting, chính sách cutoff, quy trình chạy lại / điều chỉnh khi có dữ kiện trễ và khóa tính lũy kế trong Requirements/Contracts. |
| 3 | Vòng lặp công cụ LLM | Vision đề cập đến function calling nhưng chưa chốt số lượt gọi công cụ tối đa, schema tham số cho phép, timeout của công cụ, máy trạng thái phản hồi của mô hình và hành vi khi công cụ trả về kết quả bán phần. | Vòng lặp prompt/tool có thể chạy vô hạn, gây tốn kém chi phí token hoặc trả về kết quả không được kiểm chứng. | Định nghĩa vòng lặp công cụ có chặn (bounded tool loop), kiểm tra hợp lệ bằng JSON schema, giới hạn timeout/ngân sách, các trạng thái từ chối/kết quả bán phần và fallback khi không dùng tool. |
| 4 | Ngữ nghĩa phiên hội thoại | Chưa chốt ngữ cảnh phiên (session context), câu hỏi tiếp nối (follow-up question), phạm vi chi nhánh khi người dùng đổi chi nhánh giữa các lượt chat và chính sách lưu trữ/xóa hội thoại. | Có thể dùng nhầm phạm vi/kỳ báo cáo từ lượt hỏi trước hoặc lưu giữ prompt lâu hơn chính sách quyền riêng tư cho phép. | Thiết kế hợp đồng hội thoại/phiên làm việc với phạm vi đã phân giải bất biến trên từng lượt hỏi, cơ chế đổi phạm vi tường minh, lưu trữ đã làm sạch PII và TTL hết hạn. |
| 5 | Quản trị Provider LLM | Model/phiên bản của DeepSeek, khu vực lưu trữ/egress mạng, ngân sách token, chính sách sử dụng dữ liệu của nhà cung cấp, xoay vòng API key và quy trình xử lý sự cố mới chỉ là các điểm chưa rõ. | Rủi ro về tuân thủ pháp lý, chi phí ngoài dự kiến và khó khăn trong việc kiểm toán sự sai lệch của prompt/model (model drift). | Requirements phải có hợp đồng adapter cho provider, prompt/chính sách có quản lý phiên bản, ngân sách/timeout, quản lý secret, làm sạch dữ liệu và runbook khi provider gặp sự cố. |
| 6 | Ranh giới giải thích được | Vision yêu cầu nêu “yếu tố liên quan” từ phân tích đóng góp nhưng chưa chốt công thức đóng góp, ngưỡng trọng yếu tối thiểu, tiêu chí hòa điểm và cách hiển thị các yếu tố âm/không xác định. | LLM có thể diễn đạt đúng số liệu nhưng gây hiểu nhầm về nguyên nhân thực tế hoặc làm tròn số sai. | Định nghĩa schema đóng góp, phiên bản công thức tính toán, quy tắc làm tròn, top-N/ngưỡng kích hoạt và chính sách ngôn từ chuẩn mực trước khi triển khai daily summary. |
| 7 | Phòng thủ chiều sâu trong phân quyền | Vision đã nêu phạm vi phía server và điều kiện lọc tại công cụ, nhưng chưa chỉ rõ tool gateway hay repository nào là nguồn quyết định phân quyền và cách xử lý việc so sánh chéo chi nhánh của Owner. | Một công cụ có thể vô tình tin tưởng tham số chi nhánh từ LLM/client hoặc cho phép tổng hợp dữ liệu xuyên tenant ngoài ý muốn. | Chốt chuỗi thực thi: Bộ phân giải phạm vi → Chính sách công cụ → Repository của Reporting; mọi request đều phải lưu phạm vi đã phân giải và có các fixture kiểm thử IDOR tiêu cực. |
| 8 | Hiển thị trên Dashboard | Khung chat dashboard, cơ chế stream / kết quả bán phần và việc hiển thị bản tóm tắt P2 chưa có ranh giới UI/API hay hợp đồng thông báo/trạng thái đã đọc rõ ràng. | Frontend có thể hiển thị câu trả lời bán phần/không chắc chắn như một dữ kiện chính thức hoặc tạo ra các bản tóm tắt trùng lặp. | Requirements cần định nghĩa các trạng thái phản hồi, hợp đồng UI cho độ tươi mới/không chắc chắn, ngữ nghĩa đọc/xác nhận/chạy lại bản tóm tắt; giữ cơ chế thông báo ngoài MVP nếu chưa thực sự cần. |

## Phân Tích Tác Động Chéo (Cross-Impact Analysis)

| Chiều tác động | Phát hiện | Mức rủi ro | Khuyến nghị |
|---|---|---|---|
| **Logic Dùng Chung (Shared Logic)** | Chưa có mã nguồn triển khai của AI Assistant; phụ thuộc chặt chẽ vào read model của Reporting và phân quyền của Identity. | 🟡 Trung bình | Tuyệt đối không tự viết logic tính toán chỉ số bên trong AI Assistant; tái sử dụng các định nghĩa chỉ số / công cụ do Reporting sở hữu. |
| **Dữ Liệu/Trạng Thái (Data/State)** | Hội thoại, nhật ký kiểm toán đã làm sạch, lịch sử gọi công cụ, chính sách provider và trạng thái bản tóm tắt cuối ngày là các trạng thái dữ liệu mới; Reporting tiếp tục là nguồn sự thật duy nhất. | 🟡 Trung bình | Tách bạch quyền sở hữu, chính sách lưu trữ và ranh giới phát lại; tuyệt đối không lưu raw PII hay prompt thô. |
| **Hợp Đồng API (API Contracts)** | Cần các API công khai cho chat/phiên trợ lý, công cụ Reporting nội bộ, adapter provider và API đọc/chạy lại bản tóm tắt. | 🔴 Cao | Chốt toàn bộ schema, xác thực, mã lỗi, tính lũy kế và nguồn gốc dữ liệu phản hồi trước khi bước vào Kiến trúc. |
| **Tính Toàn Vẹn Luồng (Flow Integrity)** | Dashboard → Bộ phân giải phạm vi → LLM gọi công cụ → Reporting → Câu trả lời đã xác thực; Scheduler → Độ hoàn tất → Phân tích đóng góp → Bản tóm tắt. | 🟡 Trung bình | Bổ sung các đường dẫn xử lý timeout, lỗi công cụ/bán phần, dữ kiện đến muộn, chạy lại và dữ liệu bị cũ. |
| **Mối Quan Tâm Xuyên Suốt (Cross-Cutting)** | Tấn công prompt injection, lỗ hổng IDOR chi nhánh, rò rỉ PII qua egress, sự cố/chi phí provider và độ tươi mới của chỉ số là các trọng tâm rủi ro hàng đầu. | 🔴 Cao | Thực thi chính sách phía máy chủ, allowlist công cụ, làm sạch dữ liệu, ngân sách token, kiểm toán và kiểm thử bảo mật tiêu cực. |

## Tuân Thủ Hướng Dẫn Kỹ Thuật (Instruction Compliance)

| Chiều đánh giá | Kết luận | Độ lệch (nếu có) | Khuyến nghị |
|---|---|---|---|
| **Quy ước chuẩn (Conventions)** | ✅ | Chuẩn REST/OpenAPI 3.1, phong bì chung, UUID v7, UTC, phạm vi tenant/chi nhánh và read model hướng sự kiện đều được tôn trọng ở cấp độ Vision. | — |
| **Kiến trúc (Architecture)** | ✅ | AI Assistant không đọc database của service khác trực tiếp, không thay thế Reporting và không gọi bất kỳ ranh giới thay đổi trạng thái nào. | Giữ Reporting là chủ sở hữu chỉ số duy nhất trong Requirements/Contracts. |
| **Kiểm thử (Testing)** | ⚠️ | Vision đã nêu tiêu chí thành công và hàng rào bảo vệ nhưng chưa có các fixture thực thi cho prompt injection, phạm vi công cụ, sự cố provider, độ tươi mới và chạy lại summary. | Chuyển đổi thành ma trận kiểm thử hợp đồng, bảo mật và đánh giá chất lượng trong Requirements. |

## Kết Luận Đánh Giá (Verdict)

Tài liệu Tầm nhìn (Vision) **có thể tiếp tục sang Requirements kèm các cảnh báo**. Không có rào cản ngăn chặn (blocker) nào về mục tiêu, ranh giới người dùng hay nguyên tắc chỉ đọc / tương tác qua công cụ. Các cảnh báo bắt buộc phải được chuyển hóa thành các hợp đồng cụ thể cho các công cụ Reporting, phiên/phạm vi hội thoại, máy trạng thái LLM, chính sách provider, công thức đóng góp và độ hoàn tất của daily summary trước khi viết code.

## Quyết Định Cần Người Dùng Phê Duyệt

Xác nhận đồng ý tiếp tục sang bước Requirements với trạng thái **⚠️ Cảnh báo (Tiếp tục một cách thận trọng)**.
