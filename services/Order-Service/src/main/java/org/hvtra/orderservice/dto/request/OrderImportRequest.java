package org.hvtra.orderservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Dữ liệu client đã đọc ra từ file Excel nhập đơn hàng: mỗi dòng là một dòng hàng,
 * các dòng cùng "Mã đơn tham chiếu" được gộp thành một đơn.
 */
public record OrderImportRequest(
        @NotEmpty(message = "Danh sách dòng đơn hàng cần nhập đang trống")
        @Size(max = OrderImportRequest.MAX_ROWS,
                message = "Mỗi lần chỉ được nhập tối đa " + OrderImportRequest.MAX_ROWS + " dòng hàng")
        List<OrderImportRowRequest> rows,

        @Schema(description = "true: chỉ kiểm tra dữ liệu, không tạo đơn")
        Boolean dryRun
) {
    public static final int MAX_ROWS = 1000;
    public static final int MAX_ORDERS = 200;
}
