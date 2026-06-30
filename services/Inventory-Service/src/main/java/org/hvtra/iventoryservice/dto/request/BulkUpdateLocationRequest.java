package org.hvtra.iventoryservice.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record BulkUpdateLocationRequest(
        List<UUID> inventoryBalanceIds,
        @NotNull UUID newLocationId
) {
}
