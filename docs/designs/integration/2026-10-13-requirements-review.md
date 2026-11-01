# Spec Sentinel Report — Integration & Demo Orchestration Requirements

**Spec:** [`2026-10-13-requirements.md`](./2026-10-13-requirements.md)  
**Date:** 2026-10-13  
**Status:** ⚠️ Warnings (Proceed with Caution)

## Review scope

- Tái sử dụng Vision đã được duyệt với warnings và các parent design contracts của POS Platform, Order, Payment, Inventory, Loyalty, Recommendation, Reporting, AI Assistant và Audit.
- Kiểm tra requirements về invoice/delivery state, demo run/fault injection, event/API/data ownership, tenant isolation, privacy, recovery và acceptance matrix.
- Repository chưa có application source, generated OpenAPI/AsyncAPI hoặc migration source; đây là requirements review, không phải code review.
- Đã kiểm tra `.agent/rules/security.md`, `.agent/rules/code-quality.md` và các guidance liên quan.

## Findings

### Warnings

| # | Category | Finding | Risk | Recommendation |
|---|---|---|---|---|
| 1 | OrderPaid amendment | Requirements cần `invoiceRecipientRef` và invoice profile trong checkout/`OrderPaid.v1`, nhưng parent Order event payload hiện chưa có field này và chưa chốt source/aggregate version, attempt binding, nullability hay redaction. ([requirements.md:95-106](./2026-10-13-requirements.md#L95)) | Integration không biết recipient hợp lệ nào thuộc paid attempt; producer/consumer có thể phát schema khác nhau hoặc làm lộ contact. | Architecture phải chốt additive Order amendment, schema version, reference ownership, event fixture và backward-compatible missing-ref behavior. |
| 2 | Compensation event | Requirements tham chiếu `OrderCheckoutCompensated.v1` hoặc parent equivalent, nhưng parent Order hiện chủ yếu có `OrderCheckoutFailed.v1`; chưa có canonical release evidence cho demo. ([requirements.md:410-422](./2026-10-13-requirements.md#L410)) | Demo có thể assert sai Inventory release hoặc phụ thuộc internal DB/state không được phép đọc. | Chọn một canonical source: additive compensation event, signed internal checkpoint API hoặc Inventory release event; không tạo Integration-side compensation truth. |
| 3 | Invoice money/profile | Technical invoice snapshot yêu cầu line/totals nhưng chưa nêu rõ tax/VAT/discount/rounding/profile fields, valid-after-paid snapshot và behavior khi OrderPaid payload thiếu field. ([requirements.md:108-121](./2026-10-13-requirements.md#L108)) | Sandbox invoice không reproducible hoặc về sau không nâng cấp được sang legal provider; số tiền có thể không reconcile với Order. | Chốt `InvoiceProfile`, VND minor units, allocation/rounding version, VAT treatment và reject/quality status cho thiếu snapshot. |
| 4 | Delivery provider semantics | State/retry/expiry/idempotency đã có, nhưng callback support, provider message identity, duplicate notification policy, `Retry-After`, manual resend rate limit và channel capability chưa thành contract cụ thể. ([requirements.md:123-134](./2026-10-13-requirements.md#L123)) | Worker có thể gửi trùng hoặc retry vô hạn; UI không phân biệt provider accepted với recipient delivered. | Architecture/Contracts phải chốt per-channel adapter interface, state transition, provider idempotency key, callback dedupe và retry budget. |
| 5 | Consent/PII lifecycle | Requirements đã yêu cầu encrypted/tokenized snapshot, nhưng chưa chốt encryption boundary, contact lookup/token rotation, consent revocation timing, retention/legal hold và manual resend evidence. ([requirements.md:95-106](./2026-10-13-requirements.md#L95)) | Không chứng minh được consent hoặc giữ email/Zalo identifier quá lâu; key rotation có thể làm mất khả năng retry. | Tạo data-class matrix, KMS/key version, consent snapshot schema, revocation/expiry policy và redacted Audit event contract. |
| 6 | Demo seed/reset ownership | Demo runner có thể seed qua approved APIs hoặc dedicated adapter, nhưng chưa phân biệt resource ownership/cleanup semantics của Order, Catalog, Loyalty, Inventory, Recommendation, Reporting và Shift. ([requirements.md:136-149](./2026-10-13-requirements.md#L136)) | Reset có thể xóa/mutate resource ngoài namespace hoặc để lại points/stock/events làm nhiễu các run sau. | Architecture phải chốt demo namespace/tenant, per-service seed/reset adapters, resource manifest, TTL/lease, cleanup order và no-delete production guard. |
| 7 | Fault injection mechanism | `PAYMENT_UNAVAILABLE` có lease và environment guard, nhưng chưa chọn inject tại service deployment, network proxy, provider adapter hay Order task; chưa nêu expected rollback when injection expires. ([requirements.md:140-149](./2026-10-13-requirements.md#L140)) | Fault injection có thể không chứng minh đúng Saga path hoặc gây outage ngoài run scope. | Chọn một controlled injection point, require run/step lease, auto-expiry, audit, kill switch và fixture chứng minh Inventory release/no paid side effect. |
| 8 | AI attribution authority | Requirements yêu cầu `SuggestionShown → SuggestionAccepted → add-line → OrderPaid`, nhưng Recommendation contract cảnh báo `SuggestionAccepted` cần Order-authoritative add-line evidence; chưa có canonical event/verification path trong Module 12. ([requirements.md:151-159](./2026-10-13-requirements.md#L151)) | AI có thể trả uplift từ accepted nhưng chưa thực sự thêm món hoặc chưa thanh toán. | Reporting/Recommendation/Order phải publish one authoritative attribution contract; Integration chỉ dùng checkpoint/read-only facts, không tự join raw events. |
| 9 | Public API machine-readiness | Endpoint catalogue chưa mô tả request/response fields, `Idempotency-Key`, `If-Match`, cursor/filter bounds, safe masking, ETag/cache policy, signed trace references và callback paths. ([requirements.md:212-257](./2026-10-13-requirements.md#L212)) | Clients có thể retry sai, cache scope sai hoặc leak contact/provider reference. | Materialize OpenAPI 3.1 with common envelopes, header precedence, scope binding, no-cache rules for contact/status and idempotent mutation responses. |
| 10 | Event naming/versioning | Produced events và common envelope đã có, nhưng chưa chốt topic/routing, event source registry, `eventVersion` vs schema version, source watermark và whether `InvoiceIssued` là technical vs legal capability in every consumer. ([requirements.md:322-356](./2026-10-13-requirements.md#L322)) | Consumer có thể coi sandbox `ISSUED` là legal invoice hoặc replay không convergence. | Tạo AsyncAPI/event registry, capability/profile field, compatibility rules, route/dedupe fixtures và explicit technical-status semantics. |
| 11 | Retention/partitioning | Requirements yêu cầu retention/legal hold nhưng chưa có minimum durations/data-class matrix cho encrypted contact, invoice artifacts, provider attempts, demo traces, consent and Audit reference. ([requirements.md:259-319](./2026-10-13-requirements.md#L259)) | Xóa nhầm lineage hoặc giữ PII/provider payload quá thời hạn. | Architecture/Contracts chốt retention, legal hold precedence, artifact storage, tombstone/redaction behavior and partition/archive operation. |
| 12 | Demo dependency truth | P0 demo yêu cầu Loyalty recognition, Recommendation personalization, VNPAY sandbox/IPN, Inventory commit/release và AI answer, nhưng không chốt health/freshness/seed readiness contract của từng parent. ([requirements.md:136-159](./2026-10-13-requirements.md#L136)) | Demo false PASS hoặc fail không phân biệt domain bug, stale projection, provider lag và infrastructure outage. | Tạo preflight capability matrix, checkpoint expected state/data quality, failure classification và per-service test fixtures. |

## Cross-Impact Analysis

| Dimension | Finding | Risk Level | Recommendation |
|---|---|---|---|
| **Shared Logic** | `OrderPaid.v1`, invoice recipient reference, Recommendation attribution, Inventory release evidence, VNPAY payment states, Loyalty accrual and Reporting/AI metric functions are consumed by Integration and parent services. | 🔴 High | Publish one parent event registry and do not duplicate business calculations or state transitions inside Integration. |
| **Data/State** | New state: recipient/consent snapshots, invoice/delivery operations, provider attempts, demo runs/checkpoints/fault leases, inbox/outbox/watermarks. | 🔴 High | Separate database, tenant/branch constraints, immutable attempts, CAS, encryption/key versions, data-class retention and cleanup manifest. |
| **API Contracts** | Staff invoice APIs, internal demo/fault APIs, provider adapters and AI/Reporting attribution contracts add multiple trust boundaries. | 🔴 High | Generate OpenAPI/AsyncAPI/JSON Schemas; test headers, IDOR, environment guard, idempotency, callback auth and safe responses. |
| **Flow Integrity** | Recipient capture → checkout context → OrderPaid → invoice issue → delivery; demo success; Payment outage compensation; attribution → AI answer. | 🔴 High | Add L1/L2 sequence diagrams for happy/failure/replay/reset and require checkpoint evidence from authoritative source. |
| **Cross-Cutting** | PII/consent, provider secrets, trace references, rate limits, DLQ, legal-status labeling and non-prod fault injection affect every path. | 🔴 High | Security/privacy corpus, redaction middleware, environment policy, provider runbooks, freshness/status metadata and audit references. |

## Instruction Compliance

| Dimension | Verdict | Deviation (if any) | Recommendation |
|---|---|---|---|
| **Conventions** | ✅ | UUID v7, UTC, VND minor units, EventBridge/SQS, outbox/inbox, Cognito scope and bounded async APIs align parent designs. | — |
| **Architecture** | ✅ | Requirements preserve service ownership, keep payment IPN/Order Saga outside Integration and forbid cross-database queries. | Resolve compensation-event source and demo seed/reset ownership before Architecture approval. |
| **Testing** | ⚠️ | Matrix covers happy/failure/replay/security/performance, but executable parent fixtures, provider callback vectors, consent corpus, demo cleanup and attribution golden metrics are not materialized. | Add contract/security/replay/load/AI metric fixtures before implementation planning. |

## Verdict

Requirements **có thể được duyệt với warnings**. Không có blocker về scope hoặc security boundary; các điểm còn lại là contract/architecture decisions cần chốt trước code: `OrderPaid` amendment, compensation evidence, invoice money/profile, channel semantics, consent lifecycle, demo seed/fault mechanism, authoritative attribution, machine-readable APIs và retention matrix.

**Required user decision:** Bạn duyệt Requirements với warnings để chuyển sang Architecture chứ?
