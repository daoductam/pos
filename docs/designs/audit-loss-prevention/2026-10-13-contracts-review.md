# Spec Sentinel Report — Audit & Loss Prevention Contracts

**Spec:** [`2026-10-13-contracts.md`](./2026-10-13-contracts.md)  
**Date:** 2026-10-13  
**Status:** ⚠️ Warnings (Proceed with Caution)

## Review scope

- Đối chiếu Contracts với Vision, Requirements, Architecture và tám sequence flows đã được xác nhận.
- Kiểm tra public/internal API, authorization scope, event routing, schemas, PostgreSQL constraints, digest, anomaly policy, case workflow và retention/legal hold.
- Repository chưa có application source, generated OpenAPI/AsyncAPI hoặc migrations; đây là contract review, không phải implementation code review.
- Đã áp dụng `.agent/rules/security.md`, `.agent/rules/code-quality.md` và workflow `design.md`.

## Findings

### Warnings

| # | Category | Finding | Risk | Recommendation |
|---|---|---|---|---|
| 1 | Parent event registry | Contracts đã phân biệt action-specific source events với `AuditRecorded.v1` compatibility event, nhưng registry thực thi và ownership của từng producer vẫn là artifact cần tạo; parent POS Platform hiện chưa mang đầy đủ route/dedupe contract. ([contracts.md:452-520](./2026-10-13-contracts.md#L452)) | Producer có thể phát trùng evidence hoặc dùng sai generic event. | Trước code, materialize `audit-event-registry.yaml`, duyệt parent amendments và thêm golden fixtures cho từng action/dedupe key. |
| 2 | Drawer command/result | `CashDrawerOpened.v1` đã có `operationKey`, command/result timestamps và hardware outcome, nhưng contract chưa mô tả rõ precedence khi command timeout, callback trễ, callback mâu thuẫn hoặc không có result. ([contracts.md:495-512](./2026-10-13-contracts.md#L495)) | Có thể đếm sai số lần mở két hoặc ghi outcome không phản ánh hardware. | Chốt state/result matrix trong POS Device contract: `REQUESTED`, `OPENED`, `FAILED`, `UNKNOWN`, reconciliation timeout và rule chỉ tạo anomaly từ outcome đủ tin cậy. |
| 3 | Money/allocation | Contract đã chốt VND minor units và invariant tổng quát, nhưng `DiscountAllocation` example còn thiếu currency/rounding metadata ở từng field và chưa có fixture cho nhiều line, partial/unallocated hoặc order-level discount. ([contracts.md:158-179](./2026-10-13-contracts.md#L158)) | Promotion/manual override có thể không reconcile hoặc anomaly worker so sánh sai amount. | Bổ sung schema máy đọc được cho money/allocation, rounding policy/version và fixtures tổng allocation theo line/order. |
| 4 | API materialization | Endpoint, quyền, filter, cursor, error taxonomy đã đủ ở mức thiết kế, nhưng OpenAPI chưa được sinh; precedence giữa `If-Match` và `expectedVersion`, ETag/cache behavior và download reference lifecycle chưa có schema executable. ([contracts.md:227-367](./2026-10-13-contracts.md#L227)) | Frontend/backend có thể triển khai khác nhau; cache/export có nguy cơ sai scope hoặc retry sai. | Tạo `openapi.yaml` và contract tests cho headers, cursor binding, CAS/idempotency, `202` polling, signed download và expiry/revocation. |
| 5 | Digest testability | Canonical field allowlist, ordering, `audit-c14n-v1`, SHA-256 và chain đã chốt, nhưng chưa có test vector cụ thể cho null/array/unicode/VND summary, batch boundary và previous digest. ([contracts.md:806-829](./2026-10-13-contracts.md#L806)) | Hai implementation có thể sinh digest khác nhau hoặc không phát hiện mất/sửa record. | Tạo test vector bắt buộc trước migration; cố định byte encoding, length delimiter, batch boundary và expected mismatch behavior. |
| 6 | Anomaly policy governance | Rule schema đã có baseline, minimum sample, warm-up, threshold, severity và suppression, nhưng chưa chỉ rõ actor/workflow có quyền publish/retire rule, hiệu lực theo branch khi policy thay đổi và cách xử lý zero baseline/late event trong executable fixtures. ([contracts.md:831-864](./2026-10-13-contracts.md#L831)) | Rule có thể thay đổi không kiểm soát hoặc tạo finding không tái lập được. | Chốt policy lifecycle/audit, publish permission, effectiveAt, immutable version và deterministic fixtures cho cold start, zero baseline, late facts, duplicate/no-op. |
| 7 | Case mutation semantics | State graph, CAS và quyền Owner/Manager đã rõ, nhưng contract chưa mô tả idempotent response của case mutation khi cùng `Idempotency-Key`, thứ tự ưu tiên giữa `If-Match` và `expectedVersion`, hay lineage khi reopen tạo revision mới. ([contracts.md:317-331](./2026-10-13-contracts.md#L317), [contracts.md:866-886](./2026-10-13-contracts.md#L866)) | Retry có thể tạo duplicate case action hoặc làm mất lịch sử reopen/resolution. | Chốt một nguồn version authority, response replay cho idempotency, transition ID và revision/evidence lineage trong OpenAPI/AsyncAPI fixtures. |
| 8 | Append-only/retention operations | Schema nói application không có update/delete và retention chạy bằng restricted workload, nhưng chưa chỉ rõ DB roles, transaction boundary, partition detach/archive atomicity và cách giữ liên kết khi legal hold chặn một phần partition. ([contracts.md:770-784](./2026-10-13-contracts.md#L770), [contracts.md:888-900](./2026-10-13-contracts.md#L888)) | Có thể phá append-only hoặc xóa nhầm evidence/case lineage khi cleanup. | Thêm migration/role contract, archive manifest, hold evaluation per data class/range và failure/retry fixtures cho retention job. |
| 9 | Parent Identity contract | Permission catalogue, scope hash, membership/authorization version và historical actor snapshot đã được nêu, nhưng Identity amendment vẫn chưa được materialize trong parent contract. ([contracts.md:100-137](./2026-10-13-contracts.md#L100), [contracts.md:958-961](./2026-10-13-contracts.md#L958)) | Revocation/branch-scope behavior có thể khác giữa Identity middleware và Audit repository. | Cập nhật Identity permission/scope contract trước code; test revoked membership, Manager cross-branch, Owner subset và cache invalidation. |

## Cross-Impact Analysis

| Dimension | Finding | Risk Level | Recommendation |
|---|---|---|---|
| **Shared Logic** | Event registry, redaction, actor/effective operator, scope resolution, cursor binding và digest canonicalization được dùng bởi Audit ingest, query API, anomaly worker, export và Reporting/Ops. | 🔴 High | Tạo một registry/schema package; không copy mapping hoặc redaction rules trong từng consumer. |
| **Data/State** | `audit_events`, inbox/watermark, digest batches, findings, case actions, exports, legal holds và retention operations tạo state append-only mới. | 🔴 High | Migration phải có tenant/branch predicates, unique dedupe, CAS, restricted DB roles, partition/archive checks và column comments. |
| **API Contracts** | Timeline/findings/case/export và internal rebuild/integrity endpoints phụ thuộc Identity, Order, Promotion, Payment/Shift/POS Device parent contracts. | 🔴 High | Sinh OpenAPI/AsyncAPI trước implementation; test IDOR, stale scope, cursor, CAS/idempotency và export expiry. |
| **Flow Integrity** | Source commit/outbox → EventBridge/SQS → inbox/dedupe → evidence/projection → digest/rule → finding/case → human action. | 🟡 Medium | Giữ các L1/L2 sequence flows làm acceptance fixtures, bổ sung late drawer result, DLQ replay, digest mismatch và retention hold. |
| **Cross-Cutting** | Bảy năm retention, PII/secret redaction, branch isolation, tamper evidence, query freshness, anomaly false positives và export security ảnh hưởng toàn bộ service. | 🔴 High | Fail closed khi scope/quality không chắc chắn; audit mọi export/replay/hold/case mutation; đo freshness/DLQ/integrity. |

## Instruction Compliance

| Dimension | Verdict | Deviation (if any) | Recommendation |
|---|---|---|---|
| **Conventions** | ✅ | REST/OpenAPI 3.1, AsyncAPI/JSON Schema, UUID v7, UTC, cursor, common error envelope và EventBridge/SQS phù hợp design workflow. | — |
| **Architecture** | ✅ | Audit sở hữu evidence/case/read model; source business services vẫn sở hữu business state; không có cross-database query hoặc automatic employee blocking. | Giữ ownership và parent amendments như Contracts §15. |
| **Testing** | ⚠️ | Contracts đã liệt kê OpenAPI, event registry, migrations, digest vector, golden fixtures, load/security corpus nhưng các artifact này chưa tồn tại. ([contracts.md:963-975](./2026-10-13-contracts.md#L963)) | Không bắt đầu code production trước khi materialize tối thiểu OpenAPI/AsyncAPI, registry, migration constraints, digest vector và security/retention fixtures. |

## Verdict

Contracts **có thể được duyệt với warnings**. Không có blocker về scope, tenant isolation, ownership hay safety boundary; các điểm còn lại là implementation gates cần hoàn tất trước khi code production: parent amendments, machine-readable schemas, drawer result matrix, digest test vector, anomaly policy governance, case idempotency và retention DB-role semantics.

**Required user decision:** Bạn duyệt Contracts với warnings để chuyển sang Finalization chứ?
