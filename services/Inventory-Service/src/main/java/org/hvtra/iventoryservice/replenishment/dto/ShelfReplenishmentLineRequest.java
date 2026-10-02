package org.hvtra.iventoryservice.replenishment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/** Một dòng phiếu nhập kệ: mặt hàng (theo productVariantId của Catalog) và số lượng cần đưa lên kệ. */
public record ShelfReplenishmentLineRequest(
        @NotNull UUID productVariantId,
        @NotNull @DecimalMin(value = "0.00", inclusive = false) BigDecimal quantity,
        String note
) {
}
