# Spec Sentinel Report — Integration & Demo Orchestration Contracts

**Spec:** [`2026-10-13-contracts.md`](./2026-10-13-contracts.md)  
**Date:** 2026-10-13  
**Status:** ⚠️ Warnings (Proceed with Caution)

## Review scope

- Đối chiếu Contracts với Vision, Requirements, Architecture, 8 sequence flows và parent contracts của Order, Payment, Inventory, Loyalty, Recommendation, Reporting, AI Assistant, Audit.
- Kiểm tra REST/internal/callback API, Identity scope, provider ports, event compatibility, PostgreSQL invariants, retention/PII, demo isolation và replay/recovery.
- Repository chưa có application source, generated OpenAPI/AsyncAPI hoặc migrations; đây là contract review, không phải implementation code review.
- Đã áp dụng `.agent/rules/security.md`, `.agent/rules/code-quality.md`, `api-design`, `database-design`, `log-processing` và `sequence-diagram` guidance.

## Findings

### Warnings

| # | Category | Finding | Risk | Recommendation |
|---|---|---|---|---|
| 1 | Parent event compatibility | Contracts vừa dùng `OrderPaid.v1`, vừa nhận compatibility `OrderPaid` + `eventVersion=1`; source registry, topic/routing, canonical schema và migration cutoff chưa là executable artifact. ([contracts.md:19-27](./2026-10-13-contracts.md#L19)) | Hai consumer có thể dedupe/version khác nhau hoặc tạo invoice hai lần trong migration. | Materialize event registry với canonical name, accepted aliases, source/aggregate version, cutoff date và one golden replay fixture. |
| 2 | Recipient reference authority | API tạo `invoiceRecipientRef` nhận `orderId`, `checkoutAttemptId`, `branchId`, nhưng chưa có method/schema cụ thể để xác nhận Order attempt tồn tại, thuộc scope và còn nhận reference trước khi tạo snapshot. ([contracts.md:141-159](./2026-10-13-contracts.md#L141)) | Có thể tạo consent/reference cho foreign/nonexistent/expired attempt hoặc Order không echo ref vào `OrderPaid`. | Chốt Order internal validation/context contract, ownership of attempt binding, expiry and missing-ref behavior before generated clients. |
| 3 | Money/profile schema | Contracts yêu cầu totals/VAT/discount reconcile nhưng `InvoiceProfile`/line allocation/VAT policy/rounding version chưa có full schema; technical sandbox vs future legal capability mới được mô tả bằng profile. ([contracts.md:130-139](./2026-10-13-contracts.md#L130)) | Provider adapter có thể tính/format invoice khác Order hoặc future migration không backward-compatible. | Publish machine-readable profile and money/allocation schema with rounding, VAT, discount, missing-field and capability rules. |
| 4 | Provider unknown/reconciliation | Adapter port có `Unknown` và worker recovery, nhưng chưa có explicit internal reconcile endpoint/operation state, lease/CAS, query budget, callback precedence và operator action contract. ([contracts.md:371-404](./2026-10-13-contracts.md#L371)) | Worker crash sau provider acceptance có thể stuck hoặc gọi provider trùng; Ops không biết cách kết luận an toàn. | Add reconciliation operation API/schema, provider query capability, bounded lease, result precedence, idempotency response and runbook fixture. |
| 5 | Callback contract | Callback paths được nêu nhưng chưa có canonical signature input/canonicalization, timestamp/replay window, status/amount/reference mapping, response code và provider-specific credential rotation schema. ([contracts.md:397-404](./2026-10-13-contracts.md#L397)) | Callback spoofing/replay hoặc mismatch invoice/delivery reference. | Create per-provider callback JSON Schema + signature test vectors, replay cache/unique key and safe provider-compatible response contract. |
| 6 | API concurrency/idempotency | Public retry API có cả `If-Match` và `expectedVersion`, nhưng precedence/header-body conflict và exact idempotent replay response chưa chốt; invoice list/detail cache policy chưa có executable headers. ([contracts.md:277-296](./2026-10-13-contracts.md#L277)) | Concurrent resend có thể tạo duplicate attempt hoặc client retry/ETag behavior khác nhau. | Chọn `If-Match` làm authority hoặc bỏ body version, define conflict precedence, replay response and `Cache-Control/ETag` examples in OpenAPI. |
| 7 | PII encryption contract | `encrypted_contact` cho phép `BYTEA/TEXT ref`, nhưng chưa chốt ciphertext/token reference, KMS key version, lookup/fingerprint, rotation and deletion semantics. ([contracts.md:490-508](./2026-10-13-contracts.md#L490)) | Không thể review key access/rotation hoặc vô tình tạo searchable raw PII column. | Chọn one storage pattern, add encryption metadata/key version, restricted DB role, tokenization boundary, rotation/re-encryption and erasure fixture. |
| 8 | Retention/legal alignment | Integration default giữ safe metadata/consent 2 năm, raw contact 30 ngày, Audit references theo 7 năm; transfer/lineage khi hold hoặc legal invoice production bật chưa có manifest contract. ([contracts.md:593-608](./2026-10-13-contracts.md#L593)) | Có thể xóa contact/reference cần cho dispute hoặc giữ PII không cần thiết; policy parent/legal chưa đồng bộ. | Publish data-class/legal-hold matrix, archive manifest, hold precedence, reference survival and production-profile migration gate. |
| 9 | Demo resource cleanup | Contracts có resource manifest và cleanup indexes nhưng parent seed/reset adapters, resource ownership, deletion capability, order, partial failure and compensation for cleanup are not schemas. ([contracts.md:551-557](./2026-10-13-contracts.md#L551)) | Reset có thể để lại reservation/points/events hoặc xóa nhầm dữ liệu ngoài namespace. | Define per-parent demo adapter contract, resource manifest type/status, reverse cleanup dependency, TTL, retry and no-delete guard fixture. |
| 10 | Fault lease security | Lease schema/endpoint có target/step/TTL nhưng thiếu environment attestation, caller/workload identity, allowlist version, kill-switch precedence and propagation contract into Payment. ([contracts.md:330-344](./2026-10-13-contracts.md#L330)) | Lạm dụng lease hoặc fault scope leak thành Payment outage ngoài run. | Include signed run context, environment attestation, max TTL, lease version, kill switch and Payment adapter verification fixture. |
| 11 | Attribution/AI contract | Integration consumes `SuggestionAccepted` as checkpoint while parent Recommendation review still requires Order-authoritative add-line evidence; AI function/metric schemas are referenced but not owned/materialized here. ([contracts.md:433-443](./2026-10-13-contracts.md#L433)) | Demo/AI có thể coi accepted là paid revenue hoặc sử dụng data quality sai. | Keep Integration read-only; require Reporting/Recommendation/Order-owned attribution/metric registry and golden fixture as parent gate. |
| 12 | Data model completeness | `invoice_items`, attempts, inbox/outbox and demo tables are column summaries; exact nullability, foreign-reference policy, encrypted artifact behavior and all `COMMENT ON COLUMN` content are not yet migrations. ([contracts.md:486-557](./2026-10-13-contracts.md#L486)) | Generated migration/ORM có thể diverge; required uniqueness/partition/retention behavior khó verify. | Materialize Flyway DDL, comments, constraints, roles, indexes and migration/restore tests before code. |

## Cross-Impact Analysis

| Dimension | Finding | Risk Level | Recommendation |
|---|---|---|---|
| **Shared Logic** | `OrderPaid`, invoiceRecipientRef, provider idempotency/callback, parent cleanup, Inventory release checkpoint and AI attribution are shared across Integration and parent services. | 🔴 High | One event/adapter registry; parent-owned business facts remain canonical; add consumer-driven fixtures. |
| **Data/State** | Recipient consent/PII, invoice/delivery attempts, inbox/outbox, demo runs/manifests/fault leases and retention operations are new state. | 🔴 High | Explicit tenant/branch predicates, restricted PII roles, immutable attempts, CAS, archive/hold manifests and comments. |
| **API Contracts** | Public recipient/status/retry, callback, internal demo/fault/health/reconcile APIs cross user, workload and provider trust boundaries. | 🔴 High | Generate OpenAPI 3.1 with auth/scope/header/cache/idempotency examples; generate callback schemas and signature vectors. |
| **Flow Integrity** | Recipient capture → Order checkout context → OrderPaid → invoice → delivery; provider crash/replay; demo success/failure/reset; AI attribution. | 🔴 High | Keep 16 sequence blocks as acceptance fixtures; add reconciliation, reset partial failure and event alias migration paths. |
| **Cross-Cutting** | PII, provider secrets, legal-status labeling, non-prod fault injection, trace redaction, rate limit and retention affect every endpoint/worker. | 🔴 High | Security/privacy corpus, environment policy, key rotation, provider runbook, legal profile gate and audit references. |

## Instruction Compliance

| Dimension | Verdict | Deviation (if any) | Recommendation |
|---|---|---|---|
| **Conventions** | ✅ | REST/OpenAPI direction, UUID v7, UTC, VND minor units, EventBridge/SQS, outbox/inbox, Cognito scope and safe errors align parent designs. | — |
| **Architecture** | ✅ | Dedicated Integration ownership, provider adapters, no cross-DB access, async paid trigger and separate non-prod Demo Control Plane are consistent with Architecture. | Keep reconciliation/checkpoint paths read-only with respect to parent domain state. |
| **Testing** | ⚠️ | Contract test matrix is broad, but generated schemas, provider vectors, parent cleanup adapters, key rotation, retention/legal hold and attribution fixtures are not yet executable. | Materialize listed artifacts before implementation planning/code. |

## Verdict

Contracts **có thể được duyệt với warnings**. Không có blocker về tenant isolation, provider boundary hay no-cross-domain mutation. Các warning cần được giữ như implementation gates: parent `OrderPaid` compatibility, recipient validation, money/profile schema, provider reconciliation/callbacks, API CAS/idempotency, PII encryption, retention, demo cleanup/fault lease và AI attribution registry.

**Required user decision:** Bạn duyệt Contracts với warnings để chuyển sang Finalization chứ?
