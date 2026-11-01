Started at: 2026/10/13 00:00:00
Finished at: 2026/10/13 00:00:00
Total time: 0 minutes
---

# Spec Sentinel Report — Reporting API & Data Contracts

**Spec:** [`2026-10-13-contracts.md`](./2026-10-13-contracts.md)  
**Date:** 2026-10-13  
**Status:** ⚠️ Warnings (Proceed with Caution)

## Review scope

- Phase 0 skipped — tái sử dụng Vision, Requirements, Architecture và sequence context đã được duyệt trong workflow hiện tại.
- Đối chiếu Contracts với parent POS Platform, Identity Access, Order, Loyalty/Promotion và Recommendation.
- Repository chưa có application source, generated OpenAPI/AsyncAPI hoặc migration source; review này kiểm tra design contracts và cross-service alignment, không phải code review.
- Đã áp dụng các rule hiện có trong `.agent/rules/security.md` và `.agent/rules/code-quality.md`.

## Findings

### Warnings

| # | Category | Finding | Risk | Recommendation |
|---|---|---|---|---|
| 1 | Parent event alignment | Parent `OrderPaid.v1` hiện mới có line snapshot, totals, currency, paid time và order version; Reporting cần thêm `businessDate`, timezone, checkout actor và applied promotion allocation. | Producer và Reporting có thể hiểu khác nhau về employee/promotion attribution; dashboard tổng và drill-down có thể lệch. | Materialize một additive parent amendment/JSON Schema duy nhất cho `OrderPaid.v1`, kèm producer/consumer compatibility fixtures; giữ `OrderPaid` là canonical revenue fact. |
| 2 | Correction/refund source | Contracts vẫn viết “such as `OrderRefunded.v1` or `OrderCorrected.v1`”, chưa chọn canonical event name, trạng thái và partial/full refund semantics. | Projection có thể áp dụng correction hai lần hoặc xử lý khác nhau giữa Order, Payment, Loyalty và Reporting. | Chọn một canonical business-fact contract trước implementation; định nghĩa source event ID, effective business date, affected lines/amounts, revision precedence và idempotency key. Payment provider facts không được tự sửa revenue. |
| 3 | Identity permission contract | Reporting giới thiệu `report:read`/`reporting:operate` và gọi authorization decision, nhưng parent Identity permission catalogue và response schema chưa có các permission này cùng đầy đủ scope/version fields. | Generated policy hoặc client có thể cấp nhầm tenant/branch scope, đặc biệt khi membership bị revoke trong lúc cache còn sống. | Bổ sung parent Identity amendment, canonical request/response schema, TTL/revocation fixtures và workload authorization cho internal rebuild/health. |
| 4 | Parent platform catalogue | Parent POS Platform hiện mô tả Reporting với `sales_daily`, `sales_by_branch`, `top_items`; Contracts mở rộng thêm hourly, employee, promotion, recommendation, freshness, rebuild và produced events. | Platform routing, ownership matrix và operational catalogue có thể bỏ sót read model hoặc event mới. | Cập nhật parent service/event/read-model catalogue trước khi sinh infrastructure; ghi rõ Reporting sở hữu derived read models nhưng không sở hữu business facts. |
| 5 | Recommendation lineage | `recommendation_effectiveness` aggregate theo `dimension_key`, nhưng schema chưa giữ đủ source interaction/order-context lineage để replay, audit và điều tra pending-to-paid. | Multiple accepts, duplicate/reordered events hoặc correction có thể làm sai cohort mà không truy được nguyên nhân. | Bổ sung source interaction/order context key hoặc một append-only attribution fact projection; lưu policy/version và unique order-context constraint, sau đó aggregate vào bảng query. |
| 6 | Read-model ownership and period keys | `sales_by_branch`, `sales_by_employee` và `promotion_effectiveness` dùng period range như khóa materialized row, trong khi public query có nhiều range tùy ý. | Dễ tạo quá nhiều row theo từng query hoặc không rõ row nào là source-of-truth khi range chồng lấn. | Chốt các grain cố định của projection (ví dụ day/branch/employee/promotion) và aggregate range ở query layer; nếu giữ period materialization, ghi rõ rebuild lineage và uniqueness cho mọi overlap. |
| 7 | Audit boundary | Reporting có `report_access_audit` và phát `ReportAccessAudited.v1`, trong khi parent POS Platform quy định Audit là append-only owner. | Hai nơi cùng được coi là audit source có thể lệch retention, legal hold hoặc quyền truy cập. | Định nghĩa Reporting table là outbox/staging/reference tối thiểu; Audit Service là source-of-record cho retention/query dài hạn, hoặc chốt rõ nếu Reporting-owned audit là exception. |
| 8 | Machine-readable and scale gates | Contracts đã nêu OpenAPI/AsyncAPI, fixtures, cache/ETag và partition threshold là implementation artifacts, nhưng chúng chưa tồn tại và ngưỡng tải/retention cho PostgreSQL chưa có số đo cụ thể. | Code có thể được viết từ prose, gây lệch schema; 12 tháng × 20 branches và correction/rebuild có thể vượt SLO. | Giữ các artifact là gate bắt buộc trước code; bổ sung load profile, row/partition volume threshold, exact cursor/ETag semantics và golden fixtures cho timezone, duplicate, correction, partial/unavailable states. |

## Cross-Impact Analysis

| Dimension | Finding | Risk Level | Recommendation |
|---|---|---|---|
| **Shared Logic** | Chưa có implementation call sites; boundary dùng chung là `OrderPaid.v1`, correction/refund fact, Identity authorization decision, Recommendation interaction events và common event envelope. | 🟡 Medium | Quản lý các boundary như versioned APIs; sinh schema/fixture dùng chung thay vì copy DTO giữa services. |
| **Data/State** | Reporting thêm preaggregated sales/employee/promotion/recommendation tables, inbox, watermark, rebuild operation và access-audit reference. | 🟡 Medium | Enforce tenant predicates, unique dedupe, monotonic watermark, immutable snapshots, atomic projection swap và `COMMENT ON COLUMN` trong migration. |
| **API Contracts** | Chín public report endpoints và ba internal operations endpoints là mới; auth scope, correction source và range/read-model grain phụ thuộc parent amendments. | 🔴 High | Hoàn tất Identity/Order/Platform amendments rồi generate OpenAPI 3.1; chạy consumer/provider compatibility tests trước controller implementation. |
| **Flow Integrity** | `OrderPaid`/interaction event → inbox/dedupe → projection → watermark/data-quality → cache → authorized dashboard; correction và rebuild phải cập nhật current corrected view. | 🟡 Medium | Materialize L1/L2 fixtures cho duplicate/out-of-order, pending-to-paid, late correction, revocation, Redis failure và rebuild swap. |
| **Cross-Cutting** | Tenant/branch isolation, cache leakage, stale/partial semantics, PII redaction, audit and performance are explicit. | 🔴 High | Defense-in-depth ở Identity, query policy và repository; authorization trước cache; negative IDOR/cache/PII tests và freshness alerts trước release. |

## Instruction Compliance

| Dimension | Verdict | Deviation (if any) | Recommendation |
|---|---|---|---|
| **Conventions** | ✅ | REST/OpenAPI 3.1, AsyncAPI/JSON Schema, UUID v7, UTC, VND minor units, common envelopes, cursor pagination và at-least-once events phù hợp parent conventions. | — |
| **Architecture** | ✅ | Reporting giữ đúng boundary CQRS/read model, không đọc database xuyên service và không trở thành nguồn sự thật cho Order/Payment/Promotion. | Coi parent amendments và machine-readable artifacts là prerequisite, không bypass bằng shared DB/query trực tiếp. |
| **Testing** | ⚠️ | Contract test matrix rộng nhưng fixtures/schema generated và exact correction/authorization lineage chưa được materialize. | Viết golden fixtures, replay/state-transition tests, OpenAPI/AsyncAPI compatibility tests và load/security tests trước code. |

## Verdict

Contracts **có thể được duyệt với warnings**. Không có blocker về scope hoặc hướng kiến trúc; các điểm còn lại đều được xác định thành parent amendment hoặc implementation artifact. Trước khi viết code cần chốt canonical correction/refund event, đồng bộ `OrderPaid.v1`/Identity/Platform schemas, xác định read-model grain/lineage và materialize OpenAPI/AsyncAPI, migrations, fixtures cùng load profile.

## Required user decision

Bạn duyệt Contracts với warnings để chuyển sang **Finalization** chứ?
