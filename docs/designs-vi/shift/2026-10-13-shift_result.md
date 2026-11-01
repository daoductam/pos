Started at: 2026/10/13 00:00:00
Finished at: 2026/10/13 00:00:00
Total time: 0 minutes
---

# Kết Quả Thiết Kế: Module 7 — Ca làm việc (Shift Service)

## Kết luận: APPROVED WITH WARNINGS (ĐÃ PHÊ DUYỆT VỚI CÁC CẢNH BÁO)

Thiết kế đầy đủ để bắt đầu lập kế hoạch triển khai (implementation planning) và chia phase lập trình. Không còn bất kỳ điểm nghẽn (blocker) nào về quyền sở hữu (ownership), vòng đời (lifecycle), công thức tiền tệ, cách ly tenant/chi nhánh hoặc tranh chấp race condition giữa đóng ca và thanh toán. Các cảnh báo còn lại liên quan đến kế hoạch rollout liên service và cấu hình tham số vận hành; phải được xử lý trước khi rollout chính thức ra production.

## Các tài liệu thành phần (Artifacts)

| Giai đoạn | Tài liệu | Đường dẫn | Trạng thái |
|---|---|---|---|
| Vision | Tầm nhìn Shift (Shift Vision) | [`2026-10-13-vision.md`](file:///d:/vibe-code/pos/docs/designs-vi/shift/2026-10-13-vision.md) | Đã duyệt |
| Requirements | Yêu cầu nghiệp vụ Shift | [`2026-10-13-requirements.md`](file:///d:/vibe-code/pos/docs/designs-vi/shift/2026-10-13-requirements.md) | Đã duyệt có cảnh báo |
| Requirements review | Báo cáo đánh giá yêu cầu | [`2026-10-13-requirements-review.md`](file:///d:/vibe-code/pos/docs/designs-vi/shift/2026-10-13-requirements-review.md) | Chỉ còn cảnh báo |
| Architecture | Kiến trúc hệ thống Shift | [`2026-10-13-architecture.md`](file:///d:/vibe-code/pos/docs/designs-vi/shift/2026-10-13-architecture.md) | Đã duyệt |
| Sequence | Sơ đồ tuần tự L1/L2 các luồng cốt lõi | [`2026-10-13-sequence-diagrams.adoc`](file:///d:/vibe-code/pos/docs/designs-vi/shift/2026-10-13-sequence-diagrams.adoc) | Đã sinh |
| Contracts | Hợp đồng API & Dữ liệu Shift | [`2026-10-13-contracts.md`](file:///d:/vibe-code/pos/docs/designs-vi/shift/2026-10-13-contracts.md) | Đã duyệt |
| Finalization | Kết quả tổng kết thiết kế này | `2026-10-13-shift_result.md` | Đã duyệt có cảnh báo |

## Tóm tắt đánh giá thiết kế (Design review summary)

### Tính đầy đủ (Completeness)

- Các tính năng P0: mở ca, ca hiện tại, đóng ca, tính lũy nghiệm, RBAC, audit và hủy ca bị bỏ quên đều có cấu phần kiến trúc, API, state machine và hợp đồng lưu trữ cơ sở dữ liệu.
- Các tính năng P1: kiểm đếm tiền, tính tiền mặt kỳ vọng, ngưỡng lệch, xem xét chênh lệch, đếm lại, sự kiện tiền mặt đến trễ / trùng lặp và ca đối soát đều có luồng xử lý, endpoint, sự kiện và entity trong DB.
- Tranh chấp giữa đóng ca và thanh toán (close/payment race) được giải quyết dứt điểm bằng rào chắn tiếp nhận đánh số thứ tự trong DB (DB-sequenced admission fence), không dựa vào thời gian phân phối gói tin của broker.
- Các luồng nghiệp vụ quan trọng đều có sơ đồ L1/L2: tranh chấp mở ca đồng thời, tiếp nhận trước/sau rào chắn, đóng ca P0, xem xét P1, đếm lại, hủy ca và sự kiện tiền mặt đến trễ.

### Tính nhất quán (Consistency)

- Shift là nguồn chân lý duy nhất (source of truth); Order/Payment/Saga chỉ sử dụng các tham chiếu opaque và hợp đồng tích hợp.
- Vòng đời công khai (public lifecycle) gồm: `ACTIVE`, `PENDING_REVIEW`, `CLOSED`, `CANCELLED`; pha tiếp nhận nội bộ (internal admission phase) gồm: `OPEN`, `CLOSING`, `FROZEN`.
- Công thức tính tiền mặt kỳ vọng thống nhất toàn hệ thống:

```text
expectedCashMinor = openingFloatMinor + cashCollectedMinor - cashRefundedMinor
discrepancyMinor = countedCashMinor - expectedCashMinor
```

- Lượt kiểm đếm ban đầu của P1 nằm trực tiếp trong `CloseShiftRequest`; endpoint `cash-counts` chỉ dành riêng cho lần kiểm đếm lại sau khi Quản lý/Chủ chuỗi yêu cầu.
- Sự kiện đến trễ hoặc không xác định sẽ mở ca đối soát `ShiftReconciliationCaseOpened.v1`; chỉ sau khi có quyết định giải quyết mới phát sự kiện `ShiftCashFactReconciled.v1`. Ca đã đóng (`CLOSED`) tuyệt đối không bị thay đổi (mutate) hay mở lại (reopen).

### Tính khả thi (Feasibility)

- Transaction cục bộ của PostgreSQL + khóa hàng (row lock) + partial unique index đáp ứng hoàn hảo yêu cầu mỗi chi nhánh chỉ có một ca active duy nhất và thứ tự đóng ca mang tính tất định.
- Mô hình Transactional Outbox, EventBridge/SQS, DLQ và consumer xử lý lũy nghiệm hoàn toàn tương thích với nền tảng POS Platform tổng thể.
- Cơ chế drain khi đóng ca được xử lý bằng worker có giới hạn thời gian (bounded worker), không bao giờ giữ transaction DB mở trong lúc chờ đợi các service khác.
- Baseline quy mô của parent không yêu cầu phân vùng (partition) các bảng transaction trong giai đoạn MVP; việc phân vùng chỉ là tùy chọn vận hành cho lịch sử/outbox/audit khi đạt ngưỡng dữ liệu lớn.

### Các cảnh báo còn lại (Remaining warnings)

| # | Cảnh báo | Tác động | Hành động bắt buộc |
|---:|---|---|---|
| 1 | Tài liệu parent Order/Payment/platform vẫn còn các hợp đồng sơ khai ở một số đoạn | Nếu lập trình theo tài liệu parent cũ, việc quy kết tiền mặt sẽ bị thiếu `shiftId`/`admissionId` | Đồng bộ các hợp đồng parent trước khi sinh DTO/client; giữ Shift Contracts làm nguồn chi tiết chuẩn mực |
| 2 | Lệnh đóng ca P0 có thể trả về `202` và cuối cùng trở thành `PENDING_REVIEW` khi các admission trước rào chắn bị timeout | POS cần hiển thị trạng thái đang xử lý/chờ xem xét và không cho phép mở ca mới | Chốt thông điệp giao diện UI, SLA, `drainTimeoutMs` và runbook; tuyệt đối không dùng câu lệnh SQL để ép đóng ca |
| 3 | Ngưỡng lệch, tiền đầu ca tối đa, thời gian drain timeout và khoảng chồng lấn hiệu lực chính sách cần cấu hình vận hành | Giá trị cấu hình không phù hợp có thể khiến chi nhánh bị nghẽn hoặc giảm mức độ an toàn | Đưa vào cấu hình theo từng tenant/chi nhánh với cơ chế audit và thẩm định chặt chẽ |
| 4 | Chưa sinh các artifact máy đọc OpenAPI/AsyncAPI/migration | Chưa nên bắt đầu triển khai controller/DTO/migration trực tiếp từ văn bản mô tả | Sinh các file đặc tả kỹ thuật và fixture hợp đồng ngay trong Phase 1 |
| 5 | Ca đối soát cần có SLA rõ ràng cho người phụ trách/vận hành và chính sách lưu trữ thực tế | Các sự kiện trễ có thể tồn đọng kéo dài nếu không có quy trình | Thiết lập cảnh báo theo tuổi của ca, dashboard theo dõi DLQ và job lưu trữ/lưu trữ dữ liệu cũ trước khi lên production |

Không có vấn đề mức độ nghiêm trọng (critical issue). Các cảnh báo không làm thay đổi kiến trúc thiết kế, nhưng phải được đưa vào danh sách công việc cần xử lý ở khâu triển khai và vận hành.

## Đối chiếu khả năng truy xuất nguồn gốc (Traceability review)

| Yêu cầu nghiệp vụ | Kiến trúc xử lý | Hợp đồng quy định |
|---|---|---|
| Mở ca + nộp tiền đầu ca | Public API, Shift DB, partial unique slot | `POST /branches/{branchId}/shifts`, `ShiftOpened.v1` |
| Đóng ca | Fence Coordinator, finalizer, outbox | `POST /shifts/{shiftId}/close`, `ShiftClosed.v1` |
| Chỉ duy nhất 1 ca/chi nhánh | Row lock + partial unique index | Mã lỗi `shift_already_open`, các fixture concurrency |
| Hủy ca bị bỏ quên | Cancel command, audit hoàn trả tiền đầu ca | `POST /cancel`, `ShiftCancelled.v1` |
| Kiểm đếm tiền mặt P1 | Cash Summary Engine, bản ghi đếm bất biến | `POST /cash-counts`, `CashCountAttempt`, `ShiftCountSubmitted.v1` |
| Ngưỡng lệch cho phép | Policy snapshot + trạng thái review | `ShiftReviewRequired.v1`, các API approve/recount |
| Thu hồi / đếm lại | Lịch sử kiểm đếm/review chỉ ghi nối (append-only) | Trạng thái `RECOUNT_REQUIRED`, lượt đếm mới nhất + If-Match |
| Sự kiện trễ / trùng lặp | Khử trùng lặp + worker đối soát | Bảng `cash_facts`, các endpoint và sự kiện reconciliation |
| Phân quyền RBAC / cách ly chi nhánh | Cognito + kiểm tra ở service và repository | Ma trận phân quyền, mã `403/404`, các fixture kiểm tra IDOR |
| Kiểm toán / Báo cáo | Outbox → EventBridge/SQS | Event envelope chuẩn + Consumer của Audit |

## Tóm tắt kiến trúc hệ thống (Architecture summary)

- **Service chính:** Shift Service, stateless API kết hợp background worker đóng ca / đối soát.
- **Cơ sở dữ liệu:** PostgreSQL độc lập sở hữu các bảng: `shifts`, `shift_transaction_admissions`, `cash_facts`, `cash_count_attempts`, `reconciliation_cases`, `shift_policies`, `outbox_messages`, `idempotency_records` và `audit_references`.
- **Ranh giới nhất quán:** Khóa hàng (row lock) / số thứ tự tăng dần (sequence) / phiên bản (version) trong PostgreSQL.
- **Public APIs:** 9 endpoints phục vụ: mở ca, xem ca hiện tại, tra cứu lịch sử, chi tiết, đóng ca, hủy ca, kiểm đếm lại, phê duyệt và yêu cầu đếm lại.
- **Internal APIs:** 5 endpoints phục vụ: xin tiếp nhận giao dịch, hoàn tất tiếp nhận, nạp sự kiện tiền mặt, tóm tắt tiền mặt, giải quyết ca đối soát.
- **Sự kiện (Events):** 9 sự kiện Shift chuẩn, được phân phối qua Transactional Outbox → EventBridge/SQS → Reporting/Audit/vận hành.
- **Bảo mật then chốt:** Cognito JWT cho public API, workload identity/mTLS cho internal API, ràng buộc vị từ tenant/chi nhánh bắt buộc tại cả application và repository layer.

## Lộ trình triển khai (Implementation roadmap)

### Phase 1 — Nền tảng, cấu trúc DB và sinh hợp đồng chuẩn (Foundation, schema and generated contracts)

- **Mục tiêu:** Hoàn thiện database boundary, OpenAPI/AsyncAPI và middleware dùng chung đúng theo hợp đồng thiết kế.
- **Các nhiệm vụ:**
  - Sinh OpenAPI 3.1, AsyncAPI/JSON Schema và các envelope chuẩn cho thành công/lỗi.
  - Viết các script migration tạo bảng, ghi chú cột (remarks), ràng buộc toàn vẹn, chỉ mục partial unique index và seed dữ liệu chính sách mặc định.
  - Cài đặt ngữ cảnh xác thực tenant/chi nhánh/RBAC, kho lưu trữ idempotency, UUID v7, chuyển đổi múi giờ UTC/chi nhánh.
  - Cài đặt Transactional Outbox, bộ khử trùng lặp sự kiện/inbox và module ghi nhận audit reference.
  - Xây dựng các fixture kiểm thử hợp đồng chuẩn bị cho việc sửa đổi Order/Payment.
- **Ước tính thời gian:** 3–5 ngày làm việc.
- **Phụ thuộc:** Ngữ cảnh Identity/Cognito, PostgreSQL, quy ước chuẩn EventBridge/SQS.

### Phase 2 — Vòng đời P0 và rào chắn đóng ca (P0 lifecycle and close fence)

- **Mục tiêu:** Mở ca / xem ca hiện tại / đóng ca / hủy ca vận hành an toàn và ổn định trong một chi nhánh.
- **Các nhiệm vụ:**
  - Xử lý mở ca + kiểm soát đồng thời active-slot + tính lũy nghiệm.
  - Truy vấn ca hiện tại / lịch sử / chi tiết ca và thực thi nghiêm ngặt phạm vi truy cập dữ liệu.
  - Cài đặt số thứ tự tiếp nhận, chuyển đổi pha `OPEN → CLOSING → FROZEN`, bản ghi thao tác đóng ca.
  - Xây dựng worker xử lý drain khi đóng ca, timeout chuyển sang review và ngữ nghĩa phản hồi `202 PROCESSING`.
  - Audit đóng ca / hủy ca P0, phát sự kiện outbox và cơ chế tự phục hồi sau khi restart worker.
- **Ước tính thời gian:** 4–6 ngày làm việc.
- **Phụ thuộc:** Phase 1.

### Phase 3 — Tích hợp Order / Payment / Saga (Order/Payment/Saga integration)

- **Mục tiêu:** Đảm bảo không có bất kỳ đơn hàng checkout hay sự kiện tiền mặt nào bị thiếu quy kết ca làm việc.
- **Các nhiệm vụ:**
  - Order bắt buộc lưu `shiftId` và lưu trữ ngữ cảnh tiếp nhận trong nỗ lực checkout.
  - Saga thực hiện xin và tái sử dụng quyền tiếp nhận (admission) một cách lũy nghiệm.
  - Payment nhận và thẩm định các trường `shiftId`, `admissionId`, `admittedAt` khi khởi tạo/hoàn tiền.
  - Nạp các sự kiện thu/hoàn tiền mặt, xác nhận hoàn tất, khử trùng lặp nguồn và xử lý các sự kiện đến trễ.
  - Phát và cập nhật metadata trong các sự kiện `PaymentPaid.v1` và `PaymentRefunded.v1`.
- **Ước tính thời gian:** 4–6 ngày làm việc.
- **Phụ thuộc:** Phase 1–2; đồng bộ hợp đồng kỹ thuật với Order/Payment.

### Phase 4 — Kiểm đếm P1, phê duyệt và đối soát (P1 count, review and reconciliation)

- **Mục tiêu:** Đưa tính năng kiểm đếm tiền / xử lý chênh lệch / kiểm đếm lại vào vận hành với đầy đủ lưu vết audit.
- **Các nhiệm vụ:**
  - Xây dựng watermark tóm tắt tiền mặt, kiểm tra tính đầy đủ và các bộ test kiểm tra công thức tính toán.
  - Tích hợp kiểm đếm ban đầu vào thao tác đóng ca, định tuyến theo ngưỡng lệch, lưu bản ghi kiểm đếm bất biến.
  - Quản lý / Chủ chuỗi thực hiện phê duyệt / yêu cầu đếm lại với cơ chế kiểm soát phiên bản lạc quan.
  - Quy trình mở/giải quyết ca đối soát, phát sự kiện và phân quyền nhân viên vận hành.
  - Triển khai các consumer của Reporting/Audit tiếp nhận dữ liệu ca làm việc và chênh lệch tiền mặt.
- **Ước tính thời gian:** 4–6 ngày làm việc.
- **Phụ thuộc:** Phase 2–3.

### Phase 5 — Kiểm thử xác thực, triển khai và vận hành (Verification, rollout and operations)

- **Mục tiêu:** Đầy đủ các bài kiểm thử tranh chấp / hợp đồng / replay sự kiện và có đầy đủ runbook vận hành production.
- **Các nhiệm vụ:**
  - Viết unit test cho các phép toán tiền tệ và state machine; integration test kiểm tra cơ chế khóa của PostgreSQL.
  - Contract test tích hợp với Order/Payment/Saga; kiểm thử replay sự kiện, khử trùng lặp và xử lý qua DLQ.
  - Load test luồng tiếp nhận / xem ca hiện tại / đóng ca; kiểm tra bảo mật chống IDOR và phân quyền RBAC.
  - Xây dựng dashboard và cảnh báo: tuổi thọ ca chờ duyệt, timeout khi drain đóng ca, sự kiện đến trễ, độ trễ outbox lag.
  - Soạn thảo tài liệu lưu trữ/dọn dẹp dữ liệu cũ, SLA giải quyết đối soát, quy trình rollback và bù dữ liệu an toàn.
  - Kích hoạt tính năng kiểm đếm P1 qua feature flag theo từng tenant/chi nhánh; triển khai theo hình thức canary trước khi mở rộng toàn chuỗi.
- **Ước tính thời gian:** 3–5 ngày làm việc.
- **Phụ thuộc:** Phase 1–4.

## Sơ đồ tuần tự (Sequence diagrams)

- **Trạng thái:** Đã sinh xong.
- **Tài liệu:** [`2026-10-13-sequence-diagrams.adoc`](file:///d:/vibe-code/pos/docs/designs-vi/shift/2026-10-13-sequence-diagrams.adoc).
- **Các luồng nghiệp vụ được bao quát:** Tranh chấp mở ca; tiếp nhận trước và sau rào chắn; đóng ca P0; kiểm đếm/phê duyệt P1; đếm lại; hủy ca bị bỏ quên; sự kiện tiền mặt đến trễ hoặc trùng lặp.

## Các bước tiếp theo (Next steps)

- [ ] Đồng bộ các tài liệu hợp đồng của nền tảng POS Platform, Order và Payment.
- [ ] Xác nhận các giá trị mặc định cho vận hành: `drainTimeoutMs`, số tiền đầu ca tối đa, độ ưu tiên/chồng lấn hiệu lực chính sách, SLA giải quyết review.
- [ ] Sinh các artifact kỹ thuật máy đọc: OpenAPI 3.1, AsyncAPI và migration scripts.
- [ ] Bắt đầu triển khai Phase 1 theo quy trình `.agent/workflows/feature.md`.
- [ ] Thiết lập các bước kiểm tra tự động trên CI để thẩm định schema, contract tests và độ an toàn của migration.
- [ ] Xây dựng dashboard giám sát và tài liệu hướng dẫn vận hành (runbook) trước khi bật tính năng kiểm đếm P1 trên production.

## Danh mục kiểm tra cuối cùng (Final checklist)

- [x] Toàn bộ các yêu cầu P0/P1 đều được truy vết tương ứng đến các thành phần kiến trúc.
- [x] Tất cả các component đều có đầy đủ hợp đồng API, cấu trúc dữ liệu và sự kiện.
- [x] Các yêu cầu phi chức năng (NFR) được giải quyết triệt để thông qua các quyết định về khóa, phiên bản, tính lũy nghiệm, outbox và bảo mật.
- [x] Cơ chế kiểm tra tenant/chi nhánh/RBAC/IDOR được định nghĩa chặt chẽ trên từng đường đi của dữ liệu.
- [x] Tranh chấp giữa đóng ca và thanh toán cũng như hành vi xử lý sự kiện trễ được biểu diễn rõ ràng trên các sơ đồ tuần tự.
- [x] Công thức tiền tệ và cơ chế lưu trữ bản ghi lịch sử bất biến được xác định chuẩn xác.
- [x] Lộ trình triển khai có tính hành động cao và phân chia giai đoạn hợp lý.
- [ ] Đồng bộ hóa các tài liệu liên service cấp nền tảng.
- [ ] Sinh các file hợp đồng máy đọc và migration scripts.
- [ ] Xác nhận các thông số vận hành mặc định và cam kết SLA.

## Cổng phê duyệt cuối cùng (Final gate)

Bộ tài liệu thiết kế Module 7 được đánh giá là **APPROVED WITH WARNINGS** và sẵn sàng chuyển sang bước lập kế hoạch triển khai mã nguồn. Tuyệt đối không lập trình trực tiếp từ các giả định cũ trong tài liệu nền tảng ban đầu; bắt buộc bám sát tài liệu Hợp đồng Shift đã được phê duyệt và hoàn tất các hạng mục cảnh báo nêu trên trước khi merge các luồng tích hợp P0.
