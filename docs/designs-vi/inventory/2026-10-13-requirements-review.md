# Báo Cáo Đánh Giá Yêu Cầu Nghiệp Vụ (Spec Sentinel Report)

**Tài liệu đánh giá:** [docs/designs/inventory/2026-10-13-requirements.md](./2026-10-13-requirements.md)  
**Ngày:** 2026-10-13  
**Trạng thái:** ⚠️ Có cảnh báo — Tiến hành cẩn trọng (Warnings - Proceed with Caution)

Giai đoạn Phase 0 được bỏ qua — tái sử dụng ngữ cảnh phiên hiện tại. Chưa có mã nguồn ứng dụng nào cho Inventory Service; phạm vi quét tác động tập trung vào các tài liệu thiết kế của hệ thống cha (parent platform) và các service liên quan.

Tập hợp các blocker trước đó đã được kiểm tra lại sau khi các sửa đổi được phê duyệt. Ranh giới lệnh commit trực tiếp từ Saga, API/schema của Inventory trong tài liệu cha, độ chính xác của số lượng (`NUMERIC(18,3)`), tên chuẩn hóa của các sự kiện và trạng thái kết thúc đối soát của reservation hiện đã hoàn toàn đồng bộ.

---

## 1. Các Phát Hiện & Cảnh Báo (Warnings)

| # | Phân loại | Nội dung phát hiện | Rủi ro | Khuyến nghị giải quyết |
|---|---|---|---|---|
| 1 | Xử lý đồng thời khi điều chỉnh kiểm kê (Count adjustment concurrency) | Tài liệu Requirements yêu cầu hiển thị các biến động đồng thời phát sinh sau snapshot nhưng chưa đặc tả công thức chính xác để chuyển đổi giữa snapshot, số lượng kiểm đếm thực tế và các biến động sau snapshot thành số delta được phê duyệt. | Hai người quản lý có thể phê duyệt hai giá trị chênh lệch (delta) khác nhau cho cùng một phiên kiểm kê. | Quy định rõ một công thức duy nhất và áp dụng khóa phiên bản/khóa dòng phê duyệt trong Architecture/Database; từ chối yêu cầu phê duyệt bị lỗi thời bằng mã lỗi conflict định kiểu. |
| 2 | Các ngưỡng vận hành kỹ thuật (Operational thresholds) | Thời gian chờ khóa (lock wait), số lần retry có giới hạn, thời gian timeout của API và giới hạn retry outbox mới chỉ được giới hạn về mặt khái niệm, chưa được chốt bằng các con số cụ thể. | Các service liên quan có thể cài đặt hành vi timeout và retry không tương thích khi xảy ra tranh chấp tải cao. | Thiết lập các giá trị cấu hình mặc định ban đầu và ngân sách lỗi (failure budget) trong tài liệu Architecture, sau đó xuất các chỉ số metrics và kiểm thử dưới tải reserve đồng thời. |
| 3 | Chất lượng thuật toán dự báo (Forecast quality) | Baseline và phương án fallback của P2 đã được định nghĩa, nhưng các ngưỡng chất lượng mẫu tối thiểu, tính mùa vụ và quyền sở hữu cấu hình tồn an toàn chưa được đưa vào các trường của hợp đồng contract. | Khuyến nghị dự báo có thể tạo cảm giác rất chính xác dù dữ liệu đầu vào thực tế không đủ độ tin cậy. | Bổ sung các trường `quality`, `sampleCount`, `inputWindow`, nguồn cấu hình tồn an toàn và ngữ nghĩa mã `INSUFFICIENT_HISTORY` vào hợp đồng API dự báo. |
| 4 | Kiểm thử (Testing) | Tiêu chí nghiệm thu đã bao quát các luồng chính nhưng chưa liệt kê danh sách các fixture kiểm thử thực thi được cho: retry khi deadlock, xung đột đồng thời giữa commit và release, tái tạo projection, replay outbox và tấn công IDOR xuyên tenant. | Các lỗi hồi quy nghiêm trọng về xử lý đồng thời và cô lập dữ liệu có thể lọt qua bộ test chỉ kiểm tra luồng thông thường (happy path). | Bổ sung ma trận kiểm thử tích hợp và hợp đồng P0 trước khi lập kế hoạch triển khai mã nguồn. |

---

## 2. Phân Tích Tác Động Chéo (Cross-Impact Analysis)

| Chiều phân tích | Đánh giá hiện trạng | Mức độ rủi ro | Khuyến nghị |
|---|---|---|---|
| **Logic Dùng Chung (Shared Logic)** | Chưa có code ứng dụng nào cho Inventory Service. Các bên tiêu thụ dự kiến gồm: Step Functions / Order Service, luồng định tuyến sự kiện Payment, tham chiếu mã định danh Catalog IDs, Reporting, Audit và Notification. | 🟡 Trung bình | Cụ thể hóa các hợp đồng lệnh/sự kiện có phiên bản của Inventory và bộ kiểm thử phía consumer; không chia sẻ mã repository domain dùng chung. |
| **Dữ Liệu & Trạng Thái (Data/State)** | Schema của Inventory trong hệ thống cha đã hướng tới danh mục nguyên liệu master cấp tenant, số dư theo chi nhánh, các phiên bản công thức, snapshot giữ chỗ và các dòng sổ cái định kiểu. | 🟡 Trung bình | Giữ database của Inventory thuộc quyền sở hữu riêng của service; yêu cầu có migration script, comment mô tả cột và các ràng buộc kiểm tra (check constraints) từ Database Design. |
| **Hợp Đồng API (API Contracts)** | API hệ thống cha đã bao gồm lệnh commit trực tiếp từ Saga và payload giữ chỗ cập nhật; cần hoàn thiện các ví dụ OpenAPI cụ thể, schema mã lỗi và cơ chế xác thực workload identity. | 🟡 Trung bình | Hoàn tất đầy đủ tài liệu API Contracts và các fixture kiểm thử hợp đồng trước khi bước vào triển khai. |
| **Tính Toàn Vẹn Của Luồng (Flow Integrity)** | Chuỗi luồng nghiệp vụ: Giữ chỗ (`Reserve`) → Thông tin thanh toán (`Payment fact`) → Saga commit kho → Order hoàn tất đơn (`PAID`), cùng các nhánh hủy (`Release`), hết hạn (`Expiry`) và đối soát (`Reconciliation`) hoàn toàn mạch lạc. | 🟢 Thấp | Duy trì nghiêm ngặt nguyên tắc: Không lắng nghe trực tiếp sự kiện `PaymentPaid` vào Inventory Service trong cả test hợp đồng và tài liệu runbook. |
| **Các Vấn Đề Xuyên Suốt (Cross-Cutting)** | Phòng thủ chuyên sâu đa tenant/chi nhánh, định danh hệ thống (workload identity), nhật ký kiểm toán bất biến và chính sách không rò rỉ secret trong sự kiện đều đã có; các giới hạn vận hành cần được ấn định bằng số cụ thể. | 🟡 Trung bình | Đưa các ngưỡng cấu hình và chỉ số đo lường còn lại vào tài liệu Kiến trúc (Architecture) và Vận hành (Operations). |

---

## 3. Đánh Giá Tuân Thủ Hướng Dẫn (Instruction Compliance)

| Tiêu chuẩn | Kết luận | Sai lệch (nếu có) | Khuyến nghị |
|---|---|---|---|
| **Quy ước (Conventions)** | ✅ Đạt | Không có | Duy trì các hợp đồng cha đã sửa đổi làm nguồn tương thích và các tài liệu của Inventory làm nguồn đặc tả chi tiết cho service. |
| **Kiến trúc (Architecture)** | ✅ Đạt | Không có | Lệnh commit trực tiếp từ Saga và trạng thái dữ liệu thuộc sở hữu riêng của Inventory Service hoàn toàn ăn khớp với luồng của Order Service cha. |
| **Kiểm thử (Testing)** | ⚠️ Cần chú ý | Danh sách fixture kiểm thử thực thi được cho xử lý đồng thời và hợp đồng chưa đầy đủ. | Bổ sung ma trận kiểm thử P0 vào tài liệu Contracts/Architecture và yêu cầu nghiệm thu bắt buộc tại cổng kiểm soát triển khai. |

---

## 4. Cổng Quyết Định (Decision Gate)

Không còn bất kỳ rào cản nghiêm trọng (critical blocker) nào. Tiến hành phê duyệt tài liệu Yêu cầu nghiệp vụ (Requirements), đồng thời chuyển tiếp 4 nội dung cảnh báo trên vào tài liệu Kiến trúc (Architecture), Hợp đồng API (API Contracts), Thiết kế Cơ sở Dữ liệu (Database Design) và cổng kiểm thử trước khi triển khai thực tế.
