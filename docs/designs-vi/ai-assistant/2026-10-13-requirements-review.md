Started at: 2026/10/13 00:00:00
Finished at: 2026/10/13 00:00:00
Total time: 0 minutes
---

# Báo cáo đánh giá yêu cầu — AI Assistant (Spec Sentinel Report)

**Tài liệu yêu cầu:** [`2026-10-13-requirements.md`](./2026-10-13-requirements.md)  
**Ngày:** 2026-10-13  
**Trạng thái:** ⚠️ Cảnh báo (Tiến hành với sự thận trọng / Proceed with Caution)

## Phạm vi đánh giá (Review Scope)

- Tái sử dụng tài liệu Tầm nhìn (Vision) và Đánh giá tầm nhìn (Vision Review) đã được phê duyệt của AI Assistant.
- Đối chiếu chéo với nền tảng POS Platform cha, Identity, Order, Payment, Shift, Recommendation và khung sườn hiện có của Reporting.
- Hiện chưa có mã nguồn triển khai thực tế của AI Assistant/Reporting, mã OpenAPI/AsyncAPI sinh tự động hay mã migration cơ sở dữ liệu để truy vết; đợt đánh giá này tập trung vào tính mạch lạc của các yêu cầu và tính sẵn sàng của các giao ước liên dịch vụ.

## Các phát hiện (Findings)

### Danh sách cảnh báo (Warnings)

| # | Hạng mục | Phát hiện | Rủi ro | Khuyến nghị |
|---|---|---|---|---|
| 1 | Giao ước Reporting | Các công cụ `getAverageOrderValue`, `getHourlySales`, `compareBranches` và `getContributionDrivers` được yêu cầu nhưng dịch vụ Reporting cha mới chỉ có tóm tắt `sales_daily`/`top_items`. | Việc triển khai công cụ có nguy cơ tự tính toán số liệu từ dữ liệu không thống nhất định nghĩa hoặc truy vấn chéo cơ sở dữ liệu dịch vụ khác. | Tài liệu Architecture/Contracts bắt buộc phải bổ sung schema công cụ/read-model do Reporting sở hữu, định nghĩa số liệu, chính sách hoàn/hủy, múi giờ và giao ước kiểm tra độ tươi mới. |
| 2 | Lựa chọn sự kiện / truy vấn | Yêu cầu cho phép dùng công cụ Reporting đồng bộ hoặc các sự kiện `ReportProjectionWatermarkUpdated.v1`/`BusinessDayReady.v1` nhưng chưa chốt nguồn kích hoạt chính. | Hành vi của bộ lập lịch và kiểm tra độ tươi mới có thể không nhất quán giữa các dịch vụ; gây khó khăn cho việc phát lại và kiểm thử tính đầy đủ. | Chọn một giao ước chính trong Architecture; nếu kết hợp cả hai, cần nêu rõ truy vấn đồng bộ là thẩm quyền tối cao và sự kiện chỉ là tín hiệu gợi ý/kích hoạt. |
| 3 | Lưu trữ ngữ cảnh hội thoại | Tính năng hỏi tiếp nối đòi hỏi phải có ngữ cảnh nhưng mô hình dữ liệu lại cấm lưu prompt/phản hồi thô; hiện chưa có schema cho ngữ cảnh đã che giấu hoặc cách thức tái tạo prompt. | Lượt hỏi tiếp nối có thể mất ngữ cảnh hoặc vô tình lưu dữ liệu PII thô nhằm giữ ngữ cảnh. | Chốt cấu trúc tóm tắt lượt hỏi/ngữ cảnh ngữ nghĩa đã che giấu thông tin nhạy cảm, thời gian lưu trữ (TTL), mã hóa/quyền sở hữu khóa và nguyên tắc không gửi toàn bộ transcript thô cho nhà cung cấp LLM. |
| 4 | Nguồn xác thực phân quyền | `ResolvedScope` do máy chủ sinh ra nhưng chưa chỉ rõ API quyết định phân quyền/phiên bản cache của Identity, hành vi thu hồi quyền và cơ chế phòng thủ chiều sâu tại repository cho Reporting. | Việc thu hồi tư cách thành viên hoặc cache phân quyền bị cũ có thể cho phép truy vấn số liệu chi nhánh cũ. | Chốt giao ước ngữ cảnh phân quyền, kiểm tra phiên bản thành viên/thu hồi quyền và bắt buộc điều kiện lọc tenant/chi nhánh tại công cụ/repository của Reporting. |
| 5 | Mức độ tin cậy kết quả công cụ | Yêu cầu bắt buộc từ chối/sinh lại khi văn bản mâu thuẫn với dữ liệu cấu trúc nhưng chưa định nghĩa bộ kiểm tra phản hồi/ngân sách sửa lỗi và phương án xử lý khi mô hình vẫn tiếp tục sai. | Giao diện người dùng có thể hiển thị văn bản sai lệch dù số liệu cấu trúc đúng hoặc việc thử lại liên tục làm đội chi phí token. | Định nghĩa bộ xác thực schema câu trả lời, giới hạn tối đa một lần sửa có ranh giới, sau đó dùng số liệu cấu trúc làm phương án dự phòng; ghi nhận metric/audit khi có sai lệch. |
| 6 | Giờ chốt ca P2 / Dữ liệu muộn | Trạng thái `WAITING_FOR_DATA`/mốc kiểm chứng tính đầy đủ đã có máy trạng thái nhưng chưa có cam kết thời gian (SLA), thời gian chờ tối đa, quy trình can thiệp của vận hành viên và sự kiện điều chỉnh khi `OrderPaid`/hoàn tiền đến sau khi đã công bố. | Bản tóm tắt ngày có thể bị treo hoặc bị cũ (stale) mà không có cơ chế hiệu chỉnh minh bạch. | Bổ sung chính sách chờ sau giờ chốt ca, nhánh kết thúc `DATA_LATE`/chạy lại, phả hệ phiên bản và sự kiện kích hoạt `AIDailySummarySuperseded`. |
| 7 | Quản trị nhà cung cấp LLM | Hạn mức token/chi phí DeepSeek, khu vực/chính sách dữ liệu, phiên bản prompt, xoay vòng khóa, cảnh báo mô hình lỗi thời và cơ chế kiểm tra sức khỏe an toàn chưa được chuẩn hóa thành schema cấu hình. | Khó kiểm soát chi phí, tuân thủ an toàn thông tin và tính tái lập; endpoint kiểm tra sức khỏe có nguy cơ gửi dữ liệu không cần thiết. | Cụ thể hóa chính sách nhà cung cấp có phiên bản, tham chiếu khóa bí mật, kiểm tra sức khỏe không chứa PII, cùng các bộ mẫu ngân sách/ngắt mạch/DLQ. |
| 8 | Hành vi API | Endpoint hỏi đáp công khai cho phép trả về `200/202` nhưng chưa phân định rõ giữa câu trả lời đồng bộ ngay, lượt hỏi đưa vào hàng đợi, cơ chế polling/streaming và giao ước thời gian chờ phía client. | Dashboard có thể nhầm lẫn trạng thái hàng đợi/nhà cung cấp bận là câu trả lời cuối cùng hoặc client thử lại gây trùng lặp. | Chốt máy trạng thái phản hồi, địa chỉ polling/trạng thái turn khi nhận mã `202`, dời SSE/streaming ra ngoài phạm vi MVP, và quy định ngữ nghĩa khóa lũy quyền (idempotency key). |
| 9 | Thời gian lưu trữ / Kiểm toán | Thời gian lưu trữ metadata an toàn mặc định 90 ngày và chính sách kiểm toán cha đã được nêu nhưng chưa ánh xạ chi tiết từng trường/bảng/sự kiện với chính sách xóa/lưu trữ pháp lý. | Nguy cơ giữ metadata suy diễn từ câu hỏi quá lâu hoặc xóa nhầm các bản ghi kiểm toán bắt buộc. | Xây dựng ma trận phân loại dữ liệu/thời gian lưu trữ; mặc định không lưu prompt thô; các tham chiếu kiểm toán đã che giấu tuân theo thời hạn kiểm toán cha. |
| 10 | Ngữ nghĩa số liệu | Các chỉ số AOV, gross/net, ngày kinh doanh, chênh lệch so sánh và làm tròn tỷ lệ đóng góp chưa có các bộ dữ liệu mẫu chuẩn (golden fixtures) cho công thức tính toán. | Câu trả lời kiểu “giảm 12%” có thể không khớp với biểu đồ trên Dashboard/Reporting. | Chủ sở hữu Reporting công bố từ điển số liệu chuẩn và các bộ dữ liệu mẫu; AI Assistant chỉ có nhiệm vụ hiển thị đúng các dữ kiện nhận được. |

## Phân tích tác động chéo (Cross-Impact Analysis)

| Khía cạnh | Phát hiện | Mức độ rủi ro | Khuyến nghị |
|---|---|---|---|
| **Logic dùng chung** | AI Assistant không có các điểm gọi code thực thi sẵn; phụ thuộc hoàn toàn vào bộ giải quyết phạm vi của Identity và số liệu/read-model của Reporting. | 🔴 Cao (High) | Không sao chép logic tính số liệu hoặc xác thực quyền; sử dụng giao ước nội bộ và các bộ fixture sinh tự động. |
| **Dữ liệu / Trạng thái** | Phát sinh các trạng thái mới về hội thoại/lượt hỏi/công cụ/kiểm toán/tóm tắt; số liệu báo cáo vẫn hoàn toàn thuộc về Reporting. | 🟡 Trung bình (Medium) | Bắt buộc thực thi quyền sở hữu dữ liệu, các phiên bản bất biến, thời hạn lưu trữ/xóa và tuyệt đối không lưu prompt thô. |
| **Giao ước API** | Cần chuẩn hóa schema cho Public chat/summary API, các công cụ Reporting nội bộ, kiểm tra độ tươi mới/giờ chốt ca và adapter kết nối nhà cung cấp LLM. | 🔴 Cao (High) | Chốt các trạng thái phản hồi, tính lũy quyền, schema công cụ và các sửa đổi bổ sung cho Reporting cha trước khi viết Architecture. |
| **Tính toàn vẹn luồng** | Luồng chat: xác thực → phạm vi → lập kế hoạch → gọi công cụ → xác thực kết quả → sinh phản hồi; luồng tóm tắt: chốt ca → kiểm tra tính đầy đủ → mốc so sánh → phân tích đóng góp → LLM → công bố. | 🟡 Trung bình (Medium) | Bổ sung các nhánh xử lý quá thời gian chờ, dữ liệu muộn, kết quả từng phần, chạy lại và thay thế phiên bản vào các biểu đồ sequence diagram. |
| **Vấn đề xuyên suốt** | Tấn công chèn prompt (prompt injection), lỗ hổng IDOR chi nhánh, rò rỉ dữ liệu PII ra ngoài, chi phí nhà cung cấp LLM, độ tươi mới dữ liệu và lưu trữ kiểm toán. | 🔴 Cao (High) | Bắt buộc có ma trận đánh giá/an ninh, bộ kiểm thử tính năng che giấu dữ liệu, chính sách ngân sách và tài liệu hướng dẫn vận hành (runbook). |

## Mức độ tuân thủ chỉ dẫn (Instruction Compliance)

| Tiêu chí | Kết luận | Sai lệch (nếu có) | Khuyến nghị |
|---|---|---|---|
| **Quy ước (Conventions)** | ✅ | Định hướng chuẩn REST/OpenAPI, phong bì chuẩn, UUID v7, chuẩn giờ UTC, phân trang con trỏ và định danh tải công việc đều đồng nhất. | — |
| **Kiến trúc (Architecture)** | ⚠️ | Nguồn công cụ/số liệu của Reporting và giao ước gọi quyết định phân quyền Identity mới chỉ được tham chiếu chứ chưa cụ thể hóa schema. | Giải quyết dứt điểm quyền sở hữu và giao thức truyền tải trong Architecture; tuyệt đối không truy cập DB trực tiếp. |
| **Kiểm thử (Testing)** | ⚠️ | Danh sách tiêu chí nghiệm thu rất chi tiết nhưng các bộ dữ liệu đánh giá mô hình, fixture số liệu chuẩn, tập mẫu tấn công prompt, kiểm thử che dữ liệu và kiểm thử thời hạn lưu trữ vẫn chưa ở dạng khả thi để chạy tự động. | Bổ sung các fixture giao ước/an ninh/đánh giá trước khi bước vào triển khai mã nguồn. |

## Kết luận chung (Verdict)

Tài liệu yêu cầu **có thể được thông qua với các cảnh báo**. Không có vấn đề chặn (blocker) nào liên quan đến phạm vi nghiệp vụ hay nguyên tắc an ninh chỉ đọc. Các cảnh báo bắt buộc phải được giải quyết triệt để trong tài liệu Kiến trúc (Architecture) và Giao ước (Contracts), đặc biệt là quyền sở hữu số liệu của Reporting, lan truyền phân quyền Identity, ngữ cảnh hội thoại đã che giấu, ngữ nghĩa bất đồng bộ của API, xử lý dữ liệu phát sinh muộn của P2 và quản trị nhà cung cấp LLM.

**Quyết định yêu cầu từ người dùng:** Xác nhận đồng ý tiến hành sang giai đoạn Thiết kế Kiến trúc (Architecture) với trạng thái **⚠️ Cảnh báo (Tiến hành với sự thận trọng / Proceed with Caution)**.
