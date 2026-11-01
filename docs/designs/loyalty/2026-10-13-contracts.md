# Module 5 — Loyalty service: API & Data Contracts

**Ngày:** 2026-10-13  
**Trạng thái:** Draft — chờ Contract Review  
**Phạm vi:** M — Standard Service Flow  
**Module:** Khách hàng thân thiết & khuyến mãi (Loyalty service)

## 1. Contract authority và nguyên tắc

Tài liệu này là contract normative cho Loyalty public APIs, internal checkout APIs, customer portal, PostgreSQL schema và integration events. Implementation phải tạo OpenAPI 3.1, AsyncAPI/JSON Schema và migration từ các định nghĩa này trước khi viết controller/domain code.

Nguồn liên quan:

- [Loyalty Vision](./2026-10-13-vision.md)
- [Loyalty Requirements](./2026-10-13-requirements.md)
- [Loyalty Architecture](./2026-10-13-architecture.md)
- [Order Contracts](../order/2026-10-12-contracts.md)
- [Payment Contracts](../payment/2026-10-12-contracts.md)
- [POS Platform Contracts](../pos-platform/2026-10-12-contracts.md)
- [Identity & Access Contracts](../identity-access/2026-10-12-contracts.md)

Nguyên tắc:

- REST + OpenAPI 3.1 cho synchronous API; AsyncAPI/JSON Schema cho events.
- Public API dưới `/api/v1`; internal service API dưới `/internal/v1`; customer portal có auth boundary riêng dưới `/portal/v1`.
- IDs là UUID v7 string; timestamps là ISO-8601 UTC; points/money là nonnegative `int64` trừ `pointsDelta` có dấu.
- Tenant context đến từ Cognito/workload identity; body/query `tenantId` không phải bằng chứng sở hữu.
- Event delivery at-least-once; consumer dedupe theo `eventId` và source idempotency key.
- Ledger immutable; projections và usage summaries có thể rebuild.
- Không trả hoặc log phone plaintext, OTP, token, secret, raw encryption material hay cross-tenant existence.

## 2. Common HTTP contract

### 2.1 Headers

| Header | Required | Meaning |
|---|---:|---|
| `Authorization` | Protected public/portal API | Staff: Cognito access token. Portal: customer access token sau OTP. |
| `X-Correlation-Id` | Recommended | Correlates API, Saga, event, audit và trace; server tạo nếu thiếu. |
| `X-Active-Branch-Id` | Staff branch operation | Branch được chọn; server kiểm tra membership. Không dùng để đổi tenant. |
| `Idempotency-Key` | Retryable POST/state mutation | Scoped theo actor/service + tenant + operation; request hash phải ổn định khi retry. |
| `If-Match` | Config/profile update | ETag/version của resource; stale version trả conflict. |
| `Content-Type` | Body request | `application/json; charset=utf-8`. |

Internal requests thêm workload identity/mTLS hoặc signed service context. Public client không gọi `/internal/v1`.

### 2.2 Success envelope

```json
{
  "data": {},
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

Collection dùng cursor:

```json
{
  "data": [],
  "meta": {
    "pagination": {
      "hasMore": true,
      "nextCursor": "eyJvY2N1cnJlZEF0Ijoi..."
    },
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

`limit` mặc định 20, tối đa 100. Cursor là opaque, có scope/filter/sort binding và hết hạn theo policy.

### 2.3 Error envelope và codes

```json
{
  "error": {
    "code": "insufficient_points",
    "message": "The customer does not have enough available points",
    "details": [
      {
        "field": "requestedPoints",
        "code": "available_balance",
        "message": "Requested points exceed the available balance"
      }
    ],
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

| HTTP | Code | Meaning |
|---:|---|---|
| 401 | `unauthorized` | Missing, invalid or expired token. |
| 403 | `forbidden` | Role, tenant, branch or customer ownership không hợp lệ. |
| 404 | `not_found` | Resource không tồn tại trong authorized scope; không reveal cross-tenant. |
| 404 | `customer_not_found` | Staff lookup không tìm thấy normalized phone trong tenant. |
| 409 | `duplicate_request` | Idempotency key dùng lại với request fingerprint khác. |
| 409 | `customer_phone_conflict` | Phone đã thuộc hồ sơ khác trong tenant. |
| 409 | `insufficient_points` | Available points không đủ tại thời điểm hold. |
| 409 | `hold_not_active` | Hold đã committed/released/expired hoặc không thuộc checkout attempt. |
| 409 | `quote_expired` | Quote/reservation đã hết hạn hoặc không còn revalidate được. |
| 409 | `voucher_limit_reached` | Total/per-customer limit không còn capacity. |
| 409 | `voucher_reservation_conflict` | Reservation cạnh tranh không thể tạo/consume an toàn. |
| 409 | `rule_version_retired` | Promotion version không còn active khi revalidate. |
| 409 | `version_conflict` | `If-Match` không khớp phiên bản hiện tại. |
| 409 | `state_conflict` | Command không hợp lệ với state hiện tại. |
| 422 | `validation_error` | Payload hoặc business rule invalid. |
| 422 | `invalid_phone` | Phone không normalize được theo tenant policy. |
| 422 | `invalid_basket_snapshot` | Basket thiếu field/line/amount cần cho quote. |
| 422 | `invalid_rule_config` | JSONB rule không hợp lệ với `ruleType`. |
| 422 | `foreign_resource` | Customer/order/voucher/hold không thuộc trusted tenant context. |
| 429 | `rate_limited` | Request/OTP/lookup vượt rate limit. |
| 503 | `authorization_unavailable` | Không resolve được auth an toàn; fail closed. |
| 503 | `dependency_unavailable` | Broker/key/config dependency unavailable sau bounded retry. |
| 503 | `loyalty_temporarily_unavailable` | Không thể xác định số dư/policy/hold an toàn. |

SQL, stack trace, JWT, secret, OTP, plaintext phone và raw rule payload không xuất hiện trong response.

## 3. Authorization và scope

| Permission | Roles/Caller | Scope | Allowed operations |
|---|---|---|---|
| `loyalty:customer-read` | Owner, Manager, Cashier | Tenant + validated branch membership | Lookup/profile/balance/history/voucher read |
| `loyalty:customer-create` | Owner, Manager, Cashier | Tenant + validated branch membership | Create customer profile by phone |
| `loyalty:customer-write` | Owner, Manager | Tenant; branch context được audit | Update minimal profile |
| `loyalty:config-write` | Owner, Manager theo policy | Tenant-wide | Tier, promotion, voucher config |
| `loyalty:checkout` | Order/Saga workload | Explicit tenant/branch/order/attempt | Quote, hold, release |
| `loyalty:replay` | Worker/operator workload | Explicit run/tenant scope | Inbox/DLQ/rebuild jobs |
| `loyalty:portal-read` | Customer portal token | One customer + tenant | Current balance, ledger, tier, vouchers |

Rules:

1. Owner có thể chọn tenant/branch theo Identity context; Manager/Cashier phải có active branch membership.
2. Customer/Order/Voucher/hold IDs trong path phải được query cùng `tenant_id`; không dùng ID đoán được làm authorization.
3. `customerId` trong Order là opaque Loyalty reference. Loyalty là nơi duy nhất resolve phone/profile.
4. Portal token không được gọi staff endpoint hoặc truy cập customer khác.
5. Authorization unavailable hoặc tenant context mâu thuẫn thì fail closed.

## 4. Public staff API

### 4.1 Customer lookup

`POST /api/v1/customers/lookup`

Auth: `loyalty:customer-read`; cashier/manager/owner; branch scope bắt buộc với branch role.

Request:

```json
{
  "phone": "+84901234567"
}
```

Rules:

- Server normalize phone theo tenant/country policy; không nhận `phoneHash` từ client.
- Phone không xuất hiện trong path/query để giảm nguy cơ lọt access log.
- Staff lookup không tìm thấy trả `404 customer_not_found`; cross-tenant existence vẫn trả generic `404`.

Response `200`:

```json
{
  "data": {
    "customerId": "0192b2d0-8000-7abc-8d2c-123456789012",
    "maskedPhone": "+84******567",
    "displayName": "Nguyễn An",
    "status": "ACTIVE",
    "currentTier": "GOLD",
    "balance": {
      "availablePoints": 820,
      "heldPoints": 100,
      "expiredPoints": 60,
      "asOf": "2026-10-13T09:00:00Z"
    },
    "activeVoucherCount": 2
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

### 4.2 Create customer

`POST /api/v1/customers`

Auth: `loyalty:customer-create`; requires `Idempotency-Key`.

Request:

```json
{
  "phone": "+84901234567",
  "displayName": "Nguyễn An"
}
```

| Field | Type | Constraint |
|---|---|---|
| `phone` | string | Required; normalize E.164; no extension; server validates country policy. |
| `displayName` | string/null | Optional; trimmed, max 200 Unicode characters; sanitized. |

`201` tạo mới. Nếu normalized phone đã tồn tại và request fingerprint tương thích, trả hồ sơ hiện tại với `200`; fingerprint khác trả `409 customer_phone_conflict` theo policy. Không có merge tự động.

### 4.3 Read/update profile

`GET /api/v1/customers/{customerId}`  
Auth: `loyalty:customer-read`.

`PATCH /api/v1/customers/{customerId}`  
Auth: `loyalty:customer-write`; `If-Match` bắt buộc; P0 chỉ cho `displayName` và `status=ACTIVE/BLOCKED` theo quyền.

Phone không nằm trong PATCH. Mọi PII read/update ghi audit với actor, tenant, customer và reason/policy.

Response profile không trả `phone_ciphertext` hoặc `phone_lookup_hash`; chỉ trả `maskedPhone`.

### 4.4 Balance

`GET /api/v1/customers/{customerId}/loyalty-balance`

Response:

```json
{
  "data": {
    "customerId": "0192b2d0-8000-7abc-8d2c-123456789012",
    "earnedPoints": 1200,
    "redeemedPoints": 280,
    "reversedPoints": 40,
    "expiredPoints": 60,
    "heldPoints": 100,
    "availablePoints": 820,
    "currentTier": "GOLD",
    "asOf": "2026-10-13T09:00:00Z",
    "projectionVersion": 154
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

If projection is rebuilding or stale beyond operational threshold, return `503 loyalty_temporarily_unavailable` for mutation decisions; read-only portal may return freshness metadata according to portal policy.

### 4.5 Ledger history

`GET /api/v1/customers/{customerId}/ledger`

Query:

```text
?cursor=<opaque>
&limit=20
&entryType=EARNED|REDEEMED|REVERSED|EXPIRED
&from=2026-01-01T00:00:00Z
&to=2026-10-13T23:59:59Z
&sourceType=ORDER_PAID|ORDER_REFUND|POINT_HOLD|EXPIRY
```

Ledger item:

```json
{
  "entryId": "0192b2d0-8010-7abc-8d2c-123456789012",
  "entryType": "EARNED",
  "pointsDelta": 120,
  "sourceType": "ORDER_PAID",
  "sourceId": "0192b2d0-7f30-7abc-8d2c-123456789012",
  "relatedEntryId": null,
  "policyVersion": 4,
  "occurredAt": "2026-10-12T09:03:00Z"
}
```

Source IDs là opaque references; endpoint read-only, cursor bounded và tenant/customer scoped.

### 4.6 Customer vouchers

`GET /api/v1/customers/{customerId}/vouchers`

Query: `cursor`, `limit`, `status=AVAILABLE|RESERVED|CONSUMED|EXPIRED|RELEASED`, `includeExpired=false`.

Response item:

```json
{
  "voucherId": "0192b2d0-8020-7abc-8d2c-123456789012",
  "codeMasked": "WELCOME-****",
  "status": "AVAILABLE",
  "promotionId": "0192b2d0-8030-7abc-8d2c-123456789012",
  "validFrom": "2026-10-01T00:00:00Z",
  "validTo": "2026-10-31T16:59:59Z",
  "minSpendMinor": 100000,
  "currency": "VND",
  "scope": { "branches": ["branch-1"], "channels": ["POS"] }
}
```

## 5. Internal checkout API

### 5.1 Atomic quote + reservation

`POST /internal/v1/loyalty/checkout-quotes`

Auth: `loyalty:checkout` workload identity; `Idempotency-Key` required.

Request:

```json
{
  "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
  "branchId": "0192b2d0-7f23-7abc-8d2c-123456789012",
  "orderId": "0192b2d0-7f30-7abc-8d2c-123456789012",
  "checkoutAttemptId": "0192b2d0-8040-7abc-8d2c-123456789012",
  "customerId": "0192b2d0-8000-7abc-8d2c-123456789012",
  "currency": "VND",
  "serviceMode": "TABLE",
  "channel": "POS",
  "expiresAt": "2026-10-13T09:15:00Z",
  "requestedPoints": 100,
  "voucherCode": "WELCOME10",
  "basket": {
    "subtotalMinor": 120000,
    "lines": [
      {
        "lineId": "line-1",
        "catalogItemId": "0192b2d0-8050-7abc-8d2c-123456789012",
        "variantId": "0192b2d0-8060-7abc-8d2c-123456789012",
        "quantity": 2,
        "unitPriceMinor": 60000,
        "lineSubtotalMinor": 120000,
        "tags": ["cold"]
      }
    ]
  }
}
```

Rules:

- `tenantId`, `branchId`, `orderId` và `checkoutAttemptId` trong body phải khớp trusted service context.
- `expiresAt` phải khớp hoặc không vượt quá expiry của Order checkout attempt.
- Basket là snapshot để evaluate; Loyalty không fetch Catalog/Order DB.
- Điểm hold và voucher reservation được tạo cùng transaction Loyalty; nếu một side effect không tạo được, toàn command rollback.
- Không trả Order grand total; Order vẫn tính và snapshot final total.

Response `201`:

```json
{
  "data": {
    "quoteId": "0192b2d0-8070-7abc-8d2c-123456789012",
    "quoteVersion": 3,
    "expiresAt": "2026-10-13T09:15:00Z",
    "discountTotalMinor": 12000,
    "lineDiscounts": [
      { "lineId": "line-1", "discountMinor": 12000, "reason": "WELCOME10" }
    ],
    "appliedPromotions": [
      { "promotionId": "0192b2d0-8030-7abc-8d2c-123456789012", "version": 2, "discountMinor": 12000 }
    ],
    "rejectedPromotions": [],
    "promotionExplanation": ["WELCOME10 selected in conflict group default-voucher"],
    "pointRedemption": {
      "holdId": "0192b2d0-8080-7abc-8d2c-123456789012",
      "requestedPoints": 100,
      "heldPoints": 100,
      "discountMinor": 10000,
      "state": "HELD",
      "expiresAt": "2026-10-13T09:15:00Z",
      "policyVersion": 5
    },
    "voucherReservation": {
      "reservationId": "0192b2d0-8090-7abc-8d2c-123456789012",
      "voucherId": "0192b2d0-8020-7abc-8d2c-123456789012",
      "codeMasked": "WELCOME-****",
      "state": "RESERVED",
      "expiresAt": "2026-10-13T09:15:00Z"
    }
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

No customer/points is a valid path: `customerId=null`, `requestedPoints=0`, no hold; promotion-only quote remains supported.

`quoteId` là opaque correlation reference cho Order snapshot/audit; Order phải snapshot discount breakdown và rule versions trong order contract. Loyalty không cam kết tra cứu lại quote sau expiry ở P0 nếu không có promotion quote persistence feature.

### 5.2 Release point hold

`POST /internal/v1/loyalty/point-holds/{holdId}/release`

Request:

```json
{
  "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
  "checkoutAttemptId": "0192b2d0-8040-7abc-8d2c-123456789012",
  "reasonCode": "PAYMENT_FAILED",
  "reason": "Payment provider returned definitive failure"
}
```

The same terminal release retry returns the current hold. A different tenant/attempt returns `403/404` without revealing ownership.

### 5.3 Release voucher reservation

`POST /internal/v1/loyalty/voucher-reservations/{reservationId}/release`

Request fields: `tenantId`, `checkoutAttemptId`, `reasonCode`, `reason`. It is idempotent and does not decrement historical usage because reservation has not been consumed.

### 5.4 Internal read/operations

| Method | Path | Purpose |
|---|---|---|
| GET | `/internal/v1/loyalty/checkout-attempts/{checkoutAttemptId}` | Read hold/reservation status for Saga reconciliation. |
| POST | `/internal/v1/loyalty/rebuilds` | Start scoped projection rebuild; worker/operator only. |
| POST | `/internal/v1/loyalty/dlq-replays` | Replay selected event IDs through inbox path; worker/operator only. |

These endpoints never bypass domain validation or write a balance directly.

## 6. Configuration APIs P1

All config mutations require Owner/Manager policy, `If-Match` where updating an existing resource, idempotency and audit. Published versions are immutable.

| Method | Path | Purpose |
|---|---|---|
| GET/PUT | `/api/v1/loyalty/point-policies` | Earn ratio, redemption rate, max redemption and policy version. |
| GET/PUT | `/api/v1/loyalty/tier-policies` | Metric, rolling window and Silver/Gold/Diamond thresholds. |
| GET/POST | `/api/v1/promotions` | List/create promotion parent. |
| POST | `/api/v1/promotions/{promotionId}/versions` | Create immutable draft version. |
| POST | `/api/v1/promotions/{promotionId}/versions/{version}/publish` | Validate and publish version. |
| GET/POST | `/api/v1/vouchers` | List/create voucher code and limits. |
| PATCH | `/api/v1/vouchers/{voucherId}` | Update unpublished voucher metadata. |
| POST | `/api/v1/vouchers/{voucherId}/state` | Activate, deactivate or archive voucher. |

Promotion version request:

```json
{
  "ruleType": "PERCENT_DISCOUNT",
  "priority": 100,
  "stackable": false,
  "conflictGroup": "default-voucher",
  "scope": { "branches": ["branch-1"], "channels": ["POS"] },
  "validFrom": "2026-10-15T00:00:00Z",
  "validTo": "2026-10-31T16:59:59Z",
  "ruleConfig": {
    "target": "ORDER",
    "percentBps": 1000,
    "minSpendMinor": 100000,
    "maxDiscountMinor": 50000
  }
}
```

`ruleConfig` schemas:

- `PERCENT_DISCOUNT`: target, percent basis points, optional min spend/max discount.
- `BUY_X_GET_Y`: qualifying item/variant, buy quantity, free item/variant, free quantity; gifted amount is zero for points.
- `HAPPY_HOUR`: weekday/date range, local time window, branch timezone scope and nested discount action.

Invalid fields or unknown expression operators return `422 invalid_rule_config`. No arbitrary script/expression execution is allowed.

## 7. Customer portal API P2

Portal auth is not Cognito staff auth and does not expose staff endpoints.

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/portal/v1/auth/otp/request` | Public rate-limited | Request OTP; generic response regardless of customer existence. |
| POST | `/portal/v1/auth/otp/verify` | OTP challenge | Issue customer-scoped portal token. |
| POST | `/portal/v1/auth/logout` | Portal token | Revoke/close session where supported. |
| GET | `/portal/v1/me/loyalty` | `loyalty:portal-read` | Balance, tier and freshness. |
| GET | `/portal/v1/me/ledger` | `loyalty:portal-read` | Own ledger history, cursor pagination. |
| GET | `/portal/v1/me/vouchers` | `loyalty:portal-read` | Own available/used/expired vouchers. |

OTP rules:

- Request accepts phone but never returns whether customer exists.
- Rate limit by phone hash, IP/device and tenant; cooldown and max attempts are configured.
- OTP, challenge secret and attempt metadata are encrypted/minimized and retained only for configured TTL.
- Portal token carries tenant/customer subject and cannot be exchanged for Cognito staff token.

## 8. Integration event contract

### 8.1 Common envelope

```json
{
  "eventId": "0192b2d0-80a0-7abc-8d2c-123456789012",
  "eventType": "OrderPaid",
  "eventVersion": 1,
  "occurredAt": "2026-10-13T09:03:00Z",
  "producer": "order-service",
  "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
  "branchId": "0192b2d0-7f23-7abc-8d2c-123456789012",
  "aggregateType": "Order",
  "aggregateId": "0192b2d0-7f30-7abc-8d2c-123456789012",
  "correlationId": "cor_01J...",
  "causationId": "evt_01J...",
  "payload": {}
}
```

The logical event name is `eventType + ".v" + eventVersion` (for example `OrderPaid.v1`); the envelope keeps these fields separate to match the parent platform contract. Events contain opaque IDs and immutable snapshots only. No JWT, secret, plaintext phone, OTP or raw payment credentials.

### 8.2 Consumed Order events

#### `OrderPaid.v1`

Loyalty consumes only this event for accrual and reservation commit. Parent Order contract adds these fields without changing existing field meaning:

```json
{
  "orderId": "0192b2d0-7f30-7abc-8d2c-123456789012",
  "customerId": "0192b2d0-8000-7abc-8d2c-123456789012",
  "checkoutAttemptId": "0192b2d0-8040-7abc-8d2c-123456789012",
  "pointHoldId": "0192b2d0-8080-7abc-8d2c-123456789012",
  "voucherReservationId": "0192b2d0-8090-7abc-8d2c-123456789012",
  "promotionQuoteId": "0192b2d0-8070-7abc-8d2c-123456789012",
  "promotionQuoteVersion": 3,
  "loyaltyEligibleAmountMinor": 108000,
  "loyaltyPolicyVersion": 5,
  "currency": "VND",
  "paidAt": "2026-10-13T09:03:00Z",
  "orderVersion": 7,
  "lines": []
}
```

`loyaltyEligibleAmountMinor` is integer goods value after discount and before VAT, excluding fees, tip and voucher-paid portion. `customerId`, hold and voucher references are nullable. If no customer/hold/voucher exists, Loyalty only processes eligible accrual when `customerId` is present.

#### `OrderRefunded.v1`

Order is canonical producer after a full refund is finalized. Payment refund/provider facts are not consumed directly by Loyalty.

```json
{
  "orderId": "0192b2d0-7f30-7abc-8d2c-123456789012",
  "customerId": "0192b2d0-8000-7abc-8d2c-123456789012",
  "refundId": "0192b2d0-80b0-7abc-8d2c-123456789012",
  "refundType": "FULL",
  "refundStatus": "FINALIZED",
  "refundAmountMinor": 118000,
  "currency": "VND",
  "originalPaidAt": "2026-10-13T09:03:00Z",
  "originalOrderVersion": 7,
  "refundedAt": "2026-10-20T10:00:00Z"
}
```

Unique reversal key is `(tenantId, refundId)`. Loyalty computes points from its own ledger/lot/allocation lineage and does not trust a caller-provided points amount.

#### `OrderCheckoutFailed.v1` / cancellation release payload

```json
{
  "orderId": "0192b2d0-7f30-7abc-8d2c-123456789012",
  "checkoutAttemptId": "0192b2d0-8040-7abc-8d2c-123456789012",
  "pointHoldId": "0192b2d0-8080-7abc-8d2c-123456789012",
  "voucherReservationId": "0192b2d0-8090-7abc-8d2c-123456789012",
  "reasonCode": "PAYMENT_FAILED",
  "compensationStatus": "FINALIZED",
  "failedAt": "2026-10-13T09:04:00Z"
}
```

### 8.3 Loyalty-produced events

| Event | Trigger | Required payload |
|---|---|---|
| `LoyaltyPointsAccrued.v1` | `EARNED` committed | `customerId`, `orderId`, `ledgerEntryId`, `points`, `eligibleAmountMinor`, `policyVersion` |
| `LoyaltyPointsRedeemed.v1` | Point hold committed | `customerId`, `orderId`, `checkoutAttemptId`, `holdId`, `ledgerEntryId`, `points`, `discountMinor` |
| `LoyaltyPointsReversed.v1` | Full refund reversal | `customerId`, `orderId`, `refundId`, `originalEntryIds`, `reversalEntryIds`, `pointsDelta` |
| `PointHoldReleased.v1` | Fail/cancel/expiry release | `customerId`, `orderId`, `checkoutAttemptId`, `holdId`, `points`, `reasonCode` |
| `VoucherConsumed.v1` | `OrderPaid` commit | `voucherId`, `reservationId`, `customerId`, `orderId`, `usageId`, `promotionVersion` |
| `VoucherReservationReleased.v1` | Release/expiry | `voucherId`, `reservationId`, `customerId`, `orderId`, `reasonCode` |
| `MembershipTierChanged.v1` | Tier projection changes | `customerId`, `fromTier`, `toTier`, `metric`, `metricValue`, `policyVersion`, `effectiveAt` |

All Loyalty events use the common envelope, are outboxed transactionally, are at-least-once and contain no plaintext customer phone.

### 8.4 Event compatibility

- Adding optional fields is allowed within a version.
- Removing a field, changing its meaning/type or changing points semantics requires a new event version and parallel consumers.
- Consumer must ignore unknown additive fields and reject missing required fields to retry/DLQ.
- Event schema registry stores JSON Schema, compatibility mode and example fixtures.

## 9. PostgreSQL data contract

### 9.1 Common types and conventions

| Logical field | PostgreSQL type | Constraint/description |
|---|---|---|
| IDs | `UUID` | UUID v7 generated by service; primary key unless noted. |
| Tenant/customer/order references | `UUID` | Required and tenant-scoped; cross-service references are opaque. |
| Points/money amounts | `BIGINT` | Integer units; nonnegative for balances/amounts. |
| Signed ledger delta | `BIGINT` | May be positive/negative; cannot be zero. |
| Timestamps | `TIMESTAMPTZ` | UTC; never local timestamp in storage. |
| Small enum/code | `TEXT` | Validated by application plus CHECK where stable. |
| Rule/metadata | `JSONB` | Schema-validated and allowlisted; no executable code. |
| Encrypted phone | `BYTEA` | KMS envelope ciphertext; key version stored separately. |
| Lookup hash | `BYTEA` | HMAC/peppered normalized phone; never reversible. |

Every migration must add `COMMENT ON TABLE` and `COMMENT ON COLUMN` for every new table/column using the descriptions below. No hard delete for ledger, usage, audit or terminal reservation records.

### 9.2 Core table columns

#### `customers`

| Column | Type | Null | Constraint/comment |
|---|---|---:|---|
| `id` | UUID | no | PK; customer UUID v7. |
| `tenant_id` | UUID | no | Tenant owning the profile. |
| `phone_ciphertext` | BYTEA | no | Encrypted normalized phone for controlled PII access. |
| `phone_lookup_hash` | BYTEA | no | HMAC lookup key; unique with tenant. |
| `phone_key_version` | SMALLINT | no | KMS/HMAC key version for rotation. |
| `display_name` | TEXT | yes | Sanitized customer display name. |
| `status` | TEXT | no | `ACTIVE` or `BLOCKED`. |
| `current_tier` | TEXT | no | Materialized `SILVER/GOLD/DIAMOND`. |
| `created_at` | TIMESTAMPTZ | no | Profile creation time UTC. |
| `updated_at` | TIMESTAMPTZ | no | Last profile update time UTC. |
| `version` | BIGINT | no | Optimistic profile version. |

#### `point_ledger_entries`

| Column | Type | Null | Constraint/comment |
|---|---|---:|---|
| `id` | UUID | no | PK; immutable ledger entry UUID v7. |
| `tenant_id` | UUID | no | Tenant owning the entry. |
| `customer_id` | UUID | no | Customer affected by the entry. |
| `entry_type` | TEXT | no | `EARNED`, `REDEEMED`, `REVERSED`, `EXPIRED`. |
| `points_delta` | BIGINT | no | Signed immutable balance delta; CHECK non-zero. |
| `source_type` | TEXT | no | `ORDER_PAID`, `ORDER_REFUND`, `POINT_HOLD`, `EXPIRY`. |
| `source_id` | UUID | no | Source aggregate/event reference. |
| `related_entry_id` | UUID | yes | Original entry linked by reversal. |
| `policy_version` | BIGINT | yes | Earn/redeem/expiry policy used. |
| `idempotency_key` | TEXT | no | Unique source operation key. |
| `occurred_at` | TIMESTAMPTZ | no | Business occurrence time. |
| `metadata_json` | JSONB | no | Allowlisted non-sensitive metadata. |
| `created_at` | TIMESTAMPTZ | no | Insert time UTC; never updated. |

#### `point_lots` and `point_lot_allocations`

| Table | Column | Type | Null | Constraint/comment |
|---|---|---|---:|---|
| `point_lots` | `id` | UUID | no | PK; point lot UUID v7. |
| `point_lots` | `tenant_id` | UUID | no | Tenant owning the lot. |
| `point_lots` | `customer_id` | UUID | no | Lot owner. |
| `point_lots` | `source_entry_id` | UUID | no | `EARNED` entry that created the lot. |
| `point_lots` | `earned_at` | TIMESTAMPTZ | no | Earn timestamp used in FIFO tie-break. |
| `point_lots` | `expires_at` | TIMESTAMPTZ | no | Immutable expiry from policy snapshot. |
| `point_lots` | `original_points` | BIGINT | no | Initial lot points; CHECK > 0. |
| `point_lots` | `remaining_points` | BIGINT | no | Unallocated points; CHECK >= 0 and <= original. |
| `point_lots` | `status` | TEXT | no | `OPEN`, `DEPLETED`, `EXPIRED`. |
| `point_lots` | `created_at` | TIMESTAMPTZ | no | Lot creation time. |
| `point_lots` | `updated_at` | TIMESTAMPTZ | no | Last remaining/status update. |
| `point_lots` | `version` | BIGINT | no | Optimistic lot version. |
| `point_lot_allocations` | `id` | UUID | no | PK; immutable allocation UUID v7. |
| `point_lot_allocations` | `tenant_id` | UUID | no | Tenant owning allocation. |
| `point_lot_allocations` | `lot_id` | UUID | no | Allocated lot. |
| `point_lot_allocations` | `customer_id` | UUID | no | Defense-in-depth owner. |
| `point_lot_allocations` | `allocation_type` | TEXT | no | `HOLD`, `REDEEM`, `RELEASE`, `REVERSE`, `EXPIRE`. |
| `point_lot_allocations` | `source_type` | TEXT | no | Command/event source type. |
| `point_lot_allocations` | `source_id` | UUID | no | Hold/ledger/refund/job source. |
| `point_lot_allocations` | `points` | BIGINT | no | Positive allocation quantity. |
| `point_lot_allocations` | `created_at` | TIMESTAMPTZ | no | Allocation time. |

#### `point_holds` and `loyalty_balances`

| Table | Column | Type | Null | Constraint/comment |
|---|---|---|---:|---|
| `point_holds` | `id` | UUID | no | PK; hold UUID v7. |
| `point_holds` | `tenant_id` | UUID | no | Tenant of checkout. |
| `point_holds` | `customer_id` | UUID | no | Customer whose points are held. |
| `point_holds` | `order_id` | UUID | no | Opaque Order reference. |
| `point_holds` | `checkout_attempt_id` | UUID | no | Unique checkout lifecycle reference. |
| `point_holds` | `requested_points` | BIGINT | no | Client requested points; CHECK >= 0. |
| `point_holds` | `held_points` | BIGINT | no | Validated held points; CHECK >= 0. |
| `point_holds` | `state` | TEXT | no | `HELD`, `COMMITTED`, `RELEASED`, `EXPIRED`. |
| `point_holds` | `expires_at` | TIMESTAMPTZ | no | Attempt expiry received from Order. |
| `point_holds` | `idempotency_key` | TEXT | no | Unique hold command key. |
| `point_holds` | `created_at` | TIMESTAMPTZ | no | Hold creation time. |
| `point_holds` | `updated_at` | TIMESTAMPTZ | no | Last state update. |
| `point_holds` | `version` | BIGINT | no | Optimistic hold version. |
| `loyalty_balances` | `tenant_id` | UUID | no | Tenant of projection. |
| `loyalty_balances` | `customer_id` | UUID | no | Projection customer key; PK with tenant. |
| `loyalty_balances` | `earned_points` | BIGINT | no | Earned aggregate projection. |
| `loyalty_balances` | `redeemed_points` | BIGINT | no | Committed redemption aggregate. |
| `loyalty_balances` | `reversed_points` | BIGINT | no | Reversal aggregate/delta projection. |
| `loyalty_balances` | `expired_points` | BIGINT | no | Expiry aggregate. |
| `loyalty_balances` | `held_points` | BIGINT | no | Active hold aggregate. |
| `loyalty_balances` | `available_points` | BIGINT | no | Current safe-to-hold balance; CHECK >= 0. |
| `loyalty_balances` | `projection_version` | BIGINT | no | Last materialized ledger/allocation sequence. |
| `loyalty_balances` | `calculated_at` | TIMESTAMPTZ | no | Projection freshness timestamp. |

### 9.3 Tier, promotion và voucher schema

| Table | Required columns and comments |
|---|---|
| `loyalty_point_policies` | `id UUID` policy key; `tenant_id UUID` owner; `version BIGINT` immutable policy version; `earn_amount_minor_per_unit BIGINT` eligible amount required for one earn unit; `earn_points_per_unit BIGINT` points awarded per earn unit; `redeem_points_per_unit BIGINT` points required for one redeem unit; `redeem_amount_minor_per_unit BIGINT` discount value per redeem unit; `max_redeem_percent_bps BIGINT` maximum redeemable share in basis points; `default_expiry_days INT` default lot lifetime; `status TEXT` draft/published/retired; `effective_from/effective_to TIMESTAMPTZ` validity; `created_by UUID` publishing actor; `created_at TIMESTAMPTZ` creation time. |
| `membership_tier_configs` | `id UUID` config key; `tenant_id UUID` owner; `metric TEXT` selected metric; `tier_code TEXT` Silver/Gold/Diamond; `threshold_value BIGINT` threshold; `rolling_window_days INT` rolling window; `version BIGINT` immutable policy version; `status TEXT` draft/published/retired; `effective_from/ effective_to TIMESTAMPTZ` validity; `created_by UUID` publishing actor; `created_at TIMESTAMPTZ` creation time. |
| `membership_tier_history` | `id UUID` transition key; `tenant_id UUID` owner; `customer_id UUID` customer; `from_tier/to_tier TEXT` transition; `metric TEXT` metric used; `metric_value BIGINT` measured value; `policy_version BIGINT` config; `effective_at TIMESTAMPTZ` effective time; `source_run_id UUID` recalculation source; `created_at TIMESTAMPTZ` write time. |
| `promotions` | `id UUID` parent key; `tenant_id UUID` owner; `promotion_key TEXT` stable business key; `status TEXT` lifecycle; `created_at/updated_at TIMESTAMPTZ` audit timestamps. |
| `promotion_versions` | `id UUID` immutable version key; `tenant_id UUID` owner; `promotion_id UUID` parent; `version BIGINT` version number; `rule_type TEXT` allowlisted rule; `priority INT` selection priority; `stackable BOOLEAN` stacking permission; `conflict_group TEXT` conflict group; `scope_json JSONB` branch/channel scope; `rule_config_json JSONB` validated conditions/actions; `valid_from/valid_to TIMESTAMPTZ` validity; `status TEXT` lifecycle; `published_by UUID` actor; `published_at TIMESTAMPTZ` publish time; `created_at TIMESTAMPTZ` creation time. |
| `vouchers` | `id UUID` voucher key; `tenant_id UUID` owner; `code_normalized TEXT` uppercase code; `promotion_version_id UUID` applied rule; `status TEXT` lifecycle; `total_limit BIGINT` total usage capacity; `per_customer_limit BIGINT` customer capacity; `valid_from/valid_to TIMESTAMPTZ` validity; `branch_scope_json/channel_scope_json JSONB` scope; `min_spend_minor BIGINT` minimum eligible spend; `created_at/updated_at TIMESTAMPTZ` audit timestamps. |
| `voucher_reservations` | `id UUID` reservation key; `tenant_id UUID` owner; `voucher_id/customer_id/order_id/checkout_attempt_id UUID` ownership references; `state TEXT` reservation lifecycle; `expires_at TIMESTAMPTZ` attempt expiry; `idempotency_key TEXT` reserve key; `reserved_at/committed_at/released_at TIMESTAMPTZ` lifecycle timestamps. |
| `voucher_usages` | `id UUID` usage key; `tenant_id UUID` owner; `voucher_id/customer_id/order_id/checkout_attempt_id UUID` usage references; `usage_count BIGINT` consumed quantity; `consumed_at TIMESTAMPTZ` consume time. |

### 9.4 Integration/audit schema

| Table | Required columns and comments |
|---|---|
| `inbox_events` | `id UUID` row key; `tenant_id UUID` event tenant; `event_id TEXT` broker ID; `event_type TEXT` schema type; `aggregate_id UUID` source aggregate; `received_at/processed_at TIMESTAMPTZ` lifecycle times; `processing_state TEXT` processing state; `attempt_count INT` retry count; `last_error TEXT` sanitized failure. |
| `outbox_events` | `id UUID` message key; `tenant_id UUID` event tenant; `event_type/aggregate_type TEXT` routing identity; `aggregate_id UUID` aggregate; `payload_json JSONB` event envelope; `occurred_at TIMESTAMPTZ` business time; `publish_state TEXT` publish state; `attempt_count INT` retries; `next_attempt_at/published_at/created_at TIMESTAMPTZ` delivery timestamps. |
| `audit_events` | `id UUID` audit key; `tenant_id UUID` owner; `actor_type/actor_id TEXT` actor identity; `action/entity_type/entity_id TEXT` auditable operation/target; `reason TEXT` sanitized reason; `metadata_json JSONB` allowlisted fields; `occurred_at TIMESTAMPTZ` event time. |
| `worker_runs` | `id UUID` run key; `tenant_id UUID` optional scope; `worker_type TEXT` worker; `lease_owner TEXT` instance; `lease_expires_at TIMESTAMPTZ` lease deadline; `started_at/completed_at TIMESTAMPTZ` lifecycle; `state TEXT` run state; `processed_count/failed_count BIGINT` counters; `last_error TEXT` sanitized failure. |

### 9.5 Constraints và indexes

Required names/semantics:

- `uq_customers_tenant_phone_hash`: unique `(tenant_id, phone_lookup_hash)`.
- `uq_ledger_accrual_source`: unique `(tenant_id, source_type, source_id, entry_type)` for source effects; refund reversal additionally unique `(tenant_id, source_type, source_id, related_entry_id)` where applicable.
- `uq_inbox_event`: unique `(tenant_id, event_id)`.
- `uq_point_hold_checkout_attempt`: unique `(tenant_id, checkout_attempt_id)`.
- `uq_voucher_tenant_code`: unique `(tenant_id, code_normalized)`.
- `uq_voucher_reservation_attempt`: unique `(tenant_id, voucher_id, checkout_attempt_id, customer_id)`.
- `uq_point_policy_version`: unique `(tenant_id, version)`.
- `uq_tier_config_version`: unique `(tenant_id, version, tier_code)`.
- `uq_promotion_version`: unique `(tenant_id, promotion_id, version)`.
- `uq_voucher_usage_attempt`: unique `(tenant_id, voucher_id, checkout_attempt_id)` for one consume effect per checkout.
- `idx_point_lots_fifo`: `(tenant_id, customer_id, expires_at, earned_at, id)` for remaining lots.
- `idx_ledger_customer_time`: `(tenant_id, customer_id, occurred_at DESC, id)`.
- `idx_active_reservations_expiry`: `(tenant_id, state, expires_at)`.
- `idx_outbox_pending`: `(publish_state, next_attempt_at, created_at)`.
- CHECK: `original_points > 0`, `remaining_points >= 0`, `remaining_points <= original_points`, `points_delta <> 0`, `available_points >= 0`, valid enum/state values.
- CHECK: point policy ratios/limits are positive or zero as defined, `max_redeem_percent_bps` is between 0 and 10000, expiry days are positive, and voucher limits/minimum spend are nonnegative.
- Foreign keys inside Loyalty DB use `ON DELETE RESTRICT` for ledger/usage/audit lineage. Customer archival is status-based, not hard delete.
- No P0 partition. Re-evaluate range partition by `occurred_at` at approximately 10M+ ledger/history rows with retention evidence.

## 10. Compatibility, idempotency và testing contract

- Same idempotency key + canonical request hash returns stored response and no duplicate row/event.
- Same key + different request hash returns `409 duplicate_request`.
- Event consumers ignore unknown additive fields, reject missing required fields and route invalid schema to retry/DLQ.
- OrderPaid replay cannot create second accrual, redemption commit, voucher consume or tier transition.
- Full refund replay by same `refundId` cannot create second reversal.
- Concurrent hold/usage requests must prove no over-allocation/no limit breach under database concurrency tests.
- API contract tests cover tenant/branch IDOR, masked PII, invalid phone, quote expiry, rule conflict ties, FIFO expiry, duplicate events and portal anti-enumeration.
- Migration tests verify every new column/table has database comments, constraints/indexes exist and restore/replay preserves lineage.

## 11. Parent contract amendments required

The following changes must be applied to the parent Order contract before implementation:

1. Add the Loyalty fields to `OrderPaid.v1` described in §8.2 without changing existing field meaning.
2. Define `OrderRefunded.v1` as an Order-produced finalized full-refund business event.
3. Carry `pointHoldId` and `voucherReservationId` through checkout/paid/failure compensation context.
4. Define `OrderCheckoutFailed.v1`/cancel/expiry delivery guarantees and release correlation.
5. Add AsyncAPI schemas and duplicate/replay consumer fixtures for Loyalty.

## 12. Deferred/P1 contracts

- Partial refund/line allocation and proportional points reversal.
- Manual point adjustment with manager approval/ticket.
- Customer merge workflow.
- Portal mutation/redeem.
- Production retention/SLA values and measured load results.

---

Started at: 2026/10/13 00:00:00  
Finished at: 2026/10/13 00:00:00  
Total time: 0 minutes
