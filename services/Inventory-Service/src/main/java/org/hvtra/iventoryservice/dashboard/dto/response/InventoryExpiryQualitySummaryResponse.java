package org.hvtra.iventoryservice.dashboard.dto.response;

import java.math.BigDecimal;

public record InventoryExpiryQualitySummaryResponse(
        long expiry7Days,
        long expiry30Days,
        long expiredUnhandled,
        long quarantineOverSla,
        BigDecimal damagedValue,
        long pendingDisposalApprovals
) {}
