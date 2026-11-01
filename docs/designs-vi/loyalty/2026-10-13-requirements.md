# Module 5 — Dịch Vụ Khách Hàng Thân Thiết & Khuyến Mãi (Loyalty Service): Yêu Cầu Kỹ Thuật & Nghiệp Vụ (Requirements)

**Ngày:** 2026-10-13  
**Trạng thái:** Bản thảo — Chờ Spec Review (Draft — Pending Spec Review)  
**Tài liệu tầm nhìn (Vision):** [Loyalty Vision](./2026-10-13-vision.md)  
**Phạm vi:** M — Luồng Service Tiêu chuẩn (Standard Service Flow)  
**Module:** Khách hàng thân thiết & khuyến mãi (Loyalty Service)

---

## 1. Mục Tiêu & Ranh Giới Trách Nhiệm

Loyalty Service chịu trách nhiệm quản lý hồ sơ khách hàng theo từng tenant, sổ cái điểm thưởng bất biến (append-only points ledger), số dư điểm khả dụng, hệ thống phân hạng thành viên, mã giảm giá (voucher) và công cụ tính toán quy tắc khuyến mãi (promotion rule engine).

Order Service sở hữu vòng đời đơn hàng, bản chụp giỏ hàng (basket snapshot), tổng tiền thanh toán cuối cùng và phiên checkout. Payment Service sở hữu lượt thanh toán và trạng thái giao dịch với cổng thanh toán. Loyalty Service tuyệt đối không truy vấn trực tiếp cơ sở dữ liệu của Order, Payment hoặc Catalog và không tự ý thay đổi tổng tiền thanh toán của đơn hàng.

### 1.1 Phạm Vi Giai Đoạn P0 (Cốt Lõi)
- Tìm kiếm và khởi tạo hồ sơ khách hàng theo số điện thoại trong phạm vi tenant.
- Tích lũy điểm thưởng từ sự kiện `OrderPaid.v1` dựa trên giá trị đủ điều kiện do Order Service cung cấp.
- Đổi điểm thông qua quy trình hai bước: Giữ điểm (`Hold`) → Xác nhận trừ (`Commit`) hoặc Giải phóng (`Release`).
- Thu hồi điểm thưởng đối ứng khi có sự kiện hoàn tiền toàn bộ đơn hàng đã thanh toán.
- Triển khai Transactional Outbox, consumer sự kiện idempotent, nhật ký kiểm toán và cô lập dữ liệu đa tenant.

### 1.2 Phạm Vi Giai Đoạn P1 (Mở Rộng Quản Lý Thân Thiết)
- Phân hạng thành viên `SILVER`, `GOLD`, `DIAMOND` dựa trên chu kỳ trượt 12 tháng gần nhất (rolling 12 months).
- Quản lý các lô điểm (point lots) và cơ chế hết hạn điểm theo mô hình FIFO (vào trước xuất trước).
- Quản lý hạn mức voucher, tạm giữ lượt dùng (reservation) và chính thức tiêu thụ (consumption).
- Công cụ quy tắc khuyến mãi: Giảm giá phần trăm (`% discount`), mua X tặng Y (`buy X get Y`), và khung giờ vàng (`happy hour`).
- Cơ chế giải quyết xung đột khuyến mãi tất định (deterministic conflict resolution) khi có nhiều quy tắc cùng thỏa mãn.
- Hỗ trợ đổi điểm phân bổ / hoàn điểm một phần theo các luồng đã hỗ trợ tại Order Service.
- Giao diện tra cứu lịch sử điểm, voucher và biến động hạng thành viên cho nhân viên thu ngân/quản lý.

### 1.3 Phạm Vi Giai Đoạn P2 (Cổng Khách Hàng)
- Cổng tra cứu khách hàng chỉ đọc (Customer portal read-only) để xem số dư điểm, lịch sử giao dịch, hạng thẻ và danh sách voucher.
- Cơ chế xác thực khách hàng bằng mã OTP qua điện thoại, hoàn toàn tách biệt khỏi hệ thống Cognito của nhân viên.

### 1.4 Ngoài Phạm Vi (Out of Scope)
- Loyalty Service không chốt trạng thái thanh toán, không phát sự kiện `OrderPaid.v1` và không hoàn tất đơn hàng.
- Loyalty Service không tính toán lại giá bán món ăn trong Catalog, thuế VAT, phí vận chuyển, tiền tip hay tổng tiền cuối cùng của đơn.
- Giai đoạn P0 không hỗ trợ gộp hồ sơ khách hàng, điều chỉnh điểm thủ công, hoàn tiền một phần từng món hay rút điểm thành tiền mặt (cash-out).
- Cổng khách hàng P2 không hỗ trợ đổi điểm trực tiếp, không áp mã voucher tại checkout hay tự ý chỉnh sửa thông tin hồ sơ cá nhân.

---

## 2. Các Tác Nhân (Actors) & Phân Quyền Hệ Thống

| Tác nhân (Actor) | Quyền hạn chính |
|---|---|
| **Thu ngân (`cashier`)** | Tìm kiếm/tạo khách hàng trong tenant; xem số dư điểm/hạng/voucher; tạo báo giá khuyến mãi (quote) và yêu cầu giữ điểm cho phiên checkout thuộc chi nhánh được phân công. |
| **Quản lý cửa hàng (`manager`)** | Toàn bộ quyền của thu ngân trong phạm vi chi nhánh; tra cứu lịch sử giao dịch điểm và thực hiện các thao tác vận hành được chính sách phân quyền cho phép. |
| **Chủ chuỗi (`owner`)** | Toàn quyền cấp tenant; quản lý cấu hình tiêu chí xét hạng, tỷ lệ tích/đổi điểm, chính sách hết hạn, chương trình khuyến mãi và mã voucher. |
| **Order Service / Step Functions Saga** | Gọi API báo giá khuyến mãi, tạo/commit/release yêu cầu giữ điểm; gửi các sự kiện đơn hàng thông qua tài khoản định danh hệ thống (service/workload identity). |
| **Payment Service** | Không trực tiếp thay đổi số dư điểm; sự kiện hoàn tiền/thu hồi bắt buộc đi qua hợp đồng của Order Service. |
| **Cổng khách hàng (`customer-portal`)** | Chỉ đọc dữ liệu của chính khách hàng sau khi xác thực OTP thành công; không có quyền của nhân viên hay quyền truy cập xuyên tenant. |
| **Tiến trình nền (`loyalty-worker`)** | Tiêu thụ sự kiện từ hàng đợi, quét hết hạn các lô điểm, tính toán lại hạng thành viên, đối soát các đơn giữ điểm treo; hoạt động theo định danh worker. |

*Nguyên tắc bảo mật:* Mọi thao tác đọc/ghi đều bắt buộc phải kiểm tra `tenantId` ở cả tầng Service và tầng Repository/Query. Khóa ID trên đường dẫn URL không phải là bằng chứng sở hữu. Quyền chi nhánh (`branchId`) chỉ áp dụng cho thao tác của nhân viên tại quầy; hồ sơ khách hàng và số dư điểm thuộc phạm vi sở hữu của toàn tenant và có thể được sử dụng ở bất kỳ chi nhánh nào trong chuỗi.

---

## 3. Các Trường Hợp Sử Dụng (Use Cases)

| Mã Use Case | Mức độ | Tác nhân | Tên Use Case | Kết quả chính |
|---|---|---|---|---|
| **LOY-UC-01** | P0 | Thu ngân / Quản lý | Tìm kiếm khách hàng theo SĐT | Trả về thông tin khách hàng đã che mờ (masked), số dư điểm khả dụng, hạng thành viên và các voucher đủ điều kiện trong tenant. |
| **LOY-UC-02** | P0 | Thu ngân / Quản lý | Tạo hồ sơ khách hàng mới | Chuẩn hóa số điện thoại E.164, lưu số điện thoại mã hóa + mã băm tra cứu HMAC, đảm bảo không tạo trùng lặp. |
| **LOY-UC-03** | P0 | Consumer sự kiện Order | Tích điểm sau khi đơn thanh toán | Tạo dòng sổ cái `EARNED` đúng một lần duy nhất theo khóa nguồn nghiệp vụ (unique source key). |
| **LOY-UC-04** | P0 | Checkout Saga | Đổi điểm thưởng trừ tiền | Giữ điểm nguyên tử (`HELD`), commit trừ điểm khi nhận `OrderPaid`, hoặc giải phóng (`RELEASED`) khi thất bại/timeout/hủy. |
| **LOY-UC-05** | P0 | Consumer sự kiện Order | Thu hồi điểm khi hoàn tiền đơn | Tạo dòng sổ cái `REVERSED` hoặc khôi phục điểm đổi đối ứng theo mã `refundId`, đảm bảo tính idempotent. |
| **LOY-UC-06** | P1 | Tiến trình nền Scheduler | Hết hạn các lô điểm thưởng | Quét và tạo dòng sổ cái `EXPIRED` cho số điểm còn lại thực tế chưa bị giữ chỗ. |
| **LOY-UC-07** | P1 | Tiến trình nền Worker | Tính toán lại hạng thành viên | Cập nhật hạng thành viên và phát sự kiện `MembershipTierChanged.v1` nếu có sự thay đổi thứ hạng. |
| **LOY-UC-08** | P1 | Order Service / Saga | Báo giá chiết khấu khuyến mãi | Đánh giá toàn bộ quy tắc khuyến mãi/voucher và trả về cơ cấu chiết khấu chi tiết có tính tất định. |
| **LOY-UC-09** | P1 | Order Service / Saga | Tạm giữ & tiêu thụ voucher | Giữ voucher theo lượt checkout, chính thức tiêu thụ sau khi đơn thanh toán, giải phóng khi checkout thất bại. |
| **LOY-UC-10** | P2 | Khách hàng cá nhân | Tra cứu cổng Loyalty Portal | Đăng nhập OTP an toàn, chỉ đọc dữ liệu điểm, lịch sử, voucher của chính bản thân khách hàng. |

---

## 4. Yêu Cầu Nghiệp Vụ & Các Bất Biến Cốt Lõi (Business Invariants)

### 4.1 Quản Lý Hồ Sơ Khách Hàng (Customer Profile)
1. Số điện thoại bắt buộc phải được chuẩn hóa về định dạng quốc tế E.164 theo chính sách quốc gia/tenant trước khi tra cứu hoặc tạo mới.
2. Hệ thống lưu số điện thoại dưới dạng mã hóa đối xứng để hiển thị đối soát nội bộ và lưu chuỗi băm HMAC có muối (peppered hash) để tra cứu; tuyệt đối không dùng số điện thoại dạng văn bản thô (plaintext) làm khóa chính hoặc ghi vào nhật ký log.
3. Ràng buộc duy nhất cơ sở dữ liệu: `(tenantId, phoneHash)`.
4. Phản hồi cho nhân viên chỉ hiển thị số điện thoại đã che mờ (ví dụ: `+84****1234`). Số điện thoại đầy đủ chỉ được dùng nội bộ tuân thủ chính sách bảo vệ dữ liệu PII và không trả về qua API thông thường.
5. Việc tìm kiếm không được phép tiết lộ sự tồn tại của khách hàng ở tenant khác; `tenantId` luôn được trích xuất từ token xác thực tin cậy.
6. Thao tác tạo lại cùng một số điện thoại đã chuẩn hóa trong cùng tenant sẽ trả về hồ sơ hiện có hoặc lỗi xung đột idempotent, tuyệt đối không tạo bản ghi khách hàng thứ hai.
7. Giai đoạn P0 không tự động gộp (merge) hồ sơ khách hàng. Nếu phát hiện dữ liệu trùng do import dữ liệu cũ, phải giữ nguyên vết kiểm toán và xử lý bằng công cụ quản trị riêng.
8. Hồ sơ khách hàng không chứa bản chụp giỏ hàng hay thông tin thanh toán; chỉ lưu mã tham chiếu và số liệu tổng hợp cần thiết cho nghiệp vụ Loyalty.

### 4.2 Tích Điểm Từ Sự Kiện `OrderPaid` (Points Accrual)
1. Sự kiện `OrderPaid.v1` là "nguồn chân lý duy nhất" để kích hoạt việc tích lũy điểm thưởng; sự kiện thanh toán `PaymentPaid.v1` tuyệt đối không trực tiếp tạo điểm.
2. Payload của sự kiện bắt buộc phải mang trường `loyaltyEligibleAmountMinor` (số tiền đủ điều kiện tích điểm) và `loyaltyPolicyVersion` do Order Service tính toán từ snapshot bất biến của đơn hàng.
3. `loyaltyEligibleAmountMinor` là giá trị hàng hóa thực tế sau khi trừ chiết khấu, trước thuế VAT; không bao gồm phí vận chuyển, phụ thu, tiền tip và phần giá trị thanh toán bằng voucher.
4. Tỷ lệ tích điểm (Earn ratio) được cấu hình theo từng tenant và phiên bản chính sách. Loyalty Service thực hiện tính toán số điểm bằng số học nguyên và làm tròn xuống.
5. Mỗi sự kiện chỉ được tạo tối đa một giao dịch tích điểm trong sổ cái dựa trên khóa duy nhất: `(tenantId, sourceType = 'ORDER_PAID', orderId)`.
6. Việc retry sự kiện, gửi lặp sự kiện hoặc khởi động lại worker consumer không được phép tạo thêm bản ghi `EARNED` thứ hai hay làm tăng số dư điểm hai lần.
7. Nếu khách hàng chưa tồn tại hoặc sự kiện thiếu mã tham chiếu khách hàng hợp lệ, consumer không được âm thầm bỏ qua: phải ghi nhận lỗi, retry có giới hạn hoặc chuyển vào hàng đợi chết DLQ kèm cảnh báo vận hành.
8. Bản ghi sổ cái `EARNED` bắt buộc phải lưu: `orderId`, `sourceEventId`, `policyVersion`, số tiền đủ điều kiện, số điểm tích được và dấu thời gian phát sinh từ nguồn.

### 4.3 Sổ Cái Điểm & Bảng Tổng Hợp Số Dư (Ledger & Point Balance)
1. Sổ cái điểm thưởng là bảng dữ liệu bất biến chỉ ghi thêm (append-only); tuyệt đối không cập nhật (`UPDATE`) hay xóa (`DELETE`) dữ liệu lịch sử.
2. Các loại giao dịch trong sổ cái gồm: `EARNED` (+), `REDEEMED` (-), `REVERSED` (-/+), `EXPIRED` (-). Mỗi bản ghi bao gồm: `entryId`, `tenantId`, `customerId`, số điểm, loại nguồn/mã nguồn, thời điểm phát sinh, idempotency key và liên kết bản ghi gốc.
3. Nghiệp vụ giữ điểm phục vụ thanh toán được quản lý riêng tại bảng `point_holds`, không phải là một giao dịch đã trừ trong sổ cái.
4. Bản ghi giữ điểm có các trạng thái: `HELD`, `COMMITTED`, `RELEASED`, `EXPIRED`, gắn duy nhất với `checkoutAttemptId` và khách hàng.
5. Trạng thái `HELD` làm giảm số dư khả dụng (`availablePoints`) ngay lập tức nhưng không làm giảm tổng điểm tích lũy trọn đời (`lifetimeEarnedPoints`) và không ghi bản ghi `REDEEMED` vào sổ cái.
6. Thao tác Commit chuyển trạng thái hold sang `COMMITTED` và ghi đúng một bản ghi `REDEEMED` vào sổ cái; thao tác Release chuyển hold sang `RELEASED` và hoàn trả lại số dư điểm khả dụng.
7. Bảng tổng hợp số dư là projection có thể tái tạo hoàn toàn chính xác từ lịch sử sổ cái và các đơn giữ điểm đang hoạt động.
8. Mọi thao tác nguồn đều có idempotency key và ràng buộc duy nhất trong cơ sở dữ liệu; việc khử trùng lặp ở tầng ứng dụng là chưa đủ.
9. Tuyệt đối không cho phép số dư khả dụng bị âm. Việc giữ điểm đồng thời từ nhiều quầy POS phải sử dụng database transaction với khóa dòng bi quan hoặc kiểm soát phiên bản lạc quan có retry rõ ràng.

### 4.4 Quy Trình Đổi Điểm Trừ Tiền (Points Redemption)
1. Điểm thưởng chỉ được dùng để giảm trừ trực tiếp trên hóa đơn đơn hàng, tuyệt đối không quy đổi thành tiền mặt (no cash-out).
2. Tỷ lệ đổi điểm (Redemption rate) được cấu hình theo tenant (ví dụ: `100 điểm = 10.000 VND`), lưu kèm phiên bản chính sách.
3. Order Service / Step Functions gửi thông tin khách hàng, mã lượt checkout, số điểm muốn đổi và ngữ cảnh giá trị đơn; Loyalty Service trả về báo giá đổi điểm hoặc kết quả giữ điểm.
4. Làm tròn theo điểm nguyên. Mức giảm giá tối đa tuân thủ chính sách tenant: mặc định không vượt quá tổng giá trị hàng hóa sau khuyến mãi, và không áp dụng cho phí vận chuyển, tiền tip hay phụ thu.
5. Vòng đời bắt buộc: `Báo giá / Thẩm định → Giữ điểm (Hold) → Thanh toán thành công → Commit`, hoặc `Release` khi thanh toán thất bại, timeout, hoặc đơn bị hủy.
6. Bản ghi giữ điểm bắt buộc có thời hạn hết hạn `expiresAt` kế thừa từ thời hạn của checkout attempt từ Order Service, không dùng TTL độc lập gây lệch vòng đời.
7. Retry với cùng checkout attempt sẽ không tạo thêm lượt giữ điểm thứ hai; gửi payload khác với cùng idempotency key sẽ bị từ chối với lỗi xung đột `409 Conflict`.
8. Nếu lô điểm có hạn sử dụng bị hết hạn trong lúc đang giữ chỗ, số điểm đó được bảo vệ cho tới khi lượt giữ kết thúc. Khi release, phần điểm đã quá hạn sẽ bị trừ hết hạn ngay lập tức; phần điểm chưa quá hạn được hoàn trả về số dư khả dụng.

### 4.5 Thu Hồi & Hoàn Điểm Khi Hủy / Hoàn Đơn (Points Reversal)
1. Đơn hàng bị hủy trước khi thanh toán (`OrderCancelled`) không phát sinh thu hồi điểm vì chưa từng có giao dịch `EARNED`.
2. Sự kiện `OrderRefunded.v1` với thuộc tính `refundType = 'FULL'` là trigger trong giai đoạn P0 để thu hồi toàn bộ đơn hàng đã thanh toán.
3. Sự kiện bắt buộc mang các thông tin: `refundId`, `orderId`, ngữ cảnh tenant/chi nhánh, mã tham chiếu đơn thanh toán gốc, cùng correlation/causation ID.
4. Loyalty Service tra cứu toàn bộ các giao dịch sổ cái và đơn giữ điểm liên quan đến đơn hàng đó để tạo các bản ghi đối ứng mới, tuyệt đối không chỉnh sửa bản ghi cũ.
5. Số điểm đã cộng khi mua hàng sẽ bị thu hồi bằng một bản ghi `POINTS_REVERSED` mang giá trị âm liên kết với dòng `EARNED` ban đầu. Số điểm khách đã đổi trong đơn hàng đó sẽ được hoàn trả lại thông qua một dòng ghi có đối ứng và tạo lô điểm mới; các lượt giữ điểm chưa commit sẽ được giải phóng (`RELEASED`).
6. Cùng một mã `refundId` chỉ được xử lý đúng một lần. Sự kiện gửi lặp hoặc retry không làm thu hồi điểm nhiều lần.
7. Hoàn tiền một phần (Partial refund) và thu hồi điểm theo từng dòng món ăn thuộc phạm vi P1; hợp đồng sự kiện phải chuẩn bị sẵn cấu trúc cho `refundType = 'PARTIAL'`, phân bổ tiền/dòng món và tỷ lệ điểm tương ứng, nhưng chưa được xử lý gộp chung như hoàn toàn bộ.

### 4.6 Phân Hạng Thành Viên (Membership Tier)
1. Hệ thống hỗ trợ 3 hạng thành viên chuẩn: `SILVER`, `GOLD`, `DIAMOND`; tiêu chí xét hạng và ngưỡng định mức do từng tenant cấu hình linh hoạt.
2. Tenant được chọn một trong hai chỉ số xét hạng: Tổng chi tiêu hợp lệ (`ELIGIBLE_SPEND_MINOR`) hoặc Tổng điểm tích lũy (`EARNED_POINTS`).
3. Khung thời gian đánh giá là chu kỳ trượt 12 tháng gần nhất (rolling 12 months). Các đơn hàng bị hoàn tiền, thu hồi hoặc hủy bỏ sẽ bị loại trừ khỏi chỉ số xét hạng.
4. Tiến trình tính toán lại hạng chạy tự động sau các sự kiện biến động liên quan và kết hợp với job quét định kỳ hàng ngày để khắc phục sai lệch hoặc sự kiện bị bỏ sót.
5. Hạng thành viên có thể được thăng hạng (upgrade) hoặc hạ hạng (downgrade); hạng mới có hiệu lực ngay khi tính toán thành công. Mặc định khách hàng mới hoặc chưa đủ điều kiện sẽ ở hạng `SILVER`.
6. Lưu trữ hạng hiện tại trực tiếp trên hồ sơ khách hàng để đọc nhanh và lưu lịch sử thay đổi hạng dạng append-only; mỗi lần đổi hạng phát sự kiện `MembershipTierChanged.v1` đúng một lần.
7. Các quy tắc ưu đãi theo hạng thành viên bắt buộc phải đọc hạng hiện tại từ Loyalty Service, không được tự suy diễn từ lịch sử đơn hàng.

### 4.7 Thuật Toán Quản Lý Lô Điểm & Hết Hạn FIFO (FIFO Expiry)
1. Mỗi giao dịch tích điểm `EARNED` sẽ sinh ra một lô điểm (`point_lot`) lưu: `earnedAt`, `remainingPoints`, `expiresAt` và mã tham chiếu dòng sổ cái nguồn.
2. Mặc định lô điểm hết hạn sau 12 tháng kể từ ngày tích lũy; tenant có thể cấu hình chính sách thời hạn mới cho các lô điểm tạo mới trong tương lai.
3. Khi đổi điểm, hệ thống phân bổ trừ điểm theo nguyên tắc: Ưu tiên lô có `expiresAt` sớm nhất; nếu bằng nhau thì ưu tiên lô có `earnedAt` sớm hơn, sau đó xét đến `lotId`.
4. Điểm đang ở trạng thái giữ chỗ (`HELD`) không bị quét hết hạn trong thời gian lượt giữ còn hiệu lực.
5. Job quét hết hạn điểm chạy hàng ngày theo múi giờ của tenant một cách idempotent. Mỗi lô điểm hết hạn sẽ tạo bản ghi `EXPIRED` trong sổ cái cho đúng số điểm còn lại thực tế.
6. Job tuyệt đối không được làm hết hạn phần điểm đã được commit trừ, phần điểm đã bị thu hồi hoặc phần điểm đang bị giữ chỗ.
7. Các giao dịch hết hạn điểm và đổi điểm diễn ra đồng thời phải được tuần tự hóa (serialize) theo từng khách hàng/lô điểm để không làm âm số dư.

### 4.8 Công Cụ Khuyến Mãi & Quản Lý Voucher (Promotions & Vouchers)
1. Order Service gửi bản chụp giỏ hàng, ngữ cảnh chi nhánh/thời gian/kênh bán, thông tin khách hàng/hạng thẻ, mã voucher yêu cầu và mã lượt checkout; Loyalty Service tính toán và trả về bảng báo giá `PromotionQuote`.
2. Báo giá khuyến mãi bao gồm: tổng tiền chiết khấu, phân bổ chiết khấu theo từng dòng món ăn, danh sách promotion ID, phiên bản quy tắc áp dụng, voucher đã áp dụng, thời hạn báo giá và danh sách giải thích các quy tắc bị từ chối.
3. Order Service là cơ quan có thẩm quyền quyết định tổng tiền cuối cùng; Order Service thẩm định lại báo giá tại thời điểm checkout và chụp toàn bộ thông tin báo giá/phiên bản quy tắc vào đơn hàng.
4. Các quy tắc khuyến mãi P1 hỗ trợ: Giảm giá phần trăm theo dòng hoặc theo đơn kèm mức chi tối thiểu/mức giảm tối đa; Mua X tặng Y theo món/biến thể/số lượng; và Khung giờ vàng theo múi giờ chi nhánh/ngày trong tuần/khoảng ngày/khung giờ trong ngày.
5. Phiên bản quy tắc khuyến mãi là bất biến (immutable) sau khi phát hành. Mọi chỉnh sửa nội dung phải tạo phiên bản mới để đảm bảo tính giải trình và khả năng tái hiện lịch sử.
6. **Quy tắc giải quyết xung đột:** Trong cùng một nhóm xung đột (`conflictGroup`), chỉ chọn đúng một quy tắc duy nhất theo thứ tự ưu tiên:
   - Phương án mang lại mức giảm giá nhiều nhất cho khách hàng.
   - Nếu mức giảm bằng nhau: chọn quy tắc có độ ưu tiên (`priority`) cao hơn.
   - Nếu độ ưu tiên vẫn bằng nhau: chọn quy tắc có `promotionId` nhỏ hơn (sắp xếp ổn định).
7. Các quy tắc thuộc các nhóm xung đột khác nhau chỉ được phép cộng dồn nếu tất cả các quy tắc liên quan đều có cờ `stackable = true`. Tổng số tiền chiết khấu không bao giờ được vượt quá giá trị đơn hàng (không làm tiền phải trả bị âm).
8. Đối với chương trình mua X tặng Y, phần sản phẩm được tặng miễn phí có giá trị bằng 0 và không phát sinh tích điểm thưởng.
9. Voucher mặc định nằm chung nhóm xung đột với các chương trình khuyến mãi khác; tenant có thể cấu hình cho phép voucher cộng dồn với chương trình khung giờ vàng.
10. Mã voucher được chuẩn hóa viết hoa và là duy nhất trong toàn tenant. Cấu hình hạn mức gồm: tổng số lượt dùng toàn hệ thống, số lượt dùng tối đa trên mỗi khách hàng, thời gian hiệu lực, phạm vi chi nhánh, kênh bán và giá trị đơn hàng tối thiểu.
11. Lệnh tạm giữ voucher (Voucher reservation) gắn liền với `checkoutAttemptId` và có thời hạn `expiresAt` từ Order Service; thao tác tạm giữ không làm tăng bộ đếm lượt đã sử dụng chính thức.
12. Chỉ khi nhận sự kiện `OrderPaid.v1` thì voucher mới chính thức được tiêu thụ (`consumed`) và tăng bộ đếm lượt dùng. Nếu checkout thất bại, timeout hoặc hủy đơn thì giải phóng lượt giữ; gửi lại yêu cầu với cùng checkout attempt không làm tăng lượt sử dụng.
13. Các thao tác giữ, tiêu thụ và giải phóng voucher phải ngăn chặn triệt để tình trạng race condition đối với các hạn mức tổng/khách hàng bằng ràng buộc cơ sở dữ liệu hoặc transaction cập nhật bộ đếm nguyên tử.

### 4.9 Cổng Tra Cứu Khách Hàng Giai Đoạn P2 (Customer Portal)
1. Khách hàng đăng nhập bằng mã OTP gửi về số điện thoại đã xác minh; phiên đăng nhập và định danh hoàn toàn tách biệt khỏi hệ thống Cognito của nhân viên cửa hàng.
2. Cổng thông tin chỉ cấp quyền đọc (read-only): xem số dư điểm hiện tại, tóm tắt điểm đang giữ/đã hết hạn, lịch sử biến động điểm, hạng thành viên và danh sách voucher khả dụng của chính khách hàng đó.
3. Cổng thông tin tuyệt đối không cho phép đổi điểm, tạo lượt giữ điểm, áp voucher tại quầy, chỉnh sửa thông tin cá nhân hay xem dữ liệu của khách hàng khác.
4. Endpoint gửi mã OTP áp dụng giới hạn tần suất nghiêm ngặt (rate limit), số lần thử tối đa, thời gian giãn cách (cooldown) và ghi nhật ký kiểm toán. Phản hồi API không được tiết lộ số điện thoại đó đã tồn tại trong hệ thống hay chưa (chống enumeration).
5. Token phiên làm việc bắt buộc phải liên kết chặt chẽ với đúng `tenantId` và `customerId`, tuyệt đối không thể sử dụng để gọi các API nội bộ của nhân viên.

---

## 5. Yêu Cầu Về Luồng Xử Lý Sự Kiện (Event & Flow Requirements)

### 5.1 Luồng `OrderPaid` → Tích Điểm Thưởng
1. Order Service phát sự kiện `OrderPaid.v1` sau khi thanh toán thành công và đơn hàng đã được chốt (finalize) theo hợp đồng nền tảng.
2. Consumer của Loyalty Service kiểm tra tính hợp lệ của schema, ngữ cảnh tenant/khách hàng, trường `loyaltyEligibleAmountMinor`, phiên bản chính sách và tính duy nhất của nguồn.
3. Trong cùng một transaction cơ sở dữ liệu: Ghi dòng sổ cái `EARNED`, tạo các lô điểm mới, cập nhật bảng tổng hợp số dư và ghi nhận sự kiện outbox nếu có.
4. Chỉ xác nhận đã nhận sự kiện (acknowledge message) sau khi database transaction commit thành công. Các lỗi tạm thời sẽ được retry có backoff; lỗi dữ liệu không hợp lệ được đưa vào hàng đợi chết DLQ kèm lý do và kích hoạt cảnh báo vận hành.

### 5.2 Luồng Đổi Điểm Tại Quầy Checkout
1. POS / Order Service gửi yêu cầu báo giá kèm thông tin khách hàng và số điểm muốn đổi.
2. Loyalty Service kiểm tra số dư khả dụng, thuật toán phân bổ lô điểm theo hạn dùng, tỷ lệ quy đổi, hạn mức tối đa và phạm vi tenant/khách hàng.
3. Loyalty Service thực hiện giữ điểm một cách nguyên tử gắn với `checkoutAttemptId` và trả về mã giữ điểm (`holdId`), số điểm giữ, số tiền được giảm và thời hạn hết hạn.
4. Khi nhận được `OrderPaid.v1`, hệ thống commit lượt giữ điểm và xác nhận tiêu thụ voucher; handler xử lý sự kiện đảm bảo tính idempotent.
5. Nếu thanh toán thất bại, đơn hàng bị hủy hoặc phiên checkout hết hạn, hệ thống gọi lệnh giải phóng lượt giữ điểm và voucher; tiến trình dọn dẹp nền đóng vai trò là phương án dự phòng, không thay thế cho đường dẫn lệnh trực tiếp.

### 5.3 Luồng Hoàn Tiền Toàn Bộ → Thu Hồi Điểm (Full Refund → Reversal)
1. Order Service phát sự kiện `OrderRefunded.v1` tuân thủ hợp đồng hoàn tiền toàn bộ đơn hàng.
2. Loyalty Service tra cứu lịch sử theo `orderId`, xác thực tenant và tính duy nhất của mã hoàn tiền `refundId`.
3. Tạo các dòng sổ cái đối ứng và lô điểm mới, khôi phục lại các khoản điểm đổi đã commit, đồng thời cập nhật số dư và chỉ số xét hạng thành viên trong cùng một ranh giới nhất quán.
4. Acknowledge sự kiện sau khi commit; sự kiện hoàn tiền trùng lặp tuyệt đối không tạo thêm bản ghi đối ứng thứ hai.

---

## 6. Yêu Cầu Giao Diện Lập Trình Ở Cấp Nghiệp Vụ (API Capabilities)

Chi tiết cụ thể của API contracts sẽ được hoàn thiện ở tài liệu Contracts. Các năng lực bắt buộc phải có:

| Năng lực nghiệp vụ | Tác nhân gọi | Kết quả mong đợi |
|---|---|---|
| **Tra cứu / Tạo khách hàng** | Nhân viên quầy POS | Trả về thông tin khách đã che mờ, số dư khả dụng, hạng thẻ và danh sách voucher. |
| **Báo giá khuyến mãi (Promotion quote)** | Order Service / Saga | Báo giá chiết khấu tất định kèm giải thích chi tiết cơ cấu giảm giá. |
| **Giữ điểm thưởng (Point hold)** | Order Service / Saga | Trả về mã hold ID, số điểm giữ, số tiền chiết khấu và thời hạn hết hạn. |
| **Xác nhận / Hủy giữ điểm** | Order Service / Consumer | Chuyển đổi trạng thái hold đảm bảo tính idempotent theo checkout attempt hoặc event. |
| **Giữ / Tiêu thụ / Hủy voucher** | Order Service / Consumer | Quản lý vòng đời sử dụng voucher đảm bảo tính idempotent và kiểm soát hạn mức. |
| **Tra cứu sổ cái & lịch sử** | Nhân viên / Cổng khách hàng | Phân trang an toàn, kiểm soát quyền sở hữu tenant/khách hàng và che mờ PII. |
| **Cấu hình hạng, khuyến mãi, voucher** | Chủ chuỗi / Quản lý | Phát hành các phiên bản cấu hình bất biến, lưu vết kiểm toán đầy đủ. |

*Nguyên tắc giao tiếp:* Mọi thao tác thay đổi dữ liệu bắt buộc phải có `Idempotency-Key`, `X-Correlation-Id`, định danh tác nhân/workload tin cậy và kiểm tra bắt buộc `tenantId`. Thông báo lỗi phải chuẩn hóa mã lỗi; tuyệt đối không trả stack trace, secret hay dữ liệu của tenant khác.

---

## 7. Tiêu Chí Nghiệm Thu Kỹ Thuật (Acceptance Criteria)

1. Một sự kiện `OrderPaid.v1` được gửi lặp lại ít nhất hai lần chỉ tạo duy nhất một dòng sổ cái `EARNED` và một lần thay đổi số dư.
2. Hai phiên checkout đồng thời từ các quầy khác nhau không thể giữ điểm vượt quá số dư khả dụng; thao tác retry không nhân bản lượt giữ hay lượt dùng voucher.
3. Thanh toán thành công commit đúng lượt giữ điểm; thanh toán lỗi hoặc timeout giải phóng đúng lượt giữ điểm; trạng thái của hold chỉ chuyển sang trạng thái terminal đúng một lần.
4. Hoàn tiền toàn bộ đơn hàng sau khi thanh toán tạo giao dịch thu hồi (reversal) đúng một lần và tuyệt đối không chỉnh sửa hay xóa bản ghi sổ cái gốc.
5. Thuật toán đổi điểm FIFO luôn ưu tiên tiêu hao các lô điểm sắp hết hạn trước; job quét hết hạn không làm âm số dư và không làm hết hạn phần điểm đang bị giữ chỗ.
6. Tính toán lại hạng thành viên loại trừ chính xác các đơn hàng bị hoàn/hủy và phát sự kiện đổi hạng đúng một lần khi có sự thay đổi thứ tự hạng.
7. Công cụ giải quyết xung đột khuyến mãi luôn trả về cùng một kết quả báo giá với cùng một giỏ hàng đầu vào; việc phá vỡ thế hòa (tie-break) không phụ thuộc vào thứ tự truy vấn database.
8. Voucher vượt quá tổng lượt dùng toàn hệ thống, vượt quá lượt dùng trên mỗi khách hoặc đã hết hạn đều bị từ chối; thao tác giữ chỗ đồng thời không bao giờ vượt hạn mức.
9. Nhân viên cửa hàng hoặc khách hàng trên portal không thể đọc được thông tin khách hàng hay giao dịch ngoài phạm vi tenant và quyền sở hữu được cấp.
10. Số điện thoại khách hàng không bao giờ xuất hiện dạng văn bản thô (plaintext) trong nhật ký log ứng dụng và thao tác tra cứu không quét bảng trên cột plaintext.
11. Toàn bộ các event consumer đều có cơ chế retry, hàng đợi chết DLQ và chỉ số quan sát, tuyệt đối không acknowledge message trước khi database transaction commit thành công.
12. Order Service có thể tái hiện lại việc kiểm tra báo giá và tham chiếu sổ cái dựa trên phiên bản quy tắc/chính sách đã chụp vào đơn mà không bị phụ thuộc vào các quy tắc đang hoạt động ở thời điểm hiện tại.

---

## 8. Xử Lý Các Trường Hợp Biên & Lỗi Ngoại Lệ (Edge Cases)

- **Sự kiện `OrderPaid` đến trước khi hoàn tất tạo khách hàng:** Consumer thực hiện retry có giãn cách hoặc đẩy vào DLQ có lưu vết correlation ID, tuyệt đối không âm thầm hủy bỏ sự kiện.
- **Sự kiện hoặc lệnh trùng lặp:** Gửi lại cùng idempotency key nhưng khác payload phải bị từ chối với mã lỗi xung đột `409 Conflict`.
- **Hoàn tiền xảy ra sau khi điểm đã hết hạn, đã bị đổi hết hoặc sau khi bị hạ hạng:** Giao dịch đối ứng phải có cơ chế xử lý không làm âm số dư điểm khả dụng và phải lưu rõ vết nguồn (lineage).
- **Tranh chấp giữ điểm đồng thời:** Hai lượt giữ điểm diễn ra cùng lúc trên một khách hàng; một lượt giữ bị hết hạn đúng lúc có callback thanh toán gửi tới.
- **Lượt giữ voucher bị treo do sự cố:** Tiến trình dọn dẹp nền giải phóng lượt giữ voucher quá hạn mà không tiêu thụ voucher và không xóa lịch sử.
- **Báo giá khuyến mãi hết hạn:** Báo giá đã hết hạn hiệu lực hoặc phiên bản quy tắc đã bị vô hiệu hóa khi Order Service gửi yêu cầu thẩm định lại lúc checkout.
- **Cộng dồn khuyến mãi vượt mức:** Tổng chiết khấu của các quy tắc cộng dồn vượt quá giá trị đơn hàng; chương trình mua X tặng Y không đủ số lượng tối thiểu; chương trình giờ vàng vắt qua nửa đêm theo múi giờ chi nhánh.
- **Chuẩn hóa số điện thoại:** Số điện thoại nhập vào có quy tắc chuẩn hóa khác nhau theo từng quốc gia, tài khoản khách hàng bị khóa hoặc yêu cầu bị thiếu ngữ cảnh tenant.
- **Lệch bảng tổng hợp số dư:** Cung cấp lệnh bảo trì quản trị để tái tạo lại bảng tổng hợp số dư từ lịch sử sổ cái mà không can thiệp sửa đổi dữ liệu sổ cái.
- **Sự cố hạ tầng Message Broker:** Xử lý an toàn khi broker phân phối sự kiện lặp lại (at-least-once), sự kiện đến sai thứ tự hoặc replay hàng loạt từ DLQ.

---

## 9. Khả Năng Quan Sát (Observability) & Vận Hành Hệ Thống

- **Hệ thống chỉ số đo lường (Metrics):**
  - Tỷ lệ tích điểm thành công / thất bại, độ trễ xử lý sự kiện, tỷ lệ sự kiện trùng lặp.
  - Tỷ lệ chênh lệch số dư, số lượng lượt giữ điểm đang hoạt động / đã quá hạn, lỗi đổi điểm.
  - Tỷ lệ xung đột khi tạm giữ voucher, độ trễ tính toán báo giá khuyến mãi.
  - Kết quả tính toán lại hạng thành viên, khối lượng điểm hết hạn hàng ngày, và số lượt thử OTP thất bại trên portal.
- **Ghi nhật ký có cấu trúc (Structured Logs):** Bắt buộc có các trường: `tenantId`, `customerId` (opaque ID), `orderId`, `checkoutAttemptId`, `eventId`, `correlationId`; tuyệt đối không log số điện thoại plaintext hoặc mã OTP.
- **Nhật ký kiểm toán (Audit Trail):** Ghi vết toàn bộ các thao tác xem thông tin PII của khách hàng, thu hồi điểm, phát hành phiên bản cấu hình, tiêu thụ/giải phóng voucher, thay đổi hạng thẻ và các lỗi từ chối phân quyền.
- **Hệ thống cảnh báo tự động (Alerts):** Cảnh báo ngay khi số lượng bản ghi trong DLQ tăng đột biến, phát hiện bất biến số dư bị âm, xuất hiện các lượt giữ điểm/voucher bị treo quá lâu, hoặc sai lệch hợp đồng dữ liệu sự kiện.
- **Quản lý tiến trình nền:** Các job quét hết hạn điểm, tính toán lại hạng và dọn dẹp giữ chỗ phải ghi nhận run ID, sử dụng lease lock phân tán, có cơ chế retry và đường dẫn chuyển lỗi cho nhân viên vận hành xử lý thủ công.

---

## 10. Các Quyết Định Nghiệp Vụ Đã Thống Nhất

1. `OrderPaid.v1` là nguồn kích hoạt tích điểm duy nhất; sự kiện mang đầy đủ `loyaltyEligibleAmountMinor` và `loyaltyPolicyVersion`.
2. Lượt giữ điểm (`Hold`) được quản lý tách biệt khỏi sổ cái bất biến; chỉ khi commit mới ghi nhận giao dịch `REDEEMED`.
3. Giai đoạn P0 chỉ hỗ trợ bồi hoàn toàn bộ đơn hàng thông qua sự kiện `OrderRefunded.v1`; hoãn bồi hoàn một phần sang P1.
4. Số điện thoại khách hàng gắn chặt theo tenant, chuẩn hóa E.164, lưu trữ mã hóa đối xứng kết hợp mã băm HMAC tra cứu, che mờ khi hiển thị, và không tự động gộp hồ sơ trong P0.
5. Hạng thành viên đánh giá theo chu kỳ trượt 12 tháng dựa trên chỉ số cấu hình (`ELIGIBLE_SPEND_MINOR` hoặc `EARNED_POINTS`), hỗ trợ cả thăng hạng và hạ hạng.
6. Hết hạn điểm áp dụng theo thuật toán FIFO dựa trên hạn dùng của từng lô điểm; mặc định 12 tháng; điểm đang giữ chỗ được bảo vệ không bị quét hết hạn.
7. Loyalty Service chịu trách nhiệm tính toán báo giá chiết khấu; Order Service là cơ quan có thẩm quyền chốt tổng tiền hóa đơn cuối cùng và chụp snapshot báo giá.
8. Giải quyết xung đột khuyến mãi có tính tất định cao: Mức hưởng lợi của khách hàng → Mức ưu tiên quy tắc → ID quy tắc cố định.
9. Tạm giữ voucher theo phiên checkout; chỉ chính thức tiêu thụ khi đơn hàng thanh toán thành công.
10. Cổng tra cứu khách hàng P2 sử dụng xác thực OTP độc lập, chỉ cấp quyền đọc và có cơ chế chống rò rỉ dữ liệu tài khoản (anti-enumeration).
