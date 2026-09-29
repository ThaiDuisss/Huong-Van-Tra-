package org.hvtra.iventoryservice.document.repository.projection;

import java.math.BigDecimal;
import java.time.Instant;

public interface InventoryDocumentExportRow {
    String getDocumentCode();
    String getReferenceId();
    String getSupplierId();
    String getLocationName();
    String getStatus();
    Long getTotalLines();
    BigDecimal getTotalReceivedQuantity();
    BigDecimal getTotalAcceptedQuantity();
    BigDecimal getTotalRejectedQuantity();
    Instant getReceivedAt();
    Instant getCreatedAt();
    String getCreatedBy();
}
