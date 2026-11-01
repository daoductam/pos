Started at: 2026/10/13 00:00:00
Finished at: 2026/10/13 00:00:00
Total time: 0 minutes
---

# Module 10 — Báo cáo & Dashboard: Design Result

**Ngày:** 2026-10-13  
**Trạng thái:** Design complete — Approved with warnings  
**Phạm vi:** L — Full Ceremony  
**Module:** Báo cáo & dashboard (Reporting read model; Athena là extension boundary)

## 1. Verdict

**APPROVED WITH WARNINGS**

Thiết kế đủ cơ sở để tạo implementation backlog cho P0/P1. Reporting dùng PostgreSQL event-driven read models cho đường chạy MVP, giữ `OrderPaid.v1` là nguồn sự thật doanh thu và không query chéo database. Không có blocker mức Critical/RED ở scope, ownership, tenant isolation hoặc hướng kiến trúc.

Trước khi viết code phải hoàn tất các parent amendments và materialize machine-readable contracts, migrations, fixtures và load profile được liệt kê ở §6.

## 2. Artifacts hoàn tất

| Phase | Artifact | Path | Status |
|---|---|---|---|
| Vision | Vision | [`2026-10-13-vision.md`](./2026-10-13-vision.md) | Approved |
| Vision review | Spec Sentinel Report | [`2026-10-13-vision-review.md`](./2026-10-13-vision-review.md) | Warnings accepted |
| Requirements | Requirements | [`2026-10-13-requirements.md`](./2026-10-13-requirements.md) | Approved with warnings |
| Requirements review | Spec Sentinel Report | [`2026-10-13-requirements-review.md`](./2026-10-13-requirements-review.md) | Warnings accepted |
| Architecture | Architecture | [`2026-10-13-architecture.md`](./2026-10-13-architecture.md) | Approved |
| Sequence diagrams | L1/L2 PlantUML | [`2026-10-13-sequence-diagrams.adoc`](./2026-10-13-sequence-diagrams.adoc) | Generated and confirmed |
| Contracts | API & Data Contracts | [`2026-10-13-contracts.md`](./2026-10-13-contracts.md) | Approved with warnings |
| Contracts review | Spec Sentinel Report | [`2026-10-13-contracts-review.md`](./2026-10-13-contracts-review.md) | Warnings accepted |
| Finalization | Design Result | This document | Approved with warnings |

## 3. Design review summary

| Tiêu chí | Kết quả | Nhận xét |
|---|---|---|
| Completeness | PASS WITH WARNINGS | P0 daily/branch/top-items, P1 hourly/employee/promotion/recommendation, dashboard, freshness, correction, rebuild và test matrix đã có. OpenAPI/AsyncAPI/Flyway/fixtures vẫn phải generate trước code. |
| Consistency | PASS WITH WARNINGS | Metric dictionary, timezone, VND minor units, `OrderPaid` revenue truth, current corrected view và role scope nhất quán. Canonical correction/refund event và read-model grain cần được chốt executable. |
| Feasibility | PASS | Pre-aggregated PostgreSQL đáp ứng KPI p95 ≤1s/table p95 ≤2s; EventBridge/SQS + inbox/DLQ hỗ trợ replay; Athena không nằm trong hot path MVP. |
| Security | PASS WITH WARNINGS | Identity online decision, repository predicates, cache scope hash, no raw PII và audit boundary đã có. Parent permission schema, revocation fixtures và audit source-of-record cần hoàn tất. |
| Performance | PASS WITH WARNINGS | Indexes, bounded 12-month/20-branch query và cache TTL 15s đã nêu; cần load profile và threshold định lượng cho volume/partition/rebuild. |
| Observability | PASS | Watermark, freshness/completeness, DLQ, projection health, rebuild operation, correlation/causation và data-quality events đã được thiết kế. |
| Testing readiness | PASS WITH WARNINGS | Matrix bao phủ duplicate, out-of-order, correction, attribution, auth, cache, privacy và performance; fixtures/schema executable chưa được materialize. |

## 4. Finalization review — Code Reviewer gate

**Scope:** Toàn bộ Reporting design artifacts và parent contracts trực tiếp liên quan; repository chưa có application code hoặc migration source nên đây là design-quality review theo checklist security, business logic, performance, maintainability và convention.

**Verdict:** **YELLOW — Proceed with Caution**

### Summary

Thiết kế coherent và implementation-ready ở mức backlog. Không phát hiện hardcoded secret, bypass authorization, cross-tenant query chủ ý, raw PII trong response/event, hoặc Athena/LLM dependency trên dashboard hot path. Warnings tập trung vào việc biến parent amendments và prose contracts thành artifacts có thể generate/replay chính xác.

### Warnings

| # | File:Line | Category | Issue | Recommendation |
|---|---|---|---|---|
| 1 | `2026-10-13-contracts.md:96-126, 438-505` | Authorization/API | `report:read`/`reporting:operate` và Reporting `OrderPaid` amendment chưa được publish vào parent Identity/Order schemas. | Publish one canonical Identity decision schema and additive `OrderPaid.v1` schema; add scope, revocation, checkout-actor and promotion fixtures before generated clients. |
| 2 | `2026-10-13-contracts.md:506-513` | Business logic | Correction/refund contract còn để lựa chọn giữa `OrderRefunded.v1` và `OrderCorrected.v1`. | Chọn một canonical business-fact event, chốt partial/full refund, effective date, revision precedence và source dedupe key. |
| 3 | `2026-10-13-contracts.md:576-608, 686-712` | Data model | Một số bảng materialized dùng `period_start/period_end` trong khi public query nhận range tùy ý; recommendation aggregate thiếu lineage đủ cho replay/debug. | Chốt grain cố định rồi aggregate range ở query layer, hoặc bổ sung overlap/lineage invariants và append-only attribution facts. |
| 4 | `2026-10-13-contracts.md:770-790` | Audit boundary | Reporting có access-audit table/event trong khi parent Platform đặt Audit Service làm append-only owner. | Chốt Reporting là staging/reference hay ghi rõ exception; Audit Service nên là source-of-record cho retention/legal hold/query dài hạn. |
| 5 | `2026-10-13-contracts.md:896-914` | Maintainability/operations | OpenAPI, AsyncAPI, migrations, fixtures và load profile mới là artifact requirements, chưa có executable output; partition threshold chưa đo. | Tạo artifacts trước controller/consumer/query; chạy empty/copy DB migration, compatibility, replay và 12-month/20-branch load tests. |

### Checklist summary

| Category | Verdict | Issues |
|---|---|---:|
| Security | PASS WITH WARNINGS | 1 parent authorization/audit warning; 0 critical |
| Business Logic | PASS WITH WARNINGS | 2 canonical event/read-model lineage warnings |
| Performance | PASS WITH WARNINGS | 1 load/partition measurement warning; no unbounded public query by design |
| Clean Design | PASS WITH WARNINGS | 1 machine-readable materialization warning |
| Conventions | PASS WITH WARNINGS | Parent schema/catalogue alignment remains a prerequisite |

## 5. Architecture summary

| Dimension | Summary |
|---|---|
| Logical components | Angular dashboard, Query API, Identity scope client/policy, Redis cache, EventBridge/SQS consumer, inbox/dedupe, projection handlers, watermark/completeness, rebuild worker, Reporting PostgreSQL và future Athena boundary. |
| Read models | `sales_daily`, `sales_by_branch`, `sales_by_hour`, `sales_by_employee`, `top_items`, `promotion_effectiveness`, `recommendation_effectiveness`, `projection_watermarks`, `inbox_events`, `rebuild_operations`. |
| Source facts | `OrderPaid.v1` cho paid revenue; approved correction/refund business facts; Promotion snapshots; Recommendation shown/accepted; Identity/branch/employee metadata. Payment late settlement không tự thành revenue. |
| API | Public REST `/api/v1/reports/*` cho overview, sales, top items, promotion, recommendation và freshness; internal `/internal/v1/reporting/*` cho rebuild/health. |
| Correctness model | At-least-once event processing, event/source dedupe, monotonic watermark, current corrected view, `asOfWatermark`, explicit `FRESH/STALE/PARTIAL/UNAVAILABLE/NO_DATA`. |
| Authorization | Owner tenant/subset branch; Manager assigned branches; Identity online decision trước cache; repository tenant/branch predicates; cache key chứa authorization version/scope hash. |
| Privacy/audit | Không trả customer phone, payment credential, raw order ID hoặc raw event; employee/promotion labels là snapshots an toàn; access evidence được redacted và audit boundary phải chốt với Audit Service. |

## 6. Implementation gates và risks

| Gate/Risk | Điều kiện hoàn tất |
|---|---|
| Parent event contracts | Order publish `OrderPaid.v1` additive fields cho business date/timezone, checkout actor, totals/lines và immutable promotion allocation; canonical correction/refund event được chọn và có fixtures. |
| Identity authorization | Publish `report:read` và restricted `reporting:operate`, scope decision schema, membership/authorization version, revocation behavior và deny-without-enumeration tests. |
| Generated contracts | OpenAPI 3.1, internal OpenAPI, AsyncAPI/JSON Schema, metric dictionary và Angular types validate trong CI; no controller/query DTO được tự viết lệch prose. |
| Projection correctness | Inbox atomicity, duplicate/out-of-order/gap handling, unknown version quarantine, monotonic watermark, current-pointer swap, correction replay và DLQ runbook pass. |
| Attribution | Employee missing → `UNASSIGNED`; promotion grouped by immutable version; recommendation paid-only, same order/cart context, multiple accepts count AOV once/order và control denominator zero → `NO_DATA`. |
| Database | Flyway forward-only migrations, tenant predicates, indexes/checks/comments, unique dedupe, bounded rebuild leases và deterministic seed/calculation version pass trên empty/copy DB. |
| Freshness/performance | FRESH ≤1m, STALE >1m, alert >5m; KPI p95 ≤1s, table/drill-down p95 ≤2s; 12-month/20-branch and concurrent dashboard load measured. |
| Security/privacy | Negative tenant/branch/IDOR/cache-revocation tests, no PII in API/logs/traces/events, authorization before cache, audit reference and retention behavior verified. |
| Athena boundary | No Athena dependency for MVP P0/P1; evaluate only after measured volume/query/retention threshold and a separate contract/rollout decision. |

## 7. Implementation roadmap

### Phase 0 — Contract materialization và parent amendments

- **Goal:** Chuyển Contracts thành artifact executable và đồng bộ các parent boundary.
- **Tasks:** Tạo public/internal OpenAPI, AsyncAPI/JSON Schema, metric dictionary, Flyway schema, generated Angular mocks/types; publish Identity permission, OrderPaid attribution/promotion và canonical correction/refund amendments; chốt Audit ownership.
- **Exit criteria:** Schema compatibility pass; fixtures cho auth, revenue, correction, promotion, recommendation và freshness pass; không có ambiguity về source-of-truth hoặc read-model grain.

### Phase 1 — P0 projection foundation

- **Goal:** Xây projection doanh thu theo ngày/chi nhánh và món bán chạy.
- **Tasks:** EventBridge/SQS consumer, inbox/dedupe, `sales_daily`, `sales_by_branch`, `top_items`, watermark/completeness, DLQ, rebuild/replay worker và projection health.
- **Exit criteria:** Duplicate/out-of-order event không double count; `OrderPaid` canonical; current correction/replay deterministic; tenant/branch negative tests pass; freshness target đạt trong profile chuẩn.

### Phase 2 — P0 dashboard/API

- **Goal:** Owner/Manager dùng dashboard Angular với scope và data-quality state rõ ràng.
- **Tasks:** Query API overview/daily/branch/top-items/freshness, Identity scope resolution, repository predicates, Redis cache/ETag, KPI cards/charts/tables/drill-down và safe errors.
- **Exit criteria:** Owner/Manager chỉ thấy scope hợp lệ; revocation không làm cache leak; KPI p95 ≤1s, table p95 ≤2s; UI phân biệt `NO_DATA`, `STALE`, `PARTIAL`, `UNAVAILABLE`.

### Phase 3 — P1 attribution read models

- **Goal:** Bổ sung sales theo giờ/employee và hiệu quả promotion/recommendation.
- **Tasks:** `sales_by_hour`, `sales_by_employee`, `promotion_effectiveness`, `recommendation_effectiveness`; consume checkout actor, promotion version/allocation và shown/accepted + OrderPaid; implement late attribution/correction lineage.
- **Exit criteria:** Missing employee vào `UNASSIGNED`; promotion versions không merge; recommendation chỉ tính paid orders, acceptance interaction-level và AOV order-level; all golden attribution fixtures pass.

### Phase 4 — Hardening và Athena evaluation boundary

- **Goal:** Bảo đảm vận hành, replay, scale và quyết định mở rộng lịch sử có bằng chứng.
- **Tasks:** Load/soak 12-month × 20-branch, queue/DLQ/freshness alerts, restore/rebuild drills, security/privacy tests, audit/retention verification, volume/partition measurement và Athena feasibility assessment.
- **Exit criteria:** SLO/SLA measurements được lưu; replay không tạo duplicate; runbook hoàn chỉnh; Athena chỉ được bật bằng decision/contract riêng sau khi đạt threshold.

## 8. Traceability matrix

| Requirement | Architecture owner | Contract evidence | Acceptance evidence |
|---|---|---|---|
| P0 doanh thu ngày/chi nhánh | Sales projection + Query API | `OrderPaid.v1`, `sales_daily`, `sales_by_branch`, overview/daily/branch endpoints | Canonical revenue fixture, duplicate/replay/correction, Owner/Manager scope |
| P0 món bán chạy | Top-item projection | `top_items`, net line revenue allocation, sort/tie rules | Quantity/revenue sort, tie-break, inactive item historical snapshot |
| P1 doanh thu theo giờ | Hour projection | `sales_by_hour`, branch-local/tenant timezone policy | Midnight/DST/multi-branch timezone fixtures, bounded range performance |
| P1 doanh thu theo nhân viên | Employee projection | Checkout actor snapshot, `UNASSIGNED`, attribution version | Quick-switch/disabled/missing employee fixtures, branch isolation |
| P1 hiệu quả khuyến mãi | Promotion projection | Immutable promotion/rule version, allocation status | Multiple versions/conflict group/unallocated/correction fixtures |
| P1 hiệu quả gợi ý | Recommendation projection | shown/accepted events, paid-only cohort, observed AOV difference | Duplicate/multiple accept/control zero/pending-to-paid fixtures |
| Freshness/correction | Watermark + rebuild worker | DataQualityEnvelope, `asOfWatermark`, rebuild API/events | FRESH/STALE/PARTIAL/UNAVAILABLE/NO_DATA, late correction and replay |
| Dashboard security | Identity scope + repository policy | `report:read`, `reporting:operate`, cache key and predicates | IDOR, revoked membership, foreign branch, cache-after-revocation tests |

## 9. Parent amendments required before implementation

- **Identity Access:** Add `report:read` and `reporting:operate`, canonical authorization decision schema, tenant/subset branch scope, membership/authorization version and revocation semantics.
- **Order:** Extend `OrderPaid.v1` additively with checkout actor, business date/timezone, totals/line snapshots and applied promotion allocation; choose and publish canonical refund/correction event.
- **Loyalty/Promotion:** Preserve immutable promotion/rule version, conflict group, voucher/promotion source and applied discount allocation in paid-order snapshot; Reporting never re-evaluates current rules.
- **Recommendation:** Ensure `SuggestionShown.v1`/`SuggestionAccepted.v1` have stable interaction IDs, same order/cart context, model/rule version, acceptance evidence and replay ordering.
- **POS Platform:** Update Reporting service/read-model/event catalogue to include all P0/P1 projections, freshness/rebuild operations and ownership boundaries.
- **Audit/Observability:** Chốt Audit Service source-of-record versus Reporting reference/staging; publish redacted access, projection, rebuild and data-quality event schemas.

## 10. Final checklist

- [x] Vision approved và saved.
- [x] Requirements approved with warnings và traceable.
- [x] Architecture approved; sequence flows đã confirmed.
- [x] Contracts approved with warnings; Contracts review đã hoàn tất.
- [x] P0/P1 requirements mapped tới component, contract và acceptance evidence.
- [x] Tenant/branch authorization, cache isolation, freshness, correction, replay và DLQ đã được cover.
- [x] Implementation roadmap có phase, dependency và exit criteria.
- [ ] OpenAPI/AsyncAPI/Flyway executable artifacts được materialize.
- [ ] Identity/Order/Promotion/Recommendation/Platform parent amendments được publish.
- [ ] Load, replay, security-negative, privacy và retention measurements được thu thập.

Thiết kế Module 10 được xem là **APPROVED WITH WARNINGS** để chuyển sang implementation planning. Không viết code trực tiếp từ prose hoặc parent skeleton cũ; dùng Contracts, metric dictionary, generated schemas và các implementation gates ở §6 làm nguồn chuẩn.
