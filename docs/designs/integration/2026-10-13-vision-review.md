# Spec Sentinel Report — Integration & Demo Orchestration Vision

**Spec:** [`2026-10-13-vision.md`](./2026-10-13-vision.md)  
**Date:** 2026-10-13  
**Status:** ⚠️ Warnings (Proceed with Caution)

## Review scope

- Phase 0 đã được skip — tái sử dụng context của các Module 1–11 và các parent contracts đã thiết kế.
- Đối chiếu Vision với POS Platform, Order, Payment, Inventory, Loyalty, Recommendation, Reporting và Audit contracts.
- Repository chưa có application source; review này kiểm tra integration boundary, cross-service flow, ownership, security/privacy và demo safety ở mức design.
- Đã kiểm tra `.agent/rules/security.md`, `.agent/rules/code-quality.md` và các domain guidance `api-design`, `database-design`, `log-processing`.

## Findings

### Warnings

| # | Category | Finding | Risk | Recommendation |
|---|---|---|---|---|
| 1 | Scope/boundary | Vision đặt invoice integration và demo runner trong cùng Module 12; đã nói rõ ownership nhưng chưa tách rõ production Integration runtime với non-production Demo Control Plane/deployment boundary. ([vision.md:14-40](./2026-10-13-vision.md#L14)) | Demo-only seed/reset/fault injection có thể bị deploy hoặc cấp quyền nhầm vào production. | Trong Requirements/Architecture tách logical components, IAM roles, environment policy và deployment boundary; production Integration không expose fault-injection routes. |
| 2 | Event source of truth | `OrderPaid.v1` được chọn làm canonical trigger, nhưng các parent contracts có thể còn khác nhau về fields, source version, checkout attempt, branch/business date và event delivery guarantee. ([vision.md:18-25](./2026-10-13-vision.md#L18)) | Duplicate/missing invoice hoặc invoice gắn sai order attempt khi replay/late event. | Chốt additive `OrderPaid.v1` amendment, stable source key và consumer fixture trước Contracts. |
| 3 | Invoice legal/provider boundary | Vision nói sandbox/technical integration và loại trừ tax production, nhưng chưa chỉ rõ minimum invoice payload, artifact classification hoặc cách ngăn consumer hiểu `InvoiceIssued` là legally valid invoice. ([vision.md:18-25](./2026-10-13-vision.md#L18)) | Provider migration hoặc reporting có thể dùng sandbox reference như chứng từ pháp lý. | Đặt capability/profile `SANDBOX_TECHNICAL`, explicit status/metadata, safe naming và contract guard để production legal capability là một version/decision riêng. |
| 4 | Contact consent/privacy | Email/Zalo yêu cầu khách chọn channel và consent, nhưng source-of-truth consent, retention, correction/revocation và provider-specific identifier policy chưa chốt. ([vision.md:53-62](./2026-10-13-vision.md#L53)) | Gửi nhầm, giữ PII quá lâu hoặc không chứng minh được consent khi retry/manual resend. | Chốt consent snapshot tại invoice request, masked contact reference, retention/legal hold và revocation behavior; không lấy tự động từ Loyalty phone. |
| 5 | Delivery semantics | Async delivery đã có retry/DLQ/expiry, nhưng chưa chốt provider idempotency key, at-least-once behavior, duplicate notification policy, manual resend và channel precedence. ([vision.md:64-78](./2026-10-13-vision.md#L64)) | Khách nhận trùng hoặc không có cách xử lý delivery stuck/terminal failure. | Tạo delivery state machine, per-channel attempt contract, bounded retry/backoff và operator-safe resend command. |
| 6 | Demo determinism | Demo có seed/reset/checkpoint/fault injection/X-Ray, nhưng chưa có isolation model cho concurrent runs, cleanup/TTL, service health gates và expected state timeout. ([vision.md:80-90](./2026-10-13-vision.md#L80)) | Demo không reproduce được hoặc run này làm nhiễu dữ liệu/trace của run khác. | Version scenario/seed, unique demo tenant/run namespace, lease/TTL, idempotent reset, bounded timeout và cleanup runbook. |
| 7 | Fault injection safety | `PAYMENT_UNAVAILABLE` được giới hạn non-production, nhưng chưa có authorization, allowlist, kill switch, audit event và fail-safe behavior khi environment metadata không chắc chắn. ([vision.md:136-163](./2026-10-13-vision.md#L136)) | Có thể gây outage thật hoặc lạm dụng để phá checkout trong môi trường dùng chung. | Fail closed nếu environment không phải demo; workload-only permission, explicit scenario allowlist, time-bound lease, audit và emergency disable. |
| 8 | AI attribution | Vision nêu lineage `Shown → Accepted → Added → Paid`, nhưng chưa chốt metric “tăng doanh thu”, cohort/control, attribution window, currency/timezone và handling khi event thiếu/trễ. ([vision.md:92-100](./2026-10-13-vision.md#L92)) | AI trả lời thuyết phục nhưng không reproducible hoặc nhầm accepted với revenue. | Reporting/Recommendation phải publish metric dictionary và function contract; AI chỉ đọc approved aggregate với freshness/quality metadata. |
| 9 | Retention and provider payload | Vision đề cập retention/legal hold ở open questions nhưng chưa nêu data-class matrix cho invoice artifact, contact snapshot, delivery attempts, provider response và demo trace references. ([vision.md:199-207](./2026-10-13-vision.md#L199)) | Giữ raw provider/PII quá lâu hoặc xóa mất forensic reference. | Requirements chốt data classes, redaction, encrypted artifact storage, retention/hold precedence và export/access audit. |

## Cross-Impact Analysis

| Dimension | Finding | Risk Level | Recommendation |
|---|---|---|---|
| **Shared Logic** | `OrderPaid.v1`, `SuggestionShown/Accepted`, Order add-line attribution, VNPAY payment state, Inventory reservation/commit/release, Loyalty accrual và Reporting uplift là shared facts của nhiều consumers. | 🔴 High | Dùng parent event registry và additive amendments; Integration không tạo bản sao business truth. |
| **Data/State** | Invoice operations, provider attempts, delivery operations, consent snapshot, demo runs/checkpoints và inbox/outbox là state mới; source domain state vẫn ở parent services. | 🔴 High | Database riêng, tenant predicates, immutable source references, idempotency/CAS và retention matrix. |
| **API Contracts** | Invoice status/delivery/resend, demo run/seed/reset/fault/trace và health APIs sẽ tác động POS, Owner/Manager, Ops và provider adapters. | 🔴 High | API-first OpenAPI + internal workload auth; production/demo routes tách biệt và scope-check từng resource. |
| **Flow Integrity** | Paid event → invoice/delivery; cart → suggestion → add → paid; Payment outage → Saga compensation → Inventory release; paid facts → Reporting/AI. | 🔴 High | Tạo L1/L2 sequence flows và failure fixtures cho duplicate, late, timeout, DLQ, provider failure và reset race. |
| **Cross-Cutting** | PII consent, secrets, X-Ray trace, rate limits, provider outages, fault injection và metric correctness xuyên tất cả module. | 🔴 High | Security/privacy test corpus, redaction, audit, environment policy, freshness metadata và provider runbooks trước code. |

## Instruction Compliance

| Dimension | Verdict | Deviation (if any) | Recommendation |
|---|---|---|---|
| **Conventions** | ✅ | EventBridge/SQS, outbox/inbox, UUID/correlation, service-owned database và Cognito/RBAC khớp parent design. | — |
| **Architecture** | ✅ | Integration không sở hữu domain state, không query chéo DB, không thay thế Order Saga và không biến `SuggestionAccepted` thành revenue truth. | Giữ parent ownership và tách Demo Control Plane khi triển khai. |
| **Testing** | ⚠️ | Vision có success criteria/failure intent nhưng chưa có executable provider, consent, demo isolation và attribution fixtures. | Requirements phải chuyển từng tiêu chí thành acceptance matrix và contract fixtures. |

## Verdict

Vision **có thể được duyệt với warnings**. Không có blocker về ownership hoặc safety boundary vì đã chốt Integration service riêng, async provider path, sandbox-only invoice và non-production fault injection. Các warnings phải được chốt ở Requirements/Architecture, ưu tiên: tách demo control plane, canonical `OrderPaid.v1`, consent/retention, delivery idempotency, fault-injection authorization và AI uplift metric contract.

**Required user decision:** Bạn duyệt Vision với warnings để chuyển sang Requirements chứ?
