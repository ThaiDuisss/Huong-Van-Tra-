package org.hvtra.orderservice.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

/** Một nguyên liệu khách chọn; {@code quantity} theo đơn vị tồn kho của nguyên liệu (vd gram). */
@Data
public class CustomBundleLineRequest {
    @NotNull
    private UUID ingredientId;
    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    private BigDecimal quantity;
}
