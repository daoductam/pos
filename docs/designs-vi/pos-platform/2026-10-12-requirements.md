# Yêu cầu Hệ thống: POS Platform (Nền tảng POS Đa Tenant)

## Nguồn tham chiếu

- **Tầm nhìn (Vision):** [`2026-10-12-vision.md`](./2026-10-12-vision.md)
- **Đánh giá Tầm nhìn (Vision Review):** [`2026-10-12-vision-review.md`](./2026-10-12-vision-review.md)
- **Ý tưởng ban đầu (Idea):** [`idea.md`](../../../idea.md)

---

## Phạm vi và Mức độ Ưu tiên

- **P0:** Bắt buộc phải có cho MVP/bản demo; bắt buộc phải có bài kiểm thử nghiệm thu (acceptance tests) tự động.
- **P1:** Kiến trúc bắt buộc phải để sẵn ranh giới (boundary) và điểm mở rộng phù hợp, nhưng chưa bắt buộc triển khai hoàn chỉnh trong MVP.
- **P2:** Nằm ngoài phạm vi thiết kế chi tiết của giai đoạn MVP (dành cho các giai đoạn phát triển tiếp theo).
- Các chỉ số định lượng phi chức năng (NFR) trong tài liệu này là **Mục tiêu kỹ thuật của bản demo (Demo Targets)**, không phải là cam kết dịch vụ cho môi trường sản xuất (Production SLA).

---

## Yêu cầu Chức năng (Functional Requirements)

### Danh sách Ca sử dụng (Use Cases)

| Mã | Ưu tiên | Tác nhân | Hành động | Phản hồi của Hệ thống |
|---|---|---|---|---|
| **UC-01** | P0 | Chủ chuỗi, Quản lý, Thu ngân, Bếp | Đăng nhập hệ thống qua Cognito | Xác thực OIDC/JWT, khởi tạo ngữ cảnh phân quyền gồm thông tin user, tenantId, branchId và danh sách quyền hợp lệ. |
| **UC-02** | P0 | Chủ chuỗi | Tạo và quản trị tenant, chi nhánh, tài khoản nhân viên | Lưu trữ thông tin thành viên (membership), vai trò (role), phạm vi chi nhánh được phép truy cập và ghi nhận log kiểm toán thao tác quản trị. |
| **UC-03** | P0 | Người dùng nghiệp vụ | Truy xuất dữ liệu theo quyền và chi nhánh | Chỉ trả về dữ liệu thuộc tenant và chi nhánh mà người dùng được phân quyền; mọi truy cập vượt quyền bị từ chối với mã lỗi `403` và kích hoạt security audit event. |
| **UC-04** | P0 | Chủ chuỗi, Quản lý | Quản lý danh mục, món ăn, size và topping | Thêm mới, cập nhật thông tin, bật/tắt món đang bán; mọi thay đổi có hiệu lực ngay lập tức theo phiên bản (version) hoặc mốc thời gian (timestamp). |
| **UC-05** | P0 | Thu ngân | Mở ca làm việc và khai báo số tiền đầu ca | Tạo ca làm việc mới cho chi nhánh và nhân viên; chặn không cho mở ca trùng lặp nếu ca trước chưa được đóng. |
| **UC-06** | P0 | Thu ngân | Tạo đơn hàng (tại quầy, mang đi hoặc tại bàn) | Tạo bản nháp đơn hàng (order draft) gắn với chi nhánh, loại hình phục vụ, các món gọi kèm món phụ (modifier) và ghi chú chế biến. |
| **UC-07** | P0 | Thu ngân | Chỉnh sửa hoặc hủy món trước khi thanh toán | Kiểm tra trạng thái đơn và quyền hạn; việc hủy món nhạy cảm yêu cầu phê duyệt của quản lý và phải lưu vết kiểm toán đầy đủ. |
| **UC-08** | P0 | Hệ thống | Tự động sinh gợi ý món kèm dựa trên giỏ hàng | Áp dụng luật kết hợp (association rules), tự động loại trừ các món đã hết hàng hoặc đã có sẵn trong giỏ; trả về danh sách gợi ý và phát sự kiện `SuggestionShown`. |
| **UC-09** | P0 | Thu ngân / Khách hàng | Đồng ý chọn một món gợi ý | Thêm món được chọn vào đơn hàng và phát sự kiện `SuggestionAccepted` gắn kèm mã định danh giỏ hàng và đơn hàng để đo lường hiệu quả. |
| **UC-10** | P0 | Thu ngân | Tra cứu thông tin khách hàng qua số điện thoại | Tìm kiếm hồ sơ thành viên trong phạm vi tenant hiện tại, hiển thị thông tin đã được che chắn (masking) để bảo vệ PII và gắn khách hàng vào đơn. |
| **UC-11** | P0 | Thu ngân | Thanh toán bằng tiền mặt | Tính toán tổng tiền, tiền khách đưa và tiền thừa bằng số nguyên (VND); tạo giao dịch thanh toán và khởi động luồng Checkout Saga. |
| **UC-12** | P0 | Thu ngân / Khách hàng | Thanh toán qua mã QR động VNPAY sandbox | Tạo yêu cầu thanh toán ở trạng thái chờ và hiển thị mã QR; tiếp nhận webhook/IPN, xác thực chữ ký số HMAC và cập nhật trạng thái thanh toán theo cơ chế idempotency. |
| **UC-13** | P0 | Hệ thống | Điều phối quy trình Checkout qua Saga | Tuần tự thực hiện: Giữ chỗ nguyên liệu kho → Xử lý/chờ thanh toán → Chốt đơn hàng thành công; nếu bước sau thất bại thì tự động kích hoạt bồi hoàn (compensation) cho các bước trước. |
| **UC-14** | P0 | Hệ thống | Xử lý khi thanh toán thất bại hoặc quá hạn (timeout) | Không chuyển trạng thái đơn sang `PAID`, tự động giải phóng lượng nguyên liệu kho đã giữ chỗ, lưu nguyên nhân lỗi và phát sự kiện thông báo kết quả. |
| **UC-15** | P0 | Hệ thống | Tích điểm hoặc thu hồi điểm khách hàng thân thiết | Chỉ cộng điểm sau khi đơn hàng được thanh toán thành công; việc hủy/hoàn đơn sẽ tạo một bản ghi đối ứng trên sổ cái (ledger), không chỉnh sửa lịch sử cũ. |
| **UC-16** | P0 | Hệ thống | Tự động đối soát giao dịch thanh toán bị treo | Chạy job định kỳ quét các giao dịch ở trạng thái chờ quá thời gian quy định; gọi cổng đối soát hoặc đánh dấu cần xử lý thủ công; retry an toàn và đẩy vào DLQ khi quá hạn. |
| **UC-17** | P0 | Thu ngân, Quản lý | Đóng ca làm việc và chốt tiền mặt | Chốt ca, tổng hợp doanh thu theo từng phương thức thanh toán và kiểm kê tiền mặt thực tế; ca đã đóng không thể tiếp nhận thêm đơn hàng mới. |
| **UC-18** | P0 | Chủ chuỗi, Quản lý | Xem báo cáo doanh thu theo ngày, chi nhánh và món bán chạy | Truy vấn từ mô hình đọc CQRS (Reporting Read Model), hiển thị rõ mốc thời gian cập nhật gần nhất (`lastUpdatedAt`) và độ trễ dữ liệu eventual consistency. |
| **UC-19** | P0 | Quản lý, Kiểm toán viên | Tra cứu nhật ký kiểm toán (Audit Log) | Truy vấn lịch sử các hành động nhạy cảm lọc theo tenant, chi nhánh, người thực hiện, tài nguyên tác động, thời gian và mã `correlationId`. |
| **UC-20** | P0 | Kỹ sư phát triển / Vận hành | Truy vết một giao dịch phân tán từ đầu đến cuối | Sử dụng mã `correlationId` duy nhất để liên kết từ log API, sự kiện domain, bước chuyển trạng thái, chỉ số đo lường đến trace đồ thị trên OpenTelemetry. |
| **UC-21** | P0 | Kỹ sư phát triển / Vận hành | Giả lập tắt hoặc khởi động lại Payment service trong lúc demo | Hệ thống Transactional Outbox, cơ chế Retry và DLQ đảm bảo không mất mát thông báo; quy trình Saga kết thúc ở trạng thái nhất quán và trace được luồng bồi hoàn. |
| **UC-22** | P0 | Hệ thống | Chạy bài kiểm thử phân quyền tiêu cực (Negative Authorization Tests) | Tự động từ chối mọi nỗ lực khai thác IDOR hoặc đọc/ghi dữ liệu xuyên tenant/chi nhánh; thông báo lỗi trả về không làm lộ thông tin cấu trúc nội bộ. |

---

### Ranh giới Hàng đợi Tính năng P1 / P2

- **Giai đoạn P1:** Nhập mã PIN đổi nhanh tài khoản thu ngân trên máy POS; hỗ trợ bán combo và thiết lập bảng giá riêng theo từng chi nhánh; sơ đồ bàn ăn, tách bàn, gộp bàn và tạm giữ đơn; màn hình bếp KDS thời gian thực qua WebSocket; thanh toán kết hợp nhiều phương thức (split tender) và hoàn tiền; chính sách thăng hạng thành viên, điểm hết hạn theo FIFO; bộ quy tắc khuyến mãi (voucher rule engine); nghiệp vụ nhập kho, kiểm kê kho; thuật toán gợi ý ALS và huấn luyện lại mô hình; trợ lý AI hỏi đáp báo cáo doanh thu.
- **Giai đoạn P2:** Khách hàng tự gọi món bằng mã QR tại bàn; kỹ thuật giải quyết bài toán gợi ý lúc mới khởi tạo (cold start) bằng mô hình ngôn ngữ lớn (LLM); phát hiện thất thoát và hành vi gian lận bất thường; dự báo nhu cầu nhập nguyên vật liệu; gửi báo cáo tóm tắt hàng ngày; xuất hóa đơn điện tử tự động và gửi tin nhắn qua Email/Zalo.
- **Nguyên tắc quản trị thay đổi:** Các tính năng P1/P2 không được phép làm phá vỡ các hợp đồng (contracts) đã được chốt ở P0; mọi mở rộng phải áp dụng cơ chế đánh số phiên bản (versioning) tương thích ngược.

---

### Quy tắc Nghiệp vụ Cốt lõi (Business Rules)

| Mã | Ưu tiên | Quy tắc nghiệp vụ | Rationale (Cơ sở lý do) |
|---|---|---|---|
| **BR-01** | P0 | Ngữ cảnh tenant và chi nhánh phải được trích xuất từ JWT token/membership đã được xác thực phía server; các ID do client tự gửi lên trong URL/body chỉ là tham số đầu vào cần kiểm tra tính sở hữu, tuyệt đối không được coi là bằng chứng về quyền hạn. | Ngăn chặn triệt để lỗ hổng IDOR và nguy cơ truy cập trái phép xuyên tenant. |
| **BR-02** | P0 | Chủ chuỗi (`OWNER`) có toàn quyền trên toàn bộ các chi nhánh thuộc tenant; các vai trò `MANAGER`, `CASHIER`, `KITCHEN` chỉ được thao tác trong phạm vi các chi nhánh cụ thể được chỉ định. | Đảm bảo tính phân quyền chặt chẽ theo mô hình RBAC và phù hợp phạm vi demo. |
| **BR-03** | P0 | Mọi API command có tác dụng phụ (side-effect) phải đính kèm `Idempotency-Key`; các tiến trình consumer xử lý sự kiện và webhook IPN phải thực hiện khử trùng lặp (deduplicate) dựa trên khóa nghiệp vụ. | Đảm bảo các lần gửi lại (retry) không làm sinh thêm đơn hàng, cộng trùng điểm hoặc trừ kho lặp lại. |
| **BR-04** | P0 | Toàn bộ các giá trị tiền tệ phải được lưu trữ dưới dạng số nguyên (integer) theo đơn vị tiền tệ nhỏ nhất (VND là đơn vị nguyên); cấm sử dụng số thực dấu phẩy động (float/double). Quy tắc làm tròn thuế VAT phải được quy định xác định (deterministic) thống nhất giữa Order và Payment. | Tránh triệt để sai lệch số học trong tính toán tài chính. |
| **BR-05** | P0 | Một đơn hàng chỉ được phép chuyển trạng thái sang `PAID` sau khi cổng thanh toán xác nhận thành công và quy trình Saga hoàn tất đầy đủ các bước bắt buộc. | Không ghi nhận doanh thu ảo cho các giao dịch chưa thanh toán trọn vẹn. |
| **BR-06** | P0 | Quy trình Checkout Saga phải thực hiện tuần tự: Kiểm tra tính hợp lệ của đơn hàng → Giữ chỗ nguyên liệu kho (Reserve) → Khởi tạo/xác nhận thanh toán → Chốt đơn hàng thành công → Phát tán sự kiện cho các service cấp dưới. | Chuẩn hóa nguồn sự thật và các bước chuyển đổi trạng thái của luồng chính. |
| **BR-07** | P0 | Nếu việc thanh toán bị lỗi, quá thời gian chờ hoặc bị người dùng hủy bỏ, bước bù trừ (compensation) bắt buộc phải giải phóng lượng nguyên liệu đã giữ chỗ; hàm bù trừ phải có tính lũy kế (idempotent). | Không được giam giữ tồn kho nguyên vật liệu vô thời hạn. |
| **BR-08** | P0 | Bản ghi giữ chỗ nguyên liệu kho (Reservation) sẽ tự động giải phóng khi quy trình checkout thất bại/bị hủy hoặc quá thời hạn cấu hình; thời gian hết hạn mặc định trong bản demo là 15 phút. | Thiết lập vòng đời rõ ràng cho việc chiếm giữ nguyên vật liệu. |
| **BR-09** | P0 | Webhook IPN từ cổng thanh toán chỉ được chấp nhận sau khi xác thực thành công chữ ký số HMAC và đối chiếu chính xác mã cửa hàng, mã đơn hàng và mã giao dịch. | Chống giả mạo callback hoặc callback sai đơn hàng từ bên ngoài. |
| **BR-10** | P0 | Máy trạng thái thanh toán (Payment State Machine) tối thiểu gồm: `PENDING`, `PAID`, `FAILED`, `EXPIRED`, `CANCELLED`; các bước chuyển trạng thái không hợp lệ sẽ bị từ chối và ghi log cảnh báo. | Ngăn chặn xung đột và mâu thuẫn trạng thái giao dịch tiền tệ. |
| **BR-11** | P0 | Dịch vụ Loyalty được thiết kế theo mô hình sổ cái bất biến (append-only ledger); số dư điểm khả dụng là một mô hình chiếu (projection) được tính toán từ sổ cái; việc thu hồi điểm phải tạo một bản ghi đối ứng âm gắn với giao dịch gốc, không được phép sửa đổi các bản ghi cũ. | Đảm bảo khả năng truy vết và kiểm toán chính xác lịch sử cộng/trừ điểm. |
| **BR-12** | P0 | Điểm thưởng thành viên chỉ được tích lũy khi nhận được sự kiện đơn hàng đã thanh toán thành công; tỷ lệ quy đổi điểm được cấu hình theo từng tenant và được nạp sẵn dữ liệu mẫu cho demo. | Tránh tích điểm nhầm cho các đơn hàng chưa thanh toán. |
| **BR-13** | P0 | Hệ thống gợi ý món (Recommendation) không được đề xuất các món đã có sẵn trong giỏ hàng, các món đang bị tắt hoặc các món đã hết nguyên liệu tồn kho; mỗi lần gợi ý hiển thị và mỗi lần khách chấp nhận phải được ghi nhận thành các sự kiện phân tích độc lập. | Đảm bảo gợi ý có tính thực tế và đo lường được tỷ lệ chuyển đổi. |
| **BR-14** | P0 | Việc hủy món trên đơn hàng trước khi thanh toán phải được kiểm tra quyền hạn; các hành vi hủy món nhạy cảm phải ghi nhận danh tính người hủy, lý do, trạng thái trước/sau và mã `correlationId`. | Ngăn ngừa và kiểm soát rủi ro thất thoát trong nhà hàng. |
| **BR-15** | P0 | Đơn hàng đã ở trạng thái `PAID` chưa hỗ trợ quy trình hoàn tiền tự động đầy đủ trong giai đoạn MVP (hoàn tiền là tính năng P1), nhưng hợp đồng đối ứng sổ cái loyalty phải được thiết kế sẵn sàng cho việc mở rộng này. | Giới hạn phạm vi nhưng bảo toàn tính linh hoạt cho kiến trúc tương lai. |
| **BR-16** | P0 | Mô hình báo cáo (Reporting read model) hoạt động theo nguyên tắc nhất quán sau cùng (Eventual Consistency); các API truy vấn báo cáo bắt buộc phải trả về trường `lastUpdatedAt` để người dùng nhận biết độ trễ của dữ liệu. | Tránh việc người dùng hiểu nhầm rằng hệ thống bị treo hoặc mất số liệu. |
| **BR-17** | P0 | Bản ghi Transactional Outbox phải được lưu trữ trong cùng một cơ sở dữ liệu transaction cục bộ với nghiệp vụ chính; sự kiện chỉ được đánh dấu là `PUBLISHED` sau khi Message Broker xác nhận tiếp nhận thành công. | Giải quyết triệt để bài toán Dual-Write, không làm mất sự kiện giữa database và message broker. |
| **BR-18** | P0 | Cơ chế thử lại (Retry) khi gửi sự kiện hoặc gọi API phải áp dụng thuật toán lũy thừa (Exponential Backoff) có chặn trần; khi vượt quá số lần thử lại cho phép, thông điệp sẽ tự động chuyển vào Dead-Letter Queue (DLQ) kèm lý do lỗi. | Bảo vệ hệ thống khỏi hiện tượng nghẽn mạng do bão thử lại (retry storm) và giữ lại thông điệp lỗi để xử lý. |
| **BR-19** | P0 | Nhật ký kiểm toán (Audit Log) là bảng dữ liệu chỉ thêm mới (append-only), phân vùng theo tenant và tuyệt đối không lưu trữ các bí mật, token, chữ ký số thô hoặc thông tin thẻ nhạy cảm. | Đảm bảo tính an toàn dữ liệu và tuân thủ các chuẩn mực an ninh thông tin. |
| **BR-20** | P0 | Cấu trúc thông điệp sự kiện (Event Payload) bắt buộc phải chứa đầy đủ các trường: `eventId`, `eventType`, `eventVersion`, `occurredAt`, `tenantId`, `branchId`, `correlationId` và `producer`. | Cho phép khử trùng lặp thông điệp, điều hướng routing và truy vết phân tán xuyên suốt các service. |

---

## Yêu cầu Phi Chức năng (Non-Functional Requirements)

| Nhóm yêu cầu | Chỉ số / Ràng buộc kỹ thuật | Mục tiêu kiểm chứng Demo (Demo Target) |
|---|---|---|
| **Hiệu năng (Performance)** | Thời gian phản hồi API đồng bộ cốt lõi (không tính thời gian chờ nhà cung cấp bên thứ ba) | p95 ≤ 500 ms trong bài test tải demo thông thường. |
| **Hiệu năng (Performance)** | Thời gian truy vấn danh sách gợi ý món (Recommendation) | p95 ≤ 300 ms với bộ dữ liệu mẫu demo. |
| **Hiệu năng (Performance)** | Thời gian truy vấn báo cáo doanh thu ngày / top món bán chạy | p95 ≤ 2 giây cho các báo cáo tổng hợp. |
| **Tính nhất quán (Consistency)** | Độ trễ lan truyền sự kiện từ nghiệp vụ sang mô hình báo cáo và loyalty | 95% số lượng sự kiện được xử lý và phản ánh lên báo cáo trong vòng ≤ 30 giây ở điều kiện bình thường. |
| **Năng lực xử lý (Throughput)** | Tải xử lý của luồng đặt hàng và thanh toán (Checkout) | Chịu tải ổn định tối thiểu 10 lượt checkout/giây duy trì liên tục trong 5 phút bằng k6 mà không bị lỗi hay sinh tác dụng phụ trùng. |
| **Khả năng mở rộng (Scalability)** | Mở rộng quy mô dịch vụ (Horizontal Scaling) | Các core service được thiết kế phi trạng thái (stateless) ở tầng API, có thể scale độc lập; số lượng luồng của worker có thể điều chỉnh linh hoạt. |
| **Khả năng phục hồi (Resilience)** | Khả năng cô lập lỗi khi sự cố xảy ra | Khởi động lại hoặc sự cố sập một service bất kỳ không làm mất các transaction đã commit ở database hoặc các thông điệp đã nằm trong bảng Outbox. |
| **Phục hồi sau sự cố (Recovery)** | Thời gian tự khôi phục trạng thái nhất quán | Sau khi dịch vụ phụ thuộc được bật trở lại, cơ chế retry tự động và phát lại từ DLQ sẽ đưa toàn bộ dữ liệu về trạng thái hoàn tất trong vòng ≤ 5 phút. |
| **Bảo mật (Security)** | Xác thực danh tính (Authentication) | Xác thực OIDC/JWT qua Cognito; kiểm tra chặt chẽ chữ ký số, thời hạn hết hạn (`exp`), nhà phát hành (`iss`) và đối tượng nhận (`aud`). |
| **Bảo mật (Security)** | Phân quyền truy cập (Authorization) | Áp dụng RBAC kết hợp kiểm tra quyền sở hữu tenant/branch tại API, Service và Repository; có bộ kiểm thử tiêu cực chống BOLA/IDOR. |
| **Bảo mật (Security)** | Quản lý bí mật & Dữ liệu nhạy cảm | Toàn bộ mật khẩu, khóa bí mật lưu trong AWS Secrets Manager hoặc biến môi trường; số điện thoại và PII được che mờ (masking) trên giao diện và log. |
| **Bảo mật (Security)** | An toàn dữ liệu đầu vào (Input Safety) | Kiểm tra tính hợp lệ của payload bằng schema, sử dụng parameterized query chống SQL Injection, giới hạn dung lượng request và ẩn hoàn toàn stack trace khỏi client. |
| **Độ tin cậy (Reliability)** | Tính lũy kế (Idempotency) | Việc gửi lặp lại cùng một request, webhook IPN hay sự kiện domain tuyệt đối không sinh ra bản ghi đơn hàng, thanh toán hoặc số dư điểm trùng lặp. |
| **Khả năng quan sát (Observability)** | Truy vết giao dịch (Distributed Tracing) | Mã `correlationId` bắt buộc phải có trong mọi request HTTP và header sự kiện; xuất trace theo chuẩn W3C Trace Context sang AWS X-Ray. |
| **Kiểm thử tự động (Testing)** | Độ bao phủ kiểm thử đa tầng | Bao gồm đầy đủ các tầng kiểm thử: Unit Test, Integration Test, Contract Test, End-to-End Test, k6 Load Test, Chaos Test và Security Negative Test. |
| **Triển khai (Deployment)** | Tính độc lập và khả năng tái lập | Toàn bộ cụm dịch vụ chạy được cục bộ bằng Docker Compose; tài nguyên hạ tầng AWS được tự động hóa bằng Terraform (IaC). |

---

## Ma trận Chiến lược Kiểm thử và Nghiệm thu

| Tầng kiểm thử | Phạm vi và Nội dung bắt buộc kiểm tra |
|---|---|
| **Unit Test** | Kiểm tra logic máy trạng thái (state machines), thuật toán tính tiền/làm tròn VAT, logic giữ chỗ kho, sổ cái điểm loyalty, bộ lọc gợi ý món và chính sách phân quyền RBAC. |
| **Integration Test** | Kiểm thử tương tác cơ sở dữ liệu PostgreSQL thực tế, xác thực chữ ký Cognito JWT, adapter tính chữ ký HMAC của VNPAY, tiến trình đẩy Outbox và ranh giới Step Functions. |
| **Contract Test** | Kiểm tra tính tương thích của OpenAPI request/response/error schemas và các schema sự kiện AsyncAPI đối với metadata bắt buộc. |
| **End-to-End Test** | Chạy toàn bộ luồng P0 từ đầu đến cuối trên nhiều tenant/chi nhánh khác nhau, bao gồm cả phương thức thanh toán tiền mặt và quét QR VNPAY sandbox. |
| **Failure Injection Test** | Giả lập tình huống dịch vụ Payment bị ngắt kết nối, webhook IPN bị gửi lặp lại hoặc gửi sai thứ tự, kho không đủ nguyên liệu, message broker bị chậm trễ và service bị sập giữa chừng khi Saga đang chạy. |
| **Security Negative Test** | Giả lập tấn công khai thác IDOR, cố tình truy cập trái phép dữ liệu của tenant khác hoặc chi nhánh khác, leo thang đặc quyền (role escalation), giả mạo JWT token và gửi lại callback cũ. |
| **Load Test** | Sử dụng k6 chạy kịch bản mô phỏng áp lực đặt hàng, gợi ý món và truy vấn báo cáo liên tục để đánh giá ngưỡng p95 và kiểm tra tính toàn vẹn dữ liệu. |
| **Chaos Test** | Tắt đột ngột một container service hoặc database trong lúc luồng thanh toán đang thực thi và quan sát khả năng phục hồi dữ liệu cũng như trace bồi hoàn sau khi khởi động lại. |
