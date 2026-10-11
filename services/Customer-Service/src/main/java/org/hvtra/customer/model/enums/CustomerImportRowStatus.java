package org.hvtra.customer.model.enums;

public enum CustomerImportRowStatus {
    /** Dòng hợp lệ (chỉ trả về khi dryRun = true). */
    VALID,
    /** Dòng đã được tạo thành khách hàng. */
    IMPORTED,
    /** Dòng sai dữ liệu hoặc trùng, không được nhập. */
    INVALID,
    /** Dòng hợp lệ nhưng lưu thất bại (ví dụ bị trùng do thao tác đồng thời). */
    FAILED
}
