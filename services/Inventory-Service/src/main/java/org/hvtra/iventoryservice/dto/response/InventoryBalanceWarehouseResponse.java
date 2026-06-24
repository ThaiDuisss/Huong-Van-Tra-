package org.hvtra.iventoryservice.dto.response;

import org.hvtra.iventoryservice.enums.InventoryItemType;
import org.hvtra.iventoryservice.enums.InventoryStatus;
import java.math.BigDecimal;
import java.util.UUID;

public record InventoryBalanceWarehouseResponse(
        UUID id,
        UUID locationId,
        UUID inventoryItemId,
        UUID variantId,
        InventoryItemType itemType,
        BigDecimal quantityOnHand,
        BigDecimal quantityReserved,
        BigDecimal availableQuantity,
        InventoryStatus status
) {}
