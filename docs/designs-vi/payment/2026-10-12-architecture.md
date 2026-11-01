# Module 4 — Payment Service: Kiến trúc Kỹ thuật (Architecture)

**Ngày:** 2026-10-12  
**Trạng thái:** Đã phê duyệt (Approved)  
**Phạm vi:** M — Quy trình Service Chuẩn (Standard Service Flow)  
**Module:** Thanh toán (Payment Service)

## 1. Ranh giới và Quyền sở hữu (Boundary and Ownership)

Payment Service sở hữu:

- Vòng đời của `payment_attempt`.
- Các mã tham chiếu phía nhà cung cấp (provider references) và callback từ provider.
- Xác thực checksum / chữ ký điện tử của webhook IPN.
- Quản lý tính bất biến (idempotency) và cơ chế chống trùng lặp.
- Lượt chạy và bản ghi đối soát (`reconciliation_runs`/`reconciliation_items`).
- Bản ghi đối soát thanh toán muộn (`late_payment_reconciliation`).
- Các sự kiện thanh toán và bảng transactional outbox.

Payment Service tuyệt đối không:

- Đọc trực tiếp cơ sở dữ liệu của Order Service hay Inventory Service.
- Tự tính toán lại giá bán, thuế VAT, chiết khấu hay phụ phí.
- Tự ý hoàn tất đơn hàng (finalize Order).
- Tự ý phát ra sự kiện `OrderPaid.v1`.
- Tự ý giữ chỗ lại nguyên vật liệu trong Inventory cho các thanh toán đến muộn.

Order Service / AWS Step Functions chịu trách nhiệm xác nhận checkout attempt vẫn đang hoạt động, gửi snapshot tổng tiền bất biến và điều phối tuần tự: Payment → Inventory commit → Order finalize.

## 2. Cấu trúc Mạng và Luồng Xử lý (Topology)

```text
Máy POS / Client API
  → API Gateway + AWS Cognito
  → Order Service / AWS Step Functions
  → REST API nội bộ của Payment Service
  → Tầng Ứng dụng / Tầng Domain của Payment
  → Cơ sở dữ liệu PostgreSQL Payment DB riêng biệt
       ├─ payment_attempts
       ├─ provider_callbacks
       ├─ reconciliation_runs / reconciliation_items
       ├─ late_payment_reconciliation
       ├─ idempotency_records
       └─ outbox_messages

Payment Service → Bộ điều hợp VNPAY Sandbox Adapter → Cổng thanh toán VNPAY
VNPAY → Endpoint IPN tại API Gateway → Trình xử lý Payment IPN Handler

Transactional Outbox → AWS EventBridge → Các hàng đợi SQS của Consumer
Bộ lập lịch Scheduler → Hàng đợi đối soát → Worker đối soát → Cổng thanh toán VNPAY
```

## 3. Các Thành phần Chính

| Thành phần | Trách nhiệm |
|---|---|
| Bộ điều hợp API (API Adapter) | Các endpoint REST, xác thực request, phân quyền Cognito/service, kiểm tra phạm vi tenant/chi nhánh |
| Dịch vụ Ứng dụng Thanh toán | Xử lý các lệnh khởi tạo, đọc dữ liệu, hủy/hết hạn và các lệnh đối soát |
| Domain Thanh toán | Máy trạng thái (state machine), xác thực số tiền, quyết định idempotency, bảo vệ trạng thái kết thúc |
| Bộ điều hợp Tiền mặt (Cash Adapter) | Xử lý thanh toán tiền mặt đồng bộ và tính toán tiền thừa |
| Bộ điều hợp VNPAY (VNPAY Adapter) | Tạo mã dynamic QR, tính checksum, ánh xạ IPN, tra cứu trạng thái và hỗ trợ hoàn tiền P1 |
| Bộ xử lý IPN (IPN Handler) | Lưu trữ callback, xác thực chữ ký, xác thực ánh xạ, khử trùng lặp và gọi chuyển trạng thái domain |
| Worker Đối soát (Reconciliation Worker) | Nhận tác vụ bằng lease/row lock, truy vấn provider, thử lại kèm lùi số mũ và tạo bản ghi đối soát muộn |
| Tầng Lưu trữ (Persistence Layer) | Các repository PostgreSQL với mệnh đề tenant, ràng buộc duy nhất và chuyển trạng thái chỉ nối thêm |
| Tiến trình Outbox Publisher | Xuất bản sự kiện theo cơ chế ít nhất một lần (at-least-once), quản lý thử lại và hàng đợi DLQ |
| Bảng chiếu Đọc / Báo cáo | Phạm vi P1, chỉ đọc (read-only) và tuyệt đối không thay đổi sổ cái thanh toán gốc |

## 4. Ranh giới Giao dịch Cơ sở Dữ liệu (Transaction Boundaries)

Trong một giao dịch cơ sở dữ liệu duy nhất của Payment DB:

1. Khóa/tìm kiếm bản ghi idempotency record.
2. Xác thực payment attempt và các bất biến nghiệp vụ.
3. Áp dụng chuyển đổi trạng thái hoặc tạo bản ghi đối soát.
4. Lưu trữ metadata của callback và bước chuyển trạng thái.
5. Chèn bản ghi sự kiện vào bảng outbox.
6. Commit nguyên tử (atomic).

Việc xuất bản tin nhắn sang Message Broker nằm ngoài giao dịch cơ sở dữ liệu và có thể thử lại an toàn; các bên tiêu thụ tự khử trùng lặp theo `eventId`.

Lệnh gọi API sang nhà cung cấp thanh toán (provider) không được giữ giao dịch cơ sở dữ liệu mở lâu hơn mức cần thiết. Kết quả từ provider được đưa trở lại thành lệnh domain kèm mã tương quan / mã tham chiếu provider và được commit bằng một giao dịch DB mới.

## 5. Trạng thái và Thanh toán Đến Muộn (State and Late Settlement)

- Chuyển `PENDING → PAID` chỉ khi số tiền mặt hợp lệ hoặc VNPAY báo thành công đã được xác minh chữ ký.
- Chuyển `PENDING → FAILED/EXPIRED/CANCELLED` tuân thủ đúng chính sách chuyển trạng thái.
- Các trạng thái kết thúc (terminal state) tuyệt đối không được phép ghi đè.
- Trường hợp provider báo thành công đến muộn sau khi attempt đã ở trạng thái `EXPIRED` hoặc `CANCELLED`:
  - Giữ nguyên trạng thái kết thúc của payment attempt;
  - Tạo bản ghi trong `late_payment_reconciliation` với trạng thái quyết toán `SETTLED_LATE`;
  - Phát ra sự kiện `PaymentReconciliationRequired.v1`;
  - Tuyệt đối không phát sự kiện `PaymentPaid.v1`, không phát `OrderPaid.v1` và không phục hồi lại Order.

## 6. Giao tiếp và Điều phối (Communication and Orchestration)

- AWS Step Functions gọi đồng bộ vào REST API nội bộ của Payment bằng danh tính service (service identity).
- Webhook VNPAY IPN là một webhook gửi đến (inbound) tại ranh giới của API Gateway.
- Các sự kiện của Payment đi qua Transactional Outbox → AWS EventBridge → Các hàng đợi AWS SQS.
- Quy trình đối soát chạy hoàn toàn bất đồng bộ thông qua bộ lập lịch scheduler / hàng đợi / worker.
- Tuyệt đối không chia sẻ trực tiếp cơ sở dữ liệu giữa các service.
- Payment Service không tự ý điều phối Order hay Inventory; sự kiện `PaymentPaid.v1` chỉ là sự thật thanh toán phục vụ Saga và Audit. Saga là nơi gọi trực tiếp lệnh `CommitReservation` tới Inventory.
- Sự kiện `OrderPaid.v1` chỉ do Order Service phát ra sau khi bước commit tồn kho trong Inventory và bước hoàn tất Order đã thành công trọn vẹn.

## 7. Kiến trúc Bộ điều hợp Nhà cung cấp (Provider Adapter Architecture)

Tầng domain của Payment chỉ phụ thuộc vào các cổng chuẩn hóa (normalized ports):

- `PaymentProviderPort.createDynamicQr(...)`
- `PaymentProviderPort.verifyCallback(...)`
- `PaymentProviderPort.queryStatus(...)`
- Thuộc P1: `PaymentProviderPort.refund(...)`

Các triển khai cụ thể của bộ điều hợp (adapter implementations):

- `CashPaymentProvider`: Xử lý đồng bộ tại chỗ, không gọi ra bên ngoài.
- `VnpaySandboxAdapter`: Đóng gói tên trường VNPAY, mã phản hồi, quy tắc chuẩn hóa chuỗi dữ liệu, tính checksum và tích hợp HTTP.
- `FakePaymentProviderAdapter`: Cung cấp các dữ liệu mẫu kiểm thử có tính tiền định cho môi trường cục bộ / demo mô phỏng IPN trùng lặp, sai thứ tự, timeout và IPN đến muộn.

Dữ liệu đặc thù của VNPAY tuyệt đối không được rò rỉ vào domain model. Adapter không được tự ý ghi vào cơ sở dữ liệu hay phát ra sự kiện; adapter chỉ trả về kết quả đã chuẩn hóa để tầng domain đưa ra quyết định chuyển đổi trạng thái.

## 8. Kiến trúc Cơ sở Dữ liệu (Database Architecture)

- Sử dụng cơ sở dữ liệu PostgreSQL riêng biệt cho Payment Service.
- Định danh UUID v7 cho `paymentId`, `callbackId`, `reconciliationRunId`, `refundId` và `outboxMessageId`.
- Kiểu dữ liệu `BIGINT` cho tiền tệ tính theo đơn vị minor unit (đồng); nghiêm cấm dùng số thực dấu phẩy động.
- Các bảng dữ liệu cốt lõi:
  - `payment_attempts`;
  - `payment_state_transitions`;
  - `provider_callbacks`;
  - `reconciliation_runs` / `reconciliation_items`;
  - `late_payment_reconciliation`;
  - `idempotency_records`;
  - `outbox_messages`;
  - Thuộc P1: `payment_allocations` / `refunds`.
- Các ràng buộc duy nhất (Unique constraints):
  - `(tenant_id, operation, idempotency_key)`;
  - `(tenant_id, provider, provider_transaction_id)` khi tồn tại mã tham chiếu từ provider;
  - `(provider, provider_event_key)`;
  - Tối đa một VNPAY `PENDING` đang hoạt động cho mỗi `checkout_attempt_id`.
- Các chỉ mục (Index) chính:
  - `(tenant_id, branch_id, created_at DESC)`;
  - `(tenant_id, status, expires_at)`;
  - `(tenant_id, reconciliation_required, next_retry_at)`;
  - Khóa provider / event key;
  - Trạng thái xuất bản và thời gian tạo của outbox.
- Sử dụng cơ chế khóa dòng (row lock) hoặc lease cho quy trình đối soát; khóa giao dịch cho việc chuyển đổi trạng thái.
- Không phân vùng (partitioning) trong giai đoạn P0; đánh giá lại theo khối lượng giao dịch và thời gian lưu trữ sau khi có số liệu vận hành thực tế.
- Mọi cột trong file migration Flyway đều có ghi chú (comment) mô tả mục đích sử dụng.

## 9. Kiến trúc An toàn Bảo mật (Security Architecture)

- API Gateway kiểm tra Cognito JWT; Payment Service kiểm tra lại tính hợp lệ của JWT, issuer, audience, thời gian hết hạn, vai trò và phạm vi tenant/chi nhánh.
- Lệnh gọi nội bộ sử dụng trusted service context thông qua danh tính service / mTLS hoặc ngữ cảnh workload có chữ ký.
- Các trường `tenantId`, `branchId`, `orderId` và số tiền nằm trong request body không phải là bằng chứng sở hữu độc lập; bắt buộc phải đối chiếu với trusted context và snapshot bất biến từ Order.
- Webhook VNPAY IPN chỉ mở công khai tại ranh giới API Gateway; có xác thực schema, giới hạn kích thước/tần suất request, timeout và cơ chế chống phát lại.
- Checksum / chữ ký điện tử bắt buộc phải được xác thực thành công trước khi thực hiện tra cứu nghiệp vụ hoặc chuyển trạng thái từ callback.
- Khóa bí mật của VNPAY được quản lý an toàn trong AWS Secrets Manager.
- Tuyệt đối không log secret, chữ ký thô, dữ liệu chuỗi ký thô hay payload nhạy cảm của callback.
- Raw payload của callback được lưu trữ dưới dạng mã hóa hoặc thu giảm thông tin theo chính sách lưu giữ dữ liệu.
- Audit log dạng chỉ nối thêm (append-only) cho mọi bước chuyển trạng thái, callback không hợp lệ / sai lệch, đối soát, hoàn tiền và các trường hợp bị từ chối phân quyền.

## 10. Kiến trúc Khả năng Quan sát (Observability Architecture)

- Mã tương quan / truy vết (Correlation / Trace ID) được duy trì xuyên suốt: Máy POS → Order/Saga → Payment → Provider / Message Broker.
- Log có cấu trúc bắt buộc phải có `tenantId`, `branchId`, `paymentId`, `orderId`, `checkoutAttemptId`, mã tham chiếu provider (nếu an toàn), `eventId` và kết quả thực hiện; tuyệt đối không log secret hay payload nhạy cảm thô.
- Các chỉ số đo lường (Metrics):
  - Tỷ lệ khởi tạo thanh toán thành công / thất bại theo phương thức;
  - Tỷ lệ xác thực tiền mặt thất bại / phân bố tiền thừa;
  - Thời gian trễ và tỷ lệ tạo mã VNPAY QR thất bại;
  - Số lượng IPN sai chữ ký, sai lệch ánh xạ, trùng lặp hoặc sai thứ tự;
  - Thời gian chờ của payment pending, số lượng payment hết hạn, số lượng và độ tuổi của các giao dịch cần đối soát;
  - Thời gian trễ và tỷ lệ tra cứu provider thất bại;
  - Số lần thử lại outbox, độ sâu hàng đợi DLQ và độ trễ sự kiện;
  - Bộ phát hiện tác dụng phụ trùng lặp đối với sự kiện `PaymentPaid`;
  - Thuộc P1: Số lượng giao dịch hoàn tiền pending / thất bại / sai lệch.
- Thiết lập cảnh báo khi số lượng giao dịch pending hoặc cần đối soát vượt ngưỡng, hàng đợi DLQ tăng, ứ đọng outbox, lỗi kết nối provider hoặc số trường hợp từ chối chéo tenant tăng bất thường.

## 11. Xử lý Sự cố (Failure Handling)

| Tình huống sự cố | Cách thức xử lý |
|---|---|
| Khách đưa thiếu tiền mặt | Từ chối yêu cầu, không thay đổi trạng thái |
| Máy POS gửi yêu cầu thử lại (retry) | Trả về phản hồi có tính bất biến (idempotent) đã lưu trước đó |
| VNPAY gặp lỗi khi tạo mã QR | Tuyệt đối không báo `PAID`, trả về mã lỗi có thể thử lại |
| Webhook IPN sai checksum | Ghi nhận sự kiện bảo mật, không thay đổi trạng thái payment |
| Webhook IPN bị gửi trùng lặp | Xử lý No-op, trả về kết quả tương thích với nhà cung cấp |
| Gọi sang provider bị timeout | Giữ nguyên trạng thái pending, gắn cờ yêu cầu đối soát |
| Provider báo thành công khi payment đang pending | Chuyển sang `PAID`, phát ra đúng một sự kiện `PaymentPaid.v1` |
| Provider báo thành công sau khi attempt đã kết thúc | Ghi nhận `SETTLED_LATE`, không phát sự kiện `PaymentPaid.v1` |
| Xuất bản outbox gặp lỗi | Tự động thử lại kèm lùi số mũ, sau đó đưa vào hàng đợi DLQ |
| Thanh toán thành công nhưng commit tồn kho thất bại | Saga tự động thử lại; nếu thất bại hoàn toàn sẽ chuyển sang đối soát thủ công |

## 12. Các Luồng Trình tự Hướng theo Hợp đồng (Contract-Driven Sequence Flows)

Repository hiện chưa có mã nguồn triển khai để dò vết controller/consumer thời gian chạy thực tế; các luồng dưới đây được thiết kế hướng theo hợp đồng:

### 12.1 Thanh toán Tiền mặt (Cash)

1. Máy POS gửi yêu cầu checkout thông qua Order/Saga.
2. Order/Saga thực hiện giữ chỗ tồn kho trong Inventory rồi gọi lệnh khởi tạo sang Payment Service.
3. Payment Service xác thực trường `amountReceivedMinor` trong một giao dịch DB.
4. Payment Service chuyển trạng thái từ `PENDING → PAID`, lưu thông tin tiền thừa và ghi bản ghi sự kiện vào outbox.
5. Sự kiện `PaymentPaid.v1` kích hoạt bước tiếp theo của Saga; Saga gọi lệnh `CommitReservation` tới Inventory, sau đó Order Service hoàn tất đơn hàng và phát ra sự kiện `OrderPaid.v1`.

### 12.2 Thanh toán VNPAY QR và Xử lý IPN

1. Order/Saga gọi lệnh khởi tạo thanh toán sang Payment Service.
2. Payment Service tạo một bản ghi ở trạng thái `PENDING`, gọi VNPAY adapter và trả về chuỗi QR payload cùng thời gian hết hạn.
3. Cổng thanh toán VNPAY gửi webhook IPN tới API Gateway.
4. Trình xử lý IPN handler lưu callback, xác thực checksum, kiểm tra ánh xạ đơn hàng và số tiền.
5. Payment Service chuyển trạng thái từ `PENDING → PAID` trong giao dịch DB; outbox phát ra đúng một sự kiện `PaymentPaid.v1`.
6. Saga thực hiện commit tồn kho trong Inventory, Order Service hoàn tất đơn hàng và phát ra sự kiện `OrderPaid.v1`.

### 12.3 Đối soát và Quyết toán Muộn (Reconciliation / Late Settlement)

1. Bộ lập lịch Scheduler đưa các payment pending quá hạn hoặc chưa rõ trạng thái vào hàng đợi đối soát.
2. Worker nhận tác vụ bằng cơ chế khóa lease, truy vấn trạng thái từ VNPAY và thử lại có lùi số mũ.
3. Nếu provider khẳng định chắc chắn thành công trong khi payment vẫn đang pending → Chuyển sang `PAID` + phát sự kiện `PaymentPaid.v1`.
4. Nếu trạng thái vẫn chưa xác định → Giữ nguyên trạng thái, gắn cờ yêu cầu đối soát, phát cảnh báo và đưa vào hàng đợi xử lý thủ công.
5. Nếu provider báo thành công sau khi attempt đã kết thúc → Tạo bản ghi trong `late_payment_reconciliation` với trạng thái `SETTLED_LATE`, chỉ phát sự kiện `PaymentReconciliationRequired.v1` và tuyệt đối không phục hồi lại Order.

## 13. Kiến trúc Triển khai (Deployment)

### Môi trường Cục bộ / Demo

- Docker Compose vận hành Payment Service, cơ sở dữ liệu PostgreSQL, message broker cục bộ và bộ điều hợp provider giả lập (fake provider adapter).
- Dữ liệu mẫu (fixtures) có tính tiền định mô phỏng các tình huống IPN trùng lặp, sai thứ tự, đến muộn và gọi provider bị timeout.
- Bộ điều hợp VNPAY Sandbox Adapter được kích hoạt thông qua biến môi trường cấu hình mà không làm thay đổi logic tầng domain.

### Môi trường AWS Hybrid

- Triển khai Payment Service dưới dạng các tác vụ container trên AWS ECS Fargate.
- Sử dụng AWS API Gateway cho việc định tuyến yêu cầu công khai/nội bộ và kiểm soát lưu lượng (throttling).
- AWS EventBridge định tuyến các sự kiện tới các hàng đợi SQS của consumer và hàng đợi DLQ.
- AWS EventBridge Scheduler định kỳ kích hoạt hàng đợi đối soát.
- AWS Secrets Manager lưu trữ thông tin đăng nhập và signing secret của VNPAY.
- AWS CloudWatch, OpenTelemetry và AWS X-Ray chịu trách nhiệm thu thập logs, metrics và tracing.
- Quản lý toàn bộ tài nguyên hạ tầng môi trường bằng Terraform.

## 14. Các Bản ghi Quyết định Kiến trúc (ADRs)

| Mã | Quyết định | Các giải pháp thay thế | Cơ sở lý luận |
|---|---|---|---|
| PAY-ADR-01 | Cơ sở dữ liệu PostgreSQL riêng biệt cho Payment | Dùng chung DB với Order, dùng NoSQL ledger | Giao dịch mạnh (ACID) phù hợp hoàn hảo với tiền tệ/trạng thái/idempotency và phân định quyền sở hữu dữ liệu rõ ràng |
| PAY-ADR-02 | Bộ điều hợp provider kết hợp các cổng chuẩn hóa | Đưa trực tiếp các trường VNPAY vào domain | Cô lập hoàn toàn hành vi đặc thù của sandbox/provider và dễ dàng hỗ trợ bộ điều hợp giả lập khi test |
| PAY-ADR-03 | AWS Step Functions + REST nội bộ cho lệnh điều phối | Dùng Choreography thuần túy, truy cập DB trực tiếp | Quy trình Saga đòi hỏi điều phối tuần tự, quản lý timeout, bù trừ và khả năng truy vết rõ ràng |
| PAY-ADR-04 | Transactional Outbox → EventBridge/SQS | Xuất bản trực tiếp sau commit, 2PC | Tránh lỗi ghi hai lần (dual-write) và hỗ trợ cơ chế thử lại / phát lại sự kiện an toàn |
| PAY-ADR-05 | Khóa attempt kết thúc + bản ghi quyết toán muộn riêng | Ghi đè trạng thái kết thúc trở lại thành `PAID` | Ngăn chặn việc làm sống lại Order và bảo toàn bằng chứng tài chính bất biến |
| PAY-ADR-06 | Định danh UUID v7 + số nguyên đơn vị minor unit | Dùng UUID v4, số thực float/decimal | Hỗ trợ chỉ mục sắp xếp theo thời gian và số học tiền tệ có tính tiền định tuyệt đối |
| PAY-ADR-07 | Không truy cập cơ sở dữ liệu chéo service | Dùng chung schema, join truy vấn trực tiếp | Giữ vững ranh giới Bounded Context và tính độc lập khi migration cơ sở dữ liệu |

## 15. Các Sản phẩm Bàn giao Triển khai Được Hoãn lại

- Các biểu đồ trình tự PlantUML L1/L2 sẽ được tạo sau khi các hợp đồng API/sự kiện có định nghĩa endpoint/topic/schema cụ thể; hiện tại chỉ duy trì các luồng hướng theo hợp đồng để tránh giả định sai các thành phần hoặc lệnh gọi thời gian chạy.
- Các file tài liệu OpenAPI, AsyncAPI và migration schema sẽ được hiện thực hóa ở giai đoạn Hợp đồng Dữ liệu & API (Contracts).
- Thời gian lưu trữ / cam kết SLA đối soát, múi giờ nghiệp vụ cho báo cáo và các sửa đổi bổ sung cho hợp đồng cha bắt buộc phải được ghi rõ trong hợp đồng trước khi bắt tay vào viết code.
