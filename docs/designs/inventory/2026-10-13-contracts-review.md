# Spec Sentinel Report

**Spec:** [`docs/designs/inventory/2026-10-13-contracts.md`](./2026-10-13-contracts.md)  
**Date:** 2026-10-13  
**Status:** ⚠️ Warnings (Proceed with Caution)

## Review context

- Reused the approved Inventory Vision, Requirements, Architecture and sequence diagrams.
- Applied the `spec-reviewer` gate to the API, event and PostgreSQL contracts.
- Cross-checked the current POS Platform, Order, Payment, Catalog and Identity contract documents.
- Repository contains design documents but no Inventory implementation, generated OpenAPI/AsyncAPI artifacts or executable Flyway migrations yet.
- The ambiguous availability method was resolved in this review: the internal contract now uses `POST` with an `AvailabilityRequest` body, and the parent POS Platform contract was amended to match.

## Findings

## Warnings

| # | Category | Finding | Risk | Recommendation |
|---|---|---|---|---|
| 1 | API schema coverage | The new branch reservation-history route is defined with a `ReservationCursorPage` and field rules, but the implementation artifacts still need an executable OpenAPI schema/example for cursor binding, status filters and stock-detail redaction. | Independently generated clients may expose different fields or allow an unsafe filter/sort combination. | Generate the OpenAPI component and contract fixtures from §7.3/§8 before implementation; test cursor scope/filter binding and role-based detail redaction. |
| 2 | P1 command surface | The PostgreSQL contract models `receipts.status=CANCELLED` and ledger movements `WASTE`/`RETURN`, while the public endpoint table currently specifies create/read paths but not separate cancel/waste/return command routes. | P1 operators may implement state changes by editing a receipt or ledger row, violating append-only history. | Add explicit P1 command contracts before implementing those flows; each command must append a new ledger entry, require reason/source/idempotency and never mutate historical entries. |
| 3 | Data contract materialization | Low-stock, forecast and outbox tables use compact column summaries in parts of §10, although the contract requires a typed column and `COMMENT ON COLUMN` for every field. | DDL generators or migrations may omit nullability, checks, lease recovery fields or comments. | Expand these compact rows into generated DDL/schema fixtures and migration assertions; fail CI when a contract column or comment is missing. |
| 4 | Test artifact readiness | Contract tests are listed for recipe expansion, lock races, count approval, alert re-arm, forecast quality and outbox replay, but no executable fixtures or generated schemas exist yet. | A code implementation can satisfy the prose while drifting on decimal rounding, terminal idempotency or event payload shape. | Make OpenAPI/AsyncAPI validation, Flyway empty/copy-data checks and the listed concurrency fixtures implementation prerequisites. |

## Cross-Impact Analysis

| Dimension | Finding | Risk Level | Recommendation |
|---|---|---|---|
| **Shared Logic** | No Inventory source call sites exist. The direct consumers are Step Functions/Saga, Order, Reporting, Audit and Notification through the documented internal APIs/events. | 🟡 Medium | Generate clients from the single contract and prohibit shared database/repository code across services. |
| **Data/State** | Inventory introduces recipe snapshots, branch balances, reservations, append-only ledger, count adjustments, alerts, forecasts, outbox and idempotency state. | 🔴 High | Preserve reservation-first locking, sorted balance locks, tenant predicates, local constraints and projection rebuild checks in migrations/tests. |
| **API Contracts** | Reserve/commit/release semantics align with Order and Payment. Availability was changed to an unambiguous POST body contract and synchronized in POS Platform. | 🟡 Medium | Regenerate parent/client contracts and add consumer-driven fixtures for availability, commit context and reconciliation outcomes. |
| **Flow Integrity** | Reserve → payment fact → Saga commit → Order finalize and failure/expiry release paths are traced, including terminal reconciliation. | 🟢 Low | Keep Inventory free of direct `PaymentPaid` consumption and test duplicate/out-of-order Saga commands. |
| **Cross-Cutting** | Tenant/branch defense-in-depth, workload identity, idempotency, UTC/decimal policy, outbox retry and DLQ are explicit. | 🟡 Medium | Add security, lock-timeout, redaction, event-version and lease-recovery checks to CI/runbooks. |

## Instruction Compliance

| Dimension | Verdict | Deviation (if any) | Recommendation |
|---|---|---|---|
| **Conventions** | ✅ | REST/OpenAPI 3.1, UUID v7, UTC, common envelopes, pagination allowlists and event envelope follow parent conventions. | — |
| **Architecture** | ✅ | Reservation/commit/release ownership, PostgreSQL transaction boundaries, outbox and reconciliation responsibilities match the approved architecture. | — |
| **Testing** | ⚠️ | Required fixtures and generated contract artifacts are specified but not yet materialized in the repository. | Treat generated schemas, migration checks and concurrency/replay fixtures as an implementation gate. |

## Verdict

Contracts **có thể được duyệt với warnings**. Không còn blocker về ownership, tenant isolation, Saga boundary, reservation state machine hoặc database transaction semantics. Các warnings còn lại là điều kiện materialize contract/test artifacts và hoàn thiện P1 command surface trước khi code P1.

