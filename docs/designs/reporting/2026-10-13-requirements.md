Started at: 2026/10/13 00:00:00
Finished at: 2026/10/13 00:00:00
Total time: 0 minutes
---

# Module 10 — Báo cáo & Dashboard: Requirements

**Ngày:** 2026-10-13  
**Trạng thái:** Approved with warnings — user approved  
**Vision:** [Reporting Vision](./2026-10-13-vision.md)  
**Vision review:** [Reporting Vision Review](./2026-10-13-vision-review.md)

## 1. Purpose and boundaries

Reporting service biến các event nghiệp vụ đã được version hóa thành PostgreSQL read models phục vụ dashboard gần realtime. Reporting là metric owner nhưng không sở hữu nguồn sự thật của Order, Payment, Loyalty/Promotion, Recommendation hoặc Identity.

MVP không query chéo database, không nhận arbitrary SQL và không đưa Athena vào dashboard request path. Athena là extension boundary cho lịch sử/ad-hoc sau khi có bằng chứng về volume/latency.

### In scope

- P0: sales theo ngày, chi nhánh, top items.
- P1: sales theo giờ, theo checkout employee, promotion effectiveness, recommendation effectiveness.
- Dashboard Angular dùng chung cho Owner/Manager với scope khác nhau.
- API-first REST/OpenAPI 3.1, read-only, filter/pagination/drill-down.
- Event-driven projection với at-least-once delivery, dedupe, DLQ, rebuild/replay, freshness watermark.
- Current corrected metrics với `asOfWatermark` và `lastUpdatedAt`.

### Out of scope

- Accounting/legal reporting, tax filing hoặc e-invoice.
- Arbitrary SQL/query builder cho end user.
- Predictive analytics, anomaly detection hoặc ML.
- Raw order/customer-level dashboard drill-down.
- Athena production pipeline trong MVP.
- Mutation vào domain service hoặc sửa lại source event.

## 2. Actors and authorization

| Actor | Permission | Scope |
|---|---|---|
| Owner | `report:read` | Toàn tenant hoặc subset branch thuộc tenant |
| Manager | `report:read` | Chỉ assigned branches còn membership hợp lệ |
| Cashier/Kitchen | Không có dashboard reporting permission trong MVP | Không truy cập report API |
| Dashboard Angular | User JWT + server-resolved scope | Không tự tạo tenant/branch authority |
| Reporting worker | Workload identity | Consume event và ghi AI-owned Reporting DB |
| Ops/replay worker | Restricted `reporting:operate` | Rebuild/replay theo tenant/branch/time window được phê duyệt |

Mọi public request phải resolve Identity authorization trước khi đọc cache hoặc database. Repository luôn áp dụng `tenant_id` predicate và `branch_id` predicate; không tin `branchId[]`, role, tenant hoặc scope do client gửi như authority.

## 3. Definitions and metric dictionary

### 3.1 Time and scope policy

- Một branch: dùng IANA timezone của branch.
- Nhiều branch: dùng tenant reporting timezone; response vẫn trả `timezone` và `periodBounds`.
- Browser timezone không có authority.
- `businessDate` là local date theo timezone policy; `periodFrom`/`periodTo` trả cả local date/time và UTC bounds khi cần.
- Dashboard mặc định 7 hoặc 30 ngày; max interactive range 12 tháng.
- Cross-branch filter max 20 branch IDs; Owner có thể chọn tenant scope nếu policy cho phép.

### 3.2 Revenue metrics

Reporting chỉ ingest paid-order facts từ `OrderPaid.v1` và các correction/refund facts đã được parent contract phê duyệt.

| Metric | Definition | Edge rule |
|---|---|---|
| `paidOrderCount` | Count unique finalized `OrderPaid.v1` facts sau dedupe | Duplicate event/source key count một lần; Payment late settlement không tính |
| `grossSalesMinor` | Tổng `subtotalMinor` trước discount, chưa gồm VAT/fee/tip | Lấy từ canonical OrderPaid snapshot, không tính lại line tại query time |
| `discountMinor` | Tổng discount đã áp dụng và được snapshot trong paid order | Promotion/voucher allocation phải có version; thiếu allocation vào `UNALLOCATED` |
| `netSalesMinor` | `grossSalesMinor - discountMinor` theo calculation version | Không âm; refund/correction dùng policy versioned |
| `vatMinor` | VAT snapshot/tổng VAT từ canonical paid-order fact | Không gộp vào `netSalesMinor` |
| `aovMinor` | `netSalesMinor / paidOrderCount` | Zero denominator → `NO_DATA`, không trả zero giả |
| `comparisonDelta` | Actual so với comparison period cùng metric dictionary | Trả cả amount, ratio và exact comparison period |

Money dùng integer minor units + currency. `calculationVersion` bắt buộc trong mọi aggregate response.
MVP chỉ hỗ trợ `VND`; mixed-currency aggregation bị reject hoặc trả `UNAVAILABLE` theo policy, không cộng số minor unit khác currency.

### 3.3 Top items

- Nguồn: paid line snapshots của `OrderPaid.v1`.
- `quantitySold` và `revenueMinor` chỉ tính line/order đã paid.
- `revenueMinor` của top item là net line revenue sau discount allocation, chưa gồm VAT/fee/tip; line discount không phân bổ được vào `UNALLOCATED_ITEM_DISCOUNT`.
- Item label là snapshot tại thời điểm paid; item bị archive vẫn xuất hiện lịch sử với `status=ARCHIVED` nếu user có quyền.
- Ties dùng thứ tự ổn định: `revenueMinor DESC`, `quantitySold DESC`, `itemId ASC`.
- Mặc định top 10, max 100; pagination dùng cursor khi bảng chi tiết.

### 3.4 Employee sales

- Attribution chính thức là checkout employee/cashier active tại thời điểm checkout thành công.
- Parent `OrderPaid.v1` phải mang immutable employee reference + display snapshot + attribution version.
- Employee đã disabled vẫn giữ lịch sử, không bị đổi attribution hồi tố.
- Thiếu/mismatch employee attribution đưa vào `UNASSIGNED/UNKNOWN`, giữ trong doanh thu tổng và trả data-quality warning.
- Cashier quick-switch không dùng employee tạo draft để thay thế checkout actor.
- Manager chỉ thấy employee thuộc branch scope; Owner có thể xem tenant hoặc branch subset.

### 3.5 Promotion effectiveness

Promotion được group theo immutable `promotionVersionId`/`ruleVersion`, không gộp các version khác nhau vào cùng một kết quả mặc định.

Mỗi applied promotion fact phải cung cấp: promotion/rule version, conflict group, applied timestamp/business date, order reference opaque, discount allocation, branch, channel và source event ID.

Metrics:

- `appliedOrderCount`: unique paid orders có applied promotion version.
- `grossSalesMinor`: gross sales của cohort promotion.
- `discountMinor`: discount được phân bổ cho promotion version.
- `netSalesMinor`: net sales sau discount.
- `averageNetOrderMinor`: `netSalesMinor / appliedOrderCount`, zero denominator → `NO_DATA`.
- `applicationRate` nếu denominator eligible promotion impressions/quotes tồn tại; nếu không có denominator, trả `NOT_AVAILABLE`, không tự suy đoán.

Một order có nhiều promotion version được phân bổ theo immutable discount breakdown. Nếu không phân bổ được, amount vào `UNALLOCATED_PROMOTION`, không duplicate toàn bộ discount cho từng rule.

### 3.6 Recommendation effectiveness

`SuggestionShown.v1` là exposure; `SuggestionAccepted.v1` là attribution signal; `OrderPaid.v1` là paid-order condition.

Metrics:

- `shownCount`: unique suggestion impression/interaction IDs sau dedupe.
- `acceptedCount`: unique accepted interaction IDs nối được với shown event.
- `acceptanceRate`: `acceptedCount / shownCount`; zero shown → `NO_DATA`.
- `acceptedOrderCount`: unique paid orders có ít nhất một accepted suggestion.
- `controlOrderCount`: unique paid orders đã được hiển thị suggestion trong cùng eligibility context nhưng không accepted.
- `acceptedAovMinor`: `netSalesMinor / acceptedOrderCount`.
- `controlAovMinor`: `netSalesMinor / controlOrderCount`.
- `aovUpliftMinor`: `acceptedAovMinor - controlAovMinor`.
- `aovUpliftRatio`: `aovUpliftMinor / controlAovMinor` khi control denominator > 0; nếu không, `NO_DATA`.

Control group rules:

1. Cùng branch scope và cùng selected period/timezone.
2. Order đã thấy suggestion nhưng không có accepted suggestion trong cùng attribution context.
3. Chỉ order `PAID` mới vào accepted/control AOV cohort.
4. Một order chỉ tính một lần trong accepted/control cohort, dù accepted nhiều suggestion.
5. Acceptance vẫn tính riêng theo từng suggestion impression/item/rule/model dimension.
6. AOV uplift là order-level KPI, không cộng dồn từ các suggestion item; breakdown theo item chỉ hiển thị acceptance metrics trong MVP.
7. Nếu `SuggestionAccepted` không có valid shown/order correlation hoặc OrderPaid chưa đến, giữ event attribution ở pending/error projection và không đưa vào paid AOV.

`Attribution context` mặc định là cùng order/cart session: `SuggestionShown` phải xảy ra trước `SuggestionAccepted`, và cả hai phải nối được tới cùng `orderId`/cart correlation trước khi order chuyển `PAID`. Không ghép suggestion giữa các session hoặc chỉ dựa trên customer/branch/time proximity.

`aovUplift` là observed cohort difference, không phải causal proof. Dashboard phải dùng wording “AOV chênh lệch giữa hai cohort” và hiển thị cohort counts; không gọi là “do gợi ý làm tăng” nếu chưa có randomized experiment contract.

## 4. Functional requirements

### FR-REP-001 — Consume and project canonical facts (P0)

Reporting phải consume versioned event envelopes từ Order, Payment/Order correction, Identity/branch metadata, Loyalty/Promotion và Recommendation theo consumer contract. Consumer phải:

1. Validate schema version, tenant/branch scope, event timestamp và required source fields.
2. Dedupe theo `eventId` và source business key khi parent contract yêu cầu.
3. Ack sau khi transaction projection + inbox state commit.
4. Retry transient errors với backoff; schema/tenant/metric validation errors vào DLQ.
5. Ghi `projectionVersion`, `sourceWatermark`, `lastUpdatedAt`, `calculationVersion`.
6. Không đọc trực tiếp database của producer để bù missing fields.

### FR-REP-002 — Daily sales report (P0)

API phải trả doanh thu theo `businessDate`, scope, currency và freshness. Mỗi row tối thiểu có `paidOrderCount`, gross/discount/net/VAT, AOV, comparison nếu được request, `asOfWatermark`, `lastUpdatedAt`, `projectionVersion`, `calculationVersion` và `dataQuality`.

### FR-REP-003 — Branch sales comparison (P0)

Owner có thể xem tenant aggregate hoặc branch comparison trong tenant. Manager chỉ nhận branch IDs được Identity resolve. Foreign branch không được lộ existence qua empty result, error message, count hoặc rank.

### FR-REP-004 — Top selling items (P0)

API phải hỗ trợ sort allowlist `REVENUE`/`QUANTITY`, limit 1–100, cursor pagination cho detail, archived item snapshot và data quality. Không trả raw order ID, customer ID hoặc payment ID.

### FR-REP-101 — Hourly sales (P1)

API phải trả local hour bucket `0..23`, timezone, order count, net revenue, optional gross/discount/VAT và freshness. Một branch dùng branch timezone; nhiều branch dùng tenant reporting timezone. Không trả exact transaction timestamp.

### FR-REP-102 — Employee sales (P1)

API phải group theo checkout employee snapshot, branch, period và metric. `UNASSIGNED/UNKNOWN` là bucket hợp lệ và có warning nếu tỷ lệ vượt configured threshold. Raw employee identity ngoài authorized scope không được trả.

### FR-REP-103 — Promotion effectiveness (P1)

API phải group theo promotion version/rule version, branch/period và trả applied order count, gross, discount, net, average net order, allocation status, calculation version và data quality.

### FR-REP-104 — Recommendation effectiveness (P1)

API phải trả shown/accepted/acceptance rate và order-level AOV cohort metrics theo branch/period. Response phải phân biệt `NO_DATA`, `NOT_AVAILABLE` và `PARTIAL`; không gọi accepted event là revenue fact.

### FR-REP-105 — Current corrected view (P0)

Dashboard mặc định hiển thị current projection sau correction/refund/late event. Response phải có `asOfWatermark`, `lastUpdatedAt`, `projectionVersion` và `calculationVersion`. Không overwrite source events; revision lineage giữ ở projection metadata.

### FR-REP-106 — Freshness/completeness (P0)

Data quality states:

| State | Rule | UI behavior |
|---|---|---|
| `FRESH` | Projection lag ≤1 phút và required sources complete | Hiển thị KPI bình thường + watermark |
| `STALE` | Lag >1 phút nhưng read model vẫn đọc được; >5 phút là escalated-stale/alert | Hiển thị KPI + cảnh báo stale; không gọi là fresh |
| `PARTIAL` | Thiếu một hoặc nhiều required source/branch projection | Không hiển thị aggregate như complete; nêu missing sources |
| `UNAVAILABLE` | Read model không đọc được hoặc projection unavailable | Hiển thị safe error; không fallback query domain DB |
| `NO_DATA` | Query hợp lệ nhưng không có fact trong period | Hiển thị empty state, không coi là zero |

Freshness response luôn trả `status`, `watermark`, `watermarkVersion`, `lastUpdatedAt`, `projectionVersion`, `missingSources[]` và `calculationVersion`.

### FR-REP-107 — Dashboard scope and filters (P0)

Supported filters:

- `branchIds` hoặc tenant scope theo authorization.
- `periodFrom`, `periodTo`, max 12 tháng.
- `timezonePolicy` server-resolved; client chỉ có thể request supported mode.
- `metric`, `sortBy`, `limit`, `cursor` theo từng endpoint allowlist.
- P1 dimensions: employee, promotion version, suggestion item/rule/model theo endpoint.

Unsupported filter, arbitrary field, raw SQL, raw order/customer/payment ID hoặc range vượt policy phải trả validation error.

### FR-REP-108 — Dashboard cache safety (P0)

Server-side cache tối đa 15 giây được phép cho read-only report responses. Cache key bắt buộc gồm tenant, resolved scope version, role policy, endpoint, normalized filters, calculation version và source watermark. Authorization phải được kiểm tra trước cache lookup; revoked membership không được đọc cache cũ.

ETag/conditional response chỉ dùng khi scope đã được authorize. Cache không được kéo dài freshness quá 1 phút; `UNAVAILABLE` không được cache như successful data lâu hơn retry policy.

### FR-REP-109 — Drill-down and pagination (P0/P1)

Drill-down chỉ đi từ aggregate đã authorize sang read-model resource cùng tenant/scope. Không expose raw domain order/customer/payment records. Collection endpoints dùng cursor pagination, default 20, max 100; stable sort bắt buộc có tie-break key.

### FR-REP-110 — Replay and rebuild (P0)

Ops worker có thể start scoped rebuild theo tenant/branch/date range với idempotency key. Rebuild phải:

- Không duplicate rows khi chạy lại.
- Ghi operation ID, source start/end watermark, target projection version và result.
- Không làm dashboard đọc lẫn hai projection version mà không có data-quality signal.
- Có pause/resume/failed/DLQ state và operator audit.

### FR-REP-111 — Dashboard states and error handling (P0)

Dashboard phải render được `FRESH`, `STALE`, `PARTIAL`, `UNAVAILABLE`, `NO_DATA`, authorization denied, invalid range và rate limited. Error không lộ tenant/branch ngoài scope, SQL, schema nội bộ hoặc source payload.

## 5. Public API requirements

API style: REST + OpenAPI 3.1, common `{data, meta}` success envelope and common `{error}` envelope, UUID v7, UTC timestamps, integer minor money and cursor pagination.

### 5.1 API catalogue

| Method | Path | Permission | Purpose |
|---|---|---|---|
| GET | `/api/v1/reports/dashboard/overview` | `report:read` | KPI cards and compact multi-widget snapshot |
| GET | `/api/v1/reports/sales/daily` | `report:read` | Daily revenue/order/AOV series |
| GET | `/api/v1/reports/sales/by-branch` | `report:read` | Branch aggregate/comparison |
| GET | `/api/v1/reports/sales/hourly` | `report:read` | Hour buckets for one branch or allowed scope |
| GET | `/api/v1/reports/sales/by-employee` | `report:read` | Checkout employee sales |
| GET | `/api/v1/reports/top-items` | `report:read` | Top selling items |
| GET | `/api/v1/reports/promotions/effectiveness` | `report:read` | Promotion version performance |
| GET | `/api/v1/reports/recommendations/effectiveness` | `report:read` | Suggestion acceptance/AOV cohort metrics |
| GET | `/api/v1/reports/freshness` | `report:read` | Current projection freshness/completeness |

All public report APIs are read-only. Server resolves scope on every request, including cached requests and drill-downs.

### 5.2 Common response

```json
{
  "data": {
    "scope": {"type": "BRANCH", "branchIds": ["0192..."]},
    "period": {
      "from": "2026-10-01",
      "to": "2026-10-13",
      "timezone": "Asia/Ho_Chi_Minh"
    },
    "rows": [],
    "dataQuality": {
      "status": "FRESH",
      "watermark": "reporting:tenant:v901",
      "watermarkVersion": 901,
      "lastUpdatedAt": "2026-10-13T08:00:00Z",
      "projectionVersion": 12,
      "missingSources": [],
      "calculationVersion": "sales-metric-v1"
    }
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J...",
    "nextCursor": null
  }
}
```

### 5.3 Error contract

| HTTP | Code | Meaning |
|---:|---|---|
| 401 | `unauthorized` | Missing/invalid JWT |
| 403 | `forbidden` | Role/permission denied |
| 422 | `invalid_report_filter` | Invalid date/range/dimension/limit |
| 409 | `scope_not_allowed` | Requested branch outside resolved scope |
| 409 | `report_not_ready` | Required projection not ready |
| 429 | `rate_limited` | User/tenant/API limit |
| 503 | `reporting_unavailable` | Read model/broker unavailable |

No-data is a successful domain state inside `dataQuality.status=NO_DATA`, not an authorization/error substitute.

## 6. Projection and data requirements

### 6.1 Input event catalogue

| Event | Required Reporting use |
|---|---|
| `OrderPaid.v1` | Canonical paid order, totals, lines, branch, business date, checkout employee attribution, promotion snapshot if applied |
| `OrderRefunded.v1` / approved correction event | Current corrected revenue and attribution revision |
| `SuggestionShown.v1` | Recommendation exposure denominator |
| `SuggestionAccepted.v1` | Acceptance attribution and order linkage |
| Promotion/voucher applied snapshot or versioned order discount breakdown | Promotion version metrics and discount allocation |
| Branch/employee metadata events | Safe labels, timezone, status and scope projections |
| `AuthorizationContextChanged.v1` | Invalidate optimization/cache hints, never sole authorization |

Missing source event is a contract issue, not a reason to query producer DB. Reporting marks affected rows `PARTIAL`/`UNAVAILABLE` according to policy.

### 6.2 Read models

Minimum read models:

- `sales_daily`: tenant, branch, business date, currency, paid order count, gross/discount/net/VAT, AOV inputs, projection/watermark.
- `sales_by_branch`: tenant, reporting period, branch, metrics and safe branch label snapshot.
- `sales_by_hour`: tenant, branch, business date, local hour, timezone, metrics.
- `sales_by_employee`: tenant, branch, employee snapshot or `UNASSIGNED`, period/date, metrics.
- `top_items`: tenant, branch, business date/item snapshot, quantity/revenue/rank.
- `promotion_effectiveness`: tenant, branch, promotion version/rule snapshot, cohort counts and revenue/discount metrics.
- `recommendation_effectiveness`: tenant, branch, period, suggestion/model dimensions for exposure/acceptance plus order-level cohort metrics.
- `projection_watermarks`: tenant/source/scope, watermark/version, completeness, lag, projection version.

All tenant/branch read model rows require source event IDs or deterministic source keys for replay/debugging. Every aggregate row stores/derives `calculationVersion`.

### 6.3 Projection processing state

Inbox/event state:

`RECEIVED → VALIDATING → APPLIED | IGNORED_DUPLICATE | RETRY_WAIT | DLQ`

Projection rebuild state:

`REQUESTED → RUNNING → PAUSED | COMPLETED | FAILED | CANCELLED`

Rules:

- Consumer ack only after local DB transaction commits.
- Duplicate event is observable and side-effect free.
- Out-of-order correction uses source version/effectiveAt policy, never arrival order alone.
- Unknown event version is quarantined/DLQ.
- Rebuild uses a target `projectionVersion`; publish/swap is atomic or exposes `REBUILDING` data quality.

## 7. Non-functional requirements

| Area | Requirement |
|---|---|
| Freshness | Normal dashboard data reaches `FRESH` within ≤1 minute; event processing p95 target ≤30s under normal broker conditions |
| Latency | KPI overview p95 ≤1s; paginated table/drill-down p95 ≤2s |
| Availability | Read-only report API follows platform availability target; degraded projection returns explicit status, not fabricated data |
| Query safety | Max 12-month interactive range, max 20 branches, bounded dimensions/limits, cursor pagination |
| Consistency | At-least-once event processing with dedupe; no double-count on replay |
| Security | 100% requests carry server-resolved tenant/branch scope; cross-scope negative tests pass |
| Privacy | No customer phone/name, payment credentials or raw order IDs in report response by default; employee labels limited to authorized roles |
| Cache | Server cache max 15s and scope/filter/watermark isolated |
| Observability | Correlation ID, event lag, projection version, watermark, DLQ and query latency visible |
| Retention | Read models/revision history follow Reporting policy; source events remain owned by producer/event archive |
| Scalability | Pre-aggregates/indexes/partitions support 12-month range; Athena migration threshold is measured, not guessed |

## 8. Security and privacy requirements

1. Identity online decision is authoritative for every protected API call.
2. Every repository query includes tenant predicate; branch queries include branch predicate.
3. Owner tenant aggregate is explicit `scopeType=TENANT`; wildcard branch IDs are invalid.
4. Manager requested branch set must be subset of current membership; foreign branch existence is not disclosed.
5. Cache lookup occurs only after auth and uses scope version in key.
6. API does not return customer PII, raw payment/provider identifiers or raw order IDs in MVP.
7. Employee display labels are returned only to Owner/Manager with authorized branch scope.
8. Logs/traces/events contain correlation and safe aggregation metadata only; no raw event payload when it includes personal data.
9. Report access, denied scope, rebuild/replay and DLQ replay are auditable.
10. Dashboard must escape/render labels as text; no server-provided HTML/SQL fragments are accepted.

## 9. Observability and operations

Required metrics:

- `reporting_projection_event_lag_seconds` by source/scope.
- `reporting_projection_freshness_state_total` by state/source.
- `reporting_projection_dedupe_total` and `reporting_projection_dlq_total`.
- `reporting_projection_apply_latency_ms` and error rate.
- `reporting_api_latency_ms`, status, endpoint, scope type and cache hit/miss.
- `reporting_query_rows`, range, branch count and limit bucket without raw PII.
- `reporting_metric_reconciliation_delta` for golden/correction checks.
- Recommendation acceptance/control cohort counts and missing-correlation rate.

Alerts:

- FRESH rate below target for a configured window.
- Source/branch projection lag >5 minutes.
- DLQ non-zero or growing.
- Metric reconciliation mismatch above threshold.
- Cache scope-key violation or authorization denial spike.
- API p95 breaches for KPI/table targets.

## 10. Acceptance criteria and test matrix

| Area | Required cases |
|---|---|
| Revenue | Duplicate OrderPaid, out-of-order events, refund/correction, late settlement excluded, zero data, multi-currency rejection/handling |
| Timezone | Branch-local midnight, tenant cross-branch policy, DST boundary, browser timezone ignored |
| Employee | Quick-switch, disabled employee history, missing attribution → UNASSIGNED, cross-branch employee access denied |
| Promotion | Multiple applied versions, conflict group, discount allocation, unallocated amount, version change, refund/correction |
| Recommendation | Duplicate shown/accepted, missing correlation, multiple accepts in one order, accepted/control paid cohort, zero control denominator, order-level no double count |
| Projection | At-least-once replay, unknown version, schema failure, transient retry, DLQ, rebuild/swap, concurrent rebuild request |
| API | Scope filters, IDOR, pagination cursor, invalid range, max limits, no-data, stale/partial/unavailable, cache after revocation |
| Performance | KPI p95 ≤1s, table p95 ≤2s, 12-month range, 20 branches, concurrent dashboard load, cache hit/miss |
| UI | KPI cards, charts, table, drill-down, FRESH/STALE/PARTIAL/UNAVAILABLE/NO_DATA, role-specific branch selector, safe error text |
| Privacy | No customer/payment/raw order IDs in API/logs/traces/events; employee labels only authorized scope |
| Compatibility | Parent event/schema versions, consumer-driven tests, additive field behavior, unknown version quarantine |

## 11. Decisions resolved from Vision review

- Use PostgreSQL event-driven pre-aggregated read models for MVP; no Athena request path.
- Dashboard scope: Owner tenant/subset branch; Manager assigned branches only.
- Freshness: `FRESH ≤1m`, `STALE >1–5m`, `PARTIAL` missing sources, `UNAVAILABLE` unreadable model.
- Employee attribution: checkout active employee; missing attribution → `UNASSIGNED/UNKNOWN` with warning.
- Promotion grouping: immutable promotion/rule version, with allocation and `UNALLOCATED_PROMOTION` bucket.
- Recommendation control: shown but not accepted in same eligibility context; AOV uplift order-level and each order counted once.
- Corrections: dashboard shows current corrected projection with `asOfWatermark`.
- Timezone: branch-local for one branch; tenant reporting timezone for multi-branch.
- Query range: default 7/30 days, max 12 months; pre-aggregates/indexes/partitions support this bound.
- API targets: KPI p95 ≤1s; table/drill-down p95 ≤2s; server cache max 15s with scope-safe keys.

## 12. Parent contract amendments required

### Order

- Add checkout employee/cashier immutable reference, display snapshot and attribution version to `OrderPaid.v1`.
- Add versioned promotion discount breakdown or applied promotion version references sufficient for Reporting allocation.
- Define approved refund/correction event semantics consumed by Reporting.

### Loyalty/Promotion

- Publish immutable promotion/rule version, conflict group, applied discount allocation and voucher/promotion source key.
- Preserve applied version in paid-order snapshot; never require Reporting to re-evaluate a later promotion rule.

### Recommendation

- Ensure `SuggestionShown.v1` and `SuggestionAccepted.v1` carry stable interaction ID, order/cart correlation, suggestion/rule/model version and acceptance evidence.
- Define event replay/dedupe and pending attribution behavior until `OrderPaid.v1` arrives.

### Identity

- Add `report:read` and restricted `reporting:operate` permission references.
- Confirm scope decision contract supports tenant/subset branch resolution and revocation semantics for every report request.

### POS Platform/Audit

- Add Reporting API/event/read-model catalogue to platform contract.
- Add redacted report access, projection failure and replay audit event schemas.

## 13. Approval gate

- **Current status:** Approved with warnings — user approved.
- **Required before implementation:** Materialize machine-readable contracts, parent amendments, fixtures and migration gates from Contracts/Finalization.
- **Implementation rule:** Không viết controller/query/projection từ prose; materialize OpenAPI, event JSON Schemas, metric fixtures và migration contracts trước code.

