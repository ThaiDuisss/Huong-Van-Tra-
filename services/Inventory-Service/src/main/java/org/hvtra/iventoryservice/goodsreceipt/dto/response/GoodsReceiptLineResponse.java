package org.hvtra.iventoryservice.goodsreceipt.dto.response;

import org.hvtra.iventoryservice.domain.enums.InventoryItemType;
import org.hvtra.iventoryservice.domain.enums.ReceiptExcessResolution;
import org.hvtra.iventoryservice.domain.enums.ReceiptShortageResolution;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record GoodsReceiptLineResponse(
        UUID id,
        Integer lineNo,
        UUID inventoryItemId,
        String itemSku,
        String itemName,
        InventoryItemType itemType,
        UUID unitId,
        String unitName,
        BigDecimal orderedQuantity,
        BigDecimal receivedQuantity,
        BigDecimal shortageQuantity,
        BigDecimal excessQuantity,
        BigDecimal acceptedQuantity,
        BigDecimal rejectedQuantity,
        String note,
        List<GoodsReceiptBatchAllocationResponse> batchAllocations,
        ReceiptExcessResolution excessResolution,
        ReceiptShortageResolution shortageResolution,
        BigDecimal returnedQuantity,
        BigDecimal postedQuantity,
        String resolutionNote
) {
}
