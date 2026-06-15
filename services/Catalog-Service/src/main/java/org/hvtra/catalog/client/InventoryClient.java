package org.hvtra.catalog.client;

import org.hvtra.catalog.enums.Status;

public interface InventoryClient {
    void createProductVariantItem(Long variantId, Long baseUnitId, Status status);
}
