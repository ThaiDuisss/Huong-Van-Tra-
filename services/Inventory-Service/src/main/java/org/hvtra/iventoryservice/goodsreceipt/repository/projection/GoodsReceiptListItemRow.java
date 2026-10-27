package org.hvtra.iventoryservice.goodsreceipt.repository.projection;

import java.math.BigDecimal;
import java.time.Instant;

public interface GoodsReceiptListItemRow {
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
