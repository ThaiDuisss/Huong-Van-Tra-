package org.hvtra.iventoryservice.replenishment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/** Sửa phiếu nhập kệ đang ở trạng thái nháp. */
public record UpdateShelfReplenishmentRequest(
        UUID shelfId,
        UUID warehouseId,
        String note,
        @NotEmpty @Valid List<ShelfReplenishmentLineRequest> lines
) {
}
