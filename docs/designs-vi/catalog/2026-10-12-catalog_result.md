Started at: 2026/10/12 15:54:00 +07
Finished at: 2026/10/12 15:54:00 +07
Total time: 0 minutes
---

# Kết quả Thiết kế: Catalog Service (Dịch vụ Danh mục Món ăn)

## Kết luận Cuối cùng (Final Verdict)

**PHÊ DUYỆT CÓ CẢNH BÁO (APPROVED WITH WARNINGS) — sẵn sàng cho triển khai sau khi hoàn thành các cổng kiểm soát trước khi viết code (pre-coding gates) dưới đây.**

Năm giai đoạn thiết kế đã hoàn thành và hoàn toàn nhất quán nội bộ. Không tìm thấy lỗ hổng nghiêm trọng nào về an toàn bảo mật, quyền sở hữu dữ liệu hay logic nghiệp vụ trong thiết kế của module. Các cảnh báo còn lại là các nghĩa vụ tích hợp với nền tảng cha và được theo dõi tường minh; chúng cần được hoàn thành trước khi lát cắt dọc (vertical slice) giống môi trường sản xuất đầu tiên được viết code.

## Phạm vi Thiết kế

| Hạng mục | Kết quả |
|---|---|
| Module | Module 2 — Menu / Catalog Service (Dịch vụ Thực đơn / Danh mục) |
| Quy trình (Workflow) | Quy trình Service Chuẩn, phạm vi M (Standard Service Flow, scope M) |
| Phạm vi P0 | Danh mục, món ăn, biến thể, nhóm/tùy chọn modifier, tính sẵn sàng thực đơn toàn tenant, đọc POS, bộ phân giải Order, các sự kiện |
| Phạm vi P1 | Combo giá cố định, ghi đè giá biến thể hiện tại theo chi nhánh, mô tả và tag chuẩn hóa phục vụ khởi động nguội AI |
| Ngoài phạm vi | Vòng đời đơn hàng, thanh toán, công thức/tồn kho Inventory, quy trình KDS, định giá động, lịch sử giá đầy đủ, đa ngôn ngữ / hình ảnh / xếp hạng tìm kiếm |
| Lưu trữ dữ liệu | Cơ sở dữ liệu quan hệ PostgreSQL chuẩn hóa do service tự sở hữu |
| Stack mục tiêu | Java 21, Spring Boot 3.x, REST/OpenAPI 3.1, AsyncAPI/JSON Schema, UUID v7, tiền tệ số nguyên VND |
| Trạng thái cuối cùng | Phê duyệt có cảnh báo (Approved with warnings) |

## Các Tài liệu Thiết kế (Artifacts)

1. [Tầm nhìn (Vision)](./2026-10-12-vision.md) — Đã phê duyệt.
2. [Đánh giá Tầm nhìn (Vision review)](./2026-10-12-vision-review.md) — Các cảnh báo đã được chấp nhận và giải quyết thành các yêu cầu.
3. [Yêu cầu Kỹ thuật (Requirements)](./2026-10-12-requirements.md) — Đã phê duyệt.
4. [Kiến trúc Kỹ thuật (Architecture)](./2026-10-12-architecture.md) — Đã phê duyệt.
5. [Hợp đồng Dữ liệu & API (Contracts)](./2026-10-12-contracts.md) — Đã phê duyệt.

## Tóm tắt Kiến trúc Cuối cùng

```text
Angular POS/Dashboard
        |
API Gateway + Xác thực Cognito JWT
        |
Catalog Service ── Quyết định phân quyền hiện tại từ Identity
        |
Catalog PostgreSQL
  categories/items/variants
  modifier groups/options/links
  tenant catalog revision
  idempotency + transactional outbox
        |
EventBridge → SQS/DLQ → Các bên tiêu thụ Recommendation / Reporting / Audit / Cache

Order ── Bộ phân giải nội bộ của Catalog → Các ảnh chụp Order snapshot bất biến
Order/Saga ── Ranh giới tính sẵn sàng và giữ chỗ của Inventory
```

Các quyết định mang tính thẩm quyền:

- Catalog là nguồn chân lý cho cấu trúc thực đơn, giá cơ sở và việc bật/tắt bán trên thực đơn.
- Chủ sở hữu (Owner) và Quản lý (Manager) có thể biến đổi thực đơn dùng chung của tenant. Một Quản lý cần có bất kỳ tư cách thành viên Manager nào đang hoạt động trong tenant; thao tác ghi được ghi audit là có tác động toàn tenant.
- Thu ngân (Cashier) và Bếp/Bar (Kitchen) chỉ có quyền đọc (read-only) và bắt buộc phải có tư cách thành viên chi nhánh đang hoạt động đã được xác thực cho các thao tác đọc.
- Cấu trúc phân cấp danh mục ở P0 là dạng phẳng, mỗi món có một danh mục chính, và giá bán thuộc sở hữu của một biến thể cụ thể.
- Một món đang hoạt động bắt buộc phải duy trì ít nhất một biến thể đang hoạt động và chính xác một biến thể mặc định đang hoạt động.
- Các nhóm modifier có thể tái sử dụng; liên kết giữa món và nhóm mang các cấu hình ghi đè min/max/thứ tự tùy chọn.
- Khả năng bán của Catalog và tình trạng sẵn có tồn kho của Inventory là độc lập. Order đưa ra quyết định hoàn tất đơn hàng cuối cùng.
- Mọi biến đổi đều tăng phiên bản catalog revision của tenant và ghi sự kiện outbox trong cùng một giao dịch.
- Order gọi bộ phân giải nội bộ để nhận lựa chọn hiện tại nhất quán và lưu trữ các ảnh chụp (snapshots) bất biến về tên/giá/biến thể/modifier.

## Báo cáo Đánh giá Thiết kế (Design Review Report)

**Phạm vi:** Năm tài liệu thiết kế của Catalog và các hợp đồng cha trực tiếp của chúng.

**Chế độ đánh giá:** Đánh giá tính mạch lạc của thiết kế bằng danh mục kiểm tra code-reviewer được điều chỉnh cho tài liệu; không có mã nguồn triển khai nào trong repository.

### Các Vấn đề Nghiêm trọng (Critical Issues)

Không tìm thấy.

### Các Cảnh báo (Warnings)

| # | Tệp:Dòng | Hạng mục | Vấn đề | Khuyến nghị |
|---|---|---|---|---|
| 1 | `docs/designs/catalog/2026-10-12-contracts.md:631` | Quy ước / Tích hợp | Hợp đồng cha vẫn chứa quyền bật/tắt cũ của Cashier, trường category phẳng, quan hệ modifier phẳng và giả định sự kiện có branchId không null. | Áp dụng các sửa đổi hợp đồng cha đã liệt kê trước khi triển khai lát cắt chéo service đầu tiên. |
| 2 | `docs/designs/catalog/2026-10-12-contracts.md:16` | Bàn giao | OpenAPI/AsyncAPI và các artifact client sinh tự động đã được đặc tả nhưng chưa được hiện thực hóa thành file. | Sinh chúng từ hợp đồng đã phê duyệt trước khi viết các controller hoặc DTO Angular. |
| 3 | `docs/designs/catalog/2026-10-12-contracts.md:25` | Bảo mật / Tích hợp | Catalog cố ý không bổ sung `X-Active-Tenant-Id`; luồng ngữ cảnh của gateway và Identity phải thiết lập tin cậy tenant được chọn khi người dùng có nhiều tư cách thành viên. | Bổ sung bài kiểm thử hợp đồng tích hợp cho trường hợp thiếu hoặc mơ hồ ngữ cảnh tenant và từ chối truy cập (fail closed). |
| 4 | `docs/designs/catalog/2026-10-12-architecture.md:172` | Ranh giới | Catalog không kiểm tra tồn kho, trong khi Order phải kết hợp khả năng bán của Catalog với tình trạng tồn kho của Inventory. | Triển khai chuỗi Order–Catalog–Inventory dưới dạng một bài kiểm thử nghiệm thu chéo service trước khi luồng checkout được coi là hoàn tất. |

Các cảnh báo này không yêu cầu phải thiết kế lại Catalog. Chúng là các cổng kiểm soát phát hành phục vụ việc tích hợp và đồng bộ hóa hợp đồng.

### Tóm tắt Danh mục Kiểm tra (Checklist Summary)

| Hạng mục | Kết luận | Ghi chú |
|---|---|---|
| Bảo mật | ĐẠT KÈM CẢNH BÁO | Các mệnh đề tenant, phân quyền trực tuyến qua Identity, xác thực chi nhánh và hành vi fail-closed đã được định nghĩa; ma trận auth dùng chung của hệ thống cha cần được sửa đổi. |
| Logic Nghiệp vụ | ĐẠT | Các quy tắc vòng đời, biến thể mặc định, lựa chọn modifier, xung đột phiên bản và ảnh chụp Order snapshot đều tường minh. |
| Hiệu năng | ĐẠT | Phân trang, bộ lọc danh sách trắng, ETag, vô hiệu hóa dựa trên revision và dữ liệu đầu vào resolver có giới hạn đã được xác định. |
| Thiết kế Sạch sẽ | ĐẠT | Ranh giới service, quyền sở hữu dữ liệu, tách biệt P0/P1 và quyền sở hữu sự kiện đều rất rõ ràng. |
| Quy ước | ĐẠT KÈM CẢNH BÁO | REST/OpenAPI, envelope chuẩn và phiên bản sự kiện đã được đồng bộ; việc cập nhật envelope/schema của hệ thống cha vẫn đang chờ xử lý. |

## Ma trận Truy xuất Nguồn gốc (Traceability Matrix)

| Khu vực yêu cầu | Bao phủ kiến trúc | Bao phủ hợp đồng | Bằng chứng nghiệm thu |
|---|---|---|---|
| Vòng đời danh mục/món | Các bảng chuẩn hóa và giao dịch trạng thái | Endpoint danh mục/món và chuyển đổi trạng thái | Kiểm thử archive/danh mục và vòng đời món |
| Giá/mặc định biến thể | Giá thuộc sở hữu biến thể và các bất biến | Các API tạo/sửa/trạng thái/mặc định biến thể | Kiểm thử giá và biến thể active cuối cùng / mặc định |
| Quy tắc Modifier | Mô hình nhóm/tùy chọn/liên kết tái sử dụng | Các API nhóm, tùy chọn và liên kết; lỗi lựa chọn có kiểu | Kiểm thử min/max, trùng lặp, không active và ID thuộc tenant khác |
| RBAC / Cô lập dữ liệu | Adapter Identity kết hợp mệnh đề tenant tại repository | Quyền `catalog:read/manage/resolve`, quy tắc header chi nhánh | Kiểm thử chéo tenant, thu hồi tư cách thành viên và vai trò |
| Đọc POS | Chính sách bán hàng và catalog revision của tenant | ETag, `304`, phân trang và view sellable | Kiểm thử độ trễ đọc/cache/auth |
| Tích hợp Order | Bộ phân giải repeatable-read và ranh giới ảnh chụp snapshot | `/internal/v1/catalog/resolutions` | Kiểm thử giá/tên/phiên bản nhất quán và biến thể kiểu cũ |
| Ranh giới Inventory | Catalog không sở hữu tồn kho vật lý | Trách nhiệm hạ nguồn tường minh | Kiểm thử hoàn tất đơn giữa Order + Inventory |
| Sự kiện | Outbox và EventBridge/SQS | Envelope có phiên bản và phạm vi chi nhánh toàn tenant có thể null | Kiểm thử schema, retry, DLQ và khử trùng lặp |
| Phần mở rộng P1 | Các bảng/module kiểm soát bằng feature flag | Hợp đồng combo, giá chi nhánh và tag | Kiểm thử cô lập / rơi về giá mặc định P1 |

## Các Cổng Kiểm soát Trước khi Viết Code (Pre-Coding Gates)

Đây là các nhiệm vụ bắt buộc đầu tiên trước khi triển khai tính năng:

1. Sửa đổi các hợp đồng POS cha cho quyền ghi Catalog của Thu ngân (Cashier), định dạng thực thể danh mục, các nhóm modifier, trường `branchId` sự kiện toàn tenant có thể null, và phân giải `variantId` cụ thể của Order.
2. Đăng ký các quyền `catalog:read`, `catalog:manage` và đường dẫn phân quyền workload trong bảng ánh xạ quyền hạn của Identity mà không sao chép trạng thái thành viên của Identity vào Catalog.
3. Tạo ra các file `openapi.yaml`, `asyncapi.yaml` và JSON Schema từ hợp đồng đã phê duyệt; xác thực các ví dụ mẫu và envelope lỗi trong CI.
4. Định nghĩa rõ hành vi ngữ cảnh tenant được chọn tại gateway/Identity và bổ sung bài kiểm thử phủ định cho trường hợp lựa chọn tenant bị mơ hồ hoặc thiếu.
5. Thống nhất với các nhóm Order và Inventory về bảng ánh xạ lỗi của bộ phân giải và ranh giới gọi kiểm tra tồn kho/hoàn tất đơn hàng cuối cùng.

## Lộ trình Triển khai (Implementation Roadmap)

### Giai đoạn 0 — Đồng bộ hợp đồng và dựng khung dự án (Scaffolding)

**Mục tiêu:** Làm cho các hợp đồng dùng chung có thể thực thi được trước khi viết mã domain.

- Áp dụng các sửa đổi hợp đồng cha đã liệt kê ở trên.
- Sinh các artifact OpenAPI/AsyncAPI và các model client Angular có định kiểu.
- Khởi tạo khung dự án Java 21 / Spring Boot 3.x, profile Docker Compose và các quy ước cấu hình.
- Bổ sung correlation ID, ngữ cảnh tenant, bộ ánh xạ envelope/lỗi chuẩn và bộ điều hợp workload mTLS.

**Cổng ra:** Xác thực hợp đồng thành công; service từ chối ngữ cảnh phân quyền bị thiếu/mơ hồ; không controller nào sở hữu cấu trúc request không có kiểu định danh.

### Giai đoạn 1 — Nền tảng lưu trữ và an toàn bảo mật

**Mục tiêu:** Thiết lập trạng thái an toàn do chính service sở hữu.

- Bổ sung migration Flyway cho categories, items, variants, modifier groups/options/links, tenant revision, idempotency và outbox.
- Bổ sung cơ chế sinh UUID v7, timestamp UTC, các ràng buộc tiền tệ số nguyên VND và các ràng buộc sở hữu tenant phức hợp.
- Bổ sung adapter phân quyền Identity và các mệnh đề `tenant_id` tại tầng repository.
- Bổ sung cơ chế phiên bản lạc quan, lưu trữ idempotency, tóm tắt audit và ranh giới giao dịch.

**Cổng ra:** Các bài kiểm thử tích hợp chéo tenant và phiên bản lỗi thời đều vượt qua trước khi các endpoint nghiệp vụ được bật.

### Giai đoạn 2 — Quản trị Catalog P0 và mô hình đọc (Read Model)

**Mục tiêu:** Bàn giao CRUD category/item/variant và luồng đọc thực đơn trên POS.

- Triển khai vòng đời category và item kèm theo thao tác tạo nguyên tử biến thể ban đầu.
- Triển khai việc định giá biến thể / chuyển đổi mặc định và chính sách khả năng bán của món.
- Triển khai phân trang offset phía quản trị và các bộ lọc/sắp xếp trong danh sách trắng.
- Triển khai chế độ xem bán được (sellable view) trên POS, ETag/`304`, catalog revision và hook vô hiệu hóa cache.

**Cổng ra:** Thao tác ghi của Owner/Manager và thao tác đọc của Cashier/Kitchen thỏa mãn ma trận nghiệm thu; không một món active nào đang cấu hình dang dở bị lộ ra ngoài.

### Giai đoạn 3 — Domain Modifier P0

**Mục tiêu:** Bàn giao cấu hình modifier tái sử dụng và xác thực lựa chọn.

- Triển khai vòng đời nhóm/tùy chọn và thao tác gắn/gỡ liên kết món-nhóm.
- Xác thực các quy tắc min/max/thứ tự hiệu lực và tính khả thi của nhóm bắt buộc.
- Bổ sung phụ thu giá modifier và các DTO lựa chọn tương thích với bộ phân giải.
- Xuất bản các sự kiện thay đổi modifier thông qua outbox.

**Cổng ra:** POS và bộ phân giải từ chối các tùy chọn trùng lặp, thuộc tenant khác, không hoạt động và chọn thiếu/thừa với các mã lỗi có định kiểu rõ ràng.

### Giai đoạn 4 — Bộ phân giải Đơn hàng và ranh giới hoàn tất đơn

**Mục tiêu:** Tích hợp hợp đồng Catalog vào lát cắt dọc của Order.

- Triển khai endpoint `/internal/v1/catalog/resolutions` với một giao dịch đọc nhất quán trên database chính.
- Phân giải biến thể null kiểu cũ chỉ khi có đúng một biến thể active; bắt buộc phải gửi biến thể cụ thể cho client mới.
- Trả về tên, giá, các modifier đã chọn, phiên bản tài nguyên và `catalogVersion` bất biến.
- Bổ sung các bài kiểm thử ảnh chụp Order snapshot và lệnh gọi kiểm tra tồn kho/giữ chỗ riêng biệt của Inventory.

**Cổng ra:** Một thay đổi giá thực đơn chỉ ảnh hưởng đến các lần phân giải tiếp theo; các bản snapshot Order hiện có giữ nguyên không đổi; lỗi hết hàng không thể bị che giấu bởi khả năng bán của Catalog.

### Giai đoạn 5 — Sự kiện, vận hành và củng cố hệ thống (Hardening)

**Mục tiêu:** Đảm bảo việc tích hợp hạ nguồn đáng tin cậy và có khả năng quan sát cao.

- Xuất bản các sự kiện category/item/variant/modifier có phiên bản thông qua EventBridge/SQS.
- Bổ sung dữ liệu mẫu kiểm thử khử trùng lặp ở consumer, xử lý retry/DLQ và các chỉ số đo độ tươi mới của bảng chiếu.
- Bổ sung log có cấu trúc và bản ghi audit kèm correlation ID, tenant, actor và resource ID; làm sạch secrets/PII.
- Bổ sung đo đạc p95 đọc/phân giải, chỉ số lỗi phân quyền và cảnh báo độ trễ outbox.

**Cổng ra:** Các bài kiểm thử schema sự kiện, phát lại, khử trùng lặp, DLQ và khả năng quan sát đều vượt qua trong cả môi trường cục bộ và môi trường giống AWS.

### Giai đoạn 6 — Các phần mở rộng P1

**Mục tiêu:** Bổ sung tính năng mới mà không làm thay đổi ngữ nghĩa của P0.

- Triển khai combo giá cố định không lồng nhau và khả năng bán của các thành phần bắt buộc.
- Triển khai ghi đè giá biến thể theo chi nhánh hiện tại với cơ chế cô lập tenant/chi nhánh và tự động rơi về giá mặc định.
- Triển khai mô tả, tag chuẩn hóa và sự kiện metadata cho việc khởi động nguội AI.

**Cổng ra:** Các feature flag của P1 tắt theo mặc định, các client P0 vẫn hoàn toàn tương thích, và thay đổi giá chi nhánh không làm ảnh hưởng đến chi nhánh khác.

## Phân rã Công việc Đề xuất (Work Breakdown)

| Mã | Hạng mục công việc | Phụ thuộc | Mức độ ưu tiên |
|---|---|---|---|
| CAT-01 | Sửa đổi hợp đồng cha và đăng ký phân quyền | Chủ sở hữu Identity + Nền tảng | Cổng P0 (P0 gate) |
| CAT-02 | Các schema OpenAPI/AsyncAPI và client sinh tự động | CAT-01 | Cổng P0 (P0 gate) |
| CAT-03 | Khung dự án service, ngữ cảnh auth, lỗi và correlation | CAT-02 | P0 |
| CAT-04 | Lược đồ Flyway, các ràng buộc, chỉ mục và dữ liệu mẫu | CAT-03 | P0 |
| CAT-05 | Quản trị danh mục / món ăn / biến thể | CAT-04 | P0 |
| CAT-06 | Đọc POS, ETag và catalog revision của tenant | CAT-05 | P0 |
| CAT-07 | Các nhóm modifier / tùy chọn và liên kết món | CAT-04, CAT-05 | P0 |
| CAT-08 | Transactional outbox và tiến trình publish sự kiện | CAT-04 | P0 |
| CAT-09 | Bộ phân giải Đơn hàng và tích hợp snapshot | CAT-05, CAT-07 | P0 |
| CAT-10 | Ranh giới hoàn tất đơn với Inventory và nghiệm thu end-to-end | CAT-09 + Inventory | P0 |
| CAT-11 | Bộ kiểm thử bảo mật, tương tranh, idempotency và hợp đồng | CAT-03 đến CAT-10 | P0 |
| CAT-12 | Combo, giá chi nhánh, tag và bảng chiếu metadata cho AI | CAT-11 | P1 |

## Tiêu chí Hoàn thành (Definition of Done) cho P0

- Toàn bộ các yêu cầu P0 và các bài kiểm thử nghiệm thu trong [Yêu cầu Kỹ thuật](./2026-10-12-requirements.md) đều vượt qua.
- Việc xác thực OpenAPI/AsyncAPI là một phần bắt buộc trong CI và các kiểu client sinh tự động được các bên tiêu thụ sử dụng.
- Mọi đường dẫn đọc/ghi công khai và phân giải nội bộ đều có bài kiểm thử phân quyền và cô lập tenant.
- Các bất biến món active / biến thể mặc định / modifier được thực thi trong logic service và các ràng buộc cơ sở dữ liệu khi có thể.
- Các biến đổi, bản ghi idempotency và tin nhắn outbox được lưu trữ nguyên tử.
- Order chụp lại snapshot dữ liệu catalog; Catalog không bao giờ ghi trạng thái của Order hay Inventory.
- Các sự kiện có phiên bản, có thể thử lại, được khử trùng lặp và có thể quan sát được.
- Luồng chạy Docker Compose cục bộ và luồng mục tiêu trên AWS sử dụng cùng ngữ nghĩa hợp đồng.
- Không có tính năng P1 nào bắt buộc cho việc nghiệm thu P0 và không client P0 nào phụ thuộc vào một trường dữ liệu của P1.

## Các Mục tiêu Bị Loại trừ Tường minh (Non-Goals)

- Không bổ sung tính sẵn sàng theo chi nhánh vào lược đồ P0.
- Không cho phép Thu ngân (Cashier) hoặc Bếp/Bar (Kitchen) có quyền ghi vào Catalog.
- Không lưu trữ công thức pha chế, tồn kho, giữ chỗ, VAT, chiết khấu hoặc trạng thái thanh toán trong Catalog.
- Không cho phép Order hoặc Inventory đọc trực tiếp cơ sở dữ liệu của Catalog.
- Không xóa cứng các bản ghi được tham chiếu bởi dữ liệu Order hoặc Inventory lịch sử.
- Không triển khai logic xếp hạng/gợi ý của AI bên trong Catalog Service.

## Bản ghi Phê duyệt Cuối cùng

- **Trạng thái:** PHÊ DUYỆT CÓ CẢNH BÁO (APPROVED WITH WARNINGS)
- **Giai đoạn thiết kế:** Hoàn tất giai đoạn Hoàn thiện (Finalization complete)
- **Vấn đề nghiêm trọng:** 0
- **Cảnh báo:** 4, được theo dõi ở trên dưới dạng các cổng kiểm soát trước khi viết code/tích hợp
- **Mức độ sẵn sàng triển khai:** Sẵn sàng sau khi hoàn thành các Cổng kiểm soát 1–5
- **Hoạt động tiếp theo:** Thực hiện lộ trình triển khai, bắt đầu với CAT-01 và CAT-02

## Các Kỹ năng Chuyên môn được Tham chiếu

- **code-reviewer:** Thực hiện đánh giá tính mạch lạc của thiết kế cuối cùng dựa trên các danh mục kiểm tra bảo mật, logic nghiệp vụ, hiệu năng và quy ước; không có mã nguồn triển khai nào.
- **api-design:** Xác thực các hợp đồng REST/OpenAPI, mô hình lỗi, phân trang, ETag, xác thực phân quyền, idempotency và tích hợp.
- **database-design:** Xác thực quyền sở hữu PostgreSQL chuẩn hóa, các ràng buộc, chỉ mục, phiên bản, migration và tiền tệ số nguyên.
- **log-processing:** Xác thực correlation, audit, outbox và các yêu cầu làm sạch dữ liệu nhạy cảm.
- **sequence-diagram:** Đã xem xét tính khả thi; các biểu đồ trình tự thời gian chạy vẫn là nhiệm vụ tiếp theo trong giai đoạn triển khai do chưa có đường dẫn mã nguồn thực tế.
