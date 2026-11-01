# Module 4 — Payment Service: Kết Quả Thiết Kế (Design Result)

**Ngày:** 2026-10-12  
**Trạng thái:** Thiết kế hoàn tất — Phê duyệt có cảnh báo (Approved with warnings)  
**Phạm vi:** M — Luồng Service Tiêu chuẩn (Standard Service Flow)

## 1. Các Artifact Đã Hoàn Tất

1. [Tầm nhìn (Vision)](./2026-10-12-vision.md)
2. [Yêu cầu nghiệp vụ (Requirements)](./2026-10-12-requirements.md)
3. [Đánh giá yêu cầu nghiệp vụ (Requirements Sentinel Review)](./2026-10-12-requirements-review.md)
4. [Kiến trúc hệ thống (Architecture)](./2026-10-12-architecture.md)
5. [Hợp đồng API & Dữ liệu (API & Data Contracts)](./2026-10-12-contracts.md)
6. [Đánh giá hợp đồng kỹ thuật (Contracts Sentinel Review)](./2026-10-12-contracts-review.md)

---

## 2. Các Quyết Định Đã Chốt

- **Cơ sở dữ liệu độc lập:** Sử dụng PostgreSQL riêng cho Payment Service; trạng thái thanh toán, idempotency key, audit callback, reconciliation và transactional outbox được commit hoàn toàn atomic.
- **Thanh toán tiền mặt (Cash):** Xử lý đồng bộ (synchronous), xác thực nghiêm ngặt số tiền khách đưa (`amount_tendered >= final_amount`), tự động tính tiền thừa trả khách (`change_amount`) bằng đơn vị số nguyên VND (minor unit).
- **VNPAY Sandbox QR Động:** Tạo dynamic QR code với quy tắc duy nhất một attempt `PENDING` hoạt động tại một thời điểm cho mỗi lần checkout.
- **VNPAY IPN Chuẩn hóa:** Endpoint canonical là `GET /callbacks/v1/vnpay/ipn`, nhận query parameters `vnp_*`, xác thực chữ ký số (checksum HMAC-SHA512) trước khi thực hiện bất kỳ xử lý nghiệp vụ nào.
- **Phân tách Provider Adapter:** Lớp Domain Payment được cô lập hoàn toàn khỏi định dạng đặc thù của VNPAY thông qua cổng chuẩn hóa (normalized ports/adapters).
- **Điều phối Saga Checkout:** AWS Step Functions đóng vai trò orchestrator điều phối luồng Saga; Payment Service không tự ý finalize Order hay truy vấn trực tiếp cơ sở dữ liệu của Order.
- **Transactional Outbox:** Cơ chế Outbox → EventBridge → SQS đảm bảo gửi event theo mô hình at-least-once.
- **Tách biệt Sự kiện Thanh toán và Đơn hàng:** `PaymentPaid` chỉ đại diện cho sự kiện thanh toán thành công trong Saga; `OrderPaid` do Order Service phát hành sau khi Inventory Service commit trừ kho thành công và Order hoàn tất finalization.
- **Xử lý IPN trễ (Late Provider Success):** Nếu IPN báo thành công sau khi attempt đã bị terminal (hết hạn/hủy), hệ thống ghi nhận vào `late_payment_reconciliation` với trạng thái `SETTLED_LATE`; tuyệt đối không phát hành `PaymentPaid`/`OrderPaid` và không tự tiện mở lại đơn hàng.
- **Phân trang & Caching:** Áp dụng phân trang dựa trên con trỏ (Cursor pagination) cho lịch sử thanh toán và đối soát; không cache các trạng thái thanh toán nhạy cảm P0.
- **Chuẩn dữ liệu & Bảo mật:** Sử dụng UUID v7 cho toàn bộ khóa chính, `BIGINT` cho tiền tệ (VND minor unit), kiểm tra ngữ cảnh `tenant_id` và `branch_id` đa tầng phòng thủ chuyên sâu.
- **Tính năng P1 (Split Tender / Refund):** Thiết kế thành các bảng thực thể và state machine độc lập, không ghi đè hay sửa đổi sổ cái thanh toán (payment ledger) gốc.

---

## 3. Cảnh Báo & Các Điều Kiện Tiên Quyết Triển Khai (Implementation Gates)

| Gate | Nội dung kiểm tra | Điều kiện hoàn tất |
|---|---|---|
| **Parent contracts** | Cập nhật bộ hợp đồng cha OpenAPI/AsyncAPI/DB: IPN GET method, trường nhà cung cấp nullable cho CASH, các trường chi tiết tiền mặt, sự kiện đối soát và khóa duy nhất phạm vi theo provider. | Đánh giá hợp đồng cha được thông qua + các fixture test sinh tự động đều pass |
| **VNPAY sandbox** | Xác nhận cấu hình merchant thực tế, mã hóa query canonical, lựa chọn thuật toán hash (SHA256 vs HMAC-SHA512), bảng ánh xạ mã phản hồi `RspCode`, cơ chế query trạng thái giao dịch và hành vi retry của gateway. | Bộ dữ liệu sanitized fixtures hoàn chỉnh + test hợp đồng adapter pass |
| **Gateway privacy** | Áp dụng chính sách che mờ (redact) toàn bộ tham số `vnp_*`, chuỗi secure hash, thông tin đơn hàng nhạy cảm và nội dung callback thô khỏi access log cũng như application logs. | Kiểm tra an toàn bảo mật và kiểm tra định dạng log pass |
| **Reconciliation operations** | Cụ thể hóa ngưỡng retry đối soát, thời hạn lease lock, cơ chế cảnh báo tự động, phân định trách nhiệm vận hành, SLA xử lý chênh lệch, thời gian lưu trữ dữ liệu và tài liệu runbook vận hành. | File cấu hình deployment sẵn sàng + runbook vận hành được duyệt |
| **Reporting read model** | Bổ sung metadata múi giờ chi nhánh, ngày kế toán/nghiệp vụ, độ trễ dữ liệu và tổng hợp đa chi nhánh vào read model của Reporting Service. | Schema consumer của Reporting Service và bộ test tích hợp được phê duyệt |
| **P1 refund** | Kiểm tra năng lực hoàn tiền và tra soát giao dịch thực tế của VNPAY, cũng như hành vi khi timeout trước khi bật tính năng hoàn tiền cho người dùng. | Ma trận tính năng provider + fixture kiểm thử luồng hoàn tiền |

---

## 4. Các Kịch Bản Kiểm Thử Bắt Buộc Trước Khi Ký Duyệt Triển Khai

- **Thanh toán tiền mặt:** Tiền khách đưa không đủ, trả đúng số tiền, khách đưa dư/tính đúng tiền thừa và kịch bản POS retry trùng lặp.
- **Khởi tạo VNPAY:** Khởi tạo đồng thời nhiều giao dịch và kiểm tra tính duy nhất của mã QR đang active.
- **Bảo mật Callback:** Sai chữ ký checksum, chữ ký đúng nhưng sai thông tin ánh xạ, sai lệch số tiền thanh toán, và payload callback bị lỗi format.
- **Hành vi IPN:** IPN bị gửi trùng lặp (duplicate), IPN đến sai thứ tự (out-of-order), IPN timeout từ phía nhà cung cấp, và IPN đến muộn sau khi đơn đã hủy (late IPN).
- **Xử lý đối soát:** Tranh chấp quyền xử lý (lease race condition), cơ chế retry với exponential backoff, xử lý kết quả thành công/thất bại xác định, provider không phản hồi và replay qua DLQ.
- **Nguyên tắc IPN trễ:** Trạng thái `SETTLED_LATE` tuyệt đối không bao giờ được phát hành `PaymentPaid` hoặc `OrderPaid`.
- **An toàn Saga:** Thanh toán thành công nhưng commit kho thất bại không bao giờ được tự ý âm thầm hoàn tất đơn hàng.
- **Phòng thủ đa tenant:** Thử nghiệm truy cập chéo tenant/chi nhánh, kiểm tra chặn quyền hạn RBAC, tấn công IDOR và xác minh log không rò rỉ secret hoặc payload thô.
- **Độ tin cậy sự kiện:** Thử nghiệm Outbox retry khi mất kết nối mạng, deduplication phía consumer và cơ chế cách ly kiểm dịch (quarantine) các phiên bản event không tương thích.
- **Tính năng P1:** Kiểm tra hạn mức phân bổ thanh toán hỗn hợp (split tender) và tính idempotent cùng hạn mức số dư khi hoàn tiền.

---

## 5. Bàn Giao (Handoff)

Quy trình thiết kế cho Module 4 — Payment Service đã hoàn thành. Bước tiếp theo là lập kế hoạch triển khai kỹ thuật sau khi các sửa đổi hợp đồng cha và các fixture VNPAY sandbox ở mục 3 được nghiệm thu. Không có mã nguồn ứng dụng nào được tạo trong giai đoạn thiết kế này.
