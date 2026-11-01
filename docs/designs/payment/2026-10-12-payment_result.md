# Module 4 — Payment service: Design Result

**Ngày:** 2026-10-12  
**Trạng thái:** Design complete — Approved with warnings  
**Phạm vi:** M — Standard Service Flow

## 1. Artifacts hoàn tất

1. [Vision](./2026-10-12-vision.md)
2. [Requirements](./2026-10-12-requirements.md)
3. [Requirements Sentinel Review](./2026-10-12-requirements-review.md)
4. [Architecture](./2026-10-12-architecture.md)
5. [API & Data Contracts](./2026-10-12-contracts.md)
6. [Contracts Sentinel Review](./2026-10-12-contracts-review.md)

## 2. Quyết định đã chốt

- PostgreSQL database riêng cho Payment; state, idempotency, callback, reconciliation và outbox commit atomic.
- Cash payment synchronous, validate amount và tự tính change bằng integer VND minor unit.
- VNPAY sandbox dynamic QR với một active `PENDING` attempt cho mỗi checkout attempt.
- VNPAY IPN canonical là `GET /callbacks/v1/vnpay/ipn`, query `vnp_*`, verify checksum trước business processing.
- Domain Payment cô lập khỏi VNPAY bằng provider adapter/normalized ports.
- Step Functions điều phối Saga; Payment không tự finalize Order hoặc đọc chéo database.
- Outbox → EventBridge → SQS cho event delivery at-least-once.
- `PaymentPaid` chỉ là payment/Saga fact; `OrderPaid` do Order phát sau Inventory commit và Order finalization.
- Late provider success sau terminal attempt tạo `late_payment_reconciliation` với `SETTLED_LATE`; không phát `PaymentPaid`/`OrderPaid` và không hồi sinh Order.
- Cursor pagination cho history/reconciliation; không cache P0 payment state.
- UUID v7 IDs, BIGINT money minor units, tenant/branch defense-in-depth.
- P1 split tender/refund là entity/state riêng, không sửa payment ledger gốc.

## 3. Warnings và implementation gates

| Gate | Nội dung | Điều kiện hoàn tất |
|---|---|---|
| Parent contracts | Cập nhật parent OpenAPI/AsyncAPI/DB: IPN GET, nullable CASH provider fields, cash fields, reconciliation event và provider-scoped unique key. | Parent contract review + regenerated fixtures pass |
| VNPAY sandbox | Xác nhận merchant configuration, canonical query encoding, SHA256/HMACSHA512 choice, `RspCode` mapping, query status và retry behavior. | Sanitized fixtures + adapter contract tests |
| Gateway privacy | Redact `vnp_*`, secure hash, order info và raw callback khỏi access/application logs. | Security test/log inspection pass |
| Reconciliation operations | Materialize retry threshold, lease expiry, alert, operator ownership, SLA, retention và runbook. | Deployment config + operational runbook approved |
| Reporting | Carry branch timezone/business date, freshness and cross-branch aggregation metadata into Reporting read model. | Reporting consumer schema/test approved |
| P1 refund | Verify VNPAY refund/query capability and pending/timeout behavior before enabling refund. | Provider capability matrix + refund fixtures |

## 4. Required test scenarios before implementation sign-off

- Cash insufficient, exact amount, overpayment/change and duplicate POS retry.
- Concurrent VNPAY initiation and active QR uniqueness.
- Invalid checksum, valid signature/wrong mapping, wrong amount and malformed callback.
- Duplicate, out-of-order, provider-timeout and late IPN.
- Reconciliation claim race, retry/backoff, definitive success/failure, unknown provider and DLQ replay.
- Late `SETTLED_LATE` never emits `PaymentPaid`/`OrderPaid`.
- Payment success + Inventory commit failure never silently finalizes Order.
- Cross-tenant/cross-branch access, role denial, IDOR and secret/raw-payload redaction.
- Outbox retry, consumer deduplication and unknown event version quarantine.
- P1 split tender allocation limits and refund idempotency/balance limits.

## 5. Handoff

Module 4 design workflow is complete. The next work item is implementation planning only after the parent contract amendments and VNPAY sandbox fixture gates above are accepted. No application code was created in this workflow.

