package org.hvtra.iventoryservice.stock.dto.request;

import java.util.List;
import java.util.UUID;

public record StockBatchRequest(
        List<UUID> productIds,
        UUID warehouseId
) {
}
