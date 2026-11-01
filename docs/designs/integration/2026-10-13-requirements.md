Started at: 2026/10/13 00:00:00
Finished at: 2026/10/13 00:00:00
Total time: 0 minutes
---

# Module 12 — Tích hợp mở rộng: Requirements

**Ngày:** 2026-10-13  
**Trạng thái:** Approved with warnings — user approved after Requirements review  
**Vision:** [Integration & Demo Orchestration Vision](./2026-10-13-vision.md)  
**Vision review:** [Integration & Demo Orchestration Vision Review](./2026-10-13-vision-review.md)  
**Parent modules:** POS Platform, Order, Payment, Inventory, Loyalty, Recommendation, Reporting, AI Assistant, Audit

## 1. Purpose and boundaries

Integration service cung cấp một boundary riêng cho provider integrations và demo control plane. Service không sở hữu business state của các domain POS khác và không được phát paid/revenue/points/stock truth thay cho source service.

### In scope

- P2 invoice technical/sandbox lifecycle khởi tạo từ canonical `OrderPaid.v1`.
- Capture invoice recipient preference, contact validation và consent snapshot trước checkout.
- Provider-neutral invoice adapter, provider attempts, idempotency, retry/DLQ và safe status.
- Async email/Zalo delivery sau invoice issue, có channel-specific retry/expiry/status.
- Demo scenario runner chạy service thật, seed/reset, prerequisite health, checkpoints, fault injection non-production và X-Ray correlation.
- Contract/acceptance flow nối Order, Payment, Inventory, Loyalty, Recommendation, Reporting và AI Assistant.
- Integration observability, redaction, access audit reference và operational runbooks.

### Out of scope

- Phát hành hóa đơn thuế production, chữ ký số, mã cơ quan thuế, điều chỉnh/hủy hóa đơn theo pháp lý provider.
- Thay thế Order/Step Functions checkout Saga hoặc Payment IPN/checksum logic.
- Sở hữu Order, Payment, Inventory, Loyalty, Recommendation, Reporting hay AI business state.
- Tự tính doanh thu/uplift từ raw order/payment data.
- Tự gửi email/Zalo từ số điện thoại Loyalty khi chưa có explicit consent.
- Fault injection hoặc seed/reset routes trong production.

## 2. Actors, authorization and trust boundaries

### 2.1 Actors

| Actor | Use | Trust level |
|---|---|---|
| Cashier | Capture recipient choice for current order/checkout; view safe invoice status | Cognito user, branch/POS scoped |
| Manager | View invoice/delivery status; retry delivery for assigned branches | Cognito user, assigned branch scope |
| Owner | Tenant/subset invoice/delivery view; operate approved retry/export/report context | Cognito user, tenant/subset scope |
| Demo operator | Start/reset demo and view checkpoints/traces | Non-production workload/user role only |
| Order/Payment/other services | Publish/consume trusted events and call internal contracts | Workload identity/mTLS or signed service credential |
| Invoice provider | Receive normalized request and return provider response | Adapter credential; never receives POS Cognito token |
| Email/Zalo provider | Receive delivery request and return delivery result | Channel credential; no direct domain DB access |
| AI Assistant | Read approved Reporting/Recommendation facts for demo question | Existing AI authorization/tool boundary |

### 2.2 Permission catalogue

These permissions are additive parent Identity amendments:

| Permission | Owner | Manager | Cashier | Demo workload | Scope |
|---|---:|---:|---:|---:|---|
| `integration:invoice:read` | yes | assigned branches | current branch/current order | no | Tenant/branch |
| `integration:invoice:prepare` | yes | assigned branches | current order/checkout | no | Branch + order |
| `integration:delivery:retry` | yes | assigned branches | current order only, bounded | no | Branch + invoice |
| `integration:operate` | restricted ops | no | no | no | Provider/replay/retention operations |
| `demo:run` | no | no | no | allowed non-prod | Demo namespace |
| `demo:seed-reset` | no | no | no | allowed non-prod | Demo namespace |
| `demo:fault-inject` | no | no | no | allowed non-prod | Scenario/run lease |
| `demo:trace:read` | no | no | no | allowed non-prod | Run-owned trace references |

Identity remains online authorization authority. Integration derives tenant, branch, actor and environment from trusted auth/workload context; client-supplied IDs are filters or references only.

### 2.3 Trust rules

- Public API requires Cognito JWT; internal API requires workload identity/mTLS or approved service credential.
- Demo seed/reset/fault calls fail closed unless environment is explicitly `DEMO`/`TEST` and run lease is valid.
- Provider callbacks are accepted only at adapter boundary with provider-specific signature/authentication; they never mutate Order/Payment state.
- Integration never reads parent databases directly; it consumes versioned events or calls approved internal APIs.
- Tenant and branch predicates are enforced in service/repository layers; URL IDs cannot widen scope.

## 3. Definitions and canonical identifiers

| Term | Definition |
|---|---|
| `OrderPaid.v1` | Canonical immutable paid-order fact emitted once by Order after checkout Saga finalization. |
| `invoiceOperationId` | Integration-owned idempotent invoice lifecycle operation. |
| `invoiceRecipientRef` | Opaque reference to Integration-owned encrypted contact/consent snapshot; safe to include in Order checkout context. |
| `providerAttemptId` | One call/response attempt to invoice or delivery provider; attempts are append-only. |
| `deliveryOperationId` | Integration-owned lifecycle for one channel delivery request. |
| `demoRunId` | Unique versioned non-production run namespace and trace grouping. |
| `checkpointId` | Expected/observed state marker for one demo step. |
| `faultLeaseId` | Time-bound authorization to inject an allowlisted failure into a demo dependency. |
| `attributionKey` | Stable reference joining suggestion shown/accepted, Order line addition and paid order. |

Common IDs are UUID v7 strings; timestamps are ISO-8601 UTC. Business-day and demo display windows use branch IANA timezone snapshot. MVP money is non-negative VND minor units.

## 4. Functional requirements

### 4.1 Recipient preference and consent

| ID | Priority | Requirement |
|---|---|---|
| INT-FR-001 | P2 | POS can submit an invoice recipient preference before or during checkout with `channel=EMAIL` or `ZALO`, validated contact, consent text/version, consent time, branch/order/attempt context and correlation ID. |
| INT-FR-002 | P2 | Integration stores the contact/consent snapshot encrypted or tokenized and returns only an opaque `invoiceRecipientRef`; raw contact is never returned to normal POS read APIs. |
| INT-FR-003 | P2 | A preference is bound to one tenant/order/checkout attempt and cannot be reused for another order or tenant. |
| INT-FR-004 | P2 | Missing, invalid, revoked or expired consent means no delivery request is created; invoice technical operation may still proceed if configured. |
| INT-FR-005 | P2 | Updating a preference before invoice issue creates a new version; an already captured provider/delivery attempt keeps its immutable contact/consent snapshot. |
| INT-FR-006 | P2 | Manual resend requires a current authorized actor and re-evaluates delivery eligibility; it never silently changes the original consent snapshot. |

The normal path is that Order carries `invoiceRecipientRef` in checkout context and echoes it in `OrderPaid.v1`. Integration does not look up Loyalty phone or query Order DB to recover missing contact data.

### 4.2 Invoice operation from `OrderPaid.v1`

| ID | Priority | Requirement |
|---|---|---|
| INT-FR-010 | P2 | Integration consumes only canonical `OrderPaid.v1` for invoice creation; `PaymentPaid.v1` alone must not create an invoice. |
| INT-FR-011 | P2 | For a valid paid event, Integration creates at most one invoice operation for `(tenantId, orderId, checkoutAttemptId, invoiceProfileVersion)`. |
| INT-FR-012 | P2 | Duplicate/replayed `OrderPaid.v1` returns or converges to the existing operation without a second provider issue request. |
| INT-FR-013 | P2 | Invoice operation persists safe order/line/totals/branch snapshots required by sandbox contract; it does not become Order source of truth. |
| INT-FR-014 | P2 | Invoice request is asynchronous and never blocks Order `PAID` finalization or Saga completion. |
| INT-FR-015 | P2 | Provider response is mapped to normalized status and safe error code; raw provider payload/signature/secret is not returned or logged. |
| INT-FR-016 | P2 | Provider timeout/5xx/rate limit creates retryable failure with bounded backoff and DLQ after policy limit. |
| INT-FR-017 | P2 | Invalid business response, malformed invoice data or unsupported capability creates terminal failure and an operator-readable remediation code. |
| INT-FR-018 | P2 | Invoice issue operation includes provider profile `SANDBOX_TECHNICAL`; `ISSUED` means technical provider acceptance, not legal tax validity. |
| INT-FR-019 | P2 | Integration never emits `OrderPaid`, `PaymentPaid`, points, inventory or revenue events as a side effect of invoice processing. |

### 4.3 Email/Zalo delivery

| ID | Priority | Requirement |
|---|---|---|
| INT-FR-020 | P2 | Delivery is created only after invoice issue succeeds and a valid recipient preference with consent is available. |
| INT-FR-021 | P2 | Each channel uses a stable idempotency key derived from tenant, invoice operation, recipient snapshot version and channel. |
| INT-FR-022 | P2 | Provider attempts are append-only and record normalized outcome, retryability, rate-limit metadata and safe provider reference. |
| INT-FR-023 | P2 | Delivery worker uses bounded retry/backoff, per-channel rate limit and DLQ; it does not retry invalid recipient or revoked consent indefinitely. |
| INT-FR-024 | P2 | Delivery state is visible to authorized POS/Owner/Manager APIs without exposing raw contact or provider payload. |
| INT-FR-025 | P2 | Manual retry is idempotent and requires scope/permission; it may create a new attempt only under the same invoice/consent lineage. |
| INT-FR-026 | P2 | Delivery expiry is explicit; expiry does not change invoice or payment state. |
| INT-FR-027 | P2 | Zalo and email are separate channel attempts; a failure in one channel does not silently send the other channel without user selection/policy. |

### 4.4 Demo prerequisite and run lifecycle

| ID | Priority | Requirement |
|---|---|---|
| INT-FR-030 | P0-demo | Demo operator selects a versioned scenario, seed version, tenant/branch namespace and run ID before execution. |
| INT-FR-031 | P0-demo | Preflight verifies environment, service health, sandbox credentials, required feature flags, trace exporter and clean demo namespace. |
| INT-FR-032 | P0-demo | Demo runner can seed deterministic branch/catalog/customer/rule/recipe data through approved APIs or a dedicated non-production seed adapter. |
| INT-FR-033 | P0-demo | Each run has bounded lifetime, owner, lease, cleanup policy and no cross-run data/trace visibility. |
| INT-FR-034 | P0-demo | Runner executes the successful scenario through real service APIs: open shift, create order, recommendation shown/accepted, Loyalty phone lookup, VNPAY sandbox payment/IPN, Order finalization, Loyalty accrual and Inventory commit. |
| INT-FR-035 | P0-demo | Runner records expected and observed checkpoints for each step, including IDs, state, data-quality status and trace references. |
| INT-FR-036 | P0-demo | Runner can inject `PAYMENT_UNAVAILABLE` only under a valid non-production fault lease and verifies Inventory release/compensation. |
| INT-FR-037 | P0-demo | Reset is idempotent, bounded and cannot delete or mutate resources outside the demo namespace. |
| INT-FR-038 | P0-demo | Demo summary distinguishes pass, failed assertion, timeout, prerequisite failure and infrastructure failure. |
| INT-FR-039 | P0-demo | Demo trace view links `demoRunId`, `scenarioVersion`, `correlationId`, `causationId`, `sagaId`, order/attempt IDs and child spans without raw PII/secrets. |

### 4.5 Demo AI question and attribution

| ID | Priority | Requirement |
|---|---|---|
| INT-FR-040 | P0-demo | Demo verifies the Owner/Manager AI question: “Tuần này gợi ý Coca giúp tăng bao nhiêu doanh thu?” through existing AI Assistant function calling. |
| INT-FR-041 | P0-demo | The answer uses approved Reporting/Recommendation functions, not Integration raw tables or LLM arithmetic over raw orders. |
| INT-FR-042 | P0-demo | Attribution lineage distinguishes `SuggestionShown`, `SuggestionAccepted`, successful Coca add-line, `OrderPaid` and calculated uplift/AOV metric. |
| INT-FR-043 | P0-demo | Answer includes reporting period, branch scope, freshness/completeness, metric definition/version and provenance; stale/partial data is not presented as exact fact. |
| INT-FR-044 | P0-demo | If attribution is missing, late or incomplete, AI returns a safe structured limitation rather than inventing uplift. |

## 5. Invoice and delivery state machines

### 5.1 Invoice operation

```text
NOT_REQUESTED
      │ valid OrderPaid
      ▼
REQUESTED ── worker claim ──> ISSUING
   │                              │
   │                              ├─ provider accepted ──> ISSUED
   │                              ├─ retryable error ────> FAILED_RETRYABLE ──> REQUESTED
   │                              └─ terminal error ─────> FAILED_TERMINAL
```

Rules:

- `ISSUED` is terminal for the technical invoice profile; no second issue call for the same operation key.
- `FAILED_RETRYABLE` may return to `REQUESTED` only through an idempotent worker retry or authorized manual retry.
- `FAILED_TERMINAL` requires a new policy-approved operation/version; it cannot silently rewrite the original attempt.
- No invoice state transition changes Order, Payment, Loyalty or Inventory state.

### 5.2 Delivery operation

```text
NOT_REQUESTED → PENDING → SENDING
                         ├─> SENT
                         ├─> FAILED_RETRYABLE → PENDING
                         ├─> FAILED_TERMINAL
                         └─> EXPIRED
```

Rules:

- `SENT` means provider accepted the delivery request under channel contract, not necessarily recipient opened it.
- A provider delivery callback, if supported, is deduped and can add delivery evidence without rewriting the original send attempt.
- `EXPIRED` is terminal for that attempt; manual resend creates a lineage-linked attempt after authorization/consent checks.

### 5.3 Demo run

```text
CREATED → PREFLIGHTING → READY → RUNNING
  │          │             │        ├─> PASSED
  │          │             │        ├─> FAILED
  │          │             │        └─> TIMED_OUT
  │          └─────────────┴──────> PREFLIGHT_FAILED
  └────────────────────────────────> RESETTING → RESET
```

Fault injection is a leased step within `RUNNING`; it expires automatically and cannot be attached to a production resource.

## 6. API-level requirements

All public endpoints use Cognito JWT, server-resolved tenant/branch scope and safe `{data, meta}` envelopes. Internal demo/provider endpoints use workload identity and are network/environment restricted.

### 6.1 Public/staff endpoints

| Method | Path | Permission | Purpose |
|---|---|---|---|
| POST | `/api/v1/invoice-recipient-intents` | `integration:invoice:prepare` | Capture validated email/Zalo contact + consent snapshot |
| GET | `/api/v1/invoices/{invoiceOperationId}` | `integration:invoice:read` | Safe invoice/delivery status |
| GET | `/api/v1/invoices` | `integration:invoice:read` | Scoped list/filter by order/branch/status/time |
| POST | `/api/v1/invoices/{invoiceOperationId}/delivery-retries` | `integration:delivery:retry` | Authorized retry with idempotency key |
| GET | `/api/v1/invoices/{invoiceOperationId}/deliveries` | `integration:invoice:read` | Channel/status history without raw contact |

`invoice-recipient-intents` returns an opaque `invoiceRecipientRef`; it never returns the raw provider credential, contact secret or full provider payload.

### 6.2 Internal demo endpoints

| Method | Path | Permission | Purpose |
|---|---|---|---|
| POST | `/internal/v1/demo/runs` | `demo:run` | Create versioned demo run and request preflight |
| GET | `/internal/v1/demo/runs/{demoRunId}` | `demo:run` | Read run/checkpoint/trace summary |
| POST | `/internal/v1/demo/runs/{demoRunId}/execute` | `demo:run` | Start/resume allowed scenario steps |
| POST | `/internal/v1/demo/runs/{demoRunId}/reset` | `demo:seed-reset` | Idempotent namespace reset |
| POST | `/internal/v1/demo/runs/{demoRunId}/fault-leases` | `demo:fault-inject` | Request allowlisted time-bound fault lease |
| DELETE | `/internal/v1/demo/runs/{demoRunId}/fault-leases/{faultLeaseId}` | `demo:fault-inject` | Revoke fault lease |
| GET | `/internal/v1/demo/runs/{demoRunId}/traces` | `demo:trace:read` | Return authorized trace references/links |
| GET | `/internal/v1/integration/health` | workload/ops | Dependency/provider/queue health |

### 6.3 Error taxonomy

| HTTP | Code | Meaning |
|---:|---|---|
| 400 | `invalid_request` | Malformed body/header |
| 401 | `unauthorized` | Missing/invalid identity |
| 403 | `forbidden` | Permission/environment/role denied |
| 404 | `not_found` | Resource absent or outside scope |
| 409 | `idempotency_conflict` | Same key, different request |
| 409 | `invoice_state_conflict` | Invalid invoice/delivery transition |
| 409 | `demo_run_conflict` | Run lease/version/state conflict |
| 422 | `invalid_contact_or_consent` | Contact/channel/consent validation failed |
| 422 | `invalid_demo_scenario` | Scenario/seed/fault not allowlisted |
| 429 | `rate_limited` | Provider/user/tenant/channel limit |
| 503 | `dependency_unavailable` | Required provider/service unavailable; safe retry policy applies |

Errors never reveal foreign tenant/branch existence, raw provider error, contact value, token, secret, SQL or stack trace.

## 7. Non-functional requirements

| Area | Requirement |
|---|---|
| Async isolation | Invoice/delivery workers must not be on synchronous checkout critical path; `OrderPaid` consumer outage only delays integration state. |
| Ingestion latency | p95 from accepted `OrderPaid.v1` to persisted invoice operation ≤ 5 seconds under target load; provider latency is measured separately. |
| Delivery target | p95 from `InvoiceIssued` to provider-accepted delivery ≤ 2 minutes when provider is healthy; timeout budgets are provider-configurable. |
| Demo duration | Healthy seeded success scenario target ≤ 10 minutes, with per-step timeout and VNPAY sandbox wait explicitly reported. |
| Idempotency | Duplicate/out-of-order/replayed events, API retries and worker retries converge without duplicate invoice issue, delivery side effect or demo reset. |
| Availability | Integration degradation does not change paid-order or Saga state; provider outage surfaces `FAILED_RETRYABLE`/`DEGRADED`. |
| Scale | Initial target: 10 paid events/sec sustained 5 minutes, 100 concurrent delivery attempts, 10 concurrent demo runs per non-prod environment; validate with load profile. |
| Tenant isolation | Every table/query/event projection carries tenant scope; branch predicate applies to branch-scoped resources; batch operations validate all items. |
| Security | Secrets in Secrets Manager/KMS; workload auth/mTLS; no hardcoded credentials; provider signatures verified at adapter boundary. |
| Privacy | Contact encrypted/tokenized, masked responses, consent/version retained by policy, no raw PII in logs/traces/events. |
| Observability | Correlation/causation/demo/saga IDs, structured logs, X-Ray/OpenTelemetry spans, queue lag, provider latency, retry/DLQ and checkpoint metrics. |
| Recovery | Inbox/outbox replay, operation rebuild and demo reset are bounded, leased, idempotent and auditable. |
| Retention | Invoice/delivery/consent/demo evidence data classes have explicit retention/legal-hold policy before migration; raw provider payload retention is minimized. |

## 8. Data and persistence requirements

Integration owns a separate PostgreSQL database. Every new column requires a descriptive database comment.

### 8.1 Logical tables

| Table | Purpose | Key invariants |
|---|---|---|
| `invoice_recipient_snapshots` | Encrypted/tokenized contact + consent version | Tenant/order/attempt scope; immutable per version; no raw contact in projections |
| `invoice_operations` | Technical invoice lifecycle | Unique tenant/order/attempt/profile; CAS state transition |
| `invoice_items` | Safe line/amount snapshots for invoice provider | Snapshot only; VND invariant; no source Order mutation |
| `invoice_provider_attempts` | Provider request/result attempt history | Idempotency key unique; raw response encrypted/minimized or discarded by policy |
| `delivery_operations` | Channel delivery lifecycle | One operation per invoice/channel/recipient version lineage |
| `delivery_provider_attempts` | Email/Zalo provider attempts | Append-only outcome/retry metadata; no raw credential |
| `demo_runs` | Scenario/seed/env/run state | Unique run namespace; lease/TTL; environment check |
| `demo_checkpoints` | Expected/observed step states | Monotonic sequence per run; trace references redacted |
| `fault_leases` | Non-prod allowlisted failure lease | Environment/run/expiry/permission binding; auto-expire |
| `inbox_events` | At-least-once event dedupe/status | Unique producer/source event key; DLQ/error state |
| `outbox_messages` | Produced invoice/demo/quality facts | Atomic with local state; retry/DLQ |
| `projection_watermarks` | Source/provider/demo freshness | Monotonic per scope/source/projection |
| `audit_references` | Redacted operation/access references | No raw PII/secret; linked to Audit source-of-record |

### 8.2 Required access patterns

- Invoice status by tenant + branch + order/attempt, newest first.
- Invoice operations by state/retry time for worker polling.
- Delivery status by invoice/channel and failed/retryable queue.
- Provider attempt history by operation/idempotency key.
- Demo run/checkpoint by run ID and active lease/expiry.
- Inbox/outbox by status/next attempt and source watermark.

Required indexes include tenant/branch/order/created time, operation state/retry time, invoice/channel status, unique source event key, demo environment/run/status, active fault lease expiry and watermark health. Monthly time partitioning is a candidate only after measured volume/retention validation.

## 9. Event and integration requirements

### 9.1 Common envelope

Every event uses:

```json
{
  "eventId": "0192...",
  "eventType": "InvoiceIssued.v1",
  "eventVersion": 1,
  "occurredAt": "2026-10-13T08:00:00Z",
  "tenantId": "0192...",
  "branchId": "0192...",
  "aggregateType": "INVOICE_OPERATION",
  "aggregateId": "0192...",
  "aggregateVersion": 3,
  "sourceEventId": "0192...",
  "sourceVersion": 18,
  "correlationId": "0192...",
  "causationId": "0192...",
  "producer": "integration-service",
  "data": {}
}
```

Event delivery is at-least-once. `eventId` and `(producer, sourceEventId)` are stable across retry/replay. Unknown versions or failed redaction are quarantined/DLQ.

### 9.2 Consumed events

| Event | Use | Must contain |
|---|---|---|
| `OrderPaid.v1` | Create invoice operation and downstream trigger | order/attempt/payment references, branch, totals/line snapshots or safe invoice snapshot, `invoiceRecipientRef` optional, source version, correlation |
| `OrderCheckoutFailed.v1` | Expire unused recipient intent and correlate demo failure | order/attempt, failure/compensation state, tenant/branch, correlation |
| `OrderCheckoutCompensated.v1` or parent equivalent | Verify demo release checkpoint | order/attempt/saga, released resource references, correlation |
| `SuggestionShown.v1` | Optional demo/reporting evidence lookup only; not revenue | suggestion/item/order/cart context, interaction ID |
| `SuggestionAccepted.v1` | Optional attribution checkpoint only; not revenue | suggestion/item/order line evidence, interaction ID, source version |
| `PaymentReconciliationRequired.v1` | Demo/ops visibility for late/unknown payment | payment/order/attempt, safe state/reference, correlation |

Integration must not consume `PaymentPaid.v1` as an invoice authority when `OrderPaid.v1` is absent.

### 9.3 Produced events

| Event | Use |
|---|---|
| `InvoiceOperationRequested.v1` | Technical invoice operation created |
| `InvoiceIssued.v1` | Sandbox provider accepted invoice request; includes profile/capability and safe provider reference |
| `InvoiceFailed.v1` | Retryable/terminal invoice failure with normalized code |
| `InvoiceDeliveryRequested.v1` | Channel delivery scheduled |
| `InvoiceDeliveryStatusChanged.v1` | Safe delivery status/retry/expiry change |
| `DemoRunStarted.v1` | Scenario/run started |
| `DemoCheckpointRecorded.v1` | Expected/observed checkpoint and quality/trace refs |
| `DemoRunCompleted.v1` | Pass/fail/timeout summary |
| `IntegrationDataQualityChanged.v1` | Source/provider/queue freshness or completeness change |
| `IntegrationOperationAuditReferenced.v1` | Redacted operation/access reference to Audit |

Produced events never contain raw provider payload, credential, token, OTP or unmasked contact value.

## 10. Observability and audit requirements

- Every request, event consumer, provider attempt and demo step carries correlation/causation context.
- X-Ray/OpenTelemetry spans include service, operation, tenant-safe scope hash, order/attempt/invoice/demo IDs and outcome; no phone/email/Zalo value or payment secret.
- Required metrics: `invoice_operation_lag`, `invoice_issue_success_rate`, `invoice_provider_latency`, `invoice_retry_count`, `delivery_success_rate_by_channel`, `delivery_provider_latency`, `dlq_depth`, `order_paid_consumer_lag`, `demo_step_duration`, `demo_checkpoint_failure`, `fault_lease_count` and `trace_export_failure`.
- Alerts: source lag >5 minutes, DLQ >0 for 5 minutes, retry storm, provider error-rate threshold, unexpired fault lease beyond run TTL, demo namespace leak, trace exporter outage and consent/access anomaly.
- Access to contact snapshot, invoice detail, manual resend, seed/reset and fault injection is audit-referenced with actor/workload, reason, scope, before/after safe summary and correlation ID.
- Raw provider request/response is not logged. If forensic retention is required, store minimized encrypted reference under explicit retention/legal-hold policy.

## 11. Failure and recovery requirements

| Failure | Required behavior |
|---|---|
| Duplicate `OrderPaid` | Inbox dedupe; return existing invoice operation; no second issue call |
| Out-of-order `OrderPaid`/checkout failure | Source/version guard; do not cancel an already issued invoice; record quality/reconciliation state |
| Provider timeout | Persist attempt timeout, return retryable status, bounded backoff/DLQ, no Order mutation |
| Provider malformed/unsupported response | Terminal normalized error, safe operator code, no raw payload in public response |
| Email/Zalo rate limit | Respect Retry-After/provider policy, channel-specific backoff, no infinite retry |
| Consent revoked before send | Cancel pending delivery; retain redacted lineage; invoice remains unchanged |
| Worker crash after provider accepted | Idempotency key/reconciliation query converges to `ISSUED`/`SENT` without duplicate side effect |
| Integration DB unavailable | Ack policy must preserve at-least-once; do not acknowledge source event before local inbox/evidence transaction |
| Demo service health failure | Preflight or checkpoint fails with actionable dependency; no false PASS |
| Payment fault injection | Only valid lease; Saga owns compensation; runner verifies Inventory release and no paid side effect |
| Reset timeout | Mark reset incomplete, retain lease/operation evidence, retry bounded; never delete outside namespace |
| X-Ray unavailable | Business flow may continue; checkpoint marks trace quality degraded and raises ops alert |

## 12. Acceptance and test matrix

| Area | Acceptance scenarios |
|---|---|
| Invoice idempotency | Same `OrderPaid` replay, duplicate source event, two workers race, same operation key/different payload |
| Invoice lifecycle | Sandbox accepted, timeout/retry, 429/backoff, 5xx, malformed response, terminal unsupported capability |
| Delivery | Email success, Zalo success, invalid contact, revoked consent, duplicate provider callback, rate limit, expiry, manual retry |
| Security/privacy | Cross-tenant/branch IDOR, revoked membership, scope cache, raw contact/provider secret/log/trace redaction, missing environment guard |
| Order/Saga | Invoice provider down after OrderPaid does not change PAID; Payment down before paid releases Inventory through Order Saga |
| Demo success | Open shift → gà rán → Coca suggestion shown/accepted → Loyalty customer context → VNPAY QR/IPN → PAID → points/Inventory/reporting |
| Demo failure | Payment unavailable → compensation → Inventory release → no duplicate points/stock/OrderPaid; trace references present |
| Demo concurrency | Two runs same scenario/seed, reset during run, expired lease, fault lease collision, namespace isolation |
| AI attribution | Shown without accepted, accepted without add, add without paid, paid after late event, partial Reporting data, correct uplift definition |
| Replay/recovery | Inbox/outbox replay, provider accepted then worker crash, DLQ replay, watermark gap, rebuild without duplicate side effects |
| Performance | 10 paid events/sec, 100 delivery attempts, 10 concurrent demo runs, bounded list queries and queue lag targets |

## 13. Parent contract amendments required

### POS Platform / Identity

- Add Integration permissions and environment/workload scope decisions.
- Register Integration events, common envelope, source dedupe and Audit reference ownership.
- Separate non-production demo routes/roles from production service routes.

### Order

- Add optional `invoiceRecipientRef`/invoice profile reference to checkout context and `OrderPaid.v1` without exposing raw contact.
- Ensure `OrderPaid.v1` includes stable `orderId`, `checkoutAttemptId`, `branchId`, source/aggregate version, safe line/totals snapshot, correlation/causation and exactly-once outbox semantics.
- Publish parent equivalent of `OrderCheckoutCompensated.v1` if demo verification needs an explicit release fact.

### Payment

- Keep `PaymentPaid.v1` distinct from canonical `OrderPaid.v1`; expose safe late/reconciliation fact for demo/ops context.
- Maintain VNPAY sandbox/IPN ownership in Payment; Integration must not call provider callback internals.

### Loyalty

- Provide customer context/phone recognition through approved Loyalty API/event only; no Integration direct PII lookup.
- Confirm `OrderPaid.v1` accrual timing and idempotency for demo checkpoint.

### Inventory

- Publish reservation release/commit evidence needed to verify Payment outage compensation; Integration does not calculate stock.

### Recommendation / Reporting / AI Assistant

- Publish authoritative shown/accepted/add-line/paid attribution lineage.
- Publish metric dictionary/function contract for incremental revenue/AOV uplift, branch timezone, freshness and incomplete-data behavior.
- AI remains read-only and calls approved Reporting/Recommendation functions; Integration only supplies demo correlation context.

### Audit

- Register invoice/delivery/demo/fault/access events as redacted Audit references with retention/legal-hold classification.

## 14. Requirements approval gate

- **Current status:** Approved — user accepted the Requirements review warnings.
- **Required before Architecture:** Completed; parent amendments and executable fixtures are implementation gates recorded in Architecture/Contracts.
- **Implementation rule:** Không viết controller/worker/provider adapter/demo runner; materialize OpenAPI, AsyncAPI/JSON Schema, migrations and deterministic fixtures only after Architecture/Contracts approval.
