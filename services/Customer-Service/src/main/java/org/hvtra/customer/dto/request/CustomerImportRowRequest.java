package org.hvtra.customer.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Một dòng trong file nhập khách hàng. Tất cả trường đều là chuỗi thô để lỗi định dạng
 * được trả về theo từng dòng thay vì làm hỏng cả request.
 */
public record CustomerImportRowRequest(
        @Schema(description = "Số dòng trong file Excel; không truyền thì tính theo vị trí (dòng đầu tiên là 2)")
        Integer rowNumber,
        @Schema(description = "Mã khách hàng; bỏ trống để hệ thống tự sinh")
        String customerCode,
        String fullName,
        String phone,
        String email,
        @Schema(description = "yyyy-MM-dd hoặc dd/MM/yyyy")
        String dateOfBirth,
        @Schema(description = "INDIVIDUAL / BUSINESS hoặc Cá nhân / Doanh nghiệp; bỏ trống = INDIVIDUAL")
        String customerType
) {
}
