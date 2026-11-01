# Module 11 — Giám sát & chống thất thoát: Final Design Result

**Ngày:** 2026-10-13  
**Trạng thái:** Design complete — Approved with warnings  
**Verdict:** YELLOW — Proceed with Caution  
**Scope:** 8 design artifacts, no application code

## 1. Final outcome

Bộ tài liệu thiết kế Module 11 đã hoàn tất và được duyệt qua các gate Vision → Requirements → Architecture → Sequence → Contracts → Finalization.

Phạm vi đã chốt:

- P0 audit append-only cho hủy món, giảm giá/override và mở két.
- P2 phát hiện bất thường bằng rule-based guardrail + statistical baseline, chỉ tạo finding/case cho Owner/Manager; không tự khóa nhân viên hay chặn giao dịch.
- Owner xem tenant/subset; Manager chỉ xem branch được giao; Cashier/Kitchen không có Audit console.
- Retention tối thiểu 7 năm, legal hold, integrity digest SHA-256 và async export cho lịch sử quá 90 ngày.
- Audit là service trung tâm sở hữu evidence/case/read model; Order, Payment, Shift, POS Device, Promotion/Loyalty và Identity vẫn sở hữu source business state.

## 2. Artifact registry

| Artifact | Vai trò | Trạng thái |
|---|---|---|
| [Vision](./2026-10-13-vision.md) | Scope, outcomes, boundaries, principles | Approved with warnings |
| [Vision review](./2026-10-13-vision-review.md) | Completeness/cross-impact review | Warnings accepted |
| [Requirements](./2026-10-13-requirements.md) | Functional/NFR/security/acceptance requirements | Approved with warnings |
| [Requirements review](./2026-10-13-requirements-review.md) | Requirements sentinel review | Warnings accepted |
| [Architecture](./2026-10-13-architecture.md) | Service/data/communication/failure architecture | Approved |
| [Sequence diagrams](./2026-10-13-sequence-diagrams.adoc) | 8 flows × L1/L2 PlantUML design flows | Confirmed |
| [Contracts](./2026-10-13-contracts.md) | REST/events/DB/digest/rule/case/retention contracts | Approved with warnings |
| [Contracts review](./2026-10-13-contracts-review.md) | API/data contract sentinel review | Warnings accepted |

## 3. Design review summary

| Category | Verdict | Evidence / remaining action |
|---|---|---|
| Scope and completeness | PASS with warnings | P0/P2, actors, boundaries, failure paths, retention and parent amendments are covered. |
| Security and tenant isolation | PASS with implementation gate | Server-resolved Cognito scope, repository predicates, fail-closed denial, redaction and export authorization are defined; Identity amendments and IDOR fixtures remain required. |
| Business logic | PASS with implementation gate | Event taxonomy, discount source, drawer operation key, rule families and case state machine are defined; drawer timeout/result matrix and policy fixtures remain required. |
| Data integrity | PASS with implementation gate | Append-only evidence, inbox dedupe, CAS, digest chain and legal hold are defined; DB roles, migrations and digest test vector remain required. |
| API/event readiness | WARN | Contract catalogue is complete at design level; OpenAPI, AsyncAPI, registry and generated schemas are not yet materialized. |
| Performance | PASS with implementation gate | 90-day bounded query, cursor pagination, indexes, async export and worker separation are defined; load profile remains required. |
| Observability | PASS with implementation gate | Watermark/freshness, DLQ, integrity mismatch, correlation and data-quality events are defined; dashboards/alerts/runbooks remain implementation artifacts. |
| Testing | WARN | Acceptance matrix and fixture list exist; executable contract, replay, security, retention and anomaly fixtures remain required. |

## 4. Code-reviewer gate

### Summary

Không có application source để review. Design artifacts đã được rà theo checklist security, business logic, performance, maintainability và convention. Không phát hiện critical issue; các warning đều là artifact/materialization gates trước production code.

### Warnings (Should Fix)

| # | File:Line | Category | Issue | Recommendation |
|---|---|---|---|---|
| 1 | [`2026-10-13-contracts.md:452-520`](./2026-10-13-contracts.md#L452) | Security / Data integrity | Parent event registry và action-specific producer route chưa là machine-readable artifact. | Tạo `audit-event-registry.yaml`, enforce canonical route/dedupe và test duplicate generic/action-specific event. |
| 2 | [`2026-10-13-contracts.md:495-512`](./2026-10-13-contracts.md#L495) | Business logic | Drawer command/result timeout, delayed callback và contradictory hardware result chưa có đầy đủ state matrix. | Chốt producer contract với POS Device/Shift và golden fixtures trước implementation. |
| 3 | [`2026-10-13-contracts.md:227-367`](./2026-10-13-contracts.md#L227) | API contract | OpenAPI chưa được materialize; CAS/idempotency/header precedence và export download lifecycle mới ở prose. | Sinh OpenAPI 3.1, AsyncAPI/JSON Schema và contract tests. |
| 4 | [`2026-10-13-contracts.md:806-829`](./2026-10-13-contracts.md#L806) | Data integrity | Digest canonicalization chưa có executable test vector. | Tạo input/canonical bytes/hash/previous digest/expected mismatch fixture. |
| 5 | [`2026-10-13-contracts.md:831-864`](./2026-10-13-contracts.md#L831) | Business logic | Rule governance và cold-start/zero-baseline/late-event behavior chưa có fixture executable. | Chốt publish/retire permission, `effectiveAt`, immutable policy version và deterministic fixtures. |
| 6 | [`2026-10-13-contracts.md:770-900`](./2026-10-13-contracts.md#L770) | Security / Operations | Append-only DB roles, archive manifest và partial legal-hold partition behavior chưa được materialize. | Tạo migrations/roles, retention operation contract và failure/retry tests. |

### Checklist summary

| Category | Verdict | Issues |
|---|---|---:|
| Security | WARN | 2 implementation gates; no critical issue |
| Business Logic | WARN | 2 implementation gates; no critical issue |
| Performance | PASS | 0 current issue; load profile still required |
| Clean Code | PASS | Design is separated by workflow artifact; no code reviewed |
| Conventions | WARN | Machine-readable artifacts not yet generated |

## 5. Architecture summary

```text
Order / Promotion / POS Device / Shift / Identity
        │ transactional outbox + canonical source events
        ▼
EventBridge → SQS pos-audit-events → Audit inbox/dedupe
                                      │
                                      ├─ append-only audit_events
                                      ├─ query projection + watermark
                                      ├─ SHA-256 digest/checkpoint
                                      └─ anomaly rules/baselines → finding/case
                                                           │
                                                           ├─ Owner/Manager console
                                                           └─ async export / legal hold
```

Consistency model: source mutation is authoritative; Audit is at-least-once and eventually consistent; duplicate/replay is idempotent; incomplete/stale data is surfaced explicitly and never represented as `NO_DATA`.

## 6. Implementation gates before code

1. Materialize `openapi.yaml`, `openapi-internal.yaml`, `asyncapi.yaml` and JSON Schemas.
2. Materialize `audit-event-registry.yaml` with producer ownership, canonical route, dedupe key, required fields and redaction policy.
3. Apply parent amendments for Identity, Order, Promotion/Loyalty, Payment/Shift/POS Device and POS Platform.
4. Create PostgreSQL/Flyway migrations with tenant/branch constraints, append-only roles, indexes, partition checks and column comments.
5. Create digest canonicalization vector and verify mismatch behavior.
6. Create drawer command/result, duplicate/replay, case CAS/idempotency, legal hold and retention fixtures.
7. Create anomaly policy lifecycle and deterministic rule fixtures for all four initial rule families.
8. Define dashboards/alerts/runbooks for freshness, DLQ, projection lag, export failure, case mutation conflict and integrity mismatch.
9. Run load/security/privacy tests for 90-day query, seven-year export, IDOR, revoked membership, cache leakage, PII redaction and signed-download expiry.

## 7. Delivery roadmap

| Phase | Scope | Exit criteria |
|---|---|---|
| 0. Contract materialization | Parent amendments, OpenAPI/AsyncAPI, registry, schemas, fixtures | Contracts compile/validate; producer ownership and permission matrix approved |
| 1. P0 evidence | Ingest, inbox/dedupe, append-only evidence, cancel/discount/drawer events | P0 sequence flows pass replay/idempotency/redaction tests |
| 2. Query and governance | Timeline/detail, branch scope, cursor, audit console, retention/export/legal hold | IDOR/security, 90-day latency and export authorization pass |
| 3. P2 detection | Feature aggregation, baselines, four rule families, finding creation | Cold-start/late-event/duplicate fixtures pass; no auto-block path exists |
| 4. Case and hardening | Case CAS/actions, digest verification, DLQ/replay, dashboards/runbooks | Integrity, recovery, concurrency, retention and operational readiness pass |

## 8. Traceability matrix

| User requirement | Design coverage | Contract / flow |
|---|---|---|
| Audit hủy món | `OrderLineCancelled.v1`, `OrderCancelled.v1`, append-only evidence | Requirements §4.2; Contracts §6.2; Sequence 1 |
| Audit giảm giá | Automatic/manual source, allocation, reason and outcome | Requirements §4.3; Contracts §3.2/§6.2; Sequence 2 |
| Audit mở két | Operation key, shift/device/operator, hardware result | Requirements §4.4; Contracts §6.2; Sequence 3 |
| Branch/RBAC visibility | Identity decision + repository predicate + scoped cursor/export | Requirements §2/§4.5; Contracts §2/§4; Sequence 4 |
| Bất thường nhân viên | Rule/baseline, evidence refs, explainable finding | Requirements §4.7; Contracts §10; Sequence 5 |
| Case review | Owner/Manager permissions, CAS state transitions, audit case actions | Requirements §4.8; Contracts §11; Sequence 6 |
| Tamper evidence | Append-only DB + chained SHA-256 digest | Requirements §4.6; Contracts §9; Sequence 7 |
| Bảy năm/legal hold/export | Retention matrix, hold precedence, async encrypted export | Requirements §4.6; Contracts §12; Sequence 8 |

## 9. Final checklist

- [x] Vision, Requirements, Architecture và Contracts đã được review.
- [x] User đã duyệt các warnings ở từng gate.
- [x] P0/P2 scope và no-auto-block safety boundary đã chốt.
- [x] Tenant/branch authorization, redaction và append-only ownership đã chốt.
- [x] 8 sequence flows đã tạo và kiểm tra đủ 16 PlantUML blocks.
- [x] API, event, database, digest, anomaly, case và retention contracts đã được ghi nhận.
- [ ] OpenAPI/AsyncAPI/JSON Schema và migration files được materialize.
- [ ] Parent service amendments được cập nhật vào bộ tài liệu nền.
- [ ] Executable fixtures, load profile, security/privacy corpus và operational runbooks được tạo.
- [ ] Chỉ sau các gate trên mới bắt đầu code production.

**Kết luận:** Thiết kế Module 11 hoàn tất ở mức implementation-ready design, với các warnings đã được chấp nhận và được chuyển thành implementation gates rõ ràng. Chưa viết code.
