Started at: 2026/10/13 00:00:00
Finished at: 2026/10/13 00:00:00
Total time: 0 minutes
---

# Module 7 — Ca làm việc: API & Data Contracts

**Ngày:** 2026-10-13  
**Trạng thái:** Approved  
**Vision:** [Shift Vision](./2026-10-13-vision.md)  
**Requirements:** [Shift Requirements](./2026-10-13-requirements.md)  
**Architecture:** [Shift Architecture](./2026-10-13-architecture.md)  
**Sequence diagrams:** [Shift Sequences](./2026-10-13-sequence-diagrams.adoc)

Tài liệu này là nguồn chuẩn để sinh OpenAPI 3.1, AsyncAPI/JSON Schema và migration plan cho Shift Service. Các contract Order/Payment được điều chỉnh bổ sung trong phần 9 để đảm bảo trách nhiệm giải trình tiền mặt (cash accountability) không bị suy diễn từ thời gian hoặc chi nhánh.

## 1. Quy ước hợp đồng (Contract conventions)

### 1.1 API và mã hóa (API & encoding)

- Public REST: `/api/v1`, content type `application/json; charset=utf-8`.
- Internal REST: `/internal/v1`, chỉ dùng workload identity/mTLS, không mở ra ngoài qua Angular.
- Events: AsyncAPI + JSON Schema, sử dụng event envelope chuẩn của nền tảng platform.
- IDs: Chuỗi UUID v7.
- Timestamp: ISO-8601 UTC; `businessDate` là `YYYY-MM-DD` theo snapshot múi giờ IANA của chi nhánh.
- Tiền tệ: Đơn vị minor `int64` có dấu / không âm; P0 chỉ hỗ trợ `VND`, không dùng số thực dấu phẩy động (floating point).
- Breaking change bắt buộc tạo major URI/event version mới; các trường tùy chọn (optional) mới phải tương thích ngược.

### 1.2 Headers

| Header | Public | Internal | Ý nghĩa |
|---|---:|---:|---|
| `Authorization` | Bắt buộc | Bắt buộc | Cognito JWT hoặc workload credential/mTLS |
| `X-Active-Branch-Id` | Lệnh/truy vấn chi nhánh | Không dùng làm thẩm quyền | Chi nhánh UI đang chọn; server bắt buộc kiểm tra lại quyền thành viên |
| `X-Correlation-Id` | Khuyến nghị | Bắt buộc | Trace xuyên suốt POS/Saga/Payment/Shift/events |
| `X-Causation-Id` | Không | Khuyến nghị | Command/event gây ra thao tác |
| `Idempotency-Key` | Đột biến có thể thử lại | Đột biến có thể thử lại | Phạm vi tenant + thao tác + actor/workload |
| `If-Match` | Lệnh xem xét / phiên bản | Khi dùng phiên bản | Token kiểm soát đồng thời lạc quan (optimistic concurrency token) |
| `Content-Type` | Body API | Body API | `application/json; charset=utf-8` |

Client không được phép truyền `tenantId`, role, actor hoặc quyền sở hữu chi nhánh trong request body để thay thế ngữ cảnh xác thực (auth context). `openedForUserId`/`closedForUserId` chỉ là yêu cầu chỉ định thu ngân thực tế (effective operator) và phải được máy chủ thẩm định.

### 1.3 Envelope thành công (Success envelope)

```json
{
  "data": {},
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

### 1.4 Envelope lỗi (Error envelope)

```json
{
  "error": {
    "code": "transaction_after_close_fence",
    "message": "The shift no longer accepts new transactions",
    "details": [
      {
        "field": "shiftId",
        "code": "fence_closed",
        "message": "Start a new shift before retrying checkout"
      }
    ],
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

Không trả về SQL, JWT, secret của đối tác thanh toán, raw signature hoặc tiết lộ sự tồn tại của tài nguyên ngoài phạm vi thẩm quyền.

### 1.5 Mã HTTP và mã lỗi (HTTP & error codes)

| HTTP | Mã lỗi | Khi áp dụng | Khả năng thử lại (Retry) |
|---:|---|---|---:|
| 200 | — | Đọc dữ liệu / Lệnh kết thúc thành công | Không |
| 201 | — | Đã tạo ca / tiếp nhận giao dịch / lượt kiểm đếm | Không |
| 202 | `close_processing` | Rào chắn đã commit, tiến trình dọn dẹp (finalizer) đang drain | Poll/thử lại cùng key |
| 401 | `unauthorized` | Xác thực thiếu/sai/hết hạn | Sau khi làm mới token |
| 403 | `forbidden` | Sai vai trò (role) / tenant / chi nhánh | Không |
| 404 | `not_found` | Không tồn tại trong phạm vi được ủy quyền | Không |
| 409 | `shift_already_open` | Đã tồn tại slot ACTIVE / PENDING_REVIEW / CLOSING | Không |
| 409 | `shift_pending_review` | Chi nhánh hoặc ca đang trong trạng thái chờ duyệt | Không; phải giải quyết review |
| 409 | `shift_not_active` | Lệnh không hợp lệ với trạng thái hiện tại của ca | Không |
| 409 | `transaction_after_close_fence` | Yêu cầu tiếp nhận mới gửi sau rào chắn đóng ca | Không; cần mở ca mới |
| 409 | `cash_count_required` | Chính sách yêu cầu kiểm đếm nhưng request thiếu dữ liệu đếm | Sửa request |
| 409 | `version_conflict` | Phiên bản If-Match hoặc review đã cũ | Đọc lại dữ liệu |
| 409 | `idempotency_conflict` | Trùng idempotency key nhưng hash request chuẩn tắc khác nhau | Không |
| 409 | `duplicate_request` | Yêu cầu đã có kết quả ngữ nghĩa trước đó | Dùng kết quả cũ |
| 409 | `count_attempt_immutable` | Cố ý chỉnh sửa lượt kiểm đếm đã gửi | Không |
| 409 | `late_cash_fact_reconciliation_required` | Dữ liệu sự kiện trễ không thể làm đột biến ca đã đóng | Yêu cầu vận hành xử lý |
| 409 | `opening_float_not_returned` | Hủy ca nhưng thiếu xác nhận hoàn đủ tiền đầu ca | Sửa request |
| 422 | `validation_error` | Sai cấu trúc schema hoặc dữ liệu nghiệp vụ không hợp lệ | Không |
| 422 | `invalid_amount` | Số tiền âm, tràn số hoặc sai loại tiền tệ | Không |
| 422 | `foreign_shift_reference` | Ca làm việc không cùng tenant / chi nhánh | Không |
| 422 | `manual_cash_movement_not_supported` | Nạp/rút tiền thủ công (cash-in/out) nằm ngoài phạm vi | Không |
| 429 | `rate_limited` | Vượt giới hạn tỷ lệ API Gateway / service | Chờ theo thuật toán backoff |
| 503 | `service_unavailable` | Cơ sở dữ liệu hoặc phụ thuộc không an toàn để tiếp tục | Thử lại có giới hạn |

## 2. Hợp đồng phân quyền (Authorization contract)

| Thao tác | Owner (Chủ chuỗi) | Manager (Quản lý) | Cashier (Thu ngân) | Workload (Dịch vụ nội bộ) |
|---|---:|---:|---:|---:|
| Mở ca | Chi nhánh được chọn | Chi nhánh phân công | Chi nhánh phân công | — |
| Đọc ca hiện tại / lịch sử | Phạm vi tenant/chi nhánh | Chi nhánh phân công | Chi nhánh phân công | — |
| Đóng ca của chính mình | Có | Có | Chỉ người mở ca | — |
| Đóng ca thay thế / ghi đè (override) | Yêu cầu nhập lý do | Yêu cầu nhập lý do | Không | — |
| Hủy ca bị bỏ quên (abandoned shift) | Lý do + hoàn đủ tiền | Lý do + hoàn đủ tiền | Không | — |
| Gửi kiểm đếm ban đầu / đếm lại | Có nếu đóng vai trò thu ngân | Có nếu đóng vai trò thu ngân | Có | — |
| Phê duyệt / yêu cầu kiểm đếm lại | Có | Có | Không | — |
| Tiếp nhận giao dịch (admission) | — | — | — | Workload của Order/Saga/Payment |
| Nạp dữ liệu tiền mặt (cash fact ingestion) | — | — | — | Workload của Payment |
| Xử lý ca đối soát (reconciliation) | Ops / Owner giới hạn | Quyền Ops được phân công | Không | Workload vận hành (operations) |

Identity cung cấp thông tin actor, tenant, branch và quyết định role. Shift kiểm tra lại quyền sở hữu tài nguyên tại tầng application và repository. Thao tác của Manager/Owner thực hiện thay thu ngân bắt buộc có `reason`; hệ thống ghi nhận tách bạch actor thực hiện và thu ngân thực tế.

## 3. Bề mặt API công khai (Public API surface)

| Phương thức | Đường dẫn | Quyền/Phạm vi | Yêu cầu (Request) | Phản hồi (Response) | Trạng thái |
|---|---|---|---|---|---|
| POST | `/api/v1/branches/{branchId}/shifts` | shift:open | `OpenShiftRequest` | `ShiftResponse` | 201, 403, 409, 422 |
| GET | `/api/v1/branches/{branchId}/shifts/current` | shift:read | — | `CurrentShiftResponse` | 200, 403, 404 |
| GET | `/api/v1/branches/{branchId}/shifts` | shift:read | cursor/filter | `ShiftPage` | 200, 403, 422 |
| GET | `/api/v1/shifts/{shiftId}` | shift:read | — | `ShiftDetailResponse` | 200, 403, 404 |
| POST | `/api/v1/shifts/{shiftId}/close` | shift:close | `CloseShiftRequest` | `CloseShiftResponse` | 200, 202, 403, 404, 409, 422 |
| POST | `/api/v1/shifts/{shiftId}/cancel` | shift:cancel | `CancelShiftRequest` | `ShiftResponse` | 200, 403, 404, 409, 422 |
| POST | `/api/v1/shifts/{shiftId}/cash-counts` | shift:count | `RecountRequest` | `CashCountAttemptResponse` | 201, 403, 404, 409, 422 |
| POST | `/api/v1/shifts/{shiftId}/cash-counts/{countAttemptId}/approve` | shift:review | `ApproveCountRequest` | `ShiftResponse` | 200, 403, 404, 409, 422 |
| POST | `/api/v1/shifts/{shiftId}/cash-counts/{countAttemptId}/request-recount` | shift:review | `RequestRecountRequest` | `CashCountAttemptResponse` | 200, 403, 404, 409 |

`POST /cash-counts` chỉ dùng cho lần kiểm đếm lại (recount) sau khi Quản lý/Chủ chuỗi đã yêu cầu. Lượt đếm ban đầu của P1 nằm trong `CloseShiftRequest` để rào chắn và đếm tiền được xử lý trong cùng một thao tác đóng ca.

### 3.1 Mở ca (Open shift)

`POST /api/v1/branches/{branchId}/shifts`

Request:

```json
{
  "openingFloatMinor": 1000000,
  "currency": "VND",
  "openedForUserId": null,
  "reason": null
}
```

| Trường | Kiểu dữ liệu | Bắt buộc | Quy tắc |
|---|---|---:|---|
| `openingFloatMinor` | int64 | Có | `>= 0`, P0 tối đa theo chính sách tenant |
| `currency` | enum | Có | `VND` |
| `openedForUserId` | UUID/null | Không | Thu ngân thực tế; mặc định là actor; chỉ dành cho Manager/Owner |
| `reason` | string/null | Điều kiện | Bắt buộc khi actor mở thay người khác hoặc ghi đè; 1–500 ký tự |

Response `201`:

```json
{
  "data": {
    "shiftId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
    "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789013",
    "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789014",
    "openedByUserId": "0192b2d0-7f1d-7abc-8d2c-123456789015",
    "operatorUserId": "0192b2d0-7f1d-7abc-8d2c-123456789016",
    "status": "ACTIVE",
    "admissionPhase": "OPEN",
    "openingFloatMinor": 1000000,
    "currency": "VND",
    "businessDate": "2026-10-13",
    "branchTimezone": "Asia/Ho_Chi_Minh",
    "version": 1,
    "openedAt": "2026-10-12T17:00:00Z",
    "closeStatus": "NOT_STARTED"
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

### 3.2 Ca hiện tại và lịch sử (Current shift and history)

`GET /api/v1/branches/{branchId}/shifts/current`

- Trả về `ACTIVE`, `PENDING_REVIEW` hoặc `ACTIVE + closeStatus=PROCESSING` nếu rào chắn đóng ca đã commit.
- Nếu không có ca nào đang hoạt động, trả về `404 no_active_shift` trong phạm vi thẩm quyền.
- Trạng thái `PENDING_REVIEW` bắt buộc phải kèm theo `reviewReason`, `latestCountAttempt`, `branchCheckoutBlocked=true`.

`GET /api/v1/branches/{branchId}/shifts?limit=20&cursor=<opaque>&status=CLOSED`

- Phân trang bằng cursor ổn định theo cặp `(createdAt DESC, shiftId DESC)`.
- Tham số `limit` mặc định là 20, tối đa 100.
- Các bộ lọc được phép: `status`, `businessDateFrom`, `businessDateTo`, `openedByUserId`, `hasDiscrepancy`.
- Vị từ phạm vi phân quyền (scope predicate) được áp dụng trước khi xử lý cursor; client không được phép tự giải mã hay chỉnh sửa cursor.

### 3.3 Đóng ca (Close shift)

`POST /api/v1/shifts/{shiftId}/close`

Request P0 (đóng nhanh không kiểm đếm):

```json
{
  "closedForUserId": null,
  "reason": null,
  "countedCashMinor": null,
  "countNote": null
}
```

Request P1 (khi chính sách kiểm đếm tiền mặt được bật):

```json
{
  "closedForUserId": null,
  "reason": null,
  "countedCashMinor": 2875000,
  "countNote": "Đã đếm cùng quản lý"
}
```

| Trường | Kiểu | Bắt buộc | Quy tắc |
|---|---|---:|---|
| `closedForUserId` | UUID/null | Không | Thu ngân thực tế; mặc định là actor; đóng thay cần reason |
| `reason` | string/null | Điều kiện | Bắt buộc khi đóng thay mặt hoặc ghi đè; 1–500 ký tự |
| `countedCashMinor` | int64/null | Theo chính sách | Không âm; bắt buộc khi chính sách kiểm đếm P1 được bật |
| `countNote` | string/null | Theo chênh lệch | Bắt buộc khi vượt ngưỡng lệch hoặc còn admission chưa giải quyết |

Response `200` hoàn tất (terminal):

```json
{
  "data": {
    "closeOperationId": "0192b2d0-7f1d-7abc-8d2c-123456789020",
    "closeStatus": "COMPLETED",
    "shift": {
      "shiftId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
      "status": "CLOSED",
      "admissionPhase": "FROZEN",
      "version": 4,
      "closedAt": "2026-10-13T00:05:00Z"
    },
    "cashSummary": {
      "openingFloatMinor": 1000000,
      "cashCollectedMinor": 2100000,
      "cashRefundedMinor": 22500,
      "expectedCashMinor": 3077500,
      "countedCashMinor": 3077500,
      "discrepancyMinor": 0,
      "currency": "VND",
      "sourceWatermark": "admission-seq-42",
      "calculatedAt": "2026-10-13T00:05:00Z"
    }
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

Response `202` đang xử lý:

```json
{
  "data": {
    "closeOperationId": "0192b2d0-7f1d-7abc-8d2c-123456789020",
    "closeStatus": "PROCESSING",
    "shift": {
      "shiftId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
      "status": "ACTIVE",
      "admissionPhase": "CLOSING",
      "version": 3,
      "closeFenceSeq": 42,
      "closeFenceAt": "2026-10-13T00:04:59Z"
    },
    "pollAfterMs": 500
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

Khi ở trạng thái `PROCESSING`, các lần thử lại bắt buộc phải giữ nguyên `Idempotency-Key`; không tạo thao tác đóng ca mới. Nếu quá thời gian drain timeout, thao tác sẽ chuyển sang trạng thái kết thúc `PENDING_REVIEW` với `reviewReason=UNRESOLVED_PRE_FENCE_ADMISSION`.

### 3.4 Hủy ca bị bỏ quên (Cancel abandoned shift)

`POST /api/v1/shifts/{shiftId}/cancel`

```json
{
  "reason": "Ca mở nhầm, đã hoàn lại đủ tiền đầu ca",
  "openingFloatReturnedMinor": 1000000
}
```

Hủy ca chỉ thành công khi ca đang `ACTIVE`, chưa phát sinh bất kỳ giao dịch bán/hoàn tiền mặt nào được tiếp nhận, và `openingFloatReturnedMinor` khớp chính xác với `openingFloatMinor`. Thao tác hủy không phát ra sự kiện `ShiftClosed.v1`, không tạo số liệu chênh lệch và không tự động mở ca mới.

### 3.5 Kiểm đếm lại và phê duyệt (Recount and review)

Yêu cầu kiểm đếm lại (Request recount):

```json
{
  "reason": "Đếm lại do lệch 5.000 VND",
  "expectedReviewVersion": 2
}
```

Gửi kết quả đếm lại (Submit recount):

```json
{
  "countedCashMinor": 3072500,
  "note": "Đã đếm lại với quản lý"
}
```

Phê duyệt (Approve):

```json
{
  "decisionNote": "Đã xác nhận lệch theo biên bản",
  "expectedReviewVersion": 3
}
```

Lệnh phê duyệt không nhận `countedCashMinor` từ bên ngoài; số tiền chỉ được trích xuất từ bản ghi kiểm đếm bất biến (immutable attempt). Header `If-Match` hoặc trường `expectedReviewVersion` là bắt buộc để ngăn chặn phê duyệt nhầm phiên bản cũ.

## 4. Bề mặt API nội bộ (Internal API surface)

| Phương thức | Đường dẫn | Caller | Request | Response | Trạng thái |
|---|---|---|---|---|---|
| POST | `/internal/v1/shifts/{shiftId}/transaction-admissions` | Order/Saga/Payment | `CreateAdmissionRequest` | `AdmissionResponse` | 201, 409, 422, 503 |
| POST | `/internal/v1/shifts/{shiftId}/transaction-admissions/{admissionId}/complete` | Order/Saga/Payment | `CompleteAdmissionRequest` | `AdmissionResponse` | 200, 404, 409 |
| POST | `/internal/v1/shifts/{shiftId}/cash-facts` | Payment | `CashFactRequest` | `CashFactResponse` | 201/200, 409, 422 |
| GET | `/internal/v1/shifts/{shiftId}/cash-summary` | Payment/Saga/Reporting | query `asOfFence` | `CashSummaryResponse` | 200, 404, 409 |
| POST | `/internal/v1/shifts/reconciliation-cases/{caseId}/resolve` | Vận hành (Operations) | `ResolveCaseRequest` | `ReconciliationCaseResponse` | 200, 403, 404, 409 |

### 4.1 Tiếp nhận giao dịch (Transaction admission)

Request:

```json
{
  "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789013",
  "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789014",
  "sourceType": "ORDER_CHECKOUT",
  "sourceId": "0192b2d0-7f1d-7abc-8d2c-123456789030",
  "transactionKind": "CASH_SALE",
  "amountMinor": 125000,
  "currency": "VND",
  "correlationId": "0192b2d0-7f1d-7abc-8d2c-123456789031"
}
```

Các giá trị `tenantId`/`branchId` trong body phải khớp với chữ ký ngữ cảnh của workload. `transactionKind` bao gồm `CASH_SALE`, `CASH_REFUND` hoặc `NON_CASH_CHECKOUT`. Response trả về:

```json
{
  "data": {
    "admissionId": "0192b2d0-7f1d-7abc-8d2c-123456789032",
    "shiftId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
    "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789014",
    "admissionSeq": 42,
    "admittedAt": "2026-10-13T00:04:59Z",
    "status": "ADMITTED",
    "cashAccountability": true,
    "shiftVersion": 3
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

Yêu cầu tiếp nhận gửi sau rào chắn đóng ca sẽ trả về `409 transaction_after_close_fence`; các yêu cầu đã tiếp nhận trước rào chắn giữ nguyên sequence ngay cả khi gói tin phân phối bị trễ.

### 4.2 Hoàn tất tiếp nhận (Complete admission)

Request:

```json
{
  "completionStatus": "COMPLETED",
  "sourceId": "0192b2d0-7f1d-7abc-8d2c-123456789030",
  "correlationId": "0192b2d0-7f1d-7abc-8d2c-123456789031"
}
```

Trường `completionStatus` nhận các giá trị `COMPLETED`, `FAILED` hoặc `CANCELLED`. Giao dịch tiền mặt thành công vẫn bắt buộc phải gửi `CashFactRequest`; việc hoàn tất tiếp nhận không tự động cộng vào số tiền mặt kỳ vọng.

### 4.3 Sự kiện tiền mặt (Cash fact)

Request:

```json
{
  "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789013",
  "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789014",
  "shiftId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
  "admissionId": "0192b2d0-7f1d-7abc-8d2c-123456789032",
  "sourceType": "PAYMENT",
  "sourceId": "0192b2d0-7f1d-7abc-8d2c-123456789033",
  "factKind": "CASH_COLLECTION",
  "amountMinor": 125000,
  "currency": "VND",
  "factStatus": "FINALIZED",
  "admittedAt": "2026-10-13T00:04:59Z",
  "occurredAt": "2026-10-13T00:05:00Z",
  "correlationId": "0192b2d0-7f1d-7abc-8d2c-123456789031"
}
```

Trường `factKind` là `CASH_COLLECTION` hoặc `CASH_REFUND`. Số tiền hoàn lại (refund amount) luôn là số nguyên không âm ở input và được trừ đi trong công thức tính toán. Shift kiểm tra khóa nguồn (source key), quan hệ sở hữu admission, tenant/chi nhánh, tiền tệ, trạng thái và thứ tự rào chắn. Sự kiện được tiếp nhận sau rào chắn sẽ không làm thay đổi bản tóm tắt lịch sử.

### 4.4 Tóm tắt tiền mặt (Cash summary)

Các trường phản hồi:

| Trường | Ý nghĩa |
|---|---|
| `openingFloatMinor` | Ảnh chụp tiền đầu ca |
| `cashCollectedMinor` | Tổng số tiền thu finalized hợp lệ |
| `cashRefundedMinor` | Tổng số tiền hoàn finalized hợp lệ |
| `expectedCashMinor` | Kết quả từ công thức tính toán |
| `sourceWatermark` | Thứ tự admission/fact cao nhất được đưa vào tính toán |
| `complete` | Đã đủ điều kiện để kiểm đếm / phê duyệt hay chưa |
| `unresolvedAdmissionCount` | Số lượng admission trước fence chưa kết thúc |
| `unresolvedCaseCount` | Số ca đối soát đang ảnh hưởng đến bản tóm tắt |
| `calculatedAt` | Thời điểm tính toán UTC |
| `fenceVersion` | Phiên bản ảnh chụp rào chắn được áp dụng |

Khi `complete=false`, không được phép phê duyệt kiểm đếm. Câu truy vấn không đọc Payment DB; chỉ tính toán trên các facts đã được ghi nhận vào Shift.

### 4.5 Xử lý ca đối soát (Resolve reconciliation case)

```json
{
  "resolution": "ACCEPT_AS_LATE_FACT",
  "note": "Đối chiếu biên bản cuối ngày, không điều chỉnh shift đã đóng",
  "externalReference": "OPS-2026-001"
}
```

Các mã giải quyết được phép: `ACCEPT_AS_LATE_FACT`, `REJECT_INVALID_FACT`, `DUPLICATE_CONFIRMED`, `NO_ACTION_REQUIRED`. Thao tác giải quyết không mở lại (reopen) hoặc thay đổi ca `CLOSED`; chỉ tạo bản ghi điều chỉnh/đối soát và phát sự kiện `ShiftCashFactReconciled.v1`.

## 5. Hợp đồng trạng thái và tính lũy nghiệm (State and idempotency contract)

### 5.1 Chuyển đổi trạng thái (State transitions)

| Trạng thái công khai | Pha tiếp nhận nội bộ | Lệnh thực hiện | Kết quả |
|---|---|---|---|
| none | — | Mở ca (open) | `ACTIVE/OPEN` |
| `ACTIVE` | `OPEN` | Đóng rào chắn (close fence) | `ACTIVE/CLOSING` + 202 hoặc chuyển sang finalizer |
| `ACTIVE` | `FROZEN` | Đóng P0 hoàn tất | `CLOSED/FROZEN` |
| `ACTIVE` | `FROZEN` | Kiểm đếm P1 vượt ngưỡng lệch | `PENDING_REVIEW/FROZEN` |
| `PENDING_REVIEW` | `FROZEN` | Phê duyệt lượt đếm mới nhất | `CLOSED/FROZEN` |
| `PENDING_REVIEW` | `FROZEN` | Yêu cầu kiểm đếm lại | Giữ nguyên trạng thái + yêu cầu lượt đếm mới |
| `ACTIVE` | `OPEN` | Hủy ca + không có tiền mặt + hoàn đủ float | `CANCELLED/FROZEN` |
| `CLOSED`/`CANCELLED` | `FROZEN` | Mọi lệnh làm thay đổi vòng đời | Báo lỗi xung đột (conflict); không cho phép mở lại |

Trạng thái nội bộ `CLOSING` không phải là slot mở và không cho phép mở ca mới hay tiếp nhận giao dịch mới.

### 5.2 Quy tắc lũy nghiệm (Idempotency rules)

- Đột biến công khai: Dùng `Idempotency-Key` kết hợp băm dữ liệu chuẩn tắc (canonical JSON hash).
- Tiếp nhận nội bộ: Dùng khóa thao tác nguồn kết hợp khử trùng lặp theo bộ ba `(sourceType, sourceId, shiftId)`.
- Sự kiện tiền mặt: Khóa nguồn duy nhất đóng vai trò là căn cứ khử trùng lặp có thẩm quyền; sự kiện trùng lặp từ nguồn đã biết sẽ trả lại kết quả ngữ nghĩa trước đó.
- Phê duyệt xem xét: Dùng header `If-Match` / phiên bản review để ngăn chặn duyệt trùng lặp; thử lại cùng key trả về kết quả cuối cùng.
- Outbox: Duy nhất một ID sự kiện cho mỗi lần chuyển đổi aggregate đã commit; phát lại sự kiện không tạo ra event ID mới.

## 6. Hợp đồng sự kiện (Event contracts)

### 6.1 Envelope dùng chung (Common envelope)

```json
{
  "eventId": "0192b2d0-7f1d-7abc-8d2c-123456789040",
  "eventType": "ShiftClosed.v1",
  "eventVersion": 1,
  "occurredAt": "2026-10-13T00:05:00Z",
  "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789013",
  "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789014",
  "aggregateType": "SHIFT",
  "aggregateId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
  "aggregateVersion": 4,
  "correlationId": "0192b2d0-7f1d-7abc-8d2c-123456789031",
  "causationId": "0192b2d0-7f1d-7abc-8d2c-123456789032",
  "producer": "shift-service",
  "payload": {}
}
```

### 6.2 Danh mục sự kiện (Event catalogue)

| Tên sự kiện | Dữ liệu Payload | Bên tiêu thụ chính (Consumers) |
|---|---|---|
| `ShiftOpened.v1` | Thông tin ca, người vận hành, tiền đầu ca, ngày kinh doanh, snapshot chính sách | Reporting, Audit |
| `ShiftCloseStarted.v1` | Thao tác đóng ca, số thứ tự/thời điểm rào chắn, actor | Reporting, Audit/ops |
| `ShiftCountSubmitted.v1` | Bản ghi đếm tiền, số tiền kỳ vọng/thực tế/lệch, watermark | Reporting, Audit |
| `ShiftReviewRequired.v1` | Lý do, tóm tắt chênh lệch/đối soát, ID lượt đếm | Reporting, Audit, ops |
| `ShiftRecountRequested.v1` | Lượt đếm cũ, lý do yêu cầu đếm lại, phiên bản review | Reporting, Audit |
| `ShiftClosed.v1` | Ca ở trạng thái đóng cuối cùng, tóm tắt tiền mặt, tham chiếu kiểm đếm/phê duyệt | Reporting, Audit |
| `ShiftCancelled.v1` | Lý do hủy, xác nhận hoàn đủ tiền đầu ca | Reporting, Audit |
| `ShiftReconciliationCaseOpened.v1` | Hồ sơ ca đối soát, sự kiện/tiếp nhận nguồn, lý do và thời điểm phát hiện | Reporting, Audit, ops |
| `ShiftCashFactReconciled.v1` | Hồ sơ ca đối soát, sự kiện nguồn, phương án giải quyết và người thực hiện | Reporting, Audit |

### 6.3 Payload của `ShiftOpened.v1`

```json
{
  "shiftId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
  "openedByUserId": "0192b2d0-7f1d-7abc-8d2c-123456789015",
  "operatorUserId": "0192b2d0-7f1d-7abc-8d2c-123456789016",
  "status": "ACTIVE",
  "openingFloatMinor": 1000000,
  "currency": "VND",
  "businessDate": "2026-10-13",
  "branchTimezone": "Asia/Ho_Chi_Minh",
  "openedAt": "2026-10-12T17:00:00Z",
  "policySnapshot": {
    "cashCountRequired": false,
    "discrepancyThresholdMinor": 0,
    "drainTimeoutMs": 5000
  },
  "reason": null
}
```

### 6.4 Payload của `ShiftClosed.v1`

```json
{
  "shiftId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
  "status": "CLOSED",
  "closeOperationId": "0192b2d0-7f1d-7abc-8d2c-123456789020",
  "closedByUserId": "0192b2d0-7f1d-7abc-8d2c-123456789015",
  "operatorUserId": "0192b2d0-7f1d-7abc-8d2c-123456789015",
  "closeFenceSeq": 42,
  "closedAt": "2026-10-13T00:05:00Z",
  "cashSummary": {
    "openingFloatMinor": 1000000,
    "cashCollectedMinor": 2100000,
    "cashRefundedMinor": 22500,
    "expectedCashMinor": 3077500,
    "countedCashMinor": 3077500,
    "discrepancyMinor": 0,
    "currency": "VND",
    "sourceWatermark": "admission-seq-42",
    "calculatedAt": "2026-10-13T00:05:00Z"
  },
  "countAttemptId": null,
  "reviewDecisionId": null,
  "reason": null
}
```

Yêu cầu đối với bên tiêu thụ sự kiện (Event consumer):

- Khử trùng lặp theo `eventId` và bảo đảm thứ tự xử lý bằng cặp `(aggregateId, aggregateVersion)`.
- Bỏ qua các trường mở rộng chưa biết trong tương lai mà không gây lỗi parser.
- Không xem các read model chiếu lại của Reporting/Audit là nguồn chân lý để quyết định đóng ca hoặc tiếp nhận thanh toán.
- Khi replay sự kiện, bắt buộc giữ nguyên `eventId` và `occurredAt` gốc.

## 7. Các kiểu nguyên thủy JSON Schema (JSON Schema primitives)

```yaml
MoneyMinor:
  type: integer
  format: int64
  minimum: 0

SignedMoneyMinor:
  type: integer
  format: int64

Currency:
  type: string
  enum: [VND]

ShiftStatus:
  type: string
  enum: [ACTIVE, PENDING_REVIEW, CLOSED, CANCELLED]

AdmissionPhase:
  type: string
  enum: [OPEN, CLOSING, FROZEN]

AdmissionStatus:
  type: string
  enum: [ADMITTED, COMPLETED, FAILED, CANCELLED]

CashFactKind:
  type: string
  enum: [CASH_COLLECTION, CASH_REFUND]
```

`expectedCashMinor` và `discrepancyMinor` được máy chủ tính toán tự động; client không được phép ghi đè trong command. `discrepancyMinor` mang giá trị số nguyên có dấu; giá trị tuyệt đối `abs(discrepancyMinor)` được dùng để so sánh với ngưỡng cho phép.

## 8. Hợp đồng dữ liệu — PostgreSQL schema (Data contract)

Cơ sở dữ liệu: PostgreSQL, thuộc quyền sở hữu duy nhất của Shift Service. Mọi cột mới đều có ghi chú giải thích chuẩn xác như bảng dưới đây.

### 8.1 `shifts`

| Cột | Kiểu | Null | Mô tả / Chú thích |
|---|---|---:|---|
| `id` | UUID | Không | UUID v7 định danh ca làm việc |
| `tenant_id` | UUID | Không | Tenant sở hữu ca làm việc |
| `branch_id` | UUID | Không | Chi nhánh sử dụng một két tiền mặt duy nhất |
| `status` | TEXT | Không | `ACTIVE`, `PENDING_REVIEW`, `CLOSED`, `CANCELLED` |
| `admission_phase` | TEXT | Không | Pha nội bộ: `OPEN`, `CLOSING`, `FROZEN` |
| `opened_by_user_id` | UUID | Không | Người dùng thực hiện thao tác mở ca |
| `operator_user_id` | UUID | Không | Thu ngân chịu trách nhiệm thực tế |
| `opening_float_minor` | BIGINT | Không | Tiền mặt đầu ca, số nguyên VND, không âm |
| `currency` | TEXT | Không | Ảnh chụp loại tiền tệ; P0 là `VND` |
| `business_date` | DATE | Không | Ngày kinh doanh tính theo múi giờ chi nhánh |
| `branch_timezone` | TEXT | Không | Ảnh chụp múi giờ IANA tại thời điểm mở ca |
| `opened_at` | TIMESTAMPTZ | Không | Thời điểm mở ca theo chuẩn UTC |
| `close_operation_id` | UUID | Có | ID thao tác đóng ca hiện tại hoặc đã hoàn tất |
| `close_fence_seq` | BIGINT | Có | Số thứ tự admission cuối cùng được phép hoàn tất |
| `close_fence_at` | TIMESTAMPTZ | Có | Thời điểm rào chắn commit theo đồng hồ DB |
| `close_fence_version` | BIGINT | Có | Phiên bản bảo vệ thao tác đóng ca |
| `closed_by_user_id` | UUID | Có | Người thực hiện đóng ca hoặc phê duyệt |
| `closed_at` | TIMESTAMPTZ | Có | Thời điểm đóng ca chính thức theo UTC |
| `cancelled_by_user_id` | UUID | Có | Người thực hiện hủy ca bị bỏ quên |
| `cancelled_at` | TIMESTAMPTZ | Có | Thời điểm hủy ca theo UTC |
| `cancellation_reason` | TEXT | Có | Lý do hủy ca đã được khử độc dữ liệu |
| `opening_float_returned_minor` | BIGINT | Có | Số tiền xác nhận đã hoàn lại khi hủy ca |
| `review_reason` | TEXT | Có | Lý do chuyển sang `PENDING_REVIEW` |
| `review_version` | BIGINT | Không | Phiên bản lạc quan phục vụ xem xét / đếm lại |
| `latest_count_attempt_id` | UUID | Có | Lượt kiểm đếm mới nhất |
| `policy_snapshot` | JSONB | Không | Snapshot chính sách ngưỡng lệch, kiểm đếm, drain timeout |
| `version` | BIGINT | Không | Phiên bản tăng đơn điệu của aggregate |
| `created_at` | TIMESTAMPTZ | Không | Thời điểm tạo bản ghi |
| `updated_at` | TIMESTAMPTZ | Không | Thời điểm cập nhật cuối cùng |

Ràng buộc và chỉ mục (Constraints/indexes):

- Khóa chính PK: `shifts_pkey(id)`.
- `CHECK (opening_float_minor >= 0)`.
- `CHECK (status IN (...))` và `CHECK (admission_phase IN (...))`.
- Chỉ mục duy nhất có điều kiện (Partial unique index) `uq_shifts_open_slot` trên `(tenant_id, branch_id)` khi trạng thái nằm trong danh sách `ACTIVE`, `PENDING_REVIEW`.
- Chỉ mục lịch sử `ix_shifts_branch_history` trên `(tenant_id, branch_id, business_date DESC, created_at DESC, id DESC)`.
- Không thực hiện xóa cứng (hard-delete) ca làm việc.

### 8.2 `shift_transaction_admissions`

| Cột | Kiểu | Null | Mô tả / Chú thích |
|---|---|---:|---|
| `id` | UUID | Không | UUID v7 định danh quyền tiếp nhận opaque |
| `tenant_id` | UUID | Không | Tenant lấy từ ngữ cảnh workload đã ký xác thực |
| `branch_id` | UUID | Không | Chi nhánh tiếp nhận giao dịch |
| `shift_id` | UUID | Không | Ca làm việc mục tiêu (tham chiếu opaque liên service) |
| `source_type` | TEXT | Không | `ORDER_CHECKOUT`, `PAYMENT`, `REFUND` |
| `source_id` | UUID | Không | ID nguồn của đơn hàng, thanh toán hoặc hoàn tiền |
| `transaction_kind` | TEXT | Không | `CASH_SALE`, `CASH_REFUND`, `NON_CASH_CHECKOUT` |
| `amount_minor` | BIGINT | Không | Ảnh chụp số tiền liên quan đến nguồn |
| `currency` | TEXT | Không | Ảnh chụp loại tiền tệ |
| `admission_seq` | BIGINT | Không | Số thứ tự tăng dần đơn điệu trong ca |
| `admitted_at` | TIMESTAMPTZ | Không | Thời điểm tiếp nhận theo đồng hồ DB |
| `status` | TEXT | Không | `ADMITTED`, `COMPLETED`, `FAILED`, `CANCELLED` |
| `cash_accountability` | BOOLEAN | Không | Có ảnh hưởng đến bản tóm tắt tiền mặt hay không |
| `completed_at` | TIMESTAMPTZ | Có | Thời điểm giao dịch kết thúc |
| `completion_source_id` | UUID | Có | ID nguồn xác nhận hoàn tất |
| `correlation_id` | UUID | Không | ID phục vụ truy vết / đối soát |
| `created_at` | TIMESTAMPTZ | Không | Thời điểm tạo bản ghi |
| `updated_at` | TIMESTAMPTZ | Không | Thời điểm cập nhật trạng thái |

Ràng buộc và chỉ mục:

- Ràng buộc duy nhất `(tenant_id, shift_id, source_type, source_id)`.
- Ràng buộc duy nhất `(shift_id, admission_seq)`.
- Chỉ mục drain các giao dịch đang chờ `(tenant_id, shift_id, status, admission_seq)`.
- `CHECK (amount_minor >= 0)`.
- Trạng thái chỉ được phép chuyển từ `ADMITTED` sang một trạng thái kết thúc duy nhất.

### 8.3 `cash_facts`

| Cột | Kiểu | Null | Mô tả / Chú thích |
|---|---|---:|---|
| `id` | UUID | Không | UUID v7 định danh sự kiện tiền mặt |
| `tenant_id` | UUID | Không | Tenant sở hữu sự kiện |
| `branch_id` | UUID | Không | Chi nhánh phát sinh sự kiện |
| `shift_id` | UUID | Không | Ca chịu trách nhiệm đối soát |
| `admission_id` | UUID | Không | ID tiếp nhận đã được cấp trước rào chắn |
| `source_type` | TEXT | Không | Loại producer thanh toán hoặc hoàn tiền |
| `source_id` | UUID | Không | ID sự kiện thanh toán / hoàn tiền ổn định |
| `fact_kind` | TEXT | Không | `CASH_COLLECTION` hoặc `CASH_REFUND` |
| `amount_minor` | BIGINT | Không | Số tiền không âm; tiền hoàn được trừ trong công thức |
| `currency` | TEXT | Không | Loại tiền tệ của sự kiện |
| `fact_status` | TEXT | Không | Chỉ `FINALIZED` mới vào tóm tắt; sự kiện bị từ chối chuyển thành case |
| `admission_seq` | BIGINT | Không | Sao chép thứ tự tiếp nhận đáng tin cậy để so rào chắn |
| `admitted_at` | TIMESTAMPTZ | Không | Thời điểm tiếp nhận đáng tin cậy do Shift cấp |
| `occurred_at` | TIMESTAMPTZ | Không | Thời điểm phát sinh nghiệp vụ phía producer |
| `received_at` | TIMESTAMPTZ | Không | Thời điểm Shift nhận được bản tin |
| `correlation_id` | UUID | Không | ID truy vết |
| `created_at` | TIMESTAMPTZ | Không | Thời điểm lưu bản ghi |

Ràng buộc và chỉ mục:

- Ràng buộc duy nhất `(tenant_id, shift_id, source_type, source_id, fact_kind)`.
- Chỉ mục tổng hợp tóm tắt `(tenant_id, shift_id, admission_seq, fact_kind)`.
- `CHECK (amount_minor >= 0)` và bắt buộc tiền tệ là `VND` trong P0.
- Tuyệt đối không cho phép cập nhật số tiền hay định danh nguồn; mọi chỉnh sửa sai sót đều được đưa vào ca đối soát.

### 8.4 `cash_count_attempts`

| Cột | Kiểu | Null | Mô tả / Chú thích |
|---|---|---:|---|
| `id` | UUID | Không | UUID v7 định danh lượt kiểm đếm |
| `tenant_id` | UUID | Không | Tenant sở hữu |
| `branch_id` | UUID | Không | Chi nhánh sở hữu |
| `shift_id` | UUID | Không | Ca làm việc được kiểm đếm |
| `attempt_number` | INTEGER | Không | Thứ tự lần kiểm đếm bắt đầu từ 1, mang tính bất biến |
| `counted_cash_minor` | BIGINT | Không | Số tiền mặt thực tế do thu ngân kiểm đếm nộp vào |
| `opening_float_minor` | BIGINT | Không | Ảnh chụp tiền đầu ca |
| `cash_collected_minor` | BIGINT | Không | Ảnh chụp tổng tiền thu đã chấp nhận |
| `cash_refunded_minor` | BIGINT | Không | Ảnh chụp tổng tiền hoàn đã chấp nhận |
| `expected_cash_minor` | BIGINT | Không | Kết quả tính toán từ công thức |
| `discrepancy_minor` | BIGINT | Không | Số tiền thực tế trừ đi số tiền kỳ vọng |
| `threshold_minor` | BIGINT | Không | Ảnh chụp ngưỡng lệch cho phép theo chính sách |
| `fence_version` | BIGINT | Không | Phiên bản rào chắn đóng ca áp dụng |
| `source_watermark` | TEXT | Không | Watermark sự kiện/tiếp nhận được đưa vào tính toán |
| `status` | TEXT | Không | `SUBMITTED`, `RECOUNT_REQUIRED`, `APPROVED` |
| `note` | TEXT | Có | Bắt buộc khi vượt ngưỡng lệch hoặc còn case chưa giải quyết |
| `counted_by_user_id` | UUID | Không | Người thực hiện kiểm đếm |
| `submitted_at` | TIMESTAMPTZ | Không | Thời điểm nộp kết quả kiểm đếm (bất biến) |
| `reviewed_by_user_id` | UUID | Có | Người thực hiện phê duyệt |
| `reviewed_at` | TIMESTAMPTZ | Có | Thời điểm phê duyệt hoặc yêu cầu đếm lại |
| `decision_note` | TEXT | Có | Ghi chú quyết định của Quản lý / Chủ chuỗi |
| `created_at` | TIMESTAMPTZ | Không | Thời điểm tạo bản ghi |

Ràng buộc và chỉ mục:

- Ràng buộc duy nhất `(shift_id, attempt_number)`.
- Chỉ mục truy vấn lượt mới nhất `(tenant_id, shift_id, attempt_number DESC)`.
- `CHECK (counted_cash_minor >= 0)` và các trường đầu vào không âm.
- Bất biến bắt buộc: `discrepancy_minor = counted_cash_minor - expected_cash_minor`.
- Các trường số tiền sau khi đã nộp là hoàn toàn bất biến.

### 8.5 `reconciliation_cases`

| Cột | Kiểu | Null | Mô tả / Chú thích |
|---|---|---:|---|
| `id` | UUID | Không | UUID v7 định danh hồ sơ đối soát |
| `tenant_id` | UUID | Không | Tenant của hồ sơ |
| `branch_id` | UUID | Không | Chi nhánh của hồ sơ |
| `shift_id` | UUID | Không | Ca làm việc liên quan |
| `case_type` | TEXT | Không | `LATE_FACT`, `UNKNOWN_FACT`, `MISMATCH`, `UNRESOLVED_ADMISSION` |
| `source_type` | TEXT | Có | Loại đối tượng nguồn hoặc producer |
| `source_id` | UUID | Có | ID thanh toán / hoàn tiền / tiếp nhận liên quan |
| `amount_minor` | BIGINT | Có | Số tiền đang được điều tra đối soát |
| `currency` | TEXT | Có | Loại tiền tệ đang điều tra |
| `reason` | TEXT | Không | Lý do phát sinh có thể đọc tự động |
| `status` | TEXT | Không | `OPEN`, `RESOLVED`, `REJECTED` |
| `resolution` | TEXT | Có | Mã phương án giải quyết được phê duyệt |
| `resolution_note` | TEXT | Có | Giải trình chi tiết của nhân viên vận hành |
| `resolved_by_user_id` | UUID | Có | Người thực hiện giải quyết hồ sơ |
| `resolved_at` | TIMESTAMPTZ | Có | Thời điểm giải quyết |
| `correlation_id` | UUID | Không | ID truy vết liên hệ |
| `created_at` | TIMESTAMPTZ | Không | Thời điểm mở hồ sơ đối soát |

Ràng buộc và chỉ mục:

- Chỉ mục các hồ sơ đang mở `(tenant_id, branch_id, status, created_at)`.
- Ràng buộc khóa duy nhất cho các hồ sơ đang hoạt động trên cùng nguồn/lý do để tránh bùng nổ hồ sơ trùng lặp.
- Phương án giải quyết không bao giờ được phép xóa hoặc sửa đổi sự kiện tiền mặt gốc.

### 8.6 `shift_policies`

| Cột | Kiểu | Null | Mô tả / Chú thích |
|---|---|---:|---|
| `id` | UUID | Không | ID chính sách ca |
| `tenant_id` | UUID | Không | Tenant sở hữu |
| `branch_id` | UUID | Có | Null: mặc định toàn tenant; có giá trị: ghi đè cho chi nhánh cụ thể |
| `cash_count_required` | BOOLEAN | Không | Bắt buộc kiểm đếm P1 khi đóng ca |
| `discrepancy_threshold_minor` | BIGINT | Không | Ngưỡng lệch tuyệt đối được phép |
| `drain_timeout_ms` | INTEGER | Không | Thời gian tối đa chờ drain các admission trước rào chắn |
| `effective_from` | TIMESTAMPTZ | Không | Thời điểm bắt đầu hiệu lực UTC |
| `effective_to` | TIMESTAMPTZ | Có | Thời điểm hết hiệu lực UTC |
| `updated_by_user_id` | UUID | Không | Người cập nhật chính sách |
| `created_at` | TIMESTAMPTZ | Không | Thời điểm tạo |
| `updated_at` | TIMESTAMPTZ | Không | Thời điểm cập nhật cuối cùng |

Quy định: `discrepancy_threshold_minor >= 0`, `drain_timeout_ms` nằm trong giới hạn vận hành an toàn. Chính sách có hiệu lực sẽ được lưu snapshot vào `shifts.policy_snapshot` tại thời điểm mở ca.

### 8.7 Các bảng hỗ trợ (Supporting tables)

Các bảng `outbox_messages`, `idempotency_records` và `audit_references` tuân thủ schema chuẩn của nền tảng POS Platform, bổ sung thêm các cột chi tiết dành riêng cho Shift:

| Bảng | Các cột đặc thù và mô tả bắt buộc |
|---|---|
| `outbox_messages` | `aggregate_type` — loại aggregate (`SHIFT`); `aggregate_id` — ID ca hoặc case; `aggregate_version` — thứ tự phiên bản sự kiện; `event_type` — tên sự kiện; `payload` — envelope JSON bất biến; `published_at` — thời điểm gửi thành công; `attempt_count` — số lần thử lại; `next_attempt_at` — lịch trình retry theo backoff |
| `idempotency_records` | `tenant_id` — quyền sở hữu; `operation` — tên lệnh; `idempotency_key` — khóa thử lại; `request_hash` — mã băm chuẩn tắc của payload; `response_status` — mã HTTP đã lưu; `response_body` — kết quả an toàn; `resource_id` — ID tài nguyên đã tạo; `expires_at` — mốc hết hạn lưu trữ |
| `audit_references` | `tenant_id` — quyền sở hữu; `branch_id` — phạm vi chi nhánh; `shift_id` — ca làm việc liên quan; `action` — hành động/lệnh; `actor_user_id` — JWT của người thực hiện; `operator_user_id` — thu ngân thực tế; `reason` — lý do bắt buộc khi ghi đè/hủy/xem xét; `before_state`/`after_state` — ảnh chụp trạng thái trước và sau; `correlation_id` — trace ID; `created_at` — thời điểm audit bất biến |

## 9. Điều chỉnh bổ sung bắt buộc đối với hợp đồng Order và Payment

### 9.1 Điều chỉnh hợp đồng Order (Order contract amendment)

`CreateOrderRequest` bắt buộc phải bổ sung:

| Trường | Kiểu | Bắt buộc | Quy tắc |
|---|---|---:|---|
| `shiftId` | UUID | Có | Ca phải ở trạng thái `ACTIVE`, `admissionPhase=OPEN`, cùng tenant/chi nhánh |

`OrderResponse` bắt buộc phải trả về `shiftId`. `CheckoutRequest` phải bảo toàn và kiểm tra cùng ngữ cảnh ca làm việc này, từ chối thanh toán nếu không thể xin được quyền tiếp nhận (admission) từ Shift. Order/Saga lưu trữ `admissionId` trong ngữ cảnh lần thử checkout nhưng tuyệt đối không cho phép client công khai tự cung cấp giá trị này.

Trước khi bắt đầu thanh toán phía Payment, Saga thực hiện gọi nội bộ:

```text
Shift.createAdmission(
  tenantId, branchId, shiftId,
  sourceType=ORDER_CHECKOUT,
  sourceId=orderId/checkoutAttemptId,
  transactionKind=CASH_SALE|NON_CASH_CHECKOUT,
  amountMinor, currency
)
```

Saga chuyển tiếp các giá trị `admissionId`, `admissionSeq` và `admittedAt` nhận được sang cho Payment. Thao tác thử lại (retry) sử dụng cùng khóa nguồn và khóa lũy nghiệm.

### 9.2 Điều chỉnh hợp đồng Payment (Payment contract amendment)

`InitiatePaymentRequest` bắt buộc phải bổ sung và yêu cầu:

```json
{
  "tenantId": "...",
  "branchId": "...",
  "shiftId": "...",
  "admissionId": "...",
  "admissionSeq": 42,
  "admittedAt": "2026-10-13T00:04:59Z"
}
```

Các quy tắc áp dụng:

- `tenantId`, `branchId`, `shiftId` phải khớp với ngữ cảnh đã ký của Saga và ảnh chụp từ Order.
- `admissionId`, `admissionSeq`, `admittedAt` phải khớp chính xác với phản hồi của Shift; Payment tuyệt đối không tự bịa đặt các trường này.
- `admissionId` là bắt buộc đối với luồng bán hàng / hoàn tiền mặt; trường này là tùy chọn với luồng phi tiền mặt khi admission của Saga được đánh dấu là non-cash.
- Payment không bao giờ được phép suy diễn ca làm việc dựa trên chi nhánh hoặc thời gian.

Payload của sự kiện `PaymentPaid.v1` bắt buộc phải bổ sung:

```json
{
  "tenantId": "...",
  "branchId": "...",
  "shiftId": "...",
  "admissionId": "...",
  "admissionSeq": 42,
  "admittedAt": "2026-10-13T00:04:59Z",
  "sourceId": "payment-id",
  "amountMinor": 125000,
  "currency": "VND",
  "status": "PAID",
  "paidAt": "2026-10-13T00:05:00Z"
}
```

Sự kiện `PaymentRefunded.v1` bắt buộc phải mang đầy đủ metadata về tenant / chi nhánh / ca / tiếp nhận tương tự, kèm theo `refundId`, `amountMinor`, `status=REFUNDED`, `refundedAt` và `sourceId`. Một khoản hoàn tiền mặt chỉ được đưa vào tóm tắt của Shift khi quyền tiếp nhận của nó được cấp trước rào chắn và khoản hoàn tiền đã được finalized thành công.

### 9.3 Tính tương thích của sự kiện nền tảng (Parent event compatibility)

- Các consumer hiện tại phải bỏ qua các trường tùy chọn mới trong `PaymentPaid.v1` trong suốt quá trình triển khai chuyển tiếp.
- Các consumer của Shift phải thực hiện khử trùng lặp theo `eventId` và chấp nhận `aggregateVersion`.
- Schema `shifts` của nền tảng parent phải được mở rộng từ các trường mở/đóng ca cơ bản lên mô hình đầy đủ do Shift sở hữu; tài liệu parent vẫn là bản tóm tắt cấp nền tảng chứ không phải là nguồn chân lý thứ hai.
- Danh mục sự kiện parent phải bao gồm: `ShiftReviewRequired.v1`, `ShiftRecountRequested.v1`, `ShiftCancelled.v1`, `ShiftReconciliationCaseOpened.v1` và `ShiftCashFactReconciled.v1`.

## 10. Kế hoạch di chuyển dữ liệu và triển khai (Migration and rollout contract)

Thứ tự thực hiện migration:

1. Tạo các bảng hỗ trợ `shift_policies`, `shift_transaction_admissions`, `cash_facts`, `cash_count_attempts`, `reconciliation_cases` kèm đầy đủ ghi chú mô tả, ràng buộc và chỉ mục.
2. Mở rộng bảng `shifts` với các cột trạng thái, pha nội bộ, rào chắn, phiên bản review và snapshot chính sách; điền dữ liệu (backfill) cho các dòng ca đang hoạt động với `status=ACTIVE`, `admission_phase=OPEN`, `review_version=1` chỉ sau khi xác minh mỗi chi nhánh chỉ có duy nhất một dòng ca active.
3. Thêm chỉ mục partial unique active-slot index và các ràng buộc duy nhất cho nguồn/idempotency.
4. Triển khai các API của Shift tiếp nhận các trường mới trong khi vẫn bảo đảm tính tương thích với phản hồi cơ bản cũ.
5. Triển khai Order/Saga bắt buộc lưu trữ `shiftId` và metadata tiếp nhận.
6. Triển khai Payment lưu trữ/phát ra metadata ca/tiếp nhận và gửi các sự kiện tiền mặt.
7. Kích hoạt chính sách kiểm đếm P1 theo từng tenant/chi nhánh sau khi vượt qua các bài kiểm thử; chính sách mặc định ban đầu vẫn là đóng ca P0 không kiểm đếm.

Quy tắc tương thích:

- Không bao giờ tạo một sự kiện tiền mặt thiếu `shiftId` hoặc thiếu `admissionId` hợp lệ sau khi cơ chế bắt buộc được kích hoạt.
- Trong giai đoạn triển khai đọc kép (dual-read), các sự kiện Payment cũ thiếu metadata ca làm việc sẽ được đưa vào ca đối soát, tuyệt đối không đoán mò để gán vào ca.
- Khi rollback mã nguồn ứng dụng, tuyệt đối không drop cột hoặc tắt ràng buộc duy nhất; cấu trúc database phải tương thích xuôi.
- Việc vá lỗi hoặc bù dữ liệu (backfill/repair) phải thông qua lệnh Operations có lưu vết audit, không được can thiệp SQL trực tiếp từ client POS.

## 11. Ma trận kiểm thử hợp đồng (Contract test matrix)

| Khu vực kiểm thử | Các kịch bản fixture bắt buộc |
|---|---|
| Mở ca | Mở ca đồng thời (race condition); thử lại lũy nghiệm; sai tenant/chi nhánh; mở thay mặt có kèm lý do |
| Tiếp nhận (Admission) | Tiếp nhận trước rào chắn được chấp thuận; tiếp nhận sau rào chắn bị từ chối; cùng nguồn được khử trùng lặp; ca khác tenant/chi nhánh bị từ chối |
| Đóng ca | Phản hồi 200 hoàn tất; 202 đang drain; drain hoàn tất thành công; drain timeout chuyển sang review; đóng trùng lặp |
| Kiểm đếm (Count) | Công thức số nguyên; ngưỡng lệch bằng 0; số âm/tràn số; chính sách thiếu kiểm đếm; bản ghi đếm tiền mang tính bất biến |
| Xem xét (Review) | Phê duyệt lượt đếm mới nhất; header If-Match đã cũ bị chặn; yêu cầu đếm lại; chỉ Manager/Owner mới được duyệt |
| Hủy ca (Cancel) | Không có tiền mặt + hoàn đủ tiền đầu ca; hoàn thiếu tiền bị từ chối; đã có giao dịch tiền mặt bị từ chối |
| Sự kiện tiền mặt | Thu tiền / hoàn tiền; sự kiện trùng lặp; sự kiện đến trễ; sự kiện không xác định; sai lệch thông tin; sai loại tiền tệ |
| Sự kiện (Events) | Tính nguyên tử qua Outbox; phân phối trùng lặp; thứ tự sự kiện; xử lý qua DLQ / phát lại sự kiện |
| Cách ly (Isolation) | Chống tấn công IDOR xuyên tenant; truy cập sai chi nhánh; không làm lộ sự tồn tại của tài nguyên trái phép |
| Tích hợp liên service | Truyền nhận `shiftId` từ Order; metadata trong PaymentPaid/Refunded; Saga thử lại và tái sử dụng admission |

## 12. Điều kiện cổng phê duyệt hợp đồng (Contract approval gate)

- **Trạng thái hiện tại:** Approved (Đã phê duyệt)
- **Yêu cầu trước khi triển khai:** Sinh OpenAPI/AsyncAPI/migrations và đồng bộ các tài liệu parent của Order/Payment/platform.
- **Điều kiện tiên quyết để viết mã:** Sinh mã OpenAPI 3.1 + AsyncAPI/JSON Schema + các kịch bản migration từ tài liệu này; không viết controller/DTO trước khi contracts được phê duyệt chính thức.
