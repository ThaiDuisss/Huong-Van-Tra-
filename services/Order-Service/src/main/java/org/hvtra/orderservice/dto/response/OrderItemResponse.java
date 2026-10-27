package org.hvtra.orderservice.dto.response;

import lombok.Builder;
import lombok.Data;
import org.hvtra.orderservice.domain.enums.BundlePackingStatus;
import org.hvtra.orderservice.domain.enums.OrderItemType;

import java.math.BigDecimal;
import java.util.List;
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

    /** PRODUCT hoặc CUSTOM_BUNDLE. */
    private OrderItemType itemType;
    /** Chỉ có ở dòng CUSTOM_BUNDLE. */
    private BundlePackingStatus packingStatus;
    private String packingCode;
    private List<Component> components;

    @Data
    @Builder
    public static class Component {
        private UUID ingredientId;
        private String ingredientCode;
        private String ingredientName;
        private String unit;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal lineTotal;
    }
}
