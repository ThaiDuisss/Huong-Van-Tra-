package org.hvtra.iventoryservice.dashboard.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record InventoryLocationSummaryResponse(
        UUID locationId,
        String locationCode,
        String locationName,
        BigDecimal totalOnHand,
        BigDecimal totalAvailable,
        BigDecimal totalReserved
) {}
