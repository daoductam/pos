# Spec Sentinel Report

**Spec:** `docs/designs/payment/2026-10-12-contracts.md`  
**Date:** 2026-10-12  
**Status:** ⚠️ Warnings (Proceed with Caution)

## Findings

Không còn critical blocker trong API/data contract sau khi:

- IPN được chuẩn hóa thành `GET` query contract theo VNPAY sandbox.
- Late settlement được tách khỏi `PaymentPaid` và terminal payment state.
- Cash provider fields được nullable đúng ngữ nghĩa.
- PostgreSQL active-attempt constraint không dùng predicate `now()` không immutable.

### Warnings

| # | Category | Finding | Risk | Recommendation |
|---|---|---|---|---|
| 1 | Parent contract | Parent platform contract vẫn cần cập nhật method/path IPN, nullable cash fields, provider-scoped unique key và `PaymentReconciliationRequired`. | Generated clients/AsyncAPI/DB constraints có thể lệch với Payment contract. | Apply the ten amendments in Contracts §8 before implementation and regenerate contract fixtures. |
| 2 | Provider integration | VNPAY checksum algorithm, canonical query encoding, RspCode mapping và retry behavior phụ thuộc merchant sandbox configuration. | Signature hợp lệ có thể bị reject hoặc callback retry không đúng. | Record sanitized sandbox fixtures for SHA256/HMACSHA512 configuration and test `00/02/01/04/97/99` mapping. |
| 3 | Privacy/logging | IPN dùng GET query parameters; gateway/access logs có nguy cơ ghi full `vnp_*` query và checksum. | Provider payload/signature có thể bị lộ trong logs. | Redact query values, `vnp_SecureHash`, `vnp_OrderInfo` and raw callback fields at gateway and application logging. |
| 4 | Reconciliation operations | Contract có lease/retry/status nhưng operator SLA, escalation owner và exact retention configuration chưa được materialize thành deployment config. | Giao dịch treo có thể không có người xử lý hoặc bị lưu quá lâu. | Add config keys, alarms, runbook ownership and operator audit actions in implementation/deployment plan. |
| 5 | Reporting | Contract chốt branch timezone/business date nhưng report cross-branch aggregation policy cần được thể hiện trong reporting consumer schema. | Báo cáo nhiều branch có thể gộp sai ngày. | Carry `businessDate`/timezone policy into Reporting projection and expose freshness/aggregation metadata. |
| 6 | P1 provider capability | Refund endpoint/domain contract có, nhưng VNPAY refund capability, operator reason and provider timeout semantics cần fixture/adapter contract. | Refund có thể bị hiểu là synchronous success khi provider chỉ trả accepted/pending. | Keep refund state independent; finalize P1 only after query/refund fixtures and provider capability matrix. |

## Cross-Impact Analysis

| Dimension | Finding | Risk Level | Recommendation |
|---|---|---|---|
| **Shared Logic** | OpenAPI/AsyncAPI parent contracts and generated clients consume Payment fields; no implementation call sites exist yet. | 🟡 Medium | Regenerate clients/contracts only after parent amendments and schema review. |
| **Data/State** | Payment tables capture immutable attempt, callback, reconciliation and late settlement evidence with append-only transitions. | 🟢 Low | Preserve transaction/unique/lease invariants in migrations and integration tests. |
| **API Contracts** | Internal initiate, read/list, GET IPN, reconciliation and P1 refund are explicit and versioned. | 🟡 Medium | Add OpenAPI examples for duplicate/late/provider-unavailable responses and VNPAY adapter fixtures. |
| **Flow Integrity** | Cash, VNPAY IPN, reconciliation and late settlement contracts agree with Order Saga ownership. | 🟢 Low | Consumer tests must assert `SETTLED_LATE` cannot trigger `PaymentPaid`/`OrderPaid`. |
| **Cross-Cutting** | Tenant/branch scopes, Cognito/workload auth, cursor pagination, retention and redaction are specified. | 🟡 Medium | Verify gateway query redaction and repository scope in security tests. |

## Instruction Compliance

| Dimension | Verdict | Deviation (if any) | Recommendation |
|---|---|---|---|
| **Conventions** | ✅ | Versioned REST paths, common envelope, cursor pagination and event envelope match parent conventions. | — |
| **Architecture** | ✅ | Payment owns its DB/state; no cross-service DB access; adapters isolate VNPAY. | — |
| **Testing** | ✅ | Contract matrix covers cash, concurrency, checksum, duplicate/out-of-order/late IPN, reconciliation and scope security. | Add executable fixtures when implementation begins. |

## Decision

Contracts **có thể được duyệt với warnings**. Không chuyển sang implementation cho tới khi parent contract amendments, VNPAY sandbox fixtures và gateway redaction được hoàn tất.

