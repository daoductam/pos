Started at: 2026/10/13 00:00:00
Finished at: 2026/10/13 00:00:00
Total time: 0 minutes
---

# Module 9 — AI Assistant: Design Finalization

**Ngày:** 2026-10-13  
**Trạng thái:** **APPROVED WITH WARNINGS**  
**Phạm vi:** Trợ lý AI cho chủ quán — AI service, LLM/DeepSeek

## 1. Executive summary

Thiết kế đã hoàn tất đủ 5 phase: Vision → Requirements → Architecture → Contracts → Finalization.

Module cung cấp:

- P1: Hỏi đáp báo cáo bằng ngôn ngữ tự nhiên qua DeepSeek function calling và Reporting tools allowlist.
- P1: Guardrail tenant/branch theo Identity online authorization, defense-in-depth tại Tool Gateway và Reporting repository.
- P1: PII masking, redacted audit, read-only boundary và fail-closed khi provider/tool không an toàn.
- P2: Daily summary theo branch business date/timezone, baseline, contribution drivers, freshness watermark và immutable revision khi có late facts.

Không có critical blocker trong thiết kế. Có thể bắt đầu implementation sau khi hoàn tất các artifact machine-readable và parent amendments được liệt kê ở §7.

## 2. Artifact registry

| Phase | Artifact | Status |
|---|---|---|
| Vision | [2026-10-13-vision.md](./2026-10-13-vision.md) | Approved |
| Vision review | [2026-10-13-vision-review.md](./2026-10-13-vision-review.md) | Warnings accepted and resolved downstream |
| Requirements | [2026-10-13-requirements.md](./2026-10-13-requirements.md) | Approved with warnings |
| Requirements review | [2026-10-13-requirements-review.md](./2026-10-13-requirements-review.md) | Warnings resolved in Architecture/Contracts |
| Architecture | [2026-10-13-architecture.md](./2026-10-13-architecture.md) | Approved |
| Sequence diagrams | [2026-10-13-sequence-diagrams.adoc](./2026-10-13-sequence-diagrams.adoc) | Generated and validated; 6 flows × L1/L2 |
| Contracts | [2026-10-13-contracts.md](./2026-10-13-contracts.md) | Approved after warning remediation |
| Contracts review | [2026-10-13-contracts-review.md](./2026-10-13-contracts-review.md) | Approved; implementation gates remain |

## 3. Final design review

### Code Review Gate

**Scope:** 8 Module 9 design artifacts plus direct parent contract references in Identity Access and POS Platform/Reporting. No application source, migration or generated schema exists yet; this is a design-quality review, not a runtime code review.

**Phase 0:** Skipped — context and conventions were established by the preceding design phases.

**Verdict:** **YELLOW — Proceed with Caution**

The design is coherent and safe to implement, but implementation must not begin from prose alone. The remaining warnings are materialization and integration gates, not unresolved product decisions.

### Critical Issues

None found.

### Warnings

| # | File / section | Category | Finding | Recommendation |
|---|---|---|---|---|
| 1 | `contracts.md` §13; parent Identity Contracts §Internal API | Integration | AI Assistant defines an additive multi-branch authorization amendment, while the current parent document still shows the original singular `branchId` shape. | Apply the approved Identity amendment and generate one canonical schema for `requestedBranchIds`, `requiredPermission`, `authorizationVersion` and `resolvedScope`. |
| 2 | `contracts.md` §6.4, §12, §14; parent POS Platform Reporting schema | Contract materialization | Reporting remains documented with a skeleton for `sales_daily`/`top_items`; AOV, hourly, branch and contribution tools are specified in Module 9 but not yet generated as shared schemas. | Publish the registry, digest, OpenAPI/JSON Schemas and golden metric fixtures before implementation. |
| 3 | `contracts.md` §4.4.1, §7.2, §12 | Safety/evaluation | Answer claim reconciliation, PII scanning and prompt-injection handling are contractually defined but not executable yet. | Build the redaction validator and evaluation corpus; fail closed on unsupported claims or unsafe output. |
| 4 | `contracts.md` §7.1–§7.3 | Operations/cost | DeepSeek region/egress, secret rotation, cost estimation and final usage reconciliation still require environment-level configuration and runbook validation. | Add provider policy fixtures, secret-manager integration, budget alerts and a provider outage runbook. |
| 5 | `contracts.md` §10–§11 | Database/concurrency | Summary revision rules are concrete, but the PostgreSQL migration and concurrent-worker tests do not exist yet. | Implement the partial unique indexes, lineage lock/CAS, lease handling and same-transaction outbox test before enabling reruns. |

### Info / suggestions

| # | Area | Suggestion |
|---|---|---|
| 1 | Product rollout | Release P1 chat behind a tenant/role feature flag before enabling P2 daily summaries. |
| 2 | Model quality | Track answer-fact mismatch, refusal rate, tool-call success and provider cost by policy/model version. |
| 3 | UX | Render scope, period, freshness and source provenance as first-class UI elements rather than embedding them only in prose. |

### Checklist summary

| Category | Verdict | Result |
|---|---|---|
| Security | PASS WITH GATES | Tenant/branch defense-in-depth, read-only tools, PII prohibition, fail-closed provider/output policy and audit references are specified; executable negative tests remain. |
| Business logic | PASS WITH GATES | Metric ownership, AOV formula, baseline, contribution wording, `DATA_LATE` and immutable supersede are specified; Reporting fixtures remain to be generated. |
| Performance | PASS | Tool p95 ≤2s, answered chat p95 ≤8s, timeout ≤10s, refusal p95 ≤500ms, bounded tool loop and pagination are specified. |
| Clean design | PASS | Service ownership, data boundaries, state machines, outbox/inbox and no shared database are clear. |
| Conventions | PASS WITH GATES | REST/OpenAPI 3.1, UUID v7, UTC, common envelopes, AsyncAPI and PostgreSQL/Flyway conventions align with parent platform; parent amendments must be applied. |

## 4. Architecture summary

```text
Owner/Manager Dashboard
          |
          v
AI Assistant API
  ├─ Identity Resolver + Scope/Policy Engine
  ├─ Turn Orchestrator + Answer Validator
  ├─ Tool Gateway ──> Reporting Tool API ──> Reporting read model
  ├─ DeepSeek Adapter
  ├─ AI PostgreSQL / Outbox / Audit references
  └─ Summary Scheduler/Worker ──> Reporting freshness/completeness
```

Authoritative boundaries:

- Identity owns user, role, membership and current authorization decision.
- Reporting owns revenue facts, aggregate metrics, freshness/completeness and contribution calculation.
- AI Assistant owns orchestration, safe semantic context, provider policy, validated answer/summary presentation and revision lineage.
- `OrderPaid.v1` remains the canonical paid-order fact; AI Assistant does not calculate revenue from raw Order/Payment data.

Critical runtime rules:

- Every protected conversation/turn resolves current authorization online.
- LLM receives only redacted question, structured context, tool schemas and aggregate facts.
- Maximum three tool calls per turn, one repair, one provider retry and bounded timeout.
- A synchronous turn returns within the target deadline or switches to `202 + polling`.
- Daily summary waits for Reporting completeness, uses same-weekday baseline with 7-day fallback, and creates a new immutable revision when the source watermark advances.

## 5. Requirements traceability

| Requirement | Design evidence | Acceptance gate |
|---|---|---|
| P1 natural-language report Q&A | Requirements FR-AI-001..008; Contracts public conversation/turn APIs; DeepSeek adapter; seven Reporting tools | Owner/Manager receives a validated answer or safe structured state with scope, period, freshness and provenance |
| P1 branch guardrail | Identity decision API; immutable scope revision; Tool Gateway and Reporting repository predicates | Manager cannot read foreign branch through IDOR, prompt injection or forged branch argument |
| P1 PII masking | Requirements FR-AI-009; safe answer/summary schema; no raw prompt/provider response retention | No phone, customer name, raw transaction ID, secret or SQL in provider payload, logs, events or AI DB |
| P1 read-only boundary | Allowlisted tools contain only aggregate reads; no mutation tools | Mutation requests are refused without domain side effects |
| P2 daily summary | Summary scheduler/worker, cutoff +15m, completeness watermark, baseline and contribution tools | Summary is published only with required data and has source/model/policy provenance |
| P2 late facts | `DATA_LATE`, source watermark version, immutable revision and supersede event | Late OrderPaid/refund/correction creates a traceable newer revision without overwriting history |

## 6. Implementation roadmap

### Phase 0 — Contract materialization and parent amendments

Deliverables:

- Apply Identity additive authorization amendment.
- Extend Reporting contract with AOV, hourly, branch comparison, contribution read models and metric dictionary.
- Generate public/internal OpenAPI 3.1, tool registry/JSON Schemas and AsyncAPI event schemas.
- Generate safe answer/summary schemas, provider policy schema and redaction fixtures.
- Create PostgreSQL/Flyway migrations with column comments, tenant constraints, revision indexes, leases and outbox/inbox.

Exit criteria: schema compatibility checks pass; no unresolved field ownership or version mismatch remains.

### Phase 1 — AI foundation and authorization

Deliverables:

- AI API shell, Cognito/workload identity integration and online Identity decision client.
- Scope/policy engine with Owner tenant scope, Owner branch subset and Manager assigned-branch scope.
- Conversation/scope revision/turn persistence, idempotency, TTL and redacted audit reference.
- Structured semantic context builder with no raw transcript persistence.

Exit criteria: cross-tenant/cross-branch negative tests pass; revoked membership fails on the next protected request.

### Phase 2 — P1 report chat

Deliverables:

- Tool registry and Tool Gateway validation.
- Reporting adapter for all seven allowlisted tools.
- DeepSeek adapter, provider policy/budget reservation, circuit breaker and response validator.
- Synchronous response plus `202` polling fallback.
- Dashboard provenance/freshness/safe error rendering.

Exit criteria: golden metric fixtures match Reporting; prompt-injection/PII corpus passes; p95 and cost budgets pass under load.

### Phase 3 — P2 daily summary

Deliverables:

- Branch-local scheduler and business-day cutoff handling.
- Completeness wait/`DATA_LATE` state, baseline fallback and contribution wording policy.
- Summary worker, immutable revision lineage, supersede event and rerun idempotency.
- Summary list/detail/rerun APIs and dashboard presentation.

Exit criteria: duplicate workers cannot publish competing current revisions; late-fact replay is deterministic and auditable.

### Phase 4 — Security, evaluation and controlled rollout

Deliverables:

- Security review for IDOR, prompt injection, PII egress, secret handling and provider region policy.
- Model evaluation set: fact mismatch, unsupported claims, refusal, no-data, stale data and Vietnamese wording.
- Observability dashboards/alerts for latency, freshness, refusal, provider errors, cost and revision lag.
- Feature flag rollout: internal owner pilot → selected tenants → broader manager access.

Exit criteria: operational runbook approved, alert thresholds validated, rollback/disable-provider switch tested.

## 7. Implementation gates and ownership

| Gate | Owner | Must be complete before |
|---|---|---|
| Identity authorization amendment and fixtures | Identity Access | Any protected AI endpoint |
| Reporting tool schemas, metric dictionary and fixtures | Reporting | Any tool implementation or answer evaluation |
| OpenAPI/AsyncAPI/JSON Schema generation | AI platform/API owner | Controller, adapter or event consumer code |
| Safe-output validator and PII corpus | AI/Security | DeepSeek traffic or persistence |
| Provider policy, secret/egress and budget runbook | Platform/Security/Finance | Provider enablement |
| PostgreSQL revision/lease/outbox migrations | AI service owner | Summary worker/rerun enablement |
| Observability and audit consumer schemas | Audit/Observability | Production rollout |

## 8. Operational SLO and observability baseline

- Reporting tool p95 ≤ 2s for standard aggregate queries.
- Answered chat p95 ≤ 8s when provider is healthy; hard timeout ≤ 10s.
- Scope/refusal p95 ≤ 500ms without provider call.
- Daily summary published or terminally classified within 10 minutes after data readiness; maximum completeness wait 30 minutes.
- Metrics: tool latency/error, turn state transitions, answer validation failures, refusal reasons, scope denials, freshness/late-data rate, provider token/cost usage, budget exhaustion, summary revision lag and DLQ depth.
- Logs/traces contain correlation IDs and redacted summaries only; raw prompts, provider payloads, JWTs, phone numbers and secrets are prohibited.

## 9. Final decision

**APPROVED WITH WARNINGS.**

The design is ready for implementation planning and controlled execution. The warnings are explicit pre-code gates and must be closed through generated contracts, parent amendments, security/evaluation fixtures and migration tests. No production provider traffic or daily-summary publication should be enabled before those gates pass.

**Next action:** Start Phase 0 contract materialization and create the implementation backlog from this roadmap.

## 10. Skills and workflow references

- `.agent/workflows/design.md`
- `.agent/skills/brainstorming/SKILL.md`
- `.agent/skills/spec-reviewer/SKILL.md`
- `.agent/skills/api-design/SKILL.md`
- `.agent/skills/database-design/SKILL.md`
- `.agent/skills/log-processing/SKILL.md`
- `.agent/skills/sequence-diagram/SKILL.md`
- `.agent/skills/code-reviewer/SKILL.md`

