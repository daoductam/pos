# Hợp đồng Giao tiếp & Cấu trúc Dữ liệu: Identity & Access

## Nguồn tham chiếu

- **Tầm nhìn (Vision):** [`2026-10-12-vision.md`](./2026-10-12-vision.md)
- **Yêu cầu Hệ thống (Requirements):** [`2026-10-12-requirements.md`](./2026-10-12-requirements.md)
- **Kiến trúc (Architecture):** [`2026-10-12-architecture.md`](./2026-10-12-architecture.md)
- **Hợp đồng POS Platform:** [`pos-platform/2026-10-12-contracts.md`](../pos-platform/2026-10-12-contracts.md)

---

## Nguyên tắc Hợp đồng (Contract Principles)

- Toàn bộ API công khai đồng bộ sử dụng chuẩn **RESTful + OpenAPI 3.1** dưới tiền tố `/api/v1`.
- Các API giao tiếp nội bộ giữa các microservice sử dụng tiền tố `/internal/v1` và bắt buộc phải có chứng chỉ Workload Identity / mTLS.
- Sự kiện bất đồng bộ tuân thủ cấu trúc bao bì chuẩn của nền tảng, được định nghĩa bằng JSON Schema / AsyncAPI.
- **Identity DB là nguồn sự thật duy nhất** về tenant, chi nhánh, tư cách thành viên (membership), vai trò (role) và trạng thái phân quyền.
- Mã định danh Cognito `sub` là một chuỗi mờ do nhà cung cấp bên ngoài quản lý; tuyệt đối không bao giờ nhận `sub` trực tiếp từ request body của client như một khẳng định về quyền hạn.
- Ngữ cảnh tenant được xác định thông qua đường dẫn tài nguyên (Path parameter). Tiêu đề `X-Active-Branch-Id` chỉ có giá trị lựa chọn chi nhánh hoạt động sau khi máy chủ đã kiểm tra và xác thực quyền hạn thực tế.
- Toàn bộ mốc thời gian là chuỗi ISO-8601 UTC trong API và kiểu `TIMESTAMPTZ` trong PostgreSQL.
- Toàn bộ các mã định danh nội bộ (ID) do service quản lý đều là chuỗi UUID v7.

---

## Hợp đồng HTTP Chung (Common HTTP Contract)

### Các Tiêu đề HTTP (Headers)

| Tiêu đề (Header) | Bắt buộc | Ý nghĩa và Quy ước sử dụng |
|---|:---:|---|
| `Authorization` | Bắt buộc cho Public API | `Bearer <Cognito access token>`; việc đăng nhập diễn ra tại giao diện Cognito Hosted UI / SDK, service này không trực tiếp xử lý form mật khẩu. |
| `X-Correlation-Id` | Khuyến nghị (Tự sinh nếu thiếu) | Mã định danh truy vết liên kết xuyên suốt request, audit log, outbox event và các lời gọi service cấp dưới. |
| `Idempotency-Key` | Bắt buộc cho các lệnh ghi có thể retry | Khóa do client sinh ra gắn với người gọi và tenant; việc gửi lại cùng một khóa bắt buộc phải có cùng mã băm (request hash). |
| `If-Match` | Bắt buộc khi cập nhật tài nguyên có version | Mã phiên bản ETag thu được từ lần đọc trước đó (ví dụ: `"membership-v7"`). |
| `X-Active-Branch-Id` | Khi gọi các API gắn với chi nhánh | Chi nhánh do client chọn; máy chủ bắt buộc kiểm tra lại với membership hiện tại trước khi xử lý. |
| `X-Pos-Operator-Context`| Giai đoạn P1 sau khi đổi PIN | Ngữ cảnh tạm thời do endpoint xác thực mã PIN trả về; downstream service chuyển tiếp về Identity Access để kiểm tra. |
| `Content-Type` | Bắt buộc với request có body | `application/json; charset=utf-8`. |

---

### Cấu trúc Bao bì Chuẩn hóa

#### Phản hồi Thành công (Success Envelope)

```json
{
  "data": {},
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

Đối với các danh sách phân trang (Collection Responses), bổ sung trường `meta.pagination`:

```json
{
  "data": [],
  "meta": {
    "pagination": {
      "page": 1,
      "limit": 20,
      "total": 42,
      "totalPages": 3
    },
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

#### Phản hồi Lỗi (Error Envelope)

```json
{
  "error": {
    "code": "forbidden",
    "message": "Người dùng không được phép truy cập tài nguyên này",
    "details": [],
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

#### Bảng Quy tắc Mã Lỗi của Module

| HTTP Status | Mã lỗi (Code) | Ngữ cảnh sử dụng trong Identity & Access |
|---:|---|---|
| 401 | `unauthorized` | Thiếu, sai định dạng, hết hạn hoặc token Cognito JWT không hợp lệ. |
| 403 | `forbidden` | Đã xác thực danh tính nhưng nằm ngoài phạm vi vai trò / tenant / chi nhánh / thiết bị được phân quyền. |
| 404 | `not_found` | Tài nguyên không tồn tại trong phạm vi được phân quyền của người gọi; không để lộ sự tồn tại của dữ liệu thuộc tenant khác. |
| 409 | `conflict` | Xung đột trạng thái, xung đột phiên bản khóa lạc quan (`If-Match`) hoặc xung đột khóa lũy kế (idempotency). |
| 409 | `duplicate_request` | Cùng một `Idempotency-Key` nhưng nội dung request body lại mang dữ liệu khác nhau. |
| 409 | `last_owner_protected` | Thao tác sẽ làm mất đi tài khoản Chủ chuỗi (`OWNER`) hoạt động duy nhất còn lại. |
| 409 | `membership_state_conflict` | Chuyển đổi trạng thái vòng đời thành viên không hợp lệ (ví dụ: từ `REVOKED` mở lại `ACTIVE`). |
| 409 | `invitation_pending` | Đã tồn tại một lời mời đang hoạt động cho cùng người dùng và phạm vi chi nhánh đó. |
| 422 | `validation_error` | Sai định dạng email, vai trò không hợp lệ, mã PIN sai quy cách hoặc vi phạm ràng buộc trường. |
| 429 | `rate_limited` | Vượt quá giới hạn tần suất gửi lời mời, kích hoạt tài khoản hoặc nhập sai mã PIN; kèm theo header `Retry-After`. |
| 503 | `cognito_unavailable` | Dịch vụ Cognito hoặc cổng gửi email tạm thời gián đoạn; bản ghi lời mời vẫn được lưu trữ bền vững để thử lại sau. |
| 503 | `authorization_unavailable` | Không thể kết nối Identity DB để phân giải quyền; hệ thống áp dụng nguyên tắc đóng cửa an toàn (fail-closed). |
| 500 | `internal_error` | Lỗi máy chủ ngoài ý muốn; không bao giờ trả về stack trace ra phía client. |

---

## Danh mục Hợp đồng API Công khai (Public API Contracts)

### 1. Thông tin Người dùng và Ngữ cảnh Hiện tại

#### `GET /api/v1/me`
Trả về hồ sơ người dùng đã xác thực và toàn bộ danh sách tư cách thành viên (memberships) được cấp phép trong các tenant và chi nhánh. Không chấp nhận bất kỳ tham số vai trò hay quyền hạn nào do client tự gửi lên.

- **Xác thực:** Cognito JWT (`Authorization: Bearer <token>`).
- **Phản hồi `200 OK`:**

```json
{
  "data": {
    "user": {
      "id": "0192b2d0-7f1a-7abc-8d2c-123456789012",
      "email": "manager@example.com",
      "displayName": "Nguyen Van A",
      "status": "ACTIVE"
    },
    "memberships": [
      {
        "id": "0192b2d0-7f1b-7abc-8d2c-123456789012",
        "tenantId": "0192b2d0-7f1c-7abc-8d2c-123456789012",
        "tenantName": "Coffee Chain",
        "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
        "branchName": "Chi nhánh Quận 1",
        "role": "MANAGER",
        "status": "ACTIVE",
        "permissions": [
          "branch:read",
          "employee:invite",
          "employee:manage"
        ],
        "version": 7
      }
    ]
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

*(Lưu ý: Mã định danh Cognito `sub`, access token, refresh token và mã PIN tuyệt đối không xuất hiện trong phản hồi).*

---

### 2. Khởi tạo Tenant Nền tảng (Platform Bootstrap)

#### `POST /api/v1/tenants`
Khởi tạo một tenant mới và gửi lời mời cho Chủ chuỗi (`OWNER`) đầu tiên. Đây là endpoint đặc quyền được bảo vệ dành cho Platform Admin, người dùng thông thường không thể gọi.

- **Xác thực:** Workload Identity của Platform Admin.
- **Tiêu đề bắt buộc:** `X-Correlation-Id`, `Idempotency-Key`.
- **Dữ liệu gửi lên (Request):**

```json
{
  "name": "Chuỗi Cà Phê Mới",
  "owner": {
    "email": "owner@coffeechain.com",
    "displayName": "Chủ Thương Hiệu"
  }
}
```

- **Mã phản hồi HTTP:** `201 Created` (thành công), `409 Conflict`, `422 Unprocessable Entity`.

---

### 3. Quản lý Chi nhánh (Branches)

- `GET /api/v1/tenants/{tenantId}/branches`: Lấy danh sách chi nhánh (Owner xem toàn chuỗi; Manager chỉ xem chi nhánh phụ trách).
- `POST /api/v1/tenants/{tenantId}/branches`: Tạo chi nhánh mới (chỉ Owner có quyền; mã chi nhánh `code` là bất biến sau khi tạo).
- `PATCH /api/v1/tenants/{tenantId}/branches/{branchId}`: Cập nhật tên hoặc địa chỉ chi nhánh (yêu cầu `If-Match`).
- `POST /api/v1/tenants/{tenantId}/branches/{branchId}/archive`: Lưu trữ chi nhánh (soft delete, bảo toàn lịch sử giao dịch).

---

### 4. Quản lý Nhân sự và Tư cách Thành viên (Employees & Memberships)

- `GET /api/v1/tenants/{tenantId}/employees`: Xem danh sách hồ sơ nhân viên và các membership nhìn thấy được.
- `POST /api/v1/tenants/{tenantId}/memberships`: Mời nhân viên mới vào làm việc (phát sinh trạng thái `INVITED`).

**Cấu trúc dữ liệu mời nhân viên (`POST memberships`):**

```json
{
  "email": "cashier@coffeechain.com",
  "displayName": "Nguyễn Văn Thu Ngân",
  "phone": "+84901234567",
  "role": "CASHIER",
  "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789012"
}
```

*Quy tắc nghiệp vụ:*
- Vai trò `OWNER` bắt buộc phải có `branchId: null`.
- Các vai trò `MANAGER`, `CASHIER`, `KITCHEN` bắt buộc phải truyền `branchId` của một chi nhánh đang hoạt động.
- Quản lý chi nhánh (`MANAGER`) cố tình mời vai trò `OWNER`/`MANAGER` hoặc mời vào chi nhánh khác sẽ bị chặn với lỗi `403 Forbidden`.

- `PATCH /api/v1/tenants/{tenantId}/memberships/{membershipId}`: Điều chỉnh vai trò hoặc đổi chi nhánh của nhân viên.
- `POST /api/v1/tenants/{tenantId}/memberships/{membershipId}/suspend`: Tạm đình chỉ quyền làm việc của nhân viên (`SUSPENDED`).
- `POST /api/v1/tenants/{tenantId}/memberships/{membershipId}/reactivate`: Khôi phục lại trạng thái làm việc bình thường (`ACTIVE`).
- `POST /api/v1/tenants/{tenantId}/memberships/{membershipId}/revoke`: Thu hồi vĩnh viễn tư cách thành viên (`REVOKED`); có hiệu lực chặn ngay lập tức.
- `POST /api/v1/tenants/{tenantId}/memberships/{membershipId}/invitation-resends`: Gửi lại email kích hoạt tài khoản cho thành viên đang ở trạng thái `INVITED`.

---

## Hợp đồng API Nội bộ (Internal API Contracts)

### `POST /internal/v1/authorization-decisions`

Endpoint phân giải quyền hạn theo thời gian thực dành riêng cho các microservice trong hệ thống gọi sang. Endpoint này đòi hỏi chứng chỉ mTLS hoặc Workload Identity đáng tin cậy.

- **Dữ liệu gửi lên (Request):**

```json
{
  "subject": "cognito-sub-01J...",
  "tenantId": "0192b2d0-7f1c-7abc-8d2c-123456789012",
  "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
  "requiredPermission": "order:create",
  "posOperatorContext": null
}
```

- **Dữ liệu trả về khi Hợp lệ (`200 OK` - Cho phép):**

```json
{
  "data": {
    "allowed": true,
    "principal": {
      "userId": "0192b2d0-7f1e-7abc-8d2c-123456789012",
      "membershipId": "0192b2d0-7f1b-7abc-8d2c-123456789012",
      "tenantId": "0192b2d0-7f1c-7abc-8d2c-123456789012",
      "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
      "role": "CASHIER",
      "permissions": ["order:read", "order:create"],
      "membershipVersion": 7,
      "effectivePrincipal": "0192b2d0-7f1e-7abc-8d2c-123456789012"
    }
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

- **Khi Từ chối truy cập:** Trả về `200 OK` với `allowed: false` kèm lý do lỗi `denialCode` an toàn; downstream service căn cứ vào đó để trả về mã HTTP `403` cho client.

---

## Cấu trúc Bảng Cơ sở Dữ liệu (Database Schema)

### 1. Bảng `tenants` (Chuỗi thương hiệu)
- `id` (uuid, PK): Mã định danh chuỗi.
- `name` (text, NOT NULL): Tên hiển thị của thương hiệu.
- `status` (text, NOT NULL): Trạng thái (`ACTIVE`, `SUSPENDED`, `ARCHIVED`).
- `version` (bigint, NOT NULL): Phiên bản khóa lạc quan.
- `created_at`, `updated_at` (timestamptz, NOT NULL): Mốc thời gian UTC.

### 2. Bảng `branches` (Chi nhánh)
- `id` (uuid, PK): Mã định danh chi nhánh.
- `tenant_id` (uuid, NOT NULL, FK `tenants(id)`): Thuộc chuỗi nào.
- `code` (text, NOT NULL): Mã chi nhánh do người dùng đặt (bất biến sau khi tạo).
- `normalized_code` (text, NOT NULL): Mã chi nhánh đã chuẩn hóa chữ thường để tạo ràng buộc duy nhất `uq_branches_tenant_code`.
- `name` (text, NOT NULL): Tên chi nhánh (ví dụ: Chi nhánh Quận 1).
- `address` (text, NULL): Địa chỉ thực tế.
- `status` (text, NOT NULL): Trạng thái (`ACTIVE`, `ARCHIVED`).
- `version` (bigint, NOT NULL): Phiên bản khóa lạc quan.

### 3. Bảng `users` (Hồ sơ người dùng)
- `id` (uuid, PK): Mã định danh người dùng nội bộ.
- `cognito_subject` (text, NULL, Unique): Mã `sub` định danh từ Cognito (gán sau khi kích hoạt).
- `normalized_email` (text, NOT NULL, Unique): Email đăng nhập chuẩn hóa chữ thường.
- `display_name` (text, NOT NULL): Họ và tên hiển thị.
- `phone` (text, NULL): Số điện thoại liên hệ (dữ liệu PII được bảo vệ).
- `status` (text, NOT NULL): Trạng thái (`ACTIVE`, `SUSPENDED`, `REVOKED`).
- `reconciliation_status` (text, NOT NULL): Trạng thái đồng bộ với Cognito (`PENDING`, `ALIGNED`, `DRIFTED`).

### 4. Bảng `memberships` (Tư cách thành viên)
- `id` (uuid, PK): Khóa chính bản ghi thành viên.
- `tenant_id` (uuid, NOT NULL, FK `tenants(id)`): Ngữ cảnh tenant.
- `user_id` (uuid, NOT NULL, FK `users(id)`): Người dùng tiếp nhận vai trò.
- `branch_id` (uuid, NULL): Chi nhánh được giao (bắt buộc `NULL` nếu là `OWNER`, bắt buộc `NOT NULL` nếu là các vai trò khác).
- `role` (text, NOT NULL): Vai trò (`OWNER`, `MANAGER`, `CASHIER`, `KITCHEN`).
- `status` (text, NOT NULL): Vòng đời (`INVITED`, `ACTIVE`, `SUSPENDED`, `REVOKED`).
- `status_reason` (text, NULL): Lý do thay đổi trạng thái hoặc đình chỉ/thu hồi quyền.
- `version` (bigint, NOT NULL): Phiên bản dùng cho kiểm tra xung đột `If-Match`.
- `invited_at`, `activated_at`, `suspended_at`, `revoked_at` (timestamptz): Dấu vết thời gian của từng bước chuyển trạng thái.

### 5. Bảng `idempotency_records` (Kiểm soát tính lũy kế)
- `id` (uuid, PK): Mã bản ghi.
- `tenant_id` (uuid, NULL): Ngữ cảnh tenant.
- `actor_subject` (text, NOT NULL): Danh tính người thực hiện lệnh.
- `idempotency_key` (text, NOT NULL): Khóa chống trùng do client gửi lên.
- `request_hash` (text, NOT NULL): Mã băm mật mã SHA-256 của nội dung request body.
- `response_status` (integer, NOT NULL): Mã HTTP phản hồi gốc đã lưu.
- `response_metadata` (jsonb, NOT NULL): Nội dung phản hồi an toàn (đã lọc bỏ bí mật/mã PIN).
- `expires_at` (timestamptz, NOT NULL): Hạn lưu trữ để dọn dẹp định kỳ.

### 6. Bảng `outbox_events` (Sự kiện Transactional Outbox)
- `id` (uuid, PK): Mã sự kiện dùng để khử trùng lặp ở phía nhận.
- `tenant_id`, `branch_id` (uuid): Ngữ cảnh phân quyền đi kèm sự kiện.
- `event_type` (text, NOT NULL): Tên loại sự kiện domain.
- `event_version` (integer, NOT NULL): Phiên bản lược đồ sự kiện.
- `aggregate_type`, `aggregate_id` (text/uuid, NOT NULL): Đối tượng phát sinh sự kiện.
- `payload` (jsonb, NOT NULL): Nội dung chi tiết của sự kiện (đã che giấu dữ liệu nhạy cảm).
- `correlation_id` (text, NOT NULL): Mã liên kết luồng xử lý.
- `publish_status` (text, NOT NULL): Trạng thái gửi (`PENDING`, `PUBLISHED`, `FAILED`).
- `attempt_count` (integer, NOT NULL): Số lần đã thử publish lên Message Broker.
- `next_attempt_at` (timestamptz, NULL): Mốc thời gian thử lại kế tiếp.

---

## Kế hoạch Thực thi Cơ sở Dữ liệu với Flyway

Thứ tự thực thi các script migration cho module Identity & Access:

1. `V1__create_identity_extensions.sql`: Cấu hình extension UUID và các hàm tiện ích.
2. `V2__create_tenants_branches_users.sql`: Tạo bảng `tenants`, `branches`, `users`, các khóa ngoại, kiểm tra check constraint và comment mô tả cột.
3. `V3__create_memberships.sql`: Tạo bảng `memberships`, các ràng buộc vai trò/chi nhánh, chỉ mục duy nhất một phần (partial unique index).
4. `V4__create_invitation_deliveries.sql`: Tạo bảng theo dõi tiến trình gửi email kích hoạt qua Cognito.
5. `V5__create_idempotency_outbox.sql`: Tạo bảng lưu trữ `idempotency_records` và `outbox_events`.
6. `V6__create_p1_pos_device_pin.sql`: Tạo các bảng thiết bị POS và mã PIN thu ngân (bảo vệ sau cờ tính năng P1).
7. `V7__seed_local_demo_identity.sql`: Nạp dữ liệu mẫu ban đầu cho môi trường local demo (chỉ chứa dữ liệu giả lập, không chứa thông tin nhạy cảm).

---

## Danh mục Quyền Tiêu chuẩn (Permission Catalog)

Các quyền hạn do hệ thống quản lý tập trung và đánh giá phía máy chủ:

| Mã quyền hạn | Vai trò mặc định sở hữu | Phạm vi tác động |
|---|---|---|
| `tenant:read` | `OWNER` | Toàn bộ chuỗi thương hiệu |
| `branch:read` | `OWNER`, `MANAGER`, `CASHIER`, `KITCHEN` | Chi nhánh được phân công hoặc đang chọn |
| `branch:manage` | `OWNER` | Toàn bộ chuỗi thương hiệu |
| `employee:read` | `OWNER`, `MANAGER` | Toàn chuỗi hoặc chi nhánh phụ trách |
| `employee:invite` | `OWNER`, `MANAGER` (giới hạn vai trò) | Chi nhánh phụ trách |
| `employee:manage` | `OWNER`, `MANAGER` (giới hạn vai trò) | Chi nhánh phụ trách |
| `membership:assign` | `OWNER`, `MANAGER` (giới hạn vai trò) | Chi nhánh phụ trách |
| `membership:suspend` | `OWNER`, `MANAGER` (giới hạn vai trò) | Chi nhánh phụ trách |
| `membership:revoke` | `OWNER`, `MANAGER` (giới hạn vai trò) | Chi nhánh phụ trách |
| `pos-device:manage` | `OWNER`, `MANAGER` (P1) | Chi nhánh phụ trách |
| `pos-pin:manage-self` | `CASHIER`, `OWNER` kiêm thu ngân (P1) | Thiết bị và chi nhánh phụ trách |
| `pos:quick-switch` | Mọi phiên POS đã xác thực (P1) | Thiết bị chi nhánh; chỉ được đổi sang Thu ngân |
