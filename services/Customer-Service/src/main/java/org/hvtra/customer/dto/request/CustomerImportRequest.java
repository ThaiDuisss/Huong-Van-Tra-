package org.hvtra.customer.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CustomerImportRequest(
        @NotEmpty(message = "Danh sách khách hàng cần nhập đang trống")
        @Size(max = CustomerImportRequest.MAX_ROWS, message = "Mỗi lần chỉ được nhập tối đa " + CustomerImportRequest.MAX_ROWS + " khách hàng")
        List<CustomerImportRowRequest> rows,

        @Schema(description = "true: chỉ kiểm tra dữ liệu, không lưu")
        Boolean dryRun
) {
    public static final int MAX_ROWS = 500;
}
