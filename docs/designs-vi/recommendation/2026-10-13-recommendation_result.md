Started at: 2026/10/13 00:00:00
Finished at: 2026/10/13 00:00:00
Total time: 0 minutes
---

# Module 8 — Gợi ý món / Upsell (AI Service, ML): Kết Quả Thiết Kế

**Ngày:** 2026-10-13  
**Trạng thái:** Hoàn tất thiết kế — Approved with warnings (Đã duyệt có cảnh báo)  
**Phạm vi:** M — Standard Service Flow  
**Module:** Gợi ý món / upsell (AI service, ML)

## 1. Kết luận (Verdict)

**APPROVED WITH WARNINGS (ĐÃ PHÊ DUYỆT VỚI CÁC CẢNH BÁO)**

Thiết kế đầy đủ cơ sở để tạo danh sách công việc triển khai (implementation backlog) cho P0/P1/P2. Không có điểm nghẽn mức Nghiêm trọng/Đỏ (Critical/RED) về phạm vi, quyền sở hữu, cách ly tenant hay kiến trúc lai (hybrid). Trước khi viết code phải cụ thể hóa các hợp đồng máy đọc (machine-readable contracts) và hoàn tất các cổng triển khai ở §6, đặc biệt là cơ chế bằng chứng có thẩm quyền (authoritative evidence) cho sự kiện `SuggestionAccepted`.

## 2. Các tài liệu thành phần hoàn tất (Artifacts)

| Giai đoạn | Tài liệu | Đường dẫn | Trạng thái |
|---|---|---|---|
| Vision | Tầm nhìn (Vision) | [`2026-10-13-vision.md`](file:///d:/vibe-code/pos/docs/designs-vi/recommendation/2026-10-13-vision.md) | Đã duyệt |
| Vision review | Báo cáo đánh giá tầm nhìn | [`2026-10-13-vision-review.md`](file:///d:/vibe-code/pos/docs/designs-vi/recommendation/2026-10-13-vision-review.md) | Chấp nhận cảnh báo |
| Requirements | Yêu cầu nghiệp vụ (Requirements) | [`2026-10-13-requirements.md`](file:///d:/vibe-code/pos/docs/designs-vi/recommendation/2026-10-13-requirements.md) | Đã duyệt có cảnh báo |
| Requirements review | Báo cáo đánh giá yêu cầu | [`2026-10-13-requirements-review.md`](file:///d:/vibe-code/pos/docs/designs-vi/recommendation/2026-10-13-requirements-review.md) | Chấp nhận cảnh báo |
| Architecture | Kiến trúc hệ thống (Architecture) | [`2026-10-13-architecture.md`](file:///d:/vibe-code/pos/docs/designs-vi/recommendation/2026-10-13-architecture.md) | Đã duyệt |
| Sequence diagrams | Sơ đồ tuần tự L1/L2 PlantUML | [`2026-10-13-sequence-diagrams.adoc`](file:///d:/vibe-code/pos/docs/designs-vi/recommendation/2026-10-13-sequence-diagrams.adoc) | Đã xác nhận |
| Contracts | Hợp đồng API & Dữ liệu | [`2026-10-13-contracts.md`](file:///d:/vibe-code/pos/docs/designs-vi/recommendation/2026-10-13-contracts.md) | Đã duyệt có cảnh báo |
| Contracts review | Báo cáo đánh giá hợp đồng | [`2026-10-13-contracts-review.md`](file:///d:/vibe-code/pos/docs/designs-vi/recommendation/2026-10-13-contracts-review.md) | Chấp nhận cảnh báo |
| Finalization | Kết quả thiết kế này | Tài liệu hiện tại | Đã duyệt có cảnh báo |

## 3. Tóm tắt đánh giá thiết kế (Design review summary)

| Tiêu chí | Kết quả | Nhận xét chi tiết |
|---|---|---|
| Tính đầy đủ (Completeness) | ĐẠT CÓ CẢNH BÁO | P0 luật kết hợp/hiển thị/chấp thuận, P1 ALS/vòng đời huấn luyện đêm và P2 DeepSeek/mở rộng ngữ cảnh đều có yêu cầu, cấu phần, luồng xử lý, schema và ma trận kiểm thử. Một số schema phản hồi/sự kiện cần được cụ thể hóa thành file OpenAPI/AsyncAPI. |
| Tính nhất quán (Consistency) | ĐẠT CÓ CẢNH BÁO | Recommendation không sở hữu nguồn chân lý của Catalog/Inventory/Order/Loyalty; Order thêm dòng là thẩm quyền cuối; `OrderPaid.v1` là dữ kiện đơn thanh toán/huấn luyện chuẩn mực. Bằng chứng chấp thuận và liên kết cá nhân hóa với Order/Loyalty cần chốt hợp đồng thực thi. |
| Tính khả thi (Feasibility) | ĐẠT | Đường dẫn lai (hybrid) giữ luật kết hợp và hình chiếu khả dụng trên luồng realtime; ALS/DeepSeek chạy bất đồng bộ; sự cố mô hình tự động fallback về luật kết hợp. |
| Bảo mật (Security) | ĐẠT CÓ CẢNH BÁO | Đã có Cognito/workload identity, phòng thủ đa tầng tenant/chi nhánh, khóa Loyalty opaque, không có SĐT trong DB và lưu vết kiểm toán. Cần tách rõ quyền `rule:read`, xác minh bằng chứng chấp thuận có thẩm quyền và bổ sung fixture kiểm thử phủ định IDOR. |
| Hiệu năng (Performance) | ĐẠT | Đường dẫn Recommendation hướng tới p95 ≤ 300 ms cho bản demo / ≤ 500 ms cho baseline dịch vụ; đã thiết kế cache hình chiếu/mô hình, tập ứng viên có giới hạn và không có phụ thuộc LLM lúc realtime. |
| Khả năng quan sát (Observability) | ĐẠT CÓ CẢNH BÁO | Đã có correlation/causation, outbox/inbox, DLQ, watermark hình chiếu, chỉ số mô hình và tương tác gợi ý. Cần cơ chế đối soát khi Order thêm dòng thành công nhưng lệnh chấp thuận bị mất. |
| Sẵn sàng kiểm thử (Testing readiness) | ĐẠT CÓ CẢNH BÁO | Ma trận hợp đồng bao phủ phát lại, hình chiếu trễ, cổng ALS, cách ly tenant và lỗi nhà cung cấp; các fixture/migration/schema thực thi máy đọc cần được khởi tạo ở Phase 0. |

## 4. Đánh giá tổng kết — Cổng Code Reviewer (Code Reviewer gate)

**Phạm vi:** 8 tài liệu thiết kế của Recommendation cùng các hợp đồng parent liên quan trực tiếp; hiện chưa có code ứng dụng để review, nên đây là đánh giá chất lượng thiết kế theo checklist bảo mật, logic nghiệp vụ, hiệu năng, khả năng bảo trì và tuân thủ quy ước.

**Kết luận:** **YELLOW — Tiến hành với sự thận trọng (Proceed with Caution)**

### Tóm tắt

Thiết kế mạch lạc và sẵn sàng chuyển sang triển khai ở mức danh sách backlog. Không phát hiện secret bị hardcode, không thiếu ranh giới tenant trong thiết kế, không có phụ thuộc realtime sang DeepSeek/thời tiết, và không có cơ chế kích hoạt mô hình bỏ qua cổng chất lượng. Các cảnh báo chủ yếu nằm ở việc chuyển hóa văn bản mô tả thành hợp đồng máy đọc thực thi được và bảo đảm bằng chứng quy kết chấp thuận không bị máy khách giả mạo hay thất lạc.

### Danh sách cảnh báo

| # | File:Dòng | Phân loại | Vấn đề | Khuyến nghị |
|---|---|---|---|---|
| 1 | `2026-10-13-contracts.md:131,228-241` | Logic / API | Yêu cầu chấp thuận mang bằng chứng đơn hàng từ POS nhưng chưa có cơ chế xác minh việc commit thật từ Order. | Chốt sự kiện thêm dòng có quy kết từ Order hoặc endpoint xác thực nội bộ; dữ liệu client chỉ là gợi ý tham khảo. |
| 2 | `2026-10-13-contracts.md:108-141` | Phân quyền | Danh sách luật dùng `rule:draft/read`, chưa tách quyền `rule:read` độc lập và phạm vi toàn tenant. | Tách quyền, nêu rõ phạm vi xem/soạn thảo của Owner/Manager, sau đó sinh policy và negative test. |
| 3 | `2026-10-13-contracts.md:314-354,458-477` | Tính đầy đủ hợp đồng | Phản hồi nội bộ và payload sự kiện mới dừng ở mức danh mục / trường tối thiểu, chưa đủ required/nullability/ví dụ để sinh code trực tiếp. | Cụ thể hóa OpenAPI 3.1, AsyncAPI/JSON Schema và fixture producer/consumer trước khi viết DTO/controller. |
| 4 | `2026-10-13-contracts.md:405-434,688-705` | Dữ liệu / Quyền riêng tư | Liên kết giữa `OrderPaid.v1`, khóa opaque Loyalty và `paid_interaction_projections` chưa có hợp đồng nguồn duy nhất; chính sách lưu trữ/xóa còn ở mức cấu hình. | Chốt tham chiếu opaque ổn định, ngữ nghĩa đồng thuận/xóa, chính sách snapshot và phiên bản chính sách trong metadata mô hình. |
| 5 | `2026-10-13-contracts.md:322-350,779-815` | An toàn ML / Nhà cung cấp | Ngưỡng mô hình, schema DeepSeek, ngân sách/thời gian chờ/chính sách gửi ra ngoài/thử lại và DLQ chưa thành fixture đầy đủ. | Đánh phiên bản chính sách chất lượng/nhà cung cấp, kiểm tra schema đầu ra, thử lại bất đồng bộ có giới hạn, xét duyệt của con người và bật qua feature flag. |
| 6 | `2026-10-13-contracts.md:827-858` | Dữ liệu / Migration | DDL, ghi chú cột, chỉ mục partial, khẳng định phân vùng/lưu trữ mới chỉ được mô tả, chưa chạy thử trên database trống. | Viết các migration Flyway + `COMMENT ON COLUMN`, test ràng buộc/chỉ mục, test khôi phục/tái dựng và kiểm tra thời hạn lưu trữ. |

### Tóm tắt danh mục kiểm tra (Checklist summary)

| Phân loại | Kết luận | Số lượng vấn đề |
|---|---|---:|
| Bảo mật (Security) | ĐẠT CÓ CẢNH BÁO | 2 cảnh báo về chính sách/xác minh; 0 nghiêm trọng |
| Logic nghiệp vụ | ĐẠT CÓ CẢNH BÁO | 1 cảnh báo bằng chứng chấp thuận; đã bao quát nhánh fallback/rỗng/trễ |
| Hiệu năng (Performance) | ĐẠT | 0 nghiêm trọng; đã định nghĩa đường dẫn realtime giới hạn và worker async |
| Thiết kế rõ ràng (Clean Design) | ĐẠT CÓ CẢNH BÁO | 1 cảnh báo về việc cụ thể hóa máy đọc |
| Tuân thủ quy ước (Conventions) | ĐẠT CÓ CẢNH BÁO | 2 cảnh báo về đồng bộ hợp đồng/fixture parent |

## 5. Tóm tắt kiến trúc hệ thống (Architecture summary)

| Khía cạnh | Tóm tắt |
|---|---|
| Các cấu phần logic | Recommendation API, rule engine/ranker, hình chiếu tính khả dụng Catalog/Inventory, model runtime, session/interaction writer, ALS worker, model registry, DeepSeek adapter, hàng đợi xét duyệt cold-start và observability/outbox. |
| Cơ sở dữ liệu | 1 cơ sở dữ liệu PostgreSQL riêng cho Recommendation; lưu trữ luật, hình chiếu, phiên, tương tác, metadata huấn luyện/mô hình và trạng thái cold-start; tuyệt đối không dùng chung database. |
| Lưu trữ đối tượng (Object Storage) | Lưu trữ các ảnh chụp huấn luyện và artifact mô hình bất biến đã được mã hóa; bắt buộc kiểm tra mã băm checksum khi kích hoạt. |
| Các API | Public: gợi ý giỏ hàng / hiển thị / chấp thuận / vòng đời luật / sức khỏe mô hình / cold-start; Internal: ngữ cảnh Order, huấn luyện, mô hình, tái dựng hình chiếu, ngữ cảnh đặc trưng Loyalty. |
| Các sự kiện | `SuggestionShown.v1`, `SuggestionAccepted.v1`, vòng đời luật/mô hình, hoàn tất huấn luyện và ứng viên cold-start; vận chuyển ít nhất một lần qua outbox/inbox có khử trùng lặp. |
| Mô hình tính đúng đắn | Món ứng viên chỉ là gợi ý tư vấn; Order luôn là thẩm quyền tối cao khi thêm dòng vào đơn. Luật/mô hình đã công bố là bất biến; kích hoạt mô hình dùng cổng chất lượng + CAS; hình chiếu trễ tự động loại bỏ ứng viên an toàn. |
| Ranh giới quyền riêng tư | Loyalty nắm giữ số điện thoại và danh tính khách hàng; Recommendation chỉ lưu khóa tham chiếu opaque, trạng thái đồng thuận/đủ điều kiện/phiên bản và dữ liệu phân tích đã khử độc. |

## 6. Các cổng triển khai và quản trị rủi ro (Implementation gates and risks)

| Cổng / Rủi ro | Điều kiện hoàn tất bắt buộc |
|---|---|
| Quy kết chấp thuận (Accepted attribution) | Điều chỉnh Order có bằng chứng thêm dòng có thẩm quyền hoặc endpoint xác minh; thêm dòng thành công nhưng yêu cầu chấp thuận bị lỗi có quy trình đối soát; chấp thuận trùng lặp hội tụ về một khóa nguồn. |
| Hợp đồng máy đọc (Generated contracts) | OpenAPI công khai/nội bộ, AsyncAPI/JSON Schema, ví dụ và kiểm tra tương thích được tự động validate trong CI; các fixture producer của Order/Catalog/Inventory/Loyalty chạy thành công. |
| Quản trị luật (Rule governance) | Tách bạch rõ các quyền `rule:read` / `rule:draft` / `rule:publish`; Quản lý chỉ được soạn thảo/sửa bản nháp, Chủ chuỗi có quyền công bố/ngừng áp dụng; luật đã công bố là bất biến. |
| An toàn hình chiếu (Projection safety) | Sự kiện nguồn bắt buộc có event ID, phạm vi tenant/chi nhánh, phiên bản nguồn, occurredAt; checkpoint tăng đơn điệu; có khoảng trống/dữ liệu trễ sẽ kích hoạt rebuild; Order luôn thẩm định lần cuối. |
| Quyền riêng tư & chất lượng ALS | Huấn luyện chỉ lấy dữ liệu từ `OrderPaid.v1`; liên kết khách hàng qua khóa opaque, sự đồng thuận/xóa dữ liệu, ngưỡng thưa thớt, ngưỡng chỉ số và phiên bản chính sách được fixture hóa; bảo toàn mô hình active cũ để rollback. |
| An toàn tích hợp DeepSeek | Tác vụ chạy hoàn toàn bất đồng bộ ngoài đường dẫn realtime; dữ liệu đầu vào được làm sạch, đầu ra có cấu trúc được kiểm tra schema, có timeout/ngân sách, thử lại có giới hạn qua DLQ, có sự xét duyệt của con người và không trực tiếp sửa Catalog. |
| Tính đúng đắn cơ sở dữ liệu | Flyway chạy thành công trên DB trống; các bài test về vị từ tenant, tính duy nhất của mô hình/luật active, tính lũy nghiệm tương tác, CAS/khóa thuê, ghi chú cột, chỉ mục và phân vùng đều vượt qua. |
| Sẵn sàng vận hành (Operational readiness) | Thiết lập đầy đủ chỉ số và cảnh báo cho p95, tỷ lệ rỗng, tỷ lệ hiển thị→chấp thuận, độ trễ hình chiếu, fallback mô hình, lỗi huấn luyện, lỗi nhà cung cấp, độ trễ outbox và DLQ; có sẵn tài liệu hướng dẫn vận hành (runbook) phát lại và khử độc dữ liệu. |

## 7. Lộ trình triển khai (Implementation roadmap)

### Phase 0 — Cụ thể hóa hợp đồng và nền tảng (Contract materialization and foundation)

- **Mục tiêu:** Chuyển hóa văn bản thiết kế thành các hợp đồng máy đọc thực thi được và khung nền tảng dịch vụ.
- **Nhiệm vụ:** Viết OpenAPI 3.1 công khai/nội bộ; AsyncAPI/JSON Schema; migration Flyway cơ sở; envelope chuẩn / kho lưu trữ idempotency / outbox / inbox; bộ kiểm thử phạm vi tenant; các điều chỉnh bổ sung cho Order/Loyalty/Catalog/Inventory.
- **Phụ thuộc:** Các chủ sở hữu hợp đồng parent.
- **Tiêu chí hoàn thành:** Schema hợp lệ; các fixture sinh tự động chạy thành công; thiết kế bằng chứng chấp thuận được ký duyệt; 100% bài kiểm thử phủ định xuyên tenant/chi nhánh vượt qua.

### Phase 1 — Gợi ý theo luật kết hợp P0 (P0 association recommendation)

- **Mục tiêu:** Cung cấp gợi ý theo giỏ hàng có lọc chi nhánh/giỏ hàng/tính khả dụng với độ trễ phản hồi ổn định.
- **Nhiệm vụ:** CRUD tập luật/phiên bản luật; Quản lý tạo bản nháp + Chủ chuỗi công bố/ngừng áp dụng; các hình chiếu Catalog/Inventory; chữ ký giỏ hàng chuẩn tắc; thuật toán xếp hạng tất định; phiên gợi ý / ảnh chụp ứng viên; cơ chế xóa cache; endpoint `POST recommendations`.
- **Phụ thuộc:** Phase 0 và các sự kiện nguồn từ Catalog/Inventory.
- **Tiêu chí hoàn thành:** Luật mẫu “gà rán → Coca” trả đúng ứng viên; món đã có trong giỏ/ngừng bán/hết hàng bị loại trừ; không có luật hợp lệ trả về `items=[]`; độ trễ p95 đạt chỉ tiêu cam kết.

### Phase 2 — Phân tích tương tác P0 và quy kết Order (P0 interaction analytics and Order attribution)

- **Mục tiêu:** Đo lường chuẩn xác lượt hiển thị và chấp thuận mà không làm luồng thanh toán bị phụ thuộc vào Recommendation.
- **Nhiệm vụ:** Endpoint `/shown` xác nhận sau khi render; cơ chế quy kết / xác minh thêm dòng của Order; endpoint `/accept`; bảng outbox sự kiện; khử trùng lặp / thử lại / đối soát; các fixture consumer cho Reporting/Audit.
- **Phụ thuộc:** Phase 1 và điều chỉnh bổ sung của Order.
- **Tiêu chí hoàn thành:** Render xong mới ghi `SuggestionShown`; thêm dòng thất bại không ghi nhận chấp thuận; thêm dòng thành công ghi đúng một sự kiện chấp thuận lũy nghiệm; gợi ý trễ không làm Order bị lỗi.

### Phase 3 — Ngữ cảnh Loyalty P1 và mô hình ALS (P1 Loyalty context and ALS)

- **Mục tiêu:** Cá nhân hóa gợi ý khi có khách hàng đủ điều kiện, tự động fallback an toàn khi dữ liệu thưa thớt hoặc mô hình gặp sự cố.
- **Nhiệm vụ:** API tra cứu / hình chiếu ngữ cảnh đặc trưng Loyalty dạng opaque; hình chiếu tương tác thanh toán từ `OrderPaid.v1`; tác vụ chạy đêm tạo snapshot/huấn luyện/thẩm định; model registry / con trỏ active; bộ nhớ đệm runtime / cơ chế fallback; quy trình rollback và phát lại.
- **Phụ thuộc:** Phase 0–2 và hợp đồng đặc trưng của Loyalty.
- **Tiêu chí hoàn thành:** Tuyệt đối không có số điện thoại trong Recommendation; dữ liệu huấn luyện chỉ lấy từ đơn đã thanh toán; mô hình chưa đạt cổng chất lượng không được kích hoạt; khách hàng thưa thớt hoặc mô hình lỗi tự động fallback về luật kết hợp; rollback không làm gián đoạn luồng P0.

### Phase 4 — Khởi động nguội P2 và mở rộng ngữ cảnh (P2 cold start and context extension)

- **Mục tiêu:** Làm giàu đặc trưng cho món mới ngoài đường dẫn realtime và chuẩn bị sẵn cấu trúc xếp hạng theo ngữ cảnh.
- **Nhiệm vụ:** Adapter và tác vụ DeepSeek bất đồng bộ; làm sạch dữ liệu đầu vào và kiểm tra schema đầu ra; hàng đợi thẩm định và xét duyệt của con người; phê duyệt ứng viên; cờ bật tắt feature flag; thiết kế điểm mở rộng cho khung giờ/thời tiết (chưa tích hợp nhà cung cấp trong MVP).
- **Phụ thuộc:** Phase 0 và các sự kiện món của Catalog; không chặn tiến độ của P0/P1.
- **Tiêu chí hoàn thành:** Quá thời gian chờ hoặc giới hạn tần suất từ nhà cung cấp không ảnh hưởng đến POS; ứng viên chưa được duyệt không đi vào xếp hạng production; prompt / cấu hình / phản hồi thô không bị rò rỉ.

### Phase 5 — Gia cố hệ thống và chuẩn bị triển khai (Hardening and rollout)

- **Mục tiêu:** Đo lường các chỉ số SLO, bảo đảm khả năng phát lại/khôi phục dữ liệu và sẵn sàng đưa vào vận hành.
- **Nhiệm vụ:** Kiểm thử tải k6 cho luồng gợi ý và tương tác; kiểm thử phát lại sự kiện khi bản tin đến sai thứ tự hoặc có khoảng trống phiên bản; diễn tập tái dựng hình chiếu; diễn tập nạp mô hình và rollback; kiểm thử bảo mật chống IDOR / phân lập tenant; kiểm tra khử độc nhật ký log; hoàn thiện tài liệu hướng dẫn vận hành (runbook) và dashboard theo dõi phễu chuyển đổi.
- **Phụ thuộc:** Toàn bộ Phase 1–4.
- **Tiêu chí hoàn thành:** Bằng chứng chấp thuận P0 hoàn chỉnh; đo lường thực tế đạt p95 / độ trễ sự kiện / tỷ lệ fallback; khôi phục/phát lại dữ liệu không sinh bản ghi trùng lặp; toàn bộ cảnh báo còn lại có người phụ trách và thời hạn hoàn tất.

**Tổng thời gian ước tính:** Khoảng **5–7 tuần** cho các lát cắt dọc từ P0 → P1 → P2, chưa bao gồm thời gian chờ duyệt hợp đồng từ các nhóm parent, cấp phát hạ tầng hoặc phê duyệt tài khoản nhà cung cấp dịch vụ AI.

## 8. Ma trận truy xuất nguồn gốc (Traceability matrix)

| Yêu cầu nghiệp vụ | Cấu phần kiến trúc sở hữu | Bằng chứng hợp đồng | Bằng chứng nghiệm thu |
|---|---|---|---|
| Gợi ý luật kết hợp P0 theo giỏ hàng | Rule Engine + Recommendation API | Request/Response gợi ý, các phiên bản luật, chữ ký giỏ hàng | Luật mẫu, giỏ hàng nhiều món, xếp hạng tất định, phản hồi rỗng khi không có luật |
| P0 loại trừ món đã có / hết hàng / tắt | Eligibility Projection + Order thẩm định cuối | Sự kiện nguồn Catalog/Inventory, watermark hình chiếu, ảnh chụp ứng viên | Các fixture: ngừng bán / sai chi nhánh / hết hàng / đã có trong giỏ / hình chiếu trễ |
| Sự kiện P0 `SuggestionShown` | Session/Interaction Writer + Outbox | Endpoint `/shown`, tính lũy nghiệm tương tác, sự kiện `SuggestionShown.v1` | Lệnh gọi chỉ sau khi render, phát lại trùng lặp, không phát sự kiện trước khi render |
| Sự kiện P0 `SuggestionAccepted` | Order attribution + Interaction Writer | Endpoint `/accept`, bằng chứng thêm dòng, sự kiện `SuggestionAccepted.v1` | Thêm dòng thất bại/thành công, xác minh có thẩm quyền, xử lý trùng lặp / thử lại |
| P1 cá nhân hóa ALS theo SĐT | Loyalty opaque context + Model Runtime | Ngữ cảnh đặc trưng Loyalty, hình chiếu đơn đã thanh toán | Ranh giới không có SĐT, khách hàng đủ/không đủ điều kiện/thưa thớt, cách ly tenant |
| Huấn luyện lại ALS ban đêm P1 | Scheduler + ALS Worker + Registry | Vòng đời lần chạy / ảnh chụp / mô hình | Đầu vào chỉ từ đơn thanh toán, cổng chất lượng, kích hoạt CAS, rollback, bảo toàn mô hình cũ |
| Khởi động nguội DeepSeek P2 | Async Adapter + Review Queue | Tác vụ cold-start / ứng viên / chính sách nhà cung cấp | Lỗi timeout/rate-limit/schema, xét duyệt của con người, không phụ thuộc lúc realtime |
| Mở rộng ngữ cảnh P2 | Điểm mở rộng chính sách xếp hạng | Các trường ngữ cảnh có phiên bản, feature flag | Thiếu dữ liệu khung giờ/thời tiết hoặc tắt tính năng không làm thay đổi tính đúng đắn của P0 |

## 9. Sơ đồ tuần tự (Sequence diagrams)

- **Trạng thái:** Đã sinh và xác nhận hoàn tất.
- **Tài liệu:** [`2026-10-13-sequence-diagrams.adoc`](file:///d:/vibe-code/pos/docs/designs-vi/recommendation/2026-10-13-sequence-diagrams.adoc)
- **Độ bao phủ:** 14 khối PlantUML, đầy đủ cấp độ L1/L2 cho các luồng: gợi ý giỏ hàng, hiển thị/chấp thuận, hình chiếu trễ, fallback ALS, huấn luyện/kích hoạt đêm, rollback/phát lại mô hình và khởi động nguội DeepSeek.
- **Ranh giới thiết kế:** Các sơ đồ bám sát hợp đồng kỹ thuật; repository hiện chưa có mã nguồn triển khai nên đây là sơ đồ thiết kế, chưa phải là trace lúc runtime.

## 10. Các điều chỉnh hợp đồng parent bắt buộc trước khi lập trình

- **POS Platform:** Thay thế phần tóm tắt sơ khai của Recommendation bằng hợp đồng chi tiết do Recommendation sở hữu; bổ sung schema cho vòng đời luật/mô hình và các sự kiện tương tác.
- **Order:** Bổ sung hợp đồng đọc nội bộ `recommendation-context` và cơ chế quy kết / xác thực thêm dòng có thẩm quyền cho gợi ý; bảo đảm Order giữ quyền sở hữu giá, tồn kho và commit dòng đơn hàng cuối cùng.
- **Catalog và Inventory:** Bổ sung phiên bản nguồn, phạm vi chi nhánh, mốc thời gian phát sinh/quan sát và cơ chế phát lại/checkpoint phục vụ các hình chiếu của Recommendation.
- **Loyalty:** Bổ sung hợp đồng tra cứu / sự kiện ngữ cảnh đặc trưng gợi ý dạng opaque kèm tính đủ điều kiện, phiên bản đồng thuận, phiên bản đặc trưng và ngữ nghĩa tombstone; tuyệt đối không để lộ số điện thoại plaintext.
- **Reporting và Audit:** Bổ sung các fixture consumer cho các sự kiện hiển thị / chấp thuận / luật / mô hình / cold-start và phân định rõ giữa quy kết phân tích đo lường với doanh thu thanh toán thực tế.

## 11. Các bước tiếp theo (Next steps)

1. Tạo danh sách công việc triển khai (backlog) chi tiết từ lộ trình Phase 0–5.
2. Chốt và công bố điều chỉnh bổ sung đối với Order về bằng chứng chấp thuận có thẩm quyền.
3. Tách bạch quyền `rule:read` và hoàn thiện các fixture chính sách phân quyền.
4. Cụ thể hóa các artifact máy đọc OpenAPI / AsyncAPI / Flyway; tuyệt đối không lập trình controller/DTO/trainer/migration trực tiếp từ văn bản mô tả.
5. Triển khai lát cắt dọc P0 trước; giữ ALS và DeepSeek hoàn toàn ngoài luồng xử lý quan trọng của quầy POS.
6. Phân công người phụ trách và thời hạn hoàn thành cho toàn bộ các cảnh báo trước khi rollout chính thức ra production.

## 12. Danh mục kiểm tra cuối cùng (Final checklist)

- [x] Tầm nhìn (Vision) đã được duyệt và lưu trữ.
- [x] Yêu cầu nghiệp vụ (Requirements) đã được duyệt có cảnh báo và có thể truy xuất nguồn gốc.
- [x] Kiến trúc hệ thống (Architecture) đã được duyệt và nhất quán với tài liệu yêu cầu.
- [x] Hợp đồng API & Dữ liệu (Contracts) đã được duyệt có cảnh báo; đánh giá hợp đồng đã hoàn tất.
- [x] Các sơ đồ tuần tự L1/L2 đã được sinh đầy đủ và người dùng đã xác nhận.
- [x] Toàn bộ yêu cầu P0/P1/P2 được ánh xạ chuẩn xác tới cấu phần kiến trúc, hợp đồng và bằng chứng nghiệm thu.
- [x] Cơ chế phân lập tenant, ranh giới khách hàng opaque, outbox/inbox, tính lũy nghiệm, fallback và rollback đã được bao quát toàn diện.
- [x] Lộ trình triển khai có phân chia giai đoạn, phụ thuộc và tiêu chí hoàn thành rõ ràng.
- [ ] Các artifact máy đọc thực thi được OpenAPI / AsyncAPI / Flyway được cụ thể hóa.
- [ ] Các điều chỉnh bổ sung cho Order / Loyalty / Catalog / Inventory parent được công bố chính thức.
- [ ] Các chỉ số đo lường hiệu năng, phát lại, kiểm thử bảo mật phủ định và lỗi nhà cung cấp được thu thập đầy đủ trong quá trình triển khai.

Thiết kế Module 8 chính thức được đánh giá là **APPROVED WITH WARNINGS** để chuyển sang giai đoạn lập kế hoạch triển khai mã nguồn. Tuyệt đối không viết code trực tiếp từ các giả định cũ trong tài liệu parent ban đầu; bắt buộc sử dụng Hợp đồng Recommendation và các cổng triển khai tại §6 làm nguồn chuẩn mực.
