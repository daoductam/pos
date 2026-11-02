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
- Mọi thao tác ghi có khả năng thử lại bắt buộc phải có `Idempotency-Key`.
- Mọi thao tác cập nhật tài nguyên có phiên bản bắt buộc phải truyền `If-Match`.

---

## Hợp đồng HTTP Chung (Common HTTP Contract)

### Các Tiêu đề HTTP (Headers)

| Tiêu đề (Header) | Bắt buộc | Ý nghĩa và Quy ước sử dụng |
|---|:---:|---|
| `Authorization` | Bắt buộc cho Public API | `Bearer <Cognito access token>`; việc đăng nhập diễn ra tại giao diện Cognito Hosted UI / SDK, service này không trực tiếp xử lý form mật khẩu. |
| `X-Correlation-Id` | Khuyến nghị (Tự sinh nếu thiếu) | Mã định danh truy vết liên kết xuyên suốt request, audit log, outbox event và các lời gọi service cấp dưới. |
| `Idempotency-Key` | Bắt buộc cho các lệnh ghi có thể retry | Khóa do client sinh ra gắn với người gọi và tenant; việc gửi lại cùng một khóa bắt buộc phải có cùng mã băm (request hash). |
| `If-Match` | Bắt buộc khi cập nhật tài nguyên có version | Mã phiên bản ETag thu được từ lần đọc trước đó (ví dụ: `"branch-v1"` hoặc `"membership-v7"`). |
| `X-Active-Branch-Id` | Khi gọi các API gắn với chi nhánh | Chi nhánh do client chọn; máy chủ bắt buộc kiểm tra lại với membership hiện tại trước khi xử lý. |
| `X-Pos-Operator-Context`| Giai đoạn P1 sau khi đổi PIN | Ngữ cảnh tạm thời do endpoint xác thực mã PIN trả về; downstream service chuyển tiếp về Identity Access để kiểm tra. |
| `Content-Type` | Bắt buộc với request có body | `application/json; charset=utf-8`. |

---

### Cấu trúc Bao bì Chuẩn hóa (Standard Envelope)

#### Phản hồi Thành công Đối tượng Đơn lẻ (Single Entity Response)

```json
{
  "data": {},
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

#### Phản hồi Danh sách Phân trang (Paginated Collection Response)

Module Identity & Access sử dụng phân trang trang (Offset Pagination) phù hợp cho bảng danh sách quản trị (Admin grid) với các trường `meta.pagination`:

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
    "details": [
      {
        "field": "branchId",
        "code": "branch_not_assigned",
        "message": "Chi nhánh không thuộc danh sách quản lý của người dùng"
      }
    ],
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

#### Bảng Quy tắc Mã Lỗi của Module (Error Codes)

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

## Bề mặt API Công khai (Public API Surface Summary)

| Phương thức | Đường dẫn (Path) | Quyền hạn (Scope) | Idempotent | If-Match | Mã HTTP Thành công |
|---|---|---|:---:|:---:|---|
| **GET** | `/api/v1/me` | Mọi user đã xác thực | Không | Không | `200 OK` |
| **POST** | `/api/v1/tenants` | Platform Admin / Workload | Bắt buộc | Không | `201 Created` |
| **GET** | `/api/v1/tenants/{tenantId}/branches` | `branch:read` | Không | Không | `200 OK` |
| **POST** | `/api/v1/tenants/{tenantId}/branches` | `branch:manage` (`OWNER`) | Bắt buộc | Không | `201 Created` |
| **GET** | `/api/v1/tenants/{tenantId}/branches/{branchId}` | `branch:read` | Không | Không | `200 OK` |
| **PATCH** | `/api/v1/tenants/{tenantId}/branches/{branchId}` | `branch:manage` (`OWNER`) | Bắt buộc | Bắt buộc | `200 OK` |
| **POST** | `/api/v1/tenants/{tenantId}/branches/{branchId}/archive` | `branch:manage` (`OWNER`) | Bắt buộc | Bắt buộc | `200 OK` |
| **GET** | `/api/v1/tenants/{tenantId}/employees` | `employee:read` | Không | Không | `200 OK` |
| **GET** | `/api/v1/tenants/{tenantId}/employees/{userId}` | `employee:read` | Không | Không | `200 OK` |
| **PATCH** | `/api/v1/tenants/{tenantId}/employees/{userId}` | `employee:manage` | Bắt buộc | Bắt buộc | `200 OK` |
| **GET** | `/api/v1/tenants/{tenantId}/memberships` | `employee:read` | Không | Không | `200 OK` |
| **POST** | `/api/v1/tenants/{tenantId}/memberships` | `employee:invite` | Bắt buộc | Không | `201 Created` |
| **GET** | `/api/v1/tenants/{tenantId}/memberships/{membershipId}`| `employee:read` | Không | Không | `200 OK` |
| **PATCH** | `/api/v1/tenants/{tenantId}/memberships/{membershipId}`| `membership:assign` | Bắt buộc | Bắt buộc | `200 OK` |
| **POST** | `/api/v1/tenants/{tenantId}/memberships/{membershipId}/suspend` | `membership:suspend` | Bắt buộc | Bắt buộc | `200 OK` |
| **POST** | `/api/v1/tenants/{tenantId}/memberships/{membershipId}/reactivate` | `membership:suspend` | Bắt buộc | Bắt buộc | `200 OK` |
| **POST** | `/api/v1/tenants/{tenantId}/memberships/{membershipId}/revoke` | `membership:revoke` | Bắt buộc | Bắt buộc | `200 OK` |
| **POST** | `/api/v1/tenants/{tenantId}/memberships/{membershipId}/invitation-resends` | `employee:invite` | Bắt buộc | Không | `202 Accepted` |
| **GET** | `/api/v1/tenants/{tenantId}/branches/{branchId}/pos-devices` (P1) | `pos-device:manage` | Không | Không | `200 OK` |
| **POST** | `/api/v1/tenants/{tenantId}/branches/{branchId}/pos-devices` (P1) | `pos-device:manage` | Bắt buộc | Không | `201 Created` |
| **POST** | `/api/v1/pos-devices/{deviceId}/revoke` (P1) | `pos-device:manage` | Bắt buộc | Không | `200 OK` |
| **PUT** | `/api/v1/pos-devices/{deviceId}/cashier-pins/me` (P1) | `pos-pin:manage-self` | Bắt buộc | Không | `204 No Content` |
| **POST** | `/api/v1/pos-devices/{deviceId}/quick-switch` (P1) | `pos:quick-switch` | Bắt buộc | Không | `200 OK` |
| **POST** | `/api/v1/pos-devices/{deviceId}/quick-switch-sessions/{sessionId}/revoke` (P1) | `pos:quick-switch` | Bắt buộc | Không | `204 No Content` |

---

## Chi tiết Hợp đồng Từng API Công khai (Public API Details)

### 1. Thông tin Định danh & Ngữ cảnh Hiện tại

#### `GET /api/v1/me`
Lấy hồ sơ cá nhân của người dùng đã đăng nhập và toàn bộ danh sách tư cách thành viên (`memberships`) cùng danh mục quyền hạn thực tế. Máy chủ hoàn toàn chịu trách nhiệm phân giải quyền từ Identity DB; tuyệt đối không chấp nhận bất kỳ tham số quyền hay vai trò nào do client gửi lên.

- **Thẩm quyền:** Bất kỳ người dùng nào có Cognito JWT hợp lệ.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <Cognito access token>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Tùy chọn)
- **Tham số Đường dẫn (Path Parameters):** Không có.
- **Tham số Truy vấn (Query Parameters):** Không có.
- **Dữ liệu Gửi lên (Request Body):** Không có.
- **Phản hồi Thành công (`200 OK`):**

```json
{
  "data": {
    "user": {
      "id": "0192b2d0-7f1a-7abc-8d2c-123456789012",
      "email": "manager@coffeechain.com",
      "displayName": "Nguyễn Văn Quản Lý",
      "phone": "+84901234567",
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
          "employee:read",
          "employee:invite",
          "employee:manage",
          "membership:assign",
          "membership:suspend",
          "membership:revoke"
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

- **Mã lỗi khả dĩ:**
  - `401 Unauthorized` (`unauthorized`): Token thiếu, hết hạn hoặc không hợp lệ.
  - `503 Service Unavailable` (`authorization_unavailable`): Không thể kết nối cơ sở dữ liệu Identity DB.

---

### 2. Khởi tạo Tenant Nền tảng (Platform Bootstrap)

#### `POST /api/v1/tenants`
Khởi tạo một tenant mới và gửi email lời mời đầu tiên cho Chủ chuỗi (`OWNER`). Đây là endpoint đặc quyền dành riêng cho Platform Admin hoặc tác vụ khởi tạo nền tảng (Bootstrap job).

- **Thẩm quyền:** Platform Admin / Workload Identity.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <Workload token>` (Bắt buộc)
  - `Idempotency-Key: <unique-key>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Khuyến nghị)
  - `Content-Type: application/json; charset=utf-8` (Bắt buộc)
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "name": "Chuỗi Cà Phê Mới",
  "owner": {
    "email": "owner@coffeechain.com",
    "displayName": "Chủ Thương Hiệu"
  }
}
```

*Quy cách kiểm tra (Validation):*
- `name`: Chuỗi ký tự từ 1 đến 200 ký tự Unicode, không được để trống.
- `owner.email`: Chuỗi định dạng email hợp lệ, tối đa 255 ký tự; hệ thống tự động chuẩn hóa về chữ thường (`trim + lowercase`).
- `owner.displayName`: Chuỗi từ 1 đến 200 ký tự.

- **Phản hồi Thành công (`201 Created`):**

```json
{
  "data": {
    "tenant": {
      "id": "0192b2d0-7f1c-7abc-8d2c-123456789012",
      "name": "Chuỗi Cà Phê Mới",
      "status": "ACTIVE",
      "version": 1,
      "createdAt": "2026-10-12T08:00:00Z",
      "updatedAt": "2026-10-12T08:00:00Z"
    },
    "ownerMembership": {
      "id": "0192b2d0-7f1b-7abc-8d2c-123456789012",
      "tenantId": "0192b2d0-7f1c-7abc-8d2c-123456789012",
      "branchId": null,
      "userId": "0192b2d0-7f1a-7abc-8d2c-123456789012",
      "email": "owner@coffeechain.com",
      "role": "OWNER",
      "status": "INVITED",
      "invitationStatus": "QUEUED",
      "permissions": [
        "tenant:read",
        "branch:read",
        "branch:manage",
        "employee:read",
        "employee:invite",
        "employee:manage",
        "membership:assign",
        "membership:suspend",
        "membership:revoke"
      ],
      "version": 1,
      "createdAt": "2026-10-12T08:00:00Z",
      "updatedAt": "2026-10-12T08:00:00Z"
    },
    "invitationDelivery": {
      "deliveryId": "0192b2d0-7f2a-7abc-8d2c-123456789012",
      "membershipId": "0192b2d0-7f1b-7abc-8d2c-123456789012",
      "status": "QUEUED",
      "attemptCount": 0,
      "nextAttemptAt": "2026-10-12T08:00:05Z",
      "lastErrorCode": null
    }
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

- **Mã lỗi khả dĩ:**
  - `401 Unauthorized` (`unauthorized`): Thiếu thông tin xác thực Platform Admin.
  - `403 Forbidden` (`forbidden`): Token người dùng thông thường không có quyền gọi bootstrap.
  - `409 Conflict` (`conflict`): Tenant hoặc tài khoản Owner đã tồn tại.
  - `409 Conflict` (`duplicate_request`): Trùng `Idempotency-Key` nhưng body khác nhau.
  - `422 Unprocessable Entity` (`validation_error`): Tên tenant rỗng hoặc email sai định dạng.
  - `503 Service Unavailable` (`cognito_unavailable`): Cognito tạm thời không khả dụng (lời mời vẫn được lưu trữ bền vững để thử lại).

---

### 3. Quản lý Chi nhánh (Branches)

#### `GET /api/v1/tenants/{tenantId}/branches`
Lấy danh sách các chi nhánh thuộc tenant. Chủ chuỗi (`OWNER`) được xem toàn bộ chi nhánh; Quản lý (`MANAGER`) chỉ được xem các chi nhánh được phân công.

- **Thẩm quyền:** Quyền `branch:read`.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Tùy chọn)
- **Tham số Đường dẫn (Path Parameters):**
  - `tenantId` (UUID, Bắt buộc): Mã định danh tenant.
- **Tham số Truy vấn (Query Parameters):**
  - `page` (integer, Mặc định: `1`): Số thứ tự trang.
  - `limit` (integer, Mặc định: `20`, Tối đa: `100`): Số bản ghi trên mỗi trang.
  - `status` (string, Tùy chọn): Lọc theo trạng thái (`ACTIVE`, `ARCHIVED`).
  - `search` (string, Tùy chọn): Tìm kiếm gần đúng theo mã `code` hoặc tên `name`.
  - `sort` (string, Mặc định: `code:asc`): Quy tắc sắp xếp (`name:asc`, `createdAt:desc`, v.v.).
- **Phản hồi Thành công (`200 OK`):**

```json
{
  "data": [
    {
      "id": "0192b2d0-7f1d-7abc-8d2c-123456789012",
      "tenantId": "0192b2d0-7f1c-7abc-8d2c-123456789012",
      "code": "D1",
      "name": "Chi nhánh Quận 1",
      "address": "123 Đường Nguyễn Huệ, Phường Bến Nghé, Quận 1, TP.HCM",
      "status": "ACTIVE",
      "version": 3,
      "createdAt": "2026-10-12T08:10:00Z",
      "updatedAt": "2026-10-12T09:00:00Z"
    }
  ],
  "meta": {
    "pagination": {
      "page": 1,
      "limit": 20,
      "total": 1,
      "totalPages": 1
    },
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

- **Mã lỗi khả dĩ:**
  - `401 Unauthorized` (`unauthorized`): Token không hợp lệ.
  - `403 Forbidden` (`forbidden`): Không có quyền xem danh sách chi nhánh của tenant này.
  - `404 Not Found` (`not_found`): Tenant không tồn tại hoặc người dùng không có liên kết nào.

---

#### `POST /api/v1/tenants/{tenantId}/branches`
Tạo mới một chi nhánh trong tenant. Mã chi nhánh `code` là bất biến sau khi tạo và phải là duy nhất trong cùng tenant.

- **Thẩm quyền:** Quyền `branch:manage` (Chỉ `OWNER`).
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `Idempotency-Key: <unique-key>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Khuyến nghị)
  - `Content-Type: application/json; charset=utf-8` (Bắt buộc)
- **Tham số Đường dẫn (Path Parameters):**
  - `tenantId` (UUID, Bắt buộc): Mã định danh tenant.
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "code": "D3",
  "name": "Chi nhánh Quận 3",
  "address": "456 Đường Võ Văn Tần, Phường 5, Quận 3, TP.HCM"
}
```

*Quy cách kiểm tra:*
- `code`: Chuỗi từ 1 đến 50 ký tự chữ và số (chữ cái, chữ số, gạch ngang, gạch dưới); không chứa khoảng trắng; duy nhất trong phạm vi tenant.
- `name`: Chuỗi từ 1 đến 200 ký tự Unicode, bắt buộc.
- `address`: Chuỗi tối đa 500 ký tự (có thể `null`).

- **Phản hồi Thành công (`201 Created`):**

```json
{
  "data": {
    "id": "0192b2d0-7f30-7abc-8d2c-123456789012",
    "tenantId": "0192b2d0-7f1c-7abc-8d2c-123456789012",
    "code": "D3",
    "name": "Chi nhánh Quận 3",
    "address": "456 Đường Võ Văn Tần, Phường 5, Quận 3, TP.HCM",
    "status": "ACTIVE",
    "version": 1,
    "createdAt": "2026-10-12T09:30:00Z",
    "updatedAt": "2026-10-12T09:30:00Z"
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

- **Mã lỗi khả dĩ:**
  - `403 Forbidden` (`forbidden`): Chỉ `OWNER` mới có quyền tạo chi nhánh.
  - `409 Conflict` (`conflict`): Mã chi nhánh `code` đã tồn tại trong tenant này.
  - `422 Unprocessable Entity` (`validation_error`): Thiếu trường bắt buộc hoặc mã chi nhánh sai định dạng.

---

#### `GET /api/v1/tenants/{tenantId}/branches/{branchId}`
Xem thông tin chi tiết của một chi nhánh cụ thể.

- **Thẩm quyền:** Quyền `branch:read` trong phạm vi chi nhánh đó.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Tùy chọn)
- **Tham số Đường dẫn (Path Parameters):**
  - `tenantId` (UUID, Bắt buộc)
  - `branchId` (UUID, Bắt buộc)
- **Phản hồi Thành công (`200 OK`):**

```json
{
  "data": {
    "id": "0192b2d0-7f1d-7abc-8d2c-123456789012",
    "tenantId": "0192b2d0-7f1c-7abc-8d2c-123456789012",
    "code": "D1",
    "name": "Chi nhánh Quận 1",
    "address": "123 Đường Nguyễn Huệ, Phường Bến Nghé, Quận 1, TP.HCM",
    "status": "ACTIVE",
    "version": 3,
    "createdAt": "2026-10-12T08:10:00Z",
    "updatedAt": "2026-10-12T09:00:00Z"
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

- **Mã lỗi khả dĩ:**
  - `401 Unauthorized`, `403 Forbidden`, `404 Not Found`.

---

#### `PATCH /api/v1/tenants/{tenantId}/branches/{branchId}`
Cập nhật tên hoặc địa chỉ của chi nhánh. Mã chi nhánh `code` là bất biến và không được phép cập nhật. Endpoint này đòi hỏi cơ chế khóa lạc quan (Optimistic Concurrency) thông qua header `If-Match`.

- **Thẩm quyền:** Quyền `branch:manage` (`OWNER`).
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `If-Match: "branch-v3"` (Bắt buộc - ETag phiên bản thu được từ lần đọc trước)
  - `Idempotency-Key: <unique-key>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Khuyến nghị)
  - `Content-Type: application/json; charset=utf-8` (Bắt buộc)
- **Tham số Đường dẫn (Path Parameters):**
  - `tenantId` (UUID, Bắt buộc)
  - `branchId` (UUID, Bắt buộc)
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "name": "Chi nhánh Flagship Quận 1",
  "address": "123-125 Đường Nguyễn Huệ, Phường Bến Nghé, Quận 1, TP.HCM"
}
```

- **Phản hồi Thành công (`200 OK`):**

```json
{
  "data": {
    "id": "0192b2d0-7f1d-7abc-8d2c-123456789012",
    "tenantId": "0192b2d0-7f1c-7abc-8d2c-123456789012",
    "code": "D1",
    "name": "Chi nhánh Flagship Quận 1",
    "address": "123-125 Đường Nguyễn Huệ, Phường Bến Nghé, Quận 1, TP.HCM",
    "status": "ACTIVE",
    "version": 4,
    "createdAt": "2026-10-12T08:10:00Z",
    "updatedAt": "2026-10-12T10:00:00Z"
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

- **Mã lỗi khả dĩ:**
  - `403 Forbidden` (`forbidden`): Người gọi không phải `OWNER`.
  - `404 Not Found` (`not_found`): Không tìm thấy chi nhánh.
  - `409 Conflict` (`conflict`): Phiên bản trong header `If-Match` không khớp với phiên bản hiện tại trên database (dữ liệu đã bị người khác sửa đổi).
  - `422 Unprocessable Entity` (`validation_error`): Tên rỗng hoặc vượt quá 200 ký tự.

---

#### `POST /api/v1/tenants/{tenantId}/branches/{branchId}/archive`
Lưu trữ chi nhánh (`status: ARCHIVED` - xóa mềm). Chi nhánh sau khi lưu trữ sẽ bị ẩn khỏi danh sách chọn làm việc, nhưng dữ liệu lịch sử đơn hàng, ca làm việc và doanh thu vẫn được bảo toàn nguyên vẹn.

- **Thẩm quyền:** Quyền `branch:manage` (`OWNER`).
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `If-Match: "branch-v4"` (Bắt buộc)
  - `Idempotency-Key: <unique-key>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Khuyến nghị)
  - `Content-Type: application/json; charset=utf-8` (Bắt buộc)
- **Tham số Đường dẫn (Path Parameters):**
  - `tenantId` (UUID, Bắt buộc)
  - `branchId` (UUID, Bắt buộc)
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "reason": "Hết hạn hợp đồng thuê mặt bằng kinh doanh"
}
```

*Quy cách kiểm tra:* `reason` tối đa 500 ký tự, bắt buộc nhằm mục đích kiểm toán.

- **Phản hồi Thành công (`200 OK`):**

```json
{
  "data": {
    "id": "0192b2d0-7f1d-7abc-8d2c-123456789012",
    "tenantId": "0192b2d0-7f1c-7abc-8d2c-123456789012",
    "code": "D1",
    "name": "Chi nhánh Flagship Quận 1",
    "address": "123-125 Đường Nguyễn Huệ, Phường Bến Nghé, Quận 1, TP.HCM",
    "status": "ARCHIVED",
    "version": 5,
    "createdAt": "2026-10-12T08:10:00Z",
    "updatedAt": "2026-10-12T11:00:00Z"
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

- **Mã lỗi khả dĩ:**
  - `403 Forbidden`, `404 Not Found`, `409 Conflict`, `422 Unprocessable Entity`.

---

### 4. Quản lý Nhân sự & Hồ sơ Người dùng (Employees & Profiles)

#### `GET /api/v1/tenants/{tenantId}/employees`
Lấy danh sách hồ sơ nhân viên cùng danh sách các tư cách thành viên (`memberships`) trong phạm vi nhìn thấy được. `OWNER` xem toàn chuỗi; `MANAGER` chỉ xem được các nhân viên thuộc chi nhánh mình quản lý.

- **Thẩm quyền:** Quyền `employee:read`.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Tùy chọn)
- **Tham số Đường dẫn (Path Parameters):**
  - `tenantId` (UUID, Bắt buộc)
- **Tham số Truy vấn (Query Parameters):**
  - `page` (integer, Mặc định: `1`): Số trang.
  - `limit` (integer, Mặc định: `20`, Tối đa: `100`): Kích thước trang.
  - `branchId` (UUID, Tùy chọn): Lọc nhân viên theo chi nhánh (Manager chỉ được lọc trong các chi nhánh mình quản lý).
  - `role` (string, Tùy chọn): Lọc theo vai trò (`OWNER`, `MANAGER`, `CASHIER`, `KITCHEN`).
  - `status` (string, Tùy chọn): Lọc theo trạng thái hồ sơ (`ACTIVE`, `SUSPENDED`, `REVOKED`).
  - `search` (string, Tùy chọn): Tìm kiếm theo email, số điện thoại hoặc họ tên hiển thị.
  - `sort` (string, Mặc định: `displayName:asc`): Sắp xếp.
- **Phản hồi Thành công (`200 OK`):**

```json
{
  "data": [
    {
      "userId": "0192b2d0-7f1a-7abc-8d2c-123456789012",
      "email": "cashier@coffeechain.com",
      "displayName": "Nguyễn Văn Thu Ngân",
      "phone": "+84901234567",
      "status": "ACTIVE",
      "memberships": [
        {
          "id": "0192b2d0-7f1b-7abc-8d2c-123456789012",
          "tenantId": "0192b2d0-7f1c-7abc-8d2c-123456789012",
          "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
          "branchName": "Chi nhánh Quận 1",
          "role": "CASHIER",
          "status": "ACTIVE",
          "version": 1
        }
      ],
      "createdAt": "2026-10-12T08:30:00Z",
      "updatedAt": "2026-10-12T08:30:00Z"
    }
  ],
  "meta": {
    "pagination": {
      "page": 1,
      "limit": 20,
      "total": 1,
      "totalPages": 1
    },
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

- **Mã lỗi khả dĩ:**
  - `401 Unauthorized`, `403 Forbidden`, `404 Not Found`, `422 Unprocessable Entity`.

---

#### `GET /api/v1/tenants/{tenantId}/employees/{userId}`
Xem thông tin chi tiết một nhân viên cụ thể.

- **Thẩm quyền:** Quyền `employee:read` trong phạm vi chi nhánh của nhân viên đó.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Tùy chọn)
- **Tham số Đường dẫn (Path Parameters):**
  - `tenantId` (UUID, Bắt buộc)
  - `userId` (UUID, Bắt buộc)
- **Phản hồi Thành công (`200 OK`):**

```json
{
  "data": {
    "userId": "0192b2d0-7f1a-7abc-8d2c-123456789012",
    "email": "cashier@coffeechain.com",
    "displayName": "Nguyễn Văn Thu Ngân",
    "phone": "+84901234567",
    "status": "ACTIVE",
    "memberships": [
      {
        "id": "0192b2d0-7f1b-7abc-8d2c-123456789012",
        "tenantId": "0192b2d0-7f1c-7abc-8d2c-123456789012",
        "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
        "branchName": "Chi nhánh Quận 1",
        "role": "CASHIER",
        "status": "ACTIVE",
        "version": 1
      }
    ],
    "createdAt": "2026-10-12T08:30:00Z",
    "updatedAt": "2026-10-12T08:30:00Z"
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

- **Mã lỗi khả dĩ:**
  - `401 Unauthorized`, `403 Forbidden`, `404 Not Found`.

---

#### `PATCH /api/v1/tenants/{tenantId}/employees/{userId}`
Cập nhật thông tin hồ sơ nhân viên (`displayName`, `phone`). Lưu ý rằng email đăng nhập và Cognito `sub` được bảo vệ và không được phép cập nhật qua endpoint này.

- **Thẩm quyền:** Quyền `employee:manage`.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `If-Match: "user-v1"` (Bắt buộc)
  - `Idempotency-Key: <unique-key>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Khuyến nghị)
  - `Content-Type: application/json; charset=utf-8` (Bắt buộc)
- **Tham số Đường dẫn (Path Parameters):**
  - `tenantId` (UUID, Bắt buộc)
  - `userId` (UUID, Bắt buộc)
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "displayName": "Nguyễn Văn Thu Ngân (Ca Sáng)",
  "phone": "+84909876543"
}
```

*Quy cách kiểm tra:*
- `displayName`: Chuỗi từ 1 đến 200 ký tự Unicode (tùy chọn).
- `phone`: Chuỗi định dạng số điện thoại E.164 (ví dụ: `+84...`), tối đa 20 ký tự (tùy chọn hoặc `null`).

- **Phản hồi Thành công (`200 OK`):**

```json
{
  "data": {
    "userId": "0192b2d0-7f1a-7abc-8d2c-123456789012",
    "email": "cashier@coffeechain.com",
    "displayName": "Nguyễn Văn Thu Ngân (Ca Sáng)",
    "phone": "+84909876543",
    "status": "ACTIVE",
    "memberships": [
      {
        "id": "0192b2d0-7f1b-7abc-8d2c-123456789012",
        "tenantId": "0192b2d0-7f1c-7abc-8d2c-123456789012",
        "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
        "branchName": "Chi nhánh Quận 1",
        "role": "CASHIER",
        "status": "ACTIVE",
        "version": 1
      }
    ],
    "createdAt": "2026-10-12T08:30:00Z",
    "updatedAt": "2026-10-12T12:00:00Z"
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

- **Mã lỗi khả dĩ:**
  - `403 Forbidden`, `404 Not Found`, `409 Conflict`, `422 Unprocessable Entity`.

---

### 5. Quản lý Tư cách Thành viên & Lời mời (Memberships & Invitations)

#### `GET /api/v1/tenants/{tenantId}/memberships`
Lấy danh sách chi tiết các bản ghi membership dùng cho bảng phân quyền quản trị và kiểm toán.

- **Thẩm quyền:** Quyền `employee:read`.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Tùy chọn)
- **Tham số Đường dẫn (Path Parameters):**
  - `tenantId` (UUID, Bắt buộc)
- **Tham số Truy vấn (Query Parameters):**
  - `page` (integer, Mặc định: `1`)
  - `limit` (integer, Mặc định: `20`, Tối đa: `100`)
  - `branchId` (UUID, Tùy chọn)
  - `role` (string, Tùy chọn: `OWNER`, `MANAGER`, `CASHIER`, `KITCHEN`)
  - `status` (string, Tùy chọn: `INVITED`, `ACTIVE`, `SUSPENDED`, `REVOKED`)
  - `email` (string, Tùy chọn)
  - `sort` (string, Mặc định: `createdAt:desc`)
- **Phản hồi Thành công (`200 OK`):**

```json
{
  "data": [
    {
      "id": "0192b2d0-7f1b-7abc-8d2c-123456789012",
      "tenantId": "0192b2d0-7f1c-7abc-8d2c-123456789012",
      "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
      "userId": "0192b2d0-7f1a-7abc-8d2c-123456789012",
      "email": "cashier@coffeechain.com",
      "role": "CASHIER",
      "status": "INVITED",
      "invitationStatus": "SENT",
      "permissions": [
        "branch:read",
        "order:read",
        "order:create",
        "order:checkout"
      ],
      "version": 1,
      "createdAt": "2026-10-12T08:30:00Z",
      "updatedAt": "2026-10-12T08:31:00Z"
    }
  ],
  "meta": {
    "pagination": {
      "page": 1,
      "limit": 20,
      "total": 1,
      "totalPages": 1
    },
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

- **Mã lỗi khả dĩ:**
  - `401 Unauthorized`, `403 Forbidden`, `404 Not Found`, `422 Unprocessable Entity`.

---

#### `POST /api/v1/tenants/{tenantId}/memberships`
Gửi lời mời nhân viên mới tham gia vào tenant/chi nhánh. Thao tác này khởi tạo hồ sơ nhân viên (nếu chưa có), tạo bản ghi membership ở trạng thái `INVITED` và phát sinh yêu cầu gửi email kích hoạt qua Cognito.

- **Thẩm quyền:** Quyền `employee:invite`.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `Idempotency-Key: <unique-key>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Khuyến nghị)
  - `Content-Type: application/json; charset=utf-8` (Bắt buộc)
- **Tham số Đường dẫn (Path Parameters):**
  - `tenantId` (UUID, Bắt buộc)
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "email": "new.cashier@coffeechain.com",
  "displayName": "Trần Thị Thu Ngân Mới",
  "phone": "+84901112223",
  "role": "CASHIER",
  "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789012"
}
```

*Quy tắc nghiệp vụ & Ràng buộc (Validation):*
- `role = 'OWNER'`: Bắt buộc `branchId` phải là `null`. Chỉ `OWNER` hiện tại mới có quyền mời `OWNER` mới.
- `role IN ('MANAGER', 'CASHIER', 'KITCHEN')`: Bắt buộc phải có `branchId` thuộc một chi nhánh `ACTIVE` trong cùng tenant.
- Quản lý chi nhánh (`MANAGER`) chỉ được phép mời nhân viên vào các vai trò `CASHIER` hoặc `KITCHEN` trong phạm vi chi nhánh được giao quản lý. Cố tình mời `OWNER`, `MANAGER` hoặc mời vào chi nhánh khác sẽ bị chặn với lỗi `403 Forbidden`.
- `email`: Tự động chuẩn hóa chữ thường. Nếu nhân viên đã có một membership đang `INVITED` hoặc `ACTIVE` tại cùng chi nhánh đó, hệ thống sẽ trả về lỗi `409 Conflict` (`invitation_pending`).

- **Phản hồi Thành công (`201 Created`):**

```json
{
  "data": {
    "id": "0192b2d0-7f40-7abc-8d2c-123456789012",
    "tenantId": "0192b2d0-7f1c-7abc-8d2c-123456789012",
    "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
    "userId": "0192b2d0-7f41-7abc-8d2c-123456789012",
    "email": "new.cashier@coffeechain.com",
    "role": "CASHIER",
    "status": "INVITED",
    "invitationStatus": "QUEUED",
    "permissions": [
      "branch:read",
      "order:read",
      "order:create",
      "order:checkout"
    ],
    "version": 1,
    "createdAt": "2026-10-12T13:00:00Z",
    "updatedAt": "2026-10-12T13:00:00Z"
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

- **Mã lỗi khả dĩ:**
  - `403 Forbidden` (`forbidden`): Vượt quá thẩm quyền của Manager.
  - `409 Conflict` (`invitation_pending`): Đã tồn tại lời mời hoặc tư cách thành viên cho email này tại chi nhánh.
  - `409 Conflict` (`duplicate_request`): Cùng `Idempotency-Key` nhưng body khác nhau.
  - `422 Unprocessable Entity` (`validation_error`): Sai định dạng email, vai trò không hợp lệ, hoặc chi nhánh không tồn tại/đã bị lưu trữ.
  - `429 Too Many Requests` (`rate_limited`): Vượt quá tần suất mời nhân viên (kèm header `Retry-After`).
  - `503 Service Unavailable` (`cognito_unavailable`): Lỗi từ dịch vụ Cognito.

---

#### `GET /api/v1/tenants/{tenantId}/memberships/{membershipId}`
Xem chi tiết một bản ghi membership cụ thể.

- **Thẩm quyền:** Quyền `employee:read` trong phạm vi chi nhánh của membership đó.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Tùy chọn)
- **Tham số Đường dẫn (Path Parameters):**
  - `tenantId` (UUID, Bắt buộc)
  - `membershipId` (UUID, Bắt buộc)
- **Phản hồi Thành công (`200 OK`):**

```json
{
  "data": {
    "id": "0192b2d0-7f1b-7abc-8d2c-123456789012",
    "tenantId": "0192b2d0-7f1c-7abc-8d2c-123456789012",
    "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
    "userId": "0192b2d0-7f1a-7abc-8d2c-123456789012",
    "email": "cashier@coffeechain.com",
    "role": "CASHIER",
    "status": "ACTIVE",
    "invitationStatus": "ACCEPTED",
    "permissions": [
      "branch:read",
      "order:read",
      "order:create",
      "order:checkout"
    ],
    "version": 2,
    "createdAt": "2026-10-12T08:30:00Z",
    "updatedAt": "2026-10-12T08:35:00Z"
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

- **Mã lỗi khả dĩ:**
  - `401 Unauthorized`, `403 Forbidden`, `404 Not Found`.

---

#### `PATCH /api/v1/tenants/{tenantId}/memberships/{membershipId}`
Điều chỉnh vai trò (`role`) hoặc luân chuyển chi nhánh (`branchId`) của nhân viên. Các thao tác thay đổi vòng đời (đình chỉ, khôi phục, thu hồi) bắt buộc phải sử dụng các endpoint chuyên biệt bên dưới.

- **Thẩm quyền:** Quyền `membership:assign`.
  - `OWNER`: Có toàn quyền điều chỉnh mọi vai trò và chi nhánh.
  - `MANAGER`: Chỉ được phép đổi giữa `CASHIER` và `KITCHEN` trong phạm vi các chi nhánh mình quản lý.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `If-Match: "membership-v2"` (Bắt buộc - ETag phiên bản hiện tại)
  - `Idempotency-Key: <unique-key>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Khuyến nghị)
  - `Content-Type: application/json; charset=utf-8` (Bắt buộc)
- **Tham số Đường dẫn (Path Parameters):**
  - `tenantId` (UUID, Bắt buộc)
  - `membershipId` (UUID, Bắt buộc)
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "role": "KITCHEN",
  "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789012"
}
```

- **Phản hồi Thành công (`200 OK`):**

```json
{
  "data": {
    "id": "0192b2d0-7f1b-7abc-8d2c-123456789012",
    "tenantId": "0192b2d0-7f1c-7abc-8d2c-123456789012",
    "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
    "userId": "0192b2d0-7f1a-7abc-8d2c-123456789012",
    "email": "cashier@coffeechain.com",
    "role": "KITCHEN",
    "status": "ACTIVE",
    "invitationStatus": "ACCEPTED",
    "permissions": [
      "branch:read",
      "kds:view",
      "kds:update"
    ],
    "version": 3,
    "createdAt": "2026-10-12T08:30:00Z",
    "updatedAt": "2026-10-12T14:00:00Z"
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

- **Mã lỗi khả dĩ:**
  - `403 Forbidden` (`forbidden`): Manager cố tình gán vai trò `OWNER`/`MANAGER` hoặc chuyển sang chi nhánh khác.
  - `404 Not Found` (`not_found`): Membership không tồn tại.
  - `409 Conflict` (`last_owner_protected`): Thao tác hạ vai trò của tài khoản `OWNER` duy nhất còn lại.
  - `409 Conflict` (`conflict`): Xung đột phiên bản `If-Match`.
  - `422 Unprocessable Entity` (`validation_error`): Vai trò `OWNER` nhưng `branchId` không phải `null`.

---

#### `POST /api/v1/tenants/{tenantId}/memberships/{membershipId}/suspend`
Tạm đình chỉ quyền làm việc của nhân viên (`status: SUSPENDED`). Lệnh này có hiệu lực ngay lập tức; các request tiếp theo của nhân viên tại chi nhánh đó sẽ bị chặn hoàn toàn.

- **Thẩm quyền:** Quyền `membership:suspend`.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `If-Match: "membership-v3"` (Bắt buộc)
  - `Idempotency-Key: <unique-key>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Khuyến nghị)
  - `Content-Type: application/json; charset=utf-8` (Bắt buộc)
- **Tham số Đường dẫn (Path Parameters):**
  - `tenantId` (UUID, Bắt buộc)
  - `membershipId` (UUID, Bắt buộc)
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "reason": "Tạm nghỉ phép cá nhân 1 tháng"
}
```

*Quy cách kiểm tra:* `reason` tối đa 500 ký tự.

- **Phản hồi Thành công (`200 OK`):**

```json
{
  "data": {
    "id": "0192b2d0-7f1b-7abc-8d2c-123456789012",
    "tenantId": "0192b2d0-7f1c-7abc-8d2c-123456789012",
    "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
    "userId": "0192b2d0-7f1a-7abc-8d2c-123456789012",
    "email": "cashier@coffeechain.com",
    "role": "KITCHEN",
    "status": "SUSPENDED",
    "invitationStatus": "ACCEPTED",
    "permissions": [],
    "version": 4,
    "createdAt": "2026-10-12T08:30:00Z",
    "updatedAt": "2026-10-12T14:30:00Z"
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

- **Mã lỗi khả dĩ:**
  - `403 Forbidden`, `404 Not Found`, `409 Conflict` (`last_owner_protected`, `membership_state_conflict`), `422 Unprocessable Entity`.

---

#### `POST /api/v1/tenants/{tenantId}/memberships/{membershipId}/reactivate`
Khôi phục quyền làm việc bình thường (`status: ACTIVE`) cho nhân viên đang bị tạm đình chỉ (`SUSPENDED`).

- **Thẩm quyền:** Quyền `membership:suspend`.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `If-Match: "membership-v4"` (Bắt buộc)
  - `Idempotency-Key: <unique-key>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Khuyến nghị)
  - `Content-Type: application/json; charset=utf-8` (Tùy chọn)
- **Tham số Đường dẫn (Path Parameters):**
  - `tenantId` (UUID, Bắt buộc)
  - `membershipId` (UUID, Bắt buộc)
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "reason": "Hoàn tất thời gian nghỉ phép, quay lại làm việc"
}
```

- **Phản hồi Thành công (`200 OK`):**

```json
{
  "data": {
    "id": "0192b2d0-7f1b-7abc-8d2c-123456789012",
    "tenantId": "0192b2d0-7f1c-7abc-8d2c-123456789012",
    "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
    "userId": "0192b2d0-7f1a-7abc-8d2c-123456789012",
    "email": "cashier@coffeechain.com",
    "role": "KITCHEN",
    "status": "ACTIVE",
    "invitationStatus": "ACCEPTED",
    "permissions": [
      "branch:read",
      "kds:view",
      "kds:update"
    ],
    "version": 5,
    "createdAt": "2026-10-12T08:30:00Z",
    "updatedAt": "2026-10-12T15:00:00Z"
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

- **Mã lỗi khả dĩ:**
  - `403 Forbidden`, `404 Not Found`, `409 Conflict` (`membership_state_conflict` - bản ghi không ở trạng thái `SUSPENDED`).

---

#### `POST /api/v1/tenants/{tenantId}/memberships/{membershipId}/revoke`
Thu hồi vĩnh viễn tư cách thành viên (`status: REVOKED`). Đây là trạng thái kết thúc (terminal state); bản ghi này không thể mở lại. Nếu nhân viên làm việc lại sau này, hệ thống sẽ tạo một bản ghi membership mới.

- **Thẩm quyền:** Quyền `membership:revoke`.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `If-Match: "membership-v5"` (Bắt buộc)
  - `Idempotency-Key: <unique-key>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Khuyến nghị)
  - `Content-Type: application/json; charset=utf-8` (Bắt buộc)
- **Tham số Đường dẫn (Path Parameters):**
  - `tenantId` (UUID, Bắt buộc)
  - `membershipId` (UUID, Bắt buộc)
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "reason": "Chấm dứt hợp đồng lao động"
}
```

- **Phản hồi Thành công (`200 OK`):**

```json
{
  "data": {
    "id": "0192b2d0-7f1b-7abc-8d2c-123456789012",
    "tenantId": "0192b2d0-7f1c-7abc-8d2c-123456789012",
    "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
    "userId": "0192b2d0-7f1a-7abc-8d2c-123456789012",
    "email": "cashier@coffeechain.com",
    "role": "KITCHEN",
    "status": "REVOKED",
    "invitationStatus": "ACCEPTED",
    "permissions": [],
    "version": 6,
    "createdAt": "2026-10-12T08:30:00Z",
    "updatedAt": "2026-10-12T15:30:00Z"
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

- **Mã lỗi khả dĩ:**
  - `403 Forbidden`, `404 Not Found`, `409 Conflict` (`last_owner_protected`), `422 Unprocessable Entity`.

---

#### `POST /api/v1/tenants/{tenantId}/memberships/{membershipId}/invitation-resends`
Gửi lại email mời kích hoạt tài khoản qua Cognito cho một membership đang ở trạng thái `INVITED`.

- **Thẩm quyền:** Quyền `employee:invite`.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `Idempotency-Key: <unique-key>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Khuyến nghị)
- **Tham số Đường dẫn (Path Parameters):**
  - `tenantId` (UUID, Bắt buộc)
  - `membershipId` (UUID, Bắt buộc)
- **Dữ liệu Gửi lên (Request Body):** Không có.
- **Phản hồi Thành công (`202 Accepted`):**

```json
{
  "data": {
    "deliveryId": "0192b2d0-7f50-7abc-8d2c-123456789012",
    "membershipId": "0192b2d0-7f1b-7abc-8d2c-123456789012",
    "status": "QUEUED",
    "attemptCount": 1,
    "nextAttemptAt": "2026-10-12T16:00:05Z",
    "lastErrorCode": null
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

- **Mã lỗi khả dĩ:**
  - `403 Forbidden`, `404 Not Found`, `409 Conflict` (bản ghi không còn ở trạng thái `INVITED`), `429 Too Many Requests` (vượt quá giới hạn tần suất gửi lại), `503 Service Unavailable`.

---

### 6. Thiết bị POS & Đổi Thu ngân Nhanh bằng Mã PIN (Giai đoạn P1)

Các endpoint trong phần này được bảo vệ sau cờ tính năng (Feature Flag) P1. Sự hiện diện hay vắng mặt của các endpoint này tuyệt đối không làm ảnh hưởng đến các API P0 cốt lõi.

#### `GET /api/v1/tenants/{tenantId}/branches/{branchId}/pos-devices`
Lấy danh sách các thiết bị POS đã đăng ký tại chi nhánh.

- **Thẩm quyền:** Quyền `pos-device:manage` (`OWNER` hoặc `MANAGER` của chi nhánh đó).
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Tùy chọn)
- **Phản hồi Thành công (`200 OK`):**

```json
{
  "data": [
    {
      "id": "0192b2d0-7f60-7abc-8d2c-123456789012",
      "tenantId": "0192b2d0-7f1c-7abc-8d2c-123456789012",
      "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
      "name": "Máy POS Thu ngân 01",
      "status": "ACTIVE",
      "registeredByUserId": "0192b2d0-7f1a-7abc-8d2c-123456789012",
      "lastSeenAt": "2026-10-12T16:20:00Z",
      "createdAt": "2026-10-12T08:00:00Z",
      "updatedAt": "2026-10-12T16:20:00Z"
    }
  ],
  "meta": {
    "pagination": {
      "page": 1,
      "limit": 20,
      "total": 1,
      "totalPages": 1
    },
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

---

#### `POST /api/v1/tenants/{tenantId}/branches/{branchId}/pos-devices`
Đăng ký một thiết bị POS mới gắn cố định với chi nhánh. Thiết bị gửi lên chứng thực `deviceProof` do client sinh ra; máy chủ chỉ lưu trữ bản băm mật mã `device_key_hash`.

- **Thẩm quyền:** Quyền `pos-device:manage`.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `Idempotency-Key: <unique-key>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Khuyến nghị)
  - `Content-Type: application/json; charset=utf-8` (Bắt buộc)
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "name": "Máy POS Thu ngân 02",
  "deviceProof": "client-generated-device-public-or-secret-proof"
}
```

*Quy cách kiểm tra:* `name` từ 1 đến 200 ký tự; `deviceProof` là chuỗi không rỗng từ 32 đến 512 ký tự.

- **Phản hồi Thành công (`201 Created`):**

```json
{
  "data": {
    "id": "0192b2d0-7f61-7abc-8d2c-123456789012",
    "tenantId": "0192b2d0-7f1c-7abc-8d2c-123456789012",
    "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
    "name": "Máy POS Thu ngân 02",
    "status": "ACTIVE",
    "registeredByUserId": "0192b2d0-7f1a-7abc-8d2c-123456789012",
    "lastSeenAt": null,
    "createdAt": "2026-10-12T16:30:00Z",
    "updatedAt": "2026-10-12T16:30:00Z"
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

*(Lưu ý: Không bao giờ trả lại chuỗi `deviceProof` trong phản hồi).*

- **Mã lỗi khả dĩ:**
  - `403 Forbidden`, `409 Conflict` (trùng chứng thực thiết bị), `422 Unprocessable Entity`, `429 Too Many Requests`.

---

#### `POST /api/v1/pos-devices/{deviceId}/revoke`
Thu hồi quyền hoạt động của một thiết bị POS (khi thiết bị hỏng, mất hoặc thay mới). Ngay lập tức hủy bỏ mọi phiên đổi nhanh đang chạy trên máy này.

- **Thẩm quyền:** Quyền `pos-device:manage`.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `Idempotency-Key: <unique-key>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Khuyến nghị)
  - `Content-Type: application/json; charset=utf-8` (Bắt buộc)
- **Tham số Đường dẫn (Path Parameters):**
  - `deviceId` (UUID, Bắt buộc)
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "reason": "Thiết bị hỏng màn hình, gửi đi bảo hành"
}
```

- **Phản hồi Thành công (`200 OK`):**

```json
{
  "data": {
    "id": "0192b2d0-7f61-7abc-8d2c-123456789012",
    "status": "REVOKED",
    "revokedAt": "2026-10-12T16:45:00Z"
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

- **Mã lỗi khả dĩ:**
  - `403 Forbidden`, `404 Not Found`, `409 Conflict`.

---

#### `PUT /api/v1/pos-devices/{deviceId}/cashier-pins/me`
Thu ngân tự thiết lập hoặc đổi mã PIN 6 chữ số của mình trên một thiết bị POS thuộc chi nhánh mình được phân công.

- **Thẩm quyền:** Quyền `pos-pin:manage-self` (Người dùng có membership `CASHIER` tại chi nhánh gắn với thiết bị POS).
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `Idempotency-Key: <unique-key>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Khuyến nghị)
  - `Content-Type: application/json; charset=utf-8` (Bắt buộc)
- **Tham số Đường dẫn (Path Parameters):**
  - `deviceId` (UUID, Bắt buộc)
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "pin": "482915",
  "pinConfirmation": "482915"
}
```

*Quy cách kiểm tra:*
- `pin`: Đúng 6 chữ số (`^\d{6}$`). Tuyệt đối không chấp nhận các chuỗi dễ đoán như `123456`, `000000`, `111111`.
- `pinConfirmation`: Bắt buộc trùng khớp chính xác với `pin`.
- Máy chủ băm mật mã bằng `Argon2id` hoặc `bcrypt`; nghiêm cấm ghi log hoặc lưu mã PIN thô.

- **Phản hồi Thành công (`204 No Content`):** Không có body.
- **Mã lỗi khả dĩ:**
  - `403 Forbidden` (`forbidden`): Người dùng không phải thu ngân của chi nhánh thiết bị.
  - `404 Not Found` (`not_found`): Thiết bị không tồn tại hoặc đã bị thu hồi.
  - `422 Unprocessable Entity` (`validation_error`): Mã PIN không đủ 6 chữ số hoặc hai trường không trùng nhau.
  - `429 Too Many Requests` (`rate_limited`): Giới hạn tần suất đổi mã PIN.

---

#### `POST /api/v1/pos-devices/{deviceId}/quick-switch`
Đổi nhanh người vận hành sang tài khoản Thu ngân trong một phiên POS đã đăng nhập Cognito hợp lệ.

- **Thẩm quyền:** Quyền `pos:quick-switch` (Phiên POS hiện tại hợp lệ và thuộc cùng chi nhánh với thiết bị).
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <base-session-jwt>` (Bắt buộc)
  - `X-Active-Branch-Id: <uuid>` (Bắt buộc)
  - `Idempotency-Key: <unique-key>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Khuyến nghị)
  - `Content-Type: application/json; charset=utf-8` (Bắt buộc)
- **Tham số Đường dẫn (Path Parameters):**
  - `deviceId` (UUID, Bắt buộc)
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "cashierMembershipId": "0192b2d0-7f1b-7abc-8d2c-123456789012",
  "pin": "482915",
  "deviceProof": "client-generated-device-public-or-secret-proof"
}
```

*Quy tắc bảo mật:*
- Mã PIN tuyệt đối không bao giờ được ghi log, lưu vào bảng idempotency hay trả về client.
- Nếu nhập sai quá 5 lần liên tiếp, tài khoản PIN của thu ngân trên máy này sẽ bị khóa tạm thời (`locked_until` 15 phút) và trả về lỗi `429 Too Many Requests`.

- **Phản hồi Thành công (`200 OK`):**

```json
{
  "data": {
    "sessionId": "0192b2d0-7f70-7abc-8d2c-123456789012",
    "contextToken": "ctx_01J...",
    "expiresAt": "2026-10-12T18:00:00Z",
    "tenantId": "0192b2d0-7f1c-7abc-8d2c-123456789012",
    "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
    "effectiveMembershipId": "0192b2d0-7f1b-7abc-8d2c-123456789012",
    "role": "CASHIER",
    "permissions": [
      "branch:read",
      "order:read",
      "order:create",
      "order:checkout"
    ]
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

- **Mã lỗi khả dĩ:**
  - `401 Unauthorized`: Phiên POS gốc hết hạn.
  - `403 Forbidden`: Mã PIN sai, sai chi nhánh hoặc tài khoản không phải Thu ngân active.
  - `404 Not Found`: Không tìm thấy thiết bị hoặc membership.
  - `429 Too Many Requests` (`rate_limited`): Tài khoản bị tạm khóa do nhập sai mã PIN quá số lần quy định.

---

#### `POST /api/v1/pos-devices/{deviceId}/quick-switch-sessions/{sessionId}/revoke`
Chủ động đóng và hủy bỏ phiên làm việc của Thu ngân đổi nhanh trước khi hết hạn token.

- **Thẩm quyền:** Quyền `pos:quick-switch` hoặc `pos-device:manage`.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Authorization: Bearer <token>` (Bắt buộc)
  - `Idempotency-Key: <unique-key>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Khuyến nghị)
- **Tham số Đường dẫn (Path Parameters):**
  - `deviceId` (UUID, Bắt buộc)
  - `sessionId` (UUID, Bắt buộc)
- **Phản hồi Thành công (`204 No Content`):** Không có body.
- **Mã lỗi khả dĩ:**
  - `401 Unauthorized`, `403 Forbidden`, `404 Not Found`.

---

## Hợp đồng API Nội bộ (Internal API Contracts)

Các API nội bộ sử dụng tiền tố `/internal/v1`, phục vụ giao tiếp trực tiếp giữa các microservice qua mạng riêng (Service Mesh / Private VPC). Bắt buộc phải có xác thực **Workload Identity / mTLS**. Client bên ngoài tuyệt đối không thể gọi trực tiếp.

### 1. Phân giải Quyền Hạn Thời gian Thực (Real-Time Authorization Decision)

#### `POST /internal/v1/authorization-decisions`
Endpoint phân giải thẩm quyền theo thời gian thực dành cho các downstream microservice (Order, Catalog, Payment, v.v.). Downstream service sau khi giải mã token JWT của user sẽ gọi sang Identity Access để kiểm tra xem user đó có quyền thực hiện hành động hay không.

- **Thẩm quyền:** Workload Identity / mTLS của service nội bộ đáng tin cậy.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `X-Correlation-Id: <uuid>` (Bắt buộc)
  - `Content-Type: application/json; charset=utf-8` (Bắt buộc)
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "subject": "cognito-sub-01J...",
  "tenantId": "0192b2d0-7f1c-7abc-8d2c-123456789012",
  "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
  "requiredPermission": "order:create",
  "posOperatorContext": null
}
```

*Quy cách kiểm tra:*
- `subject`: Chuỗi Cognito `sub` hoặc mã định danh user mờ.
- `tenantId`: Bắt buộc, chuỗi UUID.
- `branchId`: Chuỗi UUID (hoặc `null` nếu là thao tác cấp toàn chuỗi của Owner).
- `requiredPermission`: Chuỗi định danh quyền (ví dụ: `order:create`, `catalog:manage`).
- `posOperatorContext`: Token ngữ cảnh đổi nhanh thu ngân (nếu request bắt nguồn từ phiên đổi PIN P1).

- **Phản hồi Thành công (`200 OK` - Cho phép Truy cập `allowed: true`):**

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
      "permissions": [
        "branch:read",
        "order:read",
        "order:create",
        "order:checkout"
      ],
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

- **Phản hồi Thành công (`200 OK` - Từ chối Truy cập `allowed: false`):**
Khi người dùng không có quyền hạn yêu cầu, endpoint vẫn trả về mã HTTP `200 OK` với cờ `allowed: false` và mã lý do `denialCode` an toàn. Downstream service căn cứ vào kết quả này để trả về HTTP `403` cho client:

```json
{
  "data": {
    "allowed": false,
    "denialCode": "insufficient_permission",
    "message": "Tài khoản không sở hữu quyền order:create tại chi nhánh này"
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

- **Mã lỗi khả dĩ:**
  - `401 Unauthorized` / `403 Forbidden`: Chứng chỉ Workload Identity hoặc mTLS không hợp lệ.
  - `422 Unprocessable Entity`: Dữ liệu đầu vào sai định dạng.
  - `503 Service Unavailable` (`authorization_unavailable`): Identity DB gặp sự cố; downstream service bắt buộc áp dụng nguyên tắc **Fail-Closed** (từ chối truy cập an toàn).

---

### 2. Vô hiệu hóa Bộ nhớ Đệm Phân quyền (Cache Invalidation)

#### `POST /internal/v1/authorization-contexts/invalidate`
Endpoint tác vụ vận hành dành cho Event Consumer gọi nhằm xóa cache quyền của người dùng sau khi nhận sự kiện thay đổi phân quyền (`MembershipRoleChanged`, `MembershipRevoked`, v.v.).

- **Thẩm quyền:** Workload Identity của Event Consumer.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `X-Correlation-Id: <uuid>` (Bắt buộc)
  - `Content-Type: application/json; charset=utf-8` (Bắt buộc)
- **Dữ liệu Gửi lên (Request Body):**

```json
{
  "tenantId": "0192b2d0-7f1c-7abc-8d2c-123456789012",
  "userId": "0192b2d0-7f1e-7abc-8d2c-123456789012",
  "membershipVersion": 8,
  "eventId": "0192b2d0-8000-7abc-8d2c-123456789012"
}
```

- **Phản hồi Thành công (`202 Accepted`):**

```json
{
  "data": {
    "invalidated": true
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

- **Mã lỗi khả dĩ:**
  - `401 Unauthorized`, `403 Forbidden`, `422 Unprocessable Entity`.

---

### 3. Thử lại Gửi Lời mời Bị Lỗi (Invitation Delivery Retry)

#### `POST /internal/v1/invitation-deliveries/{deliveryId}/retry`
Worker tiến trình nền gọi để thử lại việc gửi email kích hoạt Cognito khi lần gửi trước gặp sự cố gián đoạn tạm thời.

- **Thẩm quyền:** Workload Identity của Background Worker.
- **Tiêu đề Yêu cầu (Request Headers):**
  - `Idempotency-Key: <unique-key>` (Bắt buộc)
  - `X-Correlation-Id: <uuid>` (Khuyến nghị)
- **Tham số Đường dẫn (Path Parameters):**
  - `deliveryId` (UUID, Bắt buộc)
- **Phản hồi Thành công (`202 Accepted`):**

```json
{
  "data": {
    "deliveryId": "0192b2d0-7f2a-7abc-8d2c-123456789012",
    "membershipId": "0192b2d0-7f1b-7abc-8d2c-123456789012",
    "status": "QUEUED",
    "attemptCount": 2,
    "nextAttemptAt": "2026-10-12T17:00:00Z",
    "lastErrorCode": null
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

- **Mã lỗi khả dĩ:**
  - `401 Unauthorized`, `403 Forbidden`, `404 Not Found`, `409 Conflict`, `429 Too Many Requests`, `503 Service Unavailable`.

---

## Cấu trúc Schema Dùng lại (Reusable Schemas)

### 1. `TenantResponse`

| Tên trường | Kiểu dữ liệu | Bắt buộc | Mô tả ý nghĩa |
|---|---|:---:|---|
| `id` | UUID string | Có | Mã định danh tenant chuẩn UUID v7. |
| `name` | string | Có | Tên chuỗi thương hiệu (1–200 ký tự). |
| `status` | enum | Có | `ACTIVE`, `SUSPENDED`, `ARCHIVED`. |
| `version` | integer | Có | Phiên bản khóa lạc quan (Optimistic Concurrency). |
| `createdAt` | ISO-8601 UTC | Có | Thời điểm tạo tenant. |
| `updatedAt` | ISO-8601 UTC | Có | Thời điểm cập nhật tenant gần nhất. |

### 2. `BranchResponse`

| Tên trường | Kiểu dữ liệu | Bắt buộc | Mô tả ý nghĩa |
|---|---|:---:|---|
| `id` | UUID string | Có | Mã định danh chi nhánh UUID v7. |
| `tenantId` | UUID string | Có | Chi nhánh thuộc tenant nào. |
| `code` | string | Có | Mã chi nhánh (bất biến, duy nhất trong tenant). |
| `name` | string | Có | Tên hiển thị chi nhánh. |
| `address` | string \| null | Không | Địa chỉ thực tế (tối đa 500 ký tự). |
| `status` | enum | Có | `ACTIVE`, `ARCHIVED`. |
| `version` | integer | Có | Phiên bản dùng cho kiểm tra `If-Match`. |
| `createdAt` | ISO-8601 UTC | Có | Thời điểm tạo chi nhánh. |
| `updatedAt` | ISO-8601 UTC | Có | Thời điểm sửa đổi chi nhánh. |

### 3. `EmployeeResponse`

| Tên trường | Kiểu dữ liệu | Bắt buộc | Mô tả ý nghĩa |
|---|---|:---:|---|
| `userId` | UUID string | Có | Mã định danh người dùng nội bộ. |
| `email` | string | Có | Email đăng nhập (đã chuẩn hóa; che giấu khi chính sách yêu cầu). |
| `displayName` | string | Có | Họ và tên hiển thị của nhân viên. |
| `phone` | string \| null | Không | Số điện thoại liên hệ (dữ liệu PII bảo vệ). |
| `status` | enum | Có | `ACTIVE`, `SUSPENDED`, `REVOKED`, `INVITED`. |
| `memberships` | array | Có | Danh sách tóm tắt các membership nhìn thấy được. |
| `createdAt` | ISO-8601 UTC | Có | Thời điểm tạo hồ sơ. |
| `updatedAt` | ISO-8601 UTC | Có | Thời điểm cập nhật hồ sơ gần nhất. |

### 4. `MembershipResponse`

| Tên trường | Kiểu dữ liệu | Bắt buộc | Mô tả ý nghĩa |
|---|---|:---:|---|
| `id` | UUID string | Có | Mã định danh bản ghi membership. |
| `tenantId` | UUID string | Có | Ngữ cảnh tenant. |
| `branchId` | UUID \| null | Có điều kiện | Null duy nhất đối với vai trò `OWNER`; bắt buộc có với các vai trò khác. |
| `userId` | UUID string | Có | Mã định danh người dùng tiếp nhận vai trò. |
| `email` | string | Có | Email nhân viên. |
| `role` | enum | Có | `OWNER`, `MANAGER`, `CASHIER`, `KITCHEN`. |
| `status` | enum | Có | `INVITED`, `ACTIVE`, `SUSPENDED`, `REVOKED`. |
| `invitationStatus` | enum \| null| Không | `QUEUED`, `SENT`, `FAILED`, `ACCEPTED`. |
| `permissions` | array[string] | Có | Danh mục các quyền hệ thống phân giải cho vai trò này. |
| `version` | integer | Có | Phiên bản dùng cho kiểm tra `If-Match`. |
| `createdAt` | ISO-8601 UTC | Có | Thời điểm tạo bản ghi. |
| `updatedAt` | ISO-8601 UTC | Có | Thời điểm cập nhật gần nhất. |

### 5. `InvitationDeliveryResponse`

| Tên trường | Kiểu dữ liệu | Bắt buộc | Mô tả ý nghĩa |
|---|---|:---:|---|
| `deliveryId` | UUID string | Có | Mã lần thử gửi lời mời. |
| `membershipId` | UUID string | Có | Bản ghi membership mục tiêu. |
| `status` | enum | Có | `QUEUED`, `SENT`, `FAILED`, `ACCEPTED`. |
| `attemptCount` | integer | Có | Số lần đã gọi sang nhà cung cấp Cognito. |
| `nextAttemptAt` | ISO-8601 UTC \| null | Không | Thời điểm dự kiến thử lại tiếp theo. |
| `lastErrorCode` | string \| null | Không | Mã lỗi an toàn (không chứa chi tiết bí mật). |

### 6. `PosDeviceResponse` (P1)

| Tên trường | Kiểu dữ liệu | Bắt buộc | Mô tả ý nghĩa |
|---|---|:---:|---|
| `id` | UUID string | Có | Mã định danh thiết bị POS đã đăng ký. |
| `tenantId` | UUID string | Có | Thuộc chuỗi nào. |
| `branchId` | UUID string | Có | Gắn cố định với chi nhánh nào. |
| `name` | string | Có | Tên gọi thiết bị do người dùng đặt. |
| `status` | enum | Có | `ACTIVE`, `REVOKED`. |
| `registeredByUserId` | UUID string | Có | Người thực hiện đăng ký thiết bị. |
| `lastSeenAt` | ISO-8601 UTC \| null | Không | Thời điểm cuối cùng thiết bị có hoạt động. |
| `createdAt` | ISO-8601 UTC | Có | Thời điểm đăng ký thiết bị. |
| `updatedAt` | ISO-8601 UTC | Có | Thời điểm cập nhật trạng thái gần nhất. |

### 7. `PosOperatorContextResponse` (P1)

| Tên trường | Kiểu dữ liệu | Bắt buộc | Mô tả ý nghĩa |
|---|---|:---:|---|
| `sessionId` | UUID string | Có | Mã phiên đổi nhanh phía máy chủ. |
| `contextToken` | string | Có | Token ngữ cảnh tạm thời (client bảo vệ như secret). |
| `expiresAt` | ISO-8601 UTC | Có | Thời hạn hết hiệu lực của ngữ cảnh (ngắn hạn). |
| `tenantId` / `branchId` | UUID string | Có | Phạm vi chi nhánh gắn với thiết bị. |
| `effectiveMembershipId` | UUID string | Có | Membership của thu ngân vừa đổi. |
| `role` | enum | Có | Luôn luôn là `CASHIER` trong tính năng đổi nhanh. |
| `permissions` | array[string] | Có | Danh sách quyền thu hẹp dành cho thu ngân. |

---

## Cấu trúc Bảng Cơ sở Dữ liệu (Database Schema)

### Quy ước Thiết kế Database PostgreSQL:
- Hệ quản trị CSDL: PostgreSQL 16+ / Aurora PostgreSQL.
- Định danh (ID): Kiểu `uuid` lưu trữ UUID v7 do tầng ứng dụng tự sinh.
- Chuỗi ký tự: Kiểu `text`, kiểm soát độ dài qua ràng buộc `CHECK` thay vì giới hạn cứng `varchar(n)`.
- Thời gian: Kiểu `timestamptz` chuẩn UTC.
- Cấu trúc mở rộng: Kiểu `jsonb` chỉ dùng cho metadata nhà cung cấp giới hạn hoặc bản ghi outbox/idempotency đã lọc sạch thông tin nhạy cảm.
- Tất cả các cột đều có chú thích tường minh (`COMMENT ON COLUMN`).
- Tên bảng dạng số nhiều (`tenants`, `branches`, `memberships`), tiền tố ràng buộc: `fk_`, `uq_`, `chk_`, `idx_`.

---

### 1. Bảng `tenants` (Chuỗi thương hiệu)

| Cột | Kiểu | Null | Ràng buộc | Chú thích cột |
|---|---|:---:|---|---|
| `id` | uuid | Không | PK | Mã định danh tenant ổn định UUID v7. |
| `name` | text | Không | `CHECK length(name) BETWEEN 1 AND 200` | Tên chuỗi thương hiệu hiển thị. |
| `status` | text | Không | `CHECK status IN ('ACTIVE', 'SUSPENDED', 'ARCHIVED')` | Trạng thái vòng đời tenant. |
| `version` | bigint | Không | Mặc định 1, `CHECK version > 0` | Phiên bản khóa lạc quan (Optimistic Concurrency). |
| `created_at` | timestamptz | Không | Mặc định `now()` | Thời điểm tạo tenant theo chuẩn UTC. |
| `updated_at` | timestamptz | Không | Mặc định `now()` | Thời điểm cập nhật tenant gần nhất. |
| `created_by` | text | Không | Chuỗi mờ | Tác nhân thực hiện khởi tạo tenant. |
| `updated_by` | text | Không | Chuỗi mờ | Tác nhân cập nhật thông tin tenant gần nhất. |

---

### 2. Bảng `branches` (Chi nhánh)

| Cột | Kiểu | Null | Ràng buộc | Chú thích cột |
|---|---|:---:|---|---|
| `id` | uuid | Không | PK | Mã định danh chi nhánh UUID v7. |
| `tenant_id` | uuid | Không | FK `tenants(id)` | Khóa ngoại xác định tenant sở hữu. |
| `code` | text | Không | Bất biến sau tạo | Mã chi nhánh do người dùng nhập. |
| `normalized_code` | text | Không | Duy nhất theo tenant | Mã chi nhánh chuẩn hóa chữ thường phục vụ index `uq_branches_tenant_code`. |
| `name` | text | Không | `CHECK length(name) BETWEEN 1 AND 200` | Tên hiển thị của chi nhánh. |
| `address` | text | Có | Tối đa 500 ký tự | Địa chỉ thực tế của chi nhánh. |
| `status` | text | Không | `CHECK status IN ('ACTIVE', 'ARCHIVED')` | Trạng thái hoạt động của chi nhánh. |
| `version` | bigint | Không | Mặc định 1 | Phiên bản dùng cho kiểm tra `If-Match`. |
| `created_at` | timestamptz | Không | Mặc định `now()` | Thời điểm tạo chi nhánh. |
| `updated_at` | timestamptz | Không | Mặc định `now()` | Thời điểm cập nhật chi nhánh. |
| `created_by` | text | Không | Chuỗi mờ | Tác nhân tạo chi nhánh. |
| `updated_by` | text | Không | Chuỗi mờ | Tác nhân cập nhật gần nhất. |

---

### 3. Bảng `users` (Hồ sơ người dùng)

| Cột | Kiểu | Null | Ràng buộc | Chú thích cột |
|---|---|:---:|---|---|
| `id` | uuid | Không | PK | Mã định danh người dùng nội bộ. |
| `cognito_subject` | text | Có | Duy nhất khi khác null | Mã định danh ổn định `sub` từ Cognito; null khi chưa kích hoạt. |
| `normalized_email` | text | Không | Duy nhất toàn hệ thống | Email chuẩn hóa chữ thường phục vụ tìm kiếm và gửi lời mời. |
| `display_name` | text | Không | `CHECK length(display_name) BETWEEN 1 AND 200` | Họ và tên hiển thị của người dùng. |
| `phone` | text | Có | E.164 format | Số điện thoại liên hệ (dữ liệu PII bảo vệ). |
| `status` | text | Không | `CHECK status IN ('ACTIVE', 'SUSPENDED', 'REVOKED')` | Trạng thái người dùng cấp hồ sơ. |
| `reconciliation_status` | text | Không | `CHECK status IN ('PENDING', 'ALIGNED', 'DRIFTED')` | Trạng thái đồng bộ với Cognito. |
| `last_authenticated_at` | timestamptz | Có | — | Mốc thời gian request thành công gần nhất. |
| `created_at` | timestamptz | Không | Mặc định `now()` | Thời điểm tạo hồ sơ. |
| `updated_at` | timestamptz | Không | Mặc định `now()` | Thời điểm cập nhật hồ sơ gần nhất. |

---

### 4. Bảng `memberships` (Tư cách thành viên)

| Cột | Kiểu | Null | Ràng buộc | Chú thích cột |
|---|---|:---:|---|---|
| `id` | uuid | Không | PK | Mã định danh bản ghi thành viên UUID v7. |
| `tenant_id` | uuid | Không | FK `tenants(id)` | Ngữ cảnh tenant của thành viên. |
| `user_id` | uuid | Không | FK `users(id)` | Người dùng tiếp nhận vai trò. |
| `branch_id` | uuid | Có | Ràng buộc logic vai trò | Bắt buộc `NULL` nếu là `OWNER`; bắt buộc `NOT NULL` nếu là vai trò khác. |
| `role` | text | Không | `CHECK role IN ('OWNER', 'MANAGER', 'CASHIER', 'KITCHEN')` | Vai trò hiệu lực của thành viên. |
| `status` | text | Không | `CHECK status IN ('INVITED', 'ACTIVE', 'SUSPENDED', 'REVOKED')` | Trạng thái vòng đời thành viên. |
| `status_reason` | text | Có | Tối đa 500 ký tự | Lý do thay đổi trạng thái (đình chỉ / thu hồi). |
| `version` | bigint | Không | Mặc định 1 | Phiên bản dùng cho kiểm tra `If-Match`. |
| `invited_at` | timestamptz | Không | Mặc định `now()` | Thời điểm gửi lời mời thành viên. |
| `activated_at` | timestamptz | Có | — | Thời điểm thành viên kích hoạt tài khoản thành công. |
| `suspended_at` | timestamptz | Có | — | Thời điểm bị đình chỉ gần nhất. |
| `revoked_at` | timestamptz | Có | — | Thời điểm thu hồi quyền vĩnh viễn. |
| `created_at` | timestamptz | Không | Mặc định `now()` | Thời điểm tạo bản ghi. |
| `updated_at` | timestamptz | Không | Mặc định `now()` | Thời điểm cập nhật bản ghi. |
| `created_by` | text | Không | Chuỗi mờ | Tác nhân tạo membership. |
| `updated_by` | text | Không | Chuỗi mờ | Tác nhân cập nhật membership. |

*Các ràng buộc toàn vẹn bắt buộc:*
- Khi `role = 'OWNER'` thì bắt buộc `branch_id IS NULL`.
- Khi `role IN ('MANAGER', 'CASHIER', 'KITCHEN')` thì bắt buộc `branch_id IS NOT NULL`.
- Mỗi người dùng chỉ có tối đa một membership hoạt động cấp toàn chuỗi (`branch_id IS NULL`) trong cùng một tenant.
- Mỗi người dùng chỉ có tối đa một membership hoạt động tại một chi nhánh cụ thể (`branch_id IS NOT NULL`).
- Trạng thái `REVOKED` là trạng thái kết thúc; mời lại nhân viên sẽ tạo bản ghi mới hoàn toàn.

---

### 5. Bảng `invitation_deliveries` (Tiến trình gửi lời mời)

| Cột | Kiểu | Null | Ràng buộc | Chú thích cột |
|---|---|:---:|---|---|
| `id` | uuid | Không | PK | Mã lần gửi lời mời UUID v7. |
| `membership_id` | uuid | Không | FK `memberships(id)` | Membership mục tiêu nhận lời mời. |
| `provider` | text | Không | `COGNITO` | Nhà cung cấp dịch vụ gửi lời mời. |
| `status` | text | Không | `CHECK status IN ('QUEUED', 'SENT', 'FAILED', 'ACCEPTED')` | Trạng thái gửi lời mời. |
| `attempt_count` | integer | Không | Mặc định 0 | Số lần đã gọi API gửi lời mời. |
| `next_attempt_at` | timestamptz | Có | — | Thời điểm dự kiến thử lại tiếp theo. |
| `last_error_code` | text | Có | Mã lỗi an toàn | Mã lỗi kỹ thuật khi gửi thất bại. |
| `provider_user_reference` | text | Có | Chuỗi mờ | Mã tham chiếu bên phía Cognito nếu có. |
| `created_at` | timestamptz | Không | Mặc định `now()` | Thời điểm tạo tiến trình gửi. |
| `updated_at` | timestamptz | Không | Mặc định `now()` | Thời điểm cập nhật trạng thái gửi. |

---

### 6. Bảng `idempotency_records` (Kiểm soát tính lũy kế)

| Cột | Kiểu | Null | Ràng buộc | Chú thích cột |
|---|---|:---:|---|---|
| `id` | uuid | Không | PK | Mã bản ghi idempotency. |
| `tenant_id` | uuid | Có | FK khi có tenant | Ngữ cảnh tenant (null khi gọi bootstrap platform). |
| `actor_subject` | text | Không | Chuỗi mờ | Định danh người gọi thực hiện thao tác. |
| `idempotency_key` | text | Không | Tối đa 200 ký tự | Khóa lũy kế do client sinh ra. |
| `request_hash` | text | Không | SHA-256 digest | Mã băm toàn bộ payload request để phát hiện xung đột trùng khóa nhưng khác body. |
| `response_status` | integer | Không | HTTP status code | Mã trạng thái HTTP gốc đã trả về. |
| `response_metadata` | jsonb | Không | Đã che giấu dữ liệu | Dữ liệu phản hồi an toàn (đã lọc bỏ mã PIN/secret) để replay. |
| `expires_at` | timestamptz | Không | Theo retention policy | Thời điểm hết hạn để xóa dọn dẹp định kỳ. |
| `created_at` | timestamptz | Không | Mặc định `now()` | Thời điểm lưu bản ghi. |

---

### 7. Bảng `outbox_events` (Sự kiện Transactional Outbox)

| Cột | Kiểu | Null | Ràng buộc | Chú thích cột |
|---|---|:---:|---|---|
| `id` | uuid | Không | PK | Mã sự kiện dùng khử trùng lặp (dedup) ở phía nhận. |
| `tenant_id` | uuid | Có | FK khi có tenant | Ngữ cảnh tenant của sự kiện. |
| `branch_id` | uuid | Có | Chi nhánh | Ngữ cảnh chi nhánh nếu là sự kiện cấp chi nhánh. |
| `event_type` | text | Không | Allowlist | Tên loại sự kiện nghiệp vụ (ví dụ: `MembershipRoleChanged.v1`). |
| `event_version` | integer | Không | Dương | Phiên bản cấu trúc sự kiện. |
| `aggregate_type` | text | Không | Allowlist | Đối tượng phát sinh sự kiện (`tenant`, `branch`, `membership`). |
| `aggregate_id` | uuid | Không | — | Mã định danh đối tượng phát sinh sự kiện. |
| `payload` | jsonb | Không | Đã che giấu dữ liệu | Dữ liệu sự kiện (loại bỏ token, secret, mã PIN thô). |
| `correlation_id` | text | Không | — | Mã liên kết luồng xử lý phục vụ trace. |
| `causation_id` | text | Có | — | Mã lệnh hoặc sự kiện nguồn gây ra sự kiện này. |
| `publish_status` | text | Không | `CHECK status IN ('PENDING', 'PUBLISHED', 'FAILED')` | Trạng thái gửi lên Message Broker. |
| `attempt_count` | integer | Không | Mặc định 0 | Số lần đã thử publish lên Message Broker. |
| `next_attempt_at` | timestamptz | Có | — | Mốc thời gian thử lại kế tiếp. |
| `published_at` | timestamptz | Có | — | Thời điểm broker xác nhận nhận thành công. |
| `created_at` | timestamptz | Không | Mặc định `now()` | Thời điểm sinh sự kiện. |

---

### 8. Bảng `pos_devices` (Thiết bị POS - Giai đoạn P1)

| Cột | Kiểu | Null | Ràng buộc | Chú thích cột |
|---|---|:---:|---|---|
| `id` | uuid | Không | PK | Mã thiết bị POS UUID v7. |
| `tenant_id` | uuid | Không | FK `tenants(id)` | Thuộc chuỗi nào. |
| `branch_id` | uuid | Không | FK `branches(id)` | Gắn cố định với chi nhánh nào. |
| `name` | text | Không | `CHECK length(name) BETWEEN 1 AND 200` | Tên hiển thị thiết bị. |
| `device_key_hash` | text | Không | Duy nhất toàn hệ thống | Bản băm mật mã của chứng thực thiết bị client. |
| `status` | text | Không | `CHECK status IN ('ACTIVE', 'REVOKED')` | Trạng thái hoạt động của thiết bị. |
| `registered_by_user_id`| uuid | Không | FK `users(id)` | Người thực hiện kích hoạt thiết bị. |
| `last_seen_at` | timestamptz | Có | — | Thời điểm thiết bị hoạt động gần nhất. |
| `revoked_at` | timestamptz | Có | — | Thời điểm thu hồi thiết bị. |
| `created_at` | timestamptz | Không | Mặc định `now()` | Thời điểm đăng ký thiết bị. |
| `updated_at` | timestamptz | Không | Mặc định `now()` | Thời điểm cập nhật trạng thái. |

---

### 9. Bảng `pos_pins` (Mã PIN Thu ngân - Giai đoạn P1)

| Cột | Kiểu | Null | Ràng buộc | Chú thích cột |
|---|---|:---:|---|---|
| `id` | uuid | Không | PK | Mã bản ghi credential PIN. |
| `membership_id` | uuid | Không | FK `memberships(id)` | Thu ngân sở hữu mã PIN. |
| `device_id` | uuid | Không | FK `pos_devices(id)` | Thiết bị POS mà mã PIN có hiệu lực. |
| `pin_hash` | text | Không | Argon2id / bcrypt | Bản băm một chiều của mã PIN 6 số. |
| `status` | text | Không | `CHECK status IN ('ACTIVE', 'REVOKED')` | Trạng thái hoạt động của mã PIN. |
| `failed_attempts` | integer | Không | Mặc định 0 | Số lần nhập sai liên tiếp gần nhất. |
| `locked_until` | timestamptz | Có | — | Thời điểm mở khóa sau khi bị tạm khóa vì nhập sai. |
| `rotated_at` | timestamptz | Không | Mặc định `now()` | Thời điểm đổi mã PIN gần nhất. |
| `created_at` | timestamptz | Không | Mặc định `now()` | Thời điểm tạo credential. |
| `updated_at` | timestamptz | Không | Mặc định `now()` | Thời điểm cập nhật credential. |

---

### 10. Bảng `pos_switch_sessions` (Phiên Đổi nhanh Thu ngân - Giai đoạn P1)

| Cột | Kiểu | Null | Ràng buộc | Chú thích cột |
|---|---|:---:|---|---|
| `id` | uuid | Không | PK | Mã phiên đổi nhanh UUID v7. |
| `tenant_id` | uuid | Không | FK `tenants(id)` | Ngữ cảnh tenant. |
| `branch_id` | uuid | Không | FK `branches(id)` | Ngữ cảnh chi nhánh. |
| `device_id` | uuid | Không | FK `pos_devices(id)` | Thiết bị POS nơi phiên đang chạy. |
| `base_user_id` | uuid | Không | FK `users(id)` | Người dùng đăng nhập phiên POS gốc bằng Cognito. |
| `effective_membership_id`| uuid | Không | FK `memberships(id)`| Thu ngân tiếp quản quyền thao tác qua PIN. |
| `membership_version` | bigint | Không | Dương | Phiên bản membership tại thời điểm xác thực PIN. |
| `context_token_hash` | text | Không | Duy nhất | Bản băm của token ngữ cảnh tạm thời. |
| `expires_at` | timestamptz | Không | TTL ngắn hạn | Thời điểm hết hạn của phiên đổi nhanh. |
| `revoked_at` | timestamptz | Có | — | Thời điểm chủ động đóng hoặc thu hồi phiên. |
| `created_at` | timestamptz | Không | Mặc định `now()` | Thời điểm sinh phiên đổi nhanh. |

---

## Danh mục Chỉ mục Cơ sở Dữ liệu (Database Indexes)

| Tên bảng | Tên chỉ mục / ràng buộc | Các cột (Columns) | Loại chỉ mục | Mục đích tối ưu hóa |
|---|---|---|---|---|
| `branches` | `uq_branches_tenant_code` | `(tenant_id, normalized_code)` | Unique B-tree | Đảm bảo tính duy nhất của mã chi nhánh trong cùng tenant. |
| `branches` | `idx_branches_tenant_status` | `(tenant_id, status, id)` | B-tree | Tối ưu hóa truy vấn danh sách chi nhánh và lọc theo trạng thái. |
| `users` | `uq_users_cognito_subject` | `(cognito_subject)` WHERE NOT NULL | Unique Partial B-tree| Ánh xạ nhanh và duy nhất giữa mã Cognito `sub` với user. |
| `users` | `uq_users_normalized_email`| `(normalized_email)` | Unique B-tree | Tìm kiếm người dùng khi gửi lời mời qua email. |
| `memberships` | `uq_memberships_active_tenant_user` | `(tenant_id, user_id)` WHERE branch_id IS NULL AND status IN ('INVITED','ACTIVE','SUSPENDED') | Unique Partial B-tree | Mỗi người dùng chỉ có tối đa một membership cấp chuỗi (Owner) đang hoạt động. |
| `memberships` | `uq_memberships_active_branch_user` | `(tenant_id, user_id, branch_id)` WHERE branch_id IS NOT NULL AND status IN ('INVITED','ACTIVE','SUSPENDED') | Unique Partial B-tree | Mỗi người dùng chỉ có tối đa một membership hiệu lực tại mỗi chi nhánh. |
| `memberships` | `idx_memberships_subject_status` | `(user_id, status, tenant_id, branch_id)` | B-tree | Tối ưu hóa API `GET /api/v1/me` và phân giải quyền. |
| `memberships` | `idx_memberships_scope_status` | `(tenant_id, branch_id, status, role, user_id)` | B-tree | Tối ưu hóa danh sách nhân sự của Owner/Manager và kiểm tra phạm vi. |
| `invitation_deliveries` | `idx_invitation_retry` | `(status, next_attempt_at)` | B-tree | Tối ưu hóa tiến trình Worker thăm dò lời mời cần gửi lại. |
| `idempotency_records` | `uq_idempotency_actor_key` | `(tenant_id, actor_subject, idempotency_key)` | Unique B-tree | Đảm bảo tính duy nhất và phát hiện retry lệnh chính xác. |
| `outbox_events` | `idx_outbox_publish_queue` | `(publish_status, next_attempt_at, created_at)` | B-tree | Tối ưu hóa tiến trình Outbox Publisher đẩy sự kiện lên broker. |
| `outbox_events` | `idx_outbox_aggregate` | `(aggregate_type, aggregate_id, created_at)` | B-tree | Truy vết và phát lại lịch sử sự kiện theo đối tượng. |
| `pos_devices` | `uq_pos_devices_key_hash` | `(device_key_hash)` | Unique B-tree | Ngăn chặn việc đăng ký trùng lặp chứng thực thiết bị. |
| `pos_devices` | `idx_pos_devices_scope` | `(tenant_id, branch_id, status)` | B-tree | Tối ưu hóa truy vấn thiết bị POS theo chi nhánh. |
| `pos_pins` | `uq_pos_pins_device_membership` | `(device_id, membership_id)` WHERE status = 'ACTIVE' | Unique Partial B-tree | Mỗi thu ngân chỉ có 1 mã PIN hoạt động trên mỗi thiết bị POS. |
| `pos_switch_sessions` | `uq_pos_switch_context_hash` | `(context_token_hash)` | Unique B-tree | Tra cứu nhanh phiên đổi nhanh bằng bản băm của token. |
| `pos_switch_sessions` | `idx_pos_switch_expiry` | `(expires_at, revoked_at)` | B-tree | Dọn dẹp các phiên đổi nhanh đã hết hạn. |

---

## Kế hoạch Thực thi Cơ sở Dữ liệu với Flyway

Thứ tự thực thi các script migration cho module Identity & Access:

| Thứ tự | Tên File Script Migration | Nội dung chính | Lưu ý Rollback / Phục hồi |
|:---:|---|---|---|
| 1 | `V1__create_identity_extensions.sql` | Cấu hình extension UUID và các hàm tiện ích. | Forward-only; chỉ xóa khi reset môi trường local. |
| 2 | `V2__create_tenants_branches_users.sql` | Tạo bảng `tenants`, `branches`, `users`, các khóa ngoại, check constraint và comment mô tả cột. | Khôi phục từ bản backup; không thực hiện DROP phá hủy. |
| 3 | `V3__create_memberships.sql` | Tạo bảng `memberships`, các ràng buộc vai trò/chi nhánh, partial unique index. | Điền dữ liệu trước khi bật ràng buộc trong môi trường có sẵn dữ liệu. |
| 4 | `V4__create_invitation_deliveries.sql` | Tạo bảng theo dõi tiến trình gửi email kích hoạt qua Cognito. | Bảo toàn lịch sử thất bại; sửa trạng thái qua tác vụ vận hành. |
| 5 | `V5__create_idempotency_outbox.sql` | Tạo bảng lưu trữ `idempotency_records` và `outbox_events`. | Giữ lại các sự kiện để replay; không xóa bản ghi chưa publish. |
| 6 | `V6__create_p1_pos_device_pin.sql` | Tạo các bảng thiết bị POS và mã PIN thu ngân (bảo vệ sau cờ tính năng P1). | Triển khai schema trước khi bật feature flag; tắt cờ trước khi rollback. |
| 7 | `V7__seed_local_demo_identity.sql` | Nạp dữ liệu mẫu ban đầu cho môi trường local demo (chỉ chứa dữ liệu giả lập, không chứa thông tin nhạy cảm). | Chỉ chạy trên môi trường local demo; tuyệt đối không chạy trên staging/production. |

---

## Hợp đồng Sự kiện Tích hợp (Integration Event Contracts)

### Cấu trúc Bao bì Sự kiện Chuẩn (Event Envelope)

```json
{
  "eventId": "0192b2d0-8000-7abc-8d2c-123456789012",
  "eventType": "MembershipRoleChanged.v1",
  "eventVersion": 1,
  "occurredAt": "2026-10-12T08:30:00Z",
  "tenantId": "0192b2d0-7f1c-7abc-8d2c-123456789012",
  "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
  "aggregateType": "membership",
  "aggregateId": "0192b2d0-7f1b-7abc-8d2c-123456789012",
  "correlationId": "cor_01J...",
  "causationId": "cmd_01J...",
  "producer": "identity-access",
  "payload": {}
}
```

### Danh mục Sự kiện Domain (Event Registry)

| Tên sự kiện | Tác nhân phát | Bên tiêu thụ (Consumers) | Yêu cầu dữ liệu trong Payload |
|---|---|---|---|
| `TenantCreated.v1` | Identity Access | Audit, Reporting | `tenantId`, tên, trạng thái, người tạo, thời điểm tạo. |
| `BranchCreated.v1` | Identity Access | Audit, Catalog, Order | `tenantId`, `branchId`, mã code, tên, trạng thái. |
| `BranchUpdated.v1` | Identity Access | Audit, Catalog, Order | Các trường thay đổi, phiên bản mới, người sửa. |
| `BranchArchived.v1` | Identity Access | Audit, Order, Shift | `tenantId`, `branchId`, lý do lưu trữ, người thực hiện. |
| `MembershipInvited.v1` | Identity Access | Audit, Notification Worker | `membershipId`, email, vai trò, chi nhánh, `deliveryId` (không kèm token). |
| `MembershipActivated.v1`| Identity Access | Audit, Downstream Auth | `membershipId`, `userId`, `tenantId`, `branchId`, vai trò, phiên bản. |
| `MembershipRoleChanged.v1`| Identity Access | Audit, Cache Invalidator | Vai trò cũ, vai trò mới, phiên bản mới, người đổi. |
| `MembershipScopeChanged.v1`| Identity Access| Audit, Cache Invalidator | Chi nhánh cũ, chi nhánh mới, vai trò, phiên bản mới. |
| `MembershipSuspended.v1` | Identity Access | Audit, Cache Invalidator | `membershipId`, lý do đình chỉ, người thực hiện, phiên bản mới. |
| `MembershipRevoked.v1` | Identity Access | Audit, Cache Invalidator | `membershipId`, lý do thu hồi, người thực hiện, phiên bản mới. |
| `AuthorizationContextChanged.v1` | Identity Access | Downstream Cache, Observability | `userId`, `membershipId`, phiên bản, lý do (không kèm token). |
| `PosDeviceRegistered.v1` | Identity Access | Audit | `deviceId`, `tenantId`, `branchId`, tên máy (không kèm device proof). |
| `PosDeviceRevoked.v1` | Identity Access | Audit, POS Session Manager | `deviceId`, lý do thu hồi, người thực hiện. |
| `PosPinChanged.v1` | Identity Access | Audit | `membershipId`, `deviceId`, thời điểm (tuyệt đối không kèm PIN hay hash). |
| `PosQuickSwitchSucceeded.v1` | Identity Access | Audit, Observability | `deviceId`, `branchId`, `effectiveMembershipId`, hạn dùng (không kèm PIN). |
| `PosQuickSwitchDenied.v1` | Identity Access | Audit, Loss Prevention | `deviceId`, `branchId`, lý do từ chối (không kèm PIN). |

### Định tuyến Message Broker

| Thành phần | Quy ước tích hợp |
|---|---|
| Event Bus | AWS EventBridge Bus `pos.identity.v1` (môi trường cloud); adapter local giữ nguyên envelope. |
| Queue Kiểm toán (Audit) | `identity-to-audit.fifo` khi cần bảo đảm thứ tự tuần tự tuyệt đối, hoặc standard queue kèm dedup. |
| Queue Vô hiệu hóa Cache | `identity-authorization-invalidation` standard SQS queue. |
| Chính sách Thử lại (Retry)| Exponential backoff có giới hạn số lần; Visibility timeout lớn hơn thời gian xử lý tối đa của consumer. |
| Dead Letter Queue (DLQ) | Mỗi consumer queue sở hữu một DLQ riêng; message chuyển sang DLQ giữ nguyên eventId, correlationId và mã lỗi. |
| Tính lũy kế phía Consumer | Consumer bắt buộc lưu vết `eventId` đã xử lý trước khi xác nhận tác vụ phụ (idempotent consumer pattern). |

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

---

## Yêu cầu Kiểm thử Hợp đồng (Contract Test Requirements)

- **Kiểm thử OpenAPI:** Tự động đối soát toàn bộ request, response body và error envelope với định nghĩa OpenAPI 3.1.
- **Kiểm thử Schema Sự kiện:** Kiểm tra tính tương thích ngược và cấu trúc schema của các outbox event bằng JSON Schema / AsyncAPI.
- **Kiểm thử Mã Lỗi:** Đảm bảo bao phủ 100% các ca lỗi đặc thù: `401`, `403`, `404`, `409` (`conflict`, `duplicate_request`, `last_owner_protected`, `invitation_pending`), `422`, `429`, `503`.
- **Kiểm thử Tích hợp Repository:** Xác minh ràng buộc toàn vẹn tenant/branch composite, partial unique constraint, quy tắc bất biến Last-Owner và xung đột khóa lạc quan (`If-Match`).
- **Kiểm thử An ninh & IDOR:** Thử nghiệm gọi chéo tenant ID, giả mạo branch header, hành vi leo thang đặc quyền của Manager, sử dụng token đã thu hồi, và kiểm tra rò rỉ secret/mã PIN thô trên log/response.
- **Kiểm thử Tính lũy kế (Idempotency):** Gửi lại request với cùng `Idempotency-Key` (cùng hash trả về kết quả cũ; khác hash trả về `409 duplicate_request`).
- **Kiểm thử Adapter Cognito:** Mô phỏng các ca gửi lời mời thành công, nhà cung cấp gián đoạn mạng, người dùng trùng lặp, lời mời đã chấp nhận và lệch trạng thái đồng bộ (reconciliation drift).
- **Kiểm thử Đổi nhanh PIN P1:** Kiểm tra gắn kết thiết bị, mã PIN sai, cơ chế khóa tạm thời rate limiting, thiết bị bị thu hồi và thu hẹp quyền hạn cho thu ngân.
