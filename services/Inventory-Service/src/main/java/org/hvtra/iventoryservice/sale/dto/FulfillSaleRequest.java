package org.hvtra.iventoryservice.sale.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Xử lý tồn kho cho một đơn đã thanh toán và giao hàng ngay tại quầy.
 * {@code lines}: thành phẩm bán ra (trừ trên kệ). {@code materials}: nguyên liệu theo BOM của số vừa bán,
 * chỉ để giữ chỗ cho việc sản xuất bù, không bị trừ.
 */
public record FulfillSaleRequest(
        @NotNull UUID orderId,
        UUID branchId,
        int processingAttempt,
        @NotEmpty @Valid List<Line> lines,
        @Valid List<MaterialLine> materials
) {
    public record Line(
            @NotNull UUID productVariantId,
            @NotNull @DecimalMin(value = "0.00", inclusive = false) BigDecimal quantity
    ) {
    }

    public record MaterialLine(
            @NotNull UUID productVariantId,
            @NotNull UUID ingredientId,
            @NotNull @DecimalMin(value = "0.00", inclusive = false) BigDecimal quantity
    ) {
    }
}
