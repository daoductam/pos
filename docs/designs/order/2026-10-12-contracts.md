Started at: 2026/10/12 16:33:39 +07
Finished at: 2026/10/12 16:33:39 +07
Total time: 0 minutes
---

# API & Data Contracts: Order Service

## Sources and Contract Authority

- [Order Vision](./2026-10-12-vision.md)
- [Order Requirements](./2026-10-12-requirements.md)
- [Order Architecture](./2026-10-12-architecture.md)
- [POS Platform Contracts](../pos-platform/2026-10-12-contracts.md)
- [Identity & Access Contracts](../identity-access/2026-10-12-contracts.md)
- [Catalog Contracts](../catalog/2026-10-12-contracts.md)

This document is the normative contract for Order public APIs, internal Saga callbacks, Order-owned data and Order lifecycle events. The implementation must produce OpenAPI 3.1 and AsyncAPI/JSON Schema artifacts from these definitions before controller or Angular DTO implementation.

## Contract Principles

- Public synchronous APIs use REST + OpenAPI 3.1 under `/api/v1/orders`.
- Service-to-service APIs use REST + OpenAPI 3.1 under `/internal/v1/orders`, require workload identity/mTLS and are not exposed through Angular routes.
- Events use the platform envelope and JSON Schema/AsyncAPI. Delivery is at-least-once; consumers deduplicate by `eventId`.
- Order owns order lifecycle, line state, immutable Catalog snapshots, totals snapshot and checkout attempts. Catalog, Inventory, Payment, Loyalty and Audit remain separate owners.
- IDs are UUID v7 strings. Timestamps are ISO-8601 UTC. Money is nonnegative `int64` integer VND. No floating-point money is accepted.
- Tenant context is established by Identity; Order does not introduce `X-Active-Tenant-Id`. `X-Active-Branch-Id` selects a server-validated branch for branch-scoped operations.
- Every public command carries `X-Correlation-Id`; every retryable side effect carries `Idempotency-Key`; every versioned draft mutation carries `If-Match`.
- `OrderPaid.v1` is the canonical paid-order fact for Loyalty and Reporting. `PaymentPaid.v1` is not sufficient to award points or record revenue.

## Common HTTP Contract

### Headers

| Header | Required | Meaning |
|---|---:|---|
| `Authorization` | Protected public API | `Bearer <Cognito access token>`; current Identity membership/permission is authoritative. |
| `X-Active-Branch-Id` | Branch-scoped public operation | Client-selected branch; validated against current membership. Owner must select a branch for order operations. |
| `X-Correlation-Id` | Recommended | Correlates request, Saga, audit, outbox and downstream calls; generated if absent. |
| `Idempotency-Key` | Create/checkout/reprice/cancel/retryable command | Scoped to actor + tenant + operation; same key must carry the same request hash. |
| `If-Match` | Versioned Order/line mutation | ETag from the last read, for example `"order-v7"`. |
| `Content-Type` | Body request | `application/json; charset=utf-8`. |

Internal requests additionally require workload identity/mTLS. Public clients cannot call `/internal/v1`.

### Success Envelope

```json
{
  "data": {},
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

Collection responses use cursor pagination because Order history is append-heavy:

```json
{
  "data": [],
  "meta": {
    "hasMore": true,
    "nextCursor": "eyJjcmVhdGVkQXQiOi...",
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

`limit` defaults to 20 and is capped at 100. Sort/filter fields are allowlisted and scope predicates are applied before pagination.

### Error Envelope

```json
{
  "error": {
    "code": "catalog_changed",
    "message": "The menu changed after this draft was priced",
    "details": [
      {
        "field": "lines[0]",
        "code": "price_changed",
        "message": "Refresh and confirm the new menu snapshot"
      }
    ],
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

| HTTP | Code | Meaning |
|---:|---|---|
| 401 | `unauthorized` | Missing, invalid or expired JWT. |
| 403 | `forbidden` | Role, membership, tenant or branch scope is not allowed. |
| 404 | `not_found` | Resource is absent within authorized scope; do not reveal cross-tenant existence. |
| 409 | `conflict` | Generic state conflict. |
| 409 | `catalog_changed` | Current Catalog selection/price/version differs from the draft snapshot. |
| 409 | `catalog_version_conflict` | Catalog resolver cannot provide a coherent current selection. |
| 409 | `order_version_conflict` | `If-Match` is stale. |
| 409 | `order_not_editable` | Order is `CHECKOUT_PENDING`, `PAID` or `CANCELLED`. |
| 409 | `line_not_cancellable` | Line/order state does not allow cancellation. |
| 409 | `order_no_active_lines` | Checkout has no active lines. |
| 409 | `checkout_in_progress` | A non-terminal checkout attempt already exists. |
| 409 | `compensation_incomplete` | Prior Saga compensation is not terminal/safe; retry is locked. |
| 409 | `payment_reconciliation_required` | Payment/Inventory side effect needs operator reconciliation. |
| 409 | `duplicate_request` | Idempotency key reused with a different request hash. |
| 409 | `saga_callback_stale` | Callback does not match the current attempt state; treated as safe no-op or reconciliation. |
| 422 | `validation_error` | Payload or business input invalid. |
| 422 | `invalid_service_mode` | Unsupported or malformed service mode/table context. |
| 422 | `invalid_line_selection` | Catalog item/variant/modifier selection is invalid. |
| 422 | `invalid_cash_amount` | Cash received is below the immutable grand total. |
| 422 | `checkout_total_invalid` | Total is negative, currency-mixed or inconsistent with line snapshots. |
| 429 | `rate_limited` | Gateway or service rate limit exceeded. |
| 503 | `authorization_unavailable` | Identity authorization cannot be safely resolved. |
| 503 | `dependency_unavailable` | Catalog, Inventory, Payment or Saga dependency unavailable after bounded retry. |

Error messages never expose SQL, JWT, provider secrets, raw signatures or unnecessary customer PII.

## Authorization and Scope Contract

| Permission | Role | Scope | Allowed Order operations |
|---|---|---|---|
| `order:read` | Owner, Manager, Cashier | Selected/assigned branch | Read one/list authorized orders. |
| `order:create` | Owner, Manager, Cashier | Selected/assigned branch | Create draft and add initial line(s). |
| `order:edit` | Owner, Manager, Cashier | Selected/assigned branch, `DRAFT` | Edit metadata/lines/reprice. |
| `order:checkout` | Owner, Manager, Cashier | Selected/assigned branch | Start/retry checkout. |
| `order:cancel-line` | Owner, Manager | Selected/assigned branch, `DRAFT` | Cancel active line with reason/audit. |
| `order:resolve` | Order/Saga workload | Explicit tenant/branch/order | Internal callbacks and finalization only. |

The service calls Identity for the current decision and repeats ownership checks in the service/repository. A public client cannot elevate by placing a role, tenant, branch or actor ID in the body.

## Public API Surface — P0

### Order and draft commands

| Method | Path | Auth | Request | Response | Statuses |
|---|---|---|---|---|---|
| POST | `/api/v1/orders` | `order:create` + idempotency | `CreateOrderRequest` | `OrderResponse` | 201, 403, 409, 422 |
| GET | `/api/v1/orders` | `order:read` | cursor + filters | `OrderPage` | 200, 403 |
| GET | `/api/v1/orders/{orderId}` | `order:read` | — | `OrderResponse` | 200, 403, 404 |
| PATCH | `/api/v1/orders/{orderId}` | `order:edit` | `UpdateOrderRequest` + `If-Match` | `OrderResponse` | 200, 403, 404, 409, 422 |
| POST | `/api/v1/orders/{orderId}/lines` | `order:edit` + idempotency | `AddOrderLineRequest` + `If-Match` | `OrderResponse` | 201/200, 403, 404, 409, 422 |
| PATCH | `/api/v1/orders/{orderId}/lines/{lineId}` | `order:edit` | `UpdateOrderLineRequest` + `If-Match` | `OrderResponse` | 200, 403, 404, 409, 422 |
| POST | `/api/v1/orders/{orderId}/lines/{lineId}/cancel` | `order:cancel-line` + idempotency | `CancelLineRequest` + `If-Match` | `OrderResponse` | 200, 403, 404, 409, 422 |
| POST | `/api/v1/orders/{orderId}/reprice` | `order:edit` + idempotency | `RepriceOrderRequest` + `If-Match` | `RepriceResponse` | 200, 403, 404, 409, 422 |
| POST | `/api/v1/orders/{orderId}/checkout` | `order:checkout` + idempotency | `CheckoutRequest` + `If-Match` | `CheckoutResponse` | 200/202, 403, 404, 409, 422, 503 |
| GET | `/api/v1/orders/{orderId}/checkout-attempts` | `order:read` | cursor | `CheckoutAttemptPage` | 200, 403, 404 |
| GET | `/api/v1/orders/{orderId}/checkout-attempts/{attemptId}` | `order:read` | — | `CheckoutAttemptResponse` | 200, 403, 404 |

`DELETE /orders/{id}` and hard-delete line endpoints do not exist in P0. Cancelling the final active line is the only public path to `CANCELLED`.

### List filters

```text
GET /api/v1/orders
  ?status=DRAFT|CHECKOUT_PENDING|PAID|CHECKOUT_FAILED|CANCELLED
  &serviceMode=COUNTER|TAKEAWAY|TABLE
  &from=2026-10-12T00:00:00Z
  &to=2026-10-12T23:59:59Z
  &tableReference=A03
  &limit=20
  &cursor=<opaque>
```

`branchId` is selected by validated context, not a body/query override for non-Owner roles. Owner may select a branch through the same server-validated context.

### P1/P2 compatibility endpoints

These paths are reserved and feature-gated; they are not required for P0:

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/v1/orders/{orderId}/hold` | Hold a draft for later resume. |
| POST | `/api/v1/orders/{orderId}/resume` | Resume a held draft. |
| POST | `/api/v1/orders/{orderId}/splits` | Split invoice/order lines. |
| POST | `/api/v1/orders/merge` | Merge compatible drafts before payment. |
| GET | `/api/v1/branches/{branchId}/tables` | P1 table map/occupancy. |
| POST | `/api/v1/orders/{orderId}/kitchen-tickets` | P1 durable KDS ticket submission. |
| WebSocket | `/ws/v1/kds` | P1 realtime delivery; reconnect/replay uses durable events. |
| POST | `/api/v1/qr-sessions` | P2 customer table QR session. |

## Public Request Schemas

### `CreateOrderRequest`

```json
{
  "serviceMode": "TABLE",
  "tableReference": "A03",
  "orderNote": "Ít đá",
  "customerId": null,
  "lines": [
    {
      "catalogItemId": "0192b2d0-7f1e-7abc-8d2c-123456789012",
      "variantId": "0192b2d0-7f20-7abc-8d2c-123456789012",
      "modifierOptionIds": ["0192b2d0-7f22-7abc-8d2c-123456789012"],
      "quantity": 2,
      "note": "Không hành"
    }
  ]
}
```

| Field | Type | Required | Constraint |
|---|---|---:|---|
| `serviceMode` | enum | yes | `COUNTER`, `TAKEAWAY`, `TABLE`. Immutable after create. |
| `tableReference` | string/null | TABLE only | 1–64 characters after trim; metadata only; no P0 uniqueness. |
| `orderNote` | string/null | no | Max 500 characters; sanitized. |
| `customerId` | UUID/null | no | Opaque Loyalty reference; must be same tenant if supplied. |
| `lines` | array | yes | 1–100 lines; duplicate identical line selections may be merged by the client but are not silently deduplicated by Order. |

The request is resolved by Catalog before an Order row is committed. If any line is invalid, no partial Order is created.

`OrderLineInput`:

| Field | Type | Required | Constraint |
|---|---|---:|---|
| `catalogItemId` | UUID | yes | Resolved in the current tenant. |
| `variantId` | UUID/null | yes for new clients | Null legacy input resolves only when exactly one active variant exists. |
| `modifierOptionIds` | UUID array | no | Each option at most once; Catalog validates group attachment/rules. |
| `quantity` | int32 | yes | 1–999. |
| `note` | string/null | no | Max 250 characters; sanitized and redacted from normal logs. |

### `UpdateOrderRequest`

```json
{
  "orderNote": "Giao nhanh",
  "tableReference": "A03"
}
```

All fields are optional and only apply to `DRAFT`. `serviceMode` and `branchId` cannot change. `tableReference` may be set/cleared only when `serviceMode=TABLE`.

### `AddOrderLineRequest` / `UpdateOrderLineRequest`

```json
{
  "catalogItemId": "0192b2d0-7f1e-7abc-8d2c-123456789012",
  "variantId": "0192b2d0-7f20-7abc-8d2c-123456789012",
  "modifierOptionIds": [],
  "quantity": 1,
  "note": ""
}
```

Add requires Catalog selection fields. Update may send any subset of `quantity`, `variantId`, `modifierOptionIds` and `note`; changing selection fields calls Catalog resolver. A quantity-only/note-only update reuses the current snapshot but still checks `If-Match` and recalculates totals.

### `CancelLineRequest`

```json
{
  "reason": "Khách đổi món"
}
```

`reason` is required, trimmed, 1–500 characters and sanitized. The server derives actor, role, tenant, branch, timestamp and before/after snapshots; clients cannot provide those audit fields.

### `RepriceOrderRequest` and `RepriceResponse`

```json
{
  "confirm": false,
  "expectedCatalogVersion": 18
}
```

- `confirm=false` returns a non-mutating diff of affected lines, current Catalog version and proposed snapshots.
- `confirm=true` requires the latest Order `If-Match` and applies all proposed snapshots/totals atomically. If the Catalog changes again, it returns `409 catalog_changed`.
- Reprice is allowed only in `DRAFT`; it never alters a historical checkout attempt.

### `CheckoutRequest`

```json
{
  "paymentMethod": "VNPAY_QR",
  "cashReceivedMinor": null,
  "returnUrl": "https://pos.example.test/payment-result",
  "clientReference": "counter-03-20261012-0007"
}
```

| Field | Type | Required | Constraint |
|---|---|---:|---|
| `paymentMethod` | enum | yes | `CASH`, `VNPAY_QR`. |
| `cashReceivedMinor` | int64/null | cash only | Must be >= immutable grand total. Never accepted for QR. |
| `returnUrl` | URI/null | VNPAY only | Allowlisted by Payment/Gateway policy; never used as an auth signal. |
| `clientReference` | string/null | no | Max 100 characters; display/correlation only. |

Before creating the attempt, Order re-resolves Catalog and validates current totals. The request cannot supply a price, total, actor or branch override.

## Public Response Schemas

### `OrderResponse`

```json
{
  "id": "0192b2d0-7f30-7abc-8d2c-123456789012",
  "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
  "branchId": "0192b2d0-7f23-7abc-8d2c-123456789012",
  "orderNumber": "A03-000127",
  "serviceMode": "TABLE",
  "tableReference": "A03",
  "status": "DRAFT",
  "orderNote": "Ít đá",
  "customerId": null,
  "lines": [],
  "totals": {
    "subtotalMinor": 68000,
    "vatMinor": 0,
    "discountMinor": 0,
    "grandTotalMinor": 68000,
    "currency": "VND",
    "pricingPolicyVersion": 1
  },
  "catalogVersion": 18,
  "version": 4,
  "currentCheckoutAttempt": null,
  "createdAt": "2026-10-12T09:00:00Z",
  "updatedAt": "2026-10-12T09:03:00Z"
}
```

Resource responses return `ETag: "order-v4"`. `lines` includes active and cancelled lines for authorized history; clients must use line status when calculating/displaying current totals.

### `OrderLineResponse`

```json
{
  "id": "0192b2d0-7f31-7abc-8d2c-123456789012",
  "catalogItemId": "0192b2d0-7f1e-7abc-8d2c-123456789012",
  "catalogVariantId": "0192b2d0-7f20-7abc-8d2c-123456789012",
  "itemName": "Cà phê sữa",
  "variantName": "STANDARD",
  "modifiers": [
    {
      "catalogGroupId": "0192b2d0-7f21-7abc-8d2c-123456789012",
      "catalogOptionId": "0192b2d0-7f22-7abc-8d2c-123456789012",
      "groupName": "Topping",
      "optionName": "Trân châu",
      "priceDeltaMinor": 5000
    }
  ],
  "baseUnitPriceMinor": 29000,
  "modifierUnitPriceMinor": 5000,
  "unitPriceMinor": 34000,
  "quantity": 2,
  "note": "Không hành",
  "status": "ACTIVE",
  "catalogVersion": 18,
  "version": 2,
  "cancelledAt": null,
  "cancelReason": null
}
```

### `CheckoutResponse`

```json
{
  "orderId": "0192b2d0-7f30-7abc-8d2c-123456789012",
  "attemptId": "0192b2d0-7f32-7abc-8d2c-123456789012",
  "sagaId": "0192b2d0-7f33-7abc-8d2c-123456789012",
  "orderStatus": "CHECKOUT_PENDING",
  "attemptStatus": "PAYMENT_PENDING",
  "paymentId": "0192b2d0-7f34-7abc-8d2c-123456789012",
  "reservationId": "0192b2d0-7f35-7abc-8d2c-123456789012",
  "grandTotalMinor": 68000,
  "currency": "VND",
  "cashChangeMinor": null,
  "qrPayload": "opaque-payment-payload",
  "expiresAt": "2026-10-12T09:18:00Z"
}
```

- Cash success returns HTTP `200` with `orderStatus=PAID`, `attemptStatus=COMPLETED` and `cashChangeMinor`.
- VNPAY initiation returns HTTP `202` with `orderStatus=CHECKOUT_PENDING`, `attemptStatus=PAYMENT_PENDING`, QR payload and expiry.
- Failed/compensated checkout returns HTTP `200`/`409` according to whether the command was processed or conflicted; the body includes `CHECKOUT_FAILED`, `compensationStatus` and `paymentReconciliationRequired`.

### `CheckoutAttemptResponse`

Includes `attemptId`, `orderId`, `sagaId`, `status`, `compensationStatus`, `paymentReconciliationRequired`, `paymentId`, `reservationId`, immutable totals snapshot, `failureCode`, `failureReasonSafe`, `expiresAt`, `startedAt`, `completedAt` and `correlationId`. Provider raw payload/signature is never exposed.

## Internal Saga Contracts

All endpoints require mTLS/workload identity, explicit tenant/branch context, `sagaId`, `attemptId`, `Idempotency-Key` and correlation metadata. They are idempotent by `(tenantId, orderId, attemptId, taskName)`.

### `POST /internal/v1/orders/{orderId}/checkout-attempts/{attemptId}/finalize`

Called after Payment success and Inventory commit success.

```json
{
  "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
  "branchId": "0192b2d0-7f23-7abc-8d2c-123456789012",
  "sagaId": "0192b2d0-7f33-7abc-8d2c-123456789012",
  "paymentId": "0192b2d0-7f34-7abc-8d2c-123456789012",
  "reservationId": "0192b2d0-7f35-7abc-8d2c-123456789012",
  "paymentStatus": "PAID",
  "inventoryStatus": "COMMITTED"
}
```

Response is `200` with the current terminal Order/attempt. A replay returns the same result and does not emit another `OrderPaid`.

### `POST /internal/v1/orders/{orderId}/checkout-attempts/{attemptId}/fail`

Called after non-retryable payment/reservation failure or timeout. Body includes `reasonCode`, `retryable`, `paymentStatus`, `reservationStatus`, `compensationStatus` and `failureAt`. It transitions the Order to `CHECKOUT_FAILED` only when the attempt matches; a stale callback returns `409 saga_callback_stale` or an idempotent current result.

### `POST /internal/v1/orders/{orderId}/checkout-attempts/{attemptId}/compensation`

Updates `compensationStatus` to `RETRYING`, `SAFE`, or `FAILED`, with task name, retry count, reason code and next retry time. A `FAILED` result sets the retry lock and `paymentReconciliationRequired` when payment succeeded.

### `POST /internal/v1/orders/{orderId}/checkout-attempts/{attemptId}/late-payment`

Records a verified Payment success that arrived after reservation release/terminal failure. It is idempotent, does not change Order to `PAID`, sets `paymentReconciliationRequired=true` and emits an operational reconciliation event.

### `GET /internal/v1/orders/{orderId}/checkout-context`

Returns the immutable attempt input required by Step Functions: active line snapshots, totals, tenant/branch, Catalog version, attempt expiry and current status. It never exposes secrets or provider credentials.

## Event Contracts

### Common Envelope

```json
{
  "eventId": "0192b2d0-7f40-7abc-8d2c-123456789012",
  "eventType": "OrderPaid",
  "eventVersion": 1,
  "occurredAt": "2026-10-12T09:03:00Z",
  "producer": "order-service",
  "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
  "branchId": "0192b2d0-7f23-7abc-8d2c-123456789012",
  "aggregateType": "Order",
  "aggregateId": "0192b2d0-7f30-7abc-8d2c-123456789012",
  "correlationId": "cor_01J...",
  "payload": {}
}
```

Order events always have a non-null branch because every Order belongs to a branch. Event payloads contain stable IDs, immutable snapshots needed by consumers, totals and versions; no JWT, secrets, raw payment data or unnecessary PII.

### Event Types

| Event type | Priority | Payload purpose | Consumers |
|---|---|---|---|
| `OrderCreated.v1` | P0 | New draft identity, service mode, branch, line snapshot summary and totals | Reporting, Audit, Recommendation |
| `OrderLineCancelled.v1` | P0 | Line cancellation reason/actor summary, resulting order version | Audit, Reporting |
| `OrderCancelled.v1` | P0 | Terminal cancellation after final active line | Audit, Reporting |
| `OrderCheckoutFailed.v1` | P0 | Failed attempt reason, compensation/reconciliation status | Audit, Reporting, operations |
| `OrderPaid.v1` | P0 | Canonical immutable paid-order fact with line snapshots/totals | Loyalty, Reporting, Audit |
| `OrderItemSubmitted.v1` | P1 | Durable item ticket for KDS/replay | KDS adapter, Audit |
| `OrderHeld.v1` / `OrderResumed.v1` | P1 | Draft retention/hold state | Reporting, Audit |

### `OrderPaid.v1` Payload

```json
{
  "orderId": "0192b2d0-7f30-7abc-8d2c-123456789012",
  "orderNumber": "A03-000127",
  "serviceMode": "TABLE",
  "tableReference": "A03",
  "customerId": null,
  "lines": [
    {
      "lineId": "0192b2d0-7f31-7abc-8d2c-123456789012",
      "catalogItemId": "0192b2d0-7f1e-7abc-8d2c-123456789012",
      "catalogVariantId": "0192b2d0-7f20-7abc-8d2c-123456789012",
      "itemName": "Cà phê sữa",
      "variantName": "STANDARD",
      "quantity": 2,
      "unitPriceMinor": 34000,
      "modifiers": []
    }
  ],
  "subtotalMinor": 68000,
  "vatMinor": 0,
  "discountMinor": 0,
  "grandTotalMinor": 68000,
  "currency": "VND",
  "paidAt": "2026-10-12T09:03:00Z",
  "catalogVersion": 18,
  "orderVersion": 7
}
```

Events are additive within a version. Removing/changing field meaning requires a new event version and parallel consumer migration.

## Order-Owned Data Contract

### `orders`

| Column | Type | Constraint/meaning |
|---|---|---|
| `id` | UUID | PK, UUID v7. |
| `tenant_id` | UUID | Required tenant scope. |
| `branch_id` | UUID | Required and immutable branch scope. |
| `order_number` | TEXT | Required, unique within `(tenant_id, branch_id)`. |
| `service_mode` | TEXT | `COUNTER`, `TAKEAWAY`, `TABLE`. |
| `table_reference` | TEXT | Nullable unless TABLE; max 64; not unique P0. |
| `status` | TEXT | `DRAFT`, `CHECKOUT_PENDING`, `PAID`, `CHECKOUT_FAILED`, `CANCELLED`. |
| `order_note` | TEXT | Nullable, sanitized, max 500. |
| `customer_id` | UUID | Nullable opaque Loyalty reference. |
| `subtotal_minor` | BIGINT | Nonnegative active-line total. |
| `vat_minor` | BIGINT | Nonnegative deterministic tax amount; P0 default zero. |
| `discount_minor` | BIGINT | Nonnegative; P0 default zero. |
| `grand_total_minor` | BIGINT | `subtotal + vat - discount`, nonnegative. |
| `currency` | CHAR(3) | `VND` in P0. |
| `pricing_policy_version` | INT | Version of local pricing/tax policy. |
| `catalog_version` | BIGINT | Last coherent Catalog revision used. |
| `version` | BIGINT | Optimistic concurrency version. |
| `created_at`/`updated_at` | TIMESTAMPTZ | UTC timestamps. |

### `order_lines`

| Column | Type | Constraint/meaning |
|---|---|---|
| `id` | UUID | PK, UUID v7. |
| `order_id` | UUID | Local FK to `orders`; tenant/branch ownership checked. |
| `catalog_item_id` | UUID | Opaque Catalog ID. |
| `catalog_variant_id` | UUID | Concrete resolved variant ID. |
| `item_name_snapshot` | TEXT | Required historical name. |
| `variant_name_snapshot` | TEXT | Required historical variant name. |
| `base_unit_price_minor` | BIGINT | Nonnegative resolved base price. |
| `modifier_unit_price_minor` | BIGINT | Nonnegative sum of selected deltas. |
| `unit_price_minor` | BIGINT | Base + modifier price. |
| `quantity` | INT | 1–999 while active. |
| `note` | TEXT | Nullable, sanitized, max 250. |
| `status` | TEXT | `ACTIVE` or `CANCELLED`. |
| `cancel_reason` | TEXT | Required when cancelled. |
| `cancelled_by`/`cancelled_at` | UUID/TIMESTAMPTZ | Actor and UTC time when cancelled. |
| `catalog_version` | BIGINT | Revision used for this snapshot. |
| `version` | BIGINT | Optimistic line version. |

### `order_line_modifiers`

Stores the selected modifier group/option IDs, names and `price_delta_minor` used by the line snapshot. It is local historical data, not a live Catalog relationship. Duplicate option IDs are prohibited within a line.

### `checkout_attempts`

Stores `id`, `order_id`, `tenant_id`, `branch_id`, `attempt_number`, `saga_id`, `status`, `compensation_status`, `idempotency_key`, `request_hash`, immutable totals snapshot, `payment_id`, `reservation_id`, `failure_code`, safe failure reason, `payment_reconciliation_required`, `expires_at`, retry count, timestamps and correlation ID. Only one non-terminal attempt may exist for an Order.

### `order_state_transitions`, `outbox_messages`, `idempotency_records`

- `order_state_transitions` is append-only and records important transitions, actor/workload, reason, version and correlation ID.
- `outbox_messages` stores the common event envelope/payload, event type/version, tenant/branch, aggregate, publish status, next attempt, retry count and last error.
- `idempotency_records` stores tenant, actor/workload, operation, key, canonical request hash, original response/status and expiry. Raw payment secrets or full notes are not stored.

## Idempotency and Versioning

- Create, line add, cancel, reprice and checkout commands require `Idempotency-Key`.
- Same key and same canonical request returns the original response without duplicate rows, Saga executions, audit events or `OrderPaid` events.
- Same key with a different hash returns `409 duplicate_request`.
- Checkout task keys are derived from `(tenantId, orderId, attemptId, taskName)` and are independent from the public checkout key.
- `If-Match` is required for Order/line changes and cancel/reprice. A stale value returns `409 order_version_conflict`.
- Event consumers deduplicate by `eventId`; event version is immutable after publication.
- New optional fields are backward-compatible additions. Breaking API/event changes use a new major path or event version.

## Contract Compatibility and Amendments

| Existing contract | Order decision | Required action |
|---|---|---|
| Parent `OrderLineInput.variantId` nullable | New clients send concrete variant; legacy null only resolves for one active variant. | Amend shared Order/Catalog DTO and resolver tests. |
| Parent uses `modifierIds` | Catalog contract uses modifier option IDs/groups and snapshots names/deltas. | Rename/clarify to `modifierOptionIds` in the Order contract. |
| Parent `UpdateOrderRequest` is broad | P0 separates order metadata, line mutation, cancel and reprice commands. | Update OpenAPI endpoint/schema ownership. |
| Parent CheckoutResponse is minimal | P0 returns attempt/saga/payment/reservation IDs, pending expiry, compensation and reconciliation state. | Extend response without removing existing fields. |
| Parent `OrderPaid` is canonical | Loyalty/Reporting consume only OrderPaid; PaymentPaid remains Saga/payment fact. | Add consumer contract tests and routing rules. |
| Parent Inventory APIs omit commit semantics | Successful payment requires idempotent reservation commit before Order finalization. | Add commit endpoint/result to Inventory/Saga contract. |
| Parent cancellation endpoint says Manager | Owner/Manager only, DRAFT only, reason/audit, last-line terminal cancel. | Add role/state/reason constraints and audit event schema. |
| Parent branch scope allows branch read | Owner needs explicit selected branch; Manager/Cashier must have active branch membership. | Apply same authorization context to every Order path. |

## Contract Test Matrix

1. OpenAPI validates all request/response/error envelopes, ETags, cursor pagination and status codes.
2. Owner/Manager/Cashier branch rules and Cashier cancellation denial are tested with current Identity decisions, revoked memberships and cross-branch IDs.
3. Same-tenant valid draft edits succeed; stale `If-Match` fails without partial write.
4. Catalog resolver snapshots valid selections and rejects foreign/inactive/ambiguous options; no partial Order is created.
5. `catalog_changed` blocks checkout and explicit reprice confirmation updates snapshots only once.
6. Last active line cancellation atomically transitions Order to `CANCELLED` and emits one line/order audit event.
7. Cash checkout computes integer change, finalizes once and replays idempotently.
8. VNPAY QR returns pending, reserves for 15 minutes, accepts one verified IPN and produces one `OrderPaid`.
9. Invalid, duplicate, out-of-order and late IPN paths do not duplicate payment/finalization; late IPN creates reconciliation state.
10. Inventory insufficient/payment failure/timeout releases reservation once and produces `CHECKOUT_FAILED`.
11. Compensation retry/failure locks new checkout and exposes safe operational status/DLQ evidence.
12. Payment success plus Inventory commit failure retries and never silently marks Order `PAID`.
13. New checkout retry creates a new attempt only after safe compensation; prior attempts remain immutable.
14. `OrderPaid` is emitted exactly once and replaying it does not duplicate Loyalty/Reporting side effects.
15. Tenant/branch repository predicates prevent IDOR for every single/batch/internal operation.
16. Notes, customer references, JWTs, provider signatures and secrets are absent/redacted from logs/events/responses.
17. P1 reserved endpoints do not alter P0 state semantics; KDS reconnect/replay uses durable events rather than socket state.

## Implementation Deliverables Required Before Coding

- `openapi.yaml` for public Order APIs and `/internal/v1/orders` Saga callbacks.
- `asyncapi.yaml` and JSON Schemas for `OrderCreated`, `OrderLineCancelled`, `OrderCancelled`, `OrderCheckoutFailed`, `OrderPaid` and P1 events.
- Step Functions state machine definition with task idempotency keys, timeout/retry/catch/compensation paths and callback correlation.
- Contract fixtures for Catalog `catalog_changed`, Inventory reserve/commit/release, Payment pending/paid/failed/late and reconciliation outcomes.
- Generated Angular client types; hand-written duplicate request/response DTOs are not source of truth.
- Migration SQL with comments, constraints, indexes and deterministic multi-tenant/multi-branch seed data.
- Contract/security test fixtures for cross-tenant/branch IDOR, stale versions, duplicate commands, late callbacks and redaction.

## Domain Skills Referenced

- **api-design:** REST resources, public command separation, cursor pagination, errors, authentication, idempotency and optimistic concurrency.
- **database-design:** Order snapshot schema, checkout attempts, constraints, indexes, UUID v7, integer money and migration comments.
- **log-processing:** correlation/audit/outbox fields, event metadata, retry/DLQ observability and sensitive-data redaction.
- **sequence-diagram:** architecture contains conceptual checkout/compensation flows; runtime PlantUML remains a post-implementation deliverable.

## Approval

- **Status:** Approved by user
- **Phase:** API & Data Contracts
- **Next phase after approval:** Finalization
