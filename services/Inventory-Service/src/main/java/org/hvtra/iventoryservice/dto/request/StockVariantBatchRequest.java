package org.hvtra.iventoryservice.dto.request;

import java.util.List;
import java.util.UUID;

public record StockVariantBatchRequest(
        List<UUID> variantIds,
        UUID warehouseId
) {
}
