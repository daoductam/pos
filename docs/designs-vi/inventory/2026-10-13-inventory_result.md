# Module 6 — Dịch Vụ Kho Nguyên Liệu (Inventory Service): Kết Quả Thiết Kế (Design Result)

**Ngày:** 2026-10-13  
**Trạng thái:** Thiết kế hoàn tất — Phê duyệt có cảnh báo (Approved with warnings)  
**Phạm vi:** M — Luồng Service Tiêu chuẩn (Standard Service Flow)  
**Module:** Kho nguyên liệu (Inventory Service)

---

## 1. Kết Luận Chung (Verdict)

**PHÊ DUYỆT CÓ CẢNH BÁO (APPROVED WITH WARNINGS)**

Bộ tài liệu thiết kế đã cung cấp đầy đủ cơ sở kỹ thuật và nghiệp vụ vững chắc để xây dựng backlog triển khai thực tế. Không có bất kỳ rào cản nghiêm trọng nào ở mức Critical/RED. Trước khi bắt đầu viết mã nguồn ứng dụng, cần cụ thể hóa các fixture OpenAPI 3.1, AsyncAPI/JSON Schema, các file migration Flyway và hoàn thiện bề mặt lệnh P1 cho các thao tác hủy phiếu nhập (receipt cancel), báo hao hụt (waste) và trả hàng (return).

---

## 2. Các Artifact Đã Hoàn Tất

| Giai đoạn | Tài liệu Artifact | Đường dẫn | Trạng thái |
|---|---|---|---|
| **Vision** | Tầm nhìn dịch vụ | [`2026-10-13-vision.md`](./2026-10-13-vision.md) | Đã phê duyệt (Approved) |
| **Requirements** | Yêu cầu kỹ thuật & nghiệp vụ | [`2026-10-13-requirements.md`](./2026-10-13-requirements.md) | Đã phê duyệt (Approved) |
| **Requirements Review** | Đánh giá yêu cầu (Spec Sentinel Report) | [`2026-10-13-requirements-review.md`](./2026-10-13-requirements-review.md) | Có cảnh báo (Warnings) |
| **Architecture** | Kiến trúc hệ thống | [`2026-10-13-architecture.md`](./2026-10-13-architecture.md) | Đã phê duyệt (Approved) |
| **Sequence Diagrams** | Sơ đồ tuần tự L1/L2 PlantUML | [`2026-10-13-sequence-diagrams.adoc`](./2026-10-13-sequence-diagrams.adoc) | Đã xác nhận (Confirmed) |
| **Contracts** | Hợp đồng API & Dữ liệu | [`2026-10-13-contracts.md`](./2026-10-13-contracts.md) | Đã phê duyệt có cảnh báo |
| **Contracts Review** | Đánh giá hợp đồng kỹ thuật | [`2026-10-13-contracts-review.md`](./2026-10-13-contracts-review.md) | Có cảnh báo (Warnings) |
| **Finalization** | Kết quả thiết kế tổng hợp | Tài liệu này | Đã phê duyệt có cảnh báo |

---

## 3. Ma Trận Truy Vết Nghiệp Vụ (Traceability Review)

| Yêu cầu nghiệp vụ | Thành phần Kiến trúc phụ trách | Mức độ bao phủ trong Hợp đồng (Contracts) |
|---|---|---|
| **P0 Danh mục nguyên liệu master / Đơn vị cơ sở** | Ingredient Service + Bảng master PostgreSQL cấp tenant | Các API quản lý nguyên liệu, schema quy đổi mua hàng, ràng buộc đơn vị và số lượng `NUMERIC(18,3)` |
| **P0 Phiên bản công thức món, size, topping** | Versioned Recipe + Recipe Expansion Engine | Các API công thức, snapshot công thức bất biến, liên kết modifier scope tùy chọn |
| **P0 Bán hàng tự động trừ nguyên liệu** | Stock Transaction Service + Sổ cái chỉ ghi thêm | Các API Reserve/Commit, dòng sổ cái `SALE_COMMITTED`, view tổng hợp số dư |
| **P0 Giữ chỗ / Trừ kho trong Saga Checkout** | Reservation State Machine + Biên giới Step Functions | Hợp đồng các lệnh Reserve/Commit/Release/Expiry, trạng thái đối soát `RECONCILIATION_REQUIRED` |
| **P1 Nhập kho nguyên liệu** | Receipt Service + Sổ cái biến động kho | API/schema phiếu nhập kho và mô hình biến động `RECEIPT` |
| **P1 Kiểm kê kho** | Count Session Service + Transaction phê duyệt | Công thức tính delta dựa trên snapshot và biến động sau snapshot, sổ cái `COUNT_ADJUSTMENT`, khóa phiên bản duyệt |
| **P1 Cảnh báo tồn kho thấp** | Alert Service + Transactional Outbox | Vòng đời theo dõi cắt ngưỡng tồn khả dụng và sự kiện `InventoryLowStockDetected.v1` |
| **P2 Dự báo nhu cầu nhập hàng** | Forecast Worker + Read model có thể tái tạo | Các bậc chất lượng dữ liệu (`HIGH`, `MEDIUM`, `LOW`, `INSUFFICIENT_HISTORY`), API cấu hình policy và kết quả dự báo |

---

## 4. Tóm Tắt Đánh Giá Thiết Kế (Design Review Summary)

| Tiêu chí | Đánh giá | Nhận xét chi tiết |
|---|---|---|
| **Tính Đầy Đủ (Completeness)** | ĐẠT (PASS) | Toàn bộ phạm vi P0, P1, P2 đều có yêu cầu nghiệp vụ, thành phần kiến trúc, mô hình dữ liệu và hợp đồng kỹ thuật tương ứng. |
| **Tính Nhất Quán (Consistency)** | ĐẠT CÓ CẢNH BÁO | Đã đồng bộ hoàn toàn với nền tảng POS Platform cha về API kiểm tra availability dạng POST body, các trạng thái reservation, header idempotency, các trường sổ cái và payload sự kiện. Bề mặt lệnh P1 cần được cụ thể hóa thêm. |
| **Tính Khả Thi (Feasibility)** | ĐẠT (PASS) | Mô hình transaction trong PostgreSQL, khóa dòng bi quan, Transactional Outbox và Step Functions hoàn toàn phù hợp với quy mô và yêu cầu độ trễ của chuỗi F&B. |
| **An Toàn Bảo Mật (Security)** | ĐẠT (PASS) | Cơ chế xác thực qua Cognito và Workload Identity, phòng thủ chuyên sâu đa tenant/chi nhánh, kiểm tra quyền sở hữu tài nguyên và nguyên tắc fail-closed đều được thiết lập tường minh. |
| **Hiệu Năng (Performance)** | ĐẠT (PASS) | Luồng P0 hướng tới mục tiêu p95 ≤ 500 ms; không dùng cache trong đường dẫn đảm bảo tính đúng đắn; có thời gian chờ khóa ngắn, retry có giới hạn và hệ thống index hoàn chỉnh. |
| **Khả Năng Quan Sát (Observability)** | ĐẠT (PASS) | Tích hợp đầy đủ correlation/causation ID, metrics đo lường tranh chấp khóa/thiếu hàng/hết hạn, hàng đợi DLQ, quy trình đối soát và kiểm tra checksum tái tạo số dư. |
| **Độ Sẵn Sàng Kiểm Thử (Testing Readiness)** | ĐẠT CÓ CẢNH BÁO | Ma trận kiểm thử đã bao quát đầy đủ các tình huống lỗi và xử lý đồng thời; cần cụ thể hóa thành các fixture OpenAPI/AsyncAPI và script kiểm thử thực thi được. |

---

## 5. Đánh Giá Chất Lượng Thiết Kế (Code Reviewer Gate)

**Phạm vi đánh giá:** 7 tài liệu thiết kế của Inventory Service và 5 tài liệu hợp đồng cha liên quan trực tiếp; chưa có mã nguồn ứng dụng.  
**Kết luận:** **VÀNG — Tiến hành cẩn trọng (YELLOW — Proceed with Caution)**

### Tóm tắt nhận xét:
Thiết kế không có bất kỳ khiếm khuyết nghiêm trọng nào về phân quyền, cô lập đa tenant, điều phối Saga, chống bán âm (overselling) hay quản lý trạng thái kết thúc. Cảnh báo chủ yếu tập trung vào việc chuyển đổi văn bản hợp đồng thành schema/migration/fixture thực thi được và mở rộng bề mặt lệnh P1.

### Bảng cảnh báo chi tiết:

| # | Vị trí tham chiếu | Phân loại | Nội dung vấn đề | Khuyến nghị giải quyết |
|---|---|---|---|---|
| 1 | `2026-10-13-contracts.md` (Mục 7.3) | API | Phân trang con trỏ `ReservationCursorPage` đã có route và quy tắc trường nhưng chưa có component schema OpenAPI và fixture thực thi. | Sinh schema và kiểm thử ràng buộc con trỏ/bộ lọc trước khi code. |
| 2 | `2026-10-13-contracts.md` (Mục 4.4, Mục 10.3) | Logic nghiệp vụ | Trạng thái hủy phiếu nhập và các biến động `WASTE`/`RETURN` đã có trong database nhưng chưa có endpoint/request contract riêng cho lệnh này. | Chốt hợp đồng lệnh P1; mọi thao tác điều chỉnh phải ghi thêm dòng sổ cái, bắt buộc có lý do/chứng từ/idempotency. |
| 3 | `2026-10-13-contracts.md` (Mục 10.4, Mục 10.6) | Dữ liệu / Migration | Một số bảng như cảnh báo, dự báo, outbox đang được mô tả dạng cột tóm tắt thu gọn. | Sinh đầy đủ file DDL có kiểu dữ liệu rõ ràng, `COMMENT ON COLUMN`, check constraints, các trường lease và assertion kiểm tra tự động. |
| 4 | `2026-10-13-contracts.md` (Mục 14) | Kiểm thử | Bộ fixture kiểm thử tranh chấp đồng thời, replay sự kiện và làm tròn số thập phân mới dừng ở mức ma trận yêu cầu. | Đưa fixture vào CI và chạy nghiệm thu trước khi đưa vào môi trường tải thực tế. |

---

## 6. Tóm Tắt Kiến Trúc Hệ Thống

| Chiều kiến trúc | Tóm tắt thông số cốt lõi |
|---|---|
| **Các nhóm thành phần logic** | 10 nhóm: Public API, Internal Saga API, Recipe Expansion Engine, Stock Transaction Service, Reservation State Machine, Receipt/Count Service, Alert/Forecast Service, Background Workers, Outbox Publisher, và Repositories/Observability. |
| **Cơ sở dữ liệu** | 1 database PostgreSQL/Aurora PostgreSQL thuộc quyền sở hữu riêng của Inventory Service; tuyệt đối không dùng chung database hay foreign key xuyên service. |
| **Bề mặt thao tác API** | 32 endpoint thao tác công khai và nội bộ được định nghĩa trong hợp đồng. |
| **Sự kiện bất đồng bộ** | 7 loại sự kiện domain có phiên bản, tuân thủ phong bì chuẩn, gửi qua outbox at-least-once và khử trùng lặp phía consumer. |
| **Các luồng cốt lõi (P0)** | Giữ chỗ (`Reserve`), rollback khi thiếu hàng, xác nhận trừ kho khi thanh toán thành công (`Commit`), hủy giải phóng khi thanh toán lỗi (`Release`), đối soát ngoại lệ (`Reconciliation`), duyệt kiểm kê và tái tạo bảng số dư. |
| **Mô hình tính đúng đắn** | Khóa dòng reservation trước cho các lệnh terminal; khóa các dòng số dư theo thứ tự `ingredientId` tăng dần; sổ cái kho chỉ ghi thêm; công thức bất biến: `available = onHand - reserved`. |

---

## 7. Lộ Trình Triển Khai Thực Tế (Implementation Roadmap)

### Giai đoạn 0 — Cụ Thể Hóa Hợp Đồng & Khung Nền Tảng (Phase 0)
- **Mục tiêu:** Chuyển đổi các định nghĩa thiết kế thành các artifact có thể kiểm tra tự động.
- **Nhiệm vụ:** Viết file đặc tả OpenAPI 3.1, AsyncAPI/JSON Schema, các script Flyway migration từ V1 đến V7; cụ thể hóa toàn bộ kiểu dữ liệu/comment/check constraint/chỉ mục; cấu hình database/outbox/EventBridge-SQS/DLQ; xây dựng test harness kiểm tra cô lập đa tenant.
- **Ước tính thời gian:** 3–5 ngày làm việc.
- **Điều kiện phụ thuộc:** Hoàn tất review hợp đồng với hệ thống cha POS Platform, Order Service và Step Functions Saga.

### Giai đoạn 1 — Danh Mục Nguyên Liệu, Công Thức & Số Dư Đầu Kỳ P0 (Phase 1)
- **Mục tiêu:** Quản lý master nguyên liệu, định lượng món ăn và thiết lập tồn kho ban đầu.
- **Nhiệm vụ:** Phát triển các API nguyên liệu và bảng quy đổi mua hàng; luồng soạn thảo/thẩm định/kích hoạt/lưu trữ công thức; kiểm tra tham chiếu Catalog; API thiết lập số dư đầu kỳ; kiểm tra tính duy nhất của công thức `ACTIVE`.
- **Ước tính thời gian:** 1–1.5 tuần làm việc.
- **Điều kiện phụ thuộc:** Hoàn thành Phase 0; chốt hợp đồng mã định danh Catalog item/variant.

### Giai đoạn 2 — Giữ Chỗ & Điều Phối Checkout Saga P0 (Phase 2)
- **Mục tiêu:** Đảm bảo giữ chỗ và trừ kho chính xác tuyệt đối, chống bán âm và chống trừ kho trùng lặp.
- **Nhiệm vụ:** Thuật toán mở rộng công thức và tổng hợp nhu cầu nguyên liệu; transaction giữ chỗ kèm khóa dòng bi quan; xác thực ngữ cảnh thanh toán khi commit; xử lý release và hết hạn reservation; quy trình đối soát ngoại lệ `RECONCILIATION_REQUIRED`; ghi nhận sự kiện outbox; tích hợp với AWS Step Functions Saga.
- **Ước tính thời gian:** 1.5–2 tuần làm việc.
- **Điều kiện phụ thuộc:** Hoàn thành Phase 1; bộ fixture tích hợp từ Order, Payment và Step Functions.

### Giai đoạn 3 — Nghiệp Vụ Quản Lý Kho Nâng Cao P1 (Phase 3)
- **Mục tiêu:** Mở rộng các thao tác vận hành kho thủ công có kiểm toán đầy đủ.
- **Nhiệm vụ:** Phiếu nhập kho và quy đổi đơn vị mua; lệnh hủy phiếu nhập; phiên kiểm kê kho (snapshot, nhập số liệu, tính chênh lệch, phê duyệt); các lệnh báo hao hụt (`WASTE`) và trả hàng (`RETURN`); cấu hình ngưỡng tồn, tiếp nhận và xử lý cảnh báo tồn thấp; phát sự kiện cảnh báo.
- **Ước tính thời gian:** 1.5–2 tuần làm việc.
- **Điều kiện phụ thuộc:** Hoàn thành Phase 2; chốt hợp đồng các lệnh thao tác P1.

### Giai đoạn 4 — Dự Báo Nhu Cầu Nhập Hàng P2 (Phase 4)
- **Mục tiêu:** Cung cấp API dự báo nhu cầu nhập hàng chỉ đọc kèm chỉ số chất lượng và độ tươi dữ liệu.
- **Nhiệm vụ:** Tổng hợp lượng tiêu thụ thực tế từ sổ cái; phân giải chính sách tồn an toàn và lead time; thuật toán phân bậc chất lượng (`LOW`, `MEDIUM`, `HIGH`); hiệu chỉnh theo ngày trong tuần; worker tính toán dự báo ban đêm; phản hồi mã `forecast_insufficient_history`.
- **Ước tính thời gian:** 1–1.5 tuần làm việc.
- **Điều kiện phụ thuộc:** Hoàn thành Phase 3; có dữ liệu lịch sử sổ cái kho và báo cáo tiêu thụ.

### Giai đoạn 5 — Gia Cố Hệ Thống & Sẵn Sàng Vận Hành (Phase 5)
- **Mục tiêu:** Đạt độ tin cậy tương đương môi trường production trước khi triển khai chính thức.
- **Nhiệm vụ:** Kiểm thử tải nặng về tranh chấp khóa/deadlock/timeout; diễn tập replay sự kiện trùng lặp và đến sai thứ tự; kiểm tra phục hồi backup và tái tạo projection từ sổ cái; diễn tập thu hồi lease outbox và xử lý DLQ; rà soát an toàn bảo mật và che mờ dữ liệu nhạy cảm; hoàn thiện runbook vận hành và đo kiểm thời gian phản hồi p95.
- **Ước tính thời gian:** 1 tuần làm việc.
- **Điều kiện phụ thuộc:** Hoàn thành toàn bộ các Phase từ 0 đến 4.

**Tổng thời gian ước lượng:** Khoảng **6–8 tuần** cho toàn bộ các lát cắt nghiệp vụ từ P0 → P1 → P2 (chưa tính thời gian chờ phê duyệt của các bên liên quan hoặc thay đổi phạm vi nghiệp vụ).

---

## 8. Các Cổng Kiểm Soát Triển Khai & Quản Trị Rủi Ro (Implementation Gates)

| Cổng kiểm soát / Rủi ro | Điều kiện bắt buộc trước khi viết code / phát hành |
|---|---|
| **Hợp đồng sinh tự động (Generated contracts)** | Toàn bộ các ví dụ trong OpenAPI và AsyncAPI đều hợp lệ; bộ fixture kiểm thử phía consumer cho Saga, Order, Reporting, Audit, Notification đều pass 100%. |
| **Tính đúng đắn của Database** | Script migration Flyway chạy thành công trên cả cơ sở dữ liệu trống và cơ sở dữ liệu có sẵn dữ liệu mẫu; toàn bộ cột, comment, check constraint và index đều có assertion kiểm tra tự động. |
| **An toàn trong giữ chỗ kho** | Bộ test tranh chấp đồng thời chứng minh: không bao giờ giữ chỗ dở dang một phần, số dư khả dụng không bao giờ âm, bên chiến thắng trạng thái terminal luôn tất định và lệnh retry đảm bảo tính idempotent. |
| **An toàn thao tác kho P1** | Các thao tác hủy phiếu nhập, hao hụt, trả hàng đều có hợp đồng lệnh riêng; tuyệt đối không cập nhật (`UPDATE`) hay xóa (`DELETE`) các dòng lịch sử sổ cái. |
| **Khả năng đối soát ngoại lệ** | Các trường hợp thanh toán thành công nhưng trừ kho lỗi, hết hạn trước khi commit, hoặc replay từ DLQ đều có hồ sơ đối soát và quy trình xử lý thủ công rõ ràng; tuyệt đối không tự ý mở lại reservation đã kết thúc. |
| **Khả năng quan sát (Observability)** | Hoàn thiện hệ thống metrics, log có cấu trúc, tự động che mờ dữ liệu nhạy cảm, cấu hình cảnh báo và kịch bản runbook cho các tình huống tranh chấp khóa, thiếu hàng, quá hạn, tắc nghẽn outbox, lỗi DLQ và lệch checksum. |

---

## 9. Các Bước Tiếp Theo (Next Steps)

- [x] Hoàn tất toàn bộ các tài liệu Vision, Requirements, Architecture, Contracts và Sequence Diagrams cho Inventory Service.
- [x] Đồng bộ hóa hợp đồng Inventory giữa tài liệu nội bộ và tài liệu nền tảng POS Platform cha.
- [x] Thực hiện đánh giá kỹ thuật toàn diện qua cổng Code Reviewer; không còn lỗi Critical/RED.
- [ ] Chuyển đổi lộ trình triển khai (Roadmap Phase 0–5) thành các task chi tiết trong backlog dự án.
- [ ] Cụ thể hóa các file đặc tả OpenAPI 3.1, AsyncAPI, và script Flyway migration trước khi viết code controller/domain.
- [ ] Chốt hợp đồng schema cho các lệnh thao tác P1 (hủy phiếu nhập, báo hao hụt, trả hàng).
- [ ] Chỉ bắt đầu viết code Phase 1 sau khi các cổng kiểm soát triển khai ở Mục 8 được xác nhận nghiệm thu.
