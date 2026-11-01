Started at: 2026/10/13 00:00:00
Finished at: 2026/10/13 00:00:00
Total time: 0 minutes
---

# Spec Sentinel Report — Audit & Loss Prevention Requirements

**Spec:** [`2026-10-13-requirements.md`](./2026-10-13-requirements.md)  
**Date:** 2026-10-13  
**Status:** ⚠️ Warnings (Proceed with Caution)

## Review scope

- Tái sử dụng Vision, Vision review và các quyết định Requirements đã được xác nhận trong phiên hiện tại.
- Đối chiếu Requirements với parent POS Platform, Identity Access, Order, Payment, Shift, Recommendation và Reporting contracts.
- Repository chưa có application source, generated API/event schemas hoặc migrations; review này kiểm tra requirements/design contracts, không phải code review.
- Đã kiểm tra `.agent/rules/security.md` và `.agent/rules/code-quality.md`.

## Findings

### Warnings

| # | Category | Finding | Risk | Recommendation |
|---|---|---|---|---|
| 1 | Event taxonomy | Requirements vừa dùng action-specific events (`OrderLineCancelled`, `DiscountApplied`, `CashDrawerOpened`) vừa giữ parent `AuditRecorded.v1`, nhưng chưa chọn source-of-truth cho normalized audit fact và quy tắc không tạo hai evidence rows. | Một source mutation có thể bị ghi hai lần hoặc producer/consumer hiểu khác nhau về event nào là canonical. | Trước Architecture, chọn mô hình canonical: source business events được Audit normalize, hoặc producer phát một `AuditRecorded.v1` duy nhất; ghi `sourceEventId/sourceVersion` và dedupe key trong schema. |
| 2 | Drawer transaction boundary | `AUD-FR-001` yêu cầu cùng transaction/outbox với drawer state/result, nhưng physical drawer open có thể xảy ra ngoài database transaction. | Có thể audit “đã mở” khi hardware fail, hoặc mất evidence khi command thành công nhưng result callback trễ. | Tách command/request và hardware result semantics; chốt stable operation/result key, outcome precedence, retry và reconciliation cho POS Device/Shift. |
| 3 | Money and allocation | Discount requirements nói before/after amount, allocation và VND parent convention nhưng chưa yêu cầu rõ `currency`, integer minor units, rounding và tổng allocation invariant trong Audit schema. | Discount anomaly có thể sai do đơn vị tiền hoặc allocation không cân bằng; dữ liệu đa currency khó replay. | Bổ sung Money/Allocation schema: `amountMinor`, `currency=VND` MVP, non-negative checks, rounding/version và `allocated + unallocated = discount` invariant. |
| 4 | Integrity canonicalization | Batch digest đã chọn nhưng chưa chốt canonical serialization, sort order, algorithm, batch boundary, checkpoint storage/anchoring và recovery khi mismatch. | Hai worker có thể tạo digest khác nhau hoặc không chứng minh được event nào bị sửa/mất. | Chốt canonical JSON/field order, hash algorithm, ordered event tuple, time/count batch boundary, digest pointer và verify/alert/rebuild protocol. |
| 5 | Anomaly policy | Initial rule families đã rõ nhưng threshold, baseline formula, minimum sample, warm-up, timezone/window, suppression/cooldown và severity mapping vẫn để “policy-defined”. | Hai implementation có thể tạo finding khác nhau; false positive/negative khó kiểm thử và giải thích. | Tạo versioned rule-policy schema với deterministic examples/fixtures cho new employee, branch, shift, zero baseline, spikes, duplicate và late facts. |
| 6 | Parent source coverage | P2 cần Order/Payment/Shift/POS Device facts và promotion allocation, nhưng parent contracts hiện mới thống nhất một phần Audit route/fields; drawer, discount và anomaly source fixtures chưa đủ. | Worker có thể phụ thuộc field không được producer phát hoặc dùng Payment facts sai làm business truth. | Bổ sung additive amendments và producer/consumer fixtures cho từng source; giữ Payment/Order/Shift ownership và không query DB xuyên service. |
| 7 | Case/evidence lineage | Case transition yêu cầu append-only và revision nhưng chưa chốt một state machine đầy đủ, optimistic version, assignment/ownership, evidence snapshot immutability và concurrent transition response. | Manager/Owner có thể ghi đè nhau hoặc case đã resolve bị thay đổi mà không giữ forensic lineage. | Chốt state transition graph, `If-Match`/CAS version, transition event, required reason, evidence snapshot/version and `409` conflict behavior. |
| 8 | Retention/export data classes | Seven-year retention/legal hold và async export đã có, nhưng chưa có data-class matrix cho event, digest, actor snapshots, finding/case, export file, archive và tombstone. | Có thể giữ dữ liệu nhạy cảm quá lâu, xóa nhầm evidence hoặc để file export tồn tại ngoài retention. | Tạo retention/legal-hold matrix, encrypted export lifecycle, deletion exclusion, expiry job idempotency and access audit before migration. |
| 9 | API machine-readiness | Endpoint catalogue có path/permission/purpose nhưng chưa có complete request/response/error schemas, filter allowlist, cursor shape, `ETag`/cache rule và async export download semantics. | Frontend/backend có thể triển khai khác nhau hoặc leak scope qua detail/export/cursor. | Materialize OpenAPI 3.1 with common envelope, bounded filters, authorization-before-cache, cursor binding, async `202`/polling and safe error fixtures. |

## Cross-Impact Analysis

| Dimension | Finding | Risk Level | Recommendation |
|---|---|---|---|
| **Shared Logic** | Shared boundaries gồm `AuditRecorded.v1`, action-specific source facts, actor/effective operator, device/shift context, Identity scope và source lineage; consumers gồm Audit, Loss worker, Reporting/Audit UI và Ops. | 🔴 High | Publish one event registry and producer/consumer fixtures; không duplicate audit mapping trong từng consumer. |
| **Data/State** | Append-only events, query projection, inbox, digest checkpoints, rule policies, baselines, findings, case transitions, export/legal holds and retention jobs là state mới. | 🟡 Medium | Enforce tenant predicates, immutable rows, source uniqueness, CAS state transitions, time partition/archive and column comments in migration. |
| **API Contracts** | Audit timeline/detail, findings/case actions, export, rebuild, health and integrity operations add protected APIs; Identity permission catalogue is parent-dependent. | 🔴 High | Generate OpenAPI after permission/scope amendment; test IDOR, cursor/export scope and concurrent case mutation. |
| **Flow Integrity** | Source commit/outbox → queue → inbox/dedupe → append-only evidence → projection/digest → rule evaluation → finding/case → human action. | 🟡 Medium | Add L1/L2 fixtures for hardware result delay, late correction, digest mismatch, source gap, DLQ replay and case revision. |
| **Cross-Cutting** | Seven-year retention, PII/secrets redaction, tamper evidence, branch isolation, query load and false-positive handling affect every producer and operator path. | 🔴 High | Make redaction/retention/integrity policies executable; audit every export/replay/legal-hold operation and fail closed on scope uncertainty. |

## Instruction Compliance

| Dimension | Verdict | Deviation (if any) | Recommendation |
|---|---|---|---|
| **Conventions** | ✅ | REST/OpenAPI 3.1, AsyncAPI/JSON Schema, UUID v7, UTC, EventBridge/SQS, outbox/inbox, cursor and common error patterns align parent conventions. | — |
| **Architecture** | ✅ | Audit owns evidence/cases, while Order/Payment/Shift/Identity/POS Device retain source state; no cross-database query or autonomous blocking is specified. | Keep source producer contracts additive and preserve service ownership. |
| **Testing** | ⚠️ | Matrix is broad but digest canonicalization, hardware operation semantics, rule fixtures, complete API schemas and retention/export lifecycle are not yet executable. | Materialize contract, replay, security, privacy, integrity, anomaly and retention fixtures before implementation. |

## Verdict

Requirements **có thể được duyệt với warnings**. Không có blocker về scope hoặc safety boundary; các warnings cần được chốt trong Architecture/Contracts trước code. Ưu tiên là canonical event model, drawer operation semantics, Money/allocation schema, digest algorithm, versioned anomaly policy, case state machine và retention/export matrix.

**Required user decision:** Bạn duyệt Requirements với warnings để chuyển sang Architecture chứ?
