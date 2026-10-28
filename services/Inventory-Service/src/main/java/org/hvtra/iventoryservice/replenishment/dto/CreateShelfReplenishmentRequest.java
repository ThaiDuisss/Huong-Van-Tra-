package org.hvtra.iventoryservice.replenishment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/**
 * Tạo phiếu nhập kệ (chuyển hàng từ kho lên kệ bán).
 * {@code shelfId} / {@code warehouseId} có thể để trống: hệ thống tự chọn kệ đang có hàng và kho mặc định.
 * {@code submit = true} gửi phiếu cho kho xử lý ngay; {@code false} lưu nháp để điền sau.
 * {@code issueNow = true} (POS) chuyển hàng lên kệ ngay trong phiếu riêng, manager duyệt lại sau.
 */
public record CreateShelfReplenishmentRequest(
        UUID shelfId,
        UUID warehouseId,
        UUID branchId,
        String referenceId,
        String note,
        boolean submit,
        boolean issueNow,
        @NotEmpty @Valid List<ShelfReplenishmentLineRequest> lines
) {
}
