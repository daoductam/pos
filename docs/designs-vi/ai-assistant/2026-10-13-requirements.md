Started at: 2026/10/13 00:00:00
Finished at: 2026/10/13 00:00:00
Total time: 0 minutes
---

# Module 9 — Trợ lý AI cho chủ quán: Yêu cầu nghiệp vụ (Requirements)

**Ngày:** 2026-10-13  
**Trạng thái:** Approved with warnings  
**Vision:** [AI Assistant Vision](./2026-10-13-vision.md)  
**Vision review:** [AI Assistant Vision Review](./2026-10-13-vision-review.md)  
**Phạm vi:** M — Standard Service Flow

## 1. Mục tiêu và ranh giới (Boundary)

AI Assistant Service cung cấp tính năng chat chỉ đọc (read-only) cho Chủ quán (Owner)/Quản lý (Manager) và tóm tắt cuối ngày (daily summary) P2. Service sử dụng cơ chế DeepSeek function calling nhưng không phải là nguồn lưu trữ dữ liệu báo cáo:

- Reporting read model sở hữu các chỉ số (metric), dữ liệu tổng hợp (aggregate), ngày kinh doanh (business-date), múi giờ chi nhánh (branch timezone), độ tươi mới (freshness) và các sự kiện đóng góp doanh thu (contribution facts).
- Identity/Cognito sở hữu tenant, vai trò (role) và quan hệ thành viên chi nhánh (branch membership).
- AI Assistant sở hữu trạng thái hội thoại/lượt hỏi đáp (conversation/turn state), chính sách công cụ (tool policy), nhật ký kiểm toán đã che dấu thông tin nhạy cảm (redacted audit), chính sách nhà cung cấp LLM (provider policy) và trạng thái hiển thị tóm tắt (summary presentation state).
- DeepSeek chỉ tiếp nhận prompt đã được che chắn dữ liệu nhạy cảm (redacted) và dữ liệu tổng hợp từ kết quả công cụ (tool result aggregate); không được phép đọc cơ sở dữ liệu trực tiếp hoặc gọi các lệnh làm thay đổi trạng thái nghiệp vụ (domain mutation).

### Mức độ ưu tiên (Priority)

- **P1:** Hỏi đáp báo cáo số liệu bằng ngôn ngữ tự nhiên trong trang quản trị (dashboard) của Owner/Manager.
- **P2:** Tóm tắt cuối ngày theo ngày kinh doanh/múi giờ của chi nhánh, kèm theo dữ liệu mốc so sánh (baseline) và các yếu tố đóng góp (contribution drivers).
- P1/P2 tuyệt đối không làm thay đổi trạng thái nghiệp vụ cốt lõi P0 của order/payment/inventory/loyalty/catalog/recommendation.

## 2. Tác nhân và tích hợp hệ thống (Actors & Integrations)

| Tác nhân / Hệ thống | Quyền hạn / Vai trò |
|---|---|
| Owner (Chủ quán) | Chat toàn tenant hoặc trong phạm vi một/nhóm chi nhánh thuộc tenant; xem/chạy lại (rerun) tóm tắt trong phạm vi phân quyền. |
| Manager (Quản lý) | Chat và xem tóm tắt chỉ trong phạm vi các chi nhánh có hiệu lực phân quyền thành viên. |
| Thu ngân / Bếp / Kiểm toán viên (Cashier/Kitchen/Auditor) | Không được phép dùng chat trợ lý trong MVP; Kiểm toán viên (Audit) chỉ tiếp nhận bằng chứng kiểm toán (evidence). |
| Dashboard Owner/Manager | Gửi câu hỏi, lựa chọn phạm vi phân quyền, hiển thị câu trả lời/nguồn gốc dữ liệu (provenance)/độ tươi mới (freshness)/từ chối (refusal) và báo cáo tóm tắt hàng ngày. |
| Identity/Cognito | Cung cấp JWT, thông tin tenant, role, quan hệ thành viên chi nhánh; là nguồn xác thực quyền phía máy chủ (server-side authorization source). |
| Reporting Service | Cung cấp các công cụ nội bộ chỉ đọc (read-only tools) cho KPI và phân tích đóng góp; không tiếp nhận câu lệnh SQL tùy ý. |
| Order/Payment/Loyalty/Recommendation | Đẩy các sự kiện dữ liệu (facts) sang Reporting; AI Assistant không bao giờ gọi mutation hay đọc DB trực tiếp. |
| DeepSeek adapter | Lập kế hoạch gọi hàm (function planning) và hiển thị ngôn ngữ tự nhiên (natural-language rendering); lỗi của nhà cung cấp LLM không làm sai lệch trạng thái domain. |
| Scheduler (Bộ lập lịch) | Tạo tác vụ tóm tắt hàng ngày (daily-summary job) theo múi giờ kinh doanh và giờ chốt ca/ngày của từng chi nhánh. |
| Audit/Observability | Tiếp nhận các sự kiện kiểm toán/gọi công cụ đã được che thông tin nhạy cảm cùng metrics/traces. |

## 3. Định nghĩa và bất biến nghiệp vụ (Definitions & Invariants)

### 3.1 Phạm vi đã giải quyết (Resolved Scope)

`ResolvedScope` là đối tượng do máy chủ tạo ra (server-generated object) bao gồm `tenantId`, `branchIds[]`, `role`, `scopeReason`, `authorizationVersion` và `resolvedAt`. Client hoặc mô hình LLM tuyệt đối không được phép tự tạo hoặc tự ý mở rộng phạm vi của đối tượng này.

- Owner có thể chọn phạm vi toàn chuỗi `TENANT` hoặc một danh sách các chi nhánh thuộc tenant đó.
- Manager chỉ có thể chọn tập con trong danh sách các chi nhánh mà mình đang là thành viên hợp lệ.
- Mỗi lượt tương tác (turn) sẽ lưu một bản chụp (snapshot) của `ResolvedScope`; việc đổi chi nhánh phải tạo ra một phiên bản phạm vi mới (scope revision) và không được dùng lại scope cũ cho lượt gọi tool kế tiếp.
- Trường hợp phạm vi rỗng, chi nhánh ngoài quyền, chi nhánh đã hết hiệu lực thành viên hoặc sai lệch tenant phải trả về lỗi hoặc yêu cầu làm rõ, không được tự ý suy đoán dữ liệu.

### 3.2 Kỳ kinh doanh (Business Period)

- Yêu cầu nhận giá trị `from`/`to` theo chuẩn định dạng ngày hoặc ngày giờ ISO, tuy nhiên máy chủ sẽ chuẩn hóa theo múi giờ IANA của chi nhánh tương ứng.
- Truy vấn liên chi nhánh (cross-branch query) bắt buộc phải tuân theo chính sách múi giờ rõ ràng: mặc định áp dụng múi giờ báo cáo của tenant; chỉ so sánh theo giờ địa phương của từng chi nhánh khi công cụ hỗ trợ rõ ràng.
- Múi giờ của trình duyệt (Browser timezone) tuyệt đối không được dùng làm căn cứ thẩm quyền.
- `businessDate` (ngày kinh doanh) và `businessDayCutoff` (giờ chốt ngày kinh doanh) do chính sách của Reporting/chi nhánh cung cấp.

### 3.3 Độ tươi mới và tính đầy đủ (Freshness & Completeness)

Mỗi kết quả trả về từ công cụ (tool result) bắt buộc phải có các trường:

- `sourceName` và phiên bản dữ liệu chiếu/mốc kiểm chứng (`projectionVersion`/`watermark`);
- `lastUpdatedAt` tính theo chuẩn UTC;
- `freshnessStatus`: `FRESH` (mới nhất), `STALE` (chậm trễ), `INCOMPLETE` (chưa đủ), `UNAVAILABLE` (không khả dụng);
- `completenessStatus` và danh sách nguồn còn thiếu `missingSources[]` nếu có;
- `calculationVersion` (phiên bản công thức tính toán).

Trợ lý ảo không được phép trình bày kết quả có trạng thái `UNAVAILABLE` dưới dạng một sự thật hiển nhiên. Trạng thái `STALE`/`INCOMPLETE` bắt buộc phải được hiển thị rõ ràng trong câu trả lời; bản tóm tắt ngày không được phép phát hành nếu cổng kiểm tra tính đầy đủ (completeness gate) thất bại.

### 3.4 Bất biến chỉ đọc (Read-only Invariant)

Danh mục công cụ (tool registry) của AI Assistant chỉ được phép chứa các thao tác đọc và báo cáo (read/reporting operations). Tuyệt đối không có bất kỳ công cụ hoặc kết quả đầu ra nào từ mô hình được phép:

- tạo, chỉnh sửa hoặc hủy Đơn hàng (Order);
- khởi tạo thanh toán hoặc hoàn tiền (Payment);
- giữ chỗ, xác nhận trừ hoặc giải phóng Tồn kho (Inventory);
- chỉnh sửa Danh mục hoặc giá bán món ăn (Catalog/price);
- tích lũy hoặc đổi điểm Khách hàng thân thiết (Loyalty);
- chỉnh sửa quy tắc hoặc mô hình gợi ý (Recommendation);
- chỉnh sửa danh tính, thông tin chi nhánh, nhân viên hoặc quyền hạn (Identity/branch/employee/permission).

## 4. Yêu cầu chức năng P1 — Hỏi đáp báo cáo bằng ngôn ngữ tự nhiên

### FR-AI-001 — Khởi tạo và tiếp tục phiên hội thoại (Start/Resume Chat Session)

Chủ quán (Owner)/Quản lý (Manager) có thể mở phiên chat từ màn hình dashboard. Dịch vụ phải đảm bảo:

1. xác thực Cognito JWT và giải quyết chính xác thông tin tenant/role/quan hệ thành viên chi nhánh;
2. tiếp nhận `requestedScope` nhưng chỉ coi đây là tham số đầu vào cần phải kiểm tra tính hợp lệ;
3. tạo định danh hội thoại `conversationId` và định danh lượt hỏi `turnId` theo chuẩn UUID v7;
4. gắn kèm phiên bản chính sách phiên, chính sách mô hình LLM và chính sách che dấu dữ liệu nhạy cảm;
5. không lưu chuỗi JWT thô, số điện thoại hoặc dữ liệu định danh cá nhân (PII) vào nhật ký phiên hội thoại.

Phiên làm việc có thời hạn hết hạn cấu hình trước (TTL). Sau khi chuyển trạng thái `EXPIRED`, các lượt hỏi kế tiếp phải mở phiên mới; tuyệt đối không tái sử dụng phạm vi quyền cũ.

### FR-AI-002 — Xác định phạm vi phân quyền (Resolve Authorization Scope)

Trước khi thực hiện bất kỳ lệnh gọi công cụ nào, máy chủ phải tự xác định phạm vi phân quyền độc lập hoàn toàn với mô hình LLM. Bộ thực thi công cụ (Tool executor) nhận `ResolvedScope` từ ngữ cảnh đáng tin cậy (trusted context), không nhận `tenantId`/`branchIds` trực tiếp từ các đối số do hàm LLM truyền vào làm căn cứ thẩm quyền.

Nếu người dùng đặt câu hỏi cho “toàn bộ chi nhánh”:

- Với Owner: cho phép nếu chính sách và khoảng thời gian truy vấn hợp lệ;
- Với Manager: trả về lỗi `scope_not_allowed` hoặc yêu cầu người dùng chọn lại chi nhánh hợp lệ;
- Tuyệt đối không trả về tên hoặc số lượng chi nhánh nằm ngoài phạm vi được phép nhằm tránh nguy cơ lộ thông tin (enumeration attack).

### FR-AI-003 — Phân loại ý định và lập kế hoạch gọi công cụ (Classify Intent & Tool Plan)

DeepSeek được phép phân loại câu hỏi của người dùng vào danh mục ý định cho phép (allowlist intent):

- `SALES_SUMMARY` (Tóm tắt doanh số);
- `ORDER_COUNT` (Số lượng đơn hàng);
- `AVERAGE_ORDER_VALUE` (Giá trị trung bình mỗi đơn - AOV);
- `TOP_ITEMS` (Món bán chạy nhất);
- `BRANCH_COMPARISON` (So sánh giữa các chi nhánh);
- `HOURLY_SALES` (Doanh số theo khung giờ);
- `CONTRIBUTION_DRIVERS` (Các yếu tố đóng góp doanh số cho tóm tắt/giải trình);
- `UNSUPPORTED` (Không hỗ trợ).

Máy chủ phải kiểm tra tính hợp lệ của tên ý định/hàm và cấu trúc JSON đối số theo đúng schema định nghĩa. Mô hình không được phép:

- phát ra tên công cụ nằm ngoài danh mục cho phép;
- truyền các câu lệnh SQL, tên bảng dữ liệu, biểu thức lọc tùy ý hoặc danh sách ID thô;
- thay đổi phạm vi phân quyền, vượt quá khoảng thời gian tối đa, giới hạn kết quả, loại tiền tệ hoặc các trường chính sách;
- gọi vượt quá số lần công cụ tối đa cho phép trong một lượt (`maxToolCallsPerTurn`, mặc định là 3).

Các trường hợp `UNSUPPORTED`, phát hiện cố tình tấn công bằng prompt (prompt injection) hoặc vi phạm chính sách sẽ lập tức trả về thông báo từ chối an toàn và không gọi sang dịch vụ Reporting.

### FR-AI-004 — Danh mục công cụ báo cáo được phép (Allowlisted Reporting Tools)

Dịch vụ Reporting phải cung cấp các giao ước công cụ chỉ đọc nội bộ (internal read-only tool contracts) sau:

| Công cụ | Tham số đầu vào tối thiểu | Dữ liệu đầu ra tối thiểu |
|---|---|---|
| `getSalesSummary` | resolved scope, kỳ kinh doanh, chính sách so sánh | số lượng đơn hàng, doanh thu gross/net, thuế (nếu được phép), đơn vị tiền tệ, chênh lệch so với mốc (baseline delta), độ tươi mới, phiên bản tính toán |
| `getOrderCount` | resolved scope, kỳ kinh doanh | số lượng đơn, chênh lệch so với mốc, độ tươi mới |
| `getAverageOrderValue` | resolved scope, kỳ kinh doanh | giá trị AOV, mẫu số lượng đơn hợp lệ, loại tiền tệ, chênh lệch so với mốc, phiên bản quy tắc làm tròn |
| `getTopItems` | resolved scope, kỳ kinh doanh, giới hạn limit ≤ 10, danh mục sắp xếp cho phép | nhãn an toàn của món (item safe label), số lượng bán, doanh thu tổng hợp, thứ hạng, độ tươi mới |
| `compareBranches` | phạm vi Owner, kỳ kinh doanh, danh sách chi nhánh được chọn hoặc toàn bộ chi nhánh được phép | nhãn an toàn của chi nhánh, chỉ số KPI tổng hợp, thứ hạng/chênh lệch; tuyệt đối không tiết lộ sự tồn tại của chi nhánh không có quyền |
| `getHourlySales` | resolved branch scope, chính sách kỳ tối đa, khung giờ (hour bucket) | số liệu tổng hợp từng giờ, múi giờ, số đơn/doanh thu, độ tươi mới |
| `getContributionDrivers` | phạm vi/kỳ mục tiêu, mốc so sánh baseline, danh mục chiều phân tích cho phép | danh sách top-N yếu tố đóng góp tích cực/tiêu cực, giá trị đóng góp/tỷ lệ phần trăm, ngưỡng trọng yếu (materiality), phiên bản tính toán |

Dữ liệu trả về từ các công cụ không được chứa số điện thoại/tên khách hàng, thông tin cá nhân của nhân viên, ID đơn hàng/khách hàng/thanh toán dạng thô hoặc chi tiết từng dòng hóa đơn thô.

### FR-AI-005 — Rào chắn bảo vệ khi thực thi công cụ (Tool Execution Guardrails)

Tool Gateway phải bắt buộc áp dụng:

- điều kiện lọc theo tenant (tenant predicate) và chi nhánh (branch predicate) tại ranh giới tầng ứng dụng và tầng truy xuất dữ liệu (repository boundary);
- giới hạn khoảng thời gian tối đa theo từng công cụ, mặc định tối đa 31 ngày cho các truy vấn tương tác;
- kích thước kết quả tối đa và số lượng phần tử top-N;
- chỉ cho phép các chiều phân tích và trường sắp xếp nằm trong danh mục trắng;
- thời gian chờ truy vấn (query timeout), hạn chót cho toàn bộ lượt hỏi (total turn deadline) và cơ chế ngắt mạch tự động (circuit breaker);
- kiểm tra tính hợp lệ của schema phản hồi (response schema validation);
- chính sách về độ tươi mới/tính đầy đủ của dữ liệu;
- kiểm tra và che dấu dữ liệu nhạy cảm (redaction assertion) trước khi đưa vào prompt, log hoặc hệ thống kiểm toán.

Truy vấn Reporting tuyệt đối không được thực thi thông qua câu lệnh SQL do LLM sinh ra. Khi công cụ bị quá thời gian chờ hoặc schema không hợp lệ, hệ thống phải trả về lỗi nghiệp vụ rõ ràng (typed tool error) và không được retry vô hạn.

### FR-AI-006 — Tổng hợp câu trả lời kèm nguồn gốc dữ liệu (Assemble Answer with Provenance)

Câu trả lời phải được định dạng dưới dạng cấu trúc chuẩn (structured response) trước khi sinh văn bản tự nhiên:

```json
{
  "status": "ANSWERED",
  "text": "Doanh thu chi nhánh A tuần này là ...",
  "scope": {"type": "BRANCH", "branchIds": ["..."]},
  "period": {"from": "2026-10-05", "to": "2026-10-11", "timezone": "Asia/Ho_Chi_Minh"},
  "sources": [
    {"tool": "getSalesSummary", "watermark": "sales:v901", "lastUpdatedAt": "2026-10-12T17:00:00Z"}
  ],
  "calculationVersion": "sales-metric-v2",
  "warnings": []
}
```

LLM chỉ được phép tạo câu chữ dựa hoàn toàn trên các sự kiện có cấu trúc này. Nếu mô hình sinh ra nội dung mâu thuẫn với số liệu cấu trúc, máy chủ phải từ chối/yêu cầu sinh lại trong giới hạn số lần cho phép hoặc trả về nguyên văn dữ liệu cấu trúc kèm lỗi; không bao giờ xem nội dung văn bản tự sinh của LLM là nguồn chân lý.

### FR-AI-007 — Trạng thái từ chối, làm rõ và kết quả từng phần (Refusal, Clarification & Partial Result)

Các trạng thái hiển thị tới người dùng:

- `ANSWERED`: Dữ liệu đầy đủ và hoàn toàn hợp lệ;
- `NEEDS_SCOPE`: Cần người dùng làm rõ việc chọn chi nhánh hoặc khoảng thời gian;
- `UNSUPPORTED`: Câu hỏi nằm ngoài danh mục hỗ trợ;
- `NO_DATA`: Không có dữ liệu trong khoảng thời gian hợp lệ đã chọn;
- `STALE_DATA`: Có dữ liệu nhưng độ tươi mới không đáp ứng chính sách yêu cầu;
- `PARTIAL`: Một số công cụ trả dữ liệu hợp lệ, phần còn lại không khả dụng; bắt buộc phải chỉ rõ các nguồn bị thiếu;
- `PROVIDER_UNAVAILABLE`: Mô hình DeepSeek không phản hồi nhưng không đủ dữ liệu cấu trúc để tự kết luận;
- `FAILED`: Xảy ra lỗi hệ thống nội bộ kèm mã lỗi an toàn.

Tuyệt đối không sử dụng bất kỳ trạng thái nào để làm lộ sự tồn tại của tài nguyên thuộc tenant khác.

### FR-AI-008 — Hỏi tiếp nối trong hội thoại (Conversation Follow-up)

Câu hỏi tiếp nối chỉ được phép tham chiếu đến khoảng thời gian/phạm vi trước đó khi phiên chat vẫn còn hiệu lực và máy chủ lưu giữ phiên bản phạm vi bất biến (immutable scope revision). Nếu câu hỏi mới có sự thay đổi về chi nhánh hoặc thời gian, máy chủ sẽ tạo một revision mới và hiển thị rõ phạm vi mới đã được giải quyết.

Mô hình LLM không được tự ý kế thừa phạm vi từ lịch sử chat nếu người dùng đã bị thu hồi quyền; mỗi lượt hỏi đáp (every turn) đều phải thực hiện xác thực lại quyền thành viên.

### FR-AI-009 — Kiểm toán đã che dữ liệu nhạy cảm (Redacted Audit)

Mỗi lượt hỏi đáp phải tạo ra một bản ghi bằng chứng kiểm toán theo cơ chế chỉ ghi thêm (append-only):

- ID/vai trò của tác nhân, phạm vi tenant/chi nhánh, phiên bản phạm vi;
- câu hỏi đã che dấu thông tin nhạy cảm và ý định đã chuẩn hóa;
- tên công cụ/đối số đã kiểm tra hợp lệ, quyết định chính sách, trạng thái kết quả công cụ;
- mốc kiểm chứng nguồn/độ tươi mới/phiên bản công thức tính;
- phiên bản nhà cung cấp/mô hình/chính sách prompt;
- trạng thái/mã băm phản hồi, mã từ chối/lỗi, độ trễ, correlation ID và causation ID.

Mặc định không lưu trữ nội dung prompt hoặc phản hồi thô. Trường hợp bật tính năng lấy mẫu để gỡ lỗi (debug sampling), bắt buộc phải có cờ tính năng (feature flag), kiểm tra kỹ khâu che dấu dữ liệu nhạy cảm trước khi ghi, thời gian lưu trữ ngắn và phân quyền truy cập nhật ký kiểm toán riêng biệt.

### FR-AI-010 — Kiểm soát tần suất, ngân sách và chi phí (Rate/Budget/Cost Controls)

Chính sách áp dụng trên từng người dùng/tenant phải giới hạn:

- số lượng yêu cầu mỗi phút (requests per minute);
- số phiên/lượt hỏi đồng thời (concurrent sessions/turns);
- tổng số lượt gọi công cụ trong một lượt hỏi;
- số lượng token đầu vào/đầu ra hoặc ngân sách chi phí nhà cung cấp LLM;
- khoảng thời gian truy vấn tối đa và kích thước kết quả trả về;
- số lần yêu cầu tạo lại báo cáo tóm tắt ngày (daily summary reruns).

Khi vượt quá giới hạn, hệ thống trả về mã `429`/`budget_exceeded` và không thực hiện thêm cuộc gọi nào tới nhà cung cấp LLM hoặc dịch vụ công cụ.

## 5. Yêu cầu chức năng P2 — Báo cáo tóm tắt hàng ngày (Daily Summary)

### FR-AI-201 — Lập lịch tác vụ tóm tắt ngày cho chi nhánh (Schedule Branch Daily-Summary Job)

Bộ lập lịch (Scheduler) sẽ tạo một tác vụ cho mỗi chi nhánh đủ điều kiện tương ứng với mỗi ngày kinh doanh sau giờ chốt ca/ngày (cutoff) dựa theo múi giờ IANA của chi nhánh. Khóa duy nhất của tác vụ (Job key):

```text
(tenantId, branchId, businessDate, summaryPolicyVersion)
```

Tác vụ chỉ được phép chuyển sang trạng thái `READY` khi mốc kiểm chứng tính đầy đủ (completeness watermark) của Reporting đạt chuẩn chính sách quy định. Nếu chưa đạt, tác vụ chuyển sang `WAITING_FOR_DATA`, thực hiện retry có giới hạn hoặc chờ vận hành viên kích hoạt chạy lại; tuyệt đối không công bố bản tóm tắt thiếu số liệu như một bản báo cáo hoàn chỉnh.

### FR-AI-202 — Chính sách mốc so sánh (Baseline Policy)

Thứ tự ưu tiên lựa chọn mốc so sánh mặc định:

1. cùng thứ của tuần trước đó trong cùng chi nhánh và múi giờ kinh doanh;
2. nếu thiếu hoặc không thể so sánh, chuyển sang phương án phụ là tính trung bình của 7 ngày kinh doanh gần nhất;
3. nếu vẫn thiếu dữ liệu, gán trạng thái `NO_BASELINE`, bản tóm tắt vẫn có thể hiển thị số liệu thực tế đã diễn ra nhưng không kèm theo tỷ lệ phần trăm thay đổi.

Bản tóm tắt bắt buộc phải nêu rõ `baselineType`, khoảng thời gian mốc so sánh, số lượng điểm dữ liệu đã dùng và phiên bản chính sách.

### FR-AI-203 — Phân tích đóng góp tất định (Deterministic Contribution Analysis)

Tầng công cụ/Reporting thực hiện tính toán đóng góp dựa theo danh mục chiều phân tích được cho phép:

- nhãn an toàn của món ăn / nhóm hàng (item/category safe label);
- khung giờ (hour bucket);
- chi nhánh (áp dụng khi phạm vi quyền Owner bao quát nhiều chi nhánh).

Kết quả trả về phải thể hiện rõ giá trị thực tế (actual), mốc so sánh (baseline), mức chênh lệch có dấu (signed delta), số tiền đóng góp / tỷ lệ phần trăm đóng góp, ngưỡng trọng yếu (materiality threshold) và phiên bản tính toán. Nếu dữ liệu không đủ bằng chứng quy kết, chính sách ngôn từ chỉ được sử dụng các cụm từ “yếu tố liên quan” hoặc “đóng góp quan sát được”, tuyệt đối không dùng từ “nguyên nhân chắc chắn”.

### FR-AI-204 — Sinh nội dung tóm tắt (Generate Summary)

Mô hình LLM tiếp nhận dữ liệu thực tế hàng ngày có cấu trúc đã được che chắn dữ liệu nhạy cảm và tiến hành hiển thị văn bản tóm tắt theo schema:

- tiêu đề chính (headline);
- thực tế so với mốc so sánh (actual vs baseline);
- các yếu tố đóng góp tích cực và tiêu cực hàng đầu (top positive/negative factors);
- độ tươi mới và tính đầy đủ của dữ liệu;
- các lưu ý và giới hạn của số liệu (caveats/limitations);
- nguồn dữ liệu và xuất xứ công cụ (source/tool provenance).

Trường hợp mô hình LLM không khả dụng hoặc định dạng đầu ra không hợp lệ, dịch vụ sẽ lưu lại bản tóm tắt có cấu trúc mặc định hoặc chuyển sang trạng thái `PROVIDER_UNAVAILABLE`; tuyệt đối không được tự bịa ra nội dung văn bản.

### FR-AI-205 — Xem, chạy lại và tính lũy quyền (View, Rerun & Idempotency)

Chủ quán (Owner)/Quản lý (Manager) chỉ có thể xem tóm tắt trong phạm vi chi nhánh được phân quyền. Thao tác chạy lại (rerun) bắt buộc:

- phải gửi kèm khóa lũy quyền (idempotency key);
- sử dụng cùng ngày kinh doanh/phiên bản chính sách hoặc tạo một phiên bản hiệu chỉnh tường minh (explicit revision);
- không tạo bản ghi tóm tắt bị trùng lặp hoặc sự kiện kiểm toán lặp lại;
- bảo toàn đầy đủ vết phả hệ (lineage) tới các mốc nguồn, mô hình và chính sách prompt tương ứng.

Phiên bản tóm tắt cũ không bị xóa vật lý trong cơ sở dữ liệu; trạng thái của nó được chuyển thành `SUPERSEDED` khi lượt chạy lại hoàn thành hợp lệ.

## 6. Máy trạng thái (State Machines)

### 6.1 Hội thoại và lượt hỏi đáp (Conversation & Turn)

`CREATED → ACTIVE → ANSWERED | NEEDS_SCOPE | UNSUPPORTED | NO_DATA | STALE_DATA | PARTIAL | PROVIDER_UNAVAILABLE | FAILED → EXPIRED`

Khi lượt hỏi đáp (turn) đã rơi vào một trạng thái kết thúc (terminal state), không được phép dùng lại turn ID đó để gọi lại công cụ hoặc mô hình LLM; phía client nếu muốn thử lại phải thực hiện gửi lại lũy quyền (idempotent replay) hoặc tạo một turn mới theo quy định.

### 6.2 Thực thi công cụ (Tool Execution)

`PLANNED → VALIDATED → RUNNING → SUCCEEDED | EMPTY | STALE | INCOMPLETE | TIMED_OUT | REJECTED | FAILED`

Trạng thái `REJECTED` xảy ra ngay trước khi gọi sang Reporting nếu bước kiểm tra quyền, đối số hoặc chính sách bị thất bại.

### 6.3 Tác vụ tóm tắt hàng ngày (Daily Summary)

`SCHEDULED → WAITING_FOR_DATA → READY | DATA_LATE → GENERATING → PUBLISHED | NO_BASELINE | PROVIDER_UNAVAILABLE | FAILED | SUPERSEDED`

`DATA_LATE` là trạng thái kết thúc của lần chạy hiện tại khi đã vượt quá thời gian chờ tối đa; chỉ có thao tác chạy lại kèm mốc nguồn mới hoặc khóa lũy quyền mới được phép chuyển tác vụ sang `READY`. Không được chuyển trạng thái sang `PUBLISHED` nếu thiếu các dữ kiện bắt buộc hoặc kết quả không đúng schema; trạng thái `NO_BASELINE` chỉ hợp lệ khi dữ liệu thực tế đầy đủ và chính sách cho phép công bố không kèm so sánh phần trăm.

## 7. Yêu cầu giao diện lập trình (API Requirements)

### 7.1 Public Dashboard API

| Phương thức | Đường dẫn | Phân quyền / Phạm vi | Mục đích | Trạng thái phản hồi |
|---|---|---|---|---|
| POST | `/api/v1/ai/conversations` | Owner/Manager | Mở hội thoại + giải quyết phạm vi quyền | 201, 401, 403, 422 |
| GET | `/api/v1/ai/conversations/{conversationId}` | Owner/Manager sở hữu/phạm vi | Lấy trạng thái phiên an toàn | 200, 403, 404 |
| POST | `/api/v1/ai/conversations/{conversationId}/turns` | Owner/Manager | Gửi một câu hỏi chỉ đọc | 200/202, 403, 409, 422, 429, 503 |
| GET | `/api/v1/ai/conversations/{conversationId}/turns/{turnId}` | Owner/Manager sở hữu/phạm vi | Lấy kết quả turn/câu trả lời/nguồn gốc | 200, 403, 404 |
| GET | `/api/v1/ai/daily-summaries` | Owner/Manager | Danh sách tóm tắt theo scope/ngày | 200, 403, 422 |
| GET | `/api/v1/ai/daily-summaries/{summaryId}` | Owner/Manager sở hữu/phạm vi | Xem nội dung tóm tắt + nguồn phả hệ | 200, 403, 404 |
| POST | `/api/v1/ai/daily-summaries/{summaryId}/reruns` | Owner/Manager phạm vi cho phép | Yêu cầu chạy lại tóm tắt | 202, 403, 404, 409, 429 |

API công khai tuyệt đối không làm lộ danh mục công cụ, nội dung prompt gửi mô hình hay các chính sách nội bộ của hệ thống. Truy vấn danh sách sử dụng cơ chế phân trang theo con trỏ (cursor pagination), mặc định 20 bản ghi, tối đa 100 bản ghi.

### 7.2 Internal Contracts

| Phương thức | Đường dẫn | Bên gọi | Mục đích |
|---|---|---|---|
| POST | `/internal/v1/reporting/ai-tools/{toolName}` | AI Assistant workload | Thực thi một công cụ tổng hợp trong danh mục cho phép kèm theo `ResolvedScope` đáng tin cậy |
| GET | `/internal/v1/reporting/freshness` | AI Assistant/Scheduler | Đọc độ tươi mới/tính đầy đủ của dữ liệu chiếu theo phạm vi/ngày |
| POST | `/internal/v1/ai/summary-jobs` | Scheduler/Vận hành | Đưa tác vụ tóm tắt ngày vào hàng đợi xử lý lũy quyền |
| POST | `/internal/v1/ai/summary-jobs/{jobId}/run` | Worker | Thực thi tác vụ tóm tắt ngày đã được xác thực |
| POST | `/internal/v1/ai/provider-health` | Vận hành/Worker | Kiểm tra sức khỏe kết nối/cấu hình nhà cung cấp an toàn; không bao giờ gửi PII |

API nội bộ bắt buộc phải sử dụng định danh tải công việc (workload identity) hoặc mTLS; khóa bí mật của nhà cung cấp LLM chỉ được lưu giữ trong ranh giới adapter/secret.

## 8. Yêu cầu về sự kiện và tích hợp (Event & Integration Requirements)

### Dữ kiện/Giao ước tiếp nhận (Consumed Facts/Contracts)

AI Assistant không tiêu thụ trực tiếp các sự kiện nghiệp vụ thô (raw domain events) để tính toán số liệu trong phạm vi P1; dịch vụ Reporting đóng vai trò là nguồn dữ liệu duy nhất. Cần một trong hai giao ước sau:

1. Giao diện công cụ Reporting nội bộ đồng bộ kèm endpoint kiểm tra độ tươi mới; hoặc
2. Các sự kiện có phiên bản `ReportProjectionWatermarkUpdated.v1` / `BusinessDayReady.v1` để kích hoạt hoặc chặn các tác vụ.

Payload của sự kiện tuyệt đối không chứa thông tin định danh cá nhân (PII) hoặc chi tiết từng dòng đơn hàng thô.

### Các sự kiện phát ra (Produced Events)

| Sự kiện | Payload tối thiểu | Thành phần nhận |
|---|---|---|
| `AIReportTurnCompleted.v1` | ID turn/hội thoại, vai trò tác nhân, tóm tắt phạm vi, trạng thái, tên công cụ, mốc kiểm chứng, mã băm phản hồi, occurredAt | Audit, Reporting/Vận hành |
| `AIDailySummaryPublished.v1` | ID tóm tắt, tenant/chi nhánh/ngày, loại mốc so sánh, mốc kiểm chứng nguồn, phiên bản chính sách/mô hình, số bản hiệu chỉnh | Dashboard/Reporting, Audit |
| `AIAccessRefused.v1` | Tóm tắt tác nhân/phạm vi, mã từ chối/lỗi, phiên bản chính sách, correlation ID | Audit/An ninh (Security) |
| `AIDailySummarySuperseded.v1` | ID tóm tắt cũ/mới, ngày kinh doanh, lý do chạy lại, phiên bản nguồn | Audit/Reporting |

Tuyệt đối không có bất kỳ sự kiện nào chứa câu hỏi thô của người dùng, prompt thô, phản hồi văn bản thô, số điện thoại, định danh khách hàng, thông tin thanh toán, khóa bí mật của nhà cung cấp hoặc các tệp mô hình.

## 9. Yêu cầu dữ liệu và tính nhất quán (Data & Consistency Requirements)

Cơ sở dữ liệu PostgreSQL của AI Assistant chỉ sở hữu:

- metadata và trạng thái an toàn của `ai_conversations` / `ai_turns`;
- metadata, trạng thái và độ trễ của các lần gọi công cụ `ai_tool_executions`;
- phả hệ và trạng thái của `ai_daily_summary_jobs` và `ai_daily_summaries`;
- tham chiếu cấu hình/chính sách phi bí mật có phiên bản `ai_provider_policies`;
- `ai_audit_references`, bảng outbox/inbox và trạng thái lũy quyền/thử lại (retry).

Reporting sở hữu các mô hình đọc `sales_daily`, `top_items`, so sánh theo giờ/chi nhánh, phân tích đóng góp và các định nghĩa số liệu liên quan. AI Assistant có thể lưu bản chụp (snapshot) kết quả tổng hợp an toàn/nguồn gốc phục vụ việc phát lại (replay), nhưng tuyệt đối không được trở thành nguồn tính toán số liệu thứ hai.

Các bất biến dữ liệu (Invariants):

1. Mọi bản ghi đều gắn chặt với phạm vi tenant; các bản ghi thuộc phạm vi chi nhánh phải mang branch ID hoặc loại phạm vi rõ ràng.
2. Mỗi lượt hỏi/thực thi công cụ phải lưu phiên bản phạm vi đã giải quyết và không được phép mở rộng sau khi đã chạy.
3. Chỉ tồn tại một kết quả lũy quyền duy nhất cho mỗi bộ `(tenantId, conversationId, clientTurnId)`.
4. Chỉ có một bản tóm tắt có hiệu lực duy nhất cho mỗi bộ `(tenantId, branchId, businessDate, policyVersion)`; các bản cũ bị thay thế sẽ ở trạng thái bất biến.
5. Bản tóm tắt chỉ được phép phát hành từ các dữ kiện đầy đủ hoặc dữ liệu cũ được phép và kết quả có cấu trúc hợp lệ theo schema.
6. Tuyệt đối không lưu trữ thông tin PII thô, nội dung prompt, khóa bí mật của nhà cung cấp hoặc câu lệnh SQL tùy ý.
7. Bảng Outbox được ghi cùng một giao dịch (transaction) với trạng thái cục bộ; phía nhận tự động lọc trùng theo ID sự kiện.
8. Việc thử lại khi gọi nhà cung cấp/công cụ không được tạo ra sự kiện công bố tóm tắt hoặc sự kiện kiểm toán trùng lặp.

## 10. Yêu cầu phi chức năng (Non-Functional Requirements)

> Các mục tiêu dưới đây là chỉ số mục tiêu cho giai đoạn MVP/demo trước khi tiến hành đo đạc dưới tải triển khai thực tế.

| Hạng mục | Yêu cầu | Chỉ số mục tiêu |
|---|---|---|
| Hiệu năng | Gọi công cụ Reporting nội bộ | p95 ≤ 2 giây cho các truy vấn tổng hợp tiêu chuẩn |
| Hiệu năng | Toàn bộ lượt chat từ đầu đến cuối có câu trả lời | p95 ≤ 8 giây khi DeepSeek ổn định; thời gian chờ tối đa (timeout) ≤ 10 giây |
| Hiệu năng | Xử lý phạm vi/từ chối không cần gọi mô hình LLM | p95 ≤ 500 mili-giây |
| Hiệu năng | Tóm tắt ngày sau khi dữ liệu sẵn sàng | 95% phát hành hoặc chuyển trạng thái kết thúc trong vòng 10 phút |
| An toàn công cụ | Số lần gọi công cụ mỗi lượt | Mặc định tối đa 3 lần, chỉ được phép cấu hình giảm đối với luồng công khai |
| An toàn truy vấn | Khoảng thời gian tương tác | Tối đa 31 ngày kinh doanh; chính sách liên chi nhánh của Owner có thể giảm bớt |
| Tính sẵn sàng | Cách ly khi nhà cung cấp LLM gặp sự cố | Không làm sai lệch domain hoặc hỏng dữ liệu Reporting; trả về cấu trúc lỗi/số liệu an toàn |
| An ninh | Thẩm quyền truy cập | 100% lượt gọi công cụ phải mang phạm vi tenant/chi nhánh do máy chủ giải quyết; vượt qua kiểm thử âm tính chống IDOR |
| Quyền riêng tư | Che giấu dữ liệu (Redaction) | Không có số điện thoại/tên khách hàng/ID giao dịch thô/thông tin thanh toán/bí mật trong prompt/log/event |
| Độ tin cậy | Tính lũy quyền (Idempotency) | Phát lại phiên/turn/rerun không tạo ra bản tóm tắt hoặc sự kiện trùng lặp |
| Độ tươi mới | Phản hồi tương tác | Luôn trả kèm mốc kiểm chứng watermark, `lastUpdatedAt` và trạng thái tươi mới |
| Khả năng quan sát | Khả năng truy vết (Traceability) | Giữ Correlation ID từ dashboard xuyên suốt qua chính sách/công cụ/nhà cung cấp/kiểm toán/outbox |
| Kiểm soát chi phí | Ngân sách nhà cung cấp | Giới hạn token/chi phí theo từng lượt hỏi và hạn mức ngày của tenant; vi phạm sẽ đóng ngắt an toàn |
| Thời gian lưu trữ | Hội thoại và kiểm toán | Cấu hình theo chính sách; mặc định không lưu prompt thô, metadata an toàn lưu 90 ngày, tuân thủ chính sách kiểm toán cha |
| Khả năng mở rộng | API và Worker | API hoàn toàn phi trạng thái (stateless); worker xử lý tóm tắt và nhà cung cấp mở rộng độc lập |

## 11. An ninh, quyền riêng tư và khả năng quan sát (Security, Privacy & Observability)

### An ninh (Security)

- Kiểm tra chữ ký JWT, nguồn phát hành (issuer), đối tượng thụ hưởng (audience), thời hạn hết hạn và vai trò/thành viên phía máy chủ.
- Sử dụng định danh tải công việc (workload identity) hoặc mTLS cho các cuộc gọi nội bộ giữa Reporting/nhà cung cấp/worker.
- Luôn coi tin nhắn người dùng, dữ liệu lấy về và kết quả từ mô hình là dữ liệu không đáng tin cậy; chính sách hệ thống và danh mục công cụ không thể bị ghi đè bởi nội dung prompt.
- Bắt buộc áp dụng danh mục trắng cho các tham số truy vấn, giới hạn ngày/kích thước kết quả, giới hạn tần suất và bộ ngắt mạch.
- Sử dụng AWS Secrets Manager để lưu trữ API key của DeepSeek; cho phép xoay vòng khóa bí mật mà không cần triển khai lại mã nguồn.
- Che dấu dữ liệu PII/khóa bí mật trước khi gửi vào prompt, ghi log, trace, audit và event; thông báo lỗi an toàn không bao giờ để lộ sự tồn tại của các chi nhánh không được phân quyền.
- Ghi nhận đầy đủ các quyết định chính sách và lý do từ chối vào nhật ký kiểm toán nhưng mặc định không lưu trữ nội dung chat thô.

### Chỉ số đo lường (Metrics)

- `ai_turn_total{status,intent,role}`;
- `ai_tool_total{tool,status}`;
- `ai_tool_latency_ms{tool}`;
- `ai_provider_latency_ms{model,status}`;
- `ai_provider_tokens_total{direction}` và ước tính chi phí;
- `ai_scope_refusal_total{reason}`;
- `ai_pii_redaction_total{field_class}`;
- `ai_report_freshness_seconds{tool,status}`;
- `ai_daily_summary_total{status,baseline_type}`;
- `ai_daily_summary_lag_seconds`;
- `ai_prompt_injection_refusal_total`;
- `ai_outbox_lag` / `ai_dlq_depth`.

### Nhật ký và dấu vết (Logs/Traces)

Nhật ký có cấu trúc chỉ chứa các trường ID, loại/số lượng phạm vi an toàn, ý định người dùng, tên công cụ/trạng thái, độ trễ và phiên bản chính sách; tuyệt đối không chứa câu hỏi thô/prompt/phản hồi/PII. Dấu vết phân tán (distributed traces) lưu kèm correlation/causation ID và metadata span của nhà cung cấp nhưng phải lược bỏ toàn bộ nội dung và khóa bí mật.

## 12. Trường hợp biên và chính sách xử lý sự cố (Edge Cases & Failure Policy)

| Tình huống | Hành vi yêu cầu |
|---|---|
| Manager hỏi số liệu của chi nhánh khác theo tên/ID | Trả về `NEEDS_SCOPE` hoặc `scope_not_allowed`; không để lộ sự tồn tại của chi nhánh đó và không gọi công cụ. |
| Owner hỏi tất cả chi nhánh với khoảng thời gian quá dài | Từ chối yêu cầu kèm thông báo khoảng thời gian tối đa cho phép; hướng dẫn chọn khoảng hẹp hơn, không tự ý cắt ngắn dữ liệu ngầm định. |
| Cụm từ mơ hồ như “tuần này” | Tự giải quyết dựa vào múi giờ kinh doanh của chi nhánh/tenant và trả về chính xác ngày bắt đầu/kết thúc; nếu chưa chọn chi nhánh, yêu cầu người dùng chọn. |
| Người dùng yêu cầu bỏ qua chính sách/tiết lộ prompt/sinh câu lệnh SQL | Từ chối thẳng thừng; ghi nhận sự kiện chính sách an toàn; không gọi công cụ. |
| Đối số của công cụ chứa câu lệnh SQL hoặc trường dữ liệu thô | Từ chối ngay tại tầng schema/chính sách trước khi chuyển sang Reporting. |
| Dữ liệu chiếu của Reporting bị chậm (stale) | Trả về `STALE_DATA` hoặc kết quả từng phần kèm cảnh báo độ tươi mới rõ ràng; tác vụ tóm tắt ngày chuyển sang chờ. |
| Reporting chỉ có dữ liệu của một phần danh sách chi nhánh | Tuyệt đối không trình bày tổng doanh thu như một con số hoàn chỉnh; liệt kê rõ các nguồn dữ liệu còn thiếu và trạng thái. |
| Không phát sinh doanh số trong kỳ | Trả về `NO_DATA` hoặc số thực tế bằng 0 kèm giải thích rõ mẫu số; không tự ý suy luận là doanh số suy giảm. |
| Không có dữ liệu mốc so sánh (No baseline) | Chỉ công bố nếu chính sách cho phép `NO_BASELINE`; lược bỏ phần so sánh tỷ lệ phần trăm. |
| DeepSeek bị quá thời gian chờ (timeout) | Thử lại có giới hạn nếu còn hạn mức; sau đó trả về dữ liệu cấu trúc hoặc thông báo lỗi, không tự bịa câu từ. |
| DeepSeek trả về hàm/JSON không hợp lệ | Từ chối kết quả đầu ra, thử sửa/tạo lại có giới hạn; nếu không thành công chuyển sang `FAILED` hoặc `PROVIDER_UNAVAILABLE`. |
| Người dùng gửi lại cùng một khóa lượt hỏi (turn key) | Trả về kết quả lũy quyền gốc ban đầu; không gọi lại công cụ hoặc sinh sự kiện trùng lặp. |
| Chạy lại tóm tắt sau khi có sự kiện `OrderPaid` bổ sung muộn | Tạo bản hiệu chỉnh bất biến mới, bản cũ chuyển sang `SUPERSEDED`, ghi nhận rõ mốc nguồn và lý do trong phả hệ. |
| Chi nhánh đóng cửa muộn hoặc không bao giờ đóng ca | Bộ lập lịch áp dụng giờ chốt ngày (cutoff) rõ ràng; tác vụ chuyển sang chờ hoặc gắn cờ yêu cầu can thiệp vận hành, không tự coi là đã xong. |
| Khóa bí mật/cấu hình của nhà cung cấp LLM không khả dụng | Đóng ngắt an toàn kèm mã lỗi an toàn; tuyệt đối không ghi khóa bí mật thô ra log. |

## 13. Tiêu chí nghiệm thu (Acceptance Criteria)

### P1

1. Chủ quán hợp lệ hỏi tóm tắt doanh số của một chi nhánh và nhận được câu trả lời kèm chính xác khoảng thời gian, múi giờ, mốc kiểm chứng, nguồn công cụ và phiên bản tính toán.
2. Quản lý hợp lệ hỏi danh sách món bán chạy của chi nhánh được phân công và chỉ nhận được nhãn an toàn tổng hợp của các món.
3. Truy vấn của Quản lý đối với chi nhánh chưa được phân quyền bị từ chối ngay trước khi gọi Reporting; không làm rò rỉ sự tồn tại của chi nhánh đó.
4. Việc so sánh liên chi nhánh của Chủ quán chỉ hoạt động trong phạm vi tenant và tập hợp chi nhánh được phân quyền.
5. Chỉ số AOV và số liệu theo giờ sử dụng đúng schema do Reporting sở hữu và tuân thủ quy tắc làm tròn/múi giờ tất định.
6. Mọi hành vi cố tình chèn prompt tấn công (prompt injection) nhằm chạy SQL, làm lộ prompt hệ thống, leo thang đặc quyền hoặc thay đổi dữ liệu đều bị từ chối mà không kích hoạt gọi công cụ.
7. Khi công cụ bị quá thời gian chờ, dữ liệu bị thiếu hoặc cũ, hệ thống phải hiển thị trạng thái và cảnh báo rõ ràng; không trả về bất kỳ con số nào do mô hình tự bịa.
8. Sự cố mất kết nối tới DeepSeek sẽ trả về dữ liệu cấu trúc hoặc thông báo lỗi và giữ nguyên vẹn toàn bộ trạng thái dữ liệu nghiệp vụ của hệ thống.
9. Gửi lại cùng một khóa lượt hỏi (turn key) sẽ nhận lại đúng kết quả ban đầu và không tạo ra tác dụng phụ lặp lại trong kiểm toán hoặc sự kiện.
10. Kiểm thử tính năng che giấu dữ liệu chứng minh không có số điện thoại, tên khách hàng, định danh giao dịch thô hoặc khóa bí mật nào lọt vào nhà cung cấp LLM, file log hay sự kiện.

### P2

1. Bộ lập lịch tạo duy nhất một tác vụ lũy quyền cho mỗi chi nhánh/ngày kinh doanh/phiên bản chính sách sau giờ chốt ca.
2. Tác vụ kiên nhẫn chờ mốc kiểm chứng tính đầy đủ và tuyệt đối không phát hành một bản tóm tắt thiếu hụt số liệu.
3. Sử dụng mốc so sánh cùng thứ của tuần trước; các phương án dự phòng 7 ngày và `NO_BASELINE` được thể hiện rõ ràng.
4. Dữ liệu phân tích đóng góp đảm bảo tính tất định, có phiên bản và có thể truy ngược về mốc kiểm chứng nguồn của Reporting.
5. Ngôn từ trong bản tóm tắt sử dụng các cụm từ liên quan/đóng góp và không bao giờ khẳng định tính nguyên nhân một cách tuyệt đối khi chưa đủ chứng cứ.
6. Lượt chạy lại khi có số liệu muộn sẽ tạo một bản hiệu chỉnh bất biến mới và thay thế bản cũ một cách rõ ràng không sinh trùng lặp.
7. Chủ quán/Quản lý không thể xem hoặc yêu cầu chạy lại bản tóm tắt nằm ngoài phạm vi chi nhánh đã được giải quyết quyền của mình.

## 14. Bảng truy vết yêu cầu (Traceability)

| Mục tiêu trong Vision | Yêu cầu nghiệp vụ | Bằng chứng nghiệm thu |
|---|---|---|
| Hỏi đáp báo cáo bằng ngôn ngữ tự nhiên | FR-AI-001–008, Public Chat APIs | Luồng chat E2E trên Dashboard có phân quyền kèm nguồn gốc số liệu |
| Cơ chế DeepSeek Function Calling | FR-AI-003–005, trạng thái nhà cung cấp/lỗi | Danh mục trắng công cụ, JSON schema, kiểm thử vòng lặp tối đa và kết quả lỗi |
| Rào chắn bảo vệ phạm vi chi nhánh | §3.1, FR-AI-002, yêu cầu an ninh | Ma trận phạm vi Owner/Manager, kiểm thử chống IDOR và prompt injection |
| Che giấu dữ liệu PII | FR-AI-005/009, §11 | Bằng chứng kiểm tra che dữ liệu gửi nhà cung cấp/log/event |
| Trợ lý chỉ đọc (Read-only Assistant) | §3.4, danh mục công cụ không có lệnh sửa đổi | Kiểm thử âm tính ranh giới không cho phép mutation |
| Báo cáo tóm tắt hàng ngày | FR-AI-201–205, máy trạng thái tóm tắt | Kịch bản kiểm thử giờ chốt ca/tính đầy đủ/mốc so sánh/chạy lại |
| Tính giải thích được “Tại sao” | FR-AI-203/204, schema phân tích đóng góp | Kiểm thử tính tất định của yếu tố đóng góp/làm tròn/ngôn từ |

## 15. Các quyết định thống nhất từ đợt duyệt Vision/Requirements

Các cảnh báo ảnh hưởng tới ranh giới và giao ước đã được giải quyết thông qua các quyết định dưới đây; mọi quyết định đều phải được phản ánh đầy đủ trong tài liệu Kiến trúc (Architecture) và Giao ước (Contracts).

### 15.1 Thẩm quyền sở hữu của Reporting và Từ điển số liệu (Metric Dictionary)

- Reporting là **chủ sở hữu số liệu (metric owner)** và là thẩm quyền duy nhất cho `sales_daily`, `sales_by_branch`, `sales_by_hour`, `top_items`, `aov` và `contribution_drivers`.
- AI Assistant gọi các công cụ Reporting nội bộ đồng bộ phục vụ tương tác chat trực tiếp. Các sự kiện `ReportProjectionWatermarkUpdated.v1` và `BusinessDayReady.v1` (nếu được phát ra) chỉ đóng vai trò là gợi ý/kích hoạt; truy vấn sang Reporting và endpoint kiểm tra độ tươi mới vẫn là thẩm quyền tối cao cuối cùng.
- `paidOrderCount` đếm số lượng đơn thanh toán hoàn tất duy nhất từ sự kiện `OrderPaid.v1`; các trường `grossMinor`, `netMinor`, `vatMinor` lấy từ các bản chiếu chuẩn của Reporting và không bao giờ được tự tính lại trong AI Assistant.
- `AOV = netMinor / paidOrderCount`, làm tròn theo `calculationVersion` của Reporting; trường hợp mẫu số bằng 0 trả về `NO_DATA`, không trả về số `0` gây hiểu lầm.
- Nghiệp vụ liên quan đến hoàn tiền/điều chỉnh/thanh toán muộn do chính sách số liệu của Reporting quyết định và trả kèm `calculationVersion`; AI Assistant chỉ hiển thị đúng các giá trị nhận được.
- Tài liệu Kiến trúc/Giao ước phải bổ sung đầy đủ schema của công cụ và các bộ dữ liệu mẫu chuẩn (golden fixtures) cho 6 công cụ quy định tại FR-AI-004, tuyệt đối không truy vấn chéo cơ sở dữ liệu của Order/Payment.

### 15.2 Lan truyền và thu hồi thẩm quyền phân quyền (Authorization Propagation & Revocation)

- Mỗi lượt tạo hội thoại và mỗi lượt hỏi được bảo vệ phải gọi sang Identity qua endpoint `POST /internal/v1/authorization-decisions` kèm theo Cognito subject hiện tại, thông tin yêu cầu tenant/chi nhánh, quyền `ai:report:read` và correlation ID.
- `ResolvedScope` lưu trữ `membershipVersion` / `authorizationVersion`; công cụ Reporting nhận phạm vi đáng tin cậy đã được ký số hoặc thông qua workload identity và tiếp tục áp dụng điều kiện lọc tenant/chi nhánh tại tầng truy xuất dữ liệu.
- Tuyệt đối không sử dụng bộ nhớ đệm (cache) phân quyền cục bộ lâu dài làm căn cứ thẩm quyền cho P1. Sự kiện `AuthorizationContextChanged.v1` chỉ dùng để hủy bỏ cache tối ưu không mang tính quyết định; việc thu hồi tư cách thành viên hoặc thay đổi vai trò phải có hiệu lực ngay ở request kế tiếp.
- Phạm vi toàn chuỗi của Chủ quán phải được khai báo tường minh là `scopeType=TENANT`, không biểu diễn bằng ký tự đại diện (wildcard).

### 15.3 Ngữ cảnh hội thoại không lưu trữ nội dung chat thô

- Mỗi lượt hỏi chỉ lưu trữ ngữ cảnh ngữ nghĩa có cấu trúc (structured semantic context): ý định, khoảng thời gian, loại so sánh, số liệu, các chiều phân tích, phiên bản phạm vi và các tham chiếu kết quả an toàn; không lưu tin nhắn thô, nội dung prompt hay phản hồi thô của người dùng.
- Câu hỏi tiếp nối được giải quyết dựa trên ngữ cảnh cấu trúc gần nhất kết hợp với câu hỏi hiện tại; nếu không đủ rõ ràng, trả về trạng thái `NEEDS_SCOPE` hoặc yêu cầu làm rõ.
- Ngữ cảnh có cấu trúc được mã hóa khi lưu trữ (encryption at rest), thời hạn hết hạn mặc định 90 ngày; tham chiếu kiểm toán giữ metadata đã che giấu theo chính sách kiểm toán cha, thời gian lưu trữ prompt thô mặc định bằng 0.

### 15.4 Thực thi chat và kiểm tra kết quả từ nhà cung cấp (Chat Execution & Provider Validator)

- Luồng chat P1 thực thi đồng bộ trong thời hạn tối đa 10 giây. Nếu công cụ hoặc mô hình cần thêm thời gian xử lý hoặc sắp hết hạn chót, API sẽ trả về mã `202` kèm `turnId` và địa chỉ URL để poll kết quả; MVP không áp dụng cơ chế SSE.
- Mỗi lượt hỏi tối đa 3 lần gọi công cụ, 1 lần yêu cầu mô hình sửa/tạo lại kết quả và 1 lần thử lại gọi nhà cung cấp nếu còn ngân sách; nếu vượt quá giới hạn sẽ chuyển sang trả về dữ liệu cấu trúc hoặc thông báo lỗi.
- Máy chủ chịu trách nhiệm xác thực định dạng JSON hàm của mô hình và schema câu trả lời cuối cùng. Phần văn bản mô tả nếu mâu thuẫn với số liệu cấu trúc sẽ không được phát hành; sau một lần sửa thất bại, trả về số liệu an toàn hoặc trạng thái `PROVIDER_UNAVAILABLE`.
- Adapter kết nối DeepSeek sử dụng phiên bản chính sách mô hình/prompt đã được cấu hình, áp dụng hạn mức token/chi phí, thời gian chờ 8 giây, thử lại có giới hạn 1 lần và bộ ngắt mạch; MVP không cấu hình nhà cung cấp dự phòng. Kiểm tra sức khỏe không gửi dữ liệu người dùng hoặc báo cáo.

### 15.5 Tính đầy đủ của tóm tắt ngày và dữ liệu muộn (Daily Summary Completeness & Late Facts)

- Bộ lập lịch tạo tác vụ tại thời điểm `businessDayCutoff + 15 phút` theo múi giờ của chi nhánh. Endpoint kiểm tra độ tươi mới của Reporting là thẩm quyền để chuyển trạng thái từ `WAITING_FOR_DATA → READY`.
- Tác vụ chờ tối đa 30 phút để mốc kiểm chứng tính đầy đủ đạt yêu cầu; quá thời hạn sẽ chuyển sang `WAITING_FOR_DATA` hoặc `DATA_LATE` và yêu cầu thử lại có giới hạn hoặc vận hành viên kích hoạt chạy lại, tuyệt đối không công bố như một bản hoàn chỉnh.
- Các sự kiện phát sinh muộn như `OrderPaid`, hoàn tiền/điều chỉnh hoặc việc xây dựng lại dữ liệu chiếu sau khi đã công bố sẽ tạo ra một phiên bản tóm tắt mới khi mốc kiểm chứng nguồn thay đổi; bản tóm tắt cũ chuyển sang trạng thái `SUPERSEDED`, không ghi đè vật lý.
- Sự kiện `AIDailySummarySuperseded.v1` mang ID tóm tắt cũ/mới, mốc kiểm chứng nguồn và lý do; khóa lũy quyền chạy lại là `(tenantId, branchId, businessDate, policyVersion, sourceWatermark)`.

### 15.6 Quản trị dữ liệu và nhà cung cấp LLM (Provider & Data Governance)

- Chính sách nhà cung cấp là cấu hình có phiên bản: mã mô hình/phiên bản, phiên bản chính sách prompt, khu vực/chế độ kết nối ra ngoài, hạn mức token/chi phí, thời gian chờ, số lần thử lại, trạng thái ngắt mạch và tham chiếu khóa bí mật (không lưu trực tiếp khóa bí mật).
- Dữ liệu gửi sang nhà cung cấp chỉ bao gồm chính sách hệ thống + dữ liệu tổng hợp có cấu trúc + ngữ cảnh ngữ nghĩa có cấu trúc đã được che giấu thông tin nhạy cảm; tuyệt đối không có số điện thoại/danh tính khách hàng/ID giao dịch thô.
- Ma trận lưu trữ phân định rõ ràng giữa ngữ cảnh cấu trúc, metadata công cụ, số liệu tóm tắt, tham chiếu kiểm toán và lỗi của nhà cung cấp; việc lưu trữ phục vụ pháp lý/kiểm toán không được phép lưu trữ prompt thô trái với chính sách.

### 15.7 Công thức tính đóng góp và ngôn từ sử dụng (Contribution Calculation & Wording)

- Reporting trả về mức chênh lệch có dấu, số tiền đóng góp / tỷ lệ phần trăm đóng góp, ngưỡng trọng yếu, danh sách top-N và `calculationVersion`.
- AI Assistant bắt buộc dùng các cụm từ “yếu tố liên quan / đóng góp quan sát được”; từ “nguyên nhân” chỉ được phép dùng khi Reporting có gắn cờ xác nhận bằng chứng nhân quả tường minh (explicit causal evidence flag), mặc định là false.
- Quy tắc làm tròn, trường hợp mẫu số bằng 0, đóng góp âm và trường hợp bằng điểm số phải có các bộ dữ liệu kiểm thử chuẩn (golden fixtures); mô hình LLM tuyệt đối không được tự ý tính lại phần trăm.

## 16. Đầu vào cho giai đoạn tiếp theo (Next Phase Inputs)

Tài liệu Kiến trúc (Architecture) cần quyết định:

1. Kiến trúc tổng thể và mô hình liên kết giữa AI Assistant API/Orchestrator, Tool Gateway, Reporting Adapter, DeepSeek Adapter, Policy Engine, Worker và Audit/Outbox.
2. Cơ chế thực thi lượt hỏi đồng bộ so với bất đồng bộ kèm hành vi polling/streaming trên dashboard.
3. Cơ chế lưu trữ phiên/hội thoại, các trường dữ liệu cần mã hóa/che giấu, thời gian lưu trữ và phương án phân vùng/đánh chỉ mục dữ liệu.
4. Giao thức truyền tải cho các công cụ Reporting nội bộ cùng giao ước sự kiện/truy vấn mốc kiểm chứng nguồn và tính đầy đủ.
5. Ranh giới đóng gói prompt/che giấu dữ liệu, bộ xác thực phản hồi của mô hình và máy trạng thái vòng lặp gọi công cụ có giới hạn.
6. Kiến trúc lập lịch tóm tắt ngày / giờ chốt ca / đối soát và xử lý số liệu phát sinh muộn.
7. Mô hình phục hồi sự cố, bộ ngắt mạch nhà cung cấp, kiểm soát ngân sách, hàng đợi thư chết (DLQ) và cơ chế phát lại.

## Cổng phê duyệt (Approval Gate)

- **Trạng thái hiện tại:** Approved with warnings.
- **Yêu cầu trước khi vào Architecture:** Đợt đánh giá yêu cầu (Requirements review) không còn blocker; người dùng chấp thuận yêu cầu hoặc chấp nhận các cảnh báo.
- **Quy tắc triển khai:** Không viết code trực tiếp từ các mô tả văn xuôi; bắt buộc cụ thể hóa đầy đủ OpenAPI, schema công cụ nội bộ, schema sự kiện, cấu hình chính sách nhà cung cấp và migration script trước khi bước vào triển khai thực tế.
