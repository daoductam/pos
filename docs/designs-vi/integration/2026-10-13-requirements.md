Started at: 2026/10/13 00:00:00
Finished at: 2026/10/13 00:00:00
Total time: 0 minutes
---

# Module 12 — Tích hợp mở rộng: Yêu cầu nghiệp vụ (Requirements)

**Ngày:** 2026-10-13  
**Trạng thái:** Approved with warnings — người dùng đã phê duyệt sau đợt đánh giá Requirements review  
**Tầm nhìn:** [Integration & Demo Orchestration Vision](./2026-10-13-vision.md)  
**Đánh giá tầm nhìn:** [Integration & Demo Orchestration Vision Review](./2026-10-13-vision-review.md)  
**Các module cha liên quan:** POS Platform, Order, Payment, Inventory, Loyalty, Recommendation, Reporting, AI Assistant, Audit

## 1. Mục đích và ranh giới hệ thống (Purpose & Boundaries)

Dịch vụ Integration cung cấp một ranh giới chuyên biệt phục vụ việc tích hợp các nhà cung cấp bên ngoài (external providers) và phân hệ điều khiển demo (demo control plane). Dịch vụ này tuyệt đối không sở hữu trạng thái nghiệp vụ của các domain POS khác và không được phép phát ra các sự kiện thanh toán, doanh thu, tích điểm hay tồn kho thay thế cho các dịch vụ nguồn.

### Trong phạm vi (In Scope)

- Vòng đời hóa đơn kỹ thuật/sandbox P2 khởi tạo từ sự kiện chuẩn mực `OrderPaid.v1`.
- Ghi nhận kênh nhận hóa đơn mong muốn của khách hàng, xác thực thông tin liên hệ và lưu bản chụp sự đồng ý (consent snapshot) trước khi thanh toán.
- Adapter hóa đơn trung lập với nhà cung cấp (provider-neutral adapter), quản lý các lần thử gọi nhà cung cấp, tính lũy quyền, cơ chế thử lại/hàng đợi thư chết (DLQ) và mã trạng thái an toàn.
- Chuyển phát hóa đơn bất đồng bộ qua Email/Zalo sau khi hóa đơn được phát hành, áp dụng cơ chế thử lại/hết hạn/trạng thái riêng theo từng kênh.
- Trình điều phối kịch bản demo (demo scenario runner) chạy trên các dịch vụ thật, cơ chế nạp/đặt lại dữ liệu hạt giống, kiểm tra điều kiện tiên quyết, ghi nhận điểm kiểm tra (checkpoints), tiêm lỗi thử nghiệm trên môi trường non-production và liên kết dấu vết AWS X-Ray.
- Luồng nghiệm thu/giao ước xuyên suốt kết nối Order, Payment, Inventory, Loyalty, Recommendation, Reporting và AI Assistant.
- Khả năng quan sát của Integration, che giấu dữ liệu nhạy cảm, tham chiếu kiểm toán truy cập và các tài liệu hướng dẫn vận hành (runbooks).

### Ngoài phạm vi (Out of Scope)

- Phát hành hóa đơn thuế chính thức (production), chữ ký số, mã xác thực cơ quan thuế, điều chỉnh/hủy hóa đơn theo quy định pháp lý của nhà cung cấp.
- Thay thế Checkout Saga do Order/Step Functions điều phối hoặc logic xác thực IPN/checksum của Payment.
- Sở hữu trạng thái nghiệp vụ cốt lõi của Order, Payment, Inventory, Loyalty, Recommendation, Reporting hay AI.
- Tự ý tính toán doanh thu hoặc mức tăng trưởng (uplift) từ dữ liệu đơn hàng/thanh toán thô.
- Tự động gửi email/Zalo từ số điện thoại Loyalty khi khách hàng chưa xác nhận đồng ý tường minh.
- Các route tiêm lỗi thử nghiệm hoặc nạp/đặt lại dữ liệu hạt giống trên môi trường Production.

## 2. Tác nhân, phân quyền và ranh giới tin cậy (Actors, Authorization & Trust Boundaries)

### 2.1 Tác nhân (Actors)

| Tác nhân | Mục đích sử dụng | Mức độ tin cậy |
|---|---|---|
| Thu ngân (Cashier) | Ghi nhận lựa chọn kênh nhận hóa đơn cho đơn hàng hiện tại; xem trạng thái hóa đơn an toàn | Người dùng Cognito, giới hạn phạm vi chi nhánh/POS |
| Quản lý (Manager) | Theo dõi trạng thái hóa đơn/chuyển phát; gửi lại hóa đơn cho các chi nhánh được phân công | Người dùng Cognito, phạm vi các chi nhánh được giao |
| Chủ quán (Owner) | Xem hóa đơn/chuyển phát toàn chuỗi hoặc theo nhóm chi nhánh; thực hiện gửi lại/xuất báo cáo đã duyệt | Người dùng Cognito, phạm vi toàn tenant hoặc nhóm chi nhánh |
| Vận hành viên Demo | Khởi chạy/đặt lại kịch bản demo và theo dõi các điểm kiểm tra/dấu vết phân tán | Chỉ áp dụng vai trò người dùng/tải công việc trên non-production |
| Order/Payment/các service khác | Phát/tiêu thụ các sự kiện đáng tin cậy và gọi các giao ước nội bộ | Định danh tải công việc (workload identity)/mTLS hoặc chứng thực dịch vụ đã ký |
| Nhà cung cấp hóa đơn | Tiếp nhận yêu cầu chuẩn hóa và trả về phản hồi từ hệ thống của họ | Thông tin xác thực adapter; không bao giờ nhận token Cognito của POS |
| Nhà cung cấp Email/Zalo | Tiếp nhận yêu cầu chuyển phát và trả về kết quả gửi tin | Thông tin xác thực kênh gửi; không truy cập trực tiếp DB domain |
| Trợ lý AI (AI Assistant) | Đọc các dữ kiện Reporting/Recommendation đã duyệt để trả lời câu hỏi demo | Tuân thủ ranh giới công cụ/phân quyền AI sẵn có |

### 2.2 Danh mục quyền hạn (Permission Catalogue)

Các quyền dưới đây là phần sửa đổi bổ sung cho dịch vụ Identity cha:

| Quyền hạn | Owner | Manager | Cashier | Tải công việc Demo | Phạm vi áp dụng |
|---|---:|---:|---:|---:|---|
| `integration:invoice:read` | Có | Chi nhánh được giao | Chi nhánh / đơn hàng hiện tại | Không | Tenant / Chi nhánh |
| `integration:invoice:prepare` | Có | Chi nhánh được giao | Đơn hàng / checkout hiện tại | Không | Chi nhánh + Đơn hàng |
| `integration:delivery:retry` | Có | Chi nhánh được giao | Chỉ đơn hàng hiện tại, có giới hạn | Không | Chi nhánh + Hóa đơn |
| `integration:operate` | Vận hành giới hạn | Không | Không | Không | Các thao tác provider/phát lại/lưu trữ |
| `demo:run` | Không | Không | Không | Cho phép trên non-prod | Namespace kịch bản demo |
| `demo:seed-reset` | Không | Không | Không | Cho phép trên non-prod | Namespace kịch bản demo |
| `demo:fault-inject` | Không | Không | Không | Cho phép trên non-prod | Khóa thuê phiên chạy/kịch bản |
| `demo:trace:read` | Không | Không | Không | Cho phép trên non-prod | Các tham chiếu trace thuộc phiên chạy |

Identity luôn là thẩm quyền phân quyền trực tuyến duy nhất. Integration xác định thông tin tenant, chi nhánh, tác nhân và môi trường từ ngữ cảnh xác thực/tải công việc đáng tin cậy; các ID do client tự truyền lên chỉ đóng vai trò là bộ lọc hoặc tham chiếu.

### 2.3 Các quy tắc tin cậy (Trust Rules)

- API công khai bắt buộc phải có Cognito JWT; API nội bộ bắt buộc phải có định danh tải công việc (workload identity)/mTLS hoặc chứng thực dịch vụ được phê duyệt.
- Các lệnh gọi nạp hạt giống/đặt lại dữ liệu/tiêm lỗi demo sẽ lập tức đóng ngắt an toàn (fail closed) trừ khi môi trường được xác định tường minh là `DEMO` hoặc `TEST` và khóa thuê phiên chạy vẫn còn hiệu lực.
- Các webhook/callback từ nhà cung cấp bên ngoài chỉ được tiếp nhận tại ranh giới adapter kèm xác thực chữ ký của từng nhà cung cấp; tuyệt đối không bao giờ làm thay đổi trạng thái Order/Payment.
- Integration không bao giờ đọc trực tiếp cơ sở dữ liệu của các dịch vụ cha; hệ thống chỉ tiêu thụ các sự kiện có phiên bản hoặc gọi qua các API nội bộ đã được phê duyệt.
- Điều kiện lọc theo tenant và chi nhánh được thực thi nghiêm ngặt tại tầng ứng dụng và tầng repository; các ID truyền trên đường dẫn URL không thể mở rộng phạm vi phân quyền.

## 3. Định nghĩa và các định danh chuẩn mực (Definitions & Canonical Identifiers)

| Thuật ngữ | Định nghĩa chuẩn mực |
|---|---|
| `OrderPaid.v1` | Dữ kiện đơn hàng đã thanh toán chuẩn mực bất biến do Order phát ra duy nhất một lần sau khi hoàn tất Checkout Saga. |
| `invoiceOperationId` | Định danh vòng đời tác vụ hóa đơn có tính lũy quyền do Integration sở hữu. |
| `invoiceRecipientRef` | Mã tham chiếu mờ trỏ tới bản chụp thông tin liên hệ và sự đồng ý đã mã hóa do Integration sở hữu; an toàn để đưa vào ngữ cảnh checkout của Order. |
| `providerAttemptId` | Định danh một lần gọi/phản hồi tới nhà cung cấp hóa đơn hoặc chuyển phát; các lần thử được lưu theo cơ chế chỉ ghi thêm (append-only). |
| `deliveryOperationId` | Định danh vòng đời yêu cầu chuyển phát theo từng kênh cụ thể do Integration sở hữu. |
| `demoRunId` | Định danh duy nhất cho namespace phiên chạy và nhóm dấu vết trace trên môi trường non-production. |
| `checkpointId` | Điểm đánh dấu trạng thái kỳ vọng/thực tế quan sát được cho từng bước trong kịch bản demo. |
| `faultLeaseId` | Khóa thuê phân quyền có thời hạn cho phép tiêm lỗi thử nghiệm nằm trong danh mục cho phép vào phụ thuộc demo. |
| `attributionKey` | Khóa tham chiếu ổn định liên kết giữa gợi ý món được hiển thị/chấp nhận, việc thêm món vào Order và đơn hàng thanh toán hoàn tất. |

Các ID thông dụng là chuỗi UUID v7; dấu thời gian tuân theo chuẩn ISO-8601 UTC. Khung thời gian ngày kinh doanh và hiển thị demo sử dụng bản chụp múi giờ IANA của chi nhánh tương ứng. Tiền tệ MVP là số nguyên dương tính theo đơn vị đồng (VND minor units).

## 4. Yêu cầu chức năng (Functional Requirements)

### 4.1 Thu thập kênh nhận hóa đơn và sự đồng ý của khách hàng (Recipient Preference & Consent)

| Mã yêu cầu | Mức ưu tiên | Mô tả yêu cầu |
|---|---|---|
| INT-FR-001 | P2 | Màn hình POS có thể gửi thông tin lựa chọn kênh nhận hóa đơn trước hoặc trong quá trình checkout với `channel=EMAIL` hoặc `ZALO`, thông tin liên hệ đã xác thực hợp lệ, nội dung/phiên bản đồng ý, thời điểm đồng ý, ngữ cảnh chi nhánh/đơn hàng/lượt checkout và correlation ID. |
| INT-FR-002 | P2 | Integration lưu trữ bản chụp thông tin liên hệ và sự đồng ý dưới dạng mã hóa hoặc token hóa và chỉ trả về mã tham chiếu mờ `invoiceRecipientRef`; thông tin liên hệ thô tuyệt đối không được trả về cho các API đọc thông thường của POS. |
| INT-FR-003 | P2 | Mỗi bản ghi lựa chọn kênh nhận gắn chặt với duy nhất một bộ tenant/đơn hàng/lượt checkout và không thể tái sử dụng cho đơn hàng hoặc tenant khác. |
| INT-FR-004 | P2 | Trường hợp thông tin đồng ý bị thiếu, không hợp lệ, bị thu hồi hoặc hết hạn, hệ thống sẽ không tạo yêu cầu chuyển phát; tác vụ hóa đơn kỹ thuật vẫn có thể tiếp tục nếu được cấu hình. |
| INT-FR-005 | P2 | Việc cập nhật kênh nhận trước khi hóa đơn được phát hành sẽ tạo ra một phiên bản mới; các lần thử gọi nhà cung cấp hoặc chuyển phát đã ghi nhận trước đó vẫn bảo toàn bản chụp bất biến ban đầu. |
| INT-FR-006 | P2 | Thao tác gửi lại thủ công đòi hỏi tác nhân phải có quyền hợp lệ và hệ thống sẽ thẩm định lại điều kiện chuyển phát; tuyệt đối không tự ý thay đổi bản chụp sự đồng ý gốc. |

Quy trình chuẩn là Order sẽ mang theo `invoiceRecipientRef` trong ngữ cảnh checkout và phát lại mã này trong sự kiện `OrderPaid.v1`. Integration không tự ý tra cứu số điện thoại Loyalty hay query DB của Order để tìm kiếm thông tin liên hệ bị thiếu.

### 4.2 Tác vụ phát hành hóa đơn từ sự kiện `OrderPaid.v1` (Invoice Operation)

| Mã yêu cầu | Mức ưu tiên | Mô tả yêu cầu |
|---|---|---|
| INT-FR-010 | P2 | Integration chỉ tiêu thụ sự kiện chuẩn mực `OrderPaid.v1` để tạo hóa đơn; sự kiện `PaymentPaid.v1` đứng đơn lẻ tuyệt đối không được phép tạo hóa đơn. |
| INT-FR-011 | P2 | Đối với mỗi sự kiện thanh toán hợp lệ, Integration tạo tối đa một tác vụ hóa đơn duy nhất cho mỗi bộ `(tenantId, orderId, checkoutAttemptId, invoiceProfileVersion)`. |
| INT-FR-012 | P2 | Khi sự kiện `OrderPaid.v1` bị trùng lặp hoặc phát lại (replay), hệ thống trả về hoặc hội tụ về tác vụ đã tồn tại mà không thực hiện cuộc gọi phát hành thứ hai sang nhà cung cấp. |
| INT-FR-013 | P2 | Tác vụ hóa đơn lưu giữ bản chụp an toàn của đơn hàng/dòng món/tổng tiền/chi nhánh phục vụ hợp đồng sandbox; không biến thành nguồn chân lý của Order. |
| INT-FR-014 | P2 | Yêu cầu phát hành hóa đơn diễn ra bất đồng bộ và tuyệt đối không bao giờ chặn việc hoàn tất trạng thái `PAID` của Order hoặc quá trình kết thúc Saga. |
| INT-FR-015 | P2 | Phản hồi từ nhà cung cấp được ánh xạ sang trạng thái chuẩn hóa và mã lỗi an toàn; payload thô, chữ ký số hay khóa bí mật của nhà cung cấp không bao giờ được trả ra ngoài hay ghi ra log. |
| INT-FR-016 | P2 | Lỗi timeout, lỗi 5xx hoặc vượt giới hạn tần suất từ nhà cung cấp sẽ chuyển tác vụ sang trạng thái lỗi có thể thử lại kèm cơ chế giãn cách lũy thừa và đưa vào DLQ khi vượt ngưỡng chính sách. |
| INT-FR-017 | P2 | Phản hồi nghiệp vụ không hợp lệ, dữ liệu hóa đơn sai định dạng hoặc năng lực không được hỗ trợ sẽ chuyển tác vụ sang trạng thái lỗi kết thúc kèm mã khắc phục an toàn cho vận hành viên. |
| INT-FR-018 | P2 | Tác vụ phát hành hóa đơn gắn nhãn hồ sơ nhà cung cấp `SANDBOX_TECHNICAL`; trạng thái `ISSUED` chỉ mang ý nghĩa nhà cung cấp kỹ thuật đã chấp thuận yêu cầu, không mang giá trị pháp lý về thuế. |
| INT-FR-019 | P2 | Integration tuyệt đối không bao giờ phát ra các sự kiện `OrderPaid`, `PaymentPaid`, tích điểm, tồn kho hay doanh thu như một tác dụng phụ của quy trình xử lý hóa đơn. |

### 4.3 Chuyển phát hóa đơn qua Email/Zalo (Email/Zalo Delivery)

| Mã yêu cầu | Mức ưu tiên | Mô tả yêu cầu |
|---|---|---|
| INT-FR-020 | P2 | Yêu cầu chuyển phát chỉ được khởi tạo sau khi việc phát hành hóa đơn thành công và có sẵn thông tin lựa chọn kênh nhận hợp lệ kèm sự đồng ý của khách hàng. |
| INT-FR-021 | P2 | Mỗi kênh gửi sử dụng một khóa lũy quyền ổn định được dẫn xuất từ tenant, tác vụ hóa đơn, phiên bản bản chụp người nhận và loại kênh gửi. |
| INT-FR-022 | P2 | Các lần thử gọi nhà cung cấp chuyển phát được lưu theo cơ chế chỉ ghi thêm, ghi nhận kết quả chuẩn hóa, khả năng thử lại, metadata giới hạn tần suất và mã tham chiếu nhà cung cấp an toàn. |
| INT-FR-023 | P2 | Worker chuyển phát áp dụng cơ chế thử lại có giới hạn kèm giãn cách lũy thừa, giới hạn tần suất theo từng kênh và hàng đợi DLQ; không thử lại vô hạn khi thông tin người nhận không hợp lệ hoặc sự đồng ý đã bị thu hồi. |
| INT-FR-024 | P2 | Trạng thái chuyển phát được hiển thị công khai cho các API POS/Owner/Manager được phân quyền mà không làm lộ thông tin liên hệ thô hoặc payload của nhà cung cấp. |
| INT-FR-025 | P2 | Thao tác gửi lại thủ công đảm bảo tính lũy quyền và đòi hỏi quyền hạn phù hợp; chỉ được tạo lần thử mới thuộc cùng phả hệ hóa đơn/sự đồng ý ban đầu. |
| INT-FR-026 | P2 | Thời hạn hết hạn của yêu cầu chuyển phát được xác định rõ ràng; việc hết hạn không làm thay đổi trạng thái hóa đơn hay thanh toán. |
| INT-FR-027 | P2 | Zalo và Email là các lần thử riêng biệt theo từng kênh; sự cố ở một kênh không tự ý kích hoạt gửi sang kênh còn lại khi người dùng chưa chọn hoặc chưa có chính sách cho phép. |

### 4.4 Điều kiện tiên quyết và vòng đời phiên chạy Demo (Demo Lifecycle)

| Mã yêu cầu | Mức ưu tiên | Mô tả yêu cầu |
|---|---|---|
| INT-FR-030 | P0-demo | Vận hành viên demo lựa chọn kịch bản có phiên bản, phiên bản dữ liệu hạt giống, namespace tenant/chi nhánh và run ID trước khi thực thi. |
| INT-FR-031 | P0-demo | Khâu kiểm tra sơ bộ (Preflight) xác thực môi trường, sức khỏe các dịch vụ, thông tin xác thực sandbox, cờ tính năng bắt buộc, bộ xuất trace và namespace demo sạch sẽ. |
| INT-FR-032 | P0-demo | Trình điều phối demo có thể nạp dữ liệu hạt giống tất định cho chi nhánh/danh mục/khách hàng/quy tắc/công thức thông qua các API đã duyệt hoặc adapter nạp hạt giống trên non-production. |
| INT-FR-033 | P0-demo | Mỗi phiên chạy demo có thời hạn tồn tại có giới hạn, người sở hữu, khóa thuê, chính sách dọn dẹp và hoàn toàn không thấy chéo dữ liệu/trace của phiên chạy khác. |
| INT-FR-034 | P0-demo | Trình điều phối thực thi kịch bản thành công qua các API microservice thật: mở ca, tạo đơn, hiển thị/chấp nhận gợi ý món, tra cứu số điện thoại Loyalty, thanh toán QR VNPAY sandbox/IPN, hoàn tất Order, cộng điểm Loyalty và trừ kho Inventory. |
| INT-FR-035 | P0-demo | Trình điều phối ghi nhận các điểm kiểm tra checkpoint kỳ vọng và thực tế cho từng bước, bao gồm ID, trạng thái, chất lượng dữ liệu và các tham chiếu trace. |
| INT-FR-036 | P0-demo | Trình điều phối có thể tiêm lỗi `PAYMENT_UNAVAILABLE` duy nhất khi có khóa thuê lỗi hợp lệ trên non-production và xác minh luồng bồi hoàn giải phóng tồn kho. |
| INT-FR-037 | P0-demo | Thao tác đặt lại dữ liệu (reset) đảm bảo tính lũy quyền, có giới hạn thời gian và tuyệt đối không thể xóa hoặc sửa đổi tài nguyên nằm ngoài namespace của demo. |
| INT-FR-038 | P0-demo | Báo cáo tổng kết demo phân định rạch ròi giữa kết quả: Đạt (pass), Khẳng định thất bại (failed assertion), Quá thời gian chờ (timeout), Lỗi điều kiện sơ bộ và Lỗi hạ tầng. |
| INT-FR-039 | P0-demo | Chế độ xem trace demo liên kết các trường `demoRunId`, `scenarioVersion`, `correlationId`, `causationId`, `sagaId`, order/attempt ID và các span con mà không chứa PII hay khóa bí mật thô. |

### 4.5 Câu hỏi Trợ lý AI và phân tích quy kết trong Demo (Demo AI Attribution)

| Mã yêu cầu | Mức ưu tiên | Mô tả yêu cầu |
|---|---|---|
| INT-FR-040 | P0-demo | Kịch bản demo kiểm chứng câu hỏi của Chủ quán / Quản lý: “Tuần này việc gợi ý Coca đã giúp tăng thêm bao nhiêu doanh thu?” thông qua cơ chế gọi hàm sẵn có của Trợ lý AI. |
| INT-FR-041 | P0-demo | Câu trả lời sử dụng các hàm đã được phê duyệt của Reporting/Recommendation, không truy vấn bảng thô của Integration hay để LLM tự tính toán số học trên đơn hàng thô. |
| INT-FR-042 | P0-demo | Phả hệ quy kết phân định rạch ròi các bước: `SuggestionShown` (Hiển thị gợi ý), `SuggestionAccepted` (Khách đồng ý), thêm dòng món Coca thành công, `OrderPaid` (Đơn thanh toán hoàn tất) và công thức tính mức tăng trưởng (uplift) / chỉ số AOV. |
| INT-FR-043 | P0-demo | Câu trả lời nêu rõ kỳ báo cáo, phạm vi chi nhánh, độ tươi mới / tính đầy đủ, phiên bản công thức tính số liệu và nguồn gốc; dữ liệu bị chậm hoặc từng phần không được trình bày như một sự thật hoàn chỉnh. |
| INT-FR-044 | P0-demo | Trường hợp dữ liệu quy kết bị thiếu, đến trễ hoặc chưa đầy đủ, Trợ lý AI bắt buộc phải trả về thông báo giới hạn cấu trúc an toàn thay vì tự bịa ra con số tăng trưởng. |

## 5. Máy trạng thái của Hóa đơn và Chuyển phát (State Machines)

### 5.1 Tác vụ hóa đơn (Invoice Operation)

```text
NOT_REQUESTED
      │ có sự kiện OrderPaid hợp lệ
      ▼
REQUESTED ── worker nhận tác vụ ──> ISSUING
   │                                     │
   │                                     ├─ nhà cung cấp chấp thuận ──> ISSUED
   │                                     ├─ lỗi có thể thử lại ───────> FAILED_RETRYABLE ──> REQUESTED
   │                                     └─ lỗi kết thúc ─────────────> FAILED_TERMINAL
```

Các quy tắc trạng thái:

- `ISSUED` là trạng thái kết thúc cho hồ sơ hóa đơn kỹ thuật; không thực hiện cuộc gọi phát hành thứ hai cho cùng một khóa tác vụ.
- `FAILED_RETRYABLE` chỉ có thể quay trở lại `REQUESTED` thông qua việc worker thử lại lũy quyền hoặc vận hành viên gửi lại thủ công có phân quyền.
- `FAILED_TERMINAL` đòi hỏi phải có một tác vụ/phiên bản mới được chính sách phê duyệt; không được âm thầm ghi đè lần thử ban đầu.
- Tuyệt đối không có bất kỳ lần chuyển trạng thái hóa đơn nào làm thay đổi dữ liệu của Order, Payment, Loyalty hay Inventory.

### 5.2 Tác vụ chuyển phát (Delivery Operation)

```text
NOT_REQUESTED → PENDING → SENDING
                         ├─> SENT
                         ├─> FAILED_RETRYABLE → PENDING
                         ├─> FAILED_TERMINAL
                         └─> EXPIRED
```

Các quy tắc trạng thái:

- `SENT` mang ý nghĩa nhà cung cấp đã tiếp nhận yêu cầu chuyển phát theo hợp đồng kênh gửi, không nhất thiết là người nhận đã mở tin nhắn.
- Webhook phản hồi kết quả chuyển phát từ nhà cung cấp (nếu có) được khử trùng lặp và chỉ bổ sung bằng chứng chuyển phát chứ không ghi đè lần thử gửi gốc ban đầu.
- `EXPIRED` là trạng thái kết thúc cho lần thử đó; thao tác gửi lại thủ công sẽ tạo một lần thử mới được liên kết phả hệ sau khi vượt qua kiểm tra phân quyền và sự đồng ý.

### 5.3 Phiên chạy Demo (Demo Run)

```text
CREATED → PREFLIGHTING → READY → RUNNING
  │          │             │        ├─> PASSED
  │          │             │        ├─> FAILED
  │          │             │        └─> TIMED_OUT
  │          └─────────────┴──────> PREFLIGHT_FAILED
  └────────────────────────────────> RESETTING → RESET
```

Cơ chế tiêm lỗi là một bước thuê có thời hạn nằm trong trạng thái `RUNNING`; nó sẽ tự động hết hạn và tuyệt đối không thể gắn vào tài nguyên trên môi trường production.

## 6. Yêu cầu chi tiết cấp độ API (API Requirements)

Toàn bộ các endpoint công khai sử dụng Cognito JWT, phạm vi tenant/chi nhánh do máy chủ giải quyết và phong bì phản hồi an toàn `{data, meta}`. Các endpoint nội bộ phục vụ demo/nhà cung cấp sử dụng định danh tải công việc (workload identity) và bị giới hạn chặt chẽ về mạng/môi trường.

### 6.1 Endpoints công khai cho nhân viên (Public/Staff Endpoints)

| Phương thức | Đường dẫn | Quyền hạn | Mục đích nghiệp vụ |
|---|---|---|---|
| POST | `/api/v1/invoice-recipient-intents` | `integration:invoice:prepare` | Thu thập thông tin liên hệ Email/Zalo hợp lệ + bản chụp sự đồng ý |
| GET | `/api/v1/invoices/{invoiceOperationId}` | `integration:invoice:read` | Xem trạng thái hóa đơn / chuyển phát an toàn |
| GET | `/api/v1/invoices` | `integration:invoice:read` | Danh sách hóa đơn có phân trang, lọc theo đơn/chi nhánh/trạng thái |
| POST | `/api/v1/invoices/{invoiceOperationId}/delivery-retries` | `integration:delivery:retry` | Yêu cầu gửi lại có phân quyền kèm khóa lũy quyền |
| GET | `/api/v1/invoices/{invoiceOperationId}/deliveries` | `integration:invoice:read` | Xem lịch sử trạng thái các kênh gửi mà không lộ liên hệ thô |

Endpoint `invoice-recipient-intents` chỉ trả về mã tham chiếu mờ `invoiceRecipientRef`; tuyệt đối không trả về thông tin xác thực nhà cung cấp, liên hệ thô hay payload đầy đủ của nhà cung cấp.

### 6.2 Endpoints nội bộ phục vụ Demo (Internal Demo Endpoints)

| Phương thức | Đường dẫn | Quyền hạn | Mục đích nghiệp vụ |
|---|---|---|---|
| POST | `/internal/v1/demo/runs` | `demo:run` | Tạo phiên chạy demo có phiên bản và yêu cầu kiểm tra sơ bộ |
| GET | `/internal/v1/demo/runs/{demoRunId}` | `demo:run` | Đọc tóm tắt phiên chạy / điểm kiểm tra / dấu vết trace |
| POST | `/internal/v1/demo/runs/{demoRunId}/execute` | `demo:run` | Bắt đầu hoặc tiếp tục thực thi các bước kịch bản cho phép |
| POST | `/internal/v1/demo/runs/{demoRunId}/reset` | `demo:seed-reset` | Đặt lại dữ liệu trong namespace demo một cách lũy quyền |
| POST | `/internal/v1/demo/runs/{demoRunId}/fault-leases` | `demo:fault-inject` | Yêu cầu cấp khóa thuê tiêm lỗi có thời hạn trong danh mục cho phép |
| DELETE | `/internal/v1/demo/runs/{demoRunId}/fault-leases/{faultLeaseId}` | `demo:fault-inject` | Thu hồi khóa thuê tiêm lỗi trước thời hạn |
| GET | `/internal/v1/demo/runs/{demoRunId}/traces` | `demo:trace:read` | Trả về các liên kết / tham chiếu trace được phân quyền |
| GET | `/internal/v1/integration/health` | workload/ops | Kiểm tra sức khỏe các phụ thuộc, nhà cung cấp và hàng đợi |

### 6.3 Phân loại mã lỗi (Error Taxonomy)

| HTTP | Mã lỗi | Ý nghĩa nghiệp vụ |
|---:|---|---|
| 400 | `invalid_request` | Sai định dạng phần thân hoặc tiêu đề yêu cầu |
| 401 | `unauthorized` | Thiếu hoặc thông tin xác thực danh tính không hợp lệ |
| 403 | `forbidden` | Quyền hạn, môi trường hoặc vai trò bị từ chối |
| 404 | `not_found` | Tài nguyên không tồn tại hoặc nằm ngoài phạm vi phân quyền |
| 409 | `idempotency_conflict` | Trùng khóa lũy quyền nhưng sai khác nội dung yêu cầu |
| 409 | `invoice_state_conflict` | Chuyển đổi trạng thái hóa đơn/chuyển phát không hợp lệ |
| 409 | `demo_run_conflict` | Xung đột về khóa thuê, phiên bản hoặc trạng thái phiên chạy |
| 422 | `invalid_contact_or_consent` | Xác thực thông tin liên hệ, kênh gửi hoặc sự đồng ý thất bại |
| 422 | `invalid_demo_scenario` | Kịch bản, dữ liệu hạt giống hoặc lỗi tiêm không nằm trong danh mục cho phép |
| 429 | `rate_limited` | Vượt giới hạn tần suất của nhà cung cấp, người dùng, tenant hoặc kênh gửi |
| 503 | `dependency_unavailable` | Phụ thuộc hoặc nhà cung cấp tạm thời gián đoạn; áp dụng chính sách thử lại an toàn |

Thông báo lỗi tuyệt đối không bao giờ để lộ sự tồn tại của tenant/chi nhánh ngoài quyền, lỗi thô của nhà cung cấp, giá trị liên hệ, token, secret, câu lệnh SQL hay stack trace.

## 7. Yêu cầu phi chức năng (Non-Functional Requirements)

| Lĩnh vực | Yêu cầu chi tiết |
|---|---|
| Cách ly bất đồng bộ | Các worker xử lý hóa đơn/chuyển phát không nằm trên đường dẫn găng đồng bộ của quy trình checkout; sự cố của consumer `OrderPaid` chỉ làm chậm tiến độ tích hợp. |
| Độ trễ tiếp nhận | p95 tính từ thời điểm nhận `OrderPaid.v1` tới khi lưu thành công tác vụ hóa đơn ≤ 5 giây dưới tải mục tiêu; độ trễ gọi nhà cung cấp được đo đạc riêng. |
| Mục tiêu chuyển phát | p95 tính từ thời điểm `InvoiceIssued` tới khi nhà cung cấp tiếp nhận chuyển phát ≤ 2 phút khi mạng ổn định; thời gian timeout được cấu hình theo từng provider. |
| Thời lượng kịch bản Demo | Kịch bản thành công chạy với dữ liệu hạt giống chuẩn đạt mục tiêu ≤ 10 phút, nêu rõ thời gian chờ từng bước và thời gian chờ sandbox VNPAY. |
| Tính lũy quyền | Sự kiện trùng lặp, đến sai thứ tự, phát lại, gọi lại API và worker thử lại đều phải hội tụ an toàn mà không tạo ra hóa đơn trùng, tin nhắn lặp hay reset demo sai. |
| Tính sẵn sàng | Sự cố của Integration không làm thay đổi trạng thái đơn hàng đã thanh toán hay trạng thái Saga; lỗi nhà cung cấp hiển thị `FAILED_RETRYABLE`/`DEGRADED`. |
| Khả năng mở rộng quy mô | Mục tiêu ban đầu: Duy trì ổn định 10 sự kiện thanh toán/giây trong 5 phút, 100 lần thử chuyển phát đồng thời, 10 phiên chạy demo đồng thời trên mỗi môi trường non-prod. |
| Cách ly đa tenant | Mọi bảng dữ liệu, truy vấn và bản chiếu sự kiện đều mang phạm vi tenant; điều kiện lọc chi nhánh áp dụng cho tài nguyên chi nhánh; thao tác hàng loạt phải kiểm tra toàn bộ phần tử. |
| An ninh thông tin | Khóa bí mật lưu trong AWS Secrets Manager/KMS; xác thực tải công việc qua mTLS; cấm hardcode credentials; xác thực chữ ký số tại ranh giới adapter. |
| Quyền riêng tư | Thông tin liên hệ được mã hóa hoặc token hóa, ẩn dữ liệu trên phản hồi, lưu vết sự đồng ý theo chính sách, không ghi PII thô ra log, trace hay event. |
| Khả năng quan sát | Duy trì correlation, causation, demo và saga ID, ghi log có cấu trúc, gắn span AWS X-Ray, theo dõi độ trễ hàng đợi, độ trễ nhà cung cấp và điểm kiểm tra. |
| Phục hồi sự cố | Cơ chế phát lại inbox/outbox, xây dựng lại tác vụ và đặt lại demo có giới hạn, có khóa thuê, đảm bảo tính lũy quyền và có thể kiểm toán. |
| Thời hạn lưu trữ | Các phân loại dữ liệu hóa đơn, chuyển phát, sự đồng ý và bằng chứng demo có chính sách lưu trữ / giữ pháp lý rõ ràng trước khi migration; giảm thiểu lưu payload thô. |

## 8. Yêu cầu dữ liệu và lưu trữ (Data & Persistence Requirements)

Integration sở hữu một cơ sở dữ liệu PostgreSQL hoàn toàn riêng biệt. Mọi cột mới đều bắt buộc phải có câu lệnh `COMMENT ON COLUMN` mô tả rõ ràng nghiệp vụ.

### 8.1 Các bảng logic (Logical Tables)

| Tên bảng | Mục đích sử dụng | Các bất biến nghiệp vụ cốt lõi |
|---|---|---|
| `invoice_recipient_snapshots` | Lưu thông tin liên hệ và phiên bản đồng ý đã mã hóa/token hóa | Phạm vi tenant/đơn hàng/lượt checkout; bất biến theo phiên bản; không lộ liên hệ thô |
| `invoice_operations` | Vòng đời tác vụ hóa đơn kỹ thuật | Duy nhất theo bộ tenant/đơn hàng/lượt checkout/hồ sơ; chuyển trạng thái bằng CAS |
| `invoice_items` | Bản chụp an toàn của dòng món và số tiền cho nhà cung cấp | Chỉ là bản chụp; bất biến tiền VND; không làm thay đổi Order gốc |
| `invoice_provider_attempts` | Lịch sử các lần gọi yêu cầu và phản hồi từ nhà cung cấp | Khóa lũy quyền duy nhất; phản hồi thô được mã hóa hoặc lược bỏ theo chính sách |
| `delivery_operations` | Vòng đời chuyển phát theo từng kênh | Duy nhất một tác vụ cho mỗi phả hệ hóa đơn / kênh / phiên bản người nhận |
| `delivery_provider_attempts` | Các lần thử gọi nhà cung cấp Email/Zalo | Chỉ ghi thêm kết quả và metadata thử lại; không lưu thông tin xác thực thô |
| `demo_runs` | Trạng thái kịch bản, dữ liệu hạt giống, môi trường và phiên chạy | Namespace phiên chạy duy nhất; có khóa thuê/TTL; kiểm tra môi trường nghiêm ngặt |
| `demo_checkpoints` | Trạng thái các bước kỳ vọng và thực tế quan sát được | Trình tự tăng đơn điệu trong phiên chạy; các tham chiếu trace đã che giấu thông tin |
| `fault_leases` | Khóa thuê tiêm lỗi trong danh mục cho phép trên non-production | Gắn chặt với môi trường/phiên chạy/thời hạn/quyền hạn; tự động hết hạn |
| `inbox_events` | Khử trùng lặp sự kiện và theo dõi trạng thái tiếp nhận | Duy nhất theo khóa nhà sản xuất / ID sự kiện nguồn; trạng thái lỗi / DLQ |
| `outbox_messages` | Lưu các sự kiện hóa đơn, demo và chất lượng dữ liệu phát ra | Ghi nguyên tử cùng giao dịch với trạng thái cục bộ; cơ chế thử lại / DLQ |
| `projection_watermarks` | Theo dõi độ tươi mới của nguồn, nhà cung cấp và demo | Tăng đơn điệu theo phạm vi, nguồn và bản chiếu |
| `audit_references` | Lưu các tham chiếu thao tác và truy cập đã che giấu dữ liệu | Không lưu PII/secret thô; liên kết tới nguồn lưu trữ chính của dịch vụ Audit |

### 8.2 Các mô hình truy cập dữ liệu bắt buộc (Access Patterns)

- Tra cứu trạng thái hóa đơn theo tenant + chi nhánh + đơn hàng/lượt checkout, sắp xếp mới nhất lên đầu.
- Quét các tác vụ hóa đơn theo trạng thái và thời gian thử lại phục vụ worker thăm dò.
- Tra cứu trạng thái chuyển phát theo hóa đơn/kênh gửi và hàng đợi lỗi có thể thử lại.
- Truy vấn lịch sử các lần thử gọi nhà cung cấp theo tác vụ/khóa lũy quyền.
- Quét phiên chạy/điểm kiểm tra demo theo run ID và khóa thuê/thời hạn hết hạn còn hiệu lực.
- Xử lý bảng inbox/outbox theo trạng thái, lần thử tiếp theo và mốc kiểm chứng nguồn.

Các chỉ mục bắt buộc bao gồm: tenant/chi nhánh/đơn hàng/thời điểm tạo, trạng thái tác vụ/thời gian thử lại, trạng thái hóa đơn/kênh gửi, khóa sự kiện nguồn duy nhất, môi trường/phiên chạy/trạng thái demo, thời hạn khóa thuê lỗi đang hoạt động và độ tươi mới của mốc watermark. Việc phân vùng theo tháng (RANGE partitioning) chỉ xem xét áp dụng sau khi có dữ liệu đo đạc thực tế.

## 9. Yêu cầu về sự kiện và tích hợp (Event & Integration Requirements)

### 9.1 Phong bì sự kiện chung (Common Envelope)

Mọi sự kiện đều tuân thủ cấu trúc:

```json
{
  "eventId": "0192...",
  "eventType": "InvoiceIssued.v1",
  "eventVersion": 1,
  "occurredAt": "2026-10-13T08:00:00Z",
  "tenantId": "0192...",
  "branchId": "0192...",
  "aggregateType": "INVOICE_OPERATION",
  "aggregateId": "0192...",
  "aggregateVersion": 3,
  "sourceEventId": "0192...",
  "sourceVersion": 18,
  "correlationId": "0192...",
  "causationId": "0192...",
  "producer": "integration-service",
  "data": {}
}
```

Cơ chế chuyển phát sự kiện đảm bảo tính chất ít nhất một lần. Cặp `eventId` và `(producer, sourceEventId)` ổn định qua các lần thử lại hoặc phát lại. Các sự kiện có phiên bản không xác định hoặc không vượt qua bước che giấu dữ liệu sẽ bị đưa vào khu vực cách ly hoặc hàng đợi DLQ.

### 9.2 Các sự kiện tiếp nhận (Consumed Events)

| Sự kiện | Mục đích sử dụng | Các trường bắt buộc phải có |
|---|---|---|
| `OrderPaid.v1` | Tạo tác vụ hóa đơn và kích hoạt các bước downstream | Mã đơn/lượt checkout/thanh toán, chi nhánh, bản chụp dòng món/tổng tiền an toàn, `invoiceRecipientRef` (tùy chọn), phiên bản nguồn, correlation ID |
| `OrderCheckoutFailed.v1` | Hủy bỏ ý định nhận hóa đơn chưa dùng và tương quan lỗi demo | Đơn hàng/lượt checkout, trạng thái lỗi/bồi hoàn, tenant/chi nhánh, correlation ID |
| `OrderCheckoutCompensated.v1` (hoặc tương đương) | Xác minh điểm kiểm tra giải phóng tài nguyên trong demo | Đơn hàng/lượt checkout/saga ID, tham chiếu tài nguyên được giải phóng, correlation ID |
| `SuggestionShown.v1` | Chỉ dùng tra cứu bằng chứng phục vụ demo/báo cáo; không phải doanh thu | Ngữ cảnh gợi ý/món/đơn hàng/giỏ hàng, interaction ID |
| `SuggestionAccepted.v1` | Chỉ dùng làm điểm kiểm tra quy kết demo; không phải doanh thu | Bằng chứng dòng món/gợi ý, interaction ID, phiên bản nguồn |
| `PaymentReconciliationRequired.v1` | Giám sát trạng thái thanh toán muộn/chưa rõ phục vụ demo/vận hành | Tham chiếu thanh toán/đơn hàng/lượt checkout, trạng thái an toàn, correlation ID |

Integration tuyệt đối không tiêu thụ `PaymentPaid.v1` làm căn cứ thẩm quyền phát hành hóa đơn khi chưa có sự kiện `OrderPaid.v1`.

### 9.3 Các sự kiện phát ra (Produced Events)

| Sự kiện | Mục đích sử dụng |
|---|---|
| `InvoiceOperationRequested.v1` | Đã khởi tạo tác vụ hóa đơn kỹ thuật |
| `InvoiceIssued.v1` | Nhà cung cấp sandbox đã tiếp nhận yêu cầu; kèm hồ sơ năng lực và mã tham chiếu an toàn |
| `InvoiceFailed.v1` | Thất bại khi tạo hóa đơn (có thể thử lại hoặc kết thúc) kèm mã chuẩn hóa |
| `InvoiceDeliveryRequested.v1` | Đã lập lịch yêu cầu chuyển phát qua kênh gửi |
| `InvoiceDeliveryStatusChanged.v1` | Cập nhật trạng thái chuyển phát an toàn / thử lại / hết hạn |
| `DemoRunStarted.v1` | Phiên chạy kịch bản demo đã bắt đầu |
| `DemoCheckpointRecorded.v1` | Ghi nhận điểm kiểm tra kỳ vọng/thực tế và liên kết trace/chất lượng |
| `DemoRunCompleted.v1` | Báo cáo tổng kết phiên chạy demo (Đạt / Không đạt / Quá thời gian) |
| `IntegrationDataQualityChanged.v1` | Thay đổi về độ tươi mới hoặc tính đầy đủ của nguồn / nhà cung cấp / hàng đợi |
| `IntegrationOperationAuditReferenced.v1` | Bắn tham chiếu thao tác/truy cập đã che giấu sang dịch vụ Audit |

Các sự kiện phát ra tuyệt đối không bao giờ chứa payload thô của nhà cung cấp, thông tin xác thực, token, OTP hoặc giá trị liên hệ chưa được che giấu.

## 10. Yêu cầu về khả năng quan sát và kiểm toán (Observability & Audit)

- Mọi yêu cầu API, consumer tiếp nhận sự kiện, lần thử gọi nhà cung cấp và bước demo đều mang đầy đủ ngữ cảnh correlation/causation ID.
- Các span AWS X-Ray/OpenTelemetry bao gồm thông tin dịch vụ, thao tác, mã băm phạm vi an toàn, ID đơn hàng/lượt checkout/hóa đơn/demo và kết quả; tuyệt đối không chứa số điện thoại/email/Zalo hay bí mật thanh toán.
- Các chỉ số đo lường (Metrics) bắt buộc: `invoice_operation_lag`, `invoice_issue_success_rate`, `invoice_provider_latency`, `invoice_retry_count`, `delivery_success_rate_by_channel`, `delivery_provider_latency`, `dlq_depth`, `order_paid_consumer_lag`, `demo_step_duration`, `demo_checkpoint_failure`, `fault_lease_count` và `trace_export_failure`.
- Ngưỡng cảnh báo (Alerts): Độ trễ nguồn > 5 phút, độ sâu DLQ > 0 trong 5 phút, hiện tượng bão thử lại (retry storm), tỷ lệ lỗi nhà cung cấp vượt ngưỡng, khóa thuê tiêm lỗi chưa hết hạn vượt quá thời gian phiên chạy, rò rỉ namespace demo, sự cố bộ xuất trace và phát hiện bất thường về sự đồng ý/truy cập.
- Các thao tác truy cập bản chụp liên hệ, chi tiết hóa đơn, gửi lại thủ công, nạp/đặt lại dữ liệu hạt giống và tiêm lỗi đều được ghi nhận bằng chứng kiểm toán kèm tác nhân/tải công việc, lý do, phạm vi, tóm tắt an toàn trước/sau và correlation ID.
- Không ghi nhận nội dung yêu cầu/phản hồi thô của nhà cung cấp ra log. Trường hợp cần lưu trữ phục vụ điều tra pháp y, lưu dưới dạng tham chiếu mã hóa đã được giảm thiểu dữ liệu theo chính sách lưu trữ / giữ pháp lý nghiêm ngặt.

## 11. Xử lý sự cố và phục hồi hệ thống (Failure & Recovery Requirements)

| Tình huống sự cố | Hành vi hệ thống yêu cầu |
|---|---|
| Sự kiện `OrderPaid` bị trùng lặp | Khử trùng lặp tại inbox; trả về tác vụ hóa đơn đã tồn tại; không thực hiện cuộc gọi phát hành thứ hai |
| Sự kiện `OrderPaid` đến sai thứ tự / Checkout thất bại | Rào chắn kiểm tra nguồn/phiên bản; không hủy hóa đơn đã phát hành thành công; ghi nhận trạng thái chất lượng/đối soát |
| Nhà cung cấp bị quá thời gian chờ (timeout) | Lưu trạng thái timeout của lần thử, chuyển trạng thái có thể thử lại, giãn cách lũy thừa/DLQ, không làm thay đổi Order |
| Phản hồi nhà cung cấp sai định dạng / không hỗ trợ | Chuyển sang trạng thái lỗi kết thúc chuẩn hóa, sinh mã an toàn cho vận hành viên, không lộ payload thô ra ngoài |
| Vượt giới hạn tần suất Email/Zalo (Rate limit) | Tuân thủ tiêu đề Retry-After/chính sách của provider, áp dụng backoff riêng cho từng kênh, không thử lại vô hạn |
| Khách hàng thu hồi sự đồng ý trước khi gửi | Hủy bỏ tác vụ chuyển phát đang chờ; bảo toàn phả hệ đã che giấu; trạng thái hóa đơn giữ nguyên |
| Worker bị crash sau khi nhà cung cấp đã tiếp nhận | Dựa vào khóa lũy quyền và truy vấn đối soát để hội tụ về trạng thái `ISSUED` hoặc `SENT` mà không sinh tác dụng phụ lặp lại |
| Cơ sở dữ liệu Integration bị gián đoạn | Chính sách ACK bảo toàn tính chất ít nhất một lần; tuyệt đối không xác nhận sự kiện nguồn trước khi transaction inbox/bằng chứng hoàn tất |
| Dịch vụ phụ thuộc trong Demo gặp sự cố | Khâu kiểm tra sơ bộ hoặc điểm kiểm tra báo lỗi kèm phụ thuộc cụ thể; tuyệt đối không báo ĐẠT giả tạo |
| Tiêm lỗi thanh toán trong Demo | Chỉ cho phép khi có khóa thuê hợp lệ; Saga chịu trách nhiệm bồi hoàn; trình điều phối xác minh việc giải phóng kho và không sinh tác dụng phụ thanh toán |
| Đặt lại dữ liệu demo bị quá thời gian chờ | Đánh dấu việc đặt lại chưa hoàn tất, giữ lại khóa thuê và bằng chứng thao tác, thử lại có giới hạn; không xóa ngoài namespace |
| Dịch vụ AWS X-Ray không khả dụng | Luồng nghiệp vụ vẫn tiếp tục hoạt động; điểm kiểm tra đánh dấu chất lượng trace bị suy giảm và phát cảnh báo vận hành |

## 12. Ma trận kiểm thử và tiêu chí nghiệm thu (Acceptance & Test Matrix)

| Lĩnh vực | Các kịch bản kiểm thử nghiệm thu bắt buộc |
|---|---|
| Tính lũy quyền hóa đơn | Phát lại cùng sự kiện `OrderPaid`, sự kiện nguồn trùng lặp, hai worker cùng tranh chấp xử lý, cùng khóa tác vụ nhưng sai khác payload |
| Vòng đời hóa đơn | Nhà cung cấp sandbox chấp thuận, timeout/thử lại, mã lỗi 429/backoff, lỗi 5xx, phản hồi sai định dạng, lỗi kết thúc do không hỗ trợ tính năng |
| Chuyển phát | Gửi Email thành công, gửi Zalo thành công, thông tin liên hệ không hợp lệ, sự đồng ý bị thu hồi, callback trùng lặp từ provider, rate limit, hết hạn, gửi lại thủ công |
| An ninh & Quyền riêng tư | Lỗ hổng IDOR chéo tenant/chi nhánh, tư cách thành viên bị thu hồi, bộ nhớ cache phạm vi, che giấu liên hệ thô/khóa bí mật/log/trace, thiếu rào chắn môi trường |
| Tương tác Order / Saga | Nhà cung cấp hóa đơn ngừng hoạt động sau OrderPaid không làm đổi trạng thái PAID; Payment lỗi trước khi thanh toán sẽ giải phóng tồn kho qua Order Saga |
| Kịch bản Demo thành công | Mở ca → gọi gà rán → hiển thị/chấp nhận gợi ý Coca → nhận diện khách Loyalty → quét QR VNPAY/IPN → PAID → tích điểm/trừ kho/báo cáo |
| Kịch bản Demo sự cố | Thanh toán không khả dụng → kích hoạt bồi hoàn → giải phóng tồn kho → không cộng điểm/trừ kho/OrderPaid trùng lặp; tham chiếu trace hiện diện đầy đủ |
| Chạy đồng thời Demo | Hai phiên chạy cùng kịch bản/dữ liệu hạt giống, đặt lại dữ liệu trong khi đang chạy, khóa thuê hết hạn, xung đột khóa thuê lỗi, cách ly namespace |
| Quy kết doanh thu cho AI | Gợi ý hiển thị nhưng không chấp nhận, chấp nhận nhưng không thêm món, thêm món nhưng không thanh toán, thanh toán sau sự kiện muộn, dữ liệu Reporting từng phần, tính đúng mức tăng trưởng |
| Phát lại và Phục hồi | Phát lại inbox/outbox, nhà cung cấp đã chấp thuận nhưng worker bị crash, phát lại DLQ, khoảng trống mốc watermark, xây dựng lại dữ liệu không tạo tác dụng phụ lặp lại |
| Kiểm thử hiệu năng | Đạt tải 10 sự kiện thanh toán/giây, 100 lần thử chuyển phát, 10 phiên demo đồng thời, các câu truy vấn danh sách có giới hạn và đáp ứng mục tiêu độ trễ hàng đợi |

## 13. Các sửa đổi bổ sung bắt buộc cho giao ước cha (Parent Contract Amendments)

### POS Platform / Identity

- Bổ sung các quyền hạn của Integration và các quyết định phân quyền theo phạm vi môi trường/tải công việc.
- Đăng ký danh mục sự kiện của Integration, cấu trúc phong bì chung, cơ chế khử trùng lặp nguồn và quyền sở hữu tham chiếu kiểm toán sang Audit.
- Tách biệt hoàn toàn các route/vai trò demo trên non-production ra khỏi các route dịch vụ trên production.

### Order

- Bổ sung trường tùy chọn `invoiceRecipientRef` / tham chiếu hồ sơ hóa đơn vào ngữ cảnh checkout và sự kiện `OrderPaid.v1` mà không để lộ liên hệ thô.
- Đảm bảo sự kiện `OrderPaid.v1` chứa các trường ổn định: `orderId`, `checkoutAttemptId`, `branchId`, phiên bản aggregate/nguồn, bản chụp an toàn của dòng món/tổng tiền, correlation/causation ID và ngữ nghĩa outbox chính xác một lần.
- Công bố sự kiện tương đương `OrderCheckoutCompensated.v1` của dịch vụ cha nếu việc xác minh demo cần dữ kiện giải phóng tường minh.

### Payment

- Duy trì sự phân định rõ ràng giữa `PaymentPaid.v1` và sự kiện chuẩn mực `OrderPaid.v1`; cung cấp dữ kiện đối soát/thanh toán muộn an toàn phục vụ ngữ cảnh demo/vận hành.
- Giữ vững quyền sở hữu sandbox VNPAY và xử lý IPN bên trong Payment; Integration tuyệt đối không can thiệp vào logic nội bộ của callback nhà cung cấp.

### Loyalty

- Cung cấp ngữ cảnh khách hàng / nhận diện số điện thoại duy nhất thông qua các API/sự kiện Loyalty đã được phê duyệt; Integration không tự tra cứu PII trực tiếp.
- Xác nhận thời điểm và tính lũy quyền của việc tích điểm từ sự kiện `OrderPaid.v1` phục vụ điểm kiểm tra trong kịch bản demo.

### Inventory

- Công bố các bằng chứng xác nhận trừ kho hoặc giải phóng tồn kho đã giữ cần thiết để kiểm chứng luồng bồi hoàn khi Payment gặp sự cố; Integration không tự tính toán tồn kho.

### Recommendation / Reporting / AI Assistant

- Công bố phả hệ quy kết chuẩn mực: Hiển thị → Chấp nhận → Thêm vào đơn → Thanh toán hoàn tất.
- Công bố từ điển số liệu và giao ước hàm cho việc tính toán doanh thu gia tăng / tăng trưởng AOV, múi giờ chi nhánh, độ tươi mới và hành vi xử lý dữ liệu chưa đầy đủ.
- Trợ lý AI duy trì nguyên tắc chỉ đọc và chỉ gọi các hàm Reporting/Recommendation đã được phê duyệt; Integration chỉ cung cấp ngữ cảnh tương quan cho demo.

### Audit

- Đăng ký các sự kiện hóa đơn, chuyển phát, demo, tiêm lỗi và truy cập dữ liệu dưới dạng các tham chiếu kiểm toán đã che giấu kèm phân loại thời hạn lưu trữ và giữ pháp lý.

## 14. Cổng phê duyệt yêu cầu (Requirements Approval Gate)

- **Trạng thái hiện tại:** Approved — Người dùng đã chấp thuận các cảnh báo từ đợt đánh giá Yêu cầu nghiệp vụ.
- **Yêu cầu trước khi vào Architecture:** Đã hoàn thành; các sửa đổi bổ sung của dịch vụ cha và các bộ fixture khả thi được theo dõi như các cổng triển khai trong tài liệu Kiến trúc và Giao ước.
- **Quy tắc triển khai:** Chưa viết controller, worker, adapter nhà cung cấp hay trình điều phối demo; chỉ cụ thể hóa tài liệu OpenAPI, AsyncAPI/JSON Schema, migration cơ sở dữ liệu và các bộ fixture tất định sau khi tài liệu Kiến trúc (Architecture) và Giao ước (Contracts) được phê duyệt hoàn tất.
