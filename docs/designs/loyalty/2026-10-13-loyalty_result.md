# Module 5 — Loyalty service: Design Result

**Ngày:** 2026-10-13  
**Trạng thái:** Design complete — Approved with warnings  
**Phạm vi:** M — Standard Service Flow  
**Module:** Khách hàng thân thiết & khuyến mãi (Loyalty service)

## 1. Verdict

**APPROVED WITH WARNINGS**

Thiết kế đủ cơ sở để bắt đầu implementation planning. Không có blocker nội bộ trong Loyalty. Trước khi viết code, phải hoàn tất các parent contract amendments và test fixtures được nêu ở mục 5.

## 2. Artifacts hoàn tất

1. [Vision](./2026-10-13-vision.md)
2. [Requirements](./2026-10-13-requirements.md)
3. [Requirements Sentinel Review](./2026-10-13-requirements-review.md)
4. [Architecture](./2026-10-13-architecture.md)
5. [API & Data Contracts](./2026-10-13-contracts.md)
6. [Contracts Sentinel Review](./2026-10-13-contracts-review.md)
7. [Design Result](./2026-10-13-loyalty_result.md)

## 3. Design review summary

| Tiêu chí | Kết quả | Nhận xét |
|---|---|---|
| Completeness | PASS | Customer, accrual, redemption hold, reversal, tier, FIFO, voucher, promotion và portal đều có requirement/component/contract tương ứng. |
| Consistency | PASS WITH WARNINGS | Loyalty docs nhất quán; parent Order hiện cần additive fields và `OrderRefunded.v1`. |
| Feasibility | PASS | Dedicated PostgreSQL, transactional outbox/inbox, EventBridge/SQS và worker model phù hợp với POS platform constraints. |
| Security | PASS WITH WARNINGS | Tenant/branch defense-in-depth, Cognito/workload auth, encrypted phone, HMAC lookup, masking và anti-enumeration đã được mô tả; OTP provider/retention cần chốt khi triển khai portal. |
| Performance | PASS | Core sync p95 target ≤ 500 ms; primary DB là correctness path; cursor pagination, indexes và worker scaling đã định nghĩa. |
| Observability | PASS | Correlation/trace, metrics, audit, outbox/inbox lag, DLQ và invariant alerts đã có. |
| Testing readiness | PASS WITH WARNINGS | Cần materialize schema fixtures, concurrency race tests, replay/out-of-order tests và parent contract tests. |

## 4. Quyết định kiến trúc cuối

- Loyalty là bounded context độc lập, sở hữu customer profile, points ledger, voucher/promotion và projections.
- PostgreSQL riêng; UUID v7; integer VND/points; không cache trong correctness path.
- `OrderPaid.v1` là nguồn duy nhất để accrue points và commit point/voucher reservation; không dùng `PaymentPaid.v1`.
- `OrderRefunded.v1` full refund do Order phát sau khi finalized; Loyalty tự tính reversal từ ledger/allocation lineage.
- Append-only ledger tách khỏi `point_holds`; hold chỉ thành `REDEEMED` khi paid, failure/timeout/cancel thì release.
- FIFO point lots theo expiry/earned time; held points được bảo vệ trong active hold.
- Promotion quote do Loyalty tính; Order giữ quyền quyết định total cuối cùng và snapshot quote/rule versions.
- Promotion versions immutable; conflict resolution deterministic theo customer benefit → priority → stable promotion ID.
- Voucher reserve theo checkout attempt; chỉ `OrderPaid` consume; retry không tăng usage.
- Staff API dùng Cognito + tenant/branch scope; portal dùng OTP/customer token riêng và read-only.
- EventBridge/SQS at-least-once, inbox dedupe, transactional outbox, DLQ và replay có kiểm soát.

## 5. Warnings và implementation gates

| Gate | Nội dung | Điều kiện hoàn tất |
|---|---|---|
| Parent Order contract | Thêm `loyaltyEligibleAmountMinor`, `loyaltyPolicyVersion`, `checkoutAttemptId`, `pointHoldId`, `voucherReservationId` và quote references vào `OrderPaid.v1`. | Parent AsyncAPI/JSON Schema cập nhật; producer/consumer compatibility tests pass. |
| Full refund event | Định nghĩa `OrderRefunded.v1` do Order phát sau full refund finalized; Payment không phát event thay thế cho Loyalty. | Order/Payment mapping, duplicate refund và out-of-order fixtures pass. |
| Terminal event precedence | Stale checkout failure/release không được release sau khi hold đã commit hoặc voucher đã consume. | Attempt lifecycle version + state precedence test pass. |
| Ledger allocation invariants | Materialize examples cho HOLD/RELEASE/REDEEM/REVERSE/EXPIRE và concurrent FIFO allocation. | PostgreSQL transaction/race tests chứng minh không âm, không over-allocate và rebuild đúng. |
| Voucher limit concurrency | Total/per-customer usage phải atomic dưới concurrent reserve/consume. | Lock/counter strategy và stress test pass. |
| Portal auth | Chốt OTP provider, session/token claims, rate limit, key rotation và retention. | Auth adapter contract + anti-enumeration/security test pass. |
| PII/logging | Redact phone, OTP, secrets, hashes/ciphertext và rule payload nhạy cảm khỏi logs/traces. | Log inspection và security negative tests pass. |

Không tạo application code trong design workflow này.

## 6. Implementation roadmap

### Phase 0 — Contract alignment và foundation

- **Goal:** Tạo nền tảng service/database/auth/event contract mà không mở scope P1.
- **Tasks:**
  - Cập nhật parent Order event schemas và checkout compensation references.
  - Chốt `OrderRefunded.v1` producer/consumer semantics.
  - Tạo service skeleton, OpenAPI/AsyncAPI validation trong CI.
  - Provision PostgreSQL, KMS/Secrets Manager, EventBridge/SQS/DLQ và migration framework.
  - Materialize database comments, tenant predicates, indexes và outbox/inbox tables.
- **Estimated effort:** 3–5 ngày.
- **Dependencies:** Parent Order/Payment owners review contract amendments.

### Phase 1 — Customer profile và points accrual P0

- **Goal:** Tra cứu/tạo customer và cộng điểm chính xác sau `OrderPaid`.
- **Tasks:**
  - E.164 normalization, encrypted phone, HMAC lookup, masked response.
  - Customer public APIs, Cognito scope và audit PII access.
  - Append-only ledger, point policy, `EARNED` lot, balance projection.
  - OrderPaid inbox consumer, source uniqueness, retry/DLQ và Loyalty accrual event.
  - Projection rebuild command và negative-balance invariant checks.
- **Estimated effort:** 1.5–2 tuần.
- **Dependencies:** Phase 0; OrderPaid fields/schema fixtures.

### Phase 2 — Redemption hold và checkout integration P0

- **Goal:** Đổi điểm an toàn trong Saga, không double-spend.
- **Tasks:**
  - Atomic quote/point hold/voucher reservation boundary.
  - FIFO allocation primitives, hold state machine và release commands.
  - Order/Saga integration cho payment failure, timeout, cancel và compensation.
  - `OrderPaid` commit handler cho hold; stale release precedence.
  - Concurrent hold, retry and broker replay tests.
- **Estimated effort:** 1.5–2 tuần.
- **Dependencies:** Phase 1; Order checkout attempt lifecycle contract.

### Phase 3 — Full reversal và operational reliability P0

- **Goal:** Thu hồi điểm đúng khi full refund và vận hành được event-driven service.
- **Tasks:**
  - `OrderRefunded.v1` consumer, reversal lineage, redemption restore.
  - Audit/outbox facts cho accrual/reversal/hold release.
  - DLQ replay, stuck hold cleanup, projection mismatch alert/rebuild.
  - Metrics/traces/log redaction, dashboard và runbook cơ bản.
  - Full refund, duplicate, late/out-of-order and restore-after-expiry tests.
- **Estimated effort:** 1 tuần.
- **Dependencies:** Phase 2; finalized refund event contract.

### Phase 4 — Membership, FIFO expiry, voucher và promotion P1

- **Goal:** Mở rộng loyalty/promotion engine sau khi P0 ledger/checkout ổn định.
- **Tasks:**
  - Tier policy/history, rolling 12-month recalculation và tier events.
  - Daily expiry worker theo tenant timezone và FIFO lots.
  - Voucher config, limits, reservation/consume/release và usage report.
  - Immutable promotion versions, `% discount`, `buy X get Y`, happy hour.
  - Deterministic conflict engine, rule timeout/fail-closed và explanation snapshot.
- **Estimated effort:** 2–3 tuần.
- **Dependencies:** Phase 1–3; Catalog/Order basket snapshot contract.

### Phase 5 — Customer portal P2 và hardening

- **Goal:** Read-only customer portal và production-quality verification.
- **Tasks:**
  - OTP/customer identity adapter, session claims, rate limit và retention.
  - Portal balance/ledger/voucher APIs, anti-enumeration và masked PII.
  - k6 latency/concurrency profile, restore/replay drill, security tests.
  - Contract compatibility, chaos/failure injection, operational runbook và measured SLO report.
- **Estimated effort:** 1–2 tuần.
- **Dependencies:** Phase 1–4; OTP provider decision.

### Roadmap estimate

Khoảng **7–10 tuần** cho vertical slices P0 → P1 → P2, chưa bao gồm thời gian chờ parent contract review, infrastructure provisioning hoặc provider approval.

## 7. Architecture summary

| Dimension | Summary |
|---|---|
| Components | Khoảng 12 logical components: API adapters, customer, points, promotion, voucher, inbox/consumer, outbox, workers, repositories, audit/observability, PostgreSQL và broker/infrastructure. |
| Databases | 1 PostgreSQL Loyalty database; projections trong cùng DB; không shared DB. |
| API operations | 31 operations được contract shape: staff, internal checkout/ops, P1 config và P2 portal. |
| Event schemas | 3 nhóm event consumed (`OrderPaid`, `OrderRefunded`, checkout failure/release) và 7 Loyalty-produced events, dùng common envelope/versioning. |
| Critical paths | Quote/hold/checkout, duplicate OrderPaid, full refund reversal và promotion/voucher quote. |
| Data model | Khoảng 18 logical tables/aggregates gồm customer, ledger, lot/allocation, hold, policy, tier, promotion, voucher, inbox/outbox, audit và worker runs. |

## 8. Sequence diagrams

- **Status:** Embedded trong [Architecture](./2026-10-13-architecture.md) dưới dạng contract-driven Mermaid flows.
- **Paths covered:** quote/hold/checkout success, duplicate OrderPaid, full refund reversal và promotion quote/voucher reservation.
- Repo chưa có implementation source; không tạo runtime-trace PlantUML riêng để tránh suy đoán participant/call path.

## 9. Next steps

- [ ] Apply parent Order contract amendments and publish schema fixtures.
- [ ] Confirm `OrderRefunded.v1` ownership and terminal event precedence.
- [ ] Create implementation backlog from roadmap phases.
- [ ] Set up service skeleton, CI contract validation, migration comments and tenant-scope test harness.
- [ ] Implement Phase 1 only after the contract gates in §5 are accepted.

## 10. Final checklist

- [x] All P0/P1/P2 requirements traceable to architecture components.
- [x] All major components have API/data/event contracts.
- [x] NFRs addressed by database, messaging, worker and observability decisions.
- [x] Security covers staff, internal workload and portal access paths.
- [x] Ledger/projection/idempotency/replay behavior is explicit.
- [x] Implementation roadmap is actionable and dependency-aware.
- [ ] Parent Order amendments and implementation fixtures completed before coding.

---

Started at: 2026/10/13 00:00:00  
Finished at: 2026/10/13 00:00:00  
Total time: 0 minutes
