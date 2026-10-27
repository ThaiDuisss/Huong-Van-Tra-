package org.hvtra.iventoryservice.replenishment.service;

import org.hvtra.iventoryservice.domain.enums.InventoryDocumentStatus;
import org.hvtra.iventoryservice.replenishment.dto.CreateShelfReplenishmentRequest;
import org.hvtra.iventoryservice.replenishment.dto.RejectShelfReplenishmentRequest;
import org.hvtra.iventoryservice.replenishment.dto.ShelfReplenishmentResponse;
import org.hvtra.iventoryservice.replenishment.dto.ShelfReplenishmentSummaryResponse;
import org.hvtra.iventoryservice.replenishment.dto.UpdateShelfReplenishmentRequest;
import org.hvtra.iventoryservice.shared.pagination.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Phiếu nhập kệ: chuyển thành phẩm từ kho (WAREHOUSE) lên kệ bán (SHELF) — SRS SC-05.
 * Vòng đời: DRAFT (nháp) → PENDING_APPROVAL (chờ duyệt) → POSTED (đã duyệt, hàng đã lên kệ) hoặc REJECTED (bị từ
 * chối, kèm lý do); phiếu chưa duyệt có thể CANCELLED. Người duyệt / từ chối phải khác người tạo.
 */
public interface ShelfReplenishmentService {
    PageResponse<ShelfReplenishmentResponse> findAll(
            InventoryDocumentStatus status, UUID shelfId, String query, Boolean pendingReview, Pageable pageable);

    ShelfReplenishmentSummaryResponse summary();

    ShelfReplenishmentResponse findById(UUID id);

    ShelfReplenishmentResponse create(CreateShelfReplenishmentRequest request);

    ShelfReplenishmentResponse update(UUID id, UpdateShelfReplenishmentRequest request);

    ShelfReplenishmentResponse submit(UUID id);

    /** Duyệt: kiểm tra kho đủ hàng, trừ kho / cộng kệ theo FEFO, ghi giao dịch kho. */
    ShelfReplenishmentResponse approve(UUID id);

    ShelfReplenishmentResponse reject(UUID id, RejectShelfReplenishmentRequest request);

    /** Hủy phiếu nháp / chờ duyệt — chỉ người tạo hoặc Admin. */
    ShelfReplenishmentResponse cancel(UUID id, String note);

    /** Manager duyệt lại phiếu đã được POS xuất kho ngay khi bán. */
    ShelfReplenishmentResponse review(UUID id);

    /**
     * Xuất kho lên kệ ngay cho một đơn bán đang thiếu hàng trên kệ, không chờ duyệt: gộp vào phiếu còn mở của đơn
     * (nếu nhân viên đã tạo từ POS) hoặc tạo phiếu mới, đặt số lượng đúng bằng phần thiếu rồi chuyển hàng luôn.
     * Không có mặt hàng nào thiếu thì hủy phiếu còn mở của đơn và trả về {@code null}.
     */
    ShelfReplenishmentResponse autoIssueForOrder(UUID orderId, UUID branchId,
            java.util.Map<UUID, java.math.BigDecimal> shortageByVariantId);
}
