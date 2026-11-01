# Yêu cầu Hệ thống: Identity & Access (Quản trị Định danh & Phân quyền)

## Nguồn tham chiếu và Phạm vi

- **Tầm nhìn (Vision):** [`2026-10-12-vision.md`](./2026-10-12-vision.md)
- **Đánh giá Tầm nhìn (Vision Review):** [`2026-10-12-vision-review.md`](./2026-10-12-vision-review.md)
- **Kiến trúc POS Platform:** [`pos-platform/2026-10-12-architecture.md`](../pos-platform/2026-10-12-architecture.md)
- **Yêu cầu POS Platform:** [`pos-platform/2026-10-12-requirements.md`](../pos-platform/2026-10-12-requirements.md)
- **Hợp đồng POS Platform:** [`pos-platform/2026-10-12-contracts.md`](../pos-platform/2026-10-12-contracts.md)

### Ranh giới Nghiệm thu và Ưu tiên

- **P0:** Bắt buộc phải có cho MVP/bản demo và phải có bộ bài kiểm thử nghiệm thu tự động (automated acceptance tests).
- **P1:** Bắt buộc phải có ranh giới kiến trúc rõ ràng và điểm mở rộng tương thích, nhưng không được làm cản trở tiến độ của lát cắt P0.
- Các yêu cầu trong tài liệu này thuộc quyền sở hữu của Bounded Context `identity-access`. Quyền hạn chi tiết của Order, Catalog, Payment, Inventory, Loyalty, Shift, Reporting và KDS thuộc quyền quản lý của các service tương ứng.
- **Định hướng cốt lõi:** Xác thực qua Cognito + Phân quyền qua Identity DB. Cognito không phải là nguồn sự thật cho tư cách thành viên, vai trò hay phạm vi chi nhánh.

---

## Tác nhân và Thuật ngữ (Actors & Terminology)

| Thuật ngữ | Định nghĩa và Ranh giới trách nhiệm |
|---|---|
| **Platform Admin** | Định danh quản trị cấp nền tảng hoặc tiến trình khởi tạo (bootstrap job) được bảo vệ, có quyền tạo tenant và tài khoản Owner đầu tiên; không phải là vai trò thông thường trong tenant. |
| **Owner (Chủ chuỗi)** | Quản trị viên cấp cao nhất của toàn bộ tenant. Có toàn quyền quản lý tất cả chi nhánh và mọi loại tài khoản nhân viên trong tenant. |
| **Manager (Quản lý chi nhánh)** | Quản trị viên cấp chi nhánh. Chỉ được phép quản lý nhân sự thuộc vai trò `CASHIER` và `KITCHEN` trong phạm vi các chi nhánh được phân công. |
| **Cashier (Thu ngân)** | Nhân viên vận hành luồng bán hàng và đặt món tại các chi nhánh được cấp phép. |
| **Kitchen (Nhân viên bếp)** | Nhân viên tiếp nhận chế biến món tại bếp trong các chi nhánh được phân công (màn hình KDS mở rộng ở P1). |
| **Tenant** | Ranh giới sở hữu của một chuỗi thương hiệu F&B. Mọi bản ghi dữ liệu và quyết định phân quyền đều bị cô lập tuyệt đối theo tenant. |
| **Branch (Chi nhánh)** | Địa điểm kinh doanh thuộc quyền sở hữu của duy nhất một tenant. |
| **Membership (Tư cách thành viên)** | Bản ghi phân công một người dùng vào một tenant/chi nhánh với một vai trò hiệu lực và trạng thái vòng đời xác định. |
| **Active Branch (Chi nhánh hoạt động)** | Chi nhánh do client chọn cho ngữ cảnh request hiện tại; đóng vai trò là tham số gợi ý mà máy chủ bắt buộc phải kiểm tra lại tính hợp lệ. |
| **POS Device (Thiết bị POS)** | Trình duyệt hoặc máy POS vật lý đã đăng ký định danh, gắn cố định với một chi nhánh cụ thể phục vụ tính năng đổi nhanh bằng PIN ở P1. |
| **Effective Principal (Danh tính hiệu lực)** | Đối tượng người dùng đã xác thực Cognito `sub`, hoặc nhân viên thu ngân được chọn qua mã PIN trong một phiên POS hợp lệ ở P1. |

---

## Yêu cầu Chức năng (Functional Requirements)

### Danh sách Ca sử dụng (Use Cases)

| Mã | Ưu tiên | Tác nhân | Ca sử dụng | Phản hồi của Hệ thống |
|---|---|---|---|---|
| **UC-IA-01** | P0 | Platform Admin / Bootstrap Job | Khởi tạo tenant và Owner đầu tiên | Tạo tenant mới và gửi lời mời cho Owner đầu tiên qua luồng bảo vệ; ghi nhận thông tin kiểm toán và correlation metadata. |
| **UC-IA-02** | P0 | Owner / Manager | Mời nhân viên mới qua email | Chuẩn hóa email, kiểm tra phạm vi quyền của người mời và vai trò/chi nhánh đích, tạo bản ghi membership ở trạng thái `INVITED` với tính lũy kế (idempotency), và kích hoạt gửi lời mời qua Cognito. |
| **UC-IA-03** | P0 | Nhân viên được mời | Hoàn tất kích hoạt tài khoản và đăng nhập | Cognito xác thực thông tin đăng nhập; hệ thống ánh xạ mã định danh Cognito `sub` với các bản ghi membership và chuyển trạng thái các lời mời hợp lệ sang `ACTIVE`. |
| **UC-IA-04** | P0 | Owner / Manager | Xem danh sách nhân viên trong phạm vi quyền | Chỉ trả về danh sách thành viên thuộc tenant/chi nhánh mà người gọi có quyền xem; hỗ trợ phân trang theo con trỏ hoặc phân trang trang. |
| **UC-IA-05** | P0 | Owner | Tạo mới, cập nhật hoặc lưu trữ chi nhánh | Tạo hoặc chỉnh sửa thông tin chi nhánh thuộc tenant của Owner; lưu trữ (`ARCHIVED`) là thao tác chuyển đổi trạng thái mềm (soft delete) và có ghi log kiểm toán. |
| **UC-IA-06** | P0 | Owner / Manager | Xem thông tin chi nhánh | Owner có quyền đọc toàn bộ chi nhánh trong tenant; Manager chỉ có quyền đọc các chi nhánh được phân công. |
| **UC-IA-07** | P0 | Owner | Điều chỉnh vai trò hoặc chi nhánh của nhân viên | Xác thực phạm vi đích, tính hợp lệ của việc chuyển vai trò và tính duy nhất; lưu trữ phiên bản mới (`version`) và phát tán sự kiện thay đổi phân quyền. |
| **UC-IA-08** | P0 | Manager | Quản lý nhân sự tại chi nhánh được phân công | Được quyền mời, cập nhật, đình chỉ hoặc thu hồi quyền của các vai trò `CASHIER` và `KITCHEN` trong chi nhánh phụ trách. Tuyệt đối không được gán quyền `OWNER`/`MANAGER` hay can thiệp chi nhánh khác. |
| **UC-IA-09** | P0 | Owner / Manager | Tạm đình chỉ hoặc thu hồi quyền truy cập | Cập nhật trạng thái membership sang `SUSPENDED` hoặc `REVOKED`; ngay lập tức vô hiệu hóa quyền hạn cho các request tiếp theo và phát sự kiện kiểm toán an ninh. |
| **UC-IA-10** | P0 | Mọi nhân viên | Phân giải định danh và quyền hạn hiện tại | Gọi `GET /api/v1/me` để lấy thông tin người dùng, danh sách tenant/chi nhánh có quyền và danh mục quyền phân giải từ Identity DB; không tin tưởng role claim tĩnh từ client. |
| **UC-IA-11** | P0 | Mọi nhân viên | Lựa chọn tenant và chi nhánh làm việc | Client chọn từ danh sách membership hợp lệ do máy chủ trả về; mọi request mang ngữ cảnh chi nhánh đều được máy chủ xác thực lại trước khi xử lý. |
| **UC-IA-12** | P0 | Các microservice khác | Phân quyền request theo tenant/chi nhánh | Service gọi sang API phân quyền nội bộ để kiểm tra tính hợp lệ của JWT token, trạng thái membership, vai trò và phạm vi chi nhánh trước khi truy xuất dữ liệu. |
| **UC-IA-13** | P0 | Mọi nhân viên | Xử lý khi truy cập vượt quyền hoặc trái phép | Trả về mã lỗi chuẩn `401`, `403` hoặc `404` có phạm vi bảo vệ; không làm lộ dữ liệu của tenant khác và ghi nhận sự kiện vi phạm an ninh vào Audit Log. |
| **UC-IA-14** | P1 | Người dùng POS đã đăng nhập | Đăng ký hoặc kích hoạt thiết bị POS | Liên kết một thiết bị POS với một chi nhánh duy nhất thông qua luồng xác thực; lưu vết trạng thái và người kích hoạt thiết bị. |
| **UC-IA-15** | P1 | Thu ngân | Cài đặt hoặc đổi mã PIN máy POS | Thiết lập mã PIN 6 chữ số trong phiên làm việc đã xác thực; chỉ lưu trữ bản băm mật mã (hash), cấm trả về hoặc ghi log mã PIN thô. |
| **UC-IA-16** | P1 | Người dùng POS đã đăng nhập | Đổi nhanh tài khoản thu ngân bằng PIN | Yêu cầu máy POS đã đăng nhập hợp lệ, thiết bị đã đăng ký, đúng chi nhánh và mã PIN thu ngân hợp lệ; tạo ngữ cảnh thu ngân tạm thời mà không cần cấp phát token Cognito mới. |
| **UC-IA-17** | P1 | Owner / Manager | Khóa hoặc hủy kích hoạt mã PIN/thiết bị POS | Vô hiệu hóa thiết bị hoặc mã PIN thu ngân, lập tức hủy bỏ ngữ cảnh đổi nhanh đang hoạt động và ghi nhật ký an ninh. |

---

### Ma trận Phân quyền Cốt lõi (Authorization Matrix)

Ma trận phân quyền bắt buộc phải được thực thi ở phía máy chủ. Các lớp route guard trên giao diện Angular chỉ nhằm mục đích nâng cao trải nghiệm người dùng, không được coi là biện pháp kiểm soát an ninh.

| Khả năng / Quyền hạn | Platform Admin | Owner (Chủ chuỗi) | Manager (Quản lý) | Cashier (Thu ngân) | Kitchen (Bếp) |
|---|:---:|:---:|:---:|:---:|:---:|
| Khởi tạo tenant và Owner đầu tiên | ✅ Có quyền | ❌ Không | ❌ Không | ❌ Không | ❌ Không |
| Gọi `/me` xem danh sách quyền của mình | ✅ Có quyền | ✅ Có quyền | ✅ Có quyền | ✅ Có quyền | ✅ Có quyền |
| Xem toàn bộ chi nhánh trong tenant | Phạm vi Platform | ✅ Có quyền | ❌ Không | ❌ Không | ❌ Không |
| Xem chi nhánh được phân công | Không áp dụng | ✅ Có quyền | ✅ Có quyền | ✅ Có quyền | ✅ Có quyền |
| Tạo mới, cập nhật, lưu trữ chi nhánh | Không áp dụng | ✅ Có quyền | ❌ Không | ❌ Không | ❌ Không |
| Mời nhân viên vai trò `OWNER`/`MANAGER` | Không áp dụng | ✅ Có quyền | ❌ Không | ❌ Không | ❌ Không |
| Mời nhân viên vai trò `CASHIER`/`KITCHEN` | Không áp dụng | ✅ Có quyền | Chi nhánh phụ trách | ❌ Không | ❌ Không |
| Đổi vai trò tài khoản Owner/Manager | Không áp dụng | ✅ Có quyền | ❌ Không | ❌ Không | ❌ Không |
| Đổi vai trò tài khoản Cashier/Kitchen | Không áp dụng | ✅ Có quyền | Chi nhánh phụ trách | ❌ Không | ❌ Không |
| Đình chỉ / Thu hồi quyền Cashier/Kitchen | Không áp dụng | ✅ Có quyền | Chi nhánh phụ trách | ❌ Không | ❌ Không |
| Đình chỉ / Thu hồi quyền Owner/Manager | Không áp dụng | ✅ Theo quy tắc Last-Owner | ❌ Không | ❌ Không | ❌ Không |
| Thực thi quyền hạn nghiệp vụ (Order, Catalog...)| Không áp dụng | Toàn bộ tenant | Chi nhánh phụ trách | Chi nhánh phụ trách | Chi nhánh phụ trách |
| Đăng ký / Hủy thiết bị POS (P1) | Không áp dụng | ✅ Có quyền | Chi nhánh phụ trách | ❌ Không | ❌ Không |
| Đổi mã PIN thu ngân của chính mình (P1) | Không áp dụng | Nếu kiêm thu ngân | ❌ Không | ✅ Có quyền | ❌ Không |

#### Các Quy tắc Ràng buộc Nghiêm ngặt:

1. **Quy tắc bảo vệ Owner cuối cùng (Last-Owner Invariant):** Không ai có quyền xóa, đình chỉ hoặc thu hồi quyền của tài khoản Owner duy nhất còn lại trong tenant mà không thông qua quy trình chuyển giao quyền sở hữu được bảo vệ.
2. **Quy tắc trần quyền hạn:** Quản lý chi nhánh tuyệt đối không được phép cấp quyền hoặc thay đổi quyền của bất kỳ ai lên mức ngang bằng hoặc cao hơn thẩm quyền của chính mình.
3. **Quy tắc vai trò duy nhất theo chi nhánh:** Một người dùng có thể thuộc nhiều chi nhánh khác nhau; nhưng trong một chi nhánh cụ thể, người dùng chỉ có tối đa một vai trò duy nhất có hiệu lực.
4. **Không có quyền toàn cục mặc định:** Vai trò và quyền hạn được đánh giá dựa trên membership tại chi nhánh đang chọn, không dùng khái niệm vai trò toàn cục trừ vai trò Owner.
5. **Vô hiệu hóa tức thì:** Một membership ở trạng thái `SUSPENDED` hoặc `REVOKED` không bao giờ được phép chọn làm chi nhánh hoạt động hay sử dụng để phân quyền cho các service cấp dưới.
6. **Không tin tưởng ID từ client:** Các trường `tenantId`, `branchId`, `role` do client gửi lên trên body hoặc path không bao giờ được coi là căn cứ chứng minh quyền sở hữu.

---

## Máy Trạng thái và Vòng đời Thành viên (Lifecycle State Machines)

Vòng đời của một bản ghi Membership tuân theo máy trạng thái sau:

```text
INVITED (Được mời) ──> ACTIVE (Hoạt động) ──> SUSPENDED (Đình chỉ) ──> ACTIVE
      │                       │                         │
      │                       ▼                         ▼
      └───────────────> REVOKED (Thu hồi) <─────────────┘
```

- **`INVITED`:** Bản ghi membership đã được tạo trong Identity DB nhưng nhân viên chưa hoàn tất việc kích hoạt tài khoản trên Cognito hoặc chưa đăng nhập lần đầu.
- **`ACTIVE`:** Đã ánh xạ thành công mã định danh Cognito `sub` với membership; người dùng có đầy đủ quyền hạn để đăng nhập và làm việc.
- **`SUSPENDED`:** Tài khoản bị tạm khóa bởi Owner hoặc Manager; có thể khôi phục lại trạng thái `ACTIVE` bất kỳ lúc nào.
- **`REVOKED`:** Trạng thái kết thúc (terminal) của bản ghi membership. Khi một nhân viên bị thu hồi quyền, bản ghi này không thể mở lại. Nếu mời lại nhân viên đó trong tương lai, hệ thống sẽ tạo một bản ghi membership hoàn toàn mới để bảo toàn tính toàn vẹn của lịch sử kiểm toán.
- **Cơ chế đối soát (Reconciliation):** Trạng thái giữa Cognito và Identity DB được đồng bộ qua job định kỳ và qua luồng kích hoạt đăng nhập lần đầu; không có trạng thái đơn lẻ nào bên ngoài có thể tự cấp quyền truy cập.
- **Định danh lời mời:** Lời mời được định danh bằng địa chỉ Email (được chuẩn hóa chữ thường). Sau khi kích hoạt, định danh người dùng ổn định lâu dài là Cognito `sub`.
- **Tính lũy kế khi mời:** Lệnh gửi lời mời có đính kèm `Idempotency-Key`. Gửi lại cùng một khóa và cùng nội dung sẽ trả về kết quả cũ mà không tạo thêm bản ghi hay gửi email trùng; gửi cùng khóa nhưng khác nội dung sẽ bị từ chối với lỗi `duplicate_request`.

---

## Tính năng Đổi Thu ngân Nhanh bằng Mã PIN (Giai đoạn P1)

- Mã PIN chỉ được phép sử dụng bên trong một phiên làm việc POS đã đăng nhập hợp lệ bằng Cognito; tuyệt đối không thay thế Cognito và không cấp phát JWT token mới.
- Mỗi thiết bị POS được đăng ký gắn cố định với một chi nhánh tại một thời điểm.
- Thu ngân chỉ có thể chuyển đổi thành công nếu có vai trò `CASHIER`, trạng thái `ACTIVE` và thuộc cùng chi nhánh với thiết bị POS.
- Mã PIN chuẩn gồm đúng 6 chữ số; bắt buộc phải được băm bảo mật bằng thuật toán Argon2id hoặc bcrypt; nghiêm cấm lưu trữ dạng văn bản thô (plaintext) hoặc mã hóa đảo ngược được.
- Có cơ chế giới hạn tần suất nhập sai (Rate Limiting) và tự động khóa tạm thời (Lockout) khi vượt quá số lần cho phép để chống tấn công dò mã PIN (Brute-Force).

---

## Yêu cầu Phi Chức năng (Non-Functional Requirements)

| Nhóm yêu cầu | Chỉ số kỹ thuật | Mục tiêu nghiệm thu |
|---|---|---|
| **Hiệu năng** | Phân giải thông tin `/me` kèm danh sách quyền | p95 ≤ 300 ms trong bài test tải demo (không tính thời gian mạng của Cognito). |
| **Hiệu năng** | Quyết định phân quyền nội bộ (Internal Authorization Decision) | p95 ≤ 100 ms khi Identity DB ở trạng thái bình thường; không tạo chuỗi gọi API kéo dài. |
| **Hiệu năng** | Lệnh gửi lời mời nhân viên | Lưu trữ lệnh vào database và trả phản hồi trong p95 ≤ 500 ms; việc gửi email qua Cognito được xử lý bất đồng bộ. |
| **Tính nhất quán** | Hiệu lực của lệnh thu hồi quyền (`REVOKED`) | Request nhạy cảm tiếp theo ngay sau khi transaction commit bắt buộc phải bị chặn ngay lập tức; không phụ thuộc vào thời hạn hết hạn của token JWT. |
| **Tính sẵn sàng** | Ứng xử khi mất kết nối phân quyền | Không bao giờ được phép tự động fallback sang tin tưởng role claim của client; bắt buộc áp dụng nguyên tắc đóng cửa an toàn (Fail-Closed). |
| **Bảo mật** | An toàn thông tin mật | Không lưu trữ mật khẩu hay mã PIN thô; bắt buộc có giới hạn tần suất và khóa tài khoản khi có dấu hiệu tấn công dò mã. |
| **Khả năng kiểm toán**| Ghi vết hành động nhạy cảm | 100% các thao tác thay đổi chi nhánh, mời nhân viên, đổi vai trò, đình chỉ, thu hồi quyền, đăng ký thiết bị đều sinh bản ghi kiểm toán outbox. |
