Started at: 2026/10/13 00:00:00
Finished at: 2026/10/13 00:00:00
Total time: 0 minutes
---

# Module 11 — Giám sát & chống thất thoát: API & Data Contracts

**Ngày:** 2026-10-13  
**Trạng thái:** Approved with warnings — user approved after Contracts review  
**Vision:** [Audit & Loss Prevention Vision](./2026-10-13-vision.md)  
**Requirements:** [Audit & Loss Prevention Requirements](./2026-10-13-requirements.md)  
**Architecture:** [Audit & Loss Prevention Architecture](./2026-10-13-architecture.md)  
**Sequence diagrams:** [Audit & Loss Prevention Sequence Diagrams](./2026-10-13-sequence-diagrams.adoc)

Tài liệu này là nguồn chuẩn để sinh public/internal OpenAPI 3.1, AsyncAPI/JSON Schemas, PostgreSQL/Flyway migrations, anomaly rule fixtures, retention policy và generated Angular client types. Audit sở hữu evidence/case state nhưng không sở hữu source business state.

## 1. Contract conventions

### 1.1 Encoding, identity and money

- Public REST dưới `/api/v1`; internal REST dưới `/internal/v1`.
- EventBridge/SQS delivery at-least-once; event envelope dùng `eventType` + `eventVersion`.
- IDs là UUID v7 strings; timestamps là ISO-8601 UTC.
- Business windows dùng IANA timezone snapshot của branch; browser timezone không có authority.
- MVP chỉ hỗ trợ `VND`; tiền dùng signed/non-negative `int64` minor units theo field semantics + `currency`.
- `reason`/summary là bounded UTF-8 text; không lưu raw request/response hoặc unbounded JSON.
- New optional fields/events phải backward-compatible; đổi meaning/remove field tạo major event/API version.

### 1.2 Headers

| Header | Public | Internal | Meaning |
|---|---:|---:|---|
| `Authorization` | required | required | Cognito JWT hoặc workload credential/mTLS |
| `X-Correlation-Id` | recommended | required | Trace request/event/worker operation |
| `X-Causation-Id` | no | recommended | Upstream command/event reference |
| `Idempotency-Key` | mutation/export | required for ops | Tenant + actor/workload + operation scope |
| `If-Match` | case mutation | optional | Current case version/CAS fence |
| `If-None-Match` | optional GET | no | Conditional safe response after authorization |
| `Cache-Control` | server-controlled | server-controlled | Protected audit evidence not browser/CDN cached |

Client không được gửi `tenantId`, role, branch ownership, actor/effective operator, outcome hoặc before/after snapshot như authority. Filter IDs chỉ là requested scope; server/Identity quyết định scope thật.

### 1.3 Envelopes

```json
{
  "data": {},
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J...",
    "nextCursor": null,
    "dataQuality": {
      "status": "FRESH",
      "freshnessLagSeconds": 12,
      "completenessStatus": "COMPLETE",
      "watermark": "audit:tenant:v901",
      "watermarkVersion": 901,
      "lastUpdatedAt": "2026-10-13T08:00:12Z"
    }
  }
}
```

```json
{
  "error": {
    "code": "scope_not_allowed",
    "message": "The requested audit scope is not available",
    "details": [],
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

Error messages không tiết lộ foreign tenant/branch, actor existence, order/payment details, SQL, raw event, secret hoặc stack trace.

### 1.4 Pagination, sort and ranges

- Timeline/findings dùng cursor pagination; default `20`, maximum `100`.
- Cursor encode stable sort tuple, normalized filter hash, resolved scope hash, authorization version and projection watermark.
- Default interactive period là 30 ngày; maximum là 90 ngày inclusive/exclusive theo normalized UTC bounds.
- History >90 ngày đến 7 năm dùng async export; không cho synchronous unbounded query.
- Stable sort mặc định `occurredAt DESC, eventId DESC` hoặc `detectedAt DESC, findingId DESC`.
- Cursor mismatch với filter/scope/version trả `422 invalid_cursor` hoặc `409 scope_not_allowed` theo nguyên nhân.

### 1.5 Data quality

| Status | Rule |
|---|---|
| `FRESH` | Lag ≤60 seconds and required source/projection complete |
| `STALE` | Lag >60 seconds; >300 seconds raises escalation alert |
| `PARTIAL` | Required source/branch/gap/integrity state missing |
| `UNAVAILABLE` | Read model/database unavailable |
| `NO_DATA` | Valid complete query with no events/findings in period |
| `INSUFFICIENT_BASELINE` | Anomaly evaluation lacks minimum sample/warm-up |

`NO_DATA` không có nghĩa là “không xảy ra thao tác” khi source/projection là `PARTIAL` hoặc `STALE`.

## 2. Authorization and permission contract

### 2.1 Permission catalogue amendment

| Permission | Owner | Manager | Cashier | Kitchen | Scope |
|---|---:|---:|---:|---:|---|
| `audit:read` | yes | assigned branches | no | no | Tenant/subset or assigned branch |
| `loss-case:read` | yes | assigned branches | no | no | Finding/case scope |
| `loss-case:investigate` | yes | assigned branches | no | no | Acknowledge/investigate |
| `loss-case:resolve` | yes | no | no | no | Resolve case |
| `loss-case:dismiss` | yes | no | no | no | Dismiss case with reason |
| `audit:operate` | restricted ops | no | no | no | Export/replay/integrity/retention operations |

Identity remains online authority. Audit calls parent `POST /internal/v1/authorization-decisions` for each protected public request and applies repository predicates even after approval.

### 2.2 Resolved scope

```json
{
  "scopeType": "BRANCH",
  "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789001",
  "branchIds": ["0192b2d0-7f1d-7abc-8d2c-123456789002"],
  "role": "MANAGER",
  "permissions": ["audit:read", "loss-case:read", "loss-case:investigate"],
  "membershipVersion": 12,
  "authorizationVersion": 7,
  "resolvedAt": "2026-10-13T08:00:00Z",
  "expiresAt": "2026-10-13T08:05:00Z"
}
```

Rules:

- Owner tenant scope is explicit; wildcard branch IDs are invalid.
- Manager branch set is a subset of current active memberships.
- Resource/case/evidence detail and export/replay re-check ownership; URL IDs cannot widen scope.
- Authorization version and scope hash are part of cache/cursor keys.
- Denial is fail-closed and does not enumerate foreign resources.

## 3. Common schemas

### 3.1 `ActorSnapshot`

```json
{
  "actorType": "USER",
  "actorId": "0192...",
  "displayLabel": "Thu ngân A",
  "role": "CASHIER",
  "membershipId": "0192...",
  "membershipVersion": 12,
  "statusAtEvent": "ACTIVE",
  "snapshotVersion": "identity-actor-v1"
}
```

Only safe labels/status are stored. Cognito subject may be represented by a keyed/opaque reference; phone/email/token never appears in Audit evidence.

### 3.2 `MoneyAmount` and `DiscountAllocation`

```json
{
  "amountMinor": 5000,
  "currency": "VND"
}
```

```json
{
  "source": "MANUAL_OVERRIDE",
  "promotionVersionId": null,
  "ruleVersion": null,
  "beforeMinor": 50000,
  "discountMinor": 5000,
  "afterMinor": 45000,
  "allocationStatus": "ALLOCATED"
}
```

Constraints: `amountMinor >= 0`, `currency=VND` in MVP, `afterMinor = beforeMinor - discountMinor`, and line/order allocation totals must reconcile or use explicit `UNALLOCATED`/`PARTIAL` quality status.

### 3.3 `DataQualityEnvelope`

| Field | Type | Required | Rule |
|---|---|---:|---|
| `status` | enum | yes | `FRESH`, `STALE`, `PARTIAL`, `UNAVAILABLE`, `NO_DATA`, `INSUFFICIENT_BASELINE` |
| `freshnessLagSeconds` | int64 | yes | Non-negative when source watermark exists |
| `completenessStatus` | enum | yes | `COMPLETE`, `PARTIAL`, `MISSING` |
| `watermark` | string | yes | Opaque checkpoint |
| `watermarkVersion` | int64 | yes | Monotonic per source/scope/projection |
| `lastUpdatedAt` | timestamp | yes | Projection UTC update time |
| `projectionVersion` | int64 | yes | Current query projection version |
| `missingSources` | string[] | yes | Safe source names only |
| `normalizationVersion` | string | yes | Audit normalization policy |
| `asOfWatermark` | string | yes | Evidence/projection checkpoint |

### 3.4 `AuditEvidenceSummary`

```json
{
  "id": "0192...",
  "eventType": "DiscountApplied.v1",
  "action": "DISCOUNT_APPLIED",
  "outcome": "SUCCEEDED",
  "tenantId": "0192...",
  "branchId": "0192...",
  "actor": {"actorType": "USER", "actorId": "0192...", "displayLabel": "Thu ngân A", "role": "CASHIER"},
  "effectiveOperator": {"actorId": "0192...", "displayLabel": "Thu ngân B", "role": "CASHIER"},
  "resource": {"type": "ORDER_LINE", "id": "0192..."},
  "shiftId": "0192...",
  "deviceId": "0192...",
  "reason": "Khách được hỗ trợ",
  "beforeSummary": {"discountMinor": 0, "currency": "VND"},
  "afterSummary": {"discountMinor": 5000, "currency": "VND"},
  "qualityCode": null,
  "occurredAt": "2026-10-13T08:00:00Z",
  "createdAt": "2026-10-13T08:00:03Z",
  "sourceEventId": "0192...",
  "sourceVersion": 18,
  "correlationId": "0192...",
  "redactionVersion": "audit-redaction-v1",
  "normalizationVersion": "audit-normalization-v1"
}
```

`beforeSummary`/`afterSummary` are allowlisted and bounded; full event payload is never returned by public API.

## 4. Public API contracts

All public endpoints require Cognito JWT and server-resolved permission/scope. Protected responses use `{data, meta}`.

### 4.1 Endpoint catalogue

| Method | Path | Permission | Response | Purpose |
|---|---|---|---|---|
| GET | `/api/v1/audit/events` | `audit:read` | `AuditEventPage` | Timeline/search, max 90 days |
| GET | `/api/v1/audit/events/{eventId}` | `audit:read` | `AuditEventResponse` | Safe detail with scope check |
| GET | `/api/v1/loss-prevention/findings` | `loss-case:read` | `FindingPage` | Finding/case list |
| GET | `/api/v1/loss-prevention/findings/{findingId}` | `loss-case:read` | `FindingResponse` | Detail/evidence/explanation |
| POST | `/api/v1/loss-prevention/findings/{findingId}/acknowledge` | `loss-case:investigate` | `FindingResponse` | Acknowledge case |
| POST | `/api/v1/loss-prevention/findings/{findingId}/investigation` | `loss-case:investigate` | `FindingResponse` | Start/update investigation |
| POST | `/api/v1/loss-prevention/findings/{findingId}/resolve` | `loss-case:resolve` | `FindingResponse` | Owner resolve |
| POST | `/api/v1/loss-prevention/findings/{findingId}/dismiss` | `loss-case:dismiss` | `FindingResponse` | Owner dismiss |
| POST | `/api/v1/audit/exports` | `audit:operate` | `ExportOperationResponse` | Async history export |
| GET | `/api/v1/audit/exports/{operationId}` | `audit:operate` | `ExportOperationResponse` | Poll export status |

### 4.2 Audit events list

`GET /api/v1/audit/events` query parameters:

| Parameter | Type | Rule |
|---|---|---|
| `from`, `to` | ISO date/time | Required/derived; max 90 days interactive |
| `branchIds` | UUID[] | Max 20; subset of resolved scope |
| `actions` | enum[] | `ORDER_LINE_CANCELLED`, `ORDER_CANCELLED`, `DISCOUNT_APPLIED`, `CASH_DRAWER_OPENED`, `AUTHORIZATION_DENIED`, `CASE_ACTION` |
| `outcomes` | enum[] | `SUCCEEDED`, `REJECTED`, `FAILED`, `NO_OP` |
| `actorId` | UUID | Opaque employee reference; scope checked |
| `effectiveOperatorId` | UUID | Opaque operator reference; scope checked |
| `resourceType` | enum | `ORDER`, `ORDER_LINE`, `PAYMENT`, `SHIFT`, `POS_DEVICE`, `AUDIT_CASE` |
| `resourceId` | UUID | Must belong to authorized scope |
| `shiftId`, `deviceId`, `correlationId` | UUID/string | Exact safe references |
| `limit` | int | Default 20, max 100 |
| `cursor` | string | Same filter/scope/authorization version only |

Response:

```json
{
  "data": {
    "scope": {"type": "BRANCH", "branchIds": ["0192..."]},
    "period": {"from": "2026-10-01", "to": "2026-10-13", "timezone": "Asia/Ho_Chi_Minh"},
    "rows": [],
    "dataQuality": {
      "status": "FRESH",
      "freshnessLagSeconds": 15,
      "completenessStatus": "COMPLETE",
      "watermark": "audit:branch:v901",
      "watermarkVersion": 901,
      "lastUpdatedAt": "2026-10-13T08:00:15Z",
      "projectionVersion": 4,
      "missingSources": [],
      "normalizationVersion": "audit-normalization-v1",
      "asOfWatermark": "audit:branch:v901"
    }
  },
  "meta": {"requestId": "req_01J...", "correlationId": "cor_01J...", "nextCursor": null}
}
```

### 4.3 Findings list/detail

`GET /api/v1/loss-prevention/findings` supports `state`, `severity`, `ruleFamily`, `branchIds`, `subjectId`, `from`, `to`, `limit` and `cursor`. `subjectId` is always scope-checked and never used to reveal a foreign employee.

```json
{
  "findingId": "0192...",
  "caseId": "0192...",
  "state": "OPEN",
  "severity": "MEDIUM",
  "ruleFamily": "DISCOUNT_SPIKE",
  "ruleVersion": "loss-rule-v1",
  "subject": {"id": "0192...", "displayLabel": "Thu ngân A", "role": "CASHIER"},
  "branchId": "0192...",
  "observationWindow": {"from": "2026-10-12T00:00:00Z", "to": "2026-10-13T00:00:00Z", "timezone": "Asia/Ho_Chi_Minh"},
  "metric": {"current": "0.1800", "baseline": "0.0400", "ratio": "4.5000", "unit": "discount_rate"},
  "minimumSample": 30,
  "sampleCount": 92,
  "explanationCode": "ABOVE_BASELINE_AND_MIN_SAMPLE",
  "evidenceRefs": [{"eventId": "0192...", "action": "DISCOUNT_APPLIED"}],
  "dataQuality": {"status": "FRESH", "completenessStatus": "COMPLETE"},
  "detectedAt": "2026-10-13T08:01:00Z",
  "version": 2
}
```

`FindingResponse` includes case state/history summary, not raw source payload. Evidence references are individually scope-checked.

### 4.4 Case mutation requests

```json
{
  "reason": "Đã kiểm tra camera và đối chiếu ca",
  "note": "Bằng chứng đã được xác minh",
  "expectedVersion": 2
}
```

- `reason` required for `investigation`, `resolve`, `dismiss`; 1–500 characters after trim/sanitize.
- `note` optional, bounded and redacted; no phone, secret or customer free text.
- Server derives actor, role, tenant, branch, before/after state and timestamps.
- `Owner` may resolve/dismiss; `Manager` receives `403 forbidden` for these actions.
- Stale `expectedVersion` returns `409 case_version_conflict` without partial write.

### 4.5 Export operation

`POST /api/v1/audit/exports`:

```json
{
  "branchIds": ["0192..."],
  "from": "2021-01-01T00:00:00Z",
  "to": "2026-10-13T00:00:00Z",
  "dataClasses": ["AUDIT_EVIDENCE", "CASE_ACTIONS"],
  "format": "JSONL",
  "reason": "Kiểm tra nội bộ"
}
```

Rules: max seven years, explicit scope, legal-hold evaluation, `Idempotency-Key`, `202` with `operationId`/`pollUrl`; completion returns a short-lived authorized download reference and checksum, never a public object URL.

### 4.6 Common error taxonomy

| HTTP | Code | Meaning | Retry |
|---:|---|---|---|
| 200 | — | Success, including `NO_DATA`/quality state | no |
| 202 | — | Export/rebuild/integrity operation accepted | poll |
| 304 | — | Authorized conditional result unchanged | no |
| 401 | `unauthorized` | JWT/workload invalid | refresh/fix auth |
| 403 | `forbidden` | Permission/role not allowed | no |
| 404 | `not_found` | Resource not visible or missing | no |
| 409 | `scope_not_allowed` | Requested scope outside current scope | no |
| 409 | `case_version_conflict` | Stale concurrent case mutation | refetch/review |
| 409 | `idempotency_conflict` | Same key, different request | no |
| 409 | `audit_not_ready` | Required source/projection incomplete | bounded retry |
| 422 | `invalid_audit_filter` | Invalid range/filter/action/limit | no |
| 422 | `retention_policy_violation` | Range/data class/legal hold conflict | no |
| 429 | `rate_limited` | User/tenant/export limit | Retry-After |
| 503 | `audit_unavailable` | DB/projection/object store unavailable | bounded retry |

## 5. Internal operational contracts

### 5.1 Health

`GET /internal/v1/audit/health` requires workload identity and returns per source/projection:

```json
{
  "data": {
    "sources": [{
      "sourceName": "order-service",
      "projectionName": "audit_event_projection",
      "watermark": "order:v901",
      "watermarkVersion": 901,
      "freshnessStatus": "FRESH",
      "completenessStatus": "COMPLETE",
      "lagSeconds": 12,
      "dlqCount": 0,
      "lastErrorCode": null
    }]
  }
}
```

Raw queue payload and sensitive error detail are never returned.

### 5.2 Rebuild/replay

`POST /internal/v1/audit/rebuilds`:

```json
{
  "tenantId": "0192...",
  "branchIds": ["0192..."],
  "projectionNames": ["audit_event_projection", "anomaly_baseline_projection"],
  "from": "2026-10-01T00:00:00Z",
  "to": "2026-10-13T00:00:00Z",
  "sourceWatermarkFrom": "audit:v800",
  "sourceWatermarkTo": "audit:v901",
  "reasonCode": "PROJECTION_REPAIR"
}
```

Rules: explicit scope, bounded window, allowlisted projections, workload `audit:operate`, `Idempotency-Key`, same request returns same operation ID, different request with same key returns `409 idempotency_conflict`, immutable evidence/digest never rewritten.

### 5.3 Integrity check

`POST /internal/v1/audit/integrity-checks` starts a leased operation over explicit digest batch/scope/date range. Response `202` includes `operationId`, target batch, canonicalization version and poll URL. Verification states: `REQUESTED`, `RUNNING`, `VERIFIED`, `MISMATCH`, `FAILED`, `CANCELLED`.

## 6. Event contracts

### 6.1 Common event envelope

```json
{
  "eventId": "0192b2d0-7f1d-7abc-8d2c-123456789050",
  "eventType": "DiscountApplied.v1",
  "eventVersion": 1,
  "occurredAt": "2026-10-13T08:00:05Z",
  "tenantId": "0192...",
  "branchId": "0192...",
  "aggregateType": "Order",
  "aggregateId": "0192...",
  "aggregateVersion": 18,
  "sourceEventId": "0192...",
  "sourceVersion": 18,
  "correlationId": "0192...",
  "causationId": "0192...",
  "producer": "order-service",
  "redactionVersion": "audit-redaction-v1",
  "payload": {}
}
```

Required invariants:

- `eventId` stable across retry/replay; `(producer, sourceEventId)` unique for source dedupe.
- `tenantId` required; `branchId` required for branch-scoped POS facts.
- `sourceVersion` is producer/aggregate version, not queue arrival order.
- Audit acknowledges only after inbox/evidence/projection/watermark transaction commit.
- Unknown event version or invalid redaction is quarantined/DLQ.
- Replay retains original IDs, occurredAt, correlation/causation and source version.

### 6.2 Canonical source events consumed by Audit

#### `OrderLineCancelled.v1` / `OrderCancelled.v1`

```json
{
  "orderId": "0192...",
  "lineId": "0192...",
  "orderVersion": 8,
  "cancellationType": "LINE",
  "reason": "Khách đổi món",
  "actor": {},
  "effectiveOperator": {},
  "beforeSummary": {"lineState": "ACTIVE", "quantity": 1},
  "afterSummary": {"lineState": "CANCELLED", "quantity": 0},
  "outcome": "SUCCEEDED"
}
```

#### `DiscountApplied.v1`

```json
{
  "orderId": "0192...",
  "lineId": "0192...",
  "orderVersion": 18,
  "discountSource": "MANUAL_OVERRIDE",
  "promotionVersionId": null,
  "voucherVersionId": null,
  "ruleVersion": null,
  "beforeAmount": {"amountMinor": 50000, "currency": "VND"},
  "discountAmount": {"amountMinor": 5000, "currency": "VND"},
  "afterAmount": {"amountMinor": 45000, "currency": "VND"},
  "allocationStatus": "ALLOCATED",
  "reason": "Khách được hỗ trợ",
  "actor": {},
  "effectiveOperator": {},
  "outcome": "SUCCEEDED"
}
```

Automatic promotion/voucher events must preserve immutable version/allocation. Manual override must contain a non-empty reason.

#### `CashDrawerOpened.v1`

```json
{
  "operationKey": "0192...",
  "deviceId": "0192...",
  "shiftId": "0192...",
  "trigger": "NO_SALE",
  "requestedAt": "2026-10-13T08:00:00Z",
  "resultAt": "2026-10-13T08:00:01Z",
  "hardwareResult": "OPENED",
  "actor": {},
  "effectiveOperator": {},
  "outcome": "SUCCEEDED"
}
```

`operationKey` is stable across command/result retry. Hardware failure uses `FAILED`; delayed result links to the same operation key and cannot double-count.

#### `AuthorizationDenied.v1`

Contains `permission`, safe `resourceType`, opaque `resourceId` if safe, requested scope summary, actor/workload reference, denial code and outcome. It never contains JWT, raw request headers or foreign resource details.

### 6.3 Generic `AuditRecorded.v1` compatibility event

`AuditRecorded.v1` is canonical only for generic security/operational events that have no action-specific source event. It must contain `sourceEventId`, `sourceEventType`, `sourceVersion`, common envelope and normalized action. Producer registry rejects a second `AuditRecorded.v1` for an operation already represented by an action-specific canonical event.

### 6.4 Audit-produced events

| Event | Payload minimum | Consumers |
|---|---|---|
| `AuditEvidenceRecorded.v1` | evidence ID, action, tenant/branch, source reference, safe summary, quality/version | Reporting, Ops, permitted downstream |
| `AuditDataQualityChanged.v1` | scope/source/projection, old/new status, lag, missing sources, watermark | Ops, dashboard hints |
| `AnomalyFindingCreated.v1` | finding/case ID, rule/version, subject/branch, severity, evidence refs, explanation code | Console, Ops |
| `AnomalyFindingRevised.v1` | finding ID/version, changed evidence/baseline/data quality, reason | Console, Ops |
| `AuditCaseActionRecorded.v1` | case/finding, action, old/new state, actor snapshot, reason, case version | Audit, Ops |
| `AuditIntegrityMismatchDetected.v1` | digest batch, scope, algorithm/version, expected/actual digest ref, operation ID | Security/Ops |
| `AuditExportCompleted.v1` | operation ID, scope summary, data classes, checksum, expiry, audit reference | Ops/authorized client |

Produced event payloads never contain raw audit event body, PII, secret or direct authorization command.

## 7. PostgreSQL data contract

Database is owned exclusively by Audit service. UUIDs are UUID v7 generated by application. Every column requires `COMMENT ON COLUMN` matching the descriptions below.

### 7.1 `audit_events` — immutable evidence

| Column | Type | Null | Description |
|---|---|---:|---|
| `id` | UUID | no | Normalized immutable audit evidence ID |
| `tenant_id` | UUID | no | Tenant owner |
| `branch_id` | UUID | yes | Branch scope; null only for approved tenant/security fact |
| `event_type` | TEXT | no | Canonical source/generic event type |
| `event_version` | INTEGER | no | Event schema version |
| `action` | TEXT | no | Allowlisted normalized action |
| `outcome` | TEXT | no | `SUCCEEDED`, `REJECTED`, `FAILED`, `NO_OP` |
| `aggregate_type` | TEXT | no | Source aggregate family |
| `aggregate_id` | UUID | yes | Source aggregate opaque reference |
| `aggregate_version` | BIGINT | yes | Source aggregate version |
| `source_name` | TEXT | no | Producer service/device boundary |
| `source_event_id` | UUID | no | Stable producer event ID |
| `source_version` | BIGINT | yes | Producer/source checkpoint |
| `actor_key` | TEXT | yes | Opaque actor reference |
| `actor_snapshot` | JSONB | yes | Safe historical actor/role snapshot |
| `effective_operator_key` | TEXT | yes | Opaque active POS operator |
| `effective_operator_snapshot` | JSONB | yes | Safe historical operator snapshot |
| `device_id` | UUID | yes | POS device reference |
| `shift_id` | UUID | yes | Shift reference |
| `resource_type` | TEXT | no | Order/line/payment/shift/device/case |
| `resource_id` | UUID | yes | Scope-checked resource reference |
| `reason` | TEXT | yes | Bounded sanitized reason |
| `before_summary` | JSONB | yes | Allowlisted redacted prior summary |
| `after_summary` | JSONB | yes | Allowlisted redacted resulting summary |
| `money_summary` | JSONB | yes | Minor-unit VND/allocation summary |
| `quality_code` | TEXT | yes | Missing actor/trigger/allocation/source quality |
| `correlation_id` | UUID | no | Distributed trace reference |
| `causation_id` | UUID | yes | Causal command/event reference |
| `redaction_version` | TEXT | no | Redaction policy version |
| `normalization_version` | TEXT | no | Audit normalization version |
| `occurred_at` | TIMESTAMPTZ | no | Source business time UTC |
| `created_at` | TIMESTAMPTZ | no | Audit persistence time UTC |

No `UPDATE`/`DELETE` permission is granted to application roles for historical rows.

### 7.2 `audit_event_projection`

| Column | Type | Null | Description |
|---|---|---:|---|
| `id` | UUID | no | Projection row/evidence ID |
| `tenant_id` | UUID | no | Tenant owner |
| `branch_id` | UUID | yes | Branch scope |
| `occurred_at` | TIMESTAMPTZ | no | Sort/filter time |
| `action` | TEXT | no | Normalized action filter |
| `outcome` | TEXT | no | Outcome filter |
| `actor_key` | TEXT | yes | Scope-safe actor filter |
| `actor_label` | TEXT | yes | Safe historical label |
| `effective_operator_key` | TEXT | yes | Scope-safe operator filter |
| `effective_operator_label` | TEXT | yes | Safe historical label |
| `resource_type` | TEXT | no | Resource filter |
| `resource_id` | UUID | yes | Resource reference |
| `shift_id` | UUID | yes | Shift filter |
| `device_id` | UUID | yes | Device filter |
| `summary_json` | JSONB | no | Bounded safe display summary |
| `quality_code` | TEXT | yes | Evidence quality warning |
| `projection_version` | BIGINT | no | Current rebuild version |
| `updated_at` | TIMESTAMPTZ | no | Projection update UTC |

This table is rebuildable; `audit_events` remains the evidence source.

### 7.3 `inbox_events` and `projection_watermarks`

`inbox_events`:

| Column | Type | Null | Description |
|---|---|---:|---|
| `id` | UUID | no | Local inbox row ID |
| `tenant_id` | UUID | no | Tenant owner |
| `source_name` | TEXT | no | Producer/source |
| `source_event_id` | UUID | no | Global dedupe ID |
| `source_key` | TEXT | yes | Operation/drawer key when applicable |
| `event_type` | TEXT | no | Source event schema name |
| `event_version` | INTEGER | no | Source schema version |
| `source_version` | BIGINT | yes | Producer version |
| `status` | TEXT | no | `RECEIVED`, `VALIDATING`, `APPLIED`, `IGNORED_DUPLICATE`, `RETRY_WAIT`, `DLQ` |
| `error_code` | TEXT | yes | Safe processing error |
| `received_at` | TIMESTAMPTZ | no | Queue arrival time |
| `processed_at` | TIMESTAMPTZ | yes | Terminal processing time |

`projection_watermarks`:

| Column | Type | Null | Description |
|---|---|---:|---|
| `id` | UUID | no | Watermark row ID |
| `tenant_id` | UUID | no | Tenant owner |
| `scope_key` | TEXT | no | Tenant/branch scope |
| `source_name` | TEXT | no | Event source |
| `projection_name` | TEXT | no | Projection/worker |
| `watermark` | TEXT | no | Opaque checkpoint |
| `watermark_version` | BIGINT | no | Monotonic checkpoint |
| `projection_version` | BIGINT | no | Materialized projection version |
| `freshness_status` | TEXT | no | Quality status |
| `completeness_status` | TEXT | no | `COMPLETE`, `PARTIAL`, `MISSING` |
| `missing_sources` | JSONB | no | Safe missing source names |
| `last_updated_at` | TIMESTAMPTZ | no | Checkpoint update UTC |

### 7.4 `digest_batches`

| Column | Type | Null | Description |
|---|---|---:|---|
| `id` | UUID | no | Digest batch ID |
| `tenant_id` | UUID | no | Tenant owner |
| `scope_key` | TEXT | no | Tenant/branch scope |
| `from_occurred_at` | TIMESTAMPTZ | no | Inclusive batch start |
| `to_occurred_at` | TIMESTAMPTZ | no | Exclusive batch end |
| `first_event_id` | UUID | yes | First ordered evidence ID |
| `last_event_id` | UUID | yes | Last ordered evidence ID |
| `event_count` | BIGINT | no | Number of canonical evidence rows |
| `algorithm` | TEXT | no | MVP `SHA-256` |
| `canonicalization_version` | TEXT | no | Canonical field/serialization version |
| `previous_digest` | TEXT | yes | Previous batch digest for same scope |
| `digest` | TEXT | no | Expected digest hex/base64 reference |
| `source_watermark` | TEXT | no | Evidence checkpoint |
| `verification_status` | TEXT | no | `UNVERIFIED`, `VERIFIED`, `MISMATCH`, `FAILED` |
| `verified_at` | TIMESTAMPTZ | yes | Last verification time |
| `created_at` | TIMESTAMPTZ | no | Batch creation time |

### 7.5 `audit_policies` and `anomaly_rule_versions`

`audit_policies` stores immutable published redaction, retention, permission and query policy versions:

| Column | Type | Null | Description |
|---|---|---:|---|
| `id` | UUID | no | Policy version ID |
| `tenant_id` | UUID | no | Tenant owner |
| `policy_type` | TEXT | no | `REDACTION`, `RETENTION`, `QUERY`, `INTEGRITY` |
| `version` | BIGINT | no | Immutable version |
| `status` | TEXT | no | `DRAFT`, `ACTIVE`, `RETIRED` |
| `policy_json` | JSONB | no | Validated bounded policy config |
| `published_by` | UUID | yes | Owner/ops publisher |
| `published_at` | TIMESTAMPTZ | yes | Publish time |
| `reason` | TEXT | yes | Audited publish reason |

`anomaly_rule_versions`:

| Column | Type | Null | Description |
|---|---|---:|---|
| `id` | UUID | no | Rule version ID |
| `tenant_id` | UUID | no | Tenant owner |
| `branch_id` | UUID | yes | Null for tenant default |
| `rule_family` | TEXT | no | `CANCEL_SPIKE`, `DISCOUNT_SPIKE`, `DRAWER_FREQUENCY`, `REPEATED_ACTION` |
| `version` | BIGINT | no | Immutable rule version |
| `status` | TEXT | no | `DRAFT`, `ACTIVE`, `RETIRED` |
| `window_json` | JSONB | no | Observation/comparison windows/timezone |
| `baseline_json` | JSONB | no | Dimensions/warm-up/min sample |
| `threshold_json` | JSONB | no | Deterministic threshold/severity map |
| `suppression_json` | JSONB | no | Cooldown/grouping policy |
| `published_by` | UUID | yes | Owner/policy actor |
| `published_at` | TIMESTAMPTZ | yes | Publish time |
| `reason` | TEXT | yes | Audited rule publish reason |

### 7.6 `anomaly_baselines`

| Column | Type | Null | Description |
|---|---|---:|---|
| `id` | UUID | no | Baseline row ID |
| `tenant_id` | UUID | no | Tenant owner |
| `branch_id` | UUID | no | Branch scope |
| `subject_key` | TEXT | yes | Opaque effective operator; null for branch baseline |
| `action_family` | TEXT | no | Cancel/discount/drawer/repeated action |
| `local_time_bucket` | SMALLINT | no | Branch-local hour/daypart bucket |
| `window_start` | DATE | no | Baseline period start |
| `window_end` | DATE | no | Baseline period end |
| `sample_count` | BIGINT | no | Eligible denominator/sample |
| `mean_value` | NUMERIC(20,8) | no | Baseline mean |
| `stddev_value` | NUMERIC(20,8) | no | Baseline standard deviation |
| `baseline_status` | TEXT | no | `READY`, `WARMING`, `INSUFFICIENT`, `PARTIAL` |
| `source_watermark` | TEXT | no | Facts used |
| `rule_version` | BIGINT | no | Rule policy version |
| `updated_at` | TIMESTAMPTZ | no | Materialization time |

### 7.7 `anomaly_findings` and `case_actions`

`anomaly_findings`:

| Column | Type | Null | Description |
|---|---|---:|---|
| `id` | UUID | no | Finding ID |
| `case_id` | UUID | no | Case aggregate ID |
| `tenant_id` | UUID | no | Tenant owner |
| `branch_id` | UUID | no | Finding branch |
| `subject_key` | TEXT | yes | Opaque employee/effective operator |
| `rule_family` | TEXT | no | Rule family |
| `rule_version_id` | UUID | no | Immutable rule version reference |
| `finding_key` | TEXT | no | Deterministic dedupe/revision key |
| `observation_from` | TIMESTAMPTZ | no | Evidence window start |
| `observation_to` | TIMESTAMPTZ | no | Evidence window end |
| `baseline_id` | UUID | yes | Baseline reference |
| `severity` | TEXT | no | `LOW`, `MEDIUM`, `HIGH`, `CRITICAL` |
| `state` | TEXT | no | `OPEN`, `ACKNOWLEDGED`, `INVESTIGATING`, `RESOLVED`, `DISMISSED` |
| `metric_json` | JSONB | no | Current/baseline/sample/ratio values |
| `explanation_code` | TEXT | no | Safe deterministic explanation |
| `evidence_refs` | JSONB | no | Allowlisted event IDs/versions |
| `data_quality_status` | TEXT | no | Fresh/partial/insufficient state |
| `revision` | BIGINT | no | Monotonic finding revision |
| `version` | BIGINT | no | CAS version |
| `detected_at` | TIMESTAMPTZ | no | Detection time |
| `updated_at` | TIMESTAMPTZ | no | Current pointer update time |

`case_actions`:

| Column | Type | Null | Description |
|---|---|---:|---|
| `id` | UUID | no | Case action ID |
| `case_id` | UUID | no | Finding/case aggregate |
| `tenant_id` | UUID | no | Tenant owner |
| `branch_id` | UUID | no | Case branch |
| `action` | TEXT | no | `CREATED`, `ACKNOWLEDGED`, `INVESTIGATION_UPDATED`, `RESOLVED`, `DISMISSED`, `REOPENED` |
| `from_state` | TEXT | yes | Previous state |
| `to_state` | TEXT | no | New state |
| `actor_snapshot` | JSONB | no | Safe operator snapshot |
| `reason` | TEXT | no | Bounded transition reason |
| `note` | TEXT | yes | Bounded redacted investigation note |
| `evidence_version` | BIGINT | yes | Evidence version used |
| `case_version` | BIGINT | no | Version after transition |
| `correlation_id` | UUID | no | Trace reference |
| `created_at` | TIMESTAMPTZ | no | Immutable transition time |

### 7.8 `audit_exports`, `legal_holds`, `retention_operations`

| Table | Required columns/descriptions |
|---|---|
| `audit_exports` | `id` operation ID; `tenant_id`; `scope_key`; `from/to`; `data_classes`; `format`; `status`; `object_key`; `checksum`; `expires_at`; `idempotency_key`; `audit_reference_id`; timestamps |
| `legal_holds` | `id`; `tenant_id`; `scope_key`; `data_classes`; `from/to`; `status`; `reason`; `requested_by`; `approved_by`; `active_at`; `released_at`; timestamps |
| `retention_operations` | `id`; `tenant_id`; `scope_key`; `data_class`; `partition/window`; `operation`; `hold_decision`; `status`; `row_count`; `archive_ref`; `error_code`; `actor/workload`; timestamps |

## 8. Constraints, indexes and integrity

### 8.1 Required constraints

- Every evidence/finding/case/export/hold row is tenant-scoped; branch scope is mandatory unless the event is explicitly tenant-wide/security-scoped.
- `event_type`, `action`, `outcome`, `rule_family`, `state`, `severity`, `data_quality_status` use allowlisted enums/check constraints.
- `source_event_id` and applicable `source_key` are unique within producer/tenant scope.
- `source_version`/`watermark_version` cannot decrease for the same source/scope/projection.
- Historical `audit_events` and `case_actions` have no application update/delete path; retention uses partition/archive operation under restricted workload.
- `currency='VND'` for MVP money summaries; all minor amounts non-negative; allocation invariant is explicit.
- Finding key is unique per `(tenant_id, branch_id, finding_key, revision)`; only one current pointer per case.
- Case transition is valid only according to state graph and expected CAS version.
- One active rule version per `(tenant_id, branch_id/default, rule_family)`; published rule versions immutable.
- Legal hold blocks archive/delete for overlapping tenant/scope/data class/time range.
- Digest batch has deterministic ordered range and unique `(tenant_id, scope_key, from, to, canonicalization_version)`.

### 8.2 Required indexes

| Name | Columns | Purpose |
|---|---|---|
| `idx_audit_timeline` | tenant, branch, occurred_at DESC, id DESC | 90-day timeline |
| `idx_audit_action_time` | tenant, branch, action, occurred_at DESC | Action investigation |
| `idx_audit_actor_time` | tenant, branch, actor_key, occurred_at DESC | Employee audit |
| `idx_audit_operator_time` | tenant, branch, effective_operator_key, occurred_at DESC | Quick-switch accountability |
| `idx_audit_shift_device` | tenant, branch, shift_id, device_id, occurred_at DESC | Drawer/shift investigation |
| `uq_audit_source_event` | source_name, source_event_id | Source dedupe |
| `idx_inbox_status` | tenant, status, received_at | Retry/DLQ operations |
| `idx_watermark_health` | tenant, scope_key, projection_name, last_updated_at | Freshness alert |
| `idx_findings_queue` | tenant, branch, state, severity, detected_at DESC | Case queue |
| `idx_findings_subject` | tenant, branch, subject_key, detected_at DESC | Employee findings |
| `uq_finding_key` | tenant, branch, finding_key, revision | Finding idempotency |
| `idx_case_actions` | tenant, branch, case_id, created_at DESC | Case timeline |
| `idx_digest_verify` | tenant, scope_key, verification_status, created_at | Integrity worker |
| `idx_exports_status` | tenant, status, created_at | Export worker |
| `idx_holds_range` | tenant, scope_key, status, active_at, released_at | Hold evaluation |

## 9. Digest and canonicalization contract

### 9.1 Canonical record

Digest includes only the following allowlisted fields from `audit_events`, in this order:

```text
tenantId, branchId, id, eventType, eventVersion, action, outcome,
aggregateType, aggregateId, aggregateVersion, sourceName, sourceEventId,
sourceVersion, actorKey, effectiveOperatorKey, deviceId, shiftId,
resourceType, resourceId, reasonHash, beforeSummaryCanonical,
afterSummaryCanonical, moneySummaryCanonical, qualityCode, correlationId,
causationId, redactionVersion, normalizationVersion, occurredAt, createdAt
```

- `canonicalizationVersion=audit-c14n-v1`.
- JSON object keys are sorted; absent nullable fields use explicit `null`; arrays are ordered by schema-defined stable key.
- `reason` is represented by a keyed redacted hash/reference, never raw free text in digest payload.
- Events sort by `(occurred_at ASC, id ASC)` within a batch.
- Algorithm is SHA-256 over length-delimited canonical records, then chained with `previousDigest` for the same scope.

### 9.2 Verification

Verification recomputes a bounded batch, compares digest and event count/first/last IDs, writes a verification record and emits `AuditIntegrityMismatchDetected.v1` on mismatch. Verification never repairs by editing evidence.

## 10. Anomaly rule policy contract

### 10.1 Rule policy schema

```json
{
  "ruleFamily": "DISCOUNT_SPIKE",
  "version": 1,
  "status": "ACTIVE",
  "timezonePolicy": "BRANCH_LOCAL",
  "observationWindow": "BUSINESS_DAY",
  "comparisonWindow": "ROLLING_28_COMPARABLE_DAYS",
  "dimensions": ["TENANT", "BRANCH", "EFFECTIVE_OPERATOR", "LOCAL_DAYPART"],
  "minimumSample": 30,
  "warmupDays": 28,
  "threshold": {"minZScore": "3.0000", "minRatio": "3.0000"},
  "severityMap": {"3": "MEDIUM", "4": "HIGH", "5": "CRITICAL"},
  "suppressionMinutes": 1440,
  "qualityOnInsufficient": "INSUFFICIENT_BASELINE"
}
```

Threshold values are versioned configuration, not hidden code constants. A tenant/branch may publish a new rule version; historical findings retain the policy version used.

### 10.2 Initial rule families

| Rule | Current value | Baseline | Trigger/evidence |
|---|---|---|---|
| `CANCEL_SPIKE` | Cancel count/rate/amount per business day/daypart | Rolling 28 comparable branch/operator windows | Minimum sample + `z >= minZScore` and ratio threshold |
| `DISCOUNT_SPIKE` | Manual discount ratio/amount; automatic sources separate | Same dimensions; VND allocation | Minimum sample + z/ratio threshold; evidence includes source/version |
| `DRAWER_FREQUENCY` | No-sale/manual opens per shift/daypart | Branch/device/operator comparable windows | z/absolute threshold or outside active shift; missing shift is low-quality |
| `REPEATED_ACTION` | Same action family on same order/shift | Deterministic short window | At least 3 actions within 10 minutes; group by finding key |

`INSUFFICIENT_BASELINE` never creates a high-confidence anomaly. Duplicate/failed/rejected/no-op facts are excluded from successful-action rate unless rule policy explicitly includes them.

## 11. Case state machine contract

```text
OPEN ── acknowledge ──> ACKNOWLEDGED ── investigate ──> INVESTIGATING
  │                                                   │
  │                                                   ├─ Owner resolve ──> RESOLVED
  │                                                   ├─ Owner dismiss ──> DISMISSED
  │                                                   └─ Owner reopen ────> OPEN
  └─ Owner dismiss ─────────────────────────────────────────────────────> DISMISSED
```

| Transition | Allowed actor | Required |
|---|---|---|
| create → `OPEN` | Loss worker | deterministic finding/evidence/rule version |
| `OPEN` → `ACKNOWLEDGED` | Owner/Manager in scope | reason, CAS version |
| `ACKNOWLEDGED` → `INVESTIGATING` | Owner/Manager in scope | reason/note, CAS version |
| `INVESTIGATING` → `RESOLVED` | Owner only | reason/resolution note, CAS version |
| `OPEN/ACKNOWLEDGED/INVESTIGATING` → `DISMISSED` | Owner only | mandatory dismissal reason, CAS version |
| `RESOLVED/DISMISSED` → `OPEN` | Owner only | reopen reason and new evidence/revision |

Every transition inserts `case_actions`, advances current case `version`, emits `AuditCaseActionRecorded.v1` through outbox and is itself visible in the audit timeline.

## 12. Retention, archive and legal hold contract

| Data class | Minimum retention | Archive/delete rule | Legal hold |
|---|---:|---|---|
| `AUDIT_EVIDENCE` | 7 years | Monthly partitions/archive; delete only after expiry and no hold | Hold wins |
| `CASE_ACTIONS` | 7 years | Retain with related evidence/case lineage | Hold wins |
| `DIGEST_BATCHES` | 7 years | Retain digest/checkpoint with evidence window | Hold wins |
| `FINDINGS` | 7 years | Retain revisions and dismissal/resolution reason | Hold wins |
| `EXPORT_ARTIFACT` | 24 hours default after completion | Short-lived encrypted object, revoke/expire; no long-term copy by default | Hold blocks deletion if explicitly held |
| `INBOX/TELEMETRY` | 90 days or operational policy | Delete/archive after replay window | Hold only if referenced by case |
| `RULE/BASELINE_POLICY` | Policy/evidence lineage minimum 7 years | Published versions immutable; retire, not delete while referenced | Hold wins |

Legal hold requires tenant/scope/data class/range/reason/actor, has `ACTIVE`/`RELEASED` state and cannot be released by Manager. Retention jobs are idempotent and record row/partition counts, hold decision, archive reference and outcome.

## 13. Migration and operational artifacts

Migration order:

1. Create tenant/scope enums, common operation/idempotency/outbox/inbox tables and restricted roles.
2. Create `audit_events` append-only table, source dedupe constraints, comments and monthly partition strategy.
3. Create query projection, watermarks and health indexes.
4. Create digest batches/verification tables and integrity permissions.
5. Create audit/rule policy versions, baselines, findings, cases and case action constraints.
6. Create exports/legal holds/retention operations and encrypted object-storage references.
7. Add producer compatibility fixtures for Order, Promotion/Loyalty, Payment, Shift/POS Device and Identity.
8. Seed only deterministic rule-policy fixtures; production thresholds require versioned publish/audit.
9. Validate Flyway migrations on empty and copied databases; use expand → migrate → contract for future changes.

## 14. Contract test matrix

| Area | Required fixtures/tests |
|---|---|
| Source events | Canonical action route, generic route, no double evidence, event/source version, unknown version, redaction rejection |
| Cancel | Line/terminal cancel, reason, before/after, duplicate/out-of-order, rejected cancel |
| Discount | Promotion/voucher/manual, VND minor units, allocation, partial/unallocated, missing reason, version change |
| Drawer | Sale/no-sale/refund/opening/maintenance, command/result delay, hardware failure, operation retry, missing shift/operator |
| Ingest | At-least-once, inbox atomicity, duplicate source key, retry/backoff, DLQ/replay, DB outage |
| Authorization | Owner tenant/subset, Manager branch, foreign IDs, revoked membership, permission/role escalation, cache/cursor/export scope |
| API | Envelopes, filter allowlist, 30/90-day bounds, cursor mismatch, 401/403/404/409/422/429/503, ETag after auth |
| Integrity | Canonical bytes, ordering, SHA-256, previous digest, verification success/mismatch, no silent repair |
| Retention | Seven-year matrix, legal hold overlap, export expiry/revoke, idempotent job, archive/delete audit |
| Rules | Each rule family, warm-up/min sample, zero baseline, threshold/severity, suppression, timezone/DST, branch/operator scope |
| Findings | Deterministic key, duplicate evaluation, late revision, partial source, insufficient baseline, safe explanation |
| Cases | Manager/Owner permissions, state graph, CAS conflict, required reason, reopen, append-only transitions |
| Privacy | No JWT/PIN/phone/payment secret/raw payload in DB/API/logs/traces/events/object storage |
| Performance | Ingest lag ≤60s normal, list/detail p95 ≤2s bounded, 90-day query, concurrent users, export throughput |
| UI | FRESH/STALE/PARTIAL/UNAVAILABLE/NO_DATA/INSUFFICIENT_BASELINE, safe errors, scope and case state rendering |

## 15. Parent contract amendments

### POS Platform/Audit

- Add canonical action event registry, source routing/dedupe rule and expanded `AuditRecorded.v1` compatibility schema.
- Replace minimal `audit_events` skeleton with Audit-owned schema reference; add `pos-audit-events` producer/consumer compatibility fixtures.
- Add Audit permissions, API/read-model ownership, digest, retention/legal-hold and restricted operation contracts.

### Order

- Add `DiscountApplied.v1` with source/version/allocation/actor/reason and keep cancel events authoritative for line cancellation.
- Ensure cancel/discount events carry stable source/aggregate version, branch, before/after safe summaries and correlation.

### Promotion/Loyalty

- Preserve immutable promotion/rule/voucher version and applied allocation; distinguish automatic discount from manual override.

### Payment/Shift/POS Device

- Define `CashDrawerOpened.v1` producer ownership, operation key, command/result/hardware outcome and device/shift/operator context.
- Publish only safe payment/refund/reconciliation/cash facts needed for audit/anomaly context; never raw provider credentials/signing payload.

### Identity

- Add Audit/Loss permissions and current scope/authorization version/revocation decision fields.
- Provide safe historical actor/membership snapshot contract without requiring Audit to query current PII.

## 16. Implementation artifacts required before code

- `openapi.yaml` for public Audit timeline/findings/case/export endpoints.
- `openapi-internal.yaml` for health/rebuild/integrity/retention operations.
- `asyncapi.yaml` and JSON Schemas for canonical source events, generic `AuditRecorded`, produced evidence/finding/case/integrity events.
- `audit-event-registry.yaml` mapping canonical producer route, dedupe key, required fields and redaction policy for every action.
- `audit-rule-policy.schema.yaml` plus deterministic fixtures for all initial rule families and cold start.
- `audit-metric/data-quality.schema.yaml` for watermark/status semantics.
- PostgreSQL/Flyway migrations with column comments, append-only roles, constraints, indexes and partition checks.
- Digest canonicalization test vector: input rows, canonical bytes/hash, previous digest and expected verification outcome.
- Golden fixtures for source commit/outbox, duplicate/replay, drawer command/result, authorization, retention/legal hold and case CAS.
- Load profile for 90-day interactive query, seven-year export, event ingest and anomaly worker.
- Security/privacy corpus for IDOR, revoked membership, cache leakage, PII/secret redaction and export revocation.

## 17. Contracts approval gate

- **Current status:** Approved — user accepted the Contracts review warnings.
- **Required before Finalization:** Completed; machine-readable schemas, test vectors, parent amendments and migration artifacts remain implementation gates listed in the final result.
- **Implementation rule:** Không viết controller/consumer/migration/anomaly worker trực tiếp từ prose; generate machine-readable contracts and fixtures first.
