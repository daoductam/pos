Started at: 2026/10/13 00:00:00
Finished at: 2026/10/13 00:00:00
Total time: 0 minutes
---

# Module 10 — Báo cáo & Dashboard: API & Data Contracts

**Ngày:** 2026-10-13  
**Trạng thái:** Approved with warnings — user approved  
**Vision:** [Reporting Vision](./2026-10-13-vision.md)  
**Requirements:** [Reporting Requirements](./2026-10-13-requirements.md)  
**Architecture:** [Reporting Architecture](./2026-10-13-architecture.md)  
**Sequence diagrams:** [Reporting Sequences](./2026-10-13-sequence-diagrams.adoc)

Tài liệu này là nguồn chuẩn để sinh public/internal OpenAPI 3.1, AsyncAPI/JSON Schemas, PostgreSQL/Flyway migrations, metric fixtures và generated Angular client types. Reporting sở hữu read models nhưng không sở hữu source business facts.

## 1. Contract conventions

### 1.1 Encoding and identity

- Public REST dưới `/api/v1`; internal REST dưới `/internal/v1`.
- EventBridge/SQS delivery at-least-once; event envelope dùng `eventType` + `eventVersion`.
- IDs là UUID v7 strings; timestamps là ISO-8601 UTC.
- `businessDate` là local date theo server-resolved IANA timezone policy.
- MVP chỉ hỗ trợ `VND`; money là signed `int64` minor units + `currency`.
- Ratios/percentages là decimal strings; không dùng binary floating point cho KPI.
- Breaking API/event change tạo major version; additive fields phải backward-compatible.

### 1.2 Headers

| Header | Public | Internal | Meaning |
|---|---:|---:|---|
| `Authorization` | required | required | Cognito JWT hoặc workload credential/mTLS |
| `X-Correlation-Id` | recommended | required | Trace request/event/query |
| `X-Causation-Id` | no | recommended | Upstream event/command reference |
| `Idempotency-Key` | no for GET | required for rebuild command | Tenant + operation + actor/workload scope |
| `If-None-Match` | optional | no | Safe conditional response after authorization |
| `Cache-Control` | server-controlled | server-controlled | Public report data không được browser cache quá policy |

Client không được gửi `tenantId`, role, membership version hoặc branch ownership như authority. `branchIds` chỉ là requested filter; server/Identity quyết định scope thật.

### 1.3 Envelopes

```json
{
  "data": {},
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J...",
    "nextCursor": null
  }
}
```

```json
{
  "error": {
    "code": "scope_not_allowed",
    "message": "The requested report scope is not available",
    "details": [],
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

Error messages không tiết lộ tenant/branch ngoài scope, employee/customer/payment identity, SQL, table name, raw event hay internal stack trace.

### 1.4 Pagination and sorting

- Collection endpoints dùng cursor pagination, default `20`, max `100`.
- Cursor encode stable sort tuple + scope/filter hash + projection watermark; server reject cursor dùng khác filter/scope.
- Stable tie-break luôn có opaque UUID/key ASC.
- Report series theo date/hour có thể trả bounded array không cursor nếu range/row limit đã được kiểm soát.

### 1.5 Error taxonomy

| HTTP | Code | Meaning | Retry |
|---:|---|---|---:|
| 200 | — | Read response, including `NO_DATA` | no |
| 304 | — | Authorized conditional response unchanged | no |
| 401 | `unauthorized` | JWT/workload invalid | refresh/fix auth |
| 403 | `forbidden` | Role/permission not allowed | no |
| 409 | `scope_not_allowed` | Requested branch outside resolved scope | no |
| 409 | `report_not_ready` | Projection not complete for required source | bounded retry |
| 409 | `rebuild_conflict` | Same scope/version already running | inspect operation |
| 422 | `invalid_report_filter` | Invalid date/range/dimension/limit | no |
| 422 | `unsupported_currency` | Mixed/non-VND aggregate in MVP | no |
| 429 | `rate_limited` | User/tenant/report limit | Retry-After |
| 503 | `reporting_unavailable` | Read model/cache unavailable | bounded retry |

`NO_DATA`, `STALE`, `PARTIAL` and `UNAVAILABLE` are safe domain states in `dataQuality`; no-data is not mapped to zero.

## 2. Authorization and scope contract

### 2.1 Permission catalogue amendment

Identity must add:

| Permission | Owner | Manager | Cashier | Kitchen | Scope |
|---|---:|---:|---:|---:|---|
| `report:read` | yes | assigned branches | no | no | Tenant or assigned branch |
| `reporting:operate` | restricted ops | no | no | no | Internal rebuild/replay only |

Identity remains online authority. Reporting calls parent `POST /internal/v1/authorization-decisions` for every protected public request.

### 2.2 Resolved scope

```json
{
  "scopeType": "BRANCH",
  "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789001",
  "branchIds": ["0192b2d0-7f1d-7abc-8d2c-123456789002"],
  "role": "MANAGER",
  "permission": "report:read",
  "membershipVersion": 12,
  "authorizationVersion": 7,
  "resolvedAt": "2026-10-13T08:00:00Z",
  "expiresAt": "2026-10-13T08:05:00Z"
}
```

Rules:

- `TENANT` scope is Owner-only and explicit; wildcard branch IDs are invalid.
- Manager branch set must be a subset of current membership.
- A public report request cannot widen an existing scope through query params, cursor or drill-down ID.
- Cache key contains `authorizationVersion` and scope hash; authorization is checked before cache lookup.
- Reporting repository applies tenant/branch predicates even after Identity approval.

## 3. Common report schemas

### 3.1 Normalized period

```json
{
  "from": "2026-10-01",
  "to": "2026-10-13",
  "timezone": "Asia/Ho_Chi_Minh",
  "utcFrom": "2026-09-30T17:00:00Z",
  "utcToExclusive": "2026-10-13T17:00:00Z",
  "businessDateMode": "BRANCH_LOCAL"
}
```

Single branch uses branch-local timezone. Multi-branch uses tenant reporting timezone. Browser timezone is never used as authority.

### 3.2 `DataQualityEnvelope`

| Field | Type | Required | Rule |
|---|---|---:|---|
| `status` | enum | yes | `FRESH`, `STALE`, `PARTIAL`, `UNAVAILABLE`, `NO_DATA` |
| `freshnessLagSeconds` | int64 | yes | Non-negative; calculated from source watermark |
| `completenessStatus` | enum | yes | `COMPLETE`, `PARTIAL`, `MISSING` |
| `watermark` | string | yes | Opaque source checkpoint |
| `watermarkVersion` | int64 | yes | Monotonic within source/scope/projection |
| `lastUpdatedAt` | timestamp | yes | Projection update UTC |
| `projectionVersion` | int64 | yes | Current materialized projection version |
| `missingSources` | string[] | yes | Safe source names only |
| `calculationVersion` | string | yes | Metric dictionary/version |
| `asOfWatermark` | string | yes | Current corrected view checkpoint |

State rules:

- `FRESH`: lag ≤60 seconds and required sources complete.
- `STALE`: lag >60 seconds while read model remains available; >300 seconds raises an escalation alert.
- `PARTIAL`: any required source/branch is missing.
- `UNAVAILABLE`: query/read model unavailable.
- `NO_DATA`: valid complete query with no facts in requested period.

### 3.3 `ReportScope`

```json
{
  "type": "BRANCH",
  "branchIds": ["0192b2d0-7f1d-7abc-8d2c-123456789002"]
}
```

Public response may return authorized branch IDs and safe labels only. It never returns foreign branch lists or membership internals.

## 4. Public API contracts

All endpoints require `report:read` and return `{data, meta}`. Supported common query parameters:

| Parameter | Rule |
|---|---|
| `branchIds` | Max 20; server validates subset; omitted means role-resolved default |
| `from`, `to` | ISO date; default 7 days; max 12 months |
| `timezonePolicy` | `SERVER_RESOLVED` only in MVP; client cannot set arbitrary timezone |
| `comparison` | `NONE`, `PREVIOUS_EQUAL_PERIOD`, `SAME_WEEKDAY_LAST_WEEK` where endpoint supports |
| `limit` | 1–100 according to endpoint |
| `cursor` | Cursor returned by same endpoint/filter/scope only |

### 4.1 Endpoint catalogue

| Method | Path | Response | Purpose |
|---|---|---|---|
| GET | `/api/v1/reports/dashboard/overview` | `DashboardOverviewResponse` | KPI widgets and compact summary |
| GET | `/api/v1/reports/sales/daily` | `DailySalesResponse` | Daily revenue/order/AOV series |
| GET | `/api/v1/reports/sales/by-branch` | `BranchSalesResponse` | Branch aggregate/comparison |
| GET | `/api/v1/reports/sales/hourly` | `HourlySalesResponse` | Hour buckets; one branch in MVP |
| GET | `/api/v1/reports/sales/by-employee` | `EmployeeSalesPage` | Checkout employee metrics |
| GET | `/api/v1/reports/top-items` | `TopItemsPage` | Top selling items |
| GET | `/api/v1/reports/promotions/effectiveness` | `PromotionEffectivenessPage` | Promotion version metrics |
| GET | `/api/v1/reports/recommendations/effectiveness` | `RecommendationEffectivenessResponse` | Exposure/acceptance/AOV cohorts |
| GET | `/api/v1/reports/freshness` | `FreshnessResponse` | Projection/source health |

### 4.2 Common response envelope

```json
{
  "data": {
    "scope": {
      "type": "BRANCH",
      "branchIds": ["0192b2d0-7f1d-7abc-8d2c-123456789002"]
    },
    "period": {
      "from": "2026-10-01",
      "to": "2026-10-13",
      "timezone": "Asia/Ho_Chi_Minh"
    },
    "rows": [],
    "dataQuality": {
      "status": "FRESH",
      "freshnessLagSeconds": 20,
      "completenessStatus": "COMPLETE",
      "watermark": "reporting:tenant:v901",
      "watermarkVersion": 901,
      "lastUpdatedAt": "2026-10-13T08:00:00Z",
      "projectionVersion": 12,
      "missingSources": [],
      "calculationVersion": "sales-metric-v1",
      "asOfWatermark": "reporting:tenant:v901"
    }
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J...",
    "nextCursor": null
  }
}
```

### 4.3 `DashboardOverviewResponse`

```json
{
  "data": {
    "kpis": {
      "netSalesMinor": 12400000,
      "grossSalesMinor": 13500000,
      "discountMinor": 1100000,
      "vatMinor": 1127273,
      "paidOrderCount": 120,
      "aovMinor": 103333,
      "currency": "VND"
    },
    "comparisons": {
      "netSalesDeltaMinor": -900000,
      "netSalesDeltaRatio": "-0.0677",
      "comparisonPeriod": {"from": "2026-09-18", "to": "2026-09-30"}
    },
    "dataQuality": {}
  }
}
```

KPI overview must not combine widgets with incompatible watermark/calculation version without exposing the least-fresh/partial state.

### 4.4 Daily/branch/hourly sales

`DailySalesRow`:

```json
{
  "businessDate": "2026-10-12",
  "branchId": "0192...",
  "branchLabel": "Quận 1",
  "paidOrderCount": 42,
  "grossSalesMinor": 4800000,
  "discountMinor": 300000,
  "netSalesMinor": 4500000,
  "vatMinor": 409091,
  "aovMinor": 107143,
  "currency": "VND",
  "calculationVersion": "sales-metric-v1"
}
```

`BranchSalesRow` uses the same metric fields plus `rank`, `deltaMinor`, `deltaRatio` and safe branch label. Foreign branches are omitted, not returned as hidden/unauthorized rows.

`HourlySalesRow`:

```json
{
  "branchId": "0192...",
  "businessDate": "2026-10-12",
  "hourBucket": 18,
  "timezone": "Asia/Ho_Chi_Minh",
  "paidOrderCount": 8,
  "netSalesMinor": 920000,
  "currency": "VND"
}
```

Hour bucket is branch-local for one branch and tenant-local for a multi-branch policy. Exact transaction timestamps are never returned.

### 4.5 Employee sales

```json
{
  "employee": {
    "id": "0192...",
    "displayLabel": "Thu ngân A",
    "statusAtSale": "ACTIVE"
  },
  "paidOrderCount": 42,
  "netSalesMinor": 4500000,
  "aovMinor": 107143,
  "currency": "VND",
  "attributionVersion": "checkout-employee-v1",
  "qualityCode": null
}
```

Missing/mismatched attribution uses `employee.id=UNASSIGNED`, safe label `Chưa xác định`, and `qualityCode=missing_checkout_attribution`; it remains in tenant/branch totals.

### 4.6 Top items

```json
{
  "itemId": "0192...",
  "itemLabel": "Gà rán",
  "itemStatus": "ACTIVE",
  "rank": 1,
  "quantitySold": 120,
  "revenueMinor": 3600000,
  "currency": "VND"
}
```

`revenueMinor` is net line revenue after discount allocation and excludes VAT/fee/tip. Sort allowlist: `REVENUE` or `QUANTITY`; tie-break is stable by item ID.

### 4.7 Promotion effectiveness

```json
{
  "promotionVersionId": "0192...",
  "promotionLabel": "Happy Hour 10% v3",
  "ruleVersion": 3,
  "conflictGroup": "happy-hour",
  "appliedOrderCount": 52,
  "grossSalesMinor": 5200000,
  "discountMinor": 520000,
  "netSalesMinor": 4680000,
  "averageNetOrderMinor": 90000,
  "allocationStatus": "ALLOCATED",
  "currency": "VND"
}
```

Possible `allocationStatus`: `ALLOCATED`, `UNALLOCATED_PROMOTION`, `PARTIAL`. Promotion version is immutable and not merged with another version.

### 4.8 Recommendation effectiveness

```json
{
  "shownCount": 1000,
  "acceptedCount": 180,
  "acceptanceRate": "0.1800",
  "acceptedOrderCount": 160,
  "controlOrderCount": 500,
  "acceptedAovMinor": 118000,
  "controlAovMinor": 103000,
  "aovUpliftMinor": 15000,
  "aovUpliftRatio": "0.1456",
  "currency": "VND",
  "attributionVersion": "suggestion-cohort-v1",
  "interpretation": "OBSERVED_COHORT_DIFFERENCE"
}
```

Multiple accepted suggestions in one order count once in accepted AOV cohort. Acceptance counts remain interaction-level. Missing control denominator returns `NO_DATA`; missing attribution correlation returns `PARTIAL`/pending projection and is not silently included.

### 4.9 Freshness response

`GET /api/v1/reports/freshness` returns one `DataQualityEnvelope` per requested scope/source/projection. It includes safe `missingSources`, lag, watermark and current projection version. It never returns raw queue payload or event body.

## 5. Internal operational contracts

### 5.1 Rebuild API

`POST /internal/v1/reporting/rebuilds`

Request:

```json
{
  "tenantId": "0192...",
  "branchIds": ["0192..."],
  "projectionNames": ["sales_daily", "top_items"],
  "fromBusinessDate": "2026-10-01",
  "toBusinessDate": "2026-10-13",
  "sourceWatermarkFrom": "order:v800",
  "sourceWatermarkTo": "order:v901",
  "reasonCode": "PROJECTION_REPAIR"
}
```

Rules: workload `reporting:operate`, max bounded date range per operation, explicit tenant/branch ownership, required `Idempotency-Key`, and audit reference. Same key/request returns same `operationId`; same key/different request returns `409 idempotency_conflict`.

Response `202`:

```json
{
  "data": {
    "operationId": "0192...",
    "status": "REQUESTED",
    "targetProjectionVersion": 13,
    "pollUrl": "/internal/v1/reporting/rebuilds/0192..."
  },
  "meta": {"requestId": "req_01J...", "correlationId": "cor_01J..."}
}
```

Statuses: `REQUESTED`, `RUNNING`, `PAUSED`, `COMPLETED`, `FAILED`, `CANCELLED`.

### 5.2 Projection health

`GET /internal/v1/reporting/projection-health`

Restricted workload/ops only. Returns source/projection watermark, lag, completeness, DLQ count, current/target version and last error code. It never returns raw event payload.

## 6. Event contracts

### 6.1 Common envelope

```json
{
  "eventId": "0192b2d0-7f1d-7abc-8d2c-123456789050",
  "eventType": "OrderPaid",
  "eventVersion": 1,
  "occurredAt": "2026-10-13T08:00:05Z",
  "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789001",
  "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789002",
  "aggregateType": "Order",
  "aggregateId": "0192b2d0-7f1d-7abc-8d2c-123456789011",
  "aggregateVersion": 4,
  "sourceVersion": 901,
  "correlationId": "0192b2d0-7f1d-7abc-8d2c-123456789020",
  "causationId": "0192b2d0-7f1d-7abc-8d2c-123456789021",
  "producer": "order-service",
  "payload": {}
}
```

Required invariants:

- `eventId` unique and stable across retries/replay.
- `tenantId` mandatory; `branchId` mandatory for branch-scoped facts.
- `sourceVersion` is monotonic within producer aggregate/source policy; Reporting never orders only by arrival time.
- Consumer ack happens after inbox/read-model/watermark transaction commits.
- Unknown event version is quarantined/DLQ.

### 6.2 `OrderPaid.v1` Reporting amendment

Existing OrderPaid remains canonical for revenue. The Reporting additive amendment requires:

```json
{
  "orderId": "0192...",
  "branchId": "0192...",
  "businessDate": "2026-10-12",
  "businessTimezone": "Asia/Ho_Chi_Minh",
  "checkoutActor": {
    "employeeId": "0192...",
    "displayLabel": "Thu ngân A",
    "role": "CASHIER",
    "membershipVersion": 12,
    "attributionVersion": "checkout-employee-v1"
  },
  "totals": {
    "subtotalMinor": 5200000,
    "discountMinor": 520000,
    "vatMinor": 468000,
    "grandTotalMinor": 5148000,
    "currency": "VND"
  },
  "lines": [],
  "appliedPromotions": [
    {
      "promotionVersionId": "0192...",
      "ruleVersion": 3,
      "conflictGroup": "happy-hour",
      "discountAllocatedMinor": 520000,
      "allocationStatus": "ALLOCATED"
    }
  ],
  "paidAt": "2026-10-12T10:00:00Z"
}
```

`customerId`, phone and customer profile fields are not consumed by Reporting metrics. If `checkoutActor` is absent/invalid, Reporting uses `UNASSIGNED/UNKNOWN` and retains total revenue.

### 6.3 Correction/refund event

Parent Order must publish a versioned business fact such as `OrderRefunded.v1` or `OrderCorrected.v1` with:

- tenant/branch/order reference and source event ID;
- correction/refund ID and source/aggregate version;
- affected amounts/lines and effective business date;
- reason/status and original paid reference;
- correlation/causation IDs.

Payment provider facts alone cannot change Reporting revenue. Late Payment settlement that does not produce a canonical OrderPaid/correction event is excluded.

### 6.4 Recommendation interaction events

`SuggestionShown.v1` minimum:

```json
{
  "interactionId": "0192...",
  "suggestionId": "0192...",
  "itemId": "0192...",
  "ruleOrModelVersion": "association-v4",
  "orderId": "0192...",
  "cartSessionId": "0192...",
  "renderedAt": "2026-10-12T10:00:00Z"
}
```

`SuggestionAccepted.v1` adds `acceptedInteractionId`, `shownInteractionId`, `orderLineId`/order version evidence and `acceptedAt`. Reporting requires shown → accepted ordering and same order/cart context. Accepted event is attribution only, never revenue truth.

### 6.5 Metadata events

Identity/branch metadata events must provide safe branch label/timezone/status and employee label/status/version snapshots. Reporting stores only the fields needed for historical display and authorized grouping.

### 6.6 Produced Reporting events

| Event | Payload | Consumers |
|---|---|---|
| `ReportProjectionWatermarkUpdated.v1` | tenant/scope, projection, watermark/version, status, lag, projection version | Dashboard hints, AI Assistant, ops |
| `ReportProjectionRebuilt.v1` | operation ID, scope, projection names, source range, target version, status, audit reference | Audit/ops |
| `ReportDataQualityChanged.v1` | scope/projection, old/new status, missing sources, watermark/version | Dashboard hints, observability |
| `ReportAccessAudited.v1` | safe actor hash/role, scope summary, endpoint, filters hash, status, correlation | Audit |

Events are hints/observability facts, not authorization authority or revenue facts.

## 7. PostgreSQL data contract

Database is owned exclusively by Reporting. Every column requires `COMMENT ON COLUMN` matching the descriptions below. All tables have `tenant_id`; branch-scoped tables have `branch_id`. UUIDs are UUID v7 generated by application.

### 7.1 `sales_daily`

| Column | Type | Null | Description |
|---|---|---:|---|
| `tenant_id` | UUID | no | Tenant owner |
| `branch_id` | UUID | no | Branch scope |
| `business_date` | DATE | no | Branch/tenant policy local date |
| `currency` | TEXT | no | MVP `VND` |
| `paid_order_count` | BIGINT | no | Unique paid orders |
| `gross_sales_minor` | BIGINT | no | Pre-discount net-of-tax merchandise subtotal |
| `discount_minor` | BIGINT | no | Applied discount |
| `net_sales_minor` | BIGINT | no | Gross minus discount |
| `vat_minor` | BIGINT | no | VAT snapshot |
| `projection_version` | BIGINT | no | Current materialized projection |
| `watermark` | TEXT | no | Source checkpoint |
| `watermark_version` | BIGINT | no | Monotonic source checkpoint |
| `calculation_version` | TEXT | no | Metric dictionary version |
| `last_updated_at` | TIMESTAMPTZ | no | Projection update UTC |

Primary key: `(tenant_id, branch_id, business_date, currency)`.

### 7.2 `sales_by_branch`

| Column | Type | Null | Description |
|---|---|---:|---|
| `tenant_id` | UUID | no | Tenant owner |
| `branch_id` | UUID | no | Branch scope |
| `period_start` | DATE | no | Normalized period start |
| `period_end` | DATE | no | Normalized period end |
| `timezone` | TEXT | no | Tenant reporting timezone for multi-branch aggregate |
| `branch_label` | TEXT | no | Safe historical branch label |
| `currency` | TEXT | no | MVP `VND` |
| `paid_order_count` | BIGINT | no | Unique paid orders |
| `gross_sales_minor` | BIGINT | no | Gross sales |
| `discount_minor` | BIGINT | no | Discount |
| `net_sales_minor` | BIGINT | no | Net sales |
| `vat_minor` | BIGINT | no | VAT |
| `projection_version` | BIGINT | no | Projection version |
| `watermark_version` | BIGINT | no | Source checkpoint version |
| `calculation_version` | TEXT | no | Metric dictionary version |
| `updated_at` | TIMESTAMPTZ | no | Projection update UTC |

Primary key: `(tenant_id, branch_id, period_start, period_end, currency)`.

### 7.3 `sales_by_hour`

| Column | Type | Null | Description |
|---|---|---:|---|
| `tenant_id` | UUID | no | Tenant owner |
| `branch_id` | UUID | no | Branch scope |
| `business_date` | DATE | no | Local business date |
| `hour_bucket` | SMALLINT | no | 0..23 local bucket |
| `timezone` | TEXT | no | IANA timezone |
| `currency` | TEXT | no | MVP `VND` |
| `paid_order_count` | BIGINT | no | Unique paid orders |
| `gross_sales_minor` | BIGINT | no | Gross sales |
| `discount_minor` | BIGINT | no | Discount |
| `net_sales_minor` | BIGINT | no | Net sales |
| `vat_minor` | BIGINT | no | VAT |
| `projection_version` | BIGINT | no | Projection version |
| `watermark_version` | BIGINT | no | Source checkpoint version |
| `calculation_version` | TEXT | no | Metric dictionary version |
| `updated_at` | TIMESTAMPTZ | no | Projection update UTC |

Primary key: `(tenant_id, branch_id, business_date, hour_bucket, currency)`.

### 7.4 `sales_by_employee`

| Column | Type | Null | Description |
|---|---|---:|---|
| `tenant_id` | UUID | no | Tenant owner |
| `branch_id` | UUID | no | Branch scope |
| `period_start` | DATE | no | Query period start |
| `period_end` | DATE | no | Query period end |
| `employee_key` | TEXT | no | Opaque employee ID or `UNASSIGNED` |
| `employee_label` | TEXT | no | Safe snapshot label |
| `employee_status_at_sale` | TEXT | no | Historical status snapshot |
| `attribution_version` | TEXT | no | Checkout attribution policy |
| `currency` | TEXT | no | MVP `VND` |
| `paid_order_count` | BIGINT | no | Unique paid orders |
| `net_sales_minor` | BIGINT | no | Net sales |
| `projection_version` | BIGINT | no | Projection version |
| `watermark_version` | BIGINT | no | Source checkpoint version |
| `quality_code` | TEXT | yes | Missing/mismatch attribution warning |
| `updated_at` | TIMESTAMPTZ | no | Projection update UTC |

Primary key: `(tenant_id, branch_id, period_start, period_end, employee_key, currency)`.

### 7.5 `top_items`

| Column | Type | Null | Description |
|---|---|---:|---|
| `tenant_id` | UUID | no | Tenant owner |
| `branch_id` | UUID | no | Branch scope |
| `business_date` | DATE | no | Local business date |
| `item_id` | UUID | no | Catalog item reference |
| `item_label` | TEXT | no | Paid-time item label snapshot |
| `item_status` | TEXT | no | Current/history display status |
| `quantity_sold` | BIGINT | no | Paid quantity |
| `revenue_minor` | BIGINT | no | Net line revenue after allocation |
| `currency` | TEXT | no | MVP `VND` |
| `projection_version` | BIGINT | no | Projection version |
| `watermark_version` | BIGINT | no | Source checkpoint version |
| `updated_at` | TIMESTAMPTZ | no | Projection update UTC |

Primary key: `(tenant_id, branch_id, business_date, item_id, currency)`.

### 7.6 `promotion_effectiveness`

| Column | Type | Null | Description |
|---|---|---:|---|
| `tenant_id` | UUID | no | Tenant owner |
| `branch_id` | UUID | no | Branch scope |
| `period_start` | DATE | no | Period start |
| `period_end` | DATE | no | Period end |
| `promotion_version_id` | UUID | no | Immutable promotion version |
| `rule_version` | BIGINT | no | Published rule version |
| `promotion_label` | TEXT | no | Safe historical label |
| `conflict_group` | TEXT | yes | Applied conflict group |
| `applied_order_count` | BIGINT | no | Unique paid order cohort |
| `gross_sales_minor` | BIGINT | no | Cohort gross sales |
| `discount_minor` | BIGINT | no | Allocated discount |
| `net_sales_minor` | BIGINT | no | Cohort net sales |
| `allocation_status` | TEXT | no | `ALLOCATED`, `UNALLOCATED_PROMOTION`, `PARTIAL` |
| `currency` | TEXT | no | MVP `VND` |
| `projection_version` | BIGINT | no | Projection version |
| `watermark_version` | BIGINT | no | Source checkpoint version |
| `updated_at` | TIMESTAMPTZ | no | Projection update UTC |

Primary key: `(tenant_id, branch_id, period_start, period_end, promotion_version_id, currency)`.

### 7.7 `recommendation_effectiveness`

| Column | Type | Null | Description |
|---|---|---:|---|
| `tenant_id` | UUID | no | Tenant owner |
| `branch_id` | UUID | no | Branch scope |
| `period_start` | DATE | no | Period start |
| `period_end` | DATE | no | Period end |
| `dimension_key` | TEXT | no | Overall cohort or stable model/rule dimension |
| `shown_count` | BIGINT | no | Unique shown interactions |
| `accepted_count` | BIGINT | no | Unique accepted interactions |
| `accepted_order_count` | BIGINT | no | Unique paid accepted orders |
| `control_order_count` | BIGINT | no | Unique paid exposed-not-accepted orders |
| `accepted_net_sales_minor` | BIGINT | no | Accepted cohort net sales |
| `control_net_sales_minor` | BIGINT | no | Control cohort net sales |
| `currency` | TEXT | no | MVP `VND` |
| `attribution_version` | TEXT | no | Cohort/attribution policy |
| `quality_code` | TEXT | yes | Missing correlation/pending/partial state |
| `projection_version` | BIGINT | no | Projection version |
| `watermark_version` | BIGINT | no | Source checkpoint version |
| `updated_at` | TIMESTAMPTZ | no | Projection update UTC |

Primary key: `(tenant_id, branch_id, period_start, period_end, dimension_key, currency)`.

### 7.8 `projection_watermarks`

| Column | Type | Null | Description |
|---|---|---:|---|
| `id` | UUID | no | UUID v7 watermark row |
| `tenant_id` | UUID | no | Tenant owner |
| `scope_key` | TEXT | no | Tenant/branch scope key |
| `source_name` | TEXT | no | Event source/projection input |
| `projection_name` | TEXT | no | Read model projection |
| `watermark` | TEXT | no | Opaque checkpoint |
| `watermark_version` | BIGINT | no | Monotonic checkpoint |
| `projection_version` | BIGINT | no | Materialized version |
| `freshness_status` | TEXT | no | `FRESH`, `STALE`, `PARTIAL`, `UNAVAILABLE`, `NO_DATA` |
| `completeness_status` | TEXT | no | `COMPLETE`, `PARTIAL`, `MISSING` |
| `missing_sources` | JSONB | no | Safe source names |
| `last_updated_at` | TIMESTAMPTZ | no | Projection update UTC |
| `calculation_version` | TEXT | no | Metric dictionary version |

Unique key: `(tenant_id, scope_key, source_name, projection_name)`.

### 7.9 `inbox_events`

| Column | Type | Null | Description |
|---|---|---:|---|
| `id` | UUID | no | Local inbox row ID |
| `tenant_id` | UUID | no | Tenant owner |
| `source_name` | TEXT | no | Producer/source |
| `event_id` | UUID | no | Global source event ID |
| `event_type` | TEXT | no | Event type |
| `event_version` | INTEGER | no | Event schema version |
| `source_key` | TEXT | yes | Business dedupe key |
| `source_version` | BIGINT | yes | Producer/aggregate version |
| `status` | TEXT | no | `RECEIVED`, `VALIDATING`, `APPLIED`, `IGNORED_DUPLICATE`, `RETRY_WAIT`, `DLQ` |
| `error_code` | TEXT | yes | Safe error code |
| `received_at` | TIMESTAMPTZ | no | Arrival UTC |
| `processed_at` | TIMESTAMPTZ | yes | Terminal processing UTC |

Unique keys: `(source_name, event_id)` and applicable `(tenant_id, source_name, source_key)`.

### 7.10 `rebuild_operations`

| Column | Type | Null | Description |
|---|---|---:|---|
| `id` | UUID | no | UUID v7 operation ID |
| `tenant_id` | UUID | no | Tenant owner |
| `scope_key` | TEXT | no | Tenant/branch scope |
| `projection_names` | JSONB | no | Allowlisted projections |
| `from_business_date` | DATE | no | Bounded source range |
| `to_business_date` | DATE | no | Bounded source range |
| `source_watermark_from` | TEXT | yes | Source lower bound |
| `source_watermark_to` | TEXT | yes | Source upper bound |
| `target_projection_version` | BIGINT | no | Target version |
| `status` | TEXT | no | `REQUESTED`, `RUNNING`, `PAUSED`, `COMPLETED`, `FAILED`, `CANCELLED` |
| `lease_owner` | TEXT | yes | Worker lease identity |
| `lease_expires_at` | TIMESTAMPTZ | yes | Lease expiry |
| `last_error_code` | TEXT | yes | Safe error |
| `audit_reference_id` | UUID | no | Redacted audit reference |
| `created_at` | TIMESTAMPTZ | no | Creation UTC |
| `updated_at` | TIMESTAMPTZ | no | Update UTC |

### 7.11 `report_access_audit`

| Column | Type | Null | Description |
|---|---|---:|---|
| `id` | UUID | no | Audit reference ID |
| `tenant_id` | UUID | no | Tenant owner |
| `actor_subject_hash` | TEXT | no | Keyed actor reference |
| `role` | TEXT | no | Actor role snapshot |
| `scope_summary` | JSONB | no | Scope type/count/version only |
| `endpoint` | TEXT | no | Report endpoint |
| `filter_hash` | TEXT | no | Normalized filter hash, no raw PII |
| `outcome` | TEXT | no | `ALLOWED`, `DENIED`, `PARTIAL`, `UNAVAILABLE` |
| `data_quality_status` | TEXT | yes | Response quality state |
| `correlation_id` | UUID | no | Trace reference |
| `created_at` | TIMESTAMPTZ | no | Audit UTC |

## 8. Constraints, indexes and migration

### 8.1 Required constraints/indexes

- All tables tenant-scoped; branch tables require branch ID.
- `currency = 'VND'` for MVP aggregate rows.
- All counts and minor amounts are non-negative except explicitly signed deltas in comparison tables.
- Hour bucket is `0..23`.
- Date ranges satisfy `from <= to` and max 12 months for public query.
- Cursor/filter/scope hash prevents reuse under different authorization context.
- Unique source event/inbox keys prevent duplicate effects.
- Current projection pointer/version is unique per `(tenant_id, scope_key, projection_name)`.
- Watermark version cannot decrease for the same source/scope/projection.
- Promotion version is immutable; no aggregation silently merges versions.
- Recommendation accepted/control cohort has one row per order/context per attribution version.
- `UNASSIGNED`, `UNALLOCATED_PROMOTION` and quality codes are explicit enum values.
- Public query repository predicate includes tenant and authorized branch set.

Required indexes:

| Name | Columns | Purpose |
|---|---|---|
| `pk_sales_daily` | tenant, branch, date, currency | Daily upsert/query |
| `idx_sales_daily_period` | tenant, date DESC, branch | Tenant daily dashboard |
| `pk_sales_by_hour` | tenant, branch, date, hour, currency | Hour chart |
| `idx_employee_period` | tenant, branch, period_start DESC, employee | Employee table |
| `idx_top_items_revenue` | tenant, branch, date DESC, revenue DESC, item | Top items |
| `idx_promotion_period` | tenant, branch, period_start DESC, promotion_version | Promotion table |
| `idx_recommendation_period` | tenant, branch, period_start DESC, dimension | Recommendation table |
| `idx_watermark_freshness` | tenant, scope, projection, last_updated_at | Freshness/alerts |
| `uq_inbox_event` | source, event_id | Dedupe |
| `idx_rebuild_lease` | status, lease_expires_at | Worker lease |
| `idx_report_audit_scope_time` | tenant, created_at DESC | Audit browsing |

### 8.2 Migration order

1. Create common tenant/audit/inbox/idempotency tables.
2. Create watermark and current projection version tables/constraints.
3. Create P0 `sales_daily`, `sales_by_branch`, `top_items` and indexes.
4. Create P1 hourly/employee/promotion/recommendation read models and indexes.
5. Create rebuild operations, worker leases and outbox/telemetry tables.
6. Add source-event and parent contract compatibility fixtures.
7. Add optional range partition only after measured volume/retention threshold.
8. Seed deterministic metric/calculation version references; no business-event backfill from unknown source.

Migrations are forward-only in shared environments. Destructive changes use expand → migrate → contract. Every column has `COMMENT ON COLUMN`.

## 9. Cache and consistency contract

Cache key:

```text
report:v1:{tenantId}:{scopeType}:{scopeHash}:{authorizationVersion}
  :{endpoint}:{normalizedFilterHash}:{calculationVersion}:{watermarkVersion}
```

- TTL max 15 seconds.
- Authorization check precedes cache lookup.
- Scope/authorization version is mandatory in key.
- Watermark namespace invalidation follows correction/rebuild.
- Redis failure falls back to PostgreSQL; PostgreSQL failure returns `503 reporting_unavailable`.
- Cached response carries original watermark/lastUpdatedAt; cache hit does not claim newer data.
- Browser/public CDN caching is disabled by default for protected report responses.

## 10. Contract test matrix

| Area | Required fixtures |
|---|---|
| Auth/scope | Owner tenant, Owner branch subset, Manager assigned branch, foreign branch, revoked membership, cache after revocation |
| Revenue | Duplicate OrderPaid, late Payment settlement excluded, refund/correction, zero data, VND/mixed currency |
| Employee | Quick-switch, disabled employee history, missing attribution → UNASSIGNED, snapshot label/version |
| Promotion | Multiple rule versions, conflict group, allocation, unallocated/partial, version change, correction |
| Recommendation | Duplicate shown/accepted, same cart context, late OrderPaid, multiple accepts one order, control denominator zero, one-count cohort |
| Freshness | ≤1m FRESH, >1m STALE, >5m alert, missing source PARTIAL, DB unavailable, NO_DATA |
| Projection | At-least-once replay, unknown version, schema error, retry/DLQ, out-of-order source version, rebuild swap |
| API | Filter/range/limit, pagination cursor mismatch, all endpoints/envelopes, 401/403/409/422/429/503 |
| Cache | Scope key isolation, watermark invalidation, ETag after auth, Redis failure fallback |
| Performance | KPI p95 ≤1s, table p95 ≤2s, 12 months, 20 branches, concurrent dashboard load |
| Privacy | No phone/customer/payment/raw order IDs in response/logs/traces/events; employee labels scoped |
| Events | AsyncAPI compatibility, inbox atomicity, dedupe, source version monotonicity, replay lineage |
| UI contract | All data-quality states, safe errors, no zero substitution, branch/timezone/filter behavior |

## 11. Parent contract amendments

### Identity Access

- Add `report:read` and restricted `reporting:operate` permissions.
- Support tenant/subset branch scope resolution, membership/authorization version and deny-without-enumeration behavior.

### Order

- Add checkout employee/cashier immutable reference, display snapshot and attribution version to `OrderPaid.v1`.
- Add applied promotion version and immutable discount allocation breakdown.
- Publish approved `OrderRefunded.v1`/`OrderCorrected.v1` business facts with source/effective version.

### Loyalty/Promotion

- Preserve immutable promotion/rule version, conflict group, voucher/promotion source and allocation in OrderPaid snapshot.
- Reporting never re-evaluates a published promotion rule against current configuration.

### Recommendation

- Ensure `SuggestionShown.v1`/`SuggestionAccepted.v1` include stable interaction IDs, same order/cart correlation, rule/model version, event ordering and acceptance evidence.
- Reporting consumes accepted as attribution only; `OrderPaid.v1` remains paid-order truth.

### POS Platform/Audit

- Add Reporting API/read-model/event catalogue and `report:read` references to platform contract.
- Add redacted `ReportAccessAudited.v1`, watermark, rebuild and data-quality event consumer schemas.

## 12. Implementation artifacts required before code

- `openapi.yaml` for all public report/freshness endpoints.
- `openapi-internal.yaml` for rebuild/health/ops endpoints.
- `asyncapi.yaml` and JSON Schemas for consumed/produced events.
- `reporting-metric-dictionary.yaml` with calculation versions, timezone, rounding and correction rules.
- `reporting-read-model.schema.yaml` or generated SQL/Flyway migrations with column comments/indexes/checks.
- Golden fixtures for revenue, employee, promotion, recommendation, freshness and replay.
- Consumer-driven compatibility fixtures for Order, Loyalty/Promotion, Recommendation and Identity.
- Angular generated client types and dashboard response mocks for all data-quality states.
- Load profile for KPI/table p95 and 12-month/20-branch bounded queries.
- Security/evaluation corpus for IDOR, cache leakage, PII exposure, scope revocation and unsafe filters.

## 13. Approval gate

- **Current status:** Approved with warnings — user approved after Contracts review.
- **Required before implementation:** Resolve canonical correction/refund event, publish parent amendments, and generate machine-readable API/event/schema/migration artifacts.
- **Implementation rule:** Không viết controller/query/consumer/migration trực tiếp từ prose; generate machine-readable contracts and fixtures first.

