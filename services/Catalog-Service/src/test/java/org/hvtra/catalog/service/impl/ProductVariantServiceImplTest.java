package org.hvtra.catalog.service.impl;

import org.hvtra.catalog.client.InventoryClient;
import org.hvtra.catalog.dto.request.ProductVariantRequest;
import org.hvtra.catalog.dto.response.ProductResponse;
import org.hvtra.catalog.entity.Product;
import org.hvtra.catalog.entity.ProductVariant;
import org.hvtra.catalog.enums.InventoryPolicy;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.mapper.ProductMapper;
import org.hvtra.catalog.repository.ProductRepository;
import org.hvtra.catalog.repository.ProductVariantRepository;
import org.hvtra.catalog.repository.UnitRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductVariantServiceImplTest {
    @Mock
    private ProductVariantRepository repository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductMapper mapper;

    @Mock
    private InventoryClient inventoryClient;

    @Mock
    private UnitRepository unitRepository;

    @InjectMocks
    private ProductVariantServiceImpl service;

    @Test
    void createAlsoCreatesInventoryItem() {
        Product product = new Product();
        product.setId(15L);

        ProductVariantRequest request = new ProductVariantRequest(
                "SKU-001",
                "Default",
                new BigDecimal("100000"),
                new BigDecimal("60000"),
                3L,
                InventoryPolicy.TRACK_STOCK,
                Status.ACTIVE);

        ProductResponse.VariantResponse expected = new ProductResponse.VariantResponse(
                21L,
                15L,
                "Tea",
                "SKU-001",
                "Default",
                new BigDecimal("100000"),
                new BigDecimal("60000"),
                3L,
                InventoryPolicy.TRACK_STOCK,
                Status.ACTIVE,
                Status.ACTIVE,
                true,
                List.of());

        when(repository.existsBySku("SKU-001")).thenReturn(false);
        when(productRepository.findById(15L)).thenReturn(Optional.of(product));
        when(unitRepository.existsById(3L)).thenReturn(true);
        when(repository.saveAndFlush(any(ProductVariant.class))).thenAnswer(invocation -> {
            ProductVariant variant = invocation.getArgument(0);
            variant.setId(21L);
            return variant;
        });
        when(mapper.toVariantResponse(any(ProductVariant.class))).thenReturn(expected);

        ProductResponse.VariantResponse actual = service.create(15L, request);

        assertSame(expected, actual);
        verify(inventoryClient).createProductVariantItem(21L, 3L, Status.ACTIVE);
    }
}
