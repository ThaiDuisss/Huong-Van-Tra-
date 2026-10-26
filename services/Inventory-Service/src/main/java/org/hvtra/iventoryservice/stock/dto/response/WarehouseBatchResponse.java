package org.hvtra.iventoryservice.stock.dto.response;

import org.hvtra.iventoryservice.domain.enums.InventoryItemType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record WarehouseBatchResponse(
    UUID batchId,
    String batchCode,
    String supplierBatchCode,
    LocalDate manufactureDate,
    LocalDate expiryDate,
    String status,
    String unitSymbol,
    BigDecimal quantityOnHand,
    BigDecimal quantityReserved,
    BigDecimal quantityBlocked,
    BigDecimal availableQuantity,
    Long daysUntilExpiry,
    InventoryItemType itemType
) {}
