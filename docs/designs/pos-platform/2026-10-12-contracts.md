Started at: 2026/10/12 14:26:10
Finished at: 2026/10/12 14:26:10
Total time: 0 minutes
---

# Contracts: POS Platform

## Source

- **Architecture:** [2026-10-12-architecture.md](./2026-10-12-architecture.md)
- **Requirements:** [2026-10-12-requirements.md](./2026-10-12-requirements.md)
- **Vision:** [2026-10-12-vision.md](./2026-10-12-vision.md)

## Contract Conventions

### API Style and Versioning

- Style: REST + OpenAPI 3.1 for synchronous APIs; AsyncAPI + JSON Schema for events.
- Base path: `/api/v1` for public APIs; `/internal/v1` for service-to-service APIs; `/callbacks/v1` for verified provider callbacks.
- Content type: `application/json; charset=utf-8`.
- Resource names are plural nouns in kebab-case; action endpoints use a verb sub-resource only for state transitions (`/checkout`, `/cancel`, `/close`).
- Breaking changes create a new major URI version. New optional fields/events are backward-compatible additions.
- All timestamps are ISO-8601 UTC strings; all internal IDs are UUID v7 strings.

### Authentication and Scope

```http
Authorization: Bearer <Cognito JWT>
X-Correlation-Id: <optional client correlation id>
Idempotency-Key: <required for side-effecting retryable commands>
```

- API Gateway validates Cognito issuer/signature; every Spring Boot service validates JWT claims again.
- Required JWT context for P0 is limited to cryptographic/identity claims: `sub`, `iss`, `aud`, `exp`.
  Optional `tenant_memberships`, `roles` and `branch_ids` claims may be present for UI or
  coarse routing hints, but are never authoritative for business authorization. Each
  service resolves current tenant/branch/permission scope through the versioned Identity
  Access authorization decision and applies that scope to its own data queries.
- A caller cannot set tenant scope using a request body alone. `X-Active-Branch-Id` may select one branch only after server-side membership validation.
- Internal endpoints require service identity/mTLS or signed workload credentials and are never exposed through the public Angular route table.

### Standard Success and Error Envelopes

```json
{
  "data": {},
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

```json
{
  "error": {
    "code": "validation_error",
    "message": "Request validation failed",
    "details": [
      {
        "field": "lines[0].quantity",
        "code": "min_value",
        "message": "Quantity must be greater than zero"
      }
    ],
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

Standard codes:

| Code | HTTP | Meaning |
|---|---:|---|
| `validation_error` | 422 | Payload or business input invalid |
| `unauthorized` | 401 | Missing/invalid/expired JWT |
| `forbidden` | 403 | Authenticated but outside role/tenant/branch scope |
| `not_found` | 404 | Resource is absent within authorized scope |
| `conflict` | 409 | State transition/version/idempotency conflict |
| `payment_pending` | 202 | Provider has not completed the payment |
| `inventory_unavailable` | 409 | Required recipe quantity cannot be reserved |
| `duplicate_request` | 409 | Same idempotency key has a different request hash |
| `rate_limited` | 429 | Gateway or provider limit exceeded |
| `service_unavailable` | 503 | Dependency unavailable after bounded retry |
| `internal_error` | 500 | Unexpected error; no stack trace exposed |

### Pagination and Filtering

- Transactional/audit collections use cursor pagination:

```text
GET /api/v1/orders?limit=20&cursor=<opaque>&status=PAID
```

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

- Catalog/admin collections use offset pagination because the Angular grids need page navigation: `page`, `limit`, `total`, `totalPages`.
- Maximum `limit` is 100; default is 20. All filters are tenant/branch scoped before pagination.
- Sort fields are allowlisted per endpoint; arbitrary SQL/order expressions are rejected.

### Rate Limits, Timeouts and Caching

- API Gateway applies token-bucket rate limiting per tenant/user and stricter limits to callbacks and report queries.
- Default synchronous service timeout is 3 seconds; provider calls use a shorter configured timeout and circuit breaker.
- `GET /catalog/items` may use `ETag`/`If-None-Match` for short-lived catalog caching. Order, payment, loyalty balance and audit responses are not publicly cached.

## API Contracts

### Identity and Tenant Service

| Method | Path | Auth/Scope | Request | Response | Statuses |
|---|---|---|---|---|---|
| GET | `/api/v1/me` | JWT | — | `MeResponse` | 200, 401 |
| POST | `/api/v1/tenants` | `OWNER` bootstrap | `CreateTenantRequest` | `TenantResponse` | 201, 422, 409 |
| GET | `/api/v1/tenants/{tenantId}/branches` | tenant read | query pagination | `BranchPage` | 200, 403, 404 |
| POST | `/api/v1/tenants/{tenantId}/branches` | tenant write | `CreateBranchRequest` | `BranchResponse` | 201, 403, 422 |
| POST | `/api/v1/tenants/{tenantId}/memberships` | tenant write | `CreateMembershipRequest` | `MembershipResponse` | 201, 403, 422, 409 |

### Catalog Service

| Method | Path | Auth/Scope | Request | Response | Statuses |
|---|---|---|---|---|---|
| GET | `/api/v1/catalog/items` | branch read | filters + pagination | `CatalogItemPage` | 200, 403 |
| GET | `/api/v1/catalog/items/{itemId}` | branch read | — | `CatalogItemResponse` | 200, 403, 404 |
| POST | `/api/v1/catalog/items` | owner/manager | `CreateCatalogItemRequest` | `CatalogItemResponse` | 201, 403, 422 |
| PATCH | `/api/v1/catalog/items/{itemId}` | owner/manager | `UpdateCatalogItemRequest` + `If-Match` | `CatalogItemResponse` | 200, 403, 404, 409 |
| POST | `/api/v1/catalog/items/{itemId}/availability` | owner/manager/cashier | `AvailabilityChangeRequest` | `CatalogItemResponse` | 200, 403, 404, 409 |

### Shift Service

| Method | Path | Auth/Scope | Request | Response | Statuses |
|---|---|---|---|---|---|
| POST | `/api/v1/branches/{branchId}/shifts` | cashier/manager | `OpenShiftRequest` | `ShiftResponse` | 201, 403, 409, 422 |
| GET | `/api/v1/branches/{branchId}/shifts/current` | cashier/manager | — | `ShiftResponse` | 200, 403, 404 |
| POST | `/api/v1/shifts/{shiftId}/close` | cashier/manager | `CloseShiftRequest` | `ShiftResponse` | 200, 403, 404, 409 |

### Order Service

| Method | Path | Auth/Scope | Request | Response | Statuses |
|---|---|---|---|---|---|
| POST | `/api/v1/orders` | cashier/manager | `CreateOrderRequest` | `OrderResponse` | 201, 403, 422 |
| GET | `/api/v1/orders/{orderId}` | branch read | — | `OrderResponse` | 200, 403, 404 |
| GET | `/api/v1/orders` | branch read | cursor + filters | `OrderPage` | 200, 403 |
| PATCH | `/api/v1/orders/{orderId}` | cashier/manager | `UpdateOrderRequest` + `If-Match` | `OrderResponse` | 200, 403, 404, 409 |
| POST | `/api/v1/orders/{orderId}/line-items/{lineItemId}/cancel` | manager | `CancelLineItemRequest` | `OrderResponse` | 200, 403, 404, 409 |
| POST | `/api/v1/orders/{orderId}/checkout` | cashier/manager | `CheckoutRequest` | `CheckoutResponse` | 202/200, 403, 404, 409, 422 |

### Inventory Service (Internal)

| Method | Path | Auth/Scope | Request | Response | Statuses |
|---|---|---|---|---|---|
| POST | `/internal/v1/inventory/reservations` | Saga identity | `ReserveInventoryRequest` | `ReservationResponse` | 201, 409, 422, 503 |
| POST | `/internal/v1/inventory/reservations/{reservationId}/commit` | Saga identity | `CommitReservationRequest` | `ReservationResponse` | 200, 404, 409, 422, 503 |
| POST | `/internal/v1/inventory/reservations/{reservationId}/release` | Saga identity | `ReleaseReservationRequest` | `ReservationResponse` | 200, 404, 409 |
| POST | `/internal/v1/inventory/availability` | Order/Saga identity | `AvailabilityRequest` body | `AvailabilityResponse` | 200, 403, 422, 503 |

### Payment Service

| Method | Path | Auth/Scope | Request | Response | Statuses |
|---|---|---|---|---|---|
| POST | `/internal/v1/orders/{orderId}/payments` | Order/Saga identity | `InitiatePaymentRequest` | `PaymentResponse` | 201/202, 409, 422, 503 |
| GET | `/api/v1/payments/{paymentId}` | branch read | — | `PaymentResponse` | 200, 403, 404 |
| POST | `/callbacks/v1/vnpay/ipn` | provider allowlist + signature | `VnpayIpnRequest` | `VnpayIpnResponse` | 200, 400, 401, 409 |
| POST | `/internal/v1/payments/reconciliation/run` | scheduler identity | `ReconciliationRunRequest` | `ReconciliationRunResponse` | 202, 409, 503 |

### Loyalty Service

| Method | Path | Auth/Scope | Request | Response | Statuses |
|---|---|---|---|---|---|
| POST | `/api/v1/customers/lookup` | cashier/manager | `CustomerLookupRequest` | `CustomerResponse` | 200, 403, 404, 422 |
| POST | `/api/v1/customers` | cashier/manager | `CreateCustomerRequest` | `CustomerResponse` | 201, 403, 409, 422 |
| GET | `/api/v1/customers/{customerId}/loyalty-balance` | cashier/manager/owner | — | `LoyaltyBalanceResponse` | 200, 403, 404 |
| POST | `/internal/v1/loyalty/accruals` | event consumer/replay identity | `AccruePointsRequest` | `LedgerEntryResponse` | 201/200, 409, 422 |

### Recommendation Service

| Method | Path | Auth/Scope | Request | Response | Statuses |
|---|---|---|---|---|---|
| POST | `/api/v1/recommendations` | cashier/manager | `RecommendationRequest` | `RecommendationResponse` | 200, 403, 422 |
| POST | `/api/v1/recommendations/{suggestionId}/accept` | cashier/manager | `AcceptSuggestionRequest` | `SuggestionAcceptanceResponse` | 201/200, 403, 404, 409 |

### Reporting and Audit

| Method | Path | Auth/Scope | Request | Response | Statuses |
|---|---|---|---|---|---|
| GET | `/api/v1/reports/sales/daily` | owner/manager | `from`, `to`, `branchId` | `DailySalesReport` | 200, 403, 422 |
| GET | `/api/v1/reports/top-items` | owner/manager | `from`, `to`, `branchId`, pagination | `TopItemsReport` | 200, 403, 422 |
| GET | `/api/v1/audit-events` | owner/auditor | cursor + filters | `AuditEventPage` | 200, 403 |

### Core Request/Response Schemas

#### `CreateOrderRequest`

| Field | Type | Required | Constraint/meaning |
|---|---|---:|---|
| `branchId` | UUID | yes | Must be an authorized branch |
| `shiftId` | UUID | yes | Must be an open shift in the same branch |
| `serviceMode` | enum | yes | `COUNTER`, `TAKEAWAY`, `TABLE` |
| `tableReference` | string | no | Free-form table reference; table map is P1 |
| `customerId` | UUID | no | Must belong to the same tenant |
| `lines` | array<OrderLineInput> | yes | At least one line |
| `note` | string | no | Max 500 characters; sanitized |

`OrderLineInput`: `catalogItemId` UUID, `variantId` UUID nullable, `modifierIds` UUID array, `quantity` positive integer, `note` optional max 250 characters.

#### `OrderResponse`

| Field | Type | Meaning |
|---|---|---|
| `id` | UUID | Internal order ID |
| `orderNumber` | string | Human-readable branch-scoped number |
| `tenantId` / `branchId` | UUID | Server-resolved ownership |
| `status` | enum | `DRAFT`, `CHECKOUT_PENDING`, `PAID`, `CHECKOUT_FAILED`, `CANCELLED` |
| `serviceMode` | enum | Order service mode |
| `customer` | CustomerSummary/null | Masked loyalty identity if linked |
| `lines` | array<OrderLineResponse> | Price/name snapshot and current line state |
| `totals` | MoneyTotals | Subtotal, VAT, discount and grand total |
| `version` | integer | Optimistic concurrency version for `If-Match` |
| `createdAt` / `updatedAt` | timestamp | UTC timestamps |

`MoneyTotals`: `subtotalMinor`, `vatMinor`, `discountMinor`, `grandTotalMinor`, `currency`.

#### `CheckoutRequest` and `CheckoutResponse`

| Field | Type | Required | Meaning |
|---|---|---:|---|
| `paymentMethod` | enum | yes | `CASH`, `VNPAY_QR` |
| `cashReceivedMinor` | integer | cash only | Must be ≥ grand total |
| `returnUrl` | URI | VNPAY only | Sandbox callback/return destination |
| `clientReference` | string | no | UI correlation/reference, not ownership evidence |

Response fields: `sagaId`, `orderId`, `paymentId`, `orderStatus`, `paymentStatus`, `cashChangeMinor`, `qrPayload`, `expiresAt`, `correlationId`. Cash can return `200`; VNPAY initiation returns `202` with `PENDING`.

#### `VnpayIpnRequest`

| Field | Type | Required | Meaning |
|---|---|---:|---|
| `providerTransactionId` | string | yes | VNPAY transaction identifier |
| `orderReference` | string | yes | Internal order/payment reference |
| `amountMinor` | integer | yes | Amount reported by provider |
| `responseCode` | string | yes | Provider result code |
| `transactionStatus` | string | yes | Provider transaction status |
| `signature` | string | yes | Checksum/HMAC to verify before use |
| `receivedAt` | timestamp | yes | Provider callback timestamp |

IPN response is provider-compatible and contains only `success`/`invalid` result semantics; internal error details stay in logs/audit.

#### `ReserveInventoryRequest`

| Field | Type | Required | Meaning |
|---|---|---:|---|
| `sagaId` | UUID | yes | Checkout workflow ID |
| `orderId` | UUID | yes | Order being reserved |
| `checkoutAttemptId` | UUID | yes | One active reservation scope and idempotency business key |
| `branchId` | UUID | yes | Inventory branch scope |
| `lines` | array<ReservationLineInput> | yes | Catalog item/variant/modifier selection to expand into ingredients |
| `expiresAt` | timestamp | yes | Reservation expiry |

Each `ReservationLineInput` contains `lineId`, `catalogItemId`, `variantId`, selected `modifierOptionIds` and `quantity`. Inventory resolves its own active recipe and stores the recipe/demand snapshot.

`Idempotency-Key` is required as a transport header. A successful response has `status=RESERVED`; shortage/missing-recipe failures use the typed error contract and do not create a partial reservation.

Response: `reservationId`, `status`, `reservedIngredients`, `shortages`, `expiresAt`, `correlationId` and current reservation version.

#### `CommitReservationRequest`

| Field | Type | Required | Meaning |
|---|---|---:|---|
| `reservationId` | UUID | yes | Reservation being committed |
| `checkoutAttemptId` | UUID | yes | Must match reservation |
| `orderId` | UUID | yes | Must match reservation |
| `paymentId` | UUID | yes | Verified successful payment reference |
| `paymentAttemptId` | UUID | yes | Payment attempt reference |
| `paymentStatus` | enum | yes | Must be `SUCCEEDED` |
| `paidAmountMinor` | integer | yes | Must match checkout amount context |
| `currency` | string | yes | Must match checkout currency context |

The command is accepted only from Step Functions/Saga workload identity. `PaymentPaid` is a signal consumed by Saga; it is not an automatic Inventory commit trigger.

#### `CustomerLookupRequest`

| Field | Type | Required | Constraint |
|---|---|---:|---|
| `phone` | string | yes | E.164-normalizable; never echoed raw in logs |
| `createIfMissing` | boolean | no | Default `false`; creation requires explicit cashier action |

#### `RecommendationResponse`

| Field | Type | Meaning |
|---|---|---|
| `suggestionId` | UUID | Suggestion impression ID |
| `orderId` | UUID | Cart context |
| `items` | array<SuggestedItem> | Item, reason, score and price snapshot |
| `expiresAt` | timestamp | Validity of the computed suggestion |
| `correlationId` | UUID | Trace context |

## Standard Authorization Matrix

| Scope/Role | Owner | Manager | Cashier | Kitchen |
|---|---:|---:|---:|---:|
| Tenant/branch administration | all tenant | assigned branch | — | — |
| Catalog read | all tenant | assigned branch | assigned branch | assigned branch |
| Catalog write | yes | yes | no | no |
| Order create/update | yes | yes | assigned branch | no |
| Cancel sensitive line | yes | yes | no | no |
| Payment/checkout | yes | yes | assigned branch | no |
| Loyalty lookup | yes | yes | assigned branch | no |
| Reports | all tenant | assigned branch | no | no |
| Audit read | all tenant | assigned branch | no | no |
| Internal Saga APIs | service identity only | no | no | no |

## Data Contracts

### Common Data Conventions

- PostgreSQL types: `UUID`, `TEXT`, `BIGINT`, `INTEGER`, `BOOLEAN`, `NUMERIC`, `JSONB`, `TIMESTAMPTZ`.
- Amount fields are `BIGINT` minor units. Quantity precision is service-owned; Inventory P0 uses `NUMERIC(18,3)` with `HALF_UP`, and the Inventory schema below is authoritative for stock quantities.
- Every table has an `id` UUID v7 unless the table is a projection keyed by a documented composite key.
- Every mutable core table has `created_at` and `updated_at`; actor fields are included where a human action changes state.
- Every column added by a migration must have a PostgreSQL `COMMENT ON COLUMN` containing the description in the tables below. No undocumented column is allowed.

### Identity/Tenant Schema

#### `tenants`

| Column | Type | Constraints | Description/comment |
|---|---|---|---|
| `id` | UUID | PK | Tenant identifier. |
| `code` | TEXT | NOT NULL, UNIQUE | Stable tenant code used in operations/support. |
| `name` | TEXT | NOT NULL | Display name of the business chain. |
| `status` | TEXT | CHECK | `ACTIVE` or `SUSPENDED`. |
| `timezone` | TEXT | NOT NULL | IANA timezone for business-day rendering. |
| `created_at` | TIMESTAMPTZ | NOT NULL | Creation time in UTC. |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Last metadata update in UTC. |

#### `branches`

| Column | Type | Constraints | Description/comment |
|---|---|---|---|
| `id` | UUID | PK | Branch identifier. |
| `tenant_id` | UUID | NOT NULL, index | Owning tenant; all reads require this scope. |
| `code` | TEXT | NOT NULL | Tenant-unique branch code. |
| `name` | TEXT | NOT NULL | Branch display name. |
| `timezone` | TEXT | NOT NULL | IANA timezone for shift/business day. |
| `status` | TEXT | CHECK | `ACTIVE` or `INACTIVE`. |
| `created_at` | TIMESTAMPTZ | NOT NULL | Creation time in UTC. |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Last branch update in UTC. |

#### `memberships`

| Column | Type | Constraints | Description/comment |
|---|---|---|---|
| `id` | UUID | PK | Membership identifier. |
| `tenant_id` | UUID | NOT NULL, index | Tenant in which the user has access. |
| `branch_id` | UUID | NULL, FK-local | Assigned branch; null means tenant-wide owner scope. |
| `cognito_subject` | TEXT | NOT NULL | Cognito `sub` for the user. |
| `role` | TEXT | CHECK | `OWNER`, `MANAGER`, `CASHIER`, `KITCHEN`. |
| `status` | TEXT | CHECK | `ACTIVE` or `REVOKED`. |
| `created_at` | TIMESTAMPTZ | NOT NULL | Membership creation time. |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Membership update time. |

### Catalog Schema

#### `catalog_items`

| Column | Type | Constraints | Description/comment |
|---|---|---|---|
| `id` | UUID | PK | Catalog item identifier. |
| `tenant_id` | UUID | NOT NULL, index | Owning tenant. |
| `sku` | TEXT | NOT NULL | Tenant-unique item SKU. |
| `category` | TEXT | NOT NULL | Menu category label for MVP. |
| `name` | TEXT | NOT NULL | Customer-facing item name. |
| `description` | TEXT | NULL | Optional item description. |
| `status` | TEXT | CHECK | `ACTIVE` or `INACTIVE`. |
| `version` | BIGINT | NOT NULL | Optimistic concurrency version. |
| `created_at` | TIMESTAMPTZ | NOT NULL | Item creation time. |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Last item update time. |

#### `catalog_variants`

| Column | Type | Constraints | Description/comment |
|---|---|---|---|
| `id` | UUID | PK | Variant identifier. |
| `tenant_id` | UUID | NOT NULL, index | Owning tenant. |
| `item_id` | UUID | NOT NULL, index | Parent catalog item. |
| `name` | TEXT | NOT NULL | Variant/size name. |
| `price_minor` | BIGINT | NOT NULL, CHECK >= 0 | Current price in integer minor units. |
| `currency` | TEXT | NOT NULL | ISO currency code, seeded as `VND`. |
| `status` | TEXT | CHECK | `ACTIVE` or `INACTIVE`. |
| `created_at` | TIMESTAMPTZ | NOT NULL | Variant creation time. |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Last variant update time. |

#### `modifiers` and `item_modifiers`

`modifiers` columns: `id` UUID PK (modifier identifier), `tenant_id` UUID NOT NULL (owning tenant), `name` TEXT NOT NULL (display name), `price_delta_minor` BIGINT NOT NULL (price adjustment), `status` TEXT CHECK (active/inactive), `created_at` TIMESTAMPTZ NOT NULL (creation time), `updated_at` TIMESTAMPTZ NOT NULL (last update time).

`item_modifiers` columns: `item_id` UUID PK-part (catalog item), `modifier_id` UUID PK-part (allowed modifier), `tenant_id` UUID NOT NULL (owning tenant for defense-in-depth), `created_at` TIMESTAMPTZ NOT NULL (association creation time).

### Order and Reliability Schema

#### `orders`

| Column | Type | Constraints | Description/comment |
|---|---|---|---|
| `id` | UUID | PK | Order identifier. |
| `tenant_id` | UUID | NOT NULL, index | Owning tenant. |
| `branch_id` | UUID | NOT NULL, index | Branch where order is created. |
| `shift_id` | UUID | NOT NULL | Open shift used by the order. |
| `order_number` | TEXT | NOT NULL | Human-readable branch-scoped order number. |
| `service_mode` | TEXT | CHECK | `COUNTER`, `TAKEAWAY`, `TABLE`. |
| `table_reference` | TEXT | NULL | Optional free-form table reference. |
| `customer_id` | UUID | NULL | Loyalty customer reference. |
| `status` | TEXT | CHECK | `DRAFT`, `CHECKOUT_PENDING`, `PAID`, `CHECKOUT_FAILED`, `CANCELLED`. |
| `subtotal_minor` | BIGINT | NOT NULL | Sum before VAT/discount. |
| `vat_minor` | BIGINT | NOT NULL | VAT amount after deterministic rounding. |
| `discount_minor` | BIGINT | NOT NULL | Discount amount, zero in the base P0 path. |
| `grand_total_minor` | BIGINT | NOT NULL | Amount required for payment. |
| `currency` | TEXT | NOT NULL | Currency code, `VND` for MVP. |
| `version` | BIGINT | NOT NULL | Optimistic concurrency version. |
| `created_by` | UUID | NOT NULL | Cognito subject/membership actor that created the order. |
| `created_at` | TIMESTAMPTZ | NOT NULL | Creation time. |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Last state update time. |

#### `order_lines`

| Column | Type | Constraints | Description/comment |
|---|---|---|---|
| `id` | UUID | PK | Order line identifier. |
| `order_id` | UUID | NOT NULL, index | Parent order in Order DB. |
| `catalog_item_id` | UUID | NOT NULL | Catalog item reference. |
| `variant_id` | UUID | NULL | Selected size/variant reference. |
| `item_name_snapshot` | TEXT | NOT NULL | Name at time of order. |
| `unit_price_minor` | BIGINT | NOT NULL | Price at time of order. |
| `quantity` | INTEGER | NOT NULL, CHECK > 0 | Number of units. |
| `vat_rate_bps` | INTEGER | NOT NULL | VAT rate in basis points. |
| `line_total_minor` | BIGINT | NOT NULL | Quantity and modifier total after rounding. |
| `note` | TEXT | NULL | Sanitized line note. |
| `status` | TEXT | CHECK | `ACTIVE` or `CANCELLED`. |
| `created_at` | TIMESTAMPTZ | NOT NULL | Line creation time. |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Last line update time. |

#### `outbox_messages` and `idempotency_records`

`outbox_messages`: `id` UUID PK (outbox record), `aggregate_type` TEXT (aggregate category), `aggregate_id` UUID (aggregate identifier), `event_type` TEXT (versioned event name), `event_version` INTEGER (schema version), `payload` JSONB (validated event envelope), `tenant_id` UUID (scope), `branch_id` UUID NULL (branch scope), `correlation_id` UUID (trace correlation), `published_at` TIMESTAMPTZ NULL (broker acknowledgement time), `attempt_count` INTEGER NOT NULL (publish attempts), `last_error` TEXT NULL (sanitized publish error), `created_at` TIMESTAMPTZ NOT NULL (outbox creation time).

`idempotency_records`: `id` UUID PK (record identifier), `tenant_id` UUID (scope), `operation` TEXT (command name), `idempotency_key` TEXT (client/provider retry key), `request_hash` TEXT (canonical request digest), `response_status` INTEGER (original HTTP status), `response_body` JSONB (original safe response), `resource_id` UUID NULL (created/updated resource), `expires_at` TIMESTAMPTZ (retention deadline), `created_at` TIMESTAMPTZ (record creation time).

### Payment Schema

#### `payment_attempts`

| Column | Type | Constraints | Description/comment |
|---|---|---|---|
| `id` | UUID | PK | Internal payment attempt identifier. |
| `tenant_id` | UUID | NOT NULL, index | Owning tenant. |
| `branch_id` | UUID | NOT NULL | Branch of the order. |
| `order_id` | UUID | NOT NULL, unique per active attempt | Referenced order. |
| `method` | TEXT | CHECK | `CASH` or `VNPAY_QR`. |
| `status` | TEXT | CHECK | `PENDING`, `PAID`, `FAILED`, `EXPIRED`, `CANCELLED`. |
| `amount_minor` | BIGINT | NOT NULL | Amount expected from provider/customer. |
| `currency` | TEXT | NOT NULL | Currency code. |
| `provider_transaction_id` | TEXT | NULL, unique when present | External provider transaction ID. |
| `idempotency_key` | TEXT | NOT NULL | Saga/client command retry key. |
| `expires_at` | TIMESTAMPTZ | NULL | Pending payment expiry. |
| `paid_at` | TIMESTAMPTZ | NULL | Provider-confirmed payment time. |
| `created_at` | TIMESTAMPTZ | NOT NULL | Attempt creation time. |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Last state update time. |

#### `provider_callbacks`

`provider_callbacks`: `id` UUID PK (callback record), `payment_id` UUID (internal payment attempt), `provider` TEXT (provider name), `provider_event_key` TEXT (deduplication key), `payload_hash` TEXT (canonical payload digest), `signature_valid` BOOLEAN (verification result), `provider_response_code` TEXT (provider status), `raw_payload_encrypted` JSONB NULL (encrypted/minimized payload retained for reconciliation), `processed_at` TIMESTAMPTZ NULL (business processing time), `received_at` TIMESTAMPTZ (receipt time).

### Inventory Schema

| Table | Columns with required remarks |
|---|---|
| `ingredients` | `id` UUID PK (ingredient ID), `tenant_id` UUID (owner), `code` TEXT (tenant-unique ingredient code), `name` TEXT (display name), `base_unit` TEXT (one of g/ml/unit), `status` TEXT (active/inactive/archive lifecycle), `version` BIGINT (optimistic metadata version), `created_at` TIMESTAMPTZ (creation), `updated_at` TIMESTAMPTZ (last update). |
| `recipe_versions` | `id` UUID PK (recipe version ID), `tenant_id` UUID (owner), `catalog_item_id` UUID (opaque Catalog item reference), `variant_id` UUID (opaque Catalog variant reference), `version` INTEGER (monotonic recipe version), `status` TEXT (draft/active/inactive/archived), `activated_at` TIMESTAMPTZ NULL (activation time), `created_at` TIMESTAMPTZ (creation), `updated_at` TIMESTAMPTZ (last update). |
| `recipe_lines` | `id` UUID PK (recipe line ID), `recipe_version_id` UUID (parent recipe version), `ingredient_id` UUID (required ingredient reference), `modifier_option_id` UUID NULL (optional modifier scope), `quantity_per_sale` NUMERIC(18,3) (base-unit quantity per sale), `waste_factor` NUMERIC(18,3) (minimum 1.000 multiplier), `base_unit` TEXT (unit snapshot), `created_at` TIMESTAMPTZ (creation). |
| `stock_balances` | `id` UUID PK (balance row ID), `tenant_id` UUID (owner), `branch_id` UUID (stock branch), `ingredient_id` UUID (ingredient reference), `on_hand` NUMERIC(18,3) (physical projected quantity), `reserved` NUMERIC(18,3) (held quantity), `available` NUMERIC(18,3) (on_hand minus reserved), `version` BIGINT (pessimistic mutation version), `created_at` TIMESTAMPTZ (creation), `updated_at` TIMESTAMPTZ (last projection update). |
| `reservations` | `id` UUID PK (reservation ID), `tenant_id` UUID (owner), `branch_id` UUID (stock branch), `order_id` UUID (order reference), `checkout_attempt_id` UUID (unique checkout scope), `saga_id` UUID (workflow reference), `status` TEXT (reserved/committed/released/expired/reconciliation_required), `expires_at` TIMESTAMPTZ (release deadline), `request_hash` TEXT (canonical request identity), `idempotency_key` TEXT (retry key), `reservation_version` BIGINT (state version), `terminal_at` TIMESTAMPTZ NULL (terminal transition time), `created_at` TIMESTAMPTZ (creation), `updated_at` TIMESTAMPTZ (last state update). |
| `reservation_lines` | `id` UUID PK (reservation line ID), `reservation_id` UUID (parent reservation), `ingredient_id` UUID (ingredient reference), `quantity` NUMERIC(18,3) (expanded held quantity), `recipe_snapshot` JSONB (immutable recipe/demand snapshot), `created_at` TIMESTAMPTZ (creation). |
| `stock_ledger` | `id` UUID PK (stock movement ID), `tenant_id` UUID (owner), `branch_id` UUID (stock branch), `ingredient_id` UUID (ingredient reference), `movement_type` TEXT (opening_balance/receipt/sale_committed/count_adjustment/waste/return), `quantity_delta` NUMERIC(18,3) (signed base-unit movement), `base_unit` TEXT (unit snapshot), `source_type` TEXT (order/reservation/receipt/count/manual), `source_id` UUID (source reference), `reason` TEXT NULL (operator/business reason), `actor_id` UUID NULL (human/service actor), `correlation_id` UUID (trace reference), `occurred_at` TIMESTAMPTZ (business occurrence), `idempotency_key` TEXT (deduplication key), `created_at` TIMESTAMPTZ (persistence time). |

### Loyalty Schema

| Table | Columns with required remarks |
|---|---|
| `customers` | `id` UUID PK (customer ID), `tenant_id` UUID (owner), `phone_hash` TEXT (normalized protected lookup key), `phone_ciphertext` TEXT (encrypted phone value), `display_phone` TEXT (masked phone for UI), `status` TEXT (active/blocked), `created_at` TIMESTAMPTZ (creation), `updated_at` TIMESTAMPTZ (last update). |
| `loyalty_accounts` | `id` UUID PK (account ID), `tenant_id` UUID (owner), `customer_id` UUID (customer reference), `balance_points` BIGINT (projected current balance), `version` BIGINT (concurrency version), `created_at` TIMESTAMPTZ (creation), `updated_at` TIMESTAMPTZ (last projection update). |
| `loyalty_ledger_entries` | `id` UUID PK (ledger entry), `tenant_id` UUID (owner), `customer_id` UUID (customer reference), `account_id` UUID (account reference), `entry_type` TEXT (accrual/redemption/reversal/expiry), `points_delta` BIGINT (signed point change), `source_type` TEXT (order/payment/reversal), `source_id` UUID (source transaction), `reverses_entry_id` UUID NULL (original entry for reversal), `occurred_at` TIMESTAMPTZ (business occurrence), `created_at` TIMESTAMPTZ (persisted time). |

### Shift, Recommendation, Reporting and Audit Schema

| Table | Columns with required remarks |
|---|---|
| `shifts` | `id` UUID PK (shift ID), `tenant_id` UUID (owner), `branch_id` UUID (branch), `opened_by` UUID (opening actor), `status` TEXT (active/closed), `opening_cash_minor` BIGINT (opening cash), `closing_cash_minor` BIGINT NULL (closing cash), `opened_at` TIMESTAMPTZ (opening time), `closed_at` TIMESTAMPTZ NULL (closing time), `created_at` TIMESTAMPTZ (record creation), `updated_at` TIMESTAMPTZ (last update). |
| `association_rules` | `id` UUID PK (rule ID), `tenant_id` UUID (owner), `antecedent_item_id` UUID (basket item), `consequent_item_id` UUID (suggested item), `confidence_bps` INTEGER (rule confidence), `status` TEXT (active/inactive), `created_at` TIMESTAMPTZ (creation), `updated_at` TIMESTAMPTZ (last update). |
| `suggestion_events` | `id` UUID PK (suggestion event ID), `tenant_id` UUID (owner), `branch_id` UUID (branch), `order_id` UUID (cart order), `suggestion_id` UUID (impression/acceptance group), `event_type` TEXT (shown/accepted), `item_id` UUID (suggested item), `rule_id` UUID NULL (rule used), `correlation_id` UUID (trace), `occurred_at` TIMESTAMPTZ (event time). |
| `sales_daily` | `tenant_id` UUID PK-part (owner), `branch_id` UUID PK-part (branch), `business_date` DATE PK-part (local business date), `order_count` BIGINT (paid order count), `gross_minor` BIGINT (gross amount), `vat_minor` BIGINT (VAT amount), `net_minor` BIGINT (net amount), `currency` TEXT (currency), `projection_version` BIGINT (rebuild/version marker), `last_updated_at` TIMESTAMPTZ (projection freshness). |
| `top_items` | `tenant_id` UUID PK-part (owner), `branch_id` UUID PK-part (branch), `business_date` DATE PK-part (local date), `item_id` UUID PK-part (item), `item_name` TEXT (name snapshot), `quantity_sold` BIGINT (units sold), `revenue_minor` BIGINT (revenue), `last_updated_at` TIMESTAMPTZ (projection freshness). |
| `audit_events` | `id` UUID PK (audit event), `tenant_id` UUID (owner), `branch_id` UUID NULL (branch scope), `actor_id` UUID NULL (human/service actor), `action` TEXT (business/security action), `resource_type` TEXT (resource category), `resource_id` UUID NULL (resource reference), `reason` TEXT NULL (operator reason), `before_summary` JSONB NULL (redacted prior state), `after_summary` JSONB NULL (redacted new state), `correlation_id` UUID (trace), `occurred_at` TIMESTAMPTZ (business event time), `created_at` TIMESTAMPTZ (audit persistence time). |

### Index and Constraint Contract

| Owner | Index/constraint | Columns | Purpose |
|---|---|---|---|
| Identity | `uq_branches_tenant_code` | `tenant_id, code` | Unique branch within tenant |
| Identity | `uq_membership_scope` | `tenant_id, branch_id, cognito_subject, role` | Prevent duplicate active scope |
| Catalog | `uq_catalog_sku` | `tenant_id, sku` | Stable item lookup |
| Order | `uq_order_number_branch` | `tenant_id, branch_id, order_number` | Human order number uniqueness |
| Order | `idx_orders_scope_status_time` | `tenant_id, branch_id, status, created_at DESC` | Branch order list |
| Reliability | `uq_idempotency_operation_key` | `tenant_id, operation, idempotency_key` | Exactly one stored result per retry key |
| Payment | `uq_provider_transaction` | `provider_transaction_id` | Prevent duplicate provider settlement |
| Payment | `uq_callback_provider_key` | `provider, provider_event_key` | Deduplicate IPN |
| Inventory | `idx_reservations_expiry` | `tenant_id, branch_id, status, expires_at` | Release expired reservations |
| Inventory | `uq_inventory_active_recipe` | `tenant_id, catalog_item_id, variant_id, status` (partial for ACTIVE) | At most one active recipe version |
| Inventory | `uq_inventory_checkout_reservation` | `tenant_id, checkout_attempt_id` | One business reservation per checkout attempt |
| Inventory | `uq_inventory_ledger_source` | `tenant_id, branch_id, source_type, source_id, idempotency_key` | Prevent duplicate stock movement |
| Inventory | `uq_inventory_balance` | `tenant_id, branch_id, ingredient_id` | One lockable balance row per branch ingredient |
| Loyalty | `uq_customer_phone` | `tenant_id, phone_hash` | One profile per normalized phone |
| Loyalty | `uq_loyalty_account_customer` | `tenant_id, customer_id` | One active loyalty account per customer |
| Reporting | `pk_sales_daily` | `tenant_id, branch_id, business_date` | Deterministic upsert projection |
| Audit | `idx_audit_scope_time` | `tenant_id, branch_id, occurred_at DESC` | Scope-safe audit browsing |

## Integration Contracts

### Event Envelope

```json
{
  "eventId": "01J...",
  "eventType": "PaymentPaid",
  "eventVersion": 1,
  "occurredAt": "2026-10-12T07:00:00Z",
  "tenantId": "01J...",
  "branchId": "01J...",
  "aggregateType": "Payment",
  "aggregateId": "01J...",
  "correlationId": "01J...",
  "causationId": "01J...",
  "producer": "payment-service",
  "payload": {}
}
```

Required invariants:

- `eventId` is globally unique and is the default consumer deduplication key.
- `eventType` and `eventVersion` are immutable after publish.
- `tenantId` is mandatory for tenant data; `branchId` is mandatory for branch-scoped data.
- Consumer acknowledges only after its local transaction commits.
- Unknown event versions are quarantined/DLQ, not silently interpreted as an older schema.

### Event Payloads

| Event | Required payload fields | Producer → consumers |
|---|---|---|
| `InventoryReserved.v1` | `reservationId`, `orderId`, `checkoutAttemptId`, `sagaId`, `branchId`, `expiresAt`, `lines[]`, `recipeSnapshots[]` | Inventory → Saga, Order, Audit |
| `InventoryReservationCommitted.v1` | `reservationId`, `orderId`, `checkoutAttemptId`, `sagaId`, `branchId`, `paymentId`, `lines[]`, `committedAt`, `ledgerEntryIds[]` | Inventory → Saga, Order, Audit |
| `InventoryReservationReleased.v1` | `reservationId`, `orderId`, `checkoutAttemptId`, `sagaId`, `branchId`, `reasonCode`, `releasedAt` | Inventory → Saga, Order, Audit |
| `InventoryReservationExpired.v1` | `reservationId`, `orderId`, `checkoutAttemptId`, `sagaId`, `branchId`, `expiredAt`, `reasonCode` | Inventory → Saga, Order, Audit |
| `InventoryStockChanged.v1` | `ledgerEntryId`, `branchId`, `ingredientId`, `movementType`, `quantityDelta`, `onHand`, `reserved`, `available`, `sourceType`, `sourceId` | Inventory → Reporting, Audit |
| `InventoryLowStockDetected.v1` | `alertId`, `branchId`, `ingredientId`, `available`, `threshold`, `detectedAt`, `alertState` | Inventory → Reporting, Notification, Audit |
| `InventoryReconciliationRequired.v1` | `reconciliationCaseId`, `reservationId`, `orderId`, `checkoutAttemptId`, `sagaId`, `branchId`, `paymentId`, `reasonCode`, `detectedAt` | Inventory → Saga, Payment, Audit |
| `PaymentInitiated.v1` | `paymentId`, `orderId`, `method`, `amountMinor`, `currency`, `expiresAt` | Payment → Saga, Audit |
| `PaymentPaid.v1` | `paymentId`, `orderId`, `provider`, `providerTransactionId`, `amountMinor`, `paidAt` | Payment → Saga, Audit; Saga then calls Inventory commit |
| `PaymentFailed.v1` | `paymentId`, `orderId`, `reasonCode`, `retryable`, `failedAt` | Payment → Saga, Order, Audit |
| `OrderPaid.v1` | `orderId`, `branchId`, `customerId`, `lines[]`, `totals`, `paidAt` | Order → Loyalty, Reporting, Audit; canonical paid-order fact |
| `LoyaltyPointsAccrued.v1` | `ledgerEntryId`, `customerId`, `orderId`, `pointsDelta`, `balanceAfter` | Loyalty → Reporting, Audit |
| `LoyaltyPointsReversed.v1` | `ledgerEntryId`, `reversesEntryId`, `customerId`, `pointsDelta`, `reason` | Loyalty → Reporting, Audit |
| `SuggestionShown.v1` | `suggestionId`, `orderId`, `itemId`, `ruleId`, `shownAt` | Recommendation → Reporting, Audit |
| `SuggestionAccepted.v1` | `suggestionId`, `orderId`, `itemId`, `acceptedAt`, `attributedValueMinor` | Recommendation → Reporting, Audit |
| `ShiftOpened.v1` / `ShiftClosed.v1` | `shiftId`, `branchId`, `actorId`, `openingCashMinor`/`closingCashMinor`, `occurredAt` | Shift → Reporting, Audit |
| `AuditRecorded.v1` | `auditEventId`, `action`, `resourceType`, `resourceId`, `actorId`, `reason` | Any service → Audit |

### Queue and Retry Contract

| Queue | Consumer | Retry | DLQ trigger |
|---|---|---|---|
| `pos-loyalty-events` | Loyalty | 5 attempts, exponential backoff | schema/validation failure or exhausted transient retries |
| `pos-reporting-events` | Reporting | 8 attempts, exponential backoff | projection failure after bounded retries |
| `pos-recommendation-events` | Recommendation | 5 attempts | malformed catalog/order event |
| `pos-audit-events` | Audit | 8 attempts | persistent storage or schema failure |
| `pos-saga-events` | Saga callback handler | 8 attempts with strict duplicate guard | unresolved compensation/callback correlation |

Messages are at-least-once. Every consumer stores processed `eventId` or uses a unique source-event key before acknowledging. DLQ replay requires operator authorization and preserves the original `correlationId`.

## Shared Types and Enumerations

```text
Role = OWNER | MANAGER | CASHIER | KITCHEN
ServiceMode = COUNTER | TAKEAWAY | TABLE
OrderStatus = DRAFT | CHECKOUT_PENDING | PAID | CHECKOUT_FAILED | CANCELLED
PaymentMethod = CASH | VNPAY_QR
PaymentStatus = PENDING | PAID | FAILED | EXPIRED | CANCELLED
ReservationStatus = RESERVED | COMMITTED | RELEASED | EXPIRED | RECONCILIATION_REQUIRED
LedgerEntryType = ACCRUAL | REDEMPTION | REVERSAL | EXPIRY
Currency = VND
```

Shared types must remain contract-only. Domain-specific Java entities stay inside their owning service.

## Migration Plan

### Tool and Layout

- Tool: Flyway integrated with each Spring Boot service.
- Each service owns its migration history and database credentials.
- Migration location: `classpath:db/migration/<service>/`.
- Naming: `V1__create_core_tables.sql`, `V2__create_outbox_and_idempotency.sql`, `V3__add_indexes_and_constraints.sql`, then incremental versioned migrations.
- Repeatable migrations are reserved for non-authoritative reporting views/materialized projections.
- Every new column includes `COMMENT ON COLUMN`; descriptions must match this document.

### Migration Order per Service

1. Create core tables and enum/check constraints.
2. Create audit/common timestamps and tenant/branch indexes.
3. Create outbox/idempotency tables for event-producing services.
4. Create unique constraints and query indexes.
5. Seed only deterministic reference/config data: roles, currency, demo tenant/branches, catalog recipes and association rules in a separate seed profile.
6. Validate migrations in an empty database and a data-copy database before deployment.

### Safety Rules

- Migrations are forward-only in shared environments; destructive changes use expand → migrate → contract.
- No `clean` operation is allowed in CI/staging/production profiles.
- Each migration is transactional where PostgreSQL supports it and has a documented verification query.
- Rollback is a forward corrective migration plus database backup/restore procedure; business ledger/order history is never deleted as rollback.

## Contract Testing and Compatibility

- Generate Angular typed clients from committed OpenAPI documents.
- Validate Spring Boot controllers against OpenAPI in CI.
- Validate event payloads against versioned JSON Schemas from AsyncAPI.
- Consumer-driven contract tests cover each producer/consumer event pair.
- Test cases must include missing/extra fields, wrong tenant/branch, duplicate idempotency key, duplicate event, unknown event version and stale `If-Match`.
- CI blocks a breaking change unless a new API/event major version and migration/consumer plan are included.

## Domain Skills Referenced

- **api-design:** REST resource naming, status/error format, pagination, JWT authentication, rate limiting, caching and versioning.
- **database-design:** PostgreSQL types, UUID v7, index/partition policy, mandatory column descriptions and Flyway migration conventions.
- **log-processing:** safe correlation/entity identifiers and prohibition on logging secrets, tokens, phone/card-like data or full payloads.

## Approval

- **Status:** Approved by user
- **Next phase:** Completed; Design Finalization approved with warnings
