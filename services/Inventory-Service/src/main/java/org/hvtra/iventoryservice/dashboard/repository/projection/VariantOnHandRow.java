package org.hvtra.iventoryservice.dashboard.repository.projection;

import java.math.BigDecimal;

public interface VariantOnHandRow {
    String getReferenceId();
    BigDecimal getTotalOnHand();
}
