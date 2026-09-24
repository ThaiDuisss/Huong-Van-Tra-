package org.hvtra.iventoryservice.dashboard.repository.projection;

import java.math.BigDecimal;

public interface DashboardKpiRow {
    Long getSkuCount();
    BigDecimal getTotalOnHand();
    BigDecimal getTotalReserved();
    BigDecimal getTotalBlocked();
    BigDecimal getTotalAvailable();
    Long getBelowReorderCount();
    Long getPendingDocumentsCount();
}
