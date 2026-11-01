# Module 12 — Tích hợp mở rộng: Final Design Result

**Ngày:** 2026-10-13  
**Trạng thái:** Design complete — Approved with warnings  
**Verdict:** YELLOW — Proceed with Caution  
**Scope:** 8 design artifacts, no application code

## 1. Final outcome

Thiết kế Module 12 đã hoàn tất qua các gate Vision → Requirements → Architecture → Sequence → Contracts → Finalization.

Module 12 gồm:

- **Integration Runtime:** invoice technical/sandbox operation từ canonical `OrderPaid.v1`, email/Zalo delivery async, provider adapters, retry/DLQ, status API, consent/PII boundary và observability.
- **Demo Control Plane:** non-production-only scenario runner, seed/reset namespace, checkpoints, run leases, `PAYMENT_UNAVAILABLE` fault injection và X-Ray trace summary.
- **Ownership boundary:** không sở hữu hoặc thay thế Order, Payment, Step Functions Saga, Inventory, Loyalty, Recommendation, Reporting hay AI business state.

P2 invoice/delivery không nằm trong synchronous checkout path. Demo P0 chạy qua service thật và VNPAY sandbox; `OrderPaid.v1` vẫn là paid-order truth; Reporting/AI vẫn là authority cho uplift metric.

## 2. Artifact registry

| Artifact | Vai trò | Trạng thái |
|---|---|---|
| [Vision](./2026-10-13-vision.md) | Scope, value, stakeholders, boundaries | Approved with warnings |
| [Vision review](./2026-10-13-vision-review.md) | Cross-impact/security readiness review | Warnings accepted |
| [Requirements](./2026-10-13-requirements.md) | Invoice, delivery, demo, privacy, NFR, acceptance | Approved with warnings |
| [Requirements review](./2026-10-13-requirements-review.md) | Requirements sentinel review | Warnings accepted |
| [Architecture](./2026-10-13-architecture.md) | Runtime/demo split, data/communication/failure architecture | Approved |
| [Sequence diagrams](./2026-10-13-sequence-diagrams.adoc) | 8 flows × L1/L2 = 16 PlantUML blocks | Confirmed and validated |
| [Contracts](./2026-10-13-contracts.md) | REST, callbacks, provider ports, events, DB, retention | Approved with warnings |
| [Contracts review](./2026-10-13-contracts-review.md) | API/data contract sentinel review | Warnings accepted |

## 3. Design review summary

| Category | Verdict | Evidence / remaining action |
|---|---|---|
| Scope and completeness | PASS with warnings | Invoice/delivery, demo success/failure/reset, AI attribution and ownership boundaries are covered. Parent amendments remain gates. |
| Security and tenant isolation | PASS with implementation gate | Cognito/workload scopes, repository predicates, callback boundary, PII redaction and production/demo split are defined; encryption/key/lease fixtures remain required. |
| Business logic | PASS with implementation gate | Invoice/delivery/demo state machines and provider unknown/retry paths are defined; provider reconciliation and OrderPaid compatibility require executable contracts. |
| Data integrity | PASS with implementation gate | Inbox/outbox, convergence keys, CAS, immutable attempts and money reconciliation are defined; Flyway roles/comments/migrations remain required. |
| API/event readiness | WARN | API catalogue, callback routes and event schemas exist at design level; OpenAPI/AsyncAPI/event registry are not yet generated. |
| Performance | PASS with implementation gate | Async workers, rate limits, bounded lists and load targets are defined; provider rate profile and k6 evidence remain required. |
| Observability | PASS with implementation gate | Correlation/causation/saga/demo IDs, X-Ray, metrics, DLQ and checkpoint quality are defined; dashboards/runbooks remain implementation artifacts. |
| Testing | WARN | Matrix covers replay, provider, PII, demo and AI attribution; executable parent/provider/cleanup/retention fixtures remain required. |

## 4. Code Review Report

**Scope:** 8 design artifacts in `docs/designs/integration/` — Vision, Vision review, Requirements, Requirements review, Architecture, Sequence diagrams, Contracts, Contracts review. No application code or generated implementation was reviewed.  
**Date:** 2026-10-13  
**Verdict:** YELLOW — Proceed with Caution

### Summary

Design is coherent and preserves parent service ownership, async failure isolation and non-production demo safety. No critical security or architecture blocker was found; warnings are materialization gates before production code.

### Warnings (Should Fix)

| # | File:Line | Category | Issue | Recommendation |
|---|---|---|---|---|
| 1 | [`2026-10-13-contracts.md:19-27`](./2026-10-13-contracts.md#L19) | API/Event compatibility | Current parent `OrderPaid` + version 1 and target `OrderPaid.v1` compatibility mapping chưa là executable registry. | Publish canonical event registry, alias/cutoff policy, source version and replay fixture. |
| 2 | [`2026-10-13-contracts.md:141-159`](./2026-10-13-contracts.md#L141) | Security/Business logic | `invoiceRecipientRef` capture chưa có full Order attempt validation/context contract. | Add Order internal validation schema, expiry/binding and missing-ref behavior before generated clients. |
| 3 | [`2026-10-13-contracts.md:371-404`](./2026-10-13-contracts.md#L371) | Reliability/Security | Provider `Unknown`, reconciliation và callback signature/replay semantics chưa materialize thành executable vectors. | Add reconcile operation, callback schemas/signature fixtures, lease and provider runbook. |
| 4 | [`2026-10-13-contracts.md:277-296`](./2026-10-13-contracts.md#L277) | Concurrency/API | `If-Match` và `expectedVersion` precedence, idempotent replay response và cache headers chưa chốt trong OpenAPI. | Chọn one version authority, define 409/replay/ETag/no-cache behavior. |
| 5 | [`2026-10-13-contracts.md:490-508`](./2026-10-13-contracts.md#L490) | Privacy | PII encryption field còn cho phép `BYTEA/TEXT ref`, chưa chốt KMS key version/tokenization/rotation. | Materialize one encryption boundary, restricted roles, rotation and erasure tests. |
| 6 | [`2026-10-13-contracts.md:593-608`](./2026-10-13-contracts.md#L593) | Retention | Integration 2-year/30-day policy và Audit 7-year references cần data-class/hold manifest cụ thể. | Generate retention/legal-hold matrix and archive/reference survival fixtures. |
| 7 | [`2026-10-13-contracts.md:551-557`](./2026-10-13-contracts.md#L551) | Demo safety | Parent seed/reset cleanup adapters và partial cleanup recovery chưa có machine-readable ownership contract. | Define resource manifest, reverse cleanup order, TTL, retries and no-delete guard per parent. |
| 8 | [`2026-10-13-contracts.md:330-344`](./2026-10-13-contracts.md#L330) | Security/Operations | Fault lease propagation/attestation/kill-switch precedence chưa đầy đủ. | Add signed run context, environment attestation, max TTL and Payment adapter verification. |
| 9 | [`2026-10-13-contracts.md:433-443`](./2026-10-13-contracts.md#L433) | AI attribution | Recommendation/Order authoritative add-line evidence và Reporting uplift function chưa được materialize trong parent schemas. | Keep Integration read-only; publish parent attribution/metric registry and golden fixtures. |

### Checklist summary

| Category | Verdict | Issues |
|---|---|---:|
| Security | WARN | 3 implementation gates; no critical issue |
| Business Logic | WARN | 3 implementation gates; no critical issue |
| Performance | PASS | Bounded API/worker/load targets defined; load evidence pending |
| Clean Code | PASS | Responsibilities separated into runtime, adapters and demo control plane; no code reviewed |
| Conventions | WARN | Machine-readable artifacts and parent registries not generated yet |

## 5. Architecture summary

```text
Order/Payment/Shift/Inventory/Loyalty/Recommendation/Reporting/AI
              │ canonical APIs/events + outbox/inbox
              ▼
EventBridge → SQS pos-integration-events → Integration inbox/dedupe
                                                │
                        ┌───────────────────────┴──────────────────────┐
                        │ Integration Runtime                          │
                        │ recipient → invoice → delivery workers       │
                        │ provider adapters / status / audit references │
                        └───────────────────────────────────────────────┘

Demo Control Plane (DEMO/TEST only)
  → real parent APIs → checkpoints/resource manifest → X-Ray summary
  → run-scoped Payment fault lease → Order Saga compensation assertion
```

Consistency model: parent service owns business truth; Integration is at-least-once/eventually consistent; provider calls use idempotency/reconciliation; demo checkpoints read authoritative APIs/events; stale/partial/trace-degraded quality is explicit.

## 6. Implementation gates before code

1. Publish `OrderPaid.v1` additive amendment with opaque `invoiceRecipientRef`, profile, attempt/source versions, safe totals/lines and compatibility alias policy.
2. Publish `openapi.yaml`, `openapi-internal.yaml`, callback schemas and generated client types.
3. Publish `asyncapi.yaml` and `integration-event-registry.yaml` with routes, versions, dedupe keys, redaction and parent aliases.
4. Materialize provider ports and sandbox fixtures for invoice, email and Zalo: accepted, timeout, 429, 5xx, unknown, callback replay and signature failure.
5. Materialize PostgreSQL/Flyway migrations, restricted PII roles, KMS/key metadata, comments, constraints, indexes and retention jobs.
6. Publish demo scenario/resource-manifest/checkpoint schemas and per-parent seed/reset adapters with cleanup ownership.
7. Publish signed run/fault-lease contract, Payment boundary fixture, kill switch and environment isolation tests.
8. Publish Recommendation/Order/Reporting/AI attribution and uplift metric registry/golden data.
9. Add security/privacy tests for IDOR, revoked membership, scope cache, callback replay, PII/secret redaction and production route rejection.
10. Run load/recovery tests for 10 paid events/sec, 100 delivery attempts, 10 concurrent demo runs, DLQ replay, provider crash and reset race.

## 7. Implementation roadmap

| Phase | Scope | Exit criteria |
|---|---|---|
| 0. Parent contracts | `OrderPaid` amendment, Identity permissions, event registry, attribution/metric schemas, provider capability profiles | Schemas validate; aliases/dedupe/scope and parent fixtures pass |
| 1. Integration foundation | Runtime API/auth, PostgreSQL, inbox/outbox, recipient consent snapshot, safe status/read model | Tenant/branch/PII/CAS/idempotency tests pass; no raw contact in logs/events |
| 2. Invoice technical path | `OrderPaid` consumer, invoice operation, sandbox adapter, retry/unknown/reconcile/DLQ | Duplicate/replay/crash/provider fixtures converge to one technical invoice |
| 3. Delivery channels | Email/Zalo adapters, consent gate, channel idempotency, callbacks, rate limit, manual retry | Success/failure/expiry/revocation/callback/security fixtures pass |
| 4. Demo Control Plane | Non-prod run API, preflight, seed/reset manifests, checkpoints, X-Ray summary, fault lease | Full success demo and Payment outage compensation demo pass without cross-run leakage |
| 5. AI attribution and hardening | Reporting/Recommendation/AI function integration, uplift golden tests, dashboards, retention/recovery | AI safe answer/provenance correct; load/security/retention/replay/runbooks approved |

Estimated effort: approximately **5–8 weeks** for vertical slices and hardening, excluding parent contract review, provider approval and infrastructure provisioning.

## 8. Traceability matrix

| User requirement | Design coverage | Contract/evidence |
|---|---|---|
| Hóa đơn điện tử từ máy tính tiền | `OrderPaid` consumer, technical sandbox profile, invoice state machine/provider adapter | Requirements INT-FR-010..019; Contracts §6–7; Sequence 2 |
| Gửi qua email/Zalo | Consent snapshot, channel adapter, async delivery/retry/expiry/status | Requirements INT-FR-001..006, INT-FR-020..027; Contracts §3–6; Sequence 3 |
| Demo P0 success | Demo run/seed/preflight/checkpoints, real parent APIs, VNPAY sandbox, Loyalty/Inventory/Reporting/AI verification | Requirements INT-FR-030..044; Architecture §5; Sequence 4/6 |
| Payment outage compensation | Run-scoped fault lease, Order Saga authority, Inventory release assertion, no-paid assertion | Requirements INT-FR-036; Contracts §5.3; Sequence 5 |
| X-Ray trace | Correlation/causation/saga/demo IDs, trace refs, degraded quality state | Vision §4.2; Contracts §3.5/§12; all sequence flows |
| AI Coca uplift question | Approved Reporting/Recommendation functions, paid attribution, freshness/provenance, safe limitation | Requirements INT-FR-040..044; Sequence 6 |
| Retry/replay/recovery | Inbox/outbox, provider idempotency, callback dedupe, reconciliation, DLQ and reset leases | Contracts §7/§9/§11; Sequence 7/8 |
| Tenant/privacy safety | Cognito/workload scope, masked contact, KMS boundary, environment guard, Audit refs | Requirements §2/§7/§10; Contracts §2/§10/§12 |

## 9. Final checklist

- [x] Vision, Vision review, Requirements, Requirements review, Architecture và Contracts đã được tạo.
- [x] User đã duyệt Vision, Requirements, Architecture/sequence participants và Contracts warnings.
- [x] Integration Runtime và non-production Demo Control Plane đã tách boundary.
- [x] `OrderPaid` canonical trigger, async invoice/delivery và no-cross-domain mutation đã chốt.
- [x] 8 sequence flows và 16 PlantUML blocks đã tạo/kiểm tra.
- [x] Provider adapter, consent/PII, retry/DLQ, demo fault lease, X-Ray và AI attribution đã có contract coverage.
- [ ] Machine-readable OpenAPI/AsyncAPI/JSON Schema và parent amendments được publish.
- [ ] Flyway migrations, PII roles/key policy, provider signature vectors và retention manifest được tạo.
- [ ] Demo seed/reset adapters, fault-lease security tests và full-service environment được provision.
- [ ] Load, replay, callback, IDOR, privacy, AI metric và operational runbook gates pass.
- [ ] Chỉ sau các implementation gates trên mới bắt đầu code production.

**Kết luận:** Module 12 đã hoàn tất ở mức implementation-ready design với verdict **APPROVED WITH WARNINGS**. Các warnings đều đã được chuyển thành implementation gates có owner/artifact rõ ràng. Chưa viết code production.
