package org.hvtra.orderservice.domain.DTO.request;

import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class CreateOrderItemRequest {

    private UUID productVariantId;

    private String skuSnapshot;

    private String productNameSnapshot;

    private String variantNameSnapshot;

    private BigDecimal quantity;

    private BigDecimal originalUnitPrice;

    private BigDecimal actualUnitPrice;

    private BigDecimal discountAmount = BigDecimal.ZERO;

    private UUID bomVersionId;
}