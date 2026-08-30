package org.hvtra.iventoryservice.document.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record InventoryDocumentLineResponse(
        UUID id,
        Integer lineNo,
        UUID inventoryItemId,
        String itemSku,
        String itemName,
        UUID unitId,
        String unitName,
        BigDecimal orderedQuantity,
        BigDecimal receivedQuantity,
        BigDecimal shortageQuantity,
        BigDecimal excessQuantity,
        BigDecimal acceptedQuantity,
        BigDecimal rejectedQuantity,
        String note,
        List<InventoryDocumentBatchAllocationResponse> batchAllocations
) {
}
