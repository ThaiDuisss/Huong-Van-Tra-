package org.hvtra.orderservice.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class CreateOrderItemRequest {

    @NotNull(message = "productVariantId không được null")
    private UUID productVariantId;

    private String skuSnapshot;

    @NotBlank(message = "productNameSnapshot không được null hoặc rỗng")
    private String productNameSnapshot;

    private String variantNameSnapshot;

    @NotNull(message = "quantity không được null")
    @Positive(message = "quantity phải lớn hơn 0")
    private BigDecimal quantity;

    @NotNull(message = "originalUnitPrice không được null")
    @DecimalMin(value = "0.00", message = "originalUnitPrice phải lớn hơn hoặc bằng 0")
    private BigDecimal originalUnitPrice;

    @NotNull(message = "actualUnitPrice không được null")
    @DecimalMin(value = "0.00", message = "actualUnitPrice phải lớn hơn hoặc bằng 0")
    private BigDecimal actualUnitPrice;

    @DecimalMin(value = "0.00", message = "discountAmount phải lớn hơn hoặc bằng 0")
    private BigDecimal discountAmount = BigDecimal.ZERO;

    private UUID bomVersionId;
}
