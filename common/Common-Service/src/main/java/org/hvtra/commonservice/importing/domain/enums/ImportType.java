package org.hvtra.commonservice.importing.domain.enums;

/**
 * Loại import dùng chung cho mọi service (Catalog, Inventory, ...). Thêm 1 loại mới ở đây
 * khi service sở hữu loại đó thực sự triển khai {@code ImportTypeHandler} cho nó.
 */
public enum ImportType {
    PRODUCT,
    PRODUCT_VARIANT
}
