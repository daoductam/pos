Started at: 2026/10/13 00:00:00
Finished at: 2026/10/13 00:00:00
Total time: 0 minutes
---

# Spec Sentinel Report — AI Assistant Requirements

**Spec:** [`2026-10-13-requirements.md`](./2026-10-13-requirements.md)  
**Date:** 2026-10-13  
**Status:** ⚠️ Warnings (Proceed with Caution)

## Review scope

- Reused approved AI Assistant Vision and Vision review.
- Cross-checked parent POS Platform, Identity, Order, Payment, Shift, Recommendation and existing Reporting skeleton.
- Không có AI Assistant/Reporting implementation source, generated OpenAPI/AsyncAPI hoặc migration source để trace; review tập trung vào requirements coherence và cross-service contract readiness.

## Findings

### Warnings

| # | Category | Finding | Risk | Recommendation |
|---|---|---|---|---|
| 1 | Reporting contract | `getAverageOrderValue`, `getHourlySales`, `compareBranches` và `getContributionDrivers` được yêu cầu nhưng parent Reporting mới có `sales_daily`/`top_items` summary. | Tool implementation có thể tự tính metric từ dữ liệu không cùng định nghĩa hoặc query chéo service. | Architecture/Contracts phải bổ sung Reporting-owned tool/read-model schemas, metric definitions, refund/cancel policy, timezone và freshness contract. |
| 2 | Event/query choice | Requirements cho phép synchronous Reporting tool hoặc `ReportProjectionWatermarkUpdated.v1`/`BusinessDayReady.v1` nhưng chưa chọn source trigger chính. | Scheduler và freshness behavior có thể khác giữa các service; khó replay và test completeness. | Chọn một primary contract ở Architecture; nếu dùng cả hai, nêu rõ query là authority và event chỉ là trigger/hint. |
| 3 | Conversation persistence | Follow-up yêu cầu context nhưng data model cấm raw prompt/response; chưa có schema cho redacted context/summary hoặc cách rebuild prompt. | Follow-up có thể mất ngữ cảnh hoặc vô tình lưu raw PII để giữ context. | Chốt redacted turn summary/semantic state, TTL, encryption/key ownership và rule không gửi toàn transcript cho provider. |
| 4 | Authorization source | `ResolvedScope` là server-generated nhưng chưa chỉ rõ Identity decision API/cache version, revocation behavior và repository defense-in-depth cho Reporting. | Membership revoke hoặc stale authorization cache có thể cho phép query branch cũ. | Chốt authorization context contract, membership version/revocation check và tenant/branch predicate ở Reporting tool/repository. |
| 5 | Tool response trust | Requirements yêu cầu reject/regenerate khi text mismatch structured facts nhưng chưa định nghĩa response validator/repair budget và khi model vẫn mismatch. | UI có thể hiển thị prose sai dù structured data đúng hoặc retry làm tăng cost. | Định nghĩa answer schema validator, one bounded repair, rồi fallback structured facts; lưu mismatch metric/audit. |
| 6 | P2 cutoff/late facts | `WAITING_FOR_DATA`/completeness watermark có state nhưng chưa có SLA, maximum wait, operator action và correction event khi late `OrderPaid`/refund đến sau publish. | Daily summary bị treo hoặc stale mà không có cách sửa minh bạch. | Thêm cutoff wait policy, terminal `DATA_LATE`/rerun path, revision lineage và `AIDailySummarySuperseded` trigger. |
| 7 | Provider governance | DeepSeek token/cost limits, region/data-use, prompt policy, key rotation, model deprecation và health-check semantics chưa thành config/contract schema. | Khó kiểm soát chi phí, compliance và reproducibility; provider health có thể gửi dữ liệu không cần thiết. | Materialize versioned provider policy, secret reference, zero-PII health check, budget/circuit-breaker/DLQ fixture. |
| 8 | API behavior | Public turn endpoint cho phép `200/202` nhưng chưa phân biệt synchronous answer, queued turn, polling/streaming và client timeout contract. | Dashboard có thể coi queued/provider-unavailable là final answer hoặc retry duplicate. | Chốt response state machine, `202` polling location/turn status, SSE/streaming có thể để ngoài MVP, và idempotency key semantics. |
| 9 | Retention/audit | Default safe metadata retention 90 ngày và audit parent policy được nêu nhưng chưa map từng field/table/event với deletion/legal hold policy. | Giữ scope/question-derived metadata quá lâu hoặc xóa audit bắt buộc. | Tạo data-classification/retention matrix; raw prompt zero retention mặc định; audit redacted references theo parent retention. |
| 10 | Metric semantics | AOV, gross/net, daily business date, comparison delta và contribution rounding chưa có canonical calculation/version fixtures. | Câu trả lời “giảm 12%” có thể không khớp Dashboard/Reporting. | Reporting owner publish metric dictionary and golden fixtures; AI Assistant chỉ render returned facts. |

## Cross-Impact Analysis

| Dimension | Finding | Risk Level | Recommendation |
|---|---|---|---|
| **Shared Logic** | AI Assistant không có implementation call sites; phụ thuộc Identity scope resolver và Reporting metric/read model. | 🔴 High | Không duplicate metric/auth logic; dùng internal contracts và generated fixtures. |
| **Data/State** | Conversation/turn/tool/audit/summary state mới; Report facts vẫn thuộc Reporting. | 🟡 Medium | Enforce ownership, immutable revisions, TTL/deletion and no raw prompt persistence. |
| **API Contracts** | Public chat/summary APIs, internal Reporting tools, freshness/cutoff and provider adapter cần schemas. | 🔴 High | Chốt response states, idempotency, tool schemas and parent Reporting amendment trước Architecture. |
| **Flow Integrity** | Chat: auth → scope → plan → tool → validate → render; summary: cutoff → completeness → baseline → contribution → LLM → publish. | 🟡 Medium | Add provider/tool timeout, late facts, partial result, rerun and supersede paths to sequence diagrams. |
| **Cross-Cutting** | Prompt injection, branch IDOR, PII egress, provider cost, freshness and audit retention. | 🔴 High | Require security/evaluation matrix, redaction tests, budget policy and operational runbooks. |

## Instruction Compliance

| Dimension | Verdict | Deviation (if any) | Recommendation |
|---|---|---|---|
| **Conventions** | ✅ | REST/OpenAPI direction, parent envelope, UUID v7, UTC, cursor pagination and workload identity align. | — |
| **Architecture** | ⚠️ | Reporting metric/tool source and Identity authorization decision contract are referenced but not yet materialized. | Resolve ownership and transport in Architecture; no direct DB access. |
| **Testing** | ⚠️ | Acceptance list is strong, but model evaluation, golden metric fixtures, prompt-injection corpus, redaction and retention tests are not yet executable. | Add contract/security/evaluation fixtures before implementation. |

## Verdict

Requirements **có thể được duyệt với warnings**. Không có blocker về business scope hoặc read-only security principle. Warnings phải được xử lý ở Architecture/Contracts, đặc biệt là Reporting metric ownership, Identity scope propagation, conversation redacted context, API async semantics, P2 late-data correction và provider governance.

**Required user decision:** Xác nhận tiếp tục sang Architecture với trạng thái **⚠️ Warnings (Proceed with Caution)**.

