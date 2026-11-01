# Module 4 — Payment Service: Yêu cầu Kỹ thuật (Requirements)

**Ngày:** 2026-10-12  
**Trạng thái:** Đã phê duyệt (Approved)  
**Phạm vi:** M — Quy trình Service Chuẩn (Standard Service Flow)  
**Module:** Thanh toán (Payment Service)

## 1. Phạm vi và Phụ thuộc

Payment Service sở hữu các lượt thử thanh toán (payment attempt), trạng thái thanh toán phía nhà cung cấp (provider payment state), xác thực webhook IPN, quy trình đối soát (reconciliation) và các sự kiện thanh toán. Order Service sở hữu trạng thái đơn hàng, lượt thử checkout (`checkout_attempt`) và ảnh chụp snapshot tổng tiền của Order.

### 1.1 Phạm vi P0 (P0 Scope)

- Thanh toán tiền mặt và tự động tính tiền thừa.
- Tạo mã VNPAY dynamic QR sandbox.
- Máy trạng thái Payment (Payment state machine) với các bước chuyển đổi hợp lệ.
- Xác thực IPN, kiểm tra checksum/chữ ký điện tử và tính bất biến (idempotency).
- Tiến trình đối soát (reconciliation job) cho payment bị treo, callback gặp lỗi hoặc trạng thái từ provider chưa rõ ràng.
- Ranh giới API / sự kiện với Order/Saga, Inventory và bộ phận vận hành.

### 1.2 Phạm vi P1 (P1 Scope)

- Thanh toán kết hợp nhiều phương thức (Split tender).
- Hoàn tiền một phần hoặc toàn bộ (Partial/Full Refund).
- Lịch sử giao dịch chi tiết.
- Báo cáo đối soát doanh thu.

### 1.3 Các Hệ thống Phụ thuộc

- Order Service gửi `orderId`, `checkoutAttemptId`, `tenantId`, `branchId`, `amountDueMinor`, `currency` và ngữ cảnh idempotency trong một request service đã xác thực; trusted service context là nguồn kiểm tra phạm vi (scope).
- AWS Step Functions / Order điều phối tuần tự các bước: Payment → Inventory commit → Order finalize.
- Saga nhận sự kiện `PaymentPaid.v1` để tiếp tục luồng checkout; Saga gọi trực tiếp lệnh `CommitReservation` tới Inventory. Payment Service không tự ý hoàn tất Order và sự kiện `PaymentPaid` không phải là trigger tự động kích hoạt trừ tồn kho của Inventory.
- VNPAY Sandbox cung cấp API tạo QR, gửi webhook IPN và API tra cứu trạng thái giao dịch (query status) thông qua bộ điều hợp (adapter).
- AWS Cognito cung cấp danh tính người dùng và vai trò (user identity/role) cho các API có người dùng thao tác.
- AWS Secrets Manager cung cấp thông tin xác thực và khóa bí mật ký (signing secret) của VNPAY.
- Transactional Outbox và Message Broker cung cấp cơ chế phân phát sự kiện theo nguyên tắc ít nhất một lần (at-least-once).

## 2. Các Trường hợp Sử dụng (Use Cases)

| Mã | Mức ưu tiên | Tác nhân | Trường hợp sử dụng | Kết quả chính |
|---|---|---|---|---|
| PAY-UC-01 | P0 | Cashier/Manager | Thu tiền mặt | Payment chuyển sang `PAID`, trả lại số tiền thừa, phát ra sự kiện `PaymentPaid.v1` đúng một lần |
| PAY-UC-02 | P0 | Cashier/Manager | Tạo VNPAY QR | Payment ở trạng thái `PENDING`, trả về chuỗi QR payload và thời gian hết hạn (expiry) |
| PAY-UC-03 | P0 | VNPAY | Gửi webhook IPN | Callback được xác thực chữ ký, khử trùng lặp và chuyển trạng thái có tính bất biến (idempotent) |
| PAY-UC-04 | P0 | Worker/Operator | Đối soát payment bị treo | Trạng thái phía provider được kết luận hoặc tạo ra ngoại lệ đối soát cần xử lý |
| PAY-UC-05 | P0 | Order/Saga | Đọc trạng thái payment | Trả về trạng thái, số tiền, mã tham chiếu provider và các cờ đối soát đúng phạm vi ủy quyền |
| PAY-UC-06 | P0 | Hệ thống | Xử lý thanh toán muộn | Ghi nhận ngoại lệ đối soát `SETTLED_LATE` nhưng tuyệt đối không làm sống lại Order |
| PAY-UC-07 | P1 | Cashier/Manager | Thanh toán kết hợp (Split tender) | Nhiều payment được cộng dồn cho đến khi đủ số tiền cần thanh toán (`amountDue`) |
| PAY-UC-08 | P1 | Manager/Owner | Hoàn tiền (Refund) | Tạo yêu cầu hoàn tiền một phần/toàn bộ cho payment đã `PAID`, có tính bất biến và audit đầy đủ |
| PAY-UC-09 | P1 | Manager/Owner | Xem lịch sử / Báo cáo | Đọc lịch sử giao dịch và số liệu đối soát theo phạm vi tenant/chi nhánh |

## 3. Tác nhân và Phân quyền (Actors and Authorization)

| Tác nhân / Vai trò | Quyền hạn |
|---|---|
| `cashier` (Thu ngân) | Tạo payment tiền mặt / VNPAY cho Order thuộc phạm vi chi nhánh; đọc thông tin payment thuộc phạm vi chi nhánh |
| `manager` (Quản lý) | Toàn bộ quyền của Thu ngân trong chi nhánh; yêu cầu hủy payment đang pending theo chính sách; thực hiện hoàn tiền; đọc lịch sử / báo cáo của chi nhánh |
| `owner` (Chủ sở hữu) | Toàn quyền trong toàn bộ tenant, bao gồm hoàn tiền, đối soát và xem báo cáo toàn tenant |
| `kitchen` (Bếp/Bar) | Tuyệt đối không có quyền truy cập vào Payment Service |
| `order-saga-service` | Khởi tạo payment, nhận/đọc trạng thái, gửi lệnh hết hạn/hủy theo phân quyền service nội bộ |
| `reconciliation-worker` | Tra cứu provider, chuyển trạng thái theo chính sách đối soát, phát sự kiện; không hoạt động dưới vai trò người dùng |
| VNPAY | Chỉ gọi vào endpoint IPN tại ranh giới API Gateway; không mang Cognito user token |

Mọi API có dữ liệu tenant/chi nhánh đều bắt buộc phải kiểm tra phân quyền tại tầng service và tầng repository/query. Tuyệt đối không sử dụng ID truyền từ URL làm bằng chứng sở hữu tài nguyên.

## 4. Yêu cầu Chức năng

### 4.1 Khởi tạo Thanh toán Chung (Common Payment Initiation)

1. Payment Service chỉ nhận `amountDueMinor` và `currency` từ snapshot của Order; không gọi sang Catalog, không tính toán lại giá, thuế, chiết khấu hay phụ phí.
2. Trong P0 chỉ chấp nhận `currency=VND` và số tiền là số nguyên không âm theo đơn vị nhỏ nhất (minor unit - đồng); nghiêm cấm dùng số thực dấu phẩy động.
3. Yêu cầu bắt buộc phải chứa `orderId`, `checkoutAttemptId`, `paymentMethod`, `amountDueMinor`, `currency`, `idempotencyKey`, `correlationId` và danh tính tác nhân/service; các trường `tenantId`/`branchId` trong body nếu có chỉ dùng làm ngữ cảnh đối chiếu với trusted service context.
4. Order/Saga xác nhận checkout attempt vẫn đang hoạt động và gửi snapshot/phiên bản bất biến. Payment Service xác thực tính nhất quán của ngữ cảnh tenant/chi nhánh/đơn hàng đáng tin cậy, nhưng không đọc trực tiếp Order DB và không tự sở hữu trạng thái Order.
5. Cùng một `Idempotency-Key` và fingerprint yêu cầu bắt buộc phải trả về phản hồi đã lưu trước đó. Cùng key nhưng nội dung yêu cầu khác phải trả về lỗi xung đột (conflict).
6. Mỗi `checkoutAttemptId` chỉ có duy nhất một payment VNPAY ở trạng thái `PENDING`; attempt đã ở trạng thái kết thúc (terminal) không được phép reset.
7. Bản ghi payment attempt lưu trữ snapshot số tiền bất biến, phương thức, các mã tham chiếu, thời gian hết hạn, lịch sử chuyển trạng thái và các dấu thời gian.
8. Tuyệt đối không có API công khai nào được phép tùy ý đặt trạng thái payment; mọi chuyển đổi trạng thái phải đi qua domain command, webhook IPN hoặc tiến trình đối soát hợp lệ.

### 4.2 Thanh toán Tiền mặt P0 (Cash Payment)

1. Yêu cầu thanh toán tiền mặt bắt buộc phải có trường `amountReceivedMinor` (số tiền khách đưa).
2. Nếu `amountReceivedMinor < amountDueMinor`, hệ thống trả về lỗi xác thực (validation error) và tuyệt đối không tạo payment ở trạng thái `PAID`.
3. Nếu số tiền khách đưa đủ, hệ thống tính `cashChangeMinor = amountReceivedMinor - amountDueMinor` bằng phép số học số nguyên.
4. Payment tiền mặt chuyển trạng thái từ `PENDING → PAID` trong cùng giao dịch cơ sở dữ liệu nghiệp vụ sau khi đã xác thực số tiền hợp lệ.
5. Phản hồi bắt buộc phải trả về `amountDueMinor`, `amountReceivedMinor`, `cashChangeMinor`, `paymentId`, `state=PAID` và `paidAt`.
6. Việc thử lại (retry) do máy POS bị timeout không được tạo thêm bản ghi payment mới hoặc phát trùng lặp sự kiện `PaymentPaid.v1`.
7. Thanh toán tiền mặt không gọi sang VNPAY và không lưu trữ mã tham chiếu giao dịch của nhà cung cấp.

### 4.3 VNPAY QR P0

1. Yêu cầu thanh toán VNPAY tạo một payment ở trạng thái `PENDING` và gọi bộ điều hợp để sinh mã dynamic QR từ sandbox.
2. Nếu đã tồn tại một payment VNPAY ở trạng thái `PENDING` còn hạn cho cùng một checkout attempt, hệ thống trả lại thông tin QR/payment hiện tại; không tạo attempt thứ hai.
3. Chỉ tạo attempt mới khi attempt trước đó đã chuyển sang `FAILED` hoặc `EXPIRED`.
4. Thời gian sống (TTL) mặc định trong bản demo là 15 phút, có thể cấu hình được nhưng phải được trả về trong trường `expiresAt` của phản hồi và được sử dụng nhất quán bởi Order/Saga và tiến trình đối soát.
5. Phản hồi trạng thái pending bắt buộc phải trả về `paymentId`, `orderId`, `checkoutAttemptId`, `state`, `amountDueMinor`, `currency`, `qrPayload`, `providerOrderRef`, `expiresAt` và `correlationId`.
6. Thông tin xác thực nhà cung cấp và khóa bí mật ký (signing secret) được lấy từ AWS Secrets Manager; tuyệt đối không hard-code hoặc log secret ra file.
7. Không lưu trữ dữ liệu thẻ thô. Chuỗi QR payload và các mã tham chiếu provider phải được bảo vệ theo đúng phân loại dữ liệu của nền tảng.
8. Nếu provider không thể tạo được mã QR, payment tuyệt đối không được báo `PAID`; lỗi phải duy trì được mã tương quan (correlation) và trả về mã lỗi rõ ràng để thử lại.

### 4.4 Máy Trạng thái Payment P0 (State Machine)

| Trạng thái hiện tại | Lệnh / Sự kiện kích hoạt | Điều kiện bảo vệ (Guard) | Trạng thái tiếp theo | Tác dụng phụ (Side effect) |
|---|---|---|---|---|
| `PENDING` | Xác thực số tiền mặt thành công | Tiền nhận ≥ Tiền phải trả | `PAID` | Lưu thông tin thanh toán/tiền thừa, ghi outbox `PaymentPaid.v1` |
| `PENDING` | VNPAY báo thành công hợp lệ | Chữ ký, ánh xạ, số tiền và trạng thái provider hợp lệ | `PAID` | Lưu mã tham chiếu provider, ghi outbox `PaymentPaid.v1` |
| `PENDING` | Provider kết luận thất bại rõ ràng | Callback hoặc truy vấn khẳng định thất bại | `FAILED` | Ghi outbox `PaymentFailed.v1` |
| `PENDING` | Quyết định hết hạn (Expiry) | Provider khẳng định hết hạn hoặc chính sách cục bộ đóng attempt sau khi đã áp dụng ranh giới đối soát; nếu provider chưa rõ thì giữ `PENDING` + gắn cờ | `EXPIRED` | Ghi outbox `PaymentExpired.v1`, báo cho Saga giải phóng giữ chỗ |
| `PENDING` | Lệnh hủy được ủy quyền | Order/Saga hoặc chính sách quản lý hợp lệ | `CANCELLED` | Ghi outbox `PaymentCancelled.v1` |
| Trạng thái kết thúc (Terminal) | Kết quả trùng lặp giống nhau | Idempotency key / sự kiện đã được xử lý trước đó | Giữ nguyên trạng thái | Thao tác No-op, trả về kết quả cũ đã lưu |
| Trạng thái kết thúc (Terminal) | Callback xung đột | Callback hợp lệ nhưng mâu thuẫn với trạng thái hiện tại | Giữ nguyên trạng thái | Lưu callback, ghi audit và tạo ngoại lệ đối soát |
| `EXPIRED` / `CANCELLED` | Provider báo thành công hợp lệ đến muộn | Quyết toán provider hợp lệ nhưng attempt đã ở trạng thái kết thúc | Giữ nguyên trạng thái | Tạo bản ghi trong `late_payment_reconciliation` với `SETTLED_LATE`; chỉ phát outbox `PaymentReconciliationRequired.v1` |

Các trạng thái `PAID`, `FAILED`, `EXPIRED`, `CANCELLED` là các trạng thái kết thúc (terminal) của một attempt. Thao tác thử lại sẽ tạo ra một attempt mới. Quy trình hoàn tiền (Refund ở P1) là một máy trạng thái riêng biệt, tuyệt đối không chỉnh sửa payment attempt gốc.

### 4.5 Xử lý Webhook VNPAY IPN P0

Quy trình xử lý IPN bắt buộc phải tuân theo đúng thứ tự sau:

1. API Gateway áp dụng xác thực schema, kiểm tra kích thước/tần suất (rate limiting) và nhận raw payload.
2. Lưu raw payload tối thiểu cần thiết cùng với metadata của request; dữ liệu nhạy cảm phải được mã hóa/thu giảm và không ghi log dạng văn bản rõ (plaintext).
3. Xác thực checksum / chữ ký điện tử theo thuật toán và quy tắc chuẩn hóa trường (canonicalization) của bộ điều hợp VNPAY sandbox, lấy secret từ Secrets Manager.
4. Nếu chữ ký không hợp lệ: Không tạo bản ghi idempotency nghiệp vụ, không thay đổi trạng thái payment; lưu sự kiện bảo mật/audit và trả về phản hồi không hợp lệ tương thích với nhà cung cấp.
5. Nếu chữ ký hợp lệ: Kiểm tra nhà cung cấp, mã tham chiếu đơn hàng/merchant, ánh xạ payment, ánh xạ tenant/chi nhánh, số tiền và trạng thái thành công từ phía provider.
6. Nếu ánh xạ, số tiền hoặc trạng thái sai lệch: Không chuyển sang `PAID`; lưu trữ callback và lý do sai lệch; tạo sự kiện đối soát hoặc sự kiện bảo mật khi phù hợp.
7. Đối với callback hợp lệ, khử trùng lặp dựa trên khóa sự kiện provider / mã tham chiếu giao dịch provider và fingerprint của payload trong một giao dịch cơ sở dữ liệu có ràng buộc duy nhất (unique constraint).
8. Callback hợp lệ đã xử lý trước đó sẽ trả về kết quả tương thích với nhà cung cấp nhưng không được phát sinh tác dụng phụ lần thứ hai.
9. Callback đến sai thứ tự hoặc đến sau khi attempt đã kết thúc không được ghi đè trạng thái; xử lý dưới dạng no-op hoặc tạo ngoại lệ đối soát theo chính sách.
10. Phản hồi IPN trả về cho nhà cung cấp không được làm lộ lỗi nội bộ, bí mật hệ thống, dữ liệu tenant hay stack trace.

### 4.6 Tiến trình Đối soát P0 (Reconciliation Job)

1. Bộ lập lịch (Scheduler) chạy định kỳ mặc định mỗi 5 phút; chu kỳ chạy, TTL, thời gian timeout và ngưỡng thử lại đều có thể cấu hình được.
2. Worker lựa chọn các payment ở trạng thái `PENDING` đã quá hạn, các callback chưa có kết luận hoặc các bản ghi có gắn cờ `RECONCILIATION_REQUIRED`.
3. Worker thực hiện nhận tác vụ (claim) bằng cơ chế lease hoặc khóa dòng (row lock); nghiêm cấm nhiều worker cùng truy vấn provider hoặc chuyển đổi trạng thái của cùng một payment đồng thời.
4. Mỗi lượt chạy có một định danh `reconciliationRunId`, danh sách bản ghi item, số lần thử, thời gian bắt đầu/kết thúc, kết quả và correlation ID.
5. Worker gọi API tra cứu trạng thái của provider bằng mã tham chiếu giao dịch/đơn hàng, áp dụng timeout và cơ chế lùi số mũ (exponential backoff).
6. Kết quả thành công hợp lệ từ provider đối với payment đang `PENDING` sẽ chuyển payment sang `PAID` và phát ra chính xác một sự kiện `PaymentPaid.v1`.
7. Provider khẳng định thất bại rõ ràng sẽ chuyển payment sang `FAILED`; provider xác nhận hết hạn hoặc chính sách hết hạn cục bộ sẽ chuyển sang `EXPIRED`.
8. Nếu gọi provider bị timeout hoặc trạng thái chưa rõ ràng thì không được ép thành thất bại; giữ nguyên trạng thái, tăng metadata thử lại và gắn cờ `RECONCILIATION_REQUIRED`.
9. Khi vượt quá ngưỡng thử lại tối đa, hệ thống phát cảnh báo và đưa vào hàng đợi xử lý thủ công của bộ phận vận hành.
10. Chạy lại cùng một item/run phải đảm bảo tính bất biến (idempotent) và không phát ra sự kiện trùng lặp.
11. Thanh toán thành công đến muộn sau khi Order đã giải phóng giữ chỗ tồn kho / kết thúc thất bại sẽ không làm thay đổi trạng thái của payment attempt; worker ghi nhận vào bảng `late_payment_reconciliation` với trạng thái quyết toán `SETTLED_LATE`, phát sự kiện `PaymentReconciliationRequired.v1` và không gọi lệnh tự động phục hồi lại Order.

### 4.7 Sự kiện và Ranh giới Saga P0

Payment Service phát các sự kiện thông qua transactional outbox:

- `PaymentInitiated.v1`
- `PaymentPaid.v1`
- `PaymentFailed.v1`
- `PaymentExpired.v1`
- `PaymentCancelled.v1`
- `PaymentReconciliationRequired.v1`

Mỗi sự kiện có tối thiểu các trường: `eventId`, `eventType`, `eventVersion`, `occurredAt`, `tenantId`, `branchId`, `aggregateId`, `paymentId`, `orderId`, `checkoutAttemptId`, `correlationId`, `causationId` và `data`.

- Cơ chế phân phát sự kiện là ít nhất một lần (at-least-once); các consumer thực hiện khử trùng lặp theo `eventId`.
- `PaymentPaid.v1` là sự thật thanh toán phục vụ cho Order/Saga và Audit. Saga sử dụng sự thật này để gọi lệnh commit giữ chỗ mang workload identity sang Inventory.
- Chỉ có duy nhất Order Service phát ra sự kiện `OrderPaid.v1` sau khi thanh toán thành công và bước commit tồn kho trong Inventory đã hoàn tất.
- Payment Service không phát `OrderPaid.v1`, không tự ý hoàn tất Order và không tự động giữ chỗ lại trong Inventory cho các thanh toán đến muộn.

### 4.8 Thanh toán Kết hợp P1 (Split Tender)

1. Một Order có thể bao gồm nhiều payment attempt với phương thức thanh toán và số tiền phân bổ (allocation) riêng biệt.
2. Tổng số tiền phân bổ không được vượt quá `amountDueMinor`; tổng các payment đã chuyển sang `PAID` phải bằng chính xác số tiền cần thanh toán để hoàn tất checkout.
3. Mỗi khoản thanh toán (tender) có trạng thái, tính bất biến, mã tham chiếu provider và audit log độc lập.
4. Order/Saga nhận tiến trình thanh toán theo từng phần nhưng chỉ cho phép hoàn tất đơn hàng khi đã thu đủ toàn bộ số tiền.
5. Việc phân bổ trùng lặp hoặc thử lại không được phép vượt quá tổng giá trị của Order.

### 4.9 Hoàn tiền P1 (Refund)

1. Chỉ các payment ở trạng thái `PAID` mới đủ điều kiện để thực hiện hoàn tiền.
2. Hỗ trợ hoàn tiền một phần hoặc toàn bộ nhưng tổng số tiền hoàn thành công không được vượt quá số tiền đã thanh toán của payment trừ đi các lần hoàn tiền trước đó.
3. Mỗi giao dịch hoàn tiền có một `refundId`, liên kết tới payment gốc, số tiền, lý do, tác nhân thực hiện, mã tham chiếu provider, trạng thái, idempotency key và audit log.
4. Thao tác hoàn tiền đòi hỏi vai trò `manager` hoặc `owner` theo đúng phạm vi chi nhánh/tenant.
5. Việc hoàn tiền phía provider bị thất bại hoặc đang chờ xử lý không được phép sửa đổi lịch sử đã thanh toán của payment gốc; quy trình hoàn tiền có máy trạng thái và đối soát riêng biệt.
6. Thử lại với cùng idempotency key hoàn tiền sẽ trả về kết quả cũ; nếu payload khác sẽ bị báo lỗi xung đột (conflict).
7. Hoàn tiền không tự động thay đổi trạng thái của Order hoặc phát lại sự kiện `OrderPaid` một lần nữa.

### 4.10 Lịch sử và Báo cáo Đối soát P1

1. API đọc dữ liệu có phân trang, chỉ đọc (read-only), hỗ trợ lọc theo khoảng thời gian, chi nhánh, ID đơn hàng/payment/provider, phương thức, trạng thái, thu ngân, hoàn tiền và cờ đối soát.
2. Chi tiết payment hiển thị số tiền phải trả, số tiền đã trả, tiền thừa tiền mặt, các dấu thời gian, mã tham chiếu provider, tóm tắt lịch sử chuyển trạng thái, tóm tắt callback/đối soát và các liên kết hoàn tiền.
3. Báo cáo tổng hợp theo ngày / chi nhánh / provider bao gồm tổng tiền theo phương thức, số lượng giao dịch theo trạng thái, sai lệch giữa hệ thống nội bộ và provider, thanh toán muộn và hoàn tiền.
4. Mô hình đọc và báo cáo tuyệt đối không được phép chỉnh sửa sổ cái thanh toán gốc.
5. Tính năng xuất file Excel/PDF và báo cáo kế toán nâng cao không thuộc phạm vi tối thiểu của P1.

## 5. Yêu cầu API

### 5.1 Khởi tạo Thanh toán Nội bộ (Internal Initiate Payment)

`POST /internal/v1/orders/{orderId}/payments`

Bên gọi (Caller): Danh tính service của Order/Saga. Ngữ cảnh tenant/chi nhánh đáng tin cậy bắt buộc phải đến từ xác thực service hoặc ngữ cảnh có chữ ký; các trường trong body chỉ dùng để kiểm tra tính nhất quán.  
Phản hồi: Tiền mặt có thể trả về mã `200`; khởi tạo VNPAY trả về mã `202` kèm trạng thái `PENDING`.

Yêu cầu (Request) tối thiểu:

```json
{
  "checkoutAttemptId": "uuid",
  "tenantId": "uuid",
  "branchId": "uuid",
  "paymentMethod": "CASH|VNPAY_QR",
  "amountDueMinor": 125000,
  "currency": "VND",
  "amountReceivedMinor": 150000,
  "idempotencyKey": "checkout-attempt-key",
  "correlationId": "uuid",
  "returnUrl": null
}
```

Trường `amountReceivedMinor` bắt buộc đối với `CASH`, bỏ qua hoặc không chấp nhận đối với `VNPAY_QR`; trường `returnUrl` chỉ dùng theo danh sách trắng (allowlist) cho VNPAY và không dùng làm tín hiệu xác thực.

Phản hồi (Response) tối thiểu:

```json
{
  "paymentId": "uuid",
  "orderId": "uuid",
  "checkoutAttemptId": "uuid",
  "paymentMethod": "CASH",
  "paymentStatus": "PAID",
  "amountDueMinor": 125000,
  "amountReceivedMinor": 150000,
  "cashChangeMinor": 25000,
  "currency": "VND",
  "providerTransactionId": null,
  "qrPayload": null,
  "expiresAt": null,
  "reconciliationRequired": false,
  "correlationId": "uuid"
}
```

### 5.2 Đọc Chi tiết Thanh toán (Read Payment)

`GET /api/v1/payments/{paymentId}`

- Bắt buộc phải có Cognito JWT.
- Phạm vi chi nhánh/tenant phải được kiểm tra chặt chẽ phía máy chủ.
- Không trả về raw callback payload, secret hay stack trace nội bộ.
- Có thể trả về bản tóm tắt chuyển trạng thái và tóm tắt đối soát tùy theo vai trò của người dùng.

### 5.3 Webhook VNPAY IPN

`POST /webhooks/vnpay/ipn`

- Công khai chỉ tại ranh giới API Gateway; không đòi hỏi Cognito user token.
- Bộ điều hợp VNPAY chịu trách nhiệm trích xuất các trường từ provider, chuẩn hóa dữ liệu đầu vào tính checksum, xác thực chữ ký và ánh xạ trạng thái provider.
- Phản hồi trả về chỉ chứa ngữ nghĩa thành công hoặc không hợp lệ tương thích với provider; các lỗi nội bộ được ghi log/audit kèm correlation ID.

### 5.4 Chạy Đối soát (Reconciliation Run)

`POST /internal/v1/payments/reconciliation/run`

- Bên gọi là tiến trình lập lịch (scheduler), worker hoặc danh tính service của nhân viên vận hành.
- Hỗ trợ chạy toàn bộ hoặc chạy theo mã tham chiếu payment/provider trong phạm vi được cấp quyền.
- Tuyệt đối không cho phép client máy POS gọi trực tiếp.
- Phản hồi trả về `reconciliationRunId`, số lượng bản ghi được chấp nhận và trạng thái đưa vào hàng đợi; kết quả chi tiết từng item được đọc thông qua API đối soát / mô hình đọc.

### 5.5 Hợp đồng Mã lỗi (Error Contract)

Tối thiểu bắt buộc phải phân biệt các mã lỗi sau:

| Mã HTTP / Mã lỗi | Ý nghĩa |
|---|---|
| `400` | Payload, số tiền, loại tiền tệ hoặc schema không hợp lệ |
| `401/403` | Thiếu hoặc không đủ quyền hạn / không đúng phạm vi ủy quyền |
| `404` | Tài nguyên không tồn tại trong phạm vi của tenant |
| `409` | Xung đột idempotency, xung đột payment đang active, chuyển trạng thái không hợp lệ hoặc yêu cầu đối soát |
| `422` | Xác thực nghiệp vụ phía provider, số tiền hoặc ánh xạ thất bại |
| `503` | Phụ thuộc provider hoặc Payment tạm thời không khả dụng; không khẳng định payment thất bại nếu chưa có kết luận rõ ràng |

Phản hồi lỗi bắt buộc phải có `code`, thông báo `message` an toàn, `correlationId`, cờ `retryable` và các mã định danh tài nguyên cần thiết; tuyệt đối không trả về secret hay raw payload của nhà cung cấp.

## 6. Yêu cầu Dữ liệu và Tính Nhất quán

### 6.1 Các Bản ghi do Payment Sở hữu

- `payment_attempts`: Lưu tenant/chi nhánh, tham chiếu đơn hàng / checkout attempt, phương thức, snapshot số tiền bất biến, trạng thái, mã tham chiếu provider, thời gian hết hạn, tác nhân/service, các dấu thời gian và các cờ đối soát.
- `payment_state_transitions`: Lịch sử chuyển trạng thái chỉ nối thêm (append-only), trạng thái từ/sang, lý do, tác nhân, correlation ID và causation ID.
- `provider_callbacks`: Tên provider, khóa sự kiện provider, tham chiếu payment, hash của payload, kết quả xác thực chữ ký, trạng thái provider, raw payload đã mã hóa/thu giảm, thời gian xử lý và thời gian nhận.
- `reconciliation_runs` và `reconciliation_items`: Metadata của lượt chạy đối soát, cơ chế khóa lease, số lần thử, kết quả so khớp nội bộ/provider, sai lệch phát hiện và trạng thái xử lý của vận hành.
- `late_payment_reconciliation`: Tham chiếu payment đã ở trạng thái kết thúc, mã tham chiếu / trạng thái quyết toán provider, số tiền, lý do, trạng thái thu hồi/hoàn tiền, hành động của nhân viên vận hành và tham chiếu audit.
- `idempotency_records`: Phạm vi, khóa, fingerprint yêu cầu, tham chiếu phản hồi / trạng thái và thời gian hết hạn.
- `outbox_messages`: Payload sự kiện có phiên bản, aggregate, trạng thái xuất bản, metadata thử lại và hàng đợi DLQ.
- Bảng P1 `refunds` và `payment_allocations` phục vụ cho hoàn tiền và thanh toán kết hợp.

### 6.2 Các Bất biến Bắt buộc (Required Invariants)

- Idempotency key là duy nhất theo tenant, thao tác và phạm vi bên gọi.
- Khóa sự kiện của provider (provider event key) là duy nhất theo từng provider.
- Mã tham chiếu giao dịch provider là duy nhất theo tenant và provider khi provider có trả mã này về.
- Tối đa chỉ có một payment VNPAY ở trạng thái `PENDING` còn hạn cho mỗi `checkoutAttemptId`.
- Tuyệt đối không có bước chuyển đổi trạng thái nào từ một trạng thái kết thúc (terminal) sang một trạng thái khác.
- Số tiền thanh toán và số tiền hoàn tiền bắt buộc sử dụng đơn vị minor unit dạng số nguyên (đồng).
- Mọi truy vấn payment, đối soát hoặc lịch sử đều bắt buộc phải có mệnh đề điều kiện `tenant_id`; mệnh đề `branch_id` là bắt buộc khi tác nhân không có quyền toàn tenant.
- Việc chuyển trạng thái và ghi sự kiện outbox bắt buộc phải commit nguyên tử cùng nhau trong cùng một giao dịch cơ sở dữ liệu.
- Callback có chữ ký không hợp lệ tuyệt đối không được tạo bản ghi idempotency nghiệp vụ.

### 6.3 Mô hình Tính Nhất quán (Consistency Model)

- Nhất quán mạnh (strong consistency) trong cơ sở dữ liệu của Payment cho trạng thái, tính bất biến và các ràng buộc duy nhất.
- Nhất quán cuối cùng (eventual consistency) giữa Payment, Order, Inventory và Reporting thông qua quy trình Saga, outbox và message broker.
- Các bên tiêu thụ (consumer) được đảm bảo nhận thông điệp theo cơ chế ít nhất một lần (at-least-once) và tự khử trùng lặp.
- Thanh toán thành công không đồng nghĩa với việc Order đã hoàn tất thanh toán; sự kiện `OrderPaid.v1` mới là sự thật đơn hàng đã thanh toán chuẩn mực. Quyết toán thành công đến muộn từ phía provider không được phát ra sự kiện `PaymentPaid.v1` hay `OrderPaid.v1` sau khi attempt đã kết thúc.

## 7. An Toàn Bảo Mật, Quyền Riêng Tư và Audit

- Xác thực Cognito JWT, vai trò người dùng và phạm vi tenant/chi nhánh đối với toàn bộ các API có người dùng thao tác.
- Các API giao tiếp giữa các service sử dụng danh tính service (service identity) và áp dụng danh sách trắng (allowlist) các thao tác được phép.
- Endpoint IPN công khai phải có xác thực schema, giới hạn tốc độ (rate limit), kiểm tra chữ ký/checksum từ provider và cơ chế chống phát lại / bảo vệ tính bất biến.
- Khóa bí mật của VNPAY, thông tin kết nối cơ sở dữ liệu và các khóa ký được lưu trữ an toàn trong AWS Secrets Manager.
- Tuyệt đối không ghi log khóa bí mật, chuỗi dữ liệu thô đưa vào thuật toán ký, dữ liệu thẻ thô hoặc toàn bộ payload nhạy cảm của callback.
- Raw payload của callback được lưu trữ dưới dạng mã hóa hoặc thu giảm thông tin theo chính sách lưu giữ dữ liệu.
- Bắt buộc phải ghi audit log cho các thao tác: Khởi tạo thanh toán, số tiền mặt nhận/tiền thừa, chuyển đổi trạng thái, callback không hợp lệ, callback sai lệch thông tin, đối soát, can thiệp thủ công của vận hành, hoàn tiền và các trường hợp bị từ chối phân quyền.
- Sự kiện audit mang đầy đủ thông tin: Loại/ID tác nhân, tenant, chi nhánh, tham chiếu payment/đơn hàng, hành động, lý do, correlation ID, dấu thời gian và kết quả thực hiện.

## 8. Khả Năng Quan Sát và Vận Hành

Log có cấu trúc bắt buộc phải chứa các trường: `tenantId`, `branchId`, `paymentId`, `orderId`, `checkoutAttemptId`, `provider`, `providerTransactionId` (nếu có), `correlationId` và `eventId`, nhưng tuyệt đối không chứa secret hay dữ liệu nhạy cảm thô.

Các chỉ số đo lường (metrics) và cảnh báo tối thiểu:

- Tỷ lệ khởi tạo thanh toán thành công / thất bại theo từng phương thức.
- Tỷ lệ xác thực tiền mặt thất bại và phân bố tiền thừa trả lại.
- Thời gian trễ và tỷ lệ tạo mã VNPAY QR thất bại.
- Số lượng IPN bị sai chữ ký, sai lệch ánh xạ, bị trùng lặp hoặc đến sai thứ tự.
- Thời gian chờ của payment pending, số lượng payment bị hết hạn, số lượng và thời gian tồn tại của các giao dịch cần đối soát (`RECONCILIATION_REQUIRED`).
- Thời gian trễ và tỷ lệ tra cứu trạng thái provider thất bại.
- Số lần thử lại outbox, độ sâu hàng đợi DLQ và độ trễ sự kiện (event lag).
- Bộ phát hiện tác dụng phụ trùng lặp đối với sự kiện `PaymentPaid`.
- Số lượng giao dịch hoàn tiền pending / thất bại / sai lệch trong P1.

Sổ tay vận hành (Runbook) bắt buộc phải có tài liệu hướng dẫn cụ thể cho các tình huống: Thanh toán thành công đến muộn, trạng thái provider chưa xác định, Payment đã `PAID` nhưng bước commit tồn kho thất bại, IPN trùng lặp, phát lại hàng đợi DLQ và quy trình đối soát thủ công.

## 9. Yêu cầu Phi Chức năng

- Các lệnh gọi API có thể thử lại một cách an toàn mà không tạo ra payment, hoàn tiền hay sự kiện trùng lặp.
- Hệ thống chịu được tải tối thiểu 10 lượt thử khởi tạo thanh toán / giây liên tục trong 5 phút mà không tạo payment trùng lặp cho cùng một checkout attempt.
- Tiến trình đối soát có cơ chế khóa lease, cơ chế thử lại lùi số mũ, hàng đợi DLQ / hàng đợi xử lý thủ công và không bao giờ chạy vòng lặp vô hạn mà không quan sát được.
- Sự cố gián đoạn từ phía nhà cung cấp thanh toán không được làm hệ thống kết luận sai thành `FAILED`; hệ thống phải trả về trạng thái pending, có thể thử lại hoặc yêu cầu đối soát phù hợp.
- Môi trường cục bộ / demo có bộ điều hợp provider giả lập có tính tiền định; bộ điều hợp sandbox vẫn được kiểm thử hợp đồng bằng các dữ liệu mẫu (fixtures) thực tế đã được làm sạch thông tin.
- Các bài kiểm thử bắt buộc: Tiền mặt đưa thiếu / vừa đủ / thừa tiền, tạo payment trùng lặp, tạo VNPAY QR trùng lặp, sai checksum, sai số tiền/đơn hàng, IPN trùng lặp / sai thứ tự / đến muộn, provider timeout, tranh chấp đối soát, khởi động lại service giữa chừng, thử lại outbox / DLQ, truy cập chéo tenant/chi nhánh và các tính năng hoàn tiền / thanh toán kết hợp ở P1.

## 10. Tiêu chí Nghiệm thu (Acceptance Criteria)

1. Số tiền mặt khách đưa nhỏ hơn tổng tiền phải trả sẽ bị từ chối; số tiền bằng tổng tiền trả về tiền thừa bằng `0`; số tiền lớn hơn tổng tiền trả về chính xác tiền thừa.
2. Thử lại yêu cầu thanh toán tiền mặt với cùng idempotency key sẽ trả về cùng `paymentId` và không phát ra sự kiện `PaymentPaid` thứ hai.
3. Hai yêu cầu thanh toán VNPAY gửi đồng thời cho cùng một checkout attempt chỉ tạo ra duy nhất một payment ở trạng thái `PENDING` và một mã QR đang hoạt động.
4. Phản hồi mã VNPAY QR chứa thời gian hết hạn 15 phút theo chính sách bản demo và Order duy trì việc giữ chỗ tồn kho theo cùng chính sách này.
5. Webhook IPN sai checksum không làm thay đổi trạng thái payment, không phát sự kiện thanh toán và tạo ra bằng chứng audit an ninh bảo mật.
6. Webhook IPN có chữ ký hợp lệ nhưng sai mã đơn, sai payment hoặc sai số tiền sẽ không chuyển sang `PAID`; callback được lưu trữ và chuyển hướng đối soát khi cần thiết.
7. Webhook IPN hợp lệ bị gửi lặp lại không tạo ra chuyển đổi trạng thái lặp, không phát sự kiện `PaymentPaid` trùng và không kích hoạt Saga hoàn tất đơn hai lần.
8. IPN đến sai thứ tự hoặc đến muộn không ghi đè lên trạng thái kết thúc; thanh toán thành công đến muộn sau khi đã giải phóng tồn kho tạo ra ngoại lệ đối soát `SETTLED_LATE`, chỉ phát sự kiện `PaymentReconciliationRequired.v1` và tuyệt đối không phục hồi lại Order.
9. Worker đối soát nhận tác vụ độc quyền, thử lại việc truy vấn provider và phân biệt rõ ràng giữa kết quả khẳng định chắc chắn với kết quả chưa xác định.
10. Thanh toán thành công chỉ dừng lại ở sự kiện `PaymentPaid`; sự kiện `OrderPaid` chỉ xuất hiện sau khi đã commit tồn kho trong Inventory và Order Service hoàn tất đơn hàng.
11. Mọi API đọc/ghi đều không thể truy cập vào payment của tenant hoặc chi nhánh khác bằng cách thay đổi ID trên URL hay body.
12. Trong P1, thanh toán kết hợp không cho phép hoàn tất đơn khi tổng số tiền đã trả chưa đủ; hoàn tiền không được vượt quá số tiền đã thanh toán và việc thử lại hoàn tiền không tạo ra giao dịch trùng lặp.
13. Lịch sử và báo cáo chỉ có quyền đọc (read-only), có bộ lọc / phạm vi đầy đủ và tuyệt đối không làm thay đổi sổ cái thanh toán gốc.

## 11. Các Ràng buộc Cần Giữ khi Sang Kiến trúc và Hợp đồng

- Thuật toán tính checksum VNPAY, quy tắc sắp xếp trường chuẩn hóa và ánh xạ phản hồi của provider bắt buộc phải được đóng gói gọn gàng bên trong bộ điều hợp (adapter), có fixture / kiểm thử hợp đồng theo đúng hợp đồng sandbox; tầng domain không được phụ thuộc vào các trường đặc thù của provider.
- Hợp đồng cha tại `docs/designs/pos-platform/2026-10-12-contracts.md` cần hiện thực hóa các trường phản hồi payment, kết quả IPN, đối soát thanh toán muộn và schema sự kiện trước khi tiến hành triển khai mã nguồn chéo service.
- AWS Step Functions bắt buộc phải thể hiện rõ các nhánh timeout, lỗi commit Inventory, bù trừ (compensation) và yêu cầu đối soát; Payment Service không tự ý quyết định việc hoàn tất đơn hàng của Order.
- Trong P0 không tự động hoàn tiền khi bước commit tồn kho thất bại; việc xây dựng sổ tay vận hành và quy trình đối soát thủ công là bắt buộc trước khi demo môi trường giống sản xuất.
- Các bảng dữ liệu của P1 có thể được thiết kế cùng ranh giới cơ sở dữ liệu nhưng không được phép làm chậm hoặc thay đổi bất biến của P0.
- Khởi tạo thanh toán nội bộ bắt buộc phải xác thực danh tính service và ngữ cảnh tenant đáng tin cậy; tuyệt đối không coi các ID và số tiền nằm trong request body là bằng chứng sở hữu hay nguồn chân lý độc lập.
