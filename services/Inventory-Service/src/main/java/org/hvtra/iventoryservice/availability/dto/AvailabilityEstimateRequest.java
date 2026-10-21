package org.hvtra.iventoryservice.availability.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * @param orderId   đơn đang hỏi (nếu có) để không tính trùng lệnh sản xuất đã gắn với chính đơn này
 * @param lines     số lượng khách đặt của từng mặt hàng (tổng trong đơn)
 */
public record AvailabilityEstimateRequest(
        UUID branchId,
        UUID orderId,
        String orderCode,
        @NotEmpty(message = "lines is required") @Valid List<Line> lines
) {
    public record Line(
            @NotNull(message = "productVariantId is required") UUID productVariantId,
            @NotNull(message = "quantity is required")
            @DecimalMin(value = "0.001", message = "quantity must be greater than 0") BigDecimal quantity
    ) {}
}
