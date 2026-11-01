Started at: 2026/10/13 00:00:00
Finished at: 2026/10/13 00:00:00
Total time: 0 minutes
---

# Spec Sentinel Report — Audit & Loss Prevention Vision

**Spec:** [`2026-10-13-vision.md`](./2026-10-13-vision.md)  
**Date:** 2026-10-13  
**Status:** ⚠️ Warnings (Proceed with Caution)

## Review scope

- Phase 0 skipped — tái sử dụng scope, parent architecture và decisions đã được xác nhận trong phiên thiết kế hiện tại.
- Đối chiếu Vision với parent POS Platform, Identity Access, Order, Payment, Shift, Recommendation và Reporting.
- Repository chưa có application source, generated event schemas, migrations hoặc Audit implementation; review này kiểm tra design boundary và cross-service readiness, không phải code review.
- Đã kiểm tra các rule hiện có trong `.agent/rules/security.md` và `.agent/rules/code-quality.md`.

## Findings

### Warnings

| # | Category | Finding | Risk | Recommendation |
|---|---|---|---|---|
| 1 | Parent contract alignment | Parent Platform đã có `audit_events` và `AuditRecorded.v1`, nhưng schema hiện mới có action/resource/actor/reason/before-after/correlation; chưa bao phủ event version/source event, effective operator, device, shift, outcome, redaction policy và integrity evidence mà Vision yêu cầu. | Các producer có thể phát payload không đủ để điều tra hủy món/giảm giá/mở két hoặc không dedupe/replay được. | Requirements phải mở rộng parent event envelope/schema theo additive contract; định nghĩa producer ownership, source event ID/version và bắt buộc field theo từng action. |
| 2 | Action source ambiguity | Vision chưa chốt service phát event mở két, cũng chưa phân biệt manual/automatic trigger; discount/override chưa phân biệt promotion tự động với manual discount và approval threshold. | Audit trail có thể thiếu event hoặc gán trách nhiệm sai; anomaly baseline dùng tín hiệu không đồng nhất. | Chốt action taxonomy và producer/source-of-truth cho từng P0 action; ghi rõ event sau commit, trigger type, amount allocation, reason và outcome. |
| 3 | Integrity model | Vision nêu tamper-evident/hash/chain nhưng để mở giữa nhiều lựa chọn. | Implementation có thể dùng “append-only” ở application level nhưng vẫn khó phát hiện sửa DB/backfill hoặc replay sai lineage. | Requirements phải chọn integrity level: DB append-only controls + immutable digest/checkpoint tối thiểu; nếu cần external anchoring thì ghi rõ owner, cadence và recovery behavior. |
| 4 | Authorization catalogue | Owner/Manager visibility đã rõ, nhưng parent Identity chưa có permission names cho audit read, case mutation và restricted replay/legal-hold operations. | API policy, cache scope và case references có thể cấp quá rộng hoặc không fail-closed khi membership bị revoke. | Bổ sung permission catalogue, tenant/branch scope, subject/effective-operator distinction và authorization version/revocation fixtures. |
| 5 | Anomaly readiness | Rule-based + baseline đã chọn, nhưng chưa có rule catalog, warm-up/minimum sample, threshold, severity, suppression/cooldown hoặc baseline khi nhân viên mới/chuyển branch. | False positive cao hoặc bỏ sót hành vi; Manager khó giải thích tại sao case được tạo. | Requirements phải chốt versioned rule definitions, evidence window, minimum sample, cold-start policy, severity và deterministic finding idempotency. |
| 6 | Case lifecycle and evidence | Vision có finding states và acknowledge/resolve/dismiss nhưng chưa định nghĩa ai được chuyển state, reason bắt buộc, reopen, assignment hay evidence snapshot khi source event về muộn. | Case có thể bị đóng không có trách nhiệm, mất lịch sử điều tra hoặc thay đổi kết quả sau khi evidence đã dùng. | Thiết kế state machine append-only cho case actions; mọi transition tạo audit event, giữ evidence/version snapshot và hỗ trợ late-fact revision rõ ràng. |
| 7 | Retention/privacy boundary | Minimum 7 năm và legal hold đã chọn, nhưng chưa có data-class matrix cho event, actor snapshot, case evidence, raw source reference, archive và deletion/tombstone. | Có thể giữ PII quá mức hoặc xóa nhầm evidence đang bị legal hold. | Chốt retention matrix, field-level redaction, legal-hold precedence, archive encryption, deletion exclusion và audit cho mọi retention operation. |
| 8 | Query/freshness semantics | UI mượn `FRESH/STALE/PARTIAL/UNAVAILABLE/NO_DATA` từ Reporting nhưng chưa có lag target, max query range, export/pagination/rate limit hay behavior khi một producer source bị thiếu. | Investigator có thể hiểu “không có event” là “không xảy ra”, hoặc query 7 năm gây tải không kiểm soát. | Requirements phải định nghĩa query SLO, bounded filters, cursor/export policy, source completeness watermark và banner/alert khi dữ liệu chưa đầy đủ. |

## Cross-Impact Analysis

| Dimension | Finding | Risk Level | Recommendation |
|---|---|---|---|
| **Shared Logic** | Boundary dùng chung là parent event envelope, `AuditRecorded.v1`, action taxonomy, actor/effective operator context, correlation ID và permission scope; producers gồm Order, Payment, Shift, Identity/POS Device, Recommendation và Reporting. | 🔴 High | Dùng một canonical Audit schema/registry; không để mỗi service tự định nghĩa audit payload riêng. |
| **Data/State** | Audit append-only events, query projection, inbox/dedupe, integrity checkpoints, anomaly rules, findings/case transitions, retention/legal hold là state mới. | 🟡 Medium | Thiết kế tenant predicates, immutable event rows, unique source keys, append-only case actions, time partitions và field-level comments trước migration. |
| **API Contracts** | Sẽ thêm audit timeline/search, finding/case query và case transition/replay/legal-hold APIs; parent Identity chưa có permission catalogue tương ứng. | 🔴 High | Chốt API-first REST/OpenAPI và authorization/error/pagination contracts trước frontend hoặc worker. |
| **Flow Integrity** | Source mutation → transactional outbox → EventBridge/SQS → Audit inbox → immutable store/projection → anomaly evaluation → case → human transition. | 🟡 Medium | Viết sequence/acceptance fixtures cho source commit, duplicate/out-of-order, DLQ/replay, late event, source outage và case resolution. |
| **Cross-Cutting** | Tenant isolation, PII/secret redaction, seven-year retention, tamper evidence, query scale, false positives và legal hold đều ảnh hưởng toàn hệ thống. | 🔴 High | Thiết kế security/privacy/retention policy cùng contract; audit access/replay/legal-hold operations phải fail closed và tự được audit. |

## Instruction Compliance

| Dimension | Verdict | Deviation (if any) | Recommendation |
|---|---|---|---|
| **Conventions** | ✅ | Vision tuân theo database-per-service, EventBridge/SQS, outbox/inbox, Cognito RBAC, correlation/tracing và không query chéo database. | — |
| **Architecture** | ✅ | Audit giữ source state ở Order/Payment/Shift/Identity, dùng Audit service làm evidence owner và không tự động mutate nghiệp vụ. | Giữ Audit là read/evidence boundary; mọi case mutation phải là audited command có authorization. |
| **Testing** | ⚠️ | Vision đã nêu no-loss, IDOR, privacy, replay, anomaly explainability và retention goals nhưng chưa có fixture/state-transition matrix. | Chuyển các mục tiêu thành requirements và contract tests cho event completeness, integrity, case lifecycle, cold-start baseline và legal hold. |

## Verdict

Vision **có thể tiếp tục với warnings**. Không có blocker về phạm vi hoặc hướng kiến trúc; các điểm còn lại là contract/policy cần được chốt trong Requirements. Ưu tiên cao nhất là parent Audit event amendment, action producer ownership, Identity permission catalogue, integrity level và anomaly/case semantics.

**Required user decision:** Bạn muốn tiếp tục sang Requirements với trạng thái **⚠️ Warnings (Proceed with Caution)** hay chỉnh Vision trước?
