# Báo Cáo Đánh Giá Hợp Đồng Kỹ Thuật (Contracts Sentinel Report)

**Tài liệu đánh giá:** [`docs/designs/inventory/2026-10-13-contracts.md`](./2026-10-13-contracts.md)  
**Ngày:** 2026-10-13  
**Trạng thái:** ⚠️ Có cảnh báo — Tiến hành cẩn trọng (Warnings - Proceed with Caution)

---

## 1. Ngữ Cảnh Đánh Giá

- Tái sử dụng toàn bộ ngữ cảnh đã được phê duyệt từ Tầm nhìn (Vision), Yêu cầu nghiệp vụ (Requirements), Kiến trúc (Architecture) và Sơ đồ tuần tự (Sequence diagrams) của Inventory Service.
- Áp dụng cổng đánh giá chuyên sâu `spec-reviewer` đối với các hợp đồng API, sự kiện bất đồng bộ và cơ sở dữ liệu PostgreSQL.
- Đối chiếu chéo với các tài liệu hợp đồng của Nền tảng POS Platform, Order Service, Payment Service, Catalog Service và Identity Service.
- Repository hiện tại chứa toàn bộ tài liệu thiết kế nhưng chưa có mã nguồn ứng dụng, artifact OpenAPI/AsyncAPI sinh tự động hay các file migration Flyway thực thi được.
- Điểm mơ hồ về phương thức kiểm tra khả năng đáp ứng (Availability) đã được giải quyết dứt điểm: API nội bộ thống nhất dùng phương thức `POST` với request body `AvailabilityRequest`, và tài liệu hợp đồng của nền tảng POS Platform cha đã được cập nhật đồng bộ.

---

## 2. Các Phát Hiện & Cảnh Báo (Warnings)

| # | Phân loại | Nội dung phát hiện | Rủi ro | Khuyến nghị giải quyết |
|---|---|---|---|---|
| 1 | Mức độ bao phủ schema API | Endpoint mới về lịch sử giữ chỗ theo chi nhánh đã được định nghĩa với `ReservationCursorPage` và các quy tắc trường, nhưng cần bổ sung schema OpenAPI cụ thể và ví dụ mẫu cho việc bind con trỏ cursor, bộ lọc trạng thái và cơ chế che mờ chi tiết tồn kho nhạy cảm. | Các thư viện client được sinh tự động độc lập có thể bộc lộ các trường khác nhau hoặc cho phép tổ hợp lọc/sắp xếp không an toàn. | Sinh component OpenAPI và fixture kiểm thử hợp đồng từ Mục 7.3 và Mục 8 trước khi code; kiểm thử kỹ việc bind cursor/bộ lọc và phân quyền che mờ chi tiết tồn kho theo vai trò. |
| 2 | Bề mặt lệnh giai đoạn P1 | Hợp đồng PostgreSQL đã thiết kế trạng thái `receipts.status = 'CANCELLED'` và các loại biến động sổ cái `WASTE`/`RETURN`, trong khi bảng endpoint công khai hiện mới chỉ đặc tả các luồng tạo và đọc mà chưa có route riêng cho các lệnh hủy phiếu nhập, báo hao hụt, trả hàng. | Nhân viên vận hành ở giai đoạn P1 có thể cài đặt các thay đổi trạng thái bằng cách trực tiếp sửa dòng phiếu nhập hoặc dòng sổ cái, vi phạm nguyên tắc lịch sử chỉ ghi thêm (append-only). | Bổ sung các hợp đồng lệnh rõ ràng cho P1 trước khi triển khai các luồng này; mỗi lệnh phải ghi thêm một dòng sổ cái mới, yêu cầu có lý do/chứng từ/idempotency và tuyệt đối không sửa đổi bản ghi lịch sử cũ. |
| 3 | Cụ thể hóa hợp đồng dữ liệu DDL | Một số bảng như cảnh báo tồn thấp, dự báo và outbox đang được tóm tắt dạng cột thu gọn trong Mục 10, trong khi quy ước hợp đồng đòi hỏi mọi cột đều phải có kiểu dữ liệu rõ ràng và câu lệnh `COMMENT ON COLUMN`. | Các công cụ sinh DDL tự động hoặc script migration có thể bỏ sót ràng buộc nullability, check constraint, các trường thu hồi lease lock hoặc mô tả comment. | Mở rộng các bảng thu gọn này thành các file DDL/fixture schema hoàn chỉnh và kiểm tra tự động trong migration; chặn pipeline CI nếu thiếu cột hoặc thiếu comment. |
| 4 | Mức độ sẵn sàng của Fixture kiểm thử | Các bài kiểm thử hợp đồng đã được liệt kê chi tiết (mở rộng công thức, tranh chấp khóa, phê duyệt kiểm kê, mở lại cảnh báo, chất lượng dự báo và replay outbox), nhưng chưa có sẵn các fixture thực thi được hoặc schema sinh tự động trong repo. | Việc triển khai code thực tế có thể bám sát phần văn bản nhưng lại bị sai lệch về quy tắc làm tròn số thập phân, tính idempotent của trạng thái terminal hoặc cấu trúc payload sự kiện. | Đặt việc kiểm tra tự động OpenAPI/AsyncAPI, kiểm tra migration Flyway trên DB trống/DB có dữ liệu mẫu, và bộ fixture kiểm thử tranh chấp đồng thời làm điều kiện tiên quyết bắt buộc trước khi code. |

---

## 3. Phân Tích Tác Động Chéo (Cross-Impact Analysis)

| Chiều phân tích | Đánh giá hiện trạng | Mức độ rủi ro | Khuyến nghị |
|---|---|---|---|
| **Logic Dùng Chung (Shared Logic)** | Chưa có code gọi hàm nội bộ nào của Inventory Service. Các bên tiêu thụ trực tiếp là Step Functions Saga, Order Service, Reporting, Audit và Notification thông qua các API nội bộ và sự kiện đã tài liệu hóa. | 🟡 Trung bình | Sinh mã nguồn client từ một hợp đồng duy nhất và nghiêm cấm việc chia sẻ code database/repository dùng chung giữa các service. |
| **Dữ Liệu & Trạng Thái (Data/State)** | Inventory Service đưa vào các thực thể: snapshot công thức, số dư chi nhánh, các đơn giữ chỗ, sổ cái chỉ ghi thêm, điều chỉnh kiểm kê, cảnh báo tồn, dự báo, outbox và bảng idempotency. | 🔴 Cao | Duy trì nghiêm ngặt thứ tự khóa dòng: Khóa reservation trước, khóa các dòng số dư theo thứ tự ID tăng dần, bắt buộc có điều kiện tenant, ràng buộc kiểm tra cục bộ và kiểm tra checksum khi tái tạo projection. |
| **Hợp Đồng API (API Contracts)** | Ngữ nghĩa của Reserve, Commit, Release hoàn toàn ăn khớp với Order và Payment Service. Endpoint kiểm tra khả năng đáp ứng đã thống nhất dùng POST body và đồng bộ trên tài liệu POS Platform. | 🟡 Trung bình | Tái sinh các hợp đồng cha/client và bổ sung fixture kiểm thử hướng người tiêu thụ cho availability, ngữ cảnh commit và các kết quả đối soát ngoại lệ. |
| **Tính Toàn Vẹn Của Luồng (Flow Integrity)** | Chuỗi luồng: Giữ chỗ → Báo thanh toán → Saga commit kho → Order hoàn tất đơn, cùng các nhánh bồi hoàn giải phóng và đối soát ngoại lệ khi hết hạn đã được truy vết đầy đủ và mạch lạc. | 🟢 Thấp | Giữ cho Inventory Service hoàn toàn không tiêu thụ trực tiếp sự kiện `PaymentPaid`, đồng thời kiểm thử kỹ lưỡng các lệnh Saga bị gửi trùng lặp hoặc đến sai thứ tự. |
| **Các Vấn Đề Xuyên Suốt (Cross-Cutting)** | Phòng thủ chuyên sâu đa tenant/chi nhánh, định danh hệ thống (workload identity), tính idempotent, chính sách UTC và số lượng `NUMERIC(18,3)`, cơ chế outbox retry và DLQ đều đã được quy định tường minh. | 🟡 Trung bình | Bổ sung các kiểm tra tự động về an toàn bảo mật, timeout chờ khóa, che mờ dữ liệu nhạy cảm, phiên bản sự kiện và thu hồi lease worker vào CI và runbook vận hành. |

---

## 4. Đánh Giá Tuân Thủ Hướng Dẫn (Instruction Compliance)

| Tiêu chuẩn | Kết luận | Đánh giá sai lệch (nếu có) | Khuyến nghị |
|---|---|---|---|
| **Quy ước (Conventions)** | ✅ Đạt | Kiến trúc REST/OpenAPI 3.1, UUID v7, dấu thời gian UTC, cấu trúc envelope chung, danh sách allowlist phân trang và phong bì sự kiện đều tuân thủ chặt chẽ quy ước nền tảng. | Duy trì áp dụng trong giai đoạn triển khai. |
| **Kiến trúc (Architecture)** | ✅ Đạt | Quyền sở hữu đối với Reserve/Commit/Release, ranh giới transaction trong PostgreSQL, cơ chế Transactional Outbox và trách nhiệm đối soát hoàn toàn khớp với tài liệu Architecture đã duyệt. | Duy trì kiểm soát chặt chẽ ranh giới service. |
| **Kiểm thử (Testing)** | ⚠️ Cần chú ý | Danh mục fixture kiểm thử và các artifact hợp đồng sinh tự động đã được đặc tả chi tiết nhưng chưa được cụ thể hóa thành file thực thi trong repository. | Coi việc sinh schema tự động, script kiểm tra migration và bộ fixture tranh chấp đồng thời/replay là cổng nghiệm thu bắt buộc trước khi triển khai code. |

---

## 5. Kết Luận & Quyết Định (Verdict)

Tài liệu Hợp đồng kỹ thuật (Contracts) **được phê duyệt với các cảnh báo trên**. Không còn bất kỳ rào cản nghiêm trọng nào về quyền sở hữu dữ liệu, cô lập đa tenant, biên giới Saga, máy trạng thái reservation hay ngữ nghĩa transaction cơ sở dữ liệu.

Các cảnh báo còn lại đóng vai trò là điều kiện nghiệm thu bắt buộc để cụ thể hóa các artifact schema/test và hoàn thiện bề mặt lệnh P1 trước khi bắt tay vào viết mã nguồn nghiệp vụ P1.
