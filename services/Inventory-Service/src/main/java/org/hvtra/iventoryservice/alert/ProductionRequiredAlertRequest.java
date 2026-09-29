package org.hvtra.iventoryservice.alert;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record ProductionRequiredAlertRequest(
        @NotNull UUID variantId,
        UUID branchId,
        @NotNull BigDecimal sellableQuantity,
        @NotNull BigDecimal productionThreshold,
        @NotNull BigDecimal productionBatchQuantity
) {}
