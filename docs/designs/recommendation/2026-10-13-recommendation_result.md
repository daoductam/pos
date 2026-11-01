Started at: 2026/10/13 00:00:00
Finished at: 2026/10/13 00:00:00
Total time: 0 minutes
---

# Module 8 — Recommendation/AI service: Design Result

**Ngày:** 2026-10-13  
**Trạng thái:** Design complete — Approved with warnings  
**Phạm vi:** M — Standard Service Flow  
**Module:** Gợi ý món / upsell (AI service, ML)

## 1. Verdict

**APPROVED WITH WARNINGS**

Thiết kế đủ cơ sở để tạo implementation backlog cho P0/P1/P2. Không có blocker mức Critical/RED về scope, ownership, tenant isolation hay kiến trúc hybrid. Trước khi viết code phải materialize các machine-readable contracts và hoàn tất các implementation gates ở §6, đặc biệt là evidence authoritative cho `SuggestionAccepted`.

## 2. Artifacts hoàn tất

| Phase | Artifact | Path | Status |
|---|---|---|---|
| Vision | Vision | [`2026-10-13-vision.md`](./2026-10-13-vision.md) | Approved |
| Vision review | Spec Sentinel Report | [`2026-10-13-vision-review.md`](./2026-10-13-vision-review.md) | Warnings accepted |
| Requirements | Requirements | [`2026-10-13-requirements.md`](./2026-10-13-requirements.md) | Approved with warnings |
| Requirements review | Spec Sentinel Report | [`2026-10-13-requirements-review.md`](./2026-10-13-requirements-review.md) | Warnings accepted |
| Architecture | Architecture | [`2026-10-13-architecture.md`](./2026-10-13-architecture.md) | Approved |
| Sequence diagrams | L1/L2 PlantUML | [`2026-10-13-sequence-diagrams.adoc`](./2026-10-13-sequence-diagrams.adoc) | Confirmed |
| Contracts | API & Data Contracts | [`2026-10-13-contracts.md`](./2026-10-13-contracts.md) | Approved with warnings |
| Contracts review | Spec Sentinel Report | [`2026-10-13-contracts-review.md`](./2026-10-13-contracts-review.md) | Warnings accepted |
| Finalization | Design Result | This document | Approved with warnings |

## 3. Design review summary

| Tiêu chí | Kết quả | Nhận xét |
|---|---|---|
| Completeness | PASS WITH WARNINGS | P0 association/shown/accepted, P1 ALS/nightly lifecycle và P2 DeepSeek/context extension đều có requirements, components, flows, schemas và test matrix. Một số response/event schema còn phải materialize thành OpenAPI/AsyncAPI. |
| Consistency | PASS WITH WARNINGS | Recommendation không sở hữu Catalog/Inventory/Order/Loyalty source of truth; Order add-line là authority cuối; `OrderPaid.v1` là canonical paid-order/training fact. Accepted evidence và personalization join với parent Order/Loyalty cần chốt executable contract. |
| Feasibility | PASS | Hybrid path giữ association rules và eligibility projection trên realtime path; ALS/DeepSeek chạy async; model failure fallback về association. |
| Security | PASS WITH WARNINGS | Cognito/workload identity, tenant/branch defense-in-depth, opaque Loyalty key, no-phone boundary và audit đã có. Cần bổ sung `rule:read` rõ ràng, authoritative accepted verification và negative IDOR fixtures. |
| Performance | PASS | Recommendation path hướng tới p95 ≤ 300 ms cho demo/≤ 500 ms service baseline; projection/model cache, bounded candidate set và no-LLM realtime dependency đã được thiết kế. |
| Observability | PASS WITH WARNINGS | Correlation/causation, outbox/inbox, DLQ, projection watermark, model metrics và suggestion interactions đã có. Cần reconciliation khi Order add-line thành công nhưng acceptance command bị mất. |
| Testing readiness | PASS WITH WARNINGS | Contract matrix bao phủ replay, stale projection, ALS gate, tenant isolation và provider failure; fixtures/migrations/schema executable chưa được tạo. |

## 4. Finalization review — Code Reviewer gate

**Scope:** 8 Recommendation artifacts và các parent contract trực tiếp liên quan; không có application code để review, nên đây là design-quality review theo checklist security, business logic, performance, maintainability và convention.

**Verdict:** **YELLOW — Proceed with Caution**

### Summary

Thiết kế coherent và implementation-ready ở mức backlog. Không phát hiện hardcoded secret, thiếu tenant boundary trong thiết kế, realtime dependency tới DeepSeek/weather, hoặc cơ chế activate model bỏ qua quality gate. Warnings chủ yếu nằm ở việc biến prose thành contract executable và bảo đảm acceptance attribution không thể bị client giả mạo hoặc thất lạc.

### Warnings

| # | File:Line | Category | Issue | Recommendation |
|---|---|---|---|---|
| 1 | `2026-10-13-contracts.md:131,228-241` | Business logic/API | Accepted request mang order evidence từ POS nhưng chưa có cơ chế xác minh commit thật từ Order. | Chốt Order line-added attribution event hoặc internal verification endpoint; client fields chỉ là hint. |
| 2 | `2026-10-13-contracts.md:108-141` | Authorization | Rule list dùng `rule:draft/read`, chưa tách permission `rule:read` và scope tenant-wide. | Tách permission, ghi rõ Owner/Manager list/draft scope, rồi sinh policy/negative tests. |
| 3 | `2026-10-13-contracts.md:314-354,458-477` | Contract completeness | Internal responses và event payload mới có catalogue/minimum fields, chưa đủ required/nullability/examples để generate artifact trực tiếp. | Materialize OpenAPI 3.1, AsyncAPI/JSON Schema và producer/consumer fixtures trước DTO/controller. |
| 4 | `2026-10-13-contracts.md:405-434,688-705` | Data/privacy | Join giữa `OrderPaid.v1`, Loyalty opaque key và `paid_interaction_projections` chưa có một source contract duy nhất; retention/deletion policy còn config-level. | Chốt stable opaque reference, consent/deletion semantics, snapshot policy và policy version trong training/model metadata. |
| 5 | `2026-10-13-contracts.md:322-350,779-815` | ML/provider safety | Threshold model, DeepSeek schema, budget/timeout/egress/retry và DLQ policy chưa thành fixture đầy đủ. | Version hóa quality/provider policy, validate structured output, bounded async retry, human review và feature flag publication. |
| 6 | `2026-10-13-contracts.md:827-858` | Data/migration | DDL, comment, partial indexes, partition/retention assertions mới được mô tả, chưa chạy được trên empty/copy database. | Tạo Flyway migrations + `COMMENT ON COLUMN`, constraint/index tests, restore/rebuild và retention tests. |

### Checklist summary

| Category | Verdict | Issues |
|---|---|---:|
| Security | PASS WITH WARNINGS | 2 policy/verification warnings; 0 critical |
| Business Logic | PASS WITH WARNINGS | 1 accepted-evidence warning; fallback/empty/stale semantics covered |
| Performance | PASS | 0 critical; bounded realtime path and async workers defined |
| Clean Design | PASS WITH WARNINGS | 1 machine-readable materialization warning |
| Conventions | PASS WITH WARNINGS | 2 parent contract/fixture alignment warnings |

## 5. Architecture summary

| Dimension | Summary |
|---|---|
| Logical components | Recommendation API, rule engine/ranker, Catalog/Inventory eligibility projection, model runtime, session/interaction writer, ALS worker, model registry, DeepSeek adapter, cold-start review và observability/outbox. |
| Database | 1 PostgreSQL Recommendation database; rules, projections, sessions, interactions, training/model metadata và cold-start state thuộc service; không shared DB. |
| Object storage | Encrypted immutable training snapshots/model artifacts; checksum bắt buộc khi activate. |
| APIs | Public recommendation/shown/accepted/rule lifecycle/model health/cold-start và internal Order context, training, model, rebuild, Loyalty feature-context contracts. |
| Events | `SuggestionShown.v1`, `SuggestionAccepted.v1`, rule/model lifecycle, training completion và cold-start candidate events; at-least-once với outbox/inbox dedupe. |
| Correctness model | Association candidate chỉ là suggestion; Order là authority cuối cho add-line. Published rules/models immutable; model activation dùng quality gate + CAS; projection stale loại candidate an toàn. |
| Privacy boundary | Loyalty giữ phone/customer identity; Recommendation chỉ giữ opaque/keyed reference, consent/eligibility/version và redacted analytics. |

## 6. Implementation gates và risks

| Gate/Risk | Điều kiện hoàn tất |
|---|---|
| Accepted attribution | Order amendment có authoritative line-added evidence hoặc verification path; add-line success nhưng acceptance retry/failure có reconciliation; duplicate acceptance converges về một source key. |
| Generated contracts | Public/internal OpenAPI, AsyncAPI/JSON Schema, examples và compatibility checks validate trong CI; producer fixtures cho Order/Catalog/Inventory/Loyalty pass. |
| Rule governance | `rule:read`/`rule:draft`/`rule:publish` policy tách rõ; Manager chỉ draft/edit, Owner publish/retire; published rule immutable. |
| Projection safety | Source event có event ID, tenant/branch scope, source version, occurredAt; checkpoint monotonic; gap/stale triggers rebuild; Order vẫn validate cuối. |
| ALS privacy/quality | Training chỉ từ `OrderPaid.v1`; opaque customer join, consent/deletion, sparse threshold, metric thresholds và policy version được fixture hóa; previous active model giữ được để rollback. |
| DeepSeek safety | Job async ngoài realtime path; sanitized input/structured output/schema validation, timeout/budget, bounded retry/DLQ, human review và no direct Catalog mutation. |
| Database correctness | Flyway chạy trên empty/copy DB; tenant predicates, unique active model/rule, interaction idempotency, CAS/lease, comments/indexes/partition assertions đều pass. |
| Operational readiness | Metrics/alerts cho p95, empty rate, shown→accepted, projection lag, model fallback, training failure, provider error, outbox lag và DLQ; runbook replay/redaction có sẵn. |

## 7. Implementation roadmap

### Phase 0 — Contract materialization và foundation

- **Goal:** Chuyển design prose thành executable contracts và service foundation.
- **Tasks:** Tạo public/internal OpenAPI 3.1; AsyncAPI/JSON Schema; Flyway baseline; common envelope/idempotency/outbox/inbox; tenant-scope test harness; parent Order/Loyalty/Catalog/Inventory amendments.
- **Dependencies:** Parent contract owners.
- **Exit criteria:** Schemas validate; generated fixtures pass; accepted evidence design được ký duyệt; no cross-tenant/branch negative test passes.

### Phase 1 — P0 association recommendation

- **Goal:** Gợi ý theo giỏ hàng có lọc branch/cart/availability và latency ổn định.
- **Tasks:** Rule set/version CRUD; Manager draft + Owner publish/retire; Catalog/Inventory projections; canonical basket signature; deterministic rank; suggestion session/candidate snapshot; cache invalidation; `POST recommendations`.
- **Dependencies:** Phase 0 và Catalog/Inventory source events.
- **Exit criteria:** Seed rule “gà rán → Coca” trả đúng candidate; item đã có/inactive/unavailable bị loại; không có rule hợp lệ trả `items=[]`; p95 đạt target.

### Phase 2 — P0 interaction analytics và Order attribution

- **Goal:** Đo chính xác impression và acceptance mà không làm checkout phụ thuộc Recommendation.
- **Tasks:** Render-confirmed `/shown`; Order add-line attribution/verification; `/accept`; event outbox; dedupe/retry/reconciliation; Reporting/Audit consumer fixtures.
- **Dependencies:** Phase 1 và Order amendment.
- **Exit criteria:** Render mới ghi `SuggestionShown`; add-line fail không ghi accepted; add-line success ghi đúng một accepted; stale recommendation không làm Order fail.

### Phase 3 — P1 Loyalty context và ALS

- **Goal:** Personalize khi có khách đủ điều kiện, fallback an toàn khi sparse/unavailable.
- **Tasks:** Opaque Loyalty feature-context query/projection; paid interaction projection từ `OrderPaid.v1`; nightly snapshot/train/evaluate; model registry/active pointer; runtime cache/fallback; rollback/replay.
- **Dependencies:** Phase 0–2 và Loyalty feature contract.
- **Exit criteria:** Không có phone trong Recommendation; train chỉ từ paid facts; model chưa đạt gate không activate; sparse customer/model failure fallback association; rollback không gián đoạn P0.

### Phase 4 — P2 cold start và context extension

- **Goal:** Enrich món mới ngoài realtime path và chuẩn bị contextual ranking.
- **Tasks:** DeepSeek async job/adapter; sanitized structured output; validation/human review; candidate approval; feature flag; extension point cho time/weather không tích hợp provider trong MVP.
- **Dependencies:** Phase 0 và Catalog item events; không block P0/P1.
- **Exit criteria:** Provider timeout/rate-limit không ảnh hưởng POS; candidate chưa review không đi vào production ranking; prompt/provider/raw response không rò rỉ.

### Phase 5 — Hardening và rollout

- **Goal:** Đo SLO, bảo đảm replay/restore và chuẩn bị triển khai.
- **Tasks:** k6 recommendation/interaction profile; event replay/out-of-order/gap; projection rebuild; model load/rollback drill; tenant/IDOR/security tests; log redaction; DLQ/runbook; dashboard conversion funnel.
- **Dependencies:** Phases 1–4.
- **Exit criteria:** P0 acceptance evidence đầy đủ; measured p95/event lag/fallback rate; restore/replay không tạo duplicate; warnings còn lại có owner/date.

**Tổng ước lượng:** khoảng **5–7 tuần** cho vertical slices P0 → P1 → P2, chưa tính thời gian chờ parent contract review, infrastructure provisioning hoặc provider approval.

## 8. Traceability matrix

| Requirement | Architecture owner | Contract evidence | Acceptance evidence |
|---|---|---|---|
| P0 association theo cart | Rule Engine + Recommendation API | Recommendation request/response, rule versions, basket signature | Seed rule, multi-item cart, deterministic ranking, empty result |
| P0 loại món đã có/hết hàng/tắt | Eligibility Projection + Order final validation | Catalog/Inventory source events, projection watermark, candidate snapshot | Inactive/wrong branch/unavailable/already-in-cart/stale fixtures |
| P0 `SuggestionShown` | Session/Interaction Writer + Outbox | `/shown`, interaction idempotency, `SuggestionShown.v1` | Rendered-only command, duplicate replay, no pre-render event |
| P0 `SuggestionAccepted` | Order attribution + Interaction Writer | `/accept`, add-line evidence, `SuggestionAccepted.v1` | Add-line fail/success, authoritative verification, duplicate/retry |
| P1 ALS khi nhập số điện thoại | Loyalty opaque context + Model Runtime | Loyalty feature context, paid interaction projection | No phone boundary, eligible/ineligible/sparse customer, tenant isolation |
| P1 nightly retrain | Scheduler + ALS Worker + Registry | Training run/snapshot/model lifecycle | Paid-only input, quality gate, CAS activation, rollback, previous model |
| P2 DeepSeek cold start | Async Adapter + Review Queue | Cold-start job/candidate/provider policy | Timeout/rate-limit/schema failure/human approval/no realtime dependency |
| P2 contextual extension | Ranking policy extension point | Versioned context fields, feature flag | Time/weather absent or disabled does not change P0 correctness |

## 9. Sequence diagrams

- **Status:** Generated and confirmed.
- **File:** [`2026-10-13-sequence-diagrams.adoc`](./2026-10-13-sequence-diagrams.adoc)
- **Coverage:** 14 PlantUML blocks, L1/L2 for cart recommendation, shown/accepted, stale projection, ALS fallback, nightly train/activation, model rollback/replay và DeepSeek cold start.
- **Design boundary:** Diagrams are contract-driven; repository chưa có implementation source nên chưa phải runtime trace.

## 10. Parent amendments required before implementation

- **POS Platform:** Replace the compact Recommendation skeleton with the detailed Recommendation contract; add rule/model lifecycle and interaction event schemas.
- **Order:** Add recommendation-context contract and authoritative add-line suggestion attribution/verification; preserve Order ownership of price, availability and final line commit.
- **Catalog/Inventory:** Add source event version, branch scope, observed/occurred timestamps and replay/checkpoint behavior used by Recommendation projections.
- **Loyalty:** Add opaque recommendation feature-context contract with eligibility, consent version, feature version and tombstone semantics; never expose phone plaintext.
- **Reporting/Audit:** Add consumer fixtures for shown/accepted/rule/model/cold-start events and clarify analytics attribution versus paid revenue truth.

## 11. Next steps

1. Tạo implementation backlog từ roadmap Phase 0–5.
2. Chốt và publish Order authoritative accepted-evidence amendment.
3. Tách `rule:read` và hoàn thiện authorization policy fixtures.
4. Materialize OpenAPI/AsyncAPI/Flyway artifacts; không viết controller/DTO/trainer/migration trực tiếp từ prose.
5. Chạy P0 vertical slice trước; giữ ALS/DeepSeek ngoài critical POS path.
6. Gán owner và deadline cho các warnings trước production rollout.

## 12. Final checklist

- [x] Vision approved và saved.
- [x] Requirements approved with warnings và traceable.
- [x] Architecture approved và nhất quán với requirements.
- [x] Contracts approved with warnings; contracts review đã hoàn tất.
- [x] Sequence diagrams L1/L2 đã generated và user confirmed.
- [x] P0/P1/P2 requirements mapped tới component, contract và acceptance evidence.
- [x] Tenant isolation, opaque customer boundary, outbox/inbox, idempotency, fallback và rollback đã được cover.
- [x] Implementation roadmap có phase, dependency và exit criteria.
- [ ] OpenAPI/AsyncAPI/Flyway executable artifacts được materialize.
- [ ] Order/Loyalty/Catalog/Inventory parent amendments được publish.
- [ ] Performance, replay, security negative và provider failure measurements được thu thập trong implementation.

Thiết kế Module 8 được xem là **APPROVED WITH WARNINGS** để chuyển sang implementation planning. Không viết code trực tiếp từ các giả định cũ của parent docs; dùng Recommendation Contracts và các implementation gates ở §6 làm nguồn chuẩn.

