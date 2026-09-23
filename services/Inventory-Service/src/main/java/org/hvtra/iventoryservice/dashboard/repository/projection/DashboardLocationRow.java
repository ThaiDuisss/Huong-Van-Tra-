package org.hvtra.iventoryservice.dashboard.repository.projection;

import java.math.BigDecimal;

public interface DashboardLocationRow {
    String getLocationId();
    String getLocationCode();
    String getLocationName();
    BigDecimal getTotalOnHand();
    BigDecimal getTotalAvailable();
    BigDecimal getTotalReserved();
}
