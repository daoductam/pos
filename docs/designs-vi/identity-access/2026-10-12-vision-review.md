# Báo cáo Đánh giá Tầm nhìn: Identity & Access (Spec Sentinel Report)

- **Tài liệu gốc:** [`2026-10-12-vision.md`](./2026-10-12-vision.md)  
- **Ngày lập:** 2026-10-12  
- **Trạng thái:** ⚠️ Có cảnh báo (Được phép tiếp tục nhưng cần lưu ý xử lý)

---

## 1. Ngữ cảnh Đánh giá

- Kế thừa trực tiếp các nguyên tắc và ranh giới kiến trúc từ tài liệu tổng thể POS Platform: [Kiến trúc POS Platform](../pos-platform/2026-10-12-architecture.md), [Yêu cầu POS Platform](../pos-platform/2026-10-12-requirements.md) và [Hợp đồng POS Platform](../pos-platform/2026-10-12-contracts.md).
- Toàn bộ các module là thiết kế mới từ đầu, chưa có mã nguồn sẵn có bị ảnh hưởng.

---

## 2. Các phát hiện và Cảnh báo (Findings & Warnings)

| # | Hạng mục | Phát hiện | Rủi ro tiềm ẩn | Khuyến nghị giải pháp |
|---|---|---|---|---|
| 1 | Chính sách phân quyền (Authorization policy) | Bản Vision nêu Owner/Manager được mời và quản lý nhân viên, nhưng chưa phân định rõ giới hạn của Manager: được mời role nào, có được gán lại quyền cho Manager khác không, có được đình chỉ/thu hồi quyền không. | Các service khác nhau hoặc client có thể vô tình tạo ra lỗ hổng leo thang đặc quyền (privilege escalation). | Định nghĩa ma trận RBAC chi tiết đến từng lệnh (command-level) trong Requirements. Khuyến nghị chuẩn: Manager chỉ được quản lý vai trò `CASHIER` và `KITCHEN` trong chi nhánh được phân công; chỉ duy nhất Owner mới có quyền quản trị vai trò Owner/Manager và các thiết lập trên toàn chuỗi. |
| 2 | Vòng đời thành viên (Lifecycle) | Lời mời Cognito và các trạng thái membership được nêu tên nhưng chưa có máy trạng thái (state machine) hoàn chỉnh và cơ chế đồng bộ reconciliation. | Người dùng có thể bị kẹt ở trạng thái `INVITED`, kích hoạt trên Cognito nhưng chưa kích hoạt trên Identity DB, hoặc bị mời trùng lặp. | Đặc tả rõ ràng các trạng thái membership, các bước chuyển hợp lệ, khóa idempotency, ràng buộc duy nhất (unique constraint), cơ chế thử lại và job đối soát dữ liệu. |
| 3 | Ngữ nghĩa thu hồi quyền (Revocation semantics) | Vision yêu cầu việc thu hồi quyền (`REVOKED`) phải có hiệu lực ngay request tiếp theo, nhưng chưa chỉ rõ endpoint nào phải truy vấn DB trực tiếp, endpoint nào được dùng cache. | Nhân viên bị sa thải vẫn có thể tiếp tục thao tác trái phép thông qua token JWT cũ còn hạn hoặc dữ liệu cache phân quyền bị trễ. | Bắt buộc kiểm tra phiên bản membership hoặc truy vấn DB cho các request nhạy cảm, hoặc thiết lập thời gian sống cache ngắn (TTL ngắn) kèm cơ chế xóa cache tức thì (immediate invalidation) khi có thay đổi quyền. Tuyệt đối không tin tưởng role claim đơn thuần từ Cognito. |
| 4 | Bảo mật mã PIN (PIN security) | Tính năng PIN ở P1 gắn với thiết bị/chi nhánh, nhưng quy trình đăng ký thiết bị, thiết lập lòng tin, đổi PIN, ngưỡng khóa tài khoản và xử lý khi mất máy chưa được quy định. | PIN bị đoán mò hoặc nhìn trộm có thể bị lợi dụng để giả mạo thu ngân trên máy POS dùng chung. | Thiết lập quy trình đăng ký định danh thiết bị an toàn, thuật toán băm mật mã Argon2id/bcrypt cho mã PIN, giới hạn tần suất nhập sai (rate limit / lockout) và ghi log kiểm toán. |
| 5 | Ranh giới API (API boundary) | Vision đề cập đến endpoint `/me` và tiêu đề `X-Active-Branch-Id`, nhưng chưa quy định các trường request/response, mã lỗi, phân trang và khóa lạc quan (optimistic locking). | Các consumer bên ngoài có thể hiểu sai hợp đồng API và việc cách ly tenant/branch có nguy cơ chỉ được thực hiện nửa vời trên giao diện UI. | Chuẩn hóa toàn bộ hợp đồng API công khai và nội bộ trong giai đoạn Contracts; tài liệu Requirements phải liệt kê đầy đủ các trường hợp kiểm thử hợp lệ và từ chối phân quyền. |
| 6 | Kiểm toán và bảo vệ PII (Audit/PII) | Yêu cầu phải có Audit Log, nhưng thời gian lưu trữ, đại diện danh tính người thao tác, quy tắc che giấu dữ liệu nhạy cảm (redaction) và cam kết gửi thông điệp chưa được làm rõ. | Nhật ký kiểm toán có thể làm lộ email, mật khẩu, mã PIN hoặc không đáp ứng đủ thông tin cho công tác điều tra an ninh. | Thiết kế bảng audit append-only, quy định trường dữ liệu bắt buộc che giấu (redaction rules), mã `correlationId` và cơ chế lưu trữ outbox đảm bảo không thất thoát sự kiện. |
| 7 | Tách biệt phạm vi P1 (Scope coupling) | Mã PIN thuộc P1 nằm chung service có nguy cơ làm phức tạp hóa vòng đời thiết bị và bảo mật, gây trì hoãn việc hoàn thành lát cắt P0. | Việc thiết kế mã PIN có thể làm chậm trễ tiến độ hoàn thành lõi phân quyền P0 hoặc vô tình biến mã PIN thành một hệ thống xác thực thứ hai song song với Cognito. | Đặt toàn bộ tính năng PIN sau cờ tính năng (feature flag) của phase P1; nghiệm thu P0 hoàn toàn độc lập với PIN. Tuyệt đối không cấp phát JWT token thay thế từ mã PIN. |

---

## 3. Phân tích Tác động Chéo (Cross-Impact Analysis)

| Chiều phân tích | Đánh giá hiện trạng | Mức độ rủi ro | Khuyến nghị giải pháp |
|---|---|---|---|
| **Logic dùng chung (Shared Logic)** | Ngữ cảnh phân quyền do Identity Access cung cấp sẽ được toàn bộ hệ thống (Gateway, Order, Catalog, Payment, Inventory, Loyalty, Shift, Reporting, Audit) sử dụng. | 🟡 Trung bình | Thiết lập một hợp đồng ngữ cảnh phân quyền có đánh số phiên bản duy nhất; cấm các service khác tự ý đọc trực tiếp vào database của Identity DB. |
| **Dữ liệu & Trạng thái (Data/State)** | Cơ sở dữ liệu Identity DB mới sẽ sở hữu các bảng: tenant, chi nhánh, thành viên, ánh xạ Cognito subject, vòng đời lời mời, thông tin thiết bị PIN và bảng outbox/audit. | 🟡 Trung bình | Thiết lập ràng buộc kiểm tra khóa ngoại logic, chỉ mục duy nhất cho membership, khóa lạc quan (`version`), migration bằng Flyway và Transactional Outbox. |
| **Hợp đồng API (API Contracts)** | Bổ sung các API cho vòng đời nhân sự, cập nhật chi nhánh, phân giải ngữ cảnh phân quyền và mã PIN. | 🟡 Trung bình | Giữ vững quy ước REST `/api/v1`, tiêu đề `Idempotency-Key` cho các lệnh ghi, mã `X-Correlation-Id` và các mã trạng thái HTTP chuẩn mực `401/403/404/409/422/429`. |
| **Tính toàn vẹn luồng (Flow Integrity)** | Bao gồm các luồng chính: Mời nhân viên qua Cognito → Kích hoạt → Chuyển trạng thái; Đăng nhập → Truy vấn `/me` → Chọn chi nhánh; Thay đổi quyền → Phát sự kiện kiểm toán; Nhập PIN → Đổi phiên thu ngân. | 🟡 Trung bình | Tài liệu Requirements bắt buộc phải bao quát các tình huống: dịch vụ phụ thuộc bị lỗi, gửi lại lời mời, thay đổi quyền đồng thời, phiên làm việc bị thu hồi và chọn sai chi nhánh. |
| **Thành phần dùng chung (Cross-Cutting)** | Các dữ liệu đầu vào đặc biệt nhạy cảm gồm: JWT token, email, số điện thoại, thay đổi vai trò và các lần thử mã PIN. | 🔴 Cao | Thực hiện kiểm tra quyền sở hữu tại Controller, Service và Repository; cấm tuyệt đối việc ghi log token, mã PIN thô hoặc bí mật; có bài kiểm thử tiêu cực chống IDOR. |

---

## 4. Quyết định Nghiệm thu

Bản Tầm nhìn (Vision) của module Identity & Access hoàn toàn mạch lạc, nhất quán và tuân thủ chặt chẽ định hướng kiến trúc tổng thể của POS Platform. Không có mâu thuẫn hay rủi ro chặn đứng (blocker). Các cảnh báo nêu trên sẽ được tiếp thu và cụ thể hóa thành các yêu cầu bắt buộc trong tài liệu Yêu cầu Hệ thống (Requirements).
