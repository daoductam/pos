Started at: 2026/10/13 00:00:00
Finished at: 2026/10/13 00:00:00
Total time: 0 minutes
---

# Module 9 — Trợ lý AI cho chủ quán: API & Data Contracts

**Ngày:** 2026-10-13  
**Trạng thái:** Approved — warning remediation applied  
**Vision:** [AI Assistant Vision](./2026-10-13-vision.md)  
**Requirements:** [AI Assistant Requirements](./2026-10-13-requirements.md)  
**Architecture:** [AI Assistant Architecture](./2026-10-13-architecture.md)  
**Sequence diagrams:** [AI Assistant Sequences](./2026-10-13-sequence-diagrams.adoc)

Tài liệu này là nguồn chuẩn để sinh public/internal OpenAPI 3.1, Reporting tool JSON Schemas, AsyncAPI/JSON Schema, provider policy fixtures và PostgreSQL migrations cho AI Assistant Service. Reporting, Identity và parent domain contracts là integration owners; AI Assistant không tạo shared database hoặc metric source of truth.

## 1. Contract conventions

### 1.1 API and encoding

- Public REST + OpenAPI 3.1 dưới `/api/v1`.
- Internal REST + OpenAPI 3.1 dưới `/internal/v1`, workload identity/mTLS, không expose qua Angular.
- Events dùng parent event envelope + AsyncAPI/JSON Schema; delivery at-least-once.
- IDs là UUID v7 strings; timestamp là ISO-8601 UTC.
- `businessDate` là `YYYY-MM-DD` theo IANA timezone của branch; browser timezone không có authority.
- Money dùng `int64` minor units + `currency`; P0 currency `VND`.
- Ratios/percentages dùng fixed-scale decimal/string; không dùng binary floating point cho money hoặc displayed KPI.
- Breaking change tạo major URI/event version; optional additive fields phải backward-compatible.

### 1.2 Headers

| Header | Public | Internal | Meaning |
|---|---:|---:|---|
| `Authorization` | required | required | Cognito JWT hoặc workload credential/mTLS |
| `X-Active-Branch-Id` | optional | no authority | UI selection; server/Identity re-checks membership |
| `X-Correlation-Id` | recommended | required | Trace request/tool/provider/event |
| `X-Causation-Id` | no | recommended | Command/event gây ra operation |
| `Idempotency-Key` | required for POST turn/rerun | required for job/mutation | Scope tenant + operation + actor/workload |
| `If-Match` | summary revision mutation if used | versioned command | Optimistic version |
| `Content-Type` | body | body | `application/json; charset=utf-8` |

Client không được gửi `tenantId`, role, membership version hoặc branch ownership như authority. `requestedScope` chỉ là input được Identity validate. LLM function arguments không được chứa quyền hoặc scope authority.

### 1.3 Success/error envelopes

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
    "code": "scope_not_allowed",
    "message": "The requested report scope is not available",
    "details": [],
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

Error messages không tiết lộ branch/tenant ngoài authorized scope, JWT, prompt, SQL, table, phone, model artifact, provider secret hoặc raw tool payload.

### 1.4 Pagination and caching

- Summary list dùng cursor pagination; default `20`, max `100`, sort `(businessDate DESC, branchId ASC, summaryId DESC)`.
- Conversation turn history dùng cursor pagination; default `20`, max `100`.
- AI answer/summary không public-cache. Internal tool responses không qua browser cache.
- ETag chỉ được dùng cho safe summary revision nếu resource authorization vẫn được kiểm tra trước conditional response; không dùng ETag để bypass revocation.

### 1.5 HTTP/error taxonomy

| HTTP | Code | Meaning | Retry |
|---:|---|---|---:|
| 200 | — | Read/answer/idempotent replay | no |
| 201 | — | Conversation/job created | no |
| 202 | `turn_queued` | Turn accepted, polling required | poll |
| 202 | `summary_queued` | Summary rerun accepted | poll |
| 400 | `validation_error` | Invalid shape/date/field | no |
| 401 | `unauthorized` | Missing/invalid/expired JWT | refresh auth |
| 403 | `forbidden` | Role/tenant/branch not allowed | no |
| 404 | `not_found` | Resource absent in authorized scope | no |
| 409 | `turn_expired` | Conversation/turn TTL expired | new turn/session |
| 409 | `idempotency_conflict` | Same key, different canonical request | no |
| 409 | `summary_revision_conflict` | Rerun/version no longer current | reread |
| 409 | `summary_not_ready` | Completeness/cutoff not met | poll/rerun |
| 409 | `scope_required` | Need explicit branch/period | supply scope |
| 409 | `scope_not_allowed` | Requested branch outside membership | no |
| 409 | `tool_not_allowed` | Intent/tool not in policy registry | no |
| 409 | `tool_arguments_invalid` | Function arguments fail schema/range | no |
| 409 | `report_freshness_invalid` | Report is stale/incomplete for requested policy | bounded retry |
| 409 | `privacy_redaction_failed` | Safe payload cannot be guaranteed | no; fail closed |
| 409 | `budget_exceeded` | Turn/tenant provider or query budget exceeded | later |
| 422 | `unsupported_intent` | Question outside allowlist | no |
| 422 | `ambiguous_period` | Period cannot be resolved by policy | clarify |
| 429 | `rate_limited` | User/tenant/provider rate limit | Retry-After |
| 503 | `authorization_unavailable` | Identity decision unavailable | bounded retry |
| 503 | `reporting_unavailable` | Reporting tool unavailable | bounded retry |
| 503 | `provider_unavailable` | DeepSeek unavailable/timeout/circuit open | structured fallback |
| 503 | `service_unavailable` | AI DB/broker unsafe | bounded retry |

`NO_DATA`, `STALE_DATA`, `PARTIAL`, `UNSUPPORTED` và `DATA_LATE` là safe domain states trong `data.status`, không nhất thiết là HTTP 4xx.

## 2. Authorization contract

### 2.1 Public permissions

| Permission | Owner | Manager | Cashier | Kitchen | Scope |
|---|---:|---:|---:|---:|---|
| `ai:report:read` | yes | assigned branches | no | no | Tenant or assigned branch |
| `ai:summary:read` | yes | assigned branches | no | no | Tenant or assigned branch |
| `ai:summary:rerun` | yes | assigned branches | no | no | Same as summary read; rate-limited |
| `ai:audit:read` | via Audit policy | via Audit policy | no | no | Audit service owns final permission |
| `ai:provider:operate` | restricted ops | no | no | no | Internal/ops only |

Identity Access remains authority for role/membership. AI Assistant calls parent `POST /internal/v1/authorization-decisions` per protected conversation/turn with permission and requested scope. Reporting rechecks tenant/branch predicates and rejects unsigned/expired/mismatched workload scope.

### 2.2 Resolved scope schema

```json
{
  "scopeType": "BRANCH",
  "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789001",
  "branchIds": ["0192b2d0-7f1d-7abc-8d2c-123456789002"],
  "subjectId": "0192b2d0-7f1d-7abc-8d2c-123456789003",
  "role": "MANAGER",
  "permission": "ai:report:read",
  "membershipVersion": 12,
  "authorizationVersion": 7,
  "resolvedAt": "2026-10-13T08:00:00Z",
  "expiresAt": "2026-10-13T08:05:00Z"
}
```

`TENANT` scope is valid only for Owner. Wildcard branch IDs are invalid. A turn stores a scope revision; it cannot be widened after tool execution starts.

## 3. Public API surface

| Method | Path | Auth/scope | Request | Response | Status |
|---|---|---|---|---|---|
| POST | `/api/v1/ai/conversations` | `ai:report:read` | `CreateConversationRequest` | `ConversationResponse` | 201, 401, 403, 409, 422 |
| GET | `/api/v1/ai/conversations/{conversationId}` | owner + same scope | — | `ConversationResponse` | 200, 403, 404 |
| GET | `/api/v1/ai/conversations/{conversationId}/turns` | owner + same scope | cursor/filter | `TurnPage` | 200, 403, 404, 422 |
| POST | `/api/v1/ai/conversations/{conversationId}/turns` | `ai:report:read` | `CreateTurnRequest` | `TurnResponse` | 200, 202, 403, 404, 409, 422, 429, 503 |
| GET | `/api/v1/ai/conversations/{conversationId}/turns/{turnId}` | owner + same scope | — | `TurnResponse` | 200, 403, 404 |
| GET | `/api/v1/ai/daily-summaries` | `ai:summary:read` | cursor/date/branch filter | `SummaryPage` | 200, 403, 422 |
| GET | `/api/v1/ai/daily-summaries/{summaryId}` | owner + same scope | — | `SummaryResponse` | 200, 403, 404 |
| POST | `/api/v1/ai/daily-summaries/{summaryId}/reruns` | `ai:summary:rerun` | `RerunSummaryRequest` | `SummaryJobResponse` | 202, 403, 404, 409, 422, 429, 503 |

Public API không expose tool registry, system prompt, provider prompt, raw function payload, arbitrary SQL hoặc provider health internals.

## 4. Public API schemas

### 4.1 Create conversation

`POST /api/v1/ai/conversations`

```json
{
  "requestedScope": {
    "type": "BRANCH",
    "branchIds": ["0192b2d0-7f1d-7abc-8d2c-123456789002"]
  },
  "locale": "vi-VN",
  "clientReference": "owner-dashboard-chat-1"
}
```

| Field | Type | Required | Rule |
|---|---|---:|---|
| `requestedScope.type` | enum | yes | `TENANT` or `BRANCH` |
| `requestedScope.branchIds` | UUID[] | conditional | Required for `BRANCH`; max 20; server validates |
| `locale` | string | no | Allowlist; default `vi-VN` |
| `clientReference` | string | no | Max 128; UI trace only; no PII |

Server ignores client tenant/role/permission and returns a server-resolved scope revision.

### 4.2 ConversationResponse

```json
{
  "data": {
    "conversationId": "0192b2d0-7f1d-7abc-8d2c-123456789010",
    "status": "ACTIVE",
    "scope": {
      "type": "BRANCH",
      "branchIds": ["0192b2d0-7f1d-7abc-8d2c-123456789002"],
      "authorizationVersion": 7
    },
    "policyVersion": "ai-policy-v1",
    "expiresAt": "2026-10-13T10:00:00Z",
    "createdAt": "2026-10-13T08:00:00Z"
  },
  "meta": {"requestId": "req_01J...", "correlationId": "cor_01J..."}
}
```

Response không trả `subjectId`, membership list, Cognito subject, raw prompt hoặc provider details.

### 4.3 Create turn

`POST /api/v1/ai/conversations/{conversationId}/turns`

```json
{
  "clientTurnId": "turn-client-0004",
  "question": "Tuần này doanh thu chi nhánh Quận 1 thay đổi thế nào so với cùng thứ tuần trước?",
  "requestedPeriod": {
    "type": "RELATIVE_WEEK"
  },
  "requestedScope": null,
  "responseMode": "TEXT_WITH_PROVENANCE"
}
```

| Field | Type | Required | Rule |
|---|---|---:|---|
| `clientTurnId` | string | yes | Max 128; unique per conversation; idempotency identity |
| `question` | string | yes | 1–4,000 Unicode chars; treated as untrusted input |
| `requestedPeriod` | object/null | no | Server resolves exact period/timezone |
| `requestedScope` | object/null | no | Must be subset of current authorized scope |
| `responseMode` | enum | no | `TEXT_WITH_PROVENANCE`; no raw/tool mode in MVP |

Server normalizes dates, resolves scope again, validates policy and then executes. Raw question is not persisted; only keyed hash + redacted/structured semantic context is retained.

### 4.4 TurnResponse

```json
{
  "data": {
    "turnId": "0192b2d0-7f1d-7abc-8d2c-123456789011",
    "conversationId": "0192b2d0-7f1d-7abc-8d2c-123456789010",
    "status": "ANSWERED",
    "answer": {
      "text": "Doanh thu ...",
      "headline": "Doanh thu giảm 12,4%",
      "warnings": []
    },
    "scope": {
      "type": "BRANCH",
      "branchIds": ["0192b2d0-7f1d-7abc-8d2c-123456789002"]
    },
    "period": {
      "from": "2026-10-05",
      "to": "2026-10-11",
      "timezone": "Asia/Ho_Chi_Minh"
    },
    "sources": [
      {
        "tool": "getSalesSummary",
        "watermark": "reporting:branch:42:v901",
        "watermarkVersion": 901,
        "lastUpdatedAt": "2026-10-12T17:00:00Z",
        "calculationVersion": "sales-metric-v2"
      }
    ],
    "provider": {
      "modelVersion": "configured-model-reference",
      "policyVersion": "ai-provider-policy-v1"
    },
    "warnings": [],
    "createdAt": "2026-10-13T08:00:05Z"
  },
  "meta": {"requestId": "req_01J...", "correlationId": "cor_01J..."}
}
```

Allowed `status`: `QUEUED`, `RUNNING`, `ANSWERED`, `NEEDS_SCOPE`, `UNSUPPORTED`, `NO_DATA`, `STALE_DATA`, `PARTIAL`, `PROVIDER_UNAVAILABLE`, `EXPIRED`, `FAILED`.

`202` response returns same `turnId`, status and polling URL; it never marks incomplete prose as final.

Queued response shape is normative:

```json
{
  "data": {
    "turnId": "0192b2d0-7f1d-7abc-8d2c-123456789011",
    "conversationId": "0192b2d0-7f1d-7abc-8d2c-123456789010",
    "status": "QUEUED",
    "pollUrl": "/api/v1/ai/conversations/0192b2d0-7f1d-7abc-8d2c-123456789010/turns/0192b2d0-7f1d-7abc-8d2c-123456789011",
    "pollAfterMs": 500,
    "expiresAt": "2026-10-13T08:00:30Z"
  },
  "meta": {"requestId": "req_01J...", "correlationId": "cor_01J..."}
}
```

The client polls until a terminal status (`ANSWERED`, `NEEDS_SCOPE`, `UNSUPPORTED`, `NO_DATA`, `STALE_DATA`, `PARTIAL`, `PROVIDER_UNAVAILABLE`, `EXPIRED` or `FAILED`). `pollAfterMs` is bounded to `250..5000`; an expired turn returns `409 turn_expired`. `PROVIDER_UNAVAILABLE` and `FAILED` contain only `safeFacts`/`warningCodes` when available and never an unvalidated answer. There is no successful final answer with `QUEUED` or `RUNNING` status.

### 4.4.1 Normative safe answer schema

`answer_payload` is accepted only when it matches this shape and its claims can be reconciled to the stored tool facts:

```json
{
  "headline": "Doanh thu giảm 12,4%",
  "text": "Doanh thu ...",
  "claims": [
    {"key": "net_revenue_delta_ratio", "value": "-0.124", "sourceRef": "getSalesSummary#1"}
  ],
  "sourceRefs": ["getSalesSummary#1"],
  "warnings": []
}
```

`headline`, `text`, `claims`, `sourceRefs` and `warnings` are required; `claims[].sourceRef` must refer to an accepted tool execution in the same turn. Numeric values in prose are checked against the claims/facts after the configured rounding policy. The validator rejects phone-like strings, raw UUID/order/payment identifiers, SQL, prompt-instruction text and unsupported claims. Failed redaction or reconciliation is `privacy_redaction_failed`/`provider_output_invalid` and fails closed.

### 4.5 Summary list/detail

`GET /api/v1/ai/daily-summaries`

Allowlisted filters: `branchId` (Owner scope only or assigned Manager branch), `businessDateFrom`, `businessDateTo`, `status`, `cursor`, `limit`. Server rejects dates outside retention/role policy.

`SummaryResponse` includes:

- `summaryId`, `tenantId` omitted from UI unless parent envelope policy allows it;
- `branchId`, `businessDate`, `timezone`;
- `status`: `PUBLISHED`, `NO_BASELINE`, `DATA_LATE`, `PROVIDER_UNAVAILABLE`, `FAILED`, `SUPERSEDED`; `READY` chỉ là status của `ai_daily_summary_jobs`, không phải summary revision;
- `revision`, `supersedesSummaryId`, `supersededBySummaryId`;
- validated `safeContent` (headline, actual/baseline, drivers, caveats);
- source watermarks/calculation/policy/model versions;
- `generatedAt`, `publishedAt`, `lastUpdatedAt`.

`safeContent` is normative and contains only:

```json
{
  "headline": "Doanh thu giảm 12,4%",
  "actual": {"metric": "NET_REVENUE", "valueMinor": 12400000, "currency": "VND"},
  "baseline": {"type": "SAME_WEEKDAY_LAST_WEEK", "valueMinor": 14155200},
  "deltaRatio": "-0.124",
  "drivers": [{"safeLabel": "Trà sữa truyền thống", "signedDeltaMinor": -600000, "causalEvidence": false}],
  "caveats": ["Dữ liệu đã hoàn tất đến 17:00 giờ địa phương."],
  "wordingMode": "OBSERVED_CONTRIBUTION"
}
```

`wordingMode=OBSERVED_CONTRIBUTION` is mandatory unless every driver has explicit `causalEvidence=true`. `safeContent` is schema-validated and PII/identifier-scanned before the summary becomes visible.

### 4.6 Summary rerun

`POST /api/v1/ai/daily-summaries/{summaryId}/reruns`

```json
{
  "reason": "Late OrderPaid projection completed",
  "expectedRevision": 1,
  "requestedSourceWatermark": "reporting:branch:42:v902"
}
```

Rerun is allowed only inside current scope and when source watermark/policy allows. Same idempotency key returns the same `summaryJobId`; stale expected revision returns `409 summary_revision_conflict`.

## 5. Internal Identity authorization contract

AI Assistant consumes parent Identity contract. The canonical field names are the parent names below; older draft names (`subjectId`, `permission`) are not valid wire fields.

`POST /internal/v1/authorization-decisions`

Request minimum:

```json
{
  "subject": "cognito-sub-opaque",
  "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789001",
  "requestedBranchIds": ["0192b2d0-7f1d-7abc-8d2c-123456789002"],
  "requiredPermission": "ai:report:read",
  "operation": "AI_REPORT_TURN",
  "correlationId": "0192b2d0-7f1d-7abc-8d2c-123456789020"
}
```

Response minimum:

```json
{
  "data": {
    "allowed": true,
    "denialCode": null,
    "resolvedScope": {
    "scopeType": "BRANCH",
    "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789001",
    "branchIds": ["0192b2d0-7f1d-7abc-8d2c-123456789002"],
    "role": "MANAGER",
    "permission": "ai:report:read",
    "membershipVersion": 12,
    "authorizationVersion": 7,
    "expiresAt": "2026-10-13T08:05:00Z"
    }
  },
  "meta": {"requestId": "req_01J...", "correlationId": "cor_01J..."}
}
```

The parent Identity amendment adds `requestedBranchIds[]` for a bounded multi-branch request while retaining `branchId` as a compatibility alias only for exactly one branch. `requiredPermission` is canonical; `permission` in `resolvedScope` is the returned decision field. The parent response adds `authorizationVersion` and `resolvedScope`; the `data`/`meta` envelope is required.

AI Assistant maps `allowed=false` to safe `403`/`NEEDS_SCOPE` without revealing foreign branch existence. Identity remains online authority; local cache cannot authorize after revocation. Contract fixtures must reject a request containing both `branchId` and `requestedBranchIds`, a wildcard branch, or a subject/tenant mismatch.

## 6. Reporting tool contracts

### 6.1 Internal endpoint

`POST /internal/v1/reporting/ai-tools/{toolName}`

Workload caller: AI Assistant Tool Gateway. `toolName` is accepted only from registry version configured in AI Assistant and Reporting; unknown/disabled names return `tool_not_allowed`.

Request envelope:

```json
{
  "toolVersion": "report-tools-v1",
  "registryVersion": "report-tool-registry-v1",
  "registryDigest": "sha256:tool-registry-digest",
  "resolvedScope": {
    "scopeType": "BRANCH",
    "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789001",
    "branchIds": ["0192b2d0-7f1d-7abc-8d2c-123456789002"],
    "membershipVersion": 12,
    "authorizationVersion": 7
  },
  "arguments": {
    "businessDateFrom": "2026-10-05",
    "businessDateTo": "2026-10-11",
    "timezone": "Asia/Ho_Chi_Minh"
  },
  "correlationId": "0192b2d0-7f1d-7abc-8d2c-123456789020"
}
```

Reporting must validate trusted workload, tenant, branch scope, `registryVersion`, `registryDigest`, tool version, date/range and repository predicates. It must not accept SQL/table/order expressions. A registry digest mismatch returns `tool_registry_version_mismatch`; an unknown or disabled tool returns `tool_not_allowed`.

### 6.2 Common `DataQualityEnvelope`

| Field | Type | Required | Meaning |
|---|---|---:|---|
| `freshnessStatus` | enum | yes | `FRESH`, `STALE`, `INCOMPLETE`, `UNAVAILABLE` |
| `completenessStatus` | enum | yes | `COMPLETE`, `PARTIAL`, `MISSING` |
| `projectionVersion` | int64 | yes | Rebuild/projection version |
| `watermark` | string | yes | Source checkpoint |
| `watermarkVersion` | int64 | yes | Monotonic checkpoint used for revision ordering within the scope |
| `lastUpdatedAt` | timestamp | yes | Projection update UTC |
| `missingSources` | string[] | yes | Safe source names only |
| `calculationVersion` | string | yes | Metric dictionary/version |

Every tool response is wrapped in:

```json
{
  "tool": "getSalesSummary",
  "toolVersion": "report-tools-v1",
  "registryVersion": "report-tool-registry-v1",
  "registryDigest": "sha256:tool-registry-digest",
  "status": "OK",
  "data": {},
  "dataQuality": {}
}
```

`status` is one of `OK`, `NO_DATA`, `STALE`, `PARTIAL`, `UNAVAILABLE`. The `dataQuality.watermarkVersion` is the only value AI Assistant may use to compare source freshness/revision order; the opaque `watermark` is retained for replay and audit.

### 6.3 Tool schemas

All tool arguments include server-injected scope outside model-controlled fields. Model may provide only fields listed below.

#### `getSalesSummary`

Arguments: `businessDateFrom`, `businessDateTo`, optional `comparison` (`SAME_WEEKDAY_LAST_WEEK`, `SEVEN_DAY_AVERAGE`, `NONE`). Max 31 business days.

Result: `paidOrderCount`, `grossMinor`, `netMinor`, `vatMinor`, `currency`, `comparisonDeltaMinor`, `comparisonDeltaRatio`, `baselinePeriod`, `dataQuality`.

#### `getOrderCount`

Arguments: period + optional comparison. Result: `paidOrderCount`, `baselineCount`, `deltaCount`, `deltaRatio`, `dataQuality`.

#### `getAverageOrderValue`

Arguments: period + optional comparison. Result: `aovMinor`, `paidOrderCountDenominator`, `baselineAovMinor`, `deltaMinor`, `deltaRatio`, `roundingVersion`, `currency`, `dataQuality`.

Formula is `netMinor / paidOrderCountDenominator`; zero denominator returns `NO_DATA` and no fabricated zero.

#### `getTopItems`

Arguments: period, `limit` 1–10, `sortBy` allowlist `REVENUE`/`QUANTITY`. Result items contain only safe `itemId` reference, `itemLabel`, `quantitySold`, `revenueMinor`, `currency`, `rank`; no raw order/line/customer IDs.

#### `compareBranches`

Arguments: Owner-only resolved tenant scope, period, optional selected authorized branch IDs, metric allowlist `NET_REVENUE`/`ORDER_COUNT`/`AOV`, limit ≤ 20. Result contains branch safe label/code only for authorized branches, metric, rank, delta, currency and data quality. Foreign branch existence is omitted.

#### `getHourlySales`

Arguments: exactly one authorized branch, period max 7 business days, `hourBucket` 0–23. Result contains local hour bucket, order count, net revenue, timezone, data quality. No exact transaction timestamps.

#### `getContributionDrivers`

Arguments: one business date, baseline period, `dimensions` allowlist `ITEM`/`CATEGORY`/`HOUR`/`BRANCH`, `limit` 1–10, materiality policy version. Result:

```json
{
  "drivers": [
    {
      "dimension": "ITEM",
      "safeLabel": "Trà sữa truyền thống",
      "actualMinor": 1200000,
      "baselineMinor": 1800000,
      "signedDeltaMinor": -600000,
      "contributionRatio": "-0.21",
      "material": true
    }
  ],
  "causalEvidence": false,
  "calculationVersion": "contribution-v1",
  "dataQuality": {}
}
```

No driver is returned as causal proof unless Reporting explicitly sets `causalEvidence=true`; default is false.

### 6.4 Reporting registry and metric dictionary

The registry is a versioned artifact shared by AI Assistant and Reporting, not an independently edited database row in either service:

```json
{
  "registryVersion": "report-tool-registry-v1",
  "registryDigest": "sha256:tool-registry-digest",
  "tools": [
    {
      "name": "getSalesSummary",
      "schemaId": "reporting.getSalesSummary",
      "schemaVersion": 1,
      "enabled": true
    }
  ]
}
```

The generated registry contains all seven tools, argument schemas, result schemas, limits and enabled state. AI Assistant and Reporting refuse requests unless the registry version and digest match. The registry is promoted atomically with the Reporting deployment and recorded in `policy_version`/`tool_version` metadata.

| Metric | Canonical definition | Edge rule | Fixture IDs |
|---|---|---|---|
| `paidOrderCount` | Count of unique finalized `OrderPaid.v1` facts in the Reporting projection | Duplicate event IDs/source keys count once; draft, failed and late Payment settlement do not count | `report-duplicate-order-paid`, `report-payment-late-settlement` |
| `grossMinor` / `netMinor` / `vatMinor` | Reporting projection values from canonical paid-order facts plus approved correction/refund policy | Refund/correction treatment is policy-versioned and returned in `calculationVersion` | `report-full-refund`, `report-partial-refund`, `report-correction` |
| `AOV` | `netMinor / paidOrderCount`; Reporting applies deterministic rounding | Zero denominator returns `NO_DATA`, never fabricated zero | `report-aov-zero-denominator`, `report-aov-rounding` |
| Hour bucket | Branch-local business timezone, integer bucket `0..23` | DST/offset behavior follows branch IANA timezone; no browser timezone | `report-hourly-branch-timezone`, `report-hourly-boundary` |
| Contribution | Reporting returns signed delta, ratio, materiality and causal flag | Tie-break is `dimension`, safe label and stable opaque item reference; LLM does not recalculate | `report-contribution-negative`, `report-contribution-tie`, `report-contribution-materiality` |

The parent Reporting contract must expose these schemas and fixture IDs before an AI Assistant implementation starts.

## 7. Provider adapter contract

Provider adapter is internal and no provider API is exposed to dashboard.

### 7.1 `ProviderPolicy`

| Field | Type | Required | Rule |
|---|---|---:|---|
| `policyVersion` | string | yes | Immutable version |
| `providerName` | enum | yes | `DEEPSEEK` in MVP |
| `modelReference` | string | yes | Config reference; no secret |
| `regionPolicy` | string | yes | Allowed egress/region policy |
| `promptPolicyVersion` | string | yes | Redaction/system policy |
| `maxInputTokens` | int | yes | Hard cap |
| `maxOutputTokens` | int | yes | Hard cap |
| `requestTimeoutMs` | int | yes | MVP ≤ 8,000 |
| `maxRetries` | int | yes | MVP ≤ 1 |
| `dailyBudgetMinor` | int64 | yes | Tenant/provider cost budget |
| `budgetCurrency` | string | yes | Currency used only for provider cost accounting |
| `secretReference` | string | yes | Secrets Manager/KMS reference only |
| `enabled` | boolean | yes | Feature flag |

No provider policy column stores API key, prompt secret, raw provider response or user message.

### 7.2 Provider request/response

Provider request contains `policyVersion`, safe locale, structured semantic context, current redacted question, tool schemas and aggregate facts. It must not contain JWT, phone, customer identity, raw order/payment IDs, SQL, prompt secrets or arbitrary report rows.

Provider response is accepted only if it validates one of:

- `FunctionCallSchema`: known tool name + valid JSON arguments;
- `AnswerSchema`: text/headline/warnings/source references matching structured facts;
- `RefusalSchema`: safe reason code.

One bounded repair call is allowed. Mismatch after repair returns `provider_output_invalid` and a structured, non-prose facts payload when facts are available; it never stores or renders unvalidated provider prose.

### 7.3 Provider usage and budget reservation

Every provider attempt has a unique `providerRequestId` and `attemptId`. Before sending a request, the adapter reserves the estimated cost atomically against the effective tenant policy. After the provider response, the reservation is finalized with actual usage or released on a transport failure.

```json
{
  "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789001",
  "policyVersion": "ai-provider-policy-v1",
  "attemptId": "0192b2d0-7f1d-7abc-8d2c-123456789060",
  "providerRequestId": "0192b2d0-7f1d-7abc-8d2c-123456789061",
  "estimatedCostMinor": 12,
  "budgetCurrency": "USD"
}
```

The reservation result is `RESERVED`, `BUDGET_EXCEEDED` or `POLICY_DISABLED`. Concurrent reservations are serialized per `(tenant_id, policy_version, business_date)`; a failed/expired provider attempt cannot consume the same reservation twice. Usage records include estimated/final input tokens, output tokens, cost minor, currency, retry number, provider request ID, policy version and outcome.

## 8. Daily summary job contracts

### 8.1 Internal job API

| Method | Path | Caller | Status |
|---|---|---|---|
| POST | `/internal/v1/ai/summary-jobs` | Scheduler/ops | 201/202, 409 |
| GET | `/internal/v1/ai/summary-jobs/{jobId}` | Worker/ops | 200, 404 |
| POST | `/internal/v1/ai/summary-jobs/{jobId}/run` | Summary worker | 202, 409, 503 |
| POST | `/internal/v1/ai/provider-health` | Ops/worker | 200, 403, 503 |

### 8.2 Create job

```json
{
  "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789001",
  "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789002",
  "businessDate": "2026-10-12",
  "branchTimezone": "Asia/Ho_Chi_Minh",
  "summaryPolicyVersion": "summary-policy-v1",
  "cutoffAt": "2026-10-12T17:15:00Z",
  "sourceWatermarkHint": "reporting:branch:42:v901"
}
```

Unique business key: `(tenantId, branchId, businessDate, summaryPolicyVersion)`. Scheduler retries return existing job.

### 8.3 Summary statuses

`SCHEDULED`, `WAITING_FOR_DATA`, `READY`, `GENERATING`, `PUBLISHED`, `NO_BASELINE`, `DATA_LATE`, `PROVIDER_UNAVAILABLE`, `FAILED`, `SUPERSEDED`.

`DATA_LATE` is terminal for the current attempt; a new run with a newer source watermark can create a new revision.

## 9. Event contracts

### 9.1 Common envelope

```json
{
  "eventId": "0192b2d0-7f1d-7abc-8d2c-123456789050",
  "eventType": "AIReportTurnCompleted.v1",
  "eventVersion": 1,
  "occurredAt": "2026-10-13T08:00:05Z",
  "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789001",
  "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789002",
  "aggregateType": "AI_TURN",
  "aggregateId": "0192b2d0-7f1d-7abc-8d2c-123456789011",
  "aggregateVersion": 1,
  "scopeSummary": {
    "scopeType": "BRANCH",
    "branchCount": 1,
    "authorizationVersion": 7
  },
  "auditReferenceId": "0192b2d0-7f1d-7abc-8d2c-123456789051",
  "correlationId": "0192b2d0-7f1d-7abc-8d2c-123456789020",
  "causationId": "0192b2d0-7f1d-7abc-8d2c-123456789021",
  "producer": "ai-assistant-service",
  "payload": {}
}
```

`branchId` is nullable only for explicit authorized tenant-wide events; payload still carries safe scope type/count and never foreign branch list.

`scopeSummary` is required on every produced AI event and contains only `scopeType`, `branchCount` and `authorizationVersion`. `auditReferenceId` is required for user/provider/summary actions and points to the redacted immutable audit reference. `aggregateVersion` is the local state version that produced the event; it is not a provider or Reporting projection version.

### 9.2 Consumed events

| Event | Producer | Use | Rule |
|---|---|---|---|
| `AuthorizationContextChanged.v1` | Identity | Invalidate optimization/cache hints | Never sole authorization authority |
| `ReportProjectionWatermarkUpdated.v1` | Reporting | Trigger summary/freshness reevaluation | Query Reporting remains authority |
| `BusinessDayReady.v1` | Reporting/Shift policy | Hint summary scheduler | Completeness endpoint must confirm |

AI Assistant does not consume raw `OrderPaid.v1`/Payment events for metric calculation; Reporting owns those projections.

### 9.3 Produced events

| Event | Payload minimum | Consumers |
|---|---|---|
| `AIReportTurnCompleted.v1` | turn/conversation ID, role, `scopeSummary`, status, tool names, source watermark/version, response hash, policy/model version, `auditReferenceId` | Audit, Reporting/ops |
| `AIAccessRefused.v1` | turn/conversation ID, `scopeSummary`, refusal code, permission/policy version, `auditReferenceId`, correlation | Audit/Security |
| `AIDailySummaryPublished.v1` | summary ID, tenant/branch/date, revision, baseline type, source watermark/version, calculation/policy/model version, `scopeSummary`, `auditReferenceId` | Dashboard/Reporting, Audit |
| `AIDailySummarySuperseded.v1` | old/new summary ID, date/branch, old/new watermark/version, reason, revision, `auditReferenceId` | Dashboard/Reporting, Audit |

No event includes raw question, raw prompt, raw response, phone, customer name, payment identifier, SQL, provider secret, model artifact or arbitrary line data.

### 9.4 Event delivery rules

- Transactional outbox writes event with local state transition.
- Event IDs are stable across retry/replay; occurredAt is original business time.
- Consumers dedupe by event ID and validate tenant/aggregate version.
- Unknown event version goes to quarantine/DLQ; no best-effort mutation.
- Replay preserves original IDs and cannot publish a second summary revision without explicit source/revision key.
- AsyncAPI payload schemas are the normative source for the fields above; table descriptions are not sufficient for code generation.

## 10. PostgreSQL data contract

Database: PostgreSQL owned exclusively by AI Assistant. Every column requires `COMMENT ON COLUMN` matching the descriptions below.

### 10.1 `ai_conversations`

| Column | Type | Null | Description/remark |
|---|---|---:|---|
| `id` | UUID | no | UUID v7 conversation ID |
| `tenant_id` | UUID | no | Tenant owner |
| `created_by_subject_hash` | TEXT | no | Keyed hash/opaque actor reference; never raw Cognito subject |
| `role` | TEXT | no | `OWNER` or `MANAGER` at creation; audit snapshot only |
| `scope_type` | TEXT | no | `TENANT` or `BRANCH` |
| `policy_version` | TEXT | no | AI authorization/tool policy version |
| `authorization_version` | BIGINT | no | Identity authorization context version |
| `status` | TEXT | no | `ACTIVE`, `EXPIRED`, `REVOKED` |
| `client_reference_hash` | TEXT | yes | Hashed UI reference; no raw PII |
| `expires_at` | TIMESTAMPTZ | no | Conversation TTL boundary |
| `created_at` | TIMESTAMPTZ | no | Creation UTC |
| `updated_at` | TIMESTAMPTZ | no | Last state update |

### 10.2 `ai_scope_revisions`

| Column | Type | Null | Description/remark |
|---|---|---:|---|
| `id` | UUID | no | Scope revision ID |
| `tenant_id` | UUID | no | Tenant owner |
| `conversation_id` | UUID | no | Parent conversation |
| `revision` | BIGINT | no | Monotonic revision number |
| `scope_type` | TEXT | no | `TENANT` or `BRANCH` |
| `branch_ids` | JSONB | no | Authorized branch IDs snapshot; never wildcard; no foreign branches |
| `role` | TEXT | no | Authorized role snapshot |
| `permission` | TEXT | no | Permission used for decision |
| `membership_version` | BIGINT | no | Identity membership version |
| `authorization_version` | BIGINT | no | Identity decision version |
| `resolved_at` | TIMESTAMPTZ | no | Decision time |
| `expires_at` | TIMESTAMPTZ | no | Signed context expiry |
| `created_at` | TIMESTAMPTZ | no | Persistence UTC |

### 10.3 `ai_turns`

| Column | Type | Null | Description/remark |
|---|---|---:|---|
| `id` | UUID | no | UUID v7 turn ID |
| `tenant_id` | UUID | no | Tenant owner |
| `conversation_id` | UUID | no | Parent conversation |
| `scope_revision_id` | UUID | no | Immutable resolved scope used |
| `client_turn_id` | TEXT | no | Client idempotency identity, no PII |
| `question_hash` | TEXT | no | Keyed hash of normalized question; raw question not stored |
| `semantic_context` | JSONB | no | Structured intent/period/metric/dimensions only; no raw transcript |
| `intent` | TEXT | yes | Allowlisted normalized intent |
| `requested_period` | JSONB | yes | Safe normalized period |
| `status` | TEXT | no | Turn state machine status |
| `answer_payload` | JSONB | yes | Validated/redacted answer safe for dashboard |
| `response_hash` | TEXT | yes | Hash of accepted safe response |
| `warning_codes` | JSONB | no | Safe freshness/provider/policy warning codes |
| `policy_version` | TEXT | no | Tool/prompt/redaction policy |
| `model_version` | TEXT | yes | Provider model reference, no secret |
| `attempt_count` | INTEGER | no | Bounded orchestrator attempts |
| `expires_at` | TIMESTAMPTZ | no | Turn/session expiry |
| `created_at` | TIMESTAMPTZ | no | Creation UTC |
| `updated_at` | TIMESTAMPTZ | no | Last transition UTC |

Unique source key: `(tenant_id, conversation_id, client_turn_id)`.

### 10.4 `ai_tool_executions`

| Column | Type | Null | Description/remark |
|---|---|---:|---|
| `id` | UUID | no | Tool execution ID |
| `tenant_id` | UUID | no | Tenant owner |
| `turn_id` | UUID | no | Parent turn |
| `sequence_no` | INTEGER | no | 1..maxToolCallsPerTurn |
| `tool_name` | TEXT | no | Registry allowlisted name |
| `tool_version` | TEXT | no | Tool schema/policy version |
| `validated_arguments` | JSONB | no | Server-validated safe args; no SQL/scope authority |
| `status` | TEXT | no | Tool execution state |
| `source_watermark` | TEXT | yes | Reporting source checkpoint |
| `freshness_status` | TEXT | yes | Quality status returned by Reporting |
| `calculation_version` | TEXT | yes | Reporting metric version |
| `result_summary` | JSONB | yes | Redacted bounded aggregate summary, not raw rows |
| `error_code` | TEXT | yes | Safe typed error |
| `latency_ms` | BIGINT | yes | Execution duration |
| `created_at` | TIMESTAMPTZ | no | Execution UTC |

Unique `(tenant_id, turn_id, sequence_no)`; `validated_arguments` must pass schema/policy before call.

### 10.5 `ai_daily_summary_jobs`

| Column | Type | Null | Description/remark |
|---|---|---:|---|
| `id` | UUID | no | UUID v7 summary job ID |
| `tenant_id` | UUID | no | Tenant owner |
| `branch_id` | UUID | no | Branch scope |
| `business_date` | DATE | no | Branch local business date |
| `branch_timezone` | TEXT | no | IANA timezone snapshot |
| `summary_policy_version` | TEXT | no | Baseline/cutoff/materiality policy |
| `status` | TEXT | no | Scheduled/waiting/ready/data-late/terminal state |
| `source_watermark_hint` | TEXT | yes | Scheduler hint, not authority |
| `source_watermark_used` | TEXT | yes | Watermark used for output |
| `attempt_count` | INTEGER | no | Bounded job attempts |
| `next_attempt_at` | TIMESTAMPTZ | yes | Retry schedule |
| `lease_owner` | TEXT | yes | Worker lease identity, non-secret |
| `lease_expires_at` | TIMESTAMPTZ | yes | Worker lease expiry |
| `last_error_code` | TEXT | yes | Safe terminal/retry error |
| `created_at` | TIMESTAMPTZ | no | Job creation UTC |
| `updated_at` | TIMESTAMPTZ | no | Last job transition |

Unique business key: `(tenant_id, branch_id, business_date, summary_policy_version)`.

### 10.6 `ai_daily_summaries`

| Column | Type | Null | Description/remark |
|---|---|---:|---|
| `id` | UUID | no | UUID v7 summary revision ID |
| `tenant_id` | UUID | no | Tenant owner |
| `branch_id` | UUID | no | Branch scope |
| `job_id` | UUID | no | Source job |
| `business_date` | DATE | no | Branch local date |
| `summary_policy_version` | TEXT | no | Baseline/cutoff/materiality policy key used by the job |
| `revision` | BIGINT | no | Monotonic summary revision |
| `status` | TEXT | no | `PUBLISHED`, `NO_BASELINE`, `DATA_LATE`, `PROVIDER_UNAVAILABLE`, `FAILED`, `SUPERSEDED` |
| `baseline_type` | TEXT | yes | Same weekday/7-day/none |
| `baseline_period` | JSONB | yes | Exact comparison dates |
| `safe_content` | JSONB | no | Validated redacted headline/facts/drivers/caveats |
| `source_watermark` | TEXT | no | Reporting watermark |
| `source_watermark_version` | BIGINT | no | Monotonic Reporting checkpoint used for supersede ordering |
| `calculation_version` | TEXT | no | Metric/contribution version |
| `policy_version` | TEXT | no | Summary/prompt/redaction policy |
| `model_version` | TEXT | yes | Provider model reference |
| `supersedes_summary_id` | UUID | yes | Previous immutable revision |
| `superseded_by_summary_id` | UUID | yes | Newer revision if superseded |
| `response_hash` | TEXT | no | Hash of safe content |
| `generated_at` | TIMESTAMPTZ | no | Generation UTC |
| `published_at` | TIMESTAMPTZ | yes | Publication UTC |
| `created_at` | TIMESTAMPTZ | no | Persistence UTC |

No raw prompt/provider output is stored in `safe_content`.

### 10.7 `ai_summary_sources`

| Column | Type | Null | Description/remark |
|---|---|---:|---|
| `id` | UUID | no | Source reference ID |
| `tenant_id` | UUID | no | Tenant owner |
| `summary_id` | UUID | no | Summary revision |
| `tool_name` | TEXT | no | Reporting tool source |
| `watermark` | TEXT | no | Source checkpoint |
| `watermark_version` | BIGINT | no | Monotonic source checkpoint for lineage |
| `projection_version` | BIGINT | no | Source projection version |
| `last_updated_at` | TIMESTAMPTZ | no | Source freshness |
| `freshness_status` | TEXT | no | Quality status |
| `calculation_version` | TEXT | no | Metric version |
| `created_at` | TIMESTAMPTZ | no | Persistence UTC |

### 10.8 `ai_provider_policies`

| Column | Type | Null | Description/remark |
|---|---|---:|---|
| `id` | UUID | no | Policy row ID |
| `tenant_id` | UUID | no | Tenant owner; platform default may be referenced |
| `policy_version` | TEXT | no | Immutable policy version |
| `provider_name` | TEXT | no | `DEEPSEEK` MVP |
| `model_reference` | TEXT | no | Non-secret model/config reference |
| `region_policy` | TEXT | no | Allowed egress/region policy |
| `prompt_policy_version` | TEXT | no | System/redaction policy |
| `max_input_tokens` | INTEGER | no | Hard input cap |
| `max_output_tokens` | INTEGER | no | Hard output cap |
| `request_timeout_ms` | INTEGER | no | Provider timeout |
| `max_retries` | INTEGER | no | Bounded provider retry |
| `daily_budget_minor` | BIGINT | no | Provider cost budget in configured currency |
| `budget_currency` | TEXT | no | Currency for provider cost budget; not a POS revenue fact |
| `secret_reference` | TEXT | no | Secrets Manager/KMS reference, not secret |
| `enabled` | BOOLEAN | no | Feature flag |
| `created_by_user_id` | UUID | yes | Policy actor; null for platform seed |
| `created_at` | TIMESTAMPTZ | no | Creation UTC |
| `updated_at` | TIMESTAMPTZ | no | Update UTC |

### 10.9 `ai_provider_usage`

| Column | Type | Null | Description/remark |
|---|---|---:|---|
| `id` | UUID | no | UUID v7 usage/reservation ID |
| `tenant_id` | UUID | no | Tenant owner |
| `policy_version` | TEXT | no | Effective immutable provider policy |
| `business_date` | DATE | no | Budget accounting date in tenant policy timezone |
| `attempt_id` | UUID | no | One bounded provider attempt |
| `provider_request_id` | UUID | no | Provider request correlation ID |
| `reservation_status` | TEXT | no | `RESERVED`, `FINALIZED`, `RELEASED`, `BUDGET_EXCEEDED`, `POLICY_DISABLED` |
| `estimated_cost_minor` | BIGINT | no | Atomic reservation amount |
| `actual_cost_minor` | BIGINT | yes | Final provider cost if known |
| `budget_currency` | TEXT | no | Provider cost currency |
| `input_tokens` | INTEGER | yes | Final input token count |
| `output_tokens` | INTEGER | yes | Final output token count |
| `retry_no` | INTEGER | no | Zero-based bounded retry number |
| `outcome` | TEXT | yes | `SUCCESS`, `TIMEOUT`, `INVALID_OUTPUT`, `CIRCUIT_OPEN`, `ERROR` |
| `created_at` | TIMESTAMPTZ | no | Reservation UTC |
| `finalized_at` | TIMESTAMPTZ | yes | Finalization/release UTC |

Unique `(tenant_id, attempt_id)` and unique `(tenant_id, provider_request_id)` prevent double charging or duplicate finalization.

### 10.10 `ai_audit_references`

| Column | Type | Null | Description/remark |
|---|---|---:|---|
| `id` | UUID | no | Audit reference ID |
| `tenant_id` | UUID | no | Tenant owner |
| `branch_id` | UUID | yes | Branch scope if applicable |
| `actor_subject_hash` | TEXT | yes | Keyed/opaque actor reference |
| `role` | TEXT | yes | Actor role snapshot |
| `action` | TEXT | no | Turn/tool/refusal/summary action |
| `resource_type` | TEXT | no | Conversation/turn/tool/summary/policy |
| `resource_id` | UUID | no | Related resource |
| `scope_summary` | JSONB | no | Safe scope type/count/version, no foreign branch list |
| `policy_decision` | TEXT | no | Allow/deny/refuse reason |
| `tool_summary` | JSONB | yes | Tool names/status/latency only |
| `source_summary` | JSONB | yes | Watermark/freshness/calculation version |
| `response_hash` | TEXT | yes | Safe output hash |
| `correlation_id` | UUID | no | Trace reference |
| `created_at` | TIMESTAMPTZ | no | Immutable audit time |

### 10.11 Reliability tables

`outbox_messages`, `inbox_events` and `idempotency_records` follow parent conventions:

| Table | Required fields |
|---|---|
| `outbox_messages` | event ID, tenant, aggregate type/id/version, event type/version, immutable safe payload, published time, attempts, next attempt |
| `inbox_events` | source event ID, tenant, source/version, status, processed time, safe error |
| `idempotency_records` | tenant, operation, key, canonical request hash, response status/body or resource ID, expiry |

No reliability payload may contain raw prompt, provider response, phone, JWT, secret, SQL or raw transaction data.

## 11. Constraints, indexes, retention and migration

### 11.1 Required constraints/indexes

- Every table has `tenant_id`; branch-scoped tables require `branch_id`.
- `ai_conversations` and `ai_turns` must verify actor/scope ownership on every public read.
- Unique turn key: `(tenant_id, conversation_id, client_turn_id)`.
- Unique summary job key: `(tenant_id, branch_id, business_date, summary_policy_version)`.
- One current summary revision per `(tenant_id, branch_id, business_date, summary_policy_version)`; superseded revisions immutable.
- `ai_tool_executions.sequence_no` unique per turn and bounded by policy.
- Provider policies immutable by `policy_version`; activation uses optimistic version/feature flag.
- Scope revision branch IDs must belong to the same tenant; `TENANT` scope only Owner.
- `safe_content`, semantic context and result summaries pass JSON schema validation before commit.
- No column named `phone`, raw prompt, raw response, JWT, API key, SQL or provider secret is allowed in AI schema.
- Source watermark/version monotonic for summary revision lineage; older watermark cannot supersede newer revision.
- `ai_provider_usage` has one reservation/finalization per `(tenant_id, attempt_id)` and one provider request per `(tenant_id, provider_request_id)`.

The summary lineage is protected by the following migration-level rules (names are normative; exact SQL dialect may use equivalent PostgreSQL syntax):

```sql
ALTER TABLE ai_daily_summaries
  ADD CONSTRAINT ck_ai_summary_revision_positive CHECK (revision > 0),
  ADD CONSTRAINT ck_ai_summary_watermark_version_positive CHECK (source_watermark_version >= 0),
  ADD CONSTRAINT ck_ai_summary_supersede_links CHECK (
    (status = 'SUPERSEDED' AND superseded_by_summary_id IS NOT NULL)
    OR (status <> 'SUPERSEDED' AND superseded_by_summary_id IS NULL)
  );

CREATE UNIQUE INDEX uq_ai_summary_revision
  ON ai_daily_summaries (tenant_id, branch_id, business_date, summary_policy_version, revision);

CREATE UNIQUE INDEX uq_ai_summary_current
  ON ai_daily_summaries (tenant_id, branch_id, business_date, summary_policy_version)
  WHERE status <> 'SUPERSEDED';
```

For a late-fact revision, the worker locks the lineage key, verifies `new.source_watermark_version > current.source_watermark_version`, marks the current row `SUPERSEDED`, inserts the new immutable row, and writes `AIDailySummarySuperseded.v1` in the same transaction. A uniqueness or watermark conflict rolls back all changes. `expectedRevision` and idempotency key are checked before the lock; replay returns the original job/revision.

### 11.2 Retention and partition

- Raw prompt and raw provider response retention: zero by default.
- Structured semantic context and safe turn metadata: default 90 days, configurable per tenant policy with deletion/tombstone support.
- Summary safe content: retain according to Reporting/dashboard business policy; revisions remain for configured correction/audit window.
- Audit references follow parent audit/legal retention, with only redacted evidence.
- Monthly RANGE partition candidates: `ai_turns`, `ai_tool_executions`, `ai_audit_references` only after measured volume/retention need; no MVP partition by default.
- Deletion/tombstone removes future provider/training use and invalidates session scope; it must not delete legally required redacted audit evidence outside policy.

### 11.3 Retention matrix

| Data class/table | Default retention | Deletion/tombstone | Legal hold | Training/provider use |
|---|---|---|---|---|
| Raw question, raw prompt, raw provider response | 0 days | Never persist; transport buffers are zeroized after request | No raw hold allowed by this service | Prohibited |
| `ai_turns.semantic_context`, `answer_payload`, `ai_tool_executions.result_summary` | 90 days | Delete or cryptographically tombstone; invalidate conversation scope | Preserve only redacted fields explicitly placed under hold | `not_for_training=true`; never send historical content to provider |
| `ai_daily_summaries`, `ai_summary_sources` | Dashboard/reporting policy, default 24 months | Tombstone only after policy expiry; preserve revision lineage while retained | Hold overrides deletion for safe content and source metadata | Aggregate safe facts only; no model training dataset by default |
| `ai_audit_references`, event metadata | Parent Audit/legal retention | Audit service owns deletion/hold decision | Parent Audit policy is authoritative | Prohibited from training |
| `ai_provider_usage` | 24 months or finance policy | Delete after reconciliation window; keep aggregate budget evidence under hold | Finance/legal hold applies | Prohibited |

Deletion jobs are idempotent, emit only redacted deletion metadata, and cannot re-enable provider/training use. A legal hold is explicit per resource/data class; it does not authorize retaining raw prompts because raw prompt retention is structurally prohibited.

### 11.4 Migration order

1. Create common reliability/audit tables and tenant constraints.
2. Create provider policy and registry seed with secret references only.
3. Create conversation/scope revision/turn tables and idempotency uniqueness.
4. Create tool execution table and bounded indexes.
5. Create summary job/summary/source tables and revision constraints.
6. Create provider usage/budget indexes and reservation constraints.
7. Create outbox/inbox indexes and worker lease constraints.
8. Seed tool registry/policy/calculation version references; no user content seed.
9. Backfill none by default; rebuild Reporting facts through Reporting-owned process, not AI DB inference.

## 12. Contract test matrix

| Area | Required fixtures |
|---|---|
| Auth/scope | Owner tenant scope, Owner branch scope, Manager assigned branch, foreign branch, revoked membership, stale membership version |
| Chat API | Conversation create, turn 200, turn 202/poll, expired session, idempotent replay, cursor pagination |
| Tool registry | Unknown tool, disabled version, invalid JSON, SQL/table argument, date/range/limit boundary, max loop |
| Reporting | Canonical `OrderPaid` revenue, duplicate event, payment-late settlement, full/partial refund, correction, AOV denominator/rounding, hourly timezone/DST, top item ties, branch comparison, empty data, stale/incomplete watermark, registry digest mismatch |
| Prompt safety | Ignore-policy, reveal-system-prompt, scope escalation, mutation request, PII injection, unsafe output, numeric claim mismatch, phone/UUID/SQL redaction |
| Provider | Timeout, one retry, circuit open, concurrent budget reservation, final-cost reconciliation, token/cost budget, provider request/attempt correlation, invalid function, invalid answer, mismatch repair, no fallback |
| Conversation | Follow-up structured context, branch change revision, no raw transcript persistence, TTL/deletion/tombstone |
| Daily summary | cutoff +15m, complete/incomplete, 30-minute wait, `DATA_LATE`, same-weekday baseline, 7-day fallback, no baseline |
| Summary revision | Late `OrderPaid`/refund watermark, monotonic watermark compare, concurrent worker lock, rerun idempotency, immutable supersede, duplicate publish prevention |
| Privacy | No phone/customer name/order/payment ID/secret in provider payload, logs, traces, DB and events |
| Events | Envelope/schema compatibility, `scopeSummary`/audit reference, outbox atomicity, consumer dedupe, unknown version quarantine, replay lineage |
| Performance | Tool p95 ≤2s, refusal ≤500ms, end-to-end chat target, summary lag, concurrent budget/rate limit |
| Security | BOLA/IDOR, cross-tenant, cross-branch, prompt injection, workload identity, revoked JWT/role change |

## 13. Parent contract amendments

### POS Platform

- Add AI Assistant endpoint/event table and `ai:report:read`, `ai:summary:read`, `ai:summary:rerun` permission references.
- Extend Reporting schema summary with `sales_by_hour`, `aov`, `contribution_drivers` and freshness/completeness envelope.
- Add AI event routes/queues and redaction policy reference; AI events are analytics/audit facts, not revenue facts.

### Identity Access

- Add `ai:report:read`, `ai:summary:read`, `ai:summary:rerun` to permission catalog.
- Amend `/internal/v1/authorization-decisions` to canonical request fields `subject`, `tenantId`, `requestedBranchIds`, `requiredPermission`, `operation`, `correlationId`; keep singular `branchId` only as a compatibility alias for one branch.
- Confirm the response envelope exposes `allowed`, `denialCode`, `resolvedScope`, `membershipVersion`, `authorizationVersion` and deny-without-enumeration behavior.
- Keep `AuthorizationContextChanged.v1` invalidation event additive; online decision remains authority.

### Reporting

- Define canonical tool APIs/JSON Schemas, registry digest handshake and metric dictionary for daily/branch/hourly sales, top items, AOV and contribution drivers.
- Define `OrderPaid.v1`/approved correction/refund treatment, business timezone, completeness watermark, freshness status, projection rebuild and late-fact revision trigger.
- Ensure Reporting repository applies tenant/branch predicates and never accepts arbitrary SQL from AI Assistant.

### Order/Payment/Shift

- Preserve `OrderPaid.v1` as revenue source; exclude Payment late settlement from revenue projections.
- Provide branch business date/timezone and close/cutoff policy needed by Reporting summary readiness.
- Define correction/refund events when enabled; historical summary revisions must use Reporting policy.

### Audit/Observability

- Add AsyncAPI/JSON Schema consumer definitions for `AIReportTurnCompleted.v1`, `AIAccessRefused.v1`, `AIDailySummaryPublished.v1`, `AIDailySummarySuperseded.v1`, including `scopeSummary`, `aggregateVersion` and `auditReferenceId`.
- Store only redacted evidence; AI service never publishes raw prompt/provider payload.

## 14. Implementation artifacts required before code

- `openapi.yaml` for public conversation/turn/summary APIs.
- `openapi-internal.yaml` for Reporting tool, freshness, summary jobs and provider health contracts.
- `tool-registry.json`/JSON Schemas for all allowlisted tools and `DataQualityEnvelope`.
- `asyncapi.yaml` and JSON Schemas for consumed/produced AI events.
- `provider-policy.schema.json` plus redaction/prompt assembly fixture.
- PostgreSQL/Flyway migrations with `COMMENT ON COLUMN`, checks, indexes, leases and revision constraints.
- Golden Reporting metric fixtures for AOV, timezone, baseline, contribution and late facts.
- Security/evaluation corpus for prompt injection, scope escalation, PII leakage and hallucination/fact mismatch.

## 15. Contract approval gate

- **Current status:** Approved — warning remediation applied.
- **Resolved in this revision:** Identity field mapping, Reporting registry/digest and metric fixtures, safe-output schemas, provider budget reservation, async polling payload, event schema minimums, summary revision SQL rules, retention matrix and concurrency tests.
- **Required before finalization:** User confirms public/internal APIs, Reporting tool schemas, Identity amendments, provider policy, event payloads, redaction/retention and DB constraints.
- **Implementation prerequisite:** Không viết controller/DTO/worker/migration trực tiếp từ prose; generate machine-readable contracts and fixtures first.

