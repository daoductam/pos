# Kết quả Thiết kế Kiến trúc: POS Platform (Nền tảng POS Đa Tenant)

## Kết luận Đánh giá: PHÊ DUYỆT CÓ ĐIỀU KIỆN (APPROVED WITH WARNINGS)

Hồ sơ thiết kế kiến trúc hoàn toàn chặt chẽ, mạch lạc và đủ chi tiết để bắt đầu triển khai lát cắt dọc P0 (vertical slice). Các cảnh báo kỹ thuật dưới đây đóng vai trò là các chốt chặn (implementation gates / technical spikes) cần giải quyết trong quá trình lập trình, không gây cản trở việc bắt đầu triển khai theo thiết kế đã được phê duyệt.

---

## Danh mục Tài liệu Thành phần (Artifacts)

| Giai đoạn | Tài liệu | Đường dẫn | Trạng thái |
|---|---|---|---|
| **Tầm nhìn (Vision)** | Bản Tầm nhìn Hệ thống | [`2026-10-12-vision.md`](./2026-10-12-vision.md) | Đã phê duyệt |
| **Đánh giá Tầm nhìn** | Báo cáo Rà soát Tầm nhìn (Sentinel Report) | [`2026-10-12-vision-review.md`](./2026-10-12-vision-review.md) | Đã chấp nhận các cảnh báo |
| **Yêu cầu (Requirements)** | Yêu cầu Nghiệp vụ & Kỹ thuật | [`2026-10-12-requirements.md`](./2026-10-12-requirements.md) | Đã phê duyệt |
| **Kiến trúc (Architecture)** | Kiến trúc Hệ thống Tổng thể | [`2026-10-12-architecture.md`](./2026-10-12-architecture.md) | Đã phê duyệt |
| **Hợp đồng (Contracts)** | Hợp đồng API & Cấu trúc Dữ liệu | [`2026-10-12-contracts.md`](./2026-10-12-contracts.md) | Đã phê duyệt |
| **Tổng kết (Finalization)** | Kết quả Thiết kế Kiến trúc | Tài liệu này | Phê duyệt có điều kiện |

---

## Tóm tắt Đánh giá Thiết kế (Design Review Summary)

### Tính Đầy đủ (Completeness)
- 22 Ca sử dụng (Use Cases) mức P0 đã được ánh xạ chi tiết vào từng Bounded Context, bề mặt API, quyền sở hữu trạng thái và các tầng kiểm thử tương ứng.
- 20 Quy tắc nghiệp vụ (Business Rules) P0 bao quát toàn diện các yêu cầu: cách ly đa tenant, độ chính xác số học của tiền tệ, quy trình thanh toán, giữ chỗ kho, sổ cái loyalty, bộ lọc gợi ý món, mẫu Transactional Outbox, cơ chế thử lại và lưu vết kiểm toán.
- 35 Endpoint API, 12 Sự kiện tích hợp phân tán (Domain Events), chính sách hàng đợi SQS/DLQ, lược đồ cơ sở dữ liệu và quy ước migration bằng Flyway đã được đặc tả hoàn chỉnh.
- Các yêu cầu về an ninh, khả năng quan sát, kiểm thử tiêm lỗi (fault injection), kiểm thử chịu tải (load test) và kiểm thử hỗn loạn (chaos test) đều được quy chuẩn rõ ràng.

### Tính Nhất quán (Consistency)
- `OrderPaid` được xác định là **Sự thật chuẩn mực (Canonical Fact)** duy nhất để kích hoạt cộng điểm thưởng bên Loyalty Service và tính toán số liệu doanh thu bên Reporting Service.
- Sự kiện `PaymentPaid` chỉ phục vụ bộ điều phối Saga và Audit; Saga trực tiếp phát lệnh trừ kho (`CommitReservation`) sang Inventory Service, loại trừ nguy cơ trừ kho lặp lại hoặc các tác dụng phụ ngoài ý muốn.
- Nguyên tắc sở hữu cơ sở dữ liệu riêng, cấm câu lệnh SQL JOIN xuyên database, cơ chế Transactional Outbox và chuẩn metadata sự kiện đều đồng nhất từ Architecture đến Contracts.
- Toàn bộ các API, Event và Database Model đều chứa các định danh ngữ cảnh tenant/branch scope và mã `correlationId`.

### Tính Khả thi Triển khai (Feasibility)
- Ngăn xếp công nghệ **Java 21 + Spring Boot 3.x + Angular + PostgreSQL + Docker Compose** là nền tảng cực kỳ thực tế, vững chắc và phù hợp để xây dựng bản demo.
- Ranh giới Adapter lai giúp cô lập hoàn toàn môi trường chạy demo cục bộ khỏi nguy cơ gián đoạn đường truyền từ dịch vụ cloud AWS hoặc VNPAY sandbox.
- Do phạm vi toàn hệ thống rất lớn, quy trình lập trình bắt buộc phải bám sát lộ trình lát cắt dọc (vertical slice) và giữ các tính năng P1/P2 nằm ngoài luồng chạy runtime đầu tiên.

---

## Các Cảnh báo Kỹ thuật và Spike Bắt buộc (Warnings & Required Spikes)

| # | Cảnh báo | Rủi ro kỹ thuật | Hành động bắt buộc | Giai đoạn thực hiện |
|---|---|---|---|---|
| **W-01** | Các trường dữ liệu, thuật toán tính chữ ký HMAC của VNPAY sandbox và cơ chế gửi lại webhook IPN cần được kiểm chứng thực tế trên sandbox. | Giao dịch thanh toán có thể bị treo ở trạng thái chờ hoặc tiếp nhận callback sai thứ tự / không hợp lệ. | Xây dựng một spike adapter VNPAY tinh gọn kèm các fixture mẫu ghi nhận từ sandbox và bộ test kịch bản gửi lặp / gửi sai thứ tự. | Phase 0 — Khởi tạo nền tảng |
| **W-02** | Khả năng tương quan (correlation) giữa callback của Step Functions và sự kiện bên ngoài chưa được kiểm chứng trên môi trường thực thi. | Saga có thể hoàn tất hai lần hoặc kích hoạt bồi hoàn nhầm sau khi thanh toán thành công đến muộn. | Chạy thử nghiệm độc lập một luồng: Giữ chỗ kho → Đợi thanh toán → Chốt đơn / Bồi hoàn trên Step Functions trước khi mở rộng toàn service. | Phase 0 — Khởi tạo nền tảng |
| **W-03** | Chiến lược khóa dữ liệu (Locking Strategy) trong Inventory Service đang để ngỏ giữa Optimistic Versioning và Row-level Pessimistic Locking. | Khi nhiều thu ngân cùng checkout một món bán chạy có thể dẫn đến bán quá số lượng (oversell) hoặc xảy ra deadlock database. | Chạy bài test tải k6 mô phỏng tranh chấp đặt hàng để đo lường và quyết định giữa Optimistic Locking và Pessimistic Locking. | Phase 3 — Core Checkout |
| **W-04** | Các chỉ số phi chức năng (NFR) mới chỉ là mục tiêu thiết kế trên giấy, chưa được đo lường bằng số liệu thực nghiệm. | Bài demo có thể không đạt mục tiêu p95 hoặc tốc độ xử lý khi ghép nối các integration thực tế. | Thiết lập kịch bản k6 baseline và công bố số liệu p95 cũng như độ trễ sự kiện đo được trong báo cáo nghiệm thu. | Phase 5 — Tin cậy & Đóng gói |
| **W-05** | Bộ adapter local broker có sự khác biệt về mặt vận hành so với dịch vụ AWS EventBridge / SQS thật. | Chạy thành công ở local nhưng có thể phát sinh lỗi bất ngờ khi deploy lên AWS. | Chạy tối thiểu một bài kiểm thử tích hợp trực tiếp trên AWS sandbox và duy trì bộ test tính tương đương của adapter (parity tests). | Phase 5 — Tích hợp & Đóng gói |
| **W-06** | Sơ đồ tuần tự (Sequence Diagrams) tạm hoãn sinh tự động vì chưa có mã nguồn controller hay topic thực tế để trace code. | Quá trình viết code có thể bị lệch so với luồng logic đã hình dung trên thiết kế. | Sinh sơ đồ tuần tự mức L1/L2 dạng PlantUML ngay sau khi khung mã nguồn (skeleton) và API controller đầu tiên chạy được. | Implementation |
| **W-07** | Việc gộp chung các database vào 1 PostgreSQL container ở môi trường local có thể khiến người phát triển nhầm lẫn về quyền sở hữu dữ liệu. | Dễ sa vào việc viết câu query JOIN chéo database do tiện tay khi chạy local. | Thiết lập tài khoản, mật khẩu và database riêng biệt ngay trên container local; mô tả rõ ràng cấu trúc hạ tầng độc lập trong mã nguồn Terraform. | Phase 0 — Hạ tầng |

---

## Tóm tắt Kiến trúc Tổng thể (Architecture Summary)

- **Thành phần logic:** 16 thành phần gồm Angular UI, API Gateway, Cognito, 10 Bounded Context / Reporting / Audit services, Step Functions, EventBridge / SQS và Observability.
- **Cơ sở dữ liệu:** 10 cơ sở dữ liệu / Read Model độc lập trên nền PostgreSQL với phân quyền sở hữu chặt chẽ.
- **Giao diện API:** 35 hợp đồng endpoint API bao quát các bề mặt Public, Internal và Callback.
- **Điểm tích hợp ngoài:** 12 hợp đồng sự kiện có phiên bản, tích hợp Cognito, VNPAY, Step Functions, EventBridge/SQS, Secrets Manager và CloudWatch/X-Ray.
- **Mô hình nhất quán:** Nhất quán mạnh (Strong Consistency) bên trong transaction cục bộ của từng service; Nhất quán sau cùng (Eventual Consistency) xuyên suốt các bảng chiếu báo cáo qua sự kiện; Bồi hoàn tự động (Compensation) qua Saga cho luồng đặt hàng.

---

## Lộ trình Triển khai Chi tiết (Implementation Roadmap)

### Phase 0 — Nền tảng và Kiểm chứng Kiến trúc (Foundation & Spikes)
- **Mục tiêu:** Dựng bộ khung dịch vụ có thể khởi chạy được và kiểm chứng các ranh giới tích hợp rủi ro cao nhất.
- **Nội dung công việc:**
  - Khởi tạo khung mẫu dự án (template) Java 21 / Spring Boot, Angular shell và thư viện hợp đồng dùng chung.
  - Cấu hình Docker Compose cho PostgreSQL với cơ chế phân tách database và quyền riêng biệt cho từng service.
  - Tạo baseline Flyway migrations, thư viện dùng chung cho correlation ID, error envelope, idempotency key.
  - Thiết kế các interface adapter cho Local Broker và AWS EventBridge/SQS.
  - Thực nghiệm kiểm chứng: Xác thực JWT từ Cognito, kiểm tra chữ ký HMAC VNPAY và chạy 1 quy trình Saga thành công + bồi hoàn trên Step Functions.
  - Tạo bộ khung mã nguồn Terraform cho tài nguyên sandbox và cấu hình CI checks cơ bản.
- **Tiêu chí nghiệm thu (Exit Criteria):** Toàn bộ service template khởi động thành công; bài test hợp đồng chạy thông; spike chứng minh được Saga chạy bồi hoàn chuẩn xác.

### Phase 1 — Phân quyền, Danh mục và Ca làm việc (Identity, Catalog, Shift)
- **Mục tiêu:** Thiết lập ngữ cảnh bảo mật chi nhánh và dữ liệu danh mục để có thể bán được một món ăn.
- **Nội dung công việc:**
  - Triển khai xác thực Cognito Resource Server, phân giải quyền từ database Identity DB.
  - Quản trị chuỗi (tenant), chi nhánh (branch), nhân viên và bộ test phân quyền tiêu cực.
  - Quản lý danh mục (Catalog), món, biến thể, topping và phát sự kiện trạng thái sẵn sàng của món.
  - Quản lý ca làm việc (Shift): mở ca, đóng ca và kiểm soát tiền đầu ca.
  - Xây dựng giao diện Angular: đăng nhập, chọn chi nhánh, duyệt danh mục và mở ca.
- **Tiêu chí nghiệm thu (Exit Criteria):** Thu ngân đăng nhập, chọn chi nhánh được cấp phép, mở ca làm việc và tải danh mục bán hàng; mọi truy cập trái quyền bị chặn tuyệt đối.

### Phase 2 — Đơn hàng và Gợi ý Món (Order & Recommendation Slice)
- **Mục tiêu:** Tạo bản nháp đơn hàng hoàn chỉnh và chứng minh khả năng gợi ý món upsell.
- **Nội dung công việc:**
  - Quản lý giỏ hàng, thêm/sửa/hủy món, lưu ảnh chụp giá (price snapshot) và khóa lạc quan (optimistic locking).
  - Triển khai thuật toán gợi ý món theo luật kết hợp kèm bộ lọc tồn kho/giỏ hàng.
  - Phát các sự kiện `OrderCreated`, `SuggestionShown` và `SuggestionAccepted` qua bảng Outbox.
  - Xây dựng màn hình giỏ hàng và giao diện hiển thị món gợi ý trên Angular.
- **Tiêu chí nghiệm thu (Exit Criteria):** Thu ngân thêm món vào đơn, nhận được gợi ý món uống phù hợp, chấp nhận gợi ý và hệ thống ghi nhận đầy đủ hai sự kiện phân tích.

### Phase 3 — Kho nguyên liệu, Thanh toán và Checkout Saga
- **Mục tiêu:** Hoàn thiện toàn bộ luồng tiền tệ, trừ kho nguyên vật liệu và cơ chế tự phục hồi lỗi.
- **Nội dung công việc:**
  - Xây dựng cơ sở dữ liệu công thức, nguyên liệu, sổ cái kho và cơ chế giữ chỗ (reservation).
  - Xử lý thanh toán tiền mặt và tính tiền thừa chính xác bằng số nguyên.
  - Tích hợp cổng QR VNPAY sandbox, xác thực IPN, máy trạng thái thanh toán và job đối soát giao dịch treo.
  - Triển khai quy trình điều phối Checkout Saga trên Step Functions kèm xử lý timeout, retry và bồi hoàn kho.
  - Kết nối luồng sự kiện `PaymentPaid`, `PaymentFailed` và `OrderPaid`.
  - Xây dựng màn hình thanh toán, hiển thị mã QR động và xử lý các trạng thái lỗi trên Angular.
- **Tiêu chí nghiệm thu (Exit Criteria):** Luồng tiền mặt và QR đều chốt đơn thành công (`PAID`); khi tắt dịch vụ Payment, kho được tự động giải phóng giữ chỗ và xuất trace toàn vẹn không bị lặp tác dụng phụ.

### Phase 4 — Khách hàng thân thiết, Báo cáo và Kiểm toán (Loyalty, Reporting, Audit)
- **Mục tiêu:** Đo lường kết quả kinh doanh và đảm bảo tính kiểm toán minh bạch cho toàn bộ hệ thống.
- **Nội dung công việc:**
  - Tra cứu khách hàng theo số điện thoại đã được che mờ (masking PII).
  - Triển khai sổ cái điểm thưởng bất biến (append-only loyalty ledger), tính toán số dư và xử lý thu hồi điểm khi có hoàn đơn.
  - Xây dựng các consumer tổng hợp dữ liệu báo cáo doanh thu theo chi nhánh và món bán chạy.
  - Lưu trữ và truy vấn nhật ký kiểm toán (audit log) cho các hành vi nhạy cảm và các sự kiện vi phạm an ninh.
  - Xây dựng màn hình dashboard báo cáo hiển thị độ tươi mới dữ liệu, số dư loyalty và nhật ký kiểm toán trên Angular.
- **Tiêu chí nghiệm thu (Exit Criteria):** Đơn hàng thanh toán xong tự động tích điểm chuẩn xác; phát lại sự kiện không làm cộng trùng điểm; dashboard cập nhật số liệu trong ngưỡng thời gian cho phép.

### Phase 5 — Độ tin cậy, An ninh và Đóng gói Demo (Reliability & Hardening)
- **Mục tiêu:** Kiểm chứng toàn bộ kiến trúc dưới các điều kiện lỗi giả lập và hoàn thiện tài liệu hướng dẫn demo.
- **Nội dung công việc:**
  - Chạy toàn bộ bộ test tự động: Unit, Integration, Contract, E2E và Security Negative.
  - Chạy k6 kiểm thử tải cho luồng checkout, gợi ý món và truy vấn báo cáo; ghi nhận các chỉ số p95 và độ trễ sự kiện.
  - Thực hiện các kịch bản hỗn loạn (chaos): tắt đột ngột service, làm trễ broker, gửi trùng IPN, phát lại từ DLQ.
  - Thiết lập CloudWatch Dashboard, X-Ray distributed trace và hệ thống cảnh báo lỗi tự động.
  - Nạp bộ dữ liệu mẫu đa tenant/đa chi nhánh xác định và hoàn thiện tài liệu hướng dẫn chạy demo (Demo Runbook).
  - Tự động sinh sơ đồ tuần tự mức L1/L2 dạng PlantUML từ mã nguồn và hợp đồng thực tế.
- **Tiêu chí nghiệm thu (Exit Criteria):** Toàn bộ các tiêu chí thành công của Vision đều có số liệu thực nghiệm chứng minh; bài demo có thể reset và chạy lại nhiều lần một cách tin cậy.
