package org.hvtra.iventoryservice.disposal.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Yêu cầu tiêu hủy hàng lỗi (DAMAGED) đã nhập vị trí hàng lỗi từ một phiếu QC hoàn hàng.
 * Bỏ trống {@code lineIds} để lấy mọi dòng DAMAGED chưa có phiếu tiêu hủy của phiếu QC.
 */
public record CreateDamagedReturnDisposalRequest(
        @NotNull UUID inspectionId,
        List<UUID> lineIds,
        @Size(max = 500) String note
) {
}
