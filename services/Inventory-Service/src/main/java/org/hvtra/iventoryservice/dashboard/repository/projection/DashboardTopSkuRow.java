package org.hvtra.iventoryservice.dashboard.repository.projection;

import java.math.BigDecimal;

public interface DashboardTopSkuRow {
    String getInventoryItemId();
    String getSkuCode();
    String getProductName();
    String getLocationName();
    BigDecimal getQuantityOnHand();
    BigDecimal getAvailableQuantity();
    String getUnitSymbol();
}
