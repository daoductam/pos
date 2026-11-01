# POS Platform

Nền tảng POS đa tenant cho chuỗi cửa hàng F&B, được thiết kế theo DDD và bounded
context. Mục tiêu là hỗ trợ bán hàng tại quầy/bàn, thanh toán, kho, loyalty,
khuyến mãi, báo cáo và các tính năng AI trong một kiến trúc có khả năng mở rộng,
quan sát và phục hồi lỗi.

## Trạng thái repository

Repository hiện ở giai đoạn **design-first**. Chưa có application source code,
build manifest hoặc deployment runtime. `idea.md` là nguồn phạm vi ban đầu; các
tài liệu thiết kế được lưu tại [`docs/designs/`](docs/designs/).

## Kiến trúc định hướng

- DDD/bounded context và database per service.
- API Gateway cho request đồng bộ.
- EventBridge + SQS cho event-driven integration.
- Saga orchestration bằng Step Functions với compensation.
- Transactional outbox/inbox, idempotency, retry exponential backoff và DLQ.
- CQRS/read model cho báo cáo, không query chéo database giữa các service.
- Cognito OAuth2/OIDC + JWT, RBAC và giới hạn dữ liệu theo tenant/chi nhánh.
- PostgreSQL, Docker, ECS Fargate, IaC và CI/CD là các hướng công nghệ mục tiêu;
  stack triển khai chi tiết sẽ được chốt bằng ADR.
- OpenTelemetry + AWS X-Ray, correlation ID, structured logging và CloudWatch.

## Phạm vi module

1. Tài khoản & phân quyền (Identity & Access)
2. Menu (Catalog)
3. Bán hàng / Order
4. Thanh toán (Payment)
5. Khách hàng thân thiết & khuyến mãi (Loyalty)
6. Kho nguyên liệu (Inventory)
7. Ca làm việc (Shift)
8. Gợi ý món / upsell (Recommendation AI)
9. Trợ lý AI cho chủ quán (AI Assistant)
10. Báo cáo & dashboard (Reporting)
11. Giám sát & chống thất thoát (Audit & Loss Prevention)
12. Tích hợp mở rộng (Integration)

Chi tiết ưu tiên P0/P1/P2 và các quyết định nghiệp vụ nằm trong [`idea.md`](idea.md)
và các thư mục module tương ứng dưới `docs/designs/`.

## Kịch bản demo mục tiêu

Thu ngân mở ca → tạo đơn gà rán → hệ thống gợi ý Coca → khách chấp nhận → nhập số
điện thoại để nhận diện khách thân thiết → thanh toán QR VNPAY → IPN chuyển đơn
sang `PAID` → cộng điểm và trừ nguyên liệu. Khi Payment service lỗi, checkout saga
phải nhả reservation nguyên liệu và toàn bộ luồng phải truy được qua X-Ray.

## Làm việc với repository

1. Đọc [`AGENTS.md`](AGENTS.md) trước khi tạo hoặc thay đổi artifact.
2. Với module/capability mới, dùng workflow tại [`.agent/workflows/design.md`](.agent/workflows/design.md).
3. Duy trì artifact theo thứ tự vision → requirements → architecture → contracts →
   sequence diagrams → result/review.
4. Không triển khai code khi design gate còn blocker chưa được xử lý.
5. Giữ tenant/branch authorization, idempotency, auditability, observability và
   failure compensation trong mọi thiết kế P0.

## Bảo mật và dữ liệu

Không commit secret, token hoặc dữ liệu khách hàng thật. Mọi endpoint phải validate
input và kiểm tra authorization ở server; log phải masking PII và thông tin thanh toán
nhạy cảm. Các service không được truy cập trực tiếp database của service khác.
