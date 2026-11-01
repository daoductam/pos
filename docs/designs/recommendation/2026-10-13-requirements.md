Started at: 2026/10/13 00:00:00
Finished at: 2026/10/13 00:00:00
Total time: 0 minutes
---

# Module 8 — Gợi ý món / Upsell: Requirements

**Ngày:** 2026-10-13  
**Trạng thái:** Approved with warnings  
**Vision:** [Recommendation Vision](./2026-10-13-vision.md)  
**Vision review:** [Recommendation Vision Review](./2026-10-13-vision-review.md)  
**Phạm vi:** M — Standard Service Flow

## 1. Mục tiêu và boundary

Recommendation Service là source of truth cho association rules, ranking/model metadata, suggestion sessions và suggestion analytics ingestion. Service không sở hữu Catalog item/price, Inventory balance, Order state hoặc Loyalty customer profile.

### Priority

- **P0:** Realtime cart recommendation bằng association rules; lọc branch/cart/availability; ghi `SuggestionShown` và `SuggestionAccepted` đúng semantics.
- **P1:** ALS personalization theo customer opaque key; nightly train/validate/activate với rollback/fallback.
- **P2:** DeepSeek cold-start enrichment và contextual extension point cho giờ/thời tiết; không ảnh hưởng P0/P1 path.

## 2. Actors và integrations

| Actor/System | Quyền/vai trò |
|---|---|
| Cashier | Gọi recommendation cho cart được phép; xem/trigger shown; không sửa rules |
| Manager | Gọi recommendation; tạo/chỉnh association-rule draft trong tenant scope; xem rule/model health; không publish production rule |
| Owner | Tất cả quyền Manager; publish/retire rule và model policy trong tenant |
| POS/Angular | Gọi suggestion, render, gửi shown sau render; gửi attribution sau Order add thành công |
| Order Service | Cung cấp cart/order projection; authority cuối cho add-line; phát paid/order facts |
| Catalog Service | Item/variant/price/menu availability source và change events |
| Inventory Service | Stock/availability facts/events; Recommendation chỉ giữ projection |
| Loyalty Service | Resolve phone thành `customerId` opaque/feature key; policy consent/PII owner |
| Reporting/Audit | Consume suggestion/model/rule events; không mutate Recommendation state |
| Scheduler/ML Worker | Train/validate/activate model; rebuild projection; retry/DLQ |
| DeepSeek adapter | P2 async metadata/feature enrichment; không nằm trên POS request path |

## 3. Business rules

### 3.1 Tenant, branch và scope

1. Association rules thuộc tenant và không có branch-specific variant trong P0.
2. Mọi recommendation candidate phải được lọc theo branch hiện tại.
3. ALS train trên lịch sử toàn tenant; candidate cuối cùng vẫn lọc theo branch.
4. Mọi read/write có tenant predicate ở service và repository; `branchId` phải khớp authorized context.
5. Owner/Manager không được dùng `customerId`, `orderId` hoặc item ID để truy cập tenant khác.

### 3.2 Candidate eligibility

Một candidate hợp lệ phải đồng thời:

- tồn tại trong Catalog projection đúng tenant;
- item/variant đang active và bán được tại branch;
- không xuất hiện trong cart theo canonical item identity;
- không bị projection tồn kho đánh dấu unavailable;
- projection freshness không vượt TTL policy;
- có price/name snapshot để POS hiển thị;
- không vi phạm rule/model policy hoặc suppression.

Order kiểm tra availability và ownership lần cuối khi add-line. Recommendation stale không được làm checkout fail.

### 3.3 Association ranking

- Antecedent có thể là một item/variant/modifier hoặc basket signature nhiều phần tử.
- Rule tối thiểu có `support`, `confidence`, `lift`, `priority`, `status`, `version` và effective time.
- Rank mặc định: model/rule priority → confidence DESC → lift DESC → support DESC → stable candidate ID ASC.
- `topK` mặc định là 3, tối đa 5; policy có thể cấu hình theo tenant.
- Nếu không còn rule/candidate hợp lệ, trả `items=[]`, không fallback popular/default rule ở P0.
- Rule version dùng trong response/event để truy nguyên kết quả.

### 3.4 Suggestion session và cart version

- Mỗi response tạo hoặc reuse một `suggestionId`/session theo `(tenantId, branchId, orderId, cartVersion, requestKey)`.
- Suggestion hết hạn sau policy TTL, mặc định 10 phút, hoặc khi cart version thay đổi.
- `itemId`, `variantId`, `ruleId`, rank và score trong session là immutable.
- Accept chỉ được ghi cho candidate thuộc session, đúng order/branch/tenant và chưa accept trùng interaction.

### 3.5 Analytics semantics

- `SuggestionShown` chỉ được ghi sau khi POS render thành công và gửi command có `renderedAt`/client interaction ID.
- `SuggestionAccepted` chỉ được ghi sau khi Order add-line trả thành công; request phải chứa `orderLineId` hoặc `orderVersionAfterAdd` làm evidence reference.
- Analytics không được tự thêm line, mutate Order hoặc quyết định revenue.
- Duplicate source key trả semantic result cũ; out-of-order interaction không làm giảm trạng thái đã ghi.

## 4. Functional requirements P0

### FR-REC-001 — Get cart recommendations

Input tối thiểu: `orderId`, `branchId`, `cartVersion`, cart line identity/snapshot reference và optional opaque `customerId`/feature key.

System phải:

1. validate tenant/branch/role và order context;
2. lấy basket signature từ Order projection hoặc request snapshot đã được bind với order version;
3. resolve active tenant-wide association rules;
4. loại candidate đã có trong cart;
5. loại candidate inactive, sai branch, unavailable hoặc projection stale;
6. rank deterministic và giới hạn top-K;
7. persist suggestion session với rule/model/projection versions;
8. trả suggestion response trong p95 target mà không gọi DeepSeek/weather/provider realtime.

Nếu Order projection chưa có cart version tương ứng, trả `cart_context_unavailable` hoặc yêu cầu client refresh; không đoán từ order cũ.

### FR-REC-002 — Rule governance

- Manager/Owner có thể create/update draft rule trong tenant scope.
- Chỉ Owner được publish/retire production rule.
- Mọi publish/retire có reason, actor, version và audit event.
- Rule publish phải validate no self-suggestion, candidate identity, effective time, metric range và tenant ownership.
- Publish tạo immutable version; sửa rule đã publish tạo version mới, không patch lịch sử.

### FR-REC-003 — Availability/catalog projections

Recommendation phải consume versioned events từ Catalog/Inventory và cập nhật idempotently:

- item create/update/availability event;
- branch item availability event;
- stock/available quantity event;
- event version/freshness metadata.

Projection phải có watermark, last event time và source version. Nếu projection branch/item quá cũ hơn TTL, candidate bị loại và response nêu `availabilityStatus=STALE`/freshness metadata an toàn cho UI.

### FR-REC-004 — Suggestion shown

POS gọi shown command sau khi suggestion đã render. Recommendation phải:

- xác thực suggestion session và candidate identity;
- validate tenant/branch/order scope;
- dedupe theo `(suggestionId, itemId, interactionId, eventType)`;
- ghi immutable event/outbox;
- không block POS nếu analytics broker tạm thời unavailable; lưu local outbox để retry.

### FR-REC-005 — Suggestion accepted

POS chỉ gọi accept sau khi Order add-line thành công. Recommendation phải:

- xác thực item thuộc session và session chưa hết hạn theo acceptance policy;
- lưu `orderLineId`/`orderVersionAfterAdd` evidence reference;
- dedupe duplicate click/retry;
- ghi `SuggestionAccepted.v1` một lần cho interaction;
- không coi accept là payment/revenue fact; OrderPaid mới là canonical paid-order fact.

## 5. Functional requirements P1

### FR-REC-101 — Customer opaque feature context

- Loyalty là owner của phone lookup, customer identity, consent và PII.
- Recommendation không nhận/lưu/log phone plaintext.
- POS chỉ gửi `customerId` opaque sau Loyalty resolve hoặc gọi một internal feature-context contract đã được Loyalty authorize.
- Feature context phải có tenant, customer key, eligibility/consent status, feature version và generated-at.
- Cross-tenant customer key, revoked/blocked customer hoặc missing consent không được đưa vào ALS ranking/training.

### FR-REC-102 — ALS personalized ranking

Khi có active ALS model và customer đủ signal:

1. load customer latent/features theo active model version;
2. generate bounded candidate set toàn tenant;
3. lọc cart/branch/catalog/inventory như P0;
4. rank theo ALS score với deterministic tie-break;
5. trả reason `PERSONALIZED_ALS`, model version và confidence/quality metadata không nhạy cảm.

Nếu customer chưa đủ interaction, feature context unavailable, model inactive hoặc candidate set không hợp lệ, fallback association rules P0 và trả `fallbackReason` an toàn.

### FR-REC-103 — Nightly training

Training job phải:

- lấy input từ `OrderPaid.v1`/paid-order snapshot, không dùng draft/unpaid order;
- giới hạn dữ liệu theo tenant, không trộn user/item IDs giữa tenant;
- xử lý customer không có identity bằng aggregate association path, không tạo fake customer;
- tạo immutable training snapshot ID, feature version, code version và input watermark;
- train candidate model trong isolation;
- validate minimum interaction count, Recall@K/NDCG@K hoặc metric được cấu hình, coverage, freshness, leakage và evaluation split;
- chỉ activate nếu mọi hard gate đạt;
- giữ active model cũ nếu train/validation thất bại;
- retry bounded, ghi run state/DLQ/alert và cho phép replay cùng snapshot.

### FR-REC-104 — Model lifecycle

Model states tối thiểu: `CANDIDATE`, `VALIDATING`, `ACTIVE`, `RETIRED`, `REJECTED`, `FAILED`.

- Mỗi tenant có tối đa một model ALS `ACTIVE` cho mỗi model family/version policy.
- Activate là optimistic/versioned pointer update, không race với rollback.
- Rollback chỉ trỏ về model đã validate trước đó, có audit reason.
- Artifact reference, checksum, encryption key reference, trainer code version và metrics phải được lưu.

## 6. Functional requirements P2

### FR-REC-201 — DeepSeek cold-start enrichment

- Trigger khi Catalog tạo/cập nhật item metadata đủ điều kiện.
- Job gửi metadata đã sanitize, không gửi customer/order/payment/phone data.
- Structured output tối thiểu: tags/features, prompt/model version, confidence, provenance, generated-at.
- Validate schema, allowed vocabulary, length và safety policy trước khi lưu.
- Kết quả ở trạng thái `CANDIDATE` cần human approval trước khi trở thành feature production.
- Provider timeout/rate-limit/failure retry bounded; không làm hỏng Catalog hoặc P0/P1 recommendation.

### FR-REC-202 — Context extension point

- Feature interface hỗ trợ `businessHour`, `dayOfWeek`, branch timezone và future weather provider.
- Không có weather provider trong MVP.
- Context feature missing/stale không làm request fail; ranker bỏ qua feature hoặc dùng non-context model.

## 7. API requirements

### 7.1 Public API surface

- `POST /api/v1/recommendations` — get cart suggestions, idempotent per request key/session.
- `POST /api/v1/recommendations/{suggestionId}/shown` — render confirmation.
- `POST /api/v1/recommendations/{suggestionId}/accept` — accepted after Order add-line success.
- `GET /api/v1/recommendation-rules` — Manager/Owner list tenant rules with cursor/policy filters.
- `POST /api/v1/recommendation-rules` — Manager/Owner create draft.
- `PATCH /api/v1/recommendation-rules/{ruleId}` — Manager/Owner edit draft only with `If-Match`.
- `POST /api/v1/recommendation-rules/{ruleId}/publish` — Owner publish version.
- `POST /api/v1/recommendation-rules/{ruleId}/retire` — Owner retire version with reason.
- `GET /api/v1/recommendation-models/active` — Owner/Manager health/freshness view; no artifact download.

All side-effect commands use standard envelope, Cognito auth, `X-Correlation-Id`, `X-Active-Branch-Id` where branch-scoped, `Idempotency-Key` and safe error taxonomy. Rule list uses cursor pagination, default 20/max 100.

### 7.2 Internal API and worker surface

- `POST /internal/v1/recommendations/training-runs` — scheduler enqueue/replay run.
- `GET /internal/v1/recommendations/training-runs/{runId}` — worker/ops status.
- `POST /internal/v1/recommendations/models/{modelId}/validate` — worker validation result.
- `POST /internal/v1/recommendations/models/{modelId}/activate` — Owner-approved worker activation.
- `POST /internal/v1/recommendations/projections/rebuild` — authorized rebuild by source watermark.
- `POST /internal/v1/recommendations/cold-start-jobs` — async DeepSeek job enqueue.

Internal endpoints require workload identity/mTLS, signed tenant context, bounded retry and idempotent run key.

### 7.3 API behavior/error taxonomy

Minimum machine-readable codes:

`unauthorized`, `forbidden`, `not_found`, `validation_error`, `cart_context_unavailable`, `suggestion_expired`, `candidate_not_in_session`, `candidate_unavailable`, `projection_stale`, `customer_feature_unavailable`, `model_unavailable`, `model_quality_gate_failed`, `rule_not_editable`, `rule_publish_forbidden`, `rule_version_conflict`, `idempotency_conflict`, `duplicate_request`, `interaction_duplicate`, `tenant_scope_mismatch`, `training_run_conflict`, `provider_rate_limited`, `provider_unavailable`, `service_unavailable`.

Rules:

- P0 recommendation may return HTTP 200 with `items=[]` for no valid candidate; this is not an error.
- Analytics command failure after local persistence is retried asynchronously.
- Projection stale may return 200 with empty/filtered results plus freshness metadata, not a blind candidate list.
- Model unavailable/failing quality gate uses P0 association fallback where applicable.

## 8. Event and integration requirements

### 8.1 Consumed events

| Event | Producer | Use |
|---|---|---|
| `CatalogItemCreated/Updated.v1` | Catalog | Item projection/rule identity/cold-start trigger |
| `CatalogItemAvailabilityChanged.v1` | Catalog | Branch/menu eligibility |
| `InventoryStockChanged.v1` | Inventory | Available/unavailable projection |
| `OrderCreated.v1` | Order | Cart/order projection and P2 analytics context; not ALS training signal |
| `OrderPaid.v1` | Order | Canonical ALS interaction/training signal |
| Loyalty feature context event/API | Loyalty | Opaque customer feature eligibility/version |

Consumers must use event ID + source version dedupe, preserve source watermark, handle out-of-order delivery and support replay/rebuild.

### 8.2 Produced events

All events use parent envelope: `eventId`, `eventType`, `eventVersion`, `occurredAt`, `tenantId`, `branchId`, `aggregateType`, `aggregateId`, `aggregateVersion`, `correlationId`, `causationId`, `producer`, `payload`.

| Event | Trigger | Consumers |
|---|---|---|
| `SuggestionShown.v1` | Render confirmation accepted | Reporting, Audit |
| `SuggestionAccepted.v1` | Successful add-line attribution | Reporting, Audit |
| `RecommendationRulePublished.v1` | Owner publishes rule version | Reporting, Audit, Recommendation projection |
| `RecommendationRuleRetired.v1` | Owner retires rule version | Reporting, Audit |
| `RecommendationModelActivated.v1` | Candidate passes quality gate | Reporting, Audit, Recommendation API |
| `RecommendationModelRolledBack.v1` | Explicit rollback | Reporting, Audit |
| `RecommendationTrainingRunCompleted.v1` | Run terminal state | Reporting, Audit, ops |
| `ColdStartFeatureCandidateCreated.v1` | DeepSeek output validated as candidate | Catalog/ops/Audit |

### 8.3 Order/Reporting boundary

- `SuggestionAccepted` is attribution/analytics only; it cannot replace `OrderPaid.v1`.
- Order add-line request may carry optional `suggestionId`, `ruleId`, `modelVersion` and candidate item reference; Order stores this attribution in line/order event only after successful commit.
- Recommendation may use accepted command evidence or an Order line-added attribution event; duplicate paths must converge on one source key.
- Reporting computes shown → accepted → paid conversion with explicit attribution window/version, not by joining mutable current cart state.

## 9. Data and consistency requirements

Core entities:

- `association_rules`, `association_rule_versions`, `rule_antecedents`, `rule_candidates`;
- `item_availability_projection`, `item_catalog_projection`, `projection_checkpoints`;
- `suggestion_sessions`, `suggestion_candidates`, `suggestion_interactions`;
- `model_registry`, `training_runs`, `training_snapshots`, `model_metrics`;
- `customer_feature_keys`/opaque eligibility references, if Recommendation needs a local projection;
- `cold_start_feature_candidates`, `outbox_messages`, `idempotency_records`, `inbox_events`.

Required invariants:

1. Rule production version is immutable; only one active published version per logical rule/tenant scope.
2. Rule antecedent/candidate and all projection keys include tenant ownership.
3. Suggestion session binds tenant + branch + order + cart version; candidate cannot be accepted outside binding.
4. Interaction source key is unique; duplicate shown/accepted is idempotent.
5. Model activation uses compare-and-swap/version; no two active models for the same tenant/family.
6. Training snapshot includes tenant boundary, source watermark and code/feature version.
7. Projection consumer checkpoint updates only after event/application commit.
8. No plaintext phone, raw Loyalty lookup input or unnecessary PII in Recommendation data/log/event.
9. All cross-service IDs are opaque; no cross-service foreign key/query.

## 10. Non-functional requirements

| Category | Requirement | Target |
|---|---|---|
| Latency | P0 recommendation API excluding remote provider | p95 ≤ 500 ms under parent demo load |
| Availability | P0 API when ML/training/provider unavailable | Association rules continue; empty result is safe |
| Projection freshness | Catalog/Inventory availability state | target ≤ 60 s lag; stale TTL blocks candidate |
| Determinism | Same tenant/rules/cart/version/model | Same ordered candidates and scores within numeric tolerance |
| Training | Nightly ALS completion | Complete before configured business window; alert on missed run |
| Model safety | Candidate activation | All hard quality/privacy/isolation gates pass |
| Scale | Parent baseline | 100 branches, 100k orders/day; bounded candidate set/top-K |
| Security | Auth/tenant/branch/IDOR | Cognito + workload identity; service/repository defense-in-depth |
| Privacy | Customer data | No phone plaintext; opaque feature key; retention/deletion policy |
| Reliability | Events/jobs | Outbox/inbox, at-least-once, retry, DLQ, replay/rebuild |
| Observability | API/projection/model | Structured logs/metrics/traces; no payload/PII leakage |
| Retention | Suggestion/audit/training evidence | At least parent audit retention; configurable ML artifact/event retention |
| Cost | P2 DeepSeek | Async budget/rate limit; no unbounded retry or realtime calls |

## 11. Security and observability requirements

### Security

- Public API requires Cognito JWT and current tenant/branch authorization.
- Rule publish/model activation requires Owner permission and reason/audit.
- Internal event/job APIs require workload identity/mTLS and signed tenant context.
- Repository predicates always include tenant; branch predicates apply to branch-scoped projection/session.
- Customer feature key is opaque, non-reversible by Recommendation and not exposed to UI beyond allowed reference.
- DeepSeek payload is sanitized Catalog metadata only; provider response is untrusted input.

### Metrics/logs/traces

Metrics:

- `recommendation_request_total{result,source}`;
- `recommendation_latency_ms`, `candidate_filter_total{reason}`;
- `projection_lag_seconds`, `projection_stale_total{source}`;
- `suggestion_shown_total`, `suggestion_accepted_total`, conversion lag;
- `als_training_run_total{state}`, duration, data rows, quality metrics;
- `model_activation_total{result}`, rollback count, active model age;
- `cold_start_job_total{state}`, provider latency/error/cost estimate;
- outbox/inbox retry/DLQ and replay counters.

Structured logs must contain request/correlation, tenant/branch, order/suggestion/rule/model/run IDs and result code. Never log phone, raw customer feature, prompt containing PII, model artifact content, tokens or provider secret.

## 12. Edge cases and failure policy

- Cart empty: return `items=[]`, no rule evaluation error.
- Candidate appears multiple times in rules: merge by candidate with deterministic best-rule attribution.
- Candidate already in cart under another variant/modifier identity: Catalog canonical identity policy decides exclusion; no duplicate upsell.
- Availability event duplicate/out-of-order: ignore stale version, replay safe.
- Projection missing/stale: exclude affected candidate; if all candidates unsafe, return empty + freshness metadata.
- Order version changed after suggestion: accept command rejected/marked stale; POS must request new recommendation.
- Add-line fails after click: no `SuggestionAccepted`; shown remains valid impression.
- Duplicate shown/accepted: idempotent no-op/result replay.
- Customer has insufficient ALS history: association fallback with `COLD_START` reason.
- Active model missing/quality fail: association fallback; no 5xx for normal P1 cold start.
- Training source has duplicate/out-of-order `OrderPaid`: snapshot dedupe by event/source order key.
- Training run overlaps: one tenant/model-family run wins lease; duplicate run returns existing operation.
- Model artifact checksum mismatch: reject activation, retain active model, alert.
- DeepSeek timeout/unsafe schema: candidate rejected/retried; no Catalog mutation.
- Cross-tenant IDs/customer key: forbidden/tenant_scope_mismatch without existence leakage.
- Reporting/Audit unavailable: local outbox retains events; recommendation response is not blocked.

## 13. Acceptance criteria

### P0

1. Given a cart with a configured tenant-wide rule, eligible consequent items are returned in deterministic order.
2. Items already in cart, inactive, wrong branch or unavailable/stale are absent.
3. No valid rule returns HTTP 200 with empty items and no heuristic fallback.
4. Recommendation response includes `suggestionId`, cart/order version, rule version, candidate snapshot and freshness metadata.
5. Render confirmation creates one `SuggestionShown.v1` per interaction key.
6. Add-line failure creates no `SuggestionAccepted.v1`; successful add creates exactly one idempotent acceptance.
7. Manager can create/edit draft; only Owner can publish/retire production rules with reason/audit.

### P1

1. Customer phone is resolved through Loyalty; Recommendation receives no plaintext phone.
2. ALS uses paid-order data across tenant, filters candidates for current branch and falls back to P0 when signal/model is insufficient.
3. Nightly train failure or quality-gate failure preserves the previous active model.
4. Model activation and rollback are versioned, auditable and idempotent.
5. Duplicate/out-of-order OrderPaid, Catalog, Inventory and interaction events do not corrupt projections or metrics.

### P2

1. DeepSeek jobs are async, sanitized, schema-validated and human-reviewable.
2. DeepSeek/weather outage does not alter P0/P1 availability or latency.

## 14. Traceability

| Capability | Vision | Requirement coverage |
|---|---|---|
| Cart association | §4 P0 | FR-REC-001, ranking/filter rules, API |
| Availability/cart filtering | §4 P0, §7 | FR-REC-003, projection invariants, acceptance |
| Shown/accepted logs | §4 P0 | FR-REC-004/005, events, interaction dedupe |
| ALS personalization | §4 P1 | FR-REC-101/102, feature/privacy contract |
| Nightly training | §4 P1 | FR-REC-103/104, model lifecycle/quality gates |
| DeepSeek cold start | §4 P2 | FR-REC-201, async provider guardrails |
| Context extension | §4 P2 | FR-REC-202, no-provider MVP policy |
| Tenant/branch/privacy | §5–7 | Security/NFR/repository invariants |

## 15. Next phase inputs

- Architecture must choose projection transport/read model, rule engine component boundaries, model artifact storage/registry, trainer/activation worker and failure recovery.
- Contracts must define OpenAPI request/response/error schemas, shown/accepted evidence, rule lifecycle APIs, Loyalty opaque feature contract and event JSON Schemas.
- Database Design must define rule signatures, candidate identity, projection checkpoints, immutable interactions, training snapshots/model registry and indexes/retention.
- Sequence diagrams must cover cart request/filter, shown/accepted, ALS fallback, nightly train/activate/rollback and stale projection behavior.

## Approval gate

- **Current status:** Approved with warnings
- **Next action:** Spec Sentinel review, then user approval

