# Báo cáo đánh giá tầm nhìn — Tích hợp & Điều phối Demo (Spec Sentinel Report)

**Tài liệu tầm nhìn:** [`2026-10-13-vision.md`](./2026-10-13-vision.md)  
**Ngày:** 2026-10-13  
**Trạng thái:** ⚠️ Cảnh báo (Tiến hành với sự thận trọng / Proceed with Caution)

## Phạm vi đánh giá (Review Scope)

- Giai đoạn 0 đã được bỏ qua — Tái sử dụng ngữ cảnh của các Module 1–11 và các giao ước dịch vụ cha đã được thiết kế trước đó.
- Đối chiếu Tầm nhìn (Vision) với các giao ước của POS Platform, Order, Payment, Inventory, Loyalty, Recommendation, Reporting và Audit.
- Repository hiện chưa có mã nguồn ứng dụng; đợt đánh giá này thẩm định ranh giới tích hợp, luồng liên dịch vụ, quyền sở hữu, an toàn/quyền riêng tư và tính an toàn của demo ở cấp độ thiết kế.
- Đã kiểm tra tuân thủ các quy tắc `.agent/rules/security.md`, `.agent/rules/code-quality.md` và các chỉ dẫn chuyên môn `api-design`, `database-design`, `log-processing`.

## Các phát hiện (Findings)

### Danh sách cảnh báo (Warnings)

| # | Hạng mục | Phát hiện | Rủi ro | Khuyến nghị |
|---|---|---|---|---|
| 1 | Phạm vi / Ranh giới | Vision đặt phần tích hợp hóa đơn và trình điều phối demo chung trong Module 12; dù đã nêu rõ quyền sở hữu nhưng chưa phân định rạch ròi giữa runtime Integration trên Production với ranh giới điều khiển/triển khai Demo Control Plane trên Non-production. ([vision.md:14-40](./2026-10-13-vision.md#L14)) | Các cơ chế nạp hạt giống/đặt lại dữ liệu/tiêm lỗi của demo có nguy cơ bị triển khai hoặc cấp nhầm quyền trên môi trường Production. | Trong Requirements/Architecture cần phân tách rõ các thành phần logic, vai trò IAM, chính sách môi trường và ranh giới triển khai; Integration trên production tuyệt đối không để lộ các route tiêm lỗi. |
| 2 | Nguồn chân lý sự kiện | `OrderPaid.v1` được chọn làm điểm kích hoạt chuẩn mực, nhưng các giao ước dịch vụ cha có thể còn sai khác về trường dữ liệu, phiên bản nguồn, lượt checkout, chi nhánh/ngày kinh doanh và cam kết chuyển phát sự kiện. ([vision.md:18-25](./2026-10-13-vision.md#L18)) | Nguy cơ tạo trùng/thiếu hóa đơn hoặc gắn sai lượt đơn hàng khi sự kiện bị phát lại hoặc đến muộn. | Chốt phần sửa đổi bổ sung cho sự kiện `OrderPaid.v1`, khóa nguồn ổn định và các bộ fixture kiểm thử phía nhận trước khi bước vào Contracts. |
| 3 | Ranh giới pháp lý / Nhà cung cấp hóa đơn | Vision nêu rõ đây là tích hợp kỹ thuật/sandbox và loại trừ hóa đơn thuế production, nhưng chưa chỉ rõ cấu trúc payload hóa đơn tối thiểu, phân loại artifact hoặc cơ chế ngăn người dùng hiểu nhầm `InvoiceIssued` là hóa đơn có giá trị pháp lý. ([vision.md:18-25](./2026-10-13-vision.md#L18)) | Quá trình chuyển đổi nhà cung cấp hoặc báo cáo có thể vô tình sử dụng mã tham chiếu sandbox như một chứng từ kế toán pháp lý. | Thiết lập hồ sơ năng lực rõ ràng `SANDBOX_TECHNICAL`, trạng thái/metadata tường minh, cách đặt tên an toàn và rào chắn giao ước để năng lực pháp lý production là một phiên bản/quyết định tách biệt. |
| 4 | Sự đồng ý và quyền riêng tư liên hệ | Gửi Email/Zalo yêu cầu khách hàng chủ động chọn kênh và đồng ý, nhưng nguồn chân lý lưu giữ sự đồng ý, thời hạn lưu trữ, việc hiệu chỉnh/thu hồi và chính sách định danh của nhà cung cấp chưa được chốt dứt điểm. ([vision.md:53-62](./2026-10-13-vision.md#L53)) | Nguy cơ gửi nhầm người, giữ thông tin PII quá lâu hoặc không chứng minh được sự đồng ý của khách hàng khi thử lại/gửi lại thủ công. | Chốt bản chụp sự đồng ý (consent snapshot) ngay tại yêu cầu tạo hóa đơn, tham chiếu liên hệ đã che giấu, thời hạn lưu trữ/giữ pháp lý và hành vi thu hồi; tuyệt đối không tự lấy từ số điện thoại Loyalty. |
| 5 | Ngữ nghĩa chuyển phát | Luồng gửi bất đồng bộ đã có cơ chế thử lại/DLQ/hết hạn, nhưng chưa chốt khóa lũy quyền của nhà cung cấp, hành vi ít nhất một lần, chính sách thông báo trùng lặp, gửi lại thủ công và thứ tự ưu tiên kênh gửi. ([vision.md:64-78](./2026-10-13-vision.md#L64)) | Khách hàng có thể nhận trùng tin nhắn hoặc hệ thống không có quy trình xử lý khi việc chuyển phát bị treo/thất bại kết thúc. | Xây dựng máy trạng thái chuyển phát, giao ước lần thử theo từng kênh, cơ chế thử lại/backoff có giới hạn và lệnh gửi lại an toàn cho vận hành viên. |
| 6 | Tính tất định của kịch bản Demo | Demo đã có cơ chế nạp/đặt lại dữ liệu/điểm kiểm tra/tiêm lỗi/X-Ray, nhưng chưa có mô hình cách ly khi chạy đồng thời nhiều phiên demo, cơ chế dọn dẹp/TTL, cổng kiểm tra sức khỏe và thời gian chờ trạng thái kỳ vọng. ([vision.md:80-90](./2026-10-13-vision.md#L80)) | Không thể tái lập lại kịch bản hoặc phiên chạy này làm nhiễu dữ liệu/trace của phiên chạy khác. | Quản lý phiên bản kịch bản/dữ liệu hạt giống, namespace phiên chạy/tenant demo duy nhất, khóa thuê/TTL, đặt lại dữ liệu lũy quyền, timeout có giới hạn và quy trình dọn dẹp. |
| 7 | An toàn khi tiêm lỗi thử nghiệm | Lỗi `PAYMENT_UNAVAILABLE` chỉ giới hạn trên non-production, nhưng chưa có cơ chế phân quyền, danh mục kịch bản cho phép, công tắc ngắt khẩn cấp (kill switch), sự kiện kiểm toán và hành vi an toàn khi metadata môi trường không rõ ràng. ([vision.md:136-163](./2026-10-13-vision.md#L136)) | Nguy cơ gây ra sự cố gián đoạn thật hoặc bị lạm dụng để phá hỏng luồng checkout trong môi trường dùng chung. | Đóng ngắt an toàn nếu môi trường không phải demo; quyền chỉ cấp cho tải công việc, danh mục trắng kịch bản rõ ràng, khóa thuê có thời hạn, kiểm toán đầy đủ và tính năng vô hiệu hóa khẩn cấp. |
| 8 | Quy kết doanh thu cho Trợ lý AI | Vision đã nêu phả hệ `Hiển thị → Chấp nhận → Thêm món → Thanh toán`, nhưng chưa chốt định nghĩa số liệu “tăng doanh thu”, nhóm thuần tập/đối chứng, khoảng thời gian quy kết, loại tiền/múi giờ và cách xử lý khi sự kiện bị thiếu hoặc đến trễ. ([vision.md:92-100](./2026-10-13-vision.md#L92)) | Trợ lý AI có thể trả lời trôi chảy nhưng không thể tái lập hoặc nhầm lẫn giữa món được chấp nhận với doanh thu thực nhận. | Reporting/Recommendation bắt buộc công bố từ điển số liệu và giao ước hàm; AI chỉ đọc số liệu tổng hợp đã duyệt kèm metadata độ tươi mới/chất lượng. |
| 9 | Thời hạn lưu trữ và Payload nhà cung cấp | Vision đề cập việc lưu trữ/giữ pháp lý trong phần câu hỏi mở nhưng chưa xây dựng ma trận phân loại dữ liệu cho artifact hóa đơn, bản chụp liên hệ, lần thử chuyển phát, phản hồi nhà cung cấp và tham chiếu trace demo. ([vision.md:199-207](./2026-10-13-vision.md#L199)) | Nguy cơ giữ payload thô của nhà cung cấp/PII quá lâu hoặc xóa mất các tham chiếu bằng chứng kiểm tra. | Requirements cần chốt ma trận phân loại dữ liệu, che giấu dữ liệu nhạy cảm, lưu trữ artifact có mã hóa, thứ tự ưu tiên lưu trữ/giữ pháp lý và kiểm toán truy cập xuất dữ liệu. |

## Phân tích tác động chéo (Cross-Impact Analysis)

| Khía cạnh | Phát hiện | Mức độ rủi ro | Khuyến nghị |
|---|---|---|---|
| **Logic dùng chung** | Các sự kiện `OrderPaid.v1`, `SuggestionShown/Accepted`, quy kết dòng món trong Order, trạng thái VNPAY, giữ/xác nhận/giải phóng kho, tích điểm Loyalty và tăng trưởng Reporting là dữ kiện dùng chung của nhiều bên tiêu thụ. | 🔴 Cao (High) | Sử dụng kho sự kiện cha và các phần sửa đổi bổ sung; Integration tuyệt đối không tự tạo bản sao chân lý nghiệp vụ. |
| **Dữ liệu / Trạng thái** | Tác vụ hóa đơn, lần thử gọi nhà cung cấp, tác vụ chuyển phát, bản chụp sự đồng ý, phiên chạy/điểm kiểm tra demo và bảng inbox/outbox là các trạng thái mới; trạng thái domain nguồn vẫn ở các dịch vụ cha. | 🔴 Cao (High) | Cơ sở dữ liệu riêng, điều kiện lọc tenant bắt buộc, tham chiếu nguồn bất biến, cơ chế lũy quyền/CAS và ma trận lưu trữ rõ ràng. |
| **Giao ước API** | Các API trạng thái hóa đơn/chuyển phát/gửi lại, phiên chạy demo/nạp/đặt lại/tiêm lỗi/truy vết và kiểm tra sức khỏe sẽ tác động tới POS, Chủ quán/Quản lý, Vận hành và các adapter nhà cung cấp. | 🔴 Cao (High) | Thiết kế API-first theo OpenAPI + xác thực tải công việc nội bộ; tách biệt các route production/demo và kiểm tra phạm vi từng tài nguyên. |
| **Tính toàn vẹn luồng** | Sự kiện thanh toán → hóa đơn/chuyển phát; giỏ hàng → gợi ý → thêm món → thanh toán; sự cố Payment → bồi hoàn Saga → giải phóng kho; dữ kiện thanh toán → Reporting/AI. | 🔴 Cao (High) | Xây dựng các luồng tuần tự L1/L2 và các bộ fixture xử lý lỗi cho trùng lặp, dữ liệu trễ, timeout, DLQ, lỗi nhà cung cấp và tranh chấp khi đặt lại dữ liệu. |
| **Vấn đề xuyên suốt** | Sự đồng ý PII, khóa bí mật, dấu vết AWS X-Ray, giới hạn tần suất, sự cố nhà cung cấp, tiêm lỗi và tính chính xác của số liệu trải dài qua toàn bộ các module. | 🔴 Cao (High) | Xây dựng tập kiểm thử an ninh/quyền riêng tư, che giấu dữ liệu, kiểm toán, chính sách môi trường, metadata độ tươi mới và runbook nhà cung cấp trước khi viết code. |

## Mức độ tuân thủ chỉ dẫn (Instruction Compliance)

| Tiêu chí | Kết luận | Sai lệch (nếu có) | Khuyến nghị |
|---|---|---|---|
| **Quy ước (Conventions)** | ✅ | Tuân thủ EventBridge/SQS, outbox/inbox, UUID/correlation, database do dịch vụ sở hữu và Cognito/RBAC đồng nhất với thiết kế cha. | — |
| **Kiến trúc (Architecture)** | ✅ | Integration không sở hữu trạng thái domain, không query chéo DB, không thay thế Order Saga và không biến `SuggestionAccepted` thành chân lý doanh thu. | Duy trì quyền sở hữu của dịch vụ cha và tách biệt Demo Control Plane khi triển khai. |
| **Kiểm thử (Testing)** | ⚠️ | Vision đã có tiêu chí thành công và ý định xử lý lỗi nhưng chưa có các bộ fixture khả thi cho nhà cung cấp, sự đồng ý, cách ly demo và quy kết doanh thu. | Requirements phải chuyển đổi từng tiêu chí thành ma trận nghiệm thu và các fixture giao ước cụ thể. |

## Kết luận chung (Verdict)

Tài liệu Tầm nhìn (Vision) **có thể được thông qua với các cảnh báo**. Không có vấn đề chặn nào về quyền sở hữu hay ranh giới an toàn do đã xác lập rõ dịch vụ Integration riêng biệt, luồng nhà cung cấp bất đồng bộ, hóa đơn chỉ thử nghiệm sandbox và tiêm lỗi chỉ áp dụng trên non-production. Các cảnh báo bắt buộc phải được giải quyết dứt điểm trong tài liệu Yêu cầu (Requirements) và Kiến trúc (Architecture), ưu tiên: tách biệt Demo Control Plane, chuẩn hóa sự kiện `OrderPaid.v1`, quản lý sự đồng ý/lưu trữ, lũy quyền chuyển phát, phân quyền tiêm lỗi và giao ước số liệu tăng trưởng cho AI.

**Quyết định yêu cầu từ người dùng:** Bạn có đồng ý thông qua Tầm nhìn kèm theo các cảnh báo để tiến hành sang giai đoạn Yêu cầu nghiệp vụ (Requirements) không?
