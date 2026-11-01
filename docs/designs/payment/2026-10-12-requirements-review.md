# Spec Sentinel Report

**Spec:** `docs/designs/payment/2026-10-12-requirements.md`  
**Date:** 2026-10-12  
**Status:** ⚠️ Warnings (Proceed with Caution)

## Findings

Không còn critical blocker sau khi đã tách late settlement khỏi lifecycle state, xác định trusted service context và ghi rõ các parent contract amendments cần thực hiện trước implementation.

### Warnings

| # | Category | Finding | Risk | Recommendation |
|---|---|---|---|---|
| 1 | External integration | Thuật toán checksum, canonical field ordering và provider acknowledgement code phụ thuộc hợp đồng VNPAY sandbox thực tế. | Adapter có thể verify sai hoặc khiến VNPAY retry không đúng. | Chốt bằng sanitized sandbox fixtures/contract tests; giữ provider-specific logic trong adapter, không đưa field VNPAY vào domain. |
| 2 | Reconciliation operations | Có retry threshold/manual queue nhưng chưa chốt retention, operator SLA, quyền manual resolution và evidence bắt buộc. | Giao dịch treo có thể tồn tại lâu hoặc bị xử lý thủ công không nhất quán. | Architecture/Contracts bổ sung reconciliation item lifecycle, lease expiry, operator action/audit, alert threshold và retention. |
| 3 | Reporting semantics | History/report chưa chốt business timezone, ngày đối soát, VAT/rounding và retention. | Tổng hợp theo ngày hoặc so sánh provider có thể lệch số liệu. | Dùng branch timezone/business date và integer minor units; chốt VAT/rounding/retention ở Contracts trước khi xây read model. |
| 4 | API contract | Reconciliation run là async nhưng error table có `409 reconciliation_required`; chưa phân biệt command acceptance với item result. | POS/operator có thể hiểu nhầm `409` là payment failure. | Trả `202` cho accepted run; dành `409` cho duplicate/conflict request; item status đọc qua reconciliation resource. |
| 5 | P1 boundary | Refund/split tender đã có invariant nhưng chưa có provider capability matrix và endpoint payload hoàn chỉnh. | Có thể thiết kế P1 làm thay đổi P0 payment ledger hoặc không tương thích VNPAY refund. | Giữ refund/allocations là entity riêng; Contracts phase phải định nghĩa provider support, partial/full rules và refund reconciliation trước P1 implementation. |

## Cross-Impact Analysis

| Dimension | Finding | Risk Level | Recommendation |
|---|---|---|---|
| **Shared Logic** | Chưa có source implementation; không tìm thấy call site/service code ngoài design docs. Cross-impact trực tiếp nằm ở Order/Saga, Inventory và parent event/API contracts. | 🟡 Medium | Materialize versioned contracts và consumer tests trước code; không tạo shared repository giữa services. |
| **Data/State** | Payment-owned attempts/callbacks/reconciliation/outbox; late settlement đã được tách thành record riêng với terminal attempt. | 🟡 Medium | Giữ transaction, unique constraints và append-only transitions; materialize schema trong Architecture. |
| **API Contracts** | Internal initiate, read payment, VNPAY IPN và reconciliation run; parent event/DB fields chưa biểu diễn đầy đủ CASH/VNPAY chung. | 🟡 Medium | Amend parent OpenAPI/AsyncAPI/DB contract trước implementation: nullable provider fields, cash fields và provider-scoped unique key. |
| **Flow Integrity** | Cash happy path, VNPAY pending/IPN, duplicate/out-of-order callback, late settlement và reconciliation boundary đã nhất quán. | 🟢 Low | Giữ invariant late settlement: chỉ `PaymentReconciliationRequired.v1`, không `PaymentPaid`/`OrderPaid` sau terminal attempt. |
| **Cross-Cutting** | Tenant/branch isolation, Cognito roles, checksum, secret management, outbox và audit đã có; internal caller context cần trusted-service validation. | 🟡 Medium | Defense-in-depth tenant predicate ở service/repository; không tin raw IDs/body từ caller; log security mismatch. |

## Instruction Compliance

| Dimension | Verdict | Deviation (if any) | Recommendation |
|---|---|---|---|
| **Conventions** | ✅ | Tài liệu nằm trong `docs/designs/payment/`, dùng versioned API/event và naming nhất quán với parent docs. | — |
| **Architecture** | ⚠️ | Payment/Order ownership đã được phân định; trusted service context và parent amendments vẫn cần materialize. | Đưa command context, late settlement record và cross-service contract vào Architecture/Contracts. |
| **Testing** | ✅ | Có acceptance và failure-injection coverage cho cash, IPN, concurrency, reconciliation, tenant scope và outbox. | Bổ sung test riêng cho late settlement record không phát `PaymentPaid` sau khi policy được chốt. |

## Decision

Requirements **có thể chuyển sang Architecture với các warning trên**. Trước implementation phải materialize parent contract amendments và chốt reconciliation/reporting operational policy trong Architecture/Contracts.
