Started at: 2026/10/13 00:00:00
Finished at: 2026/10/13 00:00:00
Total time: 0 minutes
---

# Module 10 — Báo Cáo & Dashboard: Yêu Cầu Nghiệp Vụ (Requirements)

**Ngày:** 2026-10-13  
**Trạng thái:** Approved with warnings — Người dùng đã phê duyệt  
**Vision:** [Reporting Vision](./2026-10-13-vision.md)  
**Vision review:** [Reporting Vision Review](./2026-10-13-vision-review.md)

## 1. Mục đích và ranh giới hệ thống (Purpose and boundaries)

Reporting service chuyển hóa các sự kiện nghiệp vụ (domain events) đã được đánh phiên bản thành các mô hình đọc (read model) trên PostgreSQL nhằm phục vụ giao diện dashboard gần thời gian thực (near-realtime). Reporting là đơn vị sở hữu chỉ số (metric owner) nhưng không sở hữu nguồn sự thật (source of truth) của Order, Payment, Loyalty/Promotion, Recommendation hay Identity.

Giai đoạn MVP không truy vấn chéo database, không tiếp nhận câu lệnh SQL tùy ý (arbitrary SQL) và không đưa AWS Athena vào luồng xử lý trực tiếp của dashboard. Athena là ranh giới mở rộng phục vụ dữ liệu lịch sử hoặc ad-hoc sau này khi có bằng chứng thực tế về khối lượng dữ liệu và độ trễ.

### Trong phạm vi (In scope)

- P0: Doanh thu theo ngày, theo chi nhánh, danh sách món bán chạy (top items).
- P1: Doanh thu theo giờ, doanh thu theo nhân viên checkout, hiệu quả khuyến mãi, hiệu quả gợi ý món.
- Dashboard Angular dùng chung cho Chủ chuỗi (Owner) và Quản lý (Manager) với phạm vi dữ liệu phân quyền khác nhau.
- Tiếp cận API-first chuẩn REST/OpenAPI 3.1, chỉ đọc (read-only), hỗ trợ lọc, phân trang và drill-down.
- Hình chiếu hướng sự kiện (event-driven projection) với cơ chế phân phối ít nhất một lần, khử trùng lặp, hàng đợi DLQ, tái dựng/phát lại và watermark độ tươi.
- Hiển thị số liệu đã điều chỉnh hiện tại (current corrected metrics) kèm `asOfWatermark` và `lastUpdatedAt`.

### Ngoài phạm vi (Out of scope)

- Báo cáo kế toán, báo cáo thuế pháp lý hoặc xuất hóa đơn điện tử.
- Bộ dựng truy vấn / chạy SQL tùy ý cho người dùng cuối.
- Phân tích dự đoán, phát hiện bất thường hay học máy (ML).
- Drill-down xem chi tiết từng đơn hàng hoặc từng khách hàng trên dashboard.
- Quy trình production dùng Athena trong giai đoạn MVP.
- Đột biến thay đổi trạng thái của các domain service khác hoặc sửa đổi sự kiện nguồn.

## 2. Các tác nhân và phân quyền (Actors and authorization)

| Tác nhân (Actor) | Quyền hạn (Permission) | Phạm vi dữ liệu (Scope) |
|---|---|---|
| Chủ chuỗi (Owner) | `report:read` | Toàn bộ tenant hoặc tập hợp các chi nhánh thuộc tenant |
| Quản lý (Manager) | `report:read` | Chỉ các chi nhánh được phân công có tư cách thành viên hợp lệ |
| Thu ngân / Bếp | Không có quyền xem báo cáo trong MVP | Không thể truy cập API báo cáo |
| Dashboard Angular | Token JWT của người dùng + quyền do máy chủ phân giải | Không tự tạo quyền hạn hay phạm vi tenant/chi nhánh |
| Reporting worker | Định danh workload identity | Tiêu thụ sự kiện và ghi vào Reporting DB do dịch vụ sở hữu |
| Worker vận hành / Replay | Quyền hạn chế `reporting:operate` | Tái dựng/phát lại dữ liệu theo tenant / chi nhánh / cửa sổ thời gian được duyệt |

Mọi yêu cầu công khai bắt buộc phải phân giải quyền từ Identity trước khi đọc bộ nhớ đệm (cache) hoặc cơ sở dữ liệu. Tầng repository luôn luôn áp dụng vị từ `tenant_id` và `branch_id`; tuyệt đối không tin cậy mảng `branchId[]`, vai trò, tenant hay phạm vi do máy khách tự gửi lên.

## 3. Định nghĩa và từ điển chỉ số (Definitions and metric dictionary)

### 3.1 Chính sách thời gian và phạm vi (Time and scope policy)

- Xem một chi nhánh duy nhất: Sử dụng múi giờ chuẩn IANA của chính chi nhánh đó.
- Xem nhiều chi nhánh cùng lúc: Sử dụng múi giờ báo cáo chung của tenant; phản hồi API vẫn trả về thông tin `timezone` và `periodBounds`.
- Múi giờ của trình duyệt người dùng hoàn toàn không có thẩm quyền.
- `businessDate` là ngày kinh doanh cục bộ tính theo chính sách múi giờ; `periodFrom`/`periodTo` trả về cả ngày/giờ cục bộ và ranh giới chuẩn UTC khi cần.
- Dashboard mặc định hiển thị 7 ngày hoặc 30 ngày gần nhất; phạm vi tương tác tối đa là 12 tháng.
- Bộ lọc xuyên chi nhánh cho phép chọn tối đa 20 chi nhánh; Chủ chuỗi có thể chọn phạm vi toàn tenant nếu chính sách cho phép.

### 3.2 Các chỉ số doanh thu (Revenue metrics)

Reporting chỉ nạp các dữ kiện đơn hàng đã thanh toán từ sự kiện `OrderPaid.v1` và các dữ kiện hoàn tiền / điều chỉnh đã được phê duyệt trong hợp đồng parent.

| Chỉ số | Định nghĩa chuẩn | Quy tắc xử lý trường hợp biên |
|---|---|---|
| `paidOrderCount` | Đếm số lượng đơn hàng `OrderPaid.v1` hoàn tất duy nhất sau khi khử trùng lặp | Sự kiện trùng lặp / trùng khóa nguồn chỉ tính 1 lần; đối soát muộn của Payment không tính |
| `grossSalesMinor` | Tổng số tiền `subtotalMinor` trước khi giảm giá, chưa bao gồm VAT / phụ phí / tiền tip | Lấy trực tiếp từ ảnh chụp OrderPaid chuẩn, không tính toán lại từng dòng lúc query |
| `discountMinor` | Tổng số tiền giảm giá đã áp dụng và được lưu snapshot trong đơn đã thanh toán | Phân bổ khuyến mãi/voucher phải có phiên bản; thiếu phân bổ đưa vào `UNALLOCATED` |
| `netSalesMinor` | `grossSalesMinor - discountMinor` theo phiên bản tính toán | Giá trị không âm; hoàn tiền / điều chỉnh áp dụng theo chính sách có đánh phiên bản |
| `vatMinor` | Ảnh chụp thuế VAT / tổng tiền VAT trích xuất từ dữ kiện đơn đã thanh toán chuẩn | Không gộp chung vào chỉ số `netSalesMinor` |
| `aovMinor` | `netSalesMinor / paidOrderCount` (Giá trị đơn trung bình) | Mẫu số bằng 0 trả về trạng thái `NO_DATA`, tuyệt đối không trả về số 0 giả tạo |
| `comparisonDelta` | Mức chênh lệch thực tế so với kỳ so sánh cùng từ điển chỉ số | Trả về cả số tiền chênh lệch, tỷ lệ phần trăm và khoảng thời gian so sánh chính xác |

Tiền tệ luôn dùng số nguyên đơn vị minor kết hợp mã loại tiền. Trường `calculationVersion` là bắt buộc trong mọi phản hồi tổng hợp.
Giai đoạn MVP chỉ hỗ trợ tiền tệ `VND`; trường hợp có nhiều loại tiền tệ bị từ chối hoặc trả về `UNAVAILABLE` theo chính sách, tuyệt đối không cộng gộp các số minor khác loại tiền.

### 3.3 Danh sách món bán chạy (Top items)

- Nguồn dữ liệu: Ảnh chụp các dòng món đã thanh toán trong sự kiện `OrderPaid.v1`.
- `quantitySold` và `revenueMinor` chỉ tính toán trên các dòng / đơn hàng đã thanh toán thành công.
- `revenueMinor` của top món là doanh thu ròng của dòng món sau khi đã phân bổ giảm giá, chưa gồm VAT / phụ phí / tip; khoản giảm giá dòng món không thể phân bổ sẽ đưa vào `UNALLOCATED_ITEM_DISCOUNT`.
- Nhãn tên món là ảnh chụp tại thời điểm thanh toán; món đã bị lưu trữ (archive) vẫn xuất hiện trong lịch sử kèm nhãn `status=ARCHIVED` nếu người dùng có quyền xem.
- Thứ tự giải quyết thế hòa (tie-break) ổn định: `revenueMinor GIẢM DẦN`, `quantitySold GIẢM DẦN`, `itemId TĂNG DẦN`.
- Mặc định hiển thị top 10 món, tối đa 100 món; phân trang bằng cursor khi xem bảng chi tiết.

### 3.4 Doanh thu theo nhân viên (Employee sales)

- Quy kết chính thức được ghi nhận cho nhân viên / thu ngân đang active tại thời điểm checkout thành công.
- Sự kiện `OrderPaid.v1` của parent bắt buộc phải mang tham chiếu nhân viên bất biến + ảnh chụp tên hiển thị + phiên bản quy kết.
- Nhân viên đã bị vô hiệu hóa (disabled) vẫn được bảo toàn dữ liệu lịch sử, không bị thay đổi quy kết hồi tố.
- Trường hợp thiếu hoặc sai lệch thông tin nhân viên sẽ được đưa vào mục `UNASSIGNED/UNKNOWN`, vẫn giữ trong tổng doanh thu và trả kèm cảnh báo chất lượng dữ liệu.
- Việc đổi ca/thu ngân nhanh không được phép lấy nhân viên tạo đơn nháp để thay thế cho người thực hiện checkout cuối cùng.
- Quản lý chỉ được xem nhân viên thuộc chi nhánh được phân công; Chủ chuỗi có thể xem toàn tenant hoặc tập hợp chi nhánh.

### 3.5 Hiệu quả chương trình khuyến mãi (Promotion effectiveness)

Các chương trình khuyến mãi được nhóm theo `promotionVersionId` / `ruleVersion` bất biến, tuyệt đối không gộp chung các phiên bản khác nhau vào một kết quả mặc định.

Mỗi dữ kiện khuyến mãi đã áp dụng phải cung cấp: phiên bản khuyến mãi / luật, nhóm xung đột, thời điểm áp dụng / ngày kinh doanh, tham chiếu đơn hàng dạng opaque, phân bổ giảm giá, chi nhánh, kênh bán và ID sự kiện nguồn.

Các chỉ số:

- `appliedOrderCount`: Số đơn hàng đã thanh toán duy nhất có áp dụng phiên bản khuyến mãi này.
- `grossSalesMinor`: Doanh thu gộp của tập đơn hàng áp dụng khuyến mãi.
- `discountMinor`: Tổng số tiền giảm giá được phân bổ cho phiên bản khuyến mãi này.
- `netSalesMinor`: Doanh thu ròng sau khi trừ giảm giá.
- `averageNetOrderMinor`: `netSalesMinor / appliedOrderCount`, mẫu số bằng 0 trả về `NO_DATA`.
- `applicationRate`: Tỷ lệ áp dụng (nếu tồn tại mẫu số là lượt hiển thị / báo giá khuyến mãi đủ điều kiện); nếu không có mẫu số thì trả về `NOT_AVAILABLE`, không tự phỏng đoán.

Một đơn hàng có nhiều phiên bản khuyến mãi sẽ được phân bổ theo bảng phân rã giảm giá bất biến. Nếu không thể phân bổ, số tiền đưa vào mục `UNALLOCATED_PROMOTION`, không nhân bản toàn bộ số tiền giảm giá cho từng luật.

### 3.6 Hiệu quả gợi ý món (Recommendation effectiveness)

Sự kiện `SuggestionShown.v1` đại diện cho lượt hiển thị; `SuggestionAccepted.v1` là tín hiệu quy kết; `OrderPaid.v1` là điều kiện xác nhận đơn đã thanh toán.

Các chỉ số:

- `shownCount`: Số lượng lượt hiển thị / tương tác gợi ý duy nhất sau khi khử trùng lặp.
- `acceptedCount`: Số lượng tương tác chấp thuận duy nhất liên kết được với sự kiện hiển thị tương ứng.
- `acceptanceRate`: Tỷ lệ chấp thuận tính bằng `acceptedCount / shownCount`; mẫu số bằng 0 trả về `NO_DATA`.
- `acceptedOrderCount`: Số đơn hàng đã thanh toán duy nhất có ít nhất một món gợi ý được chấp nhận.
- `controlOrderCount`: Số đơn hàng đã thanh toán duy nhất có hiển thị gợi ý trong cùng ngữ cảnh đủ điều kiện nhưng không chấp nhận món nào.
- `acceptedAovMinor`: `netSalesMinor / acceptedOrderCount` (AOV của nhóm chấp nhận).
- `controlAovMinor`: `netSalesMinor / controlOrderCount` (AOV của nhóm đối chứng).
- `aovUpliftMinor`: Mức chênh lệch AOV tính bằng `acceptedAovMinor - controlAovMinor`.
- `aovUpliftRatio`: Tỷ lệ tăng trưởng AOV tính bằng `aovUpliftMinor / controlAovMinor` khi mẫu số đối chứng > 0; trường hợp khác trả về `NO_DATA`.

Quy tắc nhóm đối chứng (Control group rules):

1. Phải cùng phạm vi chi nhánh và cùng khoảng thời gian / múi giờ được chọn.
2. Đơn hàng đã nhìn thấy gợi ý nhưng không chấp nhận bất kỳ gợi ý nào trong cùng ngữ cảnh quy kết.
3. Chỉ những đơn hàng ở trạng thái `PAID` mới được đưa vào tập hợp tính AOV chấp nhận / đối chứng.
4. Một đơn hàng chỉ được tính đúng 1 lần trong nhóm chấp nhận hoặc đối chứng, kể cả khi chấp nhận nhiều gợi ý khác nhau.
5. Số lượt chấp thuận vẫn được tính riêng biệt theo từng chiều: lượt hiển thị, món, luật và mô hình gợi ý.
6. Mức tăng trưởng AOV là chỉ số cấp đơn hàng (order-level KPI), không cộng dồn từ từng món gợi ý; phân rã theo món chỉ hiển thị các chỉ số về tỷ lệ chấp thuận trong MVP.
7. Nếu `SuggestionAccepted` không liên kết được với sự kiện hiển thị / đơn hàng hợp lệ, hoặc sự kiện `OrderPaid` chưa đến, dữ kiện quy kết được giữ ở trạng thái chờ/lỗi và không đưa vào tính AOV đã thanh toán.

Ngữ cảnh quy kết (`Attribution context`) mặc định là trong cùng phiên giỏ hàng / đơn hàng: Sự kiện `SuggestionShown` bắt buộc phải xảy ra trước `SuggestionAccepted`, và cả hai phải liên kết được tới cùng `orderId` / correlation giỏ hàng trước khi đơn hàng chuyển sang `PAID`. Tuyệt đối không ghép nối gợi ý giữa các phiên khác nhau hoặc chỉ dựa vào sự gần gũi về khách hàng, chi nhánh hay thời gian.

Chỉ số `aovUplift` là mức chênh lệch quan sát được giữa hai nhóm (observed cohort difference), không phải là chứng minh nguyên nhân - kết quả tuyệt đối. Giao diện dashboard phải dùng câu từ chuẩn xác: “Chênh lệch AOV giữa hai nhóm” và hiển thị rõ số lượng đơn của từng nhóm; không gọi là “do gợi ý làm tăng” khi chưa có hợp đồng thử nghiệm ngẫu nhiên (A/B testing).

## 4. Yêu cầu chức năng (Functional requirements)

### FR-REP-001 — Tiêu thụ và xây dựng hình chiếu các dữ kiện chuẩn tắc (P0)

Reporting phải tiêu thụ các envelope sự kiện có đánh phiên bản từ Order, sự kiện điều chỉnh/hoàn tiền của Payment/Order, metadata chi nhánh của Identity, Loyalty/Promotion và Recommendation theo hợp đồng consumer. Consumer phải:

1. Thẩm định phiên bản schema, phạm vi tenant/chi nhánh, thời điểm sự kiện và các trường nguồn bắt buộc.
2. Khử trùng lặp theo `eventId` và khóa nghiệp vụ nguồn khi hợp đồng parent yêu cầu.
3. Chỉ gửi xác nhận (ack) sau khi transaction cập nhật hình chiếu và trạng thái inbox đã commit thành công.
4. Thử lại các lỗi tạm thời theo thuật toán backoff; các lỗi thẩm định schema / tenant / chỉ số được chuyển vào hàng đợi DLQ.
5. Ghi nhận đầy đủ: `projectionVersion`, `sourceWatermark`, `lastUpdatedAt`, `calculationVersion`.
6. Tuyệt đối không đọc trực tiếp database của producer để bù đắp các trường dữ liệu bị thiếu.

### FR-REP-002 — Báo cáo doanh thu theo ngày (P0)

API phải trả về doanh thu theo `businessDate`, phạm vi, loại tiền tệ và độ tươi dữ liệu. Mỗi dòng tối thiểu phải có `paidOrderCount`, gross / discount / net / VAT, AOV, số liệu so sánh nếu có yêu cầu, `asOfWatermark`, `lastUpdatedAt`, `projectionVersion`, `calculationVersion` và trạng thái chất lượng dữ liệu `dataQuality`.

### FR-REP-003 — So sánh doanh thu giữa các chi nhánh (P0)

Chủ chuỗi (Owner) có thể xem tổng quan toàn tenant hoặc so sánh các chi nhánh trong tenant. Quản lý (Manager) chỉ nhận được danh sách các chi nhánh do Identity phân giải hợp lệ. Chi nhánh không thuộc quyền không được để lộ sự tồn tại qua kết quả rỗng, thông báo lỗi, số lượng đếm hay thứ hạng.

### FR-REP-004 — Danh sách món bán chạy nhất (P0)

API phải hỗ trợ sắp xếp theo allowlist `REVENUE` hoặc `QUANTITY`, giới hạn từ 1 đến 100 món, phân trang bằng cursor cho bảng chi tiết, hiển thị ảnh chụp món đã archive và trạng thái chất lượng dữ liệu. Tuyệt đối không trả về ID đơn hàng thô, ID khách hàng hay ID thanh toán.

### FR-REP-101 — Doanh thu theo khung giờ (P1)

API phải phân chia theo giỏ giờ cục bộ `0..23`, múi giờ, số lượng đơn, doanh thu ròng, các trường tùy chọn gross/discount/VAT và độ tươi. Xem một chi nhánh dùng múi giờ chi nhánh; xem nhiều chi nhánh dùng múi giờ báo cáo của tenant. Không trả về mốc thời gian giao dịch chính xác.

### FR-REP-102 — Doanh thu theo nhân viên thu ngân (P1)

API phải nhóm theo ảnh chụp nhân viên checkout, chi nhánh, kỳ báo cáo và các chỉ số. Giá trị `UNASSIGNED/UNKNOWN` là hợp lệ và phải kèm cảnh báo nếu tỷ lệ vượt ngưỡng cấu hình. Thông tin định danh nhân viên ngoài phạm vi thẩm quyền tuyệt đối không được trả về.

### FR-REP-103 — Hiệu quả chương trình khuyến mãi (P1)

API phải nhóm theo phiên bản khuyến mãi / phiên bản luật, chi nhánh / kỳ báo cáo và trả về: số đơn áp dụng, doanh thu gộp, giảm giá, doanh thu ròng, giá trị đơn ròng trung bình, trạng thái phân bổ, phiên bản tính toán và chất lượng dữ liệu.

### FR-REP-104 — Hiệu quả gợi ý món (P1)

API phải trả về số lượt hiển thị, chấp thuận, tỷ lệ chấp thuận và các chỉ số AOV theo nhóm cấp đơn hàng theo chi nhánh / kỳ báo cáo. Phản hồi phải phân biệt rõ ràng giữa `NO_DATA`, `NOT_AVAILABLE` và `PARTIAL`; không bao giờ gọi sự kiện chấp thuận là dữ kiện doanh thu.

### FR-REP-105 — Chế độ xem dữ liệu điều chỉnh hiện tại (P0)

Dashboard mặc định hiển thị hình chiếu hiện tại sau khi đã phản ánh các sự kiện hoàn tiền, sửa sai hoặc sự kiện đến trễ. Phản hồi phải kèm `asOfWatermark`, `lastUpdatedAt`, `projectionVersion` và `calculationVersion`. Không ghi đè sự kiện nguồn; phả hệ điều chỉnh được lưu giữ trong metadata của hình chiếu.

### FR-REP-106 — Độ tươi và tính đầy đủ của dữ liệu (P0)

Các trạng thái chất lượng dữ liệu (Data quality states):

| Trạng thái | Quy tắc xác định | Hành vi hiển thị trên giao diện (UI) |
|---|---|---|
| `FRESH` | Độ trễ projection ≤ 1 phút và các nguồn bắt buộc đầy đủ | Hiển thị KPI bình thường + mốc watermark |
| `STALE` | Độ trễ > 1 phút nhưng read model vẫn đọc được; > 5 phút kích hoạt cảnh báo trễ | Hiển thị KPI + cảnh báo dữ liệu cũ; không coi là fresh |
| `PARTIAL` | Thiếu dữ liệu hình chiếu của một hoặc nhiều nguồn / chi nhánh bắt buộc | Không hiển thị tổng hợp như dữ liệu hoàn chỉnh; nêu rõ các nguồn bị thiếu |
| `UNAVAILABLE` | Read model không đọc được hoặc tiến trình hình chiếu gặp sự cố | Hiển thị lỗi an toàn; tuyệt đối không query bù từ domain DB |
| `NO_DATA` | Truy vấn hợp lệ nhưng không có giao dịch nào trong khoảng thời gian chọn | Hiển thị trạng thái rỗng, không coi là số 0 mặc định |

Phản hồi độ tươi luôn luôn trả về: `status`, `watermark`, `watermarkVersion`, `lastUpdatedAt`, `projectionVersion`, danh sách nguồn bị thiếu `missingSources[]` và `calculationVersion`.

### FR-REP-107 — Phạm vi và bộ lọc trên Dashboard (P0)

Các bộ lọc được hỗ trợ:

- `branchIds` hoặc phạm vi toàn tenant theo kết quả phân quyền.
- `periodFrom`, `periodTo`, phạm vi tối đa 12 tháng.
- `timezonePolicy` do máy chủ quyết định; client chỉ có thể yêu cầu chế độ được hỗ trợ.
- `metric`, `sortBy`, `limit`, `cursor` theo allowlist của từng endpoint.
- Các chiều phân tích P1: nhân viên, phiên bản khuyến mãi, món/luật/mô hình gợi ý theo từng endpoint tương ứng.

Các bộ lọc không hỗ trợ, trường tùy ý, lệnh SQL thô, ID thô của đơn/khách/thanh toán hoặc khoảng thời gian vượt quá chính sách đều bị từ chối với lỗi thẩm định (validation error).

### FR-REP-108 — An toàn bộ nhớ đệm Dashboard (P0)

Cho phép lưu cache phía máy chủ tối đa 15 giây cho các phản hồi báo cáo chỉ đọc. Khóa cache (Cache key) bắt buộc phải cấu thành từ: tenant, phiên bản phạm vi quyền đã phân giải, chính sách vai trò, endpoint, bộ lọc đã chuẩn hóa, phiên bản tính toán và watermark nguồn. Phân quyền bắt buộc phải được kiểm tra trước khi tra cứu cache; quyền bị thu hồi không được phép đọc cache cũ.

Phản hồi có điều kiện ETag chỉ được dùng khi phạm vi quyền đã được xác thực. Cache không được kéo dài độ tươi vượt quá 1 phút; trạng thái `UNAVAILABLE` không được cache như dữ liệu thành công lâu hơn chính sách thử lại.

### FR-REP-109 — Phân trang và Drill-down (P0/P1)

Tính năng drill-down chỉ được điều hướng từ dữ liệu tổng hợp đã được ủy quyền sang tài nguyên read model thuộc cùng tenant / phạm vi. Tuyệt đối không mở ra các bản ghi đơn hàng, khách hàng hay thanh toán thô. Các endpoint danh sách dùng phân trang theo cursor, mặc định 20, tối đa 100 bản ghi; sắp xếp ổn định bắt buộc có khóa phá vỡ thế hòa (tie-break).

### FR-REP-110 — Tái dựng và phát lại hình chiếu (P0)

Worker vận hành có thể khởi chạy tác vụ tái dựng (rebuild) có giới hạn phạm vi theo tenant / chi nhánh / khoảng thời gian kèm khóa lũy nghiệm. Tác vụ rebuild phải:

- Không tạo bản ghi trùng lặp khi chạy lại nhiều lần.
- Ghi nhận ID thao tác, watermark bắt đầu/kết thúc nguồn, phiên bản hình chiếu mục tiêu và kết quả.
- Không để dashboard đọc lẫn lộn giữa hai phiên bản hình chiếu mà thiếu tín hiệu chất lượng dữ liệu.
- Hỗ trợ các trạng thái tạm dừng, tiếp tục, thất bại, DLQ và lưu vết kiểm toán người thực hiện.

### FR-REP-111 — Trạng thái giao diện và xử lý lỗi (P0)

Giao diện dashboard phải render mượt mà các trạng thái: `FRESH`, `STALE`, `PARTIAL`, `UNAVAILABLE`, `NO_DATA`, từ chối phân quyền, khoảng thời gian không hợp lệ và vượt giới hạn tần suất gọi API. Thông báo lỗi không để lộ tenant/chi nhánh ngoài quyền, câu lệnh SQL, schema nội bộ hay payload nguồn.

## 5. Yêu cầu API công khai (Public API requirements)

Phong cách API: REST + OpenAPI 3.1, sử dụng envelope thành công `{data, meta}` và envelope lỗi `{error}` chuẩn, UUID v7, mốc thời gian UTC, tiền tệ số nguyên minor và phân trang cursor.

### 5.1 Danh mục API

| Phương thức | Đường dẫn | Quyền hạn | Mục đích sử dụng |
|---|---|---|---|
| GET | `/api/v1/reports/dashboard/overview` | `report:read` | Các thẻ chỉ số KPI và ảnh chụp tổng quan đa tiện ích |
| GET | `/api/v1/reports/sales/daily` | `report:read` | Chuỗi dữ liệu doanh thu / số đơn / AOV theo ngày |
| GET | `/api/v1/reports/sales/by-branch` | `report:read` | Tổng hợp và so sánh doanh thu giữa các chi nhánh |
| GET | `/api/v1/reports/sales/hourly` | `report:read` | Doanh thu theo từng khung giờ trong ngày |
| GET | `/api/v1/reports/sales/by-employee` | `report:read` | Doanh thu theo nhân viên thực hiện checkout |
| GET | `/api/v1/reports/top-items` | `report:read` | Danh sách các món bán chạy nhất |
| GET | `/api/v1/reports/promotions/effectiveness` | `report:read` | Hiệu quả của các phiên bản chương trình khuyến mãi |
| GET | `/api/v1/reports/recommendations/effectiveness` | `report:read` | Tỷ lệ chấp thuận gợi ý và chỉ số AOV theo nhóm đối chứng |
| GET | `/api/v1/reports/freshness` | `report:read` | Kiểm tra độ tươi và tính đầy đủ của hình chiếu hiện tại |

Toàn bộ các API báo cáo công khai đều ở chế độ chỉ đọc (read-only). Máy chủ phân giải phạm vi quyền trên từng yêu cầu, kể cả các yêu cầu đọc từ cache và drill-down.

### 5.2 Phản hồi chuẩn chung (Common response)

```json
{
  "data": {
    "scope": {"type": "BRANCH", "branchIds": ["0192..."]},
    "period": {
      "from": "2026-10-01",
      "to": "2026-10-13",
      "timezone": "Asia/Ho_Chi_Minh"
    },
    "rows": [],
    "dataQuality": {
      "status": "FRESH",
      "watermark": "reporting:tenant:v901",
      "watermarkVersion": 901,
      "lastUpdatedAt": "2026-10-13T08:00:00Z",
      "projectionVersion": 12,
      "missingSources": [],
      "calculationVersion": "sales-metric-v1"
    }
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J...",
    "nextCursor": null
  }
}
```

### 5.3 Hợp đồng mã lỗi (Error contract)

| HTTP | Mã lỗi | Ý nghĩa |
|---:|---|---|
| 401 | `unauthorized` | Thiếu hoặc sai token JWT xác thực |
| 403 | `forbidden` | Vai trò hoặc quyền hạn bị từ chối |
| 422 | `invalid_report_filter` | Ngày tháng, khoảng thời gian, chiều dữ liệu hoặc giới hạn không hợp lệ |
| 409 | `scope_not_allowed` | Chi nhánh yêu cầu nằm ngoài phạm vi quyền đã phân giải |
| 409 | `report_not_ready` | Hình chiếu bắt buộc chưa sẵn sàng |
| 429 | `rate_limited` | Vượt giới hạn tần suất gọi API của người dùng hoặc tenant |
| 503 | `reporting_unavailable` | Cơ sở dữ liệu read model hoặc broker tạm thời gặp sự cố |

Trạng thái không có dữ liệu là kết quả nghiệp vụ thành công nằm trong `dataQuality.status=NO_DATA`, tuyệt đối không dùng mã lỗi hay từ chối phân quyền để thay thế.

## 6. Yêu cầu về hình chiếu và dữ liệu (Projection and data requirements)

### 6.1 Danh mục sự kiện đầu vào

| Tên sự kiện | Mục đích sử dụng bắt buộc trong Reporting |
|---|---|
| `OrderPaid.v1` | Dữ kiện đơn hàng thanh toán chuẩn, tổng tiền, dòng món, chi nhánh, ngày kinh doanh, quy kết nhân viên checkout, ảnh chụp khuyến mãi |
| `OrderRefunded.v1` / sự kiện điều chỉnh đã duyệt | Cập nhật doanh thu điều chỉnh hiện tại và điều chỉnh quy kết |
| `SuggestionShown.v1` | Mẫu số đo lường lượt hiển thị gợi ý món |
| `SuggestionAccepted.v1` | Tín hiệu quy kết chấp thuận gợi ý và liên kết đơn hàng |
| Ảnh chụp khuyến mãi/voucher hoặc bảng phân rã giảm giá đơn hàng | Đo lường chỉ số phiên bản khuyến mãi và phân bổ giảm giá |
| Sự kiện metadata chi nhánh / nhân viên | Ảnh chụp tên hiển thị an toàn, múi giờ, trạng thái và hình chiếu phạm vi |
| `AuthorizationContextChanged.v1` | Xóa gợi ý tối ưu / cache, không dùng làm thẩm quyền phân quyền duy nhất |

Thiếu sự kiện nguồn là vấn đề thuộc về hợp đồng hệ thống, không phải lý do để truy vấn trực tiếp vào database của producer. Reporting sẽ đánh dấu các dòng dữ liệu bị ảnh hưởng là `PARTIAL` hoặc `UNAVAILABLE` theo chính sách.

### 6.2 Các mô hình đọc (Read models)

Các read model tối thiểu:

- `sales_daily`: tenant, chi nhánh, ngày kinh doanh, loại tiền, số đơn thanh toán, gross/discount/net/VAT, các giá trị tính AOV, phiên bản hình chiếu / watermark.
- `sales_by_branch`: tenant, kỳ báo cáo, chi nhánh, các chỉ số và ảnh chụp tên chi nhánh an toàn.
- `sales_by_hour`: tenant, chi nhánh, ngày kinh doanh, giờ cục bộ, múi giờ, các chỉ số.
- `sales_by_employee`: tenant, chi nhánh, ảnh chụp nhân viên hoặc `UNASSIGNED`, kỳ/ngày báo cáo, các chỉ số.
- `top_items`: tenant, chi nhánh, ngày kinh doanh / ảnh chụp món, số lượng, doanh thu, thứ hạng.
- `promotion_effectiveness`: tenant, chi nhánh, phiên bản khuyến mãi / ảnh chụp luật, số đơn và các chỉ số doanh thu/giảm giá.
- `recommendation_effectiveness`: tenant, chi nhánh, kỳ báo cáo, các chiều gợi ý/mô hình cho hiển thị/chấp thuận kết hợp chỉ số AOV nhóm cấp đơn hàng.
- `projection_watermarks`: tenant / nguồn / phạm vi, watermark / phiên bản, tính đầy đủ, độ trễ lag, phiên bản hình chiếu.

Mọi bản ghi read model đều bắt buộc lưu ID sự kiện nguồn hoặc khóa nguồn tất định phục vụ việc phát lại và đối soát lỗi. Mọi dòng dữ liệu tổng hợp đều lưu trữ `calculationVersion`.

### 6.3 Trạng thái xử lý hình chiếu

Vòng đời trạng thái sự kiện trong Inbox:

`RECEIVED → VALIDATING → APPLIED | IGNORED_DUPLICATE | RETRY_WAIT | DLQ`

Vòng đời trạng thái tác vụ tái dựng hình chiếu:

`REQUESTED → RUNNING → PAUSED | COMPLETED | FAILED | CANCELLED`

Quy tắc:

- Consumer chỉ ack sự kiện sau khi transaction cơ sở dữ liệu cục bộ đã commit thành công.
- Sự kiện trùng lặp có thể quan sát được và hoàn toàn không gây tác dụng phụ (side-effect free).
- Sự kiện điều chỉnh đến sai thứ tự phải dựa trên phiên bản nguồn / thời điểm hiệu lực, tuyệt đối không phụ thuộc vào thứ tự nhận tin.
- Sự kiện có phiên bản không xác định sẽ bị cách ly vào hàng đợi DLQ.
- Tác vụ rebuild sử dụng một `projectionVersion` mục tiêu cụ thể; việc hoán đổi phiên bản diễn ra nguyên tử hoặc hiển thị trạng thái chất lượng dữ liệu là `REBUILDING`.

## 7. Yêu cầu phi chức năng (Non-functional requirements)

| Khu vực | Yêu cầu và chỉ số mục tiêu |
|---|---|
| Độ tươi dữ liệu | Dữ liệu bình thường đạt trạng thái `FRESH` trong vòng ≤ 1 phút; thời gian xử lý sự kiện p95 ≤ 30s dưới điều kiện broker bình thường |
| Độ trễ phản hồi | Tổng quan KPI p95 ≤ 1s; bảng dữ liệu có phân trang / drill-down p95 ≤ 2s |
| Tính sẵn sàng | API báo cáo chỉ đọc tuân thủ mục tiêu sẵn sàng của nền tảng; hình chiếu suy giảm trả về trạng thái rõ ràng, không bịa đặt số liệu |
| An toàn truy vấn | Phạm vi tương tác tối đa 12 tháng, tối đa 20 chi nhánh, các chiều dữ liệu và giới hạn có kiểm soát, phân trang bằng cursor |
| Tính nhất quán | Xử lý sự kiện ít nhất một lần có khử trùng lặp; không bao giờ tính trùng số liệu khi phát lại |
| Bảo mật | 100% các request đều mang phạm vi tenant/chi nhánh do máy chủ phân giải; vượt qua toàn bộ bài test phủ định xuyên phạm vi |
| Quyền riêng tư | Mặc định không trả về SĐT/tên khách hàng, thông tin thanh toán hay ID đơn hàng thô trong API báo cáo; tên nhân viên chỉ mở cho vai trò được phép |
| Bộ nhớ đệm (Cache) | Cache phía máy chủ tối đa 15s và phải được cách ly tuyệt đối theo phạm vi quyền / bộ lọc / watermark |
| Khả năng quan sát | Hiển thị rõ trace ID, độ trễ sự kiện, phiên bản hình chiếu, watermark, hàng đợi DLQ và độ trễ truy vấn |
| Lưu trữ dữ liệu | Các read model và lịch sử điều chỉnh tuân thủ chính sách Reporting; sự kiện nguồn vẫn thuộc quyền sở hữu của producer/kho lưu trữ |
| Khả năng mở rộng | Các bảng tổng hợp trước, chỉ mục và phân vùng hỗ trợ mượt mà phạm vi 12 tháng; việc chuyển sang Athena phải dựa trên đo lường thực tế |

## 8. Yêu cầu bảo mật và quyền riêng tư (Security and privacy requirements)

1. Quyết định phân quyền trực tuyến từ Identity là thẩm quyền tối cao cho mọi cuộc gọi API được bảo vệ.
2. Mọi câu truy vấn ở repository bắt buộc có điều kiện lọc tenant; truy vấn chi nhánh bắt buộc có điều kiện chi nhánh.
3. Báo cáo tổng hợp toàn tenant của Chủ chuỗi phải thể hiện rõ `scopeType=TENANT`; không dùng ký tự đại diện (wildcard) cho chi nhánh.
4. Danh sách chi nhánh do Quản lý yêu cầu bắt buộc phải là tập con của các chi nhánh được phân công; sự tồn tại của chi nhánh khác không được tiết lộ.
5. Việc tra cứu cache chỉ diễn ra sau khi đã xác thực phân quyền và khóa cache bắt buộc chứa phiên bản phạm vi quyền.
6. API không trả về PII khách hàng, định danh nhà cung cấp thanh toán hay ID đơn hàng thô trong phạm vi MVP.
7. Nhãn tên nhân viên chỉ được trả về cho Chủ chuỗi / Quản lý có thẩm quyền trên chi nhánh tương ứng.
8. Nhật ký log, trace và sự kiện chỉ chứa correlation ID và metadata tổng hợp an toàn; tuyệt đối không log payload sự kiện thô chứa dữ liệu cá nhân.
9. Mọi thao tác truy cập báo cáo, từ chối quyền, tái dựng/phát lại dữ liệu và xử lý lại DLQ đều phải được lưu vết kiểm toán (audit).
10. Giao diện dashboard bắt buộc phải khử độc (escape) và render các nhãn văn bản dưới dạng text thuần; không chấp nhận các đoạn mã HTML/SQL từ máy chủ.

## 9. Khả năng quan sát và vận hành (Observability and operations)

Các chỉ số giám sát bắt buộc:

- `reporting_projection_event_lag_seconds` phân theo nguồn và phạm vi.
- `reporting_projection_freshness_state_total` phân theo trạng thái và nguồn.
- `reporting_projection_dedupe_total` và `reporting_projection_dlq_total`.
- `reporting_projection_apply_latency_ms` và tỷ lệ lỗi xử lý.
- `reporting_api_latency_ms`, mã trạng thái, endpoint, loại phạm vi và tỷ lệ cache hit/miss.
- `reporting_query_rows`, khoảng thời gian, số chi nhánh và giới hạn kết quả (không chứa PII).
- `reporting_metric_reconciliation_delta` phục vụ đối soát số liệu chuẩn vàng và điều chỉnh.
- Số lượng đơn nhóm chấp thuận / đối chứng của gợi ý món và tỷ lệ thiếu liên kết correlation.

Hệ thống cảnh báo:

- Tỷ lệ dữ liệu đạt trạng thái `FRESH` giảm xuống dưới ngưỡng cam kết trong một khoảng thời gian.
- Độ trễ hình chiếu của nguồn / chi nhánh vượt quá 5 phút.
- Hàng đợi DLQ có bản tin hoặc đang có xu hướng tăng.
- Sai lệch đối soát chỉ số vượt quá ngưỡng cho phép.
- Phát hiện vi phạm khóa cache theo phạm vi hoặc số lượng từ chối phân quyền tăng đột biến.
- Độ trễ API p95 vi phạm ngưỡng cam kết cho KPI hoặc bảng dữ liệu.

## 10. Tiêu chí nghiệm thu và ma trận kiểm thử (Acceptance criteria and test matrix)

| Khu vực kiểm thử | Các kịch bản bắt buộc |
|---|---|
| Doanh thu | Trùng lặp sự kiện OrderPaid, sự kiện đến sai thứ tự, hoàn tiền/điều chỉnh, loại bỏ đối soát muộn của Payment, không có dữ liệu, từ chối/xử lý nhiều loại tiền |
| Múi giờ | Ranh giới nửa đêm theo chi nhánh, chính sách so sánh toàn tenant, ranh giới giờ mùa hè (DST), bỏ qua múi giờ của trình duyệt |
| Nhân viên | Đổi thu ngân nhanh, lịch sử nhân viên đã vô hiệu hóa, thiếu quy kết đưa vào UNASSIGNED, từ chối truy cập nhân viên ngoài chi nhánh |
| Khuyến mãi | Nhiều khuyến mãi trong một đơn, nhóm xung đột, phân bổ giảm giá, khoản chưa phân bổ, thay đổi phiên bản, hoàn tiền/điều chỉnh |
| Gợi ý món | Trùng hiển thị/chấp thuận, thiếu correlation, nhiều chấp thuận trong một đơn, tập đơn đã thanh toán nhóm chấp nhận/đối chứng, mẫu số đối chứng bằng 0, không tính trùng cấp đơn hàng |
| Hình chiếu | Phát lại ít nhất một lần, phiên bản lạ, lỗi schema, thử lại lỗi tạm thời, hàng đợi DLQ, tái dựng/hoán đổi phiên bản, các yêu cầu rebuild đồng thời |
| API | Bộ lọc phạm vi quyền, chống IDOR, phân trang bằng cursor, khoảng thời gian sai, vượt giới hạn, không có dữ liệu, trạng thái stale/partial/unavailable, kiểm tra cache sau khi thu hồi quyền |
| Hiệu năng | KPI p95 ≤ 1s, bảng dữ liệu p95 ≤ 2s, phạm vi 12 tháng, 20 chi nhánh, tải đồng thời trên dashboard, tỷ lệ cache hit/miss |
| Giao diện UI | Render mượt mà thẻ KPI, biểu đồ, bảng, drill-down, các trạng thái FRESH/STALE/PARTIAL/UNAVAILABLE/NO_DATA, bộ chọn chi nhánh theo vai trò, thông báo lỗi an toàn |
| Quyền riêng tư | Không có ID khách hàng / thanh toán / đơn hàng thô trong API/log/trace/sự kiện; nhãn nhân viên chỉ hiển thị trong phạm vi được ủy quyền |
| Tính tương thích | Phiên bản schema/sự kiện parent, kiểm thử theo hướng consumer, tương thích trường mở rộng, cách ly phiên bản lạ |

## 11. Các quyết định đã thống nhất từ bước Vision review

- Sử dụng các read model tổng hợp trước trên PostgreSQL cho MVP; không đưa Athena vào luồng phục vụ dashboard.
- Phạm vi dữ liệu trên dashboard: Chủ chuỗi xem toàn tenant hoặc tập chi nhánh; Quản lý chỉ xem các chi nhánh được phân công.
- Trạng thái độ tươi: `FRESH ≤ 1m`, `STALE > 1–5m`, `PARTIAL` khi thiếu nguồn, `UNAVAILABLE` khi không đọc được read model.
- Quy kết nhân viên: Ghi nhận cho nhân viên checkout active; thiếu quy kết đưa vào `UNASSIGNED/UNKNOWN` kèm cảnh báo.
- Nhóm khuyến mãi: Nhóm theo phiên bản khuyến mãi / luật bất biến, có phân bổ và nhóm dự phòng `UNALLOCATED_PROMOTION`.
- Nhóm đối chứng gợi ý: Đã hiển thị nhưng không chấp nhận trong cùng ngữ cảnh đủ điều kiện; mức tăng AOV tính ở cấp đơn hàng và mỗi đơn chỉ đếm 1 lần.
- Điều chỉnh lịch sử: Dashboard hiển thị hình chiếu hiện tại sau điều chỉnh kèm mốc `asOfWatermark`.
- Múi giờ: Xem một chi nhánh dùng múi giờ chi nhánh; xem nhiều chi nhánh dùng múi giờ báo cáo của tenant.
- Khoảng thời gian truy vấn: Mặc định 7 hoặc 30 ngày, tối đa 12 tháng; các bảng tổng hợp trước, chỉ mục và phân vùng đáp ứng giới hạn này.
- Chỉ số cam kết API: KPI p95 ≤ 1s; bảng/drill-down p95 ≤ 2s; cache máy chủ tối đa 15s với khóa an toàn theo phạm vi quyền.

## 12. Các điều chỉnh bổ sung bắt buộc đối với hợp đồng parent

### Order

- Bổ sung tham chiếu nhân viên checkout / thu ngân bất biến, ảnh chụp tên hiển thị và phiên bản quy kết vào sự kiện `OrderPaid.v1`.
- Bổ sung bảng phân rã giảm giá khuyến mãi có đánh phiên bản hoặc tham chiếu phiên bản khuyến mãi đã áp dụng đủ để Reporting phân bổ.
- Định nghĩa rõ ngữ nghĩa các sự kiện hoàn tiền / điều chỉnh được phê duyệt mà Reporting tiêu thụ.

### Loyalty / Promotion

- Công bố phiên bản khuyến mãi / luật bất biến, nhóm xung đột, phân bổ giảm giá đã áp dụng và khóa nguồn voucher/khuyến mãi.
- Bảo toàn phiên bản đã áp dụng trong ảnh chụp đơn đã thanh toán; không bao giờ yêu cầu Reporting phải tự tính toán lại luật khuyến mãi trong tương lai.

### Recommendation

- Bảo đảm các sự kiện `SuggestionShown.v1` và `SuggestionAccepted.v1` mang ID tương tác ổn định, correlation đơn hàng / giỏ hàng, phiên bản gợi ý / luật / mô hình và bằng chứng chấp thuận.
- Định nghĩa rõ hành vi phát lại / khử trùng lặp sự kiện và trạng thái quy kết đang chờ cho đến khi sự kiện `OrderPaid.v1` tới.

### Identity

- Bổ sung các quyền `report:read` và quyền hạn chế `reporting:operate`.
- Xác nhận hợp đồng phân giải phạm vi quyền hỗ trợ giải quyết toàn tenant / tập chi nhánh và ngữ nghĩa thu hồi quyền cho từng yêu cầu báo cáo.

### POS Platform / Audit

- Bổ sung danh mục API, sự kiện và read model của Reporting vào hợp đồng nền tảng.
- Bổ sung schema các sự kiện audit cho việc truy cập báo cáo đã che mờ thông tin, lỗi hình chiếu và tác vụ phát lại dữ liệu.

## 13. Cổng phê duyệt (Approval gate)

- **Trạng thái hiện tại:** Approved with warnings — Người dùng đã phê duyệt.
- **Yêu cầu trước khi triển khai:** Cụ thể hóa các hợp đồng máy đọc, các điều chỉnh parent, các fixture kiểm thử và các cổng migration từ Contracts/Finalization.
- **Quy tắc triển khai:** Tuyệt đối không lập trình controller / query / projection trực tiếp từ văn bản mô tả; bắt buộc sinh file OpenAPI, JSON Schema cho sự kiện, fixture chỉ số và hợp đồng migration trước khi viết code.
