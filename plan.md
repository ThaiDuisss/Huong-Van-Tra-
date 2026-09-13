# Kế hoạch hoàn thiện quy trình kiểm kê kho

## 1. Mục tiêu

Hoàn thiện luồng kiểm kê từ lúc quản lý tạo kỳ, phân công nhân viên, khóa phạm vi, ghi nhận kết quả, đối soát, kiểm kê lại, phê duyệt chênh lệch, hạch toán điều chỉnh cho đến khi đóng kỳ và mở khóa kho.

Luồng mục tiêu:

```text
DRAFT
→ FROZEN
→ COUNTING
→ RECONCILING
→ PENDING_APPROVAL
→ APPROVED
→ POSTED
→ COMPLETED
```

Các nhánh phụ:

```text
RECONCILING → COUNTING          (yêu cầu kiểm kê lại)
PENDING_APPROVAL → RETURNED     (trả lại để bổ sung/kiểm kê lại)
PENDING_APPROVAL → REJECTED     (từ chối yêu cầu điều chỉnh)
DRAFT/FROZEN → CANCELLED        (hủy kỳ hợp lệ)
```

## 2. Phạm vi hiện đã có

- Tạo kỳ kiểm kê theo chi nhánh.
- Phạm vi `FULL`, `LOCATION`, `PARTIAL`.
- Chụp snapshot khi bắt đầu.
- Sinh dòng kiểm kê từ balance thường và balance theo batch.
- Nhập số lượng thực đếm, lý do và ghi chú.
- Tính chênh lệch số lượng.
- Tạo adjustment từ các dòng chênh lệch.
- Duyệt, từ chối và hạch toán adjustment.
- Trang danh sách, tạo mới, chi tiết kiểm kê và adjustment riêng.

## 3. Các quyết định cần duyệt trước khi triển khai

### 3.1. Chính sách khóa phạm vi

Chọn một phương án:

- **Hard lock:** không cho post nhập, xuất, chuyển hoặc adjustment ảnh hưởng đến phạm vi đang kiểm kê.
- **Soft lock:** vẫn cho post giao dịch, nhưng reconciliation phải cộng/trừ movement phát sinh sau snapshot.

**Đã duyệt: sử dụng soft lock.** Giao dịch vẫn được post trong lúc kiểm kê; reconciliation phải tổng hợp movement phát sinh sau snapshot để tính tồn kỳ vọng tại thời điểm đối soát.

### 3.2. Cách biểu diễn loại và phạm vi kiểm kê — Đã duyệt theo đề xuất

Đề xuất tách hai thuộc tính:

```text
countType = FULL_COUNT | CYCLE_COUNT | SPOT_COUNT
scopeType = WAREHOUSE | LOCATION | ITEM | BATCH | MIXED
```

Không tiếp tục dùng `FULL`, `LOCATION`, `PARTIAL` để biểu diễn đồng thời cả loại lẫn phạm vi.

### 3.3. Adjustment có cả tăng và giảm — Đã duyệt phương án tách hai document

Chọn một phương án:

- Tách thành hai document `ADJUSTMENT_IN` và `ADJUSTMENT_OUT`.
- Cho phép document hỗn hợp và lưu hướng điều chỉnh trên từng dòng.

Đề xuất: **tách hai document**, phù hợp với transaction type hiện tại và dễ audit.

### 3.4. Điều kiện cần phê duyệt — Đã duyệt triển khai policy cấu hình được

Cần xác nhận ngưỡng theo:

- Giá trị chênh lệch.
- Tỷ lệ chênh lệch.
- Nhóm hàng hoặc hàng kiểm soát đặc biệt.
- Vai trò người tạo.
- Chi nhánh/kho.

Nếu chưa có quy định chính thức, triển khai policy cấu hình được và tạo bộ ngưỡng mặc định cho môi trường development.

### 3.5. Nguồn dữ liệu nhân viên và chi nhánh — Đã duyệt theo đề xuất

- Nhân viên lấy từ Employee Service.
- Chi nhánh/kho lấy từ Organization Service.
- Xác nhận có sử dụng dữ liệu thật ngay trong giai đoạn này hay tiếp tục provider tạm cho chi nhánh.

## 4. Giai đoạn 1 — Chuẩn hóa domain và database

### 4.1. Kỳ kiểm kê

Mở rộng `inventory_counts`:

- `warehouse_id`.
- `count_type`.
- `planned_start_at`, `planned_end_at`.
- `frozen_at`, `reconciling_at`, `submitted_at`, `closed_at`.
- `supervisor_id`.
- `approval_policy_id` nếu sử dụng policy riêng.
- `blind_count`.
- `lock_mode`.
- `cancelled_at`, `cancelled_by`, `cancel_reason`.

Chuẩn hóa enum trạng thái và chỉ giữ các trạng thái có transition thực tế.

### 4.2. Phạm vi kiểm kê

Thêm hỗ trợ:

- Kho.
- Vị trí.
- Inventory item/SKU.
- Batch/lot.
- Phạm vi kết hợp.

Thêm bảng scope batch nếu cần:

```text
inventory_count_scope_batches
```

### 4.3. Phân công

Tạo bảng:

```text
inventory_count_assignments
- inventory_count_id
- user_id
- assignment_role
- location_id (nullable)
- status
- assigned_at
- accepted_at
- completed_at
```

Vai trò tối thiểu:

```text
COUNTER
SUPERVISOR
APPROVER
```

### 4.4. Lịch sử lần đếm

Không ghi đè kết quả lần trước khi recount. Tạo bảng:

```text
inventory_count_attempts
- count_line_id
- attempt_no
- counter_id
- counted_quantity
- submitted_at
- status
- note
```

### 4.5. Tình trạng hàng

Tạo bảng chi tiết hoặc các trường tương đương để lưu:

```text
GOOD
DAMAGED
EXPIRED
QUARANTINED
```

Ràng buộc:

```text
good + damaged + expired + quarantined = countedQuantity
```

### 4.6. Bằng chứng

Tạo liên kết với File Service để lưu:

- Ảnh vị trí.
- Ảnh SKU.
- Ảnh batch/lot.
- Biên bản hoặc tài liệu bổ sung.

### 4.7. Migration

- Tạo migration Flyway mới, không sửa migration đã chạy.
- Có script seed tương thích schema mới.
- Có rollback thủ công/document khôi phục cho môi trường development.

## 5. Giai đoạn 2 — Backend workflow

### 5.1. Tạo và xác nhận kỳ

- Validate kho/chi nhánh/vị trí/item/batch tồn tại và active.
- Validate phạm vi thuộc đúng kho và chi nhánh.
- Validate nhân viên được phân công hợp lệ.
- Kiểm tra kỳ khác đang mở trùng phạm vi.
- Thêm endpoint xác nhận và khóa phạm vi.

### 5.2. Khóa phạm vi

- Tạo service kiểm tra lock theo kho, vị trí, item và batch.
- Tích hợp lock guard vào luồng post nhập, xuất, chuyển và adjustment.
- Đảm bảo unlock khi kỳ bị hủy hoặc đóng thành công.
- Có cơ chế phục hồi lock nếu transaction thất bại.

### 5.3. Snapshot và sinh danh sách đếm

- Snapshot theo phạm vi đã frozen.
- Sinh dòng theo item/location/batch.
- Không sinh trùng dòng.
- Lưu giá vốn tại thời điểm snapshot nếu cần tính variance value.
- Ghi audit event khi snapshot.

### 5.4. Nhập và gửi kết quả

- Counter chỉ được thao tác trên assignment của mình.
- Validate vị trí, SKU, batch và đơn vị.
- Hỗ trợ scan lookup.
- Lưu nhiều lần đếm.
- Thêm endpoint submit kết quả riêng; submit không đồng nghĩa đóng kỳ.

### 5.5. Reconciliation

- Chuyển kỳ sang `RECONCILING` sau khi mọi assignment submit.
- Hiển thị movement hoặc chứng từ chưa post liên quan.
- Cho supervisor xác nhận nguyên nhân.
- Cho yêu cầu recount toàn bộ hoặc từng dòng.
- Lưu lịch sử quyết định supervisor.

### 5.6. Approval policy

- Tính `variance_value = variance_quantity × snapshot_unit_cost`.
- Xác định có cần phê duyệt dựa trên policy.
- Tạo approval request với approver phù hợp.
- Hỗ trợ `APPROVED`, `REJECTED`, `RETURNED`.
- Bắt buộc reason khi reject/return.
- Không cho người tạo tự duyệt nếu policy yêu cầu phân tách nhiệm vụ.

### 5.7. Adjustment

- Chỉ tạo adjustment từ dòng đã reconciliation xác nhận.
- Xử lý đúng trường hợp có cả tăng và giảm.
- Không tạo document trùng.
- Post idempotent.
- Lock balance khi cập nhật.
- Lưu transaction và before/after quantity.
- Dùng người dùng hiện tại từ SecurityContext cho `approvedBy`, không dùng `createdBy`.

### 5.8. Đóng kỳ

Thêm endpoint `close` và chỉ cho đóng khi:

- Mọi assignment đã hoàn thành.
- Không còn recount mở.
- Mọi dòng chênh lệch đã được xử lý.
- Approval đã có kết quả hợp lệ.
- Adjustment bắt buộc đã được post.

Khi đóng:

- Chuyển sang `COMPLETED`.
- Ghi `closedAt`, `closedBy`.
- Mở khóa phạm vi.
- Phát event phục vụ reporting/notification.

## 6. Giai đoạn 3 — API contract

### 6.1. API quản lý kỳ

- Tạo/cập nhật/hủy kỳ.
- Freeze/xác nhận phạm vi.
- Bắt đầu kiểm kê.
- Danh sách assignment và tiến độ.
- Submit kết quả.
- Chuyển reconciliation.
- Recount từng dòng hoặc nhiều dòng.
- Gửi phê duyệt.
- Return/reject/approve.
- Close kỳ.

### 6.2. API dành cho scan

- Lookup vị trí theo code/barcode.
- Lookup SKU theo barcode.
- Lookup batch theo mã batch.
- Validate tổ hợp location + item + batch.
- Trả đúng dòng kiểm kê thuộc assignment hiện tại.

### 6.3. Response chi tiết

Bổ sung:

- Assignment và tiến độ.
- Các lần đếm.
- Condition quantities.
- Variance value.
- Reconciliation result.
- Approval history.
- Evidence.
- Lock state.
- Adjustment in/out liên quan.
- Audit timeline.

## 7. Giai đoạn 4 — Frontend

Thiết kế tham khảo cấu trúc và visual language của trang Quản lý khách hàng, nhưng sử dụng API thật.

### 7.1. Trang danh sách kỳ

- Bộ lọc theo kho, loại kiểm kê, trạng thái, thời gian và người phụ trách.
- Thẻ thống kê theo trạng thái.
- Hiển thị tiến độ đếm và số dòng chênh lệch.
- Cảnh báo kỳ quá hạn hoặc đang khóa kho.

### 7.2. Wizard tạo kỳ

Các bước:

1. Chọn chi nhánh/kho.
2. Chọn loại kiểm kê.
3. Chọn phạm vi location/item/batch.
4. Chọn nhân viên và supervisor.
5. Chọn lịch, blind count và chính sách khóa.
6. Xác nhận phạm vi và cảnh báo trùng.

### 7.3. Trang quản lý kỳ

- Thông tin kỳ và trạng thái lock.
- Danh sách assignment.
- Tiến độ theo vị trí/người kiểm kê.
- Các action theo state machine.
- Timeline audit.
- Không trộn thao tác adjustment vào bảng nhập đếm.

### 7.4. Trang nhân viên kiểm kê

Route đề xuất:

```text
/management/inventory/stocktakes/:stocktakeId/count
```

Luồng thao tác:

```text
Quét vị trí → quét SKU → quét batch → nhập số lượng
→ phân loại tình trạng → chụp bằng chứng → lưu/gửi
```

Yêu cầu UI:

- Tối ưu cho barcode scanner và bàn phím.
- Có trạng thái offline/error rõ ràng, nhưng không dùng mock data.
- Hiển thị tiến độ assignment.
- Blind count không hiển thị snapshot cho counter.

### 7.5. Trang reconciliation

Route đề xuất:

```text
/management/inventory/stocktakes/:stocktakeId/reconciliation
```

- Danh sách dòng chênh lệch.
- So sánh các lần đếm.
- Kiểm tra sai vị trí/batch/unit.
- Hiển thị chứng từ và movement liên quan.
- Yêu cầu recount.
- Chốt nguyên nhân.
- Gửi phê duyệt.

### 7.6. Trang approval/adjustment

Mở rộng trang adjustment hiện tại:

- Giá trị chênh lệch và ảnh hưởng giá vốn.
- Kết quả recount.
- Nguyên nhân supervisor xác nhận.
- Hình ảnh/bằng chứng.
- Approval history.
- Action `APPROVED`, `REJECTED`, `RETURNED`.
- Hiển thị riêng adjustment tăng và giảm nếu chọn phương án tách phiếu.

### 7.7. Trang Quality

- Không dùng Quality thay thế adjustment.
- Liên kết các quantity `DAMAGED`, `EXPIRED`, `QUARANTINED` sang quy trình Quality khi cần.
- Từ dòng kiểm kê có thể mở inspection tương ứng.

## 8. Giai đoạn 5 — Phân quyền, audit và bảo mật

- Enforce quyền ở backend, không chỉ ẩn nút trên frontend.
- `WAREHOUSE_MANAGER`: tạo, freeze, phân công và close.
- `COUNTER`: nhập và submit assignment.
- `SUPERVISOR`: reconciliation và yêu cầu recount.
- `APPROVER`: approve/reject/return theo hạn mức.
- Không cho người không được phân công sửa count line.
- Audit mọi transition, thay đổi số đếm, recount, approval và post.
- Ghi đúng current user từ SecurityContext.

## 9. Giai đoạn 6 — Kiểm thử

### 9.1. Unit test

- State transition hợp lệ/không hợp lệ.
- Overlap detection.
- Scope validation.
- Lock/unlock.
- Variance quantity/value.
- Approval policy.
- Recount.
- Adjustment tăng/giảm.
- Close guard.

### 9.2. Integration test

- Repository query theo scope.
- Concurrent start/freeze.
- Concurrent post balance.
- Idempotent adjustment post.
- Rollback không để lock hoặc balance sai.
- Tích hợp Catalog, Employee, Organization và File Service.

### 9.3. Frontend test

- Wizard tạo kỳ.
- Scan location/SKU/batch.
- Validation số lượng và condition.
- Blind count.
- Recount.
- Approval/return/reject.
- Close kỳ.
- Phân quyền theo role.

### 9.4. E2E bắt buộc

1. Full count không chênh lệch.
2. Full count có cả tăng và giảm.
3. Cycle count theo nhóm item.
4. Spot count theo vị trí.
5. Spot count theo batch.
6. Recount cho kết quả khác lần đầu.
7. Adjustment bị returned rồi gửi lại.
8. Adjustment bị rejected.
9. Adjustment approved và posted.
10. Không cho giao dịch tác động vào phạm vi hard lock.
11. Không cho tạo hai kỳ trùng phạm vi.
12. Close kỳ và xác nhận phạm vi đã unlock.

## 10. Tiêu chí hoàn thành

- State machine được backend enforce đầy đủ.
- Không thể tạo kỳ đang mở trùng phạm vi.
- Phạm vi được lock/unlock nhất quán.
- Counter chỉ thấy và sửa assignment của mình.
- Có lịch sử nhiều lần đếm và recount.
- Có phân loại tình trạng hàng và evidence.
- Supervisor có màn hình reconciliation riêng.
- Approval hỗ trợ approve/reject/return và hạn mức.
- Adjustment tăng/giảm được hạch toán chính xác, idempotent.
- Chỉ đóng kỳ sau khi mọi chênh lệch đã xử lý.
- Backend permission và audit đầy đủ.
- Không sử dụng mock data cho luồng chính.
- Unit, integration, frontend và E2E tests quan trọng đều đạt.

## 11. Thứ tự triển khai đề xuất

### P0 — bắt buộc để đúng nghiệp vụ

1. Chốt các quyết định tại mục 3.
2. Chuẩn hóa state machine và migration.
3. Scope, overlap detection và lock/unlock.
4. Assignment và submit kết quả.
5. Reconciliation và recount.
6. Approval policy, `RETURNED` và phân quyền backend.
7. Adjustment tăng/giảm và close kỳ.
8. FE wizard, counting, reconciliation và approval.
9. Integration/E2E test P0.

### P1 — cần cho vận hành thực tế

1. Condition quantities.
2. Evidence/File Service.
3. Variance value và cost impact.
4. Kiểm tra movement/chứng từ chưa post.
5. Audit timeline và notification.

### P2 — nâng cao

1. Blind count hoàn chỉnh.
2. Dashboard năng suất/độ chính xác.
3. Soft lock và movement reconciliation.
4. Báo cáo lịch sử kiểm kê.

## 12. Trạng thái phê duyệt

```text
STATUS: IN_PROGRESS
LOCK_MODE: SOFT_LOCK
APPROVED_AT: 2026-08-27
```

Đang triển khai theo thứ tự P0 → P1 → P2. Các thay đổi phải giữ tương thích với dữ liệu và API hiện tại trong thời gian chuyển tiếp.

## 13. Tiến độ triển khai

### Đã hoàn thành trong milestone P0.1

- Chốt `SOFT_LOCK` và cập nhật trạng thái kế hoạch.
- Thêm `countType`, `warehouseId`, `blindCount`, `lockMode` và các mốc workflow.
- Mở rộng scope cho warehouse, item, batch và mixed, đồng thời giữ tương thích scope cũ.
- Thêm migration V10 cho workflow, assignment, count attempt và batch scope.
- Thêm bước `freeze` trước khi bắt đầu kiểm kê.
- Kiểm tra kỳ đang mở trùng warehouse/location/item/batch khi freeze.
- Chuyển luồng sang `COUNTING → RECONCILING → PENDING_APPROVAL → APPROVED → POSTED → COMPLETED`.
- Thêm `RETURNED`, `REJECTED`, reopen recount và close guard.
- Soft-lock reconciliation cộng movement phát sinh sau snapshot vào expected quantity.
- Tách tính movement thường và movement theo batch.
- Lưu assignment counter/supervisor vào domain và response.
- Lưu lịch sử từng lần nhập số đếm thay vì chỉ giữ giá trị cuối.
- Enforce counter chỉ cập nhật kỳ được phân công; giữ fallback cho dữ liệu legacy chưa có assignment.
- Thêm API yêu cầu recount theo dòng và giao lại counter.
- Lưu số lượng `GOOD`, `DAMAGED`, `EXPIRED`, `QUARANTINED` và validate tổng.
- Blind count được che tại backend đối với counter, không chỉ che UI.
- Snapshot giá vốn từ Catalog và tính variance value.
- Thêm approval policy cấu hình theo chi nhánh; policy mặc định yêu cầu duyệt mọi chênh lệch.
- Enforce vai trò backend cho các transition quản lý, counter và approver.
- Cập nhật FE với freeze, gửi kết quả, gửi phê duyệt, approve/return/reject, reopen và close.
- Unit test state machine và phép tính soft lock đã đạt.

### Đang thực hiện tiếp

- API tra cứu nhân viên thật từ Employee Service (service hiện chưa có controller/API).
- Màn hình reconciliation riêng và UI chọn dòng recount.
- Tách adjustment tăng và giảm.
- Evidence/File Service và liên kết Quality cho hàng lỗi/hết hạn/cách ly.
