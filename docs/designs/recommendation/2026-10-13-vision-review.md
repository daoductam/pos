# Spec Sentinel Report

**Spec:** [`docs/designs/recommendation/2026-10-13-vision.md`](./2026-10-13-vision.md)  
**Date:** 2026-10-13  
**Status:** ⚠️ Warnings (Proceed with Caution)

## Review context

- Phase 0 skipped — reusing scope/context from the current design session.
- Vision đã được user xác nhận sau khi làm rõ P0/P1/P2 behavior và chọn Hybrid Recommendation Service.
- Cross-checked parent POS Platform, Order, Catalog và Loyalty documents.
- Không tìm thấy Recommendation/ML implementation source; chỉ có parent API/schema skeleton.

## Findings

## Warnings

| # | Category | Finding | Risk | Recommendation |
|---:|---|---|---|---|
| 1 | API/event boundary | Vision cần frontend xác nhận `SuggestionShown` sau render và `SuggestionAccepted` sau Order add-line thành công, nhưng parent hiện mới có endpoint recommendation/accept và event table cơ bản. | Impression/acceptance có thể bị ghi sớm, duplicate hoặc không phản ánh UI/Order outcome. | Requirements/Contracts phải định nghĩa explicit shown/accepted command, interaction idempotency và correlation với Order version/add-line result. |
| 2 | Rule data model | Parent `association_rules` mới mô tả một antecedent item → consequent item và confidence; Vision cần cart nhiều item, variant/modifier, support/lift, priority và version. | Rule engine có thể không biểu diễn đúng basket context hoặc ranking deterministic. | Mở rộng rule schema với antecedent set/signature, candidate identity type, metrics, version/status/effective scope; vẫn giữ tenant-wide policy. |
| 3 | Availability freshness | Recommendation dựa trên Catalog/Inventory projection nhưng event shape, freshness SLA và behavior khi projection quá cũ chưa được chốt. | Có thể đề xuất món đã hết hàng hoặc loại nhầm món hợp lệ; UX không nhất quán giữa gợi ý và Order. | Định nghĩa source events, projection watermark/TTL, stale policy và yêu cầu Order validation cuối trước add-line. |
| 4 | Personalization/privacy | `OrderPaid.v1` có thể không có `customerId`; Loyalty feature key, consent, retention và minimum ALS signal chưa có contract. | Model sparse, rò rỉ customer identity hoặc train sai tenant/khách không consent. | Chốt opaque feature contract, eligible customer policy, minimum interaction threshold, tenant-isolated training snapshot và retention/deletion behavior. |
| 5 | Model operations | Model registry, artifact encryption, validation metrics, rollback/retention và ownership của nightly job mới ở mức nguyên tắc. | Model lỗi/không tương thích có thể activate hoặc không thể rollback/reproduce. | Architecture/Contracts phải định nghĩa model lifecycle, quality gates, artifact metadata, active pointer, rollback và run/DLQ state. |
| 6 | External AI | DeepSeek P2 cần prompt/schema, dữ liệu được phép gửi, rate/cost limit, provider outage và human review cụ thể. | Có thể gửi PII/menu data không phù hợp hoặc tạo tag/feature không an toàn. | Giữ async adapter; chỉ gửi Catalog metadata đã sanitize, validate structured output, lưu provenance và approval state; không block P0/P1. |
| 7 | Rule governance | Vision chưa chốt ai tạo/sửa/publish association rules và cách audit thay đổi. | Manager/Owner có thể tạo rule khác nhau giữa branch hoặc thay đổi ranking không truy nguyên. | Requirements định nghĩa Owner/Manager permission, tenant-wide ownership, draft/publish/version/audit workflow. |

## Cross-Impact Analysis

| Dimension | Finding | Risk Level | Recommendation |
|---|---|---|---|
| **Shared Logic** | Recommendation consumes Order `OrderCreated`/`OrderPaid`, Catalog availability and Inventory availability/stock events; emits Suggestion events to Reporting/Audit. | 🟡 Medium | Treat event schemas as versioned APIs; add consumer fixtures and replay/dedupe tests. |
| **Data/State** | New rule, projection, suggestion session/event, model metadata/artifact and training-run state; no implementation tables exist. | 🔴 High | Use explicit tenant predicates, immutable event lineage, model version pointer and rebuildable projections. |
| **API Contracts** | Parent recommendation routes are incomplete for render-confirmed shown and add-success accepted semantics; Loyalty feature lookup is not defined. | 🔴 High | Resolve in Requirements/Contracts before generated clients or frontend integration. |
| **Flow Integrity** | Cart → rules/filter → render → Order add → accepted log is conceptually complete; stale availability and failed add paths need exact response/command behavior. | 🟡 Medium | Add sequence diagrams and acceptance fixtures for stale projection, add failure, retry and expired suggestion. |
| **Cross-Cutting** | Tenant isolation, PII minimization, ML reproducibility, model rollback, latency and async worker observability are required. | 🔴 High | Define privacy/retention, quality gates, metrics, DLQ/replay and p95/error budgets in Requirements. |

## Instruction Compliance

| Dimension | Verdict | Deviation (if any) | Recommendation |
|---|---|---|---|
| **Conventions** | ✅ | Vision follows `docs/designs/recommendation/`, parent REST/event/database boundaries and UUID/UTC conventions. | — |
| **Architecture** | ⚠️ | Recommendation projection/model ownership is clear, but parent event/API/schema skeleton needs specialization. | Resolve event shape, rule schema and model lifecycle before Architecture approval. |
| **Testing** | ⚠️ | Success criteria and risks exist, but executable ML evaluation, projection freshness and UI/Order interaction fixtures are not yet defined. | Add unit, contract, replay, privacy, model validation and E2E acceptance fixtures in Requirements/Contracts. |

## Verdict

Vision **có thể tiếp tục sang Requirements với warnings**. Không có blocker về scope, ownership hay priority boundary. Các điểm phải được xử lý trước code là shown/accepted contract, association-rule schema, availability freshness, opaque Loyalty feature contract và model/DeepSeek operations.

