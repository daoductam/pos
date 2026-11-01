Started at: 2026/10/13 00:00:00
Finished at: 2026/10/13 00:00:00
Total time: 0 minutes
---

# Module 8 — Gợi ý món / Upsell: API & Data Contracts

**Ngày:** 2026-10-13  
**Trạng thái:** Approved with warnings  
**Vision:** [Recommendation Vision](./2026-10-13-vision.md)  
**Requirements:** [Recommendation Requirements](./2026-10-13-requirements.md)  
**Architecture:** [Recommendation Architecture](./2026-10-13-architecture.md)  
**Sequence diagrams:** [Recommendation Sequences](./2026-10-13-sequence-diagrams.adoc)

Tài liệu này là nguồn chuẩn để sinh OpenAPI 3.1, AsyncAPI/JSON Schema và PostgreSQL migrations cho Recommendation Service. Order, Catalog, Inventory và Loyalty amendments trong §10 là integration contracts bắt buộc trước implementation.

## 1. Contract conventions

### 1.1 API style

- Public REST + OpenAPI 3.1 dưới `/api/v1`.
- Internal REST + OpenAPI 3.1 dưới `/internal/v1`, workload identity/mTLS, không expose qua Angular.
- Events dùng parent envelope + AsyncAPI/JSON Schema; delivery at-least-once.
- IDs là UUID v7 strings; timestamp là ISO-8601 UTC.
- Money dùng `int64` minor units; P0 currency `VND`.
- `score`/ML metric dùng decimal string hoặc fixed-scale number trong schema; không dùng binary floating point cho money.
- Breaking change tạo major URI/event version; field optional mới phải backward-compatible.

### 1.2 Headers

| Header | Public | Internal | Meaning |
|---|---:|---:|---|
| `Authorization` | required | required | Cognito JWT hoặc workload credential/mTLS |
| `X-Active-Branch-Id` | branch request | signed context | Branch UI đang chọn; server re-check membership |
| `X-Correlation-Id` | recommended | required | Trace xuyên POS/Order/Recommendation/events |
| `X-Causation-Id` | no | recommended | Command/event gây ra operation |
| `Idempotency-Key` | mutation | mutation/job | Scope tenant + operation + actor/workload |
| `If-Match` | rule/model mutation | versioned command | Optimistic version |
| `Content-Type` | body | body | `application/json; charset=utf-8` |

Client không được tự đặt tenant ownership, role, customer phone hoặc branch membership trong body. `customerKey` chỉ hợp lệ nếu là opaque key do Loyalty cấp và Recommendation xác minh được scope/eligibility.

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
    "code": "projection_stale",
    "message": "Recommendation data is temporarily stale",
    "details": [
      {
        "field": "branchId",
        "code": "availability_watermark_old",
        "message": "Refresh the cart and retry"
      }
    ],
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

Error messages never expose SQL, JWT, phone, feature vectors, prompts, model artifacts, provider secrets or cross-tenant existence.

### 1.4 HTTP/error codes

| HTTP | Code | Meaning | Retry |
|---:|---|---|---:|
| 200 | — | Query/interaction accepted or idempotent replay | no |
| 201 | — | Draft/session/job created | no |
| 202 | `accepted` | Training/cold-start/rebuild queued | poll |
| 400 | `validation_error` | Shape/field invalid | no |
| 401 | `unauthorized` | Missing/invalid/expired auth | refresh auth |
| 403 | `forbidden` | Role/tenant/branch not allowed | no |
| 404 | `not_found` | Resource absent in authorized scope | no |
| 409 | `cart_context_unavailable` | Order context missing/version mismatch | refresh cart |
| 409 | `suggestion_expired` | Session TTL/cart version expired | request new suggestion |
| 409 | `candidate_not_in_session` | Item not bound to suggestion | no |
| 409 | `candidate_unavailable` | Candidate failed latest known eligibility | refresh/order validation |
| 409 | `projection_stale` | Required projection older than safe TTL | bounded retry/rebuild |
| 409 | `customer_feature_unavailable` | Missing/ineligible opaque feature context | association fallback |
| 409 | `model_unavailable` | Active model missing/unhealthy | association fallback |
| 409 | `rule_not_editable` | Published/retired version immutable | create new version |
| 409 | `rule_publish_forbidden` | Only Owner can publish/retire | no |
| 409 | `rule_version_conflict` | Stale If-Match/version | reread |
| 409 | `model_quality_gate_failed` | Candidate not activatable | retain active |
| 409 | `training_run_conflict` | Existing run lease/duplicate operation | use existing run |
| 409 | `idempotency_conflict` | Same key, different canonical hash | no |
| 409 | `duplicate_request` | Existing semantic result | use result |
| 422 | `tenant_scope_mismatch` | IDs/keys outside signed tenant | no |
| 422 | `interaction_duplicate` | Duplicate interaction source | semantic no-op |
| 429 | `provider_rate_limited` | DeepSeek/provider limit | bounded backoff |
| 503 | `provider_unavailable` | External provider unavailable | async retry |
| 503 | `service_unavailable` | Unsafe local dependency outage | bounded retry |

P0 recommendation không có candidate hợp lệ trả `200` với `items=[]`; đây không phải server error.

## 2. Authorization contract

| Permission | Owner | Manager | Cashier | Worker |
|---|---:|---:|---:|---:|
| `recommendation:read` | tenant/branch | assigned branch | assigned branch | — |
| `suggestion:interact` | yes | yes | assigned branch | — |
| `rule:draft` | yes | yes | no | — |
| `rule:publish` | yes | no | no | signed worker after Owner command |
| `rule:retire` | yes | no | no | signed worker after Owner command |
| `model:read-health` | tenant | assigned branch/tenant view | no | yes |
| `model:activate/rollback` | yes via audited command | no | no | signed worker |
| `training:run/replay` | restricted ops | no | no | scheduler/worker |
| `cold-start:review` | yes/reviewer | assigned review scope | no | worker creates only |
| `projection:rebuild` | restricted ops | no | no | signed worker |

Every repository query includes tenant predicate; branch predicate applies to branch/session/projection data. Owner must select a branch for branch-scoped suggestion request.

## 3. Public API surface

| Method | Path | Auth/scope | Request | Response | Status |
|---|---|---|---|---|---|
| POST | `/api/v1/recommendations` | recommendation:read | `RecommendationRequest` | `RecommendationResponse` | 200, 403, 409, 422 |
| POST | `/api/v1/recommendations/{suggestionId}/shown` | suggestion:interact | `SuggestionShownRequest` | `InteractionResponse` | 200, 403, 404, 409, 422 |
| POST | `/api/v1/recommendations/{suggestionId}/accept` | suggestion:interact | `SuggestionAcceptedRequest` | `InteractionResponse` | 200, 403, 404, 409, 422 |
| GET | `/api/v1/recommendation-rules` | rule:draft/read | cursor/filter | `RulePage` | 200, 403, 422 |
| POST | `/api/v1/recommendation-rules` | rule:draft | `CreateRuleRequest` | `RuleResponse` | 201, 403, 409, 422 |
| PATCH | `/api/v1/recommendation-rules/{ruleId}` | rule:draft | `UpdateRuleRequest` + If-Match | `RuleResponse` | 200, 403, 404, 409, 422 |
| POST | `/api/v1/recommendation-rules/{ruleId}/publish` | rule:publish | `PublishRuleRequest` | `RuleResponse` | 200, 403, 404, 409, 422 |
| POST | `/api/v1/recommendation-rules/{ruleId}/retire` | rule:retire | `RetireRuleRequest` | `RuleResponse` | 200, 403, 404, 409 |
| GET | `/api/v1/recommendation-models/active` | model:read-health | branch/tenant scope | `ActiveModelResponse` | 200, 403, 404 |
| GET | `/api/v1/cold-start-features` | cold-start:review | cursor/filter | `ColdStartFeaturePage` | 200, 403, 422 P2 |
| POST | `/api/v1/cold-start-features/{candidateId}/approve` | cold-start:review | `ReviewColdStartRequest` | `ColdStartFeatureResponse` | 200, 403, 404, 409 P2 |

Rule list uses cursor pagination, default `20`, maximum `100`, stable sort `(updatedAt DESC, ruleId DESC)`. Filters are allowlisted: `status`, `source`, `candidateItemId`, `updatedFrom`, `updatedTo`.

## 4. Recommendation API schemas

### 4.1 `RecommendationRequest`

```json
{
  "orderId": "0192b2d0-7f1d-7abc-8d2c-123456789001",
  "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789002",
  "cartVersion": 12,
  "customerKey": "custkey_opaque_01J...",
  "maxItems": 3,
  "clientReference": "cart-panel-refresh-4"
}
```

| Field | Type | Required | Rule |
|---|---|---:|---|
| `orderId` | UUID | yes | Order in same tenant/branch context |
| `branchId` | UUID | yes | Server validates authorized branch |
| `cartVersion` | int64 | yes | Must match Order context/projection |
| `customerKey` | opaque string/null | no | Loyalty-issued; no phone; tenant-bound |
| `maxItems` | int32 | no | Default 3, range 1–5 |
| `clientReference` | string/null | no | UI trace only; max 128, sanitized |

Recommendation calls the Order context contract on cache miss or version mismatch; public clients cannot provide arbitrary line snapshots as authority.

### 4.2 `RecommendationResponse`

```json
{
  "data": {
    "suggestionId": "0192b2d0-7f1d-7abc-8d2c-123456789010",
    "orderId": "0192b2d0-7f1d-7abc-8d2c-123456789001",
    "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789002",
    "cartVersion": 12,
    "strategy": "ASSOCIATION",
    "fallbackReason": null,
    "items": [
      {
        "itemId": "0192b2d0-7f1d-7abc-8d2c-123456789011",
        "variantId": null,
        "name": "Coca Cola",
        "priceMinor": 15000,
        "currency": "VND",
        "rank": 1,
        "score": "0.8400",
        "reasonCode": "FREQUENTLY_BOUGHT_TOGETHER",
        "ruleId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
        "ruleVersion": 4,
        "modelVersion": null,
        "availabilityObservedAt": "2026-10-13T08:00:00Z"
      }
    ],
    "availabilityStatus": "FRESH",
    "projectionWatermark": "inventory:branch:42:v901",
    "expiresAt": "2026-10-13T08:10:00Z",
    "generatedAt": "2026-10-13T08:00:01Z"
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

`strategy` là `ASSOCIATION`, `PERSONALIZED_ALS` hoặc `ASSOCIATION_FALLBACK`. Empty `items` là hợp lệ.

### 4.3 Shown interaction

`POST /api/v1/recommendations/{suggestionId}/shown`

```json
{
  "itemId": "0192b2d0-7f1d-7abc-8d2c-123456789011",
  "interactionId": "ui-interaction-01J...",
  "renderedAt": "2026-10-13T08:00:02Z",
  "displaySlot": "CART_UPSELL",
  "clientVersion": "pos-web-1.2.0"
}
```

The server derives tenant/branch/actor from auth and suggestion session. The command is valid only for a candidate in that session; duplicate `(suggestionId, itemId, interactionId, SHOWN)` returns the original result.

### 4.4 Accepted interaction

`POST /api/v1/recommendations/{suggestionId}/accept`

```json
{
  "itemId": "0192b2d0-7f1d-7abc-8d2c-123456789011",
  "interactionId": "ui-interaction-01J...-accept",
  "orderLineId": "0192b2d0-7f1d-7abc-8d2c-123456789013",
  "orderVersionAfterAdd": 13,
  "acceptedAt": "2026-10-13T08:00:03Z",
  "addLineCorrelationId": "0192b2d0-7f1d-7abc-8d2c-123456789014"
}
```

The POS must call Order add-line first and only call this endpoint after a successful response. Recommendation verifies session/order/item binding and stores evidence; it does not call Payment or mutate Order.

### 4.5 Interaction response

```json
{
  "data": {
    "interactionId": "ui-interaction-01J...",
    "suggestionId": "0192b2d0-7f1d-7abc-8d2c-123456789010",
    "itemId": "0192b2d0-7f1d-7abc-8d2c-123456789011",
    "eventType": "SHOWN",
    "status": "RECORDED",
    "eventId": "0192b2d0-7f1d-7abc-8d2c-123456789015",
    "recordedAt": "2026-10-13T08:00:02Z"
  },
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

`eventType` is `SHOWN` or `ACCEPTED`; duplicate retries return the original event ID.

## 5. Rule governance schemas

### 5.1 Create draft rule

`POST /api/v1/recommendation-rules`

```json
{
  "source": "MANUAL",
  "antecedents": [
    {
      "itemId": "0192b2d0-7f1d-7abc-8d2c-123456789020",
      "variantId": null,
      "modifierOptionId": null
    }
  ],
  "consequent": {
    "itemId": "0192b2d0-7f1d-7abc-8d2c-123456789021",
    "variantId": null
  },
  "priority": 100,
  "effectiveFrom": "2026-10-13T00:00:00Z",
  "effectiveTo": null,
  "note": "Gà rán đi cùng Coca"
}
```

Server validates canonical identity, no self-suggestion, max antecedent size, item tenant ownership, effective range and priority bounds. Support/confidence/lift are computed or imported by a trusted ML job; public Manager input cannot forge quality metrics.

### 5.2 Rule lifecycle

| State | Allowed transition | Actor |
|---|---|---|
| `DRAFT` | update, submit/publish | Manager/Owner update; Owner publish |
| `PUBLISHED` | retire, supersede by new version | Owner |
| `RETIRED` | terminal/read-only | none |

`PublishRuleRequest` requires `reason`, `expectedVersion`, and optional `qualityEvidenceRef`. `RetireRuleRequest` requires `reason` and `expectedVersion`. Published versions are immutable; edits create a new draft/version.

### 5.3 `RuleResponse`

Fields: `ruleId`, `tenantId`, `logicalRuleKey`, `version`, `status`, `source`, `antecedents[]`, `consequent`, `supportBps`, `confidenceBps`, `liftMilli`, `priority`, `effectiveFrom`, `effectiveTo`, `createdBy`, `publishedBy`, `publishedAt`, `version`, `createdAt`, `updatedAt`.

Quality fields are server/trainer-owned and nullable for a draft until evaluated. `liftMilli=1000` represents lift `1.000`.

## 6. Model/training/internal contracts

### 6.1 Internal endpoint catalogue

| Method | Path | Caller | Request | Response | Status |
|---|---|---|---|---|---|
| POST | `/internal/v1/recommendations/training-runs` | Scheduler/ops | `CreateTrainingRunRequest` | `TrainingRunResponse` | 201/202, 409 |
| GET | `/internal/v1/recommendations/training-runs/{runId}` | Worker/ops | — | `TrainingRunResponse` | 200, 404 |
| POST | `/internal/v1/recommendations/models/{modelId}/validate` | ML worker | `ValidateModelRequest` | `ModelResponse` | 200, 409, 422 |
| POST | `/internal/v1/recommendations/models/{modelId}/activate` | Owner workflow/worker | `ActivateModelRequest` | `ModelResponse` | 200, 403, 409 |
| POST | `/internal/v1/recommendations/models/{modelId}/rollback` | Owner/ops workflow | `RollbackModelRequest` | `ModelResponse` | 200, 403, 409 |
| POST | `/internal/v1/recommendations/projections/rebuild` | Ops worker | `RebuildProjectionRequest` | `RebuildOperationResponse` | 202, 409 |
| POST | `/internal/v1/recommendations/cold-start-jobs` | Scheduler/worker | `CreateColdStartJobRequest` | `ColdStartJobResponse` | 201/202, 409 |

### 6.2 Training request

```json
{
  "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789030",
  "modelFamily": "ALS",
  "sourceWatermark": "order-paid:2026-10-12T23:59:59Z:v901",
  "trainingWindowFrom": "2026-07-01T00:00:00Z",
  "trainingWindowTo": "2026-10-12T23:59:59Z",
  "featureVersion": "loyalty-feature-v3",
  "codeVersion": "recommender-2026.10.13",
  "requestedBy": "scheduler",
  "correlationId": "0192b2d0-7f1d-7abc-8d2c-123456789031"
}
```

One leased run per `(tenantId, modelFamily, sourceWatermark)`; duplicate request returns existing run.

### 6.3 Validation/activation

`ValidateModelRequest` includes candidate artifact URI/checksum, snapshot ID, data row count, tenant count, feature/code version, metrics (`recallAtK`, `ndcgAtK`, `coverage`), hard-gate results and validation report reference.

`ActivateModelRequest` includes `expectedRegistryVersion`, `reason`, `qualityPolicyVersion`, `artifactChecksum`. Server requires all hard gates pass and performs compare-and-swap active pointer.

`RollbackModelRequest` includes `targetModelVersion`, `expectedRegistryVersion`, `reason`, `incidentReference`.

### 6.4 Model statuses

`CANDIDATE`, `VALIDATING`, `ACTIVE`, `RETIRED`, `REJECTED`, `FAILED`.

Only one `ACTIVE` model exists per tenant/model family. Runtime load failure activates association fallback, not a new model.

## 7. Cross-service contracts

### 7.1 Order context read contract

Order must expose a workload-only contract consumed by Recommendation:

`GET /internal/v1/orders/{orderId}/recommendation-context?branchId={branchId}&cartVersion={cartVersion}`

Response:

```json
{
  "data": {
    "orderId": "0192b2d0-7f1d-7abc-8d2c-123456789001",
    "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789030",
    "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789002",
    "cartVersion": 12,
    "status": "DRAFT",
    "lines": [
      {
        "lineId": "0192b2d0-7f1d-7abc-8d2c-123456789040",
        "itemId": "0192b2d0-7f1d-7abc-8d2c-123456789020",
        "variantId": null,
        "modifierOptionIds": [],
        "quantity": 1
      }
    ],
    "resolvedAt": "2026-10-13T08:00:00Z"
  }
}
```

Order returns `409 cart_context_unavailable` for version mismatch and never exposes another tenant/branch order. Recommendation does not cache this response beyond the bounded request/session policy.

Order add-line request/response must preserve optional suggestion attribution:

```json
{
  "suggestionAttribution": {
    "suggestionId": "0192b2d0-7f1d-7abc-8d2c-123456789010",
    "ruleId": "0192b2d0-7f1d-7abc-8d2c-123456789012",
    "modelVersion": null,
    "candidateItemId": "0192b2d0-7f1d-7abc-8d2c-123456789011"
  }
}
```

Order stores attribution only after successful add-line; client cannot use it to override item/price/availability.

### 7.2 Loyalty recommendation feature context

Loyalty must expose either an internal workload query or equivalent versioned event:

`POST /internal/v1/loyalty/recommendation-feature-context`

Request contains only tenant-scoped `customerId`/opaque reference, never phone. Response:

```json
{
  "data": {
    "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789030",
    "opaqueCustomerKey": "custkey_opaque_01J...",
    "eligibility": "ELIGIBLE",
    "consentVersion": "loyalty-consent-v2",
    "featureVersion": "loyalty-feature-v3",
    "generatedAt": "2026-10-13T07:59:00Z"
  }
}
```

`eligibility` is `ELIGIBLE`, `INELIGIBLE` or `DELETED`. Recommendation stores only the opaque key/eligibility/version; phone never crosses the boundary.

### 7.3 Catalog/Inventory normalized projections

Source events must include `tenantId`, branch scope if applicable, source entity ID, source version, `occurredAt`, status/available value, correlation and event ID.

- Catalog item/variant event supplies canonical identity, display name, price snapshot, active status and branch saleability.
- Inventory stock event supplies available status/quantity policy, source version and observed time; Recommendation does not perform stock arithmetic.
- Older event version is ignored; duplicate event ID is idempotent; gap triggers checkpoint/rebuild alert.

## 8. Event contracts

### 8.1 Common envelope

```json
{
  "eventId": "0192b2d0-7f1d-7abc-8d2c-123456789050",
  "eventType": "SuggestionAccepted.v1",
  "eventVersion": 1,
  "occurredAt": "2026-10-13T08:00:03Z",
  "tenantId": "0192b2d0-7f1d-7abc-8d2c-123456789030",
  "branchId": "0192b2d0-7f1d-7abc-8d2c-123456789002",
  "aggregateType": "SUGGESTION",
  "aggregateId": "0192b2d0-7f1d-7abc-8d2c-123456789010",
  "aggregateVersion": 2,
  "correlationId": "0192b2d0-7f1d-7abc-8d2c-123456789014",
  "causationId": "0192b2d0-7f1d-7abc-8d2c-123456789015",
  "producer": "recommendation-service",
  "payload": {}
}
```

### 8.2 Produced event catalogue

| Event | Payload minimum | Consumers |
|---|---|---|
| `SuggestionShown.v1` | suggestion/item/order/cart version, interaction ID, rule/model version, renderedAt | Reporting, Audit |
| `SuggestionAccepted.v1` | shown fields + orderLineId/orderVersionAfterAdd, acceptedAt, evidence correlation | Reporting, Audit |
| `RecommendationRulePublished.v1` | rule ID/version, tenant, actor, reason, effective range | Reporting, Audit, cache invalidation |
| `RecommendationRuleRetired.v1` | rule ID/version, actor, reason | Reporting, Audit, cache invalidation |
| `RecommendationModelActivated.v1` | tenant/model family/version, metrics, artifact checksum, policy version | Reporting, Audit, runtime refresh |
| `RecommendationModelRolledBack.v1` | old/new model version, actor, reason, incident ref | Reporting, Audit, runtime refresh |
| `RecommendationTrainingRunCompleted.v1` | run ID, state, source watermark, snapshot ID, metrics/error | Reporting, Audit, ops |
| `ColdStartFeatureCandidateCreated.v1` | candidate ID, item ID, provider/model/prompt version, confidence, review status | Owner review, Audit |

### 8.3 Event rules

- One event ID per committed local interaction/rule/model transition.
- Consumer dedupe by event ID and source/aggregate version.
- Replay preserves original event ID/occurredAt.
- No phone, raw customer feature vector, prompt, model artifact or provider secret appears in payload.
- `SuggestionAccepted` is analytics attribution, not payment/revenue truth; `OrderPaid.v1` remains canonical paid-order fact.

## 9. PostgreSQL data contract

Database: PostgreSQL, owned exclusively by Recommendation Service. Every new column requires `COMMENT ON COLUMN` matching the descriptions below.

### 9.1 `association_rule_sets`

| Column | Type | Null | Description/remark |
|---|---|---:|---|
| `id` | UUID | no | UUID v7 logical rule set ID |
| `tenant_id` | UUID | no | Tenant owner |
| `active_version` | BIGINT | yes | Published rule set version currently used |
| `status` | TEXT | no | `DRAFT`, `ACTIVE`, `RETIRED` |
| `created_by_user_id` | UUID | no | Draft creator |
| `published_by_user_id` | UUID | yes | Owner who published active set |
| `published_at` | TIMESTAMPTZ | yes | Active publish time |
| `version` | BIGINT | no | Optimistic aggregate version |
| `created_at` | TIMESTAMPTZ | no | Insert time |
| `updated_at` | TIMESTAMPTZ | no | Last metadata update |

### 9.2 `association_rule_versions`

| Column | Type | Null | Description/remark |
|---|---|---:|---|
| `id` | UUID | no | UUID v7 immutable rule version ID |
| `tenant_id` | UUID | no | Tenant owner |
| `logical_rule_key` | TEXT | no | Stable identity of a logical association |
| `rule_set_id` | UUID | no | Parent rule set |
| `version` | BIGINT | no | Monotonic logical rule version |
| `source` | TEXT | no | `MANUAL` or `ML` |
| `status` | TEXT | no | `DRAFT`, `PUBLISHED`, `RETIRED` |
| `support_bps` | INTEGER | yes | Support metric in basis points |
| `confidence_bps` | INTEGER | yes | Confidence metric in basis points |
| `lift_milli` | INTEGER | yes | Lift scaled by 1000; 1000 means 1.000 |
| `priority` | INTEGER | no | Tenant ranking priority |
| `effective_from` | TIMESTAMPTZ | no | Start of eligibility |
| `effective_to` | TIMESTAMPTZ | yes | End of eligibility |
| `quality_evidence_ref` | TEXT | yes | Snapshot/report reference for metrics |
| `created_by_user_id` | UUID | no | Draft creator or worker actor |
| `published_by_user_id` | UUID | yes | Owner publisher |
| `publish_reason` | TEXT | yes | Audited publish reason |
| `created_at` | TIMESTAMPTZ | no | Insert time |
| `updated_at` | TIMESTAMPTZ | no | Metadata update time |

### 9.3 `rule_antecedents` and `rule_consequents`

| Table/column | Type | Null | Description/remark |
|---|---|---:|---|
| `rule_antecedents.id` | UUID | no | Antecedent row ID |
| `rule_antecedents.rule_version_id` | UUID | no | Parent immutable rule version |
| `rule_antecedents.position` | INTEGER | no | Canonical sorted position |
| `rule_antecedents.item_id` | UUID | no | Catalog item reference |
| `rule_antecedents.variant_id` | UUID | yes | Optional variant reference |
| `rule_antecedents.modifier_option_id` | UUID | yes | Optional modifier reference |
| `rule_antecedents.identity_key` | TEXT | no | Canonical identity used in signature |
| `rule_consequents.id` | UUID | no | Consequent row ID |
| `rule_consequents.rule_version_id` | UUID | no | Parent immutable rule version |
| `rule_consequents.item_id` | UUID | no | Suggested Catalog item reference |
| `rule_consequents.variant_id` | UUID | yes | Suggested variant reference |
| `rule_consequents.identity_key` | TEXT | no | Canonical candidate identity |

Constraints: unique rule version + position/identity, no self-suggestion, all IDs tenant-scoped through parent version, canonical signature hash stored/indexed.

### 9.4 Projection tables

#### `catalog_item_projections`

| Column | Type | Null | Description/remark |
|---|---|---:|---|
| `id` | UUID | no | Projection row ID |
| `tenant_id` | UUID | no | Tenant owner |
| `item_id` | UUID | no | Catalog item identity |
| `variant_id` | UUID | yes | Catalog variant identity |
| `name_snapshot` | TEXT | no | Display name snapshot for suggestion |
| `price_minor` | BIGINT | no | Price snapshot in VND |
| `currency` | TEXT | no | Price currency |
| `item_status` | TEXT | no | Catalog status snapshot |
| `source_version` | BIGINT | no | Catalog event/source version |
| `source_occurred_at` | TIMESTAMPTZ | no | Catalog business event time |
| `observed_at` | TIMESTAMPTZ | no | Projection ingest time |
| `updated_at` | TIMESTAMPTZ | no | Last projection update |

#### `branch_item_eligibility`

| Column | Type | Null | Description/remark |
|---|---|---:|---|
| `id` | UUID | no | Eligibility row ID |
| `tenant_id` | UUID | no | Tenant owner |
| `branch_id` | UUID | no | Branch scope |
| `item_id` | UUID | no | Catalog item identity |
| `variant_id` | UUID | yes | Variant scope |
| `sellable` | BOOLEAN | no | Catalog branch saleability |
| `catalog_status` | TEXT | no | Active/inactive snapshot |
| `source_version` | BIGINT | no | Catalog availability version |
| `last_source_event_at` | TIMESTAMPTZ | no | Source event occurrence |
| `observed_at` | TIMESTAMPTZ | no | Projection ingest time |
| `updated_at` | TIMESTAMPTZ | no | Last update |

#### `inventory_availability_projections`

| Column | Type | Null | Description/remark |
|---|---|---:|---|
| `id` | UUID | no | Projection row ID |
| `tenant_id` | UUID | no | Tenant owner |
| `branch_id` | UUID | no | Branch scope |
| `item_id` | UUID | no | Catalog item identity |
| `variant_id` | UUID | yes | Variant scope |
| `available` | BOOLEAN | no | Inventory availability decision snapshot |
| `availability_policy` | TEXT | no | Source policy/version identifier |
| `source_version` | BIGINT | no | Inventory event version |
| `last_source_event_at` | TIMESTAMPTZ | no | Inventory occurrence time |
| `observed_at` | TIMESTAMPTZ | no | Projection ingest time |
| `updated_at` | TIMESTAMPTZ | no | Last update |

#### `projection_checkpoints`

| Column | Type | Null | Description/remark |
|---|---|---:|---|
| `id` | UUID | no | Checkpoint row ID |
| `tenant_id` | UUID | no | Tenant scope |
| `source_name` | TEXT | no | Catalog/Inventory/Order/Loyalty source |
| `partition_key` | TEXT | no | Source branch/partition key |
| `last_event_id` | UUID | yes | Last committed event ID |
| `last_source_version` | BIGINT | yes | Last applied source version |
| `last_event_at` | TIMESTAMPTZ | yes | Last source event time |
| `status` | TEXT | no | `HEALTHY`, `STALE`, `REBUILDING`, `FAILED` |
| `updated_at` | TIMESTAMPTZ | no | Checkpoint update time |

### 9.5 Suggestion/session tables

#### `suggestion_sessions`

| Column | Type | Null | Description/remark |
|---|---|---:|---|
| `id` | UUID | no | Suggestion session/impression ID |
| `tenant_id` | UUID | no | Tenant scope |
| `branch_id` | UUID | no | Branch scope |
| `order_id` | UUID | no | Order cart reference |
| `cart_version` | BIGINT | no | Order version bound to session |
| `customer_key_hash` | TEXT | yes | Non-reversible opaque customer lookup key |
| `strategy` | TEXT | no | `ASSOCIATION`, `PERSONALIZED_ALS`, `ASSOCIATION_FALLBACK` |
| `rule_set_version` | BIGINT | yes | Rule version used |
| `model_version` | TEXT | yes | ALS model version used |
| `projection_watermark` | TEXT | no | Eligibility source watermark |
| `availability_status` | TEXT | no | `FRESH`, `STALE`, `PARTIAL` |
| `generated_at` | TIMESTAMPTZ | no | Recommendation generation time |
| `expires_at` | TIMESTAMPTZ | no | Session TTL boundary |
| `created_at` | TIMESTAMPTZ | no | Insert time |

#### `suggestion_candidates`

| Column | Type | Null | Description/remark |
|---|---|---:|---|
| `id` | UUID | no | Candidate row ID |
| `tenant_id` | UUID | no | Tenant scope |
| `suggestion_id` | UUID | no | Parent suggestion session |
| `item_id` | UUID | no | Candidate item |
| `variant_id` | UUID | yes | Candidate variant |
| `rank` | INTEGER | no | Returned deterministic rank |
| `score` | NUMERIC(20,8) | no | Non-money rank score |
| `reason_code` | TEXT | no | UI-safe explanation code |
| `rule_id` | UUID | yes | Rule attribution |
| `rule_version` | BIGINT | yes | Rule version attribution |
| `model_version` | TEXT | yes | Model attribution |
| `name_snapshot` | TEXT | no | Display name snapshot |
| `price_minor` | BIGINT | no | Display price snapshot |
| `currency` | TEXT | no | Price currency |
| `availability_observed_at` | TIMESTAMPTZ | no | Freshness shown to response |
| `created_at` | TIMESTAMPTZ | no | Insert time |

#### `suggestion_interactions`

| Column | Type | Null | Description/remark |
|---|---|---:|---|
| `id` | UUID | no | Interaction/event ID |
| `tenant_id` | UUID | no | Tenant scope |
| `branch_id` | UUID | no | Branch scope |
| `suggestion_id` | UUID | no | Session reference |
| `item_id` | UUID | no | Candidate item |
| `event_type` | TEXT | no | `SHOWN` or `ACCEPTED` |
| `interaction_key` | TEXT | no | Client/source idempotency identity |
| `order_id` | UUID | no | Cart/order reference |
| `order_line_id` | UUID | yes | Successful add-line evidence for accepted |
| `order_version_after_add` | BIGINT | yes | Order version evidence |
| `rendered_at` | TIMESTAMPTZ | yes | UI render confirmation time |
| `accepted_at` | TIMESTAMPTZ | yes | Acceptance time |
| `evidence_correlation_id` | UUID | yes | Order add-line correlation |
| `correlation_id` | UUID | no | Trace ID |
| `created_at` | TIMESTAMPTZ | no | Persistence time |

Unique key: `(tenant_id, suggestion_id, item_id, event_type, interaction_key)`.

### 9.6 Customer/training/model tables

#### `customer_feature_contexts`

| Column | Type | Null | Description/remark |
|---|---|---:|---|
| `id` | UUID | no | Context row ID |
| `tenant_id` | UUID | no | Tenant scope |
| `opaque_customer_key_hash` | TEXT | no | Non-reversible customer key hash |
| `eligibility` | TEXT | no | `ELIGIBLE`, `INELIGIBLE`, `DELETED` |
| `consent_version` | TEXT | yes | Loyalty consent version |
| `feature_version` | TEXT | no | Loyalty feature schema/version |
| `source_version` | BIGINT | no | Loyalty context version |
| `generated_at` | TIMESTAMPTZ | no | Loyalty generation time |
| `observed_at` | TIMESTAMPTZ | no | Recommendation ingest time |
| `tombstoned_at` | TIMESTAMPTZ | yes | Privacy deletion/blocked time |
| `updated_at` | TIMESTAMPTZ | no | Last update |

#### `paid_interaction_projections`

| Column | Type | Null | Description/remark |
|---|---|---:|---|
| `id` | UUID | no | Projection row ID |
| `tenant_id` | UUID | no | Tenant scope |
| `event_id` | UUID | no | Source OrderPaid event ID |
| `order_id` | UUID | no | Paid order reference |
| `opaque_customer_key_hash` | TEXT | yes | Hashed eligible customer key |
| `item_id` | UUID | no | Paid item identity |
| `variant_id` | UUID | yes | Paid variant identity |
| `quantity` | INTEGER | no | Paid quantity |
| `occurred_at` | TIMESTAMPTZ | no | OrderPaid business time |
| `source_version` | BIGINT | yes | Order aggregate/event version |
| `feature_version` | TEXT | yes | Feature context version |
| `created_at` | TIMESTAMPTZ | no | Projection persistence time |

Unique source key: `(tenant_id, event_id, order_id, item_id, variant_id)` as appropriate for event line identity.

#### `training_runs`

| Column | Type | Null | Description/remark |
|---|---|---:|---|
| `id` | UUID | no | Training run ID |
| `tenant_id` | UUID | no | Tenant training scope |
| `model_family` | TEXT | no | `ALS` |
| `status` | TEXT | no | `QUEUED`, `RUNNING`, `VALIDATING`, `ACTIVATED`, `REJECTED`, `FAILED`, `CANCELLED` |
| `source_watermark` | TEXT | no | Paid interaction boundary |
| `snapshot_id` | UUID | yes | Immutable training snapshot |
| `feature_version` | TEXT | no | Input feature schema/version |
| `code_version` | TEXT | no | Trainer code version |
| `lease_owner` | TEXT | yes | Worker lease identity |
| `lease_expires_at` | TIMESTAMPTZ | yes | Lease timeout |
| `error_code` | TEXT | yes | Safe terminal error code |
| `started_at` | TIMESTAMPTZ | yes | Run start time |
| `completed_at` | TIMESTAMPTZ | yes | Run terminal time |
| `created_at` | TIMESTAMPTZ | no | Run creation time |
| `updated_at` | TIMESTAMPTZ | no | Last state update |

#### `training_snapshot_manifests`

| Column | Type | Null | Description/remark |
|---|---|---:|---|
| `id` | UUID | no | Snapshot manifest ID |
| `tenant_id` | UUID | no | Tenant scope |
| `training_run_id` | UUID | no | Owning run |
| `object_uri` | TEXT | no | Encrypted object storage location |
| `checksum` | TEXT | no | Content checksum |
| `row_count` | BIGINT | no | Input interaction rows |
| `source_watermark` | TEXT | no | Input boundary |
| `schema_version` | TEXT | no | Snapshot schema version |
| `retention_until` | TIMESTAMPTZ | no | Retention boundary |
| `created_at` | TIMESTAMPTZ | no | Snapshot creation time |

#### `model_registry`

| Column | Type | Null | Description/remark |
|---|---|---:|---|
| `id` | UUID | no | Model registry row ID |
| `tenant_id` | UUID | no | Tenant model scope |
| `model_family` | TEXT | no | `ALS` |
| `model_version` | TEXT | no | Immutable model version |
| `status` | TEXT | no | `CANDIDATE`, `VALIDATING`, `ACTIVE`, `RETIRED`, `REJECTED`, `FAILED` |
| `artifact_uri` | TEXT | no | Encrypted artifact location |
| `artifact_checksum` | TEXT | no | Artifact integrity checksum |
| `training_snapshot_id` | UUID | no | Source snapshot |
| `feature_version` | TEXT | no | Feature schema/version |
| `code_version` | TEXT | no | Trainer code version |
| `quality_policy_version` | TEXT | no | Validation policy used |
| `registry_version` | BIGINT | no | CAS pointer/version |
| `activated_at` | TIMESTAMPTZ | yes | Active time |
| `retired_at` | TIMESTAMPTZ | yes | Retired time |
| `created_at` | TIMESTAMPTZ | no | Insert time |
| `updated_at` | TIMESTAMPTZ | no | Last update |

#### `model_metrics`

| Column | Type | Null | Description/remark |
|---|---|---:|---|
| `id` | UUID | no | Metrics row ID |
| `tenant_id` | UUID | no | Tenant scope |
| `model_id` | UUID | no | Model registry reference |
| `metric_name` | TEXT | no | Metric identifier |
| `metric_value` | NUMERIC(20,8) | no | Fixed-scale evaluation value |
| `threshold_value` | NUMERIC(20,8) | no | Hard/soft threshold used |
| `gate_status` | TEXT | no | `PASS` or `FAIL` |
| `evaluation_window` | JSONB | no | Time/split definition without PII |
| `created_at` | TIMESTAMPTZ | no | Evaluation time |

### 9.7 P2 cold-start tables

#### `cold_start_jobs`

| Column | Type | Null | Description/remark |
|---|---|---:|---|
| `id` | UUID | no | Job ID |
| `tenant_id` | UUID | no | Tenant scope |
| `item_id` | UUID | no | Catalog item reference |
| `source_event_id` | UUID | no | Catalog trigger event |
| `status` | TEXT | no | `QUEUED`, `RUNNING`, `PENDING_REVIEW`, `COMPLETED`, `FAILED`, `DLQ` |
| `prompt_version` | TEXT | no | Prompt template/config version |
| `provider_model` | TEXT | no | DeepSeek model identifier |
| `attempt_count` | INTEGER | no | Bounded provider attempts |
| `last_error_code` | TEXT | yes | Safe provider/validation error |
| `lease_expires_at` | TIMESTAMPTZ | yes | Worker lease timeout |
| `created_at` | TIMESTAMPTZ | no | Job creation time |
| `updated_at` | TIMESTAMPTZ | no | Last update |

#### `cold_start_feature_candidates`

| Column | Type | Null | Description/remark |
|---|---|---:|---|
| `id` | UUID | no | Candidate feature ID |
| `tenant_id` | UUID | no | Tenant scope |
| `job_id` | UUID | no | Source cold-start job |
| `item_id` | UUID | no | Catalog item reference |
| `feature_payload` | JSONB | no | Sanitized structured feature result |
| `confidence` | NUMERIC(10,8) | no | Provider confidence after validation |
| `provider_model` | TEXT | no | Provider model version |
| `prompt_version` | TEXT | no | Prompt version |
| `provenance_ref` | TEXT | no | Sanitized evidence/reference |
| `review_status` | TEXT | no | `PENDING_REVIEW`, `APPROVED`, `REJECTED` |
| `reviewed_by_user_id` | UUID | yes | Human reviewer |
| `reviewed_at` | TIMESTAMPTZ | yes | Review time |
| `created_at` | TIMESTAMPTZ | no | Candidate creation time |

Provider raw response, prompt containing sensitive input and secrets are not stored.

### 9.8 Reliability tables

`outbox_messages`, `inbox_events`, `idempotency_records` và `audit_references` follow parent conventions with Recommendation-specific fields:

| Table | Required columns/descriptions |
|---|---|
| `outbox_messages` | `id` event ID; `tenant_id` owner; `aggregate_type/id` resource; `aggregate_version` ordering; `event_type` schema name; `payload` immutable envelope; `published_at` delivery time; `attempt_count` retry count; `next_attempt_at` backoff |
| `inbox_events` | `id` inbox row ID; `tenant_id` scope; `event_id` source dedupe key; `source_type` producer; `source_version` monotonic version; `status` applied/ignored/failed; `processed_at` commit time; `error_code` safe failure |
| `idempotency_records` | `id` row ID; `tenant_id` owner; `operation` command name; `idempotency_key` retry key; `request_hash` canonical hash; `response_status` saved HTTP status; `response_body` safe result; `resource_id` target; `expires_at` retention |
| `audit_references` | `id` audit reference; `tenant_id` owner; `branch_id` scope; `actor_user_id` human actor; `action` command; `resource_type/id` target; `reason` publish/rollback/review reason; `before_summary/after_summary` redacted state; `correlation_id` trace; `created_at` immutable time |

## 10. Constraints, indexes, retention and migration

### 10.1 Required constraints/indexes

- Every table has tenant ownership; branch-scoped tables have `(tenant_id, branch_id)` indexes.
- One active rule-set version per tenant; published rule versions immutable.
- Unique interaction source key as specified; duplicate event is no-op.
- One active ALS model per `(tenant_id, model_family)` using unique partial index.
- Training run lease unique for `(tenant_id, model_family, source_watermark)`.
- Projection checkpoint/source version monotonic; older event cannot overwrite newer state.
- Candidate price is snapshot `BIGINT`, never recomputed from live Catalog during analytics replay.
- Artifact URI/checksum required for candidate model activation; checksum mismatch blocks activation.
- Customer key is stored as keyed hash/opaque reference; no phone column exists.

### 10.2 Retention/partition

- Suggestion sessions/interactions, paid interaction projections, outbox/inbox and audit follow parent retention; default minimum audit evidence is 7 years where applicable.
- Training snapshots/model artifacts retain according to model reproducibility policy; active/previous validated models must remain rollback-capable for configured window.
- Monthly RANGE partition candidates: high-volume `suggestion_interactions`, `paid_interaction_projections`, `outbox_messages`, audit and training-run history.
- MVP does not partition small rule/model metadata tables.
- Privacy deletion/tombstone must exclude future training and remove/expire feature context per Loyalty policy; legal/audit records retain only allowed redacted evidence.

### 10.3 Migration order

1. Create reliability tables and common tenant/audit/outbox constraints.
2. Create Catalog/Inventory projection and checkpoint tables.
3. Create rule set/version/antecedent/consequent tables and active-version indexes.
4. Create suggestion session/candidate/interaction tables and unique interaction keys.
5. Create customer feature, paid interaction, training snapshot/run, model registry/metrics tables.
6. Create P2 cold-start tables and encrypted object-store integration metadata.
7. Seed default top-K/TTL/quality policies with versioned configuration.
8. Backfill projections from retained source events/snapshots; do not infer historical suggestion events.

## 11. Contract test matrix

| Area | Required fixtures |
|---|---|
| Recommendation API | exact cart version; empty cart; top-K; deterministic tie-break; no candidate |
| Eligibility | inactive, wrong branch, already-in-cart, unavailable, stale, duplicate/out-of-order source events |
| Shown/accepted | render-only shown; add-line success evidence; add-line failure no accept; duplicate/replay/expired session |
| Rule governance | Manager draft; Owner publish/retire; stale If-Match; immutable published version; self-suggestion rejection |
| Loyalty | opaque key; ineligible/deleted/consent version; tenant mismatch; no phone in logs/events |
| ALS | paid-only input; sparse customer fallback; tenant isolation; metrics gate; active pointer CAS; rollback |
| Training | duplicate run lease; snapshot checksum; worker crash/resume; DLQ/replay; previous model retained |
| Projections | checkpoint ordering; rebuild; gap/stale TTL; source event duplicate |
| DeepSeek | sanitized payload; schema/safety failure; timeout/rate-limit; human approval; no Catalog mutation |
| Security | cross-tenant order/rule/customer/model IDOR; role matrix; workload identity rejection |
| Performance | p95 recommendation; bounded candidate set; cache/model load; worker isolation from API |
| Events | envelope/schema compatibility; outbox atomicity; consumer dedupe; replay preserves event ID |

## 12. Parent contract amendments

### POS Platform

- Expand Recommendation endpoint table with `/shown`, rule lifecycle, model health and internal worker contracts.
- Add produced events `RecommendationRulePublished.v1`, `RecommendationRuleRetired.v1`, `RecommendationModelActivated.v1`, `RecommendationModelRolledBack.v1`, `RecommendationTrainingRunCompleted.v1` and `ColdStartFeatureCandidateCreated.v1`.
- Expand `association_rules`/`suggestion_events` summary to reference the detailed Recommendation schema; parent is not a second source of truth.

### Order

- Add internal `recommendation-context` read contract with order/cart version.
- Add optional `suggestionAttribution` to add-line command and persist only after successful line commit.
- Ensure Recommendation cannot use attribution to bypass Catalog/Inventory/Order validation.

### Catalog/Inventory

- Include source version, branch scope, observed/occurred time and event ID in availability/stock events.
- Define replay/checkpoint behavior for Recommendation projections.

### Loyalty

- Add opaque recommendation feature-context query/event with eligibility, consent version, feature version, tombstone semantics and tenant scope.
- Never expose phone plaintext or reversible lookup material to Recommendation.

## 13. Implementation artifacts required

Before code:

- `openapi.yaml` for public Recommendation and rule APIs.
- `openapi-internal.yaml` for Order context, training, model, rebuild and cold-start worker APIs.
- `asyncapi.yaml` and JSON Schemas for consumed/produced events.
- PostgreSQL/Flyway migrations with `COMMENT ON COLUMN` for every column.
- Contract fixtures for Order/Catalog/Inventory/Loyalty producers and Reporting/Audit consumers.
- ML evaluation policy fixture and model registry state transition fixture.

## 14. Contract approval gate

- **Current status:** Approved with warnings
- **Required before finalization:** User xác nhận API/event schemas, rule lifecycle, Order/Loyalty amendments, model lifecycle và DB constraints.
- **Implementation prerequisite:** Không viết controller/DTO/trainer/migration trực tiếp từ prose; sinh machine-readable contracts và test fixtures trước.

