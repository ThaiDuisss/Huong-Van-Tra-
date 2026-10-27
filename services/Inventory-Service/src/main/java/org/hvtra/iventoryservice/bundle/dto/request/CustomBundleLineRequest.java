package org.hvtra.iventoryservice.bundle.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * @param ingredientId id nguyên liệu bên Catalog
 * @param quantity     số lượng theo đơn vị tồn kho của nguyên liệu (vd gram)
 */
public record CustomBundleLineRequest(
        @NotNull UUID ingredientId,
        String ingredientName,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity
) {
}
