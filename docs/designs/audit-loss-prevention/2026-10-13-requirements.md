Started at: 2026/10/13 00:00:00
Finished at: 2026/10/13 00:00:00
Total time: 0 minutes
---

# Module 11 — Giám sát & chống thất thoát: Requirements

**Ngày:** 2026-10-13  
**Trạng thái:** Approved with warnings — user approved after Requirements review  
**Vision:** [Audit & Loss Prevention Vision](./2026-10-13-vision.md)  
**Vision review:** [Audit & Loss Prevention Vision Review](./2026-10-13-vision-review.md)

## 1. Purpose and boundaries

Audit & Loss Prevention là Audit service tập trung, append-only và event-driven. Service tiếp nhận audit/security facts từ các service nghiệp vụ, lưu bằng chứng đã redaction, cung cấp truy vấn theo tenant/branch và tạo anomaly findings/cases ở P2.

Audit không sở hữu hoặc sửa Order, Payment, Shift, Identity, POS device hay Promotion state. Source service phải commit business mutation và transactional outbox trước khi Audit có thể ghi nhận fact. Audit event không thay thế business event và không được dùng làm nguồn doanh thu/kế toán.

### In scope

- P0 audit cho hủy món, promotion/voucher discount và manual discount/override, mở két.
- Common `AuditRecorded.v1` envelope, inbox/dedupe, append-only event store, query projection, watermark/DLQ và integrity digest/checkpoint.
- Audit timeline/search với Owner tenant scope và Manager assigned-branch scope.
- Seven-year minimum retention, legal hold reference, restricted archive/export/replay.
- P2 explainable anomaly rules + employee/branch/time baseline và case workflow.

### Out of scope

- Automatic account lock, operation blocking, payment rejection hoặc disciplinary/payroll action.
- ML anomaly model, biometric profiling, camera/GPS/keystroke surveillance.
- Raw JWT/PIN, payment credentials, customer phone hoặc raw request/response payload storage.
- Interactive query toàn bộ 7 năm; lịch sử cũ dùng asynchronous restricted export.
- Thay thế Reporting, Order, Payment, Shift hoặc Identity source of truth.

## 2. Actors and authorization

| Actor | Permission (proposed) | Scope | Allowed actions |
|---|---|---|---|
| Owner | `audit:read`, `loss-case:read`, `loss-case:resolve`, `loss-case:dismiss`, restricted `audit:operate` | Tenant hoặc subset branch thuộc tenant | Đọc audit/finding, acknowledge/investigate/resolve/dismiss, export/replay theo policy |
| Manager | `audit:read`, `loss-case:read`, `loss-case:investigate` | Assigned branches còn membership hợp lệ | Đọc, acknowledge, bắt đầu/cập nhật investigation trong branch |
| Cashier/Kitchen | Không có quyền Audit console trong MVP | — | Chỉ xuất hiện như historical actor/effective operator snapshot |
| Audit consumer/worker | Workload identity | Tenant/branch trong event envelope | Ingest, project, digest, baseline/rule evaluation |
| Ops/replay worker | Restricted workload + `audit:operate` | Explicit tenant/branch/time window | Replay, rebuild, export, retention/legal-hold operation |

Rules:

1. Identity là online authorization authority; permission, tenant, branch và membership version không do client cung cấp.
2. Owner tenant scope không mặc định cho Manager; Manager filter/cursor/resource ID không được mở rộng assigned branch set.
3. Authorization được kiểm tra trước cache lookup và lặp lại tại Audit repository cho mọi event/finding/case/export reference.
4. Owner có thể xử lý case toàn tenant hoặc subset branch; Manager không được resolve/dismiss.
5. Access denial và suspicious cross-tenant/branch reference cũng tạo security audit event đã redaction.

## 3. Definitions and event taxonomy

### 3.1 Common audit event envelope

Mọi producer dùng common envelope:

| Field | Required | Rule |
|---|---:|---|
| `eventId` | yes | Stable UUID v7; dedupe key across retry/replay |
| `eventType`/`eventVersion` | yes | Versioned action/event schema |
| `occurredAt` | yes | Source business event UTC |
| `tenantId` | yes | Source tenant; never null for tenant facts |
| `branchId` | conditional | Required for branch-scoped POS action |
| `aggregateType`/`aggregateId` | yes | Order, Payment, Shift, PosDevice, Membership, AuditCase, etc. |
| `aggregateVersion` | yes | Source state version when available |
| `sourceEventId`/`sourceVersion` | yes | Producer outbox/source lineage for dedupe/replay |
| `actor` | conditional | Human/workload actor snapshot; absent only for approved system action |
| `effectiveOperator` | conditional | Cashier currently operating POS/device when distinct from authenticated actor |
| `deviceId`/`shiftId` | conditional | Required when action occurs on POS/cash drawer/shift boundary |
| `action`/`resourceType`/`resourceId` | yes | Allowlisted taxonomy and opaque resource reference |
| `reason` | conditional | Required for cancel, manual discount/override and case transitions |
| `beforeSummary`/`afterSummary` | conditional | Allowlisted, redacted, bounded JSON summaries |
| `outcome` | yes | `SUCCEEDED`, `REJECTED`, `FAILED`, `NO_OP` |
| `correlationId`/`causationId` | yes/recommended | Distributed trace and causality |
| `redactionVersion` | yes | Schema/policy version used before publication |

Audit stores the event envelope and safe payload reference, not raw JWT, PIN, secret, provider signature, full phone, payment credential, free-form customer data or unbounded request body.

### 3.2 P0 action taxonomy

| Event | Producer/source of truth | Required business evidence |
|---|---|---|
| `OrderLineCancelled.v1` / `OrderCancelled.v1` | Order | order/line, prior/resulting state, actor/effective operator, reason, order version, branch, outcome |
| `DiscountApplied.v1` | Order/Promotion boundary | discount source `PROMOTION`, `VOUCHER` or `MANUAL_OVERRIDE`; order/line, promotion/rule version if any, before/after amount, allocation, actor, reason for manual override, outcome |
| `CashDrawerOpened.v1` | POS Device/Shift boundary | device, shift, trigger `SALE`, `NO_SALE`, `REFUND`, `OPENING`, `MAINTENANCE` or `UNKNOWN`, actor/effective operator, requested/result time, outcome |
| `AuthorizationDenied.v1` | Gateway/service boundary | permission/resource/scope decision, actor/workload reference, safe reason code, outcome; no secret/JWT |
| `AuditCaseActionRecorded.v1` | Audit | case, transition, actor, reason, prior/new case state, evidence/version reference |

The parent platform may continue to route `AuditRecorded.v1`; Requirements must define whether the action-specific events are source business events consumed by Audit or normalized Audit events emitted by source services. The canonical machine-readable schema must prevent two audit facts for one source mutation.

### 3.3 Actor/effective operator

- `actor` is the authenticated user or workload that requested/authorized the operation.
- `effectiveOperator` is the active cashier/POS operator when quick-switch or device context makes it different from the authenticated user.
- Historical display uses immutable safe label/status snapshots; current Identity lookup cannot rewrite history.
- If attribution is missing or invalid, Audit stores `UNKNOWN` quality code and raises data-quality telemetry; it never guesses from current membership.

## 4. Functional requirements

### 4.1 P0 event production and ingestion

| ID | Priority | Requirement | Acceptance evidence |
|---|---|---|---|
| AUD-FR-001 | P0 | Source service emits audit fact in the same transaction/outbox as a committed cancel, discount/override or drawer-open state/result where applicable. | Commit/retry fixture proves no source commit without durable outbox intent; failed mutation emits rejected/failed evidence only when policy requires it. |
| AUD-FR-002 | P0 | Audit consumer accepts at-least-once EventBridge/SQS delivery and persists inbox dedupe before acknowledging. | Duplicate event ID/source key creates one evidence row and one projection effect. |
| AUD-FR-003 | P0 | Audit validates tenant/branch scope, event type/version, required action fields, bounded reason/summaries and redaction policy. | Invalid/unknown version goes to retry/DLQ with safe error; raw sensitive fields are rejected or removed before persistence. |
| AUD-FR-004 | P0 | Audit preserves source event ID, source version, aggregate version, correlation/causation and producer identity for replay. | Replay retains original lineage and does not create duplicate business evidence. |
| AUD-FR-005 | P0 | Audit exposes source/projection watermark, completeness and DLQ health without raw queue payload. | Missing producer or lagged queue is visible as `PARTIAL`/`STALE`, not treated as no activity. |
| AUD-FR-006 | P0 | Audit persistence is append-only for historical events; corrections are new versioned facts or integrity annotations, never in-place edits. | Direct update/delete is denied; correction creates a new linked event and audit trail. |

### 4.2 P0 cancel item audit

| ID | Requirement |
|---|---|
| AUD-FR-010 | Every successful line cancellation records order/line reference, branch, actor/effective operator, reason, before/after state summary, order version, outcome and correlation. |
| AUD-FR-011 | Terminal order cancellation after the last active line is distinguishable from individual line cancellation. |
| AUD-FR-012 | A rejected cancellation can be recorded as `REJECTED`/`NO_OP` with safe reason code when configured, but must not appear as a successful business mutation. |
| AUD-FR-013 | Replayed `OrderLineCancelled`/`OrderCancelled` events do not create duplicate evidence or duplicate anomaly counts. |

### 4.3 P0 discount/override audit

| ID | Requirement |
|---|---|
| AUD-FR-020 | Audit both promotion/voucher discount applications and manual discount/override; field `discountSource` distinguishes `PROMOTION`, `VOUCHER`, `MANUAL_OVERRIDE`. |
| AUD-FR-021 | Manual discount/override requires a trimmed reason, actor/effective operator, before/after amount and outcome; free-form reason is bounded and redacted. |
| AUD-FR-022 | Promotion/voucher event preserves immutable promotion/rule/voucher version and allocation reference; Audit never re-evaluates current rules. |
| AUD-FR-023 | Discount event supports partial line allocation, order-level allocation and `UNALLOCATED`/`PARTIAL` quality state without inventing an actor or amount. |
| AUD-FR-024 | Failed or rejected discount attempts do not appear as successful discounts; if retained, they carry safe reason code and no sensitive payload. |

### 4.4 P0 cash drawer audit

| ID | Requirement |
|---|---|
| AUD-FR-030 | Every successful drawer open records device, branch, shift, actor/effective operator, trigger, requested/result time, outcome and correlation. |
| AUD-FR-031 | Manual/no-sale open is distinguishable from sale/refund/opening/maintenance trigger. Unknown trigger is explicit and raises data-quality warning. |
| AUD-FR-032 | Duplicate device command or retry does not double-count one physical/open operation when a stable command/result key exists. |
| AUD-FR-033 | Drawer-open audit remains available even if Payment/Order query path is unavailable; producer outbox/queue retains evidence for retry. |

### 4.5 Audit query and timeline

| ID | Priority | Requirement |
|---|---|---|
| AUD-FR-040 | P0 | Owner/Manager can query immutable audit timeline by bounded period, branch, actor/effective operator, action, resource, outcome, device, shift and correlation ID. |
| AUD-FR-041 | P0 | Interactive query default is 30 days and maximum is 90 days; all requests return `dataQuality`, watermark/lastUpdatedAt and authorized scope. |
| AUD-FR-042 | P0 | History older than 90 days up to seven years is available through asynchronous restricted export, not unbounded synchronous query. |
| AUD-FR-043 | P0 | List API uses stable cursor pagination tied to normalized filter/scope/authorization version; maximum page size is bounded. |
| AUD-FR-044 | P0 | Event detail re-checks tenant/branch authorization and returns safe not-found/forbidden semantics without foreign resource enumeration. |
| AUD-FR-045 | P0 | UI/API distinguishes `FRESH`, `STALE`, `PARTIAL`, `UNAVAILABLE` and `NO_DATA`; absence caused by missing source is never represented as clean zero activity. |
| AUD-FR-046 | P0 | Audit query never returns raw payload, JWT, PIN, customer phone, payment secret, provider signature or unnecessary order notes. |

### 4.6 Integrity, retention and operations

| ID | Priority | Requirement |
|---|---|---|
| AUD-FR-050 | P0 | Historical event rows are protected by append-only database permissions/checks and immutable operation policy. |
| AUD-FR-051 | P0 | Audit creates a batch hash digest/checkpoint over canonical event IDs/content, stores algorithm/version/time/window and verifies it through restricted integrity jobs. |
| AUD-FR-052 | P0 | Integrity mismatch creates security/integrity finding, alert and audit event; it does not silently rewrite evidence. |
| AUD-FR-053 | P0 | Minimum retention for audit/security evidence is seven years; legal hold suspends deletion/archive for referenced scope/data class. |
| AUD-FR-054 | P0 | Archive/export is encrypted, access controlled, bounded, redacted and itself audited; export is asynchronous with operation status. |
| AUD-FR-055 | P0 | Replay/rebuild requires restricted `audit:operate`, explicit tenant/branch/time window, idempotency and original correlation preservation. |
| AUD-FR-056 | P0 | Retention, legal-hold, replay and integrity operations produce immutable operator audit facts. |

### 4.7 P2 anomaly detection

| ID | Priority | Requirement |
|---|---|---|
| AUD-FR-060 | P2 | Worker consumes approved audit/order/payment/shift facts; it cannot mutate source business state or authorize a transaction. |
| AUD-FR-061 | P2 | Rule definitions are versioned, tenant/branch scoped, auditable and configurable without changing historical finding interpretation. |
| AUD-FR-062 | P2 | Initial rule catalog includes: cancel count/amount spike, discount count/amount spike, unusual drawer-open frequency/outside shift, and repeated actions on the same order/shift. |
| AUD-FR-063 | P2 | Baseline dimensions include employee/effective operator, branch and local time window; rule evaluation uses minimum sample and warm-up policy. |
| AUD-FR-064 | P2 | New employee/branch/shift or insufficient data yields `INSUFFICIENT_BASELINE`, not a high-confidence anomaly. |
| AUD-FR-065 | P2 | Each finding includes deterministic finding key, rule/version, evidence event IDs, observation window, baseline summary, severity, confidence/explanation and data-quality state. |
| AUD-FR-066 | P2 | Same rule/subject/window/evidence version is idempotent; later facts create a revision or linked finding, not an unexplained duplicate. |
| AUD-FR-067 | P2 | Finding severity is explainable (`LOW`, `MEDIUM`, `HIGH`, `CRITICAL` policy-defined); no severity is based only on opaque model output. |
| AUD-FR-068 | P2 | Finding generation never blocks login, checkout, payment, drawer open or order mutation. |

### 4.8 P2 case workflow

| ID | Priority | Requirement |
|---|---|---|
| AUD-FR-070 | P2 | Owner/Manager can list and view authorized findings/cases with filters for state, severity, branch, subject, rule and period. |
| AUD-FR-071 | P2 | Manager can `ACKNOWLEDGE` and `INVESTIGATE` findings in assigned branches; every transition requires actor, reason and correlation. |
| AUD-FR-072 | P2 | Owner can acknowledge/investigate and `RESOLVE` or `DISMISS` findings in authorized tenant/branch scope; dismiss requires reason. |
| AUD-FR-073 | P2 | Case state machine rejects invalid transitions and preserves append-only transition history. |
| AUD-FR-074 | P2 | Case evidence is a versioned snapshot/reference; late source events or projection corrections create visible revision, not silent mutation. |
| AUD-FR-075 | P2 | Finding/case access is fail-closed when source scope or evidence authorization cannot be resolved. |

## 5. Business rules

| ID | Rule | Rationale |
|---|---|---|
| AUD-BR-001 | Audit fact is published only from a committed source operation/outbox; Audit does not infer successful business state from a failed request log. | Prevent false evidence. |
| AUD-BR-002 | One source event/source key produces one historical evidence effect; at-least-once delivery is expected. | Prevent duplicate audit/anomaly counts. |
| AUD-BR-003 | Event correction is additive and linked to the original event; historical evidence is never overwritten. | Preserve forensic lineage. |
| AUD-BR-004 | Actor and effective operator are distinct fields; current membership cannot rewrite historical actor snapshot. | Preserve accountability across quick-switch/revocation. |
| AUD-BR-005 | Promotion/voucher discount is auditable even when automatic; manual override always requires a reason. | Detect both promotion misuse and manual leakage. |
| AUD-BR-006 | Unknown/missing source, trigger, actor or allocation is explicit quality state and raises telemetry. | Never silently fill evidence. |
| AUD-BR-007 | Manager cannot resolve/dismiss; Owner resolution/dismissal is itself audited. | Separation of investigation and conclusion. |
| AUD-BR-008 | P2 anomaly is advisory only and cannot authorize or block source operations. | Avoid unsafe autonomous enforcement. |
| AUD-BR-009 | Legal hold takes precedence over ordinary retention/archive deletion. | Preserve required evidence. |
| AUD-BR-010 | Audit data access is always tenant/branch scoped, including exports, replay, case evidence and cache. | Prevent IDOR/cross-tenant leakage. |

## 6. API-level requirements

Public API is REST/OpenAPI 3.1 under `/api/v1`; internal operations use `/internal/v1` and workload identity.

| Method | Path | Permission | Purpose |
|---|---|---|---|
| GET | `/api/v1/audit/events` | `audit:read` | Interactive timeline/search, max 90 days |
| GET | `/api/v1/audit/events/{eventId}` | `audit:read` | Safe event detail with scope re-check |
| GET | `/api/v1/loss-prevention/findings` | `loss-case:read` | Findings/cases list |
| GET | `/api/v1/loss-prevention/findings/{findingId}` | `loss-case:read` | Finding/case detail and evidence references |
| POST | `/api/v1/loss-prevention/findings/{findingId}/acknowledge` | `loss-case:investigate` | Manager/Owner acknowledge |
| POST | `/api/v1/loss-prevention/findings/{findingId}/investigation` | `loss-case:investigate` | Manager/Owner start/update investigation |
| POST | `/api/v1/loss-prevention/findings/{findingId}/resolve` | `loss-case:resolve` | Owner resolve with required reason |
| POST | `/api/v1/loss-prevention/findings/{findingId}/dismiss` | `loss-case:dismiss` | Owner dismiss with required reason |
| POST | `/api/v1/audit/exports` | `audit:operate` | Start bounded historical export |
| GET | `/api/v1/audit/exports/{operationId}` | `audit:operate` | Poll export status/download reference |
| GET | `/internal/v1/audit/health` | workload/ops | Watermark, lag, completeness, DLQ and integrity health |
| POST | `/internal/v1/audit/rebuilds` | `audit:operate` | Rebuild query projection for explicit scope/window |
| POST | `/internal/v1/audit/integrity-checks` | `audit:operate` | Start restricted digest verification |

Common requirements:

- Cognito JWT for public API; workload identity/mTLS for internal API.
- `X-Correlation-Id` required/recommended; idempotency key for case mutation/export/rebuild/integrity commands.
- Common `{data, meta}` and redacted error envelope; no foreign tenant/branch/resource existence leakage.
- Cursor pagination for events/findings; cursor binds scope, filters and authorization version.
- Reason is required and bounded for investigate/resolve/dismiss and manual discount source events.
- Client cannot submit authoritative tenant, branch, actor, effective operator, outcome or before/after fields.

## 7. Non-functional requirements

| Category | Requirement |
|---|---|
| Freshness | P0 audit evidence becomes queryable within 60 seconds under normal queue/projection health; expose lag/watermark and alert when stale. |
| API performance | Audit/findings interactive list/detail p95 ≤2 seconds for bounded 30-day queries and page size ≤100; export is async. |
| Ingestion | Consumer processes at-least-once events with bounded retry, DLQ and replay; no acknowledgement before inbox/evidence transaction commit. |
| Availability | Source services do not depend synchronously on Audit query availability; outbox/queue preserves audit intent. Audit API fails safely with `503`/quality state when unavailable. |
| Scale | Design for seven-year retention, 20 branches/tenant baseline and growth measured by event volume; partition/archive thresholds are validated before migration. |
| Security | Defense-in-depth tenant/branch predicates, Identity authorization before cache, encrypted storage/transport, restricted writes and replay/export. |
| Privacy | Allowlisted/redacted fields only; no raw JWT/PIN/payment credential/customer phone in DB, API, logs, traces, events or anomaly evidence. |
| Integrity | Append-only controls plus versioned batch digest/checkpoint verification; mismatch creates alert/finding and never silent repair. |
| Retention | Audit/security evidence minimum 7 years; legal hold precedence; archive/delete jobs idempotent and audited. |
| Observability | Metrics for ingest lag, projection lag, DLQ, duplicate rate, schema rejection, digest mismatch, case generation, false-positive/dismiss rate and API authorization denial. |
| Testing | Unit, integration, contract, replay/out-of-order, security/IDOR, redaction, retention/legal-hold, load and failure-injection tests. |

## 8. Security, privacy and audit rules

- All queries and mutations require tenant context; repository methods repeat tenant/branch predicates after Identity resolution.
- Resource IDs in event/case/evidence URLs are verified against scope; foreign IDs return safe `404`/`403` without enumeration.
- Audit access, export, replay, integrity check, legal hold, case transition and authorization denial generate audit evidence.
- Raw source payload is not persisted by default. If a bounded encrypted reference is necessary for an approved forensic workflow, the reference/data class/retention must be explicit and access audited.
- Free-form reason/evidence text is length-limited, sanitized and scanned for accidental secrets/PII; UI renders text safely.
- Audit worker trusts event envelope validation, not client-supplied actor/tenant fields from public requests.
- Case evidence is redacted before storage and does not expose customer phone, payment checksum/signing input, PIN or JWT.

## 9. Data quality and failure behavior

| Failure | Required behavior |
|---|---|
| Duplicate event | No-op after dedupe; preserve first evidence and processing telemetry. |
| Out-of-order source version | Store/queue according to source policy; do not overwrite newer evidence; alert on gap. |
| Unknown event version | Quarantine/DLQ with safe schema error; never guess fields. |
| Source event missing | Mark source/projection incomplete; expose `PARTIAL`; trigger reconciliation/alert. |
| Audit DB unavailable | Consumer retries; source outbox/queue remains durable; API returns `UNAVAILABLE`/503 without fake empty result. |
| Digest mismatch | Freeze affected integrity operation, create security finding, preserve evidence and require restricted investigation. |
| Baseline insufficient | Emit no high-confidence finding; record `INSUFFICIENT_BASELINE` telemetry. |
| Late source/correction fact | Recalculate affected projection/finding revision and preserve prior versions/lineage. |
| Case transition conflict | Return `409` with current state; no partial transition or duplicate action event. |
| Export failure | Keep operation failure state and safe error; do not expose partial file as complete export. |

## 10. Event and integration requirements

### Consumed source groups

| Source | Events/facts | Audit/Loss usage |
|---|---|---|
| Order | `OrderLineCancelled.v1`, `OrderCancelled.v1`, `OrderPaid.v1`, discount/order version facts | Cancel/discount evidence, repeated-order rules, actor/order linkage |
| Promotion/Loyalty | applied promotion/voucher version/allocation, manual override fact | Discount source/version and anomaly baseline |
| Payment | payment/refund/reconciliation/security facts | Refund/open-drawer context, payment anomaly evidence; not raw provider payload |
| Shift | `ShiftOpened.v1`, `ShiftClosed.v1`, cash count/discrepancy facts | Shift baseline and drawer-open context |
| Identity/POS | membership/role/device/quick-switch/security facts | Actor/effective operator/device history and authorization |
| Recommendation/Reporting | safe attribution/access/data-quality events where needed | Optional context; no replacement of source facts |

### Produced events

| Event | Purpose |
|---|---|
| `AuditEvidenceRecorded.v1` | Notify downstream consumers of normalized redacted evidence |
| `AuditDataQualityChanged.v1` | Notify missing source/lag/integrity status |
| `AnomalyFindingCreated.v1` / `AnomalyFindingRevised.v1` | Safe finding summary/evidence references for Owner/Manager/ops |
| `AuditCaseActionRecorded.v1` | Append-only acknowledge/investigate/resolve/dismiss transition |
| `AuditIntegrityMismatchDetected.v1` | Security/ops alert and restricted investigation trigger |

All events use event ID/version, tenant/branch, aggregate/source version, correlation/causation, producer and redacted payload. Event consumers must not treat `AnomalyFindingCreated` as authorization or business-state command.

## 11. Anomaly rule requirements

Initial P2 rules are configuration-backed and versioned:

| Rule family | Signal | Minimum safety behavior |
|---|---|---|
| Cancel spike | Count/amount of cancelled lines/orders in a window vs employee/branch/time baseline | Require minimum sample; exclude duplicate/rejected events; evidence lists source IDs |
| Discount spike | Manual/promotion/voucher count/amount and ratio vs comparable baseline | Separate automatic promotion from manual override; preserve version/allocation; no customer identity |
| Drawer frequency | No-sale/manual drawer opens, outside shift/expected schedule, repeated opens | Distinguish trigger and device; missing shift/timezone yields low quality, not high confidence |
| Repeated order/shift actions | Multiple cancel/discount/open actions on same order/shift within short window | Group into one deterministic finding window; do not multiply duplicate deliveries |

Rules must define `ruleVersion`, evidence window, subject dimensions, baseline source, minimum sample, warm-up behavior, threshold, severity mapping, suppression/cooldown and finding key. Rule changes do not rewrite historical findings; a new evaluation version creates a revision.

## 12. Acceptance criteria and test matrix

| Area | Required cases |
|---|---|
| P0 cancel | Successful line cancel, terminal order cancel, rejected cancel, duplicate/out-of-order, stale order version, missing reason/actor |
| P0 discount | Automatic promotion, voucher, manual override, manual missing reason, allocation partial/unallocated, promotion version change, failed/rejected discount |
| P0 drawer | Sale/refund/opening/maintenance/no-sale, duplicate device command, unknown trigger, no shift, disabled/revoked operator |
| Authorization | Owner tenant/subset, Manager assigned branch, foreign branch/resource/case, revoked membership, cursor/export scope mismatch, cache after revocation |
| Privacy | No JWT/PIN/phone/payment secret/raw provider payload in DB/API/logs/traces/events/evidence; safe reason sanitization |
| Integrity | Append-only write denial, digest generation/verification, mismatch alert, replay preserves digest lineage, legal hold blocks deletion |
| Reliability | At-least-once, inbox atomicity, schema rejection, retry/backoff, DLQ, replay/rebuild, Audit DB outage, source outbox delay |
| Query/API | 30/90-day bounds, cursor mismatch, stable sort, empty/partial/stale/unavailable, detail scope check, export 91-day+ async |
| Anomaly | Cancel/discount/drawer/repeated-action rules, insufficient baseline, warm-up, threshold/severity, duplicate finding, late revision, no auto-block |
| Case | Manager acknowledge/investigate, Owner resolve/dismiss, invalid transition, missing reason, concurrent update, reopen/revision, every transition audited |
| Performance | Event ingest lag, query p95 ≤2s bounded list/detail, 90-day max interactive, export load, seven-year archive/retention job |

## 13. Decisions resolved from Vision review

- Central Audit service is the append-only evidence owner; producers emit events through transactional outbox/EventBridge/SQS.
- Owner sees tenant/subset branch; Manager sees assigned branches; Cashier/Kitchen do not access console in MVP.
- Minimum audit/security retention is seven years with legal hold precedence.
- Discount audit covers promotion/voucher and manual override; manual override requires reason.
- Integrity MVP is database append-only controls plus batch hash digest/checkpoint.
- P2 uses explainable rule catalog + employee/branch/time baseline; no ML and no automatic blocking.
- Manager can acknowledge/investigate; only Owner can resolve/dismiss.
- Interactive audit query is bounded at 90 days; older history is asynchronous restricted export.

## 14. Parent contract amendments required

### POS Platform/Audit

- Expand `audit_events` and `AuditRecorded.v1` with event/source lineage, actor/effective operator, device/shift, outcome, redaction version and action-specific payload schemas.
- Keep `pos-audit-events` at-least-once/retry/DLQ and define source-to-Audit dedupe key.
- Add Audit permissions, API/read-model catalogue, integrity/retention ownership and restricted operation policy.

### Order

- Publish cancel and discount/override source facts with immutable order/line version, before/after allocation, actor/effective operator, reason and outcome.

### Promotion/Loyalty

- Preserve promotion/voucher/rule version and applied allocation; distinguish automatic discount from manual override.

### Payment/Shift/POS Device

- Define drawer-open producer/source-of-truth and trigger taxonomy; expose shift/device/operator context without payment secrets.
- Publish payment/refund/reconciliation and shift/cash discrepancy audit facts needed for context and anomaly rules.

### Identity

- Add `audit:read`, `loss-case:read`, `loss-case:investigate`, `loss-case:resolve`, `loss-case:dismiss` and restricted `audit:operate`; define current membership/authorization version and revoke behavior.

## 15. Requirements approval gate

- **Current status:** Approved — user accepted the Requirements review warnings.
- **Required before Architecture:** Completed; remaining parent amendments and executable fixtures are implementation gates recorded in Architecture/Contracts.
- **Implementation rule:** Không viết controller/consumer/migration/anomaly worker từ prose; materialize OpenAPI, AsyncAPI/JSON Schema, metric/rule fixtures và migration constraints trước code.
