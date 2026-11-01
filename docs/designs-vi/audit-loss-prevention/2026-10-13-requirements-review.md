Started at: 2026/10/13 00:00:00
Finished at: 2026/10/13 00:00:00
Total time: 0 minutes
---

# Báo Cáo Spec Sentinel — Đánh Giá Yêu Cầu Kỹ Thuật (Requirements) Audit & Chống Thất Thoát

**Tài liệu Spec:** [`docs/designs-vi/audit-loss-prevention/2026-10-13-requirements.md`](file:///d:/vibe-code/pos/docs/designs-vi/audit-loss-prevention/2026-10-13-requirements.md)  
**Ngày:** 2026-10-13  
**Trạng thái:** ⚠️ Cảnh báo (Tiếp tục một cách thận trọng)

## Phạm Vi Đánh Giá

- Tái sử dụng Tầm nhìn (Vision), Đánh giá Tầm nhìn (Vision review) và các quyết định Yêu cầu (Requirements) đã được xác nhận trong phiên hiện tại.
- Đối chiếu Requirements với hợp đồng nền tảng POS Platform gốc, Identity Access, Order, Payment, Shift, Recommendation và Reporting.
- Repository hiện chưa có mã nguồn ứng dụng, schema sự kiện/API được sinh ra hay migration; bản đánh giá này kiểm tra hợp đồng yêu cầu/thiết kế, không phải review mã nguồn triển khai.
- Đã kiểm tra các quy tắc trong `.agent/rules/security.md` và `.agent/rules/code-quality.md`.

## Các Phát Hiện

### Cảnh Báo

| # | Hạng mục | Phát hiện | Rủi ro | Khuyến nghị |
|---|---|---|---|---|
| 1 | Phân loại sự kiện (Event taxonomy) | Requirements vừa dùng các sự kiện nghiệp vụ chuyên biệt (`OrderLineCancelled`, `DiscountApplied`, `CashDrawerOpened`) vừa giữ `AuditRecorded.v1` của hệ thống gốc, nhưng chưa chốt nguồn sự thật cho dữ kiện kiểm toán đã chuẩn hóa và quy tắc ngăn chặn sinh hai bản ghi bằng chứng. | Một thao tác thay đổi ở nguồn có thể bị ghi nhận hai lần hoặc producer/consumer hiểu khác nhau về sự kiện nào là chuẩn tắc (canonical). | Trước khi bước vào Kiến trúc, cần chọn mô hình chuẩn tắc: sự kiện nghiệp vụ nguồn được Audit chuẩn hóa, hoặc producer phát một sự kiện `AuditRecorded.v1` duy nhất; bắt buộc ghi `sourceEventId/sourceVersion` và dedupe key trong schema. |
| 2 | Ranh giới transaction mở két | Yêu cầu `AUD-FR-001` đòi hỏi cùng transaction/outbox với trạng thái/kết quả mở két, nhưng thao tác mở két vật lý trên phần cứng có thể diễn ra ngoài transaction cơ sở dữ liệu. | Có thể ghi nhận kiểm toán “đã mở két” khi phần cứng thực tế bị kẹt/lỗi, hoặc mất bằng chứng khi lệnh phần mềm thành công nhưng callback kết quả phần cứng bị trễ. | Tách biệt ngữ nghĩa giữa lệnh yêu cầu (command/request) và kết quả phần cứng (hardware result); chốt khóa thao tác/kết quả ổn định, thứ tự ưu tiên của kết quả, cơ chế retry và đối soát cho POS Device / Shift. |
| 3 | Tiền tệ và phân bổ (Money & Allocation) | Yêu cầu về giảm giá nhắc đến số tiền trước/sau, phân bổ và quy ước VND của nền tảng nhưng chưa quy định rõ `currency`, số nguyên minor units, quy tắc làm tròn và bất biến tổng phân bổ trong schema của Audit. | Việc phát hiện bất thường giảm giá có thể bị sai lệch do đơn vị tiền tệ hoặc phân bổ không cân bằng; khó phát lại đối với dữ liệu đa tiền tệ. | Bổ sung schema Tiền tệ / Phân bổ: `amountMinor`, `currency=VND` trong MVP, kiểm tra ràng buộc không âm, phiên bản/quy tắc làm tròn và bất biến `allocated + unallocated = discount`. |
| 4 | Chuẩn hóa tính toàn vẹn (Integrity) | Đã chọn bản tóm tắt băm theo lô (batch digest) nhưng chưa chốt định dạng tuần tự hóa chuẩn (canonical serialization), thứ tự sắp xếp, thuật toán, ranh giới lô, lưu trữ/neo giữ checkpoint và quy trình khôi phục khi phát hiện lệch hash. | Hai worker có thể sinh ra hai mã digest khác nhau hoặc không thể chứng minh được bản ghi sự kiện cụ thể nào bị sửa đổi hay biến mất. | Chốt cấu trúc JSON/thứ tự trường chuẩn tắc, thuật toán băm, bộ tuple sự kiện có sắp xếp, ranh giới lô theo thời gian/số lượng, con trỏ digest và giao thức xác minh/cảnh báo/rebuild. |
| 5 | Chính sách phát hiện bất thường | Các nhóm quy tắc ban đầu đã rõ nhưng ngưỡng kích hoạt, công thức đường cơ sở, kích thước mẫu tối thiểu, thời gian khởi động, múi giờ/cửa sổ thời gian, cơ chế giảm nhiễu/hạ nhiệt và ánh xạ mức độ nghiêm trọng vẫn ở dạng “theo chính sách quy định”. | Hai bên triển khai khác nhau có thể tạo ra các phát hiện khác nhau; việc kiểm thử và giải thích tỷ lệ dương tính/âm tính giả gặp khó khăn. | Xây dựng schema chính sách quy tắc có phiên bản kèm các ví dụ/fixture đơn định cho: nhân viên mới, chi nhánh mới, ca làm việc mới, baseline bằng 0, đột biến, sự kiện trùng lặp và sự kiện đến muộn. |
| 6 | Độ bao phủ của nguồn hệ thống gốc | P2 cần dữ kiện từ Order/Payment/Shift/POS Device và phân bổ khuyến mãi, nhưng hợp đồng hệ thống gốc hiện mới thống nhất một phần các trường/route của Audit; các fixture nguồn cho két tiền, giảm giá và bất thường chưa đầy đủ. | Worker có nguy cơ phụ thuộc vào các trường không được producer phát hành hoặc sử dụng nhầm dữ kiện Payment làm nguồn sự thật nghiệp vụ. | Bổ sung các bản tu chính mở rộng và fixture giữa producer/consumer cho từng nguồn; bảo toàn quyền sở hữu của Payment/Order/Shift và không truy vấn DB xuyên service. |
| 7 | Phả hệ vụ việc và bằng chứng | Chuyển trạng thái vụ việc yêu cầu dạng chỉ ghi thêm và bản chỉnh sửa nhưng chưa chốt một máy trạng thái hoàn chỉnh, phiên bản lạc quan (optimistic version), phân công/quyền sở hữu, tính bất biến của snapshot bằng chứng và phản hồi khi có xung đột chuyển trạng thái đồng thời. | Manager/Owner có thể ghi đè lẫn nhau hoặc một vụ việc đã giải quyết bị thay đổi mà không bảo toàn phả hệ điều tra. | Chốt đồ thị chuyển trạng thái, phiên bản kiểm tra điều kiện `If-Match`/CAS, sự kiện chuyển trạng thái, lý do bắt buộc, snapshot/phiên bản bằng chứng và hành vi trả về lỗi `409` conflict. |
| 8 | Phân loại dữ liệu lưu trữ và xuất file | Đã chọn thời hạn lưu trữ 7 năm, lưu trữ pháp lý và xuất file bất đồng bộ, nhưng chưa có ma trận phân loại dữ liệu cho: sự kiện, digest, snapshot tác nhân, phát hiện/vụ việc, file xuất, kho lưu trữ dài hạn (archive) và tombstone. | Có thể lưu giữ thông tin nhạy cảm quá lâu, xóa nhầm bằng chứng đang trong diện lưu trữ pháp lý hoặc để file xuất tồn tại vượt quá thời hạn cho phép. | Xây dựng ma trận lưu trữ / lưu trữ pháp lý, vòng đời file xuất được mã hóa, loại trừ xóa, tính lũy kế của tác vụ dọn dẹp và kiểm toán truy cập trước khi migration. |
| 9 | Mức độ sẵn sàng máy đọc được của API | Danh mục endpoint đã có đường dẫn/quyền/mục đích nhưng chưa có đầy đủ schema request/response/error, allowlist bộ lọc, định dạng cursor, quy tắc `ETag`/cache và ngữ nghĩa tải file xuất bất đồng bộ. | Frontend và backend có thể triển khai không khớp nhau hoặc để rò rỉ phạm vi dữ liệu qua xem chi tiết / xuất file / cursor. | Cụ thể hóa OpenAPI 3.1 với phong bì chuẩn, các bộ lọc có chặn, phân quyền trước khi đọc cache, ràng buộc cursor, quy trình bất đồng bộ `202`/polling và các fixture lỗi an toàn. |

## Phân Tích Tác Động Chéo (Cross-Impact Analysis)

| Chiều tác động | Phát hiện | Mức rủi ro | Khuyến nghị |
|---|---|---|---|
| **Logic Dùng Chung (Shared Logic)** | Ranh giới dùng chung bao gồm `AuditRecorded.v1`, các dữ kiện nguồn chuyên biệt, tác nhân/thu ngân thao tác, ngữ cảnh thiết bị/ca, phạm vi Identity và phả hệ nguồn; các consumer gồm Audit, worker Loss, UI Reporting/Audit và Ops. | 🔴 Cao | Ban hành một registry sự kiện chuẩn tắc duy nhất kèm fixture tương thích giữa producer/consumer; không nhân bản việc ánh xạ kiểm toán bên trong từng consumer riêng lẻ. |
| **Dữ Liệu/Trạng Thái (Data/State)** | Sự kiện append-only, projection truy vấn, inbox, checkpoint digest, chính sách quy tắc, baseline, phát hiện, chuyển trạng thái vụ việc, xuất file / lưu trữ pháp lý và tác vụ dọn dẹp lưu trữ là các trạng thái dữ liệu mới. | 🟡 Trung bình | Thực thi các điều kiện lọc tenant, các dòng bất biến, khóa nguồn duy nhất, chuyển trạng thái theo cơ chế CAS, phân vùng/lưu trữ theo thời gian và comment đầy đủ trên cột trong migration. |
| **Hợp Đồng API (API Contracts)** | Dòng thời gian/chi tiết audit, truy vấn phát hiện / thao tác vụ việc, xuất file, rebuild, kiểm tra sức khỏe và tác vụ toàn vẹn bổ sung thêm các API được bảo vệ; danh mục quyền hạn trong Identity phụ thuộc vào hệ thống gốc. | 🔴 Cao | Sinh đặc tả OpenAPI sau khi hoàn tất tu chính quyền/phạm vi; kiểm thử IDOR, phạm vi của cursor/export và thao tác thay đổi vụ việc đồng thời. |
| **Tính Toàn Vẹn Luồng (Flow Integrity)** | Commit nguồn / outbox → hàng đợi → inbox/dedupe → bằng chứng append-only → projection/digest → đánh giá quy tắc → phát hiện/vụ việc → hành động của con người. | 🟡 Trung bình | Xây dựng các fixture L1/L2 cho các ca: trễ kết quả phần cứng, điều chỉnh muộn, lệch digest, khoảng trống nguồn, phát lại DLQ và bản chỉnh sửa vụ việc. |
| **Mối Quan Tâm Xuyên Suốt (Cross-Cutting)** | Lưu trữ 7 năm, làm sạch PII/secret, phát hiện can thiệp, cô lập chi nhánh, tải truy vấn và xử lý dương tính giả ảnh hưởng đến toàn bộ các đường dẫn producer và vận hành. | 🔴 Cao | Làm cho các chính sách làm sạch dữ liệu / lưu trữ / tính toàn vẹn có thể thực thi được dạng máy đọc; kiểm toán mọi thao tác xuất file / phát lại / lưu trữ pháp lý và đóng quyền an toàn (fail-closed) khi có sự mơ hồ về phạm vi. |

## Tuân Thủ Hướng Dẫn Kỹ Thuật (Instruction Compliance)

| Chiều đánh giá | Kết luận | Độ lệch (nếu có) | Khuyến nghị |
|---|---|---|---|
| **Quy ước chuẩn (Conventions)** | ✅ | REST/OpenAPI 3.1, AsyncAPI/JSON Schema, UUID v7, UTC, EventBridge/SQS, outbox/inbox, cursor và mẫu mã lỗi chung đều đồng bộ hoàn hảo với quy ước nền tảng. | — |
| **Kiến trúc (Architecture)** | ✅ | Audit sở hữu bằng chứng/vụ việc, trong khi Order/Payment/Shift/Identity/POS Device giữ nguyên trạng thái nguồn; không truy vấn chéo database và không có cơ chế tự động khóa. | Giữ các hợp đồng producer nguồn mang tính bổ sung (additive) và bảo toàn tính tự chủ của từng service. |
| **Kiểm thử (Testing)** | ⚠️ | Ma trận kiểm thử rất rộng nhưng chuẩn hóa digest, ngữ nghĩa thao tác phần cứng, fixture quy tắc, schema API đầy đủ và vòng đời lưu trữ/xuất file chưa có dạng thực thi. | Cụ thể hóa các fixture hợp đồng, phát lại, bảo mật, riêng tư, toàn vẹn, bất thường và lưu trữ trước khi bắt tay vào triển khai mã nguồn. |

## Kết Luận Đánh Giá (Verdict)

Tài liệu Yêu cầu (Requirements) **có thể được duyệt kèm các cảnh báo**. Không có rào cản ngăn chặn (blocker) nào về mặt phạm vi hay ranh giới an toàn; toàn bộ các cảnh báo cần được giải quyết dứt điểm trong Kiến trúc/Hợp đồng trước khi viết code. Các ưu tiên hàng đầu là mô hình sự kiện chuẩn tắc, ngữ nghĩa thao tác két tiền, schema Tiền tệ/phân bổ, thuật toán digest, chính sách bất thường có phiên bản, máy trạng thái vụ việc và ma trận lưu trữ/xuất file.

## Quyết Định Cần Người Dùng Phê Duyệt

Bạn có đồng ý phê duyệt Requirements kèm các cảnh báo để chuyển sang bước **Kiến trúc (Architecture)** không?
