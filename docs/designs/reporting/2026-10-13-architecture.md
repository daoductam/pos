Started at: 2026/10/13 00:00:00
Finished at: 2026/10/13 00:00:00
Total time: 0 minutes
---

# Module 10 — Báo cáo & Dashboard: Architecture

**Ngày:** 2026-10-13  
**Trạng thái:** Approved — sequence flows confirmed  
**Vision:** [Reporting Vision](./2026-10-13-vision.md)  
**Requirements:** [Reporting Requirements](./2026-10-13-requirements.md)  
**Requirements review:** [Reporting Requirements Review](./2026-10-13-requirements-review.md)
**Sequence diagrams:** [Reporting Sequence Diagrams](./2026-10-13-sequence-diagrams.adoc)

## 1. Architecture decision summary

Reporting là một bounded context read-only, dùng PostgreSQL event-driven pre-aggregated read models cho dashboard MVP. Domain services giữ nguồn sự thật; Reporting không query chéo database và không phát business mutation.

| Decision | Choice | Rationale |
|---|---|---|
| Read path | PostgreSQL read models | Đáp ứng KPI p95 ≤1s, table p95 ≤2s và freshness ≤1 phút |
| Ingestion | EventBridge + SQS queues, at-least-once | Tách producer, retry/DLQ và replay được |
| Projection | Query-first pre-aggregates | Không aggregate raw rows trên hot path 12 tháng |
| Scope enforcement | Identity online decision + repository predicates | Defense-in-depth tenant/branch |
| Cache | Redis/ElastiCache tối đa 15s | Giảm tải dashboard nhưng không vượt freshness policy |
| Historical analytics | Athena extension boundary | Chưa đưa vào MVP request path |
| Corrections | Current projection + watermark/version lineage | Dashboard hiển thị số hiện tại, có `asOfWatermark` |
| API | REST/OpenAPI 3.1 | Phù hợp Angular dashboard và parent platform |
| IDs/time | UUID v7, UTC, branch-local business date | Nhất quán platform |

## 2. System context

```text
Owner/Manager Dashboard (Angular)
              |
              v
       API Gateway / Cognito
              |
              v
      Reporting Query API ──────────────> Redis Cache (≤15s)
              |
              v
      Reporting PostgreSQL Read Models
              ^
              |
      Projection Workers / Watermark Manager
        ^          ^          ^          ^
        |          |          |          |
     Order      Loyalty/    Recommend.  Identity/Branch
   OrderPaid   Promotion     Shown/      metadata
  correction    snapshot     Accepted
              ^
              |
     EventBridge + SQS + DLQ
```

API Gateway/Cognito validates the outer request/JWT. Reporting still calls Identity for the current authorization decision because branch membership/revocation is an online authority, then applies the resolved scope again in cache and repository layers.

## 3. Component boundaries

| Component | Responsibility | Must not do |
|---|---|---|
| Dashboard Angular | Render KPI/chart/table/filter/drill-down and data-quality states | Compute authoritative KPI or trust browser timezone/scope |
| Reporting Query API | Validate filters, resolve scope, read cache/read models, return common envelope | Query producer DB, accept SQL, expose raw domain records |
| Identity Scope Client | Request `report:read` decision, membership/authorization version and resolved branches | Cache authorization as long-lived authority |
| Report Policy Engine | Enforce role, max range, branch count, dimensions, employee/privacy policy | Resolve identity from LLM/client input |
| Cache Adapter | Scope-safe 15s response cache and ETag metadata | Serve before authorization or cross-scope key |
| Projection Ingestor | Read event, validate envelope/schema, dedupe, route to projection handlers | Ack before DB transaction commits |
| Projection Handlers | Upsert aggregate read models and watermark within transaction | Re-evaluate historical promotion rules or query source DB |
| Watermark/Completeness Manager | Track per-source/scope lag, required source completion and data quality | Mark partial data as fresh |
| Rebuild Worker | Reprocess bounded event range into target projection version, atomic swap | Mutate producer source or silently overwrite current version |
| Reporting PostgreSQL | Own read models, inbox, watermark, rebuild, reconciliation and audit references | Become a second business source of truth |
| Athena Adapter (future) | Serve measured historical/ad-hoc workloads behind explicit query contract | Enter MVP dashboard path without SLO proof |

## 4. Data flow

### 4.1 Event ingestion

1. Producer publishes a versioned event through its transactional outbox.
2. EventBridge routes to Reporting SQS queue by event family.
3. Reporting consumer validates tenant/branch scope, event version, source key and schema.
4. Inbox transaction deduplicates `eventId`/source business key.
5. Projection handler updates affected pre-aggregates and `projection_watermarks`.
6. Transaction commits; consumer acknowledges message.
7. Transient failures retry; schema/security/metric failures go to DLQ with redacted error context.

### 4.2 Query flow

1. Dashboard sends report endpoint/filter with Cognito JWT.
2. API validates filter shape and calls Identity online authorization for `report:read`.
3. Policy engine normalizes scope, period and timezone.
4. Cache key is built from tenant, resolved scope/authorization version, endpoint, normalized filters, calculation version and watermark.
5. Cache hit returns only after authorization; miss reads pre-aggregated rows with tenant/branch predicates.
6. API attaches `dataQuality`, `asOfWatermark`, `lastUpdatedAt`, `projectionVersion` and `calculationVersion`.
7. Dashboard renders value and freshness state; it never silently substitutes zero for `NO_DATA`.

### 4.3 Correction/replay flow

1. Approved refund/correction/late event arrives with source event ID and effective version.
2. Handler validates monotonic source version and dedupes.
3. Current aggregate is adjusted or recomputed from bounded source lineage according to metric policy.
4. `asOfWatermark` and `projectionVersion` advance atomically with the read-model update.
5. Cache entries matching the affected scope/period are invalidated by watermark namespace.
6. Dashboard reads the corrected current view; source event history remains immutable.

## 5. Scope and security architecture

### 5.1 Request authorization

- API requires Cognito JWT and `report:read`.
- Reporting calls `POST /internal/v1/authorization-decisions` with current subject, tenant, requested branches and operation.
- Owner may receive `TENANT` or selected authorized branches; Manager receives assigned branches only.
- `authorizationVersion`/membership version enters scope context and cache key.
- Authorization is checked before cache lookup and before every resource/drill-down query.

### 5.2 Repository defense-in-depth

Every query requires:

```text
tenant_id = resolvedScope.tenantId
AND (
  resolvedScope.scopeType = TENANT
  OR branch_id IN resolvedScope.branchIds
)
```

The predicate is generated by server-side policy, not concatenated from request strings. Employee, promotion and recommendation dimensions inherit the branch scope; no endpoint can widen scope through a dimension filter.

### 5.3 Privacy

- No customer phone/name, payment credentials or raw order IDs in public report responses.
- Employee reports return safe display snapshot only to authorized Owner/Manager.
- Logs/traces contain correlation, endpoint, scope type/count and metric metadata; no raw event payload by default.
- Audit records report access, denied scope, rebuild/replay and DLQ replay without storing raw PII.

## 6. Read-model data architecture

### 6.1 Core tables

| Table | Grain | Main key/query pattern |
|---|---|---|
| `sales_daily` | tenant/branch/business date | `(tenant_id, branch_id, business_date)` |
| `sales_by_branch` | tenant/period/branch | `(tenant_id, period_start, branch_id)` |
| `sales_by_hour` | tenant/branch/date/hour | `(tenant_id, branch_id, business_date, hour_bucket)` |
| `sales_by_employee` | tenant/branch/period/employee | `(tenant_id, branch_id, period_start, employee_id_or_unassigned)` |
| `top_items` | tenant/branch/date/item | `(tenant_id, branch_id, business_date, item_id)` |
| `promotion_effectiveness` | tenant/branch/period/promotion version | `(tenant_id, branch_id, period_start, promotion_version_id)` |
| `recommendation_effectiveness` | tenant/branch/period/dimension | `(tenant_id, branch_id, period_start, attribution_dimension)` |
| `projection_watermarks` | source/scope/projection | `(tenant_id, source_name, scope_key, projection_name)` |
| `inbox_events` | source event | `(source, event_id)` plus tenant |
| `rebuild_operations` | bounded rebuild job | UUID v7 operation ID |

Every table includes `tenant_id`, `created_at`/`updated_at` where applicable, `projection_version`, source watermark metadata and required `COMMENT ON COLUMN` in migration. Branch-scoped tables include `branch_id`.

### 6.2 Employee and promotion snapshots

Reporting stores safe snapshots needed for reproducible history, not live joins:

- `checkout_employee_id` opaque UUID/reference, safe display label, role/branch snapshot and `attribution_version`.
- `promotion_version_id`, rule label/version, conflict group, allocated discount and allocation status.
- `suggestion_interaction_id`, item/rule/model version and order/cart correlation for attribution facts.

If a source snapshot is missing, the row remains countable in total metrics but gets `UNASSIGNED`, `UNALLOCATED` or `PARTIAL` marker according to metric policy.

### 6.3 Partition/index strategy

- MVP indexes optimize `(tenant_id, branch_id, business_date DESC)` and period range queries.
- Monthly range partitions are candidates for daily/hourly/top-item/employee tables after measured volume or retention threshold; no unmeasured partition complexity is required initially.
- Composite indexes include tenant first; branch/date/time follow query patterns.
- Promotion/recommendation tables index immutable version/dimension and period.
- Cursor sort keys are deterministic and indexed.
- 12-month query limit is enforced at API and query policy layers.

## 7. Event and projection architecture

### 7.1 Canonical event inputs

| Producer | Event/fact | Required Reporting fields |
|---|---|---|
| Order | `OrderPaid.v1` | order/tenant/branch, business time/date, totals, lines, checkout employee attribution, applied promotion breakdown, source version |
| Order | approved refund/correction | source order/branch, correction/refund ID, amounts/lines, effective version/time |
| Recommendation | `SuggestionShown.v1` | stable interaction ID, suggestion/item/rule/model, order/cart correlation, rendered time |
| Recommendation | `SuggestionAccepted.v1` | shown interaction ID, accepted interaction ID, order/cart correlation, accepted time/evidence |
| Loyalty/Promotion | applied promotion/voucher snapshot | immutable promotion/rule version, conflict group, allocation, source order reference |
| Identity/Branch | branch/employee metadata events | branch timezone, safe labels, employee status/version |

OrderPaid applied promotion breakdown is the canonical promotion input. Separate promotion events may enrich labels but cannot cause an additional revenue effect.

### 7.2 Watermark and completeness

`projection_watermarks` tracks source checkpoint, monotonic `watermarkVersion`, projection version, last updated time, lag and required source completeness.

Freshness state:

```text
FRESH       lag <= 1 minute and required sources complete
STALE       lag > 1 minute but read model available
PARTIAL     one or more required source/branch projections missing
UNAVAILABLE read model/query unavailable
NO_DATA     valid complete query with no facts in requested period
```

At >5 minutes lag, state remains `STALE` with an escalated alert; it never becomes `FRESH` until the watermark advances. A cross-branch aggregate is `PARTIAL` when any required branch/source is missing unless the request explicitly asks for available-only data, which is not enabled in MVP.

### 7.3 Projection state and atomicity

Event inbox state:

`RECEIVED → VALIDATING → APPLIED | IGNORED_DUPLICATE | RETRY_WAIT | DLQ`

Rebuild state:

`REQUESTED → RUNNING → PAUSED | COMPLETED | FAILED | CANCELLED`

Projection handler transaction includes inbox dedupe, read-model mutation, watermark advancement and outbox/cache-invalidation signal. A failed transaction does not advance watermark. Rebuild writes target `projection_version` and atomically switches current pointer after validation.

## 8. API and cache architecture

### 8.1 Query API modules

- `ReportScopeResolver`: Identity decision and normalized scope.
- `ReportFilterPolicy`: date/range/dimension/limit/timezone validation.
- `ReportQueryService`: endpoint-specific read-model query and metric mapping.
- `DataQualityService`: watermark/freshness/completeness envelope.
- `ReportCacheAdapter`: Redis key namespace, TTL, ETag and invalidation.
- `ReportAuditPublisher`: redacted access/denial/rebuild evidence.

### 8.2 Cache key

```text
report:v1:{tenantId}:{scopeType}:{scopeHash}:{authorizationVersion}
  :{endpoint}:{normalizedFilterHash}:{calculationVersion}:{watermarkVersion}
```

TTL is at most 15 seconds. Authorization happens before lookup. Watermark namespace invalidation prevents corrected data from being served beyond policy. A cache hit still returns the stored data-quality envelope; it does not claim a newer timestamp.

### 8.3 Athena extension boundary

Athena is not called by MVP dashboard APIs. A future `HistoricalReportAdapter` may route queries only when:

- requested range exceeds PostgreSQL measured threshold;
- output schema and metric dictionary are identical;
- tenant/branch authorization is propagated;
- freshness/SLO and cost policy are explicit;
- results include source type `ATHENA` and query watermark.

## 9. Dashboard architecture

Angular dashboard uses a single shell with role-aware scope selector and widget-level query adapters:

- Overview KPI adapter.
- Daily/branch/hourly chart adapters.
- Top-items table adapter.
- Employee/promotion/recommendation table adapters.
- Shared filter state and cursor state.
- Shared data-quality banner and freshness timestamp.

The UI does not combine metrics from different source watermarks into one “fresh” card. If widgets have different watermarks, each widget carries its own status or the overview reports the least fresh required source.

## 10. Reliability and operations

- EventBridge/SQS at-least-once, exponential retry, per-consumer DLQ.
- Inbox dedupe by event ID/source key.
- Query API timeout and bounded result limits.
- Redis failure falls back to PostgreSQL; database failure returns `UNAVAILABLE`, never producer DB fallback.
- Projection lag alerts at 1 minute warning and 5 minutes escalation.
- Rebuild/replay requires restricted operator permission and audit reference.
- Backpressure is visible through queue depth, worker concurrency and lag metrics.
- Schema incompatibility is quarantined; no best-effort interpretation of unknown event version.

## 11. ADRs

| ADR | Decision | Alternatives rejected | Reason |
|---|---|---|---|
| REP-01 | PostgreSQL pre-aggregated read model for MVP | Athena-first, raw query projection | Meet dashboard latency/freshness with bounded complexity |
| REP-02 | Event-driven projections with inbox/dedupe | Synchronous fan-out, cross-service DB joins | Preserve service autonomy and replay |
| REP-03 | Identity online + repository predicates | Long-lived auth cache, UI-only scope | Revocation and defense-in-depth |
| REP-04 | Current corrected view with watermark lineage | Immutable dashboard snapshots only | Operators need current numbers while preserving audit lineage |
| REP-05 | OrderPaid snapshot canonical for revenue/promotion | Recompute from Payment/Promotion DB | Avoid metric drift and late rule reinterpretation |
| REP-06 | Order-level recommendation AOV cohort | Per-suggestion additive uplift | Avoid double-counting multi-accept orders |
| REP-07 | Redis 15s scope-safe cache | No cache, one-minute cache | Balance p95 targets and ≤1m freshness |
| REP-08 | Athena deferred extension | Athena in MVP path | Cost/latency/SLO not yet measured |

## 12. Sequence flows

Sequence diagrams are generated as L1 system-level + L2 internal backend diagrams after user confirmation.

### Proposed flows

1. **P0 OrderPaid projection success** — Order outbox → EventBridge/SQS → Reporting inbox/handler → read models/watermark.
2. **Duplicate/out-of-order event** — dedupe or monotonic correction handling without double-count.
3. **Dashboard KPI query cache miss/hit** — Dashboard → Identity scope → policy → Redis/PostgreSQL → data-quality response.
4. **Manager unauthorized branch/drill-down** — forged branch filter or revoked membership fails without enumeration.
5. **Recommendation effectiveness pending to paid** — SuggestionShown/Accepted → pending attribution → OrderPaid → accepted/control cohort.
6. **Promotion version revenue projection** — OrderPaid promotion snapshot → allocation → promotion read model.
7. **Late refund/correction** — correction event → current aggregate revision → watermark/cache invalidation → dashboard current view.
8. **Projection rebuild/replay** — operator request → target projection → validation → atomic current switch or failure/DLQ.

### Proposed L1 participants

| Participant | Type | Role |
|---|---|---|
| Owner/Manager Dashboard | Client | Report filters/widgets/rendering |
| Reporting Query API | Service | Scope-aware read API |
| Identity Access | Service | Online permission/scope decision |
| Redis Cache | Infrastructure | Short-lived scope-safe cache |
| Reporting PostgreSQL | Database | Read models/inbox/watermarks/rebuild state |
| Order Service | Service | `OrderPaid`/correction producer |
| Loyalty/Promotion Service | Service | Promotion snapshot/version producer |
| Recommendation Service | Service | Suggestion shown/accepted producer |
| EventBridge/SQS/DLQ | Broker | Async delivery/retry/quarantine |
| Reporting Projection Worker | Worker | Validate/dedupe/project/rebuild |
| Audit/Observability | Consumer | Redacted audit/metrics/traces |

### Proposed L2 participants

`ReportController`, `ReportScopeResolver`, `ReportPolicyEngine`, `ReportQueryService`, `ReportCacheAdapter`, `Report PostgreSQL`, `ProjectionConsumer`, `ProjectionHandler`, `WatermarkManager`, `RebuildWorker`, `IdentityClient` and `EventBridge/SQS adapter` will be collapsed to ≤7 participants per diagram.

## 13. Architecture approval gate

- **Current status:** Approved — eight flows and participant boundaries confirmed; L1/L2 diagrams generated and validated.
- **Required before Contracts:** User approves Architecture and sequence diagrams.
- **Implementation rule:** Không code; Contracts phải materialize API schemas, event schemas, read-model migrations, cache semantics và projection/rebuild protocols.

