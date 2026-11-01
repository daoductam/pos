Started at: 2026/10/12 16:30:09 +07
Finished at: 2026/10/12 16:30:09 +07
Total time: 0 minutes
---

# Yêu cầu Kỹ thuật: Order Service (Dịch vụ Đơn hàng)

## Nguồn tài liệu và Mức độ ưu tiên

- [Tầm nhìn Order (Vision)](./2026-10-12-vision.md) và [Báo cáo Đánh giá Spec Sentinel](./2026-10-12-vision-review.md).
- [Yêu cầu Nền tảng POS](../pos-platform/2026-10-12-requirements.md), [Hợp đồng Nền tảng POS](../pos-platform/2026-10-12-contracts.md), và [Kết quả Thiết kế Nền tảng POS](../pos-platform/2026-10-12-pos-platform_result.md).
- [Kết quả Thiết kế Identity & Access](../identity-access/2026-10-12-identity-access_result.md).
- [Kết quả Thiết kế Catalog](../catalog/2026-10-12-catalog_result.md).
- **P0:** Bắt buộc cho lát cắt dọc (vertical slice) đầu tiên của Order và nghiệm thu tự động. **P1/P2:** Được thiết kế dưới dạng các phần mở rộng tương thích, không bắt buộc cho bản phát hành P0.

Order Service là nguồn chân lý duy nhất (source of truth) cho vòng đời đơn hàng, trạng thái dòng món (line state), các ảnh chụp snapshot Catalog bất biến và trạng thái của các lượt thử thanh toán (checkout attempts). Service này không sở hữu dữ liệu của Catalog, Inventory, Payment, Loyalty hay Identity.

## Tác nhân và Phân quyền

| Tác nhân | P0 Đọc | P0 Ghi | Phạm vi (Scope) |
|---|---|---|---|
| Owner (Chủ sở hữu) | Có | Có | Quyền truy cập toàn tenant đối với các đơn hàng của chi nhánh được chọn; ngữ cảnh chi nhánh luôn phải tường minh cho các thao tác đơn hàng. |
| Manager (Quản lý) | Có | Có | Tư cách thành viên ACTIVE tại chi nhánh mục tiêu; có thể chỉnh sửa bản nháp và hủy dòng món trong chi nhánh đó. |
| Cashier (Thu ngân) | Có | Có | Tư cách thành viên ACTIVE tại chi nhánh mục tiêu; có thể tạo/sửa bản nháp và thực hiện checkout, nhưng không được phép hủy dòng món. |
| Kitchen (Bếp/Bar) | Không thao tác Order | Không | Đọc qua KDS thuộc P1; không có quy trình Order trực tiếp trong P0. |
| Order workload/Saga | Các callback trạng thái nội bộ | Các lệnh nội bộ | mTLS / workload identity kết hợp các bước kiểm tra quyền sở hữu tenant/chi nhánh/đơn hàng tường minh. |
| Payment callback | Không truy cập Order công khai | Sự kiện/callback nội bộ | Chỉ thông qua định danh Payment Service / hợp đồng sự kiện; nhà cung cấp cổng thanh toán không bao giờ gọi trực tiếp vào Order. |
| Customer (Khách hàng) | Không truy cập P0 trực tiếp | Không truy cập P0 trực tiếp | Tự gọi món qua QR tại bàn thuộc P2 và đòi hỏi một hợp đồng chống gian lận riêng biệt. |

Các quy tắc phân quyền:

- Identity bắt buộc phải phân giải quyền hạn hiện tại cho mọi yêu cầu được bảo vệ. Các claim vai trò/chi nhánh trong JWT và các ID do client tự gửi không phải là nguồn thẩm quyền.
- Mọi câu truy vấn và biến đổi (mutation) đều có phạm vi theo `tenant_id` và, đối với các thao tác chi nhánh, theo `branch_id`. Các ID trên đường dẫn URL (path), trong body, các dòng xử lý hàng loạt và các tham chiếu Saga nội bộ đều được kiểm tra đối chiếu với cùng phạm vi này.
- Chủ sở hữu (Owner) bắt buộc phải chọn/chỉ định một chi nhánh mục tiêu cho các thao tác đơn hàng theo chi nhánh; quyền hạn toàn tenant của Owner không biến một đơn hàng thành đơn không có chi nhánh.
- Quản lý (Manager) và Thu ngân (Cashier) bắt buộc phải có tư cách thành viên ACTIVE tại chi nhánh mục tiêu tại thời điểm yêu cầu. Một tư cách thành viên bị thu hồi sẽ từ chối ngay yêu cầu tiếp theo ngay cả khi JWT cũ vẫn còn hạn.
- Thu ngân có thể thêm/sửa dòng món và bắt đầu checkout khi đơn ở trạng thái `DRAFT`; Thu ngân không thể hủy dòng món hoặc ép buộc một quy trình bù trừ thất bại.
- Chủ sở hữu / Quản lý có thể hủy một dòng món đang active chỉ khi đơn hàng ở trạng thái `DRAFT`; hành động này bắt buộc phải nhập lý do không rỗng và phát sinh sự kiện audit log.
- Các đơn hàng ở trạng thái `CHECKOUT_PENDING`, `PAID` và `CANCELLED` không thể chỉnh sửa thông qua các lệnh sửa bản nháp công khai.
- Các lệnh gọi workload nội bộ bắt buộc phải bao gồm `tenantId`, `branchId`, `orderId`, `correlationId` và `sagaId`; Order luôn tự xác thực toàn bộ ID tại chỗ và không bao giờ tin cậy đơn lẻ một tenantId do bên gọi gửi lên.

## Yêu cầu Chức năng

| Mã | Mức độ ưu tiên | Tác nhân | Hành động | Phản hồi yêu cầu |
|---|---|---|---|---|
| ORD-UC-01 | P0 | Cashier/Manager/Owner | Tạo đơn hàng cho dịch vụ tại quầy, mang đi hoặc tại bàn | Tạo một đơn `DRAFT` với phạm vi tenant/chi nhánh, chế độ phục vụ và `tableReference` tùy chọn. |
| ORD-UC-02 | P0 | Cashier/Manager/Owner | Thêm một dòng món từ Catalog | Gọi bộ phân giải Catalog, lưu snapshot tên/giá/biến thể/modifier hiện tại và trả về phiên bản mới của đơn. |
| ORD-UC-03 | P0 | Cashier/Manager/Owner | Sửa số lượng, biến thể, modifier hoặc ghi chú món | Phân giải lại với Catalog và thay thế nguyên tử snapshot dòng món; trả về `409` nếu phiên bản đã cũ. |
| ORD-UC-04 | P0 | Cashier/Manager/Owner | Sửa ghi chú đơn / metadata phục vụ trong `DRAFT` | Xác thực độ dài / làm sạch dữ liệu và bảo toàn các bất biến của chi nhánh/chế độ phục vụ. |
| ORD-UC-05 | P0 | Cashier/Manager/Owner | Đọc một đơn hàng hoặc danh sách đơn của chi nhánh | Chỉ trả về dữ liệu tenant/chi nhánh được ủy quyền kèm theo các snapshot bất biến và trạng thái vòng đời hiện tại. |
| ORD-UC-06 | P0 | Owner/Manager | Hủy một dòng món đang hoạt động | Yêu cầu trạng thái `DRAFT`, có lý do và header `If-Match`; ghi nhận dữ liệu audit tác nhân / trước / sau. |
| ORD-UC-07 | P0 | Order | Hủy dòng món active cuối cùng của đơn | Chuyển đổi trạng thái Order sang `CANCELLED` kết thúc một cách nguyên tử; không giữ lại bản nháp rỗng để tái sử dụng. |
| ORD-UC-08 | P0 | Cashier/Manager/Owner | Làm mới snapshot Catalog bị cũ | Trả về bản xem trước (preview) các trường/giá thay đổi; chỉ khi xác nhận rõ ràng mới thay thế snapshot và tăng version Order. |
| ORD-UC-09 | P0 | Cashier/Manager/Owner | Bắt đầu thanh toán (Checkout) | Xác thực các dòng active, tổng tiền, phiên bản Catalog hiện tại và phạm vi chi nhánh; khóa đơn và khởi động 1 phiên Saga attempt. |
| ORD-UC-10 | P0 | Saga | Giữ chỗ (reserve) nguyên vật liệu | Gọi Inventory với khóa `sagaId`/attempt bất biến; tồn kho không đủ sẽ kết thúc checkout thất bại mà không chuyển sang `PAID`. |
| ORD-UC-11 | P0 | Saga/Payment | Thanh toán tiền mặt | Xác thực số tiền nhận, tính tiền thừa số nguyên, xác nhận thanh toán và hoàn tất Order đúng một lần. |
| ORD-UC-12 | P0 | Saga/Payment | Thanh toán qua VNPAY QR | Khởi tạo thanh toán, chờ webhook IPN đã xác minh, giữ chỗ tồn kho tối đa 15 phút và trả về trạng thái pending. |
| ORD-UC-13 | P0 | Saga | Hoàn tất checkout thành công | Commit/tiêu thụ giữ chỗ tồn kho, hoàn tất Order sang `PAID`, ghi sự kiện outbox và không bao giờ phát trùng `OrderPaid`. |
| ORD-UC-14 | P0 | Saga | Bù trừ (compensate) khi checkout thất bại | Đóng/hủy yêu cầu thanh toán khi phù hợp, giải phóng giữ chỗ tồn kho đúng một lần và chuyển Order sang `CHECKOUT_FAILED`. |
| ORD-UC-15 | P0 | Cashier/Manager/Owner | Thử lại checkout thất bại | Cho phép tạo lượt attempt mới chỉ sau khi quy trình bù trừ trước đó đã kết thúc an toàn; tạo khóa attempt mới mà không làm thay đổi lịch sử cũ. |
| ORD-UC-16 | P0 | Đối soát thanh toán | Xử lý VNPAY thành công muộn sau khi đã giải phóng | Không phục hồi lại đơn hàng; ghi nhận một ngoại lệ `late_payment_reconciliation` để Payment/nhân viên vận hành xử lý. |
| ORD-UC-17 | P0 | Order | Xử lý sự cố khi bù trừ thất bại | Giữ nguyên `CHECKOUT_FAILED`, ghi nhận `compensationStatus=RETRYING|FAILED`, chặn checkout mới và hiển thị trạng thái vận hành. |
| ORD-UC-18 | P0 | Order | Xuất bản sự kiện vòng đời và sự kiện đơn đã thanh toán | Commit nguyên tử trạng thái và bản ghi outbox; consumer có thể khử trùng lặp theo event ID. |
| ORD-UC-19 | P1 | Manager/Owner | Giữ đơn (Hold) và khôi phục đơn hàng | Chính sách lưu giữ bản nháp kiểm soát bằng feature flag; không làm thay đổi ngữ nghĩa định danh/snapshot của P0. |
| ORD-UC-20 | P1 | Manager/Owner | Quản lý sơ đồ bàn và trạng thái bàn có khách | Bổ sung tài nguyên bàn thuộc sở hữu và quy tắc xung đột; trường `tableReference` vẫn giữ tính tương thích. |
| ORD-UC-21 | P1 | Manager/Owner | Tách hoặc gộp hóa đơn | Tạo các liên kết audit con/gộp tường minh và bảo toàn lịch sử thanh toán/đơn hàng bất biến. |
| ORD-UC-22 | P1 | Kitchen | Nhận món ăn trong đơn tại màn hình KDS | Xuất bản sự kiện món ăn bền bỉ và chuyển phát cập nhật WebSocket thời gian thực với cơ chế tự kết nối lại / phát lại. |
| ORD-UC-23 | P2 | Customer | Tự gọi món từ mã QR tại bàn | Sử dụng ranh giới phân quyền khách hàng/phiên riêng biệt kèm kiểm tra chống giả mạo; không tin tưởng đơn lẻ vào tham số QR. |

## Quy tắc Nghiệp vụ

### Vòng đời Đơn hàng và Dòng món

1. Trạng thái `OrderStatus` bao gồm: `DRAFT`, `CHECKOUT_PENDING`, `PAID`, `CHECKOUT_FAILED` hoặc `CANCELLED`.
2. Các bước chuyển đổi trạng thái hợp lệ của Order là:
   - `DRAFT -> CHECKOUT_PENDING` chỉ sau khi đã phân giải Catalog hiện tại thành công và xác thực tổng tiền hợp lệ.
   - `DRAFT -> CANCELLED` khi dòng món đang hoạt động cuối cùng bị hủy.
   - `CHECKOUT_PENDING -> PAID` chỉ sau khi Saga xác nhận thanh toán thành công và hoàn tất bước commit tồn kho bắt buộc trong Inventory.
   - `CHECKOUT_PENDING -> CHECKOUT_FAILED` sau khi thanh toán/giữ chỗ thất bại hoặc timeout và đã ghi nhận kết quả bù trừ.
   - `CHECKOUT_FAILED -> CHECKOUT_PENDING` chỉ thông qua một lượt thử checkout mới sau khi quy trình bù trừ trước đó đã kết thúc an toàn.
   - `PAID` và `CANCELLED` là các trạng thái kết thúc (terminal) trong P0.
3. Đơn hàng ở trạng thái `CHECKOUT_PENDING` bị khóa hoàn toàn đối với các thao tác sửa dòng/đơn công khai và thao tác hủy dòng món. Các callback từ Saga chỉ được phép cập nhật các trường checkout được ủy quyền.
4. Một đơn hàng bắt buộc phải có ít nhất một dòng món đang active để bắt đầu checkout. Các dòng món đã hủy được giữ lại để lưu vết lịch sử và bị loại khỏi việc tính tổng tiền.
5. Mỗi dòng món có trạng thái `ACTIVE` hoặc `CANCELLED`. Thao tác hủy dòng món lưu lại tác nhân, vai trò, lý do, timestamp, tham chiếu snapshot trước đó và phiên bản mới của Order.
6. Đơn phục vụ tại bàn (`TABLE`) lưu chuỗi tự do `tableReference` trong P0. Đây là metadata phục vụ hiển thị; P0 không thực thi tính duy nhất hay kiểm soát trạng thái có khách của bàn.
7. Đơn hàng chỉ hiển thị trong phạm vi tenant và chi nhánh được ủy quyền của chúng. Chi nhánh không thể bị thay đổi sau khi đơn hàng đã được tạo.

### Snapshot Catalog và Chỉnh sửa Bản nháp

8. Thao tác thêm/sửa dòng món gọi bộ phân giải nội bộ của Catalog với ID món, ID biến thể cụ thể và các ID modifier được chọn. Các client mới bắt buộc phải gửi `variantId`; trường hợp null kiểu cũ chỉ giải quyết được khi có chính xác một biến thể đang active.
9. Một dòng món lưu trữ các trường snapshot bất biến tại thời điểm checkout: ID món Catalog, ID biến thể, tên món/biến thể, các ID/tên/phụ thu modifier, đơn giá cơ sở, đơn giá modifier, đơn giá tổng, số lượng và phiên bản Catalog.
10. Chỉnh sửa số lượng hoặc ghi chú mà không thay đổi lựa chọn Catalog có thể tái sử dụng snapshot dòng hiện tại, nhưng vẫn bắt buộc phải có header `If-Match` và tính toán lại các khoản tổng một cách nguyên tử.
11. Chỉnh sửa món/biến thể/modifier sẽ phân giải lại với Catalog. Các lựa chọn thuộc tenant khác, không hoạt động, mơ hồ hoặc không hợp lệ sẽ thất bại mà không làm thay đổi dòng hiện có.
12. Quá trình Checkout phân giải lại toàn bộ các dòng đang active và so sánh lựa chọn/giá/phiên bản Catalog hiệu lực với snapshot trong bản nháp. Nếu có sai lệch sẽ trả về lỗi có định kiểu `catalog_changed`; không snapshot nào được tự động thay thế âm thầm trong quá trình checkout.
13. Lệnh tính lại giá / làm mới (reprice/refresh) trả về sự khác biệt (diff). Client bắt buộc phải xác nhận rõ ràng trước khi thay thế các snapshot bị ảnh hưởng. Việc xác nhận và thay thế diễn ra trong cùng một giao dịch.
14. Hai nhân viên được ủy quyền có thể cùng chỉnh sửa một bản nháp, nhưng yêu cầu mang header `If-Match` lỗi thời sẽ thất bại với mã lỗi `409`; nghiêm cấm cơ chế ghi đè người ghi sau cùng (last-write-wins).

### Tiền tệ và Các khoản Tổng (Money and Totals)

15. Order sử dụng tiền tệ số nguyên `BIGINT` tính theo VND; nghiêm cấm việc dùng số thực dấu phẩy động (float).
16. Tạm tính (subtotal) trong P0 là tổng của `unitPriceMinor * quantity` trên tất cả các dòng active và các phụ thu modifier đã được Catalog phân giải.
17. Chiết khấu / khuyến mãi trong P0 không được áp dụng bởi Order. Trường `discountMinor` bằng 0 trừ khi được cung cấp một hợp đồng định giá được phê duyệt riêng.
18. Order sở hữu tổng tiền thanh toán cuối cùng và lưu trữ `subtotalMinor`, `vatMinor`, `discountMinor`, `grandTotalMinor`, `currency` và `pricingPolicyVersion` trong snapshot checkout. Giá trị mặc định của `vatMinor` trong P0 là 0 cho đến khi hợp đồng chính sách VAT của tenant được kích hoạt; việc kích hoạt VAT khác 0 đòi hỏi các bài kiểm thử làm tròn số nguyên có tính tiền định.
19. `grandTotalMinor = subtotalMinor + vatMinor - discountMinor` và không thể là số âm. Payment Service nhận chính xác tổng tiền checkout bất biến này.
20. Thanh toán tiền mặt yêu cầu `cashReceivedMinor >= grandTotalMinor`; tiền thừa trả lại là `changeMinor = cashReceivedMinor - grandTotalMinor`.
21. Việc thay đổi giá/tổng tiền sau khi tạo bản nháp không bao giờ làm biến đổi một lượt checkout attempt trước đó; mỗi lượt thử lại đều có một snapshot tổng tiền bất biến mới.

### Checkout Saga và Bù trừ (Compensation)

22. Order tạo một bản ghi `checkout_attempt` với các thông tin duy nhất: `attemptId`, `sagaId`, idempotency key, tổng tiền snapshot, correlation ID và trạng thái trước khi khởi động AWS Step Functions.
23. AWS Step Functions là bộ điều phối duy nhất trong P0. Nó gọi các API service có tính bất biến; nó tuyệt đối không ghi trực tiếp vào database của Order, Inventory hay Payment.
24. Luồng xử lý thành công (happy path) trong P0 là: xác thực bản nháp → đặt trạng thái `CHECKOUT_PENDING` → giữ chỗ tồn kho trong Inventory → khởi tạo/thu tiền trong Payment → đối với VNPAY chờ webhook IPN đã xác minh → commit giữ chỗ tồn kho trong Inventory → hoàn tất Order sang `PAID` → xuất bản sự kiện `OrderPaid`.
25. Thanh toán tiền mặt có thể hoàn tất đồng bộ sau khi giữ chỗ tồn kho; VNPAY trả về trạng thái `PENDING` cho đến khi nhận được IPN hợp lệ hoặc bị timeout.
26. Thời gian hết hạn giữ chỗ của VNPAY là 15 phút theo chính sách bản demo. Timeout sẽ đóng/hủy lượt thanh toán khi được hỗ trợ, giải phóng giữ chỗ Inventory và chuyển Order sang `CHECKOUT_FAILED`.
27. Thanh toán thất bại trước khi thành công sẽ giải phóng giữ chỗ Inventory đúng một lần và chuyển Order sang `CHECKOUT_FAILED` sau khi lệnh bù trừ được chấp nhận / kết thúc an toàn.
28. Nếu bước giải phóng/commit tồn kho của Inventory hoặc một bước bù trừ khác gặp sự cố, Order vẫn giữ nguyên ở trạng thái `CHECKOUT_FAILED`, `compensationStatus` là `RETRYING` hoặc `FAILED`, lượt checkout mới bị chặn và quy trình retry/DLQ/cảnh báo của Saga tiếp tục hoạt động.
29. Nếu Payment báo thành công nhưng bước commit tồn kho không thể hoàn thành, Saga không được tự ý đánh dấu `PAID` hoặc giải phóng một giữ chỗ chưa rõ ràng. Nó sẽ thử lại commit; sau khi thất bại hoàn toàn sẽ ghi nhận `paymentReconciliationRequired=true` và chuyển ngoại lệ sang quy trình đối soát thủ công của Payment/vận hành.
30. Webhook IPN thành công từ VNPAY đến muộn sau khi giữ chỗ tồn kho đã bị giải phóng sẽ không làm sống lại đơn hàng và không tự động bắt đầu giữ chỗ mới. Payment Service sở hữu ngoại lệ `late_payment_reconciliation`.
31. Các callback bị trùng lặp, việc thử lại cùng một tác vụ Saga hoặc phát lại một sự kiện không thể tạo ra việc giữ chỗ, thanh toán, hoàn tất đơn hoặc sự kiện `OrderPaid` thứ hai.
32. Checkout thất bại vẫn giữ lại bản nháp/snapshot và lượt attempt thất bại lịch sử. Việc thử lại tạo ra một lượt attempt mới chỉ sau khi quy trình bù trừ trước đó đã an toàn.

### Audit và Sự kiện (Audit and Events)

33. Thao tác hủy dòng món phát ra một sự kiện audit outbox trong cùng giao dịch chứa tenant, branch, ID đơn/dòng, actor/user ID, vai trò, lý do, correlation ID, tóm tắt snapshot dòng trước đó và trạng thái/phiên bản kết quả. Loại trừ hoàn toàn JWT thô, bí mật và PII khách hàng không cần thiết.
34. Việc phân phát sự kiện audit là bất đồng bộ qua retry/DLQ; sự cố tạm thời của consumer Audit không được phép làm tách rời thao tác biến đổi Order khỏi bản ghi outbox của nó.
35. Các sự kiện `OrderCreated`, `OrderCancelled`, `OrderCheckoutFailed` và sự kiện chuẩn mực `OrderPaid` mang event ID/phiên bản, tenant/branch, order ID, thời điểm xảy ra, correlation ID và dữ liệu snapshot bất biến liên quan.
36. `OrderPaid` là sự thật đơn hàng đã thanh toán chuẩn mực duy nhất (canonical paid-order fact) cho Loyalty và Reporting. Sự kiện `PaymentPaid` không đủ điều kiện để tính điểm hoặc thống kê doanh thu.
37. Các consumer sự kiện thực hiện khử trùng lặp theo event ID và xác thực phạm vi tenant. Độ trễ nhất quán cuối cùng của bảng chiếu là có thể quan sát được và không bao giờ được dùng để cấp quyền cho một biến đổi Order hay checkout.

## Trạng thái Lượt thử Thanh toán (Checkout Attempt State)

Các trạng thái `CheckoutAttemptStatus`:

| Trạng thái | Ý nghĩa | Kết thúc (Terminal) |
|---|---|---:|
| `STARTED` | Order đã chấp nhận lệnh checkout và tạo attempt | Không |
| `RESERVATION_PENDING` | Saga đã yêu cầu giữ chỗ tồn kho trong Inventory | Không |
| `RESERVED` | Giữ chỗ tồn kho trong Inventory thành công | Không |
| `PAYMENT_PENDING` | Thanh toán đã khởi tạo; đang chờ xác nhận tiền mặt hoặc VNPAY IPN | Không |
| `PAYMENT_SUCCEEDED` | Payment báo thành công; còn bước commit tồn kho / hoàn tất đơn | Không |
| `FINALIZING` | Saga đang commit giữ chỗ tồn kho / hoàn tất Order | Không |
| `COMPLETED` | Order đã chuyển sang `PAID` và `OrderPaid` đã lưu bền vững vào outbox | Có |
| `COMPENSATING` | Thanh toán hoặc giữ chỗ lỗi, đang thực hiện quy trình bù trừ | Không |
| `FAILED` | Order chuyển sang `CHECKOUT_FAILED`; bù trừ đã an toàn / kết thúc | Có |
| `COMPENSATION_FAILED` | Order chuyển sang `CHECKOUT_FAILED`; cần can thiệp vận hành thủ công | Có (đối với việc khóa thử lại) |
| `LATE_PAYMENT_RECONCILIATION` | Nhà cung cấp báo thành công sau khi Order đã bù trừ/giải phóng | Có (đối với attempt của Order) |

Các cờ `compensationStatus` và `paymentReconciliationRequired` hiển thị rõ ràng trên chi tiết đơn/attempt cho Quản lý / Chủ sở hữu được ủy quyền và nhân viên vận hành nội bộ.

## Yêu cầu Tích hợp

| Ranh giới | Yêu cầu |
|---|---|
| Identity | Phân quyền trực tuyến hiện tại cho mọi yêu cầu công khai; từ chối truy cập (fail closed) khi quyết định không khả dụng. |
| Catalog | Phân giải hàng loạt nội bộ trả về tên món/biến thể/modifier, giá, phiên bản nhất quán và mã từ chối có kiểu; Order chụp lại snapshot phản hồi đó. |
| Inventory | Các lệnh reserve/commit/release chỉ do Saga thực hiện; Inventory sở hữu công thức/tồn kho và không đọc trực tiếp Order DB. |
| Payment | Hợp đồng nội bộ initiate/confirm/expire/reconcile; Payment sở hữu trạng thái nhà cung cấp, chữ ký IPN và ngoại lệ thanh toán muộn. |
| Step Functions | Dữ liệu đầu vào/đầu ra của tiến trình bao gồm `sagaId`, `attemptId`, `orderId`, tenant/branch và correlation; các callback được xác thực và bất biến. |
| Audit | Order phát sự kiện audit qua outbox; Audit Service sở hữu việc lưu trữ/thời gian lưu/truy vấn. |
| Loyalty | Tiêu thụ sự kiện chuẩn `OrderPaid`; tham chiếu khách hàng là mờ (opaque) và Loyalty sở hữu PII/tra cứu hồ sơ. |
| Reporting | Tiêu thụ các sự kiện vòng đời / đơn đã thanh toán có phiên bản vào mô hình đọc (read model); không câu truy vấn báo cáo nào được join trực tiếp Order DB khi chạy. |
| Angular POS | Sử dụng client OpenAPI sinh tự động, header `If-Match`, idempotency key và hiển thị rõ ràng các trạng thái pending/lỗi/tính lại giá. |
| KDS P1 | Tiêu thụ các sự kiện món ăn của Order bền bỉ và bổ sung chuyển phát/phát lại qua WebSocket mà không biến WebSocket thành nguồn chân lý. |

## Yêu cầu Phi chức năng

Các mục tiêu này là mục tiêu thử nghiệm / MVP kế thừa từ nền tảng cha và bắt buộc phải được đo đạc thực tế thay vì giả định.

| Hạng mục | Yêu cầu | Mục tiêu |
|---|---|---|
| Hiệu năng | Tạo/sửa dòng món nháp, không tính lỗi cold dependency | p95 ≤ 500 ms dưới thực đơn mẫu đại diện. |
| Hiệu năng | Phản hồi khởi tạo checkout | p95 ≤ 1 s cho khởi tạo tiền mặt / QR; QR có thể trả về `202 PENDING`. |
| Thông lượng | Các lượt thử thanh toán (checkout attempts) | Ít nhất 10 attempts/giây trong 5 phút mà không gây tác dụng phụ trùng lặp. |
| Tính nhất quán | Biến đổi Order cục bộ | Đơn hàng, dòng món / snapshot / biến đổi attempt và bản ghi outbox commit nguyên tử. |
| Tính nhất quán | Thanh toán (Checkout) | Không bao giờ để lộ `PAID` trước khi thỏa mãn đầy đủ điều kiện Saga; các lượt thử lại có tính bất biến. |
| Độ tin cậy | Bù trừ (Compensation) | Lệnh release/commit tồn kho thử lại với cơ chế lùi số mũ có giới hạn và hàng đợi DLQ. |
| Độ tin cậy | Khôi phục sự cố | Checkout lỗi chỉ có thể thử lại sau khi bù trừ trước đó an toàn; thanh toán muộn hiển thị rõ ràng. |
| An toàn bảo mật | Cô lập dữ liệu | Mọi truy vấn repository đều bao gồm mệnh đề tenant/chi nhánh; bắt buộc có kiểm thử phủ định BOLA/IDOR. |
| An toàn bảo mật | Đầu vào | Xác thực chế độ phục vụ, tham chiếu bàn, số lượng, ghi chú, ID, tiền tệ và chuyển đổi trạng thái; làm sạch ghi chú. |
| An toàn bảo mật | Audit | Thao tác hủy, từ chối phân quyền, lỗi checkout và ngoại lệ đối soát đều có thể truy vết được. |
| Khả năng quan sát | Tracing | Log có cấu trúc và OpenTelemetry mang theo request/correlation/saga/attempt/order ID. |
| Khả năng quan sát | Metrics | Theo dõi xung đột phiên bản, thời gian chờ pending, lỗi giữ chỗ/thanh toán, bù trừ, thanh toán muộn, outbox và DLQ. |
| Tính sẵn sàng | Lỗi dependency | Các lệnh gọi Catalog/Identity/Inventory/Payment sử dụng timeout, circuit breaker và retry có giới hạn; không gọi chuỗi vô tận. |
| Khả năng bảo trì | Kỷ luật hợp đồng | OpenAPI/AsyncAPI và schema sự kiện được xác thực tự động trong CI; client sinh tự động là nguồn chân lý cho Angular. |

## Dữ liệu và Ràng buộc cho Kiến trúc

- Cơ sở dữ liệu Order DB riêng chạy trên PostgreSQL; định danh UUID v7; timestamp UTC `TIMESTAMPTZ`; tiền tệ số nguyên VND.
- Các bảng cốt lõi: `orders`, `order_lines`, `order_line_modifiers`, `checkout_attempts`, `outbox_messages`, `idempotency_records` và các trường tham chiếu `order_state_transitions`/đối soát tùy chọn.
- Bảng `orders` lưu tenant/branch, chế độ phục vụ, tham chiếu bàn, trạng thái, các khoản tổng, phiên bản, số dòng món active, tham chiếu attempt hiện tại và các dấu thời gian.
- Bảng `order_lines` lưu các ID Catalog mờ, snapshot bất biến món/biến thể/tên/giá, số lượng, ghi chú, trạng thái, metadata hủy món và phiên bản.
- Dữ liệu snapshot modifier bắt buộc phải bảo toàn option ID, tên và phụ thu giá được dùng để tính tổng Order; không cần đọc Catalog khi chạy để hiển thị các dòng Order lịch sử.
- Bảng `checkout_attempts` lưu saga/attempt ID, trạng thái, idempotency key, tổng tiền snapshot, tham chiếu mờ thanh toán/giữ chỗ tồn kho, trạng thái bù trừ, cờ timeout/đối soát và mã lý do thất bại.
- Tuyệt đối không có ràng buộc khóa ngoại thời gian chạy (runtime FK) sang cơ sở dữ liệu của Identity, Catalog, Inventory, Payment, Loyalty hay Audit. Các ID chéo service được xác thực qua hợp đồng và ngữ cảnh tenant/chi nhánh cục bộ.
- Thực thi quy tắc mỗi Order chỉ có duy nhất một checkout attempt đang hoạt động tại một thời điểm, khóa duy nhất `(tenant_id, order_id, attempt_id)` và tính duy nhất của idempotency theo tenant + operation + key.
- Thực thi số lượng không âm, tiền tệ số nguyên hợp lệ, các giá trị chế độ phục vụ/trạng thái được hỗ trợ, tính bất biến của chi nhánh và các quy tắc chuyển trạng thái kết thúc.
- Lập chỉ mục danh sách đơn theo `(tenant_id, branch_id, status, created_at DESC)`, tra cứu đơn nháp active, số hiệu đơn hàng, trạng thái/timeout checkout và trạng thái xuất bản outbox.
- Mọi cột migration đều có ghi chú/chú thích PostgreSQL. Migration Flyway tương thích tiến tới và khởi tạo dữ liệu mẫu có tính tiền định cho đa tenant / đa chi nhánh.

## Các bài Kiểm thử Nghiệm thu (Acceptance Tests)

1. Thu ngân tạo đơn tại quầy, mang đi và tại bàn; tham chiếu bàn được lưu trữ nhưng không bị ép buộc duy nhất.
2. Thu ngân / Quản lý trong cùng chi nhánh có thể sửa đơn nháp; chi nhánh khác hoặc tenant khác nhận mã lỗi `403/404` mà không bị rò rỉ dữ liệu.
3. Header `If-Match` lỗi thời không thể ghi đè lên một cập nhật đơn nháp đang diễn ra đồng thời.
4. Thêm/sửa món, biến thể và modifier sẽ chụp snapshot phản hồi của Catalog; lựa chọn không hợp lệ / thuộc tenant khác / không active sẽ không làm thay đổi dòng món.
5. Thực đơn Catalog thay đổi giữa lúc tạo nháp và lúc bấm checkout sẽ trả về lỗi `catalog_changed`; checkout không tự động tính lại giá âm thầm.
6. Lệnh tính lại giá / làm mới rõ ràng chỉ cập nhật snapshot sau khi client đã xác nhận và tăng phiên bản của Order.
7. Thu ngân không thể hủy dòng món; Chủ sở hữu / Quản lý có thể hủy trong đơn `DRAFT` chỉ khi có lý do không rỗng và phát sinh sự kiện audit.
8. Hủy dòng món active cuối cùng sẽ chuyển trạng thái Order sang `CANCELLED` một cách nguyên tử; không còn đơn nháp rỗng nào để tái sử dụng.
9. Mọi nỗ lực sửa/hủy trên các đơn `CHECKOUT_PENDING`, `PAID` hoặc `CANCELLED` đều bị từ chối.
10. Checkout tiền mặt tính toán chính xác tiền thừa số nguyên và chuyển sang `PAID` đúng một lần dưới tác động của lệnh trùng lặp / thử lại.
11. Khởi tạo VNPAY trả về trạng thái pending, duy trì giữ chỗ tồn kho trong 15 phút, và một webhook IPN hợp lệ có thể hoàn tất Saga đúng một lần.
12. Webhook IPN không hợp lệ / trùng lặp / đến sai thứ tự không bao giờ tạo thanh toán trùng, hoàn tất lặp hay phát sinh sự kiện `OrderPaid` thứ hai.
13. Tồn kho không đủ / thanh toán thất bại / timeout sẽ giải phóng giữ chỗ tồn kho đúng một lần và đưa đơn về `CHECKOUT_FAILED`.
14. Quy trình bù trừ thất bại sẽ chặn checkout mới, hiển thị rõ trạng thái retry/failure và tạo ra bằng chứng retry/DLQ/cảnh báo.
15. VNPAY thành công muộn sau khi đã giải phóng tồn kho sẽ tạo ngoại lệ đối soát và không bao giờ phục hồi lại đơn hàng.
16. Thanh toán thành công nhưng commit tồn kho thất bại không bao giờ tự ý chuyển đơn sang `PAID`; nó sẽ thử lại hoặc hiển thị yêu cầu đối soát thanh toán.
17. Checkout thất bại có thể thử lại sau khi bù trừ an toàn bằng một attempt mới và không gây tác dụng phụ trùng lặp với các attempt lịch sử.
18. Sự kiện `OrderPaid` chỉ phát ra một lần và là sự kiện duy nhất được tiêu thụ để tích điểm Loyalty và tính doanh thu Reporting.
19. Log audit và vận hành chứa đầy đủ ngữ cảnh correlation/saga/actor/order và tuyệt đối không log JWT thô, bí mật, toàn bộ ghi chú hay PII không cần thiết.
20. Kịch bản k6 và tiêm lỗi đáp ứng đầy đủ các mục tiêu thông lượng / độ trễ và chứng minh không phát sinh tác dụng phụ trùng lặp.

## Các Sửa đổi Bắt buộc Đối với Hợp đồng Cha (Parent Contract Amendments)

| Khẳng định ở hợp đồng cha | Quyết định của Order | Hành động tiếp theo bắt buộc |
|---|---|---|
| Trường `OrderLineInput.variantId` có thể null | Client mới bắt buộc gửi biến thể cụ thể; null kiểu cũ chỉ giải quyết khi có đúng một biến thể active. | Cập nhật hợp đồng Order/Catalog resolver và các DTO sinh tự động. |
| Phản hồi Order có snapshot dòng nhưng không có phiên bản/diff Catalog đầy đủ | Order hiển thị các phiên bản snapshot món/biến thể/modifier và mã lỗi có kiểu `catalog_changed`. | Bổ sung các trường dữ liệu và mã lỗi vào hợp đồng cha. |
| API Checkout liệt kê mã `202/200` nhưng không có trạng thái bù trừ | Phản hồi hiển thị `sagaId`, `attemptId`, trạng thái pending/thất bại/bù trừ/đối soát. | Cập nhật CheckoutResponse và lược đồ trạng thái Order. |
| Các API Payment/Inventory là các endpoint Saga chung chung | Order đòi hỏi ngữ nghĩa commit/release/idempotency và đối soát thanh toán muộn. | Đồng bộ các hợp đồng service nội bộ và đầu vào/đầu ra của AWS Step Functions. |
| Sự kiện cha bao gồm cả `PaymentPaid` và `OrderPaid` | `OrderPaid` là sự thật chuẩn mực cho Loyalty/Reporting; PaymentPaid không phải là sự thật doanh thu/tích điểm. | Giữ nguyên định tuyến consumer và bổ sung kiểm thử trùng lặp / phát lại. |
| Endpoint hủy dòng món chỉ dành cho Quản lý | Chỉ Owner/Manager, chỉ áp dụng trong `DRAFT`, bắt buộc có lý do/audit, hủy dòng cuối sẽ hủy Order. | Bổ sung các ràng buộc phân quyền, trạng thái và lược đồ audit. |

## Các Quyết định được Chuyển tiếp sang Kiến trúc và Hợp đồng

1. Lược đồ chính xác của Order / dòng món / checkout attempt và các ràng buộc chuyển trạng thái.
2. Các endpoint công khai cho đơn nháp, hủy món, tính lại giá và checkout kết hợp các callback Saga nội bộ.
3. Chính sách chính xác về tổng tiền / VAT / chiết khấu và snapshot tổng tiền checkout bất biến.
4. Máy trạng thái AWS Step Functions, tính bất biến của tác vụ và luồng bù trừ/đối soát.
5. Lược đồ sự kiện cho vòng đời Order, `OrderPaid`, audit và chuyển phát KDS ở P1.
6. Lập chỉ mục / thời gian lưu trữ cho danh sách đơn chi nhánh, các attempt đang pending, outbox và các tham chiếu audit.

## Các Kỹ năng Chuyên môn được Tham chiếu

- **spec-reviewer:** Chuyển đổi 8 cảnh báo trong Vision thành các yêu cầu Order rõ ràng, các bài kiểm thử nghiệm thu và các sửa đổi bổ sung cho hợp đồng cha.
- **api-design:** Thiết kế các API công khai cho đơn nháp/checkout/hủy món/tính lại giá, tương tranh lạc quan, idempotency, mã lỗi chuẩn và ranh giới workload nội bộ.
- **database-design:** Thiết kế snapshot bất biến, checkout attempts, ràng buộc trạng thái, chỉ mục tenant/chi nhánh, UUID v7, tiền tệ số nguyên và quy tắc migration.
- **log-processing:** Đo đạc viễn thông có cấu trúc cho audit/outbox/Saga, các trường correlation và làm sạch dữ liệu nhạy cảm.

## Phê duyệt

- **Trạng thái:** Đã được người dùng phê duyệt
- **Giai đoạn:** Khám phá Yêu cầu Kỹ thuật (Requirements Discovery)
- **Giai đoạn tiếp theo sau phê duyệt:** Thiết kế Kiến trúc (Architecture Design)
