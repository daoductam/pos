# Báo Cáo Kiểm Tra Đặc Tả (Spec Sentinel Report): Identity & Access P0

**Đặc tả:** [`docs/plans/identity-access/2026-10-13-spec.md`](./2026-10-13-spec.md)  
**Ngày:** 2026-10-13  
**Trạng thái:** ⚠️ Cảnh báo (Tiến hành thận trọng - Proceed with Caution)

## Các phát hiện (Findings)

### Cảnh báo (Warnings)

| # | Danh mục | Phát hiện | Rủi ro | Đề xuất giải quyết |
|---|---|---|---|---|
| 1 | Ranh giới triển khai | Công cụ migration, cấu trúc package và phong cách persistence được liệt kê là các quyết định mở. | Việc lập kế hoạch có thể tạo ra các quy ước repository, transaction và migration không nhất quán. | Chốt các lựa chọn này trong implementation plan trước khi tạo mã nguồn service; ghi lại phương án được chọn trong ADR nếu ảnh hưởng đến các service khác. |
| 2 | Bảo mật Bootstrap | Ranh giới tin cậy platform-admin/bootstrap được nêu tên nhưng cơ chế xác thực cụ thể và chính sách dùng một lần / chống replay chưa được đặc tả. | Endpoint bootstrap quá rộng có thể tạo tenant hoặc owner mà không có thẩm quyền hợp lệ từ nền tảng. | Thiết lập bootstrap thành một đường dẫn vận hành/job được xác thực riêng, vô hiệu hóa sau khi khởi tạo khi phù hợp, và bổ sung các bài kiểm thử replay/phân quyền. |
| 3 | API ủy quyền nội bộ (Internal Authz API) | Quyết định ủy quyền nội bộ chuẩn (canonical) hiện đã được đưa vào P0, nhưng chi tiết triển khai vẫn còn mở. | Xác thực danh tính workload hoặc ngữ nghĩa từ chối không đầy đủ có thể khiến các service hạ nguồn áp dụng scope không nhất quán. | Xác định rõ xác thực workload, schema request/response, danh mục quyền và ngữ nghĩa từ chối trong plan. |
| 4 | Tích hợp môi trường Local | Định nghĩa hoàn thành (DoD) yêu cầu fixture Cognito/email và ranh giới sự kiện nhưng chưa chọn cấu trúc liên kết adapter cục bộ. | Thiết lập demo có thể phân kỳ khỏi các hợp đồng production hoặc khó tái lập. | Chọn một chiến lược fixture duy nhất (stub hoặc adapter LocalStack) và nêu rõ các cổng (ports), chế độ lỗi và dữ liệu mẫu (seed data) trong plan. |

## Phân Tích Tác Động Chéo (Cross-Impact Analysis)

| Chiều phân tích | Phát hiện | Mức độ rủi ro | Đề xuất |
|---|---|---|---|
| **Logic dùng chung (Shared Logic)** | Không tìm thấy mã nguồn ứng dụng hay định danh identity nào hiện có; không có nơi gọi nào cần migrate. | 🟢 Thấp | Tạo service với ranh giới package/module mới; không suy diễn quy ước từ mã nguồn chưa tồn tại. |
| **Dữ liệu / Trạng thái** | Các bảng PostgreSQL mới cho Identity, ràng buộc duy nhất, optimistic locking, bản ghi idempotency và trạng thái outbox được giới thiệu. | 🟡 Trung bình | Thiết kế thứ tự migration, chú thích comment, rollback/sửa lỗi và kiểm thử đồng thời trước khi triển khai. |
| **Hợp đồng API** | Hợp đồng Identity được tham chiếu bởi thiết kế của service cha, Catalog, Reporting và các service khác; chính sách JWT của service cha hiện đã đồng bộ với thẩm quyền Identity DB. | 🟡 Trung bình | Coi quyết định ủy quyền nội bộ là hợp đồng scope P0 chuẩn và bổ sung các fixture tương thích/bảo mật. |
| **Tính toàn vẹn luồng (Flow Integrity)** | Lời mời → adapter Cognito → kích hoạt và thu hồi → từ chối request kế tiếp đã được bao phủ; sự cố/thử lại với provider được giới hạn và bền vững. | 🟡 Trung bình | Bổ sung kiểm thử end-to-end cho việc gửi trùng lặp, lệch dữ liệu Cognito (drift) và thu hồi trong lúc request đang xử lý. |
| **Yêu cầu xuyên suốt (Cross-Cutting)** | Cô lập tenant/chi nhánh, phòng chống IDOR, hành vi fail-closed, che giấu dữ liệu nhạy cảm và tracing được chỉ định rõ. | 🟡 Trung bình | Bắt buộc áp dụng scope repository trong API triển khai và bổ sung các bài kiểm thử bảo mật phủ định (security-negative) vào nhóm đầu tiên. |

## Tuân Thủ Hướng Dẫn (Instruction Compliance)

| Chiều kiểm tra | Phán quyết | Độ lệch (nếu có) | Đề xuất |
|---|---|---|---|
| **Quy ước (Conventions)** | ✅ | Đặt tên và vị trí tài liệu tuân thủ `AGENTS.md` và `.agent/workflows/feature.md`. | — |
| **Kiến trúc (Architecture)** | ✅ | Ranh giới service và chính sách JWT của service cha tuân thủ các quy ước DDD / cơ sở dữ liệu riêng / outbox. | — |
| **Kiểm thử (Testing)** | ✅ | Bao phủ unit, integration, contract, security-negative, worker và end-to-end. | Thêm các bài kiểm thử rõ ràng cho chính sách claim JWT đã sửa đổi và ranh giới tin cậy bootstrap. |

## Quyết Định (Decision)

Mâu thuẫn trong hợp đồng xác thực đã được giải quyết trong hợp đồng POS của service cha và bản đặc tả tính năng. Các cảnh báo còn lại là các lựa chọn triển khai phải được chốt trong kế hoạch (plan). Tiến hành lập kế hoạch một cách thận trọng; không bắt đầu triển khai code cho đến khi ranh giới tin cậy bootstrap, lựa chọn migration/persistence, fixture tích hợp local và chi tiết hợp đồng ủy quyền nội bộ được nêu rõ ràng.
