# Module 6 — Dịch Vụ Kho Nguyên Liệu (Inventory Service): Yêu Cầu Kỹ Thuật & Nghiệp Vụ (Requirements)

**Ngày:** 2026-10-13  
**Trạng thái:** Đã phê duyệt (Approved)  
**Tài liệu tầm nhìn (Vision):** [Inventory Vision](./2026-10-13-vision.md)  
**Phạm vi:** M — Luồng Service Tiêu chuẩn (Standard Service Flow)

---

## 1. Mục Đích & Ranh Giới Trách Nhiệm

Inventory Service đóng vai trò là "nguồn chân lý duy nhất" (source of truth) cho:
- Danh mục nguyên liệu master (`ingredient`) và đơn vị đo lường cơ sở (`base_unit`).
- Phiên bản công thức định lượng (`versioned recipe`) gắn với món ăn, biến thể và tùy chọn thêm trong Catalog (`catalog item/variant/modifier`).
- Số dư tồn kho chi nhánh (`stock_balance`) và sổ cái biến động kho chỉ ghi thêm (`append-only stock_ledger`).
- Nghiệp vụ giữ chỗ nguyên liệu (`reservation`) trong quy trình điều phối checkout Saga.
- Phiếu nhập kho (`receipt`), điều chỉnh kiểm kê (`count adjustment`) và cảnh báo tồn kho thấp (`low-stock alert`) trong phạm vi P1.
- API dự báo nhu cầu nhập hàng chỉ đọc (`read-only purchase forecast`) trong phạm vi P2.

**Ranh giới ngoài phạm vi:** Inventory Service không sở hữu tên món/giá bán (thuộc Catalog), tổng tiền đơn hàng (thuộc Order), trạng thái thanh toán (thuộc Payment), điểm tích lũy thành viên (thuộc Loyalty), hay tạo đơn mua hàng NCC (thuộc Procurement tương lai). Các service khác tuyệt đối không được truy vấn trực tiếp vào cơ sở dữ liệu của Inventory Service.

---

## 2. Các Tác Nhân (Actors) & Phân Quyền (RBAC)

| Tác nhân | Quyền hạn nghiệp vụ |
|---|---|
| **Chủ chuỗi (Owner)** | Toàn quyền cấp Tenant: Quản lý nguyên liệu, công thức định lượng, tạo số dư đầu kỳ, nhập kho, kiểm kê, cấu hình ngưỡng cảnh báo tồn, xem dự báo và tra cứu audit log. |
| **Quản lý cửa hàng (Manager)** | Quản lý nguyên liệu/công thức dùng chung cấp tenant; thực hiện nghiệp vụ biến động kho tại các chi nhánh được phân quyền quản lý; xem báo cáo dự báo và xử lý cảnh báo tồn kho tại chi nhánh của mình. |
| **Thu ngân (Cashier / POS)** | Tra cứu công thức/tồn kho khả dụng phục vụ bán hàng; tuyệt đối không có quyền can thiệp điều chỉnh số dư kho thủ công. |
| **Bếp / Pha chế (Kitchen / KDS)** | Đọc projection nguyên liệu/công thức phục vụ màn hình KDS nếu được phân quyền; không tạo giao dịch biến động kho. |
| **Order / Step Functions Workload** | Thực thi các lệnh `Reserve`, `Commit`, `Release` và đối soát (`Reconciliation`) thông qua tài khoản định danh hệ thống (workload identity). |
| **Reporting / Audit / Notification Workload** | Lắng nghe (consume) các sự kiện được phép theo allowlist; không có quyền sửa đổi trạng thái của Inventory Service. |

*Nguyên tắc bảo mật:* Cả tầng Service API và Repository đều phải kiểm tra bắt buộc `tenantId`; mọi thao tác biến động kho phải kiểm tra `branchId`. Client tuyệt đối không được tự ý quyết định phạm vi dữ liệu chỉ bằng request body hoặc giao diện UI.

---

## 3. Tiêu Chuẩn Dữ Liệu & Các Bất Biến Chung (Invariants)

### 3.1 Quy Chuẩn Số Lượng (Quantity)
- Các đơn vị đo lường cơ sở (Base unit) trong P0: `g` (gram), `ml` (mililit), `unit` (đơn vị chiếc/cái).
- Số lượng (`quantity`), tồn vật lý (`onHand`), và lượng giữ chỗ (`reserved`) lưu trữ bằng kiểu `NUMERIC(18,3)`.
- Tuyệt đối không dùng số thực dấu phẩy động (`FLOAT`/`DOUBLE`).
- Mọi phép tính làm tròn thực hiện ở bước cuối cùng theo nguyên tắc `HALF_UP` tới 3 chữ số thập phân (scale = 3).
- Số lượng nhập vào trong các giao dịch kho phải là số dương (`> 0`); giá trị `0` chỉ dùng để đánh dấu vô hiệu hóa một dòng công thức, không phát sinh giao dịch kho.
- Công thức tính tồn khả dụng: `available = onHand - reserved`.
- Không transaction nào được phép commit thành công nếu vi phạm điều kiện: `onHand < 0` hoặc `available < 0`.

### 3.2 Phân Định Phạm Vi Tenant & Chi Nhánh (Tenant/Branch Scope)
- Danh mục nguyên liệu master và công thức thuộc sở hữu cấp tenant, không nhân bản theo chi nhánh.
- Bảng số dư, lượt giữ chỗ, phiếu nhập, phiên kiểm kê, cảnh báo tồn và sổ cái biến động kho thuộc sở hữu cấp `tenant + branch`.
- Lượt giữ chỗ (`Reservation`) chỉ áp dụng cho chi nhánh của đơn hàng/checkout attempt tương ứng.
- Mọi yêu cầu truy cập sai tenant hoặc sai chi nhánh phải bị từ chối với lỗi quyền truy cập/sở hữu, tuyệt đối không làm lộ sự tồn tại của dữ liệu.

### 3.3 Tính Bất Biến Của Lịch Sử (Immutable History)
- Phiên bản công thức (`recipe_version`) sau khi đã kích hoạt (`ACTIVE`) hoặc đã được lượt giữ chỗ tham chiếu thì không được phép chỉnh sửa nội dung.
- Bản chụp giữ chỗ (`reservation_snapshot`) và các dòng sổ cái (`stock_ledger_entry`) không bao giờ được cập nhật (`UPDATE`) hay xóa (`DELETE`).
- Nguyên liệu/công thức/biến thể đã phát sinh giao dịch không được xóa cứng (hard delete).
- Mọi thao tác sửa sai (correction) phải tạo một bản ghi giao dịch mới liên kết với mã giao dịch gốc.

---

## 4. Các Máy Trạng Thái Domain (Domain State Machines)

### 4.1 Phiên Bản Công Thức (Recipe Version)

```text
DRAFT → ACTIVE → INACTIVE → ARCHIVED
```

- Mỗi bộ ba `(tenantId, catalogItemId, variantId)` chỉ có tối đa một phiên bản ở trạng thái `ACTIVE` tại một thời điểm.
- Thao tác kích hoạt (`ACTIVE`) một phiên bản mới phải tự động chuyển phiên bản cũ sang `INACTIVE` trong cùng một transaction nguyên tử.
- Không thể kích hoạt công thức nếu có dòng nguyên liệu bị inactive, số lượng không hợp lệ, trùng lặp dòng nguyên liệu/modifier, hoặc tham chiếu ID ngoài tenant.
- Trạng thái `ARCHIVED` là trạng thái kết thúc, không thể kích hoạt lại; phải tạo bản draft mới nếu muốn sử dụng lại.

### 4.2 Vòng Đời Giữ Chỗ (Reservation)

```text
RESERVED → COMMITTED
RESERVED → RELEASED
RESERVED → EXPIRED
RESERVED → RECONCILIATION_REQUIRED
```

- Chỉ các bản ghi ở trạng thái `RESERVED` mới được phép chuyển sang `COMMITTED`, `RELEASED` hoặc `EXPIRED`.
- Các trạng thái `COMMITTED`, `RELEASED`, `EXPIRED`, và `RECONCILIATION_REQUIRED` là trạng thái kết thúc (terminal states); các lệnh lặp lại sẽ trả về kết quả terminal tương ứng hoặc xử lý dạng no-op.
- Trạng thái `RECONCILIATION_REQUIRED` được sử dụng khi thanh toán đã thành công nhưng Inventory Service không thể hoàn thành việc commit/release tự động (ví dụ lỗi logic nội bộ nghiêm trọng). Bản ghi này không được tự ý commit/release lại; Saga/đội ngũ vận hành sẽ xử lý bồi hoàn ở biên giới riêng, và mọi điều chỉnh số dư kho sau đó phải tạo dòng sổ cái mới.
- Tuyệt đối không có chiều chuyển trạng thái quay ngược lại `RESERVED`.

### 4.3 Cảnh Báo Tồn Kho Thấp (Low-Stock Alert)

```text
OPEN → ACKNOWLEDGED → RESOLVED
OPEN → RESOLVED
```

- Cảnh báo mới chỉ được sinh ra khi tồn khả dụng cắt ngưỡng từ trên xuống: từ `> threshold` chuyển thành `<= threshold`.
- Sau khi tồn khả dụng hồi phục vượt trên ngưỡng, lần cắt ngưỡng tiếp theo mới tạo cảnh báo mới (tránh spam).
- Thao tác tiếp nhận (`ACKNOWLEDGED`) không làm thay đổi trạng thái tồn kho thực tế.

### 4.4 Phiên Kiểm Kê Kho (Count Session)

```text
OPEN → PENDING_APPROVAL → APPROVED
OPEN → CANCELLED
PENDING_APPROVAL → CANCELLED
```

- Chỉ khi phiên kiểm kê được duyệt (`APPROVED`) thì mới sinh ra giao dịch điều chỉnh sổ cái `COUNT_ADJUSTMENT`.
- Nhân viên kho có thể nhập số liệu kiểm đếm thực tế; Quản lý/Chủ chuỗi có thẩm quyền phê duyệt chênh lệch.
- Phiên kiểm kê không khóa chi nhánh và không làm dừng hoạt động bán hàng tại quầy POS.

---

## 5. Yêu Cầu Chức Năng Giai Đoạn P0 (Functional Requirements P0)

### FR-INV-001 — Danh Mục Nguyên Liệu Master (Ingredient Master)
Hệ thống cho phép Chủ chuỗi / Quản lý trong phạm vi quyền hạn:
- Tạo nguyên liệu với mã định danh (code), tên hiển thị, đơn vị đo cơ sở (`base_unit`) và tỷ lệ quy đổi đóng gói mua hàng (purchase conversion).
- Chỉnh sửa thông tin nguyên liệu áp dụng kiểm soát phiên bản lạc quan (optimistic locking version).
- Kích hoạt (`ACTIVE`) hoặc vô hiệu hóa (`INACTIVE`) nguyên liệu.
- Xem danh sách nguyên liệu theo tenant, hỗ trợ lọc và sắp xếp theo allowlist an toàn.

*Quy tắc kiểm tra (Validation):*
- Mã nguyên liệu (`code`) là duy nhất trong phạm vi tenant, chuẩn hóa chuỗi trước khi so sánh.
- Đơn vị cơ sở bắt buộc thuộc nhóm: `g`, `ml`, `unit`.
- Hệ số quy đổi đóng gói phải là số dương (`> 0`).
- Nguyên liệu đang ở trạng thái `INACTIVE` không được phép sử dụng trong công thức kích hoạt mới.

### FR-INV-002 — Phiên Bản Công Thức Định Lượng (Versioned Recipe)
Hệ thống cho phép tạo bản nháp (draft), thẩm định (validate), kích hoạt (activate) và lưu trữ (archive) công thức định lượng cho từng cặp `catalogItemId + variantId`.

Một dòng định lượng công thức (`recipe_line`) bao gồm:
- `ingredientId` (bắt buộc): Khóa định danh nguyên liệu.
- `modifierOptionId` (tùy chọn / nullable): Dùng để liên kết dòng nguyên liệu với tùy chọn thêm/topping đã chọn; đối với món gốc/biến thể cơ bản thì trường này là null.
- `quantityPerSale`: Định lượng nguyên liệu tiêu hao cho một đơn vị bán.
- `wasteFactor`: Hệ số hao hụt trong chế biến, mặc định là `1.000` (không được nhỏ hơn `1.000`).
- Bản chụp đơn vị đo cơ sở và các metadata cần thiết cho kiểm toán.

Công thức tính nhu cầu nguyên liệu:
```text
requiredQuantity = orderQuantity × quantityPerSale × wasteFactor
```
Phiên bản công thức phải lưu snapshot đầy đủ các dòng nguyên liệu, hoàn toàn không phụ thuộc thời gian thực (runtime) vào cơ sở dữ liệu của Catalog Service.

### FR-INV-003 — Thiết Lập Số Dư Đầu Kỳ (Opening Balance)
Chủ chuỗi / Quản lý có thể tạo giao dịch `OPENING_BALANCE` cho nguyên liệu tại một chi nhánh:
- Giao dịch đầu kỳ làm tăng số lượng tồn vật lý `onHand`, không làm thay đổi `reserved`.
- Bắt buộc phải có: lý do thực hiện (reason), người thực hiện (actor), chi nhánh (branch) và idempotency key.
- Gửi trùng source/idempotency key sẽ không tạo thêm giao dịch thứ hai.
- Không cho phép số dư đầu kỳ đưa tồn kho về giá trị âm.

### FR-INV-004 — Giữ Chỗ Nguyên Liệu Đơn Hàng (Reserve Ingredients)
Hàm giữ chỗ (`Reserve`) thực hiện tuần tự các bước:
1. Xác thực danh tính workload (Step Functions / POS), phạm vi tenant, branch và ngữ cảnh checkout.
2. Kiểm tra tính toàn vẹn của request hash và `Idempotency-Key`.
3. Tra cứu công thức `ACTIVE` tương ứng cho toàn bộ các dòng món ăn/biến thể/topping trong đơn.
4. Mở rộng (expand) công thức và tổng hợp thành tổng nhu cầu nguyên liệu theo từng `ingredientId`.
5. Thực hiện khóa dòng dữ liệu (`SELECT ... FOR UPDATE`) trên bảng số dư `stock_balance` theo thứ tự tăng dần cố định của `ingredientId` để chống deadlock.
6. Kiểm tra điều kiện tồn kho khả dụng: `available >= requiredQuantity` cho tất cả các nguyên liệu.
7. Nếu thỏa mãn, tăng chỉ số `reserved` một cách nguyên tử trong transaction.
8. Lưu bản chụp snapshot công thức/nhu cầu nguyên liệu vào bảng `reservation` và ghi sự kiện outbox sau khi commit transaction thành công.

*Payload tối thiểu bao gồm:* `orderId`, `checkoutAttemptId`, `branchId`, `expiresAt`, danh sách `lines[]`, `idempotencyKey` và correlation context. Mỗi line gồm: `lineId`, `catalogItemId`, `variantId`, mảng `modifierOptionIds` đã chọn, và `quantity`.

*Quy tắc Idempotency:*
- Khóa nghiệp vụ duy nhất: `(tenantId, checkoutAttemptId)`.
- Request đầu tiên lưu lại mã hash chuẩn hóa của payload.
- Retry với cùng mã hash sẽ trả lại kết quả giữ chỗ trước đó mà không trừ kho lần hai.
- Retry với payload khác mã hash sẽ bị từ chối với lỗi `409 RESERVATION_REQUEST_CONFLICT` và giữ nguyên trạng thái cũ.
- Bắt buộc truyền header `Idempotency-Key` ở tầng giao vận.

*Xử lý lỗi:*
- Thiếu công thức hoặc công thức bị inactive: từ chối giao dịch (fail-closed).
- Không đủ tồn khả dụng: từ chối với chi tiết các nguyên liệu bị thiếu hụt (`shortage details`); tuyệt đối không giữ chỗ dở dang cho các nguyên liệu còn lại.
- Timeout chờ lock / Deadlock: cơ chế retry có giới hạn với exponential backoff; nếu hết lượt retry sẽ trả về lỗi conflict tạm thời.
- Nếu database transaction bị rollback: tuyệt đối không phát event outbox thành công ra ngoài.

### FR-INV-005 — Xác Nhận Trừ Kho (Commit Reservation)
Lệnh `CommitReservation` chỉ được gọi bởi AWS Step Functions sau khi thanh toán đã thành công:
- Payload bắt buộc chứa: `reservationId`, `checkoutAttemptId`, `orderId`, `paymentId`, `paymentAttemptId`, `paymentStatus=SUCCEEDED`, `paidAmountMinor`, `currency`, và ngữ cảnh idempotency.
- Quy trình xử lý tại Inventory Service:
  - Kiểm tra quyền sở hữu của reservation và chi nhánh.
  - Kiểm tra ngữ cảnh thanh toán khớp hoàn toàn với ngữ cảnh checkout đã lưu.
  - Khóa dòng reservation trước, sau đó khóa các dòng `stock_balance` theo thứ tự cố định.
  - Ghi các dòng sổ cái `SALE_COMMITTED` với số lượng âm cho từng nguyên liệu trong nhu cầu đã lưu.
  - Đồng thời giảm `reserved`, giảm `onHand`, và chuyển trạng thái reservation sang `COMMITTED` trong cùng một transaction.
  - Ghi sự kiện outbox sau khi commit thành công.
- Inventory Service không thực hiện gọi HTTP sang Payment Service trong transaction và không tự động lắng nghe event `PaymentPaid` để trừ kho.
- Nếu ngữ cảnh thanh toán không khớp: trả lỗi `PAYMENT_CONTEXT_MISMATCH` và giữ nguyên kho.
- Nếu reservation đã ở trạng thái `COMMITTED`: yêu cầu retry trả về kết quả terminal thành công trước đó.
- Nếu reservation đã bị `RELEASED` hoặc `EXPIRED`: trả về lỗi conflict và kích hoạt tín hiệu đối soát (`reconciliation signal`).

### FR-INV-006 — Hủy Giữ Chỗ Nguyên Liệu (Release Reservation)
Lệnh `ReleaseReservation` được gọi khi thanh toán thất bại, đơn hàng bị hủy, hết hạn thanh toán hoặc có lệnh bồi hoàn (compensation) từ Saga:
- Khóa dòng reservation trước khi cập nhật.
- Chỉ khi reservation đang ở trạng thái `RESERVED` mới thực hiện giảm `reserved` và chuyển trạng thái sang `RELEASED`.
- Tuyệt đối không tạo bản ghi trừ kho `SALE_COMMITTED`.
- Yêu cầu retry trên reservation đã `RELEASED` trả về kết quả thành công idempotent.
- Lệnh Release trên reservation đã `COMMITTED`, `EXPIRED`, hoặc `RELEASED` tuyệt đối không làm thay đổi số dư kho.

### FR-INV-007 — Hết Hạn Giữ Chỗ & Đối Soát (Reservation Expiry & Reconciliation)
- Trường `expiresAt` là bắt buộc trong yêu cầu giữ chỗ do Order/Saga cung cấp.
- TTL mặc định là 15 phút, tối đa không quá 30 phút.
- Tiến trình nền (Cleanup worker) định kỳ tìm các reservation `RESERVED` đã quá hạn, áp dụng đúng quy tắc khóa dòng như lệnh release và chuyển trạng thái sang `EXPIRED`.
- Lệnh Commit gửi tới sau khi reservation đã `EXPIRED` sẽ bị từ chối với lỗi `RESERVATION_EXPIRED`, không được phép tự ý trừ kho.
- Trường hợp thanh toán thành công nhưng đến sau khi reservation đã bị `EXPIRED` sẽ kích hoạt trạng thái `RECONCILIATION_REQUIRED` để Saga và hệ thống đối soát xử lý hoàn tiền hoặc bồi hoàn thủ công.
- Các tiến trình dọn dẹp và đối soát phải đảm bảo tính idempotent và phát hành các chỉ số giám sát về thời gian quá hạn, số lần retry, và lỗi terminal.

### FR-INV-008 — Sổ Cái Kho & Bảng Tổng Hợp Số Dư (Stock Ledger & Balance Projection)
Các loại giao dịch sổ cái trong P0:
- `OPENING_BALANCE`: Số lượng dương (+), phản ánh số dư đầu kỳ.
- `SALE_COMMITTED`: Số lượng âm (-), phản ánh lượng nguyên liệu đã trừ sau khi bán hàng thành công.
- Các loại giao dịch P1: `RECEIPT` (+), `COUNT_ADJUSTMENT` (+/-), `WASTE` (-), `RETURN` (+).

Mỗi bản ghi sổ cái phải có: `tenantId`, `branchId`, `ingredientId`, số lượng có dấu (`signedQuantity`), đơn vị cơ sở (`baseUnit`), loại nguồn phát sinh (`sourceType`), mã nguồn (`sourceId`), lý do (`reason`), tác nhân (`actor`/workload), thời điểm phát sinh (`occurredAt`), idempotency key, correlation ID và các metadata bất biến.

Bảng tổng hợp số dư `stock_balance` lưu trữ: `onHand`, `reserved`, `available`, cùng số phiên bản và thời gian cập nhật. Quy trình tái tạo (rebuild projection) phải đảm bảo khôi phục chính xác 100% `onHand` từ tổng số lượng trong sổ cái và `reserved` từ các reservation đang `RESERVED`.

---

## 6. Yêu Cầu Chức Năng Giai Đoạn P1 (Functional Requirements P1)

### FR-INV-101 — Nhập Kho Nguyên Liệu (Receipt)
- Chủ chuỗi / Quản lý tạo phiếu nhập kho theo chi nhánh.
- Phiếu nhập ghi nhận: nhà cung cấp, số hóa đơn/chứng từ, số lượng nhập, tỷ lệ quy đổi đơn vị mua hàng sang đơn vị cơ sở, thông tin tùy chọn mã lô (`lotCode`) và hạn sử dụng (`expiresAt`), kèm lý do.
- Giao dịch nhập kho ghi nhận bản ghi `RECEIPT` làm tăng tồn vật lý `onHand`; không làm thay đổi các lượt giữ chỗ.
- Trong P1 chưa áp dụng tự động phân bổ theo lô hoặc xuất kho theo hạn dùng (FEFO).

### FR-INV-102 — Phiên Kiểm Kê Kho (Count Session)
- Mở phiên kiểm kê kho lưu lại bản chụp snapshot `onHand`, `reserved`, `available` và thời gian bắt đầu.
- Nhân viên kiểm kho tiến hành nhập số lượng thực tế kiểm đếm được theo từng nguyên liệu.
- Hệ thống tự động tính toán chênh lệch (discrepancy) so với snapshot; hiển thị các giao dịch phát sinh đồng thời kể từ thời điểm chụp snapshot.
- Quản lý / Chủ chuỗi phê duyệt kết quả để tạo bản ghi điều chỉnh `COUNT_ADJUSTMENT` trong sổ cái.
- Giao dịch điều chỉnh kiểm kê tuyệt đối không được làm cho tồn vật lý `onHand` hoặc tồn khả dụng `available` bị âm.

### FR-INV-103 — Điều Chỉnh Hao Hụt & Trả Hàng (Waste & Return)
- `WASTE` (Hao hụt/hư hỏng): Giảm `onHand`, bắt buộc phải có lý do chi tiết và người thực hiện.
- `RETURN` (Trả hàng/nhập lại): Tăng `onHand` khi nguyên liệu chưa sử dụng được hoàn kho, bắt buộc kèm chứng từ nguồn và lý do.
- Tuyệt đối không chỉnh sửa bản ghi sổ cái cũ; mọi điều chỉnh sai sót đều tạo bản ghi đối ứng mới.

### FR-INV-104 — Cảnh Báo Tồn Kho Thấp (Low-Stock Alert)
- Cấu hình ngưỡng cảnh báo theo bộ ba `(tenantId, branchId, ingredientId)`.
- Điều kiện kích hoạt dựa trên tồn khả dụng: `available = onHand - reserved`, không dựa trên tồn vật lý `onHand`.
- Chỉ kích hoạt một cảnh báo duy nhất khi tồn khả dụng cắt qua ngưỡng từ trên xuống.
- Vòng đời cảnh báo gồm các trạng thái: `OPEN` (Mới phát sinh), `ACKNOWLEDGED` (Đã tiếp nhận xử lý), `RESOLVED` (Đã giải quyết/nhập thêm hàng), lưu đầy đủ người xử lý và ghi chú audit.
- Cập nhật ngưỡng cảnh báo phải kích hoạt đánh giá lại trạng thái tồn kho ngay lập tức.
- Phát sự kiện `InventoryLowStockDetected.v1` cho các hệ thống downstream (Notification/Reporting).

---

## 7. Yêu Cầu Chức Năng Giai Đoạn P2 (Functional Requirements P2)

### FR-INV-201 — Dự Báo Nhu Cầu Nhập Hàng Chỉ Đọc (Read-Only Forecast)
- Cung cấp API truy vấn dự báo theo chi nhánh, nguyên liệu và ngày dự báo.
- Thuật toán cơ sở sử dụng trung bình trượt có trọng số (weighted moving average) trong 28 ngày dựa trên lịch sử `SALE_COMMITTED`, có hiệu chỉnh theo các ngày trong tuần (weekday adjustment) khi có đủ dữ liệu.
- Nếu dữ liệu lịch sử dưới 14 ngày: sử dụng trung bình đơn giản trên số ngày hiện có.
- Nếu hoàn toàn chưa có dữ liệu: trả về mã `INSUFFICIENT_HISTORY`, tuyệt đối không tự phỏng đoán số lượng.
- Công thức gợi ý lượng cần nhập:
```text
forecastDemand = demandDuringLeadTime + safetyStock - available
```
- Phản hồi API bao gồm: khoảng thời gian dữ liệu đầu vào (input window), thời điểm tính toán (calculatedAt), phiên bản thuật toán (modelVersion), chỉ số độ tin cậy/chất lượng dữ liệu (confidence/quality), và độ tươi của dữ liệu (freshness).
- Chức năng chỉ mang tính chất khuyến nghị, không tự động tạo đơn mua hàng và không tự động nhập kho.

---

## 8. Yêu Cầu Về Giao Diện Lập Trình (API Requirements)

### 8.1 Kiến Trúc & An Toàn Bảo Mật
- Kiến trúc RESTful tuân thủ đặc tả OpenAPI 3.1; các API nội bộ phục vụ Saga nằm trong phân vùng internal API riêng biệt.
- API người dùng sử dụng Cognito Access Token mang đầy đủ các claims về tenant, branch và vai trò (role); API nội bộ sử dụng định danh hệ thống (workload identity).
- Kiểm tra bảo mật đa tầng: cả tầng Gateway/Controller và tầng Repository đều phải kiểm tra `tenantId`; các endpoint thao tác chi nhánh phải xác thực quyền truy cập chi nhánh tương ứng.
- Cấu trúc phản hồi lỗi chuẩn hóa: `code`, `message`, `details[]`, và `requestId`.
- Bảng mã lỗi máy đọc được tối thiểu:
  - `validation_error`: Dữ liệu đầu vào không hợp lệ.
  - `forbidden`: Không có quyền truy cập tài nguyên.
  - `not_found`: Không tìm thấy thực thể trong phạm vi tenant/branch.
  - `conflict`: Xung đột trạng thái dữ liệu.
  - `insufficient_stock`: Không đủ tồn kho khả dụng để giữ chỗ.
  - `reservation_request_conflict`: Xung đột payload giữ chỗ cho cùng một lượt checkout.
  - `reservation_expired`: Lượt giữ chỗ đã hết hạn hiệu lực.
  - `payment_context_mismatch`: Ngữ cảnh thanh toán không khớp với thông tin giữ chỗ.
  - `reconciliation_required`: Phát sinh ngoại lệ cần đối soát thủ công.
  - `lock_timeout`: Quá thời gian chờ khóa dòng dữ liệu.

### 8.2 Phân Trang & Tra Cứu Dữ Liệu
- Phân trang dạng Offset (Offset pagination) cho danh mục nguyên liệu, công thức, phiếu nhập kho, phiên kiểm kê và cảnh báo tồn thấp.
- Phân trang dạng Con trỏ (Cursor pagination) dựa trên cặp `occurredAt + id` cho sổ cái biến động kho và lịch sử giữ chỗ.
- Áp dụng giới hạn kích thước trang tối đa (`maxPageSize`); tham số lọc và sắp xếp chỉ chấp nhận danh sách trường an toàn (allowlist).
- Tuyệt đối không cho phép client truyền các đoạn SQL lọc thô hoặc sắp xếp theo cột tùy ý.

### 8.3 Tính Idempotency & Xử Lý Đồng Thời
- Các API làm thay đổi trạng thái (`Reserve`, `Commit`, `Release`, `Opening Balance`, `Receipt`, `Count Adjustment`) bắt buộc phải có `Idempotency-Key`.
- Các yêu cầu retry cùng request hash phải trả về kết quả ngữ nghĩa giống hệt kết quả ban đầu.
- Khi phản hồi lỗi xung đột (`409 Conflict`), phải nêu rõ trạng thái hiện tại và ID tài nguyên liên quan mà không làm rò rỉ dữ liệu ngoài phạm vi quyền hạn.

---

## 9. Yêu Cầu Về Sự Kiện & Tích Hợp Hệ Thống (Events & Integration)

### 9.1 Danh Mục Sự Kiện (Events)
- `InventoryReserved.v1`: Giữ chỗ nguyên liệu thành công.
- `InventoryReservationCommitted.v1`: Trừ kho thành công sau thanh toán.
- `InventoryReservationReleased.v1`: Giải phóng lượng giữ chỗ khi đơn hủy.
- `InventoryReservationExpired.v1`: Lượt giữ chỗ bị hết hạn tự động.
- `InventoryStockChanged.v1`: Biến động tồn kho vật lý do nhập kho/kiểm kê/hao hụt.
- `InventoryLowStockDetected.v1`: Phát hiện tồn kho khả dụng giảm xuống dưới ngưỡng an toàn.
- `InventoryReconciliationRequired.v1`: Yêu cầu can thiệp đối soát bất thường.

*Cấu trúc phong bì sự kiện (Event Envelope) chuẩn hóa:* Bắt buộc chứa `eventId`, `eventType`, `occurredAt`, `tenantId`, `branchId`, `aggregateId`, `aggregateVersion`, `correlationId`, và `causationId`.

### 9.2 Cơ Chế Phân Phối Sự Kiện (Event Delivery)
- Ghi nhận sự kiện vào bảng transactional outbox trong cùng một database transaction với nghiệp vụ.
- Phát hành sự kiện lên EventBridge theo mô hình at-least-once.
- Downstream consumer thực hiện deduplication dựa trên `eventId` hoặc khóa nghiệp vụ ngữ nghĩa.
- Nội dung sự kiện tuyệt đối không chứa secret, mã token thanh toán hay dữ liệu PII nhạy cảm của khách hàng.
- Xử lý các sự kiện phát lại (replay) hoặc đến sai thứ tự phải đảm bảo tính idempotent hoặc chuyển hướng vào quy trình đối soát.

---

## 10. Yêu Cầu Phi Chức Năng (Non-Functional Requirements)

| Tiêu chí | Yêu cầu kỹ thuật chi tiết |
|---|---|
| **Tính Nhất Quán (Consistency)** | Các thao tác `Reserve`, `Commit`, `Release` và biến động số dư kho phải đảm bảo tính nhất quán mạnh (strongly consistent) trong phạm vi transaction cơ sở dữ liệu của một chi nhánh. |
| **Tính Khả Dụng (Availability)** | Hệ thống cho phép retry lệnh an toàn sau các sự cố gián đoạn tạm thời mà không tạo trùng lặp reservation hoặc dòng sổ cái. |
| **Hiệu Năng (Performance)** | Luồng P0 (`Reserve`/`Commit`/`Release`) hướng tới mục tiêu thời gian phản hồi p95 ≤ 500 ms dưới điều kiện tải thử nghiệm; thời gian chờ khóa và số lượt retry phải được đo lường chi tiết. |
| **Kiểm Soát Đồng Thời (Concurrency)** | Áp dụng pessimistic row lock (`SELECT ... FOR UPDATE`) theo thứ tự tăng dần cố định của `ingredientId`; transaction giữ ngắn tối đa, không gọi ra bên ngoài khi đang giữ khóa. |
| **An Toàn Bảo Mật (Security)** | Triển khai phòng thủ chuyên sâu theo mô hình đa tenant và đa chi nhánh; sử dụng workload identity cho các lệnh nội bộ của Saga; kiểm tra quyền hạn RBAC nghiêm ngặt cho các thao tác thủ công. |
| **Khả Năng Kiểm Toán (Auditability)** | Mọi biến động sổ cái, giữ chỗ, kiểm kê và cảnh báo đều phải lưu vết: tác nhân thực hiện, lý do/chứng từ nguồn, correlation ID và dấu thời gian UTC. |
| **Khả Năng Quan Sát (Observability)** | Ghi log có cấu trúc (structured JSON log); cung cấp đầy đủ metrics về tỷ lệ thiếu hụt hàng, mức độ tranh chấp khóa (lock contention), số lượng hết hạn, trường hợp cần đối soát, độ trễ outbox và độ tươi của dự báo. |
| **Độ Tin Cậy (Reliability)** | Ứng dụng mẫu thiết kế Outbox/Inbox, cơ chế retry có giới hạn với exponential backoff, có hàng đợi chết (DLQ) và luồng đối soát cho các lỗi không thể tự khắc phục. |
| **Lưu Trữ Dữ Liệu (Retention)** | Dữ liệu sổ cái kho, lịch sử giữ chỗ và audit log phải được lưu trữ tối thiểu 7 năm; lên phương án phân vùng bảng (table partitioning) khi sổ cái vượt mốc 10 triệu dòng. |
| **Quản Lý Thời Gian (Time Handling)** | Toàn bộ dấu thời gian được lưu trữ chuẩn UTC trong database; tầng hiển thị (read layer) chịu trách nhiệm chuyển đổi sang múi giờ của chi nhánh phục vụ giao diện người dùng. |

---

## 11. Xử Lý Các Trường Hợp Biên & Lỗi Ngoại Lệ (Error & Edge Cases)

1. **Tổng hợp nhu cầu nguyên liệu:** Một đơn hàng có nhiều dòng món ăn sử dụng chung một nguyên liệu (ví dụ: trà sữa và hồng trà đều dùng đường nước) phải được tổng hợp lại thành một tổng nhu cầu duy nhất trước khi khóa dòng và kiểm tra tồn kho.
2. **Nguyên tắc "Tất cả hoặc không gì cả" (All-or-Nothing):** Nếu bất kỳ nguyên liệu nào trong đơn bị thiếu hụt, toàn bộ transaction giữ chỗ sẽ bị rollback hoàn toàn; tuyệt đối không thực hiện giữ chỗ dở dang một phần.
3. **Tranh chấp giữ chỗ đồng thời (Race Condition):** Khi nhiều máy POS cùng giữ chỗ một nguyên liệu có hạn, chỉ có yêu cầu giành được lock trước được xử lý; các yêu cầu đến sau nếu không đủ tồn khả dụng sẽ nhận lỗi thiếu hàng (`insufficient_stock`) hoặc lỗi xung đột có thể retry.
4. **Tranh chấp giữa Commit và Release:** Khi lệnh Commit và Release chạy đồng thời, chỉ một lệnh được phép xác định trạng thái terminal (thành công hoặc bồi hoàn); lệnh đến sau không được phép đảo ngược trạng thái.
5. **Cập nhật công thức đồng thời với giữ chỗ:** Nếu công thức món ăn được kích hoạt phiên bản mới đúng thời điểm có lệnh giữ chỗ, hệ thống phải chọn một phiên bản công thức duy nhất và nhất quán; tuyệt đối không tạo bản chụp giữ chỗ bị trộn lẫn giữa hai phiên bản công thức khác nhau.
6. **Lệnh hoặc sự kiện trùng lặp:** Hệ thống sử dụng idempotency key và bảng inbox để đảm bảo không sinh thêm dòng sổ cái, cảnh báo hay số lượng giữ chỗ trùng lặp.
7. **Sai lệch Tenant/Chi nhánh:** Yêu cầu truy cập tài nguyên sai phạm vi tenant hoặc chi nhánh phải bị chặn ngay tại tầng phân quyền trước khi trả về chi tiết tài nguyên.
8. **Dữ liệu số lượng không hợp lệ:** Số lượng vượt quá độ chính xác cho phép (`NUMERIC(18,3)`), sai đơn vị đo hoặc gây tràn số sẽ bị từ chối ngay lập tức với mã lỗi `validation_error`.
9. **Dữ liệu danh mục không đầy đủ:** Món ăn chưa có công thức định lượng, công thức bị vô hiệu hóa, hoặc mapping tùy chọn thêm bị lỗi sẽ kích hoạt cơ chế fail-closed (từ chối bán).
10. **Lỗi nghiêm trọng sau thanh toán thành công:** Trường hợp thanh toán đã trừ tiền thành công nhưng Inventory Service gặp lỗi phần cứng/dữ liệu không thể commit kho sẽ chuyển reservation sang trạng thái `RECONCILIATION_REQUIRED` và phát sinh cảnh báo khẩn cấp, tuyệt đối không tự động ghi âm kho vật lý.
11. **Độ trễ của bảng tổng hợp (Projection Lag):** Nếu tầng đọc sử dụng read model bất đồng bộ, API tra cứu số dư phải trả kèm thông tin về phiên bản (`aggregateVersion`) và độ tươi dữ liệu (`freshness`) để client nhận diện được độ trễ.

---

## 12. Tiêu Chí Nghiệm Thu Theo Mức Độ Ưu Tiên (Acceptance Criteria)

### Mức P0 (Cốt lõi cho vận hành và Saga Checkout)
- [ ] Có thể tạo nguyên liệu với đơn vị đo cơ sở và kích hoạt công thức định lượng cho từng biến thể món.
- [ ] Một ly trà sữa với kích cỡ size và các topping đi kèm được mở rộng chính xác thành tổng nhu cầu tiêu hao nguyên liệu.
- [ ] Khi nhiều POS đồng thời giữ chỗ, số dư khả dụng (`available`) không bao giờ bị âm và chỉ số `reserved` không bị cộng dồn trùng lặp.
- [ ] Thanh toán thành công kích hoạt lệnh commit từ Saga tạo đúng dòng sổ cái `SALE_COMMITTED` và chuyển reservation sang trạng thái kết thúc `COMMITTED`.
- [ ] Thanh toán thất bại hoặc timeout kích hoạt lệnh release giải phóng chính xác lượng `reserved`, không tạo dòng trừ kho trong sổ cái.
- [ ] Gửi lại yêu cầu với cùng idempotency key trả về kết quả cũ; gửi payload khác với cùng checkout attempt bị từ chối với lỗi xung đột `409`.
- [ ] Việc chỉnh sửa công thức sau khi đã giữ chỗ không làm thay đổi nhu cầu nguyên liệu của các đơn hàng đã giữ chỗ trước đó.
- [ ] Chi nhánh A hoàn toàn không thể xem hoặc tiêu hao số dư tồn kho của Chi nhánh B.
- [ ] Bảng tổng hợp số dư tồn kho có thể tái tạo hoàn toàn chính xác 100% từ lịch sử sổ cái và trạng thái giữ chỗ.

### Mức P1 (Nghiệp vụ quản lý kho nâng cao)
- [ ] Phiếu nhập kho làm tăng tồn vật lý `onHand` chính xác theo đơn vị cơ sở đã quy đổi.
- [ ] Phiên kiểm kê kho diễn ra bình thường mà không làm gián đoạn bán hàng tại quầy POS; việc điều chỉnh chênh lệch yêu cầu Quản lý phê duyệt.
- [ ] Các giao dịch hao hụt, trả hàng, điều chỉnh kiểm kê được ghi nhận vào sổ cái theo nguyên tắc bất biến chỉ ghi thêm.
- [ ] Cảnh báo tồn kho thấp phát sinh chính xác khi tồn khả dụng cắt qua ngưỡng cấu hình, không spam lặp lại và tự động mở lại khi tồn kho phục hồi.

### Mức P2 (Tính năng dự báo thông minh)
- [ ] API dự báo cung cấp số liệu dự báo kèm khoảng thời gian đầu vào, mức độ tin cậy và phiên bản thuật toán.
- [ ] Hệ thống phản hồi rõ ràng khi chưa có đủ dữ liệu lịch sử và tuyệt đối không tự ý sinh đơn mua hàng NCC.

---

## 13. Ma Trận Truy Vết Quyết Định (Traceability Matrix)

| Quyết định trong Vision | Mục Yêu cầu Kỹ thuật tương ứng |
|---|---|
| Đơn vị cơ sở & Không cho phép âm kho | Mục 3.1, FR-INV-001, FR-INV-004, FR-INV-008 |
| Phiên bản công thức & Định lượng topping | Mục 3.3, FR-INV-002, FR-INV-004 |
| Vòng đời giữ chỗ trong Saga (Reserve/Commit/Release) | Mục 4.2, FR-INV-004 đến FR-INV-007, Mục 11 |
| Chiến lược khóa dòng bi quan (Pessimistic locking) | FR-INV-004 đến FR-INV-006, Mục 10 |
| Cô lập số dư theo từng chi nhánh | Mục 2, Mục 3.2, Tiêu chí nghiệm thu P0 |
| Nhập kho, kiểm kê, cảnh báo tồn thấp | FR-INV-101 đến FR-INV-104 |
| Thuật toán dự báo nhu cầu cơ sở | FR-INV-201 |
| Chuẩn REST, Idempotency và Outbox pattern | Mục 8, Mục 9, Mục 10 |

---

## 14. Đầu Vào Cho Giai Đoạn Tiếp Theo

- **Kiến trúc (Architecture):** Cần xác định cụ thể ranh giới transaction cơ sở dữ liệu, cấu trúc bảng và chỉ mục (index), chiến lược retry chờ lock, cơ chế outbox publisher và tiến trình nền đối soát.
- **Hợp đồng API (Contracts):** Cụ thể hóa toàn bộ schema OpenAPI 3.1, các ví dụ payload thành công/thất bại, cơ chế xác thực workload nội bộ, và JSON Schema cho các sự kiện bất đồng bộ.
- **Thiết kế Cơ sở Dữ liệu (Database Design):** Bổ sung chú thích chi tiết cho từng cột dữ liệu, các ràng buộc toàn vẹn `UNIQUE` và `CHECK`, kịch bản tái tạo projection và thứ tự chạy migration.
- **Sơ đồ Tuần Tự (Sequence Diagrams):** Mô tả trực quan luồng giữ chỗ thành công/thiếu hàng, luồng commit sau thanh toán, luồng release bồi hoàn, luồng hết hạn tự động và hành vi xử lý tranh chấp khóa đồng thời.
