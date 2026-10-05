package org.hvtra.iventoryservice.warehouse.dto;

import java.math.BigDecimal;

public record WarehouseInventorySummaryResponse(
    String inventoryItemId,
    String sku,
    String itemName,
    String productName,
    String categoryName,
    String unitName,
    String unitSymbol,
    BigDecimal sellingPrice,
    BigDecimal totalQuantity,
    BigDecimal shelfQuantity,
    BigDecimal warehouseQuantity,
    BigDecimal reorderPoint,
    String stockStatus
) {}
