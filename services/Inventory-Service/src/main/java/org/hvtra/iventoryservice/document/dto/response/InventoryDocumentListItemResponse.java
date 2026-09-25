package org.hvtra.iventoryservice.document.dto.response;

import org.hvtra.iventoryservice.domain.enums.InventoryDocumentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Một dòng trong danh sách phiếu (phân trang) — chỉ các trường hiển thị trên bảng; chi tiết dùng {@link InventoryDocumentResponse}. */
public record InventoryDocumentListItemResponse(
        UUID id,
        String documentCode,
        InventoryDocumentStatus status,
        UUID supplierId,
        String supplierName,
        UUID locationId,
        String locationName,
        BigDecimal totalReceivedQuantity,
        Instant receivedAt,
        UUID createdBy
) {
}
