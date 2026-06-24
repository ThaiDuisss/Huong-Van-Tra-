package org.hvtra.catalog.service.impl;

import org.hvtra.catalog.client.InventoryClient;
import org.hvtra.catalog.dto.request.ProductVariantRequest;
import org.hvtra.catalog.dto.response.ProductResponse;
import org.hvtra.catalog.entity.Product;
import org.hvtra.catalog.entity.ProductVariant;
import org.hvtra.catalog.entity.BusinessSequence;
import org.hvtra.catalog.enums.InventoryPolicy;
import org.hvtra.catalog.enums.Status;
import org.hvtra.catalog.mapper.ProductMapper;
import org.hvtra.catalog.repository.ProductRepository;
import org.hvtra.catalog.repository.ProductVariantRepository;
import org.hvtra.catalog.repository.UnitRepository;
import org.hvtra.catalog.repository.BusinessSequenceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    @Mock
    private BusinessSequenceRepository businessSequenceRepository;

    @InjectMocks
    private ProductVariantServiceImpl service;

    @Test
    void createAlsoCreatesInventoryItem() {
        UUID productId = UUID.randomUUID();
        UUID baseUnitId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();

        Product product = new Product();
        product.setId(productId);
        product.setName("Trà Sen");

        ProductVariantRequest request = new ProductVariantRequest(
                "Default",
                new BigDecimal("100000"),
                new BigDecimal("60000"),
                baseUnitId,
                InventoryPolicy.TRACK_STOCK,
                Status.ACTIVE,
                1);

        ProductResponse.VariantResponse expected = new ProductResponse.VariantResponse(
                variantId,
                productId,
                "Trà Sen",
                "PRD00001",
                "TS100001",
                "Default",
                new BigDecimal("100000"),
                new BigDecimal("60000"),
                1,
                baseUnitId,
                InventoryPolicy.TRACK_STOCK,
                Status.ACTIVE,
                Status.ACTIVE,
                true,
                List.of());

        BusinessSequence seq = new BusinessSequence();
        seq.setSequenceKey("SKU");
        seq.setCurrentValue(0L);

        when(businessSequenceRepository.findBySequenceKeyWithLock("SKU")).thenReturn(Optional.of(seq));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(unitRepository.existsById(baseUnitId)).thenReturn(true);
        when(repository.saveAndFlush(any(ProductVariant.class))).thenAnswer(invocation -> {
            ProductVariant variant = invocation.getArgument(0);
            variant.setId(variantId);
            return variant;
        });
        when(mapper.toVariantResponse(any(ProductVariant.class))).thenReturn(expected);

        ProductResponse.VariantResponse actual = service.create(productId, request);

        assertSame(expected, actual);
        verify(inventoryClient).createProductVariantItem(any(InventoryClient.CreateInventoryItemRequest.class));
    }
}
