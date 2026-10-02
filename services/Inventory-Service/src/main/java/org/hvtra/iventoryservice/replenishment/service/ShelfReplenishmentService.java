package org.hvtra.iventoryservice.replenishment.service;

import org.hvtra.iventoryservice.domain.enums.InventoryDocumentStatus;
import org.hvtra.iventoryservice.replenishment.dto.CreateShelfReplenishmentRequest;
import org.hvtra.iventoryservice.replenishment.dto.ShelfReplenishmentResponse;
import org.hvtra.iventoryservice.replenishment.dto.ShelfReplenishmentSummaryResponse;
import org.hvtra.iventoryservice.replenishment.dto.UpdateShelfReplenishmentRequest;
import org.hvtra.iventoryservice.shared.pagination.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Phiếu nhập kệ: chuyển thành phẩm từ kho (WAREHOUSE) lên kệ bán (SHELF).
 * Vòng đời: DRAFT (nháp) → PENDING_APPROVAL (đã gửi kho) → POSTED (đã chuyển lên kệ) hoặc CANCELLED.
 */
public interface ShelfReplenishmentService {
    PageResponse<ShelfReplenishmentResponse> findAll(
            InventoryDocumentStatus status, UUID shelfId, String query, Boolean pendingReview, Pageable pageable);

    ShelfReplenishmentSummaryResponse summary();

    ShelfReplenishmentResponse findById(UUID id);

    ShelfReplenishmentResponse create(CreateShelfReplenishmentRequest request);

    ShelfReplenishmentResponse update(UUID id, UpdateShelfReplenishmentRequest request);

    ShelfReplenishmentResponse submit(UUID id);

    ShelfReplenishmentResponse post(UUID id);

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
