package org.hvtra.iventoryservice.dashboard.repository.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface DashboardExpiringBatchRow {
    String getId();
    String getBatchCode();
    String getInventoryItemId();
    String getSkuCode();
    String getProductName();
    String getLocationId();
    String getLocationName();
    BigDecimal getOnHandQuantity();
    BigDecimal getAvailableQuantity();
    String getUnit();
    LocalDate getExpiryDate();
    Long getDaysRemaining();
    String getStatus();
}
