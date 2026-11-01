Started at: 2026/10/12 16:18:00
Finished at: 2026/10/12 16:18:00
Total time: 0 minutes
---

# Yêu cầu Kỹ thuật: Catalog Service (Dịch vụ Danh mục Món ăn)

## Nguồn tài liệu và Mức độ ưu tiên

- [Tầm nhìn (Vision)](./2026-10-12-vision.md) và [Báo cáo Đánh giá Spec Sentinel](./2026-10-12-vision-review.md).
- [Yêu cầu Nền tảng POS](../pos-platform/2026-10-12-requirements.md), [Hợp đồng Nền tảng POS](../pos-platform/2026-10-12-contracts.md), [Hợp đồng Identity & Access](../identity-access/2026-10-12-contracts.md).
- **P0:** Bắt buộc cho lát cắt POS đầu tiên và kiểm thử nghiệm thu tự động. **P1:** Được thiết kế dưới dạng phần mở rộng tương thích; không bắt buộc cho bản phát hành P0.
- Một mặt hàng Catalog thuộc sở hữu của tenant. Trong P0, thực đơn và bảng giá được chia sẻ chung giữa các chi nhánh của tenant đó. Trạng thái tồn kho và công thức pha chế/định lượng thuộc quyền sở hữu của Inventory Service.

## Tác nhân và Phân quyền

| Tác nhân | P0 Đọc | P0 Ghi | Phạm vi (Scope) |
|---|---|---|---|
| Owner (Chủ sở hữu) | Có | Có | Tất cả các chi nhánh của tenant được chọn. |
| Manager (Quản lý) | Có | Có | Phải có tư cách thành viên quản lý ACTIVE tại một chi nhánh ACTIVE thuộc tenant được chọn. Các thao tác ghi sẽ thay đổi thực đơn dùng chung cho toàn bộ chi nhánh của tenant. |
| Cashier (Thu ngân) | Có | Không | Chỉ xem thực đơn POS cho chi nhánh ACTIVE được chỉ định. |
| Kitchen (Bếp/Bar) | Có | Không | Chỉ đọc thực đơn cho chi nhánh ACTIVE được chỉ định. |
| Order workload (Tác vụ đơn hàng) | Có | Không | Phân giải mặt hàng hiện tại nội bộ cho yêu cầu tenant/chi nhánh đã được xác thực hợp lệ. |
| Recommendation/Reporting | Sự kiện/hình chiếu (Events/projection) | Không | Các sự kiện và dữ liệu chiếu (projections) theo phạm vi tenant. |

- Catalog phải sử dụng quyết định phân quyền hiện tại từ Identity Service. Một JWT Cognito hoặc role/branch do client tự cung cấp không bao giờ được phép tự cấp quyền truy cập.
- Mọi câu truy vấn và biến đổi (mutation) của Catalog đều phải lọc theo `tenant_id`; ID tài nguyên trong path, body và các liên kết liên quan đều được kiểm tra đối chiếu với tenant đó. Giới hạn quyền đọc cũng yêu cầu tư cách thành viên chi nhánh đang hoạt động đối với Manager/Cashier/Kitchen. Chủ sở hữu (Owner) có thể sử dụng ngữ cảnh toàn tenant.
- Quyền ghi P0 của Quản lý (Manager) là một năng lực toàn tenant có chủ đích của Catalog, được cấp bởi bất kỳ tư cách thành viên quản lý chi nhánh nào đang hoạt động trong tenant đó; nó không cấp quyền quản trị chi nhánh, quản lý nhân viên hay truy cập danh mục của tenant khác.
- Thu ngân (Cashier) là quyền chỉ đọc (read-only). Quyền của thu ngân đối với endpoint gốc `POST /api/v1/catalog/items/{itemId}/availability` đã bị thay thế đối với module này và phải được sửa đổi trước khi triển khai.
- Các thao tác biến đổi quản lý đều được ghi audit log với thông tin tác nhân, tư cách thành viên/chi nhánh được chọn, tác động toàn tenant, đối tượng mục tiêu, tóm tắt dữ liệu cũ/mới và correlation ID.

## Yêu cầu Chức năng

| Mã | Mức độ ưu tiên | Tác nhân | Hành động | Phản hồi yêu cầu |
|---|---|---|---|---|
| CAT-UC-01 | P0 | Owner/Manager | Tạo, đổi tên, sắp xếp lại thứ tự hoặc lưu trữ (archive) danh mục phẳng | Danh mục thuộc sở hữu của tenant; thao tác lưu trữ bảo toàn lịch sử và ngăn các món đang hoạt động trở thành không có danh mục. |
| CAT-UC-02 | P0 | Owner/Manager | Tạo món trong một danh mục với ít nhất một biến thể (variant) có giá | Tạo nguyên tử (atomically) món và (các) biến thể, xác thực quyền sở hữu tenant, SKU duy nhất và biến thể mặc định. |
| CAT-UC-03 | P0 | Owner/Manager | Chỉnh sửa tên món, SKU, danh mục hoặc thứ tự hiển thị | Kiểm tra phiên bản lạc quan (optimistic version check); cập nhật theo phạm vi và ghi bản ghi audit/sự kiện. |
| CAT-UC-04 | P0 | Owner/Manager | Đặt món sang trạng thái `ACTIVE`/`INACTIVE` hoặc lưu trữ (archive) | Áp dụng hiệu lực bán hàng theo chuỗi (cascade sellability); các tham chiếu lịch sử vẫn có thể phân giải được bởi các tác nhân nội bộ được ủy quyền. |
| CAT-UC-05 | P0 | Owner/Manager | Thêm, sửa, sắp xếp lại hoặc vô hiệu hóa biến thể/size | Giá là số nguyên không âm tính bằng VND; món đang hoạt động phải duy trì một biến thể mặc định đang hoạt động, nếu không việc kích hoạt sẽ thất bại. |
| CAT-UC-06 | P0 | Owner/Manager | Tạo/sửa/vô hiệu hóa nhóm modifier tái sử dụng và các tùy chọn | Nhóm và các tùy chọn thuộc sở hữu của tenant; xác thực quy tắc lựa chọn và giá. |
| CAT-UC-07 | P0 | Owner/Manager | Gắn/gỡ nhóm modifier vào món kèm ghi đè quy tắc tùy chọn | Số lượng tối thiểu/tối đa hiệu lực (effective min/max) và thứ tự hiển thị được lưu cho món đó; không tham chiếu chéo tenant. |
| CAT-UC-08 | P0 | Owner/Manager | Thay đổi giá của modifier hoặc biến thể | Tăng phiên bản; các lượt đọc mới sẽ thấy giá mới; các ảnh chụp (snapshots) hiện có của Order giữ nguyên không đổi. |
| CAT-UC-09 | P0 | POS user được ủy quyền | Liệt kê/xem danh mục có thể bán cho chi nhánh đang hoạt động | Chỉ trả về các kết hợp danh mục/món/biến thể/tùy chọn đang hoạt động với phạm vi tenant được máy chủ phân giải. |
| CAT-UC-10 | P0 | Owner/Manager | Liệt kê/xem danh mục quản trị, bao gồm các mục không hoạt động/đã lưu trữ | Phân trang, bộ lọc/sắp xếp theo danh sách trắng (allowlist), kiểm tra phạm vi trước khi truy vấn. |
| CAT-UC-11 | P0 | Order workload | Phân giải món, biến thể và các modifier được chọn để tạo đơn hàng | Trả về tên hiện tại, giá số nguyên, kết quả kiểm tra quy tắc và phiên bản danh mục, hoặc trả về từ chối có định kiểu rõ ràng (typed rejection). |
| CAT-UC-12 | P0 | Catalog worker | Xuất bản sự kiện thay đổi/tình trạng sẵn sàng | Thay đổi trạng thái và bản ghi outbox được commit nguyên tử cùng nhau; gửi lặp lại an toàn cho consumer (idempotent). |
| CAT-UC-13 | P1 | Owner/Manager | Tạo/sửa/vô hiệu hóa combo giá cố định | Các thành phần là các lựa chọn món/biến thể/modifier; không cho phép combo lồng nhau; các thành phần bắt buộc phải ở trạng thái có thể bán được. |
| CAT-UC-14 | P1 | Owner/Manager | Đặt/xóa ghi đè giá biến thể hiện tại theo chi nhánh | Ghi đè thuộc về một chi nhánh Identity trong cùng tenant; nếu không có sẽ rơi về giá biến thể mặc định của tenant. |
| CAT-UC-15 | P1 | Owner/Manager | Chỉnh sửa mô tả món và các tag được chuẩn hóa | Slug của tag là duy nhất trong phạm vi tenant; hình chiếu AI nhận metadata có phiên bản. |

## Quy tắc Nghiệp vụ

### Danh mục và Món ăn (Categories and Items)

1. Cấu trúc phân cấp danh mục là dạng phẳng (flat) trong P0. Món ăn có chính xác một danh mục chính, và cả hai bản ghi phải dùng chung `tenant_id`.
2. Danh mục chỉ có thể được lưu trữ (archive) khi nó không chứa món ăn nào chưa lưu trữ; nếu không sẽ trả về `409 conflict` và yêu cầu chuyển món sang danh mục khác hoặc lưu trữ các món trước. Không xóa cứng (hard delete) trong P0.
3. Vòng đời của món ăn: `ACTIVE ↔ INACTIVE`, và từ trạng thái nào cũng có thể chuyển sang → `ARCHIVED`. `ARCHIVED` là trạng thái kết thúc (terminal). SKU vẫn được giữ đặt chỗ trong phạm vi tenant sau khi lưu trữ.
4. Món ăn ở trạng thái `ACTIVE` phải có chính xác một biến thể mặc định đang hoạt động và ít nhất một biến thể đang hoạt động. Cho phép tạo bản nháp không hoạt động trong một giao dịch duy nhất nếu cấu hình bán hàng bắt buộc chưa hoàn chỉnh; việc kích hoạt một món chưa hoàn chỉnh sẽ trả về lỗi `409`.
5. Vô hiệu hóa hoặc lưu trữ biến thể đang hoạt động cuối cùng, hoặc biến thể mặc định mà không chọn biến thể thay thế trong cùng một giao dịch, sẽ trả về lỗi `409` đối với một món đang active.
6. Tính khả dụng để bán trên POS đòi hỏi danh mục, món ăn và biến thể đều phải đang hoạt động (`ACTIVE`). Món ăn ở mức `INACTIVE`/`ARCHIVED` sẽ ẩn toàn bộ các biến thể của nó; biến thể không hoạt động chỉ tự ẩn chính nó. Việc bật/tắt trong Danh mục là độc lập với tình trạng tồn kho vật lý trong Dịch vụ Tồn kho (Inventory). Luồng tiêu thụ phía Order cũng phải xác thực các quy tắc tồn kho/giữ chỗ hiện tại trong Inventory.
7. Trạng thái danh mục P0 có phạm vi toàn cầu đối với tenant. Các chỉnh sửa của Quản lý (Manager) được audit là tác động toàn tenant ngay cả khi người quản lý đó được phân công tại một chi nhánh cụ thể.

### Biến thể, Giá và Modifier (Variants, Prices and Modifiers)

8. Mọi món ăn đều có ít nhất một biến thể, bao gồm một biến thể `STANDARD` cho các món không phân size. Việc chọn món từ Order phải phân giải ra một `variantId` cụ thể trước khi định giá; dữ liệu đầu vào `variantId` có thể null kiểu cũ chỉ ánh xạ sang biến thể mặc định khi món đó chỉ có duy nhất một biến thể đang hoạt động. Các client mới bắt buộc phải gửi `variantId`.
9. Tiền tệ trong P0 là kiểu `BIGINT` dạng số nguyên VND, với `currency = VND`. Giá cơ sở của biến thể và phụ thu của modifier là số không âm; Catalog không triển khai chiết khấu hoặc delta modifier âm trong P0. Order sở hữu logic thuế VAT/làm tròn và ảnh chụp giá cuối cùng.
10. Các tùy chọn modifier thuộc về chính xác một nhóm. Một nhóm có thể gắn vào nhiều món ăn; một tùy chọn chỉ có thể được chọn thông qua một nhóm được gắn vào món ăn đã chọn.
11. Cấu hình mặc định của nhóm chứa `minSelections` và `maxSelections` thỏa mãn `0 ≤ min ≤ max ≤ số tùy chọn đang active`; tùy chọn (optional) nghĩa là `min = 0`, bắt buộc (required) nghĩa là `min ≥ 1`. Liên kết giữa món và nhóm có thể ghi đè min/max và thứ tự hiển thị. Quy tắc có hiệu lực là phần ghi đè của liên kết nếu có, ngược lại dùng mặc định của nhóm.
12. Mỗi tùy chọn modifier chỉ được chọn tối đa một lần cho mỗi đơn vị món trong P0; việc trùng lặp ID tùy chọn sẽ bị từ chối. Số lượng theo từng tùy chọn và lặp lại định lượng topping nằm ngoài phạm vi P0. Số lượng lựa chọn được tính theo từng nhóm, và mọi tùy chọn được chọn đều phải đang hoạt động (`ACTIVE`).
13. Việc hủy kích hoạt một tùy chọn/nhóm sẽ bị từ chối nếu nó làm cho yêu cầu số lượng tối thiểu bắt buộc của một món `ACTIVE` trở nên bất khả thi. Ngoài ra, cùng một giao dịch có thể hủy kích hoạt món bị ảnh hưởng; nghiêm cấm việc để lại cấu hình không hợp lệ trong im lặng.
14. Các thay đổi về giá và cấu hình sử dụng cơ chế phiên bản lạc quan (optimistic versions). Một header `If-Match` lỗi thời sẽ trả về `409`; một thao tác phân giải từ Order sẽ đọc một trạng thái Catalog đã commit nhất quán và trả về các phiên bản được sử dụng cho ảnh chụp (snapshot) của nó.

### Các phần mở rộng P1 (P1 Extensions)

15. Combo không được chứa combo con lồng nhau. Mỗi thành phần bắt buộc phải phân giải ra một món/biến thể đang hoạt động và lựa chọn modifier hợp lệ. Giá cố định của combo là số nguyên không âm tính bằng VND; Order sẽ chụp lại cấu trúc combo và mức giá.
16. Combo trở nên không thể bán được khi bất kỳ thành phần bắt buộc nào không còn bán được. Kiểm tra tồn kho vật lý trong Inventory vẫn là một bước kiểm tra hoàn tất đơn hàng riêng biệt.
17. Ghi đè giá theo chi nhánh là duy nhất cho bộ ba `(tenant_id, branch_id, variant_id)` và chỉ áp dụng cho thời điểm hiện tại. Một bản ghi đè có thể bị xóa để quay về giá mặc định của tenant. Chi nhánh phải ở trạng thái ACTIVE và thuộc về tenant; Catalog lưu trữ ID của chi nhánh, không tạo khóa ngoại cơ sở dữ liệu (DB FK) sang Identity DB.
18. Mô tả món ăn là văn bản tùy chọn, có giới hạn độ dài và được làm sạch (sanitized). Tag có slug được chuẩn hóa duy nhất trong phạm vi tenant và tên hiển thị; tag được tái sử dụng giữa các món. Xử lý khởi động nguội AI (AI cold-start) nằm ở hạ tầng hạ nguồn và có thể nhận sự kiện metadata sau.

## Yêu cầu Tích hợp và API

| Ranh giới | Yêu cầu |
|---|---|
| Identity | Xác thực Cognito JWT tại gateway/service và phân giải tenant/chi nhánh/quyền hạn hiện tại thông qua dịch vụ ủy quyền nội bộ của Identity; từ chối truy cập (fail closed) khi dịch vụ phân quyền không khả dụng. |
| Public REST | Các route `/api/v1/catalog`; chuẩn OpenAPI 3.1; cấu trúc bao bọc (envelope) thành công/lỗi chuẩn; `X-Correlation-Id`, `Idempotency-Key` cho các biến đổi có thể thử lại, `If-Match` cho các cập nhật có phiên bản. |
| Phân trang (Pagination) | Danh sách quản trị món/danh mục/nhóm sử dụng phân trang offset (`page`, `limit`, `total`, `totalPages`), mặc định 20 / tối đa 100. Các mệnh đề phạm vi (scope predicates) phải đứng trước khi phân trang. |
| Cache đọc POS | ETag/`If-None-Match` có thời gian sống ngắn theo phạm vi tenant; khóa cache bao gồm ngữ cảnh tenant, chi nhánh và phiên bản catalog. Quyền truy cập được kiểm tra trước khi trả về `304`. |
| Order | Endpoint phân giải giá/kiểm tra hàng loạt (bulk resolution) riêng tư chấp nhận ID món, biến thể và modifier kèm ngữ cảnh tenant/chi nhánh, trả về tên, giá, phiên bản và mã từ chối có kiểu rõ ràng. Order lưu trữ các bản snapshot bất biến. |
| Inventory | Inventory sở hữu công thức/tồn kho. Các sự kiện Catalog mang ID món/biến thể ổn định và thay đổi trạng thái; không dùng câu lệnh SQL join chéo service hoặc DB FK giữa các database. |
| Recommendation/Reporting | EventBridge/SQS tiêu thụ các sự kiện Catalog có phiên bản với cơ chế chuyển phát ít nhất một lần (at-least-once delivery), chống trùng lặp (deduplication), thử lại/DLQ và độ tươi mới của dữ liệu chiếu (projection freshness). |
| Audit | Các thay đổi nhạy cảm trong quản lý thực đơn bao gồm tác nhân, phạm vi, tóm tắt trước/sau và correlation ID mà không ghi bí mật hay PII không cần thiết. |

Tính khả dụng để bán hiệu lực của Catalog chính là việc bật/tắt trên thực đơn. Phía POS/Recommendation có thể ẩn thêm các món hết hàng dựa trên dữ liệu từ Inventory; Order đưa ra quyết định hoàn tất đơn hàng cuối cùng. Một dữ liệu chiếu (projection) lỗi thời của Recommendation không thể dùng để cấp phép tạo một Order.

### Các sửa đổi bắt buộc đối với Hợp đồng Cha (Parent Contract Amendments)

| Khẳng định trong hợp đồng cha | Quyết định của Service | Hành động tiếp theo bắt buộc |
|---|---|---|
| Thu ngân (Cashier) có thể gọi `POST /catalog/items/{itemId}/availability` | Thu ngân là quyền chỉ đọc (read-only) đối với Catalog; chỉ Owner/Manager mới có quyền ghi. | Sửa đổi ma trận phân quyền endpoint của hợp đồng cha trước khi triển khai. |
| Cột `catalog_items.category` là một nhãn văn bản | Danh mục là một thực thể thuộc sở hữu của tenant và món ăn lưu trữ khóa ngoại `category_id`. | Giữ lại nhãn hiển thị danh mục trong DTO đọc khi cần thiết; cập nhật hợp đồng lược đồ. |
| Mối quan hệ phẳng `item_modifiers` | Nhóm tái sử dụng cộng với liên kết món-nhóm sẽ sở hữu các quy tắc lựa chọn. | Cập nhật lược đồ dữ liệu/API; giữ lại khái niệm `modifierIds` tại ranh giới Order. |
| Cột `OrderLineInput.variantId` có thể null | Các client Order mới bắt buộc phải cung cấp biến thể cụ thể; trường hợp bỏ qua kiểu cũ chỉ giải quyết được khi chỉ có duy nhất một biến thể đang hoạt động. | Ghi nhận ràng buộc xác thực này vào hợp đồng Order trước khi triển khai Order. |
| Hợp đồng cha P2 liệt kê AI cold start | Catalog P1 chỉ cung cấp mô tả/tag và sự kiện metadata. | Thuật toán khởi động nguội AI vẫn nằm ở hạ nguồn/P2 trừ khi được phê duyệt riêng. |

## Yêu cầu Phi chức năng

Đây là các mục tiêu thử nghiệm (demo targets), không phải là cam kết SLA môi trường sản xuất.

| Hạng mục | Yêu cầu | Mục tiêu |
|---|---|---|
| Hiệu năng | Danh sách/chi tiết catalog trên POS | p95 ≤ 500 ms dưới hồ sơ dữ liệu demo cha với thực đơn mẫu đại diện; cache có thể hỗ trợ nhưng tính chính xác phải được ưu tiên hàng đầu. |
| Hiệu năng | Phân giải hàng loạt cho Order | p95 ≤ 300 ms cho một đơn hàng tối đa 20 dòng và 50 lựa chọn modifier, không tính thời gian của Inventory. |
| Khả năng mở rộng | API/worker | API không trạng thái (stateless) và tiến trình outbox publisher có thể mở rộng độc lập; nhóm kết nối DB (connection pool) có giới hạn. |
| Tính nhất quán | Biến đổi Catalog | Trạng thái món/biến thể/modifier và sự kiện outbox được commit nguyên tử; các lượt đọc không bao giờ nhìn thấy món đang hoạt động nhưng cấu hình dang dở. |
| An toàn bảo mật | Cô lập dữ liệu | Mọi thao tác đọc/ghi và liên kết đều xác thực quyền sở hữu tenant tại tầng service và repository; có các bài kiểm thử BOLA/IDOR phủ định. |
| An toàn bảo mật | Đầu vào | Xác thực tên/SKU/giá/quy tắc/ID; dùng câu truy vấn SQL tham số hóa (parameterized SQL); làm sạch mô tả/ghi chú và thông báo lỗi an toàn. |
| Độ tin cậy | Tính bất biến (Idempotency) | Cùng key/hash sẽ phát lại kết quả ban đầu; cùng key khác hash trả về lỗi `duplicate_request` 409; consumer sự kiện khử trùng lặp theo event ID. |
| Khả năng quan sát | Tracing | Log JSON có cấu trúc kèm correlation ID, tenant, actor, resource, outcome và duration; tuyệt đối không log JWT/secret/PII. |
| Khả năng quan sát | Cảnh báo | Theo dõi các biến đổi thất bại, từ chối giá lỗi thời, độ trễ outbox (lag), độ sâu hàng đợi DLQ, p95 đọc danh mục và lỗi phân quyền. |
| Triển khai | Khả năng tái lập | Chạy Docker Compose cục bộ, PostgreSQL riêng cho từng service và môi trường mục tiêu AWS ECS Fargate/EventBridge/SQS. |
| Khả năng bảo trì | Kỷ luật hợp đồng | OpenAPI/AsyncAPI và migration được xác thực tự động trong CI; các phần mở rộng P1 phải tương thích ngược. |

## Dữ liệu và Ràng buộc cho Kiến trúc

- Java 21/Spring Boot 3.x, cơ sở dữ liệu PostgreSQL do service tự sở hữu, định danh UUID v7, timestamp UTC `TIMESTAMPTZ`, tiền tệ `BIGINT` dạng số nguyên VND.
- Các thực thể cốt lõi: categories, catalog items, item variants, modifier groups, modifier options, item-group links, các bản ghi outbox và idempotency. P1: combos/components, branch variant prices, tags và item-tag links.
- Thực thi tính nhất quán tenant bằng các khóa phức hợp/khóa ngoại (composite keys/FKs) bên trong Catalog DB. Không tạo khóa ngoại (FK) sang cơ sở dữ liệu của Identity, Order, Inventory hay Recommendation.
- Lưu trữ (Archive) bảo toàn các ID món/biến thể/modifier cần thiết cho các tham chiếu lịch sử của Order và Inventory.
- Lên kế hoạch đánh index cho tenant/category/status/sort/SKU, biến thể món, liên kết nhóm, lựa chọn modifier, tiến trình publish sự kiện và tra cứu ghi đè giá chi nhánh.
- Không áp dụng phân vùng (partitioning) cho các bảng Catalog thông thường ở quy mô MVP; xem xét sự tăng trưởng/thời gian lưu trữ của bảng outbox một cách riêng biệt.
- Mọi cột trong lược đồ đều cần có ghi chú/chú thích migration mô tả rõ ràng. Migration phải tương thích tiến tới kèm theo kế hoạch phục hồi/rollback và dữ liệu khởi tạo (seed data) cục bộ có tính tiền định.

## Các bài Kiểm thử Nghiệm thu (Acceptance Tests)

1. Owner và Manager đang active có thể tạo/sửa catalog toàn tenant; Cashier/Kitchen thực hiện thao tác ghi sẽ nhận mã `403`.
2. Một Manager chỉ có quyền truy cập ở tenant A không thể đọc/ghi ở tenant B; thu hồi tư cách thành viên manager sẽ từ chối ngay yêu cầu tiếp theo dù mang JWT cũ.
3. Lưu trữ danh mục khi vẫn còn món chưa lưu trữ sẽ trả về mã lỗi `409`; sau khi các món đã được di chuyển hoặc lưu trữ, thao tác sẽ thành công mà không xóa mất lịch sử.
4. Tạo món ăn sẽ lưu đồng thời một biến thể mặc định và giá số nguyên hợp lệ trong một giao dịch nguyên tử; trùng lặp SKU trong cùng tenant trả về `409`.
5. Vô hiệu hóa biến thể đang hoạt động cuối cùng hoặc biến thể mặc định của một món đang active sẽ thất bại, trừ khi biến thể thay thế hoặc chuyển đổi trạng thái được commit cùng nhau.
6. Món/biến thể/modifier không hoạt động sẽ bị loại khỏi các lựa chọn POS mới; việc tra cứu lịch sử được phân quyền vẫn khả dụng trong nội bộ.
7. Tái sử dụng nhóm modifier sẽ áp dụng quy tắc min/max hiệu lực của từng món; việc chọn thiếu/thừa số lượng, ID tùy chọn trùng lặp/thuộc tenant khác/không hoạt động đều sẽ thất bại.
8. Một cập nhật giá sẽ làm thay đổi các lần phân giải tiếp theo của Order, trong khi bản snapshot của Order hiện có giữ nguyên không đổi.
9. Trường hợp thiếu `variantId` chỉ phân giải được khi món đó có chính xác một biến thể đang hoạt động; nếu mơ hồ nhiều biến thể sẽ thất bại với lỗi xác thực (validation error).
10. Món đang active trong Catalog nhưng hết hàng vật lý sẽ bị từ chối bởi quy trình hoàn tất đơn hàng của Order/Inventory, ngay cả khi giao diện xem của POS/Recommendation bị cũ (stale).
11. Cùng idempotency key và hash nội dung sẽ không tạo bản ghi biến đổi/outbox trùng lặp; cùng key nhưng khác hash nội dung sẽ trả về mã `409`.
12. Ghi đè giá chi nhánh ở P1 chỉ ảnh hưởng đến chi nhánh được chọn và tự động rơi về giá mặc định của tenant khi bị xóa.
13. Combo ở P1 có thành phần bắt buộc không hoạt động sẽ trở thành không thể bán được; combo lồng nhau sẽ bị từ chối.
14. Chuẩn hóa tag ở P1 ngăn chặn slug trùng lặp trong tenant; sự kiện metadata mang phiên bản và không chứa dữ liệu nhạy cảm.
15. Các bài kiểm thử hợp đồng và bảo mật bao phủ trường hợp ID chéo tenant trong mọi liên kết, header `If-Match` lỗi thời, thiếu xác thực phân quyền và cơ chế thử lại/DLQ của outbox.

## Các Quyết định được Chuyển tiếp sang Kiến trúc và Hợp đồng

1. Ràng buộc cụ thể trên bảng category/item/group và ranh giới giao dịch cho các bất biến của món đang hoạt động.
2. Cấu trúc request/response của bộ phân giải hàng loạt (bulk resolver) phía Order, các mã lý do không khả dụng và ngữ nghĩa phiên bản.
3. Liệu việc lưu trữ danh mục hay cập nhật giá sẽ xuất bản các sự kiện vô hiệu hóa ở cấp độ món hay sự kiện phiên bản tổng hợp.
4. Cơ chế sinh ETag, vô hiệu hóa bộ nhớ đệm (cache invalidation) và luồng đọc đối chiếu với phân quyền hiện tại.
5. Cấu trúc cấu thành combo ở P1 và chi tiết API/lược đồ ghi đè giá theo chi nhánh.

## Các Kỹ năng Chuyên môn được Tham chiếu

- **spec-reviewer:** Chuyển đổi tám cảnh báo trong Vision thành các chính sách tường minh và các sửa đổi bổ sung cho hợp đồng cha.
- **api-design:** Thiết kế REST/OpenAPI, kiểm tra vai trò, phân trang, GET có điều kiện, kiểm soát tương tranh lạc quan và định dạng lỗi chuẩn.
- **database-design:** Ràng buộc quan hệ theo tenant, lập chỉ mục (indexes), UUID v7, tiền tệ số nguyên, ghi chú cột lược đồ và lập kế hoạch migration.
- **log-processing:** Tín hiệu audit/correlation có cấu trúc kèm làm sạch dữ liệu nhạy cảm; không thay đổi mã nguồn ghi log.

## Phê duyệt

- **Trạng thái:** Đã được người dùng phê duyệt
- **Giai đoạn:** Khám phá Yêu cầu (Requirements Discovery)
- **Giai đoạn tiếp theo sau phê duyệt:** Thiết kế Kiến trúc (Architecture Design)
