Started at: 2026/10/13 00:00:00
Finished at: 2026/10/13 00:00:00
Total time: 0 minutes
---

# Spec Sentinel Report — Recommendation API & Data Contracts

**Spec:** [`2026-10-13-contracts.md`](./2026-10-13-contracts.md)  
**Date:** 2026-10-13  
**Status:** ⚠️ Warnings (Proceed with Caution)

## Review scope

- Reused the approved Recommendation Vision, Requirements, Architecture và sequence diagrams.
- Đối chiếu parent POS Platform, Order, Catalog, Inventory, Loyalty và Payment contracts.
- Repository chưa có Recommendation implementation source, generated OpenAPI/AsyncAPI hoặc migration source; review này kiểm tra design contracts, không phải code review.

## Findings

### Warnings

| # | Category | Finding | Risk | Recommendation |
|---|---|---|---|---|
| 1 | Accepted evidence | `POST /recommendations/{suggestionId}/accept` nhận `orderLineId`, `orderVersionAfterAdd` và `addLineCorrelationId` từ POS; contract chưa yêu cầu Recommendation xác minh được add-line commit thật từ Order hoặc một event authoritative. | Client lỗi/giả mạo có thể tạo acceptance không gắn với line đã thêm; analytics P0 sai và khó đối soát. | Trước implementation, chốt một cơ chế authoritative: Order line-added attribution event hoặc internal verification endpoint; client evidence chỉ là hint và không đủ để ghi accepted. |
| 2 | Order amendment | Order add-line attribution được mô tả ở dạng JSON nhưng chưa có method/path, response schema và event/source-of-truth cụ thể. | Các team có thể triển khai khác nhau, làm duplicate/mất `SuggestionAccepted`. | Bổ sung contract Order đầy đủ, producer fixture và quy tắc dedupe; Recommendation phải converge mọi đường ghi về một source key. |
| 3 | Permission naming | Rule list dùng `rule:draft/read`, trong khi authorization matrix chưa định nghĩa permission `rule:read` độc lập; scope tenant của Owner/Manager chưa ghi rõ cho list và draft. | Generated auth policy có thể không nhất quán hoặc cấp quyền quá rộng. | Tách `rule:read` khỏi `rule:draft`, ghi rõ tenant-wide rule scope và branch-independent behavior trước khi sinh policy. |
| 4 | Machine-readable completeness | Nhiều response/event mới mô tả bằng bảng hoặc payload minimum (`RulePage`, `ActiveModelResponse`, event payloads, rebuild/cold-start responses), chưa đủ schema property, required/nullability và examples để generate artifact trực tiếp. | OpenAPI/AsyncAPI sinh ra có thể lệch giữa service và consumer. | Tạo OpenAPI 3.1, AsyncAPI và JSON Schema canonical từ các bảng này; thêm examples, compatibility rules và producer/consumer fixtures trước code. |
| 5 | Event/source alignment | Contracts yêu cầu `CatalogItemCreated/Updated`, `CatalogItemAvailabilityChanged`, `OrderPaid` và Loyalty feature context nhưng chưa chốt đầy đủ version/source-version semantics của từng producer parent. | Projection checkpoint/replay có thể không monotonic hoặc không tái dựng được sau gap. | Bổ sung schema references/amendments trong Catalog, Inventory, Order và Loyalty; mỗi event phải có event ID, aggregate/source version, occurredAt và tenant/branch scope. |
| 6 | OrderPaid personalization join | `paid_interaction_projections` cần `opaque_customer_key_hash`, trong khi parent `OrderPaid.v1` hiện chỉ đảm bảo `customerId` và chưa quy định opaque feature-key handoff. | ALS có thể bỏ qua giao dịch có khách hoặc join bằng identity không được phép. | Chọn một join contract rõ ràng: Order phát opaque customer reference đã được Loyalty authorize, hoặc Recommendation nhận versioned Loyalty projection keyed bằng stable customer reference; không join bằng phone. |
| 7 | ML governance/privacy | Retention, deletion/tombstone và minimum ALS signal có nêu nguyên tắc nhưng chưa có policy value/config schema; model quality gate chưa có threshold policy fixture bắt buộc. | Không tái lập được quyết định activate/rollback và có rủi ro giữ dữ liệu khách quá lâu. | Đưa retention, sparse threshold, metric thresholds, consent/deletion behavior vào versioned policy contract và lưu policy version cùng snapshot/model. |
| 8 | DeepSeek operations | P2 có job, provider model và prompt version nhưng chưa định nghĩa structured input/output schema, budget/timeout, egress policy, safety validation và retry/DLQ limits đầy đủ. | Cold-start có thể tạo tag không an toàn, tốn chi phí hoặc phụ thuộc provider trong realtime path. | Giữ job async; bổ sung sanitized schema, schema validation, timeout/budget, bounded retry/DLQ, human review và feature-flagged publication. |

## Cross-Impact Analysis

| Dimension | Finding | Risk Level | Recommendation |
|---|---|---|---|
| **Shared Logic** | Recommendation là service mới, không có implementation call sites; các consumer chính là POS, Order, Reporting/Audit và ML workers. | 🟡 Medium | Dùng generated clients/schema fixtures; không để frontend tự định nghĩa acceptance semantics. |
| **Data/State** | Rule versions, eligibility projections, suggestion sessions, paid interactions, model registry và cold-start candidates là các state mới. | 🟡 Medium | Enforce tenant predicates, immutable published versions, monotonic checkpoints, CAS active pointer và outbox/inbox dedupe trong migration/test fixtures. |
| **API Contracts** | Thêm shown/accepted, rule lifecycle, model lifecycle, Order context/add-line attribution và Loyalty feature context. | 🔴 High | Chốt Order authoritative evidence và tách permission `rule:read` trước generated contracts. |
| **Flow Integrity** | P0 recommendation → render → add-line → accepted; P1 ALS training/activation; P2 DeepSeek review đã có happy/error paths. | 🟡 Medium | Bổ sung reconciliation cho add-line success nhưng accepted request thất bại và replay từ source event. |
| **Cross-Cutting** | Multi-tenant/auth, PII boundary, model fallback, stale projection và provider isolation đã được nêu. | 🟡 Medium | Thêm negative contract tests cho IDOR/tenant mismatch, no-phone logs, stale fallback và provider timeout/budget. |

## Instruction Compliance

| Dimension | Verdict | Deviation (if any) | Recommendation |
|---|---|---|---|
| **Conventions** | ✅ | REST/OpenAPI 3.1, AsyncAPI/JSON Schema, UUID v7, UTC, envelopes, cursor pagination và parent event conventions được giữ. | — |
| **Architecture** | ✅ | Boundary Recommendation không sở hữu Catalog/Inventory/Order/Loyalty source of truth; Order add-line vẫn là authority cuối. | Giữ các parent amendments là prerequisite, không tạo shared DB. |
| **Testing** | ⚠️ | Contract test matrix đã có nhưng machine-readable fixtures và authoritative accepted-evidence tests chưa tồn tại. | Viết fixtures/property tests/replay tests trước controller, worker và migration implementation. |

## Verdict

Contracts **có thể được duyệt với warnings**. Không có blocker về scope hoặc hướng kiến trúc. Trước implementation cần chốt 4 điểm có ảnh hưởng trực tiếp đến correctness: Order accepted evidence, permission scope, complete machine-readable schemas và Order/Loyalty personalization join. Các warnings còn lại phải được chuyển thành policy/config/fixture cụ thể trong bước chuẩn bị implementation.

**Required user decision:** Xác nhận tiếp tục với Contracts ở trạng thái **⚠️ Warnings (Proceed with Caution)** để chuyển sang Design Finalization.

