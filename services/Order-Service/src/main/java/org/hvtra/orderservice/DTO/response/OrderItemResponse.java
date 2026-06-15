package org.hvtra.orderservice.DTO.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class OrderItemResponse {
    private UUID id;
    private UUID productVariantId;
    private String skuSnapshot;
    private String productNameSnapshot;
    private String variantNameSnapshot;
    private BigDecimal quantity;
    private BigDecimal originalUnitPrice;
    private BigDecimal actualUnitPrice;
    private BigDecimal discountAmount;
    private BigDecimal lineTotal;
    private UUID bomVersionId;
}