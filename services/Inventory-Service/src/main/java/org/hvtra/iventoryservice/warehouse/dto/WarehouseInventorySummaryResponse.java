package org.hvtra.iventoryservice.warehouse.dto;

import java.math.BigDecimal;

public record WarehouseInventorySummaryResponse(
    String inventoryItemId,
    String productCode,
    String productName,
    String categoryName,
    String unitName,
    BigDecimal totalQuantity,
    BigDecimal shelfQuantity, // POS SHELF
    BigDecimal warehouseQuantity, // WAREHOUSE
    BigDecimal reorderPoint,
    String stockStatus // OUT_OF_STOCK, LOW_STOCK, SUFFICIENT
) {}
