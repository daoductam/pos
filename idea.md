Kiến trúc

★ DDD, bounded context: căn cứ để chia service
★ Database per service, API Gateway
★ Event-driven architecture (EventBridge + SQS)
CQRS: tách read model cho báo cáo, tránh query chéo service

Nhất quán dữ liệu phân tán

★ Saga (orchestration bằng Step Functions) và compensation
★ Transactional outbox
★ Idempotency (API, consumer, IPN), cùng retry + exponential backoff, DLQ
Eventual consistency: giải thích được khi nào dữ liệu “chưa khớp ngay”

Chịu lỗi

Timeout, circuit breaker, rate limiting, bulkhead

Nghiệp vụ

★ Payment state machine, verify chữ ký IPN, job đối soát
★ Loyalty dạng ledger, điểm hết hạn theo FIFO
★ Giữ chỗ tồn kho, optimistic hoặc pessimistic locking
Rule engine cho khuyến mãi
Xử lý tiền bằng số nguyên, quy tắc làm tròn VAT

Bảo mật

★ OAuth2/OIDC + JWT (Cognito), RBAC giới hạn theo chi nhánh
HMAC checksum, Secrets Manager, audit log

DevOps & hạ tầng

★ Docker, ECS Fargate, IaC (Terraform hoặc CDK), CI/CD
Auto scaling, blue/green deploy

Observability

Correlation ID, distributed tracing (OpenTelemetry + X-Ray), structured logging
Metrics và alerting trên CloudWatch

Testing

Unit test, integration test 
★ Load test (k6), chaos test

---------------------------------------------------------------------------------------------

Mức ưu tiên: P0 = bắt buộc cho MVP và demo · P1 = nên có · P2 = điểm cộng nếu còn thời gian

Actor

Chủ chuỗi · Quản lý chi nhánh · Thu ngân · Bếp · Khách hàng

Module và tính năng

1. Tài khoản & phân quyền (Cognito)

P0: Đăng nhập, phân quyền RBAC (chủ / quản lý / thu ngân / bếp), giới hạn dữ liệu theo chi nhánh
P0: Quản lý chi nhánh, nhân viên
P1: Mã PIN để đổi thu ngân nhanh trên cùng một máy POS

2. Menu (Catalog service)

P0: Danh mục, món, biến thể (size), topping/modifier, bật/tắt món
P1: Combo, giá riêng theo chi nhánh
P1: Mô tả và tag món (cay, lạnh, ăn sáng…), dùng làm dữ liệu cho AI cold start

3. Bán hàng / Order (Order service)

P0: Tạo đơn tại quầy, mang đi hoặc tại bàn, ghi chú món
P0: Sửa, hủy món (hủy cần quyền quản lý và ghi audit log)
P0: Saga checkout: giữ nguyên liệu → thanh toán → hoàn tất hoặc bù trừ
P1: Sơ đồ bàn, tách và gộp hóa đơn, giữ đơn
P1: Màn hình bếp (KDS) nhận món realtime qua WebSocket
P2: Khách tự order bằng QR tại bàn

4. Thanh toán (Payment service)

P0: Tiền mặt (tự tính tiền thừa), QR động VNPAY sandbox
P0: Payment state machine, xử lý IPN có idempotency, xác thực checksum
P0: Job đối soát giao dịch treo
P1: Kết hợp nhiều phương thức trong một đơn (split tender), hoàn tiền
P1: Lịch sử giao dịch, báo cáo đối soát

5. Khách hàng thân thiết & khuyến mãi (Loyalty service)

P0: Hồ sơ khách theo số điện thoại, tích điểm theo sổ cái (ledger), đổi điểm
P0: Thu hồi điểm khi hủy hoặc hoàn đơn
P1: Hạng thành viên (Bạc / Vàng / Kim cương), điểm hết hạn theo FIFO
P1: Voucher; rule engine khuyến mãi (giảm %, mua X tặng Y, khung giờ vàng) và xử lý xung đột khi nhiều khuyến mãi cùng áp dụng
P2: Cổng thông tin cho khách xem điểm và voucher

6. Kho nguyên liệu (Inventory service)

P0: Nguyên liệu và định lượng (công thức món), bán một ly trà sữa thì tự trừ trà, sữa, trân châu
P0: Giữ chỗ và trừ kho trong saga
P1: Nhập kho, kiểm kho, cảnh báo tồn thấp
P2: Dự báo lượng nhập hàng

7. Ca làm việc

P0: Mở ca (tiền đầu ca), đóng ca
P1: Đếm tiền đối soát, báo chênh lệch tiền mặt

8. Gợi ý món / upsell (AI service, ML)

P0: Gợi ý theo giỏ hàng bằng association rules (gà rán → Coca), áp business rules (bỏ món hết hàng hoặc đã có trong giỏ)
P0: Ghi log SuggestionShown và SuggestionAccepted
P1: Gợi ý cá nhân hóa bằng ALS khi khách nhập số điện thoại (nối với Loyalty)
P1: Job train lại hằng đêm
P2: Cold start cho món mới bằng DeepSeek; gợi ý theo ngữ cảnh (giờ, thời tiết)
9. Trợ lý AI cho chủ quán (AI service, LLM)

P1: Hỏi đáp báo cáo bằng ngôn ngữ tự nhiên, dùng DeepSeek + function calling, có guardrail theo chi nhánh và che dữ liệu cá nhân
P2: Tự động tóm tắt cuối ngày (“Hôm nay doanh thu giảm 12%, do…”)

10. Báo cáo & dashboard (read model hoặc Athena)

P0: Doanh thu theo ngày và chi nhánh, món bán chạy
P1: Doanh thu theo khung giờ, theo nhân viên; hiệu quả khuyến mãi
P1: Hiệu quả gợi ý: tỷ lệ chấp nhận, mức tăng giá trị đơn trung bình (AOV)

11. Giám sát & chống thất thoát

P0: Audit log (hủy món, giảm giá, mở két)
P2: Phát hiện bất thường hành vi nhân viên

12. Tích hợp mở rộng

P2: Hóa đơn điện tử khởi tạo từ máy tính tiền, gửi hóa đơn qua email hoặc Zalo
Kịch bản demo (chạy được toàn bộ P0)
Thu ngân mở ca → khách gọi gà rán → hệ thống gợi ý Coca → khách đồng ý thêm.
Nhập số điện thoại → hệ thống nhận ra khách thân thiết → gợi ý thêm món khách hay gọi.
Thanh toán bằng QR VNPAY → IPN về → đơn chuyển sang PAID → cộng điểm + trừ nguyên liệu.
Demo lỗi: tắt Payment service → saga nhả nguyên liệu đã giữ, xem toàn bộ trace trên X-Ray.
Chủ quán hỏi trợ lý AI: “Tuần này gợi ý Coca giúp tăng bao nhiêu doanh thu?”