# Module 6 — Inventory service: Design Result

**Ngày:** 2026-10-13  
**Trạng thái:** Design complete — Approved with warnings  
**Phạm vi:** M — Standard Service Flow  
**Module:** Kho nguyên liệu (Inventory service)

Started at: 2026-10-13 00:00:00  
Finished at: 2026-10-13 00:00:00  
Total time: 0 minutes

## 1. Verdict

**APPROVED WITH WARNINGS**

Thiết kế đủ cơ sở để tạo implementation backlog. Không có blocker mức Critical/RED. Trước khi viết code phải materialize OpenAPI/AsyncAPI/Flyway fixtures và hoàn thiện command surface P1 cho receipt cancel, waste và return.

## 2. Artifacts hoàn tất

| Phase | Artifact | Path | Status |
|---|---|---|---|
| Vision | Vision | [`2026-10-13-vision.md`](./2026-10-13-vision.md) | Approved |
| Requirements | Requirements | [`2026-10-13-requirements.md`](./2026-10-13-requirements.md) | Approved |
| Requirements review | Spec Sentinel Report | [`2026-10-13-requirements-review.md`](./2026-10-13-requirements-review.md) | Warnings |
| Architecture | Architecture | [`2026-10-13-architecture.md`](./2026-10-13-architecture.md) | Approved |
| Sequence diagrams | L1/L2 PlantUML | [`2026-10-13-sequence-diagrams.adoc`](./2026-10-13-sequence-diagrams.adoc) | Confirmed |
| Contracts | API & Data Contracts | [`2026-10-13-contracts.md`](./2026-10-13-contracts.md) | Approved with warnings |
| Contracts review | Spec Sentinel Report | [`2026-10-13-contracts-review.md`](./2026-10-13-contracts-review.md) | Warnings |
| Finalization | Design Result | This document | Approved with warnings |

## 3. Traceability review

| Requirement | Architecture component | Contract coverage |
|---|---|---|
| P0 ingredient master/base unit | Ingredient service + tenant-scoped PostgreSQL master | Ingredient APIs, conversion schema, unit/quantity constraints |
| P0 recipe và định lượng món/size/topping | Versioned recipe + recipe expansion engine | Recipe APIs, immutable recipe snapshot, modifier scope |
| P0 bán hàng tự trừ nguyên liệu | Stock transaction service + append-only ledger | Reserve/commit APIs, `SALE_COMMITTED`, balance projection |
| P0 giữ chỗ/trừ kho trong Saga | Reservation state machine + Step Functions boundary | Reserve/commit/release/expiry contracts, terminal reconciliation |
| P1 nhập kho | Receipt service + stock ledger | Receipt API/schema and `RECEIPT` movement model |
| P1 kiểm kho | Count session service + approval transaction | Snapshot/delta formula, `COUNT_ADJUSTMENT`, approval version |
| P1 cảnh báo tồn thấp | Alert service + outbox | Threshold/alert lifecycle and `InventoryLowStockDetected.v1` |
| P2 dự báo nhập hàng | Forecast worker + rebuildable read model | Quality tiers, forecast policy/result APIs and schema |

## 4. Design review summary

| Tiêu chí | Kết quả | Nhận xét |
|---|---|---|
| Completeness | PASS | Toàn bộ P0/P1/P2 đã có requirement, component, data model và contract tương ứng. |
| Consistency | PASS WITH WARNINGS | Đã đồng bộ parent POS Platform về availability POST, reservation states, idempotency header, ledger field và event payloads. P1 command surface vẫn cần materialize thêm. |
| Feasibility | PASS | PostgreSQL transaction, row locking, outbox và Step Functions phù hợp với scale/latency baseline của POS platform. |
| Security | PASS | Cognito/workload identity, tenant/branch defense-in-depth, resource ownership check và fail-closed boundary đã được định nghĩa. |
| Performance | PASS | Reserve/commit/release hướng tới p95 ≤ 500 ms; không cache trong correctness path; có lock timeout, bounded retry và index baseline. |
| Observability | PASS | Correlation/causation, metrics contention/shortage/expiry, outbox/DLQ, reconciliation và projection checksum đã có. |
| Testing readiness | PASS WITH WARNINGS | Ma trận test đã đầy đủ về ý định; OpenAPI/AsyncAPI fixtures, migration assertions và concurrency tests chưa được materialize. |

## 5. Finalization review — Code Reviewer gate

**Scope:** 7 tài liệu Inventory và 5 parent contract trực tiếp liên quan; không có application code để review.

**Verdict:** **YELLOW — Proceed with Caution**

### Summary

Thiết kế không có lỗi Critical về auth, tenant isolation, Saga ownership, overselling prevention hoặc terminal state. Warning tập trung ở việc chuyển contract prose thành schema/migration/test artifact và mở rộng P1 command surface.

### Warnings

| # | File:Line | Category | Issue | Recommendation |
|---|---|---|---|---|
| 1 | `2026-10-13-contracts.md:183,369` | API | `ReservationCursorPage` đã có route và field rules nhưng chưa có OpenAPI component/fixture executable. | Sinh schema và test cursor/filter scope trước implementation. |
| 2 | `2026-10-13-contracts.md:190-200,569` | Business logic | Receipt cancellation và WASTE/RETURN được mô hình hóa nhưng chưa có command endpoint/request contract riêng. | Chốt P1 commands; mọi correction phải append ledger, có reason/source/idempotency. |
| 3 | `2026-10-13-contracts.md:663-707,711-748` | Data/migration | Một số bảng alert/forecast/outbox dùng compact column summary. | Sinh DDL typed đầy đủ, `COMMENT ON COLUMN`, checks, lease fields và migration assertions. |
| 4 | `2026-10-13-contracts.md:826-831` | Testing | Concurrency/replay/rounding fixtures mới ở dạng ma trận yêu cầu. | Đưa fixture vào CI và chạy trước khi bật production-like traffic. |

### Checklist summary

| Category | Verdict | Issues |
|---|---|---:|
| Security | PASS | 0 critical; warning chỉ còn materialize negative tests/redaction checks |
| Business Logic | PASS WITH WARNINGS | 1 P1 command-surface warning |
| Performance | PASS | 0 |
| Clean Design | PASS WITH WARNINGS | 1 data-contract materialization warning |
| Conventions | PASS WITH WARNINGS | 1 generated-artifact/testing warning |

## 6. Architecture summary

| Dimension | Summary |
|---|---|
| Logical components | 10 nhóm: public API, internal Saga API, recipe expansion, stock transaction, reservation state machine, receipt/count, alert/forecast, workers, outbox publisher và repositories/observability. |
| Database | 1 PostgreSQL/Aurora PostgreSQL do Inventory sở hữu; không shared DB/FK cross-service. |
| API operations | 32 public/internal operations được định nghĩa trong contract. |
| Event schemas | 7 Inventory event types, common envelope, at-least-once, outbox và consumer dedupe. |
| Critical flows | Reserve, shortage rollback, payment success commit, failure/expiry release, reconciliation, count approval và projection rebuild. |
| Correctness model | Reservation-first lock cho terminal commands; balance locks theo ingredient ID tăng dần; ledger append-only; `available = onHand - reserved`. |

## 7. Implementation roadmap

### Phase 0 — Contract materialization và foundation

- **Goal:** Chuyển thiết kế thành artifacts có thể validate.
- **Tasks:** Tạo OpenAPI 3.1, AsyncAPI/JSON Schema, Flyway V1–V7; materialize typed columns/comments/checks/indexes; provision DB/outbox/EventBridge-SQS/DLQ; tạo tenant-scope test harness.
- **Estimated effort:** 3–5 ngày.
- **Dependencies:** Parent POS Platform/Order/Saga contract review.

### Phase 1 — Ingredient, recipe và opening balance P0

- **Goal:** Quản lý master/công thức và tạo tồn đầu kỳ.
- **Tasks:** Ingredient/conversion APIs; recipe draft/validate/activate/archive; Catalog opaque-reference validation; opening balance; audit/idempotency; active-recipe uniqueness.
- **Estimated effort:** 1–1.5 tuần.
- **Dependencies:** Phase 0; Catalog item/variant contract.

### Phase 2 — Reservation và checkout Saga P0

- **Goal:** Bảo đảm giữ chỗ/trừ kho đúng, không oversell/double-consume.
- **Tasks:** Recipe expansion/aggregation; reserve transaction; commit payment-context validation; release/expiry; terminal reconciliation; outbox events; Step Functions integration.
- **Estimated effort:** 1.5–2 tuần.
- **Dependencies:** Phase 1; Order/Payment/Saga integration fixtures.

### Phase 3 — P1 receipt, count và low-stock alert

- **Goal:** Mở vận hành kho thủ công có audit.
- **Tasks:** Receipt posting/conversion; explicit receipt cancel correction; count snapshot/input/approval; WASTE/RETURN commands; thresholds, alert acknowledge/resolve/re-arm; dashboards/events.
- **Estimated effort:** 1.5–2 tuần.
- **Dependencies:** Phase 2; P1 command contracts must be finalized.

### Phase 4 — P2 forecast

- **Goal:** Cung cấp forecast read-only có quality/freshness.
- **Tasks:** Consumption aggregation; policy resolution; LOW/MEDIUM/HIGH quality tiers; weekday adjustment; forecast worker/results; freshness and insufficient-history response.
- **Estimated effort:** 1–1.5 tuần.
- **Dependencies:** Phase 3 ledger history and reporting inputs.

### Phase 5 — Hardening và operational readiness

- **Goal:** Đạt production-like confidence trước rollout.
- **Tasks:** Lock/deadlock/timeout stress; duplicate/out-of-order replay; migration restore/rebuild; projection checksum; outbox lease/DLQ drill; security/redaction; runbooks and measured p95.
- **Estimated effort:** 1 tuần.
- **Dependencies:** Phases 0–4.

**Tổng ước lượng:** khoảng **6–8 tuần** cho vertical slices P0 → P1 → P2, chưa tính thời gian chờ parent contract review, infrastructure provisioning hoặc thay đổi scope.

## 8. Implementation gates và risks

| Gate/Risk | Điều kiện trước khi code/rollout |
|---|---|
| Generated contracts | OpenAPI/AsyncAPI examples validate; consumer fixtures cho Saga, Order, Reporting, Audit, Notification pass. |
| Database correctness | Flyway chạy được trên empty DB và copied-data DB; mọi column/comment/check/index có assertion. |
| Reservation safety | Race tests chứng minh không partial reserve, không available âm, terminal winner deterministic và retry idempotent. |
| P1 mutation safety | Receipt cancel, WASTE, RETURN có command contract riêng; không update/delete ledger history. |
| Reconciliation | Payment success + commit failure, expiry và DLQ replay có case/operator path; không reopen reservation terminal. |
| Observability | Metrics, structured logs, redaction, alert và runbook cho contention, shortage, expiry, outbox lag, DLQ và checksum mismatch. |

## 9. Sequence diagrams

- **Status:** Generated and confirmed.
- **File:** [`2026-10-13-sequence-diagrams.adoc`](./2026-10-13-sequence-diagrams.adoc)
- **Paths:** checkout reserve → payment → commit → Order finalize; shortage/release; expiry/reconciliation; internal lock/transaction flow; commit/release/expiry race.
- Repo chưa có implementation source, nên diagram mô tả contract flow chứ không phải runtime trace.

## 10. Next steps

- [x] Hoàn tất Vision, Requirements, Architecture, Contracts và sequence diagrams.
- [x] Đồng bộ parent POS Platform Inventory contract với Inventory source contract.
- [x] Review cuối theo code-reviewer gate; không có Critical/RED.
- [ ] Tạo implementation backlog từ roadmap Phase 0–5.
- [ ] Materialize OpenAPI/AsyncAPI/Flyway fixtures trước khi viết controller/domain code.
- [ ] Chốt explicit P1 receipt cancel, WASTE và RETURN command schemas.
- [ ] Chỉ bắt đầu Phase 1 sau khi các implementation gates được owner xác nhận.

## 11. Final checklist

- [x] Tất cả P0/P1/P2 requirements traceable tới component và contract.
- [x] Components, data ownership, API, events và worker responsibilities đã xác định.
- [x] NFR về security, consistency, performance, reliability, retention và observability đã được address.
- [x] Saga/payment boundary không để `PaymentPaid` tự động mutate Inventory.
- [x] Tenant/branch isolation và append-only ledger đã được quy định.
- [x] Implementation roadmap có phase, effort và dependency.
- [x] Các warning có recommendation và implementation gate.
- [ ] Generated schemas, migrations và executable tests được materialize.

