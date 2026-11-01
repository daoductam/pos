# Kết quả Thiết kế Kiến trúc: Identity & Access

## Kết luận Đánh giá: PHÊ DUYỆT CÓ ĐIỀU KIỆN (APPROVED WITH WARNINGS)

Hồ sơ thiết kế của service `identity-access` hoàn toàn chặt chẽ, mạch lạc, tuân thủ tuyệt đối các ràng buộc của nền tảng POS Platform và sẵn sàng để bước vào giai đoạn lập trình sau khi các hành động chuẩn bị dưới đây được xác nhận. Quá trình rà soát an ninh không phát hiện bất kỳ lỗ hổng nghiêm trọng (blocker) hay đường dẫn truy cập trái phép xuyên tenant nào.

---

## Danh mục Tài liệu Thành phần (Artifacts)

| Giai đoạn | Tài liệu | Đường dẫn | Trạng thái |
|---|---|---|---|
| **Tầm nhìn (Vision)** | Bản Tầm nhìn Module | [`2026-10-12-vision.md`](./2026-10-12-vision.md) | Đã phê duyệt |
| **Đánh giá Tầm nhìn** | Báo cáo Rà soát Tầm nhìn (Sentinel Report) | [`2026-10-12-vision-review.md`](./2026-10-12-vision-review.md) | Đã chấp nhận các cảnh báo |
| **Yêu cầu (Requirements)** | Yêu cầu Nghiệp vụ & Kỹ thuật | [`2026-10-12-requirements.md`](./2026-10-12-requirements.md) | Đã phê duyệt |
| **Kiến trúc (Architecture)** | Kiến trúc Hệ thống Module | [`2026-10-12-architecture.md`](./2026-10-12-architecture.md) | Đã phê duyệt |
| **Hợp đồng (Contracts)** | Hợp đồng API & Cấu trúc Dữ liệu | [`2026-10-12-contracts.md`](./2026-10-12-contracts.md) | Đã phê duyệt |
| **Tổng kết (Finalization)** | Kết quả Thiết kế Module | Tài liệu này | Phê duyệt có điều kiện |

---

## Tóm tắt Đánh giá Thiết kế (Design Review Summary)

### Tính Đầy đủ và Nhất quán
- Thiết kế phân tách dứt khoát giữa Xác thực qua Cognito và Cấp quyền qua Identity DB; xác lập ranh giới cách ly dữ liệu đa tenant và đa chi nhánh rõ ràng.
- Bao quát toàn bộ nghiệp vụ P0: quản trị tenant, chi nhánh, vòng đời nhân viên, phân quyền RBAC và giữ cho tính năng mã PIN ở P1 nằm trong phạm vi kiểm soát an toàn.
- Các cảnh báo còn lại chủ yếu là các biện pháp gia cố vận hành và tích hợp kỹ thuật, không ảnh hưởng đến cấu trúc Bounded Context đã định hình.

---

## Các Cảnh báo Kỹ thuật Cần Lưu ý

| # | Hạng mục | Phát hiện | Khuyến nghị hành động |
|---|---|---|---|
| 1 | Đồng bộ với hợp đồng cha | Hợp đồng tổng thể POS Platform từng mô tả các trường JWT như `tenant_memberships`, `roles` là bắt buộc, trong khi module này quy định quyền hạn chỉ được lấy từ database. | Cập nhật lại diễn đạt trong hợp đồng chung để khẳng định: các trường trong JWT chỉ là gợi ý hiển thị cho UI, quyền hạn nghiệp vụ thực tế luôn được phân giải từ Identity DB. |
| 2 | Xác thực endpoint Bootstrap | Endpoint `POST /api/v1/tenants` nằm ở đường dẫn public nhưng chỉ dành cho Platform Admin. | Chọn một cơ chế triển khai cụ thể: xác thực qua Cognito JWT của Platform Admin với quyền `platform:tenant:create`, hoặc chuyển hẳn sang đường dẫn nội bộ `/internal/v1`. |
| 3 | Phụ thuộc vào phân quyền trực tuyến | Việc kiểm tra quyền trực tiếp tại Identity DB cho mỗi request giúp thu hồi quyền tức thì, nhưng biến Identity Access thành điểm thắt cổ chai về độ trễ và tính sẵn sàng. | Chạy bài test tải cho resolver, cấu hình connection pool hợp lý, thiết lập timeout, circuit breaker và cảnh báo lỗi; tuyệt đối không fallback sang tin tưởng claim của client. |
| 4 | Chứng minh thiết bị P1 | Hợp đồng có trường `deviceProof` nhưng chưa chốt giải pháp lưu khóa bảo mật trên trình duyệt. | Hoàn thiện quy trình kích hoạt và chứng minh thiết bị trước khi bật cờ tính năng P1; không để ảnh hưởng tiến độ nghiệm thu P0. |
| 5 | Tự động hóa hợp đồng máy đọc | Hợp đồng API và sự kiện mới được viết dưới dạng tài liệu Markdown, chưa có file OpenAPI / AsyncAPI định dạng JSON/YAML. | Sinh các file đặc tả OpenAPI 3.1 và AsyncAPI/JSON Schema chính thức để đưa vào quy trình kiểm thử tự động trên CI. |

---

## Tóm tắt Kiến trúc Module (Architecture Summary)

- **Đơn vị triển khai:** 1 Spring Boot microservice `identity-access` độc lập với cấu trúc mô-đun hóa nội bộ.
- **Thành phần cốt lõi:** API Adapters, JWT Validator, Authorization Context Resolver, Mô-đun Quản trị Tenant/Chi nhánh, Mô-đun Quản lý Thành viên, Cognito Adapter, Mô-đun Thiết bị/Mã PIN P1, Outbox Publisher, Audit Event Publisher, PostgreSQL DB và Telemetry.
- **Cơ sở dữ liệu:** 1 cơ sở dữ liệu PostgreSQL/Aurora độc lập do chính service sở hữu.
- **Bề mặt API:** 23 Endpoint API công khai và 3 Endpoint API nội bộ phục vụ giao tiếp giữa các service.
- **Hợp đồng sự kiện:** 16 loại sự kiện Domain Event có đánh số phiên bản.
- **Cơ chế phân quyền chính:** Xác thực chữ ký Cognito JWT → Phân giải quyền hạn trực tuyến từ Identity DB → Áp dụng điều kiện lọc vào repository của service đích.

---

## Lộ trình Triển khai Chi tiết (Implementation Roadmap)

### Phase 1 — Khởi tạo Khung Service và Ranh giới Bảo mật
- Tạo cấu trúc dự án Spring Boot, Java 21 và các package phân lớp.
- Cấu hình xác thực Cognito JWT/JWK và phân quyền Workload Identity nội bộ.
- Xây dựng middleware xử lý correlation ID, chuẩn hóa envelope thành công/lỗi và che giấu dữ liệu PII.
- Kết nối PostgreSQL, thiết lập Flyway migrations V1–V3, Testcontainers và nạp seed data demo.
- Triển khai các lớp Repository với điều kiện lọc tenant/branch bắt buộc theo nguyên tắc deny-by-default.

### Phase 2 — Nghiệp vụ Tenant, Chi nhánh và Nhân sự P0
- Triển khai endpoint bootstrap tenant và quy tắc bảo vệ Owner cuối cùng (Last-Owner Invariant).
- Quản trị chi nhánh: tạo mới, cập nhật, lưu trữ mềm với khóa lạc quan (`version`).
- Quản trị thành viên: mời nhân viên, phân vai trò, gán chi nhánh, đình chỉ và thu hồi quyền.
- Xây dựng endpoint `/api/v1/me` và endpoint phân quyền nội bộ `/internal/v1/authorization-decisions`.
- Kiểm tra tính sở hữu chặt chẽ tại Controller, Application Service và Repository.

### Phase 3 — Lời mời, Kiểm toán và Tích hợp Sự kiện
- Triển khai Cognito Invitation Adapter và worker xử lý gửi email kích hoạt có cơ chế thử lại.
- Thiết lập Flyway V4–V5, lưu trữ bảng `idempotency_records` và bảng `outbox_events`.
- Xuất bản các sự kiện thay đổi phân quyền lên AWS EventBridge / Hàng đợi SQS.
- Tích hợp phát tán sự kiện kiểm toán đã che mờ thông tin nhạy cảm sang Audit Service.
- Sinh file OpenAPI/AsyncAPI chính thức và chạy kiểm thử hợp đồng trên CI.

### Phase 4 — Ghép nối Phân quyền với các Service Nghiệp vụ khác
- Xây dựng thư viện client dùng chung để các service khác gọi sang kiểm tra quyền nội bộ.
- Tích hợp thử nghiệm với lát cắt dọc đầu tiên (khuyến nghị: Shift Service hoặc Order Service).
- Bổ sung bộ test an ninh toàn diện: kiểm thử IDOR xuyên tenant, truy cập trái phép xuyên chi nhánh, leo thang đặc quyền và dùng lại JWT sau khi bị thu hồi quyền.
- Đo tải cho endpoint phân quyền trực tuyến; tinh chỉnh connection pool, timeout và circuit breaker.

### Phase 5 — Đổi Thu ngân Nhanh bằng Mã PIN (Giai đoạn P1)
- Hoàn thiện giải pháp xác thực và đăng ký thiết bị POS.
- Kích hoạt Flyway V6 và các mô-đun quản lý thiết bị / mã PIN sau cờ tính năng (Feature Flag).
- Thuật toán băm mật mã Argon2id/bcrypt cho mã PIN, giới hạn tần suất nhập sai và khóa tạm thời.
- Tạo ngữ cảnh thu ngân tạm thời có thời hạn ngắn và xác thực phía máy chủ.

### Phase 6 — Gia cố Vận hành và Sẵn sàng Demo
- Triển khai trên AWS ECS Fargate, tích hợp AWS Secrets Manager và cấu hình auto-scaling.
- Xây dựng Dashboard giám sát và cảnh báo lỗi trên AWS CloudWatch / OpenTelemetry.
- Diễn tập quy trình migration cơ sở dữ liệu, sao lưu/phục hồi và phát lại thông điệp từ DLQ.
- Chạy k6 kiểm thử tải, kịch bản hỗn loạn (chaos) và giả lập sự cố đứt mạng Cognito/Email.
- Viết tài liệu hướng dẫn vận hành (Runbook) cho các trường hợp xử lý sự cố.
