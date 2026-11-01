Started at: 2026/10/13 00:00:00
Finished at: 2026/10/13 00:00:00
Total time: 0 minutes
---

# Báo Cáo Đánh Giá Hợp Đồng (Spec Sentinel Report) — Recommendation API & Data Contracts

**Tài liệu hợp đồng:** [`2026-10-13-contracts.md`](file:///d:/vibe-code/pos/docs/designs-vi/recommendation/2026-10-13-contracts.md)  
**Ngày:** 13-10-2026  
**Trạng thái:** ⚠️ Cảnh báo (Tiến hành với sự thận trọng)

## Phạm vi đánh giá (Review scope)

- Tái sử dụng bản Tầm nhìn Recommendation, Yêu cầu nghiệp vụ, Kiến trúc và các sơ đồ tuần tự đã được phê duyệt.
- Đối chiếu chéo với các hợp đồng của POS Platform, Order, Catalog, Inventory, Loyalty và Payment.
- Repository hiện chưa có mã nguồn triển khai Recommendation, artifact OpenAPI/AsyncAPI sinh tự động hay mã nguồn migration; bản đánh giá này kiểm tra các hợp đồng thiết kế, không phải là code review mã nguồn.

## Các phát hiện

### Cảnh báo (Warnings)

| # | Phân loại | Phát hiện | Rủi ro | Khuyến nghị |
|---|---|---|---|---|
| 1 | Bằng chứng chấp thuận | Endpoint `POST /recommendations/{suggestionId}/accept` nhận `orderLineId`, `orderVersionAfterAdd` và `addLineCorrelationId` từ POS; hợp đồng chưa yêu cầu Recommendation phải xác minh được lệnh thêm dòng thực tế từ Order hoặc một sự kiện có thẩm quyền. | Máy khách (client) lỗi hoặc giả mạo có thể tạo ra sự kiện chấp thuận không gắn liền với dòng đơn hàng thực tế được thêm; số liệu phân tích P0 bị sai lệch và rất khó đối soát. | Trước khi lập trình, phải chốt một cơ chế có thẩm quyền: sự kiện thêm dòng có quy kết từ Order hoặc endpoint xác thực nội bộ; bằng chứng từ client chỉ đóng vai trò là gợi ý và không đủ để ghi nhận chấp thuận độc lập. |
| 2 | Điều chỉnh hợp đồng Order | Quy kết thêm dòng của Order mới được mô tả dưới dạng JSON nhưng chưa có phương thức/đường dẫn cụ thể, schema phản hồi và sự kiện/nguồn chân lý rõ ràng. | Các nhóm phát triển có thể triển khai khác nhau, dẫn đến trùng lặp hoặc làm mất sự kiện `SuggestionAccepted`. | Bổ sung đầy đủ hợp đồng Order, fixture cho producer và quy tắc khử trùng lặp; Recommendation phải quy tụ mọi đường ghi nhận về duy nhất một khóa nguồn. |
| 3 | Đặt tên quyền phân quyền | Danh sách luật sử dụng `rule:draft/read`, trong khi ma trận phân quyền chưa định nghĩa quyền `rule:read` độc lập; phạm vi tenant của Owner/Manager chưa được ghi rõ cho thao tác xem danh sách và tạo bản nháp. | Chính sách phân quyền sinh tự động có thể không nhất quán hoặc vô tình cấp quyền quá rộng. | Tách bạch `rule:read` ra khỏi `rule:draft`, nêu rõ phạm vi luật toàn tenant và hành vi độc lập với chi nhánh trước khi sinh mã chính sách. |
| 4 | Tính đầy đủ của máy đọc | Nhiều phản hồi / sự kiện mới chỉ được mô tả bằng bảng tóm tắt hoặc payload tối thiểu (`RulePage`, `ActiveModelResponse`, payload sự kiện, phản hồi rebuild/cold-start), chưa có đầy đủ thuộc tính schema, tính bắt buộc/nullable và ví dụ cụ thể để sinh artifact trực tiếp. | File OpenAPI/AsyncAPI sinh ra có thể bị sai lệch giữa dịch vụ cung cấp và bên tiêu thụ. | Xây dựng OpenAPI 3.1, AsyncAPI và JSON Schema chuẩn tắc từ các bảng này; bổ sung ví dụ, quy tắc tương thích và fixture producer/consumer trước khi viết code. |
| 5 | Đồng bộ sự kiện và nguồn | Hợp đồng yêu cầu `CatalogItemCreated/Updated`, `CatalogItemAvailabilityChanged`, `OrderPaid` và ngữ cảnh đặc trưng Loyalty nhưng chưa chốt đầy đủ ngữ nghĩa phiên bản / phiên bản nguồn của từng producer parent. | Checkpoint/phát lại của hình chiếu có thể không tăng đơn điệu hoặc không thể tái dựng dữ liệu sau khoảng trống phiên bản (gap). | Bổ sung tham chiếu schema / điều chỉnh trong Catalog, Inventory, Order và Loyalty; mỗi sự kiện bắt buộc có event ID, phiên bản nguồn/aggregate, thời điểm occurredAt và phạm vi tenant/chi nhánh. |
| 6 | Khóa liên kết cá nhân hóa OrderPaid | Bảng `paid_interaction_projections` cần `opaque_customer_key_hash`, trong khi sự kiện `OrderPaid.v1` của parent hiện mới chỉ bảo đảm trường `customerId` và chưa quy định cơ chế bàn giao khóa đặc trưng opaque. | Mô hình ALS có thể bỏ qua các giao dịch có khách hàng hoặc liên kết bằng danh tính không được phép. | Chọn một hợp đồng liên kết rõ ràng: Order phát mã tham chiếu khách hàng opaque đã được Loyalty ủy quyền, hoặc Recommendation nhận hình chiếu có phiên bản từ Loyalty đánh khóa theo tham chiếu khách hàng ổn định; tuyệt đối không join bằng số điện thoại. |
| 7 | Quản trị ML và quyền riêng tư | Thời hạn lưu trữ, việc xóa/tombstone dữ liệu và ngưỡng tín hiệu tối thiểu cho ALS mới chỉ nêu nguyên tắc mà chưa có giá trị chính sách / schema cấu hình; cổng chất lượng mô hình chưa có fixture chính sách ngưỡng bắt buộc. | Không thể tái lập lại các quyết định kích hoạt / rollback và tiềm ẩn rủi ro giữ dữ liệu khách hàng quá thời hạn cho phép. | Đưa thời hạn lưu trữ, ngưỡng thưa thớt, ngưỡng chỉ số, hành vi đồng thuận/xóa dữ liệu vào hợp đồng chính sách có đánh phiên bản và lưu phiên bản chính sách cùng ảnh chụp/mô hình. |
| 8 | Vận hành tích hợp DeepSeek | Tính năng P2 có tác vụ, model nhà cung cấp và phiên bản prompt nhưng chưa định nghĩa đầy đủ schema đầu vào/đầu ra có cấu trúc, ngân sách/thời gian chờ, chính sách gửi dữ liệu ra ngoài, kiểm tra an toàn và giới hạn thử lại qua DLQ. | Khởi động nguội có thể tạo ra các nhãn không an toàn, phát sinh chi phí ngoài tầm kiểm soát hoặc vô tình làm phụ thuộc vào nhà cung cấp ngoại vi trên đường dẫn realtime. | Giữ tác vụ chạy hoàn toàn bất đồng bộ; bổ sung schema đã khử độc, kiểm tra tính hợp lệ của schema, timeout/ngân sách, thử lại có giới hạn qua DLQ, xét duyệt của con người và kích hoạt qua feature flag. |

## Phân tích tác động chéo (Cross-Impact Analysis)

| Chiều phân tích | Phát hiện | Mức độ rủi ro | Khuyến nghị |
|---|---|---|---|
| **Logic dùng chung** | Recommendation là service mới hoàn toàn, chưa có call site triển khai; các bên tiêu thụ chính gồm POS, Order, Reporting/Audit và các worker ML. | 🟡 Trung bình | Sử dụng client và fixture schema sinh tự động; không để giao diện frontend tự định nghĩa ngữ nghĩa chấp thuận. |
| **Dữ liệu / Trạng thái** | Các phiên bản luật, hình chiếu tính khả dụng, phiên gợi ý, đơn thanh toán, model registry và ứng viên cold-start đều là các trạng thái mới. | 🟡 Trung bình | Thực thi bắt buộc các vị từ tenant, phiên bản công bố bất biến, checkpoint tăng đơn điệu, con trỏ CAS cho mô hình active và khử trùng lặp outbox/inbox trong các fixture migration và test. |
| **Hợp đồng API** | Bổ sung endpoint hiển thị/chấp thuận, vòng đời luật, vòng đời mô hình, ngữ cảnh Order / quy kết thêm dòng và ngữ cảnh đặc trưng Loyalty. | 🔴 Cao | Chốt cơ chế bằng chứng có thẩm quyền từ Order và tách quyền `rule:read` trước khi sinh các hợp đồng tự động. |
| **Tính toàn vẹn luồng** | Các luồng P0 gợi ý → render → thêm món → chấp thuận; P1 huấn luyện/kích hoạt ALS; P2 xét duyệt DeepSeek đã có đầy đủ nhánh thành công và lỗi. | 🟡 Trung bình | Bổ sung quy trình đối soát cho trường hợp thêm dòng thành công nhưng yêu cầu chấp thuận thất bại, cùng cơ chế phát lại từ sự kiện nguồn. |
| **Khía cạnh xuyên suốt** | Yêu cầu đa tenant/xác thực, ranh giới PII, fallback mô hình, hình chiếu quá hạn và cô lập nhà cung cấp ngoại vi đã được nêu rõ. | 🟡 Trung bình | Bổ sung các bài kiểm thử hợp đồng phủ định (negative contract tests) cho IDOR/sai tenant, không có SĐT trong log, fallback khi dữ liệu trễ và timeout/ngân sách nhà cung cấp. |

## Tuân thủ chỉ dẫn (Instruction Compliance)

| Tiêu chí | Kết luận | Sai lệch (nếu có) | Khuyến nghị |
|---|---|---|---|
| **Quy ước** | ✅ | Duy trì đúng chuẩn REST/OpenAPI 3.1, AsyncAPI/JSON Schema, UUID v7, UTC, envelopes chuẩn, phân trang cursor và event envelope của hệ thống parent. | — |
| **Kiến trúc** | ✅ | Ranh giới phân định rõ: Recommendation không sở hữu nguồn chân lý của Catalog, Inventory, Order hay Loyalty; Order thêm dòng vẫn giữ thẩm quyền tối cao. | Giữ các điều chỉnh parent là điều kiện tiên quyết bắt buộc, không tạo cơ sở dữ liệu dùng chung. |
| **Kiểm thử** | ⚠️ | Ma trận kiểm thử hợp đồng đã được thiết lập, nhưng các fixture máy đọc và bài kiểm thử bằng chứng chấp thuận có thẩm quyền chưa được cụ thể hóa. | Viết các fixture, property test và replay test trước khi lập trình controller, worker và migration. |

## Kết luận (Verdict)

Tài liệu hợp đồng **có thể được duyệt với các cảnh báo**. Không có điểm nghẽn về phạm vi nghiệp vụ hay định hướng kiến trúc. Trước khi triển khai mã nguồn, bắt buộc phải chốt 4 điểm có ảnh hưởng trực tiếp đến tính đúng đắn: Bằng chứng chấp thuận từ Order, phạm vi phân quyền, bộ schema máy đọc hoàn chỉnh và cơ chế liên kết cá nhân hóa giữa Order và Loyalty. Các cảnh báo còn lại phải được chuyển hóa thành chính sách, cấu hình và fixture cụ thể trong giai đoạn chuẩn bị lập trình.

**Quyết định bắt buộc của người dùng:** Xác nhận tiếp tục với bộ tài liệu Hợp đồng ở trạng thái **⚠️ Cảnh báo (Tiến hành với sự thận trọng)** để chuyển sang bước Tổng kết thiết kế (Design Finalization).
