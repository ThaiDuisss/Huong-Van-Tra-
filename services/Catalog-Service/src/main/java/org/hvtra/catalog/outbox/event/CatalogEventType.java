package org.hvtra.catalog.outbox.event;

public final class CatalogEventType {

    private CatalogEventType() {
    }

    public static final String PRODUCT_VARIANT_CREATED = "PRODUCT_VARIANT_CREATED";
    public static final String PRODUCT_VARIANT_UPDATED = "PRODUCT_VARIANT_UPDATED";
    public static final String PRODUCT_VARIANT_DELETED = "PRODUCT_VARIANT_DELETED";

    public static final String INGREDIENT_CREATED = "INGREDIENT_CREATED";
    public static final String INGREDIENT_UPDATED = "INGREDIENT_UPDATED";
    public static final String INGREDIENT_DELETED = "INGREDIENT_DELETED";
}

