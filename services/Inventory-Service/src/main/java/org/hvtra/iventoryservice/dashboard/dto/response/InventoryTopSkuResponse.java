package org.hvtra.iventoryservice.dashboard.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record InventoryTopSkuResponse(
        UUID inventoryItemId,
        String skuCode,
        String productName,
        String locationName,
        BigDecimal quantityOnHand,
        BigDecimal availableQuantity,
        String unitSymbol
) {}
