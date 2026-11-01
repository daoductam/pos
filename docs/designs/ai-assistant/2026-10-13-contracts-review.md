Started at: 2026/10/13 00:00:00
Finished at: 2026/10/13 00:00:00
Total time: 0 minutes
---

# Spec Sentinel Report

**Spec:** [`docs/designs/ai-assistant/2026-10-13-contracts.md`](./2026-10-13-contracts.md)  
**Date:** 2026-10-13  
**Status:** ✅ Approved (design warnings remediated; implementation artifact gates remain)

## Review context

- Phase 0 skipped — reusing the approved Vision, Requirements, Architecture and sequence context from the current design workflow.
- Repository scan found no application source or migration code yet; the review therefore checks design artifacts and parent contracts rather than concrete call sites.
- The contract document covers public/internal REST, Reporting tools, DeepSeek adapter policy, events, PostgreSQL schema, retention and contract tests.

## Findings

### Warnings

| # | Category | Finding | Risk | Recommendation |
|---|---|---|---|---|
| 1 | Parent contract alignment | AI Assistant's Identity request uses `subjectId`, `requestedBranchIds`, `permission`, while the parent Identity contract currently names the fields `subject`, `branchId`, `requiredPermission` and does not expose the same resolved authorization fields in the shown response. | Generated clients can disagree about field names or silently omit branch/version context; authorization may fail closed or, worse, be implemented inconsistently. | Add an explicit additive amendment or versioned adapter mapping in Identity; publish one machine-readable schema and fixtures for tenant scope, branch scope, denial, revocation and version propagation. |
| 2 | Reporting contract completeness | The parent Reporting contract currently documents `sales_daily` and `top_items`, while this contract adds `sales_by_branch`, `sales_by_hour`, `aov` and `contribution_drivers` plus seven tool response schemas only in prose. | Different services may calculate AOV, hourly buckets, refund treatment or contribution ratios differently; AI output can drift from the dashboard. | Before code, publish Reporting-owned OpenAPI/JSON Schemas, metric dictionary, `calculationVersion` rules and golden fixtures for empty data, refunds/corrections, timezone, ties and late facts. |
| 3 | Dynamic tool route | `POST /internal/v1/reporting/ai-tools/{toolName}` is intentionally dynamic, but the contract does not yet define how the same registry version, schema digest and enabled-state are atomically distributed to AI Assistant and Reporting. | A tool can be accepted by one service and rejected by the other, or an old argument schema can reach a newer implementation. | Keep the route behind a generated registry; require `toolVersion` plus registry/schema digest, reject mismatches, and test unknown/disabled/version-skew cases. |
| 4 | Provider governance | `dailyBudgetMinor` now has `budgetCurrency`, but cost accounting, tenant/platform default precedence, concurrent reservation and provider request/attempt correlation are not machine-readable. | Budget enforcement can race under concurrent turns, and provider spend may be difficult to reconcile or audit. | Define a provider usage record or atomic budget-reservation contract with `providerRequestId`, `attemptId`, estimated/final cost, currency, policy version and reservation outcome. |
| 5 | Safe-output boundary | `answer_payload` and `safe_content` are described as validated/redacted, but the contract does not provide their JSON Schemas or a normative validator/redaction failure response shape. | Raw model prose or hidden identifiers could be persisted or rendered if an implementation treats validation as advisory. | Add versioned `AnswerSchema`, `SummaryContentSchema` and redaction invariant fixtures; commit/publish only after schema validation and PII/ID scanning, otherwise fail closed with a typed safe error. |
| 6 | Summary revision constraint | “One current summary revision” and monotonic watermark rules are stated, but the exact partial unique index, transaction ordering and concurrent rerun behavior are not specified. | Two workers can publish competing revisions or mark the wrong revision as superseded during late-fact reruns. | Define the SQL constraints/locking protocol: immutable revision insert, compare-and-swap on source watermark, single-current partial index or current-pointer row, and idempotent outbox write in the same transaction. |
| 7 | Event schema detail | Event payload minimums are listed, but the produced event schemas do not yet define the exact actor/audit reference, safe scope summary, status enum, source watermark object and aggregate-version semantics. | Audit consumers may infer different meanings, and replay/deduplication may not preserve authorization or revision lineage. | Add AsyncAPI/JSON Schema payloads with required fields, nullable rules, enum values, `aggregateVersion`, `scopeSummary`, `auditReferenceId` and compatibility fixtures. |
| 8 | Retention and deletion | Retention is intentionally configurable, but the contract does not yet map each table/field to deletion, legal hold, tombstone and provider-training exclusion behavior. | A deletion request could remove evidence that must be retained, or retained structured context could be reused for training contrary to policy. | Publish a retention matrix by data class/table, with legal-hold precedence, deletion job idempotency, tombstone semantics and an explicit `not_for_training` invariant. |
| 9 | Error/async semantics | Public turn `200`/`202` and polling are described, but the exact response schema for `202`, `Location`/retry metadata, terminal provider failure and structured-facts fallback is not fully materialized. | Dashboard clients may render incomplete prose or poll forever after `DATA_LATE`, provider failure or validation failure. | Add OpenAPI examples for every turn terminal state and queued state, including `pollAfterMs`, `expiresAt`, `safeFacts`, terminal error code and no-final-answer invariant. |
| 10 | Metric fixture semantics | `sales_daily.order_count` is the parent field, while the AI contract calls it `paidOrderCount` and relies on unique finalized `OrderPaid.v1`; approved corrections/refunds and late settlements are referenced but not fully fixture-defined. | AOV and revenue answers may not match Reporting when a payment is late, refunded or corrected after projection. | Add canonical fixtures for duplicate `OrderPaid`, payment-late settlement, full/partial refund, correction, projection rebuild and branch-local business-date boundaries; expose the metric policy version in every tool result. |

## Cross-Impact Analysis

| Dimension | Finding | Risk Level | Recommendation |
|---|---|---|---|
| **Shared Logic** | No application symbols or call sites exist yet. The proposed shared boundaries are Identity authorization, Reporting metric tools and parent event envelope. | 🟡 Medium | Treat parent contracts as versioned dependencies; add generated-client and compatibility tests before implementation. |
| **Data/State** | AI-owned conversations, turns, tool executions, summary jobs/revisions, provider policies and audit references introduce encrypted/redacted state plus immutable summary lineage. | 🟡 Medium | Materialize migrations with check constraints, tenant predicates, partial/current-revision uniqueness, retention jobs and `COMMENT ON COLUMN`. |
| **API Contracts** | Public chat/summary APIs and internal Identity/Reporting/job/provider endpoints are new; the Identity field-name mismatch and dynamic tool route need one canonical schema. | 🟡 Medium | Generate OpenAPI 3.1 from canonical schemas and run consumer/provider compatibility tests in CI. |
| **Flow Integrity** | Chat flow is auth → scope → tool loop → validation → answer/poll; summary flow is cutoff → completeness → baseline/contribution → provider → publish/revision. Failure states are covered, but async terminal payloads and concurrent revision writes need concrete schemas. | 🟡 Medium | Add L1/L2 contract fixtures for refusal, timeout, stale data, `DATA_LATE`, rerun and supersede; test state transitions transactionally. |
| **Cross-Cutting** | Branch/tenant isolation, prompt injection, PII masking, provider egress, budgets, audit and observability are explicitly in scope. | 🟡 Medium | Enforce defense-in-depth in Identity, AI policy, Tool Gateway and Reporting repository; scan logs/traces/events/provider payloads in security tests. |

## Instruction Compliance

| Dimension | Verdict | Deviation (if any) | Recommendation |
|---|---|---|---|
| **Conventions** | ✅ | REST/OpenAPI 3.1, UUID v7, UTC, common envelopes, cursor pagination, at-least-once events and service-owned PostgreSQL are followed. | — |
| **Architecture** | ✅ | The design preserves Reporting as metric owner, Identity as online authorization authority and AI Assistant as read-only orchestration boundary. | Resolve the parent Identity schema alignment and generated registry/version handshake before implementation. |
| **Testing** | ⚠️ | A broad matrix exists, but several required fixtures are named rather than materialized as schemas/examples. | Create OpenAPI/AsyncAPI/JSON Schema fixtures and migration/state-transition tests as implementation prerequisites. |

## Verdict

The Contracts document is sufficiently complete to enter Finalization. The design-level warnings have been remediated in the revised Contracts document. No code should be written directly from prose; the remaining items are implementation artifacts and parent-contract changes, not unresolved design ambiguity.

## Post-remediation verification

| Original warning | Remediation in Contracts | Verification |
|---|---|---|
| Identity field mismatch | Canonical `subject`, `requestedBranchIds`, `requiredPermission`, response envelope and version fields | §5 and Identity amendment |
| Reporting schemas/metric drift | Registry version/digest, wrapped tool result, metric dictionary and fixture IDs | §6.2–§6.4 and §13 Reporting |
| Dynamic tool route | Registry digest handshake and atomic promotion rule | §6.1 and §6.4 |
| Provider governance/budget race | Provider request/attempt IDs, reservation result, usage table and concurrency rule | §7.3 and §10.9 |
| Unsafe answer/summary persistence | Normative safe payloads, claim reconciliation, PII/identifier scanning and fail-closed behavior | §4.4.1 and §4.5 |
| Summary revision race | Partial unique indexes, watermark ordering, lineage lock and same-transaction outbox | §11.1 |
| Event schema detail | `scopeSummary`, `aggregateVersion`, `auditReferenceId` and AsyncAPI requirement | §9 |
| Retention/deletion ambiguity | Data-class retention matrix, legal hold and training exclusion | §11.3 |
| Async terminal semantics | Normative `202` polling body, bounded polling and terminal-state rules | §4.4 |
| Metric fixtures | Canonical definitions and fixture IDs for duplicates, refunds, timezone, AOV and contribution | §6.4 and §12 |

The following implementation gates remain intentionally explicit:

1. Materialize the Identity amendment as the parent machine-readable schema.
2. Publish the Reporting tool schemas/metric fixtures for AOV, hourly, branch and contribution data.
3. Generate safe-output schemas, event schemas and summary-revision migrations from the revised contract.
4. Implement provider budget accounting and the public `202` polling payload exactly as specified.

These are now materialization tasks already specified by the revised Contracts, not open design decisions.

**Decision:** Contracts are approved for Design Finalization, subject to user confirmation. Implementation must complete the listed machine-readable artifacts and parent amendments before code.

