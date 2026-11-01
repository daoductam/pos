Started at: 2026/10/12 16:35:00 +07
Finished at: 2026/10/12 16:35:00 +07
Total time: 0 minutes
---

# Kết quả Thiết kế: Order Service (Dịch vụ Đơn hàng)

## Kết luận Cuối cùng (Final Verdict)

**PHÊ DUYỆT CÓ CẢNH BÁO (APPROVED WITH WARNINGS) — sẵn sàng cho triển khai sau khi hoàn thành các cổng kiểm soát trước khi viết code (pre-coding gates) dưới đây.**

Năm giai đoạn thiết kế đã hoàn thành. Ranh giới service của Order, máy trạng thái, chính sách snapshot Catalog, quy trình Saga trên AWS Step Functions, mô hình bù trừ, luồng đối soát thanh toán và việc cô lập tenant/chi nhánh đều hoàn toàn mạch lạc và nhất quán. Không tìm thấy vấn đề chặn đứng nghiêm trọng nào về an toàn bảo mật hay tính toàn vẹn trạng thái trong các tài liệu. Các cảnh báo còn lại là các cổng kiểm soát tích hợp/phát hành, đặc biệt là các sửa đổi hợp đồng cha và các dữ liệu mẫu kiểm thử (fixtures) có thể thực thi cho Saga/thanh toán/tồn kho.

## Phạm vi Thiết kế

| Hạng mục | Kết quả |
|---|---|
| Module | Module 3 — Bán hàng / Order Service (Dịch vụ Bán hàng / Đơn hàng) |
| Quy trình (Workflow) | Quy trình Service Chuẩn, phạm vi M (Standard Service Flow, scope M) |
| Phạm vi P0 | Đơn tại quầy / mang đi / tại bàn, snapshot Catalog, sửa đơn nháp, Quản lý / Chủ sở hữu hủy dòng món, checkout Saga tiền mặt / VNPAY, bù trừ, tích hợp sự kiện và audit |
| Phạm vi P1 | Sơ đồ bàn, giữ/tiếp tục đơn, tách/gộp hóa đơn, KDS WebSocket, tính tương thích hoàn tiền / đảo ngược giao dịch sau thanh toán |
| Phạm vi P2 | Khách hàng tự gọi món qua mã QR tại bàn |
| Lưu trữ dữ liệu | Cơ sở dữ liệu quan hệ PostgreSQL chuẩn hóa do service tự sở hữu |
| Điều phối Saga | AWS Step Functions hoặc bộ điều hợp cục bộ tương thích hoàn toàn về mặt ngữ nghĩa |
| Stack mục tiêu | Java 21, Spring Boot 3.x, REST/OpenAPI 3.1, AsyncAPI/JSON Schema, UUID v7, tiền tệ số nguyên VND |
| Trạng thái cuối cùng | Phê duyệt có cảnh báo (Approved with warnings) |

## Các Tài liệu Thiết kế (Artifacts)

1. [Tầm nhìn (Vision)](./2026-10-12-vision.md) — Đã phê duyệt.
2. [Đánh giá Tầm nhìn (Vision review)](./2026-10-12-vision-review.md) — Các cảnh báo đã được chấp nhận và giải quyết thành các yêu cầu.
3. [Yêu cầu Kỹ thuật (Requirements)](./2026-10-12-requirements.md) — Đã phê duyệt.
4. [Kiến trúc Kỹ thuật (Architecture)](./2026-10-12-architecture.md) — Đã phê duyệt.
5. [Hợp đồng Dữ liệu & API (Contracts)](./2026-10-12-contracts.md) — Đã phê duyệt.
6. Bản [Kết quả Thiết kế](./2026-10-12-order_result.md) này — Phê duyệt có cảnh báo.

## Tóm tắt Kiến trúc Cuối cùng

```text
POS → API Gateway → Order Service → Order PostgreSQL
                         │             ├─ Snapshot Catalog bất biến
                         │             ├─ Lượt thử checkout (checkout attempts)
                         │             ├─ Trạng thái idempotency / phiên bản
                         │             └─ Transactional outbox
                         │
                         ├─ Quyết định phân quyền từ Identity
                         ├─ Bộ phân giải Catalog
                         └─ Step Functions Checkout Saga
                              ├─ Inventory reserve / commit / release
                              ├─ Payment tiền mặt / VNPAY / pending / IPN
                              └─ Order finalize / fail / reconcile

Sự kiện Order → EventBridge → SQS/DLQ → Loyalty / Reporting / Audit / KDS(P1)
```

Các quyết định mang tính thẩm quyền:

- Order sở hữu vòng đời đơn hàng, trạng thái dòng món, snapshot Catalog bất biến, snapshot các khoản tổng và các lượt checkout attempts.
- Trạng thái `DRAFT` có thể chỉnh sửa bởi Cashier/Manager/Owner được ủy quyền trong ngữ cảnh chi nhánh; tương tranh lạc quan ngăn chặn việc ghi đè âm thầm.
- Owner/Manager có thể hủy một dòng món đang active chỉ trong `DRAFT`; Cashier không được phép hủy. Việc hủy dòng món active cuối cùng sẽ tự động hủy đơn Order một cách nguyên tử.
- Quá trình Checkout phân giải lại với Catalog; nếu thực đơn/giá/cấu hình bị cũ sẽ trả về `catalog_changed` và đòi hỏi phải xác nhận tính lại giá rõ ràng.
- Step Functions điều phối chuỗi `reserve → payment → commit → finalize`, với các tác vụ bất biến, timeout, thử lại và bù trừ.
- VNPAY pending giữ chỗ tồn kho trong 15 phút; thanh toán thành công đến muộn sau khi đã giải phóng tồn kho không bao giờ làm sống lại Order.
- Thanh toán thành công nhưng commit tồn kho thất bại sẽ chuyển thành ngoại lệ đối soát, tuyệt đối không âm thầm chuyển sang `PAID`.
- `OrderPaid` là sự thật đơn hàng đã thanh toán chuẩn mực; sự kiện PaymentPaid không được tiêu thụ trực tiếp để tích điểm Loyalty hay ghi nhận doanh thu Reporting.
- Các sự kiện / bản ghi audit của Order sử dụng transactional outbox; các consumer đạt tính nhất quán cuối cùng và khử trùng lặp theo event ID.

## Báo cáo Đánh giá Thiết kế (Design Review Report)

**Phạm vi:** Năm tài liệu thiết kế của Order cộng với các hợp đồng cha trực tiếp của Identity, Catalog và POS Platform.

**Chế độ đánh giá:** Đánh giá tính mạch lạc của tài liệu dựa trên danh mục kiểm tra code-reviewer; không có mã nguồn triển khai Order nào trong repository.

### Các Vấn đề Nghiêm trọng (Critical Issues)

Không tìm thấy.

### Các Cảnh báo (Warnings)

| # | Tệp:Dòng | Hạng mục | Vấn đề | Khuyến nghị |
|---|---|---|---|---|
| 1 | `docs/designs/order/2026-10-12-contracts.md:562` | Tích hợp | Hợp đồng cha vẫn cần bổ sung biến thể cụ thể, mở rộng phản hồi checkout, commit Inventory, ràng buộc hủy món và định tuyến sự kiện Order. | Áp dụng và kiểm thử hợp đồng các sửa đổi đã liệt kê trước khi triển khai lát cắt dọc chéo service đầu tiên. |
| 2 | `docs/designs/order/2026-10-12-contracts.md:595` | Bàn giao | OpenAPI, AsyncAPI, JSON Schema và định nghĩa Step Functions đã được mô tả nhưng chưa được hiện thực hóa thành file. | Sinh và xác thực chúng trong CI trước khi triển khai controller, DTO Angular hay các tác vụ Saga. |
| 3 | `docs/designs/order/2026-10-12-requirements.md:116` | Tài chính / Sự cố | Thanh toán có thể thành công trong khi commit tồn kho thất bại; P0 chưa có tính năng tự động hoàn tiền. | Triển khai sổ tay vận hành (runbook) đối soát và dữ liệu mẫu kiểm thử sự cố; khóa checkout mới và không bao giờ tự ý đánh dấu `PAID`. Xác nhận kênh hoàn tiền/vận hành của Payment trước buổi demo. |
| 4 | `docs/designs/order/2026-10-12-requirements.md:102` | Chính sách Nghiệp vụ | P0 mặc định VAT bằng 0 và để ngỏ việc tính VAT khác 0 phía sau một hợp đồng chính sách tenant trong tương lai. | Thu thập xác nhận về nghiệp vụ/thuế trước khi kích hoạt VAT khác 0; duy trì sẵn các bài kiểm thử làm tròn số nguyên có tính tiền định. |
| 5 | `docs/designs/order/2026-10-12-architecture.md:444` | Khả năng Truy vết | Các biểu đồ trình tự L1/L2 thời gian chạy bị hoãn lại do chưa có controller/adapter/listener nào tồn tại. | Sinh và xác nhận các vết PlantUML ngay sau khi khung dự án service và các hợp đồng Saga đi vào hoạt động. |

Các cảnh báo này không yêu cầu phải thiết kế lại. Chúng xác định con đường an toàn tối thiểu từ các tài liệu đã duyệt tới việc tích hợp có thể thực thi được.

### Tóm tắt Danh mục Kiểm tra (Checklist Summary)

| Hạng mục | Kết luận | Số lượng vấn đề |
|---|---|---:|
| An toàn Bảo mật | ĐẠT KÈM CẢNH BÁO | 1 |
| Logic Nghiệp vụ | ĐẠT KÈM CẢNH BÁO | 2 |
| Hiệu năng | ĐẠT | 0 |
| Thiết kế Sạch sẽ | ĐẠT | 0 |
| Quy ước | ĐẠT KÈM CẢNH BÁO | 2 |

Cảnh báo bảo mật chỉ giới hạn ở việc áp dụng các sửa đổi phân quyền/sự kiện cha đã được tài liệu hóa. Các cảnh báo nghiệp vụ bao gồm quy trình đối soát thanh toán / tồn kho và xác nhận chính sách VAT; cả hai đều tường minh và có thể kiểm thử được.

## Ma trận Truy xuất Nguồn gốc (Traceability Matrix)

| Khu vực yêu cầu | Bên sở hữu kiến trúc | Bằng chứng hợp đồng | Bằng chứng nghiệm thu |
|---|---|---|---|
| Đơn tại quầy / mang đi / tại bàn | Module bản nháp và dòng món | CreateOrderRequest, chế độ phục vụ và tham chiếu bàn | Kiểm thử tạo đơn theo các chế độ phục vụ |
| Sửa nháp / tương tranh | Order DB / module phiên bản | PATCH / thêm dòng, `If-Match`, `order_version_conflict` | Kiểm thử cập nhật POS đồng thời |
| Hủy dòng món / audit | Vòng đời dòng món + outbox | Endpoint hủy món, lý do, sự kiện audit | Kiểm thử RBAC / dòng cuối cùng / audit |
| Snapshot Catalog | Bộ điều hợp Catalog | Lựa chọn của resolver, snapshot dòng món, tính lại giá/catalog_changed | Kiểm thử lệch giá / cấu hình |
| Tiền tệ / các khoản tổng | Bộ xác thực tổng tiền | MoneyTotals, số nguyên VND, phiên bản chính sách định giá | Kiểm thử tiền mặt / tiền thừa / làm tròn |
| Checkout Saga | Step Functions + module attempt | CheckoutResponse, các API nội bộ finalize/fail/compensation | Kiểm thử luồng thành công / thất bại / thử lại |
| Giữ chỗ tồn kho | Ranh giới tác vụ Inventory | Tham chiếu reserve/commit/release | Kiểm thử tồn kho không đủ / lệnh trùng lặp |
| Thanh toán / IPN | Ranh giới tác vụ Payment / đối soát | Callback pending/paid/late-payment | Kiểm thử IPN trùng lặp / sai thứ tự / đến muộn |
| Sự thật thanh toán chuẩn mực | Outbox của Order | Lược đồ `OrderPaid.v1` | Kiểm thử phát lại / khử trùng lặp ở Loyalty/Reporting |
| Cô lập tenant/chi nhánh | Adapter Identity + repositories | Header phân quyền/phạm vi và ngữ cảnh nội bộ | Kiểm thử IDOR / thu hồi tư cách thành viên |
| Khả năng quan sát | Đo đạc viễn thông Outbox/Saga | Các trường correlation/saga/attempt và metrics | Bằng chứng trace / làm sạch dữ liệu / DLQ |
| Tính tương thích P1/P2 | Các adapter kiểm soát bằng feature flag | Các đường dẫn giữ chỗ bàn/giữ đơn/tách đơn/KDS/QR | Kiểm thử hồi quy P0 cộng với kiểm thử tính năng sau |

## Các Cổng Kiểm soát Trước khi Viết Code (Pre-Coding Gates)

1. Áp dụng các sửa đổi hợp đồng cha: `variantId` cụ thể, đặt tên tùy chọn modifier, mở rộng phản hồi checkout, kết quả commit Inventory, ràng buộc hủy món và định tuyến sự kiện chuẩn mực.
2. Đăng ký các quyền `order:read`, `order:create`, `order:edit`, `order:checkout`, `order:cancel-line` và `order:resolve` với Identity mà không sao chép trạng thái thành viên vào Order.
3. Sinh các file `openapi.yaml`, `asyncapi.yaml`, JSON Schema và các model client Angular có định kiểu.
4. Hiện thực hóa máy trạng thái AWS Step Functions với các khóa idempotency có tính tiền định cho từng tác vụ, các đường dẫn timeout/retry/catch/compensation và tương quan callback.
5. Chứng minh hành vi Catalog → Snapshot Order → Inventory reserve/commit/release → Payment pending/paid/late thông qua các dữ liệu mẫu kiểm thử hợp đồng.
6. Xác nhận sổ tay vận hành (runbook) đối soát trường hợp thanh toán thành công nhưng commit tồn kho thất bại và chính sách VAT P0 trước khi bật đường dẫn checkout demo.
7. Duy trì việc tạo các biểu đồ trình tự PlantUML thời gian chạy như một cổng kiểm soát sau khi controller, bộ điều hợp tác vụ và event listener đã có thể dò vết được.

## Lộ trình Triển khai (Implementation Roadmap)

### Giai đoạn 0 — Đồng bộ hợp đồng và thử nghiệm các kịch bản sự cố (Failure Spikes)

**Mục tiêu:** Làm cho ngữ nghĩa chéo service có thể thực thi được trước khi triển khai domain.

- Áp dụng các sửa đổi hợp đồng cha và đăng ký phân quyền Identity.
- Sinh và xác thực OpenAPI/AsyncAPI/JSON Schema cùng các client có định kiểu.
- Xây dựng bộ điều hợp Step Functions / điều phối cục bộ tối thiểu với một luồng thực thi thành công và một luồng bù trừ.
- Tạo các dữ liệu mẫu kiểm thử Payment/Inventory cho các trường hợp trùng lặp, timeout, IPN sai thứ tự và đối soát khi commit thất bại.
- Xác nhận chính sách tiền số nguyên / VAT và công bố sổ tay vận hành cho nhân viên.

**Cổng ra:** Xác thực hợp đồng thành công và một phiên Saga có tính tiền định chứng minh được luồng thành công, thanh toán lỗi/giải phóng và thử lại bù trừ mà không gây tác dụng phụ trùng lặp.

### Giai đoạn 1 — Nền tảng service và lưu trữ Order

**Mục tiêu:** Thiết lập trạng thái Order an toàn, có phạm vi theo tenant.

- Khởi tạo khung dự án Order Service chạy Java 21 / Spring Boot, Docker Compose profile và các quy ước cấu hình.
- Bổ sung Flyway migration cho orders, lines, snapshot modifier, checkout attempts, transitions, idempotency và outbox.
- Triển khai adapter phân quyền Identity, `TenantBranchScope`, mã lỗi chuẩn, correlation và xử lý ETag/phiên bản.
- Bổ sung các mệnh đề tenant/chi nhánh tại repository, cơ chế sinh số hiệu đơn hàng theo chi nhánh và dữ liệu mẫu có tính tiền định.

**Cổng ra:** Các bài kiểm thử chéo tenant/chi nhánh, thu hồi tư cách thành viên, phiên bản lỗi thời và tính bất biến đều vượt qua.

### Giai đoạn 2 — Lát cắt dọc Đơn nháp và Snapshot Catalog

**Mục tiêu:** Giúp việc tạo và chỉnh sửa đơn nháp trên POS trở nên đáng tin cậy.

- Triển khai tạo/liệt kê/đọc/cập nhật metadata đơn hàng.
- Triển khai thêm/sửa dòng món và ánh xạ snapshot từ Catalog resolver.
- Triển khai tính toán các khoản tổng số nguyên và xem trước / xác nhận tính lại giá rõ ràng.
- Triển khai hủy dòng món, hủy đơn hàng kết thúc khi hủy dòng cuối cùng và sự kiện audit outbox.
- Xây dựng các trạng thái giỏ hàng / đơn nháp / lỗi trên Angular từ các kiểu client sinh tự động.

**Cổng ra:** Các bài kiểm thử nghiệm thu đơn nháp tại quầy / mang đi / tại bàn, chỉnh sửa đồng thời, thay đổi thực đơn và RBAC/audit khi hủy món đều vượt qua.

### Giai đoạn 3 — Checkout Saga và các luồng thanh toán P0

**Mục tiêu:** Hoàn thành quy trình checkout tiền mặt và VNPAY kèm theo bù trừ.

- Triển khai giao dịch tiền điều kiện checkout và tạo attempt.
- Triển khai các tác vụ Step Functions cho việc xác thực Catalog, giữ chỗ Inventory, khởi tạo/xác nhận Payment, commit/release Inventory và hoàn tất/thất bại Order.
- Triển khai tính tiền thừa tiền mặt và tương quan VNPAY pending/hết hạn/IPN thông qua ranh giới Payment.
- Triển khai trạng thái bù trừ / khóa thử lại, hàng đợi DLQ và trạng thái đối soát thanh toán muộn.
- Bổ sung giao diện checkout pending / thất bại / thử lại và các hành động khôi phục.

**Cổng ra:** Các trường hợp tiền mặt và QR thành công, Payment thất bại, tồn kho không đủ, timeout, IPN trùng lặp, IPN đến muộn và commit tồn kho thất bại đều có bằng chứng tiền định và không bao giờ nhân bản `OrderPaid`.

### Giai đoạn 4 — Sự kiện, audit, reporting và độ tin cậy

**Mục tiêu:** Đảm bảo các hệ quả hạ nguồn có thể quan sát được và an toàn khi phát lại.

- Xuất bản các sự kiện vòng đời, hủy món, checkout thất bại và đơn đã thanh toán thông qua outbox/EventBridge/SQS.
- Bổ sung dữ liệu mẫu kiểm thử khử trùng lặp và chỉ số độ tươi mới của bảng chiếu cho Loyalty/Reporting/Audit.
- Bổ sung log có cấu trúc, trace X-Ray/OpenTelemetry, metrics dashboard và cảnh báo cho Saga/pending/DLQ/đối soát.
- Chạy kịch bản k6 checkout, khởi động lại service, trễ message broker, phát lại DLQ và các kịch bản bù trừ thất bại.

**Cổng ra:** Các schema/phát lại sự kiện P0, bằng chứng audit, tương quan trace và các mục tiêu độ tin cậy đều đạt yêu cầu.

### Giai đoạn 5 — Các phần mở rộng P1/P2

**Mục tiêu:** Mở rộng Order mà không làm thay đổi ngữ nghĩa vòng đời/snapshot của P0.

- Bổ sung tính năng giữ/tiếp tục đơn và danh mục bàn/trạng thái có khách phía sau các feature flag.
- Bổ sung các bất biến tách/gộp hóa đơn và liên kết audit.
- Bổ sung sự kiện món ăn bền bỉ cho KDS và bộ điều hợp WebSocket tự kết nối lại / phát lại.
- Bổ sung hợp đồng hoàn tiền / đảo ngược giao dịch sau thanh toán với Payment/Loyalty.
- Bổ sung các phiên tự gọi món qua QR với cơ chế phân quyền khách hàng riêng và chống giả mạo.

**Cổng ra:** Các client P0 vẫn hoàn toàn tương thích, các feature flag tắt theo mặc định và mọi phần mở rộng đều tuân thủ các quy tắc sở hữu/idempotency của Order.

## Phân rã Công việc Đề xuất (Work Breakdown)

| Mã | Hạng mục công việc | Phụ thuộc | Mức độ ưu tiên |
|---|---|---|---|
| ORD-01 | Sửa đổi hợp đồng cha và đăng ký phân quyền Identity | Chủ sở hữu Nền tảng + Identity | Cổng P0 (P0 gate) |
| ORD-02 | Các schema OpenAPI/AsyncAPI và client Angular sinh tự động | ORD-01 | Cổng P0 (P0 gate) |
| ORD-03 | Máy trạng thái Step Functions và thử nghiệm adapter Saga cục bộ | ORD-01, ORD-02 | Cổng P0 (P0 gate) |
| ORD-04 | Dữ liệu mẫu kiểm thử sự cố Payment/Inventory và đối soát | ORD-03 | Cổng P0 (P0 gate) |
| ORD-05 | Khung dự án Order Service, ngữ cảnh auth, lỗi và correlation | ORD-02 | P0 |
| ORD-06 | Lược đồ Flyway, snapshots, attempts, ràng buộc và chỉ mục | ORD-05 | P0 |
| ORD-07 | Các API đơn nháp/đơn/dòng món và tích hợp Catalog snapshot | ORD-06 | P0 |
| ORD-08 | Tính lại giá, hủy món, trạng thái kết thúc dòng cuối và outbox audit | ORD-07 | P0 |
| ORD-09 | Lệnh checkout, phản hồi tiền mặt và VNPAY pending | ORD-03, ORD-07 | P0 |
| ORD-10 | Tích hợp tác vụ reserve/commit/release Inventory và Payment | ORD-03, ORD-04, ORD-09 | P0 |
| ORD-11 | Sự kiện OrderPaid, hợp đồng consumer, metrics và kiểm thử sự cố | ORD-08, ORD-10 | P0 |
| ORD-12 | Bàn ăn P1 / giữ đơn / tách gộp / KDS và gọi món qua QR ở P2 | ORD-11 | P1/P2 |

## Tiêu chí Hoàn thành (Definition of Done) cho P0

- Toàn bộ các yêu cầu P0 và bài kiểm thử nghiệm thu trong [Yêu cầu Kỹ thuật](./2026-10-12-requirements.md) đều vượt qua.
- Các hợp đồng OpenAPI/AsyncAPI/JSON Schema công khai và nội bộ được xác thực trong CI; các kiểu Angular sinh tự động được các client sử dụng.
- Mọi endpoint công khai/nội bộ đều xác thực phạm vi Identity hiện tại và lặp lại kiểm tra quyền sở hữu tenant/chi nhánh tại tầng repository/service.
- Các snapshot Order/dòng món và checkout attempts là bất biến theo đúng đặc tả; chuyển đổi trạng thái và hoàn tất `OrderPaid` có tính bất biến.
- Chỉnh sửa đơn nháp sử dụng `If-Match`; hủy dòng món đòi hỏi quyền Owner/Manager, lý do và phát sinh sự kiện audit outbox.
- Các luồng tiền mặt/VNPAY thành công, timeout giữ chỗ tồn kho, bù trừ thử lại/DLQ, IPN muộn và commit tồn kho thất bại đều có bằng chứng tự động.
- Các bản ghi Order, outbox và idempotency được lưu trữ nguyên tử; tuyệt đối không có truy vấn cơ sở dữ liệu hay khóa ngoại chéo service.
- Sự kiện `OrderPaid` chỉ phát ra một lần và là sự thật đơn đã thanh toán duy nhất được Loyalty/Reporting sử dụng.
- Log/trace/metrics có cấu trúc mang đầy đủ định danh correlation, tenant, branch, order, attempt và Saga mà không làm rò rỉ bí mật/PII.
- Ngữ nghĩa giữa môi trường Docker Compose cục bộ và AWS mục tiêu khớp hoàn toàn cho các hợp đồng API, sự kiện và Saga.
- Các tính năng P1/P2 bị vô hiệu hóa hoặc không xuất hiện trong đường dẫn chạy thời gian thực của P0.

## Các Mục tiêu Bị Loại trừ Tường minh (Non-Goals)

- Không để Order sở hữu trạng thái thực đơn/giá/modifier của Catalog, trạng thái tồn kho/công thức của Inventory, trạng thái nhà cung cấp của Payment hay sổ cái của Loyalty.
- Không cho phép Thu ngân (Cashier) hủy dòng món hoặc thực hiện chỉnh sửa công khai trong trạng thái `CHECKOUT_PENDING`.
- Không tự động phục hồi lại một Order sau khi thanh toán muộn hoặc âm thầm đánh dấu `PAID` sau khi commit tồn kho thất bại.
- Không sử dụng số thực dấu phẩy động, tổng tiền do client gửi lên, tác nhân/vai trò do client tự xưng, hoặc các vòng lặp thử lại vô hạn.
- Không để lộ callback thanh toán thô, chữ ký, JWT, bí mật hoặc PII khách hàng không cần thiết trong phản hồi/sự kiện/log của Order.
- Không biến tính năng bàn ăn P1, tách/gộp đơn, KDS WebSocket hay luồng QR P2 thành điều kiện tiên quyết cho việc checkout P0.

## Bản ghi Phê duyệt Cuối cùng

- **Trạng thái:** PHÊ DUYỆT CÓ CẢNH BÁO (APPROVED WITH WARNINGS)
- **Giai đoạn thiết kế:** Hoàn tất giai đoạn Hoàn thiện (Finalization complete)
- **Vấn đề nghiêm trọng:** 0
- **Cảnh báo:** 5, được theo dõi ở trên dưới dạng các cổng kiểm soát trước khi viết code / tích hợp
- **Mức độ sẵn sàng triển khai:** Sẵn sàng sau khi hoàn thành các Cổng kiểm soát 1–7
- **Hoạt động tiếp theo:** Thực hiện lộ trình triển khai, bắt đầu với ORD-01 đến ORD-04

## Các Kỹ năng Chuyên môn được Tham chiếu

- **code-reviewer:** Thực hiện đánh giá tính mạch lạc của thiết kế cuối cùng đối chiếu với các danh mục kiểm tra an toàn bảo mật, logic nghiệp vụ, hiệu năng và quy ước; không có mã nguồn triển khai nào.
- **api-design:** Xác thực REST công khai/nội bộ, phân trang, mô hình lỗi, xác thực phân quyền, idempotency và kiểm soát tương tranh lạc quan.
- **database-design:** Xác thực snapshot do Order sở hữu, checkout attempts, ràng buộc, chỉ mục, UUID v7, tiền tệ số nguyên và chiến lược migration.
- **log-processing:** Xác thực audit, correlation, outbox, metrics Saga, che giấu dữ liệu nhạy cảm và khả năng quan sát DLQ.
- **sequence-diagram:** Xác thực các luồng trọng yếu mang tính khái niệm; biểu đồ L1/L2 thời gian chạy vẫn là cổng kiểm soát sau khi triển khai.
