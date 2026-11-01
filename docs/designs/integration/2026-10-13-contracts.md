Started at: 2026/10/13 00:00:00
Finished at: 2026/10/13 00:00:00
Total time: 0 minutes
---

# Module 12 — Tích hợp mở rộng: API & Data Contracts

**Ngày:** 2026-10-13  
**Trạng thái:** Approved with warnings — user approved after Contracts review  
**Vision:** [Integration & Demo Orchestration Vision](./2026-10-13-vision.md)  
**Requirements:** [Integration & Demo Orchestration Requirements](./2026-10-13-requirements.md)  
**Architecture:** [Integration & Demo Orchestration Architecture](./2026-10-13-architecture.md)  
**Sequence diagrams:** [Integration & Demo Orchestration Sequence Diagrams](./2026-10-13-sequence-diagrams.adoc)

Tài liệu này là nguồn chuẩn để sinh OpenAPI 3.1, AsyncAPI/JSON Schema, PostgreSQL/Flyway migrations, provider adapter ports, demo scenario schema, audit reference fixtures và generated client types. Integration sở hữu invoice/delivery/demo control state; không sở hữu Order/Payment/Loyalty/Inventory/Recommendation/Reporting/AI business state.

## 1. Contract conventions

### 1.1 Identity, time and money

- Public REST dưới `/api/v1`; internal/workload REST dưới `/internal/v1`; provider callback boundary dưới `/callbacks/v1`.
- EventBridge/SQS delivery at-least-once; event names use `EventName.v1` in the Integration registry. Parent compatibility mapping accepts current `OrderPaid` + `eventVersion=1` until parent amendment publishes `OrderPaid.v1`.
- IDs là UUID v7 strings; timestamps là ISO-8601 UTC.
- Business windows use branch IANA timezone snapshot; browser timezone is never authoritative.
- MVP money là signed/non-negative `int64` minor units with `currency=VND`; invoice totals must reconcile to Order snapshot.
- Public responses use `{data, meta}`; provider/raw payloads never appear in public responses.
- New optional fields are backward-compatible; changed meaning/removal requires new API major path or event version.

### 1.2 Headers

| Header | Public | Internal | Callback | Meaning |
|---|---:|---:|---:|---|
| `Authorization` | required | required | no | Cognito JWT or workload credential/mTLS |
| `X-Correlation-Id` | recommended | required | recommended | Request/event/worker trace correlation |
| `X-Causation-Id` | no | recommended | recommended | Upstream command/event reference |
| `Idempotency-Key` | mutation | mutation/ops | provider key | Stable retry key; tenant + caller scoped |
| `If-Match` | optional mutation | required for demo CAS | no | Expected resource version |
| `X-Demo-Run-Id` | no | demo only | no | Run context; accepted only in non-prod |
| `X-Fault-Lease-Id` | no | demo only | no | Run-scoped fault authorization |
| `X-Provider-Signature` | no | no | required where provider supports | Adapter callback verification |
| `Cache-Control` | server | server | no | No cache for contact/provider detail; status cache only if scope-safe |

Client cannot submit tenant, actor, role, effective operator, environment, source event authority or branch ownership as truth. IDs in body/path are validated against trusted scope.

### 1.3 Common response envelope

```json
{
  "data": {},
  "meta": {
    "requestId": "req_01J...",
    "correlationId": "cor_01J...",
    "resourceVersion": 3,
    "dataQuality": {
      "status": "FRESH",
      "freshnessLagSeconds": 2,
      "completenessStatus": "COMPLETE",
      "watermark": "order-paid:v901",
      "watermarkVersion": 901,
      "lastUpdatedAt": "2026-10-13T08:00:02Z"
    }
  }
}
```

Quality status: `FRESH`, `STALE`, `PARTIAL`, `UNAVAILABLE`, `NO_DATA`, `DEGRADED_TRACE`, `NO_RECIPIENT`.

### 1.4 Error envelope

```json
{
  "error": {
    "code": "invoice_state_conflict",
    "message": "The requested operation is not valid for the current state",
    "details": [],
    "requestId": "req_01J...",
    "correlationId": "cor_01J..."
  }
}
```

Messages never enumerate foreign resources or expose contact, provider payload, secret, token, SQL or stack trace.

### 1.5 Pagination/filtering

- Invoice list and delivery history use cursor pagination, default 20, maximum 100.
- Stable sort: `createdAt DESC, id DESC`.
- Cursor binds normalized filters, resolved scope hash, authorization version and projection watermark.
- Interactive query range maximum is 90 days; older history uses restricted async export/reference owned by Audit or approved Ops workflow.
- `404 not_found` is returned for missing or out-of-scope resources; no foreign existence enumeration.

## 2. Authorization and scope contract

### 2.1 Permission amendment

| Permission | Owner | Manager | Cashier | Demo workload | Scope |
|---|---:|---:|---:|---:|---|
| `integration:invoice:read` | yes | assigned branches | current branch/current order | no | Tenant/branch |
| `integration:invoice:prepare` | yes | assigned branches | current order/checkout | no | Branch + order |
| `integration:delivery:retry` | yes | assigned branches | current order only/bounded | no | Branch + invoice |
| `integration:operate` | restricted ops | no | no | no | Provider/replay/retention |
| `demo:run` | no | no | no | allowed non-prod | Demo namespace |
| `demo:seed-reset` | no | no | no | allowed non-prod | Run namespace |
| `demo:fault-inject` | no | no | no | allowed non-prod | Run/lease |
| `demo:trace:read` | no | no | no | allowed non-prod | Run-owned refs |

Identity remains online authority. Integration calls the parent authorization decision contract where needed, then applies repository predicates with `tenant_id` and resolved `branch_id`.

### 2.2 Resolved scope

```json
{
  "scopeType": "BRANCH",
  "tenantId": "0192...001",
  "branchIds": ["0192...002"],
  "role": "MANAGER",
  "permissions": ["integration:invoice:read", "integration:delivery:retry"],
  "membershipVersion": 12,
  "authorizationVersion": 7,
  "scopeHash": "sha256:opaque",
  "resolvedAt": "2026-10-13T08:00:00Z",
  "expiresAt": "2026-10-13T08:05:00Z"
}
```

Owner tenant scope must be explicit; wildcard branch IDs are invalid. Every detail, list, retry, export, demo trace and callback-linked resource is scope-checked.

## 3. Common schemas

### 3.1 `MoneyAmount`

```json
{
  "amountMinor": 50000,
  "currency": "VND"
}
```

Constraints: `amountMinor >= 0`, `currency=VND` in MVP, integer arithmetic only, and invoice subtotal/discount/VAT/total reconcile exactly to the trusted OrderPaid snapshot.

### 3.2 `InvoiceRecipientIntentRequest`

```json
{
  "orderId": "0192...",
  "checkoutAttemptId": "0192...",
  "branchId": "0192...",
  "channel": "EMAIL",
  "contact": {"email": "customer@example.test"},
  "consent": {
    "policyVersion": "invoice-delivery-consent-v1",
    "textVersion": "2026-10-13",
    "capturedAt": "2026-10-13T08:00:00Z",
    "purpose": "INVOICE_DELIVERY"
  }
}
```

For `ZALO`, `contact` is one validated `zaloUserId` or E.164 phone under the provider capability; the API never returns it. The raw contact is accepted only over TLS, encrypted/tokenized before persistence and redacted before logs/traces.

### 3.3 `InvoiceRecipientSnapshot`

```json
{
  "invoiceRecipientRef": "0192...",
  "channel": "EMAIL",
  "maskedContact": "c***@example.test",
  "consentPolicyVersion": "invoice-delivery-consent-v1",
  "consentTextVersion": "2026-10-13",
  "consentedAt": "2026-10-13T08:00:00Z",
  "version": 1,
  "status": "ACTIVE"
}
```

### 3.4 `InvoiceOperationSummary`

```json
{
  "invoiceOperationId": "0192...",
  "tenantId": "0192...",
  "branchId": "0192...",
  "orderId": "0192...",
  "checkoutAttemptId": "0192...",
  "sourceEventId": "0192...",
  "profile": "SANDBOX_TECHNICAL",
  "status": "ISSUED",
  "technicalReference": "sandbox-inv-123",
  "total": {"amountMinor": 105000, "currency": "VND"},
  "recipient": {"channel": "EMAIL", "maskedContact": "c***@example.test"},
  "deliveryStatus": "SENT",
  "version": 3,
  "createdAt": "2026-10-13T08:00:01Z",
  "updatedAt": "2026-10-13T08:00:05Z"
}
```

`ISSUED` means sandbox/provider technical acceptance only. It is not a legal tax invoice status.

### 3.5 `DemoCheckpoint`

```json
{
  "checkpointId": "0192...",
  "demoRunId": "0192...",
  "stepKey": "PAYMENT_IPN_VERIFIED",
  "sequence": 5,
  "expectedState": {"payment": "PAID"},
  "observedState": {"payment": "PAID", "order": "PAID"},
  "status": "PASSED",
  "quality": "FRESH",
  "traceRefs": ["trace_opaque_1"],
  "correlationId": "cor_01J...",
  "recordedAt": "2026-10-13T08:01:00Z"
}
```

Trace refs are opaque, scoped and may be `DEGRADED_TRACE` when X-Ray is unavailable; checkpoint does not become a false PASS solely from a missing trace.

## 4. Public API contracts

### 4.1 Endpoint catalogue

| Method | Path | Permission | Response | Purpose |
|---|---|---|---|---|
| POST | `/api/v1/invoice-recipient-intents` | `integration:invoice:prepare` | `201 RecipientResponse` | Capture contact/consent, return opaque ref |
| GET | `/api/v1/invoices` | `integration:invoice:read` | `InvoicePage` | Scoped list by order/branch/status/time |
| GET | `/api/v1/invoices/{invoiceOperationId}` | `integration:invoice:read` | `InvoiceResponse` | Safe invoice/delivery detail |
| GET | `/api/v1/invoices/{invoiceOperationId}/deliveries` | `integration:invoice:read` | `DeliveryPage` | Channel/attempt history |
| POST | `/api/v1/invoices/{invoiceOperationId}/delivery-retries` | `integration:delivery:retry` | `202 DeliveryRetryResponse` | Authorized retry |

### 4.2 Prepare recipient request

`POST /api/v1/invoice-recipient-intents` requires `Idempotency-Key`.

```json
{
  "data": {
    "orderId": "0192...",
    "checkoutAttemptId": "0192...",
    "channel": "EMAIL",
    "contact": {"email": "customer@example.test"},
    "consent": {
      "policyVersion": "invoice-delivery-consent-v1",
      "textVersion": "2026-10-13",
      "capturedAt": "2026-10-13T08:00:00Z",
      "purpose": "INVOICE_DELIVERY"
    }
  }
}
```

Response `201`:

```json
{
  "data": {
    "invoiceRecipientRef": "0192...",
    "channel": "EMAIL",
    "maskedContact": "c***@example.test",
    "version": 1,
    "status": "ACTIVE",
    "expiresAt": "2026-10-13T10:00:00Z"
  },
  "meta": {"requestId": "req_01J...", "correlationId": "cor_01J..."}
}
```

Same idempotency key and canonical request replays the same response. Same key with a different normalized contact/channel returns `409 idempotency_conflict`.

### 4.3 Invoice list/detail

`GET /api/v1/invoices?from=&to=&branchIds=&orderId=&status=&deliveryStatus=&cursor=&limit=`.

Rules: server resolves branch scope; maximum 90 days; `branchIds` max 20 and subset of scope; no raw contact/provider request/response; invoice `technicalReference` is returned only as safe provider reference.

### 4.4 Delivery retry

`POST /api/v1/invoices/{invoiceOperationId}/delivery-retries` requires `Idempotency-Key` and optional `If-Match`.

```json
{
  "data": {
    "channel": "EMAIL",
    "reason": "Khách yêu cầu gửi lại",
    "expectedVersion": 3
  }
}
```

Rules:

- Server re-checks current scope, consent, contact status, invoice state and channel capability.
- Retry never accepts a new raw contact; changing contact requires a new consent snapshot/version.
- Response `202` returns `deliveryOperationId`, `attemptId`, current state and `pollUrl`.
- Stale version returns `409 invoice_state_conflict`; same idempotency request returns original operation.

## 5. Internal/demo API contracts

### 5.1 Demo endpoints

| Method | Path | Permission | Response |
|---|---|---|---|
| POST | `/internal/v1/demo/runs` | `demo:run` | `201 DemoRunResponse` |
| GET | `/internal/v1/demo/runs/{demoRunId}` | `demo:run` | `DemoRunResponse` |
| POST | `/internal/v1/demo/runs/{demoRunId}/execute` | `demo:run` | `202 DemoRunResponse` |
| POST | `/internal/v1/demo/runs/{demoRunId}/reset` | `demo:seed-reset` | `202 ResetOperationResponse` |
| POST | `/internal/v1/demo/runs/{demoRunId}/fault-leases` | `demo:fault-inject` | `201 FaultLeaseResponse` |
| DELETE | `/internal/v1/demo/runs/{demoRunId}/fault-leases/{faultLeaseId}` | `demo:fault-inject` | `204` |
| GET | `/internal/v1/demo/runs/{demoRunId}/traces` | `demo:trace:read` | `TraceReferencePage` |
| GET | `/internal/v1/integration/health` | workload/ops | `IntegrationHealthResponse` |

### 5.2 Create demo run

```json
{
  "data": {
    "scenarioKey": "POS_P0_SUCCESS_AND_PAYMENT_OUTAGE",
    "scenarioVersion": 1,
    "seedVersion": "pos-demo-seed-v1",
    "tenantNamespace": "demo-tenant-01",
    "branchNamespace": "demo-branch-01",
    "requestedSteps": ["SUCCESS", "PAYMENT_UNAVAILABLE"]
  }
}
```

Server ignores client environment claims; deployment environment must be `DEMO`/`TEST`. Run creation acquires namespace/lease and performs preflight before `READY`.

### 5.3 Fault lease

```json
{
  "data": {
    "faultType": "PAYMENT_UNAVAILABLE",
    "target": "payment-initiate",
    "stepKey": "PAYMENT_FAILURE",
    "reason": "Verify Saga releases Inventory reservation",
    "ttlSeconds": 300
  }
}
```

Server caps TTL, verifies scenario allowlist, binds lease to run/environment/owner and returns `faultLeaseId`, expiry and status. Lease expiry/revocation is audited and fails open to normal behavior.

### 5.4 Provider health and reconciliation

`GET /internal/v1/integration/health` returns per source/queue/provider:

```json
{
  "data": {
    "environment": "DEMO",
    "sources": [{
      "name": "order-paid-consumer",
      "status": "HEALTHY",
      "lagSeconds": 2,
      "dlqCount": 0,
      "watermark": "order-paid:v901"
    }],
    "providers": [{
      "name": "invoice-sandbox",
      "profile": "SANDBOX_TECHNICAL",
      "status": "HEALTHY",
      "latencyMsP95": 240
    }]
  }
}
```

## 6. Provider adapter contracts

### 6.1 Invoice port

```text
issue(IssueInvoiceCommand, ProviderIdempotencyKey)
  → Accepted(profile, technicalReference, artifactRef, occurredAt)
  → RetryableFailure(normalizedCode, retryAfter)
  → TerminalFailure(normalizedCode)
  → Unknown(operationQueryRef)
```

`IssueInvoiceCommand` includes tenant/branch-safe profile, order/attempt/source references, immutable line/totals/VAT/discount snapshots, recipient delivery capability only when needed and correlation. It never includes Cognito token or unrelated customer PII.

### 6.2 Delivery port

```text
send(SendDeliveryCommand, ChannelIdempotencyKey)
  → Accepted(channel, providerMessageRefSafe, acceptedAt)
  → RetryableFailure(normalizedCode, retryAfter)
  → TerminalFailure(normalizedCode)
  → Unknown(callbackOrQueryRef)
```

Adapters own provider signing/authentication, timeout, retry-after mapping, callback verification, provider idempotency and raw payload redaction. Domain state transitions accept normalized results only.

### 6.3 Callback contracts

Provider callbacks enter only through adapter boundary:

- `POST /callbacks/v1/invoice-providers/{providerKey}/invoice-status`
- `POST /callbacks/v1/delivery-providers/{providerKey}/delivery-status`

Callback requirements: provider signature/auth verification, timestamp/replay window, provider event dedupe, amount/order/reference mapping, safe response, no Cognito dependency and no direct parent state mutation. Unsupported provider callback is quarantined.

## 7. Event contracts

### 7.1 Common envelope

```json
{
  "eventId": "0192...",
  "eventType": "InvoiceIssued.v1",
  "eventVersion": 1,
  "occurredAt": "2026-10-13T08:00:02Z",
  "tenantId": "0192...",
  "branchId": "0192...",
  "aggregateType": "INVOICE_OPERATION",
  "aggregateId": "0192...",
  "aggregateVersion": 3,
  "sourceEventId": "0192...",
  "sourceVersion": 18,
  "correlationId": "cor_01J...",
  "causationId": "evt_01J...",
  "producer": "integration-service",
  "schemaVersion": "integration-events-v1",
  "data": {}
}
```

Invariants: stable event ID across replay, `(producer, sourceEventId)` unique, tenant required, branch required for branch facts, original occurred time preserved, unknown version/redaction → quarantine/DLQ.

### 7.2 Consumed source events

| Event | Canonical source | Integration behavior |
|---|---|---|
| `OrderPaid.v1` | Order | Create/converge invoice operation; parent compatibility accepts `OrderPaid` + version 1 during migration |
| `OrderCheckoutFailed.v1` | Order | Expire unused recipient intent; never cancel an already issued invoice |
| `SuggestionShown.v1` | Recommendation | Demo/attribution checkpoint only; no invoice state mutation |
| `SuggestionAccepted.v1` | Recommendation/Order evidence | Demo/attribution checkpoint only; not revenue truth |
| `PaymentReconciliationRequired.v1` | Payment | Demo/ops quality context only; never creates invoice |

Demo Control Plane may also read approved non-prod checkpoint APIs/events from Shift, Order, Payment, Inventory, Loyalty, Reporting and AI. Those APIs remain owned by the parent services.

### 7.3 `OrderPaid.v1` required additive fields

Parent Order must add/normalize:

```json
{
  "orderId": "0192...",
  "checkoutAttemptId": "0192...",
  "branchId": "0192...",
  "aggregateVersion": 18,
  "sourceVersion": 18,
  "invoiceRecipientRef": "0192...",
  "invoiceProfile": "SANDBOX_TECHNICAL",
  "lines": [],
  "subtotalMinor": 100000,
  "discountMinor": 0,
  "vatMinor": 0,
  "grandTotalMinor": 100000,
  "currency": "VND"
}
```

`invoiceRecipientRef` is optional/opaque. Raw email/Zalo/phone never appears in `OrderPaid.v1`.

### 7.4 Produced events

| Event | Minimum payload | Semantics |
|---|---|---|
| `InvoiceOperationRequested.v1` | invoice operation, order/attempt/source refs, profile, amount, correlation | Technical operation created |
| `InvoiceIssued.v1` | operation/version, profile, technical reference, safe artifact ref, status | Sandbox/provider accepted; not legal tax validity |
| `InvoiceFailed.v1` | operation/version, retryability, normalized code, next retry/terminal state | Safe failure fact |
| `InvoiceDeliveryRequested.v1` | invoice, channel, recipient snapshot version, attempt/idempotency ref | Channel send scheduled |
| `InvoiceDeliveryStatusChanged.v1` | delivery/attempt, channel, status, safe provider ref, quality | Provider accepted/failure/expiry |
| `DemoRunStarted.v1` | run/scenario/seed/environment safe refs | Non-prod only |
| `DemoCheckpointRecorded.v1` | run/step, expected/observed safe state, status, quality, trace refs | Checkpoint evidence |
| `DemoRunCompleted.v1` | run, result, failure class, checkpoint summary | Non-prod only |
| `IntegrationDataQualityChanged.v1` | source/provider/projection, old/new status, lag, watermark | Ops/console freshness |
| `IntegrationOperationAuditReferenced.v1` | operation type/resource/scope/actor/workload/reason, redaction version | Audit reference; no raw body |

Produced events never include raw provider request/response, signature, secret, OTP or unmasked contact.

## 8. PostgreSQL data contract

Database is owned exclusively by Integration Runtime. Demo Control DB is a separate non-production database. Every column requires `COMMENT ON COLUMN` with purpose, PII class and retention class.

### 8.1 `invoice_recipient_snapshots`

| Column | Type | Required | Description |
|---|---|---:|---|
| `id` | UUID | yes | Snapshot ID |
| `tenant_id` | UUID | yes | Owning tenant |
| `branch_id` | UUID | yes | Capture branch |
| `order_id` | UUID | yes | Bound Order |
| `checkout_attempt_id` | UUID | yes | Bound checkout attempt |
| `channel` | TEXT | yes | `EMAIL` or `ZALO` |
| `encrypted_contact` | BYTEA/TEXT ref | yes | Encrypted/tokenized contact; restricted PII |
| `masked_contact` | TEXT | yes | Safe display mask |
| `consent_policy_version` | TEXT | yes | Consent policy |
| `consent_text_version` | TEXT | yes | Presented text version |
| `consented_at` | TIMESTAMPTZ | yes | Consent capture time |
| `status` | TEXT | yes | `ACTIVE`, `REVOKED`, `EXPIRED`, `USED` |
| `version` | INT | yes | Immutable snapshot version |
| `expires_at` | TIMESTAMPTZ | yes | Contact/intent expiry |
| `created_at` | TIMESTAMPTZ | yes | Creation time |

### 8.2 `invoice_operations`

| Column | Type | Required | Description |
|---|---|---:|---|
| `id` | UUID | yes | Invoice operation ID |
| `tenant_id`/`branch_id` | UUID | yes | Scope |
| `order_id`/`checkout_attempt_id` | UUID | yes | Source business references |
| `source_event_id` | UUID | yes | Canonical OrderPaid event |
| `source_version` | BIGINT | yes | Order source/aggregate version |
| `profile` | TEXT | yes | `SANDBOX_TECHNICAL` |
| `status` | TEXT | yes | `REQUESTED`, `ISSUING`, `ISSUED`, `FAILED_RETRYABLE`, `FAILED_TERMINAL` |
| `currency` | TEXT | yes | `VND` MVP |
| `subtotal_minor`/`discount_minor`/`vat_minor`/`total_minor` | BIGINT | yes | Trusted OrderPaid money snapshot |
| `technical_reference` | TEXT | no | Safe provider reference |
| `artifact_ref` | TEXT | no | Encrypted/minimized artifact reference |
| `version` | INT | yes | CAS version |
| `next_attempt_at` | TIMESTAMPTZ | no | Retry schedule |
| `quality_code` | TEXT | no | Missing/stale/normalized quality |
| `created_at`/`updated_at` | TIMESTAMPTZ | yes | Lifecycle timestamps |

### 8.3 `invoice_items`

Stores immutable invoice-safe line snapshots: item/variant IDs, redacted display names, quantity, unit/discount/VAT/total minor amounts, currency, allocation version and source line ID. No raw modifier/customer/payment secret.

### 8.4 Provider/delivery tables

`invoice_provider_attempts` fields: `id`, tenant, operation, provider key/profile, idempotency key, attempt number, state, normalized code, safe provider ref, retry-after, started/finished/next-attempt timestamps, response fingerprint, correlation and error class.

`delivery_operations` fields: `id`, tenant/branch, invoice operation, channel, recipient snapshot/version, state, attempt count, expiry, current provider ref, version and timestamps.

`delivery_provider_attempts` fields: `id`, delivery operation, provider key, idempotency key, attempt number, state, normalized code, safe provider ref, retry-after, callback event key/fingerprint and timestamps.

### 8.5 Infrastructure/control tables

| Table | Required columns/meaning |
|---|---|
| `inbox_events` | producer, source event ID/version, event type, tenant/branch, status, received/processed timestamps, error class, retry count, payload fingerprint |
| `outbox_messages` | event ID/type/version, aggregate, tenant/branch, payload reference, publish state, next attempt, retry count, DLQ/error and timestamps |
| `projection_watermarks` | source/projection/scope, watermark/version, freshness/completeness status, last updated, gap/error reference |
| `audit_references` | tenant/branch, actor/workload, action/resource, reason, redacted before/after summary, correlation, Audit reference and created time |

### 8.6 Demo Control DB

`demo_runs`: run/scenario/seed version, environment, namespace, owner, state/version, expiry/lease, created/finished times.  
`demo_resource_manifests`: run, parent service, resource type/opaque ID, cleanup order/status, deletion policy.  
`demo_checkpoints`: run, step/sequence, expected/observed safe state, status/quality, trace refs, correlation and timestamps.  
`fault_leases`: run, environment, fault type/target/step, owner/reason, status, issued/expires/revoked times, kill-switch version.  
`demo_reset_operations`: run, idempotency key, state, resource counts, error class and timestamps.

## 9. Constraints, indexes and integrity

### 9.1 Required constraints

- All runtime rows have tenant; branch is required for branch-scoped resources.
- `invoice_recipient_snapshots` unique `(tenant_id, order_id, checkout_attempt_id, version)`; versions immutable.
- `invoice_operations` unique `(tenant_id, order_id, checkout_attempt_id, profile)` and unique `(tenant_id, source_event_id)`.
- `invoice_provider_attempts.idempotency_key` unique per provider/tenant; attempts append-only.
- `delivery_operations` unique `(tenant_id, invoice_operation_id, channel, recipient_snapshot_id)` for the current lineage.
- Delivery/provider state transitions use allowlisted enums and CAS `version`.
- Money fields are non-negative VND minor units and reconcile to source OrderPaid snapshot; negative/overflow values reject the event.
- Inbox source dedupe uses unique `(producer, source_event_id)`; replay preserves original IDs.
- Outbox insert commits atomically with local state transition.
- Demo run/fault lease environment check is mandatory; active fault lease unique per run/target/step.
- Reset/execute/fault operations require lease/version; stale mutation returns conflict without partial write.
- No application update/delete path for provider attempts, consent history, checkpoints or audit references; restricted retention/archive operation only.

### 9.2 Required indexes

| Name | Columns | Purpose |
|---|---|---|
| `idx_invoice_scope_time` | tenant, branch, created_at DESC, id DESC | Scoped invoice list |
| `idx_invoice_order_attempt` | tenant, order_id, checkout_attempt_id | Detail/dedupe |
| `idx_invoice_worker` | tenant, status, next_attempt_at | Issue worker |
| `idx_delivery_worker` | tenant, status, next_attempt_at, channel | Delivery worker |
| `idx_delivery_invoice` | tenant, invoice_operation_id, created_at DESC | Delivery history |
| `uq_inbox_source` | producer, source_event_id | Event dedupe |
| `idx_inbox_retry` | status, next_attempt_at | Retry/DLQ |
| `idx_outbox_publish` | status, next_attempt_at | Publisher |
| `idx_watermark_health` | source, scope, last_updated_at | Freshness alert |
| `idx_demo_run_state` | environment, state, expires_at | Run lease/cleanup |
| `idx_demo_checkpoint` | demo_run_id, sequence | Run summary |
| `idx_fault_lease_expiry` | environment, status, expires_at | Auto-expiry/kill switch |

## 10. Retention, privacy and legal hold contract

Default MVP policy (subject to legal/provider review before production tax capability):

| Data class | Default retention | Rule |
|---|---:|---|
| Safe invoice operation/line metadata | 2 years | Keep technical lineage/status; archive/delete after expiry unless Audit reference/hold requires longer |
| Consent metadata/hash/masked contact | 2 years | Keep proof metadata; no raw contact after contact snapshot expiry |
| Encrypted raw contact snapshot | 30 days after terminal invoice/delivery | Delete key/materialized contact after expiry; hold blocks deletion |
| Provider raw payload | 0 days by default | Store only normalized safe result/fingerprint; encrypted exception requires explicit ops policy |
| Provider attempts/delivery status | 2 years | Keep retry/reconciliation lineage, no raw payload |
| Audit references | Parent Audit policy, minimum 7 years where classified as evidence | Integration stores reference, Audit owns forensic retention/legal hold |
| Demo runs/checkpoints/fault leases | 90 days | Non-prod TTL cleanup; retain redacted summary longer only if operational policy requires |
| Outbox/inbox/telemetry | 90 days or replay policy | Delete/archive after replay window; never delete unresolved DLQ without runbook |

Legal hold contains tenant/scope/data class/range/reason/actor and overrides deletion. Retention operations are idempotent, count rows/artifacts, record hold decision and emit an audit reference. Production legal invoice retention is a separate capability decision.

## 11. Error, retry and recovery contract

| Condition | Normalized code/state | Behavior |
|---|---|---|
| Duplicate source event | `duplicate_source` | No-op; ACK after dedupe |
| Same idempotency key/different request | `idempotency_conflict` | HTTP 409/no provider call |
| Provider timeout/429/5xx | `provider_retryable` | Backoff, retry budget, then DLQ |
| Provider unknown after timeout | `provider_unknown` | Reconciliation query; never assume failure/success |
| Invalid invoice data | `invoice_data_invalid` | Terminal failure; no repeated provider call |
| Consent invalid/revoked | `consent_invalid` | Skip/cancel delivery; invoice state unchanged |
| Scope/environment denied | `forbidden` | Fail closed; safe error + audit reference |
| Stale CAS/version | `version_conflict` | 409; no partial write |
| Integration DB unavailable | `integration_unavailable` | Do not acknowledge event; broker retry/DLQ |
| Trace exporter unavailable | `trace_degraded` | Continue business path; checkpoint quality degraded + alert |
| Demo reset conflict | `demo_run_conflict` | No cleanup; caller refetches/retries |

## 12. Observability and audit contract

Required span/log fields: service, environment, safe tenant/branch scope hash, operation, event type/version, order/attempt/invoice/delivery/demo IDs, correlation/causation/saga IDs, provider key/profile, state, normalized error, retry count and duration.

Never log/span: raw email, phone/Zalo ID, OTP, JWT, provider signature, secret, raw payment payload, raw provider response, SQL or unbounded customer note.

Metrics:

- `order_paid_consumer_lag`, `invoice_operation_lag`, `invoice_issue_success_rate`, `invoice_provider_latency`, `invoice_retry_count`;
- `delivery_success_rate{channel}`, `delivery_latency{channel}`, `delivery_retry_count`, `delivery_expiry_count`;
- `inbox_duplicate_count`, `outbox_lag`, `dlq_depth`, `watermark_age`, `provider_unknown_count`;
- `demo_preflight_failure`, `demo_step_duration`, `demo_checkpoint_failure`, `demo_reset_duration`, `fault_lease_active_count`;
- `scope_denial`, `redaction_failure`, `trace_export_failure`.

Audit reference payload: tenant/branch, actor/workload, action, resource type/ID, reason, safe before/after summary, correlation, redaction version and source operation. Audit service remains source-of-record for seven-year evidence/legal hold.

## 13. Contract test matrix

1. OpenAPI validates envelope, auth, scope, idempotency, ETag/CAS, cursor, error and no-cache rules.
2. `OrderPaid` current parent compatibility (`OrderPaid` + version 1) and new `OrderPaid.v1` schema both converge to one operation.
3. Missing/invalid/foreign/expired `invoiceRecipientRef` never leaks contact and never creates unauthorized delivery.
4. Duplicate/out-of-order/replayed `OrderPaid` creates one invoice operation and one technical issue side effect.
5. Provider accepted then worker crash converges through same idempotency key/reconciliation.
6. Timeout/429/5xx/invalid response map to retryable/unknown/terminal states without Order mutation.
7. Email/Zalo delivery success, duplicate callback, rate limit, invalid contact, consent revocation, expiry and manual retry are deterministic.
8. Tenant/branch/role/revoked-membership/IDOR/cache tests cover all public and internal detail/list/mutation endpoints.
9. Demo production guard rejects seed/reset/fault endpoints; two runs remain namespace/trace isolated.
10. Fault lease expiry/revoke/kill switch cannot leave a global Payment outage; Saga releases Inventory exactly once.
11. Demo success checkpoints verify Shift, Recommendation, Loyalty, Payment/IPN, OrderPaid, Inventory, Reporting and AI provenance.
12. AI attribution golden fixtures cover shown-only, accepted-without-add, add-without-paid, late/partial data and correct uplift metric.
13. Retention removes raw contact by policy, preserves required safe lineage, respects legal hold and is idempotent.
14. Outbox/inbox/DLQ replay preserves event IDs and never duplicates invoice/delivery/demo side effects.
15. Load profile validates 10 paid events/sec, 100 delivery attempts and 10 concurrent demo runs with bounded queue/API latency.

## 14. Parent contract amendments

### POS Platform / Identity

- Add Integration permissions and online scope/environment decision fields.
- Register Integration event routes, schema versions, source dedupe and Audit reference ownership.
- Keep Demo Control Plane routes/roles out of production deployment and generated public clients.

### Order

- Add optional opaque `invoiceRecipientRef` and `invoiceProfile` to checkout context and `OrderPaid.v1`.
- Normalize event name/version mapping, source/aggregate version, checkout attempt, safe lines/totals and correlation/causation.
- Add canonical compensation/release evidence or approved checkpoint API for demo verification.

### Payment / Step Functions

- Keep Payment IPN/checksum and `PaymentPaid.v1` ownership in Payment; expose safe non-prod fault lease context at controlled boundary.
- Preserve Order Saga compensation and late-payment semantics; Integration only observes.

### Inventory / Loyalty / Shift

- Publish approved non-prod checkpoint/read APIs and/or events for reservation release/commit, point accrual, customer context and shift state.

### Recommendation / Reporting / AI

- Publish authoritative add-line attribution and paid cohort lineage.
- Publish uplift/AOV metric dictionary, tool schema, freshness/completeness and provenance version.
- AI remains read-only; no Integration or LLM revenue calculation.

### Audit

- Register invoice/delivery/demo/fault/access references, redaction and retention/legal-hold classification.

## 15. Implementation artifacts required before code

- `openapi.yaml` for public invoice/recipient/status/retry APIs.
- `openapi-internal.yaml` for demo, health, reconciliation and provider operations.
- `asyncapi.yaml` + JSON Schemas for consumed/produced events and parent compatibility mapping.
- `provider-ports.schema.yaml` and sandbox adapter fixtures for invoice/email/Zalo.
- `demo-scenario.schema.yaml`, seed/resource manifest schema and checkpoint golden fixtures.
- PostgreSQL/Flyway migrations with column comments, PII/restricted roles, constraints, indexes and retention jobs.
- Contract fixtures for OrderPaid duplicate/replay, provider crash, callback signature, consent/revocation and out-of-scope access.
- Fault-lease security corpus and non-prod environment policy tests.
- AI uplift metric dictionary/golden data and Reporting/Recommendation function schemas.
- k6/load profile, DLQ/replay/runbook, X-Ray redaction tests and provider credential rotation procedure.

## 16. Contracts approval gate

- **Current status:** Approved — user accepted the Contracts review warnings.
- **Required before Finalization:** Completed; machine-readable schemas, parent amendments, migrations, provider vectors and demo fixtures remain implementation gates listed in the final result.
- **Implementation rule:** Không viết controller/worker/provider adapter/demo runner trực tiếp từ prose; generate machine-readable contracts and fixtures first.
