Started at: 2026/10/13 00:00:00
Finished at: 2026/10/13 00:00:00
Total time: 0 minutes
---

# Spec Sentinel Report — AI Assistant Vision

**Spec:** [`2026-10-13-vision.md`](./2026-10-13-vision.md)  
**Date:** 2026-10-13  
**Status:** ⚠️ Warnings (Proceed with Caution)

## Review scope

- Reused the approved POS Platform, Identity, Order, Payment and Recommendation designs.
- Scanned the repository for existing AI Assistant, LLM, Reporting, dashboard and query implementation.
- Không tìm thấy application source; parent POS contracts hiện chỉ có Reporting skeleton cho `sales_daily` và `top_items`.

## Findings

### Warnings

| # | Category | Finding | Risk | Recommendation |
|---|---|---|---|---|
| 1 | Reporting boundary | Vision nêu sáu nhóm tool (doanh thu, số đơn, AOV, top items, branch comparison, hourly) nhưng parent Reporting contracts chưa có schema/endpoint cho AOV, hourly và contribution analysis. | Requirements có thể tự tạo nhiều cách tính khác nhau hoặc truy vấn trực tiếp service DB, phá CQRS/boundary. | Requirements phải định nghĩa Reporting tool/read-model contract, owner của metric, business timezone, refund/cancel treatment và freshness watermark. |
| 2 | Business-day completeness | Daily summary phụ thuộc business-day cutoff và completeness watermark nhưng chưa có owner/algorithm khi branch chưa đóng ca, late `OrderPaid` hoặc reconciliation đến sau cutoff. | Summary có thể phát sớm, sai baseline hoặc không reproducible. | Chốt Reporting completeness state, cutoff policy, late-fact correction/re-run và idempotency key trong Requirements/Contracts. |
| 3 | LLM tool loop | Vision nói function calling nhưng chưa chốt maximum tool calls, allowed argument schema, tool timeout, model response state machine và behavior khi tool trả partial result. | Prompt/tool loop có thể chạy vô hạn, tốn chi phí hoặc trả kết quả không kiểm chứng. | Định nghĩa bounded tool loop, JSON schema validation, timeout/budget, refusal/partial-result states và no-tool fallback. |
| 4 | Conversation semantics | Chưa chốt session context, follow-up question, branch scope khi user đổi chi nhánh giữa các lượt và retention/deletion của conversation. | Có thể dùng nhầm scope/kỳ báo cáo từ lượt trước hoặc giữ prompt lâu hơn policy. | Thiết kế conversation/session contract với immutable resolved scope per turn, explicit scope change, redacted storage và TTL. |
| 5 | Provider governance | DeepSeek model/version, egress/region, token budget, provider data-use policy, key rotation và incident handling mới là known unknowns. | Rủi ro compliance, chi phí và khó audit prompt/model drift. | Requirements phải có provider adapter contract, versioned prompt/policy, budget/timeout, secret management, redaction và provider outage runbook. |
| 6 | Explainability boundary | Vision yêu cầu “yếu tố liên quan” từ contribution analysis nhưng chưa chốt công thức contribution, minimum materiality, tie-break và cách hiển thị negative/unknown factors. | LLM có thể diễn đạt đúng số nhưng gây hiểu nhầm về nguyên nhân hoặc làm tròn sai. | Định nghĩa contribution schema, calculation version, rounding, top-N/threshold và wording policy trước summary implementation. |
| 7 | Authorization defense-in-depth | Vision đã nêu server-side scope và tool predicate, nhưng chưa chỉ rõ tool gateway/repository nào là source of authorization decision và cách xử lý Owner cross-branch comparison. | Một tool có thể tin branch input từ LLM/client hoặc cho phép cross-tenant aggregation ngoài ý muốn. | Chốt scope resolver → tool policy → Reporting repository chain; mọi request lưu resolved scope và negative IDOR fixtures. |
| 8 | Dashboard delivery | Chat dashboard, streaming/partial result và P2 summary presentation chưa có UI/API boundary hoặc notification/read-state contract. | Frontend có thể hiển thị partial/uncertain answer như final fact hoặc tạo duplicate summary. | Requirements định nghĩa response states, freshness/uncertainty UI contract, summary read/ack/rerun semantics; giữ notification ngoài MVP nếu chưa cần. |

## Cross-Impact Analysis

| Dimension | Finding | Risk Level | Recommendation |
|---|---|---|---|
| **Shared Logic** | Không có AI Assistant implementation; phụ thuộc Reporting read model và Identity authorization. | 🟡 Medium | Không tạo logic metric trong AI Assistant; reuse Reporting-owned metric definitions/tools. |
| **Data/State** | Conversation, redacted audit, tool execution, provider policy và daily summary state là state mới; Reporting remains source of truth. | 🟡 Medium | Tách ownership, retention và replay boundary; không lưu raw PII/prompt. |
| **API Contracts** | Cần public assistant chat/session, internal Reporting tool, provider adapter và summary read/rerun APIs. | 🔴 High | Chốt schemas, auth, errors, idempotency và response provenance trước Architecture. |
| **Flow Integrity** | Dashboard → scope resolver → LLM tool call → Reporting → validated answer; scheduler → completeness → contribution → summary. | 🟡 Medium | Bổ sung timeout, partial/tool failure, late facts, rerun và stale-data paths. |
| **Cross-Cutting** | Prompt injection, branch IDOR, PII egress, provider outage/cost và metric freshness là trọng tâm. | 🔴 High | Enforce server-side policy/tool allowlist, redaction, budgets, audit and negative security tests. |

## Instruction Compliance

| Dimension | Verdict | Deviation (if any) | Recommendation |
|---|---|---|---|
| **Conventions** | ✅ | REST/OpenAPI 3.1, parent envelopes, UUID v7, UTC, tenant/branch scope và event-driven read model được tôn trọng ở mức Vision. | — |
| **Architecture** | ✅ | AI Assistant không đọc service DB trực tiếp, không thay thế Reporting và không gọi mutation boundary. | Giữ Reporting là metric owner trong Requirements/Contracts. |
| **Testing** | ⚠️ | Vision có success/guardrail nhưng chưa có executable fixtures cho prompt injection, tool scope, provider failure, freshness và summary rerun. | Đưa thành contract/security/evaluation test matrix ở Requirements. |

## Verdict

Vision **có thể tiếp tục sang Requirements với warnings**. Không có blocker về mục tiêu, user boundary hay nguyên tắc read-only/tool-mediated. Các warnings phải được chuyển thành contract cụ thể cho Reporting tools, session/scope, LLM state machine, provider policy, contribution calculation và daily-summary completeness trước khi viết code.

**Required user decision:** Xác nhận tiếp tục sang Requirements với trạng thái **⚠️ Warnings (Proceed with Caution)**.

