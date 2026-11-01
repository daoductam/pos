# Spec Sentinel Report

**Spec:** [`docs/designs/recommendation/2026-10-13-requirements.md`](./2026-10-13-requirements.md)  
**Date:** 2026-10-13  
**Status:** ⚠️ Warnings (Proceed with Caution)

## Review context

- Reused the approved Recommendation Vision and Vision review.
- Applied `spec-reviewer`, `api-design`, `database-design` and observability/privacy checks.
- Cross-checked parent POS Platform, Order, Catalog, Loyalty and existing Recommendation skeleton.
- Không có Recommendation/ML implementation source, OpenAPI artifact hoặc migration source để trace.

## Findings

## Warnings

| # | Category | Finding | Risk | Recommendation |
|---:|---|---|---|---|
| 1 | Order attribution | Requirements yêu cầu `SuggestionAccepted` chỉ sau add-line thành công và có `orderLineId/orderVersionAfterAdd`, nhưng parent Order chưa có event/field attribution chuẩn cho suggestion. | UI có thể tự khai success hoặc acceptance không liên kết với line commit thật. | Architecture/Contracts chọn một source chuẩn: Order line-added attribution event hoặc signed mutation evidence; chỉ emit accepted một lần theo source key. |
| 2 | Loyalty feature contract | `customerId` opaque/feature key, consent, minimum signal và deletion/retention behavior chưa tồn tại trong Loyalty parent contract. | ALS có thể thiếu dữ liệu, train sai customer hoặc xử lý identity ngoài policy. | Định nghĩa internal Loyalty feature-context contract, tenant/consent checks và data lifecycle trước P1 implementation. |
| 3 | Projection freshness | TTL 60s và stale candidate policy đã nêu, nhưng Catalog/Inventory event version, availability semantics và snapshot recovery chưa có schema. | Candidate list có thể khác giữa Recommendation và Order hoặc projection không tự rebuild được. | Architecture chọn event checkpoint/rebuild strategy; Contracts định nghĩa source version, freshness metadata và stale response behavior. |
| 4 | Rule representation | Multi-item antecedent, variants/modifiers, candidate canonical identity và metric validation chưa có concrete schema/API shape. | Rule engine và UI rule editor có thể hiểu khác nhau; ranking không reproducible. | Contracts định nghĩa canonical basket signature, rule version/effective scope, support/confidence/lift ranges và deterministic tie-break. |
| 5 | Model quality policy | Requirements nêu Recall@K/NDCG@K/coverage nhưng chưa chọn metric bắt buộc, threshold ownership hoặc evaluation window. | Model candidate có thể được activate theo quality gate không nhất quán giữa tenant/run. | Architecture/Contracts lưu policy version + hard gates; nightly run phải fail closed nếu metric thiếu. |
| 6 | ML data governance | Snapshot dedupe, event correction, customer deletion propagation và artifact retention mới ở mức invariant. | Rebuild/replay có thể tạo model khác hoặc giữ feature sau khi customer bị xóa/blocked. | Định nghĩa immutable snapshot manifest, source watermark, tombstone handling, retention/purge job và reproducibility metadata. |
| 7 | P2 provider operations | DeepSeek async flow có schema validation/human review nhưng chưa có concrete provider budget, prompt versioning và approval queue contract. | Chi phí/rate limit hoặc unsafe output có thể tăng mà không có bounded recovery. | Chốt adapter interface, sanitized payload schema, budget/rate limit, retry/DLQ và approval state ở Contracts. |
| 8 | API consistency | Parent có `POST /recommendations/{suggestionId}/accept`; Requirements thêm `/shown` và rule/model commands nhưng chưa có complete response/error examples. | Generated clients/Frontend có thể gửi evidence/idempotency khác nhau. | Contracts phải là API-first source với OpenAPI components/examples cho all command paths và cursor filters. |

## Cross-Impact Analysis

| Dimension | Finding | Risk Level | Recommendation |
|---|---|---|---|
| **Shared Logic** | Recommendation consumes Order, Catalog, Inventory, Loyalty facts and emits Reporting/Audit events; no implementation call sites exist. | 🔴 High | Treat all event/API shapes as versioned contracts; add producer/consumer fixtures before generated clients. |
| **Data/State** | Rules, projections, interactions, training snapshots, model registry and cold-start candidates require immutable lineage and rebuildability. | 🔴 High | Use tenant-scoped constraints, source watermarks, append-only interactions and active-model CAS pointer. |
| **API Contracts** | New shown/accepted evidence, rule governance, model status and Loyalty feature context extend parent API surface. | 🔴 High | Resolve exact schemas/statuses/auth and update parent Order/Loyalty/Recommendation sections before code. |
| **Flow Integrity** | Cart → filter → render → add-line → accepted; paid events → snapshot → train → validate → activate/fallback is covered, with source-evidence gaps noted. | 🟡 Medium | Add sequence diagrams for success/failure, stale projection, acceptance race, training rollback and replay. |
| **Cross-Cutting** | Tenant isolation, PII minimization, model reproducibility, prompt safety, latency and job observability are explicit but need operational values. | 🔴 High | Make thresholds/TTL/retention/budget config versioned and auditable; add alerts and privacy deletion tests. |

## Instruction Compliance

| Dimension | Verdict | Deviation (if any) | Recommendation |
|---|---|---|---|
| **Conventions** | ✅ | REST/OpenAPI, UUID v7, UTC, common envelope, cursor pagination and parent event envelope are followed. | — |
| **Architecture** | ⚠️ | Requirements intentionally defer projection transport, model registry and source-evidence choice to Architecture/Contracts. | Resolve these before architecture approval; do not implement from prose defaults. |
| **Testing** | ⚠️ | Acceptance criteria are broad, but executable ML evaluation, privacy deletion, projection rebuild and event attribution fixtures are not materialized. | Add unit/contract/integration/replay/load/security/model evaluation fixtures to implementation gate. |

## Verdict

Requirements **có thể được duyệt với warnings**. Không có blocker về scope hoặc business priority. Warnings phải được xử lý ở Architecture/Contracts, đặc biệt là Order add-line attribution, Loyalty feature-context contract, projection freshness/rebuild, rule schema và model governance.

