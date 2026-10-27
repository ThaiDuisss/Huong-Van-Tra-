package org.hvtra.iventoryservice.goodsreceipt.repository.projection;

import java.math.BigDecimal;
import java.time.Instant;

public interface GoodsReceiptExportRow {
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
