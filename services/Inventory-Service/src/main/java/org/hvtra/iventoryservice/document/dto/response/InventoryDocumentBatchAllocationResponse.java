package org.hvtra.iventoryservice.document.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InventoryDocumentBatchAllocationResponse(
        UUID id,
        UUID inventoryBatchId,
        String batchCode,
        String supplierLot,
        LocalDate manufactureDate,
        LocalDate expiryDate,
        BigDecimal quantity
) {
}
