# Module 5 — Dịch Vụ Khách Hàng Thân Thiết & Khuyến Mãi (Loyalty Service): Kết Quả Thiết Kế (Design Result)

**Ngày:** 2026-10-13  
**Trạng thái:** Thiết kế hoàn tất — Phê duyệt có cảnh báo (Approved with warnings)  
**Phạm vi:** M — Luồng Service Tiêu chuẩn (Standard Service Flow)  
**Module:** Khách hàng thân thiết & khuyến mãi (Loyalty Service)

---

## 1. Kết Luận Chung (Verdict)

**PHÊ DUYỆT CÓ CẢNH BÁO (APPROVED WITH WARNINGS)**

Bộ tài liệu thiết kế đã cung cấp đầy đủ cơ sở kỹ thuật và nghiệp vụ vững chắc để bắt đầu lập kế hoạch triển khai (implementation planning). Không có bất kỳ rào cản nghiêm trọng nội bộ nào trong Loyalty Service. Trước khi bắt đầu viết mã nguồn ứng dụng, cần hoàn tất việc cập nhật các hợp đồng hệ thống cha (Order Service) và các fixture kiểm thử được nêu chi tiết ở Mục 5.

---

## 2. Các Artifact Đã Hoàn Tất

1. [Tầm nhìn (Vision)](./2026-10-13-vision.md) — Đã phê duyệt (Approved)
2. [Yêu cầu kỹ thuật & nghiệp vụ (Requirements)](./2026-10-13-requirements.md) — Đã phê duyệt (Approved)
3. [Đánh giá yêu cầu nghiệp vụ (Requirements Sentinel Review)](./2026-10-13-requirements-review.md) — Có cảnh báo (Warnings)
4. [Kiến trúc hệ thống (Architecture)](./2026-10-13-architecture.md) — Đã phê duyệt (Approved)
5. [Hợp đồng API & Dữ liệu (API & Data Contracts)](./2026-10-13-contracts.md) — Đã phê duyệt có cảnh báo
6. [Đánh giá hợp đồng kỹ thuật (Contracts Sentinel Review)](./2026-10-13-contracts-review.md) — Có cảnh báo (Warnings)
7. [Kết quả thiết kế tổng hợp (Design Result)](./2026-10-13-loyalty_result.md) — Đã phê duyệt có cảnh báo

---

## 3. Tóm Tắt Đánh Giá Thiết Kế (Design Review Summary)

| Tiêu chí | Đánh giá | Nhận xét chi tiết |
|---|---|---|
| **Tính Đầy Đủ (Completeness)** | ĐẠT (PASS) | Toàn bộ các mảng nghiệp vụ: hồ sơ khách hàng, tích điểm, giữ điểm đổi thưởng, thu hồi điểm, hạng thành viên, hết hạn FIFO, voucher, công cụ khuyến mãi và cổng khách hàng đều có yêu cầu, thành phần kiến trúc và hợp đồng tương ứng. |
| **Tính Nhất Quán (Consistency)** | ĐẠT CÓ CẢNH BÁO | Các tài liệu nội bộ của Loyalty Service hoàn toàn nhất quán; hợp đồng Order Service cha cần bổ sung các trường dữ liệu và sự kiện `OrderRefunded.v1`. |
| **Tính Khả Thi (Feasibility)** | ĐẠT (PASS) | Mô hình database PostgreSQL độc lập, Transactional Outbox/Inbox, AWS EventBridge/SQS và các background worker hoàn toàn phù hợp với các ràng buộc hạ tầng của nền tảng POS. |
| **An Toàn Bảo Mật (Security)** | ĐẠT CÓ CẢNH BÁO | Phòng thủ chuyên sâu đa tenant/chi nhánh, phân quyền Cognito/Workload Identity, mã hóa số điện thoại, tra cứu bằng HMAC, che mờ dữ liệu PII và chống quét tài khoản đã được thiết lập; cần chốt nhà cung cấp OTP và thời hạn lưu trữ khi làm portal. |
| **Hiệu Năng (Performance)** | ĐẠT (PASS) | Mục tiêu thời gian phản hồi p95 ≤ 500 ms cho các API đồng bộ; Primary Database là đường dẫn đảm bảo tính đúng đắn; áp dụng phân trang con trỏ, hệ thống chỉ mục và worker scaling rõ ràng. |
| **Khả Năng Quan Sát (Observability)** | ĐẠT (PASS) | Thiết lập đầy đủ correlation ID, trace phân tán, hệ thống metrics, nhật ký kiểm toán, giám sát độ trễ outbox/inbox, hàng đợi chết DLQ và cảnh báo bất biến số dư. |
| **Độ Sẵn Sàng Kiểm Thử (Testing Readiness)** | ĐẠT CÓ CẢNH BÁO | Cần cụ thể hóa các fixture schema, kịch bản kiểm thử tranh chấp đồng thời, kiểm thử sự kiện gửi lặp/đến sai thứ tự và kiểm thử hợp đồng với hệ thống cha. |

---

## 4. Các Quyết Định Kiến Trúc Cuối Cùng

- Loyalty Service là một Bounded Context độc lập, sở hữu hồ sơ khách hàng, sổ cái điểm thưởng, voucher, quy tắc khuyến mãi và các view tổng hợp số dư.
- Cơ sở dữ liệu PostgreSQL riêng biệt; khóa chính UUID v7; tiền tệ VND và điểm thưởng lưu kiểu số nguyên; tuyệt đối không dùng cache trong đường dẫn kiểm tra số dư và giữ điểm.
- Sự kiện `OrderPaid.v1` là tín hiệu duy nhất kích hoạt việc tích điểm và commit các đơn giữ điểm/voucher; tuyệt đối không lắng nghe trực tiếp sự kiện thanh toán `PaymentPaid.v1`.
- Sự kiện hoàn tiền toàn bộ `OrderRefunded.v1` do Order Service phát hành sau khi hoàn tất thủ tục; Loyalty Service tự động tính toán số điểm thu hồi và khôi phục từ lịch sử sổ cái và phân bổ lô điểm của chính mình.
- Sổ cái điểm bất biến chỉ ghi thêm được quản lý tách biệt khỏi bảng giữ điểm `point_holds`; điểm giữ chỉ chuyển thành `REDEEMED` khi đơn thanh toán thành công, nếu thất bại/hết hạn thì giải phóng (`RELEASED`).
- Quản lý các lô điểm theo mô hình FIFO dựa trên hạn dùng và thời điểm tích lũy; phần điểm đang bị giữ chỗ được bảo vệ không bị quét hết hạn.
- Loyalty Service tính toán báo giá chiết khấu; Order Service giữ quyền quyết định tổng tiền thanh toán cuối cùng và lưu bản chụp báo giá/phiên bản quy tắc vào đơn hàng.
- Phiên bản quy tắc khuyến mãi là bất biến; cơ chế giải quyết xung đột có tính tất định cao: Mức hưởng lợi của khách hàng → Mức ưu tiên quy tắc → ID quy tắc cố định.
- Tạm giữ voucher theo phiên checkout; chỉ tiêu thụ chính thức khi đơn thanh toán thành công; việc gửi lại request không làm tăng bộ đếm lượt sử dụng.
- API cho nhân viên sử dụng Cognito kết hợp kiểm tra quyền tenant/chi nhánh; cổng portal khách hàng sử dụng mã OTP riêng biệt và chỉ cấp quyền đọc dữ liệu.
- Phân phối sự kiện qua EventBridge/SQS theo mô hình at-least-once, khử trùng lặp qua bảng Inbox, ghi nhận qua Transactional Outbox, có hàng đợi chết DLQ và quy trình replay có kiểm soát.

---

## 5. Cảnh Báo & Các Điều Kiện Tiên Quyết Triển Khai (Implementation Gates)

| Cổng kiểm soát (Gate) | Nội dung kiểm tra | Điều kiện hoàn tất |
|---|---|---|
| **Hợp đồng Order Service cha** | Bổ sung các trường `loyaltyEligibleAmountMinor`, `loyaltyPolicyVersion`, `checkoutAttemptId`, `pointHoldId`, `voucherReservationId` và các tham chiếu báo giá vào sự kiện `OrderPaid.v1`. | Cập nhật AsyncAPI/JSON Schema của Order; bộ test tương thích giữa producer và consumer pass 100%. |
| **Sự kiện hoàn tiền toàn bộ** | Định nghĩa sự kiện `OrderRefunded.v1` do Order Service phát hành sau khi hoàn tất hoàn tiền toàn bộ; Payment Service không phát sự kiện thay thế cho Loyalty. | Hoàn tất bảng ánh xạ Order/Payment, kịch bản hoàn tiền trùng lặp và đến sai thứ tự pass. |
| **Thứ tự ưu tiên trạng thái terminal** | Sự kiện lỗi checkout hoặc lệnh release gửi trễ tuyệt đối không được phép giải phóng một đơn giữ điểm đã commit hoặc voucher đã tiêu thụ. | Bổ sung số phiên bản vòng đời attempt và bộ test thứ tự ưu tiên trạng thái pass. |
| **Bất biến phân bổ lô điểm** | Cụ thể hóa các ví dụ mẫu cho các thao tác `HOLD`, `RELEASE`, `REDEEM`, `REVERSE`, `EXPIRE` và phân bổ FIFO khi có tranh chấp đồng thời. | Bộ test transaction và race condition trong PostgreSQL chứng minh không âm số dư, không phân bổ vượt mức và rebuild chính xác. |
| **Tính nguyên tử của hạn mức voucher** | Bộ đếm lượt dùng tổng toàn hệ thống và trên mỗi khách hàng phải đảm bảo tính nguyên tử khi có nhiều lượt checkout đồng thời. | Chiến lược khóa dòng/cập nhật bộ đếm và kiểm thử tải tranh chấp pass. |
| **Xác thực cổng khách hàng** | Chốt nhà cung cấp dịch vụ OTP, cấu hình claims của token phiên, giới hạn tần suất gọi, cơ chế xoay vòng khóa và thời hạn lưu trữ. | Hợp đồng adapter xác thực + bộ test an ninh chống dò quét tài khoản pass. |
| **Bảo vệ dữ liệu riêng tư (PII)** | Tự động che mờ số điện thoại, mã OTP, secret, chuỗi hash/ciphertext và các payload quy tắc nhạy cảm khỏi nhật ký log và trace. | Kiểm tra định dạng log và bộ test bảo mật negative pass. |

---

## 6. Lộ Trình Triển Khai Thực Tế (Implementation Roadmap)

### Giai đoạn 0 — Đồng Bộ Hợp Đồng & Khung Nền Tảng (Phase 0)
- **Mục tiêu:** Xây dựng nền tảng service, database, xác thực và hợp đồng sự kiện vững chắc.
- **Nhiệm vụ:**
  - Cập nhật schema các sự kiện của Order Service và các tham chiếu bồi hoàn checkout.
  - Chốt ngữ nghĩa producer/consumer cho sự kiện `OrderRefunded.v1`.
  - Khởi tạo khung dự án (service skeleton), tích hợp kiểm tra tự động OpenAPI/AsyncAPI trong CI.
  - Cấu hình PostgreSQL, AWS KMS, Secrets Manager, EventBridge, SQS, DLQ và framework migration Flyway.
  - Cụ thể hóa toàn bộ comment cột database, điều kiện tenant, hệ thống index và các bảng Outbox/Inbox.
- **Ước tính thời gian:** 3–5 ngày làm việc.
- **Điều kiện phụ thuộc:** Chủ sở hữu Order và Payment Service phê duyệt các nội dung sửa đổi hợp đồng.

### Giai đoạn 1 — Hồ Sơ Khách Hàng & Tích Điểm P0 (Phase 1)
- **Mục tiêu:** Tra cứu/tạo khách hàng và tích lũy điểm thưởng chính xác sau khi đơn thanh toán thành công.
- **Nhiệm vụ:**
  - Chuẩn hóa số điện thoại E.164, mã hóa đối xứng, sinh mã băm HMAC tra cứu, che mờ phản hồi.
  - Xây dựng các Public API cho khách hàng, cấu hình quyền Cognito và ghi nhật ký truy cập PII.
  - Sổ cái bất biến, chính sách điểm, tạo các lô điểm `EARNED`, bảng tổng hợp số dư.
  - Consumer xử lý sự kiện `OrderPaid`, khử trùng lặp qua Inbox, kiểm tra tính duy nhất, xử lý DLQ và phát sự kiện tích điểm của Loyalty.
  - Lệnh quản trị tái tạo bảng số dư và kiểm tra bất biến không cho phép số dư âm.
- **Ước tính thời gian:** 1.5–2 tuần làm việc.
- **Điều kiện phụ thuộc:** Hoàn thành Phase 0; bộ fixture schema cho sự kiện `OrderPaid`.

### Giai đoạn 2 — Giữ Điểm Đổi Thưởng & Tích Hợp Checkout P0 (Phase 2)
- **Mục tiêu:** Đổi điểm an toàn trong luồng Saga, ngăn chặn triệt để hành vi chi tiêu kép.
- **Nhiệm vụ:**
  - Ranh giới giao dịch nguyên tử cho việc báo giá, giữ điểm và tạm giữ voucher.
  - Các hàm phân bổ lô điểm theo thuật toán FIFO, máy trạng thái giữ điểm và lệnh giải phóng.
  - Tích hợp với Order Service / Saga xử lý các kịch bản: thanh toán thất bại, timeout, hủy đơn và bồi hoàn.
  - Handler xử lý commit điểm giữ khi nhận `OrderPaid`; quy tắc ưu tiên trạng thái terminal khi lệnh release đến trễ.
  - Kiểm thử giữ điểm đồng thời, retry lệnh và replay từ message broker.
- **Ước tính thời gian:** 1.5–2 tuần làm việc.
- **Điều kiện phụ thuộc:** Hoàn thành Phase 1; hợp đồng vòng đời checkout attempt của Order Service.

### Giai đoạn 3 — Thu Hồi Điểm Khi Hoàn Đơn & Vận Hành Tin Cậy P0 (Phase 3)
- **Mục tiêu:** Thu hồi điểm chính xác khi hoàn tiền toàn bộ đơn hàng và vận hành ổn định hệ thống hướng sự kiện.
- **Nhiệm vụ:**
  - Consumer xử lý sự kiện `OrderRefunded.v1`, truy vết nguồn gốc thu hồi, khôi phục lại điểm đã đổi bằng lô điểm mới.
  - Ghi nhận nhật ký audit và outbox cho các sự kiện tích điểm, thu hồi và giải phóng điểm giữ.
  - Tiến trình replay từ DLQ, dọn dẹp các đơn giữ điểm treo, cảnh báo sai lệch số dư và kích hoạt rebuild.
  - Hoàn thiện hệ thống metrics, trace phân tán, che mờ log, dashboard giám sát và runbook vận hành cơ bản.
  - Kiểm thử các kịch bản hoàn tiền toàn bộ, sự kiện gửi lặp, sự kiện đến muộn/sai thứ tự và khôi phục điểm sau khi hết hạn.
- **Ước tính thời gian:** 1 tuần làm việc.
- **Điều kiện phụ thuộc:** Hoàn thành Phase 2; chốt hợp đồng sự kiện hoàn tiền chính thức.

### Giai đoạn 4 — Hạng Thành Viên, Hết Hạn FIFO, Voucher & Khuyến Mãi P1 (Phase 4)
- **Mục tiêu:** Mở rộng toàn diện các tính năng thân thiết và công cụ khuyến mãi sau khi tầng cốt lõi P0 đã ổn định.
- **Nhiệm vụ:**
  - Chính sách và lịch sử hạng thành viên, tiến trình tính toán lại hạng theo chu kỳ trượt 12 tháng, phát sự kiện đổi hạng.
  - Worker quét hết hạn điểm hàng ngày theo múi giờ tenant và phân bổ tiêu hao các lô điểm FIFO.
  - Quản lý cấu hình voucher, kiểm soát hạn mức, tạm giữ, tiêu thụ, giải phóng và báo cáo sử dụng.
  - Các phiên bản quy tắc khuyến mãi bất biến: giảm giá phần trăm, mua X tặng Y, khung giờ vàng.
  - Công cụ giải quyết xung đột khuyến mãi tất định, timeout tính toán quy tắc, cơ chế fail-closed và lưu bản chụp giải thích chiết khấu.
- **Ước tính thời gian:** 2–3 tuần làm việc.
- **Điều kiện phụ thuộc:** Hoàn thành Phase 1–3; hợp đồng bản chụp giỏ hàng từ Catalog và Order Service.

### Giai đoạn 5 — Cổng Tra Cứu Khách Hàng P2 & Gia Cố Toàn Diện (Phase 5)
- **Mục tiêu:** Cung cấp cổng tra cứu chỉ đọc cho khách hàng và nghiệm thu chất lượng tương đương môi trường production.
- **Nhiệm vụ:**
  - Adapter xác thực khách hàng qua mã OTP, quản lý phiên đăng nhập, giới hạn tần suất và thời hạn lưu trữ.
  - Các API portal tra cứu số dư điểm, lịch sử sổ cái, danh sách voucher, cơ chế chống dò quét tài khoản và che mờ PII.
  - Đo kiểm hiệu năng độ trễ và tải đồng thời bằng k6, diễn tập phục hồi backup và replay sự kiện, kiểm tra an toàn bảo mật.
  - Kiểm tra tính tương thích hợp đồng, diễn tập cấy lỗi gián đoạn (chaos testing), hoàn thiện tài liệu runbook vận hành và báo cáo đo lường SLO.
- **Ước tính thời gian:** 1–2 tuần làm việc.
- **Điều kiện phụ thuộc:** Hoàn thành Phase 1–4; chốt lựa chọn nhà cung cấp dịch vụ viễn thông gửi mã OTP.

**Tổng thời gian ước lượng:** Khoảng **7–10 tuần** cho toàn bộ các lát cắt nghiệp vụ từ P0 → P1 → P2 (chưa bao gồm thời gian chờ phê duyệt sửa đổi hợp đồng của các service khác hoặc thay đổi phạm vi nghiệp vụ).

---

## 7. Tóm Tắt Kiến Trúc Hệ Thống

| Chiều kiến trúc | Tóm tắt thông số cốt lõi |
|---|---|
| **Các nhóm thành phần logic** | Khoảng 12 nhóm: Public API Adapter, Internal Checkout API, Customer Module, Points Service & Domain, Promotion Engine, Voucher Module, Event Consumer/Inbox, Outbox Publisher, Background Workers, Repositories, Audit/Observability, và Cơ sở dữ liệu/Hạ tầng Broker. |
| **Cơ sở dữ liệu** | 1 database PostgreSQL thuộc quyền sở hữu riêng của Loyalty Service; các bảng projection lưu trữ trong cùng DB; tuyệt đối không dùng chung database hay foreign key xuyên service. |
| **Bề mặt thao tác API** | 31 endpoint thao tác được thiết kế trong hợp đồng: API nhân viên quầy, API nội bộ cho checkout/vận hành, API cấu hình quản trị P1 và API cổng khách hàng P2. |
| **Sự kiện bất đồng bộ** | Tiêu thụ 3 nhóm sự kiện từ Order Service (`OrderPaid`, `OrderRefunded`, lỗi checkout/giải phóng) và phát hành 7 loại sự kiện định kiểu của Loyalty, tuân thủ phong bì chuẩn và phiên bản hóa. |
| **Các luồng cốt lõi (Critical Paths)** | Báo giá chiết khấu/giữ điểm/checkout thành công, xử lý sự kiện `OrderPaid` gửi lặp, thu hồi điểm khi hoàn tiền toàn bộ, và thẩm định quy tắc khuyến mãi/voucher. |
| **Mô hình dữ liệu** | Khoảng 18 bảng/thực thể aggregate logic bao gồm: hồ sơ khách hàng, sổ cái điểm, lô điểm/phân bổ tiêu hao, đơn giữ điểm, chính sách điểm, cấu hình/lịch sử hạng, chương trình/phiên bản khuyến mãi, voucher/tạm giữ/sử dụng, bảng inbox/outbox, nhật ký kiểm toán và metadata worker. |

---

## 8. Các Bước Tiếp Theo (Next Steps)

- [ ] Áp dụng các nội dung sửa đổi hợp đồng vào Order Service cha và xuất bản các fixture schema tương ứng.
- [ ] Xác nhận quyền sở hữu sự kiện `OrderRefunded.v1` và quy tắc ưu tiên trạng thái terminal khi lệnh đến trễ.
- [ ] Chuyển đổi lộ trình triển khai (Roadmap Phase 0–5) thành các task chi tiết trong backlog dự án.
- [ ] Khởi tạo khung dự án service skeleton, thiết lập pipeline CI kiểm tra tính hợp lệ của hợp đồng, bổ sung comment database và xây dựng test harness kiểm tra cô lập đa tenant.
- [ ] Chỉ bắt đầu viết code Phase 1 sau khi toàn bộ các cổng kiểm soát triển khai ở Mục 5 được các bên phê duyệt nghiệm thu.
