package org.hvtra.orderservice.dto.response;

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
    private String sku;
    private String productNameSnapshot;
    private String productName;
    private String variantNameSnapshot;
    private BigDecimal quantity;
    private BigDecimal originalUnitPrice;
    private BigDecimal actualUnitPrice;
    private BigDecimal unitPrice;
    private BigDecimal discountAmount;
    private BigDecimal lineTotal;
    private UUID bomVersionId;

    private String unitNameSnapshot;
    private String imageUrlSnapshot;
    private BigDecimal taxAmount;
    private String note;
}
