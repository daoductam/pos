Started at: 2026/10/13 00:00:00
Finished at: 2026/10/13 00:00:00
Total time: 0 minutes
---

# Spec Sentinel Report

**Spec:** [`docs/designs/reporting/2026-10-13-requirements.md`](./2026-10-13-requirements.md)  
**Date:** 2026-10-13  
**Status:** ⚠️ Warnings (Proceed with Caution)

## Review context

- Phase 0 skipped — Vision and Vision Review were completed in the current Module 10 workflow.
- No application source or Reporting migration exists; cross-impact checks use the parent POS Platform, Order, Loyalty and Recommendation contracts.
- Requirements now resolve the Vision warnings at the business-rule level. Remaining warnings are contract materialization and parent amendment gates.

## Findings

### Warnings

| # | Category | Finding | Risk | Recommendation |
|---|---|---|---|---|
| 1 | Parent Order event | Requirements require immutable checkout employee attribution and promotion breakdown in `OrderPaid.v1`, but the current parent Order contract does not yet show these Reporting fields as normative. | Employee and promotion reports cannot be generated consistently across producer/replay paths. | Apply the parent Order amendment and publish producer/consumer JSON Schema fixtures before projection implementation. |
| 2 | Promotion source | Requirements allow an applied-promotion snapshot or versioned order discount breakdown, but only one canonical wire shape should be accepted by Reporting. | Two producers may allocate discount differently or cause double-counted promotion revenue. | Make `OrderPaid.v1` applied-promotion breakdown the canonical source; treat separate promotion events as optional enrichment only. |
| 3 | Recommendation control | The same order/cart attribution context and one-count-per-order rule are defined, but the parent Recommendation event schema and event-to-paid-order pending state still need a generated fixture. | Acceptance and control cohorts can diverge under retries, multiple accepted suggestions or late `OrderPaid`. | Add an end-to-end fixture with stable interaction ID, shown/accepted ordering, order correlation and late paid completion. |
| 4 | Projection revision | Current corrected view and `asOfWatermark` are required, but the exact source-watermark ordering/rebuild-swap transaction is deferred to Architecture/Contracts. | Replay or late correction can expose mixed projection versions or double-count historical facts. | Define projection version swap, watermark monotonicity, correction lineage and current-view atomicity. |
| 5 | Freshness semantics | `STALE` is readable for lag >1 minute and escalates after 5 minutes, but readiness/completeness rules for each source and branch are not yet machine-readable. | Dashboard may label a partially populated aggregate as fresh or stale inconsistently. | Define per-read-model required sources, completeness calculation, state transition thresholds and golden freshness fixtures. |
| 6 | API/cache contract | The API catalogue and scope-safe cache rules are specified, but exact query parameter names, cursor encoding, ETag behavior and response schemas are still prose. | Angular clients and server implementations may disagree, especially after authorization revocation. | Materialize OpenAPI 3.1 with examples for all statuses, scope filters, cursor pagination and cache revalidation. |
| 7 | Storage scale | Pre-aggregates and 12-month range are required, but volume assumptions and partition thresholds are not quantified. | A PostgreSQL read model may meet functional requirements but fail latency under tenant/branch growth. | Define load profile, index/partition candidates, retention and measured Athena escalation criteria in Architecture. |
| 8 | Permission ownership | `report:read` and `reporting:operate` are proposed but not yet in the parent Identity permission catalogue. | API authorization can be implemented with an ad-hoc role check rather than the shared decision contract. | Add permissions, scope semantics and denied-without-enumeration fixtures to Identity. |

## Cross-Impact Analysis

| Dimension | Finding | Risk Level | Recommendation |
|---|---|---|---|
| **Shared Logic** | Reporting consumers depend on OrderPaid, Order correction/refund, Recommendation interactions, promotion snapshots and branch/employee metadata; AI Assistant and Dashboard consume the resulting metrics. | 🔴 High | Version every producer/consumer schema and preserve one metric dictionary/calculation version. |
| **Data/State** | Seven pre-aggregated read models plus inbox/watermark/rebuild state; correction and replay must be idempotent. | 🔴 High | Design source keys, tenant/branch composite indexes, projection version swap and rebuild lineage before migrations. |
| **API Contracts** | Nine public report/freshness endpoints with shared scope/filter/error/data-quality envelopes. | 🟡 Medium | Generate OpenAPI and Angular client types; test cache/authorization ordering. |
| **Flow Integrity** | Event → validate/dedupe → project → watermark → API/cache → dashboard is specified; parent event extensions and current-version swap remain gates. | 🟡 Medium | Add sequence diagrams for normal, duplicate, late, partial and rebuild paths. |
| **Cross-Cutting** | Multi-tenant security, PII minimization, 12-month query load, freshness alerts and replay audit are covered. | 🟡 Medium | Add IDOR, load, privacy, observability and DLQ replay tests to Contracts. |

## Instruction Compliance

| Dimension | Verdict | Deviation (if any) | Recommendation |
|---|---|---|---|
| **Conventions** | ✅ | REST/OpenAPI 3.1, common envelopes, UUID v7, UTC, integer money, cursor pagination and event-driven projections are aligned. | — |
| **Architecture** | ✅ | Reporting remains metric owner and does not query producer databases; PostgreSQL MVP/Athena boundary is explicit. | Materialize parent amendments and versioned read-model contracts. |
| **Testing** | ⚠️ | Requirements contain a broad matrix, but event fixtures, metric goldens, migration/replay tests and OpenAPI examples are not yet files. | Generate fixtures and contract tests before implementation. |

## Verdict

Requirements **có thể được duyệt với warnings**. Không có blocker về business logic hay security boundary. Architecture phải đóng các gate về canonical event shapes, projection revision/freshness state, API schema, Identity permissions và storage/load profile trước khi Contracts được duyệt.

