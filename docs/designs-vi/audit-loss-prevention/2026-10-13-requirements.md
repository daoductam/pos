Started at: 2026/10/13 00:00:00
Finished at: 2026/10/13 00:00:00
Total time: 0 minutes
---

# Module 11 — Giám Sát & Chống Thất Thoát: Yêu Cầu Kỹ Thuật (Requirements)

**Ngày:** 2026-10-13  
**Trạng thái:** Đã phê duyệt kèm cảnh báo — người dùng đã phê duyệt sau khi đánh giá Requirements  
**Tầm nhìn:** [Audit & Loss Prevention Vision](file:///d:/vibe-code/pos/docs/designs-vi/audit-loss-prevention/2026-10-13-vision.md)  
**Đánh giá tầm nhìn:** [Audit & Loss Prevention Vision Review](file:///d:/vibe-code/pos/docs/designs-vi/audit-loss-prevention/2026-10-13-vision-review.md)

## 1. Mục Đích và Ranh Giới (Purpose & Boundaries)

Audit & Loss Prevention là dịch vụ kiểm toán tập trung, dạng chỉ ghi thêm (append-only) và hoạt động hướng sự kiện (event-driven). Service này tiếp nhận các dữ kiện bảo mật/kiểm toán từ các service nghiệp vụ, lưu trữ bằng chứng đã được làm sạch dữ liệu nhạy cảm (redacted), cung cấp khả năng truy vấn theo tenant/chi nhánh và phát hiện bất thường, tạo hồ sơ vụ việc (finding/case) ở phân hệ P2.

Audit không sở hữu và không chỉnh sửa trạng thái của Order, Payment, Shift, Identity, thiết bị POS hay Promotion. Service nguồn bắt buộc phải commit transaction thay đổi trạng thái nghiệp vụ cùng Transactional Outbox trước khi Audit có thể ghi nhận dữ kiện. Sự kiện kiểm toán không thay thế sự kiện nghiệp vụ và tuyệt đối không được sử dụng làm nguồn doanh thu hay kế toán.

### Trong phạm vi (In scope)

- Kiểm toán P0 cho các hành động: hủy món, giảm giá qua promotion/voucher và giảm giá/ghi đè thủ công, mở két tiền.
- Phong bì sự kiện chung `AuditRecorded.v1`, inbox/dedupe, kho lưu trữ sự kiện append-only, projection truy vấn, quản lý watermark/DLQ và checkpoint/bản tóm tắt băm toàn vẹn (hash digest).
- Dòng thời gian/tìm kiếm kiểm toán với phạm vi toàn tenant cho Owner và phạm vi chi nhánh được phân công cho Manager.
- Thời gian lưu trữ tối thiểu 7 năm, tham chiếu lưu trữ pháp lý (legal hold), xuất/lưu trữ/phát lại dữ liệu hạn chế.
- Quy tắc phát hiện bất thường P2 có thể giải thích được kết hợp với đường cơ sở thống kê theo nhân viên/chi nhánh/thời gian và quy trình xử lý vụ việc.

### Ngoài phạm vi (Out of scope)

- Tự động khóa tài khoản, chặn thao tác, từ chối thanh toán hay xử lý kỷ luật/bảng lương.
- Mô hình Machine Learning phức tạp, hồ sơ sinh trắc học, giám sát camera/GPS/bàn phím.
- Lưu trữ raw JWT/PIN, thông tin thanh toán nhạy cảm, số điện thoại khách hàng hoặc toàn bộ raw payload request/response.
- Truy vấn tương tác trực tiếp toàn bộ 7 năm; dữ liệu lịch sử cũ phải thông qua cơ chế xuất file bất đồng bộ có kiểm soát.
- Thay thế nguồn sự thật của Reporting, Order, Payment, Shift hay Identity.

## 2. Tác Nhân và Phân Quyền (Actors & Authorization)

| Tác nhân | Quyền đề xuất | Phạm vi | Các hành động được phép |
|---|---|---|---|
| Chủ cửa hàng (Owner) | `audit:read`, `loss-case:read`, `loss-case:resolve`, `loss-case:dismiss`, quyền hạn chế `audit:operate` | Toàn tenant hoặc tập con chi nhánh thuộc tenant | Đọc audit/phát hiện, xác nhận/điều tra/giải quyết/bác bỏ vụ việc, xuất/phát lại dữ liệu theo chính sách |
| Quản lý (Manager) | `audit:read`, `loss-case:read`, `loss-case:investigate` | Các chi nhánh được phân công còn quan hệ thành viên hợp lệ | Đọc, xác nhận (acknowledge), bắt đầu/cập nhật điều tra (investigate) trong chi nhánh |
| Thu ngân / Bếp | Không có quyền vào Audit console trong MVP | — | Chỉ xuất hiện dưới dạng snapshot tác nhân lịch sử / thu ngân thao tác thực tế |
| Worker / Consumer của Audit | Workload identity | Tenant/chi nhánh trong phong bì sự kiện | Thu nhận, project, tính digest, đánh giá đường cơ sở/quy tắc |
| Worker phát lại / vận hành | Workload hạn chế + `audit:operate` | Khoảng thời gian / tenant / chi nhánh chỉ định rõ | Phát lại, rebuild, xuất dữ liệu, thao tác lưu trữ/lưu trữ pháp lý |

Quy tắc:

1. Identity là thẩm quyền phân quyền trực tuyến duy nhất; quyền hạn, tenant, chi nhánh và phiên bản membership không do client tự gửi.
2. Quyền toàn tenant của Owner không được mặc định trao cho Manager; bộ lọc/cursor/ID tài nguyên của Manager không thể mở rộng tập chi nhánh được giao.
3. Phân quyền luôn được kiểm tra trước khi đọc cache và được lặp lại tại repository của Audit cho mọi tham chiếu event/finding/case/export.
4. Owner có thể xử lý vụ việc trên toàn tenant hoặc tập con chi nhánh; Manager không có quyền giải quyết (resolve) hay bác bỏ (dismiss) vụ việc.
5. Việc bị từ chối truy cập và các tham chiếu đáng ngờ xuyên tenant/chi nhánh cũng tự động sinh sự kiện kiểm toán bảo mật đã làm sạch dữ liệu nhạy cảm.

## 3. Định Nghĩa và Danh Mục Phân Loại Sự Kiện (Taxonomy)

### 3.1 Phong bì sự kiện kiểm toán chung (Common Audit Event Envelope)

Mọi producer đều sử dụng chung một phong bì chuẩn:

| Trường | Bắt buộc | Quy tắc |
|---|---:|---|
| `eventId` | có | Chuỗi UUID v7 ổn định; dùng làm khóa dedupe xuyên suốt các lần retry/phát lại |
| `eventType`/`eventVersion` | có | Schema sự kiện / hành động có phiên bản |
| `occurredAt` | có | Thời điểm UTC phát sinh sự kiện nghiệp vụ nguồn |
| `tenantId` | có | Tenant nguồn; không bao giờ null đối với các dữ kiện của tenant |
| `branchId` | có điều kiện | Bắt buộc đối với các hành động POS theo phạm vi chi nhánh |
| `aggregateType`/`aggregateId` | có | Order, Payment, Shift, PosDevice, Membership, AuditCase, v.v. |
| `aggregateVersion` | có | Phiên bản trạng thái nguồn khi có sẵn |
| `sourceEventId`/`sourceVersion` | có | Phả hệ outbox/nguồn của producer phục vụ việc dedupe/phát lại |
| `actor` | có điều kiện | Snapshot tác nhân con người/workload; chỉ vắng mặt đối với hành động hệ thống được phê duyệt |
| `effectiveOperator` | có điều kiện | Thu ngân hiện đang thao tác trên POS/thiết bị khi khác biệt với tác nhân đã xác thực |
| `deviceId`/`shiftId` | có điều kiện | Bắt buộc khi hành động diễn ra tại ranh giới POS / két tiền / ca làm việc |
| `action`/`resourceType`/`resourceId` | có | Danh mục được phép (allowlisted) và tham chiếu tài nguyên mờ |
| `reason` | có điều kiện | Bắt buộc đối với hủy món, giảm giá/ghi đè thủ công và các bước chuyển trạng thái vụ việc |
| `beforeSummary`/`afterSummary` | có điều kiện | Tóm tắt JSON có giới hạn, thuộc danh mục cho phép và đã được che giấu dữ liệu nhạy cảm |
| `outcome` | có | `SUCCEEDED`, `REJECTED`, `FAILED`, `NO_OP` |
| `correlationId`/`causationId` | có / khuyến nghị | Truy vết phân tán và mối quan hệ nhân quả |
| `redactionVersion` | có | Phiên bản chính sách/schema làm sạch dữ liệu áp dụng trước khi phát hành |

Audit chỉ lưu trữ phong bì sự kiện và tham chiếu payload an toàn, tuyệt đối không lưu raw JWT, mã PIN, secret, chữ ký cổng thanh toán, số điện thoại đầy đủ, thông tin thẻ hay body request không giới hạn.

### 3.2 Danh mục hành động P0 (P0 Action Taxonomy)

| Sự kiện | Producer / Nguồn sự thật | Bằng chứng nghiệp vụ bắt buộc |
|---|---|---|
| `OrderLineCancelled.v1` / `OrderCancelled.v1` | Order | order/line, trạng thái trước/sau, tác nhân/thu ngân thao tác, lý do, phiên bản đơn hàng, chi nhánh, kết quả |
| `DiscountApplied.v1` | Ranh giới Order/Promotion | nguồn giảm giá `PROMOTION`, `VOUCHER` hoặc `MANUAL_OVERRIDE`; order/line, phiên bản khuyến mãi/quy tắc (nếu có), số tiền trước/sau, phân bổ, tác nhân, lý do ghi đè thủ công, kết quả |
| `CashDrawerOpened.v1` | Ranh giới Thiết bị POS / Shift | thiết bị, ca làm việc, nguồn kích hoạt `SALE`, `NO_SALE`, `REFUND`, `OPENING`, `MAINTENANCE` hoặc `UNKNOWN`, tác nhân/thu ngân thao tác, thời điểm yêu cầu/kết quả, kết quả |
| `AuthorizationDenied.v1` | Ranh giới Gateway / Service | quyết định quyền/tài nguyên/phạm vi, tham chiếu tác nhân/workload, mã lý do an toàn, kết quả; không chứa secret/JWT |
| `AuditCaseActionRecorded.v1` | Audit | vụ việc, thao tác chuyển đổi, tác nhân, lý do, trạng thái cũ/mới của vụ việc, tham chiếu bằng chứng/phiên bản |

Nền tảng gốc có thể tiếp tục định tuyến sự kiện `AuditRecorded.v1`; tài liệu Yêu cầu cần xác định rõ các sự kiện chuyên biệt này là sự kiện nghiệp vụ nguồn do Audit tiêu thụ hay là sự kiện kiểm toán đã chuẩn hóa do chính service nguồn phát hành. Schema máy đọc được chuẩn tắc phải ngăn chặn việc phát sinh hai bản ghi kiểm toán cho cùng một thay đổi trạng thái nguồn.

### 3.3 Tác nhân và thu ngân thao tác thực tế (Actor vs Effective Operator)

- `actor` là người dùng hoặc workload đã xác thực gửi yêu cầu / ủy quyền thao tác.
- `effectiveOperator` là thu ngân / nhân viên đang trực tiếp thao tác POS khi có cơ chế đổi ca nhanh hoặc ngữ cảnh thiết bị làm cho người này khác với người đã đăng nhập.
- Việc hiển thị lịch sử sử dụng snapshot nhãn an toàn/trạng thái bất biến; việc tra cứu Identity hiện tại không bao giờ được ghi đè lịch sử quá khứ.
- Nếu dữ liệu ghi nhận bị thiếu hoặc không hợp lệ, Audit sẽ lưu mã chất lượng `UNKNOWN` và kích hoạt telemetry cảnh báo chất lượng; tuyệt đối không tự ý đoán định từ danh sách thành viên hiện tại.

## 4. Yêu Cầu Chức Năng (Functional Requirements)

### 4.1 Thu nhận và phát hành sự kiện P0

| ID | Mức ưu tiên | Yêu cầu | Bằng chứng nghiệm thu |
|---|---|---|---|
| AUD-FR-001 | P0 | Service nguồn phát hành dữ kiện kiểm toán trong cùng transaction/outbox với thao tác hủy món, giảm giá/ghi đè hoặc mở két đã commit thành công. | Fixture commit/retry chứng minh không có commit tại nguồn nào diễn ra mà thiếu bản ghi outbox bền vững; thao tác thất bại chỉ phát bằng chứng rejected/failed khi chính sách yêu cầu. |
| AUD-FR-002 | P0 | Consumer của Audit chấp nhận phân phối at-least-once qua EventBridge/SQS và lưu trạng thái dedupe trong inbox trước khi gửi ACK. | ID sự kiện / khóa nguồn trùng lặp chỉ tạo ra đúng một bản ghi bằng chứng và một tác động projection duy nhất. |
| AUD-FR-003 | P0 | Audit kiểm tra hợp lệ phạm vi tenant/chi nhánh, loại/phiên bản sự kiện, các trường bắt buộc, lý do/tóm tắt có chặn độ dài và chính sách làm sạch dữ liệu. | Phiên bản lạ/không hợp lệ chuyển sang retry/DLQ kèm lỗi an toàn; các trường nhạy cảm thô bị từ chối hoặc loại bỏ trước khi lưu trữ. |
| AUD-FR-004 | P0 | Audit bảo toàn ID sự kiện nguồn, phiên bản nguồn, phiên bản aggregate, correlation/causation và định danh producer phục vụ phát lại. | Việc phát lại giữ nguyên phả hệ gốc và không tạo ra bằng chứng nghiệp vụ trùng lặp. |
| AUD-FR-005 | P0 | Audit công khai watermark nguồn/projection, độ hoàn tất và sức khỏe DLQ mà không để lộ payload hàng đợi thô. | Producer bị thiếu hoặc hàng đợi bị trễ được hiển thị dưới dạng `PARTIAL`/`STALE`, không được coi là không có hoạt động. |
| AUD-FR-006 | P0 | Việc lưu trữ của Audit là dạng append-only đối với sự kiện lịch sử; các điều chỉnh phải là sự kiện có phiên bản mới hoặc chú thích toàn vẹn, không bao giờ sửa tại chỗ. | Thao tác cập nhật/xóa trực tiếp bị từ chối; điều chỉnh tạo ra sự kiện liên kết mới và dấu vết kiểm toán tương ứng. |

### 4.2 Kiểm toán hủy món P0 (Cancel Item)

| ID | Yêu cầu |
|---|---|
| AUD-FR-010 | Mọi thao tác hủy dòng món thành công đều ghi nhận tham chiếu đơn hàng/dòng món, chi nhánh, tác nhân/thu ngân thao tác, lý do, tóm tắt trạng thái trước/sau, phiên bản đơn hàng, kết quả và correlation. |
| AUD-FR-011 | Thao tác hủy toàn bộ đơn hàng sau khi dòng món hoạt động cuối cùng bị hủy phải phân biệt rõ với việc hủy từng dòng món riêng lẻ. |
| AUD-FR-012 | Thao tác hủy bị từ chối có thể được ghi nhận là `REJECTED`/`NO_OP` kèm mã lý do an toàn nếu được cấu hình, nhưng không bao giờ xuất hiện như một thay đổi nghiệp vụ thành công. |
| AUD-FR-013 | Việc phát lại các sự kiện `OrderLineCancelled`/`OrderCancelled` không tạo ra bằng chứng trùng lặp hay số lượng bất thường bị nhân đôi. |

### 4.3 Kiểm toán giảm giá / ghi đè P0 (Discount/Override)

| ID | Yêu cầu |
|---|---|
| AUD-FR-020 | Kiểm toán cả việc áp dụng giảm giá qua khuyến mãi/voucher và việc giảm giá/ghi đè thủ công; trường `discountSource` phân biệt rõ `PROMOTION`, `VOUCHER`, `MANUAL_OVERRIDE`. |
| AUD-FR-021 | Giảm giá/ghi đè thủ công bắt buộc có lý do đã cắt tỉa khoảng trắng, tác nhân/thu ngân thao tác, số tiền trước/sau và kết quả; văn bản lý do tự do được giới hạn độ dài và làm sạch PII. |
| AUD-FR-022 | Sự kiện khuyến mãi/voucher bảo toàn phiên bản khuyến mãi/quy tắc/voucher bất biến và tham chiếu phân bổ; Audit không bao giờ đánh giá lại quy tắc hiện tại. |
| AUD-FR-023 | Sự kiện giảm giá hỗ trợ phân bổ từng phần theo dòng, phân bổ ở cấp đơn hàng và trạng thái chất lượng `UNALLOCATED`/`PARTIAL` mà không tự ý gán thêm tác nhân hay số tiền giả định. |
| AUD-FR-024 | Các lần áp dụng giảm giá thất bại hoặc bị từ chối không xuất hiện như giảm giá thành công; nếu được lưu lại, chúng mang mã lý do an toàn và không chứa payload nhạy cảm. |

### 4.4 Kiểm toán mở két tiền P0 (Cash Drawer)

| ID | Yêu cầu |
|---|---|
| AUD-FR-030 | Mọi thao tác mở két thành công đều ghi nhận thiết bị, chi nhánh, ca làm việc, tác nhân/thu ngân thao tác, nguồn kích hoạt, thời điểm yêu cầu/kết quả, kết quả và correlation. |
| AUD-FR-031 | Việc mở két thủ công / không bán hàng (no-sale) phải phân biệt rõ với các kích hoạt bán hàng/hoàn tiền/mở ca/bảo trì. Kích hoạt không xác định là giá trị tường minh và phát cảnh báo chất lượng dữ liệu. |
| AUD-FR-032 | Lệnh thiết bị trùng lặp hoặc retry không gây tính trùng một thao tác mở két vật lý khi đã có khóa lệnh/kết quả ổn định. |
| AUD-FR-033 | Nhật ký mở két vẫn được ghi nhận ngay cả khi đường dẫn truy vấn Payment/Order gặp sự cố; outbox/hàng đợi của producer giữ bằng chứng để retry. |

### 4.5 Truy vấn và dòng thời gian kiểm toán

| ID | Mức ưu tiên | Yêu cầu |
|---|---|---|
| AUD-FR-040 | P0 | Owner/Manager có thể truy vấn dòng thời gian kiểm toán bất biến theo khoảng thời gian có chặn, chi nhánh, tác nhân/thu ngân thao tác, hành động, tài nguyên, kết quả, thiết bị, ca làm việc và correlation ID. |
| AUD-FR-041 | P0 | Truy vấn tương tác mặc định là 30 ngày và tối đa là 90 ngày; mọi phản hồi đều trả về `dataQuality`, watermark/lastUpdatedAt và phạm vi được cấp quyền. |
| AUD-FR-042 | P0 | Dữ liệu lịch sử cũ hơn 90 ngày cho đến 7 năm được phục vụ thông qua cơ chế xuất file bất đồng bộ có kiểm soát, không qua truy vấn đồng bộ không giới hạn. |
| AUD-FR-043 | P0 | API danh sách sử dụng phân trang con trỏ (cursor) ổn định gắn liền với hash bộ lọc/phạm vi/phiên bản phân quyền chuẩn hóa; kích thước trang tối đa có chặn trên. |
| AUD-FR-044 | P0 | Xem chi tiết sự kiện luôn kiểm tra lại quyền tenant/chi nhánh và trả về ngữ nghĩa not-found/forbidden an toàn mà không làm lộ sự tồn tại của tài nguyên ngoại lai. |
| AUD-FR-045 | P0 | UI/API phân biệt rõ các trạng thái `FRESH`, `STALE`, `PARTIAL`, `UNAVAILABLE` và `NO_DATA`; việc vắng mặt dữ liệu do thiếu nguồn không bao giờ được thể hiện thành hoạt động sạch không có vi phạm. |
| AUD-FR-046 | P0 | Truy vấn kiểm toán không bao giờ trả về payload thô, JWT, mã PIN, SĐT khách hàng, bí mật thanh toán, chữ ký provider hay ghi chú đơn hàng không cần thiết. |

### 4.6 Tính toàn vẹn, lưu trữ và vận hành

| ID | Mức ưu tiên | Yêu cầu |
|---|---|---|
| AUD-FR-050 | P0 | Các dòng sự kiện lịch sử được bảo vệ bởi quyền hạn chỉ ghi thêm (append-only) ở cấp DB và chính sách vận hành bất biến. |
| AUD-FR-051 | P0 | Audit tạo bản tóm tắt băm (hash digest) / checkpoint theo lô trên các ID/nội dung sự kiện chuẩn tắc, lưu trữ thuật toán/phiên bản/thời gian/cửa sổ và xác minh qua tác vụ toàn vẹn hạn chế. |
| AUD-FR-052 | P0 | Sự không khớp tính toàn vẹn (integrity mismatch) sẽ tạo phát hiện bảo mật/toàn vẹn, phát cảnh báo và sinh sự kiện kiểm toán; tuyệt đối không âm thầm sửa dữ liệu. |
| AUD-FR-053 | P0 | Thời gian lưu trữ tối thiểu cho bằng chứng kiểm toán/bảo mật là 7 năm; lệnh lưu trữ pháp lý (legal hold) sẽ đình chỉ việc xóa/lưu trữ dài hạn đối với phạm vi/phân loại dữ liệu được chỉ định. |
| AUD-FR-054 | P0 | Việc lưu trữ dài hạn/xuất file được mã hóa, kiểm soát truy cập, có giới hạn, được làm sạch dữ liệu và bản thân hành động đó được kiểm toán; xuất file là tác vụ bất đồng bộ có trạng thái. |
| AUD-FR-055 | P0 | Việc phát lại/rebuild yêu cầu quyền hạn chế `audit:operate`, chỉ định rõ tenant/chi nhánh/khoảng thời gian, có tính lũy kế (idempotent) và bảo toàn correlation gốc. |
| AUD-FR-056 | P0 | Mọi thao tác lưu trữ, lưu trữ pháp lý, phát lại và kiểm tra toàn vẹn đều phát sinh dữ kiện kiểm toán bất biến của vận hành viên. |

### 4.7 Phát hiện bất thường P2 (Anomaly Detection)

| ID | Mức ưu tiên | Yêu cầu |
|---|---|---|
| AUD-FR-060 | P2 | Worker tiêu thụ các dữ kiện kiểm toán/đơn hàng/thanh toán/ca làm việc đã được phê duyệt; worker không thể thay đổi trạng thái nghiệp vụ nguồn hay ủy quyền giao dịch. |
| AUD-FR-061 | P2 | Các định nghĩa quy tắc có phiên bản, theo phạm vi tenant/chi nhánh, có thể kiểm toán và cấu hình được mà không làm thay đổi cách diễn giải phát hiện trong lịch sử. |
| AUD-FR-062 | P2 | Danh mục quy tắc ban đầu bao gồm: tăng đột biến số lượng/tiền hủy món, tăng đột biến số lượng/tiền giảm giá, tần suất mở két bất thường / mở két ngoài ca, và các hành động lặp đi lặp lại trên cùng đơn hàng/ca. |
| AUD-FR-063 | P2 | Các chiều của đường cơ sở bao gồm nhân viên/thu ngân thao tác, chi nhánh và khung thời gian cục bộ; việc đánh giá quy tắc áp dụng kích thước mẫu tối thiểu và chính sách khởi động (warm-up). |
| AUD-FR-064 | P2 | Nhân viên/chi nhánh/ca mới hoặc khi chưa đủ dữ liệu sẽ trả về `INSUFFICIENT_BASELINE`, không tạo phát hiện bất thường với độ tin cậy cao. |
| AUD-FR-065 | P2 | Mỗi phát hiện chứa khóa phát hiện đơn định, quy tắc/phiên bản, danh sách ID sự kiện bằng chứng, cửa sổ quan sát, tóm tắt đường cơ sở, mức độ nghiêm trọng, độ tin cậy/giải thích và trạng thái chất lượng dữ liệu. |
| AUD-FR-066 | P2 | Cùng một phiên bản quy tắc/đối tượng/cửa sổ/bằng chứng mang tính lũy kế (idempotent); dữ kiện đến sau tạo ra phiên bản chỉnh sửa (revision) hoặc phát hiện liên kết, không tạo bản sao trùng lặp khó hiểu. |
| AUD-FR-067 | P2 | Mức độ nghiêm trọng của phát hiện có thể giải thích được (`LOW`, `MEDIUM`, `HIGH`, `CRITICAL` theo chính sách); không có mức độ nghiêm trọng nào chỉ dựa trên đầu ra của mô hình mờ. |
| AUD-FR-068 | P2 | Việc sinh phát hiện bất thường tuyệt đối không bao giờ chặn đăng nhập, checkout, thanh toán, mở két hay thay đổi đơn hàng. |

### 4.8 Quy trình xử lý vụ việc P2 (Case Workflow)

| ID | Mức ưu tiên | Yêu cầu |
|---|---|---|
| AUD-FR-070 | P2 | Owner/Manager có thể liệt kê và xem các phát hiện/vụ việc được ủy quyền kèm bộ lọc theo trạng thái, mức độ nghiêm trọng, chi nhánh, đối tượng, quy tắc và khoảng thời gian. |
| AUD-FR-071 | P2 | Manager có thể xác nhận (`ACKNOWLEDGE`) và điều tra (`INVESTIGATE`) các phát hiện thuộc chi nhánh được phân công; mọi bước chuyển đổi bắt buộc có tác nhân, lý do và correlation. |
| AUD-FR-072 | P2 | Owner có thể xác nhận/điều tra và giải quyết (`RESOLVE`) hoặc bác bỏ (`DISMISS`) các phát hiện trong phạm vi tenant/chi nhánh được cấp quyền; bác bỏ bắt buộc phải có lý do. |
| AUD-FR-073 | P2 | Máy trạng thái vụ việc từ chối các bước chuyển đổi không hợp lệ và bảo toàn lịch sử chuyển đổi dạng chỉ ghi thêm (append-only). |
| AUD-FR-074 | P2 | Bằng chứng vụ việc là một snapshot/tham chiếu có phiên bản; sự kiện nguồn đến muộn hoặc điều chỉnh projection tạo ra bản chỉnh sửa hiển thị rõ ràng, không âm thầm thay đổi. |
| AUD-FR-075 | P2 | Việc truy cập phát hiện/vụ việc luôn đóng an toàn (fail-closed) khi không thể phân giải phạm vi nguồn hoặc phân quyền bằng chứng. |

## 5. Các Quy Tắc Nghiệp Vụ (Business Rules)

| ID | Quy tắc | Lý do |
|---|---|---|
| AUD-BR-001 | Dữ kiện kiểm toán chỉ được phát hành từ thao tác nguồn / outbox đã commit thành công; Audit không suy đoán trạng thái nghiệp vụ thành công từ log của một request thất bại. | Ngăn chặn việc sinh bằng chứng giả mạo. |
| AUD-BR-002 | Một sự kiện nguồn / khóa nguồn chỉ sinh ra đúng một tác động bằng chứng lịch sử; hệ thống chấp nhận cơ chế phân phối at-least-once. | Ngăn chặn việc tính trùng lặp số lượng kiểm toán/bất thường. |
| AUD-BR-003 | Việc điều chỉnh sự kiện mang tính bổ sung (additive) và liên kết với sự kiện gốc; bằng chứng lịch sử không bao giờ bị ghi đè. | Bảo toàn phả hệ phục vụ điều tra hồi tố. |
| AUD-BR-004 | Tác nhân (actor) và thu ngân thao tác thực tế (effective operator) là hai trường phân biệt; danh sách thành viên hiện tại không thể viết lại snapshot tác nhân trong quá khứ. | Bảo đảm tính trách nhiệm khi đổi ca nhanh hoặc khi nhân viên bị thu hồi quyền. |
| AUD-BR-005 | Giảm giá qua khuyến mãi/voucher được kiểm toán ngay cả khi tự động; giảm giá/ghi đè thủ công luôn bắt buộc phải có lý do. | Phát hiện cả việc lạm dụng khuyến mãi lẫn thất thoát do thao tác tay. |
| AUD-BR-006 | Nguồn, kích hoạt, tác nhân hoặc phân bổ bị thiếu/không xác định là trạng thái chất lượng tường minh và phải kích hoạt telemetry cảnh báo. | Tuyệt đối không âm thầm điền dữ liệu giả định vào bằng chứng. |
| AUD-BR-007 | Manager không có quyền giải quyết/bác bỏ; việc Owner giải quyết/bác bỏ bản thân nó cũng được kiểm toán tự động. | Phân tách rõ ràng giữa khâu điều tra và khâu kết luận xử lý. |
| AUD-BR-008 | Phát hiện bất thường P2 chỉ mang tính cố vấn/khuyến nghị và không thể ủy quyền hay ngăn chặn các thao tác nghiệp vụ ở nguồn. | Tránh việc tự động cưỡng chế gây rủi ro vận hành. |
| AUD-BR-009 | Lưu trữ pháp lý (legal hold) có quyền ưu tiên tuyệt đối so với quy trình xóa/lưu trữ định kỳ thông thường. | Bảo toàn nguyên vẹn bằng chứng bắt buộc theo yêu cầu. |
| AUD-BR-010 | Truy cập dữ liệu kiểm toán luôn bị ràng buộc theo phạm vi tenant/chi nhánh, bao gồm cả xuất file, phát lại, bằng chứng vụ việc và cache. | Ngăn chặn triệt để lỗ hổng IDOR và rò rỉ dữ liệu xuyên tenant. |

## 6. Yêu Cầu Cấp Độ API (API-Level Requirements)

API công khai tuân theo chuẩn REST/OpenAPI 3.1 dưới đường dẫn `/api/v1`; các thao tác vận hành nội bộ sử dụng `/internal/v1` và workload identity.

| Phương thức | Đường dẫn | Quyền | Mục đích |
|---|---|---|---|
| GET | `/api/v1/audit/events` | `audit:read` | Dòng thời gian / tìm kiếm tương tác, tối đa 90 ngày |
| GET | `/api/v1/audit/events/{eventId}` | `audit:read` | Chi tiết sự kiện an toàn kèm kiểm tra lại phạm vi |
| GET | `/api/v1/loss-prevention/findings` | `loss-case:read` | Danh sách các phát hiện / vụ việc bất thường |
| GET | `/api/v1/loss-prevention/findings/{findingId}` | `loss-case:read` | Chi tiết vụ việc và các tham chiếu bằng chứng |
| POST | `/api/v1/loss-prevention/findings/{findingId}/acknowledge` | `loss-case:investigate` | Manager/Owner xác nhận đã tiếp nhận vụ việc |
| POST | `/api/v1/loss-prevention/findings/{findingId}/investigation` | `loss-case:investigate` | Manager/Owner bắt đầu hoặc cập nhật quá trình điều tra |
| POST | `/api/v1/loss-prevention/findings/{findingId}/resolve` | `loss-case:resolve` | Owner giải quyết vụ việc kèm lý do bắt buộc |
| POST | `/api/v1/loss-prevention/findings/{findingId}/dismiss` | `loss-case:dismiss` | Owner bác bỏ vụ việc kèm lý do bắt buộc |
| POST | `/api/v1/audit/exports` | `audit:operate` | Khởi tạo tác vụ xuất dữ liệu lịch sử có giới hạn |
| GET | `/api/v1/audit/exports/{operationId}` | `audit:operate` | Kiểm tra trạng thái xuất dữ liệu / link tải về |
| GET | `/internal/v1/audit/health` | workload/ops | Sức khỏe watermark, độ trễ, độ hoàn tất, DLQ và tính toàn vẹn |
| POST | `/internal/v1/audit/rebuilds` | `audit:operate` | Rebuild projection truy vấn cho khoảng thời gian/phạm vi chỉ định |
| POST | `/internal/v1/audit/integrity-checks` | `audit:operate` | Bắt đầu tác vụ kiểm tra bản tóm tắt băm toàn vẹn hạn chế |

Các yêu cầu chung:

- Sử dụng Cognito JWT cho API công khai; workload identity/mTLS cho API nội bộ.
- Bắt buộc / khuyến nghị header `X-Correlation-Id`; yêu cầu Idempotency key cho các lệnh thay đổi trạng thái vụ việc/xuất file/rebuild/toàn vẹn.
- Sử dụng phong bì chuẩn `{data, meta}` và phong bì lỗi đã ẩn thông tin nhạy cảm; không làm lộ sự tồn tại của tenant/chi nhánh/tài nguyên lạ.
- Phân trang con trỏ (cursor pagination) cho danh sách sự kiện/vụ việc; cursor ràng buộc chặt chẽ với phạm vi, bộ lọc và phiên bản phân quyền.
- Lý do là trường bắt buộc và có giới hạn độ dài cho các hành động điều tra/giải quyết/bác bỏ và sự kiện giảm giá thủ công.
- Client tuyệt đối không được tự ý gửi các trường tenant, chi nhánh, tác nhân, thu ngân thao tác, kết quả hay tóm tắt trước/sau với tư cách là dữ liệu có thẩm quyền.

## 7. Yêu Cầu Phi Chức Năng (Non-Functional Requirements)

| Hạng mục | Yêu cầu |
|---|---|
| Độ tươi mới (Freshness) | Bằng chứng kiểm toán P0 có thể truy vấn được trong vòng 60 giây ở điều kiện hàng đợi/projection bình thường; công khai độ trễ/watermark và cảnh báo khi bị cũ. |
| Hiệu năng API | Phản hồi danh sách/chi tiết tương tác của audit/vụ việc đạt p95 ≤2 giây cho các truy vấn 30 ngày có chặn và kích thước trang ≤100; xuất file xử lý bất đồng bộ. |
| Thu nhận (Ingestion) | Consumer xử lý sự kiện at-least-once với cơ chế retry có chặn, DLQ và phát lại; không gửi ACK trước khi transaction lưu inbox/bằng chứng commit thành công. |
| Tính sẵn sàng (Availability) | Các service nguồn không phụ thuộc đồng bộ vào tính sẵn sàng truy vấn của Audit; outbox/hàng đợi lưu giữ ý định kiểm toán. API Audit trả về lỗi an toàn `503`/trạng thái chất lượng khi không khả dụng. |
| Khả năng mở rộng (Scale) | Thiết kế đáp ứng lưu trữ 7 năm, cơ sở 20 chi nhánh/tenant và tăng trưởng đo lường theo khối lượng sự kiện; các ngưỡng phân vùng/lưu trữ dài hạn được kiểm tra trước khi migration. |
| An toàn thông tin (Security) | Phòng thủ chiều sâu bằng điều kiện lọc tenant/chi nhánh, phân quyền Identity trước khi đọc cache, mã hóa lưu trữ/truyền tải, hạn chế quyền ghi và kiểm soát chặt việc phát lại/xuất dữ liệu. |
| Quyền riêng tư (Privacy) | Chỉ lưu các trường thuộc danh mục cho phép và đã được làm sạch; không lưu raw JWT/PIN/thông tin thanh toán/SĐT khách hàng trong DB, API, log, trace, event hay bằng chứng bất thường. |
| Tính toàn vẹn (Integrity) | Kiểm soát append-only kết hợp với việc tạo/xác minh checkpoint/bản tóm tắt băm theo lô có phiên bản; sự không khớp tạo cảnh báo/phát hiện và không bao giờ tự sửa âm thầm. |
| Chính sách lưu trữ (Retention) | Bằng chứng kiểm toán/bảo mật lưu tối thiểu 7 năm; quyền ưu tiên của lưu trữ pháp lý; các tác vụ lưu trữ dài hạn/xóa dữ liệu có tính lũy kế và được kiểm toán đầy đủ. |
| Khả năng quan sát (Observability) | Đầy đủ metric cho: độ trễ thu nhận, độ trễ projection, DLQ, tỷ lệ trùng lặp, từ chối schema, lệch hash toàn vẹn, tỷ lệ sinh vụ việc, tỷ lệ bác bỏ/dương tính giả và từ chối phân quyền API. |
| Kiểm thử (Testing) | Đầy đủ unit, integration, contract, kiểm thử phát lại/sai thứ tự, kiểm thử bảo mật/IDOR, làm sạch dữ liệu, lưu trữ/lưu trữ pháp lý, tải và tiêm lỗi (failure-injection). |

## 8. Quy Tắc Bảo Mật, Quyền Riêng Tư và Kiểm Toán

- Mọi câu truy vấn và thay đổi trạng thái đều bắt buộc có ngữ cảnh tenant; các hàm repository lặp lại điều kiện tenant/chi nhánh sau khi Identity phân giải.
- ID tài nguyên trong URL của event/case/evidence được kiểm tra đối chiếu với phạm vi; ID ngoại lai trả về mã lỗi an toàn `404`/`403` mà không liệt kê danh sách.
- Mọi thao tác truy cập kiểm toán, xuất dữ liệu, phát lại, kiểm tra toàn vẹn, lưu trữ pháp lý, chuyển trạng thái vụ việc và từ chối phân quyền đều tự động sinh dữ kiện kiểm toán.
- Payload nguồn thô mặc định không được lưu trữ. Nếu cần một tham chiếu mã hóa có giới hạn cho quy trình điều tra được duyệt, tham chiếu/loại dữ liệu/thời hạn lưu trữ phải tường minh và việc truy cập phải được kiểm toán.
- Văn bản lý do/bằng chứng tự do bị giới hạn độ dài, được làm sạch và quét tìm các secret/PII vô tình xuất hiện; UI render văn bản an toàn chống XSS.
- Worker của Audit chỉ tin cậy vào việc xác thực phong bì sự kiện, không tin cậy các trường actor/tenant do client tự gửi từ request công khai.
- Bằng chứng vụ việc được làm sạch dữ liệu trước khi lưu trữ và không để lộ SĐT khách hàng, dữ liệu ký/checksum thanh toán, mã PIN hay JWT.

## 9. Chất Lượng Dữ Liệu và Hành Vi Khi Có Lỗi

| Lỗi phát sinh | Hành vi bắt buộc |
|---|---|
| Sự kiện trùng lặp | Bỏ qua (no-op) sau khi dedupe; giữ nguyên bằng chứng đầu tiên và ghi nhận telemetry xử lý. |
| Phiên bản nguồn sai thứ tự | Lưu trữ/xếp hàng theo chính sách nguồn; không ghi đè lên bằng chứng mới hơn; cảnh báo khoảng trống dữ liệu. |
| Phiên bản sự kiện không xác định | Cách ly / chuyển vào DLQ kèm mã lỗi schema an toàn; tuyệt đối không tự đoán định các trường. |
| Thiếu sự kiện nguồn | Đánh dấu nguồn/projection chưa hoàn tất; công khai trạng thái `PARTIAL`; kích hoạt đối soát / cảnh báo. |
| Database của Audit không khả dụng | Consumer retry; outbox/hàng đợi của nguồn vẫn bền vững; API trả về `UNAVAILABLE`/503 mà không trả kết quả rỗng giả mạo. |
| Lệch mã băm toàn vẹn (Digest mismatch) | Đóng băng tác vụ toàn vẹn bị ảnh hưởng, tạo phát hiện bảo mật, giữ nguyên bằng chứng và yêu cầu điều tra hạn chế. |
| Đường cơ sở không đủ dữ liệu | Không phát hành phát hiện với độ tin cậy cao; ghi nhận telemetry trạng thái `INSUFFICIENT_BASELINE`. |
| Dữ kiện điều chỉnh / nguồn đến muộn | Tính toán lại phiên bản chỉnh sửa của projection/phát hiện bị ảnh hưởng và bảo toàn các phiên bản/phả hệ trước đó. |
| Xung đột chuyển trạng thái vụ việc | Trả về mã lỗi `409` kèm trạng thái hiện tại; không chuyển trạng thái bán phần hay sinh sự kiện hành động trùng lặp. |
| Lỗi xuất file | Giữ nguyên trạng thái thất bại của tác vụ và mã lỗi an toàn; không trả về file xuất bán phần như một bản xuất hoàn tất. |

## 10. Yêu Cầu Tích Hợp và Sự Kiện (Event & Integration)

### Các nhóm nguồn tiêu thụ (Consumed source groups)

| Nguồn | Các sự kiện / dữ kiện | Mục đích sử dụng trong Audit / Loss |
|---|---|---|
| Order | `OrderLineCancelled.v1`, `OrderCancelled.v1`, `OrderPaid.v1`, dữ kiện phiên bản đơn/giảm giá | Bằng chứng hủy/giảm giá, quy tắc đơn lặp, liên kết tác nhân/đơn hàng |
| Promotion / Loyalty | Phiên bản khuyến mãi/voucher/quy tắc áp dụng, dữ kiện ghi đè thủ công | Nguồn/phiên bản giảm giá và đường cơ sở bất thường |
| Payment | Dữ kiện thanh toán/hoàn tiền/đối soát/bảo mật | Ngữ cảnh hoàn tiền/mở két, bằng chứng bất thường thanh toán; không chứa payload provider thô |
| Shift | `ShiftOpened.v1`, `ShiftClosed.v1`, dữ kiện kiểm tiền/chênh lệch tiền mặt | Đường cơ sở ca làm việc và ngữ cảnh mở két |
| Identity / POS | Dữ kiện membership/vai trò/thiết bị/đổi ca nhanh/bảo mật | Lịch sử tác nhân/thu ngân thao tác/thiết bị và kiểm tra phân quyền |
| Recommendation / Reporting | Sự kiện ghi nhận/truy cập/chất lượng dữ liệu an toàn khi cần | Ngữ cảnh tùy chọn bổ trợ; không thay thế dữ kiện nguồn |

### Các sự kiện phát hành (Produced events)

| Sự kiện | Mục đích |
|---|---|
| `AuditEvidenceRecorded.v1` | Thông báo cho các consumer hạ nguồn về bằng chứng đã được chuẩn hóa và làm sạch |
| `AuditDataQualityChanged.v1` | Thông báo trạng thái thiếu nguồn, độ trễ hoặc sự cố toàn vẹn |
| `AnomalyFindingCreated.v1` / `AnomalyFindingRevised.v1` | Tóm tắt phát hiện an toàn và tham chiếu bằng chứng cho Owner/Manager/ops |
| `AuditCaseActionRecorded.v1` | Chuyển trạng thái xác nhận/điều tra/giải quyết/bác bỏ dạng chỉ ghi thêm (append-only) |
| `AuditIntegrityMismatchDetected.v1` | Cảnh báo bảo mật/vận hành và kích hoạt quy trình điều tra hạn chế |

Mọi sự kiện đều bắt buộc có: ID/phiên bản sự kiện, tenant/chi nhánh, phiên bản aggregate/nguồn, correlation/causation, producer và payload đã được làm sạch dữ liệu. Consumer của sự kiện tuyệt đối không được coi `AnomalyFindingCreated` là lệnh ủy quyền hay thay đổi trạng thái nghiệp vụ.

## 11. Yêu Cầu Quy Tắc Bất Thường (Anomaly Rules)

Các quy tắc P2 ban đầu được cấu hình qua bảng tham số và có quản lý phiên bản:

| Nhóm quy tắc | Tín hiệu nhận diện | Hành vi an toàn tối thiểu |
|---|---|---|
| Đột biến hủy món (Cancel spike) | Số lượng/tiền của các dòng/đơn bị hủy trong cửa sổ thời gian so với đường cơ sở nhân viên/chi nhánh/thời gian | Yêu cầu kích thước mẫu tối thiểu; loại trừ sự kiện trùng/bị từ chối; bằng chứng liệt kê ID nguồn |
| Đột biến giảm giá (Discount spike) | Số lượng/tiền giảm giá thủ công/khuyến mãi/voucher và tỷ lệ so với đường cơ sở tương đương | Tách biệt khuyến mãi tự động với ghi đè thủ công; bảo toàn phiên bản/phân bổ; không lộ định danh khách hàng |
| Tần suất mở két (Drawer frequency) | Mở két thủ công / không bán hàng, mở ngoài ca / ngoài lịch dự kiến, mở két lặp lại liên tục | Phân biệt rõ nguồn kích hoạt và thiết bị; thiếu thông tin ca/múi giờ trả về chất lượng thấp, không tạo cảnh báo độ tin cậy cao |
| Hành động lặp trên đơn/ca | Nhiều hành động hủy/giảm giá/mở két trên cùng một đơn hàng/ca trong cửa sổ ngắn | Gom nhóm vào một cửa sổ phát hiện đơn định duy nhất; không nhân đôi do phân phối lặp lại |

Các quy tắc bắt buộc phải định nghĩa rõ: `ruleVersion`, cửa sổ bằng chứng, các chiều đối tượng, nguồn đường cơ sở, kích thước mẫu tối thiểu, hành vi khởi động nguội, ngưỡng kích hoạt, ánh xạ mức độ nghiêm trọng, cơ chế giảm nhiễu/hạ nhiệt (suppression/cooldown) và khóa phát hiện. Việc thay đổi quy tắc không viết lại các phát hiện lịch sử; một phiên bản đánh giá mới sẽ sinh ra một bản chỉnh sửa (revision).

## 12. Tiêu Chí Nghiệm Thu và Ma Trận Kiểm Thử

| Khu vực | Các ca kiểm thử bắt buộc |
|---|---|
| P0 Hủy món | Hủy dòng món thành công, hủy toàn bộ đơn, hủy bị từ chối, trùng lặp/sai thứ tự, phiên bản đơn cũ, thiếu lý do/tác nhân |
| P0 Giảm giá | Khuyến mãi tự động, voucher, ghi đè thủ công, ghi đè thủ công thiếu lý do, phân bổ bán phần/chưa phân bổ, thay đổi phiên bản khuyến mãi, giảm giá thất bại/bị từ chối |
| P0 Mở két | Bán hàng/hoàn tiền/mở ca/bảo trì/không bán hàng, lệnh thiết bị trùng lặp, kích hoạt không xác định, không có ca, nhân viên bị vô hiệu hóa/thu hồi quyền |
| Phân quyền | Owner toàn tenant/tập con, Manager chi nhánh được giao, chi nhánh/tài nguyên/vụ việc ngoại lai, thu hồi quan hệ thành viên, lệch phạm vi cursor/export, kiểm tra cache sau thu hồi |
| Quyền riêng tư | Tuyệt đối không có JWT/PIN/SĐT/bí mật thanh toán/payload provider thô trong DB/API/log/trace/event/bằng chứng; làm sạch lý do an toàn |
| Tính toàn vẹn | Từ chối ghi đè append-only, sinh/xác minh bản tóm tắt băm, cảnh báo khi lệch hash, phát lại bảo toàn phả hệ digest, lưu trữ pháp lý chặn lệnh xóa |
| Độ tin cậy | At-least-once, tính nguyên tử của inbox, từ chối schema lạ, retry/lũy thừa backoff, DLQ, phát lại/rebuild, sự cố DB Audit, trễ outbox nguồn |
| Truy vấn / API | Giới hạn 30/90 ngày, sai lệch cursor, sắp xếp ổn định, trạng thái rỗng/bán phần/cũ/không khả dụng, kiểm tra phạm vi xem chi tiết, xuất file >90 ngày bất đồng bộ |
| Bất thường | Quy tắc hủy/giảm giá/mở két/hành động lặp, đường cơ sở không đủ, khởi động nguội, ngưỡng/mức độ nghiêm trọng, phát hiện trùng lặp, chỉnh sửa muộn, không tự động khóa |
| Vụ việc | Manager xác nhận/điều tra, Owner giải quyết/bác bỏ, chuyển trạng thái không hợp lệ, thiếu lý do, cập nhật đồng thời, mở lại/chỉnh sửa, mọi chuyển đổi đều được kiểm toán |
| Hiệu năng | Độ trễ thu nhận sự kiện, truy vấn p95 ≤2s danh sách/chi tiết có chặn, tối đa 90 ngày tương tác, tải xuất file, tác vụ lưu trữ/retention 7 năm |

## 13. Các Quyết Định Đã Giải Quyết Từ Đánh Giá Vision

- Dịch vụ Audit tập trung là chủ sở hữu bằng chứng dạng chỉ ghi thêm (append-only); các producer phát hành sự kiện qua Transactional Outbox / EventBridge / SQS.
- Chủ sở hữu (Owner) xem toàn tenant hoặc tập con chi nhánh; Quản lý (Manager) chỉ xem các chi nhánh được phân công; Thu ngân và bếp không có quyền truy cập console trong MVP.
- Thời gian lưu trữ bằng chứng kiểm toán/bảo mật tối thiểu là 7 năm kèm quyền ưu tiên tuyệt đối của lưu trữ pháp lý (legal hold).
- Kiểm toán giảm giá bao quát cả khuyến mãi/voucher và ghi đè thủ công; ghi đè thủ công bắt buộc phải có lý do.
- Cơ chế toàn vẹn trong MVP là kiểm soát append-only ở cấp cơ sở dữ liệu kết hợp với việc tạo/xác minh checkpoint/bản tóm tắt băm theo lô.
- Phân hệ P2 sử dụng danh mục quy tắc có thể giải thích được kết hợp với đường cơ sở nhân viên/chi nhánh/thời gian; không dùng ML và không tự động khóa tài khoản/nghiệp vụ.
- Manager có quyền xác nhận/điều tra; chỉ Owner mới có quyền giải quyết hoặc bác bỏ vụ việc.
- Truy vấn kiểm toán tương tác bị chặn ở mức tối đa 90 ngày; dữ liệu lịch sử cũ hơn được phục vụ qua cơ chế xuất file bất đồng bộ có kiểm soát.

## 14. Các Tu Chính Hợp Đồng Hệ Thống Gốc Bắt Buộc

### POS Platform / Audit

- Mở rộng bảng `audit_events` và sự kiện `AuditRecorded.v1` với phả hệ sự kiện/nguồn, tác nhân/thu ngân thao tác, thiết bị/ca, kết quả, phiên bản làm sạch dữ liệu và schema payload chuyên biệt theo từng hành động.
- Duy trì hàng đợi `pos-audit-events` với cơ chế at-least-once / retry / DLQ và định nghĩa khóa dedupe từ nguồn sang Audit.
- Bổ sung các quyền hạn của Audit, danh mục API/read model, quyền sở hữu tính toàn vẹn/lưu trữ và chính sách vận hành hạn chế.

### Order

- Phát hành các dữ kiện nguồn về việc hủy món và giảm giá/ghi đè kèm phiên bản đơn/dòng bất biến, phân bổ trước/sau, tác nhân/thu ngân thao tác, lý do và kết quả.

### Promotion / Loyalty

- Bảo toàn phiên bản khuyến mãi/voucher/quy tắc và giá trị phân bổ áp dụng; phân biệt rõ giảm giá tự động với ghi đè thủ công.

### Payment / Shift / Thiết bị POS

- Định nghĩa producer/nguồn sự thật và danh mục kích hoạt mở két tiền; công khai ngữ cảnh ca/thiết bị/thu ngân mà không để lộ bí mật thanh toán.
- Phát hành các dữ kiện kiểm toán về thanh toán/hoàn tiền/đối soát và chênh lệch tiền ca làm việc phục vụ ngữ cảnh và các quy tắc bất thường.

### Identity

- Bổ sung các quyền `audit:read`, `loss-case:read`, `loss-case:investigate`, `loss-case:resolve`, `loss-case:dismiss` và quyền hạn chế `audit:operate`; định nghĩa phiên bản membership/phân quyền hiện hành và hành vi thu hồi quyền tức thì.

## 15. Cổng Phê Duyệt Yêu Cầu (Requirements Approval Gate)

- **Trạng thái hiện tại:** Đã phê duyệt — người dùng đã chấp nhận các cảnh báo trong đánh giá Requirements.
- **Yêu cầu trước khi chuyển sang Kiến trúc:** Đã hoàn thành; các tu chính hệ thống gốc còn lại và fixture thực thi là các cổng triển khai được ghi nhận trong Kiến trúc/Hợp đồng.
- **Quy tắc triển khai:** Tuyệt đối không viết controller/consumer/migration/anomaly worker trực tiếp từ văn bản mô tả; bắt buộc phải cụ thể hóa các đặc tả OpenAPI, AsyncAPI/JSON Schema, fixture quy tắc/chỉ số và các ràng buộc migration trước khi viết code.
