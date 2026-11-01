Started at: 2026/10/13 00:00:00
Finished at: 2026/10/13 00:00:00
Total time: 0 minutes
---

# Spec Sentinel Report

**Spec:** [`docs/designs/reporting/2026-10-13-vision.md`](./2026-10-13-vision.md)  
**Date:** 2026-10-13  
**Status:** ⚠️ Warnings (Proceed with Caution)

## Review context

- Phase 0 skipped — reusing the Module 10 scope decision, approved Vision exploration and parent POS platform context from this session.
- Repository scan found no application source or Reporting implementation. Existing related design artifacts are parent POS Platform, Order, Loyalty and Recommendation contracts.
- The Vision is clear enough for Requirements, but several cross-service data contracts must be made executable in Requirements/Architecture/Contracts.

## Findings

### Warnings

| # | Category | Finding | Risk | Recommendation |
|---|---|---|---|---|
| 1 | Reporting source contracts | Parent POS Platform currently documents `sales_daily` and `top_items`, while this Vision adds hourly sales, employee sales, promotion effectiveness and recommendation effectiveness. | Requirements may invent incompatible read models or query domain databases directly, breaking service ownership. | Define Reporting-owned read models, event inputs, metric dictionary, projection version and freshness/completeness envelope before Architecture. |
| 2 | Employee attribution | The Vision chooses the cashier active at successful checkout, but the current canonical `OrderPaid.v1` summary does not visibly guarantee a cashier/employee attribution field and version semantics. | Staff revenue can be assigned incorrectly after quick cashier switching or replays. | Add immutable `checkoutActor`/employee reference and attribution policy to the OrderPaid contract; define behavior for missing, changed or disabled employees. |
| 3 | Promotion attribution | Promotion requirements depend on Loyalty/Promotion quote, voucher, rule version and discount allocation, but Reporting input and refund/correction treatment are not yet defined as a single fact contract. | Discount amount, net revenue and promotion ROI can differ from checkout history. | Consume immutable applied-promotion facts or the OrderPaid snapshot with promotion breakdown; define conflict-group, discount allocation, reversal and late correction semantics. |
| 4 | Recommendation attribution | `SuggestionAccepted` is explicitly an attribution signal and `OrderPaid.v1` is canonical, but the Vision does not define the eligible-control population, attribution window or treatment of multiple suggestions in one basket. | Acceptance rate/AOV uplift may be biased or double-counted and cannot be reproduced. | Define shown/accepted/order linkage, eligible non-accepted control rules, attribution window, one/multiple suggestion handling and aggregation version. |
| 5 | Freshness SLO | A ≤1 minute dashboard target is stated, while the parent platform currently describes a broader event propagation target and at-least-once queues. | The UI may promise freshness the projection cannot guarantee during backlog, replay or provider outage. | Define normal/degraded freshness thresholds, lag status transitions, completeness rules, alerting and the exact meaning of `FRESH`/`STALE`/`PARTIAL`. |
| 6 | Timezone and comparison | Daily/hourly/branch metrics need branch-local business dates, cross-branch comparison timezone and DST behavior; the Vision lists this as unknown. | Day boundaries and hourly totals can disagree between dashboard, Order and branch operations. | Select a canonical timezone policy and require every aggregate response to return business date, timezone, period bounds and calculation version. |
| 7 | Historical correction | Refunds, corrections and late events can revise historical revenue and attribution, but the Vision only states replay/rebuild at a high level. | Dashboard numbers may change without lineage, or replay may double-count. | Define immutable source events, dedupe keys, correction/revision lineage, rebuild watermark and whether the dashboard shows current or as-of values. |
| 8 | 12-month query boundary | PostgreSQL is selected for dashboard traffic and the Vision allows up to 12 months, but partition/index/aggregation strategy is not yet defined. | Large cross-branch queries may violate latency targets or overload the operational read model. | Design query-first read models, indexes/partitions and pre-aggregates; define a measured threshold for Athena/ad-hoc migration. |
| 9 | Dashboard authorization | Shared Owner/Manager UI and drill-down are in scope, but resource-level authorization for every filter, branch list and drill-down path is not yet specified. | A valid dashboard endpoint could leak unauthorized branch or employee data through parameters or cached responses. | Require online Identity scope resolution, repository predicates, cache key scope isolation and negative IDOR fixtures for all report resources. |

## Cross-Impact Analysis

| Dimension | Finding | Risk Level | Recommendation |
|---|---|---|---|
| **Shared Logic** | Reporting consumes `OrderPaid.v1`, `SuggestionShown.v1`, `SuggestionAccepted.v1`, promotion/discount facts and branch/employee context; consumers include Dashboard, AI Assistant and Audit. | 🔴 High | Treat each event as a versioned API; add source/consumer compatibility tests and never derive facts from another service's database. |
| **Data/State** | New read models for daily/branch/hourly sales, top items, employee, promotion and recommendation metrics; projections must be replayable. | 🔴 High | Define tenant/branch keys, source event IDs, projection version, watermark, correction lineage and idempotent upsert rules. |
| **API Contracts** | New dashboard report endpoints with filters, drill-down, cursor/pagination, freshness and partial-data states. | 🟡 Medium | Use REST/OpenAPI 3.1, allowlisted filters, scope-aware cache keys and explicit error/data-quality envelopes. |
| **Flow Integrity** | Event → validation/dedupe → projection → freshness → API → dashboard; replay and late correction paths are not yet executable. | 🟡 Medium | Add sequence diagrams and state/fixture coverage for duplicate, out-of-order, stale, partial and rebuild flows. |
| **Cross-Cutting** | Multi-tenant authorization, dashboard performance, PII minimization for employee/customer-linked facts, observability and data retention are all relevant. | 🟡 Medium | Define redacted report payloads, query limits, lag alerts, audit access and retention by read model. |

## Instruction Compliance

| Dimension | Verdict | Deviation (if any) | Recommendation |
|---|---|---|---|
| **Conventions** | ✅ | Vision follows the parent CQRS, database-per-service, event-driven, REST/OpenAPI, UUID v7 and UTC direction. | — |
| **Architecture** | ⚠️ | Reporting ownership is clear, but concrete input event/read-model boundaries are intentionally deferred to Requirements. | Resolve source fact ownership and no-cross-database rule in Requirements/Architecture. |
| **Testing** | ⚠️ | Success criteria exist, but executable metric, replay, authorization, freshness and dashboard interaction fixtures are not yet defined. | Add contract, projection replay, security, load and UI acceptance matrices in Requirements. |

## Verdict

Vision **có thể tiếp tục sang Requirements với warnings**. Không có blocker về business scope hoặc dashboard direction. Requirements phải đóng các warning về event/source contracts, attribution, timezone/freshness, historical revision, query scaling và drill-down authorization trước khi Architecture được duyệt.

