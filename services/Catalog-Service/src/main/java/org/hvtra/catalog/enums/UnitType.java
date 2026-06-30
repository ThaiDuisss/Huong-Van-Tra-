package org.hvtra.catalog.enums;

/**
 * Phân loại đơn vị tính:
 * - BASE: Đơn vị gốc / đơn vị cơ sở (ví dụ: Gram, ml, cái)
 * - PACKAGING: Đơn vị đóng gói thương mại (ví dụ: Hộp, Gói 100g, Thùng)
 *              Liên kết ngược về đơn vị BASE thông qua base_unit_id + conversion_rate
 */
public enum UnitType {
    BASE,
    PACKAGING
}
