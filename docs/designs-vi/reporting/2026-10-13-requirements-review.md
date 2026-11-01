Started at: 2026/10/13 00:00:00
Finished at: 2026/10/13 00:00:00
Total time: 0 minutes
---

# Báo Cáo Spec Sentinel (Đánh Giá Yêu Cầu Reporting)

**Tài liệu Spec:** [`docs/designs-vi/reporting/2026-10-13-requirements.md`](file:///d:/vibe-code/pos/docs/designs-vi/reporting/2026-10-13-requirements.md)  
**Ngày:** 2026-10-13  
**Trạng thái:** ⚠️ Cảnh báo (Tiếp tục một cách thận trọng)

## Ngữ Cảnh Đánh Giá

- Giai đoạn Phase 0 đã bỏ qua — Tầm nhìn (Vision) và Đánh giá Tầm nhìn (Vision Review) đã hoàn thành trong quy trình Module 10 hiện tại.
- Chưa có mã nguồn ứng dụng hay migration cho Reporting; kiểm tra tác động chéo (cross-impact) dựa trên các hợp đồng gốc của POS Platform, Order, Loyalty và Recommendation.
- Yêu cầu kỹ thuật hiện đã giải quyết các cảnh báo trong Vision ở cấp độ quy tắc nghiệp vụ. Các cảnh báo còn lại thuộc về cổng cụ thể hóa hợp đồng (contract materialization) và tu chính hợp đồng hệ thống gốc (parent amendment).

## Các Phát Hiện

### Cảnh Báo

| # | Hạng mục | Phát hiện | Rủi ro | Khuyến nghị |
|---|---|---|---|---|
| 1 | Event Order gốc | Yêu cầu nghiệp vụ bắt buộc gán định danh nhân viên checkout và bóc tách khuyến mãi bất biến trong `OrderPaid.v1`, nhưng hợp đồng Order gốc hiện tại chưa chuẩn hóa hiển thị các trường Reporting này mang tính quy chuẩn bắt buộc. | Báo cáo nhân viên và chương trình khuyến mãi không thể được tổng hợp nhất quán xuyên suốt các luồng producer/replay. | Áp dụng tu chính cho Order gốc và phát hành fixture JSON Schema phía producer/consumer trước khi triển khai projection. |
| 2 | Nguồn khuyến mãi | Yêu cầu cho phép snapshot khuyến mãi đã áp dụng hoặc phân rã chiết khấu đơn hàng có phiên bản, nhưng Reporting chỉ nên chấp nhận duy nhất một định dạng wire chuẩn hóa (canonical wire shape). | Hai producer có thể phân bổ chiết khấu khác nhau hoặc gây trùng lặp doanh thu tính toán cho khuyến mãi. | Quy định phân rã khuyến mãi áp dụng trong `OrderPaid.v1` làm nguồn chuẩn duy nhất; coi các event khuyến mãi riêng biệt chỉ là dữ liệu làm giàu (enrichment) tùy chọn. |
| 3 | Kiểm soát khuyến nghị | Ngữ cảnh ghi nhận order/cart và quy tắc 1-lần-tính-trên-mỗi-đơn đã được định nghĩa, nhưng schema event của Recommendation và trạng thái pending giữa tương tác đến đơn hoàn tất thanh toán vẫn cần fixture tạo sẵn. | Nhóm thử nghiệm (acceptance) và nhóm đối chứng (control) có thể bị lệch khi retry, nhiều gợi ý được chấp nhận hoặc `OrderPaid` đến muộn. | Bổ sung fixture kiểm thử end-to-end với interaction ID ổn định, thứ tự hiển thị/chấp nhận, liên kết correlation đơn hàng và hoàn tất thanh toán trễ. |
| 4 | Phiên bản hóa Projection | Chế độ xem điều chỉnh hiện tại (current corrected view) và `asOfWatermark` là bắt buộc, nhưng transaction hoán đổi/rebuild và thứ tự source-watermark chính xác được hoãn lại sang Kiến trúc/Hợp đồng. | Replay hoặc điều chỉnh đến muộn có thể phơi bày các phiên bản projection bị trộn lẫn hoặc tính trùng lặp các sự kiện lịch sử. | Định nghĩa cơ chế hoán đổi phiên bản projection, tính đơn điệu của watermark (watermark monotonicity), phả hệ điều chỉnh (correction lineage) và tính nguyên tử của current-view. |
| 5 | Ngữ nghĩa độ tươi mới (Freshness) | Dữ liệu `STALE` vẫn có thể đọc được khi độ trễ > 1 phút và leo thang cảnh báo sau 5 phút, nhưng quy tắc sẵn sàng/hoàn tất cho từng nguồn và chi nhánh chưa được chuẩn hóa dạng máy đọc được. | Dashboard có thể gắn nhãn một bảng tổng hợp bán phần là fresh hoặc stale một cách không nhất quán. | Định nghĩa các nguồn dữ liệu bắt buộc cho từng read model, công thức tính độ hoàn tất, ngưỡng chuyển đổi trạng thái và fixture chuẩn hóa độ tươi mới. |
| 6 | Hợp đồng API/cache | Danh mục API và quy tắc cache an toàn theo phạm vi (scope-safe) đã được đặc tả, nhưng tên tham số query chính xác, mã hóa con trỏ (cursor), hành vi ETag và schema phản hồi vẫn ở dạng văn xuôi. | Client Angular và triển khai server có thể bất đồng bộ, đặc biệt là sau khi quyền bị thu hồi. | Cụ thể hóa OpenAPI 3.1 với các ví dụ cho tất cả mã trạng thái, bộ lọc phạm vi, phân trang cursor và tái xác thực cache (revalidation). |
| 7 | Quy mô lưu trữ | Cần bảng tổng hợp sẵn và phạm vi truy vấn 12 tháng, nhưng giả định dung lượng và ngưỡng phân vùng (partition) chưa được định lượng. | Read model PostgreSQL có thể đáp ứng yêu cầu chức năng nhưng vi phạm SLA độ trễ khi tenant/chi nhánh tăng trưởng. | Xác định hồ sơ tải (load profile), ứng viên đánh chỉ mục/phân vùng, chính sách lưu trữ (retention) và tiêu chí đo lường leo thang lên Athena trong Kiến trúc. |
| 8 | Quyền sở hữu phân quyền | Quyền `report:read` và `reporting:operate` đã được đề xuất nhưng chưa có trong danh mục quyền hạn của Identity gốc. | Phân quyền API có thể bị cài đặt tùy tiện bằng kiểm tra role cục bộ thay vì dùng hợp đồng quyết định phân quyền chia sẻ. | Bổ sung các quyền hạn, ngữ nghĩa phạm vi và fixture kiểm thử từ chối không liệt kê vào Identity. |

## Phân Tích Tác Động Chéo (Cross-Impact Analysis)

| Chiều tác động | Phát hiện | Mức rủi ro | Khuyến nghị |
|---|---|---|---|
| **Logic Dùng Chung (Shared Logic)** | Consumer Reporting phụ thuộc vào OrderPaid, điều chỉnh/hoàn tiền Order, tương tác Recommendation, snapshot khuyến mãi và metadata chi nhánh/nhân viên; AI Assistant và Dashboard tiêu thụ các chỉ số kết quả. | 🔴 Cao | Phiên bản hóa mọi schema producer/consumer và duy trì duy nhất một từ điển chỉ số/phiên bản công thức tính toán. |
| **Dữ Liệu/Trạng Thái (Data/State)** | Bảy read model tổng hợp sẵn kèm trạng thái inbox/watermark/rebuild; việc điều chỉnh và phát lại (replay) phải mang tính lũy kế (idempotent). | 🔴 Cao | Thiết kế khóa nguồn (source key), chỉ mục kết hợp tenant/chi nhánh, cơ chế hoán đổi phiên bản projection và phả hệ rebuild trước khi viết migration. |
| **Hợp Đồng API (API Contracts)** | Chín endpoint báo cáo/freshness công khai dùng chung phong bì phạm vi/bộ lọc/lỗi/chất lượng dữ liệu. | 🟡 Trung bình | Tạo OpenAPI và kiểu dữ liệu TypeScript cho client Angular; kiểm thử thứ tự giữa cache và phân quyền (authorization). |
| **Tính Toàn Vẹn Luồng (Flow Integrity)** | Luồng Sự kiện → xác thực/dedupe → project → watermark → API/cache → dashboard đã được đặc tả; mở rộng event gốc và hoán đổi phiên bản hiện hành vẫn là các cổng cần duyệt. | 🟡 Trung bình | Bổ sung biểu đồ tuần tự cho các luồng bình thường, trùng lặp, đến trễ, bán phần và rebuild. |
| **Mối Quan Tâm Xuyên Suốt (Cross-Cutting)** | Bảo mật đa tenant, tối thiểu hóa PII, tải truy vấn 12 tháng, cảnh báo độ trễ tươi mới và kiểm toán phát lại đã được bao quát. | 🟡 Trung bình | Bổ sung kiểm thử IDOR, tải, quyền riêng tư, khả năng quan sát và replay DLQ vào Hợp đồng. |

## Tuân Thủ Hướng Dẫn Kỹ Thuật (Instruction Compliance)

| Chiều đánh giá | Kết luận | Độ lệch (nếu có) | Khuyến nghị |
|---|---|---|---|
| **Quy ước chuẩn (Conventions)** | ✅ | Đồng bộ chuẩn REST/OpenAPI 3.1, phong bì chung, UUID v7, UTC, tiền tệ số nguyên VND, phân trang cursor và projection hướng sự kiện. | — |
| **Kiến trúc (Architecture)** | ✅ | Reporting giữ vai trò chủ sở hữu chỉ số và không truy vấn trực tiếp DB của producer; ranh giới PostgreSQL MVP và Athena được quy định rõ. | Cụ thể hóa các tu chính từ service gốc và hợp đồng read model có phiên bản. |
| **Kiểm thử (Testing)** | ⚠️ | Tài liệu yêu cầu có ma trận rộng, nhưng các fixture event, dữ liệu vàng (golden metrics), kiểm thử migration/replay và ví dụ OpenAPI chưa được tạo thành file. | Tạo các fixture và bài test hợp đồng trước khi triển khai code. |

## Kết Luận Đánh Giá (Verdict)

Tài liệu yêu cầu (Requirements) **có thể được duyệt kèm các cảnh báo**. Không có rào cản ngăn chặn (blocker) về mặt logic nghiệp vụ hay ranh giới an toàn thông tin. Tài liệu Kiến trúc (Architecture) cần giải quyết triệt để các cổng liên quan đến định dạng event chuẩn tắc (canonical event shapes), trạng thái phiên bản/độ tươi mới của projection, schema API, quyền trong Identity và hồ sơ tải/lưu trữ trước khi Hợp đồng (Contracts) được phê duyệt.
