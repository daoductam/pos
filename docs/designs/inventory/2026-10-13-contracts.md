Started at: 2026-10-13 00:00:00
Finished at: 2026-10-13 00:00:00

# Module 6 — Inventory service: API & Data Contracts

**Ngày:** 2026-10-13  
**Trạng thái:** Approved with warnings  
**Vision:** [Inventory Vision](./2026-10-13-vision.md)  
**Requirements:** [Inventory Requirements](./2026-10-13-requirements.md)  
**Architecture:** [Inventory Architecture](./2026-10-13-architecture.md)  
**Phạm vi:** M — Standard Service Flow

Tài liệu này là contract normative cho Inventory public/internal REST API, event JSON Schema và PostgreSQL data contract. Implementation phải sinh/validate OpenAPI 3.1, AsyncAPI/JSON Schema và Flyway migrations từ các định nghĩa này trước khi viết controller/domain code.

## 1. Contract principles

- Public APIs dùng REST + OpenAPI 3.1 dưới `/api/v1/inventory`.
- Service-to-service APIs dùng REST + OpenAPI 3.1 dưới `/internal/v1/inventory`; bắt buộc workload identity/mTLS và không expose qua Angular route.
- Events dùng platform envelope + JSON Schema/AsyncAPI, delivery at-least-once và consumer dedupe theo `eventId`.
- ID nội bộ dùng UUID v7 dạng string; timestamp dùng ISO-8601 UTC; quantity dùng decimal scale 3.
- Tenant context lấy từ Identity decision/trusted workload context, không nhận `tenantId` để quyết định ownership từ body.
- `X-Active-Branch-Id` chỉ là branch selector đã được server validate; path `branchId` phải match selector khi cả hai xuất hiện.
- Mọi state-changing request retryable có `Idempotency-Key`; mọi optimistic update có `If-Match`.
- Không hard delete ingredient/recipe/reservation/ledger/count/alert đã có historical reference.
- OpenAPI không cho arbitrary filter/sort field; mỗi collection có allowlist riêng.

## 2. Common HTTP contract

### 2.1 Headers

| Header | Required | Meaning |
|---|---:|---|
| `Authorization` | Public protected API | `Bearer <Cognito access token>`; Identity cung cấp role/tenant/branch decision. |
| `X-Correlation-Id` | Recommended | Correlation value; service generate nếu thiếu và trả lại trong meta. |
| `X-Active-Branch-Id` | Branch-scoped public API | Branch đang thao tác; server kiểm tra membership. |
| `Idempotency-Key` | Retryable mutation | Khóa retry trong scope actor/workload + tenant + operation. |
| `If-Match` | Versioned update/approval | ETag hoặc version đọc từ response trước đó. |
| `Content-Type` | Request có body | `application/json; charset=utf-8`. |
| `X-Workload-Identity`/mTLS | Internal API | Workload identity của Step Functions/Saga hoặc scheduler. |

Public client không được gọi `/internal/v1`. Tenant ID không được lấy từ body/query để grant access.

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

Collection offset response:

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

Collection cursor response:

```json
{
  "data": [],
  "meta": {
    "hasMore": true,
    "nextCursor": "eyJvY2N1cnJlZEF0Ijoi...",
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

### 2.3 Error envelope

```json
{
  "error": {
    "code": "insufficient_stock",
    "message": "Required ingredients are not available",
    "details": [
      {
        "field": "shortages[0].ingredientId",
        "code": "available_below_required",
        "message": "The requested quantity cannot be reserved"
      }
    ],
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

Error messages are safe for clients. SQL details, secrets, JWTs, payment provider raw payloads and unnecessary PII are never returned or logged.

| HTTP | Code | Meaning | Retry |
|---:|---|---|---|
| 400 | `malformed_request` | Invalid JSON or unsupported media type | No |
| 401 | `unauthorized` | Missing/invalid/expired auth | No |
| 403 | `forbidden` | Role, tenant or branch scope denied | No |
| 404 | `not_found` | Resource absent within authorized scope | No |
| 409 | `duplicate_request` | Same idempotency key has another request hash | No |
| 409 | `recipe_version_conflict` | Stale recipe update/activation | Refresh |
| 409 | `active_recipe_exists` | Active recipe invariant conflict | Refresh/retry |
| 409 | `insufficient_stock` | Required available quantity is not enough | User/Saga decision |
| 409 | `reservation_request_conflict` | Same checkout attempt has different payload | No |
| 409 | `reservation_state_conflict` | Command invalid for current terminal state | No |
| 409 | `reservation_expired` | Commit attempted after expiry | Reconciliation |
| 409 | `payment_context_mismatch` | Commit payment context differs from checkout context | No |
| 409 | `count_session_version_conflict` | Approval uses stale snapshot/version | Refresh/recount |
| 409 | `reconciliation_required` | Automatic Saga completion is unsafe | Operator/Saga |
| 409 | `archived_resource` | Archived resource cannot mutate | No |
| 422 | `validation_error` | Quantity/unit/field/business validation failure | No |
| 422 | `invalid_recipe_line` | Invalid ingredient/modifier scope or duplicate line | No |
| 422 | `invalid_branch_scope` | Path/body branch mismatch | No |
| 422 | `forecast_insufficient_history` | No usable consumption history | No |
| 429 | `rate_limited` | Gateway/service rate limit | Bounded retry |
| 503 | `lock_timeout` | Lock/statement timeout after bounded retries | Saga/client retry |
| 503 | `service_unavailable` | DB/broker dependency unavailable | Bounded retry |

## 3. Authorization contract

| Permission | Default role | Scope |
|---|---|---|
| `inventory:read` | Owner, Manager, Cashier, Kitchen | Tenant/branch according to membership; sensitive stock detail may be limited to Manager/Owner. |
| `inventory:recipe:manage` | Owner, Manager | Tenant-wide shared recipe. |
| `inventory:stock:write` | Owner, Manager | Authorized branch. |
| `inventory:count:input` | Assigned employee | Authorized count session/branch. |
| `inventory:count:approve` | Owner, Manager | Authorized branch. |
| `inventory:alert:manage` | Owner, Manager | Authorized branch. |
| `inventory:forecast:read` | Owner, Manager | Authorized branch. |
| `inventory:forecast:configure` | Owner, Manager | Owner tenant-wide; Manager branch/ingredient override. |

Identity supplies the decision; Inventory rechecks tenant/resource/branch ownership at service and repository layers. Saga/scheduler operations use workload identity and are not granted through end-user roles.

## 4. Public API surface

All public mutation endpoints use the common envelope, `Idempotency-Key` where marked, and `If-Match` for versioned updates.

### 4.1 Ingredients

| Method | Path | Auth | Request | Response | Status |
|---|---|---|---|---|---|
| GET | `/api/v1/inventory/ingredients` | `inventory:read` | offset filters | `IngredientPage` | 200, 401, 403 |
| POST | `/api/v1/inventory/ingredients` | `inventory:recipe:manage` + idempotency | `CreateIngredientRequest` | `IngredientResponse` | 201, 403, 409, 422 |
| GET | `/api/v1/inventory/ingredients/{ingredientId}` | `inventory:read` | — | `IngredientResponse` | 200, 403, 404 |
| PATCH | `/api/v1/inventory/ingredients/{ingredientId}` | `inventory:recipe:manage` + `If-Match` | `UpdateIngredientRequest` | `IngredientResponse` | 200, 403, 404, 409, 422 |
| POST | `/api/v1/inventory/ingredients/{ingredientId}/state` | `inventory:recipe:manage` + idempotency + `If-Match` | `ChangeIngredientStateRequest` | `IngredientResponse` | 200, 403, 404, 409 |

### 4.2 Recipe versions

| Method | Path | Auth | Request | Response | Status |
|---|---|---|---|---|---|
| GET | `/api/v1/inventory/recipes` | `inventory:read` | offset filters | `RecipePage` | 200, 403 |
| POST | `/api/v1/inventory/recipes` | `inventory:recipe:manage` + idempotency | `CreateRecipeRequest` | `RecipeResponse` | 201, 403, 409, 422 |
| GET | `/api/v1/inventory/recipes/{recipeVersionId}` | `inventory:read` | — | `RecipeResponse` | 200, 403, 404 |
| PATCH | `/api/v1/inventory/recipes/{recipeVersionId}` | `inventory:recipe:manage` + `If-Match` | `UpdateRecipeRequest` | `RecipeResponse` | 200, 403, 404, 409, 422 |
| POST | `/api/v1/inventory/recipes/{recipeVersionId}/validate` | `inventory:recipe:manage` + `If-Match` | `{}` | `RecipeValidationResponse` | 200, 403, 404, 409 |
| POST | `/api/v1/inventory/recipes/{recipeVersionId}/activate` | `inventory:recipe:manage` + idempotency + `If-Match` | `{}` | `RecipeResponse` | 200, 403, 404, 409, 422 |
| POST | `/api/v1/inventory/recipes/{recipeVersionId}/archive` | `inventory:recipe:manage` + idempotency + `If-Match` | `{}` | `RecipeResponse` | 200, 403, 404, 409 |

`PATCH` is allowed only for `DRAFT`. Active content changes require a new draft version. Activation atomically replaces the active version for the same tenant/item/variant.

### 4.3 Stock and ledger query

| Method | Path | Auth | Request | Response | Status |
|---|---|---|---|---|---|
| GET | `/api/v1/inventory/branches/{branchId}/balances` | `inventory:read` | offset filters | `BalancePage` | 200, 403, 404 |
| GET | `/api/v1/inventory/branches/{branchId}/ledger` | `inventory:read` | cursor + filters | `StockLedgerCursorPage` | 200, 403, 404 |
| GET | `/api/v1/inventory/branches/{branchId}/reservations` | `inventory:read` | cursor + filters | `ReservationCursorPage` | 200, 403, 404 |
| POST | `/api/v1/inventory/branches/{branchId}/opening-balances` | `inventory:stock:write` + idempotency | `CreateOpeningBalanceRequest` | `StockLedgerResponse` | 201, 403, 409, 422 |

Ledger cursor is stable on `(occurredAt, id)` and cannot be client-supplied SQL/order syntax.

### 4.4 Receipts and count sessions

| Method | Path | Auth | Request | Response | Status |
|---|---|---|---|---|---|
| GET | `/api/v1/inventory/branches/{branchId}/receipts` | `inventory:read` | offset filters | `ReceiptPage` | 200, 403, 404 |
| POST | `/api/v1/inventory/branches/{branchId}/receipts` | `inventory:stock:write` + idempotency | `CreateReceiptRequest` | `ReceiptResponse` | 201, 403, 409, 422 |
| GET | `/api/v1/inventory/branches/{branchId}/count-sessions` | `inventory:read` | offset filters | `CountSessionPage` | 200, 403, 404 |
| POST | `/api/v1/inventory/branches/{branchId}/count-sessions` | `inventory:count:input` + idempotency | `CreateCountSessionRequest` | `CountSessionResponse` | 201, 403, 409, 422 |
| POST | `/api/v1/inventory/count-sessions/{countSessionId}/lines` | `inventory:count:input` + idempotency | `RecordCountLineRequest` | `CountSessionResponse` | 200, 403, 404, 409, 422 |
| POST | `/api/v1/inventory/count-sessions/{countSessionId}/approve` | `inventory:count:approve` + idempotency + `If-Match` | `ApproveCountSessionRequest` | `CountSessionResponse` | 200, 403, 404, 409, 422 |
| POST | `/api/v1/inventory/count-sessions/{countSessionId}/cancel` | `inventory:count:approve` + idempotency + `If-Match` | `CancelCountSessionRequest` | `CountSessionResponse` | 200, 403, 404, 409 |

### 4.5 Alerts and forecast

| Method | Path | Auth | Request | Response | Status |
|---|---|---|---|---|---|
| GET | `/api/v1/inventory/branches/{branchId}/low-stock-alerts` | `inventory:read` | offset filters | `LowStockAlertPage` | 200, 403, 404 |
| POST | `/api/v1/inventory/low-stock-alerts/{alertId}/acknowledge` | `inventory:alert:manage` + idempotency | `AcknowledgeAlertRequest` | `LowStockAlertResponse` | 200, 403, 404, 409 |
| POST | `/api/v1/inventory/low-stock-alerts/{alertId}/resolve` | `inventory:alert:manage` + idempotency | `ResolveAlertRequest` | `LowStockAlertResponse` | 200, 403, 404, 409 |
| GET | `/api/v1/inventory/branches/{branchId}/forecasts` | `inventory:forecast:read` | offset/cursor filters | `ForecastPage` | 200, 403, 404 |
| PUT | `/api/v1/inventory/branches/{branchId}/forecast-policies/{ingredientId}` | `inventory:forecast:configure` + idempotency + `If-Match` | `UpdateForecastPolicyRequest` | `ForecastPolicyResponse` | 200, 403, 404, 409, 422 |

## 5. Internal Saga/API surface

| Method | Path | Auth | Request | Response | Status |
|---|---|---|---|---|---|
| POST | `/internal/v1/inventory/reservations` | Saga workload | `ReserveInventoryRequest` | `ReservationResponse` | 201, 409, 422, 503 |
| POST | `/internal/v1/inventory/reservations/{reservationId}/commit` | Saga workload | `CommitReservationRequest` | `ReservationResponse` | 200, 404, 409, 422, 503 |
| POST | `/internal/v1/inventory/reservations/{reservationId}/release` | Saga workload | `ReleaseReservationRequest` | `ReservationResponse` | 200, 404, 409, 503 |
| POST | `/internal/v1/inventory/availability` | Order/Saga workload | `AvailabilityRequest` body | `AvailabilityResponse` | 200, 403, 422, 503 |

`PaymentPaid.v1` is consumed by Saga. Inventory never exposes an event endpoint that automatically commits a reservation from that fact.

## 6. Request schemas

### 6.1 Ingredient

```json
{
  "code": "MILK-WHOLE",
  "name": "Whole milk",
  "baseUnit": "ml",
  "purchaseConversions": [
    { "purchaseUnit": "carton", "baseQuantity": 1000 }
  ]
}
```

Rules: `code` normalized/unique in tenant; `baseUnit ∈ {g, ml, unit}`; `baseQuantity > 0`; inactive ingredient cannot be used by a newly activated recipe.

### 6.2 Recipe

```json
{
  "catalogItemId": "01J...",
  "variantId": "01J...",
  "lines": [
    {
      "ingredientId": "01J...",
      "modifierOptionId": null,
      "quantityPerSale": 180.000,
      "wasteFactor": 1.050,
      "baseUnit": "ml"
    }
  ]
}
```

Every line requires `ingredientId`. `modifierOptionId` is nullable scope metadata. `quantityPerSale > 0`, `wasteFactor >= 1.000`; duplicate `(ingredientId, modifierOptionId)` lines are rejected unless a future version explicitly defines aggregation.

### 6.3 Reserve

```json
{
  "sagaId": "01J...",
  "orderId": "01J...",
  "checkoutAttemptId": "01J...",
  "branchId": "01J...",
  "expiresAt": "2026-10-13T10:15:00Z",
  "lines": [
    {
      "lineId": "01J...",
      "catalogItemId": "01J...",
      "variantId": "01J...",
      "modifierOptionIds": ["01J..."],
      "quantity": 2
    }
  ]
}
```

`tenantId` comes from trusted workload context. `Idempotency-Key` is required in the header. Quantity is integer `1..999` in P0. Inventory resolves active recipes and stores the input/recipe/demand snapshot.

### 6.4 Commit

```json
{
  "checkoutAttemptId": "01J...",
  "orderId": "01J...",
  "paymentId": "01J...",
  "paymentAttemptId": "01J...",
  "paymentStatus": "SUCCEEDED",
  "paidAmountMinor": 200000,
  "currency": "VND"
}
```

`reservationId` is in the path. All IDs, amount and currency must match the stored checkout context. `paymentStatus` accepts only `SUCCEEDED`.

### 6.5 Release

```json
{
  "checkoutAttemptId": "01J...",
  "orderId": "01J...",
  "reasonCode": "PAYMENT_FAILED",
  "reason": "Provider returned definitive failure"
}
```

Allowed reason codes: `PAYMENT_FAILED`, `CHECKOUT_TIMEOUT`, `ORDER_CANCELLED`, `SAGA_COMPENSATION`, `OPERATOR_RECONCILIATION`.

### 6.6 Availability

```json
{
  "branchId": "01J...",
  "selections": [
    {
      "lineId": "01J...",
      "catalogItemId": "01J...",
      "variantId": "01J...",
      "modifierOptionIds": [],
      "quantity": 1
    }
  ]
}
```

Availability is advisory and may be stale. It returns `canFulfill`, `shortages[]`, `recipeVersion`, `calculatedAt` and per-ingredient availability. Reserve remains authoritative.

## 7. Response schemas

### 7.1 Reservation response

```json
{
  "reservationId": "01J...",
  "status": "RESERVED",
  "orderId": "01J...",
  "checkoutAttemptId": "01J...",
  "branchId": "01J...",
  "expiresAt": "2026-10-13T10:15:00Z",
  "reservationVersion": 1,
  "reservedIngredients": [
    { "ingredientId": "01J...", "quantity": 360.000, "baseUnit": "ml" }
  ],
  "shortages": [],
  "recipeSnapshots": [
    { "catalogItemId": "01J...", "variantId": "01J...", "recipeVersion": 4 }
  ]
}
```

Terminal statuses: `COMMITTED`, `RELEASED`, `EXPIRED`, `RECONCILIATION_REQUIRED`.

### 7.2 Balance response

```json
{
  "ingredientId": "01J...",
  "branchId": "01J...",
  "baseUnit": "ml",
  "onHand": 12000.000,
  "reserved": 1800.000,
  "available": 10200.000,
  "version": 17,
  "updatedAt": "2026-10-13T10:12:00Z"
}
```

### 7.3 Reservation history response

`ReservationCursorPage` uses the common cursor envelope. Each item contains the
same immutable reservation identifiers and lifecycle fields as
`ReservationResponse`: `reservationId`, `orderId`, `checkoutAttemptId`,
`branchId`, `status`, `expiresAt`, `reservationVersion`, `createdAt`,
`updatedAt` and `terminalAt`, plus aggregated `reservedIngredients` when the
caller has the authorized stock-detail scope.

### 7.4 Forecast response

```json
{
  "branchId": "01J...",
  "ingredientId": "01J...",
  "forecastDate": "2026-10-14",
  "recommendedQuantity": 2400.000,
  "baseUnit": "ml",
  "quality": "HIGH",
  "sampleCount": 35,
  "inputWindow": { "from": "2026-09-16", "to": "2026-10-13" },
  "safetyStock": 500.000,
  "safetyStockSource": "TENANT_DEFAULT",
  "modelVersion": "moving-average-weekday-v1",
  "calculatedAt": "2026-10-13T10:00:00Z",
  "freshnessSeconds": 120
}
```

Quality `INSUFFICIENT_HISTORY` returns no recommended quantity and a typed `forecast_insufficient_history` status in the result metadata.

## 8. Pagination, filtering and caching

- Offset pagination: ingredient, recipe, balance, receipt, count session, alert and forecast admin collections.
- Cursor pagination on `(occurredAt, id)`: stock ledger and branch reservation history.
- Default page size 20, maximum 100.
- Allowlisted filters: tenant/branch scope, status, ingredient, recipe item/variant, source type, date range and alert state.
- No public caching for balances, reservation or ledger mutation results.
- Advisory availability may use a short ETag/cache only if response freshness is explicit; it never authorizes reserve.

## 9. Event contract

### 9.1 Envelope

```json
{
  "eventId": "01J...",
  "eventType": "InventoryReservationCommitted",
  "eventVersion": 1,
  "occurredAt": "2026-10-13T10:15:05Z",
  "tenantId": "01J...",
  "branchId": "01J...",
  "aggregateType": "InventoryReservation",
  "aggregateId": "01J...",
  "aggregateVersion": 2,
  "correlationId": "01J...",
  "causationId": "01J...",
  "producer": "inventory-service",
  "payload": {}
}
```

`branchId` is null for tenant-wide ingredient/recipe events and required for stock/reservation/alert/count events. Event type/version is immutable after publish. Unknown versions go to quarantine/DLQ.

### 9.2 Payload requirements

| Event | Required payload | Consumers |
|---|---|---|
| `InventoryReserved.v1` | `reservationId`, `orderId`, `checkoutAttemptId`, `sagaId`, `branchId`, `expiresAt`, `lines[]`, `recipeSnapshots[]` | Saga, Order, Audit |
| `InventoryReservationCommitted.v1` | `reservationId`, `orderId`, `checkoutAttemptId`, `sagaId`, `branchId`, `paymentId`, `lines[]`, `committedAt`, `ledgerEntryIds[]` | Saga, Order, Reporting, Audit |
| `InventoryReservationReleased.v1` | `reservationId`, `orderId`, `checkoutAttemptId`, `sagaId`, `branchId`, `reasonCode`, `releasedAt` | Saga, Order, Audit |
| `InventoryReservationExpired.v1` | `reservationId`, `orderId`, `checkoutAttemptId`, `sagaId`, `branchId`, `expiredAt`, `reasonCode` | Saga, Order, Audit |
| `InventoryStockChanged.v1` | `ledgerEntryId`, `branchId`, `ingredientId`, `movementType`, `quantityDelta`, `onHand`, `reserved`, `available`, `sourceType`, `sourceId` | Reporting, Audit |
| `InventoryLowStockDetected.v1` | `alertId`, `branchId`, `ingredientId`, `available`, `threshold`, `detectedAt`, `alertState` | Reporting, Notification, Audit |
| `InventoryReconciliationRequired.v1` | `reconciliationCaseId`, `reservationId`, `orderId`, `checkoutAttemptId`, `sagaId`, `branchId`, `paymentId`, `reasonCode`, `detectedAt` | Saga, Payment, Order, Audit |

Every event is written to outbox with the local state transaction and published at-least-once. Consumers acknowledge only after their local transaction commits.

## 10. PostgreSQL data contract

### 10.1 Common conventions

- PostgreSQL/Aurora PostgreSQL, UUID v7, `TIMESTAMPTZ` UTC, `JSONB` for versioned snapshots.
- Quantity columns use `NUMERIC(18,3)` with application `HALF_UP`; money uses integer minor units where applicable.
- Every new table/column requires `COMMENT ON TABLE/COLUMN` matching the descriptions below.
- Every core row includes `created_at`; mutable rows also include `updated_at` and an optimistic/version marker where needed.
- No cross-service FK. Local FK/check constraints are allowed inside Inventory DB.

### 10.2 Ingredient and recipe tables

#### `ingredients`

| Column | Type | Constraint | Remark |
|---|---|---|---|
| `id` | UUID | PK | Ingredient UUID v7. |
| `tenant_id` | UUID | NOT NULL, index | Owning tenant; every query includes this scope. |
| `code` | TEXT | NOT NULL | Tenant-normalized unique ingredient code. |
| `name` | TEXT | NOT NULL | Human-readable ingredient name. |
| `base_unit` | TEXT | CHECK `g/ml/unit` | Canonical stock unit. |
| `status` | TEXT | CHECK | `ACTIVE`, `INACTIVE`, `ARCHIVED`. |
| `version` | BIGINT | NOT NULL | Optimistic metadata version. |
| `created_by` | UUID NULL | — | Human actor that created the ingredient. |
| `created_at` | TIMESTAMPTZ | NOT NULL | Creation time UTC. |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Last metadata change UTC. |

#### `ingredient_conversions`

| Column | Type | Constraint | Remark |
|---|---|---|---|
| `id` | UUID | PK | Purchase conversion UUID v7. |
| `tenant_id` | UUID | NOT NULL | Tenant scope defense-in-depth. |
| `ingredient_id` | UUID | FK `ingredients(id)` | Ingredient receiving purchase conversion. |
| `purchase_unit` | TEXT | NOT NULL | Unit used by receipt/purchase input. |
| `base_quantity` | NUMERIC(18,3) | CHECK `> 0` | Base-unit quantity represented by one purchase unit. |
| `version` | BIGINT | NOT NULL | Optimistic conversion version. |
| `status` | TEXT | CHECK | `ACTIVE` or `INACTIVE`. |
| `created_at` | TIMESTAMPTZ | NOT NULL | Creation time UTC. |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Last conversion update UTC. |

#### `recipe_versions`

| Column | Type | Constraint | Remark |
|---|---|---|---|
| `id` | UUID | PK | Recipe version UUID v7. |
| `tenant_id` | UUID | NOT NULL | Tenant owning the recipe. |
| `catalog_item_id` | UUID | NOT NULL | Opaque Catalog item reference. |
| `variant_id` | UUID | NOT NULL | Opaque Catalog variant reference. |
| `version` | INTEGER | NOT NULL | Monotonic version for item/variant. |
| `status` | TEXT | CHECK | `DRAFT`, `ACTIVE`, `INACTIVE`, `ARCHIVED`. |
| `version_etag` | TEXT | NOT NULL | API optimistic concurrency token. |
| `activated_at` | TIMESTAMPTZ NULL | — | Time the version became active. |
| `created_by` | UUID NULL | — | Actor that created the version. |
| `created_at` | TIMESTAMPTZ | NOT NULL | Creation time UTC. |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Last draft/state update UTC. |

#### `recipe_lines`

| Column | Type | Constraint | Remark |
|---|---|---|---|
| `id` | UUID | PK | Recipe line UUID v7. |
| `tenant_id` | UUID | NOT NULL | Tenant scope defense-in-depth. |
| `recipe_version_id` | UUID | FK `recipe_versions(id)` | Parent immutable recipe version. |
| `ingredient_id` | UUID | FK `ingredients(id)` | Required consumable ingredient reference. |
| `modifier_option_id` | UUID NULL | No cross-service FK | Optional Catalog modifier scope; null means base item/variant. |
| `quantity_per_sale` | NUMERIC(18,3) | CHECK `> 0` | Base-unit quantity per one sale. |
| `waste_factor` | NUMERIC(18,3) | CHECK `>= 1.000` | Multiplicative waste factor. |
| `base_unit` | TEXT | CHECK | Unit snapshot matching ingredient base unit. |
| `created_at` | TIMESTAMPTZ | NOT NULL | Creation time UTC. |

### 10.3 Stock and reservation tables

#### `stock_balances`

| Column | Type | Constraint | Remark |
|---|---|---|---|
| `id` | UUID | PK | Balance row UUID v7. |
| `tenant_id` | UUID | NOT NULL | Owning tenant. |
| `branch_id` | UUID | NOT NULL | Opaque branch reference and lock scope. |
| `ingredient_id` | UUID | FK `ingredients(id)` | Ingredient held at branch. |
| `on_hand` | NUMERIC(18,3) | CHECK `>= 0` | Projected physical quantity. |
| `reserved` | NUMERIC(18,3) | CHECK `>= 0` | Quantity held by active reservations. |
| `available` | NUMERIC(18,3) | CHECK `>= 0` | Stored projection equal to `on_hand - reserved`. |
| `version` | BIGINT | NOT NULL | Monotonic mutation/version marker. |
| `created_at` | TIMESTAMPTZ | NOT NULL | Creation time UTC. |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Last balance mutation UTC. |

Constraint: `available = on_hand - reserved`; unique `(tenant_id, branch_id, ingredient_id)`.

#### `reservations`

| Column | Type | Constraint | Remark |
|---|---|---|---|
| `id` | UUID | PK | Reservation UUID v7. |
| `tenant_id` | UUID | NOT NULL | Owning tenant. |
| `branch_id` | UUID | NOT NULL | Sale branch and stock scope. |
| `order_id` | UUID | NOT NULL | Opaque Order reference. |
| `checkout_attempt_id` | UUID | NOT NULL | Unique business reservation scope. |
| `saga_id` | UUID | NOT NULL | Checkout Saga workflow reference. |
| `status` | TEXT | CHECK | `RESERVED`, `COMMITTED`, `RELEASED`, `EXPIRED`, `RECONCILIATION_REQUIRED`. |
| `expires_at` | TIMESTAMPTZ | CHECK `> created_at` | Reservation deadline. |
| `request_hash` | TEXT | NOT NULL | Canonical input hash for idempotency. |
| `idempotency_key` | TEXT | NOT NULL | Transport retry key. |
| `reservation_version` | BIGINT | NOT NULL | State transition version. |
| `created_at` | TIMESTAMPTZ | NOT NULL | Creation time UTC. |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Last state transition UTC. |
| `terminal_at` | TIMESTAMPTZ NULL | — | Time terminal state was reached. |

#### `reservation_lines`

| Column | Type | Constraint | Remark |
|---|---|---|---|
| `id` | UUID | PK | Reservation line UUID v7. |
| `tenant_id` | UUID | NOT NULL | Tenant scope defense-in-depth. |
| `reservation_id` | UUID | FK `reservations(id)` | Parent reservation. |
| `ingredient_id` | UUID | FK `ingredients(id)` | Expanded ingredient. |
| `quantity` | NUMERIC(18,3) | CHECK `> 0` | Held/consumed base-unit quantity. |
| `base_unit` | TEXT | CHECK | Unit snapshot for the quantity. |
| `source_line_ids` | JSONB | NOT NULL | Order line IDs contributing to this aggregate. |
| `recipe_snapshot` | JSONB | NOT NULL | Immutable recipe version/line snapshot. |
| `created_at` | TIMESTAMPTZ | NOT NULL | Creation time UTC. |

#### `stock_ledger`

| Column | Type | Constraint | Remark |
|---|---|---|---|
| `id` | UUID | PK | Ledger entry UUID v7. |
| `tenant_id` | UUID | NOT NULL | Owning tenant. |
| `branch_id` | UUID | NOT NULL | Stock branch. |
| `ingredient_id` | UUID | FK `ingredients(id)` | Ingredient moved. |
| `movement_type` | TEXT | CHECK | `OPENING_BALANCE`, `RECEIPT`, `SALE_COMMITTED`, `COUNT_ADJUSTMENT`, `WASTE`, `RETURN`. |
| `quantity_delta` | NUMERIC(18,3) | CHECK `<> 0` | Signed base-unit movement. |
| `base_unit` | TEXT | CHECK | Unit snapshot. |
| `source_type` | TEXT | NOT NULL | `ORDER`, `RESERVATION`, `RECEIPT`, `COUNT`, `MANUAL`. |
| `source_id` | UUID | NOT NULL | Source operation reference. |
| `reason` | TEXT NULL | — | Required for manual/waste/count/return operations. |
| `actor_id` | UUID NULL | — | Human actor; null for Saga workload where appropriate. |
| `correlation_id` | UUID | NOT NULL | Cross-service trace reference. |
| `occurred_at` | TIMESTAMPTZ | NOT NULL | Business occurrence UTC. |
| `idempotency_key` | TEXT | NOT NULL | Duplicate side-effect key. |
| `created_at` | TIMESTAMPTZ | NOT NULL | Persistence time UTC. |

### 10.4 Receipt, count, alert and reconciliation tables

#### `receipts`

| Column | Type | Constraint | Remark |
|---|---|---|---|
| `id` | UUID | PK | Receipt UUID v7. |
| `tenant_id` | UUID | NOT NULL | Owning tenant. |
| `branch_id` | UUID | NOT NULL | Receiving branch. |
| `supplier_reference` | TEXT NULL | — | Optional supplier/document reference. |
| `status` | TEXT | CHECK | `POSTED` or `CANCELLED`; posted history is not deleted. |
| `reason` | TEXT | NOT NULL | Business reason for receipt. |
| `actor_id` | UUID | NOT NULL | Human actor creating receipt. |
| `idempotency_key` | TEXT | NOT NULL | Receipt retry key. |
| `created_at` | TIMESTAMPTZ | NOT NULL | Receipt creation time UTC. |

#### `receipt_lines`

| Column | Type | Constraint | Remark |
|---|---|---|---|
| `id` | UUID | PK | Receipt line UUID v7. |
| `tenant_id` | UUID | NOT NULL | Tenant scope. |
| `receipt_id` | UUID | FK `receipts(id)` | Parent receipt. |
| `ingredient_id` | UUID | FK `ingredients(id)` | Received ingredient. |
| `purchase_quantity` | NUMERIC(18,3) | CHECK `> 0` | Quantity in purchase unit. |
| `purchase_unit` | TEXT | NOT NULL | Input purchase unit. |
| `base_quantity` | NUMERIC(18,3) | CHECK `> 0` | Converted base-unit quantity. |
| `base_unit` | TEXT | CHECK | Ingredient base unit snapshot. |
| `conversion_factor` | NUMERIC(18,3) | CHECK `> 0` | Conversion snapshot used. |
| `lot_code` | TEXT NULL | — | Optional lot metadata; no FEFO in P1. |
| `expires_at` | TIMESTAMPTZ NULL | — | Optional expiry metadata. |
| `created_at` | TIMESTAMPTZ | NOT NULL | Receipt line creation time UTC. |

#### `count_sessions`

| Column | Type | Constraint | Remark |
|---|---|---|---|
| `id` | UUID | PK | Count session UUID v7. |
| `tenant_id` | UUID | NOT NULL | Owning tenant. |
| `branch_id` | UUID | NOT NULL | Count branch. |
| `status` | TEXT | CHECK | `OPEN`, `PENDING_APPROVAL`, `APPROVED`, `CANCELLED`. |
| `snapshot_at` | TIMESTAMPTZ | NOT NULL | Snapshot time UTC. |
| `snapshot_version` | BIGINT | NOT NULL | Session approval version. |
| `opened_by` | UUID | NOT NULL | Actor opening session. |
| `approved_by` | UUID NULL | — | Actor approving adjustment. |
| `approved_at` | TIMESTAMPTZ NULL | — | Approval time UTC. |
| `reason` | TEXT | NOT NULL | Count business reason. |
| `idempotency_key` | TEXT | NOT NULL | Creation/approval retry key. |
| `created_at` | TIMESTAMPTZ | NOT NULL | Session creation time UTC. |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Last session transition UTC. |

#### `count_session_lines`

| Column | Type | Constraint | Remark |
|---|---|---|---|
| `id` | UUID | PK | Count line UUID v7. |
| `tenant_id` | UUID | NOT NULL | Tenant scope. |
| `count_session_id` | UUID | FK `count_sessions(id)` | Parent count session. |
| `ingredient_id` | UUID | FK `ingredients(id)` | Counted ingredient. |
| `snapshot_on_hand` | NUMERIC(18,3) | NOT NULL | Physical balance at session snapshot. |
| `snapshot_reserved` | NUMERIC(18,3) | NOT NULL | Reservation balance at snapshot. |
| `snapshot_available` | NUMERIC(18,3) | NOT NULL | Available balance at snapshot. |
| `counted_quantity` | NUMERIC(18,3) NULL | CHECK `>= 0` | Physical quantity entered by employee. |
| `expected_on_hand_at_approval` | NUMERIC(18,3) NULL | — | Snapshot plus ledger delta after snapshot. |
| `adjustment_quantity` | NUMERIC(18,3) NULL | — | Counted minus expected on-hand. |
| `entered_by` | UUID NULL | — | Employee entering count. |
| `created_at` | TIMESTAMPTZ | NOT NULL | Count line creation time UTC. |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Last count line update UTC. |

#### `low_stock_thresholds` and `low_stock_alerts`

| Table | Columns with remarks |
|---|---|
| `low_stock_thresholds` | `id UUID` threshold ID; `tenant_id UUID` owner; `branch_id UUID` branch scope; `ingredient_id UUID` ingredient; `threshold NUMERIC(18,3)` available threshold; `version BIGINT` optimistic config version; `updated_by UUID` actor; `created_at/updated_at TIMESTAMPTZ` lifecycle timestamps. |
| `low_stock_alerts` | `id UUID` alert ID; `tenant_id UUID` owner; `branch_id UUID` branch; `ingredient_id UUID` ingredient; `threshold NUMERIC(18,3)` threshold snapshot; `available_at_detection NUMERIC(18,3)` available at crossing; `status TEXT` `OPEN/ACKNOWLEDGED/RESOLVED`; `detected_at TIMESTAMPTZ` crossing time; `acknowledged_by/at` optional actor/time; `resolved_by/at` optional actor/time; `resolution_reason TEXT` operator reason; `crossing_key TEXT` dedupe/re-arm key; `created_at/updated_at TIMESTAMPTZ` lifecycle timestamps. |

#### `reconciliation_cases`

| Column | Type | Constraint | Remark |
|---|---|---|---|
| `id` | UUID | PK | Reconciliation case UUID v7. |
| `tenant_id` | UUID | NOT NULL | Owning tenant. |
| `branch_id` | UUID | NOT NULL | Stock branch. |
| `reservation_id` | UUID | NOT NULL | Affected reservation. |
| `order_id` | UUID | NOT NULL | Opaque Order reference. |
| `payment_id` | UUID NULL | — | Payment reference if known. |
| `reason_code` | TEXT | NOT NULL | Typed unsafe/failed transition reason. |
| `status` | TEXT | CHECK | `OPEN`, `IN_REVIEW`, `RESOLVED`, `CLOSED`. |
| `last_safe_state` | TEXT | NOT NULL | Reservation state before exception. |
| `correlation_id` | UUID | NOT NULL | Trace reference. |
| `operator_reference` | TEXT NULL | — | External/manual resolution reference. |
| `next_review_at` | TIMESTAMPTZ NULL | — | Review/alert schedule. |
| `created_at` | TIMESTAMPTZ | NOT NULL | Case creation time UTC. |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Last case update UTC. |

### 10.5 Forecast tables

#### `forecast_policies`

| Column | Type | Constraint | Remark |
|---|---|---|---|
| `id` | UUID | PK | Forecast policy UUID v7. |
| `tenant_id` | UUID | NOT NULL | Owning tenant. |
| `branch_id` | UUID NULL | — | Null for tenant default, branch ID for override. |
| `ingredient_id` | UUID | FK `ingredients(id)` | Ingredient policy applies to. |
| `lead_time_days` | INTEGER | CHECK `>= 0` | Supplier lead time in days. |
| `safety_stock` | NUMERIC(18,3) | CHECK `>= 0` | Base-unit safety stock. |
| `review_period_days` | INTEGER | CHECK `>= 0` | Optional planning review period. |
| `version` | BIGINT | NOT NULL | Optimistic config version. |
| `updated_by` | UUID | NOT NULL | Actor changing policy. |
| `created_at/updated_at` | TIMESTAMPTZ | NOT NULL | Policy lifecycle timestamps. |

#### `forecast_runs` and `forecast_results`

| Table | Columns with remarks |
|---|---|
| `forecast_runs` | `id UUID` run ID; `tenant_id UUID` owner; `started_at/finished_at TIMESTAMPTZ` execution window; `model_version TEXT` algorithm version; `status TEXT` run state; `error_code TEXT NULL` safe failure; `created_at TIMESTAMPTZ` persistence time. |
| `forecast_results` | `id UUID` result ID; `tenant_id UUID` owner; `branch_id UUID` branch; `ingredient_id UUID` ingredient; `forecast_date DATE` target date; `recommended_quantity NUMERIC(18,3) NULL` result, null for insufficient history; `base_unit TEXT` unit; `quality TEXT` quality tier; `sample_count INTEGER` valid sample days; `input_from/input_to DATE` source window; `safety_stock NUMERIC(18,3)` applied stock; `safety_stock_source TEXT` config origin; `model_version TEXT` algorithm; `calculated_at TIMESTAMPTZ` result time; `freshness_seconds INTEGER` age at read; `run_id UUID` parent run; `created_at TIMESTAMPTZ` persistence time. |

### 10.6 Common reliability tables

#### `outbox_messages`

| Column | Type | Constraint | Remark |
|---|---|---|---|
| `id` | UUID | PK | Outbox message UUID v7/event ID. |
| `tenant_id` | UUID | NOT NULL | Event tenant scope. |
| `branch_id` | UUID NULL | — | Null only for tenant-wide events. |
| `aggregate_type` | TEXT | NOT NULL | Aggregate category. |
| `aggregate_id` | UUID | NOT NULL | Aggregate reference. |
| `aggregate_version` | BIGINT | NOT NULL | Aggregate version at event creation. |
| `event_type` | TEXT | NOT NULL | Versioned event type. |
| `event_version` | INTEGER | NOT NULL | Event schema version. |
| `payload` | JSONB | NOT NULL | Validated event envelope/payload. |
| `correlation_id` | UUID | NOT NULL | Trace correlation. |
| `causation_id` | UUID NULL | — | Command/event causing this event. |
| `publish_status` | TEXT | CHECK | `PENDING`, `PUBLISHED`, `FAILED`. |
| `attempt_count` | INTEGER | NOT NULL | Publish retry count. |
| `last_error` | TEXT NULL | — | Sanitized publish failure. |
| `published_at` | TIMESTAMPTZ NULL | — | Broker acknowledgement time. |
| `next_attempt_at` | TIMESTAMPTZ NOT NULL | — | Earliest time eligible for the next publish attempt. |
| `lease_owner` | TEXT NULL | — | Worker instance holding the publish lease. |
| `lease_until` | TIMESTAMPTZ NULL | — | Lease expiry; expired leases are reclaimable. |
| `created_at` | TIMESTAMPTZ | NOT NULL | Outbox creation time. |

#### `idempotency_records`

| Column | Type | Constraint | Remark |
|---|---|---|---|
| `id` | UUID | PK | Idempotency record UUID v7. |
| `tenant_id` | UUID | NOT NULL | Tenant scope. |
| `actor_or_workload_id` | TEXT | NOT NULL | Calling actor/workload scope. |
| `operation` | TEXT | NOT NULL | Command name. |
| `idempotency_key` | TEXT | NOT NULL | Client/workload retry key. |
| `request_hash` | TEXT | NOT NULL | Canonical request digest. |
| `response_status` | INTEGER | NOT NULL | Original HTTP status. |
| `response_body` | JSONB | NOT NULL | Safe original response. |
| `resource_id` | UUID NULL | — | Created/affected resource. |
| `expires_at` | TIMESTAMPTZ | NOT NULL | Idempotency retention deadline. |
| `created_at` | TIMESTAMPTZ | NOT NULL | Record creation time. |

## 11. Constraints and indexes

### 11.1 Local constraints

- Local FK only within Inventory DB; no Catalog/Order/Payment/Identity/Branch cross-database FK.
- Every child row carries tenant scope where defense-in-depth is needed; application verifies parent/child tenant equality.
- `stock_balances.available = stock_balances.on_hand - stock_balances.reserved`.
- `on_hand`, `reserved`, `available`, receipt/count/forecast quantities are nonnegative where applicable.
- Signed `stock_ledger.quantity_delta` is nonzero; movement type controls expected sign.
- One active recipe: partial unique `(tenant_id, catalog_item_id, variant_id) WHERE status='ACTIVE'`.
- One checkout reservation: unique `(tenant_id, checkout_attempt_id)`.
- One balance row: unique `(tenant_id, branch_id, ingredient_id)`.
- Idempotency: unique `(tenant_id, actor_or_workload_id, operation, idempotency_key)`.
- Ledger source dedupe: unique `(tenant_id, branch_id, source_type, source_id, idempotency_key)`.
- Threshold: unique `(tenant_id, branch_id, ingredient_id)`.
- Alert crossing: partial unique `(tenant_id, branch_id, ingredient_id, crossing_key) WHERE status IN ('OPEN','ACKNOWLEDGED')`; resolving an alert permits a new crossing key/alert.

### 11.2 Baseline indexes

| Table | Index | Purpose |
|---|---|---|
| `ingredients` | `(tenant_id, status, code)` | Tenant ingredient lookup/filter. |
| `recipe_versions` | `(tenant_id, catalog_item_id, variant_id, status, version DESC)` | Resolve active/history versions. |
| `recipe_lines` | `(tenant_id, recipe_version_id, ingredient_id)` | Expand recipe and validate lines. |
| `stock_balances` | unique `(tenant_id, branch_id, ingredient_id)` | Lockable branch balance lookup. |
| `reservations` | `(tenant_id, branch_id, status, expires_at)` | Expiry worker scan. |
| `reservations` | `(tenant_id, checkout_attempt_id)` unique | Retry/ownership lookup. |
| `reservation_lines` | `(tenant_id, reservation_id, ingredient_id)` | Commit/release balance updates. |
| `stock_ledger` | `(tenant_id, branch_id, ingredient_id, occurred_at DESC, id DESC)` | Ledger cursor/history. |
| `stock_ledger` | `(tenant_id, source_type, source_id, idempotency_key)` unique | Side-effect dedupe. |
| `count_sessions` | `(tenant_id, branch_id, status, created_at DESC)` | Count admin list/approval. |
| `low_stock_alerts` | `(tenant_id, branch_id, status, detected_at DESC)` | Alert queue/dashboard. |
| `reconciliation_cases` | `(tenant_id, status, next_review_at)` | Operations review queue. |
| `forecast_results` | `(tenant_id, branch_id, ingredient_id, forecast_date DESC)` | Forecast lookup. |
| `outbox_messages` | `(publish_status, next_attempt_at, created_at)` | Publisher eligibility/poll. |
| `outbox_messages` | `(lease_until)` WHERE `publish_status='PENDING'` | Reclaim expired publisher leases. |

### 11.3 Partitioning

- No partitioning for ordinary P0 tables below approximately 10M rows.
- Review monthly RANGE partitioning for `stock_ledger`, `outbox_messages` and audit/event history when volume/retention operations justify it.
- Keep tenant/branch columns and equivalent indexes in every partition.
- Never use partitioning as a substitute for tenant predicates or balance row locks.

## 12. Flyway migration order

Migration path: `classpath:db/migration/inventory/`.

| Version | Migration | Contents |
|---|---|---|
| V1 | `V1__create_inventory_core.sql` | Extensions/UUID prerequisites, ingredients, conversions, recipe versions/lines, comments and core checks. |
| V2 | `V2__create_stock_and_reservations.sql` | Stock balances, reservations, reservation lines, row constraints and indexes. |
| V3 | `V3__create_ledger_receipt_count.sql` | Stock ledger, receipts, count sessions/lines, comments and source constraints. |
| V4 | `V4__create_alert_reconciliation_forecast.sql` | Thresholds, alerts, reconciliation cases, forecast policy/run/result tables. |
| V5 | `V5__create_outbox_idempotency.sql` | Outbox, idempotency records, publisher indexes and comments. |
| V6 | `V6__add_inventory_unique_constraints.sql` | Partial active-recipe, checkout, balance, source-dedupe and alert-crossing constraints. |
| V7 | `V7__add_inventory_operational_indexes.sql` | Cursor/history, expiry, reconciliation, forecast and outbox indexes. |

Rules:

- Every migration is transactional where PostgreSQL supports it and has verification queries.
- Every table/column has `COMMENT ON TABLE/COLUMN`.
- Shared environments use forward-only migrations; destructive changes use expand → migrate → contract.
- Rollback is corrective migration plus backup/restore; never delete ledger/reservation history.
- Validate migrations on empty DB and copied data DB before deployment.

## 13. Event delivery and retry contract

- Outbox insert is atomic with local business state.
- Publisher leases pending rows, publishes at-least-once and marks `PUBLISHED` after broker acknowledgement.
- Retry uses exponential backoff with jitter; permanent schema/auth failures go to DLQ/manual review.
- Consumers validate event version and tenant/branch metadata before applying side effects.
- Duplicate event is a no-op after inbox/semantic source-key check.
- Inventory has no direct `PaymentPaid` consumer; Saga owns payment fact handling and Inventory command retry.

## 14. Contract test matrix

| Area | Required fixtures |
|---|---|
| Auth/scope | Missing JWT, wrong role, revoked membership, cross-tenant ID, cross-branch ID, workload identity mismatch. |
| Ingredient/recipe | Duplicate code, inactive ingredient, invalid unit, stale `If-Match`, two active recipes, invalid modifier scope, archive reference. |
| Reserve | Milk-tea expansion, topping expansion, shared ingredient aggregation, missing recipe, shortage rollback, same request retry, different hash conflict. |
| Concurrency | Concurrent reserve, deadlock retry, lock timeout, commit/release race, commit/expiry race, recipe activation race. |
| Commit/release | Payment context mismatch, terminal retry, release compensation, `SALE_COMMITTED` uniqueness, no direct PaymentPaid commit. |
| Ledger/projection | Signed movement checks, rebuild checksum, no negative available, source dedupe, append-only history. |
| Count | Snapshot plus later ledger delta formula, concurrent sale, stale approval, duplicate approval, projected-negative-on-hand rejection. |
| Alerts | Threshold crossing, duplicate event, resolve/re-arm, threshold update re-evaluation, tenant/branch scope. |
| Forecast | 0/1–13/14–27/28+ sample tiers, weekday adjustment, safety stock override, freshness and insufficient history. |
| Reliability | Outbox rollback, publish retry, duplicate event, unknown version quarantine, DLQ replay and correlation propagation. |
| API | OpenAPI request/response/error validation, pagination allowlist, maximum page size, no raw SQL sort/filter. |

## 15. Contract artifacts and compatibility

The implementation gate requires:

- `openapi.yaml` for public and internal Inventory APIs.
- AsyncAPI/JSON Schemas for all Inventory events.
- Example fixtures for reserve/commit/release/expiry/shortage/reconciliation.
- Generated client/server validation in CI.
- Consumer-driven tests for Saga, Order, Reporting, Audit and Notification event consumers.
- Additive fields within an event version only; field removal/meaning change requires a new event version and migration plan.

## 16. Approval gate

- **Current status:** Approved with warnings
- **Next action:** Design Finalization; materialize schema/test artifacts before implementation
