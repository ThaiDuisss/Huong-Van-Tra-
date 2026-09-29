package org.hvtra.iventoryservice.warehouse.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record LocationInventoryDetailResponse(
    UUID locationId,
    String locationName,
    String locationType, // SHELF, WAREHOUSE, etc
    BigDecimal quantityOnHand,
    BigDecimal quantityReserved,
    BigDecimal quantityBlocked,
    BigDecimal availableQuantity,
    long batchCount
) {}
