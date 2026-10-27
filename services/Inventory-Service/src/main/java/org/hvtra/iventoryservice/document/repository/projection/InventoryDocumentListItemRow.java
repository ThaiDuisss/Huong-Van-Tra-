package org.hvtra.iventoryservice.document.repository.projection;

import java.math.BigDecimal;
import java.time.Instant;

public interface InventoryDocumentListItemRow {
    String getId();
    String getDocumentCode();
    String getStatus();
    String getSupplierId();
    String getLocationId();
    String getLocationName();
    BigDecimal getTotalReceivedQuantity();
    Instant getReceivedAt();

    Instant getCreatedAt();
    String getCreatedBy();
}
