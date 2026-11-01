Started at: 2026/10/13 00:00:00
Finished at: 2026/10/13 00:00:00
Total time: 0 minutes
---

# Module 9 — Trợ lý AI cho chủ quán: Requirements

**Ngày:** 2026-10-13  
**Trạng thái:** Approved with warnings  
**Vision:** [AI Assistant Vision](./2026-10-13-vision.md)  
**Vision review:** [AI Assistant Vision Review](./2026-10-13-vision-review.md)  
**Phạm vi:** M — Standard Service Flow

## 1. Mục tiêu và boundary

AI Assistant Service cung cấp chat read-only cho Owner/Manager và daily summary P2. Service dùng DeepSeek function calling nhưng không phải nguồn dữ liệu báo cáo:

- Reporting read model sở hữu metric, aggregate, business-date, branch timezone, freshness và contribution facts.
- Identity/Cognito sở hữu tenant, role và branch membership.
- AI Assistant sở hữu conversation/turn state, tool policy, redacted audit, provider policy và summary presentation state.
- DeepSeek chỉ nhận prompt đã redacted và tool result aggregate; không được đọc database hoặc gọi domain mutation.

### Priority

- **P1:** Hỏi đáp báo cáo bằng ngôn ngữ tự nhiên trong Owner/Manager dashboard.
- **P2:** Tóm tắt cuối ngày theo business date/branch timezone, có baseline và contribution drivers.
- P1/P2 không thay đổi P0 order/payment/inventory/loyalty/catalog/recommendation state.

## 2. Actors và integrations

| Actor/System | Quyền/vai trò |
|---|---|
| Owner | Chat toàn tenant hoặc một/một nhóm branch trong tenant; xem/rerun summary trong scope. |
| Manager | Chat và xem summary chỉ trong branch membership còn hiệu lực. |
| Cashier/Kitchen/Auditor | Không được sử dụng assistant chat trong MVP; Audit chỉ nhận evidence. |
| Owner/Manager dashboard | Gửi câu hỏi, chọn scope, hiển thị answer/provenance/freshness/refusal và daily summary. |
| Identity/Cognito | JWT, tenant, role, branch membership; server-side authorization source. |
| Reporting Service | Internal read-only tools cho KPI và contribution; không nhận SQL tùy ý. |
| Order/Payment/Loyalty/Recommendation | Facts đi vào Reporting; AI Assistant không gọi mutation hoặc đọc DB trực tiếp. |
| DeepSeek adapter | Function planning và natural-language rendering; provider failure không làm sai domain state. |
| Scheduler | Tạo daily-summary job theo branch business timezone/cutoff. |
| Audit/Observability | Nhận redacted tool/audit events và metrics/traces. |

## 3. Definitions và invariants

### 3.1 Resolved scope

`ResolvedScope` là server-generated object gồm `tenantId`, `branchIds[]`, `role`, `scopeReason`, `authorizationVersion` và `resolvedAt`. Client/LLM không được tự tạo hoặc mở rộng object này.

- Owner có thể chọn `TENANT` hoặc một danh sách branch thuộc tenant.
- Manager chỉ có thể chọn subset của branch membership hiện tại.
- Mỗi turn snapshot một `ResolvedScope`; đổi branch phải tạo scope revision mới và không dùng scope cũ cho tool call.
- Scope rỗng, foreign branch, branch không còn membership hoặc tenant mismatch trả lỗi/clarification, không tự suy đoán.

### 3.2 Business period

- Request nhận `from`/`to` theo ISO date hoặc datetime nhưng server chuẩn hóa theo IANA timezone của branch.
- Cross-branch query phải dùng một timezone policy rõ ràng: mặc định tenant reporting timezone; branch-local comparison chỉ khi tool hỗ trợ.
- Browser timezone không bao giờ là authority.
- `businessDate` và `businessDayCutoff` do Reporting/branch policy cung cấp.

### 3.3 Freshness và completeness

Mỗi tool result phải trả:

- `sourceName` và `projectionVersion`/`watermark`;
- `lastUpdatedAt` UTC;
- `freshnessStatus`: `FRESH`, `STALE`, `INCOMPLETE`, `UNAVAILABLE`;
- `completenessStatus` và `missingSources[]` nếu có;
- `calculationVersion`.

Assistant không trình bày result `UNAVAILABLE` như fact. `STALE`/`INCOMPLETE` phải được hiển thị trong answer; daily summary không được publish khi completeness gate fail.

### 3.4 Read-only invariant

Tool registry của AI Assistant chỉ chứa read/reporting operations. Không có tool hoặc provider output nào được phép:

- tạo/sửa/hủy Order;
- initiate/refund Payment;
- reserve/commit/release Inventory;
- sửa Catalog/price;
- accrue/redeem Loyalty;
- sửa Recommendation rule/model;
- sửa Identity/branch/employee/permission.

## 4. Functional requirements P1 — Natural-language report Q&A

### FR-AI-001 — Start/resume chat session

Owner/Manager có thể mở session từ dashboard. Service phải:

1. xác thực Cognito JWT và resolve tenant/role/branch membership;
2. nhận `requestedScope` nhưng chỉ dùng nó như input cần validate;
3. tạo `conversationId` và `turnId` UUID v7;
4. gắn session policy version, model policy version và redaction policy version;
5. không đưa raw JWT/phone/PII vào session transcript.

Session có TTL cấu hình. Sau `EXPIRED`, follow-up turn phải mở session mới; không tự reuse scope cũ.

### FR-AI-002 — Resolve authorization scope

Trước mọi tool call, server phải resolve scope độc lập với LLM. Tool executor nhận `ResolvedScope` từ trusted context, không nhận `tenantId`/`branchIds` từ function arguments như authority.

Nếu user hỏi “toàn bộ chi nhánh”:

- Owner: cho phép nếu policy và date range hợp lệ;
- Manager: trả `scope_not_allowed` hoặc yêu cầu user chọn branch hợp lệ;
- không trả tên/count branch ngoài scope để tránh enumeration.

### FR-AI-003 — Classify intent và lập tool plan

DeepSeek được phép phân loại câu hỏi vào allowlist intent:

- `SALES_SUMMARY`;
- `ORDER_COUNT`;
- `AVERAGE_ORDER_VALUE`;
- `TOP_ITEMS`;
- `BRANCH_COMPARISON`;
- `HOURLY_SALES`;
- `CONTRIBUTION_DRIVERS` cho daily summary/explanation;
- `UNSUPPORTED`.

Server phải validate intent/function name và JSON arguments theo schema. Model không được:

- phát tool name ngoài registry;
- truyền SQL, table name, arbitrary filter expression hoặc raw ID list;
- thay đổi scope, max range, result limit, currency hoặc policy fields;
- gọi quá `maxToolCallsPerTurn` (mặc định 3).

`UNSUPPORTED`, prompt injection hoặc policy violation trả refusal an toàn, không gọi Reporting.

### FR-AI-004 — Allowlisted reporting tools

Reporting phải cung cấp các read-only tool contracts sau:

| Tool | Input tối thiểu | Output tối thiểu |
|---|---|---|
| `getSalesSummary` | resolved scope, business period, comparison policy | order count, gross/net, tax if allowed, currency, baseline delta, freshness, calculation version |
| `getOrderCount` | resolved scope, period | count, comparison delta, freshness |
| `getAverageOrderValue` | resolved scope, period | AOV, order count denominator, currency, comparison delta, rounding version |
| `getTopItems` | resolved scope, period, limit ≤ 10, sort allowlist | item safe label, quantity, revenue aggregate, rank, freshness |
| `compareBranches` | Owner scope, period, selected branch subset or all authorized branches | branch safe label, aggregate KPI, rank/delta; no unauthorized branch existence |
| `getHourlySales` | resolved branch scope, max period policy, hour bucket | hourly aggregate, timezone, count/revenue, freshness |
| `getContributionDrivers` | target scope/period, baseline, dimension allowlist | top-N positive/negative contributors, contribution amount/percentage, materiality, calculation version |

Tool outputs không chứa customer phone/name, employee personal data, raw order/customer/payment IDs hoặc raw line payload.

### FR-AI-005 — Tool execution guardrails

Tool Gateway phải enforce:

- tenant predicate và branch predicate ở application/repository boundary;
- maximum date range theo tool, mặc định 31 ngày cho interactive query;
- maximum result size and top-N;
- allowlisted dimensions/sorts only;
- query timeout, total turn deadline và circuit breaker;
- response schema validation;
- freshness/completeness policy;
- redaction assertion trước prompt/log/audit.

Reporting query không được thực thi trực tiếp bởi LLM-generated SQL. Tool timeout hoặc schema failure trả typed tool error và không retry vô hạn.

### FR-AI-006 — Assemble answer with provenance

Answer phải là structured response trước khi render text:

```json
{
  "status": "ANSWERED",
  "text": "Doanh thu chi nhánh A tuần này là ...",
  "scope": {"type": "BRANCH", "branchIds": ["..."]},
  "period": {"from": "2026-10-05", "to": "2026-10-11", "timezone": "Asia/Ho_Chi_Minh"},
  "sources": [
    {"tool": "getSalesSummary", "watermark": "sales:v901", "lastUpdatedAt": "2026-10-12T17:00:00Z"}
  ],
  "calculationVersion": "sales-metric-v2",
  "warnings": []
}
```

LLM chỉ được render dựa trên structured facts. Nếu provider trả text mâu thuẫn với facts, server phải reject/regenerate bounded hoặc trả structured facts/error; không coi text của model là source of truth.

### FR-AI-007 — Refusal, clarification và partial result

Các trạng thái user-facing:

- `ANSWERED`: facts đủ và hợp lệ;
- `NEEDS_SCOPE`: cần user chọn branch hoặc thời gian;
- `UNSUPPORTED`: câu hỏi ngoài allowlist;
- `NO_DATA`: không có dữ liệu trong kỳ hợp lệ;
- `STALE_DATA`: có dữ liệu nhưng freshness policy không đạt;
- `PARTIAL`: một số tool facts hợp lệ, phần còn lại unavailable; phải liệt kê missing source;
- `PROVIDER_UNAVAILABLE`: DeepSeek không sẵn sàng nhưng không có structured answer đủ để render;
- `FAILED`: lỗi nội bộ với safe error code.

Không status nào được dùng để che giấu cross-tenant resource existence.

### FR-AI-008 — Conversation follow-up

Follow-up có thể tham chiếu period/scope trước đó chỉ khi session còn active và server giữ immutable scope revision. Nếu câu hỏi mới chứa branch/time change, server tạo revision mới và hiển thị scope đã resolve.

LLM không được tự mang scope từ transcript nếu user không còn quyền; every turn re-authorizes membership.

### FR-AI-009 — Redacted audit

Mỗi turn phải tạo audit evidence append-only:

- actor ID/role, tenant/branch scope, scope revision;
- redacted question and normalized intent;
- validated tool names/arguments, policy decision, tool result status;
- source watermark/freshness/calculation version;
- provider/model/prompt-policy version;
- response status/hash, refusal/error, latency, correlation/causation.

Không persist raw prompt/response mặc định. Nếu bật debug sampling, phải có feature flag, redaction kiểm tra trước lưu, retention ngắn và audit permission riêng.

### FR-AI-010 — Rate/budget/cost controls

Per user/tenant policy phải giới hạn:

- requests per minute;
- concurrent sessions/turns;
- total tool calls per turn;
- input/output tokens hoặc provider cost budget;
- maximum period/result size;
- daily summary reruns.

Limit breach trả `429`/`budget_exceeded` và không gọi thêm provider/tool.

## 5. Functional requirements P2 — Daily summary

### FR-AI-201 — Schedule branch daily-summary job

Scheduler tạo một job cho mỗi eligible branch/business date sau cutoff theo branch IANA timezone. Job key duy nhất:

```text
(tenantId, branchId, businessDate, summaryPolicyVersion)
```

Job chỉ chuyển sang `READY` nếu Reporting completeness watermark đạt policy. Nếu chưa đủ, job chuyển `WAITING_FOR_DATA`, retry bounded hoặc cần operator rerun; không publish summary thiếu dữ liệu như hoàn chỉnh.

### FR-AI-202 — Baseline policy

Baseline mặc định:

1. cùng thứ của tuần trước trong cùng branch/business timezone;
2. nếu thiếu hoặc không comparable, fallback trung bình 7 business days gần nhất;
3. nếu vẫn thiếu, status `NO_BASELINE`, summary vẫn có thể hiển thị actual facts nhưng không gắn percentage change.

Summary phải ghi `baselineType`, baseline period, data points used và policy version.

### FR-AI-203 — Deterministic contribution analysis

Reporting/tool layer tính contribution theo dimension allowlist:

- item/category safe label;
- hour bucket;
- branch khi scope Owner có nhiều branch.

Kết quả phải có actual, baseline, signed delta, contribution amount/percentage, materiality threshold và calculation version. Nếu dữ liệu không đủ chứng minh, wording policy chỉ dùng “yếu tố liên quan”/“đóng góp quan sát được”, không dùng “nguyên nhân chắc chắn”.

### FR-AI-204 — Generate summary

LLM nhận structured daily facts đã redacted và render summary theo schema:

- headline;
- actual vs baseline;
- top positive/negative contributing factors;
- freshness/completeness;
- caveats/limitations;
- source/tool provenance.

Nếu LLM unavailable hoặc output schema invalid, service lưu structured summary fallback hoặc trạng thái `PROVIDER_UNAVAILABLE`; không tạo prose bịa.

### FR-AI-205 — View, rerun và idempotency

Owner/Manager chỉ xem summary trong authorized scope. Rerun phải:

- yêu cầu idempotency key;
- dùng cùng business date/policy version hoặc tạo explicit revision;
- không duplicate published summary/audit event;
- giữ lineage tới source watermarks/model/prompt policy.

Summary revision cũ không bị overwrite vật lý; state chuyển qua `SUPERSEDED` nếu rerun hợp lệ.

## 6. State machines

### 6.1 Conversation/turn

`CREATED → ACTIVE → ANSWERED | NEEDS_SCOPE | UNSUPPORTED | NO_DATA | STALE_DATA | PARTIAL | PROVIDER_UNAVAILABLE | FAILED → EXPIRED`

Turn terminal state không được chạy lại tool/provider bằng cùng turn ID; retry client tạo idempotent replay hoặc turn mới theo policy.

### 6.2 Tool execution

`PLANNED → VALIDATED → RUNNING → SUCCEEDED | EMPTY | STALE | INCOMPLETE | TIMED_OUT | REJECTED | FAILED`

`REJECTED` xảy ra trước Reporting call nếu auth/argument/policy fail.

### 6.3 Daily summary

`SCHEDULED → WAITING_FOR_DATA → READY | DATA_LATE → GENERATING → PUBLISHED | NO_BASELINE | PROVIDER_UNAVAILABLE | FAILED | SUPERSEDED`

`DATA_LATE` là terminal state của lần chạy hiện tại khi hết maximum wait; chỉ rerun có source watermark/idempotency key mới được chuyển job sang `READY`. Không chuyển `PUBLISHED` nếu required facts thiếu hoặc output không đạt schema; `NO_BASELINE` chỉ hợp lệ khi actual facts đủ và policy cho phép publish không có percentage comparison.

## 7. API requirements

### 7.1 Public dashboard API

| Method | Path | Auth/scope | Purpose | Status |
|---|---|---|---|---|
| POST | `/api/v1/ai/conversations` | Owner/Manager | Mở conversation + resolve scope | 201, 401, 403, 422 |
| GET | `/api/v1/ai/conversations/{conversationId}` | Owner/Manager owner/scope | Lấy safe session state | 200, 403, 404 |
| POST | `/api/v1/ai/conversations/{conversationId}/turns` | Owner/Manager | Hỏi một câu read-only | 200/202, 403, 409, 422, 429, 503 |
| GET | `/api/v1/ai/conversations/{conversationId}/turns/{turnId}` | Owner/Manager owner/scope | Poll turn/answer/provenance | 200, 403, 404 |
| GET | `/api/v1/ai/daily-summaries` | Owner/Manager | List summary theo scope/date | 200, 403, 422 |
| GET | `/api/v1/ai/daily-summaries/{summaryId}` | Owner/Manager owner/scope | Xem summary + lineage | 200, 403, 404 |
| POST | `/api/v1/ai/daily-summaries/{summaryId}/reruns` | Owner/Manager allowed scope | Rerun summary | 202, 403, 404, 409, 429 |

Public API không expose tool registry/provider prompt/system policy. Query list dùng cursor pagination, limit default 20 max 100.

### 7.2 Internal contracts

| Method | Path | Caller | Purpose |
|---|---|---|---|
| POST | `/internal/v1/reporting/ai-tools/{toolName}` | AI Assistant workload | Execute one allowlisted aggregate tool with trusted `ResolvedScope` |
| GET | `/internal/v1/reporting/freshness` | AI Assistant/Scheduler | Read projection freshness/completeness per scope/date |
| POST | `/internal/v1/ai/summary-jobs` | Scheduler/ops | Enqueue idempotent daily summary job |
| POST | `/internal/v1/ai/summary-jobs/{jobId}/run` | Worker | Run a validated summary job |
| POST | `/internal/v1/ai/provider-health` | Ops/worker | Safe provider health/config check; never sends PII |

Internal API yêu cầu workload identity/mTLS; provider secret chỉ nằm trong adapter/secret boundary.

## 8. Event và integration requirements

### Consumed facts/contracts

AI Assistant không cần consume raw domain events để tính metric trong P1; Reporting là source. Cần một trong hai contract:

1. synchronous internal Reporting tool + freshness endpoint; hoặc
2. versioned `ReportProjectionWatermarkUpdated.v1`/`BusinessDayReady.v1` để trigger/suppress jobs.

Event/payload không chứa PII hoặc raw order lines.

### Produced events

| Event | Payload minimum | Consumers |
|---|---|---|
| `AIReportTurnCompleted.v1` | turn/conversation ID, actor role, scope summary, status, tool names, watermark, response hash, occurredAt | Audit, Reporting/ops |
| `AIDailySummaryPublished.v1` | summary ID, tenant/branch/date, baseline type, source watermarks, policy/model version, revision | Dashboard/Reporting, Audit |
| `AIAccessRefused.v1` | actor/scope summary, refusal/error code, policy version, correlation | Audit/Security |
| `AIDailySummarySuperseded.v1` | old/new summary ID, business date, rerun reason, source revision | Audit/Reporting |

No event chứa raw question, raw prompt, raw response, phone, customer identity, payment identifier, provider secret hoặc model artifact.

## 9. Data and consistency requirements

AI Assistant PostgreSQL owns only:

- `ai_conversations`/`ai_turns` safe metadata and state;
- `ai_tool_executions` validated call metadata/status/latency;
- `ai_daily_summary_jobs` and `ai_daily_summaries` lineage/status;
- `ai_provider_policies` versioned non-secret policy/config reference;
- `ai_audit_references`, outbox/inbox/idempotency/retry state.

Reporting owns `sales_daily`, `top_items`, hourly/branch comparison/contribution read models and their fact definitions. AI Assistant may snapshot safe aggregate result/provenance needed for replay, but must not become a second metric source.

Invariants:

1. Every row is tenant-scoped; branch-scoped rows carry branch ID or explicit scope type.
2. Every turn/tool execution stores the resolved scope revision and cannot be expanded after execution.
3. One idempotent result per `(tenantId, conversationId, clientTurnId)`.
4. One active summary revision per `(tenantId, branchId, businessDate, policyVersion)`; superseded revisions remain immutable.
5. Summary can only publish from complete/allowed stale facts and schema-valid structured output.
6. No raw PII, prompt, provider secret or arbitrary SQL is persisted.
7. Outbox is written transactionally with local state; consumer dedupes by event ID.
8. Retrying provider/tool does not duplicate summary publication or audit event.

## 10. Non-functional requirements

> Targets are MVP/demo targets until measured under implementation load.

| Category | Requirement | Target |
|---|---|---|
| Performance | Internal Reporting tool call | p95 ≤ 2 s for standard aggregate query |
| Performance | End-to-end answered chat turn | p95 ≤ 8 s when DeepSeek healthy; timeout ≤ 10 s |
| Performance | Scope/refusal without provider | p95 ≤ 500 ms |
| Performance | Daily summary after data ready | 95% published/terminal within 10 min |
| Tool safety | Calls per turn | max 3 by default, policy-configurable downward only for public path |
| Query safety | Interactive period | max 31 business days; Owner cross-branch policy may lower limit |
| Availability | Provider outage isolation | No domain mutation or Reporting corruption; structured error/facts fallback |
| Security | Authorization | 100% tool calls carry server-resolved tenant/branch scope; negative IDOR tests pass |
| Privacy | Redaction | No phone/customer name/raw transaction ID/payment identifier/secret in prompt/log/event |
| Reliability | Idempotency | Session/turn/rerun replay creates no duplicate summary/event |
| Freshness | Interactive response | Always return watermark/lastUpdatedAt/status |
| Observability | Traceability | Correlation ID from dashboard through policy/tool/provider/audit/outbox |
| Cost control | Provider budget | Per-turn token/cost cap and tenant daily budget; breach fail-closed |
| Retention | Conversation/audit | Configurable policy; default raw prompt retention zero, safe metadata 90 days, audit parent policy applies |
| Scalability | API/worker | Stateless API; summary/provider workers scale independently |

## 11. Security, privacy and observability

### Security

- Verify JWT signature, issuer, audience, expiry and role/membership server-side.
- Use workload identity/mTLS for Reporting/provider/worker internal calls.
- Treat user message, retrieved facts and model output as untrusted data; system policy and tool registry cannot be overridden by prompt.
- Enforce query parameter allowlists, date/result limits, rate limits and circuit breakers.
- Use Secrets Manager for DeepSeek API key; rotate without redeploying prompt/business policy.
- Redact PII/secrets before prompt, logs, traces, audit and events; safe error messages never reveal unauthorized branch existence.
- Audit policy decisions/refusals but do not store raw transcript by default.

### Metrics

- `ai_turn_total{status,intent,role}`;
- `ai_tool_total{tool,status}`;
- `ai_tool_latency_ms{tool}`;
- `ai_provider_latency_ms{model,status}`;
- `ai_provider_tokens_total{direction}` and cost estimate;
- `ai_scope_refusal_total{reason}`;
- `ai_pii_redaction_total{field_class}`;
- `ai_report_freshness_seconds{tool,status}`;
- `ai_daily_summary_total{status,baseline_type}`;
- `ai_daily_summary_lag_seconds`;
- `ai_prompt_injection_refusal_total`;
- `ai_outbox_lag`/`ai_dlq_depth`.

### Logs/traces

Structured logs contain IDs, safe scope type/count, intent, tool/status, latency and policy version; never raw question/prompt/response/PII. Distributed traces include correlation/causation and provider span metadata but redact content and secrets.

## 12. Edge cases and failure policy

| Case | Required behavior |
|---|---|
| Manager asks another branch by name/ID | `NEEDS_SCOPE` or `scope_not_allowed`; do not reveal branch existence or call tool. |
| Owner asks all branches with too-wide date range | Reject with allowed range; offer narrower range, no implicit truncation. |
| Ambiguous “tuần này” | Resolve using branch/tenant business timezone and return exact dates; if no branch scope, ask selection. |
| User prompt says ignore policy/reveal prompt/SQL | Refuse; log safe policy event; no tool call. |
| Tool argument contains SQL/raw field | Schema/policy reject before Reporting. |
| Reporting projection stale | Return `STALE_DATA` or partial with visible freshness; daily summary waits. |
| Reporting partial branch set | Do not present cross-branch total as complete; list missing source/status. |
| No sales in period | `NO_DATA` or zero actual with explicit denominator semantics; do not infer decline. |
| No baseline | Publish only if policy allows `NO_BASELINE`; omit percentage comparison. |
| DeepSeek timeout | Bounded retry if budget remains; then structured facts/error, no invented prose. |
| DeepSeek returns invalid function/JSON | Reject output, bounded repair/regenerate; otherwise `FAILED`/`PROVIDER_UNAVAILABLE`. |
| User retries same turn key | Return original idempotent result; no duplicate tool/event. |
| Summary rerun after late `OrderPaid` | New immutable revision, old becomes `SUPERSEDED`, lineage records watermark/reason. |
| Branch closes late or never closes | Scheduler uses explicit cutoff policy; job waits/marks operator action, never silently assumes complete. |
| Provider secret/config unavailable | Fail closed with safe error; no raw secret in logs. |

## 13. Acceptance criteria

### P1

1. Authorized Owner asks sales summary for one branch and receives answer with exact period, timezone, watermark, tool source and calculation version.
2. Authorized Manager asks top items for an assigned branch and receives aggregate safe labels only.
3. Manager query for an unassigned branch is rejected before Reporting call; no branch existence leaks.
4. Owner cross-branch comparison works only within tenant and authorized branch set.
5. AOV and hourly results use Reporting-owned schemas and deterministic rounding/timezone.
6. Prompt injection attempting SQL, system prompt disclosure, scope escalation or mutation is refused without tool call.
7. Tool timeout/partial/stale data produces explicit status/warning; no hallucinated number is returned.
8. DeepSeek outage returns structured facts/error and leaves all domain/reporting state unchanged.
9. Replaying the same conversation turn key returns the original result and does not duplicate audit/event side effects.
10. Redaction tests prove no phone, customer name, raw order/customer/payment identifier or secret reaches provider/log/event.

### P2

1. Scheduler creates one idempotent job per eligible branch/business date/policy version after cutoff.
2. Job waits for completeness watermark and does not publish an incomplete summary.
3. Same-weekday baseline is used; 7-day fallback and `NO_BASELINE` are explicit.
4. Contribution facts are deterministic, versioned and traceable to Reporting source watermarks.
5. Summary language uses related/contributing wording and never asserts causal proof.
6. Rerun after late facts creates one new immutable revision and supersedes the old summary without duplicates.
7. Owner/Manager cannot view/rerun summaries outside their resolved branch scope.

## 14. Traceability

| Vision goal | Requirements | Acceptance evidence |
|---|---|---|
| Natural-language report Q&A | FR-AI-001–008, public chat APIs | Authorized dashboard chat E2E with provenance |
| DeepSeek function calling | FR-AI-003–005, provider/error states | Tool allowlist, JSON schema, max-loop and invalid-output tests |
| Branch guardrail | §3.1, FR-AI-002, security requirements | Owner/Manager scope matrix, IDOR/prompt-injection tests |
| PII masking | FR-AI-005/009, §11 | Provider/log/event redaction inspection |
| Read-only assistant | §3.4, no mutation tool registry | Mutation boundary negative tests |
| Daily summary | FR-AI-201–205, summary state machine | Cutoff/completeness/baseline/rerun fixtures |
| Explainable “why” | FR-AI-203/204, contribution schema | Deterministic driver/rounding/wording tests |

## 15. Decisions resolved from Vision/Requirements review

Các warning ảnh hưởng tới boundary/contract được đóng bằng các quyết định sau; mọi decision phải được phản ánh lại trong Architecture và Contracts.

### 15.1 Reporting ownership và metric dictionary

- Reporting là **metric owner** và là authority duy nhất cho `sales_daily`, `sales_by_branch`, `sales_by_hour`, `top_items`, `aov` và `contribution_drivers`.
- AI Assistant gọi synchronous internal Reporting tools cho interactive chat. `ReportProjectionWatermarkUpdated.v1` và `BusinessDayReady.v1` (nếu được phát) chỉ là trigger/hint; query Reporting và freshness endpoint vẫn là authority cuối.
- `paidOrderCount` đếm unique finalized `OrderPaid.v1`; `grossMinor`, `netMinor`, `vatMinor` lấy từ canonical Reporting projections và không được tính lại trong AI Assistant.
- `AOV = netMinor / paidOrderCount`, làm tròn theo `calculationVersion` của Reporting; zero denominator trả `NO_DATA`, không trả `0` như một fact mơ hồ.
- Refund/correction/late settlement semantics do Reporting metric policy quyết định và trả cùng `calculationVersion`; AI Assistant chỉ render values đã nhận.
- Architecture/Contracts phải bổ sung tool schemas và golden fixtures cho sáu tool ở FR-AI-004, không query chéo Order/Payment DB.

### 15.2 Authorization propagation và revocation

- Mỗi conversation creation và mỗi protected turn gọi Identity `POST /internal/v1/authorization-decisions` với current Cognito subject, tenant/branch request, permission `ai:report:read` và correlation ID.
- `ResolvedScope` chứa `membershipVersion`/`authorizationVersion`; Reporting tool nhận trusted signed/workload scope và vẫn enforce tenant/branch predicates tại repository.
- Không dùng long-lived local authorization cache cho P1 authority. `AuthorizationContextChanged.v1` chỉ invalidate non-authoritative optimization; membership revoke/role change có hiệu lực ở request kế tiếp.
- Owner tenant-wide scope là explicit `scopeType=TENANT`, không biểu diễn bằng wildcard branch ID.

### 15.3 Conversation context không lưu raw transcript

- Mỗi turn lưu structured semantic context: intent, period, comparison, metric, dimensions, scope revision và safe result references; không lưu raw user message/prompt/response.
- Follow-up được resolve từ structured context gần nhất cộng với câu hỏi hiện tại; nếu không đủ rõ, trả `NEEDS_SCOPE`/clarification.
- Structured context được mã hóa at rest, TTL mặc định 90 ngày; audit reference giữ metadata redacted theo parent policy, raw prompt retention mặc định bằng 0.

### 15.4 Chat execution và provider validator

- P1 chat chạy synchronous trong deadline 10 giây. Nếu tool/model cần tiếp tục hoặc deadline sắp hết, API trả `202` kèm `turnId`/poll location; MVP không dùng SSE.
- Mỗi turn tối đa 3 tool calls, một lần repair/regenerate output và một provider retry nếu còn budget; quá giới hạn chuyển sang structured facts/error.
- Server validate model function JSON và final answer schema. Prose mismatch với facts không được publish; sau một repair thất bại, trả facts an toàn hoặc `PROVIDER_UNAVAILABLE`.
- DeepSeek adapter dùng một configured model/prompt policy version, token/cost budget, timeout 8 giây, bounded retry 1 lần và circuit breaker; không fallback provider trong MVP. Health check không gửi user/report data.

### 15.5 Daily summary completeness và late facts

- Scheduler tạo job tại `businessDayCutoff + 15 phút` theo branch timezone. Reporting freshness endpoint là authority để chuyển `WAITING_FOR_DATA → READY`.
- Job chờ tối đa 30 phút cho completeness watermark; quá thời hạn chuyển `WAITING_FOR_DATA`/`DATA_LATE` và yêu cầu bounded retry/operator rerun, không publish như complete.
- Late `OrderPaid`, refund/correction hoặc projection rebuild sau publish tạo summary revision mới khi source watermark thay đổi; summary cũ chuyển `SUPERSEDED`, không overwrite vật lý.
- `AIDailySummarySuperseded.v1` mang old/new summary IDs, source watermark và reason; rerun idempotency key là `(tenantId, branchId, businessDate, policyVersion, sourceWatermark)`.

### 15.6 Provider/data governance

- Provider policy là versioned config: model ID/version, prompt policy version, region/egress mode, token/cost cap, timeout, retry, circuit state và secret reference (không lưu secret).
- Provider payload chỉ chứa system policy + structured aggregate facts + structured semantic context đã redacted; no phone/customer identity/raw transaction ID.
- Retention matrix phân biệt structured context, tool metadata, summary facts, audit reference và provider error; legal/audit retention không cho phép giữ raw prompt trái policy.

### 15.7 Contribution calculation và wording

- Reporting trả signed delta/contribution amount/percentage, materiality threshold, top-N và `calculationVersion`.
- AI Assistant dùng wording “yếu tố liên quan/đóng góp quan sát được”; từ “nguyên nhân” chỉ được dùng nếu Reporting có explicit causal evidence flag, mặc định false.
- Rounding, zero denominator, negative contribution và ties phải có golden fixtures; LLM không được tự tính lại phần trăm.

## 16. Next phase inputs

Architecture must decide:

1. AI Assistant API/orchestrator, Tool Gateway, Reporting adapter, DeepSeek adapter, policy engine, worker and audit/outbox topology.
2. Synchronous versus asynchronous turn execution and dashboard polling/streaming behavior.
3. Conversation/session storage, encrypted/redacted fields, retention and partition/index plan.
4. Reporting internal tool transport and source watermark/completeness event/query contract.
5. Prompt assembly/redaction boundary, model response validator and bounded tool-loop state machine.
6. Daily summary scheduler/cutoff/late-fact reconciliation architecture.
7. Recovery, provider circuit breaker, budget enforcement, DLQ and replay model.

## Approval gate

- **Current status:** Approved with warnings.
- **Required before Architecture:** Requirements review must find no blockers; user must approve requirements or accept warnings.
- **Implementation rule:** Không code trực tiếp từ prose; materialize OpenAPI, internal tool schemas, event schemas, provider policy and migrations before implementation.

