package org.hvtra.orderservice.service.mapper;

import org.hvtra.orderservice.client.CatalogClient;
import org.hvtra.orderservice.domain.entity.OrderItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderItemMapperTest {

    private final OrderItemMapper mapper = new OrderItemMapper();

    @Test
    void createItemUsesPreparedSnapshotValuesWithoutCallingExternalServices() {
        UUID variantId = UUID.randomUUID();
        CatalogClient.ProductVariantPosViewResponse variant =
                new CatalogClient.ProductVariantPosViewResponse(
                        UUID.randomUUID(),
                        variantId,
                        "SKU-1",
                        "Trà",
                        "Size M",
                        "Danh mục",
                        null,
                        null
                );

        OrderItem item = mapper.toOrderItem(
                variant,
                new BigDecimal("2"),
                BigDecimal.ZERO,
                new BigDecimal("1.00"),
                new BigDecimal("-1.00"),
                null
        );

        assertEquals(variantId, item.getProductVariantId());
        assertEquals(BigDecimal.ZERO, item.getOriginalUnitPrice());
        assertEquals(BigDecimal.ZERO, item.getActualUnitPrice());
        assertEquals(new BigDecimal("-1.00"), item.getLineTotal());
    }
}
