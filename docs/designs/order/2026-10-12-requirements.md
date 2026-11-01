Started at: 2026/10/12 16:30:09 +07
Finished at: 2026/10/12 16:30:09 +07
Total time: 0 minutes
---

# Requirements: Order Service

## Sources and Priority

- [Order Vision](./2026-10-12-vision.md) and [Spec Sentinel Report](./2026-10-12-vision-review.md).
- [POS Platform Requirements](../pos-platform/2026-10-12-requirements.md), [POS Platform Contracts](../pos-platform/2026-10-12-contracts.md), and [POS Platform Design Result](../pos-platform/2026-10-12-pos-platform_result.md).
- [Identity & Access Design Result](../identity-access/2026-10-12-identity-access_result.md).
- [Catalog Design Result](../catalog/2026-10-12-catalog_result.md).
- **P0:** required for the first Order vertical slice and automated acceptance. **P1/P2:** designed as compatible extensions, not required for P0 release.

Order is the source of truth for order lifecycle, line state, immutable Catalog snapshots and checkout attempt state. It does not own Catalog, Inventory, Payment, Loyalty or Identity data.

## Actors and Authorization

| Actor | P0 read | P0 write | Scope |
|---|---|---|---|
| Owner | Yes | Yes | Tenant-wide access to selected branch orders; branch context is explicit for order operations. |
| Manager | Yes | Yes | Active membership in the target branch; can edit drafts and cancel lines in that branch. |
| Cashier | Yes | Yes | Active membership in the target branch; can create/edit drafts and checkout, but cannot cancel a line. |
| Kitchen | No Order mutation | No | KDS read is P1; no direct Order workflow in P0. |
| Order workload/Saga | Internal state callbacks | Internal commands | mTLS/workload identity plus explicit tenant/branch/order ownership checks. |
| Payment callback | No public Order access | Internal event/callback | Payment service identity/event contract only; a provider never calls Order directly. |
| Customer | No P0 direct access | No P0 direct access | QR self-order is P2 and requires a separate anti-tampering contract. |

Authorization rules:

- Identity must resolve current authorization for every protected request. JWT role/branch claims and client-provided IDs are not authority.
- Every query and mutation scopes by `tenant_id` and, for branch operations, `branch_id`. Path IDs, body IDs, batch lines and internal Saga references are checked against the same scope.
- Owner must select/target a branch for branch-scoped order operations; tenant-wide Owner authority does not make an order branchless.
- Manager and Cashier require an ACTIVE membership in the target branch at request time. A revoked membership denies the next request even if the old JWT is still valid.
- Cashier can add/edit lines and initiate checkout while the order is `DRAFT`; Cashier cannot cancel a line or force a failed compensation.
- Owner/Manager can cancel an active line only while the Order is `DRAFT`; the action requires a non-empty reason and creates an audit event.
- `CHECKOUT_PENDING`, `PAID` and `CANCELLED` orders are not editable through public draft commands.
- Internal workload calls must include `tenantId`, `branchId`, `orderId`, `correlationId` and `sagaId`; Order validates all IDs locally and never trusts a caller-provided tenant alone.

## Functional Requirements

| ID | Priority | Actor | Action | Required response |
|---|---|---|---|---|
| ORD-UC-01 | P0 | Cashier/Manager/Owner | Create an order for counter, takeaway or table service | Creates a `DRAFT` with branch/tenant scope, service mode and optional `tableReference`. |
| ORD-UC-02 | P0 | Cashier/Manager/Owner | Add a Catalog item line | Calls Catalog resolver, stores current name/price/variant/modifier snapshot and returns a new order version. |
| ORD-UC-03 | P0 | Cashier/Manager/Owner | Edit quantity, variant, modifiers or item note | Re-resolves Catalog and atomically replaces the line snapshot; stale version returns `409`. |
| ORD-UC-04 | P0 | Cashier/Manager/Owner | Edit order note/service metadata in `DRAFT` | Validates length/sanitization and preserves branch/service-mode invariants. |
| ORD-UC-05 | P0 | Cashier/Manager/Owner | Read one order or branch order list | Returns only authorized tenant/branch data with immutable snapshots and current lifecycle state. |
| ORD-UC-06 | P0 | Owner/Manager | Cancel an active line | Requires `DRAFT`, reason and `If-Match`; records actor/before/after audit data. |
| ORD-UC-07 | P0 | Order | Cancel the final active line | Atomically transitions the Order to terminal `CANCELLED`; no empty draft remains reusable. |
| ORD-UC-08 | P0 | Cashier/Manager/Owner | Refresh a stale Catalog snapshot | Returns changed-field/price preview; only explicit confirmation replaces snapshots and increments Order version. |
| ORD-UC-09 | P0 | Cashier/Manager/Owner | Start checkout | Validates active lines, total, current Catalog version and branch scope; locks order and starts one Saga attempt. |
| ORD-UC-10 | P0 | Saga | Reserve ingredients | Calls Inventory with idempotent `sagaId`/attempt key; insufficient stock fails checkout without `PAID`. |
| ORD-UC-11 | P0 | Saga/Payment | Pay cash | Validates received amount, calculates integer change, confirms payment and finalizes Order once. |
| ORD-UC-12 | P0 | Saga/Payment | Pay by VNPAY QR | Initiates payment, waits for verified IPN, keeps reservation for at most 15 minutes and returns pending state. |
| ORD-UC-13 | P0 | Saga | Complete successful checkout | Commits/consumes reservation, finalizes Order `PAID`, writes outbox event and never duplicates `OrderPaid`. |
| ORD-UC-14 | P0 | Saga | Compensate failed checkout | Closes/expires payment as applicable, releases reservation exactly once and moves Order to `CHECKOUT_FAILED`. |
| ORD-UC-15 | P0 | Cashier/Manager/Owner | Retry failed checkout | Allows a new attempt only after previous compensation is terminal/safe; creates a new attempt key without changing historical attempts. |
| ORD-UC-16 | P0 | Payment reconciliation | Process late VNPAY success after release | Does not resurrect Order; records a `late_payment_reconciliation` exception for Payment/operator handling. |
| ORD-UC-17 | P0 | Order | Handle failed compensation | Keeps `CHECKOUT_FAILED`, records `compensationStatus=RETRYING|FAILED`, blocks new checkout and exposes operational status. |
| ORD-UC-18 | P0 | Order | Publish lifecycle and paid-order events | Commits state and outbox record atomically; consumers can deduplicate by event ID. |
| ORD-UC-19 | P1 | Manager/Owner | Hold and resume an order | Feature-gated draft retention policy; does not change P0 order identity/snapshot semantics. |
| ORD-UC-20 | P1 | Manager/Owner | Manage table map and table occupancy | Adds owned table resources and conflict rules; `tableReference` remains compatible. |
| ORD-UC-21 | P1 | Manager/Owner | Split or merge invoices | Creates explicit child/merge audit links and preserves immutable payment/order history. |
| ORD-UC-22 | P1 | Kitchen | Receive order items in KDS | Publishes durable order-item events and delivers realtime WebSocket updates with reconnect/replay semantics. |
| ORD-UC-23 | P2 | Customer | Self-order from table QR | Uses a separate customer/session authorization boundary and anti-tampering checks; no trust in QR parameters alone. |

## Business Rules

### Order and Line Lifecycle

1. `OrderStatus` is `DRAFT`, `CHECKOUT_PENDING`, `PAID`, `CHECKOUT_FAILED` or `CANCELLED`.
2. Valid Order transitions are:
   - `DRAFT -> CHECKOUT_PENDING` only after current Catalog resolution and total validation.
   - `DRAFT -> CANCELLED` when its last active line is cancelled.
   - `CHECKOUT_PENDING -> PAID` only after Saga confirms payment and the required Inventory completion step.
   - `CHECKOUT_PENDING -> CHECKOUT_FAILED` after terminal payment/reservation failure or timeout and recorded compensation result.
   - `CHECKOUT_FAILED -> CHECKOUT_PENDING` only through a new checkout attempt after prior compensation is terminal/safe.
   - `PAID` and `CANCELLED` are terminal in P0.
3. A `CHECKOUT_PENDING` order is locked against public line/order edits and line cancellation. Saga callbacks may update only the authorized checkout fields.
4. An Order must have at least one active line to start checkout. Cancelled lines remain for history and are excluded from totals.
5. Each line has `ACTIVE` or `CANCELLED` status. A line cancellation stores actor, role, reason, timestamp, prior snapshot reference and new Order version.
6. A `TABLE` order stores a free-form `tableReference` in P0. It is display metadata; P0 does not enforce uniqueness or table occupancy.
7. Orders are visible only within their tenant and authorized branch scope. A branch cannot be changed after Order creation.

### Catalog Snapshot and Draft Editing

8. Add/edit line calls the Catalog private resolver with item, concrete variant and selected modifier IDs. New Order clients must send `variantId`; legacy null resolves only when exactly one active variant exists.
9. A line stores immutable-at-checkout snapshot fields: Catalog item ID, variant ID, item/variant names, modifier IDs/names/price deltas, base unit price, modifier unit price, unit price, quantity and Catalog version.
10. Editing quantity or note without changing Catalog selection may reuse the current line snapshot, but still requires `If-Match` and recalculates totals atomically.
11. Editing item/variant/modifiers re-resolves Catalog. Foreign, inactive, ambiguous or invalid selections fail without changing the existing line.
12. Checkout re-resolves all active lines and compares the effective Catalog selection/price/version with the draft snapshot. A mismatch returns typed `catalog_changed`; no snapshot is silently replaced during checkout.
13. A reprice/refresh command returns a diff. The client must explicitly confirm before replacing affected snapshots. Confirmation and replacement occur in one transaction.
14. Two authorized operators may edit the same draft, but the stale `If-Match` request fails with `409`; last-write-wins is prohibited.

### Money and Totals

15. Order uses integer `BIGINT` money in VND; floating point is prohibited.
16. P0 subtotal is the sum of `unitPriceMinor * quantity` across active lines and selected modifier deltas already resolved by Catalog.
17. P0 discounts/promotions are not applied by Order. `discountMinor` is zero unless a separately approved pricing contract is supplied.
18. Order owns the final payable total and stores `subtotalMinor`, `vatMinor`, `discountMinor`, `grandTotalMinor`, `currency` and `pricingPolicyVersion` in the checkout snapshot. P0 default `vatMinor` is zero until a tenant VAT policy contract is enabled; enabling nonzero VAT requires deterministic integer rounding tests.
19. `grandTotalMinor = subtotalMinor + vatMinor - discountMinor` and cannot be negative. Payment receives exactly the immutable checkout total.
20. Cash payment requires `cashReceivedMinor >= grandTotalMinor`; `changeMinor = cashReceivedMinor - grandTotalMinor`.
21. A price/total change after draft creation never mutates a previous checkout attempt; each retry has a new immutable total snapshot.

### Checkout Saga and Compensation

22. Order creates one `checkout_attempt` with a unique `attemptId`, `sagaId`, idempotency key, snapshot total, correlation ID and state before starting Step Functions.
23. Step Functions is the only P0 orchestrator. It calls idempotent service APIs; it never writes Order, Inventory or Payment databases directly.
24. P0 happy path is: validate draft → set `CHECKOUT_PENDING` → reserve Inventory → initiate/capture Payment → for VNPAY wait for verified IPN → commit Inventory reservation → finalize Order `PAID` → publish `OrderPaid`.
25. Cash payment may complete synchronously after reservation; VNPAY returns `PENDING` until verified IPN or timeout.
26. VNPAY reservation expiry is 15 minutes in the demo policy. Timeout closes/expires the payment attempt where supported, releases Inventory and moves Order to `CHECKOUT_FAILED`.
27. Payment failure before success releases Inventory exactly once and moves Order to `CHECKOUT_FAILED` after the compensation command is accepted/terminal.
28. If Inventory release/commit or another compensation step fails, Order remains `CHECKOUT_FAILED`, `compensationStatus` is `RETRYING` or `FAILED`, new checkout is blocked and Saga retries/DLQ/alerting continue.
29. If Payment is marked successful but Inventory commit cannot be completed, Saga must not silently mark `PAID` or release an unknown reservation. It retries commit; after terminal failure it records `paymentReconciliationRequired=true` and routes the exception to Payment/operator reconciliation.
30. A late verified VNPAY success after reservation release does not resurrect Order and does not start a new reservation automatically. Payment owns the `late_payment_reconciliation` exception.
31. Duplicate callbacks, retrying the same Saga task or replaying an event cannot create a second reservation, payment, finalization or `OrderPaid` event.
32. Checkout failure retains the draft/snapshots and historical failed attempt. Retry creates a new attempt only after the previous compensation is safe.

### Audit and Events

33. Line cancellation emits a transactional outbox audit event containing tenant, branch, order/line IDs, actor/user ID, role, reason, correlation ID, prior line snapshot summary and resulting state/version. Raw JWT, secrets and unnecessary customer PII are excluded.
34. Audit delivery is asynchronous with retry/DLQ; an Audit consumer outage must not split the Order mutation from its outbox record.
35. `OrderCreated`, `OrderCancelled`, `OrderCheckoutFailed` and canonical `OrderPaid` events carry event ID/version, tenant/branch, order ID, occurred time, correlation ID and relevant immutable snapshot data.
36. `OrderPaid` is the only canonical paid-order fact for Loyalty and Reporting. `PaymentPaid` is not sufficient for points or revenue projection.
37. Event consumers deduplicate by event ID and validate tenant scope. Eventual projection lag is observable and must not authorize an Order mutation or checkout.

## Checkout Attempt State

`CheckoutAttemptStatus`:

| State | Meaning | Terminal |
|---|---|---:|
| `STARTED` | Order accepted checkout command and created attempt | No |
| `RESERVATION_PENDING` | Saga requested Inventory reservation | No |
| `RESERVED` | Inventory reservation succeeded | No |
| `PAYMENT_PENDING` | Payment initiated; waiting for cash confirmation or VNPAY IPN | No |
| `PAYMENT_SUCCEEDED` | Payment reports success; Inventory commit/finalization remains | No |
| `FINALIZING` | Saga is committing reservation/finalizing Order | No |
| `COMPLETED` | Order is `PAID` and `OrderPaid` is durably outboxed | Yes |
| `COMPENSATING` | Payment/reservation failure requires compensation | No |
| `FAILED` | Order is `CHECKOUT_FAILED`; compensation is safe/terminal | Yes |
| `COMPENSATION_FAILED` | Order is `CHECKOUT_FAILED`; operational intervention is required | Yes for retry lock |
| `LATE_PAYMENT_RECONCILIATION` | Provider success arrived after Order compensation/release | Yes for Order attempt |

`compensationStatus` and `paymentReconciliationRequired` remain visible on the order/attempt detail for authorized Manager/Owner and internal operators.

## Integration Requirements

| Boundary | Requirement |
|---|---|
| Identity | Online current authorization for every public request; fail closed on unavailable decision. |
| Catalog | Private bulk resolution returns coherent item/variant/modifier names, prices, versions and typed rejection; Order snapshots the response. |
| Inventory | Saga-only reserve/commit/release commands; Inventory owns recipe/stock and no Order DB reads. |
| Payment | Internal initiate/confirm/expire/reconcile contract; Payment owns provider state, IPN signature and late settlement exception. |
| Step Functions | Execution input/output includes `sagaId`, `attemptId`, `orderId`, tenant/branch and correlation; callbacks are authenticated and idempotent. |
| Audit | Order emits audit outbox event; Audit owns persistence/retention/query. |
| Loyalty | Consume canonical `OrderPaid`; customer reference is opaque and Loyalty owns PII/profile lookup. |
| Reporting | Consume versioned lifecycle/paid-order events into a read model; no reporting query joins Order DB at runtime. |
| Angular POS | Uses generated OpenAPI client, `If-Match`, idempotency key and explicit pending/failed/reprice states. |
| KDS P1 | Consume durable Order item events and add WebSocket delivery/replay without making WebSocket the source of truth. |

## Non-Functional Requirements

These targets are demo/MVP targets inherited from the parent platform and must be measured rather than assumed.

| Category | Requirement | Target |
|---|---|---|
| Performance | Create/update draft line, excluding cold dependency failure | p95 ≤ 500 ms under representative demo menu. |
| Performance | Checkout initiation response | p95 ≤ 1 s for cash/QR initiation; QR may return `202 PENDING`. |
| Throughput | Checkout attempts | At least 10 attempts/second for 5 minutes without duplicate side effects. |
| Consistency | Local Order transition | Order, line/snapshot/attempt mutation and outbox record commit atomically. |
| Consistency | Checkout | Never expose `PAID` before required Saga conditions; retries are idempotent. |
| Reliability | Compensation | Reservation release/commit commands retry with bounded exponential backoff and DLQ. |
| Reliability | Recovery | Failed checkout is retryable only after prior compensation is safe; late payment is visible. |
| Security | Isolation | Every repository query includes tenant/branch predicates; BOLA/IDOR negative tests required. |
| Security | Input | Validate service mode, table reference, quantities, notes, IDs, money and state transitions; sanitize notes. |
| Security | Audit | Cancellation, authorization denial, checkout failure and reconciliation exceptions are traceable. |
| Observability | Trace | Structured logs and OpenTelemetry carry request/correlation/saga/attempt/order IDs. |
| Observability | Metrics | Track version conflicts, pending age, reservation/payment failures, compensation, late payment, outbox and DLQ. |
| Availability | Dependency failure | Catalog/Identity/Inventory/Payment calls use timeout, circuit breaker and bounded retry; no unbounded chain. |
| Maintainability | Contract discipline | OpenAPI/AsyncAPI and event schemas validate in CI; generated client is source of truth for Angular. |

## Data and Constraints for Architecture

- PostgreSQL service-owned Order DB; UUID v7 IDs; UTC `TIMESTAMPTZ`; integer VND money.
- Core tables: `orders`, `order_lines`, `order_line_modifiers`, `checkout_attempts`, `outbox_messages`, `idempotency_records` and optional `order_state_transitions`/reconciliation reference fields.
- `orders` stores tenant/branch, service mode, table reference, status, totals, version, active line count, current attempt reference and timestamps.
- `order_lines` stores opaque Catalog IDs, immutable item/variant/name/price snapshots, quantity, note, status, cancellation metadata and version.
- Modifier snapshot data must preserve option IDs, names and price deltas used by the Order total; no runtime Catalog read is needed to render historical Order lines.
- `checkout_attempts` stores saga/attempt IDs, status, idempotency key, snapshot totals, payment/reservation opaque references, compensation status, timeout/reconciliation flags and failure reason code.
- No runtime FK to Identity, Catalog, Inventory, Payment, Loyalty or Audit databases. Cross-service IDs are validated through contracts and local tenant/branch context.
- Enforce one active checkout attempt per Order, unique `(tenant_id, order_id, attempt_id)` and idempotency uniqueness by tenant + operation + key.
- Enforce nonnegative quantity, valid integer money, supported service mode/status values, branch immutability and terminal-state transition rules.
- Index order lists by `(tenant_id, branch_id, status, created_at DESC)`, active draft lookup, order number, checkout status/timeouts and outbox publication state.
- Every migration column has a PostgreSQL comment/description. Flyway migrations are forward-compatible and seed deterministic multi-tenant/multi-branch data.

## Acceptance Tests

1. Cashier creates counter, takeaway and table orders; table reference is stored but not unique-enforced.
2. Cashier/Manager in the same branch can edit a draft; a different branch or tenant receives `403/404` without data leakage.
3. A stale `If-Match` cannot overwrite a concurrent draft update.
4. Add/edit item, variant and modifiers snapshots the Catalog response; invalid/foreign/inactive selections do not mutate the line.
5. Catalog change between draft and checkout returns `catalog_changed`; checkout does not silently reprice.
6. Explicit reprice/refresh updates snapshots only after client confirmation and increments Order version.
7. Cashier cannot cancel a line; Owner/Manager can cancel in `DRAFT` only with a non-empty reason and audit event.
8. Cancelling the final active line atomically produces `CANCELLED`; no empty reusable draft exists.
9. Edit/cancel attempts on `CHECKOUT_PENDING`, `PAID` or `CANCELLED` are rejected.
10. Cash checkout calculates exact integer change and reaches `PAID` once under duplicate command/retry.
11. VNPAY initiation returns pending, keeps reservation within 15 minutes, and a valid IPN can complete the Saga once.
12. Invalid/duplicate/out-of-order IPN never duplicates Payment, finalization or `OrderPaid`.
13. Inventory insufficient/payment failure/timeout releases reservation exactly once and yields `CHECKOUT_FAILED`.
14. A failed compensation blocks new checkout, exposes retry/failure status and produces retry/DLQ/alert evidence.
15. Late VNPAY success after release creates reconciliation exception and never resurrects the Order.
16. Payment success plus Inventory commit failure never silently creates `PAID`; it retries or exposes payment reconciliation required.
17. Failed checkout can retry after safe compensation with a new attempt and no duplicate historical attempt side effects.
18. `OrderPaid` is emitted once and is the only event consumed for Loyalty points and Reporting revenue.
19. Audit and operational logs contain correlation/saga/actor/order context and no raw JWT, secret, full note or unnecessary PII.
20. k6 and failure-injection profiles meet throughput/latency targets and demonstrate no duplicate side effect.

## Parent Contract Amendments Required

| Parent statement | Order decision | Required follow-up |
|---|---|---|
| `OrderLineInput.variantId` is nullable | New clients send concrete variant; legacy null resolves only when exactly one active variant exists. | Update Order/Catalog resolver contract and generated DTOs. |
| Order response has line snapshots but not full Catalog version/diff | Order exposes item/variant/modifier snapshot versions and typed `catalog_changed`. | Add fields and error code to parent contract. |
| Checkout API lists `202/200` but not compensation state | Response exposes `sagaId`, `attemptId`, pending/failure/compensation/reconciliation state. | Update CheckoutResponse and Order state schema. |
| Payment/Inventory APIs are generic Saga endpoints | Order requires commit/release/idempotency and late-payment reconciliation semantics. | Align internal service contracts and Step Functions state input/output. |
| Parent events include `PaymentPaid` and `OrderPaid` | `OrderPaid` is canonical for Loyalty/Reporting; PaymentPaid is not a revenue/points fact. | Preserve consumer routing and add duplicate/replay tests. |
| Cancel line endpoint exists for Manager | Owner/Manager only, `DRAFT` only, reason/audit required, last line cancels Order. | Add authorization, state and audit schema constraints. |

## Decisions Carried to Architecture and Contracts

1. Exact Order/line/checkout-attempt schema and state transition constraints.
2. Public draft, cancel, reprice and checkout endpoints plus internal Saga callbacks.
3. Exact totals/VAT/discount policy and immutable checkout total snapshot.
4. Step Functions state machine, task idempotency and compensation/reconciliation flow.
5. Event schemas for Order lifecycle, `OrderPaid`, audit and P1 KDS delivery.
6. Indexes/retention for branch order lists, pending attempts, outbox and audit references.

## Domain Skills Referenced

- **spec-reviewer:** converted eight Vision warnings into explicit Order requirements, acceptance tests and parent contract amendments.
- **api-design:** public draft/checkout/cancel/reprice APIs, optimistic concurrency, idempotency, standard errors and internal workload boundaries.
- **database-design:** immutable snapshots, checkout attempts, state constraints, tenant/branch indexes, UUID v7, integer money and migration rules.
- **log-processing:** structured audit/outbox/Saga telemetry, correlation fields and sensitive-data redaction.

## Approval

- **Status:** Approved by user
- **Phase:** Requirements Discovery
- **Next phase after approval:** Architecture Design
