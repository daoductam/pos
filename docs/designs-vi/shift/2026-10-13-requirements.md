# Module 7 — Dịch Vụ Ca Làm Việc (Shift Service): Yêu Cầu Kỹ Thuật & Nghiệp Vụ (Requirements)

**Ngày:** 2026-10-13  
**Trạng thái:** Đã phê duyệt có cảnh báo (Approved with warnings)  
**Tài liệu tầm nhìn (Vision):** [Shift Vision](./2026-10-13-vision.md)  
**Đánh giá tầm nhìn (Vision Review):** [Shift Vision Review](./2026-10-13-vision-review.md)  
**Phạm vi:** M — Luồng Service Tiêu chuẩn (Standard Service Flow)  
**Module:** Ca làm việc (Shift Service)

---

## 1. Mục Tiêu & Ranh Giới Trách Nhiệm

Shift Service đóng vai trò là "nguồn chân lý duy nhất" (source of truth) cho vòng đời ca làm việc và trách nhiệm quản lý két tiền mặt tại quầy theo từng cặp `(tenantId, branchId)`. Service sở hữu: thực thể ca làm việc, số tiền đầu ca (`openingFloatMinor`), các lượt kiểm đếm tiền mặt (`cash_count_attempts`), mức chênh lệch thừa/thiếu (`discrepancy`), quyết định phê duyệt của quản lý, lệnh hủy ca và các bản ghi sự kiện/kiểm toán liên quan.

Shift Service không sở hữu tổng tiền đơn hàng (thuộc Order Service), trạng thái thanh toán và quyết toán với nhà cung cấp (thuộc Payment Service), người dùng/hội viên (thuộc Identity/Loyalty) hay sổ sách kế toán tổng hợp. Order Service và Payment Service giao tiếp với Shift Service thông qua hợp đồng API và sự kiện bất đồng bộ; tuyệt đối không truy vấn trực tiếp cơ sở dữ liệu của Shift Service.

### Mức Độ Ưu Tiên (Priority)
- **P0:** Mở ca với tiền mặt đầu ca, tra cứu ca hiện tại, đóng ca cơ bản, quản lý máy trạng thái, cơ chế chống trùng lặp (idempotency), phân quyền RBAC và ghi nhật ký kiểm toán.
- **P1:** Kiểm đếm tiền mặt thực tế, tính toán tiền mặt kỳ vọng theo sổ sách, xác định mức chênh lệch, quy trình Quản lý duyệt/yêu cầu đếm lại và đối soát các giao dịch đến muộn (late-fact reconciliation).
- Giai đoạn P2 không nằm trong phạm vi của module này.

---

## 2. Các Tác Nhân (Actors) & Phân Quyền (RBAC)

| Tác nhân (Actor) | Quyền hạn nghiệp vụ |
|---|---|
| **Chủ chuỗi (Owner)** | Mở/đóng/hủy/duyệt ca tại chi nhánh được chọn; cấu hình ngưỡng chênh lệch và chính sách kiểm đếm tiền trên toàn bộ tenant; xem toàn bộ lịch sử và nhật ký kiểm toán. |
| **Quản lý cửa hàng (Manager)** | Mở/đóng/hủy/duyệt ca tại các chi nhánh được phân quyền; đóng ca thay nhân viên hoặc yêu cầu đếm lại bắt buộc phải nhập lý do (`reason`); cấu hình chính sách trong phạm vi được ủy quyền. |
| **Thu ngân (Cashier)** | Mở ca tại chi nhánh được phân công; tự đóng ca do chính mình mở; nhập số tiền kiểm đếm/đếm lại; xem thông tin ca hiện tại và kết quả ca làm việc của mình. |
| **Order / Step Functions Workload** | Kiểm tra điều kiện tiếp nhận giao dịch (`transaction admission`) và gắn mã `shiftId` vào phiên checkout; không có quyền làm thay đổi vòng đời ca. |
| **Payment Workload** | Gửi bằng chứng thanh toán/hoàn tiền mặt kèm mã `shiftId`; không có quyền đóng hoặc mở ca. |
| **Reporting / Audit Workload** | Lắng nghe (consume) các sự kiện nghiệp vụ; tuyệt đối không có quyền sửa đổi trạng thái của Shift Service. |

*Nguyên tắc bảo mật:* Identity Service cung cấp các quyết định về tenant, chi nhánh và vai trò người dùng. Shift Service tái kiểm tra quyền sở hữu tenant, chi nhánh và tài nguyên ở cả tầng Application và Repository. Chủ chuỗi cấp tenant bắt buộc phải chọn chi nhánh cụ thể cho mọi thao tác liên quan đến ca làm việc.

---

## 3. Các Quy Tắc Nghiệp Vụ Cốt Lõi (Core Domain Rules)

1. Mỗi cặp `(tenantId, branchId)` chỉ có tối đa duy nhất một ca làm việc ở trạng thái `ACTIVE` hoặc `PENDING_REVIEW` tại một thời điểm.
2. Tiền đầu ca (`openingFloatMinor`), tiền kiểm đếm và mọi số tiền mặt đều lưu trữ dưới dạng số nguyên không âm minor unit (`BIGINT`); loại tiền tệ chuẩn P0 là `VND`.
3. Toàn bộ dấu thời gian lưu trữ theo chuẩn UTC; ngày kế toán/nghiệp vụ (`businessDate`) được tính toán dựa trên múi giờ IANA của chi nhánh tại thời điểm mở ca.
4. Trạng thái `CLOSED` (Đã đóng) và `CANCELLED` (Đã hủy) là trạng thái kết thúc (terminal states); tuyệt đối không cho phép mở lại (no reopen) và không chỉnh sửa trực tiếp dữ liệu lịch sử.
5. Mọi lệnh làm thay đổi trạng thái đều bắt buộc có header `Idempotency-Key`; gửi lại cùng key và cùng mã hash payload sẽ trả về kết quả cũ, gửi khác payload sẽ bị từ chối với lỗi xung đột `409 Conflict`.
6. Trường hợp Quản lý/Chủ chuỗi thao tác thay mặt thu ngân hoặc can thiệp vượt quyền (override) bắt buộc phải nhập lý do (`reason`) và hệ thống sẽ tự động phát sinh bản ghi kiểm toán (`audit_event`).
7. Shift Service không coi số tiền đầu ca (`openingFloat`) là một giao dịch thanh toán của Payment Service. Nếu hủy ca làm việc, bắt buộc phải có bước ghi nhận xác nhận đã hoàn trả lại số tiền đầu ca vào quỹ.

---

## 4. Máy Trạng Thái Ca Làm Việc (Shift State Machine)

| Trạng thái hiện tại | Lệnh / Điều kiện kích hoạt | Trạng thái tiếp theo | Tác nhân & Điều kiện rào chắn (Guard) |
|---|---|---|---|
| **None** (Chưa có ca) | Lệnh Mở ca (`Open shift`) | `ACTIVE` | Thu ngân / Quản lý / Chủ chuỗi; có quyền tại chi nhánh; không có ca nào đang active hoặc pending review. |
| `ACTIVE` | Lệnh Đóng ca P0 (không đếm tiền) | `CLOSED` | Người mở ca hoặc Quản lý/Chủ chuỗi; yêu cầu lý do nếu đóng thay. |
| `ACTIVE` | Nộp tiền đếm P1 trong ngưỡng cho phép | `CLOSED` | Thu ngân mở ca / được phân công; bản chụp tiền đếm bất biến sau khi nộp. |
| `ACTIVE` | Nộp tiền đếm P1 vượt ngưỡng cho phép | `PENDING_REVIEW` | Số tiền đếm hợp lệ; bắt buộc nhập ghi chú giải trình (`note`). |
| `ACTIVE` | Quản lý hủy ca chưa có giao dịch tiền mặt | `CANCELLED` | Quản lý / Chủ chuỗi; bắt buộc có lý do và xác nhận đã hoàn trả tiền đầu ca. |
| `PENDING_REVIEW` | Quản lý phê duyệt số tiền đếm mới nhất | `CLOSED` | Quản lý / Chủ chuỗi; bắt buộc có `If-Match` / số phiên bản duyệt. |
| `PENDING_REVIEW` | Quản lý yêu cầu kiểm đếm lại (Recount) | `PENDING_REVIEW` | Quản lý / Chủ chuỗi; yêu cầu thu ngân nộp bản ghi đếm tiền mới. |
| `CLOSED` / `CANCELLED` | Bất kỳ lệnh thay đổi trạng thái nào | Báo lỗi xung đột / No-op | Trạng thái kết thúc, không mở lại; chỉ cho phép đọc và đối soát ngoại lệ. |

*Lưu ý:* Trạng thái `PENDING_REVIEW` khóa hoàn toàn việc mở ca mới tại chi nhánh. Lệnh yêu cầu đếm lại không chuyển ca quay ngược về `ACTIVE`.

---

## 5. Yêu Cầu Chức Năng Giai Đoạn P0 (Functional Requirements P0)

### FR-SHIFT-001 — Mở Ca Làm Việc (Open Shift)
Yêu cầu mở ca bắt buộc phải có: `branchId`, `openingFloatMinor`, ngữ cảnh người thao tác và `Idempotency-Key`. Nếu Quản lý/Chủ chuỗi mở ca thay cho thu ngân, request bắt buộc phải có trường `reason`.

Quy trình xử lý tại Shift Service:
- Xác thực tư cách thành viên chi nhánh, quyền sở hữu tenant và kiểm tra chi nhánh đang ở trạng thái `ACTIVE`.
- Khóa dòng bi quan trên chi nhánh để kiểm tra tính duy nhất của ca làm việc active.
- Từ chối ngay lập tức nếu chi nhánh đã có một ca làm việc đang ở trạng thái `ACTIVE` hoặc `PENDING_REVIEW`.
- Tính toán và lưu trữ ngày nghiệp vụ `businessDate` dựa trên múi giờ chi nhánh.
- Lưu trữ người mở ca, thời điểm mở ca (UTC), số tiền đầu ca, correlation ID và số phiên bản.
- Ghi sự kiện `ShiftOpened.v1` vào bảng Transactional Outbox trong cùng một database transaction.
- Trả về kết quả đã lưu trước đó nếu nhận được yêu cầu retry trùng lặp an toàn.

*Các mã lỗi tối thiểu:* `branch_not_found`, `forbidden`, `shift_already_open`, `invalid_amount`, `duplicate_request`, `idempotency_conflict`.

### FR-SHIFT-002 — Tra Cứu Ca Làm Việc Hiện Tại (Read Current Shift)
Endpoint `GET /api/v1/branches/{branchId}/shifts/current` trả về ca làm việc đang `ACTIVE` hoặc `PENDING_REVIEW` của chi nhánh được phân quyền, hoặc trả về lỗi `404 no_active_shift` nếu chưa có ca nào mở.

Phản hồi bao gồm: mã ca, trạng thái, chi nhánh, người mở ca, số tiền đầu ca, ngày nghiệp vụ, phiên bản dữ liệu, thông tin rào chắn đóng ca (nếu có), và tóm tắt kiểm đếm/phê duyệt tuân theo phạm vi vai trò của người gọi.

Endpoint tuyệt đối không làm lộ sự tồn tại của tài nguyên thuộc tenant hoặc chi nhánh khác. Trạng thái `PENDING_REVIEW` phải được hiển thị rõ ràng cho Quản lý và thu ngân tại quầy để máy POS giải thích lý do vì sao chi nhánh đang bị chặn mở ca mới hoặc chặn checkout.

### FR-SHIFT-003 — Đóng Ca Làm Việc Giai Đoạn P0 (Close Shift P0)
Lệnh đóng ca P0 phải:
- Thiết lập rào chắn đóng ca (Close fence) một cách nguyên tử trước khi chuyển trạng thái từ `ACTIVE` sang `CLOSED`.
- Từ chối toàn bộ các yêu cầu tiếp nhận giao dịch tiền mặt mới gửi tới sau thời điểm thiết lập rào chắn.
- Chỉ tính toán các bằng chứng thanh toán tiền mặt đã được tiếp nhận hợp lệ trước rào chắn.
- Xác thực quyền hạn: người mở ca hoặc Quản lý/Chủ chuỗi.
- Yêu cầu bắt buộc nhập lý do (`reason`) đối với trường hợp Quản lý/Chủ chuỗi đóng ca thay nhân viên.
- Ghi sự kiện `ShiftClosed.v1` và bản ghi kiểm toán trong cùng một transaction cục bộ.
- Đảm bảo tính idempotent khi gửi lặp lệnh hoặc retry mạng.
- Từ chối lệnh đóng ca đối với ca đang ở trạng thái `PENDING_REVIEW` (chờ duyệt) và từ chối đối với ca đã ở trạng thái kết thúc.

*Lưu ý:* Giai đoạn P0 không bắt buộc phải đếm tiền. Khi chính sách đếm tiền mặt P1 được bật cho chi nhánh, nếu đóng ca mà không gửi số tiền kiểm đếm thì hệ thống sẽ từ chối với mã lỗi `cash_count_required`.

### FR-SHIFT-004 — Hủy Ca Làm Việc Bị Bỏ Quên (Cancel Abandoned Shift)
Quản lý hoặc Chủ chuỗi chỉ được phép hủy một ca làm việc đang ở trạng thái `ACTIVE` khi ca đó **hoàn toàn chưa phát sinh bất kỳ giao dịch bán hàng hoặc hoàn tiền mặt nào**. Lệnh hủy ca phải:
- Bắt buộc nhập lý do hủy ca (`reason`) và số tiền đầu ca hoàn trả thực tế: `openingFloatReturnedMinor`.
- Bắt buộc kiểm tra điều kiện: `openingFloatReturnedMinor = openingFloatMinor` (tiền hoàn trả phải bằng đúng tiền đầu ca đã nộp).
- Tạo bản ghi kiểm toán bất biến chỉ ghi thêm về việc hủy ca và hoàn trả tiền quỹ.
- Phát sự kiện `ShiftCancelled.v1` ra hệ thống.
- Tuyệt đối không sinh ra chênh lệch tiền mặt và không phát sự kiện `ShiftClosed.v1`.
- Kiên quyết từ chối lệnh hủy ca nếu ca đó đã có bất kỳ giao dịch tiền mặt nào được tiếp nhận, kể cả khi sự kiện từ Payment Service vẫn đang trên đường truyền.

---

## 6. Yêu Cầu Chức Năng Giai Đoạn P1 (Functional Requirements P1)

### FR-SHIFT-101 — Kiểm Đếm Tiền Mặt Thực Tế (Cash Count Attempt)
Khi chính sách kiểm đếm tiền mặt P1 được kích hoạt, quy trình đóng ca bắt buộc phải nộp một bản chụp kiểm đếm tiền mặt bất biến (`cash_count_attempt`). Bản ghi lưu trữ:
- `countedCashMinor`: Số tiền mặt kiểm đếm thực tế (không âm).
- `expectedCashMinor`: Bản chụp số tiền mặt kỳ vọng theo sổ sách tại thời điểm chốt.
- Các giá trị đầu vào: `openingFloatMinor`, `cashCollectedMinor` (tiền thu), `cashRefundedMinor` (tiền hoàn).
- `discrepancyMinor`: Mức chênh lệch tiền mặt (`countedCashMinor - expectedCashMinor`).
- `discrepancyThresholdMinor`: Bản chụp ngưỡng chênh lệch cho phép tại thời điểm đóng ca.
- Danh tính người đếm, thời điểm đếm, thời điểm rào chắn đóng ca, số phiên bản nguồn và ghi chú giải trình (bắt buộc khi có chênh lệch).

Công thức tính toán:
```text
expectedCashMinor = openingFloatMinor 
                    + cashCollectedMinor 
                    - cashRefundedMinor

discrepancyMinor = countedCashMinor - expectedCashMinor
```
Bản ghi kiểm đếm là bất biến chỉ ghi thêm sau khi gửi. Nhân viên thu ngân tuyệt đối không thể tự ý chỉnh sửa lại số tiền đã nộp.

### FR-SHIFT-102 — Xác Thực Các Bằng Chứng Tiền Mặt Kỳ Vọng
Hệ thống chỉ tính toán các bằng chứng giao dịch hợp lệ khớp đúng tenant, chi nhánh và `shiftId`:
- Số tiền mặt thu từ các đơn hàng thanh toán tiền mặt thành công.
- Số tiền mặt hoàn trả khách đã được hoàn tất trước thời điểm thiết lập rào chắn đóng ca.
- Tuyệt đối không tính tiền từ các giao dịch VNPAY hoặc các phương thức không dùng tiền mặt.
- Giai đoạn P0/P1 không hỗ trợ nạp/rút tiền mặt thủ công ngoài bán hàng.

Payment Service / Saga bắt buộc phải cung cấp: mã giao dịch nguồn, thời điểm tiếp nhận hợp lệ (`admittedAt`), thời điểm phát sinh, số tiền, loại tiền tệ, trạng thái, mã `shiftId` và correlation ID. Ranh giới rào chắn đóng ca dựa trên thời điểm tiếp nhận tin cậy (`admittedAt`), không dựa trên thời điểm nhận sự kiện trễ từ broker. Shift Service tự động khử trùng lặp các bằng chứng nguồn và không truy vấn trực tiếp Payment database.

### FR-SHIFT-103 — Ngưỡng Chênh Lệch & Phê Duyệt Của Quản Lý (Review Flow)
- Ngưỡng chênh lệch được cấu hình chi tiết theo từng tenant/chi nhánh (mặc định bằng `0 VND`).
- Nếu giá trị tuyệt đối của chênh lệch: `abs(discrepancyMinor) <= threshold`, ca làm việc được chuyển thẳng sang trạng thái `CLOSED`.
- Nếu chênh lệch vượt quá ngưỡng cho phép, ca làm việc bắt buộc phải chuyển sang trạng thái chờ duyệt: `PENDING_REVIEW` và thu ngân bắt buộc phải nhập ghi chú giải trình (`note`).
- Thu ngân có thể xem kết quả tính toán nhưng không có quyền tự phê duyệt hoặc chỉnh sửa số liệu.
- Quản lý / Chủ chuỗi có thẩm quyền phê duyệt bản đếm tiền mới nhất kèm ghi chú quyết định và số phiên bản kiểm soát xung đột (`If-Match`).
- Khi được phê duyệt, ca làm việc chính thức chuyển sang `CLOSED` và phát sự kiện đóng ca/đối soát.
- Quản lý / Chủ chuỗi có thể yêu cầu đếm lại (`request recount`); khi đó bản ghi đếm hiện tại chuyển sang trạng thái `RECOUNT_REQUIRED`, ca làm việc vẫn giữ nguyên ở `PENDING_REVIEW`, và thu ngân bắt buộc phải nộp một bản đếm tiền mới.
- Việc phê duyệt chênh lệch tuyệt đối không làm thay đổi các bằng chứng thanh toán/đơn hàng và không trực tiếp chỉnh sửa số tiền đếm thực tế của thu ngân.

### FR-SHIFT-104 — Đối Soát Giao Dịch Đến Muộn & Trùng Lặp (Late/Duplicate Fact Reconciliation)
- Một bằng chứng tiền mặt được tiếp nhận sau thời điểm rào chắn đóng ca, hoặc một bằng chứng gửi lặp/không rõ nguồn gốc, tuyệt đối không được phép làm thay đổi một ca làm việc đã `CLOSED`.
- Shift Service tự động tạo hoặc cập nhật hồ sơ đối soát ngoại lệ bất biến (`reconciliation_case`) lưu trữ: mã nguồn, mã ca, lý do, số tiền, thời điểm phát hiện và correlation ID.
- Giao dịch nguồn đã biết gửi lặp lại sẽ được xử lý dạng no-op an toàn sau bước khử trùng lặp.
- Giao dịch sai tenant, sai chi nhánh hoặc sai ca làm việc sẽ bị từ chối và ghi nhật ký kiểm toán mà không làm rò rỉ dữ liệu tài nguyên không liên quan.
- Đội ngũ vận hành có thể xử lý giải quyết hồ sơ đối soát thông qua một lệnh được phân quyền riêng biệt; việc giải quyết này tuyệt đối không mở lại ca làm việc đã đóng.

### FR-SHIFT-105 — Truy Vết Lịch Sử Đối Soát Ca Làm Việc
Lịch sử ca làm việc phải hiển thị đầy đủ và minh bạch: thông tin mở ca, đóng ca, các lượt kiểm đếm tiền, quyết định phê duyệt của quản lý, lệnh hủy ca/hoàn tiền đầu ca và các hồ sơ đối soát ngoại lệ, kèm danh tính người thực hiện, lý do, số phiên bản và dấu thời gian. Các bản ghi lịch sử là bất biến; mọi điều chỉnh sửa sai đều phải tạo bản ghi mới liên kết với bản ghi ban đầu.

---

## 7. Yêu Cầu Tích Hợp Hệ Thống (Integration Requirements)

| Hệ thống tích hợp | Yêu cầu kỹ thuật chi tiết |
|---|---|
| **Order Service** | Luồng tạo đơn và checkout bắt buộc phải mang mã `shiftId`; tuyệt đối không tiếp nhận giao dịch tiền mặt mới sau khi ca đã thiết lập rào chắn đóng ca. |
| **Payment Service** | Yêu cầu thanh toán và bằng chứng hoàn tiền mặt bắt buộc phải mang: `shiftId`, `branchId`, `tenantId`, mã giao dịch nguồn, số tiền và trạng thái. Tuyệt đối không tự suy diễn mã ca từ thời gian hay chi nhánh. |
| **Step Functions Saga** | Saga chuyển tiếp mã `shiftId`, correlation ID và kết quả tiếp nhận giao dịch; các lệnh retry đảm bảo tính idempotent. |
| **Identity Service** | Ngữ cảnh Cognito và Workload Identity là cơ quan có thẩm quyền quyết định danh tính tác nhân, tenant, chi nhánh và quyền hạn RBAC. |
| **Reporting Service** | Lắng nghe các sự kiện mở ca, đóng ca, kiểm đếm tiền, chênh lệch và đối soát có phiên bản kèm metadata độ tươi dữ liệu. |
| **Audit Service** | Lưu trữ nhật ký kiểm toán bất biến chỉ ghi thêm cho toàn bộ các thay đổi trạng thái ca, thao tác đóng ca thay, hủy ca, kiểm đếm, phê duyệt và các lỗi từ chối truy cập. |

---

## 8. Yêu Cầu Về Giao Diện Lập Trình (API Requirements)

### 8.1 Danh Sách API Công Khai (Public API Surface)
- `POST /api/v1/branches/{branchId}/shifts`: Mở ca làm việc mới (idempotent).
- `GET /api/v1/branches/{branchId}/shifts/current`: Tra cứu ca làm việc đang hoạt động của chi nhánh.
- `POST /api/v1/shifts/{shiftId}/close`: Đóng ca P0 hoặc nộp số tiền đếm kèm đóng ca P1.
- `POST /api/v1/shifts/{shiftId}/cancel`: Quản lý / Chủ chuỗi hủy ca làm việc chưa có giao dịch.
- `POST /api/v1/shifts/{shiftId}/cash-counts`: Nộp bản ghi kiểm đếm lại (recount) sau khi Quản lý yêu cầu; bản đếm tiền ban đầu được nộp trực tiếp qua `CloseShiftRequest`.
- `POST /api/v1/shifts/{shiftId}/cash-counts/{countAttemptId}/approve`: Quản lý phê duyệt bản đếm tiền mới nhất.
- `POST /api/v1/shifts/{shiftId}/cash-counts/{countAttemptId}/request-recount`: Quản lý yêu cầu thu ngân kiểm đếm lại tiền.
- `GET /api/v1/shifts/{shiftId}` và các endpoint lịch sử: Tra cứu chi tiết ca và vết kiểm toán theo phân quyền.

*Quy chuẩn Header:* Toàn bộ các API thay đổi trạng thái sử dụng phong bì chuẩn, bắt buộc có `Authorization`, `X-Correlation-Id`, `X-Active-Branch-Id`, `Idempotency-Key`; các thao tác phê duyệt yêu cầu header `If-Match` hoặc số phiên bản duyệt.

### 8.2 Danh Sách API Tích Hợp Nội Bộ (Internal Integration Surface)
- `POST /internal/v1/shifts/{shiftId}/transaction-admissions`: Thẩm định và tiếp nhận giao dịch trước rào chắn đóng ca, trả về metadata xác nhận và phiên bản ca.
- `POST /internal/v1/shifts/{shiftId}/cash-facts`: Nhận bằng chứng thanh toán hoặc hoàn tiền mặt đã hoàn tất từ Payment Service một cách idempotent.
- `GET /internal/v1/shifts/{shiftId}/cash-summary`: Cung cấp số liệu tiền mặt kỳ vọng chuẩn tắc phục vụ đối soát quy trình Step Functions Saga.
- `POST /internal/v1/shifts/reconciliation-cases/{caseId}/resolve`: Xử lý giải quyết hồ sơ đối soát ngoại lệ của đội ngũ vận hành mà không mở lại ca làm việc.

*Xác thực nội bộ:* Bắt buộc sử dụng Workload Identity / mTLS. Ngữ cảnh tenant và chi nhánh được đối soát đa tầng phòng thủ chuyên sâu ở phía server.

### 8.3 Bảng Mã Lỗi Máy Đọc Được (Error Taxonomy)
Các mã lỗi tối thiểu bắt buộc hỗ trợ:
`unauthorized`, `forbidden`, `not_found`, `shift_already_open`, `no_active_shift`, `shift_not_active`, `shift_pending_review`, `shift_closed`, `cash_count_required`, `invalid_amount`, `invalid_currency`, `idempotency_conflict`, `duplicate_request`, `version_conflict`, `count_attempt_immutable`, `recount_required`, `transaction_after_close_fence`, `cash_fact_duplicate`, `foreign_shift_reference`, `late_cash_fact_reconciliation_required`, `opening_float_not_returned`, `manual_cash_movement_not_supported`, `lock_timeout`, `service_unavailable`.

---

## 9. Yêu Cầu Dữ Liệu & Các Bất Biến Toàn Vẹn (Invariants)

### Các Thực Thể Dữ Liệu Cốt Lõi:
- `shifts`: Quản lý vòng đời ca, chi nhánh, người mở ca, số tiền đầu ca, ngày nghiệp vụ, rào chắn đóng ca, trạng thái và phiên bản.
- `cash_count_attempts`: Bản ghi bất biến các lượt kiểm đếm tiền mặt, snapshot tiền kỳ vọng, mức chênh lệch, phiên bản phê duyệt.
- `cash_facts`: Bằng chứng các giao dịch thu/hoàn tiền mặt đã được khử trùng lặp theo ca và nguồn.
- `reconciliation_cases`: Hồ sơ đối soát các giao dịch đến muộn, sai lệch hoặc bất thường kèm metadata xử lý.
- `shift_policies`: Cấu hình ngưỡng chênh lệch cho phép và cờ bắt buộc đếm tiền theo tenant/chi nhánh.
- `outbox_messages`, `idempotency_records`, `audit_references`.

### Các Ràng Buộc Bất Biến Bắt Buộc:
- Ràng buộc partial unique trên ca active: `(tenant_id, branch_id) WHERE status IN ('ACTIVE', 'PENDING_REVIEW')`.
- Toàn bộ các giá trị tiền: `opening_float_minor`, `counted_cash_minor`, `expected_cash_minor`, tiền thu và tiền hoàn `>= 0`.
- Công thức tính toán: `discrepancy_minor = counted_cash_minor - expected_cash_minor`.
- Khóa nguồn của bằng chứng tiền mặt là duy nhất trong phạm vi `tenant + shift + sourceType + sourceId`.
- Phiên bản của bản đếm tiền ngăn chặn triệt để việc phê duyệt trùng lặp hoặc mất mát quyết định duyệt.
- Thiết lập rào chắn đóng ca và chuyển đổi trạng thái ca làm việc được thực thi trong cùng một database transaction cục bộ.
- Ghi nhận sự kiện Outbox diễn ra nguyên tử cùng với biến động trạng thái ca.
- Mọi truy vấn đọc/ghi đều bắt buộc gắn điều kiện `tenant_id` và `branch_id`.

---

## 10. Yêu Cầu Phi Chức Năng (Non-Functional Requirements)

| Tiêu chuẩn | Yêu cầu kỹ thuật | Chỉ số mục tiêu |
|---|---|---|
| **Hiệu năng (Performance)** | Thời gian phản hồi của các API mở/đọc/đóng/đếm/duyệt ca | p95 ≤ 500 ms dưới điều kiện tải thử nghiệm |
| **Tính nhất quán (Consistency)** | Ràng buộc duy nhất một ca active và chuyển đổi trạng thái kết thúc | Nhất quán mạnh (ACID) trong database transaction |
| **Kiểm soát đồng thời (Concurrency)** | Mở ca đồng thời, đóng ca đồng thời với tiếp nhận thanh toán | Khóa dòng bi quan xác định bên chiến thắng tất định; không nhân bản ca active hay duyệt trùng |
| **Độ tin cậy (Reliability)** | Xử lý an toàn các lệnh và sự kiện gửi lặp lại | Idempotency, Transactional Outbox, retry có giới hạn, DLQ và đối soát ngoại lệ |
| **An toàn bảo mật (Security)** | Phòng thủ chuyên sâu đa tenant, chi nhánh, RBAC và chống IDOR | Xác thực đa tầng tại Controller/Service/Repository; nguyên tắc fail-closed |
| **Tiền tệ (Money)** | Tính toán số học tiền tệ | Số nguyên minor units (VND), tuyệt đối không dùng số thực dấu phẩy động |
| **Thời gian (Time)** | Quản lý ngày nghiệp vụ và dấu thời gian | Lưu trữ chuẩn UTC; chuyển đổi theo múi giờ IANA của chi nhánh khi hiển thị |
| **Khả năng kiểm toán (Auditability)** | Lưu vết vòng đời, can thiệp vượt quyền, kiểm đếm và chênh lệch | Nhật ký kiểm toán bất biến chỉ ghi thêm kèm người thực hiện, lý do, correlation ID |
| **Khả năng quan sát (Observability)** | Giám sát ca quá hạn, rào chắn đóng ca, chênh lệch tiền mặt, hàng đợi | Ghi log JSON có cấu trúc, thu thập metrics, trace phân tán và cảnh báo tự động |
| **Lưu trữ dữ liệu (Retention)** | Lịch sử ca làm việc, kiểm đếm, đối soát và kiểm toán | Lưu trữ tối thiểu 7 năm tuân thủ quy định tài chính kế toán |
| **Tính khả dụng (Availability)** | Xử lý khi phụ thuộc bên ngoài gián đoạn | Không âm thầm đóng ca khi thiếu bằng chứng tiền mặt; cung cấp trạng thái retry/đối soát an toàn |

---

## 11. Xử Lý Các Trường Hợp Biên & Lỗi Ngoại Lệ (Edge Cases)

- **Mở ca đồng thời từ nhiều thiết bị:** Chỉ một yêu cầu giành được lock thành công chuyển sang `ACTIVE`, các yêu cầu còn lại nhận lỗi xung đột `409 shift_already_open` hoặc kết quả idempotent đã lưu.
- **Retry lệnh đóng ca:** Gửi lại cùng key và cùng mã hash payload trả về kết quả ban đầu; gửi khác payload trả về lỗi `409 idempotency_conflict`.
- **Đóng ca trong lúc giao dịch thanh toán đang trên đường truyền:** Rào chắn đóng ca xác định tất định: hoặc giao dịch được tiếp nhận trước rào chắn và được tính vào tiền kỳ vọng, hoặc giao dịch bị từ chối sau rào chắn; tuyệt đối không bỏ sót tiền mặt ngoài sổ sách.
- **Thanh toán tiền mặt không đủ tiền:** Payment Service từ chối trước khi sinh bằng chứng; Shift Service không nhận được giao dịch.
- **Sự kiện thanh toán bị gửi lặp:** Cơ chế khử trùng lặp theo khóa nguồn xử lý dạng no-op an toàn.
- **Giao dịch tiền mặt đến sau khi ca đã `CLOSED`:** Tự động tạo hồ sơ đối soát ngoại lệ (`reconciliation_case`); tuyệt đối không mở lại ca và không tự ý sửa đổi tiền kỳ vọng.
- **Số tiền kiểm đếm không hợp lệ (số âm, sai đơn vị):** Trả về lỗi validation ngay lập tức; không thực hiện chuyển đổi trạng thái ca.
- **Nộp bản đếm lại sau khi Quản lý đã duyệt:** Bị từ chối ngay lập tức; ca làm việc đã đóng không thể mở lại.
- **Hủy ca làm việc sau khi đã phát sinh giao dịch tiền mặt:** Bị từ chối ngay lập tức; bắt buộc phải đi qua luồng đóng ca, đếm tiền và đối soát chuẩn.
- **Giao dịch sai tenant hoặc sai chi nhánh:** Bị từ chối với lỗi forbidden mà không làm rò rỉ thông tin chi tiết của tài nguyên.
- **Message Broker gặp sự cố sau khi đóng ca cục bộ:** Trạng thái đóng ca trong database cục bộ vẫn được commit thành công, bản ghi outbox sẽ được worker retry gửi lại khi broker phục hồi.

---

## 12. Tiêu Chí Nghiệm Thu Theo Mức Độ Ưu Tiên (Acceptance Criteria)

### Mức P0 (Cốt lõi cho vận hành quầy thu ngân)
- [ ] Thu ngân hợp lệ mở ca thành công tại chi nhánh với số tiền đầu ca không âm và nhận trạng thái `ACTIVE`.
- [ ] Hai yêu cầu mở ca đồng thời tại cùng một chi nhánh không thể tạo ra hai ca active hoặc pending review.
- [ ] Nhân viên sai vai trò, sai tenant hoặc sai chi nhánh không thể mở ca, xem ca hay đóng ca của chi nhánh khác.
- [ ] Lệnh đóng ca P0 chuyển trạng thái từ `ACTIVE → CLOSED` một cách an toàn kèm rào chắn đóng ca, bản ghi kiểm toán và một sự kiện outbox duy nhất.
- [ ] Quản lý/Chủ chuỗi đóng ca thay nhân viên bắt buộc phải nhập lý do; việc gửi lại lệnh retry không nhân bản sự kiện, kiểm toán hay biến động trạng thái.
- [ ] Quản lý chỉ có thể hủy ca làm việc đang `ACTIVE` khi ca đó hoàn toàn chưa có giao dịch tiền mặt nào và đã xác nhận hoàn trả đủ 100% tiền đầu ca.
- [ ] Yêu cầu tiếp nhận giao dịch tiền mặt gửi tới sau rào chắn đóng ca bị từ chối với mã lỗi định kiểu rõ ràng; bằng chứng gửi muộn tự động tạo hồ sơ đối soát.

### Mức P1 (Kiểm đếm tiền mặt và phê duyệt chênh lệch)
- [ ] Đóng ca có kiểm đếm tiền mặt tính toán chính xác 100% số tiền kỳ vọng và chênh lệch có dấu bằng số học nguyên.
- [ ] Cấu hình ngưỡng chênh lệch bằng 0 sẽ điều hướng mọi ca có chênh lệch sang trạng thái chờ duyệt `PENDING_REVIEW`; ca đếm khớp tiền được chuyển thẳng sang `CLOSED`.
- [ ] Trạng thái `PENDING_REVIEW` khóa hoàn toàn việc mở ca mới và chặn tiếp nhận giao dịch mới tại chi nhánh tương ứng.
- [ ] Quản lý / Chủ chuỗi có thể phê duyệt bản đếm tiền mới nhất; thao tác phê duyệt chuyển ca sang `CLOSED` đúng một lần duy nhất.
- [ ] Quản lý / Chủ chuỗi có thể yêu cầu kiểm đếm lại; bản đếm tiền cũ được giữ nguyên phục vụ kiểm toán và thu ngân bắt buộc nộp bản đếm mới.
- [ ] Thu ngân tuyệt đối không thể tự ý sửa đổi số tiền đếm đã nộp và không có quyền tự phê duyệt chênh lệch.
- [ ] Các bằng chứng tiền mặt bị gửi lặp, gửi muộn hoặc đến sai thứ tự không làm thay đổi ca đã đóng và không làm tính trùng tiền mặt.
- [ ] Reporting Service và Audit Service tiếp nhận đầy đủ toàn bộ các sự kiện mở ca, đóng ca, kiểm đếm tiền, phê duyệt chênh lệch, hủy ca và đối soát ngoại lệ.
