Started at: 2026/10/13 00:00:00
Finished at: 2026/10/13 00:00:00
Total time: 0 minutes
---

# Báo cáo đánh giá giao ước — AI Assistant (Spec Sentinel Report)

**Tài liệu giao ước:** [`docs/designs/ai-assistant/2026-10-13-contracts.md`](./2026-10-13-contracts.md)  
**Ngày:** 2026-10-13  
**Trạng thái:** ✅ Đã phê duyệt (Đã khắc phục xong các cảnh báo thiết kế; giữ lại các cổng artifact phục vụ triển khai)

## Bối cảnh đánh giá (Review Context)

- Bỏ qua Giai đoạn 0 — Tái sử dụng Tầm nhìn (Vision), Yêu cầu nghiệp vụ (Requirements), Kiến trúc (Architecture) và ngữ cảnh biểu đồ tuần tự đã được phê duyệt từ quy trình thiết kế hiện tại.
- Quét kho mã nguồn không tìm thấy mã triển khai ứng dụng hay mã migration nào; do đó đợt đánh giá tập trung vào các artifact thiết kế và giao ước dịch vụ cha thay vì các điểm gọi mã thực tế.
- Tài liệu giao ước bao quát đầy đủ REST công khai/nội bộ, các công cụ Reporting, chính sách adapter kết nối DeepSeek, các sự kiện, schema PostgreSQL, thời hạn lưu trữ và các bài kiểm thử giao ước.

## Các phát hiện (Findings)

### Danh sách cảnh báo (Warnings)

| # | Hạng mục | Phát hiện | Rủi ro | Khuyến nghị |
|---|---|---|---|---|
| 1 | Đồng bộ giao ước cha | Yêu cầu gọi sang Identity của AI Assistant dùng `subjectId`, `requestedBranchIds`, `permission`, trong khi giao ước Identity cha hiện đặt tên trường là `subject`, `branchId`, `requiredPermission` và chưa làm lộ các trường phân quyền đã giải quyết trong phản hồi. | Client sinh tự động có thể sai lệch tên trường hoặc vô tình bỏ sót ngữ cảnh chi nhánh/phiên bản; phân quyền có thể bị đóng ngắt an toàn hoặc triển khai không nhất quán. | Bổ sung phần sửa đổi bổ sung tường minh hoặc ánh xạ adapter có phiên bản trong Identity; công bố schema máy đọc được và các bộ fixture cho phạm vi tenant, phạm vi chi nhánh, từ chối, thu hồi và lan truyền phiên bản. |
| 2 | Tính đầy đủ giao ước Reporting | Giao ước Reporting cha hiện chỉ ghi nhận `sales_daily` và `top_items`, trong khi giao ước này bổ sung thêm `sales_by_branch`, `sales_by_hour`, `aov` và `contribution_drivers` cùng 7 schema phản hồi công cụ chỉ dưới dạng văn xuôi. | Các dịch vụ khác nhau có thể tính AOV, khung giờ, xử lý hoàn tiền hoặc tỷ lệ đóng góp khác nhau; câu trả lời của AI có thể lệch so với Dashboard. | Trước khi viết code, công bố OpenAPI/JSON Schema do Reporting sở hữu, từ điển số liệu, quy tắc `calculationVersion` và các bộ golden fixture cho dữ liệu rỗng, hoàn tiền/điều chỉnh, múi giờ, bằng điểm và dữ liệu muộn. |
| 3 | Route công cụ động | Endpoint `POST /internal/v1/reporting/ai-tools/{toolName}` được thiết kế động có chủ đích, nhưng giao ước chưa định nghĩa cơ chế phân phối nguyên tử cùng một phiên bản registry, mã băm digest và trạng thái kích hoạt tới cả AI Assistant và Reporting. | Một công cụ có thể được dịch vụ này chấp nhận nhưng bị dịch vụ kia từ chối, hoặc schema đối số cũ gửi sang tầng triển khai mới hơn. | Đặt route phía sau một registry sinh tự động; bắt buộc có `toolVersion` cùng mã băm digest registry/schema, từ chối khi không khớp và kiểm thử các trường hợp công cụ lạ/bị tắt/lệch phiên bản. |
| 4 | Quản trị nhà cung cấp LLM | Trường `dailyBudgetMinor` đã có `budgetCurrency`, nhưng cơ chế hạch toán chi phí, thứ tự ưu tiên mặc định giữa tenant/nền tảng, việc đặt trước ngân sách đồng thời và tương quan yêu cầu/lần thử chưa ở dạng máy đọc được. | Kiểm soát ngân sách có thể gặp tranh chấp (race condition) dưới các lượt hỏi đồng thời, và chi phí LLM khó đối soát hoặc kiểm toán. | Định nghĩa bản ghi sử dụng nhà cung cấp hoặc giao ước đặt trước chi phí nguyên tử với `providerRequestId`, `attemptId`, chi phí ước tính/thực tế, tiền tệ, phiên bản chính sách và kết quả đặt trước. |
| 5 | Ranh giới đầu ra an toàn | `answer_payload` và `safe_content` được mô tả là đã kiểm định/che giấu dữ liệu, nhưng giao ước chưa cung cấp JSON Schema hoặc cấu trúc phản hồi lỗi chuẩn khi khâu xác thực/che giấu thất bại. | Văn bản mô tả thô của mô hình hoặc các định danh ẩn có thể bị lưu trữ hoặc hiển thị nếu quá trình triển khai chỉ xem việc xác thực mang tính khuyến nghị. | Bổ sung `AnswerSchema`, `SummaryContentSchema` có phiên bản cùng các fixture bất biến che giấu dữ liệu; chỉ commit/công bố sau khi đã xác thực schema và quét PII/ID, nếu không sẽ đóng ngắt an toàn với mã lỗi an toàn. |
| 6 | Ràng buộc phiên bản tóm tắt | Quy tắc “Chỉ có một phiên bản tóm tắt hiện hành” và mốc kiểm chứng tăng đơn điệu đã được nêu, nhưng chỉ mục duy nhất một phần (partial unique index), thứ tự giao dịch và hành vi chạy lại đồng thời chưa được đặc tả chính xác. | Hai worker có thể cùng công bố các phiên bản cạnh tranh hoặc đánh dấu nhầm bản ghi bị thay thế trong các lần chạy lại khi có số liệu muộn. | Định nghĩa các ràng buộc SQL/giao thức khóa: chèn bản hiệu chỉnh bất biến, so sánh-và-đổi (CAS) trên mốc nguồn, chỉ mục một phần cho bản hiện hành hoặc dòng con trỏ hiện hành, và ghi outbox lũy quyền trong cùng một transaction. |
| 7 | Chi tiết schema sự kiện | Payload tối thiểu của sự kiện đã được liệt kê, nhưng schema sự kiện phát ra chưa định nghĩa chính xác tham chiếu tác nhân/kiểm toán, tóm tắt phạm vi an toàn, enum trạng thái, đối tượng mốc nguồn và ngữ nghĩa phiên bản aggregate. | Các thành phần tiêu thụ sự kiện kiểm toán có thể suy diễn sai ý nghĩa, và việc phát lại/khử trùng lặp có thể không bảo toàn được phân quyền hoặc phả hệ phiên bản. | Bổ sung payload AsyncAPI/JSON Schema với các trường bắt buộc, quy tắc cho phép null, giá trị enum, `aggregateVersion`, `scopeSummary`, `auditReferenceId` và các fixture tương thích. |
| 8 | Thời hạn lưu trữ và xóa dữ liệu | Thời hạn lưu trữ được thiết kế có thể cấu hình linh hoạt, nhưng giao ước chưa ánh xạ chi tiết từng bảng/trường với hành vi xóa, giữ pháp lý, tombstone và loại trừ khỏi việc huấn luyện mô hình. | Lệnh xóa có thể xóa nhầm bằng chứng bắt buộc phải giữ, hoặc ngữ cảnh cấu trúc được giữ lại bị đem đi huấn luyện trái với chính sách. | Công bố ma trận lưu trữ theo phân loại dữ liệu/bảng, nêu rõ thứ tự ưu tiên của lệnh giữ pháp lý, tính lũy quyền của tác vụ xóa, ngữ nghĩa tombstone và điều kiện bất biến `not_for_training`. |
| 9 | Ngữ nghĩa lỗi và bất đồng bộ | Lượt hỏi công khai trả mã `200`/`202` và cơ chế polling đã được mô tả, nhưng schema phản hồi chính xác cho mã `202`, metadata vị trí/thử lại, lỗi kết thúc từ nhà cung cấp và phương án dự phòng số liệu cấu trúc chưa được cụ thể hóa đầy đủ. | Dashboard có thể hiển thị văn bản chưa hoàn chỉnh hoặc poll vô hạn sau khi rơi vào `DATA_LATE`, lỗi nhà cung cấp hoặc lỗi xác thực. | Bổ sung ví dụ OpenAPI cho mọi trạng thái kết thúc và trạng thái hàng đợi của lượt hỏi, bao gồm `pollAfterMs`, `expiresAt`, `safeFacts`, mã lỗi kết thúc và bất biến không có câu trả lời cuối cùng ở trạng thái tạm thời. |
| 10 | Ngữ nghĩa fixture số liệu | Trường `sales_daily.order_count` là trường của dịch vụ cha, trong khi giao ước AI gọi là `paidOrderCount` và dựa vào sự kiện `OrderPaid.v1` hoàn tất duy nhất; các sự kiện hoàn tiền/điều chỉnh đã duyệt và thanh toán muộn mới chỉ được nhắc tới chứ chưa có fixture cụ thể. | Chỉ số AOV và doanh thu có thể không khớp với Reporting khi khoản thanh toán bị chậm, được hoàn tiền hoặc điều chỉnh sau khi đã chiếu dữ liệu. | Bổ sung các fixture chuẩn cho sự kiện `OrderPaid` trùng lặp, thanh toán đối soát muộn, hoàn tiền toàn phần/từng phần, sự kiện điều chỉnh, xây dựng lại bản chiếu và biên ngày kinh doanh chi nhánh; làm lộ phiên bản chính sách số liệu trong mọi kết quả công cụ. |

## Phân tích tác động chéo (Cross-Impact Analysis)

| Khía cạnh | Phát hiện | Mức độ rủi ro | Khuyến nghị |
|---|---|---|---|
| **Logic dùng chung** | Chưa có ký hiệu mã nguồn hoặc điểm gọi thực tế. Các ranh giới dùng chung đề xuất là phân quyền Identity, công cụ số liệu Reporting và phong bì sự kiện cha. | 🟡 Trung bình (Medium) | Coi các giao ước cha là các phụ thuộc có phiên bản; bổ sung mã client sinh tự động và kiểm thử tương thích trước khi triển khai. |
| **Dữ liệu / Trạng thái** | Các bảng do AI sở hữu về hội thoại, lượt hỏi, thực thi công cụ, tác vụ/phiên bản tóm tắt, chính sách nhà cung cấp và tham chiếu kiểm toán giới thiệu trạng thái đã mã hóa/che giấu cùng phả hệ tóm tắt bất biến. | 🟡 Trung bình (Medium) | Cụ thể hóa các migration với ràng buộc check, điều kiện lọc tenant, tính duy nhất của phiên bản hiện hành/một phần, tác vụ xóa dữ liệu và `COMMENT ON COLUMN`. |
| **Giao ước API** | Các API chat/tóm tắt công khai và các endpoint nội bộ Identity/Reporting/job/provider là mới; sự sai lệch tên trường Identity và route công cụ động cần một schema chuẩn mực. | 🟡 Trung bình (Medium) | Sinh tài liệu OpenAPI 3.1 từ các schema chuẩn và chạy kiểm thử tương thích phía gọi/phía cung cấp trong quy trình CI. |
| **Tính toàn vẹn luồng** | Luồng chat: xác thực → phạm vi → vòng lặp công cụ → kiểm định → trả lời/poll; luồng tóm tắt: chốt ca → tính đầy đủ → mốc so sánh / đóng góp → nhà cung cấp → công bố / phiên bản. Các trạng thái lỗi đã được bao quát, nhưng payload kết thúc bất đồng bộ và việc ghi phiên bản đồng thời cần schema cụ thể. | 🟡 Trung bình (Medium) | Bổ sung fixture giao ước L1/L2 cho các trường hợp từ chối, timeout, dữ liệu chậm, `DATA_LATE`, chạy lại và thay thế phiên bản; kiểm thử các chuyển đổi trạng thái mang tính giao dịch. |
| **Vấn đề xuyên suốt** | Cách ly chi nhánh/tenant, tấn công prompt injection, che giấu dữ liệu PII, lưu lượng ra ngoài nhà cung cấp, ngân sách, kiểm toán và khả năng quan sát đều nằm trong phạm vi rõ ràng. | 🟡 Trung bình (Medium) | Bắt buộc thực thi cơ chế phòng thủ chiều sâu tại Identity, chính sách AI, Tool Gateway và repository của Reporting; quét kiểm tra logs/traces/events/payload nhà cung cấp trong các bài kiểm thử an ninh. |

## Mức độ tuân thủ chỉ dẫn (Instruction Compliance)

| Tiêu chí | Kết luận | Sai lệch (nếu có) | Khuyến nghị |
|---|---|---|---|
| **Quy ước (Conventions)** | ✅ | Tuân thủ đầy đủ chuẩn REST/OpenAPI 3.1, UUID v7, giờ UTC, phong bì chuẩn, phân trang con trỏ, sự kiện ít nhất một lần và PostgreSQL do dịch vụ sở hữu. | — |
| **Kiến trúc (Architecture)** | ✅ | Thiết kế bảo toàn Reporting là chủ sở hữu số liệu, Identity là thẩm quyền phân quyền trực tuyến và AI Assistant là ranh giới điều phối chỉ đọc. | Giải quyết dứt điểm sự đồng bộ schema với Identity cha và cơ chế bắt tay phiên bản/mã băm registry sinh tự động trước khi viết code. |
| **Kiểm thử (Testing)** | ⚠️ | Ma trận kiểm thử rất rộng, nhưng một số fixture bắt buộc mới chỉ được đặt tên chứ chưa được cụ thể hóa thành schema/ví dụ thực tế. | Xây dựng các fixture OpenAPI/AsyncAPI/JSON Schema cùng kiểm thử chuyển đổi trạng thái/migration như điều kiện tiên quyết trước khi triển khai. |

## Kết luận chung (Verdict)

Tài liệu Giao ước (Contracts) đã đủ độ hoàn thiện để bước vào giai đoạn Hoàn thiện thiết kế (Finalization). Các cảnh báo ở cấp độ thiết kế đã được khắc phục hoàn toàn trong tài liệu Giao ước hiệu chỉnh. Tuyệt đối không viết code trực tiếp từ các mô tả văn xuôi; các đầu việc còn lại là tạo các artifact triển khai thực tế và cập nhật giao ước cha, không còn điểm mơ hồ nào trong thiết kế.

## Xác minh sau khắc phục cảnh báo (Post-Remediation Verification)

| Cảnh báo ban đầu | Khắc phục trong Contracts | Xác minh kiểm chứng |
|---|---|---|
| Lệch trường dữ liệu với Identity | Các trường chuẩn `subject`, `requestedBranchIds`, `requiredPermission`, phong bì phản hồi và các trường phiên bản | Mục §5 và phần sửa đổi bổ sung Identity |
| Schema Reporting / Sai lệch số liệu | Phiên bản registry / mã băm digest, kết quả công cụ được bọc chuẩn, từ điển số liệu và mã fixture | Mục §6.2–§6.4 và §13 Reporting |
| Route công cụ động | Cơ chế bắt tay mã băm digest registry và quy tắc triển khai nguyên tử | Mục §6.1 và §6.4 |
| Quản trị nhà cung cấp / Tranh chấp ngân sách | ID yêu cầu/lần thử nhà cung cấp, kết quả đặt trước chi phí, bảng sử dụng và quy tắc đồng thời | Mục §7.3 và §10.9 |
| Lưu trữ câu trả lời / Tóm tắt không an toàn | Các payload an toàn chuẩn mực, đối soát luận điểm, quét rà soát PII/ID và hành vi đóng ngắt an toàn | Mục §4.4.1 và §4.5 |
| Tranh chấp phiên bản tóm tắt | Chỉ mục duy nhất một phần, thứ tự mốc kiểm chứng nguồn, khóa phả hệ và outbox cùng transaction | Mục §11.1 |
| Chi tiết schema sự kiện | Bổ sung `scopeSummary`, `aggregateVersion`, `auditReferenceId` và yêu cầu AsyncAPI | Mục §9 |
| Mơ hồ về thời hạn lưu trữ / Xóa dữ liệu | Ma trận thời hạn lưu trữ theo phân loại dữ liệu, lệnh giữ pháp lý và loại trừ khỏi việc huấn luyện | Mục §11.3 |
| Ngữ nghĩa kết thúc bất đồng bộ | Cấu trúc phản hồi polling `202` chuẩn mực, thời gian poll có giới hạn và quy tắc trạng thái kết thúc | Mục §4.4 |
| Fixture số liệu chuẩn | Định nghĩa chuẩn mực và mã fixture cho trùng lặp, hoàn tiền, múi giờ, AOV và phân tích đóng góp | Mục §6.4 và §12 |

Các cổng triển khai sau đây được duy trì tường minh có chủ đích:

1. Cụ thể hóa phần sửa đổi bổ sung của Identity thành schema máy đọc được của dịch vụ cha.
2. Công bố các schema công cụ Reporting / fixture số liệu cho dữ liệu AOV, theo giờ, chi nhánh và phân tích đóng góp.
3. Sinh các schema đầu ra an toàn, schema sự kiện và migration quản lý phiên bản tóm tắt từ giao ước đã hiệu chỉnh.
4. Triển khai cơ chế hạch toán ngân sách nhà cung cấp LLM và payload polling `202` công khai đúng theo đặc tả.

Đây là các tác vụ cụ thể hóa đã được quy định chi tiết trong tài liệu Giao ước hiệu chỉnh, không còn là các quyết định thiết kế mở.

**Quyết định:** Giao ước đã được phê duyệt để tiến hành Hoàn thiện Thiết kế (Design Finalization), chờ xác nhận từ người dùng. Quá trình triển khai bắt buộc phải hoàn thành các artifact máy đọc được và các sửa đổi bổ sung của dịch vụ cha trước khi viết mã nguồn ứng dụng.
