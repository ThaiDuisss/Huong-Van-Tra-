package org.hvtra.orderservice.service.mapper;

import org.hvtra.orderservice.client.CatalogClient;
import org.hvtra.orderservice.domain.entity.OrderItem;
import org.hvtra.orderservice.dto.response.OrderItemResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class OrderItemMapper {

    public OrderItem toOrderItem(
            CatalogClient.ProductVariantPosViewResponse variant,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal discountAmount,
            BigDecimal lineTotal,
            UUID bomVersionId
    ) {
        return OrderItem.builder()
                .productVariantId(variant.variantId())
                .skuSnapshot(variant.sku())
                .productNameSnapshot(variant.productName())
                .variantNameSnapshot(variant.variantName())
                .quantity(quantity)
                .originalUnitPrice(unitPrice)
                .actualUnitPrice(unitPrice)
                .discountAmount(discountAmount)
                .lineTotal(lineTotal)
                .bomVersionId(bomVersionId)
                .build();
    }

    public OrderItem toDraftOrderItem(
            CatalogClient.ProductVariantPosViewResponse variant,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal discountAmount,
            UUID bomVersionId
    ) {
        return OrderItem.builder()
                .productVariantId(variant.variantId())
                .skuSnapshot(variant.sku())
                .productNameSnapshot(variant.productName())
                .variantNameSnapshot(variant.variantName())
                .quantity(quantity)
                .originalUnitPrice(unitPrice)
                .actualUnitPrice(unitPrice)
                .discountAmount(discountAmount)
                .bomVersionId(bomVersionId)
                .build();
    }

    public OrderItemResponse toResponse(OrderItem item) {
        return OrderItemResponse.builder()
                .id(item.getId())
                .productVariantId(item.getProductVariantId())
                .skuSnapshot(item.getSkuSnapshot())
                .sku(item.getSkuSnapshot())
                .productNameSnapshot(item.getProductNameSnapshot())
                .productName(item.getProductNameSnapshot())
                .variantNameSnapshot(item.getVariantNameSnapshot())
                .quantity(item.getQuantity())
                .originalUnitPrice(item.getOriginalUnitPrice())
                .actualUnitPrice(item.getActualUnitPrice())
                .unitPrice(item.getActualUnitPrice())
                .discountAmount(item.getDiscountAmount())
                .lineTotal(item.getLineTotal())
                .bomVersionId(item.getBomVersionId())
                .build();
    }
}
