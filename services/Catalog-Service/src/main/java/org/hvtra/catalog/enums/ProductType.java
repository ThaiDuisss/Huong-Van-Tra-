package org.hvtra.catalog.enums;

/**
 * Phân loại biến thể sản phẩm trong Catalog:
 * - FINISHED_PRODUCT: Sản phẩm thành phẩm thương mại chuẩn do cửa hàng phát triển và quản lý tồn/khẩu vị.
 * - CUSTOMER: Công thức gói trà do khách hàng mua qua AI/Đóng trà theo yêu cầu, lưu lại để tái sử dụng & bán lại qua CUSTOM_BUNDLE.
 */
public enum ProductType {
    FINISHED_PRODUCT,
    CUSTOMER
}
