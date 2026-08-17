package org.hvtra.orderservice.service.support;

import org.hvtra.orderservice.client.CatalogClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductSnapshotResolverTest {

    @Mock
    private CatalogClient catalogClient;

    @InjectMocks
    private ProductSnapshotResolver resolver;

    @Test
    void returnsTheSingleExistingCatalogPosViewCallUnchanged() {
        UUID productVariantId = UUID.randomUUID();
        CatalogClient.ProductVariantPosViewResponse expected =
                new CatalogClient.ProductVariantPosViewResponse(
                        UUID.randomUUID(),
                        productVariantId,
                        "SKU-1",
                        "Trà",
                        "Size M",
                        "Danh mục",
                        new BigDecimal("25000"),
                        "image.png"
                );
        when(catalogClient.getProductVariantPosView(productVariantId)).thenReturn(expected);

        CatalogClient.ProductVariantPosViewResponse result = resolver.resolvePosView(productVariantId);

        assertSame(expected, result);
        verify(catalogClient).getProductVariantPosView(productVariantId);
    }
}
