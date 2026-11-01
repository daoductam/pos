Started at: 2026/10/13 00:00:00
Finished at: 2026/10/13 00:00:00
Total time: 0 minutes
---

# Báo Cáo Spec Sentinel — Đánh Giá Tầm Nhìn (Vision) Audit & Chống Thất Thoát

**Tài liệu Spec:** [`docs/designs-vi/audit-loss-prevention/2026-10-13-vision.md`](file:///d:/vibe-code/pos/docs/designs-vi/audit-loss-prevention/2026-10-13-vision.md)  
**Ngày:** 2026-10-13  
**Trạng thái:** ⚠️ Cảnh báo (Tiếp tục một cách thận trọng)

## Phạm Vi Đánh Giá

- Giai đoạn Phase 0 đã bỏ qua — tái sử dụng phạm vi, kiến trúc hệ thống gốc và các quyết định đã được xác nhận trong phiên thiết kế hiện tại.
- Đối chiếu Vision với nền tảng POS Platform gốc, Identity Access, Order, Payment, Shift, Recommendation và Reporting.
- Repository hiện chưa có mã nguồn ứng dụng, schema sự kiện được sinh ra, migration hay mã nguồn triển khai của Audit; bản đánh giá này kiểm tra tính tương thích ranh giới thiết kế và mức độ sẵn sàng liên kết giữa các service, không phải review mã nguồn triển khai.
- Đã kiểm tra các quy tắc hiện có trong `.agent/rules/security.md` và `.agent/rules/code-quality.md`.

## Các Phát Hiện

### Cảnh Báo

| # | Hạng mục | Phát hiện | Rủi ro | Khuyến nghị |
|---|---|---|---|---|
| 1 | Đồng bộ hợp đồng hệ thống gốc | Nền tảng gốc đã có bảng `audit_events` và sự kiện `AuditRecorded.v1`, nhưng schema hiện mới có action/resource/actor/reason/before-after/correlation; chưa bao quát phiên bản sự kiện/sự kiện nguồn, thu ngân hiệu lực, thiết bị, ca làm việc, kết quả, chính sách làm sạch dữ liệu và bằng chứng toàn vẹn mà Vision đòi hỏi. | Các producer có thể phát payload không đủ thông tin để phục vụ điều tra hủy món/giảm giá/mở két hoặc không thể dedupe/phát lại được. | Requirements phải mở rộng phong bì/schema sự kiện hệ thống gốc theo hợp đồng bổ sung (additive contract); định nghĩa quyền sở hữu của producer, ID/phiên bản sự kiện nguồn và các trường bắt buộc theo từng hành động. |
| 2 | Mơ hồ về nguồn phát sinh hành động | Vision chưa chốt service nào chịu trách nhiệm phát sự kiện mở két tiền, cũng chưa phân biệt kích hoạt thủ công hay tự động; giảm giá/ghi đè chưa phân biệt giữa khuyến mãi tự động với giảm giá thủ công và ngưỡng phê duyệt. | Dấu vết kiểm toán có thể bị thiếu sự kiện hoặc gán sai trách nhiệm; đường cơ sở bất thường sử dụng các tín hiệu không đồng nhất. | Chốt danh mục phân loại hành động và producer/nguồn sự thật cho từng hành động P0; ghi rõ sự kiện chỉ phát sau khi commit, loại kích hoạt, phân bổ số tiền, lý do và kết quả. |
| 3 | Mô hình tính toàn vẹn (Integrity model) | Vision nêu cơ chế chống sửa đổi/mã băm/chuỗi băm nhưng để ngỏ giữa nhiều phương án kỹ thuật khác nhau. | Triển khai thực tế có thể chỉ áp dụng “append-only” ở tầng ứng dụng nhưng vẫn bất lực trong việc phát hiện can thiệp trực tiếp vào DB, backfill dữ liệu sai lệch hoặc phát lại sai phả hệ. | Requirements phải chọn cấp độ toàn vẹn cụ thể: kiểm soát append-only ở cấp DB + bản tóm tắt/checkpoint bất biến tối thiểu; nếu cần neo ngoài (external anchoring) thì ghi rõ chủ thể sở hữu, tần suất và hành vi khôi phục. |
| 4 | Danh mục phân quyền (Authorization) | Khả năng hiển thị của Owner/Manager đã rõ, nhưng Identity gốc chưa có tên quyền hạn cụ thể cho việc đọc audit, chuyển trạng thái vụ việc và các thao tác hạn chế như phát lại/lưu trữ pháp lý. | Chính sách API, phạm vi cache và các tham chiếu vụ việc có thể cấp quyền quá rộng hoặc không đóng quyền an toàn (fail-closed) khi quan hệ thành viên bị thu hồi. | Bổ sung danh mục quyền hạn, phạm vi tenant/chi nhánh, phân biệt đối tượng xác thực với nhân viên thao tác thực tế, và fixture kiểm thử phiên bản phân quyền/thu hồi quyền. |
| 5 | Mức độ sẵn sàng phát hiện bất thường | Đã chọn hướng tiếp cận dựa trên quy tắc + đường cơ sở thống kê, nhưng chưa có danh mục quy tắc, thời gian khởi động baseline/kích thước mẫu tối thiểu, ngưỡng kích hoạt, mức độ nghiêm trọng, cơ chế giảm nhiễu (suppression/cooldown) hoặc cách xử lý khi có nhân viên mới/chuyển chi nhánh. | Tỷ lệ dương tính giả cao hoặc bỏ sót hành vi tiêu cực; Quản lý khó giải thích lý do tại sao một vụ việc lại được tạo ra. | Requirements phải chốt định nghĩa quy tắc có phiên bản, cửa sổ bằng chứng, kích thước mẫu tối thiểu, chính sách khởi động nguội (cold-start), mức độ nghiêm trọng và tính lũy kế đơn định của phát hiện. |
| 6 | Vòng đời vụ việc và bằng chứng | Vision có trạng thái phát hiện và các thao tác xác nhận/giải quyết/bác bỏ nhưng chưa quy định ai được phép chuyển trạng thái, lý do bắt buộc, mở lại vụ việc, phân công xử lý hay snapshot bằng chứng khi sự kiện nguồn về muộn. | Vụ việc có thể bị đóng thiếu trách nhiệm, mất lịch sử điều tra hoặc thay đổi kết luận sau khi bằng chứng đã được sử dụng. | Thiết kế máy trạng thái dạng chỉ ghi thêm (append-only) cho các thao tác vụ việc; mọi chuyển đổi trạng thái phải sinh sự kiện kiểm toán, giữ snapshot bằng chứng/phiên bản và hỗ trợ điều chỉnh dữ kiện đến muộn minh bạch. |
| 7 | Ranh giới lưu trữ và quyền riêng tư | Đã chọn cam kết lưu trữ tối thiểu 7 năm và lưu trữ pháp lý, nhưng chưa có ma trận phân loại dữ liệu cho sự kiện, snapshot tác nhân, bằng chứng vụ việc, tham chiếu nguồn thô, lưu trữ dài hạn (archive) và xóa/tombstone. | Có thể lưu giữ thông tin định danh cá nhân (PII) quá mức cần thiết hoặc xóa nhầm các bằng chứng đang nằm trong diện lưu trữ pháp lý. | Chốt ma trận chính sách lưu trữ, che giấu dữ liệu ở cấp độ trường (field-level redaction), quyền ưu tiên của lưu trữ pháp lý, mã hóa kho lưu trữ, loại trừ xóa và kiểm toán cho mọi thao tác lưu trữ. |
| 8 | Ngữ nghĩa truy vấn và độ tươi mới | Giao diện mượn các trạng thái `FRESH/STALE/PARTIAL/UNAVAILABLE/NO_DATA` từ Reporting nhưng chưa có mục tiêu độ trễ (lag target), khoảng thời gian truy vấn tối đa, chính sách xuất file/phân trang/giới hạn tần suất hay hành vi khi thiếu một nguồn producer. | Người điều tra có thể ngộ nhận “không có sự kiện” nghĩa là “không có vi phạm xảy ra”, hoặc thực hiện truy vấn 7 năm gây quá tải hệ thống ngoài tầm kiểm soát. | Requirements phải định nghĩa SLO truy vấn, các bộ lọc có chặn khoảng, chính sách phân trang con trỏ/xuất dữ liệu, watermark độ hoàn tất nguồn và hiển thị banner/cảnh báo khi dữ liệu chưa đầy đủ. |

## Phân Tích Tác Động Chéo (Cross-Impact Analysis)

| Chiều tác động | Phát hiện | Mức rủi ro | Khuyến nghị |
|---|---|---|---|
| **Logic Dùng Chung (Shared Logic)** | Ranh giới dùng chung bao gồm phong bì sự kiện hệ thống gốc, `AuditRecorded.v1`, danh mục hành động, ngữ cảnh tác nhân/nhân viên thao tác thực tế, correlation ID và phạm vi phân quyền; các producer bao gồm Order, Payment, Shift, Identity/POS Device, Recommendation và Reporting. | 🔴 Cao | Sử dụng một schema/registry kiểm toán chuẩn tắc duy nhất; tuyệt đối không để mỗi service tự định nghĩa payload kiểm toán riêng lẻ. |
| **Dữ Liệu/Trạng Thái (Data/State)** | Sự kiện kiểm toán append-only, projection truy vấn, inbox/dedupe, checkpoint toàn vẹn, quy tắc bất thường, phát hiện/chuyển trạng thái vụ việc, chính sách lưu trữ/lưu trữ pháp lý đều là các trạng thái dữ liệu mới. | 🟡 Trung bình | Thiết kế các điều kiện lọc tenant, các dòng sự kiện bất biến, khóa nguồn duy nhất, thao tác vụ việc chỉ ghi thêm, phân vùng theo thời gian và comment đầy đủ trên từng trường trước khi viết migration. |
| **Hợp Đồng API (API Contracts)** | Sẽ bổ sung các API tìm kiếm/dòng thời gian kiểm toán, truy vấn phát hiện/vụ việc và các API chuyển trạng thái vụ việc/phát lại/lưu trữ pháp lý; Identity gốc hiện chưa có danh mục quyền tương ứng. | 🔴 Cao | Chốt chuẩn thiết kế API-first REST/OpenAPI và các hợp đồng phân quyền/mã lỗi/phân trang trước khi viết frontend hoặc worker. |
| **Tính Toàn Vẹn Luồng (Flow Integrity)** | Mutation tại nguồn → Transactional Outbox → EventBridge/SQS → Audit Inbox → Lưu trữ/Projection bất biến → Đánh giá bất thường → Vụ việc → Thao tác chuyển đổi của con người. | 🟡 Trung bình | Xây dựng các fixture tuần tự/nghiệm thu cho các trường hợp: commit tại nguồn, trùng lặp/sai thứ tự, DLQ/phát lại, sự kiện đến muộn, sự cố gián đoạn nguồn và giải quyết vụ việc. |
| **Mối Quan Tâm Xuyên Suốt (Cross-Cutting)** | Cách ly tenant, làm sạch PII/secret, lưu trữ 7 năm, phát hiện can thiệp dữ liệu, quy mô tải truy vấn, kiểm soát dương tính giả và lưu trữ pháp lý đều ảnh hưởng sâu rộng toàn hệ thống. | 🔴 Cao | Thiết kế chính sách bảo mật/riêng tư/lưu trữ đồng thời với hợp đồng; mọi thao tác truy cập kiểm toán/phát lại/lưu trữ pháp lý phải đóng quyền an toàn (fail-closed) và bản thân chúng phải được kiểm toán tự động. |

## Tuân Thủ Hướng Dẫn Kỹ Thuật (Instruction Compliance)

| Chiều đánh giá | Kết luận | Độ lệch (nếu có) | Khuyến nghị |
|---|---|---|---|
| **Quy ước chuẩn (Conventions)** | ✅ | Vision tuân thủ nghiêm ngặt nguyên tắc database-per-service, EventBridge/SQS, outbox/inbox, Cognito RBAC, correlation/tracing phân tán và không query chéo database. | — |
| **Kiến trúc (Architecture)** | ✅ | Audit giữ nguyên trạng thái nguồn tại Order/Payment/Shift/Identity, sử dụng Audit service làm chủ sở hữu bằng chứng và không tự ý thay đổi dữ liệu nghiệp vụ. | Giữ Audit là ranh giới đọc/bằng chứng; mọi thao tác thay đổi trên vụ việc phải là lệnh có kiểm toán (audited command) và được phân quyền chặt chẽ. |
| **Kiểm thử (Testing)** | ⚠️ | Vision đã nêu rõ các mục tiêu không mất dữ liệu, chống IDOR, quyền riêng tư, phát lại, khả năng giải thích bất thường và chính sách lưu trữ nhưng chưa có ma trận fixture/chuyển đổi trạng thái. | Chuyển đổi các mục tiêu này thành các yêu cầu kỹ thuật và bài kiểm thử hợp đồng cho tính đầy đủ của sự kiện, tính toàn vẹn, vòng đời vụ việc, đường cơ sở khởi động nguội và lưu trữ pháp lý. |

## Kết Luận Đánh Giá (Verdict)

Tài liệu Tầm nhìn (Vision) **có thể tiếp tục với các cảnh báo**. Không có rào cản ngăn chặn (blocker) nào về mặt phạm vi hay định hướng kiến trúc; toàn bộ các điểm còn lại đều là các hợp đồng/chính sách cần được cụ thể hóa trong tài liệu Yêu cầu (Requirements). Ưu tiên cao nhất là ban hành tu chính cho sự kiện Audit hệ thống gốc, xác định quyền sở hữu producer cho từng hành động, bổ sung danh mục quyền trong Identity, chốt cấp độ toàn vẹn và ngữ nghĩa của vụ việc bất thường.

## Quyết Định Cần Người Dùng Phê Duyệt

Bạn có muốn tiếp tục sang bước Requirements với trạng thái **⚠️ Cảnh báo (Tiếp tục một cách thận trọng)** hay muốn chỉnh sửa Vision trước?
