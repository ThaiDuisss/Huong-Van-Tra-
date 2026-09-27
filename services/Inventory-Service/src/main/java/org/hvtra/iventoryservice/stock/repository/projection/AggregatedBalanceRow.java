package org.hvtra.iventoryservice.stock.repository.projection;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Tồn kho đã gộp theo (inventoryItem + location) trên tất cả các batch.
 */
public interface AggregatedBalanceRow {
    String getRepresentativeBalanceId();
    String getInventoryItemId();
    String getLocationId();
    BigDecimal getQuantityOnHand();
    BigDecimal getQuantityReserved();
    BigDecimal getQuantityBlocked();
    Instant getUpdatedAt();
}
