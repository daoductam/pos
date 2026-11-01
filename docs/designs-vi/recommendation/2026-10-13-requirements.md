Started at: 2026/10/13 00:00:00
Finished at: 2026/10/13 00:00:00
Total time: 0 minutes
---

# Module 8 — Gợi ý món / Upsell: Requirements

**Ngày:** 2026-10-13  
**Trạng thái:** Approved with warnings  
**Vision:** [Recommendation Vision](./2026-10-13-vision.md)  
**Vision review:** [Recommendation Vision Review](./2026-10-13-vision-review.md)  
**Phạm vi:** M — Standard Service Flow

## 1. Mục tiêu và ranh giới (Goals and boundaries)

Recommendation Service là nguồn chân lý (source of truth) cho các luật kết hợp (association rules), metadata mô hình/bộ xếp hạng, các phiên gợi ý (suggestion sessions) và việc thu nạp dữ liệu phân tích gợi ý. Service không sở hữu món/giá của Catalog, số dư tồn kho của Inventory, trạng thái đơn hàng của Order hay hồ sơ khách hàng của Loyalty.

### Mức độ ưu tiên (Priority)

- **P0:** Gợi ý giỏ hàng thời gian thực (realtime) bằng luật kết hợp; lọc theo chi nhánh/giỏ hàng/tính khả dụng; ghi nhận sự kiện `SuggestionShown` và `SuggestionAccepted` đúng ngữ nghĩa nghiệp vụ.
- **P1:** Cá nhân hóa bằng mô hình ALS theo customer opaque key; tác vụ huấn luyện đêm / thẩm định / kích hoạt mô hình với cơ chế rollback/fallback an toàn.
- **P2:** Làm giàu dữ liệu khởi động nguội (cold-start) qua DeepSeek và thiết kế điểm mở rộng ngữ cảnh (khung giờ/thời tiết); tuyệt đối không ảnh hưởng đến luồng P0/P1.

## 2. Các tác nhân và tích hợp (Actors and integrations)

| Tác nhân / Hệ thống | Quyền hạn và vai trò |
|---|---|
| Thu ngân (Cashier) | Gọi API gợi ý cho giỏ hàng hợp lệ; xem/kích hoạt hiển thị; không có quyền sửa luật |
| Quản lý (Manager) | Gọi API gợi ý; tạo/chỉnh sửa bản nháp (draft) luật kết hợp trong phạm vi tenant; xem sức khỏe luật/mô hình; không có quyền công bố luật ra production |
| Chủ chuỗi (Owner) | Đầy đủ quyền của Manager; công bố (publish) / ngừng kích hoạt (retire) luật và chính sách mô hình trong tenant |
| POS / Angular | Gọi API gợi ý, render giao diện, gửi xác nhận hiển thị sau khi render; gửi báo cáo chấp thuận sau khi Order thêm món thành công |
| Order Service | Cung cấp hình chiếu giỏ hàng/đơn hàng; thẩm quyền cuối cùng quyết định thêm dòng vào đơn; phát sự kiện đơn hàng đã thanh toán |
| Catalog Service | Nguồn dữ liệu món/biến thể/giá/thực đơn khả dụng và phát sự kiện thay đổi dữ liệu |
| Inventory Service | Sự kiện/dữ liệu biến động tồn kho/khả dụng; Recommendation chỉ duy trì hình chiếu cục bộ |
| Loyalty Service | Phân giải số điện thoại thành `customerId` opaque / feature key; sở hữu sự đồng thuận (consent) và dữ liệu PII |
| Reporting / Audit | Tiêu thụ sự kiện gợi ý / mô hình / luật; không làm thay đổi trạng thái của Recommendation |
| Scheduler / ML Worker | Huấn luyện / thẩm định / kích hoạt mô hình; tái dựng hình chiếu; quản lý thử lại qua DLQ |
| DeepSeek adapter | Làm giàu metadata/feature ngữ nghĩa bất đồng bộ (P2); không nằm trên luồng gọi của POS |

## 3. Quy tắc nghiệp vụ (Business rules)

### 3.1 Tenant, chi nhánh và phạm vi (Tenant, branch and scope)

1. Các luật kết hợp (association rules) thuộc quyền sở hữu của tenant và không có biến thể riêng theo từng chi nhánh trong P0.
2. Mọi ứng viên gợi ý bắt buộc phải được lọc theo chi nhánh hiện tại của đơn hàng.
3. Mô hình ALS được huấn luyện trên toàn bộ lịch sử của tenant; danh sách ứng viên cuối cùng vẫn phải được lọc theo chi nhánh.
4. Mọi thao tác đọc/ghi đều bắt buộc có vị từ tenant ở cả tầng service và repository; `branchId` phải khớp với ngữ cảnh được ủy quyền.
5. Owner/Manager không được phép sử dụng `customerId`, `orderId` hay item ID để truy xuất trái phép dữ liệu của tenant khác.

### 3.2 Tính đủ điều kiện của ứng viên (Candidate eligibility)

Một món ứng viên được coi là hợp lệ khi thỏa mãn đồng thời các điều kiện:

- Tồn tại trong hình chiếu Catalog của đúng tenant;
- Món/biến thể đang ở trạng thái active và được phép kinh doanh tại chi nhánh;
- Không xuất hiện trong giỏ hàng hiện tại (dựa trên định danh món chuẩn tắc);
- Không bị hình chiếu tồn kho đánh dấu là hết hàng (unavailable);
- Độ tươi của hình chiếu không vượt quá ngưỡng thời gian sống (TTL) quy định;
- Có sẵn ảnh chụp tên và giá bán để POS hiển thị;
- Không vi phạm các chính sách luật/mô hình hoặc danh sách chặn (suppression).

Order chịu trách nhiệm thẩm định tính khả dụng và quyền sở hữu lần cuối khi thực hiện thêm dòng vào giỏ. Dữ liệu trễ từ Recommendation không được làm gián đoạn luồng thanh toán (checkout).

### 3.3 Xếp hạng luật kết hợp (Association ranking)

- Vế điều kiện (antecedent) có thể là một món / biến thể / topping đơn lẻ hoặc chữ ký giỏ hàng (basket signature) gồm nhiều phần tử.
- Mỗi luật tối thiểu phải có các chỉ số: `support`, `confidence`, `lift`, `priority`, `status`, `version` và thời gian hiệu lực.
- Thứ tự xếp hạng mặc định: độ ưu tiên mô hình/luật → `confidence` GIẢM DẦN → `lift` GIẢM DẦN → `support` GIẢM DẦN → ID ứng viên ổn định TĂNG DẦN.
- Số lượng gợi ý `topK` mặc định là 3, tối đa 5; chính sách này có thể cấu hình theo từng tenant.
- Nếu không còn luật hoặc ứng viên hợp lệ, trả về danh sách rỗng `items=[]`, không fallback sang món bán chạy (popular) hay luật mặc định trong P0.
- Phiên bản luật (rule version) bắt buộc phải có trong response và sự kiện để phục vụ truy xuất nguồn gốc.

### 3.4 Phiên gợi ý và phiên bản giỏ hàng (Suggestion session and cart version)

- Mỗi phản hồi API sẽ tạo mới hoặc tái sử dụng một `suggestionId` / session dựa trên bộ giá trị `(tenantId, branchId, orderId, cartVersion, requestKey)`.
- Phiên gợi ý sẽ hết hạn sau thời gian TTL chính sách (mặc định 10 phút) hoặc ngay khi phiên bản giỏ hàng (cart version) thay đổi.
- Các trường `itemId`, `variantId`, `ruleId`, thứ hạng và điểm số lưu trong phiên mang tính bất biến.
- Thao tác chấp thuận (accept) chỉ được ghi nhận cho ứng viên thuộc phiên đó, đúng đơn hàng/chi nhánh/tenant và chưa từng ghi nhận cho cùng tương tác.

### 3.5 Ngữ nghĩa phân tích đo lường (Analytics semantics)

- `SuggestionShown` chỉ được ghi nhận sau khi POS đã render thành công lên màn hình và gửi lệnh kèm thời điểm `renderedAt` cùng ID tương tác máy khách.
- `SuggestionAccepted` chỉ được ghi nhận sau khi Order thêm món vào giỏ thành công; yêu cầu bắt buộc phải chứa `orderLineId` hoặc `orderVersionAfterAdd` làm bằng chứng đối chiếu.
- Hệ thống phân tích tuyệt đối không tự thêm món, không làm thay đổi Order hay tự quyết định doanh thu.
- Gửi trùng khóa nguồn (source key) sẽ trả về kết quả ngữ nghĩa cũ; các tương tác đến sai thứ tự không làm suy giảm trạng thái đã ghi nhận trước đó.

## 4. Yêu cầu chức năng P0 (Functional requirements P0)

### FR-REC-001 — Lấy danh sách gợi ý giỏ hàng (Get cart recommendations)

Đầu vào tối thiểu: `orderId`, `branchId`, `cartVersion`, danh sách định danh dòng giỏ hàng / ảnh chụp tham chiếu và trường tùy chọn `customerId` opaque / feature key.

Hệ thống phải:

1. Thẩm định quyền tenant / chi nhánh / vai trò và ngữ cảnh đơn hàng;
2. Trích xuất chữ ký giỏ hàng từ hình chiếu Order hoặc ảnh chụp request đã liên kết với phiên bản đơn hàng;
3. Tìm kiếm các luật kết hợp đang active trên toàn tenant;
4. Loại bỏ các ứng viên đã có sẵn trong giỏ hàng;
5. Loại bỏ các ứng viên ngừng kinh doanh, sai chi nhánh, hết hàng hoặc hình chiếu bị quá hạn (stale);
6. Xếp hạng tất định và giới hạn số lượng theo top-K;
7. Lưu vết phiên gợi ý cùng phiên bản luật / mô hình / hình chiếu;
8. Trả về kết quả gợi ý trong cam kết p95 mà không gọi đồng bộ DeepSeek, thời tiết hay bên thứ ba.

Nếu hình chiếu của Order chưa kịp đồng bộ phiên bản giỏ hàng tương ứng, trả về mã `cart_context_unavailable` hoặc yêu cầu client làm mới; không đoán mò từ đơn hàng cũ.

### FR-REC-002 — Quản trị luật kết hợp (Rule governance)

- Quản lý / Chủ chuỗi có quyền tạo mới / chỉnh sửa bản nháp (draft) của luật trong phạm vi tenant.
- Duy nhất Chủ chuỗi (Owner) có quyền công bố (publish) hoặc ngừng áp dụng (retire) luật ra môi trường production.
- Mọi thao tác publish/retire đều bắt buộc có lý do (reason), người thực hiện (actor), số phiên bản (version) và sự kiện audit tương ứng.
- Khi công bố luật, hệ thống phải kiểm tra: không tự gợi ý chính nó (no self-suggestion), định danh ứng viên hợp lệ, thời gian hiệu lực chuẩn, dải giá trị các chỉ số hợp lệ và thuộc đúng tenant.
- Mỗi lần công bố sẽ tạo ra một phiên bản bất biến; chỉnh sửa luật đã công bố sẽ tạo ra một phiên bản mới, không sửa đè lịch sử cũ.

### FR-REC-003 — Hình chiếu thực đơn và trạng thái khả dụng (Availability/catalog projections)

Recommendation phải tiêu thụ các sự kiện có đánh phiên bản từ Catalog và Inventory để cập nhật hình chiếu cục bộ một cách lũy nghiệm:

- Sự kiện tạo mới / cập nhật / thay đổi khả dụng của món ăn;
- Sự kiện món ăn khả dụng theo từng chi nhánh;
- Sự kiện thay đổi số lượng tồn kho / khả dụng;
- Metadata về phiên bản sự kiện và độ tươi dữ liệu.

Hình chiếu phải lưu trữ watermark, thời điểm của sự kiện cuối cùng và phiên bản nguồn. Nếu hình chiếu của chi nhánh/món bị trễ quá thời gian TTL, ứng viên đó sẽ bị loại bỏ và phản hồi trả về cờ `availabilityStatus=STALE` cùng metadata độ tươi an toàn cho giao diện.

### FR-REC-004 — Xác nhận gợi ý đã hiển thị (Suggestion shown)

Giao diện POS gọi lệnh xác nhận hiển thị sau khi gợi ý đã render xong. Recommendation phải:

- Xác thực phiên gợi ý và định danh món ứng viên;
- Kiểm tra tính hợp lệ của phạm vi tenant / chi nhánh / đơn hàng;
- Khử trùng lặp theo bộ bốn `(suggestionId, itemId, interactionId, eventType)`;
- Ghi nhận sự kiện bất biến vào cơ sở dữ liệu và hàng đợi outbox;
- Không làm nghẽn hoạt động của POS nếu broker phân tích tạm thời gặp sự cố; lưu trữ tại outbox cục bộ để thử lại.

### FR-REC-005 — Xác nhận gợi ý được chấp thuận (Suggestion accepted)

Giao diện POS chỉ được gọi lệnh chấp thuận sau khi Order thêm món vào giỏ thành công. Recommendation phải:

- Xác thực món thuộc phiên gợi ý đó và phiên chưa hết hạn theo chính sách;
- Lưu vết bằng chứng đối chiếu `orderLineId` hoặc `orderVersionAfterAdd`;
- Khử trùng lặp các lần click / thử lại trùng nhau;
- Phát sự kiện `SuggestionAccepted.v1` duy nhất một lần cho mỗi tương tác;
- Không xem việc chấp thuận là sự kiện thanh toán / doanh thu; sự kiện `OrderPaid.v1` mới là nguồn dữ liệu thanh toán chính thức.

## 5. Yêu cầu chức năng P1 (Functional requirements P1)

### FR-REC-101 — Ngữ cảnh đặc trưng khách hàng dạng opaque (Customer opaque feature context)

- Loyalty là đơn vị sở hữu việc tra cứu số điện thoại, danh tính khách hàng, sự đồng thuận và dữ liệu cá nhân (PII).
- Recommendation tuyệt đối không nhận, không lưu trữ và không ghi log số điện thoại dạng văn bản thuần (plaintext).
- POS chỉ gửi `customerId` dạng opaque sau khi Loyalty phân giải thành công hoặc gọi qua một hợp đồng đặc trưng nội bộ đã được Loyalty cấp phép.
- Ngữ cảnh đặc trưng phải chứa: tenant, khóa khách hàng (customer key), trạng thái đồng thuận/đủ điều kiện, phiên bản đặc trưng và thời điểm tạo.
- Các khóa khách hàng khác tenant, khách hàng đã thu hồi quyền / bị khóa hoặc thiếu sự đồng thuận sẽ không được đưa vào huấn luyện hay xếp hạng ALS.

### FR-REC-102 — Xếp hạng cá nhân hóa bằng mô hình ALS (ALS personalized ranking)

Khi có mô hình ALS đang active và khách hàng có đủ tín hiệu lịch sử:

1. Nạp vector đặc trưng ẩn (latent features) của khách hàng theo phiên bản mô hình active;
2. Sinh tập ứng viên giới hạn trên toàn tenant;
3. Áp dụng các bộ lọc giỏ hàng / chi nhánh / thực đơn / tồn kho tương tự luồng P0;
4. Xếp hạng dựa trên điểm số ALS kết hợp cơ chế phá vỡ thế hòa tất định;
5. Trả về lý do `PERSONALIZED_ALS`, phiên bản mô hình và metadata chất lượng/độ tin cậy không nhạy cảm.

Nếu khách hàng chưa đủ lượt tương tác, ngữ cảnh đặc trưng không khả dụng, mô hình chưa active hoặc tập ứng viên không hợp lệ, hệ thống tự động fallback về luật kết hợp P0 kèm lý do `fallbackReason` an toàn.

### FR-REC-103 — Tác vụ huấn luyện ban đêm (Nightly training)

Tác vụ huấn luyện định kỳ phải:

- Lấy dữ liệu đầu vào duy nhất từ `OrderPaid.v1` / ảnh chụp các đơn hàng đã thanh toán; không sử dụng đơn nháp hay đơn chưa thanh toán;
- Giới hạn phạm vi dữ liệu theo tenant, tuyệt đối không trộn lẫn ID người dùng/món giữa các tenant;
- Xử lý các đơn hàng vãng lai không có danh tính qua luồng tổng hợp luật kết hợp (aggregate association path), không tạo khách hàng giả mạo;
- Tạo mã ảnh chụp huấn luyện (snapshot ID) bất biến, phiên bản đặc trưng, phiên bản mã nguồn và watermark dữ liệu đầu vào;
- Huấn luyện mô hình ứng viên trong môi trường cô lập;
- Thẩm định các tiêu chí: số lượng tương tác tối thiểu, chỉ số Recall@K/NDCG@K theo cấu hình, độ bao phủ, độ tươi, kiểm tra rò rỉ dữ liệu và tập chia đánh giá (evaluation split);
- Chỉ kích hoạt mô hình nếu vượt qua 100% các cổng kiểm soát bắt buộc (hard gates);
- Giữ nguyên mô hình active cũ nếu quá trình huấn luyện hoặc thẩm định thất bại;
- Cơ chế thử lại có giới hạn, ghi nhận trạng thái lần chạy vào DLQ / phát cảnh báo và cho phép chạy lại trên cùng ảnh chụp dữ liệu.

### FR-REC-104 — Vòng đời mô hình ML (Model lifecycle)

Các trạng thái tối thiểu của mô hình: `CANDIDATE`, `VALIDATING`, `ACTIVE`, `RETIRED`, `REJECTED`, `FAILED`.

- Mỗi tenant chỉ có tối đa một mô hình ALS ở trạng thái `ACTIVE` cho mỗi dòng mô hình / chính sách phiên bản.
- Kích hoạt mô hình là thao tác cập nhật con trỏ lạc quan có kiểm tra phiên bản (optimistic/versioned pointer update), không xảy ra tranh chấp với thao tác rollback.
- Rollback chỉ được phép trỏ về một mô hình đã từng vượt qua thẩm định trước đó và bắt buộc phải có lý do kiểm toán (audit reason).
- Bắt buộc phải lưu trữ: tham chiếu artifact, checksum, khóa mã hóa, phiên bản code huấn luyện và các chỉ số đánh giá.

## 6. Yêu cầu chức năng P2 (Functional requirements P2)

### FR-REC-201 — Làm giàu dữ liệu khởi động nguội qua DeepSeek (DeepSeek cold-start enrichment)

- Được kích hoạt tự động khi Catalog tạo mới hoặc cập nhật metadata món ăn đủ điều kiện.
- Tác vụ chỉ gửi các metadata đã được làm sạch, tuyệt đối không gửi dữ liệu khách hàng, đơn hàng, thanh toán hay số điện thoại.
- Cấu trúc đầu ra tối thiểu: danh sách nhãn/đặc trưng, phiên bản prompt/mô hình, điểm tin cậy, nguồn gốc dữ liệu và thời điểm tạo.
- Thẩm định schema, danh mục từ khóa được phép, độ dài và chính sách an toàn trước khi lưu.
- Kết quả ban đầu ở trạng thái `CANDIDATE` và bắt buộc có sự phê duyệt của con người trước khi trở thành đặc trưng phục vụ production.
- Thiết lập thời gian chờ (timeout), giới hạn tần suất (rate-limit) và thử lại có giới hạn; sự cố từ bên ngoài không ảnh hưởng đến Catalog hay luồng gợi ý P0/P1.

### FR-REC-202 — Điểm mở rộng ngữ cảnh (Context extension point)

- Giao diện đặc trưng hỗ trợ: khung giờ kinh doanh (`businessHour`), ngày trong tuần (`dayOfWeek`), múi giờ chi nhánh và tích hợp thời tiết trong tương lai.
- Giai đoạn MVP chưa tích hợp với nhà cung cấp dữ liệu thời tiết.
- Trường hợp thiếu đặc trưng ngữ cảnh hoặc dữ liệu bị trễ, hệ thống không báo lỗi request mà bỏ qua đặc trưng đó hoặc dùng mô hình phi ngữ cảnh.

## 7. Yêu cầu về API (API requirements)

### 7.1 Bề mặt API công khai (Public API surface)

- `POST /api/v1/recommendations` — Lấy danh sách gợi ý giỏ hàng, bảo đảm tính lũy nghiệm theo khóa yêu cầu / phiên.
- `POST /api/v1/recommendations/{suggestionId}/shown` — Xác nhận gợi ý đã hiển thị trên màn hình.
- `POST /api/v1/recommendations/{suggestionId}/accept` — Xác nhận chấp thuận sau khi Order thêm dòng thành công.
- `GET /api/v1/recommendation-rules` — Quản lý / Chủ chuỗi tra cứu danh sách luật của tenant kèm bộ lọc và phân trang con trỏ (cursor).
- `POST /api/v1/recommendation-rules` — Quản lý / Chủ chuỗi tạo bản nháp (draft) luật mới.
- `PATCH /api/v1/recommendation-rules/{ruleId}` — Quản lý / Chủ chuỗi chỉnh sửa bản nháp kèm header `If-Match`.
- `POST /api/v1/recommendation-rules/{ruleId}/publish` — Chủ chuỗi công bố phiên bản luật chính thức.
- `POST /api/v1/recommendation-rules/{ruleId}/retire` — Chủ chuỗi ngừng áp dụng luật có kèm lý do.
- `GET /api/v1/recommendation-models/active` — Chủ chuỗi / Quản lý xem tình trạng sức khỏe và độ tươi của mô hình; không cho tải file artifact.

Mọi lệnh thay đổi trạng thái đều sử dụng envelope chuẩn, xác thực Cognito, truyền `X-Correlation-Id`, `X-Active-Branch-Id` cho các thao tác theo chi nhánh, `Idempotency-Key` và phân loại lỗi an toàn. Danh sách luật phân trang theo cursor, mặc định 20, tối đa 100 bản ghi.

### 7.2 Bề mặt API nội bộ và worker (Internal API and worker surface)

- `POST /internal/v1/recommendations/training-runs` — Bộ lập lịch đưa tác vụ huấn luyện vào hàng đợi hoặc chạy lại.
- `GET /internal/v1/recommendations/training-runs/{runId}` — Kiểm tra trạng thái tác vụ của worker / vận hành.
- `POST /internal/v1/recommendations/models/{modelId}/validate` — Gửi kết quả thẩm định chất lượng mô hình từ worker.
- `POST /internal/v1/recommendations/models/{modelId}/activate` — Kích hoạt mô hình do Chủ chuỗi phê duyệt.
- `POST /internal/v1/recommendations/projections/rebuild` — Yêu cầu tái dựng hình chiếu theo watermark nguồn được ủy quyền.
- `POST /internal/v1/recommendations/cold-start-jobs` — Đưa tác vụ DeepSeek bất đồng bộ vào hàng đợi.

Các endpoint nội bộ bắt buộc sử dụng workload identity/mTLS, ngữ cảnh tenant có chữ ký xác thực, cơ chế thử lại có giới hạn và khóa chạy lũy nghiệm.

### 7.3 Phân loại mã lỗi API (API behavior/error taxonomy)

Các mã lỗi máy đọc tối thiểu:

`unauthorized`, `forbidden`, `not_found`, `validation_error`, `cart_context_unavailable`, `suggestion_expired`, `candidate_not_in_session`, `candidate_unavailable`, `projection_stale`, `customer_feature_unavailable`, `model_unavailable`, `model_quality_gate_failed`, `rule_not_editable`, `rule_publish_forbidden`, `rule_version_conflict`, `idempotency_conflict`, `duplicate_request`, `interaction_duplicate`, `tenant_scope_mismatch`, `training_run_conflict`, `provider_rate_limited`, `provider_unavailable`, `service_unavailable`.

Quy tắc:

- Gợi ý P0 có thể trả về HTTP 200 với danh sách `items=[]` khi không tìm thấy ứng viên hợp lệ; đây là hành vi bình thường, không phải lỗi.
- Lệnh phân tích đo lường thất bại sau khi đã ghi cục bộ sẽ được thử lại bất đồng bộ.
- Hình chiếu bị trễ có thể trả về HTTP 200 với danh sách rỗng kèm metadata độ tươi, không trả danh sách ứng viên một cách mù quáng.
- Mô hình không khả dụng hoặc trượt cổng chất lượng sẽ tự động fallback về luật kết hợp P0 nếu có.

## 8. Yêu cầu về sự kiện và tích hợp (Event and integration requirements)

### 8.1 Các sự kiện tiêu thụ (Consumed events)

| Tên sự kiện | Dịch vụ phát (Producer) | Mục đích sử dụng |
|---|---|---|
| `CatalogItemCreated/Updated.v1` | Catalog | Hình chiếu món / định danh luật / kích hoạt cold-start |
| `CatalogItemAvailabilityChanged.v1` | Catalog | Tính đủ điều kiện theo chi nhánh và thực đơn |
| `InventoryStockChanged.v1` | Inventory | Hình chiếu trạng thái còn hàng / hết hàng |
| `OrderCreated.v1` | Order | Hình chiếu đơn/giỏ hàng và ngữ cảnh phân tích P2; không dùng làm tín hiệu ALS |
| `OrderPaid.v1` | Order | Tín hiệu tương tác / huấn luyện ALS chuẩn mực duy nhất |
| Sự kiện/API ngữ cảnh Loyalty | Loyalty | Trạng thái đủ điều kiện và phiên bản đặc trưng khách hàng dạng opaque |

Các consumer phải khử trùng lặp theo ID sự kiện + phiên bản nguồn, duy trì watermark nguồn, xử lý bản tin đến sai thứ tự và hỗ trợ phát lại / tái dựng dữ liệu.

### 8.2 Các sự kiện phát ra (Produced events)

Toàn bộ sự kiện sử dụng envelope chuẩn của nền tảng: `eventId`, `eventType`, `eventVersion`, `occurredAt`, `tenantId`, `branchId`, `aggregateType`, `aggregateId`, `aggregateVersion`, `correlationId`, `causationId`, `producer`, `payload`.

| Tên sự kiện | Điều kiện kích hoạt | Bên tiêu thụ chính (Consumers) |
|---|---|---|
| `SuggestionShown.v1` | Xác nhận hiển thị được chấp nhận | Reporting, Audit |
| `SuggestionAccepted.v1` | Ghi nhận thêm món thành công | Reporting, Audit |
| `RecommendationRulePublished.v1` | Chủ chuỗi công bố phiên bản luật | Reporting, Audit, Recommendation projection |
| `RecommendationRuleRetired.v1` | Chủ chuỗi ngừng áp dụng luật | Reporting, Audit |
| `RecommendationModelActivated.v1` | Ứng viên vượt qua các cổng kiểm soát chất lượng | Reporting, Audit, Recommendation API |
| `RecommendationModelRolledBack.v1` | Thao tác rollback có chủ đích | Reporting, Audit |
| `RecommendationTrainingRunCompleted.v1` | Lần chạy huấn luyện kết thúc | Reporting, Audit, vận hành |
| `ColdStartFeatureCandidateCreated.v1` | Đầu ra của DeepSeek được thẩm định là ứng viên | Catalog, vận hành, Audit |

### 8.3 Ranh giới giữa Order và Reporting

- `SuggestionAccepted` chỉ mang tính chất quy kết / phân tích đo lường; không thể thay thế cho sự kiện `OrderPaid.v1`.
- Lệnh thêm dòng của Order có thể mang theo các trường tùy chọn: `suggestionId`, `ruleId`, `modelVersion` và tham chiếu món ứng viên; Order chỉ lưu quy kết này vào sự kiện đơn hàng sau khi commit thành công.
- Recommendation có thể nhận diện chấp thuận qua lệnh gọi trực tiếp hoặc qua sự kiện thêm dòng của Order; các đường dẫn trùng nhau bắt buộc phải hội tụ về cùng một khóa nguồn duy nhất.
- Reporting tính toán tỷ lệ chuyển đổi Hiển thị → Chấp thuận → Thanh toán dựa trên cửa sổ quy kết và phiên bản rõ ràng, không join trực tiếp vào trạng thái giỏ hàng hiện tại vốn có thể bị biến đổi.

## 9. Yêu cầu dữ liệu và tính nhất quán (Data and consistency requirements)

Các thực thể cốt lõi:

- `association_rules`, `association_rule_versions`, `rule_antecedents`, `rule_candidates`;
- `item_availability_projection`, `item_catalog_projection`, `projection_checkpoints`;
- `suggestion_sessions`, `suggestion_candidates`, `suggestion_interactions`;
- `model_registry`, `training_runs`, `training_snapshots`, `model_metrics`;
- `customer_feature_keys` / tham chiếu tính đủ điều kiện dạng opaque (nếu Recommendation cần hình chiếu cục bộ);
- `cold_start_feature_candidates`, `outbox_messages`, `idempotency_records`, `inbox_events`.

Các bất biến bắt buộc:

1. Phiên bản công bố của luật mang tính bất biến; chỉ có duy nhất một phiên bản active được công bố cho mỗi phạm vi luật / tenant.
2. Vế điều kiện / kết quả của luật và toàn bộ khóa hình chiếu đều bắt buộc chứa thông tin sở hữu tenant.
3. Phiên gợi ý liên kết chặt chẽ bộ tứ: tenant + chi nhánh + đơn hàng + phiên bản giỏ hàng; không thể chấp thuận ứng viên ngoài phạm vi này.
4. Khóa nguồn của tương tác là duy nhất; gửi trùng lặp hiển thị/chấp thuận được xử lý lũy nghiệm.
5. Kích hoạt mô hình sử dụng kỹ thuật compare-and-swap / phiên bản; không để tồn tại hai mô hình active cùng lúc cho một dòng mô hình của tenant.
6. Ảnh chụp huấn luyện bắt buộc chứa ranh giới tenant, watermark nguồn và phiên bản code/feature.
7. Checkpoint của consumer hình chiếu chỉ được cập nhật sau khi nghiệp vụ và sự kiện đã commit thành công.
8. Tuyệt đối không lưu số điện thoại plaintext, dữ liệu tra cứu thô từ Loyalty hay PII thừa trong dữ liệu/log/sự kiện của Recommendation.
9. Toàn bộ ID liên service là dạng opaque; không tạo khóa ngoại hay câu lệnh query trực tiếp xuyên database.

## 10. Yêu cầu phi chức năng (Non-functional requirements)

| Phân loại | Yêu cầu | Chỉ số mục tiêu |
|---|---|---|
| Độ trễ (Latency) | API gợi ý P0 (không tính các bên ngoài) | p95 ≤ 500 ms dưới tải thử nghiệm nền tảng |
| Tính sẵn sàng (Availability) | API P0 khi ML / huấn luyện / bên ngoài gặp sự cố | Luật kết hợp tiếp tục phục vụ; danh sách rỗng là an toàn |
| Độ tươi hình chiếu | Trạng thái khả dụng của Catalog/Inventory | Độ trễ mục tiêu ≤ 60s; quá hạn TTL sẽ chặn ứng viên |
| Tính tất định (Determinism) | Cùng tenant / luật / giỏ hàng / phiên bản / mô hình | Trả về đúng thứ tự ứng viên và điểm số trong sai số số học |
| Huấn luyện (Training) | Hoàn thành huấn luyện ALS đêm | Hoàn tất trước khung giờ kinh doanh; cảnh báo nếu trễ |
| An toàn mô hình | Kích hoạt mô hình ứng viên | Vượt qua 100% cổng chất lượng / bảo mật / cô lập dữ liệu |
| Quy mô (Scale) | Baseline nền tảng POS | 100 chi nhánh, 100k đơn hàng/ngày; giới hạn tập ứng viên/top-K |
| Bảo mật (Security) | Xác thực / tenant / chi nhánh / IDOR | Cognito + workload identity; bảo vệ đa tầng ở service/repo |
| Quyền riêng tư (Privacy) | Dữ liệu khách hàng | Không có SĐT plaintext; dùng feature key opaque; có chính sách xóa |
| Độ tin cậy (Reliability) | Sự kiện và tác vụ nền | Outbox/inbox, at-least-once, thử lại, DLQ, replay/rebuild |
| Khả năng quan sát | API / hình chiếu / mô hình | Structured logs / metrics / traces; không rò rỉ dữ liệu nhạy cảm |
| Lưu trữ dữ liệu | Bằng chứng gợi ý / audit / huấn luyện | Tối thiểu bằng thời hạn audit; cấu hình lưu trữ mô hình/sự kiện |
| Chi phí (Cost) | Tích hợp DeepSeek P2 | Giới hạn ngân sách/tần suất bất đồng bộ; không gọi không kiểm soát |

## 11. Yêu cầu bảo mật và khả năng quan sát (Security and observability requirements)

### Bảo mật (Security)

- Public API yêu cầu Cognito JWT và kiểm tra phân quyền tenant/chi nhánh hiện tại.
- Công bố luật và kích hoạt mô hình bắt buộc có quyền của Chủ chuỗi (Owner) cùng lý do/lưu vết kiểm toán.
- API sự kiện/tác vụ nội bộ yêu cầu workload identity/mTLS và ngữ cảnh tenant có chữ ký.
- Câu lệnh truy vấn ở repository luôn luôn chứa điều kiện lọc tenant; vị từ chi nhánh áp dụng cho hình chiếu/phiên theo chi nhánh.
- Khóa đặc trưng khách hàng là dạng opaque, Recommendation không thể dịch ngược và không hiển thị ra giao diện UI ngoài các tham chiếu cho phép.
- Payload gửi sang DeepSeek chỉ chứa metadata Catalog đã khử độc; dữ liệu trả về từ nhà cung cấp bị coi là dữ liệu chưa đáng tin cậy.

### Chỉ số, nhật ký và truy vết (Metrics, logs, traces)

Các chỉ số (Metrics):

- `recommendation_request_total{result,source}`;
- `recommendation_latency_ms`, `candidate_filter_total{reason}`;
- `projection_lag_seconds`, `projection_stale_total{source}`;
- `suggestion_shown_total`, `suggestion_accepted_total`, độ trễ chuyển đổi;
- `als_training_run_total{state}`, thời gian chạy, số dòng dữ liệu, chỉ số chất lượng;
- `model_activation_total{result}`, số lần rollback, tuổi của mô hình active;
- `cold_start_job_total{state}`, độ trễ bên ngoài / lỗi / ước tính chi phí;
- Bộ đếm thử lại / DLQ / replay của outbox và inbox.

Structured logs bắt buộc chứa request/correlation ID, tenant/branch, order/suggestion/rule/model/run ID và mã kết quả. Tuyệt đối không log số điện thoại, đặc trưng thô của khách hàng, prompt chứa PII, nội dung artifact mô hình, token xác thực hay secret của nhà cung cấp.

## 12. Các trường hợp biên và chính sách xử lý lỗi (Edge cases and failure policy)

- Giỏ hàng rỗng: Trả về `items=[]`, không báo lỗi đánh giá luật.
- Ứng viên xuất hiện nhiều lần trong các luật khác nhau: Gộp theo ứng viên và gán cho luật có điểm số tốt nhất một cách tất định.
- Ứng viên đã có trong giỏ hàng dưới biến thể/topping khác: Chính sách định danh chuẩn tắc của Catalog quyết định loại trừ; không upsell trùng món.
- Sự kiện khả dụng bị trùng lặp hoặc đến sai thứ tự: Bỏ qua phiên bản cũ, bảo đảm an toàn khi phát lại.
- Hình chiếu bị thiếu hoặc quá hạn: Loại bỏ các ứng viên bị ảnh hưởng; nếu toàn bộ ứng viên không an toàn thì trả về rỗng kèm metadata độ tươi.
- Phiên bản đơn hàng bị thay đổi sau khi gợi ý: Lệnh chấp thuận bị từ chối / đánh dấu đã cũ; POS phải xin phiên gợi ý mới.
- Thêm dòng thất bại sau khi click: Không phát `SuggestionAccepted`; sự kiện đã hiển thị vẫn giữ nguyên giá trị đo lường.
- Gửi trùng hiển thị/chấp thuận: Xử lý lũy nghiệm, trả lại kết quả cũ mà không thực hiện lại thao tác.
- Khách hàng không đủ lịch sử tương tác ALS: Tự động fallback về luật kết hợp kèm lý do `COLD_START`.
- Mô hình active bị thiếu hoặc trượt cổng chất lượng: Tự động fallback về luật kết hợp; không trả mã lỗi 5xx đối với trường hợp cold start thông thường.
- Dữ liệu nguồn huấn luyện có các sự kiện `OrderPaid` trùng/sai thứ tự: Lọc trùng ảnh chụp theo sự kiện / mã đơn hàng nguồn.
- Các lần chạy huấn luyện bị chồng lấn: Tiến trình đầu tiên chiếm được khóa thuê (lease) của tenant/dòng mô hình sẽ thực thi; tiến trình trùng lặp trả về thao tác hiện có.
- Sai lệch mã băm checksum của artifact mô hình: Từ chối kích hoạt, giữ nguyên mô hình active cũ và gửi cảnh báo.
- DeepSeek bị quá thời gian chờ hoặc schema không an toàn: Ứng viên bị từ chối / đưa vào thử lại; không làm ảnh hưởng đến dữ liệu Catalog.
- ID / khóa khách hàng sai tenant: Trả về lỗi forbidden / tenant_scope_mismatch mà không làm lộ sự tồn tại của tài nguyên.
- Reporting / Audit tạm thời không khả dụng: Outbox cục bộ lưu giữ sự kiện; phản hồi gợi ý cho quầy POS không bị chặn.

## 13. Tiêu chí nghiệm thu (Acceptance criteria)

### P0

1. Với một giỏ hàng thỏa mãn luật kết hợp toàn tenant, hệ thống trả về danh sách các món kết quả hợp lệ theo thứ tự tất định.
2. Các món đã có trong giỏ, ngừng bán, sai chi nhánh hoặc hết hàng/hình chiếu quá hạn tuyệt đối không xuất hiện.
3. Khi không có luật hợp lệ, hệ thống trả về HTTP 200 với danh sách rỗng và không áp dụng các cơ chế phỏng đoán tự ý.
4. Phản hồi gợi ý chứa đầy đủ: `suggestionId`, phiên bản giỏ hàng/đơn hàng, phiên bản luật, ảnh chụp ứng viên và metadata độ tươi.
5. Xác nhận hiển thị tạo ra duy nhất một sự kiện `SuggestionShown.v1` cho mỗi khóa tương tác.
6. Thêm dòng thất bại không sinh sự kiện `SuggestionAccepted.v1`; thêm dòng thành công tạo ra đúng một sự kiện chấp thuận có tính lũy nghiệm.
7. Quản lý có quyền tạo/sửa bản nháp; duy nhất Chủ chuỗi có quyền công bố / ngừng áp dụng luật có kèm lý do và lưu vết kiểm toán.

### P1

1. Số điện thoại khách hàng được phân giải qua Loyalty; Recommendation hoàn toàn không nhận số điện thoại plaintext.
2. Mô hình ALS sử dụng dữ liệu đơn hàng đã thanh toán toàn tenant, lọc ứng viên theo chi nhánh hiện tại và fallback về P0 khi thiếu tín hiệu hoặc mô hình lỗi.
3. Huấn luyện đêm thất bại hoặc trượt cổng chất lượng vẫn bảo toàn mô hình active trước đó.
4. Kích hoạt và rollback mô hình có đánh phiên bản, lưu vết kiểm toán và có tính lũy nghiệm.
5. Các sự kiện OrderPaid, Catalog, Inventory và tương tác bị trùng lặp hoặc đến sai thứ tự không làm hỏng hình chiếu hay sai lệch số liệu.

### P2

1. Các tác vụ DeepSeek chạy hoàn toàn bất đồng bộ, dữ liệu được làm sạch, kiểm tra schema và có sự xét duyệt của con người.
2. Sự cố từ DeepSeek hay dịch vụ thời tiết không làm suy giảm tính sẵn sàng hoặc độ trễ của luồng P0/P1.

## 14. Bảng đối chiếu truy xuất nguồn gốc (Traceability)

| Tính năng | Mục trong Vision | Bao phủ trong Yêu cầu |
|---|---|---|
| Gợi ý luật kết hợp giỏ hàng | §4 P0 | FR-REC-001, quy tắc xếp hạng/lọc, bề mặt API |
| Lọc giỏ hàng và tính khả dụng | §4 P0, §7 | FR-REC-003, các bất biến hình chiếu, tiêu chí nghiệm thu |
| Nhật ký hiển thị / chấp thuận | §4 P0 | FR-REC-004/005, danh mục sự kiện, khử trùng lặp tương tác |
| Cá nhân hóa bằng ALS | §4 P1 | FR-REC-101/102, hợp đồng đặc trưng và quyền riêng tư |
| Huấn luyện ban đêm | §4 P1 | FR-REC-103/104, vòng đời mô hình / các cổng chất lượng |
| Khởi động nguội với DeepSeek | §4 P2 | FR-REC-201, kiểm soát an toàn cho adapter bất đồng bộ |
| Điểm mở rộng ngữ cảnh | §4 P2 | FR-REC-202, chính sách không tích hợp bên thứ ba trong MVP |
| Tenant / chi nhánh / quyền riêng tư | §5–7 | Yêu cầu bảo mật / NFR / các bất biến repository |

## 15. Đầu vào cho giai đoạn tiếp theo (Next phase inputs)

- Tài liệu Architecture phải lựa chọn: cơ chế vận chuyển hình chiếu / read model, ranh giới các cấu phần trong rule engine, giải pháp lưu trữ/đăng ký artifact mô hình, worker huấn luyện / kích hoạt và phương án phục hồi lỗi.
- Tài liệu Contracts phải định nghĩa: schema request/response/error của OpenAPI, bằng chứng hiển thị/chấp thuận, API vòng đời luật, hợp đồng đặc trưng opaque từ Loyalty và JSON Schema cho các sự kiện.
- Kỹ năng Thiết kế cơ sở dữ liệu (Database Design) phải định nghĩa: chữ ký luật, định danh ứng viên, checkpoint hình chiếu, tương tác bất biến, ảnh chụp huấn luyện / model registry và các chỉ mục / chính sách lưu trữ.
- Các sơ đồ tuần tự (Sequence diagrams) phải bao quát: yêu cầu/lọc giỏ hàng, hiển thị/chấp thuận, nhánh fallback của ALS, huấn luyện/kích hoạt/rollback ban đêm và ứng xử khi hình chiếu bị trễ.

## Cổng phê duyệt (Approval gate)

- **Trạng thái hiện tại:** Approved with warnings (Đã phê duyệt với các cảnh báo)
- **Hành động tiếp theo:** Đánh giá Spec Sentinel, sau đó trình người dùng phê duyệt.
