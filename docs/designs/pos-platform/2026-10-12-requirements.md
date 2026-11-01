Started at: 2026/10/12 14:18:22
Finished at: 2026/10/12 14:18:22
Total time: 0 minutes
---

# Requirements: POS Platform

## Source

- **Vision:** [2026-10-12-vision.md](./2026-10-12-vision.md)
- **Vision review:** [2026-10-12-vision-review.md](./2026-10-12-vision-review.md)
- **Idea:** [idea.md](../../../idea.md)

## Scope and Priority

- **P0:** bắt buộc cho MVP/demo và phải có acceptance test.
- **P1:** thiết kế phải để dành boundary phù hợp nhưng chưa bắt buộc triển khai trong MVP.
- **P2:** ngoài phạm vi thiết kế chi tiết của MVP.
- Các NFR định lượng trong tài liệu này là **demo targets**, không phải production SLA.

## Functional Requirements

### Use Cases

| # | Priority | Actor | Action | System Response |
|---|---|---|---|---|
| UC-01 | P0 | Chủ chuỗi, quản lý, thu ngân, bếp | Đăng nhập bằng Cognito | Xác thực OIDC/JWT, tạo auth context gồm user, tenant, branch và permissions |
| UC-02 | P0 | Chủ chuỗi | Tạo/quản lý tenant, chi nhánh và nhân viên | Lưu membership/role, gán phạm vi chi nhánh, audit thao tác quản trị |
| UC-03 | P0 | Người dùng nghiệp vụ | Truy cập dữ liệu theo role và branch | Chỉ trả dữ liệu thuộc tenant và branch được cấp; truy cập sai scope trả `403` và tạo security audit event |
| UC-04 | P0 | Chủ chuỗi, quản lý | Quản lý catalog, món, size và topping | Tạo/cập nhật/bật/tắt item; thay đổi có hiệu lực theo version hoặc timestamp |
| UC-05 | P0 | Thu ngân | Mở ca với tiền đầu ca | Tạo shift đang mở cho branch và user; không cho mở trùng ca chưa đóng |
| UC-06 | P0 | Thu ngân | Tạo order tại quầy, mang đi hoặc tại bàn | Tạo order draft với branch, loại phục vụ, line item, modifier và ghi chú |
| UC-07 | P0 | Thu ngân | Sửa hoặc hủy line item trước checkout | Kiểm tra state/permission; hủy item nhạy cảm cần quyền quản lý và ghi audit log |
| UC-08 | P0 | Hệ thống | Sinh recommendation từ giỏ hàng | Áp association rules, loại item hết hàng hoặc đã có trong giỏ, trả danh sách gợi ý và ghi `SuggestionShown` |
| UC-09 | P0 | Thu ngân/khách hàng | Chấp nhận một recommendation | Thêm item hợp lệ vào order và ghi `SuggestionAccepted` với correlation/order context |
| UC-10 | P0 | Thu ngân | Tra cứu khách theo số điện thoại | Tìm loyalty profile trong phạm vi tenant, hiển thị dữ liệu đã che/masked và gắn customer vào order |
| UC-11 | P0 | Thu ngân | Thanh toán tiền mặt | Tính tổng tiền, tiền khách đưa và tiền thừa bằng số nguyên; tạo payment và chuyển order qua checkout Saga |
| UC-12 | P0 | Thu ngân/khách hàng | Thanh toán QR VNPAY sandbox | Tạo giao dịch pending và QR; nhận IPN, verify checksum, idempotently cập nhật payment state |
| UC-13 | P0 | Hệ thống | Checkout order qua Saga | Giữ chỗ nguyên liệu → xử lý/đợi payment → hoàn tất; nếu bước sau lỗi thì chạy compensation |
| UC-14 | P0 | Hệ thống | Xử lý payment failure/timeout | Không đánh dấu order `PAID`, giải phóng inventory reservation, lưu lý do và phát event kết quả |
| UC-15 | P0 | Hệ thống | Cộng hoặc thu hồi loyalty points | Chỉ cộng điểm sau payment thành công; reversal tạo ledger entry đối ứng, không sửa lịch sử ledger |
| UC-16 | P0 | Hệ thống | Đối soát giao dịch treo | Job tìm payment pending quá hạn, gọi boundary đối soát hoặc đánh dấu cần xử lý, retry an toàn và đưa DLQ khi quá giới hạn |
| UC-17 | P0 | Thu ngân, quản lý | Đóng ca | Chốt shift, tổng hợp giao dịch/tiền mặt; trạng thái ca không thể ghi thêm order mới |
| UC-18 | P0 | Chủ chuỗi, quản lý | Xem doanh thu theo ngày/chi nhánh và món bán chạy | Đọc CQRS/reporting model, nêu rõ timestamp cập nhật cuối và eventual consistency |
| UC-19 | P0 | Quản lý, auditor | Xem audit log | Truy vấn hành động nhạy cảm theo tenant/branch, actor, resource, thời gian và correlation ID |
| UC-20 | P0 | Developer/operator | Theo dõi một giao dịch phân tán | Dùng correlation ID để liên kết API log, event, state transition, metric và trace |
| UC-21 | P0 | Developer/operator | Tắt hoặc khởi động lại Payment service trong demo | Outbox/retry/DLQ giữ message an toàn; Saga kết thúc ở trạng thái nhất quán và có trace compensation |
| UC-22 | P0 | Hệ thống | Chạy negative authorization test | Từ chối IDOR/cross-tenant/cross-branch read/write, không tiết lộ dữ liệu trong error response |

### P1/P2 Backlog Boundaries

- **P1:** PIN đổi thu ngân, combo và giá riêng theo chi nhánh, sơ đồ bàn/tách/gộp/giữ đơn, KDS WebSocket, split tender/refund, membership tier/FIFO expiry, voucher/rule engine, nhập/kiểm kho, báo cáo nâng cao, ALS và retraining, AI report assistant.
- **P2:** QR self-order, cold start bằng LLM/context, anomaly detection, dự báo nhập hàng, daily summary, hóa đơn điện tử/email/Zalo.
- P1/P2 không được làm thay đổi contract P0 đã chốt; extension phải dùng field/endpoint/event versioning tương thích.

### Business Rules

| # | Priority | Rule | Rationale |
|---|---|---|---|
| BR-01 | P0 | Tenant và branch context lấy từ token/membership đã xác thực; client-provided IDs chỉ là input cần kiểm tra, không phải bằng chứng ownership. | Ngăn IDOR và cross-tenant access. |
| BR-02 | P0 | Owner được quản lý toàn tenant; manager/cashier/kitchen chỉ thao tác trong các branch được gán. Role kitchen có thể đăng nhập ở MVP nhưng KDS là P1. | Phù hợp RBAC và phạm vi demo. |
| BR-03 | P0 | Mỗi API command có idempotency key khi có side effect; consumer và IPN deduplicate theo provider/event key. | Retry không tạo giao dịch, điểm hoặc tồn kho trùng. |
| BR-04 | P0 | Tiền được biểu diễn bằng số nguyên theo đơn vị tiền tệ nhỏ nhất; không dùng floating point. VAT rounding dùng một quy tắc deterministic thống nhất ở Order/Payment. | Tránh sai số tài chính. |
| BR-05 | P0 | Order chỉ được `PAID` sau khi payment thành công và Saga hoàn tất các bước bắt buộc. | Không ghi nhận doanh thu cho giao dịch chưa hoàn tất. |
| BR-06 | P0 | Checkout Saga theo thứ tự: validate order → reserve inventory → initiate/confirm payment → finalize order → publish downstream events. | Xác định source/state transition cho flow chính. |
| BR-07 | P0 | Nếu payment thất bại, timeout hoặc workflow bị hủy, compensation phải release reservation; compensation phải idempotent. | Không giữ tồn kho vô thời hạn. |
| BR-08 | P0 | Reservation được release khi checkout thất bại/hủy hoặc hết timeout cấu hình; timeout mặc định của demo là 15 phút. | Có lifecycle rõ ràng cho inventory hold. |
| BR-09 | P0 | IPN chỉ được chấp nhận sau khi verify checksum/HMAC và kiểm tra merchant/order/provider mapping. | Không tin callback giả hoặc callback sai order. |
| BR-10 | P0 | Payment state machine tối thiểu gồm `PENDING`, `PAID`, `FAILED`, `EXPIRED`, `CANCELLED`; transition không hợp lệ bị từ chối và audit. | Ngăn trạng thái thanh toán mâu thuẫn. |
| BR-11 | P0 | Loyalty là append-only ledger; balance là projection từ ledger; reversal tạo entry đối ứng gắn với transaction gốc. | Truy vết và đối soát được cộng/thu hồi điểm. |
| BR-12 | P0 | Điểm chỉ được cộng từ event payment/order thành công; cấu hình tỷ lệ tích/đổi điểm nằm trong tenant config và được seed cho demo. | Không cộng điểm cho order thất bại. |
| BR-13 | P0 | Recommendation không trả item đã có trong cart, item đang tắt hoặc hết tồn khả dụng; mỗi lần hiển thị/chấp nhận là một analytics event riêng. | Gợi ý phải actionable và đo được. |
| BR-14 | P0 | Hủy line item trước khi order `PAID` cần permission phù hợp; hành động nhạy cảm ghi actor, reason, before/after và correlation ID. | Kiểm soát thất thoát. |
| BR-15 | P0 | Order sau `PAID` không hỗ trợ refund đầy đủ trong MVP; refund là P1, nhưng ledger reversal contract phải để dành cho phase sau. | Giới hạn scope nhưng giữ được boundary mở rộng. |
| BR-16 | P0 | Reporting model là eventual consistent; API phải trả `lastUpdatedAt`/data freshness để người dùng biết độ trễ. | Tránh hiểu nhầm dữ liệu báo cáo chưa cập nhật ngay. |
| BR-17 | P0 | Outbox record được ghi cùng transaction nghiệp vụ; chỉ đánh dấu published sau khi broker acknowledge. | Không mất event giữa DB và broker. |
| BR-18 | P0 | Retry dùng exponential backoff có giới hạn; message lỗi quá số lần retry vào DLQ kèm lý do và correlation ID. | Bảo vệ hệ thống khỏi retry storm và giữ message lỗi để điều tra. |
| BR-19 | P0 | Audit log là append-only, tenant-scoped và không chứa secret, token, checksum raw hoặc dữ liệu thanh toán nhạy cảm. | Đáp ứng traceability và data safety. |
| BR-20 | P0 | Event payload phải có event ID, event type/version, occurredAt, tenantId, branchId, correlationId và producer. | Cho phép deduplication, routing và tracing xuyên service. |

### Integration Requirements

| # | Integration | Requirement |
|---|---|---|
| INT-01 | Amazon Cognito | OAuth2/OIDC login và JWT verification; authorization dùng role/membership claims kết hợp kiểm tra server-side. |
| INT-02 | VNPAY sandbox | Dynamic QR, checksum/HMAC verification, IPN endpoint, idempotency và reconciliation boundary. Không lưu raw card data. |
| INT-03 | EventBridge + SQS | Domain/integration events đi qua broker; queue có retry, visibility timeout, DLQ và consumer deduplication. |
| INT-04 | Step Functions | Orchestrate checkout Saga và expose execution/correlation state đủ để debug compensation. |
| INT-05 | PostgreSQL | Mỗi service sở hữu database/schema riêng; không query chéo database trong runtime business flow. |
| INT-06 | CloudWatch/X-Ray | Nhận structured logs, metrics, alerts và distributed trace từ OpenTelemetry. |
| INT-07 | Secrets Manager | Lưu VNPAY secret, database credentials, signing keys và integration credentials; không hardcode trong source/config committed. |
| INT-08 | Local adapters | Khi AWS/sandbox không khả dụng, adapter local cho phép demo core flow mà vẫn giữ nguyên API/event contract. |

## Non-Functional Requirements

> Các target dưới đây là mục tiêu chấp nhận cho MVP demo; production SLA sẽ được xác định ở một phase hardening riêng.

| Category | Requirement | Target |
|---|---|---|
| Performance | Core synchronous API (không chờ external provider) | p95 ≤ 500 ms trong demo load profile |
| Performance | Recommendation lookup | p95 ≤ 300 ms với dataset demo |
| Performance | Reporting query | p95 ≤ 2 s cho daily revenue/top items |
| Consistency | Event propagation tới reporting/loyalty projection | 95% event được xử lý trong ≤ 30 s trong điều kiện broker bình thường |
| Throughput | Checkout command path | Chịu ít nhất 10 checkout attempts/second trong 5 phút bằng k6, không duplicate side effect |
| Scalability | Service scaling | Core services stateless ở API layer và có thể scale độc lập; worker concurrency cấu hình được |
| Availability | Failure isolation | Restart một core service không làm mất committed transaction hoặc message đã ghi outbox |
| Recovery | Demo recovery | Sau khi dependency phục hồi, retry/DLQ replay đưa flow về trạng thái cuối hợp lệ trong ≤ 5 phút |
| Security | Authentication | Cognito OIDC/JWT, token signature/expiry/issuer/audience được verify |
| Security | Authorization | RBAC + tenant/branch ownership check ở API/service/repository; negative tests cho BOLA/IDOR |
| Security | Secrets/PII | Secret qua Secrets Manager/env injection; phone và PII được mask trong UI/log, không log token/card/checksum raw |
| Security | Input safety | Validate payload/schema, chống SQL injection, giới hạn request body/rate limit và không lộ stack trace |
| Reliability | Idempotency | Replay cùng API key/IPN/event không tạo duplicate order/payment/ledger/reservation |
| Reliability | Messaging | Retry exponential backoff, bounded attempts, DLQ và replay procedure có kiểm soát |
| Observability | Traceability | Correlation ID bắt buộc cho request/event; OpenTelemetry trace tới X-Ray; structured JSON logs |
| Observability | Operational signals | Metrics tối thiểu cho checkout success/failure, payment pending, reservation, DLQ depth, event lag và auth denial |
| Maintainability | Contract discipline | API-first OpenAPI + AsyncAPI; contract validation trong CI trước khi build image |
| Testing | Automated coverage | Unit, integration, contract, end-to-end, k6 load, chaos/failure injection và security negative tests |
| Deployment | Reproducibility | Core stack chạy được bằng Docker Compose; AWS resources được mô tả bằng IaC và cấu hình theo environment |

## Constraints

| Type | Constraint | Impact |
|---|---|---|
| Technology | Backend Java 21 + Spring Boot 3.x | Dùng Spring Security/OAuth2 resource server, Spring Data/JDBC/JPA phù hợp từng service và Spring Cloud/AWS SDK khi cần. |
| Technology | Frontend Angular | UI role-based, POS flow và dashboard dùng route guard, typed API client và form validation. |
| Technology | PostgreSQL per service | Transactional workloads dùng SQL; reporting đọc từ projection/read model, không query chéo service. |
| Technology | Docker Compose local + AWS hybrid | Local-first developer/demo path; external integration phải có adapter và config boundary. |
| Technology | API-first | OpenAPI cho synchronous API, AsyncAPI/event schema cho broker message; contract là artifact trước implementation. |
| Technology | EventBridge/SQS + Step Functions | Architecture phải giữ event-driven integration và orchestration Saga như mục tiêu kỹ thuật của idea. |
| Infrastructure | Terraform là IaC mặc định cho MVP | Tránh phụ thuộc framework ứng dụng; AWS resource naming/state/backend phải được thiết kế ở Architecture. |
| Infrastructure | ECS Fargate là deployment target cloud | Container image, health check, graceful shutdown và stateless API là bắt buộc. |
| Security | Cognito, Secrets Manager và TLS | Không commit secret; mọi external callback phải verify authenticity và chống replay. |
| Scope | Chỉ P0 là implementation acceptance | P1/P2 chỉ cần boundary/extension point, không được làm chậm vertical slice P0. |
| Data | VND và tiền nguyên số | Không dùng floating point; quy tắc VAT/rounding phải deterministic và testable. |
| Compliance | Chưa yêu cầu chứng nhận pháp lý cụ thể cho demo | Vẫn áp dụng baseline bảo vệ PII, secret, auditability và không lưu card data. |
| Delivery | Chưa có deadline ngoài mục tiêu demo | Roadmap sẽ chia theo vertical slice và dependency, không cam kết production SLA. |

## Test and Acceptance Strategy

| Test Layer | Required Coverage |
|---|---|
| Unit | State machines, money/VAT rounding, inventory reservation, loyalty ledger, recommendation filters, permission policies. |
| Integration | PostgreSQL repositories, Cognito JWT validation, VNPAY checksum/IPN adapter, broker/outbox, Step Functions boundary. |
| Contract | OpenAPI request/response and error schema; AsyncAPI event version/payload/required metadata. |
| End-to-end | Full P0 happy path across tenant/branch, including cash and VNPAY sandbox paths. |
| Failure injection | Payment unavailable, IPN duplicated/out-of-order, inventory insufficient, broker retry/DLQ, service restart during Saga. |
| Security | Cross-tenant, cross-branch, role escalation, IDOR, invalid JWT, replayed callback and sensitive-data leakage. |
| Load | k6 profile for checkout, recommendation and reporting; verify p95 targets and no duplicate side effects. |
| Chaos | Stop/restart selected service or dependency and verify recovery/compensation trace. |

## Open Decisions Carried to Architecture

1. Bounded context-to-service mapping and ownership of shared/reference data.
2. UUID v7, ULID or another distributed ID strategy.
3. PostgreSQL database/schema isolation level per service and migration tool details.
4. Optimistic versus pessimistic inventory locking under the demo concurrency profile.
5. Exact EventBridge/SQS topology, queue naming, ordering and deduplication strategy.
6. Step Functions orchestration state model and compensation timeout behavior.
7. Angular API client generation and shared contract repository layout.
8. CloudWatch/X-Ray deployment topology and local tracing fallback.

## Domain Skills Referenced

- **spec-reviewer:** kiểm tra completeness, cross-impact, tenant isolation, flow integrity và testing gaps của Vision.
- **api-design:** API-first, OpenAPI/AsyncAPI, JWT, error handling, rate limiting và versioning requirements.
- **database-design:** database-per-service, transactional data, money representation, consistency, indexing/partitioning questions và migration concerns.
- **log-processing:** structured logging, correlation ID, entity context và PII/secret masking requirements.

## Approval

- **Status:** Approved by user
- **Next phase:** Completed; Architecture Design approved
