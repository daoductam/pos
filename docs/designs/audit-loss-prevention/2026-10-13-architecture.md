Started at: 2026/10/13 00:00:00
Finished at: 2026/10/13 00:00:00
Total time: 0 minutes
---

# Module 11 — Giám sát & chống thất thoát: Architecture

**Ngày:** 2026-10-13  
**Trạng thái:** Approved — user approved Architecture after sequence confirmation  
**Vision:** [Audit & Loss Prevention Vision](./2026-10-13-vision.md)  
**Requirements:** [Audit & Loss Prevention Requirements](./2026-10-13-requirements.md)  
**Requirements review:** [Audit & Loss Prevention Requirements Review](./2026-10-13-requirements-review.md)  
**Sequence diagrams:** [Audit & Loss Prevention Sequence Diagrams](./2026-10-13-sequence-diagrams.adoc)

## 1. Architecture decision summary

Audit & Loss Prevention là một service tập trung, read/evidence-oriented, sở hữu database riêng. Source service phát action-specific business/security facts qua transactional outbox; Audit normalize và lưu evidence append-only. P2 anomaly worker chỉ tạo finding/case, không tham gia synchronous business path và không có quyền chặn nguồn.

| Decision | Choice | Rationale |
|---|---|---|
| Audit ownership | Dedicated Audit service + PostgreSQL | Một nguồn evidence/query/retention thống nhất; không để từng service tự định nghĩa timeline riêng |
| Canonical source facts | Action-specific source events là canonical cho business mutation; Audit tạo normalized evidence | Giữ Order/Payment/Shift/POS Device là owner state và tránh double evidence qua generic event |
| Generic audit route | `AuditRecorded.v1` dùng cho generic security/operational events không có source business event tương ứng | Tương thích parent platform nhưng không dùng thêm cho cùng một cancel/discount/drawer operation |
| Ingestion | EventBridge → SQS `pos-audit-events` → Audit consumer/inbox | At-least-once, retry/DLQ, source decoupling và replay |
| Storage | Immutable `audit_events` + query projections + case/action tables | Tách forensic evidence khỏi filter/query hot path; corrections append-only |
| Integrity | Append-only DB permissions/checks + SHA-256 batch digest/checkpoint | Đạt MVP tamper-evidence mà không phụ thuộc external WORM; digest có lineage/verification |
| Query | REST/OpenAPI 3.1, bounded interactive 90 days, async export older history | Bảo vệ latency/retention data; scope-safe API |
| P2 detection | Versioned rules + employee/branch/time baselines in PostgreSQL | Explainable, deterministic và phù hợp cold start; chưa dùng ML |
| Case workflow | Append-only transitions + optimistic CAS; Manager investigate, Owner resolve/dismiss | Separation of duties và không mất forensic lineage |
| Archive/export | Encrypted object storage for bounded export/archive artifacts, audited lifecycle | Không trả file lớn qua request; legal hold/expiry được quản lý riêng |
| Authorization | Identity online decision + repository predicates + cache scope/version | Defense-in-depth tenant/branch isolation |
| Time/IDs | UUID v7, UTC timestamps, branch-local business windows | Nhất quán platform và anomaly baseline đúng ca/giờ địa phương |

## 2. Service boundary and component map

```text
Order / Promotion / Payment / Shift / POS Device / Identity
  └─ transactional outbox
        └─ EventBridge
              └─ SQS pos-audit-events + DLQ
                    └─ Audit Ingestor
                         ├─ Schema/Redaction Validator
                         ├─ Inbox + Dedupe
                         ├─ Immutable Evidence Writer ──┐
                         ├─ Projection Writer             │
                         └─ Watermark/Completeness       │
                                                         ▼
                                                   Audit PostgreSQL
                                                         │
             ┌───────────────────────────────────────────┼────────────────┐
             ▼                                           ▼                ▼
       Query API + Policy                         Digest Worker     Loss Worker
             │                                           │                │
       Angular Audit Console                      digest/checkpoints  rules/baselines
             │                                                            │
             └───────────────────────────────┐                            ▼
                                             ▼                     Findings/Case Service
                                      Identity decision                     │
                                             │                              ▼
                                      Scope-safe response             Owner/Manager console
```

### 2.1 Components

| Component | Responsibility | Must not do |
|---|---|---|
| Source producers | Emit action-specific audit facts in source outbox after commit | Write Audit DB or invent current Identity authority |
| EventBridge/SQS | Route, retry, retain, DLQ and expose delivery metadata | Mutate evidence or reorder source facts silently |
| Audit Ingestor | Validate envelope/schema/redaction, dedupe, persist evidence/projection atomically | Re-evaluate business rules or infer successful mutation from logs |
| Immutable Evidence Writer | Insert append-only normalized evidence and source lineage | Update/delete historical event rows |
| Query Projection Writer | Build filter-safe denormalized timeline/index views and data-quality state | Become a second source of truth |
| Watermark/Completeness | Track source/projection checkpoint, gaps, lag and missing source | Mark missing source as clean zero/no activity |
| Query API + Policy | Resolve authorization, validate filters, query projections/evidence and redact response | Accept client tenant/branch/actor as authority |
| Digest Worker | Canonicalize ordered event batch, compute/verify SHA-256 digest/checkpoint | Repair mismatch by rewriting evidence |
| Retention/Export Worker | Enforce retention/legal hold, create encrypted async exports/archive | Delete held data or expose unbounded synchronous export |
| Loss Prevention Worker | Materialize baseline, evaluate versioned rules, create/revise finding | Block Order/Payment/Shift/POS operation |
| Case Service | CAS state transitions, evidence snapshot references and case actions | Resolve/dismiss without Owner permission/reason |
| Identity Scope Client | Resolve current permission/scope/version | Store or become historical actor authority |
| Audit PostgreSQL | Own evidence, projections, policies, findings, cases and operations | Be shared by source services |

## 3. Canonical event and ownership architecture

### 3.1 Source-of-truth rule

For a business mutation, the action-specific source event is canonical:

```text
Order.cancel line / apply discount
  → Order transaction + outbox(OrderLineCancelled / DiscountApplied)
  → Audit consumes and normalizes one AuditEvidence row

POS Device/Shift drawer operation
  → Drawer operation/result transaction + outbox(CashDrawerOpened)
  → Audit consumes and normalizes one AuditEvidence row

Generic authorization/security operation
  → source transaction + outbox(AuditRecorded.v1)
  → Audit consumes one AuditEvidence row
```

The same source operation must not publish both an action-specific audit fact and a second generic `AuditRecorded.v1` that Audit consumes. If parent Platform keeps `AuditRecorded.v1` as a compatibility envelope, it carries `sourceEventId`/`sourceEventType` and the registry marks which route is canonical.

### 3.2 Normalization boundary

Audit normalizes producer-specific payloads into `AuditEvidence` with:

- common tenant/branch/source/aggregate/event lineage;
- actor and effective operator snapshots;
- action/resource/outcome/reason;
- bounded before/after summaries;
- money/allocation as integer minor units + `currency`;
- device/shift/order/line/promotion version references;
- `redactionVersion`, `normalizationVersion` and `qualityCode`.

Normalized evidence is an internal Audit representation. Downstream consumers use `AuditEvidenceRecorded.v1` only for safe references/summary; it is never an authorization command or revenue source.

## 4. Data architecture

### 4.1 PostgreSQL ownership

Audit uses a dedicated PostgreSQL database. All tables carry `tenant_id`; branch-scoped rows carry `branch_id`; repository queries always include tenant and resolved branch predicates.

| Table/aggregate | Purpose | Mutability |
|---|---|---|
| `audit_events` | Canonical normalized evidence, source lineage, redacted payload summary | Insert-only; correction is new linked row |
| `audit_event_links` | Related source/correction/revision/evidence references | Insert-only |
| `audit_event_projection` | Query-optimized timeline/index row and safe labels | Rebuildable derived state |
| `inbox_events` | At-least-once dedupe, source version/status/error | State transition with immutable processing history/reference |
| `projection_watermarks` | Source/projection lag, completeness, last applied checkpoint | Current pointer + transition telemetry |
| `digest_batches` | Batch range, canonicalization/version, SHA-256 digest, previous digest/checkpoint, verification status | Batch metadata append-only; status adds verification record |
| `audit_policies` | Redaction, action allowlist, retention and query policy versions | Versioned publish; no mutation of historical policy |
| `anomaly_rule_versions` | Rule family/config/threshold/baseline dimensions/severity | Immutable published versions |
| `anomaly_baselines` | Employee/branch/time-window statistics and warm-up state | Versioned materialization |
| `anomaly_findings` | Deterministic finding key, rule/evidence/baseline references, severity/status | Revisioned; no destructive delete |
| `case_actions` | Append-only acknowledge/investigate/resolve/dismiss transitions | Insert-only |
| `audit_exports` | Async export operation/status/object reference/expiry | State machine + audit trail |
| `legal_holds` | Scope/data class/owner/reason/active period | Versioned lifecycle |
| `retention_operations` | Archive/delete/hold evaluation and outcome | Append-only operation evidence |
| `outbox_messages` | Produced Audit/finding/case/health events | Standard outbox state |

### 4.2 Partitioning and indexes

- `audit_events`, `audit_event_projection`, `case_actions` and high-volume `inbox_events` are designed for monthly RANGE partitioning by `occurred_at`/`created_at`; activate/size partitions after event-volume measurement, while preserving seven-year lifecycle controls.
- Primary/unique keys include partition date where PostgreSQL requires it; global event uniqueness is enforced through a dedicated source-key registry or scoped unique index strategy.
- Minimum indexes:
  - `(tenant_id, branch_id, occurred_at DESC, id)` for timeline;
  - `(tenant_id, branch_id, action, occurred_at DESC)` for action filters;
  - `(tenant_id, branch_id, actor_key, occurred_at DESC)` for employee investigation;
  - `(tenant_id, branch_id, shift_id, occurred_at DESC)` for shift/device investigation;
  - `(tenant_id, status, severity, detected_at DESC)` for findings/cases;
  - unique `(source_name, source_event_id)` and applicable `(tenant_id, source_key)` for dedupe;
  - `(tenant_id, scope_key, projection_name, watermark_version)` for health/replay;
  - `(tenant_id, active_from, active_to, data_class)` for legal holds.
- Every migration column requires `COMMENT ON COLUMN`; no source service has a foreign key into Audit DB.

### 4.3 Digest architecture

For each tenant/scope/time batch:

1. Select committed normalized event rows in deterministic tuple order `(occurred_at, event_id)`.
2. Serialize an allowlisted canonical field set using `canonicalizationVersion`.
3. Compute SHA-256 over ordered event records; include `previousDigest` when chaining batches for the same scope.
4. Persist `digest_batches` with event count, first/last event IDs, source/projection watermark, algorithm/version, digest and creation time.
5. Verification worker recomputes a bounded batch and records `VERIFIED` or `MISMATCH`; mismatch creates a security finding and does not rewrite evidence.

Digest is integrity evidence, not authorization. Rebuilds create a new projection version and reference the same immutable evidence/digest lineage.

## 5. Communication architecture

### 5.1 Asynchronous ingest

- Source services write source mutation and outbox in one local transaction.
- EventBridge routes action/security events to `pos-audit-events`.
- SQS delivers at-least-once to Audit consumer; bounded exponential retry then `pos-audit-events-dlq`.
- Audit acknowledges only after inbox dedupe, normalized evidence, projection update and watermark transaction commits.
- Unknown schema version or failed redaction is quarantined; no best-effort field guessing.

### 5.2 Synchronous query/case path

- Angular calls Audit API with Cognito JWT; Identity scope decision is resolved before cache/database access.
- Query API validates bounded filters and maps to repository predicates; event detail/evidence/case fetch repeats scope check.
- Case mutation uses idempotency key + `If-Match`/CAS version; successful transition writes `case_actions`, current case pointer and outbox in one transaction.
- Audit query failure does not block source mutations; source outbox and queue preserve evidence intent.

### 5.3 Export/archive/retention

- Export command creates an idempotent `audit_exports` operation with explicit tenant/branch/data class/date range.
- Worker re-checks authorization/holds, streams bounded redacted rows to encrypted object storage, writes checksum/expiry and emits completion event.
- Download uses short-lived authorized reference; no public bucket or unbounded object URL.
- Retention worker evaluates legal hold before archive/delete; every operation writes `retention_operations` and audit evidence.

## 6. Anomaly architecture

```text
Audit evidence + approved Order/Payment/Shift facts
        │ normalized event stream / query projection
        ▼
Feature Aggregator
  ├─ employee/branch/time-window counters
  ├─ monetary/count ratios
  ├─ shift/device context
  └─ data-quality/warm-up state
        ▼
Versioned Rule Evaluator
  ├─ cancel spike
  ├─ discount spike
  ├─ drawer frequency/outside shift
  └─ repeated order/shift actions
        ▼
Finding Writer (idempotent key + evidence snapshot)
        ▼
Case Service → Owner/Manager workflow
```

### 6.1 Baseline policy

- Baselines are branch-local and timezone-aware; default dimensions are effective operator, branch, local hour/daypart and action family.
- A rule policy declares warm-up duration, minimum sample, comparison window, threshold, severity map, suppression/cooldown and `ruleVersion`.
- New employee/branch/shift or missing source facts produce `INSUFFICIENT_BASELINE`; the worker does not produce high-confidence findings.
- Finding key is deterministic over `(tenant, branch, subject, ruleVersion, observationWindow, evidenceVersion)`.
- Late facts create a linked revision/evidence version; previous finding remains available for audit.

### 6.2 Case state machine

```text
OPEN → ACKNOWLEDGED → INVESTIGATING → RESOLVED
                         │             └→ OPEN (reopen by Owner with reason)
                         └────────────→ DISMISSED (Owner + reason)
```

- Manager may transition `OPEN → ACKNOWLEDGED → INVESTIGATING` only within assigned branch.
- Owner may perform all transitions and is required for `RESOLVED`/`DISMISSED`.
- Every transition uses CAS version and creates an immutable `case_actions` row plus `AuditCaseActionRecorded.v1`.
- Case status never deletes findings/evidence; resolution notes are bounded/redacted.

## 7. Security and privacy architecture

- Identity is online authority; Audit stores historical actor/membership snapshots only for display/forensics.
- Authorization order: verify JWT/workload → resolve current permission/scope → normalize filter → cache lookup (if enabled) → repository tenant/branch predicate → response redaction.
- No public API accepts tenant/branch/actor/outcome/before-after authority fields.
- Audit API does not expose raw source payload, phone, PIN, JWT, VNPAY signature, payment credentials or full customer/order notes.
- Cache, if enabled for timeline/findings list, is short-lived (≤15s), keyed by scope hash + authorization version + filter hash; no browser/CDN cache for protected evidence.
- Audit access denial, export, replay, digest verification, legal hold and case action are themselves evidence.
- Operator/replay credentials use workload identity/mTLS and least-privilege `audit:operate`; no direct SQL from POS or public client.

## 8. Failure, consistency and recovery

| Failure | Architecture response |
|---|---|
| Producer outbox delay | Source remains committed; Audit quality/watermark shows lag when event eventually arrives; reconciliation alerts on gap |
| Duplicate delivery | Inbox unique source key/event ID makes no-op; SQS ack after transaction |
| Out-of-order source version | Keep newer evidence; hold/gap queue or replay according to producer version policy; never overwrite history |
| Schema/redaction failure | Quarantine/DLQ; safe error and producer alert; no unsafe payload persisted |
| Audit DB unavailable | Retry/DLQ; API returns unavailable; source services remain independent |
| Projection corruption | Rebuild projection from immutable `audit_events`; source evidence/digest is not rewritten |
| Digest mismatch | Freeze verification result, create integrity finding, preserve rows and require restricted investigation |
| Rule worker failure | Keep previous baseline/finding state; retry/DLQ; no source operation impact |
| Concurrent case action | CAS/`If-Match` returns `409`; no partial action or duplicate transition |
| Legal hold conflict | Hold wins; retention job records skipped operation and alert |

## 9. Observability and operations

### Metrics

- `audit_ingest_lag_seconds`, `audit_projection_lag_seconds`, `audit_missing_source_count`;
- `audit_event_processed_total`, duplicate, schema rejection, redaction rejection, retry and DLQ counts;
- digest batch count, verification latency, mismatch count and unverified age;
- API p50/p95, 401/403/404/409/422/429/503 rates, export duration/expiry/failure;
- anomaly evaluations, insufficient baseline, findings by rule/severity, duplicate/revision/dismiss rate;
- case transition conflict/denial and legal-hold/retention operation outcomes.

### Logs/traces

- Structured logs contain correlation/causation, tenant/branch safe reference, event/source ID, operation and outcome.
- Do not log raw event payload, reason text without redaction, JWT/PIN, phone, payment signature or export content.
- Trace spans cover producer outbox, EventBridge/SQS delivery, ingest transaction, query authorization, digest worker, rule evaluation and case transition.

### Alerts/runbooks

- Alert on audit lag >60 seconds, DLQ growth, missing source gap, integrity mismatch, unverified digest age, export leakage/failure, authorization denial spike and high schema rejection.
- Runbooks cover replay by source window, projection rebuild, digest verification, legal hold, export revocation, DLQ quarantine and privacy incident response.

## 10. ADRs

| ADR | Decision | Alternatives rejected | Reason |
|---|---|---|---|
| AUD-ADR-01 | Dedicated Audit service owns evidence/query/cases | Each service owns private audit; Reporting owns audit | Consistent retention, scope and forensic timeline |
| AUD-ADR-02 | Source action events canonical; Audit normalizes one evidence row | Dual source event + generic AuditRecorded; Audit infers from logs | Prevent double evidence and preserve source ownership |
| AUD-ADR-03 | PostgreSQL immutable evidence + projections | Shared DB; event-only storage without query model | Queryable 90-day UX with rebuildable read path |
| AUD-ADR-04 | Append-only + batch SHA-256 digest/checkpoint | External WORM in MVP; unverified application-only append | Meets MVP integrity need with manageable AWS/PostgreSQL operations |
| AUD-ADR-05 | Rules + statistical baseline, no ML/auto-block | ML-first; autonomous blocking | Explainability, cold start and safety for P2 |
| AUD-ADR-06 | Async 7-year export/archive; 90-day interactive query | Synchronous seven-year query | Protects latency and resource isolation |
| AUD-ADR-07 | Case actions append-only with Owner resolution authority | Mutable case row only; Manager full resolution | Separation of duties and auditability |

## 11. Sequence diagram candidates

Sequence diagrams should be generated after confirming the following flow set and participants:

| # | Flow | L1 participants | L2 participants |
|---|---|---|---|
| 1 | Successful order cancel → audit evidence | Manager/POS, Order, EventBridge/SQS, Audit, PostgreSQL | Audit API/consumer, schema-redaction validator, inbox, evidence writer, DB/outbox |
| 2 | Manual discount/promotion → audit evidence | POS/Order/Promotion, EventBridge/SQS, Audit | source event adapter, normalizer, allocation validator, evidence/projection writer |
| 3 | Cash drawer command/result → audit evidence | Cashier/POS Device, Shift, EventBridge/SQS, Audit | drawer operation adapter, result reconciler, inbox/evidence writer |
| 4 | Owner/Manager query timeline with scope denial | Angular Console, Audit API, Identity, PostgreSQL | Policy, cache, query service, repository/DB |
| 5 | P2 anomaly detection → finding/case | Audit/Order/Payment/Shift facts, Loss Worker, Audit DB, Case/Console | feature aggregator, rule evaluator, finding writer, case transition store |
| 6 | Owner/Manager case transition | Console, Identity, Audit API, Audit DB, Audit event bus | controller/policy, CAS case service, case actions, outbox |
| 7 | Digest verification mismatch | Scheduler/Ops, Digest Worker, Audit DB, Alert/Audit | batch selector, canonicalizer, verifier, integrity finding writer |
| 8 | Seven-year export/legal hold/replay | Owner/Ops, Audit API, Identity, Export/Retention Worker, PostgreSQL/Object Storage | operation state, hold evaluator, stream/export writer, audit/outbox |

The sequence artifact will contain L1 and L2 PlantUML blocks and will not introduce implementation participants beyond these confirmed design boundaries.

## 12. Architecture approval gate

- **Current status:** Approved — user confirmed the eight sequence flows and participant boundaries.
- **Required before Contracts:** Completed; contract materialization and remaining parent amendments are implementation gates, not unresolved architecture decisions.
- **Implementation rule:** Chưa viết code; Contracts phải materialize API/event schemas, migration/index/partition rules, rule policy and state-transition fixtures.
