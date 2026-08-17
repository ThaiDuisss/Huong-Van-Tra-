package org.hvtra.orderservice.service.support;

import lombok.RequiredArgsConstructor;
import org.hvtra.orderservice.client.CatalogClient;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProductSnapshotResolver {

    private final CatalogClient catalogClient;

    public CatalogClient.ProductVariantPosViewResponse resolvePosView(UUID productVariantId) {
        return catalogClient.getProductVariantPosView(productVariantId);
    }
}
