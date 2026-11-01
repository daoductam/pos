# Báo cáo Đánh giá Tầm nhìn: Catalog Service (Spec Sentinel Report)

- **Tài liệu gốc:** [`2026-10-12-vision.md`](./2026-10-12-vision.md)  
- **Ngày lập:** 2026-10-12  
- **Trạng thái:** ⚠️ Có cảnh báo (Được phép tiếp tục nhưng cần lưu ý xử lý)

---

## 1. Các phát hiện và Cảnh báo (Findings & Warnings)

| # | Hạng mục | Phát hiện | Rủi ro tiềm ẩn | Khuyến nghị giải pháp |
|---|---|---|---|---|
| 1 | Phạm vi phân quyền (Authorization) | Thực đơn P0 áp dụng chung cho toàn tenant, nhưng Vision lại ghi Quản lý có quyền chỉnh sửa “trong chi nhánh phụ trách”. Lệnh sửa thực đơn toàn tenant sẽ làm thay đổi cả thực đơn ở các chi nhánh khác. | Quản lý của một chi nhánh có thể vô tình làm xáo trộn bảng giá và món bán của chi nhánh khác. | Trong tài liệu Requirements, làm rõ: Manager có được phép chỉnh sửa thực đơn dùng chung của chuỗi không hay chỉ Owner mới có quyền. Cần phân định rạch ròi giữa phạm vi thẩm quyền của người gọi và phạm vi tác động của lệnh. |
| 2 | Mâu thuẫn với hợp đồng cha (Parent Contract Conflict) | Hợp đồng POS Platform cha từng cho phép Thu ngân (`CASHIER`) gọi `POST /items/{itemId}/availability`, nhưng Vision lại quy định Thu ngân chỉ có quyền đọc. | Các nhóm phát triển khác nhau có thể triển khai lệch quyền hạn của vai trò Thu ngân. | Thống nhất một chính sách quyền duy nhất trong Requirements và cập nhật điều chỉnh lại hợp đồng nền tảng cha. |
| 3 | Cấu trúc dữ liệu cha (Parent Data Shape) | Hợp đồng cha lưu danh mục dưới dạng một nhãn chuỗi văn bản thuần và dùng bảng phẳng `item_modifiers`; trong khi Vision chuẩn hóa thành thực thể Category và nhóm topping tái sử dụng (`modifier_groups`). | Các service tích hợp (Order / Client) có thể bị bỡ ngỡ do thay đổi định dạng dữ liệu trả về. | Giữ nguyên định danh món và đánh số phiên bản cho cấu trúc mới của category/group; giải thích rõ sự cải tiến này trong giai đoạn Contracts. |
| 4 | Ranh giới giá của Order (Order pricing boundary) | Vision yêu cầu giá nằm ở cấp độ biến thể (variant), nhưng cấu trúc `OrderLineInput.variantId` ở hợp đồng cha lại cho phép giá trị `null`. | Order Service có thể bị bối rối khi tính giá nếu client không gửi kèm mã biến thể. | Bắt buộc phải có `variantId` rõ ràng hoặc quy định rõ cơ chế tự động chọn biến thể mặc định (ví dụ: `STANDARD`) khi tạo đơn. |
| 5 | Ngữ nghĩa tính sẵn sàng bán (Sellability semantics) | Đặt ra tiêu chí “Món đang bán bắt buộc phải có ít nhất một biến thể đang bán”, nhưng việc tắt biến thể cuối cùng có thể vi phạm tiêu chí này. | Các bước chuyển trạng thái có thể để lại một món ăn đang ở trạng thái `ACTIVE` nhưng thực tế khách lại không thể mua được. | Quy định một bất biến nguyên tử (atomic invariant): chặn không cho tắt biến thể active cuối cùng, hoặc nếu tắt biến thể cuối cùng thì tự động chuyển cả món ăn sang trạng thái `INACTIVE`. |
| 6 | Quan hệ giữa Catalog và Kho (Inventory & Recommendation) | Trạng thái của Catalog là bật/tắt bán theo ý muốn chủ quán; còn Inventory kiểm soát tồn kho thực tế. Vision chưa nêu rõ mức độ ưu tiên khi món đang bật bán nhưng nguyên liệu kho đã hết. | Màn hình máy POS và AI gợi ý có thể hiển thị một món ăn mà nhà bếp thực tế không thể chế biến. | Quy định rõ: Khả năng bán thực tế (Effective Sellability) là sự kết hợp đồng thời giữa trạng thái mở bán của Catalog VÀ lượng nguyên liệu khả dụng của Inventory. |
| 7 | Số lượng và giá món phụ (Modifier quantity) | Đã nêu các trường `minSelections`/`maxSelections`, nhưng việc chọn trùng lặp một loại topping nhiều lần và độ chênh lệch giá âm (giảm giá) chưa được quy định rõ. | Máy POS và Order Service có thể tính toán ra tổng tiền khác nhau cho cùng một món. | Chốt chặt quy tắc chọn món phụ, giới hạn số lượng từng loại topping và quy tắc tính tiền phụ thu trong Requirements/Contracts. |
| 8 | Khả năng mở rộng P1 (P1 extensions) | Cấu trúc thành phần của combo và bảng giá riêng theo chi nhánh mới chỉ ở mức khái quát; dữ liệu tag phục vụ AI cold-start chưa có hợp đồng phiên bản. | Giai đoạn P1 có thể làm phát sinh các payload không tương thích giữa Order và Recommendation. | Giữ P1 hoàn toàn độc lập sau cờ tính năng; đặc tả chi tiết giao diện mở rộng trong Phase 4. |

---

## 2. Phân tích Tác động Chéo (Cross-Impact Analysis)

| Chiều phân tích | Đánh giá hiện trạng | Mức độ rủi ro | Khuyến nghị giải pháp |
|---|---|---|---|
| **Logic dùng chung (Shared Logic)** | Chưa có mã nguồn thực tế. Các hệ thống tiêu thụ tương lai gồm Angular POS, Order, Inventory, Recommendation và Reporting. | 🟡 Trung bình | Đặc tả các quy tắc kiểm tra hợp lệ của Catalog và hợp đồng ảnh chụp dữ liệu (snapshot) trước khi lập trình. |
| **Dữ liệu & Trạng thái (Data/State)** | Cơ sở dữ liệu Catalog DB chuẩn hóa các thực thể category, biến thể và nhóm topping thay thế cho cấu trúc phẳng cũ. | 🟡 Trung bình | Thiết lập ràng buộc khóa ngoại logic, khóa duy nhất phân vùng theo tenant, phiên bản khóa lạc quan và tính tương thích khi migration. |
| **Hợp đồng API (API Contracts)** | Hợp đồng nền tảng cha có 5 endpoint; module này bổ sung thêm các API cho category, variant, modifier group. | 🟡 Trung bình | Giữ vững quy ước đường dẫn `/api/v1/catalog` và tài liệu hóa chi tiết mọi điểm cập nhật so với hợp đồng cha. |
| **Tính toàn vẹn luồng (Flow Integrity)** | Chu trình: Đọc menu POS → Chọn món & Chụp ảnh giá → Giữ chỗ kho → Cập nhật AI gợi ý hoàn toàn liền mạch. | 🟡 Trung bình | Cần đặc tả rõ cơ chế xác thực giá hiện hành và thời điểm chụp ảnh dữ liệu đơn hàng. |
| **Thành phần dùng chung (Cross-Cutting)** | Cách ly tenant, kiểm tra quyền hạn, bộ nhớ đệm cache và phát tán sự kiện tác động đến mọi thao tác của Catalog. | 🔴 Cao | Bắt buộc áp dụng ngữ cảnh phân quyền từ Identity Access ở tầng Service và Repository; chỉ cache các dữ liệu đọc không nhạy cảm và xóa cache bằng sự kiện. |

---

## 3. Quyết định Nghiệm thu

Bản Tầm nhìn (Vision) của module Catalog Service hoàn toàn mạch lạc, phù hợp với kiến trúc POS Platform tổng thể. 8 cảnh báo nêu trên sẽ được tiếp thu và xử lý dứt điểm trong tài liệu Yêu cầu Hệ thống (Requirements) và Hợp đồng Giao tiếp (Contracts).
