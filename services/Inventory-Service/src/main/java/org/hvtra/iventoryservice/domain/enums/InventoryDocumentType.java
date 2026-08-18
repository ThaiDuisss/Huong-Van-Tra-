package org.hvtra.iventoryservice.domain.enums;

public enum InventoryDocumentType {
    PURCHASE_RECEIPT,
    PRODUCTION_RECEIPT,
    CUSTOMER_RETURN_RECEIPT,

    SALE_ISSUE,
    PRODUCTION_ISSUE,
    CUSTOM_BUNDLE_ISSUE,

    TRANSFER_ISSUE,
    TRANSFER_RECEIPT,

    SUPPLIER_RETURN,

    ADJUSTMENT_IN,
    ADJUSTMENT_OUT,
    WRITE_OFF
}
