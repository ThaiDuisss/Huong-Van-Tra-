package org.hvtra.iventoryservice.document.repository.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface InventoryDocumentBatchAllocationRow {
    String getId();
    String getDocumentLineId();
    String getInventoryBatchId();
    String getBatchCode();
    String getSupplierBatchCode();
    LocalDate getManufactureDate();
    LocalDate getExpiryDate();
    BigDecimal getQuantity();
}
