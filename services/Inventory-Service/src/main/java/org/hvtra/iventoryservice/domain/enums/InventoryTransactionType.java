package org.hvtra.iventoryservice.domain.enums;

public enum InventoryTransactionType {
    // Existing values kept for backward compatibility with current services.
    STOCK_IN,
    SALE_OUT,
    RETURN_IN,
    ADJUSTMENT,
    TRANSFER,

    // Richer inventory movement semantics for V8+.
    PURCHASE_RECEIPT,
    PRODUCTION_RECEIPT,
    CUSTOMER_RETURN_RECEIPT,
    PRODUCTION_ISSUE,
    CUSTOM_BUNDLE_ISSUE,
    TRANSFER_ISSUE,
    TRANSFER_RECEIPT,
    SUPPLIER_RETURN,
    ADJUSTMENT_IN,
    ADJUSTMENT_OUT,
    WRITE_OFF
}
