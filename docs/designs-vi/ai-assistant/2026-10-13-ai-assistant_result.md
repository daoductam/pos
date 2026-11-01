Started at: 2026/10/13 00:00:00
Finished at: 2026/10/13 00:00:00
Total time: 0 minutes
---

# Module 9 — Trợ lý AI cho chủ quán: Hoàn thiện Thiết kế (Design Finalization)

**Ngày:** 2026-10-13  
**Trạng thái:** **APPROVED WITH WARNINGS** (Đã phê duyệt kèm cảnh báo)  
**Phạm vi:** Trợ lý AI cho chủ quán — Dịch vụ AI, LLM / DeepSeek

## 1. Tóm tắt điều hành (Executive Summary)

Thiết kế đã hoàn tất đầy đủ cả 5 giai đoạn: Tầm nhìn (Vision) → Yêu cầu nghiệp vụ (Requirements) → Kiến trúc (Architecture) → Giao ước (Contracts) → Hoàn thiện thiết kế (Finalization).

Module cung cấp:

- P1: Hỏi đáp báo cáo bằng ngôn ngữ tự nhiên thông qua cơ chế DeepSeek Function Calling và danh mục trắng công cụ Reporting.
- P1: Rào chắn bảo vệ phạm vi tenant/chi nhánh theo quyết định phân quyền trực tuyến từ Identity, phòng thủ chiều sâu tại Tool Gateway và repository của Reporting.
- P1: Che giấu thông tin PII, kiểm toán đã lược bỏ dữ liệu nhạy cảm, ranh giới chỉ đọc và cơ chế đóng ngắt an toàn (fail-closed) khi nhà cung cấp hoặc công cụ không an toàn.
- P2: Báo cáo tóm tắt ngày theo ngày kinh doanh/múi giờ của chi nhánh, mốc so sánh, phân tích đóng góp, mốc kiểm chứng độ tươi mới và phiên bản hiệu chỉnh bất biến khi có dữ liệu phát sinh muộn.

Không phát hiện bất kỳ vấn đề chặn nghiêm trọng (critical blocker) nào trong thiết kế. Dự án có thể bước vào giai đoạn triển khai sau khi hoàn tất các artifact máy đọc được và các sửa đổi bổ sung của dịch vụ cha được liệt kê tại Mục §7.

## 2. Sổ đăng ký Artifact (Artifact Registry)

| Giai đoạn | Tài liệu Artifact | Trạng thái |
|---|---|---|
| Tầm nhìn (Vision) | [2026-10-13-vision.md](./2026-10-13-vision.md) | Đã phê duyệt |
| Đánh giá tầm nhìn (Vision Review) | [2026-10-13-vision-review.md](./2026-10-13-vision-review.md) | Chấp nhận cảnh báo và xử lý ở các giai đoạn sau |
| Yêu cầu (Requirements) | [2026-10-13-requirements.md](./2026-10-13-requirements.md) | Đã phê duyệt kèm cảnh báo |
| Đánh giá yêu cầu (Requirements Review) | [2026-10-13-requirements-review.md](./2026-10-13-requirements-review.md) | Các cảnh báo đã được xử lý trong Kiến trúc / Giao ước |
| Kiến trúc (Architecture) | [2026-10-13-architecture.md](./2026-10-13-architecture.md) | Đã phê duyệt |
| Biểu đồ tuần tự (Sequence Diagrams) | [2026-10-13-sequence-diagrams.adoc](./2026-10-13-sequence-diagrams.adoc) | Đã sinh và xác thực hợp lệ; 6 luồng × L1/L2 |
| Giao ước (Contracts) | [2026-10-13-contracts.md](./2026-10-13-contracts.md) | Đã phê duyệt sau khi khắc phục xong cảnh báo |
| Đánh giá giao ước (Contracts Review) | [2026-10-13-contracts-review.md](./2026-10-13-contracts-review.md) | Đã phê duyệt; duy trì các cổng triển khai |

## 3. Đánh giá thiết kế cuối cùng (Final Design Review)

### Cổng đánh giá mã nguồn (Code Review Gate)

**Phạm vi:** 8 tài liệu thiết kế của Module 9 kết hợp với các tham chiếu giao ước cha trực tiếp trong Identity Access và POS Platform/Reporting. Hiện chưa có mã nguồn ứng dụng, script migration hay schema sinh tự động; đây là đợt đánh giá chất lượng thiết kế chứ không phải đánh giá mã nguồn lúc runtime.

**Giai đoạn 0:** Đã bỏ qua — Ngữ cảnh và các quy ước đã được thiết lập vững chắc qua các giai đoạn thiết kế tiền nhiệm.

**Kết luận đánh giá:** **YELLOW — Proceed with Caution (VÀNG — Tiến hành với sự thận trọng)**

Thiết kế hoàn toàn mạch lạc và an toàn để triển khai, tuy nhiên tuyệt đối không bắt đầu viết code chỉ dựa vào mô tả văn xuôi. Các cảnh báo còn lại đóng vai trò là các cổng cụ thể hóa và tích hợp thực tế chứ không phải là các quyết định sản phẩm còn bỏ ngỏ.

### Các vấn đề nghiêm trọng (Critical Issues)

Không có.

### Danh sách cảnh báo (Warnings)

| # | Tệp / Mục | Hạng mục | Phát hiện | Khuyến nghị |
|---|---|---|---|---|
| 1 | `contracts.md` §13; parent Identity Contracts §Internal API | Tích hợp | AI Assistant định nghĩa phần sửa đổi bổ sung phân quyền đa chi nhánh, trong khi tài liệu Identity cha hiện tại vẫn hiển thị cấu trúc `branchId` số ít ban đầu. | Áp dụng phần sửa đổi bổ sung Identity đã duyệt và sinh một schema chuẩn mực duy nhất cho `requestedBranchIds`, `requiredPermission`, `authorizationVersion` và `resolvedScope`. |
| 2 | `contracts.md` §6.4, §12, §14; parent POS Platform Reporting schema | Cụ thể hóa giao ước | Dịch vụ Reporting mới chỉ có khung sườn cho `sales_daily`/`top_items`; các công cụ AOV, theo giờ, chi nhánh và đóng góp được đặc tả trong Module 9 nhưng chưa sinh thành schema dùng chung. | Công bố danh mục registry, mã băm digest, OpenAPI/JSON Schema và các bộ golden metric fixture trước khi viết code. |
| 3 | `contracts.md` §4.4.1, §7.2, §12 | An toàn / Đánh giá | Việc đối soát luận điểm câu trả lời, rà soát PII và xử lý tấn công prompt injection đã được định nghĩa trong giao ước nhưng chưa ở dạng có thể chạy tự động. | Xây dựng bộ xác thực che giấu dữ liệu và tập mẫu dữ liệu đánh giá; đóng ngắt an toàn khi gặp các luận điểm không có căn cứ hoặc đầu ra không an toàn. |
| 4 | `contracts.md` §7.1–§7.3 | Vận hành / Chi phí | Khu vực/lưu lượng mạng ra ngoài của DeepSeek, xoay vòng khóa bí mật, ước tính chi phí và đối soát mức sử dụng thực tế vẫn cần cấu hình môi trường và tài liệu hướng dẫn vận hành (runbook). | Bổ sung các fixture chính sách nhà cung cấp, tích hợp Secrets Manager, cảnh báo ngân sách và tài liệu hướng dẫn xử lý khi nhà cung cấp gặp sự cố. |
| 5 | `contracts.md` §10–§11 | Database / Xử lý đồng thời | Các quy tắc phiên bản tóm tắt rất cụ thể, nhưng migration PostgreSQL và kiểm thử worker đồng thời chưa được xây dựng. | Triển khai các chỉ mục duy nhất một phần, khóa phả hệ / CAS, quản lý khóa thuê và bài kiểm thử outbox cùng giao dịch trước khi kích hoạt tính năng chạy lại. |

### Thông tin và đề xuất bổ sung (Info / Suggestions)

| # | Khía cạnh | Đề xuất |
|---|---|---|
| 1 | Lộ trình phát hành sản phẩm | Phát hành luồng chat P1 phía sau cờ tính năng phân quyền theo tenant/vai trò trước khi kích hoạt báo cáo tóm tắt ngày P2. |
| 2 | Chất lượng mô hình LLM | Giám sát tỷ lệ câu trả lời mâu thuẫn số liệu, tỷ lệ từ chối, tỷ lệ gọi công cụ thành công và chi phí nhà cung cấp theo từng phiên bản chính sách/mô hình. |
| 3 | Trải nghiệm người dùng (UX) | Hiển thị phạm vi phân quyền, khoảng thời gian, độ tươi mới và nguồn gốc số liệu thành các thành phần giao diện trực quan thay vì chỉ lồng vào câu chữ văn xuôi. |

### Tóm tắt danh mục kiểm tra (Checklist Summary)

| Hạng mục | Kết luận | Kết quả kiểm tra |
|---|---|---|
| An ninh | PASS WITH GATES | Cơ chế phòng thủ chiều sâu tenant/chi nhánh, công cụ chỉ đọc, cấm lưu PII, chính sách đóng ngắt an toàn khi gọi nhà cung cấp và tham chiếu kiểm toán đã được đặc tả chi tiết; giữ lại cổng kiểm thử âm tính tự động. |
| Logic nghiệp vụ | PASS WITH GATES | Quyền sở hữu số liệu, công thức AOV, mốc so sánh, ngôn từ đóng góp, trạng thái `DATA_LATE` và cơ chế thay thế phiên bản bất biến đã được quy định; giữ lại cổng sinh fixture của Reporting. |
| Hiệu năng | PASS | Đạt chuẩn gọi công cụ p95 ≤ 2s, chat có câu trả lời p95 ≤ 8s, timeout ≤ 10s, từ chối quyền p95 ≤ 500ms, vòng lặp công cụ có giới hạn và phân trang dữ liệu. |
| Thiết kế trong sáng | PASS | Phân định rõ ràng quyền sở hữu dịch vụ, ranh giới dữ liệu, máy trạng thái, cơ chế outbox/inbox và tuyệt đối không dùng chung cơ sở dữ liệu. |
| Tuân thủ quy ước | PASS WITH GATES | Tuân thủ đầy đủ chuẩn REST/OpenAPI 3.1, UUID v7, giờ UTC, phong bì chung, AsyncAPI và quy ước PostgreSQL/Flyway của nền tảng; cần áp dụng các sửa đổi bổ sung của dịch vụ cha. |

## 4. Tóm tắt kiến trúc (Architecture Summary)

```text
Bảng điều khiển Owner/Manager
          |
          v
AI Assistant API
  ├─ Identity Resolver + Scope/Policy Engine
  ├─ Turn Orchestrator + Answer Validator
  ├─ Tool Gateway ──> Reporting Tool API ──> Mô hình đọc Reporting
  ├─ DeepSeek Adapter
  ├─ AI PostgreSQL / Outbox / Tham chiếu kiểm toán
  └─ Summary Scheduler/Worker ──> Độ tươi mới / Tính đầy đủ của Reporting
```

Các ranh giới thẩm quyền:

- Identity sở hữu người dùng, vai trò, tư cách thành viên và quyết định phân quyền hiện hành trực tuyến.
- Reporting sở hữu các sự kiện doanh thu, chỉ số tổng hợp, độ tươi mới/tính đầy đủ và tính toán đóng góp.
- AI Assistant sở hữu tầng điều phối, ngữ cảnh ngữ nghĩa an toàn, chính sách nhà cung cấp, hiển thị câu trả lời/tóm tắt đã qua kiểm định và phả hệ phiên bản.
- `OrderPaid.v1` luôn là dữ kiện đơn hàng thanh toán chuẩn duy nhất; AI Assistant tuyệt đối không tự tính toán doanh thu từ dữ liệu thô của Order/Payment.

Các quy tắc vận hành lúc runtime quan trọng:

- Mọi phiên hội thoại / lượt hỏi được bảo vệ đều phải giải quyết phân quyền trực tuyến.
- Mô hình LLM chỉ nhận câu hỏi đã che giấu PII, ngữ cảnh cấu trúc, schema công cụ và các dữ kiện tổng hợp.
- Tối đa 3 lần gọi công cụ trong một lượt hỏi, 1 lần sửa lỗi, 1 lần thử lại gọi nhà cung cấp và giới hạn thời gian chờ nghiêm ngặt.
- Lượt hỏi đồng bộ phản hồi trong thời hạn mục tiêu hoặc chuyển sang mã `202 + polling`.
- Báo cáo tóm tắt ngày kiên nhẫn chờ tính đầy đủ của Reporting, sử dụng mốc so sánh cùng thứ tuần trước kèm dự phòng 7 ngày, và tạo một phiên bản hiệu chỉnh bất biến mới khi mốc kiểm chứng nguồn tăng lên.

## 5. Bảng truy vết yêu cầu (Requirements Traceability)

| Yêu cầu nghiệp vụ | Bằng chứng thiết kế | Cổng nghiệm thu |
|---|---|---|
| P1 Hỏi đáp báo cáo bằng ngôn ngữ tự nhiên | Yêu cầu FR-AI-001..008; API hội thoại/lượt hỏi công khai trong Contracts; DeepSeek adapter; 7 công cụ Reporting | Owner/Manager nhận được câu trả lời đã qua kiểm định hoặc trạng thái cấu trúc an toàn kèm phạm vi, thời gian, độ tươi mới và nguồn gốc |
| P1 Rào chắn bảo vệ phạm vi chi nhánh | API quyết định phân quyền Identity; bản hiệu chỉnh phạm vi bất biến; điều kiện lọc tại Tool Gateway và repository Reporting | Manager không thể xem dữ liệu chi nhánh ngoài quyền thông qua IDOR, tấn công prompt injection hoặc giả mạo đối số chi nhánh |
| P1 Che giấu dữ liệu PII | Yêu cầu FR-AI-009; schema câu trả lời / tóm tắt an toàn; không lưu prompt thô / phản hồi thô của nhà cung cấp | Không có số điện thoại, tên khách hàng, ID giao dịch thô, khóa bí mật hay câu lệnh SQL trong payload gửi mô hình, logs, events hay DB |
| P1 Ranh giới chỉ đọc | Danh mục công cụ cho phép chỉ chứa thao tác đọc tổng hợp; không có công cụ làm thay đổi dữ liệu | Các yêu cầu thay đổi trạng thái nghiệp vụ bị từ chối thẳng thừng mà không sinh ra tác dụng phụ trong domain |
| P2 Báo cáo tóm tắt ngày | Bộ lập lịch/worker tóm tắt, giờ chốt ca + 15 phút, mốc kiểm chứng tính đầy đủ, công cụ mốc so sánh và phân tích đóng góp | Bản tóm tắt chỉ được công bố khi có đủ dữ liệu theo quy định và mang đầy đủ nguồn gốc xuất xứ về nguồn, mô hình và chính sách |
| P2 Xử lý dữ kiện muộn | Trạng thái `DATA_LATE`, phiên bản mốc kiểm chứng nguồn, phiên bản hiệu chỉnh bất biến và sự kiện superseded | Sự kiện OrderPaid / hoàn tiền / điều chỉnh bổ sung muộn sẽ tạo ra một phiên bản mới có thể truy vết mà không ghi đè lịch sử |

## 6. Lộ trình triển khai (Implementation Roadmap)

### Giai đoạn 0 — Cụ thể hóa giao ước và sửa đổi bổ sung dịch vụ cha

Các kết quả bàn giao:

- Áp dụng phần sửa đổi bổ sung phân quyền đa chi nhánh của Identity.
- Mở rộng giao ước Reporting với các mô hình đọc AOV, theo giờ, so sánh chi nhánh, phân tích đóng góp và từ điển số liệu.
- Sinh tài liệu OpenAPI 3.1 công khai/nội bộ, registry công cụ / JSON Schema và schema sự kiện AsyncAPI.
- Sinh schema câu trả lời / tóm tắt an toàn, schema chính sách nhà cung cấp và các bộ dữ liệu mẫu che giấu PII.
- Tạo các file migration PostgreSQL / Flyway kèm theo comment cột, ràng buộc tenant, chỉ mục phiên bản, khóa thuê và bảng outbox/inbox.

Tiêu chí hoàn thành: Vượt qua các bài kiểm tra tương thích schema; không còn bất kỳ sự mơ hồ nào về quyền sở hữu trường dữ liệu hoặc sai lệch phiên bản.

### Giai đoạn 1 — Nền tảng AI và phân quyền

Các kết quả bàn giao:

- Khung vỏ API của AI, tích hợp Cognito/workload identity và client gọi quyết định phân quyền Identity trực tuyến.
- Bộ thẩm định phạm vi và chính sách với phạm vi toàn tenant của Owner, tập con chi nhánh của Owner và chi nhánh được giao của Manager.
- Lưu trữ phiên hội thoại, bản hiệu chỉnh phạm vi, lượt hỏi, cơ chế lũy quyền, thời hạn TTL và bản ghi kiểm toán đã che giấu.
- Bộ tạo ngữ cảnh ngữ nghĩa có cấu trúc, tuyệt đối không lưu trữ transcript thô.

Tiêu chí hoàn thành: Vượt qua các bài kiểm thử âm tính chéo tenant / chéo chi nhánh; tư cách thành viên bị thu hồi sẽ lập tức chặn đứng request được bảo vệ tiếp theo.

### Giai đoạn 2 — Luồng chat báo cáo P1

Các kết quả bàn giao:

- Danh mục công cụ và khâu thẩm định hợp lệ tại Tool Gateway.
- Adapter kết nối Reporting cho toàn bộ 7 công cụ được phép.
- DeepSeek adapter, quản lý chính sách / đặt trước ngân sách nhà cung cấp, bộ ngắt mạch và bộ xác thực phản hồi.
- Phản hồi đồng bộ kèm cơ chế dự phòng chuyển sang polling qua mã `202`.
- Hiển thị nguồn gốc, độ tươi mới và thông báo lỗi an toàn trên Dashboard.

Tiêu chí hoàn thành: Các bộ dữ liệu kiểm thử số liệu chuẩn khớp hoàn toàn với Reporting; vượt qua tập mẫu tấn công prompt / che giấu PII; đáp ứng cam kết p95 và ngân sách chi phí dưới tải thử nghiệm.

### Giai đoạn 3 — Báo cáo tóm tắt ngày P2

Các kết quả bàn giao:

- Bộ lập lịch theo múi giờ chi nhánh và xử lý giờ chốt ngày kinh doanh.
- Trạng thái chờ tính đầy đủ / `DATA_LATE`, phương án dự phòng mốc so sánh và chính sách ngôn từ phân tích đóng góp.
- Worker xử lý tóm tắt, phả hệ phiên bản bất biến, sự kiện superseded và tính lũy quyền khi chạy lại.
- Các API danh sách / chi tiết / chạy lại tóm tắt và hiển thị trên Dashboard.

Tiêu chí hoàn thành: Các worker chạy đồng thời không thể công bố các phiên bản cạnh tranh trùng lặp; việc phát lại khi có số liệu muộn diễn ra tất định và có thể kiểm toán.

### Giai đoạn 4 — An ninh, đánh giá chất lượng và phát hành có kiểm soát

Các kết quả bàn giao:

- Đánh giá an ninh toàn diện chống lỗ hổng IDOR, tấn công prompt injection, rò rỉ dữ liệu PII ra ngoài, quản lý khóa bí mật và chính sách khu vực nhà cung cấp.
- Bộ dữ liệu đánh giá chất lượng mô hình: phát hiện sai lệch số liệu, luận điểm không căn cứ, từ chối an toàn, trường hợp không có dữ liệu, dữ liệu cũ và chuẩn hóa ngôn từ tiếng Việt.
- Bảng điều khiển / cảnh báo khả năng quan sát cho độ trễ, độ tươi mới, tỷ lệ từ chối, lỗi nhà cung cấp, chi phí và độ trễ phiên bản.
- Kế hoạch triển khai theo cờ tính năng: thử nghiệm nội bộ với Owner → một số tenant chọn lọc → mở rộng cho toàn bộ Quản lý.

Tiêu chí hoàn thành: Tài liệu hướng dẫn vận hành (runbook) được phê duyệt, ngưỡng cảnh báo được kiểm chứng, công tắc ngắt khẩn cấp / vô hiệu hóa nhà cung cấp hoạt động chính xác.

## 7. Các cổng triển khai và đơn vị sở hữu (Implementation Gates & Ownership)

| Cổng triển khai | Đơn vị sở hữu | Bắt buộc hoàn thành trước khi |
|---|---|---|
| Sửa đổi bổ sung phân quyền Identity và fixtures | Identity Access | Bất kỳ endpoint AI được bảo vệ nào |
| Schema công cụ Reporting, từ điển số liệu và fixtures | Reporting | Bất kỳ việc triển khai công cụ hoặc đánh giá câu trả lời nào |
| Sinh OpenAPI / AsyncAPI / JSON Schema | AI Platform / Chủ sở hữu API | Viết code controller, adapter hoặc consumer sự kiện |
| Bộ xác thực đầu ra an toàn và tập mẫu kiểm thử PII | AI / An ninh (Security) | Cho phép lưu lượng DeepSeek hoặc lưu trữ DB |
| Chính sách nhà cung cấp, bí mật / mạng ra ngoài và runbook ngân sách | Nền tảng / An ninh / Tài chính | Kích hoạt nhà cung cấp LLM |
| Migrations PostgreSQL quản lý phiên bản / khóa thuê / outbox | Chủ sở hữu dịch vụ AI | Kích hoạt worker tóm tắt hoặc tính năng chạy lại |
| Schema thành phần tiêu thụ kiểm toán và khả năng quan sát | Audit / Observability | Triển khai môi trường Production |

## 8. Cam kết vận hành (SLO) và chuẩn đo lường khả năng quan sát

- Gọi công cụ Reporting đạt p95 ≤ 2 giây cho các truy vấn tổng hợp tiêu chuẩn.
- Lượt chat có câu trả lời đạt p95 ≤ 8 giây khi nhà cung cấp ổn định; thời gian chờ tối đa cứng ≤ 10 giây.
- Xử lý phạm vi / từ chối đạt p95 ≤ 500 mili-giây không cần gọi mô hình LLM.
- Báo cáo tóm tắt ngày được công bố hoặc chuyển trạng thái kết thúc trong vòng 10 phút sau khi dữ liệu sẵn sàng; thời gian chờ tính đầy đủ tối đa 30 phút.
- Chỉ số đo lường (Metrics): độ trễ/lỗi công cụ, chuyển đổi trạng thái lượt hỏi, lỗi xác thực câu trả lời, lý do từ chối, từ chối phạm vi, tỷ lệ độ tươi mới/dữ liệu muộn, mức tiêu thụ token/chi phí nhà cung cấp, cạn kiệt ngân sách, độ trễ phiên bản tóm tắt và độ sâu hàng đợi DLQ.
- Nhật ký và dấu vết (Logs/Traces): chỉ chứa Correlation ID và tóm tắt đã che giấu thông tin nhạy cảm; nghiêm cấm ghi lại prompt thô, payload nhà cung cấp, chuỗi JWT, số điện thoại hoặc khóa bí mật.

## 9. Quyết định cuối cùng (Final Decision)

**APPROVED WITH WARNINGS (ĐÃ PHÊ DUYỆT KÈM CẢNH BÁO).**

Thiết kế đã sẵn sàng cho việc lập kế hoạch triển khai và thực thi có kiểm soát. Các cảnh báo là các cổng kiểm soát tường minh trước khi viết mã nguồn và bắt buộc phải được đóng lại thông qua các giao ước sinh tự động, sửa đổi bổ sung của dịch vụ cha, các bộ fixture an ninh/đánh giá và kiểm thử migration cơ sở dữ liệu. Tuyệt đối không kích hoạt lưu lượng gọi nhà cung cấp thật trên production hoặc phát hành tóm tắt ngày trước khi vượt qua các cổng kiểm soát này.

**Hành động tiếp theo:** Khởi động Giai đoạn 0 cụ thể hóa giao ước và tạo danh mục backlog triển khai từ lộ trình này.

## 10. Tham chiếu kỹ năng và quy trình làm việc

- `.agent/workflows/design.md`
- `.agent/skills/brainstorming/SKILL.md`
- `.agent/skills/spec-reviewer/SKILL.md`
- `.agent/skills/api-design/SKILL.md`
- `.agent/skills/database-design/SKILL.md`
- `.agent/skills/log-processing/SKILL.md`
- `.agent/skills/sequence-diagram/SKILL.md`
- `.agent/skills/code-reviewer/SKILL.md`
