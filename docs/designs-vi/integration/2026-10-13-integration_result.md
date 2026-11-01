# Module 12 — Tích hợp mở rộng: Kết quả thiết kế cuối cùng (Final Design Result)

**Ngày:** 2026-10-13  
**Trạng thái:** Thiết kế hoàn tất — Phê duyệt có cảnh báo (Design complete — Approved with warnings)  
**Phán quyết:** VÀNG — Tiến hành thận trọng (YELLOW — Proceed with Caution)  
**Phạm vi:** 8 tài liệu thiết kế (design artifacts), chưa có mã ứng dụng (application code)

## 1. Kết quả cuối cùng (Final outcome)

Thiết kế Module 12 đã hoàn tất qua các cổng kiểm soát (gate) Vision → Requirements → Architecture → Sequence → Contracts → Finalization.

Module 12 gồm:

- **Integration Runtime:** tác vụ hóa đơn kỹ thuật/sandbox từ sự kiện chuẩn `OrderPaid.v1`, phân phối email/Zalo bất đồng bộ, các adapter kết nối nhà cung cấp, retry/DLQ, API trạng thái, ranh giới consent/PII và khả năng quan sát (observability).
- **Demo Control Plane:** trình điều phối kịch bản chỉ chạy trên môi trường non-production, namespace dữ liệu mẫu/reset, các checkpoint, lease cho đợt chạy, chèn lỗi `PAYMENT_UNAVAILABLE` và tóm tắt trace X-Ray.
- **Ranh giới sở hữu (Ownership boundary):** không sở hữu hoặc thay thế trạng thái nghiệp vụ của Order, Payment, Step Functions Saga, Inventory, Loyalty, Recommendation, Reporting hay AI.

Hóa đơn/phân phối P2 không nằm trên đường dẫn thanh toán đồng bộ (synchronous checkout path). Luồng Demo P0 chạy qua service thật và VNPAY sandbox; `OrderPaid.v1` vẫn là chân lý đơn hàng đã thanh toán; Reporting/AI vẫn là cơ quan thẩm quyền cho các chỉ số doanh thu gia tăng (uplift metric).

## 2. Sổ đăng ký tài liệu thiết kế (Artifact registry)

| Tài liệu | Vai trò | Trạng thái |
|---|---|---|
| [Vision](./2026-10-13-vision.md) | Phạm vi, giá trị, các bên liên quan, ranh giới | Phê duyệt có cảnh báo |
| [Vision review](./2026-10-13-vision-review.md) | Đánh giá tính sẵn sàng về bảo mật/tác động chéo | Cảnh báo đã được chấp nhận |
| [Requirements](./2026-10-13-requirements.md) | Hóa đơn, phân phối, demo, quyền riêng tư, NFR, tiêu chí chấp nhận | Phê duyệt có cảnh báo |
| [Requirements review](./2026-10-13-requirements-review.md) | Đánh giá kiểm soát yêu cầu nghiệp vụ | Cảnh báo đã được chấp nhận |
| [Architecture](./2026-10-13-architecture.md) | Phân tách runtime/demo, kiến trúc dữ liệu/giao tiếp/xử lý sự cố | Đã phê duyệt |
| [Sequence diagrams](./2026-10-13-sequence-diagrams.adoc) | 8 luồng × L1/L2 = 16 khối PlantUML | Đã xác nhận và xác thực |
| [Contracts](./2026-10-13-contracts.md) | REST, callback, cổng provider, sự kiện, DB, lưu giữ dữ liệu | Phê duyệt có cảnh báo |
| [Contracts review](./2026-10-13-contracts-review.md) | Đánh giá kiểm soát hợp đồng API/dữ liệu | Cảnh báo đã được chấp nhận |

## 3. Tóm tắt đánh giá thiết kế (Design review summary)

| Danh mục | Phán quyết | Bằng chứng / hành động còn lại |
|---|---|---|
| Phạm vi và độ hoàn thiện | ĐẠT có cảnh báo (PASS with warnings) | Đã bao phủ hóa đơn/phân phối, demo thành công/thất bại/reset, gán ghi nhận AI và ranh giới sở hữu. Các sửa đổi bổ sung của service cha vẫn là điều kiện tiên quyết (gates). |
| Bảo mật và cô lập tenant | ĐẠT với điều kiện triển khai (PASS with implementation gate) | Phạm vi Cognito/workload, điều kiện lọc repository, ranh giới callback, che giấu PII và phân tách production/demo đã được định nghĩa; bộ cố định mã hóa/khóa/lease vẫn là yêu cầu bắt buộc. |
| Logic nghiệp vụ | ĐẠT với điều kiện triển khai | Máy trạng thái hóa đơn/phân phối/demo và các đường dẫn provider không xác định/thử lại đã được định nghĩa; việc đối soát provider và tương thích OrderPaid yêu cầu hợp đồng khả thi bằng mã. |
| Toàn vẹn dữ liệu | ĐẠT với điều kiện triển khai | Inbox/outbox, khóa hội tụ, CAS, các lượt thử bất biến và đối soát tiền bạc đã được định nghĩa; vai trò/chú thích/migration Flyway vẫn là yêu cầu bắt buộc. |
| Tính sẵn sàng API/sự kiện | CẢNH BÁO (WARN) | Danh mục API, các route callback và schema sự kiện đã tồn tại ở cấp thiết kế; OpenAPI/AsyncAPI/sổ đăng ký sự kiện chưa được sinh ra dạng máy đọc được. |
| Hiệu năng | ĐẠT với điều kiện triển khai | Các worker bất đồng bộ, giới hạn tốc độ, danh sách có giới hạn và mục tiêu tải đã được định nghĩa; hồ sơ tốc độ provider và bằng chứng k6 vẫn là yêu cầu bắt buộc. |
| Khả năng quan sát (Observability) | ĐẠT với điều kiện triển khai | Các ID correlation/causation/saga/demo, X-Ray, metric, DLQ và chất lượng checkpoint đã được định nghĩa; dashboard/runbook vẫn là các tài liệu giai đoạn triển khai. |
| Kiểm thử | CẢNH BÁO (WARN) | Ma trận kiểm thử bao phủ replay, provider, PII, demo và gán ghi nhận AI; bộ dữ liệu kiểm thử thực thi cho service cha/provider/cleanup/retention vẫn là yêu cầu bắt buộc. |

## 4. Báo cáo đánh giá mã nguồn / thiết kế (Code Review Report)

**Phạm vi:** 8 tài liệu thiết kế trong `docs/designs/integration/` — Vision, Vision review, Requirements, Requirements review, Architecture, Sequence diagrams, Contracts, Contracts review. Chưa có mã nguồn ứng dụng hay mã sinh tự động nào được đánh giá.  
**Ngày:** 2026-10-13  
**Phán quyết:** VÀNG — Tiến hành thận trọng (YELLOW — Proceed with Caution)

### Tóm tắt (Summary)

Thiết kế mạch lạc, bảo toàn quyền sở hữu của các service cha, cô lập lỗi bất đồng bộ và đảm bảo an toàn cho demo trên môi trường non-production. Không phát hiện rào cản nghiêm trọng nào về bảo mật hay kiến trúc; các cảnh báo là những điều kiện tiên quyết cần cụ thể hóa trước khi viết mã nguồn production.

### Cảnh báo cần xử lý (Warnings - Should Fix)

| # | File:Line | Danh mục | Vấn đề | Đề xuất giải quyết |
|---|---|---|---|---|
| 1 | [`2026-10-13-contracts.md:19-27`](./2026-10-13-contracts.md#L19) | Tính tương thích API/Sự kiện | Ánh xạ tương thích giữa `OrderPaid` hiện tại của service cha + phiên bản 1 với mục tiêu `OrderPaid.v1` chưa được đưa vào sổ đăng ký thực thi được. | Xuất bản sổ đăng ký sự kiện chuẩn, chính sách alias/cắt chuyển, phiên bản nguồn và kịch bản kiểm thử replay. |
| 2 | [`2026-10-13-contracts.md:141-159`](./2026-10-13-contracts.md#L141) | Bảo mật / Logic nghiệp vụ | Việc ghi nhận `invoiceRecipientRef` chưa có hợp đồng xác thực/ngữ cảnh đầy đủ của Order. | Bổ sung schema xác thực nội bộ của Order, thời hạn hết hạn/ràng buộc và hành vi khi thiếu ref trước khi sinh client. |
| 3 | [`2026-10-13-contracts.md:371-404`](./2026-10-13-contracts.md#L371) | Độ tin cậy / Bảo mật | Trạng thái `Unknown` của Provider, cơ chế đối soát và ngữ nghĩa chữ ký callback/replay chưa được hiện thực hóa thành các vector kiểm thử thực thi. | Thêm tác vụ đối soát, schema callback/bộ dữ liệu kiểm thử chữ ký, lease và sổ tay vận hành (runbook) provider. |
| 4 | [`2026-10-13-contracts.md:277-296`](./2026-10-13-contracts.md#L277) | Đồng thời / API | Thứ tự ưu tiên giữa `If-Match` và `expectedVersion`, phản hồi replay idempotent và các header cache chưa được chốt trong OpenAPI. | Chọn một nguồn thẩm quyền duy nhất về phiên bản, xác định rõ hành vi 409/replay/ETag/no-cache. |
| 5 | [`2026-10-13-contracts.md:490-508`](./2026-10-13-contracts.md#L490) | Quyền riêng tư | Trường mã hóa PII còn cho phép `BYTEA/TEXT ref`, chưa chốt phiên bản khóa KMS/tokenization/xoay vòng khóa. | Hiện thực hóa một ranh giới mã hóa duy nhất, các role bị hạn chế, việc xoay vòng khóa và kiểm thử xóa dữ liệu. |
| 6 | [`2026-10-13-contracts.md:593-608`](./2026-10-13-contracts.md#L593) | Lưu giữ dữ liệu | Chính sách lưu giữ 2 năm/30 ngày của Integration và tham chiếu 7 năm của Audit cần bản kê khai phân loại dữ liệu/tạm hoãn pháp lý (legal-hold) cụ thể. | Tạo ma trận lưu giữ/tạm hoãn pháp lý và bộ dữ liệu kiểm thử việc tồn tại của dữ liệu lưu trữ/tham chiếu. |
| 7 | [`2026-10-13-contracts.md:551-557`](./2026-10-13-contracts.md#L551) | An toàn Demo | Các adapter dọn dẹp seed/reset của service cha và phục hồi khi dọn dẹp một phần chưa có hợp đồng sở hữu dạng máy đọc được. | Định nghĩa manifest tài nguyên, thứ tự dọn dẹp ngược, TTL, số lần thử lại và chốt chặn cấm xóa cho từng service cha. |
| 8 | [`2026-10-13-contracts.md:330-344`](./2026-10-13-contracts.md#L330) | Bảo mật / Vận hành | Cơ chế lan truyền/chứng thực fault lease và mức độ ưu tiên của nút tắt khẩn cấp (kill-switch) chưa đầy đủ. | Bổ sung ngữ cảnh đợt chạy có chữ ký số, chứng thực môi trường, TTL tối đa và xác minh adapter Payment. |
| 9 | [`2026-10-13-contracts.md:433-443`](./2026-10-13-contracts.md#L433) | Gán ghi nhận AI (AI attribution) | Bằng chứng thêm dòng món có thẩm quyền của Recommendation/Order và hàm doanh thu gia tăng của Reporting chưa được cụ thể hóa trong schema service cha. | Giữ Integration ở chế độ chỉ đọc; xuất bản sổ đăng ký attribution/chỉ số của service cha và bộ dữ liệu mẫu chuẩn (golden fixtures). |

### Tóm tắt danh mục kiểm tra (Checklist summary)

| Danh mục | Phán quyết | Vấn đề |
|---|---|---:|
| Bảo mật (Security) | CẢNH BÁO (WARN) | 3 điều kiện tiên quyết triển khai; không có vấn đề nghiêm trọng |
| Logic nghiệp vụ (Business Logic) | CẢNH BÁO (WARN) | 3 điều kiện tiên quyết triển khai; không có vấn đề nghiêm trọng |
| Hiệu năng (Performance) | ĐẠT (PASS) | Mục tiêu giới hạn API/worker/tải đã định nghĩa; đang chờ bằng chứng kiểm thử tải |
| Code sạch (Clean Code) | ĐẠT (PASS) | Trách nhiệm được phân tách rõ thành runtime, adapter và demo control plane; chưa có mã ứng dụng |
| Quy ước (Conventions) | CẢNH BÁO (WARN) | Các tài liệu máy đọc được và sổ đăng ký service cha chưa được sinh ra |

## 5. Tóm tắt kiến trúc (Architecture summary)

```text
Order/Payment/Shift/Inventory/Loyalty/Recommendation/Reporting/AI
              │ API/sự kiện chuẩn + outbox/inbox
              ▼
EventBridge → SQS pos-integration-events → Integration inbox/dedupe
                                                │
                        ┌───────────────────────┴──────────────────────┐
                        │ Integration Runtime                          │
                        │ recipient → invoice → delivery workers       │
                        │ provider adapters / status / audit references │
                        └───────────────────────────────────────────────┘

Demo Control Plane (chỉ áp dụng DEMO/TEST)
  → gọi API service cha thật → checkpoints/resource manifest → tóm tắt X-Ray
  → fault lease Payment trong phạm vi đợt chạy → xác nhận bồi hoàn Saga Order
```

Mô hình tính nhất quán: service cha sở hữu chân lý nghiệp vụ; Integration hoạt động theo cơ chế phân phối ít nhất một lần / nhất quán sau cùng; các lệnh gọi provider sử dụng idempotency/đối soát; các checkpoint của demo đọc trực tiếp từ API/sự kiện có thẩm quyền; trạng thái chất lượng dữ liệu cũ/một phần/suy giảm trace được thể hiện tường minh.

## 6. Các điều kiện tiên quyết trước khi viết code (Implementation gates before code)

1. Xuất bản sửa đổi bổ sung cho `OrderPaid.v1` với trường opaque `invoiceRecipientRef`, hồ sơ cấu hình, phiên bản lượt thử/nguồn, tổng tiền/dòng món an toàn và chính sách alias tương thích.
2. Xuất bản `openapi.yaml`, `openapi-internal.yaml`, schema callback và các kiểu client sinh tự động.
3. Xuất bản `asyncapi.yaml` và `integration-event-registry.yaml` kèm theo route, phiên bản, khóa khử trùng lặp, che giấu dữ liệu nhạy cảm và alias service cha.
4. Hiện thực hóa các cổng provider và fixture sandbox cho hóa đơn, email và Zalo: đã chấp nhận, timeout, 429, 5xx, không xác định, replay callback và lỗi chữ ký.
5. Hiện thực hóa các migration PostgreSQL/Flyway, role bị hạn chế đối với PII, siêu dữ liệu KMS/khóa, chú thích, ràng buộc, chỉ mục và tác vụ lưu giữ dữ liệu.
6. Xuất bản schema kịch bản demo/resource-manifest/checkpoint và các adapter seed/reset cho từng service cha với quyền sở hữu dọn dẹp rõ ràng.
7. Xuất bản hợp đồng đợt chạy/fault-lease có chữ ký, fixture ranh giới Payment, nút tắt khẩn cấp (kill-switch) và kiểm thử cô lập môi trường.
8. Xuất bản sổ đăng ký attribution và chỉ số doanh thu gia tăng của Recommendation/Order/Reporting/AI cùng bộ dữ liệu chuẩn (golden data).
9. Bổ sung kiểm thử bảo mật/quyền riêng tư cho IDOR, tư cách thành viên bị thu hồi, cache phạm vi, replay callback, che giấu PII/bí mật và từ chối route production.
10. Chạy kiểm thử tải/khôi phục cho 10 sự kiện thanh toán/giây, 100 lượt thử phân phối, 10 đợt chạy demo đồng thời, replay DLQ, sự cố sập provider và xung đột reset.

## 7. Lộ trình triển khai (Implementation roadmap)

| Giai đoạn | Phạm vi | Tiêu chí hoàn thành (Exit criteria) |
|---|---|---|
| 0. Hợp đồng service cha | Sửa đổi `OrderPaid`, quyền Identity, sổ đăng ký sự kiện, schema attribution/chỉ số, hồ sơ khả năng của provider | Các schema hợp lệ; alias/dedupe/scope và các fixture service cha vượt qua kiểm thử |
| 1. Nền tảng Integration | Runtime API/xác thực, PostgreSQL, inbox/outbox, snapshot đồng thuận người nhận, read model/trạng thái an toàn | Vượt qua kiểm thử tenant/chi nhánh/PII/CAS/idempotency; không có liên hệ thô trong log/sự kiện |
| 2. Luồng kỹ thuật hóa đơn | Consumer `OrderPaid`, tác vụ hóa đơn, adapter sandbox, retry/unknown/reconcile/DLQ | Các fixture trùng lặp/replay/sập mạng/provider hội tụ về đúng một hóa đơn kỹ thuật |
| 3. Kênh phân phối | Adapter Email/Zalo, cổng đồng thuận, idempotency kênh, callback, giới hạn tốc độ, thử lại thủ công | Vượt qua các fixture thành công/thất bại/hết hạn/thu hồi/callback/bảo mật |
| 4. Demo Control Plane | API chạy non-prod, preflight, manifest seed/reset, checkpoint, tóm tắt X-Ray, fault lease | Demo thành công toàn phần và demo bồi hoàn khi Payment ngừng hoạt động vượt qua kiểm thử mà không rò rỉ chéo |
| 5. Gán ghi nhận AI và hoàn thiện | Tích hợp hàm Reporting/Recommendation/AI, kiểm thử chuẩn về doanh thu gia tăng, dashboard, lưu giữ/khôi phục dữ liệu | Câu trả lời an toàn/provenance của AI chính xác; các cổng kiểm thử tải/bảo mật/lưu giữ/replay/runbook được duyệt |

Thời gian ước tính: khoảng **5–8 tuần** cho các lát cắt chức năng theo chiều dọc (vertical slices) và tối ưu hóa hệ thống, không tính thời gian duyệt hợp đồng của service cha, phê duyệt từ provider và cấp phát hạ tầng.

## 8. Ma trận truy vết yêu cầu (Traceability matrix)

| Yêu cầu người dùng | Độ bao phủ thiết kế | Hợp đồng / Bằng chứng |
|---|---|---|
| Hóa đơn điện tử từ máy tính tiền | Consumer `OrderPaid`, hồ sơ sandbox kỹ thuật, máy trạng thái hóa đơn / adapter provider | Yêu cầu INT-FR-010..019; Hợp đồng §6–7; Sequence 2 |
| Gửi qua email/Zalo | Snapshot đồng thuận, adapter kênh, phân phối bất đồng bộ / retry / hết hạn / trạng thái | Yêu cầu INT-FR-001..006, INT-FR-020..027; Hợp đồng §3–6; Sequence 3 |
| Demo P0 thành công | Đợt chạy/seed/preflight/checkpoint của Demo, API service cha thật, VNPAY sandbox, xác minh Loyalty/Inventory/Reporting/AI | Yêu cầu INT-FR-030..044; Kiến trúc §5; Sequence 4/6 |
| Bồi hoàn sự cố gián đoạn Payment | Fault lease trong phạm vi đợt chạy, thẩm quyền Saga Order, xác nhận giải phóng Inventory, xác nhận không có paid | Yêu cầu INT-FR-036; Hợp đồng §5.3; Sequence 5 |
| Trace X-Ray | ID correlation/causation/saga/demo, tham chiếu trace, trạng thái chất lượng suy giảm | Vision §4.2; Hợp đồng §3.5/§12; tất cả các luồng sequence |
| Truy vấn AI về doanh thu gia tăng từ Coca | Các hàm Reporting/Recommendation đã phê duyệt, attribution thanh toán, độ tươi mới/provenance, giới hạn an toàn | Yêu cầu INT-FR-040..044; Sequence 6 |
| Thử lại / replay / khôi phục | Inbox/outbox, idempotency provider, khử trùng lặp callback, đối soát, DLQ và reset lease | Hợp đồng §7/§9/§11; Sequence 7/8 |
| An toàn tenant / quyền riêng tư | Phạm vi Cognito/workload, liên hệ đã che giấu, ranh giới KMS, chốt chặn môi trường, tham chiếu Audit | Yêu cầu §2/§7/§10; Hợp đồng §2/§10/§12 |

## 9. Danh mục kiểm tra cuối cùng (Final checklist)

- [x] Vision, Vision review, Requirements, Requirements review, Architecture và Contracts đã được tạo.
- [x] Người dùng đã duyệt Vision, Requirements, Architecture/sequence participants và các cảnh báo của Contracts.
- [x] Integration Runtime và Demo Control Plane (chỉ áp dụng non-production) đã được tách biệt ranh giới rõ ràng.
- [x] Trigger chuẩn `OrderPaid`, hóa đơn/phân phối bất đồng bộ và nguyên tắc không đột biến chéo domain đã được chốt.
- [x] 8 luồng sequence và 16 khối PlantUML đã được tạo/kiểm tra.
- [x] Adapter provider, consent/PII, retry/DLQ, fault lease cho demo, X-Ray và gán ghi nhận AI đã có độ bao phủ hợp đồng.
- [ ] Các schema máy đọc được OpenAPI/AsyncAPI/JSON Schema và sửa đổi bổ sung của service cha được xuất bản.
- [ ] Các migration Flyway, role PII/chính sách khóa, vector kiểm thử chữ ký provider và manifest lưu giữ dữ liệu được tạo.
- [ ] Adapter seed/reset demo, kiểm thử bảo mật fault-lease và môi trường dịch vụ đầy đủ được cấp phát.
- [ ] Vượt qua các cổng kiểm tra tải, replay, callback, IDOR, quyền riêng tư, chỉ số AI và sổ tay vận hành.
- [ ] Chỉ sau khi vượt qua tất cả các cổng triển khai (implementation gates) trên mới bắt đầu viết mã nguồn production.

**Kết luận:** Module 12 đã hoàn tất ở mức thiết kế sẵn sàng cho triển khai (implementation-ready design) với phán quyết **APPROVED WITH WARNINGS (PHÊ DUYỆT CÓ CẢNH BÁO)**. Tất cả các cảnh báo đều đã được chuyển thành các điều kiện tiên quyết triển khai có người chịu trách nhiệm và tài liệu rõ ràng. Chưa viết mã ứng dụng cho production.
