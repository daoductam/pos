# AGENTS.md

## Project overview

Đây là repository design-first cho nền tảng POS đa tenant dành cho chuỗi cửa hàng F&B.
Hiện repository chưa có application source code, build manifest hoặc runtime deployment;
các tài liệu trong `docs/designs/` là nguồn thiết kế chuẩn bị cho giai đoạn triển khai.

Nguồn ý tưởng và phạm vi nghiệp vụ: [`idea.md`](idea.md).

## Architecture direction

- DDD và bounded context; mỗi service sở hữu database riêng.
- API Gateway cho truy cập đồng bộ; EventBridge + SQS cho tích hợp bất đồng bộ.
- Saga orchestration bằng Step Functions, transactional outbox/inbox và compensation.
- CQRS/read model cho báo cáo; không query trực tiếp database của service khác.
- Idempotency cho API, consumer và payment IPN; retry exponential backoff và DLQ.
- OAuth2/OIDC + JWT qua Cognito; RBAC và giới hạn dữ liệu theo tenant/chi nhánh.
- PostgreSQL là định hướng database cho các service nghiệp vụ.
- Docker, ECS Fargate, IaC và CI/CD là định hướng hạ tầng; công cụ IaC cụ thể cần được
  chốt trong ADR trước khi triển khai.
- OpenTelemetry + AWS X-Ray, correlation ID, structured logging, CloudWatch metrics và
  alerting cho observability.

## Business modules

1. Identity & Access — Cognito, RBAC, tenant/branch scope, nhân viên và PIN POS.
2. Catalog — danh mục, món, size, modifier, combo, giá và tag.
3. Order — đơn hàng, bàn, KDS và checkout saga.
4. Payment — tiền mặt, VNPAY sandbox, state machine, IPN và đối soát.
5. Loyalty — customer profile, points ledger, tiers, voucher và promotion rules.
6. Inventory — công thức, reservation, stock movements và cảnh báo tồn.
7. Shift — mở/đóng ca và đối soát tiền mặt.
8. Recommendation — association rules, ALS và event tracking.
9. AI Assistant — hỏi đáp báo cáo bằng LLM với function calling và guardrail.
10. Reporting — read model/dashboard cho doanh thu, món, khuyến mãi và recommendation.
11. Audit & Loss Prevention — audit log và phát hiện bất thường.
12. Integration — hóa đơn điện tử, email/Zalo và kịch bản demo end-to-end.

## Repository conventions

- Luôn kiểm tra tenant/branch scope ở authorization và tại repository/query; không tin
  branch ID do client tự gửi.
- Dùng UUID v7 cho ID mới, UTC cho timestamp và số nguyên VND cho tiền; quy tắc VAT/
  rounding phải được đặc tả rõ.
- Service chỉ ghi dữ liệu do mình sở hữu. Trao đổi liên service qua API hoặc event đã
  version hóa, không dùng cross-service database query.
- Consumer và handler phải an toàn khi xử lý lại; dùng idempotency key, inbox/dedup và
  correlation ID khi phù hợp.
- Hợp đồng đồng bộ dùng OpenAPI 3.1; event dùng AsyncAPI/JSON Schema. Breaking change
  phải có versioning và migration plan.
- Không ghi log secret, token, checksum, thông tin thanh toán nhạy cảm hoặc PII không cần
  thiết; cần masking/redaction và Secrets Manager cho secret vận hành.
- Validate input, chống IDOR, kiểm tra authorization ở server, dùng parameterized query
  và không trả stack trace hoặc dữ liệu nội bộ ra client.
- Ưu tiên code dễ đọc, trách nhiệm rõ, tránh magic value, dead code, duplication và god
  class. Mọi thay đổi nghiệp vụ quan trọng cần test và auditability tương ứng.

## Design workflow

- Với module hoặc capability mới, bắt đầu bằng `.agent/workflows/design.md`.
- Duy trì các artifact theo thứ tự: vision, requirements, architecture, contracts,
  sequence diagrams và result/review.
- Không bắt đầu implementation khi các design gate còn chưa được duyệt hoặc còn blocker
  chưa có owner/plan.
- Các tài liệu hiện có nằm trong `docs/designs/<module>/`; giữ ngày tháng và naming
  convention hiện tại khi bổ sung artifact.
- Khi có quyết định ảnh hưởng nhiều service hoặc làm thay đổi target stack, ghi thành ADR
  trước khi cập nhật code/tài liệu phụ thuộc.

## Validation expectations

- Unit test cho domain/rule/state machine.
- Integration test cho database, messaging, payment callback và authorization boundary.
- Contract test cho API/event; kiểm tra idempotency, retry, timeout, DLQ và compensation.
- Load test bằng k6 và chaos test cho các luồng P0 khi bước implementation được mở.
- Mọi flow P0 phải có trace xuyên service và có thể quan sát được failure/compensation.

## Current status

Tất cả module nghiệp vụ chính đã có bộ tài liệu thiết kế trong `docs/designs/`. Chưa có
code production; khi bắt đầu triển khai cần tạo ADR về stack ứng dụng, cấu trúc monorepo
hoặc multirepo, CI/CD, IaC và chiến lược migration.
