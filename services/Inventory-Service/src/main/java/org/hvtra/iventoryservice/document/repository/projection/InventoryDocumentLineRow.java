package org.hvtra.iventoryservice.document.repository.projection;

import java.math.BigDecimal;

public interface InventoryDocumentLineRow {
    String getId();
    String getDocumentId();
    Integer getLineNo();
    String getInventoryItemId();
    String getItemSku();
    String getItemName();
    String getUnitId();
    BigDecimal getOrderedQuantity();
    BigDecimal getReceivedQuantity();
    BigDecimal getShortageQuantity();
    BigDecimal getExcessQuantity();
    BigDecimal getAcceptedQuantity();
    BigDecimal getRejectedQuantity();
    String getNote();
}
 