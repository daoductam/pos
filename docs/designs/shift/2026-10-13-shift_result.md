Started at: 2026/10/13 00:00:00
Finished at: 2026/10/13 00:00:00
Total time: 0 minutes
---

# Design Result: Module 7 — Ca làm việc (Shift Service)

## Verdict: APPROVED WITH WARNINGS

Thiết kế đủ để bắt đầu implementation planning và chia phase code. Không còn blocker về ownership, lifecycle, money formula, tenant/branch isolation hoặc close/payment race. Các warnings còn lại là cross-service rollout và operational configuration; phải xử lý trước production rollout.

## Artifacts

| Phase | Artifact | Path | Status |
|---|---|---|---|
| Vision | Shift Vision | [`2026-10-13-vision.md`](./2026-10-13-vision.md) | Approved |
| Requirements | Shift Requirements | [`2026-10-13-requirements.md`](./2026-10-13-requirements.md) | Approved with warnings |
| Requirements review | Spec Sentinel Report | [`2026-10-13-requirements-review.md`](./2026-10-13-requirements-review.md) | Warnings only |
| Architecture | Shift Architecture | [`2026-10-13-architecture.md`](./2026-10-13-architecture.md) | Approved |
| Sequence | L1/L2 critical flows | [`2026-10-13-sequence-diagrams.adoc`](./2026-10-13-sequence-diagrams.adoc) | Generated |
| Contracts | Shift API & Data Contracts | [`2026-10-13-contracts.md`](./2026-10-13-contracts.md) | Approved |
| Finalization | This result | `2026-10-13-shift_result.md` | Approved with warnings |

## Design review summary

### Completeness

- P0 open, current, close, idempotency, RBAC, audit và cancel abandoned shift đều có component, API, state và persistence contract.
- P1 count, expected cash, threshold, discrepancy review, recount, late/duplicate facts và reconciliation đều có flow, endpoint, event và database entity.
- Close/payment race được xử lý bằng DB-sequenced admission fence, không dựa vào broker delivery time.
- Critical flows đã có L1/L2 diagrams: open race, admission/fence race, P0 close, P1 review, recount, cancellation và late fact.

### Consistency

- Shift là source of truth; Order/Payment/Saga chỉ dùng opaque references và integration contracts.
- `ACTIVE`, `PENDING_REVIEW`, `CLOSED`, `CANCELLED` là public lifecycle; `OPEN`, `CLOSING`, `FROZEN` là internal admission phase.
- Expected cash thống nhất:

```text
expectedCashMinor = openingFloatMinor + cashCollectedMinor - cashRefundedMinor
discrepancyMinor = countedCashMinor - expectedCashMinor
```

- Initial P1 count nằm trong `CloseShiftRequest`; endpoint `cash-counts` chỉ dành cho recount sau khi Manager/Owner yêu cầu.
- Late/unknown fact mở `ShiftReconciliationCaseOpened.v1`; chỉ sau resolution mới phát `ShiftCashFactReconciled.v1`. Closed shift không bị mutate hoặc reopen.

### Feasibility

- PostgreSQL local transaction + row lock + partial unique index đáp ứng one-active-slot và deterministic close ordering.
- Transactional outbox, EventBridge/SQS, DLQ và idempotent consumers phù hợp với parent platform.
- Close drain là worker bounded, không giữ DB transaction trong khi chờ service khác.
- Scale baseline của parent không yêu cầu partition transaction tables trong MVP; partition chỉ là operational option cho history/outbox/audit khi đạt ngưỡng.

### Warnings còn lại

| # | Warning | Tác động | Hành động bắt buộc |
|---:|---|---|---|
| 1 | Parent Order/Payment/platform docs vẫn còn contract cơ bản ở một số đoạn | Nếu code theo parent docs cũ, cash attribution sẽ thiếu `shiftId`/`admissionId` | Đồng bộ parent contracts trước khi sinh DTO/client; giữ Shift Contracts làm nguồn chi tiết |
| 2 | P0 close có thể trả `202` và cuối cùng thành `PENDING_REVIEW` khi pre-fence admission timeout | POS cần hiển thị processing/review và không cho mở ca mới | Chốt UI copy, SLA, `drainTimeoutMs` và runbook; không force-close bằng SQL |
| 3 | Threshold, max opening float, drain timeout và policy effective overlap cần config vận hành | Giá trị không phù hợp có thể làm branch bị block hoặc giảm an toàn | Đưa vào tenant/branch configuration với audit và validation |
| 4 | Chưa sinh artifact máy đọc OpenAPI/AsyncAPI/migration | Chưa nên bắt đầu controller/DTO/migration implementation trực tiếp từ prose | Sinh spec files và contract fixtures ở Phase 1 |
| 5 | Reconciliation case cần owner/operator SLA và retention policy thực tế | Case late fact có thể tồn đọng lâu | Tạo alert theo age, DLQ dashboard và retention/archive job trước production |

Không có critical issue. Warnings không phủ định design, nhưng phải được đưa vào backlog implementation/operations.

## Traceability review

| Requirement | Architecture | Contract |
|---|---|---|
| Mở ca + tiền đầu ca | Public API, Shift DB, partial unique slot | `POST /branches/{branchId}/shifts`, `ShiftOpened.v1` |
| Đóng ca | Fence Coordinator, finalizer, outbox | `POST /shifts/{shiftId}/close`, `ShiftClosed.v1` |
| Chỉ một ca/branch | Row lock + partial unique index | `shift_already_open`, concurrency fixtures |
| Hủy ca abandoned | Cancel command, float-return audit | `/cancel`, `ShiftCancelled.v1` |
| Đếm tiền P1 | Cash Summary Engine, immutable attempts | `/cash-counts`, `CashCountAttempt`, `ShiftCountSubmitted.v1` |
| Discrepancy threshold | Policy snapshot + review state | `ShiftReviewRequired.v1`, approve/recount APIs |
| Thu hồi/recount | Append-only count/review | `RECOUNT_REQUIRED`, latest attempt + If-Match |
| Late/duplicate facts | Dedupe + reconciliation worker | cash facts, reconciliation endpoints/events |
| RBAC/branch isolation | Cognito + service/repository checks | permission matrix, `403/404`, IDOR fixtures |
| Audit/reporting | Outbox → EventBridge/SQS | event envelope + Audit consumer |

## Architecture summary

- **Primary service:** Shift Service, stateless API + close/reconciliation worker.
- **Database:** Dedicated PostgreSQL với `shifts`, admissions, cash facts, count attempts, reconciliation cases, policies, outbox, idempotency và audit references.
- **Consistency boundary:** PostgreSQL row lock/sequence/version.
- **Public APIs:** 9 endpoints cho open/current/history/detail/close/cancel/count/review.
- **Internal APIs:** 5 endpoints cho admission/completion/cash facts/summary/reconciliation resolution.
- **Events:** 9 Shift events, delivered through transactional outbox → EventBridge/SQS → Reporting/Audit/ops.
- **Critical security:** Cognito JWT cho public, workload identity/mTLS cho internal, tenant/branch predicates ở application + repository.

## Implementation roadmap

### Phase 1 — Foundation, schema và generated contracts

- **Goal:** Có database boundary, OpenAPI/AsyncAPI và shared middleware đúng contract.
- **Tasks:**
  - Sinh OpenAPI 3.1, AsyncAPI/JSON Schema và common error/success envelopes.
  - Tạo migrations cho tables, remarks, constraints, partial unique indexes và seed policy.
  - Implement tenant/branch/RBAC context, idempotency store, UUID v7, UTC/business timezone.
  - Implement transactional outbox, inbox/event dedupe và audit reference writer.
  - Tạo contract fixtures cho parent Order/Payment amendment.
- **Estimated effort:** 3–5 ngày làm việc.
- **Dependencies:** Identity/Cognito context, PostgreSQL, EventBridge/SQS conventions.

### Phase 2 — P0 lifecycle và close fence

- **Goal:** Open/current/close/cancel chạy an toàn trong một branch.
- **Tasks:**
  - Open shift + active-slot concurrency + idempotency.
  - Current/history/detail queries và scope enforcement.
  - Admission sequence, `OPEN → CLOSING → FROZEN`, close operation record.
  - Close worker drain, timeout-to-review và `202 PROCESSING` semantics.
  - P0 close/cancel audit, outbox events và recovery after worker restart.
- **Estimated effort:** 4–6 ngày làm việc.
- **Dependencies:** Phase 1.

### Phase 3 — Order/Payment/Saga integration

- **Goal:** Không có checkout/cash fact thiếu shift attribution.
- **Tasks:**
  - Order bắt buộc `shiftId` và lưu admission context trong checkout attempt.
  - Saga tạo/reuse admission idempotently.
  - Payment initiate/refund nhận và validate `shiftId`, `admissionId`, `admittedAt`.
  - Ingest cash collection/refund facts, completion, source dedupe và late-fact behavior.
  - Emit/update `PaymentPaid.v1` và `PaymentRefunded.v1` metadata.
- **Estimated effort:** 4–6 ngày làm việc.
- **Dependencies:** Phase 1–2; Order/Payment contract synchronization.

### Phase 4 — P1 count, review và reconciliation

- **Goal:** Cash count/discrepancy/recount vận hành được và audit đầy đủ.
- **Tasks:**
  - Cash summary watermark/completeness và formula tests.
  - Initial count in close, threshold routing, immutable attempt.
  - Manager/Owner approve/request recount với optimistic version.
  - Reconciliation case open/resolve, events và operator permissions.
  - Reporting/Audit consumers cho shift facts và discrepancy.
- **Estimated effort:** 4–6 ngày làm việc.
- **Dependencies:** Phase 2–3.

### Phase 5 — Verification, rollout và operations

- **Goal:** Đủ kiểm thử race/contract/replay và có runbook production.
- **Tasks:**
  - Unit money/state tests; integration PostgreSQL lock tests.
  - Contract tests với Order/Payment/Saga; event replay/dedupe/DLQ tests.
  - Load test admission/current/close; IDOR/RBAC/security tests.
  - Dashboards/alerts: pending review age, drain timeout, late facts, outbox lag.
  - Document retention/archive, reconciliation SLA, rollback/backfill procedure.
  - Feature flag P1 count theo tenant/branch; canary rollout rồi mở rộng.
- **Estimated effort:** 3–5 ngày làm việc.
- **Dependencies:** Phase 1–4.

## Sequence diagrams

- **Status:** Generated.
- **Artifact:** [`2026-10-13-sequence-diagrams.adoc`](./2026-10-13-sequence-diagrams.adoc).
- **Paths covered:** open race; admission-before/after-fence; P0 close; P1 count/review; recount; abandoned cancellation; late/duplicate cash fact.

## Next steps

- [ ] Synchronize parent POS Platform, Order và Payment contract documents.
- [ ] Confirm operational defaults: `drainTimeoutMs`, maximum opening float, policy precedence/effective overlap, review SLA.
- [ ] Generate machine-readable OpenAPI/AsyncAPI/migration artifacts.
- [ ] Start implementation Phase 1 via `.agent/workflows/feature.md`.
- [ ] Set up CI checks for schema validation, contract tests và migration safety.
- [ ] Create dashboards/runbooks before enabling P1 count in production.

## Final checklist

- [x] All P0/P1 requirements traceable to architecture components.
- [x] All components have API/data/event contracts.
- [x] NFRs addressed by lock/version/idempotency/outbox/security decisions.
- [x] Tenant/branch/RBAC/IDOR checks defined for every data path.
- [x] Close/payment race and late-fact behavior represented in sequence diagrams.
- [x] Money formula and immutable historical records defined.
- [x] Implementation roadmap is actionable.
- [ ] Parent cross-service documents synchronized.
- [ ] Machine-readable contracts and migrations generated.
- [ ] Operational defaults and SLA confirmed.

## Final gate

Thiết kế Module 7 được xem là **APPROVED WITH WARNINGS** để chuyển sang implementation planning. Không viết code trực tiếp từ các giả định cũ của parent docs; dùng Shift Contracts đã approve và hoàn tất các warning rollout ở trên trước khi merge P0 integration.
